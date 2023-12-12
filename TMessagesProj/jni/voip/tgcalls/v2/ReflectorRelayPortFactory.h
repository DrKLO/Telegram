#ifndef TGCALLS_REFLECTOR_RELAY_PORT_FACTORY_H
#define TGCALLS_REFLECTOR_RELAY_PORT_FACTORY_H

#include "p2p/client/relay_port_factory_interface.h"

#include "Instance.h"

namespace tgcalls {

class ReflectorRelayPortFactory : public cricket::RelayPortFactoryInterface {
public:
    ReflectorRelayPortFactory(std::vector<RtcServer> servers);
    ~ReflectorRelayPortFactory() override;
    
    // This variant is used for UDP connection to the relay server
    // using a already existing shared socket.
    virtual std::unique_ptr<cricket::Port> Create(const cricket::CreateRelayPortArgs& args, rtc::AsyncPacketSocket* udp_socket) override;
    
    // This variant is used for the other cases.
    virtual std::unique_ptr<cricket::Port> Create(const cricket::CreateRelayPortArgs& args, int min_port, int max_port) override;
    
private:
    std::vector<RtcServer> _servers;
};

} // namespace tgcalls

#endif
