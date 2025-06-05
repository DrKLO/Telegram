/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

/*
 * testG722.cpp : Defines the entry point for the console application.
 */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

/* include API */
#include "modules/audio_coding/codecs/g722/g722_interface.h"

/* Runtime statistics */
#include <time.h>
#define CLOCKS_PER_SEC_G722 100000

// Forward declaration
typedef struct WebRtcG722EncInst G722EncInst;
typedef struct WebRtcG722DecInst G722DecInst;

/* function for reading audio data from PCM file */
bool readframe(int16_t* data, FILE* inp, size_t length) {
  size_t rlen = fread(data, sizeof(int16_t), length, inp);
  if (rlen >= length)
    return false;
  memset(data + rlen, 0, (length - rlen) * sizeof(int16_t));
  return true;
}

int main(int argc, char* argv[]) {
  char inname[60], outbit[40], outname[40];
  FILE *inp, *outbitp, *outp;

  int framecnt;
  bool endfile;
  size_t framelength = 160;
  G722EncInst* G722enc_inst;
  G722DecInst* G722dec_inst;

  /* Runtime statistics */
  double starttime;
  double runtime = 0;
  double length_file;

  size_t stream_len = 0;
  int16_t shortdata[960];
  int16_t decoded[960];
  uint8_t streamdata[80 * 6];
  int16_t speechType[1];

  /* handling wrong input arguments in the command line */
  if (argc != 5) {
    printf("\n\nWrong number of arguments or flag values.\n\n");

    printf("\n");
    printf("Usage:\n\n");
    printf("./testG722.exe framelength infile outbitfile outspeechfile \n\n");
    printf("with:\n");
    printf("framelength  :    Framelength in samples.\n\n");
    printf("infile       :    Normal speech input file\n\n");
    printf("outbitfile   :    Bitstream output file\n\n");
    printf("outspeechfile:    Speech output file\n\n");
    exit(0);
  }

  /* Get frame length */
  int framelength_int = atoi(argv[1]);
  if (framelength_int < 0) {
    printf("  G.722: Invalid framelength %d.\n", framelength_int);
    exit(1);
  }
  framelength = static_cast<size_t>(framelength_int);

  /* Get Input and Output files */
  sscanf(argv[2], "%s", inname);
  sscanf(argv[3], "%s", outbit);
  sscanf(argv[4], "%s", outname);

  if ((inp = fopen(inname, "rb")) == NULL) {
    printf("  G.722: Cannot read file %s.\n", inname);
    exit(1);
  }
  if ((outbitp = fopen(outbit, "wb")) == NULL) {
    printf("  G.722: Cannot write file %s.\n", outbit);
    exit(1);
  }
  if ((outp = fopen(outname, "wb")) == NULL) {
    printf("  G.722: Cannot write file %s.\n", outname);
    exit(1);
  }
  printf("\nInput:%s\nOutput bitstream:%s\nOutput:%s\n", inname, outbit,
         outname);

  /* Create and init */
  WebRtcG722_CreateEncoder((G722EncInst**)&G722enc_inst);
  WebRtcG722_CreateDecoder((G722DecInst**)&G722dec_inst);
  WebRtcG722_EncoderInit((G722EncInst*)G722enc_inst);
  WebRtcG722_DecoderInit((G722DecInst*)G722dec_inst);

  /* Initialize encoder and decoder */
  framecnt = 0;
  endfile = false;
  while (!endfile) {
    framecnt++;

    /* Read speech block */
    endfile = readframe(shortdata, inp, framelength);

    /* Start clock before call to encoder and decoder */
    starttime = clock() / (double)CLOCKS_PER_SEC_G722;

    /* G.722 encoding + decoding */
    stream_len = WebRtcG722_Encode((G722EncInst*)G722enc_inst, shortdata,
                                   framelength, streamdata);
    WebRtcG722_Decode(G722dec_inst, streamdata, stream_len, decoded,
                      speechType);

    /* Stop clock after call to encoder and decoder */
    runtime += (double)((clock() / (double)CLOCKS_PER_SEC_G722) - starttime);

    /* Write coded bits to file */
    if (fwrite(streamdata, sizeof(short), stream_len / 2, outbitp) !=
        stream_len / 2) {
      return -1;
    }
    /* Write coded speech to file */
    if (fwrite(decoded, sizeof(short), framelength, outp) != framelength) {
      return -1;
    }
  }

  WebRtcG722_FreeEncoder((G722EncInst*)G722enc_inst);
  WebRtcG722_FreeDecoder((G722DecInst*)G722dec_inst);

  length_file = ((double)framecnt * (double)framelength / 16000);
  printf("\n\nLength of speech file: %.1f s\n", length_file);
  printf("Time to run G.722:      %.2f s (%.2f %% of realtime)\n\n", runtime,
         (100 * runtime / length_file));
  printf("---------------------END----------------------\n");

  fclose(inp);
  fclose(outbitp);
  fclose(outp);

  return 0;
}
