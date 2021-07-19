/*
 * rdjpeg.c
 *
 * Copyright (C) 1991-1996, Thomas G. Lane.
 * mozjpeg Modifications:
 * Copyright (C) 2014, Mozilla Corporation.
 * This file is part of the Independent JPEG Group's software.
 * For conditions of distribution and use, see the accompanying README file.
 *
 */

#include "cdjpeg.h"             /* Common decls for cjpeg/djpeg applications */

#if JPEG_RAW_READER
#define NUM_ROWS 32
#endif

/* Private version of data source object */

typedef struct _jpeg_source_struct * jpeg_source_ptr;

typedef struct _jpeg_source_struct {
  struct cjpeg_source_struct pub; /* public fields */

  j_compress_ptr cinfo;         /* back link saves passing separate parm */
  
  struct jpeg_decompress_struct dinfo;
  struct jpeg_error_mgr jerr;
} jpeg_source_struct;



METHODDEF(JDIMENSION)
get_rows (j_compress_ptr cinfo, cjpeg_source_ptr sinfo)
{
  jpeg_source_ptr source = (jpeg_source_ptr) sinfo;
  
#if !JPEG_RAW_READER
  return jpeg_read_scanlines(&source->dinfo, source->pub.buffer, source->pub.buffer_height);
#else
  jpeg_read_raw_data(&source->dinfo, source->pub.plane_pointer, 8*cinfo->max_v_samp_factor);

  return 8*cinfo->max_v_samp_factor;
#endif
}


/*
 * Read the file header; return image size and component count.
 */

METHODDEF(void)
start_input_jpeg (j_compress_ptr cinfo, cjpeg_source_ptr sinfo)
{
#if JPEG_RAW_READER
  int i;
#endif
  int m;
  jpeg_source_ptr source = (jpeg_source_ptr) sinfo;

  source->dinfo.err = jpeg_std_error(&source->jerr);
  jpeg_create_decompress(&source->dinfo);
  jpeg_stdio_src(&source->dinfo, source->pub.input_file);
  
  jpeg_save_markers(&source->dinfo, JPEG_COM, 0xFFFF);
  
  for (m = 0; m < 16; m++)
    jpeg_save_markers(&source->dinfo, JPEG_APP0 + m, 0xFFFF);

  jpeg_read_header(&source->dinfo, TRUE);

  source->pub.marker_list = source->dinfo.marker_list;
  
#if !JPEG_RAW_READER
  source->dinfo.raw_data_out = FALSE;
  
  jpeg_start_decompress(&source->dinfo);

  cinfo->in_color_space = source->dinfo.out_color_space;
  cinfo->input_components = source->dinfo.output_components;
  cinfo->data_precision = source->dinfo.data_precision;
  cinfo->image_width = source->dinfo.image_width;
  cinfo->image_height = source->dinfo.image_height;

  cinfo->raw_data_in = FALSE;

  source->pub.buffer = (*cinfo->mem->alloc_sarray)
  ((j_common_ptr) cinfo, JPOOL_IMAGE,
   (JDIMENSION) (cinfo->image_width * cinfo->input_components), (JDIMENSION) 1);
  source->pub.buffer_height = 1;
#else
  source->dinfo.raw_data_out = TRUE;
  source->dinfo.do_fancy_upsampling = FALSE;
  
  jpeg_start_decompress(&source->dinfo);

  cinfo->in_color_space = source->dinfo.out_color_space;
  cinfo->input_components = source->dinfo.output_components;
  cinfo->data_precision = source->dinfo.data_precision;
  cinfo->image_width = source->dinfo.image_width;
  cinfo->image_height = source->dinfo.image_height;
  
  jpeg_set_colorspace(cinfo, source->dinfo.jpeg_color_space);
  
  cinfo->max_v_samp_factor = source->dinfo.max_v_samp_factor;
  cinfo->max_h_samp_factor = source->dinfo.max_h_samp_factor;
  
  cinfo->raw_data_in = TRUE;
#if JPEG_LIB_VERSION >= 70
  cinfo->do_fancy_upsampling = FALSE;
#endif
  
  for (i = 0; i < cinfo->input_components; i++) {
    cinfo->comp_info[i].h_samp_factor = source->dinfo.comp_info[i].h_samp_factor;
    cinfo->comp_info[i].v_samp_factor = source->dinfo.comp_info[i].v_samp_factor;
    
    source->pub.plane_pointer[i] =  (*cinfo->mem->alloc_sarray)
    ((j_common_ptr) cinfo, JPOOL_IMAGE,
     (JDIMENSION) cinfo->image_width, (JDIMENSION) NUM_ROWS);
  }
#endif

  source->pub.get_pixel_rows = get_rows;
}


/*
 * Finish up at the end of the file.
 */

METHODDEF(void)
finish_input_jpeg (j_compress_ptr cinfo, cjpeg_source_ptr sinfo)
{
  jpeg_source_ptr source = (jpeg_source_ptr) sinfo;
  
  jpeg_finish_decompress(&source->dinfo);
  jpeg_destroy_decompress(&source->dinfo);
}


/*
 * The module selection routine for JPEG format input.
 */

GLOBAL(cjpeg_source_ptr)
jinit_read_jpeg (j_compress_ptr cinfo)
{
  jpeg_source_ptr source;

  /* Create module interface object */
  source = (jpeg_source_ptr)
      (*cinfo->mem->alloc_small) ((j_common_ptr) cinfo, JPOOL_IMAGE,
                                  sizeof(jpeg_source_struct));
  source->cinfo = cinfo;        /* make back link for subroutines */
  /* Fill in method ptrs, except get_pixel_rows which start_input sets */
  source->pub.start_input = start_input_jpeg;
  source->pub.finish_input = finish_input_jpeg;

  return (cjpeg_source_ptr) source;
}
