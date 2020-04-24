#ifndef VOIPCONTROLLERDECLARATIONS_H
#define VOIPCONTROLLERDECLARATIONS_H

#include <cstdint>
#include <string>

#define TGVOIP_PEER_CAP_GROUP_CALLS             (1 << 0)
#define TGVOIP_PEER_CAP_VIDEO_CAPTURE           (1 << 1)
#define TGVOIP_PEER_CAP_VIDEO_DISPLAY           (1 << 2)

/* flags:# voice_call_id:flags.2?int128 in_seq_no:flags.4?int out_seq_no:flags.4?int
 * recent_received_mask:flags.5?int proto:flags.3?int extra:flags.1?string raw_data:flags.0?string
 */

#define PFLAG_HAS_DATA                          std::uint32_t{1 << 0}
#define PFLAG_HAS_EXTRA                         std::uint32_t{1 << 1}
#define PFLAG_HAS_CALL_ID                       std::uint32_t{1 << 2}
#define PFLAG_HAS_PROTO                         std::uint32_t{1 << 3}
#define PFLAG_HAS_SEQ                           std::uint32_t{1 << 4}
#define PFLAG_HAS_RECENT_RECV                   std::uint32_t{1 << 5}
#define PFLAG_HAS_SENDER_TAG_HASH               std::uint32_t{1 << 6}

#define XPFLAG_HAS_EXTRA                        std::uint8_t{1 << 0}
#define XPFLAG_HAS_RECV_TS                      std::uint8_t{1 << 1}

#define STREAM_FLAG_ENABLED                     std::uint8_t{1 << 0}
#define STREAM_FLAG_DTX                         std::uint8_t{1 << 1}
#define STREAM_FLAG_EXTRA_EC                    std::uint8_t{1 << 2}
#define STREAM_FLAG_PAUSED                      std::uint8_t{1 << 3}

#define STREAM_RFLAG_SUPPORTED                  1

#define INIT_FLAG_DATA_SAVING_ENABLED           std::uint8_t{1 << 0}
#define INIT_FLAG_GROUP_CALLS_SUPPORTED         std::uint8_t{1 << 1}
#define INIT_FLAG_VIDEO_SEND_SUPPORTED          std::uint8_t{1 << 2}
#define INIT_FLAG_VIDEO_RECV_SUPPORTED          std::uint8_t{1 << 3}

#define TLID_DECRYPTED_AUDIO_BLOCK              std::uint32_t{0xDBF948C1}
#define TLID_SIMPLE_AUDIO_BLOCK                 std::uint32_t{0xCC0D0E76}
#define TLID_UDP_REFLECTOR_PEER_INFO            std::uint32_t{0x27D9371C}
#define TLID_UDP_REFLECTOR_PEER_INFO_IPV6       std::uint32_t{0x83fc73b1}
#define TLID_UDP_REFLECTOR_SELF_INFO            std::uint32_t{0xc01572c7}
#define TLID_UDP_REFLECTOR_REQUEST_PACKETS_INFO std::uint32_t{0x1a06fc96}
#define TLID_UDP_REFLECTOR_LAST_PACKETS_INFO    std::uint32_t{0x0e107305}
#define TLID_VECTOR                             std::uint32_t{0x1cb5c415}

#define NEED_RATE_FLAG_SHITTY_INTERNET_MODE     std::uint8_t{1 << 0}
#define NEED_RATE_FLAG_UDP_NA                   std::uint8_t{1 << 1}
#define NEED_RATE_FLAG_UDP_BAD                  std::uint8_t{1 << 2}
#define NEED_RATE_FLAG_RECONNECTING             std::uint8_t{1 << 3}

namespace tgvoip
{

enum class Proxy
{
    NONE = 0,
    SOCKS5,
    //HTTP,
};

enum class State
{
    WAIT_INIT = 1,
    WAIT_INIT_ACK,
    ESTABLISHED,
    FAILED,
    RECONNECTING,
};

enum class Error
{
    UNKNOWN = 0,
    INCOMPATIBLE,
    TIMEOUT,
    AUDIO_IO,
    PROXY,
};

enum class PktType : std::uint8_t
{
    INIT = 1,
    INIT_ACK,
    STREAM_STATE,
    STREAM_DATA,
    UPDATE_STREAMS,
    PING,
    PONG,
    STREAM_DATA_X2,
    STREAM_DATA_X3,
    LAN_ENDPOINT,
    NETWORK_CHANGED,
    SWITCH_PREF_RELAY,
    SWITCH_TO_P2P,
    NOP,
//    GROUP_CALL_KEY,		// replaced with 'extra' in 2.1 (protocol v6)
//    REQUEST_GROUP,
    STREAM_EC,
};

enum class ExtraType : std::uint8_t
{
    STREAM_FLAGS = 1,
    STREAM_CSD,
    LAN_ENDPOINT,
    NETWORK_CHANGED,
    GROUP_CALL_KEY,
    REQUEST_GROUP,
    IPV6_ENDPOINT,
};

enum class StreamType : std::uint8_t
{
    AUDIO = 1,
    VIDEO,
};

enum class NetType
{
    UNKNOWN = 0,
    GPRS,
    EDGE,
    THREE_G,
    HSPA,
    LTE,
    WIFI,
    ETHERNET,
    OTHER_HIGH_SPEED,
    OTHER_LOW_SPEED,
    DIALUP,
    OTHER_MOBILE,
};

enum class DataSaving
{
    NEVER = 0,
    MOBILE,
    ALWAYS,
};

enum class InitVideoRes : std::uint8_t
{
    NONE = 0,
    _240,
    _360,
    _480,
    _720,
    _1080,
    _1440,
    _4K,
};

enum class VideoRotation : std::uint8_t
{
    _0 = 0,
    _90,
    _180,
    _270,
};
#define VIDEO_ROTATION_MASK 3

struct CryptoFunctions
{
    void (*rand_bytes)(std::uint8_t* buffer, std::size_t length);
    void (*sha1)(const std::uint8_t* msg, std::size_t length, std::uint8_t* output);
    void (*sha256)(const std::uint8_t* msg, std::size_t length, std::uint8_t* output);
    void (*aes_ige_encrypt)(const std::uint8_t* in, std::uint8_t* out, std::size_t length, const std::uint8_t* key, std::uint8_t* iv);
    void (*aes_ige_decrypt)(const std::uint8_t* in, std::uint8_t* out, std::size_t length, const std::uint8_t* key, std::uint8_t* iv);
    void (*aes_ctr_encrypt)(std::uint8_t* inout, std::size_t length, const std::uint8_t* key, std::uint8_t* iv, std::uint8_t* ecount, std::uint32_t* num);
    void (*aes_cbc_encrypt)(const std::uint8_t* in, std::uint8_t* out, std::size_t length, const std::uint8_t* key, std::uint8_t* iv);
    void (*aes_cbc_decrypt)(const std::uint8_t* in, std::uint8_t* out, std::size_t length, const std::uint8_t* key, std::uint8_t* iv);
};

struct CellularCarrierInfo
{
    std::string name;
    std::string mcc;
    std::string mnc;
    std::string countryCode;
};

class PacketSender;

namespace video
{

class VideoPacketSender;

} // namespace video

} // namespace tgvoip

#endif // VOIPCONTROLLERDECLARATIONS_H
