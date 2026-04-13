#include <jni.h>
#include <android/bitmap.h>
#include <cstring>
#include <rlottie.h>
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
    std::unique_ptr<Animation> animation;
    size_t frameCount = 0;
    int32_t fps = 30;
    bool precache = false;
    bool createCache = false;
    std::string path;
    std::string cacheFile;
    volatile uint32_t maxFrameSize = 0;
    uint32_t imageSize = 0;
    uint32_t fileOffset = 0;
    uint32_t fileFrame = 0;
    std::map<int32_t, int32_t>* colors;
    int bufferSize = 0;
};

const unsigned int num_threads = std::thread::hardware_concurrency();
std::vector<std::thread> threads;

JNIEXPORT jlong Java_org_telegram_ui_Components_RLottieDrawable_create(JNIEnv *env, jclass clazz, jstring src, jstring json, jint w, jint h, jintArray data, jboolean precache, jintArray colorReplacement, jboolean limitFps, jint fitzModifier) {
    auto info = std::make_unique<LottieInfo>();

    if (colorReplacement != nullptr) {
        jint *arr = env->GetIntArrayElements(colorReplacement, nullptr);
        if (arr != nullptr) {
            jsize len = env->GetArrayLength(colorReplacement);
            std::vector<std::map<int32_t, int32_t>> partial_maps(num_threads);
            int32_t chunk_size = (len / 2 + num_threads - 1) / num_threads;
            auto thread_func = [&](int32_t start, int32_t end, std::map<int32_t, int32_t> *colors) {
                for (int32_t i = start; i < end; i++) {
                    colors->insert({arr[i * 2], arr[i * 2 + 1]});
                }
            };

            for (int32_t i = 0; i < num_threads; i++) {
                int32_t start = i * chunk_size;
                int32_t end = std::min(start + chunk_size, static_cast<int32_t>(len / 2));
                threads.emplace_back(thread_func, start, end, &partial_maps[i]);
            }

            for (auto &t : threads) {
                t.join();
            }

            std::map<int32_t, int32_t> result;
            for (auto &partial_map : partial_maps) {
                result.insert(partial_map.begin(), partial_map.end());
            }

            info->colors = &result;

            for (int32_t i = 0; i < len / 2; i++) {
                info->colors->insert({arr[i*2],arr[i*2+1]});
            }
            env->ReleaseIntArrayElements(colorReplacement, arr, 0);
        }
    }

    FitzModifier modifier;

    switch (fitzModifier) {
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
        case 12:
            modifier = FitzModifier::Type12;
            break;
        default:
            modifier = FitzModifier::None;
            break;
    }
    const char *srcString = env->GetStringUTFChars(src, nullptr);
    info->path = srcString;
    if (json != nullptr) {
        const char *jsonString = env->GetStringUTFChars(json, nullptr);
        if (jsonString) {
            info->animation = rlottie::Animation::loadFromData(jsonString, info->path, info->colors, modifier);
            env->ReleaseStringUTFChars(json, jsonString);
        }
    } else {
        info->animation = rlottie::Animation::loadFromFile(info->path, info->colors, modifier);
    }
    if (srcString) {
        env->ReleaseStringUTFChars(src, srcString);
    }
    if (info->animation == nullptr) {
        return 0;
    }

    info->frameCount = info->animation->totalFrame();
    info->fps = static_cast<int32_t>(info->animation->frameRate());
    if (info->fps > 60 || info->frameCount > 600) {
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
        if (!info->colors->empty()) {
            info->cacheFile += "_" + std::to_string(info->colors->begin()->second);
        }
        if (limitFps) {
            info->cacheFile += ".s.cache";
        } else {
            info->cacheFile += ".cache";
        }
        FILE *precacheFile = fopen(info->cacheFile.c_str(), "r+");
        if (precacheFile == nullptr) {
            precacheFile = fopen(info->cacheFile.c_str(), "w+");
            if (precacheFile == nullptr) {
                return 0;
            }
        } else {
            fclose(precacheFile);
        }

        if (access(info->cacheFile.c_str(), F_OK) == -1) {
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
                info->fileFrame = 0;
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
    return (jlong) info.release();
}

JNIEXPORT jlong Java_org_telegram_ui_Components_RLottieDrawable_getFramesCount(JNIEnv *env, jclass clazz, jstring src, jstring json) {
    auto info = new LottieInfo();
    char const *srcString = env->GetStringUTFChars(src, nullptr);
    info->path = srcString;
    if (json != nullptr) {
        char const *jsonString = env->GetStringUTFChars(json, nullptr);
        if (jsonString) {
            info->animation = rlottie::Animation::loadFromData(jsonString, info->path, nullptr, FitzModifier::None);
            env->ReleaseStringUTFChars(json, jsonString);
        }
    } else {
        info->animation = rlottie::Animation::loadFromFile(info->path, nullptr, FitzModifier::None);
    }
    if (srcString) {
        env->ReleaseStringUTFChars(src, srcString);
    }
    if (info->animation == nullptr) {
        delete info;
        return 0;
    }
    long framesCount = info->animation->totalFrame();
    delete info;
    return (jlong) framesCount;
}

JNIEXPORT jdouble Java_org_telegram_ui_Components_RLottieDrawable_getDuration(JNIEnv *env, jclass clazz, jstring src, jstring json) {
    auto info = new LottieInfo();
    char const *srcString = env->GetStringUTFChars(src, nullptr);
    info->path = srcString;
    if (json != nullptr) {
        char const *jsonString = env->GetStringUTFChars(json, nullptr);
        if (jsonString) {
            info->animation = rlottie::Animation::loadFromData(jsonString, info->path, nullptr, FitzModifier::None);
            env->ReleaseStringUTFChars(json, jsonString);
        }
    } else {
        info->animation = rlottie::Animation::loadFromFile(info->path, nullptr, FitzModifier::None);
    }
    if (srcString) {
        env->ReleaseStringUTFChars(src, srcString);
    }
    if (info->animation == nullptr) {
        delete info;
        return 0;
    }
    double duration = info->animation->duration();
    delete info;
    return (jdouble) duration;
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

    auto info = std::make_unique<LottieInfo>();

    const char *jsonString = env->GetStringUTFChars(json, nullptr);
    const char *nameString = env->GetStringUTFChars(name, nullptr);
    info->animation = Animation::loadFromData(jsonString, nameString, colors);
    env->ReleaseStringUTFChars(json, jsonString);
    env->ReleaseStringUTFChars(name, nameString);

    if (info->animation == nullptr) {
        return 0;
    }

    info->frameCount = info->animation->totalFrame();
    info->fps = static_cast<int32_t>(info->animation->frameRate());

    jint *dataArr = env->GetIntArrayElements(data, nullptr);
    if (dataArr != nullptr) {
        dataArr[0] = (int) info->frameCount;
        dataArr[1] = (int) info->animation->frameRate();
        dataArr[2] = 0;
        env->ReleaseIntArrayElements(data, dataArr, 0);
    }
    return (jlong) info.release();
}

JNIEXPORT void JNICALL Java_org_telegram_ui_Components_RLottieDrawable_destroy(JNIEnv *env, jclass clazz, jlong ptr) {
    auto info = reinterpret_cast<LottieInfo *>(ptr);
    if (info == nullptr) {
        return;
    }
    delete info;
}

JNIEXPORT void JNICALL
Java_org_telegram_ui_Components_RLottieDrawable_setLayerColor(JNIEnv *env, jclass clazz, jlong ptr, jstring layer,
                                                              jint color) {
    auto info = reinterpret_cast<LottieInfo *>(ptr);
    if (info == nullptr || layer == nullptr) {
        return;
    }
    float r = static_cast<float>(color & 0xff) / 255.0f;
    float g = static_cast<float>((color >> 8) & 0xff) / 255.0f;
    float b = static_cast<float>((color >> 16) & 0xff) / 255.0f;
    const char *layerString = env->GetStringUTFChars(layer, nullptr);

    info->animation->setValue<Property::Color>(layerString, Color(r, g, b));
    env->ReleaseStringUTFChars(layer, layerString);
}

JNIEXPORT void JNICALL
Java_org_telegram_ui_Components_RLottieDrawable_replaceColors(JNIEnv *env, jclass clazz, jlong ptr,
                                                              jintArray colorReplacement) {
    auto info = reinterpret_cast<LottieInfo *>(ptr);
    if (info == nullptr || colorReplacement == nullptr) {
        return;
    }

    jint *arr = env->GetIntArrayElements(colorReplacement, nullptr);
    if (arr != nullptr) {
        jsize len = env->GetArrayLength(colorReplacement);
        for (int32_t i = 0; i < len / 2; i++) {
            info->animation->colorMap->insert({arr[i * 2], arr[i * 2 + 1]});
        }
        info->animation->resetCurrentFrame();
        env->ReleaseIntArrayElements(colorReplacement, arr, 0);
    }
}

JNIEXPORT jint JNICALL
Java_org_telegram_ui_Components_RLottieDrawable_getFrame(JNIEnv *env, jclass clazz, jlong ptr, jint frame,
                                                         jobject bitmap, jint w, jint h, jint stride, jboolean clear) {
    auto info = reinterpret_cast<LottieInfo *>(ptr);
    if (info == nullptr || bitmap == nullptr) {
        return 0;
    }
    void *pixels = nullptr;
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) < 0 || pixels == nullptr) {
        return -1;
    }

    bool result = false;

    Surface surface(reinterpret_cast<uint32_t *>(pixels),
                    static_cast<size_t>(w),
                    static_cast<size_t>(h),
                    static_cast<size_t>(stride)
                    );
    info->animation->renderSync(static_cast<size_t>(frame), surface, clear, &result);

    AndroidBitmap_unlockPixels(env, bitmap);

    return result ? frame : -5;
}
}
