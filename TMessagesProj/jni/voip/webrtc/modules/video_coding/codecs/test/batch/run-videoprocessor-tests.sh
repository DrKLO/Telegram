#!/bin/bash

# Copyright (c) 2018 The WebRTC project authors. All Rights Reserved.
#
# Use of this source code is governed by a BSD-style license
# that can be found in the LICENSE file in the root of the source
# tree. An additional intellectual property rights grant can be found
# in the file PATENTS.  All contributing project authors may
# be found in the AUTHORS file in the root of the source tree.

if [ $# -ne 1 ]; then
    echo "Usage: run.sh ADB-DEVICE-ID"
    exit 1
fi

# Paths: update these based on your git checkout and gn output folder names.
WEBRTC_DIR=$HOME/src/webrtc/src
BUILD_DIR=$WEBRTC_DIR/out/Android_Release

# Clips: update these to encode/decode other content.
CLIPS=('Foreman')
RESOLUTIONS=('128x96' '160x120' '176x144' '320x240' '352x288')
FRAMERATES=(30)

# Other settings.
ADB=`which adb`
SERIAL=$1
TIMEOUT=7200

# Ensure we are using the latest version.
ninja -C $BUILD_DIR modules_tests

# Transfer the required files by trying to run a test that doesn't exist.
echo "===> Transferring required resources to device $1."
$WEBRTC_DIR/build/android/test_runner.py gtest \
  --output-directory $BUILD_DIR \
  --suite modules_tests \
  --gtest_filter "DoesNotExist" \
  --shard-timeout $TIMEOUT \
  --runtime-deps-path $BUILD_DIR/gen.runtime/modules/modules_tests__test_runner_script.runtime_deps \
  --adb-path $ADB \
  --device $SERIAL \
  --verbose

# Run all tests as separate test invocations.
mkdir $SERIAL
pushd $SERIAL
for clip in "${CLIPS[@]}"; do
  for resolution in "${RESOLUTIONS[@]}"; do
    for framerate in "${FRAMERATES[@]}"; do
      test_name="${clip}_${resolution}_${framerate}"
      log_name="${test_name}.log"

      echo "===> Running ${test_name} on device $1."

      $WEBRTC_DIR/build/android/test_runner.py gtest \
        --output-directory $BUILD_DIR \
        --suite modules_tests \
        --gtest_filter "CodecSettings/*${test_name}*" \
        --shard-timeout $TIMEOUT \
        --runtime-deps-path ../empty-runtime-deps \
        --test-launcher-retry-limit 0 \
        --adb-path $ADB \
        --device $SERIAL \
        --verbose \
        2>&1 | tee -a ${log_name}
    done
  done
done
popd
