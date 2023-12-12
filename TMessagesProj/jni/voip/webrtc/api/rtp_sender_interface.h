/*
 *  Copyright 2015 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

// This file contains interfaces for RtpSenders
// http://w3c.github.io/webrtc-pc/#rtcrtpsender-interface

#ifndef API_RTP_SENDER_INTERFACE_H_
#define API_RTP_SENDER_INTERFACE_H_

#include <memory>
#include <string>
#include <vector>

#include "api/crypto/frame_encryptor_interface.h"
#include "api/dtls_transport_interface.h"
#include "api/dtmf_sender_interface.h"
#include "api/frame_transformer_interface.h"
#include "api/media_stream_interface.h"
#include "api/media_types.h"
#include "api/rtc_error.h"
#include "api/rtp_parameters.h"
#include "api/scoped_refptr.h"
#include "api/video_codecs/video_encoder_factory.h"
#include "rtc_base/ref_count.h"
#include "rtc_base/system/rtc_export.h"

namespace webrtc {

class RTC_EXPORT RtpSenderInterface : public rtc::RefCountInterface {
 public:
  // Returns true if successful in setting the track.
  // Fails if an audio track is set on a video RtpSender, or vice-versa.
  virtual bool SetTrack(MediaStreamTrackInterface* track) = 0;
  virtual rtc::scoped_refptr<MediaStreamTrackInterface> track() const = 0;

  // The dtlsTransport attribute exposes the DTLS transport on which the
  // media is sent. It may be null.
  // https://w3c.github.io/webrtc-pc/#dom-rtcrtpsender-transport
  virtual rtc::scoped_refptr<DtlsTransportInterface> dtls_transport() const = 0;

  // Returns primary SSRC used by this sender for sending media.
  // Returns 0 if not yet determined.
  // TODO(deadbeef): Change to absl::optional.
  // TODO(deadbeef): Remove? With GetParameters this should be redundant.
  virtual uint32_t ssrc() const = 0;

  // Audio or video sender?
  virtual cricket::MediaType media_type() const = 0;

  // Not to be confused with "mid", this is a field we can temporarily use
  // to uniquely identify a receiver until we implement Unified Plan SDP.
  virtual std::string id() const = 0;

  // Returns a list of media stream ids associated with this sender's track.
  // These are signalled in the SDP so that the remote side can associate
  // tracks.
  virtual std::vector<std::string> stream_ids() const = 0;

  // Sets the IDs of the media streams associated with this sender's track.
  // These are signalled in the SDP so that the remote side can associate
  // tracks.
  virtual void SetStreams(const std::vector<std::string>& stream_ids) = 0;

  // Returns the list of encoding parameters that will be applied when the SDP
  // local description is set. These initial encoding parameters can be set by
  // PeerConnection::AddTransceiver, and later updated with Get/SetParameters.
  // TODO(orphis): Make it pure virtual once Chrome has updated
  virtual std::vector<RtpEncodingParameters> init_send_encodings() const = 0;

  virtual RtpParameters GetParameters() const = 0;
  // Note that only a subset of the parameters can currently be changed. See
  // rtpparameters.h
  // The encodings are in increasing quality order for simulcast.
  virtual RTCError SetParameters(const RtpParameters& parameters) = 0;

  // Returns null for a video sender.
  virtual rtc::scoped_refptr<DtmfSenderInterface> GetDtmfSender() const = 0;

  // Sets a user defined frame encryptor that will encrypt the entire frame
  // before it is sent across the network. This will encrypt the entire frame
  // using the user provided encryption mechanism regardless of whether SRTP is
  // enabled or not.
  virtual void SetFrameEncryptor(
      rtc::scoped_refptr<FrameEncryptorInterface> frame_encryptor) = 0;

  // Returns a pointer to the frame encryptor set previously by the
  // user. This can be used to update the state of the object.
  virtual rtc::scoped_refptr<FrameEncryptorInterface> GetFrameEncryptor()
      const = 0;

  virtual void SetEncoderToPacketizerFrameTransformer(
      rtc::scoped_refptr<FrameTransformerInterface> frame_transformer) = 0;

  // Sets a user defined encoder selector.
  // Overrides selector that is (optionally) provided by VideoEncoderFactory.
  virtual void SetEncoderSelector(
      std::unique_ptr<VideoEncoderFactory::EncoderSelectorInterface>
          encoder_selector) = 0;

  // TODO(crbug.com/1354101): make pure virtual again after Chrome roll.
  virtual RTCError GenerateKeyFrame() { return RTCError::OK(); }

 protected:
  ~RtpSenderInterface() override = default;
};

}  // namespace webrtc

#endif  // API_RTP_SENDER_INTERFACE_H_
