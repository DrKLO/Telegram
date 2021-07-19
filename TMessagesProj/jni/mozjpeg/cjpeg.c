/*
 * cjpeg.c
 *
 * This file was part of the Independent JPEG Group's software:
 * Copyright (C) 1991-1998, Thomas G. Lane.
 * Modified 2003-2011 by Guido Vollbeding.
 * libjpeg-turbo Modifications:
 * Copyright (C) 2010, 2013-2014, 2017, D. R. Commander.
 * mozjpeg Modifications:
 * Copyright (C) 2014, Mozilla Corporation.
 * For conditions of distribution and use, see the accompanying README file.
 *
 * This file contains a command-line user interface for the JPEG compressor.
 * It should work on any system with Unix- or MS-DOS-style command lines.
 *
 * Two different command line styles are permitted, depending on the
 * compile-time switch TWO_FILE_COMMANDLINE:
 *      cjpeg [options]  inputfile outputfile
 *      cjpeg [options]  [inputfile]
 * In the second style, output is always to standard output, which you'd
 * normally redirect to a file or pipe to some other program.  Input is
 * either from a named file or from standard input (typically redirected).
 * The second style is convenient on Unix but is unhelpful on systems that
 * don't support pipes.  Also, you MUST use the first style if your system
 * doesn't do binary I/O to stdin/stdout.
 * To simplify script writing, the "-outfile" switch is provided.  The syntax
 *      cjpeg [options]  -outfile outputfile  inputfile
 * works regardless of which command line style is used.
 */

#include "cdjpeg.h"             /* Common decls for cjpeg/djpeg applications */
#include "jversion.h"           /* for version message */
#include "jconfigint.h"

#ifndef HAVE_STDLIB_H           /* <stdlib.h> should declare malloc(),free() */
extern void *malloc(size_t size);
extern void free(void *ptr);
#endif

#ifdef USE_CCOMMAND             /* command-line reader for Macintosh */
#ifdef __MWERKS__
#include <SIOUX.h>              /* Metrowerks needs this */
#include <console.h>            /* ... and this */
#endif
#ifdef THINK_C
#include <console.h>            /* Think declares it here */
#endif
#endif


/* Create the add-on message string table. */

#define JMESSAGE(code, string)  string,

static const char * const cdjpeg_message_table[] = {
#include "cderror.h"
  NULL
};


/*
 * This routine determines what format the input file is,
 * and selects the appropriate input-reading module.
 *
 * To determine which family of input formats the file belongs to,
 * we may look only at the first byte of the file, since C does not
 * guarantee that more than one character can be pushed back with ungetc.
 * Looking at additional bytes would require one of these approaches:
 *     1) assume we can fseek() the input file (fails for piped input);
 *     2) assume we can push back more than one character (works in
 *        some C implementations, but unportable);
 *     3) provide our own buffering (breaks input readers that want to use
 *        stdio directly, such as the RLE library);
 * or  4) don't put back the data, and modify the input_init methods to assume
 *        they start reading after the start of file (also breaks RLE library).
 * #1 is attractive for MS-DOS but is untenable on Unix.
 *
 * The most portable solution for file types that can't be identified by their
 * first byte is to make the user tell us what they are.  This is also the
 * only approach for "raw" file types that contain only arbitrary values.
 * We presently apply this method for Targa files.  Most of the time Targa
 * files start with 0x00, so we recognize that case.  Potentially, however,
 * a Targa file could start with any byte value (byte 0 is the length of the
 * seldom-used ID field), so we provide a switch to force Targa input mode.
 */

static boolean is_targa;        /* records user -targa switch */
static boolean is_jpeg;
static boolean copy_markers;

LOCAL(cjpeg_source_ptr)
select_file_type(j_compress_ptr cinfo, FILE *infile)
{
  int c;

  if (is_targa) {
#ifdef TARGA_SUPPORTED
    return jinit_read_targa(cinfo);
#else
    ERREXIT(cinfo, JERR_TGA_NOTCOMP);
#endif
  }

  if ((c = getc(infile)) == EOF)
    ERREXIT(cinfo, JERR_INPUT_EMPTY);
  if (ungetc(c, infile) == EOF)
    ERREXIT(cinfo, JERR_UNGETC_FAILED);

  switch (c) {
#ifdef BMP_SUPPORTED
  case 'B':
    return jinit_read_bmp(cinfo, TRUE);
#endif
#ifdef GIF_SUPPORTED
  case 'G':
    return jinit_read_gif(cinfo);
#endif
#ifdef PPM_SUPPORTED
  case 'P':
    return jinit_read_ppm(cinfo);
#endif
#ifdef PNG_SUPPORTED
  case 0x89:
    copy_markers = TRUE;
    return jinit_read_png(cinfo);
#endif
#ifdef RLE_SUPPORTED
  case 'R':
    return jinit_read_rle(cinfo);
#endif
#ifdef TARGA_SUPPORTED
  case 0x00:
    return jinit_read_targa(cinfo);
#endif
  case 0xff:
    is_jpeg = TRUE;
    copy_markers = TRUE;
    return jinit_read_jpeg(cinfo);
  default:
    ERREXIT(cinfo, JERR_UNKNOWN_FORMAT);
    break;
  }

  return NULL;                  /* suppress compiler warnings */
}


/*
 * Argument-parsing code.
 * The switch parser is designed to be useful with DOS-style command line
 * syntax, ie, intermixed switches and file names, where only the switches
 * to the left of a given file name affect processing of that file.
 * The main program in this file doesn't actually use this capability...
 */


static const char *progname;    /* program name for error messages */
static char *icc_filename;      /* for -icc switch */
static char *outfilename;       /* for -outfile switch */
boolean memdst;                 /* for -memdst switch */


LOCAL(void)
usage(void)
/* complain about bad command line */
{
  fprintf(stderr, "usage: %s [switches] ", progname);
#ifdef TWO_FILE_COMMANDLINE
  fprintf(stderr, "inputfile outputfile\n");
#else
  fprintf(stderr, "[inputfile]\n");
#endif

  fprintf(stderr, "Switches (names may be abbreviated):\n");
  fprintf(stderr, "  -quality N[,...]   Compression quality (0..100; 5-95 is most useful range,\n");
  fprintf(stderr, "                     default is 75)\n");
  fprintf(stderr, "  -grayscale     Create monochrome JPEG file\n");
  fprintf(stderr, "  -rgb           Create RGB JPEG file\n");
#ifdef ENTROPY_OPT_SUPPORTED
  fprintf(stderr, "  -optimize      Optimize Huffman table (smaller file, but slow compression, enabled by default)\n");
#endif
#ifdef C_PROGRESSIVE_SUPPORTED
  fprintf(stderr, "  -progressive   Create progressive JPEG file (enabled by default)\n");
#endif
  fprintf(stderr, "  -baseline      Create baseline JPEG file (disable progressive coding)\n");
#ifdef TARGA_SUPPORTED
  fprintf(stderr, "  -targa         Input file is Targa format (usually not needed)\n");
#endif
  fprintf(stderr, "  -revert        Revert to standard defaults (instead of mozjpeg defaults)\n");
  fprintf(stderr, "  -fastcrush     Disable progressive scan optimization\n");
  fprintf(stderr, "  -dc-scan-opt   DC scan optimization mode\n");
  fprintf(stderr, "                 - 0 One scan for all components\n");
  fprintf(stderr, "                 - 1 One scan per component (default)\n");
  fprintf(stderr, "                 - 2 Optimize between one scan for all components and one scan for 1st component\n");
  fprintf(stderr, "                     plus one scan for remaining components\n");
  fprintf(stderr, "  -notrellis     Disable trellis optimization\n");
  fprintf(stderr, "  -trellis-dc    Enable trellis optimization of DC coefficients (default)\n");
  fprintf(stderr, "  -notrellis-dc  Disable trellis optimization of DC coefficients\n");
  fprintf(stderr, "  -tune-psnr     Tune trellis optimization for PSNR\n");
  fprintf(stderr, "  -tune-hvs-psnr Tune trellis optimization for PSNR-HVS (default)\n");
  fprintf(stderr, "  -tune-ssim     Tune trellis optimization for SSIM\n");
  fprintf(stderr, "  -tune-ms-ssim  Tune trellis optimization for MS-SSIM\n");
  fprintf(stderr, "Switches for advanced users:\n");
  fprintf(stderr, "  -noovershoot   Disable black-on-white deringing via overshoot\n");
  fprintf(stderr, "  -nojfif        Do not write JFIF (reduces size by 18 bytes but breaks standards; no known problems in Web browsers)\n");
#ifdef C_ARITH_CODING_SUPPORTED
  fprintf(stderr, "  -arithmetic    Use arithmetic coding\n");
#endif
#ifdef DCT_ISLOW_SUPPORTED
  fprintf(stderr, "  -dct int       Use integer DCT method%s\n",
          (JDCT_DEFAULT == JDCT_ISLOW ? " (default)" : ""));
#endif
#ifdef DCT_IFAST_SUPPORTED
  fprintf(stderr, "  -dct fast      Use fast integer DCT (less accurate)%s\n",
          (JDCT_DEFAULT == JDCT_IFAST ? " (default)" : ""));
#endif
#ifdef DCT_FLOAT_SUPPORTED
  fprintf(stderr, "  -dct float     Use floating-point DCT method%s\n",
          (JDCT_DEFAULT == JDCT_FLOAT ? " (default)" : ""));
#endif
  fprintf(stderr, "  -quant-baseline Use 8-bit quantization table entries for baseline JPEG compatibility\n");
  fprintf(stderr, "  -quant-table N Use predefined quantization table N:\n");
  fprintf(stderr, "                 - 0 JPEG Annex K\n");
  fprintf(stderr, "                 - 1 Flat\n");
  fprintf(stderr, "                 - 2 Custom, tuned for MS-SSIM\n");
  fprintf(stderr, "                 - 3 ImageMagick table by N. Robidoux\n");
  fprintf(stderr, "                 - 4 Custom, tuned for PSNR-HVS\n");
  fprintf(stderr, "                 - 5 Table from paper by Klein, Silverstein and Carney\n");
  fprintf(stderr, "  -icc FILE      Embed ICC profile contained in FILE\n");
  fprintf(stderr, "  -restart N     Set restart interval in rows, or in blocks with B\n");
#ifdef INPUT_SMOOTHING_SUPPORTED
  fprintf(stderr, "  -smooth N      Smooth dithered input (N=1..100 is strength)\n");
#endif
  fprintf(stderr, "  -maxmemory N   Maximum memory to use (in kbytes)\n");
  fprintf(stderr, "  -outfile name  Specify name for output file\n");
#if JPEG_LIB_VERSION >= 80 || defined(MEM_SRCDST_SUPPORTED)
  fprintf(stderr, "  -memdst        Compress to memory instead of file (useful for benchmarking)\n");
#endif
  fprintf(stderr, "  -verbose  or  -debug   Emit debug output\n");
  fprintf(stderr, "  -version       Print version information and exit\n");
  fprintf(stderr, "Switches for wizards:\n");
  fprintf(stderr, "  -qtables FILE  Use quantization tables given in FILE\n");
  fprintf(stderr, "  -qslots N[,...]    Set component quantization tables\n");
  fprintf(stderr, "  -sample HxV[,...]  Set component sampling factors\n");
#ifdef C_MULTISCAN_FILES_SUPPORTED
  fprintf(stderr, "  -scans FILE    Create multi-scan JPEG per script FILE\n");
#endif
  exit(EXIT_FAILURE);
}


LOCAL(int)
parse_switches(j_compress_ptr cinfo, int argc, char **argv,
                int last_file_arg_seen, boolean for_real)
/* Parse optional switches.
 * Returns argv[] index of first file-name argument (== argc if none).
 * Any file names with indexes <= last_file_arg_seen are ignored;
 * they have presumably been processed in a previous iteration.
 * (Pass 0 for last_file_arg_seen on the first or only iteration.)
 * for_real is FALSE on the first (dummy) pass; we may skip any expensive
 * processing.
 */
{
  int argn;
  char *arg;
  boolean force_baseline;
  boolean simple_progressive;
  char *qualityarg = NULL;      /* saves -quality parm if any */
  char *qtablefile = NULL;      /* saves -qtables filename if any */
  char *qslotsarg = NULL;       /* saves -qslots parm if any */
  char *samplearg = NULL;       /* saves -sample parm if any */
  char *scansarg = NULL;        /* saves -scans parm if any */

  /* Set up default JPEG parameters. */

  force_baseline = FALSE;       /* by default, allow 16-bit quantizers */
#ifdef C_PROGRESSIVE_SUPPORTED
  simple_progressive = cinfo->num_scans == 0 ? FALSE : TRUE;
#else
  simple_progressive = FALSE;
#endif
  is_targa = FALSE;
  icc_filename = NULL;
  outfilename = NULL;
  memdst = FALSE;
  cinfo->err->trace_level = 0;

  /* Scan command line options, adjust parameters */

  for (argn = 1; argn < argc; argn++) {
    arg = argv[argn];
    if (*arg != '-') {
      /* Not a switch, must be a file name argument */
      if (argn <= last_file_arg_seen) {
        outfilename = NULL;     /* -outfile applies to just one input file */
        continue;               /* ignore this name if previously processed */
      }
      break;                    /* else done parsing switches */
    }
    arg++;                      /* advance past switch marker character */

    if (keymatch(arg, "arithmetic", 1)) {
      /* Use arithmetic coding. */
#ifdef C_ARITH_CODING_SUPPORTED
      cinfo->arith_code = TRUE;
      
      /* No table optimization required for AC */
      cinfo->optimize_coding = FALSE;
#else
      fprintf(stderr, "%s: sorry, arithmetic coding not supported\n",
              progname);
      exit(EXIT_FAILURE);
#endif

    } else if (keymatch(arg, "baseline", 1)) {
      /* Force baseline-compatible output (8-bit quantizer values). */
      force_baseline = TRUE;
      /* Disable multiple scans */
      simple_progressive = FALSE;
      cinfo->num_scans = 0;
      cinfo->scan_info = NULL;

    } else if (keymatch(arg, "dct", 2)) {
      /* Select DCT algorithm. */
      if (++argn >= argc) {      /* advance to next argument */
        fprintf(stderr, "%s: missing argument for dct\n", progname);
        usage();
      }
      if (keymatch(argv[argn], "int", 1)) {
        cinfo->dct_method = JDCT_ISLOW;
      } else if (keymatch(argv[argn], "fast", 2)) {
        cinfo->dct_method = JDCT_IFAST;
      } else if (keymatch(argv[argn], "float", 2)) {
        cinfo->dct_method = JDCT_FLOAT;
      } else {
        fprintf(stderr, "%s: invalid argument for dct\n", progname);
        usage();
      }

    } else if (keymatch(arg, "debug", 1) || keymatch(arg, "verbose", 1)) {
      /* Enable debug printouts. */
      /* On first -d, print version identification */
      static boolean printed_version = FALSE;

      if (!printed_version) {
        fprintf(stderr, "%s version %s (build %s)\n",
                PACKAGE_NAME, VERSION, BUILD);
        fprintf(stderr, "%s\n\n", JCOPYRIGHT);
        fprintf(stderr, "Emulating The Independent JPEG Group's software, version %s\n\n",
                JVERSION);
        printed_version = TRUE;
      }
      cinfo->err->trace_level++;

    } else if (keymatch(arg, "version", 4)) {
      fprintf(stderr, "%s version %s (build %s)\n",
              PACKAGE_NAME, VERSION, BUILD);
      exit(EXIT_SUCCESS);

    } else if (keymatch(arg, "fastcrush", 4)) {
      jpeg_c_set_bool_param(cinfo, JBOOLEAN_OPTIMIZE_SCANS, FALSE);

    } else if (keymatch(arg, "grayscale", 2) || keymatch(arg, "greyscale",2)) {
      /* Force a monochrome JPEG file to be generated. */
      jpeg_set_colorspace(cinfo, JCS_GRAYSCALE);

    } else if (keymatch(arg, "rgb", 3)) {
      /* Force an RGB JPEG file to be generated. */
      jpeg_set_colorspace(cinfo, JCS_RGB);

    } else if (keymatch(arg, "lambda1", 7)) {
      if (++argn >= argc)       /* advance to next argument */
        usage();
      jpeg_c_set_float_param(cinfo, JFLOAT_LAMBDA_LOG_SCALE1,
                             atof(argv[argn]));

    } else if (keymatch(arg, "lambda2", 7)) {
      if (++argn >= argc)       /* advance to next argument */
        usage();
      jpeg_c_set_float_param(cinfo, JFLOAT_LAMBDA_LOG_SCALE2,
                             atof(argv[argn]));

    } else if (keymatch(arg, "icc", 1)) {
      /* Set ICC filename. */
      if (++argn >= argc)       /* advance to next argument */
        usage();
      icc_filename = argv[argn];

    } else if (keymatch(arg, "maxmemory", 3)) {
      /* Maximum memory in Kb (or Mb with 'm'). */
      long lval;
      char ch = 'x';

      if (++argn >= argc)       /* advance to next argument */
        usage();
      if (sscanf(argv[argn], "%ld%c", &lval, &ch) < 1)
        usage();
      if (ch == 'm' || ch == 'M')
        lval *= 1000L;
      cinfo->mem->max_memory_to_use = lval * 1000L;

    } else if (keymatch(arg, "dc-scan-opt", 3)) {
      if (++argn >= argc) {      /* advance to next argument */
        fprintf(stderr, "%s: missing argument for dc-scan-opt\n", progname);
        usage();
      }
      jpeg_c_set_int_param(cinfo, JINT_DC_SCAN_OPT_MODE, atoi(argv[argn]));

    } else if (keymatch(arg, "optimize", 1) || keymatch(arg, "optimise", 1)) {
      /* Enable entropy parm optimization. */
#ifdef ENTROPY_OPT_SUPPORTED
      cinfo->optimize_coding = TRUE;
#else
      fprintf(stderr, "%s: sorry, entropy optimization was not compiled in\n",
              progname);
      exit(EXIT_FAILURE);
#endif

    } else if (keymatch(arg, "outfile", 4)) {
      /* Set output file name. */
      if (++argn >= argc) {      /* advance to next argument */
        fprintf(stderr, "%s: missing argument for outfile\n", progname);
        usage();
      }
      outfilename = argv[argn]; /* save it away for later use */

    } else if (keymatch(arg, "progressive", 1)) {
      /* Select simple progressive mode. */
#ifdef C_PROGRESSIVE_SUPPORTED
      simple_progressive = TRUE;
      /* We must postpone execution until num_components is known. */
#else
      fprintf(stderr, "%s: sorry, progressive output was not compiled in\n",
              progname);
      exit(EXIT_FAILURE);
#endif

    } else if (keymatch(arg, "memdst", 2)) {
      /* Use in-memory destination manager */
#if JPEG_LIB_VERSION >= 80 || defined(MEM_SRCDST_SUPPORTED)
      memdst = TRUE;
#else
      fprintf(stderr, "%s: sorry, in-memory destination manager was not compiled in\n",
              progname);
      exit(EXIT_FAILURE);
#endif

    } else if (keymatch(arg, "quality", 1)) {
      /* Quality ratings (quantization table scaling factors). */
      if (++argn >= argc) {      /* advance to next argument */
        fprintf(stderr, "%s: missing argument for quality\n", progname);
        usage();
      }
      qualityarg = argv[argn];

    } else if (keymatch(arg, "qslots", 2)) {
      /* Quantization table slot numbers. */
      if (++argn >= argc)       /* advance to next argument */
        usage();
      qslotsarg = argv[argn];
      /* Must delay setting qslots until after we have processed any
       * colorspace-determining switches, since jpeg_set_colorspace sets
       * default quant table numbers.
       */

    } else if (keymatch(arg, "qtables", 2)) {
      /* Quantization tables fetched from file. */
      if (++argn >= argc)       /* advance to next argument */
        usage();
      qtablefile = argv[argn];
      /* We postpone actually reading the file in case -quality comes later. */

    } else if (keymatch(arg, "quant-table", 7)) {
      int val;
      if (++argn >= argc)       /* advance to next argument */
        usage();
      val = atoi(argv[argn]);
      jpeg_c_set_int_param(cinfo, JINT_BASE_QUANT_TBL_IDX, val);
      if (jpeg_c_get_int_param(cinfo, JINT_BASE_QUANT_TBL_IDX) != val) {
        fprintf(stderr, "%s: %d is invalid argument for quant-table\n", progname, val);
        usage();
      }
      jpeg_set_quality(cinfo, 75, TRUE);

    } else if (keymatch(arg, "quant-baseline", 7)) {
      /* Force quantization table to meet baseline requirements */
      force_baseline = TRUE;
    
    } else if (keymatch(arg, "restart", 1)) {
      /* Restart interval in MCU rows (or in MCUs with 'b'). */
      long lval;
      char ch = 'x';

      if (++argn >= argc)       /* advance to next argument */
        usage();
      if (sscanf(argv[argn], "%ld%c", &lval, &ch) < 1)
        usage();
      if (lval < 0 || lval > 65535L)
        usage();
      if (ch == 'b' || ch == 'B') {
        cinfo->restart_interval = (unsigned int)lval;
        cinfo->restart_in_rows = 0; /* else prior '-restart n' overrides me */
      } else {
        cinfo->restart_in_rows = (int)lval;
        /* restart_interval will be computed during startup */
      }

    } else if (keymatch(arg, "revert", 3)) {
      /* revert to old JPEG default */
      jpeg_c_set_int_param(cinfo, JINT_COMPRESS_PROFILE, JCP_FASTEST);
      jpeg_set_defaults(cinfo);

    } else if (keymatch(arg, "sample", 2)) {
      /* Set sampling factors. */
      if (++argn >= argc)       /* advance to next argument */
        usage();
      samplearg = argv[argn];
      /* Must delay setting sample factors until after we have processed any
       * colorspace-determining switches, since jpeg_set_colorspace sets
       * default sampling factors.
       */

    } else if (keymatch(arg, "scans", 4)) {
      /* Set scan script. */
#ifdef C_MULTISCAN_FILES_SUPPORTED
      if (++argn >= argc)       /* advance to next argument */
        usage();
      scansarg = argv[argn];
      /* We must postpone reading the file in case -progressive appears. */
#else
      fprintf(stderr, "%s: sorry, multi-scan output was not compiled in\n",
              progname);
      exit(EXIT_FAILURE);
#endif

    } else if (keymatch(arg, "smooth", 2)) {
      /* Set input smoothing factor. */
      int val;

      if (++argn >= argc)       /* advance to next argument */
        usage();
      if (sscanf(argv[argn], "%d", &val) != 1)
        usage();
      if (val < 0 || val > 100)
        usage();
      cinfo->smoothing_factor = val;

    } else if (keymatch(arg, "targa", 1)) {
      /* Input file is Targa format. */
      is_targa = TRUE;

    } else if (keymatch(arg, "notrellis-dc", 11)) {
      /* disable trellis quantization */
      jpeg_c_set_bool_param(cinfo, JBOOLEAN_TRELLIS_QUANT_DC, FALSE);
      
    } else if (keymatch(arg, "notrellis", 1)) {
      /* disable trellis quantization */
      jpeg_c_set_bool_param(cinfo, JBOOLEAN_TRELLIS_QUANT, FALSE);
      
    } else if (keymatch(arg, "trellis-dc-ver-weight", 12)) {
      if (++argn >= argc) {      /* advance to next argument */
        fprintf(stderr, "%s: missing argument for trellis-dc-ver-weight\n", progname);
        usage();
      }
      jpeg_c_set_float_param(cinfo, JFLOAT_TRELLIS_DELTA_DC_WEIGHT, atof(argv[argn]));
      
    } else if (keymatch(arg, "trellis-dc", 9)) {
      /* enable DC trellis quantization */
      jpeg_c_set_bool_param(cinfo, JBOOLEAN_TRELLIS_QUANT_DC, TRUE);
      
    } else if (keymatch(arg, "tune-psnr", 6)) {
      jpeg_c_set_int_param(cinfo, JINT_BASE_QUANT_TBL_IDX, 1);
      jpeg_c_set_float_param(cinfo, JFLOAT_LAMBDA_LOG_SCALE1, 9.0);
      jpeg_c_set_float_param(cinfo, JFLOAT_LAMBDA_LOG_SCALE2, 0.0);
      jpeg_c_set_bool_param(cinfo, JBOOLEAN_USE_LAMBDA_WEIGHT_TBL, FALSE);
      jpeg_set_quality(cinfo, 75, TRUE);
      
    } else if (keymatch(arg, "tune-ssim", 6)) {
      jpeg_c_set_int_param(cinfo, JINT_BASE_QUANT_TBL_IDX, 1);
      jpeg_c_set_float_param(cinfo, JFLOAT_LAMBDA_LOG_SCALE1, 11.5);
      jpeg_c_set_float_param(cinfo, JFLOAT_LAMBDA_LOG_SCALE2, 12.75);
      jpeg_c_set_bool_param(cinfo, JBOOLEAN_USE_LAMBDA_WEIGHT_TBL, FALSE);
      jpeg_set_quality(cinfo, 75, TRUE);
      
    } else if (keymatch(arg, "tune-ms-ssim", 6)) {
      jpeg_c_set_int_param(cinfo, JINT_BASE_QUANT_TBL_IDX, 3);
      jpeg_c_set_float_param(cinfo, JFLOAT_LAMBDA_LOG_SCALE1, 12.0);
      jpeg_c_set_float_param(cinfo, JFLOAT_LAMBDA_LOG_SCALE2, 13.0);
      jpeg_c_set_bool_param(cinfo, JBOOLEAN_USE_LAMBDA_WEIGHT_TBL, TRUE);
      jpeg_set_quality(cinfo, 75, TRUE);
      
    } else if (keymatch(arg, "tune-hvs-psnr", 6)) {
      jpeg_c_set_int_param(cinfo, JINT_BASE_QUANT_TBL_IDX, 3);
      jpeg_c_set_float_param(cinfo, JFLOAT_LAMBDA_LOG_SCALE1, 14.75);
      jpeg_c_set_float_param(cinfo, JFLOAT_LAMBDA_LOG_SCALE2, 16.5);
      jpeg_c_set_bool_param(cinfo, JBOOLEAN_USE_LAMBDA_WEIGHT_TBL, TRUE);
      jpeg_set_quality(cinfo, 75, TRUE);

    } else if (keymatch(arg, "noovershoot", 11)) {
      jpeg_c_set_bool_param(cinfo, JBOOLEAN_OVERSHOOT_DERINGING, FALSE);

	} else if (keymatch(arg, "nojfif", 6)) {
      cinfo->write_JFIF_header = 0;
    } else {
      fprintf(stderr, "%s: unknown option '%s'\n", progname, arg);
      usage();                  /* bogus switch */
    }
  }

  /* Post-switch-scanning cleanup */

  if (for_real) {

    /* Set quantization tables for selected quality. */
    /* Some or all may be overridden if -qtables is present. */
    if (qualityarg != NULL)     /* process -quality if it was present */
      if (! set_quality_ratings(cinfo, qualityarg, force_baseline)) {
        fprintf(stderr, "%s: can't set quality ratings\n", progname);
        usage();
      }

    if (qtablefile != NULL)     /* process -qtables if it was present */
      if (! read_quant_tables(cinfo, qtablefile, force_baseline)) {
        fprintf(stderr, "%s: can't read qtable file\n", progname);
        usage();
      }

    if (qslotsarg != NULL)      /* process -qslots if it was present */
      if (!set_quant_slots(cinfo, qslotsarg))
        usage();

    /* set_quality_ratings sets default subsampling, so the explicit
       subsampling must be set after it */
    if (samplearg != NULL)      /* process -sample if it was present */
      if (! set_sample_factors(cinfo, samplearg)) {
        fprintf(stderr, "%s: can't set sample factors\n", progname);
        usage();
      }

#ifdef C_PROGRESSIVE_SUPPORTED
    if (simple_progressive)     /* process -progressive; -scans can override */
      jpeg_simple_progression(cinfo);
#endif

#ifdef C_MULTISCAN_FILES_SUPPORTED
    if (scansarg != NULL)       /* process -scans if it was present */
      if (!read_scan_script(cinfo, scansarg))
        usage();
#endif
  }

  return argn;                  /* return index of next arg (file name) */
}


/*
 * The main program.
 */

int
main(int argc, char **argv)
{
  struct jpeg_compress_struct cinfo;
  struct jpeg_error_mgr jerr;
#ifdef PROGRESS_REPORT
  struct cdjpeg_progress_mgr progress;
#endif
  int file_index;
  cjpeg_source_ptr src_mgr;
  FILE *input_file;
  FILE *icc_file;
  JOCTET *icc_profile = NULL;
  long icc_len = 0;
  FILE *output_file = NULL;
  unsigned char *outbuffer = NULL;
  unsigned long outsize = 0;
  JDIMENSION num_scanlines;

  /* On Mac, fetch a command line. */
#ifdef USE_CCOMMAND
  argc = ccommand(&argv);
#endif

  progname = argv[0];
  if (progname == NULL || progname[0] == 0)
    progname = "cjpeg";         /* in case C library doesn't provide it */

  /* Initialize the JPEG compression object with default error handling. */
  cinfo.err = jpeg_std_error(&jerr);
  jpeg_create_compress(&cinfo);
  /* Add some application-specific error messages (from cderror.h) */
  jerr.addon_message_table = cdjpeg_message_table;
  jerr.first_addon_message = JMSG_FIRSTADDONCODE;
  jerr.last_addon_message = JMSG_LASTADDONCODE;

  /* Initialize JPEG parameters.
   * Much of this may be overridden later.
   * In particular, we don't yet know the input file's color space,
   * but we need to provide some value for jpeg_set_defaults() to work.
   */

  cinfo.in_color_space = JCS_RGB; /* arbitrary guess */
  jpeg_set_defaults(&cinfo);

  /* Scan command line to find file names.
   * It is convenient to use just one switch-parsing routine, but the switch
   * values read here are ignored; we will rescan the switches after opening
   * the input file.
   */

  file_index = parse_switches(&cinfo, argc, argv, 0, FALSE);

#ifdef TWO_FILE_COMMANDLINE
  if (!memdst) {
    /* Must have either -outfile switch or explicit output file name */
    if (outfilename == NULL) {
      if (file_index != argc - 2) {
        fprintf(stderr, "%s: must name one input and one output file\n",
                progname);
        usage();
      }
      outfilename = argv[file_index + 1];
    } else {
      if (file_index != argc - 1) {
        fprintf(stderr, "%s: must name one input and one output file\n",
                progname);
        usage();
      }
    }
  }
#else
  /* Unix style: expect zero or one file name */
  if (file_index < argc - 1) {
    fprintf(stderr, "%s: only one input file\n", progname);
    usage();
  }
#endif /* TWO_FILE_COMMANDLINE */

  /* Open the input file. */
  if (file_index < argc) {
    if ((input_file = fopen(argv[file_index], READ_BINARY)) == NULL) {
      fprintf(stderr, "%s: can't open %s\n", progname, argv[file_index]);
      exit(EXIT_FAILURE);
    }
  } else {
    /* default input file is stdin */
    input_file = read_stdin();
  }

  /* Open the output file. */
  if (outfilename != NULL) {
    if ((output_file = fopen(outfilename, WRITE_BINARY)) == NULL) {
      fprintf(stderr, "%s: can't open %s\n", progname, outfilename);
      exit(EXIT_FAILURE);
    }
  } else if (!memdst) {
    /* default output file is stdout */
    output_file = write_stdout();
  }

  if (icc_filename != NULL) {
    if ((icc_file = fopen(icc_filename, READ_BINARY)) == NULL) {
      fprintf(stderr, "%s: can't open %s\n", progname, icc_filename);
      exit(EXIT_FAILURE);
    }
    if (fseek(icc_file, 0, SEEK_END) < 0 ||
        (icc_len = ftell(icc_file)) < 1 ||
        fseek(icc_file, 0, SEEK_SET) < 0) {
      fprintf(stderr, "%s: can't determine size of %s\n", progname,
              icc_filename);
      exit(EXIT_FAILURE);
    }
    if ((icc_profile = (JOCTET *)malloc(icc_len)) == NULL) {
      fprintf(stderr, "%s: can't allocate memory for ICC profile\n", progname);
      fclose(icc_file);
      exit(EXIT_FAILURE);
    }
    if (fread(icc_profile, icc_len, 1, icc_file) < 1) {
      fprintf(stderr, "%s: can't read ICC profile from %s\n", progname,
              icc_filename);
      free(icc_profile);
      fclose(icc_file);
      exit(EXIT_FAILURE);
    }
    fclose(icc_file);
  }

#ifdef PROGRESS_REPORT
  start_progress_monitor((j_common_ptr)&cinfo, &progress);
#endif

  /* Figure out the input file format, and set up to read it. */
  src_mgr = select_file_type(&cinfo, input_file);
  src_mgr->input_file = input_file;

  /* Read the input file header to obtain file size & colorspace. */
  (*src_mgr->start_input) (&cinfo, src_mgr);

  /* Now that we know input colorspace, fix colorspace-dependent defaults */
#if JPEG_RAW_READER
  if (!is_jpeg)
#endif
  jpeg_default_colorspace(&cinfo);

  /* Adjust default compression parameters by re-parsing the options */
  file_index = parse_switches(&cinfo, argc, argv, 0, TRUE);

  /* Specify data destination for compression */
#if JPEG_LIB_VERSION >= 80 || defined(MEM_SRCDST_SUPPORTED)
  if (memdst)
    jpeg_mem_dest(&cinfo, &outbuffer, &outsize);
  else
#endif
    jpeg_stdio_dest(&cinfo, output_file);

  /* Start compressor */
  jpeg_start_compress(&cinfo, TRUE);

  /* Copy metadata */
  if (copy_markers) {
    jpeg_saved_marker_ptr marker;
    
    /* In the current implementation, we don't actually need to examine the
     * option flag here; we just copy everything that got saved.
     * But to avoid confusion, we do not output JFIF and Adobe APP14 markers
     * if the encoder library already wrote one.
     */
    for (marker = src_mgr->marker_list; marker != NULL; marker = marker->next) {
      if (cinfo.write_JFIF_header &&
          marker->marker == JPEG_APP0 &&
          marker->data_length >= 5 &&
          GETJOCTET(marker->data[0]) == 0x4A &&
          GETJOCTET(marker->data[1]) == 0x46 &&
          GETJOCTET(marker->data[2]) == 0x49 &&
          GETJOCTET(marker->data[3]) == 0x46 &&
          GETJOCTET(marker->data[4]) == 0)
        continue;                       /* reject duplicate JFIF */
      if (cinfo.write_Adobe_marker &&
          marker->marker == JPEG_APP0+14 &&
          marker->data_length >= 5 &&
          GETJOCTET(marker->data[0]) == 0x41 &&
          GETJOCTET(marker->data[1]) == 0x64 &&
          GETJOCTET(marker->data[2]) == 0x6F &&
          GETJOCTET(marker->data[3]) == 0x62 &&
          GETJOCTET(marker->data[4]) == 0x65)
        continue;                       /* reject duplicate Adobe */
      jpeg_write_marker(&cinfo, marker->marker, marker->data,
                        marker->data_length);
    }
  }
  if (icc_profile != NULL)
    jpeg_write_icc_profile(&cinfo, icc_profile, (unsigned int)icc_len);
  
  /* Process data */
  while (cinfo.next_scanline < cinfo.image_height) {
    num_scanlines = (*src_mgr->get_pixel_rows) (&cinfo, src_mgr);
#if JPEG_RAW_READER
    if (is_jpeg)
      (void) jpeg_write_raw_data(&cinfo, src_mgr->plane_pointer, num_scanlines);
    else
#endif
    (void) jpeg_write_scanlines(&cinfo, src_mgr->buffer, num_scanlines);
  }

  /* Finish compression and release memory */
  (*src_mgr->finish_input) (&cinfo, src_mgr);
  jpeg_finish_compress(&cinfo);
  jpeg_destroy_compress(&cinfo);

  /* Close files, if we opened them */
  if (input_file != stdin)
    fclose(input_file);
  if (output_file != stdout && output_file != NULL)
    fclose(output_file);

#ifdef PROGRESS_REPORT
  end_progress_monitor((j_common_ptr)&cinfo);
#endif

  if (memdst) {
    fprintf(stderr, "Compressed size:  %lu bytes\n", outsize);
    free(outbuffer);
  }

  free(icc_profile);

  /* All done. */
  exit(jerr.num_warnings ? EXIT_WARNING : EXIT_SUCCESS);
  return 0;                     /* suppress no-return-value warnings */
}
