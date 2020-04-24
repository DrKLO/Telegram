/*
 *  Copyright (c) 2014 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

// Based on the WAV file format documentation at
// https://ccrma.stanford.edu/courses/422/projects/WaveFormat/ and
// http://www-mmsp.ece.mcgill.ca/Documents/AudioFormats/WAVE/WAVE.html

#include "common_audio/wav_header.h"

#include <cstring>
#include <limits>
#include <string>

#include "rtc_base/checks.h"
#include "rtc_base/logging.h"
#include "rtc_base/sanitizer.h"
#include "rtc_base/system/arch.h"

namespace webrtc {
namespace {

struct ChunkHeader {
  uint32_t ID;
  uint32_t Size;
};
static_assert(sizeof(ChunkHeader) == 8, "ChunkHeader size");

struct RiffHeader {
  ChunkHeader header;
  uint32_t Format;
};

// We can't nest this definition in WavHeader, because VS2013 gives an error
// on sizeof(WavHeader::fmt): "error C2070: 'unknown': illegal sizeof operand".
struct FmtSubchunk {
  ChunkHeader header;
  uint16_t AudioFormat;
  uint16_t NumChannels;
  uint32_t SampleRate;
  uint32_t ByteRate;
  uint16_t BlockAlign;
  uint16_t BitsPerSample;
};
static_assert(sizeof(FmtSubchunk) == 24, "FmtSubchunk size");
const uint32_t kFmtSubchunkSize = sizeof(FmtSubchunk) - sizeof(ChunkHeader);

// Simple wav header. It does not include chunks that are not essential to read
// audio samples.
struct WavHeader {
  RiffHeader riff;
  FmtSubchunk fmt;
  struct {
    ChunkHeader header;
  } data;
};
static_assert(sizeof(WavHeader) == kWavHeaderSize, "no padding in header");

#ifdef WEBRTC_ARCH_LITTLE_ENDIAN
static inline void WriteLE16(uint16_t* f, uint16_t x) {
  *f = x;
}
static inline void WriteLE32(uint32_t* f, uint32_t x) {
  *f = x;
}
static inline void WriteFourCC(uint32_t* f, char a, char b, char c, char d) {
  *f = static_cast<uint32_t>(a) | static_cast<uint32_t>(b) << 8 |
       static_cast<uint32_t>(c) << 16 | static_cast<uint32_t>(d) << 24;
}

static inline uint16_t ReadLE16(uint16_t x) {
  return x;
}
static inline uint32_t ReadLE32(uint32_t x) {
  return x;
}
static inline std::string ReadFourCC(uint32_t x) {
  return std::string(reinterpret_cast<char*>(&x), 4);
}
#else
static inline void WriteLE16(uint16_t* f, uint16_t x) {
  *f = ((x << 8) & 0xff00)  | ( ( x >> 8) & 0x00ff);
}

static inline void WriteLE32(uint32_t* f, uint32_t x) {
    *f = ( (x & 0x000000ff) << 24 )
      | ((x & 0x0000ff00) << 8)
      | ((x & 0x00ff0000) >> 8)
      | ((x & 0xff000000) >> 24 );
}

static inline void WriteFourCC(uint32_t* f, char a, char b, char c, char d) {
    *f = (static_cast<uint32_t>(a) << 24 )
      |  (static_cast<uint32_t>(b) << 16)
      |  (static_cast<uint32_t>(c) << 8)
      |  (static_cast<uint32_t>(d) );
}

static inline uint16_t ReadLE16(uint16_t x) {
  return  (( x & 0x00ff) << 8 )| ((x & 0xff00)>>8);
}

static inline uint32_t ReadLE32(uint32_t x) {
  return   ( (x & 0x000000ff) << 24 )
         | ( (x & 0x0000ff00) << 8 )
         | ( (x & 0x00ff0000) >> 8)
         | ( (x & 0xff000000) >> 24 );
}

static inline std::string ReadFourCC(uint32_t x) {
  x = ReadLE32(x);
  return std::string(reinterpret_cast<char*>(&x), 4);
}
#endif

static inline uint32_t RiffChunkSize(size_t bytes_in_payload) {
  return static_cast<uint32_t>(bytes_in_payload + kWavHeaderSize -
                               sizeof(ChunkHeader));
}

static inline uint32_t ByteRate(size_t num_channels,
                                int sample_rate,
                                size_t bytes_per_sample) {
  return static_cast<uint32_t>(num_channels * sample_rate * bytes_per_sample);
}

static inline uint16_t BlockAlign(size_t num_channels,
                                  size_t bytes_per_sample) {
  return static_cast<uint16_t>(num_channels * bytes_per_sample);
}

// Finds a chunk having the sought ID. If found, then |readable| points to the
// first byte of the sought chunk data. If not found, the end of the file is
// reached.
void FindWaveChunk(ChunkHeader* chunk_header,
                   ReadableWav* readable,
                   const std::string sought_chunk_id) {
  RTC_DCHECK_EQ(sought_chunk_id.size(), 4);
  while (!readable->Eof()) {
    if (readable->Read(chunk_header, sizeof(*chunk_header)) !=
        sizeof(*chunk_header))
      return;  // EOF.
    if (ReadFourCC(chunk_header->ID) == sought_chunk_id)
      return;  // Sought chunk found.
    // Ignore current chunk by skipping its payload.
    if (!readable->SeekForward(chunk_header->Size))
      return;  // EOF or error.
  }
  return;  // EOF.
}

bool ReadFmtChunkData(FmtSubchunk* fmt_subchunk, ReadableWav* readable) {
  // Reads "fmt " chunk payload.
  if (readable->Read(&(fmt_subchunk->AudioFormat), kFmtSubchunkSize) !=
      kFmtSubchunkSize)
    return false;
  const uint32_t fmt_size = ReadLE32(fmt_subchunk->header.Size);
  if (fmt_size != kFmtSubchunkSize) {
    // There is an optional two-byte extension field permitted to be present
    // with PCM, but which must be zero.
    int16_t ext_size;
    if (kFmtSubchunkSize + sizeof(ext_size) != fmt_size)
      return false;
    if (readable->Read(&ext_size, sizeof(ext_size)) != sizeof(ext_size))
      return false;
    if (ext_size != 0)
      return false;
  }
  return true;
}

}  // namespace

bool CheckWavParameters(size_t num_channels,
                        int sample_rate,
                        WavFormat format,
                        size_t bytes_per_sample,
                        size_t num_samples) {
  // num_channels, sample_rate, and bytes_per_sample must be positive, must fit
  // in their respective fields, and their product must fit in the 32-bit
  // ByteRate field.
  if (num_channels == 0 || sample_rate <= 0 || bytes_per_sample == 0)
    return false;
  if (static_cast<uint64_t>(sample_rate) > std::numeric_limits<uint32_t>::max())
    return false;
  if (num_channels > std::numeric_limits<uint16_t>::max())
    return false;
  if (static_cast<uint64_t>(bytes_per_sample) * 8 >
      std::numeric_limits<uint16_t>::max())
    return false;
  if (static_cast<uint64_t>(sample_rate) * num_channels * bytes_per_sample >
      std::numeric_limits<uint32_t>::max())
    return false;

  // format and bytes_per_sample must agree.
  switch (format) {
    case kWavFormatPcm:
      // Other values may be OK, but for now we're conservative:
      if (bytes_per_sample != 1 && bytes_per_sample != 2)
        return false;
      break;
    case kWavFormatALaw:
    case kWavFormatMuLaw:
      if (bytes_per_sample != 1)
        return false;
      break;
    default:
      return false;
  }

  // The number of bytes in the file, not counting the first ChunkHeader, must
  // be less than 2^32; otherwise, the ChunkSize field overflows.
  const size_t header_size = kWavHeaderSize - sizeof(ChunkHeader);
  const size_t max_samples =
      (std::numeric_limits<uint32_t>::max() - header_size) / bytes_per_sample;
  if (num_samples > max_samples)
    return false;

  // Each channel must have the same number of samples.
  if (num_samples % num_channels != 0)
    return false;

  return true;
}

void WriteWavHeader(uint8_t* buf,
                    size_t num_channels,
                    int sample_rate,
                    WavFormat format,
                    size_t bytes_per_sample,
                    size_t num_samples) {
  RTC_CHECK(CheckWavParameters(num_channels, sample_rate, format,
                               bytes_per_sample, num_samples));

  auto header = rtc::MsanUninitialized<WavHeader>({});
  const size_t bytes_in_payload = bytes_per_sample * num_samples;

  WriteFourCC(&header.riff.header.ID, 'R', 'I', 'F', 'F');
  WriteLE32(&header.riff.header.Size, RiffChunkSize(bytes_in_payload));
  WriteFourCC(&header.riff.Format, 'W', 'A', 'V', 'E');

  WriteFourCC(&header.fmt.header.ID, 'f', 'm', 't', ' ');
  WriteLE32(&header.fmt.header.Size, kFmtSubchunkSize);
  WriteLE16(&header.fmt.AudioFormat, format);
  WriteLE16(&header.fmt.NumChannels, static_cast<uint16_t>(num_channels));
  WriteLE32(&header.fmt.SampleRate, sample_rate);
  WriteLE32(&header.fmt.ByteRate,
            ByteRate(num_channels, sample_rate, bytes_per_sample));
  WriteLE16(&header.fmt.BlockAlign, BlockAlign(num_channels, bytes_per_sample));
  WriteLE16(&header.fmt.BitsPerSample,
            static_cast<uint16_t>(8 * bytes_per_sample));

  WriteFourCC(&header.data.header.ID, 'd', 'a', 't', 'a');
  WriteLE32(&header.data.header.Size, static_cast<uint32_t>(bytes_in_payload));

  // Do an extra copy rather than writing everything to buf directly, since buf
  // might not be correctly aligned.
  memcpy(buf, &header, kWavHeaderSize);
}

bool ReadWavHeader(ReadableWav* readable,
                   size_t* num_channels,
                   int* sample_rate,
                   WavFormat* format,
                   size_t* bytes_per_sample,
                   size_t* num_samples) {
  auto header = rtc::MsanUninitialized<WavHeader>({});

  // Read RIFF chunk.
  if (readable->Read(&header.riff, sizeof(header.riff)) != sizeof(header.riff))
    return false;
  if (ReadFourCC(header.riff.header.ID) != "RIFF")
    return false;
  if (ReadFourCC(header.riff.Format) != "WAVE")
    return false;

  // Find "fmt " and "data" chunks. While the official Wave file specification
  // does not put requirements on the chunks order, it is uncommon to find the
  // "data" chunk before the "fmt " one. The code below fails if this is not the
  // case.
  FindWaveChunk(&header.fmt.header, readable, "fmt ");
  if (ReadFourCC(header.fmt.header.ID) != "fmt ") {
    RTC_LOG(LS_ERROR) << "Cannot find 'fmt ' chunk.";
    return false;
  }
  if (!ReadFmtChunkData(&header.fmt, readable)) {
    RTC_LOG(LS_ERROR) << "Cannot read 'fmt ' chunk.";
    return false;
  }
  if (readable->Eof()) {
    RTC_LOG(LS_ERROR) << "'fmt ' chunk placed after 'data' chunk.";
    return false;
  }
  FindWaveChunk(&header.data.header, readable, "data");
  if (ReadFourCC(header.data.header.ID) != "data") {
    RTC_LOG(LS_ERROR) << "Cannot find 'data' chunk.";
    return false;
  }

  // Parse needed fields.
  *format = static_cast<WavFormat>(ReadLE16(header.fmt.AudioFormat));
  *num_channels = ReadLE16(header.fmt.NumChannels);
  *sample_rate = ReadLE32(header.fmt.SampleRate);
  *bytes_per_sample = ReadLE16(header.fmt.BitsPerSample) / 8;
  const size_t bytes_in_payload = ReadLE32(header.data.header.Size);
  if (*bytes_per_sample == 0)
    return false;
  *num_samples = bytes_in_payload / *bytes_per_sample;

  if (ReadLE32(header.riff.header.Size) < RiffChunkSize(bytes_in_payload))
    return false;
  if (ReadLE32(header.fmt.ByteRate) !=
      ByteRate(*num_channels, *sample_rate, *bytes_per_sample))
    return false;
  if (ReadLE16(header.fmt.BlockAlign) !=
      BlockAlign(*num_channels, *bytes_per_sample))
    return false;

  return CheckWavParameters(*num_channels, *sample_rate, *format,
                            *bytes_per_sample, *num_samples);
}

}  // namespace webrtc
