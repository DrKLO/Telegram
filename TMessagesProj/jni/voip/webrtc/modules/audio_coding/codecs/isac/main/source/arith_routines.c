/*
 *  Copyright (c) 2011 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_coding/codecs/isac/main/source/arith_routines.h"
#include "modules/audio_coding/codecs/isac/main/source/settings.h"


/*
 * terminate and return byte stream;
 * returns the number of bytes in the stream
 */
int WebRtcIsac_EncTerminate(Bitstr *streamdata) /* in-/output struct containing bitstream */
{
  uint8_t *stream_ptr;


  /* point to the right place in the stream buffer */
  stream_ptr = streamdata->stream + streamdata->stream_index;

  /* find minimum length (determined by current interval width) */
  if ( streamdata->W_upper > 0x01FFFFFF )
  {
    streamdata->streamval += 0x01000000;
    /* add carry to buffer */
    if (streamdata->streamval < 0x01000000)
    {
      /* propagate carry */
      while ( !(++(*--stream_ptr)) );
      /* put pointer back to the old value */
      stream_ptr = streamdata->stream + streamdata->stream_index;
    }
    /* write remaining data to bitstream */
    *stream_ptr++ = (uint8_t) (streamdata->streamval >> 24);
  }
  else
  {
    streamdata->streamval += 0x00010000;
    /* add carry to buffer */
    if (streamdata->streamval < 0x00010000)
    {
      /* propagate carry */
      while ( !(++(*--stream_ptr)) );
      /* put pointer back to the old value */
      stream_ptr = streamdata->stream + streamdata->stream_index;
    }
    /* write remaining data to bitstream */
    *stream_ptr++ = (uint8_t) (streamdata->streamval >> 24);
    *stream_ptr++ = (uint8_t) ((streamdata->streamval >> 16) & 0x00FF);
  }

  /* calculate stream length */
  return (int)(stream_ptr - streamdata->stream);
}
