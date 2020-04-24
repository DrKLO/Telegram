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
 * Contains the API functions for the AEC.
 */
#include "modules/audio_processing/aec/echo_cancellation.h"

#include <math.h>
#include <stdlib.h>
#include <string.h>

extern "C" {
#include "common_audio/ring_buffer.h"
#include "common_audio/signal_processing/include/signal_processing_library.h"
}
#include "modules/audio_processing/aec/aec_core.h"
#include "modules/audio_processing/aec/aec_resampler.h"
#include "modules/audio_processing/logging/apm_data_dumper.h"

namespace webrtc {

Aec::Aec() = default;
Aec::~Aec() = default;

// Measured delays [ms]
// Device                Chrome  GTP
// MacBook Air           10
// MacBook Retina        10      100
// MacPro                30?
//
// Win7 Desktop          70      80?
// Win7 T430s            110
// Win8 T420s            70
//
// Daisy                 50
// Pixel (w/ preproc?)           240
// Pixel (w/o preproc?)  110     110

// The extended filter mode gives us the flexibility to ignore the system's
// reported delays. We do this for platforms which we believe provide results
// which are incompatible with the AEC's expectations. Based on measurements
// (some provided above) we set a conservative (i.e. lower than measured)
// fixed delay.
//
// WEBRTC_UNTRUSTED_DELAY will only have an impact when |extended_filter_mode|
// is enabled. See the note along with |DelayCorrection| in
// echo_cancellation_impl.h for more details on the mode.
//
// Justification:
// Chromium/Mac: Here, the true latency is so low (~10-20 ms), that it plays
// havoc with the AEC's buffering. To avoid this, we set a fixed delay of 20 ms
// and then compensate by rewinding by 10 ms (in wideband) through
// kDelayDiffOffsetSamples. This trick does not seem to work for larger rewind
// values, but fortunately this is sufficient.
//
// Chromium/Linux(ChromeOS): The values we get on this platform don't correspond
// well to reality. The variance doesn't match the AEC's buffer changes, and the
// bulk values tend to be too low. However, the range across different hardware
// appears to be too large to choose a single value.
//
// GTP/Linux(ChromeOS): TBD, but for the moment we will trust the values.
#if defined(WEBRTC_CHROMIUM_BUILD) && defined(WEBRTC_MAC)
#define WEBRTC_UNTRUSTED_DELAY
#endif

#if defined(WEBRTC_UNTRUSTED_DELAY) && defined(WEBRTC_MAC)
static const int kDelayDiffOffsetSamples = -160;
#else
// Not enabled for now.
static const int kDelayDiffOffsetSamples = 0;
#endif

#if defined(WEBRTC_MAC)
static const int kFixedDelayMs = 20;
#else
static const int kFixedDelayMs = 50;
#endif
#if !defined(WEBRTC_UNTRUSTED_DELAY)
static const int kMinTrustedDelayMs = 20;
#endif
static const int kMaxTrustedDelayMs = 500;

// Maximum length of resampled signal. Must be an integer multiple of frames
// (ceil(1/(1 + MIN_SKEW)*2) + 1)*FRAME_LEN
// The factor of 2 handles wb, and the + 1 is as a safety margin
// TODO(bjornv): Replace with kResamplerBufferSize
#define MAX_RESAMP_LEN (5 * FRAME_LEN)

static const int kMaxBufSizeStart = 62;  // In partitions
static const int sampMsNb = 8;           // samples per ms in nb
static const int initCheck = 42;

int Aec::instance_count = 0;

// Estimates delay to set the position of the far-end buffer read pointer
// (controlled by knownDelay)
static void EstBufDelayNormal(Aec* aecInst);
static void EstBufDelayExtended(Aec* aecInst);
static int ProcessNormal(Aec* aecInst,
                         const float* const* nearend,
                         size_t num_bands,
                         float* const* out,
                         size_t num_samples,
                         int16_t reported_delay_ms,
                         int32_t skew);
static void ProcessExtended(Aec* aecInst,
                            const float* const* nearend,
                            size_t num_bands,
                            float* const* out,
                            size_t num_samples,
                            int16_t reported_delay_ms,
                            int32_t skew);

void* WebRtcAec_Create() {
  Aec* aecpc = new Aec();

  if (!aecpc) {
    return NULL;
  }
  aecpc->data_dumper.reset(new ApmDataDumper(aecpc->instance_count));

  aecpc->aec = WebRtcAec_CreateAec(aecpc->instance_count);
  if (!aecpc->aec) {
    WebRtcAec_Free(aecpc);
    return NULL;
  }
  aecpc->resampler = WebRtcAec_CreateResampler();
  if (!aecpc->resampler) {
    WebRtcAec_Free(aecpc);
    return NULL;
  }
  // Create far-end pre-buffer. The buffer size has to be large enough for
  // largest possible drift compensation (kResamplerBufferSize) + "almost" an
  // FFT buffer (PART_LEN2 - 1).
  aecpc->far_pre_buf =
      WebRtc_CreateBuffer(PART_LEN2 + kResamplerBufferSize, sizeof(float));
  if (!aecpc->far_pre_buf) {
    WebRtcAec_Free(aecpc);
    return NULL;
  }

  aecpc->initFlag = 0;

  aecpc->instance_count++;
  return aecpc;
}

void WebRtcAec_Free(void* aecInst) {
  Aec* aecpc = reinterpret_cast<Aec*>(aecInst);

  if (aecpc == NULL) {
    return;
  }

  WebRtc_FreeBuffer(aecpc->far_pre_buf);

  WebRtcAec_FreeAec(aecpc->aec);
  WebRtcAec_FreeResampler(aecpc->resampler);
  delete aecpc;
}

int32_t WebRtcAec_Init(void* aecInst, int32_t sampFreq, int32_t scSampFreq) {
  Aec* aecpc = reinterpret_cast<Aec*>(aecInst);
  aecpc->data_dumper->InitiateNewSetOfRecordings();
  AecConfig aecConfig;

  if (sampFreq != 8000 && sampFreq != 16000 && sampFreq != 32000 &&
      sampFreq != 48000) {
    return AEC_BAD_PARAMETER_ERROR;
  }
  aecpc->sampFreq = sampFreq;

  if (scSampFreq < 1 || scSampFreq > 96000) {
    return AEC_BAD_PARAMETER_ERROR;
  }
  aecpc->scSampFreq = scSampFreq;

  // Initialize echo canceller core
  if (WebRtcAec_InitAec(aecpc->aec, aecpc->sampFreq) == -1) {
    return AEC_UNSPECIFIED_ERROR;
  }

  if (WebRtcAec_InitResampler(aecpc->resampler, aecpc->scSampFreq) == -1) {
    return AEC_UNSPECIFIED_ERROR;
  }

  WebRtc_InitBuffer(aecpc->far_pre_buf);
  WebRtc_MoveReadPtr(aecpc->far_pre_buf, -PART_LEN);  // Start overlap.

  aecpc->initFlag = initCheck;  // indicates that initialization has been done

  if (aecpc->sampFreq == 32000 || aecpc->sampFreq == 48000) {
    aecpc->splitSampFreq = 16000;
  } else {
    aecpc->splitSampFreq = sampFreq;
  }

  aecpc->delayCtr = 0;
  aecpc->sampFactor = (aecpc->scSampFreq * 1.0f) / aecpc->splitSampFreq;
  // Sampling frequency multiplier (SWB is processed as 160 frame size).
  aecpc->rate_factor = aecpc->splitSampFreq / 8000;

  aecpc->sum = 0;
  aecpc->counter = 0;
  aecpc->checkBuffSize = 1;
  aecpc->firstVal = 0;

  // We skip the startup_phase completely (setting to 0) if DA-AEC is enabled,
  // but not extended_filter mode.
  aecpc->startup_phase = WebRtcAec_extended_filter_enabled(aecpc->aec) ||
                         !WebRtcAec_delay_agnostic_enabled(aecpc->aec);
  aecpc->bufSizeStart = 0;
  aecpc->checkBufSizeCtr = 0;
  aecpc->msInSndCardBuf = 0;
  aecpc->filtDelay = -1;  // -1 indicates an initialized state.
  aecpc->timeForDelayChange = 0;
  aecpc->knownDelay = 0;
  aecpc->lastDelayDiff = 0;

  aecpc->skewFrCtr = 0;
  aecpc->resample = kAecFalse;
  aecpc->highSkewCtr = 0;
  aecpc->skew = 0;

  aecpc->farend_started = 0;

  // Default settings.
  aecConfig.nlpMode = kAecNlpModerate;
  aecConfig.skewMode = kAecFalse;
  aecConfig.metricsMode = kAecFalse;
  aecConfig.delay_logging = kAecFalse;

  if (WebRtcAec_set_config(aecpc, aecConfig) == -1) {
    return AEC_UNSPECIFIED_ERROR;
  }

  return 0;
}

// Returns any error that is caused when buffering the
// far-end signal.
int32_t WebRtcAec_GetBufferFarendError(void* aecInst,
                                       const float* farend,
                                       size_t nrOfSamples) {
  Aec* aecpc = reinterpret_cast<Aec*>(aecInst);

  if (!farend)
    return AEC_NULL_POINTER_ERROR;

  if (aecpc->initFlag != initCheck)
    return AEC_UNINITIALIZED_ERROR;

  // number of samples == 160 for SWB input
  if (nrOfSamples != 80 && nrOfSamples != 160)
    return AEC_BAD_PARAMETER_ERROR;

  return 0;
}

// only buffer L band for farend
int32_t WebRtcAec_BufferFarend(void* aecInst,
                               const float* farend,
                               size_t nrOfSamples) {
  Aec* aecpc = reinterpret_cast<Aec*>(aecInst);
  size_t newNrOfSamples = nrOfSamples;
  float new_farend[MAX_RESAMP_LEN];
  const float* farend_ptr = farend;

  // Get any error caused by buffering the farend signal.
  int32_t error_code =
      WebRtcAec_GetBufferFarendError(aecInst, farend, nrOfSamples);

  if (error_code != 0)
    return error_code;

  if (aecpc->skewMode == kAecTrue && aecpc->resample == kAecTrue) {
    // Resample and get a new number of samples
    WebRtcAec_ResampleLinear(aecpc->resampler, farend, nrOfSamples, aecpc->skew,
                             new_farend, &newNrOfSamples);
    farend_ptr = new_farend;
  }

  aecpc->farend_started = 1;
  WebRtcAec_SetSystemDelay(aecpc->aec, WebRtcAec_system_delay(aecpc->aec) +
                                           static_cast<int>(newNrOfSamples));

  // Write the time-domain data to |far_pre_buf|.
  WebRtc_WriteBuffer(aecpc->far_pre_buf, farend_ptr, newNrOfSamples);

  // TODO(minyue): reduce to |PART_LEN| samples for each buffering.
  while (WebRtc_available_read(aecpc->far_pre_buf) >= PART_LEN2) {
    // We have enough data to pass to the FFT, hence read PART_LEN2 samples.
    {
      float* ptmp = NULL;
      float tmp[PART_LEN2];
      WebRtc_ReadBuffer(aecpc->far_pre_buf, reinterpret_cast<void**>(&ptmp),
                        tmp, PART_LEN2);
      WebRtcAec_BufferFarendBlock(aecpc->aec, &ptmp[PART_LEN]);
    }

    // Rewind |far_pre_buf| PART_LEN samples for overlap before continuing.
    WebRtc_MoveReadPtr(aecpc->far_pre_buf, -PART_LEN);
  }

  return 0;
}

int32_t WebRtcAec_Process(void* aecInst,
                          const float* const* nearend,
                          size_t num_bands,
                          float* const* out,
                          size_t nrOfSamples,
                          int16_t msInSndCardBuf,
                          int32_t skew) {
  Aec* aecpc = reinterpret_cast<Aec*>(aecInst);
  int32_t retVal = 0;

  if (out == NULL) {
    return AEC_NULL_POINTER_ERROR;
  }

  if (aecpc->initFlag != initCheck) {
    return AEC_UNINITIALIZED_ERROR;
  }

  // number of samples == 160 for SWB input
  if (nrOfSamples != 80 && nrOfSamples != 160) {
    return AEC_BAD_PARAMETER_ERROR;
  }

  if (msInSndCardBuf < 0) {
    msInSndCardBuf = 0;
    retVal = AEC_BAD_PARAMETER_WARNING;
  } else if (msInSndCardBuf > kMaxTrustedDelayMs) {
    // The clamping is now done in ProcessExtended/Normal().
    retVal = AEC_BAD_PARAMETER_WARNING;
  }

  // This returns the value of aec->extended_filter_enabled.
  if (WebRtcAec_extended_filter_enabled(aecpc->aec)) {
    ProcessExtended(aecpc, nearend, num_bands, out, nrOfSamples, msInSndCardBuf,
                    skew);
  } else {
    retVal = ProcessNormal(aecpc, nearend, num_bands, out, nrOfSamples,
                           msInSndCardBuf, skew);
  }

  int far_buf_size_samples = WebRtcAec_system_delay(aecpc->aec);
  aecpc->data_dumper->DumpRaw("aec_system_delay", 1, &far_buf_size_samples);
  aecpc->data_dumper->DumpRaw("aec_known_delay", 1, &aecpc->knownDelay);

  return retVal;
}

int WebRtcAec_set_config(void* handle, AecConfig config) {
  Aec* self = reinterpret_cast<Aec*>(handle);
  if (self->initFlag != initCheck) {
    return AEC_UNINITIALIZED_ERROR;
  }

  if (config.skewMode != kAecFalse && config.skewMode != kAecTrue) {
    return AEC_BAD_PARAMETER_ERROR;
  }
  self->skewMode = config.skewMode;

  if (config.nlpMode != kAecNlpConservative &&
      config.nlpMode != kAecNlpModerate &&
      config.nlpMode != kAecNlpAggressive) {
    return AEC_BAD_PARAMETER_ERROR;
  }

  if (config.metricsMode != kAecFalse && config.metricsMode != kAecTrue) {
    return AEC_BAD_PARAMETER_ERROR;
  }

  if (config.delay_logging != kAecFalse && config.delay_logging != kAecTrue) {
    return AEC_BAD_PARAMETER_ERROR;
  }

  WebRtcAec_SetConfigCore(self->aec, config.nlpMode, config.metricsMode,
                          config.delay_logging);
  return 0;
}

int WebRtcAec_get_echo_status(void* handle, int* status) {
  Aec* self = reinterpret_cast<Aec*>(handle);
  if (status == NULL) {
    return AEC_NULL_POINTER_ERROR;
  }
  if (self->initFlag != initCheck) {
    return AEC_UNINITIALIZED_ERROR;
  }

  *status = WebRtcAec_echo_state(self->aec);

  return 0;
}

int WebRtcAec_GetMetrics(void* handle, AecMetrics* metrics) {
  const float kUpWeight = 0.7f;
  float dtmp;
  int stmp;
  Aec* self = reinterpret_cast<Aec*>(handle);
  Stats erl;
  Stats erle;
  Stats a_nlp;

  if (handle == NULL) {
    return -1;
  }
  if (metrics == NULL) {
    return AEC_NULL_POINTER_ERROR;
  }
  if (self->initFlag != initCheck) {
    return AEC_UNINITIALIZED_ERROR;
  }

  WebRtcAec_GetEchoStats(self->aec, &erl, &erle, &a_nlp,
                         &metrics->divergent_filter_fraction);

  // ERL
  metrics->erl.instant = static_cast<int>(erl.instant);

  if ((erl.himean > kOffsetLevel) && (erl.average > kOffsetLevel)) {
    // Use a mix between regular average and upper part average.
    dtmp = kUpWeight * erl.himean + (1 - kUpWeight) * erl.average;
    metrics->erl.average = static_cast<int>(dtmp);
  } else {
    metrics->erl.average = kOffsetLevel;
  }

  metrics->erl.max = static_cast<int>(erl.max);

  if (erl.min < (kOffsetLevel * (-1))) {
    metrics->erl.min = static_cast<int>(erl.min);
  } else {
    metrics->erl.min = kOffsetLevel;
  }

  // ERLE
  metrics->erle.instant = static_cast<int>(erle.instant);

  if ((erle.himean > kOffsetLevel) && (erle.average > kOffsetLevel)) {
    // Use a mix between regular average and upper part average.
    dtmp = kUpWeight * erle.himean + (1 - kUpWeight) * erle.average;
    metrics->erle.average = static_cast<int>(dtmp);
  } else {
    metrics->erle.average = kOffsetLevel;
  }

  metrics->erle.max = static_cast<int>(erle.max);

  if (erle.min < (kOffsetLevel * (-1))) {
    metrics->erle.min = static_cast<int>(erle.min);
  } else {
    metrics->erle.min = kOffsetLevel;
  }

  // RERL
  if ((metrics->erl.average > kOffsetLevel) &&
      (metrics->erle.average > kOffsetLevel)) {
    stmp = metrics->erl.average + metrics->erle.average;
  } else {
    stmp = kOffsetLevel;
  }
  metrics->rerl.average = stmp;

  // No other statistics needed, but returned for completeness.
  metrics->rerl.instant = stmp;
  metrics->rerl.max = stmp;
  metrics->rerl.min = stmp;

  // A_NLP
  metrics->aNlp.instant = static_cast<int>(a_nlp.instant);

  if ((a_nlp.himean > kOffsetLevel) && (a_nlp.average > kOffsetLevel)) {
    // Use a mix between regular average and upper part average.
    dtmp = kUpWeight * a_nlp.himean + (1 - kUpWeight) * a_nlp.average;
    metrics->aNlp.average = static_cast<int>(dtmp);
  } else {
    metrics->aNlp.average = kOffsetLevel;
  }

  metrics->aNlp.max = static_cast<int>(a_nlp.max);

  if (a_nlp.min < (kOffsetLevel * (-1))) {
    metrics->aNlp.min = static_cast<int>(a_nlp.min);
  } else {
    metrics->aNlp.min = kOffsetLevel;
  }

  return 0;
}

int WebRtcAec_GetDelayMetrics(void* handle,
                              int* median,
                              int* std,
                              float* fraction_poor_delays) {
  Aec* self = reinterpret_cast<Aec*>(handle);
  if (median == NULL) {
    return AEC_NULL_POINTER_ERROR;
  }
  if (std == NULL) {
    return AEC_NULL_POINTER_ERROR;
  }
  if (self->initFlag != initCheck) {
    return AEC_UNINITIALIZED_ERROR;
  }
  if (WebRtcAec_GetDelayMetricsCore(self->aec, median, std,
                                    fraction_poor_delays) == -1) {
    // Logging disabled.
    return AEC_UNSUPPORTED_FUNCTION_ERROR;
  }

  return 0;
}

AecCore* WebRtcAec_aec_core(void* handle) {
  if (!handle) {
    return NULL;
  }
  return reinterpret_cast<Aec*>(handle)->aec;
}

static int ProcessNormal(Aec* aecInst,
                         const float* const* nearend,
                         size_t num_bands,
                         float* const* out,
                         size_t num_samples,
                         int16_t reported_delay_ms,
                         int32_t skew) {
  int retVal = 0;
  size_t i;
  size_t nBlocks10ms;
  // Limit resampling to doubling/halving of signal
  const float minSkewEst = -0.5f;
  const float maxSkewEst = 1.0f;

  reported_delay_ms = reported_delay_ms > kMaxTrustedDelayMs
                          ? kMaxTrustedDelayMs
                          : reported_delay_ms;
  // TODO(andrew): we need to investigate if this +10 is really wanted.
  reported_delay_ms += 10;
  aecInst->msInSndCardBuf = reported_delay_ms;

  if (aecInst->skewMode == kAecTrue) {
    if (aecInst->skewFrCtr < 25) {
      aecInst->skewFrCtr++;
    } else {
      retVal = WebRtcAec_GetSkew(aecInst->resampler, skew, &aecInst->skew);
      if (retVal == -1) {
        aecInst->skew = 0;
        retVal = AEC_BAD_PARAMETER_WARNING;
      }

      aecInst->skew /= aecInst->sampFactor * num_samples;

      if (aecInst->skew < 1.0e-3 && aecInst->skew > -1.0e-3) {
        aecInst->resample = kAecFalse;
      } else {
        aecInst->resample = kAecTrue;
      }

      if (aecInst->skew < minSkewEst) {
        aecInst->skew = minSkewEst;
      } else if (aecInst->skew > maxSkewEst) {
        aecInst->skew = maxSkewEst;
      }

      aecInst->data_dumper->DumpRaw("aec_skew", 1, &aecInst->skew);
    }
  }

  nBlocks10ms = num_samples / (FRAME_LEN * aecInst->rate_factor);

  if (aecInst->startup_phase) {
    for (i = 0; i < num_bands; ++i) {
      // Only needed if they don't already point to the same place.
      if (nearend[i] != out[i]) {
        memcpy(out[i], nearend[i], sizeof(nearend[i][0]) * num_samples);
      }
    }

    // The AEC is in the start up mode
    // AEC is disabled until the system delay is OK

    // Mechanism to ensure that the system delay is reasonably stable.
    if (aecInst->checkBuffSize) {
      aecInst->checkBufSizeCtr++;
      // Before we fill up the far-end buffer we require the system delay
      // to be stable (+/-8 ms) compared to the first value. This
      // comparison is made during the following 6 consecutive 10 ms
      // blocks. If it seems to be stable then we start to fill up the
      // far-end buffer.
      if (aecInst->counter == 0) {
        aecInst->firstVal = aecInst->msInSndCardBuf;
        aecInst->sum = 0;
      }

      if (abs(aecInst->firstVal - aecInst->msInSndCardBuf) <
          WEBRTC_SPL_MAX(0.2 * aecInst->msInSndCardBuf, sampMsNb)) {
        aecInst->sum += aecInst->msInSndCardBuf;
        aecInst->counter++;
      } else {
        aecInst->counter = 0;
      }

      if (aecInst->counter * nBlocks10ms >= 6) {
        // The far-end buffer size is determined in partitions of
        // PART_LEN samples. Use 75% of the average value of the system
        // delay as buffer size to start with.
        aecInst->bufSizeStart =
            WEBRTC_SPL_MIN((3 * aecInst->sum * aecInst->rate_factor * 8) /
                               (4 * aecInst->counter * PART_LEN),
                           kMaxBufSizeStart);
        // Buffer size has now been determined.
        aecInst->checkBuffSize = 0;
      }

      if (aecInst->checkBufSizeCtr * nBlocks10ms > 50) {
        // For really bad systems, don't disable the echo canceller for
        // more than 0.5 sec.
        aecInst->bufSizeStart = WEBRTC_SPL_MIN(
            (aecInst->msInSndCardBuf * aecInst->rate_factor * 3) / 40,
            kMaxBufSizeStart);
        aecInst->checkBuffSize = 0;
      }
    }

    // If |checkBuffSize| changed in the if-statement above.
    if (!aecInst->checkBuffSize) {
      // The system delay is now reasonably stable (or has been unstable
      // for too long). When the far-end buffer is filled with
      // approximately the same amount of data as reported by the system
      // we end the startup phase.
      int overhead_elements = WebRtcAec_system_delay(aecInst->aec) / PART_LEN -
                              aecInst->bufSizeStart;
      if (overhead_elements == 0) {
        // Enable the AEC
        aecInst->startup_phase = 0;
      } else if (overhead_elements > 0) {
        // TODO(bjornv): Do we need a check on how much we actually
        // moved the read pointer? It should always be possible to move
        // the pointer |overhead_elements| since we have only added data
        // to the buffer and no delay compensation nor AEC processing
        // has been done.
        WebRtcAec_AdjustFarendBufferSizeAndSystemDelay(aecInst->aec,
                                                       overhead_elements);

        // Enable the AEC
        aecInst->startup_phase = 0;
      }
    }
  } else {
    // AEC is enabled.
    EstBufDelayNormal(aecInst);

    // Call the AEC.
    // TODO(bjornv): Re-structure such that we don't have to pass
    // |aecInst->knownDelay| as input. Change name to something like
    // |system_buffer_diff|.
    WebRtcAec_ProcessFrames(aecInst->aec, nearend, num_bands, num_samples,
                            aecInst->knownDelay, out);
  }

  return retVal;
}

static void ProcessExtended(Aec* self,
                            const float* const* nearend,
                            size_t num_bands,
                            float* const* out,
                            size_t num_samples,
                            int16_t reported_delay_ms,
                            int32_t skew) {
  size_t i;
  const int delay_diff_offset = kDelayDiffOffsetSamples;
  RTC_DCHECK(num_samples == 80 || num_samples == 160);
#if defined(WEBRTC_UNTRUSTED_DELAY)
  reported_delay_ms = kFixedDelayMs;
#else
  // This is the usual mode where we trust the reported system delay values.
  // Due to the longer filter, we no longer add 10 ms to the reported delay
  // to reduce chance of non-causality. Instead we apply a minimum here to avoid
  // issues with the read pointer jumping around needlessly.
  reported_delay_ms = reported_delay_ms < kMinTrustedDelayMs
                          ? kMinTrustedDelayMs
                          : reported_delay_ms;
  // If the reported delay appears to be bogus, we attempt to recover by using
  // the measured fixed delay values. We use >= here because higher layers
  // may already clamp to this maximum value, and we would otherwise not
  // detect it here.
  reported_delay_ms = reported_delay_ms >= kMaxTrustedDelayMs
                          ? kFixedDelayMs
                          : reported_delay_ms;
#endif
  self->msInSndCardBuf = reported_delay_ms;

  if (!self->farend_started) {
    for (i = 0; i < num_bands; ++i) {
      // Only needed if they don't already point to the same place.
      if (nearend[i] != out[i]) {
        memcpy(out[i], nearend[i], sizeof(nearend[i][0]) * num_samples);
      }
    }
    return;
  }
  if (self->startup_phase) {
    // In the extended mode, there isn't a startup "phase", just a special
    // action on the first frame. In the trusted delay case, we'll take the
    // current reported delay, unless it's less then our conservative
    // measurement.
    int startup_size_ms =
        reported_delay_ms < kFixedDelayMs ? kFixedDelayMs : reported_delay_ms;
#if defined(WEBRTC_ANDROID)
    int target_delay = startup_size_ms * self->rate_factor * 8;
#else
    // To avoid putting the AEC in a non-causal state we're being slightly
    // conservative and scale by 2. On Android we use a fixed delay and
    // therefore there is no need to scale the target_delay.
    int target_delay = startup_size_ms * self->rate_factor * 8 / 2;
#endif
    int overhead_elements =
        (WebRtcAec_system_delay(self->aec) - target_delay) / PART_LEN;
    WebRtcAec_AdjustFarendBufferSizeAndSystemDelay(self->aec,
                                                   overhead_elements);
    self->startup_phase = 0;
  }

  EstBufDelayExtended(self);

  {
    // |delay_diff_offset| gives us the option to manually rewind the delay on
    // very low delay platforms which can't be expressed purely through
    // |reported_delay_ms|.
    const int adjusted_known_delay =
        WEBRTC_SPL_MAX(0, self->knownDelay + delay_diff_offset);

    WebRtcAec_ProcessFrames(self->aec, nearend, num_bands, num_samples,
                            adjusted_known_delay, out);
  }
}

static void EstBufDelayNormal(Aec* aecInst) {
  int nSampSndCard = aecInst->msInSndCardBuf * sampMsNb * aecInst->rate_factor;
  int current_delay = nSampSndCard - WebRtcAec_system_delay(aecInst->aec);
  int delay_difference = 0;

  // Before we proceed with the delay estimate filtering we:
  // 1) Compensate for the frame that will be read.
  // 2) Compensate for drift resampling.
  // 3) Compensate for non-causality if needed, since the estimated delay can't
  //    be negative.

  // 1) Compensating for the frame(s) that will be read/processed.
  current_delay += FRAME_LEN * aecInst->rate_factor;

  // 2) Account for resampling frame delay.
  if (aecInst->skewMode == kAecTrue && aecInst->resample == kAecTrue) {
    current_delay -= kResamplingDelay;
  }

  // 3) Compensate for non-causality, if needed, by flushing one block.
  if (current_delay < PART_LEN) {
    current_delay +=
        WebRtcAec_AdjustFarendBufferSizeAndSystemDelay(aecInst->aec, 1) *
        PART_LEN;
  }

  // We use -1 to signal an initialized state in the "extended" implementation;
  // compensate for that.
  aecInst->filtDelay = aecInst->filtDelay < 0 ? 0 : aecInst->filtDelay;
  aecInst->filtDelay = WEBRTC_SPL_MAX(
      0, static_cast<int16_t>(0.8 * aecInst->filtDelay + 0.2 * current_delay));

  delay_difference = aecInst->filtDelay - aecInst->knownDelay;
  if (delay_difference > 224) {
    if (aecInst->lastDelayDiff < 96) {
      aecInst->timeForDelayChange = 0;
    } else {
      aecInst->timeForDelayChange++;
    }
  } else if (delay_difference < 96 && aecInst->knownDelay > 0) {
    if (aecInst->lastDelayDiff > 224) {
      aecInst->timeForDelayChange = 0;
    } else {
      aecInst->timeForDelayChange++;
    }
  } else {
    aecInst->timeForDelayChange = 0;
  }
  aecInst->lastDelayDiff = delay_difference;

  if (aecInst->timeForDelayChange > 25) {
    aecInst->knownDelay = WEBRTC_SPL_MAX((int)aecInst->filtDelay - 160, 0);
  }
}

static void EstBufDelayExtended(Aec* aecInst) {
  int reported_delay =
      aecInst->msInSndCardBuf * sampMsNb * aecInst->rate_factor;
  int current_delay = reported_delay - WebRtcAec_system_delay(aecInst->aec);
  int delay_difference = 0;

  // Before we proceed with the delay estimate filtering we:
  // 1) Compensate for the frame that will be read.
  // 2) Compensate for drift resampling.
  // 3) Compensate for non-causality if needed, since the estimated delay can't
  //    be negative.

  // 1) Compensating for the frame(s) that will be read/processed.
  current_delay += FRAME_LEN * aecInst->rate_factor;

  // 2) Account for resampling frame delay.
  if (aecInst->skewMode == kAecTrue && aecInst->resample == kAecTrue) {
    current_delay -= kResamplingDelay;
  }

  // 3) Compensate for non-causality, if needed, by flushing two blocks.
  if (current_delay < PART_LEN) {
    current_delay +=
        WebRtcAec_AdjustFarendBufferSizeAndSystemDelay(aecInst->aec, 2) *
        PART_LEN;
  }

  if (aecInst->filtDelay == -1) {
    aecInst->filtDelay = WEBRTC_SPL_MAX(0, 0.5 * current_delay);
  } else {
    aecInst->filtDelay = WEBRTC_SPL_MAX(
        0,
        static_cast<int16_t>(0.95 * aecInst->filtDelay + 0.05 * current_delay));
  }

  delay_difference = aecInst->filtDelay - aecInst->knownDelay;
  if (delay_difference > 384) {
    if (aecInst->lastDelayDiff < 128) {
      aecInst->timeForDelayChange = 0;
    } else {
      aecInst->timeForDelayChange++;
    }
  } else if (delay_difference < 128 && aecInst->knownDelay > 0) {
    if (aecInst->lastDelayDiff > 384) {
      aecInst->timeForDelayChange = 0;
    } else {
      aecInst->timeForDelayChange++;
    }
  } else {
    aecInst->timeForDelayChange = 0;
  }
  aecInst->lastDelayDiff = delay_difference;

  if (aecInst->timeForDelayChange > 25) {
    aecInst->knownDelay = WEBRTC_SPL_MAX((int)aecInst->filtDelay - 256, 0);
  }
}
}  // namespace webrtc
