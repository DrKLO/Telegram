/*
 * jcdctmgr.c
 *
 * This file was part of the Independent JPEG Group's software:
 * Copyright (C) 1994-1996, Thomas G. Lane.
 * libjpeg-turbo Modifications:
 * Copyright (C) 1999-2006, MIYASAKA Masaru.
 * Copyright 2009 Pierre Ossman <ossman@cendio.se> for Cendio AB
 * Copyright (C) 2011, 2014-2015 D. R. Commander
 * mozjpeg Modifications:
 * Copyright (C) 2014, Mozilla Corporation.
 * For conditions of distribution and use, see the accompanying README file.
 *
 * This file contains the forward-DCT management logic.
 * This code selects a particular DCT implementation to be used,
 * and it performs related housekeeping chores including coefficient
 * quantization.
 */

#define JPEG_INTERNALS
#include "jinclude.h"
#include "jpeglib.h"
#include "jdct.h"               /* Private declarations for DCT subsystem */
#include "jsimddct.h"
#include "jchuff.h"
#include <assert.h>
#include <math.h>


/* Private subobject for this module */

typedef void (*forward_DCT_method_ptr) (DCTELEM *data);
typedef void (*float_DCT_method_ptr) (FAST_FLOAT *data);

typedef void (*preprocess_method_ptr)(DCTELEM*, const JQUANT_TBL*);
typedef void (*float_preprocess_method_ptr)(FAST_FLOAT*, const JQUANT_TBL*);

typedef void (*convsamp_method_ptr) (JSAMPARRAY sample_data,
                                     JDIMENSION start_col,
                                     DCTELEM *workspace);
typedef void (*float_convsamp_method_ptr) (JSAMPARRAY sample_data,
                                           JDIMENSION start_col,
                                           FAST_FLOAT *workspace);

typedef void (*quantize_method_ptr) (JCOEFPTR coef_block, DCTELEM *divisors,
                                     DCTELEM *workspace);
typedef void (*float_quantize_method_ptr) (JCOEFPTR coef_block,
                                           FAST_FLOAT *divisors,
                                           FAST_FLOAT *workspace);

METHODDEF(void) quantize (JCOEFPTR, DCTELEM *, DCTELEM *);

typedef struct {
  struct jpeg_forward_dct pub;  /* public fields */

  /* Pointer to the DCT routine actually in use */
  forward_DCT_method_ptr dct;
  convsamp_method_ptr convsamp;
  preprocess_method_ptr preprocess;
  quantize_method_ptr quantize;

  /* The actual post-DCT divisors --- not identical to the quant table
   * entries, because of scaling (especially for an unnormalized DCT).
   * Each table is given in normal array order.
   */
  DCTELEM *divisors[NUM_QUANT_TBLS];

  /* work area for FDCT subroutine */
  DCTELEM *workspace;

#ifdef DCT_FLOAT_SUPPORTED
  /* Same as above for the floating-point case. */
  float_DCT_method_ptr float_dct;
  float_convsamp_method_ptr float_convsamp;
  float_preprocess_method_ptr float_preprocess;
  float_quantize_method_ptr float_quantize;
  FAST_FLOAT *float_divisors[NUM_QUANT_TBLS];
  FAST_FLOAT *float_workspace;
#endif
} my_fdct_controller;

typedef my_fdct_controller *my_fdct_ptr;


#if BITS_IN_JSAMPLE == 8

/*
 * Find the highest bit in an integer through binary search.
 */

LOCAL(int)
flss (UINT16 val)
{
  int bit;

  bit = 16;

  if (!val)
    return 0;

  if (!(val & 0xff00)) {
    bit -= 8;
    val <<= 8;
  }
  if (!(val & 0xf000)) {
    bit -= 4;
    val <<= 4;
  }
  if (!(val & 0xc000)) {
    bit -= 2;
    val <<= 2;
  }
  if (!(val & 0x8000)) {
    bit -= 1;
    val <<= 1;
  }

  return bit;
}


/*
 * Compute values to do a division using reciprocal.
 *
 * This implementation is based on an algorithm described in
 *   "How to optimize for the Pentium family of microprocessors"
 *   (http://www.agner.org/assem/).
 * More information about the basic algorithm can be found in
 * the paper "Integer Division Using Reciprocals" by Robert Alverson.
 *
 * The basic idea is to replace x/d by x * d^-1. In order to store
 * d^-1 with enough precision we shift it left a few places. It turns
 * out that this algoright gives just enough precision, and also fits
 * into DCTELEM:
 *
 *   b = (the number of significant bits in divisor) - 1
 *   r = (word size) + b
 *   f = 2^r / divisor
 *
 * f will not be an integer for most cases, so we need to compensate
 * for the rounding error introduced:
 *
 *   no fractional part:
 *
 *       result = input >> r
 *
 *   fractional part of f < 0.5:
 *
 *       round f down to nearest integer
 *       result = ((input + 1) * f) >> r
 *
 *   fractional part of f > 0.5:
 *
 *       round f up to nearest integer
 *       result = (input * f) >> r
 *
 * This is the original algorithm that gives truncated results. But we
 * want properly rounded results, so we replace "input" with
 * "input + divisor/2".
 *
 * In order to allow SIMD implementations we also tweak the values to
 * allow the same calculation to be made at all times:
 *
 *   dctbl[0] = f rounded to nearest integer
 *   dctbl[1] = divisor / 2 (+ 1 if fractional part of f < 0.5)
 *   dctbl[2] = 1 << ((word size) * 2 - r)
 *   dctbl[3] = r - (word size)
 *
 * dctbl[2] is for stupid instruction sets where the shift operation
 * isn't member wise (e.g. MMX).
 *
 * The reason dctbl[2] and dctbl[3] reduce the shift with (word size)
 * is that most SIMD implementations have a "multiply and store top
 * half" operation.
 *
 * Lastly, we store each of the values in their own table instead
 * of in a consecutive manner, yet again in order to allow SIMD
 * routines.
 */

LOCAL(int)
compute_reciprocal (UINT16 divisor, DCTELEM *dtbl)
{
  UDCTELEM2 fq, fr;
  UDCTELEM c;
  int b, r;

  if (divisor == 1) {
    /* divisor == 1 means unquantized, so these reciprocal/correction/shift
     * values will cause the C quantization algorithm to act like the
     * identity function.  Since only the C quantization algorithm is used in
     * these cases, the scale value is irrelevant.
     */
    dtbl[DCTSIZE2 * 0] = (DCTELEM) 1;                       /* reciprocal */
    dtbl[DCTSIZE2 * 1] = (DCTELEM) 0;                       /* correction */
    dtbl[DCTSIZE2 * 2] = (DCTELEM) 1;                       /* scale */
    dtbl[DCTSIZE2 * 3] = -(DCTELEM) (sizeof(DCTELEM) * 8);  /* shift */
    return 0;
  }

  b = flss(divisor) - 1;
  r  = sizeof(DCTELEM) * 8 + b;

  fq = ((UDCTELEM2)1 << r) / divisor;
  fr = ((UDCTELEM2)1 << r) % divisor;

  c = divisor / 2; /* for rounding */

  if (fr == 0) { /* divisor is power of two */
    /* fq will be one bit too large to fit in DCTELEM, so adjust */
    fq >>= 1;
    r--;
  } else if (fr <= (divisor / 2U)) { /* fractional part is < 0.5 */
    c++;
  } else { /* fractional part is > 0.5 */
    fq++;
  }

  dtbl[DCTSIZE2 * 0] = (DCTELEM) fq;      /* reciprocal */
  dtbl[DCTSIZE2 * 1] = (DCTELEM) c;       /* correction + roundfactor */
#ifdef WITH_SIMD
  dtbl[DCTSIZE2 * 2] = (DCTELEM) (1 << (sizeof(DCTELEM)*8*2 - r));  /* scale */
#else
  dtbl[DCTSIZE2 * 2] = 1;
#endif
  dtbl[DCTSIZE2 * 3] = (DCTELEM) r - sizeof(DCTELEM)*8; /* shift */

  if (r <= 16) return 0;
  else return 1;
}

#endif


/*
 * Initialize for a processing pass.
 * Verify that all referenced Q-tables are present, and set up
 * the divisor table for each one.
 * In the current implementation, DCT of all components is done during
 * the first pass, even if only some components will be output in the
 * first scan.  Hence all components should be examined here.
 */

METHODDEF(void)
start_pass_fdctmgr (j_compress_ptr cinfo)
{
  my_fdct_ptr fdct = (my_fdct_ptr) cinfo->fdct;
  int ci, qtblno, i;
  jpeg_component_info *compptr;
  JQUANT_TBL *qtbl;
  DCTELEM *dtbl;

  for (ci = 0, compptr = cinfo->comp_info; ci < cinfo->num_components;
       ci++, compptr++) {
    qtblno = compptr->quant_tbl_no;
    /* Make sure specified quantization table is present */
    if (qtblno < 0 || qtblno >= NUM_QUANT_TBLS ||
        cinfo->quant_tbl_ptrs[qtblno] == NULL)
      ERREXIT1(cinfo, JERR_NO_QUANT_TABLE, qtblno);
    qtbl = cinfo->quant_tbl_ptrs[qtblno];
    /* Compute divisors for this quant table */
    /* We may do this more than once for same table, but it's not a big deal */
    switch (cinfo->dct_method) {
#ifdef DCT_ISLOW_SUPPORTED
    case JDCT_ISLOW:
      /* For LL&M IDCT method, divisors are equal to raw quantization
       * coefficients multiplied by 8 (to counteract scaling).
       */
      if (fdct->divisors[qtblno] == NULL) {
        fdct->divisors[qtblno] = (DCTELEM *)
          (*cinfo->mem->alloc_small) ((j_common_ptr) cinfo, JPOOL_IMAGE,
                                      (DCTSIZE2 * 4) * sizeof(DCTELEM));
      }
      dtbl = fdct->divisors[qtblno];
      for (i = 0; i < DCTSIZE2; i++) {
#if BITS_IN_JSAMPLE == 8
        if (!compute_reciprocal(qtbl->quantval[i] << 3, &dtbl[i]) &&
            fdct->quantize == jsimd_quantize)
          fdct->quantize = quantize;
#else
        dtbl[i] = ((DCTELEM) qtbl->quantval[i]) << 3;
#endif
      }
      break;
#endif
#ifdef DCT_IFAST_SUPPORTED
    case JDCT_IFAST:
      {
        /* For AA&N IDCT method, divisors are equal to quantization
         * coefficients scaled by scalefactor[row]*scalefactor[col], where
         *   scalefactor[0] = 1
         *   scalefactor[k] = cos(k*PI/16) * sqrt(2)    for k=1..7
         * We apply a further scale factor of 8.
         */
#define CONST_BITS 14
        static const INT16 aanscales[DCTSIZE2] = {
          /* precomputed values scaled up by 14 bits */
          16384, 22725, 21407, 19266, 16384, 12873,  8867,  4520,
          22725, 31521, 29692, 26722, 22725, 17855, 12299,  6270,
          21407, 29692, 27969, 25172, 21407, 16819, 11585,  5906,
          19266, 26722, 25172, 22654, 19266, 15137, 10426,  5315,
          16384, 22725, 21407, 19266, 16384, 12873,  8867,  4520,
          12873, 17855, 16819, 15137, 12873, 10114,  6967,  3552,
           8867, 12299, 11585, 10426,  8867,  6967,  4799,  2446,
           4520,  6270,  5906,  5315,  4520,  3552,  2446,  1247
        };
        SHIFT_TEMPS

        if (fdct->divisors[qtblno] == NULL) {
          fdct->divisors[qtblno] = (DCTELEM *)
            (*cinfo->mem->alloc_small) ((j_common_ptr) cinfo, JPOOL_IMAGE,
                                        (DCTSIZE2 * 4) * sizeof(DCTELEM));
        }
        dtbl = fdct->divisors[qtblno];
        for (i = 0; i < DCTSIZE2; i++) {
#if BITS_IN_JSAMPLE == 8
          if (!compute_reciprocal(
                DESCALE(MULTIPLY16V16((JLONG) qtbl->quantval[i],
                                      (JLONG) aanscales[i]),
                        CONST_BITS-3), &dtbl[i]) &&
              fdct->quantize == jsimd_quantize)
            fdct->quantize = quantize;
#else
           dtbl[i] = (DCTELEM)
             DESCALE(MULTIPLY16V16((JLONG) qtbl->quantval[i],
                                   (JLONG) aanscales[i]),
                     CONST_BITS-3);
#endif
        }
      }
      break;
#endif
#ifdef DCT_FLOAT_SUPPORTED
    case JDCT_FLOAT:
      {
        /* For float AA&N IDCT method, divisors are equal to quantization
         * coefficients scaled by scalefactor[row]*scalefactor[col], where
         *   scalefactor[0] = 1
         *   scalefactor[k] = cos(k*PI/16) * sqrt(2)    for k=1..7
         * We apply a further scale factor of 8.
         * What's actually stored is 1/divisor so that the inner loop can
         * use a multiplication rather than a division.
         */
        FAST_FLOAT *fdtbl;
        int row, col;
        static const double aanscalefactor[DCTSIZE] = {
          1.0, 1.387039845, 1.306562965, 1.175875602,
          1.0, 0.785694958, 0.541196100, 0.275899379
        };

        if (fdct->float_divisors[qtblno] == NULL) {
          fdct->float_divisors[qtblno] = (FAST_FLOAT *)
            (*cinfo->mem->alloc_small) ((j_common_ptr) cinfo, JPOOL_IMAGE,
                                        DCTSIZE2 * sizeof(FAST_FLOAT));
        }
        fdtbl = fdct->float_divisors[qtblno];
        i = 0;
        for (row = 0; row < DCTSIZE; row++) {
          for (col = 0; col < DCTSIZE; col++) {
            fdtbl[i] = (FAST_FLOAT)
              (1.0 / (((double) qtbl->quantval[i] *
                       aanscalefactor[row] * aanscalefactor[col] * 8.0)));
            i++;
          }
        }
      }
      break;
#endif
    default:
      ERREXIT(cinfo, JERR_NOT_COMPILED);
      break;
    }
  }
}

METHODDEF(float)
catmull_rom(const DCTELEM value1, const DCTELEM value2, const DCTELEM value3, const DCTELEM value4, const float t, int size)
{
  const int tan1 = (value3 - value1) * size;
  const int tan2 = (value4 - value2) * size;

  const float t2 = t * t;
  const float t3 = t2 * t;

  const float f1 = 2.f * t3 - 3.f * t2 + 1.f;
  const float f2 = -2.f * t3 + 3.f * t2;
  const float f3 = t3 - 2.f * t2 + t;
  const float f4 = t3 - t2;

  return value2 * f1 + tan1 * f3 +
         value3 * f2 + tan2 * f4;
}

/** Prevents visible ringing artifacts near hard edges on white backgrounds.

  1. JPEG can encode samples with higher values than it's possible to display (higher than 255 in RGB),
     and the decoder will always clamp values to 0-255. To encode 255 you can use any value >= 255,
     and distortions of the out-of-range values won't be visible as long as they decode to anything >= 255.

  2. From DCT perspective pixels in a block are a waveform. Hard edges form square waves (bad).
     Edges with white are similar to waveform clipping, and anti-clipping algorithms can turn square waves
     into softer ones that compress better.

 */
METHODDEF(void)
preprocess_deringing(DCTELEM *data, const JQUANT_TBL *quantization_table)
{
  const DCTELEM maxsample = 255 - CENTERJSAMPLE;
  const int size = DCTSIZE * DCTSIZE;

  /* Decoders don't handle overflow of DC very well, so calculate
     maximum overflow that is safe to do without increasing DC out of range */
  int sum = 0;
  int maxsample_count = 0;
  int i;
  DCTELEM maxovershoot;
  int n;
  
  for(i=0; i < size; i++) {
    sum += data[i];
    if (data[i] >= maxsample) {
      maxsample_count++;
    }
  }

  /* If nothing reaches max value there's nothing to overshoot
     and if the block is completely flat, it's already the best case. */
  if (!maxsample_count || maxsample_count == size) {
    return;
  }

  /* Too much overshoot is not good: increased amplitude will cost bits, and the cost is proportional to quantization (here using DC quant as a rough guide). */
  maxovershoot = maxsample + MIN(MIN(31, 2*quantization_table->quantval[0]), (maxsample * size - sum) / maxsample_count);

  n = 0;
  do {
    int start, end, length;
    DCTELEM f1, f2, l1, l2, fslope, lslope;
    float step, position;
    
    /* Pixels are traversed in zig-zag order to process them as a line */
    if (data[jpeg_natural_order[n]] < maxsample) {
      n++;
      continue;
    }

    /* Find a run of maxsample pixels. Start is the first pixel inside the range, end the first pixel outside. */
    start = n;
    while(++n < size && data[jpeg_natural_order[n]] >= maxsample) {}
    end = n;

    /* the run will be replaced with a catmull-rom interpolation of values from the edges */

    /* Find suitable upward slope from pixels around edges of the run.
       Just feeding nearby pixels as catmull rom points isn't good enough,
       as slope with one sample before the edge may have been flattened by clipping,
       and slope of two samples before the edge could be downward. */
    f1 = data[jpeg_natural_order[start >= 1 ? start-1 : 0]];
    f2 = data[jpeg_natural_order[start >= 2 ? start-2 : 0]];

    l1 = data[jpeg_natural_order[end < size-1 ? end : size-1]];
    l2 = data[jpeg_natural_order[end < size-2 ? end+1 : size-1]];

    fslope = MAX(f1-f2, maxsample-f1);
    lslope = MAX(l1-l2, maxsample-l1);

    /* if slope at the start/end is unknown, just make the curve symmetric */
    if (start == 0) {
      fslope = lslope;
    }
    if (end == size) {
      lslope = fslope;
    }

    /* The curve fits better if first and last pixel is omitted */
    length = end - start;
    step = 1.f/(float)(length + 1);
    position = step;

    for(i = start; i < end; i++, position += step) {
      DCTELEM tmp = ceilf(catmull_rom(maxsample - fslope, maxsample, maxsample, maxsample - lslope, position, length));
      data[jpeg_natural_order[i]] = MIN(tmp, maxovershoot);
    }
    n++;
  }
  while(n < size);
}

/*
  Float version of preprocess_deringing()
 */
METHODDEF(void)
float_preprocess_deringing(FAST_FLOAT *data, const JQUANT_TBL *quantization_table)
{
  const FAST_FLOAT maxsample = 255 - CENTERJSAMPLE;
  const int size = DCTSIZE * DCTSIZE;

  FAST_FLOAT sum = 0;
  int maxsample_count = 0;
  int i;
  int n;
  FAST_FLOAT maxovershoot;
  
  for(i=0; i < size; i++) {
    sum += data[i];
    if (data[i] >= maxsample) {
      maxsample_count++;
    }
  }

  if (!maxsample_count || maxsample_count == size) {
    return;
  }

  maxovershoot = maxsample + MIN(MIN(31, 2*quantization_table->quantval[0]), (maxsample * size - sum) / maxsample_count);

  n = 0;
  do {
    int start, end, length;
    FAST_FLOAT f1, f2, l1, l2, fslope, lslope;
    float step, position;
    
    if (data[jpeg_natural_order[n]] < maxsample) {
      n++;
      continue;
    }

    start = n;
    while(++n < size && data[jpeg_natural_order[n]] >= maxsample) {}
    end = n;

    f1 = data[jpeg_natural_order[start >= 1 ? start-1 : 0]];
    f2 = data[jpeg_natural_order[start >= 2 ? start-2 : 0]];

    l1 = data[jpeg_natural_order[end < size-1 ? end : size-1]];
    l2 = data[jpeg_natural_order[end < size-2 ? end+1 : size-1]];

    fslope = MAX(f1-f2, maxsample-f1);
    lslope = MAX(l1-l2, maxsample-l1);

    if (start == 0) {
      fslope = lslope;
    }
    if (end == size) {
      lslope = fslope;
    }

    length = end - start;
    step = 1.f/(float)(length + 1);
    position = step;

    for(i = start; i < end; i++, position += step) {
      FAST_FLOAT tmp = catmull_rom(maxsample - fslope, maxsample, maxsample, maxsample - lslope, position, length);
      data[jpeg_natural_order[i]] = MIN(tmp, maxovershoot);
    }
    n++;
  }
  while(n < size);
}

/*
 * Load data into workspace, applying unsigned->signed conversion.
 */

METHODDEF(void)
convsamp (JSAMPARRAY sample_data, JDIMENSION start_col, DCTELEM *workspace)
{
  register DCTELEM *workspaceptr;
  register JSAMPROW elemptr;
  register int elemr;

  workspaceptr = workspace;
  for (elemr = 0; elemr < DCTSIZE; elemr++) {
    elemptr = sample_data[elemr] + start_col;

#if DCTSIZE == 8                /* unroll the inner loop */
    *workspaceptr++ = GETJSAMPLE(*elemptr++) - CENTERJSAMPLE;
    *workspaceptr++ = GETJSAMPLE(*elemptr++) - CENTERJSAMPLE;
    *workspaceptr++ = GETJSAMPLE(*elemptr++) - CENTERJSAMPLE;
    *workspaceptr++ = GETJSAMPLE(*elemptr++) - CENTERJSAMPLE;
    *workspaceptr++ = GETJSAMPLE(*elemptr++) - CENTERJSAMPLE;
    *workspaceptr++ = GETJSAMPLE(*elemptr++) - CENTERJSAMPLE;
    *workspaceptr++ = GETJSAMPLE(*elemptr++) - CENTERJSAMPLE;
    *workspaceptr++ = GETJSAMPLE(*elemptr++) - CENTERJSAMPLE;
#else
    {
      register int elemc;
      for (elemc = DCTSIZE; elemc > 0; elemc--)
        *workspaceptr++ = GETJSAMPLE(*elemptr++) - CENTERJSAMPLE;
    }
#endif
  }
}


/*
 * Quantize/descale the coefficients, and store into coef_blocks[].
 */

METHODDEF(void)
quantize (JCOEFPTR coef_block, DCTELEM *divisors, DCTELEM *workspace)
{
  int i;
  DCTELEM temp;
  JCOEFPTR output_ptr = coef_block;

#if BITS_IN_JSAMPLE == 8

  UDCTELEM recip, corr;
  int shift;
  UDCTELEM2 product;

  for (i = 0; i < DCTSIZE2; i++) {
    temp = workspace[i];
    recip = divisors[i + DCTSIZE2 * 0];
    corr =  divisors[i + DCTSIZE2 * 1];
    shift = divisors[i + DCTSIZE2 * 3];

    if (temp < 0) {
      temp = -temp;
      product = (UDCTELEM2)(temp + corr) * recip;
      product >>= shift + sizeof(DCTELEM)*8;
      temp = (DCTELEM)product;
      temp = -temp;
    } else {
      product = (UDCTELEM2)(temp + corr) * recip;
      product >>= shift + sizeof(DCTELEM)*8;
      temp = (DCTELEM)product;
    }
    output_ptr[i] = (JCOEF) temp;
  }

#else

  register DCTELEM qval;

  for (i = 0; i < DCTSIZE2; i++) {
    qval = divisors[i];
    temp = workspace[i];
    /* Divide the coefficient value by qval, ensuring proper rounding.
     * Since C does not specify the direction of rounding for negative
     * quotients, we have to force the dividend positive for portability.
     *
     * In most files, at least half of the output values will be zero
     * (at default quantization settings, more like three-quarters...)
     * so we should ensure that this case is fast.  On many machines,
     * a comparison is enough cheaper than a divide to make a special test
     * a win.  Since both inputs will be nonnegative, we need only test
     * for a < b to discover whether a/b is 0.
     * If your machine's division is fast enough, define FAST_DIVIDE.
     */
#ifdef FAST_DIVIDE
#define DIVIDE_BY(a,b)  a /= b
#else
#define DIVIDE_BY(a,b)  if (a >= b) a /= b; else a = 0
#endif
    if (temp < 0) {
      temp = -temp;
      temp += qval>>1;  /* for rounding */
      DIVIDE_BY(temp, qval);
      temp = -temp;
    } else {
      temp += qval>>1;  /* for rounding */
      DIVIDE_BY(temp, qval);
    }
    output_ptr[i] = (JCOEF) temp;
  }

#endif

}


/*
 * Perform forward DCT on one or more blocks of a component.
 *
 * The input samples are taken from the sample_data[] array starting at
 * position start_row/start_col, and moving to the right for any additional
 * blocks. The quantized coefficients are returned in coef_blocks[].
 */

METHODDEF(void)
forward_DCT (j_compress_ptr cinfo, jpeg_component_info *compptr,
             JSAMPARRAY sample_data, JBLOCKROW coef_blocks,
             JDIMENSION start_row, JDIMENSION start_col,
             JDIMENSION num_blocks, JBLOCKROW dst)
/* This version is used for integer DCT implementations. */
{
  /* This routine is heavily used, so it's worth coding it tightly. */
  my_fdct_ptr fdct = (my_fdct_ptr) cinfo->fdct;
  DCTELEM *divisors = fdct->divisors[compptr->quant_tbl_no];
  JQUANT_TBL *qtbl = cinfo->quant_tbl_ptrs[compptr->quant_tbl_no];
  DCTELEM *workspace;
  JDIMENSION bi;

  /* Make sure the compiler doesn't look up these every pass */
  forward_DCT_method_ptr do_dct = fdct->dct;
  convsamp_method_ptr do_convsamp = fdct->convsamp;
  preprocess_method_ptr do_preprocess = fdct->preprocess;
  quantize_method_ptr do_quantize = fdct->quantize;
  workspace = fdct->workspace;

  sample_data += start_row;     /* fold in the vertical offset once */

  for (bi = 0; bi < num_blocks; bi++, start_col += DCTSIZE) {
    /* Load data into workspace, applying unsigned->signed conversion */
    (*do_convsamp) (sample_data, start_col, workspace);

    if (do_preprocess) {
      (*do_preprocess) (workspace, qtbl);
    }

    /* Perform the DCT */
    (*do_dct) (workspace);

    /* Save unquantized transform coefficients for later trellis quantization */
    if (dst) {
      int i;
      if (cinfo->dct_method == JDCT_IFAST) {
        static const INT16 aanscales[DCTSIZE2] = {
          /* precomputed values scaled up by 14 bits */
          16384, 22725, 21407, 19266, 16384, 12873,  8867,  4520,
          22725, 31521, 29692, 26722, 22725, 17855, 12299,  6270,
          21407, 29692, 27969, 25172, 21407, 16819, 11585,  5906,
          19266, 26722, 25172, 22654, 19266, 15137, 10426,  5315,
          16384, 22725, 21407, 19266, 16384, 12873,  8867,  4520,
          12873, 17855, 16819, 15137, 12873, 10114,  6967,  3552,
          8867, 12299, 11585, 10426,  8867,  6967,  4799,  2446,
          4520,  6270,  5906,  5315,  4520,  3552,  2446,  1247
        };
        
        for (i = 0; i < DCTSIZE2; i++) {
          int x = workspace[i];
          int s = aanscales[i];
          x = (x >= 0) ? (x * 32768 + s) / (2*s) : (x * 32768 - s) / (2*s);
          dst[bi][i] = x;
        }
        
      } else {
        for (i = 0; i < DCTSIZE2; i++) {
          dst[bi][i] = workspace[i];
        }
      }
    }
    
    /* Quantize/descale the coefficients, and store into coef_blocks[] */
    (*do_quantize) (coef_blocks[bi], divisors, workspace);

    if (do_preprocess) {
      int i;
      int maxval = (1 << MAX_COEF_BITS) - 1;
      for (i = 0; i < 64; i++) {
        if (coef_blocks[bi][i] < -maxval)
          coef_blocks[bi][i] = -maxval;
        if (coef_blocks[bi][i] > maxval)
          coef_blocks[bi][i] = maxval;
  }
  }
}
}


#ifdef DCT_FLOAT_SUPPORTED

METHODDEF(void)
convsamp_float(JSAMPARRAY sample_data, JDIMENSION start_col,
               FAST_FLOAT *workspace)
{
  register FAST_FLOAT *workspaceptr;
  register JSAMPROW elemptr;
  register int elemr;

  workspaceptr = workspace;
  for (elemr = 0; elemr < DCTSIZE; elemr++) {
    elemptr = sample_data[elemr] + start_col;
#if DCTSIZE == 8                /* unroll the inner loop */
    *workspaceptr++ = (FAST_FLOAT)(GETJSAMPLE(*elemptr++) - CENTERJSAMPLE);
    *workspaceptr++ = (FAST_FLOAT)(GETJSAMPLE(*elemptr++) - CENTERJSAMPLE);
    *workspaceptr++ = (FAST_FLOAT)(GETJSAMPLE(*elemptr++) - CENTERJSAMPLE);
    *workspaceptr++ = (FAST_FLOAT)(GETJSAMPLE(*elemptr++) - CENTERJSAMPLE);
    *workspaceptr++ = (FAST_FLOAT)(GETJSAMPLE(*elemptr++) - CENTERJSAMPLE);
    *workspaceptr++ = (FAST_FLOAT)(GETJSAMPLE(*elemptr++) - CENTERJSAMPLE);
    *workspaceptr++ = (FAST_FLOAT)(GETJSAMPLE(*elemptr++) - CENTERJSAMPLE);
    *workspaceptr++ = (FAST_FLOAT)(GETJSAMPLE(*elemptr++) - CENTERJSAMPLE);
#else
    {
      register int elemc;
      for (elemc = DCTSIZE; elemc > 0; elemc--)
        *workspaceptr++ = (FAST_FLOAT)
                          (GETJSAMPLE(*elemptr++) - CENTERJSAMPLE);
    }
#endif
  }
}


METHODDEF(void)
quantize_float(JCOEFPTR coef_block, FAST_FLOAT *divisors,
               FAST_FLOAT *workspace)
{
  register FAST_FLOAT temp;
  register int i;
  register JCOEFPTR output_ptr = coef_block;

  for (i = 0; i < DCTSIZE2; i++) {
    /* Apply the quantization and scaling factor */
    temp = workspace[i] * divisors[i];

    /* Round to nearest integer.
     * Since C does not specify the direction of rounding for negative
     * quotients, we have to force the dividend positive for portability.
     * The maximum coefficient size is +-16K (for 12-bit data), so this
     * code should work for either 16-bit or 32-bit ints.
     */
    output_ptr[i] = (JCOEF) ((int) (temp + (FAST_FLOAT) 16384.5) - 16384);
  }
}


METHODDEF(void)
forward_DCT_float (j_compress_ptr cinfo, jpeg_component_info *compptr,
                   JSAMPARRAY sample_data, JBLOCKROW coef_blocks,
                   JDIMENSION start_row, JDIMENSION start_col,
                   JDIMENSION num_blocks, JBLOCKROW dst)
/* This version is used for floating-point DCT implementations. */
{
  /* This routine is heavily used, so it's worth coding it tightly. */
  my_fdct_ptr fdct = (my_fdct_ptr) cinfo->fdct;
  FAST_FLOAT *divisors = fdct->float_divisors[compptr->quant_tbl_no];
  JQUANT_TBL *qtbl = cinfo->quant_tbl_ptrs[compptr->quant_tbl_no];
  FAST_FLOAT *workspace;
  JDIMENSION bi;
  float v;
  int x;


  /* Make sure the compiler doesn't look up these every pass */
  float_DCT_method_ptr do_dct = fdct->float_dct;
  float_convsamp_method_ptr do_convsamp = fdct->float_convsamp;
  float_preprocess_method_ptr do_preprocess = fdct->float_preprocess;
  float_quantize_method_ptr do_quantize = fdct->float_quantize;
  workspace = fdct->float_workspace;

  sample_data += start_row;     /* fold in the vertical offset once */

  for (bi = 0; bi < num_blocks; bi++, start_col += DCTSIZE) {
    /* Load data into workspace, applying unsigned->signed conversion */
    (*do_convsamp) (sample_data, start_col, workspace);

    if (do_preprocess) {
      (*do_preprocess) (workspace, qtbl);
    }

    /* Perform the DCT */
    (*do_dct) (workspace);

    /* Save unquantized transform coefficients for later trellis quantization */
    /* Currently save as integer values. Could save float values but would require */
    /* modifications to memory allocation and trellis quantization */

    if (dst) {
      int i;
      static const double aanscalefactor[DCTSIZE] = {
        1.0, 1.387039845, 1.306562965, 1.175875602,
        1.0, 0.785694958, 0.541196100, 0.275899379
      };

      for (i = 0; i < DCTSIZE2; i++) {
        v = workspace[i];
        v /= aanscalefactor[i%8];
        v /= aanscalefactor[i/8];
        x = (v >= 0.0) ? (int)(v + 0.5) : (int)(v - 0.5);
        dst[bi][i] = x;
      }
    }

    /* Quantize/descale the coefficients, and store into coef_blocks[] */
    (*do_quantize) (coef_blocks[bi], divisors, workspace);
    
    if (do_preprocess) {
      int i;
      int maxval = (1 << MAX_COEF_BITS) - 1;
      for (i = 0; i < 64; i++) {
        if (coef_blocks[bi][i] < -maxval)
          coef_blocks[bi][i] = -maxval;
        if (coef_blocks[bi][i] > maxval)
          coef_blocks[bi][i] = maxval;
  }
}
  }
}

#endif /* DCT_FLOAT_SUPPORTED */

#include "jpeg_nbits_table.h"

static const float jpeg_lambda_weights_flat[64] = {
  1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f,
  1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f,
  1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f,
  1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f,
  1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f,
  1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f,
  1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f,
  1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f
};

static const float jpeg_lambda_weights_csf_luma[64] = {
  3.35630f, 3.59892f, 3.20921f, 2.28102f, 1.42378f, 0.88079f, 0.58190f, 0.43454f,
  3.59893f, 3.21284f, 2.71282f, 1.98092f, 1.30506f, 0.83852f, 0.56346f, 0.42146f,
  3.20921f, 2.71282f, 2.12574f, 1.48616f, 0.99660f, 0.66132f, 0.45610f, 0.34609f,
  2.28102f, 1.98092f, 1.48616f, 0.97492f, 0.64622f, 0.43812f, 0.31074f, 0.24072f,
  1.42378f, 1.30506f, 0.99660f, 0.64623f, 0.42051f, 0.28446f, 0.20380f, 0.15975f,
  0.88079f, 0.83852f, 0.66132f, 0.43812f, 0.28446f, 0.19092f, 0.13635f, 0.10701f,
  0.58190f, 0.56346f, 0.45610f, 0.31074f, 0.20380f, 0.13635f, 0.09674f, 0.07558f,
  0.43454f, 0.42146f, 0.34609f, 0.24072f, 0.15975f, 0.10701f, 0.07558f, 0.05875f,
};

#define DC_TRELLIS_MAX_CANDIDATES 9

LOCAL(int) get_num_dc_trellis_candidates(int dc_quantval) {
  /* Higher qualities can tolerate higher DC distortion */
  return MIN(DC_TRELLIS_MAX_CANDIDATES, (2 + 60 / dc_quantval)|1);
}

GLOBAL(void)
quantize_trellis(j_compress_ptr cinfo, c_derived_tbl *dctbl, c_derived_tbl *actbl, JBLOCKROW coef_blocks, JBLOCKROW src, JDIMENSION num_blocks,
                 JQUANT_TBL * qtbl, double *norm_src, double *norm_coef, JCOEF *last_dc_val,
                 JBLOCKROW coef_blocks_above, JBLOCKROW src_above)
{
  int i, j, k, l;
  float accumulated_zero_dist[DCTSIZE2];
  float accumulated_cost[DCTSIZE2];
  int run_start[DCTSIZE2];
  int bi;
  float best_cost;
  int last_coeff_idx; /* position of last nonzero coefficient */
  float norm = 0.0;
  float lambda_base;
  float lambda;
  float lambda_dc;
  const float *lambda_tbl = (cinfo->master->use_lambda_weight_tbl) ?
                            jpeg_lambda_weights_csf_luma :
                            jpeg_lambda_weights_flat;
  int Ss, Se;
  float *accumulated_zero_block_cost = NULL;
  float *accumulated_block_cost = NULL;
  int *block_run_start = NULL;
  int *requires_eob = NULL;
  int has_eob;
  float cost_all_zeros;
  float best_cost_skip;
  float cost;
  int zero_run;
  int run_bits;
  int rate;
  float *accumulated_dc_cost[DC_TRELLIS_MAX_CANDIDATES];
  int *dc_cost_backtrack[DC_TRELLIS_MAX_CANDIDATES];
  JCOEF *dc_candidate[DC_TRELLIS_MAX_CANDIDATES];
  int mode = 1;
  float lambda_table[DCTSIZE2];
  const int dc_trellis_candidates = get_num_dc_trellis_candidates(qtbl->quantval[0]);
  
  Ss = cinfo->Ss;
  Se = cinfo->Se;
  if (Ss == 0)
    Ss = 1;
  if (Se < Ss)
    return;
  if (cinfo->master->trellis_eob_opt) {
    accumulated_zero_block_cost = (float *)malloc((num_blocks + 1) * sizeof(float));
    accumulated_block_cost = (float *)malloc((num_blocks + 1) * sizeof(float));
    block_run_start = (int *)malloc(num_blocks * sizeof(int));
    requires_eob = (int *)malloc((num_blocks + 1) * sizeof(int));
    if (!accumulated_zero_block_cost ||
        !accumulated_block_cost ||
        !block_run_start ||
        !requires_eob) {
      ERREXIT(cinfo, JERR_OUT_OF_MEMORY);
    }

    accumulated_zero_block_cost[0] = 0;
    accumulated_block_cost[0] = 0;
    requires_eob[0] = 0;
  }
  
  if (cinfo->master->trellis_quant_dc) {
    for (i = 0; i < dc_trellis_candidates; i++) {
      accumulated_dc_cost[i] = (float *)malloc(num_blocks * sizeof(float));
      dc_cost_backtrack[i] = (int *)malloc(num_blocks * sizeof(int));
      dc_candidate[i] = (JCOEF *)malloc(num_blocks * sizeof(JCOEF));
      if (!accumulated_dc_cost[i] ||
          !dc_cost_backtrack[i] ||
          !dc_candidate[i]) {
        ERREXIT(cinfo, JERR_OUT_OF_MEMORY);
      }
    }
  }
  
  norm = 0.0;
  for (i = 1; i < DCTSIZE2; i++) {
    norm += qtbl->quantval[i] * qtbl->quantval[i];
  }
  norm /= 63.0;

  if (mode == 1) {
    lambda_base = 1.0;
    lambda_tbl = lambda_table;
    for (i = 0; i < DCTSIZE2; i++)
      lambda_table[i] = 1.0 / (qtbl->quantval[i] * qtbl->quantval[i]);
  } else
    lambda_base = 1.0 / norm;
  
  for (bi = 0; bi < num_blocks; bi++) {
    
    norm = 0.0;
    for (i = 1; i < DCTSIZE2; i++) {
      norm += src[bi][i] * src[bi][i];
    }
    norm /= 63.0;
    
    if (cinfo->master->lambda_log_scale2 > 0.0)
      lambda = pow(2.0, cinfo->master->lambda_log_scale1) * lambda_base /
                   (pow(2.0, cinfo->master->lambda_log_scale2) + norm);
    else
      lambda = pow(2.0, cinfo->master->lambda_log_scale1 - 12.0) * lambda_base;
    
    lambda_dc = lambda * lambda_tbl[0];
    
    accumulated_zero_dist[Ss-1] = 0.0;
    accumulated_cost[Ss-1] = 0.0;

    /* Do DC coefficient */
    if (cinfo->master->trellis_quant_dc) {
      int sign = src[bi][0] >> 31;
      int x = abs(src[bi][0]);
      int q = 8 * qtbl->quantval[0];
      int qval;
      float dc_candidate_dist;

      qval = (x + q/2) / q; /* quantized value (round nearest) */
      for (k = 0; k < dc_trellis_candidates; k++) {
        int delta;
        int dc_delta;
        int bits;

        dc_candidate[k][bi] = qval - dc_trellis_candidates/2 + k;
        if (dc_candidate[k][bi] >= (1<<MAX_COEF_BITS))
          dc_candidate[k][bi] = (1<<MAX_COEF_BITS)-1;
        if (dc_candidate[k][bi] <= -(1<<MAX_COEF_BITS))
          dc_candidate[k][bi] = -(1<<MAX_COEF_BITS)+1;

        delta = dc_candidate[k][bi] * q - x;
        dc_candidate_dist = delta * delta * lambda_dc;
        dc_candidate[k][bi] *= 1 + 2*sign;
        
        /* Take into account DC differences */
        if (coef_blocks_above && src_above && cinfo->master->trellis_delta_dc_weight > 0.0) {
          int dc_above_orig;
          int dc_above_recon;
          int dc_orig;
          int dc_recon;
          float vertical_dist;
          
          dc_above_orig = src_above[bi][0];
          dc_above_recon = coef_blocks_above[bi][0] * q;
          dc_orig = src[bi][0];
          dc_recon = dc_candidate[k][bi] * q;
          /* delta is difference of vertical gradients */
          delta = (dc_above_orig - dc_orig) - (dc_above_recon - dc_recon);
          vertical_dist = delta * delta * lambda_dc;
          dc_candidate_dist +=  cinfo->master->trellis_delta_dc_weight * (vertical_dist - dc_candidate_dist);
        }
        
        if (bi == 0) {
          dc_delta = dc_candidate[k][bi] - *last_dc_val;

          /* Derive number of suffix bits */
          bits = 0;
          dc_delta = abs(dc_delta);
          while (dc_delta) {
            dc_delta >>= 1;
            bits++;
          }
          cost = bits + dctbl->ehufsi[bits] + dc_candidate_dist;
          accumulated_dc_cost[k][0] = cost;
          dc_cost_backtrack[k][0] = -1;
        } else {
          for (l = 0; l < dc_trellis_candidates; l++) {
            dc_delta = dc_candidate[k][bi] - dc_candidate[l][bi-1];

            /* Derive number of suffix bits */
            bits = 0;
            dc_delta = abs(dc_delta);
            while (dc_delta) {
              dc_delta >>= 1;
              bits++;
            }
            cost = bits + dctbl->ehufsi[bits] + dc_candidate_dist + accumulated_dc_cost[l][bi-1];
            if (l == 0 || cost < accumulated_dc_cost[k][bi]) {
              accumulated_dc_cost[k][bi] = cost;
              dc_cost_backtrack[k][bi] = l;
            }
          }
        }
      }
    }

    /* Do AC coefficients */
    for (i = Ss; i <= Se; i++) {
      int z = jpeg_natural_order[i];

      int sign = src[bi][z] >> 31;
      int x = abs(src[bi][z]);
      int q = 8 * qtbl->quantval[z];
      int candidate[16];
      int candidate_bits[16];
      float candidate_dist[16];
      int num_candidates;
      int qval;
      
      accumulated_zero_dist[i] = x * x * lambda * lambda_tbl[z] + accumulated_zero_dist[i-1];
      
      qval = (x + q/2) / q; /* quantized value (round nearest) */

      if (qval == 0) {
        coef_blocks[bi][z] = 0;
        accumulated_cost[i] = 1e38; /* Shouldn't be needed */
        continue;
      }

      if (qval >= (1<<MAX_COEF_BITS))
        qval = (1<<MAX_COEF_BITS)-1;
      
      num_candidates = jpeg_nbits_table[qval];
      for (k = 0; k < num_candidates; k++) {
        int delta;
        candidate[k] = (k < num_candidates - 1) ? (2 << k) - 1 : qval;
        delta = candidate[k] * q - x;
        candidate_bits[k] = k+1;
        candidate_dist[k] = delta * delta * lambda * lambda_tbl[z];
      }
      
      accumulated_cost[i] = 1e38;
      
      for (j = Ss-1; j < i; j++) {
        int zz = jpeg_natural_order[j];
        if (j != Ss-1 && coef_blocks[bi][zz] == 0)
          continue;
        
        zero_run = i - 1 - j;
        if ((zero_run >> 4) && actbl->ehufsi[0xf0] == 0)
          continue;
        
        run_bits = (zero_run >> 4) * actbl->ehufsi[0xf0];
        zero_run &= 15;

        for (k = 0; k < num_candidates; k++) {
          int coef_bits = actbl->ehufsi[16 * zero_run + candidate_bits[k]];
          if (coef_bits == 0)
            continue;
          
          rate = coef_bits + candidate_bits[k] + run_bits;
          cost = rate + candidate_dist[k];
          cost += accumulated_zero_dist[i-1] - accumulated_zero_dist[j] + accumulated_cost[j];
          
          if (cost < accumulated_cost[i]) {
            coef_blocks[bi][z] = (candidate[k] ^ sign) - sign;
            accumulated_cost[i] = cost;
            run_start[i] = j;
          }
        }
      }
    }
    
    last_coeff_idx = Ss-1;
    best_cost = accumulated_zero_dist[Se] + actbl->ehufsi[0];
    cost_all_zeros = accumulated_zero_dist[Se];
    best_cost_skip = cost_all_zeros;
    
    for (i = Ss; i <= Se; i++) {
      int z = jpeg_natural_order[i];
      if (coef_blocks[bi][z] != 0) {
        float cost = accumulated_cost[i] + accumulated_zero_dist[Se] - accumulated_zero_dist[i];
        float cost_wo_eob = cost;
        
        if (i < Se)
          cost += actbl->ehufsi[0];
        
        if (cost < best_cost) {
          best_cost = cost;
          last_coeff_idx = i;
          best_cost_skip = cost_wo_eob;
        }
      }
    }
    
    has_eob = (last_coeff_idx < Se) + (last_coeff_idx == Ss-1);
    
    /* Zero out coefficients that are part of runs */
    i = Se;
    while (i >= Ss)
    {
      while (i > last_coeff_idx) {
        int z = jpeg_natural_order[i];
        coef_blocks[bi][z] = 0;
        i--;
      }
      last_coeff_idx = run_start[i];
      i--;
    }
    
    if (cinfo->master->trellis_eob_opt) {
      accumulated_zero_block_cost[bi+1] = accumulated_zero_block_cost[bi];
      accumulated_zero_block_cost[bi+1] += cost_all_zeros;
      requires_eob[bi+1] = has_eob;
      
      best_cost = 1e38;
      
      if (has_eob != 2) {
        for (i = 0; i <= bi; i++) {
          int zero_block_run;
          int nbits;
          float cost;
          
          if (requires_eob[i] == 2)
            continue;
          
          cost = best_cost_skip; /* cost of coding a nonzero block */
          cost += accumulated_zero_block_cost[bi];
          cost -= accumulated_zero_block_cost[i];
          cost += accumulated_block_cost[i];
          zero_block_run = bi - i + requires_eob[i];
          nbits = jpeg_nbits_table[zero_block_run];
          cost += actbl->ehufsi[16*nbits] + nbits;
          
          if (cost < best_cost) {
            block_run_start[bi] = i;
            best_cost = cost;
            accumulated_block_cost[bi+1] = cost;
          }
        }
      }
    }
  }
  
  if (cinfo->master->trellis_eob_opt) {
    int last_block = num_blocks;
    best_cost = 1e38;
    
    for (i = 0; i <= num_blocks; i++) {
      int zero_block_run;
      int nbits;
      float cost = 0.0;
      
      if (requires_eob[i] == 2)
        continue;

      cost += accumulated_zero_block_cost[num_blocks];
      cost -= accumulated_zero_block_cost[i];
      zero_block_run = num_blocks - i + requires_eob[i];
      nbits = jpeg_nbits_table[zero_block_run];
      cost += actbl->ehufsi[16*nbits] + nbits;
      if (cost < best_cost) {
        best_cost = cost;
        last_block = i;
      }
    }
    last_block--;
    bi = num_blocks - 1;
    while (bi >= 0) {
      while (bi > last_block) {
        for (j = Ss; j <= Se; j++) {
          int z = jpeg_natural_order[j];
          coef_blocks[bi][z] = 0;
        }
        bi--;
      }
      last_block = block_run_start[bi]-1;
      bi--;
    }
    free(accumulated_zero_block_cost);
    free(accumulated_block_cost);
    free(block_run_start);
    free(requires_eob);
  }
  
  if (cinfo->master->trellis_q_opt) {
    for (bi = 0; bi < num_blocks; bi++) {
      for (i = 1; i < DCTSIZE2; i++) {
        norm_src[i] += src[bi][i] * coef_blocks[bi][i];
        norm_coef[i] += 8 * coef_blocks[bi][i] * coef_blocks[bi][i];
      }
    }
  }
  
  if (cinfo->master->trellis_quant_dc) {
    j = 0;
    for (i = 1; i < dc_trellis_candidates; i++) {
      if (accumulated_dc_cost[i][num_blocks-1] < accumulated_dc_cost[j][num_blocks-1])
        j = i;
    }
    for (bi = num_blocks-1; bi >= 0; bi--) {
      coef_blocks[bi][0] = dc_candidate[j][bi];
      j = dc_cost_backtrack[j][bi];
    }

    /* Save DC predictor */
    *last_dc_val = coef_blocks[num_blocks-1][0];

    for (i = 0; i < dc_trellis_candidates; i++) {
      free(accumulated_dc_cost[i]);
      free(dc_cost_backtrack[i]);
      free(dc_candidate[i]);
    }
  }

}

#ifdef C_ARITH_CODING_SUPPORTED
GLOBAL(void)
quantize_trellis_arith(j_compress_ptr cinfo, arith_rates *r, JBLOCKROW coef_blocks, JBLOCKROW src, JDIMENSION num_blocks,
                 JQUANT_TBL * qtbl, double *norm_src, double *norm_coef, JCOEF *last_dc_val,
                 JBLOCKROW coef_blocks_above, JBLOCKROW src_above)
{
  int i, j, k, l;
  float accumulated_zero_dist[DCTSIZE2];
  float accumulated_cost[DCTSIZE2];
  int run_start[DCTSIZE2];
  int bi;
  float best_cost;
  int last_coeff_idx; /* position of last nonzero coefficient */
  float norm = 0.0;
  float lambda_base;
  float lambda;
  float lambda_dc;
  const float *lambda_tbl = (cinfo->master->use_lambda_weight_tbl) ?
  jpeg_lambda_weights_csf_luma :
  jpeg_lambda_weights_flat;
  int Ss, Se;
  float cost;
  float run_bits;
  int rate;
  float *accumulated_dc_cost[DC_TRELLIS_MAX_CANDIDATES];
  int *dc_cost_backtrack[DC_TRELLIS_MAX_CANDIDATES];
  JCOEF *dc_candidate[DC_TRELLIS_MAX_CANDIDATES];
  int *dc_context[DC_TRELLIS_MAX_CANDIDATES];
  
  int mode = 1;
  float lambda_table[DCTSIZE2];
  const int dc_trellis_candidates = get_num_dc_trellis_candidates(qtbl->quantval[0]);
  
  Ss = cinfo->Ss;
  Se = cinfo->Se;
  if (Ss == 0)
    Ss = 1;
  if (Se < Ss)
    return;
  
  if (cinfo->master->trellis_quant_dc) {
    for (i = 0; i < dc_trellis_candidates; i++) {
      accumulated_dc_cost[i] = (float *)malloc(num_blocks * sizeof(float));
      dc_cost_backtrack[i] = (int *)malloc(num_blocks * sizeof(int));
      dc_candidate[i] = (JCOEF *)malloc(num_blocks * sizeof(JCOEF));
      dc_context[i] = (int *)malloc(num_blocks * sizeof(int));
      if (!accumulated_dc_cost[i] ||
          !dc_cost_backtrack[i] ||
          !dc_candidate[i] ||
          !dc_context[i]) {
        ERREXIT(cinfo, JERR_OUT_OF_MEMORY);
      }
    }
  }
  
  norm = 0.0;
  for (i = 1; i < DCTSIZE2; i++) {
    norm += qtbl->quantval[i] * qtbl->quantval[i];
  }
  norm /= 63.0;
  
  if (mode == 1) {
    lambda_base = 1.0;
    lambda_tbl = lambda_table;
    for (i = 0; i < DCTSIZE2; i++)
      lambda_table[i] = 1.0 / (qtbl->quantval[i] * qtbl->quantval[i]);
  } else
    lambda_base = 1.0 / norm;
  
  for (bi = 0; bi < num_blocks; bi++) {
    
    norm = 0.0;
    for (i = 1; i < DCTSIZE2; i++) {
      norm += src[bi][i] * src[bi][i];
    }
    norm /= 63.0;
    
    if (cinfo->master->lambda_log_scale2 > 0.0)
      lambda = pow(2.0, cinfo->master->lambda_log_scale1) * lambda_base /
      (pow(2.0, cinfo->master->lambda_log_scale2) + norm);
    else
      lambda = pow(2.0, cinfo->master->lambda_log_scale1 - 12.0) * lambda_base;
    
    lambda_dc = lambda * lambda_tbl[0];
    
    accumulated_zero_dist[Ss-1] = 0.0;
    accumulated_cost[Ss-1] = 0.0;
    
    /* Do DC coefficient */
    if (cinfo->master->trellis_quant_dc) {
      int sign = src[bi][0] >> 31;
      int x = abs(src[bi][0]);
      int q = 8 * qtbl->quantval[0];
      int qval;
      float dc_candidate_dist;
      
      qval = (x + q/2) / q; /* quantized value (round nearest) */
      
      /* loop over candidates in current block */
      for (k = 0; k < dc_trellis_candidates; k++) {
        int delta;
        int dc_delta;
        float bits;
        int m;
        int v2;
        
        dc_candidate[k][bi] = qval - dc_trellis_candidates/2 + k;
        delta = dc_candidate[k][bi] * q - x;
        dc_candidate_dist = delta * delta * lambda_dc;
        dc_candidate[k][bi] *= 1 + 2*sign;
        
        /* Take into account DC differences */
        if (coef_blocks_above && src_above && cinfo->master->trellis_delta_dc_weight > 0.0) {
          int dc_above_orig;
          int dc_above_recon;
          int dc_orig;
          int dc_recon;
          float vertical_dist;
          
          dc_above_orig = src_above[bi][0];
          dc_above_recon = coef_blocks_above[bi][0] * q;
          dc_orig = src[bi][0];
          dc_recon = dc_candidate[k][bi] * q;
          /* delta is difference of vertical gradients */
          delta = (dc_above_orig - dc_orig) - (dc_above_recon - dc_recon);
          vertical_dist = delta * delta * lambda_dc;
          dc_candidate_dist +=  cinfo->master->trellis_delta_dc_weight * (vertical_dist - dc_candidate_dist);
        }
        
        /* loop of candidates from previous block */
        for (l = 0; l < (bi == 0 ? 1 : dc_trellis_candidates); l++) {
          int dc_pred = (bi == 0 ? *last_dc_val : dc_candidate[l][bi-1]);
          int updated_dc_context = 0;
          int st = (bi == 0) ? 0 : dc_context[l][bi-1];
          dc_delta = dc_candidate[k][bi] - dc_pred;
          
          bits = r->rate_dc[st][dc_delta != 0];
          
          if (dc_delta != 0) {
            bits += r->rate_dc[st+1][dc_delta < 0];
            st += 2 + (dc_delta < 0);
            updated_dc_context = (dc_delta < 0) ? 8 : 4;
            
            dc_delta = abs(dc_delta);
            
            m = 0;
            if (dc_delta -= 1) {
              bits += r->rate_dc[st][1];
              st = 20;
              m = 1;
              v2 = dc_delta;
              while (v2 >>= 1) {
                bits += r->rate_dc[st][1];
                m <<= 1;
                st++;
              }
            }
            bits += r->rate_dc[st][0];
            
            if (m < (int) ((1L << r->arith_dc_L) >> 1))
              updated_dc_context = 0;    /* zero diff category */
            else if (m > (int) ((1L << r->arith_dc_U) >> 1))
              updated_dc_context += 8;   /* large diff category */

            st += 14;
            while (m >>= 1)
              bits += r->rate_dc[st][(m & dc_delta) ? 1 : 0];
          }
          
          cost = bits + dc_candidate_dist;
          if (bi != 0)
            cost += accumulated_dc_cost[l][bi-1];
          
          if (l == 0 || cost < accumulated_dc_cost[k][bi]) {
            accumulated_dc_cost[k][bi] = cost;
            dc_cost_backtrack[k][bi] = (bi == 0 ? -1 : l);
            dc_context[k][bi] = updated_dc_context;
          }
        }
      }
    }
    
    /* Do AC coefficients */
    for (i = Ss; i <= Se; i++) {
      int z = jpeg_natural_order[i];
      
      int sign = src[bi][z] >> 31;
      int x = abs(src[bi][z]);
      int q = 8 * qtbl->quantval[z];
      int candidate[16];
      float candidate_dist[16];
      int num_candidates;
      int qval;
      int delta;
      
      accumulated_zero_dist[i] = x * x * lambda * lambda_tbl[z] + accumulated_zero_dist[i-1];
      
      qval = (x + q/2) / q; /* quantized value (round nearest) */
      
      if (qval == 0) {
        coef_blocks[bi][z] = 0;
        accumulated_cost[i] = 1e38; /* Shouldn't be needed */
        continue;
      }
      
      k = 0;
      candidate[k] = qval;
      delta = candidate[k] * q - x;
      candidate_dist[k] = delta * delta * lambda * lambda_tbl[z];
      k++;
      if (qval > 1) {
        candidate[k] = qval - 1;
        delta = candidate[k] * q - x;
        candidate_dist[k] = delta * delta * lambda * lambda_tbl[z];
        k++;
      }
      num_candidates = k;
      
      accumulated_cost[i] = 1e38;
      
      for (j = Ss-1; j < i; j++) {
        int zz = jpeg_natural_order[j];
        if (j != Ss-1 && coef_blocks[bi][zz] == 0)
          continue;
        
        run_bits = r->rate_ac[3*j][0]; /* EOB */
        for (k = j+1; k < i; k++)
          run_bits += r->rate_ac[3*(k-1)+1][0];
        run_bits += r->rate_ac[3*(i-1)+1][1];
        
        for (k = 0; k < num_candidates; k++) {
          float coef_bits = 1.0; /* sign bit */
          int v = candidate[k];
          int v2;
          int m;
          int st;
          
          st = 3*(i-1)+2;
          m = 0;
          if (v -= 1) {
            coef_bits += r->rate_ac[st][1];
            m = 1;
            v2 = v;
            if (v2 >>= 1) {
              coef_bits += r->rate_ac[st][1];
              m <<= 1;
              st = (i <= r->arith_ac_K) ? 189 : 217;
              while (v2 >>= 1) {
                coef_bits += r->rate_ac[st][1];
                m <<= 1;
                st++;
              }
            }
          }
          coef_bits += r->rate_ac[st][0];
          st += 14;
          while (m >>= 1)
            coef_bits += r->rate_ac[st][(m & v) ? 1 : 0];
          
          rate = coef_bits + run_bits;
          cost = rate + candidate_dist[k];
          cost += accumulated_zero_dist[i-1] - accumulated_zero_dist[j] + accumulated_cost[j];
          
          if (cost < accumulated_cost[i]) {
            coef_blocks[bi][z] = (candidate[k] ^ sign) - sign;
            accumulated_cost[i] = cost;
            run_start[i] = j;
          }
        }
      }
    }
    
    last_coeff_idx = Ss-1;
    best_cost = accumulated_zero_dist[Se] + r->rate_ac[0][1];
    
    for (i = Ss; i <= Se; i++) {
      int z = jpeg_natural_order[i];
      if (coef_blocks[bi][z] != 0) {
        float cost = accumulated_cost[i] + accumulated_zero_dist[Se] - accumulated_zero_dist[i];
        
        if (i < Se)
          cost += r->rate_ac[3*(i-1)][1];
        
        if (cost < best_cost) {
          best_cost = cost;
          last_coeff_idx = i;
        }
      }
    }
    
    /* Zero out coefficients that are part of runs */
    i = Se;
    while (i >= Ss)
    {
      while (i > last_coeff_idx) {
        int z = jpeg_natural_order[i];
        coef_blocks[bi][z] = 0;
        i--;
      }
      last_coeff_idx = run_start[i];
      i--;
    }
    
  }
  
  if (cinfo->master->trellis_q_opt) {
    for (bi = 0; bi < num_blocks; bi++) {
      for (i = 1; i < DCTSIZE2; i++) {
        norm_src[i] += src[bi][i] * coef_blocks[bi][i];
        norm_coef[i] += 8 * coef_blocks[bi][i] * coef_blocks[bi][i];
      }
    }
  }
  
  if (cinfo->master->trellis_quant_dc) {
    j = 0;
    for (i = 1; i < dc_trellis_candidates; i++) {
      if (accumulated_dc_cost[i][num_blocks-1] < accumulated_dc_cost[j][num_blocks-1])
        j = i;
    }
    for (bi = num_blocks-1; bi >= 0; bi--) {
      coef_blocks[bi][0] = dc_candidate[j][bi];
      j = dc_cost_backtrack[j][bi];
    }
    
    /* Save DC predictor */
    *last_dc_val = coef_blocks[num_blocks-1][0];
    
    for (i = 0; i < dc_trellis_candidates; i++) {
      free(accumulated_dc_cost[i]);
      free(dc_cost_backtrack[i]);
      free(dc_candidate[i]);
      free(dc_context[i]);
    }
  }
}
#endif

/*
 * Initialize FDCT manager.
 */

GLOBAL(void)
jinit_forward_dct (j_compress_ptr cinfo)
{
  my_fdct_ptr fdct;
  int i;

  fdct = (my_fdct_ptr)
    (*cinfo->mem->alloc_small) ((j_common_ptr) cinfo, JPOOL_IMAGE,
                                sizeof(my_fdct_controller));
  cinfo->fdct = (struct jpeg_forward_dct *) fdct;
  fdct->pub.start_pass = start_pass_fdctmgr;

  /* First determine the DCT... */
  switch (cinfo->dct_method) {
#ifdef DCT_ISLOW_SUPPORTED
  case JDCT_ISLOW:
    fdct->pub.forward_DCT = forward_DCT;
    if (jsimd_can_fdct_islow())
      fdct->dct = jsimd_fdct_islow;
    else
      fdct->dct = jpeg_fdct_islow;
    break;
#endif
#ifdef DCT_IFAST_SUPPORTED
  case JDCT_IFAST:
    fdct->pub.forward_DCT = forward_DCT;
    if (jsimd_can_fdct_ifast())
      fdct->dct = jsimd_fdct_ifast;
    else
      fdct->dct = jpeg_fdct_ifast;
    break;
#endif
#ifdef DCT_FLOAT_SUPPORTED
  case JDCT_FLOAT:
    fdct->pub.forward_DCT = forward_DCT_float;
    if (jsimd_can_fdct_float())
      fdct->float_dct = jsimd_fdct_float;
    else
      fdct->float_dct = jpeg_fdct_float;
    break;
#endif
  default:
    ERREXIT(cinfo, JERR_NOT_COMPILED);
    break;
  }

  /* ...then the supporting stages. */
  switch (cinfo->dct_method) {
#ifdef DCT_ISLOW_SUPPORTED
  case JDCT_ISLOW:
#endif
#ifdef DCT_IFAST_SUPPORTED
  case JDCT_IFAST:
#endif
#if defined(DCT_ISLOW_SUPPORTED) || defined(DCT_IFAST_SUPPORTED)
    if (jsimd_can_convsamp())
      fdct->convsamp = jsimd_convsamp;
    else
      fdct->convsamp = convsamp;

    if (cinfo->master->overshoot_deringing) {
      fdct->preprocess = preprocess_deringing;
    } else {
      fdct->preprocess = NULL;
    }

    if (jsimd_can_quantize())
      fdct->quantize = jsimd_quantize;
    else
      fdct->quantize = quantize;
    break;
#endif
#ifdef DCT_FLOAT_SUPPORTED
  case JDCT_FLOAT:
    if (jsimd_can_convsamp_float())
      fdct->float_convsamp = jsimd_convsamp_float;
    else
      fdct->float_convsamp = convsamp_float;

    if (cinfo->master->overshoot_deringing) {
      fdct->float_preprocess = float_preprocess_deringing;
    } else {
      fdct->float_preprocess = NULL;
    }

    if (jsimd_can_quantize_float())
      fdct->float_quantize = jsimd_quantize_float;
    else
      fdct->float_quantize = quantize_float;
    break;
#endif
  default:
    ERREXIT(cinfo, JERR_NOT_COMPILED);
    break;
  }

  /* Allocate workspace memory */
#ifdef DCT_FLOAT_SUPPORTED
  if (cinfo->dct_method == JDCT_FLOAT)
    fdct->float_workspace = (FAST_FLOAT *)
      (*cinfo->mem->alloc_small) ((j_common_ptr) cinfo, JPOOL_IMAGE,
                                  sizeof(FAST_FLOAT) * DCTSIZE2);
  else
#endif
    fdct->workspace = (DCTELEM *)
      (*cinfo->mem->alloc_small) ((j_common_ptr) cinfo, JPOOL_IMAGE,
                                  sizeof(DCTELEM) * DCTSIZE2);

  /* Mark divisor tables unallocated */
  for (i = 0; i < NUM_QUANT_TBLS; i++) {
    fdct->divisors[i] = NULL;
#ifdef DCT_FLOAT_SUPPORTED
    fdct->float_divisors[i] = NULL;
#endif
  }
}
