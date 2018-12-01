#include "utils.h"
#define BUFSIZE 255

void throwException(JNIEnv *env, char *format, ...) {
    jclass exClass = (*env)->FindClass(env, "java/lang/UnsupportedOperationException");
    if (!exClass) {
        return;
	}
    char dest[BUFSIZE+1] ={0};
    va_list argptr;
    va_start(argptr, format);
    vsnprintf(dest, BUFSIZE, format, argptr);
    va_end(argptr);
    (*env)->ThrowNew(env, exClass, dest);
}
