package pt.ulisboa.tecnico.tuplespaces.server.service.centralized;

import io.grpc.stub.StreamObserver;
import pt.ulisboa.tecnico.tuplespaces.centralized.contract.TupleSpacesCentralized.*;
import pt.ulisboa.tecnico.tuplespaces.centralized.contract.TupleSpacesGrpc;
import pt.ulisboa.tecnico.tuplespaces.server.domain.centralized.ServerState;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.List;

import static io.grpc.Status.INVALID_ARGUMENT;

public class TupleSpacesCentralizedService extends TupleSpacesGrpc.TupleSpacesImplBase {

    private ServerState state;
    private ReentrantLock lock = new ReentrantLock();
    private Condition cond = lock.newCondition();

    public TupleSpacesCentralizedService(boolean debugFlag) {
        this.state = new ServerState(debugFlag);
    }


    @Override
    public void put(PutRequest request, StreamObserver<PutResponse> responseObserver) {
        String tuple = request.getNewTuple();

        if (!state.tupleIsValid(tuple)) {
            state.debug("put: Invalid tuple: " + tuple);
            responseObserver.onError(INVALID_ARGUMENT.withDescription("Invalid tuple").asRuntimeException());
        }
        else {
            lock.lock();
            try {
                state.put(tuple);
                state.debug("Added tuple: " + tuple);
                cond.signalAll();
            } finally {
                lock.unlock();
            }
            responseObserver.onNext(PutResponse.getDefaultInstance());
            responseObserver.onCompleted();
        }
    }

    @Override
    public void read(ReadRequest request, StreamObserver<ReadResponse> responseObserver) {
        String pattern = request.getSearchPattern();
        String result = null;

        if (!state.tupleIsValid(pattern)) {
            state.debug("read: Invalid pattern: " + pattern);
            responseObserver.onError(INVALID_ARGUMENT.withDescription("Invalid pattern").asRuntimeException());
        } else {
            lock.lock();
            try {
                while ((result = state.read(pattern)) == null) {
                    cond.await();
                }
                state.debug("Read tuple: " + result);
            } catch (InterruptedException e) {
                e.printStackTrace();
                responseObserver.onError(INVALID_ARGUMENT.withDescription("Server unavailable").asRuntimeException());
            } finally {
                lock.unlock();
            }
            responseObserver.onNext(ReadResponse.newBuilder().setResult(result).build());
            responseObserver.onCompleted();
        }
    }

    @Override
    public void take(TakeRequest request, StreamObserver<TakeResponse> responseObserver) {
        String pattern = request.getSearchPattern();
        String result = null;

        if (!state.tupleIsValid(pattern)) {
            state.debug("take: Invalid pattern: " + pattern);
            responseObserver.onError(INVALID_ARGUMENT.withDescription("Invalid pattern").asRuntimeException());
        } else {
            lock.lock();
            try {
                while ((result = state.take(pattern)) == null) {
                    cond.await();
                }
                state.debug("Took tuple: " + result);
            } catch (InterruptedException e) {
                e.printStackTrace();
                responseObserver.onError(INVALID_ARGUMENT.withDescription("Server unavailable").asRuntimeException());
            } finally {
                lock.unlock();
            }
            responseObserver.onNext(TakeResponse.newBuilder().setResult(result).build());
            responseObserver.onCompleted();
        }
    }

    @Override
    public void getTupleSpacesState(getTupleSpacesStateRequest request, StreamObserver<getTupleSpacesStateResponse> responseObserver) {
        List<String> tuple;

        lock.lock();
        try {
            tuple = state.getTupleSpacesState();
        } finally {
            lock.unlock();
        }
        responseObserver.onNext(getTupleSpacesStateResponse.newBuilder().addAllTuple(tuple).build());
        responseObserver.onCompleted();
    }
}
