package pt.ulisboa.tecnico.tuplespaces.server.service.TotalOrderService;

import io.grpc.stub.StreamObserver;
import pt.ulisboa.tecnico.tuplespaces.replicaTotalOrder.contract.TupleSpacesReplicaGrpc;
import pt.ulisboa.tecnico.tuplespaces.replicaTotalOrder.contract.TupleSpacesReplicaTotalOrder.*;
import pt.ulisboa.tecnico.tuplespaces.server.domain.TotalOrder.ServerStateTotalOrder;

import static io.grpc.Status.INVALID_ARGUMENT;
import static io.grpc.Status.UNAVAILABLE;

import java.util.List;

public class TupleSpacesTotalOrderService extends TupleSpacesReplicaGrpc.TupleSpacesReplicaImplBase {


    private ServerStateTotalOrder state;
    private final boolean debugFlag;
    public TupleSpacesTotalOrderService(boolean debugFlag) {
        this.state = new ServerStateTotalOrder(debugFlag);
        this.debugFlag = debugFlag;
    }

    private void debug(String debugMessage) {
        if (this.debugFlag)
            System.err.println(debugMessage);
    }
    @Override
    public void put(PutRequest request, StreamObserver<PutResponse> responseObserver) {
        try {
            if (!state.tupleIsValid(request.getNewTuple())) {
                debug("read: Invalid pattern: " + request.getNewTuple());
                responseObserver.onError(INVALID_ARGUMENT.withDescription("Invalid tuple").asRuntimeException());
                return;
            }
            state.put(request.getNewTuple(), request.getSeqNumber());
            responseObserver.onNext(PutResponse.getDefaultInstance());
        } catch (InterruptedException e) {
            responseObserver.onError(UNAVAILABLE.withDescription("Interrupted").asRuntimeException());
        }
        responseObserver.onCompleted();
    }

    @Override
    public void read(ReadRequest request, StreamObserver<ReadResponse> responseObserver) {
        try {
            if (!state.tupleIsValid(request.getSearchPattern())) {
                debug("read: Invalid pattern: " + request.getSearchPattern());
                responseObserver.onError(INVALID_ARGUMENT.withDescription("Invalid pattern").asRuntimeException());
                return;
            }
            String result = state.read(request.getSearchPattern());
            debug("result: " + result);
            responseObserver.onNext(ReadResponse.newBuilder().setResult(result).build());
        } catch (InterruptedException e) {
            responseObserver.onError(UNAVAILABLE.withDescription("Interrupted").asRuntimeException());
            return;
        }
        responseObserver.onCompleted();
    }

    @Override
    public void take(TakeRequest request, StreamObserver<TakeResponse> responseObserver) {
        try {
            if (!state.tupleIsValid(request.getSearchPattern())) {
                debug("read: Invalid pattern: " + request.getSearchPattern());
                responseObserver.onError(INVALID_ARGUMENT.withDescription("Invalid pattern").asRuntimeException());
                return;
            }
            String result = state.take(request.getSearchPattern(), request.getSeqNumber());
            debug("result: " + result);
            responseObserver.onNext(TakeResponse.newBuilder().setResult(result).build());
        } catch (InterruptedException e) {
            responseObserver.onError(UNAVAILABLE.withDescription("Interrupted").asRuntimeException());
            return;
        }
        responseObserver.onCompleted();
    }

    @Override
    public void getTupleSpacesState(getTupleSpacesStateRequest request, StreamObserver<getTupleSpacesStateResponse> responseObserver) {
        List<String> result = state.getTupleSpacesState();
        debug("result: " + result);
        responseObserver.onNext(getTupleSpacesStateResponse.newBuilder().addAllTuple(result).build());
        responseObserver.onCompleted();
    }
}
