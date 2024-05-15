package pt.ulisboa.tecnico.tuplespaces.client.grpc.centralized;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.util.List;
import java.util.ArrayList;

import io.grpc.StatusRuntimeException;
import pt.ulisboa.tecnico.tuplespaces.centralized.contract.TupleSpacesCentralized;
import pt.ulisboa.tecnico.tuplespaces.centralized.contract.TupleSpacesGrpc;
import pt.ulisboa.tecnico.tuplespaces.client.grpc.ClientService;

public class ClientCentralizedService extends ClientService {

    private TupleSpacesGrpc.TupleSpacesBlockingStub serverStub;
    private ManagedChannel serverChannel;

    public ClientCentralizedService(String nsAddress, String service, boolean debugFlag) {
        super(nsAddress, service, debugFlag);
        debug(ClientCentralizedService.class.getSimpleName() + " client started");
    }

    public int connectToServers(String address) {
        debug("Connecting to " + address + "...");
        try {
            this.serverChannel = ManagedChannelBuilder.forTarget(address).usePlaintext().build();
            this.serverStub = TupleSpacesGrpc.newBlockingStub(serverChannel);
            debug("Connected to " + address);
        } catch (StatusRuntimeException e) {
            debug("Couldn't connect to server " + address);
            return -1;
        }
        return 1;
    }
    @Override
    public int connectToServers() {
        debug("Connecting to new Server");
        List<String> servers = this.lookup();
        if (servers == null) {
            // NameServer unavailable
            return -1;
        }
        if (servers.size() == 0) {
            System.out.println("Server unavailable");
            return -1;
        }
        String chosenServerAddress = servers.get(0);
        return this.connectToServers(chosenServerAddress);
    }

    @Override
    public void shutDown() {
        if (this.nameServerChannel != null && !this.nameServerChannel.isShutdown()) {
            this.nameServerChannel.shutdown();
        }
        if (this.serverChannel != null && !this.serverChannel.isShutdown()) {
            this.serverChannel.shutdown();
        }
    }

    @Override
    public void put(String tuple) {
        debug("put: " + tuple);

        if(this.serverChannel == null || this.serverChannel.isShutdown()) {
            if (this.connectToServers() == -1) {
                return;
            }
        }

        TupleSpacesCentralized.PutRequest request = TupleSpacesCentralized.PutRequest.newBuilder().setNewTuple(tuple).build();
        try {
            TupleSpacesCentralized.PutResponse response = this.serverStub.put(request);
            System.out.println(OK);
        } catch (StatusRuntimeException e) {
            if (e.getStatus().getCode() == io.grpc.Status.Code.UNAVAILABLE) {
                this.serverChannel.shutdown();
                System.out.println("Server unavailable");
            } else {
                System.out.println(e.getStatus().getDescription());
            }
        }
    }

    @Override
    public void read(String tuple) {
        debug("read: " + tuple);

        if(this.serverChannel == null || this.serverChannel.isShutdown()) {
            if (this.connectToServers() == -1) {
                return;
            }
        }

        TupleSpacesCentralized.ReadRequest request = TupleSpacesCentralized.ReadRequest.newBuilder().setSearchPattern(tuple).build();
        try {
            TupleSpacesCentralized.ReadResponse response = this.serverStub.read(request);
            System.out.println(OK);
            System.out.println(response.getResult());
        } catch(StatusRuntimeException e) {
            if (e.getStatus().getCode() == io.grpc.Status.Code.UNAVAILABLE) {
                this.serverChannel.shutdown();
                System.out.println("Server unavailable");
            } else {
                System.out.println(e.getStatus().getDescription());
            }
        }
    }

    @Override
    public void take(String tuple) {
        debug("take: " + tuple);

        if(this.serverChannel == null || this.serverChannel.isShutdown()) {
            if (this.connectToServers() == -1) {
                return;
            }
        }

        TupleSpacesCentralized.TakeRequest request = TupleSpacesCentralized.TakeRequest.newBuilder().setSearchPattern(tuple).build();
        try {
            TupleSpacesCentralized.TakeResponse response = this.serverStub.take(request);
            System.out.println(OK);
            System.out.println(response.getResult());
        } catch (StatusRuntimeException e) {
            if (e.getStatus().getCode() == io.grpc.Status.Code.UNAVAILABLE) {
                this.serverChannel.shutdown();
                System.out.println("Server unavailable");
            } else {
                System.out.println(e.getStatus().getDescription());
            }
        }
    }

    @Override
    public void getTupleSpacesState(String qualifier) {
        debug("getTupleSpacesState: " + qualifier);
        String server = this.lookup(qualifier);
        if (server == null) {
            // No servers available or couldn't connect to NameServer
            return;
        }
        if(this.serverChannel == null || this.serverChannel.isShutdown()) {
            this.connectToServers(server);
        }
        TupleSpacesCentralized.getTupleSpacesStateRequest request = TupleSpacesCentralized.getTupleSpacesStateRequest.getDefaultInstance();
        try {
            TupleSpacesCentralized.getTupleSpacesStateResponse response = this.serverStub.getTupleSpacesState(request);
            System.out.println(OK);
            System.out.println(response.getTupleList());
        } catch (StatusRuntimeException e) {
            if (e.getStatus().getCode() == io.grpc.Status.Code.UNAVAILABLE) {
                this.serverChannel.shutdown();
                System.out.println("Server unavailable");
            } else {
                System.out.println(e.getStatus().getDescription());
            }
        }
    }

    @Override
    public void setDelay(int id, int delay) {
        System.out.println("SetDelay");
        System.out.println(OK);
    }
}
