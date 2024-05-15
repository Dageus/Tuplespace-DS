package pt.ulisboa.tecnico.tuplespaces.client;

import pt.ulisboa.tecnico.tuplespaces.client.grpc.ClientService;
import pt.ulisboa.tecnico.tuplespaces.client.grpc.TotalOrder.ClientTotalOrderService;
import pt.ulisboa.tecnico.tuplespaces.client.grpc.XuLiskov.ClientXuLiskovService;
import pt.ulisboa.tecnico.tuplespaces.client.grpc.centralized.ClientCentralizedService;

import java.util.List;

public class ClientMain {

     /** Set flag to true to print debug messages. 
     * The flag can be set using the -Ddebug command line option. */
    private static final boolean DEBUG_FLAG = (System.getProperty("debug") != null);

    /** Helper method to print debug messages. */
    private static void debug(String debugMessage) {
        if (DEBUG_FLAG)
            System.err.println(debugMessage);
    }

    public static void main(String[] args) {

        debug(ClientMain.class.getSimpleName());

        // Receive and print arguments
        if (DEBUG_FLAG) {
            System.out.printf("Received %d arguments%n", args.length);
            for (int i = 0; i < args.length; i++) {
                System.out.printf("arg[%d] = %s%n", i, args[i]);
            }
        }

        // Check arguments
        if (args.length != 3) {
            System.err.println("Argument(s) missing!");
            System.err.println("Usage: mvn exec:java -Dexec.args=<host> <port> <service>");
            return;
        }

        // Get the host and the port
        final String nsHost = args[0];
        final String nsPort = args[1];
        final String service = args[2];

        // Create the address
        String nsAddress = nsHost + ":" + nsPort;
        ClientService clientService;

        // Start the service according to the service input by the user
        switch (service) {
             case "TupleSpacesCentralized":
                 clientService = new ClientCentralizedService(nsAddress, service, DEBUG_FLAG);
                 break;
            case "TupleSpacesXuLiskov":
                clientService = new ClientXuLiskovService(3, nsAddress, service, DEBUG_FLAG);
                break;
            case "TupleSpacesTotalOrder":
                clientService = new ClientTotalOrderService(3, nsAddress, service, DEBUG_FLAG);
                break;
            default:
                System.err.println("Unknown service: " + service);
                System.exit(0);
                return;
        }

        // Connect to the servers
        if (clientService.getClientID() == -1 || clientService.connectToServers() == -1) {
            System.exit(0);
            return;
        }

        CommandProcessor parser = new CommandProcessor(clientService, DEBUG_FLAG);
        parser.parseInput();

        // Shut down the service
        clientService.shutDown();
    }
}
