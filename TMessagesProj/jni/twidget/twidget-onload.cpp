// libtwidget.so: the widget engine on its own. The isolated engine process (WidgetEngineService) loads nothing else;
// the client never loads it, but for the private builds' in-process engine of the debug runners (its filters and
// gesture arbiter are in the client library, twidget-client.cpp).
#include "twidget-bridge.h"

extern "C" JNIEXPORT jint JNI_OnLoad(JavaVM *vm, void *) {
    JNIEnv *env = nullptr;
    if (vm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6) != JNI_OK)
        return -1;
    return registerTwidgetNatives(env) ? JNI_VERSION_1_6 : -1;
}
