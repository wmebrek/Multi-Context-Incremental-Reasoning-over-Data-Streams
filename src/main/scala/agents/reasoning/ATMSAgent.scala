package agents.reasoning

import com.typesafe.scalalogging.Logger
import core.Atom
import core.asp.NormalRule
import insight_centre.aceis.MsgObj
import jade.core.Agent
import jade.core.behaviours.CyclicBehaviour
import jade.domain.FIPAAgentManagement.{DFAgentDescription, ServiceDescription}
import jade.domain.{DFService, FIPAException}
import jade.lang.acl.ACLMessage
import reasoner.incremental.atms.Atms

class ATMSAgent extends Agent {

  val logger = Logger("ATMSAgent")
  var atms: Atms = Atms()
  var lastModel : Option[Set[Atom]] = Some(Set())

  override def setup(): Unit = {
    logger.info("Lancement Agent: " + this.getLocalName)
    EnregistrerServiceDF()

    addBehaviour(new CyclicBehaviour() {
      override def action(): Unit = {
        val msg = receive
        if (msg != null) {
          try {
            val msgObj = msg.getContentObject.asInstanceOf[MsgObj]
            msgObj.getAction match {
              case "add" =>
                atms.add(msgObj.getData.asInstanceOf[NormalRule])
                //println("ATMS  -------------------- added =>" + msgObj.getData.toString)
                return
              case "remove" =>
                atms.remove(msgObj.getData.asInstanceOf[NormalRule])
                //println("ATMS  -------------------- remove =>" + msgObj.getData.toString)
                return
              case "getModel" => {
                //println("\nATMS  -------------------- get model ")
                val reply: ACLMessage = msg.createReply
                if(lastModel != atms.getModel) {
                  lastModel = atms.getModel
                  lastModel.foreach(println(_))
                  reply.setPerformative(ACLMessage.AGREE)
                  var msgObjRep = new MsgObj("getModel", atms.getModel())
                  reply.setContentObject(msgObjRep)
                  send(reply)
                  //print("\n\nmodel sent with succes", atms.tms.getNodes, "\n\n", atms.tms.getNodes)
                }
                //else print("same model !")
                else {
                  print("same model")
                  reply.setPerformative(ACLMessage.REFUSE)
                  var msgObjRep = new MsgObj("getModel", None)
                  reply.setContentObject(msgObjRep)
                  send(reply)
                }
                return
              }
            }
          } catch {
            case exception: Exception => println("atms catch")
          }
        }
        block()
      }
    })
  }

  /**
    * import jade.lang.acl.ACLMessage
    * import jade.lang.acl.MessageTemplate
    * val agree: ACLMessage = receive(MessageTemplate.MatchPerformative(ACLMessage.AGREE))
    * val cancel: ACLMessage = receive(MessageTemplate.MatchPerformative(ACLMessage.CANCEL))
    * MatchContent
    * */


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



}
