package pt.ulisboa.tecnico.tuplespaces.server.domain.TotalOrder;

import java.util.concurrent.locks.Condition;

public class TakeEntry {

    private int seqNumber;
    private String pattern;
    private Condition condition;

    public TakeEntry(String pattern, int seqNumber, Condition condition) {
        this.condition = condition;
        this.pattern = pattern;
        this.seqNumber = seqNumber;
    }

    public String getPattern() {
        return pattern;
    }

    public void setPattern(String pattern) {
        this.pattern = pattern;
    }

    public int getSeqNumber() {
        return seqNumber;
    }

    public Condition getCondition() {
        return condition;
    }
}
