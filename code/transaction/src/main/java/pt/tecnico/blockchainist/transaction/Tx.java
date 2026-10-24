package pt.tecnico.blockchainist.transaction;

import pt.tecnico.blockchainist.contract.CreateWalletRequest;
import pt.tecnico.blockchainist.contract.DeleteWalletRequest;
import pt.tecnico.blockchainist.contract.TransferRequest;

public class Tx {
    public enum Type {
        CREATE_WALLET,
        DELETE_WALLET,
        TRANSFER
    }
    private final Type type;

    // Create/Delete
    private String userId;
    private String walletId;

    // Transfer
    private String srcUserId;
    private String srcWalletId;
    private String dstWalletId;
    private long value;

    public static Tx createWallet(String userId, String walletId) {
        Tx t = new Tx(Type.CREATE_WALLET);
        t.userId = userId;
        t.walletId = walletId;
        return t;
    }

    public static Tx deleteWallet(String userId, String walletId) {
        Tx t = new Tx(Type.DELETE_WALLET);
        t.userId = userId;
        t.walletId = walletId;
        return t;
    }

    public static Tx transfer(String srcUserId, String srcWalletId, String dstWalletId, long value) {
        Tx t = new Tx(Type.TRANSFER);
        t.srcUserId = srcUserId;
        t.srcWalletId = srcWalletId;
        t.dstWalletId = dstWalletId;
        t.value = value;
        return t;
    }

    private Tx(Type type) {
        this.type = type;
    }

    //cria trasaction (dominio) a partir da trasaction (proto)
    public Tx(pt.tecnico.blockchainist.contract.Transaction proto) {
        switch (proto.getOperationCase()) {
            case CREATE_WALLET:
                this.type = Type.CREATE_WALLET;
                this.userId = proto.getCreateWallet().getUserId();
                this.walletId = proto.getCreateWallet().getWalletId();
                break;
            case DELETE_WALLET:
                this.type = Type.DELETE_WALLET;
                this.userId = proto.getDeleteWallet().getUserId();
                this.walletId = proto.getDeleteWallet().getWalletId();
                break;
            case TRANSFER:
                this.type = Type.TRANSFER;
                this.srcUserId = proto.getTransfer().getSrcUserId();
                this.srcWalletId = proto.getTransfer().getSrcWalletId();
                this.dstWalletId = proto.getTransfer().getDstWalletId();
                this.value = proto.getTransfer().getValue();
                break;
            default:
                throw new IllegalArgumentException("Invalid transaction");
        }
    }

    //converte trasaction do dominio para trasaction do proto
    public pt.tecnico.blockchainist.contract.Transaction toProto() {
        pt.tecnico.blockchainist.contract.Transaction.Builder builder =
                pt.tecnico.blockchainist.contract.Transaction.newBuilder();
        switch (type) {
            case CREATE_WALLET:
                builder.setCreateWallet(
                        CreateWalletRequest.newBuilder()
                                .setUserId(userId)
                                .setWalletId(walletId)
                                .build()
                );
                break;
            case DELETE_WALLET:
                builder.setDeleteWallet(
                        DeleteWalletRequest.newBuilder()
                                .setUserId(userId)
                                .setWalletId(walletId)
                                .build()
                );
                break;
            case TRANSFER:
                builder.setTransfer(
                        TransferRequest.newBuilder()
                                .setSrcUserId(srcUserId)
                                .setSrcWalletId(srcWalletId)
                                .setDstWalletId(dstWalletId)
                                .setValue(value)
                                .build()
                );
                break;
        }
        return builder.build();
    }

    public Type getType() {
        return type;
    }

    public String getUserId() {
        return userId;
    }

    public String getWalletId() {
        return walletId;
    }

    public String getSrcUserId() {
        return srcUserId;
    }

    public String getSrcWalletId() {
        return srcWalletId;
    }

    public String getDstWalletId() {
        return dstWalletId;
    }

    public long getValue() {
        return value;
    }

    @Override
    public String toString() {
        switch (type) {
            case CREATE_WALLET:
                return "CREATE_WALLET user=" + userId + " wallet=" + walletId;
            case DELETE_WALLET:
                return "DELETE_WALLET user=" + userId + " wallet=" + walletId;
            case TRANSFER:
                return "TRANSFER " + srcWalletId + " -> " + dstWalletId + " value=" + value;
            default:
                return "UNKNOWN";
        }
    }
} 
    

