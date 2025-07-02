#!/bin/bash

# Copyright (c) 2018 The WebRTC project authors. All Rights Reserved.
#
# Use of this source code is governed by a BSD-style license
# that can be found in the LICENSE file in the root of the source
# tree. An additional intellectual property rights grant can be found
# in the file PATENTS.  All contributing project authors may
# be found in the AUTHORS file in the root of the source tree.

if [ $# -ne 1 ]; then
    echo "Usage: run-instantiation-tests.sh ADB-DEVICE-ID"
    exit 1
fi

# Paths: update these based on your git checkout and gn output folder names.
WEBRTC_DIR=$HOME/src/webrtc/src
BUILD_DIR=$WEBRTC_DIR/out/Android_Release

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
$WEBRTC_DIR/build/android/test_runner.py gtest \
  --output-directory $BUILD_DIR \
  --suite modules_tests \
  --gtest_filter "*InstantiationTest*" \
  --gtest_also_run_disabled_tests \
  --shard-timeout $TIMEOUT \
  --runtime-deps-path ../empty-runtime-deps \
  --test-launcher-retry-limit 0 \
  --adb-path $ADB \
  --device $SERIAL \
  --verbose \
  --num-retries 0 \
  2>&1 | tee -a instantiation-tests.log
popd
