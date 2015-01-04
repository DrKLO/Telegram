#ifndef gif_h
#define gif_h

jint gifOnJNILoad(JavaVM *vm, void *reserved, JNIEnv *env);
void gifOnJNIUnload(JavaVM *vm, void *reserved);

#endif