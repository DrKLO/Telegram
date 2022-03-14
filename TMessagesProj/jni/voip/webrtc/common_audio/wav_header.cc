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

#ifndef WEBRTC_ARCH_LITTLE_ENDIAN
#error "Code not working properly for big endian platforms."
#endif

#pragma pack(2)
struct ChunkHeader {
  uint32_t ID;
  uint32_t Size;
};
static_assert(sizeof(ChunkHeader) == 8, "ChunkHeader size");

#pragma pack(2)
struct RiffHeader {
  ChunkHeader header;
  uint32_t Format;
};
static_assert(sizeof(RiffHeader) == sizeof(ChunkHeader) + 4, "RiffHeader size");

// We can't nest this definition in WavHeader, because VS2013 gives an error
// on sizeof(WavHeader::fmt): "error C2070: 'unknown': illegal sizeof operand".
#pragma pack(2)
struct FmtPcmSubchunk {
  ChunkHeader header;
  uint16_t AudioFormat;
  uint16_t NumChannels;
  uint32_t SampleRate;
  uint32_t ByteRate;
  uint16_t BlockAlign;
  uint16_t BitsPerSample;
};
static_assert(sizeof(FmtPcmSubchunk) == 24, "FmtPcmSubchunk size");
const uint32_t kFmtPcmSubchunkSize =
    sizeof(FmtPcmSubchunk) - sizeof(ChunkHeader);

// Pack struct to avoid additional padding bytes.
#pragma pack(2)
struct FmtIeeeFloatSubchunk {
  ChunkHeader header;
  uint16_t AudioFormat;
  uint16_t NumChannels;
  uint32_t SampleRate;
  uint32_t ByteRate;
  uint16_t BlockAlign;
  uint16_t BitsPerSample;
  uint16_t ExtensionSize;
};
static_assert(sizeof(FmtIeeeFloatSubchunk) == 26, "FmtIeeeFloatSubchunk size");
const uint32_t kFmtIeeeFloatSubchunkSize =
    sizeof(FmtIeeeFloatSubchunk) - sizeof(ChunkHeader);

// Simple PCM wav header. It does not include chunks that are not essential to
// read audio samples.
#pragma pack(2)
struct WavHeaderPcm {
  WavHeaderPcm(const WavHeaderPcm&) = default;
  WavHeaderPcm& operator=(const WavHeaderPcm&) = default;
  RiffHeader riff;
  FmtPcmSubchunk fmt;
  struct {
    ChunkHeader header;
  } data;
};
static_assert(sizeof(WavHeaderPcm) == kPcmWavHeaderSize,
              "no padding in header");

// IEEE Float Wav header, includes extra chunks necessary for proper non-PCM
// WAV implementation.
#pragma pack(2)
struct WavHeaderIeeeFloat {
  WavHeaderIeeeFloat(const WavHeaderIeeeFloat&) = default;
  WavHeaderIeeeFloat& operator=(const WavHeaderIeeeFloat&) = default;
  RiffHeader riff;
  FmtIeeeFloatSubchunk fmt;
  struct {
    ChunkHeader header;
    uint32_t SampleLength;
  } fact;
  struct {
    ChunkHeader header;
  } data;
};
static_assert(sizeof(WavHeaderIeeeFloat) == kIeeeFloatWavHeaderSize,
              "no padding in header");

uint32_t PackFourCC(char a, char b, char c, char d) {
  uint32_t packed_value =
      static_cast<uint32_t>(a) | static_cast<uint32_t>(b) << 8 |
      static_cast<uint32_t>(c) << 16 | static_cast<uint32_t>(d) << 24;
  return packed_value;
}

std::string ReadFourCC(uint32_t x) {
  return std::string(reinterpret_cast<char*>(&x), 4);
}

uint16_t MapWavFormatToHeaderField(WavFormat format) {
  switch (format) {
    case WavFormat::kWavFormatPcm:
      return 1;
    case WavFormat::kWavFormatIeeeFloat:
      return 3;
    case WavFormat::kWavFormatALaw:
      return 6;
    case WavFormat::kWavFormatMuLaw:
      return 7;
  }
  RTC_CHECK_NOTREACHED();
}

WavFormat MapHeaderFieldToWavFormat(uint16_t format_header_value) {
  if (format_header_value == 1) {
    return WavFormat::kWavFormatPcm;
  }
  if (format_header_value == 3) {
    return WavFormat::kWavFormatIeeeFloat;
  }

  RTC_CHECK(false) << "Unsupported WAV format";
}

uint32_t RiffChunkSize(size_t bytes_in_payload, size_t header_size) {
  return static_cast<uint32_t>(bytes_in_payload + header_size -
                               sizeof(ChunkHeader));
}

uint32_t ByteRate(size_t num_channels,
                  int sample_rate,
                  size_t bytes_per_sample) {
  return static_cast<uint32_t>(num_channels * sample_rate * bytes_per_sample);
}

uint16_t BlockAlign(size_t num_channels, size_t bytes_per_sample) {
  return static_cast<uint16_t>(num_channels * bytes_per_sample);
}

// Finds a chunk having the sought ID. If found, then `readable` points to the
// first byte of the sought chunk data. If not found, the end of the file is
// reached.
bool FindWaveChunk(ChunkHeader* chunk_header,
                   WavHeaderReader* readable,
                   const std::string sought_chunk_id) {
  RTC_DCHECK_EQ(sought_chunk_id.size(), 4);
  while (true) {
    if (readable->Read(chunk_header, sizeof(*chunk_header)) !=
        sizeof(*chunk_header))
      return false;  // EOF.
    if (ReadFourCC(chunk_header->ID) == sought_chunk_id)
      return true;  // Sought chunk found.
    // Ignore current chunk by skipping its payload.
    if (!readable->SeekForward(chunk_header->Size))
      return false;  // EOF or error.
  }
}

bool ReadFmtChunkData(FmtPcmSubchunk* fmt_subchunk, WavHeaderReader* readable) {
  // Reads "fmt " chunk payload.
  if (readable->Read(&(fmt_subchunk->AudioFormat), kFmtPcmSubchunkSize) !=
      kFmtPcmSubchunkSize)
    return false;
  const uint32_t fmt_size = fmt_subchunk->header.Size;
  if (fmt_size != kFmtPcmSubchunkSize) {
    // There is an optional two-byte extension field permitted to be present
    // with PCM, but which must be zero.
    int16_t ext_size;
    if (kFmtPcmSubchunkSize + sizeof(ext_size) != fmt_size)
      return false;
    if (readable->Read(&ext_size, sizeof(ext_size)) != sizeof(ext_size))
      return false;
    if (ext_size != 0)
      return false;
  }
  return true;
}

void WritePcmWavHeader(size_t num_channels,
                       int sample_rate,
                       size_t bytes_per_sample,
                       size_t num_samples,
                       uint8_t* buf,
                       size_t* header_size) {
  RTC_CHECK(buf);
  RTC_CHECK(header_size);
  *header_size = kPcmWavHeaderSize;
  auto header = rtc::MsanUninitialized<WavHeaderPcm>({});
  const size_t bytes_in_payload = bytes_per_sample * num_samples;

  header.riff.header.ID = PackFourCC('R', 'I', 'F', 'F');
  header.riff.header.Size = RiffChunkSize(bytes_in_payload, *header_size);
  header.riff.Format = PackFourCC('W', 'A', 'V', 'E');
  header.fmt.header.ID = PackFourCC('f', 'm', 't', ' ');
  header.fmt.header.Size = kFmtPcmSubchunkSize;
  header.fmt.AudioFormat = MapWavFormatToHeaderField(WavFormat::kWavFormatPcm);
  header.fmt.NumChannels = static_cast<uint16_t>(num_channels);
  header.fmt.SampleRate = sample_rate;
  header.fmt.ByteRate = ByteRate(num_channels, sample_rate, bytes_per_sample);
  header.fmt.BlockAlign = BlockAlign(num_channels, bytes_per_sample);
  header.fmt.BitsPerSample = static_cast<uint16_t>(8 * bytes_per_sample);
  header.data.header.ID = PackFourCC('d', 'a', 't', 'a');
  header.data.header.Size = static_cast<uint32_t>(bytes_in_payload);

  // Do an extra copy rather than writing everything to buf directly, since buf
  // might not be correctly aligned.
  memcpy(buf, &header, *header_size);
}

void WriteIeeeFloatWavHeader(size_t num_channels,
                             int sample_rate,
                             size_t bytes_per_sample,
                             size_t num_samples,
                             uint8_t* buf,
                             size_t* header_size) {
  RTC_CHECK(buf);
  RTC_CHECK(header_size);
  *header_size = kIeeeFloatWavHeaderSize;
  auto header = rtc::MsanUninitialized<WavHeaderIeeeFloat>({});
  const size_t bytes_in_payload = bytes_per_sample * num_samples;

  header.riff.header.ID = PackFourCC('R', 'I', 'F', 'F');
  header.riff.header.Size = RiffChunkSize(bytes_in_payload, *header_size);
  header.riff.Format = PackFourCC('W', 'A', 'V', 'E');
  header.fmt.header.ID = PackFourCC('f', 'm', 't', ' ');
  header.fmt.header.Size = kFmtIeeeFloatSubchunkSize;
  header.fmt.AudioFormat =
      MapWavFormatToHeaderField(WavFormat::kWavFormatIeeeFloat);
  header.fmt.NumChannels = static_cast<uint16_t>(num_channels);
  header.fmt.SampleRate = sample_rate;
  header.fmt.ByteRate = ByteRate(num_channels, sample_rate, bytes_per_sample);
  header.fmt.BlockAlign = BlockAlign(num_channels, bytes_per_sample);
  header.fmt.BitsPerSample = static_cast<uint16_t>(8 * bytes_per_sample);
  header.fmt.ExtensionSize = 0;
  header.fact.header.ID = PackFourCC('f', 'a', 'c', 't');
  header.fact.header.Size = 4;
  header.fact.SampleLength = static_cast<uint32_t>(num_channels * num_samples);
  header.data.header.ID = PackFourCC('d', 'a', 't', 'a');
  header.data.header.Size = static_cast<uint32_t>(bytes_in_payload);

  // Do an extra copy rather than writing everything to buf directly, since buf
  // might not be correctly aligned.
  memcpy(buf, &header, *header_size);
}

// Returns the number of bytes per sample for the format.
size_t GetFormatBytesPerSample(WavFormat format) {
  switch (format) {
    case WavFormat::kWavFormatPcm:
      // Other values may be OK, but for now we're conservative.
      return 2;
    case WavFormat::kWavFormatALaw:
    case WavFormat::kWavFormatMuLaw:
      return 1;
    case WavFormat::kWavFormatIeeeFloat:
      return 4;
  }
  RTC_CHECK_NOTREACHED();
}

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
    case WavFormat::kWavFormatPcm:
      // Other values may be OK, but for now we're conservative:
      if (bytes_per_sample != 1 && bytes_per_sample != 2)
        return false;
      break;
    case WavFormat::kWavFormatALaw:
    case WavFormat::kWavFormatMuLaw:
      if (bytes_per_sample != 1)
        return false;
      break;
    case WavFormat::kWavFormatIeeeFloat:
      if (bytes_per_sample != 4)
        return false;
      break;
    default:
      return false;
  }

  // The number of bytes in the file, not counting the first ChunkHeader, must
  // be less than 2^32; otherwise, the ChunkSize field overflows.
  const size_t header_size = kPcmWavHeaderSize - sizeof(ChunkHeader);
  const size_t max_samples =
      (std::numeric_limits<uint32_t>::max() - header_size) / bytes_per_sample;
  if (num_samples > max_samples)
    return false;

  // Each channel must have the same number of samples.
  if (num_samples % num_channels != 0)
    return false;

  return true;
}

}  // namespace

bool CheckWavParameters(size_t num_channels,
                        int sample_rate,
                        WavFormat format,
                        size_t num_samples) {
  return CheckWavParameters(num_channels, sample_rate, format,
                            GetFormatBytesPerSample(format), num_samples);
}

void WriteWavHeader(size_t num_channels,
                    int sample_rate,
                    WavFormat format,
                    size_t num_samples,
                    uint8_t* buf,
                    size_t* header_size) {
  RTC_CHECK(buf);
  RTC_CHECK(header_size);

  const size_t bytes_per_sample = GetFormatBytesPerSample(format);
  RTC_CHECK(CheckWavParameters(num_channels, sample_rate, format,
                               bytes_per_sample, num_samples));
  if (format == WavFormat::kWavFormatPcm) {
    WritePcmWavHeader(num_channels, sample_rate, bytes_per_sample, num_samples,
                      buf, header_size);
  } else {
    RTC_CHECK_EQ(format, WavFormat::kWavFormatIeeeFloat);
    WriteIeeeFloatWavHeader(num_channels, sample_rate, bytes_per_sample,
                            num_samples, buf, header_size);
  }
}

bool ReadWavHeader(WavHeaderReader* readable,
                   size_t* num_channels,
                   int* sample_rate,
                   WavFormat* format,
                   size_t* bytes_per_sample,
                   size_t* num_samples,
                   int64_t* data_start_pos) {
  // Read using the PCM header, even though it might be float Wav file
  auto header = rtc::MsanUninitialized<WavHeaderPcm>({});

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
  if (!FindWaveChunk(&header.fmt.header, readable, "fmt ")) {
    RTC_LOG(LS_ERROR) << "Cannot find 'fmt ' chunk.";
    return false;
  }
  if (!ReadFmtChunkData(&header.fmt, readable)) {
    RTC_LOG(LS_ERROR) << "Cannot read 'fmt ' chunk.";
    return false;
  }
  if (!FindWaveChunk(&header.data.header, readable, "data")) {
    RTC_LOG(LS_ERROR) << "Cannot find 'data' chunk.";
    return false;
  }

  // Parse needed fields.
  *format = MapHeaderFieldToWavFormat(header.fmt.AudioFormat);
  *num_channels = header.fmt.NumChannels;
  *sample_rate = header.fmt.SampleRate;
  *bytes_per_sample = header.fmt.BitsPerSample / 8;
  const size_t bytes_in_payload = header.data.header.Size;
  if (*bytes_per_sample == 0)
    return false;
  *num_samples = bytes_in_payload / *bytes_per_sample;

  const size_t header_size = *format == WavFormat::kWavFormatPcm
                                 ? kPcmWavHeaderSize
                                 : kIeeeFloatWavHeaderSize;

  if (header.riff.header.Size < RiffChunkSize(bytes_in_payload, header_size))
    return false;
  if (header.fmt.ByteRate !=
      ByteRate(*num_channels, *sample_rate, *bytes_per_sample))
    return false;
  if (header.fmt.BlockAlign != BlockAlign(*num_channels, *bytes_per_sample))
    return false;

  if (!CheckWavParameters(*num_channels, *sample_rate, *format,
                          *bytes_per_sample, *num_samples)) {
    return false;
  }

  *data_start_pos = readable->GetPosition();
  return true;
}

}  // namespace webrtc
