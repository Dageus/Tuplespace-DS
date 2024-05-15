import sys
sys.path.insert(1, '../Contract/target/generated-sources/protobuf/python')

import NameServer_pb2
import NameServer_pb2_grpc

class NamingServerServiceImpl(NameServer_pb2_grpc.NameServerServicer):
    def __init__(self, naming_server):
        self.naming_server = naming_server

    def qualifier_is_valid(self, qualifier):
        return qualifier in ("A", "B", "C")

    def register(self, request, context):
        try:
            new_server = self.naming_server.register_server(request.serviceName, request.address, request.qualifier)
            print("Registered '" + new_server.qualifier + ", " + new_server.address)
            return NameServer_pb2.RegisterResponse()
        except Exception as e:
            print("Register Exception: " + str(e))
            return NameServer_pb2.RegisterResponse()
    
    def lookup(self, request, context):
        try:
            server_entries = []
            service_entry = self.naming_server.get_service(request.serviceName)
            if service_entry is not None and request.qualifier == "":
                server_entries.extend(service_entry.get_all_servers())
            elif service_entry is not None and self.qualifier_is_valid(request.qualifier):
                server_entry = service_entry.get_server(request.qualifier)
                if server_entry is not None:
                    server_entries.append(server_entry)
            print("Lookup: " + str(server_entries))
            response = NameServer_pb2.LookupResponse()
            response.servers.extend(server_entries)
            return response
        except Exception as e:
            print("Lookup Exception: " + str(e))
            return NameServer_pb2.LookupResponse()
        
    def delete(self, request, context):
        try:
            service_entry = self.naming_server.get_service(request.serviceName)
            if service_entry is None:
                return NameServer_pb2.DeleteResponse()
            service_entry.remove_server(request.address)
            print("Deleted: " + request.address)
            return NameServer_pb2.DeleteResponse()
        except Exception as e:
            print("Delete Exception: " + str(e))
            return NameServer_pb2.DeleteResponse()

    def registerClient(self, request, context):
        try:
            response = NameServer_pb2.RegisterClientResponse()
            response.id = self.naming_server.register_client()
            print("Register client: "+ str(response.id))
            return response
        except Exception as e:
            response = NameServer_pb2.RegisterClientResponse()
            response.id = -1
            print("Register client Exception: " + str(e))
            return NameServer_pb2.RegisterClientResponse()
