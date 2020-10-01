/*
 *  Copyright 2015 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

// This file contains classes that implement RtpReceiverInterface.
// An RtpReceiver associates a MediaStreamTrackInterface with an underlying
// transport (provided by cricket::VoiceChannel/cricket::VideoChannel)

#ifndef PC_RTP_RECEIVER_H_
#define PC_RTP_RECEIVER_H_

#include <stdint.h>

#include <string>
#include <vector>

#include "absl/types/optional.h"
#include "api/crypto/frame_decryptor_interface.h"
#include "api/media_stream_interface.h"
#include "api/media_types.h"
#include "api/rtp_parameters.h"
#include "api/rtp_receiver_interface.h"
#include "api/scoped_refptr.h"
#include "api/video/video_frame.h"
#include "api/video/video_sink_interface.h"
#include "api/video/video_source_interface.h"
#include "media/base/media_channel.h"
#include "media/base/video_broadcaster.h"
#include "pc/video_track_source.h"
#include "rtc_base/ref_counted_object.h"
#include "rtc_base/thread.h"

namespace webrtc {

// Internal class used by PeerConnection.
class RtpReceiverInternal : public RtpReceiverInterface {
 public:
  virtual void Stop() = 0;

  // Sets the underlying MediaEngine channel associated with this RtpSender.
  // A VoiceMediaChannel should be used for audio RtpSenders and
  // a VideoMediaChannel should be used for video RtpSenders.
  // Must call SetMediaChannel(nullptr) before the media channel is destroyed.
  virtual void SetMediaChannel(cricket::MediaChannel* media_channel) = 0;

  // Configures the RtpReceiver with the underlying media channel, with the
  // given SSRC as the stream identifier.
  virtual void SetupMediaChannel(uint32_t ssrc) = 0;

  // Configures the RtpReceiver with the underlying media channel to receive an
  // unsignaled receive stream.
  virtual void SetupUnsignaledMediaChannel() = 0;

  virtual void set_transport(
      rtc::scoped_refptr<DtlsTransportInterface> dtls_transport) = 0;
  // This SSRC is used as an identifier for the receiver between the API layer
  // and the WebRtcVideoEngine, WebRtcVoiceEngine layer.
  virtual uint32_t ssrc() const = 0;

  // Call this to notify the RtpReceiver when the first packet has been received
  // on the corresponding channel.
  virtual void NotifyFirstPacketReceived() = 0;

  // Set the associated remote media streams for this receiver. The remote track
  // will be removed from any streams that are no longer present and added to
  // any new streams.
  virtual void set_stream_ids(std::vector<std::string> stream_ids) = 0;
  // TODO(https://crbug.com/webrtc/9480): Remove SetStreams() in favor of
  // set_stream_ids() as soon as downstream projects are no longer dependent on
  // stream objects.
  virtual void SetStreams(
      const std::vector<rtc::scoped_refptr<MediaStreamInterface>>& streams) = 0;

  // Returns an ID that changes if the attached track changes, but
  // otherwise remains constant. Used to generate IDs for stats.
  // The special value zero means that no track is attached.
  virtual int AttachmentId() const = 0;

 protected:
  static int GenerateUniqueId();

  static std::vector<rtc::scoped_refptr<MediaStreamInterface>>
  CreateStreamsFromIds(std::vector<std::string> stream_ids);

  static void MaybeAttachFrameDecryptorToMediaChannel(
      const absl::optional<uint32_t>& ssrc,
      rtc::Thread* worker_thread,
      rtc::scoped_refptr<webrtc::FrameDecryptorInterface> frame_decryptor,
      cricket::MediaChannel* media_channel,
      bool stopped);
};

}  // namespace webrtc

#endif  // PC_RTP_RECEIVER_H_
