#include <jni.h>
#include <cstdio>
#include <csetjmp>
#include <cstdlib>
#include <cstring>
#include <cmath>
#include <unistd.h>
#include <android/bitmap.h>
#include <string>
#include <mozjpeg/java/org_libjpegturbo_turbojpeg_TJ.h>
#include <mozjpeg/jpeglib.h>
#include <tgnet/FileLog.h>
#include <vector>
#include <algorithm>
#include "webp/decode.h"
#include "webp/encode.h"
#include "mozjpeg/turbojpeg.h"
#include "c_utils.h"

extern "C" {
jclass jclass_NullPointerException;
jclass jclass_RuntimeException;

jclass jclass_Options;
jfieldID jclass_Options_inJustDecodeBounds;
jfieldID jclass_Options_outHeight;
jfieldID jclass_Options_outWidth;

jint imageOnJNILoad(JavaVM *vm, JNIEnv *env) {
    jclass_NullPointerException = (jclass) env->NewGlobalRef(env->FindClass("java/lang/NullPointerException"));
    if (jclass_NullPointerException == 0) {
        return JNI_FALSE;
    }
    jclass_RuntimeException = (jclass) env->NewGlobalRef(env->FindClass("java/lang/RuntimeException"));
    if (jclass_RuntimeException == 0) {
        return JNI_FALSE;
    }
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

JNIEXPORT jboolean Java_org_telegram_messenger_Utilities_loadWebpImage(JNIEnv *env, jclass clazz, jobject outputBitmap, jobject buffer, jint len, jobject options, jboolean unpin) {
    if (!buffer) {
        env->ThrowNew(jclass_NullPointerException, "Input buffer can not be null");
        return 0;
    }

    jbyte *inputBuffer = (jbyte *) env->GetDirectBufferAddress(buffer);

    int32_t bitmapWidth = 0;
    int32_t bitmapHeight = 0;
    if (!WebPGetInfo((uint8_t *)inputBuffer, len, &bitmapWidth, &bitmapHeight)) {
        env->ThrowNew(jclass_RuntimeException, "Invalid WebP format");
        return 0;
    }

    if (options && env->GetBooleanField(options, jclass_Options_inJustDecodeBounds) == JNI_TRUE) {
        env->SetIntField(options, jclass_Options_outWidth, bitmapWidth);
        env->SetIntField(options, jclass_Options_outHeight, bitmapHeight);
        return 1;
    }

    if (!outputBitmap) {
        env->ThrowNew(jclass_NullPointerException, "output bitmap can not be null");
        return 0;
    }

    AndroidBitmapInfo bitmapInfo;
    if (AndroidBitmap_getInfo(env, outputBitmap, &bitmapInfo) != ANDROID_BITMAP_RESUT_SUCCESS) {
        env->ThrowNew(jclass_RuntimeException, "Failed to get Bitmap information");
        return 0;
    }

    void *bitmapPixels = nullptr;
    if (AndroidBitmap_lockPixels(env, outputBitmap, &bitmapPixels) != ANDROID_BITMAP_RESUT_SUCCESS) {
        env->ThrowNew(jclass_RuntimeException, "Failed to lock Bitmap pixels");
        return 0;
    }

    if (!WebPDecodeRGBAInto((uint8_t *) inputBuffer, len, (uint8_t *) bitmapPixels, bitmapInfo.height * bitmapInfo.stride, bitmapInfo.stride)) {
        AndroidBitmap_unlockPixels(env, outputBitmap);
        env->ThrowNew(jclass_RuntimeException, "Failed to decode webp image");
        return 0;
    }

    if (unpin && AndroidBitmap_unlockPixels(env, outputBitmap) != ANDROID_BITMAP_RESUT_SUCCESS) {
        env->ThrowNew(jclass_RuntimeException, "Failed to unlock Bitmap pixels");
        return 0;
    }

    return 1;
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

JNIEXPORT jint Java_org_telegram_messenger_Utilities_saveProgressiveJpeg(JNIEnv *env, jclass clazz, jobject bitmap, jint width, jint height, jint stride, jint quality, jstring path) {
    if (!bitmap || !path || !width || !height || !stride || stride != width * 4) {
        return 0;
    }
    void *pixels = 0;
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) < 0) {
        return 0;
    }
    if (pixels == NULL) {
        return 0;
    }
    tjhandle handle = 0;
    if ((handle = tjInitCompress()) == NULL) {
        return 0;
    }
    const char *pathStr = env->GetStringUTFChars(path, 0);
    std::string filePath = std::string(pathStr);
    if (pathStr != 0) {
        env->ReleaseStringUTFChars(path, pathStr);
    }

    const char *enabledValue = "1";
    const char *disabledValue = "0";
    setenv("TJ_OPTIMIZE", enabledValue, 1);
    setenv("TJ_ARITHMETIC", disabledValue, 1);
    setenv("TJ_PROGRESSIVE", enabledValue, 1);
    setenv("TJ_REVERT", enabledValue, 1);

    TJSAMP jpegSubsamp = TJSAMP::TJSAMP_420;
    jint buffSize = (jint) tjBufSize(width, height, jpegSubsamp);
    unsigned char *jpegBuf = new unsigned char[buffSize];
    unsigned char *srcBuf = (unsigned char *) pixels;

    int pf = org_libjpegturbo_turbojpeg_TJ_PF_RGBA;

    jsize actualPitch = width * tjPixelSize[pf];
    jsize arraySize = (height - 1) * actualPitch + (width) * tjPixelSize[pf];
    unsigned long jpegSize = tjBufSize(width, height, jpegSubsamp);

    if (tjCompress2(handle, srcBuf, width, stride, height, pf, &jpegBuf, &jpegSize, jpegSubsamp, quality, TJFLAG_ACCURATEDCT | TJFLAG_PROGRESSIVE | TJFLAG_NOREALLOC) == 0) {
        FILE *f = fopen(filePath.c_str(), "wb");
        if (f && fwrite(jpegBuf, sizeof(unsigned char), jpegSize, f) == jpegSize) {
            fflush(f);
            fsync(fileno(f));
        } else {
            jpegSize = -1;
        }
        fclose(f);
    } else {
        jpegSize = -1;
    }
    delete[] jpegBuf;
    tjDestroy(handle);
    AndroidBitmap_unlockPixels(env, bitmap);
    return jpegSize;

    /*struct jpeg_compress_struct cinfo;
    struct jpeg_error_mgr jerr;
    cinfo.err = jpeg_std_error(&jerr);
    jpeg_create_compress(&cinfo);

    const char *pathStr = env->GetStringUTFChars(path, 0);
    std::string filePath = std::string(pathStr);
    if (pathStr != 0) {
        env->ReleaseStringUTFChars(path, pathStr);
    }

    uint8_t *outBuffer = NULL;
    unsigned long outSize = 0;
    jpeg_mem_dest(&cinfo, &outBuffer, &outSize);
    unsigned char *srcBuf = (unsigned char *) pixels;

    cinfo.image_width = (uint32_t) width;
    cinfo.image_height = (uint32_t) height;
    cinfo.input_components = 4;
    cinfo.in_color_space = JCS_EXT_RGBA;
    jpeg_c_set_int_param(&cinfo, JINT_COMPRESS_PROFILE, JCP_FASTEST);
    jpeg_set_defaults(&cinfo);
    cinfo.arith_code = FALSE;
    cinfo.dct_method = JDCT_ISLOW;
    cinfo.optimize_coding = TRUE;
    jpeg_set_quality(&cinfo, 78, 1);
    jpeg_simple_progression(&cinfo);
    jpeg_start_compress(&cinfo, 1);

    JSAMPROW rowPointer[1];
    while (cinfo.next_scanline < cinfo.image_height) {
        rowPointer[0] = (JSAMPROW) (srcBuf + cinfo.next_scanline * stride);
        jpeg_write_scanlines(&cinfo, rowPointer, 1);
    }

    jpeg_finish_compress(&cinfo);

    FILE *f = fopen(filePath.c_str(), "wb");
    if (f && fwrite(outBuffer, sizeof(uint8_t), outSize, f) == outSize) {
        fflush(f);
        fsync(fileno(f));
    }
    fclose(f);

    jpeg_destroy_compress(&cinfo);
    return outSize;*/
}

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

}