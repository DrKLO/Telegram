#include <jni.h>
#include <utils.h>
#include <libyuv.h>
#include <android/bitmap.h>
#include <cstdint>
#include <limits>
#include <string>

extern "C" {
#include <libavformat/avformat.h>
#include <libavutil/eval.h>
    
static const std::string av_make_error_str(int errnum) {
    char errbuf[AV_ERROR_MAX_STRING_SIZE];
    av_strerror(errnum, errbuf, AV_ERROR_MAX_STRING_SIZE);
    return (std::string) errbuf;
}
    
#undef av_err2str
#define av_err2str(errnum) av_make_error_str(errnum).c_str()

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
        av_free_packet(&orig_pkt);
        
        video_stream_idx = -1;
        video_stream = nullptr;
    }
    
    AVFormatContext *fmt_ctx = nullptr;
    char *src = nullptr;
    int video_stream_idx = -1;
    AVStream *video_stream = nullptr;
    AVCodecContext *video_dec_ctx = nullptr;
    AVFrame *frame = nullptr;
    bool has_decoded_frames = false;
    AVPacket pkt;
    AVPacket orig_pkt;
};

jobject makeGlobarRef(JNIEnv *env, jobject object) {
    if (object) {
        return env->NewGlobalRef(object);
    }
    return 0;
}

int gifvideoOnJNILoad(JavaVM *vm, JNIEnv *env) {
    av_register_all();
    return 0;
}

int open_codec_context(int *stream_idx, AVFormatContext *fmt_ctx, enum AVMediaType type) {
    int ret;
    AVStream *st;
    AVCodecContext *dec_ctx = NULL;
    AVCodec *dec = NULL;
    AVDictionary *opts = NULL;
    
    ret = av_find_best_stream(fmt_ctx, type, -1, -1, NULL, 0);
    if (ret < 0) {
        LOGE("can't find %s stream in input file\n", av_get_media_type_string(type));
        return ret;
    } else {
        *stream_idx = ret;
        st = fmt_ctx->streams[*stream_idx];
        
        dec_ctx = st->codec;
        dec = avcodec_find_decoder(dec_ctx->codec_id);
        if (!dec) {
            LOGE("failed to find %s codec\n", av_get_media_type_string(type));
            return ret;
        }
        
        av_dict_set(&opts, "refcounted_frames", "1", 0);
        if ((ret = avcodec_open2(dec_ctx, dec, &opts)) < 0) {
            LOGE("failed to open %s codec\n", av_get_media_type_string(type));
            return ret;
        }
    }
    
    return 0;
}
    
int decode_packet(VideoInfo *info, int *got_frame) {
    int ret = 0;
    int decoded = info->pkt.size;
    
    *got_frame = 0;
    if (info->pkt.stream_index == info->video_stream_idx) {
        ret = avcodec_decode_video2(info->video_dec_ctx, info->frame, got_frame, &info->pkt);
        if (ret != 0) {
            return ret;
        }
    }
    
    return decoded;
}

jint Java_org_telegram_ui_Components_AnimatedFileDrawable_createDecoder(JNIEnv *env, jclass clazz, jstring src, jintArray data) {
    VideoInfo *info = new VideoInfo();
    
    char const *srcString = env->GetStringUTFChars(src, 0);
    int len = strlen(srcString);
    info->src = new char[len + 1];
    memcpy(info->src, srcString, len);
    info->src[len] = '\0';
    if (srcString != 0) {
        env->ReleaseStringUTFChars(src, srcString);
    }
    
    int ret;
    if ((ret = avformat_open_input(&info->fmt_ctx, info->src, NULL, NULL)) < 0) {
        LOGE("can't open source file %s, %s", info->src, av_err2str(ret));
        delete info;
        return 0;
    }
    
    if ((ret = avformat_find_stream_info(info->fmt_ctx, NULL)) < 0) {
        LOGE("can't find stream information %s, %s", info->src, av_err2str(ret));
        delete info;
        return 0;
    }
    
    if (open_codec_context(&info->video_stream_idx, info->fmt_ctx, AVMEDIA_TYPE_VIDEO) >= 0) {
        info->video_stream = info->fmt_ctx->streams[info->video_stream_idx];
        info->video_dec_ctx = info->video_stream->codec;
    }
    
    if (info->video_stream <= 0) {
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
        AVDictionaryEntry *rotate_tag = av_dict_get(info->video_stream->metadata, "rotate", NULL, 0);
        if (rotate_tag && *rotate_tag->value && strcmp(rotate_tag->value, "0")) {
            char *tail;
            dataArr[2] = (int) av_strtod(rotate_tag->value, &tail);
            if (*tail) {
                dataArr[2] = 0;
            }
        } else {
            dataArr[2] = 0;
        }
        env->ReleaseIntArrayElements(data, dataArr, 0);
    }
    
    //LOGD("successfully opened file %s", info->src);
    
    return (int) info;
}

void Java_org_telegram_ui_Components_AnimatedFileDrawable_destroyDecoder(JNIEnv *env, jclass clazz, jobject ptr) {
    if (ptr == NULL) {
        return;
    }
    VideoInfo *info = (VideoInfo *) ptr;
    delete info;
}

    
jint Java_org_telegram_ui_Components_AnimatedFileDrawable_getVideoFrame(JNIEnv *env, jclass clazz, jobject ptr, jobject bitmap, jintArray data) {
    if (ptr == NULL || bitmap == nullptr) {
        return 0;
    }
    VideoInfo *info = (VideoInfo *) ptr;
    int ret = 0;
    int got_frame = 0;
    
    while (true) {
        if (info->pkt.size == 0) {
            ret = av_read_frame(info->fmt_ctx, &info->pkt);
            //LOGD("got packet with size %d", info->pkt.size);
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
                //LOGD("read size %d from packet", ret);
                info->pkt.data += ret;
                info->pkt.size -= ret;
            }
            
            if (info->pkt.size == 0) {
                av_free_packet(&info->orig_pkt);
            }
        } else {
            info->pkt.data = NULL;
            info->pkt.size = 0;
            ret = decode_packet(info, &got_frame);
            if (ret < 0) {
                LOGE("can't decode packet flushed %s", info->src);
                return 0;
            }
            if (got_frame == 0) {
                if (info->has_decoded_frames) {
                    //LOGD("file end reached %s", info->src);
                    if ((ret = avformat_seek_file(info->fmt_ctx, -1, std::numeric_limits<int64_t>::min(), 0, std::numeric_limits<int64_t>::max(), 0)) < 0) {
                        LOGE("can't seek to begin of file %s, %s", info->src, av_err2str(ret));
                        return 0;
                    } else {
                        avcodec_flush_buffers(info->video_dec_ctx);
                    }
                }
            }
        }
        if (ret < 0) {
            return 0;
        }
        if (got_frame) {
            //LOGD("decoded frame with w = %d, h = %d, format = %d", info->frame->width, info->frame->height, info->frame->format);
            if (info->frame->format == AV_PIX_FMT_YUV420P || info->frame->format == AV_PIX_FMT_BGRA || info->frame->format == AV_PIX_FMT_YUVJ420P) {
                jint *dataArr = env->GetIntArrayElements(data, 0);
                if (dataArr != nullptr) {
                    dataArr[3] = (int) (1000 * info->frame->pkt_pts * av_q2d(info->video_stream->time_base));
                    env->ReleaseIntArrayElements(data, dataArr, 0);
                }
                
                void *pixels;
                if (AndroidBitmap_lockPixels(env, bitmap, &pixels) >= 0) {
                    if (info->frame->format == AV_PIX_FMT_YUV420P || info->frame->format == AV_PIX_FMT_YUVJ420P) {
                        //LOGD("y %d, u %d, v %d, width %d, height %d", info->frame->linesize[0], info->frame->linesize[2], info->frame->linesize[1], info->frame->width, info->frame->height);
                        libyuv::I420ToARGB(info->frame->data[0], info->frame->linesize[0], info->frame->data[2], info->frame->linesize[2], info->frame->data[1], info->frame->linesize[1], (uint8_t *) pixels, info->frame->width * 4, info->frame->width, info->frame->height);
                    } else if (info->frame->format == AV_PIX_FMT_BGRA) {
                        libyuv::ABGRToARGB(info->frame->data[0], info->frame->linesize[0], (uint8_t *) pixels, info->frame->width * 4, info->frame->width, info->frame->height);
                    }
                    AndroidBitmap_unlockPixels(env, bitmap);
                }
            }
            info->has_decoded_frames = true;
            av_frame_unref(info->frame);
            return 1;
        }
    }
    return 0;
}
}
