#pragma once

#include <jni.h>
#ifndef __cplusplus
#include <stdbool.h>
#endif

#ifdef __cplusplus
extern "C" {
#endif

// Call once from the client's existing JNI_OnLoad. Returns false on registration failure.
bool registerTwidgetNatives(JNIEnv *env);

#ifdef __cplusplus
}
#endif
