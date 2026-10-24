package pt.tecnico.blockchainist.node.domain;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import pt.tecnico.blockchainist.block.Bc;
import pt.tecnico.blockchainist.contract.Transaction;
import pt.tecnico.blockchainist.transaction.Tx;
import pt.tecnico.blockchainist.wallet.domain.Wallet;

public class NodeState {

    /** Set flag to true to print debug messages. 
     * The flag can be set using the -Ddebug command line option. */
    private static final boolean DEBUG_FLAG = (System.getProperty("debug") != null);

    /** Helper method to print debug messages. */
    private static void debug(String message) {
        if (DEBUG_FLAG)
            System.err.println("[DEBUG] NodeState: " +  message);
    }
    
    final private Map<String, Wallet> wallets;
    final private List<Tx> ledger; //it uses the domain class
    private final List<Bc> blockchain; //it uses the domain class

    private final Object lock = new Object();

    public NodeState() {
        this.wallets = new HashMap<>();
        Wallet walletBC = new Wallet("BC", "bc");
        walletBC.deposit(1000);
        wallets.put("bc", walletBC);
        this.ledger = new ArrayList<>();
        this.blockchain = new ArrayList<>();
        
    }

    public String getWalletOwner(String walletId) {
        Wallet wallet = wallets.get(walletId);

        if (wallet == null) {
            throw new IllegalArgumentException("Wallet does not exist: " + walletId);
        }

        return wallet.getUserId();
    }

    // Apply the commands

    private void createWallet(String userId, String walletId) {
        if (wallets.containsKey(walletId)) {
            throw new IllegalArgumentException("Wallet already exists");
        }

        wallets.put(walletId, new Wallet(userId, walletId));
        debug("Created wallet with userId: " + userId + " walletId: " + walletId);
    }

    private void deleteWallet(String userId, String walletId) {
        Wallet wallet = wallets.get(walletId);

        if (wallet == null)
            throw new IllegalArgumentException("Wallet does not exist");

        if (!wallet.getUserId().equals(userId))
            throw new IllegalArgumentException("Wallet isnt from user");

        if (wallet.getBalance() != 0)
            throw new IllegalArgumentException("Balance isnt zero");

        wallets.remove(walletId);
        debug("Deleted wallet with userId: " + userId + " walletId: " + walletId);
    }

    private void transfer(String srcUserId, String srcWalletId, String dstWalletId, Long amount) {
        Wallet src = wallets.get(srcWalletId);
        Wallet dst = wallets.get(dstWalletId);

        if (src == null)
            throw new IllegalArgumentException("Source Wallet does not exist");

        if (dst == null)
            throw new IllegalArgumentException("Destination Wallet does not exist");

        if (!src.getUserId().equals(srcUserId))
            throw new IllegalArgumentException("Source Wallet isnt from user");

        if (amount == null || amount <= 0)
            throw new IllegalArgumentException("Amount isnt positive");

        if (src.getBalance() < amount)
            throw new IllegalArgumentException("Insufficient balance");

        src.withdraw(amount);
        dst.deposit(amount);
        debug("Tranfered " + amount + " from wallet: " + srcWalletId + " from: " + srcUserId + " to: " + dstWalletId);
    }

    public long readBalance(String walletId) {
        synchronized (lock) {
            Wallet wallet = wallets.get(walletId);

            if (wallet == null)
                throw new IllegalArgumentException("Wallet does not exist");
            
            return wallet.getBalance();
        }
    }


    public void applyTransaction(Tx tx) {
        synchronized (lock) {
            switch (tx.getType()) {
                case CREATE_WALLET -> createWallet(tx.getUserId(), tx.getWalletId());
                case DELETE_WALLET -> deleteWallet(tx.getUserId(), tx.getWalletId());
                case TRANSFER -> transfer(tx.getSrcUserId(), tx.getSrcWalletId(), tx.getDstWalletId(), tx.getValue());
                default -> throw new IllegalArgumentException("Unknown transaction type");
            }
            
            addTransaction(tx);
            debug("Applied transaction " + tx + " successfully ");
        }
    }

    public List<Transaction> getBlockchainState() {
        synchronized(lock) {
            return ledger.stream()
                        .map(Tx::toProto)   // método que converte Tx -> Transaction
                        .toList();
        }
    }

    public void addTransaction(Tx tx) {
        ledger.add(tx);
        debug("Added transaction " + tx);
    }

    public void addBlock(Bc block) {
        synchronized(lock) { 
            blockchain.add(block);
            debug("Added block " + block);
        }
    }

    public List<Tx> getLedger() {
        return ledger;
    }

    public List<Bc> getBlockchain() {
        return blockchain;
    }

    public void futureDeleteWallet(String walletId){
        wallets.get(walletId).futureDelete();
    }

    public boolean checkWalletDeletion(String walletId){
        return wallets.get(walletId).getDelete();
    }
}
