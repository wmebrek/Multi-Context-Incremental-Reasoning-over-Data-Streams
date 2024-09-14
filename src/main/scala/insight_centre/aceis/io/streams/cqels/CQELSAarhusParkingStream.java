package insight_centre.aceis.io.streams.cqels;

import com.csvreader.CsvReader;
import com.hp.hpl.jena.rdf.model.*;
import com.hp.hpl.jena.vocabulary.RDF;
import insight_centre.aceis.eventmodel.EventDeclaration;
import insight_centre.aceis.io.rdf.RDFFileManager;
import insight_centre.aceis.io.streams.DataWrapper;
import insight_centre.aceis.observations.AarhusParkingObservation;
import insight_centre.aceis.observations.SensorObservation;
import org.deri.cqels.engine.ExecContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

public class CQELSAarhusParkingStream extends CQELSSensorStream {

	private final Logger logger = LoggerFactory.getLogger(getClass());
	CsvReader streamData;
	EventDeclaration ed;
	SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd' 'HH:mm:ss");
	private Date startDate = null;
	private Date endDate = null;
	private Map<String, String> lFOI = new HashMap<>();

	public static void main(String[] args) {
		try {
			List<String> payloads = new ArrayList<String>();
			payloads.add(RDFFileManager.defaultPrefix + "Property-1|" + RDFFileManager.defaultPrefix + "FoI-1|"
					+ RDFFileManager.ctPrefix + "API");
			EventDeclaration ed = new EventDeclaration("testEd", "testsrc", "air_pollution", null, payloads, 5.0);
			CQELSAarhusPollutionStream aps = new CQELSAarhusPollutionStream(null, "testuri",
					"streams/pollutionData158324.stream", ed);
			Thread th = new Thread(aps);
			th.start();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public CQELSAarhusParkingStream(ExecContext context, String uri, String txtFile, EventDeclaration ed)
			throws IOException {
		super(context, uri);
		streamData = new CsvReader(String.valueOf(txtFile));
		this.ed = ed;
		streamData.setTrimWhitespace(false);
		streamData.setDelimiter(',');
		streamData.readHeaders();
	}

	public CQELSAarhusParkingStream(ExecContext context, String uri, String txtFile, EventDeclaration ed, Date start,
                                    Date end) throws IOException {
		super(context, uri);

		streamData = new CsvReader(String.valueOf(txtFile));
		this.ed = ed;
		streamData.setTrimWhitespace(false);
		streamData.setDelimiter(',');
		streamData.readHeaders();
		this.startDate = start;
		this.endDate = end;
	}

	@Override
	public void run() {
		logger.info("Starting sensor stream: " + this.getURI());
		try {
			while (streamData.readRecord() && !stop) {
				Date obTime = sdf.parse(streamData.get("updatetime"));
				// logger.info("obTime: " + obTime + " start: " + this.startDate + " end: " + this.endDate);
				if (this.startDate != null && this.endDate != null) {
					if (obTime.before(this.startDate) || obTime.after(this.endDate)) {
						// logger.debug(this.getURI() + ": Disgarded observation @" + obTime);
						continue;
					}
				}

				AarhusParkingObservation po = (AarhusParkingObservation) this.createObservation(streamData);
				// logger.debug("Reading data: " + new Gson().toJson(po));
				List<Statement> stmts = this.getStatements(po);

				for (Statement st : stmts) {
					try {
						logger.debug(this.getURI() + " Streaming: " + st.toString());
						stream(st.getSubject().asNode(), st.getPredicate().asNode(), st.getObject().asNode());

					} catch (Exception e) {
						e.printStackTrace();
						logger.error(this.getURI() + " CQELS streamming error.");
					}
					// messageByte += st.toString().getBytes().length;
				}
				// logger.info("Messages streamed to CQELS successfully.");
				try {
					if (this.getRate() == 1.0)
						Thread.sleep(sleep);
				} catch (Exception e) {

					e.printStackTrace();
					this.stop();
				}

			}
		} catch (Exception e) {
			logger.error("Unexpected thread termination");

		} finally {
			logger.info("Stream Terminated: " + this.getURI());
			this.stop();
		}

	}

	@Override
	protected List<Statement> getStatements(SensorObservation so) {
		Model m = ModelFactory.createDefaultModel();
		Resource observation = m.createResource(RDFFileManager.defaultPrefix + so.getObId() + UUID.randomUUID());
		AarhusParkingObservation data = ((AarhusParkingObservation) so);

		String garageCode = data.getGarageCode();
		if(!lFOI.containsKey(garageCode)){
			String codeGarageResource = RDFFileManager.defaultPrefix + "FOI-" + UUID.randomUUID();
			lFOI.put(garageCode, codeGarageResource);

			Resource observationFoi = m.createResource(codeGarageResource);
			observationFoi.addProperty(RDF.type, m.createProperty(RDFFileManager.ssnPrefix + "FeatureOfInterest"));
			observationFoi.addLiteral(m.createProperty(RDFFileManager.ctPrefix + "hasStartLatitude"), data.getLat() );
			observationFoi.addLiteral(m.createProperty(RDFFileManager.ctPrefix + "hasStartLongitude"), data.getLon() );
		}

		//CityBench.obMap.put(observation.toString(), so);
		observation.addProperty(RDF.type, m.createResource(RDFFileManager.ssnPrefix + "Observation"));
		// observation.addProperty(RDF.type,
		// m.createResource(RDFFileManager.saoPrefix + "StreamData"));
		Resource serviceID = m.createResource(ed.getServiceId());
		observation.addProperty(m.createProperty(RDFFileManager.ssnPrefix + "observedBy"), serviceID);
		// Resource property = m.createResource(s.split("\\|")[2]);
		// property.addProperty(RDF.type, m.createResource(s.split("\\|")[0]));
		Resource observationProperty = m.createResource(RDFFileManager.defaultPrefix + "property-" + data.getObId() + UUID.randomUUID());
		observationProperty.addProperty(m.createProperty(RDFFileManager.ssnPrefix + "isPropertyOf"), m.createResource(lFOI.get(garageCode)));
		observationProperty.addProperty(RDF.type, m.createProperty(RDFFileManager.ctPrefix + "ParkingVacancy"));

		observation.addProperty(m.createProperty(RDFFileManager.ssnPrefix + "observedProperty"), observationProperty);
		Property hasValue = m.createProperty(RDFFileManager.saoPrefix + "hasValue");
		// Literal l;
		// System.out.println("Annotating: " + observedProperty.toString());
		// if (observedProperty.contains("AvgSpeed"))
		observation.addLiteral(hasValue, ((AarhusParkingObservation) so).getVacancies());
		// observation.addLiteral(m.createProperty(RDFFileManager.ssnPrefix + "featureOfInterest"),
		// ((AarhusParkingObservation) so).getGarageCode());
		return m.listStatements().toList();
	}

	@Override
	protected SensorObservation createObservation(Object data) {
		try {
			// CsvReader streamData = (CsvReader) data;
			int vehicleCnt = Integer.parseInt(streamData.get("vehiclecount")), id = Integer.parseInt(streamData
					.get("_id")), total_spaces = Integer.parseInt(streamData.get("totalspaces"));
			String garagecode = streamData.get("garagecode");
			Date obTime = sdf.parse(streamData.get("updatetime"));
			AarhusParkingObservation apo = new AarhusParkingObservation(total_spaces - vehicleCnt, garagecode, "", Double.parseDouble(streamData.get("Point1_Lat")),
					Double.parseDouble(streamData.get("Point1_Long")));
			apo.setObTimeStamp(obTime);
			// logger.info("Annotating obTime: " + obTime + " in ms: " + obTime.getTime());
			apo.setObId("AarhusParkingObservation-" + id);
			logger.debug(ed.getServiceId() + ": streaming record @" + apo.getObTimeStamp());
			DataWrapper.waitForInterval(this.currentObservation, apo, this.startDate, getRate());
			this.currentObservation = apo;
			return apo;
		} catch (NumberFormatException | IOException e) {
			e.printStackTrace();
		} catch (ParseException e) {
			logger.error("ed parse error: " + ed.getServiceId());
			e.printStackTrace();
		}
		return null;

	}

}
