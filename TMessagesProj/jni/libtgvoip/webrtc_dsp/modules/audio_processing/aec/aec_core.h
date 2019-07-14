/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

/*
 * Specifies the interface for the AEC core.
 */

#ifndef MODULES_AUDIO_PROCESSING_AEC_AEC_CORE_H_
#define MODULES_AUDIO_PROCESSING_AEC_AEC_CORE_H_

#include <stddef.h>

#include <memory>

extern "C" {
#include "common_audio/ring_buffer.h"
}
#include "modules/audio_processing/aec/aec_common.h"
#include "modules/audio_processing/utility/block_mean_calculator.h"
#include "modules/audio_processing/utility/ooura_fft.h"
#include "rtc_base/constructormagic.h"

namespace webrtc {

#define FRAME_LEN 80
#define PART_LEN 64               // Length of partition
#define PART_LEN1 (PART_LEN + 1)  // Unique fft coefficients
#define PART_LEN2 (PART_LEN * 2)  // Length of partition * 2
#define NUM_HIGH_BANDS_MAX 2      // Max number of high bands

class ApmDataDumper;

typedef float complex_t[2];
// For performance reasons, some arrays of complex numbers are replaced by twice
// as long arrays of float, all the real parts followed by all the imaginary
// ones (complex_t[SIZE] -> float[2][SIZE]). This allows SIMD optimizations and
// is better than two arrays (one for the real parts and one for the imaginary
// parts) as this other way would require two pointers instead of one and cause
// extra register spilling. This also allows the offsets to be calculated at
// compile time.

// Metrics
enum { kOffsetLevel = -100 };

typedef struct Stats {
  float instant;
  float average;
  float min;
  float max;
  float sum;
  float hisum;
  float himean;
  size_t counter;
  size_t hicounter;
} Stats;

// Number of partitions for the extended filter mode. The first one is an enum
// to be used in array declarations, as it represents the maximum filter length.
enum { kExtendedNumPartitions = 32 };
static const int kNormalNumPartitions = 12;

// Delay estimator constants, used for logging and delay compensation if
// if reported delays are disabled.
enum { kLookaheadBlocks = 15 };
enum {
  // 500 ms for 16 kHz which is equivalent with the limit of reported delays.
  kHistorySizeBlocks = 125
};

typedef struct PowerLevel {
  PowerLevel();

  BlockMeanCalculator framelevel;
  BlockMeanCalculator averagelevel;
  float minlevel;
} PowerLevel;

class BlockBuffer {
 public:
  BlockBuffer();
  ~BlockBuffer();
  void ReInit();
  void Insert(const float block[PART_LEN]);
  void ExtractExtendedBlock(float extended_block[PART_LEN]);
  int AdjustSize(int buffer_size_decrease);
  size_t Size();
  size_t AvaliableSpace();

 private:
  RingBuffer* buffer_;
};

class DivergentFilterFraction {
 public:
  DivergentFilterFraction();

  // Reset.
  void Reset();

  void AddObservation(const PowerLevel& nearlevel,
                      const PowerLevel& linoutlevel,
                      const PowerLevel& nlpoutlevel);

  // Return the latest fraction.
  float GetLatestFraction() const;

 private:
  // Clear all values added.
  void Clear();

  size_t count_;
  size_t occurrence_;
  float fraction_;

  RTC_DISALLOW_COPY_AND_ASSIGN(DivergentFilterFraction);
};

typedef struct CoherenceState {
  complex_t sde[PART_LEN1];  // cross-psd of nearend and error
  complex_t sxd[PART_LEN1];  // cross-psd of farend and nearend
  float sx[PART_LEN1], sd[PART_LEN1], se[PART_LEN1];  // far, near, error psd
} CoherenceState;

struct AecCore {
  explicit AecCore(int instance_index);
  ~AecCore();

  std::unique_ptr<ApmDataDumper> data_dumper;
  const OouraFft ooura_fft;

  CoherenceState coherence_state;

  int farBufWritePos, farBufReadPos;

  int knownDelay;
  int inSamples, outSamples;
  int delayEstCtr;

  // Nearend buffer used for changing from FRAME_LEN to PART_LEN sample block
  // sizes. The buffer stores all the incoming bands and for each band a maximum
  // of PART_LEN - (FRAME_LEN - PART_LEN) values need to be buffered in order to
  // change the block size from FRAME_LEN to PART_LEN.
  float nearend_buffer[NUM_HIGH_BANDS_MAX + 1]
                      [PART_LEN - (FRAME_LEN - PART_LEN)];
  size_t nearend_buffer_size;
  float output_buffer[NUM_HIGH_BANDS_MAX + 1][2 * PART_LEN];
  size_t output_buffer_size;

  float eBuf[PART_LEN2];  // error

  float previous_nearend_block[NUM_HIGH_BANDS_MAX + 1][PART_LEN];

  float xPow[PART_LEN1];
  float dPow[PART_LEN1];
  float dMinPow[PART_LEN1];
  float dInitMinPow[PART_LEN1];
  float* noisePow;

  float xfBuf[2][kExtendedNumPartitions * PART_LEN1];  // farend fft buffer
  float wfBuf[2][kExtendedNumPartitions * PART_LEN1];  // filter fft
  // Farend windowed fft buffer.
  complex_t xfwBuf[kExtendedNumPartitions * PART_LEN1];

  float hNs[PART_LEN1];
  float hNlFbMin, hNlFbLocalMin;
  float hNlXdAvgMin;
  int hNlNewMin, hNlMinCtr;
  float overDrive;
  float overdrive_scaling;
  int nlp_mode;
  float outBuf[PART_LEN];
  int delayIdx;

  short stNearState, echoState;
  short divergeState;

  int xfBufBlockPos;

  BlockBuffer farend_block_buffer_;

  int system_delay;  // Current system delay buffered in AEC.

  int mult;  // sampling frequency multiple
  int sampFreq = 16000;
  size_t num_bands;
  uint32_t seed;

  float filter_step_size;  // stepsize
  float error_threshold;   // error threshold

  int noiseEstCtr;

  PowerLevel farlevel;
  PowerLevel nearlevel;
  PowerLevel linoutlevel;
  PowerLevel nlpoutlevel;

  int metricsMode;
  int stateCounter;
  Stats erl;
  Stats erle;
  Stats aNlp;
  Stats rerl;
  DivergentFilterFraction divergent_filter_fraction;

  // Quantities to control H band scaling for SWB input
  int freq_avg_ic;       // initial bin for averaging nlp gain
  int flag_Hband_cn;     // for comfort noise
  float cn_scale_Hband;  // scale for comfort noise in H band

  int delay_metrics_delivered;
  int delay_histogram[kHistorySizeBlocks];
  int num_delay_values;
  int delay_median;
  int delay_std;
  float fraction_poor_delays;
  int delay_logging_enabled;
  void* delay_estimator_farend;
  void* delay_estimator;
  // Variables associated with delay correction through signal based delay
  // estimation feedback.
  int previous_delay;
  int delay_correction_count;
  int shift_offset;
  float delay_quality_threshold;
  int frame_count;

  // 0 = delay agnostic mode (signal based delay correction) disabled.
  // Otherwise enabled.
  int delay_agnostic_enabled;
  // 1 = extended filter mode enabled, 0 = disabled.
  int extended_filter_enabled;
  // 1 = refined filter adaptation aec mode enabled, 0 = disabled.
  bool refined_adaptive_filter_enabled;

  // Runtime selection of number of filter partitions.
  int num_partitions;

  // Flag that extreme filter divergence has been detected by the Echo
  // Suppressor.
  int extreme_filter_divergence;
};

AecCore* WebRtcAec_CreateAec(int instance_count);  // Returns NULL on error.
void WebRtcAec_FreeAec(AecCore* aec);
int WebRtcAec_InitAec(AecCore* aec, int sampFreq);
void WebRtcAec_InitAec_SSE2(void);
#if defined(MIPS_FPU_LE)
void WebRtcAec_InitAec_mips(void);
#endif
#if defined(WEBRTC_HAS_NEON)
void WebRtcAec_InitAec_neon(void);
#endif

void WebRtcAec_BufferFarendBlock(AecCore* aec, const float* farend);
void WebRtcAec_ProcessFrames(AecCore* aec,
                             const float* const* nearend,
                             size_t num_bands,
                             size_t num_samples,
                             int knownDelay,
                             float* const* out);

// A helper function to call adjust the farend buffer size.
// Returns the number of elements the size was decreased with, and adjusts
// |system_delay| by the corresponding amount in ms.
int WebRtcAec_AdjustFarendBufferSizeAndSystemDelay(AecCore* aec,
                                                   int size_decrease);

// Calculates the median, standard deviation and amount of poor values among the
// delay estimates aggregated up to the first call to the function. After that
// first call the metrics are aggregated and updated every second. With poor
// values we mean values that most likely will cause the AEC to perform poorly.
// TODO(bjornv): Consider changing tests and tools to handle constant
// constant aggregation window throughout the session instead.
int WebRtcAec_GetDelayMetricsCore(AecCore* self,
                                  int* median,
                                  int* std,
                                  float* fraction_poor_delays);

// Returns the echo state (1: echo, 0: no echo).
int WebRtcAec_echo_state(AecCore* self);

// Gets statistics of the echo metrics ERL, ERLE, A_NLP.
void WebRtcAec_GetEchoStats(AecCore* self,
                            Stats* erl,
                            Stats* erle,
                            Stats* a_nlp,
                            float* divergent_filter_fraction);

// Sets local configuration modes.
void WebRtcAec_SetConfigCore(AecCore* self,
                             int nlp_mode,
                             int metrics_mode,
                             int delay_logging);

// Non-zero enables, zero disables.
void WebRtcAec_enable_delay_agnostic(AecCore* self, int enable);

// Returns non-zero if delay agnostic (i.e., signal based delay estimation) is
// enabled and zero if disabled.
int WebRtcAec_delay_agnostic_enabled(AecCore* self);

// Turns on/off the refined adaptive filter feature.
void WebRtcAec_enable_refined_adaptive_filter(AecCore* self, bool enable);

// Returns whether the refined adaptive filter is enabled.
bool WebRtcAec_refined_adaptive_filter(const AecCore* self);

// Enables or disables extended filter mode. Non-zero enables, zero disables.
void WebRtcAec_enable_extended_filter(AecCore* self, int enable);

// Returns non-zero if extended filter mode is enabled and zero if disabled.
int WebRtcAec_extended_filter_enabled(AecCore* self);

// Returns the current |system_delay|, i.e., the buffered difference between
// far-end and near-end.
int WebRtcAec_system_delay(AecCore* self);

// Sets the |system_delay| to |value|.  Note that if the value is changed
// improperly, there can be a performance regression.  So it should be used with
// care.
void WebRtcAec_SetSystemDelay(AecCore* self, int delay);

}  // namespace webrtc

#endif  // MODULES_AUDIO_PROCESSING_AEC_AEC_CORE_H_
