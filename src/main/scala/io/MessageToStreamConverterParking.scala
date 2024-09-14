package io

import java.io.{IOException, Serializable}
import java.util
import java.util.UUID

import com.hp.hpl.jena.rdf.model.{ModelFactory, Statement}
import com.hp.hpl.jena.vocabulary.{RDF, RDFS}
import insight_centre.aceis.eventmodel.EventDeclaration
import insight_centre.aceis.io.rdf.RDFFileManager
import insight_centre.aceis.observations.AarhusParkingObservation
import org.slf4j.LoggerFactory

class MessageToStreamConverterParking(iri: String, ed: EventDeclaration) extends MessageToStreamConverterGenerique(iri) {
  private val logger = LoggerFactory.getLogger(classOf[MessageToStreamConverterParking])
  val lFOI: util.Map[String, String] = new util.HashMap[String, String]()

  @throws[NumberFormatException]
  @throws[IOException]
  override def getStatements(arg: Serializable): util.List[Statement] = {
    val data = arg.asInstanceOf[AarhusParkingObservation]
    val m = ModelFactory.createDefaultModel
    val observation = m.createResource(RDFFileManager.defaultPrefix + data.getObId + UUID.randomUUID)
    
    /** LAT & LNG*/
    val garageCode = data.getGarageCode
    if(!lFOI.containsKey(garageCode)){

      val codeGarageResource = RDFFileManager.defaultPrefix + "FOI-" + UUID.randomUUID
      lFOI.put(garageCode, codeGarageResource)

    }

    val observationFoi = m.createResource(lFOI.get(garageCode))
    observationFoi.addProperty(RDF.`type`, m.createProperty(RDFFileManager.ssnPrefix + "FeatureOfInterest"))
    observationFoi.addLiteral(RDFS.`label`, data.getGarageCode)
    observationFoi.addLiteral(m.createProperty(RDFFileManager.ctPrefix + "hasStartLatitude"), data.getLat )
    observationFoi.addLiteral(m.createProperty(RDFFileManager.ctPrefix + "hasStartLongitude"), data.getLon )


    main.Program.obMap.put(observation.toString, data)
    observation.addProperty(RDF.`type`, m.createResource(RDFFileManager.ssnPrefix + "Observation"))
    // observation.addProperty(RDF.type,
    // m.createResource(RDFFileManager.saoPrefix + "StreamData"));
    val serviceID = m.createResource(ed.getServiceId)
    observation.addProperty(m.createProperty(RDFFileManager.ssnPrefix + "observedBy"), serviceID)
    // Resource property = m.createResource(s.split("\\|")[2]);
    // property.addProperty(RDF.type, m.createResource(s.split("\\|")[0]));
    val observationProperty = m.createResource(RDFFileManager.defaultPrefix + "property-" + data.getObId + UUID.randomUUID)
    observationProperty.addProperty(m.createProperty(RDFFileManager.ssnPrefix + "isPropertyOf"), m.createResource(lFOI.get(garageCode)))
    observationProperty.addProperty(RDF.`type`, m.createProperty(RDFFileManager.ctPrefix + "ParkingVacancy"))
    observation.addProperty(m.createProperty(RDFFileManager.ssnPrefix + "observedProperty"), observationProperty)
    //observation.addProperty(m.createProperty(RDFFileManager.ssnPrefix + "observedProperty"), m.createResource(ed.getPayloads.get(0).split("\\|")(2)))

    val hasValue = m.createProperty(RDFFileManager.saoPrefix + "hasValue")
    // Literal l;
    // System.out.println("Annotating: " + observedProperty.toString());
    // if (observedProperty.contains("AvgSpeed"))
    observation.addLiteral(hasValue, data.getVacancies)
    // observation.addLiteral(m.createProperty(RDFFileManager.ssnPrefix + "featureOfInterest"),
    // ((AarhusParkingObservation) so).getGarageCode());
    m.listStatements.toList
  }
}
