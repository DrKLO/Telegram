/*
 *  Copyright (c) 2011 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_coding/neteq/test/result_sink.h"

#include <string>

#include "absl/strings/string_view.h"
#include "rtc_base/message_digest.h"
#include "rtc_base/string_encode.h"
#include "test/gtest.h"

#ifdef WEBRTC_NETEQ_UNITTEST_BITEXACT

#ifdef WEBRTC_ANDROID_PLATFORM_BUILD
#include "external/webrtc/webrtc/modules/audio_coding/neteq/neteq_unittest.pb.h"
#else
#include "modules/audio_coding/neteq/neteq_unittest.pb.h"
#endif

#endif

namespace webrtc {

#ifdef WEBRTC_NETEQ_UNITTEST_BITEXACT
void Convert(const webrtc::NetEqNetworkStatistics& stats_raw,
             webrtc::neteq_unittest::NetEqNetworkStatistics* stats) {
  stats->set_current_buffer_size_ms(stats_raw.current_buffer_size_ms);
  stats->set_preferred_buffer_size_ms(stats_raw.preferred_buffer_size_ms);
  stats->set_jitter_peaks_found(stats_raw.jitter_peaks_found);
  stats->set_expand_rate(stats_raw.expand_rate);
  stats->set_speech_expand_rate(stats_raw.speech_expand_rate);
  stats->set_preemptive_rate(stats_raw.preemptive_rate);
  stats->set_accelerate_rate(stats_raw.accelerate_rate);
  stats->set_secondary_decoded_rate(stats_raw.secondary_decoded_rate);
  stats->set_secondary_discarded_rate(stats_raw.secondary_discarded_rate);
  stats->set_mean_waiting_time_ms(stats_raw.mean_waiting_time_ms);
  stats->set_median_waiting_time_ms(stats_raw.median_waiting_time_ms);
  stats->set_min_waiting_time_ms(stats_raw.min_waiting_time_ms);
  stats->set_max_waiting_time_ms(stats_raw.max_waiting_time_ms);
}

void AddMessage(FILE* file,
                rtc::MessageDigest* digest,
                absl::string_view message) {
  int32_t size = message.length();
  if (file)
    ASSERT_EQ(1u, fwrite(&size, sizeof(size), 1, file));
  digest->Update(&size, sizeof(size));

  if (file)
    ASSERT_EQ(static_cast<size_t>(size),
              fwrite(message.data(), sizeof(char), size, file));
  digest->Update(message.data(), sizeof(char) * size);
}

#endif  // WEBRTC_NETEQ_UNITTEST_BITEXACT

ResultSink::ResultSink(absl::string_view output_file)
    : output_fp_(nullptr),
      digest_(rtc::MessageDigestFactory::Create(rtc::DIGEST_SHA_1)) {
  if (!output_file.empty()) {
    output_fp_ = fopen(std::string(output_file).c_str(), "wb");
    EXPECT_TRUE(output_fp_ != NULL);
  }
}

ResultSink::~ResultSink() {
  if (output_fp_)
    fclose(output_fp_);
}

void ResultSink::AddResult(const NetEqNetworkStatistics& stats_raw) {
#ifdef WEBRTC_NETEQ_UNITTEST_BITEXACT
  neteq_unittest::NetEqNetworkStatistics stats;
  Convert(stats_raw, &stats);

  std::string stats_string;
  ASSERT_TRUE(stats.SerializeToString(&stats_string));
  AddMessage(output_fp_, digest_.get(), stats_string);
#else
  FAIL() << "Writing to reference file requires Proto Buffer.";
#endif  // WEBRTC_NETEQ_UNITTEST_BITEXACT
}

void ResultSink::VerifyChecksum(absl::string_view checksum) {
  std::string buffer;
  buffer.resize(digest_->Size());
  digest_->Finish(buffer.data(), buffer.size());
  const std::string result = rtc::hex_encode(buffer);
  if (checksum.size() == result.size()) {
    EXPECT_EQ(checksum, result);
  } else {
    // Check result is one the '|'-separated checksums.
    EXPECT_NE(checksum.find(result), absl::string_view::npos)
        << result << " should be one of these:\n"
        << checksum;
  }
}

}  // namespace webrtc
