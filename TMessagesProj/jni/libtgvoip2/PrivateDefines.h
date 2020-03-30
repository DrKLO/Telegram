//
// Created by Grishka on 20.04.2018.
//

#ifndef TGVOIP_PRIVATEDEFINES_H
#define TGVOIP_PRIVATEDEFINES_H

#define PKT_INIT 1
#define PKT_INIT_ACK 2
#define PKT_STREAM_STATE 3
#define PKT_STREAM_DATA 4
#define PKT_UPDATE_STREAMS 5
#define PKT_PING 6
#define PKT_PONG 7
#define PKT_STREAM_DATA_X2 8
#define PKT_STREAM_DATA_X3 9
#define PKT_LAN_ENDPOINT 10
#define PKT_NETWORK_CHANGED 11
#define PKT_SWITCH_PREF_RELAY 12
#define PKT_SWITCH_TO_P2P 13
#define PKT_NOP 14
//#define PKT_GROUP_CALL_KEY 15		// replaced with 'extra' in 2.1 (protocol v6)
//#define PKT_REQUEST_GROUP 16
#define PKT_STREAM_EC 17

#define IS_MOBILE_NETWORK(x) (x==NET_TYPE_GPRS || x==NET_TYPE_EDGE || x==NET_TYPE_3G || x==NET_TYPE_HSPA || x==NET_TYPE_LTE || x==NET_TYPE_OTHER_MOBILE)

#define PROTOCOL_NAME 0x50567247 // "GrVP" in little endian (reversed here)
#define PROTOCOL_VERSION 9
#define MIN_PROTOCOL_VERSION 3

#define STREAM_DATA_FLAG_LEN16 0x40
#define STREAM_DATA_FLAG_HAS_MORE_FLAGS 0x80
// Since the data can't be larger than the MTU anyway,
// 5 top bits of data length are allocated for these flags
#define STREAM_DATA_XFLAG_KEYFRAME (1 << 15)
#define STREAM_DATA_XFLAG_FRAGMENTED (1 << 14)
#define STREAM_DATA_XFLAG_EXTRA_FEC (1 << 13)

#define STREAM_TYPE_AUDIO 1
#define STREAM_TYPE_VIDEO 2

#define FOURCC(a,b,c,d) ((uint32_t)d | ((uint32_t)c << 8) | ((uint32_t)b << 16) | ((uint32_t)a << 24))
#define PRINT_FOURCC(x) (char)(x >> 24), (char)(x >> 16), (char)(x >> 8), (char)x

#define CODEC_OPUS_OLD 1
#define CODEC_OPUS FOURCC('O','P','U','S')

#define CODEC_AVC FOURCC('A','V','C',' ')
#define CODEC_HEVC FOURCC('H','E','V','C')
#define CODEC_VP8 FOURCC('V','P','8','0')
#define CODEC_VP9 FOURCC('V','P','9','0')
#define CODEC_AV1 FOURCC('A','V','0','1')

#define DEFAULT_MTU 1100

/*flags:# voice_call_id:flags.2?int128 in_seq_no:flags.4?int out_seq_no:flags.4?int
	 * recent_received_mask:flags.5?int proto:flags.3?int extra:flags.1?string raw_data:flags.0?string*/
#define PFLAG_HAS_DATA 1
#define PFLAG_HAS_EXTRA 2
#define PFLAG_HAS_CALL_ID 4
#define PFLAG_HAS_PROTO 8
#define PFLAG_HAS_SEQ 16
#define PFLAG_HAS_RECENT_RECV 32
#define PFLAG_HAS_SENDER_TAG_HASH 64

#define XPFLAG_HAS_EXTRA 1
#define XPFLAG_HAS_RECV_TS 2

#define EXTRA_TYPE_STREAM_FLAGS 1
#define EXTRA_TYPE_STREAM_CSD 2
#define EXTRA_TYPE_LAN_ENDPOINT 3
#define EXTRA_TYPE_NETWORK_CHANGED 4
#define EXTRA_TYPE_GROUP_CALL_KEY 5
#define EXTRA_TYPE_REQUEST_GROUP 6
#define EXTRA_TYPE_IPV6_ENDPOINT 7

#define STREAM_FLAG_ENABLED 1
#define STREAM_FLAG_DTX 2
#define STREAM_FLAG_EXTRA_EC 4
#define STREAM_FLAG_PAUSED 8

#define STREAM_RFLAG_SUPPORTED 1

#define INIT_FLAG_DATA_SAVING_ENABLED 1
#define INIT_FLAG_GROUP_CALLS_SUPPORTED 2
#define INIT_FLAG_VIDEO_SEND_SUPPORTED 4
#define INIT_FLAG_VIDEO_RECV_SUPPORTED 8

#define INIT_VIDEO_RES_NONE 0
#define INIT_VIDEO_RES_240 1
#define INIT_VIDEO_RES_360 2
#define INIT_VIDEO_RES_480 3
#define INIT_VIDEO_RES_720 4
#define INIT_VIDEO_RES_1080 5
#define INIT_VIDEO_RES_1440 6
#define INIT_VIDEO_RES_4K 7

#define TLID_DECRYPTED_AUDIO_BLOCK 0xDBF948C1
#define TLID_SIMPLE_AUDIO_BLOCK 0xCC0D0E76
#define TLID_UDP_REFLECTOR_PEER_INFO 0x27D9371C
#define TLID_UDP_REFLECTOR_PEER_INFO_IPV6 0x83fc73b1
#define TLID_UDP_REFLECTOR_SELF_INFO 0xc01572c7
#define TLID_UDP_REFLECTOR_REQUEST_PACKETS_INFO 0x1a06fc96
#define TLID_UDP_REFLECTOR_LAST_PACKETS_INFO 0x0e107305
#define TLID_VECTOR 0x1cb5c415
#define PAD4(x) (4-(x+(x<=253 ? 1 : 0))%4)

#define MAX_RECENT_PACKETS 128

#define SHA1_LENGTH 20
#define SHA256_LENGTH 32

#ifdef _MSC_VER
#define MSC_STACK_FALLBACK(a, b) (b)
#else
#define MSC_STACK_FALLBACK(a, b) (a)
#endif

#define SEQ_MAX 0xFFFFFFFF

inline bool seqgt(uint32_t s1, uint32_t s2){
	return ((s1>s2) && (s1-s2<=SEQ_MAX/2)) || ((s1<s2) && (s2-s1>SEQ_MAX/2));
}

#define NEED_RATE_FLAG_SHITTY_INTERNET_MODE 1
#define NEED_RATE_FLAG_UDP_NA 2
#define NEED_RATE_FLAG_UDP_BAD 4
#define NEED_RATE_FLAG_RECONNECTING 8

#define VIDEO_FRAME_FLAG_KEYFRAME 1

#define VIDEO_ROTATION_MASK 3
#define VIDEO_ROTATION_0 0
#define VIDEO_ROTATION_90 1
#define VIDEO_ROTATION_180 2
#define VIDEO_ROTATION_270 3

#define FEC_SCHEME_XOR 1
#define FEC_SCHEME_CM256 2

#endif //TGVOIP_PRIVATEDEFINES_H
