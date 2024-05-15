package pt.ulisboa.tecnico.tuplespaces.server.domain.centralized;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ConcurrentHashMap;

public class ServerState {
  private static final String BGN_TUPLE = "<";
  private static final String END_TUPLE = ">";
  private List<String> tuples;
  private final boolean debugFlag;
  public ServerState(boolean debugFlag) {
    this.debugFlag = debugFlag;
    this.tuples = new CopyOnWriteArrayList<String>();
  }
  public boolean tupleIsValid(String tuple) {
    return tuple.substring(0,1).equals(BGN_TUPLE)
            &&
            tuple.endsWith(END_TUPLE)
            &&
            tuple.length() > 2
            &&
            !tuple.contains(" ");
  }

  public void debug(String debugMessage) {
    if (this.debugFlag)
      System.err.println(debugMessage);
  }

  public void put(String tuple) {
      tuples.add(tuple);
  }

  private String getMatchingTuple(String pattern) {
    for (String tuple : this.tuples) {
      if (tuple.matches(pattern)) {
        return tuple;
      }
    }
    return null;
  }

  public List<String> getAllMatchingTuples(String pattern) {
      List<String> matchingTuples = new ArrayList<String>();
      for (String tuple : this.tuples) {
          if (tuple.matches(pattern)) {
              matchingTuples.add(tuple);
          }
      }
      return matchingTuples;
  }

  public String read(String pattern) {
      return getMatchingTuple(pattern);
  }

  public String take(String pattern) {
      String tuple = getMatchingTuple(pattern);
      if (tuple == null) {
          return null;
      }
      if(tuples.remove(tuple)) {
          debug("Took tuple: " + tuple);
          return tuple;
      }
      debug("Didn't take: " + tuple);
      return tuple;
  }

  public List<String> getTupleSpacesState() {
    return new ArrayList<>(tuples);
  }
}
