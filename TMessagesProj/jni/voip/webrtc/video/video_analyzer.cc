/*
 *  Copyright 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#include "video/video_analyzer.h"

#include <algorithm>
#include <utility>

#include "absl/algorithm/container.h"
#include "absl/flags/flag.h"
#include "absl/flags/parse.h"
#include "common_video/libyuv/include/webrtc_libyuv.h"
#include "modules/rtp_rtcp/source/create_video_rtp_depacketizer.h"
#include "modules/rtp_rtcp/source/rtp_packet.h"
#include "rtc_base/cpu_time.h"
#include "rtc_base/format_macros.h"
#include "rtc_base/memory_usage.h"
#include "rtc_base/task_queue_for_test.h"
#include "rtc_base/task_utils/repeating_task.h"
#include "system_wrappers/include/cpu_info.h"
#include "test/call_test.h"
#include "test/testsupport/file_utils.h"
#include "test/testsupport/frame_writer.h"
#include "test/testsupport/perf_test.h"
#include "test/testsupport/test_artifacts.h"

ABSL_FLAG(bool,
          save_worst_frame,
          false,
          "Enable saving a frame with the lowest PSNR to a jpeg file in the "
          "test_artifacts_dir");

namespace webrtc {
namespace {
constexpr TimeDelta kSendStatsPollingInterval = TimeDelta::Seconds(1);
constexpr size_t kMaxComparisons = 10;
// How often is keep alive message printed.
constexpr int kKeepAliveIntervalSeconds = 30;
// Interval between checking that the test is over.
constexpr int kProbingIntervalMs = 500;
constexpr int kKeepAliveIntervalIterations =
    kKeepAliveIntervalSeconds * 1000 / kProbingIntervalMs;

bool IsFlexfec(int payload_type) {
  return payload_type == test::CallTest::kFlexfecPayloadType;
}
}  // namespace

VideoAnalyzer::VideoAnalyzer(test::LayerFilteringTransport* transport,
                             const std::string& test_label,
                             double avg_psnr_threshold,
                             double avg_ssim_threshold,
                             int duration_frames,
                             TimeDelta test_duration,
                             FILE* graph_data_output_file,
                             const std::string& graph_title,
                             uint32_t ssrc_to_analyze,
                             uint32_t rtx_ssrc_to_analyze,
                             size_t selected_stream,
                             int selected_sl,
                             int selected_tl,
                             bool is_quick_test_enabled,
                             Clock* clock,
                             std::string rtp_dump_name,
                             TaskQueueBase* task_queue)
    : transport_(transport),
      receiver_(nullptr),
      call_(nullptr),
      send_stream_(nullptr),
      receive_stream_(nullptr),
      audio_receive_stream_(nullptr),
      captured_frame_forwarder_(this, clock, duration_frames, test_duration),
      test_label_(test_label),
      graph_data_output_file_(graph_data_output_file),
      graph_title_(graph_title),
      ssrc_to_analyze_(ssrc_to_analyze),
      rtx_ssrc_to_analyze_(rtx_ssrc_to_analyze),
      selected_stream_(selected_stream),
      selected_sl_(selected_sl),
      selected_tl_(selected_tl),
      mean_decode_time_ms_(0.0),
      freeze_count_(0),
      total_freezes_duration_ms_(0),
      total_frames_duration_ms_(0),
      sum_squared_frame_durations_(0),
      decode_frame_rate_(0),
      render_frame_rate_(0),
      last_fec_bytes_(0),
      frames_to_process_(duration_frames),
      test_end_(clock->CurrentTime() + test_duration),
      frames_recorded_(0),
      frames_processed_(0),
      captured_frames_(0),
      dropped_frames_(0),
      dropped_frames_before_first_encode_(0),
      dropped_frames_before_rendering_(0),
      last_render_time_(0),
      last_render_delta_ms_(0),
      last_unfreeze_time_ms_(0),
      rtp_timestamp_delta_(0),
      cpu_time_(0),
      wallclock_time_(0),
      avg_psnr_threshold_(avg_psnr_threshold),
      avg_ssim_threshold_(avg_ssim_threshold),
      is_quick_test_enabled_(is_quick_test_enabled),
      quit_(false),
      done_(true, false),
      vp8_depacketizer_(CreateVideoRtpDepacketizer(kVideoCodecVP8)),
      vp9_depacketizer_(CreateVideoRtpDepacketizer(kVideoCodecVP9)),
      clock_(clock),
      start_ms_(clock->TimeInMilliseconds()),
      task_queue_(task_queue) {
  // Create thread pool for CPU-expensive PSNR/SSIM calculations.

  // Try to use about as many threads as cores, but leave kMinCoresLeft alone,
  // so that we don't accidentally starve "real" worker threads (codec etc).
  // Also, don't allocate more than kMaxComparisonThreads, even if there are
  // spare cores.

  uint32_t num_cores = CpuInfo::DetectNumberOfCores();
  RTC_DCHECK_GE(num_cores, 1);
  static const uint32_t kMinCoresLeft = 4;
  static const uint32_t kMaxComparisonThreads = 8;

  if (num_cores <= kMinCoresLeft) {
    num_cores = 1;
  } else {
    num_cores -= kMinCoresLeft;
    num_cores = std::min(num_cores, kMaxComparisonThreads);
  }

  for (uint32_t i = 0; i < num_cores; ++i) {
    rtc::PlatformThread* thread =
        new rtc::PlatformThread(&FrameComparisonThread, this, "Analyzer");
    thread->Start();
    comparison_thread_pool_.push_back(thread);
  }

  if (!rtp_dump_name.empty()) {
    fprintf(stdout, "Writing rtp dump to %s\n", rtp_dump_name.c_str());
    rtp_file_writer_.reset(test::RtpFileWriter::Create(
        test::RtpFileWriter::kRtpDump, rtp_dump_name));
  }
}

VideoAnalyzer::~VideoAnalyzer() {
  {
    MutexLock lock(&comparison_lock_);
    quit_ = true;
  }
  for (rtc::PlatformThread* thread : comparison_thread_pool_) {
    thread->Stop();
    delete thread;
  }
}

void VideoAnalyzer::SetReceiver(PacketReceiver* receiver) {
  receiver_ = receiver;
}

void VideoAnalyzer::SetSource(
    rtc::VideoSourceInterface<VideoFrame>* video_source,
    bool respect_sink_wants) {
  if (respect_sink_wants)
    captured_frame_forwarder_.SetSource(video_source);
  rtc::VideoSinkWants wants;
  video_source->AddOrUpdateSink(InputInterface(), wants);
}

void VideoAnalyzer::SetCall(Call* call) {
  MutexLock lock(&lock_);
  RTC_DCHECK(!call_);
  call_ = call;
}

void VideoAnalyzer::SetSendStream(VideoSendStream* stream) {
  MutexLock lock(&lock_);
  RTC_DCHECK(!send_stream_);
  send_stream_ = stream;
}

void VideoAnalyzer::SetReceiveStream(VideoReceiveStream* stream) {
  MutexLock lock(&lock_);
  RTC_DCHECK(!receive_stream_);
  receive_stream_ = stream;
}

void VideoAnalyzer::SetAudioReceiveStream(AudioReceiveStream* recv_stream) {
  MutexLock lock(&lock_);
  RTC_CHECK(!audio_receive_stream_);
  audio_receive_stream_ = recv_stream;
}

rtc::VideoSinkInterface<VideoFrame>* VideoAnalyzer::InputInterface() {
  return &captured_frame_forwarder_;
}

rtc::VideoSourceInterface<VideoFrame>* VideoAnalyzer::OutputInterface() {
  return &captured_frame_forwarder_;
}

PacketReceiver::DeliveryStatus VideoAnalyzer::DeliverPacket(
    MediaType media_type,
    rtc::CopyOnWriteBuffer packet,
    int64_t packet_time_us) {
  // Ignore timestamps of RTCP packets. They're not synchronized with
  // RTP packet timestamps and so they would confuse wrap_handler_.
  if (RtpHeaderParser::IsRtcp(packet.cdata(), packet.size())) {
    return receiver_->DeliverPacket(media_type, std::move(packet),
                                    packet_time_us);
  }

  if (rtp_file_writer_) {
    test::RtpPacket p;
    memcpy(p.data, packet.cdata(), packet.size());
    p.length = packet.size();
    p.original_length = packet.size();
    p.time_ms = clock_->TimeInMilliseconds() - start_ms_;
    rtp_file_writer_->WritePacket(&p);
  }

  RtpPacket rtp_packet;
  rtp_packet.Parse(packet);
  if (!IsFlexfec(rtp_packet.PayloadType()) &&
      (rtp_packet.Ssrc() == ssrc_to_analyze_ ||
       rtp_packet.Ssrc() == rtx_ssrc_to_analyze_)) {
    // Ignore FlexFEC timestamps, to avoid collisions with media timestamps.
    // (FlexFEC and media are sent on different SSRCs, which have different
    // timestamps spaces.)
    // Also ignore packets from wrong SSRC, but include retransmits.
    MutexLock lock(&lock_);
    int64_t timestamp =
        wrap_handler_.Unwrap(rtp_packet.Timestamp() - rtp_timestamp_delta_);
    recv_times_[timestamp] = clock_->CurrentNtpInMilliseconds();
  }

  return receiver_->DeliverPacket(media_type, std::move(packet),
                                  packet_time_us);
}

void VideoAnalyzer::PreEncodeOnFrame(const VideoFrame& video_frame) {
  MutexLock lock(&lock_);
  if (!first_encoded_timestamp_) {
    while (frames_.front().timestamp() != video_frame.timestamp()) {
      ++dropped_frames_before_first_encode_;
      frames_.pop_front();
      RTC_CHECK(!frames_.empty());
    }
    first_encoded_timestamp_ = video_frame.timestamp();
  }
}

void VideoAnalyzer::PostEncodeOnFrame(size_t stream_id, uint32_t timestamp) {
  MutexLock lock(&lock_);
  if (!first_sent_timestamp_ && stream_id == selected_stream_) {
    first_sent_timestamp_ = timestamp;
  }
}

bool VideoAnalyzer::SendRtp(const uint8_t* packet,
                            size_t length,
                            const PacketOptions& options) {
  RtpPacket rtp_packet;
  rtp_packet.Parse(packet, length);

  int64_t current_time = clock_->CurrentNtpInMilliseconds();

  bool result = transport_->SendRtp(packet, length, options);
  {
    MutexLock lock(&lock_);
    if (rtp_timestamp_delta_ == 0 && rtp_packet.Ssrc() == ssrc_to_analyze_) {
      RTC_CHECK(static_cast<bool>(first_sent_timestamp_));
      rtp_timestamp_delta_ = rtp_packet.Timestamp() - *first_sent_timestamp_;
    }

    if (!IsFlexfec(rtp_packet.PayloadType()) &&
        rtp_packet.Ssrc() == ssrc_to_analyze_) {
      // Ignore FlexFEC timestamps, to avoid collisions with media timestamps.
      // (FlexFEC and media are sent on different SSRCs, which have different
      // timestamps spaces.)
      // Also ignore packets from wrong SSRC and retransmits.
      int64_t timestamp =
          wrap_handler_.Unwrap(rtp_packet.Timestamp() - rtp_timestamp_delta_);
      send_times_[timestamp] = current_time;

      if (IsInSelectedSpatialAndTemporalLayer(rtp_packet)) {
        encoded_frame_sizes_[timestamp] += rtp_packet.payload_size();
      }
    }
  }
  return result;
}

bool VideoAnalyzer::SendRtcp(const uint8_t* packet, size_t length) {
  return transport_->SendRtcp(packet, length);
}

void VideoAnalyzer::OnFrame(const VideoFrame& video_frame) {
  int64_t render_time_ms = clock_->CurrentNtpInMilliseconds();

  MutexLock lock(&lock_);

  StartExcludingCpuThreadTime();

  int64_t send_timestamp =
      wrap_handler_.Unwrap(video_frame.timestamp() - rtp_timestamp_delta_);

  while (wrap_handler_.Unwrap(frames_.front().timestamp()) < send_timestamp) {
    if (!last_rendered_frame_) {
      // No previous frame rendered, this one was dropped after sending but
      // before rendering.
      ++dropped_frames_before_rendering_;
    } else {
      AddFrameComparison(frames_.front(), *last_rendered_frame_, true,
                         render_time_ms);
    }
    frames_.pop_front();
    RTC_DCHECK(!frames_.empty());
  }

  VideoFrame reference_frame = frames_.front();
  frames_.pop_front();
  int64_t reference_timestamp =
      wrap_handler_.Unwrap(reference_frame.timestamp());
  if (send_timestamp == reference_timestamp - 1) {
    // TODO(ivica): Make this work for > 2 streams.
    // Look at RTPSender::BuildRTPHeader.
    ++send_timestamp;
  }
  ASSERT_EQ(reference_timestamp, send_timestamp);

  AddFrameComparison(reference_frame, video_frame, false, render_time_ms);

  last_rendered_frame_ = video_frame;

  StopExcludingCpuThreadTime();
}

void VideoAnalyzer::Wait() {
  // Frame comparisons can be very expensive. Wait for test to be done, but
  // at time-out check if frames_processed is going up. If so, give it more
  // time, otherwise fail. Hopefully this will reduce test flakiness.

  RepeatingTaskHandle stats_polling_task = RepeatingTaskHandle::DelayedStart(
      task_queue_, kSendStatsPollingInterval, [this] {
        PollStats();
        return kSendStatsPollingInterval;
      });

  int last_frames_processed = -1;
  int last_frames_captured = -1;
  int iteration = 0;

  while (!done_.Wait(kProbingIntervalMs)) {
    int frames_processed;
    int frames_captured;
    {
      MutexLock lock(&comparison_lock_);
      frames_processed = frames_processed_;
      frames_captured = captured_frames_;
    }

    // Print some output so test infrastructure won't think we've crashed.
    const char* kKeepAliveMessages[3] = {
        "Uh, I'm-I'm not quite dead, sir.",
        "Uh, I-I think uh, I could pull through, sir.",
        "Actually, I think I'm all right to come with you--"};
    if (++iteration % kKeepAliveIntervalIterations == 0) {
      printf("- %s\n", kKeepAliveMessages[iteration % 3]);
    }

    if (last_frames_processed == -1) {
      last_frames_processed = frames_processed;
      last_frames_captured = frames_captured;
      continue;
    }
    if (frames_processed == last_frames_processed &&
        last_frames_captured == frames_captured &&
        clock_->CurrentTime() > test_end_) {
      done_.Set();
      break;
    }
    last_frames_processed = frames_processed;
    last_frames_captured = frames_captured;
  }

  if (iteration > 0)
    printf("- Farewell, sweet Concorde!\n");

  SendTask(RTC_FROM_HERE, task_queue_, [&] { stats_polling_task.Stop(); });

  PrintResults();
  if (graph_data_output_file_)
    PrintSamplesToFile();
}

void VideoAnalyzer::StartMeasuringCpuProcessTime() {
  MutexLock lock(&cpu_measurement_lock_);
  cpu_time_ -= rtc::GetProcessCpuTimeNanos();
  wallclock_time_ -= rtc::SystemTimeNanos();
}

void VideoAnalyzer::StopMeasuringCpuProcessTime() {
  MutexLock lock(&cpu_measurement_lock_);
  cpu_time_ += rtc::GetProcessCpuTimeNanos();
  wallclock_time_ += rtc::SystemTimeNanos();
}

void VideoAnalyzer::StartExcludingCpuThreadTime() {
  MutexLock lock(&cpu_measurement_lock_);
  cpu_time_ += rtc::GetThreadCpuTimeNanos();
}

void VideoAnalyzer::StopExcludingCpuThreadTime() {
  MutexLock lock(&cpu_measurement_lock_);
  cpu_time_ -= rtc::GetThreadCpuTimeNanos();
}

double VideoAnalyzer::GetCpuUsagePercent() {
  MutexLock lock(&cpu_measurement_lock_);
  return static_cast<double>(cpu_time_) / wallclock_time_ * 100.0;
}

bool VideoAnalyzer::IsInSelectedSpatialAndTemporalLayer(
    const RtpPacket& rtp_packet) {
  if (rtp_packet.PayloadType() == test::CallTest::kPayloadTypeVP8) {
    auto parsed_payload = vp8_depacketizer_->Parse(rtp_packet.PayloadBuffer());
    RTC_DCHECK(parsed_payload);
    const auto& vp8_header = absl::get<RTPVideoHeaderVP8>(
        parsed_payload->video_header.video_type_header);
    int temporal_idx = vp8_header.temporalIdx;
    return selected_tl_ < 0 || temporal_idx == kNoTemporalIdx ||
           temporal_idx <= selected_tl_;
  }

  if (rtp_packet.PayloadType() == test::CallTest::kPayloadTypeVP9) {
    auto parsed_payload = vp9_depacketizer_->Parse(rtp_packet.PayloadBuffer());
    RTC_DCHECK(parsed_payload);
    const auto& vp9_header = absl::get<RTPVideoHeaderVP9>(
        parsed_payload->video_header.video_type_header);
    int temporal_idx = vp9_header.temporal_idx;
    int spatial_idx = vp9_header.spatial_idx;
    return (selected_tl_ < 0 || temporal_idx == kNoTemporalIdx ||
            temporal_idx <= selected_tl_) &&
           (selected_sl_ < 0 || spatial_idx == kNoSpatialIdx ||
            spatial_idx <= selected_sl_);
  }

  return true;
}

void VideoAnalyzer::PollStats() {
  // Do not grab |comparison_lock_|, before |GetStats()| completes.
  // Otherwise a deadlock may occur:
  // 1) |comparison_lock_| is acquired after |lock_|
  // 2) |lock_| is acquired after internal pacer lock in SendRtp()
  // 3) internal pacer lock is acquired by GetStats().
  Call::Stats call_stats = call_->GetStats();

  MutexLock lock(&comparison_lock_);

  send_bandwidth_bps_.AddSample(call_stats.send_bandwidth_bps);

  VideoSendStream::Stats send_stats = send_stream_->GetStats();
  // It's not certain that we yet have estimates for any of these stats.
  // Check that they are positive before mixing them in.
  if (send_stats.encode_frame_rate > 0)
    encode_frame_rate_.AddSample(send_stats.encode_frame_rate);
  if (send_stats.avg_encode_time_ms > 0)
    encode_time_ms_.AddSample(send_stats.avg_encode_time_ms);
  if (send_stats.encode_usage_percent > 0)
    encode_usage_percent_.AddSample(send_stats.encode_usage_percent);
  if (send_stats.media_bitrate_bps > 0)
    media_bitrate_bps_.AddSample(send_stats.media_bitrate_bps);
  size_t fec_bytes = 0;
  for (const auto& kv : send_stats.substreams) {
    fec_bytes += kv.second.rtp_stats.fec.payload_bytes +
                 kv.second.rtp_stats.fec.padding_bytes;
  }
  fec_bitrate_bps_.AddSample((fec_bytes - last_fec_bytes_) * 8);
  last_fec_bytes_ = fec_bytes;

  if (receive_stream_ != nullptr) {
    VideoReceiveStream::Stats receive_stats = receive_stream_->GetStats();
    // |total_decode_time_ms| gives a good estimate of the mean decode time,
    // |decode_ms| is used to keep track of the standard deviation.
    if (receive_stats.frames_decoded > 0)
      mean_decode_time_ms_ =
          static_cast<double>(receive_stats.total_decode_time_ms) /
          receive_stats.frames_decoded;
    if (receive_stats.decode_ms > 0)
      decode_time_ms_.AddSample(receive_stats.decode_ms);
    if (receive_stats.max_decode_ms > 0)
      decode_time_max_ms_.AddSample(receive_stats.max_decode_ms);
    if (receive_stats.width > 0 && receive_stats.height > 0) {
      pixels_.AddSample(receive_stats.width * receive_stats.height);
    }

    // |frames_decoded| and |frames_rendered| are used because they are more
    // accurate than |decode_frame_rate| and |render_frame_rate|.
    // The latter two are calculated on a momentary basis.
    const double total_frames_duration_sec_double =
        static_cast<double>(receive_stats.total_frames_duration_ms) / 1000.0;
    if (total_frames_duration_sec_double > 0) {
      decode_frame_rate_ = static_cast<double>(receive_stats.frames_decoded) /
                           total_frames_duration_sec_double;
      render_frame_rate_ = static_cast<double>(receive_stats.frames_rendered) /
                           total_frames_duration_sec_double;
    }

    // Freeze metrics.
    freeze_count_ = receive_stats.freeze_count;
    total_freezes_duration_ms_ = receive_stats.total_freezes_duration_ms;
    total_frames_duration_ms_ = receive_stats.total_frames_duration_ms;
    sum_squared_frame_durations_ = receive_stats.sum_squared_frame_durations;
  }

  if (audio_receive_stream_ != nullptr) {
    AudioReceiveStream::Stats receive_stats =
        audio_receive_stream_->GetStats(/*get_and_clear_legacy_stats=*/true);
    audio_expand_rate_.AddSample(receive_stats.expand_rate);
    audio_accelerate_rate_.AddSample(receive_stats.accelerate_rate);
    audio_jitter_buffer_ms_.AddSample(receive_stats.jitter_buffer_ms);
  }

  memory_usage_.AddSample(rtc::GetProcessResidentSizeBytes());
}

void VideoAnalyzer::FrameComparisonThread(void* obj) {
  VideoAnalyzer* analyzer = static_cast<VideoAnalyzer*>(obj);
  while (analyzer->CompareFrames()) {
  }
}

bool VideoAnalyzer::CompareFrames() {
  if (AllFramesRecorded())
    return false;

  FrameComparison comparison;

  if (!PopComparison(&comparison)) {
    // Wait until new comparison task is available, or test is done.
    // If done, wake up remaining threads waiting.
    comparison_available_event_.Wait(1000);
    if (AllFramesRecorded()) {
      comparison_available_event_.Set();
      return false;
    }
    return true;  // Try again.
  }

  StartExcludingCpuThreadTime();

  PerformFrameComparison(comparison);

  StopExcludingCpuThreadTime();

  if (FrameProcessed()) {
    done_.Set();
    comparison_available_event_.Set();
    return false;
  }

  return true;
}

bool VideoAnalyzer::PopComparison(VideoAnalyzer::FrameComparison* comparison) {
  MutexLock lock(&comparison_lock_);
  // If AllFramesRecorded() is true, it means we have already popped
  // frames_to_process_ frames from comparisons_, so there is no more work
  // for this thread to be done. frames_processed_ might still be lower if
  // all comparisons are not done, but those frames are currently being
  // worked on by other threads.
  if (comparisons_.empty() || AllFramesRecordedLocked())
    return false;

  *comparison = comparisons_.front();
  comparisons_.pop_front();

  FrameRecorded();
  return true;
}

void VideoAnalyzer::FrameRecorded() {
  ++frames_recorded_;
}

bool VideoAnalyzer::AllFramesRecorded() {
  MutexLock lock(&comparison_lock_);
  return AllFramesRecordedLocked();
}

bool VideoAnalyzer::AllFramesRecordedLocked() {
  RTC_DCHECK(frames_recorded_ <= frames_to_process_);
  return frames_recorded_ == frames_to_process_ ||
         (clock_->CurrentTime() > test_end_ && comparisons_.empty()) || quit_;
}

bool VideoAnalyzer::FrameProcessed() {
  MutexLock lock(&comparison_lock_);
  ++frames_processed_;
  assert(frames_processed_ <= frames_to_process_);
  return frames_processed_ == frames_to_process_ ||
         (clock_->CurrentTime() > test_end_ && comparisons_.empty());
}

void VideoAnalyzer::PrintResults() {
  using ::webrtc::test::ImproveDirection;

  StopMeasuringCpuProcessTime();
  int dropped_frames_diff;
  {
    MutexLock lock(&lock_);
    dropped_frames_diff = dropped_frames_before_first_encode_ +
                          dropped_frames_before_rendering_ + frames_.size();
  }
  MutexLock lock(&comparison_lock_);
  PrintResult("psnr", psnr_, "dB", ImproveDirection::kBiggerIsBetter);
  PrintResult("ssim", ssim_, "unitless", ImproveDirection::kBiggerIsBetter);
  PrintResult("sender_time", sender_time_, "ms",
              ImproveDirection::kSmallerIsBetter);
  PrintResult("receiver_time", receiver_time_, "ms",
              ImproveDirection::kSmallerIsBetter);
  PrintResult("network_time", network_time_, "ms",
              ImproveDirection::kSmallerIsBetter);
  PrintResult("total_delay_incl_network", end_to_end_, "ms",
              ImproveDirection::kSmallerIsBetter);
  PrintResult("time_between_rendered_frames", rendered_delta_, "ms",
              ImproveDirection::kSmallerIsBetter);
  PrintResult("encode_frame_rate", encode_frame_rate_, "fps",
              ImproveDirection::kBiggerIsBetter);
  PrintResult("encode_time", encode_time_ms_, "ms",
              ImproveDirection::kSmallerIsBetter);
  PrintResult("media_bitrate", media_bitrate_bps_, "bps",
              ImproveDirection::kNone);
  PrintResult("fec_bitrate", fec_bitrate_bps_, "bps", ImproveDirection::kNone);
  PrintResult("send_bandwidth", send_bandwidth_bps_, "bps",
              ImproveDirection::kNone);
  PrintResult("pixels_per_frame", pixels_, "count",
              ImproveDirection::kBiggerIsBetter);

  test::PrintResult("decode_frame_rate", "", test_label_.c_str(),
                    decode_frame_rate_, "fps", false,
                    ImproveDirection::kBiggerIsBetter);
  test::PrintResult("render_frame_rate", "", test_label_.c_str(),
                    render_frame_rate_, "fps", false,
                    ImproveDirection::kBiggerIsBetter);

  // Record the time from the last freeze until the last rendered frame to
  // ensure we cover the full timespan of the session. Otherwise the metric
  // would penalize an early freeze followed by no freezes until the end.
  time_between_freezes_.AddSample(last_render_time_ - last_unfreeze_time_ms_);

  // Freeze metrics.
  PrintResult("time_between_freezes", time_between_freezes_, "ms",
              ImproveDirection::kBiggerIsBetter);

  const double freeze_count_double = static_cast<double>(freeze_count_);
  const double total_freezes_duration_ms_double =
      static_cast<double>(total_freezes_duration_ms_);
  const double total_frames_duration_ms_double =
      static_cast<double>(total_frames_duration_ms_);

  if (total_frames_duration_ms_double > 0) {
    test::PrintResult(
        "freeze_duration_ratio", "", test_label_.c_str(),
        total_freezes_duration_ms_double / total_frames_duration_ms_double,
        "unitless", false, ImproveDirection::kSmallerIsBetter);
    RTC_DCHECK_LE(total_freezes_duration_ms_double,
                  total_frames_duration_ms_double);

    constexpr double ms_per_minute = 60 * 1000;
    const double total_frames_duration_min =
        total_frames_duration_ms_double / ms_per_minute;
    if (total_frames_duration_min > 0) {
      test::PrintResult("freeze_count_per_minute", "", test_label_.c_str(),
                        freeze_count_double / total_frames_duration_min,
                        "unitless", false, ImproveDirection::kSmallerIsBetter);
    }
  }

  test::PrintResult("freeze_duration_average", "", test_label_.c_str(),
                    freeze_count_double > 0
                        ? total_freezes_duration_ms_double / freeze_count_double
                        : 0,
                    "ms", false, ImproveDirection::kSmallerIsBetter);

  if (1000 * sum_squared_frame_durations_ > 0) {
    test::PrintResult(
        "harmonic_frame_rate", "", test_label_.c_str(),
        total_frames_duration_ms_double / (1000 * sum_squared_frame_durations_),
        "fps", false, ImproveDirection::kBiggerIsBetter);
  }

  if (worst_frame_) {
    test::PrintResult("min_psnr", "", test_label_.c_str(), worst_frame_->psnr,
                      "dB", false, ImproveDirection::kBiggerIsBetter);
  }

  if (receive_stream_ != nullptr) {
    PrintResultWithExternalMean("decode_time", mean_decode_time_ms_,
                                decode_time_ms_, "ms",
                                ImproveDirection::kSmallerIsBetter);
  }
  dropped_frames_ += dropped_frames_diff;
  test::PrintResult("dropped_frames", "", test_label_.c_str(), dropped_frames_,
                    "count", false, ImproveDirection::kSmallerIsBetter);
  test::PrintResult("cpu_usage", "", test_label_.c_str(), GetCpuUsagePercent(),
                    "%", false, ImproveDirection::kSmallerIsBetter);

#if defined(WEBRTC_WIN)
  // On Linux and Mac in Resident Set some unused pages may be counted.
  // Therefore this metric will depend on order in which tests are run and
  // will be flaky.
  PrintResult("memory_usage", memory_usage_, "sizeInBytes",
              ImproveDirection::kSmallerIsBetter);
#endif

  // Saving only the worst frame for manual analysis. Intention here is to
  // only detect video corruptions and not to track picture quality. Thus,
  // jpeg is used here.
  if (absl::GetFlag(FLAGS_save_worst_frame) && worst_frame_) {
    std::string output_dir;
    test::GetTestArtifactsDir(&output_dir);
    std::string output_path =
        test::JoinFilename(output_dir, test_label_ + ".jpg");
    RTC_LOG(LS_INFO) << "Saving worst frame to " << output_path;
    test::JpegFrameWriter frame_writer(output_path);
    RTC_CHECK(
        frame_writer.WriteFrame(worst_frame_->frame, 100 /*best quality*/));
  }

  if (audio_receive_stream_ != nullptr) {
    PrintResult("audio_expand_rate", audio_expand_rate_, "unitless",
                ImproveDirection::kSmallerIsBetter);
    PrintResult("audio_accelerate_rate", audio_accelerate_rate_, "unitless",
                ImproveDirection::kSmallerIsBetter);
    PrintResult("audio_jitter_buffer", audio_jitter_buffer_ms_, "ms",
                ImproveDirection::kNone);
  }

  //  Disable quality check for quick test, as quality checks may fail
  //  because too few samples were collected.
  if (!is_quick_test_enabled_) {
    EXPECT_GT(*psnr_.GetMean(), avg_psnr_threshold_);
    EXPECT_GT(*ssim_.GetMean(), avg_ssim_threshold_);
  }
}

void VideoAnalyzer::PerformFrameComparison(
    const VideoAnalyzer::FrameComparison& comparison) {
  // Perform expensive psnr and ssim calculations while not holding lock.
  double psnr = -1.0;
  double ssim = -1.0;
  if (comparison.reference && !comparison.dropped) {
    psnr = I420PSNR(&*comparison.reference, &*comparison.render);
    ssim = I420SSIM(&*comparison.reference, &*comparison.render);
  }

  MutexLock lock(&comparison_lock_);

  if (psnr >= 0.0 && (!worst_frame_ || worst_frame_->psnr > psnr)) {
    worst_frame_.emplace(FrameWithPsnr{psnr, *comparison.render});
  }

  if (graph_data_output_file_) {
    samples_.push_back(Sample(comparison.dropped, comparison.input_time_ms,
                              comparison.send_time_ms, comparison.recv_time_ms,
                              comparison.render_time_ms,
                              comparison.encoded_frame_size, psnr, ssim));
  }
  if (psnr >= 0.0)
    psnr_.AddSample(psnr);
  if (ssim >= 0.0)
    ssim_.AddSample(ssim);

  if (comparison.dropped) {
    ++dropped_frames_;
    return;
  }
  if (last_unfreeze_time_ms_ == 0)
    last_unfreeze_time_ms_ = comparison.render_time_ms;
  if (last_render_time_ != 0) {
    const int64_t render_delta_ms =
        comparison.render_time_ms - last_render_time_;
    rendered_delta_.AddSample(render_delta_ms);
    if (last_render_delta_ms_ != 0 &&
        render_delta_ms - last_render_delta_ms_ > 150) {
      time_between_freezes_.AddSample(last_render_time_ -
                                      last_unfreeze_time_ms_);
      last_unfreeze_time_ms_ = comparison.render_time_ms;
    }
    last_render_delta_ms_ = render_delta_ms;
  }
  last_render_time_ = comparison.render_time_ms;

  sender_time_.AddSample(comparison.send_time_ms - comparison.input_time_ms);
  if (comparison.recv_time_ms > 0) {
    // If recv_time_ms == 0, this frame consisted of a packets which were all
    // lost in the transport. Since we were able to render the frame, however,
    // the dropped packets were recovered by FlexFEC. The FlexFEC recovery
    // happens internally in Call, and we can therefore here not know which
    // FEC packets that protected the lost media packets. Consequently, we
    // were not able to record a meaningful recv_time_ms. We therefore skip
    // this sample.
    //
    // The reasoning above does not hold for ULPFEC and RTX, as for those
    // strategies the timestamp of the received packets is set to the
    // timestamp of the protected/retransmitted media packet. I.e., then
    // recv_time_ms != 0, even though the media packets were lost.
    receiver_time_.AddSample(comparison.render_time_ms -
                             comparison.recv_time_ms);
    network_time_.AddSample(comparison.recv_time_ms - comparison.send_time_ms);
  }
  end_to_end_.AddSample(comparison.render_time_ms - comparison.input_time_ms);
  encoded_frame_size_.AddSample(comparison.encoded_frame_size);
}

void VideoAnalyzer::PrintResult(
    const char* result_type,
    Statistics stats,
    const char* unit,
    webrtc::test::ImproveDirection improve_direction) {
  test::PrintResultMeanAndError(
      result_type, "", test_label_.c_str(), stats.GetMean().value_or(0),
      stats.GetStandardDeviation().value_or(0), unit, false, improve_direction);
}

void VideoAnalyzer::PrintResultWithExternalMean(
    const char* result_type,
    double mean,
    Statistics stats,
    const char* unit,
    webrtc::test::ImproveDirection improve_direction) {
  // If the true mean is different than the sample mean, the sample variance is
  // too low. The sample variance given a known mean is obtained by adding the
  // squared error between the true mean and the sample mean.
  double compensated_variance =
      stats.Size() > 0
          ? *stats.GetVariance() + pow(mean - *stats.GetMean(), 2.0)
          : 0.0;
  test::PrintResultMeanAndError(result_type, "", test_label_.c_str(), mean,
                                std::sqrt(compensated_variance), unit, false,
                                improve_direction);
}

void VideoAnalyzer::PrintSamplesToFile() {
  FILE* out = graph_data_output_file_;
  MutexLock lock(&comparison_lock_);
  absl::c_sort(samples_, [](const Sample& A, const Sample& B) -> bool {
    return A.input_time_ms < B.input_time_ms;
  });

  fprintf(out, "%s\n", graph_title_.c_str());
  fprintf(out, "%" RTC_PRIuS "\n", samples_.size());
  fprintf(out,
          "dropped "
          "input_time_ms "
          "send_time_ms "
          "recv_time_ms "
          "render_time_ms "
          "encoded_frame_size "
          "psnr "
          "ssim "
          "encode_time_ms\n");
  for (const Sample& sample : samples_) {
    fprintf(out,
            "%d %" PRId64 " %" PRId64 " %" PRId64 " %" PRId64 " %" RTC_PRIuS
            " %lf %lf\n",
            sample.dropped, sample.input_time_ms, sample.send_time_ms,
            sample.recv_time_ms, sample.render_time_ms,
            sample.encoded_frame_size, sample.psnr, sample.ssim);
  }
}

void VideoAnalyzer::AddCapturedFrameForComparison(
    const VideoFrame& video_frame) {
  bool must_capture = false;
  {
    MutexLock lock(&comparison_lock_);
    must_capture = captured_frames_ < frames_to_process_;
    if (must_capture) {
      ++captured_frames_;
    }
  }
  if (must_capture) {
    MutexLock lock(&lock_);
    frames_.push_back(video_frame);
  }
}

void VideoAnalyzer::AddFrameComparison(const VideoFrame& reference,
                                       const VideoFrame& render,
                                       bool dropped,
                                       int64_t render_time_ms) {
  int64_t reference_timestamp = wrap_handler_.Unwrap(reference.timestamp());
  int64_t send_time_ms = send_times_[reference_timestamp];
  send_times_.erase(reference_timestamp);
  int64_t recv_time_ms = recv_times_[reference_timestamp];
  recv_times_.erase(reference_timestamp);

  // TODO(ivica): Make this work for > 2 streams.
  auto it = encoded_frame_sizes_.find(reference_timestamp);
  if (it == encoded_frame_sizes_.end())
    it = encoded_frame_sizes_.find(reference_timestamp - 1);
  size_t encoded_size = it == encoded_frame_sizes_.end() ? 0 : it->second;
  if (it != encoded_frame_sizes_.end())
    encoded_frame_sizes_.erase(it);

  MutexLock lock(&comparison_lock_);
  if (comparisons_.size() < kMaxComparisons) {
    comparisons_.push_back(FrameComparison(
        reference, render, dropped, reference.ntp_time_ms(), send_time_ms,
        recv_time_ms, render_time_ms, encoded_size));
  } else {
    comparisons_.push_back(FrameComparison(dropped, reference.ntp_time_ms(),
                                           send_time_ms, recv_time_ms,
                                           render_time_ms, encoded_size));
  }
  comparison_available_event_.Set();
}

VideoAnalyzer::FrameComparison::FrameComparison()
    : dropped(false),
      input_time_ms(0),
      send_time_ms(0),
      recv_time_ms(0),
      render_time_ms(0),
      encoded_frame_size(0) {}

VideoAnalyzer::FrameComparison::FrameComparison(const VideoFrame& reference,
                                                const VideoFrame& render,
                                                bool dropped,
                                                int64_t input_time_ms,
                                                int64_t send_time_ms,
                                                int64_t recv_time_ms,
                                                int64_t render_time_ms,
                                                size_t encoded_frame_size)
    : reference(reference),
      render(render),
      dropped(dropped),
      input_time_ms(input_time_ms),
      send_time_ms(send_time_ms),
      recv_time_ms(recv_time_ms),
      render_time_ms(render_time_ms),
      encoded_frame_size(encoded_frame_size) {}

VideoAnalyzer::FrameComparison::FrameComparison(bool dropped,
                                                int64_t input_time_ms,
                                                int64_t send_time_ms,
                                                int64_t recv_time_ms,
                                                int64_t render_time_ms,
                                                size_t encoded_frame_size)
    : dropped(dropped),
      input_time_ms(input_time_ms),
      send_time_ms(send_time_ms),
      recv_time_ms(recv_time_ms),
      render_time_ms(render_time_ms),
      encoded_frame_size(encoded_frame_size) {}

VideoAnalyzer::Sample::Sample(int dropped,
                              int64_t input_time_ms,
                              int64_t send_time_ms,
                              int64_t recv_time_ms,
                              int64_t render_time_ms,
                              size_t encoded_frame_size,
                              double psnr,
                              double ssim)
    : dropped(dropped),
      input_time_ms(input_time_ms),
      send_time_ms(send_time_ms),
      recv_time_ms(recv_time_ms),
      render_time_ms(render_time_ms),
      encoded_frame_size(encoded_frame_size),
      psnr(psnr),
      ssim(ssim) {}

VideoAnalyzer::CapturedFrameForwarder::CapturedFrameForwarder(
    VideoAnalyzer* analyzer,
    Clock* clock,
    int frames_to_capture,
    TimeDelta test_duration)
    : analyzer_(analyzer),
      send_stream_input_(nullptr),
      video_source_(nullptr),
      clock_(clock),
      captured_frames_(0),
      frames_to_capture_(frames_to_capture),
      test_end_(clock->CurrentTime() + test_duration) {}

void VideoAnalyzer::CapturedFrameForwarder::SetSource(
    VideoSourceInterface<VideoFrame>* video_source) {
  video_source_ = video_source;
}

void VideoAnalyzer::CapturedFrameForwarder::OnFrame(
    const VideoFrame& video_frame) {
  VideoFrame copy = video_frame;
  // Frames from the capturer does not have a rtp timestamp.
  // Create one so it can be used for comparison.
  RTC_DCHECK_EQ(0, video_frame.timestamp());
  if (video_frame.ntp_time_ms() == 0)
    copy.set_ntp_time_ms(clock_->CurrentNtpInMilliseconds());
  copy.set_timestamp(copy.ntp_time_ms() * 90);
  analyzer_->AddCapturedFrameForComparison(copy);
  MutexLock lock(&lock_);
  ++captured_frames_;
  if (send_stream_input_ && clock_->CurrentTime() <= test_end_ &&
      captured_frames_ <= frames_to_capture_) {
    send_stream_input_->OnFrame(copy);
  }
}

void VideoAnalyzer::CapturedFrameForwarder::AddOrUpdateSink(
    rtc::VideoSinkInterface<VideoFrame>* sink,
    const rtc::VideoSinkWants& wants) {
  {
    MutexLock lock(&lock_);
    RTC_DCHECK(!send_stream_input_ || send_stream_input_ == sink);
    send_stream_input_ = sink;
  }
  if (video_source_) {
    video_source_->AddOrUpdateSink(this, wants);
  }
}

void VideoAnalyzer::CapturedFrameForwarder::RemoveSink(
    rtc::VideoSinkInterface<VideoFrame>* sink) {
  MutexLock lock(&lock_);
  RTC_DCHECK(sink == send_stream_input_);
  send_stream_input_ = nullptr;
}

}  // namespace webrtc
