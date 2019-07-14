/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef MODULES_AUDIO_PROCESSING_NS_NOISE_SUPPRESSION_X_H_
#define MODULES_AUDIO_PROCESSING_NS_NOISE_SUPPRESSION_X_H_

#include <stddef.h>
#include <stdint.h>

typedef struct NsxHandleT NsxHandle;

#ifdef __cplusplus
extern "C" {
#endif

/*
 * This function creates an instance of the fixed point Noise Suppression.
 */
NsxHandle* WebRtcNsx_Create(void);

/*
 * This function frees the dynamic memory of a specified Noise Suppression
 * instance.
 *
 * Input:
 *      - nsxInst       : Pointer to NS instance that should be freed
 */
void WebRtcNsx_Free(NsxHandle* nsxInst);

/*
 * This function initializes a NS instance
 *
 * Input:
 *      - nsxInst       : Instance that should be initialized
 *      - fs            : sampling frequency
 *
 * Output:
 *      - nsxInst       : Initialized instance
 *
 * Return value         :  0 - Ok
 *                        -1 - Error
 */
int WebRtcNsx_Init(NsxHandle* nsxInst, uint32_t fs);

/*
 * This changes the aggressiveness of the noise suppression method.
 *
 * Input:
 *      - nsxInst       : Instance that should be initialized
 *      - mode          : 0: Mild, 1: Medium , 2: Aggressive
 *
 * Output:
 *      - nsxInst       : Initialized instance
 *
 * Return value         :  0 - Ok
 *                        -1 - Error
 */
int WebRtcNsx_set_policy(NsxHandle* nsxInst, int mode);

/*
 * This functions does noise suppression for the inserted speech frame. The
 * input and output signals should always be 10ms (80 or 160 samples).
 *
 * Input
 *      - nsxInst       : NSx instance. Needs to be initiated before call.
 *      - speechFrame   : Pointer to speech frame buffer for each band
 *      - num_bands     : Number of bands
 *
 * Output:
 *      - nsxInst       : Updated NSx instance
 *      - outFrame      : Pointer to output frame for each band
 */
void WebRtcNsx_Process(NsxHandle* nsxInst,
                       const short* const* speechFrame,
                       int num_bands,
                       short* const* outFrame);

/* Returns a pointer to the noise estimate per frequency bin. The number of
 * frequency bins can be provided using WebRtcNsx_num_freq().
 *
 * Input
 *      - nsxInst       : NSx instance. Needs to be initiated before call.
 *      - q_noise       : Q value of the noise estimate, which is the number of
 *                        bits that it needs to be right-shifted to be
 *                        normalized.
 *
 * Return value         : Pointer to the noise estimate per frequency bin.
 *                        Returns NULL if the input is a NULL pointer or an
 *                        uninitialized instance.
 */
const uint32_t* WebRtcNsx_noise_estimate(const NsxHandle* nsxInst,
                                         int* q_noise);

/* Returns the number of frequency bins, which is the length of the noise
 * estimate for example.
 *
 * Return value         : Number of frequency bins.
 */
size_t WebRtcNsx_num_freq(void);

#ifdef __cplusplus
}
#endif

#endif  // MODULES_AUDIO_PROCESSING_NS_NOISE_SUPPRESSION_X_H_
