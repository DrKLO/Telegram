#ifndef TGCALLS_SIGNALING_ENCRYPTION_H
#define TGCALLS_SIGNALING_ENCRYPTION_H

#include "Instance.h"
#include "EncryptedConnection.h"

namespace tgcalls {

class SignalingEncryption {
public:
    SignalingEncryption(EncryptionKey const &encryptionKey);
    ~SignalingEncryption();

    absl::optional<rtc::CopyOnWriteBuffer> encryptOutgoing(std::vector<uint8_t> const &data);
    absl::optional<rtc::CopyOnWriteBuffer> decryptIncoming(std::vector<uint8_t> const &data);

private:
    std::unique_ptr<EncryptedConnection> _connection;
};

} // namespace tgcalls

#endif
