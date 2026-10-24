package pt.tecnico.blockchainist.node;

import java.security.PublicKey;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import pt.tecnico.blockchainist.block.Bc;
import pt.tecnico.blockchainist.contract.Block;
import pt.tecnico.blockchainist.contract.BroadcastRequest;
import pt.tecnico.blockchainist.contract.BroadcastResponse;
import pt.tecnico.blockchainist.contract.CreateWalletRequest;
import pt.tecnico.blockchainist.contract.CreateWalletResponse;
import pt.tecnico.blockchainist.contract.DeleteWalletRequest;
import pt.tecnico.blockchainist.contract.DeleteWalletResponse;
import pt.tecnico.blockchainist.contract.DeliverBlockRequest;
import pt.tecnico.blockchainist.contract.DeliverBlockResponse;
import pt.tecnico.blockchainist.contract.GetBlockchainStateRequest;
import pt.tecnico.blockchainist.contract.GetBlockchainStateResponse;
import pt.tecnico.blockchainist.contract.NodeServiceGrpc;
import pt.tecnico.blockchainist.contract.ReadBalanceRequest;
import pt.tecnico.blockchainist.contract.ReadBalanceResponse;
import pt.tecnico.blockchainist.contract.SequencerServiceGrpc;
import pt.tecnico.blockchainist.contract.SyncBlockchainRequest;
import pt.tecnico.blockchainist.contract.SyncBlockchainResponse;
import pt.tecnico.blockchainist.contract.Transaction;
import pt.tecnico.blockchainist.contract.TransferRequest;
import pt.tecnico.blockchainist.contract.TransferResponse;
import pt.tecnico.blockchainist.cripto.Cripto;
import pt.tecnico.blockchainist.node.domain.NodeState;
import pt.tecnico.blockchainist.transaction.Tx;

public class NodeServiceImpl extends NodeServiceGrpc.NodeServiceImplBase {

    /**
     * Set flag to true to print debug messages. The flag can be set using the
     * -Ddebug command line option.
     */
    private static final boolean DEBUG_FLAG = (System.getProperty("debug") != null);

    /**
     * Helper method to print debug messages.
     */
    private static void debug(String message) {
        if (DEBUG_FLAG) {
            System.err.println("[DEBUG] NodeServiceImpl: " + message);
        }
    }

    private final NodeState state;
    private final SequencerServiceGrpc.SequencerServiceBlockingStub sequencerStub;
    private final Map<String, CompletableFuture<Void>> pending = new ConcurrentHashMap<>();
    private int lastBlock;
    private final String nodeOrg;
    private static final Map<String, String> CLIENT_ORG = Map.of(
            "BC", "OrgA",
            "Alice", "OrgA",
            "Bob", "OrgA",
            "Charlie", "OrgA",
            "David", "OrgB",
            "Emma", "OrgB",
            "Fred", "OrgB",
            "Ginger", "OrgC",
            "Henry", "OrgC",
            "Iris", "OrgC"
    );
    private static final Map<String, PublicKey> PUBLIC_KEYS = new HashMap<>();

    public NodeServiceImpl(NodeState state, SequencerServiceGrpc.SequencerServiceBlockingStub sequencerStub, String nodeOrg) {
        this.state = state;
        this.sequencerStub = sequencerStub;
        this.nodeOrg = nodeOrg;
        loadPublicKeys();
        debug("NodeServiceImpl initialized with NodeState and Sequencer stub");
    }

    @Override
    public void createWallet(CreateWalletRequest request, StreamObserver<CreateWalletResponse> responseObserver) {

        CreateWalletRequest unsignedRequest = request.toBuilder().clearSignature().build();

        try {
            if (!verifyTransactionSignature(request.getUserId(), request.getSignature().toByteArray(), unsignedRequest.toByteArray())) {
                responseObserver.onError(
                        Status.INVALID_ARGUMENT
                                .withDescription("Invalid signature")
                                .asRuntimeException()
                );
                return;
            }

        } catch (Exception e) {
            debug("Exception: " + e.getMessage());
            responseObserver.onError(Status.INTERNAL.withDescription("Internal error: " + e.getMessage()).asRuntimeException());
            return;
        }
        debug("Create request with valid signature");

        validateClient(request.getUserId());

        Integer delay = NodeDelayInterceptor.DELAY_KEY.get();
        try {
            Thread.sleep(delay * 1000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            debug("Sleep was interrupted");
        }

        debug("Received createWallet request for userId:" + request.getUserId() + " walletId: " + request.getWalletId());

        Transaction tx = Transaction.newBuilder().setCreateWallet(request).build();

        try {
            confirmTransaction(tx);
            debug("Transaction confirmed with sequencer: " + tx);
            debug("Transaction applied: " + tx);
            CreateWalletResponse response = CreateWalletResponse.newBuilder().build();
            // Send a single response through the stream.
            responseObserver.onNext(response);
            // Notify the client that the operation has been completed.
            responseObserver.onCompleted();
            debug("Sent createWallet response to client");

        } catch (CompletionException e) {
            handleTransactionFailure(responseObserver, e.getCause());
        } catch (StatusRuntimeException e) {
            debug("StatusRuntimeException: " + e.getMessage());
            responseObserver.onError(e);
        } catch (Exception e) {
            debug("Exception: " + e.getMessage());
            responseObserver.onError(Status.INTERNAL.withDescription("Internal error: " + e.getMessage()).asRuntimeException());
        }
    }

    @Override
    public void deleteWallet(DeleteWalletRequest request, StreamObserver<DeleteWalletResponse> responseObserver) {

        DeleteWalletRequest unsignedRequest = request.toBuilder().clearSignature().build();

        try {
            if (!verifyTransactionSignature(request.getUserId(), request.getSignature().toByteArray(), unsignedRequest.toByteArray())) {
                responseObserver.onError(
                        Status.INVALID_ARGUMENT
                                .withDescription("Invalid signature")
                                .asRuntimeException()
                );
                return;
            }
        } catch (Exception e) {
            debug("Exception: " + e.getMessage());
            responseObserver.onError(Status.INTERNAL.withDescription("Internal error: " + e.getMessage()).asRuntimeException());
            return;
        }
        debug("Delete request with valid signature");

        validateClient(request.getUserId());

        String srcOrg = CLIENT_ORG.get(request.getUserId());

        Integer delay = NodeDelayInterceptor.DELAY_KEY.get();
        try {
            Thread.sleep(delay * 1000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            debug("Sleep was interrupted");
        }

        debug("Received deleteWallet request for userId:" + request.getUserId() + " walletId: " + request.getWalletId());

        Transaction tx = Transaction.newBuilder().setDeleteWallet(request).build();

        try {
            // Activate deletion flag
            state.futureDeleteWallet(request.getWalletId());

            confirmTransaction(tx);
            debug("Transaction confirmed with sequencer: " + tx);
            debug("Transaction applied: " + tx);
            DeleteWalletResponse response = DeleteWalletResponse.newBuilder().build();
            // Send a single response through the stream.
            responseObserver.onNext(response);
            // Notify the client that the operation has been completed.
            responseObserver.onCompleted();
            debug("Sent deleteWallet response to client");
        } catch (CompletionException e) {
            handleTransactionFailure(responseObserver, e.getCause());
        } catch (StatusRuntimeException e) {
            debug("StatusRuntimeException: " + e.getMessage());
            responseObserver.onError(e);
        } catch (Exception e) {
            debug("Exception: " + e.getMessage());
            responseObserver.onError(Status.INTERNAL.withDescription("Internal error: " + e.getMessage()).asRuntimeException());
        }

    }

    @Override
    public void transfer(TransferRequest request, StreamObserver<TransferResponse> responseObserver) {

        TransferRequest unsignedRequest = request.toBuilder().clearSignature().build();

        try {
            if (!verifyTransactionSignature(request.getSrcUserId(), request.getSignature().toByteArray(), unsignedRequest.toByteArray())) {
                responseObserver.onError(
                        Status.INVALID_ARGUMENT
                                .withDescription("Invalid signature")
                                .asRuntimeException()
                );
                return;
            }
        } catch (Exception e) {
            debug("Exception: " + e.getMessage());
            responseObserver.onError(Status.INTERNAL.withDescription("Internal error: " + e.getMessage()).asRuntimeException());
            return;
        }
        debug("Transfer request with valid signature");

        validateClient(request.getSrcUserId());

        String srcOrg = CLIENT_ORG.get(request.getSrcUserId());
        String dstUserId;

        try {
            dstUserId = state.getWalletOwner(request.getDstWalletId());
        } catch (IllegalArgumentException e) {
            debug("Get wallet owner failed: " + e.getMessage());
            responseObserver.onError(Status.INVALID_ARGUMENT
                    .withDescription(e.getMessage())
                    .asRuntimeException());
            return;
        }

        String dstOrg = CLIENT_ORG.get(dstUserId);

        Integer delay = NodeDelayInterceptor.DELAY_KEY.get();
        try {
            Thread.sleep(delay * 1000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            debug("Sleep was interrupted");
        }

        debug("Received transfer request from srcUserId:" + request.getSrcUserId() + " srcWalletId:" + request.getSrcWalletId());

        Transaction tx = Transaction.newBuilder().setTransfer(request).build();

        try {
            // Same org, ordem causal
            if (srcOrg.equals(dstOrg)) {
                confirmTransfer(tx);
            } else {
                confirmTransaction(tx);
            }

            debug("Transaction confirmed with sequencer: " + tx);
            debug("Transaction applied: " + tx);
            TransferResponse response = TransferResponse.newBuilder().build();
            // Send a single response through the stream.
            responseObserver.onNext(response);
            // Notify the client that the operation has been completed.
            responseObserver.onCompleted();
            debug("Sent transfer response to client");
        } catch (CompletionException e) {
            handleTransactionFailure(responseObserver, e.getCause());
        } catch (StatusRuntimeException e) {
            debug("StatusRuntimeException: " + e.getMessage());
            responseObserver.onError(e);
        } catch (Exception e) {
            debug("Exception: " + e.getMessage());
            responseObserver.onError(Status.INTERNAL.withDescription("Internal error: " + e.getMessage()).asRuntimeException());
        }
    }

    @Override
    public void readBalance(ReadBalanceRequest request, StreamObserver<ReadBalanceResponse> responseObserver) {

        Integer delay = NodeDelayInterceptor.DELAY_KEY.get();
        try {
            Thread.sleep(delay * 1000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            debug("Sleep was interrupted");
        }

        debug("Received readBalance request for walletId: " + request.getWalletId());

        try {
            long balance = state.readBalance(request.getWalletId());
            debug("Balance read: " + balance);
            ReadBalanceResponse response = ReadBalanceResponse.newBuilder().setBalance(balance).build();
            // Send a single response through the stream.
            responseObserver.onNext(response);
            // Notify the client that the operation has been completed.
            responseObserver.onCompleted();
            debug("Sent readBalance response to client");
        } catch (StatusRuntimeException e) {
            debug("StatusRuntimeException: " + e.getMessage());
            responseObserver.onError(e);
        } catch (IllegalArgumentException e) {
            debug("IllegalArgumentException: " + e.getMessage());
            responseObserver.onError(Status.INVALID_ARGUMENT
                    .withDescription(e.getMessage())
                    .asRuntimeException());
        } catch (Exception e) {
            debug("Exception: " + e.getMessage());
            responseObserver.onError(Status.INTERNAL.withDescription("Internal error: " + e.getMessage()).asRuntimeException());
        }
    }

    @Override
    public void getBlockchainState(GetBlockchainStateRequest request, StreamObserver<GetBlockchainStateResponse> responseObserver) {
        debug("Received getBlockchainState request");

        try {
            List<Transaction> ledger = state.getBlockchainState();
            GetBlockchainStateResponse response = GetBlockchainStateResponse.newBuilder().addAllTransactions(ledger).build();
            // Send a single response through the stream.
            responseObserver.onNext(response);
            // Notify the client that the operation has been completed.
            responseObserver.onCompleted();
            debug("Sent getBlockchainState response to client");
        } catch (StatusRuntimeException e) {
            debug("StatusRuntimeException: " + e.getMessage());
            responseObserver.onError(e);
        } catch (IllegalArgumentException e) {
            debug("IllegalArgumentException: " + e.getMessage());
            responseObserver.onError(Status.INVALID_ARGUMENT
                    .withDescription(e.getMessage())
                    .asRuntimeException());
        } catch (Exception e) {
            debug("Exception: " + e.getMessage());
            responseObserver.onError(Status.INTERNAL.withDescription("Internal error: " + e.getMessage()).asRuntimeException());
        }
    }

    // Function that informs the transaction to the sequencer, and sends the transaction back to confirm if the response is equal to the transaction number.
    private void confirmTransaction(Transaction tx) throws StatusRuntimeException {
        debug("Sending transaction to sequencer: " + tx);

        BroadcastRequest broadcastRequest = BroadcastRequest.newBuilder().setTransaction(tx).build();
        String id = getTransactionId(broadcastRequest.getTransaction());
        // Send broadcast to sequencer
        BroadcastResponse broadcastResponse;
        try {
            CompletableFuture<Void> future = new CompletableFuture<>();
            pending.put(id, future);
            broadcastResponse = sequencerStub.broadcast(broadcastRequest);
            if (broadcastResponse.getNotApplied()) {
                future.join();
            } else {
                pending.remove(id);
            }
        } catch (StatusRuntimeException e) {
            System.err.println("broadcast RPC failed: " + e.getStatus().getDescription() + " - " + e.getMessage());
            throw e;
        } catch (Exception e) {
            System.err.println("Unexpected Error during broadcast request: " + e.getMessage());
            throw e;
        }

    }

    private void confirmTransfer(Transaction tx) {
        if (state.checkWalletDeletion(tx.getTransfer().getSrcWalletId())) {
            throw new IllegalArgumentException("Source Wallet will be deleted");
        }
        if (state.checkWalletDeletion(tx.getTransfer().getDstWalletId())) {
            throw new IllegalArgumentException("Destination Wallet will be deleted");
        }

        BroadcastRequest broadcastRequest = BroadcastRequest.newBuilder()
                .setTransaction(tx)
                .build();
        try {
            sequencerStub.broadcast(broadcastRequest);
        } catch (StatusRuntimeException e) {
            System.err.println("broadcast RPC failed: " + e.getStatus().getDescription() + " - " + e.getMessage());
            throw e;
        } catch (Exception e) {
            System.err.println("Unexpected Error during broadcast request: " + e.getMessage());
            throw e;
        }

    }

    private void validateClient(String id) {
        String clientOrg = CLIENT_ORG.get(id);

        if (clientOrg == null) {
            throw Status.INVALID_ARGUMENT
                    .withDescription("Unknown user: " + id)
                    .asRuntimeException();
        }
        if (!clientOrg.equals(nodeOrg)) {
            throw Status.INVALID_ARGUMENT
                    .withDescription("User " + id + " does not belong to organization " + nodeOrg)
                    .asRuntimeException();
        }
    }

    public void startBlockListener() {
        new Thread(() -> {
            while (true) {
                int nextBlock = lastBlock + 1;
                try {
                    DeliverBlockRequest request
                            = DeliverBlockRequest.newBuilder()
                                    .setBlockNumber(nextBlock)
                                    .build();
                    debug("Asked for block: " + nextBlock);
                    DeliverBlockResponse response
                            = sequencerStub.deliverBlock(request);
                    if (response.hasBlock()) {
                        Block block = response.getBlock();
                        debug("Received block " + block.getBlockNumber());
                        if (!verifyBlockSignature(block)) {
                            debug("Invalid block signature on block: " + block.getBlockNumber());
                            lastBlock++;
                            continue;
                        }
                        state.addBlock(Bc.fromProto(block));
                        debug("Added block to NodeState");
                        for (Transaction transaction : block.getTransactionsList()) {
                            String id = getTransactionId(transaction);
                            CompletableFuture<Void> future = pending.remove(id);
                            try {
                                if (!verifyTransactionFromBlock(transaction)) {
                                    continue;
                                }
                                state.applyTransaction(new Tx(transaction));

                                if (future != null) {
                                    future.complete(null);
                                }
                            } catch (Exception e) {
                                if (future != null) {
                                    future.completeExceptionally(e);
                                }
                            }
                            debug("Applied transaction id: " + id + " from block " + block.getBlockNumber());
                        }
                        lastBlock++;
                    }
                } catch (Exception e) {
                    debug("Error receiving block: " + e.getMessage());
                }
            }
        }).start();
    }

    public void syncBlockchain() {
        try {
            debug("Requesting full blockchain sync");

            SyncBlockchainRequest request = SyncBlockchainRequest.newBuilder().build();
            SyncBlockchainResponse response = sequencerStub.syncBlockchain(request);

            List<Block> blocks = response.getBlocksList();

            if (blocks.isEmpty()) {
                debug("No blocks to sync. Blockchain is empty.");
                return;
            }

            for (Block block : blocks) {
                debug("Syncing block " + block.getBlockNumber());

                if (!verifyBlockSignature(block)) {
                    debug("Invalid block signature on block: " + block.getBlockNumber());
                }

                Bc bcBlock = Bc.fromProto(block);
                state.addBlock(bcBlock);

                for (Transaction transaction : block.getTransactionsList()) {
                    try {
                        if (!verifyTransactionFromBlock(transaction)) {
                            continue;
                        }
                        state.applyTransaction(new Tx(transaction));
                        debug("Applied transaction id: " + getTransactionId(transaction)
                                + " from block " + block.getBlockNumber());
                    } catch (Exception e) {
                        debug("Failed to apply transaction id: "
                                + getTransactionId(transaction) + ": " + e.getMessage());
                    }
                }
                lastBlock = block.getBlockNumber();
            }

            debug("Blockchain sync completed. Total blocks: " + blocks.size());

        } catch (Exception e) {
            debug("Error during blockchain sync: " + e.getMessage());
        }
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

    private void handleTransactionFailure(StreamObserver<?> responseObserver, Throwable cause) {
        if (cause instanceof IllegalArgumentException) {
            responseObserver.onError(Status.INVALID_ARGUMENT
                    .withDescription(cause.getMessage())
                    .asRuntimeException());
        } else if (cause instanceof StatusRuntimeException) {
            responseObserver.onError((StatusRuntimeException) cause);
        } else {
            responseObserver.onError(Status.INTERNAL
                    .withDescription(cause.getMessage())
                    .asRuntimeException());
        }
    }

    public void loadPublicKeys() {
        for (String userId : CLIENT_ORG.keySet()) {
            debug("Loading key: " + userId.toLowerCase() + "_public.der");
            try {
                PublicKey key = Cripto.loadPublicKey(userId.toLowerCase() + "_public.der");
                PUBLIC_KEYS.put(userId, key);
            } catch (Exception e) {
                debug("Failed to load public key for user: " + userId + " : " + e.getMessage());
                System.err.println("Failed to load public key for user: " + userId);
                throw new RuntimeException(e);
            }
        }
        String sequencerFile = "sequencer_public.der";
        debug("Loading sequencer key: " + sequencerFile);
        try {
            PublicKey key = Cripto.loadPublicKey(sequencerFile);
            PUBLIC_KEYS.put("sequencer", key);
        } catch (Exception e) {
            debug("Failed to load sequencer public key:" + e.getMessage());
            System.err.println("Failed to load sequencer public key");
            throw new RuntimeException(e);
        }
    }

    private boolean verifyTransactionSignature(String userId, byte[] signature, byte[] data) {
        debug("Verifying Transaction Request signature");
        try {
            PublicKey key = PUBLIC_KEYS.get(userId);

            if (key == null) {
                System.err.println("Unknown user: " + userId);
                return false;
            }

            if (Cripto.verify(data, signature, key)) {
                debug("Valid Transaction Request signature");
                return true;
            }
            debug("Invalid Transaction Request signature");
            debug("user: " + userId);
            debug("key: " + key);
            return false;

        } catch (Exception e) {
            debug("Signature Verification error: " + e.getMessage());
            System.err.println("Signature Verification error: " + e.getMessage());
            return false;
        }
    }

    private boolean verifyBlockSignature(Block block) {
        debug("Verifying Block signature: " + block.getBlockNumber());
        try {
            PublicKey key = PUBLIC_KEYS.get("sequencer");

            if (key == null) {
                System.err.println("Failed to get Sequencer public key");
                return false;
            }

            Block unsignedBlock = block.toBuilder().clearSignature().build();

            byte[] data = unsignedBlock.toByteArray();
            byte[] signature = block.getSignature().toByteArray();

            if (Cripto.verify(data, signature, key)) {
                debug("Valid Block signature");
                return true;
            }
            debug("Invalid Block signature");
            return false;

        } catch (Exception e) {
            debug("Block signature verification error: " + e.getMessage());
            System.err.println("Block signature verification error: " + e.getMessage());
            return false;
        }
    }

    private boolean verifyTransactionFromBlock(Transaction transaction) {
        byte[] signature = null;
        byte[] unsignedBytes = null;
        String userId = null;
        switch (transaction.getOperationCase()) {
            case CREATE_WALLET: {
                CreateWalletRequest req = transaction.getCreateWallet();
                userId = req.getUserId();
                CreateWalletRequest unsigned = req.toBuilder().clearSignature().build();
                signature = req.getSignature().toByteArray();
                unsignedBytes = unsigned.toByteArray();
                break;
            }
            case DELETE_WALLET: {
                DeleteWalletRequest req = transaction.getDeleteWallet();
                userId = req.getUserId();
                DeleteWalletRequest unsigned = req.toBuilder().clearSignature().build();
                signature = req.getSignature().toByteArray();
                unsignedBytes = unsigned.toByteArray();
                break;
            }
            case TRANSFER: {
                TransferRequest req = transaction.getTransfer();
                userId = req.getSrcUserId();
                TransferRequest unsigned = req.toBuilder().clearSignature().build();
                signature = req.getSignature().toByteArray();
                unsignedBytes = unsigned.toByteArray();
                break;
            }
            default:
                debug("Unknown transaction type");
                return false;
        }
        if (signature == null || unsignedBytes == null || userId == null) {
            debug("Signature validation failed for transaction: " + getTransactionId(transaction));
            return false;
        }
        try {
            if (!verifyTransactionSignature(userId, signature, unsignedBytes)) {
                debug("Invalid signature for transaction: " + getTransactionId(transaction));
                return false;
            }
        } catch (Exception e) {
            debug("Exception: " + e.getMessage());
            return false;
        }
        debug("Transaction request with valid signature");
        return true;
    }
}
