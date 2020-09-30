/*
 * jcmaster.h
 *
 * This file was part of the Independent JPEG Group's software:
 * Copyright (C) 1991-1997, Thomas G. Lane.
 * mozjpeg Modifications:
 * Copyright (C) 2014, Mozilla Corporation.
 * For conditions of distribution and use, see the accompanying README file.
 *
 * This file contains the master control structures for the JPEG compressor.
 */


/* Private state */

typedef enum {
        main_pass,              /* input data, also do first output step */
        huff_opt_pass,          /* Huffman code optimization pass */
        output_pass,            /* data output pass */
        trellis_pass            /* trellis quantization pass */
} c_pass_type;

typedef struct {
  struct jpeg_comp_master pub;  /* public fields */

  c_pass_type pass_type;        /* the type of the current pass */

  int pass_number;              /* # of passes completed */
  int total_passes;             /* total # of passes needed */

  int scan_number;              /* current index in scan_info[] */

  /* fields for scan optimisation */
  int pass_number_scan_opt_base; /* pass number where scan optimization begins */
  unsigned char * scan_buffer[64]; /* buffer for a given scan */
  unsigned long scan_size[64]; /* size for a given scan */
  int actual_Al[64]; /* actual value of Al used for a scan */
  unsigned long best_cost; /* bit count for best frequency split */
  int best_freq_split_idx_luma; /* index for best frequency split (luma) */
  int best_freq_split_idx_chroma; /* index for best frequency split (chroma) */
  int best_Al_luma; /* best value for Al found in scan search (luma) */
  int best_Al_chroma; /* best value for Al found in scan search (luma) */
  boolean interleave_chroma_dc; /* indicate whether to interleave chroma DC scans */
  struct jpeg_destination_mgr * saved_dest; /* saved value of cinfo->dest */

  /*
   * This is here so we can add libjpeg-turbo version/build information to the
   * global string table without introducing a new global symbol.  Adding this
   * information to the global string table allows one to examine a binary
   * object and determine which version of libjpeg-turbo it was built from or
   * linked against.
   */
  const char *jpeg_version;
} my_comp_master;

typedef my_comp_master * my_master_ptr;
