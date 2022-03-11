/*
 *  Copyright (c) 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/rtp_rtcp/source/rtp_header_extension_size.h"

#include "api/rtp_parameters.h"

namespace webrtc {

int RtpHeaderExtensionSize(rtc::ArrayView<const RtpExtensionSize> extensions,
                           const RtpHeaderExtensionMap& registered_extensions) {
  // RFC3550 Section 5.3.1
  static constexpr int kExtensionBlockHeaderSize = 4;

  int values_size = 0;
  int num_extensions = 0;
  int each_extension_header_size = 1;
  for (const RtpExtensionSize& extension : extensions) {
    int id = registered_extensions.GetId(extension.type);
    if (id == RtpHeaderExtensionMap::kInvalidId)
      continue;
    // All extensions should use same size header. Check if the `extension`
    // forces to switch to two byte header that allows larger id and value size.
    if (id > RtpExtension::kOneByteHeaderExtensionMaxId ||
        extension.value_size >
            RtpExtension::kOneByteHeaderExtensionMaxValueSize) {
      each_extension_header_size = 2;
    }
    values_size += extension.value_size;
    num_extensions++;
  }
  if (values_size == 0)
    return 0;
  int size = kExtensionBlockHeaderSize +
             each_extension_header_size * num_extensions + values_size;
  // Extension size specified in 32bit words,
  // so result must be multiple of 4 bytes. Round up.
  return size + 3 - (size + 3) % 4;
}

}  // namespace webrtc
