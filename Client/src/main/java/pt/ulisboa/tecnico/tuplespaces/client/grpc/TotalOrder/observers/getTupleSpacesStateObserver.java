package pt.ulisboa.tecnico.tuplespaces.client.grpc.TotalOrder.observers;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import pt.ulisboa.tecnico.tuplespaces.client.grpc.ClientService;
import pt.ulisboa.tecnico.tuplespaces.replicaTotalOrder.contract.TupleSpacesReplicaTotalOrder.getTupleSpacesStateResponse;

import java.util.List;
import java.util.ArrayList;

public class getTupleSpacesStateObserver implements StreamObserver<getTupleSpacesStateResponse> {

    private List<String> result;
    private boolean hasReceived;
    private ClientService clientService;

    public getTupleSpacesStateObserver(ClientService clientService) {
        this.result = new ArrayList<String>();
        this.clientService = clientService;
        this.hasReceived = false;
    }

    @Override
    synchronized public void onNext(getTupleSpacesStateResponse r) {
        if (result.isEmpty()) {
            this.result = r.getTupleList();
        }
    }

    @Override
    public void onError(Throwable throwable) {
        if (throwable instanceof StatusRuntimeException) {
            StatusRuntimeException statusException = (StatusRuntimeException) throwable;
            io.grpc.Status status = statusException.getStatus();
            if (status != null) {
                String description = status.getDescription();
                if (description != null && !description.isEmpty()) {
                    System.out.println("Server: " + description);
                }
            }
            else {
                System.out.println("Server Status error: " + statusException.getStatus());
            }
        } else {
            System.out.println("Server Error: " + throwable.getMessage());
        }
        System.out.println("Shutting down...");
        clientService.shutDown();
    }

    @Override
    synchronized public void onCompleted() {
        System.out.println("OK");
        this.hasReceived = true;
        notifyAll();
    }

    synchronized public List<String> waitAndGetResult() throws InterruptedException {
        if (!hasReceived) {
            wait();
        }
        return this.result;
    }
}
