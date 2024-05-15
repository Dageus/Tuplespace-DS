import sys
import grpc
from concurrent import futures
sys.path.insert(1, '../Contract/target/generated-sources/protobuf/python')

import NameServer_pb2
import NameServer_pb2_grpc
import threading

import serverImpl

# define the port
PORT = 5001


class ServerEntry:
    def __init__(self, address, qualifier):
        self.address = address
        self.qualifier = qualifier

class ServiceEntry:
    def __init__(self, service_name):
        self.service_name = service_name
        self.servers = []

    def add_server(self, server_entry):
        self.servers.append(server_entry)

    def remove_server(self, address):
        for server in self.servers:
            if server.address == address:
                self.servers.remove(server)
                return True
        return False

    def get_server(self, qualifier):
        for sv in self.servers:
            if qualifier == sv.qualifier:
                return sv.address

    def get_all_servers(self):
        servers = []
        for sv in self.servers:
            servers.append(sv.address)
        return servers

class NamingServer:
    def __init__(self):
        self.services = []
        self.client_id_counter = 0
        self.client_id_lock = threading.Lock()

    def register_server(self, service_name, address, qualifier):
        server_entry = ServerEntry(address, qualifier)
        for service in self.services:
            if service.service_name == service_name:
                service.add_server(server_entry)
                return server_entry
        service_entry = ServiceEntry(service_name)
        service_entry.add_server(server_entry)
        self.services.append(service_entry)
        return server_entry

    def get_service(self, service_name):
        for service in self.services:
            if service.service_name == service_name:
                return service
        return None

    def register_client(self):
        with self.client_id_lock:
            self.client_id_counter += 1
            return self.client_id_counter

if __name__ == '__main__':
    try:

        print("NameServer started")
        
        server = grpc.server(futures.ThreadPoolExecutor(max_workers=1))

        nserver = NamingServer()

        NameServer_pb2_grpc.add_NameServerServicer_to_server(serverImpl.NamingServerServiceImpl(nserver), server)


        server.add_insecure_port('127.0.0.1:' + str(PORT))
        server.start()
        print("NameServer listening on port " + str(PORT))
        
        server.wait_for_termination()

    except KeyboardInterrupt:
        print("NameServer stopped")
        exit(0)
