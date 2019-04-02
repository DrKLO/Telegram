/* libFLAC - Free Lossless Audio Codec library
 * Copyright (C) 2000-2009  Josh Coalson
 * Copyright (C) 2011-2016  Xiph.Org Foundation
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * - Redistributions of source code must retain the above copyright
 * notice, this list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above copyright
 * notice, this list of conditions and the following disclaimer in the
 * documentation and/or other materials provided with the distribution.
 *
 * - Neither the name of the Xiph.org Foundation nor the names of its
 * contributors may be used to endorse or promote products derived from
 * this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * ``AS IS'' AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE FOUNDATION OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

#ifdef HAVE_CONFIG_H
#  include <config.h>
#endif

#include <limits.h>
#include <stdio.h>
#include <stdlib.h> /* for malloc() */
#include <string.h> /* for memcpy() */
#include <sys/types.h> /* for off_t */
#ifdef _WIN32
#include <windows.h> /* for GetFileType() */
#include <io.h> /* for _get_osfhandle() */
#endif
#include "share/compat.h"
#include "FLAC/assert.h"
#include "FLAC/stream_decoder.h"
#include "protected/stream_encoder.h"
#include "private/bitwriter.h"
#include "private/bitmath.h"
#include "private/crc.h"
#include "private/cpu.h"
#include "private/fixed.h"
#include "private/format.h"
#include "private/lpc.h"
#include "private/md5.h"
#include "private/memory.h"
#include "private/macros.h"
#if FLAC__HAS_OGG
#include "private/ogg_helper.h"
#include "private/ogg_mapping.h"
#endif
#include "private/stream_encoder.h"
#include "private/stream_encoder_framing.h"
#include "private/window.h"
#include "share/alloc.h"
#include "share/private.h"

#define FLAC__HAS_OGG 0


/* Exact Rice codeword length calculation is off by default.  The simple
 * (and fast) estimation (of how many bits a residual value will be
 * encoded with) in this encoder is very good, almost always yielding
 * compression within 0.1% of exact calculation.
 */
#undef EXACT_RICE_BITS_CALCULATION
/* Rice parameter searching is off by default.  The simple (and fast)
 * parameter estimation in this encoder is very good, almost always
 * yielding compression within 0.1% of the optimal parameters.
 */
#undef ENABLE_RICE_PARAMETER_SEARCH


typedef struct {
	FLAC__int32 *data[FLAC__MAX_CHANNELS];
	uint32_t size; /* of each data[] in samples */
	uint32_t tail;
} verify_input_fifo;

typedef struct {
	const FLAC__byte *data;
	uint32_t capacity;
	uint32_t bytes;
} verify_output;

typedef enum {
	ENCODER_IN_MAGIC = 0,
	ENCODER_IN_METADATA = 1,
	ENCODER_IN_AUDIO = 2
} EncoderStateHint;

static const  struct CompressionLevels {
	FLAC__bool do_mid_side_stereo;
	FLAC__bool loose_mid_side_stereo;
	uint32_t max_lpc_order;
	uint32_t qlp_coeff_precision;
	FLAC__bool do_qlp_coeff_prec_search;
	FLAC__bool do_escape_coding;
	FLAC__bool do_exhaustive_model_search;
	uint32_t min_residual_partition_order;
	uint32_t max_residual_partition_order;
	uint32_t rice_parameter_search_dist;
	const char *apodization;
} compression_levels_[] = {
	{ false, false,  0, 0, false, false, false, 0, 3, 0, "tukey(5e-1)" },
	{ true , true ,  0, 0, false, false, false, 0, 3, 0, "tukey(5e-1)" },
	{ true , false,  0, 0, false, false, false, 0, 3, 0, "tukey(5e-1)" },
	{ false, false,  6, 0, false, false, false, 0, 4, 0, "tukey(5e-1)" },
	{ true , true ,  8, 0, false, false, false, 0, 4, 0, "tukey(5e-1)" },
	{ true , false,  8, 0, false, false, false, 0, 5, 0, "tukey(5e-1)" },
	{ true , false,  8, 0, false, false, false, 0, 6, 0, "tukey(5e-1);partial_tukey(2)" },
	{ true , false, 12, 0, false, false, false, 0, 6, 0, "tukey(5e-1);partial_tukey(2)" },
	{ true , false, 12, 0, false, false, false, 0, 6, 0, "tukey(5e-1);partial_tukey(2);punchout_tukey(3)" }
	/* here we use locale-independent 5e-1 instead of 0.5 or 0,5 */
};


/***********************************************************************
 *
 * Private class method prototypes
 *
 ***********************************************************************/

static void set_defaults_(FLAC__StreamEncoder *encoder);
static void free_(FLAC__StreamEncoder *encoder);
static FLAC__bool resize_buffers_(FLAC__StreamEncoder *encoder, uint32_t new_blocksize);
static FLAC__bool write_bitbuffer_(FLAC__StreamEncoder *encoder, uint32_t samples, FLAC__bool is_last_block);
static FLAC__StreamEncoderWriteStatus write_frame_(FLAC__StreamEncoder *encoder, const FLAC__byte buffer[], size_t bytes, uint32_t samples, FLAC__bool is_last_block);
static void update_metadata_(const FLAC__StreamEncoder *encoder);
#if FLAC__HAS_OGG
static void update_ogg_metadata_(FLAC__StreamEncoder *encoder);
#endif
static FLAC__bool process_frame_(FLAC__StreamEncoder *encoder, FLAC__bool is_fractional_block, FLAC__bool is_last_block);
static FLAC__bool process_subframes_(FLAC__StreamEncoder *encoder, FLAC__bool is_fractional_block);

static FLAC__bool process_subframe_(
	FLAC__StreamEncoder *encoder,
	uint32_t min_partition_order,
	uint32_t max_partition_order,
	const FLAC__FrameHeader *frame_header,
	uint32_t subframe_bps,
	const FLAC__int32 integer_signal[],
	FLAC__Subframe *subframe[2],
	FLAC__EntropyCodingMethod_PartitionedRiceContents *partitioned_rice_contents[2],
	FLAC__int32 *residual[2],
	uint32_t *best_subframe,
	uint32_t *best_bits
);

static FLAC__bool add_subframe_(
	FLAC__StreamEncoder *encoder,
	uint32_t blocksize,
	uint32_t subframe_bps,
	const FLAC__Subframe *subframe,
	FLAC__BitWriter *frame
);

static uint32_t evaluate_constant_subframe_(
	FLAC__StreamEncoder *encoder,
	const FLAC__int32 signal,
	uint32_t blocksize,
	uint32_t subframe_bps,
	FLAC__Subframe *subframe
);

static uint32_t evaluate_fixed_subframe_(
	FLAC__StreamEncoder *encoder,
	const FLAC__int32 signal[],
	FLAC__int32 residual[],
	FLAC__uint64 abs_residual_partition_sums[],
	uint32_t raw_bits_per_partition[],
	uint32_t blocksize,
	uint32_t subframe_bps,
	uint32_t order,
	uint32_t rice_parameter,
	uint32_t rice_parameter_limit,
	uint32_t min_partition_order,
	uint32_t max_partition_order,
	FLAC__bool do_escape_coding,
	uint32_t rice_parameter_search_dist,
	FLAC__Subframe *subframe,
	FLAC__EntropyCodingMethod_PartitionedRiceContents *partitioned_rice_contents
);

#ifndef FLAC__INTEGER_ONLY_LIBRARY
static uint32_t evaluate_lpc_subframe_(
	FLAC__StreamEncoder *encoder,
	const FLAC__int32 signal[],
	FLAC__int32 residual[],
	FLAC__uint64 abs_residual_partition_sums[],
	uint32_t raw_bits_per_partition[],
	const FLAC__real lp_coeff[],
	uint32_t blocksize,
	uint32_t subframe_bps,
	uint32_t order,
	uint32_t qlp_coeff_precision,
	uint32_t rice_parameter,
	uint32_t rice_parameter_limit,
	uint32_t min_partition_order,
	uint32_t max_partition_order,
	FLAC__bool do_escape_coding,
	uint32_t rice_parameter_search_dist,
	FLAC__Subframe *subframe,
	FLAC__EntropyCodingMethod_PartitionedRiceContents *partitioned_rice_contents
);
#endif

static uint32_t evaluate_verbatim_subframe_(
	FLAC__StreamEncoder *encoder,
	const FLAC__int32 signal[],
	uint32_t blocksize,
	uint32_t subframe_bps,
	FLAC__Subframe *subframe
);

static uint32_t find_best_partition_order_(
	struct FLAC__StreamEncoderPrivate *private_,
	const FLAC__int32 residual[],
	FLAC__uint64 abs_residual_partition_sums[],
	uint32_t raw_bits_per_partition[],
	uint32_t residual_samples,
	uint32_t predictor_order,
	uint32_t rice_parameter,
	uint32_t rice_parameter_limit,
	uint32_t min_partition_order,
	uint32_t max_partition_order,
	uint32_t bps,
	FLAC__bool do_escape_coding,
	uint32_t rice_parameter_search_dist,
	FLAC__EntropyCodingMethod *best_ecm
);

static void precompute_partition_info_sums_(
	const FLAC__int32 residual[],
	FLAC__uint64 abs_residual_partition_sums[],
	uint32_t residual_samples,
	uint32_t predictor_order,
	uint32_t min_partition_order,
	uint32_t max_partition_order,
	uint32_t bps
);

static void precompute_partition_info_escapes_(
	const FLAC__int32 residual[],
	uint32_t raw_bits_per_partition[],
	uint32_t residual_samples,
	uint32_t predictor_order,
	uint32_t min_partition_order,
	uint32_t max_partition_order
);

static FLAC__bool set_partitioned_rice_(
#ifdef EXACT_RICE_BITS_CALCULATION
	const FLAC__int32 residual[],
#endif
	const FLAC__uint64 abs_residual_partition_sums[],
	const uint32_t raw_bits_per_partition[],
	const uint32_t residual_samples,
	const uint32_t predictor_order,
	const uint32_t suggested_rice_parameter,
	const uint32_t rice_parameter_limit,
	const uint32_t rice_parameter_search_dist,
	const uint32_t partition_order,
	const FLAC__bool search_for_escapes,
	FLAC__EntropyCodingMethod_PartitionedRiceContents *partitioned_rice_contents,
	uint32_t *bits
);

static uint32_t get_wasted_bits_(FLAC__int32 signal[], uint32_t samples);

/* verify-related routines: */
static void append_to_verify_fifo_(
	verify_input_fifo *fifo,
	const FLAC__int32 * const input[],
	uint32_t input_offset,
	uint32_t channels,
	uint32_t wide_samples
);

static void append_to_verify_fifo_interleaved_(
	verify_input_fifo *fifo,
	const FLAC__int32 input[],
	uint32_t input_offset,
	uint32_t channels,
	uint32_t wide_samples
);

static FLAC__StreamDecoderReadStatus verify_read_callback_(const FLAC__StreamDecoder *decoder, FLAC__byte buffer[], size_t *bytes, void *client_data);
static FLAC__StreamDecoderWriteStatus verify_write_callback_(const FLAC__StreamDecoder *decoder, const FLAC__Frame *frame, const FLAC__int32 * const buffer[], void *client_data);
static void verify_metadata_callback_(const FLAC__StreamDecoder *decoder, const FLAC__StreamMetadata *metadata, void *client_data);
static void verify_error_callback_(const FLAC__StreamDecoder *decoder, FLAC__StreamDecoderErrorStatus status, void *client_data);

static FLAC__StreamEncoderReadStatus file_read_callback_(const FLAC__StreamEncoder *encoder, FLAC__byte buffer[], size_t *bytes, void *client_data);
static FLAC__StreamEncoderSeekStatus file_seek_callback_(const FLAC__StreamEncoder *encoder, FLAC__uint64 absolute_byte_offset, void *client_data);
static FLAC__StreamEncoderTellStatus file_tell_callback_(const FLAC__StreamEncoder *encoder, FLAC__uint64 *absolute_byte_offset, void *client_data);
static FLAC__StreamEncoderWriteStatus file_write_callback_(const FLAC__StreamEncoder *encoder, const FLAC__byte buffer[], size_t bytes, uint32_t samples, uint32_t current_frame, void *client_data);
static FILE *get_binary_stdout_(void);


/***********************************************************************
 *
 * Private class data
 *
 ***********************************************************************/

typedef struct FLAC__StreamEncoderPrivate {
	uint32_t input_capacity;                          /* current size (in samples) of the signal and residual buffers */
	FLAC__int32 *integer_signal[FLAC__MAX_CHANNELS];  /* the integer version of the input signal */
	FLAC__int32 *integer_signal_mid_side[2];          /* the integer version of the mid-side input signal (stereo only) */
#ifndef FLAC__INTEGER_ONLY_LIBRARY
	FLAC__real *real_signal[FLAC__MAX_CHANNELS];      /* (@@@ currently unused) the floating-point version of the input signal */
	FLAC__real *real_signal_mid_side[2];              /* (@@@ currently unused) the floating-point version of the mid-side input signal (stereo only) */
	FLAC__real *window[FLAC__MAX_APODIZATION_FUNCTIONS]; /* the pre-computed floating-point window for each apodization function */
	FLAC__real *windowed_signal;                      /* the integer_signal[] * current window[] */
#endif
	uint32_t subframe_bps[FLAC__MAX_CHANNELS];        /* the effective bits per sample of the input signal (stream bps - wasted bits) */
	uint32_t subframe_bps_mid_side[2];                /* the effective bits per sample of the mid-side input signal (stream bps - wasted bits + 0/1) */
	FLAC__int32 *residual_workspace[FLAC__MAX_CHANNELS][2]; /* each channel has a candidate and best workspace where the subframe residual signals will be stored */
	FLAC__int32 *residual_workspace_mid_side[2][2];
	FLAC__Subframe subframe_workspace[FLAC__MAX_CHANNELS][2];
	FLAC__Subframe subframe_workspace_mid_side[2][2];
	FLAC__Subframe *subframe_workspace_ptr[FLAC__MAX_CHANNELS][2];
	FLAC__Subframe *subframe_workspace_ptr_mid_side[2][2];
	FLAC__EntropyCodingMethod_PartitionedRiceContents partitioned_rice_contents_workspace[FLAC__MAX_CHANNELS][2];
	FLAC__EntropyCodingMethod_PartitionedRiceContents partitioned_rice_contents_workspace_mid_side[FLAC__MAX_CHANNELS][2];
	FLAC__EntropyCodingMethod_PartitionedRiceContents *partitioned_rice_contents_workspace_ptr[FLAC__MAX_CHANNELS][2];
	FLAC__EntropyCodingMethod_PartitionedRiceContents *partitioned_rice_contents_workspace_ptr_mid_side[FLAC__MAX_CHANNELS][2];
	uint32_t best_subframe[FLAC__MAX_CHANNELS];       /* index (0 or 1) into 2nd dimension of the above workspaces */
	uint32_t best_subframe_mid_side[2];
	uint32_t best_subframe_bits[FLAC__MAX_CHANNELS];  /* size in bits of the best subframe for each channel */
	uint32_t best_subframe_bits_mid_side[2];
	FLAC__uint64 *abs_residual_partition_sums;        /* workspace where the sum of abs(candidate residual) for each partition is stored */
	uint32_t *raw_bits_per_partition;                 /* workspace where the sum of silog2(candidate residual) for each partition is stored */
	FLAC__BitWriter *frame;                           /* the current frame being worked on */
	uint32_t loose_mid_side_stereo_frames;            /* rounded number of frames the encoder will use before trying both independent and mid/side frames again */
	uint32_t loose_mid_side_stereo_frame_count;       /* number of frames using the current channel assignment */
	FLAC__ChannelAssignment last_channel_assignment;
	FLAC__StreamMetadata streaminfo;                  /* scratchpad for STREAMINFO as it is built */
	FLAC__StreamMetadata_SeekTable *seek_table;       /* pointer into encoder->protected_->metadata_ where the seek table is */
	uint32_t current_sample_number;
	uint32_t current_frame_number;
	FLAC__MD5Context md5context;
	FLAC__CPUInfo cpuinfo;
	void (*local_precompute_partition_info_sums)(const FLAC__int32 residual[], FLAC__uint64 abs_residual_partition_sums[], uint32_t residual_samples, uint32_t predictor_order, uint32_t min_partition_order, uint32_t max_partition_order, uint32_t bps);
#ifndef FLAC__INTEGER_ONLY_LIBRARY
	uint32_t (*local_fixed_compute_best_predictor)(const FLAC__int32 data[], uint32_t data_len, float residual_bits_per_sample[FLAC__MAX_FIXED_ORDER+1]);
	uint32_t (*local_fixed_compute_best_predictor_wide)(const FLAC__int32 data[], uint32_t data_len, float residual_bits_per_sample[FLAC__MAX_FIXED_ORDER+1]);
#else
	uint32_t (*local_fixed_compute_best_predictor)(const FLAC__int32 data[], uint32_t data_len, FLAC__fixedpoint residual_bits_per_sample[FLAC__MAX_FIXED_ORDER+1]);
	uint32_t (*local_fixed_compute_best_predictor_wide)(const FLAC__int32 data[], uint32_t data_len, FLAC__fixedpoint residual_bits_per_sample[FLAC__MAX_FIXED_ORDER+1]);
#endif
#ifndef FLAC__INTEGER_ONLY_LIBRARY
	void (*local_lpc_compute_autocorrelation)(const FLAC__real data[], uint32_t data_len, uint32_t lag, FLAC__real autoc[]);
	void (*local_lpc_compute_residual_from_qlp_coefficients)(const FLAC__int32 *data, uint32_t data_len, const FLAC__int32 qlp_coeff[], uint32_t order, int lp_quantization, FLAC__int32 residual[]);
	void (*local_lpc_compute_residual_from_qlp_coefficients_64bit)(const FLAC__int32 *data, uint32_t data_len, const FLAC__int32 qlp_coeff[], uint32_t order, int lp_quantization, FLAC__int32 residual[]);
	void (*local_lpc_compute_residual_from_qlp_coefficients_16bit)(const FLAC__int32 *data, uint32_t data_len, const FLAC__int32 qlp_coeff[], uint32_t order, int lp_quantization, FLAC__int32 residual[]);
#endif
	FLAC__bool disable_constant_subframes;
	FLAC__bool disable_fixed_subframes;
	FLAC__bool disable_verbatim_subframes;
	FLAC__bool is_ogg;
	FLAC__StreamEncoderReadCallback read_callback; /* currently only needed for Ogg FLAC */
	FLAC__StreamEncoderSeekCallback seek_callback;
	FLAC__StreamEncoderTellCallback tell_callback;
	FLAC__StreamEncoderWriteCallback write_callback;
	FLAC__StreamEncoderMetadataCallback metadata_callback;
	FLAC__StreamEncoderProgressCallback progress_callback;
	void *client_data;
	uint32_t first_seekpoint_to_check;
	FILE *file;                            /* only used when encoding to a file */
	FLAC__uint64 bytes_written;
	FLAC__uint64 samples_written;
	uint32_t frames_written;
	uint32_t total_frames_estimate;
	/* unaligned (original) pointers to allocated data */
	FLAC__int32 *integer_signal_unaligned[FLAC__MAX_CHANNELS];
	FLAC__int32 *integer_signal_mid_side_unaligned[2];
#ifndef FLAC__INTEGER_ONLY_LIBRARY
	FLAC__real *real_signal_unaligned[FLAC__MAX_CHANNELS]; /* (@@@ currently unused) */
	FLAC__real *real_signal_mid_side_unaligned[2]; /* (@@@ currently unused) */
	FLAC__real *window_unaligned[FLAC__MAX_APODIZATION_FUNCTIONS];
	FLAC__real *windowed_signal_unaligned;
#endif
	FLAC__int32 *residual_workspace_unaligned[FLAC__MAX_CHANNELS][2];
	FLAC__int32 *residual_workspace_mid_side_unaligned[2][2];
	FLAC__uint64 *abs_residual_partition_sums_unaligned;
	uint32_t *raw_bits_per_partition_unaligned;
	/*
	 * These fields have been moved here from private function local
	 * declarations merely to save stack space during encoding.
	 */
#ifndef FLAC__INTEGER_ONLY_LIBRARY
	FLAC__real lp_coeff[FLAC__MAX_LPC_ORDER][FLAC__MAX_LPC_ORDER]; /* from process_subframe_() */
#endif
	FLAC__EntropyCodingMethod_PartitionedRiceContents partitioned_rice_contents_extra[2]; /* from find_best_partition_order_() */
	/*
	 * The data for the verify section
	 */
	struct {
		FLAC__StreamDecoder *decoder;
		EncoderStateHint state_hint;
		FLAC__bool needs_magic_hack;
		verify_input_fifo input_fifo;
		verify_output output;
		struct {
			FLAC__uint64 absolute_sample;
			uint32_t frame_number;
			uint32_t channel;
			uint32_t sample;
			FLAC__int32 expected;
			FLAC__int32 got;
		} error_stats;
	} verify;
	FLAC__bool is_being_deleted; /* if true, call to ..._finish() from ..._delete() will not call the callbacks */
} FLAC__StreamEncoderPrivate;

/***********************************************************************
 *
 * Public static class data
 *
 ***********************************************************************/

FLAC_API const char * const FLAC__StreamEncoderStateString[] = {
	"FLAC__STREAM_ENCODER_OK",
	"FLAC__STREAM_ENCODER_UNINITIALIZED",
	"FLAC__STREAM_ENCODER_OGG_ERROR",
	"FLAC__STREAM_ENCODER_VERIFY_DECODER_ERROR",
	"FLAC__STREAM_ENCODER_VERIFY_MISMATCH_IN_AUDIO_DATA",
	"FLAC__STREAM_ENCODER_CLIENT_ERROR",
	"FLAC__STREAM_ENCODER_IO_ERROR",
	"FLAC__STREAM_ENCODER_FRAMING_ERROR",
	"FLAC__STREAM_ENCODER_MEMORY_ALLOCATION_ERROR"
};

FLAC_API const char * const FLAC__StreamEncoderInitStatusString[] = {
	"FLAC__STREAM_ENCODER_INIT_STATUS_OK",
	"FLAC__STREAM_ENCODER_INIT_STATUS_ENCODER_ERROR",
	"FLAC__STREAM_ENCODER_INIT_STATUS_UNSUPPORTED_CONTAINER",
	"FLAC__STREAM_ENCODER_INIT_STATUS_INVALID_CALLBACKS",
	"FLAC__STREAM_ENCODER_INIT_STATUS_INVALID_NUMBER_OF_CHANNELS",
	"FLAC__STREAM_ENCODER_INIT_STATUS_INVALID_BITS_PER_SAMPLE",
	"FLAC__STREAM_ENCODER_INIT_STATUS_INVALID_SAMPLE_RATE",
	"FLAC__STREAM_ENCODER_INIT_STATUS_INVALID_BLOCK_SIZE",
	"FLAC__STREAM_ENCODER_INIT_STATUS_INVALID_MAX_LPC_ORDER",
	"FLAC__STREAM_ENCODER_INIT_STATUS_INVALID_QLP_COEFF_PRECISION",
	"FLAC__STREAM_ENCODER_INIT_STATUS_BLOCK_SIZE_TOO_SMALL_FOR_LPC_ORDER",
	"FLAC__STREAM_ENCODER_INIT_STATUS_NOT_STREAMABLE",
	"FLAC__STREAM_ENCODER_INIT_STATUS_INVALID_METADATA",
	"FLAC__STREAM_ENCODER_INIT_STATUS_ALREADY_INITIALIZED"
};

FLAC_API const char * const FLAC__StreamEncoderReadStatusString[] = {
	"FLAC__STREAM_ENCODER_READ_STATUS_CONTINUE",
	"FLAC__STREAM_ENCODER_READ_STATUS_END_OF_STREAM",
	"FLAC__STREAM_ENCODER_READ_STATUS_ABORT",
	"FLAC__STREAM_ENCODER_READ_STATUS_UNSUPPORTED"
};

FLAC_API const char * const FLAC__StreamEncoderWriteStatusString[] = {
	"FLAC__STREAM_ENCODER_WRITE_STATUS_OK",
	"FLAC__STREAM_ENCODER_WRITE_STATUS_FATAL_ERROR"
};

FLAC_API const char * const FLAC__StreamEncoderSeekStatusString[] = {
	"FLAC__STREAM_ENCODER_SEEK_STATUS_OK",
	"FLAC__STREAM_ENCODER_SEEK_STATUS_ERROR",
	"FLAC__STREAM_ENCODER_SEEK_STATUS_UNSUPPORTED"
};

FLAC_API const char * const FLAC__StreamEncoderTellStatusString[] = {
	"FLAC__STREAM_ENCODER_TELL_STATUS_OK",
	"FLAC__STREAM_ENCODER_TELL_STATUS_ERROR",
	"FLAC__STREAM_ENCODER_TELL_STATUS_UNSUPPORTED"
};

/* Number of samples that will be overread to watch for end of stream.  By
 * 'overread', we mean that the FLAC__stream_encoder_process*() calls will
 * always try to read blocksize+1 samples before encoding a block, so that
 * even if the stream has a total sample count that is an integral multiple
 * of the blocksize, we will still notice when we are encoding the last
 * block.  This is needed, for example, to correctly set the end-of-stream
 * marker in Ogg FLAC.
 *
 * WATCHOUT: some parts of the code assert that OVERREAD_ == 1 and there's
 * not really any reason to change it.
 */
static const uint32_t OVERREAD_ = 1;

/***********************************************************************
 *
 * Class constructor/destructor
 *
 */
FLAC_API FLAC__StreamEncoder *FLAC__stream_encoder_new(void)
{
	FLAC__StreamEncoder *encoder;
	uint32_t i;

	FLAC__ASSERT(sizeof(int) >= 4); /* we want to die right away if this is not true */

	encoder = calloc(1, sizeof(FLAC__StreamEncoder));
	if(encoder == 0) {
		return 0;
	}

	encoder->protected_ = calloc(1, sizeof(FLAC__StreamEncoderProtected));
	if(encoder->protected_ == 0) {
		free(encoder);
		return 0;
	}

	encoder->private_ = calloc(1, sizeof(FLAC__StreamEncoderPrivate));
	if(encoder->private_ == 0) {
		free(encoder->protected_);
		free(encoder);
		return 0;
	}

	encoder->private_->frame = FLAC__bitwriter_new();
	if(encoder->private_->frame == 0) {
		free(encoder->private_);
		free(encoder->protected_);
		free(encoder);
		return 0;
	}

	encoder->private_->file = 0;

	set_defaults_(encoder);

	encoder->private_->is_being_deleted = false;

	for(i = 0; i < FLAC__MAX_CHANNELS; i++) {
		encoder->private_->subframe_workspace_ptr[i][0] = &encoder->private_->subframe_workspace[i][0];
		encoder->private_->subframe_workspace_ptr[i][1] = &encoder->private_->subframe_workspace[i][1];
	}
	for(i = 0; i < 2; i++) {
		encoder->private_->subframe_workspace_ptr_mid_side[i][0] = &encoder->private_->subframe_workspace_mid_side[i][0];
		encoder->private_->subframe_workspace_ptr_mid_side[i][1] = &encoder->private_->subframe_workspace_mid_side[i][1];
	}
	for(i = 0; i < FLAC__MAX_CHANNELS; i++) {
		encoder->private_->partitioned_rice_contents_workspace_ptr[i][0] = &encoder->private_->partitioned_rice_contents_workspace[i][0];
		encoder->private_->partitioned_rice_contents_workspace_ptr[i][1] = &encoder->private_->partitioned_rice_contents_workspace[i][1];
	}
	for(i = 0; i < 2; i++) {
		encoder->private_->partitioned_rice_contents_workspace_ptr_mid_side[i][0] = &encoder->private_->partitioned_rice_contents_workspace_mid_side[i][0];
		encoder->private_->partitioned_rice_contents_workspace_ptr_mid_side[i][1] = &encoder->private_->partitioned_rice_contents_workspace_mid_side[i][1];
	}

	for(i = 0; i < FLAC__MAX_CHANNELS; i++) {
		FLAC__format_entropy_coding_method_partitioned_rice_contents_init(&encoder->private_->partitioned_rice_contents_workspace[i][0]);
		FLAC__format_entropy_coding_method_partitioned_rice_contents_init(&encoder->private_->partitioned_rice_contents_workspace[i][1]);
	}
	for(i = 0; i < 2; i++) {
		FLAC__format_entropy_coding_method_partitioned_rice_contents_init(&encoder->private_->partitioned_rice_contents_workspace_mid_side[i][0]);
		FLAC__format_entropy_coding_method_partitioned_rice_contents_init(&encoder->private_->partitioned_rice_contents_workspace_mid_side[i][1]);
	}
	for(i = 0; i < 2; i++)
		FLAC__format_entropy_coding_method_partitioned_rice_contents_init(&encoder->private_->partitioned_rice_contents_extra[i]);

	encoder->protected_->state = FLAC__STREAM_ENCODER_UNINITIALIZED;

	return encoder;
}

FLAC_API void FLAC__stream_encoder_delete(FLAC__StreamEncoder *encoder)
{
	uint32_t i;

	if (encoder == NULL)
		return ;

	FLAC__ASSERT(0 != encoder->protected_);
	FLAC__ASSERT(0 != encoder->private_);
	FLAC__ASSERT(0 != encoder->private_->frame);

	encoder->private_->is_being_deleted = true;

	(void)FLAC__stream_encoder_finish(encoder);

	if(0 != encoder->private_->verify.decoder)
		FLAC__stream_decoder_delete(encoder->private_->verify.decoder);

	for(i = 0; i < FLAC__MAX_CHANNELS; i++) {
		FLAC__format_entropy_coding_method_partitioned_rice_contents_clear(&encoder->private_->partitioned_rice_contents_workspace[i][0]);
		FLAC__format_entropy_coding_method_partitioned_rice_contents_clear(&encoder->private_->partitioned_rice_contents_workspace[i][1]);
	}
	for(i = 0; i < 2; i++) {
		FLAC__format_entropy_coding_method_partitioned_rice_contents_clear(&encoder->private_->partitioned_rice_contents_workspace_mid_side[i][0]);
		FLAC__format_entropy_coding_method_partitioned_rice_contents_clear(&encoder->private_->partitioned_rice_contents_workspace_mid_side[i][1]);
	}
	for(i = 0; i < 2; i++)
		FLAC__format_entropy_coding_method_partitioned_rice_contents_clear(&encoder->private_->partitioned_rice_contents_extra[i]);

	FLAC__bitwriter_delete(encoder->private_->frame);
	free(encoder->private_);
	free(encoder->protected_);
	free(encoder);
}

/***********************************************************************
 *
 * Public class methods
 *
 ***********************************************************************/

static FLAC__StreamEncoderInitStatus init_stream_internal_(
	FLAC__StreamEncoder *encoder,
	FLAC__StreamEncoderReadCallback read_callback,
	FLAC__StreamEncoderWriteCallback write_callback,
	FLAC__StreamEncoderSeekCallback seek_callback,
	FLAC__StreamEncoderTellCallback tell_callback,
	FLAC__StreamEncoderMetadataCallback metadata_callback,
	void *client_data,
	FLAC__bool is_ogg
)
{
	uint32_t i;
	FLAC__bool metadata_has_seektable, metadata_has_vorbis_comment, metadata_picture_has_type1, metadata_picture_has_type2;

	FLAC__ASSERT(0 != encoder);

	if(encoder->protected_->state != FLAC__STREAM_ENCODER_UNINITIALIZED)
		return FLAC__STREAM_ENCODER_INIT_STATUS_ALREADY_INITIALIZED;

	if(FLAC__HAS_OGG == 0 && is_ogg)
		return FLAC__STREAM_ENCODER_INIT_STATUS_UNSUPPORTED_CONTAINER;

	if(0 == write_callback || (seek_callback && 0 == tell_callback))
		return FLAC__STREAM_ENCODER_INIT_STATUS_INVALID_CALLBACKS;

	if(encoder->protected_->channels == 0 || encoder->protected_->channels > FLAC__MAX_CHANNELS)
		return FLAC__STREAM_ENCODER_INIT_STATUS_INVALID_NUMBER_OF_CHANNELS;

	if(encoder->protected_->channels != 2) {
		encoder->protected_->do_mid_side_stereo = false;
		encoder->protected_->loose_mid_side_stereo = false;
	}
	else if(!encoder->protected_->do_mid_side_stereo)
		encoder->protected_->loose_mid_side_stereo = false;

	if(encoder->protected_->bits_per_sample >= 32)
		encoder->protected_->do_mid_side_stereo = false; /* since we currenty do 32-bit math, the side channel would have 33 bps and overflow */

	if(encoder->protected_->bits_per_sample < FLAC__MIN_BITS_PER_SAMPLE || encoder->protected_->bits_per_sample > FLAC__REFERENCE_CODEC_MAX_BITS_PER_SAMPLE)
		return FLAC__STREAM_ENCODER_INIT_STATUS_INVALID_BITS_PER_SAMPLE;

	if(!FLAC__format_sample_rate_is_valid(encoder->protected_->sample_rate))
		return FLAC__STREAM_ENCODER_INIT_STATUS_INVALID_SAMPLE_RATE;

	if(encoder->protected_->blocksize == 0) {
		if(encoder->protected_->max_lpc_order == 0)
			encoder->protected_->blocksize = 1152;
		else
			encoder->protected_->blocksize = 4096;
	}

	if(encoder->protected_->blocksize < FLAC__MIN_BLOCK_SIZE || encoder->protected_->blocksize > FLAC__MAX_BLOCK_SIZE)
		return FLAC__STREAM_ENCODER_INIT_STATUS_INVALID_BLOCK_SIZE;

	if(encoder->protected_->max_lpc_order > FLAC__MAX_LPC_ORDER)
		return FLAC__STREAM_ENCODER_INIT_STATUS_INVALID_MAX_LPC_ORDER;

	if(encoder->protected_->blocksize < encoder->protected_->max_lpc_order)
		return FLAC__STREAM_ENCODER_INIT_STATUS_BLOCK_SIZE_TOO_SMALL_FOR_LPC_ORDER;

	if(encoder->protected_->qlp_coeff_precision == 0) {
		if(encoder->protected_->bits_per_sample < 16) {
			/* @@@ need some data about how to set this here w.r.t. blocksize and sample rate */
			/* @@@ until then we'll make a guess */
			encoder->protected_->qlp_coeff_precision = flac_max(FLAC__MIN_QLP_COEFF_PRECISION, 2 + encoder->protected_->bits_per_sample / 2);
		}
		else if(encoder->protected_->bits_per_sample == 16) {
			if(encoder->protected_->blocksize <= 192)
				encoder->protected_->qlp_coeff_precision = 7;
			else if(encoder->protected_->blocksize <= 384)
				encoder->protected_->qlp_coeff_precision = 8;
			else if(encoder->protected_->blocksize <= 576)
				encoder->protected_->qlp_coeff_precision = 9;
			else if(encoder->protected_->blocksize <= 1152)
				encoder->protected_->qlp_coeff_precision = 10;
			else if(encoder->protected_->blocksize <= 2304)
				encoder->protected_->qlp_coeff_precision = 11;
			else if(encoder->protected_->blocksize <= 4608)
				encoder->protected_->qlp_coeff_precision = 12;
			else
				encoder->protected_->qlp_coeff_precision = 13;
		}
		else {
			if(encoder->protected_->blocksize <= 384)
				encoder->protected_->qlp_coeff_precision = FLAC__MAX_QLP_COEFF_PRECISION-2;
			else if(encoder->protected_->blocksize <= 1152)
				encoder->protected_->qlp_coeff_precision = FLAC__MAX_QLP_COEFF_PRECISION-1;
			else
				encoder->protected_->qlp_coeff_precision = FLAC__MAX_QLP_COEFF_PRECISION;
		}
		FLAC__ASSERT(encoder->protected_->qlp_coeff_precision <= FLAC__MAX_QLP_COEFF_PRECISION);
	}
	else if(encoder->protected_->qlp_coeff_precision < FLAC__MIN_QLP_COEFF_PRECISION || encoder->protected_->qlp_coeff_precision > FLAC__MAX_QLP_COEFF_PRECISION)
		return FLAC__STREAM_ENCODER_INIT_STATUS_INVALID_QLP_COEFF_PRECISION;

	if(encoder->protected_->streamable_subset) {
		if(!FLAC__format_blocksize_is_subset(encoder->protected_->blocksize, encoder->protected_->sample_rate))
			return FLAC__STREAM_ENCODER_INIT_STATUS_NOT_STREAMABLE;
		if(!FLAC__format_sample_rate_is_subset(encoder->protected_->sample_rate))
			return FLAC__STREAM_ENCODER_INIT_STATUS_NOT_STREAMABLE;
		if(
			encoder->protected_->bits_per_sample != 8 &&
			encoder->protected_->bits_per_sample != 12 &&
			encoder->protected_->bits_per_sample != 16 &&
			encoder->protected_->bits_per_sample != 20 &&
			encoder->protected_->bits_per_sample != 24
		)
			return FLAC__STREAM_ENCODER_INIT_STATUS_NOT_STREAMABLE;
		if(encoder->protected_->max_residual_partition_order > FLAC__SUBSET_MAX_RICE_PARTITION_ORDER)
			return FLAC__STREAM_ENCODER_INIT_STATUS_NOT_STREAMABLE;
		if(
			encoder->protected_->sample_rate <= 48000 &&
			(
				encoder->protected_->blocksize > FLAC__SUBSET_MAX_BLOCK_SIZE_48000HZ ||
				encoder->protected_->max_lpc_order > FLAC__SUBSET_MAX_LPC_ORDER_48000HZ
			)
		) {
			return FLAC__STREAM_ENCODER_INIT_STATUS_NOT_STREAMABLE;
		}
	}

	if(encoder->protected_->max_residual_partition_order >= (1u << FLAC__ENTROPY_CODING_METHOD_PARTITIONED_RICE_ORDER_LEN))
		encoder->protected_->max_residual_partition_order = (1u << FLAC__ENTROPY_CODING_METHOD_PARTITIONED_RICE_ORDER_LEN) - 1;
	if(encoder->protected_->min_residual_partition_order >= encoder->protected_->max_residual_partition_order)
		encoder->protected_->min_residual_partition_order = encoder->protected_->max_residual_partition_order;

#if FLAC__HAS_OGG
	/* reorder metadata if necessary to ensure that any VORBIS_COMMENT is the first, according to the mapping spec */
	if(is_ogg && 0 != encoder->protected_->metadata && encoder->protected_->num_metadata_blocks > 1) {
		uint32_t i1;
		for(i1 = 1; i1 < encoder->protected_->num_metadata_blocks; i1++) {
			if(0 != encoder->protected_->metadata[i1] && encoder->protected_->metadata[i1]->type == FLAC__METADATA_TYPE_VORBIS_COMMENT) {
				FLAC__StreamMetadata *vc = encoder->protected_->metadata[i1];
				for( ; i1 > 0; i1--)
					encoder->protected_->metadata[i1] = encoder->protected_->metadata[i1-1];
				encoder->protected_->metadata[0] = vc;
				break;
			}
		}
	}
#endif
	/* keep track of any SEEKTABLE block */
	if(0 != encoder->protected_->metadata && encoder->protected_->num_metadata_blocks > 0) {
		uint32_t i2;
		for(i2 = 0; i2 < encoder->protected_->num_metadata_blocks; i2++) {
			if(0 != encoder->protected_->metadata[i2] && encoder->protected_->metadata[i2]->type == FLAC__METADATA_TYPE_SEEKTABLE) {
				encoder->private_->seek_table = &encoder->protected_->metadata[i2]->data.seek_table;
				break; /* take only the first one */
			}
		}
	}

	/* validate metadata */
	if(0 == encoder->protected_->metadata && encoder->protected_->num_metadata_blocks > 0)
		return FLAC__STREAM_ENCODER_INIT_STATUS_INVALID_METADATA;
	metadata_has_seektable = false;
	metadata_has_vorbis_comment = false;
	metadata_picture_has_type1 = false;
	metadata_picture_has_type2 = false;
	for(i = 0; i < encoder->protected_->num_metadata_blocks; i++) {
		const FLAC__StreamMetadata *m = encoder->protected_->metadata[i];
		if(m->type == FLAC__METADATA_TYPE_STREAMINFO)
			return FLAC__STREAM_ENCODER_INIT_STATUS_INVALID_METADATA;
		else if(m->type == FLAC__METADATA_TYPE_SEEKTABLE) {
			if(metadata_has_seektable) /* only one is allowed */
				return FLAC__STREAM_ENCODER_INIT_STATUS_INVALID_METADATA;
			metadata_has_seektable = true;
			if(!FLAC__format_seektable_is_legal(&m->data.seek_table))
				return FLAC__STREAM_ENCODER_INIT_STATUS_INVALID_METADATA;
		}
		else if(m->type == FLAC__METADATA_TYPE_VORBIS_COMMENT) {
			if(metadata_has_vorbis_comment) /* only one is allowed */
				return FLAC__STREAM_ENCODER_INIT_STATUS_INVALID_METADATA;
			metadata_has_vorbis_comment = true;
		}
		else if(m->type == FLAC__METADATA_TYPE_CUESHEET) {
			if(!FLAC__format_cuesheet_is_legal(&m->data.cue_sheet, m->data.cue_sheet.is_cd, /*violation=*/0))
				return FLAC__STREAM_ENCODER_INIT_STATUS_INVALID_METADATA;
		}
		else if(m->type == FLAC__METADATA_TYPE_PICTURE) {
			if(!FLAC__format_picture_is_legal(&m->data.picture, /*violation=*/0))
				return FLAC__STREAM_ENCODER_INIT_STATUS_INVALID_METADATA;
			if(m->data.picture.type == FLAC__STREAM_METADATA_PICTURE_TYPE_FILE_ICON_STANDARD) {
				if(metadata_picture_has_type1) /* there should only be 1 per stream */
					return FLAC__STREAM_ENCODER_INIT_STATUS_INVALID_METADATA;
				metadata_picture_has_type1 = true;
				/* standard icon must be 32x32 pixel PNG */
				if(
					m->data.picture.type == FLAC__STREAM_METADATA_PICTURE_TYPE_FILE_ICON_STANDARD &&
					(
						(strcmp(m->data.picture.mime_type, "image/png") && strcmp(m->data.picture.mime_type, "-->")) ||
						m->data.picture.width != 32 ||
						m->data.picture.height != 32
					)
				)
					return FLAC__STREAM_ENCODER_INIT_STATUS_INVALID_METADATA;
			}
			else if(m->data.picture.type == FLAC__STREAM_METADATA_PICTURE_TYPE_FILE_ICON) {
				if(metadata_picture_has_type2) /* there should only be 1 per stream */
					return FLAC__STREAM_ENCODER_INIT_STATUS_INVALID_METADATA;
				metadata_picture_has_type2 = true;
			}
		}
	}

	encoder->private_->input_capacity = 0;
	for(i = 0; i < encoder->protected_->channels; i++) {
		encoder->private_->integer_signal_unaligned[i] = encoder->private_->integer_signal[i] = 0;
#ifndef FLAC__INTEGER_ONLY_LIBRARY
		encoder->private_->real_signal_unaligned[i] = encoder->private_->real_signal[i] = 0;
#endif
	}
	for(i = 0; i < 2; i++) {
		encoder->private_->integer_signal_mid_side_unaligned[i] = encoder->private_->integer_signal_mid_side[i] = 0;
#ifndef FLAC__INTEGER_ONLY_LIBRARY
		encoder->private_->real_signal_mid_side_unaligned[i] = encoder->private_->real_signal_mid_side[i] = 0;
#endif
	}
#ifndef FLAC__INTEGER_ONLY_LIBRARY
	for(i = 0; i < encoder->protected_->num_apodizations; i++)
		encoder->private_->window_unaligned[i] = encoder->private_->window[i] = 0;
	encoder->private_->windowed_signal_unaligned = encoder->private_->windowed_signal = 0;
#endif
	for(i = 0; i < encoder->protected_->channels; i++) {
		encoder->private_->residual_workspace_unaligned[i][0] = encoder->private_->residual_workspace[i][0] = 0;
		encoder->private_->residual_workspace_unaligned[i][1] = encoder->private_->residual_workspace[i][1] = 0;
		encoder->private_->best_subframe[i] = 0;
	}
	for(i = 0; i < 2; i++) {
		encoder->private_->residual_workspace_mid_side_unaligned[i][0] = encoder->private_->residual_workspace_mid_side[i][0] = 0;
		encoder->private_->residual_workspace_mid_side_unaligned[i][1] = encoder->private_->residual_workspace_mid_side[i][1] = 0;
		encoder->private_->best_subframe_mid_side[i] = 0;
	}
	encoder->private_->abs_residual_partition_sums_unaligned = encoder->private_->abs_residual_partition_sums = 0;
	encoder->private_->raw_bits_per_partition_unaligned = encoder->private_->raw_bits_per_partition = 0;
#ifndef FLAC__INTEGER_ONLY_LIBRARY
	encoder->private_->loose_mid_side_stereo_frames = (uint32_t)((double)encoder->protected_->sample_rate * 0.4 / (double)encoder->protected_->blocksize + 0.5);
#else
	/* 26214 is the approximate fixed-point equivalent to 0.4 (0.4 * 2^16) */
	/* sample rate can be up to 655350 Hz, and thus use 20 bits, so we do the multiply&divide by hand */
	FLAC__ASSERT(FLAC__MAX_SAMPLE_RATE <= 655350);
	FLAC__ASSERT(FLAC__MAX_BLOCK_SIZE <= 65535);
	FLAC__ASSERT(encoder->protected_->sample_rate <= 655350);
	FLAC__ASSERT(encoder->protected_->blocksize <= 65535);
	encoder->private_->loose_mid_side_stereo_frames = (uint32_t)FLAC__fixedpoint_trunc((((FLAC__uint64)(encoder->protected_->sample_rate) * (FLAC__uint64)(26214)) << 16) / (encoder->protected_->blocksize<<16) + FLAC__FP_ONE_HALF);
#endif
	if(encoder->private_->loose_mid_side_stereo_frames == 0)
		encoder->private_->loose_mid_side_stereo_frames = 1;
	encoder->private_->loose_mid_side_stereo_frame_count = 0;
	encoder->private_->current_sample_number = 0;
	encoder->private_->current_frame_number = 0;

	/*
	 * get the CPU info and set the function pointers
	 */
	FLAC__cpu_info(&encoder->private_->cpuinfo);
	/* first default to the non-asm routines */
#ifndef FLAC__INTEGER_ONLY_LIBRARY
	encoder->private_->local_lpc_compute_autocorrelation = FLAC__lpc_compute_autocorrelation;
#endif
	encoder->private_->local_precompute_partition_info_sums = precompute_partition_info_sums_;
	encoder->private_->local_fixed_compute_best_predictor = FLAC__fixed_compute_best_predictor;
	encoder->private_->local_fixed_compute_best_predictor_wide = FLAC__fixed_compute_best_predictor_wide;
#ifndef FLAC__INTEGER_ONLY_LIBRARY
	encoder->private_->local_lpc_compute_residual_from_qlp_coefficients = FLAC__lpc_compute_residual_from_qlp_coefficients;
	encoder->private_->local_lpc_compute_residual_from_qlp_coefficients_64bit = FLAC__lpc_compute_residual_from_qlp_coefficients_wide;
	encoder->private_->local_lpc_compute_residual_from_qlp_coefficients_16bit = FLAC__lpc_compute_residual_from_qlp_coefficients;
#endif
	/* now override with asm where appropriate */
#ifndef FLAC__INTEGER_ONLY_LIBRARY
# ifndef FLAC__NO_ASM
	if(encoder->private_->cpuinfo.use_asm) {
#  ifdef FLAC__CPU_IA32
		FLAC__ASSERT(encoder->private_->cpuinfo.type == FLAC__CPUINFO_TYPE_IA32);
#   ifdef FLAC__HAS_NASM
		if (encoder->private_->cpuinfo.x86.sse) {
			if(encoder->protected_->max_lpc_order < 4)
				encoder->private_->local_lpc_compute_autocorrelation = FLAC__lpc_compute_autocorrelation_asm_ia32_sse_lag_4_old;
			else if(encoder->protected_->max_lpc_order < 8)
				encoder->private_->local_lpc_compute_autocorrelation = FLAC__lpc_compute_autocorrelation_asm_ia32_sse_lag_8_old;
			else if(encoder->protected_->max_lpc_order < 12)
				encoder->private_->local_lpc_compute_autocorrelation = FLAC__lpc_compute_autocorrelation_asm_ia32_sse_lag_12_old;
			else if(encoder->protected_->max_lpc_order < 16)
				encoder->private_->local_lpc_compute_autocorrelation = FLAC__lpc_compute_autocorrelation_asm_ia32_sse_lag_16_old;
			else
				encoder->private_->local_lpc_compute_autocorrelation = FLAC__lpc_compute_autocorrelation_asm_ia32;
		}
		else
			encoder->private_->local_lpc_compute_autocorrelation = FLAC__lpc_compute_autocorrelation_asm_ia32;

		encoder->private_->local_lpc_compute_residual_from_qlp_coefficients_64bit = FLAC__lpc_compute_residual_from_qlp_coefficients_wide_asm_ia32; /* OPT_IA32: was really necessary for GCC < 4.9 */
		if (encoder->private_->cpuinfo.x86.mmx) {
			encoder->private_->local_lpc_compute_residual_from_qlp_coefficients = FLAC__lpc_compute_residual_from_qlp_coefficients_asm_ia32;
			encoder->private_->local_lpc_compute_residual_from_qlp_coefficients_16bit = FLAC__lpc_compute_residual_from_qlp_coefficients_asm_ia32_mmx;
		}
		else {
			encoder->private_->local_lpc_compute_residual_from_qlp_coefficients = FLAC__lpc_compute_residual_from_qlp_coefficients_asm_ia32;
			encoder->private_->local_lpc_compute_residual_from_qlp_coefficients_16bit = FLAC__lpc_compute_residual_from_qlp_coefficients_asm_ia32;
		}

		if (encoder->private_->cpuinfo.x86.mmx && encoder->private_->cpuinfo.x86.cmov)
			encoder->private_->local_fixed_compute_best_predictor = FLAC__fixed_compute_best_predictor_asm_ia32_mmx_cmov;
#   endif /* FLAC__HAS_NASM */
#   if FLAC__HAS_X86INTRIN
#    if defined FLAC__SSE_SUPPORTED
		if (encoder->private_->cpuinfo.x86.sse) {
			if (encoder->private_->cpuinfo.x86.sse42 || !encoder->private_->cpuinfo.x86.intel) { /* use new autocorrelation functions */
				if(encoder->protected_->max_lpc_order < 4)
					encoder->private_->local_lpc_compute_autocorrelation = FLAC__lpc_compute_autocorrelation_intrin_sse_lag_4_new;
				else if(encoder->protected_->max_lpc_order < 8)
					encoder->private_->local_lpc_compute_autocorrelation = FLAC__lpc_compute_autocorrelation_intrin_sse_lag_8_new;
				else if(encoder->protected_->max_lpc_order < 12)
					encoder->private_->local_lpc_compute_autocorrelation = FLAC__lpc_compute_autocorrelation_intrin_sse_lag_12_new;
				else if(encoder->protected_->max_lpc_order < 16)
					encoder->private_->local_lpc_compute_autocorrelation = FLAC__lpc_compute_autocorrelation_intrin_sse_lag_16_new;
				else
					encoder->private_->local_lpc_compute_autocorrelation = FLAC__lpc_compute_autocorrelation;
			}
			else { /* use old autocorrelation functions */
				if(encoder->protected_->max_lpc_order < 4)
					encoder->private_->local_lpc_compute_autocorrelation = FLAC__lpc_compute_autocorrelation_intrin_sse_lag_4_old;
				else if(encoder->protected_->max_lpc_order < 8)
					encoder->private_->local_lpc_compute_autocorrelation = FLAC__lpc_compute_autocorrelation_intrin_sse_lag_8_old;
				else if(encoder->protected_->max_lpc_order < 12)
					encoder->private_->local_lpc_compute_autocorrelation = FLAC__lpc_compute_autocorrelation_intrin_sse_lag_12_old;
				else if(encoder->protected_->max_lpc_order < 16)
					encoder->private_->local_lpc_compute_autocorrelation = FLAC__lpc_compute_autocorrelation_intrin_sse_lag_16_old;
				else
					encoder->private_->local_lpc_compute_autocorrelation = FLAC__lpc_compute_autocorrelation;
			}
		}
#    endif

#    ifdef FLAC__SSE2_SUPPORTED
		if (encoder->private_->cpuinfo.x86.sse2) {
			encoder->private_->local_lpc_compute_residual_from_qlp_coefficients       = FLAC__lpc_compute_residual_from_qlp_coefficients_intrin_sse2;
			encoder->private_->local_lpc_compute_residual_from_qlp_coefficients_16bit = FLAC__lpc_compute_residual_from_qlp_coefficients_16_intrin_sse2;
		}
#    endif
#    ifdef FLAC__SSE4_1_SUPPORTED
		if (encoder->private_->cpuinfo.x86.sse41) {
			encoder->private_->local_lpc_compute_residual_from_qlp_coefficients       = FLAC__lpc_compute_residual_from_qlp_coefficients_intrin_sse41;
			encoder->private_->local_lpc_compute_residual_from_qlp_coefficients_64bit = FLAC__lpc_compute_residual_from_qlp_coefficients_wide_intrin_sse41;
		}
#    endif
#    ifdef FLAC__AVX2_SUPPORTED
		if (encoder->private_->cpuinfo.x86.avx2) {
			encoder->private_->local_lpc_compute_residual_from_qlp_coefficients_16bit = FLAC__lpc_compute_residual_from_qlp_coefficients_16_intrin_avx2;
			encoder->private_->local_lpc_compute_residual_from_qlp_coefficients       = FLAC__lpc_compute_residual_from_qlp_coefficients_intrin_avx2;
			encoder->private_->local_lpc_compute_residual_from_qlp_coefficients_64bit = FLAC__lpc_compute_residual_from_qlp_coefficients_wide_intrin_avx2;
		}
#    endif

#    ifdef FLAC__SSE2_SUPPORTED
		if (encoder->private_->cpuinfo.x86.sse2) {
			encoder->private_->local_fixed_compute_best_predictor      = FLAC__fixed_compute_best_predictor_intrin_sse2;
			encoder->private_->local_fixed_compute_best_predictor_wide = FLAC__fixed_compute_best_predictor_wide_intrin_sse2;
		}
#    endif
#    ifdef FLAC__SSSE3_SUPPORTED
		if (encoder->private_->cpuinfo.x86.ssse3) {
			encoder->private_->local_fixed_compute_best_predictor      = FLAC__fixed_compute_best_predictor_intrin_ssse3;
			encoder->private_->local_fixed_compute_best_predictor_wide = FLAC__fixed_compute_best_predictor_wide_intrin_ssse3;
		}
#    endif
#   endif /* FLAC__HAS_X86INTRIN */
#  elif defined FLAC__CPU_X86_64
		FLAC__ASSERT(encoder->private_->cpuinfo.type == FLAC__CPUINFO_TYPE_X86_64);
#   if FLAC__HAS_X86INTRIN
#    ifdef FLAC__SSE_SUPPORTED
		if(encoder->private_->cpuinfo.x86.sse42 || !encoder->private_->cpuinfo.x86.intel) { /* use new autocorrelation functions */
			if(encoder->protected_->max_lpc_order < 4)
				encoder->private_->local_lpc_compute_autocorrelation = FLAC__lpc_compute_autocorrelation_intrin_sse_lag_4_new;
			else if(encoder->protected_->max_lpc_order < 8)
				encoder->private_->local_lpc_compute_autocorrelation = FLAC__lpc_compute_autocorrelation_intrin_sse_lag_8_new;
			else if(encoder->protected_->max_lpc_order < 12)
				encoder->private_->local_lpc_compute_autocorrelation = FLAC__lpc_compute_autocorrelation_intrin_sse_lag_12_new;
			else if(encoder->protected_->max_lpc_order < 16)
				encoder->private_->local_lpc_compute_autocorrelation = FLAC__lpc_compute_autocorrelation_intrin_sse_lag_16_new;
		}
		else {
			if(encoder->protected_->max_lpc_order < 4)
				encoder->private_->local_lpc_compute_autocorrelation = FLAC__lpc_compute_autocorrelation_intrin_sse_lag_4_old;
			else if(encoder->protected_->max_lpc_order < 8)
				encoder->private_->local_lpc_compute_autocorrelation = FLAC__lpc_compute_autocorrelation_intrin_sse_lag_8_old;
			else if(encoder->protected_->max_lpc_order < 12)
				encoder->private_->local_lpc_compute_autocorrelation = FLAC__lpc_compute_autocorrelation_intrin_sse_lag_12_old;
			else if(encoder->protected_->max_lpc_order < 16)
				encoder->private_->local_lpc_compute_autocorrelation = FLAC__lpc_compute_autocorrelation_intrin_sse_lag_16_old;
		}
#    endif

#    ifdef FLAC__SSE2_SUPPORTED
		encoder->private_->local_lpc_compute_residual_from_qlp_coefficients_16bit = FLAC__lpc_compute_residual_from_qlp_coefficients_16_intrin_sse2;
#    endif
#    ifdef FLAC__SSE4_1_SUPPORTED
		if(encoder->private_->cpuinfo.x86.sse41) {
			encoder->private_->local_lpc_compute_residual_from_qlp_coefficients = FLAC__lpc_compute_residual_from_qlp_coefficients_intrin_sse41;
		}
#    endif
#    ifdef FLAC__AVX2_SUPPORTED
		if(encoder->private_->cpuinfo.x86.avx2) {
			encoder->private_->local_lpc_compute_residual_from_qlp_coefficients_16bit = FLAC__lpc_compute_residual_from_qlp_coefficients_16_intrin_avx2;
			encoder->private_->local_lpc_compute_residual_from_qlp_coefficients       = FLAC__lpc_compute_residual_from_qlp_coefficients_intrin_avx2;
			encoder->private_->local_lpc_compute_residual_from_qlp_coefficients_64bit = FLAC__lpc_compute_residual_from_qlp_coefficients_wide_intrin_avx2;
		}
#    endif

#    ifdef FLAC__SSE2_SUPPORTED
		encoder->private_->local_fixed_compute_best_predictor      = FLAC__fixed_compute_best_predictor_intrin_sse2;
		encoder->private_->local_fixed_compute_best_predictor_wide = FLAC__fixed_compute_best_predictor_wide_intrin_sse2;
#    endif
#    ifdef FLAC__SSSE3_SUPPORTED
		if (encoder->private_->cpuinfo.x86.ssse3) {
			encoder->private_->local_fixed_compute_best_predictor      = FLAC__fixed_compute_best_predictor_intrin_ssse3;
			encoder->private_->local_fixed_compute_best_predictor_wide = FLAC__fixed_compute_best_predictor_wide_intrin_ssse3;
		}
#    endif
#   endif /* FLAC__HAS_X86INTRIN */
#  endif /* FLAC__CPU_... */
	}
# endif /* !FLAC__NO_ASM */
#endif /* !FLAC__INTEGER_ONLY_LIBRARY */
#if !defined FLAC__NO_ASM && FLAC__HAS_X86INTRIN
	if(encoder->private_->cpuinfo.use_asm) {
# if defined FLAC__CPU_IA32
#  ifdef FLAC__SSE2_SUPPORTED
		if (encoder->private_->cpuinfo.x86.sse2)
			encoder->private_->local_precompute_partition_info_sums = FLAC__precompute_partition_info_sums_intrin_sse2;
#  endif
#  ifdef FLAC__SSSE3_SUPPORTED
		if (encoder->private_->cpuinfo.x86.ssse3)
			encoder->private_->local_precompute_partition_info_sums = FLAC__precompute_partition_info_sums_intrin_ssse3;
#  endif
#  ifdef FLAC__AVX2_SUPPORTED
		if (encoder->private_->cpuinfo.x86.avx2)
			encoder->private_->local_precompute_partition_info_sums = FLAC__precompute_partition_info_sums_intrin_avx2;
#  endif
# elif defined FLAC__CPU_X86_64
#  ifdef FLAC__SSE2_SUPPORTED
		encoder->private_->local_precompute_partition_info_sums = FLAC__precompute_partition_info_sums_intrin_sse2;
#  endif
#  ifdef FLAC__SSSE3_SUPPORTED
		if(encoder->private_->cpuinfo.x86.ssse3)
			encoder->private_->local_precompute_partition_info_sums = FLAC__precompute_partition_info_sums_intrin_ssse3;
#  endif
#  ifdef FLAC__AVX2_SUPPORTED
		if(encoder->private_->cpuinfo.x86.avx2)
			encoder->private_->local_precompute_partition_info_sums = FLAC__precompute_partition_info_sums_intrin_avx2;
#  endif
# endif /* FLAC__CPU_... */
	}
#endif /* !FLAC__NO_ASM && FLAC__HAS_X86INTRIN */

	/* set state to OK; from here on, errors are fatal and we'll override the state then */
	encoder->protected_->state = FLAC__STREAM_ENCODER_OK;

#if FLAC__HAS_OGG
	encoder->private_->is_ogg = is_ogg;
	if(is_ogg && !FLAC__ogg_encoder_aspect_init(&encoder->protected_->ogg_encoder_aspect)) {
		encoder->protected_->state = FLAC__STREAM_ENCODER_OGG_ERROR;
		return FLAC__STREAM_ENCODER_INIT_STATUS_ENCODER_ERROR;
	}
#endif

	encoder->private_->read_callback = read_callback;
	encoder->private_->write_callback = write_callback;
	encoder->private_->seek_callback = seek_callback;
	encoder->private_->tell_callback = tell_callback;
	encoder->private_->metadata_callback = metadata_callback;
	encoder->private_->client_data = client_data;

	if(!resize_buffers_(encoder, encoder->protected_->blocksize)) {
		/* the above function sets the state for us in case of an error */
		return FLAC__STREAM_ENCODER_INIT_STATUS_ENCODER_ERROR;
	}

	if(!FLAC__bitwriter_init(encoder->private_->frame)) {
		encoder->protected_->state = FLAC__STREAM_ENCODER_MEMORY_ALLOCATION_ERROR;
		return FLAC__STREAM_ENCODER_INIT_STATUS_ENCODER_ERROR;
	}

	/*
	 * Set up the verify stuff if necessary
	 */
	if(encoder->protected_->verify) {
		/*
		 * First, set up the fifo which will hold the
		 * original signal to compare against
		 */
		encoder->private_->verify.input_fifo.size = encoder->protected_->blocksize+OVERREAD_;
		for(i = 0; i < encoder->protected_->channels; i++) {
			if(0 == (encoder->private_->verify.input_fifo.data[i] = safe_malloc_mul_2op_p(sizeof(FLAC__int32), /*times*/encoder->private_->verify.input_fifo.size))) {
				encoder->protected_->state = FLAC__STREAM_ENCODER_MEMORY_ALLOCATION_ERROR;
				return FLAC__STREAM_ENCODER_INIT_STATUS_ENCODER_ERROR;
			}
		}
		encoder->private_->verify.input_fifo.tail = 0;

		/*
		 * Now set up a stream decoder for verification
		 */
		if(0 == encoder->private_->verify.decoder) {
			encoder->private_->verify.decoder = FLAC__stream_decoder_new();
			if(0 == encoder->private_->verify.decoder) {
				encoder->protected_->state = FLAC__STREAM_ENCODER_VERIFY_DECODER_ERROR;
				return FLAC__STREAM_ENCODER_INIT_STATUS_ENCODER_ERROR;
			}
		}

		if(FLAC__stream_decoder_init_stream(encoder->private_->verify.decoder, verify_read_callback_, /*seek_callback=*/0, /*tell_callback=*/0, /*length_callback=*/0, /*eof_callback=*/0, verify_write_callback_, verify_metadata_callback_, verify_error_callback_, /*client_data=*/encoder) != FLAC__STREAM_DECODER_INIT_STATUS_OK) {
			encoder->protected_->state = FLAC__STREAM_ENCODER_VERIFY_DECODER_ERROR;
			return FLAC__STREAM_ENCODER_INIT_STATUS_ENCODER_ERROR;
		}
	}
	encoder->private_->verify.error_stats.absolute_sample = 0;
	encoder->private_->verify.error_stats.frame_number = 0;
	encoder->private_->verify.error_stats.channel = 0;
	encoder->private_->verify.error_stats.sample = 0;
	encoder->private_->verify.error_stats.expected = 0;
	encoder->private_->verify.error_stats.got = 0;

	/*
	 * These must be done before we write any metadata, because that
	 * calls the write_callback, which uses these values.
	 */
	encoder->private_->first_seekpoint_to_check = 0;
	encoder->private_->samples_written = 0;
	encoder->protected_->streaminfo_offset = 0;
	encoder->protected_->seektable_offset = 0;
	encoder->protected_->audio_offset = 0;

	/*
	 * write the stream header
	 */
	if(encoder->protected_->verify)
		encoder->private_->verify.state_hint = ENCODER_IN_MAGIC;
	if(!FLAC__bitwriter_write_raw_uint32(encoder->private_->frame, FLAC__STREAM_SYNC, FLAC__STREAM_SYNC_LEN)) {
		encoder->protected_->state = FLAC__STREAM_ENCODER_FRAMING_ERROR;
		return FLAC__STREAM_ENCODER_INIT_STATUS_ENCODER_ERROR;
	}
	if(!write_bitbuffer_(encoder, 0, /*is_last_block=*/false)) {
		/* the above function sets the state for us in case of an error */
		return FLAC__STREAM_ENCODER_INIT_STATUS_ENCODER_ERROR;
	}

	/*
	 * write the STREAMINFO metadata block
	 */
	if(encoder->protected_->verify)
		encoder->private_->verify.state_hint = ENCODER_IN_METADATA;
	encoder->private_->streaminfo.type = FLAC__METADATA_TYPE_STREAMINFO;
	encoder->private_->streaminfo.is_last = false; /* we will have at a minimum a VORBIS_COMMENT afterwards */
	encoder->private_->streaminfo.length = FLAC__STREAM_METADATA_STREAMINFO_LENGTH;
	encoder->private_->streaminfo.data.stream_info.min_blocksize = encoder->protected_->blocksize; /* this encoder uses the same blocksize for the whole stream */
	encoder->private_->streaminfo.data.stream_info.max_blocksize = encoder->protected_->blocksize;
	encoder->private_->streaminfo.data.stream_info.min_framesize = 0; /* we don't know this yet; have to fill it in later */
	encoder->private_->streaminfo.data.stream_info.max_framesize = 0; /* we don't know this yet; have to fill it in later */
	encoder->private_->streaminfo.data.stream_info.sample_rate = encoder->protected_->sample_rate;
	encoder->private_->streaminfo.data.stream_info.channels = encoder->protected_->channels;
	encoder->private_->streaminfo.data.stream_info.bits_per_sample = encoder->protected_->bits_per_sample;
	encoder->private_->streaminfo.data.stream_info.total_samples = encoder->protected_->total_samples_estimate; /* we will replace this later with the real total */
	memset(encoder->private_->streaminfo.data.stream_info.md5sum, 0, 16); /* we don't know this yet; have to fill it in later */
	if(encoder->protected_->do_md5)
		FLAC__MD5Init(&encoder->private_->md5context);
	if(!FLAC__add_metadata_block(&encoder->private_->streaminfo, encoder->private_->frame)) {
		encoder->protected_->state = FLAC__STREAM_ENCODER_FRAMING_ERROR;
		return FLAC__STREAM_ENCODER_INIT_STATUS_ENCODER_ERROR;
	}
	if(!write_bitbuffer_(encoder, 0, /*is_last_block=*/false)) {
		/* the above function sets the state for us in case of an error */
		return FLAC__STREAM_ENCODER_INIT_STATUS_ENCODER_ERROR;
	}

	/*
	 * Now that the STREAMINFO block is written, we can init this to an
	 * absurdly-high value...
	 */
	encoder->private_->streaminfo.data.stream_info.min_framesize = (1u << FLAC__STREAM_METADATA_STREAMINFO_MIN_FRAME_SIZE_LEN) - 1;
	/* ... and clear this to 0 */
	encoder->private_->streaminfo.data.stream_info.total_samples = 0;

	/*
	 * Check to see if the supplied metadata contains a VORBIS_COMMENT;
	 * if not, we will write an empty one (FLAC__add_metadata_block()
	 * automatically supplies the vendor string).
	 *
	 * WATCHOUT: the Ogg FLAC mapping requires us to write this block after
	 * the STREAMINFO.  (In the case that metadata_has_vorbis_comment is
	 * true it will have already insured that the metadata list is properly
	 * ordered.)
	 */
	if(!metadata_has_vorbis_comment) {
		FLAC__StreamMetadata vorbis_comment;
		vorbis_comment.type = FLAC__METADATA_TYPE_VORBIS_COMMENT;
		vorbis_comment.is_last = (encoder->protected_->num_metadata_blocks == 0);
		vorbis_comment.length = 4 + 4; /* MAGIC NUMBER */
		vorbis_comment.data.vorbis_comment.vendor_string.length = 0;
		vorbis_comment.data.vorbis_comment.vendor_string.entry = 0;
		vorbis_comment.data.vorbis_comment.num_comments = 0;
		vorbis_comment.data.vorbis_comment.comments = 0;
		if(!FLAC__add_metadata_block(&vorbis_comment, encoder->private_->frame)) {
			encoder->protected_->state = FLAC__STREAM_ENCODER_FRAMING_ERROR;
			return FLAC__STREAM_ENCODER_INIT_STATUS_ENCODER_ERROR;
		}
		if(!write_bitbuffer_(encoder, 0, /*is_last_block=*/false)) {
			/* the above function sets the state for us in case of an error */
			return FLAC__STREAM_ENCODER_INIT_STATUS_ENCODER_ERROR;
		}
	}

	/*
	 * write the user's metadata blocks
	 */
	for(i = 0; i < encoder->protected_->num_metadata_blocks; i++) {
		encoder->protected_->metadata[i]->is_last = (i == encoder->protected_->num_metadata_blocks - 1);
		if(!FLAC__add_metadata_block(encoder->protected_->metadata[i], encoder->private_->frame)) {
			encoder->protected_->state = FLAC__STREAM_ENCODER_FRAMING_ERROR;
			return FLAC__STREAM_ENCODER_INIT_STATUS_ENCODER_ERROR;
		}
		if(!write_bitbuffer_(encoder, 0, /*is_last_block=*/false)) {
			/* the above function sets the state for us in case of an error */
			return FLAC__STREAM_ENCODER_INIT_STATUS_ENCODER_ERROR;
		}
	}

	/* now that all the metadata is written, we save the stream offset */
	if(encoder->private_->tell_callback && encoder->private_->tell_callback(encoder, &encoder->protected_->audio_offset, encoder->private_->client_data) == FLAC__STREAM_ENCODER_TELL_STATUS_ERROR) { /* FLAC__STREAM_ENCODER_TELL_STATUS_UNSUPPORTED just means we didn't get the offset; no error */
		encoder->protected_->state = FLAC__STREAM_ENCODER_CLIENT_ERROR;
		return FLAC__STREAM_ENCODER_INIT_STATUS_ENCODER_ERROR;
	}

	if(encoder->protected_->verify)
		encoder->private_->verify.state_hint = ENCODER_IN_AUDIO;

	return FLAC__STREAM_ENCODER_INIT_STATUS_OK;
}

FLAC_API FLAC__StreamEncoderInitStatus FLAC__stream_encoder_init_stream(
	FLAC__StreamEncoder *encoder,
	FLAC__StreamEncoderWriteCallback write_callback,
	FLAC__StreamEncoderSeekCallback seek_callback,
	FLAC__StreamEncoderTellCallback tell_callback,
	FLAC__StreamEncoderMetadataCallback metadata_callback,
	void *client_data
)
{
	return init_stream_internal_(
		encoder,
		/*read_callback=*/0,
		write_callback,
		seek_callback,
		tell_callback,
		metadata_callback,
		client_data,
		/*is_ogg=*/false
	);
}

FLAC_API FLAC__StreamEncoderInitStatus FLAC__stream_encoder_init_ogg_stream(
	FLAC__StreamEncoder *encoder,
	FLAC__StreamEncoderReadCallback read_callback,
	FLAC__StreamEncoderWriteCallback write_callback,
	FLAC__StreamEncoderSeekCallback seek_callback,
	FLAC__StreamEncoderTellCallback tell_callback,
	FLAC__StreamEncoderMetadataCallback metadata_callback,
	void *client_data
)
{
	return init_stream_internal_(
		encoder,
		read_callback,
		write_callback,
		seek_callback,
		tell_callback,
		metadata_callback,
		client_data,
		/*is_ogg=*/true
	);
}

static FLAC__StreamEncoderInitStatus init_FILE_internal_(
	FLAC__StreamEncoder *encoder,
	FILE *file,
	FLAC__StreamEncoderProgressCallback progress_callback,
	void *client_data,
	FLAC__bool is_ogg
)
{
	FLAC__StreamEncoderInitStatus init_status;

	FLAC__ASSERT(0 != encoder);
	FLAC__ASSERT(0 != file);

	if(encoder->protected_->state != FLAC__STREAM_ENCODER_UNINITIALIZED)
		return FLAC__STREAM_ENCODER_INIT_STATUS_ALREADY_INITIALIZED;

	/* double protection */
	if(file == 0) {
		encoder->protected_->state = FLAC__STREAM_ENCODER_IO_ERROR;
		return FLAC__STREAM_ENCODER_INIT_STATUS_ENCODER_ERROR;
	}

	/*
	 * To make sure that our file does not go unclosed after an error, we
	 * must assign the FILE pointer before any further error can occur in
	 * this routine.
	 */
	if(file == stdout)
		file = get_binary_stdout_(); /* just to be safe */

#ifdef _WIN32
	/*
	 * Windows can suffer quite badly from disk fragmentation. This can be
	 * reduced significantly by setting the output buffer size to be 10MB.
	 */
	if(GetFileType((HANDLE)_get_osfhandle(_fileno(file))) == FILE_TYPE_DISK)
		setvbuf(file, NULL, _IOFBF, 10*1024*1024);
#endif
	encoder->private_->file = file;

	encoder->private_->progress_callback = progress_callback;
	encoder->private_->bytes_written = 0;
	encoder->private_->samples_written = 0;
	encoder->private_->frames_written = 0;

	init_status = init_stream_internal_(
		encoder,
		encoder->private_->file == stdout? 0 : is_ogg? file_read_callback_ : 0,
		file_write_callback_,
		encoder->private_->file == stdout? 0 : file_seek_callback_,
		encoder->private_->file == stdout? 0 : file_tell_callback_,
		/*metadata_callback=*/0,
		client_data,
		is_ogg
	);
	if(init_status != FLAC__STREAM_ENCODER_INIT_STATUS_OK) {
		/* the above function sets the state for us in case of an error */
		return init_status;
	}

	{
		uint32_t blocksize = FLAC__stream_encoder_get_blocksize(encoder);

		FLAC__ASSERT(blocksize != 0);
		encoder->private_->total_frames_estimate = (uint32_t)((FLAC__stream_encoder_get_total_samples_estimate(encoder) + blocksize - 1) / blocksize);
	}

	return init_status;
}

FLAC_API FLAC__StreamEncoderInitStatus FLAC__stream_encoder_init_FILE(
	FLAC__StreamEncoder *encoder,
	FILE *file,
	FLAC__StreamEncoderProgressCallback progress_callback,
	void *client_data
)
{
	return init_FILE_internal_(encoder, file, progress_callback, client_data, /*is_ogg=*/false);
}

FLAC_API FLAC__StreamEncoderInitStatus FLAC__stream_encoder_init_ogg_FILE(
	FLAC__StreamEncoder *encoder,
	FILE *file,
	FLAC__StreamEncoderProgressCallback progress_callback,
	void *client_data
)
{
	return init_FILE_internal_(encoder, file, progress_callback, client_data, /*is_ogg=*/true);
}

static FLAC__StreamEncoderInitStatus init_file_internal_(
	FLAC__StreamEncoder *encoder,
	const char *filename,
	FLAC__StreamEncoderProgressCallback progress_callback,
	void *client_data,
	FLAC__bool is_ogg
)
{
	FILE *file;

	FLAC__ASSERT(0 != encoder);

	/*
	 * To make sure that our file does not go unclosed after an error, we
	 * have to do the same entrance checks here that are later performed
	 * in FLAC__stream_encoder_init_FILE() before the FILE* is assigned.
	 */
	if(encoder->protected_->state != FLAC__STREAM_ENCODER_UNINITIALIZED)
		return FLAC__STREAM_ENCODER_INIT_STATUS_ALREADY_INITIALIZED;

	file = filename? flac_fopen(filename, "w+b") : stdout;

	if(file == 0) {
		encoder->protected_->state = FLAC__STREAM_ENCODER_IO_ERROR;
		return FLAC__STREAM_ENCODER_INIT_STATUS_ENCODER_ERROR;
	}

	return init_FILE_internal_(encoder, file, progress_callback, client_data, is_ogg);
}

FLAC_API FLAC__StreamEncoderInitStatus FLAC__stream_encoder_init_file(
	FLAC__StreamEncoder *encoder,
	const char *filename,
	FLAC__StreamEncoderProgressCallback progress_callback,
	void *client_data
)
{
	return init_file_internal_(encoder, filename, progress_callback, client_data, /*is_ogg=*/false);
}

FLAC_API FLAC__StreamEncoderInitStatus FLAC__stream_encoder_init_ogg_file(
	FLAC__StreamEncoder *encoder,
	const char *filename,
	FLAC__StreamEncoderProgressCallback progress_callback,
	void *client_data
)
{
	return init_file_internal_(encoder, filename, progress_callback, client_data, /*is_ogg=*/true);
}

FLAC_API FLAC__bool FLAC__stream_encoder_finish(FLAC__StreamEncoder *encoder)
{
	FLAC__bool error = false;

	if (encoder == NULL)
		return false;

	FLAC__ASSERT(0 != encoder->private_);
	FLAC__ASSERT(0 != encoder->protected_);

	if(encoder->protected_->state == FLAC__STREAM_ENCODER_UNINITIALIZED)
		return true;

	if(encoder->protected_->state == FLAC__STREAM_ENCODER_OK && !encoder->private_->is_being_deleted) {
		if(encoder->private_->current_sample_number != 0) {
			const FLAC__bool is_fractional_block = encoder->protected_->blocksize != encoder->private_->current_sample_number;
			encoder->protected_->blocksize = encoder->private_->current_sample_number;
			if(!process_frame_(encoder, is_fractional_block, /*is_last_block=*/true))
				error = true;
		}
	}

	if(encoder->protected_->do_md5)
		FLAC__MD5Final(encoder->private_->streaminfo.data.stream_info.md5sum, &encoder->private_->md5context);

	if(!encoder->private_->is_being_deleted) {
		if(encoder->protected_->state == FLAC__STREAM_ENCODER_OK) {
			if(encoder->private_->seek_callback) {
#if FLAC__HAS_OGG
				if(encoder->private_->is_ogg)
					update_ogg_metadata_(encoder);
				else
#endif
				update_metadata_(encoder);

				/* check if an error occurred while updating metadata */
				if(encoder->protected_->state != FLAC__STREAM_ENCODER_OK)
					error = true;
			}
			if(encoder->private_->metadata_callback)
				encoder->private_->metadata_callback(encoder, &encoder->private_->streaminfo, encoder->private_->client_data);
		}

		if(encoder->protected_->verify && 0 != encoder->private_->verify.decoder && !FLAC__stream_decoder_finish(encoder->private_->verify.decoder)) {
			if(!error)
				encoder->protected_->state = FLAC__STREAM_ENCODER_VERIFY_MISMATCH_IN_AUDIO_DATA;
			error = true;
		}
	}

	if(0 != encoder->private_->file) {
		if(encoder->private_->file != stdout)
			fclose(encoder->private_->file);
		encoder->private_->file = 0;
	}

#if FLAC__HAS_OGG
	if(encoder->private_->is_ogg)
		FLAC__ogg_encoder_aspect_finish(&encoder->protected_->ogg_encoder_aspect);
#endif

	free_(encoder);
	set_defaults_(encoder);

	if(!error)
		encoder->protected_->state = FLAC__STREAM_ENCODER_UNINITIALIZED;

	return !error;
}

FLAC_API FLAC__bool FLAC__stream_encoder_set_ogg_serial_number(FLAC__StreamEncoder *encoder, long value)
{
	FLAC__ASSERT(0 != encoder);
	FLAC__ASSERT(0 != encoder->private_);
	FLAC__ASSERT(0 != encoder->protected_);
	if(encoder->protected_->state != FLAC__STREAM_ENCODER_UNINITIALIZED)
		return false;
#if FLAC__HAS_OGG
	/* can't check encoder->private_->is_ogg since that's not set until init time */
	FLAC__ogg_encoder_aspect_set_serial_number(&encoder->protected_->ogg_encoder_aspect, value);
	return true;
#else
	(void)value;
	return false;
#endif
}

FLAC_API FLAC__bool FLAC__stream_encoder_set_verify(FLAC__StreamEncoder *encoder, FLAC__bool value)
{
	FLAC__ASSERT(0 != encoder);
	FLAC__ASSERT(0 != encoder->private_);
	FLAC__ASSERT(0 != encoder->protected_);
	if(encoder->protected_->state != FLAC__STREAM_ENCODER_UNINITIALIZED)
		return false;
#ifndef FLAC__MANDATORY_VERIFY_WHILE_ENCODING
	encoder->protected_->verify = value;
#endif
	return true;
}

FLAC_API FLAC__bool FLAC__stream_encoder_set_streamable_subset(FLAC__StreamEncoder *encoder, FLAC__bool value)
{
	FLAC__ASSERT(0 != encoder);
	FLAC__ASSERT(0 != encoder->private_);
	FLAC__ASSERT(0 != encoder->protected_);
	if(encoder->protected_->state != FLAC__STREAM_ENCODER_UNINITIALIZED)
		return false;
	encoder->protected_->streamable_subset = value;
	return true;
}

FLAC_API FLAC__bool FLAC__stream_encoder_set_do_md5(FLAC__StreamEncoder *encoder, FLAC__bool value)
{
	FLAC__ASSERT(0 != encoder);
	FLAC__ASSERT(0 != encoder->private_);
	FLAC__ASSERT(0 != encoder->protected_);
	if(encoder->protected_->state != FLAC__STREAM_ENCODER_UNINITIALIZED)
		return false;
	encoder->protected_->do_md5 = value;
	return true;
}

FLAC_API FLAC__bool FLAC__stream_encoder_set_channels(FLAC__StreamEncoder *encoder, uint32_t value)
{
	FLAC__ASSERT(0 != encoder);
	FLAC__ASSERT(0 != encoder->private_);
	FLAC__ASSERT(0 != encoder->protected_);
	if(encoder->protected_->state != FLAC__STREAM_ENCODER_UNINITIALIZED)
		return false;
	encoder->protected_->channels = value;
	return true;
}

FLAC_API FLAC__bool FLAC__stream_encoder_set_bits_per_sample(FLAC__StreamEncoder *encoder, uint32_t value)
{
	FLAC__ASSERT(0 != encoder);
	FLAC__ASSERT(0 != encoder->private_);
	FLAC__ASSERT(0 != encoder->protected_);
	if(encoder->protected_->state != FLAC__STREAM_ENCODER_UNINITIALIZED)
		return false;
	encoder->protected_->bits_per_sample = value;
	return true;
}

FLAC_API FLAC__bool FLAC__stream_encoder_set_sample_rate(FLAC__StreamEncoder *encoder, uint32_t value)
{
	FLAC__ASSERT(0 != encoder);
	FLAC__ASSERT(0 != encoder->private_);
	FLAC__ASSERT(0 != encoder->protected_);
	if(encoder->protected_->state != FLAC__STREAM_ENCODER_UNINITIALIZED)
		return false;
	encoder->protected_->sample_rate = value;
	return true;
}

FLAC_API FLAC__bool FLAC__stream_encoder_set_compression_level(FLAC__StreamEncoder *encoder, uint32_t value)
{
	FLAC__bool ok = true;
	FLAC__ASSERT(0 != encoder);
	FLAC__ASSERT(0 != encoder->private_);
	FLAC__ASSERT(0 != encoder->protected_);
	if(encoder->protected_->state != FLAC__STREAM_ENCODER_UNINITIALIZED)
		return false;
	if(value >= sizeof(compression_levels_)/sizeof(compression_levels_[0]))
		value = sizeof(compression_levels_)/sizeof(compression_levels_[0]) - 1;
	ok &= FLAC__stream_encoder_set_do_mid_side_stereo          (encoder, compression_levels_[value].do_mid_side_stereo);
	ok &= FLAC__stream_encoder_set_loose_mid_side_stereo       (encoder, compression_levels_[value].loose_mid_side_stereo);
#ifndef FLAC__INTEGER_ONLY_LIBRARY
#if 1
	ok &= FLAC__stream_encoder_set_apodization                 (encoder, compression_levels_[value].apodization);
#else
	/* equivalent to -A tukey(0.5) */
	encoder->protected_->num_apodizations = 1;
	encoder->protected_->apodizations[0].type = FLAC__APODIZATION_TUKEY;
	encoder->protected_->apodizations[0].parameters.tukey.p = 0.5;
#endif
#endif
	ok &= FLAC__stream_encoder_set_max_lpc_order               (encoder, compression_levels_[value].max_lpc_order);
	ok &= FLAC__stream_encoder_set_qlp_coeff_precision         (encoder, compression_levels_[value].qlp_coeff_precision);
	ok &= FLAC__stream_encoder_set_do_qlp_coeff_prec_search    (encoder, compression_levels_[value].do_qlp_coeff_prec_search);
	ok &= FLAC__stream_encoder_set_do_escape_coding            (encoder, compression_levels_[value].do_escape_coding);
	ok &= FLAC__stream_encoder_set_do_exhaustive_model_search  (encoder, compression_levels_[value].do_exhaustive_model_search);
	ok &= FLAC__stream_encoder_set_min_residual_partition_order(encoder, compression_levels_[value].min_residual_partition_order);
	ok &= FLAC__stream_encoder_set_max_residual_partition_order(encoder, compression_levels_[value].max_residual_partition_order);
	ok &= FLAC__stream_encoder_set_rice_parameter_search_dist  (encoder, compression_levels_[value].rice_parameter_search_dist);
	return ok;
}

FLAC_API FLAC__bool FLAC__stream_encoder_set_blocksize(FLAC__StreamEncoder *encoder, uint32_t value)
{
	FLAC__ASSERT(0 != encoder);
	FLAC__ASSERT(0 != encoder->private_);
	FLAC__ASSERT(0 != encoder->protected_);
	if(encoder->protected_->state != FLAC__STREAM_ENCODER_UNINITIALIZED)
		return false;
	encoder->protected_->blocksize = value;
	return true;
}

FLAC_API FLAC__bool FLAC__stream_encoder_set_do_mid_side_stereo(FLAC__StreamEncoder *encoder, FLAC__bool value)
{
	FLAC__ASSERT(0 != encoder);
	FLAC__ASSERT(0 != encoder->private_);
	FLAC__ASSERT(0 != encoder->protected_);
	if(encoder->protected_->state != FLAC__STREAM_ENCODER_UNINITIALIZED)
		return false;
	encoder->protected_->do_mid_side_stereo = value;
	return true;
}

FLAC_API FLAC__bool FLAC__stream_encoder_set_loose_mid_side_stereo(FLAC__StreamEncoder *encoder, FLAC__bool value)
{
	FLAC__ASSERT(0 != encoder);
	FLAC__ASSERT(0 != encoder->private_);
	FLAC__ASSERT(0 != encoder->protected_);
	if(encoder->protected_->state != FLAC__STREAM_ENCODER_UNINITIALIZED)
		return false;
	encoder->protected_->loose_mid_side_stereo = value;
	return true;
}

/*@@@@add to tests*/
FLAC_API FLAC__bool FLAC__stream_encoder_set_apodization(FLAC__StreamEncoder *encoder, const char *specification)
{
	FLAC__ASSERT(0 != encoder);
	FLAC__ASSERT(0 != encoder->private_);
	FLAC__ASSERT(0 != encoder->protected_);
	FLAC__ASSERT(0 != specification);
	if(encoder->protected_->state != FLAC__STREAM_ENCODER_UNINITIALIZED)
		return false;
#ifdef FLAC__INTEGER_ONLY_LIBRARY
	(void)specification; /* silently ignore since we haven't integerized; will always use a rectangular window */
#else
	encoder->protected_->num_apodizations = 0;
	while(1) {
		const char *s = strchr(specification, ';');
		const size_t n = s? (size_t)(s - specification) : strlen(specification);
		if     (n==8  && 0 == strncmp("bartlett"     , specification, n))
			encoder->protected_->apodizations[encoder->protected_->num_apodizations++].type = FLAC__APODIZATION_BARTLETT;
		else if(n==13 && 0 == strncmp("bartlett_hann", specification, n))
			encoder->protected_->apodizations[encoder->protected_->num_apodizations++].type = FLAC__APODIZATION_BARTLETT_HANN;
		else if(n==8  && 0 == strncmp("blackman"     , specification, n))
			encoder->protected_->apodizations[encoder->protected_->num_apodizations++].type = FLAC__APODIZATION_BLACKMAN;
		else if(n==26 && 0 == strncmp("blackman_harris_4term_92db", specification, n))
			encoder->protected_->apodizations[encoder->protected_->num_apodizations++].type = FLAC__APODIZATION_BLACKMAN_HARRIS_4TERM_92DB_SIDELOBE;
		else if(n==6  && 0 == strncmp("connes"       , specification, n))
			encoder->protected_->apodizations[encoder->protected_->num_apodizations++].type = FLAC__APODIZATION_CONNES;
		else if(n==7  && 0 == strncmp("flattop"      , specification, n))
			encoder->protected_->apodizations[encoder->protected_->num_apodizations++].type = FLAC__APODIZATION_FLATTOP;
		else if(n>7   && 0 == strncmp("gauss("       , specification, 6)) {
			FLAC__real stddev = (FLAC__real)strtod(specification+6, 0);
			if (stddev > 0.0 && stddev <= 0.5) {
				encoder->protected_->apodizations[encoder->protected_->num_apodizations].parameters.gauss.stddev = stddev;
				encoder->protected_->apodizations[encoder->protected_->num_apodizations++].type = FLAC__APODIZATION_GAUSS;
			}
		}
		else if(n==7  && 0 == strncmp("hamming"      , specification, n))
			encoder->protected_->apodizations[encoder->protected_->num_apodizations++].type = FLAC__APODIZATION_HAMMING;
		else if(n==4  && 0 == strncmp("hann"         , specification, n))
			encoder->protected_->apodizations[encoder->protected_->num_apodizations++].type = FLAC__APODIZATION_HANN;
		else if(n==13 && 0 == strncmp("kaiser_bessel", specification, n))
			encoder->protected_->apodizations[encoder->protected_->num_apodizations++].type = FLAC__APODIZATION_KAISER_BESSEL;
		else if(n==7  && 0 == strncmp("nuttall"      , specification, n))
			encoder->protected_->apodizations[encoder->protected_->num_apodizations++].type = FLAC__APODIZATION_NUTTALL;
		else if(n==9  && 0 == strncmp("rectangle"    , specification, n))
			encoder->protected_->apodizations[encoder->protected_->num_apodizations++].type = FLAC__APODIZATION_RECTANGLE;
		else if(n==8  && 0 == strncmp("triangle"     , specification, n))
			encoder->protected_->apodizations[encoder->protected_->num_apodizations++].type = FLAC__APODIZATION_TRIANGLE;
		else if(n>7   && 0 == strncmp("tukey("       , specification, 6)) {
			FLAC__real p = (FLAC__real)strtod(specification+6, 0);
			if (p >= 0.0 && p <= 1.0) {
				encoder->protected_->apodizations[encoder->protected_->num_apodizations].parameters.tukey.p = p;
				encoder->protected_->apodizations[encoder->protected_->num_apodizations++].type = FLAC__APODIZATION_TUKEY;
			}
		}
		else if(n>15   && 0 == strncmp("partial_tukey("       , specification, 14)) {
			FLAC__int32 tukey_parts = (FLAC__int32)strtod(specification+14, 0);
			const char *si_1 = strchr(specification, '/');
			FLAC__real overlap = si_1?flac_min((FLAC__real)strtod(si_1+1, 0),0.99f):0.1f;
			FLAC__real overlap_units = 1.0f/(1.0f - overlap) - 1.0f;
			const char *si_2 = strchr((si_1?(si_1+1):specification), '/');
			FLAC__real tukey_p = si_2?(FLAC__real)strtod(si_2+1, 0):0.2f;

			if (tukey_parts <= 1) {
				encoder->protected_->apodizations[encoder->protected_->num_apodizations].parameters.tukey.p = tukey_p;
				encoder->protected_->apodizations[encoder->protected_->num_apodizations++].type = FLAC__APODIZATION_TUKEY;
			}else if (encoder->protected_->num_apodizations + tukey_parts < 32){
				FLAC__int32 m;
				for(m = 0; m < tukey_parts; m++){
					encoder->protected_->apodizations[encoder->protected_->num_apodizations].parameters.multiple_tukey.p = tukey_p;
					encoder->protected_->apodizations[encoder->protected_->num_apodizations].parameters.multiple_tukey.start = m/(tukey_parts+overlap_units);
					encoder->protected_->apodizations[encoder->protected_->num_apodizations].parameters.multiple_tukey.end = (m+1+overlap_units)/(tukey_parts+overlap_units);
					encoder->protected_->apodizations[encoder->protected_->num_apodizations++].type = FLAC__APODIZATION_PARTIAL_TUKEY;
				}
			}
		}
		else if(n>16   && 0 == strncmp("punchout_tukey("       , specification, 15)) {
			FLAC__int32 tukey_parts = (FLAC__int32)strtod(specification+15, 0);
			const char *si_1 = strchr(specification, '/');
			FLAC__real overlap = si_1?flac_min((FLAC__real)strtod(si_1+1, 0),0.99f):0.2f;
			FLAC__real overlap_units = 1.0f/(1.0f - overlap) - 1.0f;
			const char *si_2 = strchr((si_1?(si_1+1):specification), '/');
			FLAC__real tukey_p = si_2?(FLAC__real)strtod(si_2+1, 0):0.2f;

			if (tukey_parts <= 1) {
				encoder->protected_->apodizations[encoder->protected_->num_apodizations].parameters.tukey.p = tukey_p;
				encoder->protected_->apodizations[encoder->protected_->num_apodizations++].type = FLAC__APODIZATION_TUKEY;
			}else if (encoder->protected_->num_apodizations + tukey_parts < 32){
				FLAC__int32 m;
				for(m = 0; m < tukey_parts; m++){
					encoder->protected_->apodizations[encoder->protected_->num_apodizations].parameters.multiple_tukey.p = tukey_p;
					encoder->protected_->apodizations[encoder->protected_->num_apodizations].parameters.multiple_tukey.start = m/(tukey_parts+overlap_units);
					encoder->protected_->apodizations[encoder->protected_->num_apodizations].parameters.multiple_tukey.end = (m+1+overlap_units)/(tukey_parts+overlap_units);
					encoder->protected_->apodizations[encoder->protected_->num_apodizations++].type = FLAC__APODIZATION_PUNCHOUT_TUKEY;
				}
			}
		}
		else if(n==5  && 0 == strncmp("welch"        , specification, n))
			encoder->protected_->apodizations[encoder->protected_->num_apodizations++].type = FLAC__APODIZATION_WELCH;
		if (encoder->protected_->num_apodizations == 32)
			break;
		if (s)
			specification = s+1;
		else
			break;
	}
	if(encoder->protected_->num_apodizations == 0) {
		encoder->protected_->num_apodizations = 1;
		encoder->protected_->apodizations[0].type = FLAC__APODIZATION_TUKEY;
		encoder->protected_->apodizations[0].parameters.tukey.p = 0.5;
	}
#endif
	return true;
}

FLAC_API FLAC__bool FLAC__stream_encoder_set_max_lpc_order(FLAC__StreamEncoder *encoder, uint32_t value)
{
	FLAC__ASSERT(0 != encoder);
	FLAC__ASSERT(0 != encoder->private_);
	FLAC__ASSERT(0 != encoder->protected_);
	if(encoder->protected_->state != FLAC__STREAM_ENCODER_UNINITIALIZED)
		return false;
	encoder->protected_->max_lpc_order = value;
	return true;
}

FLAC_API FLAC__bool FLAC__stream_encoder_set_qlp_coeff_precision(FLAC__StreamEncoder *encoder, uint32_t value)
{
	FLAC__ASSERT(0 != encoder);
	FLAC__ASSERT(0 != encoder->private_);
	FLAC__ASSERT(0 != encoder->protected_);
	if(encoder->protected_->state != FLAC__STREAM_ENCODER_UNINITIALIZED)
		return false;
	encoder->protected_->qlp_coeff_precision = value;
	return true;
}

FLAC_API FLAC__bool FLAC__stream_encoder_set_do_qlp_coeff_prec_search(FLAC__StreamEncoder *encoder, FLAC__bool value)
{
	FLAC__ASSERT(0 != encoder);
	FLAC__ASSERT(0 != encoder->private_);
	FLAC__ASSERT(0 != encoder->protected_);
	if(encoder->protected_->state != FLAC__STREAM_ENCODER_UNINITIALIZED)
		return false;
	encoder->protected_->do_qlp_coeff_prec_search = value;
	return true;
}

FLAC_API FLAC__bool FLAC__stream_encoder_set_do_escape_coding(FLAC__StreamEncoder *encoder, FLAC__bool value)
{
	FLAC__ASSERT(0 != encoder);
	FLAC__ASSERT(0 != encoder->private_);
	FLAC__ASSERT(0 != encoder->protected_);
	if(encoder->protected_->state != FLAC__STREAM_ENCODER_UNINITIALIZED)
		return false;
#if 0
	/*@@@ deprecated: */
	encoder->protected_->do_escape_coding = value;
#else
	(void)value;
#endif
	return true;
}

FLAC_API FLAC__bool FLAC__stream_encoder_set_do_exhaustive_model_search(FLAC__StreamEncoder *encoder, FLAC__bool value)
{
	FLAC__ASSERT(0 != encoder);
	FLAC__ASSERT(0 != encoder->private_);
	FLAC__ASSERT(0 != encoder->protected_);
	if(encoder->protected_->state != FLAC__STREAM_ENCODER_UNINITIALIZED)
		return false;
	encoder->protected_->do_exhaustive_model_search = value;
	return true;
}

FLAC_API FLAC__bool FLAC__stream_encoder_set_min_residual_partition_order(FLAC__StreamEncoder *encoder, uint32_t value)
{
	FLAC__ASSERT(0 != encoder);
	FLAC__ASSERT(0 != encoder->private_);
	FLAC__ASSERT(0 != encoder->protected_);
	if(encoder->protected_->state != FLAC__STREAM_ENCODER_UNINITIALIZED)
		return false;
	encoder->protected_->min_residual_partition_order = value;
	return true;
}

FLAC_API FLAC__bool FLAC__stream_encoder_set_max_residual_partition_order(FLAC__StreamEncoder *encoder, uint32_t value)
{
	FLAC__ASSERT(0 != encoder);
	FLAC__ASSERT(0 != encoder->private_);
	FLAC__ASSERT(0 != encoder->protected_);
	if(encoder->protected_->state != FLAC__STREAM_ENCODER_UNINITIALIZED)
		return false;
	encoder->protected_->max_residual_partition_order = value;
	return true;
}

FLAC_API FLAC__bool FLAC__stream_encoder_set_rice_parameter_search_dist(FLAC__StreamEncoder *encoder, uint32_t value)
{
	FLAC__ASSERT(0 != encoder);
	FLAC__ASSERT(0 != encoder->private_);
	FLAC__ASSERT(0 != encoder->protected_);
	if(encoder->protected_->state != FLAC__STREAM_ENCODER_UNINITIALIZED)
		return false;
#if 0
	/*@@@ deprecated: */
	encoder->protected_->rice_parameter_search_dist = value;
#else
	(void)value;
#endif
	return true;
}

FLAC_API FLAC__bool FLAC__stream_encoder_set_total_samples_estimate(FLAC__StreamEncoder *encoder, FLAC__uint64 value)
{
	FLAC__ASSERT(0 != encoder);
	FLAC__ASSERT(0 != encoder->private_);
	FLAC__ASSERT(0 != encoder->protected_);
	if(encoder->protected_->state != FLAC__STREAM_ENCODER_UNINITIALIZED)
		return false;
	value = flac_min(value, (FLAC__U64L(1) << FLAC__STREAM_METADATA_STREAMINFO_TOTAL_SAMPLES_LEN) - 1);
	encoder->protected_->total_samples_estimate = value;
	return true;
}

FLAC_API FLAC__bool FLAC__stream_encoder_set_metadata(FLAC__StreamEncoder *encoder, FLAC__StreamMetadata **metadata, uint32_t num_blocks)
{
	FLAC__ASSERT(0 != encoder);
	FLAC__ASSERT(0 != encoder->private_);
	FLAC__ASSERT(0 != encoder->protected_);
	if(encoder->protected_->state != FLAC__STREAM_ENCODER_UNINITIALIZED)
		return false;
	if(0 == metadata)
		num_blocks = 0;
	if(0 == num_blocks)
		metadata = 0;
	/* realloc() does not do exactly what we want so... */
	if(encoder->protected_->metadata) {
		free(encoder->protected_->metadata);
		encoder->protected_->metadata = 0;
		encoder->protected_->num_metadata_blocks = 0;
	}
	if(num_blocks) {
		FLAC__StreamMetadata **m;
		if(0 == (m = safe_malloc_mul_2op_p(sizeof(m[0]), /*times*/num_blocks)))
			return false;
		memcpy(m, metadata, sizeof(m[0]) * num_blocks);
		encoder->protected_->metadata = m;
		encoder->protected_->num_metadata_blocks = num_blocks;
	}
#if FLAC__HAS_OGG
	if(!FLAC__ogg_encoder_aspect_set_num_metadata(&encoder->protected_->ogg_encoder_aspect, num_blocks))
		return false;
#endif
	return true;
}

/*
 * These three functions are not static, but not publically exposed in
 * include/FLAC/ either.  They are used by the test suite.
 */
FLAC_API FLAC__bool FLAC__stream_encoder_disable_constant_subframes(FLAC__StreamEncoder *encoder, FLAC__bool value)
{
	FLAC__ASSERT(0 != encoder);
	FLAC__ASSERT(0 != encoder->private_);
	FLAC__ASSERT(0 != encoder->protected_);
	if(encoder->protected_->state != FLAC__STREAM_ENCODER_UNINITIALIZED)
		return false;
	encoder->private_->disable_constant_subframes = value;
	return true;
}

FLAC_API FLAC__bool FLAC__stream_encoder_disable_fixed_subframes(FLAC__StreamEncoder *encoder, FLAC__bool value)
{
	FLAC__ASSERT(0 != encoder);
	FLAC__ASSERT(0 != encoder->private_);
	FLAC__ASSERT(0 != encoder->protected_);
	if(encoder->protected_->state != FLAC__STREAM_ENCODER_UNINITIALIZED)
		return false;
	encoder->private_->disable_fixed_subframes = value;
	return true;
}

FLAC_API FLAC__bool FLAC__stream_encoder_disable_verbatim_subframes(FLAC__StreamEncoder *encoder, FLAC__bool value)
{
	FLAC__ASSERT(0 != encoder);
	FLAC__ASSERT(0 != encoder->private_);
	FLAC__ASSERT(0 != encoder->protected_);
	if(encoder->protected_->state != FLAC__STREAM_ENCODER_UNINITIALIZED)
		return false;
	encoder->private_->disable_verbatim_subframes = value;
	return true;
}

FLAC_API FLAC__StreamEncoderState FLAC__stream_encoder_get_state(const FLAC__StreamEncoder *encoder)
{
	FLAC__ASSERT(0 != encoder);
	FLAC__ASSERT(0 != encoder->private_);
	FLAC__ASSERT(0 != encoder->protected_);
	return encoder->protected_->state;
}

FLAC_API FLAC__StreamDecoderState FLAC__stream_encoder_get_verify_decoder_state(const FLAC__StreamEncoder *encoder)
{
	FLAC__ASSERT(0 != encoder);
	FLAC__ASSERT(0 != encoder->private_);
	FLAC__ASSERT(0 != encoder->protected_);
	if(encoder->protected_->verify)
		return FLAC__stream_decoder_get_state(encoder->private_->verify.decoder);
	else
		return FLAC__STREAM_DECODER_UNINITIALIZED;
}

FLAC_API const char *FLAC__stream_encoder_get_resolved_state_string(const FLAC__StreamEncoder *encoder)
{
	FLAC__ASSERT(0 != encoder);
	FLAC__ASSERT(0 != encoder->private_);
	FLAC__ASSERT(0 != encoder->protected_);
	if(encoder->protected_->state != FLAC__STREAM_ENCODER_VERIFY_DECODER_ERROR)
		return FLAC__StreamEncoderStateString[encoder->protected_->state];
	else
		return FLAC__stream_decoder_get_resolved_state_string(encoder->private_->verify.decoder);
}

FLAC_API void FLAC__stream_encoder_get_verify_decoder_error_stats(const FLAC__StreamEncoder *encoder, FLAC__uint64 *absolute_sample, uint32_t *frame_number, uint32_t *channel, uint32_t *sample, FLAC__int32 *expected, FLAC__int32 *got)
{
	FLAC__ASSERT(0 != encoder);
	FLAC__ASSERT(0 != encoder->private_);
	FLAC__ASSERT(0 != encoder->protected_);
	if(0 != absolute_sample)
		*absolute_sample = encoder->private_->verify.error_stats.absolute_sample;
	if(0 != frame_number)
		*frame_number = encoder->private_->verify.error_stats.frame_number;
	if(0 != channel)
		*channel = encoder->private_->verify.error_stats.channel;
	if(0 != sample)
		*sample = encoder->private_->verify.error_stats.sample;
	if(0 != expected)
		*expected = encoder->private_->verify.error_stats.expected;
	if(0 != got)
		*got = encoder->private_->verify.error_stats.got;
}

FLAC_API FLAC__bool FLAC__stream_encoder_get_verify(const FLAC__StreamEncoder *encoder)
{
	FLAC__ASSERT(0 != encoder);
	FLAC__ASSERT(0 != encoder->private_);
	FLAC__ASSERT(0 != encoder->protected_);
	return encoder->protected_->verify;
}

FLAC_API FLAC__bool FLAC__stream_encoder_get_streamable_subset(const FLAC__StreamEncoder *encoder)
{
	FLAC__ASSERT(0 != encoder);
	FLAC__ASSERT(0 != encoder->private_);
	FLAC__ASSERT(0 != encoder->protected_);
	return encoder->protected_->streamable_subset;
}

FLAC_API FLAC__bool FLAC__stream_encoder_get_do_md5(const FLAC__StreamEncoder *encoder)
{
	FLAC__ASSERT(0 != encoder);
	FLAC__ASSERT(0 != encoder->private_);
	FLAC__ASSERT(0 != encoder->protected_);
	return encoder->protected_->do_md5;
}

FLAC_API uint32_t FLAC__stream_encoder_get_channels(const FLAC__StreamEncoder *encoder)
{
	FLAC__ASSERT(0 != encoder);
	FLAC__ASSERT(0 != encoder->private_);
	FLAC__ASSERT(0 != encoder->protected_);
	return encoder->protected_->channels;
}

FLAC_API uint32_t FLAC__stream_encoder_get_bits_per_sample(const FLAC__StreamEncoder *encoder)
{
	FLAC__ASSERT(0 != encoder);
	FLAC__ASSERT(0 != encoder->private_);
	FLAC__ASSERT(0 != encoder->protected_);
	return encoder->protected_->bits_per_sample;
}

FLAC_API uint32_t FLAC__stream_encoder_get_sample_rate(const FLAC__StreamEncoder *encoder)
{
	FLAC__ASSERT(0 != encoder);
	FLAC__ASSERT(0 != encoder->private_);
	FLAC__ASSERT(0 != encoder->protected_);
	return encoder->protected_->sample_rate;
}

FLAC_API uint32_t FLAC__stream_encoder_get_blocksize(const FLAC__StreamEncoder *encoder)
{
	FLAC__ASSERT(0 != encoder);
	FLAC__ASSERT(0 != encoder->private_);
	FLAC__ASSERT(0 != encoder->protected_);
	return encoder->protected_->blocksize;
}

FLAC_API FLAC__bool FLAC__stream_encoder_get_do_mid_side_stereo(const FLAC__StreamEncoder *encoder)
{
	FLAC__ASSERT(0 != encoder);
	FLAC__ASSERT(0 != encoder->private_);
	FLAC__ASSERT(0 != encoder->protected_);
	return encoder->protected_->do_mid_side_stereo;
}

FLAC_API FLAC__bool FLAC__stream_encoder_get_loose_mid_side_stereo(const FLAC__StreamEncoder *encoder)
{
	FLAC__ASSERT(0 != encoder);
	FLAC__ASSERT(0 != encoder->private_);
	FLAC__ASSERT(0 != encoder->protected_);
	return encoder->protected_->loose_mid_side_stereo;
}

FLAC_API uint32_t FLAC__stream_encoder_get_max_lpc_order(const FLAC__StreamEncoder *encoder)
{
	FLAC__ASSERT(0 != encoder);
	FLAC__ASSERT(0 != encoder->private_);
	FLAC__ASSERT(0 != encoder->protected_);
	return encoder->protected_->max_lpc_order;
}

FLAC_API uint32_t FLAC__stream_encoder_get_qlp_coeff_precision(const FLAC__StreamEncoder *encoder)
{
	FLAC__ASSERT(0 != encoder);
	FLAC__ASSERT(0 != encoder->private_);
	FLAC__ASSERT(0 != encoder->protected_);
	return encoder->protected_->qlp_coeff_precision;
}

FLAC_API FLAC__bool FLAC__stream_encoder_get_do_qlp_coeff_prec_search(const FLAC__StreamEncoder *encoder)
{
	FLAC__ASSERT(0 != encoder);
	FLAC__ASSERT(0 != encoder->private_);
	FLAC__ASSERT(0 != encoder->protected_);
	return encoder->protected_->do_qlp_coeff_prec_search;
}

FLAC_API FLAC__bool FLAC__stream_encoder_get_do_escape_coding(const FLAC__StreamEncoder *encoder)
{
	FLAC__ASSERT(0 != encoder);
	FLAC__ASSERT(0 != encoder->private_);
	FLAC__ASSERT(0 != encoder->protected_);
	return encoder->protected_->do_escape_coding;
}

FLAC_API FLAC__bool FLAC__stream_encoder_get_do_exhaustive_model_search(const FLAC__StreamEncoder *encoder)
{
	FLAC__ASSERT(0 != encoder);
	FLAC__ASSERT(0 != encoder->private_);
	FLAC__ASSERT(0 != encoder->protected_);
	return encoder->protected_->do_exhaustive_model_search;
}

FLAC_API uint32_t FLAC__stream_encoder_get_min_residual_partition_order(const FLAC__StreamEncoder *encoder)
{
	FLAC__ASSERT(0 != encoder);
	FLAC__ASSERT(0 != encoder->private_);
	FLAC__ASSERT(0 != encoder->protected_);
	return encoder->protected_->min_residual_partition_order;
}

FLAC_API uint32_t FLAC__stream_encoder_get_max_residual_partition_order(const FLAC__StreamEncoder *encoder)
{
	FLAC__ASSERT(0 != encoder);
	FLAC__ASSERT(0 != encoder->private_);
	FLAC__ASSERT(0 != encoder->protected_);
	return encoder->protected_->max_residual_partition_order;
}

FLAC_API uint32_t FLAC__stream_encoder_get_rice_parameter_search_dist(const FLAC__StreamEncoder *encoder)
{
	FLAC__ASSERT(0 != encoder);
	FLAC__ASSERT(0 != encoder->private_);
	FLAC__ASSERT(0 != encoder->protected_);
	return encoder->protected_->rice_parameter_search_dist;
}

FLAC_API FLAC__uint64 FLAC__stream_encoder_get_total_samples_estimate(const FLAC__StreamEncoder *encoder)
{
	FLAC__ASSERT(0 != encoder);
	FLAC__ASSERT(0 != encoder->private_);
	FLAC__ASSERT(0 != encoder->protected_);
	return encoder->protected_->total_samples_estimate;
}

FLAC_API FLAC__bool FLAC__stream_encoder_process(FLAC__StreamEncoder *encoder, const FLAC__int32 * const buffer[], uint32_t samples)
{
	uint32_t i, j = 0, channel;
	const uint32_t channels = encoder->protected_->channels, blocksize = encoder->protected_->blocksize;

	FLAC__ASSERT(0 != encoder);
	FLAC__ASSERT(0 != encoder->private_);
	FLAC__ASSERT(0 != encoder->protected_);
	FLAC__ASSERT(encoder->protected_->state == FLAC__STREAM_ENCODER_OK);

	do {
		const uint32_t n = flac_min(blocksize+OVERREAD_-encoder->private_->current_sample_number, samples-j);

		if(encoder->protected_->verify)
			append_to_verify_fifo_(&encoder->private_->verify.input_fifo, buffer, j, channels, n);

		for(channel = 0; channel < channels; channel++) {
			if (buffer[channel] == NULL) {
				return false;
			}
			memcpy(&encoder->private_->integer_signal[channel][encoder->private_->current_sample_number], &buffer[channel][j], sizeof(buffer[channel][0]) * n);
		}

		if(encoder->protected_->do_mid_side_stereo) {
			FLAC__ASSERT(channels == 2);
			/* "i <= blocksize" to overread 1 sample; see comment in OVERREAD_ decl */
			for(i = encoder->private_->current_sample_number; i <= blocksize && j < samples; i++, j++) {
				encoder->private_->integer_signal_mid_side[1][i] = buffer[0][j] - buffer[1][j];
				encoder->private_->integer_signal_mid_side[0][i] = (buffer[0][j] + buffer[1][j]) >> 1; /* NOTE: not the same as 'mid = (buffer[0][j] + buffer[1][j]) / 2' ! */
			}
		}
		else
			j += n;

		encoder->private_->current_sample_number += n;

		/* we only process if we have a full block + 1 extra sample; final block is always handled by FLAC__stream_encoder_finish() */
		if(encoder->private_->current_sample_number > blocksize) {
			FLAC__ASSERT(encoder->private_->current_sample_number == blocksize+OVERREAD_);
			FLAC__ASSERT(OVERREAD_ == 1); /* assert we only overread 1 sample which simplifies the rest of the code below */
			if(!process_frame_(encoder, /*is_fractional_block=*/false, /*is_last_block=*/false))
				return false;
			/* move unprocessed overread samples to beginnings of arrays */
			for(channel = 0; channel < channels; channel++)
				encoder->private_->integer_signal[channel][0] = encoder->private_->integer_signal[channel][blocksize];
			if(encoder->protected_->do_mid_side_stereo) {
				encoder->private_->integer_signal_mid_side[0][0] = encoder->private_->integer_signal_mid_side[0][blocksize];
				encoder->private_->integer_signal_mid_side[1][0] = encoder->private_->integer_signal_mid_side[1][blocksize];
			}
			encoder->private_->current_sample_number = 1;
		}
	} while(j < samples);

	return true;
}

FLAC_API FLAC__bool FLAC__stream_encoder_process_interleaved(FLAC__StreamEncoder *encoder, const FLAC__int32 buffer[], uint32_t samples)
{
	uint32_t i, j, k, channel;
	FLAC__int32 x, mid, side;
	const uint32_t channels = encoder->protected_->channels, blocksize = encoder->protected_->blocksize;

	FLAC__ASSERT(0 != encoder);
	FLAC__ASSERT(0 != encoder->private_);
	FLAC__ASSERT(0 != encoder->protected_);
	FLAC__ASSERT(encoder->protected_->state == FLAC__STREAM_ENCODER_OK);

	j = k = 0;
	/*
	 * we have several flavors of the same basic loop, optimized for
	 * different conditions:
	 */
	if(encoder->protected_->do_mid_side_stereo && channels == 2) {
		/*
		 * stereo coding: unroll channel loop
		 */
		do {
			if(encoder->protected_->verify)
				append_to_verify_fifo_interleaved_(&encoder->private_->verify.input_fifo, buffer, j, channels, flac_min(blocksize+OVERREAD_-encoder->private_->current_sample_number, samples-j));

			/* "i <= blocksize" to overread 1 sample; see comment in OVERREAD_ decl */
			for(i = encoder->private_->current_sample_number; i <= blocksize && j < samples; i++, j++) {
				encoder->private_->integer_signal[0][i] = mid = side = buffer[k++];
				x = buffer[k++];
				encoder->private_->integer_signal[1][i] = x;
				mid += x;
				side -= x;
				mid >>= 1; /* NOTE: not the same as 'mid = (left + right) / 2' ! */
				encoder->private_->integer_signal_mid_side[1][i] = side;
				encoder->private_->integer_signal_mid_side[0][i] = mid;
			}
			encoder->private_->current_sample_number = i;
			/* we only process if we have a full block + 1 extra sample; final block is always handled by FLAC__stream_encoder_finish() */
			if(i > blocksize) {
				if(!process_frame_(encoder, /*is_fractional_block=*/false, /*is_last_block=*/false))
					return false;
				/* move unprocessed overread samples to beginnings of arrays */
				FLAC__ASSERT(i == blocksize+OVERREAD_);
				FLAC__ASSERT(OVERREAD_ == 1); /* assert we only overread 1 sample which simplifies the rest of the code below */
				encoder->private_->integer_signal[0][0] = encoder->private_->integer_signal[0][blocksize];
				encoder->private_->integer_signal[1][0] = encoder->private_->integer_signal[1][blocksize];
				encoder->private_->integer_signal_mid_side[0][0] = encoder->private_->integer_signal_mid_side[0][blocksize];
				encoder->private_->integer_signal_mid_side[1][0] = encoder->private_->integer_signal_mid_side[1][blocksize];
				encoder->private_->current_sample_number = 1;
			}
		} while(j < samples);
	}
	else {
		/*
		 * independent channel coding: buffer each channel in inner loop
		 */
		do {
			if(encoder->protected_->verify)
				append_to_verify_fifo_interleaved_(&encoder->private_->verify.input_fifo, buffer, j, channels, flac_min(blocksize+OVERREAD_-encoder->private_->current_sample_number, samples-j));

			/* "i <= blocksize" to overread 1 sample; see comment in OVERREAD_ decl */
			for(i = encoder->private_->current_sample_number; i <= blocksize && j < samples; i++, j++) {
				for(channel = 0; channel < channels; channel++)
					encoder->private_->integer_signal[channel][i] = buffer[k++];
			}
			encoder->private_->current_sample_number = i;
			/* we only process if we have a full block + 1 extra sample; final block is always handled by FLAC__stream_encoder_finish() */
			if(i > blocksize) {
				if(!process_frame_(encoder, /*is_fractional_block=*/false, /*is_last_block=*/false))
					return false;
				/* move unprocessed overread samples to beginnings of arrays */
				FLAC__ASSERT(i == blocksize+OVERREAD_);
				FLAC__ASSERT(OVERREAD_ == 1); /* assert we only overread 1 sample which simplifies the rest of the code below */
				for(channel = 0; channel < channels; channel++)
					encoder->private_->integer_signal[channel][0] = encoder->private_->integer_signal[channel][blocksize];
				encoder->private_->current_sample_number = 1;
			}
		} while(j < samples);
	}

	return true;
}

/***********************************************************************
 *
 * Private class methods
 *
 ***********************************************************************/

void set_defaults_(FLAC__StreamEncoder *encoder)
{
	FLAC__ASSERT(0 != encoder);

#ifdef FLAC__MANDATORY_VERIFY_WHILE_ENCODING
	encoder->protected_->verify = true;
#else
	encoder->protected_->verify = false;
#endif
	encoder->protected_->streamable_subset = true;
	encoder->protected_->do_md5 = true;
	encoder->protected_->do_mid_side_stereo = false;
	encoder->protected_->loose_mid_side_stereo = false;
	encoder->protected_->channels = 2;
	encoder->protected_->bits_per_sample = 16;
	encoder->protected_->sample_rate = 44100;
	encoder->protected_->blocksize = 0;
#ifndef FLAC__INTEGER_ONLY_LIBRARY
	encoder->protected_->num_apodizations = 1;
	encoder->protected_->apodizations[0].type = FLAC__APODIZATION_TUKEY;
	encoder->protected_->apodizations[0].parameters.tukey.p = 0.5;
#endif
	encoder->protected_->max_lpc_order = 0;
	encoder->protected_->qlp_coeff_precision = 0;
	encoder->protected_->do_qlp_coeff_prec_search = false;
	encoder->protected_->do_exhaustive_model_search = false;
	encoder->protected_->do_escape_coding = false;
	encoder->protected_->min_residual_partition_order = 0;
	encoder->protected_->max_residual_partition_order = 0;
	encoder->protected_->rice_parameter_search_dist = 0;
	encoder->protected_->total_samples_estimate = 0;
	encoder->protected_->metadata = 0;
	encoder->protected_->num_metadata_blocks = 0;

	encoder->private_->seek_table = 0;
	encoder->private_->disable_constant_subframes = false;
	encoder->private_->disable_fixed_subframes = false;
	encoder->private_->disable_verbatim_subframes = false;
	encoder->private_->is_ogg = false;
	encoder->private_->read_callback = 0;
	encoder->private_->write_callback = 0;
	encoder->private_->seek_callback = 0;
	encoder->private_->tell_callback = 0;
	encoder->private_->metadata_callback = 0;
	encoder->private_->progress_callback = 0;
	encoder->private_->client_data = 0;

#if FLAC__HAS_OGG
	FLAC__ogg_encoder_aspect_set_defaults(&encoder->protected_->ogg_encoder_aspect);
#endif

	FLAC__stream_encoder_set_compression_level(encoder, 5);
}

void free_(FLAC__StreamEncoder *encoder)
{
	uint32_t i, channel;

	FLAC__ASSERT(0 != encoder);
	if(encoder->protected_->metadata) {
		free(encoder->protected_->metadata);
		encoder->protected_->metadata = 0;
		encoder->protected_->num_metadata_blocks = 0;
	}
	for(i = 0; i < encoder->protected_->channels; i++) {
		if(0 != encoder->private_->integer_signal_unaligned[i]) {
			free(encoder->private_->integer_signal_unaligned[i]);
			encoder->private_->integer_signal_unaligned[i] = 0;
		}
#ifndef FLAC__INTEGER_ONLY_LIBRARY
		if(0 != encoder->private_->real_signal_unaligned[i]) {
			free(encoder->private_->real_signal_unaligned[i]);
			encoder->private_->real_signal_unaligned[i] = 0;
		}
#endif
	}
	for(i = 0; i < 2; i++) {
		if(0 != encoder->private_->integer_signal_mid_side_unaligned[i]) {
			free(encoder->private_->integer_signal_mid_side_unaligned[i]);
			encoder->private_->integer_signal_mid_side_unaligned[i] = 0;
		}
#ifndef FLAC__INTEGER_ONLY_LIBRARY
		if(0 != encoder->private_->real_signal_mid_side_unaligned[i]) {
			free(encoder->private_->real_signal_mid_side_unaligned[i]);
			encoder->private_->real_signal_mid_side_unaligned[i] = 0;
		}
#endif
	}
#ifndef FLAC__INTEGER_ONLY_LIBRARY
	for(i = 0; i < encoder->protected_->num_apodizations; i++) {
		if(0 != encoder->private_->window_unaligned[i]) {
			free(encoder->private_->window_unaligned[i]);
			encoder->private_->window_unaligned[i] = 0;
		}
	}
	if(0 != encoder->private_->windowed_signal_unaligned) {
		free(encoder->private_->windowed_signal_unaligned);
		encoder->private_->windowed_signal_unaligned = 0;
	}
#endif
	for(channel = 0; channel < encoder->protected_->channels; channel++) {
		for(i = 0; i < 2; i++) {
			if(0 != encoder->private_->residual_workspace_unaligned[channel][i]) {
				free(encoder->private_->residual_workspace_unaligned[channel][i]);
				encoder->private_->residual_workspace_unaligned[channel][i] = 0;
			}
		}
	}
	for(channel = 0; channel < 2; channel++) {
		for(i = 0; i < 2; i++) {
			if(0 != encoder->private_->residual_workspace_mid_side_unaligned[channel][i]) {
				free(encoder->private_->residual_workspace_mid_side_unaligned[channel][i]);
				encoder->private_->residual_workspace_mid_side_unaligned[channel][i] = 0;
			}
		}
	}
	if(0 != encoder->private_->abs_residual_partition_sums_unaligned) {
		free(encoder->private_->abs_residual_partition_sums_unaligned);
		encoder->private_->abs_residual_partition_sums_unaligned = 0;
	}
	if(0 != encoder->private_->raw_bits_per_partition_unaligned) {
		free(encoder->private_->raw_bits_per_partition_unaligned);
		encoder->private_->raw_bits_per_partition_unaligned = 0;
	}
	if(encoder->protected_->verify) {
		for(i = 0; i < encoder->protected_->channels; i++) {
			if(0 != encoder->private_->verify.input_fifo.data[i]) {
				free(encoder->private_->verify.input_fifo.data[i]);
				encoder->private_->verify.input_fifo.data[i] = 0;
			}
		}
	}
	FLAC__bitwriter_free(encoder->private_->frame);
}

FLAC__bool resize_buffers_(FLAC__StreamEncoder *encoder, uint32_t new_blocksize)
{
	FLAC__bool ok;
	uint32_t i, channel;

	FLAC__ASSERT(new_blocksize > 0);
	FLAC__ASSERT(encoder->protected_->state == FLAC__STREAM_ENCODER_OK);
	FLAC__ASSERT(encoder->private_->current_sample_number == 0);

	/* To avoid excessive malloc'ing, we only grow the buffer; no shrinking. */
	if(new_blocksize <= encoder->private_->input_capacity)
		return true;

	ok = true;

	/* WATCHOUT: FLAC__lpc_compute_residual_from_qlp_coefficients_asm_ia32_mmx() and ..._intrin_sse2()
	 * require that the input arrays (in our case the integer signals)
	 * have a buffer of up to 3 zeroes in front (at negative indices) for
	 * alignment purposes; we use 4 in front to keep the data well-aligned.
	 */

	for(i = 0; ok && i < encoder->protected_->channels; i++) {
		ok = ok && FLAC__memory_alloc_aligned_int32_array(new_blocksize+4+OVERREAD_, &encoder->private_->integer_signal_unaligned[i], &encoder->private_->integer_signal[i]);
		memset(encoder->private_->integer_signal[i], 0, sizeof(FLAC__int32)*4);
		encoder->private_->integer_signal[i] += 4;
#ifndef FLAC__INTEGER_ONLY_LIBRARY
#if 0 /* @@@ currently unused */
		if(encoder->protected_->max_lpc_order > 0)
			ok = ok && FLAC__memory_alloc_aligned_real_array(new_blocksize+OVERREAD_, &encoder->private_->real_signal_unaligned[i], &encoder->private_->real_signal[i]);
#endif
#endif
	}
	for(i = 0; ok && i < 2; i++) {
		ok = ok && FLAC__memory_alloc_aligned_int32_array(new_blocksize+4+OVERREAD_, &encoder->private_->integer_signal_mid_side_unaligned[i], &encoder->private_->integer_signal_mid_side[i]);
		memset(encoder->private_->integer_signal_mid_side[i], 0, sizeof(FLAC__int32)*4);
		encoder->private_->integer_signal_mid_side[i] += 4;
#ifndef FLAC__INTEGER_ONLY_LIBRARY
#if 0 /* @@@ currently unused */
		if(encoder->protected_->max_lpc_order > 0)
			ok = ok && FLAC__memory_alloc_aligned_real_array(new_blocksize+OVERREAD_, &encoder->private_->real_signal_mid_side_unaligned[i], &encoder->private_->real_signal_mid_side[i]);
#endif
#endif
	}
#ifndef FLAC__INTEGER_ONLY_LIBRARY
	if(ok && encoder->protected_->max_lpc_order > 0) {
		for(i = 0; ok && i < encoder->protected_->num_apodizations; i++)
			ok = ok && FLAC__memory_alloc_aligned_real_array(new_blocksize, &encoder->private_->window_unaligned[i], &encoder->private_->window[i]);
		ok = ok && FLAC__memory_alloc_aligned_real_array(new_blocksize, &encoder->private_->windowed_signal_unaligned, &encoder->private_->windowed_signal);
	}
#endif
	for(channel = 0; ok && channel < encoder->protected_->channels; channel++) {
		for(i = 0; ok && i < 2; i++) {
			ok = ok && FLAC__memory_alloc_aligned_int32_array(new_blocksize, &encoder->private_->residual_workspace_unaligned[channel][i], &encoder->private_->residual_workspace[channel][i]);
		}
	}
	for(channel = 0; ok && channel < 2; channel++) {
		for(i = 0; ok && i < 2; i++) {
			ok = ok && FLAC__memory_alloc_aligned_int32_array(new_blocksize, &encoder->private_->residual_workspace_mid_side_unaligned[channel][i], &encoder->private_->residual_workspace_mid_side[channel][i]);
		}
	}
	/* the *2 is an approximation to the series 1 + 1/2 + 1/4 + ... that sums tree occupies in a flat array */
	/*@@@ new_blocksize*2 is too pessimistic, but to fix, we need smarter logic because a smaller new_blocksize can actually increase the # of partitions; would require moving this out into a separate function, then checking its capacity against the need of the current blocksize&min/max_partition_order (and maybe predictor order) */
	ok = ok && FLAC__memory_alloc_aligned_uint64_array(new_blocksize * 2, &encoder->private_->abs_residual_partition_sums_unaligned, &encoder->private_->abs_residual_partition_sums);
	if(encoder->protected_->do_escape_coding)
		ok = ok && FLAC__memory_alloc_aligned_unsigned_array(new_blocksize * 2, &encoder->private_->raw_bits_per_partition_unaligned, &encoder->private_->raw_bits_per_partition);

	/* now adjust the windows if the blocksize has changed */
#ifndef FLAC__INTEGER_ONLY_LIBRARY
	if(ok && new_blocksize != encoder->private_->input_capacity && encoder->protected_->max_lpc_order > 0) {
		for(i = 0; ok && i < encoder->protected_->num_apodizations; i++) {
			switch(encoder->protected_->apodizations[i].type) {
				case FLAC__APODIZATION_BARTLETT:
					FLAC__window_bartlett(encoder->private_->window[i], new_blocksize);
					break;
				case FLAC__APODIZATION_BARTLETT_HANN:
					FLAC__window_bartlett_hann(encoder->private_->window[i], new_blocksize);
					break;
				case FLAC__APODIZATION_BLACKMAN:
					FLAC__window_blackman(encoder->private_->window[i], new_blocksize);
					break;
				case FLAC__APODIZATION_BLACKMAN_HARRIS_4TERM_92DB_SIDELOBE:
					FLAC__window_blackman_harris_4term_92db_sidelobe(encoder->private_->window[i], new_blocksize);
					break;
				case FLAC__APODIZATION_CONNES:
					FLAC__window_connes(encoder->private_->window[i], new_blocksize);
					break;
				case FLAC__APODIZATION_FLATTOP:
					FLAC__window_flattop(encoder->private_->window[i], new_blocksize);
					break;
				case FLAC__APODIZATION_GAUSS:
					FLAC__window_gauss(encoder->private_->window[i], new_blocksize, encoder->protected_->apodizations[i].parameters.gauss.stddev);
					break;
				case FLAC__APODIZATION_HAMMING:
					FLAC__window_hamming(encoder->private_->window[i], new_blocksize);
					break;
				case FLAC__APODIZATION_HANN:
					FLAC__window_hann(encoder->private_->window[i], new_blocksize);
					break;
				case FLAC__APODIZATION_KAISER_BESSEL:
					FLAC__window_kaiser_bessel(encoder->private_->window[i], new_blocksize);
					break;
				case FLAC__APODIZATION_NUTTALL:
					FLAC__window_nuttall(encoder->private_->window[i], new_blocksize);
					break;
				case FLAC__APODIZATION_RECTANGLE:
					FLAC__window_rectangle(encoder->private_->window[i], new_blocksize);
					break;
				case FLAC__APODIZATION_TRIANGLE:
					FLAC__window_triangle(encoder->private_->window[i], new_blocksize);
					break;
				case FLAC__APODIZATION_TUKEY:
					FLAC__window_tukey(encoder->private_->window[i], new_blocksize, encoder->protected_->apodizations[i].parameters.tukey.p);
					break;
				case FLAC__APODIZATION_PARTIAL_TUKEY:
					FLAC__window_partial_tukey(encoder->private_->window[i], new_blocksize, encoder->protected_->apodizations[i].parameters.multiple_tukey.p, encoder->protected_->apodizations[i].parameters.multiple_tukey.start, encoder->protected_->apodizations[i].parameters.multiple_tukey.end);
					break;
				case FLAC__APODIZATION_PUNCHOUT_TUKEY:
					FLAC__window_punchout_tukey(encoder->private_->window[i], new_blocksize, encoder->protected_->apodizations[i].parameters.multiple_tukey.p, encoder->protected_->apodizations[i].parameters.multiple_tukey.start, encoder->protected_->apodizations[i].parameters.multiple_tukey.end);
					break;
				case FLAC__APODIZATION_WELCH:
					FLAC__window_welch(encoder->private_->window[i], new_blocksize);
					break;
				default:
					FLAC__ASSERT(0);
					/* double protection */
					FLAC__window_hann(encoder->private_->window[i], new_blocksize);
					break;
			}
		}
	}
#endif

	if(ok)
		encoder->private_->input_capacity = new_blocksize;
	else
		encoder->protected_->state = FLAC__STREAM_ENCODER_MEMORY_ALLOCATION_ERROR;

	return ok;
}

FLAC__bool write_bitbuffer_(FLAC__StreamEncoder *encoder, uint32_t samples, FLAC__bool is_last_block)
{
	const FLAC__byte *buffer;
	size_t bytes;

	FLAC__ASSERT(FLAC__bitwriter_is_byte_aligned(encoder->private_->frame));

	if(!FLAC__bitwriter_get_buffer(encoder->private_->frame, &buffer, &bytes)) {
		encoder->protected_->state = FLAC__STREAM_ENCODER_MEMORY_ALLOCATION_ERROR;
		return false;
	}

	if(encoder->protected_->verify) {
		encoder->private_->verify.output.data = buffer;
		encoder->private_->verify.output.bytes = bytes;
		if(encoder->private_->verify.state_hint == ENCODER_IN_MAGIC) {
			encoder->private_->verify.needs_magic_hack = true;
		}
		else {
			if(!FLAC__stream_decoder_process_single(encoder->private_->verify.decoder)) {
				FLAC__bitwriter_release_buffer(encoder->private_->frame);
				FLAC__bitwriter_clear(encoder->private_->frame);
				if(encoder->protected_->state != FLAC__STREAM_ENCODER_VERIFY_MISMATCH_IN_AUDIO_DATA)
					encoder->protected_->state = FLAC__STREAM_ENCODER_VERIFY_DECODER_ERROR;
				return false;
			}
		}
	}

	if(write_frame_(encoder, buffer, bytes, samples, is_last_block) != FLAC__STREAM_ENCODER_WRITE_STATUS_OK) {
		FLAC__bitwriter_release_buffer(encoder->private_->frame);
		FLAC__bitwriter_clear(encoder->private_->frame);
		encoder->protected_->state = FLAC__STREAM_ENCODER_CLIENT_ERROR;
		return false;
	}

	FLAC__bitwriter_release_buffer(encoder->private_->frame);
	FLAC__bitwriter_clear(encoder->private_->frame);

	if(samples > 0) {
		encoder->private_->streaminfo.data.stream_info.min_framesize = flac_min(bytes, encoder->private_->streaminfo.data.stream_info.min_framesize);
		encoder->private_->streaminfo.data.stream_info.max_framesize = flac_max(bytes, encoder->private_->streaminfo.data.stream_info.max_framesize);
	}

	return true;
}

FLAC__StreamEncoderWriteStatus write_frame_(FLAC__StreamEncoder *encoder, const FLAC__byte buffer[], size_t bytes, uint32_t samples, FLAC__bool is_last_block)
{
	FLAC__StreamEncoderWriteStatus status;
	FLAC__uint64 output_position = 0;

#if FLAC__HAS_OGG == 0
	(void)is_last_block;
#endif

	/* FLAC__STREAM_ENCODER_TELL_STATUS_UNSUPPORTED just means we didn't get the offset; no error */
	if(encoder->private_->tell_callback && encoder->private_->tell_callback(encoder, &output_position, encoder->private_->client_data) == FLAC__STREAM_ENCODER_TELL_STATUS_ERROR) {
		encoder->protected_->state = FLAC__STREAM_ENCODER_CLIENT_ERROR;
		return FLAC__STREAM_ENCODER_WRITE_STATUS_FATAL_ERROR;
	}

	/*
	 * Watch for the STREAMINFO block and first SEEKTABLE block to go by and store their offsets.
	 */
	if(samples == 0) {
		FLAC__MetadataType type = (buffer[0] & 0x7f);
		if(type == FLAC__METADATA_TYPE_STREAMINFO)
			encoder->protected_->streaminfo_offset = output_position;
		else if(type == FLAC__METADATA_TYPE_SEEKTABLE && encoder->protected_->seektable_offset == 0)
			encoder->protected_->seektable_offset = output_position;
	}

	/*
	 * Mark the current seek point if hit (if audio_offset == 0 that
	 * means we're still writing metadata and haven't hit the first
	 * frame yet)
	 */
	if(0 != encoder->private_->seek_table && encoder->protected_->audio_offset > 0 && encoder->private_->seek_table->num_points > 0) {
		const uint32_t blocksize = FLAC__stream_encoder_get_blocksize(encoder);
		const FLAC__uint64 frame_first_sample = encoder->private_->samples_written;
		const FLAC__uint64 frame_last_sample = frame_first_sample + (FLAC__uint64)blocksize - 1;
		FLAC__uint64 test_sample;
		uint32_t i;
		for(i = encoder->private_->first_seekpoint_to_check; i < encoder->private_->seek_table->num_points; i++) {
			test_sample = encoder->private_->seek_table->points[i].sample_number;
			if(test_sample > frame_last_sample) {
				break;
			}
			else if(test_sample >= frame_first_sample) {
				encoder->private_->seek_table->points[i].sample_number = frame_first_sample;
				encoder->private_->seek_table->points[i].stream_offset = output_position - encoder->protected_->audio_offset;
				encoder->private_->seek_table->points[i].frame_samples = blocksize;
				encoder->private_->first_seekpoint_to_check++;
				/* DO NOT: "break;" and here's why:
				 * The seektable template may contain more than one target
				 * sample for any given frame; we will keep looping, generating
				 * duplicate seekpoints for them, and we'll clean it up later,
				 * just before writing the seektable back to the metadata.
				 */
			}
			else {
				encoder->private_->first_seekpoint_to_check++;
			}
		}
	}

#if FLAC__HAS_OGG
	if(encoder->private_->is_ogg) {
		status = FLAC__ogg_encoder_aspect_write_callback_wrapper(
			&encoder->protected_->ogg_encoder_aspect,
			buffer,
			bytes,
			samples,
			encoder->private_->current_frame_number,
			is_last_block,
			(FLAC__OggEncoderAspectWriteCallbackProxy)encoder->private_->write_callback,
			encoder,
			encoder->private_->client_data
		);
	}
	else
#endif
	status = encoder->private_->write_callback(encoder, buffer, bytes, samples, encoder->private_->current_frame_number, encoder->private_->client_data);

	if(status == FLAC__STREAM_ENCODER_WRITE_STATUS_OK) {
		encoder->private_->bytes_written += bytes;
		encoder->private_->samples_written += samples;
		/* we keep a high watermark on the number of frames written because
		 * when the encoder goes back to write metadata, 'current_frame'
		 * will drop back to 0.
		 */
		encoder->private_->frames_written = flac_max(encoder->private_->frames_written, encoder->private_->current_frame_number+1);
	}
	else
		encoder->protected_->state = FLAC__STREAM_ENCODER_CLIENT_ERROR;

	return status;
}

/* Gets called when the encoding process has finished so that we can update the STREAMINFO and SEEKTABLE blocks.  */
void update_metadata_(const FLAC__StreamEncoder *encoder)
{
	FLAC__byte b[flac_max(6u, FLAC__STREAM_METADATA_SEEKPOINT_LENGTH)];
	const FLAC__StreamMetadata *metadata = &encoder->private_->streaminfo;
	const FLAC__uint64 samples = metadata->data.stream_info.total_samples;
	const uint32_t min_framesize = metadata->data.stream_info.min_framesize;
	const uint32_t max_framesize = metadata->data.stream_info.max_framesize;
	const uint32_t bps = metadata->data.stream_info.bits_per_sample;
	FLAC__StreamEncoderSeekStatus seek_status;

	FLAC__ASSERT(metadata->type == FLAC__METADATA_TYPE_STREAMINFO);

	/* All this is based on intimate knowledge of the stream header
	 * layout, but a change to the header format that would break this
	 * would also break all streams encoded in the previous format.
	 */

	/*
	 * Write MD5 signature
	 */
	{
		const uint32_t md5_offset =
			FLAC__STREAM_METADATA_HEADER_LENGTH +
			(
				FLAC__STREAM_METADATA_STREAMINFO_MIN_BLOCK_SIZE_LEN +
				FLAC__STREAM_METADATA_STREAMINFO_MAX_BLOCK_SIZE_LEN +
				FLAC__STREAM_METADATA_STREAMINFO_MIN_FRAME_SIZE_LEN +
				FLAC__STREAM_METADATA_STREAMINFO_MAX_FRAME_SIZE_LEN +
				FLAC__STREAM_METADATA_STREAMINFO_SAMPLE_RATE_LEN +
				FLAC__STREAM_METADATA_STREAMINFO_CHANNELS_LEN +
				FLAC__STREAM_METADATA_STREAMINFO_BITS_PER_SAMPLE_LEN +
				FLAC__STREAM_METADATA_STREAMINFO_TOTAL_SAMPLES_LEN
			) / 8;

		if((seek_status = encoder->private_->seek_callback(encoder, encoder->protected_->streaminfo_offset + md5_offset, encoder->private_->client_data)) != FLAC__STREAM_ENCODER_SEEK_STATUS_OK) {
			if(seek_status == FLAC__STREAM_ENCODER_SEEK_STATUS_ERROR)
				encoder->protected_->state = FLAC__STREAM_ENCODER_CLIENT_ERROR;
			return;
		}
		if(encoder->private_->write_callback(encoder, metadata->data.stream_info.md5sum, 16, 0, 0, encoder->private_->client_data) != FLAC__STREAM_ENCODER_WRITE_STATUS_OK) {
			encoder->protected_->state = FLAC__STREAM_ENCODER_CLIENT_ERROR;
			return;
		}
	}

	/*
	 * Write total samples
	 */
	{
		const uint32_t total_samples_byte_offset =
			FLAC__STREAM_METADATA_HEADER_LENGTH +
			(
				FLAC__STREAM_METADATA_STREAMINFO_MIN_BLOCK_SIZE_LEN +
				FLAC__STREAM_METADATA_STREAMINFO_MAX_BLOCK_SIZE_LEN +
				FLAC__STREAM_METADATA_STREAMINFO_MIN_FRAME_SIZE_LEN +
				FLAC__STREAM_METADATA_STREAMINFO_MAX_FRAME_SIZE_LEN +
				FLAC__STREAM_METADATA_STREAMINFO_SAMPLE_RATE_LEN +
				FLAC__STREAM_METADATA_STREAMINFO_CHANNELS_LEN +
				FLAC__STREAM_METADATA_STREAMINFO_BITS_PER_SAMPLE_LEN
				- 4
			) / 8;

		b[0] = ((FLAC__byte)(bps-1) << 4) | (FLAC__byte)((samples >> 32) & 0x0F);
		b[1] = (FLAC__byte)((samples >> 24) & 0xFF);
		b[2] = (FLAC__byte)((samples >> 16) & 0xFF);
		b[3] = (FLAC__byte)((samples >> 8) & 0xFF);
		b[4] = (FLAC__byte)(samples & 0xFF);
		if((seek_status = encoder->private_->seek_callback(encoder, encoder->protected_->streaminfo_offset + total_samples_byte_offset, encoder->private_->client_data)) != FLAC__STREAM_ENCODER_SEEK_STATUS_OK) {
			if(seek_status == FLAC__STREAM_ENCODER_SEEK_STATUS_ERROR)
				encoder->protected_->state = FLAC__STREAM_ENCODER_CLIENT_ERROR;
			return;
		}
		if(encoder->private_->write_callback(encoder, b, 5, 0, 0, encoder->private_->client_data) != FLAC__STREAM_ENCODER_WRITE_STATUS_OK) {
			encoder->protected_->state = FLAC__STREAM_ENCODER_CLIENT_ERROR;
			return;
		}
	}

	/*
	 * Write min/max framesize
	 */
	{
		const uint32_t min_framesize_offset =
			FLAC__STREAM_METADATA_HEADER_LENGTH +
			(
				FLAC__STREAM_METADATA_STREAMINFO_MIN_BLOCK_SIZE_LEN +
				FLAC__STREAM_METADATA_STREAMINFO_MAX_BLOCK_SIZE_LEN
			) / 8;

		b[0] = (FLAC__byte)((min_framesize >> 16) & 0xFF);
		b[1] = (FLAC__byte)((min_framesize >> 8) & 0xFF);
		b[2] = (FLAC__byte)(min_framesize & 0xFF);
		b[3] = (FLAC__byte)((max_framesize >> 16) & 0xFF);
		b[4] = (FLAC__byte)((max_framesize >> 8) & 0xFF);
		b[5] = (FLAC__byte)(max_framesize & 0xFF);
		if((seek_status = encoder->private_->seek_callback(encoder, encoder->protected_->streaminfo_offset + min_framesize_offset, encoder->private_->client_data)) != FLAC__STREAM_ENCODER_SEEK_STATUS_OK) {
			if(seek_status == FLAC__STREAM_ENCODER_SEEK_STATUS_ERROR)
				encoder->protected_->state = FLAC__STREAM_ENCODER_CLIENT_ERROR;
			return;
		}
		if(encoder->private_->write_callback(encoder, b, 6, 0, 0, encoder->private_->client_data) != FLAC__STREAM_ENCODER_WRITE_STATUS_OK) {
			encoder->protected_->state = FLAC__STREAM_ENCODER_CLIENT_ERROR;
			return;
		}
	}

	/*
	 * Write seektable
	 */
	if(0 != encoder->private_->seek_table && encoder->private_->seek_table->num_points > 0 && encoder->protected_->seektable_offset > 0) {
		uint32_t i;

		FLAC__format_seektable_sort(encoder->private_->seek_table);

		FLAC__ASSERT(FLAC__format_seektable_is_legal(encoder->private_->seek_table));

		if((seek_status = encoder->private_->seek_callback(encoder, encoder->protected_->seektable_offset + FLAC__STREAM_METADATA_HEADER_LENGTH, encoder->private_->client_data)) != FLAC__STREAM_ENCODER_SEEK_STATUS_OK) {
			if(seek_status == FLAC__STREAM_ENCODER_SEEK_STATUS_ERROR)
				encoder->protected_->state = FLAC__STREAM_ENCODER_CLIENT_ERROR;
			return;
		}

		for(i = 0; i < encoder->private_->seek_table->num_points; i++) {
			FLAC__uint64 xx;
			uint32_t x;
			xx = encoder->private_->seek_table->points[i].sample_number;
			b[7] = (FLAC__byte)xx; xx >>= 8;
			b[6] = (FLAC__byte)xx; xx >>= 8;
			b[5] = (FLAC__byte)xx; xx >>= 8;
			b[4] = (FLAC__byte)xx; xx >>= 8;
			b[3] = (FLAC__byte)xx; xx >>= 8;
			b[2] = (FLAC__byte)xx; xx >>= 8;
			b[1] = (FLAC__byte)xx; xx >>= 8;
			b[0] = (FLAC__byte)xx; xx >>= 8;
			xx = encoder->private_->seek_table->points[i].stream_offset;
			b[15] = (FLAC__byte)xx; xx >>= 8;
			b[14] = (FLAC__byte)xx; xx >>= 8;
			b[13] = (FLAC__byte)xx; xx >>= 8;
			b[12] = (FLAC__byte)xx; xx >>= 8;
			b[11] = (FLAC__byte)xx; xx >>= 8;
			b[10] = (FLAC__byte)xx; xx >>= 8;
			b[9] = (FLAC__byte)xx; xx >>= 8;
			b[8] = (FLAC__byte)xx; xx >>= 8;
			x = encoder->private_->seek_table->points[i].frame_samples;
			b[17] = (FLAC__byte)x; x >>= 8;
			b[16] = (FLAC__byte)x; x >>= 8;
			if(encoder->private_->write_callback(encoder, b, 18, 0, 0, encoder->private_->client_data) != FLAC__STREAM_ENCODER_WRITE_STATUS_OK) {
				encoder->protected_->state = FLAC__STREAM_ENCODER_CLIENT_ERROR;
				return;
			}
		}
	}
}

#if FLAC__HAS_OGG
/* Gets called when the encoding process has finished so that we can update the STREAMINFO and SEEKTABLE blocks.  */
void update_ogg_metadata_(FLAC__StreamEncoder *encoder)
{
	/* the # of bytes in the 1st packet that precede the STREAMINFO */
	static const uint32_t FIRST_OGG_PACKET_STREAMINFO_PREFIX_LENGTH =
		FLAC__OGG_MAPPING_PACKET_TYPE_LENGTH +
		FLAC__OGG_MAPPING_MAGIC_LENGTH +
		FLAC__OGG_MAPPING_VERSION_MAJOR_LENGTH +
		FLAC__OGG_MAPPING_VERSION_MINOR_LENGTH +
		FLAC__OGG_MAPPING_NUM_HEADERS_LENGTH +
		FLAC__STREAM_SYNC_LENGTH
	;
	FLAC__byte b[flac_max(6u, FLAC__STREAM_METADATA_SEEKPOINT_LENGTH)];
	const FLAC__StreamMetadata *metadata = &encoder->private_->streaminfo;
	const FLAC__uint64 samples = metadata->data.stream_info.total_samples;
	const uint32_t min_framesize = metadata->data.stream_info.min_framesize;
	const uint32_t max_framesize = metadata->data.stream_info.max_framesize;
	ogg_page page;

	FLAC__ASSERT(metadata->type == FLAC__METADATA_TYPE_STREAMINFO);
	FLAC__ASSERT(0 != encoder->private_->seek_callback);

	/* Pre-check that client supports seeking, since we don't want the
	 * ogg_helper code to ever have to deal with this condition.
	 */
	if(encoder->private_->seek_callback(encoder, 0, encoder->private_->client_data) == FLAC__STREAM_ENCODER_SEEK_STATUS_UNSUPPORTED)
		return;

	/* All this is based on intimate knowledge of the stream header
	 * layout, but a change to the header format that would break this
	 * would also break all streams encoded in the previous format.
	 */

	/**
	 ** Write STREAMINFO stats
	 **/
	simple_ogg_page__init(&page);
	if(!simple_ogg_page__get_at(encoder, encoder->protected_->streaminfo_offset, &page, encoder->private_->seek_callback, encoder->private_->read_callback, encoder->private_->client_data)) {
		simple_ogg_page__clear(&page);
		return; /* state already set */
	}

	/*
	 * Write MD5 signature
	 */
	{
		const uint32_t md5_offset =
			FIRST_OGG_PACKET_STREAMINFO_PREFIX_LENGTH +
			FLAC__STREAM_METADATA_HEADER_LENGTH +
			(
				FLAC__STREAM_METADATA_STREAMINFO_MIN_BLOCK_SIZE_LEN +
				FLAC__STREAM_METADATA_STREAMINFO_MAX_BLOCK_SIZE_LEN +
				FLAC__STREAM_METADATA_STREAMINFO_MIN_FRAME_SIZE_LEN +
				FLAC__STREAM_METADATA_STREAMINFO_MAX_FRAME_SIZE_LEN +
				FLAC__STREAM_METADATA_STREAMINFO_SAMPLE_RATE_LEN +
				FLAC__STREAM_METADATA_STREAMINFO_CHANNELS_LEN +
				FLAC__STREAM_METADATA_STREAMINFO_BITS_PER_SAMPLE_LEN +
				FLAC__STREAM_METADATA_STREAMINFO_TOTAL_SAMPLES_LEN
			) / 8;

		if(md5_offset + 16 > (uint32_t)page.body_len) {
			encoder->protected_->state = FLAC__STREAM_ENCODER_OGG_ERROR;
			simple_ogg_page__clear(&page);
			return;
		}
		memcpy(page.body + md5_offset, metadata->data.stream_info.md5sum, 16);
	}

	/*
	 * Write total samples
	 */
	{
		const uint32_t total_samples_byte_offset =
			FIRST_OGG_PACKET_STREAMINFO_PREFIX_LENGTH +
			FLAC__STREAM_METADATA_HEADER_LENGTH +
			(
				FLAC__STREAM_METADATA_STREAMINFO_MIN_BLOCK_SIZE_LEN +
				FLAC__STREAM_METADATA_STREAMINFO_MAX_BLOCK_SIZE_LEN +
				FLAC__STREAM_METADATA_STREAMINFO_MIN_FRAME_SIZE_LEN +
				FLAC__STREAM_METADATA_STREAMINFO_MAX_FRAME_SIZE_LEN +
				FLAC__STREAM_METADATA_STREAMINFO_SAMPLE_RATE_LEN +
				FLAC__STREAM_METADATA_STREAMINFO_CHANNELS_LEN +
				FLAC__STREAM_METADATA_STREAMINFO_BITS_PER_SAMPLE_LEN
				- 4
			) / 8;

		if(total_samples_byte_offset + 5 > (uint32_t)page.body_len) {
			encoder->protected_->state = FLAC__STREAM_ENCODER_OGG_ERROR;
			simple_ogg_page__clear(&page);
			return;
		}
		b[0] = (FLAC__byte)page.body[total_samples_byte_offset] & 0xF0;
		b[0] |= (FLAC__byte)((samples >> 32) & 0x0F);
		b[1] = (FLAC__byte)((samples >> 24) & 0xFF);
		b[2] = (FLAC__byte)((samples >> 16) & 0xFF);
		b[3] = (FLAC__byte)((samples >> 8) & 0xFF);
		b[4] = (FLAC__byte)(samples & 0xFF);
		memcpy(page.body + total_samples_byte_offset, b, 5);
	}

	/*
	 * Write min/max framesize
	 */
	{
		const uint32_t min_framesize_offset =
			FIRST_OGG_PACKET_STREAMINFO_PREFIX_LENGTH +
			FLAC__STREAM_METADATA_HEADER_LENGTH +
			(
				FLAC__STREAM_METADATA_STREAMINFO_MIN_BLOCK_SIZE_LEN +
				FLAC__STREAM_METADATA_STREAMINFO_MAX_BLOCK_SIZE_LEN
			) / 8;

		if(min_framesize_offset + 6 > (uint32_t)page.body_len) {
			encoder->protected_->state = FLAC__STREAM_ENCODER_OGG_ERROR;
			simple_ogg_page__clear(&page);
			return;
		}
		b[0] = (FLAC__byte)((min_framesize >> 16) & 0xFF);
		b[1] = (FLAC__byte)((min_framesize >> 8) & 0xFF);
		b[2] = (FLAC__byte)(min_framesize & 0xFF);
		b[3] = (FLAC__byte)((max_framesize >> 16) & 0xFF);
		b[4] = (FLAC__byte)((max_framesize >> 8) & 0xFF);
		b[5] = (FLAC__byte)(max_framesize & 0xFF);
		memcpy(page.body + min_framesize_offset, b, 6);
	}
	if(!simple_ogg_page__set_at(encoder, encoder->protected_->streaminfo_offset, &page, encoder->private_->seek_callback, encoder->private_->write_callback, encoder->private_->client_data)) {
		simple_ogg_page__clear(&page);
		return; /* state already set */
	}
	simple_ogg_page__clear(&page);

	/*
	 * Write seektable
	 */
	if(0 != encoder->private_->seek_table && encoder->private_->seek_table->num_points > 0 && encoder->protected_->seektable_offset > 0) {
		uint32_t i;
		FLAC__byte *p;

		FLAC__format_seektable_sort(encoder->private_->seek_table);

		FLAC__ASSERT(FLAC__format_seektable_is_legal(encoder->private_->seek_table));

		simple_ogg_page__init(&page);
		if(!simple_ogg_page__get_at(encoder, encoder->protected_->seektable_offset, &page, encoder->private_->seek_callback, encoder->private_->read_callback, encoder->private_->client_data)) {
			simple_ogg_page__clear(&page);
			return; /* state already set */
		}

		if((FLAC__STREAM_METADATA_HEADER_LENGTH + 18*encoder->private_->seek_table->num_points) != (uint32_t)page.body_len) {
			encoder->protected_->state = FLAC__STREAM_ENCODER_OGG_ERROR;
			simple_ogg_page__clear(&page);
			return;
		}

		for(i = 0, p = page.body + FLAC__STREAM_METADATA_HEADER_LENGTH; i < encoder->private_->seek_table->num_points; i++, p += 18) {
			FLAC__uint64 xx;
			uint32_t x;
			xx = encoder->private_->seek_table->points[i].sample_number;
			b[7] = (FLAC__byte)xx; xx >>= 8;
			b[6] = (FLAC__byte)xx; xx >>= 8;
			b[5] = (FLAC__byte)xx; xx >>= 8;
			b[4] = (FLAC__byte)xx; xx >>= 8;
			b[3] = (FLAC__byte)xx; xx >>= 8;
			b[2] = (FLAC__byte)xx; xx >>= 8;
			b[1] = (FLAC__byte)xx; xx >>= 8;
			b[0] = (FLAC__byte)xx; xx >>= 8;
			xx = encoder->private_->seek_table->points[i].stream_offset;
			b[15] = (FLAC__byte)xx; xx >>= 8;
			b[14] = (FLAC__byte)xx; xx >>= 8;
			b[13] = (FLAC__byte)xx; xx >>= 8;
			b[12] = (FLAC__byte)xx; xx >>= 8;
			b[11] = (FLAC__byte)xx; xx >>= 8;
			b[10] = (FLAC__byte)xx; xx >>= 8;
			b[9] = (FLAC__byte)xx; xx >>= 8;
			b[8] = (FLAC__byte)xx; xx >>= 8;
			x = encoder->private_->seek_table->points[i].frame_samples;
			b[17] = (FLAC__byte)x; x >>= 8;
			b[16] = (FLAC__byte)x; x >>= 8;
			memcpy(p, b, 18);
		}

		if(!simple_ogg_page__set_at(encoder, encoder->protected_->seektable_offset, &page, encoder->private_->seek_callback, encoder->private_->write_callback, encoder->private_->client_data)) {
			simple_ogg_page__clear(&page);
			return; /* state already set */
		}
		simple_ogg_page__clear(&page);
	}
}
#endif

FLAC__bool process_frame_(FLAC__StreamEncoder *encoder, FLAC__bool is_fractional_block, FLAC__bool is_last_block)
{
	FLAC__uint16 crc;
	FLAC__ASSERT(encoder->protected_->state == FLAC__STREAM_ENCODER_OK);

	/*
	 * Accumulate raw signal to the MD5 signature
	 */
	if(encoder->protected_->do_md5 && !FLAC__MD5Accumulate(&encoder->private_->md5context, (const FLAC__int32 * const *)encoder->private_->integer_signal, encoder->protected_->channels, encoder->protected_->blocksize, (encoder->protected_->bits_per_sample+7) / 8)) {
		encoder->protected_->state = FLAC__STREAM_ENCODER_MEMORY_ALLOCATION_ERROR;
		return false;
	}

	/*
	 * Process the frame header and subframes into the frame bitbuffer
	 */
	if(!process_subframes_(encoder, is_fractional_block)) {
		/* the above function sets the state for us in case of an error */
		return false;
	}

	/*
	 * Zero-pad the frame to a byte_boundary
	 */
	if(!FLAC__bitwriter_zero_pad_to_byte_boundary(encoder->private_->frame)) {
		encoder->protected_->state = FLAC__STREAM_ENCODER_MEMORY_ALLOCATION_ERROR;
		return false;
	}

	/*
	 * CRC-16 the whole thing
	 */
	FLAC__ASSERT(FLAC__bitwriter_is_byte_aligned(encoder->private_->frame));
	if(
		!FLAC__bitwriter_get_write_crc16(encoder->private_->frame, &crc) ||
		!FLAC__bitwriter_write_raw_uint32(encoder->private_->frame, crc, FLAC__FRAME_FOOTER_CRC_LEN)
	) {
		encoder->protected_->state = FLAC__STREAM_ENCODER_MEMORY_ALLOCATION_ERROR;
		return false;
	}

	/*
	 * Write it
	 */
	if(!write_bitbuffer_(encoder, encoder->protected_->blocksize, is_last_block)) {
		/* the above function sets the state for us in case of an error */
		return false;
	}

	/*
	 * Get ready for the next frame
	 */
	encoder->private_->current_sample_number = 0;
	encoder->private_->current_frame_number++;
	encoder->private_->streaminfo.data.stream_info.total_samples += (FLAC__uint64)encoder->protected_->blocksize;

	return true;
}

FLAC__bool process_subframes_(FLAC__StreamEncoder *encoder, FLAC__bool is_fractional_block)
{
	FLAC__FrameHeader frame_header;
	uint32_t channel, min_partition_order = encoder->protected_->min_residual_partition_order, max_partition_order;
	FLAC__bool do_independent, do_mid_side;

	/*
	 * Calculate the min,max Rice partition orders
	 */
	if(is_fractional_block) {
		max_partition_order = 0;
	}
	else {
		max_partition_order = FLAC__format_get_max_rice_partition_order_from_blocksize(encoder->protected_->blocksize);
		max_partition_order = flac_min(max_partition_order, encoder->protected_->max_residual_partition_order);
	}
	min_partition_order = flac_min(min_partition_order, max_partition_order);

	/*
	 * Setup the frame
	 */
	frame_header.blocksize = encoder->protected_->blocksize;
	frame_header.sample_rate = encoder->protected_->sample_rate;
	frame_header.channels = encoder->protected_->channels;
	frame_header.channel_assignment = FLAC__CHANNEL_ASSIGNMENT_INDEPENDENT; /* the default unless the encoder determines otherwise */
	frame_header.bits_per_sample = encoder->protected_->bits_per_sample;
	frame_header.number_type = FLAC__FRAME_NUMBER_TYPE_FRAME_NUMBER;
	frame_header.number.frame_number = encoder->private_->current_frame_number;

	/*
	 * Figure out what channel assignments to try
	 */
	if(encoder->protected_->do_mid_side_stereo) {
		if(encoder->protected_->loose_mid_side_stereo) {
			if(encoder->private_->loose_mid_side_stereo_frame_count == 0) {
				do_independent = true;
				do_mid_side = true;
			}
			else {
				do_independent = (encoder->private_->last_channel_assignment == FLAC__CHANNEL_ASSIGNMENT_INDEPENDENT);
				do_mid_side = !do_independent;
			}
		}
		else {
			do_independent = true;
			do_mid_side = true;
		}
	}
	else {
		do_independent = true;
		do_mid_side = false;
	}

	FLAC__ASSERT(do_independent || do_mid_side);

	/*
	 * Check for wasted bits; set effective bps for each subframe
	 */
	if(do_independent) {
		for(channel = 0; channel < encoder->protected_->channels; channel++) {
			uint32_t w = get_wasted_bits_(encoder->private_->integer_signal[channel], encoder->protected_->blocksize);
			if (w > encoder->protected_->bits_per_sample) {
				w = encoder->protected_->bits_per_sample;
			}
			encoder->private_->subframe_workspace[channel][0].wasted_bits = encoder->private_->subframe_workspace[channel][1].wasted_bits = w;
			encoder->private_->subframe_bps[channel] = encoder->protected_->bits_per_sample - w;
		}
	}
	if(do_mid_side) {
		FLAC__ASSERT(encoder->protected_->channels == 2);
		for(channel = 0; channel < 2; channel++) {
			uint32_t w = get_wasted_bits_(encoder->private_->integer_signal_mid_side[channel], encoder->protected_->blocksize);
			if (w > encoder->protected_->bits_per_sample) {
				w = encoder->protected_->bits_per_sample;
			}
			encoder->private_->subframe_workspace_mid_side[channel][0].wasted_bits = encoder->private_->subframe_workspace_mid_side[channel][1].wasted_bits = w;
			encoder->private_->subframe_bps_mid_side[channel] = encoder->protected_->bits_per_sample - w + (channel==0? 0:1);
		}
	}

	/*
	 * First do a normal encoding pass of each independent channel
	 */
	if(do_independent) {
		for(channel = 0; channel < encoder->protected_->channels; channel++) {
			if(!
				process_subframe_(
					encoder,
					min_partition_order,
					max_partition_order,
					&frame_header,
					encoder->private_->subframe_bps[channel],
					encoder->private_->integer_signal[channel],
					encoder->private_->subframe_workspace_ptr[channel],
					encoder->private_->partitioned_rice_contents_workspace_ptr[channel],
					encoder->private_->residual_workspace[channel],
					encoder->private_->best_subframe+channel,
					encoder->private_->best_subframe_bits+channel
				)
			)
				return false;
		}
	}

	/*
	 * Now do mid and side channels if requested
	 */
	if(do_mid_side) {
		FLAC__ASSERT(encoder->protected_->channels == 2);

		for(channel = 0; channel < 2; channel++) {
			if(!
				process_subframe_(
					encoder,
					min_partition_order,
					max_partition_order,
					&frame_header,
					encoder->private_->subframe_bps_mid_side[channel],
					encoder->private_->integer_signal_mid_side[channel],
					encoder->private_->subframe_workspace_ptr_mid_side[channel],
					encoder->private_->partitioned_rice_contents_workspace_ptr_mid_side[channel],
					encoder->private_->residual_workspace_mid_side[channel],
					encoder->private_->best_subframe_mid_side+channel,
					encoder->private_->best_subframe_bits_mid_side+channel
				)
			)
				return false;
		}
	}

	/*
	 * Compose the frame bitbuffer
	 */
	if(do_mid_side) {
		uint32_t left_bps = 0, right_bps = 0; /* initialized only to prevent superfluous compiler warning */
		FLAC__Subframe *left_subframe = 0, *right_subframe = 0; /* initialized only to prevent superfluous compiler warning */
		FLAC__ChannelAssignment channel_assignment;

		FLAC__ASSERT(encoder->protected_->channels == 2);

		if(encoder->protected_->loose_mid_side_stereo && encoder->private_->loose_mid_side_stereo_frame_count > 0) {
			channel_assignment = (encoder->private_->last_channel_assignment == FLAC__CHANNEL_ASSIGNMENT_INDEPENDENT? FLAC__CHANNEL_ASSIGNMENT_INDEPENDENT : FLAC__CHANNEL_ASSIGNMENT_MID_SIDE);
		}
		else {
			uint32_t bits[4]; /* WATCHOUT - indexed by FLAC__ChannelAssignment */
			uint32_t min_bits;
			int ca;

			FLAC__ASSERT(FLAC__CHANNEL_ASSIGNMENT_INDEPENDENT == 0);
			FLAC__ASSERT(FLAC__CHANNEL_ASSIGNMENT_LEFT_SIDE   == 1);
			FLAC__ASSERT(FLAC__CHANNEL_ASSIGNMENT_RIGHT_SIDE  == 2);
			FLAC__ASSERT(FLAC__CHANNEL_ASSIGNMENT_MID_SIDE    == 3);
			FLAC__ASSERT(do_independent && do_mid_side);

			/* We have to figure out which channel assignent results in the smallest frame */
			bits[FLAC__CHANNEL_ASSIGNMENT_INDEPENDENT] = encoder->private_->best_subframe_bits         [0] + encoder->private_->best_subframe_bits         [1];
			bits[FLAC__CHANNEL_ASSIGNMENT_LEFT_SIDE  ] = encoder->private_->best_subframe_bits         [0] + encoder->private_->best_subframe_bits_mid_side[1];
			bits[FLAC__CHANNEL_ASSIGNMENT_RIGHT_SIDE ] = encoder->private_->best_subframe_bits         [1] + encoder->private_->best_subframe_bits_mid_side[1];
			bits[FLAC__CHANNEL_ASSIGNMENT_MID_SIDE   ] = encoder->private_->best_subframe_bits_mid_side[0] + encoder->private_->best_subframe_bits_mid_side[1];

			channel_assignment = FLAC__CHANNEL_ASSIGNMENT_INDEPENDENT;
			min_bits = bits[channel_assignment];
			for(ca = 1; ca <= 3; ca++) {
				if(bits[ca] < min_bits) {
					min_bits = bits[ca];
					channel_assignment = (FLAC__ChannelAssignment)ca;
				}
			}
		}

		frame_header.channel_assignment = channel_assignment;

		if(!FLAC__frame_add_header(&frame_header, encoder->private_->frame)) {
			encoder->protected_->state = FLAC__STREAM_ENCODER_FRAMING_ERROR;
			return false;
		}

		switch(channel_assignment) {
			case FLAC__CHANNEL_ASSIGNMENT_INDEPENDENT:
				left_subframe  = &encoder->private_->subframe_workspace         [0][encoder->private_->best_subframe         [0]];
				right_subframe = &encoder->private_->subframe_workspace         [1][encoder->private_->best_subframe         [1]];
				break;
			case FLAC__CHANNEL_ASSIGNMENT_LEFT_SIDE:
				left_subframe  = &encoder->private_->subframe_workspace         [0][encoder->private_->best_subframe         [0]];
				right_subframe = &encoder->private_->subframe_workspace_mid_side[1][encoder->private_->best_subframe_mid_side[1]];
				break;
			case FLAC__CHANNEL_ASSIGNMENT_RIGHT_SIDE:
				left_subframe  = &encoder->private_->subframe_workspace_mid_side[1][encoder->private_->best_subframe_mid_side[1]];
				right_subframe = &encoder->private_->subframe_workspace         [1][encoder->private_->best_subframe         [1]];
				break;
			case FLAC__CHANNEL_ASSIGNMENT_MID_SIDE:
				left_subframe  = &encoder->private_->subframe_workspace_mid_side[0][encoder->private_->best_subframe_mid_side[0]];
				right_subframe = &encoder->private_->subframe_workspace_mid_side[1][encoder->private_->best_subframe_mid_side[1]];
				break;
			default:
				FLAC__ASSERT(0);
		}

		switch(channel_assignment) {
			case FLAC__CHANNEL_ASSIGNMENT_INDEPENDENT:
				left_bps  = encoder->private_->subframe_bps         [0];
				right_bps = encoder->private_->subframe_bps         [1];
				break;
			case FLAC__CHANNEL_ASSIGNMENT_LEFT_SIDE:
				left_bps  = encoder->private_->subframe_bps         [0];
				right_bps = encoder->private_->subframe_bps_mid_side[1];
				break;
			case FLAC__CHANNEL_ASSIGNMENT_RIGHT_SIDE:
				left_bps  = encoder->private_->subframe_bps_mid_side[1];
				right_bps = encoder->private_->subframe_bps         [1];
				break;
			case FLAC__CHANNEL_ASSIGNMENT_MID_SIDE:
				left_bps  = encoder->private_->subframe_bps_mid_side[0];
				right_bps = encoder->private_->subframe_bps_mid_side[1];
				break;
			default:
				FLAC__ASSERT(0);
		}

		/* note that encoder_add_subframe_ sets the state for us in case of an error */
		if(!add_subframe_(encoder, frame_header.blocksize, left_bps , left_subframe , encoder->private_->frame))
			return false;
		if(!add_subframe_(encoder, frame_header.blocksize, right_bps, right_subframe, encoder->private_->frame))
			return false;
	}
	else {
		if(!FLAC__frame_add_header(&frame_header, encoder->private_->frame)) {
			encoder->protected_->state = FLAC__STREAM_ENCODER_FRAMING_ERROR;
			return false;
		}

		for(channel = 0; channel < encoder->protected_->channels; channel++) {
			if(!add_subframe_(encoder, frame_header.blocksize, encoder->private_->subframe_bps[channel], &encoder->private_->subframe_workspace[channel][encoder->private_->best_subframe[channel]], encoder->private_->frame)) {
				/* the above function sets the state for us in case of an error */
				return false;
			}
		}
	}

	if(encoder->protected_->loose_mid_side_stereo) {
		encoder->private_->loose_mid_side_stereo_frame_count++;
		if(encoder->private_->loose_mid_side_stereo_frame_count >= encoder->private_->loose_mid_side_stereo_frames)
			encoder->private_->loose_mid_side_stereo_frame_count = 0;
	}

	encoder->private_->last_channel_assignment = frame_header.channel_assignment;

	return true;
}

FLAC__bool process_subframe_(
	FLAC__StreamEncoder *encoder,
	uint32_t min_partition_order,
	uint32_t max_partition_order,
	const FLAC__FrameHeader *frame_header,
	uint32_t subframe_bps,
	const FLAC__int32 integer_signal[],
	FLAC__Subframe *subframe[2],
	FLAC__EntropyCodingMethod_PartitionedRiceContents *partitioned_rice_contents[2],
	FLAC__int32 *residual[2],
	uint32_t *best_subframe,
	uint32_t *best_bits
)
{
#ifndef FLAC__INTEGER_ONLY_LIBRARY
	float fixed_residual_bits_per_sample[FLAC__MAX_FIXED_ORDER+1];
#else
	FLAC__fixedpoint fixed_residual_bits_per_sample[FLAC__MAX_FIXED_ORDER+1];
#endif
#ifndef FLAC__INTEGER_ONLY_LIBRARY
	double lpc_residual_bits_per_sample;
	FLAC__real autoc[FLAC__MAX_LPC_ORDER+1]; /* WATCHOUT: the size is important even though encoder->protected_->max_lpc_order might be less; some asm and x86 intrinsic routines need all the space */
	double lpc_error[FLAC__MAX_LPC_ORDER];
	uint32_t min_lpc_order, max_lpc_order, lpc_order;
	uint32_t min_qlp_coeff_precision, max_qlp_coeff_precision, qlp_coeff_precision;
#endif
	uint32_t min_fixed_order, max_fixed_order, guess_fixed_order, fixed_order;
	uint32_t rice_parameter;
	uint32_t _candidate_bits, _best_bits;
	uint32_t _best_subframe;
	/* only use RICE2 partitions if stream bps > 16 */
	const uint32_t rice_parameter_limit = FLAC__stream_encoder_get_bits_per_sample(encoder) > 16? FLAC__ENTROPY_CODING_METHOD_PARTITIONED_RICE2_ESCAPE_PARAMETER : FLAC__ENTROPY_CODING_METHOD_PARTITIONED_RICE_ESCAPE_PARAMETER;

	FLAC__ASSERT(frame_header->blocksize > 0);

	/* verbatim subframe is the baseline against which we measure other compressed subframes */
	_best_subframe = 0;
	if(encoder->private_->disable_verbatim_subframes && frame_header->blocksize >= FLAC__MAX_FIXED_ORDER)
		_best_bits = UINT_MAX;
	else
		_best_bits = evaluate_verbatim_subframe_(encoder, integer_signal, frame_header->blocksize, subframe_bps, subframe[_best_subframe]);

	if(frame_header->blocksize >= FLAC__MAX_FIXED_ORDER) {
		uint32_t signal_is_constant = false;
		if(subframe_bps + 4 + FLAC__bitmath_ilog2((frame_header->blocksize-FLAC__MAX_FIXED_ORDER)|1) <= 32)
			guess_fixed_order = encoder->private_->local_fixed_compute_best_predictor(integer_signal+FLAC__MAX_FIXED_ORDER, frame_header->blocksize-FLAC__MAX_FIXED_ORDER, fixed_residual_bits_per_sample);
		else
			guess_fixed_order = encoder->private_->local_fixed_compute_best_predictor_wide(integer_signal+FLAC__MAX_FIXED_ORDER, frame_header->blocksize-FLAC__MAX_FIXED_ORDER, fixed_residual_bits_per_sample);
		/* check for constant subframe */
		if(
			!encoder->private_->disable_constant_subframes &&
#ifndef FLAC__INTEGER_ONLY_LIBRARY
			fixed_residual_bits_per_sample[1] == 0.0
#else
			fixed_residual_bits_per_sample[1] == FLAC__FP_ZERO
#endif
		) {
			/* the above means it's possible all samples are the same value; now double-check it: */
			uint32_t i;
			signal_is_constant = true;
			for(i = 1; i < frame_header->blocksize; i++) {
				if(integer_signal[0] != integer_signal[i]) {
					signal_is_constant = false;
					break;
				}
			}
		}
		if(signal_is_constant) {
			_candidate_bits = evaluate_constant_subframe_(encoder, integer_signal[0], frame_header->blocksize, subframe_bps, subframe[!_best_subframe]);
			if(_candidate_bits < _best_bits) {
				_best_subframe = !_best_subframe;
				_best_bits = _candidate_bits;
			}
		}
		else {
			if(!encoder->private_->disable_fixed_subframes || (encoder->protected_->max_lpc_order == 0 && _best_bits == UINT_MAX)) {
				/* encode fixed */
				if(encoder->protected_->do_exhaustive_model_search) {
					min_fixed_order = 0;
					max_fixed_order = FLAC__MAX_FIXED_ORDER;
				}
				else {
					min_fixed_order = max_fixed_order = guess_fixed_order;
				}
				if(max_fixed_order >= frame_header->blocksize)
					max_fixed_order = frame_header->blocksize - 1;
				for(fixed_order = min_fixed_order; fixed_order <= max_fixed_order; fixed_order++) {
#ifndef FLAC__INTEGER_ONLY_LIBRARY
					if(fixed_residual_bits_per_sample[fixed_order] >= (float)subframe_bps)
						continue; /* don't even try */
					rice_parameter = (fixed_residual_bits_per_sample[fixed_order] > 0.0)? (uint32_t)(fixed_residual_bits_per_sample[fixed_order]+0.5) : 0; /* 0.5 is for rounding */
#else
					if(FLAC__fixedpoint_trunc(fixed_residual_bits_per_sample[fixed_order]) >= (int)subframe_bps)
						continue; /* don't even try */
					rice_parameter = (fixed_residual_bits_per_sample[fixed_order] > FLAC__FP_ZERO)? (uint32_t)FLAC__fixedpoint_trunc(fixed_residual_bits_per_sample[fixed_order]+FLAC__FP_ONE_HALF) : 0; /* 0.5 is for rounding */
#endif
					rice_parameter++; /* to account for the signed->uint32_t conversion during rice coding */
					if(rice_parameter >= rice_parameter_limit) {
#ifndef NDEBUG
						fprintf(stderr, "clipping rice_parameter (%u -> %u) @0\n", rice_parameter, rice_parameter_limit - 1);
#endif
						rice_parameter = rice_parameter_limit - 1;
					}
					_candidate_bits =
						evaluate_fixed_subframe_(
							encoder,
							integer_signal,
							residual[!_best_subframe],
							encoder->private_->abs_residual_partition_sums,
							encoder->private_->raw_bits_per_partition,
							frame_header->blocksize,
							subframe_bps,
							fixed_order,
							rice_parameter,
							rice_parameter_limit,
							min_partition_order,
							max_partition_order,
							encoder->protected_->do_escape_coding,
							encoder->protected_->rice_parameter_search_dist,
							subframe[!_best_subframe],
							partitioned_rice_contents[!_best_subframe]
						);
					if(_candidate_bits < _best_bits) {
						_best_subframe = !_best_subframe;
						_best_bits = _candidate_bits;
					}
				}
			}

#ifndef FLAC__INTEGER_ONLY_LIBRARY
			/* encode lpc */
			if(encoder->protected_->max_lpc_order > 0) {
				if(encoder->protected_->max_lpc_order >= frame_header->blocksize)
					max_lpc_order = frame_header->blocksize-1;
				else
					max_lpc_order = encoder->protected_->max_lpc_order;
				if(max_lpc_order > 0) {
					uint32_t a;
					for (a = 0; a < encoder->protected_->num_apodizations; a++) {
						FLAC__lpc_window_data(integer_signal, encoder->private_->window[a], encoder->private_->windowed_signal, frame_header->blocksize);
						encoder->private_->local_lpc_compute_autocorrelation(encoder->private_->windowed_signal, frame_header->blocksize, max_lpc_order+1, autoc);
						/* if autoc[0] == 0.0, the signal is constant and we usually won't get here, but it can happen */
						if(autoc[0] != 0.0) {
							FLAC__lpc_compute_lp_coefficients(autoc, &max_lpc_order, encoder->private_->lp_coeff, lpc_error);
							if(encoder->protected_->do_exhaustive_model_search) {
								min_lpc_order = 1;
							}
							else {
								const uint32_t guess_lpc_order =
									FLAC__lpc_compute_best_order(
										lpc_error,
										max_lpc_order,
										frame_header->blocksize,
										subframe_bps + (
											encoder->protected_->do_qlp_coeff_prec_search?
												FLAC__MIN_QLP_COEFF_PRECISION : /* have to guess; use the min possible size to avoid accidentally favoring lower orders */
												encoder->protected_->qlp_coeff_precision
										)
									);
								min_lpc_order = max_lpc_order = guess_lpc_order;
							}
							if(max_lpc_order >= frame_header->blocksize)
								max_lpc_order = frame_header->blocksize - 1;
							for(lpc_order = min_lpc_order; lpc_order <= max_lpc_order; lpc_order++) {
								lpc_residual_bits_per_sample = FLAC__lpc_compute_expected_bits_per_residual_sample(lpc_error[lpc_order-1], frame_header->blocksize-lpc_order);
								if(lpc_residual_bits_per_sample >= (double)subframe_bps)
									continue; /* don't even try */
								rice_parameter = (lpc_residual_bits_per_sample > 0.0)? (uint32_t)(lpc_residual_bits_per_sample+0.5) : 0; /* 0.5 is for rounding */
								rice_parameter++; /* to account for the signed->uint32_t conversion during rice coding */
								if(rice_parameter >= rice_parameter_limit) {
#ifndef NDEBUG
									fprintf(stderr, "clipping rice_parameter (%u -> %u) @1\n", rice_parameter, rice_parameter_limit - 1);
#endif
									rice_parameter = rice_parameter_limit - 1;
								}
								if(encoder->protected_->do_qlp_coeff_prec_search) {
									min_qlp_coeff_precision = FLAC__MIN_QLP_COEFF_PRECISION;
									/* try to keep qlp coeff precision such that only 32-bit math is required for decode of <=16bps(+1bps for side channel) streams */
									if(subframe_bps <= 17) {
										max_qlp_coeff_precision = flac_min(32 - subframe_bps - FLAC__bitmath_ilog2(lpc_order), FLAC__MAX_QLP_COEFF_PRECISION);
										max_qlp_coeff_precision = flac_max(max_qlp_coeff_precision, min_qlp_coeff_precision);
									}
									else
										max_qlp_coeff_precision = FLAC__MAX_QLP_COEFF_PRECISION;
								}
								else {
									min_qlp_coeff_precision = max_qlp_coeff_precision = encoder->protected_->qlp_coeff_precision;
								}
								for(qlp_coeff_precision = min_qlp_coeff_precision; qlp_coeff_precision <= max_qlp_coeff_precision; qlp_coeff_precision++) {
									_candidate_bits =
										evaluate_lpc_subframe_(
											encoder,
											integer_signal,
											residual[!_best_subframe],
											encoder->private_->abs_residual_partition_sums,
											encoder->private_->raw_bits_per_partition,
											encoder->private_->lp_coeff[lpc_order-1],
											frame_header->blocksize,
											subframe_bps,
											lpc_order,
											qlp_coeff_precision,
											rice_parameter,
											rice_parameter_limit,
											min_partition_order,
											max_partition_order,
											encoder->protected_->do_escape_coding,
											encoder->protected_->rice_parameter_search_dist,
											subframe[!_best_subframe],
											partitioned_rice_contents[!_best_subframe]
										);
									if(_candidate_bits > 0) { /* if == 0, there was a problem quantizing the lpcoeffs */
										if(_candidate_bits < _best_bits) {
											_best_subframe = !_best_subframe;
											_best_bits = _candidate_bits;
										}
									}
								}
							}
						}
					}
				}
			}
#endif /* !defined FLAC__INTEGER_ONLY_LIBRARY */
		}
	}

	/* under rare circumstances this can happen when all but lpc subframe types are disabled: */
	if(_best_bits == UINT_MAX) {
		FLAC__ASSERT(_best_subframe == 0);
		_best_bits = evaluate_verbatim_subframe_(encoder, integer_signal, frame_header->blocksize, subframe_bps, subframe[_best_subframe]);
	}

	*best_subframe = _best_subframe;
	*best_bits = _best_bits;

	return true;
}

FLAC__bool add_subframe_(
	FLAC__StreamEncoder *encoder,
	uint32_t blocksize,
	uint32_t subframe_bps,
	const FLAC__Subframe *subframe,
	FLAC__BitWriter *frame
)
{
	switch(subframe->type) {
		case FLAC__SUBFRAME_TYPE_CONSTANT:
			if(!FLAC__subframe_add_constant(&(subframe->data.constant), subframe_bps, subframe->wasted_bits, frame)) {
				encoder->protected_->state = FLAC__STREAM_ENCODER_FRAMING_ERROR;
				return false;
			}
			break;
		case FLAC__SUBFRAME_TYPE_FIXED:
			if(!FLAC__subframe_add_fixed(&(subframe->data.fixed), blocksize - subframe->data.fixed.order, subframe_bps, subframe->wasted_bits, frame)) {
				encoder->protected_->state = FLAC__STREAM_ENCODER_FRAMING_ERROR;
				return false;
			}
			break;
		case FLAC__SUBFRAME_TYPE_LPC:
			if(!FLAC__subframe_add_lpc(&(subframe->data.lpc), blocksize - subframe->data.lpc.order, subframe_bps, subframe->wasted_bits, frame)) {
				encoder->protected_->state = FLAC__STREAM_ENCODER_FRAMING_ERROR;
				return false;
			}
			break;
		case FLAC__SUBFRAME_TYPE_VERBATIM:
			if(!FLAC__subframe_add_verbatim(&(subframe->data.verbatim), blocksize, subframe_bps, subframe->wasted_bits, frame)) {
				encoder->protected_->state = FLAC__STREAM_ENCODER_FRAMING_ERROR;
				return false;
			}
			break;
		default:
			FLAC__ASSERT(0);
	}

	return true;
}

#define SPOTCHECK_ESTIMATE 0
#if SPOTCHECK_ESTIMATE
static void spotcheck_subframe_estimate_(
	FLAC__StreamEncoder *encoder,
	uint32_t blocksize,
	uint32_t subframe_bps,
	const FLAC__Subframe *subframe,
	uint32_t estimate
)
{
	FLAC__bool ret;
	FLAC__BitWriter *frame = FLAC__bitwriter_new();
	if(frame == 0) {
		fprintf(stderr, "EST: can't allocate frame\n");
		return;
	}
	if(!FLAC__bitwriter_init(frame)) {
		fprintf(stderr, "EST: can't init frame\n");
		return;
	}
	ret = add_subframe_(encoder, blocksize, subframe_bps, subframe, frame);
	FLAC__ASSERT(ret);
	{
		const uint32_t actual = FLAC__bitwriter_get_input_bits_unconsumed(frame);
		if(estimate != actual)
			fprintf(stderr, "EST: bad, frame#%u sub#%%d type=%8s est=%u, actual=%u, delta=%d\n", encoder->private_->current_frame_number, FLAC__SubframeTypeString[subframe->type], estimate, actual, (int)actual-(int)estimate);
	}
	FLAC__bitwriter_delete(frame);
}
#endif

uint32_t evaluate_constant_subframe_(
	FLAC__StreamEncoder *encoder,
	const FLAC__int32 signal,
	uint32_t blocksize,
	uint32_t subframe_bps,
	FLAC__Subframe *subframe
)
{
	uint32_t estimate;
	subframe->type = FLAC__SUBFRAME_TYPE_CONSTANT;
	subframe->data.constant.value = signal;

	estimate = FLAC__SUBFRAME_ZERO_PAD_LEN + FLAC__SUBFRAME_TYPE_LEN + FLAC__SUBFRAME_WASTED_BITS_FLAG_LEN + subframe->wasted_bits + subframe_bps;

#if SPOTCHECK_ESTIMATE
	spotcheck_subframe_estimate_(encoder, blocksize, subframe_bps, subframe, estimate);
#else
	(void)encoder, (void)blocksize;
#endif

	return estimate;
}

uint32_t evaluate_fixed_subframe_(
	FLAC__StreamEncoder *encoder,
	const FLAC__int32 signal[],
	FLAC__int32 residual[],
	FLAC__uint64 abs_residual_partition_sums[],
	uint32_t raw_bits_per_partition[],
	uint32_t blocksize,
	uint32_t subframe_bps,
	uint32_t order,
	uint32_t rice_parameter,
	uint32_t rice_parameter_limit,
	uint32_t min_partition_order,
	uint32_t max_partition_order,
	FLAC__bool do_escape_coding,
	uint32_t rice_parameter_search_dist,
	FLAC__Subframe *subframe,
	FLAC__EntropyCodingMethod_PartitionedRiceContents *partitioned_rice_contents
)
{
	uint32_t i, residual_bits, estimate;
	const uint32_t residual_samples = blocksize - order;

	FLAC__fixed_compute_residual(signal+order, residual_samples, order, residual);

	subframe->type = FLAC__SUBFRAME_TYPE_FIXED;

	subframe->data.fixed.entropy_coding_method.type = FLAC__ENTROPY_CODING_METHOD_PARTITIONED_RICE;
	subframe->data.fixed.entropy_coding_method.data.partitioned_rice.contents = partitioned_rice_contents;
	subframe->data.fixed.residual = residual;

	residual_bits =
		find_best_partition_order_(
			encoder->private_,
			residual,
			abs_residual_partition_sums,
			raw_bits_per_partition,
			residual_samples,
			order,
			rice_parameter,
			rice_parameter_limit,
			min_partition_order,
			max_partition_order,
			subframe_bps,
			do_escape_coding,
			rice_parameter_search_dist,
			&subframe->data.fixed.entropy_coding_method
		);

	subframe->data.fixed.order = order;
	for(i = 0; i < order; i++)
		subframe->data.fixed.warmup[i] = signal[i];

	estimate = FLAC__SUBFRAME_ZERO_PAD_LEN + FLAC__SUBFRAME_TYPE_LEN + FLAC__SUBFRAME_WASTED_BITS_FLAG_LEN + subframe->wasted_bits + (order * subframe_bps) + residual_bits;

#if SPOTCHECK_ESTIMATE
	spotcheck_subframe_estimate_(encoder, blocksize, subframe_bps, subframe, estimate);
#endif

	return estimate;
}

#ifndef FLAC__INTEGER_ONLY_LIBRARY
uint32_t evaluate_lpc_subframe_(
	FLAC__StreamEncoder *encoder,
	const FLAC__int32 signal[],
	FLAC__int32 residual[],
	FLAC__uint64 abs_residual_partition_sums[],
	uint32_t raw_bits_per_partition[],
	const FLAC__real lp_coeff[],
	uint32_t blocksize,
	uint32_t subframe_bps,
	uint32_t order,
	uint32_t qlp_coeff_precision,
	uint32_t rice_parameter,
	uint32_t rice_parameter_limit,
	uint32_t min_partition_order,
	uint32_t max_partition_order,
	FLAC__bool do_escape_coding,
	uint32_t rice_parameter_search_dist,
	FLAC__Subframe *subframe,
	FLAC__EntropyCodingMethod_PartitionedRiceContents *partitioned_rice_contents
)
{
	FLAC__int32 qlp_coeff[FLAC__MAX_LPC_ORDER]; /* WATCHOUT: the size is important; some x86 intrinsic routines need more than lpc order elements */
	uint32_t i, residual_bits, estimate;
	int quantization, ret;
	const uint32_t residual_samples = blocksize - order;

	/* try to keep qlp coeff precision such that only 32-bit math is required for decode of <=16bps(+1bps for side channel) streams */
	if(subframe_bps <= 17) {
		FLAC__ASSERT(order > 0);
		FLAC__ASSERT(order <= FLAC__MAX_LPC_ORDER);
		qlp_coeff_precision = flac_min(qlp_coeff_precision, 32 - subframe_bps - FLAC__bitmath_ilog2(order));
	}

	ret = FLAC__lpc_quantize_coefficients(lp_coeff, order, qlp_coeff_precision, qlp_coeff, &quantization);
	if(ret != 0)
		return 0; /* this is a hack to indicate to the caller that we can't do lp at this order on this subframe */

	if(subframe_bps + qlp_coeff_precision + FLAC__bitmath_ilog2(order) <= 32)
		if(subframe_bps <= 16 && qlp_coeff_precision <= 16)
			encoder->private_->local_lpc_compute_residual_from_qlp_coefficients_16bit(signal+order, residual_samples, qlp_coeff, order, quantization, residual);
		else
			encoder->private_->local_lpc_compute_residual_from_qlp_coefficients(signal+order, residual_samples, qlp_coeff, order, quantization, residual);
	else
		encoder->private_->local_lpc_compute_residual_from_qlp_coefficients_64bit(signal+order, residual_samples, qlp_coeff, order, quantization, residual);

	subframe->type = FLAC__SUBFRAME_TYPE_LPC;

	subframe->data.lpc.entropy_coding_method.type = FLAC__ENTROPY_CODING_METHOD_PARTITIONED_RICE;
	subframe->data.lpc.entropy_coding_method.data.partitioned_rice.contents = partitioned_rice_contents;
	subframe->data.lpc.residual = residual;

	residual_bits =
		find_best_partition_order_(
			encoder->private_,
			residual,
			abs_residual_partition_sums,
			raw_bits_per_partition,
			residual_samples,
			order,
			rice_parameter,
			rice_parameter_limit,
			min_partition_order,
			max_partition_order,
			subframe_bps,
			do_escape_coding,
			rice_parameter_search_dist,
			&subframe->data.lpc.entropy_coding_method
		);

	subframe->data.lpc.order = order;
	subframe->data.lpc.qlp_coeff_precision = qlp_coeff_precision;
	subframe->data.lpc.quantization_level = quantization;
	memcpy(subframe->data.lpc.qlp_coeff, qlp_coeff, sizeof(FLAC__int32)*FLAC__MAX_LPC_ORDER);
	for(i = 0; i < order; i++)
		subframe->data.lpc.warmup[i] = signal[i];

	estimate = FLAC__SUBFRAME_ZERO_PAD_LEN + FLAC__SUBFRAME_TYPE_LEN + FLAC__SUBFRAME_WASTED_BITS_FLAG_LEN + subframe->wasted_bits + FLAC__SUBFRAME_LPC_QLP_COEFF_PRECISION_LEN + FLAC__SUBFRAME_LPC_QLP_SHIFT_LEN + (order * (qlp_coeff_precision + subframe_bps)) + residual_bits;

#if SPOTCHECK_ESTIMATE
	spotcheck_subframe_estimate_(encoder, blocksize, subframe_bps, subframe, estimate);
#endif

	return estimate;
}
#endif

uint32_t evaluate_verbatim_subframe_(
	FLAC__StreamEncoder *encoder,
	const FLAC__int32 signal[],
	uint32_t blocksize,
	uint32_t subframe_bps,
	FLAC__Subframe *subframe
)
{
	uint32_t estimate;

	subframe->type = FLAC__SUBFRAME_TYPE_VERBATIM;

	subframe->data.verbatim.data = signal;

	estimate = FLAC__SUBFRAME_ZERO_PAD_LEN + FLAC__SUBFRAME_TYPE_LEN + FLAC__SUBFRAME_WASTED_BITS_FLAG_LEN + subframe->wasted_bits + (blocksize * subframe_bps);

#if SPOTCHECK_ESTIMATE
	spotcheck_subframe_estimate_(encoder, blocksize, subframe_bps, subframe, estimate);
#else
	(void)encoder;
#endif

	return estimate;
}

uint32_t find_best_partition_order_(
	FLAC__StreamEncoderPrivate *private_,
	const FLAC__int32 residual[],
	FLAC__uint64 abs_residual_partition_sums[],
	uint32_t raw_bits_per_partition[],
	uint32_t residual_samples,
	uint32_t predictor_order,
	uint32_t rice_parameter,
	uint32_t rice_parameter_limit,
	uint32_t min_partition_order,
	uint32_t max_partition_order,
	uint32_t bps,
	FLAC__bool do_escape_coding,
	uint32_t rice_parameter_search_dist,
	FLAC__EntropyCodingMethod *best_ecm
)
{
	uint32_t residual_bits, best_residual_bits = 0;
	uint32_t best_parameters_index = 0;
	uint32_t best_partition_order = 0;
	const uint32_t blocksize = residual_samples + predictor_order;

	max_partition_order = FLAC__format_get_max_rice_partition_order_from_blocksize_limited_max_and_predictor_order(max_partition_order, blocksize, predictor_order);
	min_partition_order = flac_min(min_partition_order, max_partition_order);

	private_->local_precompute_partition_info_sums(residual, abs_residual_partition_sums, residual_samples, predictor_order, min_partition_order, max_partition_order, bps);

	if(do_escape_coding)
		precompute_partition_info_escapes_(residual, raw_bits_per_partition, residual_samples, predictor_order, min_partition_order, max_partition_order);

	{
		int partition_order;
		uint32_t sum;

		for(partition_order = (int)max_partition_order, sum = 0; partition_order >= (int)min_partition_order; partition_order--) {
			if(!
				set_partitioned_rice_(
#ifdef EXACT_RICE_BITS_CALCULATION
					residual,
#endif
					abs_residual_partition_sums+sum,
					raw_bits_per_partition+sum,
					residual_samples,
					predictor_order,
					rice_parameter,
					rice_parameter_limit,
					rice_parameter_search_dist,
					(uint32_t)partition_order,
					do_escape_coding,
					&private_->partitioned_rice_contents_extra[!best_parameters_index],
					&residual_bits
				)
			)
			{
				FLAC__ASSERT(best_residual_bits != 0);
				break;
			}
			sum += 1u << partition_order;
			if(best_residual_bits == 0 || residual_bits < best_residual_bits) {
				best_residual_bits = residual_bits;
				best_parameters_index = !best_parameters_index;
				best_partition_order = partition_order;
			}
		}
	}

	best_ecm->data.partitioned_rice.order = best_partition_order;

	{
		/*
		 * We are allowed to de-const the pointer based on our special
		 * knowledge; it is const to the outside world.
		 */
		FLAC__EntropyCodingMethod_PartitionedRiceContents* prc = (FLAC__EntropyCodingMethod_PartitionedRiceContents*)best_ecm->data.partitioned_rice.contents;
		uint32_t partition;

		/* save best parameters and raw_bits */
		FLAC__format_entropy_coding_method_partitioned_rice_contents_ensure_size(prc, flac_max(6u, best_partition_order));
		memcpy(prc->parameters, private_->partitioned_rice_contents_extra[best_parameters_index].parameters, sizeof(uint32_t)*(1<<(best_partition_order)));
		if(do_escape_coding)
			memcpy(prc->raw_bits, private_->partitioned_rice_contents_extra[best_parameters_index].raw_bits, sizeof(uint32_t)*(1<<(best_partition_order)));
		/*
		 * Now need to check if the type should be changed to
		 * FLAC__ENTROPY_CODING_METHOD_PARTITIONED_RICE2 based on the
		 * size of the rice parameters.
		 */
		for(partition = 0; partition < (1u<<best_partition_order); partition++) {
			if(prc->parameters[partition] >= FLAC__ENTROPY_CODING_METHOD_PARTITIONED_RICE_ESCAPE_PARAMETER) {
				best_ecm->type = FLAC__ENTROPY_CODING_METHOD_PARTITIONED_RICE2;
				break;
			}
		}
	}

	return best_residual_bits;
}

void precompute_partition_info_sums_(
	const FLAC__int32 residual[],
	FLAC__uint64 abs_residual_partition_sums[],
	uint32_t residual_samples,
	uint32_t predictor_order,
	uint32_t min_partition_order,
	uint32_t max_partition_order,
	uint32_t bps
)
{
	const uint32_t default_partition_samples = (residual_samples + predictor_order) >> max_partition_order;
	uint32_t partitions = 1u << max_partition_order;

	FLAC__ASSERT(default_partition_samples > predictor_order);

	/* first do max_partition_order */
	{
		const uint32_t threshold = 32 - FLAC__bitmath_ilog2(default_partition_samples);
		uint32_t partition, residual_sample, end = (uint32_t)(-(int)predictor_order);
		/* WATCHOUT: "bps + FLAC__MAX_EXTRA_RESIDUAL_BPS" is the maximum assumed size of the average residual magnitude */
		if(bps + FLAC__MAX_EXTRA_RESIDUAL_BPS < threshold) {
			for(partition = residual_sample = 0; partition < partitions; partition++) {
				FLAC__uint32 abs_residual_partition_sum = 0;
				end += default_partition_samples;
				for( ; residual_sample < end; residual_sample++)
					abs_residual_partition_sum += abs(residual[residual_sample]); /* abs(INT_MIN) is undefined, but if the residual is INT_MIN we have bigger problems */
				abs_residual_partition_sums[partition] = abs_residual_partition_sum;
			}
		}
		else { /* have to pessimistically use 64 bits for accumulator */
			for(partition = residual_sample = 0; partition < partitions; partition++) {
				FLAC__uint64 abs_residual_partition_sum64 = 0;
				end += default_partition_samples;
				for( ; residual_sample < end; residual_sample++)
					abs_residual_partition_sum64 += abs(residual[residual_sample]); /* abs(INT_MIN) is undefined, but if the residual is INT_MIN we have bigger problems */
				abs_residual_partition_sums[partition] = abs_residual_partition_sum64;
			}
		}
	}

	/* now merge partitions for lower orders */
	{
		uint32_t from_partition = 0, to_partition = partitions;
		int partition_order;
		for(partition_order = (int)max_partition_order - 1; partition_order >= (int)min_partition_order; partition_order--) {
			uint32_t i;
			partitions >>= 1;
			for(i = 0; i < partitions; i++) {
				abs_residual_partition_sums[to_partition++] =
					abs_residual_partition_sums[from_partition  ] +
					abs_residual_partition_sums[from_partition+1];
				from_partition += 2;
			}
		}
	}
}

void precompute_partition_info_escapes_(
	const FLAC__int32 residual[],
	uint32_t raw_bits_per_partition[],
	uint32_t residual_samples,
	uint32_t predictor_order,
	uint32_t min_partition_order,
	uint32_t max_partition_order
)
{
	int partition_order;
	uint32_t from_partition, to_partition = 0;
	const uint32_t blocksize = residual_samples + predictor_order;

	/* first do max_partition_order */
	for(partition_order = (int)max_partition_order; partition_order >= 0; partition_order--) {
		FLAC__int32 r;
		FLAC__uint32 rmax;
		uint32_t partition, partition_sample, partition_samples, residual_sample;
		const uint32_t partitions = 1u << partition_order;
		const uint32_t default_partition_samples = blocksize >> partition_order;

		FLAC__ASSERT(default_partition_samples > predictor_order);

		for(partition = residual_sample = 0; partition < partitions; partition++) {
			partition_samples = default_partition_samples;
			if(partition == 0)
				partition_samples -= predictor_order;
			rmax = 0;
			for(partition_sample = 0; partition_sample < partition_samples; partition_sample++) {
				r = residual[residual_sample++];
				/* OPT: maybe faster: rmax |= r ^ (r>>31) */
				if(r < 0)
					rmax |= ~r;
				else
					rmax |= r;
			}
			/* now we know all residual values are in the range [-rmax-1,rmax] */
			raw_bits_per_partition[partition] = rmax? FLAC__bitmath_ilog2(rmax) + 2 : 1;
		}
		to_partition = partitions;
		break; /*@@@ yuck, should remove the 'for' loop instead */
	}

	/* now merge partitions for lower orders */
	for(from_partition = 0, --partition_order; partition_order >= (int)min_partition_order; partition_order--) {
		uint32_t m;
		uint32_t i;
		const uint32_t partitions = 1u << partition_order;
		for(i = 0; i < partitions; i++) {
			m = raw_bits_per_partition[from_partition];
			from_partition++;
			raw_bits_per_partition[to_partition] = flac_max(m, raw_bits_per_partition[from_partition]);
			from_partition++;
			to_partition++;
		}
	}
}

#ifdef EXACT_RICE_BITS_CALCULATION
static inline uint32_t count_rice_bits_in_partition_(
	const uint32_t rice_parameter,
	const uint32_t partition_samples,
	const FLAC__int32 *residual
)
{
	uint32_t i, partition_bits =
		FLAC__ENTROPY_CODING_METHOD_PARTITIONED_RICE_PARAMETER_LEN + /* actually could end up being FLAC__ENTROPY_CODING_METHOD_PARTITIONED_RICE2_PARAMETER_LEN but err on side of 16bps */
		(1+rice_parameter) * partition_samples /* 1 for unary stop bit + rice_parameter for the binary portion */
	;
	for(i = 0; i < partition_samples; i++)
		partition_bits += ( (FLAC__uint32)((residual[i]<<1)^(residual[i]>>31)) >> rice_parameter );
	return partition_bits;
}
#else
static inline uint32_t count_rice_bits_in_partition_(
	const uint32_t rice_parameter,
	const uint32_t partition_samples,
	const FLAC__uint64 abs_residual_partition_sum
)
{
	return
		FLAC__ENTROPY_CODING_METHOD_PARTITIONED_RICE_PARAMETER_LEN + /* actually could end up being FLAC__ENTROPY_CODING_METHOD_PARTITIONED_RICE2_PARAMETER_LEN but err on side of 16bps */
		(1+rice_parameter) * partition_samples + /* 1 for unary stop bit + rice_parameter for the binary portion */
		(
			rice_parameter?
				(uint32_t)(abs_residual_partition_sum >> (rice_parameter-1)) /* rice_parameter-1 because the real coder sign-folds instead of using a sign bit */
				: (uint32_t)(abs_residual_partition_sum << 1) /* can't shift by negative number, so reverse */
		)
		- (partition_samples >> 1)
		/* -(partition_samples>>1) to subtract out extra contributions to the abs_residual_partition_sum.
		 * The actual number of bits used is closer to the sum(for all i in the partition) of  abs(residual[i])>>(rice_parameter-1)
		 * By using the abs_residual_partition sum, we also add in bits in the LSBs that would normally be shifted out.
		 * So the subtraction term tries to guess how many extra bits were contributed.
		 * If the LSBs are randomly distributed, this should average to 0.5 extra bits per sample.
		 */
	;
}
#endif

FLAC__bool set_partitioned_rice_(
#ifdef EXACT_RICE_BITS_CALCULATION
	const FLAC__int32 residual[],
#endif
	const FLAC__uint64 abs_residual_partition_sums[],
	const uint32_t raw_bits_per_partition[],
	const uint32_t residual_samples,
	const uint32_t predictor_order,
	const uint32_t suggested_rice_parameter,
	const uint32_t rice_parameter_limit,
	const uint32_t rice_parameter_search_dist,
	const uint32_t partition_order,
	const FLAC__bool search_for_escapes,
	FLAC__EntropyCodingMethod_PartitionedRiceContents *partitioned_rice_contents,
	uint32_t *bits
)
{
	uint32_t rice_parameter, partition_bits;
	uint32_t best_partition_bits, best_rice_parameter = 0;
	uint32_t bits_ = FLAC__ENTROPY_CODING_METHOD_TYPE_LEN + FLAC__ENTROPY_CODING_METHOD_PARTITIONED_RICE_ORDER_LEN;
	uint32_t *parameters, *raw_bits;
#ifdef ENABLE_RICE_PARAMETER_SEARCH
	uint32_t min_rice_parameter, max_rice_parameter;
#else
	(void)rice_parameter_search_dist;
#endif

	FLAC__ASSERT(suggested_rice_parameter < FLAC__ENTROPY_CODING_METHOD_PARTITIONED_RICE2_ESCAPE_PARAMETER);
	FLAC__ASSERT(rice_parameter_limit <= FLAC__ENTROPY_CODING_METHOD_PARTITIONED_RICE2_ESCAPE_PARAMETER);

	FLAC__format_entropy_coding_method_partitioned_rice_contents_ensure_size(partitioned_rice_contents, flac_max(6u, partition_order));
	parameters = partitioned_rice_contents->parameters;
	raw_bits = partitioned_rice_contents->raw_bits;

	if(partition_order == 0) {
		best_partition_bits = (uint32_t)(-1);
#ifdef ENABLE_RICE_PARAMETER_SEARCH
		if(rice_parameter_search_dist) {
			if(suggested_rice_parameter < rice_parameter_search_dist)
				min_rice_parameter = 0;
			else
				min_rice_parameter = suggested_rice_parameter - rice_parameter_search_dist;
			max_rice_parameter = suggested_rice_parameter + rice_parameter_search_dist;
			if(max_rice_parameter >= rice_parameter_limit) {
#ifndef NDEBUG
				fprintf(stderr, "clipping rice_parameter (%u -> %u) @5\n", max_rice_parameter, rice_parameter_limit - 1);
#endif
				max_rice_parameter = rice_parameter_limit - 1;
			}
		}
		else
			min_rice_parameter = max_rice_parameter = suggested_rice_parameter;

		for(rice_parameter = min_rice_parameter; rice_parameter <= max_rice_parameter; rice_parameter++) {
#else
			rice_parameter = suggested_rice_parameter;
#endif
#ifdef EXACT_RICE_BITS_CALCULATION
			partition_bits = count_rice_bits_in_partition_(rice_parameter, residual_samples, residual);
#else
			partition_bits = count_rice_bits_in_partition_(rice_parameter, residual_samples, abs_residual_partition_sums[0]);
#endif
			if(partition_bits < best_partition_bits) {
				best_rice_parameter = rice_parameter;
				best_partition_bits = partition_bits;
			}
#ifdef ENABLE_RICE_PARAMETER_SEARCH
		}
#endif
		if(search_for_escapes) {
			partition_bits = FLAC__ENTROPY_CODING_METHOD_PARTITIONED_RICE2_PARAMETER_LEN + FLAC__ENTROPY_CODING_METHOD_PARTITIONED_RICE_RAW_LEN + raw_bits_per_partition[0] * residual_samples;
			if(partition_bits <= best_partition_bits) {
				raw_bits[0] = raw_bits_per_partition[0];
				best_rice_parameter = 0; /* will be converted to appropriate escape parameter later */
				best_partition_bits = partition_bits;
			}
			else
				raw_bits[0] = 0;
		}
		parameters[0] = best_rice_parameter;
		bits_ += best_partition_bits;
	}
	else {
		uint32_t partition, residual_sample;
		uint32_t partition_samples;
		FLAC__uint64 mean, k;
		const uint32_t partitions = 1u << partition_order;
		for(partition = residual_sample = 0; partition < partitions; partition++) {
			partition_samples = (residual_samples+predictor_order) >> partition_order;
			if(partition == 0) {
				if(partition_samples <= predictor_order)
					return false;
				else
					partition_samples -= predictor_order;
			}
			mean = abs_residual_partition_sums[partition];
			/* we are basically calculating the size in bits of the
			 * average residual magnitude in the partition:
			 *   rice_parameter = floor(log2(mean/partition_samples))
			 * 'mean' is not a good name for the variable, it is
			 * actually the sum of magnitudes of all residual values
			 * in the partition, so the actual mean is
			 * mean/partition_samples
			 */
#if 0 /* old simple code */
			for(rice_parameter = 0, k = partition_samples; k < mean; rice_parameter++, k <<= 1)
				;
#else
#if defined FLAC__CPU_X86_64 /* and other 64-bit arch, too */
			if(mean <= 0x80000000/512) { /* 512: more or less optimal for both 16- and 24-bit input */
#else
			if(mean <= 0x80000000/8) { /* 32-bit arch: use 32-bit math if possible */
#endif
				FLAC__uint32 k2, mean2 = (FLAC__uint32) mean;
				rice_parameter = 0; k2 = partition_samples;
				while(k2*8 < mean2) { /* requires: mean <= (2^31)/8 */
					rice_parameter += 4; k2 <<= 4; /* tuned for 16-bit input */
				}
				while(k2 < mean2) { /* requires: mean <= 2^31 */
					rice_parameter++; k2 <<= 1;
				}
			}
			else {
				rice_parameter = 0; k = partition_samples;
				if(mean <= FLAC__U64L(0x8000000000000000)/128) /* usually mean is _much_ smaller than this value */
					while(k*128 < mean) { /* requires: mean <= (2^63)/128 */
						rice_parameter += 8; k <<= 8; /* tuned for 24-bit input */
					}
				while(k < mean) { /* requires: mean <= 2^63 */
					rice_parameter++; k <<= 1;
				}
			}
#endif
			if(rice_parameter >= rice_parameter_limit) {
#ifndef NDEBUG
				fprintf(stderr, "clipping rice_parameter (%u -> %u) @6\n", rice_parameter, rice_parameter_limit - 1);
#endif
				rice_parameter = rice_parameter_limit - 1;
			}

			best_partition_bits = (uint32_t)(-1);
#ifdef ENABLE_RICE_PARAMETER_SEARCH
			if(rice_parameter_search_dist) {
				if(rice_parameter < rice_parameter_search_dist)
					min_rice_parameter = 0;
				else
					min_rice_parameter = rice_parameter - rice_parameter_search_dist;
				max_rice_parameter = rice_parameter + rice_parameter_search_dist;
				if(max_rice_parameter >= rice_parameter_limit) {
#ifndef NDEBUG
					fprintf(stderr, "clipping rice_parameter (%u -> %u) @7\n", max_rice_parameter, rice_parameter_limit - 1);
#endif
					max_rice_parameter = rice_parameter_limit - 1;
				}
			}
			else
				min_rice_parameter = max_rice_parameter = rice_parameter;

			for(rice_parameter = min_rice_parameter; rice_parameter <= max_rice_parameter; rice_parameter++) {
#endif
#ifdef EXACT_RICE_BITS_CALCULATION
				partition_bits = count_rice_bits_in_partition_(rice_parameter, partition_samples, residual+residual_sample);
#else
				partition_bits = count_rice_bits_in_partition_(rice_parameter, partition_samples, abs_residual_partition_sums[partition]);
#endif
				if(partition_bits < best_partition_bits) {
					best_rice_parameter = rice_parameter;
					best_partition_bits = partition_bits;
				}
#ifdef ENABLE_RICE_PARAMETER_SEARCH
			}
#endif
			if(search_for_escapes) {
				partition_bits = FLAC__ENTROPY_CODING_METHOD_PARTITIONED_RICE2_PARAMETER_LEN + FLAC__ENTROPY_CODING_METHOD_PARTITIONED_RICE_RAW_LEN + raw_bits_per_partition[partition] * partition_samples;
				if(partition_bits <= best_partition_bits) {
					raw_bits[partition] = raw_bits_per_partition[partition];
					best_rice_parameter = 0; /* will be converted to appropriate escape parameter later */
					best_partition_bits = partition_bits;
				}
				else
					raw_bits[partition] = 0;
			}
			parameters[partition] = best_rice_parameter;
			bits_ += best_partition_bits;
			residual_sample += partition_samples;
		}
	}

	*bits = bits_;
	return true;
}

uint32_t get_wasted_bits_(FLAC__int32 signal[], uint32_t samples)
{
	uint32_t i, shift;
	FLAC__int32 x = 0;

	for(i = 0; i < samples && !(x&1); i++)
		x |= signal[i];

	if(x == 0) {
		shift = 0;
	}
	else {
		for(shift = 0; !(x&1); shift++)
			x >>= 1;
	}

	if(shift > 0) {
		for(i = 0; i < samples; i++)
			 signal[i] >>= shift;
	}

	return shift;
}

void append_to_verify_fifo_(verify_input_fifo *fifo, const FLAC__int32 * const input[], uint32_t input_offset, uint32_t channels, uint32_t wide_samples)
{
	uint32_t channel;

	for(channel = 0; channel < channels; channel++)
		memcpy(&fifo->data[channel][fifo->tail], &input[channel][input_offset], sizeof(FLAC__int32) * wide_samples);

	fifo->tail += wide_samples;

	FLAC__ASSERT(fifo->tail <= fifo->size);
}

void append_to_verify_fifo_interleaved_(verify_input_fifo *fifo, const FLAC__int32 input[], uint32_t input_offset, uint32_t channels, uint32_t wide_samples)
{
	uint32_t channel;
	uint32_t sample, wide_sample;
	uint32_t tail = fifo->tail;

	sample = input_offset * channels;
	for(wide_sample = 0; wide_sample < wide_samples; wide_sample++) {
		for(channel = 0; channel < channels; channel++)
			fifo->data[channel][tail] = input[sample++];
		tail++;
	}
	fifo->tail = tail;

	FLAC__ASSERT(fifo->tail <= fifo->size);
}

FLAC__StreamDecoderReadStatus verify_read_callback_(const FLAC__StreamDecoder *decoder, FLAC__byte buffer[], size_t *bytes, void *client_data)
{
	FLAC__StreamEncoder *encoder = (FLAC__StreamEncoder*)client_data;
	const size_t encoded_bytes = encoder->private_->verify.output.bytes;
	(void)decoder;

	if(encoder->private_->verify.needs_magic_hack) {
		FLAC__ASSERT(*bytes >= FLAC__STREAM_SYNC_LENGTH);
		*bytes = FLAC__STREAM_SYNC_LENGTH;
		memcpy(buffer, FLAC__STREAM_SYNC_STRING, *bytes);
		encoder->private_->verify.needs_magic_hack = false;
	}
	else {
		if(encoded_bytes == 0) {
			/*
			 * If we get here, a FIFO underflow has occurred,
			 * which means there is a bug somewhere.
			 */
			FLAC__ASSERT(0);
			return FLAC__STREAM_DECODER_READ_STATUS_ABORT;
		}
		else if(encoded_bytes < *bytes)
			*bytes = encoded_bytes;
		memcpy(buffer, encoder->private_->verify.output.data, *bytes);
		encoder->private_->verify.output.data += *bytes;
		encoder->private_->verify.output.bytes -= *bytes;
	}

	return FLAC__STREAM_DECODER_READ_STATUS_CONTINUE;
}

FLAC__StreamDecoderWriteStatus verify_write_callback_(const FLAC__StreamDecoder *decoder, const FLAC__Frame *frame, const FLAC__int32 * const buffer[], void *client_data)
{
	FLAC__StreamEncoder *encoder = (FLAC__StreamEncoder *)client_data;
	uint32_t channel;
	const uint32_t channels = frame->header.channels;
	const uint32_t blocksize = frame->header.blocksize;
	const uint32_t bytes_per_block = sizeof(FLAC__int32) * blocksize;

	(void)decoder;

	for(channel = 0; channel < channels; channel++) {
		if(0 != memcmp(buffer[channel], encoder->private_->verify.input_fifo.data[channel], bytes_per_block)) {
			uint32_t i, sample = 0;
			FLAC__int32 expect = 0, got = 0;

			for(i = 0; i < blocksize; i++) {
				if(buffer[channel][i] != encoder->private_->verify.input_fifo.data[channel][i]) {
					sample = i;
					expect = (FLAC__int32)encoder->private_->verify.input_fifo.data[channel][i];
					got = (FLAC__int32)buffer[channel][i];
					break;
				}
			}
			FLAC__ASSERT(i < blocksize);
			FLAC__ASSERT(frame->header.number_type == FLAC__FRAME_NUMBER_TYPE_SAMPLE_NUMBER);
			encoder->private_->verify.error_stats.absolute_sample = frame->header.number.sample_number + sample;
			encoder->private_->verify.error_stats.frame_number = (uint32_t)(frame->header.number.sample_number / blocksize);
			encoder->private_->verify.error_stats.channel = channel;
			encoder->private_->verify.error_stats.sample = sample;
			encoder->private_->verify.error_stats.expected = expect;
			encoder->private_->verify.error_stats.got = got;
			encoder->protected_->state = FLAC__STREAM_ENCODER_VERIFY_MISMATCH_IN_AUDIO_DATA;
			return FLAC__STREAM_DECODER_WRITE_STATUS_ABORT;
		}
	}
	/* dequeue the frame from the fifo */
	encoder->private_->verify.input_fifo.tail -= blocksize;
	FLAC__ASSERT(encoder->private_->verify.input_fifo.tail <= OVERREAD_);
	for(channel = 0; channel < channels; channel++)
		memmove(&encoder->private_->verify.input_fifo.data[channel][0], &encoder->private_->verify.input_fifo.data[channel][blocksize], encoder->private_->verify.input_fifo.tail * sizeof(encoder->private_->verify.input_fifo.data[0][0]));
	return FLAC__STREAM_DECODER_WRITE_STATUS_CONTINUE;
}

void verify_metadata_callback_(const FLAC__StreamDecoder *decoder, const FLAC__StreamMetadata *metadata, void *client_data)
{
	(void)decoder, (void)metadata, (void)client_data;
}

void verify_error_callback_(const FLAC__StreamDecoder *decoder, FLAC__StreamDecoderErrorStatus status, void *client_data)
{
	FLAC__StreamEncoder *encoder = (FLAC__StreamEncoder*)client_data;
	(void)decoder, (void)status;
	encoder->protected_->state = FLAC__STREAM_ENCODER_VERIFY_DECODER_ERROR;
}

FLAC__StreamEncoderReadStatus file_read_callback_(const FLAC__StreamEncoder *encoder, FLAC__byte buffer[], size_t *bytes, void *client_data)
{
	(void)client_data;

	*bytes = fread(buffer, 1, *bytes, encoder->private_->file);
	if (*bytes == 0) {
		if (feof(encoder->private_->file))
			return FLAC__STREAM_ENCODER_READ_STATUS_END_OF_STREAM;
		else if (ferror(encoder->private_->file))
			return FLAC__STREAM_ENCODER_READ_STATUS_ABORT;
	}
	return FLAC__STREAM_ENCODER_READ_STATUS_CONTINUE;
}

FLAC__StreamEncoderSeekStatus file_seek_callback_(const FLAC__StreamEncoder *encoder, FLAC__uint64 absolute_byte_offset, void *client_data)
{
	(void)client_data;

	if(fseeko(encoder->private_->file, (FLAC__off_t)absolute_byte_offset, SEEK_SET) < 0)
		return FLAC__STREAM_ENCODER_SEEK_STATUS_ERROR;
	else
		return FLAC__STREAM_ENCODER_SEEK_STATUS_OK;
}

FLAC__StreamEncoderTellStatus file_tell_callback_(const FLAC__StreamEncoder *encoder, FLAC__uint64 *absolute_byte_offset, void *client_data)
{
	FLAC__off_t offset;

	(void)client_data;

	offset = ftello(encoder->private_->file);

	if(offset < 0) {
		return FLAC__STREAM_ENCODER_TELL_STATUS_ERROR;
	}
	else {
		*absolute_byte_offset = (FLAC__uint64)offset;
		return FLAC__STREAM_ENCODER_TELL_STATUS_OK;
	}
}

#ifdef FLAC__VALGRIND_TESTING
static size_t local__fwrite(const void *ptr, size_t size, size_t nmemb, FILE *stream)
{
	size_t ret = fwrite(ptr, size, nmemb, stream);
	if(!ferror(stream))
		fflush(stream);
	return ret;
}
#else
#define local__fwrite fwrite
#endif

FLAC__StreamEncoderWriteStatus file_write_callback_(const FLAC__StreamEncoder *encoder, const FLAC__byte buffer[], size_t bytes, uint32_t samples, uint32_t current_frame, void *client_data)
{
	(void)client_data, (void)current_frame;

	if(local__fwrite(buffer, sizeof(FLAC__byte), bytes, encoder->private_->file) == bytes) {
		FLAC__bool call_it = 0 != encoder->private_->progress_callback && (
#if FLAC__HAS_OGG
			/* We would like to be able to use 'samples > 0' in the
			 * clause here but currently because of the nature of our
			 * Ogg writing implementation, 'samples' is always 0 (see
			 * ogg_encoder_aspect.c).  The downside is extra progress
			 * callbacks.
			 */
			encoder->private_->is_ogg? true :
#endif
			samples > 0
		);
		if(call_it) {
			/* NOTE: We have to add +bytes, +samples, and +1 to the stats
			 * because at this point in the callback chain, the stats
			 * have not been updated.  Only after we return and control
			 * gets back to write_frame_() are the stats updated
			 */
			encoder->private_->progress_callback(encoder, encoder->private_->bytes_written+bytes, encoder->private_->samples_written+samples, encoder->private_->frames_written+(samples?1:0), encoder->private_->total_frames_estimate, encoder->private_->client_data);
		}
		return FLAC__STREAM_ENCODER_WRITE_STATUS_OK;
	}
	else
		return FLAC__STREAM_ENCODER_WRITE_STATUS_FATAL_ERROR;
}

/*
 * This will forcibly set stdout to binary mode (for OSes that require it)
 */
FILE *get_binary_stdout_(void)
{
	/* if something breaks here it is probably due to the presence or
	 * absence of an underscore before the identifiers 'setmode',
	 * 'fileno', and/or 'O_BINARY'; check your system header files.
	 */
#if defined _MSC_VER || defined __MINGW32__
	_setmode(_fileno(stdout), _O_BINARY);
#elif defined __EMX__
	setmode(fileno(stdout), O_BINARY);
#endif

	return stdout;
}
