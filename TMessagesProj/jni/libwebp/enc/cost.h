// Copyright 2011 Google Inc. All Rights Reserved.
//
// Use of this source code is governed by a BSD-style license
// that can be found in the COPYING file in the root of the source
// tree. An additional intellectual property rights grant can be found
// in the file PATENTS. All contributing project authors may
// be found in the AUTHORS file in the root of the source tree.
// -----------------------------------------------------------------------------
//
// Cost tables for level and modes.
//
// Author: Skal (pascal.massimino@gmail.com)

#ifndef WEBP_ENC_COST_H_
#define WEBP_ENC_COST_H_

#include <assert.h>
#include <stdlib.h>
#include "./vp8enci.h"

#ifdef __cplusplus
extern "C" {
#endif

// On-the-fly info about the current set of residuals. Handy to avoid
// passing zillions of params.
typedef struct {
  int first;
  int last;
  const int16_t* coeffs;

  int coeff_type;
  ProbaArray* prob;
  StatsArray* stats;
  CostArray*  cost;
} VP8Residual;

void VP8InitResidual(int first, int coeff_type,
                     VP8Encoder* const enc, VP8Residual* const res);

typedef void (*VP8SetResidualCoeffsFunc)(const int16_t* const coeffs,
                                         VP8Residual* const res);
extern VP8SetResidualCoeffsFunc VP8SetResidualCoeffs;

void VP8SetResidualCoeffsInit(void);  // must be called first

int VP8RecordCoeffs(int ctx, const VP8Residual* const res);

// approximate cost per level:
extern const uint16_t VP8LevelFixedCosts[MAX_LEVEL + 1];
extern const uint16_t VP8EntropyCost[256];        // 8bit fixed-point log(p)

// Cost of coding one event with probability 'proba'.
static WEBP_INLINE int VP8BitCost(int bit, uint8_t proba) {
  return !bit ? VP8EntropyCost[proba] : VP8EntropyCost[255 - proba];
}

// Cost calculation function.
typedef int (*VP8GetResidualCostFunc)(int ctx0, const VP8Residual* const res);
extern VP8GetResidualCostFunc VP8GetResidualCost;

void VP8GetResidualCostInit(void);  // must be called first

// Level cost calculations
extern const uint16_t VP8LevelCodes[MAX_VARIABLE_LEVEL][2];
void VP8CalculateLevelCosts(VP8Proba* const proba);
static WEBP_INLINE int VP8LevelCost(const uint16_t* const table, int level) {
  return VP8LevelFixedCosts[level]
       + table[(level > MAX_VARIABLE_LEVEL) ? MAX_VARIABLE_LEVEL : level];
}

// Mode costs
extern const uint16_t VP8FixedCostsUV[4];
extern const uint16_t VP8FixedCostsI16[4];
extern const uint16_t VP8FixedCostsI4[NUM_BMODES][NUM_BMODES][NUM_BMODES];

//------------------------------------------------------------------------------

#ifdef __cplusplus
}    // extern "C"
#endif

#endif  /* WEBP_ENC_COST_H_ */
