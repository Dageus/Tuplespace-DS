package pt.ulisboa.tecnico.tuplespaces.client;

import java.util.concurrent.ConcurrentHashMap;

public class ResponseCollector<T> {
    
    private ConcurrentHashMap<Integer, T> collectedResponses;

    private boolean valid = true;
    private int acceptedCounter = 0;


    public ResponseCollector() {
        collectedResponses = new ConcurrentHashMap<Integer, T>();
    }

    synchronized public boolean changeValid() {
        this.valid = !this.valid;
        return this.valid;
    }

    synchronized public boolean isValid() {
        return this.valid;
    }

    public int getAcceptedCounter() {
        return this.acceptedCounter;
    }
    public void incrementAcceptedCounter() {
        this.acceptedCounter++;
    }

    synchronized public void addString(Integer identifier, T s) {
        collectedResponses.put(identifier, s);
        notifyAll(); // notify that received another response
    }

    synchronized public ConcurrentHashMap<Integer, T> getStringsList() {
        return collectedResponses;
    }

    synchronized public void waitUntilAllReceived(int n) throws InterruptedException {
        while (collectedResponses.size() < n)
            wait(); // wait for another response
    }
}