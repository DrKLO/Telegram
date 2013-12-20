#include "video.h"
#include <stdio.h>
#include <libavutil/timestamp.h>
#include <libavutil/imgutils.h>
#include "log.h"

AVPacket pkt;
int video_stream_idx = -1, audio_stream_idx = -1;
AVCodecContext *video_dec_ctx = NULL, *audio_dec_ctx = NULL;
AVFrame *frame = NULL;
AVStream *video_stream = NULL, *audio_stream = NULL;
AVFormatContext *fmt_ctx = NULL;
int64_t total_duration;
int64_t current_duration;
char *src = NULL;
char *dest = NULL;
int lastLog = 10;

void cleanup_in() {
    if (video_dec_ctx) {
        avcodec_close(video_dec_ctx);
        video_dec_ctx = NULL;
    }
    if (audio_dec_ctx) {
        avcodec_close(audio_dec_ctx);
        audio_dec_ctx = NULL;
    }
    if (fmt_ctx) {
        avformat_close_input(&fmt_ctx);
        fmt_ctx = NULL;
    }
    if (frame) {
        av_frame_free(&frame);
        frame = NULL;
    }
    if (src) {
        free(src);
        src = NULL;
    }
    if (dest) {
        free(dest);
        dest = NULL;
    }
    
    total_duration = 0;
    current_duration = 0;
    video_stream_idx = -1;
    audio_stream_idx = -1;
    video_stream = NULL;
    audio_stream = NULL;
    lastLog = 10;
}

void onError() {
    cleanup_in();
    cleanup_out();
}

void onDone() {
    LOGD("OK\n");
    cleanup_in();
    cleanup_out();
}

void onProgress() {
    float progress = (float)current_duration / (float)total_duration * 100;
    if (progress > lastLog) {
        lastLog += 10;
        LOGD("progress %.2f\n", progress);
    }
}

int open_codec_context(int *stream_idx, AVFormatContext *fmt_ctx, enum AVMediaType type) {
    int ret;
    AVStream *st;
    AVCodecContext *dec_ctx = NULL;
    AVCodec *dec = NULL;
    AVDictionary *opts = NULL;
    
    ret = av_find_best_stream(fmt_ctx, type, -1, -1, NULL, 0);
    if (ret < 0) {
        LOGD("Could not find %s stream in input file\n", av_get_media_type_string(type));
        return ret;
    } else {
        *stream_idx = ret;
        st = fmt_ctx->streams[*stream_idx];
        
        dec_ctx = st->codec;
        dec = avcodec_find_decoder(dec_ctx->codec_id);
        if (!dec) {
            LOGD("Failed to find %s codec\n", av_get_media_type_string(type));
            return ret;
        }
        
        av_dict_set(&opts, "refcounted_frames", "1", 0);
        if ((ret = avcodec_open2(dec_ctx, dec, &opts)) < 0) {
            LOGD("Failed to open %s codec\n", av_get_media_type_string(type));
            return ret;
        }
    }
    
    return 0;
}

int decode_packet(int *got_frame, int cached) {
    int ret = 0;
    int decoded = pkt.size;
    
    *got_frame = 0;
    
    if (pkt.stream_index == video_stream_idx) {
        ret = avcodec_decode_video2(video_dec_ctx, frame, got_frame, &pkt);
        if (ret < 0) {
            LOGD("Error decoding video frame\n");
            return ret;
        }
        
        if (*got_frame) {
            ret = write_video_frame(frame);
            if (ret < 0) {
                return ret;
            }
        }
    } else if (pkt.stream_index == audio_stream_idx) {
        ret = avcodec_decode_audio4(audio_dec_ctx, frame, got_frame, &pkt);
        
        if (ret < 0) {
            LOGD("Error decoding audio frame\n");
            return ret;
        }
        decoded = FFMIN(ret, pkt.size);
        
        if (*got_frame) {
            ret = write_audio_frame(frame, audio_dec_ctx);
            if (ret < 0) {
                return -1;
            }
        }
        frame->pts = AV_NOPTS_VALUE;
    }
    
    if (*got_frame) {
        av_frame_unref(frame);
    }
    
    return decoded;
}

void convertFile(const char *src_filename, const char *dst_filename, int bitr) {
    int ret = 0;
    int got_frame;
    
    src = malloc(strlen(src_filename) + 1);
    memcpy(src, src_filename, strlen(src_filename));
    src[strlen(src_filename)] = '\0';
    dest = malloc(strlen(dst_filename) + 1);
    memcpy(dest, dst_filename, strlen(dst_filename));
    dest[strlen(dst_filename)] = '\0';
    
    if ((ret = avformat_open_input(&fmt_ctx, src, NULL, NULL)) < 0) {
        LOGD("Could not open source file %s, %s\n", src, av_err2str(ret));
        onError();
        return;
    }
    
    if (avformat_find_stream_info(fmt_ctx, NULL) < 0) {
        LOGD("Could not find stream information\n");
        onError();
        return;
    }
    
    if (open_codec_context(&video_stream_idx, fmt_ctx, AVMEDIA_TYPE_VIDEO) >= 0) {
        video_stream = fmt_ctx->streams[video_stream_idx];
        video_dec_ctx = video_stream->codec;
    }
    
    if (open_codec_context(&audio_stream_idx, fmt_ctx, AVMEDIA_TYPE_AUDIO) >= 0) {
        audio_stream = fmt_ctx->streams[audio_stream_idx];
        audio_dec_ctx = audio_stream->codec;
    }
    
    av_dump_format(fmt_ctx, 0, src, 0);
    
    if (!audio_stream && !video_stream) {
        LOGD("Could not find audio or video stream in the input, aborting\n");
        onError();
        return;
    }
    
    frame = av_frame_alloc();
    if (!frame) {
        LOGD("Could not allocate frame\n");
        onError();
        return;
    }
    
    av_init_packet(&pkt);
    pkt.data = NULL;
    pkt.size = 0;
    
    if (video_stream) {
        LOGD("Demuxing video from file '%s'\n", src);
    }
    if (audio_stream) {
        LOGD("Demuxing audio from file '%s'\n", src);
    }

    ret = prepare_for_video_conversion(dest, video_dec_ctx, audio_dec_ctx, fmt_ctx, video_stream, audio_stream, bitr);
    if (ret < 0) {
        return;
    }
    if (video_stream) {
        total_duration = video_stream->duration;
    }
    if (audio_stream) {
        total_duration += audio_stream->duration;
    }
    
    while (av_read_frame(fmt_ctx, &pkt) >= 0) {
        AVPacket orig_pkt = pkt;
        do {
            ret = decode_packet(&got_frame, 0);
            if (ret < 0) {
                onError();
                return;
            }
            pkt.data += ret;
            pkt.size -= ret;
            current_duration += pkt.duration;
            onProgress();
        } while (pkt.size > 0);
        av_free_packet(&orig_pkt);
    }
    
    pkt.data = NULL;
    pkt.size = 0;
    do {
        decode_packet(&got_frame, 1);
    } while (got_frame);
    
    post_video_conversion();
    onDone();
}
