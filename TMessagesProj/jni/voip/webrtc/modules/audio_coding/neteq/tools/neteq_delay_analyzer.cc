/*
 *  Copyright (c) 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_coding/neteq/tools/neteq_delay_analyzer.h"

#include <algorithm>
#include <fstream>
#include <ios>
#include <iterator>
#include <limits>
#include <utility>

#include "absl/strings/string_view.h"
#include "rtc_base/checks.h"
#include "rtc_base/numerics/sequence_number_unwrapper.h"

namespace webrtc {
namespace test {
namespace {
constexpr char kArrivalDelayX[] = "arrival_delay_x";
constexpr char kArrivalDelayY[] = "arrival_delay_y";
constexpr char kTargetDelayX[] = "target_delay_x";
constexpr char kTargetDelayY[] = "target_delay_y";
constexpr char kPlayoutDelayX[] = "playout_delay_x";
constexpr char kPlayoutDelayY[] = "playout_delay_y";

// Helper function for NetEqDelayAnalyzer::CreateGraphs. Returns the
// interpolated value of a function at the point x. Vector x_vec contains the
// sample points, and y_vec contains the function values at these points. The
// return value is a linear interpolation between y_vec values.
double LinearInterpolate(double x,
                         const std::vector<int64_t>& x_vec,
                         const std::vector<int64_t>& y_vec) {
  // Find first element which is larger than x.
  auto it = std::upper_bound(x_vec.begin(), x_vec.end(), x);
  if (it == x_vec.end()) {
    --it;
  }
  const size_t upper_ix = it - x_vec.begin();

  size_t lower_ix;
  if (upper_ix == 0 || x_vec[upper_ix] <= x) {
    lower_ix = upper_ix;
  } else {
    lower_ix = upper_ix - 1;
  }
  double y;
  if (lower_ix == upper_ix) {
    y = y_vec[lower_ix];
  } else {
    RTC_DCHECK_NE(x_vec[lower_ix], x_vec[upper_ix]);
    y = (x - x_vec[lower_ix]) * (y_vec[upper_ix] - y_vec[lower_ix]) /
            (x_vec[upper_ix] - x_vec[lower_ix]) +
        y_vec[lower_ix];
  }
  return y;
}

void PrintDelays(const NetEqDelayAnalyzer::Delays& delays,
                 int64_t ref_time_ms,
                 absl::string_view var_name_x,
                 absl::string_view var_name_y,
                 std::ofstream& output,
                 absl::string_view terminator = "") {
  output << var_name_x << " = [ ";
  for (const std::pair<int64_t, float>& delay : delays) {
    output << (delay.first - ref_time_ms) / 1000.f << ", ";
  }
  output << "]" << terminator << std::endl;

  output << var_name_y << " = [ ";
  for (const std::pair<int64_t, float>& delay : delays) {
    output << delay.second << ", ";
  }
  output << "]" << terminator << std::endl;
}

}  // namespace

void NetEqDelayAnalyzer::AfterInsertPacket(
    const test::NetEqInput::PacketData& packet,
    NetEq* neteq) {
  data_.insert(
      std::make_pair(packet.header.timestamp, TimingData(packet.time_ms)));
  ssrcs_.insert(packet.header.ssrc);
  payload_types_.insert(packet.header.payloadType);
}

void NetEqDelayAnalyzer::BeforeGetAudio(NetEq* neteq) {
  last_sync_buffer_ms_ = neteq->SyncBufferSizeMs();
}

void NetEqDelayAnalyzer::AfterGetAudio(int64_t time_now_ms,
                                       const AudioFrame& audio_frame,
                                       bool /*muted*/,
                                       NetEq* neteq) {
  get_audio_time_ms_.push_back(time_now_ms);
  for (const RtpPacketInfo& info : audio_frame.packet_infos_) {
    auto it = data_.find(info.rtp_timestamp());
    if (it == data_.end()) {
      // This is a packet that was split out from another packet. Skip it.
      continue;
    }
    auto& it_timing = it->second;
    RTC_CHECK(!it_timing.decode_get_audio_count)
        << "Decode time already written";
    it_timing.decode_get_audio_count = get_audio_count_;
    RTC_CHECK(!it_timing.sync_delay_ms) << "Decode time already written";
    it_timing.sync_delay_ms = last_sync_buffer_ms_;
    it_timing.target_delay_ms = neteq->TargetDelayMs();
    it_timing.current_delay_ms = neteq->FilteredCurrentDelayMs();
  }
  last_sample_rate_hz_ = audio_frame.sample_rate_hz_;
  ++get_audio_count_;
}

void NetEqDelayAnalyzer::CreateGraphs(Delays* arrival_delay_ms,
                                      Delays* corrected_arrival_delay_ms,
                                      Delays* playout_delay_ms,
                                      Delays* target_delay_ms) const {
  if (get_audio_time_ms_.empty()) {
    return;
  }
  // Create nominal_get_audio_time_ms, a vector starting at
  // get_audio_time_ms_[0] and increasing by 10 for each element.
  std::vector<int64_t> nominal_get_audio_time_ms(get_audio_time_ms_.size());
  nominal_get_audio_time_ms[0] = get_audio_time_ms_[0];
  std::transform(
      nominal_get_audio_time_ms.begin(), nominal_get_audio_time_ms.end() - 1,
      nominal_get_audio_time_ms.begin() + 1, [](int64_t& x) { return x + 10; });
  RTC_DCHECK(
      std::is_sorted(get_audio_time_ms_.begin(), get_audio_time_ms_.end()));

  std::vector<double> rtp_timestamps_ms;
  double offset = std::numeric_limits<double>::max();
  RtpTimestampUnwrapper unwrapper;
  // This loop traverses data_ and populates rtp_timestamps_ms as well as
  // calculates the base offset.
  for (auto& d : data_) {
    rtp_timestamps_ms.push_back(
        static_cast<double>(unwrapper.Unwrap(d.first)) /
        rtc::CheckedDivExact(last_sample_rate_hz_, 1000));
    offset =
        std::min(offset, d.second.arrival_time_ms - rtp_timestamps_ms.back());
  }

  // This loop traverses the data again and populates the graph vectors. The
  // reason to have two loops and traverse twice is that the offset cannot be
  // known until the first traversal is done. Meanwhile, the final offset must
  // be known already at the start of this second loop.
  size_t i = 0;
  for (const auto& data : data_) {
    const double offset_send_time_ms = rtp_timestamps_ms[i++] + offset;
    const auto& timing = data.second;
    corrected_arrival_delay_ms->push_back(std::make_pair(
        timing.arrival_time_ms,
        LinearInterpolate(timing.arrival_time_ms, get_audio_time_ms_,
                          nominal_get_audio_time_ms) -
            offset_send_time_ms));
    arrival_delay_ms->push_back(std::make_pair(
        timing.arrival_time_ms, timing.arrival_time_ms - offset_send_time_ms));

    if (timing.decode_get_audio_count) {
      // This packet was decoded.
      RTC_DCHECK(timing.sync_delay_ms);
      const int64_t get_audio_time =
          *timing.decode_get_audio_count * 10 + get_audio_time_ms_[0];
      const float playout_ms =
          get_audio_time + *timing.sync_delay_ms - offset_send_time_ms;
      playout_delay_ms->push_back(std::make_pair(get_audio_time, playout_ms));
      RTC_DCHECK(timing.target_delay_ms);
      RTC_DCHECK(timing.current_delay_ms);
      const float target =
          playout_ms - *timing.current_delay_ms + *timing.target_delay_ms;
      target_delay_ms->push_back(std::make_pair(get_audio_time, target));
    }
  }
}

void NetEqDelayAnalyzer::CreateMatlabScript(
    absl::string_view script_name) const {
  Delays arrival_delay_ms;
  Delays corrected_arrival_delay_ms;
  Delays playout_delay_ms;
  Delays target_delay_ms;
  CreateGraphs(&arrival_delay_ms, &corrected_arrival_delay_ms,
               &playout_delay_ms, &target_delay_ms);

  // Maybe better to find the actually smallest timestamp, to surely avoid
  // x-axis starting from negative.
  const int64_t ref_time_ms = arrival_delay_ms.front().first;

  // Create an output file stream to Matlab script file.
  std::ofstream output(std::string{script_name});

  PrintDelays(corrected_arrival_delay_ms, ref_time_ms, kArrivalDelayX,
              kArrivalDelayY, output, ";");

  // PrintDelays(corrected_arrival_delay_x, kCorrectedArrivalDelayX,
  // kCorrectedArrivalDelayY, output);

  PrintDelays(playout_delay_ms, ref_time_ms, kPlayoutDelayX, kPlayoutDelayY,
              output, ";");

  PrintDelays(target_delay_ms, ref_time_ms, kTargetDelayX, kTargetDelayY,
              output, ";");

  output << "h=plot(" << kArrivalDelayX << ", " << kArrivalDelayY << ", "
         << kTargetDelayX << ", " << kTargetDelayY << ", 'g.', "
         << kPlayoutDelayX << ", " << kPlayoutDelayY << ");" << std::endl;
  output << "set(h(1),'color',0.75*[1 1 1]);" << std::endl;
  output << "set(h(2),'markersize',6);" << std::endl;
  output << "set(h(3),'linew',1.5);" << std::endl;
  output << "ax1=axis;" << std::endl;
  output << "axis tight" << std::endl;
  output << "ax2=axis;" << std::endl;
  output << "axis([ax2(1:3) ax1(4)])" << std::endl;
  output << "xlabel('time [s]');" << std::endl;
  output << "ylabel('relative delay [ms]');" << std::endl;
  if (!ssrcs_.empty()) {
    auto ssrc_it = ssrcs_.cbegin();
    output << "title('SSRC: 0x" << std::hex << static_cast<int64_t>(*ssrc_it++);
    while (ssrc_it != ssrcs_.end()) {
      output << ", 0x" << std::hex << static_cast<int64_t>(*ssrc_it++);
    }
    output << std::dec;
    auto pt_it = payload_types_.cbegin();
    output << "; Payload Types: " << *pt_it++;
    while (pt_it != payload_types_.end()) {
      output << ", " << *pt_it++;
    }
    output << "');" << std::endl;
  }
}

void NetEqDelayAnalyzer::CreatePythonScript(
    absl::string_view script_name) const {
  Delays arrival_delay_ms;
  Delays corrected_arrival_delay_ms;
  Delays playout_delay_ms;
  Delays target_delay_ms;
  CreateGraphs(&arrival_delay_ms, &corrected_arrival_delay_ms,
               &playout_delay_ms, &target_delay_ms);

  // Maybe better to find the actually smallest timestamp, to surely avoid
  // x-axis starting from negative.
  const int64_t ref_time_ms = arrival_delay_ms.front().first;

  // Create an output file stream to the python script file.
  std::ofstream output(std::string{script_name});

  // Necessary includes
  output << "import numpy as np" << std::endl;
  output << "import matplotlib.pyplot as plt" << std::endl;

  PrintDelays(corrected_arrival_delay_ms, ref_time_ms, kArrivalDelayX,
              kArrivalDelayY, output);

  // PrintDelays(corrected_arrival_delay_x, kCorrectedArrivalDelayX,
  // kCorrectedArrivalDelayY, output);

  PrintDelays(playout_delay_ms, ref_time_ms, kPlayoutDelayX, kPlayoutDelayY,
              output);

  PrintDelays(target_delay_ms, ref_time_ms, kTargetDelayX, kTargetDelayY,
              output);

  output << "if __name__ == '__main__':" << std::endl;
  output << "  h=plt.plot(" << kArrivalDelayX << ", " << kArrivalDelayY << ", "
         << kTargetDelayX << ", " << kTargetDelayY << ", 'g.', "
         << kPlayoutDelayX << ", " << kPlayoutDelayY << ")" << std::endl;
  output << "  plt.setp(h[0],'color',[.75, .75, .75])" << std::endl;
  output << "  plt.setp(h[1],'markersize',6)" << std::endl;
  output << "  plt.setp(h[2],'linewidth',1.5)" << std::endl;
  output << "  plt.axis('tight')" << std::endl;
  output << "  plt.xlabel('time [s]')" << std::endl;
  output << "  plt.ylabel('relative delay [ms]')" << std::endl;
  if (!ssrcs_.empty()) {
    auto ssrc_it = ssrcs_.cbegin();
    output << "  plt.legend((\"arrival delay\", \"target delay\", \"playout "
              "delay\"))"
           << std::endl;
    output << "  plt.title('SSRC: 0x" << std::hex
           << static_cast<int64_t>(*ssrc_it++);
    while (ssrc_it != ssrcs_.end()) {
      output << ", 0x" << std::hex << static_cast<int64_t>(*ssrc_it++);
    }
    output << std::dec;
    auto pt_it = payload_types_.cbegin();
    output << "; Payload Types: " << *pt_it++;
    while (pt_it != payload_types_.end()) {
      output << ", " << *pt_it++;
    }
    output << "')" << std::endl;
  }
  output << "  plt.show()" << std::endl;
}

}  // namespace test
}  // namespace webrtc
