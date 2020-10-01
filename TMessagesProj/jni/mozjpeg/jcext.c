/*
 * jcext.c
 *
 * Copyright (C) 2014, D. R. Commander.
 * Copyright (C) 2014, Mozilla Corporation.
 * For conditions of distribution and use, see the accompanying README file.
 *
 * This file contains accessor functions for extension parameters.  These
 * allow for extending the functionality of the libjpeg API without breaking
 * backward ABI compatibility.
 */

#define JPEG_INTERNALS
#include "jinclude.h"
#include "jpeglib.h"


GLOBAL(boolean)
jpeg_c_bool_param_supported (const j_compress_ptr cinfo, J_BOOLEAN_PARAM param)
{
  switch (param) {
  case JBOOLEAN_OPTIMIZE_SCANS:
  case JBOOLEAN_TRELLIS_QUANT:
  case JBOOLEAN_TRELLIS_QUANT_DC:
  case JBOOLEAN_TRELLIS_EOB_OPT:
  case JBOOLEAN_USE_LAMBDA_WEIGHT_TBL:
  case JBOOLEAN_USE_SCANS_IN_TRELLIS:
  case JBOOLEAN_TRELLIS_Q_OPT:
  case JBOOLEAN_OVERSHOOT_DERINGING:
    return TRUE;
  }

  return FALSE;
}


GLOBAL(void)
jpeg_c_set_bool_param (j_compress_ptr cinfo, J_BOOLEAN_PARAM param,
                       boolean value)
{
  switch(param) {
  case JBOOLEAN_OPTIMIZE_SCANS:
    cinfo->master->optimize_scans = value;
    break;
  case JBOOLEAN_TRELLIS_QUANT:
    cinfo->master->trellis_quant = value;
    break;
  case JBOOLEAN_TRELLIS_QUANT_DC:
    cinfo->master->trellis_quant_dc = value;
    break;
  case JBOOLEAN_TRELLIS_EOB_OPT:
    cinfo->master->trellis_eob_opt = value;
    break;
  case JBOOLEAN_USE_LAMBDA_WEIGHT_TBL:
    cinfo->master->use_lambda_weight_tbl = value;
    break;
  case JBOOLEAN_USE_SCANS_IN_TRELLIS:
    cinfo->master->use_scans_in_trellis = value;
    break;
  case JBOOLEAN_TRELLIS_Q_OPT:
    cinfo->master->trellis_q_opt = value;
    break;
  case JBOOLEAN_OVERSHOOT_DERINGING:
    cinfo->master->overshoot_deringing = value;
    break;
  default:
    ERREXIT(cinfo, JERR_BAD_PARAM);
  }
}


GLOBAL(boolean)
jpeg_c_get_bool_param (const j_compress_ptr cinfo, J_BOOLEAN_PARAM param)
{
  switch(param) {
  case JBOOLEAN_OPTIMIZE_SCANS:
    return cinfo->master->optimize_scans;
  case JBOOLEAN_TRELLIS_QUANT:
    return cinfo->master->trellis_quant;
  case JBOOLEAN_TRELLIS_QUANT_DC:
    return cinfo->master->trellis_quant_dc;
  case JBOOLEAN_TRELLIS_EOB_OPT:
    return cinfo->master->trellis_eob_opt;
  case JBOOLEAN_USE_LAMBDA_WEIGHT_TBL:
    return cinfo->master->use_lambda_weight_tbl;
  case JBOOLEAN_USE_SCANS_IN_TRELLIS:
    return cinfo->master->use_scans_in_trellis;
  case JBOOLEAN_TRELLIS_Q_OPT:
    return cinfo->master->trellis_q_opt;
  case JBOOLEAN_OVERSHOOT_DERINGING:
    return cinfo->master->overshoot_deringing;
  default:
    ERREXIT(cinfo, JERR_BAD_PARAM);
  }

  return FALSE;
}


GLOBAL(boolean)
jpeg_c_float_param_supported (const j_compress_ptr cinfo, J_FLOAT_PARAM param)
{
  switch (param) {
  case JFLOAT_LAMBDA_LOG_SCALE1:
  case JFLOAT_LAMBDA_LOG_SCALE2:
  case JFLOAT_TRELLIS_DELTA_DC_WEIGHT:
    return TRUE;
  }

  return FALSE;
}


GLOBAL(void)
jpeg_c_set_float_param (j_compress_ptr cinfo, J_FLOAT_PARAM param, float value)
{
  switch (param) {
  case JFLOAT_LAMBDA_LOG_SCALE1:
    cinfo->master->lambda_log_scale1 = value;
    break;
  case JFLOAT_LAMBDA_LOG_SCALE2:
    cinfo->master->lambda_log_scale2 = value;
    break;
  case JFLOAT_TRELLIS_DELTA_DC_WEIGHT:
    cinfo->master->trellis_delta_dc_weight = value;
    break;
  default:
    ERREXIT(cinfo, JERR_BAD_PARAM);
  }
}


GLOBAL(float)
jpeg_c_get_float_param (const j_compress_ptr cinfo, J_FLOAT_PARAM param)
{
  switch (param) {
  case JFLOAT_LAMBDA_LOG_SCALE1:
    return cinfo->master->lambda_log_scale1;
  case JFLOAT_LAMBDA_LOG_SCALE2:
    return cinfo->master->lambda_log_scale2;
  case JFLOAT_TRELLIS_DELTA_DC_WEIGHT:
    return cinfo->master->trellis_delta_dc_weight;
  default:
    ERREXIT(cinfo, JERR_BAD_PARAM);
  }

  return -1;
}


GLOBAL(boolean)
jpeg_c_int_param_supported (const j_compress_ptr cinfo, J_INT_PARAM param)
{
  switch (param) {
  case JINT_COMPRESS_PROFILE:
  case JINT_TRELLIS_FREQ_SPLIT:
  case JINT_TRELLIS_NUM_LOOPS:
  case JINT_BASE_QUANT_TBL_IDX:
  case JINT_DC_SCAN_OPT_MODE:
    return TRUE;
  }

  return FALSE;
}


GLOBAL(void)
jpeg_c_set_int_param (j_compress_ptr cinfo, J_INT_PARAM param, int value)
{
  switch (param) {
  case JINT_COMPRESS_PROFILE:
    switch (value) {
    case JCP_MAX_COMPRESSION:
    case JCP_FASTEST:
      cinfo->master->compress_profile = value;
      break;
    default:
      ERREXIT(cinfo, JERR_BAD_PARAM_VALUE);
    }
    break;
  case JINT_TRELLIS_FREQ_SPLIT:
    cinfo->master->trellis_freq_split = value;
    break;
  case JINT_TRELLIS_NUM_LOOPS:
    cinfo->master->trellis_num_loops = value;
    break;
  case JINT_BASE_QUANT_TBL_IDX:
    if (value >= 0 && value <= 8)
      cinfo->master->quant_tbl_master_idx = value;
    break;
  case JINT_DC_SCAN_OPT_MODE:
    cinfo->master->dc_scan_opt_mode = value;
    break;
  default:
    ERREXIT(cinfo, JERR_BAD_PARAM);
  }
}


GLOBAL(int)
jpeg_c_get_int_param (const j_compress_ptr cinfo, J_INT_PARAM param)
{
  switch (param) {
  case JINT_COMPRESS_PROFILE:
    return cinfo->master->compress_profile;
  case JINT_TRELLIS_FREQ_SPLIT:
    return cinfo->master->trellis_freq_split;
  case JINT_TRELLIS_NUM_LOOPS:
    return cinfo->master->trellis_num_loops;
  case JINT_BASE_QUANT_TBL_IDX:
    return cinfo->master->quant_tbl_master_idx;
  case JINT_DC_SCAN_OPT_MODE:
    return cinfo->master->dc_scan_opt_mode;
  default:
    ERREXIT(cinfo, JERR_BAD_PARAM);
  }

  return -1;
}
