#ifndef TGCALLS_DIRECT_CONNECTION_CHANNEL_H
#define TGCALLS_DIRECT_CONNECTION_CHANNEL_H

#include <functional>
#include <memory>
#include <vector>

namespace tgcalls {

class DirectConnectionChannel {
public:
    virtual ~DirectConnectionChannel() = default;

    virtual std::vector<uint8_t> addOnIncomingPacket(std::function<void(std::shared_ptr<std::vector<uint8_t>>)> &&) = 0;
    virtual void removeOnIncomingPacket(std::vector<uint8_t> &token) = 0;
    virtual void sendPacket(std::unique_ptr<std::vector<uint8_t>> &&packet) = 0;
};

} // namespace tgcalls

#endif
