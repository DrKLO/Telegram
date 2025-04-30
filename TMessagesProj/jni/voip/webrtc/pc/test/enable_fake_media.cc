/*
 *  Copyright 2023 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "pc/test/enable_fake_media.h"

#include <memory>
#include <utility>

#include "absl/base/nullability.h"
#include "api/environment/environment.h"
#include "api/peer_connection_interface.h"
#include "call/call.h"
#include "call/call_config.h"
#include "media/base/fake_media_engine.h"
#include "pc/media_factory.h"
#include "rtc_base/checks.h"

namespace webrtc {

using ::cricket::FakeMediaEngine;
using ::cricket::MediaEngineInterface;

void EnableFakeMedia(
    PeerConnectionFactoryDependencies& deps,
    absl::Nonnull<std::unique_ptr<FakeMediaEngine>> fake_media_engine) {
  class FakeMediaFactory : public MediaFactory {
   public:
    explicit FakeMediaFactory(
        absl::Nonnull<std::unique_ptr<FakeMediaEngine>> fake)
        : fake_(std::move(fake)) {}

    std::unique_ptr<Call> CreateCall(const CallConfig& config) override {
      return Call::Create(config);
    }

    std::unique_ptr<MediaEngineInterface> CreateMediaEngine(
        const Environment& /*env*/,
        PeerConnectionFactoryDependencies& /*dependencies*/) {
      RTC_CHECK(fake_ != nullptr)
          << "CreateMediaEngine can be called at most once.";
      return std::move(fake_);
    }

   private:
    absl::Nullable<std::unique_ptr<FakeMediaEngine>> fake_;
  };

  deps.media_factory =
      std::make_unique<FakeMediaFactory>(std::move(fake_media_engine));
}

void EnableFakeMedia(PeerConnectionFactoryDependencies& deps) {
  EnableFakeMedia(deps, std::make_unique<cricket::FakeMediaEngine>());
}

}  // namespace webrtc
