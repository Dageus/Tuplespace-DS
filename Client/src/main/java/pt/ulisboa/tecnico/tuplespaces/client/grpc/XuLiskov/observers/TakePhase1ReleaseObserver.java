package pt.ulisboa.tecnico.tuplespaces.client.grpc.XuLiskov.observers;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import pt.ulisboa.tecnico.tuplespaces.client.ResponseCollector;
import pt.ulisboa.tecnico.tuplespaces.replicaXuLiskov.contract.TupleSpacesReplicaXuLiskov.*;

public class TakePhase1ReleaseObserver implements StreamObserver<TakePhase1ReleaseResponse> {
    
    private final ResponseCollector<TakePhase1ReleaseResponse> collector;
    private final Integer identifier;

    public TakePhase1ReleaseObserver(Integer identifier, ResponseCollector<TakePhase1ReleaseResponse> collector) {
        this.collector = collector;
        this.identifier = identifier;
    }
    @Override
    public void onNext(TakePhase1ReleaseResponse r) {
        this.collector.addString(identifier, r);
    }

    @Override
    public void onError(Throwable throwable) {
        if (throwable instanceof StatusRuntimeException) {
            StatusRuntimeException e = (StatusRuntimeException) throwable;
            Status.Code code = e.getStatus().getCode();
            if (code == Status.Code.UNAVAILABLE) {
                System.out.println("Server unavailable");
            }
            else {
                System.out.println(e.getStatus().getDescription());
            }
        } else {
            System.out.println("Unknown error: " + throwable.getMessage());
        }
    }

    @Override
    public void onCompleted() {
    }
}
