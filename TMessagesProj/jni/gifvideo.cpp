#include <jni.h>
#include <android/bitmap.h>
#include <cstdint>
#include <limits>
#include <string>
#include <unistd.h>
#include <linux/stat.h>
#include <asm/fcntl.h>
#include <fcntl.h>
#include <libyuv.h>
#include <tgnet/FileLog.h>
#include "tgnet/ConnectionsManager.h"
#include "voip/webrtc/common_video/h264/sps_parser.h"
#include "voip/webrtc/common_video/h264/h264_common.h"
#include "c_utils.h"

extern "C" {
#include <libavformat/avformat.h>
#include <libavformat/isom.h>
#include <libavcodec/bytestream.h>
#include <libavcodec/get_bits.h>
#include <libavcodec/golomb.h>
#include <libavutil/eval.h>
#include <libavutil/intmath.h>
#include <libswscale/swscale.h>
}

#define RGB8888_A(p) ((p & (0xff<<24))      >> 24 )

static const std::string av_make_error_str(int errnum) {
    char errbuf[AV_ERROR_MAX_STRING_SIZE];
    av_strerror(errnum, errbuf, AV_ERROR_MAX_STRING_SIZE);
    return (std::string) errbuf;
}

#undef av_err2str
#define av_err2str(errnum) av_make_error_str(errnum).c_str()
#define FFMPEG_AVSEEK_SIZE 0x10000

jclass jclass_AnimatedFileDrawableStream;
jmethodID jclass_AnimatedFileDrawableStream_read;
jmethodID jclass_AnimatedFileDrawableStream_cancel;
jmethodID jclass_AnimatedFileDrawableStream_isCanceled;
jmethodID jclass_AnimatedFileDrawableStream_isFinishedLoadingFile;
jmethodID jclass_AnimatedFileDrawableStream_getFinishedFilePath;

typedef struct VideoInfo {

    ~VideoInfo() {
        if (video_dec_ctx) {
            avcodec_close(video_dec_ctx);
            video_dec_ctx = nullptr;
        }
        if (fmt_ctx) {
            avformat_close_input(&fmt_ctx);
            fmt_ctx = nullptr;
        }
        if (frame) {
            av_frame_free(&frame);
            frame = nullptr;
        }
        if (src) {
            delete [] src;
            src = nullptr;
        }
        if (stream != nullptr) {
            JNIEnv *jniEnv = nullptr;
            JavaVMAttachArgs jvmArgs;
            jvmArgs.version = JNI_VERSION_1_6;

            bool attached;
            if (JNI_EDETACHED == javaVm->GetEnv((void **) &jniEnv, JNI_VERSION_1_6)) {
                javaVm->AttachCurrentThread(&jniEnv, &jvmArgs);
                attached = true;
            } else {
                attached = false;
            }
            DEBUG_DELREF("gifvideo.cpp stream");
            jniEnv->DeleteGlobalRef(stream);
            if (attached) {
                javaVm->DetachCurrentThread();
            }
            stream = nullptr;
        }
        if (ioContext != nullptr) {
            if (ioContext->buffer) {
                av_freep(&ioContext->buffer);
            }
            avio_context_free(&ioContext);
            ioContext = nullptr;
        }
        if (sws_ctx != nullptr) {
            sws_freeContext(sws_ctx);
            sws_ctx = nullptr;
        }
        if (fd >= 0) {
            close(fd);
            fd = -1;
        }

        av_packet_unref(&orig_pkt);

        video_stream_idx = -1;
        video_stream = nullptr;
        audio_stream = nullptr;
    }

    AVFormatContext *fmt_ctx = nullptr;
    char *src = nullptr;
    int video_stream_idx = -1;
    AVStream *video_stream = nullptr;
    AVStream *audio_stream = nullptr;
    AVCodecContext *video_dec_ctx = nullptr;
    AVFrame *frame = nullptr;
    bool has_decoded_frames = false;
    AVPacket pkt;
    AVPacket orig_pkt;
    bool stopped = false;
    bool seeking = false;

    int firstWidth = 0;
    int firstHeight = 0;

    bool dropFrames = false;

    int32_t dst_linesize[1];

    struct SwsContext *sws_ctx = nullptr;

    AVIOContext *ioContext = nullptr;
    unsigned char *ioBuffer = nullptr;
    jobject stream = nullptr;
    int32_t account = 0;
    int fd = -1;
    int64_t file_size = 0;
    int64_t last_seek_p = 0;
};

void custom_log(void *ptr, int level, const char* fmt, va_list vl){
    va_list vl2;
    char line[1024];
    static int print_prefix = 1;

    va_copy(vl2, vl);
    av_log_format_line(ptr, level, fmt, vl2, line, sizeof(line), &print_prefix);
    va_end(vl2);

    LOGE(line);
}

static enum AVPixelFormat get_format(AVCodecContext *ctx,
                                        const enum AVPixelFormat *pix_fmts)
{
    const enum AVPixelFormat *p;

    for (p = pix_fmts; *p != -1; p++) {
        LOGE("available format %d", p);
    }

    return pix_fmts[0];
}

int open_codec_context(int *stream_idx, AVCodecContext **dec_ctx, AVFormatContext *fmt_ctx, enum AVMediaType type) {
    int ret, stream_index;
    AVStream *st;
    AVCodec *dec = NULL;
    AVDictionary *opts = NULL;

    ret = av_find_best_stream(fmt_ctx, type, -1, -1, NULL, 0);
    if (ret < 0) {
        LOGE("can't find %s stream in input file", av_get_media_type_string(type));
        return ret;
    } else {
        stream_index = ret;
        st = fmt_ctx->streams[stream_index];

        dec = avcodec_find_decoder(st->codecpar->codec_id);
        if (!dec) {
            LOGE("failed to find %d codec", st->codecpar->codec_id);
            return AVERROR(EINVAL);
        }

        *dec_ctx = avcodec_alloc_context3(dec);
        if (!*dec_ctx) {
            LOGE("Failed to allocate the %s codec context", av_get_media_type_string(type));
            return AVERROR(ENOMEM);
        }

        if ((ret = avcodec_parameters_to_context(*dec_ctx, st->codecpar)) < 0) {
            LOGE("Failed to copy %s codec parameters to decoder context", av_get_media_type_string(type));
            return ret;
        }

        av_dict_set(&opts, "refcounted_frames", "1", 0);
        if ((ret = avcodec_open2(*dec_ctx, dec, &opts)) < 0) {
            LOGE("Failed to open %s codec", av_get_media_type_string(type));
            return ret;
        }
        *stream_idx = stream_index;
    }

    return 0;
}

int decode_packet(VideoInfo *info, int *got_frame) {
    int ret = 0;
    int decoded = info->pkt.size;
    *got_frame = 0;

    if (info->pkt.stream_index == info->video_stream_idx) {
        while (decoded > 0) {
            ret = avcodec_send_packet(info->video_dec_ctx, &info->pkt);
            if (ret < 0 && ret != AVERROR(EAGAIN)) {
                return ret;
            }
            if (ret >= 0) {
                ret = avcodec_receive_frame(info->video_dec_ctx, info->frame);
                if (ret == AVERROR(EAGAIN) || ret == AVERROR_EOF) {
                    return 0;
                } else if (ret < 0) {
                    return ret;
                }
                *got_frame = 1;
                return info->pkt.size;
            }
            decoded = info->pkt.size;
        }
    }

    return decoded;
}

void requestFd(VideoInfo *info) {
    JNIEnv *jniEnv = nullptr;

    JavaVMAttachArgs jvmArgs;
    jvmArgs.version = JNI_VERSION_1_6;

    bool attached;
    if (JNI_EDETACHED == javaVm->GetEnv((void **) &jniEnv, JNI_VERSION_1_6)) {
        javaVm->AttachCurrentThread(&jniEnv, &jvmArgs);
        attached = true;
    } else {
        attached = false;
    }
    jniEnv->CallIntMethod(info->stream, jclass_AnimatedFileDrawableStream_read, (jint) 0, (jint) 1);
    jboolean loaded = jniEnv->CallBooleanMethod(info->stream, jclass_AnimatedFileDrawableStream_isFinishedLoadingFile);
    if (loaded) {
        delete[] info->src;
        jstring src = (jstring) jniEnv->CallObjectMethod(info->stream, jclass_AnimatedFileDrawableStream_getFinishedFilePath);
        char const *srcString = jniEnv->GetStringUTFChars(src, 0);
        size_t len = strlen(srcString);
        info->src = new char[len + 1];
        memcpy(info->src, srcString, len);
        info->src[len] = '\0';
        if (srcString != 0) {
            jniEnv->ReleaseStringUTFChars(src, srcString);
        }
    }

    if (attached) {
        javaVm->DetachCurrentThread();
    }
    info->fd = open(info->src, O_RDONLY, S_IRUSR);
}

int readCallback(void *opaque, uint8_t *buf, int buf_size) {
    VideoInfo *info = (VideoInfo *) opaque;
    if (!info->stopped) {
        if (info->fd < 0) {
            requestFd(info);
        }
        if (info->fd >= 0) {
            if (info->last_seek_p + buf_size > info->file_size) {
                buf_size = (int) (info->file_size - info->last_seek_p);
            }
            if (buf_size > 0) {
                JNIEnv *jniEnv = nullptr;

                JavaVMAttachArgs jvmArgs;
                jvmArgs.version = JNI_VERSION_1_6;

                bool attached;
                if (JNI_EDETACHED == javaVm->GetEnv((void **) &jniEnv, JNI_VERSION_1_6)) {
                    javaVm->AttachCurrentThread(&jniEnv, &jvmArgs);
                    attached = true;
                } else {
                    attached = false;
                }

                buf_size = jniEnv->CallIntMethod(info->stream, jclass_AnimatedFileDrawableStream_read, (jint) info->last_seek_p, (jint) buf_size);
                info->last_seek_p += buf_size;
                if (attached) {
                    javaVm->DetachCurrentThread();
                }
                if (buf_size == 0) {
                    return AVERROR_EXIT;
                }
                int ret = (int) read(info->fd, buf, (size_t) buf_size);
                if (ret <= 0) {
                    return AVERROR_EOF;
                }
                return ret;
            }
        }
    }
    return AVERROR_EOF;
}

int64_t seekCallback(void *opaque, int64_t offset, int whence) {
    VideoInfo *info = (VideoInfo *) opaque;
    if (!info->stopped) {
        if (info->fd < 0) {
            requestFd(info);
        }
        if (info->fd >= 0) {
            if (whence & FFMPEG_AVSEEK_SIZE) {
                return info->file_size;
            } else {
                info->last_seek_p = offset;
                lseek(info->fd, off_t(offset), SEEK_SET);
                return offset;
            }
        }
    }
    return 0;
}

enum PARAM_NUM {
    PARAM_NUM_SUPPORTED_VIDEO_CODEC = 0,
    PARAM_NUM_WIDTH = 1,
    PARAM_NUM_HEIGHT = 2,
    PARAM_NUM_BITRATE = 3,
    PARAM_NUM_DURATION = 4,
    PARAM_NUM_AUDIO_FRAME_SIZE = 5,
    PARAM_NUM_VIDEO_FRAME_SIZE = 6,
    PARAM_NUM_FRAMERATE = 7,
    PARAM_NUM_ROTATION = 8,
    PARAM_NUM_SUPPORTED_AUDIO_CODEC = 9,
    PARAM_NUM_HAS_AUDIO = 10,
    PARAM_NUM_COUNT = 11,
};

extern "C" JNIEXPORT void JNICALL Java_org_telegram_ui_Components_AnimatedFileDrawable_getVideoInfo(JNIEnv *env, jclass clazz, jint sdkVersion, jstring src, jintArray data) {
    VideoInfo *info = new VideoInfo();

    char const *srcString = env->GetStringUTFChars(src, 0);
    size_t len = strlen(srcString);
    info->src = new char[len + 1];
    memcpy(info->src, srcString, len);
    info->src[len] = '\0';
    if (srcString != nullptr) {
        env->ReleaseStringUTFChars(src, srcString);
    }

    int ret;
    if ((ret = avformat_open_input(&info->fmt_ctx, info->src, NULL, NULL)) < 0) {
        LOGE("can't open source file %s, %s", info->src, av_err2str(ret));
        delete info;
        return;
    }

    if ((ret = avformat_find_stream_info(info->fmt_ctx, NULL)) < 0) {
        LOGE("can't find stream information %s, %s", info->src, av_err2str(ret));
        delete info;
        return;
    }

    if ((ret = av_find_best_stream(info->fmt_ctx, AVMEDIA_TYPE_VIDEO, -1, -1, NULL, 0)) >= 0) {
        info->video_stream = info->fmt_ctx->streams[ret];
    }

    if ((ret = av_find_best_stream(info->fmt_ctx, AVMEDIA_TYPE_AUDIO, -1, -1, NULL, 0)) >= 0) {
        info->audio_stream = info->fmt_ctx->streams[ret];
    }

    if (info->video_stream == nullptr) {
        LOGE("can't find video stream in the input, aborting %s", info->src);
        delete info;
        return;
    }

    jint *dataArr = env->GetIntArrayElements(data, 0);
    if (dataArr != nullptr) {
        //https://developer.android.com/guide/topics/media/media-formats
        dataArr[PARAM_NUM_SUPPORTED_VIDEO_CODEC] =
                info->video_stream->codecpar->codec_id == AV_CODEC_ID_H264 ||
                info->video_stream->codecpar->codec_id == AV_CODEC_ID_H263 ||
                info->video_stream->codecpar->codec_id == AV_CODEC_ID_MPEG4 ||
                info->video_stream->codecpar->codec_id == AV_CODEC_ID_VP8 ||
                info->video_stream->codecpar->codec_id == AV_CODEC_ID_VP9 ||
                (sdkVersion > 21 && info->video_stream->codecpar->codec_id == AV_CODEC_ID_HEVC);

        if (strstr(info->fmt_ctx->iformat->name, "mov") != 0 && dataArr[PARAM_NUM_SUPPORTED_VIDEO_CODEC]) {
            MOVStreamContext *mov = (MOVStreamContext *) info->video_stream->priv_data;
            dataArr[PARAM_NUM_VIDEO_FRAME_SIZE] = (jint) mov->data_size;

            if (info->audio_stream != nullptr) {
                mov = (MOVStreamContext *) info->audio_stream->priv_data;
                dataArr[PARAM_NUM_AUDIO_FRAME_SIZE] = (jint) mov->data_size;
            }
        }

        if (info->audio_stream != nullptr) {
            //https://developer.android.com/guide/topics/media/media-formats
            dataArr[PARAM_NUM_SUPPORTED_AUDIO_CODEC] =
                    info->audio_stream->codecpar->codec_id == AV_CODEC_ID_AAC ||
                    info->audio_stream->codecpar->codec_id == AV_CODEC_ID_AAC_LATM ||
                    info->audio_stream->codecpar->codec_id == AV_CODEC_ID_VORBIS ||
                    info->audio_stream->codecpar->codec_id == AV_CODEC_ID_AMR_NB ||
                    info->audio_stream->codecpar->codec_id == AV_CODEC_ID_AMR_WB ||
                    info->audio_stream->codecpar->codec_id == AV_CODEC_ID_FLAC ||
                    info->audio_stream->codecpar->codec_id == AV_CODEC_ID_MP3 ||
                    // not supported codec, skip audio in this case
                    info->audio_stream->codecpar->codec_id == AV_CODEC_ID_ADPCM_IMA_WAV ||
                    (sdkVersion > 21 && info->audio_stream->codecpar->codec_id == AV_CODEC_ID_OPUS);
            dataArr[PARAM_NUM_HAS_AUDIO] = 1;
        } else {
            dataArr[PARAM_NUM_HAS_AUDIO] = 0;
        }

        dataArr[PARAM_NUM_BITRATE] = (jint) info->video_stream->codecpar->bit_rate;
        dataArr[PARAM_NUM_WIDTH] = info->video_stream->codecpar->width;
        dataArr[PARAM_NUM_HEIGHT] = info->video_stream->codecpar->height;
        AVDictionaryEntry *rotate_tag = av_dict_get(info->video_stream->metadata, "rotate", NULL, 0);
        if (rotate_tag && *rotate_tag->value && strcmp(rotate_tag->value, "0") != 0) {
            char *tail;
            dataArr[PARAM_NUM_ROTATION] = (jint) av_strtod(rotate_tag->value, &tail);
            if (*tail) {
                dataArr[PARAM_NUM_ROTATION] = 0;
            }
        } else {
            dataArr[PARAM_NUM_ROTATION] = 0;
        }
        if (info->video_stream->codecpar->codec_id == AV_CODEC_ID_H264 || info->video_stream->codecpar->codec_id == AV_CODEC_ID_HEVC) {
            dataArr[PARAM_NUM_FRAMERATE] = (jint) av_q2d(info->video_stream->avg_frame_rate);
        } else {
            dataArr[PARAM_NUM_FRAMERATE] = (jint) av_q2d(info->video_stream->r_frame_rate);
        }
        dataArr[PARAM_NUM_DURATION] = (int32_t) (info->fmt_ctx->duration * 1000 / AV_TIME_BASE);
        env->ReleaseIntArrayElements(data, dataArr, 0);
        delete info;
    }
}

extern "C" JNIEXPORT jlong JNICALL Java_org_telegram_ui_Components_AnimatedFileDrawable_createDecoder(JNIEnv *env, jclass clazz, jstring src, jintArray data, jint account, jlong streamFileSize, jobject stream, jboolean preview) {
    VideoInfo *info = new VideoInfo();

    char const *srcString = env->GetStringUTFChars(src, 0);
    size_t len = strlen(srcString);
    info->src = new char[len + 1];
    memcpy(info->src, srcString, len);
    info->src[len] = '\0';
    if (srcString != 0) {
        env->ReleaseStringUTFChars(src, srcString);
    }

    int ret;
    if (streamFileSize != 0) {
        info->file_size = streamFileSize;
        DEBUG_REF("gifvideo.cpp new stream");
        info->stream = env->NewGlobalRef(stream);
        info->account = account;
        info->fd = open(info->src, O_RDONLY, S_IRUSR);

        info->ioBuffer = (unsigned char *) av_malloc(64 * 1024);
        info->ioContext = avio_alloc_context(info->ioBuffer, 64 * 1024, 0, info, readCallback, nullptr, seekCallback);
        if (info->ioContext == nullptr) {
            delete info;
            return 0;
        }

        info->fmt_ctx = avformat_alloc_context();
        info->fmt_ctx->pb = info->ioContext;

        AVDictionary *options = NULL;
        av_dict_set(&options, "usetoc", "1", 0);
        ret = avformat_open_input(&info->fmt_ctx, "http://localhost/file", NULL, &options);
        av_dict_free(&options);
        if (ret < 0) {
            LOGE("can't open source file %s, %s", info->src, av_err2str(ret));
            delete info;
            return 0;
        }
        info->fmt_ctx->flags |= AVFMT_FLAG_FAST_SEEK;
        if (preview) {
            info->fmt_ctx->flags |= AVFMT_FLAG_NOBUFFER;
        }
    } else {
        if ((ret = avformat_open_input(&info->fmt_ctx, info->src, NULL, NULL)) < 0) {
            LOGE("can't open source file %s, %s", info->src, av_err2str(ret));
            delete info;
            return 0;
        }
    }

    if ((ret = avformat_find_stream_info(info->fmt_ctx, NULL)) < 0) {
        LOGE("can't find stream information %s, %s", info->src, av_err2str(ret));
        delete info;
        return 0;
    }

    if (open_codec_context(&info->video_stream_idx, &info->video_dec_ctx, info->fmt_ctx, AVMEDIA_TYPE_VIDEO) >= 0) {
        info->video_stream = info->fmt_ctx->streams[info->video_stream_idx];
    }

    if (info->video_stream == nullptr) {
        LOGE("can't find video stream in the input, aborting %s", info->src);
        delete info;
        return 0;
    }

    info->frame = av_frame_alloc();
    if (info->frame == nullptr) {
        LOGE("can't allocate frame %s", info->src);
        delete info;
        return 0;
    }

    av_init_packet(&info->pkt);
    info->pkt.data = NULL;
    info->pkt.size = 0;

    jint *dataArr = env->GetIntArrayElements(data, 0);
    if (dataArr != nullptr) {
        dataArr[0] = info->video_dec_ctx->width;
        dataArr[1] = info->video_dec_ctx->height;
        //float pixelWidthHeightRatio = info->video_dec_ctx->sample_aspect_ratio.num / info->video_dec_ctx->sample_aspect_ratio.den; TODO support
        AVDictionaryEntry *rotate_tag = av_dict_get(info->video_stream->metadata, "rotate", NULL, 0);
        if (rotate_tag && *rotate_tag->value && strcmp(rotate_tag->value, "0")) {
            char *tail;
            dataArr[2] = (jint) av_strtod(rotate_tag->value, &tail);
            if (*tail) {
                dataArr[2] = 0;
            }
        } else {
            dataArr[2] = 0;
        }
        dataArr[4] = (int32_t) (info->fmt_ctx->duration * 1000 / AV_TIME_BASE);
        int video_stream_index = -1;
        double fps = 30.0;
        for (int i = 0; i < info->fmt_ctx->nb_streams; i++) {
            if (info->fmt_ctx->streams[i]->codecpar->codec_type == AVMEDIA_TYPE_VIDEO) {
                video_stream_index = i;
                break;
            }
        }
        if (video_stream_index != -1) {
            AVStream* video_stream = info->fmt_ctx->streams[video_stream_index];
            if (video_stream->avg_frame_rate.den && video_stream->avg_frame_rate.num) {
                fps = av_q2d(video_stream->avg_frame_rate);
            } else if(video_stream->r_frame_rate.den && video_stream->r_frame_rate.num) {
                fps = av_q2d(video_stream->r_frame_rate);
            } else {
                int ticks = video_stream->codec->ticks_per_frame;
                fps = 1.0 / (ticks * av_q2d(video_stream->time_base));
            }
        }
        dataArr[5] = (int32_t) fps;
        //(int32_t) (1000 * info->video_stream->duration * av_q2d(info->video_stream->time_base));
        env->ReleaseIntArrayElements(data, dataArr, 0);
    }

    //LOGD("successfully opened file %s", info->src);

    return (jlong) (intptr_t) info;
}

extern "C" JNIEXPORT void JNICALL Java_org_telegram_ui_Components_AnimatedFileDrawable_destroyDecoder(JNIEnv *env, jclass clazz, jlong ptr) {
    if (ptr == NULL) {
        return;
    }
    VideoInfo *info = (VideoInfo *) (intptr_t) ptr;
    if (info->stream != nullptr) {
        JNIEnv *jniEnv = nullptr;
        JavaVMAttachArgs jvmArgs;
        jvmArgs.version = JNI_VERSION_1_6;

        bool attached;
        if (JNI_EDETACHED == javaVm->GetEnv((void **) &jniEnv, JNI_VERSION_1_6)) {
            javaVm->AttachCurrentThread(&jniEnv, &jvmArgs);
            attached = true;
        } else {
            attached = false;
        }
        jniEnv->CallVoidMethod(info->stream, jclass_AnimatedFileDrawableStream_cancel);
        if (attached) {
            javaVm->DetachCurrentThread();
        }
    }
    delete info;
}

extern "C" JNIEXPORT void JNICALL Java_org_telegram_ui_Components_AnimatedFileDrawable_stopDecoder(JNIEnv *env, jclass clazz, jlong ptr) {
    if (ptr == NULL) {
        return;
    }
    VideoInfo *info = (VideoInfo *) (intptr_t) ptr;
    info->stopped = true;
}

extern "C" JNIEXPORT void JNICALL Java_org_telegram_ui_Components_AnimatedFileDrawable_prepareToSeek(JNIEnv *env, jclass clazz, jlong ptr) {
    if (ptr == NULL) {
        return;
    }
    VideoInfo *info = (VideoInfo *) (intptr_t) ptr;
    info->seeking = true;
}

void push_time(JNIEnv *env, VideoInfo* info, jintArray data) {
    jint *dataArr = env->GetIntArrayElements(data, 0);
    dataArr[3] = (jint) (1000 * info->frame->best_effort_timestamp * av_q2d(info->video_stream->time_base));
    env->ReleaseIntArrayElements(data, dataArr, 0);
}

extern "C" JNIEXPORT void JNICALL Java_org_telegram_ui_Components_AnimatedFileDrawable_seekToMs(JNIEnv *env, jclass clazz, jlong ptr, jlong ms, jintArray data, jboolean precise) {
    if (ptr == NULL) {
        return;
    }
    VideoInfo *info = (VideoInfo *) (intptr_t) ptr;
    info->seeking = false;
    int64_t pts = (int64_t) (ms / av_q2d(info->video_stream->time_base) / 1000);
    int ret = 0;
    if ((ret = av_seek_frame(info->fmt_ctx, info->video_stream_idx, pts, AVSEEK_FLAG_BACKWARD | AVSEEK_FLAG_FRAME)) < 0) {
        LOGE("can't seek file %s, %s", info->src, av_err2str(ret));
        return;
    } else {
        avcodec_flush_buffers(info->video_dec_ctx);
        if (!precise) {
            push_time(env, info, data);
            return;
        }
        int got_frame = 0;
        int32_t tries = 100;
        while (tries > 0) {
            if (info->pkt.size == 0) {
                ret = av_read_frame(info->fmt_ctx, &info->pkt);
                if (ret >= 0) {
                    info->orig_pkt = info->pkt;
                }
            }

            if (info->pkt.size > 0) {
                ret = decode_packet(info, &got_frame);
                if (ret < 0) {
                    if (info->has_decoded_frames) {
                        ret = 0;
                    }
                    info->pkt.size = 0;
                } else {
                    info->pkt.data += ret;
                    info->pkt.size -= ret;
                }
                if (info->pkt.size == 0) {
                    av_packet_unref(&info->orig_pkt);
                }
            } else {
                info->pkt.data = NULL;
                info->pkt.size = 0;
                ret = decode_packet(info, &got_frame);
                if (ret < 0) {
                    push_time(env, info, data);
                    return;
                }
                if (got_frame == 0) {
                    av_seek_frame(info->fmt_ctx, info->video_stream_idx, 0, AVSEEK_FLAG_BACKWARD | AVSEEK_FLAG_FRAME);
                    push_time(env, info, data);
                    return;
                }
            }
            if (ret < 0) {
                push_time(env, info, data);
                return;
            }
            if (got_frame) {
                info->has_decoded_frames = true;
                bool finished = false;
                if (info->frame->format == AV_PIX_FMT_YUV444P || info->frame->format == AV_PIX_FMT_YUV420P || info->frame->format == AV_PIX_FMT_BGRA || info->frame->format == AV_PIX_FMT_YUVJ420P) {
                    int64_t pkt_pts = info->frame->best_effort_timestamp;
                    if (pkt_pts >= pts) {
                        finished = true;
                    }
                }
                av_frame_unref(info->frame);
                if (finished) {
                    push_time(env, info, data);
                    return;
                }
            }
            tries--;
        }
    }
    push_time(env, info, data);
}

uint32_t premultiply_channel_value(const uint32_t pixel, const uint8_t offset, const float normalizedAlpha) {
    auto multipliedValue = ((pixel >> offset) & 0xFF) * normalizedAlpha;
    return ((uint32_t)std::min(multipliedValue, 255.0f)) << offset;
}

static inline void writeFrameToBitmap(JNIEnv *env, VideoInfo *info, jintArray data, jobject bitmap, jint stride) {
    if (env->IsSameObject(bitmap, NULL)) {
        push_time(env, info, data);
        return;
    }
    jint *dataArr = env->GetIntArrayElements(data, 0);
    int32_t wantedWidth;
    int32_t wantedHeight;

    AndroidBitmapInfo bitmapInfo;
    AndroidBitmap_getInfo(env, bitmap, &bitmapInfo);
    int32_t bitmapWidth = bitmapInfo.width;
    int32_t bitmapHeight = bitmapInfo.height;
    if (dataArr != nullptr) {
        wantedWidth = dataArr[0];
        wantedHeight = dataArr[1];
        dataArr[3] = (jint) (1000 * info->frame->best_effort_timestamp * av_q2d(info->video_stream->time_base));
        env->ReleaseIntArrayElements(data, dataArr, 0);
    } else {
        wantedWidth = bitmapWidth;
        wantedHeight = bitmapHeight;
    }

    if (wantedWidth == info->frame->width && wantedHeight == info->frame->height || wantedWidth == info->frame->height && wantedHeight == info->frame->width) {
        void *pixels;
        if (AndroidBitmap_lockPixels(env, bitmap, &pixels) >= 0) {
            if (info->sws_ctx == nullptr) {
                if (info->frame->format > AV_PIX_FMT_NONE && info->frame->format < AV_PIX_FMT_NB && info->frame->format != AV_PIX_FMT_YUVA420P) {
                    info->sws_ctx = sws_getContext(info->frame->width, info->frame->height, (AVPixelFormat) info->frame->format, bitmapWidth, bitmapHeight, AV_PIX_FMT_RGBA, SWS_BILINEAR, NULL, NULL, NULL);
                } else if (info->video_dec_ctx->pix_fmt > AV_PIX_FMT_NONE && info->video_dec_ctx->pix_fmt < AV_PIX_FMT_NB && info->frame->format != AV_PIX_FMT_YUVA420P) {
                    info->sws_ctx = sws_getContext(info->video_dec_ctx->width, info->video_dec_ctx->height, info->video_dec_ctx->pix_fmt, bitmapWidth, bitmapHeight, AV_PIX_FMT_RGBA, SWS_BILINEAR, NULL, NULL, NULL);
                }
            }
            if (info->sws_ctx == nullptr || ((intptr_t) pixels) % 16 != 0) {
                if (info->frame->format == AV_PIX_FMT_YUVA420P) {
                    libyuv::I420AlphaToARGBMatrix(info->frame->data[0], info->frame->linesize[0], info->frame->data[2], info->frame->linesize[2], info->frame->data[1], info->frame->linesize[1], info->frame->data[3], info->frame->linesize[3], (uint8_t *) pixels, bitmapWidth * 4, &libyuv::kYvuI601Constants, bitmapWidth, bitmapHeight, 1);
                } else if (info->frame->format == AV_PIX_FMT_YUV444P) {
                    libyuv::H444ToARGB(info->frame->data[0], info->frame->linesize[0], info->frame->data[2], info->frame->linesize[2], info->frame->data[1], info->frame->linesize[1], (uint8_t *) pixels, bitmapWidth * 4, bitmapWidth, bitmapHeight);
                } else if (info->frame->format == AV_PIX_FMT_YUV420P || info->frame->format == AV_PIX_FMT_YUVJ420P) {
                    if (info->frame->colorspace == AVColorSpace::AVCOL_SPC_BT709) {
                        libyuv::H420ToARGB(info->frame->data[0], info->frame->linesize[0], info->frame->data[2], info->frame->linesize[2], info->frame->data[1], info->frame->linesize[1], (uint8_t *) pixels, bitmapWidth * 4, bitmapWidth, bitmapHeight);
                    } else {
                        libyuv::I420ToARGB(info->frame->data[0], info->frame->linesize[0], info->frame->data[2], info->frame->linesize[2], info->frame->data[1], info->frame->linesize[1], (uint8_t *) pixels, bitmapWidth * 4, bitmapWidth, bitmapHeight);
                    }
                } else if (info->frame->format == AV_PIX_FMT_BGRA) {
                    libyuv::ABGRToARGB(info->frame->data[0], info->frame->linesize[0], (uint8_t *) pixels, info->frame->width * 4, info->frame->width, info->frame->height);
                }
            } else {
                uint8_t __attribute__ ((aligned (16))) *dst_data[1];
                dst_data[0] = (uint8_t *) pixels;
                info->dst_linesize[0] = stride;
                sws_scale(info->sws_ctx, info->frame->data, info->frame->linesize, 0, info->frame->height, dst_data, info->dst_linesize);
            }
        }
        AndroidBitmap_unlockPixels(env, bitmap);
    }
}

extern "C" JNIEXPORT int JNICALL Java_org_telegram_ui_Components_AnimatedFileDrawable_getFrameAtTime(JNIEnv *env, jclass clazz, jlong ptr, jlong ms, jobject bitmap, jintArray data, jint stride) {
    if (ptr == NULL || bitmap == nullptr || data == nullptr) {
        return 0;
    }
    VideoInfo *info = (VideoInfo *) (intptr_t) ptr;
    info->seeking = false;
    int64_t pts = (int64_t) (ms / av_q2d(info->video_stream->time_base) / 1000);
    int ret = 0;
    if ((ret = av_seek_frame(info->fmt_ctx, info->video_stream_idx, pts, AVSEEK_FLAG_BACKWARD | AVSEEK_FLAG_FRAME)) < 0) {
        LOGE("can't seek file %s, %s", info->src, av_err2str(ret));
        return 0;
    } else {
        avcodec_flush_buffers(info->video_dec_ctx);
        int got_frame = 0;
        int32_t tries = 1000;
        bool readNextPacket = true;
        while (tries > 0) {
            if (info->stream != nullptr) {
                JNIEnv *jniEnv = nullptr;
                JavaVMAttachArgs jvmArgs;
                jvmArgs.version = JNI_VERSION_1_6;

                bool attached;
                if (JNI_EDETACHED == javaVm->GetEnv((void **) &jniEnv, JNI_VERSION_1_6)) {
                    javaVm->AttachCurrentThread(&jniEnv, &jvmArgs);
                    attached = true;
                } else {
                    attached = false;
                }
                jboolean canceled = jniEnv->CallBooleanMethod(info->stream, jclass_AnimatedFileDrawableStream_isCanceled);
                if (attached) {
                    javaVm->DetachCurrentThread();
                }
                if (canceled) {
                    return 0;
                }
            }
            if (info->pkt.size == 0 && readNextPacket) {
                ret = av_read_frame(info->fmt_ctx, &info->pkt);
                if (ret >= 0) {
                    info->orig_pkt = info->pkt;
                }
            }

            if (info->pkt.size > 0) {
                ret = decode_packet(info, &got_frame);
                if (ret < 0) {
                    if (info->has_decoded_frames) {
                        ret = 0;
                    }
                    info->pkt.size = 0;
                } else {
                    info->pkt.data += ret;
                    info->pkt.size -= ret;
                }
                if (info->pkt.size == 0) {
                    av_packet_unref(&info->orig_pkt);
                }
            } else {
                info->pkt.data = NULL;
                info->pkt.size = 0;
                ret = decode_packet(info, &got_frame);
                if (ret < 0) {
                    return 0;
                }
                if (got_frame == 0) {
                    av_seek_frame(info->fmt_ctx, info->video_stream_idx, 0, AVSEEK_FLAG_BACKWARD | AVSEEK_FLAG_FRAME);
                    return 0;
                }
            }
            if (ret < 0) {
                return 0;
            }
            if (got_frame) {
                bool finished = false;
                if (info->frame->format == AV_PIX_FMT_YUV444P || info->frame->format == AV_PIX_FMT_YUV420P || info->frame->format == AV_PIX_FMT_BGRA || info->frame->format == AV_PIX_FMT_YUVJ420P) {
                    int64_t pkt_pts = info->frame->best_effort_timestamp;
                    bool isLastPacket = false;
                    if (info->pkt.size == 0) {
                        readNextPacket = false;
                        isLastPacket = av_read_frame(info->fmt_ctx, &info->pkt) < 0;
                    }
                    if (pkt_pts >= pts || isLastPacket) {
                        writeFrameToBitmap(env, info, data, bitmap, stride);
                        finished = true;
                    }
                }
                av_frame_unref(info->frame);
                if (finished) {
                    return 1;
                }
            } else {
                readNextPacket = true;
            }
            tries--;
        }
        return 0;
    }
}

extern "C" JNIEXPORT jint JNICALL Java_org_telegram_ui_Components_AnimatedFileDrawable_getVideoFrame(JNIEnv *env, jclass clazz, jlong ptr, jobject bitmap, jintArray data, jint stride, jboolean preview, jfloat start_time, jfloat end_time, jboolean loop) {
    if (ptr == NULL) {
        return 0;
    }
    //int64_t time = ConnectionsManager::getInstance(0).getCurrentTimeMonotonicMillis();
    VideoInfo *info = (VideoInfo *) (intptr_t) ptr;
    int ret = 0;
    int got_frame = 0;
    int32_t triesCount = preview ? 50 : 6;
    //info->has_decoded_frames = false;
    while (!info->stopped && triesCount != 0) {
        if (info->stream != nullptr) {
            JNIEnv *jniEnv = nullptr;
            JavaVMAttachArgs jvmArgs;
            jvmArgs.version = JNI_VERSION_1_6;

            bool attached;
            if (JNI_EDETACHED == javaVm->GetEnv((void **) &jniEnv, JNI_VERSION_1_6)) {
                javaVm->AttachCurrentThread(&jniEnv, &jvmArgs);
                attached = true;
            } else {
                attached = false;
            }
            jboolean canceled = jniEnv->CallBooleanMethod(info->stream, jclass_AnimatedFileDrawableStream_isCanceled);
            if (attached) {
                javaVm->DetachCurrentThread();
            }
            if (canceled) {
                return 0;
            }
        }
        if (info->pkt.size == 0) {
            ret = av_read_frame(info->fmt_ctx, &info->pkt);
            if (ret >= 0) {
                double pts = info->pkt.pts * av_q2d(info->video_stream->time_base);
                if (end_time > 0 && info->pkt.stream_index == info->video_stream_idx && pts > end_time) {
                    av_packet_unref(&info->pkt);
                    info->pkt.data = NULL;
                    info->pkt.size = 0;
                } else {
                    info->orig_pkt = info->pkt;
                }
            }
        }

        if (info->pkt.size > 0) {
            ret = decode_packet(info, &got_frame);
            if (ret < 0) {
                if (info->has_decoded_frames) {
                    ret = 0;
                }
                info->pkt.size = 0;
            } else {
                //LOGD("read size %d from packet", ret);
                info->pkt.data += ret;
                info->pkt.size -= ret;
            }

            if (info->pkt.size == 0) {
                av_packet_unref(&info->orig_pkt);
            }
        } else {
            info->pkt.data = NULL;
            info->pkt.size = 0;
            ret = decode_packet(info, &got_frame);
            if (ret < 0) {
                LOGE("can't decode packet flushed %s", info->src);
                return 0;
            }
            if (!preview && got_frame == 0 && info->has_decoded_frames) {
                if (!loop) {
                    return 0;
                }
                int64_t start_from = 0;
                if (start_time > 0) {
                    start_from = (int64_t)(start_time / av_q2d(info->video_stream->time_base));
                }
                if ((ret = av_seek_frame(info->fmt_ctx, info->video_stream_idx, start_from, AVSEEK_FLAG_BACKWARD | AVSEEK_FLAG_FRAME)) < 0) {
                    LOGE("can't seek to begin of file %s, %s", info->src, av_err2str(ret));
                    return 0;
                } else {
                    avcodec_flush_buffers(info->video_dec_ctx);
                }
            }
        }
        if (ret < 0 || info->seeking) {
            return 0;
        }
        if (got_frame) {
            //LOGD("decoded frame with w = %d, h = %d, format = %d", info->frame->width, info->frame->height, info->frame->format);
            if (bitmap != nullptr && (info->frame->format == AV_PIX_FMT_YUV420P || info->frame->format == AV_PIX_FMT_BGRA || info->frame->format == AV_PIX_FMT_YUVJ420P || info->frame->format == AV_PIX_FMT_YUV444P || info->frame->format == AV_PIX_FMT_YUVA420P)) {
                writeFrameToBitmap(env, info, data, bitmap, stride);
            }
            info->has_decoded_frames = true;
            push_time(env, info, data);
            av_frame_unref(info->frame);
            return 1;
        }
        if (!info->has_decoded_frames) {
            triesCount--;
        }
    }
    return 0;
}

extern "C" jint videoOnJNILoad(JavaVM *vm, JNIEnv *env) {
    //av_log_set_callback(custom_log);
    DEBUG_REF("gifvideo.cpp AnimatedFileDrawableStream ref");
    jclass_AnimatedFileDrawableStream = (jclass) env->NewGlobalRef(env->FindClass("org/telegram/messenger/AnimatedFileDrawableStream"));
    if (jclass_AnimatedFileDrawableStream == 0) {
        return JNI_FALSE;
    }
    jclass_AnimatedFileDrawableStream_read = env->GetMethodID(jclass_AnimatedFileDrawableStream, "read", "(II)I");
    if (jclass_AnimatedFileDrawableStream_read == 0) {
        return JNI_FALSE;
    }
    jclass_AnimatedFileDrawableStream_cancel = env->GetMethodID(jclass_AnimatedFileDrawableStream, "cancel", "()V");
    if (jclass_AnimatedFileDrawableStream_cancel == 0) {
        return JNI_FALSE;
    }
    jclass_AnimatedFileDrawableStream_isFinishedLoadingFile = env->GetMethodID(jclass_AnimatedFileDrawableStream, "isFinishedLoadingFile", "()Z");
    if (jclass_AnimatedFileDrawableStream_isFinishedLoadingFile == 0) {
        return JNI_FALSE;
    }
    jclass_AnimatedFileDrawableStream_isCanceled = env->GetMethodID(jclass_AnimatedFileDrawableStream, "isCanceled", "()Z");
    if (jclass_AnimatedFileDrawableStream_isCanceled == 0) {
        return JNI_FALSE;
    }
    jclass_AnimatedFileDrawableStream_getFinishedFilePath = env->GetMethodID(jclass_AnimatedFileDrawableStream, "getFinishedFilePath", "()Ljava/lang/String;");
    if (jclass_AnimatedFileDrawableStream_getFinishedFilePath == 0) {
        return JNI_FALSE;
    }

    return JNI_TRUE;
}
