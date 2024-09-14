package insight_centre.aceis;

import java.io.Serializable;

public class MsgObj implements Serializable {
    String action;
    Object data;

    public MsgObj(String action, Object data){
        this.action = action;
        this.data = data;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public Object getData() {
        return data;
    }

    public void setData(Object data) {
        this.data = data;
    }
}
