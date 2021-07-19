/* libFLAC - Free Lossless Audio Codec library
 * Copyright (C) 2013-2016  Xiph.org Foundation
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

#ifndef FLAC__SHARE__PRIVATE_H
#define FLAC__SHARE__PRIVATE_H

/*
 * Unpublished debug routines from libFLAC. This should not be used from any
 * client code other than code shipped with the FLAC sources.
 */
FLAC_API FLAC__bool FLAC__stream_encoder_disable_constant_subframes(FLAC__StreamEncoder *encoder, FLAC__bool value);
FLAC_API FLAC__bool FLAC__stream_encoder_disable_fixed_subframes(FLAC__StreamEncoder *encoder, FLAC__bool value);
FLAC_API FLAC__bool FLAC__stream_encoder_disable_verbatim_subframes(FLAC__StreamEncoder *encoder, FLAC__bool value);
FLAC_API FLAC__bool FLAC__stream_encoder_set_do_md5(FLAC__StreamEncoder *encoder, FLAC__bool value);
FLAC_API FLAC__bool FLAC__stream_encoder_get_do_md5(const FLAC__StreamEncoder *encoder);

#endif /* FLAC__SHARE__PRIVATE_H */
