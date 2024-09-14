package agents.old

import java.util
import java.util.concurrent.CompletableFuture
import java.util.{Observable, Observer}

import com.google.common.base.Splitter
import common.Util
import jade.core.behaviours.OneShotBehaviour
import jade.core.{AID, Agent}
import jade.domain.FIPAAgentManagement.{DFAgentDescription, ServiceDescription}
import jade.domain.{DFService, FIPAException}
import jade.lang.acl.ACLMessage
import org.slf4j.LoggerFactory

class SingleAppartAgent extends Agent{
  val logger = LoggerFactory.getLogger(classOf[SingleAppartAgent])
  /** correspondance entre les types de capteur et les acronymes */
  private[agents] val listeAgentParType: util.Map[String, AID] = new util.HashMap[String, AID]
  /** correspondance entre les types de capteur et les acronymes */
  private[agents] val acrnoymeType : util.HashMap[String, String] = new util.HashMap[String, String]
  /** utils */
  private[agents] var split: Array[String] = null
  private[agents] var room : String = null
  private[agents] var typeCapteur : String = null
  private[agents] var argCasted : String = null
  val delimiter = "\t"


  override def setup(): Unit = {
    println("Agent setup - start");

    /** Lancer le traitement => Simulation Stream with file */
    addBehaviour(new OneShotBehaviour() {

      override def action(): Unit = {
        startTraitement()
      }
    })
  }

  def startTraitement(): Unit = {
    try {
      /** Stream chaq xx millesecondes */
      val streamSingleAppart = new SingleAppartStreamer(1000L, 1)
      val singleAppartThread = new Thread(streamSingleAppart)

      /** Observer du stream - En fonction type du capteur, on envoi à l'agent compétent */
      streamSingleAppart.addObserver(new CustomFormatter)

      /** Start streaming data */
      singleAppartThread.start()
    } catch {
      case e: Exception =>
        logger.error(e.getMessage, e)
    }

  }

  class CustomFormatter() extends Observable with Observer {
    override def update(o: Observable, arg: Any): Unit = {
      CompletableFuture.runAsync(() => {
        def foo() = {
          val str = new StringBuilder
          argCasted = arg.asInstanceOf[String]
          split = argCasted.split(delimiter)
          val splitIt = Splitter.on(delimiter).splitToList(argCasted)
          //room = split[1].substring(split[1].length()-3, split[1].length());
          room = splitIt.get(1).substring(splitIt.get(1).length - 2, splitIt.get(1).length)
          typeCapteur = getCapteurNameCache(splitIt.get(1))
          //str.append(splitIt.get(1));
          //str.append(delimiter);
          str.append(argCasted)
          str.append(delimiter)
          str.append(typeCapteur)
          str.append(delimiter)
          str.append(room)
          /** Envoyer à l'agent compétent le stream sous forme de message */
          val cs = rechercherAgentCache(typeCapteur)
          //logger.info("{} : Data read => {}", argCasted, typeCapteur)

          /** Envoyer à l'agent compétent le stream sous forme de message */
          if (cs != null) {
            logger.info("Capteur {} est de type {}, les données vont être envoyé à l'Agent: {}", split(1), typeCapteur, cs.getLocalName)
            val message = new ACLMessage(ACLMessage.INFORM)
            message.addReceiver(cs)
            message.setContent(argCasted + "\t" + typeCapteur + "\t" + room)
            send(message)
          }
          //else System.out.println("Aucun n'agent ne gère ce type de capteur : " + typeCapteur)
        }

        foo()
      })
    }
  }

  def getCapteurNameCache(split: String): String = if (acrnoymeType.get(split) != null) acrnoymeType.get(split)
  else {
    val captName = getCapteurName(split)
    acrnoymeType.put(split, captName)
    acrnoymeType.get(split)
  }

  def getCapteurName(split: String): String = {
    var typeCapteur = ""
    split.substring(0, 1) match {
      case "D" =>
        typeCapteur = "magnetic_door_sensors"
      case "L" =>
        typeCapteur = "light_sensors"
        split.substring(0, 2) match {
          case "LS" =>
            typeCapteur = "light_sensors"
          case "LA" =>
            typeCapteur = "light_switches"
        }
      case "M" =>
        if ("MA".equals(split.substring(0, 2))) {
          typeCapteur = "wide_area_infrared_motion_sensors"
        }
        else {
          typeCapteur = "infrared_motion_sensors"
        }
      case "T" =>
        typeCapteur = "temperature_sensors"
      case "B" =>
        if("BA".equals(split.substring(0,2)))
          typeCapteur = "sensor_battery_levels"

      case _ =>
        typeCapteur = "unknown_sensors"
    }
    typeCapteur
  }


  /** Rechercher agent par type de valeur des capteurs integer - string */
  def rechercherAgentParType(typeValeur: String): AID = {
    val dfd = new DFAgentDescription
    val sd = new ServiceDescription
    //String typeAgent = Arrays.asList("T", "L", "LS", "BA").contains(typeCapteur) ? "Integer" : Arrays.asList("M", "MA", "D").contains(typeCapteur) ? "Boolean" : "String";
    val typeAgent = if (Util.isInteger(typeValeur)) "Integer"
    else if (Util.isBooleanHerit(typeValeur) || Util.isBoolean(typeValeur)) "Boolean"
    else "String"
    sd.setType(typeAgent + "-service")
    dfd.addServices(sd)
    try {
      var result = new Array[DFAgentDescription](0)
      result = DFService.search(this, dfd)
      //   System.out.println(result.length + " results recherche Clingo" );
      if (result.length > 0) {
        System.out.println(result(0).getName.getLocalName)
        return result(0).getName
      }
    } catch {
      case e: FIPAException =>
        e.printStackTrace()
    }
    null
  }


  def rechercherAgentParTypeCapteur(typeCapteur: String): AID = {
    val dfd = new DFAgentDescription
    val sd = new ServiceDescription
    sd.setType(typeCapteur + "-service")
    dfd.addServices(sd)
    try {
      var result = new Array[DFAgentDescription](0)
      result = DFService.search(this, dfd)
      if (result.length > 0) {
        System.out.println(result(0).getName.getLocalName)
        return result(0).getName
      }
    } catch {
      case e: FIPAException =>
        e.printStackTrace()
    }
    null
  }

  def rechercherAgentCache(`type`: String): AID = {
    var curr = listeAgentParType.get(`type`)
    if (curr == null) {
      curr = rechercherAgentParTypeCapteur(`type`)
      listeAgentParType.put(`type`, curr)
    }
    curr
  }

}
