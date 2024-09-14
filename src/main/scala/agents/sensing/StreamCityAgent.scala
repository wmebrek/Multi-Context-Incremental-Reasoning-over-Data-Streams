package agents.sensing

import java.util.concurrent.CompletableFuture
import java.util.{Observable, Observer}

import insight_centre.aceis.io.streams.brut.CSPARQLSensorStream
import insight_centre.aceis.io.streams.cqels.CQELSSensorStream
import insight_centre.aceis.observations.SensorObservation
import jade.core.behaviours.{CyclicBehaviour, OneShotBehaviour}
import jade.core.{AID, Agent}
import jade.lang.acl.ACLMessage
import org.slf4j.LoggerFactory

class StreamCityAgent extends Agent {

  val logger = LoggerFactory.getLogger(classOf[StreamCityAgent])
  var agentsDest : Set[AID]= Set[AID]()


  override def setup(): Unit = {
    println("Agent stream city setup - start");

    addBehaviour(new CyclicBehaviour() {
      override def action(): Unit = {
        val msg = receive
        if (msg != null) {
          val msgObj = msg.getContent
          addAgentToDest(new AID(msgObj, AID.ISLOCALNAME))
          /*val reply: ACLMessage = msg.createReply
          reply.setPerformative(ACLMessage.AGREE)
          reply.setContent("Ok")
          send(reply)*/
        }
        block
      }
    })

    /** Lancer le traitement => Simulation Stream with file */
    addBehaviour(new OneShotBehaviour() {

      override def action(): Unit = {
        CompletableFuture.runAsync(() => {
          startTraitement()
        })
      }
    })

  }

  def startTraitement(): Unit = {
    try {

      val args = getArguments
      agentsDest += args(0).asInstanceOf[AID]
      var stream: Runnable = null

      if(args(1).isInstanceOf[CSPARQLSensorStream]) {
        stream = args(1).asInstanceOf[CSPARQLSensorStream]
        (stream.asInstanceOf[CSPARQLSensorStream]).addObserver(new CustomFormatter)
      }
      else if(args(1).isInstanceOf[CQELSSensorStream]) {
        stream = args(1).asInstanceOf[CQELSSensorStream]
      }
      val streamThread = new Thread(stream)
      /** Observer du stream */
      /** Start streaming data */
      streamThread.start()
    } catch {
      case e: Exception =>
        logger.error("error {}", e)
    }

  }

  class CustomFormatter() extends Observer {
    override def update(o: Observable, arg: Any): Unit = {
      CompletableFuture.runAsync(() => {
        def sendMessage() = {
          val msg = arg.asInstanceOf[SensorObservation]
          val message = new ACLMessage(ACLMessage.INFORM)
          message.setContentObject(msg)

          agentsDest.foreach(agent => {
            message.addReceiver(agent)
            send(message)
          })
        }
        sendMessage()
      })
    }
  }

  def addAgentToDest(agent: AID): Unit = {
    agentsDest += agent
  }
}
