package insight_centre.aceis;

import com.hp.hpl.jena.sparql.core.Var;
import eu.larkc.csparql.common.RDFTable;
import eu.larkc.csparql.common.RDFTuple;
import org.deri.cqels.data.Mapping;
import org.deri.cqels.engine.ExecContext;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

import static insight_centre.aceis.RDFTupleSer.toRDFTupleSer;

public class RDFTableSer implements Serializable {

    private List<String> names = new ArrayList();
    private List<RDFTupleSer> tuples = new ArrayList();

    public static RDFTableSer toRDFTableSer(RDFTable rdfTable){
        RDFTableSer rdfTableSer = new RDFTableSer();
        rdfTableSer.names = new ArrayList<>(rdfTable.getNames());
        rdfTableSer.tuples = rdfTable.getTuples().stream().map(t -> toRDFTupleSer(t)).collect(Collectors.toList());
        return rdfTableSer;
    }

    public static RDFTableSer toRDFTableSer(Mapping mapping, ExecContext cqelsContext){
        Iterator<Var> vars = mapping.vars();
        RDFTableSer rdfTableSer = new RDFTableSer();
        RDFTupleSer rdfTupleSer = new RDFTupleSer();
        while (vars.hasNext()) {
            Var value = vars.next();
            String varName = value.getName();

            if(mapping.get(value) != -1) {
                if(!rdfTableSer.names.contains(varName)){
                    rdfTableSer.names.add(varName);
                }
                rdfTupleSer.getFields().add(cqelsContext.engine().decode(mapping.get(value)).toString());
            }
        }
        rdfTableSer.tuples.add(rdfTupleSer);
        //rdfTableSer.names = new ArrayList<>(mapping.getNames());
        //rdfTableSer.tuples = rdfTable.getTuples().stream().map(t -> toRDFTupleSer(t)).collect(Collectors.toList());
        return rdfTableSer;
    }

    public List<String> getNames() {
        return names;
    }

    public List<RDFTupleSer> getTuples() {
        return tuples;
    }

    @Override
    public String toString() {
        return "RDFTableSer{" +
                "names=" + names +
                ", tuples=" + tuples +
                '}';
    }

    public int size() {
        return this.getTuples().size();
    }

    public boolean isEmpty(){
        return tuples.isEmpty();
    }
}
