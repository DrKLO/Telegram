#include <jni.h>
#include <stdio.h>
#include <setjmp.h>
#include <android/bitmap.h>
#include <libjpeg/jpeglib.h>
#include "utils.h"

typedef struct my_error_mgr {
    struct jpeg_error_mgr pub;
    jmp_buf setjmp_buffer;
} *my_error_ptr;


METHODDEF(void) my_error_exit(j_common_ptr cinfo) {
    my_error_ptr myerr = (my_error_ptr) cinfo->err;
    (*cinfo->err->output_message) (cinfo);
    longjmp(myerr->setjmp_buffer, 1);
}

JNIEXPORT void Java_org_telegram_messenger_Utilities_loadBitmap(JNIEnv *env, jclass class, jstring path, jobject bitmap, int scale) {
    
    AndroidBitmapInfo info;
    int i;
    
    if ((i = AndroidBitmap_getInfo(env, bitmap, &info)) >= 0) {
        char *fileName = (*env)->GetStringUTFChars(env, path, NULL);
        FILE *infile;
        
        if ((infile = fopen(fileName, "rb"))) {
            struct my_error_mgr jerr;
            struct jpeg_decompress_struct cinfo;
            
            cinfo.err = jpeg_std_error(&jerr.pub);
            jerr.pub.error_exit = my_error_exit;
            
            if (!setjmp(jerr.setjmp_buffer)) {
                jpeg_create_decompress(&cinfo);
                jpeg_stdio_src(&cinfo, infile);
                
                jpeg_read_header(&cinfo, TRUE);
                
                cinfo.scale_denom = scale;
                cinfo.scale_num = 1;
                
                jpeg_start_decompress(&cinfo);
                int row_stride = cinfo.output_width * cinfo.output_components;
                JSAMPARRAY buffer = (*cinfo.mem->alloc_sarray) ((j_common_ptr) &cinfo, JPOOL_IMAGE, row_stride, 1);
                
                unsigned char *pixels;
                if ((i = AndroidBitmap_lockPixels(env, bitmap, &pixels)) >= 0) {
                    
                    int rowCount = min(cinfo.output_height, info.height);
                    int colCount = min(cinfo.output_width, info.width);
                    
                    while (cinfo.output_scanline < rowCount) {
                        jpeg_read_scanlines(&cinfo, buffer, 1);
                        
                        if (info.format == ANDROID_BITMAP_FORMAT_RGBA_8888) {
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
                                    pixels[i * 4] = buffer[0][i * 3];
                                    pixels[i * 4 + 1] = buffer[0][i * 3 + 1];
                                    pixels[i * 4 + 2] = buffer[0][i * 3 + 2];
                                    pixels[i * 4 + 3] = 255;
                                    c += 4;
                                }
                            }
                        } else if (info.format == ANDROID_BITMAP_FORMAT_RGB_565) {
                            
                        }
                        
                        pixels += info.stride;
                    }
                    
                    AndroidBitmap_unlockPixels(env, bitmap);
                } else {
                    throwException(env, "AndroidBitmap_lockPixels() failed ! error=%d", i);
                }
                
                jpeg_finish_decompress(&cinfo);
            } else {
                throwException(env, "the JPEG code has signaled an error");
            }
            
            jpeg_destroy_decompress(&cinfo);
            fclose(infile);
        } else {
            throwException(env, "can't open %s\n", fileName);
        }
        
        (*env)->ReleaseStringUTFChars(env, path, fileName);
    } else {
        throwException(env, "AndroidBitmap_getInfo() failed ! error=%d", i);
    }
}
