#include <jni.h>
#include "e2e_api.h"

void java_throw(JNIEnv *env, tde2e_api::Error error) {
    jclass exceptionCls = env->FindClass("java/lang/RuntimeException");
    if (exceptionCls != nullptr) {
        env->ThrowNew(exceptionCls, ("tde2e error: " + error.message).c_str());
    }
}

void java_throw(JNIEnv *env, std::string error) {
    jclass exceptionCls = env->FindClass("java/lang/RuntimeException");
    if (exceptionCls != nullptr) {
        env->ThrowNew(exceptionCls, ("tde2e bridge error: " + error).c_str());
    }
}

jbyteArray java_bytes(JNIEnv *env, const std::string& str) {
    jbyteArray byteArray = env->NewByteArray(str.size());
    if (byteArray == nullptr) {
        return nullptr;
    }
    env->SetByteArrayRegion(byteArray, 0, str.size(), reinterpret_cast<const jbyte*>(str.data()));
    return byteArray;
}

jobjectArray java_bytes2d(JNIEnv *env, const std::vector<std::string>& arr) {
    jobjectArray array = env->NewObjectArray(arr.size(), env->FindClass("[B"), nullptr);
    for (size_t i = 0; i < arr.size(); ++i) {
        auto bytes = java_bytes(env, arr[i]);
        env->SetObjectArrayElement(array, i, bytes);
        env->DeleteLocalRef(bytes);
    }
    return array;
}

extern "C"
JNIEXPORT jlong JNICALL
Java_org_telegram_messenger_voip_ConferenceCall_key_1generate_1temporary_1private_1key(JNIEnv *env, jclass clazz) {
    auto result = tde2e_api::key_generate_temporary_private_key();
    if (!result.is_ok()) {
        java_throw(env, result.error());
        return 0L;
    }
    return result.value();
}

extern "C" JNIEXPORT jbyteArray Java_org_telegram_messenger_voip_ConferenceCall_key_1to_1public_1key(JNIEnv *env, jclass clazz, jlong private_key) {
    auto result = tde2e_api::key_to_public_key(private_key);
    if (!result.is_ok()) {
        java_throw(env, result.error());
        return 0L;
    }
    return java_bytes(env, result.value());
}

extern "C"
JNIEXPORT jlong JNICALL
Java_org_telegram_messenger_voip_ConferenceCall_key_1from_1public_1key(JNIEnv *env, jclass clazz,
                                                                       jbyteArray public_key) {
    jsize length = env->GetArrayLength(public_key);
    jbyte* bytes = env->GetByteArrayElements(public_key, nullptr);
    std::string_view view(reinterpret_cast<const char*>(bytes), length);
    auto result = tde2e_api::key_from_public_key(view);
    env->ReleaseByteArrayElements(public_key, bytes, JNI_ABORT);
    if (!result.is_ok()) {
        java_throw(env, result.error());
        return 0L;
    }
    return result.value();
}

jobject java_CallParticipant(JNIEnv* env, tde2e_api::CallParticipant o) {
    const jclass cls = env->FindClass("org/telegram/messenger/voip/ConferenceCall$CallParticipant");
    jmethodID constructor = env->GetMethodID(cls, "<init>", "()V");

    jobject obj = env->NewObject(cls, constructor);
    env->SetLongField(obj, env->GetFieldID(cls, "user_id", "J"), o.user_id);
    env->SetLongField(obj, env->GetFieldID(cls, "public_key_id", "J"), o.public_key_id);
    env->SetIntField(obj, env->GetFieldID(cls, "permissions", "I"), o.permissions);

    return obj;
}

tde2e_api::CallParticipant jni_CallParticipant(JNIEnv* env, jobject o) {
    const jclass cls = env->FindClass("org/telegram/messenger/voip/ConferenceCall$CallParticipant");

    return {
        env->GetLongField(o, env->GetFieldID(cls, "user_id", "J")),
        env->GetLongField(o, env->GetFieldID(cls, "public_key_id", "J")),
        env->GetIntField(o, env->GetFieldID(cls, "permissions", "I"))
    };
}

jobject java_CallState(JNIEnv* env, tde2e_api::CallState o) {
    const jclass cls = env->FindClass("org/telegram/messenger/voip/ConferenceCall$CallState");
    const jmethodID constructor = env->GetMethodID(cls, "<init>", "()V");

    const jobject obj = env->NewObject(cls, constructor);
    env->SetIntField(obj, env->GetFieldID(cls, "height", "I"), o.height);

    const jobjectArray array = env->NewObjectArray(
        o.participants.size(),
        env->FindClass("org/telegram/messenger/voip/ConferenceCall$CallParticipant"),
        nullptr
    );
    for (jsize i = 0; i < o.participants.size(); ++i) {
        const auto participant = java_CallParticipant(env, o.participants[i]);
        env->SetObjectArrayElement(array, i, participant);
        env->DeleteLocalRef(participant);
    }
    env->SetObjectField(obj, env->GetFieldID(cls, "participants", "[Lorg/telegram/messenger/voip/ConferenceCall$CallParticipant;"), array);

    return obj;
}

tde2e_api::CallState jni_CallState(JNIEnv* env, jobject o) {
    const jclass cls = env->FindClass("org/telegram/messenger/voip/ConferenceCall$CallState");

    const jobjectArray array = (jobjectArray) env->GetObjectField(o, env->GetFieldID(cls, "participants", "[Lorg/telegram/messenger/voip/ConferenceCall$CallParticipant;"));
    const int arraySize = env->GetArrayLength(array);
    std::vector<tde2e_api::CallParticipant> participants;
    for (int i = 0; i < arraySize; ++i) {
        participants.push_back(jni_CallParticipant(env, env->GetObjectArrayElement(array, i)));
    }

    return {
        env->GetIntField(o, env->GetFieldID(cls, "height", "I")),
        participants
    };
}

extern "C"
JNIEXPORT jbyteArray JNICALL
Java_org_telegram_messenger_voip_ConferenceCall_call_1create_1zero_1block(JNIEnv *env, jclass clazz,
                                                                          jlong private_key,
                                                                          jobject initial_state) {
    auto result = tde2e_api::call_create_zero_block(private_key, jni_CallState(env, initial_state));
    if (!result.is_ok()) {
        java_throw(env, result.error());
        return nullptr;
    }
    return java_bytes(env, result.value());
}

extern "C"
JNIEXPORT jbyteArray JNICALL
Java_org_telegram_messenger_voip_ConferenceCall_call_1create_1self_1add_1block(JNIEnv *env, jclass clazz,
                                                                          jlong private_key_id,
                                                                          jbyteArray previous_block,
                                                                          jobject self) {
    jsize length = env->GetArrayLength(previous_block);
    jbyte* bytes = env->GetByteArrayElements(previous_block, nullptr);
    std::string_view view(reinterpret_cast<const char*>(bytes), length);
    auto result = tde2e_api::call_create_self_add_block(private_key_id, view, jni_CallParticipant(env, self));
    env->ReleaseByteArrayElements(previous_block, bytes, JNI_ABORT);
    if (!result.is_ok()) {
        java_throw(env, result.error());
        return nullptr;
    }
    return java_bytes(env, result.value());
}

extern "C"
JNIEXPORT jlong JNICALL
Java_org_telegram_messenger_voip_ConferenceCall_call_1create(JNIEnv *env, jclass clazz,
                                                             jlong user_id,
                                                             jlong private_key_id,
                                                             jbyteArray last_block) {
    jsize length = env->GetArrayLength(last_block);
    jbyte* bytes = env->GetByteArrayElements(last_block, nullptr);
    std::string_view view(reinterpret_cast<const char*>(bytes), length);
    auto result = tde2e_api::call_create(user_id, private_key_id, view);
    env->ReleaseByteArrayElements(last_block, bytes, JNI_ABORT);
    if (!result.is_ok()) {
        java_throw(env, result.error());
        return 0;
    }
    return result.value();
}

extern "C"
JNIEXPORT jbyteArray JNICALL
Java_org_telegram_messenger_voip_ConferenceCall_call_1create_1change_1state_1block(JNIEnv *env,
                                                                                   jclass clazz,
                                                                                   jlong call_id,
                                                                                   jobject new_state) {
    auto result = tde2e_api::call_create_change_state_block(call_id, jni_CallState(env, new_state));
    if (!result.is_ok()) {
        java_throw(env, result.error());
        return 0;
    }
    return java_bytes(env, result.value());
}

extern "C"
JNIEXPORT jint JNICALL
Java_org_telegram_messenger_voip_ConferenceCall_call_1get_1height(JNIEnv *env, jclass clazz,
                                                                  jlong call_id) {
    auto result = tde2e_api::call_get_height(call_id);
    if (!result.is_ok()) {
        java_throw(env, result.error());
        return 0;
    }
    return result.value();
}

extern "C"
JNIEXPORT jobject JNICALL
Java_org_telegram_messenger_voip_ConferenceCall_call_1apply_1block(JNIEnv *env, jclass clazz,
                                                                   jlong call_id,
                                                                   jbyteArray block) {
    jsize length = env->GetArrayLength(block);
    jbyte* bytes = env->GetByteArrayElements(block, nullptr);
    std::string_view view(reinterpret_cast<const char*>(bytes), length);
    auto result = tde2e_api::call_apply_block(call_id, view);
    env->ReleaseByteArrayElements(block, bytes, JNI_ABORT);
    if (!result.is_ok()) {
        java_throw(env, result.error());
        return 0;
    }
    return java_CallState(env, result.value());
}

extern "C"
JNIEXPORT jobject JNICALL
Java_org_telegram_messenger_voip_ConferenceCall_call_1get_1state(JNIEnv *env, jclass clazz,
                                                                 jlong call_id) {
    auto result = tde2e_api::call_get_state(call_id);
    if (!result.is_ok()) {
        java_throw(env, result.error());
        return 0;
    }
    return java_CallState(env, result.value());
}

jobject java_CallVerificationState(JNIEnv* env, tde2e_api::CallVerificationState o) {
    const jclass cls = env->FindClass("org/telegram/messenger/voip/ConferenceCall$CallVerificationState");
    const jmethodID constructor = env->GetMethodID(cls, "<init>", "()V");

    const jobject obj = env->NewObject(cls, constructor);
    env->SetIntField(obj, env->GetFieldID(cls, "height", "I"), o.height);

    if (o.emoji_hash) {
        jbyteArray arr = java_bytes(env, o.emoji_hash.value());
        env->SetObjectField(obj, env->GetFieldID(cls, "emoji_hash", "[B"), arr);
        env->DeleteLocalRef(arr);
    } else {
        env->SetObjectField(obj, env->GetFieldID(cls, "emoji_hash", "[B"), (jbyteArray) nullptr); // set as null
    }
    env->DeleteLocalRef(cls);

    return obj;
}

extern "C"
JNIEXPORT jobject JNICALL
Java_org_telegram_messenger_voip_ConferenceCall_call_1get_1verification_1state(JNIEnv *env,
                                                                               jclass clazz,
                                                                               jlong call_id) {
    auto result = tde2e_api::call_get_verification_state(call_id);
    if (!result.is_ok()) {
        java_throw(env, result.error());
        return 0;
    }
    return java_CallVerificationState(env, result.value());
}

extern "C"
JNIEXPORT jobject JNICALL
Java_org_telegram_messenger_voip_ConferenceCall_call_1receive_1inbound_1message(JNIEnv *env,
                                                                                jclass clazz,
                                                                                jlong call_id,
                                                                                jbyteArray message) {
    jsize length = env->GetArrayLength(message);
    jbyte* bytes = env->GetByteArrayElements(message, nullptr);
    std::string_view view(reinterpret_cast<const char*>(bytes), length);
    auto result = tde2e_api::call_receive_inbound_message(call_id, view);
    env->ReleaseByteArrayElements(message, bytes, JNI_ABORT);
    if (!result.is_ok()) {
        java_throw(env, result.error());
        return 0;
    }
    return java_CallVerificationState(env, result.value());
}

extern "C"
JNIEXPORT jobjectArray JNICALL
Java_org_telegram_messenger_voip_ConferenceCall_call_1pull_1outbound_1messages(JNIEnv *env,
                                                                               jclass clazz,
                                                                               jlong call_id) {
    auto result = tde2e_api::call_pull_outbound_messages(call_id);
    if (!result.is_ok()) {
        java_throw(env, result.error());
        return 0;
    }
    return java_bytes2d(env, result.value());
}

jobject java_CallVerificationWords(JNIEnv* env, tde2e_api::CallVerificationWords o) {
    const jclass cls = env->FindClass("org/telegram/messenger/voip/ConferenceCall$CallVerificationWords");
    const jmethodID constructor = env->GetMethodID(cls, "<init>", "()V");

    const jobject obj = env->NewObject(cls, constructor);
    env->SetIntField(obj, env->GetFieldID(cls, "height", "I"), o.height);

    const jclass stringClass = env->FindClass("java/lang/String");
    const jobjectArray wordArray = env->NewObjectArray(o.words.size(), stringClass, nullptr);
    for (size_t i = 0; i < o.words.size(); ++i) {
        const jstring jstr = env->NewStringUTF(o.words[i].c_str());
        env->SetObjectArrayElement(wordArray, i, jstr);
        env->DeleteLocalRef(jstr);
    }
    env->SetObjectField(obj, env->GetFieldID(cls, "words", "[Ljava/lang/String;"), wordArray);
    env->DeleteLocalRef(wordArray);
    env->DeleteLocalRef(cls);

    return obj;
}

extern "C"
JNIEXPORT jobject JNICALL
Java_org_telegram_messenger_voip_ConferenceCall_call_1get_1verification_1words(JNIEnv *env,
                                                                               jclass clazz,
                                                                               jlong call_id) {
    auto result = tde2e_api::call_get_verification_words(call_id);
    if (!result.is_ok()) {
        java_throw(env, result.error());
        return nullptr;
    }
    return java_CallVerificationWords(env, result.value());
}

extern "C"
JNIEXPORT void JNICALL
Java_org_telegram_messenger_voip_ConferenceCall_call_1destroy(JNIEnv *env, jclass clazz,
                                                              jlong call_id) {
    auto result = tde2e_api::call_destroy(call_id);
    if (!result.is_ok()) {
        java_throw(env, result.error());
    }
}

extern "C"
JNIEXPORT void JNICALL
Java_org_telegram_messenger_voip_ConferenceCall_call_1destroy_1all(JNIEnv *env, jclass clazz) {
    auto result = tde2e_api::call_destroy_all();
    if (!result.is_ok()) {
        java_throw(env, result.error());
    }
}

jstring java_string(JNIEnv* env, const std::string& s) {
    jobject bb = env->NewDirectByteBuffer((void*) s.data(), s.size());

    jclass cls_Charset = env->FindClass("java/nio/charset/Charset");
    jmethodID mid_Charset_forName = env->GetStaticMethodID(cls_Charset, "forName", "(Ljava/lang/String;)Ljava/nio/charset/Charset;");
    jobject charset = env->CallStaticObjectMethod(cls_Charset, mid_Charset_forName, env->NewStringUTF("UTF-8"));

    jmethodID mid_Charset_decode = env->GetMethodID(cls_Charset, "decode", "(Ljava/nio/ByteBuffer;)Ljava/nio/CharBuffer;");
    jobject cb = env->CallObjectMethod(charset, mid_Charset_decode, bb);
    env->DeleteLocalRef(bb);

    jclass cls_CharBuffer = env->FindClass("java/nio/CharBuffer");
    jmethodID mid_CharBuffer_toString = env->GetMethodID(cls_CharBuffer, "toString", "()Ljava/lang/String;");
    return (jstring) env->CallObjectMethod(cb, mid_CharBuffer_toString);
}

extern "C"
JNIEXPORT jstring JNICALL
Java_org_telegram_messenger_voip_ConferenceCall_call_1describe(JNIEnv *env, jclass clazz,
                                                                      jlong call_id) {
    auto result = tde2e_api::call_describe(call_id);
    if (!result.is_ok()) {
        java_throw(env, result.error());
        return nullptr;
    }
    return java_string(env, result.value());
}

extern "C"
JNIEXPORT jstring JNICALL
Java_org_telegram_messenger_voip_ConferenceCall_call_1describe_1block(JNIEnv *env, jclass clazz,
                                                                      jbyteArray block) {
    jsize length = env->GetArrayLength(block);
    jbyte* bytes = env->GetByteArrayElements(block, nullptr);
    std::string_view view(reinterpret_cast<const char*>(bytes), length);
    auto result = tde2e_api::call_describe_block(view);
    env->ReleaseByteArrayElements(block, bytes, JNI_ABORT);
    if (!result.is_ok()) {
        java_throw(env, result.error());
        return nullptr;
    }
    return java_string(env, result.value());
}

extern "C"
JNIEXPORT jstring JNICALL
Java_org_telegram_messenger_voip_ConferenceCall_call_1describe_1message(JNIEnv *env, jclass clazz,
                                                                        jbyteArray message) {
    jsize length = env->GetArrayLength(message);
    jbyte* bytes = env->GetByteArrayElements(message, nullptr);
    std::string_view view(reinterpret_cast<const char*>(bytes), length);
    auto result = tde2e_api::call_describe_message(view);
    env->ReleaseByteArrayElements(message, bytes, JNI_ABORT);
    if (!result.is_ok()) {
        java_throw(env, result.error());
        return nullptr;
    }
    return java_string(env, result.value());
}