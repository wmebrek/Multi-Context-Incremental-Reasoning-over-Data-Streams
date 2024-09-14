package io

import java.io.{IOException, Serializable}
import java.util
import java.util.UUID

import com.hp.hpl.jena.rdf.model.{ModelFactory, Statement}
import com.hp.hpl.jena.vocabulary.RDF
import insight_centre.aceis.eventmodel.EventDeclaration
import insight_centre.aceis.io.rdf.RDFFileManager
import insight_centre.aceis.observations.WeatherObservation
import org.slf4j.LoggerFactory

class MessageToStreamConverterWeather(iri: String, ed: EventDeclaration) extends MessageToStreamConverterGenerique(iri) {
  private val logger = LoggerFactory.getLogger(classOf[MessageToStreamConverterWeather])

  @throws[NumberFormatException]
  @throws[IOException]
  override def getStatements(arg: Serializable): util.List[Statement] = {
    val wo = arg.asInstanceOf[WeatherObservation]

    val m = ModelFactory.createDefaultModel
    if (ed != null) {
      import scala.collection.JavaConversions._
      for (s <- ed.getPayloads) {
        val observation = m.createResource(RDFFileManager.defaultPrefix + wo.getObId + UUID.randomUUID)
        main.Program.obMap.put(observation.toString, wo)
        // wo.setObId(observation.toString());
        observation.addProperty(RDF.`type`, m.createResource(RDFFileManager.ssnPrefix + "Observation"))
        val serviceID = m.createResource(ed.getServiceId)
        observation.addProperty(m.createProperty(RDFFileManager.ssnPrefix + "observedBy"), serviceID)
        observation.addProperty(m.createProperty(RDFFileManager.ssnPrefix + "observedProperty"), m.createResource(s.split("\\|")(2)))
        val hasValue = m.createProperty(RDFFileManager.saoPrefix + "hasValue")
        if (s.contains("Temperature")) observation.addLiteral(hasValue, wo.getTemperature)
        else if (s.toString.contains("Humidity")) observation.addLiteral(hasValue, wo.getHumidity)
        else if (s.toString.contains("WindSpeed")) observation.addLiteral(hasValue, wo.getWindSpeed)
      }
    }
    return m.listStatements.toList
  }
}
