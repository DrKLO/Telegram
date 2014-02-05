#ifndef video_h
#define video_h

#include <libavformat/avformat.h>

int prepare_for_video_conversion(const char *dst_filename, AVCodecContext *video_dec_ctx, AVCodecContext *audio_dec_ctx, AVFormatContext *fmt_ctx, AVStream *src_video_stream, AVStream *src_audio_stream, int bitr);
int write_video_frame(AVFrame *src_frame);
int write_audio_frame(AVFrame *src_frame, AVCodecContext *src_codec);
void post_video_conversion();
void cleanup_out();
void onError();
void onProgress();
void onDone();

void convertFile(const char *src_filename, const char *dst_filename, int bitr);

#endif
