package pt.tecnico.blockchainist.client;

import java.util.ArrayList;
import java.util.Arrays;

import pt.tecnico.blockchainist.client.grpc.ClientNodeService;

public class ClientMain {

    /** Set flag to true to print debug messages. 
     * The flag can be set using the -Ddebug command line option. */
    private static boolean DEBUG_FLAG = System.getProperty("debug") != null;

    private static void debug(String message) {
        if (DEBUG_FLAG) {
            System.err.println("[DEBUG] Client: " + message);
        }
    }

    public static void main(String[] args) {

        System.out.println(ClientMain.class.getSimpleName());

        //If the property did not activate debug
        if (!DEBUG_FLAG) { 
            for (String arg : args) {
                if ("-debug".equals(arg)) {
                    DEBUG_FLAG = true;
                    System.setProperty("debug", "true");
                    break;
                }
            }
        }

        // If the flag was activated via -debug, remove it from args 
        if (DEBUG_FLAG) {
            debug("Debug mode enabled");
            args = Arrays.stream(args)
                         .filter(a -> !"-debug".equals(a))
                         .toArray(String[]::new);
        }

        debug("Received " + args.length + " arguments");

        // Defines default timeout for client blocking requests
        final int DEFAULT_TIMEOUT = 8;

        // check arguments
        if (args.length < 1) {
            System.err.println("Argument(s) missing!");
            printUsage();
            return;
        }

        int timeout = DEFAULT_TIMEOUT;
        if (args.length > 0) {
            String lastArg = args[args.length - 1];
            try {
                timeout = Integer.parseInt(lastArg);
                args = Arrays.copyOf(args, args.length - 1);
                debug("Parsed optional timeout: " + timeout);
            } catch (NumberFormatException e) {
                debug("No timeout provided, using default: " + DEFAULT_TIMEOUT);
            }
        }

        // parse arguments
        ArrayList<ClientNodeService> nodes = new ArrayList<>(args.length);
        for (String arg : args) {
            String[] split = arg.split(":");
            if (split.length != 3) {
                System.err.println("Invalid argument: " + arg);
                printUsage();
                return;
            }
            String host = split[0];
            int port = -1;
            try {
                port = Integer.parseInt(split[1]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid port (" + split[1] + ") in argument: " + arg);
                printUsage();
                return;
            }
            if (port > 65535 || port < 0) {
                System.err.println("Port number out of range (0-65535): " + port);
                printUsage();
                return;
            }
            String organization = split[2];

            debug("Host:" + host + " Port:" + port + " Org:" + organization);

            nodes.add(new ClientNodeService(host, port, organization, timeout));

            debug("ClientNodeService created for " + host + ":" + port);
        }

        CommandProcessor processor = new CommandProcessor(nodes);
        processor.userInputLoop();
    }

    private static void printUsage() {
        System.err.println("Usage: mvn exec:java -Dexec.args=\"<host>:<port>:<organization> [<host>:<port>:<organization> ...]\"");
    }
}
