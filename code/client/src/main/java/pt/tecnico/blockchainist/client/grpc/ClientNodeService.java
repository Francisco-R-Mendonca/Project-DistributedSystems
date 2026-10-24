package pt.tecnico.blockchainist.client.grpc;

import pt.tecnico.blockchainist.contract.NodeServiceGrpc;
import pt.tecnico.blockchainist.contract.NodeServiceGrpc.NodeServiceBlockingStub;
import pt.tecnico.blockchainist.contract.NodeServiceGrpc.NodeServiceStub;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import pt.tecnico.blockchainist.contract.CreateWalletRequest;
import pt.tecnico.blockchainist.contract.CreateWalletResponse;
import pt.tecnico.blockchainist.contract.DeleteWalletRequest;
import pt.tecnico.blockchainist.contract.DeleteWalletResponse;
import pt.tecnico.blockchainist.contract.ReadBalanceRequest;
import pt.tecnico.blockchainist.contract.ReadBalanceResponse;
import pt.tecnico.blockchainist.contract.TransferRequest;
import pt.tecnico.blockchainist.contract.TransferResponse;
import pt.tecnico.blockchainist.cripto.Cripto;
import pt.tecnico.blockchainist.contract.GetBlockchainStateRequest;
import pt.tecnico.blockchainist.contract.GetBlockchainStateResponse;

import java.security.PrivateKey;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

import io.grpc.stub.MetadataUtils;
import io.grpc.Status;

public class ClientNodeService {

    /** Set flag to true to print debug messages. 
     * The flag can be set using the -Ddebug command line option. */
    private static final boolean DEBUG_FLAG = (System.getProperty("debug") != null);

    /** Helper method to print debug messages. */
    private static void debug(String message) {
        if (DEBUG_FLAG)
            System.err.println("[DEBUG] ClientNodeService: " + message);
    }

    private final ManagedChannel channel;
    private final NodeServiceGrpc.NodeServiceBlockingStub blockingStub;
    private final NodeServiceGrpc.NodeServiceStub asyncStub;
    private final String organization;
    private final Integer defaultDeadline;

    public ClientNodeService(String host, int port, String organization, Integer defaultDeadline) {

        this.organization = organization;

        final String target = host + ":" + port;
		debug("Connecting to node at "  + target + "(organization: " + organization + ")");

        // Plaintext communication, no certificates.
		this.channel = ManagedChannelBuilder.forTarget(target).usePlaintext().build();

        // Create stubs
		this.blockingStub = NodeServiceGrpc.newBlockingStub(channel);

        this.asyncStub = NodeServiceGrpc.newStub(channel);

        // Define default deadline time
        this.defaultDeadline = defaultDeadline;
    }
    

    public CreateWalletResponse createWalletBlocking(String userId, String walletId, String transaction_Id, Integer nodeDelay, Integer startIndex, Integer nodeIndex, ArrayList<ClientNodeService> nodes, Integer deadline) {

        debug("Sending createWallet(blocking) request with userId: " + userId + " walletId: " + walletId);

        Integer finalDeadline = defaultDeadline;
        if (deadline != null){
            finalDeadline = deadline;
        }

        Metadata metadata = new Metadata();
        metadata.put(Metadata.Key.of("delaySeconds", Metadata.ASCII_STRING_MARSHALLER), Integer.toString(nodeDelay));

        NodeServiceGrpc.NodeServiceBlockingStub stubWithMeta = blockingStub.withInterceptors(MetadataUtils.newAttachHeadersInterceptor(metadata)).withDeadlineAfter(finalDeadline, TimeUnit.SECONDS);

        try {
            debug("Loading private key and building request");
            PrivateKey privateKey = Cripto.loadPrivateKey(userId.toLowerCase() + "_private.der");

            CreateWalletRequest request = CreateWalletRequest.newBuilder()
                    .setUserId(userId)
                    .setWalletId(walletId)
                    .setTransactionId(transaction_Id)
                    .build();

            byte[] signature = Cripto.sign(request.toByteArray(), privateKey);

            request = request.toBuilder()
                    .setSignature(com.google.protobuf.ByteString.copyFrom(signature))
                    .build();

            CreateWalletResponse response = stubWithMeta.createWallet(request);
            debug("Created Wallet(blocking) with userId: " + userId + " walletId: " + walletId);
            return response;

        } catch (StatusRuntimeException e) {
            if (e.getStatus().getCode() == Status.Code.INVALID_ARGUMENT) {
                System.err.println(e.getStatus().getDescription());
                return null;
            }
            System.err.println("createWallet(blocking) RPC failed: " + e.getStatus().getDescription() + " - " + e.getMessage());
            debug("createWallet(blocking) FAILED");
        } catch (Exception e) {
            System.err.println("Unexpected Error during the create wallet(blocking) request: " + e.getMessage());
            debug("Unexpected exception in createWallet(blocking)");
        }
        int next = (nodeIndex + 1) % nodes.size();

        if (next == startIndex) {
            debug("All nodes failed");
            return null;
        }

        debug("Retrying on node " + next);
        return nodes.get(next).createWalletBlocking(userId, walletId, transaction_Id, nodeDelay, startIndex, next, nodes, deadline);
        
    }
    

    public void createWalletAsync(String userId, String walletId, String transaction_Id, Integer nodeDelay, Integer startIndex, Integer nodeIndex, ArrayList<ClientNodeService> nodes, Long commandNumber) {

        debug("Sending createWallet(async) request with userId: " + userId + " walletId: " + walletId);

        Metadata metadata = new Metadata();
        metadata.put(Metadata.Key.of("delaySeconds", Metadata.ASCII_STRING_MARSHALLER), Integer.toString(nodeDelay));

        NodeServiceGrpc.NodeServiceStub stubWithMeta = asyncStub.withInterceptors(MetadataUtils.newAttachHeadersInterceptor(metadata));
        
        CreateWalletRequest request;

        try {
            debug("Loading private key and building request");
            PrivateKey privateKey = Cripto.loadPrivateKey(userId.toLowerCase() + "_private.der");

            //Build request
            request = CreateWalletRequest.newBuilder()
                    .setUserId(userId)
                    .setWalletId(walletId)
                    .setTransactionId(transaction_Id)
                    .build();
            
            byte[] signature = Cripto.sign(request.toByteArray(), privateKey);

            request = request.toBuilder()
                    .setSignature(com.google.protobuf.ByteString.copyFrom(signature))
                    .build();
        
        } catch (Exception e) {
            debug("Error while creating wallet request with private key");
            System.err.println("Error while creating wallet request with private key: " + e.getMessage());
            return;
        }
        
        // Ask response
        stubWithMeta.createWallet(request, new StreamObserver<CreateWalletResponse>(){

            @Override
            public void onNext(CreateWalletResponse createWalletResponse) {
                debug("Created Wallet with userId: " + userId + " walletId: " + walletId);
                System.out.println("OK " + commandNumber);
            }

            @Override
            public void onError(Throwable t) {
                if (t instanceof StatusRuntimeException) {
                    StatusRuntimeException e = (StatusRuntimeException) t;

                    if (e.getStatus().getCode() == Status.Code.INVALID_ARGUMENT) {
                        System.err.println(e.getStatus().getDescription());
                        return;
                    }
                }
                System.err.println("createWallet(async) RPC failed: " + t.getMessage());
                int next = (nodeIndex + 1) % nodes.size();

                if (next == startIndex) {
                    debug("All nodes failed");
                    return;
                }

                debug("Retrying on node " + next);
                nodes.get(next).createWalletAsync(userId, walletId, transaction_Id, nodeDelay, startIndex, next, nodes, commandNumber);
            }

            @Override
            public void onCompleted() {
                debug("createWallet(async) call completed");
            }
        });
    }


    public DeleteWalletResponse deleteWalletBlocking(String userId, String walletId, String transaction_Id, Integer nodeDelay, Integer startIndex, Integer nodeIndex, ArrayList<ClientNodeService> nodes, Integer deadline) {

        debug("Sending deleteWallet(blocking) request with userId: " + userId + " walletId: " + walletId);

        Integer finalDeadline = defaultDeadline;
        if (deadline != null){
            finalDeadline = deadline;
        }

        Metadata metadata = new Metadata();
        metadata.put(Metadata.Key.of("delaySeconds", Metadata.ASCII_STRING_MARSHALLER), Integer.toString(nodeDelay));

        NodeServiceGrpc.NodeServiceBlockingStub stubWithMeta = blockingStub.withInterceptors(MetadataUtils.newAttachHeadersInterceptor(metadata)).withDeadlineAfter(finalDeadline, TimeUnit.SECONDS);

        // Ask response
        try {
            debug("Loading private key and building request");
            PrivateKey privateKey = Cripto.loadPrivateKey(userId.toLowerCase() + "_private.der");
        
            //Build request
            DeleteWalletRequest request = DeleteWalletRequest.newBuilder()
                    .setUserId(userId)
                    .setWalletId(walletId)
                    .setTransactionId(transaction_Id)
                    .build();
            
            byte[] signature = Cripto.sign(request.toByteArray(), privateKey);

            request = request.toBuilder()
                    .setSignature(com.google.protobuf.ByteString.copyFrom(signature))
                    .build();
                
            DeleteWalletResponse response = stubWithMeta.deleteWallet(request);
            debug("Delete Wallet(blocking) with userId: " + userId + " walletId: " + walletId);
            return response;
        } catch (StatusRuntimeException e) {
            if (e.getStatus().getCode() == Status.Code.INVALID_ARGUMENT) {
                System.err.println(e.getStatus().getDescription());
                return null;
            }
            System.err.println("deleteWallet(blocking) RPC failed: " + e.getStatus().getDescription() + " - " + e.getMessage());
            debug("deleteWallet(blocking) FAILED");
        } catch (Exception e) {
            System.err.println("Unexpected Error during the delete wallet(blocking) request: " + e.getMessage());
            debug("Unexpected exception in deleteWallet(blocking)");
        }
        int next = (nodeIndex + 1) % nodes.size();

        if (next == startIndex) {
            debug("All nodes failed");
            return null;
        }

        debug("Retrying on node " + next);
        return nodes.get(next).deleteWalletBlocking(userId, walletId, transaction_Id, nodeDelay, startIndex, next, nodes, deadline);  
    }


    public void deleteWalletAsync(String userId, String walletId, String transaction_Id, Integer nodeDelay, Integer startIndex, Integer nodeIndex, ArrayList<ClientNodeService> nodes, Long commandNumber) {

        debug("Sending deleteWallet(async) request with userId: " + userId + " walletId: " + walletId);

        Metadata metadata = new Metadata();
        metadata.put(Metadata.Key.of("delaySeconds", Metadata.ASCII_STRING_MARSHALLER), Integer.toString(nodeDelay));

        NodeServiceGrpc.NodeServiceStub stubWithMeta = asyncStub.withInterceptors(MetadataUtils.newAttachHeadersInterceptor(metadata));

        DeleteWalletRequest request;

        try {
            debug("Loading private key and building request");
            PrivateKey privateKey = Cripto.loadPrivateKey(userId.toLowerCase() + "_private.der");

            //Build request
            request = DeleteWalletRequest.newBuilder()
                    .setUserId(userId)
                    .setWalletId(walletId)
                    .setTransactionId(transaction_Id)
                    .build();
            
            byte[] signature = Cripto.sign(request.toByteArray(), privateKey);

            request = request.toBuilder()
                    .setSignature(com.google.protobuf.ByteString.copyFrom(signature))
                    .build();
        
        } catch (Exception e) {
            debug("Error while deleting wallet request with private key");
            System.err.println("Error while deleting wallet request with private key: " + e.getMessage());
            return;
        }
        

        // Ask response
        stubWithMeta.deleteWallet(request, new StreamObserver<DeleteWalletResponse>(){

            @Override
            public void onNext(DeleteWalletResponse deleteWalletResponse) {
                debug("Delete Wallet(async) with userId: " + userId + " walletId: " + walletId);
                System.out.println("OK " + commandNumber);
            }

            @Override
            public void onError(Throwable t) {
                if (t instanceof StatusRuntimeException) {
                    StatusRuntimeException e = (StatusRuntimeException) t;

                    if (e.getStatus().getCode() == Status.Code.INVALID_ARGUMENT) {
                        System.err.println(e.getStatus().getDescription());
                        return;
                    }
                }
                System.err.println("deleteWallet(async) RPC failed: " + t.getMessage());

                int next = (nodeIndex + 1) % nodes.size();

                if (next == startIndex) {
                    debug("All nodes failed");
                    return;
                }

                debug("Retrying on node " + next);
                nodes.get(next).deleteWalletAsync(userId, walletId, transaction_Id, nodeDelay, startIndex, next, nodes, commandNumber);
            }

            @Override
            public void onCompleted() {
                debug("deleteWallet(async) call completed");
            }
        });
    }
    

    public ReadBalanceResponse readBalance(String walletId, Integer nodeDelay, Integer startIndex, Integer nodeIndex, ArrayList<ClientNodeService> nodes, Integer deadline){

        debug("Sending readBalance request: " + " walletId: " + walletId);

        Integer finalDeadline = defaultDeadline;
        if (deadline != null){
            finalDeadline = deadline;
        }

        Metadata metadata = new Metadata();
        metadata.put(Metadata.Key.of("delaySeconds", Metadata.ASCII_STRING_MARSHALLER), Integer.toString(nodeDelay));

        NodeServiceGrpc.NodeServiceBlockingStub stubWithMeta = blockingStub.withInterceptors(MetadataUtils.newAttachHeadersInterceptor(metadata)).withDeadlineAfter(finalDeadline, TimeUnit.SECONDS);

        //Build request
        ReadBalanceRequest request = ReadBalanceRequest.newBuilder()
                .setWalletId(walletId)
                .build();

        // Ask response
        try {
            ReadBalanceResponse response = stubWithMeta.readBalance(request);
            debug("Read balance for walletId: " + walletId);
            return response;
        } catch (StatusRuntimeException e) {
            if (e.getStatus().getCode() == Status.Code.INVALID_ARGUMENT) {
                System.err.println(e.getStatus().getDescription());
                return null;
            }
            System.err.println("readBalance RPC failed: " + e.getStatus().getDescription() + " - " + e.getMessage());
            debug("readBalance FAILED");
        } catch (Exception e) {
            System.err.println("Unexpected Error during the read balance request: " + e.getMessage());
            debug("Unexpected exception in readBalance");
        }
        int next = (nodeIndex + 1) % nodes.size();

        if (next == startIndex) {
            debug("All nodes failed");
            return null;
        }

        debug("Retrying on node " + next);
        nodes.get(next).readBalance(walletId, nodeDelay, startIndex, next, nodes, deadline);
        return null;
    }


    public TransferResponse transferBlocking(String src_userId, String src_walletId, String dst_walletId, long value, String transaction_Id, Integer nodeDelay, Integer startIndex, Integer nodeIndex, ArrayList<ClientNodeService> nodes, Integer deadline) {

        debug("Sending transfer(blocking) request with src_userId: " + src_userId + " src_walletId: " + src_walletId + " dst_walletId: " + dst_walletId + " value: " + value);

        Integer finalDeadline = defaultDeadline;
        if (deadline != null){
            finalDeadline = deadline;
        }

        Metadata metadata = new Metadata();
        metadata.put(Metadata.Key.of("delaySeconds", Metadata.ASCII_STRING_MARSHALLER), Integer.toString(nodeDelay));

        NodeServiceGrpc.NodeServiceBlockingStub stubWithMeta = blockingStub.withInterceptors(MetadataUtils.newAttachHeadersInterceptor(metadata)).withDeadlineAfter(finalDeadline, TimeUnit.SECONDS);

        // Ask response
        try {
            debug("Loading private key and building request");
            PrivateKey privateKey = Cripto.loadPrivateKey(src_userId.toLowerCase() + "_private.der");
        
            //Build request
            TransferRequest request = TransferRequest.newBuilder()
                    .setSrcUserId(src_userId)
                    .setSrcWalletId(src_walletId)
                    .setDstWalletId(dst_walletId)
                    .setValue(value)
                    .setTransactionId(transaction_Id)
                    .build();
            
            byte[] signature = Cripto.sign(request.toByteArray(), privateKey);

            request = request.toBuilder()
                    .setSignature(com.google.protobuf.ByteString.copyFrom(signature))
                    .build();
                
            TransferResponse response = stubWithMeta.transfer(request);
            debug("Transfer(blocking) from src_userId: " + src_userId + " src_walletId: " + src_walletId + " | to dst_walletId: " + dst_walletId + " | value: " + value);
            return response;
        } catch (StatusRuntimeException e) {
            if (e.getStatus().getCode() == Status.Code.INVALID_ARGUMENT) {
                System.err.println(e.getStatus().getDescription());
                return null;
            }
            System.err.println("transfer(blocking) RPC failed: " + e.getStatus().getDescription() + " - " + e.getMessage());
            debug("transfer(blocking) FAILED");
        } catch (Exception e) {
            System.err.println("Unexpected Error during the transfer(blocking) request: " + e.getMessage());
            debug("Unexpected exception in transfer(blocking)");
        }
        int next = (nodeIndex + 1) % nodes.size();

        if (next == startIndex) {
            debug("All nodes failed");
            return null;
        }

        debug("Retrying on node " + next);
        return nodes.get(next).transferBlocking(src_userId, src_walletId, dst_walletId, value, transaction_Id, nodeDelay, startIndex, next, nodes, deadline);
      
    }


    public void transferAsync(String src_userId, String src_walletId, String dst_walletId, long value, String transaction_Id, Integer nodeDelay, Integer startIndex, Integer nodeIndex, ArrayList<ClientNodeService> nodes, Long commandNumber) {

        debug("Sending transfer request with src_userId: " + src_userId + " src_walletId: " + src_walletId + " dst_walletId: " + dst_walletId + " value: " + value);

        Metadata metadata = new Metadata();
        metadata.put(Metadata.Key.of("delaySeconds", Metadata.ASCII_STRING_MARSHALLER), Integer.toString(nodeDelay));

        NodeServiceGrpc.NodeServiceStub  stubWithMeta = asyncStub.withInterceptors(MetadataUtils.newAttachHeadersInterceptor(metadata));

        TransferRequest request;

        try {
            debug("Loading private key and building request");
            PrivateKey privateKey = Cripto.loadPrivateKey(src_userId.toLowerCase() + "_private.der");

            //Build request
            request = TransferRequest.newBuilder()
                    .setSrcUserId(src_userId)
                    .setSrcWalletId(src_walletId)
                    .setDstWalletId(dst_walletId)
                    .setValue(value)
                    .setTransactionId(transaction_Id)
                    .build();
            
            byte[] signature = Cripto.sign(request.toByteArray(), privateKey);

            request = request.toBuilder()
                    .setSignature(com.google.protobuf.ByteString.copyFrom(signature))
                    .build();
        
        } catch (Exception e) {
            debug("Error while transfer wallet request with private key");
            System.err.println("Error while transfer wallet request with private key: " + e.getMessage());
            return;
        }

        // Ask response
        stubWithMeta.transfer(request, new StreamObserver<TransferResponse>(){

            @Override
            public void onNext(TransferResponse transferResponse) {
                debug("Transfer(async) from src_userId: " + src_userId + " src_walletId: " + src_walletId + " | to dst_walletId: " + dst_walletId + " | value: " + value);
                System.out.println("OK " + commandNumber);
            }

            @Override
            public void onError(Throwable t) {
                if (t instanceof StatusRuntimeException) {
                    StatusRuntimeException e = (StatusRuntimeException) t;

                    if (e.getStatus().getCode() == Status.Code.INVALID_ARGUMENT) {
                        System.err.println(e.getStatus().getDescription());
                        return;
                    }
                }
                System.err.println("transfer(async) RPC failed: " + t.getMessage());
                int next = (nodeIndex + 1) % nodes.size();

                if (next == startIndex) {
                    debug("All nodes failed");
                    return;
                }

                debug("Retrying on node " + next);
                nodes.get(next).transferAsync(src_userId, src_walletId, dst_walletId, value, transaction_Id, nodeDelay, startIndex, next, nodes, commandNumber);
            }

            @Override
            public void onCompleted() {
                debug("transfer(async) call completed");
            }
        });
    }


    public GetBlockchainStateResponse getBlockchainState(Integer startIndex, Integer nodeIndex, ArrayList<ClientNodeService> nodes, Integer deadline) {
        debug("Sending getBlockchainState request");

        Integer finalDeadline = defaultDeadline;
        if (deadline != null){
            finalDeadline = deadline;
        }

        NodeServiceGrpc.NodeServiceBlockingStub stubWithDeadline = blockingStub.withDeadlineAfter(finalDeadline, TimeUnit.SECONDS);

        //Build request
        GetBlockchainStateRequest request = GetBlockchainStateRequest.newBuilder().build();

        // Ask response
        try {
            GetBlockchainStateResponse response = stubWithDeadline.getBlockchainState(request);
            debug("Blockchain State: " + response.getTransactionsList());
            return response;
        } catch (StatusRuntimeException e) {
            if (e.getStatus().getCode() == Status.Code.INVALID_ARGUMENT) {
                System.err.println(e.getStatus().getDescription());
                return null;
            }
            System.err.println("getBlockchainState RPC failed: " + e.getStatus().getDescription() + " - " + e.getMessage());
            debug("getBlockchainState FAILED");
        } catch (Exception e) {
            System.err.println("Unexpected Error during the get Blockchain State request: " + e.getMessage());
            debug("Unexpected exception in getBlockchainState");
        }
        int next = (nodeIndex + 1) % nodes.size();

        if (next == startIndex) {
            debug("All nodes failed");
            return null;
        }

        debug("Retrying on node " + next);
        nodes.get(next).getBlockchainState(startIndex, next, nodes, deadline);
        return null;
    }

    // Properly close the gRPC channel
    public void shutdown() {
        channel.shutdown();
        debug("Closed gRPC channel.");
    }
}