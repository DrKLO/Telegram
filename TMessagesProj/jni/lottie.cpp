#include <jni.h>
#include <android/bitmap.h>
#include <cstring>
#include <rlottie.h>
#include <lz4.h>
#include <unistd.h>
#include <condition_variable>
#include <atomic>
#include <thread>
#include <map>
#include <sys/stat.h>
#include <utime.h>
#include "tgnet/FileLog.h"
#include "tgnet/ConnectionsManager.h"
#include "c_utils.h"

extern "C" {
using namespace rlottie;

typedef struct LottieInfo {

    ~LottieInfo() {
        if (decompressBuffer != nullptr) {
            delete[]decompressBuffer;
            decompressBuffer = nullptr;
        }
    }

    std::unique_ptr<Animation> animation;
    size_t frameCount = 0;
    int32_t fps = 30;
    bool precache = false;
    bool createCache = false;
    bool limitFps = false;
    std::string path;
    std::string cacheFile;
    uint8_t *decompressBuffer = nullptr;
    uint32_t decompressBufferSize = 0;
    volatile uint32_t maxFrameSize = 0;
    uint32_t imageSize = 0;
    uint32_t fileOffset = 0;
    bool nextFrameIsCacheFrame = false;

    FILE *precacheFile = nullptr;
    char *compressBuffer = nullptr;
    const char *buffer = nullptr;
    bool firstFrame = false;
    int bufferSize = 0;
    int compressBound = 0;
    int firstFrameSize = 0;
    volatile uint32_t framesAvailableInCache = 0;
};

JNIEXPORT jlong Java_org_telegram_ui_Components_RLottieDrawable_create(JNIEnv *env, jclass clazz, jstring src, jstring json, jint w, jint h, jintArray data, jboolean precache, jintArray colorReplacement, jboolean limitFps, jint fitzModifier) {
    auto info = new LottieInfo();

    std::map<int32_t, int32_t> *colors = nullptr;
    int color = 0;
    if (colorReplacement != nullptr) {
        jint *arr = env->GetIntArrayElements(colorReplacement, nullptr);
        if (arr != nullptr) {
            jsize len = env->GetArrayLength(colorReplacement);
            colors = new std::map<int32_t, int32_t>();
            for (int32_t a = 0; a < len / 2; a++) {
                (*colors)[arr[a * 2]] = arr[a * 2 + 1];
                if (color == 0) {
                    color = arr[a * 2 + 1];
                }
            }
            env->ReleaseIntArrayElements(colorReplacement, arr, 0);
        }
    }


    FitzModifier modifier = FitzModifier::None;
    switch (fitzModifier) {
        case 12:
            modifier = FitzModifier::Type12;
            break;
        case 3:
            modifier = FitzModifier::Type3;
            break;
        case 4:
            modifier = FitzModifier::Type4;
            break;
        case 5:
            modifier = FitzModifier::Type5;
            break;
        case 6:
            modifier = FitzModifier::Type6;
            break;
    }
    char const *srcString = env->GetStringUTFChars(src, nullptr);
    info->path = srcString;
    if (json != nullptr) {
        char const *jsonString = env->GetStringUTFChars(json, nullptr);
        if (jsonString) {
            info->animation = rlottie::Animation::loadFromData(jsonString, info->path, colors, modifier);
            env->ReleaseStringUTFChars(json, jsonString);
        }
    } else {
        info->animation = rlottie::Animation::loadFromFile(info->path, colors, modifier);
    }
    if (srcString) {
        env->ReleaseStringUTFChars(src, srcString);
    }
    if (info->animation == nullptr) {
        delete info;
        return 0;
    }
    info->frameCount = info->animation->totalFrame();
    info->fps = (int) info->animation->frameRate();
    info->limitFps = limitFps;
    if (info->fps > 60 || info->frameCount > 600) {
        delete info;
        return 0;
    }
    info->precache = precache;
    if (info->precache) {
        info->cacheFile = info->path;
        std::string::size_type index = info->cacheFile.find_last_of('/');
        if (index != std::string::npos) {
            std::string dir = info->cacheFile.substr(0, index) + "/acache";
            mkdir(dir.c_str(), 0777);
            info->cacheFile.insert(index, "/acache");
        }
        info->cacheFile += std::to_string(w) + "_" + std::to_string(h);
        if (color != 0) {
            info->cacheFile += "_" + std::to_string(color);
        }
        if (limitFps) {
            info->cacheFile += ".s.cache";
        } else {
            info->cacheFile += ".cache";
        }
        FILE *precacheFile = fopen(info->cacheFile.c_str(), "r+");
        if (precacheFile == nullptr) {
            info->createCache = true;
        } else {
            uint8_t temp;
            size_t read = fread(&temp, sizeof(uint8_t), 1, precacheFile);
            info->createCache = read != 1 || temp == 0;
            if (!info->createCache) {
                uint32_t maxFrameSize;
                fread(&maxFrameSize, sizeof(uint32_t), 1, precacheFile);
                info->maxFrameSize = maxFrameSize;
                fread(&(info->imageSize), sizeof(uint32_t), 1, precacheFile);
                info->fileOffset = 9;
                utimensat(0, info->cacheFile.c_str(), nullptr, 0);
            }
            fclose(precacheFile);
        }
    }

    jint *dataArr = env->GetIntArrayElements(data, nullptr);
    if (dataArr != nullptr) {
        dataArr[0] = (jint) info->frameCount;
        dataArr[1] = (jint) info->animation->frameRate();
        dataArr[2] = info->createCache ? 1 : 0;
        env->ReleaseIntArrayElements(data, dataArr, 0);
    }
    return (jlong) (intptr_t) info;
}

JNIEXPORT jlong Java_org_telegram_ui_Components_RLottieDrawable_createWithJson(JNIEnv *env, jclass clazz, jstring json, jstring name, jintArray data, jintArray colorReplacement) {
    std::map<int32_t, int32_t> *colors = nullptr;
    if (colorReplacement != nullptr) {
        jint *arr = env->GetIntArrayElements(colorReplacement, nullptr);
        if (arr != nullptr) {
            jsize len = env->GetArrayLength(colorReplacement);
            colors = new std::map<int32_t, int32_t>();
            for (int32_t a = 0; a < len / 2; a++) {
                (*colors)[arr[a * 2]] = arr[a * 2 + 1];
            }
            env->ReleaseIntArrayElements(colorReplacement, arr, 0);
        }
    }

    auto info = new LottieInfo();

    char const *jsonString = env->GetStringUTFChars(json, nullptr);
    char const *nameString = env->GetStringUTFChars(name, nullptr);
    info->animation = rlottie::Animation::loadFromData(jsonString, nameString, colors);
    if (jsonString) {
        env->ReleaseStringUTFChars(json, jsonString);
    }
    if (nameString) {
        env->ReleaseStringUTFChars(name, nameString);
    }
    if (info->animation == nullptr) {
        delete info;
        return 0;
    }
    info->frameCount = info->animation->totalFrame();
    info->fps = (int) info->animation->frameRate();

    jint *dataArr = env->GetIntArrayElements(data, nullptr);
    if (dataArr != nullptr) {
        dataArr[0] = (int) info->frameCount;
        dataArr[1] = (int) info->animation->frameRate();
        dataArr[2] = 0;
        env->ReleaseIntArrayElements(data, dataArr, 0);
    }
    return (jlong) (intptr_t) info;
}

JNIEXPORT void Java_org_telegram_ui_Components_RLottieDrawable_destroy(JNIEnv *env, jclass clazz, jlong ptr) {
    if (!ptr) {
        return;
    }
    auto info = (LottieInfo *) (intptr_t) ptr;
    delete info;
}

JNIEXPORT void Java_org_telegram_ui_Components_RLottieDrawable_setLayerColor(JNIEnv *env, jclass clazz, jlong ptr, jstring layer, jint color) {
    if (!ptr || layer == nullptr) {
        return;
    }
    auto info = (LottieInfo *) (intptr_t) ptr;
    char const *layerString = env->GetStringUTFChars(layer, nullptr);
    info->animation->setValue<Property::Color>(layerString, Color(((color) & 0xff) / 255.0f, ((color >> 8) & 0xff) / 255.0f, ((color >> 16) & 0xff) / 255.0f));
    if (layerString) {
        env->ReleaseStringUTFChars(layer, layerString);
    }
}

JNIEXPORT void Java_org_telegram_ui_Components_RLottieDrawable_replaceColors(JNIEnv *env, jclass clazz, jlong ptr, jintArray colorReplacement) {
    if (!ptr || colorReplacement == nullptr) {
        return;
    }
    auto info = (LottieInfo *) (intptr_t) ptr;

    jint *arr = env->GetIntArrayElements(colorReplacement, nullptr);
    if (arr != nullptr) {
        jsize len = env->GetArrayLength(colorReplacement);
        for (int32_t a = 0; a < len / 2; a++) {
            (*info->animation->colorMap)[arr[a * 2]] = arr[a * 2 + 1];
        }
        info->animation->resetCurrentFrame();
        env->ReleaseIntArrayElements(colorReplacement, arr, 0);
    }
}

bool cacheWriteThreadCreated{false};
LottieInfo *cacheWriteThreadTask{nullptr};
bool cacheWriteThreadDone{false};
std::thread worker;
std::mutex cacheMutex;
std::condition_variable cacheCv;

std::mutex cacheDoneMutex;
std::condition_variable cacheDoneCv;
std::atomic<bool> frameReady{false};

void CacheWriteThreadProc() {
    while (!cacheWriteThreadDone) {
        std::unique_lock<std::mutex> lk(cacheMutex);
        cacheCv.wait(lk, [] { return frameReady.load(); });
        std::lock_guard<std::mutex> lg(cacheDoneMutex);
        LottieInfo *task;
        if (cacheWriteThreadTask != nullptr) {
            task = cacheWriteThreadTask;
            cacheWriteThreadTask = nullptr;
        } else {
            task = nullptr;
        }
        lk.unlock();

        if (task != nullptr) {
            auto size = (uint32_t) LZ4_compress_default(task->buffer, task->compressBuffer, task->bufferSize, task->compressBound);
            if (task->firstFrame) {
                task->firstFrameSize = size;
                task->fileOffset = 9 + sizeof(uint32_t) + task->firstFrameSize;
            }
            task->maxFrameSize = MAX(task->maxFrameSize, size);
            fwrite(&size, sizeof(uint32_t), 1, task->precacheFile);
            fwrite(task->compressBuffer, sizeof(uint8_t), size, task->precacheFile);

            fflush(task->precacheFile);
            fsync(fileno(task->precacheFile));
            task->framesAvailableInCache++;
        }
        frameReady = false;
        cacheDoneCv.notify_one();
    }
}

JNIEXPORT void Java_org_telegram_ui_Components_RLottieDrawable_createCache(JNIEnv *env, jclass clazz, jlong ptr, jint w, jint h) {
    if (ptr == NULL) {
        return;
    }
    auto info = (LottieInfo *) (intptr_t) ptr;

    FILE *cacheFile = fopen(info->cacheFile.c_str(), "r+");
    if (cacheFile != nullptr) {
        uint8_t temp;
        size_t read = fread(&temp, sizeof(uint8_t), 1, cacheFile);
        fclose(cacheFile);
        if (read == 1 && temp != 0) {
            return;
        }
    }

    if (!cacheWriteThreadCreated) {
        cacheWriteThreadCreated = true;
        worker = std::thread(CacheWriteThreadProc);
    }

    if (info->nextFrameIsCacheFrame && info->createCache && info->frameCount != 0) {
        info->precacheFile = fopen(info->cacheFile.c_str(), "w+");
        if (info->precacheFile != nullptr) {
            fseek(info->precacheFile, info->fileOffset = 9, SEEK_SET);
            info->maxFrameSize = 0;
            info->bufferSize = w * h * 4;
            info->imageSize = (uint32_t) w * h * 4;
            info->compressBound = LZ4_compressBound(info->bufferSize);
            info->compressBuffer = new char[info->compressBound];
            auto firstBuffer = new uint8_t[info->bufferSize];
            auto secondBuffer = new uint8_t[info->bufferSize];
            //long time = ConnectionsManager::getInstance(0).getCurrentTimeMonotonicMillis();

            Surface surface1((uint32_t *) firstBuffer, (size_t) w, (size_t) h, (size_t) w * 4);
            Surface surface2((uint32_t *) secondBuffer, (size_t) w, (size_t) h, (size_t) w * 4);
            int framesPerUpdate = !info->limitFps || info->fps < 60 ? 1 : 2;
            int num = 0;
            for (size_t a = 0; a < info->frameCount; a += framesPerUpdate) {
                Surface &surfaceToRender = num % 2 == 0 ? surface1 : surface2;
                num++;
                info->animation->renderSync(a, surfaceToRender, true);
                if (a != 0) {
                    std::unique_lock<std::mutex> lk(cacheDoneMutex);
                    cacheDoneCv.wait(lk, [] { return !frameReady.load(); });
                }

                std::lock_guard<std::mutex> lg(cacheMutex);
                cacheWriteThreadTask = info;
                info->firstFrame = a == 0;
                info->buffer = (const char *) surfaceToRender.buffer();
                frameReady = true;
                cacheCv.notify_one();
            }
            std::unique_lock<std::mutex> lk(cacheDoneMutex);
            cacheDoneCv.wait(lk, [] { return !frameReady.load(); });

            //DEBUG_D("sticker time = %d", (int) (ConnectionsManager::getInstance(0).getCurrentTimeMonotonicMillis() - time));
            delete[] info->compressBuffer;
            delete[] firstBuffer;
            delete[] secondBuffer;
            fseek(info->precacheFile, 0, SEEK_SET);
            uint8_t byte = 1;
            fwrite(&byte, sizeof(uint8_t), 1, info->precacheFile);
            uint32_t maxFrameSize = info->maxFrameSize;
            fwrite(&maxFrameSize, sizeof(uint32_t), 1, info->precacheFile);
            fwrite(&info->imageSize, sizeof(uint32_t), 1, info->precacheFile);
            fflush(info->precacheFile);
            fsync(fileno(info->precacheFile));
            info->createCache = false;
            fclose(info->precacheFile);
        }
    }
}

JNIEXPORT jint Java_org_telegram_ui_Components_RLottieDrawable_getFrame(JNIEnv *env, jclass clazz, jlong ptr, jint frame, jobject bitmap, jint w, jint h, jint stride, jboolean clear) {
    if (!ptr || bitmap == nullptr) {
        return 0;
    }
    auto info = (LottieInfo *) (intptr_t) ptr;

    int framesPerUpdate = !info->limitFps || info->fps < 60 ? 1 : 2;
    int framesAvailableInCache = info->framesAvailableInCache;

    if (info->createCache && info->precache && frame > 0) {
        if (frame / framesPerUpdate >= framesAvailableInCache) {
            return -1;
        }
    }

    void *pixels;
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) >= 0) {
        bool loadedFromCache = false;
        uint32_t maxFrameSize = info->maxFrameSize;
        if (info->precache && (!info->createCache || frame > 0) && w * 4 == stride && maxFrameSize <= w * h * 4 && info->imageSize == w * h * 4) {
            FILE *precacheFile = fopen(info->cacheFile.c_str(), "r");
            if (precacheFile != nullptr) {
                if (info->decompressBuffer != nullptr && info->decompressBufferSize < maxFrameSize) {
                    delete[] info->decompressBuffer;
                    info->decompressBuffer = nullptr;
                }
                if (info->decompressBuffer == nullptr) {
                    info->decompressBufferSize = maxFrameSize;
                    if (info->createCache) {
                        info->decompressBufferSize += 10000;
                    }
                    info->decompressBuffer = new uint8_t[info->decompressBufferSize];
                }
                fseek(precacheFile, info->fileOffset, SEEK_SET);
                uint32_t frameSize;
                fread(&frameSize, sizeof(uint32_t), 1, precacheFile);
                if (frameSize > 0 && frameSize <= info->decompressBufferSize) {
                    fread(info->decompressBuffer, sizeof(uint8_t), frameSize, precacheFile);
                    info->fileOffset += 4 + frameSize;
                    LZ4_decompress_safe((const char *) info->decompressBuffer, (char *) pixels, frameSize, w * h * 4);
                    loadedFromCache = true;
                }
                fclose(precacheFile);
                if (frame + framesPerUpdate >= info->frameCount) {
                    info->fileOffset = 9;
                }
            }
        }

        if (!loadedFromCache) {
            if (!info->nextFrameIsCacheFrame || !info->precache) {
                Surface surface((uint32_t *) pixels, (size_t) w, (size_t) h, (size_t) stride);
                info->animation->renderSync((size_t) frame, surface, clear);
                info->nextFrameIsCacheFrame = true;
            }
        }

        AndroidBitmap_unlockPixels(env, bitmap);
    }
    return frame;
}
}
