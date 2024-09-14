package agents.reasoning

import java.io.File
import java.util
import java.util.concurrent.{Executors, TimeUnit}

import com.typesafe.scalalogging.Logger
import common.Util
import core.lars.{ClockTime, Format, LarsProgram, TimePoint}
import core.{Atom, Predicate}
import engine.connectors._
import engine.{Change, ChangeBasedWriting, Engine, OutputTiming, OutputWriting, OutputWritingEvery, Signal, SignalBasedWriting, Time, TimeBasedWriting}
import insight_centre.aceis.{MsgObj, RDFTableSer}
import jade.core.behaviours.{CyclicBehaviour, OneShotBehaviour, TickerBehaviour}
import jade.core.{AID, Agent}
import jade.domain.FIPAAgentManagement.{DFAgentDescription, ServiceDescription}
import jade.domain.{DFService, FIPAException}
import jade.lang.acl.ACLMessage
import jade.wrapper.StaleProxyException
import reasoner.config.ReasonerChoice.ReasonerChoice
import reasoner.config.{BuildReasoner, EvaluationModifier, ReasonerChoice}
import reasoner.parser.LarsParser
import reasoner.{Reasoner, Result}

import scala.collection.JavaConverters
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

/**
  * Created by FM on 10.11.16.
  */
case class EngineAgent() extends Agent with IReasonerAgent {

  registerO2AInterface(classOf[IReasonerAgent], this)

  var clockTime: ClockTime = null

  var outputTiming: OutputTiming = null

  var reasoner: Reasoner = null

  val logger = Logger[EngineAgent]

  val clockTimeUnitWritten = null

  type ResultCallback = (Result, TimePoint) => Unit

  private implicit val executor = ExecutionContext.fromExecutor(Executors.newSingleThreadExecutor())
  val timer = new java.util.Timer()

  var resultCallbacks: Seq[ResultCallback] = List()

  var tmsAgent: AID = null


  var subscribeToReceiveStream = new util.HashSet[String]

  //protected var listeFait = new util.ArrayList[String]()

  @volatile private var EngineAgentTimePoint: TimePoint = TimePoint(0)

  @volatile private var outputWriting: OutputWriting = ChangeBasedWriting
  /*outputTiming match {
    case Change => ChangeBasedWriting
    case Time(None) => TimeBasedWriting(clockTime, clockTime)
    case Time(Some(interval)) => TimeBasedWriting(interval, clockTime)
    case Signal(interval) => SignalBasedWriting(interval)
  }*/

  //def convertToTimePoint(duration: Duration): TimePoint = Duration(duration.toMillis / clockTime.toMillis, clockTime.unit).length

  def convertToTime(timePoint: TimePoint) = {
    val time = Duration(timePoint.value * clockTime.toMillis, TimeUnit.MILLISECONDS).toUnit(clockTime.unit).intValue()
    //Duration(time,clockTime.unit).toUnit(clockTime._2).intValue()
    time
  }

  private def updateClock(): Unit = {
    EngineAgentTimePoint = EngineAgentTimePoint + 1

    outputWriting match {
      case timeBasedWriting: TimeBasedWriting if shouldWrite(timeBasedWriting, EngineAgentTimePoint) => {
        val timePoint = EngineAgentTimePoint
        Future {
          evaluateModel(timePoint)
        }
      }
      case `ChangeBasedWriting` => Future { //TODO hb why not test (i) shouldWrite here, and (ii) other modes (cases)?
        evaluateModel(EngineAgentTimePoint)
      }
      case _ => /* noop*/
    }
  }

  // capture EngineAgentTimePoint (threading!)
  def evaluateModel(): Unit = evaluateModel(EngineAgentTimePoint)

  def evaluateModel(currentTimePoint: TimePoint): Unit = {
    val model = reasoner.evaluate(currentTimePoint)

    outputWriting match {
      case ChangeBasedWriting => {
        if (shouldWrite(ChangeBasedWriting, model))
          publishModel()
      }
      case _ => publishModel() //TODO other modes? shouldWrite?
    }

    def publishModel() = resultCallbacks.foreach(callback => callback(model, currentTimePoint))
  }



  def shouldWrite[TUpdate](outputWriting: OutputWritingEvery[TUpdate], update: TUpdate) = {
    val shouldWrite = outputWriting.shouldWriteAfterUpdate(update)
    outputWriting.registerUpdate(update)
    shouldWrite
  }

  def append(enteredTimePoint: Option[TimePoint], atoms: Seq[Atom]): Unit = {
    if (atoms.nonEmpty) {
      Future {
        // if we are not provided with an explicit user entered time-point
        // we add the atoms at the first time-point when they can be added in the EngineAgent
        // (which is determined by calling the code inside the future;
        // another strategy could be fixing the time-point at the moment when the atom arrives at the EngineAgent boundary,
        // but this would lead to adding atoms always in the past)
        val timePoint = enteredTimePoint.getOrElse(EngineAgentTimePoint)

        val timePassed = convertToTime(timePoint)

        if (Util.log_level == Util.LOG_LEVEL_DEBUG) {
          logger.debug(f"Received input ${atoms.mkString(", ")} at $timePassed${clockTimeUnitWritten} (t=$timePoint)")
        }

        reasoner.append(timePoint)(atoms: _*)

        outputWriting match {
          case ChangeBasedWriting => evaluateModel()
          case s: SignalBasedWriting => {
            if (shouldWrite(s, atoms)) {
              evaluateModel()
            }
          }
          case _ => /* noop */
        }
      }
    }
  }

//  def start(): Unit = {
//    timer.scheduleAtFixedRate(new TimerTask {
//      override def run(): Unit = updateClock()
//    }, clockTime.toMillis, clockTime.toMillis)
//
//    connectors.foreach(startable => new Thread(() => startable()).start())
//    // forces the caller thread to wait
//    Thread.currentThread().join()
//  }

  override def setup(): Unit = {
    val args = getArguments
    val typeTMS = args(0).toString
    /** Agents Sources of Ticker are identified by file rules name
      * ex: /src/ressource/Q1-Q2.lars => Q1-Q2 */
    //val rulesFileName = args(1).toString.split("/").reverse(0).stripSuffix(".lars")
    /** ex: Q1-Q2 => Q1 , Q2*/
    args(2).toString.split("-").foreach(ag => subscribeToReceiveStream.add(ag));

    EnregistrerServiceDF()

    val ac = getContainerController

    try {
      val createTmsAgent = ac.createNewAgent(typeTMS + "Agent", "agents.reasoning." + typeTMS + "Agent", null)
      createTmsAgent.start()
    } catch {
      case e: StaleProxyException => {
        System.out.println("Error - Agent " + typeTMS + " already exists")
      }
    }
    //tmsAgent = createTmsAgent
    //searchTms(typeTMS)
    tmsAgent = searchAgentByService(typeTMS+"-service")

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
              val message = new ACLMessage(ACLMessage.REQUEST)
              message.addReceiver(agentSensing)
              message.setContent(getAID.getLocalName)
              send(message)
            }
          })
        }
      }
    })


    parseParameters(Array("--program", args(1).toString ,"-f", "inferences", "-e", "change")) match {
      case Some(config) => {

        overrideLogLevel(config)

        val program = config.parseProgramFromFiles()
        checkTimeWindowCompatibility(program, config)
        clockTime = config.clockTime

        outputTiming = config.outputTiming

        reasoner = config.buildReasoner(program)
      }
    }

    addBehaviour(new TickerBehaviour(this, clockTime.toMillis) {
      override def onTick(): Unit = {
        updateClock()
      }
    })

    addBehaviour(new CyclicBehaviour() {
      override def action(): Unit = {
        val msg = receive
        if (msg != null && msg.hasByteSequenceContent) {
          try {
            val msgObj = msg.getContentObject.asInstanceOf[MsgObj]

            msgObj.getAction match {
              case "getModel" => {
                //val arr = msgObj.getData.asInstanceOf[Result]
                print("GetModel data received", msgObj.getData)
                return
              }
              case "add" =>
                val arr = msgObj.getData.asInstanceOf[RDFTableSer]
                val collec = formatMessage(arr)
                JavaConverters.asScalaIteratorConverter(collec.iterator).asScala.toSeq
                  .filter(_ != null)
                  .map(parseInput(_))
                  .takeWhile(_.nonEmpty)
                  .foreach(input => {
                    append(None, input)
                    /** Pour voir ls données en entrée de Ticker en provenance des agents processing
                      * println("-- INPUT: --Some data received: \n" + input)*/
                  })
                return
            }
          } catch {
            case exception: Exception => println("soucis engine agent ")
          }
        } else {
          print("Received response from Generique")
        }
        block()
      }
    })

  }

  /*def searchTms(typeTMS: String) : Unit = {
    val dfd = new DFAgentDescription
    val sd = new ServiceDescription
    sd.setType(typeTMS+"-service")
    dfd.addServices(sd)
    val c = new SearchConstraints
    c.setMaxResults(1L)

    try {
      var result = new Array[DFAgentDescription](0)
      while(result.length == 0) {
        result = DFService.search(this, dfd)

        if (result.length > 0) {
          System.out.println(" " + result(0).getName)

          tmsAgent = result(0).getName
        }
      }
    } catch {
      case e: FIPAException =>
        e.printStackTrace()
    }
  }*/

  def searchAgentByService(agentId: String): AID = {
    val dfd = new DFAgentDescription
    val sd = new ServiceDescription
    sd.setType(agentId)
    dfd.addServices(sd)
    try {
      var result = new Array[DFAgentDescription](0)

      while(result.length == 0) {
        result = DFService.search(this, dfd)
      }

      //result = DFService.search(this, dfd)
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

  def parseParameters(args: Array[String]) = {
    val parser = new scopt.OptionParser[Config]("scopt") {
      head("scopt", "3.x")

      opt[Seq[File]]('p', "program").required().valueName("<file>,<file>,...").
        action((x, c) => c.copy(programFiles = x)).
        text("Program file required")

      opt[ReasonerChoice]('r', "reasoner").optional().valueName("<reasoner>").
        action((x, c) => c.copy(reasoner = x)).
        text("Reasoning strategy required, possible values: " + ReasonerChoice.values)

      opt[Seq[String]]('f', "filter").optional().valueName("all | inferences | <predicate>,<predicate>,...").
        action((x,c) => c.copy(filter = x)).
        text("Possible filters: all | inferences | <predicate>,<predicate>,...")

      opt[Duration]('c', "clock").optional().valueName("<value><time-unit>").
        validate(d =>
          if (d.lteq(Duration.Zero))
            Left("clock time must be > 0")
          else
            Right((): Unit)
        ).
        action((x, c) => c.copy(clockTime = x)).
        text("valid units: ms, s, sec, min, h. eg: 10ms")

      opt[OutputTiming]('e', "outputEvery").
        optional().
        valueName("change | signal | time | <value>signals | <value><time-unit>").
        validate {
          case Signal(count) if count < 0 => Left("signal count must be > 0")
          case Time(Some(duration)) if duration.lteq(Duration.Zero) => Left("duration must be > 0")
          case _ => Right((): Unit)
        }.
        action((x, c) => c.copy(outputTiming = x)).
        text("valid units: ms, s, min, h. eg: 10ms")

      opt[Seq[Source]]('i', "input").optional().valueName("<source>,<source>,...").
        action((x, c) => c.copy(inputs = x)).
        text("Possible input sources: read from input with 'stdin', read from a socket with 'socket:<port>'")

      opt[Seq[Sink]]('o', "output").optional().valueName("<sink>,<sink>,...").
        action((x, c) => c.copy(outputs = x)).
        text("Possible output sinks: write to output with 'stdout', write to a socket with 'socket:<port>'")

      opt[String]('l', "loglevel").optional().valueName("none | info | debug").
        action((x, c) => c.copy(logLevel = x)).
        text("Possible log levels: none | info | debug")

      help("help").
        text("Specify init parameters for running the engine")

      checkConfig(c => {
        c.outputTiming match {
          case Time(Some(duration)) if duration.lt(c.clockTime) =>
            reportWarning("outputEvery: time interval is less than the engine clock time. The output time interval will be set to the engine unit")
          case _ =>
        }

        Right((): Unit)
      })
    }

    parser.parse(args, Config())
  }

  private val SignalPattern = "(\\d+)signals".r

  implicit val outputEveryRead: scopt.Read[OutputTiming] =
    scopt.Read.reads(s => s.toLowerCase match {
      case "change" => Change
      case "signal" => Signal()
      case "time" => Time()
      case SignalPattern(count) => Signal(count.toInt)
      case shouldBeTime => Time(Some(Duration.create(shouldBeTime)))
    })

  implicit val evaluationTypesRead: scopt.Read[ReasonerChoice.Value] =
    scopt.Read.reads(ReasonerChoice withName)

  implicit val evaluationModifierRead: scopt.Read[EvaluationModifier.Value] = scopt.Read.reads(EvaluationModifier withName)

  private val SocketPattern = "socket:(\\d+)".r
  implicit val inputSourcesRead: scopt.Read[Source] = scopt.Read.reads(s => s.toLowerCase match {
    case "stdin" => StdIn
    case SocketPattern(port) => SocketInput(port.toInt)
  })

  implicit val outputSinksRead: scopt.Read[Sink] = scopt.Read.reads(s => s.toLowerCase match {
    case "stdout" => StdOut
    case SocketPattern(port) => SocketOutput(port.toInt)
  })

  sealed trait Source
  object StdIn extends Source
  case class SocketInput(port: Int) extends Source

  sealed trait Sink
  object StdOut extends Sink
  case class SocketOutput(port: Int) extends Sink

  case class Config(programFiles: Seq[File] = null,
                    reasoner: ReasonerChoice = ReasonerChoice.incremental,
                    filter: Seq[String] = Seq("inferences"),
                    clockTime: ClockTime = 5 second,
                    outputTiming: OutputTiming = Change,
                    inputs: Seq[Source] = Seq(StdIn),
                    outputs: Seq[Sink] = Seq(StdOut),
                    logLevel: String = "none"
                   ) {

    def parseProgramFromFiles() = {
      if (programFiles.isEmpty) {
        throw new RuntimeException("mergedProgram argument missing")
      }
      val programs: Seq[LarsProgram] = programFiles.map{file => LarsParser(file.toURI.toURL)}
      val mergedProgram: LarsProgram = {
        if (programs.size == 1) {
          programs(0)
        } else {
          programs.reduce { (p1,p2) => p1 ++ p2 }
        }
      }

      if (Util.log_level >= Util.LOG_LEVEL_INFO) {
        logger.info(f"Lars Program of ${mergedProgram.rules.size} rules with ${mergedProgram.extendedAtoms.size} different atoms.")
        Format.parsed(mergedProgram).foreach(logger.info(_))
      }

      mergedProgram
    }

    def buildReasoner(program: LarsProgram): Reasoner = {

      val reasonerBuilder = BuildReasoner.
        withProgram(program).
        withClockTime(clockTime)

      val preparedReasoner = reasonerBuilder.configure().withIncremental().useAgent(getO2AInterface(classOf[IReasonerAgent]))
      /*reasoner match {
        case ReasonerChoice.incremental => reasonerBuilder.configure().withIncremental().use()
        case ReasonerChoice.clingo => {
          val cfg = reasonerBuilder.configure().withClingo().withDefaultEvaluationMode()
          outputTiming match {
            case Time(_) => cfg.usePull()
            case Signal(n) if n > 1 => cfg.usePull()
            case _ => cfg.usePush()
          }
        }
      }*/

      if (filter.isEmpty) {
        preparedReasoner.withIntensionalFilter().seal() //default
      } else if (filter.size == 1) {
        filter(0) match {
          case "inferences" => preparedReasoner.withIntensionalFilter().seal()
          case "all" => preparedReasoner.withNoFilter().seal()
          case pred:String => preparedReasoner.withPredicateFilter(Set(Predicate(pred))).seal()
        }
      } else {
        preparedReasoner.withPredicateFilter(filter.map(Predicate(_)).toSet).seal()
      }
    }
  }

  def overrideLogLevel(config: Config): Unit = {
    config.logLevel match {
      case "none" => Util.log_level = Util.LOG_LEVEL_NONE
      case "info" => Util.log_level = Util.LOG_LEVEL_INFO
      case "debug" => Util.log_level = Util.LOG_LEVEL_DEBUG
      case _ => {
        logger.warn(f"unknown log level: ${config.logLevel}. using 'none' (only warnings and errors).")
        Util.log_level = Util.LOG_LEVEL_NONE
      }
    }
    if (Util.log_level >= Util.LOG_LEVEL_INFO) {
      logger.info(f"Engine Configuration: " + Util.prettyPrint(config))
    }
  }

  def checkTimeWindowCompatibility(program: LarsProgram, config: Config): Unit = {
    program.timeWindows.find { w =>
      Duration(w.windowSize.length, w.windowSize.unit).lt(config.clockTime)
    } match {
      case Some(w) => throw new IllegalArgumentException(f"Time window size ${Duration(w.windowSize.length,w.windowSize.unit)} has smaller duration than the clock time: ${config.clockTime}.")
      case _ =>
    }
    val clockMillis = config.clockTime.toMillis
    program.timeWindows.find { w =>
      Duration(w.windowSize.length,w.windowSize.unit).toMillis % clockMillis > 0
    } match {
      case Some(w) => throw new IllegalArgumentException(f"Time window size ${Duration(w.windowSize.length,w.windowSize.unit)} is not compatible with clock time ${config.clockTime}; must be a multiple.")
      case _ =>
    }
  }

  def connectIO(engine: Engine, config: Config): Unit = {
    config.inputs foreach {
      case SocketInput(port) => engine.connect(ReadFromSocket(config.clockTime._2, port))
      case StdIn => engine.connect(ReadFromStdIn(config.clockTime._2))
    }
    config.outputs foreach {
      case StdOut => engine.connect(OutputToStdOut)
      case SocketOutput(port) => engine.connect(OutputToSocket(port))
    }
  }

  override def sendMessageToTMS(verb: Int ,action:String, msg: Serializable): Unit = {
    val msgObj = new MsgObj(action, msg);
    val message = new ACLMessage(verb)
    message.addReceiver(tmsAgent)
    message.setContentObject(msgObj)
    send(message)
  }

  def formatMessage(m: RDFTableSer): util.ArrayList[String] ={
    print(m)

    val listeFait = new util.ArrayList[String]()
    val listeTriple = m.getTuples

    //m.getTuples.forEach(tp => tp.getFields.forEach(f => listeFait.add(f)))

    m.getTuples.forEach(tp => {
      if("[Subject, Predicate, Object, Timestamp]".equals(m.getNames.toString)){
        var fait = extractSubject(tp.getFields.get(1)) + ";"
        /*fait = fait + "(" // \""
        //fait = fait + tp.getFields.get(0)
        //fait = fait + "\","
        fait = fait + extractValue(tp.getFields.get(2))
        fait = fait + ");"*/
        listeFait.add(fait)

      }
      else {
        m.getNames.forEach(name => {
          var index = m.getNames.indexOf(name)
          var txt = name +";"
          /*if (index != 0) {
            txt = txt + "("//\""
            //txt = txt + tp.getFields.get(0)
            //txt = txt + "\","
            txt = txt + extractValue(tp.getFields.get(index))
            txt = txt + ");"
            listeFait.add(txt)
          }*/
          listeFait.add(txt)
        })
      }
    })

    listeFait
  }

  def extractSubject(subjectBrut: String): String={
    subjectBrut.split("/").reverse(0).split("#").reverse(0)
  }

  def extractValue(brut: String): String ={
    //brut.contains("#decimal") ||
    if (brut.contains("#double") || brut.contains("#long")) {
      var a = BigDecimal(("""\".*\"""".r.findFirstIn(brut)).get.replace("\"", ""))*1000
      a.setScale(0, BigDecimal.RoundingMode.HALF_UP).toString()
    }
    else{
      val fi = """\".*\"""".r.findFirstIn(brut)
      if(fi != None){
        fi.get
      }
      else "\""+ brut.split("#").reverse(0) +"\""
    }

  }



}
