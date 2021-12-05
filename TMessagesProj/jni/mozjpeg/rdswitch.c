/*
 * rdswitch.c
 *
 * This file was part of the Independent JPEG Group's software:
 * Copyright (C) 1991-1996, Thomas G. Lane.
 * libjpeg-turbo Modifications:
 * Copyright (C) 2010, 2018, D. R. Commander.
 * For conditions of distribution and use, see the accompanying README.ijg
 * file.
 *
 * This file contains routines to process some of cjpeg's more complicated
 * command-line switches.  Switches processed here are:
 *      -qtables file           Read quantization tables from text file
 *      -scans file             Read scan script from text file
 *      -quality N[,N,...]      Set quality ratings
 *      -qslots N[,N,...]       Set component quantization table selectors
 *      -sample HxV[,HxV,...]   Set component sampling factors
 */

#include "cdjpeg.h"             /* Common decls for cjpeg/djpeg applications */
#include <ctype.h>              /* to declare isdigit(), isspace() */


LOCAL(int)
text_getc (FILE *file)
/* Read next char, skipping over any comments (# to end of line) */
/* A comment/newline sequence is returned as a newline */
{
  register int ch;

  ch = getc(file);
  if (ch == '#') {
    do {
      ch = getc(file);
    } while (ch != '\n' && ch != EOF);
  }
  return ch;
}


LOCAL(boolean)
read_text_integer (FILE *file, long *result, int *termchar)
/* Read an unsigned decimal integer from a file, store it in result */
/* Reads one trailing character after the integer; returns it in termchar */
{
  register int ch;
  register long val;

  /* Skip any leading whitespace, detect EOF */
  do {
    ch = text_getc(file);
    if (ch == EOF) {
      *termchar = ch;
      return FALSE;
    }
  } while (isspace(ch));

  if (! isdigit(ch)) {
    *termchar = ch;
    return FALSE;
  }

  val = ch - '0';
  while ((ch = text_getc(file)) != EOF) {
    if (! isdigit(ch))
      break;
    val *= 10;
    val += ch - '0';
  }
  *result = val;
  *termchar = ch;
  return TRUE;
}


#if JPEG_LIB_VERSION < 70
static int q_scale_factor[NUM_QUANT_TBLS] = {100, 100, 100, 100};
#endif

GLOBAL(boolean)
read_quant_tables(j_compress_ptr cinfo, char *filename, boolean force_baseline)
/* Read a set of quantization tables from the specified file.
 * The file is plain ASCII text: decimal numbers with whitespace between.
 * Comments preceded by '#' may be included in the file.
 * There may be one to NUM_QUANT_TBLS tables in the file, each of 64 values.
 * The tables are implicitly numbered 0,1,etc.
 * NOTE: does not affect the qslots mapping, which will default to selecting
 * table 0 for luminance (or primary) components, 1 for chrominance components.
 * You must use -qslots if you want a different component->table mapping.
 */
{
  FILE *fp;
  int tblno, i, termchar;
  long val;
  unsigned int table[DCTSIZE2];

  if ((fp = fopen(filename, "r")) == NULL) {
    fprintf(stderr, "Can't open table file %s\n", filename);
    return FALSE;
  }
  tblno = 0;

  while (read_text_integer(fp, &val, &termchar)) { /* read 1st element of table */
    if (tblno >= NUM_QUANT_TBLS) {
      fprintf(stderr, "Too many tables in file %s\n", filename);
      fclose(fp);
      return FALSE;
    }
    table[0] = (unsigned int) val;
    for (i = 1; i < DCTSIZE2; i++) {
      if (! read_text_integer(fp, &val, &termchar)) {
        fprintf(stderr, "Invalid table data in file %s\n", filename);
        fclose(fp);
        return FALSE;
      }
      table[i] = (unsigned int) val;
    }
#if JPEG_LIB_VERSION >= 70
    jpeg_add_quant_table(cinfo, tblno, table, cinfo->q_scale_factor[tblno],
                         force_baseline);
#else
    jpeg_add_quant_table(cinfo, tblno, table, q_scale_factor[tblno],
                         force_baseline);
#endif
    tblno++;
  }

  if (termchar != EOF) {
    fprintf(stderr, "Non-numeric data in file %s\n", filename);
    fclose(fp);
    return FALSE;
  }

  fclose(fp);
  return TRUE;
}


#ifdef C_MULTISCAN_FILES_SUPPORTED

LOCAL(boolean)
read_scan_integer (FILE *file, long *result, int *termchar)
/* Variant of read_text_integer that always looks for a non-space termchar;
 * this simplifies parsing of punctuation in scan scripts.
 */
{
  register int ch;

  if (! read_text_integer(file, result, termchar))
    return FALSE;
  ch = *termchar;
  while (ch != EOF && isspace(ch))
    ch = text_getc(file);
  if (isdigit(ch)) {            /* oops, put it back */
    if (ungetc(ch, file) == EOF)
      return FALSE;
    ch = ' ';
  } else {
    /* Any separators other than ';' and ':' are ignored;
     * this allows user to insert commas, etc, if desired.
     */
    if (ch != EOF && ch != ';' && ch != ':')
      ch = ' ';
  }
  *termchar = ch;
  return TRUE;
}


GLOBAL(boolean)
read_scan_script (j_compress_ptr cinfo, char *filename)
/* Read a scan script from the specified text file.
 * Each entry in the file defines one scan to be emitted.
 * Entries are separated by semicolons ';'.
 * An entry contains one to four component indexes,
 * optionally followed by a colon ':' and four progressive-JPEG parameters.
 * The component indexes denote which component(s) are to be transmitted
 * in the current scan.  The first component has index 0.
 * Sequential JPEG is used if the progressive-JPEG parameters are omitted.
 * The file is free format text: any whitespace may appear between numbers
 * and the ':' and ';' punctuation marks.  Also, other punctuation (such
 * as commas or dashes) can be placed between numbers if desired.
 * Comments preceded by '#' may be included in the file.
 * Note: we do very little validity checking here;
 * jcmaster.c will validate the script parameters.
 */
{
  FILE *fp;
  int scanno, ncomps, termchar;
  long val;
  jpeg_scan_info *scanptr;
#define MAX_SCANS  100          /* quite arbitrary limit */
  jpeg_scan_info scans[MAX_SCANS];

  if ((fp = fopen(filename, "r")) == NULL) {
    fprintf(stderr, "Can't open scan definition file %s\n", filename);
    return FALSE;
  }
  scanptr = scans;
  scanno = 0;

  while (read_scan_integer(fp, &val, &termchar)) {
    if (scanno >= MAX_SCANS) {
      fprintf(stderr, "Too many scans defined in file %s\n", filename);
      fclose(fp);
      return FALSE;
    }
    scanptr->component_index[0] = (int) val;
    ncomps = 1;
    while (termchar == ' ') {
      if (ncomps >= MAX_COMPS_IN_SCAN) {
        fprintf(stderr, "Too many components in one scan in file %s\n",
                filename);
        fclose(fp);
        return FALSE;
      }
      if (! read_scan_integer(fp, &val, &termchar))
        goto bogus;
      scanptr->component_index[ncomps] = (int) val;
      ncomps++;
    }
    scanptr->comps_in_scan = ncomps;
    if (termchar == ':') {
      if (! read_scan_integer(fp, &val, &termchar) || termchar != ' ')
        goto bogus;
      scanptr->Ss = (int) val;
      if (! read_scan_integer(fp, &val, &termchar) || termchar != ' ')
        goto bogus;
      scanptr->Se = (int) val;
      if (! read_scan_integer(fp, &val, &termchar) || termchar != ' ')
        goto bogus;
      scanptr->Ah = (int) val;
      if (! read_scan_integer(fp, &val, &termchar))
        goto bogus;
      scanptr->Al = (int) val;
    } else {
      /* set non-progressive parameters */
      scanptr->Ss = 0;
      scanptr->Se = DCTSIZE2-1;
      scanptr->Ah = 0;
      scanptr->Al = 0;
    }
    if (termchar != ';' && termchar != EOF) {
bogus:
      fprintf(stderr, "Invalid scan entry format in file %s\n", filename);
      fclose(fp);
      return FALSE;
    }
    scanptr++, scanno++;
  }

  if (termchar != EOF) {
    fprintf(stderr, "Non-numeric data in file %s\n", filename);
    fclose(fp);
    return FALSE;
  }

  if (scanno > 0) {
    /* Stash completed scan list in cinfo structure.
     * NOTE: for cjpeg's use, JPOOL_IMAGE is the right lifetime for this data,
     * but if you want to compress multiple images you'd want JPOOL_PERMANENT.
     */
    scanptr = (jpeg_scan_info *)
      (*cinfo->mem->alloc_small) ((j_common_ptr) cinfo, JPOOL_IMAGE,
                                  scanno * sizeof(jpeg_scan_info));
    MEMCOPY(scanptr, scans, scanno * sizeof(jpeg_scan_info));
    cinfo->scan_info = scanptr;
    cinfo->num_scans = scanno;
    
    /* Disable scan optimization if using custom scan */
    jpeg_c_set_bool_param(cinfo, JBOOLEAN_OPTIMIZE_SCANS, FALSE);
  }

  fclose(fp);
  return TRUE;
}

#endif /* C_MULTISCAN_FILES_SUPPORTED */


#if JPEG_LIB_VERSION < 70
/* These are the sample quantization tables given in Annex K (Clause K.1) of
 * Recommendation ITU-T T.81 (1992) | ISO/IEC 10918-1:1994.
 * The spec says that the values given produce "good" quality, and
 * when divided by 2, "very good" quality.
 */
static const unsigned int std_luminance_quant_tbl[9][DCTSIZE2] = {
  {
    /* JPEG Annex K
     */
  16,  11,  10,  16,  24,  40,  51,  61,
  12,  12,  14,  19,  26,  58,  60,  55,
  14,  13,  16,  24,  40,  57,  69,  56,
  14,  17,  22,  29,  51,  87,  80,  62,
  18,  22,  37,  56,  68, 109, 103,  77,
  24,  35,  55,  64,  81, 104, 113,  92,
  49,  64,  78,  87, 103, 121, 120, 101,
  72,  92,  95,  98, 112, 100, 103,  99
  },
  {
    /* flat
     */
    16,  16,  16,  16,  16,  16,  16,  16,
    16,  16,  16,  16,  16,  16,  16,  16,
    16,  16,  16,  16,  16,  16,  16,  16,
    16,  16,  16,  16,  16,  16,  16,  16,
    16,  16,  16,  16,  16,  16,  16,  16,
    16,  16,  16,  16,  16,  16,  16,  16,
    16,  16,  16,  16,  16,  16,  16,  16,
    16,  16,  16,  16,  16,  16,  16,  16
  },
  {
    12, 17, 20, 21, 30, 34, 56, 63,
    18, 20, 20, 26, 28, 51, 61, 55,
    19, 20, 21, 26, 33, 58, 69, 55,
    26, 26, 26, 30, 46, 87, 86, 66,
    31, 33, 36, 40, 46, 96, 100, 73,
    40, 35, 46, 62, 81, 100, 111, 91,
    46, 66, 76, 86, 102, 121, 120, 101,
    68, 90, 90, 96, 113, 102, 105, 103
  },
  {
    /* From http://www.imagemagick.org/discourse-server/viewtopic.php?f=22&t=20333&p=98008#p98008
     */
    16,  16,  16,  18,  25,  37,  56,  85,
    16,  17,  20,  27,  34,  40,  53,  75,
    16,  20,  24,  31,  43,  62,  91,  135,
    18,  27,  31,  40,  53,  74,  106, 156,
    25,  34,  43,  53,  69,  94,  131, 189,
    37,  40,  62,  74,  94,  124, 169, 238,
    56,  53,  91,  106, 131, 169, 226, 311,
    85,  75,  135, 156, 189, 238, 311, 418
  },
  {
    9, 10, 12, 14, 27, 32, 51, 62,
    11, 12, 14, 19, 27, 44, 59, 73,
    12, 14, 18, 25, 42, 59, 79, 78,
    17, 18, 25, 42, 61, 92, 87, 92,
    23, 28, 42, 75, 79, 112, 112, 99,
    40, 42, 59, 84, 88, 124, 132, 111,
    42, 64, 78, 95, 105, 126, 125, 99,
    70, 75, 100, 102, 116, 100, 107, 98
  },
  {
    /* Relevance of human vision to JPEG-DCT compression (1992) Klein, Silverstein and Carney.
     */
    10, 12, 14, 19, 26, 38, 57, 86,
    12, 18, 21, 28, 35, 41, 54, 76,
    14, 21, 25, 32, 44, 63, 92, 136,
    19, 28, 32, 41, 54, 75, 107, 157,
    26, 35, 44, 54, 70, 95, 132, 190,
    38, 41, 63, 75, 95, 125, 170, 239,
    57, 54, 92, 107, 132, 170, 227, 312,
    86, 76, 136, 157, 190, 239, 312, 419
  },
  {
    /* DCTune perceptual optimization of compressed dental X-Rays (1997) Watson, Taylor, Borthwick
     */
    7, 8, 10, 14, 23, 44, 95, 241,
    8, 8, 11, 15, 25, 47, 102, 255,
    10, 11, 13, 19, 31, 58, 127, 255,
    14, 15, 19, 27, 44, 83, 181, 255,
    23, 25, 31, 44, 72, 136, 255, 255,
    44, 47, 58, 83, 136, 255, 255, 255,
    95, 102, 127, 181, 255, 255, 255, 255,
    241, 255, 255, 255, 255, 255, 255, 255
  },
  {
    /* A visual detection model for DCT coefficient quantization (12/9/93) Ahumada, Watson, Peterson
     */
    15, 11, 11, 12, 15, 19, 25, 32,
    11, 13, 10, 10, 12, 15, 19, 24,
    11, 10, 14, 14, 16, 18, 22, 27,
    12, 10, 14, 18, 21, 24, 28, 33,
    15, 12, 16, 21, 26, 31, 36, 42,
    19, 15, 18, 24, 31, 38, 45, 53,
    25, 19, 22, 28, 36, 45, 55, 65,
    32, 24, 27, 33, 42, 53, 65, 77
  },
  {
    /* An improved detection model for DCT coefficient quantization (1993) Peterson, Ahumada and Watson
     */
    14, 10, 11, 14, 19, 25, 34, 45,
    10, 11, 11, 12, 15, 20, 26, 33,
    11, 11, 15, 18, 21, 25, 31, 38,
    14, 12, 18, 24, 28, 33, 39, 47,
    19, 15, 21, 28, 36, 43, 51, 59,
    25, 20, 25, 33, 43, 54, 64, 74,
    34, 26, 31, 39, 51, 64, 77, 91,
    45, 33, 38, 47, 59, 74, 91, 108
  }
};

static const unsigned int std_chrominance_quant_tbl[9][DCTSIZE2] = {
  {
    /* JPEG Annex K
     */
  17,  18,  24,  47,  99,  99,  99,  99,
  18,  21,  26,  66,  99,  99,  99,  99,
  24,  26,  56,  99,  99,  99,  99,  99,
  47,  66,  99,  99,  99,  99,  99,  99,
  99,  99,  99,  99,  99,  99,  99,  99,
  99,  99,  99,  99,  99,  99,  99,  99,
  99,  99,  99,  99,  99,  99,  99,  99,
  99,  99,  99,  99,  99,  99,  99,  99
  },
  {
    /* flat
     */
    16,  16,  16,  16,  16,  16,  16,  16,
    16,  16,  16,  16,  16,  16,  16,  16,
    16,  16,  16,  16,  16,  16,  16,  16,
    16,  16,  16,  16,  16,  16,  16,  16,
    16,  16,  16,  16,  16,  16,  16,  16,
    16,  16,  16,  16,  16,  16,  16,  16,
    16,  16,  16,  16,  16,  16,  16,  16,
    16,  16,  16,  16,  16,  16,  16,  16
  },
  {
    8, 12, 15, 15, 86, 96, 96, 98,
    13, 13, 15, 26, 90, 96, 99, 98,
    12, 15, 18, 96, 99, 99, 99, 99,
    17, 16, 90, 96, 99, 99, 99, 99,
    96, 96, 99, 99, 99, 99, 99, 99,
    99, 99, 99, 99, 99, 99, 99, 99,
    99, 99, 99, 99, 99, 99, 99, 99,
    99, 99, 99, 99, 99, 99, 99, 99
  },
  {
    /* From http://www.imagemagick.org/discourse-server/viewtopic.php?f=22&t=20333&p=98008#p98008
     */
    16,  16,  16,  18,  25,  37,  56,  85,
    16,  17,  20,  27,  34,  40,  53,  75,
    16,  20,  24,  31,  43,  62,  91,  135,
    18,  27,  31,  40,  53,  74,  106, 156,
    25,  34,  43,  53,  69,  94,  131, 189,
    37,  40,  62,  74,  94,  124, 169, 238,
    56,  53,  91,  106, 131, 169, 226, 311,
    85,  75,  135, 156, 189, 238, 311, 418
  },
  {
    9, 10, 17, 19, 62, 89, 91, 97,
    12, 13, 18, 29, 84, 91, 88, 98,
    14, 19, 29, 93, 95, 95, 98, 97,
    20, 26, 84, 88, 95, 95, 98, 94,
    26, 86, 91, 93, 97, 99, 98, 99,
    99, 100, 98, 99, 99, 99, 99, 99,
    99, 99, 99, 99, 99, 99, 99, 99,
    97, 97, 99, 99, 99, 99, 97, 99
  },
  {
    /* Relevance of human vision to JPEG-DCT compression (1992) Klein, Silverstein and Carney.
     * Copied from luma
     */
    10, 12, 14, 19, 26, 38, 57, 86,
    12, 18, 21, 28, 35, 41, 54, 76,
    14, 21, 25, 32, 44, 63, 92, 136,
    19, 28, 32, 41, 54, 75, 107, 157,
    26, 35, 44, 54, 70, 95, 132, 190,
    38, 41, 63, 75, 95, 125, 170, 239,
    57, 54, 92, 107, 132, 170, 227, 312,
    86, 76, 136, 157, 190, 239, 312, 419
  },
  {
    /* DCTune perceptual optimization of compressed dental X-Rays (1997) Watson, Taylor, Borthwick
     * Copied from luma
     */
    7, 8, 10, 14, 23, 44, 95, 241,
    8, 8, 11, 15, 25, 47, 102, 255,
    10, 11, 13, 19, 31, 58, 127, 255,
    14, 15, 19, 27, 44, 83, 181, 255,
    23, 25, 31, 44, 72, 136, 255, 255,
    44, 47, 58, 83, 136, 255, 255, 255,
    95, 102, 127, 181, 255, 255, 255, 255,
    241, 255, 255, 255, 255, 255, 255, 255
  },
  {
    /* A visual detection model for DCT coefficient quantization (12/9/93) Ahumada, Watson, Peterson
     * Copied from luma
     */
    15, 11, 11, 12, 15, 19, 25, 32,
    11, 13, 10, 10, 12, 15, 19, 24,
    11, 10, 14, 14, 16, 18, 22, 27,
    12, 10, 14, 18, 21, 24, 28, 33,
    15, 12, 16, 21, 26, 31, 36, 42,
    19, 15, 18, 24, 31, 38, 45, 53,
    25, 19, 22, 28, 36, 45, 55, 65,
    32, 24, 27, 33, 42, 53, 65, 77
  },
  {
    /* An improved detection model for DCT coefficient quantization (1993) Peterson, Ahumada and Watson
     * Copied from luma
     */
    14, 10, 11, 14, 19, 25, 34, 45,
    10, 11, 11, 12, 15, 20, 26, 33,
    11, 11, 15, 18, 21, 25, 31, 38,
    14, 12, 18, 24, 28, 33, 39, 47,
    19, 15, 21, 28, 36, 43, 51, 59,
    25, 20, 25, 33, 43, 54, 64, 74,
    34, 26, 31, 39, 51, 64, 77, 91,
    45, 33, 38, 47, 59, 74, 91, 108
  }
};

LOCAL(void)
jpeg_default_qtables (j_compress_ptr cinfo, boolean force_baseline)
{
  int quant_tbl_master_idx = 0;
  if (jpeg_c_int_param_supported(cinfo, JINT_BASE_QUANT_TBL_IDX))
    quant_tbl_master_idx = jpeg_c_get_int_param(cinfo, JINT_BASE_QUANT_TBL_IDX);
  
  jpeg_add_quant_table(cinfo, 0, std_luminance_quant_tbl[quant_tbl_master_idx],
                       q_scale_factor[0], force_baseline);
  jpeg_add_quant_table(cinfo, 1, std_chrominance_quant_tbl[quant_tbl_master_idx],
                       q_scale_factor[1], force_baseline);
}
#endif


GLOBAL(boolean)
set_quality_ratings (j_compress_ptr cinfo, char *arg, boolean force_baseline)
/* Process a quality-ratings parameter string, of the form
 *     N[,N,...]
 * If there are more q-table slots than parameters, the last value is replicated.
 */
{
  float val = 75.f;                 /* default value */
  int tblno;
  char ch;

  for (tblno = 0; tblno < NUM_QUANT_TBLS; tblno++) {
    if (*arg) {
      ch = ',';                 /* if not set by sscanf, will be ',' */
      if (sscanf(arg, "%f%c", &val, &ch) < 1)
        return FALSE;
      if (ch != ',')            /* syntax check */
        return FALSE;
      /* Convert user 0-100 rating to percentage scaling */
#if JPEG_LIB_VERSION >= 70
      cinfo->q_scale_factor[tblno] = jpeg_float_quality_scaling(val);
#else
      q_scale_factor[tblno] = jpeg_float_quality_scaling(val);
#endif
      while (*arg && *arg++ != ','); /* advance to next segment of arg
                                        string */
    } else {
      /* reached end of parameter, set remaining factors to last value */
#if JPEG_LIB_VERSION >= 70
      cinfo->q_scale_factor[tblno] = jpeg_float_quality_scaling(val);
#else
      q_scale_factor[tblno] = jpeg_float_quality_scaling(val);
#endif
    }
  }
  jpeg_default_qtables(cinfo, force_baseline);

  /* For some images chroma subsampling significantly degrades color quality,
     making it impossible to achieve high visual quality regardless of quality setting.
     To make the quality setting more intuitive, disable subsampling when high-quality
     color is desired. */
  if (val >= 90) {
    set_sample_factors(cinfo, "1x1");
  } else if (val >= 80) {
    set_sample_factors(cinfo, "2x1");
  }

  return TRUE;
}


GLOBAL(boolean)
set_quant_slots (j_compress_ptr cinfo, char *arg)
/* Process a quantization-table-selectors parameter string, of the form
 *     N[,N,...]
 * If there are more components than parameters, the last value is replicated.
 */
{
  int val = 0;                  /* default table # */
  int ci;
  char ch;

  for (ci = 0; ci < MAX_COMPONENTS; ci++) {
    if (*arg) {
      ch = ',';                 /* if not set by sscanf, will be ',' */
      if (sscanf(arg, "%d%c", &val, &ch) < 1)
        return FALSE;
      if (ch != ',')            /* syntax check */
        return FALSE;
      if (val < 0 || val >= NUM_QUANT_TBLS) {
        fprintf(stderr, "JPEG quantization tables are numbered 0..%d\n",
                NUM_QUANT_TBLS-1);
        return FALSE;
      }
      cinfo->comp_info[ci].quant_tbl_no = val;
      while (*arg && *arg++ != ','); /* advance to next segment of arg
                                        string */
    } else {
      /* reached end of parameter, set remaining components to last table */
      cinfo->comp_info[ci].quant_tbl_no = val;
    }
  }
  return TRUE;
}


GLOBAL(boolean)
set_sample_factors (j_compress_ptr cinfo, char *arg)
/* Process a sample-factors parameter string, of the form
 *     HxV[,HxV,...]
 * If there are more components than parameters, "1x1" is assumed for the rest.
 */
{
  int ci, val1, val2;
  char ch1, ch2;

  for (ci = 0; ci < MAX_COMPONENTS; ci++) {
    if (*arg) {
      ch2 = ',';                /* if not set by sscanf, will be ',' */
      if (sscanf(arg, "%d%c%d%c", &val1, &ch1, &val2, &ch2) < 3)
        return FALSE;
      if ((ch1 != 'x' && ch1 != 'X') || ch2 != ',') /* syntax check */
        return FALSE;
      if (val1 <= 0 || val1 > 4 || val2 <= 0 || val2 > 4) {
        fprintf(stderr, "JPEG sampling factors must be 1..4\n");
        return FALSE;
      }
      cinfo->comp_info[ci].h_samp_factor = val1;
      cinfo->comp_info[ci].v_samp_factor = val2;
      while (*arg && *arg++ != ',');  /* advance to next segment of arg
                                         string */
    } else {
      /* reached end of parameter, set remaining components to 1x1 sampling */
      cinfo->comp_info[ci].h_samp_factor = 1;
      cinfo->comp_info[ci].v_samp_factor = 1;
    }
  }
  return TRUE;
}
