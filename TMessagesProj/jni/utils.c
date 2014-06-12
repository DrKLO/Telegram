#include "utils.h"

void throwException(JNIEnv *env, char *format, ...) {
    jclass exClass = (*env)->FindClass(env, "java/lang/UnsupportedOperationException");
    if (!exClass) {
        return;
	}
    char dest[256];
    va_list argptr;
    va_start(argptr, format);
    vsprintf(dest, format, argptr);
    va_end(argptr);
    (*env)->ThrowNew(env, exClass, dest);
}
