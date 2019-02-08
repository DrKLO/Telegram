#ifndef log_h
#define log_h

#include <android/log.h>
#include <jni.h>

#define LOG_TAG "tmessages_native"
#ifndef LOG_DISABLED
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGV(...) __android_log_print(ANDROID_LOG_VERBOSE, LOG_TAG, __VA_ARGS__)
#else
#define LOGI(...)
#define LOGD(...)
#define LOGE(...)
#define LOGV(...)
#endif

#ifdef __cplusplus
// Use std::min/std::max from <algorithm> rather than the macros used for C
// since the macros will collide with things like numeric_limits::max.
#include <algorithm>
using std::min;
using std::max;
#else
#ifndef max
#define max(x, y) ((x) > (y)) ? (x) : (y)
#endif
#ifndef min
#define min(x, y) ((x) < (y)) ? (x) : (y)
#endif
#endif

void throwException(JNIEnv *env, char *format, ...);

#endif
