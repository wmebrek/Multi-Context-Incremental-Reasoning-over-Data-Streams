package agents.processing

import java.text.SimpleDateFormat
import java.util

import insight_centre.aceis.RDFTableSer
import insight_centre.aceis.eventmodel.EventDeclaration
import insight_centre.aceis.io.EventRepository
import insight_centre.aceis.io.rdf.RDFFileManager
import insight_centre.aceis.io.streams.cqels._
import insight_centre.aceis.observations.SensorObservation
import io._
import jade.core.behaviours.{CyclicBehaviour, OneShotBehaviour}
import jade.core.{AID, Agent}
import jade.domain.FIPAAgentManagement.{AMSAgentDescription, DFAgentDescription, SearchConstraints, ServiceDescription}
import jade.domain.{AMSService, DFService, FIPAException}
import jade.lang.acl.ACLMessage
import jade.wrapper.StaleProxyException
import org.deri.cqels.data.Mapping
import org.deri.cqels.engine.{ContinuousListener, ExecContext}
import org.slf4j.LoggerFactory

class GeneriqueAgentCQels extends Agent {

  private[agents] var agentTicker : AID = null
  val logger = LoggerFactory.getLogger(classOf[GeneriqueAgentCQels])
  private[agents] var streamGenerique: MessageToStreamConverterGenerique = null
  private[agents] var streamQuery : String = null
  var cqelsContext: ExecContext = null
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
    cqelsContext = args(3).asInstanceOf[ExecContext]
    EnregistrerServiceDF()

    addBehaviour(new OneShotBehaviour() {
      override def action(): Unit = {
        val ac = getContainerController

        val streamNames: util.List[String] = getStreamFileNamesFromQuery(streamQuery)
        streamNames.forEach(sn => {
          val uri = RDFFileManager.defaultPrefix + sn.split("\\.")(0)
          val path = streams + "/" + sn
          if (!startedStreams.contains(uri)) {
            startedStreams.add(uri)
            var css : CQELSSensorStream = null
            val ed : EventDeclaration = er.getEds.get(uri)
            if (ed.getEventType.contains("traffic")) {
              css = new CQELSAarhusTrafficStream(cqelsContext, uri, path, ed, start, end)
              var str =  new MessageToStreamConverterTraffic(uri, ed)
              startedStreamsSet.put(uri, str)
              streamGenerique = str
            }
            else if (ed.getEventType.contains("pollution")) {
              css = new CQELSAarhusPollutionStream(cqelsContext, uri, path, ed, start, end)
              startedStreamsSet.put(uri, new MessageToStreamConverterPollution(uri, ed))
            }
            else if (ed.getEventType.contains("weather")) {
              css = new CQELSAarhusWeatherStream(cqelsContext, uri, path, ed, start, end)
              startedStreamsSet.put(uri, new MessageToStreamConverterWeather(uri, ed))
            }
            else if (ed.getEventType.contains("location")) {
              css = new CQELSLocationStream(cqelsContext, uri, path, ed)
              startedStreamsSet.put(uri, new MessageToStreamConverterLocation(uri, ed))
            }
            else if (ed.getEventType.contains("parking")) {
              css = new CQELSAarhusParkingStream(cqelsContext, uri, path, ed, start, end)
              startedStreamsSet.put(uri, new MessageToStreamConverterParking(uri, ed))
            }
            else throw new Exception("Sensor type not supported.")
            css.setRate(rate)
            css.setFreq(frequency)

            val nickNameAgentSimulation = "simulation_" + sn

            //if(!addAgentToExistingStreamer(getAgent, nickNameAgentSimulation)){
            try{
              val sensingAgent = ac.createNewAgent("simulation_" + sn, "agents.sensing.StreamCityAgent", Array.apply(getAID, css))
              sensingAgent.start()
            }
            catch{
              case e: StaleProxyException => {
                val message = new ACLMessage(ACLMessage.INFORM)
                message.addReceiver(new AID(nickNameAgentSimulation, AID.ISLOCALNAME))
                message.setContent(getAID.getLocalName)
                send(message)
              }
            }
          }
        })

      }
    })


    /** Chercher le service Ticker */
    /*addBehaviour(new OneShotBehaviour() {
      override def action(): Unit = {
        while (
          agentTicker == null
        )
          agentTicker = searchTicker
      }
    })*/

    /** Lancer le traitement => Simulation Stream et execution requete */
    addBehaviour(new OneShotBehaviour() {
      override def action(): Unit = {
        initialisationCQELS()
      }
    })
    addBehaviour(new CyclicBehaviour() {
      override def action(): Unit = {
        val msg = receive
        if (msg != null) {
          logger.debug("actual {} => sender {} " + getAID.getLocalName + " ___ " + msg.getSender)
          val so = msg.getContentObject.asInstanceOf[SensorObservation]
          startedStreamsSet.get(so.getIri).convertMsg(so)
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
    val typeAgent = this.getLocalName.split("Agent")(0)
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

  def initialisationCQELS(): Unit = {
    try {
      //Create CQELS engine instance
      //val crl = new CQELSResultListener(qid)
      //logger.info("Registering result observer: " + crl.getUri)
      val cs = cqelsContext.registerSelect(streamQuery)
      val ms = new MessageFormatter(null)
      //cs.register(crl)
      cs.register(ms)
      // register all stream
      //startedStreamsSet.forEach((uri, st) => engine.registerStream(st))

      /**
        * CityBench Performance monitor
      val cro = new CQELSResultObserver(qid +"-" + UUID.randomUUID)
      logger.info("Registering result observer: " + cro.getIRI)
      engine.registerStream(cro)

      cqrp.addObserver(cro)*/

      /** Send to REASONER */
      /*val co = new MessageFormatter(agentTicker)
      cqrp.addObserver(co.asInstanceOf[GenericObserver[RDFTable]])*/
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
          i += 1; i - 1
        }
      }
    }

    val results = new util.ArrayList[String]
    results.addAll(resultSet)
    return results
  }

  def searchTicker: AID = {
    val dfd = new DFAgentDescription
    val sd = new ServiceDescription
    sd.setType("tickerEngine-service")
    dfd.addServices(sd)
    try {
      var result = new Array[DFAgentDescription](0)
      result = DFService.search(this, dfd)
      if (result.length > 0) {
        logger.info(" " + result(0).getName)
        return result(0).getName
      }
    } catch {
      case e: FIPAException =>
        e.printStackTrace()
    }
    null
  }

  @SerialVersionUID(100L)
  class MessageFormatter(val agent: AID) extends ContinuousListener with Serializable {
    private[agents] var reasonerAgent : AID = agent

    override def update(mapping:Mapping): Unit = {
      val q: RDFTableSer = RDFTableSer.toRDFTableSer(mapping, cqelsContext)
      logger.info("res => {} ", q)
      /*if (!mapping.isEmpty){
        var msgObject = new MsgObj("add", q)
        val message = new ACLMessage(ACLMessage.INFORM)
        message.addReceiver(reasonerAgent)
        message.setContentObject(msgObject)
        send(message)
      }*/
    }

}

}
