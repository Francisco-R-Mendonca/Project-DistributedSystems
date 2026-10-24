package pt.tecnico.blockchainist.node;

import java.io.IOException;
import java.util.Arrays;

import io.grpc.BindableService;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import pt.tecnico.blockchainist.contract.SequencerServiceGrpc;
import pt.tecnico.blockchainist.node.domain.NodeState;

/** 
 * <argument>${node.port}</argument> args[0]
        <argument>${node.org}</argument> args[1]
	        <argument>${sequencer.host}:${sequencer.port}</argument> args[2]
*/
public class NodeMain {

    /** Set flag to true to print debug messages. 
	 * The flag can be set using the -Ddebug command line option. */
	private static boolean DEBUG_FLAG = (System.getProperty("debug") != null);


    private static void debug(String message) {
        if (DEBUG_FLAG) {
            System.err.println("[DEBUG] Node: " + message);
        }
    }

    public static void main(String[] args) throws IOException, InterruptedException {

        System.out.println(NodeMain.class.getSimpleName());

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

        // check arguments
		if (args.length < 3) {
			System.err.println("Argument(s) missing!");
			//System.err.printf("Usage: java %s port%n", NodeMain.class.getName());
			return;
		}

        final int nodePort;
        final String nodeOrg; // node id/organização
        final String sequencerHost;
        final int sequencerPort; 

        try {
            nodePort = Integer.parseInt(args[0]); //nodePort
            debug("Parsed node port: " + nodePort);
        } catch (NumberFormatException e) {
            System.err.println("Invalid node port: " + args[0]);
            debug("Failed to parse node port: " + args[0]);
            return;
        }

        nodeOrg = args[1];
        debug("Node organization: " + nodeOrg);

        String[] sequencerSplit = args[2].split(":");
        if (sequencerSplit.length != 2) {
            System.err.println("Invalid argument (expected host:port): " + args[2]);
            debug("Failed to parse sequencer host:port from argument: " + args[2]);
            return;
        }

        sequencerHost = sequencerSplit[0];
        debug("Sequencer host: " + sequencerHost);

        try {
            sequencerPort = Integer.parseInt(sequencerSplit[1]);
            debug("Sequencer port: " + sequencerPort);
        } catch (NumberFormatException e) {
            System.err.println("Invalid sequencer port: " + sequencerSplit[1]);
            debug("Failed to parse sequencer port: " + sequencerSplit[1]);
            return;
        }

        // Local node state 
        NodeState state = new NodeState();
        debug("Initialized local NodeState");

        final String target = sequencerHost + ":" + sequencerPort;
        debug("Sequencer target: " + target);


        // ManagedChannel para comunicar com o sequencer
        ManagedChannel sequencerChannel = ManagedChannelBuilder.forTarget(target)
                    .usePlaintext()
                    .build();
        debug("Created ManagedChannel for sequencer");


        //grpc client
        SequencerServiceGrpc.SequencerServiceBlockingStub sequencerStub = SequencerServiceGrpc.newBlockingStub(sequencerChannel);
        debug("Created SequencerService blocking stub");

        // it needs to comunicate with the sequencer
        final NodeServiceImpl impl = new NodeServiceImpl(state, sequencerStub, nodeOrg);

        // Needed to interpret header and metadata
        final NodeDelayInterceptor interceptor = new NodeDelayInterceptor();

        impl.syncBlockchain();

        // Create a new server to listen on port
		Server server = ServerBuilder.forPort(nodePort).addService(impl).intercept(interceptor).build();
        debug("Server built for port: " + nodePort);

        // Start the server
		server.start();

		// Server threads are running in the background.
		System.out.println("Node started, port: " + nodePort);

        impl.startBlockListener();

		// Do not exit the main thread. Wait until server is terminated.
		server.awaitTermination();
        debug("Server terminated");


    }
}
