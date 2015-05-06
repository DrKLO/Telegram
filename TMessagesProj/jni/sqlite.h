#ifndef sqlite_h
#define sqlite_h

#include <jni.h>
#include "sqlite/sqlite3.h"

void throw_sqlite3_exception(JNIEnv* env, sqlite3 *handle, int errcode);
jint sqliteOnJNILoad(JavaVM *vm, void *reserved, JNIEnv *env);

#endif
