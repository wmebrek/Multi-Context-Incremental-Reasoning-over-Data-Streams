package insight_centre.aceis.io.streams.brut;

import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import com.hp.hpl.jena.rdf.model.Resource;
import com.hp.hpl.jena.rdf.model.Statement;
import com.hp.hpl.jena.vocabulary.RDF;
import eu.larkc.csparql.cep.api.RdfQuadruple;
import insight_centre.aceis.eventmodel.EventDeclaration;
import insight_centre.aceis.io.rdf.RDFFileManager;
import insight_centre.aceis.observations.SensorObservation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

//import org.insight.engine.ContextualFilteringManager;

public class CSPARQLLocationStream extends CSPARQLSensorStream implements Runnable {
	private static final Logger logger = LoggerFactory.getLogger(CSPARQLLocationStream.class);
	private String txtFile;
	private EventDeclaration ed;

	// private ContextualFilteringManager cfm;

	public CSPARQLLocationStream(String uri, String txtFile, EventDeclaration ed) {
		super(uri);
		this.txtFile = txtFile;
		this.ed = ed;

	}

	// public UserLocationStream(ExecContext context, String uri, String txtFile, EventDeclaration ed,
	// ContextualFilteringManager cfm) {
	// super(context, uri);
	// this.txtFile = txtFile;
	// this.ed = ed;
	// this.cfm = cfm;
	//
	// }

	@Override
	public void run() {
		logger.info("Starting sensor stream: " + this.getIRI());
		try {
			if (txtFile.contains("Location")) {
				BufferedReader reader = new BufferedReader(new FileReader(txtFile));
				String strLine;
				while ((strLine = reader.readLine()) != null && (!stop)) {
					SensorObservation po = this.createObservation(strLine);
					this.setChanged();
					this.notifyObservers(po);

					//List<Statement> stmts = this.getStatements(this.createObservation(strLine));

					/*long messageByte = 0;
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
					}*/
					// this.messageCnt += 1;
					// this.byteCnt += messageByte;
					// cw.write(new SimpleDateFormat("hh:mm:ss").format(new Date()));
					// cw.write(this.messageCnt + "");
					// cw.write(this.byteCnt + "");
					// cw.endRecord();
					// cw.flush();
					// stream(n(RDFFileManager.upPrefix + "007"), n(RDFFileManager.ctPrefix + "detectedAt"),
					// n(RDFFileManager.defaultPrefix + "FoI-c6edd705-66a5-45c1-9d93-220da78f9421"));
					if (sleep > 0) {
						try {
							Thread.sleep(sleep);
						} catch (InterruptedException e) {

							e.printStackTrace();

						}
					}
				}
			}

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@Override
	protected SensorObservation createObservation(Object data) {
		String str = data.toString();
		String userStr = str.split("\\|")[0];
		String coordinatesStr = str.split("\\|")[1];
		SensorObservation so = new SensorObservation();
		so.setFoi(userStr);
		// so.setServiceId(this.getURI());
		so.setValue(coordinatesStr);
		so.setObTimeStamp(new Date());
		so.setObId("UserLocationObservation-" + (int) Math.random() * 10000);
		so.setIri(getIRI());
		// return so;
		this.currentObservation = so;
		return so;
	}

}
