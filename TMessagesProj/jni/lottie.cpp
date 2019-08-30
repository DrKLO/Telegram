#include <jni.h>
#include <android/bitmap.h>
#include <cstring>
#include <rlottie.h>
#include <lz4.h>
#include <unistd.h>
#include <pthread.h>
#include <map>
#include <tgnet/ConnectionsManager.h>
#include <tgnet/FileLog.h>
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
    std::string path;
    std::string cacheFile;
    uint8_t *decompressBuffer = nullptr;
    uint32_t maxFrameSize = 0;
    uint32_t imageSize = 0;
    uint32_t fileOffset = 0;
    bool nextFrameIsCacheFrame = false;
};

jlong Java_org_telegram_ui_Components_RLottieDrawable_create(JNIEnv *env, jclass clazz, jstring src, jintArray data, jboolean precache, jintArray colorReplacement) {
    LottieInfo *info = new LottieInfo();

    std::map<int32_t, int32_t> colors;
    if (colorReplacement != nullptr) {
        jint *arr = env->GetIntArrayElements(colorReplacement, 0);
        if (arr != nullptr) {
            jsize len = env->GetArrayLength(colorReplacement);
            for (int32_t a = 0; a < len / 2; a++) {
                colors[arr[a * 2]] = arr[a * 2 + 1];
            }
            env->ReleaseIntArrayElements(colorReplacement, arr, 0);
        }
    }

    char const *srcString = env->GetStringUTFChars(src, 0);
    info->path = srcString;
    info->animation = rlottie::Animation::loadFromFile(info->path, colors);
    if (srcString != 0) {
        env->ReleaseStringUTFChars(src, srcString);
    }
    if (info->animation == nullptr) {
        delete info;
        return 0;
    }
    info->frameCount = info->animation->totalFrame();
    info->fps = (int) info->animation->frameRate();
    if (info->fps > 60 || info->frameCount > 600) {
        delete info;
        return 0;
    }
    info->precache = precache;
    if (info->precache) {
        info->cacheFile = info->path;
        info->cacheFile += ".cache";
        FILE *precacheFile = fopen(info->cacheFile.c_str(), "r+");
        if (precacheFile == nullptr) {
            info->createCache = true;
        } else {
            uint8_t temp;
            size_t read = fread(&temp, sizeof(uint8_t), 1, precacheFile);
            info->createCache = read != 1 || temp == 0;
            if (!info->createCache) {
                fread(&(info->maxFrameSize), sizeof(uint32_t), 1, precacheFile);
                fread(&(info->imageSize), sizeof(uint32_t), 1, precacheFile);
                info->fileOffset = 9;
            }
            fclose(precacheFile);
        }
    }

    jint *dataArr = env->GetIntArrayElements(data, 0);
    if (dataArr != nullptr) {
        dataArr[0] = (jint) info->frameCount;
        dataArr[1] = (jint) info->animation->frameRate();
        dataArr[2] = info->createCache ? 1 : 0;
        env->ReleaseIntArrayElements(data, dataArr, 0);
    }
    return (jlong) (intptr_t) info;
}

jlong Java_org_telegram_ui_Components_RLottieDrawable_createWithJson(JNIEnv *env, jclass clazz, jstring json, jstring name, jintArray data) {
    LottieInfo *info = new LottieInfo();

    char const *jsonString = env->GetStringUTFChars(json, 0);
    char const *nameString = env->GetStringUTFChars(name, 0);
    info->animation = rlottie::Animation::loadFromData(jsonString, nameString);
    if (jsonString != 0) {
        env->ReleaseStringUTFChars(json, jsonString);
    }
    if (nameString != 0) {
        env->ReleaseStringUTFChars(name, nameString);
    }
    if (info->animation == nullptr) {
        delete info;
        return 0;
    }
    info->frameCount = info->animation->totalFrame();
    info->fps = (int) info->animation->frameRate();

    jint *dataArr = env->GetIntArrayElements(data, 0);
    if (dataArr != nullptr) {
        dataArr[0] = (int) info->frameCount;
        dataArr[1] = (int) info->animation->frameRate();
        dataArr[2] = 0;
        env->ReleaseIntArrayElements(data, dataArr, 0);
    }
    return (jlong) (intptr_t) info;
}

void Java_org_telegram_ui_Components_RLottieDrawable_destroy(JNIEnv *env, jclass clazz, jlong ptr) {
    if (ptr == NULL) {
        return;
    }
    LottieInfo *info = (LottieInfo *) (intptr_t) ptr;
    delete info;
}

void Java_org_telegram_ui_Components_RLottieDrawable_setLayerColor(JNIEnv *env, jclass clazz, jlong ptr, jstring layer, jint color) {
    if (ptr == NULL || layer == nullptr) {
        return;
    }
    LottieInfo *info = (LottieInfo *) (intptr_t) ptr;
    char const *layerString = env->GetStringUTFChars(layer, 0);
    info->animation->setValue<Property::Color>(layerString, Color(((color) & 0xff) / 255.0f, ((color >> 8) & 0xff) / 255.0f, ((color >> 16) & 0xff) / 255.0f));
    if (layerString != 0) {
        env->ReleaseStringUTFChars(layer, layerString);
    }
}

void Java_org_telegram_ui_Components_RLottieDrawable_createCache(JNIEnv *env, jclass clazz, jlong ptr, jobject bitmap, jint w, jint h, jint stride) {
    if (ptr == NULL || bitmap == nullptr) {
        return;
    }
    LottieInfo *info = (LottieInfo *) (intptr_t) ptr;

    FILE *precacheFile = fopen(info->cacheFile.c_str(), "r+");
    if (precacheFile != nullptr) {
        uint8_t temp;
        size_t read = fread(&temp, sizeof(uint8_t), 1, precacheFile);
        fclose(precacheFile);
        if (read == 1 && temp != 0) {
            return;
        }
    }

    void *pixels;
    if (info->nextFrameIsCacheFrame && info->createCache && info->frameCount != 0 && w * 4 == stride && AndroidBitmap_lockPixels(env, bitmap, &pixels) >= 0) {
        precacheFile = fopen(info->cacheFile.c_str(), "w+");
        if (precacheFile != nullptr) {
            fseek(precacheFile, info->fileOffset = 9, SEEK_SET);

            uint32_t size;
            uint32_t firstFrameSize = 0;
            info->maxFrameSize = 0;
            int bound = LZ4_compressBound(w * h * 4);
            uint8_t *compressBuffer = new uint8_t[bound];
            Surface surface((uint32_t *) pixels, (size_t) w, (size_t) h, (size_t) stride);
            //int64_t time = ConnectionsManager::getInstance(0).getCurrentTimeMillis();
            //int totalSize = 0;
            int framesPerUpdate = info->fps < 60 ? 1 : 2;
            for (size_t a = 0; a < info->frameCount; a += framesPerUpdate) {
                info->animation->renderSync(a, surface);
                size = (uint32_t) LZ4_compress_default((const char *) pixels, (char *) compressBuffer, w * h * 4, bound);
                //totalSize += size;
                if (a == 0) {
                    firstFrameSize = size;
                }
                info->maxFrameSize = MAX(info->maxFrameSize, size);
                fwrite(&size, sizeof(uint32_t), 1, precacheFile);
                fwrite(compressBuffer, sizeof(uint8_t), size, precacheFile);
            }
            delete[] compressBuffer;
            //DEBUG_D("total size %s = %d, time = %lld ms", info->path.c_str(), totalSize, (ConnectionsManager::getInstance(0).getCurrentTimeMillis() - time));
            fseek(precacheFile, 0, SEEK_SET);
            uint8_t byte = 1;
            info->imageSize = (uint32_t) w * h * 4;
            fwrite(&byte, sizeof(uint8_t), 1, precacheFile);
            fwrite(&info->maxFrameSize, sizeof(uint32_t), 1, precacheFile);
            fwrite(&info->imageSize, sizeof(uint32_t), 1, precacheFile);
            fflush(precacheFile);
            int fd = fileno(precacheFile);
            fsync(fd);
            info->createCache = false;
            info->fileOffset = 9 + sizeof(uint32_t) + firstFrameSize;
            fclose(precacheFile);
        }
        AndroidBitmap_unlockPixels(env, bitmap);
    }
}

jint Java_org_telegram_ui_Components_RLottieDrawable_getFrame(JNIEnv *env, jclass clazz, jlong ptr, jint frame, jobject bitmap, jint w, jint h, jint stride) {
    if (ptr == NULL || bitmap == nullptr) {
        return 0;
    }
    LottieInfo *info = (LottieInfo *) (intptr_t) ptr;
    void *pixels;
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) >= 0) {
        bool loadedFromCache = false;
        if (info->precache && !info->createCache && w * 4 == stride && info->maxFrameSize <= w * h * 4 && info->imageSize == w * h * 4) {
            FILE *precacheFile = fopen(info->cacheFile.c_str(), "r");
            if (precacheFile != nullptr) {
                fseek(precacheFile, info->fileOffset, SEEK_SET);
                if (info->decompressBuffer == nullptr) {
                    info->decompressBuffer = new uint8_t[info->maxFrameSize];
                }
                uint32_t frameSize;
                fread(&frameSize, sizeof(uint32_t), 1, precacheFile);
                if (frameSize <= info->maxFrameSize) {
                    fread(info->decompressBuffer, sizeof(uint8_t), frameSize, precacheFile);
                    info->fileOffset += 4 + frameSize;
                    LZ4_decompress_safe((const char *) info->decompressBuffer, (char *) pixels, frameSize, w * h * 4);
                    loadedFromCache = true;
                }
                fclose(precacheFile);
                int framesPerUpdate = info->fps < 60 ? 1 : 2;
                if (frame + framesPerUpdate >= info->frameCount) {
                    info->fileOffset = 9;
                }
            }
        }
        if (!loadedFromCache) {
            Surface surface((uint32_t *) pixels, (size_t) w, (size_t) h, (size_t) stride);
            info->animation->renderSync((size_t) frame, surface);
            info->nextFrameIsCacheFrame = true;
        }

        AndroidBitmap_unlockPixels(env, bitmap);
    }
    return frame;
}
}
