package insight_centre.aceis;

import eu.larkc.csparql.common.RDFTuple;

import java.io.Serializable;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

public class RDFTupleSer implements Serializable {
    private List<String> fields = new ArrayList();

    public static RDFTupleSer toRDFTupleSer(RDFTuple rdfTuple) {
        RDFTupleSer rdfTupleSer = new RDFTupleSer();

        Field fieldsRdf = null;
        try {
            fieldsRdf = rdfTuple.getClass().getDeclaredField("fields");
            fieldsRdf.setAccessible(true);
            rdfTupleSer.fields = (List<String>) fieldsRdf.get(rdfTuple);
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }

        return rdfTupleSer;
    }

    public List<String> getFields() {
        return fields;
    }

    @Override
    public String toString() {
        return
                "fields=" + fields;
    }
}
