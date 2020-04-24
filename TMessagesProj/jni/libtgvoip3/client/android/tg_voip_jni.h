//
// Created by Grishka on 14.08.2018.
//

#ifndef TELEGRAM_TG_VOIP_JNI_H
#define TELEGRAM_TG_VOIP_JNI_H

#include <jni.h>

#ifdef __cplusplus
extern "C"{
#endif
void tgvoipRegisterNatives(JNIEnv* env);
#ifdef __cplusplus
}
#endif

#endif //TELEGRAM_TG_VOIP_JNI_H
