package pt.ulisboa.tecnico.tuplespaces.server.domain.XuLiskov;

public class TupleSpaceEntry {

    private String tuple; // tuple
    private int clientID = 0; // owner of the lock of the tuple, default=0
    private boolean flag; // tuple is locked

    public TupleSpaceEntry(String tuple, boolean flag) {
        this.tuple = tuple;
        this.flag = flag;
    }

    public String getTuple() {
        return tuple;
    }

    public void setTuple(String tuple) {
        this.tuple = tuple;
    }

    public int getClientID() {
        return clientID;
    }

    public void setClientID(int clientID) {
        this.clientID = clientID;
    }

    public boolean isFlag() {
        return flag;
    }

    public void setFlag(boolean flag) {
        this.flag = flag;
    }
}
