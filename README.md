# TupleSpaces

Distributed Systems Project 2024

### Team Members

*(fill the table below with the team members, and then delete this line)*

| Number | Name                   | User                                 | Email                                              |
|--------|------------------------|--------------------------------------|----------------------------------------------------|
| 1103808  | João Miguel Nogueira | <https://github.com/Dageus>          | <mailto:joao.miguel.nogueira@tecnico.ulisboa.pt>   |
| 1103465  | João Rocha           | <https://github.com/jonhspyro>       | <mailto:joaonolascorocha@tecnico.ulisboa.pt>       |
| 1103809  | Rodrigo Ganância     | <https://github.com/RodrigoGanancia> | <mailto:rodrigo.ganancia@tecnico.ulisboa.pt>       |

## Getting Started

The overall system is made up of several modules. The different types of servers are located in _ServerX_ (where X denotes stage 1, 2 or 3). 
The clients is in _Client_.
The definition of messages and services is in _Contract_. The future naming server
is in _NamingServer_.

See the [Project Statement](https://github.com/tecnico-distsys/TupleSpaces) for a complete domain and system description.

### Prerequisites

The Project is configured with Java 17 (which is only compatible with Maven >= 3.8), but if you want to use Java 11 you
can too -- just downgrade the version in the POMs.

To confirm that you have them installed and which versions they are, run in the terminal:

```s
javac -version
mvn -version
```

Before executing:

Create a virtual environment and activate it:

```s
python -m venv .venv
source .venv/bin/activate
```

Install grpcio and grpcio-tools packages:

```s
python -m pip install grpcio
python -m pip install grpcio-tools
```

If you wish to deactivate the virtual environment, run in terminal:

```S
deactivate
```

## Installation and compilation

To compile and install all modules:

### Contract:


```s
mvn clean install
mvn exec:exec
```

### NameServer:

```s
python server.py
```
### Sequencer:

```s
mvn compile
```

### Server:

```s
mvn compile
```

### Client:

```s
mvn compile
```


## How to run project after compiling

### NameServer Directory

```s
python server.py
```
### Sequencer Directory
```s
mvn exec:java
```

### Server Directory
```s
mvn exec:java -Dexec.args="<host> <port> <qualificador> <serivco>" [-Ddebug]
```
#### Example:
Terminal 1:
```s
mvn exec:java -Dexec.args="localhost 2001 A TupleSpacesTotalOrder"
```
Terminal 2:
```s
mvn exec:java -Dexec.args="localhost 2002 B TupleSpacesTotalOrder"
```
Terminal 3:
```s
mvn exec:java -Dexec.args="localhost 2003 C TupleSpacesXuTotalOrder"
```

### Client directory
The Name Server automatically provides the client's ID.
```s
 mvn exec:java -Dexec.args="<NS_host> <NS_port> <qualificador> <service>" [-Debug]
``` 
#### Example:
```s
mvn exec:java
```

## Run extra tests
Extra tests for synchronization purposes in sync_tests directory
where multiple clients try requesting at the same time

#### 1. Execute Name Server
#### 2. Execute Servers
#### 3. Run in 'tests/TotalOrder' directory the following command


```s
./run_tests.sh
```


## Built With

* [Maven](https://maven.apache.org/) - Build and dependency management tool;
* [gRPC](https://grpc.io/) - RPC framework.
