#pragma once

#include <functional>

extern "C" {
#include <libavformat/avformat.h>
#include <libavcodec/avcodec.h>
}

// Pull-based reader over a single decoded video stream.
//
// Wraps the FFmpeg send/receive API and exposes getNextFrame(), which yields
// frames one at a time in DISPLAY (presentation) order. The decoder itself
// performs B-frame reordering, so callers never see decode-order packets.
//
// Scope: this class owns ONLY the per-stream decode loop (send/receive/flush/
// seek). Higher-level concerns -- looping, end_time trimming, target-pts
// skipping -- belong to the caller and are intentionally kept out.
//
// Ownership: the reader BORROWS the format and codec contexts. The caller
// allocates, opens, frees them, and keeps them alive for the reader's lifetime.
class VideoFrameReader {
public:
    enum class Status {
        Ok,       // a frame is available via frame()
        Eof,      // stream fully drained, no more frames
        Aborted,  // shouldAbort() requested a stop mid-decode
        Error,    // unrecoverable decode/demux error
    };

    VideoFrameReader(AVFormatContext *fmt, AVCodecContext *dec, int streamIndex)
            : m_fmt(fmt), m_dec(dec), m_streamIndex(streamIndex) {
        m_pkt = av_packet_alloc();
        m_frame = av_frame_alloc();
    }

    ~VideoFrameReader() {
        av_frame_free(&m_frame);
        av_packet_free(&m_pkt);
        // m_fmt and m_dec are borrowed: not freed here.
    }

    VideoFrameReader(const VideoFrameReader &) = delete;
    VideoFrameReader &operator=(const VideoFrameReader &) = delete;

    // Optional. If set and it returns true, getNextFrame() stops promptly and
    // returns Status::Aborted. Polled between packet reads. Use it to react to
    // external cancellation without waiting for a blocking I/O read to unwind.
    std::function<bool()> shouldAbort;

    // Decodes and returns the next frame in display order.
    // On Status::Ok, the frame is valid via frame() until the next call.
    // Idempotent after Eof.
    Status getNextFrame() {
        if (m_pkt == nullptr || m_frame == nullptr) {
            return Status::Error;
        }

        av_frame_unref(m_frame);

        for (;;) {
            if (shouldAbort && shouldAbort()) {
                return Status::Aborted;
            }

            // 1. Try to pull a decoded frame first.
            int ret = avcodec_receive_frame(m_dec, m_frame);
            if (ret == 0) {
                return Status::Ok;                 // display-order frame
            }
            if (ret == AVERROR_EOF) {
                return Status::Eof;                // decoder fully drained
            }
            if (ret != AVERROR(EAGAIN)) {
                return Status::Error;              // real decode error
            }

            // 2. EAGAIN: decoder needs more input. Because we always fully drain
            //    receive before sending, send() itself can never return EAGAIN.
            if (m_draining) {
                return Status::Eof;                // flushed and drained
            }
            if (!feedNextPacket()) {
                return Status::Error;
            }
        }
    }

    // Seeks so that the next getNextFrame() starts at (or just before) pts.
    // Flushes decoder buffers and clears draining state. pts is in the video
    // stream's time_base. Returns false on seek failure.
    bool seek(int64_t pts,
              int flags = AVSEEK_FLAG_BACKWARD) {
        int ret = av_seek_frame(m_fmt, m_streamIndex, pts, flags);
        if (ret < 0) {
            return false;
        }
        avcodec_flush_buffers(m_dec);
        av_frame_unref(m_frame);
        m_draining = false;
        return true;
    }

    // Valid only immediately after getNextFrame() returned Status::Ok.
    AVFrame *frame() const { return m_frame; }

    // Presentation timestamp of the current frame, in seconds (display order).
    double frameTimeSeconds() const {
        AVRational tb = m_fmt->streams[m_streamIndex]->time_base;
        return m_frame->best_effort_timestamp * av_q2d(tb);
    }

    int streamIndex() const { return m_streamIndex; }

private:
    // Reads one packet for our stream and sends it to the decoder. On
    // end-of-input, sends a NULL flush packet and enters draining mode.
    bool feedNextPacket() {
        for (;;) {
            int ret = av_read_frame(m_fmt, m_pkt);
            if (ret < 0) {
                avcodec_send_packet(m_dec, nullptr);   // flush
                m_draining = true;
                return true;
            }

            if (m_pkt->stream_index != m_streamIndex) {
                av_packet_unref(m_pkt);
                continue;                              // skip other streams
            }

            ret = avcodec_send_packet(m_dec, m_pkt);
            av_packet_unref(m_pkt);
            if (ret < 0 && ret != AVERROR(EAGAIN)) {
                return false;
            }
            return true;
        }
    }

    AVFormatContext *m_fmt;   // borrowed
    AVCodecContext *m_dec;    // borrowed
    int m_streamIndex;

    AVPacket *m_pkt = nullptr;
    AVFrame *m_frame = nullptr;
    bool m_draining = false;
};