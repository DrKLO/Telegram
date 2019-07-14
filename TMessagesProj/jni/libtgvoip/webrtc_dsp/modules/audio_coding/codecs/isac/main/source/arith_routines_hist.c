/*
 *  Copyright (c) 2011 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_coding/codecs/isac/main/source/settings.h"
#include "modules/audio_coding/codecs/isac/main/source/arith_routines.h"


/*
 * code symbols into arithmetic bytestream
 */
void WebRtcIsac_EncHistMulti(Bitstr *streamdata, /* in-/output struct containing bitstream */
                             const int *data,  /* input: data vector */
                             const uint16_t *const *cdf, /* input: array of cdf arrays */
                             const int N)   /* input: data vector length */
{
  uint32_t W_lower, W_upper;
  uint32_t W_upper_LSB, W_upper_MSB;
  uint8_t *stream_ptr;
  uint8_t *stream_ptr_carry;
  uint32_t cdf_lo, cdf_hi;
  int k;


  /* point to beginning of stream buffer */
  stream_ptr = streamdata->stream + streamdata->stream_index;
  W_upper = streamdata->W_upper;

  for (k=N; k>0; k--)
  {
    /* fetch cdf_lower and cdf_upper from cdf tables */
    cdf_lo = (uint32_t) *(*cdf + *data);
    cdf_hi = (uint32_t) *(*cdf++ + *data++ + 1);

    /* update interval */
    W_upper_LSB = W_upper & 0x0000FFFF;
    W_upper_MSB = W_upper >> 16;
    W_lower = W_upper_MSB * cdf_lo;
    W_lower += (W_upper_LSB * cdf_lo) >> 16;
    W_upper = W_upper_MSB * cdf_hi;
    W_upper += (W_upper_LSB * cdf_hi) >> 16;

    /* shift interval such that it begins at zero */
    W_upper -= ++W_lower;

    /* add integer to bitstream */
    streamdata->streamval += W_lower;

    /* handle carry */
    if (streamdata->streamval < W_lower)
    {
      /* propagate carry */
      stream_ptr_carry = stream_ptr;
      while (!(++(*--stream_ptr_carry)));
    }

    /* renormalize interval, store most significant byte of streamval and update streamval */
    while ( !(W_upper & 0xFF000000) )      /* W_upper < 2^24 */
    {
      W_upper <<= 8;
      *stream_ptr++ = (uint8_t) (streamdata->streamval >> 24);
      streamdata->streamval <<= 8;
    }
  }

  /* calculate new stream_index */
  streamdata->stream_index = (int)(stream_ptr - streamdata->stream);
  streamdata->W_upper = W_upper;

  return;
}



/*
 * function to decode more symbols from the arithmetic bytestream, using method of bisection
 * cdf tables should be of size 2^k-1 (which corresponds to an alphabet size of 2^k-2)
 */
int WebRtcIsac_DecHistBisectMulti(int *data,     /* output: data vector */
                                  Bitstr *streamdata,   /* in-/output struct containing bitstream */
                                  const uint16_t *const *cdf,  /* input: array of cdf arrays */
                                  const uint16_t *cdf_size, /* input: array of cdf table sizes+1 (power of two: 2^k) */
                                  const int N)    /* input: data vector length */
{
  uint32_t    W_lower, W_upper;
  uint32_t    W_tmp;
  uint32_t    W_upper_LSB, W_upper_MSB;
  uint32_t    streamval;
  const   uint8_t *stream_ptr;
  const   uint16_t *cdf_ptr;
  int     size_tmp;
  int     k;

  W_lower = 0; //to remove warning -DH
  stream_ptr = streamdata->stream + streamdata->stream_index;
  W_upper = streamdata->W_upper;
  if (W_upper == 0)
    /* Should not be possible in normal operation */
    return -2;

  if (streamdata->stream_index == 0)   /* first time decoder is called for this stream */
  {
    /* read first word from bytestream */
    streamval = *stream_ptr << 24;
    streamval |= *++stream_ptr << 16;
    streamval |= *++stream_ptr << 8;
    streamval |= *++stream_ptr;
  } else {
    streamval = streamdata->streamval;
  }

  for (k=N; k>0; k--)
  {
    /* find the integer *data for which streamval lies in [W_lower+1, W_upper] */
    W_upper_LSB = W_upper & 0x0000FFFF;
    W_upper_MSB = W_upper >> 16;

    /* start halfway the cdf range */
    size_tmp = *cdf_size++ >> 1;
    cdf_ptr = *cdf + (size_tmp - 1);

    /* method of bisection */
    for ( ;; )
    {
      W_tmp = W_upper_MSB * *cdf_ptr;
      W_tmp += (W_upper_LSB * *cdf_ptr) >> 16;
      size_tmp >>= 1;
      if (size_tmp == 0) break;
      if (streamval > W_tmp)
      {
        W_lower = W_tmp;
        cdf_ptr += size_tmp;
      } else {
        W_upper = W_tmp;
        cdf_ptr -= size_tmp;
      }
    }
    if (streamval > W_tmp)
    {
      W_lower = W_tmp;
      *data++ = (int)(cdf_ptr - *cdf++);
    } else {
      W_upper = W_tmp;
      *data++ = (int)(cdf_ptr - *cdf++ - 1);
    }

    /* shift interval to start at zero */
    W_upper -= ++W_lower;

    /* add integer to bitstream */
    streamval -= W_lower;

    /* renormalize interval and update streamval */
    while ( !(W_upper & 0xFF000000) )    /* W_upper < 2^24 */
    {
      /* read next byte from stream */
      streamval = (streamval << 8) | *++stream_ptr;
      W_upper <<= 8;
    }

    if (W_upper == 0)
      /* Should not be possible in normal operation */
      return -2;


  }

  streamdata->stream_index = (int)(stream_ptr - streamdata->stream);
  streamdata->W_upper = W_upper;
  streamdata->streamval = streamval;


  /* find number of bytes in original stream (determined by current interval width) */
  if ( W_upper > 0x01FFFFFF )
    return streamdata->stream_index - 2;
  else
    return streamdata->stream_index - 1;
}



/*
 * function to decode more symbols from the arithmetic bytestream, taking single step up or
 * down at a time
 * cdf tables can be of arbitrary size, but large tables may take a lot of iterations
 */
int WebRtcIsac_DecHistOneStepMulti(int *data,        /* output: data vector */
                                   Bitstr *streamdata,      /* in-/output struct containing bitstream */
                                   const uint16_t *const *cdf,   /* input: array of cdf arrays */
                                   const uint16_t *init_index, /* input: vector of initial cdf table search entries */
                                   const int N)     /* input: data vector length */
{
  uint32_t    W_lower, W_upper;
  uint32_t    W_tmp;
  uint32_t    W_upper_LSB, W_upper_MSB;
  uint32_t    streamval;
  const   uint8_t *stream_ptr;
  const   uint16_t *cdf_ptr;
  int     k;


  stream_ptr = streamdata->stream + streamdata->stream_index;
  W_upper = streamdata->W_upper;
  if (W_upper == 0)
    /* Should not be possible in normal operation */
    return -2;

  if (streamdata->stream_index == 0)   /* first time decoder is called for this stream */
  {
    /* read first word from bytestream */
    streamval = (uint32_t)(*stream_ptr) << 24;
    streamval |= (uint32_t)(*++stream_ptr) << 16;
    streamval |= (uint32_t)(*++stream_ptr) << 8;
    streamval |= (uint32_t)(*++stream_ptr);
  } else {
    streamval = streamdata->streamval;
  }


  for (k=N; k>0; k--)
  {
    /* find the integer *data for which streamval lies in [W_lower+1, W_upper] */
    W_upper_LSB = W_upper & 0x0000FFFF;
    W_upper_MSB = W_upper >> 16;

    /* start at the specified table entry */
    cdf_ptr = *cdf + (*init_index++);
    W_tmp = W_upper_MSB * *cdf_ptr;
    W_tmp += (W_upper_LSB * *cdf_ptr) >> 16;
    if (streamval > W_tmp)
    {
      for ( ;; )
      {
        W_lower = W_tmp;
        if (cdf_ptr[0]==65535)
          /* range check */
          return -3;
        W_tmp = W_upper_MSB * *++cdf_ptr;
        W_tmp += (W_upper_LSB * *cdf_ptr) >> 16;
        if (streamval <= W_tmp) break;
      }
      W_upper = W_tmp;
      *data++ = (int)(cdf_ptr - *cdf++ - 1);
    } else {
      for ( ;; )
      {
        W_upper = W_tmp;
        --cdf_ptr;
        if (cdf_ptr<*cdf) {
          /* range check */
          return -3;
        }
        W_tmp = W_upper_MSB * *cdf_ptr;
        W_tmp += (W_upper_LSB * *cdf_ptr) >> 16;
        if (streamval > W_tmp) break;
      }
      W_lower = W_tmp;
      *data++ = (int)(cdf_ptr - *cdf++);
    }

    /* shift interval to start at zero */
    W_upper -= ++W_lower;
    /* add integer to bitstream */
    streamval -= W_lower;

    /* renormalize interval and update streamval */
    while ( !(W_upper & 0xFF000000) )    /* W_upper < 2^24 */
    {
      /* read next byte from stream */
      streamval = (streamval << 8) | *++stream_ptr;
      W_upper <<= 8;
    }
  }

  streamdata->stream_index = (int)(stream_ptr - streamdata->stream);
  streamdata->W_upper = W_upper;
  streamdata->streamval = streamval;


  /* find number of bytes in original stream (determined by current interval width) */
  if ( W_upper > 0x01FFFFFF )
    return streamdata->stream_index - 2;
  else
    return streamdata->stream_index - 1;
}
