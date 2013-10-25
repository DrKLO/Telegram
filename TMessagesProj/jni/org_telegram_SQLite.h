#ifndef __SQLITE_H__
#define __SQLITE_H__

#include <jni.h>
#include "sqlite3.h"

void throw_sqlite3_exception(JNIEnv* env, sqlite3 *handle, int errcode);

#endif
