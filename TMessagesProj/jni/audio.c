#include <jni.h>
#include <ogg/ogg.h>
#include <stdio.h>
#include <opus.h>
#include <stdlib.h>
#include <time.h>
#include <opusfile.h>
#include <math.h>
#include "c_utils.h"
#include "libavformat/avformat.h"

typedef struct {
    int version;
    int channels; /* Number of channels: 1..255 */
    int preskip;
    ogg_uint32_t input_sample_rate;
    int gain; /* in dB S7.8 should be zero whenever possible */
    int channel_mapping;
    /* The rest is only used if channel_mapping != 0 */
    int nb_streams;
    int nb_coupled;
    unsigned char stream_map[255];
} OpusHeader;

typedef struct {
    unsigned char *data;
    int maxlen;
    int pos;
} Packet;

typedef struct {
    const unsigned char *data;
    int maxlen;
    int pos;
} ROPacket;

typedef struct {
    void *readdata;
    opus_int64 total_samples_per_channel;
    int rawmode;
    int channels;
    long rate;
    int gain;
    int samplesize;
    int endianness;
    char *infilename;
    int ignorelength;
    int skip;
    int extraout;
    char *comments;
    int comments_length;
    int copy_comments;
} oe_enc_opt;

static int write_uint32(Packet *p, ogg_uint32_t val) {
    if (p->pos > p->maxlen - 4) {
        return 0;
    }
    p->data[p->pos  ] = (val    ) & 0xFF;
    p->data[p->pos+1] = (val>> 8) & 0xFF;
    p->data[p->pos+2] = (val>>16) & 0xFF;
    p->data[p->pos+3] = (val>>24) & 0xFF;
    p->pos += 4;
    return 1;
}

static int write_uint16(Packet *p, ogg_uint16_t val) {
    if (p->pos > p->maxlen-2) {
        return 0;
    }
    p->data[p->pos  ] = (val    ) & 0xFF;
    p->data[p->pos+1] = (val>> 8) & 0xFF;
    p->pos += 2;
    return 1;
}

static int write_chars(Packet *p, const unsigned char *str, int nb_chars)
{
    int i;
    if (p->pos>p->maxlen-nb_chars)
        return 0;
    for (i=0;i<nb_chars;i++)
        p->data[p->pos++] = str[i];
    return 1;
}

static int read_uint32(ROPacket *p, ogg_uint32_t *val)
{
    if (p->pos>p->maxlen-4)
        return 0;
    *val =  (ogg_uint32_t)p->data[p->pos  ];
    *val |= (ogg_uint32_t)p->data[p->pos+1]<< 8;
    *val |= (ogg_uint32_t)p->data[p->pos+2]<<16;
    *val |= (ogg_uint32_t)p->data[p->pos+3]<<24;
    p->pos += 4;
    return 1;
}

static int read_uint16(ROPacket *p, ogg_uint16_t *val)
{
    if (p->pos>p->maxlen-2)
        return 0;
    *val =  (ogg_uint16_t)p->data[p->pos  ];
    *val |= (ogg_uint16_t)p->data[p->pos+1]<<8;
    p->pos += 2;
    return 1;
}

static int read_chars(ROPacket *p, unsigned char *str, int nb_chars)
{
    int i;
    if (p->pos>p->maxlen-nb_chars)
        return 0;
    for (i=0;i<nb_chars;i++)
        str[i] = p->data[p->pos++];
    return 1;
}

int opus_header_to_packet(const OpusHeader *h, unsigned char *packet, int len) {
    int i;
    Packet p;
    unsigned char ch;
    
    p.data = packet;
    p.maxlen = len;
    p.pos = 0;
    if (len < 19) {
        return 0;
    }
    if (!write_chars(&p, (const unsigned char *)"OpusHead", 8)) {
        return 0;
    }

    ch = 1;
    if (!write_chars(&p, &ch, 1)) {
        return 0;
    }
    
    ch = h->channels;
    if (!write_chars(&p, &ch, 1)) {
        return 0;
    }
    
    if (!write_uint16(&p, h->preskip)) {
        return 0;
    }
    
    if (!write_uint32(&p, h->input_sample_rate)) {
        return 0;
    }
    
    if (!write_uint16(&p, h->gain)) {
        return 0;
    }
    
    ch = h->channel_mapping;
    if (!write_chars(&p, &ch, 1)) {
        return 0;
    }
    
    if (h->channel_mapping != 0) {
        ch = h->nb_streams;
        if (!write_chars(&p, &ch, 1)) {
            return 0;
        }
        
        ch = h->nb_coupled;
        if (!write_chars(&p, &ch, 1)) {
            return 0;
        }
        
        /* Multi-stream support */
        for (i = 0; i < h->channels; i++) {
            if (!write_chars(&p, &h->stream_map[i], 1)) {
                return 0;
            }
        }
    }
    
    return p.pos;
}

#define writeint(buf, base, val) do { buf[base + 3] = ((val) >> 24) & 0xff; \
buf[base + 2]=((val) >> 16) & 0xff; \
buf[base + 1]=((val) >> 8) & 0xff; \
buf[base] = (val) & 0xff; \
} while(0)

static void comment_init(char **comments, int *length, const char *vendor_string) {
    // The 'vendor' field should be the actual encoding library used
    size_t vendor_length = strlen(vendor_string);
    int user_comment_list_length = 0;
    size_t len = 8 + 4 + vendor_length + 4;
    char *p = (char *)malloc(len);
    memcpy(p, "OpusTags", 8);
    writeint(p, 8, vendor_length);
    memcpy(p + 12, vendor_string, vendor_length);
    writeint(p, 12 + vendor_length, user_comment_list_length);
    *length = len;
    *comments = p;
}

static void comment_pad(char **comments, int* length, size_t amount) {
    if (amount > 0) {
        char *p = *comments;
        // Make sure there is at least amount worth of padding free, and round up to the maximum that fits in the current ogg segments
        size_t newlen = (*length + amount + 255) / 255 * 255 - 1;
        p = realloc(p, newlen);
        for (int32_t i = *length; i < newlen; i++) {
            p[i] = 0;
        }
        *comments = p;
        *length = newlen;
    }
}

static int writeOggPage(ogg_page *page, FILE *os) {
    int written = fwrite(page->header, sizeof(unsigned char), (size_t) page->header_len, os);
    written += fwrite(page->body, sizeof(unsigned char), (size_t) page->body_len, os);
    return written;
}

const opus_int32 bitrate = OPUS_BITRATE_MAX;
const opus_int32 frame_size = 960;
const int with_cvbr = 1;
const int max_ogg_delay = 0;
const int comment_padding = 512;

opus_int32 rate = 48000;
opus_int32 coding_rate = 48000;

ogg_int32_t _packetId;
OpusEncoder *_encoder = 0;
uint8_t *_packet = 0;
ogg_stream_state os;
const char *_filePath;
FILE *_fileOs = 0;
oe_enc_opt inopt;
OpusHeader header;
opus_int32 min_bytes;
int max_frame_bytes;
ogg_packet op;
ogg_page og;
opus_int64 bytes_written;
opus_int64 pages_out;
opus_int64 total_samples;
ogg_int64_t enc_granulepos;
ogg_int64_t last_granulepos;
int size_segments;
int last_segments;
int serialno;

void cleanupRecorder() {

    if (_fileOs) {
        while (ogg_stream_flush(&os, &og)) {
            writeOggPage(&og, _fileOs);
        }
    } else {
        ogg_stream_flush(&os, &og);
    }
    
    if (_encoder) {
        opus_encoder_destroy(_encoder);
        _encoder = 0;
    }
    
    ogg_stream_clear(&os);
    
    if (_packet) {
        free(_packet);
        _packet = 0;
    }
    
    if (_fileOs) {
        fclose(_fileOs);
        _fileOs = 0;
    }
    
    _packetId = -1;
    bytes_written = 0;
    pages_out = 0;
    total_samples = 0;
    enc_granulepos = 0;
    size_segments = 0;
    last_segments = 0;
    last_granulepos = 0;
    if (_filePath) {
        free(_filePath);
        _filePath = 0;
    }
    memset(&os, 0, sizeof(ogg_stream_state));
    memset(&inopt, 0, sizeof(oe_enc_opt));
    memset(&header, 0, sizeof(OpusHeader));
    memset(&op, 0, sizeof(ogg_packet));
    memset(&og, 0, sizeof(ogg_page));
}

int initRecorder(const char *path, opus_int32 sampleRate) {
    cleanupRecorder();

    coding_rate = sampleRate;
    rate = sampleRate;

    if (!path) {
        LOGE("path is null");
        return 0;
    }

    int length = strlen(path);
    _filePath = (char*) malloc(length + 1);
    strcpy(_filePath, path);

    _fileOs = fopen(path, "w");
    if (!_fileOs) {
        LOGE("error cannot open file: %s", path);
        return 0;
    }
    
    inopt.rate = rate;
    inopt.gain = 0;
    inopt.endianness = 0;
    inopt.copy_comments = 0;
    inopt.rawmode = 0;
    inopt.ignorelength = 0;
    inopt.samplesize = 16;
    inopt.channels = 1;
    inopt.skip = 0;
    
    comment_init(&inopt.comments, &inopt.comments_length, opus_get_version_string());
    
    if (rate != coding_rate) {
        LOGE("Invalid rate");
        return 0;
    }
    
    header.channels = 1;
    header.channel_mapping = 0;
    header.input_sample_rate = rate;
    header.gain = inopt.gain;
    header.nb_streams = 1;
    
    int result = OPUS_OK;
    _encoder = opus_encoder_create(coding_rate, 1, OPUS_APPLICATION_VOIP, &result);
    if (result != OPUS_OK) {
        LOGE("Error cannot create encoder: %s", opus_strerror(result));
        return 0;
    }
    
    min_bytes = max_frame_bytes = (1275 * 3 + 7) * header.nb_streams;
    _packet = malloc(max_frame_bytes);
    
    result = opus_encoder_ctl(_encoder, OPUS_SET_BITRATE(bitrate));
    //result = opus_encoder_ctl(_encoder, OPUS_SET_COMPLEXITY(10));
    if (result != OPUS_OK) {
        LOGE("Error OPUS_SET_BITRATE returned: %s", opus_strerror(result));
        return 0;
    }
    
#ifdef OPUS_SET_LSB_DEPTH
    result = opus_encoder_ctl(_encoder, OPUS_SET_LSB_DEPTH(MAX(8, MIN(24, inopt.samplesize))));
    if (result != OPUS_OK) {
        LOGE("Warning OPUS_SET_LSB_DEPTH returned: %s", opus_strerror(result));
    }
#endif
    
    opus_int32 lookahead;
    result = opus_encoder_ctl(_encoder, OPUS_GET_LOOKAHEAD(&lookahead));
    if (result != OPUS_OK) {
        LOGE("Error OPUS_GET_LOOKAHEAD returned: %s", opus_strerror(result));
        return 0;
    }
    
    inopt.skip += lookahead;
    header.preskip = (int)(inopt.skip * (48000.0 / coding_rate));
    inopt.extraout = (int)(header.preskip * (rate / 48000.0));
    
    if (ogg_stream_init(&os, serialno = rand()) == -1) {
        LOGE("Error: stream init failed");
        return 0;
    }
    
    unsigned char header_data[100];
    int packet_size = opus_header_to_packet(&header, header_data, 100);
    op.packet = header_data;
    op.bytes = packet_size;
    op.b_o_s = 1;
    op.e_o_s = 0;
    op.granulepos = 0;
    op.packetno = 0;
    ogg_stream_packetin(&os, &op);
    
    while ((result = ogg_stream_flush(&os, &og))) {
        if (!result) {
            break;
        }
        
        int pageBytesWritten = writeOggPage(&og, _fileOs);
        if (pageBytesWritten != og.header_len + og.body_len) {
            LOGE("Error: failed writing header to output stream");
            return 0;
        }
        bytes_written += pageBytesWritten;
        pages_out++;
    }
    
    comment_pad(&inopt.comments, &inopt.comments_length, comment_padding);
    op.packet = (unsigned char *)inopt.comments;
    op.bytes = inopt.comments_length;
    op.b_o_s = 0;
    op.e_o_s = 0;
    op.granulepos = 0;
    op.packetno = 1;
    ogg_stream_packetin(&os, &op);
    
    while ((result = ogg_stream_flush(&os, &og))) {
        if (result == 0) {
            break;
        }
        
        int writtenPageBytes = writeOggPage(&og, _fileOs);
        if (writtenPageBytes != og.header_len + og.body_len) {
            LOGE("Error: failed writing header to output stream");
            return 0;
        }
        
        bytes_written += writtenPageBytes;
        pages_out++;
    }
    
    free(inopt.comments);
    
    return 1;
}

int writeFrame(uint8_t *framePcmBytes, uint32_t frameByteCount, int end) {
    size_t cur_frame_size = frame_size;
    _packetId++;
    
    opus_int32 nb_samples = frameByteCount / 2;
    total_samples += nb_samples;
    op.e_o_s = end;
    
    int nbBytes = 0;
    
    if (nb_samples != 0) {
        uint8_t *paddedFrameBytes = framePcmBytes;
        int freePaddedFrameBytes = 0;

        if (nb_samples < cur_frame_size) {
            paddedFrameBytes = malloc(cur_frame_size * 2);
            freePaddedFrameBytes = 1;
            memcpy(paddedFrameBytes, framePcmBytes, frameByteCount);
            memset(paddedFrameBytes + nb_samples * 2, 0, cur_frame_size * 2 - nb_samples * 2);
        }
        
        nbBytes = opus_encode(_encoder, (opus_int16 *)paddedFrameBytes, cur_frame_size, _packet, max_frame_bytes / 10);
        if (freePaddedFrameBytes) {
            free(paddedFrameBytes);
        }
        
        if (nbBytes < 0) {
            LOGE("Encoding failed: %s. Aborting.", opus_strerror(nbBytes));
            return 0;
        }
        
        enc_granulepos += cur_frame_size * 48000 / coding_rate;
        size_segments = (nbBytes + 255) / 255;
        min_bytes = MIN(nbBytes, min_bytes);
    }
    
    while ((((size_segments <= 255) && (last_segments + size_segments > 255)) || (enc_granulepos - last_granulepos > max_ogg_delay)) && ogg_stream_flush_fill(&os, &og, 255 * 255)) {
        if (ogg_page_packets(&og) != 0) {
            last_granulepos = ogg_page_granulepos(&og);
        }
        
        last_segments -= og.header[26];
        int writtenPageBytes = writeOggPage(&og, _fileOs);
        if (writtenPageBytes != og.header_len + og.body_len) {
            LOGE("Error: failed writing data to output stream");
            return 0;
        }
        bytes_written += writtenPageBytes;
        pages_out++;
    }
    
    op.packet = _packet;
    op.bytes = nbBytes;
    op.b_o_s = 0;
    op.granulepos = enc_granulepos;
    if (op.e_o_s) {
        op.granulepos = ((total_samples * 48000 + rate - 1) / rate) + header.preskip;
    }
    op.packetno = 2 + _packetId;
    ogg_stream_packetin(&os, &op);
    last_segments += size_segments;
    
    while ((op.e_o_s || (enc_granulepos + (frame_size * 48000 / coding_rate) - last_granulepos > max_ogg_delay) || (last_segments >= 255)) ? ogg_stream_flush_fill(&os, &og, 255 * 255) : ogg_stream_pageout_fill(&os, &og, 255 * 255)) {
        if (ogg_page_packets(&og) != 0) {
            last_granulepos = ogg_page_granulepos(&og);
        }
        last_segments -= og.header[26];
        int writtenPageBytes = writeOggPage(&og, _fileOs);
        if (writtenPageBytes != og.header_len + og.body_len) {
            LOGE("Error: failed writing data to output stream");
            return 0;
        }
        bytes_written += writtenPageBytes;
        pages_out++;
    }
    
    return 1;
}

JNIEXPORT jint Java_org_telegram_messenger_MediaController_startRecord(JNIEnv *env, jclass class, jstring path, jint sampleRate) {
    const char *pathStr = (*env)->GetStringUTFChars(env, path, 0);

    int32_t result = initRecorder(pathStr, sampleRate);

    if (pathStr != 0) {
        (*env)->ReleaseStringUTFChars(env, path, pathStr);
    }

    return result;
}

JNIEXPORT jint Java_org_telegram_messenger_MediaController_writeFrame(JNIEnv *env, jclass class, jobject frame, jint len) {
    jbyte *frameBytes = (*env)->GetDirectBufferAddress(env, frame);
    return writeFrame((uint8_t *) frameBytes, (uint32_t) len, len / 2 < frame_size);
}

JNIEXPORT void Java_org_telegram_messenger_MediaController_stopRecord(JNIEnv *env, jclass class) {
    cleanupRecorder();
}

JNIEXPORT jint Java_org_telegram_messenger_MediaController_isOpusFile(JNIEnv *env, jclass class, jstring path) {
    const char *pathStr = (*env)->GetStringUTFChars(env, path, 0);
    
    int32_t result = 0;
    
    int32_t error = OPUS_OK;
    OggOpusFile *file = op_test_file(pathStr, &error);
    if (file != NULL) {
        error = op_test_open(file);
        op_free(file);
        result = error == OPUS_OK;
    }
    
    if (pathStr != 0) {
        (*env)->ReleaseStringUTFChars(env, path, pathStr);
    }
    
    return result;
}

static inline void set_bits(uint8_t *bytes, int32_t bitOffset, int32_t value) {
    bytes += bitOffset / 8;
    bitOffset %= 8;
    *((int32_t *) bytes) |= (value << bitOffset);
}

JNIEXPORT jbyteArray Java_org_telegram_messenger_MediaController_getWaveform2(JNIEnv *env, jclass class, jshortArray array, jint length) {

    jshort *sampleBuffer = (*env)->GetShortArrayElements(env, array, 0);

    const int32_t resultSamples = 100;
    uint16_t *samples = malloc(100 * 2);
    uint64_t sampleIndex = 0;
    uint16_t peakSample = 0;
    int32_t sampleRate = (int32_t) MAX(1, length / resultSamples);
    int32_t index = 0;

    for (int32_t i = 0; i < length; i++) {
        uint16_t sample = (uint16_t) abs(sampleBuffer[i]);
        if (sample > peakSample) {
            peakSample = sample;
        }
        if (sampleIndex++ % sampleRate == 0) {
            if (index < resultSamples) {
                samples[index++] = peakSample;
            }
            peakSample = 0;
        }
    }

    int64_t sumSamples = 0;
    for (int32_t i = 0; i < resultSamples; i++) {
        sumSamples += samples[i];
    }
    uint16_t peak = (uint16_t) (sumSamples * 1.8f / resultSamples);
    if (peak < 2500) {
        peak = 2500;
    }

    for (int32_t i = 0; i < resultSamples; i++) {
        uint16_t sample = (uint16_t) ((int64_t) samples[i]);
        if (sample > peak) {
            samples[i] = peak;
        }
    }

    (*env)->ReleaseShortArrayElements(env, array, sampleBuffer, 0);

    uint32_t bitstreamLength = resultSamples * 5 / 8 + 1;
    jbyteArray *result = (*env)->NewByteArray(env, bitstreamLength);
    if (result) {
        uint8_t *bytes = malloc(bitstreamLength + 4);
        memset(bytes, 0, bitstreamLength + 4);
        for (int32_t i = 0; i < resultSamples; i++) {
            int32_t value = MIN(31, abs((int32_t) samples[i]) * 31 / peak);
            set_bits(bytes, i * 5, value & 31);
        }
        (*env)->SetByteArrayRegion(env, result, 0, bitstreamLength, (jbyte *) bytes);
    }
    free(samples);
    
    return result;
}

int16_t *sampleBuffer = NULL;

JNIEXPORT jbyteArray Java_org_telegram_messenger_MediaController_getWaveform(JNIEnv *env, jclass class, jstring path) {
    const char *pathStr = (*env)->GetStringUTFChars(env, path, 0);
    jbyteArray result = 0;
    
    int error = OPUS_OK;
    OggOpusFile *opusFile = op_open_file(pathStr, &error);
    if (opusFile != NULL && error == OPUS_OK) {
        int64_t totalSamples = op_pcm_total(opusFile, -1);
        const uint32_t resultSamples = 100;
        int32_t sampleRate = MAX(1, (int32_t) (totalSamples / resultSamples));

        uint16_t *samples = malloc(100 * 2);

        size_t bufferSize = 1024 * 128;
        if (sampleBuffer == NULL) {
            sampleBuffer = malloc(bufferSize);
        }
        uint64_t sampleIndex = 0;
        uint16_t peakSample = 0;

        int32_t index = 0;

        while (1) {
            int readSamples = op_read(opusFile, sampleBuffer, bufferSize / 2, NULL);
            for (int32_t i = 0; i < readSamples; i++) {
                uint16_t sample = (uint16_t) abs(sampleBuffer[i]);
                if (sample > peakSample) {
                    peakSample = sample;
                }
                if (sampleIndex++ % sampleRate == 0) {
                    if (index < resultSamples) {
                        samples[index++] = peakSample;
                    }
                    peakSample = 0;
                }
            }
            if (readSamples == 0) {
                break;
            }
        }

        int64_t sumSamples = 0;
        for (int32_t i = 0; i < resultSamples; i++) {
            sumSamples += samples[i];
        }
        uint16_t peak = (uint16_t) (sumSamples * 1.8f / resultSamples);
        if (peak < 2500) {
            peak = 2500;
        }

        for (int32_t i = 0; i < resultSamples; i++) {
            uint16_t sample = (uint16_t) ((int64_t) samples[i]);
            if (sample > peak) {
                samples[i] = peak;
            }
        }

        //free(sampleBuffer);
        op_free(opusFile);

        uint32_t bitstreamLength = (resultSamples * 5) / 8 + 1;
        result = (*env)->NewByteArray(env, bitstreamLength);
        if (result) {
            uint8_t *bytes = malloc(bitstreamLength + 4);
            memset(bytes, 0, bitstreamLength + 4);

            for (int32_t i = 0; i < resultSamples; i++) {
                int32_t value = MIN(31, abs((int32_t) samples[i]) * 31 / peak);
                set_bits(bytes, i * 5, value & 31);
            }

            (*env)->SetByteArrayRegion(env, result, 0, bitstreamLength, (jbyte *) bytes);
        }
        free(samples);
    }
    
    if (pathStr != 0) {
        (*env)->ReleaseStringUTFChars(env, path, pathStr);
    }
    
    return result;
}

JNIEXPORT void JNICALL Java_org_telegram_ui_Stories_recorder_FfmpegAudioWaveformLoader_init(JNIEnv *env, jobject obj, jstring pathJStr, jint count) {
    const char *path = (*env)->GetStringUTFChars(env, pathJStr, 0);

    // Initialize FFmpeg components
    av_register_all();

    AVFormatContext *formatContext = avformat_alloc_context();
    if (!formatContext) {
        // Handle error
        return;
    }

    int res;
    if ((res = avformat_open_input(&formatContext, path, NULL, NULL)) != 0) {
        LOGD("avformat_open_input error %s", av_err2str(res));
        // Handle error
        avformat_free_context(formatContext);
        return;
    }

    if (avformat_find_stream_info(formatContext, NULL) < 0) {
        // Handle error
        avformat_close_input(&formatContext);
        return;
    }

    AVCodec *codec = NULL;
    int audioStreamIndex = av_find_best_stream(formatContext, AVMEDIA_TYPE_AUDIO, -1, -1, &codec, 0);
    if (audioStreamIndex < 0) {
        LOGD("av_find_best_stream error %s", av_err2str(audioStreamIndex));
        // Handle error
        avformat_close_input(&formatContext);
        return;
    }

    AVCodecContext *codecContext = avcodec_alloc_context3(codec);
    avcodec_parameters_to_context(codecContext, formatContext->streams[audioStreamIndex]->codecpar);

    int64_t duration_in_microseconds = formatContext->duration;
    double duration_in_seconds = (double)duration_in_microseconds / AV_TIME_BASE;

    if (avcodec_open2(codecContext, codec, NULL) < 0) {
        // Handle error
        avcodec_free_context(&codecContext);
        avformat_close_input(&formatContext);
        return;
    }

    // Obtain the class and method to callback
    jclass cls = (*env)->GetObjectClass(env, obj);
    jmethodID mid = (*env)->GetMethodID(env, cls, "receiveChunk", "([SI)V");

    AVFrame *frame = av_frame_alloc();
    AVPacket packet;

    int sampleRate = codecContext->sample_rate;  // Sample rate from FFmpeg's codec context
    int skip = 4;
    int barWidth = (int) round((double) duration_in_seconds * sampleRate / count / (1 + skip)); // Assuming you have 'duration' and 'count' defined somewhere

    int channels = codecContext->channels;

    short peak = 0;
    int currentCount = 0;
    int index = 0;
    int chunkIndex = 0;
    short waveformChunkData[32];  // Allocate the chunk array

    while (av_read_frame(formatContext, &packet) >= 0) {
        if (packet.stream_index == audioStreamIndex) {
            // Decode the audio packet
            int response = avcodec_send_packet(codecContext, &packet);

            while (response >= 0) {
                response = avcodec_receive_frame(codecContext, frame);
                if (response == AVERROR(EAGAIN) || response == AVERROR_EOF) {
                    break;
                } else if (response < 0) {
                    // Handle error
                    break;
                }

                const int is_planar = av_sample_fmt_is_planar(codecContext->sample_fmt);
                const int sample_size = av_get_bytes_per_sample(codecContext->sample_fmt);
                for (int i = 0; i < frame->nb_samples; i++) {
                    int sum = 0;
                    for (int channel = 0; channel < channels; channel++) {
                        uint8_t *data;
                        if (is_planar) {
                            data = frame->data[channel] + i * sample_size;
                        } else {
                            data = frame->data[0] + (i * channels + channel) * sample_size;
                        }
                        short sample_value = 0;
                        switch (codecContext->sample_fmt) {
                            case AV_SAMPLE_FMT_S16:
                            case AV_SAMPLE_FMT_S16P:
                                // Signed 16-bit PCM
                                sample_value = *(int16_t *)data;
                                break;

                            case AV_SAMPLE_FMT_FLT:
                            case AV_SAMPLE_FMT_FLTP:
                                // 32-bit float, scale to 16-bit PCM range
                                sample_value = (short)(*(float *)data * 32767.0f);
                                break;

                            case AV_SAMPLE_FMT_U8:
                            case AV_SAMPLE_FMT_U8P:
                                // Unsigned 8-bit PCM, scale to 16-bit PCM range
                                sample_value = (*(uint8_t *)data - 128) * 256;
                                break;

                            default:
                                break;
                        }
                        sum += sample_value;
                    }
                    short value = sum / channels;

                    if (currentCount >= barWidth) {
                        waveformChunkData[index - chunkIndex] = peak;
                        index++;
                        if (index - chunkIndex >= sizeof(waveformChunkData) / sizeof(short) || index >= count) {
                            jshortArray waveformData = (*env)->NewShortArray(env, sizeof(waveformChunkData) / sizeof(short));
                            (*env)->SetShortArrayRegion(env, waveformData, 0, sizeof(waveformChunkData) / sizeof(short), waveformChunkData);
                            (*env)->CallVoidMethod(env, obj, mid, waveformData, sizeof(waveformChunkData) / sizeof(short));

                            // Reset the chunk data
                            memset(waveformChunkData, 0, sizeof(waveformChunkData));
                            chunkIndex = index;

                            // Delete local reference to avoid memory leak
                            (*env)->DeleteLocalRef(env, waveformData);
                        }
                        peak = 0;
                        currentCount = 0;
                        if (index >= count) {
                            break;
                        }
                    }

                    if (peak < value) {
                        peak = value;
                    }
                    currentCount++;

                    // Skip logic
                    i += skip;
                    if (i >= frame->nb_samples) {
                        break;
                    }
                }
            }
        }

        av_packet_unref(&packet);

        if (index >= count) {
            break;
        }

        // Check for stopping flag
        jfieldID fid = (*env)->GetFieldID(env, cls, "running", "Z");
        jboolean running = (*env)->GetBooleanField(env, obj, fid);
        if (running == JNI_FALSE) {
            break;
        }
    }

    av_frame_free(&frame);
    avcodec_free_context(&codecContext);
    avformat_close_input(&formatContext);

    (*env)->ReleaseStringUTFChars(env, pathJStr, path);
}

int cropOpusAudio(const char *inputPath, const char *outputPath, float startTimeMs, float endTimeMs) {
    int error;
    OggOpusFile *opusFile = op_open_file(inputPath, &error);
    if (!opusFile || error != OPUS_OK) {
        LOGE("Failed to open input opus file: %s", opus_strerror(error));
        return 0;
    }

    const OpusHead *head = op_head(opusFile, -1);
    if (!head) {
        LOGE("Failed to read Opus header");
        op_free(opusFile);
        return 0;
    }

    int channels = head->channel_count;
    opus_int64 total_source_samples = op_pcm_total(opusFile, -1);
    opus_int32 rate = 48000;

    opus_int64 start_sample = MAX(0, MIN(total_source_samples, (opus_int64) ((startTimeMs / 1000.0) * rate)));
    opus_int64 end_sample = MAX(0, MIN(total_source_samples, (opus_int64) ((endTimeMs / 1000.0) * rate)));
    opus_int64 crop_length = end_sample - start_sample;

    if (start_sample >= total_source_samples || end_sample > total_source_samples || start_sample >= end_sample) {
        LOGE("Invalid crop range");
        op_free(opusFile);
        return 0;
    }

    if (op_pcm_seek(opusFile, start_sample) != 0) {
        LOGE("Failed to seek to start sample");
        op_free(opusFile);
        return 0;
    }

    if (!initRecorder(outputPath, rate)) {
        LOGE("Failed to init recorder");
        op_free(opusFile);
        return 0;
    }

    int16_t *buffer = malloc(sizeof(int16_t) * frame_size * channels);
    if (!buffer) {
        LOGE("Out of memory");
        cleanupRecorder();
        op_free(opusFile);
        return 0;
    }

    opus_int64 remaining_samples = crop_length;
    while (remaining_samples > 0) {
        int max_samples = (int) MIN(960, remaining_samples / channels);
        if (max_samples <= 0) break;

        int samples_read = op_read(opusFile, buffer, max_samples * channels, NULL);
        if (samples_read <= 0) break;
        remaining_samples -= samples_read;

        int end = remaining_samples <= 0;

        size_t byte_count = samples_read * sizeof(int16_t);
        if (!writeFrame((uint8_t *)buffer, byte_count, end)) {
            LOGE("Failed to write frame");
            free(buffer);
            cleanupRecorder();
            op_free(opusFile);
            return 0;
        }
    }

    free(buffer);
    cleanupRecorder();
    op_free(opusFile);
    return 1;
}

int append_stream(OggOpusFile *of, int16_t* buffer, int channels, int is_last) {
    opus_int64 total_source_samples = op_pcm_total(of, -1);
    while (total_source_samples > 0) {
        int samples_read = op_read(of, buffer, frame_size, NULL);
        if (samples_read < 0) {
            LOGE("Decoding error from op_read(): %d", samples_read);
            return 0;
        }
        if (samples_read == 0) {
            break;
        }
        total_source_samples -= samples_read;
        size_t byte_count = (size_t) samples_read * channels * sizeof(int16_t);
        if (!writeFrame((uint8_t*) buffer, byte_count, is_last && total_source_samples <= 0)) {
            LOGE("Failed to write encoded frame");
            return 0;
        }
    }
    return 1;
}

int joinOpusAudios(const char* file1, const char* file2, const char* dest) {
    int error;
    OggOpusFile *opusFile1 = op_open_file(file1, &error);
    if (!opusFile1 || error != OPUS_OK) {
        LOGE("Failed to open input opus file1: %s", opus_strerror(error));
        return 0;
    }
    OggOpusFile *opusFile2 = op_open_file(file2, &error);
    if (!opusFile2 || error != OPUS_OK) {
        LOGE("Failed to open input opus file2: %s", opus_strerror(error));
        return 0;
    }

    const OpusHead *head1 = op_head(opusFile1, -1);
    if (!head1) {
        LOGE("Failed to read Opus header");
        op_free(opusFile1);
        op_free(opusFile2);
        return 0;
    }
    const OpusHead *head2 = op_head(opusFile2, -1);
    if (!head2) {
        LOGE("Failed to read Opus header");
        op_free(opusFile1);
        op_free(opusFile2);
        return 0;
    }

    int channels = MIN(head1->channel_count, head2->channel_count);
    opus_int32 rate = 48000;

    if (!initRecorder(dest, rate)) {
        LOGE("Failed to init recorder");
        op_free(opusFile1);
        op_free(opusFile2);
        return 0;
    }

    int16_t *buffer = malloc(sizeof(int16_t) * frame_size * channels);
    if (!buffer) {
        LOGE("Out of memory");
        cleanupRecorder();
        op_free(opusFile1);
        op_free(opusFile2);
        return 0;
    }

    append_stream(opusFile1, buffer, channels, 0);
    append_stream(opusFile2, buffer, channels, 1);

    free(buffer);
    cleanupRecorder();
    op_free(opusFile1);
    op_free(opusFile2);

    return 1;
}

JNIEXPORT jboolean Java_org_telegram_messenger_MediaController_cropOpusFile(JNIEnv *env, jclass class, jstring src, jstring dst, jlong startMs, jlong endMs) {
    const char* srcStr = (*env)->GetStringUTFChars(env, src, 0);
    const char* dstStr = (*env)->GetStringUTFChars(env, dst, 0);
    int result = cropOpusAudio(srcStr, dstStr, startMs, endMs);
    (*env)->ReleaseStringUTFChars(env, src, srcStr);
    (*env)->ReleaseStringUTFChars(env, dst, dstStr);
    return result == 1;
}

JNIEXPORT jboolean Java_org_telegram_messenger_MediaController_joinOpusFiles(JNIEnv* env, jclass class, jstring file1, jstring file2, jstring dest) {
    const char* file1Str = (*env)->GetStringUTFChars(env, file1, 0);
    const char* file2Str = (*env)->GetStringUTFChars(env, file2, 0);
    const char* destStr = (*env)->GetStringUTFChars(env, dest, 0);
    int result = joinOpusAudios(file1Str, file2Str, destStr);
    (*env)->ReleaseStringUTFChars(env, file1, file1Str);
    (*env)->ReleaseStringUTFChars(env, file2, file2Str);
    (*env)->ReleaseStringUTFChars(env, dest, destStr);
    return result == 1;
}