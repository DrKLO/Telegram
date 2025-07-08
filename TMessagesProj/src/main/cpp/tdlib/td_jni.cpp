#include <jni.h>
#include <string>
#include <memory>
#include <android/log.h>
#include "td/telegram/td_json_client.h" // Esta ruta dependerá de dónde se coloquen las cabeceras de TDLib

#define LOG_TAG "TDLib-JNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// Definición de la clase TdClientManager
class TdClientManager {
private:
    void* client;

public:
    TdClientManager() : client(nullptr) {
        client = td_json_client_create();
        if (client) {
            LOGI("TDLib client created successfully: %p", client);
        } else {
            LOGE("Failed to create TDLib client");
        }
    }

    ~TdClientManager() {
        if (client) {
            td_json_client_destroy(client);
            LOGI("TDLib client destroyed: %p", client);
            client = nullptr;
        }
    }

    void sendRequest(const std::string& request) {
        if (client) {
            LOGI("Sending request to client %p: %s", client, request.c_str());
            td_json_client_send(client, request.c_str());
        } else {
            LOGE("Cannot send request, client is null");
        }
    }

    std::string receiveResponse(double timeout) {
        if (client) {
            const char* response = td_json_client_receive(client, timeout);
            if (response) {
                LOGI("Received response from client %p", client); // No loguear la respuesta completa por si es muy larga o sensible
                return std::string(response);
            }
            return ""; // Timeout o no hay respuesta
        }
        LOGE("Cannot receive response, client is null");
        return "";
    }

    // Nota: td_json_client_execute es síncrono y no necesita una instancia de cliente.
    // Se pasa nullptr como primer argumento según la documentación de TDLib.
    static std::string executeCommand(const std::string& command) {
        LOGI("Executing command: %s", command.c_str());
        const char* result = td_json_client_execute(nullptr, command.c_str());
        if (result) {
            return std::string(result);
        }
        return "";
    }
};

// Puntero global al cliente. En una app real con múltiples cuentas, esto necesitaría un manejo más sofisticado
// (ej. un mapa de client_id a instancias de TdClientManager).
// Por simplicidad y siguiendo la guía, se usa un único puntero global.
static std::unique_ptr<TdClientManager> g_client_manager_instance;

// Funciones JNI
extern "C" JNIEXPORT jlong JNICALL
Java_org_telegram_tdlib_TelegramClient_createNativeClient(JNIEnv *env, jobject thiz) {
    if (!g_client_manager_instance) {
        g_client_manager_instance = std::make_unique<TdClientManager>();
    }
    // Devolvemos la dirección del objeto TdClientManager como un jlong.
    // Es crucial que el lado Java lo trate como un handle opaco.
    return reinterpret_cast<jlong>(g_client_manager_instance.get());
}

extern "C" JNIEXPORT void JNICALL
Java_org_telegram_tdlib_TelegramClient_destroyNativeClient(JNIEnv *env, jobject thiz, jlong client_ptr) {
    // El client_ptr es el handle que obtuvimos de createNativeClient.
    // Comparamos para asegurarnos de que estamos destruyendo la instancia correcta.
    if (g_client_manager_instance && reinterpret_cast<jlong>(g_client_manager_instance.get()) == client_ptr) {
        g_client_manager_instance.reset(); // Esto llamará al destructor de TdClientManager.
        LOGI("Native client instance destroyed via JNI.");
    } else {
        LOGE("destroyNativeClient called with invalid or already destroyed client_ptr.");
    }
}

extern "C" JNIEXPORT void JNICALL
Java_org_telegram_tdlib_TelegramClient_sendNativeRequest(JNIEnv *env, jobject thiz, jlong client_ptr, jstring request) {
    if (g_client_manager_instance && reinterpret_cast<jlong>(g_client_manager_instance.get()) == client_ptr) {
        const char* request_cstr = env->GetStringUTFChars(request, nullptr);
        if (request_cstr == nullptr) {
            LOGE("Failed to get string chars for request");
            return; // Error al obtener el string
        }
        std::string request_str(request_cstr);
        g_client_manager_instance->sendRequest(request_str);
        env->ReleaseStringUTFChars(request, request_cstr);
    } else {
        LOGE("sendNativeRequest called with invalid client_ptr.");
    }
}

extern "C" JNIEXPORT jstring JNICALL
Java_org_telegram_tdlib_TelegramClient_receiveNativeResponse(JNIEnv *env, jobject thiz, jlong client_ptr, jdouble timeout) {
    if (g_client_manager_instance && reinterpret_cast<jlong>(g_client_manager_instance.get()) == client_ptr) {
        std::string response = g_client_manager_instance->receiveResponse(timeout);
        return env->NewStringUTF(response.c_str());
    }
    LOGE("receiveNativeResponse called with invalid client_ptr.");
    return env->NewStringUTF("");
}

// Este método estaba en native-lib.cpp en la guía, pero tiene más sentido aquí
// si TdClientManager::executeCommand es estático o si se decide no usar g_client_manager_instance para esto.
// Por consistencia con la guía, lo dejo como estaba pero hago notar que td_json_client_execute no necesita una instancia.
extern "C" JNIEXPORT jstring JNICALL
Java_org_telegram_tdlib_TelegramClient_executeNativeCommand(JNIEnv *env, jobject thiz, jstring command) {
    const char* command_cstr = env->GetStringUTFChars(command, nullptr);
    if (command_cstr == nullptr) {
        LOGE("Failed to get string chars for command");
        return env->NewStringUTF(""); // Error
    }
    std::string command_str(command_cstr);
    // Llamada estática o a través de una instancia si se prefiere, aunque td_json_client_execute no la necesita.
    // Para mantener la estructura de la guía que usaba g_client->executeCommand:
    // if (g_client_manager_instance) {
    //    std::string result = g_client_manager_instance->executeCommand(command_str);
    //    env->ReleaseStringUTFChars(command, command_cstr);
    //    return env->NewStringUTF(result.c_str());
    // }
    // Siguiendo la firma de td_json_client_execute que no necesita una instancia de cliente:
    std::string result = TdClientManager::executeCommand(command_str);
    env->ReleaseStringUTFChars(command, command_cstr);
    return env->NewStringUTF(result.c_str());
}
// NOTA: La función getLibraryVersion estaba en native-lib.cpp en la guía.
// La moveré allí para mantener la separación, pero si fuera específica de la JNI de TDLib,
// podría estar aquí también.
// Para que coincida con el nombre de la clase Java (org.telegram.tdlib.TelegramClient):
// Java_org_telegram_tdlib_TelegramClient_getLibraryVersion
// Sin embargo, el native-lib.cpp de la guía lo tenía como Java_com_example_tdlib_TelegramClient_getLibraryVersion
// Ajustaré los nombres de las funciones JNI para que coincidan con el paquete `org.telegram.tdlib`.
