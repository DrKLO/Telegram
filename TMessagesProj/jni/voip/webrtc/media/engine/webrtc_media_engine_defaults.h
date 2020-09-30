/*
 *  Copyright (c) 2019 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef MEDIA_ENGINE_WEBRTC_MEDIA_ENGINE_DEFAULTS_H_
#define MEDIA_ENGINE_WEBRTC_MEDIA_ENGINE_DEFAULTS_H_

#include "media/engine/webrtc_media_engine.h"
#include "rtc_base/system/rtc_export.h"

namespace webrtc {

// Sets required but null dependencies with default factories.
RTC_EXPORT void SetMediaEngineDefaults(cricket::MediaEngineDependencies* deps);

}  // namespace webrtc

#endif  // MEDIA_ENGINE_WEBRTC_MEDIA_ENGINE_DEFAULTS_H_
