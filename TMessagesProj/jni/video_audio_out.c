#include "video.h"
#include <libswscale/swscale.h>
#include <libswresample/swresample.h>
#include <libavutil/opt.h>
#include <libavutil/mathematics.h>
#include "log.h"

AVFrame *out_frame = NULL;
struct SwsContext *sws_ctx = NULL;
AVStream *video_st = NULL, *audio_st = NULL;
AVFormatContext *oc = NULL;
AVOutputFormat *fmt = NULL;
AVPicture dst_picture;

uint8_t **dst_samples_data = NULL;
SwrContext *swr_ctx = NULL;

int current_n_out = 0;
int current_in_buff = 0;
uint8_t buff[4096 * 2];

int min(int val1, int val2) {
    return val1 < val2 ? val1 : val2;
}

int prepare_for_video_conversion(const char *dst_filename, AVCodecContext *video_dec_ctx, AVCodecContext *audio_dec_ctx, AVFormatContext *fmt_ctx, AVStream *src_video_stream, AVStream *src_audio_stream, int bitr) {
    
    if (!video_dec_ctx && !audio_dec_ctx) {
        onError();
        return -1;
    }
    
    avformat_alloc_output_context2(&oc, NULL, "mp4", dst_filename);
    if (!oc) {
        onError();
        return -1;
    }
    fmt = oc->oformat;
    av_dict_copy(&oc->metadata, fmt_ctx->metadata, 0);
    
    int ret = 0;
    if (!(fmt->flags & AVFMT_NOFILE)) {
        ret = avio_open(&oc->pb, dst_filename, AVIO_FLAG_WRITE);
        if (ret < 0) {
            LOGD("Could not open '%s': %s\n", dst_filename, av_err2str(ret));
            onError();
            return -1;
        }
    }
    
    AVCodecContext *c;
    
    if (video_dec_ctx && src_video_stream && fmt_ctx) {
        //calculate video resolution
        int dst_width = video_dec_ctx->width, dst_height = video_dec_ctx->height;
        if (video_dec_ctx->width > video_dec_ctx->height) {
            if (video_dec_ctx->width > 480) {
                float scale = video_dec_ctx->width / 480.0f;
                dst_width = 480;
                dst_height = ceilf(video_dec_ctx->height / scale);
            }
        } else {
            if (video_dec_ctx->width > 480) {
                float scale = video_dec_ctx->height / 480.0f;
                dst_height = 480;
                dst_width = ceilf(video_dec_ctx->width / scale);
            }
        }
        if (video_dec_ctx->height != dst_height || video_dec_ctx->width != dst_width || video_dec_ctx->pix_fmt != AV_PIX_FMT_YUV420P) {
            sws_ctx = sws_getContext(video_dec_ctx->width, video_dec_ctx->height, video_dec_ctx->pix_fmt, dst_width, dst_height, AV_PIX_FMT_YUV420P, SWS_BILINEAR, NULL, NULL, NULL);
            if (!sws_ctx) {
                LOGD("Could not initialize the conversion context\n");
                onError();
                return -1;
            }
        }
        
        //create video stream
        AVCodec *codec = avcodec_find_encoder(AV_CODEC_ID_MPEG4);
        if (!codec) {
            LOGD("Could not find encoder for '%s'\n", avcodec_get_name(AV_CODEC_ID_MPEG4));
            onError();
            return -1;
        }
        
        video_st = avformat_new_stream(oc, codec);
        if (!video_st) {
            LOGD("Could not allocate stream\n");
            onError();
            return -1;
        }
        video_st->id = oc->nb_streams - 1;
        av_dict_copy(&video_st->metadata, src_video_stream->metadata, 0);
        c = video_st->codec;
        c->codec_id = AV_CODEC_ID_MPEG4;
        c->bit_rate = bitr;
        c->width = dst_width;
        c->height = dst_height;
        double fps = (double)src_video_stream->avg_frame_rate.num / (double)src_video_stream->avg_frame_rate.den;
        c->time_base.den = 65535;
        c->time_base.num = floor(65635 / fps);
        c->gop_size = 12;
        c->pix_fmt = AV_PIX_FMT_YUV420P;
        
        if (oc->oformat->flags & AVFMT_GLOBALHEADER) {
            c->flags |= CODEC_FLAG_GLOBAL_HEADER;
        }
        
        ret = avcodec_open2(c, codec, NULL);
        if (ret < 0) {
            LOGD("Could not open video codec: %s\n", av_err2str(ret));
            onError();
            return -1;
        }
        
        out_frame = avcodec_alloc_frame();
        if (!out_frame) {
            LOGD("Could not allocate video frame\n");
            onError();
            return -1;
        }
        
        ret = avpicture_alloc(&dst_picture, c->pix_fmt, c->width, c->height);
        if (ret < 0) {
            LOGD("Could not allocate picture: %s\n", av_err2str(ret));
            onError();
            return -1;
        }
        
        *((AVPicture *)out_frame) = dst_picture;
    }
    
    //create audio stream
    if (audio_dec_ctx && src_audio_stream) {
        AVCodec *codec = avcodec_find_encoder(AV_CODEC_ID_AAC);
        if (!codec) {
            LOGD("Could not find encoder for '%s'\n", avcodec_get_name(AV_CODEC_ID_AAC));
            onError();
            return -1;
        }
        
        audio_st = avformat_new_stream(oc, codec);
        if (!audio_st) {
            LOGD("Could not allocate stream\n");
            onError();
            return -1;
        }
        audio_st->id = oc->nb_streams - 1;
        av_dict_copy(&audio_st->metadata, src_audio_stream->metadata, 0);
        c = audio_st->codec;
        c->sample_fmt = AV_SAMPLE_FMT_FLTP;
        c->bit_rate = 40000;
        c->sample_rate = min(audio_dec_ctx->sample_rate, 44100);
        c->channels = 1;
        
        if (oc->oformat->flags & AVFMT_GLOBALHEADER) {
            c->flags |= CODEC_FLAG_GLOBAL_HEADER;
        }
        
        c = audio_st->codec;
        c->strict_std_compliance = -2;
        
        swr_ctx = swr_alloc_set_opts(NULL, AV_CH_LAYOUT_MONO, c->sample_fmt, c->sample_rate, audio_dec_ctx->channel_layout, audio_dec_ctx->sample_fmt, audio_dec_ctx->sample_rate, 0, NULL);
        if (!swr_ctx) {
            LOGD("Could not allocate resampler context\n");
            onError();
            return -1;
        }
        
        if ((ret = swr_init(swr_ctx)) < 0) {
            LOGD("Failed to initialize the resampling context\n");
            onError();
            return -1;
        }
        
        ret = avcodec_open2(c, codec, NULL);
        if (ret < 0) {
            LOGD("Could not open audio codec: %s\n", av_err2str(ret));
            onError();
            return -1;
        }
        
        av_dump_format(oc, 0, dst_filename, 1);
        
        ret = avformat_write_header(oc, NULL);
        if (ret < 0) {
            LOGD("Error occurred when opening output file: %s\n", av_err2str(ret));
            onError();
            return -1;
        }
        
        if (out_frame) {
            out_frame->pts = 0;
        }
    }
    return 0;
}

void cleanup_out() {
    if (video_st) {
        avcodec_close(video_st->codec);
        if (dst_picture.data) {
            av_free(dst_picture.data[0]);
        }
        if (out_frame) {
            av_free(out_frame);
            out_frame = NULL;
        }
        video_st = NULL;
    }
    if (audio_st) {
        avcodec_close(audio_st->codec);
        if (dst_samples_data) {
            av_free(dst_samples_data[0]);
            dst_samples_data = NULL;
        }
        audio_st = NULL;
    }
    
    if (fmt && !(fmt->flags & AVFMT_NOFILE)) {
        avio_close(oc->pb);
        fmt = NULL;
    }
    
    if (oc) {
        avformat_free_context(oc);
        oc = NULL;
    }
    if (sws_ctx) {
        sws_freeContext(sws_ctx);
        sws_ctx = NULL;
    }
    if (swr_ctx) {
        swr_free(&swr_ctx);
        swr_ctx = NULL;
    }
    current_n_out = 0;
    current_in_buff = 0;
}

int write_video_frame(AVFrame *src_frame) {
    int ret;
    
    if (sws_ctx) {
        ret = sws_scale(sws_ctx, (const uint8_t * const *)src_frame->data, src_frame->linesize, 0, src_frame->height, out_frame->data, out_frame->linesize);
        if (ret < 0) {
            LOGD("scale error: %s\n", av_err2str(ret));
            onError();
            return -1;
        }
    } else {
        for (int i = 0; i < 4; i++){
            out_frame->data[i] = src_frame->data[i];
            out_frame->linesize[i] = src_frame->linesize[i];
        }
    }
    
    AVPacket pkt = { 0 };
    int got_packet;
    av_init_packet(&pkt);
    
    ret = avcodec_encode_video2(video_st->codec, &pkt, out_frame, &got_packet);
    if (ret < 0) {
        LOGD("Error encoding video frame: %s\n", av_err2str(ret));
        onError();
        return -1;
    }
    
    if (!ret && got_packet && pkt.size) {
        pkt.stream_index = video_st->index;
        ret = av_interleaved_write_frame(oc, &pkt);
    } else {
        ret = 0;
    }
    
    if (ret != 0) {
        LOGD("Error while writing video frame: %s\n", av_err2str(ret));
        onError();
        return -1;
    }
    int64_t val = av_rescale_q(1, video_st->codec->time_base, video_st->time_base);
    out_frame->pts += val;
    return 0;
}

int check_write_packet(int flush) {
    int got_packet, ret;
    int writed = 0;
    int dst_samples_size = av_samples_get_buffer_size(NULL, audio_st->codec->channels, audio_st->codec->frame_size, audio_st->codec->sample_fmt, 1);
    while (current_n_out > audio_st->codec->frame_size || (flush && current_n_out)) {
        AVFrame *frame = avcodec_alloc_frame();
        AVPacket pkt2 = { 0 };
        av_init_packet(&pkt2);
        
        frame->nb_samples = min(audio_st->codec->frame_size, current_n_out);
        int nb_samples_size = min(dst_samples_size, current_in_buff);
        ret = avcodec_fill_audio_frame(frame, audio_st->codec->channels, audio_st->codec->sample_fmt, buff + writed, nb_samples_size, 1);
        
        if (ret < 0) {
            LOGD("Error fill frame: %s\n", av_err2str(ret));
            onError();
            return -1;
        }
        
        ret = avcodec_encode_audio2(audio_st->codec, &pkt2, frame, &got_packet);
        if (ret < 0) {
            LOGD("Error encoding audio frame: %s\n", av_err2str(ret));
            onError();
            return -1;
        }
        
        if (got_packet) {
            pkt2.stream_index = audio_st->index;
            ret = av_interleaved_write_frame(oc, &pkt2);
            if (ret != 0) {
                LOGD("Error while writing audio frame: %s\n", av_err2str(ret));
                onError();
                return -1;
            }
        }
        writed += dst_samples_size;
        current_n_out -= frame->nb_samples;
        current_in_buff -= nb_samples_size;
        avcodec_free_frame(&frame);
    }
    if (current_in_buff != 0 && writed != 0) {
        memcpy(buff, buff + writed, current_in_buff);
    }
    return 0;
}

int write_audio_frame(AVFrame *src_frame, AVCodecContext *src_codec) {
    const int n_in  = src_frame->nb_samples;
    double ratio = (double)audio_st->codec->sample_rate / src_frame->sample_rate;
    int n_out = n_in * ratio + 32;
    int64_t delay = swr_get_delay(swr_ctx, audio_st->codec->sample_rate);
    if (delay > 0) {
        n_out += delay;
    }
    
    if (!dst_samples_data) {
        int ret = av_samples_alloc_array_and_samples(&dst_samples_data, NULL, audio_st->codec->channels, n_out, audio_st->codec->sample_fmt, 0);
        if (ret < 0) {
            LOGD("Could not allocate destination samples\n");
            onError();
            return -1;
        }
    }
    
    n_out = swr_convert(swr_ctx, dst_samples_data, n_out, (const uint8_t **)src_frame->extended_data, src_frame->nb_samples);
    if (n_out <= 0) {
        LOGD("Error while converting\n");
        onError();
        return -1;
    }
    int total_size = av_samples_get_buffer_size(NULL, audio_st->codec->channels, n_out, audio_st->codec->sample_fmt, 1);
    memcpy(buff + current_in_buff, dst_samples_data[0], total_size);
    current_n_out += n_out;
    current_in_buff += total_size;
    return check_write_packet(0);
}

void post_video_conversion() {
    check_write_packet(1);
    av_write_trailer(oc);
}
