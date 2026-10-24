package pt.tecnico.blockchainist.block;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import pt.tecnico.blockchainist.transaction.Tx;

public class Bc {
    
    private int blockNumber;
    private final List<Tx> transactions;

    private Bc(int blockNumber, List<Tx> transactions) {
        this.blockNumber = blockNumber;
        this.transactions = Collections.unmodifiableList(transactions);
    }

    public static Bc fromProto(pt.tecnico.blockchainist.contract.Block proto) {
        List<Tx> txs = proto.getTransactionsList().stream()
                .map(ptx -> new Tx(ptx))
                .collect(Collectors.toUnmodifiableList());
        return new Bc(proto.getBlockNumber(), txs);
    }

    public pt.tecnico.blockchainist.contract.Block toProto() {
        pt.tecnico.blockchainist.contract.Block.Builder builder =
                pt.tecnico.blockchainist.contract.Block.newBuilder()
                        .setBlockNumber(blockNumber);
        transactions.stream()
                .map(Tx::toProto)
                .forEach(builder::addTransactions);
        return builder.build();
    }

    public int getBlockNumber() {
        return blockNumber;
    }

    public List<Tx> getTransactions() {
        return transactions;
    }

    @Override
    public String toString() {
        return "Block number:" + blockNumber + " Transactions number:" + transactions.size();
    }
}
