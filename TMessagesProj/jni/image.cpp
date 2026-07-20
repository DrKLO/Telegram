#include <jni.h>
#include <cstdio>
#include <csetjmp>
#include <cstdlib>
#include <cstring>
#include <cmath>
#include <cstdint>
#include <mutex>
#include <unistd.h>
#include <android/bitmap.h>
#include <string>
#include <limits.h>
#include "libyuv/scale_argb.h"
//#include <mozjpeg/java/org_libjpegturbo_turbojpeg_TJ.h>
//#include <mozjpeg/jpeglib.h>
#include <tgnet/FileLog.h>
#include <vector>
#include <algorithm>
//#include "mozjpeg/turbojpeg.h"
#include "c_utils.h"

extern "C" {
jclass jclass_NullPointerException;
jclass jclass_RuntimeException;

jclass jclass_Options;
jfieldID jclass_Options_inJustDecodeBounds;
jfieldID jclass_Options_outHeight;
jfieldID jclass_Options_outWidth;

jint imageOnJNILoad(JavaVM *vm, JNIEnv *env) {
    DEBUG_REF("image.cpp nullpointerexception class");
    jclass_NullPointerException = (jclass) env->NewGlobalRef(env->FindClass("java/lang/NullPointerException"));
    if (jclass_NullPointerException == 0) {
        return JNI_FALSE;
    }
    DEBUG_REF("image.cpp runtimeexception class");
    jclass_RuntimeException = (jclass) env->NewGlobalRef(env->FindClass("java/lang/RuntimeException"));
    if (jclass_RuntimeException == 0) {
        return JNI_FALSE;
    }
    DEBUG_REF("image.cpp bitmapfactoryoptions class");
    jclass_Options = (jclass) env->NewGlobalRef(env->FindClass("android/graphics/BitmapFactory$Options"));
    if (jclass_Options == 0) {
        return JNI_FALSE;
    }
    jclass_Options_inJustDecodeBounds = env->GetFieldID(jclass_Options, "inJustDecodeBounds", "Z");
    if (jclass_Options_inJustDecodeBounds == 0) {
        return JNI_FALSE;
    }
    jclass_Options_outHeight = env->GetFieldID(jclass_Options, "outHeight", "I");
    if (jclass_Options_outHeight == 0) {
        return JNI_FALSE;
    }
    jclass_Options_outWidth = env->GetFieldID(jclass_Options, "outWidth", "I");
    if (jclass_Options_outWidth == 0) {
        return JNI_FALSE;
    }

    return JNI_TRUE;
}

static inline uint64_t getColors(const uint8_t *p) {
    return p[0] + (p[1] << 16) + ((uint64_t) p[2] << 32) + ((uint64_t) p[3] << 48);
}

static inline uint64_t getColors565(const uint8_t *p) {
    uint16_t *ps = (uint16_t *) p;
    return ((((ps[0] & 0xF800) >> 11) * 255) / 31) + (((((ps[0] & 0x07E0) >> 5) * 255) / 63) << 16) + ((uint64_t)(((ps[0] & 0x001F) * 255) / 31) << 32);
}

static void fastBlurMore(int32_t w, int32_t h, int32_t stride, uint8_t *pix, int32_t radius) {
    const int32_t r1 = radius + 1;
    const int32_t div = radius * 2 + 1;

    if (radius > 15 || div >= w || div >= h || w * h > 150 * 150 || stride > w * 4) {
        return;
    }

    uint64_t *rgb = new uint64_t[w * h];
    if (rgb == NULL) {
        return;
    }

    int32_t x, y, i;

    int32_t yw = 0;
    const int32_t we = w - r1;
    for (y = 0; y < h; y++) {
        uint64_t cur = getColors(&pix[yw]);
        uint64_t rgballsum = -radius * cur;
        uint64_t rgbsum = cur * ((r1 * (r1 + 1)) >> 1);

        for (i = 1; i <= radius; i++) {
            cur = getColors(&pix[yw + i * 4]);
            rgbsum += cur * (r1 - i);
            rgballsum += cur;
        }

        x = 0;

#define update(start, middle, end) \
            rgb[y * w + x] = (rgbsum >> 6) & 0x00FF00FF00FF00FF; \
            rgballsum += getColors(&pix[yw + (start) * 4]) - 2 * getColors(&pix[yw + (middle) * 4]) + getColors(&pix[yw + (end) * 4]); \
            rgbsum += rgballsum; \
            x++; \

        while (x < r1) {
            update (0, x, x + r1)
        }
        while (x < we) {
            update (x - r1, x, x + r1)
        }
        while (x < w) {
            update (x - r1, x, w - 1)
        }
#undef update

        yw += stride;
    }

    const int32_t he = h - r1;
    for (x = 0; x < w; x++) {
        uint64_t rgballsum = -radius * rgb[x];
        uint64_t rgbsum = rgb[x] * ((r1 * (r1 + 1)) >> 1);
        for (i = 1; i <= radius; i++) {
            rgbsum += rgb[i * w + x] * (r1 - i);
            rgballsum += rgb[i * w + x];
        }

        y = 0;
        int32_t yi = x * 4;

#define update(start, middle, end) \
            int64_t res = rgbsum >> 6; \
            pix[yi] = res;              \
            pix[yi + 1] = res >> 16;    \
            pix[yi + 2] = res >> 32;    \
            pix[yi + 3] = res >> 48;    \
            rgballsum += rgb[x + (start) * w] - 2 * rgb[x + (middle) * w] + rgb[x + (end) * w]; \
            rgbsum += rgballsum; \
            y++; \
            yi += stride;

        while (y < r1) {
            update (0, y, y + r1)
        }
        while (y < he) {
            update (y - r1, y, y + r1)
        }
        while (y < h) {
            update (y - r1, y, h - 1)
        }
#undef update
    }

    delete[] rgb;
}

static void fastBlur(int32_t w, int32_t h, int32_t stride, uint8_t *pix, int32_t radius) {
    if (pix == nullptr) {
        return;
    }
    const int32_t r1 = radius + 1;
    const int32_t div = radius * 2 + 1;
    int32_t shift;
    if (radius == 1) {
        shift = 2;
    } else if (radius == 3) {
        shift = 4;
    } else if (radius == 7) {
        shift = 6;
    } else if (radius == 15) {
        shift = 8;
    } else {
        return;
    }

    if (radius > 15 || div >= w || div >= h || w * h > 150 * 150 || stride > w * 4) {
        return;
    }

    uint64_t *rgb = new uint64_t[w * h];
    if (rgb == nullptr) {
        return;
    }

    int32_t x, y, i;

    int32_t yw = 0;
    const int32_t we = w - r1;
    for (y = 0; y < h; y++) {
        uint64_t cur = getColors(&pix[yw]);
        uint64_t rgballsum = -radius * cur;
        uint64_t rgbsum = cur * ((r1 * (r1 + 1)) >> 1);

        for (i = 1; i <= radius; i++) {
            cur = getColors(&pix[yw + i * 4]);
            rgbsum += cur * (r1 - i);
            rgballsum += cur;
        }

        x = 0;

#define update(start, middle, end)  \
                rgb[y * w + x] = (rgbsum >> shift) & 0x00FF00FF00FF00FFLL; \
                rgballsum += getColors(&pix[yw + (start) * 4]) - 2 * getColors(&pix[yw + (middle) * 4]) + getColors(&pix[yw + (end) * 4]); \
                rgbsum += rgballsum;        \
                x++;                        \

        while (x < r1) {
            update (0, x, x + r1)
        }
        while (x < we) {
            update (x - r1, x, x + r1)
        }
        while (x < w) {
            update (x - r1, x, w - 1)
        }

#undef update

        yw += stride;
    }

    const int32_t he = h - r1;
    for (x = 0; x < w; x++) {
        uint64_t rgballsum = -radius * rgb[x];
        uint64_t rgbsum = rgb[x] * ((r1 * (r1 + 1)) >> 1);
        for (i = 1; i <= radius; i++) {
            rgbsum += rgb[i * w + x] * (r1 - i);
            rgballsum += rgb[i * w + x];
        }

        y = 0;
        int32_t yi = x * 4;

#define update(start, middle, end)  \
                int64_t res = rgbsum >> shift;   \
                pix[yi] = res;              \
                pix[yi + 1] = res >> 16;    \
                pix[yi + 2] = res >> 32;    \
                pix[yi + 3] = res >> 48;    \
                rgballsum += rgb[x + (start) * w] - 2 * rgb[x + (middle) * w] + rgb[x + (end) * w]; \
                rgbsum += rgballsum;        \
                y++;                        \
                yi += stride;

        while (y < r1) {
            update (0, y, y + r1)
        }
        while (y < he) {
            update (y - r1, y, y + r1)
        }
        while (y < h) {
            update (y - r1, y, h - 1)
        }
#undef update
    }

    delete[] rgb;
}

static void fastBlurMore565(int32_t w, int32_t h, int32_t stride, uint8_t *pix, int32_t radius) {
    const int32_t r1 = radius + 1;
    const int32_t div = radius * 2 + 1;

    if (radius > 15 || div >= w || div >= h || w * h > 150 * 150 || stride > w * 2) {
        return;
    }

    uint64_t *rgb = new uint64_t[w * h];
    if (rgb == NULL) {
        return;
    }

    int32_t x, y, i;

    int32_t yw = 0;
    const int32_t we = w - r1;
    for (y = 0; y < h; y++) {
        uint64_t cur = getColors565(&pix[yw]);
        uint64_t rgballsum = -radius * cur;
        uint64_t rgbsum = cur * ((r1 * (r1 + 1)) >> 1);

        for (i = 1; i <= radius; i++) {
            cur = getColors565(&pix[yw + i * 2]);
            rgbsum += cur * (r1 - i);
            rgballsum += cur;
        }

        x = 0;

#define update(start, middle, end) \
            rgb[y * w + x] = (rgbsum >> 6) & 0x00FF00FF00FF00FF; \
            rgballsum += getColors565(&pix[yw + (start) * 2]) - 2 * getColors565(&pix[yw + (middle) * 2]) + getColors565(&pix[yw + (end) * 2]); \
            rgbsum += rgballsum; \
            x++; \

        while (x < r1) {
            update (0, x, x + r1)
        }
        while (x < we) {
            update (x - r1, x, x + r1)
        }
        while (x < w) {
            update (x - r1, x, w - 1)
        }
#undef update

        yw += stride;
    }

    const int32_t he = h - r1;
    for (x = 0; x < w; x++) {
        uint64_t rgballsum = -radius * rgb[x];
        uint64_t rgbsum = rgb[x] * ((r1 * (r1 + 1)) >> 1);
        for (i = 1; i <= radius; i++) {
            rgbsum += rgb[i * w + x] * (r1 - i);
            rgballsum += rgb[i * w + x];
        }

        y = 0;
        int32_t yi = x * 2;

#define update(start, middle, end) \
            int64_t res = rgbsum >> 6; \
            pix[yi] = ((res >> 13) & 0xe0) | ((res >> 35) & 0x1f); \
            pix[yi + 1] = (res & 0xf8) | ((res >> 21) & 0x7); \
            rgballsum += rgb[x + (start) * w] - 2 * rgb[x + (middle) * w] + rgb[x + (end) * w]; \
            rgbsum += rgballsum; \
            y++; \
            yi += stride;

        while (y < r1) {
            update (0, y, y + r1)
        }
        while (y < he) {
            update (y - r1, y, y + r1)
        }
        while (y < h) {
            update (y - r1, y, h - 1)
        }
#undef update
    }

    delete[] rgb;
}

static void fastBlur565(int32_t w, int32_t h, int32_t stride, uint8_t *pix, int32_t radius) {
    if (pix == NULL) {
        return;
    }
    const int32_t r1 = radius + 1;
    const int32_t div = radius * 2 + 1;
    int32_t shift;
    if (radius == 1) {
        shift = 2;
    } else if (radius == 3) {
        shift = 4;
    } else if (radius == 7) {
        shift = 6;
    } else if (radius == 15) {
        shift = 8;
    } else {
        return;
    }

    if (radius > 15 || div >= w || div >= h || w * h > 150 * 150 || stride > w * 2) {
        return;
    }

    uint64_t *rgb = new uint64_t[w * h];
    if (rgb == NULL) {
        return;
    }

    int32_t x, y, i;

    int32_t yw = 0;
    const int32_t we = w - r1;
    for (y = 0; y < h; y++) {
        uint64_t cur = getColors565(&pix[yw]);
        uint64_t rgballsum = -radius * cur;
        uint64_t rgbsum = cur * ((r1 * (r1 + 1)) >> 1);

        for (i = 1; i <= radius; i++) {
            cur = getColors565(&pix[yw + i * 2]);
            rgbsum += cur * (r1 - i);
            rgballsum += cur;
        }

        x = 0;

#define update(start, middle, end)  \
                rgb[y * w + x] = (rgbsum >> shift) & 0x00FF00FF00FF00FFLL; \
                rgballsum += getColors565(&pix[yw + (start) * 2]) - 2 * getColors565(&pix[yw + (middle) * 2]) + getColors565(&pix[yw + (end) * 2]); \
                rgbsum += rgballsum;        \
                x++;

        while (x < r1) {
            update(0, x, x + r1)
        }
        while (x < we) {
            update(x - r1, x, x + r1)
        }
        while (x < w) {
            update(x - r1, x, w - 1)
        }

#undef update

        yw += stride;
    }

    const int32_t he = h - r1;
    for (x = 0; x < w; x++) {
        uint64_t rgballsum = -radius * rgb[x];
        uint64_t rgbsum = rgb[x] * ((r1 * (r1 + 1)) >> 1);
        for (i = 1; i <= radius; i++) {
            rgbsum += rgb[i * w + x] * (r1 - i);
            rgballsum += rgb[i * w + x];
        }

        y = 0;
        int32_t yi = x * 2;

#define update(start, middle, end)  \
                uint64_t res = rgbsum >> shift;   \
                pix[yi] = ((res >> 13) & 0xe0) | ((res >> 35) & 0x1f); \
                pix[yi + 1] = (res & 0xf8) | ((res >> 21) & 0x7); \
                rgballsum += rgb[x + (start) * w] - 2 * rgb[x + (middle) * w] + rgb[x + (end) * w]; \
                rgbsum += rgballsum;        \
                y++;                        \
                yi += stride;

        while (y < r1) {
            update (0, y, y + r1)
        }
        while (y < he) {
            update (y - r1, y, y + r1)
        }
        while (y < h) {
            update (y - r1, y, h - 1)
        }
#undef update
    }

    delete[] rgb;
}

JNIEXPORT int Java_org_telegram_messenger_Utilities_needInvert(JNIEnv *env, jclass clazz, jobject bitmap, jint unpin, jint width, jint height, jint stride) {
    if (!bitmap) {
        return 0;
    }

    if (!width || !height || !stride || stride != width * 4 || width * height > 150 * 150) {
        return 0;
    }

    void *pixels = nullptr;
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) < 0) {
        return 0;
    }
    if (pixels == nullptr) {
        return 0;
    }
    uint8_t *pix = (uint8_t *) pixels;

    int32_t hasAlpha = 0;
    float matching = 0;
    float total = 0;
    for (int32_t y = 0; y < height; y++) {
        for (int32_t x = 0; x < width; x++) {
            int32_t index = y * stride + x * 4;
            uint8_t a = pix[index + 3];
            float alpha = a / 255.0f;

            uint8_t r = (uint8_t)(pix[index] * alpha);
            uint8_t g = (uint8_t)(pix[index + 1] * alpha);
            uint8_t b = (uint8_t)(pix[index + 2] * alpha);

            uint8_t cmax = (r > g) ? r : g;
            if (b > cmax) {
                cmax = b;
            }
            uint8_t cmin = (r < g) ? r : g;
            if (b < cmin) {
                cmin = b;
            }

            float saturation;
            float brightness = ((float) cmax) / 255.0f;
            if (cmax != 0) {
                saturation = ((float) (cmax - cmin)) / ((float) cmax);
            } else {
                saturation = 0;
            }

            if (alpha < 1.0) {
                hasAlpha = 1;
            }

            if (alpha > 0.0) {
                total += 1;
                if (saturation < 0.1f && brightness < 0.25f) {
                    matching += 1;
                }
            }
        }
    }
    if (unpin) {
        AndroidBitmap_unlockPixels(env, bitmap);
    }
    return hasAlpha && matching / total > 0.85;
}

JNIEXPORT void Java_org_telegram_messenger_Utilities_blurBitmap(JNIEnv *env, jclass clazz, jobject bitmap, jint radius, jint unpin, jint width, jint height, jint stride) {
    if (!bitmap) {
        return;
    }

    if (!width || !height || !stride) {
        return;
    }

    void *pixels = nullptr;
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) < 0) {
        return;
    }
    if (stride == width * 2) {
        if (radius <= 3) {
            fastBlur565(width, height, stride, (uint8_t *) pixels, radius);
        } else {
            fastBlurMore565(width, height, stride, (uint8_t *) pixels, radius);
        }
    } else {
        if (radius <= 3) {
            fastBlur(width, height, stride, (uint8_t *) pixels, radius);
        } else {
            fastBlurMore(width, height, stride, (uint8_t *) pixels, radius);
        }
    }
    if (unpin) {
        AndroidBitmap_unlockPixels(env, bitmap);
    }
}

const uint32_t PGPhotoEnhanceHistogramBins = 256;
const uint32_t PGPhotoEnhanceSegments = 4;

JNIEXPORT void Java_org_telegram_messenger_Utilities_calcCDT(JNIEnv *env, jclass clazz, jobject hsvBuffer, jint width, jint height, jobject buffer, jobject calcBuffer) {
    float imageWidth = width;
    float imageHeight = height;
    float _clipLimit = 1.25f;

    uint32_t totalSegments = PGPhotoEnhanceSegments * PGPhotoEnhanceSegments;
    uint32_t tileArea = (uint32_t) (floorf(imageWidth / PGPhotoEnhanceSegments) * floorf(imageHeight / PGPhotoEnhanceSegments));
    uint32_t clipLimit = (uint32_t) MAX(1, _clipLimit * tileArea / (float) PGPhotoEnhanceHistogramBins);
    float scale = 255.0f / (float) tileArea;

    unsigned char *bytes = (unsigned char *) env->GetDirectBufferAddress(hsvBuffer);
    uint32_t *calcBytes = (uint32_t *) env->GetDirectBufferAddress(calcBuffer);
    unsigned char *result = (unsigned char *) env->GetDirectBufferAddress(buffer);

    uint32_t *cdfsMin = calcBytes;
    calcBytes += totalSegments;
    uint32_t *cdfsMax = calcBytes;
    calcBytes += totalSegments;
    uint32_t *cdfs = calcBytes;
    calcBytes += totalSegments * PGPhotoEnhanceHistogramBins;
    uint32_t *hist = calcBytes;
    memset(hist, 0, sizeof(uint32_t) * totalSegments * PGPhotoEnhanceHistogramBins);

    float xMul = PGPhotoEnhanceSegments / imageWidth;
    float yMul = PGPhotoEnhanceSegments / imageHeight;

    uint32_t i, j;

    for (i = 0; i < imageHeight; i++) {
        uint32_t yOffset = i * width * 4;
        for (j = 0; j < imageWidth; j++) {
            uint32_t index = j * 4 + yOffset;

            uint32_t tx = (uint32_t)(j * xMul);
            uint32_t ty = (uint32_t)(i * yMul);
            uint32_t t = ty * PGPhotoEnhanceSegments + tx;

            hist[t * PGPhotoEnhanceHistogramBins + bytes[index + 2]]++;
        }
    }

    for (i = 0; i < totalSegments; i++) {
        if (clipLimit > 0) {
            uint32_t clipped = 0;
            for (j = 0; j < PGPhotoEnhanceHistogramBins; j++) {
                if (hist[i * PGPhotoEnhanceHistogramBins + j] > clipLimit) {
                    clipped += hist[i * PGPhotoEnhanceHistogramBins + j] - clipLimit;
                    hist[i * PGPhotoEnhanceHistogramBins + j] = clipLimit;
                }
            }

            uint32_t redistBatch = clipped / PGPhotoEnhanceHistogramBins;
            uint32_t residual = clipped - redistBatch * PGPhotoEnhanceHistogramBins;

            for (j = 0; j < PGPhotoEnhanceHistogramBins; j++) {
                hist[i * PGPhotoEnhanceHistogramBins + j] += redistBatch;
                if (j < residual) {
                    hist[i * PGPhotoEnhanceHistogramBins + j]++;
                }
            }
        }
        memcpy(cdfs + i * PGPhotoEnhanceHistogramBins, hist + i * PGPhotoEnhanceHistogramBins, PGPhotoEnhanceHistogramBins * sizeof(uint32_t));

        uint32_t hMin = PGPhotoEnhanceHistogramBins - 1;
        for (j = 0; j < hMin; ++j) {
            if (cdfs[i * PGPhotoEnhanceHistogramBins + j] != 0) {
                hMin = j;
            }
        }

        uint32_t cdf = 0;
        for (j = hMin; j < PGPhotoEnhanceHistogramBins; j++) {
            cdf += cdfs[i * PGPhotoEnhanceHistogramBins + j];
            cdfs[i * PGPhotoEnhanceHistogramBins + j] = (uint8_t) MIN(255, cdf * scale);
        }

        cdfsMin[i] = cdfs[i * PGPhotoEnhanceHistogramBins + hMin];
        cdfsMax[i] = cdfs[i * PGPhotoEnhanceHistogramBins + PGPhotoEnhanceHistogramBins - 1];
    }

    for (j = 0; j < totalSegments; j++) {
        uint32_t yOffset = j * PGPhotoEnhanceHistogramBins * 4;
        for (i = 0; i < PGPhotoEnhanceHistogramBins; i++) {
            uint32_t index = i * 4 + yOffset;
            result[index] = (uint8_t) cdfs[j * PGPhotoEnhanceHistogramBins + i];
            result[index + 1] = (uint8_t) cdfsMin[j];
            result[index + 2] = (uint8_t) cdfsMax[j];
            result[index + 3] = 255;
        }
    }
}

JNIEXPORT jint Java_org_telegram_messenger_Utilities_pinBitmap(JNIEnv *env, jclass clazz, jobject bitmap) {
    if (bitmap == nullptr) {
        return 0;
    }
    void *pixels;
    return AndroidBitmap_lockPixels(env, bitmap, &pixels) >= 0 ? 1 : 0;
}

JNIEXPORT void Java_org_telegram_messenger_Utilities_unpinBitmap(JNIEnv *env, jclass clazz, jobject bitmap) {
    if (bitmap == nullptr) {
        return;
    }
    AndroidBitmap_unlockPixels(env, bitmap);
}

#define SQUARE(i) ((i)*(i))

inline static void zeroClearInt(int *p, size_t count) {
    memset(p, 0, sizeof(int) * count);
}

JNIEXPORT void Java_org_telegram_messenger_Utilities_stackBlurBitmap(JNIEnv *env, jclass clazz, jobject bitmap, jint radius) {
    if (radius < 1) {
        return;
    }

    AndroidBitmapInfo info;
    if (AndroidBitmap_getInfo(env, bitmap, &info) != ANDROID_BITMAP_RESULT_SUCCESS) {
        return;
    }
    if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888) {
        return;
    }

    int w = info.width;
    int h = info.height;
    int stride = info.stride;

    unsigned char *pixels = nullptr;
    AndroidBitmap_lockPixels(env, bitmap, (void **) &pixels);
    if (!pixels) {
        return;
    }
    // Constants
    //const int radius = (int)inradius; // Transform unsigned into signed for further operations
    const int wm = w - 1;
    const int hm = h - 1;
    const int wh = w * h;
    const int div = radius + radius + 1;
    const int r1 = radius + 1;
    const int divsum = SQUARE((div + 1) >> 1);

    // Small buffers
    int stack[div * 4];
    zeroClearInt(stack, div * 4);

    int vmin[MAX(w, h)];
    zeroClearInt(vmin, MAX(w, h));

    // Large buffers
    int *r = new int[wh];
    int *g = new int[wh];
    int *b = new int[wh];
    int *a = new int[wh];
    zeroClearInt(r, wh);
    zeroClearInt(g, wh);
    zeroClearInt(b, wh);
    zeroClearInt(a, wh);

    const size_t dvcount = 256 * divsum;
    int *dv = new int[dvcount];
    int i;
    for (i = 0; (size_t) i < dvcount; i++) {
        dv[i] = (i / divsum);
    }

    // Variables
    int x, y;
    int *sir;
    int routsum, goutsum, boutsum, aoutsum;
    int rinsum, ginsum, binsum, ainsum;
    int rsum, gsum, bsum, asum, p, yp;
    int stackpointer;
    int stackstart;
    int rbs;

    int yw = 0, yi = 0;
    for (y = 0; y < h; y++) {
        ainsum = aoutsum = asum = rinsum = ginsum = binsum = routsum = goutsum = boutsum = rsum = gsum = bsum = 0;

        for (i = -radius; i <= radius; i++) {
            sir = &stack[(i + radius) * 4];
            int offset = (y * stride + (MIN(wm, MAX(i, 0))) * 4);
            sir[0] = pixels[offset];
            sir[1] = pixels[offset + 1];
            sir[2] = pixels[offset + 2];
            sir[3] = pixels[offset + 3];

            rbs = r1 - abs(i);
            rsum += sir[0] * rbs;
            gsum += sir[1] * rbs;
            bsum += sir[2] * rbs;
            asum += sir[3] * rbs;
            if (i > 0) {
                rinsum += sir[0];
                ginsum += sir[1];
                binsum += sir[2];
                ainsum += sir[3];
            } else {
                routsum += sir[0];
                goutsum += sir[1];
                boutsum += sir[2];
                aoutsum += sir[3];
            }
        }
        stackpointer = radius;

        for (x = 0; x < w; x++) {
            r[yi] = dv[rsum];
            g[yi] = dv[gsum];
            b[yi] = dv[bsum];
            a[yi] = dv[asum];

            rsum -= routsum;
            gsum -= goutsum;
            bsum -= boutsum;
            asum -= aoutsum;

            stackstart = stackpointer - radius + div;
            sir = &stack[(stackstart % div) * 4];

            routsum -= sir[0];
            goutsum -= sir[1];
            boutsum -= sir[2];
            aoutsum -= sir[3];

            if (y == 0) {
                vmin[x] = MIN(x + radius + 1, wm);
            }

            int offset = (y * stride + vmin[x] * 4);
            sir[0] = pixels[offset];
            sir[1] = pixels[offset + 1];
            sir[2] = pixels[offset + 2];
            sir[3] = pixels[offset + 3];
            rinsum += sir[0];
            ginsum += sir[1];
            binsum += sir[2];
            ainsum += sir[3];

            rsum += rinsum;
            gsum += ginsum;
            bsum += binsum;
            asum += ainsum;

            stackpointer = (stackpointer + 1) % div;
            sir = &stack[(stackpointer % div) * 4];

            routsum += sir[0];
            goutsum += sir[1];
            boutsum += sir[2];
            aoutsum += sir[3];

            rinsum -= sir[0];
            ginsum -= sir[1];
            binsum -= sir[2];
            ainsum -= sir[3];

            yi++;
        }
        yw += w;
    }

    for (x = 0; x < w; x++) {
        ainsum = aoutsum = asum = rinsum = ginsum = binsum = routsum = goutsum = boutsum = rsum = gsum = bsum = 0;
        yp = -radius * w;
        for (i = -radius; i <= radius; i++) {
            yi = MAX(0, yp) + x;

            sir = &stack[(i + radius) * 4];

            sir[0] = r[yi];
            sir[1] = g[yi];
            sir[2] = b[yi];
            sir[3] = a[yi];

            rbs = r1 - abs(i);

            rsum += r[yi] * rbs;
            gsum += g[yi] * rbs;
            bsum += b[yi] * rbs;
            asum += a[yi] * rbs;

            if (i > 0) {
                rinsum += sir[0];
                ginsum += sir[1];
                binsum += sir[2];
                ainsum += sir[3];
            } else {
                routsum += sir[0];
                goutsum += sir[1];
                boutsum += sir[2];
                aoutsum += sir[3];
            }

            if (i < hm) {
                yp += w;
            }
        }
        stackpointer = radius;
        for (y = 0; y < h; y++) {
            int offset = stride * y + x * 4;
            pixels[offset] = dv[rsum];
            pixels[offset + 1] = dv[gsum];
            pixels[offset + 2] = dv[bsum];
            pixels[offset + 3] = dv[asum];
            rsum -= routsum;
            gsum -= goutsum;
            bsum -= boutsum;
            asum -= aoutsum;

            stackstart = stackpointer - radius + div;
            sir = &stack[(stackstart % div) * 4];

            routsum -= sir[0];
            goutsum -= sir[1];
            boutsum -= sir[2];
            aoutsum -= sir[3];

            if (x == 0) {
                vmin[y] = (MIN(y + r1, hm)) * w;
            }
            p = x + vmin[y];

            sir[0] = r[p];
            sir[1] = g[p];
            sir[2] = b[p];
            sir[3] = a[p];

            rinsum += sir[0];
            ginsum += sir[1];
            binsum += sir[2];
            ainsum += sir[3];

            rsum += rinsum;
            gsum += ginsum;
            bsum += binsum;
            asum += ainsum;

            stackpointer = (stackpointer + 1) % div;
            sir = &stack[stackpointer * 4];

            routsum += sir[0];
            goutsum += sir[1];
            boutsum += sir[2];
            aoutsum += sir[3];

            rinsum -= sir[0];
            ginsum -= sir[1];
            binsum -= sir[2];
            ainsum -= sir[3];

            yi += w;
        }
    }

    delete[] r;
    delete[] g;
    delete[] b;
    delete[] a;
    delete[] dv;
    AndroidBitmap_unlockPixels(env, bitmap);
}

JNIEXPORT void Java_org_telegram_messenger_Utilities_drawDitheredGradient(JNIEnv *env, jclass clazz, jobject bitmap, jintArray colors, jint startX, jint startY, jint endX, jint endY) {
    AndroidBitmapInfo info;
    void *pixelsBuffer;
    int reason;

    if ((reason = AndroidBitmap_getInfo(env, bitmap, &info)) != ANDROID_BITMAP_RESULT_SUCCESS) {
        env->ThrowNew(jclass_RuntimeException, "AndroidBitmap_getInfo failed with a reason: " + reason);
        return;
    }

    if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888) {
        env->ThrowNew(jclass_RuntimeException, "Bitmap must be in ARGB_8888 format");
        return;
    }

    if ((reason = AndroidBitmap_lockPixels(env, bitmap, &pixelsBuffer)) != ANDROID_BITMAP_RESULT_SUCCESS) {
        env->ThrowNew(jclass_RuntimeException, "AndroidBitmap_lockPixels failed with a reason: " + reason);
        return;
    }

    uint8_t i, j, n;

    // gradient colors extracting
    jint *colorsBuffer = env->GetIntArrayElements(colors, 0);
    uint8_t *colorsComponents = (uint8_t *) colorsBuffer;
    float colorsF[4][2];
    for (i = 0; i < 4; i++) {
        // swap red and green channels
        n = (uint8_t) (i == 0 ? 2 : (i == 2 ? 0 : i));
        for (j = 0; j < 2; j++) {
            colorsF[n][j] = colorsComponents[j * 4 + i] / 255.F;
        }
    }
    env->ReleaseIntArrayElements(colors, colorsBuffer, JNI_ABORT);

    // gradient vector
    const int32_t vx = endX - startX;
    const int32_t vy = endY - startY;
    const float vSquaredMag = vx * vx + vy * vy;

    float noise, fraction, error, componentF;
    float *pixelsComponentsF = new float[info.height * info.stride * 4];
    memset(pixelsComponentsF, 0, info.height * info.stride * 4 * sizeof(float));
    uint8_t * bitmapPixelsComponents = (uint8_t * )
    pixelsBuffer;

    int32_t x, y;
    int32_t offset;
    int32_t position;
    for (y = 0; y < info.height; y++) {
        offset = y * info.stride;
        for (x = 0; x < info.width; x++) {
            // triangular probability density function dither noise
            noise = (rand() - rand()) / 255.F / RAND_MAX;

            // alpha channel
            bitmapPixelsComponents[offset + x * 4 + 3] = 255;

            for (i = 0; i < 3; i++) {
                position = offset + x * 4 + i;
                fraction = (vx * (x - startX) + vy * (y - startY)) / vSquaredMag;

                // gradient interpolation and noise
                pixelsComponentsF[position] += colorsF[i][0] + fraction * (colorsF[i][1] - colorsF[i][0]) + noise;

                // clamp
                if (pixelsComponentsF[position] > 1.F) {
                    pixelsComponentsF[position] = 1.F;
                } else if (pixelsComponentsF[position] < 0.F) {
                    pixelsComponentsF[position] = 0.F;
                }

                // draw
                componentF = roundf(pixelsComponentsF[position] * 255.F);
                bitmapPixelsComponents[position] = (uint8_t)
                componentF;

                // floyd-steinberg dithering
                error = pixelsComponentsF[position] - componentF / 255.F;
                if (x + 1 < info.width) {
                    pixelsComponentsF[position + 4] += error * 7.F / 16.F;
                    if (y + 1 < info.height) {
                        pixelsComponentsF[position + info.height + 4] += error * 1.F / 16.F;
                    }
                }
                if (y + 1 < info.height) {
                    pixelsComponentsF[position + info.height] += error * 5.F / 16.F;
                    if (x - 1 >= 0) {
                        pixelsComponentsF[position + info.height - 4] += error * 3.F / 16.F;
                    }
                }
            }
        }
    }

    delete[] pixelsComponentsF;

    if ((reason = AndroidBitmap_unlockPixels(env, bitmap)) != ANDROID_BITMAP_RESULT_SUCCESS) {
        env->ThrowNew(jclass_RuntimeException, "AndroidBitmap_unlockPixels failed with a reason: " + reason);
        return;
    }
}

//JNIEXPORT jint Java_org_telegram_messenger_Utilities_saveProgressiveJpeg(JNIEnv *env, jclass clazz, jobject bitmap, jint width, jint height, jint stride, jint quality, jstring path) {
//    if (!bitmap || !path || !width || !height || !stride || stride != width * 4) {
//        return 0;
//    }
//    void *pixels = 0;
//    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) < 0) {
//        return 0;
//    }
//    if (pixels == NULL) {
//        return 0;
//    }
//    tjhandle handle = 0;
//    if ((handle = tjInitCompress()) == NULL) {
//        return 0;
//    }
//    const char *pathStr = env->GetStringUTFChars(path, 0);
//    std::string filePath = std::string(pathStr);
//    if (pathStr != 0) {
//        env->ReleaseStringUTFChars(path, pathStr);
//    }
//
//    const char *enabledValue = "1";
//    const char *disabledValue = "0";
//    setenv("TJ_OPTIMIZE", enabledValue, 1);
//    setenv("TJ_ARITHMETIC", disabledValue, 1);
//    setenv("TJ_PROGRESSIVE", enabledValue, 1);
//    setenv("TJ_REVERT", enabledValue, 1);
//
//    TJSAMP jpegSubsamp = TJSAMP::TJSAMP_420;
//    jint buffSize = (jint) tjBufSize(width, height, jpegSubsamp);
//    unsigned char *jpegBuf = new unsigned char[buffSize];
//    unsigned char *srcBuf = (unsigned char *) pixels;
//
//    int pf = org_libjpegturbo_turbojpeg_TJ_PF_RGBA;
//
//    jsize actualPitch = width * tjPixelSize[pf];
//    jsize arraySize = (height - 1) * actualPitch + (width) * tjPixelSize[pf];
//    unsigned long jpegSize = tjBufSize(width, height, jpegSubsamp);
//
//    if (tjCompress2(handle, srcBuf, width, stride, height, pf, &jpegBuf, &jpegSize, jpegSubsamp, quality, TJFLAG_ACCURATEDCT | TJFLAG_PROGRESSIVE | TJFLAG_NOREALLOC) == 0) {
//        FILE *f = fopen(filePath.c_str(), "wb");
//        if (f && fwrite(jpegBuf, sizeof(unsigned char), jpegSize, f) == jpegSize) {
//            fflush(f);
//            fsync(fileno(f));
//        } else {
//            jpegSize = -1;
//        }
//        fclose(f);
//    } else {
//        jpegSize = -1;
//    }
//    delete[] jpegBuf;
//    tjDestroy(handle);
//    AndroidBitmap_unlockPixels(env, bitmap);
//    return jpegSize;
//
//    /*struct jpeg_compress_struct cinfo;
//    struct jpeg_error_mgr jerr;
//    cinfo.err = jpeg_std_error(&jerr);
//    jpeg_create_compress(&cinfo);
//
//    const char *pathStr = env->GetStringUTFChars(path, 0);
//    std::string filePath = std::string(pathStr);
//    if (pathStr != 0) {
//        env->ReleaseStringUTFChars(path, pathStr);
//    }
//
//    uint8_t *outBuffer = NULL;
//    unsigned long outSize = 0;
//    jpeg_mem_dest(&cinfo, &outBuffer, &outSize);
//    unsigned char *srcBuf = (unsigned char *) pixels;
//
//    cinfo.image_width = (uint32_t) width;
//    cinfo.image_height = (uint32_t) height;
//    cinfo.input_components = 4;
//    cinfo.in_color_space = JCS_EXT_RGBA;
//    jpeg_c_set_int_param(&cinfo, JINT_COMPRESS_PROFILE, JCP_FASTEST);
//    jpeg_set_defaults(&cinfo);
//    cinfo.arith_code = FALSE;
//    cinfo.dct_method = JDCT_ISLOW;
//    cinfo.optimize_coding = TRUE;
//    jpeg_set_quality(&cinfo, 78, 1);
//    jpeg_simple_progression(&cinfo);
//    jpeg_start_compress(&cinfo, 1);
//
//    JSAMPROW rowPointer[1];
//    while (cinfo.next_scanline < cinfo.image_height) {
//        rowPointer[0] = (JSAMPROW) (srcBuf + cinfo.next_scanline * stride);
//        jpeg_write_scanlines(&cinfo, rowPointer, 1);
//    }
//
//    jpeg_finish_compress(&cinfo);
//
//    FILE *f = fopen(filePath.c_str(), "wb");
//    if (f && fwrite(outBuffer, sizeof(uint8_t), outSize, f) == outSize) {
//        fflush(f);
//        fsync(fileno(f));
//    }
//    fclose(f);
//
//    jpeg_destroy_compress(&cinfo);
//    return outSize;*/
//}

std::vector<std::pair<float, float>> gatherPositions(std::vector<std::pair<float, float>> list, int phase) {
    std::vector<std::pair<float, float>> result(4);
    for (int i = 0; i < 4; i++) {
        int pos = phase + i * 2;
        while (pos >= 8) {
            pos -= 8;
        }
        result[i] = list[pos];
        result[i].second = 1.0f - result[i].second;
    }
    return result;
}

thread_local static float *pixelCache = nullptr;
thread_local static int pixelCacheSize = 0;

JNIEXPORT void Java_org_telegram_messenger_Utilities_generateGradient(JNIEnv *env, jclass clazz, jobject bitmap, jboolean unpin, jint phase, jfloat progress, jint width, jint height, jint stride, jintArray colors) {
    if (!bitmap) {
        return;
    }

    if (!width || !height) {
        return;
    }

    uint8_t *pixels = nullptr;
    if (AndroidBitmap_lockPixels(env, bitmap, (void **) &pixels) < 0) {
        return;
    }

    std::vector<std::pair<float, float>> positions{
            {0.80f, 0.10f},
            {0.60f, 0.20f},
            {0.35f, 0.25f},
            {0.25f, 0.60f},
            {0.20f, 0.90f},
            {0.40f, 0.80f},
            {0.65f, 0.75f},
            {0.75f, 0.40f}
    };

    int32_t previousPhase = phase + 1;
    if (previousPhase > 7) {
        previousPhase = 0;
    }
    std::vector<std::pair<float, float>> previous = gatherPositions(positions, previousPhase);
    std::vector<std::pair<float, float>> current = gatherPositions(positions, phase);

    auto colorsArray = (uint8_t *) env->GetIntArrayElements(colors, nullptr);
    float *newPixelCache = nullptr;

    if (width * height != pixelCacheSize && pixelCache != nullptr) {
        delete[] pixelCache;
        pixelCache = nullptr;
    }
    pixelCacheSize = width * height;

    if (pixelCache == nullptr) {
        newPixelCache = new float[width * height * 2];
    }
    float directPixelY;
    float centerDistanceY;
    float centerDistanceY2;
    int32_t colorsCount = colorsArray[12] == 0 && colorsArray[13] == 0 && colorsArray[14] == 0 && colorsArray[15] == 0 ? 3 : 4;

    for (int y = 0; y < height; y++) {
        if (pixelCache == nullptr) {
            directPixelY = (float) y / (float) height;
            centerDistanceY = directPixelY - 0.5f;
            centerDistanceY2 = centerDistanceY * centerDistanceY;
        }
        uint32_t offset = y * stride;
        for (int x = 0; x < width; x++) {
            float pixelX;
            float pixelY;
            if (pixelCache != nullptr) {
                pixelX = pixelCache[(y * width + x) * 2];
                pixelY = pixelCache[(y * width + x) * 2 + 1];
            } else {
                float directPixelX = (float) x / (float) width;

                float centerDistanceX = directPixelX - 0.5f;
                float centerDistance = sqrtf(centerDistanceX * centerDistanceX + centerDistanceY2);

                float swirlFactor = 0.35f * centerDistance;
                float theta = swirlFactor * swirlFactor * 0.8f * 8.0f;
                float sinTheta = sinf(theta);
                float cosTheta = cosf(theta);

                pixelX = newPixelCache[(y * width + x) * 2] = std::max(0.0f, std::min(1.0f, 0.5f + centerDistanceX * cosTheta - centerDistanceY * sinTheta));
                pixelY = newPixelCache[(y * width + x) * 2 + 1] = std::max(0.0f, std::min(1.0f, 0.5f + centerDistanceX * sinTheta + centerDistanceY * cosTheta));
            }

            float distanceSum = 0.0f;

            float r = 0.0f;
            float g = 0.0f;
            float b = 0.0f;

            for (int i = 0; i < colorsCount; i++) {
                float colorX = previous[i].first + (current[i].first - previous[i].first) * progress;
                float colorY = previous[i].second + (current[i].second - previous[i].second) * progress;

                float distanceX = pixelX - colorX;
                float distanceY = pixelY - colorY;

                float distance = std::max(0.0f, 0.9f - sqrtf(distanceX * distanceX + distanceY * distanceY));
                distance = distance * distance * distance * distance;
                distanceSum += distance;

                r = r + distance * ((float) colorsArray[i * 4] / 255.0f);
                g = g + distance * ((float) colorsArray[i * 4 + 1] / 255.0f);
                b = b + distance * ((float) colorsArray[i * 4 + 2] / 255.0f);
            }

            pixels[offset + x * 4] = (uint8_t) (b / distanceSum * 255.0f);
            pixels[offset + x * 4 + 1] = (uint8_t) (g / distanceSum * 255.0f);
            pixels[offset + x * 4 + 2] = (uint8_t) (r / distanceSum * 255.0f);
            pixels[offset + x * 4 + 3] = 0xff;
        }
    }
    if (newPixelCache != nullptr) {
        delete [] pixelCache;
        pixelCache = newPixelCache;
    }

    env->ReleaseIntArrayElements(colors, (jint *) colorsArray, JNI_ABORT);

    if (unpin) {
        AndroidBitmap_unlockPixels(env, bitmap);
    }
}

static inline uint32_t bitmapBytesPerPixel(int32_t format) {
    switch (format) {
        case ANDROID_BITMAP_FORMAT_A_8:
            return 1;

        case ANDROID_BITMAP_FORMAT_RGB_565:
        case ANDROID_BITMAP_FORMAT_RGBA_4444: // deprecated since API 13
            return 2;

        case ANDROID_BITMAP_FORMAT_RGBA_8888:
            return 4;

        case ANDROID_BITMAP_FORMAT_RGBA_F16:
            return 8;

        case ANDROID_BITMAP_FORMAT_RGBA_1010102:
            return 4;

        default:
            return 0;
    }
}

/**
 * Copies pixel data from src to dst.
 *
 * Both bitmaps must have identical dimensions and pixel format.
 * Hardware-backed bitmaps are not supported.
 * Copying a bitmap to itself is a no-op and returns JNI_TRUE.
 *
 * @param src  Source bitmap.
 * @param dst  Destination bitmap.
 * @return JNI_TRUE on success, JNI_FALSE if bitmaps are incompatible or an error occurred.
 */
JNIEXPORT jboolean JNICALL
Java_org_telegram_messenger_Utilities_copyBitmaps(
        JNIEnv *env,
        jclass /*clazz*/,
        jobject src,
        jobject dst) {

    if (__builtin_expect(src == nullptr || dst == nullptr, 0)) {
        return JNI_FALSE;
    }

    if (__builtin_expect(env->IsSameObject(src, dst), 0)) {
        return JNI_TRUE;
    }

    AndroidBitmapInfo srcInfo{};
    AndroidBitmapInfo dstInfo{};

    if (__builtin_expect(
            AndroidBitmap_getInfo(env, src, &srcInfo) != ANDROID_BITMAP_RESULT_SUCCESS ||
            AndroidBitmap_getInfo(env, dst, &dstInfo) != ANDROID_BITMAP_RESULT_SUCCESS,
            0)) {
        return JNI_FALSE;
    }

    if (__builtin_expect(
            (srcInfo.flags & ANDROID_BITMAP_FLAGS_IS_HARDWARE) != 0 ||
            (dstInfo.flags & ANDROID_BITMAP_FLAGS_IS_HARDWARE) != 0,
            0)) {
        return JNI_FALSE;
    }

    if (__builtin_expect(
            srcInfo.width != dstInfo.width ||
            srcInfo.height != dstInfo.height ||
            srcInfo.format != dstInfo.format ||
            srcInfo.width == 0 ||
            srcInfo.height == 0,
            0)) {
        return JNI_FALSE;
    }

    const uint32_t bytesPerPixel = bitmapBytesPerPixel(srcInfo.format);
    if (__builtin_expect(bytesPerPixel == 0, 0)) {
        return JNI_FALSE;
    }

    // size_t cast prevents width * bytesPerPixel overflow on 32-bit platforms
    const size_t rowBytes = static_cast<size_t>(srcInfo.width) * bytesPerPixel;

    if (__builtin_expect(
            static_cast<size_t>(srcInfo.stride) < rowBytes ||
            static_cast<size_t>(dstInfo.stride) < rowBytes,
            0)) {
        return JNI_FALSE;
    }

    void *srcPixels = nullptr;
    void *dstPixels = nullptr;

    if (__builtin_expect(
            AndroidBitmap_lockPixels(env, src, &srcPixels) != ANDROID_BITMAP_RESULT_SUCCESS,
            0)) {
        return JNI_FALSE;
    }

    if (__builtin_expect(
            AndroidBitmap_lockPixels(env, dst, &dstPixels) != ANDROID_BITMAP_RESULT_SUCCESS,
            0)) {
        AndroidBitmap_unlockPixels(env, src);
        return JNI_FALSE;
    }

    const bool contiguous =
            static_cast<size_t>(srcInfo.stride) == rowBytes &&
            static_cast<size_t>(dstInfo.stride) == rowBytes;

    if (contiguous) {
        // size_t cast prevents rowBytes * height overflow on 32-bit platforms
        std::memcpy(dstPixels, srcPixels, rowBytes * static_cast<size_t>(srcInfo.height));
    } else {
        auto *srcRow = static_cast<const uint8_t *>(srcPixels);
        auto *dstRow = static_cast<uint8_t *>(dstPixels);

        for (uint32_t y = 0; y < srcInfo.height; ++y) {
            std::memcpy(dstRow, srcRow, rowBytes);
            srcRow += static_cast<ptrdiff_t>(srcInfo.stride);
            dstRow += static_cast<ptrdiff_t>(dstInfo.stride);
        }
    }

    AndroidBitmap_unlockPixels(env, dst);
    AndroidBitmap_unlockPixels(env, src);
    return JNI_TRUE;
}

// ---------------------------------------------------------------------------
// Soft-Light blend — exact Android/Skia formula, simplified for α_dst = 1.
//
// General form (C values are pre-multiplied):
//   m   = C_dst / α_dst
//   g   = (16m² + 4m)*(m-1) + 7m    if 4*C_dst <= α_dst  (m <= 0.25)
//       = sqrt(m) - m                otherwise
//   f   = C_dst*(α_src + (2*C_src - α_src)*(1-m))    if 2*C_src <= α_src
//       = C_dst*α_src + α_dst*(2*C_src - α_src)*g     otherwise
//   α_out = α_src + α_dst - α_src*α_dst
//   C_out = C_src/α_dst + C_dst/α_src + f
//
// Simplified for α_dst = 1 (guaranteed by caller).
// Let cb = straight backdrop channel, cs = straight source channel,
//     a  = α_src (color alpha, in [0,1]):
//
//   m = cb
//   g = (16cb² + 4cb)*(cb-1) + 7cb    if cb <= 0.25
//     = sqrt(cb) - cb                  otherwise
//   f = cb*(a + (2*a*cs - a)*(1-cb))  if 2*a*cs <= a  →  cs <= 0.5
//     = cb*a + (2*a*cs - a)*g         otherwise
//
// result_straight = f/a  (recover straight channel from pre-multiplied f)
//
// Both LUTs are built on the first call and reused across all subsequent calls.
// ---------------------------------------------------------------------------

// g_sl_lut[cs_u8][cb_u8] -> soft-light result as uint8, for fully opaque color (a=1).
// 256 * 256 = 64 KB — fits in L2 cache on modern ARM cores.
static uint8_t g_sl_lut[256][256];

// g_lerp_lut[alpha_u8][value_u8] -> floor(alpha * value / 255)
// Used for branch-free integer lerp in the hot loop:
//   out = g_lerp_lut[alpha][blend] + g_lerp_lut[255 - alpha][cb]
// 256 * 256 = 64 KB.
static uint8_t g_lerp_lut[256][256];

static std::once_flag g_lut_flag;

static void build_luts() {
    // lerp LUT: floor(a * v / 255) — intentional floor, not round.
    // This guarantees lerpA[x] + lerpInvA[x] <= 255 for any x and any alpha,
    // preventing uint8_t overflow when the two terms are summed in process_alpha.
    //
    // Proof: floor(a*x/255) + floor((255-a)*x/255)
    //      <= a*x/255 + (255-a)*x/255 = x <= 255.
    for (int a = 0; a < 256; ++a) {
        for (int v = 0; v < 256; ++v) {
            g_lerp_lut[a][v] = static_cast<uint8_t>((a * v) / 255);
        }
    }

    // Soft-light LUT for fully opaque color (α_src = 1, i.e. a = 1).
    // With a = 1: C_src = cs, so 2*C_src <= α_src becomes cs <= 0.5.
    // f = cb*(1 + (2*cs - 1)*(1-cb))    if cs <= 0.5
    //   = cb + (2*cs - 1)*g             otherwise
    // result = f  (already straight since a = 1)
    for (int cs_i = 0; cs_i < 256; ++cs_i) {
        const float cs = cs_i / 255.0f;

        for (int cb_i = 0; cb_i < 256; ++cb_i) {
            const float cb = cb_i / 255.0f;
            float result;

            if (cs <= 0.5f) {
                // f = cb * (α_src + (2*C_src - α_src)*(1 - m))
                //   = cb * (1 + (2*cs - 1)*(1 - cb))
                result = cb * (1.0f + (2.0f * cs - 1.0f) * (1.0f - cb));
            } else {
                // g = (16m² + 4m)*(m-1) + 7m,  m = cb
                float g;
                if (cb <= 0.25f) {
                    g = (16.0f * cb * cb + 4.0f * cb) * (cb - 1.0f) + 7.0f * cb;
                } else {
                    g = sqrtf(cb) - cb;
                }
                // f = cb*α_src + α_dst*(2*C_src - α_src)*g
                //   = cb + (2*cs - 1)*g          (α_src = α_dst = 1)
                result = cb + (2.0f * cs - 1.0f) * g;
            }

            // Clamp for float rounding safety.
            if (result < 0.0f) result = 0.0f;
            if (result > 1.0f) result = 1.0f;

            g_sl_lut[cs_i][cb_i] = static_cast<uint8_t>(result * 255.0f + 0.5f);
        }
    }
}

// ---------------------------------------------------------------------------
// Three specialised hot loops, selected by colorA before entering the loop.
// Alpha branching is lifted OUT of the loop — no branches inside iterations.
// ---------------------------------------------------------------------------

// colorA == 0xFF: out[ch] = sl_lut[cs][cb]
static void process_opaque(
        const uint8_t * __restrict__ inPx,
        uint8_t * __restrict__ outPx,
        uint32_t width, uint32_t height,
        uint32_t inStride, uint32_t outStride,
        uint8_t csR, uint8_t csG, uint8_t csB)
{
    // LUT row pointers are fixed for a given color — load them once outside the loop.
    const uint8_t * __restrict__ slR = g_sl_lut[csR];
    const uint8_t * __restrict__ slG = g_sl_lut[csG];
    const uint8_t * __restrict__ slB = g_sl_lut[csB];

    for (uint32_t y = 0; y < height; ++y) {
        const uint8_t * __restrict__ src = inPx  + y * inStride;
        uint8_t * __restrict__ dst = outPx + y * outStride;
        const uint8_t * const end = src + width * 4u;

        while (src < end) {
            dst[0] = slR[src[0]];
            dst[1] = slG[src[1]];
            dst[2] = slB[src[2]];
            dst[3] = 0xFF;
            src += 4;
            dst += 4;
        }
    }
}

// colorA == 0x00: output is a copy of input with alpha forced to 0xFF.
// (Input bitmap is guaranteed opaque, so the copy is a straight pixel copy.)
static void process_transparent(
        const uint8_t * __restrict__ inPx,
        uint8_t * __restrict__ outPx,
        uint32_t width, uint32_t height,
        uint32_t inStride, uint32_t outStride)
{
    if (inStride == width * 4u && outStride == width * 4u) {
        memcpy(outPx, inPx, width * height * 4u);
    } else {
        for (uint32_t y = 0; y < height; ++y) {
            memcpy(outPx + y * outStride, inPx + y * inStride, width * 4u);
        }
    }
}

// 0 < colorA < 0xFF:
//   out[ch] = lerp_lut[colorA][sl[cs][cb]] + lerp_lut[255 - colorA][cb]
// No floats, no branches inside the loop.
static void process_alpha(
        const uint8_t * __restrict__ inPx,
        uint8_t * __restrict__ outPx,
        uint32_t width, uint32_t height,
        uint32_t inStride, uint32_t outStride,
        uint8_t csR, uint8_t csG, uint8_t csB, uint8_t colorA)
{
    const uint8_t invA = static_cast<uint8_t>(255 - colorA);

    const uint8_t * __restrict__ slR      = g_sl_lut[csR];
    const uint8_t * __restrict__ slG      = g_sl_lut[csG];
    const uint8_t * __restrict__ slB      = g_sl_lut[csB];
    const uint8_t * __restrict__ lerpA    = g_lerp_lut[colorA];
    const uint8_t * __restrict__ lerpInvA = g_lerp_lut[invA];

    for (uint32_t y = 0; y < height; ++y) {
        const uint8_t * __restrict__ src = inPx  + y * inStride;
        uint8_t * __restrict__ dst = outPx + y * outStride;
        const uint8_t * const end = src + width * 4u;

        while (src < end) {
            const uint8_t cbR = src[0];
            const uint8_t cbG = src[1];
            const uint8_t cbB = src[2];
            dst[0] = lerpA[slR[cbR]] + lerpInvA[cbR];
            dst[1] = lerpA[slG[cbG]] + lerpInvA[cbG];
            dst[2] = lerpA[slB[cbB]] + lerpInvA[cbB];
            dst[3] = 0xFF;
            src += 4;
            dst += 4;
        }
    }
}

// ---------------------------------------------------------------------------
// JNI entry point
//
// Kotlin: external fun applySoftLight(input: Bitmap, output: Bitmap, color: Int): Boolean  [org.telegram.messenger.Utilities]
//
// color  — Android packed ARGB (0xAARRGGBB), straight (non-premultiplied) alpha.
// Returns true on success, false on error (size mismatch or unsupported format).
// ---------------------------------------------------------------------------
JNIEXPORT jboolean JNICALL
Java_org_telegram_messenger_Utilities_applySoftLight(
        JNIEnv *env,
        jclass  /*clazz*/,
        jobject inputBitmap,
        jobject outputBitmap,
        jint    color)
{
    std::call_once(g_lut_flag, build_luts);

    if (__builtin_expect(env->IsSameObject(inputBitmap, outputBitmap), 0)) {
        return JNI_FALSE;
    }

    AndroidBitmapInfo inInfo{};
    AndroidBitmapInfo outInfo{};

    if (__builtin_expect(
            AndroidBitmap_getInfo(env, inputBitmap,  &inInfo)  != ANDROID_BITMAP_RESULT_SUCCESS ||
            AndroidBitmap_getInfo(env, outputBitmap, &outInfo) != ANDROID_BITMAP_RESULT_SUCCESS,
            0)) {
        return JNI_FALSE;
    }

    if (__builtin_expect(
            inInfo.width   != outInfo.width                   ||
            inInfo.height  != outInfo.height                  ||
            inInfo.format  != ANDROID_BITMAP_FORMAT_RGBA_8888 ||
            outInfo.format != ANDROID_BITMAP_FORMAT_RGBA_8888 ||
            inInfo.width   == 0                               ||
            inInfo.height  == 0                               ||
            inInfo.stride  < inInfo.width  * 4u               ||
            outInfo.stride < outInfo.width * 4u,
            0)) {
        return JNI_FALSE;
    }

    void *inPixels  = nullptr;
    void *outPixels = nullptr;

    if (__builtin_expect(
            AndroidBitmap_lockPixels(env, inputBitmap, &inPixels) != ANDROID_BITMAP_RESULT_SUCCESS,
            0)) {
        return JNI_FALSE;
    }
    if (__builtin_expect(
            AndroidBitmap_lockPixels(env, outputBitmap, &outPixels) != ANDROID_BITMAP_RESULT_SUCCESS,
            0)) {
        AndroidBitmap_unlockPixels(env, inputBitmap);
        return JNI_FALSE;
    }

    // Unpack Java color (0xAARRGGBB) into separate channels.
    const auto u         = static_cast<uint32_t>(color);
    const uint8_t colorA = static_cast<uint8_t>(u >> 24);
    const uint8_t colorR = static_cast<uint8_t>(u >> 16);
    const uint8_t colorG = static_cast<uint8_t>(u >>  8);
    const uint8_t colorB = static_cast<uint8_t>(u);

    const auto *src = static_cast<const uint8_t *>(inPixels);
    auto       *dst = static_cast<uint8_t *>(outPixels);
    const uint32_t w  = inInfo.width;
    const uint32_t h  = inInfo.height;
    const uint32_t si = inInfo.stride;
    const uint32_t so = outInfo.stride;

    // Dispatch before the loop so no alpha branching occurs inside it.
    if (colorA == 0xFF) {
        process_opaque(src, dst, w, h, si, so, colorR, colorG, colorB);
    } else if (colorA == 0x00) {
        process_transparent(src, dst, w, h, si, so);
    } else {
        process_alpha(src, dst, w, h, si, so, colorR, colorG, colorB, colorA);
    }

    AndroidBitmap_unlockPixels(env, outputBitmap);
    AndroidBitmap_unlockPixels(env, inputBitmap);
    return JNI_TRUE;
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_org_telegram_messenger_Utilities_nLibyuvARGBSaleBitmap(
        JNIEnv* env,
        jclass,
        jobject inputBitmap,
        jobject outputBitmap,
        jint filterMode
) {
    if (__builtin_expect(inputBitmap == nullptr || outputBitmap == nullptr, 0)) {
        return JNI_FALSE;
    }

    AndroidBitmapInfo inInfo{};
    AndroidBitmapInfo outInfo{};

    if (__builtin_expect(
            AndroidBitmap_getInfo(env, inputBitmap, &inInfo) != ANDROID_BITMAP_RESULT_SUCCESS ||
            AndroidBitmap_getInfo(env, outputBitmap, &outInfo) != ANDROID_BITMAP_RESULT_SUCCESS,
            0)) {
        return JNI_FALSE;
    }

    if (__builtin_expect(
            inInfo.width == 0 || inInfo.height == 0 ||
            outInfo.width == 0 || outInfo.height == 0,
            0)) {
        return JNI_FALSE;
    }

    if (__builtin_expect(
            inInfo.format != ANDROID_BITMAP_FORMAT_RGBA_8888 ||
            outInfo.format != ANDROID_BITMAP_FORMAT_RGBA_8888,
            0)) {
        return JNI_FALSE;
    }

    if (__builtin_expect(
            inInfo.width > INT_MAX / 4 ||
            outInfo.width > INT_MAX / 4 ||
            inInfo.height > INT_MAX ||
            outInfo.height > INT_MAX ||
            inInfo.stride > INT_MAX ||
            outInfo.stride > INT_MAX,
            0)) {
        return JNI_FALSE;
    }

    if (__builtin_expect(
            inInfo.stride < inInfo.width * 4 ||
            outInfo.stride < outInfo.width * 4,
            0)) {
        return JNI_FALSE;
    }

    libyuv::FilterMode mode;

    switch (filterMode) {
        case 0: mode = libyuv::kFilterNone; break;
        case 1: mode = libyuv::kFilterLinear; break;
        case 2: mode = libyuv::kFilterBilinear; break;
        case 3: mode = libyuv::kFilterBox; break;
        default: return JNI_FALSE;
    }

    void* inPixels = nullptr;
    void* outPixels = nullptr;

    if (__builtin_expect(
            AndroidBitmap_lockPixels(env, inputBitmap, &inPixels) != ANDROID_BITMAP_RESULT_SUCCESS ||
            inPixels == nullptr,
            0)) {
        return JNI_FALSE;
    }

    if (__builtin_expect(
            AndroidBitmap_lockPixels(env, outputBitmap, &outPixels) != ANDROID_BITMAP_RESULT_SUCCESS ||
            outPixels == nullptr,
            0)) {
        AndroidBitmap_unlockPixels(env, inputBitmap);
        return JNI_FALSE;
    }

    const int scaleResult = libyuv::ARGBScale(
        static_cast<const uint8_t*>(inPixels),
        static_cast<int>(inInfo.stride),
        static_cast<int>(inInfo.width),
        static_cast<int>(inInfo.height),
        static_cast<uint8_t*>(outPixels),
        static_cast<int>(outInfo.stride),
        static_cast<int>(outInfo.width),
        static_cast<int>(outInfo.height),
        mode
    );

    AndroidBitmap_unlockPixels(env, outputBitmap);
    AndroidBitmap_unlockPixels(env, inputBitmap);

    return __builtin_expect(scaleResult == 0, 1) ? JNI_TRUE : JNI_FALSE;
}


extern "C"
JNIEXPORT jint JNICALL
Java_org_telegram_messenger_Utilities_averageBitmapColor(
        JNIEnv* env,
        jclass,
        jobject bitmap,
        jint left,
        jint top,
        jint right,
        jint bottom
) {
    if (__builtin_expect(bitmap == nullptr, 0)) {
        return 0;
    }

    if (__builtin_expect(left < 0 || top < 0 || right <= left || bottom <= top, 0)) {
        return 0;
    }

    AndroidBitmapInfo info{};

    if (__builtin_expect(
            AndroidBitmap_getInfo(env, bitmap, &info) != ANDROID_BITMAP_RESULT_SUCCESS,
            0)) {
        return 0;
    }

    if (__builtin_expect(
            info.width == 0 || info.height == 0 ||
            info.format != ANDROID_BITMAP_FORMAT_RGBA_8888,
            0)) {
        return 0;
    }

    if (__builtin_expect(
            info.width > INT_MAX / 4 ||
            info.height > INT_MAX ||
            info.stride > INT_MAX,
            0)) {
        return 0;
    }

    if (__builtin_expect(info.stride < info.width * 4, 0)) {
        return 0;
    }

    if (__builtin_expect(
            right > static_cast<jint>(info.width) ||
            bottom > static_cast<jint>(info.height),
            0)) {
        return 0;
    }

    void* pixels = nullptr;

    if (__builtin_expect(
            AndroidBitmap_lockPixels(env, bitmap, &pixels) != ANDROID_BITMAP_RESULT_SUCCESS ||
            pixels == nullptr,
            0)) {
        return 0;
    }

    uint64_t sumR = 0;
    uint64_t sumG = 0;
    uint64_t sumB = 0;
    uint64_t sumA = 0;

    const uint8_t* base = static_cast<const uint8_t*>(pixels);
    const int stride = static_cast<int>(info.stride);

    for (int y = top; y < bottom; y++) {
        const uint8_t* row = base + static_cast<size_t>(y) * stride + static_cast<size_t>(left) * 4;

        for (int x = left; x < right; x++) {
            sumR += row[0];
            sumG += row[1];
            sumB += row[2];
            sumA += row[3];
            row += 4;
        }
    }

    AndroidBitmap_unlockPixels(env, bitmap);

    const uint64_t count = static_cast<uint64_t>(right - left) * static_cast<uint64_t>(bottom - top);

    const uint32_t avgR = static_cast<uint32_t>(sumR / count);
    const uint32_t avgG = static_cast<uint32_t>(sumG / count);
    const uint32_t avgB = static_cast<uint32_t>(sumB / count);
    const uint32_t avgA = static_cast<uint32_t>(sumA / count);

    return static_cast<jint>(
            (avgA << 24) |
            (avgR << 16) |
            (avgG << 8) |
            avgB
    );
}

}