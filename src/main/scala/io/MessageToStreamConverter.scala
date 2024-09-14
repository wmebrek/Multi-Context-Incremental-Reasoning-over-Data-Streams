package io

import java.text.SimpleDateFormat
import java.util.{Random, UUID}

import common.Util
import eu.larkc.csparql.cep.api.{RdfQuadruple, RdfStream}
import org.slf4j.LoggerFactory

class MessageToStreamConverter(iri: String) extends RdfStream(iri) {

  private var baseUri : String = null
  private val logger = LoggerFactory.getLogger(classOf[MessageToStreamConverter])

  def this(iri: String, baseUri: String) {
    this(iri)
    //super (iri)
    this.baseUri = baseUri
  }

  def convertMsg(arg: String): Unit = {
    val random = new Random
    val sensorIndex = 0
    var observationIndex: UUID = null
    val split = arg.split("\t")
    val sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss+SSS")

    val typeCapteur = Util.getCapteurName(split(1))

    try {
      val room = split(1).substring(split(1).length - 2, split(1).length)
      val valeurCapteur = split(2)
      var valeurCapteurNormalize : String = null
      observationIndex = UUID.randomUUID

      val observationTime = observationIndex + "time"
      val obsId = "_:" + observationIndex
      var q = new RdfQuadruple(obsId, "http://www.ontologydesignpatterns.org/ont/dul/DUL.owl#isObservableAt", sdf.format(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSSSSS").parse(split(0))) + "^^http://www.w3.org/2001/XMLSchema#dateTime", System.currentTimeMillis)
      this.put(q)
      q = new RdfQuadruple(obsId, "http://www.w3.org/ns/sosa/isObservedBy", split(1), System.currentTimeMillis)
      this.put(q)
      q = new RdfQuadruple(obsId, "http://www.w3.org/ns/sosa/madeBySensor", typeCapteur, System.currentTimeMillis)
      this.put(q)
      q = new RdfQuadruple(obsId, "http://users.abo.fi/ndiaz/public/HumanActivity.owl#isLocatedIn", room, System.currentTimeMillis)
      this.put(q)
      valeurCapteurNormalize = Util.getTypeValeur(valeurCapteur)
      q = new RdfQuadruple(obsId, "http://www.w3.org/ns/sosa/hasSimpleResult", valeurCapteurNormalize, System.currentTimeMillis)
      this.put(q)

    } catch {
      case e: Exception =>
        e.printStackTrace()
    }


  }
}