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

#include "modules/audio_coding/codecs/ilbc/defines.h"
#include "modules/audio_coding/codecs/ilbc/nit_encode.h"
#include "modules/audio_coding/codecs/ilbc/encode.h"
#include "modules/audio_coding/codecs/ilbc/init_decode.h"
#include "modules/audio_coding/codecs/ilbc/decode.h"
#include "modules/audio_coding/codecs/ilbc/constants.h"
#include "modules/audio_coding/codecs/ilbc/ilbc.h"

#define ILBCNOOFWORDS_MAX (NO_OF_BYTES_30MS)/2

/* Runtime statistics */
#include <time.h>
/* #define CLOCKS_PER_SEC  1000 */

/*----------------------------------------------------------------*
 *  Encoder interface function
 *---------------------------------------------------------------*/

short encode(                         /* (o) Number of bytes encoded */
    IlbcEncoder *iLBCenc_inst,    /* (i/o) Encoder instance */
    int16_t *encoded_data,      /* (o) The encoded bytes */
    int16_t *data               /* (i) The signal block to encode */
                                                        ){

  /* do the actual encoding */
  WebRtcIlbcfix_Encode((uint16_t *)encoded_data, data, iLBCenc_inst);

  return (iLBCenc_inst->no_of_bytes);
}

/*----------------------------------------------------------------*
 *  Decoder interface function
 *---------------------------------------------------------------*/

short decode( /* (o) Number of decoded samples */
    IlbcDecoder *iLBCdec_inst, /* (i/o) Decoder instance */
    short *decoded_data, /* (o) Decoded signal block */
    short *encoded_data, /* (i) Encoded bytes */
    short mode           /* (i) 0=PL, 1=Normal */
              ){

  /* check if mode is valid */

  if (mode<0 || mode>1) {
    printf("\nERROR - Wrong mode - 0, 1 allowed\n"); exit(3);}

  /* do actual decoding of block */

  WebRtcIlbcfix_Decode(decoded_data, (uint16_t *)encoded_data,
                       iLBCdec_inst, mode);

  return (iLBCdec_inst->blockl);
}

/*----------------------------------------------------------------*
 *  Main program to test iLBC encoding and decoding
 *
 *  Usage:
 *		exefile_name.exe <infile> <bytefile> <outfile> <channelfile>
 *
 *---------------------------------------------------------------*/

#define MAXFRAMES   10000
#define MAXFILELEN (BLOCKL_MAX*MAXFRAMES)

int main(int argc, char* argv[])
{

  /* Runtime statistics */

  float starttime1, starttime2;
  float runtime1, runtime2;
  float outtime;

  FILE *ifileid,*efileid,*ofileid, *chfileid;
  short *inputdata, *encodeddata, *decodeddata;
  short *channeldata;
  int blockcount = 0, noOfBlocks=0, i, noOfLostBlocks=0;
  short mode;
  IlbcEncoder Enc_Inst;
  IlbcDecoder Dec_Inst;

  short frameLen;
  short count;
#ifdef SPLIT_10MS
  short size;
#endif

  inputdata=(short*) malloc(MAXFILELEN*sizeof(short));
  if (inputdata==NULL) {
    fprintf(stderr,"Could not allocate memory for vector\n");
    exit(0);
  }
  encodeddata=(short*) malloc(ILBCNOOFWORDS_MAX*MAXFRAMES*sizeof(short));
  if (encodeddata==NULL) {
    fprintf(stderr,"Could not allocate memory for vector\n");
    free(inputdata);
    exit(0);
  }
  decodeddata=(short*) malloc(MAXFILELEN*sizeof(short));
  if (decodeddata==NULL) {
    fprintf(stderr,"Could not allocate memory for vector\n");
    free(inputdata);
    free(encodeddata);
    exit(0);
  }
  channeldata=(short*) malloc(MAXFRAMES*sizeof(short));
  if (channeldata==NULL) {
    fprintf(stderr,"Could not allocate memory for vector\n");
    free(inputdata);
    free(encodeddata);
    free(decodeddata);
    exit(0);
  }

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
    exit(2);}


  /* print info */
#ifndef PRINT_MIPS
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
#endif

  /* Initialization */

  WebRtcIlbcfix_EncoderInit(&Enc_Inst, mode);
  WebRtcIlbcfix_DecoderInit(&Dec_Inst, mode, 1);

  /* extract the input file and channel file */

#ifdef SPLIT_10MS
  frameLen = (mode==20)? 80:160;
  fread(Enc_Inst.past_samples, sizeof(short), frameLen, ifileid);
  Enc_Inst.section = 0;

  while( fread(&inputdata[noOfBlocks*80], sizeof(short),
               80, ifileid) == 80 ) {
    noOfBlocks++;
  }

  noOfBlocks += frameLen/80;
  frameLen = 80;
#else
  frameLen = Enc_Inst.blockl;

  while( fread(&inputdata[noOfBlocks*Enc_Inst.blockl],sizeof(short),
               Enc_Inst.blockl,ifileid)==(uint16_t)Enc_Inst.blockl){
    noOfBlocks++;
  }
#endif


  while ((fread(&channeldata[blockcount],sizeof(short), 1,chfileid)==1)
            && ( blockcount < noOfBlocks/(Enc_Inst.blockl/frameLen) )) {
    blockcount++;
  }

  if ( blockcount < noOfBlocks/(Enc_Inst.blockl/frameLen) ) {
    fprintf(stderr,"Channel file %s is too short\n", argv[4]);
    free(inputdata);
    free(encodeddata);
    free(decodeddata);
    free(channeldata);
    exit(0);
  }

  count=0;

  /* Runtime statistics */

  starttime1 = clock()/(float)CLOCKS_PER_SEC;

  /* Encoding loop */
#ifdef PRINT_MIPS
  printf("-1 -1\n");
#endif

#ifdef SPLIT_10MS
  /* "Enc_Inst.section != 0" is to make sure we run through full
     lengths of all vectors for 10ms split mode.
  */
  //   while( (count < noOfBlocks) || (Enc_Inst.section != 0) )    {
  while( count < blockcount * (Enc_Inst.blockl/frameLen) )    {

    encode(&Enc_Inst, &encodeddata[Enc_Inst.no_of_words *
                                   (count/(Enc_Inst.nsub/2))],
           &inputdata[frameLen * count] );
#else
    while (count < noOfBlocks) {
      encode( &Enc_Inst, &encodeddata[Enc_Inst.no_of_words * count],
              &inputdata[frameLen * count] );
#endif

#ifdef PRINT_MIPS
      printf("-1 -1\n");
#endif

      count++;
    }

    count=0;

    /* Runtime statistics */

    starttime2=clock()/(float)CLOCKS_PER_SEC;
    runtime1 = (float)(starttime2-starttime1);

    /* Decoding loop */

    while (count < blockcount) {
      if (channeldata[count]==1) {
        /* Normal decoding */
        decode(&Dec_Inst, &decodeddata[count * Dec_Inst.blockl],
               &encodeddata[Dec_Inst.no_of_words * count], 1);
      } else if (channeldata[count]==0) {
        /* PLC */
        short emptydata[ILBCNOOFWORDS_MAX];
        memset(emptydata, 0, Dec_Inst.no_of_words*sizeof(short));
        decode(&Dec_Inst, &decodeddata[count*Dec_Inst.blockl],
               emptydata, 0);
        noOfLostBlocks++;
      } else {
        printf("Error in channel file (values have to be either 1 or 0)\n");
        exit(0);
      }
#ifdef PRINT_MIPS
      printf("-1 -1\n");
#endif

      count++;
    }

    /* Runtime statistics */

    runtime2 = (float)(clock()/(float)CLOCKS_PER_SEC-starttime2);

    outtime = (float)((float)blockcount*
                      (float)mode/1000.0);

#ifndef PRINT_MIPS
    printf("\nLength of speech file: %.1f s\n", outtime);
    printf("Lost frames          : %.1f%%\n\n", 100*(float)noOfLostBlocks/(float)blockcount);

    printf("Time to run iLBC_encode+iLBC_decode:");
    printf(" %.1f s (%.1f%% of realtime)\n", runtime1+runtime2,
           (100*(runtime1+runtime2)/outtime));

    printf("Time in iLBC_encode                :");
    printf(" %.1f s (%.1f%% of total runtime)\n",
           runtime1, 100.0*runtime1/(runtime1+runtime2));

    printf("Time in iLBC_decode                :");
    printf(" %.1f s (%.1f%% of total runtime)\n\n",
           runtime2, 100.0*runtime2/(runtime1+runtime2));
#endif

    /* Write data to files */
    for (i=0; i<blockcount; i++) {
      fwrite(&encodeddata[i*Enc_Inst.no_of_words], sizeof(short),
             Enc_Inst.no_of_words, efileid);
    }
    for (i=0;i<blockcount;i++) {
      fwrite(&decodeddata[i*Enc_Inst.blockl],sizeof(short),Enc_Inst.blockl,ofileid);
    }

    /* return memory and close files */

    free(inputdata);
    free(encodeddata);
    free(decodeddata);
    free(channeldata);
    fclose(ifileid);  fclose(efileid); fclose(ofileid);
    return(0);
  }
