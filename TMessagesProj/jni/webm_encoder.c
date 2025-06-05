#include <jni.h>
#include "libavformat/avio.h"
#include "libavcodec/codec.h"
#include "libavformat/avformat.h"
#include "c_utils.h"
#include "libavutil/opt.h"
#include "libswscale/swscale.h"

typedef struct {
    AVCodecContext *codec_ctx;
    AVFormatContext *fmt_ctx;
    AVStream *video_stream;
    AVFrame *frame;
    int frame_count;
    struct SwsContext *sws_ctx;
} EncoderContext;

JNIEXPORT jlong JNICALL Java_org_telegram_messenger_video_WebmEncoder_createEncoder(
    JNIEnv *env, jobject obj,
    jstring outputPath_,
    jint width, jint height,
    jint fps,
    jlong bitrate
) {

    int ret;
    const char* outputPath = (*env)->GetStringUTFChars(env, outputPath_, 0);

    EncoderContext* ctx = (EncoderContext*) malloc(sizeof(EncoderContext));
    if (!ctx) {
        LOGE("vp9: failed to alloc context");
        return (jlong)0;
    }
    memset(ctx, 0, sizeof(EncoderContext));

    avformat_alloc_output_context2(&ctx->fmt_ctx, NULL, "matroska", outputPath);
    if (!ctx->fmt_ctx) {
        LOGE("vp9: no context created!");
        return (jlong)0;
    }

    if (!(ctx->fmt_ctx->oformat->flags & AVFMT_NOFILE)) {
        ret = avio_open(&ctx->fmt_ctx->pb, outputPath, AVIO_FLAG_WRITE);
        if (ret < 0) {
            LOGE("vp9: failed to write open file %d", ret);
            return (jlong) 0;
        }
    }

    AVCodec* codec = avcodec_find_encoder(AV_CODEC_ID_VP9);
    if (!codec) {
        LOGE("vp9: no encoder found!");
        return 0;
    }

    ctx->codec_ctx = avcodec_alloc_context3(codec);
    if (!ctx->codec_ctx) {
        LOGE("vp9: failed to create codec ctx");
        return (jlong) 0;
    }

    ctx->codec_ctx->codec_id = AV_CODEC_ID_VP9;
    ctx->codec_ctx->codec_type = AVMEDIA_TYPE_VIDEO;
    ctx->codec_ctx->width = width;
    ctx->codec_ctx->height = height;
    ctx->codec_ctx->pix_fmt = AV_PIX_FMT_YUVA420P;
    ctx->codec_ctx->color_range = AVCOL_RANGE_MPEG;
    ctx->codec_ctx->color_primaries = AVCOL_PRI_BT709;
    ctx->codec_ctx->colorspace = AVCOL_SPC_BT709;
    ctx->codec_ctx->time_base = (AVRational){ 1, fps };
    ctx->codec_ctx->framerate = (AVRational){ fps, 1 };
    ctx->codec_ctx->bit_rate = bitrate;
    ctx->codec_ctx->rc_min_rate = bitrate / 8;
    ctx->codec_ctx->rc_max_rate = bitrate;
//    ctx->codec_ctx->rc_buffer_size = 2 * bitrate;

    if (ctx->fmt_ctx->oformat->flags & AVFMT_GLOBALHEADER) {
        ctx->codec_ctx->flags |= AV_CODEC_FLAG_GLOBAL_HEADER;
    }

    ctx->video_stream = avformat_new_stream(ctx->fmt_ctx, codec);
    if (!ctx->video_stream) {
        LOGE("vp9: failed to create stream");
        return (jlong) 0;
    }

    ctx->video_stream->codecpar->codec_id = ctx->codec_ctx->codec_id;
    ctx->video_stream->codecpar->codec_type = ctx->codec_ctx->codec_type;
    ctx->video_stream->codecpar->width = ctx->codec_ctx->width;
    ctx->video_stream->codecpar->height = ctx->codec_ctx->height;
    ctx->video_stream->codecpar->format = ctx->codec_ctx->pix_fmt;
    ctx->video_stream->time_base = ctx->codec_ctx->time_base;

    ret = avcodec_open2(ctx->codec_ctx, codec, NULL);
    if (ret < 0) {
        LOGE("vp9: failed to open codec %s", av_err2str(ret));
        return (jlong) 0;
    }

    ctx->sws_ctx = sws_getContext(width, height, AV_PIX_FMT_RGBA, width, height, AV_PIX_FMT_YUVA420P, 0, 0, 0, 0);
    if (!ctx->sws_ctx) {
        LOGE("vp9: failed to sws_ctx");
        return (jlong) 0;
    }

    ctx->frame = av_frame_alloc();
    if (!ctx->frame) {
        LOGE("vp9: failed to alloc frame");
        return (jlong)0;
    }

    ctx->frame->format = ctx->codec_ctx->pix_fmt;
    ctx->frame->width = ctx->codec_ctx->width;
    ctx->frame->height = ctx->codec_ctx->height;
    ret = av_frame_get_buffer(ctx->frame, 0);
    if (ret < 0) {
        LOGE("vp9: failed to get frame buffer %d", ret);
        return (jlong)0;
    }

    if (avcodec_parameters_from_context(ctx->video_stream->codecpar, ctx->codec_ctx) < 0) {
        LOGE("vp9: failed to copy codec parameters to stream");
        return (jlong) 0;
    }

    ret = avformat_write_header(ctx->fmt_ctx, NULL);
    if (ret < 0) {
        LOGE("vp9: failed to write header %d", ret);
        return (jlong) 0;
    }

    (*env)->ReleaseStringUTFChars(env, outputPath_, outputPath);

    return (jlong)ctx;
}

JNIEXPORT jboolean JNICALL Java_org_telegram_messenger_video_WebmEncoder_writeFrame(
    JNIEnv *env, jobject obj,
    jlong ptr,
    jobject argbPixels,
    jint width, jint height
) {
    EncoderContext *ctx = (EncoderContext *) ptr;

    uint8_t *pixels = (*env)->GetDirectBufferAddress(env, argbPixels);

    if (!ctx || !pixels) {
        LOGE("vp9: no ctx or no pixels");
        return JNI_FALSE;
    }

    int ret;
    AVPacket pkt;
    av_init_packet(&pkt);
    pkt.data = NULL;
    pkt.size = 0;

    ret = av_frame_make_writable(ctx->frame);
    if (ret < 0) {
        LOGE("vp9: failed to make writable %d", ret);
        return JNI_FALSE;
    }

    const uint8_t* srcSlice[1] = { pixels };
    int srcStride[1] = { 4 * width };
    sws_scale(ctx->sws_ctx, srcSlice, srcStride, 0, ctx->codec_ctx->height, ctx->frame->data, ctx->frame->linesize);

    ctx->frame->pts = ctx->frame_count++;

    ret = avcodec_send_frame(ctx->codec_ctx, ctx->frame);
    if (ret < 0) {
        LOGE("vp9: failed to send packet %d", ret);
        return JNI_FALSE;
    }

    while (ret >= 0) {
        ret = avcodec_receive_packet(ctx->codec_ctx, &pkt);
        if (ret == AVERROR(EAGAIN) || ret == AVERROR_EOF) {
            break;
        } else if (ret < 0) {
            LOGE("vp9: failed to receive packet %d", ret);
            return JNI_FALSE;
        }

        av_packet_rescale_ts(&pkt, ctx->codec_ctx->time_base, ctx->video_stream->time_base);
        pkt.stream_index = ctx->video_stream->index;

        ret = av_interleaved_write_frame(ctx->fmt_ctx, &pkt);
        if (ret < 0) {
            LOGE("vp9: failed to av_interleaved_write_frame %d", ret);
        }
        av_packet_unref(&pkt);
    }

    return JNI_TRUE;
}

JNIEXPORT void JNICALL Java_org_telegram_messenger_video_WebmEncoder_stop(
    JNIEnv *env, jobject obj,
    jlong ptr
) {
    EncoderContext *ctx = (EncoderContext *) ptr;
    if (!ctx || !ctx->fmt_ctx) {
        return;
    }

    int ret;
    ret = avcodec_send_frame(ctx->codec_ctx, NULL);
    if (ret < 0) {
        LOGE("vp9: failed to avcodec_send_frame %d", ret);
    }
    AVPacket pkt;
    av_init_packet(&pkt);
    pkt.data = NULL;
    pkt.size = 0;

    while (1) {
        ret = avcodec_receive_packet(ctx->codec_ctx, &pkt);
        if (ret == AVERROR(EAGAIN) || ret == AVERROR_EOF) {
            break;
        } else if (ret < 0) {
            LOGE("vp9: failed to receive packet %d", ret);
            return;
        }

        av_packet_rescale_ts(&pkt, ctx->codec_ctx->time_base, ctx->video_stream->time_base);
        pkt.stream_index = ctx->video_stream->index;

        ret = av_interleaved_write_frame(ctx->fmt_ctx, &pkt);
        if (ret < 0) {
            LOGE("vp9: failed to av_interleaved_write_frame %d", ret);
        }
        av_packet_unref(&pkt);
    }

    ret = av_write_trailer(ctx->fmt_ctx);
    if (ret < 0) {
        LOGE("vp9: failed to av_write_trailer %d", ret);
    }

    if (ctx->frame) {
        av_frame_free(&ctx->frame);
    }
    if (ctx->codec_ctx) {
        avcodec_free_context(&ctx->codec_ctx);
    }
    if (ctx->sws_ctx) {
        sws_freeContext(ctx->sws_ctx);
    }
    if (ctx->fmt_ctx) {
        if (!(ctx->fmt_ctx->oformat->flags & AVFMT_NOFILE)) {
            avio_closep(&ctx->fmt_ctx->pb);
        }
        avformat_free_context(ctx->fmt_ctx);
    }

    free(ctx);
}