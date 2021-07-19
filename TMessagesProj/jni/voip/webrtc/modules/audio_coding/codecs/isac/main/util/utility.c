/*
 *  Copyright (c) 2011 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include <stdlib.h>
#include <stdio.h>
#include <string.h>
#include <math.h>

#include "modules/audio_coding/codecs/isac/main/util/utility.h"

/* function for reading audio data from PCM file */
int
readframe(
    short* data,
    FILE*  inp,
    int    length)
{
    short k, rlen, status = 0;
	unsigned char* ptrUChar;
	ptrUChar = (unsigned char*)data;

    rlen = (short)fread(data, sizeof(short), length, inp);
    if (rlen < length) {
        for (k = rlen; k < length; k++)
            data[k] = 0;
        status = 1;
    }

	// Assuming that our PCM files are written in Intel machines
	for(k = 0; k < length; k++)
	{
		data[k] = (short)ptrUChar[k<<1] | ((((short)ptrUChar[(k<<1) + 1]) << 8) & 0xFF00);
	}

    return status;
}

short
readSwitch(
    int   argc,
    char* argv[],
    char* strID)
{
    short n;
    for(n = 0; n < argc; n++)
    {
        if(strcmp(argv[n], strID) == 0)
        {
            return 1;
        }
    }
    return 0;
}

double
readParamDouble(
    int    argc,
    char*  argv[],
    char*  strID,
    double defaultVal)
{
    double returnVal = defaultVal;
    short n;
    for(n = 0; n < argc; n++)
    {
        if(strcmp(argv[n], strID) == 0)
        {
            n++;
            if(n < argc)
            {
                returnVal = atof(argv[n]);
            }
            break;
        }
    }
    return returnVal;
}

int
readParamInt(
    int   argc,
    char* argv[],
    char* strID,
    int   defaultVal)
{
    int returnVal = defaultVal;
    short n;
    for(n = 0; n < argc; n++)
    {
        if(strcmp(argv[n], strID) == 0)
        {
            n++;
            if(n < argc)
            {
                returnVal = atoi(argv[n]);
            }
            break;
        }
    }
    return returnVal;
}

int
readParamString(
    int   argc,
    char* argv[],
    char* strID,
    char* stringParam,
    int   maxSize)
{
    int paramLenght = 0;
    short n;
    for(n = 0; n < argc; n++)
    {
        if(strcmp(argv[n], strID) == 0)
        {
            n++;
            if(n < argc)
            {
                strncpy(stringParam, argv[n], maxSize);
                paramLenght = (int)strlen(argv[n]);
            }
            break;
        }
    }
    return paramLenght;
}

void
get_arrival_time(
    int              current_framesamples,   /* samples */
    size_t           packet_size,            /* bytes */
    int              bottleneck,             /* excluding headers; bits/s */
    BottleNeckModel* BN_data,
    short            senderSampFreqHz,
    short            receiverSampFreqHz)
{
    unsigned int travelTimeMs;
	const int headerSizeByte = 35;

	int headerRate;

    BN_data->whenPackGeneratedMs += (current_framesamples / (senderSampFreqHz / 1000));

	headerRate = headerSizeByte * 8 * senderSampFreqHz / current_framesamples;     /* bits/s */

	/* everything in samples */
	BN_data->sample_count = BN_data->sample_count + current_framesamples;

    //travelTimeMs = ((packet_size + HeaderSize) * 8 * sampFreqHz) /
    //    (bottleneck + HeaderRate)
    travelTimeMs = (unsigned int)floor((double)((packet_size + headerSizeByte) * 8 * 1000)
        / (double)(bottleneck + headerRate) + 0.5);

    if(BN_data->whenPrevPackLeftMs > BN_data->whenPackGeneratedMs)
    {
        BN_data->whenPrevPackLeftMs += travelTimeMs;
    }
    else
    {
        BN_data->whenPrevPackLeftMs = BN_data->whenPackGeneratedMs +
            travelTimeMs;
    }

    BN_data->arrival_time = (BN_data->whenPrevPackLeftMs *
        (receiverSampFreqHz / 1000));

//	if (BN_data->arrival_time < BN_data->sample_count)
//		BN_data->arrival_time = BN_data->sample_count;

	BN_data->rtp_number++;
}
