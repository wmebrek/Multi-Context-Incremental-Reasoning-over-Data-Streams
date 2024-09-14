package io

import java.io.{IOException, Serializable}
import java.util
import java.util.UUID

import com.hp.hpl.jena.rdf.model.{ModelFactory, Statement}
import com.hp.hpl.jena.vocabulary.RDF
import insight_centre.aceis.eventmodel.EventDeclaration
import insight_centre.aceis.io.rdf.RDFFileManager
import insight_centre.aceis.observations.SensorObservation
import org.slf4j.LoggerFactory

class MessageToStreamConverterLocation(iri: String, ed: EventDeclaration) extends MessageToStreamConverterGenerique(iri) {
  private val logger = LoggerFactory.getLogger(classOf[MessageToStreamConverterLocation])

  @throws[NumberFormatException]
  @throws[IOException]
  override def getStatements(arg: Serializable): util.List[Statement] = {
    val so = arg.asInstanceOf[SensorObservation]
    val userStr = so.getFoi
    val coordinatesStr = so.getValue.toString
    val m = ModelFactory.createDefaultModel
    val lat = coordinatesStr.split(",")(0).toDouble
    val lon = coordinatesStr.split(",")(1).toDouble
    val serviceID = m.createResource(ed.getServiceId)
    //
    // Resource user = m.createResource(userStr);

    val observation = m.createResource(RDFFileManager.defaultPrefix + so.getObId + UUID.randomUUID)
    main.Program.obMap.put(observation.toString, so)
    observation.addProperty(RDF.`type`, m.createResource(RDFFileManager.ssnPrefix + "Observation"))
    // observation.addProperty(RDF.type, m.createResource(RDFFileManager.saoPrefix + "StreamData"));

    // location.addProperty(RDF.type, m.createResource(RDFFileManager.ctPrefix + "Location"));

    val coordinates = m.createResource
    coordinates.addLiteral(m.createProperty(RDFFileManager.ctPrefix + "hasLatitude"), lat)
    coordinates.addLiteral(m.createProperty(RDFFileManager.ctPrefix + "hasLongitude"), lon)

    // observation.addProperty(m.createProperty(RDFFileManager.ssnPrefix + "featureOfInterest"), user);
    observation.addProperty(m.createProperty(RDFFileManager.ssnPrefix + "observedProperty"), m.createResource(ed.getPayloads.get(0).split("\\|")(2)))
    observation.addProperty(m.createProperty(RDFFileManager.ssnPrefix + "observedBy"), serviceID)
    // fake fixed foi
    observation.addProperty(m.createProperty(RDFFileManager.ssnPrefix + "featureOfInterest"), m.createResource("http://iot.ee.surrey.ac.uk/citypulse/datasets/aarhusculturalevents/culturalEvents_aarhus#context_do63jk2t8c3bjkfb119ojgkhs7"))

    observation.addProperty(m.createProperty(RDFFileManager.saoPrefix + "hasValue"), coordinates)
    // System.out.println("transformed: " + m.listStatements().toList().size());s
    return m.listStatements.toList
  }
}
