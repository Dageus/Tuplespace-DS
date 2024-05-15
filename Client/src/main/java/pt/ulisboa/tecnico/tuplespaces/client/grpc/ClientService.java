package pt.ulisboa.tecnico.tuplespaces.client.grpc;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import pt.ulisboa.tecnico.name.server.grpc.NameServerGrpc;
import pt.ulisboa.tecnico.name.server.grpc.NameServerOuterClass;

import java.util.List;
import java.util.ArrayList;


public abstract class ClientService {

    public static final String OK = "OK";
    private final String service;
    protected final boolean debugFlag;
    private final String nsAddress;
    private final int clientId;
    protected NameServerGrpc.NameServerBlockingStub nameServerStub;
    protected ManagedChannel nameServerChannel;

    public ClientService(String nsAddress, String service, boolean debugFlag) {
        this.debugFlag = debugFlag;
        this.service = service;
        this.nsAddress = nsAddress;
        this.connectToNameServer();
        this.clientId = requestClientID();
    }

    public abstract int connectToServers();

    public abstract void shutDown();

    public abstract void put(String tuple);

    public abstract void read(String searchPattern);

    public abstract void take(String searchPattern);

    public abstract void getTupleSpacesState(String qualifier);

    public abstract void setDelay(int id, int delay);


    /** Helper method to print debug messages. */
    protected void debug(String debugMessage) {
        if (debugFlag)
            System.err.println(debugMessage);
    }

    public int getClientID() {
        return this.clientId;
    }

    public int connectToNameServer() {
        debug("Connecting to NameServer...");
        try {
            this.nameServerChannel = ManagedChannelBuilder.forTarget(this.nsAddress).usePlaintext().build();
            this.nameServerStub = NameServerGrpc.newBlockingStub(nameServerChannel);
            debug("Connected to NameServer");
        } catch (StatusRuntimeException e) {
            System.out.println("Name Server unavailable");
            return -1;
        }
        return 1;
    }

    public int requestClientID() {
        NameServerOuterClass.RegisterClientRequest request = NameServerOuterClass.RegisterClientRequest.getDefaultInstance();
        try {
            // Get clientID from name server
            return this.nameServerStub.registerClient(request).getId();
        } catch (StatusRuntimeException e) {
            if (e.getStatus().getCode() == io.grpc.Status.Code.UNAVAILABLE) {
                this.nameServerChannel.shutdown();
                System.out.println("Name Server unavailable");
            } else {
                System.out.println(e.getStatus().getDescription());
            }
            return -1;
        }
    }

    public List<String> lookup() {
        debug("lookup: any");

        if(this.nameServerChannel == null || this.nameServerChannel.isShutdown()) {
            this.connectToNameServer();
        }

        List<String> servers = new ArrayList<String>();
        NameServerOuterClass.LookupRequest request = NameServerOuterClass.LookupRequest.newBuilder().setServiceName(this.service).build();
        try {
            servers = this.nameServerStub.lookup(request).getServersList();
        } catch (StatusRuntimeException e) {
            if (e.getStatus().getCode() == io.grpc.Status.Code.UNAVAILABLE) {
                this.nameServerChannel.shutdown();
                System.out.println("Name Server unavailable");
            } else {
                System.out.println(e.getStatus().getDescription());
            }
            return null;
        }
        return servers;
    }
    public String lookup(String qualifier) {
        debug("lookup: " + qualifier);

        if(this.nameServerChannel == null || this.nameServerChannel.isShutdown()) {
            this.connectToNameServer();
        }

        List<String> servers = new ArrayList<>();
        NameServerOuterClass.LookupRequest request = NameServerOuterClass.LookupRequest.newBuilder().setServiceName(this.service).setQualifier(qualifier).build();
        try {
            servers = this.nameServerStub.lookup(request).getServersList();
        } catch (StatusRuntimeException e) {
            if (e.getStatus().getCode() == io.grpc.Status.Code.UNAVAILABLE) {
                this.nameServerChannel.shutdown();
                System.out.println("Name Server unavailable");
            } else {
                System.out.println(e.getStatus().getDescription());
            }
            return null;
        }
        if (servers.size() == 0) {
            System.out.println("Server with qualifier '" + qualifier + "' not available");
            return null;
        }
        return servers.get(0);
    }

}
