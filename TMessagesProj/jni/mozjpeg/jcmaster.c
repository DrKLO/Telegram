/*
 * jcmaster.c
 *
 * This file was part of the Independent JPEG Group's software:
 * Copyright (C) 1991-1997, Thomas G. Lane.
 * Modified 2003-2010 by Guido Vollbeding.
 * libjpeg-turbo Modifications:
 * Copyright (C) 2010, 2016, 2018, D. R. Commander.
 * mozjpeg Modifications:
 * Copyright (C) 2014, Mozilla Corporation.
 * For conditions of distribution and use, see the accompanying README file.
 *
 * This file contains master control logic for the JPEG compressor.
 * These routines are concerned with parameter validation, initial setup,
 * and inter-pass control (determining the number of passes and the work
 * to be done in each pass).
 */

#define JPEG_INTERNALS
#include "jinclude.h"
#include "jpeglib.h"
#include "jpegcomp.h"
#include "jconfigint.h"
#include "jmemsys.h"
#include "jcmaster.h"


  /*
 * Support routines that do various essential calculations.
 */

#if JPEG_LIB_VERSION >= 70
/*
 * Compute JPEG image dimensions and related values.
 * NOTE: this is exported for possible use by application.
 * Hence it mustn't do anything that can't be done twice.
 */

GLOBAL(void)
jpeg_calc_jpeg_dimensions (j_compress_ptr cinfo)
/* Do computations that are needed before master selection phase */
{
  /* Hardwire it to "no scaling" */
  cinfo->jpeg_width = cinfo->image_width;
  cinfo->jpeg_height = cinfo->image_height;
  cinfo->min_DCT_h_scaled_size = DCTSIZE;
  cinfo->min_DCT_v_scaled_size = DCTSIZE;
}
#endif


LOCAL(void)
initial_setup (j_compress_ptr cinfo, boolean transcode_only)
/* Do computations that are needed before master selection phase */
{
  int ci;
  jpeg_component_info *compptr;
  long samplesperrow;
  JDIMENSION jd_samplesperrow;

#if JPEG_LIB_VERSION >= 70
#if JPEG_LIB_VERSION >= 80
  if (!transcode_only)
#endif
    jpeg_calc_jpeg_dimensions(cinfo);
#endif

  /* Sanity check on image dimensions */
  if (cinfo->_jpeg_height <= 0 || cinfo->_jpeg_width <= 0 ||
      cinfo->num_components <= 0 || cinfo->input_components <= 0)
    ERREXIT(cinfo, JERR_EMPTY_IMAGE);

  /* Make sure image isn't bigger than I can handle */
  if ((long) cinfo->_jpeg_height > (long) JPEG_MAX_DIMENSION ||
      (long) cinfo->_jpeg_width > (long) JPEG_MAX_DIMENSION)
    ERREXIT1(cinfo, JERR_IMAGE_TOO_BIG, (unsigned int) JPEG_MAX_DIMENSION);

  /* Width of an input scanline must be representable as JDIMENSION. */
  samplesperrow = (long) cinfo->image_width * (long) cinfo->input_components;
  jd_samplesperrow = (JDIMENSION) samplesperrow;
  if ((long) jd_samplesperrow != samplesperrow)
    ERREXIT(cinfo, JERR_WIDTH_OVERFLOW);

  /* For now, precision must match compiled-in value... */
  if (cinfo->data_precision != BITS_IN_JSAMPLE)
    ERREXIT1(cinfo, JERR_BAD_PRECISION, cinfo->data_precision);

  /* Check that number of components won't exceed internal array sizes */
  if (cinfo->num_components > MAX_COMPONENTS)
    ERREXIT2(cinfo, JERR_COMPONENT_COUNT, cinfo->num_components,
             MAX_COMPONENTS);

  /* Compute maximum sampling factors; check factor validity */
  cinfo->max_h_samp_factor = 1;
  cinfo->max_v_samp_factor = 1;
  for (ci = 0, compptr = cinfo->comp_info; ci < cinfo->num_components;
       ci++, compptr++) {
    if (compptr->h_samp_factor <= 0 ||
        compptr->h_samp_factor > MAX_SAMP_FACTOR ||
        compptr->v_samp_factor <= 0 ||
        compptr->v_samp_factor > MAX_SAMP_FACTOR)
      ERREXIT(cinfo, JERR_BAD_SAMPLING);
    cinfo->max_h_samp_factor = MAX(cinfo->max_h_samp_factor,
                                   compptr->h_samp_factor);
    cinfo->max_v_samp_factor = MAX(cinfo->max_v_samp_factor,
                                   compptr->v_samp_factor);
  }

  /* Compute dimensions of components */
  for (ci = 0, compptr = cinfo->comp_info; ci < cinfo->num_components;
       ci++, compptr++) {
    /* Fill in the correct component_index value; don't rely on application */
    compptr->component_index = ci;
    /* For compression, we never do DCT scaling. */
#if JPEG_LIB_VERSION >= 70
    compptr->DCT_h_scaled_size = compptr->DCT_v_scaled_size = DCTSIZE;
#else
    compptr->DCT_scaled_size = DCTSIZE;
#endif
    /* Size in DCT blocks */
    compptr->width_in_blocks = (JDIMENSION)
      jdiv_round_up((long) cinfo->_jpeg_width * (long) compptr->h_samp_factor,
                    (long) (cinfo->max_h_samp_factor * DCTSIZE));
    compptr->height_in_blocks = (JDIMENSION)
      jdiv_round_up((long) cinfo->_jpeg_height * (long) compptr->v_samp_factor,
                    (long) (cinfo->max_v_samp_factor * DCTSIZE));
    /* Size in samples */
    compptr->downsampled_width = (JDIMENSION)
      jdiv_round_up((long) cinfo->_jpeg_width * (long) compptr->h_samp_factor,
                    (long) cinfo->max_h_samp_factor);
    compptr->downsampled_height = (JDIMENSION)
      jdiv_round_up((long) cinfo->_jpeg_height * (long) compptr->v_samp_factor,
                    (long) cinfo->max_v_samp_factor);
    /* Mark component needed (this flag isn't actually used for compression) */
    compptr->component_needed = TRUE;
  }

  /* Compute number of fully interleaved MCU rows (number of times that
   * main controller will call coefficient controller).
   */
  cinfo->total_iMCU_rows = (JDIMENSION)
    jdiv_round_up((long) cinfo->_jpeg_height,
                  (long) (cinfo->max_v_samp_factor*DCTSIZE));
}


#ifdef C_MULTISCAN_FILES_SUPPORTED

LOCAL(void)
validate_script (j_compress_ptr cinfo)
/* Verify that the scan script in cinfo->scan_info[] is valid; also
 * determine whether it uses progressive JPEG, and set cinfo->progressive_mode.
 */
{
  const jpeg_scan_info *scanptr;
  int scanno, ncomps, ci, coefi, thisi;
  int Ss, Se, Ah, Al;
  boolean component_sent[MAX_COMPONENTS];
#ifdef C_PROGRESSIVE_SUPPORTED
  int *last_bitpos_ptr;
  int last_bitpos[MAX_COMPONENTS][DCTSIZE2];
  /* -1 until that coefficient has been seen; then last Al for it */
#endif

  if (cinfo->master->optimize_scans) {
    cinfo->progressive_mode = TRUE;
    /* When we optimize scans, there is redundancy in the scan list
     * and this function will fail. Therefore skip all this checking
     */
    return;
  }
  
  if (cinfo->num_scans <= 0)
    ERREXIT1(cinfo, JERR_BAD_SCAN_SCRIPT, 0);

  /* For sequential JPEG, all scans must have Ss=0, Se=DCTSIZE2-1;
   * for progressive JPEG, no scan can have this.
   */
  scanptr = cinfo->scan_info;
  if (scanptr->Ss != 0 || scanptr->Se != DCTSIZE2-1) {
#ifdef C_PROGRESSIVE_SUPPORTED
    cinfo->progressive_mode = TRUE;
    last_bitpos_ptr = & last_bitpos[0][0];
    for (ci = 0; ci < cinfo->num_components; ci++)
      for (coefi = 0; coefi < DCTSIZE2; coefi++)
        *last_bitpos_ptr++ = -1;
#else
    ERREXIT(cinfo, JERR_NOT_COMPILED);
#endif
  } else {
    cinfo->progressive_mode = FALSE;
    for (ci = 0; ci < cinfo->num_components; ci++)
      component_sent[ci] = FALSE;
  }

  for (scanno = 1; scanno <= cinfo->num_scans; scanptr++, scanno++) {
    /* Validate component indexes */
    ncomps = scanptr->comps_in_scan;
    if (ncomps <= 0 || ncomps > MAX_COMPS_IN_SCAN)
      ERREXIT2(cinfo, JERR_COMPONENT_COUNT, ncomps, MAX_COMPS_IN_SCAN);
    for (ci = 0; ci < ncomps; ci++) {
      thisi = scanptr->component_index[ci];
      if (thisi < 0 || thisi >= cinfo->num_components)
        ERREXIT1(cinfo, JERR_BAD_SCAN_SCRIPT, scanno);
      /* Components must appear in SOF order within each scan */
      if (ci > 0 && thisi <= scanptr->component_index[ci-1])
        ERREXIT1(cinfo, JERR_BAD_SCAN_SCRIPT, scanno);
    }
    /* Validate progression parameters */
    Ss = scanptr->Ss;
    Se = scanptr->Se;
    Ah = scanptr->Ah;
    Al = scanptr->Al;
    if (cinfo->progressive_mode) {
#ifdef C_PROGRESSIVE_SUPPORTED
      /* Rec. ITU-T T.81 | ISO/IEC 10918-1 simply gives the ranges 0..13 for Ah
       * and Al, but that seems wrong: the upper bound ought to depend on data
       * precision.  Perhaps they really meant 0..N+1 for N-bit precision.
       * Here we allow 0..10 for 8-bit data; Al larger than 10 results in
       * out-of-range reconstructed DC values during the first DC scan,
       * which might cause problems for some decoders.
       */
#if BITS_IN_JSAMPLE == 8
#define MAX_AH_AL 10
#else
#define MAX_AH_AL 13
#endif
      if (Ss < 0 || Ss >= DCTSIZE2 || Se < Ss || Se >= DCTSIZE2 ||
          Ah < 0 || Ah > MAX_AH_AL || Al < 0 || Al > MAX_AH_AL)
        ERREXIT1(cinfo, JERR_BAD_PROG_SCRIPT, scanno);
      if (Ss == 0) {
        if (Se != 0)            /* DC and AC together not OK */
          ERREXIT1(cinfo, JERR_BAD_PROG_SCRIPT, scanno);
      } else {
        if (ncomps != 1)        /* AC scans must be for only one component */
          ERREXIT1(cinfo, JERR_BAD_PROG_SCRIPT, scanno);
      }
      for (ci = 0; ci < ncomps; ci++) {
        last_bitpos_ptr = & last_bitpos[scanptr->component_index[ci]][0];
        if (Ss != 0 && last_bitpos_ptr[0] < 0) /* AC without prior DC scan */
          ERREXIT1(cinfo, JERR_BAD_PROG_SCRIPT, scanno);
        for (coefi = Ss; coefi <= Se; coefi++) {
          if (last_bitpos_ptr[coefi] < 0) {
            /* first scan of this coefficient */
            if (Ah != 0)
              ERREXIT1(cinfo, JERR_BAD_PROG_SCRIPT, scanno);
          } else {
            /* not first scan */
            if (Ah != last_bitpos_ptr[coefi] || Al != Ah-1)
              ERREXIT1(cinfo, JERR_BAD_PROG_SCRIPT, scanno);
          }
          last_bitpos_ptr[coefi] = Al;
        }
      }
#endif
    } else {
      /* For sequential JPEG, all progression parameters must be these: */
      if (Ss != 0 || Se != DCTSIZE2-1 || Ah != 0 || Al != 0)
        ERREXIT1(cinfo, JERR_BAD_PROG_SCRIPT, scanno);
      /* Make sure components are not sent twice */
      for (ci = 0; ci < ncomps; ci++) {
        thisi = scanptr->component_index[ci];
        if (component_sent[thisi])
          ERREXIT1(cinfo, JERR_BAD_SCAN_SCRIPT, scanno);
        component_sent[thisi] = TRUE;
      }
    }
  }

  /* Now verify that everything got sent. */
  if (cinfo->progressive_mode) {
#ifdef C_PROGRESSIVE_SUPPORTED
    /* For progressive mode, we only check that at least some DC data
     * got sent for each component; the spec does not require that all bits
     * of all coefficients be transmitted.  Would it be wiser to enforce
     * transmission of all coefficient bits??
     */
    for (ci = 0; ci < cinfo->num_components; ci++) {
      if (last_bitpos[ci][0] < 0)
        ERREXIT(cinfo, JERR_MISSING_DATA);
    }
#endif
  } else {
    for (ci = 0; ci < cinfo->num_components; ci++) {
      if (! component_sent[ci])
        ERREXIT(cinfo, JERR_MISSING_DATA);
    }
  }
}

#endif /* C_MULTISCAN_FILES_SUPPORTED */


LOCAL(void)
select_scan_parameters (j_compress_ptr cinfo)
/* Set up the scan parameters for the current scan */
{
  int ci;

#ifdef C_MULTISCAN_FILES_SUPPORTED
  my_master_ptr master = (my_master_ptr) cinfo->master;
  if (master->pass_number < master->pass_number_scan_opt_base) {
    cinfo->comps_in_scan = 1;
    if (cinfo->master->use_scans_in_trellis) {
      cinfo->cur_comp_info[0] =
        &cinfo->comp_info[master->pass_number / 
                          (4 * cinfo->master->trellis_num_loops)];
      cinfo->Ss = (master->pass_number % 4 < 2) ?
                  1 : cinfo->master->trellis_freq_split + 1;
      cinfo->Se = (master->pass_number % 4 < 2) ?
                  cinfo->master->trellis_freq_split : DCTSIZE2 - 1;
    } else {
      cinfo->cur_comp_info[0] =
        &cinfo->comp_info[master->pass_number /
                          (2 * cinfo->master->trellis_num_loops)];
      cinfo->Ss = 1;
      cinfo->Se = DCTSIZE2-1;
    }
  }
  else if (cinfo->scan_info != NULL) {
    /* Prepare for current scan --- the script is already validated */
    const jpeg_scan_info *scanptr = cinfo->scan_info + master->scan_number;

    cinfo->comps_in_scan = scanptr->comps_in_scan;
    for (ci = 0; ci < scanptr->comps_in_scan; ci++) {
      cinfo->cur_comp_info[ci] =
        &cinfo->comp_info[scanptr->component_index[ci]];
    }
    cinfo->Ss = scanptr->Ss;
    cinfo->Se = scanptr->Se;
    cinfo->Ah = scanptr->Ah;
    cinfo->Al = scanptr->Al;
    if (cinfo->master->optimize_scans) {
      /* luma frequency split passes */
      if (master->scan_number >= cinfo->master->num_scans_luma_dc +
                                 3 * cinfo->master->Al_max_luma + 2 &&
          master->scan_number < cinfo->master->num_scans_luma)
        cinfo->Al = master->best_Al_luma;
      /* chroma frequency split passes */
      if (master->scan_number >= cinfo->master->num_scans_luma +
                                 cinfo->master->num_scans_chroma_dc +
                                 (6 * cinfo->master->Al_max_chroma + 4) &&
          master->scan_number < cinfo->num_scans)
        cinfo->Al = master->best_Al_chroma;
  }
    /* save value for later retrieval during printout of scans */
    master->actual_Al[master->scan_number] = cinfo->Al;
  }
  else
#endif
  {
    /* Prepare for single sequential-JPEG scan containing all components */
    if (cinfo->num_components > MAX_COMPS_IN_SCAN)
      ERREXIT2(cinfo, JERR_COMPONENT_COUNT, cinfo->num_components,
               MAX_COMPS_IN_SCAN);
    cinfo->comps_in_scan = cinfo->num_components;
    for (ci = 0; ci < cinfo->num_components; ci++) {
      cinfo->cur_comp_info[ci] = &cinfo->comp_info[ci];
    }
    cinfo->Ss = 0;
    cinfo->Se = DCTSIZE2-1;
    cinfo->Ah = 0;
    cinfo->Al = 0;
  }
}


LOCAL(void)
per_scan_setup (j_compress_ptr cinfo)
/* Do computations that are needed before processing a JPEG scan */
/* cinfo->comps_in_scan and cinfo->cur_comp_info[] are already set */
{
  int ci, mcublks, tmp;
  jpeg_component_info *compptr;

  if (cinfo->comps_in_scan == 1) {

    /* Noninterleaved (single-component) scan */
    compptr = cinfo->cur_comp_info[0];

    /* Overall image size in MCUs */
    cinfo->MCUs_per_row = compptr->width_in_blocks;
    cinfo->MCU_rows_in_scan = compptr->height_in_blocks;

    /* For noninterleaved scan, always one block per MCU */
    compptr->MCU_width = 1;
    compptr->MCU_height = 1;
    compptr->MCU_blocks = 1;
    compptr->MCU_sample_width = DCTSIZE;
    compptr->last_col_width = 1;
    /* For noninterleaved scans, it is convenient to define last_row_height
     * as the number of block rows present in the last iMCU row.
     */
    tmp = (int) (compptr->height_in_blocks % compptr->v_samp_factor);
    if (tmp == 0) tmp = compptr->v_samp_factor;
    compptr->last_row_height = tmp;

    /* Prepare array describing MCU composition */
    cinfo->blocks_in_MCU = 1;
    cinfo->MCU_membership[0] = 0;

  } else {

    /* Interleaved (multi-component) scan */
    if (cinfo->comps_in_scan <= 0 || cinfo->comps_in_scan > MAX_COMPS_IN_SCAN)
      ERREXIT2(cinfo, JERR_COMPONENT_COUNT, cinfo->comps_in_scan,
               MAX_COMPS_IN_SCAN);

    /* Overall image size in MCUs */
    cinfo->MCUs_per_row = (JDIMENSION)
      jdiv_round_up((long) cinfo->_jpeg_width,
                    (long) (cinfo->max_h_samp_factor*DCTSIZE));
    cinfo->MCU_rows_in_scan = (JDIMENSION)
      jdiv_round_up((long) cinfo->_jpeg_height,
                    (long) (cinfo->max_v_samp_factor*DCTSIZE));

    cinfo->blocks_in_MCU = 0;

    for (ci = 0; ci < cinfo->comps_in_scan; ci++) {
      compptr = cinfo->cur_comp_info[ci];
      /* Sampling factors give # of blocks of component in each MCU */
      compptr->MCU_width = compptr->h_samp_factor;
      compptr->MCU_height = compptr->v_samp_factor;
      compptr->MCU_blocks = compptr->MCU_width * compptr->MCU_height;
      compptr->MCU_sample_width = compptr->MCU_width * DCTSIZE;
      /* Figure number of non-dummy blocks in last MCU column & row */
      tmp = (int) (compptr->width_in_blocks % compptr->MCU_width);
      if (tmp == 0) tmp = compptr->MCU_width;
      compptr->last_col_width = tmp;
      tmp = (int) (compptr->height_in_blocks % compptr->MCU_height);
      if (tmp == 0) tmp = compptr->MCU_height;
      compptr->last_row_height = tmp;
      /* Prepare array describing MCU composition */
      mcublks = compptr->MCU_blocks;
      if (cinfo->blocks_in_MCU + mcublks > C_MAX_BLOCKS_IN_MCU)
        ERREXIT(cinfo, JERR_BAD_MCU_SIZE);
      while (mcublks-- > 0) {
        cinfo->MCU_membership[cinfo->blocks_in_MCU++] = ci;
      }
    }

  }

  /* Convert restart specified in rows to actual MCU count. */
  /* Note that count must fit in 16 bits, so we provide limiting. */
  if (cinfo->restart_in_rows > 0) {
    long nominal = (long) cinfo->restart_in_rows * (long) cinfo->MCUs_per_row;
    cinfo->restart_interval = (unsigned int) MIN(nominal, 65535L);
  }
}


/*
 * Per-pass setup.
 * This is called at the beginning of each pass.  We determine which modules
 * will be active during this pass and give them appropriate start_pass calls.
 * We also set is_last_pass to indicate whether any more passes will be
 * required.
 */

METHODDEF(void)
prepare_for_pass (j_compress_ptr cinfo)
{
  my_master_ptr master = (my_master_ptr) cinfo->master;
  cinfo->master->trellis_passes =
    master->pass_number < master->pass_number_scan_opt_base;

  switch (master->pass_type) {
  case main_pass:
    /* Initial pass: will collect input data, and do either Huffman
     * optimization or data output for the first scan.
     */
    select_scan_parameters(cinfo);
    per_scan_setup(cinfo);
    if (! cinfo->raw_data_in) {
      (*cinfo->cconvert->start_pass) (cinfo);
      (*cinfo->downsample->start_pass) (cinfo);
      (*cinfo->prep->start_pass) (cinfo, JBUF_PASS_THRU);
    }
    (*cinfo->fdct->start_pass) (cinfo);
    (*cinfo->entropy->start_pass) (cinfo, (cinfo->optimize_coding || cinfo->master->trellis_quant) && !cinfo->arith_code);
    (*cinfo->coef->start_pass) (cinfo,
                                (master->total_passes > 1 ?
                                 JBUF_SAVE_AND_PASS : JBUF_PASS_THRU));
    (*cinfo->main->start_pass) (cinfo, JBUF_PASS_THRU);
    if (cinfo->optimize_coding || cinfo->master->trellis_quant) {
      /* No immediate data output; postpone writing frame/scan headers */
      master->pub.call_pass_startup = FALSE;
    } else {
      /* Will write frame/scan headers at first jpeg_write_scanlines call */
      master->pub.call_pass_startup = TRUE;
    }
    break;
#ifdef ENTROPY_OPT_SUPPORTED
  case huff_opt_pass:
    /* Do Huffman optimization for a scan after the first one. */
    select_scan_parameters(cinfo);
    per_scan_setup(cinfo);
    if (cinfo->Ss != 0 || cinfo->Ah == 0 || cinfo->arith_code) {
      (*cinfo->entropy->start_pass) (cinfo, TRUE);
      (*cinfo->coef->start_pass) (cinfo, JBUF_CRANK_DEST);
      master->pub.call_pass_startup = FALSE;
      break;
    }
    /* Special case: Huffman DC refinement scans need no Huffman table
     * and therefore we can skip the optimization pass for them.
     */
    master->pass_type = output_pass;
    master->pass_number++;
#endif
    /*FALLTHROUGH*/
  case output_pass:
    /* Do a data-output pass. */
    /* We need not repeat per-scan setup if prior optimization pass did it. */
    if (! cinfo->optimize_coding) {
      select_scan_parameters(cinfo);
      per_scan_setup(cinfo);
    }
    if (cinfo->master->optimize_scans) {
      master->saved_dest = cinfo->dest;
      cinfo->dest = NULL;
      master->scan_size[master->scan_number] = 0;
      jpeg_mem_dest_internal(cinfo, &master->scan_buffer[master->scan_number], &master->scan_size[master->scan_number], JPOOL_IMAGE);
      (*cinfo->dest->init_destination)(cinfo);
    }
    (*cinfo->entropy->start_pass) (cinfo, FALSE);
    (*cinfo->coef->start_pass) (cinfo, JBUF_CRANK_DEST);
    /* We emit frame/scan headers now */
    if (master->scan_number == 0)
      (*cinfo->marker->write_frame_header) (cinfo);
    (*cinfo->marker->write_scan_header) (cinfo);
    master->pub.call_pass_startup = FALSE;
    break;
  case trellis_pass:
    if (master->pass_number %
        (cinfo->num_components * (cinfo->master->use_scans_in_trellis ? 4 : 2)) == 1 &&
        cinfo->master->trellis_q_opt) {
      int i, j;

      for (i = 0; i < NUM_QUANT_TBLS; i++) {
        for (j = 1; j < DCTSIZE2; j++) {
          cinfo->master->norm_src[i][j] = 0.0;
          cinfo->master->norm_coef[i][j] = 0.0;
        }
      }
    }
    (*cinfo->entropy->start_pass) (cinfo, !cinfo->arith_code);
    (*cinfo->coef->start_pass) (cinfo, JBUF_REQUANT);
    master->pub.call_pass_startup = FALSE;
    break;
      
  default:
    ERREXIT(cinfo, JERR_NOT_COMPILED);
  }

  master->pub.is_last_pass = (master->pass_number == master->total_passes-1);

  /* Set up progress monitor's pass info if present */
  if (cinfo->progress != NULL) {
    cinfo->progress->completed_passes = master->pass_number;
    cinfo->progress->total_passes = master->total_passes;
  }
}


/*
 * Special start-of-pass hook.
 * This is called by jpeg_write_scanlines if call_pass_startup is TRUE.
 * In single-pass processing, we need this hook because we don't want to
 * write frame/scan headers during jpeg_start_compress; we want to let the
 * application write COM markers etc. between jpeg_start_compress and the
 * jpeg_write_scanlines loop.
 * In multi-pass processing, this routine is not used.
 */

METHODDEF(void)
pass_startup (j_compress_ptr cinfo)
{
  cinfo->master->call_pass_startup = FALSE; /* reset flag so call only once */

  (*cinfo->marker->write_frame_header) (cinfo);
  (*cinfo->marker->write_scan_header) (cinfo);
}


LOCAL(void)
copy_buffer (j_compress_ptr cinfo, int scan_idx)
{
  my_master_ptr master = (my_master_ptr) cinfo->master;
  
  unsigned long size = master->scan_size[scan_idx];
  unsigned char * src = master->scan_buffer[scan_idx];
  int i;
  
  if (cinfo->err->trace_level > 0) {
    fprintf(stderr, "SCAN ");
    for (i = 0; i < cinfo->scan_info[scan_idx].comps_in_scan; i++)
      fprintf(stderr, "%s%d", (i==0)?"":",", cinfo->scan_info[scan_idx].component_index[i]);
    fprintf(stderr, ": %d %d", cinfo->scan_info[scan_idx].Ss, cinfo->scan_info[scan_idx].Se);
    fprintf(stderr, " %d %d", cinfo->scan_info[scan_idx].Ah, master->actual_Al[scan_idx]);
    fprintf(stderr, "\n");
  }
  
  while (size >= cinfo->dest->free_in_buffer)
  {
    MEMCOPY(cinfo->dest->next_output_byte, src, cinfo->dest->free_in_buffer);
    src += cinfo->dest->free_in_buffer;
    size -= cinfo->dest->free_in_buffer;
    cinfo->dest->next_output_byte += cinfo->dest->free_in_buffer;
    cinfo->dest->free_in_buffer = 0;
    
    if (!(*cinfo->dest->empty_output_buffer)(cinfo))
      ERREXIT(cinfo, JERR_UNSUPPORTED_SUSPEND);
  }

  MEMCOPY(cinfo->dest->next_output_byte, src, size);
  cinfo->dest->next_output_byte += size;
  cinfo->dest->free_in_buffer -= size;
}

LOCAL(void)
select_scans (j_compress_ptr cinfo, int next_scan_number)
{
  my_master_ptr master = (my_master_ptr) cinfo->master;
  
  int base_scan_idx = 0;
  int luma_freq_split_scan_start = cinfo->master->num_scans_luma_dc +
                                   3 * cinfo->master->Al_max_luma + 2;
  int chroma_freq_split_scan_start = cinfo->master->num_scans_luma +
                                     cinfo->master->num_scans_chroma_dc +
                                     (6 * cinfo->master->Al_max_chroma + 4);
  int passes_per_scan = cinfo->optimize_coding ? 2 : 1;
  
  if (next_scan_number > 1 && next_scan_number <= luma_freq_split_scan_start) {
    if ((next_scan_number - 1) % 3 == 2) {
      int Al = (next_scan_number - 1) / 3;
      int i;
      unsigned long cost = 0;
      cost += master->scan_size[next_scan_number-2];
      cost += master->scan_size[next_scan_number-1];
      for (i = 0; i < Al; i++)
        cost += master->scan_size[3 + 3*i];
      
      if (Al == 0 || cost < master->best_cost) {
        master->best_cost = cost;
        master->best_Al_luma = Al;
      } else {
        master->scan_number = luma_freq_split_scan_start - 1;
        master->pass_number = passes_per_scan * (master->scan_number + 1) - 1 + master->pass_number_scan_opt_base;
      }
    }
  
  } else if (next_scan_number > luma_freq_split_scan_start &&
             next_scan_number <= cinfo->master->num_scans_luma) {
    if (next_scan_number == luma_freq_split_scan_start + 1) {
      master->best_freq_split_idx_luma = 0;
      master->best_cost = master->scan_size[next_scan_number-1];
      
    } else if ((next_scan_number - luma_freq_split_scan_start) % 2 == 1) {
      int idx = (next_scan_number - luma_freq_split_scan_start) >> 1;
      unsigned long cost = 0;
      cost += master->scan_size[next_scan_number-2];
      cost += master->scan_size[next_scan_number-1];
      
      if (cost < master->best_cost) {
        master->best_cost = cost;
        master->best_freq_split_idx_luma = idx;
      }
      
      /* if after testing first 3, no split is the best, don't search further */
      if ((idx == 2 && master->best_freq_split_idx_luma == 0) ||
          (idx == 3 && master->best_freq_split_idx_luma != 2) ||
          (idx == 4 && master->best_freq_split_idx_luma != 4)) {
        master->scan_number = cinfo->master->num_scans_luma - 1;
        master->pass_number = passes_per_scan * (master->scan_number + 1) - 1 + master->pass_number_scan_opt_base;
        master->pub.is_last_pass = (master->pass_number == master->total_passes - 1);
      }
    }
    
  } else if (cinfo->num_scans > cinfo->master->num_scans_luma) {

    if (next_scan_number == cinfo->master->num_scans_luma + 
                            cinfo->master->num_scans_chroma_dc) {
      base_scan_idx = cinfo->master->num_scans_luma;

      master->interleave_chroma_dc = master->scan_size[base_scan_idx] <= master->scan_size[base_scan_idx+1] + master->scan_size[base_scan_idx+2];
      
    } else if (next_scan_number > cinfo->master->num_scans_luma +
                                  cinfo->master->num_scans_chroma_dc &&
               next_scan_number <= chroma_freq_split_scan_start) {
      base_scan_idx = cinfo->master->num_scans_luma +
                      cinfo->master->num_scans_chroma_dc;
      if ((next_scan_number - base_scan_idx) % 6 == 4) {
        int Al = (next_scan_number - base_scan_idx) / 6;
        int i;
        unsigned long cost = 0;
        cost += master->scan_size[next_scan_number-4];
        cost += master->scan_size[next_scan_number-3];
        cost += master->scan_size[next_scan_number-2];
        cost += master->scan_size[next_scan_number-1];
        for (i = 0; i < Al; i++) {
          cost += master->scan_size[base_scan_idx + 4 + 6*i];
          cost += master->scan_size[base_scan_idx + 5 + 6*i];
        }
        
        if (Al == 0 || cost < master->best_cost) {
          master->best_cost = cost;
          master->best_Al_chroma = Al;
        } else {
          master->scan_number = chroma_freq_split_scan_start - 1;
          master->pass_number = passes_per_scan * (master->scan_number + 1) - 1 + master->pass_number_scan_opt_base;
        }
      }

    } else if (next_scan_number > chroma_freq_split_scan_start && next_scan_number <= cinfo->num_scans) {
      if (next_scan_number == chroma_freq_split_scan_start + 2) {
        master->best_freq_split_idx_chroma = 0;
        master->best_cost  = master->scan_size[next_scan_number-2];
        master->best_cost += master->scan_size[next_scan_number-1];
        
      } else if ((next_scan_number - chroma_freq_split_scan_start) % 4 == 2) {
        int idx = (next_scan_number - chroma_freq_split_scan_start) >> 2;
        unsigned long cost = 0;
        cost += master->scan_size[next_scan_number-4];
        cost += master->scan_size[next_scan_number-3];
        cost += master->scan_size[next_scan_number-2];
        cost += master->scan_size[next_scan_number-1];
        
        if (cost < master->best_cost) {
          master->best_cost = cost;
          master->best_freq_split_idx_chroma = idx;
        }
        
        /* if after testing first 3, no split is the best, don't search further */
        if ((idx == 2 && master->best_freq_split_idx_chroma == 0) ||
            (idx == 3 && master->best_freq_split_idx_chroma != 2) ||
            (idx == 4 && master->best_freq_split_idx_chroma != 4)) {
          master->scan_number = cinfo->num_scans - 1;
          master->pass_number = passes_per_scan * (master->scan_number + 1) - 1 + master->pass_number_scan_opt_base;
          master->pub.is_last_pass = (master->pass_number == master->total_passes - 1);
        }
      }
    }
  }
  
  if (master->scan_number == cinfo->num_scans - 1) {
    int i, Al;
    int min_Al = MIN(master->best_Al_luma, master->best_Al_chroma);
    
    copy_buffer(cinfo, 0);

    if (cinfo->num_scans > cinfo->master->num_scans_luma &&
        cinfo->master->dc_scan_opt_mode != 0) {
      base_scan_idx = cinfo->master->num_scans_luma;
      
      if (master->interleave_chroma_dc && cinfo->master->dc_scan_opt_mode != 1)
        copy_buffer(cinfo, base_scan_idx);
      else {
        copy_buffer(cinfo, base_scan_idx+1);
        copy_buffer(cinfo, base_scan_idx+2);
      }
    }
    
    if (master->best_freq_split_idx_luma == 0)
      copy_buffer(cinfo, luma_freq_split_scan_start);
    else {
      copy_buffer(cinfo, luma_freq_split_scan_start+2*(master->best_freq_split_idx_luma-1)+1);
      copy_buffer(cinfo, luma_freq_split_scan_start+2*(master->best_freq_split_idx_luma-1)+2);
    }
    
    /* copy the LSB refinements as well */
    for (Al = master->best_Al_luma-1; Al >= min_Al; Al--)
      copy_buffer(cinfo, 3 + 3*Al);

    if (cinfo->num_scans > cinfo->master->num_scans_luma) {
      if (master->best_freq_split_idx_chroma == 0) {
        copy_buffer(cinfo, chroma_freq_split_scan_start);
        copy_buffer(cinfo, chroma_freq_split_scan_start+1);
      }
      else {
        copy_buffer(cinfo, chroma_freq_split_scan_start+4*(master->best_freq_split_idx_chroma-1)+2);
        copy_buffer(cinfo, chroma_freq_split_scan_start+4*(master->best_freq_split_idx_chroma-1)+3);
        copy_buffer(cinfo, chroma_freq_split_scan_start+4*(master->best_freq_split_idx_chroma-1)+4);
        copy_buffer(cinfo, chroma_freq_split_scan_start+4*(master->best_freq_split_idx_chroma-1)+5);
      }
      
      base_scan_idx = cinfo->master->num_scans_luma +
                      cinfo->master->num_scans_chroma_dc;
      
      for (Al = master->best_Al_chroma-1; Al >= min_Al; Al--) {
        copy_buffer(cinfo, base_scan_idx + 6*Al + 4);
        copy_buffer(cinfo, base_scan_idx + 6*Al + 5);
      }
    }
    
    for (Al = min_Al-1; Al >= 0; Al--) {
      copy_buffer(cinfo, 3 + 3*Al);
      
      if (cinfo->num_scans > cinfo->master->num_scans_luma) {
        copy_buffer(cinfo, base_scan_idx + 6*Al + 4);
        copy_buffer(cinfo, base_scan_idx + 6*Al + 5);
      }
    }
    
    /* free the memory allocated for buffers */
    for (i = 0; i < cinfo->num_scans; i++)
      if (master->scan_buffer[i])
        free(master->scan_buffer[i]);
  }
}

/*
 * Finish up at end of pass.
 */

METHODDEF(void)
finish_pass_master (j_compress_ptr cinfo)
{
  my_master_ptr master = (my_master_ptr) cinfo->master;

  /* The entropy coder always needs an end-of-pass call,
   * either to analyze statistics or to flush its output buffer.
   */
  (*cinfo->entropy->finish_pass) (cinfo);

  /* Update state for next pass */
  switch (master->pass_type) {
  case main_pass:
    /* next pass is either output of scan 0 (after optimization)
     * or output of scan 1 (if no optimization).
     */
    if (cinfo->master->trellis_quant)
      master->pass_type = trellis_pass;
    else {
    master->pass_type = output_pass;
    if (! cinfo->optimize_coding)
      master->scan_number++;
    }
    break;
  case huff_opt_pass:
    /* next pass is always output of current scan */
    master->pass_type = (master->pass_number < master->pass_number_scan_opt_base-1) ? trellis_pass : output_pass;
    break;
  case output_pass:
    /* next pass is either optimization or output of next scan */
    if (cinfo->optimize_coding)
      master->pass_type = huff_opt_pass;
    if (cinfo->master->optimize_scans) {
      (*cinfo->dest->term_destination)(cinfo);
      cinfo->dest = master->saved_dest;
      select_scans(cinfo, master->scan_number + 1);
    }

    master->scan_number++;
    break;
  case trellis_pass:
    if (cinfo->optimize_coding)
      master->pass_type = huff_opt_pass;
    else
      master->pass_type = (master->pass_number < master->pass_number_scan_opt_base-1) ? trellis_pass : output_pass;
      
    if ((master->pass_number + 1) %
        (cinfo->num_components * (cinfo->master->use_scans_in_trellis ? 4 : 2)) == 0 &&
        cinfo->master->trellis_q_opt) {
      int i, j;

      for (i = 0; i < NUM_QUANT_TBLS; i++) {
        for (j = 1; j < DCTSIZE2; j++) {
          if (cinfo->master->norm_coef[i][j] != 0.0) {
            int q = (int)(cinfo->master->norm_src[i][j] /
                          cinfo->master->norm_coef[i][j] + 0.5);
            if (q > 254) q = 254;
            if (q < 1) q = 1;
            cinfo->quant_tbl_ptrs[i]->quantval[j] = q;
  }
  }
      }
    }
    break;
  }

  master->pass_number++;
}


/*
 * Initialize master compression control.
 */

GLOBAL(void)
jinit_c_master_control (j_compress_ptr cinfo, boolean transcode_only)
{
  my_master_ptr master = (my_master_ptr) cinfo->master;

  master->pub.prepare_for_pass = prepare_for_pass;
  master->pub.pass_startup = pass_startup;
  master->pub.finish_pass = finish_pass_master;
  master->pub.is_last_pass = FALSE;
  master->pub.call_pass_startup = FALSE;

  /* Validate parameters, determine derived values */
  initial_setup(cinfo, transcode_only);

  if (cinfo->scan_info != NULL) {
#ifdef C_MULTISCAN_FILES_SUPPORTED
    validate_script(cinfo);
#else
    ERREXIT(cinfo, JERR_NOT_COMPILED);
#endif
  } else {
    cinfo->progressive_mode = FALSE;
    cinfo->num_scans = 1;
  }

  if (cinfo->progressive_mode && !cinfo->arith_code)  /*  TEMPORARY HACK ??? */
    cinfo->optimize_coding = TRUE; /* assume default tables no good for progressive mode */

  /* Initialize my private state */
  if (transcode_only) {
    /* no main pass in transcoding */
    if (cinfo->optimize_coding)
      master->pass_type = huff_opt_pass;
    else
      master->pass_type = output_pass;
  } else {
    /* for normal compression, first pass is always this type: */
    master->pass_type = main_pass;
  }
  master->scan_number = 0;
  master->pass_number = 0;
  if (cinfo->optimize_coding)
    master->total_passes = cinfo->num_scans * 2;
  else
    master->total_passes = cinfo->num_scans;

  master->jpeg_version = PACKAGE_NAME " version " VERSION " (build " BUILD ")";
  
  master->pass_number_scan_opt_base = 0;
  if (cinfo->master->trellis_quant) {
    if (cinfo->optimize_coding)
      master->pass_number_scan_opt_base =
        ((cinfo->master->use_scans_in_trellis) ? 4 : 2) * cinfo->num_components *
        cinfo->master->trellis_num_loops;
    else
      master->pass_number_scan_opt_base =
        ((cinfo->master->use_scans_in_trellis) ? 2 : 1) * cinfo->num_components *
        cinfo->master->trellis_num_loops + 1;
    master->total_passes += master->pass_number_scan_opt_base;
}
  
  if (cinfo->master->optimize_scans) {
    int i;
    master->best_Al_chroma = 0;
    
    for (i = 0; i < cinfo->num_scans; i++)
      master->scan_buffer[i] = NULL;
  }
}
