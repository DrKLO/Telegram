/*
 *  Copyright (c) 2011 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef MODULES_AUDIO_CODING_CODECS_ISAC_MAIN_UTIL_UTILITY_H_
#define MODULES_AUDIO_CODING_CODECS_ISAC_MAIN_UTIL_UTILITY_H_

#include <stdio.h>
#include <stdlib.h>

#if defined(__cplusplus)
extern "C" {
#endif

#define OPEN_FILE_WB(filePtr, fullPath)                     \
  do {                                                      \
    if (fullPath != NULL) {                                 \
      filePtr = fopen(fullPath, "wb");                      \
      if (filePtr == NULL) {                                \
        printf("could not open %s to write to.", fullPath); \
        return -1;                                          \
      }                                                     \
    } else {                                                \
      filePtr = NULL;                                       \
    }                                                       \
  } while (0)

#define OPEN_FILE_AB(filePtr, fullPath)                     \
  do {                                                      \
    if (fullPath != NULL) {                                 \
      filePtr = fopen(fullPath, "ab");                      \
      if (filePtr == NULL) {                                \
        printf("could not open %s to write to.", fullPath); \
        return -1;                                          \
      }                                                     \
    } else {                                                \
      filePtr = NULL;                                       \
    }                                                       \
  } while (0)

#define OPEN_FILE_RB(filePtr, fullPath)                      \
  do {                                                       \
    if (fullPath != NULL) {                                  \
      filePtr = fopen(fullPath, "rb");                       \
      if (filePtr == NULL) {                                 \
        printf("could not open %s to read from.", fullPath); \
        return -1;                                           \
      }                                                      \
    } else {                                                 \
      filePtr = NULL;                                        \
    }                                                        \
  } while (0)

#define WRITE_FILE_D(bufferPtr, len, filePtr)      \
  do {                                             \
    if (filePtr != NULL) {                         \
      double dummy[1000];                          \
      int cntr;                                    \
      for (cntr = 0; cntr < (len); cntr++) {       \
        dummy[cntr] = (double)bufferPtr[cntr];     \
      }                                            \
      fwrite(dummy, sizeof(double), len, filePtr); \
      fflush(filePtr);                             \
    }                                              \
  } while (0)

typedef struct {
  unsigned int whenPackGeneratedMs;
  unsigned int whenPrevPackLeftMs;
  unsigned int sendTimeMs;   /* milisecond */
  unsigned int arrival_time; /* samples */
  unsigned int sample_count; /* samples, also used as "send time stamp" */
  unsigned int rtp_number;
} BottleNeckModel;

void get_arrival_time(int current_framesamples, /* samples */
                      size_t packet_size,       /* bytes */
                      int bottleneck,           /* excluding headers; bits/s */
                      BottleNeckModel* BN_data,
                      short senderSampFreqHz,
                      short receiverSampFreqHz);

/* function for reading audio data from PCM file */
int readframe(short* data, FILE* inp, int length);

short readSwitch(int argc, char* argv[], char* strID);

double readParamDouble(int argc, char* argv[], char* strID, double defaultVal);

int readParamInt(int argc, char* argv[], char* strID, int defaultVal);

int readParamString(int argc,
                    char* argv[],
                    char* strID,
                    char* stringParam,
                    int maxSize);

#if defined(__cplusplus)
}
#endif

#endif
