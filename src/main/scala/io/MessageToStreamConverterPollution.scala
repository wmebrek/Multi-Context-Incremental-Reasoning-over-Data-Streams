package io

import java.io.{IOException, Serializable}
import java.util
import java.util.UUID

import com.hp.hpl.jena.rdf.model.{ModelFactory, Statement}
import com.hp.hpl.jena.vocabulary.RDF
import eu.larkc.csparql.cep.api.RdfQuadruple
import insight_centre.aceis.eventmodel.EventDeclaration
import insight_centre.aceis.io.rdf.RDFFileManager
import insight_centre.aceis.observations.PollutionObservation
import org.slf4j.LoggerFactory

class MessageToStreamConverterPollution(iri: String, ed: EventDeclaration) extends MessageToStreamConverterGenerique(iri) {
  private val logger = LoggerFactory.getLogger(classOf[MessageToStreamConverterPollution])


  @throws[NumberFormatException]
  @throws[IOException]
  override def getStatements(arg: Serializable): util.List[Statement] = {
    val so = arg.asInstanceOf[PollutionObservation]
    val m = ModelFactory.createDefaultModel
    if (ed != null) {
      import scala.collection.JavaConversions._
      for (s <- ed.getPayloads) {
        val observation = m.createResource(RDFFileManager.defaultPrefix + so.getObId + UUID.randomUUID)
        main.Program.obMap.put(observation.toString, so)
        // so.setObId(RDFFileManager.defaultPrefix + observation.toString());
        observation.addProperty(RDF.`type`, m.createResource(RDFFileManager.ssnPrefix + "Observation"))
        val serviceID = m.createResource(ed.getServiceId)
        observation.addProperty(m.createProperty(RDFFileManager.ssnPrefix + "observedBy"), serviceID)
        observation.addProperty(m.createProperty(RDFFileManager.ssnPrefix + "observedProperty"), m.createResource(s.split("\\|")(2)))
        val hasValue = m.createProperty(RDFFileManager.saoPrefix + "hasValue")
        observation.addLiteral(hasValue, so.getApi)
      }
    }
    return m.listStatements.toList
  }
}
