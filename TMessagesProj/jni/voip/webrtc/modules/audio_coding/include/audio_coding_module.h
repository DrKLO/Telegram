/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef MODULES_AUDIO_CODING_INCLUDE_AUDIO_CODING_MODULE_H_
#define MODULES_AUDIO_CODING_INCLUDE_AUDIO_CODING_MODULE_H_

#include <memory>
#include <string>
#include <utility>
#include <vector>

#include "absl/types/optional.h"
#include "api/audio_codecs/audio_decoder_factory.h"
#include "api/audio_codecs/audio_encoder.h"
#include "api/function_view.h"
#include "api/neteq/neteq.h"
#include "api/neteq/neteq_factory.h"
#include "modules/audio_coding/include/audio_coding_module_typedefs.h"
#include "system_wrappers/include/clock.h"

namespace webrtc {

// forward declarations
class AudioDecoder;
class AudioEncoder;
class AudioFrame;
struct RTPHeader;

// Callback class used for sending data ready to be packetized
class AudioPacketizationCallback {
 public:
  virtual ~AudioPacketizationCallback() {}

  virtual int32_t SendData(AudioFrameType frame_type,
                           uint8_t payload_type,
                           uint32_t timestamp,
                           const uint8_t* payload_data,
                           size_t payload_len_bytes,
                           int64_t absolute_capture_timestamp_ms) {
    // TODO(bugs.webrtc.org/10739): Deprecate the old SendData and make this one
    // pure virtual.
    return SendData(frame_type, payload_type, timestamp, payload_data,
                    payload_len_bytes);
  }
  virtual int32_t SendData(AudioFrameType frame_type,
                           uint8_t payload_type,
                           uint32_t timestamp,
                           const uint8_t* payload_data,
                           size_t payload_len_bytes) {
    RTC_NOTREACHED() << "This method must be overridden, or not used.";
    return -1;
  }
};

class AudioCodingModule {
 protected:
  AudioCodingModule() {}

 public:
  struct Config {
    explicit Config(
        rtc::scoped_refptr<AudioDecoderFactory> decoder_factory = nullptr);
    Config(const Config&);
    ~Config();

    NetEq::Config neteq_config;
    Clock* clock;
    rtc::scoped_refptr<AudioDecoderFactory> decoder_factory;
    NetEqFactory* neteq_factory = nullptr;
  };

  static AudioCodingModule* Create(const Config& config);
  virtual ~AudioCodingModule() = default;

  ///////////////////////////////////////////////////////////////////////////
  //   Sender
  //

  // |modifier| is called exactly once with one argument: a pointer to the
  // unique_ptr that holds the current encoder (which is null if there is no
  // current encoder). For the duration of the call, |modifier| has exclusive
  // access to the unique_ptr; it may call the encoder, steal the encoder and
  // replace it with another encoder or with nullptr, etc.
  virtual void ModifyEncoder(
      rtc::FunctionView<void(std::unique_ptr<AudioEncoder>*)> modifier) = 0;

  // Utility method for simply replacing the existing encoder with a new one.
  void SetEncoder(std::unique_ptr<AudioEncoder> new_encoder) {
    ModifyEncoder([&](std::unique_ptr<AudioEncoder>* encoder) {
      *encoder = std::move(new_encoder);
    });
  }

  // int32_t RegisterTransportCallback()
  // Register a transport callback which will be called to deliver
  // the encoded buffers whenever Process() is called and a
  // bit-stream is ready.
  //
  // Input:
  //   -transport          : pointer to the callback class
  //                         transport->SendData() is called whenever
  //                         Process() is called and bit-stream is ready
  //                         to deliver.
  //
  // Return value:
  //   -1 if the transport callback could not be registered
  //    0 if registration is successful.
  //
  virtual int32_t RegisterTransportCallback(
      AudioPacketizationCallback* transport) = 0;

  ///////////////////////////////////////////////////////////////////////////
  // int32_t Add10MsData()
  // Add 10MS of raw (PCM) audio data and encode it. If the sampling
  // frequency of the audio does not match the sampling frequency of the
  // current encoder ACM will resample the audio. If an encoded packet was
  // produced, it will be delivered via the callback object registered using
  // RegisterTransportCallback, and the return value from this function will
  // be the number of bytes encoded.
  //
  // Input:
  //   -audio_frame        : the input audio frame, containing raw audio
  //                         sampling frequency etc.
  //
  // Return value:
  //   >= 0   number of bytes encoded.
  //     -1   some error occurred.
  //
  virtual int32_t Add10MsData(const AudioFrame& audio_frame) = 0;

  ///////////////////////////////////////////////////////////////////////////
  // int SetPacketLossRate()
  // Sets expected packet loss rate for encoding. Some encoders provide packet
  // loss gnostic encoding to make stream less sensitive to packet losses,
  // through e.g., FEC. No effects on codecs that do not provide such encoding.
  //
  // Input:
  //   -packet_loss_rate   : expected packet loss rate (0 -- 100 inclusive).
  //
  // Return value
  //   -1 if failed to set packet loss rate,
  //   0 if succeeded.
  //
  // This is only used in test code that rely on old ACM APIs.
  // TODO(minyue): Remove it when possible.
  virtual int SetPacketLossRate(int packet_loss_rate) = 0;

  ///////////////////////////////////////////////////////////////////////////
  //   Receiver
  //

  ///////////////////////////////////////////////////////////////////////////
  // int32_t InitializeReceiver()
  // Any decoder-related state of ACM will be initialized to the
  // same state when ACM is created. This will not interrupt or
  // effect encoding functionality of ACM. ACM would lose all the
  // decoding-related settings by calling this function.
  // For instance, all registered codecs are deleted and have to be
  // registered again.
  //
  // Return value:
  //   -1 if failed to initialize,
  //    0 if succeeded.
  //
  virtual int32_t InitializeReceiver() = 0;

  // Replace any existing decoders with the given payload type -> decoder map.
  virtual void SetReceiveCodecs(
      const std::map<int, SdpAudioFormat>& codecs) = 0;

  ///////////////////////////////////////////////////////////////////////////
  // int32_t IncomingPacket()
  // Call this function to insert a parsed RTP packet into ACM.
  //
  // Inputs:
  //   -incoming_payload   : received payload.
  //   -payload_len_bytes  : the length of payload in bytes.
  //   -rtp_info           : the relevant information retrieved from RTP
  //                         header.
  //
  // Return value:
  //   -1 if failed to push in the payload
  //    0 if payload is successfully pushed in.
  //
  virtual int32_t IncomingPacket(const uint8_t* incoming_payload,
                                 const size_t payload_len_bytes,
                                 const RTPHeader& rtp_header) = 0;

  ///////////////////////////////////////////////////////////////////////////
  // int32_t PlayoutData10Ms(
  // Get 10 milliseconds of raw audio data for playout, at the given sampling
  // frequency. ACM will perform a resampling if required.
  //
  // Input:
  //   -desired_freq_hz    : the desired sampling frequency, in Hertz, of the
  //                         output audio. If set to -1, the function returns
  //                         the audio at the current sampling frequency.
  //
  // Output:
  //   -audio_frame        : output audio frame which contains raw audio data
  //                         and other relevant parameters.
  //   -muted              : if true, the sample data in audio_frame is not
  //                         populated, and must be interpreted as all zero.
  //
  // Return value:
  //   -1 if the function fails,
  //    0 if the function succeeds.
  //
  virtual int32_t PlayoutData10Ms(int32_t desired_freq_hz,
                                  AudioFrame* audio_frame,
                                  bool* muted) = 0;

  ///////////////////////////////////////////////////////////////////////////
  //   statistics
  //

  ///////////////////////////////////////////////////////////////////////////
  // int32_t  GetNetworkStatistics()
  // Get network statistics. Note that the internal statistics of NetEq are
  // reset by this call.
  //
  // Input:
  //   -network_statistics : a structure that contains network statistics.
  //
  // Return value:
  //   -1 if failed to set the network statistics,
  //    0 if statistics are set successfully.
  //
  virtual int32_t GetNetworkStatistics(
      NetworkStatistics* network_statistics) = 0;

  virtual ANAStats GetANAStats() const = 0;
};

}  // namespace webrtc

#endif  // MODULES_AUDIO_CODING_INCLUDE_AUDIO_CODING_MODULE_H_
