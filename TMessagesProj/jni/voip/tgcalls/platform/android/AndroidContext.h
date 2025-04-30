#ifndef TGCALLS_ANDROID_CONTEXT_H
#define TGCALLS_ANDROID_CONTEXT_H

#include "PlatformContext.h"

#include <jni.h>
#include <voip/tgcalls/group/GroupInstanceImpl.h>

namespace tgcalls {

class AndroidContext final : public PlatformContext {
public:
    AndroidContext(JNIEnv *env, jobject peerInstance, jobject groupInstance, bool screencast);
    ~AndroidContext() override;

    jobject getJavaCapturer();
    jobject getJavaPeerInstance();
    jobject getJavaGroupInstance();
    jclass getJavaCapturerClass();

    void setJavaPeerInstance(JNIEnv *env, jobject instance);
    void setJavaGroupInstance(JNIEnv *env, jobject instance);

    std::vector<std::shared_ptr<BroadcastPartTask>> audioStreamTasks;
    std::vector<std::shared_ptr<BroadcastPartTask>> videoStreamTasks;
    std::vector<std::shared_ptr<RequestMediaChannelDescriptionTask>> descriptionTasks;

private:
    jclass VideoCapturerDeviceClass = nullptr;
    jobject javaCapturer = nullptr;

    jobject javaPeerInstance = nullptr;
    jobject javaGroupInstance = nullptr;

};

} // namespace tgcalls

#endif
