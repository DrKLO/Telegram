//
// libtgvoip is free and unencumbered public domain software.
// For more information, see http://unlicense.org or the UNLICENSE file
// you should have received with this source code distribution.
//

#ifndef __LOGGING_H
#define __LOGGING_H
#define LSTR_INT(x) LSTR_DO_INT(x)
#define LSTR_DO_INT(x) #x

#ifdef __APPLE__
#include <TargetConditionals.h>
#endif

#include <stdio.h>

void tgvoip_log_file_printf(char level, const char* msg, ...);
void tgvoip_log_file_write_header(FILE* file);

#if defined(__ANDROID__)

#include <android/log.h>

//#define _LOG_WRAP(...) __BASE_FILE__":"LSTR_INT(__LINE__)": "__VA_ARGS__
#define _LOG_WRAP(...) __VA_ARGS__
#define TAG "tgvoip"
#define LOGV(...) {__android_log_print(ANDROID_LOG_VERBOSE, TAG, _LOG_WRAP(__VA_ARGS__)); tgvoip_log_file_printf('V', __VA_ARGS__);}
#define LOGD(...) {__android_log_print(ANDROID_LOG_DEBUG, TAG, _LOG_WRAP(__VA_ARGS__)); tgvoip_log_file_printf('D', __VA_ARGS__);}
#define LOGI(...) {__android_log_print(ANDROID_LOG_INFO, TAG, _LOG_WRAP(__VA_ARGS__)); tgvoip_log_file_printf('I', __VA_ARGS__);}
#define LOGW(...) {__android_log_print(ANDROID_LOG_WARN, TAG, _LOG_WRAP(__VA_ARGS__)); tgvoip_log_file_printf('W', __VA_ARGS__);}
#define LOGE(...) {__android_log_print(ANDROID_LOG_ERROR, TAG, _LOG_WRAP(__VA_ARGS__)); tgvoip_log_file_printf('E', __VA_ARGS__);}

#elif defined(__APPLE__) && TARGET_OS_IPHONE && defined(TGVOIP_HAVE_TGLOG)

#include "os/darwin/TGLogWrapper.h"

#define LOGV(msg, ...) {__tgvoip_call_tglog("V/tgvoip: " msg, ##__VA_ARGS__); tgvoip_log_file_printf('V', msg, ##__VA_ARGS__);}
#define LOGD(msg, ...) {__tgvoip_call_tglog("D/tgvoip: " msg, ##__VA_ARGS__); tgvoip_log_file_printf('D', msg, ##__VA_ARGS__);}
#define LOGI(msg, ...) {__tgvoip_call_tglog("I/tgvoip: " msg, ##__VA_ARGS__); tgvoip_log_file_printf('I', msg, ##__VA_ARGS__);}
#define LOGW(msg, ...) {__tgvoip_call_tglog("W/tgvoip: " msg, ##__VA_ARGS__); tgvoip_log_file_printf('W', msg, ##__VA_ARGS__);}
#define LOGE(msg, ...) {__tgvoip_call_tglog("E/tgvoip: " msg, ##__VA_ARGS__); tgvoip_log_file_printf('E', msg, ##__VA_ARGS__);}

#elif defined(_WIN32) && defined(_DEBUG)

#include <windows.h>
#include <stdio.h>

#define _TGVOIP_W32_LOG_PRINT(verb, msg, ...){ char __log_buf[1024]; snprintf(__log_buf, 1024, "%c/tgvoip: " msg "\n", verb, ##__VA_ARGS__); OutputDebugStringA(__log_buf); tgvoip_log_file_printf((char)verb, msg, __VA_ARGS__);}

#define LOGV(msg, ...) _TGVOIP_W32_LOG_PRINT('V', msg, ##__VA_ARGS__)
#define LOGD(msg, ...) _TGVOIP_W32_LOG_PRINT('D', msg, ##__VA_ARGS__)
#define LOGI(msg, ...) _TGVOIP_W32_LOG_PRINT('I', msg, ##__VA_ARGS__)
#define LOGW(msg, ...) _TGVOIP_W32_LOG_PRINT('W', msg, ##__VA_ARGS__)
#define LOGE(msg, ...) _TGVOIP_W32_LOG_PRINT('E', msg, ##__VA_ARGS__)

#else

#include <stdio.h>

#ifndef TGVOIP_NO_STDOUT_LOGS
#ifndef TGVOIP_NO_STDOUT_COLOR
#define _TGVOIP_LOG_PRINT(verb, color, msg, ...) {printf("\033[%dm%c/tgvoip: " msg "\033[0m\n", color, verb, ##__VA_ARGS__); tgvoip_log_file_printf(verb, msg, ##__VA_ARGS__);}
#else
#define _TGVOIP_LOG_PRINT(verb, color, msg, ...) {printf("%c/tgvoip: " msg "\n", verb, ##__VA_ARGS__); tgvoip_log_file_printf(verb, msg, ##__VA_ARGS__);}
#endif
#else
#define _TGVOIP_LOG_PRINT(verb, color, msg, ...) {tgvoip_log_file_printf(verb, msg, ##__VA_ARGS__);}
#endif

#define LOGV(msg, ...) _TGVOIP_LOG_PRINT('V', 90, msg, ##__VA_ARGS__)
#define LOGD(msg, ...) _TGVOIP_LOG_PRINT('D', 37, msg, ##__VA_ARGS__)
#define LOGI(msg, ...) _TGVOIP_LOG_PRINT('I', 94, msg, ##__VA_ARGS__)
#define LOGW(msg, ...) _TGVOIP_LOG_PRINT('W', 93, msg, ##__VA_ARGS__)
#define LOGE(msg, ...) _TGVOIP_LOG_PRINT('E', 91, msg, ##__VA_ARGS__)

#endif

#if !defined(snprintf) && defined(_WIN32) && defined(__cplusplus_winrt)
#define snprintf _snprintf
#endif

#ifdef TGVOIP_LOG_VERBOSITY
#if TGVOIP_LOG_VERBOSITY<5
#undef LOGV
#define LOGV(msg, ...)
#endif
#if TGVOIP_LOG_VERBOSITY<4
#undef LOGD
#define LOGD(msg, ...)
#endif
#if TGVOIP_LOG_VERBOSITY<3
#undef LOGI
#define LOGI(msg, ...)
#endif
#if TGVOIP_LOG_VERBOSITY<2
#undef LOGW
#define LOGW(msg, ...)
#endif
#if TGVOIP_LOG_VERBOSITY<1
#undef LOGE
#define LOGE(msg, ...)
#endif
#endif

#endif //__LOGGING_H
