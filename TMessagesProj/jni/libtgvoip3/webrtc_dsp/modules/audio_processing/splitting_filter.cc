/*
 *  Copyright (c) 2014 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_processing/splitting_filter.h"

#include "common_audio/channel_buffer.h"
#include "common_audio/signal_processing/include/signal_processing_library.h"
#include "rtc_base/checks.h"

namespace webrtc {

SplittingFilter::SplittingFilter(size_t num_channels,
                                 size_t num_bands,
                                 size_t num_frames)
    : num_bands_(num_bands) {
  RTC_CHECK(num_bands_ == 2 || num_bands_ == 3);
  if (num_bands_ == 2) {
    two_bands_states_.resize(num_channels);
  } else if (num_bands_ == 3) {
    for (size_t i = 0; i < num_channels; ++i) {
      three_band_filter_banks_.push_back(std::unique_ptr<ThreeBandFilterBank>(
          new ThreeBandFilterBank(num_frames)));
    }
  }
}

SplittingFilter::~SplittingFilter() = default;

void SplittingFilter::Analysis(const IFChannelBuffer* data,
                               IFChannelBuffer* bands) {
  RTC_DCHECK_EQ(num_bands_, bands->num_bands());
  RTC_DCHECK_EQ(data->num_channels(), bands->num_channels());
  RTC_DCHECK_EQ(data->num_frames(),
                bands->num_frames_per_band() * bands->num_bands());
  if (bands->num_bands() == 2) {
    TwoBandsAnalysis(data, bands);
  } else if (bands->num_bands() == 3) {
    ThreeBandsAnalysis(data, bands);
  }
}

void SplittingFilter::Synthesis(const IFChannelBuffer* bands,
                                IFChannelBuffer* data) {
  RTC_DCHECK_EQ(num_bands_, bands->num_bands());
  RTC_DCHECK_EQ(data->num_channels(), bands->num_channels());
  RTC_DCHECK_EQ(data->num_frames(),
                bands->num_frames_per_band() * bands->num_bands());
  if (bands->num_bands() == 2) {
    TwoBandsSynthesis(bands, data);
  } else if (bands->num_bands() == 3) {
    ThreeBandsSynthesis(bands, data);
  }
}

void SplittingFilter::TwoBandsAnalysis(const IFChannelBuffer* data,
                                       IFChannelBuffer* bands) {
  RTC_DCHECK_EQ(two_bands_states_.size(), data->num_channels());
  for (size_t i = 0; i < two_bands_states_.size(); ++i) {
    WebRtcSpl_AnalysisQMF(data->ibuf_const()->channels()[i], data->num_frames(),
                          bands->ibuf()->channels(0)[i],
                          bands->ibuf()->channels(1)[i],
                          two_bands_states_[i].analysis_state1,
                          two_bands_states_[i].analysis_state2);
  }
}

void SplittingFilter::TwoBandsSynthesis(const IFChannelBuffer* bands,
                                        IFChannelBuffer* data) {
  RTC_DCHECK_LE(data->num_channels(), two_bands_states_.size());
  for (size_t i = 0; i < data->num_channels(); ++i) {
    WebRtcSpl_SynthesisQMF(
        bands->ibuf_const()->channels(0)[i],
        bands->ibuf_const()->channels(1)[i], bands->num_frames_per_band(),
        data->ibuf()->channels()[i], two_bands_states_[i].synthesis_state1,
        two_bands_states_[i].synthesis_state2);
  }
}

void SplittingFilter::ThreeBandsAnalysis(const IFChannelBuffer* data,
                                         IFChannelBuffer* bands) {
  RTC_DCHECK_EQ(three_band_filter_banks_.size(), data->num_channels());
  for (size_t i = 0; i < three_band_filter_banks_.size(); ++i) {
    three_band_filter_banks_[i]->Analysis(data->fbuf_const()->channels()[i],
                                          data->num_frames(),
                                          bands->fbuf()->bands(i));
  }
}

void SplittingFilter::ThreeBandsSynthesis(const IFChannelBuffer* bands,
                                          IFChannelBuffer* data) {
  RTC_DCHECK_LE(data->num_channels(), three_band_filter_banks_.size());
  for (size_t i = 0; i < data->num_channels(); ++i) {
    three_band_filter_banks_[i]->Synthesis(bands->fbuf_const()->bands(i),
                                           bands->num_frames_per_band(),
                                           data->fbuf()->channels()[i]);
  }
}

}  // namespace webrtc
