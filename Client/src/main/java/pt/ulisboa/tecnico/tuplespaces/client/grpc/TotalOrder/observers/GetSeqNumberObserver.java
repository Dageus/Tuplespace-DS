package pt.ulisboa.tecnico.tuplespaces.client.grpc.TotalOrder.observers;

import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import pt.ulisboa.tecnico.sequencer.contract.SequencerOuterClass.GetSeqNumberResponse;
import pt.ulisboa.tecnico.tuplespaces.client.grpc.ClientService;
import pt.ulisboa.tecnico.tuplespaces.replicaTotalOrder.contract.TupleSpacesReplicaTotalOrder;


public class GetSeqNumberObserver implements StreamObserver<GetSeqNumberResponse> {

    private int seqNumber;
    private boolean hasReceived;
    private ClientService clientService;

    public GetSeqNumberObserver(ClientService clientService) {
        this.hasReceived = false;
        this.seqNumber = 0;
        this.clientService = clientService;
    }
    @Override
    synchronized public void onNext(GetSeqNumberResponse getSeqNumberResponse) {
        if (!hasReceived) {
            this.seqNumber = getSeqNumberResponse.getSeqNumber();
            this.hasReceived = true;
            notifyAll();
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
                    System.out.println("Sequencer: " + description);
                }
            }
            else {
                System.out.println("Sequencer Status error: " + statusException.getStatus());
            }
        } else {
            System.out.println("Sequencer Error: " + throwable.getMessage());
        }
        System.out.println("Shutting down...");
        clientService.shutDown();
    }


    @Override
    public void onCompleted() {
    }

    synchronized public int waitAndGetSeqNumber() throws InterruptedException{
        if (!hasReceived) {
            wait();
        }
        return this.seqNumber;
    }
}
