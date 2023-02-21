#include "utils/gzip.h"

#include <zlib.h>

#include "rtc_base/copy_on_write_buffer.h"

namespace tgcalls {
namespace {

using uint = decltype(z_stream::avail_in);

} // namespace

bool isGzip(std::vector<uint8_t> const &data) {
    if (data.size() < 2) {
        return false;
    }

    if ((data[0] == 0x1f && data[1] == 0x8b) || (data[0] == 0x78 && data[1] == 0x9c)) {
        return true;
    } else {
        return false;
    }
}

absl::optional<std::vector<uint8_t>> gzipData(std::vector<uint8_t> const &data) {
    z_stream stream;
    stream.zalloc = Z_NULL;
    stream.zfree = Z_NULL;
    stream.opaque = Z_NULL;
    stream.avail_in = (uint)data.size();
    stream.next_in = (Bytef *)(void *)data.data();
    stream.total_out = 0;
    stream.avail_out = 0;

    static const uint ChunkSize = 16384;

    std::vector<uint8_t> output;
    int compression = 9;
    if (deflateInit2(&stream, compression, Z_DEFLATED, 31, 8, Z_DEFAULT_STRATEGY) == Z_OK) {
        output.resize(ChunkSize);

        while (stream.avail_out == 0) {
            if (stream.total_out >= output.size()) {
                output.resize(output.size() + ChunkSize);
            }
            stream.next_out = (uint8_t *)output.data() + stream.total_out;
            stream.avail_out = (uInt)(output.size() - stream.total_out);
            deflate(&stream, Z_FINISH);
        }
        deflateEnd(&stream);
        output.resize(stream.total_out);
    }

    return output;
}

absl::optional<std::vector<uint8_t>> gunzipData(std::vector<uint8_t> const &data, size_t sizeLimit) {
    if (!isGzip(data)) {
        return absl::nullopt;
    }

    z_stream stream;
    stream.zalloc = Z_NULL;
    stream.zfree = Z_NULL;
    stream.avail_in = (uint)data.size();
    stream.next_in = (Bytef *)data.data();
    stream.total_out = 0;
    stream.avail_out = 0;

    std::vector<uint8_t> output;
    if (inflateInit2(&stream, 47) == Z_OK) {
        int status = Z_OK;
        output.resize(data.size() * 2);
        while (status == Z_OK) {
            if (sizeLimit > 0 && stream.total_out > sizeLimit) {
                return absl::nullopt;
            }

            if (stream.total_out >= output.size()) {
                output.resize(output.size() + data.size() / 2);
            }
            stream.next_out = (uint8_t *)output.data() + stream.total_out;
            stream.avail_out = (uInt)(output.size() - stream.total_out);
            status = inflate(&stream, Z_SYNC_FLUSH);
        }
        if (inflateEnd(&stream) == Z_OK) {
            if (status == Z_STREAM_END) {
                output.resize(stream.total_out);
            } else if (sizeLimit > 0 && output.size() > sizeLimit) {
                return absl::nullopt;
            }
        }
    }

    return output;
}

}

/*bool TGIsGzippedData(NSData *data) {
    const UInt8 *bytes = (const UInt8 *)data.bytes;
    return data.length >= 2 && ((bytes[0] == 0x1f && bytes[1] == 0x8b) || (bytes[0] == 0x78 && bytes[1] == 0x9c));
}

NSData *TGGZipData(NSData *data, float level) {
    if (data.length == 0 || TGIsGzippedData(data)) {
        return data;
    }

    z_stream stream;
    stream.zalloc = Z_NULL;
    stream.zfree = Z_NULL;
    stream.opaque = Z_NULL;
    stream.avail_in = (uint)data.length;
    stream.next_in = (Bytef *)(void *)data.bytes;
    stream.total_out = 0;
    stream.avail_out = 0;

    static const NSUInteger ChunkSize = 16384;

    NSMutableData *output = nil;
    int compression = (level < 0.0f) ? Z_DEFAULT_COMPRESSION : (int)(roundf(level * 9));
    if (deflateInit2(&stream, compression, Z_DEFLATED, 31, 8, Z_DEFAULT_STRATEGY) == Z_OK) {
        output = [NSMutableData dataWithLength:ChunkSize];
        while (stream.avail_out == 0) {
            if (stream.total_out >= output.length) {
                output.length += ChunkSize;
            }
            stream.next_out = (uint8_t *)output.mutableBytes + stream.total_out;
            stream.avail_out = (uInt)(output.length - stream.total_out);
            deflate(&stream, Z_FINISH);
        }
        deflateEnd(&stream);
        output.length = stream.total_out;
    }

    return output;
}

NSData * _Nullable TGGUnzipData(NSData *data, uint sizeLimit)
{
    if (data.length == 0 || !TGIsGzippedData(data)) {
        return nil;
    }

    z_stream stream;
    stream.zalloc = Z_NULL;
    stream.zfree = Z_NULL;
    stream.avail_in = (uint)data.length;
    stream.next_in = (Bytef *)data.bytes;
    stream.total_out = 0;
    stream.avail_out = 0;

    NSMutableData *output = nil;
    if (inflateInit2(&stream, 47) == Z_OK) {
        int status = Z_OK;
        output = [NSMutableData dataWithCapacity:data.length * 2];
        while (status == Z_OK) {
            if (sizeLimit > 0 && stream.total_out > sizeLimit) {
                return nil;
            }

            if (stream.total_out >= output.length) {
                output.length = output.length + data.length / 2;
            }
            stream.next_out = (uint8_t *)output.mutableBytes + stream.total_out;
            stream.avail_out = (uInt)(output.length - stream.total_out);
            status = inflate(&stream, Z_SYNC_FLUSH);
        }
        if (inflateEnd(&stream) == Z_OK) {
            if (status == Z_STREAM_END) {
                output.length = stream.total_out;
            } else if (sizeLimit > 0 && output.length > sizeLimit) {
                return nil;
            }
        }
    }

    return output;
}

*/
