/*
 *  Copyright 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "sdk/android/src/jni/pc/peer_connection_factory.h"

#include <memory>
#include <utility>

#include "absl/memory/memory.h"
#include "api/video_codecs/video_decoder_factory.h"
#include "api/video_codecs/video_encoder_factory.h"
#include "media/base/media_engine.h"
#include "modules/audio_device/include/audio_device.h"
#include "modules/utility/include/jvm_android.h"
// We don't depend on the audio processing module implementation.
// The user may pass in a nullptr.
#include "api/call/call_factory_interface.h"
#include "api/rtc_event_log/rtc_event_log_factory.h"
#include "api/task_queue/default_task_queue_factory.h"
#include "api/video_codecs/video_decoder_factory.h"
#include "api/video_codecs/video_encoder_factory.h"
#include "media/engine/webrtc_media_engine.h"
#include "modules/audio_device/include/audio_device.h"
#include "modules/audio_processing/include/audio_processing.h"
#include "rtc_base/event_tracer.h"
#include "rtc_base/physical_socket_server.h"
#include "rtc_base/system/thread_registry.h"
#include "rtc_base/thread.h"
#include "sdk/android/generated_peerconnection_jni/PeerConnectionFactory_jni.h"
#include "sdk/android/native_api/jni/java_types.h"
#include "sdk/android/native_api/stacktrace/stacktrace.h"
#include "sdk/android/src/jni/jni_helpers.h"
#include "sdk/android/src/jni/logging/log_sink.h"
#include "sdk/android/src/jni/pc/android_network_monitor.h"
#include "sdk/android/src/jni/pc/audio.h"
#include "sdk/android/src/jni/pc/ice_candidate.h"
#include "sdk/android/src/jni/pc/owned_factory_and_threads.h"
#include "sdk/android/src/jni/pc/peer_connection.h"
#include "sdk/android/src/jni/pc/ssl_certificate_verifier_wrapper.h"
#include "sdk/android/src/jni/pc/video.h"
#include "system_wrappers/include/field_trial.h"

namespace webrtc {
namespace jni {

namespace {

// Take ownership of the jlong reference and cast it into an rtc::scoped_refptr.
template <typename T>
rtc::scoped_refptr<T> TakeOwnershipOfRefPtr(jlong j_pointer) {
  T* ptr = reinterpret_cast<T*>(j_pointer);
  rtc::scoped_refptr<T> refptr;
  refptr.swap(&ptr);
  return refptr;
}

// Take ownership of the jlong reference and cast it into a std::unique_ptr.
template <typename T>
std::unique_ptr<T> TakeOwnershipOfUniquePtr(jlong native_pointer) {
  return std::unique_ptr<T>(reinterpret_cast<T*>(native_pointer));
}

typedef void (*JavaMethodPointer)(JNIEnv*, const JavaRef<jobject>&);

// Post a message on the given thread that will call the Java method on the
// given Java object.
void PostJavaCallback(JNIEnv* env,
                      rtc::Thread* queue,
                      const rtc::Location& posted_from,
                      const JavaRef<jobject>& j_object,
                      JavaMethodPointer java_method_pointer) {
  // One-off message handler that calls the Java method on the specified Java
  // object before deleting itself.
  class JavaAsyncCallback : public rtc::MessageHandler {
   public:
    JavaAsyncCallback(JNIEnv* env,
                      const JavaRef<jobject>& j_object,
                      JavaMethodPointer java_method_pointer)
        : j_object_(env, j_object), java_method_pointer_(java_method_pointer) {}

    void OnMessage(rtc::Message*) override {
      java_method_pointer_(AttachCurrentThreadIfNeeded(), j_object_);
      // The message has been delivered, clean up after ourself.
      delete this;
    }

   private:
    ScopedJavaGlobalRef<jobject> j_object_;
    JavaMethodPointer java_method_pointer_;
  };

  queue->Post(posted_from,
              new JavaAsyncCallback(env, j_object, java_method_pointer));
}

absl::optional<PeerConnectionFactoryInterface::Options>
JavaToNativePeerConnectionFactoryOptions(JNIEnv* jni,
                                         const JavaRef<jobject>& j_options) {
  if (j_options.is_null())
    return absl::nullopt;

  PeerConnectionFactoryInterface::Options native_options;

  // This doesn't necessarily match the c++ version of this struct; feel free
  // to add more parameters as necessary.
  native_options.network_ignore_mask =
      Java_Options_getNetworkIgnoreMask(jni, j_options);
  native_options.disable_encryption =
      Java_Options_getDisableEncryption(jni, j_options);
  native_options.disable_network_monitor =
      Java_Options_getDisableNetworkMonitor(jni, j_options);

  return native_options;
}

// Place static objects into a container that gets leaked so we avoid
// non-trivial destructor.
struct StaticObjectContainer {
  // Field trials initialization string
  std::unique_ptr<std::string> field_trials_init_string;
  // Set in PeerConnectionFactory_InjectLoggable().
  std::unique_ptr<JNILogSink> jni_log_sink;
};

StaticObjectContainer& GetStaticObjects() {
  static StaticObjectContainer* static_objects = new StaticObjectContainer();
  return *static_objects;
}

ScopedJavaLocalRef<jobject> NativeToScopedJavaPeerConnectionFactory(
    JNIEnv* env,
    rtc::scoped_refptr<webrtc::PeerConnectionFactoryInterface> pcf,
    std::unique_ptr<rtc::SocketFactory> socket_factory,
    std::unique_ptr<rtc::Thread> network_thread,
    std::unique_ptr<rtc::Thread> worker_thread,
    std::unique_ptr<rtc::Thread> signaling_thread) {
  OwnedFactoryAndThreads* owned_factory = new OwnedFactoryAndThreads(
      std::move(socket_factory), std::move(network_thread),
      std::move(worker_thread), std::move(signaling_thread), pcf);

  ScopedJavaLocalRef<jobject> j_pcf = Java_PeerConnectionFactory_Constructor(
      env, NativeToJavaPointer(owned_factory));

  PostJavaCallback(env, owned_factory->network_thread(), RTC_FROM_HERE, j_pcf,
                   &Java_PeerConnectionFactory_onNetworkThreadReady);
  PostJavaCallback(env, owned_factory->worker_thread(), RTC_FROM_HERE, j_pcf,
                   &Java_PeerConnectionFactory_onWorkerThreadReady);
  PostJavaCallback(env, owned_factory->signaling_thread(), RTC_FROM_HERE, j_pcf,
                   &Java_PeerConnectionFactory_onSignalingThreadReady);

  return j_pcf;
}

PeerConnectionFactoryInterface* PeerConnectionFactoryFromJava(jlong j_p) {
  return reinterpret_cast<OwnedFactoryAndThreads*>(j_p)->factory();
}

}  // namespace

// Note: Some of the video-specific PeerConnectionFactory methods are
// implemented in "video.cc". This is done so that if an application
// doesn't need video support, it can just link with "null_video.cc"
// instead of "video.cc", which doesn't bring in the video-specific
// dependencies.

// Set in PeerConnectionFactory_initializeAndroidGlobals().
static bool factory_static_initialized = false;

jobject NativeToJavaPeerConnectionFactory(
    JNIEnv* jni,
    rtc::scoped_refptr<webrtc::PeerConnectionFactoryInterface> pcf,
    std::unique_ptr<rtc::SocketFactory> socket_factory,
    std::unique_ptr<rtc::Thread> network_thread,
    std::unique_ptr<rtc::Thread> worker_thread,
    std::unique_ptr<rtc::Thread> signaling_thread) {
  return NativeToScopedJavaPeerConnectionFactory(
             jni, pcf, std::move(socket_factory), std::move(network_thread),
             std::move(worker_thread), std::move(signaling_thread))
      .Release();
}

static void JNI_PeerConnectionFactory_InitializeAndroidGlobals(JNIEnv* jni) {
  if (!factory_static_initialized) {
    JVM::Initialize(GetJVM());
    factory_static_initialized = true;
  }
}

static void JNI_PeerConnectionFactory_InitializeFieldTrials(
    JNIEnv* jni,
    const JavaParamRef<jstring>& j_trials_init_string) {
  std::unique_ptr<std::string>& field_trials_init_string =
      GetStaticObjects().field_trials_init_string;

  if (j_trials_init_string.is_null()) {
    field_trials_init_string = nullptr;
    field_trial::InitFieldTrialsFromString(nullptr);
    return;
  }
  field_trials_init_string = std::make_unique<std::string>(
      JavaToNativeString(jni, j_trials_init_string));
  RTC_LOG(LS_INFO) << "initializeFieldTrials: " << *field_trials_init_string;
  field_trial::InitFieldTrialsFromString(field_trials_init_string->c_str());
}

static void JNI_PeerConnectionFactory_InitializeInternalTracer(JNIEnv* jni) {
  rtc::tracing::SetupInternalTracer();
}

static ScopedJavaLocalRef<jstring>
JNI_PeerConnectionFactory_FindFieldTrialsFullName(
    JNIEnv* jni,
    const JavaParamRef<jstring>& j_name) {
  return NativeToJavaString(
      jni, field_trial::FindFullName(JavaToStdString(jni, j_name)));
}

static jboolean JNI_PeerConnectionFactory_StartInternalTracingCapture(
    JNIEnv* jni,
    const JavaParamRef<jstring>& j_event_tracing_filename) {
  if (j_event_tracing_filename.is_null())
    return false;

  const char* init_string =
      jni->GetStringUTFChars(j_event_tracing_filename.obj(), NULL);
  RTC_LOG(LS_INFO) << "Starting internal tracing to: " << init_string;
  bool ret = rtc::tracing::StartInternalCapture(init_string);
  jni->ReleaseStringUTFChars(j_event_tracing_filename.obj(), init_string);
  return ret;
}

static void JNI_PeerConnectionFactory_StopInternalTracingCapture(JNIEnv* jni) {
  rtc::tracing::StopInternalCapture();
}

static void JNI_PeerConnectionFactory_ShutdownInternalTracer(JNIEnv* jni) {
  rtc::tracing::ShutdownInternalTracer();
}

// Following parameters are optional:
// `audio_device_module`, `jencoder_factory`, `jdecoder_factory`,
// `audio_processor`, `fec_controller_factory`,
// `network_state_predictor_factory`, `neteq_factory`.
ScopedJavaLocalRef<jobject> CreatePeerConnectionFactoryForJava(
    JNIEnv* jni,
    const JavaParamRef<jobject>& jcontext,
    const JavaParamRef<jobject>& joptions,
    rtc::scoped_refptr<AudioDeviceModule> audio_device_module,
    rtc::scoped_refptr<AudioEncoderFactory> audio_encoder_factory,
    rtc::scoped_refptr<AudioDecoderFactory> audio_decoder_factory,
    const JavaParamRef<jobject>& jencoder_factory,
    const JavaParamRef<jobject>& jdecoder_factory,
    rtc::scoped_refptr<AudioProcessing> audio_processor,
    std::unique_ptr<FecControllerFactoryInterface> fec_controller_factory,
    std::unique_ptr<NetworkControllerFactoryInterface>
        network_controller_factory,
    std::unique_ptr<NetworkStatePredictorFactoryInterface>
        network_state_predictor_factory,
    std::unique_ptr<NetEqFactory> neteq_factory) {
  // talk/ assumes pretty widely that the current Thread is ThreadManager'd, but
  // ThreadManager only WrapCurrentThread()s the thread where it is first
  // created.  Since the semantics around when auto-wrapping happens in
  // webrtc/rtc_base/ are convoluted, we simply wrap here to avoid having to
  // think about ramifications of auto-wrapping there.
  rtc::ThreadManager::Instance()->WrapCurrentThread();

  auto socket_server = std::make_unique<rtc::PhysicalSocketServer>();
  auto network_thread = std::make_unique<rtc::Thread>(socket_server.get());
  network_thread->SetName("network_thread", nullptr);
  RTC_CHECK(network_thread->Start()) << "Failed to start thread";

  std::unique_ptr<rtc::Thread> worker_thread = rtc::Thread::Create();
  worker_thread->SetName("worker_thread", nullptr);
  RTC_CHECK(worker_thread->Start()) << "Failed to start thread";

  std::unique_ptr<rtc::Thread> signaling_thread = rtc::Thread::Create();
  signaling_thread->SetName("signaling_thread", NULL);
  RTC_CHECK(signaling_thread->Start()) << "Failed to start thread";

  const absl::optional<PeerConnectionFactoryInterface::Options> options =
      JavaToNativePeerConnectionFactoryOptions(jni, joptions);

  PeerConnectionFactoryDependencies dependencies;
  // TODO(bugs.webrtc.org/13145): Also add socket_server.get() to the
  // dependencies.
  dependencies.network_thread = network_thread.get();
  dependencies.worker_thread = worker_thread.get();
  dependencies.signaling_thread = signaling_thread.get();
  dependencies.task_queue_factory = CreateDefaultTaskQueueFactory();
  dependencies.call_factory = CreateCallFactory();
  dependencies.event_log_factory = std::make_unique<RtcEventLogFactory>(
      dependencies.task_queue_factory.get());
  dependencies.fec_controller_factory = std::move(fec_controller_factory);
  dependencies.network_controller_factory =
      std::move(network_controller_factory);
  dependencies.network_state_predictor_factory =
      std::move(network_state_predictor_factory);
  dependencies.neteq_factory = std::move(neteq_factory);
  if (!(options && options->disable_network_monitor)) {
    dependencies.network_monitor_factory =
        std::make_unique<AndroidNetworkMonitorFactory>();
  }

  cricket::MediaEngineDependencies media_dependencies;
  media_dependencies.task_queue_factory = dependencies.task_queue_factory.get();
  media_dependencies.adm = std::move(audio_device_module);
  media_dependencies.audio_encoder_factory = std::move(audio_encoder_factory);
  media_dependencies.audio_decoder_factory = std::move(audio_decoder_factory);
  media_dependencies.audio_processing = std::move(audio_processor);
  media_dependencies.video_encoder_factory =
      absl::WrapUnique(CreateVideoEncoderFactory(jni, jencoder_factory));
  media_dependencies.video_decoder_factory =
      absl::WrapUnique(CreateVideoDecoderFactory(jni, jdecoder_factory));
  dependencies.media_engine =
      cricket::CreateMediaEngine(std::move(media_dependencies));

  rtc::scoped_refptr<PeerConnectionFactoryInterface> factory =
      CreateModularPeerConnectionFactory(std::move(dependencies));

  RTC_CHECK(factory) << "Failed to create the peer connection factory; "
                        "WebRTC/libjingle init likely failed on this device";
  // TODO(honghaiz): Maybe put the options as the argument of
  // CreatePeerConnectionFactory.
  if (options)
    factory->SetOptions(*options);

  return NativeToScopedJavaPeerConnectionFactory(
      jni, factory, std::move(socket_server), std::move(network_thread),
      std::move(worker_thread), std::move(signaling_thread));
}

static ScopedJavaLocalRef<jobject>
JNI_PeerConnectionFactory_CreatePeerConnectionFactory(
    JNIEnv* jni,
    const JavaParamRef<jobject>& jcontext,
    const JavaParamRef<jobject>& joptions,
    jlong native_audio_device_module,
    jlong native_audio_encoder_factory,
    jlong native_audio_decoder_factory,
    const JavaParamRef<jobject>& jencoder_factory,
    const JavaParamRef<jobject>& jdecoder_factory,
    jlong native_audio_processor,
    jlong native_fec_controller_factory,
    jlong native_network_controller_factory,
    jlong native_network_state_predictor_factory,
    jlong native_neteq_factory) {
  rtc::scoped_refptr<AudioProcessing> audio_processor(
      reinterpret_cast<AudioProcessing*>(native_audio_processor));
  return CreatePeerConnectionFactoryForJava(
      jni, jcontext, joptions,
      rtc::scoped_refptr<AudioDeviceModule>(
          reinterpret_cast<AudioDeviceModule*>(native_audio_device_module)),
      TakeOwnershipOfRefPtr<AudioEncoderFactory>(native_audio_encoder_factory),
      TakeOwnershipOfRefPtr<AudioDecoderFactory>(native_audio_decoder_factory),
      jencoder_factory, jdecoder_factory,
      audio_processor ? audio_processor : CreateAudioProcessing(),
      TakeOwnershipOfUniquePtr<FecControllerFactoryInterface>(
          native_fec_controller_factory),
      TakeOwnershipOfUniquePtr<NetworkControllerFactoryInterface>(
          native_network_controller_factory),
      TakeOwnershipOfUniquePtr<NetworkStatePredictorFactoryInterface>(
          native_network_state_predictor_factory),
      TakeOwnershipOfUniquePtr<NetEqFactory>(native_neteq_factory));
}

static void JNI_PeerConnectionFactory_FreeFactory(JNIEnv*,
                                                  jlong j_p) {
  delete reinterpret_cast<OwnedFactoryAndThreads*>(j_p);
  field_trial::InitFieldTrialsFromString(nullptr);
  GetStaticObjects().field_trials_init_string = nullptr;
}

static jlong JNI_PeerConnectionFactory_CreateLocalMediaStream(
    JNIEnv* jni,
    jlong native_factory,
    const JavaParamRef<jstring>& label) {
  rtc::scoped_refptr<MediaStreamInterface> stream(
      PeerConnectionFactoryFromJava(native_factory)
          ->CreateLocalMediaStream(JavaToStdString(jni, label)));
  return jlongFromPointer(stream.release());
}

static jlong JNI_PeerConnectionFactory_CreateAudioSource(
    JNIEnv* jni,
    jlong native_factory,
    const JavaParamRef<jobject>& j_constraints) {
  std::unique_ptr<MediaConstraints> constraints =
      JavaToNativeMediaConstraints(jni, j_constraints);
  cricket::AudioOptions options;
  CopyConstraintsIntoAudioOptions(constraints.get(), &options);
  rtc::scoped_refptr<AudioSourceInterface> source(
      PeerConnectionFactoryFromJava(native_factory)
          ->CreateAudioSource(options));
  return jlongFromPointer(source.release());
}

jlong JNI_PeerConnectionFactory_CreateAudioTrack(
    JNIEnv* jni,
    jlong native_factory,
    const JavaParamRef<jstring>& id,
    jlong native_source) {
  rtc::scoped_refptr<AudioTrackInterface> track(
      PeerConnectionFactoryFromJava(native_factory)
          ->CreateAudioTrack(
              JavaToStdString(jni, id),
              reinterpret_cast<AudioSourceInterface*>(native_source)));
  return jlongFromPointer(track.release());
}

static jboolean JNI_PeerConnectionFactory_StartAecDump(
    JNIEnv* jni,
    jlong native_factory,
    jint file_descriptor,
    jint filesize_limit_bytes) {
  FILE* f = fdopen(file_descriptor, "wb");
  if (!f) {
    close(file_descriptor);
    return false;
  }

  return PeerConnectionFactoryFromJava(native_factory)
      ->StartAecDump(f, filesize_limit_bytes);
}

static void JNI_PeerConnectionFactory_StopAecDump(JNIEnv* jni,
                                                  jlong native_factory) {
  PeerConnectionFactoryFromJava(native_factory)->StopAecDump();
}

static jlong JNI_PeerConnectionFactory_CreatePeerConnection(
    JNIEnv* jni,
    jlong factory,
    const JavaParamRef<jobject>& j_rtc_config,
    const JavaParamRef<jobject>& j_constraints,
    jlong observer_p,
    const JavaParamRef<jobject>& j_sslCertificateVerifier) {
  std::unique_ptr<PeerConnectionObserver> observer(
      reinterpret_cast<PeerConnectionObserver*>(observer_p));

  PeerConnectionInterface::RTCConfiguration rtc_config(
      PeerConnectionInterface::RTCConfigurationType::kAggressive);
  JavaToNativeRTCConfiguration(jni, j_rtc_config, &rtc_config);

  if (rtc_config.certificates.empty()) {
    // Generate non-default certificate.
    rtc::KeyType key_type = GetRtcConfigKeyType(jni, j_rtc_config);
    if (key_type != rtc::KT_DEFAULT) {
      rtc::scoped_refptr<rtc::RTCCertificate> certificate =
          rtc::RTCCertificateGenerator::GenerateCertificate(
              rtc::KeyParams(key_type), absl::nullopt);
      if (!certificate) {
        RTC_LOG(LS_ERROR) << "Failed to generate certificate. KeyType: "
                          << key_type;
        return 0;
      }
      rtc_config.certificates.push_back(certificate);
    }
  }

  std::unique_ptr<MediaConstraints> constraints;
  if (!j_constraints.is_null()) {
    constraints = JavaToNativeMediaConstraints(jni, j_constraints);
    CopyConstraintsIntoRtcConfiguration(constraints.get(), &rtc_config);
  }

  PeerConnectionDependencies peer_connection_dependencies(observer.get());
  if (!j_sslCertificateVerifier.is_null()) {
    peer_connection_dependencies.tls_cert_verifier =
        std::make_unique<SSLCertificateVerifierWrapper>(
            jni, j_sslCertificateVerifier);
  }

  auto result =
      PeerConnectionFactoryFromJava(factory)->CreatePeerConnectionOrError(
          rtc_config, std::move(peer_connection_dependencies));
  if (!result.ok())
    return 0;

  return jlongFromPointer(new OwnedPeerConnection(
      result.MoveValue(), std::move(observer), std::move(constraints)));
}

static jlong JNI_PeerConnectionFactory_CreateVideoSource(
    JNIEnv* jni,
    jlong native_factory,
    jboolean is_screencast,
    jboolean align_timestamps) {
  OwnedFactoryAndThreads* factory =
      reinterpret_cast<OwnedFactoryAndThreads*>(native_factory);
  return jlongFromPointer(CreateVideoSource(jni, factory->signaling_thread(),
                                            factory->worker_thread(),
                                            is_screencast, align_timestamps));
}

static jlong JNI_PeerConnectionFactory_CreateVideoTrack(
    JNIEnv* jni,
    jlong native_factory,
    const JavaParamRef<jstring>& id,
    jlong native_source) {
  rtc::scoped_refptr<VideoTrackInterface> track =
      PeerConnectionFactoryFromJava(native_factory)
          ->CreateVideoTrack(
              JavaToStdString(jni, id),
              reinterpret_cast<VideoTrackSourceInterface*>(native_source));
  return jlongFromPointer(track.release());
}

static jlong JNI_PeerConnectionFactory_GetNativePeerConnectionFactory(
    JNIEnv* jni,
    jlong native_factory) {
  return jlongFromPointer(PeerConnectionFactoryFromJava(native_factory));
}

static void JNI_PeerConnectionFactory_InjectLoggable(
    JNIEnv* jni,
    const JavaParamRef<jobject>& j_logging,
    jint nativeSeverity) {
  std::unique_ptr<JNILogSink>& jni_log_sink = GetStaticObjects().jni_log_sink;

  // If there is already a LogSink, remove it from LogMessage.
  if (jni_log_sink) {
    rtc::LogMessage::RemoveLogToStream(jni_log_sink.get());
  }
  jni_log_sink = std::make_unique<JNILogSink>(jni, j_logging);
  rtc::LogMessage::AddLogToStream(
      jni_log_sink.get(), static_cast<rtc::LoggingSeverity>(nativeSeverity));
  rtc::LogMessage::LogToDebug(rtc::LS_NONE);
}

static void JNI_PeerConnectionFactory_DeleteLoggable(JNIEnv* jni) {
  std::unique_ptr<JNILogSink>& jni_log_sink = GetStaticObjects().jni_log_sink;

  if (jni_log_sink) {
    rtc::LogMessage::RemoveLogToStream(jni_log_sink.get());
    jni_log_sink.reset();
  }
}

static void JNI_PeerConnectionFactory_PrintStackTrace(JNIEnv* env, jint tid) {
  RTC_LOG(LS_WARNING) << StackTraceToString(GetStackTrace(tid));
}

static void JNI_PeerConnectionFactory_PrintStackTracesOfRegisteredThreads(
    JNIEnv* env) {
  PrintStackTracesOfRegisteredThreads();
}

}  // namespace jni
}  // namespace webrtc
