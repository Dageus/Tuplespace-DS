package pt.ulisboa.tecnico.tuplespaces.client.grpc.TotalOrder;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import pt.ulisboa.tecnico.tuplespaces.client.grpc.ClientService;
import pt.ulisboa.tecnico.sequencer.contract.SequencerGrpc;
import pt.ulisboa.tecnico.tuplespaces.client.grpc.TotalOrder.observers.GetSeqNumberObserver;
import pt.ulisboa.tecnico.tuplespaces.client.grpc.TotalOrder.observers.getTupleSpacesStateObserver;
import pt.ulisboa.tecnico.tuplespaces.client.grpc.TotalOrder.observers.PutObserver;
import pt.ulisboa.tecnico.tuplespaces.client.grpc.TotalOrder.observers.ReadObserver;
import pt.ulisboa.tecnico.tuplespaces.client.grpc.TotalOrder.observers.TakeObserver;
import pt.ulisboa.tecnico.tuplespaces.client.util.OrderedDelayer;
import pt.ulisboa.tecnico.tuplespaces.replicaTotalOrder.contract.TupleSpacesReplicaGrpc;
import pt.ulisboa.tecnico.tuplespaces.replicaTotalOrder.contract.TupleSpacesReplicaTotalOrder;
import pt.ulisboa.tecnico.sequencer.contract.SequencerOuterClass.GetSeqNumberRequest;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.ArrayList;


public class ClientTotalOrderService extends ClientService {
    private final String sequencerAddress = "localhost:8080";
    private final OrderedDelayer delayer;
    private TupleSpacesReplicaGrpc.TupleSpacesReplicaStub[] stubs;
    private ManagedChannel[] serversChannels;
    private SequencerGrpc.SequencerStub sequencerStub;
    private ManagedChannel sequencerChannel;

    public ClientTotalOrderService(int numServers, String nsAddress, String service, boolean debugFlag) {
        super(nsAddress, service, debugFlag);
        this.delayer = new OrderedDelayer(numServers);
        debug(ClientTotalOrderService.class.getSimpleName() + " client started");
    }

    public int getNumServers() {
        return this.stubs.length;
    }

    public int connectToServers() {
        debug("Connecting...");
        List<String> serversAddr = this.lookup();
        if (serversAddr == null) {
            // NameServer unavailable
            return -1;
        }
        if (serversAddr.size() == 0) {
            System.out.println("Servers unavailable");
            return -1;
        }
        /* if the servers aren't all available */
        else if (serversAddr.size() != 3) {
            System.out.println("Only " + serversAddr.size() + " servers available");
            return -1;
        }
        try {
            this.sequencerChannel = ManagedChannelBuilder.forTarget(sequencerAddress).usePlaintext().build();
            this.sequencerStub = SequencerGrpc.newStub(this.sequencerChannel);
        } catch (StatusRuntimeException e) {
            debug("Couldn't connect to sequencer: " + sequencerAddress);
            return -1;
        }
        this.serversChannels = new ManagedChannel[serversAddr.size()];
        this.stubs = new TupleSpacesReplicaGrpc.TupleSpacesReplicaStub[serversAddr.size()];
        for (int i = 0; i < serversAddr.size(); i++) {
            try {
                this.serversChannels[i] = ManagedChannelBuilder.forTarget(serversAddr.get(i)).usePlaintext().build();
                this.stubs[i] = TupleSpacesReplicaGrpc.newStub(this.serversChannels[i]);
            } catch (StatusRuntimeException e) {
                debug("Couldn't connect to server: " + serversAddr.get(i));
                return -1;
            }
        }
        return 1;
    }

    public void shutDown() {
        if (this.nameServerChannel != null) {
            this.nameServerChannel.shutdown();
        }
        if(this.sequencerChannel != null) {
            this.sequencerChannel.shutdown();
        }
        for (ManagedChannel ch : serversChannels) {
            if (ch != null) {
                ch.shutdown();
            }
        }
        System.exit(0);
    }

    public int getSequenceNumber() throws InterruptedException {

        // Request sequence number
        GetSeqNumberObserver obs = new GetSeqNumberObserver(this);
        this.sequencerStub.getSeqNumber(GetSeqNumberRequest.getDefaultInstance(), obs);

        // Wait for response and return designated sequence number
        return obs.waitAndGetSeqNumber();
    }

    public void put(String tuple) {
        debug("Put: " + tuple);

        // Get sequence number for put request
        int seqNumber = 0;
        try {
            seqNumber = getSequenceNumber();
        } catch (InterruptedException e) {
            System.out.println("Cancelled operation");
            return;
        }
        debug("Request: " + seqNumber);

        // Create put request
        TupleSpacesReplicaTotalOrder.PutRequest request = TupleSpacesReplicaTotalOrder.PutRequest.newBuilder()
                .setNewTuple(tuple).setSeqNumber(seqNumber).build();

        // Create shared put observer
        PutObserver obs = new PutObserver(this);

        // Create a new thread such that one thread makes the requests and other waits for the responses
        // otherwise the client would only start waiting after all requests were sent
        Thread thread = new Thread(() -> {
            try {
                for (Integer i : delayer) {
                    this.stubs[i].put(request, obs);
                }
            } catch (StatusRuntimeException e) {
                if (e.getStatus().getCode() == io.grpc.Status.Code.UNAVAILABLE) {
                    System.out.println("Server unavailable");
                } else {
                    System.out.println(e.getStatus().getDescription());
                }
                return;
            }
        });

        // Start sending requests
        thread.start();

        try {
            // Wait for all OK messages before proceeding to the next operations
            // for avoiding misinterpretation of OK messages related to previous operations
            obs.waitResult(3);
        } catch (InterruptedException e) {
            System.out.println("Cancelled operation");
        }
    }

    public void read(String pattern) {

        // Create read request
        TupleSpacesReplicaTotalOrder.ReadRequest request = TupleSpacesReplicaTotalOrder.ReadRequest.newBuilder()
                .setSearchPattern(pattern).build();

        // Create read observer
        ReadObserver obs = new ReadObserver(this);

        // Create a new thread such that one thread makes the requests and other waits for the responses
        // otherwise the client would only start waiting after all requests were sent
        Thread thread = new Thread(() -> {
            for (int i : delayer) {
                try {
                    stubs[i].read(request, obs);
                } catch (StatusRuntimeException e) {
                    if (e.getStatus().getCode() == io.grpc.Status.Code.UNAVAILABLE) {
                        System.out.println("Server unavailable");
                    } else {
                        System.out.println(e.getStatus().getDescription());
                    }
                }
            }
        });

        // Start sending requests
        thread.start();

        try {
            // Wait for first response and print tuple
            System.out.println(obs.waitAndGetResult(1));

            // Wait for all OK messages before proceeding to the next operations
            // for avoiding misinterpretation of OK messages related to previous operations
            obs.waitAndGetResult(3);
        } catch (InterruptedException e) {
            System.out.println("Cancelled operation");
        }
    }

    public void take(String pattern) {
        debug("Take: " + pattern);

        // Get sequence number for take request
        int seqNumber = 0;
        try {
            seqNumber = getSequenceNumber();
        } catch (InterruptedException e) {
            System.out.println("Cancelled operation");
            return;
        }
        debug("Request: " + seqNumber);

        // Create take request
        TupleSpacesReplicaTotalOrder.TakeRequest request = TupleSpacesReplicaTotalOrder.TakeRequest.newBuilder()
                .setSearchPattern(pattern).setSeqNumber(seqNumber).build();

        // Create take observer
        TakeObserver obs = new TakeObserver(this);

        // Create a new thread such that one thread makes the requests and other waits for the responses
        // otherwise the client would only start waiting after all requests were sent
        Thread thread = new Thread(() -> {
            try {
                for (Integer i : delayer) {
                    this.stubs[i].take(request, obs);
                }
            } catch (StatusRuntimeException e) {
                if (e.getStatus().getCode() == io.grpc.Status.Code.UNAVAILABLE) {
                    System.out.println("Server unavailable");
                } else {
                    System.out.println(e.getStatus().getDescription());
                }
                return;
            }
        });

        // Start sending requests
        thread.start();

        try {
            // Wait for first response and get tuple
            System.out.println(obs.waitAndGetResult(1));

            // Wait for all OK messages before proceeding to the next operations
            // for avoiding misinterpretation of OK messages related to previous operations
            obs.waitAndGetResult(3);
        }  catch (InterruptedException e) {
            System.out.println("Cancelled operation");
        }
    }

    public void getTupleSpacesState(String qualifier) {
        debug("getTupleSpacesState: " + qualifier);

        String serverAddr = this.lookup(qualifier);
        if (serverAddr == null) {
            // No servers available or couldn't connect to NameServer
            return;
        }
        // Create the request
        TupleSpacesReplicaTotalOrder.getTupleSpacesStateRequest request = TupleSpacesReplicaTotalOrder.
                getTupleSpacesStateRequest.getDefaultInstance();

        // Create observer
        getTupleSpacesStateObserver obs = new getTupleSpacesStateObserver(this);

        for (int i = 0; i < this.stubs.length; i++) {
            // Check if channel belongs to address
            if (serverAddr.equals(this.serversChannels[i].authority())) {
                try {
                    this.stubs[i].getTupleSpacesState(request, obs);
                } catch (StatusRuntimeException e) {
                    if (e.getStatus().getCode() == io.grpc.Status.Code.UNAVAILABLE) {
                        System.out.println("Server unavailable");
                    } else {
                        System.out.println(e.getStatus().getDescription());
                    }
                    return;
                }

                try {
                    // Wait for response and print TupleSpacesState
                    System.out.println(obs.waitAndGetResult());
                } catch(InterruptedException e) {
                    System.out.println("Cancelled operation");
                }
                return;
            }
        }
        System.out.println("No Server found with address: " + serverAddr);
    }


    public void setDelay(int id, int delay) {
        this.delayer.setDelay(id, delay);
        System.out.println("OK");
    }

}
