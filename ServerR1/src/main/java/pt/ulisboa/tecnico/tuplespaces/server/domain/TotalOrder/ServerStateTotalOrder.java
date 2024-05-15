package pt.ulisboa.tecnico.tuplespaces.server.domain.TotalOrder;

import java.util.List;
import java.util.TreeSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.Condition;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;

public class ServerStateTotalOrder {

    private static final String BGN_TUPLE = "<";
    private static final String END_TUPLE = ">";

    private int nextSeqNumber;

    private List<String> tuples;

    // Lock for Read and Write in TupleSpaces
    private ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    // Lock for waiting calls
    private ReentrantLock waitLock = new ReentrantLock();

    // Condition for Read operations
    private Condition readCond = waitLock.newCondition();

    // Condition for Take operations
    private Condition takeCond = waitLock.newCondition();

    // Lock for sequence number
    private ReentrantLock seqLock = new ReentrantLock();

    // Condition for sequence number
    private Condition seqLockCond = seqLock.newCondition();

    private HashMap<String, TreeSet<TakeEntry>> takeWaitList = new HashMap<String, TreeSet<TakeEntry>>();

    private final boolean debugFlag;

    public ServerStateTotalOrder(boolean debugFlag) {
        this.debugFlag = debugFlag;
        this.tuples = new LinkedList<String>();
        this.nextSeqNumber = 1;
    }
    public boolean tupleIsValid(String tuple) {
        if(tuple == null || !tuple.substring(0,1).equals(BGN_TUPLE) || !tuple.endsWith(END_TUPLE)
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

    public void debug(String debugMessage) {
        if (this.debugFlag)
            System.err.println(debugMessage);
    }

    private void incrementSeqNumber() {
        seqLock.lock();
        try {
            nextSeqNumber++;
            seqLockCond.signalAll();
        } finally {
            seqLock.unlock();
        }
    }

    private void waitForSeqNumber(int seqNumber) throws InterruptedException {
        seqLock.lock();
        try {
            while (nextSeqNumber != seqNumber) {
                // Wait for its turn
                seqLockCond.await();
            }
        } finally {
            seqLock.unlock();
        }
    }

    public void put(String tuple, int seqNumber) throws InterruptedException {

        // Wait for its turn
        waitForSeqNumber(seqNumber);
        debug("Put: " + seqNumber);

        // Check if any take is in wait list
        List<String> matchingPatterns = getTakeWaitListMatchingPatterns(tuple);

        Integer minimum = Integer.MAX_VALUE;
        TakeEntry takeEntry = null;

        // Find take entry with minimum sequence number for matching patterns
        for (String pattern : matchingPatterns) {
            TreeSet<TakeEntry> takeEntries = takeWaitList.get(pattern);
            TakeEntry entry = takeEntries.first();
            if (entry.getSeqNumber() < minimum) {
                // Select least recent take entry
                minimum = entry.getSeqNumber();
                takeEntry = entry;
            }
        }

        if (takeEntry != null) {
            // There is a take waiting and takeEntry is the least recent take
            // Not adding tuple to TupleSpace and instead delivering directly to take entry
            takeEntry.setPattern(tuple);
            debug("Added directly " + tuple);

            waitLock.lock();
            try {
                // Signal take entry for take operation completion
                takeEntry.getCondition().signal();
                // Wait for take to end before incrementing sequence number
                takeCond.await();
            } finally {
                waitLock.unlock();
            }
        }
        else {
            debug("Added " + tuple);
            // No take is waiting therefore adding to TupleSpace
            lock.writeLock().lock();
            try {
                tuples.add(tuple);
            } finally {
                lock.writeLock().unlock();
            }

            waitLock.lock();
            try {
                // Signal all waiting read operations
                readCond.signalAll();
            } finally {
                waitLock.unlock();
            }
        }
    incrementSeqNumber();
    }

    private String getMatchingTuple(String pattern) {
        for (String tuple : this.tuples) {
            if (tuple.matches(pattern)) {
                return tuple;
            }
        }
        return null;
    }

    private List<String> getTakeWaitListMatchingPatterns(String newPattern) {
        List<String> matchingPatterns = new LinkedList<>();
        for (String pattern : takeWaitList.keySet()) {
            if (newPattern.matches(pattern)) {
                matchingPatterns.add(pattern);
            }
        }
        return matchingPatterns;
    }

    public String read(String pattern) throws InterruptedException {
        lock.readLock().lock();
        try {
            String tuple = getMatchingTuple(pattern);
            while (tuple == null) {
                // tuple not found => release lock and wait
                lock.readLock().unlock();
                waitLock.lock();
                try {
                    // wait for another put
                    readCond.await();
                } finally {
                    waitLock.unlock();
                }
                // Ready to search again
                lock.readLock().lock();
                tuple = getMatchingTuple(pattern);
            }
            debug("Read tuple: " + tuple);
            return tuple;
        } finally {
            lock.readLock().unlock();
        }
    }

    public String take(String pattern, int seqNumber) throws InterruptedException {
        // Wait for its turn
        waitForSeqNumber(seqNumber);
        debug("Take: " + seqNumber);
        String tuple = null;
        if (getTakeWaitListMatchingPatterns(pattern).isEmpty()) {
            // No takes waiting for pattern
            tuple = getMatchingTuple(pattern);
        }
        if (tuple == null) {
            // No matching tuple

            // Create new take entry with specific pattern, condition and sequence number
            Condition takeEntryCondition = waitLock.newCondition();
            TakeEntry takeEntry = new TakeEntry(pattern, seqNumber, takeEntryCondition);

            // Check if pattern already exists
            TreeSet<TakeEntry> takeEntriesSet = takeWaitList.get(pattern);
            if (takeEntriesSet == null) {
                // Pattern set doesn't yet exist therefore create it
                // Create Tree Set ordered by sequence number
                takeEntriesSet = new TreeSet<>(Comparator.comparingInt(TakeEntry::getSeqNumber));
                takeWaitList.put(pattern, takeEntriesSet);
            }
            // Add take entry to pattern set
            takeEntriesSet.add(takeEntry);

            waitLock.lock();
            try {
                // Signal next request and wait for specific pattern put
                incrementSeqNumber();
                // Wait for put
                takeEntryCondition.await();
            } finally {
                waitLock.unlock();
            }
            // No need for Tuple Spaces write lock since it's not being directly put in Tuple Spaces
            // Tuple was set directly in entry by put operation
            tuple = takeEntry.getPattern();
            debug("Took directly" + tuple);

            // Remove this take request from pattern set
            takeEntriesSet.remove(takeEntry);
            // If there is no more takes associated with pattern, then remove the pattern set from wait list
            if (takeEntriesSet.isEmpty()) {
                takeWaitList.remove(pattern);
            }

            waitLock.lock();
            try {
                // Signal back put operation that woke up this thread to increase sequence number together
                takeCond.signal();
            } finally {
                waitLock.unlock();
            }
        }
        else {
            lock.writeLock().lock();
            try {
                // Remove tuple from TupleSpaces
                tuples.remove(tuple);
            } finally {
                lock.writeLock().unlock();
            }
            debug("Took " + tuple);
            incrementSeqNumber();
        }
        return tuple;
    }

    public List<String> getTupleSpacesState() {
        lock.readLock().lock();
        try {
            return new ArrayList<>(tuples);
        } finally {
            lock.readLock().unlock();
        }
    }
}

