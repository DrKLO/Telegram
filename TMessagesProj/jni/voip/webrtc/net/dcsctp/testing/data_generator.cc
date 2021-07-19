/*
 *  Copyright (c) 2021 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#include "net/dcsctp/testing/data_generator.h"

#include <cstdint>
#include <string>
#include <utility>
#include <vector>

#include "absl/strings/string_view.h"
#include "net/dcsctp/packet/data.h"
#include "net/dcsctp/public/types.h"

namespace dcsctp {
constexpr PPID kPpid = PPID(53);

Data DataGenerator::Ordered(std::vector<uint8_t> payload,
                            absl::string_view flags,
                            const DataGeneratorOptions opts) {
  Data::IsBeginning is_beginning(flags.find('B') != std::string::npos);
  Data::IsEnd is_end(flags.find('E') != std::string::npos);

  if (is_beginning) {
    fsn_ = FSN(0);
  } else {
    fsn_ = FSN(*fsn_ + 1);
  }
  MID message_id = opts.message_id.value_or(message_id_);
  Data ret = Data(opts.stream_id, SSN(static_cast<uint16_t>(*message_id)),
                  message_id, fsn_, opts.ppid, std::move(payload), is_beginning,
                  is_end, IsUnordered(false));

  if (is_end) {
    message_id_ = MID(*message_id + 1);
  }
  return ret;
}

Data DataGenerator::Unordered(std::vector<uint8_t> payload,
                              absl::string_view flags,
                              const DataGeneratorOptions opts) {
  Data::IsBeginning is_beginning(flags.find('B') != std::string::npos);
  Data::IsEnd is_end(flags.find('E') != std::string::npos);

  if (is_beginning) {
    fsn_ = FSN(0);
  } else {
    fsn_ = FSN(*fsn_ + 1);
  }
  MID message_id = opts.message_id.value_or(message_id_);
  Data ret = Data(opts.stream_id, SSN(0), message_id, fsn_, kPpid,
                  std::move(payload), is_beginning, is_end, IsUnordered(true));
  if (is_end) {
    message_id_ = MID(*message_id + 1);
  }
  return ret;
}
}  // namespace dcsctp
