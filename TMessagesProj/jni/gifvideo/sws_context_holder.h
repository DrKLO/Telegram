#ifndef SWS_CONTEXT_HOLDER_H
#define SWS_CONTEXT_HOLDER_H

extern "C" {
#include <libswscale/swscale.h>
#include <libavutil/pixfmt.h>
}

struct SwsContextHolder {

    SwsContext*    ctx        = nullptr;
    int            src_width  = 0;
    int            src_height = 0;
    AVPixelFormat  src_format = AV_PIX_FMT_NONE;
    int            dst_width  = 0;
    int            dst_height = 0;
    AVPixelFormat  dst_format = AV_PIX_FMT_NONE;

    // Default constructor — starts with no context
    SwsContextHolder() = default;

    // Destructor — always free the context when the object is destroyed
    ~SwsContextHolder() {
        reset();
    }

    // Copy constructor — disabled, context ownership must be unique
    SwsContextHolder(const SwsContextHolder&) = delete;

    // Copy assignment — disabled for the same reason
    SwsContextHolder& operator=(const SwsContextHolder&) = delete;

    // Move constructor — transfer ownership from another holder without freeing
    SwsContextHolder(SwsContextHolder&& other) noexcept
            : ctx(other.ctx),
              src_width(other.src_width),   src_height(other.src_height), src_format(other.src_format),
              dst_width(other.dst_width),   dst_height(other.dst_height), dst_format(other.dst_format) {
        other.ctx = nullptr; // prevent the other holder from freeing the context
    }

    // Move assignment — free current context, then take ownership from other
    SwsContextHolder& operator=(SwsContextHolder&& other) noexcept {
        if (this != &other) {
            reset(); // free whatever we currently hold
            ctx        = other.ctx;
            src_width  = other.src_width;
            src_height = other.src_height;
            src_format = other.src_format;
            dst_width  = other.dst_width;
            dst_height = other.dst_height;
            dst_format = other.dst_format;
            other.ctx  = nullptr; // prevent the other holder from freeing the context
        }
        return *this;
    }

    // Returns a valid context for the given parameters.
    // Recreates the context if any parameter has changed since the last call.
    SwsContext* get(
            int new_src_width, int new_src_height, AVPixelFormat new_src_format,
            int new_dst_width, int new_dst_height, AVPixelFormat new_dst_format
    ) {
        // Reuse existing context if all parameters match
        if (ctx        != nullptr        &&
            src_width  == new_src_width  && src_height == new_src_height && src_format == new_src_format &&
            dst_width  == new_dst_width  && dst_height == new_dst_height && dst_format == new_dst_format) {
            return ctx;
        }

        // Parameters changed — free the old context before creating a new one
        reset();

        ctx = sws_getContext(
                new_src_width, new_src_height, new_src_format,
                new_dst_width, new_dst_height, new_dst_format,
                SWS_BILINEAR, nullptr, nullptr, nullptr
        );

        // Only store parameters if context was created successfully
        if (ctx != nullptr) {
            src_width  = new_src_width;
            src_height = new_src_height;
            src_format = new_src_format;
            dst_width  = new_dst_width;
            dst_height = new_dst_height;
            dst_format = new_dst_format;
        }

        return ctx;
    }

    // Free the context and reset all parameters
    void reset() {
        if (ctx != nullptr) {
            sws_freeContext(ctx);
            ctx = nullptr;
        }
        src_width  = src_height = dst_width = dst_height = 0;
        src_format = dst_format = AV_PIX_FMT_NONE;
    }
};

#endif // SWS_CONTEXT_HOLDER_H