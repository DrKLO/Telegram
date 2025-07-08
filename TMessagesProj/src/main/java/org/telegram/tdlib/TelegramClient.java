package org.telegram.tdlib;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import org.json.JSONObject;
import org.json.JSONException;

import org.telegram.messenger.ApplicationLoader; // Necesario para directorios
import org.telegram.messenger.BuildVars;       // Necesario para API_ID y API_HASH

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

public class TelegramClient {
    private static final String TAG = "TelegramClient";

    static {
        // Asumiendo que la biblioteca nativa se llamará 'tdjni' como en la guía de CMake
        // o 'tdlib' si se sigue el nombre del proyecto en CMakeLists.txt.
        // La guía tenía add_library(tdlib SHARED ...)
        // y target_link_libraries(tdlib tdjsonandroid ...),
        // pero también add_library(tdjsonandroid SHARED IMPORTED).
        // Usaré 'tdlib' como el nombre de la biblioteca JNI principal.
        try {
            System.loadLibrary("tdlib");
        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "Failed to load native TDLib library.", e);
            // En una aplicación real, aquí se manejaría el error apropiadamente.
        }
    }

    // Métodos nativos
    private native long createNativeClient();
    private native void destroyNativeClient(long clientId);
    private native void sendNativeRequest(long clientId, String request);
    private native String receiveNativeResponse(long clientId, double timeout);
    private native String executeNativeCommand(String command);
    private native int getLibraryVersion(); // Este estaba en native-lib.cpp

    // Variables de instancia
    private long nativeClientId;
    private final AtomicLong currentQueryId = new AtomicLong(1);
    // private final BlockingQueue<String> responseQueue = new LinkedBlockingQueue<>(); // No se usa en el código provisto
    private Thread responseThread;
    private volatile boolean isRunning = false; // Asegurar visibilidad entre hilos
    private Handler mainHandler;

    // Interfaces para callbacks
    public interface AuthorizationStateListener {
        void onAuthorizationStateChanged(String state, JSONObject data);
    }

    public interface UpdateListener {
        void onUpdate(String updateType, JSONObject data);
    }

    private AuthorizationStateListener authListener;
    private UpdateListener updateListener;
    private Context applicationContext;

    public TelegramClient(Context context) {
        this.applicationContext = context.getApplicationContext(); // Guardar contexto de aplicación
        this.mainHandler = new Handler(Looper.getMainLooper());
        initialize();
    }

    private void initialize() {
        try {
            nativeClientId = createNativeClient();
            Log.i(TAG, "TDLib client initialized with ID: " + nativeClientId);

            // Iniciar el hilo de respuestas
            startResponseThread();

            // Configurar TDLib
            setupTdLib();
        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "Failed to initialize native client due to UnsatisfiedLinkError. Ensure native libraries are correctly loaded.", e);
            // Aquí se podría re-lanzar una excepción o establecer un estado de error.
            // Por ahora, nativeClientId será 0 y las operaciones fallarán.
        }
    }

    private void setupTdLib() {
        try {
            // Configurar parámetros TDLib
            JSONObject setTdlibParams = new JSONObject();
            setTdlibParams.put("@type", "setTdlibParameters");

            JSONObject parameters = new JSONObject();
            parameters.put("use_test_dc", BuildVars.DEBUG_VERSION); // Usar DEBUG_VERSION como indicador
            parameters.put("api_id", BuildVars.APP_ID);
            parameters.put("api_hash", BuildVars.APP_HASH);
            parameters.put("database_directory", applicationContext.getFilesDir().getPath() + "/tdlib/db");
            parameters.put("files_directory", applicationContext.getFilesDir().getPath() + "/tdlib/files");
            parameters.put("use_file_database", true);
            parameters.put("use_chat_info_database", true);
            parameters.put("use_message_database", true);
            parameters.put("use_secret_chats", true); // Asumiendo que se soportarán chats secretos
            parameters.put("system_language_code", java.util.Locale.getDefault().getLanguage());
            parameters.put("device_model", android.os.Build.MODEL);
            parameters.put("system_version", android.os.Build.VERSION.RELEASE);
            parameters.put("application_version", BuildVars.BUILD_VERSION_STRING); // Usar de BuildVars
            parameters.put("enable_storage_optimizer", true);
            // Faltarían otros parámetros como 'database_encryption_key' si se usa.

            setTdlibParams.put("parameters", parameters);

            sendRequest(setTdlibParams.toString());

        } catch (JSONException e) {
            Log.e(TAG, "Error setting up TDLib parameters", e);
        }
    }

    private void startResponseThread() {
        if (nativeClientId == 0) {
            Log.e(TAG, "Native client not initialized, cannot start response thread.");
            return;
        }
        responseThread = new Thread(() -> {
            isRunning = true;
            while (isRunning && !Thread.currentThread().isInterrupted()) {
                try {
                    String response = receiveNativeResponse(nativeClientId, 1.0); // Timeout de 1 segundo
                    if (response != null && !response.isEmpty()) {
                        handleResponse(response);
                    }
                } catch (Exception e) {
                    if (isRunning) { // Solo loguear si no estamos cerrando
                        Log.e(TAG, "Error in response thread", e);
                    }
                }
            }
            Log.i(TAG, "Response thread finished.");
        });
        responseThread.setName("TDLibResponseThread");
        responseThread.start();
    }

    private void handleResponse(String response) {
        try {
            JSONObject jsonResponse = new JSONObject(response);
            String type = jsonResponse.optString("@type");

            // Log.d(TAG, "Received response: " + type + " DATA: " + response); // Loguear toda la respuesta puede ser útil

            switch (type) {
                case "updateAuthorizationState":
                    handleAuthorizationUpdate(jsonResponse);
                    break;
                // Añadir más casos para otros tipos de updates que se quieran manejar
                case "updateNewMessage":
                case "updateMessageContent":
                case "updateNewChat":
                // ... otros updates relevantes
                    handleUpdate(type, jsonResponse);
                    break;
                case "error":
                    Log.e(TAG, "TDLib error: " + jsonResponse.toString());
                    // Aquí se podría notificar a un listener de errores global
                    break;
                case "ok":
                    // Solicitud procesada correctamente sin un resultado específico
                    Log.d(TAG, "TDLib ok: " + jsonResponse.toString());
                    break;
                default:
                    // Para respuestas a getChats, sendMessage, etc., que tienen un @extra_id
                    if (jsonResponse.has("@extra")) {
                        // Este es un resultado de una solicitud específica, no un update general.
                        // En una implementación completa, se necesitaría un mapa de request_id a callbacks.
                        Log.d(TAG, "Received result for a request: " + response);
                    } else {
                        // Si no es un error, ok, ni un resultado con @extra, puede ser un update no manejado explícitamente.
                        Log.d(TAG, "Unhandled TDLib update/response type: " + type + " - " + response);
                        handleUpdate(type, jsonResponse); // O pasarlo a un listener genérico de updates
                    }
                    break;
            }

        } catch (JSONException e) {
            Log.e(TAG, "Error parsing response: " + response, e);
        }
    }

    private void handleAuthorizationUpdate(JSONObject update) {
        try {
            JSONObject authState = update.getJSONObject("authorization_state");
            String stateType = authState.getString("@type");

            Log.i(TAG, "Authorization state: " + stateType);

            if (authListener != null) {
                mainHandler.post(() -> authListener.onAuthorizationStateChanged(stateType, authState));
            }

        } catch (JSONException e) {
            Log.e(TAG, "Error handling authorization update", e);
        }
    }

    private void handleUpdate(String type, JSONObject updateData) {
        if (updateListener != null) {
            mainHandler.post(() -> updateListener.onUpdate(type, updateData));
        }
    }

    public long sendRequest(String requestJson) {
        if (nativeClientId == 0) {
            Log.e(TAG, "Cannot send request, native client not initialized.");
            return -1;
        }
        // En una implementación completa, se añadiría un @extra_id para rastrear respuestas.
        // long queryId = currentQueryId.getAndIncrement();
        // Podrías modificar requestJson para incluir {"@extra": queryId}
        // y luego mapear respuestas con ese @extra_id a callbacks específicos.
        sendNativeRequest(nativeClientId, requestJson);
        Log.d(TAG, "Sent request: " + requestJson);
        return 0; // Devolver un ID de consulta si se implementa el rastreo.
    }

    // Métodos de API de ejemplo (como en la guía)

    public void setAuthenticationPhoneNumber(String phoneNumber, JSONObject settings) {
        try {
            JSONObject request = new JSONObject();
            request.put("@type", "setAuthenticationPhoneNumber");
            request.put("phone_number", phoneNumber);
            if (settings != null) {
                request.put("settings", settings);
            }
            sendRequest(request.toString());
        } catch (JSONException e) {
            Log.e(TAG, "Error setting phone number", e);
        }
    }

    public void checkAuthenticationCode(String code) {
        try {
            JSONObject request = new JSONObject();
            request.put("@type", "checkAuthenticationCode");
            request.put("code", code);
            sendRequest(request.toString());
        } catch (JSONException e) {
            Log.e(TAG, "Error checking authentication code", e);
        }
    }

    public void checkAuthenticationPassword(String password) {
        try {
            JSONObject request = new JSONObject();
            request.put("@type", "checkAuthenticationPassword");
            request.put("password", password);
            sendRequest(request.toString());
        } catch (JSONException e) {
            Log.e(TAG, "Error checking authentication password", e);
        }
    }

    public void registerUser(String firstName, String lastName) {
        try {
            JSONObject request = new JSONObject();
            request.put("@type", "registerUser");
            request.put("first_name", firstName);
            request.put("last_name", lastName);
            sendRequest(request.toString());
        } catch (JSONException e) {
            Log.e(TAG, "Error registering user", e);
        }
    }


    public void sendMessage(long chatId, String messageText) {
        try {
            JSONObject messageContent = new JSONObject();
            messageContent.put("@type", "inputMessageText");

            JSONObject text = new JSONObject();
            text.put("@type", "formattedText");
            text.put("text", messageText);

            messageContent.put("text", text);

            JSONObject request = new JSONObject();
            request.put("@type", "sendMessage");
            request.put("chat_id", chatId);
            request.put("input_message_content", messageContent);

            sendRequest(request.toString());

        } catch (JSONException e) {
            Log.e(TAG, "Error sending message", e);
        }
    }

    public void getChats(String offsetOrder, long offsetChatId, int limit) {
        try {
            JSONObject request = new JSONObject();
            request.put("@type", "getChats");

            JSONObject chatList = new JSONObject();
            // Por defecto, obtiene la lista principal de chats. Se puede especificar otras listas.
            // chatList.put("@type", "chatListMain");
            // request.put("chat_list", chatList);

            request.put("offset_order", offsetOrder); // ej "9223372036854775807" para el inicio
            request.put("offset_chat_id", offsetChatId); // ej 0 para el inicio
            request.put("limit", limit);

            sendRequest(request.toString());

        } catch (JSONException e) {
            Log.e(TAG, "Error getting chats", e);
        }
    }

    public void setAuthorizationStateListener(AuthorizationStateListener listener) {
        this.authListener = listener;
    }

    public void setUpdateListener(UpdateListener listener) {
        this.updateListener = listener;
    }

    public void destroy() {
        Log.i(TAG, "Destroying TelegramClient...");
        isRunning = false; // Señal para que el hilo de respuesta termine
        if (responseThread != null) {
            responseThread.interrupt(); // Interrumpir si está bloqueado en receive
            try {
                responseThread.join(2000); // Esperar un poco a que termine
            } catch (InterruptedException e) {
                Log.e(TAG, "Response thread interruption during destroy", e);
                Thread.currentThread().interrupt();
            }
        }
        if (nativeClientId != 0) {
            destroyNativeClient(nativeClientId);
            nativeClientId = 0;
        }
        Log.i(TAG, "TelegramClient destroyed.");
    }

    // Método de ejemplo para ejecutar un comando síncrono (como getOption)
    public String execute(String commandJson) {
        if (nativeClientId == 0) { // Aunque execute no usa clientId, es bueno chequear
             Log.e(TAG, "Cannot execute command, native client not initialized.");
             return "";
        }
        return executeNativeCommand(commandJson);
    }

    public int getTdlibVersion() {
        if (nativeClientId == 0) return 0;
        return getLibraryVersion();
    }
}
