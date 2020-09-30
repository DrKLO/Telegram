Mozilla JPEG Encoder Project
============================

mozjpeg is a fork of libjpeg-turbo that aims to speed up load times of web
pages by reducing the size (and, by extension, the transmission time) of JPEG
files.  It accomplishes this by enabling optimized Huffman trees and
progressive entropy coding by default in the JPEG compressor, as well as
splitting the spectrum of DCT coefficients into separate scans and using
Trellis quantisation.

Although it is based on libjpeg-turbo, mozjpeg is not intended to be a
general-purpose or high-performance JPEG library.  Its performance is highly
"asymmetric".  That is, the JPEG files it generates require much more time to
compress than to decompress.  When the default settings are used, mozjpeg is
considerably slower than libjpeg-turbo or even libjpeg at compressing images.
Thus, it is not generally suitable for real-time compression.  It is best used
as part of a web encoding workflow.


libjpeg API Extensibility Framework
===================================

mozjpeg's implementation of the libjpeg API includes an extensibility framework
that allows new features to be added without modifying the transparent libjpeg
compress/decompress structures (which would break backward ABI compatibility.)
Extension parameters are placed into the opaque jpeg_comp_master structure, and
a set of accessor functions and globally unique tokens allows for 
getting/setting those parameters without directly accessing the structure.

Currently, only the accessor functions necessary to support the mozjpeg
extensions are implemented, but the framework can be easily extended in the
future to accommodate additional simple parameter types, complex or
multi-valued parameters, or decompressor extensions.


The currently-implemented accessor functions are as follows:

boolean jpeg_c_bool_param_supported (j_compress_ptr cinfo,
                                     J_BOOLEAN_PARAM param)
        Returns TRUE if the given boolean extension parameter is supported by
        this implementation of the libjpeg API, or FALSE otherwise.

void jpeg_c_set_bool_param (j_compress_ptr cinfo,
                            J_BOOLEAN_PARAM param, boolean value);
        Set the given boolean extension parameter to the given value (TRUE or
        FALSE.)

boolean jpeg_c_get_bool_param (j_compress_ptr cinfo, J_BOOLEAN_PARAM param)
        Get the value of the given boolean extension parameter (TRUE or FALSE.)

boolean jpeg_c_float_param_supported (j_compress_ptr cinfo,
                                      J_FLOAT_PARAM param)
        Returns TRUE if the given floating point extension parameter is
        supported by this implementation of the libjpeg API, or FALSE
        otherwise.

void jpeg_c_set_float_param (j_compress_ptr cinfo, J_FLOAT_PARAM param,
                             float value)
        Set the given floating point extension parameter to the given value.

float jpeg_c_get_float_param (j_compress_ptr cinfo, J_FLOAT_PARAM param);
        Get the value of the given floating point extension parameter.

boolean jpeg_c_int_param_supported (j_compress_ptr cinfo,
                                    J_INT_PARAM param)
        Returns TRUE if the given integer extension parameter is supported by
        this implementation of the libjpeg API, or FALSE otherwise.

void jpeg_c_set_int_param (j_compress_ptr cinfo, J_INT_PARAM param,
                          int value)
        Set the given integer extension parameter to the given value.

int jpeg_c_get_int_param (j_compress_ptr cinfo, J_INT_PARAM param)
        Get the value of the given integer extension parameter.


Boolean Extension Parameters Supported by mozjpeg
-------------------------------------------------

* JBOOLEAN_OPTIMIZE_SCANS (default: TRUE)
  Specifies whether scan parameters should be optimized.  Parameter
  optimization is done as in jpgcrush. jpeg_simple_progression() should be called
  after setting JBOOLEAN_OPTIMIZE_SCANS.
  When disabling JBOOLEAN_OPTIMIZE_SCANS, cinfo.scan_info should additionally be
  set to NULL to disable use of the progressive coding mode, if so desired.

* JBOOLEAN_TRELLIS_QUANT (default: TRUE)
  Specifies whether to apply trellis quantization.  For each 8x8 block, trellis
  quantization determines the best tradeoff between rate and distortion.

* JBOOLEAN_TRELLIS_QUANT_DC (default: TRUE)
  Specifies whether to apply trellis quantization to DC coefficients.

* JBOOLEAN_TRELLIS_EOB_OPT (default: FALSE)
  Specifies whether to optimize runs of zero blocks in trellis quantization.
  This is applicable only when JBOOLEAN_USE_SCANS_IN_TRELLIS is enabled.

* JBOOLEAN_USE_LAMBDA_WEIGHT_TBL currently has no effect.

* JBOOLEAN_USE_SCANS_IN_TRELLIS (default: FALSE)
  Specifies whether multiple scans should be considered during trellis
  quantization.

* JBOOLEAN_TRELLIS_Q_OPT (default: FALSE)
  Specifies whether to optimize the quantization table after trellis
  quantization.  If enabled, then a revised quantization table is derived so
  as to minimize the reconstruction error of the quantized coefficients.

* JBOOLEAN_OVERSHOOT_DERINGING (default: TRUE)
  Specifies whether overshooting is applied to samples with extreme values
  (for example, 0 and 255 for 8-bit samples).  Overshooting may reduce ringing
  artifacts from compression, in particular in areas where black text appears
  on a white background.


Floating Point Extension Parameters Supported by mozjpeg
--------------------------------------------------------

* JFLOAT_LAMBDA_LOG_SCALE1 (default: 14.75)
  JFLOAT_LAMBDA_LOG_SCALE2 (default: 16.5)
  These parameters specify the lambda value used in trellis quantization.  The
  lambda value (Lagrange multiplier) in the
    R + lambda * D
  equation is derived from
    lambda = 2^s1 / ((2^s2 + n) * q^2),
  where s1 and s2 are the values of JFLOAT_LAMBDA_LOG_SCALE1 and
  JFLOAT_LAMBDA_LOG_SCALE2, n is the average of the squared unquantized AC
  coefficients within the current 8x8 block, and q is the quantization table
  entry associated with the current coefficient frequency.  If
  JFLOAT_LAMBDA_LOG_SCALE2 is 0, then an alternate form is used that does not
  rely on n:
    lambda = 2^(s1-12) / q^2.

* JFLOAT_TRELLIS_DELTA_DC_WEIGHT (default: 0.0)
  This parameter controls how distortion is calculated in DC trellis quantization
  (enabled with JBOOLEAN_TRELLIS_QUANT_DC). It defines weighting between distortion
  of the DC coefficient and distortion of the vertical gradient of DC coefficients.
  The value of the parameter corresponds to the weight applied to the distortion
  of the vertical gradient.


Integer Extension Parameters Supported by mozjpeg
-------------------------------------------------

* JINT_COMPRESS_PROFILE (default: JCP_MAX_COMPRESSION)
  Select a compression profile, which is a set of default parameters that will
  achieve a desired compression goal.  This parameter controls the behavior of
  the jpeg_set_defaults() function.  Thus, setting JINT_COMPRESS_PROFILE does
  not cause any other parameters to be modified until jpeg_set_defaults() is
  called.  The following compression profiles are supported:

  - JCP_MAX_COMPRESSION (default)
    Increase the compression ratio as much as possible, at the expense of
    increased encoding time.  This enables progressive entropy coding and all
    mozjpeg extensions.

  - JCP_FASTEST
    Use the libjpeg[-turbo] defaults (baseline entropy coding, no mozjpeg
    extensions enabled.)

* JINT_TRELLIS_FREQ_SPLIT (default: 8)
  Specifies the position within the zigzag scan at which the split between
  scans is positioned in the context of trellis quantization.
  JBOOLEAN_USE_SCANS_IN_TRELLIS must be enabled for this parameter to have any
  effect.

* JINT_TRELLIS_NUM_LOOPS (default: 1)
  Specifies the number of trellis quantization passes.  Huffman tables are
  updated between passes.

* JINT_BASE_QUANT_TBL_IDX (default: 3)
  Specifies which quantization table set to use.  The following options are
  available:
  0 = Tables from JPEG Annex K
  1 = Flat table
  2 = Table tuned for MSSIM on Kodak image set
  3 = Table from http://www.imagemagick.org/discourse-server/viewtopic.php?f=22&t=20333&p=98008#p98008
  4 = Table tuned for PSNR-HVS-M on Kodak image set
  5 = Table from:  Relevance of Human Vision to JPEG-DCT Compression
      (1992) Klein, Silverstein and Carney
  6 = Table from:  DCTune Perceptual Optimization of Compressed Dental X-Rays
      (1997) Watson, Taylor, Borthwick
  7 = Table from:  A Visual Detection Model for DCT Coefficient Quantization
      (12/9/93) Ahumada, Watson, Peterson
  8 = Table from:  An Improved Detection Model for DCT Coefficient Quantization
      (1993) Peterson, Ahumada and Watson

* JINT_DC_SCAN_OPT_MODE (default: 1)
  Specifies the DC scan optimization mode.  The following options are
  available:
  0 = One scan for all components
  1 = One scan per component
  2 = Optimize between one scan for all components and one scan for the first
      component plus one scan for the remaining components
