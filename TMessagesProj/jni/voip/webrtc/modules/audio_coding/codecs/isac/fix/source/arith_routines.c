/*
 *  Copyright (c) 2011 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

/*
 * arith_routins.c
 *
 * This C file contains a function for finalizing the bitstream
 * after arithmetic coding.
 *
 */

#include "modules/audio_coding/codecs/isac/fix/source/arith_routins.h"


/****************************************************************************
 * WebRtcIsacfix_EncTerminate(...)
 *
 * Final call to the arithmetic coder for an encoder call. This function
 * terminates and return byte stream.
 *
 * Input:
 *      - streamData        : in-/output struct containing bitstream
 *
 * Return value             : number of bytes in the stream
 */
int16_t WebRtcIsacfix_EncTerminate(Bitstr_enc *streamData)
{
  uint16_t *streamPtr;
  uint16_t negCarry;

  /* point to the right place in the stream buffer */
  streamPtr = streamData->stream + streamData->stream_index;

  /* find minimum length (determined by current interval width) */
  if ( streamData->W_upper > 0x01FFFFFF )
  {
    streamData->streamval += 0x01000000;

    /* if result is less than the added value we must take care of the carry */
    if (streamData->streamval < 0x01000000)
    {
      /* propagate carry */
      if (streamData->full == 0) {
        /* Add value to current value */
        negCarry = *streamPtr;
        negCarry += 0x0100;
        *streamPtr = negCarry;

        /* if value is too big, propagate carry to next byte, and so on */
        while (!(negCarry))
        {
          negCarry = *--streamPtr;
          negCarry++;
          *streamPtr = negCarry;
        }
      } else {
        /* propagate carry by adding one to the previous byte in the
         * stream if that byte is 0xFFFF we need to propagate the carry
         * furhter back in the stream */
        while ( !(++(*--streamPtr)) );
      }

      /* put pointer back to the old value */
      streamPtr = streamData->stream + streamData->stream_index;
    }
    /* write remaining data to bitstream, if "full == 0" first byte has data */
    if (streamData->full == 0) {
      *streamPtr++ += (uint16_t)(streamData->streamval >> 24);
      streamData->full = 1;
    } else {
      *streamPtr = (uint16_t)((streamData->streamval >> 24) << 8);
      streamData->full = 0;
    }
  }
  else
  {
    streamData->streamval += 0x00010000;

    /* if result is less than the added value we must take care of the carry */
    if (streamData->streamval < 0x00010000)
    {
      /* propagate carry */
      if (streamData->full == 0) {
        /* Add value to current value */
        negCarry = *streamPtr;
        negCarry += 0x0100;
        *streamPtr = negCarry;

        /* if value to big, propagate carry to next byte, and so on */
        while (!(negCarry))
        {
          negCarry = *--streamPtr;
          negCarry++;
          *streamPtr = negCarry;
        }
      } else {
        /* Add carry to previous byte */
        while ( !(++(*--streamPtr)) );
      }

      /* put pointer back to the old value */
      streamPtr = streamData->stream + streamData->stream_index;
    }
    /* write remaining data (2 bytes) to bitstream */
    if (streamData->full) {
      *streamPtr++ = (uint16_t)(streamData->streamval >> 16);
    } else {
      *streamPtr++ |= (uint16_t)(streamData->streamval >> 24);
      *streamPtr = (uint16_t)(streamData->streamval >> 8) & 0xFF00;
    }
  }

  /* calculate stream length in bytes */
  return (((streamPtr - streamData->stream)<<1) + !(streamData->full));
}
