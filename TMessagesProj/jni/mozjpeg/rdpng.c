
#include "cdjpeg.h"   /* Common decls for cjpeg/djpeg applications */

#ifdef PNG_SUPPORTED

#include <png.h>      /* if this fails, you need to install libpng-devel */


typedef struct png_source_struct {
    struct cjpeg_source_struct pub;
    png_structp  png_ptr;
    png_infop    info_ptr;
    JDIMENSION   current_row;
} png_source_struct;


METHODDEF(void)
finish_input_png (j_compress_ptr cinfo, cjpeg_source_ptr sinfo);

METHODDEF(JDIMENSION)
get_pixel_rows_png (j_compress_ptr cinfo, cjpeg_source_ptr sinfo);

METHODDEF(void)
start_input_png (j_compress_ptr cinfo, cjpeg_source_ptr sinfo);


GLOBAL(cjpeg_source_ptr)
jinit_read_png(j_compress_ptr cinfo)
{
    png_source_struct *source = (*cinfo->mem->alloc_small)((j_common_ptr) cinfo, JPOOL_IMAGE, sizeof(png_source_struct));

    memset(source, 0, sizeof(*source));

    /* Fill in method ptrs, except get_pixel_rows which start_input sets */
    source->pub.start_input = start_input_png;
    source->pub.finish_input = finish_input_png;

    return &source->pub;
}

METHODDEF(void) error_input_png(png_structp png_ptr, png_const_charp msg) {
    j_compress_ptr cinfo = png_get_error_ptr(png_ptr);
    ERREXITS(cinfo, JERR_PNG_ERROR, msg);
}

/* This is a small ICC profile for sRGB */
static unsigned char tiny_srgb[] = {0,0,2,24,108,99,109,115,2,16,0,0,109,110,
116,114,82,71,66,32,88,89,90,32,7,220,0,1,0,25,0,3,0,41,0,57,97,99,115,112,65,
80,80,76,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,246,214,0,1,0,0,0,
0,211,45,108,99,109,115,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,
0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,10,100,101,115,99,0,0,0,252,0,0,0,106,
99,112,114,116,0,0,1,104,0,0,0,11,119,116,112,116,0,0,1,116,0,0,0,20,98,107,
112,116,0,0,1,136,0,0,0,20,114,88,89,90,0,0,1,156,0,0,0,20,103,88,89,90,0,0,1,
176,0,0,0,20,98,88,89,90,0,0,1,196,0,0,0,20,114,84,82,67,0,0,1,216,0,0,0,64,98,
84,82,67,0,0,1,216,0,0,0,64,103,84,82,67,0,0,1,216,0,0,0,64,100,101,115,99,0,0,
0,0,0,0,0,13,115,82,71,66,32,77,111,122,74,80,69,71,0,0,0,0,0,0,0,0,1,0,0,0,0,
1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,
0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,116,101,120,
116,0,0,0,0,80,68,0,0,88,89,90,32,0,0,0,0,0,0,246,214,0,1,0,0,0,0,211,45,88,89,
90,32,0,0,0,0,0,0,3,22,0,0,3,51,0,0,2,164,88,89,90,32,0,0,0,0,0,0,111,162,0,0,
56,245,0,0,3,144,88,89,90,32,0,0,0,0,0,0,98,153,0,0,183,133,0,0,24,218,88,89,
90,32,0,0,0,0,0,0,36,160,0,0,15,132,0,0,182,207,99,117,114,118,0,0,0,0,0,0,0,
26,0,0,0,203,1,201,3,99,5,146,8,107,11,246,16,63,21,81,27,52,33,241,41,144,50,
24,59,146,70,5,81,119,93,237,107,112,122,5,137,177,154,124,172,105,191,125,211,
195,233,48,255,255,};

METHODDEF(void)
start_input_png (j_compress_ptr cinfo, cjpeg_source_ptr sinfo)
{
    png_source_struct *source = (png_source_struct *)sinfo;
    png_uint_32 width, height;
    int bit_depth, color_type;
    int has_srgb_chunk;
    double gamma;
    png_bytep profile;
    png_charp unused1;
    int unused2;
    png_uint_32 proflen;
    int has_profile;
    size_t datalen;
    JOCTET *dataptr;
    struct jpeg_marker_struct *marker;
    png_size_t rowbytes;

    source->png_ptr = png_create_read_struct(PNG_LIBPNG_VER_STRING, cinfo, error_input_png, NULL);
    source->info_ptr = png_create_info_struct(source->png_ptr);

    if (!source->png_ptr || !source->info_ptr) {
        ERREXITS(cinfo, JERR_PNG_ERROR, "Can't create read/info_struct");
        return;
    }

    png_set_palette_to_rgb(source->png_ptr);
    png_set_expand_gray_1_2_4_to_8(source->png_ptr);
    png_set_strip_alpha(source->png_ptr);
    png_set_interlace_handling(source->png_ptr);

    png_init_io(source->png_ptr, source->pub.input_file);
    png_read_info(source->png_ptr, source->info_ptr);

    png_get_IHDR(source->png_ptr, source->info_ptr, &width, &height,
                 &bit_depth, &color_type, NULL, NULL, NULL);

    if (width > 65535 || height > 65535) {
        ERREXITS(cinfo, JERR_PNG_ERROR, "Image too large");
        return;
    }

    if (color_type == PNG_COLOR_TYPE_GRAY || color_type == PNG_COLOR_TYPE_GRAY_ALPHA) {
        cinfo->in_color_space = JCS_GRAYSCALE;
        cinfo->input_components = 1;
    } else {
        cinfo->in_color_space = JCS_RGB;
        cinfo->input_components = 3;
    }

    if (bit_depth == 16) {
        png_set_strip_16(source->png_ptr);
    }

    cinfo->data_precision = 8;
    cinfo->image_width = width;
    cinfo->image_height = height;

    has_srgb_chunk = png_get_valid(source->png_ptr, source->info_ptr, PNG_INFO_sRGB);

    gamma = 0.45455;
    if (!has_srgb_chunk) {
        png_get_gAMA(source->png_ptr, source->info_ptr, &gamma);
    }
    cinfo->input_gamma = gamma;
    sinfo->get_pixel_rows = get_pixel_rows_png;

    source->pub.marker_list = NULL;
    profile = NULL;
    unused1 = NULL;
    unused2 = 0;
    proflen = 0;
    has_profile = 0;

    if (has_srgb_chunk) {
        /* PNG can declare use of an sRGB profile without embedding an ICC file, but JPEG doesn't have such feature */
        has_profile = 1;
        profile = tiny_srgb;
        proflen = sizeof(tiny_srgb);
    } else {
        has_profile = png_get_iCCP(source->png_ptr, source->info_ptr, &unused1, &unused2, &profile, &proflen); /* your libpng is out of date if you get a warning here */
    }

    if (has_profile && profile && proflen) {
        if (proflen < 65535-14) {
            datalen = proflen + 14;
            dataptr = (*cinfo->mem->alloc_small)((j_common_ptr)cinfo, JPOOL_IMAGE, datalen);
            memcpy(dataptr, "ICC_PROFILE\0\x01\x01", 14);
            memcpy(dataptr + 14, profile, proflen);
            marker = (*cinfo->mem->alloc_small)((j_common_ptr)cinfo, JPOOL_IMAGE, sizeof(struct jpeg_marker_struct));
            marker->next = NULL;
            marker->marker = JPEG_APP0+2;
            marker->original_length = 0;
            marker->data_length = datalen;
            marker->data = dataptr;
            source->pub.marker_list = marker;
        } else {
            WARNMS(cinfo, JERR_PNG_PROFILETOOLARGE);
        }
    }

    png_read_update_info(source->png_ptr, source->info_ptr);

    rowbytes = png_get_rowbytes(source->png_ptr, source->info_ptr);

    source->pub.buffer = (*cinfo->mem->alloc_sarray)((j_common_ptr)cinfo, JPOOL_IMAGE, (JDIMENSION)rowbytes, 1);
    source->pub.buffer_height = 1;
}

METHODDEF(JDIMENSION)
get_pixel_rows_png (j_compress_ptr cinfo, cjpeg_source_ptr sinfo)
{
    png_source_struct *source = (png_source_struct *)sinfo;

    png_read_row(source->png_ptr, source->pub.buffer[0], NULL);
    return 1;
}

METHODDEF(void)
finish_input_png (j_compress_ptr cinfo, cjpeg_source_ptr sinfo)
{
    png_source_struct *source = (png_source_struct *)sinfo;

    png_read_end(source->png_ptr, source->info_ptr);
    png_destroy_read_struct(&source->png_ptr, &source->info_ptr, NULL);
}

#endif
