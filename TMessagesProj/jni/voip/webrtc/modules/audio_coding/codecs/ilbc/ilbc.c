/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

/******************************************************************

 iLBC Speech Coder ANSI-C Source Code

 iLBCInterface.c

******************************************************************/

#include "modules/audio_coding/codecs/ilbc/ilbc.h"

#include <stdlib.h>

#include "modules/audio_coding/codecs/ilbc/decode.h"
#include "modules/audio_coding/codecs/ilbc/defines.h"
#include "modules/audio_coding/codecs/ilbc/encode.h"
#include "modules/audio_coding/codecs/ilbc/init_decode.h"
#include "modules/audio_coding/codecs/ilbc/init_encode.h"
#include "rtc_base/checks.h"

int16_t WebRtcIlbcfix_EncoderAssign(IlbcEncoderInstance** iLBC_encinst,
                                    int16_t* ILBCENC_inst_Addr,
                                    int16_t* size) {
  *iLBC_encinst=(IlbcEncoderInstance*)ILBCENC_inst_Addr;
  *size=sizeof(IlbcEncoder)/sizeof(int16_t);
  if (*iLBC_encinst!=NULL) {
    return(0);
  } else {
    return(-1);
  }
}

int16_t WebRtcIlbcfix_DecoderAssign(IlbcDecoderInstance** iLBC_decinst,
                                    int16_t* ILBCDEC_inst_Addr,
                                    int16_t* size) {
  *iLBC_decinst=(IlbcDecoderInstance*)ILBCDEC_inst_Addr;
  *size=sizeof(IlbcDecoder)/sizeof(int16_t);
  if (*iLBC_decinst!=NULL) {
    return(0);
  } else {
    return(-1);
  }
}

int16_t WebRtcIlbcfix_EncoderCreate(IlbcEncoderInstance **iLBC_encinst) {
  *iLBC_encinst=(IlbcEncoderInstance*)malloc(sizeof(IlbcEncoder));
  if (*iLBC_encinst!=NULL) {
    return(0);
  } else {
    return(-1);
  }
}

int16_t WebRtcIlbcfix_DecoderCreate(IlbcDecoderInstance **iLBC_decinst) {
  *iLBC_decinst=(IlbcDecoderInstance*)malloc(sizeof(IlbcDecoder));
  if (*iLBC_decinst!=NULL) {
    return(0);
  } else {
    return(-1);
  }
}

int16_t WebRtcIlbcfix_EncoderFree(IlbcEncoderInstance *iLBC_encinst) {
  free(iLBC_encinst);
  return(0);
}

int16_t WebRtcIlbcfix_DecoderFree(IlbcDecoderInstance *iLBC_decinst) {
  free(iLBC_decinst);
  return(0);
}

int16_t WebRtcIlbcfix_EncoderInit(IlbcEncoderInstance* iLBCenc_inst,
                                  int16_t mode) {
  if ((mode==20)||(mode==30)) {
    WebRtcIlbcfix_InitEncode((IlbcEncoder*) iLBCenc_inst, mode);
    return(0);
  } else {
    return(-1);
  }
}

int WebRtcIlbcfix_Encode(IlbcEncoderInstance* iLBCenc_inst,
                         const int16_t* speechIn,
                         size_t len,
                         uint8_t* encoded) {
  size_t pos = 0;
  size_t encpos = 0;

  if ((len != ((IlbcEncoder*)iLBCenc_inst)->blockl) &&
#ifdef SPLIT_10MS
      (len != 80) &&
#endif
      (len != 2*((IlbcEncoder*)iLBCenc_inst)->blockl) &&
      (len != 3*((IlbcEncoder*)iLBCenc_inst)->blockl))
  {
    /* A maximum of 3 frames/packet is allowed */
    return(-1);
  } else {

    /* call encoder */
    while (pos<len) {
      WebRtcIlbcfix_EncodeImpl((uint16_t*)&encoded[2 * encpos], &speechIn[pos],
                               (IlbcEncoder*)iLBCenc_inst);
#ifdef SPLIT_10MS
      pos += 80;
      if(((IlbcEncoder*)iLBCenc_inst)->section == 0)
#else
        pos += ((IlbcEncoder*)iLBCenc_inst)->blockl;
#endif
      encpos += ((IlbcEncoder*)iLBCenc_inst)->no_of_words;
    }
    return (int)(encpos*2);
  }
}

int16_t WebRtcIlbcfix_DecoderInit(IlbcDecoderInstance* iLBCdec_inst,
                                  int16_t mode) {
  if ((mode==20)||(mode==30)) {
    WebRtcIlbcfix_InitDecode((IlbcDecoder*) iLBCdec_inst, mode, 1);
    return(0);
  } else {
    return(-1);
  }
}
void WebRtcIlbcfix_DecoderInit20Ms(IlbcDecoderInstance* iLBCdec_inst) {
  WebRtcIlbcfix_InitDecode((IlbcDecoder*) iLBCdec_inst, 20, 1);
}
void WebRtcIlbcfix_Decoderinit30Ms(IlbcDecoderInstance* iLBCdec_inst) {
  WebRtcIlbcfix_InitDecode((IlbcDecoder*) iLBCdec_inst, 30, 1);
}


int WebRtcIlbcfix_Decode(IlbcDecoderInstance* iLBCdec_inst,
                         const uint8_t* encoded,
                         size_t len,
                         int16_t* decoded,
                         int16_t* speechType)
{
  size_t i=0;
  /* Allow for automatic switching between the frame sizes
     (although you do get some discontinuity) */
  if ((len==((IlbcDecoder*)iLBCdec_inst)->no_of_bytes)||
      (len==2*((IlbcDecoder*)iLBCdec_inst)->no_of_bytes)||
      (len==3*((IlbcDecoder*)iLBCdec_inst)->no_of_bytes)) {
    /* ok, do nothing */
  } else {
    /* Test if the mode has changed */
    if (((IlbcDecoder*)iLBCdec_inst)->mode==20) {
      if ((len==NO_OF_BYTES_30MS)||
          (len==2*NO_OF_BYTES_30MS)||
          (len==3*NO_OF_BYTES_30MS)) {
        WebRtcIlbcfix_InitDecode(
            ((IlbcDecoder*)iLBCdec_inst), 30,
            ((IlbcDecoder*)iLBCdec_inst)->use_enhancer);
      } else {
        /* Unsupported frame length */
        return(-1);
      }
    } else {
      if ((len==NO_OF_BYTES_20MS)||
          (len==2*NO_OF_BYTES_20MS)||
          (len==3*NO_OF_BYTES_20MS)) {
        WebRtcIlbcfix_InitDecode(
            ((IlbcDecoder*)iLBCdec_inst), 20,
            ((IlbcDecoder*)iLBCdec_inst)->use_enhancer);
      } else {
        /* Unsupported frame length */
        return(-1);
      }
    }
  }

  while ((i*((IlbcDecoder*)iLBCdec_inst)->no_of_bytes)<len) {
    if (WebRtcIlbcfix_DecodeImpl(
            &decoded[i * ((IlbcDecoder*)iLBCdec_inst)->blockl],
            (const uint16_t*)&encoded
                [2 * i * ((IlbcDecoder*)iLBCdec_inst)->no_of_words],
            (IlbcDecoder*)iLBCdec_inst, 1) == -1)
      return -1;
    i++;
  }
  /* iLBC does not support VAD/CNG yet */
  *speechType=1;
  return (int)(i*((IlbcDecoder*)iLBCdec_inst)->blockl);
}

int WebRtcIlbcfix_Decode20Ms(IlbcDecoderInstance* iLBCdec_inst,
                             const uint8_t* encoded,
                             size_t len,
                             int16_t* decoded,
                             int16_t* speechType)
{
  size_t i=0;
  if ((len==((IlbcDecoder*)iLBCdec_inst)->no_of_bytes)||
      (len==2*((IlbcDecoder*)iLBCdec_inst)->no_of_bytes)||
      (len==3*((IlbcDecoder*)iLBCdec_inst)->no_of_bytes)) {
    /* ok, do nothing */
  } else {
    return(-1);
  }

  while ((i*((IlbcDecoder*)iLBCdec_inst)->no_of_bytes)<len) {
    if (!WebRtcIlbcfix_DecodeImpl(
        &decoded[i * ((IlbcDecoder*)iLBCdec_inst)->blockl],
        (const uint16_t*)&encoded
            [2 * i * ((IlbcDecoder*)iLBCdec_inst)->no_of_words],
        (IlbcDecoder*)iLBCdec_inst, 1))
      return -1;
    i++;
  }
  /* iLBC does not support VAD/CNG yet */
  *speechType=1;
  return (int)(i*((IlbcDecoder*)iLBCdec_inst)->blockl);
}

int WebRtcIlbcfix_Decode30Ms(IlbcDecoderInstance* iLBCdec_inst,
                             const uint8_t* encoded,
                             size_t len,
                             int16_t* decoded,
                             int16_t* speechType)
{
  size_t i=0;
  if ((len==((IlbcDecoder*)iLBCdec_inst)->no_of_bytes)||
      (len==2*((IlbcDecoder*)iLBCdec_inst)->no_of_bytes)||
      (len==3*((IlbcDecoder*)iLBCdec_inst)->no_of_bytes)) {
    /* ok, do nothing */
  } else {
    return(-1);
  }

  while ((i*((IlbcDecoder*)iLBCdec_inst)->no_of_bytes)<len) {
    if (!WebRtcIlbcfix_DecodeImpl(
        &decoded[i * ((IlbcDecoder*)iLBCdec_inst)->blockl],
        (const uint16_t*)&encoded
            [2 * i * ((IlbcDecoder*)iLBCdec_inst)->no_of_words],
        (IlbcDecoder*)iLBCdec_inst, 1))
      return -1;
    i++;
  }
  /* iLBC does not support VAD/CNG yet */
  *speechType=1;
  return (int)(i*((IlbcDecoder*)iLBCdec_inst)->blockl);
}

size_t WebRtcIlbcfix_DecodePlc(IlbcDecoderInstance* iLBCdec_inst,
                               int16_t* decoded,
                               size_t noOfLostFrames) {
  size_t i;
  uint16_t dummy;

  for (i=0;i<noOfLostFrames;i++) {
    // PLC decoding shouldn't fail, because there is no external input data
    // that can be bad.
    int result = WebRtcIlbcfix_DecodeImpl(
        &decoded[i * ((IlbcDecoder*)iLBCdec_inst)->blockl], &dummy,
        (IlbcDecoder*)iLBCdec_inst, 0);
    RTC_CHECK_EQ(result, 0);
  }
  return (noOfLostFrames*((IlbcDecoder*)iLBCdec_inst)->blockl);
}

size_t WebRtcIlbcfix_NetEqPlc(IlbcDecoderInstance* iLBCdec_inst,
                              int16_t* decoded,
                              size_t noOfLostFrames) {
  /* Two input parameters not used, but needed for function pointers in NetEQ */
  (void)(decoded = NULL);
  (void)(noOfLostFrames = 0);

  WebRtcSpl_MemSetW16(((IlbcDecoder*)iLBCdec_inst)->enh_buf, 0, ENH_BUFL);
  ((IlbcDecoder*)iLBCdec_inst)->prev_enh_pl = 2;

  return (0);
}

void WebRtcIlbcfix_version(char *version)
{
  strcpy((char*)version, "1.1.1");
}
