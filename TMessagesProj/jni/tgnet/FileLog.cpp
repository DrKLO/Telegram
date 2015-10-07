/*
 * This is the source code of tgnet library v. 1.0
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2015.
 */

#include <stdio.h>
#include <stdarg.h>
#include "FileLog.h"

#ifdef ANDROID
#include <android/log.h>
#endif

void FileLog::e(const char *message, ...) {
    va_list argptr;
    va_start(argptr, message);
#ifdef ANDROID
    __android_log_vprint(ANDROID_LOG_ERROR, "tgnet", message, argptr);
#else
    printf("error: ");
    vprintf(message, argptr);
    printf("\n");
    fflush(stdout);
#endif
    va_end(argptr);
}

void FileLog::w(const char *message, ...) {
    va_list argptr;
    va_start(argptr, message);
#ifdef ANDROID
    __android_log_vprint(ANDROID_LOG_WARN, "tgnet", message, argptr);
#else
    printf("warning: ");
    vprintf(message, argptr);
    printf("\n");
    fflush(stdout);
#endif
    va_end(argptr);
}

void FileLog::d(const char *message, ...) {
    va_list argptr;
    va_start(argptr, message);
#ifdef ANDROID
    __android_log_vprint(ANDROID_LOG_DEBUG, "tgnet", message, argptr);
#else
    printf("debug: ");
    vprintf(message, argptr);
    printf("\n");
    fflush(stdout);
#endif
    va_end(argptr);
}
