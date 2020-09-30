/*
 *  Copyright 2004 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef PC_SESSION_DESCRIPTION_H_
#define PC_SESSION_DESCRIPTION_H_

#include <stddef.h>
#include <stdint.h>

#include <iosfwd>
#include <memory>
#include <string>
#include <utility>
#include <vector>

#include "absl/memory/memory.h"
#include "api/crypto_params.h"
#include "api/media_types.h"
#include "api/rtp_parameters.h"
#include "api/rtp_transceiver_interface.h"
#include "media/base/media_channel.h"
#include "media/base/media_constants.h"
#include "media/base/stream_params.h"
#include "p2p/base/transport_description.h"
#include "p2p/base/transport_info.h"
#include "pc/media_protocol_names.h"
#include "pc/simulcast_description.h"
#include "rtc_base/deprecation.h"
#include "rtc_base/socket_address.h"
#include "rtc_base/system/rtc_export.h"

namespace cricket {

typedef std::vector<AudioCodec> AudioCodecs;
typedef std::vector<VideoCodec> VideoCodecs;
typedef std::vector<RtpDataCodec> RtpDataCodecs;
typedef std::vector<CryptoParams> CryptoParamsVec;
typedef std::vector<webrtc::RtpExtension> RtpHeaderExtensions;

// RTC4585 RTP/AVPF
extern const char kMediaProtocolAvpf[];
// RFC5124 RTP/SAVPF
extern const char kMediaProtocolSavpf[];

extern const char kMediaProtocolDtlsSavpf[];

// Options to control how session descriptions are generated.
const int kAutoBandwidth = -1;

class AudioContentDescription;
class VideoContentDescription;
class RtpDataContentDescription;
class SctpDataContentDescription;

// Describes a session description media section. There are subclasses for each
// media type (audio, video, data) that will have additional information.
class MediaContentDescription {
 public:
  MediaContentDescription() = default;
  virtual ~MediaContentDescription() = default;

  virtual MediaType type() const = 0;

  // Try to cast this media description to an AudioContentDescription. Returns
  // nullptr if the cast fails.
  virtual AudioContentDescription* as_audio() { return nullptr; }
  virtual const AudioContentDescription* as_audio() const { return nullptr; }

  // Try to cast this media description to a VideoContentDescription. Returns
  // nullptr if the cast fails.
  virtual VideoContentDescription* as_video() { return nullptr; }
  virtual const VideoContentDescription* as_video() const { return nullptr; }

  virtual RtpDataContentDescription* as_rtp_data() { return nullptr; }
  virtual const RtpDataContentDescription* as_rtp_data() const {
    return nullptr;
  }

  virtual SctpDataContentDescription* as_sctp() { return nullptr; }
  virtual const SctpDataContentDescription* as_sctp() const { return nullptr; }

  virtual bool has_codecs() const = 0;

  // Copy operator that returns an unique_ptr.
  // Not a virtual function.
  // If a type-specific variant of Clone() is desired, override it, or
  // simply use std::make_unique<typename>(*this) instead of Clone().
  std::unique_ptr<MediaContentDescription> Clone() const {
    return absl::WrapUnique(CloneInternal());
  }

  // |protocol| is the expected media transport protocol, such as RTP/AVPF,
  // RTP/SAVPF or SCTP/DTLS.
  virtual std::string protocol() const { return protocol_; }
  virtual void set_protocol(const std::string& protocol) {
    protocol_ = protocol;
  }

  virtual webrtc::RtpTransceiverDirection direction() const {
    return direction_;
  }
  virtual void set_direction(webrtc::RtpTransceiverDirection direction) {
    direction_ = direction;
  }

  virtual bool rtcp_mux() const { return rtcp_mux_; }
  virtual void set_rtcp_mux(bool mux) { rtcp_mux_ = mux; }

  virtual bool rtcp_reduced_size() const { return rtcp_reduced_size_; }
  virtual void set_rtcp_reduced_size(bool reduced_size) {
    rtcp_reduced_size_ = reduced_size;
  }

  // Indicates support for the remote network estimate packet type. This
  // functionality is experimental and subject to change without notice.
  virtual bool remote_estimate() const { return remote_estimate_; }
  virtual void set_remote_estimate(bool remote_estimate) {
    remote_estimate_ = remote_estimate;
  }

  virtual int bandwidth() const { return bandwidth_; }
  virtual void set_bandwidth(int bandwidth) { bandwidth_ = bandwidth; }
  virtual std::string bandwidth_type() const { return bandwidth_type_; }
  virtual void set_bandwidth_type(std::string bandwidth_type) {
    bandwidth_type_ = bandwidth_type;
  }

  virtual const std::vector<CryptoParams>& cryptos() const { return cryptos_; }
  virtual void AddCrypto(const CryptoParams& params) {
    cryptos_.push_back(params);
  }
  virtual void set_cryptos(const std::vector<CryptoParams>& cryptos) {
    cryptos_ = cryptos;
  }

  virtual const RtpHeaderExtensions& rtp_header_extensions() const {
    return rtp_header_extensions_;
  }
  virtual void set_rtp_header_extensions(
      const RtpHeaderExtensions& extensions) {
    rtp_header_extensions_ = extensions;
    rtp_header_extensions_set_ = true;
  }
  virtual void AddRtpHeaderExtension(const webrtc::RtpExtension& ext) {
    rtp_header_extensions_.push_back(ext);
    rtp_header_extensions_set_ = true;
  }
  virtual void ClearRtpHeaderExtensions() {
    rtp_header_extensions_.clear();
    rtp_header_extensions_set_ = true;
  }
  // We can't always tell if an empty list of header extensions is
  // because the other side doesn't support them, or just isn't hooked up to
  // signal them. For now we assume an empty list means no signaling, but
  // provide the ClearRtpHeaderExtensions method to allow "no support" to be
  // clearly indicated (i.e. when derived from other information).
  virtual bool rtp_header_extensions_set() const {
    return rtp_header_extensions_set_;
  }
  virtual const StreamParamsVec& streams() const { return send_streams_; }
  // TODO(pthatcher): Remove this by giving mediamessage.cc access
  // to MediaContentDescription
  virtual StreamParamsVec& mutable_streams() { return send_streams_; }
  virtual void AddStream(const StreamParams& stream) {
    send_streams_.push_back(stream);
  }
  // Legacy streams have an ssrc, but nothing else.
  void AddLegacyStream(uint32_t ssrc) {
    AddStream(StreamParams::CreateLegacy(ssrc));
  }
  void AddLegacyStream(uint32_t ssrc, uint32_t fid_ssrc) {
    StreamParams sp = StreamParams::CreateLegacy(ssrc);
    sp.AddFidSsrc(ssrc, fid_ssrc);
    AddStream(sp);
  }

  // Sets the CNAME of all StreamParams if it have not been set.
  virtual void SetCnameIfEmpty(const std::string& cname) {
    for (cricket::StreamParamsVec::iterator it = send_streams_.begin();
         it != send_streams_.end(); ++it) {
      if (it->cname.empty())
        it->cname = cname;
    }
  }
  virtual uint32_t first_ssrc() const {
    if (send_streams_.empty()) {
      return 0;
    }
    return send_streams_[0].first_ssrc();
  }
  virtual bool has_ssrcs() const {
    if (send_streams_.empty()) {
      return false;
    }
    return send_streams_[0].has_ssrcs();
  }

  virtual void set_conference_mode(bool enable) { conference_mode_ = enable; }
  virtual bool conference_mode() const { return conference_mode_; }

  // https://tools.ietf.org/html/rfc4566#section-5.7
  // May be present at the media or session level of SDP. If present at both
  // levels, the media-level attribute overwrites the session-level one.
  virtual void set_connection_address(const rtc::SocketAddress& address) {
    connection_address_ = address;
  }
  virtual const rtc::SocketAddress& connection_address() const {
    return connection_address_;
  }

  // Determines if it's allowed to mix one- and two-byte rtp header extensions
  // within the same rtp stream.
  enum ExtmapAllowMixed { kNo, kSession, kMedia };
  virtual void set_extmap_allow_mixed_enum(
      ExtmapAllowMixed new_extmap_allow_mixed) {
    if (new_extmap_allow_mixed == kMedia &&
        extmap_allow_mixed_enum_ == kSession) {
      // Do not downgrade from session level to media level.
      return;
    }
    extmap_allow_mixed_enum_ = new_extmap_allow_mixed;
  }
  virtual ExtmapAllowMixed extmap_allow_mixed_enum() const {
    return extmap_allow_mixed_enum_;
  }
  virtual bool extmap_allow_mixed() const {
    return extmap_allow_mixed_enum_ != kNo;
  }

  // Simulcast functionality.
  virtual bool HasSimulcast() const { return !simulcast_.empty(); }
  virtual SimulcastDescription& simulcast_description() { return simulcast_; }
  virtual const SimulcastDescription& simulcast_description() const {
    return simulcast_;
  }
  virtual void set_simulcast_description(
      const SimulcastDescription& simulcast) {
    simulcast_ = simulcast;
  }
  virtual const std::vector<RidDescription>& receive_rids() const {
    return receive_rids_;
  }
  virtual void set_receive_rids(const std::vector<RidDescription>& rids) {
    receive_rids_ = rids;
  }

 protected:
  bool rtcp_mux_ = false;
  bool rtcp_reduced_size_ = false;
  bool remote_estimate_ = false;
  int bandwidth_ = kAutoBandwidth;
  std::string bandwidth_type_ = kApplicationSpecificBandwidth;
  std::string protocol_;
  std::vector<CryptoParams> cryptos_;
  std::vector<webrtc::RtpExtension> rtp_header_extensions_;
  bool rtp_header_extensions_set_ = false;
  StreamParamsVec send_streams_;
  bool conference_mode_ = false;
  webrtc::RtpTransceiverDirection direction_ =
      webrtc::RtpTransceiverDirection::kSendRecv;
  rtc::SocketAddress connection_address_;
  // Mixed one- and two-byte header not included in offer on media level or
  // session level, but we will respond that we support it. The plan is to add
  // it to our offer on session level. See todo in SessionDescription.
  ExtmapAllowMixed extmap_allow_mixed_enum_ = kNo;

  SimulcastDescription simulcast_;
  std::vector<RidDescription> receive_rids_;

 private:
  // Copy function that returns a raw pointer. Caller will assert ownership.
  // Should only be called by the Clone() function. Must be implemented
  // by each final subclass.
  virtual MediaContentDescription* CloneInternal() const = 0;
};

template <class C>
class MediaContentDescriptionImpl : public MediaContentDescription {
 public:
  void set_protocol(const std::string& protocol) override {
    RTC_DCHECK(IsRtpProtocol(protocol));
    protocol_ = protocol;
  }

  typedef C CodecType;

  // Codecs should be in preference order (most preferred codec first).
  virtual const std::vector<C>& codecs() const { return codecs_; }
  virtual void set_codecs(const std::vector<C>& codecs) { codecs_ = codecs; }
  bool has_codecs() const override { return !codecs_.empty(); }
  virtual bool HasCodec(int id) {
    bool found = false;
    for (typename std::vector<C>::iterator iter = codecs_.begin();
         iter != codecs_.end(); ++iter) {
      if (iter->id == id) {
        found = true;
        break;
      }
    }
    return found;
  }
  virtual void AddCodec(const C& codec) { codecs_.push_back(codec); }
  virtual void AddOrReplaceCodec(const C& codec) {
    for (typename std::vector<C>::iterator iter = codecs_.begin();
         iter != codecs_.end(); ++iter) {
      if (iter->id == codec.id) {
        *iter = codec;
        return;
      }
    }
    AddCodec(codec);
  }
  virtual void AddCodecs(const std::vector<C>& codecs) {
    typename std::vector<C>::const_iterator codec;
    for (codec = codecs.begin(); codec != codecs.end(); ++codec) {
      AddCodec(*codec);
    }
  }

 private:
  std::vector<C> codecs_;
};

class AudioContentDescription : public MediaContentDescriptionImpl<AudioCodec> {
 public:
  AudioContentDescription() {}

  virtual MediaType type() const { return MEDIA_TYPE_AUDIO; }
  virtual AudioContentDescription* as_audio() { return this; }
  virtual const AudioContentDescription* as_audio() const { return this; }

 private:
  virtual AudioContentDescription* CloneInternal() const {
    return new AudioContentDescription(*this);
  }
};

class VideoContentDescription : public MediaContentDescriptionImpl<VideoCodec> {
 public:
  virtual MediaType type() const { return MEDIA_TYPE_VIDEO; }
  virtual VideoContentDescription* as_video() { return this; }
  virtual const VideoContentDescription* as_video() const { return this; }

 private:
  virtual VideoContentDescription* CloneInternal() const {
    return new VideoContentDescription(*this);
  }
};

class RtpDataContentDescription
    : public MediaContentDescriptionImpl<RtpDataCodec> {
 public:
  RtpDataContentDescription() {}
  MediaType type() const override { return MEDIA_TYPE_DATA; }
  RtpDataContentDescription* as_rtp_data() override { return this; }
  const RtpDataContentDescription* as_rtp_data() const override { return this; }

 private:
  RtpDataContentDescription* CloneInternal() const override {
    return new RtpDataContentDescription(*this);
  }
};

class SctpDataContentDescription : public MediaContentDescription {
 public:
  SctpDataContentDescription() {}
  SctpDataContentDescription(const SctpDataContentDescription& o)
      : MediaContentDescription(o),
        use_sctpmap_(o.use_sctpmap_),
        port_(o.port_),
        max_message_size_(o.max_message_size_) {}
  MediaType type() const override { return MEDIA_TYPE_DATA; }
  SctpDataContentDescription* as_sctp() override { return this; }
  const SctpDataContentDescription* as_sctp() const override { return this; }

  bool has_codecs() const override { return false; }
  void set_protocol(const std::string& protocol) override {
    RTC_DCHECK(IsSctpProtocol(protocol));
    protocol_ = protocol;
  }

  bool use_sctpmap() const { return use_sctpmap_; }
  void set_use_sctpmap(bool enable) { use_sctpmap_ = enable; }
  int port() const { return port_; }
  void set_port(int port) { port_ = port; }
  int max_message_size() const { return max_message_size_; }
  void set_max_message_size(int max_message_size) {
    max_message_size_ = max_message_size;
  }

 private:
  SctpDataContentDescription* CloneInternal() const override {
    return new SctpDataContentDescription(*this);
  }
  bool use_sctpmap_ = true;  // Note: "true" is no longer conformant.
  // Defaults should be constants imported from SCTP. Quick hack.
  int port_ = 5000;
  // draft-ietf-mmusic-sdp-sctp-23: Max message size default is 64K
  int max_message_size_ = 64 * 1024;
};

// Protocol used for encoding media. This is the "top level" protocol that may
// be wrapped by zero or many transport protocols (UDP, ICE, etc.).
enum class MediaProtocolType {
  kRtp,  // Section will use the RTP protocol (e.g., for audio or video).
         // https://tools.ietf.org/html/rfc3550
  kSctp  // Section will use the SCTP protocol (e.g., for a data channel).
         // https://tools.ietf.org/html/rfc4960
};

// Represents a session description section. Most information about the section
// is stored in the description, which is a subclass of MediaContentDescription.
// Owns the description.
class RTC_EXPORT ContentInfo {
 public:
  explicit ContentInfo(MediaProtocolType type) : type(type) {}
  ~ContentInfo();
  // Copy
  ContentInfo(const ContentInfo& o);
  ContentInfo& operator=(const ContentInfo& o);
  ContentInfo(ContentInfo&& o) = default;
  ContentInfo& operator=(ContentInfo&& o) = default;

  // Alias for |name|.
  std::string mid() const { return name; }
  void set_mid(const std::string& mid) { this->name = mid; }

  // Alias for |description|.
  MediaContentDescription* media_description();
  const MediaContentDescription* media_description() const;

  void set_media_description(std::unique_ptr<MediaContentDescription> desc) {
    description_ = std::move(desc);
  }

  // TODO(bugs.webrtc.org/8620): Rename this to mid.
  std::string name;
  MediaProtocolType type;
  bool rejected = false;
  bool bundle_only = false;

 private:
  friend class SessionDescription;
  std::unique_ptr<MediaContentDescription> description_;
};

typedef std::vector<std::string> ContentNames;

// This class provides a mechanism to aggregate different media contents into a
// group. This group can also be shared with the peers in a pre-defined format.
// GroupInfo should be populated only with the |content_name| of the
// MediaDescription.
class ContentGroup {
 public:
  explicit ContentGroup(const std::string& semantics);
  ContentGroup(const ContentGroup&);
  ContentGroup(ContentGroup&&);
  ContentGroup& operator=(const ContentGroup&);
  ContentGroup& operator=(ContentGroup&&);
  ~ContentGroup();

  const std::string& semantics() const { return semantics_; }
  const ContentNames& content_names() const { return content_names_; }

  const std::string* FirstContentName() const;
  bool HasContentName(const std::string& content_name) const;
  void AddContentName(const std::string& content_name);
  bool RemoveContentName(const std::string& content_name);

 private:
  std::string semantics_;
  ContentNames content_names_;
};

typedef std::vector<ContentInfo> ContentInfos;
typedef std::vector<ContentGroup> ContentGroups;

const ContentInfo* FindContentInfoByName(const ContentInfos& contents,
                                         const std::string& name);
const ContentInfo* FindContentInfoByType(const ContentInfos& contents,
                                         const std::string& type);

// Determines how the MSID will be signaled in the SDP. These can be used as
// flags to indicate both or none.
enum MsidSignaling {
  // Signal MSID with one a=msid line in the media section.
  kMsidSignalingMediaSection = 0x1,
  // Signal MSID with a=ssrc: msid lines in the media section.
  kMsidSignalingSsrcAttribute = 0x2
};

// Describes a collection of contents, each with its own name and
// type.  Analogous to a <jingle> or <session> stanza.  Assumes that
// contents are unique be name, but doesn't enforce that.
class SessionDescription {
 public:
  SessionDescription();
  ~SessionDescription();

  std::unique_ptr<SessionDescription> Clone() const;

  // Content accessors.
  const ContentInfos& contents() const { return contents_; }
  ContentInfos& contents() { return contents_; }
  const ContentInfo* GetContentByName(const std::string& name) const;
  ContentInfo* GetContentByName(const std::string& name);
  const MediaContentDescription* GetContentDescriptionByName(
      const std::string& name) const;
  MediaContentDescription* GetContentDescriptionByName(const std::string& name);
  const ContentInfo* FirstContentByType(MediaProtocolType type) const;
  const ContentInfo* FirstContent() const;

  // Content mutators.
  // Adds a content to this description. Takes ownership of ContentDescription*.
  void AddContent(const std::string& name,
                  MediaProtocolType type,
                  std::unique_ptr<MediaContentDescription> description);
  void AddContent(const std::string& name,
                  MediaProtocolType type,
                  bool rejected,
                  std::unique_ptr<MediaContentDescription> description);
  void AddContent(const std::string& name,
                  MediaProtocolType type,
                  bool rejected,
                  bool bundle_only,
                  std::unique_ptr<MediaContentDescription> description);
  void AddContent(ContentInfo&& content);

  bool RemoveContentByName(const std::string& name);

  // Transport accessors.
  const TransportInfos& transport_infos() const { return transport_infos_; }
  TransportInfos& transport_infos() { return transport_infos_; }
  const TransportInfo* GetTransportInfoByName(const std::string& name) const;
  TransportInfo* GetTransportInfoByName(const std::string& name);
  const TransportDescription* GetTransportDescriptionByName(
      const std::string& name) const {
    const TransportInfo* tinfo = GetTransportInfoByName(name);
    return tinfo ? &tinfo->description : NULL;
  }

  // Transport mutators.
  void set_transport_infos(const TransportInfos& transport_infos) {
    transport_infos_ = transport_infos;
  }
  // Adds a TransportInfo to this description.
  void AddTransportInfo(const TransportInfo& transport_info);
  bool RemoveTransportInfoByName(const std::string& name);

  // Group accessors.
  const ContentGroups& groups() const { return content_groups_; }
  const ContentGroup* GetGroupByName(const std::string& name) const;
  bool HasGroup(const std::string& name) const;

  // Group mutators.
  void AddGroup(const ContentGroup& group) { content_groups_.push_back(group); }
  // Remove the first group with the same semantics specified by |name|.
  void RemoveGroupByName(const std::string& name);

  // Global attributes.
  void set_msid_supported(bool supported) { msid_supported_ = supported; }
  bool msid_supported() const { return msid_supported_; }

  // Determines how the MSIDs were/will be signaled. Flag value composed of
  // MsidSignaling bits (see enum above).
  void set_msid_signaling(int msid_signaling) {
    msid_signaling_ = msid_signaling;
  }
  int msid_signaling() const { return msid_signaling_; }

  // Determines if it's allowed to mix one- and two-byte rtp header extensions
  // within the same rtp stream.
  void set_extmap_allow_mixed(bool supported) {
    extmap_allow_mixed_ = supported;
    MediaContentDescription::ExtmapAllowMixed media_level_setting =
        supported ? MediaContentDescription::kSession
                  : MediaContentDescription::kNo;
    for (auto& content : contents_) {
      // Do not set to kNo if the current setting is kMedia.
      if (supported || content.media_description()->extmap_allow_mixed_enum() !=
                           MediaContentDescription::kMedia) {
        content.media_description()->set_extmap_allow_mixed_enum(
            media_level_setting);
      }
    }
  }
  bool extmap_allow_mixed() const { return extmap_allow_mixed_; }

 private:
  SessionDescription(const SessionDescription&);

  ContentInfos contents_;
  TransportInfos transport_infos_;
  ContentGroups content_groups_;
  bool msid_supported_ = true;
  // Default to what Plan B would do.
  // TODO(bugs.webrtc.org/8530): Change default to kMsidSignalingMediaSection.
  int msid_signaling_ = kMsidSignalingSsrcAttribute;
  // TODO(webrtc:9985): Activate mixed one- and two-byte header extension in
  // offer at session level. It's currently not included in offer by default
  // because clients prior to https://bugs.webrtc.org/9712 cannot parse this
  // correctly. If it's included in offer to us we will respond that we support
  // it.
  bool extmap_allow_mixed_ = false;
};

// Indicates whether a session description was sent by the local client or
// received from the remote client.
enum ContentSource { CS_LOCAL, CS_REMOTE };

}  // namespace cricket

#endif  // PC_SESSION_DESCRIPTION_H_
