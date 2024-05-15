package pt.ulisboa.tecnico.tuplespaces.server.domain.XuLiskov;

import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.Condition;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;

public class ServerStateXuLiskov {

    private static final String BGN_TUPLE = "<";
    private static final String END_TUPLE = ">";
    private List<TupleSpaceEntry> tuples = new ArrayList<TupleSpaceEntry>();
    private ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private ReentrantLock waitLock = new ReentrantLock();
    private Condition cond = waitLock.newCondition();
    private ReentrantReadWriteLock takeLock = new ReentrantReadWriteLock();

    private final boolean debugFlag;

    public ServerStateXuLiskov(boolean debugFlag) {
        this.debugFlag = debugFlag;
    }

    public void debug(String debugMessage) {
        if (this.debugFlag)
            System.err.println(debugMessage);
    }

    public boolean tupleIsValid(String tuple) {
        if(!tuple.substring(0,1).equals(BGN_TUPLE) || !tuple.endsWith(END_TUPLE)
            || tuple.length() <= 2 || tuple.contains(" "))
            return false;
        try {
            Pattern.compile(tuple);
        } catch (PatternSyntaxException e) {
            // Invalid regex
            return false;
        }
        return true;
    }
    private String getMatchingTuple(String pattern) {
        for (TupleSpaceEntry entry : this.tuples) {
            if (entry.getTuple().matches(pattern)) {
                return entry.getTuple();
            }
        }
        return null;
    }

    private int getMatchingClientTupleIndex(String pattern, int clientID) {
        int size = this.tuples.size();
        for (int i = 0; i < size; i++) {
            TupleSpaceEntry entry = this.tuples.get(i);
            if (entry.getTuple().matches(pattern) && clientID == entry.getClientID() && entry.isFlag()) {
                // found reserved tuple for clientID
                return i;
            }
        }
        return -1;
    }


    public void unlockClientTuples(int clientID) {
        debug("Unlocking: Start (clientID: " + clientID + ")");
        lock.readLock().lock();
        try {
            for (TupleSpaceEntry entry: this.tuples) {
                if (entry.getClientID() == clientID) {
                    // Tuple belongs to client
                    takeLock.writeLock().lock();
                    // Check if it is still locked
                    if (entry.isFlag()) {
                        // Unlock
                        entry.setFlag(false);
                        entry.setClientID(0);
                    }
                    takeLock.writeLock().unlock();
                }
            }
        } finally {
            lock.readLock().unlock();
        }
        debug("Unlocking: Success (clientID: " + clientID + ")");
    }

    public List<String> tryLockMatchingTuples(String pattern, int clientID) {
        List<String> matchingTuples = new ArrayList<String>();
        debug("TryLock: Start (pattern: " + pattern+ ", clientID: " + clientID + ")");

        lock.readLock().lock();
        try {
            for (TupleSpaceEntry entry : this.tuples) {
                if (entry.getTuple().matches(pattern) && !matchingTuples.contains(entry.getTuple())) {
                    // if tuple already exists in matching tuples only locks one of them
                    takeLock.writeLock().lock();
                    if (entry.getClientID() != clientID && entry.isFlag()) {
                        // tuple lock is already taken by other take request
                        takeLock.writeLock().unlock();
                        debug("TryLock: Unsuccessful, (pattern: " + pattern+ ", clientID: " + clientID + ")");
                        unlockClientTuples(clientID);
                        // return null when client doesn't get the lock
                        return null;
                    }
                    // take tuple lock
                    entry.setFlag(true);
                    entry.setClientID(clientID);

                    takeLock.writeLock().unlock();
                    matchingTuples.add(entry.getTuple());
                }
            }
        } finally {
            lock.readLock().unlock();
        }
        debug("TryLock: Locked successfully, (pattern: " + pattern + ", clientID: " + clientID + ")");
        return matchingTuples;
    }

    public int getSize() {
        return tuples.size();
    }

    public boolean put(String tuple) {
        if (!tupleIsValid(tuple)) {
            debug("put: Invalid tuple: " + tuple);
            return false;
        }
        try {
            lock.writeLock().lock();
            tuples.add(new TupleSpaceEntry(tuple, false));
            debug("Added tuple: " + tuple);
            try {
                waitLock.lock();
                cond.signalAll(); // signal all to reading threads waiting
            } finally {
                waitLock.unlock();
            }
        } finally {
            lock.writeLock().unlock();
        }
        return true;
    }

    public String read(String pattern) {
        if (!tupleIsValid(pattern)) {
            debug("read: Invalid pattern: " + pattern);
            return null;
        }

        lock.readLock().lock();
        try {
            String tuple = getMatchingTuple(pattern);
            while (tuple == null) {
                // tuple not found => release lock and wait
                lock.readLock().unlock();
                waitLock.lock();
                try {
                    // wait for another put
                    cond.await();
                } finally {
                    waitLock.unlock();
                }
                // Ready to search again
                lock.readLock().lock();
                tuple = getMatchingTuple(pattern);
            }
            debug("Read tuple: " + tuple);
            return tuple;
        } catch (InterruptedException e) {
            e.printStackTrace();
            return null;
        } finally {
            lock.readLock().unlock();
        }
    }

    public void take(String tuple, int clientID) {
        try{
            lock.writeLock().lock();
            takeLock.writeLock().lock();
            // Search for tuple index for removal since tuple space may have repeated tuples
            // that don't belong to the client locked list
            int tupleIndex = getMatchingClientTupleIndex(tuple, clientID);
            tuples.remove(tupleIndex);
            debug("took: " + tuple);
        } finally {
            takeLock.writeLock().unlock();
            lock.writeLock().unlock();
        }
    }

    public List<String> getTupleSpacesState() {
        List<String> tupleSpaces = new ArrayList<String>();
        try {
            // Can readLock since it's not changing anything
            lock.readLock().lock();
            takeLock.readLock().lock();
            tupleSpaces = new ArrayList<>(tuples).stream().map(TupleSpaceEntry::getTuple).collect(Collectors.toList());
        } finally {
            takeLock.readLock().unlock();
            lock.readLock().unlock();
        }
        return tupleSpaces;
    }
}
