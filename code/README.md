# BlockchainIST

Distributed Systems Project 2026

**Group A17**

**Difficulty level: I am Death incarnate!**


### Code Identification

In all source files (namely in the *groupId*s of the POMs), replace __GXX__ with your group identifier. The group
identifier consists of either A or T followed by the group number - always two digits. This change is important for 
code dependency management, to ensure your code runs using the correct components and not someone else's.

### Team Members


| Number | Name              | User                             | Email                               |
|--------|-------------------|----------------------------------|-------------------------------------|
| 109260  | Ana Santos       | <https://github.com/anabdsantos>   | <mailto:ana.b.dos.santos@tecnico.ulisboa.pt>   |
| 109264  | Francisco Mendonça       | <https://github.com/Francisco-R-Mendonca>     | <mailto:francisco.r.mendonca@tecnico.ulisboa.pt>     |
| 109834  | Ricardo Fonseca     | <https://github.com/kako94321> | <mailto:ricardofonseca94321@tecnico.ulisboa.pt> |

## Getting Started

The overall system is made up of several modules.
The definition of messages and services is in _Contract_.

See the [Project Statement](https://github.com/tecnico-distsys/BlockchainIST-2026) for a complete domain and system description.

### Prerequisites

The Project is configured with Java 17 (which is only compatible with Maven >= 3.8), but if you want to use Java 11 you
can too -- just downgrade the version in the POMs.

To confirm that you have them installed and which versions they are, run in the terminal:

```s
javac -version
mvn -version
```

### Installation

To compile and install all modules:

```s
mvn clean install
```

### Valid Users and Their Organizations

| User   | Organization |
|--------|-------------|
| BC     | OrgA        |
| Alice  | OrgA        |
| Bob    | OrgA        |
| Charlie| OrgA        |
| David  | OrgB        |
| Emma   | OrgB        |
| Fred   | OrgB        |
| Ginger | OrgC        |
| Henry  | OrgC        |
| Iris   | OrgC        |

### How to run

Start the components in the following order:

Sequencer:
```s
mvn -pl sequencer exec:java -Dexec.args="3001 -debug"
```
- Sequencer arguments: `<port> [<blockSize> [<timeout>]] [-debug]`

Node:
```s
mvn -pl node exec:java -Dexec.args="2001 OrgA localhost:3001 -debug" 
```
- Node arguments: `<nodePort> <Organization> <sequencerHost:sequencerPort> [-debug]`

Client:
```s
mvn -pl client exec:java -Dexec.args="localhost:2001:OrgA -debug" 
```
- Client arguments: `<nodeHost1:nodePort1:Organization1> [<nodeHost2:nodePort2:Organization2> ...] [<timeout>] [-debug]`

The above commands run with the `-debug` flag enabled. To run without debug messages, just run the commands without `-debug`.

## Built With

* [Maven](https://maven.apache.org/) - Build and dependency management tool;
* [gRPC](https://grpc.io/) - RPC framework.
