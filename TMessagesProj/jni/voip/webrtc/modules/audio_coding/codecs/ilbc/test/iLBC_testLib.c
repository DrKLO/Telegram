/*
 *  Copyright (c) 2011 The WebRTC project authors. All Rights Reserved.
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

#include <math.h>
#include <stdlib.h>
#include <stdio.h>
#include <string.h>
#include <time.h>
#include "modules/audio_coding/codecs/ilbc/ilbc.h"

//#define JUNK_DATA
#ifdef JUNK_DATA
#define SEED_FILE "randseed.txt"
#endif


/*----------------------------------------------------------------*
*  Main program to test iLBC encoding and decoding
*
*  Usage:
*		exefile_name.exe <infile> <bytefile> <outfile>
*
*---------------------------------------------------------------*/

int main(int argc, char* argv[])
{
  FILE *ifileid,*efileid,*ofileid, *chfileid;
  short encoded_data[55], data[240], speechType;
  int len_int, mode;
  short pli;
  size_t len, readlen;
  int blockcount = 0;

  IlbcEncoderInstance *Enc_Inst;
  IlbcDecoderInstance *Dec_Inst;
#ifdef JUNK_DATA
  size_t i;
  FILE *seedfile;
  unsigned int random_seed = (unsigned int) time(NULL);//1196764538
#endif

  /* Create structs */
  WebRtcIlbcfix_EncoderCreate(&Enc_Inst);
  WebRtcIlbcfix_DecoderCreate(&Dec_Inst);

  /* get arguments and open files */

  if (argc != 6 ) {
    fprintf(stderr, "%s mode inputfile bytefile outputfile channelfile\n",
            argv[0]);
    fprintf(stderr, "Example:\n");
    fprintf(stderr, "%s <30,20> in.pcm byte.dat out.pcm T30.0.dat\n", argv[0]);
    exit(1);
  }
  mode=atoi(argv[1]);
  if (mode != 20 && mode != 30) {
    fprintf(stderr,"Wrong mode %s, must be 20, or 30\n", argv[1]);
    exit(2);
  }
  if ( (ifileid=fopen(argv[2],"rb")) == NULL) {
    fprintf(stderr,"Cannot open input file %s\n", argv[2]);
    exit(2);}
  if ( (efileid=fopen(argv[3],"wb")) == NULL) {
    fprintf(stderr, "Cannot open channelfile file %s\n",
            argv[3]); exit(3);}
  if( (ofileid=fopen(argv[4],"wb")) == NULL) {
    fprintf(stderr, "Cannot open output file %s\n",
            argv[4]); exit(3);}
  if ( (chfileid=fopen(argv[5],"rb")) == NULL) {
    fprintf(stderr,"Cannot open channel file file %s\n", argv[5]);
    exit(2);
  }
  /* print info */
  fprintf(stderr, "\n");
  fprintf(stderr,
          "*---------------------------------------------------*\n");
  fprintf(stderr,
          "*                                                   *\n");
  fprintf(stderr,
          "*      iLBCtest                                     *\n");
  fprintf(stderr,
          "*                                                   *\n");
  fprintf(stderr,
          "*                                                   *\n");
  fprintf(stderr,
		"*---------------------------------------------------*\n");
#ifdef SPLIT_10MS
  fprintf(stderr,"\n10ms split with raw mode: %2d ms\n", mode);
#else
  fprintf(stderr,"\nMode          : %2d ms\n", mode);
#endif
  fprintf(stderr,"\nInput file    : %s\n", argv[2]);
  fprintf(stderr,"Coded file    : %s\n", argv[3]);
  fprintf(stderr,"Output file   : %s\n\n", argv[4]);
  fprintf(stderr,"Channel file  : %s\n\n", argv[5]);

#ifdef JUNK_DATA
  srand(random_seed);

  if ( (seedfile = fopen(SEED_FILE, "a+t") ) == NULL ) {
    fprintf(stderr, "Error: Could not open file %s\n", SEED_FILE);
  }
  else {
    fprintf(seedfile, "%u\n", random_seed);
    fclose(seedfile);
  }
#endif

  /* Initialization */
  WebRtcIlbcfix_EncoderInit(Enc_Inst, mode);
  WebRtcIlbcfix_DecoderInit(Dec_Inst, mode);

  /* loop over input blocks */
#ifdef SPLIT_10MS
  readlen = 80;
#else
  readlen = (size_t)(mode << 3);
#endif
  while(fread(data, sizeof(short), readlen, ifileid) == readlen) {
    blockcount++;

    /* encoding */
    fprintf(stderr, "--- Encoding block %i --- ",blockcount);
    len_int=WebRtcIlbcfix_Encode(Enc_Inst, data, readlen, encoded_data);
    if (len_int < 0) {
      fprintf(stderr, "Error encoding\n");
      exit(0);
    }
    len = (size_t)len_int;
    fprintf(stderr, "\r");

#ifdef JUNK_DATA
    for ( i = 0; i < len; i++) {
      encoded_data[i] = (short) (encoded_data[i] + (short) rand());
    }
#endif
    /* write byte file */
    if(len != 0){ //len may be 0 in 10ms split case
      fwrite(encoded_data,1,len,efileid);

      /* get channel data if provided */
      if (argc==6) {
        if (fread(&pli, sizeof(int16_t), 1, chfileid)) {
          if ((pli!=0)&&(pli!=1)) {
            fprintf(stderr, "Error in channel file\n");
            exit(0);
          }
          if (pli==0) {
            /* Packet loss -> remove info from frame */
            memset(encoded_data, 0, sizeof(int16_t)*25);
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
        len_int = WebRtcIlbcfix_Decode(Dec_Inst, encoded_data, len, data,
                                       &speechType);
        if (len_int < 0) {
          fprintf(stderr, "Error decoding\n");
          exit(0);
        }
        len = (size_t)len_int;
      } else {
        len=WebRtcIlbcfix_DecodePlc(Dec_Inst, data, 1);
      }
      fprintf(stderr, "\r");

      /* write output file */
      fwrite(data,sizeof(short),len,ofileid);
    }
  }

#ifdef JUNK_DATA
  if ( (seedfile = fopen(SEED_FILE, "a+t") ) == NULL ) {
    fprintf(stderr, "Error: Could not open file %s\n", SEED_FILE);
  }
  else {
    fprintf(seedfile, "ok\n\n");
    fclose(seedfile);
  }
#endif

  /* free structs */
  WebRtcIlbcfix_EncoderFree(Enc_Inst);
  WebRtcIlbcfix_DecoderFree(Dec_Inst);

  /* close files */
  fclose(ifileid);
  fclose(efileid);
  fclose(ofileid);

  return 0;
}
