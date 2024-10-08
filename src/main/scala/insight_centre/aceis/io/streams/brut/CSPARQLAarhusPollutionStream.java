package insight_centre.aceis.io.streams.brut;

import com.csvreader.CsvReader;
import com.hp.hpl.jena.rdf.model.*;
import com.hp.hpl.jena.vocabulary.RDF;
import eu.larkc.csparql.cep.api.RdfQuadruple;
import insight_centre.aceis.eventmodel.EventDeclaration;
import insight_centre.aceis.io.rdf.RDFFileManager;
import insight_centre.aceis.io.streams.DataWrapper;
import insight_centre.aceis.observations.PollutionObservation;
import insight_centre.aceis.observations.SensorObservation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

//import com.google.gson.Gson;

public class CSPARQLAarhusPollutionStream extends CSPARQLSensorStream implements Runnable {
	private final Logger logger = LoggerFactory.getLogger(getClass());
	CsvReader streamData;
	EventDeclaration ed;
	SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd' 'HH:mm:ss");
	private Date startDate = null;
	private Date endDate = null;

	public static void main(String[] args) {
		try {
			List<String> payloads = new ArrayList<String>();
			payloads.add(RDFFileManager.defaultPrefix + "Property-1|" + RDFFileManager.defaultPrefix + "FoI-1|"
					+ RDFFileManager.ctPrefix + "API");
			EventDeclaration ed = new EventDeclaration("testEd", "testsrc", "air_pollution", null, payloads, 5.0);
			CSPARQLAarhusPollutionStream aps = new CSPARQLAarhusPollutionStream("testuri",
					"streams/pollutionData158324.stream", ed);
			Thread th = new Thread(aps);
			th.start();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public CSPARQLAarhusPollutionStream(String uri, String txtFile, EventDeclaration ed) throws IOException {
		super(uri);
		streamData = new CsvReader(String.valueOf(txtFile));
		this.ed = ed;
		streamData.setTrimWhitespace(false);
		streamData.setDelimiter(',');
		streamData.readHeaders();
	}

	public CSPARQLAarhusPollutionStream(String uri, String txtFile, EventDeclaration ed, Date start, Date end)
			throws IOException {
		super(uri);

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
		logger.info("Starting sensor stream: " + this.getIRI());
		try {
			while (streamData.readRecord() && !stop) {
				Date obTime = sdf.parse(streamData.get("timestamp"));
				if (this.startDate != null && this.endDate != null) {
					if (obTime.before(this.startDate) || obTime.after(this.endDate)) {
						logger.debug(this.getIRI() + ": Disgarded observation @" + obTime);
						continue;
					}
				}
				// logger.info("Reading data: " + streamData.toString());

				PollutionObservation po = (PollutionObservation) this.createObservation(streamData);
				this.setChanged();
				this.notifyObservers(po);

				// logger.debug("Reading data: " + new Gson().toJson(po));
				/*List<Statement> stmts = this.getStatements(po);
				for (Statement st : stmts) {
					try {
						final RdfQuadruple q = new RdfQuadruple(st.getSubject().toString(), st.getPredicate()
								.toString(), st.getObject().toString(), System.currentTimeMillis());
						this.put(q);
						logger.debug(this.getIRI() + " Streaming: " + q.toString());

					} catch (Exception e) {
						e.printStackTrace();
						logger.error(this.getIRI() + " CSPARQL streamming error.");
					}
					// messageByte += st.toString().getBytes().length;
				}*/
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
			e.printStackTrace();

		} finally {
			logger.info("Stream Terminated: " + this.getIRI());
			this.stop();
		}

	}

	@Override
	protected SensorObservation createObservation(Object data) {
		try {
			// CsvReader streamData = (CsvReader) data;
			int ozone = Integer.parseInt(streamData.get("ozone")), particullate_matter = Integer.parseInt(streamData
					.get("particullate_matter")), carbon_monoxide = Integer.parseInt(streamData.get("carbon_monoxide")), sulfure_dioxide = Integer
					.parseInt(streamData.get("sulfure_dioxide")), nitrogen_dioxide = Integer.parseInt(streamData
					.get("nitrogen_dioxide"));
			Date obTime = sdf.parse(streamData.get("timestamp"));
			PollutionObservation po = new PollutionObservation(0.0, 0.0, 0.0, ozone, particullate_matter,
					carbon_monoxide, sulfure_dioxide, nitrogen_dioxide, obTime, getIRI());
			// logger.debug(ed.getServiceId() + ": streaming record @" + po.getObTimeStamp());
			po.setObId("AarhusPollutionObservation-" + (int) Math.random() * 10000);
			DataWrapper.waitForInterval(currentObservation, po, startDate, getRate());
			this.currentObservation = po;
			return po;
		} catch (NumberFormatException | IOException | ParseException e) {
			e.printStackTrace();
		}
		return null;

	}
}
