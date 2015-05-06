/*
 * jddctmgr.c
 *
 * Copyright (C) 1994-1996, Thomas G. Lane.
 * This file is part of the Independent JPEG Group's software.
 * For conditions of distribution and use, see the accompanying README file.
 *
 * This file contains the inverse-DCT management logic.
 * This code selects a particular IDCT implementation to be used,
 * and it performs related housekeeping chores.  No code in this file
 * is executed per IDCT step, only during output pass setup.
 *
 * Note that the IDCT routines are responsible for performing coefficient
 * dequantization as well as the IDCT proper.  This module sets up the
 * dequantization multiplier table needed by the IDCT routine.
 */

#define JPEG_INTERNALS
#include "jinclude.h"
#include "jpeglib.h"
#include "jdct.h"		/* Private declarations for DCT subsystem */

#ifdef ANDROID_ARMV6_IDCT
  #undef ANDROID_ARMV6_IDCT
  #ifdef __arm__
    #include <machine/cpu-features.h>
    #if __ARM_ARCH__ >= 6
      #define ANDROID_ARMV6_IDCT
    #else
      #warning "ANDROID_ARMV6_IDCT is disabled"
    #endif
  #endif
#endif

#ifdef NV_ARM_NEON
#include "jsimd_neon.h"
#endif

#ifdef ANDROID_ARMV6_IDCT

/* Intentionally declare the prototype with arguments of primitive types instead
 * of type-defined ones. This will at least generate some warnings if jmorecfg.h
 * is changed and becomes incompatible with the assembly code.
 */
extern void armv6_idct(short *coefs, int *quans, unsigned char **rows, int col);

void jpeg_idct_armv6 (j_decompress_ptr cinfo, jpeg_component_info * compptr,
		 JCOEFPTR coef_block,
		 JSAMPARRAY output_buf, JDIMENSION output_col)
{
  IFAST_MULT_TYPE *dct_table = (IFAST_MULT_TYPE *)compptr->dct_table;
  armv6_idct(coef_block, dct_table, output_buf, output_col);
}

#endif

#ifdef ANDROID_INTELSSE2_IDCT
extern short __attribute__((aligned(16))) quantptrSSE[DCTSIZE2];
extern void jpeg_idct_intelsse (j_decompress_ptr cinfo, jpeg_component_info * compptr,
		JCOEFPTR coef_block,
		JSAMPARRAY output_buf, JDIMENSION output_col);
#endif

#ifdef ANDROID_MIPS_IDCT
extern void jpeg_idct_mips(j_decompress_ptr, jpeg_component_info *, JCOEFPTR, JSAMPARRAY, JDIMENSION);
#endif

/*
 * The decompressor input side (jdinput.c) saves away the appropriate
 * quantization table for each component at the start of the first scan
 * involving that component.  (This is necessary in order to correctly
 * decode files that reuse Q-table slots.)
 * When we are ready to make an output pass, the saved Q-table is converted
 * to a multiplier table that will actually be used by the IDCT routine.
 * The multiplier table contents are IDCT-method-dependent.  To support
 * application changes in IDCT method between scans, we can remake the
 * multiplier tables if necessary.
 * In buffered-image mode, the first output pass may occur before any data
 * has been seen for some components, and thus before their Q-tables have
 * been saved away.  To handle this case, multiplier tables are preset
 * to zeroes; the result of the IDCT will be a neutral gray level.
 */


/* Private subobject for this module */

typedef struct {
  struct jpeg_inverse_dct pub;	/* public fields */

  /* This array contains the IDCT method code that each multiplier table
   * is currently set up for, or -1 if it's not yet set up.
   * The actual multiplier tables are pointed to by dct_table in the
   * per-component comp_info structures.
   */
  int cur_method[MAX_COMPONENTS];
} my_idct_controller;

typedef my_idct_controller * my_idct_ptr;


/* Allocated multiplier tables: big enough for any supported variant */

typedef union {
  ISLOW_MULT_TYPE islow_array[DCTSIZE2];
#ifdef DCT_IFAST_SUPPORTED
  IFAST_MULT_TYPE ifast_array[DCTSIZE2];
#endif
#ifdef DCT_FLOAT_SUPPORTED
  FLOAT_MULT_TYPE float_array[DCTSIZE2];
#endif
} multiplier_table;


/* The current scaled-IDCT routines require ISLOW-style multiplier tables,
 * so be sure to compile that code if either ISLOW or SCALING is requested.
 */
#ifdef DCT_ISLOW_SUPPORTED
#define PROVIDE_ISLOW_TABLES
#else
#ifdef IDCT_SCALING_SUPPORTED
#define PROVIDE_ISLOW_TABLES
#endif
#endif


/*
 * Prepare for an output pass.
 * Here we select the proper IDCT routine for each component and build
 * a matching multiplier table.
 */

METHODDEF(void)
start_pass (j_decompress_ptr cinfo)
{
  my_idct_ptr idct = (my_idct_ptr) cinfo->idct;
  int ci, i;
  jpeg_component_info *compptr;
  int method = 0;
  inverse_DCT_method_ptr method_ptr = NULL;
  JQUANT_TBL * qtbl;

  for (ci = 0, compptr = cinfo->comp_info; ci < cinfo->num_components;
       ci++, compptr++) {
    /* Select the proper IDCT routine for this component's scaling */
    switch (compptr->DCT_scaled_size) {
#ifdef IDCT_SCALING_SUPPORTED
    case 1:
      method_ptr = jpeg_idct_1x1;
      method = JDCT_ISLOW;	/* jidctred uses islow-style table */
      break;
    case 2:
#if defined(NV_ARM_NEON) && defined(__ARM_HAVE_NEON)
      if (cap_neon_idct_2x2()) {
        method_ptr = jsimd_idct_2x2;
      } else {
        method_ptr = jpeg_idct_2x2;
      }
#else
      method_ptr = jpeg_idct_2x2;
#endif
      method = JDCT_ISLOW;	/* jidctred uses islow-style table */
      break;
    case 4:
#if defined(NV_ARM_NEON) && defined(__ARM_HAVE_NEON)
	  if (cap_neon_idct_4x4()) {
        method_ptr = jsimd_idct_4x4;
      } else {
        method_ptr = jpeg_idct_4x4;
      }
#else
      method_ptr = jpeg_idct_4x4;
#endif
      method = JDCT_ISLOW;	/* jidctred uses islow-style table */
      break;
#endif
    case DCTSIZE:
      switch (cinfo->dct_method) {
#ifdef ANDROID_ARMV6_IDCT
      case JDCT_ISLOW:
      case JDCT_IFAST:
	method_ptr = jpeg_idct_armv6;
	method = JDCT_IFAST;
	break;
#else /* ANDROID_ARMV6_IDCT */
#ifdef ANDROID_INTELSSE2_IDCT
      case JDCT_ISLOW:
      case JDCT_IFAST:
	method_ptr = jpeg_idct_intelsse;
	method = JDCT_ISLOW; /* Use quant table of ISLOW.*/
	break;
#else /* ANDROID_INTELSSE2_IDCT */
#ifdef ANDROID_MIPS_IDCT
      case JDCT_ISLOW:
      case JDCT_IFAST:
	method_ptr = jpeg_idct_mips;
	method = JDCT_IFAST;
	break;
#else /* ANDROID_MIPS_IDCT */
#ifdef DCT_ISLOW_SUPPORTED
      case JDCT_ISLOW:
	method_ptr = jpeg_idct_islow;
	method = JDCT_ISLOW;
	break;
#endif
#ifdef DCT_IFAST_SUPPORTED
      case JDCT_IFAST:
#if defined(NV_ARM_NEON) && defined(__ARM_HAVE_NEON)
        if (cap_neon_idct_ifast()) {
          method_ptr = jsimd_idct_ifast;
        } else {
          method_ptr = jpeg_idct_ifast;
        }
#else
        method_ptr = jpeg_idct_ifast;
#endif
	method = JDCT_IFAST;
	break;
#endif
#endif /* ANDROID_MIPS_IDCT */
#endif /* ANDROID_INTELSSE2_IDCT*/
#endif /* ANDROID_ARMV6_IDCT */
#ifdef DCT_FLOAT_SUPPORTED
      case JDCT_FLOAT:
	method_ptr = jpeg_idct_float;
	method = JDCT_FLOAT;
	break;
#endif
      default:
	ERREXIT(cinfo, JERR_NOT_COMPILED);
	break;
      }
      break;
    default:
      ERREXIT1(cinfo, JERR_BAD_DCTSIZE, compptr->DCT_scaled_size);
      break;
    }
    idct->pub.inverse_DCT[ci] = method_ptr;
    /* Create multiplier table from quant table.
     * However, we can skip this if the component is uninteresting
     * or if we already built the table.  Also, if no quant table
     * has yet been saved for the component, we leave the
     * multiplier table all-zero; we'll be reading zeroes from the
     * coefficient controller's buffer anyway.
     */
    if (! compptr->component_needed || idct->cur_method[ci] == method)
      continue;
    qtbl = compptr->quant_table;
    if (qtbl == NULL)		/* happens if no data yet for component */
      continue;
    idct->cur_method[ci] = method;
    switch (method) {
#ifdef PROVIDE_ISLOW_TABLES
    case JDCT_ISLOW:
      {
	/* For LL&M IDCT method, multipliers are equal to raw quantization
	 * coefficients, but are stored as ints to ensure access efficiency.
	 */
	ISLOW_MULT_TYPE * ismtbl = (ISLOW_MULT_TYPE *) compptr->dct_table;
	for (i = 0; i < DCTSIZE2; i++) {
	  ismtbl[i] = (ISLOW_MULT_TYPE) qtbl->quantval[i];
	}
      }
      break;
#endif
#ifdef DCT_IFAST_SUPPORTED
    case JDCT_IFAST:
      {
	/* For AA&N IDCT method, multipliers are equal to quantization
	 * coefficients scaled by scalefactor[row]*scalefactor[col], where
	 *   scalefactor[0] = 1
	 *   scalefactor[k] = cos(k*PI/16) * sqrt(2)    for k=1..7
	 * For integer operation, the multiplier table is to be scaled by
	 * IFAST_SCALE_BITS.
	 */
	IFAST_MULT_TYPE * ifmtbl = (IFAST_MULT_TYPE *) compptr->dct_table;
#ifdef ANDROID_ARMV6_IDCT
	/* Precomputed values scaled up by 15 bits. */
	static const unsigned short scales[DCTSIZE2] = {
	  32768, 45451, 42813, 38531, 32768, 25746, 17734,  9041,
	  45451, 63042, 59384, 53444, 45451, 35710, 24598, 12540,
	  42813, 59384, 55938, 50343, 42813, 33638, 23170, 11812,
	  38531, 53444, 50343, 45308, 38531, 30274, 20853, 10631,
	  32768, 45451, 42813, 38531, 32768, 25746, 17734,  9041,
	  25746, 35710, 33638, 30274, 25746, 20228, 13933,  7103,
	  17734, 24598, 23170, 20853, 17734, 13933,  9598,  4893,
	   9041, 12540, 11812, 10631,  9041,  7103,  4893,  2494,
	};
	/* Inverse map of [7, 5, 1, 3, 0, 2, 4, 6]. */
	static const char orders[DCTSIZE] = {4, 2, 5, 3, 6, 1, 7, 0};
	/* Reorder the columns after transposing. */
	for (i = 0; i < DCTSIZE2; ++i) {
	  int j = ((i & 7) << 3) + orders[i >> 3];
	  ifmtbl[j] = (qtbl->quantval[i] * scales[i] + 2) >> 2;
	}
#else /* ANDROID_ARMV6_IDCT */

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

	for (i = 0; i < DCTSIZE2; i++) {
	  ifmtbl[i] = (IFAST_MULT_TYPE)
	    DESCALE(MULTIPLY16V16((INT32) qtbl->quantval[i],
				  (INT32) aanscales[i]),
		    CONST_BITS-IFAST_SCALE_BITS);
	}
#endif /* ANDROID_ARMV6_IDCT */
      }
      break;
#endif
#ifdef DCT_FLOAT_SUPPORTED
    case JDCT_FLOAT:
      {
	/* For float AA&N IDCT method, multipliers are equal to quantization
	 * coefficients scaled by scalefactor[row]*scalefactor[col], where
	 *   scalefactor[0] = 1
	 *   scalefactor[k] = cos(k*PI/16) * sqrt(2)    for k=1..7
	 */
	FLOAT_MULT_TYPE * fmtbl = (FLOAT_MULT_TYPE *) compptr->dct_table;
	int row, col;
	static const double aanscalefactor[DCTSIZE] = {
	  1.0, 1.387039845, 1.306562965, 1.175875602,
	  1.0, 0.785694958, 0.541196100, 0.275899379
	};

	i = 0;
	for (row = 0; row < DCTSIZE; row++) {
	  for (col = 0; col < DCTSIZE; col++) {
	    fmtbl[i] = (FLOAT_MULT_TYPE)
	      ((double) qtbl->quantval[i] *
	       aanscalefactor[row] * aanscalefactor[col]);
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


/*
 * Initialize IDCT manager.
 */

GLOBAL(void)
jinit_inverse_dct (j_decompress_ptr cinfo)
{
  my_idct_ptr idct;
  int ci;
  jpeg_component_info *compptr;

  idct = (my_idct_ptr)
    (*cinfo->mem->alloc_small) ((j_common_ptr) cinfo, JPOOL_IMAGE,
				SIZEOF(my_idct_controller));
  cinfo->idct = (struct jpeg_inverse_dct *) idct;
  idct->pub.start_pass = start_pass;

  for (ci = 0, compptr = cinfo->comp_info; ci < cinfo->num_components;
       ci++, compptr++) {
    /* Allocate and pre-zero a multiplier table for each component */
    compptr->dct_table =
      (*cinfo->mem->alloc_small) ((j_common_ptr) cinfo, JPOOL_IMAGE,
				  SIZEOF(multiplier_table));
    MEMZERO(compptr->dct_table, SIZEOF(multiplier_table));
    /* Mark multiplier table not yet set up for any method */
    idct->cur_method[ci] = -1;
  }
}
