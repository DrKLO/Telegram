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

#ifndef FLAC__PRIVATE__LPC_H
#define FLAC__PRIVATE__LPC_H

#ifdef HAVE_CONFIG_H
#include <config.h>
#endif

#include "private/cpu.h"
#include "private/float.h"
#include "FLAC/format.h"

#ifndef FLAC__INTEGER_ONLY_LIBRARY

/*
 *	FLAC__lpc_window_data()
 *	--------------------------------------------------------------------
 *	Applies the given window to the data.
 *  OPT: asm implementation
 *
 *	IN in[0,data_len-1]
 *	IN window[0,data_len-1]
 *	OUT out[0,lag-1]
 *	IN data_len
 */
void FLAC__lpc_window_data(const FLAC__int32 in[], const FLAC__real window[], FLAC__real out[], uint32_t data_len);

/*
 *	FLAC__lpc_compute_autocorrelation()
 *	--------------------------------------------------------------------
 *	Compute the autocorrelation for lags between 0 and lag-1.
 *	Assumes data[] outside of [0,data_len-1] == 0.
 *	Asserts that lag > 0.
 *
 *	IN data[0,data_len-1]
 *	IN data_len
 *	IN 0 < lag <= data_len
 *	OUT autoc[0,lag-1]
 */
void FLAC__lpc_compute_autocorrelation(const FLAC__real data[], uint32_t data_len, uint32_t lag, FLAC__real autoc[]);
#ifndef FLAC__NO_ASM
#  ifdef FLAC__CPU_IA32
#    ifdef FLAC__HAS_NASM
void FLAC__lpc_compute_autocorrelation_asm_ia32(const FLAC__real data[], uint32_t data_len, uint32_t lag, FLAC__real autoc[]);
void FLAC__lpc_compute_autocorrelation_asm_ia32_sse_lag_4_old(const FLAC__real data[], uint32_t data_len, uint32_t lag, FLAC__real autoc[]);
void FLAC__lpc_compute_autocorrelation_asm_ia32_sse_lag_8_old(const FLAC__real data[], uint32_t data_len, uint32_t lag, FLAC__real autoc[]);
void FLAC__lpc_compute_autocorrelation_asm_ia32_sse_lag_12_old(const FLAC__real data[], uint32_t data_len, uint32_t lag, FLAC__real autoc[]);
void FLAC__lpc_compute_autocorrelation_asm_ia32_sse_lag_16_old(const FLAC__real data[], uint32_t data_len, uint32_t lag, FLAC__real autoc[]);
#    endif
#  endif
#  if (defined FLAC__CPU_IA32 || defined FLAC__CPU_X86_64) && FLAC__HAS_X86INTRIN
#    ifdef FLAC__SSE_SUPPORTED
void FLAC__lpc_compute_autocorrelation_intrin_sse_lag_4_old(const FLAC__real data[], uint32_t data_len, uint32_t lag, FLAC__real autoc[]);
void FLAC__lpc_compute_autocorrelation_intrin_sse_lag_8_old(const FLAC__real data[], uint32_t data_len, uint32_t lag, FLAC__real autoc[]);
void FLAC__lpc_compute_autocorrelation_intrin_sse_lag_12_old(const FLAC__real data[], uint32_t data_len, uint32_t lag, FLAC__real autoc[]);
void FLAC__lpc_compute_autocorrelation_intrin_sse_lag_16_old(const FLAC__real data[], uint32_t data_len, uint32_t lag, FLAC__real autoc[]);
void FLAC__lpc_compute_autocorrelation_intrin_sse_lag_4_new(const FLAC__real data[], uint32_t data_len, uint32_t lag, FLAC__real autoc[]);
void FLAC__lpc_compute_autocorrelation_intrin_sse_lag_8_new(const FLAC__real data[], uint32_t data_len, uint32_t lag, FLAC__real autoc[]);
void FLAC__lpc_compute_autocorrelation_intrin_sse_lag_12_new(const FLAC__real data[], uint32_t data_len, uint32_t lag, FLAC__real autoc[]);
void FLAC__lpc_compute_autocorrelation_intrin_sse_lag_16_new(const FLAC__real data[], uint32_t data_len, uint32_t lag, FLAC__real autoc[]);
#    endif
#  endif
#endif

/*
 *	FLAC__lpc_compute_lp_coefficients()
 *	--------------------------------------------------------------------
 *	Computes LP coefficients for orders 1..max_order.
 *	Do not call if autoc[0] == 0.0.  This means the signal is zero
 *	and there is no point in calculating a predictor.
 *
 *	IN autoc[0,max_order]                      autocorrelation values
 *	IN 0 < max_order <= FLAC__MAX_LPC_ORDER    max LP order to compute
 *	OUT lp_coeff[0,max_order-1][0,max_order-1] LP coefficients for each order
 *	*** IMPORTANT:
 *	*** lp_coeff[0,max_order-1][max_order,FLAC__MAX_LPC_ORDER-1] are untouched
 *	OUT error[0,max_order-1]                   error for each order (more
 *	                                           specifically, the variance of
 *	                                           the error signal times # of
 *	                                           samples in the signal)
 *
 *	Example: if max_order is 9, the LP coefficients for order 9 will be
 *	         in lp_coeff[8][0,8], the LP coefficients for order 8 will be
 *			 in lp_coeff[7][0,7], etc.
 */
void FLAC__lpc_compute_lp_coefficients(const FLAC__real autoc[], uint32_t *max_order, FLAC__real lp_coeff[][FLAC__MAX_LPC_ORDER], double error[]);

/*
 *	FLAC__lpc_quantize_coefficients()
 *	--------------------------------------------------------------------
 *	Quantizes the LP coefficients.  NOTE: precision + bits_per_sample
 *	must be less than 32 (sizeof(FLAC__int32)*8).
 *
 *	IN lp_coeff[0,order-1]    LP coefficients
 *	IN order                  LP order
 *	IN FLAC__MIN_QLP_COEFF_PRECISION < precision
 *	                          desired precision (in bits, including sign
 *	                          bit) of largest coefficient
 *	OUT qlp_coeff[0,order-1]  quantized coefficients
 *	OUT shift                 # of bits to shift right to get approximated
 *	                          LP coefficients.  NOTE: could be negative.
 *	RETURN 0 => quantization OK
 *	       1 => coefficients require too much shifting for *shift to
 *              fit in the LPC subframe header.  'shift' is unset.
 *         2 => coefficients are all zero, which is bad.  'shift' is
 *              unset.
 */
int FLAC__lpc_quantize_coefficients(const FLAC__real lp_coeff[], uint32_t order, uint32_t precision, FLAC__int32 qlp_coeff[], int *shift);

/*
 *	FLAC__lpc_compute_residual_from_qlp_coefficients()
 *	--------------------------------------------------------------------
 *	Compute the residual signal obtained from sutracting the predicted
 *	signal from the original.
 *
 *	IN data[-order,data_len-1] original signal (NOTE THE INDICES!)
 *	IN data_len                length of original signal
 *	IN qlp_coeff[0,order-1]    quantized LP coefficients
 *	IN order > 0               LP order
 *	IN lp_quantization         quantization of LP coefficients in bits
 *	OUT residual[0,data_len-1] residual signal
 */
void FLAC__lpc_compute_residual_from_qlp_coefficients(const FLAC__int32 *data, uint32_t data_len, const FLAC__int32 qlp_coeff[], uint32_t order, int lp_quantization, FLAC__int32 residual[]);
void FLAC__lpc_compute_residual_from_qlp_coefficients_wide(const FLAC__int32 *data, uint32_t data_len, const FLAC__int32 qlp_coeff[], uint32_t order, int lp_quantization, FLAC__int32 residual[]);
#ifndef FLAC__NO_ASM
#  ifdef FLAC__CPU_IA32
#    ifdef FLAC__HAS_NASM
void FLAC__lpc_compute_residual_from_qlp_coefficients_asm_ia32(const FLAC__int32 *data, uint32_t data_len, const FLAC__int32 qlp_coeff[], uint32_t order, int lp_quantization, FLAC__int32 residual[]);
void FLAC__lpc_compute_residual_from_qlp_coefficients_asm_ia32_mmx(const FLAC__int32 *data, uint32_t data_len, const FLAC__int32 qlp_coeff[], uint32_t order, int lp_quantization, FLAC__int32 residual[]);
void FLAC__lpc_compute_residual_from_qlp_coefficients_wide_asm_ia32(const FLAC__int32 *data, uint32_t data_len, const FLAC__int32 qlp_coeff[], uint32_t order, int lp_quantization, FLAC__int32 residual[]);
#    endif
#  endif
#  if (defined FLAC__CPU_IA32 || defined FLAC__CPU_X86_64) && FLAC__HAS_X86INTRIN
#    ifdef FLAC__SSE2_SUPPORTED
void FLAC__lpc_compute_residual_from_qlp_coefficients_16_intrin_sse2(const FLAC__int32 *data, uint32_t data_len, const FLAC__int32 qlp_coeff[], uint32_t order, int lp_quantization, FLAC__int32 residual[]);
void FLAC__lpc_compute_residual_from_qlp_coefficients_intrin_sse2(const FLAC__int32 *data, uint32_t data_len, const FLAC__int32 qlp_coeff[], uint32_t order, int lp_quantization, FLAC__int32 residual[]);
#    endif
#    ifdef FLAC__SSE4_1_SUPPORTED
void FLAC__lpc_compute_residual_from_qlp_coefficients_intrin_sse41(const FLAC__int32 *data, uint32_t data_len, const FLAC__int32 qlp_coeff[], uint32_t order, int lp_quantization, FLAC__int32 residual[]);
void FLAC__lpc_compute_residual_from_qlp_coefficients_wide_intrin_sse41(const FLAC__int32 *data, uint32_t data_len, const FLAC__int32 qlp_coeff[], uint32_t order, int lp_quantization, FLAC__int32 residual[]);
#    endif
#    ifdef FLAC__AVX2_SUPPORTED
void FLAC__lpc_compute_residual_from_qlp_coefficients_16_intrin_avx2(const FLAC__int32 *data, uint32_t data_len, const FLAC__int32 qlp_coeff[], uint32_t order, int lp_quantization, FLAC__int32 residual[]);
void FLAC__lpc_compute_residual_from_qlp_coefficients_intrin_avx2(const FLAC__int32 *data, uint32_t data_len, const FLAC__int32 qlp_coeff[], uint32_t order, int lp_quantization, FLAC__int32 residual[]);
void FLAC__lpc_compute_residual_from_qlp_coefficients_wide_intrin_avx2(const FLAC__int32 *data, uint32_t data_len, const FLAC__int32 qlp_coeff[], uint32_t order, int lp_quantization, FLAC__int32 residual[]);
#    endif
#  endif
#endif

#endif /* !defined FLAC__INTEGER_ONLY_LIBRARY */

/*
 *	FLAC__lpc_restore_signal()
 *	--------------------------------------------------------------------
 *	Restore the original signal by summing the residual and the
 *	predictor.
 *
 *	IN residual[0,data_len-1]  residual signal
 *	IN data_len                length of original signal
 *	IN qlp_coeff[0,order-1]    quantized LP coefficients
 *	IN order > 0               LP order
 *	IN lp_quantization         quantization of LP coefficients in bits
 *	*** IMPORTANT: the caller must pass in the historical samples:
 *	IN  data[-order,-1]        previously-reconstructed historical samples
 *	OUT data[0,data_len-1]     original signal
 */
void FLAC__lpc_restore_signal(const FLAC__int32 residual[], uint32_t data_len, const FLAC__int32 qlp_coeff[], uint32_t order, int lp_quantization, FLAC__int32 data[]);
void FLAC__lpc_restore_signal_wide(const FLAC__int32 residual[], uint32_t data_len, const FLAC__int32 qlp_coeff[], uint32_t order, int lp_quantization, FLAC__int32 data[]);
#ifndef FLAC__NO_ASM
#  ifdef FLAC__CPU_IA32
#    ifdef FLAC__HAS_NASM
void FLAC__lpc_restore_signal_asm_ia32(const FLAC__int32 residual[], uint32_t data_len, const FLAC__int32 qlp_coeff[], uint32_t order, int lp_quantization, FLAC__int32 data[]);
void FLAC__lpc_restore_signal_asm_ia32_mmx(const FLAC__int32 residual[], uint32_t data_len, const FLAC__int32 qlp_coeff[], uint32_t order, int lp_quantization, FLAC__int32 data[]);
void FLAC__lpc_restore_signal_wide_asm_ia32(const FLAC__int32 residual[], uint32_t data_len, const FLAC__int32 qlp_coeff[], uint32_t order, int lp_quantization, FLAC__int32 data[]);
#    endif /* FLAC__HAS_NASM */
#  endif /* FLAC__CPU_IA32 */
#  if (defined FLAC__CPU_IA32 || defined FLAC__CPU_X86_64) && FLAC__HAS_X86INTRIN
#    ifdef FLAC__SSE4_1_SUPPORTED
void FLAC__lpc_restore_signal_intrin_sse41(const FLAC__int32 residual[], uint32_t data_len, const FLAC__int32 qlp_coeff[], uint32_t order, int lp_quantization, FLAC__int32 data[]);
void FLAC__lpc_restore_signal_16_intrin_sse41(const FLAC__int32 residual[], uint32_t data_len, const FLAC__int32 qlp_coeff[], uint32_t order, int lp_quantization, FLAC__int32 data[]);
void FLAC__lpc_restore_signal_wide_intrin_sse41(const FLAC__int32 residual[], uint32_t data_len, const FLAC__int32 qlp_coeff[], uint32_t order, int lp_quantization, FLAC__int32 data[]);
#    endif
#  endif
#endif /* FLAC__NO_ASM */

#ifndef FLAC__INTEGER_ONLY_LIBRARY

/*
 *	FLAC__lpc_compute_expected_bits_per_residual_sample()
 *	--------------------------------------------------------------------
 *	Compute the expected number of bits per residual signal sample
 *	based on the LP error (which is related to the residual variance).
 *
 *	IN lpc_error >= 0.0   error returned from calculating LP coefficients
 *	IN total_samples > 0  # of samples in residual signal
 *	RETURN                expected bits per sample
 */
double FLAC__lpc_compute_expected_bits_per_residual_sample(double lpc_error, uint32_t total_samples);
double FLAC__lpc_compute_expected_bits_per_residual_sample_with_error_scale(double lpc_error, double error_scale);

/*
 *	FLAC__lpc_compute_best_order()
 *	--------------------------------------------------------------------
 *	Compute the best order from the array of signal errors returned
 *	during coefficient computation.
 *
 *	IN lpc_error[0,max_order-1] >= 0.0  error returned from calculating LP coefficients
 *	IN max_order > 0                    max LP order
 *	IN total_samples > 0                # of samples in residual signal
 *	IN overhead_bits_per_order          # of bits overhead for each increased LP order
 *	                                    (includes warmup sample size and quantized LP coefficient)
 *	RETURN [1,max_order]                best order
 */
uint32_t FLAC__lpc_compute_best_order(const double lpc_error[], uint32_t max_order, uint32_t total_samples, uint32_t overhead_bits_per_order);

#endif /* !defined FLAC__INTEGER_ONLY_LIBRARY */

#endif
