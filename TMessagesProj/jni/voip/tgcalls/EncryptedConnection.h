#ifndef TGCALLS_ENCRYPTED_CONNECTION_H
#define TGCALLS_ENCRYPTED_CONNECTION_H

#include "Instance.h"
#include "Message.h"

namespace rtc {
class ByteBufferReader;
} // namespace rtc

namespace tgcalls {

class EncryptedConnection final {
public:
    enum class Type : uint8_t {
        Signaling,
        Transport,
    };
    EncryptedConnection(
        Type type,
        const EncryptionKey &key,
        std::function<void(int delayMs, int cause)> requestSendService);

    struct EncryptedPacket {
        std::vector<uint8_t> bytes;
        uint32_t counter = 0;
    };
    absl::optional<EncryptedPacket> prepareForSending(const Message &message);
    absl::optional<EncryptedPacket> prepareForSendingService(int cause);

    struct DecryptedPacket {
        DecryptedMessage main;
        std::vector<DecryptedMessage> additional;
    };
    absl::optional<DecryptedPacket> handleIncomingPacket(const char *bytes, size_t size);

    absl::optional<rtc::CopyOnWriteBuffer> encryptRawPacket(rtc::CopyOnWriteBuffer const &buffer);
    absl::optional<rtc::CopyOnWriteBuffer> decryptRawPacket(rtc::CopyOnWriteBuffer const &buffer);

private:
    struct DelayIntervals {
        // In milliseconds.
        int minDelayBeforeMessageResend = 0;
        int maxDelayBeforeMessageResend = 0;
        int maxDelayBeforeAckResend = 0;
    };
    struct MessageForResend {
        rtc::CopyOnWriteBuffer data;
        int64_t lastSent = 0;
    };

    bool enoughSpaceInPacket(const rtc::CopyOnWriteBuffer &buffer, size_t amount) const;
    size_t packetLimit() const;
    size_t fullNotAckedLength() const;
    void appendAcksToSend(rtc::CopyOnWriteBuffer &buffer);
    void appendAdditionalMessages(rtc::CopyOnWriteBuffer &buffer);
    EncryptedPacket encryptPrepared(const rtc::CopyOnWriteBuffer &buffer);
    bool registerIncomingCounter(uint32_t incomingCounter);
    absl::optional<DecryptedPacket> processPacket(const rtc::Buffer &fullBuffer, uint32_t packetSeq);
    bool registerSentAck(uint32_t counter, bool firstInPacket);
    void ackMyMessage(uint32_t counter);
    void sendAckPostponed(uint32_t incomingSeq);
    bool haveAdditionalMessages() const;
    absl::optional<uint32_t> computeNextSeq(bool messageRequiresAck, bool singleMessagePacket);
    void appendReceivedMessage(
        absl::optional<DecryptedPacket> &to,
        Message &&message,
        uint32_t incomingSeq);

    const char *logHeader() const;

    static DelayIntervals DelayIntervalsByType(Type type);
    static rtc::CopyOnWriteBuffer SerializeEmptyMessageWithSeq(uint32_t seq);

    Type _type = Type();
    EncryptionKey _key;
    uint32_t _counter = 0;
    DelayIntervals _delayIntervals;
    std::vector<uint32_t> _largestIncomingCounters;
    std::vector<uint32_t> _ackedIncomingCounters;
    std::vector<uint32_t> _acksToSendSeqs;
    std::vector<uint32_t> _acksSentCounters;
    std::vector<MessageForResend> _myNotYetAckedMessages;
    std::function<void(int delayMs, int cause)> _requestSendService;
    bool _resendTimerActive = false;
    bool _sendAcksTimerActive = false;

};

} // namespace tgcalls

#endif
