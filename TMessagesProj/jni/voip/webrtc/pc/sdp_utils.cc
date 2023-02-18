/*
 *  Copyright 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "pc/sdp_utils.h"

#include <utility>
#include <vector>

#include "api/jsep_session_description.h"
#include "rtc_base/checks.h"

namespace webrtc {

std::unique_ptr<SessionDescriptionInterface> CloneSessionDescription(
    const SessionDescriptionInterface* sdesc) {
  RTC_DCHECK(sdesc);
  return CloneSessionDescriptionAsType(sdesc, sdesc->GetType());
}

std::unique_ptr<SessionDescriptionInterface> CloneSessionDescriptionAsType(
    const SessionDescriptionInterface* sdesc,
    SdpType type) {
  RTC_DCHECK(sdesc);
  auto clone = std::make_unique<JsepSessionDescription>(type);
  if (sdesc->description()) {
    clone->Initialize(sdesc->description()->Clone(), sdesc->session_id(),
                      sdesc->session_version());
  }
  // As of writing, our version of GCC does not allow returning a unique_ptr of
  // a subclass as a unique_ptr of a base class. To get around this, we need to
  // std::move the return value.
  return std::move(clone);
}

bool SdpContentsAll(SdpContentPredicate pred,
                    const cricket::SessionDescription* desc) {
  RTC_DCHECK(desc);
  for (const auto& content : desc->contents()) {
    const auto* transport_info = desc->GetTransportInfoByName(content.name);
    if (!pred(&content, transport_info)) {
      return false;
    }
  }
  return true;
}

bool SdpContentsNone(SdpContentPredicate pred,
                     const cricket::SessionDescription* desc) {
  return SdpContentsAll(
      [pred](const cricket::ContentInfo* content_info,
             const cricket::TransportInfo* transport_info) {
        return !pred(content_info, transport_info);
      },
      desc);
}

void SdpContentsForEach(SdpContentMutator fn,
                        cricket::SessionDescription* desc) {
  RTC_DCHECK(desc);
  for (auto& content : desc->contents()) {
    auto* transport_info = desc->GetTransportInfoByName(content.name);
    fn(&content, transport_info);
  }
}

}  // namespace webrtc
