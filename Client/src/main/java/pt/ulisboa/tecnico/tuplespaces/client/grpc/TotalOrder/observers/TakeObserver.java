package pt.ulisboa.tecnico.tuplespaces.client.grpc.TotalOrder.observers;

import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import pt.ulisboa.tecnico.tuplespaces.client.grpc.ClientService;
import pt.ulisboa.tecnico.tuplespaces.replicaTotalOrder.contract.TupleSpacesReplicaTotalOrder.TakeResponse;

public class TakeObserver implements StreamObserver<TakeResponse> {

    private String result;
    private int hasReceived;
    private ClientService clientService;

    public TakeObserver(ClientService clientService) {
        this.hasReceived = 0;
        this.result = null;
        this.clientService = clientService;
    }

    @Override
    synchronized public void onNext(TakeResponse r) {
        if (result == null) {
            this.result = r.getResult();
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
        this.hasReceived++;
        notifyAll();
    }


    synchronized public String waitAndGetResult(int n) throws InterruptedException {
        while (hasReceived < n) {
            wait();
        }
        return this.result;
    }
}
