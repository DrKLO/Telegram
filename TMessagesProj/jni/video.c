#include <jni.h>
#include <libyuv.h>
#include <utils.h>

enum COLOR_FORMATTYPE {
    COLOR_FormatMonochrome              = 1,
    COLOR_Format8bitRGB332              = 2,
    COLOR_Format12bitRGB444             = 3,
    COLOR_Format16bitARGB4444           = 4,
    COLOR_Format16bitARGB1555           = 5,
    COLOR_Format16bitRGB565             = 6,
    COLOR_Format16bitBGR565             = 7,
    COLOR_Format18bitRGB666             = 8,
    COLOR_Format18bitARGB1665           = 9,
    COLOR_Format19bitARGB1666           = 10,
    COLOR_Format24bitRGB888             = 11,
    COLOR_Format24bitBGR888             = 12,
    COLOR_Format24bitARGB1887           = 13,
    COLOR_Format25bitARGB1888           = 14,
    COLOR_Format32bitBGRA8888           = 15,
    COLOR_Format32bitARGB8888           = 16,
    COLOR_FormatYUV411Planar            = 17,
    COLOR_FormatYUV411PackedPlanar      = 18,
    COLOR_FormatYUV420Planar            = 19,
    COLOR_FormatYUV420PackedPlanar      = 20,
    COLOR_FormatYUV420SemiPlanar        = 21,
    COLOR_FormatYUV422Planar            = 22,
    COLOR_FormatYUV422PackedPlanar      = 23,
    COLOR_FormatYUV422SemiPlanar        = 24,
    COLOR_FormatYCbYCr                  = 25,
    COLOR_FormatYCrYCb                  = 26,
    COLOR_FormatCbYCrY                  = 27,
    COLOR_FormatCrYCbY                  = 28,
    COLOR_FormatYUV444Interleaved       = 29,
    COLOR_FormatRawBayer8bit            = 30,
    COLOR_FormatRawBayer10bit           = 31,
    COLOR_FormatRawBayer8bitcompressed  = 32,
    COLOR_FormatL2                      = 33,
    COLOR_FormatL4                      = 34,
    COLOR_FormatL8                      = 35,
    COLOR_FormatL16                     = 36,
    COLOR_FormatL24                     = 37,
    COLOR_FormatL32                     = 38,
    COLOR_FormatYUV420PackedSemiPlanar  = 39,
    COLOR_FormatYUV422PackedSemiPlanar  = 40,
    COLOR_Format18BitBGR666             = 41,
    COLOR_Format24BitARGB6666           = 42,
    COLOR_Format24BitABGR6666           = 43,

    COLOR_TI_FormatYUV420PackedSemiPlanar = 0x7f000100,
    COLOR_FormatSurface                   = 0x7F000789,
    COLOR_QCOM_FormatYUV420SemiPlanar     = 0x7fa30c00
};

int isSemiPlanarYUV(int colorFormat) {
    switch (colorFormat) {
        case COLOR_FormatYUV420Planar:
        case COLOR_FormatYUV420PackedPlanar:
            return 0;
        case COLOR_FormatYUV420SemiPlanar:
        case COLOR_FormatYUV420PackedSemiPlanar:
        case COLOR_TI_FormatYUV420PackedSemiPlanar:
            return 1;
        default:
            return 0;
    }
}

JNIEXPORT int Java_org_telegram_messenger_Utilities_convertVideoFrame(JNIEnv *env, jclass class, jobject src, jobject dest, int destFormat, int width, int height, int padding, int swap) {
    if (!src || !dest || !destFormat) {
        return 0;
    }
    
    jbyte *srcBuff = (*env)->GetDirectBufferAddress(env, src);
    jbyte *destBuff = (*env)->GetDirectBufferAddress(env, dest);

    int half_width = (width + 1) / 2;
    int half_height = (height + 1) / 2;
    
    if (!isSemiPlanarYUV(destFormat)) {
        if (!swap) {
            ARGBToI420(srcBuff, width * 4,
                       destBuff, width,
                       destBuff + width * height + half_width * half_height + padding * 5 / 4, half_width,
                       destBuff + width * height + padding, half_width,
                       width, height);
        } else {
            ARGBToI420(srcBuff, width * 4,
                       destBuff, width,
                       destBuff + width * height + padding, half_width,
                       destBuff + width * height + half_width * half_height + padding * 5 / 4, half_width,
                       width, height);
        }
    } else {
        if (!swap) {
            ARGBToNV21(srcBuff, width * 4,
                       destBuff, width,
                       destBuff + width * height + padding, half_width * 2,
                       width, height);
        } else {
            ARGBToNV12(srcBuff, width * 4,
                       destBuff, width,
                       destBuff + width * height + padding, half_width * 2,
                       width, height);
        }
    }
    
    return 1;
}
