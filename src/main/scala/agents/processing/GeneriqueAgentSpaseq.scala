package agents.processing

import java.text.SimpleDateFormat
import java.util
import java.util.concurrent._

import edu.telecom.stet.cep.events.{GraphEvent, MappedEvent}
import edu.telecomstet.cep.dictionary.optimised.DictionaryOpImpl
import edu.telecomstet.cep.engine.optimised.SpsaseqQueryProcessor
import edu.telecomstet.cep.nfahelpers2.NFA
import edu.telecomstet.cep.query.parser.QueryParser
import eu.larkc.csparql.common.RDFTable
import eu.larkc.csparql.common.streams.format.{GenericObservable, GenericObserver}
import eu.larkc.csparql.engine.CsparqlEngineImpl
import insight_centre.aceis.eventmodel.EventDeclaration
import insight_centre.aceis.io.EventRepository
import insight_centre.aceis.io.rdf.RDFFileManager
import insight_centre.aceis.io.streams.brut._
import insight_centre.aceis.observations.SensorObservation
import insight_centre.aceis.{MsgObj, RDFTableSer, RDFTupleSer}
import io._
import jade.core.behaviours.{CyclicBehaviour, OneShotBehaviour}
import jade.core.{AID, Agent}
import jade.domain.FIPAAgentManagement.{AMSAgentDescription, DFAgentDescription, SearchConstraints, ServiceDescription}
import jade.domain.{AMSService, DFService, FIPAException}
import jade.lang.acl.ACLMessage
import jade.wrapper.StaleProxyException
import org.semanticweb.yars.nx.Resource
import org.semanticweb.yars.nx.parser.NxParser
import org.slf4j.LoggerFactory

class GeneriqueAgentSpaseq extends Agent {

  private[agents] var agentDest : AID = null
  val logger = LoggerFactory.getLogger(classOf[GeneriqueAgentSpaseq])
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
  var queue: ArrayBlockingQueue[GraphEvent] = null
  var dictimpl = new DictionaryOpImpl
  var stos: Long = 1L
  var spaseqEngine: SpsaseqQueryProcessor = null

  override def setup(): Unit = {
    logger.info("Lancement Agent: " + this.getLocalName)
    val args = getArguments
    qid = args(0).toString
    streamQuery = args(1).toString
    er = args(2).asInstanceOf[EventRepository]
    var subscribeToReceiveStream = new util.HashSet[String]
    EnregistrerServiceDF()

    addBehaviour(new OneShotBehaviour() {
      override def action(): Unit = {
        val ac = getContainerController
        dictimpl = new DictionaryOpImpl


        val streamNames: util.List[String] = getStreamFileNamesFromQuery(streamQuery)
        streamNames.forEach(sn => {

          val uri = RDFFileManager.defaultPrefix + sn.split("\\.")(0)
          dictimpl.addResourcePersistant(new Resource(uri));
          val path = streams + "/" + sn
          if (!startedStreams.contains(uri)) {
            startedStreams.add(uri)
            var css : CSPARQLSensorStream = null
            val ed : EventDeclaration = er.getEds.get(uri)
            if(ed != null) {
              if (ed.getEventType.contains("traffic")) {
                css = new CSPARQLAarhusTrafficStream(uri, path, ed, start, end)
                var str = new MessageToStreamConverterTraffic(uri, ed)
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

              val nickNameAgentSimulation = "simulation_" + sn

              //if(!addAgentToExistingStreamer(getAgent, nickNameAgentSimulation)){
              try {
                val sensingAgent = ac.createNewAgent("simulation_" + sn, "agents.sensing.StreamCityAgent", Array.apply(getAID, css))
                sensingAgent.start()
              }
              catch {
                case e: StaleProxyException => {
                  val message = new ACLMessage(ACLMessage.SUBSCRIBE)
                  message.addReceiver(new AID(nickNameAgentSimulation, AID.ISLOCALNAME))
                  message.setContent(getAID.getLocalName)
                  send(message)
                }
              }
            }

            else {
              subscribeToReceiveStream.add(sn)
            }

          }

        })

      }
    })


    /** Subscribe like a consumer in another agent to receivData */
    addBehaviour(new OneShotBehaviour() {
      override def action(): Unit = {

          while (
            !subscribeToReceiveStream.isEmpty
          ) {
            subscribeToReceiveStream.forEach(sn => {
              var agentSensing = searchAgentByService(sn.split("\\.")(0)+"-service")
              if(agentSensing != null) {
                subscribeToReceiveStream.remove(sn)
                val message = new ACLMessage(ACLMessage.SUBSCRIBE)
                message.addReceiver(agentSensing)
                message.setContent(getAID.getLocalName)
                send(message)
              }
            })
        }
      }
    })

    /** Lancer le traitement => Simulation Stream et execution requete */
    addBehaviour(new OneShotBehaviour() {
      override def action(): Unit = {
        stos = System.nanoTime()
        initialisationSpaseq()
      }
    })
    addBehaviour(new CyclicBehaviour() {
      override def action(): Unit = {
        val msg = receive
        if (msg != null) {
          if(msg.hasByteSequenceContent) {
            if (msg.getContentObject.isInstanceOf[MsgObj]) {
              val tuples = msg.getContentObject.asInstanceOf[MsgObj].getData.asInstanceOf[RDFTableSer].getTuples
              convertMsg(tuples, queue, dictimpl, stos)
            }
            else {
              val so = msg.getContentObject.asInstanceOf[SensorObservation]
              startedStreamsSet.get(so.getIri).convertMsg(so, queue, dictimpl, stos)
            }
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

    addBehaviour(new OneShotBehaviour() {
      override def action(): Unit = {

        var runnable = new Runnable() {
          def run():Unit ={
            logger.info("res => {}", spaseqEngine.getResultSet)
            System.gc()
            val rt = Runtime.getRuntime
            val usedMB = (rt.totalMemory - rt.freeMemory) / 1024.0 / 1024.0
            logger.info("memory consumption => {}", usedMB)
          }
        }
        var service = Executors.newSingleThreadScheduledExecutor();
        service.scheduleWithFixedDelay(runnable, 10L, 10L , TimeUnit.SECONDS);

        CompletableFuture.runAsync(() => {
          while(true) {
            val res = spaseqEngine.getResultqueue.take()
            // to show spaseq result
            logger.info("res => {}", res)
          }
        })

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
        //e.printStackTrace()
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

  def convertMsg(data: util.List[RDFTupleSer], queue:  ArrayBlockingQueue[GraphEvent], dicImpl: DictionaryOpImpl, sostr: Long): Unit = {
    var mapE = new Array[MappedEvent](8)
    var i = 0
    data.forEach(st => {
      logger.debug(" Streaming: " + st.toString)
      //val q: RdfQuadruple = new RdfQuadruple(st.getSubject.toString, st.getPredicate.toString, st.getObject.toString, System.currentTimeMillis)
      var obj = toN3(st.getFields.get(2))
      var suj = toN3(st.getFields.get(0))


      var line =  suj + " <" + st.getFields.get(1) + "> " + obj + " ."
      logger.debug("line => {}", line)

      var node = NxParser.parseNodes(line)
      if(i < mapE.size - 1){
        mapE(i) = new MappedEvent(node, dicImpl)
        i = i+1
      }
      else {
        mapE(i) = new MappedEvent(node, dicImpl)
        var gr = new GraphEvent(1, System.nanoTime() - sostr, dicImpl, 1, mapE);
        queue.put(gr)
        i = 0

      }

    })

  }

  def toN3(st : String ): String = {
    var res = ""
    if (st.contains("http")) {
      res = "<" + st.toString + ">"
    }
    else if(st.contains("^^")) {
      res = "\"" + st.toString.replace("^^", "\"^^<") + ">"
    }
    else {
      res = "_:"+st
    }
    res
  }


  private def EnregistrerServiceDF(): Unit = {
    /** Déclaration d'un service + lien avec l'agent et l'enregistrer dans DF */
    val dfd = new DFAgentDescription
    dfd.setName(getAID)
    val sd = new ServiceDescription
    val typeAgent = this.getLocalName.split("_Agent")(0)
    sd.setType(typeAgent + "-service")
    sd.setName(typeAgent + "-spaseq-Agent")
    dfd.addServices(sd)
    try
      DFService.register(this, dfd)
    catch {
      case fe: FIPAException =>
        fe.printStackTrace()
    }
  }

  def initialisationSpaseq(): Unit = {
    try {
      queue = new ArrayBlockingQueue[GraphEvent](100)
      val descriptor = QueryParser.parse(streamQuery, dictimpl, "")
      val latch = new CountDownLatch(2)

      spaseqEngine = new SpsaseqQueryProcessor(new NFA(descriptor.getPattData, descriptor.getNfaDataList), descriptor.getNfaDataList, dictimpl, descriptor.getConstRules, queue, latch)
      //spaseqEngine.getResultqueue.
      new Thread(spaseqEngine).start()


    } catch {
      case e: Exception =>
        logger.error(e.getMessage, e)
    }
  }


  @throws[Exception]
  def getStreamFileNamesFromQuery(query: String): util.List[String] = {
    val resultSet = new util.HashSet[String]
    val streamSegments = query.trim.split("stream|STREAM")
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

  def searchAgentByService(agentId: String): AID = {
    val dfd = new DFAgentDescription
    val sd = new ServiceDescription
    sd.setType(agentId)
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
  class MessageFormatter(val agent: AID) extends GenericObserver[RDFTable] with Serializable {
    private[agents] var reasonerAgent : AID = agent

    override def update(o: GenericObservable[RDFTable], arg: RDFTable): Unit = {
      val q: RDFTableSer = RDFTableSer.toRDFTableSer(arg)

      logger.info("------- Sending to: " + reasonerAgent.getLocalName + "\n" +q.toString + "\n" + q.size + " results at SystemTime=[" + System.currentTimeMillis + "]--------")
      if (!q.isEmpty){
        var msgObject = new MsgObj("add", q)
        val message = new ACLMessage(ACLMessage.INFORM)
        message.addReceiver(reasonerAgent)
        message.setContentObject(msgObject)
        send(message)
      }
    }
  }

}
