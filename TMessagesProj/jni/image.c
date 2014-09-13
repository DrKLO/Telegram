#include <jni.h>
#include <stdio.h>
#include <setjmp.h>
#include <libjpeg/jpeglib.h>
#include <android/bitmap.h>
#include "utils.h"

static inline uint64_t get_colors (const uint8_t *p) {
    return p[0] + (p[1] << 16) + ((uint64_t)p[2] << 32);
}

static void fastBlur(int imageWidth, int imageHeight, int imageStride, void *pixels) {
    uint8_t *pix = (uint8_t *)pixels;
    const int w = imageWidth;
    const int h = imageHeight;
    const int stride = imageStride;
    const int radius = 3;
    const int r1 = radius + 1;
    const int div = radius * 2 + 1;
    
    if (radius > 15 || div >= w || div >= h || w * h > 90 * 90 || imageStride > imageWidth * 4) {
        return;
    }
    
    uint64_t *rgb = malloc(imageWidth * imageHeight * sizeof(uint64_t));
    
    int x, y, i;
    
    int yw = 0;
    const int we = w - r1;
    for (y = 0; y < h; y++) {
        uint64_t cur = get_colors (&pix[yw]);
        uint64_t rgballsum = -radius * cur;
        uint64_t rgbsum = cur * ((r1 * (r1 + 1)) >> 1);
        
        for (i = 1; i <= radius; i++) {
            uint64_t cur = get_colors (&pix[yw + i * 4]);
            rgbsum += cur * (r1 - i);
            rgballsum += cur;
        }
        
        x = 0;
        
        #define update(start, middle, end)  \
                rgb[y * w + x] = (rgbsum >> 4) & 0x00FF00FF00FF00FFLL; \
                rgballsum += get_colors (&pix[yw + (start) * 4]) - 2 * get_colors (&pix[yw + (middle) * 4]) + get_colors (&pix[yw + (end) * 4]); \
                rgbsum += rgballsum;        \
                x++;                        \

        while (x < r1) {
            update (0, x, x + r1);
        }
        while (x < we) {
            update (x - r1, x, x + r1);
        }
        while (x < w) {
            update (x - r1, x, w - 1);
        }
        
        #undef update
        
        yw += stride;
    }
    
    const int he = h - r1;
    for (x = 0; x < w; x++) {
        uint64_t rgballsum = -radius * rgb[x];
        uint64_t rgbsum = rgb[x] * ((r1 * (r1 + 1)) >> 1);
        for (i = 1; i <= radius; i++) {
            rgbsum += rgb[i * w + x] * (r1 - i);
            rgballsum += rgb[i * w + x];
        }
        
        y = 0;
        int yi = x * 4;
        
        #define update(start, middle, end)  \
                int64_t res = rgbsum >> 4;   \
                pix[yi] = res;              \
                pix[yi + 1] = res >> 16;    \
                pix[yi + 2] = res >> 32;    \
                rgballsum += rgb[x + (start) * w] - 2 * rgb[x + (middle) * w] + rgb[x + (end) * w]; \
                rgbsum += rgballsum;        \
                y++;                        \
                yi += stride;
        
        while (y < r1) {
            update (0, y, y + r1);
        }
        while (y < he) {
            update (y - r1, y, y + r1);
        }
        while (y < h) {
            update (y - r1, y, h - 1);
        }
        #undef update
    }
    
    free(rgb);
}

typedef struct my_error_mgr {
    struct jpeg_error_mgr pub;
    jmp_buf setjmp_buffer;
} *my_error_ptr;


METHODDEF(void) my_error_exit(j_common_ptr cinfo) {
    my_error_ptr myerr = (my_error_ptr) cinfo->err;
    (*cinfo->err->output_message) (cinfo);
    longjmp(myerr->setjmp_buffer, 1);
}

JNIEXPORT void Java_org_telegram_messenger_Utilities_blurBitmap(JNIEnv *env, jclass class, jobject bitmap) {
    if (!bitmap) {
        return;
    }
    
    AndroidBitmapInfo info;
    
    if (AndroidBitmap_getInfo(env, bitmap, &info) < 0) {
        return;
    }
    
    if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888 || !info.width || !info.height || !info.stride) {
        return;
    }
    
    void *pixels = 0;
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) < 0) {
        return;
    }
    fastBlur(info.width, info.height, info.stride, pixels);
    AndroidBitmap_unlockPixels(env, bitmap);
}

JNIEXPORT void Java_org_telegram_messenger_Utilities_loadBitmap(JNIEnv *env, jclass class, jstring path, jintArray bitmap, int scale, int format, int width, int height) {
    
    int i;
    
    char *fileName = (*env)->GetStringUTFChars(env, path, NULL);
    FILE *infile;
    
    if ((infile = fopen(fileName, "rb"))) {
        struct my_error_mgr jerr;
        struct jpeg_decompress_struct cinfo;
        
        cinfo.err = jpeg_std_error(&jerr.pub);
        jerr.pub.error_exit = my_error_exit;
        
        if (!setjmp(jerr.setjmp_buffer)) {
            unsigned char *bitmapBuf = (*env)->GetPrimitiveArrayCritical(env, bitmap, 0);
            if (bitmapBuf) {
                jpeg_create_decompress(&cinfo);
                jpeg_stdio_src(&cinfo, infile);
                
                jpeg_read_header(&cinfo, TRUE);
                
                cinfo.scale_denom = scale;
                cinfo.scale_num = 1;
                
                jpeg_start_decompress(&cinfo);
                int row_stride = cinfo.output_width * cinfo.output_components;
                JSAMPARRAY buffer = (*cinfo.mem->alloc_sarray) ((j_common_ptr) &cinfo, JPOOL_IMAGE, row_stride, 1);
                int stride = width;
                if (format == 0) {
                    stride *= 4;
                } else if (format == 1) {
                    stride *= 2;
                }

                unsigned char *pixels = bitmapBuf;

                int rowCount = min(cinfo.output_height, height);
                int colCount = min(cinfo.output_width, width);
                
                while (cinfo.output_scanline < rowCount) {
                    jpeg_read_scanlines(&cinfo, buffer, 1);
                    
                    if (format == 0) {
                        if (cinfo.out_color_space == JCS_GRAYSCALE) {
                            for (i = 0; i < colCount; i++) {
                                float alpha = buffer[0][i] / 255.0f;
                                pixels[i * 4] *= alpha;
                                pixels[i * 4 + 1] *= alpha;
                                pixels[i * 4 + 2] *= alpha;
                                pixels[i * 4 + 3] = buffer[0][i];
                            }
                        } else {
                            int c = 0;
                            for (i = 0; i < colCount; i++) {
                                pixels[i * 4] = buffer[0][i * 3 + 2];
                                pixels[i * 4 + 1] = buffer[0][i * 3 + 1];
                                pixels[i * 4 + 2] = buffer[0][i * 3];
                                pixels[i * 4 + 3] = 255;
                                c += 4;
                            }
                        }
                    } else if (format == 1) {
                        
                    }
                    
                    pixels += stride;
                }
                (*env)->ReleasePrimitiveArrayCritical(env, bitmap, bitmapBuf, 0);
                jpeg_finish_decompress(&cinfo);
            } else {
                throwException(env, "can't get bitmap buff");
            }
        } else {
            throwException(env, "the JPEG code has signaled an error");
        }
        
        jpeg_destroy_decompress(&cinfo);
        fclose(infile);
    } else {
        throwException(env, "can't open %s", fileName);
    }
    
    (*env)->ReleaseStringUTFChars(env, path, fileName);
}
