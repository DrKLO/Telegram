#include <jni.h>
#include <vector>
#include <string>
#include "tgnet/TLSHello.h"

static const size_t kHelloBufferSize = 8192;

extern "C" JNIEXPORT jbyteArray JNICALL
Java_org_telegram_tgnet_ConnectionsManager_nativeTestGenerateClientHello(JNIEnv *env, jclass, jstring domain) {
    const char *chars = env->GetStringUTFChars(domain, nullptr);
    std::string domainStr(chars != nullptr ? chars : "");
    if (chars != nullptr) {
        env->ReleaseStringUTFChars(domain, chars);
    }

    // getDefault() returns a const singleton, writeToBuffer() is non-const — take a copy.
    TLSHello hello = TLSHello::getDefault();
    hello.setDomain(domainStr);

    std::vector<uint8_t> buffer(kHelloBufferSize, 0);
    uint32_t size = hello.writeToBuffer(buffer.data());

    jbyteArray result = env->NewByteArray(static_cast<jsize>(size));
    if (result == nullptr) {
        return nullptr;
    }
    env->SetByteArrayRegion(result, 0, static_cast<jsize>(size),
                            reinterpret_cast<const jbyte *>(buffer.data()));
    return result;
}