/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include <android/log.h>
#include <android/native_window.h>
#include <android/native_window_jni.h>
#include <jni.h>
#include <stdlib.h>

#include <cstring>
#include <new>

#include <libyuv.h>
#include <libyuv/scale.h>

extern "C" {
#ifdef __cplusplus
#define __STDC_CONSTANT_MACROS
#ifdef _STDINT_H
#undef _STDINT_H
#endif
#include <stdint.h>
#endif

#include <libavcodec/avcodec.h>
#include <libavutil/channel_layout.h>
#include <libavutil/error.h>
#include <libavutil/mem.h>
#include <libavutil/opt.h>
#include <libswresample/swresample.h>
#include <libswscale/swscale.h>
}

#define LOG_TAG "ffmpeg_jni"
#define LOGE(...) \
  ((void)__android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__))
#define LOGW(...) \
  ((void)__android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__))
#define LOGD(...) \
  ((void)__android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__))

#define LIBRARY_FUNC(RETURN_TYPE, NAME, ...)                               \
  extern "C" {                                                             \
  JNIEXPORT RETURN_TYPE                                                    \
  Java_androidx_media3_decoder_ffmpeg_FfmpegLibrary_##NAME(JNIEnv* env,    \
                                                           jobject thiz,   \
                                                           ##__VA_ARGS__); \
  }                                                                        \
  JNIEXPORT RETURN_TYPE                                                    \
  Java_androidx_media3_decoder_ffmpeg_FfmpegLibrary_##NAME(                \
      JNIEnv* env, jobject thiz, ##__VA_ARGS__)

#define AUDIO_DECODER_FUNC(RETURN_TYPE, NAME, ...)               \
  extern "C" {                                                   \
  JNIEXPORT RETURN_TYPE                                          \
  Java_androidx_media3_decoder_ffmpeg_FfmpegAudioDecoder_##NAME( \
      JNIEnv* env, jobject thiz, ##__VA_ARGS__);                 \
  }                                                              \
  JNIEXPORT RETURN_TYPE                                          \
  Java_androidx_media3_decoder_ffmpeg_FfmpegAudioDecoder_##NAME( \
      JNIEnv* env, jobject thiz, ##__VA_ARGS__)

#define VIDEO_DECODER_FUNC(RETURN_TYPE, NAME, ...)                               \
  extern "C" {                                                                   \
  JNIEXPORT RETURN_TYPE                                                          \
  Java_androidx_media3_decoder_ffmpeg_ExperimentalFfmpegVideoDecoder_##NAME(     \
      JNIEnv* env, jobject thiz, ##__VA_ARGS__);                                 \
  }                                                                              \
  JNIEXPORT RETURN_TYPE                                                          \
  Java_androidx_media3_decoder_ffmpeg_ExperimentalFfmpegVideoDecoder_##NAME(     \
      JNIEnv* env, jobject thiz, ##__VA_ARGS__)

#define ERROR_STRING_BUFFER_LENGTH 256

// Output format corresponding to AudioFormat.ENCODING_PCM_16BIT.
static const AVSampleFormat OUTPUT_FORMAT_PCM_16BIT = AV_SAMPLE_FMT_S16;
// Output format corresponding to AudioFormat.ENCODING_PCM_FLOAT.
static const AVSampleFormat OUTPUT_FORMAT_PCM_FLOAT = AV_SAMPLE_FMT_FLT;

// LINT.IfChange
static const int AUDIO_DECODER_ERROR_INVALID_DATA = -1;
static const int AUDIO_DECODER_ERROR_OTHER = -2;
// LINT.ThenChange(../java/androidx/media3/decoder/ffmpeg/FfmpegAudioDecoder.java)

// LINT.IfChange
static const int VIDEO_DECODER_ERROR_SURFACE = -4;
static const int VIDEO_DECODER_SUCCESS = 0;
static const int VIDEO_DECODER_ERROR_INVALID_DATA = -1;
static const int VIDEO_DECODER_ERROR_OTHER = -2;
static const int VIDEO_DECODER_ERROR_READ_FRAME = -3;
// LINT.ThenChange(../java/androidx/media3/decoder/ffmpeg/ExperimentalFfmpegVideoDecoder.java)

static jmethodID growOutputBufferMethod;

/**
 * Returns the AVCodec with the specified name, or NULL if it is not available.
 */
const AVCodec* getCodecByName(JNIEnv* env, jstring codecName);

/**
 * Allocates and opens a new AVCodecContext for the specified codec, passing the
 * provided extraData as initialization data for the decoder if it is non-NULL.
 * Returns the created context.
 */
AVCodecContext* createContext(JNIEnv* env,
                              const AVCodec* codec,
                              jbyteArray extraData,
                              jboolean outputFloat,
                              jint rawSampleRate,
                              jint rawChannelCount);

struct GrowOutputBufferCallback {
    uint8_t* operator()(int requiredSize) const;
    JNIEnv* env;
    jobject thiz;
    jobject decoderOutputBuffer;
};

/**
 * Decodes the packet into the output buffer, returning the number of bytes
 * written, or a negative AUDIO_DECODER_ERROR constant value in the case of an
 * error.
 */
int decodePacket(AVCodecContext* context,
                 AVPacket* packet,
                 uint8_t* outputBuffer,
                 int outputSize,
                 GrowOutputBufferCallback growBuffer);

/**
 * Transforms ffmpeg AVERROR into a negative AUDIO_DECODER_ERROR constant value.
 */
int transformError(int errorNumber);

/**
 * Outputs a log message describing the avcodec error number.
 */
void logError(const char* functionName, int errorNumber);

/**
 * Releases the specified context.
 */
void releaseContext(AVCodecContext* context);

jstring ffmpegGetVersion(JNIEnv* env, jobject thiz) {
    return env->NewStringUTF(LIBAVCODEC_IDENT);
}

jint ffmpegGetInputBufferPaddingSize(JNIEnv* env, jobject thiz) {
    return static_cast<jint>(AV_INPUT_BUFFER_PADDING_SIZE);
}

jboolean ffmpegHasDecoder(JNIEnv* env, jobject thiz, jstring codecName) {
    return getCodecByName(env, codecName) != NULL;
}

jstring ffmpegGetAv1DecoderName(JNIEnv* env, jobject thiz) {
    if (avcodec_find_decoder_by_name("libdav1d")) {
        return env->NewStringUTF("libdav1d");
    }
    if (avcodec_find_decoder_by_name("libaom-av1")) {
        return env->NewStringUTF("libaom-av1");
    }
    return NULL;
}

jlong ffmpegInitialize(JNIEnv* env,
                       jobject thiz,
                       jstring codecName,
                       jbyteArray extraData,
                       jboolean outputFloat,
                       jint rawSampleRate,
                       jint rawChannelCount) {
    const AVCodec* codec = getCodecByName(env, codecName);
    if (!codec) {
        LOGE("Codec not found.");
        return 0L;
    }
    return reinterpret_cast<jlong>(
            createContext(env, codec, extraData, outputFloat, rawSampleRate,
                          rawChannelCount));
}

jint ffmpegDecode(JNIEnv* env,
                  jobject thiz,
                  jlong context,
                  jobject inputData,
                  jint inputSize,
                  jobject decoderOutputBuffer,
                  jobject outputData,
                  jint outputSize) {
    if (!context) {
        LOGE("Context must be non-NULL.");
        return -1;
    }
    if (!inputData || !decoderOutputBuffer || !outputData) {
        LOGE("Input and output buffers must be non-NULL.");
        return -1;
    }
    if (inputSize < 0) {
        LOGE("Invalid input buffer size: %d.", inputSize);
        return -1;
    }
    if (outputSize < 0) {
        LOGE("Invalid output buffer length: %d", outputSize);
        return -1;
    }

    uint8_t* inputBuffer =
            static_cast<uint8_t*>(env->GetDirectBufferAddress(inputData));
    uint8_t* outputBuffer =
            static_cast<uint8_t*>(env->GetDirectBufferAddress(outputData));

    AVPacket* packet = av_packet_alloc();
    if (!packet) {
        LOGE("Failed to allocate packet.");
        return -1;
    }

    packet->data = inputBuffer;
    packet->size = inputSize;

    const int ret =
            decodePacket(reinterpret_cast<AVCodecContext*>(context), packet,
                         outputBuffer, outputSize,
                         GrowOutputBufferCallback{env, thiz, decoderOutputBuffer});
    av_packet_free(&packet);
    return ret;
}

uint8_t* GrowOutputBufferCallback::operator()(int requiredSize) const {
    jobject newOutputData = env->CallObjectMethod(
            thiz, growOutputBufferMethod, decoderOutputBuffer, requiredSize);
    if (env->ExceptionCheck()) {
        LOGE("growOutputBuffer() failed");
        env->ExceptionDescribe();
        return nullptr;
    }
    return static_cast<uint8_t*>(env->GetDirectBufferAddress(newOutputData));
}

jint ffmpegGetChannelCount(JNIEnv* env, jobject thiz, jlong context) {
    if (!context) {
        LOGE("Context must be non-NULL.");
        return -1;
    }
    return reinterpret_cast<AVCodecContext*>(context)->ch_layout.nb_channels;
}

jint ffmpegGetSampleRate(JNIEnv* env, jobject thiz, jlong context) {
    if (!context) {
        LOGE("Context must be non-NULL.");
        return -1;
    }
    return reinterpret_cast<AVCodecContext*>(context)->sample_rate;
}

jlong ffmpegReset(JNIEnv* env,
                  jobject thiz,
                  jlong jContext,
                  jbyteArray extraData) {
    AVCodecContext* context = reinterpret_cast<AVCodecContext*>(jContext);
    if (!context) {
        LOGE("Tried to reset without a context.");
        return 0L;
    }

    AVCodecID codecId = context->codec_id;
    if (codecId == AV_CODEC_ID_TRUEHD) {
        jboolean outputFloat =
                static_cast<jboolean>(context->request_sample_fmt == OUTPUT_FORMAT_PCM_FLOAT);

        // Release and recreate the context if the codec is TrueHD.
        // TODO: Figure out why flushing doesn't work for this codec.
        releaseContext(context);

        const AVCodec* codec = avcodec_find_decoder(codecId);
        if (!codec) {
            LOGE("Unexpected error finding codec %d.", codecId);
            return 0L;
        }

        return reinterpret_cast<jlong>(
                createContext(env, codec, extraData, outputFloat,
                        /* rawSampleRate= */ -1,
                        /* rawChannelCount= */ -1));
    }

    avcodec_flush_buffers(context);
    return reinterpret_cast<jlong>(context);
}

void ffmpegRelease(JNIEnv* env, jobject thiz, jlong context) {
    if (context) {
        releaseContext(reinterpret_cast<AVCodecContext*>(context));
    }
}

const AVCodec* getCodecByName(JNIEnv* env, jstring codecName) {
    if (!codecName) {
        return NULL;
    }

    const char* codecNameChars = env->GetStringUTFChars(codecName, NULL);
    if (!codecNameChars) {
        return NULL;
    }

    const AVCodec* codec = avcodec_find_decoder_by_name(codecNameChars);
    env->ReleaseStringUTFChars(codecName, codecNameChars);
    return codec;
}

AVCodecContext* createContext(JNIEnv* env,
                              const AVCodec* codec,
                              jbyteArray extraData,
                              jboolean outputFloat,
                              jint rawSampleRate,
                              jint rawChannelCount) {
    AVCodecContext* context = avcodec_alloc_context3(codec);
    if (!context) {
        LOGE("Failed to allocate context.");
        return NULL;
    }

    context->request_sample_fmt =
            outputFloat ? OUTPUT_FORMAT_PCM_FLOAT : OUTPUT_FORMAT_PCM_16BIT;

    if (extraData) {
        const jsize size = env->GetArrayLength(extraData);
        context->extradata_size = size;

        // FFmpeg requires AV_INPUT_BUFFER_PADDING_SIZE zero bytes after extradata.
        context->extradata = static_cast<uint8_t*>(
                av_mallocz(static_cast<size_t>(size) + AV_INPUT_BUFFER_PADDING_SIZE));
        if (!context->extradata) {
            LOGE("Failed to allocate extradata.");
            releaseContext(context);
            return NULL;
        }

        env->GetByteArrayRegion(
                extraData, 0, size, reinterpret_cast<jbyte*>(context->extradata));
        if (env->ExceptionCheck()) {
            releaseContext(context);
            return NULL;
        }
    }

    if (context->codec_id == AV_CODEC_ID_PCM_MULAW ||
        context->codec_id == AV_CODEC_ID_PCM_ALAW) {
        context->sample_rate = rawSampleRate;
        av_channel_layout_default(&context->ch_layout, rawChannelCount);
    }

    context->err_recognition = AV_EF_IGNORE_ERR;

    const int result = avcodec_open2(context, codec, NULL);
    if (result < 0) {
        logError("avcodec_open2", result);
        releaseContext(context);
        return NULL;
    }

    return context;
}

int decodePacket(AVCodecContext* context,
                 AVPacket* packet,
                 uint8_t* outputBuffer,
                 int outputSize,
                 GrowOutputBufferCallback growBuffer) {
    int result = avcodec_send_packet(context, packet);
    if (result) {
        logError("avcodec_send_packet", result);
        return transformError(result);
    }

    int outSize = 0;
    while (true) {
        AVFrame* frame = av_frame_alloc();
        if (!frame) {
            LOGE("Failed to allocate output frame.");
            return AUDIO_DECODER_ERROR_INVALID_DATA;
        }

        result = avcodec_receive_frame(context, frame);
        if (result) {
            av_frame_free(&frame);
            if (result == AVERROR(EAGAIN)) {
                break;
            }
            logError("avcodec_receive_frame", result);
            return transformError(result);
        }

        AVSampleFormat sampleFormat = context->sample_fmt;
        int channelCount = context->ch_layout.nb_channels;
        int sampleRate = context->sample_rate;
        int sampleCount = frame->nb_samples;

        SwrContext* resampleContext =
                static_cast<SwrContext*>(context->opaque);
        if (!resampleContext) {
            result =
                    swr_alloc_set_opts2(&resampleContext,
                                        &context->ch_layout,
                                        context->request_sample_fmt,
                                        sampleRate,
                                        &context->ch_layout,
                                        sampleFormat,
                                        sampleRate,
                                        0,
                                        NULL);
            if (result < 0) {
                logError("swr_alloc_set_opts2", result);
                av_frame_free(&frame);
                return transformError(result);
            }

            result = swr_init(resampleContext);
            if (result < 0) {
                logError("swr_init", result);
                swr_free(&resampleContext);
                av_frame_free(&frame);
                return transformError(result);
            }

            context->opaque = resampleContext;
        }

        const int outSampleSize =
                av_get_bytes_per_sample(context->request_sample_fmt);
        const int outSamples =
                swr_get_out_samples(resampleContext, sampleCount);
        const int bufferOutSize =
                outSampleSize * channelCount * outSamples;

        if (outSize + bufferOutSize > outputSize) {
            LOGD(
                    "Output buffer size (%d) too small for output data (%d), "
                    "reallocating buffer.",
                    outputSize, outSize + bufferOutSize);

            outputSize = outSize + bufferOutSize;
            outputBuffer = growBuffer(outputSize);
            if (!outputBuffer) {
                LOGE("Failed to reallocate output buffer.");
                av_frame_free(&frame);
                return AUDIO_DECODER_ERROR_OTHER;
            }
        }

        result = swr_convert(
                resampleContext,
                &outputBuffer,
                bufferOutSize,
                const_cast<const uint8_t**>(frame->data),
                frame->nb_samples);

        av_frame_free(&frame);

        if (result < 0) {
            logError("swr_convert", result);
            return AUDIO_DECODER_ERROR_INVALID_DATA;
        }

        const int available = swr_get_out_samples(resampleContext, 0);
        if (available != 0) {
            LOGE("Expected no samples remaining after resampling, but found %d.",
                 available);
            return AUDIO_DECODER_ERROR_INVALID_DATA;
        }

        outputBuffer += bufferOutSize;
        outSize += bufferOutSize;
    }

    return outSize;
}

int transformError(int errorNumber) {
    return errorNumber == AVERROR_INVALIDDATA
           ? AUDIO_DECODER_ERROR_INVALID_DATA
           : AUDIO_DECODER_ERROR_OTHER;
}

void logError(const char* functionName, int errorNumber) {
    char buffer[ERROR_STRING_BUFFER_LENGTH];
    av_strerror(errorNumber, buffer, ERROR_STRING_BUFFER_LENGTH);
    LOGE("Error in %s: %s", functionName, buffer);
}

void releaseContext(AVCodecContext* context) {
    if (!context) {
        return;
    }

    SwrContext* swrContext =
            static_cast<SwrContext*>(context->opaque);
    if (swrContext) {
        swr_free(&swrContext);
        context->opaque = NULL;
    }

    avcodec_free_context(&context);
}

// -----------------------------------------------------------------------------
// Video
// -----------------------------------------------------------------------------

// Android YUV format. See:
// https://developer.android.com/reference/android/graphics/ImageFormat.html#YV12.
static const int kImageFormatYV12 = 0x32315659;

struct JniContext {
    ~JniContext() {
        if (native_window) {
            ANativeWindow_release(native_window);
            native_window = nullptr;
        }
    }

    bool MaybeAcquireNativeWindow(JNIEnv* env, jobject new_surface) {
        if (!new_surface) {
            LOGE("Surface must be non-null.");
            return false;
        }

        if (surface != nullptr &&
            native_window != nullptr &&
            env->IsSameObject(surface, new_surface)) {
            return true;
        }

        if (native_window) {
            ANativeWindow_release(native_window);
            native_window = nullptr;
        }

        native_window_width = 0;
        native_window_height = 0;
        native_window_format = 0;

        if (surface != nullptr) {
            env->DeleteGlobalRef(surface);
            surface = nullptr;
        }

        native_window = ANativeWindow_fromSurface(env, new_surface);
        if (!native_window) {
            LOGE("ANativeWindow_fromSurface failed.");
            return false;
        }

        surface = env->NewGlobalRef(new_surface);
        if (!surface) {
            LOGE("NewGlobalRef(Surface) failed.");
            ANativeWindow_release(native_window);
            native_window = nullptr;
            return false;
        }

        return true;
    }

    jfieldID data_field = nullptr;
    jfieldID width_field = nullptr;
    jfieldID height_field = nullptr;
    jfieldID pts_field = nullptr;
    jfieldID colorspace_field = nullptr;
    jfieldID y_stride_field = nullptr;
    jfieldID uv_stride_field = nullptr;
    jmethodID init_for_yuv_frame_method = nullptr;

    AVCodecContext* codecContext = nullptr;
    SwsContext* swsContext = nullptr;

    ANativeWindow* native_window = nullptr;
    jobject surface = nullptr;

    int rotate_degree = 0;
    int native_window_width = 0;
    int native_window_height = 0;
    int native_window_format = 0;
    bool use_rgba_fallback = false;
};

constexpr int AlignTo16(int value) {
    return (value + 15) & ~15;
}

/**
 * Converts AVFrame color space to VideoDecoderOutputBuffer#COLORSPACE_*.
 */
constexpr int cvt_colorspace(AVColorSpace colorSpace) {
    switch (colorSpace) {
        case AVCOL_SPC_BT470BG:
        case AVCOL_SPC_SMPTE170M:
        case AVCOL_SPC_SMPTE240M:
            return 1;  // COLORSPACE_BT601
        case AVCOL_SPC_BT709:
            return 2;  // COLORSPACE_BT709
        case AVCOL_SPC_BT2020_NCL:
        case AVCOL_SPC_BT2020_CL:
            return 3;  // COLORSPACE_BT2020
        default:
            return 0;  // COLORSPACE_UNKNOWN
    }
}

const int* cvt_colorspace_coefficients(AVColorSpace colorSpace) {
    switch (colorSpace) {
        case AVCOL_SPC_BT709:
            return sws_getCoefficients(SWS_CS_ITU709);

        case AVCOL_SPC_BT2020_NCL:
        case AVCOL_SPC_BT2020_CL:
            return sws_getCoefficients(SWS_CS_BT2020);

        case AVCOL_SPC_BT470BG:
        case AVCOL_SPC_SMPTE170M:
        case AVCOL_SPC_SMPTE240M:
        default:
            return sws_getCoefficients(SWS_CS_ITU601);
    }
}

/**
 * Converts a decoded frame to the requested pixel format and size.
 */
AVFrame* cvt_format(JniContext* jniContext,
                    AVFrame* src,
                    AVPixelFormat dst_format,
                    int dst_width,
                    int dst_height) {
    if (!jniContext || !src || dst_width <= 0 || dst_height <= 0) {
        LOGE("Invalid arguments for pixel format conversion.");
        return nullptr;
    }

    const AVPixelFormat src_format =
            static_cast<AVPixelFormat>(src->format);

    SwsContext* swsContext =
            sws_getCachedContext(
                    jniContext->swsContext,
                    src->width,
                    src->height,
                    src_format,
                    dst_width,
                    dst_height,
                    dst_format,
                    SWS_BILINEAR,
                    NULL,
                    NULL,
                    NULL);

    if (!swsContext) {
        LOGE("Failed to allocate swsContext.");
        return nullptr;
    }

    jniContext->swsContext = swsContext;

    // Preserve the decoded frame's matrix/range while changing bit depth/format.
    const int src_range =
            src->color_range == AVCOL_RANGE_JPEG ? 1 : 0;
    const int* coefficients =
            cvt_colorspace_coefficients(src->colorspace);

    const int colorspace_result =
            sws_setColorspaceDetails(
                    swsContext,
                    coefficients,
                    src_range,
                    coefficients,
                    src_range,
                    0,
                    1 << 16,
                    1 << 16);

    if (colorspace_result < 0) {
        logError("sws_setColorspaceDetails", colorspace_result);
        return nullptr;
    }

    AVFrame* dst = av_frame_alloc();
    if (!dst) {
        LOGE("Failed to allocate converted AVFrame.");
        return nullptr;
    }

    const int props_result = av_frame_copy_props(dst, src);
    if (props_result < 0) {
        logError("av_frame_copy_props", props_result);
        av_frame_free(&dst);
        return nullptr;
    }

    dst->width = dst_width;
    dst->height = dst_height;
    dst->format = dst_format;

    const int alloc_result = av_frame_get_buffer(dst, 0);
    if (alloc_result < 0) {
        logError("av_frame_get_buffer", alloc_result);
        av_frame_free(&dst);
        return nullptr;
    }

    const int scale_result =
            sws_scale(
                    swsContext,
                    src->data,
                    src->linesize,
                    0,
                    src->height,
                    dst->data,
                    dst->linesize);

    if (scale_result <= 0) {
        if (scale_result < 0) {
            logError("sws_scale", scale_result);
        } else {
            LOGE("sws_scale produced no output.");
        }
        av_frame_free(&dst);
        return nullptr;
    }

    return dst;
}

libyuv::RotationMode cvt_rotate(int degree) {
    switch (degree) {
        case 90:
            return libyuv::kRotate90;
        case 180:
            return libyuv::kRotate180;
        case 270:
            return libyuv::kRotate270;
        default:
            return libyuv::kRotate0;
    }
}

JniContext* createVideoContext(JNIEnv* env,
                               const AVCodec* codec,
                               jbyteArray extraData,
                               jint threads,
                               jint degree,
                               jint width,
                               jint height) {
    JniContext* jniContext = new (std::nothrow) JniContext();
    if (!jniContext) {
        LOGE("Failed to allocate JniContext.");
        return NULL;
    }

    AVCodecContext* codecContext = avcodec_alloc_context3(codec);
    if (!codecContext) {
        LOGE("Failed to allocate video codec context.");
        delete jniContext;
        return NULL;
    }

    jniContext->rotate_degree = degree;

    if (extraData) {
        const jsize size = env->GetArrayLength(extraData);
        codecContext->extradata_size = size;

        // FFmpeg requires zeroed padding after extradata.
        codecContext->extradata = static_cast<uint8_t*>(
                av_mallocz(static_cast<size_t>(size) + AV_INPUT_BUFFER_PADDING_SIZE));
        if (!codecContext->extradata) {
            LOGE("Failed to allocate video extradata.");
            releaseContext(codecContext);
            delete jniContext;
            return NULL;
        }

        env->GetByteArrayRegion(
                extraData, 0, size,
                reinterpret_cast<jbyte*>(codecContext->extradata));
        if (env->ExceptionCheck()) {
            releaseContext(codecContext);
            delete jniContext;
            return NULL;
        }
    }

    codecContext->skip_loop_filter = AVDISCARD_DEFAULT;
    codecContext->skip_frame = AVDISCARD_DEFAULT;
    codecContext->thread_count = threads;
    codecContext->thread_type = FF_THREAD_FRAME;
    codecContext->err_recognition = AV_EF_IGNORE_ERR;

    AVDictionary* opts = NULL;

    if (codec->id == AV_CODEC_ID_AV1 &&
        strcmp(codec->name, "libdav1d") == 0) {
        av_dict_set(&opts, "max_frame_delay", "1", 0);
    }

    if (width > 0 && height > 0) {
        codecContext->width = width;
        codecContext->height = height;
    }

    const int result = avcodec_open2(codecContext, codec, &opts);
    av_dict_free(&opts);

    if (result < 0) {
        logError("avcodec_open2", result);
        releaseContext(codecContext);
        delete jniContext;
        return NULL;
    }

    jniContext->codecContext = codecContext;

    const jclass outputBufferClass =
            env->FindClass("androidx/media3/decoder/VideoDecoderOutputBuffer");
    if (!outputBufferClass) {
        LOGE("Failed to find VideoDecoderOutputBuffer class.");
        releaseContext(codecContext);
        jniContext->codecContext = nullptr;
        delete jniContext;
        return NULL;
    }

    jniContext->data_field =
            env->GetFieldID(
                    outputBufferClass, "data", "Ljava/nio/ByteBuffer;");
    jniContext->width_field =
            env->GetFieldID(outputBufferClass, "width", "I");
    jniContext->height_field =
            env->GetFieldID(outputBufferClass, "height", "I");
    jniContext->pts_field =
            env->GetFieldID(outputBufferClass, "timeUs", "J");
    jniContext->colorspace_field =
            env->GetFieldID(outputBufferClass, "colorspace", "I");
    jniContext->y_stride_field =
            env->GetFieldID(outputBufferClass, "yStride", "I");
    jniContext->uv_stride_field =
            env->GetFieldID(outputBufferClass, "uvStride", "I");
    jniContext->init_for_yuv_frame_method =
            env->GetMethodID(
                    outputBufferClass, "initForYuvFrame", "(IIIII)Z");

    const bool fields_ok =
            jniContext->data_field &&
            jniContext->width_field &&
            jniContext->height_field &&
            jniContext->pts_field &&
            jniContext->colorspace_field &&
            jniContext->y_stride_field &&
            jniContext->uv_stride_field &&
            jniContext->init_for_yuv_frame_method;

    env->DeleteLocalRef(outputBufferClass);

    if (!fields_ok || env->ExceptionCheck()) {
        LOGE("Failed to resolve VideoDecoderOutputBuffer JNI members.");
        releaseContext(codecContext);
        jniContext->codecContext = nullptr;
        delete jniContext;
        return NULL;
    }

    return jniContext;
}

VIDEO_DECODER_FUNC(
        jlong,
        ffmpegInitialize,
        jstring codecName,
        jbyteArray extraData,
        jint threads,
        jint degree,
        jint width,
        jint height) {
    const AVCodec* codec = getCodecByName(env, codecName);
    if (!codec) {
        LOGE("Codec not found.");
        return 0L;
    }

    return reinterpret_cast<jlong>(
            createVideoContext(
                    env, codec, extraData, threads, degree, width, height));
}

VIDEO_DECODER_FUNC(jlong, ffmpegReset, jlong jContext) {
    JniContext* const jniContext =
            reinterpret_cast<JniContext*>(jContext);
    if (!jniContext || !jniContext->codecContext) {
        LOGE("Tried to reset without a context.");
        return 0L;
    }

    avcodec_flush_buffers(jniContext->codecContext);
    return reinterpret_cast<jlong>(jniContext);
}

VIDEO_DECODER_FUNC(void, ffmpegRelease, jlong jContext) {
    JniContext* const jniContext =
            reinterpret_cast<JniContext*>(jContext);
    if (!jniContext) {
        return;
    }

    if (jniContext->codecContext) {
        avcodec_free_context(&jniContext->codecContext);
    }

    if (jniContext->swsContext) {
        sws_freeContext(jniContext->swsContext);
        jniContext->swsContext = nullptr;
    }

    if (jniContext->surface != nullptr) {
        env->DeleteGlobalRef(jniContext->surface);
        jniContext->surface = nullptr;
    }

    // ~JniContext releases ANativeWindow.
    delete jniContext;
}

VIDEO_DECODER_FUNC(
        jint,
        ffmpegSendPacket,
        jlong jContext,
        jobject encodedData,
        jint length,
        jlong inputTimeUs) {
    JniContext* const jniContext =
            reinterpret_cast<JniContext*>(jContext);

    if (!jniContext ||
        !jniContext->codecContext ||
        !encodedData ||
        length < 0) {
        LOGE("Invalid context or input buffer.");
        return VIDEO_DECODER_ERROR_OTHER;
    }

    uint8_t* inputBuffer =
            static_cast<uint8_t*>(
                    env->GetDirectBufferAddress(encodedData));
    const jlong inputCapacity =
            env->GetDirectBufferCapacity(encodedData);

    if (!inputBuffer ||
        inputCapacity < 0 ||
        inputCapacity < static_cast<jlong>(length)) {
        LOGE("Invalid direct input buffer: capacity=%lld length=%d",
             static_cast<long long>(inputCapacity), length);
        return VIDEO_DECODER_ERROR_OTHER;
    }

    AVPacket* packet = av_packet_alloc();
    if (!packet) {
        LOGE("Failed to allocate packet.");
        return VIDEO_DECODER_ERROR_OTHER;
    }

    // Do not point AVPacket directly at Java memory.
    //
    // The normal DecoderInputBuffer is padded, but PendingInput in the PR is
    // copied into a direct ByteBuffer whose capacity is exactly the payload
    // length. FFmpeg parsers may legally read AV_INPUT_BUFFER_PADDING_SIZE bytes
    // past packet->data + packet->size. av_new_packet() allocates that padding
    // and zeroes it.
    const int allocationResult = av_new_packet(packet, length);
    if (allocationResult < 0) {
        logError("av_new_packet", allocationResult);
        av_packet_free(&packet);
        return VIDEO_DECODER_ERROR_OTHER;
    }

    if (length > 0) {
        std::memcpy(
                packet->data,
                inputBuffer,
                static_cast<size_t>(length));
    }

    packet->pts = inputTimeUs;

    const int result =
            avcodec_send_packet(jniContext->codecContext, packet);

    av_packet_free(&packet);

    if (result == 0) {
        return VIDEO_DECODER_SUCCESS;
    }

    if (result == AVERROR_INVALIDDATA) {
        logError("avcodec_send_packet", result);
        return VIDEO_DECODER_ERROR_INVALID_DATA;
    }

    if (result == AVERROR(EAGAIN)) {
        return VIDEO_DECODER_ERROR_READ_FRAME;
    }

    logError("avcodec_send_packet", result);
    return VIDEO_DECODER_ERROR_OTHER;
}

VIDEO_DECODER_FUNC(
        jint,
        ffmpegReceiveFrame,
        jlong jContext,
        jint outputMode,
        jobject jOutputBuffer,
        jboolean decodeOnly) {
    JniContext* const jniContext =
            reinterpret_cast<JniContext*>(jContext);

    if (!jniContext ||
        !jniContext->codecContext ||
        !jOutputBuffer) {
        LOGE("Invalid context or output buffer.");
        return VIDEO_DECODER_ERROR_OTHER;
    }

    AVFrame* rawFrame = av_frame_alloc();
    if (!rawFrame) {
        LOGE("Failed to allocate output frame.");
        return VIDEO_DECODER_ERROR_OTHER;
    }

    const int receiveResult =
            avcodec_receive_frame(
                    jniContext->codecContext, rawFrame);

    if (receiveResult == AVERROR(EAGAIN)) {
        av_frame_free(&rawFrame);
        return VIDEO_DECODER_ERROR_INVALID_DATA;
    }

    if (receiveResult != 0) {
        logError("avcodec_receive_frame", receiveResult);
        av_frame_free(&rawFrame);
        return VIDEO_DECODER_ERROR_OTHER;
    }

    // The frame must still be dequeued from FFmpeg for decode-only input, but
    // there is no need to allocate/convert/copy it into the Java output buffer.
    if (decodeOnly) {
        av_frame_free(&rawFrame);
        return VIDEO_DECODER_ERROR_INVALID_DATA;
    }

    AVFrame* frame = rawFrame;

    if (rawFrame->format != AV_PIX_FMT_YUV420P) {
        frame =
                cvt_format(
                        jniContext,
                        rawFrame,
                        AV_PIX_FMT_YUV420P,
                        rawFrame->width,
                        rawFrame->height);

        if (!frame) {
            av_frame_free(&rawFrame);
            LOGW("Convert to YUV420P failed.");
            return VIDEO_DECODER_ERROR_OTHER;
        }

        av_frame_free(&rawFrame);
    }

    const int decodedWidth = frame->width;
    const int decodedHeight = frame->height;

    if (decodedWidth <= 0 || decodedHeight <= 0) {
        LOGE("Invalid decoded frame dimensions: %dx%d",
             decodedWidth, decodedHeight);
        av_frame_free(&frame);
        return VIDEO_DECODER_ERROR_OTHER;
    }

    int outputWidth = decodedWidth;
    int outputHeight = decodedHeight;

    if (jniContext->rotate_degree == 90 ||
        jniContext->rotate_degree == 270) {
        outputWidth = decodedHeight;
        outputHeight = decodedWidth;
    }

    const int colorSpace =
            cvt_colorspace(frame->colorspace);

    // We store the intermediate Java buffer as tightly packed I420.
    const int strideY = outputWidth;
    const int strideUv = (outputWidth + 1) / 2;

    const int oldWidth =
            env->GetIntField(
                    jOutputBuffer, jniContext->width_field);
    const int oldHeight =
            env->GetIntField(
                    jOutputBuffer, jniContext->height_field);

    if (oldWidth != outputWidth ||
        oldHeight != outputHeight) {
        const jboolean initResult =
                env->CallBooleanMethod(
                        jOutputBuffer,
                        jniContext->init_for_yuv_frame_method,
                        outputWidth,
                        outputHeight,
                        strideY,
                        strideUv,
                        colorSpace);

        if (env->ExceptionCheck()) {
            av_frame_free(&frame);
            return VIDEO_DECODER_ERROR_OTHER;
        }

        if (!initResult) {
            av_frame_free(&frame);
            return VIDEO_DECODER_ERROR_OTHER;
        }
    }

    // Color space is frame metadata and can change without a resolution change.
    env->SetIntField(
            jOutputBuffer,
            jniContext->colorspace_field,
            colorSpace);

    if (frame->pts != AV_NOPTS_VALUE) {
        env->SetLongField(
                jOutputBuffer,
                jniContext->pts_field,
                frame->pts);
    }

    if (env->ExceptionCheck()) {
        av_frame_free(&frame);
        return VIDEO_DECODER_ERROR_OTHER;
    }

    jobject dataObject =
            env->GetObjectField(
                    jOutputBuffer,
                    jniContext->data_field);

    if (!dataObject) {
        LOGE("Output data buffer is null.");
        av_frame_free(&frame);
        return VIDEO_DECODER_ERROR_OTHER;
    }

    uint8_t* data =
            static_cast<uint8_t*>(
                    env->GetDirectBufferAddress(dataObject));
    const jlong dataCapacity =
            env->GetDirectBufferCapacity(dataObject);

    const int uvHeight = (outputHeight + 1) / 2;

    const size_t lengthY =
            static_cast<size_t>(strideY) *
            static_cast<size_t>(outputHeight);
    const size_t lengthUv =
            static_cast<size_t>(strideUv) *
            static_cast<size_t>(uvHeight);
    const size_t requiredSize =
            lengthY + 2u * lengthUv;

    if (!data ||
        dataCapacity < 0 ||
        requiredSize > static_cast<size_t>(dataCapacity)) {
        LOGE("Invalid output YUV buffer: capacity=%lld required=%zu",
             static_cast<long long>(dataCapacity),
             requiredSize);

        env->DeleteLocalRef(dataObject);
        av_frame_free(&frame);
        return VIDEO_DECODER_ERROR_OTHER;
    }

    const libyuv::RotationMode rotate =
            cvt_rotate(jniContext->rotate_degree);

    const int rotateResult =
            libyuv::I420Rotate(
                    frame->data[0],
                    frame->linesize[0],
                    frame->data[1],
                    frame->linesize[1],
                    frame->data[2],
                    frame->linesize[2],
                    data,
                    strideY,
                    data + lengthY,
                    strideUv,
                    data + lengthY + lengthUv,
                    strideUv,
                    decodedWidth,
                    decodedHeight,
                    rotate);

    env->DeleteLocalRef(dataObject);
    av_frame_free(&frame);

    if (rotateResult != 0) {
        LOGE("I420Rotate failed: %d", rotateResult);
        return VIDEO_DECODER_ERROR_OTHER;
    }

    return VIDEO_DECODER_SUCCESS;
}

int I420ToRgba8888(int colorSpace,
                   const uint8_t* srcY,
                   int srcStrideY,
                   const uint8_t* srcU,
                   int srcStrideU,
                   const uint8_t* srcV,
                   int srcStrideV,
                   uint8_t* dstRgba,
                   int dstStrideRgba,
                   int width,
                   int height) {
    // libyuv names formats by register order. On little-endian Android,
    // I420ToABGR writes R,G,B,A bytes in memory, which matches RGBA_8888.
    switch (colorSpace) {
        case 2:  // COLORSPACE_BT709
            return libyuv::H420ToABGR(
                    srcY, srcStrideY,
                    srcU, srcStrideU,
                    srcV, srcStrideV,
                    dstRgba, dstStrideRgba,
                    width, height);
        case 3:  // COLORSPACE_BT2020
            return libyuv::U420ToABGR(
                    srcY, srcStrideY,
                    srcU, srcStrideU,
                    srcV, srcStrideV,
                    dstRgba, dstStrideRgba,
                    width, height);
        case 1:  // COLORSPACE_BT601
        default:
            return libyuv::I420ToABGR(
                    srcY, srcStrideY,
                    srcU, srcStrideU,
                    srcV, srcStrideV,
                    dstRgba, dstStrideRgba,
                    width, height);
    }
}

VIDEO_DECODER_FUNC(
        jint,
        ffmpegRenderFrame,
        jlong jContext,
        jobject jSurface,
        jobject jOutputBuffer,
        jint displayedWidth,
        jint displayedHeight) {
    JniContext* const jniContext =
            reinterpret_cast<JniContext*>(jContext);

    if (!jniContext ||
        !jniContext->codecContext ||
        !jSurface ||
        !jOutputBuffer ||
        displayedWidth <= 0 ||
        displayedHeight <= 0) {
        LOGE("Invalid render arguments.");
        return VIDEO_DECODER_ERROR_OTHER;
    }

    if (!jniContext->MaybeAcquireNativeWindow(env, jSurface)) {
        return VIDEO_DECODER_ERROR_OTHER;
    }

    ANativeWindow* const nativeWindow = jniContext->native_window;
    if (!nativeWindow) {
        return VIDEO_DECODER_ERROR_OTHER;
    }

    jobject dataObject =
            env->GetObjectField(jOutputBuffer, jniContext->data_field);
    if (!dataObject) {
        LOGE("Output data buffer is null.");
        return VIDEO_DECODER_ERROR_OTHER;
    }

    uint8_t* data = static_cast<uint8_t*>(
            env->GetDirectBufferAddress(dataObject));
    const jlong dataCapacity = env->GetDirectBufferCapacity(dataObject);

    const int frameWidth =
            env->GetIntField(jOutputBuffer, jniContext->width_field);
    const int frameHeight =
            env->GetIntField(jOutputBuffer, jniContext->height_field);
    const int srcStrideY =
            env->GetIntField(jOutputBuffer, jniContext->y_stride_field);
    const int srcStrideUv =
            env->GetIntField(jOutputBuffer, jniContext->uv_stride_field);
    const int colorSpace =
            env->GetIntField(jOutputBuffer, jniContext->colorspace_field);

    if (!data || dataCapacity < 0) {
        LOGE("Output data is not a direct ByteBuffer.");
        env->DeleteLocalRef(dataObject);
        return VIDEO_DECODER_ERROR_OTHER;
    }

    if (frameWidth != displayedWidth || frameHeight != displayedHeight) {
        LOGE("Frame/window size mismatch: frame=%dx%d window=%dx%d",
             frameWidth, frameHeight, displayedWidth, displayedHeight);
        env->DeleteLocalRef(dataObject);
        return VIDEO_DECODER_ERROR_OTHER;
    }

    if (srcStrideY < frameWidth || srcStrideUv < (frameWidth + 1) / 2) {
        LOGE("Invalid source strides: frame=%dx%d y=%d uv=%d",
             frameWidth, frameHeight, srcStrideY, srcStrideUv);
        env->DeleteLocalRef(dataObject);
        return VIDEO_DECODER_ERROR_OTHER;
    }

    const int srcUvHeight = (frameHeight + 1) / 2;
    const size_t srcLengthY =
            static_cast<size_t>(srcStrideY) * static_cast<size_t>(frameHeight);
    const size_t srcLengthUv =
            static_cast<size_t>(srcStrideUv) * static_cast<size_t>(srcUvHeight);
    const size_t srcRequiredSize = srcLengthY + 2u * srcLengthUv;

    if (srcRequiredSize > static_cast<size_t>(dataCapacity)) {
        LOGE("Source YUV buffer too small: capacity=%lld required=%zu",
             static_cast<long long>(dataCapacity), srcRequiredSize);
        env->DeleteLocalRef(dataObject);
        return VIDEO_DECODER_ERROR_OTHER;
    }

    const uint8_t* srcY = data;
    const uint8_t* srcU = data + srcLengthY;
    const uint8_t* srcV = srcU + srcLengthUv;

    // YV12 cannot represent odd dimensions. Go directly to the packed RGBA
    // fallback instead of trying to invent a YV12 layout.
    if ((displayedWidth & 1) != 0 || (displayedHeight & 1) != 0) {
        jniContext->use_rgba_fallback = true;
    }

    // At most two attempts are needed. The first one tries the cheap YV12 path.
    // If the vendor returns an unusable legacy stride (notably stride == 0),
    // switch this decoder context permanently to RGBA_8888 and retry the same
    // frame without guessing the YUV plane layout.
    for (int attempt = 0; attempt < 2; ++attempt) {
        const bool rgba = jniContext->use_rgba_fallback;
        const int windowFormat =
                rgba ? WINDOW_FORMAT_RGBA_8888 : kImageFormatYV12;

        if (jniContext->native_window_width != displayedWidth ||
            jniContext->native_window_height != displayedHeight ||
            jniContext->native_window_format != windowFormat) {
            const int geometryResult =
                    ANativeWindow_setBuffersGeometry(
                            nativeWindow,
                            displayedWidth,
                            displayedHeight,
                            windowFormat);
            if (geometryResult != 0) {
                LOGE("ANativeWindow_setBuffersGeometry failed: %d format=0x%x",
                     geometryResult, windowFormat);
                env->DeleteLocalRef(dataObject);
                return VIDEO_DECODER_ERROR_OTHER;
            }

            jniContext->native_window_width = displayedWidth;
            jniContext->native_window_height = displayedHeight;
            jniContext->native_window_format = windowFormat;
        }

        ANativeWindow_Buffer windowBuffer;
        const int lockResult =
                ANativeWindow_lock(nativeWindow, &windowBuffer, nullptr);

        if (lockResult == -19) {
            if (jniContext->surface != nullptr) {
                env->DeleteGlobalRef(jniContext->surface);
                jniContext->surface = nullptr;
            }
            jniContext->native_window_width = 0;
            jniContext->native_window_height = 0;
            jniContext->native_window_format = 0;
            env->DeleteLocalRef(dataObject);
            return VIDEO_DECODER_ERROR_SURFACE;
        }

        if (lockResult != 0 || !windowBuffer.bits) {
            LOGE("ANativeWindow_lock failed: %d", lockResult);
            env->DeleteLocalRef(dataObject);
            return VIDEO_DECODER_ERROR_OTHER;
        }

        if (windowBuffer.width != displayedWidth ||
            windowBuffer.height != displayedHeight) {
            LOGE("Unexpected ANativeWindow size: buffer=%dx%d requested=%dx%d",
                 windowBuffer.width, windowBuffer.height,
                 displayedWidth, displayedHeight);
            ANativeWindow_unlockAndPost(nativeWindow);
            jniContext->native_window_width = 0;
            jniContext->native_window_height = 0;
            jniContext->native_window_format = 0;
            env->DeleteLocalRef(dataObject);
            return VIDEO_DECODER_ERROR_OTHER;
        }

        if (windowBuffer.format != windowFormat) {
            LOGE("Unexpected ANativeWindow format: got=0x%x requested=0x%x",
                 windowBuffer.format, windowFormat);
            ANativeWindow_unlockAndPost(nativeWindow);
            jniContext->native_window_width = 0;
            jniContext->native_window_height = 0;
            jniContext->native_window_format = 0;
            env->DeleteLocalRef(dataObject);
            return VIDEO_DECODER_ERROR_OTHER;
        }

        if (!rgba && windowBuffer.stride == 0) {
            // Some gralloc implementations expose no meaningful legacy stride for
            // a YUV buffer through ANativeWindow_Buffer. There is no public NDK API
            // that exposes the individual Y/U/V plane layouts of this already locked
            // ANativeWindow buffer. Do not guess ALIGN(width, 16): a vendor is free
            // to use a larger allocation stride.
            LOGW("YV12 ANativeWindow returned stride=0; switching to RGBA fallback "
                 "for this decoder context (size=%dx%d)",
                 windowBuffer.width, windowBuffer.height);

            const int unlockResult = ANativeWindow_unlockAndPost(nativeWindow);
            if (unlockResult != 0) {
                LOGE("ANativeWindow_unlockAndPost failed while switching format: %d",
                     unlockResult);
                env->DeleteLocalRef(dataObject);
                return VIDEO_DECODER_ERROR_OTHER;
            }

            jniContext->use_rgba_fallback = true;
            jniContext->native_window_width = 0;
            jniContext->native_window_height = 0;
            jniContext->native_window_format = 0;
            continue;
        }

        int renderResult = VIDEO_DECODER_SUCCESS;

        if (!rgba) {
            if (windowBuffer.stride < windowBuffer.width ||
                (windowBuffer.stride & 15) != 0) {
                LOGE("Invalid YV12 stride: width=%d stride=%d",
                     windowBuffer.width, windowBuffer.stride);
                renderResult = VIDEO_DECODER_ERROR_OTHER;
            } else {
                const int dstStrideY = windowBuffer.stride;
                const int dstStrideUv = AlignTo16(dstStrideY / 2);
                const int dstUvHeight = windowBuffer.height / 2;

                const size_t dstLengthY =
                        static_cast<size_t>(dstStrideY) *
                        static_cast<size_t>(windowBuffer.height);
                const size_t dstLengthUv =
                        static_cast<size_t>(dstStrideUv) *
                        static_cast<size_t>(dstUvHeight);

                uint8_t* dstY = static_cast<uint8_t*>(windowBuffer.bits);
                uint8_t* dstV = dstY + dstLengthY;
                uint8_t* dstU = dstV + dstLengthUv;

                const int copyResult =
                        libyuv::I420Copy(
                                srcY, srcStrideY,
                                srcU, srcStrideUv,
                                srcV, srcStrideUv,
                                dstY, dstStrideY,
                                dstU, dstStrideUv,
                                dstV, dstStrideUv,
                                frameWidth, frameHeight);

                if (copyResult != 0) {
                    LOGE("I420Copy failed: %d", copyResult);
                    renderResult = VIDEO_DECODER_ERROR_OTHER;
                }
            }
        } else {
            if (windowBuffer.stride < windowBuffer.width) {
                LOGE("Invalid RGBA stride: width=%d stride=%d",
                     windowBuffer.width, windowBuffer.stride);
                renderResult = VIDEO_DECODER_ERROR_OTHER;
            } else {
                const int dstStrideBytes = windowBuffer.stride * 4;
                uint8_t* dstRgba = static_cast<uint8_t*>(windowBuffer.bits);

                const int convertResult =
                        I420ToRgba8888(
                                colorSpace,
                                srcY, srcStrideY,
                                srcU, srcStrideUv,
                                srcV, srcStrideUv,
                                dstRgba, dstStrideBytes,
                                frameWidth, frameHeight);

                if (convertResult != 0) {
                    LOGE("I420ToRgba8888 failed: %d", convertResult);
                    renderResult = VIDEO_DECODER_ERROR_OTHER;
                }
            }
        }

        const int unlockResult =
                ANativeWindow_unlockAndPost(nativeWindow);
        if (unlockResult != 0) {
            LOGE("ANativeWindow_unlockAndPost failed: %d", unlockResult);
            env->DeleteLocalRef(dataObject);
            return VIDEO_DECODER_ERROR_OTHER;
        }

        env->DeleteLocalRef(dataObject);
        return renderResult;
    }

    env->DeleteLocalRef(dataObject);
    return VIDEO_DECODER_ERROR_OTHER;
}

extern "C" int ffmpegOnJNILoad(JavaVM *vm, JNIEnv *env) {
    jclass clazz =
            env->FindClass(
                    "androidx/media3/decoder/ffmpeg/FfmpegAudioDecoder");
    if (!clazz) {
        LOGE("JNI_OnLoad: FindClass failed");
        return -1;
    }

    growOutputBufferMethod =
            env->GetMethodID(
                    clazz,
                    "growOutputBuffer",
                    "(Landroidx/media3/decoder/"
                    "SimpleDecoderOutputBuffer;I)Ljava/nio/ByteBuffer;");
    if (!growOutputBufferMethod) {
        LOGE("JNI_OnLoad: GetMethodID failed");
        return -1;
    }

    static const JNINativeMethod kFfmpegAudioDecoderMethods[] = {
            {"ffmpegInitialize", "(Ljava/lang/String;[BZII)J",
                    reinterpret_cast<void*>(ffmpegInitialize)},
            {"ffmpegDecode",
                                 "(JLjava/nio/ByteBuffer;ILandroidx/media3/decoder/"
                                 "SimpleDecoderOutputBuffer;Ljava/nio/ByteBuffer;I)I",
                    reinterpret_cast<void*>(ffmpegDecode)},
            {"ffmpegGetChannelCount", "(J)I",
                    reinterpret_cast<void*>(ffmpegGetChannelCount)},
            {"ffmpegGetSampleRate", "(J)I",
                    reinterpret_cast<void*>(ffmpegGetSampleRate)},
            {"ffmpegReset", "(J[B)J",
                    reinterpret_cast<void*>(ffmpegReset)},
            {"ffmpegRelease", "(J)V",
                    reinterpret_cast<void*>(ffmpegRelease)},
    };

    if (env->RegisterNatives(
            clazz,
            kFfmpegAudioDecoderMethods,
            sizeof(kFfmpegAudioDecoderMethods) /
            sizeof(kFfmpegAudioDecoderMethods[0])) < 0) {
        LOGE("JNI_OnLoad: RegisterNatives failed for FfmpegAudioDecoder");
        return -1;
    }

    jclass libraryClazz =
            env->FindClass(
                    "androidx/media3/decoder/ffmpeg/FfmpegLibrary");
    if (!libraryClazz) {
        LOGE("JNI_OnLoad: FindClass failed for FfmpegLibrary");
        return -1;
    }

    static const JNINativeMethod kFfmpegLibraryMethods[] = {
            {"ffmpegGetVersion", "()Ljava/lang/String;",
                    reinterpret_cast<void*>(ffmpegGetVersion)},
            {"ffmpegGetInputBufferPaddingSize", "()I",
                    reinterpret_cast<void*>(ffmpegGetInputBufferPaddingSize)},
            {"ffmpegHasDecoder", "(Ljava/lang/String;)Z",
                    reinterpret_cast<void*>(ffmpegHasDecoder)},
            {"ffmpegGetAv1DecoderName", "()Ljava/lang/String;",
                    reinterpret_cast<void*>(ffmpegGetAv1DecoderName)},
    };

    if (env->RegisterNatives(
            libraryClazz,
            kFfmpegLibraryMethods,
            sizeof(kFfmpegLibraryMethods) /
            sizeof(kFfmpegLibraryMethods[0])) < 0) {
        LOGE("JNI_OnLoad: RegisterNatives failed for FfmpegLibrary");
        return -1;
    }

    return JNI_TRUE;
}
