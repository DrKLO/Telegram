//
// Created by opiumfive on 03.07.2025.
// IIR blur with constant time, not depending to radius
// it can have sharpen mode too

#include <android/bitmap.h>
#include <jni.h>
#include <math.h>
#include <stdlib.h>
#include <string.h>
#include <thread>
#include <vector>
#include <atomic>
#include <condition_variable>
#include <functional>

/* --------------------------------------------------------------------- */
/* A tiny thread pool                                                    */
/* --------------------------------------------------------------------- */
struct ThreadPool {
    explicit ThreadPool(unsigned n)
            : done(false)
    {
        for (unsigned i = 0; i < n; ++i)
            workers.emplace_back([this]{ worker(); });
    }
    ~ThreadPool() {
        {
            std::lock_guard<std::mutex> lk(mx);
            done = true;
            cv.notify_all();
        }
        for (auto &t: workers) t.join();
    }
    template<typename F>
    void enqueue(F &&f) {
        {
            std::lock_guard<std::mutex> lk(mx);
            queue.emplace_back(std::forward<F>(f));
        }
        cv.notify_one();
    }
    void wait_empty() {
        std::unique_lock<std::mutex> lk(mx);
        cv_done.wait(lk, [this]{ return queue.empty() && busy == 0; });
    }
private:
    void worker() {
        while (true) {
            std::function<void()> job;
            {
                std::unique_lock<std::mutex> lk(mx);
                cv.wait(lk, [this]{ return done || !queue.empty(); });
                if (done && queue.empty()) return;
                job = std::move(queue.back());
                queue.pop_back();
                ++busy;
            }
            job();
            {
                std::lock_guard<std::mutex> lk(mx);
                --busy;
                if (queue.empty() && busy == 0) cv_done.notify_one();
            }
        }
    }

    std::vector<std::thread>          workers;
    std::vector<std::function<void()>> queue;
    std::mutex                        mx;
    std::condition_variable           cv, cv_done;
    std::atomic<int>                  busy{0};
    bool                              done;
};

static ThreadPool& pool() {
    static ThreadPool p(std::max(2u, std::thread::hardware_concurrency()));
    return p;
}

/* --------------------------------------------------------------------- */
/*  Direct-RGBA separable IIR Gaussian blur                               */
/* --------------------------------------------------------------------- */
static void iir_gauss_blur_u8_rgba_parallel(
        unsigned char* image,   /* RGBA, alpha untouched                       */
        float*         ext_buf, /* 3 × W × H scratch (nullptr → allocate)       */
        unsigned       width,
        unsigned       height,
        float          sigma,
        float          amount)
{
    /* ---- maths helpers ------------------------------------------------ */
#define BLUR(dst, src, ch)  { dst = val##ch = B * src + (b0 * prev0##ch + b1 * prev1##ch + b2 * prev2##ch); \
                              prev2##ch = prev1##ch; prev1##ch = prev0##ch; prev0##ch = val##ch; }

#define BLUR_FINAL(dst, src, ch) { val##ch = B * src + (b0 * prev0##ch + b1 * prev1##ch + b2 * prev2##ch); \
                                   prev2##ch = prev1##ch; prev1##ch = prev0##ch; prev0##ch = val##ch; \
                                   dst = val##ch + 0.5f; }

#define SHARP(dst, src, ch) { val##ch = B * src + (b0 * prev0##ch + b1 * prev1##ch + b2 * prev2##ch); \
                              prev2##ch = prev1##ch; prev1##ch = prev0##ch; prev0##ch = val##ch; \
                              float sharpened##ch = (float)dst + ((float)dst - val##ch) * amount + 0.5f; \
                              dst = sharpened##ch < 0.0f ? 0 : (sharpened##ch > 255.0f ? 255 : sharpened##ch); }

    /* ---- coefficients -------------------------------------------------- */
    float q = sigma >= 2.5f
              ? 0.98711f * sigma - 0.96330f
              : 3.97156f - 4.14554f * sqrtf(1.0f - 0.26891f * sigma);

    float d  = 1.57825f + 2.44413f * q + 1.4281f * q * q + 0.422205f * q * q * q;
    float b0 = (2.44413f * q + 2.85619f * q * q + 1.26661f * q * q * q) / d;
    float b1 = -(1.4281f  * q * q + 1.26661f * q * q * q) / d;
    float b2 =  (0.422205f * q * q * q) / d;
    float B  = 1.0f - (b0 + b1 + b2);

    /* ---- dimensions ---------------------------------------------------- */
    const unsigned imgStride  = width * 4;   /* bytes per row in RGBA      */
    const unsigned bufStride  = width * 3;   /* floats per row in scratch  */

    bool   own_buf = (ext_buf == nullptr);
    float* buffer  = own_buf
                     ? static_cast<float*>(malloc(sizeof(float) * bufStride * height))
                     : ext_buf;
    if (!buffer) return;

    /* small image → single-thread path to avoid overhead */
    const size_t threshold = 256 * 256;
    if (static_cast<size_t>(width) * height <= threshold) {
        /* ---- horizontal pass (1 thread) ------------------------------- */
        for (unsigned y = 0; y < height; ++y) {
            unsigned char* ptr_img = image  + y * imgStride;
            float*         ptr_buf = buffer + y * bufStride;

            float prev00 = ptr_img[0], prev10 = ptr_img[0], prev20 = ptr_img[0];
            float prev01 = ptr_img[1], prev11 = ptr_img[1], prev21 = ptr_img[1];
            float prev02 = ptr_img[2], prev12 = ptr_img[2], prev22 = ptr_img[2];
            float val0{}, val1{}, val2{};

            for (unsigned x = 0; x < width; ++x, ptr_buf += 3, ptr_img += 4) {
                BLUR(ptr_buf[0], ptr_img[0], 0);
                BLUR(ptr_buf[1], ptr_img[1], 1);
                BLUR(ptr_buf[2], ptr_img[2], 2);
            }
            ptr_buf -= 3;
            ptr_img -= 4;
            prev00 = prev10 = prev20 = val0;
            prev01 = prev11 = prev21 = val1;
            prev02 = prev12 = prev22 = val2;

            for (unsigned x = 0; x < width; ++x, ptr_buf -= 3, ptr_img -= 4) {
                BLUR(ptr_buf[0], ptr_buf[0], 0);
                BLUR(ptr_buf[1], ptr_buf[1], 1);
                BLUR(ptr_buf[2], ptr_buf[2], 2);
            }
        }

        /* ---- vertical pass (1 thread) --------------------------------- */
        for (unsigned xb = 0; xb < bufStride; xb += 3) {
            float*         ptr_buf = buffer + xb + bufStride * (height - 1);
            unsigned       xPixel  = xb / 3;
            unsigned char* ptr_img = image  + xPixel * 4;

            float prev00 = ptr_buf[0], prev10 = ptr_buf[0], prev20 = ptr_buf[0];
            float prev01 = ptr_buf[1], prev11 = ptr_buf[1], prev21 = ptr_buf[1];
            float prev02 = ptr_buf[2], prev12 = ptr_buf[2], prev22 = ptr_buf[2];
            float val0{}, val1{}, val2{};

            for (unsigned y = 0; y < height; ++y, ptr_buf -= bufStride) {
                BLUR(ptr_buf[0], ptr_buf[0], 0);
                BLUR(ptr_buf[1], ptr_buf[1], 1);
                BLUR(ptr_buf[2], ptr_buf[2], 2);
            }
            ptr_buf += bufStride;
            prev00 = prev10 = prev20 = val0;
            prev01 = prev11 = prev21 = val1;
            prev02 = prev12 = prev22 = val2;

            if (amount > 0.0f) {
                for (unsigned y = 0; y < height; ++y,
                        ptr_buf += bufStride,
                        ptr_img += imgStride) {
                    SHARP(ptr_img[0], ptr_buf[0], 0);
                    SHARP(ptr_img[1], ptr_buf[1], 1);
                    SHARP(ptr_img[2], ptr_buf[2], 2);
                }
            } else {
                for (unsigned y = 0; y < height; ++y,
                        ptr_buf += bufStride,
                        ptr_img += imgStride) {
                    BLUR_FINAL(ptr_img[0], ptr_buf[0], 0);
                    BLUR_FINAL(ptr_img[1], ptr_buf[1], 1);
                    BLUR_FINAL(ptr_img[2], ptr_buf[2], 2);
                }
            }
        }

        if (own_buf) free(buffer);
        return;
    }

    /* ---------- multi-threaded horizontal pass ------------------------- */
    const unsigned rowsPerTask = 32;
    for (unsigned y0 = 0; y0 < height; y0 += rowsPerTask) {
        unsigned y1 = std::min(y0 + rowsPerTask, height);
        pool().enqueue([=]{
            float prev00, prev01, prev02,
                    prev10, prev11, prev12,
                    prev20, prev21, prev22,
                    val0,  val1,  val2;

            for (unsigned y = y0; y < y1; ++y) {
                unsigned char* ptr_img = image  + y * imgStride;
                float*         ptr_buf = buffer + y * bufStride;

                prev00 = prev10 = prev20 = ptr_img[0];
                prev01 = prev11 = prev21 = ptr_img[1];
                prev02 = prev12 = prev22 = ptr_img[2];

                for (unsigned x = 0; x < width; ++x, ptr_buf += 3, ptr_img += 4) {
                    BLUR(ptr_buf[0], ptr_img[0], 0);
                    BLUR(ptr_buf[1], ptr_img[1], 1);
                    BLUR(ptr_buf[2], ptr_img[2], 2);
                }
                ptr_buf -= 3;
                ptr_img -= 4;

                prev00 = prev10 = prev20 = val0;
                prev01 = prev11 = prev21 = val1;
                prev02 = prev12 = prev22 = val2;

                for (unsigned x = 0; x < width; ++x, ptr_buf -= 3, ptr_img -= 4) {
                    BLUR(ptr_buf[0], ptr_buf[0], 0);
                    BLUR(ptr_buf[1], ptr_buf[1], 1);
                    BLUR(ptr_buf[2], ptr_buf[2], 2);
                }
            }
        });
    }
    pool().wait_empty();

    /* ---------- multi-threaded vertical pass --------------------------- */
    const unsigned colsPerTask = 32;
    for (unsigned xb0 = 0; xb0 < bufStride; xb0 += colsPerTask * 3) {
        unsigned xb1 = std::min(xb0 + colsPerTask * 3, bufStride);
        pool().enqueue([=]{
            float prev00, prev01, prev02,
                    prev10, prev11, prev12,
                    prev20, prev21, prev22,
                    val0,  val1,  val2;

            for (unsigned xb = xb0; xb < xb1; xb += 3) {
                float* ptr_buf = buffer + xb + bufStride * (height - 1);
                unsigned xPixel  = xb / 3;
                unsigned char* ptr_img = image + xPixel * 4;

                prev00 = prev10 = prev20 = ptr_buf[0];
                prev01 = prev11 = prev21 = ptr_buf[1];
                prev02 = prev12 = prev22 = ptr_buf[2];

                for (unsigned y = 0; y < height; ++y, ptr_buf -= bufStride) {
                    BLUR(ptr_buf[0], ptr_buf[0], 0);
                    BLUR(ptr_buf[1], ptr_buf[1], 1);
                    BLUR(ptr_buf[2], ptr_buf[2], 2);
                }
                ptr_buf += bufStride;
                prev00 = prev10 = prev20 = val0;
                prev01 = prev11 = prev21 = val1;
                prev02 = prev12 = prev22 = val2;

                if (amount > 0.0f) {
                    for (unsigned y = 0; y < height; ++y,
                            ptr_buf += bufStride,
                            ptr_img += imgStride) {
                        SHARP(ptr_img[0], ptr_buf[0], 0);
                        SHARP(ptr_img[1], ptr_buf[1], 1);
                        SHARP(ptr_img[2], ptr_buf[2], 2);
                    }
                } else {
                    for (unsigned y = 0; y < height; ++y,
                            ptr_buf += bufStride,
                            ptr_img += imgStride) {
                        BLUR_FINAL(ptr_img[0], ptr_buf[0], 0);
                        BLUR_FINAL(ptr_img[1], ptr_buf[1], 1);
                        BLUR_FINAL(ptr_img[2], ptr_buf[2], 2);
                    }
                }
            }
        });
    }
    pool().wait_empty();

    if (own_buf) free(buffer);
#undef BLUR
#undef BLUR_FINAL
#undef SHARP
}

extern "C"
JNIEXPORT void JNICALL
Java_org_telegram_messenger_Utilities_blurBitmapIIR(JNIEnv* env, jclass, jobject bmp, jfloat sigma)
{
    AndroidBitmapInfo info;
    unsigned char* px;
    if (AndroidBitmap_getInfo(env, bmp, &info) != ANDROID_BITMAP_RESULT_SUCCESS ||
        AndroidBitmap_lockPixels(env, bmp, (void**)&px) != ANDROID_BITMAP_RESULT_SUCCESS)
        return;

    iir_gauss_blur_u8_rgba_parallel(px, nullptr,
                                    info.width, info.height,
                                    sigma, 0.0f);
    AndroidBitmap_unlockPixels(env, bmp);
}