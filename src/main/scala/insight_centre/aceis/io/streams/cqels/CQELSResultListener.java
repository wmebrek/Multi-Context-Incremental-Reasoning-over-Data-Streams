package insight_centre.aceis.io.streams.cqels;

import com.hp.hpl.jena.sparql.core.Var;
import org.deri.cqels.data.Mapping;
import org.deri.cqels.engine.ContinuousListener;
import org.deri.cqels.engine.ExecContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class CQELSResultListener implements ContinuousListener {
	private String uri;
	private static final Logger logger = LoggerFactory.getLogger(CQELSResultListener.class);
	public static Set<String> capturedObIds = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
	public static Set<String> capturedResults = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
	ExecContext cqelsContext;
	public CQELSResultListener(String string, ExecContext cqelsContext) {
		setUri(string);
		this.cqelsContext = cqelsContext;
	}

	@Override
	public void update(Mapping mapping) {
		String result = "";
		try {
			Map<String, Long> latencies = new HashMap<String, Long>();
			// int cnt = 0;
			for (Iterator<Var> vars = mapping.vars(); vars.hasNext();) {
				Var var = vars.next();
                String varName = var.getName();

                //logger.debug("resultat cqels => {}", mapping.get(var));
                String varStr = cqelsContext.engine().decode(mapping.get(var)).toString();
                if (varName.contains("obId")) {
                    if (!capturedObIds.contains(varStr)) {
                        capturedObIds.add(varStr);
                        long initTime = main.Program.obMap().get(varStr).getSysTimestamp().getTime();
                        latencies.put(varStr, (System.currentTimeMillis() - initTime));
                    }
                }
                result += " " + varStr;

				/*if(mapping.get(var) != -1) {
					String varStr = main.Program.tempContext().engine().decode(mapping.get(var)).toString();
					if (varName.contains("obId")) {
						if (!capturedObIds.contains(varStr) && main.Program.obMap().get(varStr) != null) {
							capturedObIds.add(varStr);
							long initTime = main.Program.obMap().get(varStr).getSysTimestamp().getTime();
							latencies.put(varStr, (System.currentTimeMillis() - initTime));
						}
					}
					result += " " + varStr;
				}*/
			}
			//logger.info("CQELS result arrived: " + result);
			if (!capturedResults.contains(result)) {
				capturedResults.add(result);
				// uncomment for testing the completeness, i.e., showing how many observations are captured
				// logger.info("CQELS result arrived " + capturedResults.size() + ", obs size: " + capturedObIds.size()
				// + ", result: " + result);
				main.Program.pm().addResults(getUri(), latencies, 1);
			} else {
				logger.debug("CQELS result discarded: " + result);
			}

		} catch (Exception e) {
			logger.error("CQELS decoding error: " + e.getMessage());
			e.printStackTrace();
		}

	}

	public String getUri() {
		return uri;
	}

	public void setUri(String uri) {
		this.uri = uri;
	}

}
