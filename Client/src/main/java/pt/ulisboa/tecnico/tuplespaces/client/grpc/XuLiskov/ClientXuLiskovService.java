
package pt.ulisboa.tecnico.tuplespaces.client.grpc.XuLiskov;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.Collections;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Arrays;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import pt.ulisboa.tecnico.tuplespaces.client.ResponseCollector;
import pt.ulisboa.tecnico.tuplespaces.client.grpc.ClientService;
import pt.ulisboa.tecnico.tuplespaces.client.grpc.XuLiskov.observers.*;
import pt.ulisboa.tecnico.tuplespaces.client.util.OrderedDelayer;
import pt.ulisboa.tecnico.tuplespaces.replicaXuLiskov.contract.TupleSpacesReplicaXuLiskov.*;
import pt.ulisboa.tecnico.tuplespaces.replicaXuLiskov.contract.TupleSpacesReplicaGrpc;
import pt.ulisboa.tecnico.tuplespaces.replicaXuLiskov.contract.TupleSpacesReplicaXuLiskov;


public class ClientXuLiskovService extends ClientService {
    private ManagedChannel[] channels;
    private TupleSpacesReplicaGrpc.TupleSpacesReplicaStub[] stubs;
    private ResponseCollector collector;
    private OrderedDelayer delayer;

    public ClientXuLiskovService(Integer numServers, String nsAddress, String service, boolean debugFlag) {
        super(nsAddress, service, debugFlag);
        this.collector = new ResponseCollector();
        this.delayer = new OrderedDelayer(numServers);
        debug(ClientXuLiskovService.class.getSimpleName() + " client started");
    }

    /* This method allows the command processor to set the request delay assigned to a given server */
    public void setDelay(int id, int delay) {
        delayer.setDelay(id, delay);
        System.out.println("OK");
    }

    /* Get the number of servers */
    public int getNumServers() {
        return this.stubs.length;
    }

    /* Connect to the servers through the stubs */
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
        this.channels = new ManagedChannel[serversAddr.size()];
        this.stubs = new TupleSpacesReplicaGrpc.TupleSpacesReplicaStub[serversAddr.size()];
        for (int i = 0; i < serversAddr.size(); i++) {
            try {
                this.channels[i] = ManagedChannelBuilder.forTarget(serversAddr.get(i)).usePlaintext().build();
                this.stubs[i] = TupleSpacesReplicaGrpc.newStub(this.channels[i]);
            } catch (StatusRuntimeException e) {
                debug("Couldn't connect to server " + serversAddr.get(i));
                return -1;
            }
        }
        return 1;
    }

    /* close the connection */
    @Override
    public void shutDown() {
        if (this.nameServerChannel != null) {
            this.nameServerChannel.shutdown();
        }
        for (ManagedChannel ch : channels) {
            if (ch != null) {
                ch.shutdown();
            }
        }
    }

    /* insert a tuple into the Servers */
    public void put(String tuple) {

        // Create the collector
        collector = new ResponseCollector<PutResponse>();

        // Create the request
        TupleSpacesReplicaXuLiskov.PutRequest request = TupleSpacesReplicaXuLiskov.PutRequest.newBuilder().setNewTuple(tuple).build();

        // Spread the request to all servers
        for (int i : delayer) {
            try {
                this.stubs[i].put(request, new PutObserver(i, collector));
            } catch (StatusRuntimeException e) {
                if (e.getStatus().getCode() == io.grpc.Status.Code.UNAVAILABLE) {
                    System.out.println("Server unavailable");
                } else {
                    System.out.println(e.getStatus().getDescription());
                }
            }
        }
        try {
            // Wait for all responses
            collector.waitUntilAllReceived(getNumServers());
            System.out.println("OK");
        } catch (InterruptedException e) {
            System.out.println("Cancelled operation");
        }
    }

    public void read(String tuple) {

        // Create the collector
        collector = new ResponseCollector<ReadResponse>();

        // Create the request
        TupleSpacesReplicaXuLiskov.ReadRequest request = TupleSpacesReplicaXuLiskov.ReadRequest.newBuilder().setSearchPattern(tuple).build();

        // Create a new thread such that one thread makes the requests and other waits for the responses
        // otherwise the client would only start waiting after all requests were sent
        Thread thread = new Thread(() -> {
            for (int i : delayer) {
                try {
                    stubs[i].read(request, new ReadObserver(i, collector));
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
            // Wait for first response only
            collector.waitUntilAllReceived(1);
            Optional<ReadResponse> response = collector.getStringsList().values().stream().filter(Objects::nonNull).findFirst();
            System.out.println("OK");
            System.out.println(response.get().getResult());
        } catch (InterruptedException e) {
            System.out.println("Cancelled operation");
        }
    }

    public List<List<String>> takePhase1(String tuple) {
        debug("TakePhase1: pattern: " + tuple);

        // Create the collector
        collector = new ResponseCollector<TakePhase1Response>();
        
        // Create the request
        TupleSpacesReplicaXuLiskov.TakePhase1Request request = TupleSpacesReplicaXuLiskov.TakePhase1Request.newBuilder()
                .setSearchPattern(tuple).setClientId(getClientID()).build();

        // Spread the request to all servers
        try {
            for (Integer i: delayer) {
                this.stubs[i].takePhase1(request, new TakePhase1Observer(i, collector));
            }
            // Wait for all responses
            collector.waitUntilAllReceived(getNumServers());
        } catch (StatusRuntimeException e) {
            if (e.getStatus().getCode() == io.grpc.Status.Code.UNAVAILABLE) {
                System.out.println("Server unavailable");
            } else {
                System.out.println(e.getStatus().getDescription());
            }
        } catch (InterruptedException e) {
            // Was interrupted, release locks before dying
            takePhase1Release();
            throw new RuntimeException(e);
        }

        // List of reserved tuples of each server
        List<List<String>> lists = (List<List<String>>) collector.getStringsList().values().stream()
            .map(response -> ((TakePhase1Response) response).getReservedTuplesList())
            .collect(Collectors.toList());

        return lists;
    }

    public boolean takePhase1Release() {
        debug("TakePhase1Release: releasing...");

        // Create the collector
        collector = new ResponseCollector<TakePhase1ReleaseResponse>();

        // Create the request
        TupleSpacesReplicaXuLiskov.TakePhase1ReleaseRequest request = TupleSpacesReplicaXuLiskov.TakePhase1ReleaseRequest.newBuilder()
                .setClientId(getClientID()).build();

        // Spread the request to all servers
        try {
            for (int i: delayer)  {
                this.stubs[i].takePhase1Release(request, new TakePhase1ReleaseObserver(i, collector));
            }
            // Wait for all responses
            collector.waitUntilAllReceived(getNumServers());
        } catch (InterruptedException e) {
            debug("Cancelled takePhase1Release");
            return false;
        } catch (StatusRuntimeException e) {
            if (e.getStatus().getCode() == io.grpc.Status.Code.UNAVAILABLE) {
                System.out.println("Server unavailable");
            } else {
                System.out.println(e.getStatus().getDescription());
            }
        }

        debug("TakePhase1Release: all released");
        return true;
    }

    public void takePhase2(String tuple) {
        debug("TakePhase2: taking:" + tuple);

        // Create the collector
        collector = new ResponseCollector<TakePhase2Response>();

        // Create the request
        TupleSpacesReplicaXuLiskov.TakePhase2Request request = TupleSpacesReplicaXuLiskov.TakePhase2Request.newBuilder()
                .setTuple(tuple).setClientId(getClientID()).build();

        // Spread the request to all servers
        try {
            for (int i: delayer) {
                this.stubs[i].takePhase2(request, new TakePhase2Observer(i, collector));
            }
            // Wait for all responses
            collector.waitUntilAllReceived(getNumServers());
        } catch (InterruptedException e) {
            System.out.println("Cancelled operation");
            return;
        } catch (StatusRuntimeException e) {
            if (e.getStatus().getCode() == io.grpc.Status.Code.UNAVAILABLE) {
                System.out.println("Server unavailable");
            } else {
                System.out.println(e.getStatus().getDescription());
            }
        }
        debug("TakePhase2: took:" + tuple);
    }

    public void take(String pattern) {
        List<List<String>> lists;
        List<String> intersection = new ArrayList<String>();

        // try to take the tuple until all the servers have accepted it
        while (true) {
            lists = takePhase1(pattern);
            // Count not empty lists
            int nonEmptyLists = (int) lists.stream().filter(list -> !list.isEmpty()).count();

            if (!collector.isValid()) {
                // Invalid arguments
                return;
            }
            else if (collector.getAcceptedCounter() < 2) {
                // Minority have accepted -> Release, Repeat TakePhase1
                takePhase1Release();
            }
            else if (collector.getAcceptedCounter() == 3 && nonEmptyLists == 3) {
                // All replicas accepted
                intersection = new ArrayList<>(lists.get(0));
                for (List<String> list : lists) {
                    // Intersect the lists
                    intersection.retainAll(list);
                }
                if (!intersection.isEmpty()) {
                    // Found tuple
                    break;
                }
                // Intersection is empty => Repeat TakePhase1
            }
        }

        // Intersect the lists
        String randomElement = null;
        if (!intersection.isEmpty()) {
            // Select a random tuple from the intersection
            // Seed for debugging purposes
            Random rand = new Random(123456789L);
            int randomIndex = rand.nextInt(intersection.size());
            // Selected tuple
            randomElement = intersection.get(randomIndex);
        }
        debug("Random Element chosen:" + randomElement);

        // Take selected tuple
        takePhase2(randomElement);
        System.out.println("OK");
        System.out.println(randomElement);
    }

    public void getTupleSpacesState(String qualifier) {
        debug("getTupleSpacesState: " + qualifier);

        String serverAddr = this.lookup(qualifier);
        if (serverAddr == null) {
            // No servers available or couldn't connect to NameServer
            return;
        }

        // Create the collector
        collector = new ResponseCollector<getTupleSpacesStateResponse>();

        // Create the request
        TupleSpacesReplicaXuLiskov.getTupleSpacesStateRequest request = TupleSpacesReplicaXuLiskov.getTupleSpacesStateRequest.getDefaultInstance();

        // Spread the request to all servers
        for (int i = 0; i < this.stubs.length; i++) {
            if (serverAddr.equals(this.channels[i].authority())) {

                try {
                    this.stubs[i].getTupleSpacesState(request, new getTupleSpacesStateObserver(collector));
                    collector.waitUntilAllReceived(1);
                } catch (StatusRuntimeException e) {
                    if (e.getStatus().getCode() == io.grpc.Status.Code.UNAVAILABLE) {
                        System.out.println("Server unavailable");
                    } else {
                        System.out.println(e.getStatus().getDescription());
                    }
                } catch (InterruptedException e) {
                    debug("Cancelled getTupleSpacesState");
                    return;
                }
                // Print TupleSpace
                System.out.println("OK");
                // Get the first and only response that contains the tuple for printing
                System.out.println((List<String>) collector.getStringsList().values().stream()
                        .map(response -> ((getTupleSpacesStateResponse) response).getTupleList())
                        .findFirst().orElse(Collections.emptyList()));
                return;
            }
        }
        System.out.println("No Server found with address: " + serverAddr);
    }
}
