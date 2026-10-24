package pt.tecnico.blockchainist.sequencer;

import java.security.PrivateKey;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.google.protobuf.ByteString;

import io.grpc.stub.StreamObserver;
import pt.tecnico.blockchainist.contract.Block;
import pt.tecnico.blockchainist.contract.BroadcastRequest;
import pt.tecnico.blockchainist.contract.BroadcastResponse;
import pt.tecnico.blockchainist.contract.DeliverBlockRequest;
import pt.tecnico.blockchainist.contract.DeliverBlockResponse;
import pt.tecnico.blockchainist.contract.SequencerServiceGrpc;
import pt.tecnico.blockchainist.contract.SyncBlockchainRequest;
import pt.tecnico.blockchainist.contract.SyncBlockchainResponse;
import pt.tecnico.blockchainist.contract.Transaction;
import pt.tecnico.blockchainist.cripto.Cripto;

public class SequencerServiceImpl extends SequencerServiceGrpc.SequencerServiceImplBase {
    /** Set flag to true to print debug messages. 
     * The flag can be set using the -Ddebug command line option. */
    private static final boolean DEBUG_FLAG = (System.getProperty("debug") != null);

    /** Helper method to print debug messages. */
    private static void debug(String message) {
        if (DEBUG_FLAG)
            System.err.println("[DEBUG] SequencerServiceImpl: " + message);
    } 

    // Global list of transactions, sequence number
    private final List<Transaction> transactions = new ArrayList<>();

    // List of pending transactions waiting to be added to block
    private final List<Transaction> pendingTransactions = new ArrayList<>();

    // Closed blocks
    private final List<Block> blocks = new ArrayList<>();

    private final Object lock = new Object();


    private int nextSequenceNumber = 1;
    private int nextBlockNumber = 1;

    private final int blockSize;
    private final long blockTimeout;
    private long blockStartTime = 0L;

    private final ScheduledExecutorService scheduler;

    private PrivateKey sequencerPrivateKey;


    public SequencerServiceImpl(int blockSize, long blockTimeout) {
        this.blockSize = blockSize;
        this.blockTimeout = blockTimeout;

        this.blockStartTime = System.currentTimeMillis();

        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r);
            thread.setDaemon(true);
            return thread;
        });

        long periodMs = Math.max(50, blockTimeout / 10);
        scheduler.scheduleAtFixedRate(this::checkTimeout, periodMs, periodMs, TimeUnit.MILLISECONDS);

        try {
            this.sequencerPrivateKey = Cripto.loadPrivateKey("sequencer_private.der");
        } catch (Exception e) {
            debug("Failed to load sequencer private key:" + e.getMessage());
            System.err.println("Failed to load sequencer private key");
        }
        
    }

    private void checkTimeout() {
        synchronized (lock) {
            // if there arent any transactions it does not close the block
            if (pendingTransactions.isEmpty()) {
                return;
            }
            long now = System.currentTimeMillis();
            if ((now - blockStartTime) >= blockTimeout) {
                debug("Timer expired! Closing block with" + pendingTransactions.size() + "transactions");
                closeBlock(); 
            }
        }
    }

    private void closeBlock() {
        synchronized (lock) {
            if (pendingTransactions.isEmpty()) return;  
            
            int size = Math.min(blockSize,pendingTransactions.size());
            List <Transaction> blockTransactions = new ArrayList<>(size);

            for (int i = 0; i < size; i++) {
                blockTransactions.add(pendingTransactions.remove(0));
            }
            Block block = Block.newBuilder()
                    .setBlockNumber(nextBlockNumber++)
                    .addAllTransactions(blockTransactions)
                    .build();
            
            try {
                byte[] signature = Cripto.sign(block.toByteArray(), sequencerPrivateKey);

                block = block.toBuilder()
                        .setSignature(ByteString.copyFrom(signature))
                        .build();
            } catch (Exception e) {
                System.err.println("Error while building block request with private key: " + e.getMessage());
            }

            blocks.add(block);
            debug("Closed block number: " + block.getBlockNumber());
            blockStartTime = System.currentTimeMillis();
            lock.notifyAll();
        }      
    }

    /** Broadcast adds a transaction to the list and returns the sequence number */
    @Override
    public void broadcast(BroadcastRequest request, StreamObserver<BroadcastResponse> responseObserver) {
        
        Transaction transaction = request.getTransaction();
        String txId = getTransactionId(transaction);
        boolean notApplied;

        int sequence;

        synchronized(lock) {
            boolean inTransactions = transactions.stream()
                .anyMatch(t -> getTransactionId(t).equals(txId));

            boolean inPending = pendingTransactions.stream()
                .anyMatch(t -> getTransactionId(t).equals(txId));
            
            if (inPending) {
                notApplied = true;
            } else if (inTransactions) {
                notApplied = false;
            } else {
                sequence = nextSequenceNumber++;
                transactions.add(transaction);
                pendingTransactions.add(transaction);
                notApplied = true;
                if (pendingTransactions.size() >= blockSize) {
                    closeBlock();
                }
                debug("New transaction added with sequence: " + sequence);
            }

        }

        debug("Broadcast received transaction: " + txId);

        BroadcastResponse response = BroadcastResponse.newBuilder()
                    .setNotApplied(notApplied)
                    .build();

        // Send a single response through the stream.
		responseObserver.onNext(response);
        // Notify the client that the operation has been completed.
		responseObserver.onCompleted();
        debug("Broadcast response sent");
    }
    

    @Override
    public void deliverBlock(DeliverBlockRequest request, StreamObserver<DeliverBlockResponse> responseObserver) {
        int blockRequested = request.getBlockNumber();
        Block found = null;

        synchronized (lock) {
            while (found == null) {
                for (Block block : blocks) {
                    if (block.getBlockNumber() == blockRequested) {
                        found = block;
                        break;
                    }
                }

                if (found == null) {
                    try {
                        lock.wait();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        responseObserver.onError(e);
                        return;
                    }
                }
            }
            
        }

        DeliverBlockResponse response = DeliverBlockResponse.newBuilder()
                .setBlock(found)
                .build();
           
        // Send a single response through the stream.
		responseObserver.onNext(response);
        // Notify the client that the operation has been completed. 
		responseObserver.onCompleted();
        
    }

    @Override
    public void syncBlockchain(SyncBlockchainRequest request, StreamObserver<SyncBlockchainResponse> responseObserver) {

        SyncBlockchainResponse response;

        synchronized (lock) {
            response = SyncBlockchainResponse.newBuilder()
                    .addAllBlocks(blocks)
                    .build();
        }

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    public void shutdown() {
        debug("Shutting down scheduler");
        scheduler.shutdownNow();
    }

    private String getTransactionId(Transaction tx) {
        switch (tx.getOperationCase()) {
            case CREATE_WALLET:
                return tx.getCreateWallet().getTransactionId();
            case DELETE_WALLET:
                return tx.getDeleteWallet().getTransactionId();
            case TRANSFER:
                return tx.getTransfer().getTransactionId();
        }
        return null;
    }

    
}