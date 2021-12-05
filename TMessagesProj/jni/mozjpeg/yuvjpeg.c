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

/* Expects 4:2:0 YCbCr */

/* gcc -std=c99 yuvjpeg.c -I/opt/local/include/ -L/opt/local/lib/ -ljpeg -o yuvjpeg */

#include <errno.h>
#include <stdio.h>
#include <string.h>
#include <stdlib.h>

#include "jpeglib.h"

void extend_edge(JSAMPLE *image, int width, int height, unsigned char *yuv,
 int luma_width, int luma_height, int chroma_width, int chroma_height) {
  int x;
  int y;

  for (y = 0; y < luma_height; y++) {
    for (x = 0; x < luma_width; x++) {
      image[width*y + x] = yuv[luma_width*y + x];
    }
  }
  for (y = 0; y < chroma_height; y++) {
    for (x = 0; x < chroma_width; x++) {
      image[width*height + (width/2)*y + x] =
       yuv[luma_width*luma_height + chroma_width*y + x];
      image[width*height + (width/2)*((height/2) + y) + x] =
       yuv[luma_width*luma_height + chroma_width*(chroma_height + y) + x];
    }
  }

  /* Perform right edge extension. */
  for (y = 0; y < luma_height; y++) {
    for (x = luma_width; x < width; x++) {
      image[width*y + x] = image[width*y + (x - 1)];
    }
  }
  for (y = 0; y < chroma_height; y++) {
    for (x = chroma_width; x < width/2; x++) {
      image[width*height + (width/2)*y + x] =
       image[width*height + (width/2)*y + (x - 1)];
      image[width*height + (width/2)*((height/2) + y) + x] =
       image[width*height + (width/2)*((height/2) + y) + (x - 1)];
    }
  }

  /* Perform bottom edge extension. */
  for (x = 0; x < width; x++) {
    for (y = luma_height; y < height; y++) {
      image[width*y + x] = image[width*(y - 1) + x];
    }
  }
  for (x = 0; x < width/2; x++) {
    for (y = chroma_height; y < height/2; y++) {
      image[width*height + (width/2)*y + x] =
       image[width*height + (width/2)*(y - 1) + x];
      image[width*height + (width/2)*((height/2) + y) + x] =
       image[width*height + (width/2)*((height/2) + (y - 1)) + x];
    }
  }
}

int main(int argc, char *argv[]) {
  long quality;
  int matches;
  int luma_width;
  int luma_height;
  int chroma_width;
  int chroma_height;
  int frame_width;
  int frame_height;
  const char *yuv_path;
  const char *jpg_path;
  FILE *yuv_fd;
  size_t yuv_size;
  unsigned char *yuv_buffer;
  JSAMPLE *jpg_buffer;
  struct jpeg_compress_struct cinfo;
  struct jpeg_error_mgr jerr;
  FILE *jpg_fd;
  JSAMPROW yrow_pointer[16];
  JSAMPROW cbrow_pointer[8];
  JSAMPROW crrow_pointer[8];
  JSAMPROW *plane_pointer[3];
  int y;

  if (argc != 5) {
    fprintf(stderr, "Required arguments:\n");
    fprintf(stderr, "1. JPEG quality value, 0-100\n");
    fprintf(stderr, "2. Image size (e.g. '512x512')\n");
    fprintf(stderr, "3. Path to YUV input file\n");
    fprintf(stderr, "4. Path to JPG output file\n");
    return 1;
  }

  errno = 0;

  quality = strtol(argv[1], NULL, 10);
  if (errno != 0 || quality < 0 || quality > 100) {
    fprintf(stderr, "Invalid JPEG quality value!\n");
    return 1;
  }

  matches = sscanf(argv[2], "%dx%d", &luma_width, &luma_height);
  if (matches != 2) {
    fprintf(stderr, "Invalid image size input!\n");
    return 1;
  }
  if (luma_width <= 0 || luma_height <= 0) {
    fprintf(stderr, "Invalid image size input!\n");
    return 1;
  }

  chroma_width = (luma_width + 1) >> 1;
  chroma_height = (luma_height + 1) >> 1;

  /* Will check these for validity when opening via 'fopen'. */
  yuv_path = argv[3];
  jpg_path = argv[4];

  yuv_fd = fopen(yuv_path, "r");
  if (!yuv_fd) {
    fprintf(stderr, "Invalid path to YUV file!\n");
    return 1;
  }

  fseek(yuv_fd, 0, SEEK_END);
  yuv_size = ftell(yuv_fd);
  fseek(yuv_fd, 0, SEEK_SET);

  /* Check that the file size matches 4:2:0 yuv. */
  if (yuv_size !=
   (size_t)luma_width*luma_height + 2*chroma_width*chroma_height) {
    fclose(yuv_fd);
    fprintf(stderr, "Unexpected input format!\n");
    return 1;
  }

  yuv_buffer = malloc(yuv_size);
  if (!yuv_buffer) {
    fclose(yuv_fd);
    fprintf(stderr, "Memory allocation failure!\n");
    return 1;
  }

  if (fread(yuv_buffer, yuv_size, 1, yuv_fd) != 1) {
    fprintf(stderr, "Error reading yuv file\n");
  };

  fclose(yuv_fd);

  frame_width = (luma_width + (16 - 1)) & ~(16 - 1);
  frame_height = (luma_height + (16 - 1)) & ~(16 - 1);

  jpg_buffer =
   malloc(frame_width*frame_height + 2*(frame_width/2)*(frame_height/2));
  if (!jpg_buffer) {
    free(yuv_buffer);
    fprintf(stderr, "Memory allocation failure!\n");
    return 1;
  }

  extend_edge(jpg_buffer, frame_width, frame_height,
   yuv_buffer, luma_width, luma_height, chroma_width, chroma_height);

  free(yuv_buffer);

  cinfo.err = jpeg_std_error(&jerr);
  jpeg_create_compress(&cinfo);

  jpg_fd = fopen(jpg_path, "wb");
  if (!jpg_fd) {    
    free(jpg_buffer);
    fprintf(stderr, "Invalid path to JPEG file!\n");
    return 1;
  }

  jpeg_stdio_dest(&cinfo, jpg_fd);

  cinfo.image_width = luma_width;
  cinfo.image_height = luma_height;
  cinfo.input_components = 3;

  cinfo.in_color_space = JCS_YCbCr;
  jpeg_set_defaults(&cinfo);

  cinfo.raw_data_in = TRUE;

  cinfo.comp_info[0].h_samp_factor = 2;
  cinfo.comp_info[0].v_samp_factor = 2;
  cinfo.comp_info[0].dc_tbl_no = 0;
  cinfo.comp_info[0].ac_tbl_no = 0;
  cinfo.comp_info[0].quant_tbl_no = 0;

  cinfo.comp_info[1].h_samp_factor = 1;
  cinfo.comp_info[1].v_samp_factor = 1;
  cinfo.comp_info[1].dc_tbl_no = 1;
  cinfo.comp_info[1].ac_tbl_no = 1;
  cinfo.comp_info[1].quant_tbl_no = 1;

  cinfo.comp_info[2].h_samp_factor = 1;
  cinfo.comp_info[2].v_samp_factor = 1;
  cinfo.comp_info[2].dc_tbl_no = 1;
  cinfo.comp_info[2].ac_tbl_no = 1;
  cinfo.comp_info[2].quant_tbl_no = 1;

  jpeg_set_quality(&cinfo, quality, TRUE);
  cinfo.optimize_coding = TRUE;

  jpeg_start_compress(&cinfo, TRUE);

  plane_pointer[0] = yrow_pointer;
  plane_pointer[1] = cbrow_pointer;
  plane_pointer[2] = crrow_pointer;

  while (cinfo.next_scanline < cinfo.image_height) {
    int scanline;
    scanline = cinfo.next_scanline;

    for (y = 0; y < 16; y++) {
      yrow_pointer[y] = &jpg_buffer[frame_width*(scanline + y)];
    }
    for (y = 0; y < 8; y++) {
      cbrow_pointer[y] = &jpg_buffer[frame_width*frame_height +
       (frame_width/2)*((scanline/2) + y)];
      crrow_pointer[y] = &jpg_buffer[frame_width*frame_height +
       (frame_width/2)*(frame_height/2) + (frame_width/2)*((scanline/2) + y)];
    }
    jpeg_write_raw_data(&cinfo, plane_pointer, 16);
  }

  jpeg_finish_compress(&cinfo);
  jpeg_destroy_compress(&cinfo);

  free(jpg_buffer);
  fclose(jpg_fd);

  return 0;
}
