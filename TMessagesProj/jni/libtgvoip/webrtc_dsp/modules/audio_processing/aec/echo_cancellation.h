/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef MODULES_AUDIO_PROCESSING_AEC_ECHO_CANCELLATION_H_
#define MODULES_AUDIO_PROCESSING_AEC_ECHO_CANCELLATION_H_

#include <memory>

#include <stddef.h>

extern "C" {
#include "common_audio/ring_buffer.h"
}
#include "modules/audio_processing/aec/aec_core.h"

namespace webrtc {

// Errors
#define AEC_UNSPECIFIED_ERROR 12000
#define AEC_UNSUPPORTED_FUNCTION_ERROR 12001
#define AEC_UNINITIALIZED_ERROR 12002
#define AEC_NULL_POINTER_ERROR 12003
#define AEC_BAD_PARAMETER_ERROR 12004

// Warnings
#define AEC_BAD_PARAMETER_WARNING 12050

enum { kAecNlpConservative = 0, kAecNlpModerate, kAecNlpAggressive };

enum { kAecFalse = 0, kAecTrue };

typedef struct {
  int16_t nlpMode;      // default kAecNlpModerate
  int16_t skewMode;     // default kAecFalse
  int16_t metricsMode;  // default kAecFalse
  int delay_logging;    // default kAecFalse
  // float realSkew;
} AecConfig;

typedef struct {
  int instant;
  int average;
  int max;
  int min;
} AecLevel;

typedef struct {
  AecLevel rerl;
  AecLevel erl;
  AecLevel erle;
  AecLevel aNlp;
  float divergent_filter_fraction;
} AecMetrics;

struct AecCore;

class ApmDataDumper;

typedef struct Aec {
  Aec();
  ~Aec();

  std::unique_ptr<ApmDataDumper> data_dumper;

  int delayCtr;
  int sampFreq;
  int splitSampFreq;
  int scSampFreq;
  float sampFactor;  // scSampRate / sampFreq
  short skewMode;
  int bufSizeStart;
  int knownDelay;
  int rate_factor;

  short initFlag;  // indicates if AEC has been initialized

  // Variables used for averaging far end buffer size
  short counter;
  int sum;
  short firstVal;
  short checkBufSizeCtr;

  // Variables used for delay shifts
  short msInSndCardBuf;
  short filtDelay;  // Filtered delay estimate.
  int timeForDelayChange;
  int startup_phase;
  int checkBuffSize;
  short lastDelayDiff;

  // Structures
  void* resampler;

  int skewFrCtr;
  int resample;  // if the skew is small enough we don't resample
  int highSkewCtr;
  float skew;

  RingBuffer* far_pre_buf;  // Time domain far-end pre-buffer.

  int farend_started;

  // Aec instance counter.
  static int instance_count;
  AecCore* aec;
} Aec;

/*
 * Allocates the memory needed by the AEC. The memory needs to be initialized
 * separately using the WebRtcAec_Init() function. Returns a pointer to the
 * object or NULL on error.
 */
void* WebRtcAec_Create();

/*
 * This function releases the memory allocated by WebRtcAec_Create().
 *
 * Inputs                       Description
 * -------------------------------------------------------------------
 * void*        aecInst         Pointer to the AEC instance
 */
void WebRtcAec_Free(void* aecInst);

/*
 * Initializes an AEC instance.
 *
 * Inputs                       Description
 * -------------------------------------------------------------------
 * void*          aecInst       Pointer to the AEC instance
 * int32_t        sampFreq      Sampling frequency of data
 * int32_t        scSampFreq    Soundcard sampling frequency
 *
 * Outputs                      Description
 * -------------------------------------------------------------------
 * int32_t        return        0: OK
 *                             -1: error
 */
int32_t WebRtcAec_Init(void* aecInst, int32_t sampFreq, int32_t scSampFreq);

/*
 * Inserts an 80 or 160 sample block of data into the farend buffer.
 *
 * Inputs                       Description
 * -------------------------------------------------------------------
 * void*          aecInst       Pointer to the AEC instance
 * const float*   farend        In buffer containing one frame of
 *                              farend signal for L band
 * int16_t        nrOfSamples   Number of samples in farend buffer
 *
 * Outputs                      Description
 * -------------------------------------------------------------------
 * int32_t        return        0: OK
 *                              12000-12050: error code
 */
int32_t WebRtcAec_BufferFarend(void* aecInst,
                               const float* farend,
                               size_t nrOfSamples);

/*
 * Reports any errors that would arise if buffering a farend buffer
 *
 * Inputs                       Description
 * -------------------------------------------------------------------
 * void*          aecInst       Pointer to the AEC instance
 * const float*   farend        In buffer containing one frame of
 *                              farend signal for L band
 * int16_t        nrOfSamples   Number of samples in farend buffer
 *
 * Outputs                      Description
 * -------------------------------------------------------------------
 * int32_t        return        0: OK
 *                              12000-12050: error code
 */
int32_t WebRtcAec_GetBufferFarendError(void* aecInst,
                                       const float* farend,
                                       size_t nrOfSamples);

/*
 * Runs the echo canceller on an 80 or 160 sample blocks of data.
 *
 * Inputs                       Description
 * -------------------------------------------------------------------
 * void*         aecInst        Pointer to the AEC instance
 * float* const* nearend        In buffer containing one frame of
 *                              nearend+echo signal for each band
 * int           num_bands      Number of bands in nearend buffer
 * int16_t       nrOfSamples    Number of samples in nearend buffer
 * int16_t       msInSndCardBuf Delay estimate for sound card and
 *                              system buffers
 * int16_t       skew           Difference between number of samples played
 *                              and recorded at the soundcard (for clock skew
 *                              compensation)
 *
 * Outputs                      Description
 * -------------------------------------------------------------------
 * float* const* out            Out buffer, one frame of processed nearend
 *                              for each band
 * int32_t       return         0: OK
 *                              12000-12050: error code
 */
int32_t WebRtcAec_Process(void* aecInst,
                          const float* const* nearend,
                          size_t num_bands,
                          float* const* out,
                          size_t nrOfSamples,
                          int16_t msInSndCardBuf,
                          int32_t skew);

/*
 * This function enables the user to set certain parameters on-the-fly.
 *
 * Inputs                       Description
 * -------------------------------------------------------------------
 * void*          handle        Pointer to the AEC instance
 * AecConfig      config        Config instance that contains all
 *                              properties to be set
 *
 * Outputs                      Description
 * -------------------------------------------------------------------
 * int            return        0: OK
 *                              12000-12050: error code
 */
int WebRtcAec_set_config(void* handle, AecConfig config);

/*
 * Gets the current echo status of the nearend signal.
 *
 * Inputs                       Description
 * -------------------------------------------------------------------
 * void*          handle        Pointer to the AEC instance
 *
 * Outputs                      Description
 * -------------------------------------------------------------------
 * int*           status        0: Almost certainly nearend single-talk
 *                              1: Might not be neared single-talk
 * int            return        0: OK
 *                              12000-12050: error code
 */
int WebRtcAec_get_echo_status(void* handle, int* status);

/*
 * Gets the current echo metrics for the session.
 *
 * Inputs                       Description
 * -------------------------------------------------------------------
 * void*          handle        Pointer to the AEC instance
 *
 * Outputs                      Description
 * -------------------------------------------------------------------
 * AecMetrics*    metrics       Struct which will be filled out with the
 *                              current echo metrics.
 * int            return        0: OK
 *                              12000-12050: error code
 */
int WebRtcAec_GetMetrics(void* handle, AecMetrics* metrics);

/*
 * Gets the current delay metrics for the session.
 *
 * Inputs                       Description
 * -------------------------------------------------------------------
 * void*   handle               Pointer to the AEC instance
 *
 * Outputs                      Description
 * -------------------------------------------------------------------
 * int*    median               Delay median value.
 * int*    std                  Delay standard deviation.
 * float*  fraction_poor_delays Fraction of the delay estimates that may
 *                              cause the AEC to perform poorly.
 *
 * int            return        0: OK
 *                              12000-12050: error code
 */
int WebRtcAec_GetDelayMetrics(void* handle,
                              int* median,
                              int* std,
                              float* fraction_poor_delays);

// Returns a pointer to the low level AEC handle.
//
// Input:
//  - handle                    : Pointer to the AEC instance.
//
// Return value:
//  - AecCore pointer           : NULL for error.
//
struct AecCore* WebRtcAec_aec_core(void* handle);

}  // namespace webrtc

#endif  // MODULES_AUDIO_PROCESSING_AEC_ECHO_CANCELLATION_H_
