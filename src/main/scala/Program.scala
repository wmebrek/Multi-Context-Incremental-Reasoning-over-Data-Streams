import insight_centre.aceis.io.EventRepository
import insight_centre.aceis.io.rdf.RDFFileManager
import insight_centre.aceis.observations.SensorObservation
import insight_centre.aceis.utils.test.PerformanceMonitor
import com.hp.hpl.jena.reasoner.ReasonerRegistry
import jade.core.{Profile, ProfileImpl, Runtime}
import jade.wrapper.StaleProxyException
import org.deri.cqels.engine.ExecContext
import org.slf4j.LoggerFactory

import java.io.File
import java.net.URL
import java.text.SimpleDateFormat
import java.util
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import scala.io.Source


object Program {
  private[this] var _obMap: ConcurrentHashMap[String, SensorObservation] = new ConcurrentHashMap[String, SensorObservation]
  var tempContext: ExecContext = null

  def obMap: ConcurrentHashMap[String, SensorObservation] = _obMap

  private[this] var _pm: PerformanceMonitor = null

  def pm: PerformanceMonitor = _pm

  def pm_=(value: PerformanceMonitor): Unit = {
    _pm = value
  }

  def obMap_=(value: ConcurrentHashMap[String, SensorObservation]): Unit = {
    _obMap = value
  }

  def main(args: Array[String]): Unit = {
    val queryMap: util.Map[String, String] = new util.HashMap[String, String]
    val duration = 900000

    val sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss")
    var start = null
    var end = null
    val startedStreams = new util.HashSet[String]
    var er: EventRepository = null
    var frequency = 1.0
    var rate = 1.0 // stream rate factor
    val streams = "streams"
    var dataset: String = "SensorRepository.n3"
    var configuration: Config = null
    var resultName = UUID.randomUUID + " r=" + rate + ",f=" + frequency + ",dup="
    +1 + ",e= Csparql"

    val runtime = Runtime.instance
    val profile = new ProfileImpl
    val logger = LoggerFactory.getLogger("Program")


    /*** Lire les paramètres passés en arguments au program **/
    parseParameters(args) match {
      case Some(config) => {
        configuration = config

      }
      case None => throw new RuntimeException("Could not parse all arguments")
    }


    /** Charger les datasets en mémoire **/
    try {
      logger.info("Initialisation - load dataset")
      tempContext = RDFFileManager.initializeCQELSContext(dataset, ReasonerRegistry.getRDFSReasoner)

      er = RDFFileManager.buildRepoFromFile(0)
    }
    catch {
      case e: Exception =>
        e.printStackTrace()
        System.exit(0)
    }
    finally {
      logger.info("End initialisation")
    }


    /** SMA Initialisation **/
    logger.info("Run multi-agents system")
    profile.setParameter(Profile.MAIN_HOST, "localhost") // serveur local
    profile.setParameter(Profile.GUI, "true") // interface graphique
    val containerController = runtime.createMainContainer(profile)

    try
        for (query <- configuration.queries) {
          val qid: String = query.getName.split("\\.")(0)
          var agentClass = ""
          var queryTxt = readFile(query.toURI.toURL)

          import java.util.regex.Pattern
          /** Patterns pour détecter le moteur de requete à lancer */
          //select(\s([a-zA-Z]+\s)+) where \{\s+stream
          val csparqlPattern = Pattern.compile("(?=.*select|construct)(?=.*step)", Pattern.MULTILINE | Pattern.CASE_INSENSITIVE | Pattern.DOTALL)
          val cqelsPattern = Pattern.compile("(?=.*select)(?=.*stream)(?=.*range)", Pattern.MULTILINE |Pattern.CASE_INSENSITIVE | Pattern.DOTALL)
          val spaseqPattern = Pattern.compile("(?=.*select)(?=.*within)", Pattern.MULTILINE |Pattern.CASE_INSENSITIVE | Pattern.DOTALL)

          if(csparqlPattern.matcher(queryTxt).find()) {
              queryTxt = "REGISTER QUERY " + qid + " AS " + queryTxt
              agentClass = "agents.processing.GeneriqueAgentCsparqlSpa"
          }
          else if(spaseqPattern.matcher(queryTxt).find()){
            agentClass = "agents.processing.GeneriqueAgentSpaseq"
          }
          else if(spaseqPattern.matcher(queryTxt).find()){
            agentClass = "agents.processing.GeneriqueAgentCQels2"
          }
          else {
            agentClass = "agents.processing.GeneriqueAgentCQels2"

            logger.error("No pattern match with your query")

          }

          queryMap.put(qid, queryTxt)
          containerController.createNewAgent(qid + "_Agent",
            agentClass,
            Array.apply(qid, queryTxt, er, tempContext)).start()
          //Thread.sleep(30000)
        }
    catch {
      case e: StaleProxyException =>
        e.printStackTrace()
    }

    /** RUN ONLY TICKER ENGINE AND THE TMS IN ARGS (ATMS/JTMS) */
    if(configuration.tms != "none" && configuration.rules != null){
      configuration.rules.foreach(f => {
        val filename= f.getName.replace(".lars", "");
        containerController.createNewAgent("tickerEngineAgent" + filename,
          "agents.reasoning.EngineAgent",
          Array.apply(configuration.tms, f.getAbsolutePath, filename))
          .start()
      });
    }


    /** DISABLED PERFORMANCE MONITOR*/
    /*pm = new PerformanceMonitor(queryMap, duration, 1, resultName, containerController)

    //CompletableFuture.runAsync(() => {
      new Thread(pm).start()
    //})
    */
  }

  /** Fonction pour lire le contenu d'un fichier*/
  private def readFile(url: URL): String = {
    val source = Source.fromURL(url)
    try source.mkString finally source.close()
  }

  /** Fonction qui vérifie qu'on a bien des requetes en argument*/
  case class Config(queries: Seq[File] = null, tms: String = "JTMS", rules: Seq[File] = null) {
    def parseQueriesFromFiles() = {
      if (queries.isEmpty) {
        throw new RuntimeException("mergedProgram argument missing")
      }
      queries
    }
  }


  /** Fonction qui lit les arguments du program */
  def parseParameters(args: Array[String]) = {
    val parser = new scopt.OptionParser[Config]("scopt") {
      head("scopt", "3.x")

      opt[Seq[File]]('q', "queries").required().valueName("<file>,<file>,...").
        action((x, c) => c.copy(queries = x)).
        text("Queries files required")

      opt[String]('t', "tms").optional().valueName("ATMS | JTMS").
        action((x, c) => c.copy(tms = x)).
        text("Possible log levels: none | ATMS | JTMS")


      opt[Seq[File]]('r', "rules").optional().valueName("<file>,<file>,...").
        action((x, c) => c.copy(rules = x)).
        text("Rules Ticker files")

      help("help").
        text("Specify init parameters for running the engine")

    }

    parser.parse(args, Config())
  }

}
