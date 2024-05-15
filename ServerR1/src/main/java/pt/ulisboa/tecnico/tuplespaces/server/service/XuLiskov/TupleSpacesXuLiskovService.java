package pt.ulisboa.tecnico.tuplespaces.server.service.XuLiskov;

import io.grpc.stub.StreamObserver;
import pt.ulisboa.tecnico.tuplespaces.replicaXuLiskov.contract.TupleSpacesReplicaGrpc;
import pt.ulisboa.tecnico.tuplespaces.replicaXuLiskov.contract.TupleSpacesReplicaXuLiskov.*;
import pt.ulisboa.tecnico.tuplespaces.server.domain.XuLiskov.ServerStateXuLiskov;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.ConcurrentHashMap;
import java.util.List;

import static io.grpc.Status.*;

public class TupleSpacesXuLiskovService extends TupleSpacesReplicaGrpc.TupleSpacesReplicaImplBase {
    private ServerStateXuLiskov state;

    private final boolean debugFlag;
    public TupleSpacesXuLiskovService(boolean debugFlag) {
        this.state = new ServerStateXuLiskov(debugFlag);
        this.debugFlag = debugFlag;
    }

    private void debug(String debugMessage) {
        if (this.debugFlag)
            System.err.println(debugMessage);
    }

    @Override
    public void put(PutRequest request, StreamObserver<PutResponse> responseObserver) {
        if (!state.put(request.getNewTuple())) {
            responseObserver.onError(INVALID_ARGUMENT.withDescription("Invalid tuple").asRuntimeException());
        }
        else {
            responseObserver.onNext(PutResponse.getDefaultInstance());
            responseObserver.onCompleted();
        }
    }

    @Override
    public void read(ReadRequest request, StreamObserver<ReadResponse> responseObserver) {
        String result = state.read(request.getSearchPattern());

        if (result == null) {
            responseObserver.onError(INVALID_ARGUMENT.withDescription("Invalid pattern").asRuntimeException());
        }
        else {
            responseObserver.onNext(ReadResponse.newBuilder().setResult(result).build());
            responseObserver.onCompleted();
        }
    }

    @Override
    public void getTupleSpacesState(getTupleSpacesStateRequest request, StreamObserver<getTupleSpacesStateResponse> responseObserver) {
        List<String> tuple;

        tuple = state.getTupleSpacesState();

        responseObserver.onNext(getTupleSpacesStateResponse.newBuilder().addAllTuple(tuple).build());
        responseObserver.onCompleted();
    }

    @Override  
    public void takePhase1(TakePhase1Request request, StreamObserver<TakePhase1Response> responseObserver) {
        debug("TakePhase1 request");

        if (!state.tupleIsValid(request.getSearchPattern())) {
            responseObserver.onError(INVALID_ARGUMENT.withDescription("Invalid argument").asRuntimeException());
            debug("TakePhase1 INVALID_ARGUMENT");
            return;
        }
        List<String> matchingTuples = state.tryLockMatchingTuples(request.getSearchPattern(), request.getClientId());

        if (matchingTuples == null) {
            // Another client already locked the tuples => Refuse client's request
            responseObserver.onError(CANCELLED.withDescription("Failed to lock tuples").asRuntimeException());
            debug("TakePhase1 CANCELLED");
        }
        else {
            // Client got the lock or there is no found tuples
            responseObserver.onNext(TakePhase1Response.newBuilder().addAllReservedTuples(matchingTuples).build());
            debug("TakePhase1 response");
            responseObserver.onCompleted();
        }
    }

    @Override
    public void takePhase1Release(TakePhase1ReleaseRequest request, StreamObserver<TakePhase1ReleaseResponse> responseObserver) {
        debug("TakePhase1Release request");
        state.unlockClientTuples(request.getClientId());
        responseObserver.onNext(TakePhase1ReleaseResponse.getDefaultInstance());
        responseObserver.onCompleted();
        debug("TakePhase1Release response");
    }

    @Override
    public void takePhase2(TakePhase2Request request, StreamObserver<TakePhase2Response> responseObserver) {
        debug("TakePhase2 request");
        state.take(request.getTuple(),request.getClientId());
        state.unlockClientTuples(request.getClientId());

        responseObserver.onNext(TakePhase2Response.getDefaultInstance());
        responseObserver.onCompleted();
        debug("TakePhase2 response");
    }
}
