/*
 *  Copyright (c) 2020 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef MODULES_AUDIO_PROCESSING_OPTIONALLY_BUILT_SUBMODULE_CREATORS_H_
#define MODULES_AUDIO_PROCESSING_OPTIONALLY_BUILT_SUBMODULE_CREATORS_H_

#include <memory>

#include "modules/audio_processing/transient/transient_suppressor.h"

namespace webrtc {

// These overrides are only to be used for testing purposes.
// Each flag emulates a preprocessor macro to exclude a submodule of APM from
// the build, e.g. WEBRTC_EXCLUDE_TRANSIENT_SUPPRESSOR. If the corresponding
// flag `transient_suppression` is enabled, then the creators will return
// nullptr instead of a submodule instance, as if the macro had been defined.
struct ApmSubmoduleCreationOverrides {
  bool transient_suppression = false;
};

// Creates a transient suppressor.
// Will instead return nullptr if one of the following is true:
// * WEBRTC_EXCLUDE_TRANSIENT_SUPPRESSOR is defined
// * The corresponding override in `overrides` is enabled.
std::unique_ptr<TransientSuppressor> CreateTransientSuppressor(
    const ApmSubmoduleCreationOverrides& overrides);

}  // namespace webrtc

#endif  // MODULES_AUDIO_PROCESSING_OPTIONALLY_BUILT_SUBMODULE_CREATORS_H_
