package pt.tecnico.blockchainist.wallet.domain;

public class Wallet {

    private final String walletId;
    private final String userId;
    private long balance;
    boolean deletionFlag = false;

    public Wallet(String userId, String walletId) {
        this.walletId = walletId;
        this.userId = userId;
        this.balance = 0L;
    }

    public String getWalletId() {
        return walletId;
    }

    public String getUserId() {
        return userId;
    }

    public long getBalance() {
        return balance;
    }

    public boolean getDelete() {
        return deletionFlag;
    }

    public void futureDelete() {
        this.deletionFlag = true;
    }

    public void deposit(long amount) {
        this.balance += amount;
    }

    public void withdraw(long amount) {
        this.balance -= amount;
    }
}