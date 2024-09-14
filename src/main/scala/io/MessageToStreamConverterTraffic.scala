package io

import java.io.{IOException, Serializable}
import java.util
import java.util.UUID

import com.hp.hpl.jena.rdf.model.{ModelFactory, Statement}
import com.hp.hpl.jena.vocabulary.RDF
import insight_centre.aceis.eventmodel.EventDeclaration
import insight_centre.aceis.io.rdf.RDFFileManager
import insight_centre.aceis.observations.AarhusTrafficObservation
import org.slf4j.LoggerFactory
import scala.collection.JavaConversions._

class MessageToStreamConverterTraffic(iri: String, ed: EventDeclaration) extends MessageToStreamConverterGenerique(iri) {
  private val logger = LoggerFactory.getLogger(classOf[MessageToStreamConverterTraffic])
  val lFOI: util.Map[String, String] = new util.HashMap[String, String]()


  @throws[NumberFormatException]
  @throws[IOException]
  override def getStatements(arg: Serializable): util.List[Statement] = {
    val data = arg.asInstanceOf[AarhusTrafficObservation]

    logger.debug("traffic get statement")
    val m = ModelFactory.createDefaultModel
    if (ed != null) {

      val idReport = data.getReport_ID.toString
      if(!lFOI.containsKey(idReport)){
        val idReportResource = RDFFileManager.defaultPrefix + "FOI-" + UUID.randomUUID
        lFOI.put(idReport, idReportResource)
        }
      else {
        logger.debug("lFOI.containsKey(idReport) ==> {}", idReport)
      }

      val observationFoi = m.createResource(lFOI.get(idReport))
      observationFoi.addProperty(RDF.`type`, m.createProperty(RDFFileManager.ssnPrefix + "FeatureOfInterest"))
      observationFoi.addLiteral(m.createProperty(RDFFileManager.ctPrefix + "hasStartLatitude"), data.getLatitude1 )
      observationFoi.addLiteral(m.createProperty(RDFFileManager.ctPrefix + "hasStartLongitude"), data.getLongtitude1 )
      observationFoi.addLiteral(m.createProperty(RDFFileManager.ctPrefix + "hasEndLatitude"), data.getLatitude2 )
      observationFoi.addLiteral(m.createProperty(RDFFileManager.ctPrefix + "hasEndLongitude"), data.getLongtitude2 )


      for (pStr <- ed.getPayloads) { // if (s.contains("EstimatedTime")) {
        // Resource observedProperty = m.createResource(s);
        val obId = data.getObId
        val observation = m.createResource(RDFFileManager.defaultPrefix + obId + UUID.randomUUID)
        main.Program.obMap.put(observation.toString, data)
        // data.setObId(observation.toString());
        // System.out.println("OB: " + observation.toString());
        observation.addProperty(RDF.`type`, m.createResource(RDFFileManager.ssnPrefix + "Observation"))
        val serviceID = m.createResource(ed.getServiceId)
        observation.addProperty(m.createProperty(RDFFileManager.ssnPrefix + "observedBy"), serviceID)

        val observationProperty = m.createResource(RDFFileManager.defaultPrefix + "property-" + obId + UUID.randomUUID)
        observationProperty.addProperty(m.createProperty(RDFFileManager.ssnPrefix + "isPropertyOf"), m.createResource(lFOI.get(idReport)))
        //observation.addProperty(m.createProperty(RDFFileManager.ssnPrefix + "observedProperty"), m.createResource(pStr.split("\\|")(2)))
        observation.addProperty(m.createProperty(RDFFileManager.ssnPrefix + "observedProperty"), observationProperty)


        val hasValue = m.createProperty(RDFFileManager.saoPrefix + "hasValue")
        // System.out.println("Annotating: " + observedProperty.toString());


        if (pStr.contains("AvgSpeed")) {
          observation.addLiteral(hasValue, data.getAverageSpeed)
          observationProperty.addProperty(RDF.`type`, m.createProperty(RDFFileManager.ctPrefix + "AvgSpeed"))
        }
        else if (pStr.contains("VehicleCount")) {
          val value = data.getVehicle_count
          observation.addLiteral(hasValue, value)
          observationProperty.addProperty(RDF.`type`, m.createProperty(RDFFileManager.ctPrefix + "VehicleCount"))
        }
        else if (pStr.contains("MeasuredTime")) {
          observation.addLiteral(hasValue, data.getAvgMeasuredTime)
          observationProperty.addProperty(RDF.`type`, m.createProperty(RDFFileManager.ctPrefix + "MeasuredTime"))
        }
        else if (pStr.contains("EstimatedTime")) {
          observation.addLiteral(hasValue, data.getEstimatedTime)
          observationProperty.addProperty(RDF.`type`, m.createProperty(RDFFileManager.ctPrefix + "EstimatedTime"))
        }
        else if (pStr.contains("CongestionLevel")) {
          observation.addLiteral(hasValue, data.getCongestionLevel)
          observationProperty.addProperty(RDF.`type`, m.createProperty(RDFFileManager.ctPrefix + "CongestionLevel"))
        }
        // break;
        // }
      }
    }
    return m.listStatements.toList
  }
}
