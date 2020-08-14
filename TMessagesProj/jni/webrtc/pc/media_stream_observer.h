/*
 *  Copyright 2015 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef PC_MEDIA_STREAM_OBSERVER_H_
#define PC_MEDIA_STREAM_OBSERVER_H_

#include "api/media_stream_interface.h"
#include "api/scoped_refptr.h"
#include "rtc_base/third_party/sigslot/sigslot.h"

namespace webrtc {

// Helper class which will listen for changes to a stream and emit the
// corresponding signals.
class MediaStreamObserver : public ObserverInterface {
 public:
  explicit MediaStreamObserver(MediaStreamInterface* stream);
  ~MediaStreamObserver() override;

  const MediaStreamInterface* stream() const { return stream_; }

  void OnChanged() override;

  sigslot::signal2<AudioTrackInterface*, MediaStreamInterface*>
      SignalAudioTrackAdded;
  sigslot::signal2<AudioTrackInterface*, MediaStreamInterface*>
      SignalAudioTrackRemoved;
  sigslot::signal2<VideoTrackInterface*, MediaStreamInterface*>
      SignalVideoTrackAdded;
  sigslot::signal2<VideoTrackInterface*, MediaStreamInterface*>
      SignalVideoTrackRemoved;

 private:
  rtc::scoped_refptr<MediaStreamInterface> stream_;
  AudioTrackVector cached_audio_tracks_;
  VideoTrackVector cached_video_tracks_;
};

}  // namespace webrtc

#endif  // PC_MEDIA_STREAM_OBSERVER_H_
