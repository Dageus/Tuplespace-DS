package pt.ulisboa.tecnico.tuplespaces.server;

import io.grpc.*;

import pt.ulisboa.tecnico.tuplespaces.server.service.TotalOrderService.TupleSpacesTotalOrderService;
import pt.ulisboa.tecnico.tuplespaces.server.service.XuLiskov.TupleSpacesXuLiskovService;
import pt.ulisboa.tecnico.tuplespaces.server.service.centralized.TupleSpacesCentralizedService;
import pt.ulisboa.tecnico.name.server.grpc.NameServerGrpc;
import pt.ulisboa.tecnico.name.server.grpc.NameServerOuterClass;

import java.io.IOException;

public class ServerMain {

  /** Set flag to true to print debug messages.
   * The flag can be set using the -Ddebug command line option. */
  private static final boolean DEBUG_FLAG = (System.getProperty("debug") != null);

  /** Helper method to print debug messages. */
  private static void debug(String debugMessage) {
    if (DEBUG_FLAG)
      System.err.println(debugMessage);
  }


  public static void main(String[] args) throws IOException, InterruptedException {

      debug(ServerMain.class.getSimpleName());

      // receive and print arguments
      if(DEBUG_FLAG) {
        System.out.printf("Received %d arguments%n", args.length);
        for (int i = 0; i < args.length; i++) {
          System.out.printf("arg[%d] = %s%n", i, args[i]);
        }
      }

      // check arguments
      if (args.length != 4) {
        System.err.println("Argument(s) missing!");
        System.err.printf("Usage: java %s <host> <port> <qualifier> <service>%n", ServerMain.class.getName());
        return;
      }

      final int port = Integer.parseInt(args[1]);
      final String host = args[0];
      final String qualifier = args[2];
      final String service = args[3];

      BindableService impl;

      switch(service) {
          case "TupleSpacesCentralized":
              impl = new TupleSpacesCentralizedService(DEBUG_FLAG);
              break;
          case "TupleSpacesXuLiskov":
              impl = new TupleSpacesXuLiskovService(DEBUG_FLAG);
              break;
          case "TupleSpacesTotalOrder":
              impl = new TupleSpacesTotalOrderService(DEBUG_FLAG);
              break;
          default:
              debug("Unkwown service");
              return;
      }


      // Create a new server to listen on port
      Server server = ServerBuilder.forPort(port).addService(impl).build();

      // Start the server
      server.start();

      // Connecting to NameSever
      String nameServerAddress = host + ":5001";
      debug("Connecting to " + nameServerAddress);

      ManagedChannel channel = ManagedChannelBuilder.forTarget(nameServerAddress).usePlaintext().build();
      NameServerGrpc.NameServerBlockingStub stub = NameServerGrpc.newBlockingStub(channel);

      String serverAddress = host + ":" + args[1];
      debug("Server address: " + serverAddress);

      // Delete server in NameServer when server closes
      Runtime.getRuntime().addShutdownHook(new Thread(() -> {
        NameServerOuterClass.DeleteRequest request = NameServerOuterClass.DeleteRequest.newBuilder().setServiceName(service).setAddress(serverAddress).build();
        try{
            stub.delete(request);
            debug("Deleted");
        } catch (StatusRuntimeException e) {
            debug("Name Server unavailable for deletion");
        }
        if (channel != null && !channel.isShutdown()) {
            debug("NameServer channel shutdown");
            channel.shutdown();
        }
      }));

      // Register server
      NameServerOuterClass.RegisterRequest request = NameServerOuterClass.RegisterRequest.newBuilder().setServiceName(service).setQualifier(qualifier).setAddress(serverAddress).build();
      try {
          stub.register(request);
      } catch (StatusRuntimeException e) {
          System.err.println("Name Server unavailable");
          System.exit(0);
      }
      debug("Listening: " + serverAddress);

      // Server threads are running in the background.
      debug("Server started");

      // Do not exit the main thread. Wait until server is terminated.
      server.awaitTermination();
    }
}

