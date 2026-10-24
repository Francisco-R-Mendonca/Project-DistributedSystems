package pt.tecnico.blockchainist.client;

import java.util.ArrayList;
import java.util.Scanner;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

import pt.tecnico.blockchainist.client.grpc.ClientNodeService;
import pt.tecnico.blockchainist.contract.CreateWalletResponse;
import pt.tecnico.blockchainist.contract.DeleteWalletResponse;
import pt.tecnico.blockchainist.contract.GetBlockchainStateResponse;
import pt.tecnico.blockchainist.contract.ReadBalanceResponse;
import pt.tecnico.blockchainist.contract.TransferResponse;

import java.util.UUID;

public class CommandProcessor {

    private static final String SPACE = " ";
    private static final String CREATE_BLOCKING = "C";
    private static final String CREATE_ASYNC = "c";
    private static final String DELETE_BLOCKING = "E";
    private static final String DELETE_ASYNC = "e";
    private static final String BALANCE_BLOCKING = "S";
    private static final String BALANCE_ASYNC = "s";
    private static final String TRANSFER_BLOCKING = "T";
    private static final String TRANSFER_ASYNC = "t";
    private static final String DEBUG_BLOCKCHAIN_STATE = "B";
    private static final String PAUSE = "P";
    private static final String EXIT = "X";

    private static final Pattern ID_PATTERN = Pattern.compile("^[a-zA-Z0-9]+$");

    private final AtomicLong commandCounter = new AtomicLong(0);
    private final ArrayList<ClientNodeService> nodes;

    public CommandProcessor(ArrayList<ClientNodeService> nodes) {
        this.nodes = nodes;
    }

    void userInputLoop() {

        Scanner scanner = new Scanner(System.in);
        boolean exit = false;

        while (!exit) {
            System.out.print("\n> ");
            String line = scanner.nextLine().trim();
            String[] split = line.split(SPACE);
            try {
                switch (split[0]) {
                    case CREATE_BLOCKING:
                        this.create(split, true);
                        break;

                    case CREATE_ASYNC:
                        this.create(split, false);
                        break;

                    case DELETE_BLOCKING:
                        this.delete(split, true);
                        break;

                    case DELETE_ASYNC:
                        this.delete(split, false);
                        break;

                    case BALANCE_BLOCKING:
                        this.balance(split, true);
                        break;

                    case BALANCE_ASYNC:
                        this.balance(split, false);
                        break;

                    case TRANSFER_BLOCKING:
                        this.transfer(split, true);
                        break;

                    case TRANSFER_ASYNC:
                        this.transfer(split, false);
                        break;

                    case DEBUG_BLOCKCHAIN_STATE:
                        this.debugBlockchainState(split);
                        break;

                    case PAUSE:
                        this.pause(split);
                        break;

                    case EXIT:
                        exit = true;
                        break;

                    default:
                        printUsage();
                        break;
                }
            } catch (IllegalArgumentException e) {
                System.err.println("Error: " + e.getMessage());
                printUsage();
            }
        }
        scanner.close();
    }

    private void create(String[] split, boolean isBlocking) {
        this.checkCreateCommandArgs(split);

        Long commandNumber = this.commandCounter.incrementAndGet();
        String transaction_Id = UUID.randomUUID().toString();

        String userId = split[1];
        String walletId = split[2];
        Integer nodeIndex = Integer.parseInt(split[3]);
        Integer nodeDelay = Integer.parseInt(split[4]);
        Integer deadline = null;
        if (split.length == 6){
            deadline = Integer.parseInt(split[5]);
        }

        if(isBlocking){
            CreateWalletResponse response = this.nodes.get(nodeIndex).createWalletBlocking(userId, walletId, transaction_Id, nodeDelay, nodeIndex, nodeIndex, nodes, deadline);
            if (response != null) {
                System.out.println("OK " + commandNumber);
            }
        }
        else{
            this.nodes.get(nodeIndex).createWalletAsync(userId, walletId, transaction_Id, nodeDelay, nodeIndex, nodeIndex, nodes, commandNumber);
        }
    }

    private void delete(String[] split, boolean isBlocking) {
        this.checkDeleteCommandArgs(split);

        Long commandNumber = this.commandCounter.incrementAndGet();
        String transaction_Id = UUID.randomUUID().toString();

        String userId = split[1];
        String walletId = split[2];
        Integer nodeIndex = Integer.parseInt(split[3]);
        Integer nodeDelay = Integer.parseInt(split[4]);
        Integer deadline = null;
        if (split.length == 6){
            deadline = Integer.parseInt(split[5]);
        }

        if(isBlocking){
            DeleteWalletResponse response = this.nodes.get(nodeIndex).deleteWalletBlocking(userId, walletId, transaction_Id, nodeDelay, nodeIndex, nodeIndex, this.nodes, deadline);
            if (response != null) {
                System.out.println("OK " + commandNumber);
            }
        }
        else{
            this.nodes.get(nodeIndex).deleteWalletAsync(userId, walletId, transaction_Id, nodeDelay, nodeIndex, nodeIndex, this.nodes, commandNumber);
        }
    }

    private void balance(String[] split, boolean isBlocking) {
        this.checkBalanceCommandArgs(split);

        Long commandNumber = this.commandCounter.incrementAndGet();

        String walletId = split[1];
        Integer nodeIndex = Integer.parseInt(split[2]);
        Integer nodeDelay = Integer.parseInt(split[3]);
        Integer deadline = null;
        if (split.length == 5){
            deadline = Integer.parseInt(split[4]);
        }

        ReadBalanceResponse response = this.nodes.get(nodeIndex).readBalance(walletId, nodeDelay, nodeIndex, nodeIndex, this.nodes, deadline);
        if (response != null) {
            System.out.println("OK " + commandNumber + "\n" + response.getBalance());
        }
    }

    private void transfer(String[] split, boolean isBlocking) {
        this.checkTransferCommandArgs(split);

        Long commandNumber = this.commandCounter.incrementAndGet();
        String transaction_Id = UUID.randomUUID().toString();

        String src_userId = split[1];
        String src_walletId = split[2];
        String dst_walletId = split[3];
        Long value = Long.parseLong(split[4]);
        Integer nodeIndex = Integer.parseInt(split[5]);
        Integer nodeDelay = Integer.parseInt(split[6]);
        Integer deadline = null;
        if (split.length == 8){
            deadline = Integer.parseInt(split[7]);
        }

        if(isBlocking){
            TransferResponse response = this.nodes.get(nodeIndex).transferBlocking(src_userId, src_walletId, dst_walletId, value, transaction_Id, nodeDelay, nodeIndex, nodeIndex, this.nodes, deadline);
            if (response != null) {
                System.out.println("OK " + commandNumber);
            }
        }
        else{
            this.nodes.get(nodeIndex).transferAsync(src_userId, src_walletId, dst_walletId, value, transaction_Id, nodeDelay, nodeIndex, nodeIndex, this.nodes, commandNumber);
        }
    }

    private void debugBlockchainState(String[] split) {
        this.checkDebugBlockchainStateArgs(split);

        Long commandNumber = this.commandCounter.incrementAndGet();

        Integer nodeIndex = Integer.parseInt(split[1]);
        Integer deadline = null;
        if (split.length == 3){
            deadline = Integer.parseInt(split[2]);
        }

        GetBlockchainStateResponse response = this.nodes.get(nodeIndex).getBlockchainState(nodeIndex, nodeIndex, this.nodes, deadline);
        if (response != null) {
            System.out.println("OK " + commandNumber + "\n" + response.toString());
        }
    }

    private void pause(String[] split) {
        this.checkPauseArgs(split);

        Integer time;

        time = Integer.parseInt(split[1]);

        try {
            Thread.sleep(time * 1000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private void checkCreateCommandArgs(String[] split) {
        // C|c <user_id> <wallet_id> <node_index> <node_delay>
        if (split.length != 5 && split.length != 6) {
            throw new IllegalArgumentException("Expected 5 or 6 arguments, got " + split.length);
        }

        if (!ID_PATTERN.matcher(split[1]).matches()) {
            throw new IllegalArgumentException("Expected User ID to be composed of ASCII alphanumeric characters, got \"" + split[1] + "\"");
        }

        if (!ID_PATTERN.matcher(split[2]).matches()) {
            throw new IllegalArgumentException("Expected Wallet ID to be composed of ASCII alphanumeric characters, got \"" + split[2] + "\"");
        }

        try {
            int nodeIndex = Integer.parseInt(split[3]);
            if (nodeIndex < 0 || nodeIndex >= this.nodes.size()) {
                throw new IllegalArgumentException("Node index must be between 0 and " + (this.nodes.size() - 1));
            }
            if (Integer.parseInt(split[4]) < 0) {
                throw new IllegalArgumentException("Node delay cannot be negative");
            }
            if (split.length == 6){
                if (Integer.parseInt(split[5]) < 0) {
                    throw new IllegalArgumentException("Deadline cannot be negative");
                }
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Expected initial balance, node number, node delay and deadline to be integers");
        }
    }

    private void checkDeleteCommandArgs(String[] split) {
        // E|e <user_id> <wallet_id> <node_index> <node_delay>
        if (split.length != 5 && split.length != 6) {
            throw new IllegalArgumentException("Expected 5 or 6 arguments, got " + split.length);
        }

        if (!ID_PATTERN.matcher(split[1]).matches()) {
            throw new IllegalArgumentException("Expected User ID to be composed of ASCII alphanumeric characters, got \"" + split[1] + "\"");
        }

        if (!ID_PATTERN.matcher(split[2]).matches()) {
            throw new IllegalArgumentException("Expected Wallet ID to be composed of ASCII alphanumeric characters, got \"" + split[2] + "\"");
        }

        try {
            int nodeIndex = Integer.parseInt(split[3]);
            if (nodeIndex < 0 || nodeIndex >= this.nodes.size()) {
                throw new IllegalArgumentException("Node index must be between 0 and " + (this.nodes.size() - 1));
            }
            if (Integer.parseInt(split[4]) < 0) {
                throw new IllegalArgumentException("Node delay cannot be negative");
            }
            if (split.length == 6){
                if (Integer.parseInt(split[5]) < 0) {
                    throw new IllegalArgumentException("Deadline cannot be negative");
                }
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Expected node number, node delay and deadline to be integers");
        }
    }

    private void checkBalanceCommandArgs(String[] split) {
        // S|s <wallet_id> <node_index> <node_delay>
        if (split.length != 4 && split.length != 5) {
            throw new IllegalArgumentException("Expected 4 or 5 arguments, got " + split.length);
        }

        if (!ID_PATTERN.matcher(split[1]).matches()) {
            throw new IllegalArgumentException("Expected Wallet ID to be composed of ASCII alphanumeric characters, got \"" + split[1] + "\"");
        }

        try {
            int nodeIndex = Integer.parseInt(split[2]);
            if (nodeIndex < 0 || nodeIndex >= this.nodes.size()) {
                throw new IllegalArgumentException("Node index must be between 0 and " + (this.nodes.size() - 1));
            }
            if (Integer.parseInt(split[3]) < 0) {
                throw new IllegalArgumentException("Node delay cannot be negative");
            }
            if (split.length == 5){
                if (Integer.parseInt(split[4]) < 0) {
                    throw new IllegalArgumentException("Deadline cannot be negative");
                }
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Expected node number, node delay and deadline to be integers");
        }
    }

    private void checkTransferCommandArgs(String[] split) {
        // T|t <source_user_id> <source_wallet_id> <destination_wallet_id> <amount> <node_index> <node_delay>
        if (split.length != 7 && split.length != 8) {
            throw new IllegalArgumentException("Expected 7 or 8 arguments, got " + split.length);
        }

        if (!ID_PATTERN.matcher(split[1]).matches()) {
            throw new IllegalArgumentException("Expected Source User ID to be composed of ASCII alphanumeric characters, got \"" + split[1] + "\"");
        }

        if (!ID_PATTERN.matcher(split[2]).matches()) {
            throw new IllegalArgumentException("Expected Source Wallet ID to be composed of ASCII alphanumeric characters, got \"" + split[2] + "\"");
        }

        if (!ID_PATTERN.matcher(split[3]).matches()) {
            throw new IllegalArgumentException("Expected Destination Wallet ID to be composed of ASCII alphanumeric characters, got \"" + split[1] + "\"");
        }

        try {
            if (Long.parseLong(split[4]) < 0) {
                throw new IllegalArgumentException("Amount cannot be negative");
            }
            int nodeIndex = Integer.parseInt(split[5]);
            if (nodeIndex < 0 || nodeIndex >= this.nodes.size()) {
                throw new IllegalArgumentException("Node index must be between 0 and " + (this.nodes.size() - 1));
            }
            if (Integer.parseInt(split[6]) < 0) {
                throw new IllegalArgumentException("Node delay cannot be negative");
            }
            if (split.length == 8){
                if (Integer.parseInt(split[7]) < 0) {
                    throw new IllegalArgumentException("Deadline cannot be negative");
                }
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Expected amount, node number, and node delay to be integers");
        }
    }

    private void checkDebugBlockchainStateArgs(String[] split) {
        // B <node_index>
        if (split.length != 2 && split.length != 3) {
            throw new IllegalArgumentException("Expected 2 arguments, got " + split.length);
        }

        try {
            int nodeIndex = Integer.parseInt(split[1]);
            if (nodeIndex < 0 || nodeIndex >= this.nodes.size()) {
                throw new IllegalArgumentException("Node index must be between 0 and " + (this.nodes.size() - 1));
            }
            if (split.length == 3){
                if (Integer.parseInt(split[2]) < 0) {
                    throw new IllegalArgumentException("Deadline cannot be negative");
                }
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Expected node index to be an integer");
        }
    }

    private void checkPauseArgs(String[] split) {
        // P <integer>
        if (split.length != 2) {
            throw new IllegalArgumentException("Expected 2 arguments, got " + split.length);
        }

        try {
            if (Integer.parseInt(split[1]) < 0) {
                throw new IllegalArgumentException("Pause time cannot be negative");
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Expected pause time to be an integer");
        }
    }

    private static void printUsage() {
        System.err.println("Usage:\n" +
                "- C|c <user_id> <wallet_id> <node_index> <node_delay>\n" +
                "- E|e <user_id> <wallet_id> <node_index> <node_delay>\n" +
                "- S|s <wallet_id> <node_index> <node_delay>\n" +
                "- T|t <source_user_id> <source_wallet_id> <destination_wallet_id> <amount> <node_index> <node_delay>\n" +
                "- B <node_index>\n" +
                "- P <integer>\n" +
                "- X\n");
    }

}
