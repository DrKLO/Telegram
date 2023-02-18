/*
 *  Copyright 2009 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "pc/srtp_filter.h"

#include <string.h>

#include <string>

#include "absl/strings/match.h"
#include "rtc_base/logging.h"
#include "rtc_base/ssl_stream_adapter.h"
#include "rtc_base/third_party/base64/base64.h"
#include "rtc_base/zero_memory.h"

namespace cricket {

SrtpFilter::SrtpFilter() {}

SrtpFilter::~SrtpFilter() {}

bool SrtpFilter::IsActive() const {
  return state_ >= ST_ACTIVE;
}

bool SrtpFilter::Process(const std::vector<CryptoParams>& cryptos,
                         webrtc::SdpType type,
                         ContentSource source) {
  bool ret = false;
  switch (type) {
    case webrtc::SdpType::kOffer:
      ret = SetOffer(cryptos, source);
      break;
    case webrtc::SdpType::kPrAnswer:
      ret = SetProvisionalAnswer(cryptos, source);
      break;
    case webrtc::SdpType::kAnswer:
      ret = SetAnswer(cryptos, source);
      break;
    default:
      break;
  }

  if (!ret) {
    return false;
  }

  return true;
}

bool SrtpFilter::SetOffer(const std::vector<CryptoParams>& offer_params,
                          ContentSource source) {
  if (!ExpectOffer(source)) {
    RTC_LOG(LS_ERROR) << "Wrong state to update SRTP offer";
    return false;
  }
  return StoreParams(offer_params, source);
}

bool SrtpFilter::SetAnswer(const std::vector<CryptoParams>& answer_params,
                           ContentSource source) {
  return DoSetAnswer(answer_params, source, true);
}

bool SrtpFilter::SetProvisionalAnswer(
    const std::vector<CryptoParams>& answer_params,
    ContentSource source) {
  return DoSetAnswer(answer_params, source, false);
}

bool SrtpFilter::ExpectOffer(ContentSource source) {
  return ((state_ == ST_INIT) || (state_ == ST_ACTIVE) ||
          (state_ == ST_SENTOFFER && source == CS_LOCAL) ||
          (state_ == ST_SENTUPDATEDOFFER && source == CS_LOCAL) ||
          (state_ == ST_RECEIVEDOFFER && source == CS_REMOTE) ||
          (state_ == ST_RECEIVEDUPDATEDOFFER && source == CS_REMOTE));
}

bool SrtpFilter::StoreParams(const std::vector<CryptoParams>& params,
                             ContentSource source) {
  offer_params_ = params;
  if (state_ == ST_INIT) {
    state_ = (source == CS_LOCAL) ? ST_SENTOFFER : ST_RECEIVEDOFFER;
  } else if (state_ == ST_ACTIVE) {
    state_ =
        (source == CS_LOCAL) ? ST_SENTUPDATEDOFFER : ST_RECEIVEDUPDATEDOFFER;
  }
  return true;
}

bool SrtpFilter::ExpectAnswer(ContentSource source) {
  return ((state_ == ST_SENTOFFER && source == CS_REMOTE) ||
          (state_ == ST_RECEIVEDOFFER && source == CS_LOCAL) ||
          (state_ == ST_SENTUPDATEDOFFER && source == CS_REMOTE) ||
          (state_ == ST_RECEIVEDUPDATEDOFFER && source == CS_LOCAL) ||
          (state_ == ST_SENTPRANSWER_NO_CRYPTO && source == CS_LOCAL) ||
          (state_ == ST_SENTPRANSWER && source == CS_LOCAL) ||
          (state_ == ST_RECEIVEDPRANSWER_NO_CRYPTO && source == CS_REMOTE) ||
          (state_ == ST_RECEIVEDPRANSWER && source == CS_REMOTE));
}

bool SrtpFilter::DoSetAnswer(const std::vector<CryptoParams>& answer_params,
                             ContentSource source,
                             bool final) {
  if (!ExpectAnswer(source)) {
    RTC_LOG(LS_ERROR) << "Invalid state for SRTP answer";
    return false;
  }

  // If the answer doesn't requests crypto complete the negotiation of an
  // unencrypted session.
  // Otherwise, finalize the parameters and apply them.
  if (answer_params.empty()) {
    if (final) {
      return ResetParams();
    } else {
      // Need to wait for the final answer to decide if
      // we should go to Active state.
      state_ = (source == CS_LOCAL) ? ST_SENTPRANSWER_NO_CRYPTO
                                    : ST_RECEIVEDPRANSWER_NO_CRYPTO;
      return true;
    }
  }
  CryptoParams selected_params;
  if (!NegotiateParams(answer_params, &selected_params))
    return false;

  const CryptoParams& new_send_params =
      (source == CS_REMOTE) ? selected_params : answer_params[0];
  const CryptoParams& new_recv_params =
      (source == CS_REMOTE) ? answer_params[0] : selected_params;
  if (!ApplySendParams(new_send_params) || !ApplyRecvParams(new_recv_params)) {
    return false;
  }
  applied_send_params_ = new_send_params;
  applied_recv_params_ = new_recv_params;

  if (final) {
    offer_params_.clear();
    state_ = ST_ACTIVE;
  } else {
    state_ = (source == CS_LOCAL) ? ST_SENTPRANSWER : ST_RECEIVEDPRANSWER;
  }
  return true;
}

bool SrtpFilter::NegotiateParams(const std::vector<CryptoParams>& answer_params,
                                 CryptoParams* selected_params) {
  // We're processing an accept. We should have exactly one set of params,
  // unless the offer didn't mention crypto, in which case we shouldn't be here.
  bool ret = (answer_params.size() == 1U && !offer_params_.empty());
  if (ret) {
    // We should find a match between the answer params and the offered params.
    std::vector<CryptoParams>::const_iterator it;
    for (it = offer_params_.begin(); it != offer_params_.end(); ++it) {
      if (answer_params[0].Matches(*it)) {
        break;
      }
    }

    if (it != offer_params_.end()) {
      *selected_params = *it;
    } else {
      ret = false;
    }
  }

  if (!ret) {
    RTC_LOG(LS_WARNING) << "Invalid parameters in SRTP answer";
  }
  return ret;
}

bool SrtpFilter::ResetParams() {
  offer_params_.clear();
  applied_send_params_ = CryptoParams();
  applied_recv_params_ = CryptoParams();
  send_cipher_suite_ = absl::nullopt;
  recv_cipher_suite_ = absl::nullopt;
  send_key_.Clear();
  recv_key_.Clear();
  state_ = ST_INIT;
  return true;
}

bool SrtpFilter::ApplySendParams(const CryptoParams& send_params) {
  if (applied_send_params_.cipher_suite == send_params.cipher_suite &&
      applied_send_params_.key_params == send_params.key_params) {
    RTC_LOG(LS_INFO) << "Applying the same SRTP send parameters again. No-op.";

    // We do not want to reset the ROC if the keys are the same. So just return.
    return true;
  }

  send_cipher_suite_ = rtc::SrtpCryptoSuiteFromName(send_params.cipher_suite);
  if (send_cipher_suite_ == rtc::kSrtpInvalidCryptoSuite) {
    RTC_LOG(LS_WARNING) << "Unknown crypto suite(s) received:"
                           " send cipher_suite "
                        << send_params.cipher_suite;
    return false;
  }

  int send_key_len, send_salt_len;
  if (!rtc::GetSrtpKeyAndSaltLengths(*send_cipher_suite_, &send_key_len,
                                     &send_salt_len)) {
    RTC_LOG(LS_ERROR) << "Could not get lengths for crypto suite(s):"
                         " send cipher_suite "
                      << send_params.cipher_suite;
    return false;
  }

  send_key_ = rtc::ZeroOnFreeBuffer<uint8_t>(send_key_len + send_salt_len);
  return ParseKeyParams(send_params.key_params, send_key_.data(),
                        send_key_.size());
}

bool SrtpFilter::ApplyRecvParams(const CryptoParams& recv_params) {
  if (applied_recv_params_.cipher_suite == recv_params.cipher_suite &&
      applied_recv_params_.key_params == recv_params.key_params) {
    RTC_LOG(LS_INFO) << "Applying the same SRTP recv parameters again. No-op.";

    // We do not want to reset the ROC if the keys are the same. So just return.
    return true;
  }

  recv_cipher_suite_ = rtc::SrtpCryptoSuiteFromName(recv_params.cipher_suite);
  if (recv_cipher_suite_ == rtc::kSrtpInvalidCryptoSuite) {
    RTC_LOG(LS_WARNING) << "Unknown crypto suite(s) received:"
                           " recv cipher_suite "
                        << recv_params.cipher_suite;
    return false;
  }

  int recv_key_len, recv_salt_len;
  if (!rtc::GetSrtpKeyAndSaltLengths(*recv_cipher_suite_, &recv_key_len,
                                     &recv_salt_len)) {
    RTC_LOG(LS_ERROR) << "Could not get lengths for crypto suite(s):"
                         " recv cipher_suite "
                      << recv_params.cipher_suite;
    return false;
  }

  recv_key_ = rtc::ZeroOnFreeBuffer<uint8_t>(recv_key_len + recv_salt_len);
  return ParseKeyParams(recv_params.key_params, recv_key_.data(),
                        recv_key_.size());
}

bool SrtpFilter::ParseKeyParams(const std::string& key_params,
                                uint8_t* key,
                                size_t len) {
  // example key_params: "inline:YUJDZGVmZ2hpSktMbW9QUXJzVHVWd3l6MTIzNDU2"

  // Fail if key-method is wrong.
  if (!absl::StartsWith(key_params, "inline:")) {
    return false;
  }

  // Fail if base64 decode fails, or the key is the wrong size.
  std::string key_b64(key_params.substr(7)), key_str;
  if (!rtc::Base64::Decode(key_b64, rtc::Base64::DO_STRICT, &key_str,
                           nullptr) ||
      key_str.size() != len) {
    return false;
  }

  memcpy(key, key_str.c_str(), len);
  // TODO(bugs.webrtc.org/8905): Switch to ZeroOnFreeBuffer for storing
  // sensitive data.
  rtc::ExplicitZeroMemory(&key_str[0], key_str.size());
  return true;
}

}  // namespace cricket
