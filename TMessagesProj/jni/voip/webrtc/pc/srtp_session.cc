/*
 *  Copyright 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "pc/srtp_session.h"

#include <string.h>

#include <iomanip>
#include <string>

#include "absl/base/attributes.h"
#include "absl/base/const_init.h"
#include "absl/strings/string_view.h"
#include "api/array_view.h"
#include "api/field_trials_view.h"
#include "modules/rtp_rtcp/source/rtp_util.h"
#include "pc/external_hmac.h"
#include "rtc_base/byte_order.h"
#include "rtc_base/checks.h"
#include "rtc_base/logging.h"
#include "rtc_base/ssl_stream_adapter.h"
#include "rtc_base/string_encode.h"
#include "rtc_base/thread_annotations.h"
#include "rtc_base/time_utils.h"
#include "system_wrappers/include/metrics.h"
#include "third_party/libsrtp/include/srtp.h"
#include "third_party/libsrtp/include/srtp_priv.h"

namespace cricket {

namespace {
class LibSrtpInitializer {
 public:
  // Returns singleton instance of this class. Instance created on first use,
  // and never destroyed.
  static LibSrtpInitializer& Get() {
    static LibSrtpInitializer* const instance = new LibSrtpInitializer();
    return *instance;
  }
  void ProhibitLibsrtpInitialization();

  // These methods are responsible for initializing libsrtp (if the usage count
  // is incremented from 0 to 1) or deinitializing it (when decremented from 1
  // to 0).
  //
  // Returns true if successful (will always be successful if already inited).
  bool IncrementLibsrtpUsageCountAndMaybeInit(
      srtp_event_handler_func_t* handler);
  void DecrementLibsrtpUsageCountAndMaybeDeinit();

 private:
  LibSrtpInitializer() = default;

  webrtc::Mutex mutex_;
  int usage_count_ RTC_GUARDED_BY(mutex_) = 0;
};

void LibSrtpInitializer::ProhibitLibsrtpInitialization() {
  webrtc::MutexLock lock(&mutex_);
  ++usage_count_;
}

bool LibSrtpInitializer::IncrementLibsrtpUsageCountAndMaybeInit(
    srtp_event_handler_func_t* handler) {
  webrtc::MutexLock lock(&mutex_);

  RTC_DCHECK_GE(usage_count_, 0);
  if (usage_count_ == 0) {
    int err;
    err = srtp_init();
    if (err != srtp_err_status_ok) {
      RTC_LOG(LS_ERROR) << "Failed to init SRTP, err=" << err;
      return false;
    }

    err = srtp_install_event_handler(handler);
    if (err != srtp_err_status_ok) {
      RTC_LOG(LS_ERROR) << "Failed to install SRTP event handler, err=" << err;
      return false;
    }

    err = external_crypto_init();
    if (err != srtp_err_status_ok) {
      RTC_LOG(LS_ERROR) << "Failed to initialize fake auth, err=" << err;
      return false;
    }
  }
  ++usage_count_;
  return true;
}

void LibSrtpInitializer::DecrementLibsrtpUsageCountAndMaybeDeinit() {
  webrtc::MutexLock lock(&mutex_);

  RTC_DCHECK_GE(usage_count_, 1);
  if (--usage_count_ == 0) {
    int err = srtp_shutdown();
    if (err) {
      RTC_LOG(LS_ERROR) << "srtp_shutdown failed. err=" << err;
    }
  }
}

}  // namespace

using ::webrtc::ParseRtpSequenceNumber;

// One more than the maximum libsrtp error code. Required by
// RTC_HISTOGRAM_ENUMERATION. Keep this in sync with srtp_error_status_t defined
// in srtp.h.
constexpr int kSrtpErrorCodeBoundary = 28;

SrtpSession::SrtpSession() {}

SrtpSession::SrtpSession(const webrtc::FieldTrialsView& field_trials) {
  dump_plain_rtp_ = field_trials.IsEnabled("WebRTC-Debugging-RtpDump");
}

SrtpSession::~SrtpSession() {
  if (session_) {
    srtp_set_user_data(session_, nullptr);
    srtp_dealloc(session_);
  }
  if (inited_) {
    LibSrtpInitializer::Get().DecrementLibsrtpUsageCountAndMaybeDeinit();
  }
}

bool SrtpSession::SetSend(int crypto_suite,
                          const uint8_t* key,
                          size_t len,
                          const std::vector<int>& extension_ids) {
  return SetKey(ssrc_any_outbound, crypto_suite, key, len, extension_ids);
}

bool SrtpSession::UpdateSend(int crypto_suite,
                             const uint8_t* key,
                             size_t len,
                             const std::vector<int>& extension_ids) {
  return UpdateKey(ssrc_any_outbound, crypto_suite, key, len, extension_ids);
}

bool SrtpSession::SetRecv(int crypto_suite,
                          const uint8_t* key,
                          size_t len,
                          const std::vector<int>& extension_ids) {
  return SetKey(ssrc_any_inbound, crypto_suite, key, len, extension_ids);
}

bool SrtpSession::UpdateRecv(int crypto_suite,
                             const uint8_t* key,
                             size_t len,
                             const std::vector<int>& extension_ids) {
  return UpdateKey(ssrc_any_inbound, crypto_suite, key, len, extension_ids);
}

bool SrtpSession::ProtectRtp(void* p, int in_len, int max_len, int* out_len) {
  RTC_DCHECK(thread_checker_.IsCurrent());
  if (!session_) {
    RTC_LOG(LS_WARNING) << "Failed to protect SRTP packet: no SRTP Session";
    return false;
  }

  // Note: the need_len differs from the libsrtp recommendatіon to ensure
  // SRTP_MAX_TRAILER_LEN bytes of free space after the data. WebRTC
  // never includes a MKI, therefore the amount of bytes added by the
  // srtp_protect call is known in advance and depends on the cipher suite.
  int need_len = in_len + rtp_auth_tag_len_;  // NOLINT
  if (max_len < need_len) {
    RTC_LOG(LS_WARNING) << "Failed to protect SRTP packet: The buffer length "
                        << max_len << " is less than the needed " << need_len;
    return false;
  }
  if (dump_plain_rtp_) {
    DumpPacket(p, in_len, /*outbound=*/true);
  }

  *out_len = in_len;
  int err = srtp_protect(session_, p, out_len);
  int seq_num = ParseRtpSequenceNumber(
      rtc::MakeArrayView(reinterpret_cast<const uint8_t*>(p), in_len));
  if (err != srtp_err_status_ok) {
    RTC_LOG(LS_WARNING) << "Failed to protect SRTP packet, seqnum=" << seq_num
                        << ", err=" << err
                        << ", last seqnum=" << last_send_seq_num_;
    return false;
  }
  last_send_seq_num_ = seq_num;
  return true;
}

bool SrtpSession::ProtectRtp(void* p,
                             int in_len,
                             int max_len,
                             int* out_len,
                             int64_t* index) {
  if (!ProtectRtp(p, in_len, max_len, out_len)) {
    return false;
  }
  return (index) ? GetSendStreamPacketIndex(p, in_len, index) : true;
}

bool SrtpSession::ProtectRtcp(void* p, int in_len, int max_len, int* out_len) {
  RTC_DCHECK(thread_checker_.IsCurrent());
  if (!session_) {
    RTC_LOG(LS_WARNING) << "Failed to protect SRTCP packet: no SRTP Session";
    return false;
  }

  // Note: the need_len differs from the libsrtp recommendatіon to ensure
  // SRTP_MAX_TRAILER_LEN bytes of free space after the data. WebRTC
  // never includes a MKI, therefore the amount of bytes added by the
  // srtp_protect_rtp call is known in advance and depends on the cipher suite.
  int need_len = in_len + sizeof(uint32_t) + rtcp_auth_tag_len_;  // NOLINT
  if (max_len < need_len) {
    RTC_LOG(LS_WARNING) << "Failed to protect SRTCP packet: The buffer length "
                        << max_len << " is less than the needed " << need_len;
    return false;
  }
  if (dump_plain_rtp_) {
    DumpPacket(p, in_len, /*outbound=*/true);
  }

  *out_len = in_len;
  int err = srtp_protect_rtcp(session_, p, out_len);
  if (err != srtp_err_status_ok) {
    RTC_LOG(LS_WARNING) << "Failed to protect SRTCP packet, err=" << err;
    return false;
  }
  return true;
}

bool SrtpSession::UnprotectRtp(void* p, int in_len, int* out_len) {
  RTC_DCHECK(thread_checker_.IsCurrent());
  if (!session_) {
    RTC_LOG(LS_WARNING) << "Failed to unprotect SRTP packet: no SRTP Session";
    return false;
  }

  *out_len = in_len;
  int err = srtp_unprotect(session_, p, out_len);
  if (err != srtp_err_status_ok) {
    // Limit the error logging to avoid excessive logs when there are lots of
    // bad packets.
    const int kFailureLogThrottleCount = 100;
    if (decryption_failure_count_ % kFailureLogThrottleCount == 0) {
      RTC_LOG(LS_WARNING) << "Failed to unprotect SRTP packet, err=" << err
                          << ", previous failure count: "
                          << decryption_failure_count_;
    }
    ++decryption_failure_count_;
    RTC_HISTOGRAM_ENUMERATION("WebRTC.PeerConnection.SrtpUnprotectError",
                              static_cast<int>(err), kSrtpErrorCodeBoundary);
    return false;
  }
  if (dump_plain_rtp_) {
    DumpPacket(p, *out_len, /*outbound=*/false);
  }
  return true;
}

bool SrtpSession::UnprotectRtcp(void* p, int in_len, int* out_len) {
  RTC_DCHECK(thread_checker_.IsCurrent());
  if (!session_) {
    RTC_LOG(LS_WARNING) << "Failed to unprotect SRTCP packet: no SRTP Session";
    return false;
  }

  *out_len = in_len;
  int err = srtp_unprotect_rtcp(session_, p, out_len);
  if (err != srtp_err_status_ok) {
    RTC_LOG(LS_WARNING) << "Failed to unprotect SRTCP packet, err=" << err;
    RTC_HISTOGRAM_ENUMERATION("WebRTC.PeerConnection.SrtcpUnprotectError",
                              static_cast<int>(err), kSrtpErrorCodeBoundary);
    return false;
  }
  if (dump_plain_rtp_) {
    DumpPacket(p, *out_len, /*outbound=*/false);
  }
  return true;
}

bool SrtpSession::GetRtpAuthParams(uint8_t** key, int* key_len, int* tag_len) {
  RTC_DCHECK(thread_checker_.IsCurrent());
  RTC_DCHECK(IsExternalAuthActive());
  if (!IsExternalAuthActive()) {
    return false;
  }

  ExternalHmacContext* external_hmac = nullptr;
  // stream_template will be the reference context for other streams.
  // Let's use it for getting the keys.
  srtp_stream_ctx_t* srtp_context = session_->stream_template;
  if (srtp_context && srtp_context->session_keys &&
      srtp_context->session_keys->rtp_auth) {
    external_hmac = reinterpret_cast<ExternalHmacContext*>(
        srtp_context->session_keys->rtp_auth->state);
  }

  if (!external_hmac) {
    RTC_LOG(LS_ERROR) << "Failed to get auth keys from libsrtp!.";
    return false;
  }

  *key = external_hmac->key;
  *key_len = external_hmac->key_length;
  *tag_len = rtp_auth_tag_len_;
  return true;
}

int SrtpSession::GetSrtpOverhead() const {
  return rtp_auth_tag_len_;
}

void SrtpSession::EnableExternalAuth() {
  RTC_DCHECK(!session_);
  external_auth_enabled_ = true;
}

bool SrtpSession::IsExternalAuthEnabled() const {
  return external_auth_enabled_;
}

bool SrtpSession::IsExternalAuthActive() const {
  return external_auth_active_;
}

bool SrtpSession::RemoveSsrcFromSession(uint32_t ssrc) {
  RTC_DCHECK(session_);
  // libSRTP expects the SSRC to be in network byte order.
  return srtp_remove_stream(session_, htonl(ssrc)) == srtp_err_status_ok;
}

bool SrtpSession::GetSendStreamPacketIndex(void* p,
                                           int in_len,
                                           int64_t* index) {
  RTC_DCHECK(thread_checker_.IsCurrent());
  srtp_hdr_t* hdr = reinterpret_cast<srtp_hdr_t*>(p);
  srtp_stream_ctx_t* stream = srtp_get_stream(session_, hdr->ssrc);
  if (!stream) {
    return false;
  }

  // Shift packet index, put into network byte order
  *index = static_cast<int64_t>(rtc::NetworkToHost64(
      srtp_rdbx_get_packet_index(&stream->rtp_rdbx) << 16));
  return true;
}

bool SrtpSession::DoSetKey(int type,
                           int crypto_suite,
                           const uint8_t* key,
                           size_t len,
                           const std::vector<int>& extension_ids) {
  RTC_DCHECK(thread_checker_.IsCurrent());

  srtp_policy_t policy;
  memset(&policy, 0, sizeof(policy));
  if (!(srtp_crypto_policy_set_from_profile_for_rtp(
            &policy.rtp, (srtp_profile_t)crypto_suite) == srtp_err_status_ok &&
        srtp_crypto_policy_set_from_profile_for_rtcp(
            &policy.rtcp, (srtp_profile_t)crypto_suite) ==
            srtp_err_status_ok)) {
    RTC_LOG(LS_ERROR) << "Failed to " << (session_ ? "update" : "create")
                      << " SRTP session: unsupported cipher_suite "
                      << crypto_suite;
    return false;
  }

  if (!key || len != static_cast<size_t>(policy.rtp.cipher_key_len)) {
    RTC_LOG(LS_ERROR) << "Failed to " << (session_ ? "update" : "create")
                      << " SRTP session: invalid key";
    return false;
  }

  policy.ssrc.type = static_cast<srtp_ssrc_type_t>(type);
  policy.ssrc.value = 0;
  policy.key = const_cast<uint8_t*>(key);
  // TODO(astor) parse window size from WSH session-param
  policy.window_size = 1024;
  policy.allow_repeat_tx = 1;
  // If external authentication option is enabled, supply custom auth module
  // id EXTERNAL_HMAC_SHA1 in the policy structure.
  // We want to set this option only for rtp packets.
  // By default policy structure is initialized to HMAC_SHA1.
  // Enable external HMAC authentication only for outgoing streams and only
  // for cipher suites that support it (i.e. only non-GCM cipher suites).
  if (type == ssrc_any_outbound && IsExternalAuthEnabled() &&
      !rtc::IsGcmCryptoSuite(crypto_suite)) {
    policy.rtp.auth_type = EXTERNAL_HMAC_SHA1;
  }
  if (!extension_ids.empty()) {
    policy.enc_xtn_hdr = const_cast<int*>(&extension_ids[0]);
    policy.enc_xtn_hdr_count = static_cast<int>(extension_ids.size());
  }
  policy.next = nullptr;

  if (!session_) {
    int err = srtp_create(&session_, &policy);
    if (err != srtp_err_status_ok) {
      session_ = nullptr;
      RTC_LOG(LS_ERROR) << "Failed to create SRTP session, err=" << err;
      return false;
    }
    srtp_set_user_data(session_, this);
  } else {
    int err = srtp_update(session_, &policy);
    if (err != srtp_err_status_ok) {
      RTC_LOG(LS_ERROR) << "Failed to update SRTP session, err=" << err;
      return false;
    }
  }

  rtp_auth_tag_len_ = policy.rtp.auth_tag_len;
  rtcp_auth_tag_len_ = policy.rtcp.auth_tag_len;
  external_auth_active_ = (policy.rtp.auth_type == EXTERNAL_HMAC_SHA1);
  return true;
}

bool SrtpSession::SetKey(int type,
                         int crypto_suite,
                         const uint8_t* key,
                         size_t len,
                         const std::vector<int>& extension_ids) {
  RTC_DCHECK(thread_checker_.IsCurrent());
  if (session_) {
    RTC_LOG(LS_ERROR) << "Failed to create SRTP session: "
                         "SRTP session already created";
    return false;
  }

  // This is the first time we need to actually interact with libsrtp, so
  // initialize it if needed.
  if (LibSrtpInitializer::Get().IncrementLibsrtpUsageCountAndMaybeInit(
          &SrtpSession::HandleEventThunk)) {
    inited_ = true;
  } else {
    return false;
  }

  return DoSetKey(type, crypto_suite, key, len, extension_ids);
}

bool SrtpSession::UpdateKey(int type,
                            int crypto_suite,
                            const uint8_t* key,
                            size_t len,
                            const std::vector<int>& extension_ids) {
  RTC_DCHECK(thread_checker_.IsCurrent());
  if (!session_) {
    RTC_LOG(LS_ERROR) << "Failed to update non-existing SRTP session";
    return false;
  }

  return DoSetKey(type, crypto_suite, key, len, extension_ids);
}

void ProhibitLibsrtpInitialization() {
  LibSrtpInitializer::Get().ProhibitLibsrtpInitialization();
}

void SrtpSession::HandleEvent(const srtp_event_data_t* ev) {
  RTC_DCHECK(thread_checker_.IsCurrent());
  switch (ev->event) {
    case event_ssrc_collision:
      RTC_LOG(LS_INFO) << "SRTP event: SSRC collision";
      break;
    case event_key_soft_limit:
      RTC_LOG(LS_INFO) << "SRTP event: reached soft key usage limit";
      break;
    case event_key_hard_limit:
      RTC_LOG(LS_INFO) << "SRTP event: reached hard key usage limit";
      break;
    case event_packet_index_limit:
      RTC_LOG(LS_INFO)
          << "SRTP event: reached hard packet limit (2^48 packets)";
      break;
    default:
      RTC_LOG(LS_INFO) << "SRTP event: unknown " << ev->event;
      break;
  }
}

void SrtpSession::HandleEventThunk(srtp_event_data_t* ev) {
  // Callback will be executed from same thread that calls the "srtp_protect"
  // and "srtp_unprotect" functions.
  SrtpSession* session =
      static_cast<SrtpSession*>(srtp_get_user_data(ev->session));
  if (session) {
    session->HandleEvent(ev);
  }
}

// Logs the unencrypted packet in text2pcap format. This can then be
// extracted by searching for RTP_DUMP
//   grep RTP_DUMP chrome_debug.log > in.txt
// and converted to pcap using
//   text2pcap -D -u 1000,2000 -t %H:%M:%S. in.txt out.pcap
// The resulting file can be replayed using the WebRTC video_replay tool and
// be inspected in Wireshark using the RTP, VP8 and H264 dissectors.
void SrtpSession::DumpPacket(const void* buf, int len, bool outbound) {
  int64_t time_of_day = rtc::TimeUTCMillis() % (24 * 3600 * 1000);
  int64_t hours = time_of_day / (3600 * 1000);
  int64_t minutes = (time_of_day / (60 * 1000)) % 60;
  int64_t seconds = (time_of_day / 1000) % 60;
  int64_t millis = time_of_day % 1000;
  RTC_LOG(LS_VERBOSE) << "\n"
                      << (outbound ? "O" : "I") << " " << std::setfill('0')
                      << std::setw(2) << hours << ":" << std::setfill('0')
                      << std::setw(2) << minutes << ":" << std::setfill('0')
                      << std::setw(2) << seconds << "." << std::setfill('0')
                      << std::setw(3) << millis << " "
                      << "000000 "
                      << rtc::hex_encode_with_delimiter(
                             absl::string_view((const char*)buf, len), ' ')
                      << " # RTP_DUMP";
}

}  // namespace cricket
