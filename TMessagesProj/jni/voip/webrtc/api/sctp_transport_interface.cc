/*
 *  Copyright 2019 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "api/sctp_transport_interface.h"

#include <utility>

namespace webrtc {

SctpTransportInformation::SctpTransportInformation(SctpTransportState state)
    : state_(state) {}

SctpTransportInformation::SctpTransportInformation(
    SctpTransportState state,
    rtc::scoped_refptr<DtlsTransportInterface> dtls_transport,
    absl::optional<double> max_message_size,
    absl::optional<int> max_channels)
    : state_(state),
      dtls_transport_(std::move(dtls_transport)),
      max_message_size_(max_message_size),
      max_channels_(max_channels) {}

SctpTransportInformation::~SctpTransportInformation() {}

}  // namespace webrtc
