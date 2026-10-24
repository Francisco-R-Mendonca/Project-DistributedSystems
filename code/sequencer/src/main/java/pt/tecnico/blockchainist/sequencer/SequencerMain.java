package pt.tecnico.blockchainist.sequencer;

import java.io.IOException;
import java.util.Arrays;

import io.grpc.BindableService;
import io.grpc.Server;
import io.grpc.ServerBuilder;


public class SequencerMain {
    /** Set flag to true to print debug messages. 
	 * The flag can be set using the -Ddebug command line option. */
	private static boolean DEBUG_FLAG = (System.getProperty("debug") != null);

    /** Helper method to print debug messages. */
	private static void debug(String message) {
		if (DEBUG_FLAG)
			System.err.println("[DEBUG] Sequencer: " + message);
	}

    public static void main(String[] args) throws IOException, InterruptedException {

        System.out.println(SequencerMain.class.getSimpleName());

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

        final int DEFAULT_BLOCK_SIZE = 4;
        final long DEFAULT_BLOCK_TIMEOUT = 5_000L;
        
        // check arguments
		if (args.length < 1) {
			System.err.println("Argument(s) missing!");
			return;
		}

        final int port;
        try {
            port = Integer.parseInt(args[0]);
			debug("Parsed port: " + port);
        } catch (NumberFormatException e) {
            System.err.println("Invalid port: " + args[0]);
			debug("Port parsing failed for input: " + args[0]);
            return;
        }

        int blockSize = DEFAULT_BLOCK_SIZE;
        long blockTimeout = DEFAULT_BLOCK_TIMEOUT;
        if (args.length > 1) {
            try {
                blockSize = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid block size: " + args[1]);
                debug("Block size failed for input: " + args[1]);
                return;
            }
        }
        if (args.length > 2) {
            try {
                long seconds = Long.parseLong(args[2]);
                blockTimeout = Math.max(1, seconds) * 1000L;
            } catch (NumberFormatException e) {
                System.err.println("Invalid block timeout: " + args[2]);
                debug("Block timeout failed for input: " + args[2]);
                return;
            }
        }


        final BindableService impl = new SequencerServiceImpl(blockSize, blockTimeout);
		debug("SequencerServiceImpl instance created");

        // Create a new server to listen on port
		Server server = ServerBuilder.forPort(port).addService(impl).build(); 
        // Start the server
		server.start();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (impl instanceof SequencerServiceImpl) {
                ((SequencerServiceImpl) impl).shutdown();
            }
            //server.shutdown(); ??
        }));

		// Server threads are running in the background.
		System.out.println("Sequencer started, port: " + port);
		debug("Server started and listening on port: " + port);

		// Do not exit the main thread. Wait until server is terminated.
		server.awaitTermination();
    }
    
}
