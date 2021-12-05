/*
 * Written by Josh Aas and Tim Terriberry
 * Copyright (c) 2013, Mozilla Corporation
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 3. Neither the name of the Mozilla Corporation nor the names of its
 *    contributors may be used to endorse or promote products derived from this
 *    software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

/* Input: JPEG YUV 4:2:0 */
/* Output: YUV 4:2:0 */

/* gcc -std=c99 jpegyuv.c -I/opt/local/include/ -L/opt/local/lib/ -ljpeg -o jpegyuv */

#include <stdio.h>
#include <stdlib.h>

#include "jpeglib.h"

int main(int argc, char *argv[]) {
  const char *jpg_path;
  const char *yuv_path;
  struct jpeg_decompress_struct cinfo;
  struct jpeg_error_mgr jerr;
  FILE *jpg_fd;
  int luma_width;
  int luma_height;
  int chroma_width;
  int chroma_height;
  int frame_width;
  int yuv_size;
  JSAMPLE *jpg_buffer;
  JSAMPROW yrow_pointer[16];
  JSAMPROW cbrow_pointer[8];
  JSAMPROW crrow_pointer[8];
  JSAMPROW *plane_pointer[3];
  unsigned char *yuv_buffer;
  int x;
  int y;
  FILE *yuv_fd;

  if (argc != 3) {
    fprintf(stderr, "Required arguments:\n");
    fprintf(stderr, "1. Path to JPG input file\n");
    fprintf(stderr, "2. Path to YUV output file\n");
    return 1;
  }

  /* Will check these for validity when opening via 'fopen'. */
  jpg_path = argv[1];
  yuv_path = argv[2];

  cinfo.err = jpeg_std_error(&jerr);
  jpeg_create_decompress(&cinfo);

  jpg_fd = fopen(jpg_path, "rb");
  if (!jpg_fd) {
    fprintf(stderr, "Invalid path to JPEG file!\n");
    return 1;
  }

  jpeg_stdio_src(&cinfo, jpg_fd);

  jpeg_read_header(&cinfo, TRUE);

  cinfo.raw_data_out = TRUE;
  cinfo.do_fancy_upsampling = FALSE;

  jpeg_start_decompress(&cinfo);

  luma_width = cinfo.output_width;
  luma_height = cinfo.output_height;

  chroma_width = (luma_width + 1) >> 1;
  chroma_height = (luma_height + 1) >> 1;

  yuv_size = luma_width*luma_height + 2*chroma_width*chroma_height;
  yuv_buffer = malloc(yuv_size);
  if (!yuv_buffer) {
    fclose(jpg_fd);
    fprintf(stderr, "Memory allocation failure!\n");
    return 1;
  }

  frame_width = (cinfo.output_width + (16 - 1)) & ~(16 - 1);

  jpg_buffer = malloc(frame_width*16 + 2*(frame_width/2)*8);
  if (!jpg_buffer) {
    fclose(jpg_fd);
    free(yuv_buffer);
    fprintf(stderr, "Memory allocation failure!\n");
    return 1;
  }

  plane_pointer[0] = yrow_pointer;
  plane_pointer[1] = cbrow_pointer;
  plane_pointer[2] = crrow_pointer;

  for (y = 0; y < 16; y++) {
    yrow_pointer[y] = &jpg_buffer[frame_width*y];
  }
  for (y = 0; y < 8; y++) {
    cbrow_pointer[y] = &jpg_buffer[frame_width*16 + (frame_width/2)*y];
    crrow_pointer[y] = &jpg_buffer[frame_width*16 + (frame_width/2)*(8 + y)];
  }

  while (cinfo.output_scanline < cinfo.output_height) {
    int luma_scanline;
    int chroma_scanline;

    luma_scanline = cinfo.output_scanline;
    chroma_scanline = (luma_scanline + 1) >> 1;

    jpeg_read_raw_data(&cinfo, plane_pointer, 16);

    for (y = 0; y < 16 && luma_scanline + y < luma_height; y++) {
      for (x = 0; x < luma_width; x++) {
        yuv_buffer[luma_width*(luma_scanline + y) + x] = yrow_pointer[y][x];
      }
    }
    for (y = 0; y < 8 && chroma_scanline + y < chroma_height; y++) {
      for (x = 0; x < chroma_width; x++) {
        yuv_buffer[luma_width*luma_height +
         chroma_width*(chroma_scanline + y) + x] = cbrow_pointer[y][x];
        yuv_buffer[luma_width*luma_height + chroma_width*chroma_height +
         chroma_width*(chroma_scanline + y) + x] = crrow_pointer[y][x];
      }
    }
  }

  jpeg_finish_decompress(&cinfo);
  jpeg_destroy_decompress(&cinfo);

  fclose(jpg_fd);
  free(jpg_buffer);

  yuv_fd = fopen(yuv_path, "wb");
  if (!yuv_fd) {
    fprintf(stderr, "Invalid path to YUV file!");
    free(yuv_buffer);
    return 1;
  }
  if (fwrite(yuv_buffer, yuv_size, 1, yuv_fd) != 1) {
    fprintf(stderr, "Error writing yuv file\n");
  }

  fclose(yuv_fd);
  free(yuv_buffer);

  return 0;
}
