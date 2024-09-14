package agents.processing

import java.text.SimpleDateFormat
import java.util

import eu.larkc.csparql.common.RDFTable
import eu.larkc.csparql.common.streams.format.{GenericObservable, GenericObserver}
import eu.larkc.csparql.engine.CsparqlEngineImpl
import insight_centre.aceis.eventmodel.EventDeclaration
import insight_centre.aceis.io.EventRepository
import insight_centre.aceis.io.rdf.RDFFileManager
import insight_centre.aceis.io.streams.brut.{CSPARQLSensorStream, _}
import insight_centre.aceis.observations.SensorObservation
import insight_centre.aceis.{MsgObj, RDFTableSer}
import io._
import jade.core.behaviours.{CyclicBehaviour, OneShotBehaviour}
import jade.core.{AID, Agent}
import jade.domain.FIPAAgentManagement.{AMSAgentDescription, DFAgentDescription, SearchConstraints, ServiceDescription}
import jade.domain.{AMSService, DFService, FIPAException}
import jade.lang.acl.ACLMessage
import jade.wrapper.StaleProxyException
import org.slf4j.LoggerFactory

class GeneriqueAgentCsparqlSpa extends Agent {

  private[agents] var agentDest : AID = null
  val logger = LoggerFactory.getLogger(classOf[GeneriqueAgentCsparqlSpa])
  private[agents] var streamGenerique: MessageToStreamConverterGenerique = null
  private[agents] var engine: CsparqlEngineImpl = null
  private[agents] var streamQuery : String = null

  val sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss")
  var start = null
  var end = null
  val startedStreams = new util.HashSet[String]
  val startedStreamsSet = new util.HashMap[String, MessageToStreamConverterGenerique]
  var er: EventRepository = null
  private var frequency = 1.0
  private var rate = 1.0 // stream rate factor
  private val streams = "streams"
  var dataset:String = "SensorRepository.n3"
  var qid: String = null

  override def setup(): Unit = {
    logger.info("Lancement Agent: " + this.getLocalName)
    val args = getArguments
    qid = args(0).toString
    streamQuery = args(1).toString
    er = args(2).asInstanceOf[EventRepository]
    EnregistrerServiceDF()

    addBehaviour(new OneShotBehaviour() {
      override def action(): Unit = {
        val ac = getContainerController

        /** Detecter les noms streams */
        val streamNames: util.List[String] = getStreamFileNamesFromQuery(streamQuery)

        /** Déterminer la classe streamer à lancer pour chaque Stream **/
        streamNames.forEach(sn => {
          val uri = RDFFileManager.defaultPrefix + sn.split("\\.")(0)
          val path = streams + "/" + sn
          if (!startedStreams.contains(uri)) {
            startedStreams.add(uri)
            var css : CSPARQLSensorStream = null
            val ed : EventDeclaration = er.getEds.get(uri)
            if (ed.getEventType.contains("traffic")) {
              css = new CSPARQLAarhusTrafficStream(uri, path, ed, start, end)
              var str =  new MessageToStreamConverterTraffic(uri, ed)
              startedStreamsSet.put(uri, str)
              streamGenerique = str
            }
            else if (ed.getEventType.contains("pollution")) {
              css = new CSPARQLAarhusPollutionStream(uri, path, ed, start, end)
              startedStreamsSet.put(uri, new MessageToStreamConverterPollution(uri, ed))
            }
            else if (ed.getEventType.contains("weather")) {
              css = new CSPARQLAarhusWeatherStream(uri, path, ed, start, end)
              startedStreamsSet.put(uri, new MessageToStreamConverterWeather(uri, ed))
            }
            else if (ed.getEventType.contains("location")) {
              css = new CSPARQLLocationStream(uri, path, ed)
              startedStreamsSet.put(uri, new MessageToStreamConverterLocation(uri, ed))
            }
            else if (ed.getEventType.contains("parking")) {
              css = new CSPARQLAarhusParkingStream(uri, path, ed, start, end)
              startedStreamsSet.put(uri, new MessageToStreamConverterParking(uri, ed))
            }
            else throw new Exception("Sensor type not supported.")
            css.setRate(rate)
            css.setFreq(frequency)

            val nickNameAgentSimulation = sn //"simulation_" + sn

            //if(!addAgentToExistingStreamer(getAgent, nickNameAgentSimulation)){
            try{
              /** Lancer un agent streamer */
              val sensingAgent = ac.createNewAgent(nickNameAgentSimulation, "agents.sensing.StreamCityAgent", Array.apply(getAID, css))
              sensingAgent.start()
              Thread.sleep(8000)
            }
            catch{
              /** une erreur se produit si un agent qui porte l même nom existe - dans ce cas, on demande à l'agent existant d'envoyer les datas à cet agent de processing*/
              case e: StaleProxyException => {
                val message = new ACLMessage(ACLMessage.SUBSCRIBE)
                message.addReceiver(new AID(nickNameAgentSimulation, AID.ISLOCALNAME))
                message.setContent(getAID.getLocalName)
                send(message)
              }
            }
          }
        })
      }
    })


    /** Lancer le traitement => Simulation Stream et execution requete */
    addBehaviour(new OneShotBehaviour() {
      override def action(): Unit = {
        initialisationCSPARQL()
      }
    })

    addBehaviour(new CyclicBehaviour() {
      override def action(): Unit = {
        val msg = receive
        if (msg != null) {
          if (msg.hasByteSequenceContent) {
            logger.debug("received msg {} => sender {} " + getAID.getLocalName + " ___ " + msg.getSender)
            val so = msg.getContentObject.asInstanceOf[SensorObservation]
            startedStreamsSet.get(so.getIri).convertMsg(so)
          }
          else {
            val so = msg.getContent
            if (!so.equals("OK")) {
              agentDest = new AID(so, AID.ISLOCALNAME);
              val reply: ACLMessage = msg.createReply
              reply.setPerformative(ACLMessage.AGREE)
              reply.setContent("OK")
              send(reply)
            }
          }
        }
        else {
          block()
        }
      }
    })
  }

  def addAgentToExistingStreamer(agent: Agent, nickNameAgentSimulation:String ): Boolean = {
    var flagAdd = false
    var agents: Array[AMSAgentDescription] = null
    try {
      val c = new SearchConstraints
      c.setMaxResults(-1L)
      agents = AMSService.search(agent, new AMSAgentDescription, c)
    } catch {
      case e: Exception =>
        System.out.println("Erreur " + e)
        e.printStackTrace()
    }

    System.out.println("----- Agent AMS Lister ----- ")
    var i = 0
    while ( {
      i < agents.length
    }) {
      val agentID = agents(i).getName
      System.out.println("    " + i + ": " + agentID.getLocalName)
      if (agentID.getLocalName.eq(nickNameAgentSimulation)) {
        System.out.print("Already exist ---->  ", agentID.getLocalName)
        val message = new ACLMessage(ACLMessage.INFORM)
        message.addReceiver(agentID)
        message.setContentObject(getAID)
        send(message)
        flagAdd = true
      }

      i += 1
    }
    flagAdd
  }


  private def EnregistrerServiceDF(): Unit = {
    /** Déclaration d'un service + lien avec l'agent et l'enregistrer dans DF */
    val dfd = new DFAgentDescription
    dfd.setName(getAID)
    val sd = new ServiceDescription
    val typeAgent = this.getLocalName.split("_Agent")(0)
    sd.setType(typeAgent + "-service")
    sd.setName(typeAgent + "-Agent")
    dfd.addServices(sd)
    try
      DFService.register(this, dfd)
    catch {
      case fe: FIPAException =>
        fe.printStackTrace()
    }
  }

  val task = new Runnable {
    def run() =  {
      //System.gc()
      val rt = Runtime.getRuntime
      val usedMB = (rt.totalMemory - rt.freeMemory) / 1024.0 / 1024.0
      logger.info("memory consumption => {}", usedMB)
      //startedStreamsSet.forEach((uri, st) => st.profilingData().printProfiling())
    }
  }

  def initialisationCSPARQL(): Unit = {
    try {
      //Create csparql engine instance
      engine = new CsparqlEngineImpl
      //Initialize the engine instance
      engine.initialize()
      // register all stream
      startedStreamsSet.forEach((uri, st) => engine.registerStream(st))

      val cqrp = engine.registerQuery(streamQuery)
      /**
        * CityBench Performance monitor-*/
      //val cro = new CSPARQLResultObserver(qid +"-" + UUID.randomUUID)
      //engine.registerStream(cro)
      //cqrp.addObserver(cro) /**/

      /** Send to REASONER */
      val co = new MessageFormatter
      cqrp.addObserver(co.asInstanceOf[GenericObserver[RDFTable]])

      //import java.util.concurrent.Executors
      //val executorService = Executors.newSingleThreadScheduledExecutor
      //executorService.scheduleAtFixedRate(task, 10, 10, TimeUnit.SECONDS)

      /** TO SHOW RESULT OF QUERY IN CONSOLE*/
      //cqrp.addObserver(new ConsoleFormatter)

    } catch {
      case e: Exception =>
        logger.error(e.getMessage, e)
    }
  }


  @throws[Exception]
  def getStreamFileNamesFromQuery(query: String): util.List[String] = {
    val resultSet = new util.HashSet[String]
    val streamSegments = query.trim.split("stream")
    if (streamSegments.length == 1) throw new Exception("Error parsing query, no stream statements found for: " + query)
    else {
      var i = 1
      while ( {
        i < streamSegments.length
      }) {
        val indexOfLeftBracket = streamSegments(i).trim.indexOf("<")
        val indexOfRightBracket = streamSegments(i).trim.indexOf(">")
        val streamURI = streamSegments(i).substring(indexOfLeftBracket + 2, indexOfRightBracket + 1)
        logger.info("Stream detected: " + streamURI)
        resultSet.add(streamURI.split("#")(1) + ".stream")

        {
          i += 1
        }
      }
    }

    val results = new util.ArrayList[String]
    results.addAll(resultSet)
    return results
  }

  @SerialVersionUID(100L)
  class MessageFormatter() extends GenericObserver[RDFTable] with Serializable {

    override def update(o: GenericObservable[RDFTable], arg: RDFTable): Unit = {
      val q: RDFTableSer = RDFTableSer.toRDFTableSer(arg)
      if(agentDest != null) {


        logger.info("------- Sending to: " + agentDest.getLocalName + "\n" + q.toString + "\n" + q.size + " results at SystemTime=[" + System.currentTimeMillis + "]--------")
        if (!q.isEmpty) {
          var msgObject = new MsgObj("add", q)
          val message = new ACLMessage(ACLMessage.INFORM)
          message.addReceiver(agentDest)
          message.setContentObject(msgObject)
          send(message)
        }
      }
      else {
        logger.info("Results: " + q.toString + "\n" + q.size + " results at SystemTime=[" + System.currentTimeMillis + "]--------")

      }
    }
  }

}
