#include "v2/ReflectorRelayPortFactory.h"

#include "p2p/base/turn_port.h"

#include "v2/ReflectorPort.h"

namespace tgcalls {

ReflectorRelayPortFactory::ReflectorRelayPortFactory(std::vector<RtcServer> servers) :
_servers(servers) {
}

ReflectorRelayPortFactory::~ReflectorRelayPortFactory() {
}

std::unique_ptr<cricket::Port> ReflectorRelayPortFactory::Create(const cricket::CreateRelayPortArgs& args, rtc::AsyncPacketSocket* udp_socket) {
    if (args.config->credentials.username == "reflector") {
        uint8_t id = 0;
        for (const auto &server : _servers) {
            rtc::SocketAddress serverAddress(server.host, server.port);
            if (args.server_address->address == serverAddress) {
                id = server.id;
                break;
            }
        }

        if (id == 0) {
            return nullptr;
        }

        auto port = ReflectorPort::Create(args, udp_socket, id);
        if (!port) {
            return nullptr;
        }
        return port;
    } else {
        auto port = cricket::TurnPort::Create(args, udp_socket);
        if (!port) {
            return nullptr;
        }
        port->SetTlsCertPolicy(args.config->tls_cert_policy);
        port->SetTurnLoggingId(args.config->turn_logging_id);
        return port;
    }
}

std::unique_ptr<cricket::Port> ReflectorRelayPortFactory::Create(const cricket::CreateRelayPortArgs& args, int min_port, int max_port) {
    if (args.config->credentials.username == "reflector") {
        uint8_t id = 0;
        for (const auto &server : _servers) {
            rtc::SocketAddress serverAddress(server.host, server.port);
            if (args.server_address->address == serverAddress) {
                id = server.id;
                break;
            }
        }

        if (id == 0) {
            return nullptr;
        }

        auto port = ReflectorPort::Create(args, min_port, max_port, id);
        if (!port) {
            return nullptr;
        }
        return port;
    } else {
        auto port = cricket::TurnPort::Create(args, min_port, max_port);
        if (!port) {
            return nullptr;
        }
        port->SetTlsCertPolicy(args.config->tls_cert_policy);
        port->SetTurnLoggingId(args.config->turn_logging_id);
        return port;
    }
}

} // namespace tgcalls
