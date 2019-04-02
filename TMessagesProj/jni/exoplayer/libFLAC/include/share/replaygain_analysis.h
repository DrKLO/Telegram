/*
 *  ReplayGainAnalysis - analyzes input samples and give the recommended dB change
 *  Copyright (C) 2001 David Robinson and Glen Sawyer
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 *
 *  concept and filter values by David Robinson (David@Robinson.org)
 *    -- blame him if you think the idea is flawed
 *  coding by Glen Sawyer (glensawyer@hotmail.com) 442 N 700 E, Provo, UT 84606 USA
 *    -- blame him if you think this runs too slowly, or the coding is otherwise flawed
 *  minor cosmetic tweaks to integrate with FLAC by Josh Coalson
 *
 *  For an explanation of the concepts and the basic algorithms involved, go to:
 *    http://www.replaygain.org/
 */

#ifndef GAIN_ANALYSIS_H
#define GAIN_ANALYSIS_H

#include <stddef.h>

#define GAIN_NOT_ENOUGH_SAMPLES  -24601
#define GAIN_ANALYSIS_ERROR           0
#define GAIN_ANALYSIS_OK              1

#define INIT_GAIN_ANALYSIS_ERROR      0
#define INIT_GAIN_ANALYSIS_OK         1

#ifdef __cplusplus
extern "C" {
#endif

typedef float   flac_float_t;         /* Type used for filtering */

extern flac_float_t ReplayGainReferenceLoudness; /* in dB SPL, currently == 89.0 */

int     InitGainAnalysis ( long samplefreq );
int     ValidGainFrequency ( long samplefreq );
int     AnalyzeSamples   ( const flac_float_t* left_samples, const flac_float_t* right_samples, size_t num_samples, int num_channels );
flac_float_t GetTitleGain     ( void );
flac_float_t GetAlbumGain     ( void );

#ifdef __cplusplus
}
#endif

#endif /* GAIN_ANALYSIS_H */
