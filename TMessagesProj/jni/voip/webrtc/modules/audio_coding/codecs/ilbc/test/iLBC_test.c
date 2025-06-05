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

        iLBC_test.c

******************************************************************/

#include <stdlib.h>
#include <stdio.h>
#include <string.h>
#include "modules/audio_coding/codecs/ilbc/ilbc.h"

/*---------------------------------------------------------------*
 *  Main program to test iLBC encoding and decoding
 *
 *  Usage:
 *	  exefile_name.exe <infile> <bytefile> <outfile> <channel>
 *
 *    <infile>   : Input file, speech for encoder (16-bit pcm file)
 *    <bytefile> : Bit stream output from the encoder
 *    <outfile>  : Output file, decoded speech (16-bit pcm file)
 *    <channel>  : Bit error file, optional (16-bit)
 *                     1 - Packet received correctly
 *                     0 - Packet Lost
 *
 *--------------------------------------------------------------*/

#define BLOCKL_MAX			240
#define ILBCNOOFWORDS_MAX	25


int main(int argc, char* argv[])
{

  FILE *ifileid,*efileid,*ofileid, *cfileid;
  int16_t data[BLOCKL_MAX];
  uint8_t encoded_data[2 * ILBCNOOFWORDS_MAX];
  int16_t decoded_data[BLOCKL_MAX];
  int len_int, mode;
  short pli;
  int blockcount = 0;
  size_t frameLen, len, len_i16s;
  int16_t speechType;
  IlbcEncoderInstance *Enc_Inst;
  IlbcDecoderInstance *Dec_Inst;

#ifdef __ILBC_WITH_40BITACC
  /* Doublecheck that long long exists */
  if (sizeof(long)>=sizeof(long long)) {
    fprintf(stderr, "40-bit simulation is not be supported on this platform\n");
    exit(0);
  }
#endif

  /* get arguments and open files */

  if ((argc!=5) && (argc!=6)) {
    fprintf(stderr,
            "\n*-----------------------------------------------*\n");
    fprintf(stderr,
            "   %s <20,30> input encoded decoded (channel)\n\n",
            argv[0]);
    fprintf(stderr,
            "   mode    : Frame size for the encoding/decoding\n");
    fprintf(stderr,
            "                 20 - 20 ms\n");
    fprintf(stderr,
            "                 30 - 30 ms\n");
    fprintf(stderr,
            "   input   : Speech for encoder (16-bit pcm file)\n");
    fprintf(stderr,
            "   encoded : Encoded bit stream\n");
    fprintf(stderr,
            "   decoded : Decoded speech (16-bit pcm file)\n");
    fprintf(stderr,
            "   channel : Packet loss pattern, optional (16-bit)\n");
    fprintf(stderr,
            "                  1 - Packet received correctly\n");
    fprintf(stderr,
            "                  0 - Packet Lost\n");
    fprintf(stderr,
            "*-----------------------------------------------*\n\n");
    exit(1);
  }
  mode=atoi(argv[1]);
  if (mode != 20 && mode != 30) {
    fprintf(stderr,"Wrong mode %s, must be 20, or 30\n",
            argv[1]);
    exit(2);
  }
  if ( (ifileid=fopen(argv[2],"rb")) == NULL) {
    fprintf(stderr,"Cannot open input file %s\n", argv[2]);
    exit(2);}
  if ( (efileid=fopen(argv[3],"wb")) == NULL) {
    fprintf(stderr, "Cannot open encoded file file %s\n",
            argv[3]); exit(1);}
  if ( (ofileid=fopen(argv[4],"wb")) == NULL) {
    fprintf(stderr, "Cannot open decoded file %s\n",
            argv[4]); exit(1);}
  if (argc==6) {
    if( (cfileid=fopen(argv[5],"rb")) == NULL) {
      fprintf(stderr, "Cannot open channel file %s\n",
              argv[5]);
      exit(1);
    }
  } else {
    cfileid=NULL;
  }

  /* print info */

  fprintf(stderr, "\n");
  fprintf(stderr,
          "*---------------------------------------------------*\n");
  fprintf(stderr,
          "*                                                   *\n");
  fprintf(stderr,
          "*      iLBC test program                            *\n");
  fprintf(stderr,
          "*                                                   *\n");
  fprintf(stderr,
          "*                                                   *\n");
  fprintf(stderr,
          "*---------------------------------------------------*\n");
  fprintf(stderr,"\nMode           : %2d ms\n", mode);
  fprintf(stderr,"Input file     : %s\n", argv[2]);
  fprintf(stderr,"Encoded file   : %s\n", argv[3]);
  fprintf(stderr,"Output file    : %s\n", argv[4]);
  if (argc==6) {
    fprintf(stderr,"Channel file   : %s\n", argv[5]);
  }
  fprintf(stderr,"\n");

  /* Create structs */
  WebRtcIlbcfix_EncoderCreate(&Enc_Inst);
  WebRtcIlbcfix_DecoderCreate(&Dec_Inst);


  /* Initialization */

  WebRtcIlbcfix_EncoderInit(Enc_Inst, mode);
  WebRtcIlbcfix_DecoderInit(Dec_Inst, mode);
  frameLen = (size_t)(mode*8);

  /* loop over input blocks */

  while (fread(data,sizeof(int16_t),frameLen,ifileid) == frameLen) {

    blockcount++;

    /* encoding */

    fprintf(stderr, "--- Encoding block %i --- ",blockcount);
    len_int = WebRtcIlbcfix_Encode(Enc_Inst, data, frameLen, encoded_data);
    if (len_int < 0) {
      fprintf(stderr, "Error encoding\n");
      exit(0);
    }
    len = (size_t)len_int;
    fprintf(stderr, "\r");

    /* write byte file */

    len_i16s = (len + 1) / sizeof(int16_t);
    if (fwrite(encoded_data, sizeof(int16_t), len_i16s, efileid) != len_i16s) {
      return -1;
    }

    /* get channel data if provided */
    if (argc==6) {
      if (fread(&pli, sizeof(int16_t), 1, cfileid)) {
        if ((pli!=0)&&(pli!=1)) {
          fprintf(stderr, "Error in channel file\n");
          exit(0);
        }
        if (pli==0) {
          /* Packet loss -> remove info from frame */
          memset(encoded_data, 0,
                 sizeof(int16_t)*ILBCNOOFWORDS_MAX);
        }
      } else {
        fprintf(stderr, "Error. Channel file too short\n");
        exit(0);
      }
    } else {
      pli=1;
    }

    /* decoding */

    fprintf(stderr, "--- Decoding block %i --- ",blockcount);
    if (pli==1) {
      len_int=WebRtcIlbcfix_Decode(Dec_Inst, encoded_data,
                                   len, decoded_data,&speechType);
      if (len_int < 0) {
        fprintf(stderr, "Error decoding\n");
        exit(0);
      }
      len = (size_t)len_int;
    } else {
      len=WebRtcIlbcfix_DecodePlc(Dec_Inst, decoded_data, 1);
    }
    fprintf(stderr, "\r");

    /* write output file */

    if (fwrite(decoded_data, sizeof(int16_t), len, ofileid) != len) {
      return -1;
    }
  }

  /* close files */

  fclose(ifileid);  fclose(efileid); fclose(ofileid);
  if (argc==6) {
    fclose(cfileid);
  }

  /* Free structs */
  WebRtcIlbcfix_EncoderFree(Enc_Inst);
  WebRtcIlbcfix_DecoderFree(Dec_Inst);


  printf("\nDone with simulation\n\n");

  return(0);
}
