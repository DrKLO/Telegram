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
#include "gifvideo/sws_context_holder.h"
#include "gifvideo/video_frame_reader.h"
#include <cmath>

extern "C" {
#include <libavformat/avformat.h>
#include <libavutil/eval.h>
#include <libswscale/swscale.h>
#include <libavutil/display.h>
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

struct OffsetIOContext;
struct VideoInfo;
static void freeOffsetIO(VideoInfo *info);

typedef struct VideoInfo {

    ~VideoInfo() {
        delete reader;
        reader = nullptr;

        if (video_dec_ctx) {
            // avcodec_close() frees internals but NOT the context allocated by
            // avcodec_alloc_context3(); avcodec_free_context() frees both.
            avcodec_free_context(&video_dec_ctx);
        }
        if (fmt_ctx) {
            avformat_close_input(&fmt_ctx);
            fmt_ctx = nullptr;
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
        freeOffsetIO(this);
        if (fd >= 0) {
            close(fd);
            fd = -1;
        }

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
    // Borrows fmt_ctx and video_dec_ctx; must be deleted before them (see dtor).
    VideoFrameReader *reader = nullptr;
    bool stopped = false;
    bool seeking = false;
    bool afterEof = false;
    bool isSingleFrame = false;

    struct SwsContextHolder sws_ctx_holder;

    AVIOContext *ioContext = nullptr;
    unsigned char *ioBuffer = nullptr;
    // Custom AVIO for the fileOffset path in nGetVideoInfo. Owned here because
    // fmt_ctx->pb is not freed by avformat_close_input for a caller-supplied pb.
    AVIOContext *offsetIoContext = nullptr;
    struct OffsetIOContext *offsetIoOpaque = nullptr;
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
    const AVCodec *dec = NULL;
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
        ret = avcodec_open2(*dec_ctx, dec, &opts);
        av_dict_free(&opts);   // frees leftover (unconsumed) options on all paths
        if (ret < 0) {
            LOGE("Failed to open %s codec", av_get_media_type_string(type));
            return ret;
        }
        *stream_idx = stream_index;
    }

    return 0;
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

struct OffsetIOContext {
    int fd;
    int64_t offset;
};

static int offsetRead(void *opaque, uint8_t *buf, int size) {
    OffsetIOContext *ctx = (OffsetIOContext *)opaque;
    return read(ctx->fd, buf, size);
}

static int64_t offsetSeek(void *opaque, int64_t pos, int whence) {
    OffsetIOContext *ctx = (OffsetIOContext *)opaque;
    if (whence == AVSEEK_SIZE) return -1;  // unknown size, FFmpeg handles this
    return lseek(ctx->fd, ctx->offset + pos, whence);
}

// Frees the custom AVIO built for the fileOffset path (see nGetVideoInfo).
// Safe to call when nothing was allocated: all fields default to null/-1.
static void freeOffsetIO(VideoInfo *info) {
    if (info->offsetIoContext != nullptr) {
        av_freep(&info->offsetIoContext->buffer);
        avio_context_free(&info->offsetIoContext);
        info->offsetIoContext = nullptr;
    }
    if (info->offsetIoOpaque != nullptr) {
        if (info->offsetIoOpaque->fd >= 0) {
            close(info->offsetIoOpaque->fd);
        }
        delete info->offsetIoOpaque;
        info->offsetIoOpaque = nullptr;
    }
}

int getVideoRotation(const AVStream *stream) {
    const AVPacketSideData *displayMatrix = av_packet_side_data_get(
            stream->codecpar->coded_side_data,
            stream->codecpar->nb_coded_side_data,
            AV_PKT_DATA_DISPLAYMATRIX);
    if (displayMatrix != nullptr && displayMatrix->size >= 9 * sizeof(int32_t)) {
        // av_display_rotation_get() returns the counter-clockwise angle; negate
        // to match the legacy clockwise convention, then normalize.
        double theta = -av_display_rotation_get((const int32_t *) displayMatrix->data);
        if (!std::isnan(theta)) {
            int rotation = ((int) lround(theta / 90.0) * 90) % 360;
            return rotation < 0 ? rotation + 360 : rotation;
        }
        return 0;
    }

    // Fallback for old containers that still carry the legacy metadata tag.
    AVDictionaryEntry *rotate_tag = av_dict_get(stream->metadata, "rotate", nullptr, 0);
    if (rotate_tag && *rotate_tag->value && strcmp(rotate_tag->value, "0") != 0) {
        char *tail;
        int rotation = (int) av_strtod(rotate_tag->value, &tail);
        if (*tail == '\0') {
            rotation = ((rotation / 90) * 90) % 360;
            return rotation < 0 ? rotation + 360 : rotation;
        }
    }
    return 0;
}


extern "C" JNIEXPORT void JNICALL Java_org_telegram_ui_Components_AnimatedFileNative_nGetVideoInfo(JNIEnv *env, jclass clazz, jstring src, jintArray data, jlong fileOffset) {
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
    if (fileOffset > 0) {
        int fd = open(info->src, O_RDONLY);
        if (fd < 0) {
            LOGE("can't open source file %s", info->src);
            delete info;
            return;
        }
        if (lseek(fd, fileOffset, SEEK_SET) < 0) {
            LOGE("can't seek to offset %lld in file %s", (long long)fileOffset, info->src);
            close(fd);
            delete info;
            return;
        }

        OffsetIOContext *ioCtx = new OffsetIOContext{fd, fileOffset};
        uint8_t *ioBuf = (uint8_t *)av_malloc(32 * 1024);
        AVIOContext *avio = avio_alloc_context(ioBuf, 32 * 1024, 0, ioCtx, offsetRead, nullptr, offsetSeek);

        // Owned by info from here on: ~VideoInfo -> freeOffsetIO frees avio, its
        // buffer, ioCtx and its fd. avformat_close_input won't free a custom pb.
        info->offsetIoContext = avio;
        info->offsetIoOpaque = ioCtx;

        info->fmt_ctx = avformat_alloc_context();
        info->fmt_ctx->pb = avio;

        if ((ret = avformat_open_input(&info->fmt_ctx, nullptr, nullptr, nullptr)) < 0) {
            LOGE("can't open source file at offset %s (offset=%lld), %s", info->src, fileOffset, av_err2str(ret));
            info->fmt_ctx = nullptr;
            delete info;
            return;
        }
    } else {
        if ((ret = avformat_open_input(&info->fmt_ctx, info->src, nullptr, nullptr)) < 0) {
            LOGE("can't open source file %s, %s", info->src, av_err2str(ret));
            delete info;
            return;
        }
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
                info->video_stream->codecpar->codec_id == AV_CODEC_ID_HEVC;

        /*
        if (strstr(info->fmt_ctx->iformat->name, "mov") != 0 && dataArr[PARAM_NUM_SUPPORTED_VIDEO_CODEC]) {
            MOVStreamContext *mov = (MOVStreamContext *) info->video_stream->priv_data;
            dataArr[PARAM_NUM_VIDEO_FRAME_SIZE] = (jint) mov->data_size;

            if (info->audio_stream != nullptr) {
                mov = (MOVStreamContext *) info->audio_stream->priv_data;
                dataArr[PARAM_NUM_AUDIO_FRAME_SIZE] = (jint) mov->data_size;
            }
        }
         */

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
                    info->audio_stream->codecpar->codec_id == AV_CODEC_ID_OPUS;
            dataArr[PARAM_NUM_HAS_AUDIO] = 1;
        } else {
            dataArr[PARAM_NUM_HAS_AUDIO] = 0;
        }

        dataArr[PARAM_NUM_BITRATE] = (jint) info->video_stream->codecpar->bit_rate;
        dataArr[PARAM_NUM_WIDTH] = info->video_stream->codecpar->width;
        dataArr[PARAM_NUM_HEIGHT] = info->video_stream->codecpar->height;
        dataArr[PARAM_NUM_ROTATION] = (jint) getVideoRotation(info->video_stream);
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

static bool isStreamCanceled(VideoInfo *info) {
    if (info->stream == nullptr) {
        return false;
    }
    JNIEnv *jniEnv = nullptr;
    JavaVMAttachArgs jvmArgs;
    jvmArgs.version = JNI_VERSION_1_6;

    bool attached = false;
    if (JNI_EDETACHED == javaVm->GetEnv((void **) &jniEnv, JNI_VERSION_1_6)) {
        javaVm->AttachCurrentThread(&jniEnv, &jvmArgs);
        attached = true;
    }
    jboolean canceled = jniEnv->CallBooleanMethod(
            info->stream, jclass_AnimatedFileDrawableStream_isCanceled);
    if (attached) {
        javaVm->DetachCurrentThread();
    }
    return canceled;
}

extern "C" JNIEXPORT jlong JNICALL Java_org_telegram_ui_Components_AnimatedFileNative_nCreateDecoder(JNIEnv *env, jclass clazz, jstring src, jintArray data, jint account, jlong streamFileSize, jobject stream, jboolean preview) {
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
            // avio didn't take ownership of the buffer; free it here. After a
            // successful alloc the buffer lives in ioContext->buffer and is
            // freed by the destructor (and may be reallocated by FFmpeg).
            av_freep(&info->ioBuffer);
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

    info->reader = new VideoFrameReader(info->fmt_ctx, info->video_dec_ctx, info->video_stream_idx);
    VideoInfo *self = info;
    info->reader->shouldAbort = [self]() {
        // Covers nStopDecoder (stopped), nPrepareToSeek (seeking) and stream cancel.
        return self->stopped || self->seeking || isStreamCanceled(self);
    };

    jint *dataArr = env->GetIntArrayElements(data, 0);
    if (dataArr != nullptr) {
        dataArr[0] = info->video_dec_ctx->width;
        dataArr[1] = info->video_dec_ctx->height;
        //float pixelWidthHeightRatio = info->video_dec_ctx->sample_aspect_ratio.num / info->video_dec_ctx->sample_aspect_ratio.den; TODO support
        dataArr[2] = (jint) getVideoRotation(info->video_stream);
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
                /*
                int ticks = video_stream->codec->ticks_per_frame;
                fps = 1.0 / (ticks * av_q2d(video_stream->time_base));
                 */
            }
        }
        dataArr[5] = (int32_t) fps;
        //(int32_t) (1000 * info->video_stream->duration * av_q2d(info->video_stream->time_base));
        env->ReleaseIntArrayElements(data, dataArr, 0);
    }

    //LOGD("successfully opened file %s", info->src);

    return (jlong) (intptr_t) info;
}

extern "C" JNIEXPORT void JNICALL Java_org_telegram_ui_Components_AnimatedFileNative_nDestroyDecoder(JNIEnv *env, jclass clazz, jlong ptr) {
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

extern "C" JNIEXPORT void JNICALL Java_org_telegram_ui_Components_AnimatedFileNative_nStopDecoder(JNIEnv *env, jclass clazz, jlong ptr) {
    if (ptr == NULL) {
        return;
    }
    VideoInfo *info = (VideoInfo *) (intptr_t) ptr;
    info->stopped = true;
}

extern "C" JNIEXPORT void JNICALL Java_org_telegram_ui_Components_AnimatedFileNative_nPrepareToSeek(JNIEnv *env, jclass clazz, jlong ptr) {
    if (ptr == NULL) {
        return;
    }
    VideoInfo *info = (VideoInfo *) (intptr_t) ptr;
    info->seeking = true;
}

void push_time(JNIEnv *env, VideoInfo* info, AVFrame *frame, jintArray data) {
    jint *dataArr = env->GetIntArrayElements(data, 0);
    dataArr[3] = (jint) (1000 * frame->best_effort_timestamp * av_q2d(info->video_stream->time_base));
    env->ReleaseIntArrayElements(data, dataArr, 0);
}

void push_single_frame(JNIEnv *env, jintArray data) {
    jint *dataArr = env->GetIntArrayElements(data, 0);
    dataArr[7] = 1;
    env->ReleaseIntArrayElements(data, dataArr, 0);
}

extern "C" JNIEXPORT void JNICALL Java_org_telegram_ui_Components_AnimatedFileNative_nSeekToMs(JNIEnv *env, jclass clazz, jlong ptr, jlong ms, jintArray data, jboolean precise) {
    if (ptr == 0) {
        return;
    }
    VideoInfo *info = (VideoInfo *) (intptr_t) ptr;
    info->seeking = false;   // clear before decoding so shouldAbort() won't bail
    info->afterEof = false;

    AVRational tb = info->video_stream->time_base;
    int64_t pts = (int64_t) (ms / av_q2d(tb) / 1000);

    if (!info->reader->seek(pts)) {
        LOGE("can't seek file %s", info->src);
        return;
    }

    if (!precise) {
        // Non-precise: land on the keyframe, don't decode toward the target.
        return;
    }

    double targetSec = ms / 1000.0;
    for (;;) {
        VideoFrameReader::Status st = info->reader->getNextFrame();
        if (st != VideoFrameReader::Status::Ok) {
            // Eof: target lies past the last frame -> rewind to start, as before.
            // Aborted/Error: stop without advancing.
            if (st == VideoFrameReader::Status::Eof) {
                info->reader->seek(0);
            }
            return;
        }

        if (info->reader->frameTimeSeconds() >= targetSec) {
            push_time(env, info, info->reader->frame(), data);
            return;
        }
    }
}

static inline void writeFrameToBitmap(JNIEnv *env, VideoInfo *info, AVFrame *frame, jintArray data, jobject bitmap) {
    if (env->IsSameObject(bitmap, NULL)) {
        push_time(env, info, frame, data);
        return;
    }
    jint *dataArr = env->GetIntArrayElements(data, 0);
    int32_t wantedWidth;
    int32_t wantedHeight;

    AndroidBitmapInfo bitmapInfo;
    AndroidBitmap_getInfo(env, bitmap, &bitmapInfo);
    int32_t bitmapWidth = bitmapInfo.width;
    int32_t bitmapHeight = bitmapInfo.height;
    int32_t bitmapStride = bitmapInfo.stride;

    if (dataArr != nullptr) {
        wantedWidth = dataArr[0];
        wantedHeight = dataArr[1];
        dataArr[3] = (jint) (1000 * frame->best_effort_timestamp * av_q2d(info->video_stream->time_base));
        if (env->GetArrayLength(data) > 6) {
            bool isOpaque = (
                frame->format == AV_PIX_FMT_YUV420P  ||
                frame->format == AV_PIX_FMT_YUVJ420P ||
                frame->format == AV_PIX_FMT_YUV444P
            );
            dataArr[6] = isOpaque ? 1 : 0;
        }
        env->ReleaseIntArrayElements(data, dataArr, 0);
    } else {
        wantedWidth = bitmapWidth;
        wantedHeight = bitmapHeight;
    }

    if (!(wantedWidth == frame->width && wantedHeight == frame->height || wantedWidth == frame->height && wantedHeight == frame->width)) {
        return;
    }

    void *pixels;
    if (__builtin_expect(AndroidBitmap_lockPixels(env, bitmap, &pixels) != ANDROID_BITMAP_RESULT_SUCCESS, 0)) {
        return;
    }

    SwsContext* sws_ctx = nullptr;
    if (frame->format > AV_PIX_FMT_NONE && frame->format < AV_PIX_FMT_NB && frame->format != AV_PIX_FMT_YUVA420P) {
        sws_ctx = info->sws_ctx_holder.get(
            frame->width,
            frame->height,
            (AVPixelFormat) frame->format,
            bitmapWidth,
            bitmapHeight,
            AV_PIX_FMT_RGBA);
    } else if (info->video_dec_ctx->pix_fmt > AV_PIX_FMT_NONE && info->video_dec_ctx->pix_fmt < AV_PIX_FMT_NB && frame->format != AV_PIX_FMT_YUVA420P) {
        sws_ctx = info->sws_ctx_holder.get(
            info->video_dec_ctx->width,
            info->video_dec_ctx->height,
            info->video_dec_ctx->pix_fmt,
            bitmapWidth,
            bitmapHeight,
            AV_PIX_FMT_RGBA);
    }

    if (sws_ctx != nullptr && ((intptr_t) pixels) % 16 == 0) {
        uint8_t __attribute__ ((aligned (16))) *dst_data[1];
        dst_data[0] = (uint8_t *) pixels;

        int32_t dst_stride[1];
        dst_stride[0] = bitmapStride;
        sws_scale(sws_ctx,
            frame->data,
            frame->linesize,
            0,
            frame->height,
            dst_data,
            dst_stride
        );
    } else if (frame->width == bitmapWidth && frame->height == bitmapHeight) {
        if (frame->format == AV_PIX_FMT_YUVA420P) {
            libyuv::I420AlphaToARGBMatrix(
                frame->data[0], frame->linesize[0],
                frame->data[2], frame->linesize[2],
                frame->data[1], frame->linesize[1],
                frame->data[3], frame->linesize[3],
                (uint8_t *) pixels,
                bitmapStride,
                &libyuv::kYvuI601Constants,
                bitmapWidth,
                bitmapHeight,
                1
            );
        } else if (frame->format == AV_PIX_FMT_YUV444P) {
            libyuv::H444ToARGB(
                frame->data[0], frame->linesize[0],
                frame->data[2], frame->linesize[2],
                frame->data[1], frame->linesize[1],
                (uint8_t *) pixels,
                bitmapStride,
                bitmapWidth,
                bitmapHeight
            );
        } else if (frame->format == AV_PIX_FMT_YUV420P || frame->format == AV_PIX_FMT_YUVJ420P) {
            if (frame->colorspace == AVColorSpace::AVCOL_SPC_BT709) {
                libyuv::H420ToARGB(
                    frame->data[0], frame->linesize[0],
                    frame->data[2], frame->linesize[2],
                    frame->data[1], frame->linesize[1],
                    (uint8_t *) pixels,
                    bitmapStride,
                    bitmapWidth,
                    bitmapHeight
                );
            } else {
                libyuv::I420ToARGB(
                    frame->data[0], frame->linesize[0],
                    frame->data[2], frame->linesize[2],
                    frame->data[1], frame->linesize[1],
                    (uint8_t *) pixels,
                    bitmapStride,
                    bitmapWidth,
                    bitmapHeight
                );
            }
        } else if (frame->format == AV_PIX_FMT_BGRA) {
            libyuv::ABGRToARGB(
                frame->data[0], frame->linesize[0],
                (uint8_t *) pixels,
                bitmapStride,
                bitmapWidth,
                bitmapHeight
            );
        }
    } else if (sws_ctx != nullptr && ((intptr_t) pixels) % 16 != 0) {
        // fallback if pixels not aligned
        int alignedStride = FFALIGN(bitmapWidth * 4, 16);
        int bufSize = alignedStride * bitmapHeight;
        uint8_t *alignedBuf = (uint8_t *) av_malloc(bufSize);
        if (alignedBuf != nullptr) {
            uint8_t *dst_data[1] = { alignedBuf };
            int32_t dst_stride[1] = { alignedStride };
            sws_scale(sws_ctx,
                      frame->data,
                      frame->linesize,
                      0,
                      frame->height,
                      dst_data,
                      dst_stride
            );
            if (alignedStride == bitmapStride) {
                memcpy(pixels, alignedBuf, bufSize);
            } else {
                uint8_t *src = alignedBuf;
                uint8_t *dst = (uint8_t *) pixels;
                int copyStride = bitmapWidth * 4;
                for (int i = 0; i < bitmapHeight; i++) {
                    memcpy(dst, src, copyStride);
                    src += alignedStride;
                    dst += bitmapStride;
                }
            }
            av_free(alignedBuf);
        }
    }

    AndroidBitmap_unlockPixels(env, bitmap);
}

extern "C" JNIEXPORT int JNICALL Java_org_telegram_ui_Components_AnimatedFileNative_nGetFrameAtTime(JNIEnv *env, jclass clazz, jlong ptr, jlong ms, jobject bitmap, jintArray data) {
    if (ptr == 0 || bitmap == nullptr || data == nullptr) {
        return 0;
    }
    VideoInfo *info = (VideoInfo *) (intptr_t) ptr;
    info->seeking = false;   // clear before decoding so shouldAbort() won't bail
    info->afterEof = false;

    AVRational tb = info->video_stream->time_base;
    int64_t pts = (int64_t) (ms / av_q2d(tb) / 1000);

    if (!info->reader->seek(pts)) {
        LOGE("can't seek file %s", info->src);
        return 0;
    }

    double targetSec = ms / 1000.0;
    AVFrame *held = av_frame_alloc();   // most recent frame before target (fallback)
    bool haveHeld = false;
    int result = 0;

    for (;;) {
        VideoFrameReader::Status st = info->reader->getNextFrame();
        if (st != VideoFrameReader::Status::Ok) {
            // Eof: target lies at/after the last frame -> emit the last frame we
            // held. Aborted/Error: give up with no frame.
            if (st == VideoFrameReader::Status::Eof && haveHeld) {
                writeFrameToBitmap(env, info, held, data, bitmap);
                result = 1;
            }
            break;
        }

        AVFrame *frame = info->reader->frame();
        if (info->reader->frameTimeSeconds() >= targetSec) {
            writeFrameToBitmap(env, info, frame, data, bitmap);
            result = 1;
            break;
        }

        // Frame is before the target: keep it as the fallback and continue.
        av_frame_unref(held);
        av_frame_ref(held, frame);
        haveHeld = true;
    }

    av_frame_free(&held);
    return result;
}

extern "C" JNIEXPORT jint JNICALL Java_org_telegram_ui_Components_AnimatedFileNative_nGetVideoFrame(JNIEnv *env, jclass clazz, jlong ptr, jobject bitmap, jintArray data, jboolean preview, jfloat start_time, jfloat end_time, jboolean loop) {
    if (ptr == 0) {
        return 0;
    }
    VideoInfo *info = (VideoInfo *) (intptr_t) ptr;
    if (info->stopped || info->seeking) {
        return 0;
    }

    AVRational tb = info->video_stream->time_base;
    int64_t startPts = start_time > 0 ? (int64_t) (start_time / av_q2d(tb)) : 0;

    VideoFrameReader::Status st = info->reader->getNextFrame();

    // End of stream, or the frame ran past the trim point -> loop back or finish.
    // Note: end_time is compared against the frame's display-order timestamp,
    // not a packet pts, so B-frame reordering can no longer drop the wrong frame.
    bool pastEnd = st == VideoFrameReader::Status::Ok &&
                   end_time > 0 && info->reader->frameTimeSeconds() > end_time;

    if (st == VideoFrameReader::Status::Eof) {
        if (info->afterEof && !info->isSingleFrame) {
            push_single_frame(env, data);
            info->isSingleFrame = true;
        }
        info->afterEof = true;
    } else {
        info->afterEof = false;
    }

    if (st == VideoFrameReader::Status::Eof || pastEnd) {
        if (!loop) {
            return 0;
        }
        if (!info->reader->seek(startPts)) {
            return 0;
        }
        st = info->reader->getNextFrame();
    }

    if (st != VideoFrameReader::Status::Ok) {
        // Aborted (stopped / seeking / canceled), Error, or Eof after looping.
        return 0;
    }

    AVFrame *frame = info->reader->frame();
    if (bitmap != nullptr) {
        writeFrameToBitmap(env, info, frame, data, bitmap);
    }
    push_time(env, info, frame, data);
    return 1;
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
