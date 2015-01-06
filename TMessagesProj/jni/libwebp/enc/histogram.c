// Copyright 2012 Google Inc. All Rights Reserved.
//
// Use of this source code is governed by a BSD-style license
// that can be found in the COPYING file in the root of the source
// tree. An additional intellectual property rights grant can be found
// in the file PATENTS. All contributing project authors may
// be found in the AUTHORS file in the root of the source tree.
// -----------------------------------------------------------------------------
//
// Author: Jyrki Alakuijala (jyrki@google.com)
//
#ifdef HAVE_CONFIG_H
#include "../webp/config.h"
#endif

#include <math.h>

#include "./backward_references.h"
#include "./histogram.h"
#include "../dsp/lossless.h"
#include "../utils/utils.h"

#define MAX_COST 1.e38

// Number of partitions for the three dominant (literal, red and blue) symbol
// costs.
#define NUM_PARTITIONS 4
// The size of the bin-hash corresponding to the three dominant costs.
#define BIN_SIZE (NUM_PARTITIONS * NUM_PARTITIONS * NUM_PARTITIONS)

static void HistogramClear(VP8LHistogram* const p) {
  uint32_t* const literal = p->literal_;
  const int cache_bits = p->palette_code_bits_;
  const int histo_size = VP8LGetHistogramSize(cache_bits);
  memset(p, 0, histo_size);
  p->palette_code_bits_ = cache_bits;
  p->literal_ = literal;
}

static void HistogramCopy(const VP8LHistogram* const src,
                          VP8LHistogram* const dst) {
  uint32_t* const dst_literal = dst->literal_;
  const int dst_cache_bits = dst->palette_code_bits_;
  const int histo_size = VP8LGetHistogramSize(dst_cache_bits);
  assert(src->palette_code_bits_ == dst_cache_bits);
  memcpy(dst, src, histo_size);
  dst->literal_ = dst_literal;
}

int VP8LGetHistogramSize(int cache_bits) {
  const int literal_size = VP8LHistogramNumCodes(cache_bits);
  const size_t total_size = sizeof(VP8LHistogram) + sizeof(int) * literal_size;
  assert(total_size <= (size_t)0x7fffffff);
  return (int)total_size;
}

void VP8LFreeHistogram(VP8LHistogram* const histo) {
  WebPSafeFree(histo);
}

void VP8LFreeHistogramSet(VP8LHistogramSet* const histo) {
  WebPSafeFree(histo);
}

void VP8LHistogramStoreRefs(const VP8LBackwardRefs* const refs,
                            VP8LHistogram* const histo) {
  VP8LRefsCursor c = VP8LRefsCursorInit(refs);
  while (VP8LRefsCursorOk(&c)) {
    VP8LHistogramAddSinglePixOrCopy(histo, c.cur_pos);
    VP8LRefsCursorNext(&c);
  }
}

void VP8LHistogramCreate(VP8LHistogram* const p,
                         const VP8LBackwardRefs* const refs,
                         int palette_code_bits) {
  if (palette_code_bits >= 0) {
    p->palette_code_bits_ = palette_code_bits;
  }
  HistogramClear(p);
  VP8LHistogramStoreRefs(refs, p);
}

void VP8LHistogramInit(VP8LHistogram* const p, int palette_code_bits) {
  p->palette_code_bits_ = palette_code_bits;
  HistogramClear(p);
}

VP8LHistogram* VP8LAllocateHistogram(int cache_bits) {
  VP8LHistogram* histo = NULL;
  const int total_size = VP8LGetHistogramSize(cache_bits);
  uint8_t* const memory = (uint8_t*)WebPSafeMalloc(total_size, sizeof(*memory));
  if (memory == NULL) return NULL;
  histo = (VP8LHistogram*)memory;
  // literal_ won't necessary be aligned.
  histo->literal_ = (uint32_t*)(memory + sizeof(VP8LHistogram));
  VP8LHistogramInit(histo, cache_bits);
  return histo;
}

VP8LHistogramSet* VP8LAllocateHistogramSet(int size, int cache_bits) {
  int i;
  VP8LHistogramSet* set;
  const size_t total_size = sizeof(*set)
                            + sizeof(*set->histograms) * size
                            + (size_t)VP8LGetHistogramSize(cache_bits) * size;
  uint8_t* memory = (uint8_t*)WebPSafeMalloc(total_size, sizeof(*memory));
  if (memory == NULL) return NULL;

  set = (VP8LHistogramSet*)memory;
  memory += sizeof(*set);
  set->histograms = (VP8LHistogram**)memory;
  memory += size * sizeof(*set->histograms);
  set->max_size = size;
  set->size = size;
  for (i = 0; i < size; ++i) {
    set->histograms[i] = (VP8LHistogram*)memory;
    // literal_ won't necessary be aligned.
    set->histograms[i]->literal_ = (uint32_t*)(memory + sizeof(VP8LHistogram));
    VP8LHistogramInit(set->histograms[i], cache_bits);
    // There's no padding/alignment between successive histograms.
    memory += VP8LGetHistogramSize(cache_bits);
  }
  return set;
}

// -----------------------------------------------------------------------------

void VP8LHistogramAddSinglePixOrCopy(VP8LHistogram* const histo,
                                     const PixOrCopy* const v) {
  if (PixOrCopyIsLiteral(v)) {
    ++histo->alpha_[PixOrCopyLiteral(v, 3)];
    ++histo->red_[PixOrCopyLiteral(v, 2)];
    ++histo->literal_[PixOrCopyLiteral(v, 1)];
    ++histo->blue_[PixOrCopyLiteral(v, 0)];
  } else if (PixOrCopyIsCacheIdx(v)) {
    const int literal_ix =
        NUM_LITERAL_CODES + NUM_LENGTH_CODES + PixOrCopyCacheIdx(v);
    ++histo->literal_[literal_ix];
  } else {
    int code, extra_bits;
    VP8LPrefixEncodeBits(PixOrCopyLength(v), &code, &extra_bits);
    ++histo->literal_[NUM_LITERAL_CODES + code];
    VP8LPrefixEncodeBits(PixOrCopyDistance(v), &code, &extra_bits);
    ++histo->distance_[code];
  }
}

static WEBP_INLINE double BitsEntropyRefine(int nonzeros, int sum, int max_val,
                                            double retval) {
  double mix;
  if (nonzeros < 5) {
    if (nonzeros <= 1) {
      return 0;
    }
    // Two symbols, they will be 0 and 1 in a Huffman code.
    // Let's mix in a bit of entropy to favor good clustering when
    // distributions of these are combined.
    if (nonzeros == 2) {
      return 0.99 * sum + 0.01 * retval;
    }
    // No matter what the entropy says, we cannot be better than min_limit
    // with Huffman coding. I am mixing a bit of entropy into the
    // min_limit since it produces much better (~0.5 %) compression results
    // perhaps because of better entropy clustering.
    if (nonzeros == 3) {
      mix = 0.95;
    } else {
      mix = 0.7;  // nonzeros == 4.
    }
  } else {
    mix = 0.627;
  }

  {
    double min_limit = 2 * sum - max_val;
    min_limit = mix * min_limit + (1.0 - mix) * retval;
    return (retval < min_limit) ? min_limit : retval;
  }
}

static double BitsEntropy(const uint32_t* const array, int n) {
  double retval = 0.;
  uint32_t sum = 0;
  int nonzeros = 0;
  uint32_t max_val = 0;
  int i;
  for (i = 0; i < n; ++i) {
    if (array[i] != 0) {
      sum += array[i];
      ++nonzeros;
      retval -= VP8LFastSLog2(array[i]);
      if (max_val < array[i]) {
        max_val = array[i];
      }
    }
  }
  retval += VP8LFastSLog2(sum);
  return BitsEntropyRefine(nonzeros, sum, max_val, retval);
}

static double BitsEntropyCombined(const uint32_t* const X,
                                  const uint32_t* const Y, int n) {
  double retval = 0.;
  int sum = 0;
  int nonzeros = 0;
  int max_val = 0;
  int i;
  for (i = 0; i < n; ++i) {
    const int xy = X[i] + Y[i];
    if (xy != 0) {
      sum += xy;
      ++nonzeros;
      retval -= VP8LFastSLog2(xy);
      if (max_val < xy) {
        max_val = xy;
      }
    }
  }
  retval += VP8LFastSLog2(sum);
  return BitsEntropyRefine(nonzeros, sum, max_val, retval);
}

static double InitialHuffmanCost(void) {
  // Small bias because Huffman code length is typically not stored in
  // full length.
  static const int kHuffmanCodeOfHuffmanCodeSize = CODE_LENGTH_CODES * 3;
  static const double kSmallBias = 9.1;
  return kHuffmanCodeOfHuffmanCodeSize - kSmallBias;
}

// Finalize the Huffman cost based on streak numbers and length type (<3 or >=3)
static double FinalHuffmanCost(const VP8LStreaks* const stats) {
  double retval = InitialHuffmanCost();
  retval += stats->counts[0] * 1.5625 + 0.234375 * stats->streaks[0][1];
  retval += stats->counts[1] * 2.578125 + 0.703125 * stats->streaks[1][1];
  retval += 1.796875 * stats->streaks[0][0];
  retval += 3.28125 * stats->streaks[1][0];
  return retval;
}

// Trampolines
static double HuffmanCost(const uint32_t* const population, int length) {
  const VP8LStreaks stats = VP8LHuffmanCostCount(population, length);
  return FinalHuffmanCost(&stats);
}

static double HuffmanCostCombined(const uint32_t* const X,
                                  const uint32_t* const Y, int length) {
  const VP8LStreaks stats = VP8LHuffmanCostCombinedCount(X, Y, length);
  return FinalHuffmanCost(&stats);
}

// Aggregated costs
static double PopulationCost(const uint32_t* const population, int length) {
  return BitsEntropy(population, length) + HuffmanCost(population, length);
}

static double GetCombinedEntropy(const uint32_t* const X,
                                 const uint32_t* const Y, int length) {
  return BitsEntropyCombined(X, Y, length) + HuffmanCostCombined(X, Y, length);
}

// Estimates the Entropy + Huffman + other block overhead size cost.
double VP8LHistogramEstimateBits(const VP8LHistogram* const p) {
  return
      PopulationCost(p->literal_, VP8LHistogramNumCodes(p->palette_code_bits_))
      + PopulationCost(p->red_, NUM_LITERAL_CODES)
      + PopulationCost(p->blue_, NUM_LITERAL_CODES)
      + PopulationCost(p->alpha_, NUM_LITERAL_CODES)
      + PopulationCost(p->distance_, NUM_DISTANCE_CODES)
      + VP8LExtraCost(p->literal_ + NUM_LITERAL_CODES, NUM_LENGTH_CODES)
      + VP8LExtraCost(p->distance_, NUM_DISTANCE_CODES);
}

double VP8LHistogramEstimateBitsBulk(const VP8LHistogram* const p) {
  return
      BitsEntropy(p->literal_, VP8LHistogramNumCodes(p->palette_code_bits_))
      + BitsEntropy(p->red_, NUM_LITERAL_CODES)
      + BitsEntropy(p->blue_, NUM_LITERAL_CODES)
      + BitsEntropy(p->alpha_, NUM_LITERAL_CODES)
      + BitsEntropy(p->distance_, NUM_DISTANCE_CODES)
      + VP8LExtraCost(p->literal_ + NUM_LITERAL_CODES, NUM_LENGTH_CODES)
      + VP8LExtraCost(p->distance_, NUM_DISTANCE_CODES);
}

// -----------------------------------------------------------------------------
// Various histogram combine/cost-eval functions

static int GetCombinedHistogramEntropy(const VP8LHistogram* const a,
                                       const VP8LHistogram* const b,
                                       double cost_threshold,
                                       double* cost) {
  const int palette_code_bits = a->palette_code_bits_;
  assert(a->palette_code_bits_ == b->palette_code_bits_);
  *cost += GetCombinedEntropy(a->literal_, b->literal_,
                              VP8LHistogramNumCodes(palette_code_bits));
  *cost += VP8LExtraCostCombined(a->literal_ + NUM_LITERAL_CODES,
                                 b->literal_ + NUM_LITERAL_CODES,
                                 NUM_LENGTH_CODES);
  if (*cost > cost_threshold) return 0;

  *cost += GetCombinedEntropy(a->red_, b->red_, NUM_LITERAL_CODES);
  if (*cost > cost_threshold) return 0;

  *cost += GetCombinedEntropy(a->blue_, b->blue_, NUM_LITERAL_CODES);
  if (*cost > cost_threshold) return 0;

  *cost += GetCombinedEntropy(a->alpha_, b->alpha_, NUM_LITERAL_CODES);
  if (*cost > cost_threshold) return 0;

  *cost += GetCombinedEntropy(a->distance_, b->distance_, NUM_DISTANCE_CODES);
  *cost += VP8LExtraCostCombined(a->distance_, b->distance_,
                                 NUM_DISTANCE_CODES);
  if (*cost > cost_threshold) return 0;

  return 1;
}

// Performs out = a + b, computing the cost C(a+b) - C(a) - C(b) while comparing
// to the threshold value 'cost_threshold'. The score returned is
//  Score = C(a+b) - C(a) - C(b), where C(a) + C(b) is known and fixed.
// Since the previous score passed is 'cost_threshold', we only need to compare
// the partial cost against 'cost_threshold + C(a) + C(b)' to possibly bail-out
// early.
static double HistogramAddEval(const VP8LHistogram* const a,
                               const VP8LHistogram* const b,
                               VP8LHistogram* const out,
                               double cost_threshold) {
  double cost = 0;
  const double sum_cost = a->bit_cost_ + b->bit_cost_;
  cost_threshold += sum_cost;

  if (GetCombinedHistogramEntropy(a, b, cost_threshold, &cost)) {
    VP8LHistogramAdd(a, b, out);
    out->bit_cost_ = cost;
    out->palette_code_bits_ = a->palette_code_bits_;
  }

  return cost - sum_cost;
}

// Same as HistogramAddEval(), except that the resulting histogram
// is not stored. Only the cost C(a+b) - C(a) is evaluated. We omit
// the term C(b) which is constant over all the evaluations.
static double HistogramAddThresh(const VP8LHistogram* const a,
                                 const VP8LHistogram* const b,
                                 double cost_threshold) {
  double cost = -a->bit_cost_;
  GetCombinedHistogramEntropy(a, b, cost_threshold, &cost);
  return cost;
}

// -----------------------------------------------------------------------------

// The structure to keep track of cost range for the three dominant entropy
// symbols.
// TODO(skal): Evaluate if float can be used here instead of double for
// representing the entropy costs.
typedef struct {
  double literal_max_;
  double literal_min_;
  double red_max_;
  double red_min_;
  double blue_max_;
  double blue_min_;
} DominantCostRange;

static void DominantCostRangeInit(DominantCostRange* const c) {
  c->literal_max_ = 0.;
  c->literal_min_ = MAX_COST;
  c->red_max_ = 0.;
  c->red_min_ = MAX_COST;
  c->blue_max_ = 0.;
  c->blue_min_ = MAX_COST;
}

static void UpdateDominantCostRange(
    const VP8LHistogram* const h, DominantCostRange* const c) {
  if (c->literal_max_ < h->literal_cost_) c->literal_max_ = h->literal_cost_;
  if (c->literal_min_ > h->literal_cost_) c->literal_min_ = h->literal_cost_;
  if (c->red_max_ < h->red_cost_) c->red_max_ = h->red_cost_;
  if (c->red_min_ > h->red_cost_) c->red_min_ = h->red_cost_;
  if (c->blue_max_ < h->blue_cost_) c->blue_max_ = h->blue_cost_;
  if (c->blue_min_ > h->blue_cost_) c->blue_min_ = h->blue_cost_;
}

static void UpdateHistogramCost(VP8LHistogram* const h) {
  const double alpha_cost = PopulationCost(h->alpha_, NUM_LITERAL_CODES);
  const double distance_cost =
      PopulationCost(h->distance_, NUM_DISTANCE_CODES) +
      VP8LExtraCost(h->distance_, NUM_DISTANCE_CODES);
  const int num_codes = VP8LHistogramNumCodes(h->palette_code_bits_);
  h->literal_cost_ = PopulationCost(h->literal_, num_codes) +
                     VP8LExtraCost(h->literal_ + NUM_LITERAL_CODES,
                                   NUM_LENGTH_CODES);
  h->red_cost_ = PopulationCost(h->red_, NUM_LITERAL_CODES);
  h->blue_cost_ = PopulationCost(h->blue_, NUM_LITERAL_CODES);
  h->bit_cost_ = h->literal_cost_ + h->red_cost_ + h->blue_cost_ +
                 alpha_cost + distance_cost;
}

static int GetBinIdForEntropy(double min, double max, double val) {
  const double range = max - min + 1e-6;
  const double delta = val - min;
  return (int)(NUM_PARTITIONS * delta / range);
}

// TODO(vikasa): Evaluate, if there's any correlation between red & blue.
static int GetHistoBinIndex(
    const VP8LHistogram* const h, const DominantCostRange* const c) {
  const int bin_id =
      GetBinIdForEntropy(c->blue_min_, c->blue_max_, h->blue_cost_) +
      NUM_PARTITIONS * GetBinIdForEntropy(c->red_min_, c->red_max_,
                                          h->red_cost_) +
      NUM_PARTITIONS * NUM_PARTITIONS * GetBinIdForEntropy(c->literal_min_,
                                                           c->literal_max_,
                                                           h->literal_cost_);
  assert(bin_id < BIN_SIZE);
  return bin_id;
}

// Construct the histograms from backward references.
static void HistogramBuild(
    int xsize, int histo_bits, const VP8LBackwardRefs* const backward_refs,
    VP8LHistogramSet* const image_histo) {
  int x = 0, y = 0;
  const int histo_xsize = VP8LSubSampleSize(xsize, histo_bits);
  VP8LHistogram** const histograms = image_histo->histograms;
  VP8LRefsCursor c = VP8LRefsCursorInit(backward_refs);
  assert(histo_bits > 0);
  // Construct the Histo from a given backward references.
  while (VP8LRefsCursorOk(&c)) {
    const PixOrCopy* const v = c.cur_pos;
    const int ix = (y >> histo_bits) * histo_xsize + (x >> histo_bits);
    VP8LHistogramAddSinglePixOrCopy(histograms[ix], v);
    x += PixOrCopyLength(v);
    while (x >= xsize) {
      x -= xsize;
      ++y;
    }
    VP8LRefsCursorNext(&c);
  }
}

// Copies the histograms and computes its bit_cost.
static void HistogramCopyAndAnalyze(
    VP8LHistogramSet* const orig_histo, VP8LHistogramSet* const image_histo) {
  int i;
  const int histo_size = orig_histo->size;
  VP8LHistogram** const orig_histograms = orig_histo->histograms;
  VP8LHistogram** const histograms = image_histo->histograms;
  for (i = 0; i < histo_size; ++i) {
    VP8LHistogram* const histo = orig_histograms[i];
    UpdateHistogramCost(histo);
    // Copy histograms from orig_histo[] to image_histo[].
    HistogramCopy(histo, histograms[i]);
  }
}

// Partition histograms to different entropy bins for three dominant (literal,
// red and blue) symbol costs and compute the histogram aggregate bit_cost.
static void HistogramAnalyzeEntropyBin(
    VP8LHistogramSet* const image_histo, int16_t* const bin_map) {
  int i;
  VP8LHistogram** const histograms = image_histo->histograms;
  const int histo_size = image_histo->size;
  const int bin_depth = histo_size + 1;
  DominantCostRange cost_range;
  DominantCostRangeInit(&cost_range);

  // Analyze the dominant (literal, red and blue) entropy costs.
  for (i = 0; i < histo_size; ++i) {
    VP8LHistogram* const histo = histograms[i];
    UpdateDominantCostRange(histo, &cost_range);
  }

  // bin-hash histograms on three of the dominant (literal, red and blue)
  // symbol costs.
  for (i = 0; i < histo_size; ++i) {
    int num_histos;
    VP8LHistogram* const histo = histograms[i];
    const int16_t bin_id = (int16_t)GetHistoBinIndex(histo, &cost_range);
    const int bin_offset = bin_id * bin_depth;
    // bin_map[n][0] for every bin 'n' maintains the counter for the number of
    // histograms in that bin.
    // Get and increment the num_histos in that bin.
    num_histos = ++bin_map[bin_offset];
    assert(bin_offset + num_histos < bin_depth * BIN_SIZE);
    // Add histogram i'th index at num_histos (last) position in the bin_map.
    bin_map[bin_offset + num_histos] = i;
  }
}

// Compact the histogram set by moving the valid one left in the set to the
// head and moving the ones that have been merged to other histograms towards
// the end.
// TODO(vikasa): Evaluate if this method can be avoided by altering the code
// logic of HistogramCombineEntropyBin main loop.
static void HistogramCompactBins(VP8LHistogramSet* const image_histo) {
  int start = 0;
  int end = image_histo->size - 1;
  VP8LHistogram** const histograms = image_histo->histograms;
  while (start < end) {
    while (start <= end && histograms[start] != NULL &&
           histograms[start]->bit_cost_ != 0.) {
      ++start;
    }
    while (start <= end && histograms[end]->bit_cost_ == 0.) {
      histograms[end] = NULL;
      --end;
    }
    if (start < end) {
      assert(histograms[start] != NULL);
      assert(histograms[end] != NULL);
      HistogramCopy(histograms[end], histograms[start]);
      histograms[end] = NULL;
      --end;
    }
  }
  image_histo->size = end + 1;
}

static void HistogramCombineEntropyBin(VP8LHistogramSet* const image_histo,
                                       VP8LHistogram* const histos,
                                       int16_t* const bin_map, int bin_depth,
                                       double combine_cost_factor) {
  int bin_id;
  VP8LHistogram* cur_combo = histos;
  VP8LHistogram** const histograms = image_histo->histograms;

  for (bin_id = 0; bin_id < BIN_SIZE; ++bin_id) {
    const int bin_offset = bin_id * bin_depth;
    const int num_histos = bin_map[bin_offset];
    const int idx1 = bin_map[bin_offset + 1];
    int n;
    for (n = 2; n <= num_histos; ++n) {
      const int idx2 = bin_map[bin_offset + n];
      const double bit_cost_idx2 = histograms[idx2]->bit_cost_;
      if (bit_cost_idx2 > 0.) {
        const double bit_cost_thresh = -bit_cost_idx2 * combine_cost_factor;
        const double curr_cost_diff =
            HistogramAddEval(histograms[idx1], histograms[idx2],
                             cur_combo, bit_cost_thresh);
        if (curr_cost_diff < bit_cost_thresh) {
          HistogramCopy(cur_combo, histograms[idx1]);
          histograms[idx2]->bit_cost_ = 0.;
        }
      }
    }
  }
  HistogramCompactBins(image_histo);
}

static uint32_t MyRand(uint32_t *seed) {
  *seed *= 16807U;
  if (*seed == 0) {
    *seed = 1;
  }
  return *seed;
}

static void HistogramCombine(VP8LHistogramSet* const image_histo,
                             VP8LHistogramSet* const histos, int quality) {
  int iter;
  uint32_t seed = 0;
  int tries_with_no_success = 0;
  int image_histo_size = image_histo->size;
  const int iter_mult = (quality < 25) ? 2 : 2 + (quality - 25) / 8;
  const int outer_iters = image_histo_size * iter_mult;
  const int num_pairs = image_histo_size / 2;
  const int num_tries_no_success = outer_iters / 2;
  const int min_cluster_size = 2;
  VP8LHistogram** const histograms = image_histo->histograms;
  VP8LHistogram* cur_combo = histos->histograms[0];   // trial histogram
  VP8LHistogram* best_combo = histos->histograms[1];  // best histogram so far

  // Collapse similar histograms in 'image_histo'.
  for (iter = 0;
       iter < outer_iters && image_histo_size >= min_cluster_size;
       ++iter) {
    double best_cost_diff = 0.;
    int best_idx1 = -1, best_idx2 = 1;
    int j;
    const int num_tries =
        (num_pairs < image_histo_size) ? num_pairs : image_histo_size;
    seed += iter;
    for (j = 0; j < num_tries; ++j) {
      double curr_cost_diff;
      // Choose two histograms at random and try to combine them.
      const uint32_t idx1 = MyRand(&seed) % image_histo_size;
      const uint32_t tmp = (j & 7) + 1;
      const uint32_t diff =
          (tmp < 3) ? tmp : MyRand(&seed) % (image_histo_size - 1);
      const uint32_t idx2 = (idx1 + diff + 1) % image_histo_size;
      if (idx1 == idx2) {
        continue;
      }

      // Calculate cost reduction on combining.
      curr_cost_diff = HistogramAddEval(histograms[idx1], histograms[idx2],
                                        cur_combo, best_cost_diff);
      if (curr_cost_diff < best_cost_diff) {    // found a better pair?
        {     // swap cur/best combo histograms
          VP8LHistogram* const tmp_histo = cur_combo;
          cur_combo = best_combo;
          best_combo = tmp_histo;
        }
        best_cost_diff = curr_cost_diff;
        best_idx1 = idx1;
        best_idx2 = idx2;
      }
    }

    if (best_idx1 >= 0) {
      HistogramCopy(best_combo, histograms[best_idx1]);
      // swap best_idx2 slot with last one (which is now unused)
      --image_histo_size;
      if (best_idx2 != image_histo_size) {
        HistogramCopy(histograms[image_histo_size], histograms[best_idx2]);
        histograms[image_histo_size] = NULL;
      }
      tries_with_no_success = 0;
    }
    if (++tries_with_no_success >= num_tries_no_success) {
      break;
    }
  }
  image_histo->size = image_histo_size;
}

// -----------------------------------------------------------------------------
// Histogram refinement

// Find the best 'out' histogram for each of the 'in' histograms.
// Note: we assume that out[]->bit_cost_ is already up-to-date.
static void HistogramRemap(const VP8LHistogramSet* const orig_histo,
                           const VP8LHistogramSet* const image_histo,
                           uint16_t* const symbols) {
  int i;
  VP8LHistogram** const orig_histograms = orig_histo->histograms;
  VP8LHistogram** const histograms = image_histo->histograms;
  for (i = 0; i < orig_histo->size; ++i) {
    int best_out = 0;
    double best_bits =
        HistogramAddThresh(histograms[0], orig_histograms[i], MAX_COST);
    int k;
    for (k = 1; k < image_histo->size; ++k) {
      const double cur_bits =
          HistogramAddThresh(histograms[k], orig_histograms[i], best_bits);
      if (cur_bits < best_bits) {
        best_bits = cur_bits;
        best_out = k;
      }
    }
    symbols[i] = best_out;
  }

  // Recompute each out based on raw and symbols.
  for (i = 0; i < image_histo->size; ++i) {
    HistogramClear(histograms[i]);
  }

  for (i = 0; i < orig_histo->size; ++i) {
    const int idx = symbols[i];
    VP8LHistogramAdd(orig_histograms[i], histograms[idx], histograms[idx]);
  }
}

static double GetCombineCostFactor(int histo_size, int quality) {
  double combine_cost_factor = 0.16;
  if (histo_size > 256) combine_cost_factor /= 2.;
  if (histo_size > 512) combine_cost_factor /= 2.;
  if (histo_size > 1024) combine_cost_factor /= 2.;
  if (quality <= 50) combine_cost_factor /= 2.;
  return combine_cost_factor;
}

int VP8LGetHistoImageSymbols(int xsize, int ysize,
                             const VP8LBackwardRefs* const refs,
                             int quality, int histo_bits, int cache_bits,
                             VP8LHistogramSet* const image_histo,
                             uint16_t* const histogram_symbols) {
  int ok = 0;
  const int histo_xsize = histo_bits ? VP8LSubSampleSize(xsize, histo_bits) : 1;
  const int histo_ysize = histo_bits ? VP8LSubSampleSize(ysize, histo_bits) : 1;
  const int image_histo_raw_size = histo_xsize * histo_ysize;

  // The bin_map for every bin follows following semantics:
  // bin_map[n][0] = num_histo; // The number of histograms in that bin.
  // bin_map[n][1] = index of first histogram in that bin;
  // bin_map[n][num_histo] = index of last histogram in that bin;
  // bin_map[n][num_histo + 1] ... bin_map[n][bin_depth - 1] = un-used indices.
  const int bin_depth = image_histo_raw_size + 1;
  int16_t* bin_map = NULL;
  VP8LHistogramSet* const histos = VP8LAllocateHistogramSet(2, cache_bits);
  VP8LHistogramSet* const orig_histo =
      VP8LAllocateHistogramSet(image_histo_raw_size, cache_bits);

  if (orig_histo == NULL || histos == NULL) {
    goto Error;
  }

  // Don't attempt linear bin-partition heuristic for:
  // histograms of small sizes, as bin_map will be very sparse and;
  // Higher qualities (> 90), to preserve the compression gains at those
  // quality settings.
  if (orig_histo->size > 2 * BIN_SIZE && quality < 90) {
    const int bin_map_size = bin_depth * BIN_SIZE;
    bin_map = (int16_t*)WebPSafeCalloc(bin_map_size, sizeof(*bin_map));
    if (bin_map == NULL) goto Error;
  }

  // Construct the histograms from backward references.
  HistogramBuild(xsize, histo_bits, refs, orig_histo);
  // Copies the histograms and computes its bit_cost.
  HistogramCopyAndAnalyze(orig_histo, image_histo);

  if (bin_map != NULL) {
    const double combine_cost_factor =
        GetCombineCostFactor(image_histo_raw_size, quality);
    HistogramAnalyzeEntropyBin(orig_histo, bin_map);
    // Collapse histograms with similar entropy.
    HistogramCombineEntropyBin(image_histo, histos->histograms[0],
                               bin_map, bin_depth, combine_cost_factor);
  }

  // Collapse similar histograms by random histogram-pair compares.
  HistogramCombine(image_histo, histos, quality);

  // Find the optimal map from original histograms to the final ones.
  HistogramRemap(orig_histo, image_histo, histogram_symbols);

  ok = 1;

 Error:
  WebPSafeFree(bin_map);
  VP8LFreeHistogramSet(orig_histo);
  VP8LFreeHistogramSet(histos);
  return ok;
}
