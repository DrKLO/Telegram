#include <jni.h>
#include <string>
#include <android/log.h>

#define LOG_TAG "TDLib-Native"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

// Esta función es de ejemplo y podría estar en MainActivity o similar.
// Ajustaré el nombre para que coincida con un posible uso en org.telegram.ui.LaunchActivity
// o alguna clase de utilidad si se quisiera llamar.
// Por ahora, la dejo como estaba en la guía, asumiendo que se llamaría desde una clase
// com.example.tdlib.MainActivity que no estamos creando en este contexto.
// Si se quiere llamar desde el código de Telegram, el nombre JNI debería cambiar.
// Para el propósito de esta integración conceptual, la presencia del archivo es lo importante.

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_tdlib_MainActivity_stringFromJNI(JNIEnv *env, jobject /* this */) {
    std::string hello = "TDLib Android Integration (native-lib.cpp) Ready";
    LOGI("Native library (native-lib.cpp) loaded successfully, stringFromJNI called.");
    return env->NewStringUTF(hello.c_str());
}

// Esta función SÍ es llamada por org.telegram.tdlib.TelegramClient
extern "C" JNIEXPORT jint JNICALL
Java_org_telegram_tdlib_TelegramClient_getLibraryVersion(JNIEnv *env, jobject /* this */) {
    LOGI("getLibraryVersion called from JNI.");
    // Aquí se podría devolver una versión real si TDLib la expone,
    // o una versión de esta capa JNI.
    return 108016; // Ejemplo: TDLib 1.8.16
}
