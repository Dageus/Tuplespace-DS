 package pt.ulisboa.tecnico.tuplespaces.client.grpc.XuLiskov.observers;

import java.util.ArrayList;
import java.util.Collections;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import pt.ulisboa.tecnico.tuplespaces.client.ResponseCollector;
import pt.ulisboa.tecnico.tuplespaces.replicaXuLiskov.contract.TupleSpacesReplicaXuLiskov.TakePhase1Response;

 public class TakePhase1Observer implements StreamObserver<TakePhase1Response>{
     
    private final ResponseCollector<TakePhase1Response> collector;

    private final Integer identifier;

    public TakePhase1Observer(Integer identifier, ResponseCollector<TakePhase1Response> collector) {
        this.collector = collector;
        this.identifier = identifier;
    }

    @Override
    public void onNext(TakePhase1Response r) {
        // Server accepted the request
        this.collector.addString(identifier, r);
        collector.incrementAcceptedCounter();
    }

    @Override
    public void onError(Throwable throwable) {
        if (throwable instanceof StatusRuntimeException) {
            StatusRuntimeException e = (StatusRuntimeException) throwable;
            Status.Code code = e.getStatus().getCode();
            if (code == Status.Code.UNAVAILABLE) {
                System.out.println("Server unavailable");
            }
            else if (e.getStatus().getCode() == Status.Code.CANCELLED) {
                // Replica refused the TakePhase1 request
                // Therefore don't increment accepted counter and create empty tuple list response
                TakePhase1Response r = TakePhase1Response.newBuilder().addAllReservedTuples(Collections.emptyList()).build();
                this.collector.addString(identifier, r);
            }
            else if (e.getStatus().getCode() == Status.Code.INVALID_ARGUMENT) {
                // invalid argument
                TakePhase1Response r = TakePhase1Response.newBuilder().addAllReservedTuples(Collections.emptyList()).build();
                // if statement for only changing valid status once
                if(this.collector.changeValid()){
                    System.out.println(e.getStatus().getDescription());
                }
                this.collector.addString(identifier, r);
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
        // TODO
    }
 }