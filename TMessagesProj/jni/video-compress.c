#include <libavformat/avformat.h>
#include <libavcodec/avcodec.h>
#include <libswscale/swscale.h>
#include "c_utils.h"
#include <jni.h>
#include <malloc.h>

typedef struct StreamContext {
    AVCodecContext *dec_ctx;
    AVCodecContext *enc_ctx;
    int64_t pts_start_from;
    int streamIndex;
} StreamContext;

typedef struct VideoConfig {
    int height;
    int width;
    int bitrate;
    int frame_rate;
    float start_time;
    float end_time;
};

typedef struct Context {
    struct StreamContext audio_stream_context;
    struct VideoConfig videoConfig;

    AVFormatContext *ifmt_ctx;
    AVFormatContext *ofmt_ctx;
    AVFrame *video_frame;
    AVCodecContext *video_enc_ctx;

    int videoStreamIndex;
    int audioStreamIndex;
    double_t pts_scale;
};

int open_input_file(const char *filename, struct Context *context) {
    int ret;
    unsigned int i;

    context->ifmt_ctx = NULL;
    if ((ret = avformat_open_input(&context->ifmt_ctx, filename, NULL, NULL)) < 0) {
        LOGE("Cannot open input file\n");
        return ret;
    }

    if ((ret = avformat_find_stream_info(context->ifmt_ctx, NULL)) < 0) {
        LOGE("Cannot find stream information\n");
        return ret;
    }


    for (i = 0; i < context->ifmt_ctx->nb_streams; i++) {
        AVStream *stream = context->ifmt_ctx->streams[i];
        if (stream->codecpar->codec_type == AVMEDIA_TYPE_AUDIO) {
            context->audioStreamIndex = i;
            context->audio_stream_context.streamIndex = context->audioStreamIndex;
            if (stream->codecpar->codec_id != AV_CODEC_ID_AAC) {
                AVCodec *dec = avcodec_find_decoder(stream->codecpar->codec_id);
                AVCodecContext *codec_ctx;
                if (!dec) {
                    LOGE("Failed to find decoder for stream #%u\n", i);
                    return AVERROR_DECODER_NOT_FOUND;
                }
                codec_ctx = avcodec_alloc_context3(dec);
                if (!codec_ctx) {
                    LOGE("Failed to allocate the decoder context for stream #%u\n", i);
                    return AVERROR(ENOMEM);
                }
                ret = avcodec_parameters_to_context(codec_ctx, stream->codecpar);
                if (ret < 0) {
                    LOGE("Failed to copy decoder parameters to input decoder context "
                         "for stream #%u\n", i);
                    return ret;
                }

                AVDictionary *opt = NULL;
                if (codec_ctx->codec_id == AV_CODEC_ID_H264) {
                    av_dict_set(&opt, "preset", "superfast", 0);
                }
                ret = avcodec_open2(codec_ctx, dec, &opt);
                av_dict_free(&opt);
                if (ret < 0) {
                    LOGE("Failed to open decoder for stream #%u\n", i);
                    return ret;
                }

                context->audio_stream_context.dec_ctx = codec_ctx;
            } else {
                context->audio_stream_context.dec_ctx = NULL;
                context->audio_stream_context.enc_ctx = NULL;
            }
        }

        if (stream->codecpar->codec_type == AVMEDIA_TYPE_VIDEO) {
            context->videoStreamIndex = i;
        }
    }
    return 0;
}

int open_output_file(const char *filename, struct Context *context) {
    AVStream *in_stream;
    AVCodecContext *dec_ctx, *enc_ctx;
    AVCodec *encoder;
    int ret;
    unsigned int i;

    context->ofmt_ctx = NULL;
    avformat_alloc_output_context2(&context->ofmt_ctx, NULL, NULL, filename);


    for (i = 0; i < context->ifmt_ctx->nb_streams; i++) {
        in_stream = context->ifmt_ctx->streams[i];
        if (context->audioStreamIndex == i) {
            AVStream *out_stream = avformat_new_stream(context->ofmt_ctx, NULL);
            if (context->audio_stream_context.dec_ctx == NULL) {
                ret = avcodec_parameters_copy(out_stream->codecpar, in_stream->codecpar);
                if (ret < 0) {
                    av_log(NULL, AV_LOG_ERROR, "Copying parameters for stream #%u failed\n", i);
                    return ret;
                }
                out_stream->time_base = in_stream->time_base;
            } else {
                encoder = avcodec_find_encoder(AV_CODEC_ID_AAC);
                if (!encoder) {
                    LOGE("Necessary encoder not found aac\n");
                    return AVERROR_INVALIDDATA;
                }
                enc_ctx = avcodec_alloc_context3(encoder);
                if (!enc_ctx) {
                    LOGE("Failed to allocate the encoder context\n");
                    return AVERROR(ENOMEM);
                }

                enc_ctx->sample_rate = dec_ctx->sample_rate;
                enc_ctx->channel_layout = dec_ctx->channel_layout;
                enc_ctx->channels = av_get_channel_layout_nb_channels(enc_ctx->channel_layout);
                enc_ctx->sample_fmt = encoder->sample_fmts[0];
                enc_ctx->time_base = (AVRational) {1, enc_ctx->sample_rate};
                enc_ctx->flags |= AV_CODEC_FLAG_GLOBAL_HEADER;

                ret = avcodec_open2(enc_ctx, encoder, NULL);

                if (ret < 0) {
                    LOGE("Cannot open audio encoder %s\n", av_err2str(ret));
                    return ret;
                }
                if (ret < 0) {
                    LOGE("Failed to copy encoder parameters to output stream #%u\n", i);
                    return ret;
                }

                context->audio_stream_context.enc_ctx = enc_ctx;
            }
        }

        if (context->videoStreamIndex == i) {
            encoder = avcodec_find_encoder(AV_CODEC_ID_H264);
            if (!encoder) {
                LOGE("Necessary encoder not found %d\n");
                return AVERROR_INVALIDDATA;
            }
            context->video_enc_ctx = avcodec_alloc_context3(encoder);

            context->video_enc_ctx->height = context->videoConfig.height;
            context->video_enc_ctx->width = context->videoConfig.width;
            context->video_enc_ctx->pix_fmt = AV_PIX_FMT_YUV420P;

            context->pts_scale =
                    context->videoConfig.frame_rate /
                    av_q2d(context->ifmt_ctx->streams[i]->avg_frame_rate);
            context->video_enc_ctx->time_base = (AVRational) {
                    1,
                    context->videoConfig.frame_rate
            };
            context->video_enc_ctx->bit_rate = context->videoConfig.bitrate;

            context->video_enc_ctx->flags |= AV_CODEC_FLAG_GLOBAL_HEADER;
            context->video_frame = av_frame_alloc();
            context->video_frame->format = AV_PIX_FMT_YUV420P;
            context->video_frame->width = context->video_enc_ctx->width;
            context->video_frame->height = context->video_enc_ctx->height;
            av_frame_get_buffer(context->video_frame, 1);

            AVDictionary *opt = NULL;
            av_dict_set(&opt, "preset", "superfast", 0);
            avcodec_open2(context->video_enc_ctx, encoder, &opt);
            av_dict_free(&opt);


            AVStream *out_stream = avformat_new_stream(context->ofmt_ctx, NULL);
            ret = avcodec_parameters_from_context(out_stream->codecpar, context->video_enc_ctx);
            if (ret < 0) {
                av_log(NULL, AV_LOG_ERROR,
                       "Failed to copy encoder parameters to output stream #%u\n", i);
                return ret;
            }

            out_stream->time_base = context->video_enc_ctx->time_base;
        }
    }

    if (!(context->ofmt_ctx->oformat->flags & AVFMT_NOFILE)) {
        ret = avio_open(&context->ofmt_ctx->pb, filename, AVIO_FLAG_WRITE);
        if (ret < 0) {
            av_log(NULL, AV_LOG_ERROR, "Could not open output file '%s'", filename);
            return ret;
        }
    }

    avformat_write_header(context->ofmt_ctx, NULL);

    return 0;
}

int encode_write_frame(AVFrame *frame, struct Context *context, StreamContext *streamContext,
                       int *got_frame) {
    int ret;
    int got_frame_local;
    AVPacket enc_pkt;
    int (*enc_func)(AVCodecContext *, AVPacket *, const AVFrame *, int *) =
    (streamContext->enc_ctx->codec_type == AVMEDIA_TYPE_VIDEO) ? avcodec_encode_video2
                                                               : avcodec_encode_audio2;

    if (!got_frame) got_frame = &got_frame_local;

    enc_pkt.data = NULL;
    enc_pkt.size = 0;
    av_init_packet(&enc_pkt);
    ret = enc_func(streamContext->enc_ctx, &enc_pkt,
                   frame, got_frame);
    av_frame_free(&frame);
    if (ret < 0)
        return ret;
    if (!(*got_frame))
        return 0;


    enc_pkt.stream_index = streamContext->streamIndex;

    av_packet_rescale_ts(&enc_pkt,
                         streamContext->enc_ctx->time_base,
                         context->ifmt_ctx->streams[context->audioStreamIndex]->time_base);

    if (context->videoConfig.start_time > 0) {
        enc_pkt.pts -= streamContext->pts_start_from;
        if (enc_pkt.pts < 0) {
            streamContext->pts_start_from += enc_pkt.pts;
            enc_pkt.pts = 0;
        }
        enc_pkt.dts -= streamContext->pts_start_from;
    }

    ret = av_interleaved_write_frame(context->ofmt_ctx, &enc_pkt);
    av_packet_unref(&enc_pkt);
    return ret;
}

int
process_encode_write_frame(AVFrame *frame, struct Context *context, StreamContext *streamContext) {
    int ret;

    if (ret < 0) {
        if (ret == AVERROR(EAGAIN) || ret == AVERROR_EOF)
            av_frame_free(&frame);
    }

    ret = encode_write_frame(frame, context, streamContext, NULL);
    av_frame_free(&frame);
    return ret;
}

int flush_encoder(struct Context *context, StreamContext *streamContext) {
    int ret;
    int got_frame;
    if (!(streamContext->enc_ctx->codec->capabilities & AV_CODEC_CAP_DELAY))
        return 0;

    while (1) {
        ret = encode_write_frame(NULL, context, streamContext, &got_frame);
        if (ret < 0)
            break;
        if (!got_frame)
            return 0;
    }
    return ret;
}


int compress_video(const char *src_in, const char *src_out, struct Context *context, JNIEnv *env,
                   jobject convertorObject) {

    jclass jclass_FfmpegVideoConvertor = (*env)->GetObjectClass(env, convertorObject);
    jmethodID checkConversionCanceled = (*env)->GetMethodID(env, jclass_FfmpegVideoConvertor,
                                                            "checkConversionCanceled", "()Z");

    jmethodID updateProgress = (*env)->GetMethodID(env, jclass_FfmpegVideoConvertor,
                                                   "updateProgress", "(F)V");


    jmethodID pullDecoder = (*env)->GetMethodID(env, jclass_FfmpegVideoConvertor,
                                                "pullDecoder", "()Ljava/nio/ByteBuffer;");


    int ret;
    AVPacket packet = {.data = NULL, .size = 0};
    AVFrame *frame = NULL;

    int stream_index;
    int got_frame;

    context->audioStreamIndex = -1;
    context->videoStreamIndex = -1;

    if ((ret = open_input_file(src_in, context) < 0))
        goto end;

    if ((ret = open_output_file(src_out, context)) < 0)
        goto end;

    if (context->videoConfig.start_time > 0) {
        context->audio_stream_context.pts_start_from = -1;
        av_seek_frame(context->ifmt_ctx, -1,
                      (int64_t) (context->videoConfig.start_time * AV_TIME_BASE),
                      AVSEEK_FLAG_BACKWARD);
    }


//    float duration_sec;
//    float pts2sec = (float) av_q2d(context->video_enc_ctx->time_base);
//
//    if (context->videoConfig.end_time > 0) {
//        duration_sec = context->videoConfig.end_time - context->videoConfig.start_time;
//    } else {
//        duration_sec = (float) (av_q2d(context->ifmt_ctx->streams[0]->time_base) *
//                                context->ifmt_ctx->streams[0]->duration) - context->videoConfig.start_time;
//    }


    /* read audio stream packets */
    int video_stream_end = 0;
    int audio_stream_end = 0;
    int decoded_frame_count = 0;
    int encoded_frame_count = 0;

    int64_t last_pts = -1;

    int cycle_count = 0;
    while (1) {
        if (cycle_count % 20 == 0) {
            if ((*env)->CallBooleanMethod(env, convertorObject, checkConversionCanceled)) {
                ret = -1;
                break;
            }
        }
        cycle_count++;
        if ((context->videoStreamIndex < 0 || video_stream_end) &&
            (context->audioStreamIndex < 0 || audio_stream_end))
            break;

        while (context->videoStreamIndex >= 0 && !video_stream_end) {
            jobject decoded_data = (*env)->CallObjectMethod(env, convertorObject, pullDecoder);
            if (decoded_data != NULL) {
                uint8_t *dataBuf = (uint8_t *) (*env)->GetDirectBufferAddress(env, decoded_data);
                jlong dataBufSize = (*env)->GetDirectBufferCapacity(env, decoded_data);

                if (dataBufSize == 3 && dataBuf[0] == 0x00 && dataBuf[1] == 0x00 &&
                    dataBuf[2] == 0x08) {
                    video_stream_end = 1;
                    break;
                }

                AVPacket enc_pkt;
                av_init_packet(&enc_pkt);
                enc_pkt.data = NULL;
                enc_pkt.size = 0;
                enc_pkt.stream_index = context->videoStreamIndex;
                context->video_frame->pts = (int64_t) (decoded_frame_count * context->pts_scale);
                decoded_frame_count++;
                if (context->video_frame->pts <= last_pts) {
                    break;
                }

                last_pts = context->video_frame->pts;


                int y_count = 0;
                int c_count = 0;

                for (int y = 0; y < context->video_frame->height; y++) {
                    int k = y * context->video_frame->width;
                    for (int x = 0; x < context->video_frame->width; x++) {

                        context->video_frame->data[0][y_count] = dataBuf[(k + x) * 4];
                        y_count++;


                        if (y % 2 == 0 && x % 2 == 0) {
                            context->video_frame->data[1][c_count] = dataBuf[(k + x) * 4 + 1];
                            context->video_frame->data[2][c_count] = dataBuf[(k + x) * 4 + 2];
                            c_count++;
                        }
                    }
                }


                avcodec_encode_video2(context->video_enc_ctx, &enc_pkt, context->video_frame,
                                      &got_frame);

                if (got_frame) {
                    encoded_frame_count++;
                    if (encoded_frame_count % 20 == 0) {
                        // float progress =(enc_pkt.pts * pts2sec - context->videoConfig.start_time) /duration_sec;
                        (*env)->CallVoidMethod(env, convertorObject, updateProgress, 0.0);
                    }

                    av_packet_rescale_ts(&enc_pkt,
                                         context->video_enc_ctx->time_base,
                                         context->ofmt_ctx->streams[context->videoStreamIndex]->time_base);
                    enc_pkt.stream_index = context->videoStreamIndex;

                    av_interleaved_write_frame(context->ofmt_ctx, &enc_pkt);
                }

                av_packet_unref(&enc_pkt);
            }
            break;
        }

        if (context->audioStreamIndex < 0 || audio_stream_end) continue;
        if ((ret = av_read_frame(context->ifmt_ctx, &packet)) < 0) {
            if (ret == AVERROR_EOF) ret = 0;
            audio_stream_end = 1;
            continue;
        }

        stream_index = packet.stream_index;

        if (stream_index == context->audioStreamIndex) {
            if (context->audio_stream_context.enc_ctx == NULL) {
                double if_time_s = av_q2d(context->ifmt_ctx->streams[stream_index]->time_base);
                if (context->videoConfig.start_time > 0 &&
                    if_time_s * packet.pts <
                    context->videoConfig.start_time) {
                    continue;
                }

                if (context->videoConfig.end_time > 0 &&
                    if_time_s * packet.pts >
                    context->videoConfig.end_time) {
                    audio_stream_end = 1;
                    continue;
                }


                if (context->videoConfig.start_time > 0) {
                    if (context->audio_stream_context.pts_start_from == -1) {
                        context->audio_stream_context.pts_start_from = av_rescale_q_rnd(
                                packet.pts,
                                context->ifmt_ctx->streams[stream_index]->time_base,
                                context->ifmt_ctx->streams[stream_index]->time_base,
                                AV_ROUND_NEAR_INF |
                                AV_ROUND_PASS_MINMAX);
                    }
                }

                av_packet_rescale_ts(&packet,
                                     context->ifmt_ctx->streams[stream_index]->time_base,
                                     context->ifmt_ctx->streams[stream_index]->time_base);

                if (context->videoConfig.start_time > 0) {
                    packet.pts -= context->audio_stream_context.pts_start_from;
                    if (packet.pts < 0) {
                        context->audio_stream_context.pts_start_from += packet.pts;
                        packet.pts = 0;
                    }
                    packet.dts -= context->audio_stream_context.pts_start_from;
                }
                av_interleaved_write_frame(context->ofmt_ctx, &packet);
                continue;
            } else {
                frame = av_frame_alloc();
                if (!frame) {
                    ret = AVERROR(ENOMEM);
                    break;
                }
                av_packet_rescale_ts(&packet,
                                     context->ifmt_ctx->streams[stream_index]->time_base,
                                     context->audio_stream_context.dec_ctx->time_base);


                ret = avcodec_decode_audio4(context->audio_stream_context.dec_ctx, frame,
                                            &got_frame, &packet);
                if (ret < 0) {
                    av_frame_free(&frame);
                    LOGE("Decoding failed\n");
                    break;
                }

                if (got_frame) {
                    double decoder_time_s = av_q2d(
                            context->audio_stream_context.dec_ctx->time_base);

                    if (context->videoConfig.start_time > 0 &&
                        decoder_time_s * packet.pts <
                        context->videoConfig.start_time) {
                        continue;
                    }
                    if (context->videoConfig.end_time > 0 &&
                        decoder_time_s * packet.pts >
                        context->videoConfig.end_time) {
                        audio_stream_end = 1;
                        continue;
                    }

                    if (context->videoConfig.start_time > 0) {
                        if (context->audio_stream_context.pts_start_from == -1) {
                            context->audio_stream_context.pts_start_from = av_rescale_q_rnd(
                                    packet.pts,
                                    context->audio_stream_context.dec_ctx->time_base,
                                    context->ifmt_ctx->streams[stream_index]->time_base,
                                    AV_ROUND_NEAR_INF |
                                    AV_ROUND_PASS_MINMAX);
                        }
                    }

                    ret = process_encode_write_frame(frame, context,
                                                     &context->audio_stream_context);
                    av_frame_free(&frame);
                    if (ret < 0)
                        goto end;
                }
            }
        }

        av_packet_unref(&packet);
    }

    if (context->audio_stream_context.enc_ctx != NULL) {
        ret = flush_encoder(context, &context->audio_stream_context);
        if (ret < 0) {
            LOGE("Flushing encoder failed\n");
            goto end;
        }
    }

    got_frame = 1;
    while (got_frame) {
        AVPacket enc_pkt;
        av_init_packet(&enc_pkt);
        enc_pkt.data = NULL;
        enc_pkt.size = 0;
        enc_pkt.stream_index = context->videoStreamIndex;
        avcodec_encode_video2(context->video_enc_ctx, &enc_pkt, NULL, &got_frame);

        if (got_frame) {
            encoded_frame_count++;
            if (encoded_frame_count % 20 == 0) {
                //     float progress =(enc_pkt.pts * pts2sec - context->videoConfig.start_time) / duration_sec;
                (*env)->CallVoidMethod(env, convertorObject, updateProgress, 0.0);
            }

            av_packet_rescale_ts(&enc_pkt,
                                 context->video_enc_ctx->time_base,
                                 context->ofmt_ctx->streams[context->videoStreamIndex]->time_base);

            av_interleaved_write_frame(context->ofmt_ctx, &enc_pkt);
        }

        av_packet_unref(&enc_pkt);
    }


    av_write_trailer(context->ofmt_ctx);
    end:

    av_frame_free(&frame);
    av_frame_free(&context->video_frame);
    av_packet_unref(&packet);

    if (context->audioStreamIndex >= 0 && context->audio_stream_context.dec_ctx != NULL) {
        avcodec_free_context(&context->audio_stream_context.enc_ctx);
        avcodec_free_context(&context->audio_stream_context.dec_ctx);
    }

    if (context->videoStreamIndex >= 0) {
        avcodec_free_context(&context->video_enc_ctx);
    }

    avformat_free_context(context->ofmt_ctx);
    avformat_close_input(&context->ifmt_ctx);


    if (ret < 0) LOGE("Error occurred: %s\n", av_err2str(ret));
    return ret;
}

JNIEXPORT jint
Java_org_telegram_messenger_Utilities_compressVideo(JNIEnv *env, jclass class,
                                                    jobject convertor,
                                                    jstring src_path, jstring out_path,
                                                    jint width, jint height,
                                                    jint frame_rate, jint bitrate,
                                                    jfloat startTime, jfloat endTime) {

    struct Context *context = malloc(sizeof(struct Context));

    context->ifmt_ctx = NULL;
    context->video_frame = NULL;
    context->video_enc_ctx = NULL;
    context->audio_stream_context.dec_ctx = NULL;
    context->audio_stream_context.enc_ctx = NULL;

    context->videoConfig.bitrate = bitrate;
    context->videoConfig.frame_rate = frame_rate;
    context->videoConfig.width = width;
    context->videoConfig.height = height;
    context->videoConfig.start_time = (float) startTime;
    context->videoConfig.end_time = (float) endTime;

    if (context->videoConfig.start_time < 0) context->videoConfig.start_time = 0;

    int r = compress_video(
            (*env)->GetStringUTFChars(env, src_path, 0),
            (*env)->GetStringUTFChars(env, out_path, 0),
            context,
            env, convertor);

    realloc(context, sizeof(struct Context));
    return r;
}

