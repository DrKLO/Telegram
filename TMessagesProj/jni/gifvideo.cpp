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
jmethodID jclass_AnimatedFileDrawableStream_isFinishedLoadingFile;
jmethodID jclass_AnimatedFileDrawableStream_getFinishedFilePath;

typedef struct H2645NAL {
    uint8_t *rbsp_buffer;
    int size;
    const uint8_t *data;
    int size_bits;
    int raw_size;
    const uint8_t *raw_data;
    int type;
    int temporal_id;
    int nuh_layer_id;
    int skipped_bytes;
    int skipped_bytes_pos_size;
    int *skipped_bytes_pos;
    int ref_idc;
    GetBitContext gb;
} H2645NAL;

typedef struct H2645RBSP {
    uint8_t *rbsp_buffer;
    AVBufferRef *rbsp_buffer_ref;
    int rbsp_buffer_alloc_size;
    int rbsp_buffer_size;
} H2645RBSP;

typedef struct H2645Packet {
    H2645NAL *nals;
    H2645RBSP rbsp;
    int nb_nals;
    int nals_allocated;
    unsigned nal_buffer_size;
} H2645Packet;

void ff_h2645_packet_uninit(H2645Packet *pkt) {
    int i;
    for (i = 0; i < pkt->nals_allocated; i++) {
        av_freep(&pkt->nals[i].skipped_bytes_pos);
    }
    av_freep(&pkt->nals);
    pkt->nals_allocated = pkt->nal_buffer_size = 0;
    if (pkt->rbsp.rbsp_buffer_ref) {
        av_buffer_unref(&pkt->rbsp.rbsp_buffer_ref);
        pkt->rbsp.rbsp_buffer = NULL;
    } else
        av_freep(&pkt->rbsp.rbsp_buffer);
    pkt->rbsp.rbsp_buffer_alloc_size = pkt->rbsp.rbsp_buffer_size = 0;
}

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

        ff_h2645_packet_uninit(&h2645Packet);
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

    H2645Packet h2645Packet = {nullptr};

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
            LOGE("failed to find %s codec", av_get_media_type_string(type));
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

#define MAX_MBPAIR_SIZE (256*1024)

int ff_h2645_extract_rbsp(const uint8_t *src, int length, H2645RBSP *rbsp, H2645NAL *nal)
{
    int i, si, di;
    uint8_t *dst;

    nal->skipped_bytes = 0;
#define STARTCODE_TEST                                                  \
        if (i + 2 < length && src[i + 1] == 0 && src[i + 2] <= 3) {     \
            if (src[i + 2] != 3 && src[i + 2] != 0) {                   \
                /* startcode, so we must be past the end */             \
                length = i;                                             \
            }                                                           \
            break;                                                      \
        }

    for (i = 0; i + 1 < length; i += 2) {
        if (src[i])
            continue;
        if (i > 0 && src[i - 1] == 0)
            i--;
        STARTCODE_TEST;
    }

    if (i > length)
        i = length;

    nal->rbsp_buffer = &rbsp->rbsp_buffer[rbsp->rbsp_buffer_size];
    dst = nal->rbsp_buffer;

    memcpy(dst, src, i);
    si = di = i;
    while (si + 2 < length) {
        if (src[si + 2] > 3) {
            dst[di++] = src[si++];
            dst[di++] = src[si++];
        } else if (src[si] == 0 && src[si + 1] == 0 && src[si + 2] != 0) {
            if (src[si + 2] == 3) {
                dst[di++] = 0;
                dst[di++] = 0;
                si       += 3;

                if (nal->skipped_bytes_pos) {
                    nal->skipped_bytes++;
                    if (nal->skipped_bytes_pos_size < nal->skipped_bytes) {
                        nal->skipped_bytes_pos_size *= 2;
                        av_reallocp_array(&nal->skipped_bytes_pos,
                                          nal->skipped_bytes_pos_size,
                                          sizeof(*nal->skipped_bytes_pos));
                        if (!nal->skipped_bytes_pos) {
                            nal->skipped_bytes_pos_size = 0;
                            return AVERROR(ENOMEM);
                        }
                    }
                    if (nal->skipped_bytes_pos)
                        nal->skipped_bytes_pos[nal->skipped_bytes-1] = di - 1;
                }
                continue;
            } else // next start code
                goto nsc;
        }

        dst[di++] = src[si++];
    }
    while (si < length)
        dst[di++] = src[si++];

    nsc:
    memset(dst + di, 0, AV_INPUT_BUFFER_PADDING_SIZE);

    nal->data = dst;
    nal->size = di;
    nal->raw_data = src;
    nal->raw_size = si;
    rbsp->rbsp_buffer_size += si;

    return si;
}

static inline int get_nalsize(int nal_length_size, const uint8_t *buf, int buf_size, int *buf_index) {
    int i, nalsize = 0;
    if (*buf_index >= buf_size - nal_length_size) {
        return AVERROR(EAGAIN);
    }
    for (i = 0; i < nal_length_size; i++)
        nalsize = ((unsigned)nalsize << 8) | buf[(*buf_index)++];
    if (nalsize <= 0 || nalsize > buf_size - *buf_index) {
        return AVERROR_INVALIDDATA;
    }
    return nalsize;
}

static int find_next_start_code(const uint8_t *buf, const uint8_t *next_avc) {
    int i = 0;
    if (buf + 3 >= next_avc)
        return next_avc - buf;
    while (buf + i + 3 < next_avc) {
        if (buf[i] == 0 && buf[i + 1] == 0 && buf[i + 2] == 1)
            break;
        i++;
    }
    return i + 3;
}

static int get_bit_length(H2645NAL *nal, int skip_trailing_zeros) {
    int size = nal->size;
    int v;

    while (skip_trailing_zeros && size > 0 && nal->data[size - 1] == 0)
        size--;

    if (!size)
        return 0;

    v = nal->data[size - 1];

    if (size > INT_MAX / 8)
        return AVERROR(ERANGE);
    size *= 8;

    /* remove the stop bit and following trailing zeros,
     * or nothing for damaged bitstreams */
    if (v)
        size -= ff_ctz(v) + 1;

    return size;
}

static void alloc_rbsp_buffer(H2645RBSP *rbsp, unsigned int size) {
    int min_size = size;

    if (size > INT_MAX - AV_INPUT_BUFFER_PADDING_SIZE)
        goto fail;
    size += AV_INPUT_BUFFER_PADDING_SIZE;

    if (rbsp->rbsp_buffer_alloc_size >= size &&
        (!rbsp->rbsp_buffer_ref || av_buffer_is_writable(rbsp->rbsp_buffer_ref))) {
        memset(rbsp->rbsp_buffer + min_size, 0, AV_INPUT_BUFFER_PADDING_SIZE);
        return;
    }

    size = FFMIN(size + size / 16 + 32, INT_MAX);

    if (rbsp->rbsp_buffer_ref)
        av_buffer_unref(&rbsp->rbsp_buffer_ref);
    else
        av_free(rbsp->rbsp_buffer);

    rbsp->rbsp_buffer = (uint8_t *) av_mallocz(size);
    if (!rbsp->rbsp_buffer)
        goto fail;
    rbsp->rbsp_buffer_alloc_size = size;

    return;

    fail:
    rbsp->rbsp_buffer_alloc_size = 0;
    if (rbsp->rbsp_buffer_ref) {
        av_buffer_unref(&rbsp->rbsp_buffer_ref);
        rbsp->rbsp_buffer = NULL;
    } else
        av_freep(&rbsp->rbsp_buffer);

    return;
}

static int h264_parse_nal_header(H2645NAL *nal) {
    GetBitContext *gb = &nal->gb;

    if (get_bits1(gb) != 0)
        return AVERROR_INVALIDDATA;

    nal->ref_idc = get_bits(gb, 2);
    nal->type    = get_bits(gb, 5);

    return 1;
}

int ff_h2645_packet_split(H2645Packet *pkt, const uint8_t *buf, int length, int is_nalff, int nal_length_size) {
    GetByteContext bc;
    int consumed, ret = 0;
    int next_avc = is_nalff ? 0 : length;
    int64_t padding = MAX_MBPAIR_SIZE;

    bytestream2_init(&bc, buf, length);
    alloc_rbsp_buffer(&pkt->rbsp, length + padding);

    if (!pkt->rbsp.rbsp_buffer)
        return AVERROR(ENOMEM);

    pkt->rbsp.rbsp_buffer_size = 0;
    pkt->nb_nals = 0;
    while (bytestream2_get_bytes_left(&bc) >= 4) {
        H2645NAL *nal;
        int extract_length = 0;
        int skip_trailing_zeros = 1;

        if (bytestream2_tell(&bc) == next_avc) {
            int i = 0;
            extract_length = get_nalsize(nal_length_size, bc.buffer, bytestream2_get_bytes_left(&bc), &i);
            if (extract_length < 0)
                return extract_length;

            bytestream2_skip(&bc, nal_length_size);

            next_avc = bytestream2_tell(&bc) + extract_length;
        } else {
            int buf_index;
            buf_index = find_next_start_code(bc.buffer, buf + next_avc);
            bytestream2_skip(&bc, buf_index);
            if (!bytestream2_get_bytes_left(&bc)) {
                if (pkt->nb_nals > 0) {
                    return 0;
                } else {
                    return AVERROR_INVALIDDATA;
                }
            }
            extract_length = FFMIN(bytestream2_get_bytes_left(&bc), next_avc - bytestream2_tell(&bc));
            if (bytestream2_tell(&bc) >= next_avc) {
                bytestream2_skip(&bc, next_avc - bytestream2_tell(&bc));
                continue;
            }
        }

        if (pkt->nals_allocated < pkt->nb_nals + 1) {
            int new_size = pkt->nals_allocated + 1;
            void *tmp;

            if (new_size >= INT_MAX / sizeof(*pkt->nals))
                return AVERROR(ENOMEM);

            tmp = av_fast_realloc(pkt->nals, &pkt->nal_buffer_size, new_size * sizeof(*pkt->nals));
            if (!tmp)
                return AVERROR(ENOMEM);

            pkt->nals = (H2645NAL *) tmp;
            memset(pkt->nals + pkt->nals_allocated, 0, sizeof(*pkt->nals));

            nal = &pkt->nals[pkt->nb_nals];
            nal->skipped_bytes_pos_size = 1024;
            nal->skipped_bytes_pos = (int *) av_malloc_array(nal->skipped_bytes_pos_size, sizeof(*nal->skipped_bytes_pos));
            if (!nal->skipped_bytes_pos)
                return AVERROR(ENOMEM);

            pkt->nals_allocated = new_size;
        }
        nal = &pkt->nals[pkt->nb_nals];

        consumed = ff_h2645_extract_rbsp(bc.buffer, extract_length, &pkt->rbsp, nal);
        if (consumed < 0)
            return consumed;

        pkt->nb_nals++;

        bytestream2_skip(&bc, consumed);

        /* see commit 3566042a0 */
        if (bytestream2_get_bytes_left(&bc) >= 4 &&
            bytestream2_peek_be32(&bc) == 0x000001E0)
            skip_trailing_zeros = 0;

        nal->size_bits = get_bit_length(nal, skip_trailing_zeros);

        ret = init_get_bits(&nal->gb, nal->data, nal->size_bits);
        if (ret < 0)
            return ret;

        ret = h264_parse_nal_header(nal);
        if (ret <= 0 || nal->size <= 0 || nal->size_bits <= 0) {
            pkt->nb_nals--;
        }
    }

    return 0;
}

#define MAX_SPS_COUNT          32

const uint8_t ff_zigzag_direct[64] = {
        0,   1,  8, 16,  9,  2,  3, 10,
        17, 24, 32, 25, 18, 11,  4,  5,
        12, 19, 26, 33, 40, 48, 41, 34,
        27, 20, 13,  6,  7, 14, 21, 28,
        35, 42, 49, 56, 57, 50, 43, 36,
        29, 22, 15, 23, 30, 37, 44, 51,
        58, 59, 52, 45, 38, 31, 39, 46,
        53, 60, 61, 54, 47, 55, 62, 63
};

const uint8_t ff_zigzag_scan[16+1] = {
        0 + 0 * 4, 1 + 0 * 4, 0 + 1 * 4, 0 + 2 * 4,
        1 + 1 * 4, 2 + 0 * 4, 3 + 0 * 4, 2 + 1 * 4,
        1 + 2 * 4, 0 + 3 * 4, 1 + 3 * 4, 2 + 2 * 4,
        3 + 1 * 4, 3 + 2 * 4, 2 + 3 * 4, 3 + 3 * 4,
};

static int decode_scaling_list(GetBitContext *gb, uint8_t *factors, int size) {
    int i, last = 8, next = 8;
    const uint8_t *scan = size == 16 ? ff_zigzag_scan : ff_zigzag_direct;
    if (!get_bits1(gb)) {

    } else {
        for (i = 0; i < size; i++) {
            if (next) {
                int v = get_se_golomb(gb);
                if (v < -128 || v > 127) {
                    return AVERROR_INVALIDDATA;
                }
                next = (last + v) & 0xff;
            }
            if (!i && !next) { /* matrix not written, we use the preset one */
                break;
            }
            last = factors[scan[i]] = next ? next : last;
        }
    }
    return 0;
}

static int decode_scaling_matrices(GetBitContext *gb, int chroma_format_idc, uint8_t(*scaling_matrix4)[16], uint8_t(*scaling_matrix8)[64]) {
    int ret = 0;
    if (get_bits1(gb)) {
        ret |= decode_scaling_list(gb, scaling_matrix4[0], 16);        // Intra, Y
        ret |= decode_scaling_list(gb, scaling_matrix4[1], 16); // Intra, Cr
        ret |= decode_scaling_list(gb, scaling_matrix4[2], 16); // Intra, Cb
        ret |= decode_scaling_list(gb, scaling_matrix4[3], 16);        // Inter, Y
        ret |= decode_scaling_list(gb, scaling_matrix4[4], 16); // Inter, Cr
        ret |= decode_scaling_list(gb, scaling_matrix4[5], 16); // Inter, Cb

        ret |= decode_scaling_list(gb, scaling_matrix8[0], 64); // Intra, Y
        ret |= decode_scaling_list(gb, scaling_matrix8[3], 64); // Inter, Y
        if (chroma_format_idc == 3) {
            ret |= decode_scaling_list(gb, scaling_matrix8[1], 64); // Intra, Cr
            ret |= decode_scaling_list(gb, scaling_matrix8[4], 64); // Inter, Cr
            ret |= decode_scaling_list(gb, scaling_matrix8[2], 64); // Intra, Cb
            ret |= decode_scaling_list(gb, scaling_matrix8[5], 64); // Inter, Cb
        }
        if (!ret)
            ret = 1;
    }

    return ret;
}

int ff_h264_decode_seq_parameter_set(GetBitContext *gb, int &width, int &height) {
    int profile_idc, level_idc, constraint_set_flags = 0;
    unsigned int sps_id;
    int i, log2_max_frame_num_minus4;
    int ret;

    profile_idc = get_bits(gb, 8);
    constraint_set_flags |= get_bits1(gb) << 0;
    constraint_set_flags |= get_bits1(gb) << 1;
    constraint_set_flags |= get_bits1(gb) << 2;
    constraint_set_flags |= get_bits1(gb) << 3;
    constraint_set_flags |= get_bits1(gb) << 4;
    constraint_set_flags |= get_bits1(gb) << 5;
    skip_bits(gb, 2);
    level_idc = get_bits(gb, 8);
    sps_id = get_ue_golomb_31(gb);

    if (sps_id >= MAX_SPS_COUNT) {
        return false;
    }

    if (profile_idc == 100 ||  // High profile
        profile_idc == 110 ||  // High10 profile
        profile_idc == 122 ||  // High422 profile
        profile_idc == 244 ||  // High444 Predictive profile
        profile_idc == 44 ||  // Cavlc444 profile
        profile_idc == 83 ||  // Scalable Constrained High profile (SVC)
        profile_idc == 86 ||  // Scalable High Intra profile (SVC)
        profile_idc == 118 ||  // Stereo High profile (MVC)
        profile_idc == 128 ||  // Multiview High profile (MVC)
        profile_idc == 138 ||  // Multiview Depth High profile (MVCD)
        profile_idc == 144) {  // old High444 profile
        int chroma_format_idc = get_ue_golomb_31(gb);
        if (chroma_format_idc > 3U) {
            return false;
        } else if (chroma_format_idc == 3) {
            int residual_color_transform_flag = get_bits1(gb);
            if (residual_color_transform_flag) {
                return false;
            }
        }
        int bit_depth_luma = get_ue_golomb(gb) + 8;
        int bit_depth_chroma = get_ue_golomb(gb) + 8;
        if (bit_depth_chroma != bit_depth_luma) {
            return false;
        }
        if (bit_depth_luma < 8 || bit_depth_luma > 14 || bit_depth_chroma < 8 || bit_depth_chroma > 14) {
            return false;
        }
        get_bits1(gb);
        uint8_t scaling_matrix4[6][16];
        uint8_t scaling_matrix8[6][64];
        ret = decode_scaling_matrices(gb, chroma_format_idc, scaling_matrix4, scaling_matrix8);
        if (ret < 0)
            return false;
    }

    get_ue_golomb(gb);

    int poc_type = get_ue_golomb_31(gb);

    if (poc_type == 0) {
        unsigned t = get_ue_golomb(gb);
        if (t > 12) {
            return false;
        }
    } else if (poc_type == 1) {
        get_bits1(gb);
        int offset_for_non_ref_pic = get_se_golomb_long(gb);
        int offset_for_top_to_bottom_field = get_se_golomb_long(gb);

        if (offset_for_non_ref_pic == INT32_MIN || offset_for_top_to_bottom_field == INT32_MIN) {
            return false;
        }

        int poc_cycle_length = get_ue_golomb(gb);

        if ((unsigned) poc_cycle_length >= 256) {
            return false;
        }

        for (i = 0; i < poc_cycle_length; i++) {
            int offset_for_ref_frame = get_se_golomb_long(gb);
            if (offset_for_ref_frame == INT32_MIN) {
                return false;
            }
        }
    } else if (poc_type != 2) {
        return false;
    }

    get_ue_golomb_31(gb);
    get_bits1(gb);
    int mb_width = get_ue_golomb(gb) + 1;
    int mb_height = get_ue_golomb(gb) + 1;

    if (width == 0 || height == 0) {
        width = mb_width;
        height = mb_height;
    }
    return mb_width != width || mb_height != height;
}

int decode_packet(VideoInfo *info, int *got_frame) {
    int ret = 0;
    int decoded = info->pkt.size;
    *got_frame = 0;

    if (info->pkt.stream_index == info->video_stream_idx) {
        if (info->video_stream->codecpar->codec_id == AV_CODEC_ID_H264 && decoded > 0) {
            ff_h2645_packet_split(&info->h2645Packet, info->pkt.data, info->pkt.size, 1, 4);
            for (int i = 0; i < info->h2645Packet.nb_nals; i++) {
                H2645NAL *nal = &info->h2645Packet.nals[i];
                switch (nal->type) {
                    case 7: {
                        GetBitContext tmp_gb = nal->gb;
                        info->dropFrames = ff_h264_decode_seq_parameter_set(&tmp_gb, info->firstWidth, info->firstHeight);
                    }
                }
            }
        }
        if (!info->dropFrames) {
            ret = avcodec_decode_video2(info->video_dec_ctx, info->frame, got_frame, &info->pkt);
            if (ret != 0) {
                return ret;
            }
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
                int ret = (int) read(info->fd, buf, (size_t) buf_size);
                return ret ? ret : AVERROR_EOF;
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

extern "C" JNIEXPORT void JNICALL Java_org_telegram_ui_Components_AnimatedFileDrawable_getVideoInfo(JNIEnv *env, jclass clazz,jint sdkVersion, jstring src, jintArray data) {
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

extern "C" JNIEXPORT void JNICALL Java_org_telegram_ui_Components_AnimatedFileDrawable_seekToMs(JNIEnv *env, jclass clazz, jlong ptr, jlong ms, jboolean precise) {
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
            return;
        }
        int got_frame = 0;
        int32_t tries = 1000;
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
                    return;
                }
                if (got_frame == 0) {
                    av_seek_frame(info->fmt_ctx, info->video_stream_idx, 0, AVSEEK_FLAG_BACKWARD | AVSEEK_FLAG_FRAME);
                    return;
                }
            }
            if (ret < 0) {
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
                    return;
                }
            }
            tries--;
        }
    }
}

uint32_t premultiply_channel_value(const uint32_t pixel, const uint8_t offset, const float normalizedAlpha) {
    auto multipliedValue = ((pixel >> offset) & 0xFF) * normalizedAlpha;
    return ((uint32_t)std::min(multipliedValue, 255.0f)) << offset;
}

static inline void writeFrameToBitmap(JNIEnv *env, VideoInfo *info, jintArray data, jobject bitmap, jint stride) {
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
                if (info->frame->format > AV_PIX_FMT_NONE && info->frame->format < AV_PIX_FMT_NB && !info->frame->format == AV_PIX_FMT_YUVA420P) {
                    info->sws_ctx = sws_getContext(info->frame->width, info->frame->height, (AVPixelFormat) info->frame->format, bitmapWidth, bitmapHeight, AV_PIX_FMT_RGBA, SWS_BILINEAR, NULL, NULL, NULL);
                } else if (info->video_dec_ctx->pix_fmt > AV_PIX_FMT_NONE && info->video_dec_ctx->pix_fmt < AV_PIX_FMT_NB && !info->frame->format == AV_PIX_FMT_YUVA420P) {
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
                sws_scale(info->sws_ctx, info->frame->data, info->frame->linesize, 0,
                          info->frame->height, dst_data, info->dst_linesize);
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

extern "C" JNIEXPORT jint JNICALL Java_org_telegram_ui_Components_AnimatedFileDrawable_getVideoFrame(JNIEnv *env, jclass clazz, jlong ptr, jobject bitmap, jintArray data, jint stride, jboolean preview, jfloat start_time, jfloat end_time) {
    if (ptr == NULL || bitmap == nullptr) {
        return 0;
    }
    //int64_t time = ConnectionsManager::getInstance(0).getCurrentTimeMonotonicMillis();
    VideoInfo *info = (VideoInfo *) (intptr_t) ptr;
    int ret = 0;
    int got_frame = 0;
    int32_t triesCount = preview ? 50 : 6;
    //info->has_decoded_frames = false;
    while (!info->stopped && triesCount != 0) {
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
            if (!preview && got_frame == 0) {
                if (info->has_decoded_frames) {
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
        }
        if (ret < 0 || info->seeking) {
            return 0;
        }
        if (got_frame) {
            //LOGD("decoded frame with w = %d, h = %d, format = %d", info->frame->width, info->frame->height, info->frame->format);
            if (info->frame->format == AV_PIX_FMT_YUV420P || info->frame->format == AV_PIX_FMT_BGRA || info->frame->format == AV_PIX_FMT_YUVJ420P || info->frame->format == AV_PIX_FMT_YUV444P || info->frame->format == AV_PIX_FMT_YUVA420P) {
                writeFrameToBitmap(env, info, data, bitmap, stride);
            }
            info->has_decoded_frames = true;
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
    jclass_AnimatedFileDrawableStream_getFinishedFilePath = env->GetMethodID(jclass_AnimatedFileDrawableStream, "getFinishedFilePath", "()Ljava/lang/String;");
    if (jclass_AnimatedFileDrawableStream_getFinishedFilePath == 0) {
        return JNI_FALSE;
    }

    return JNI_TRUE;
}
