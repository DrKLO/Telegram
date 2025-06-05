#!/bin/bash
#
#  Copyright (c) 2019 The WebRTC project authors. All Rights Reserved.
#
#  Use of this source code is governed by a BSD-style license
#  that can be found in the LICENSE file in the root of the source
#  tree. An additional intellectual property rights grant can be found
#  in the file PATENTS.  All contributing project authors may
#  be found in the AUTHORS file in the root of the source tree.
#

# Aliases.
BIN=$1
TEST_RTC_EVENT_LOG=$2
INPUT_PCM_FILE=$3

# Check setup.
if [ ! -f $BIN ]; then
  echo "Cannot find neteq_rtpplay binary."
  exit 99
fi
if [ ! -f $TEST_RTC_EVENT_LOG ]; then
  echo "Cannot find RTC event log file."
  exit 99
fi
if [ ! -f $INPUT_PCM_FILE ]; then
  echo "Cannot find PCM file."
  exit 99
fi

# Defines.

TMP_DIR=$(mktemp -d /tmp/tmp_XXXXXXXXXX)
PASS=0
FAIL=1
TEST_SUITE_RESULT=$PASS

file_hash () {
  md5sum $1 | awk '{ print $1 }'
}

test_passed () {
  echo PASS
}

test_failed () {
  echo "FAIL: $1"
  TEST_SUITE_RESULT=$FAIL
}

test_file_checksums_match () {
  if [ ! -f $1 ] || [ ! -f $2 ]; then
    test_failed "Cannot compare hash values: file(s) not found."
    return
  fi
  HASH1=$(file_hash $1)
  HASH2=$(file_hash $2)
  if [ "$HASH1" = "$HASH2" ]; then
    test_passed
  else
    test_failed "$1 differs from $2"
  fi
}

test_file_exists () {
  if [ -f $1 ]; then
    test_passed
  else
    test_failed "$1 does not exist"
  fi
}

test_exit_code_0 () {
  if [ $1 -eq 0 ]; then
    test_passed
  else
    test_failed "$1 did not return 0"
  fi
}

test_exit_code_not_0 () {
  if [ $1 -eq 0 ]; then
    test_failed "$1 returned 0"
  else
    test_passed
  fi
}

# Generate test data.

# Case 1. Pre-existing way.
CASE1_WAV=$TMP_DIR/case1.wav
$BIN $TEST_RTC_EVENT_LOG $CASE1_WAV  \
    --replacement_audio_file $INPUT_PCM_FILE  \
    --textlog --pythonplot --matlabplot  \
    > $TMP_DIR/case1.stdout 2> /dev/null
CASE1_RETURN_CODE=$?
CASE1_TEXTLOG=$TMP_DIR/case1.wav.text_log.txt
CASE1_PYPLOT=$TMP_DIR/case1_wav.py
CASE1_MATPLOT=$TMP_DIR/case1_wav.m

# Case 2. No output files.
$BIN $TEST_RTC_EVENT_LOG --replacement_audio_file $INPUT_PCM_FILE  \
    > $TMP_DIR/case2.stdout 2> /dev/null
CASE2_RETURN_CODE=$?

# Case 3. No output audio file.

# Case 3.1 Without --output_files_base_name (won't run).
$BIN $TEST_RTC_EVENT_LOG  \
    --replacement_audio_file $INPUT_PCM_FILE  \
    --textlog --pythonplot --matlabplot  \
    &> /dev/null
CASE3_1_RETURN_CODE=$?

# Case 3.2 With --output_files_base_name (runs).
$BIN $TEST_RTC_EVENT_LOG  \
    --replacement_audio_file $INPUT_PCM_FILE  \
    --output_files_base_name $TMP_DIR/case3_2  \
    --textlog --pythonplot --matlabplot  \
    > $TMP_DIR/case3_2.stdout 2> /dev/null
CASE3_2_RETURN_CODE=$?
CASE3_2_TEXTLOG=$TMP_DIR/case3_2.text_log.txt
CASE3_2_PYPLOT=$TMP_DIR/case3_2.py
CASE3_2_MATPLOT=$TMP_DIR/case3_2.m

# Case 4. With output audio file and --output_files_base_name.
CASE4_WAV=$TMP_DIR/case4.wav
$BIN $TEST_RTC_EVENT_LOG $TMP_DIR/case4.wav \
    --replacement_audio_file $INPUT_PCM_FILE  \
    --output_files_base_name $TMP_DIR/case4  \
    --textlog --pythonplot --matlabplot  \
    > $TMP_DIR/case4.stdout 2> /dev/null
CASE4_RETURN_CODE=$?
CASE4_TEXTLOG=$TMP_DIR/case4.text_log.txt
CASE4_PYPLOT=$TMP_DIR/case4.py
CASE4_MATPLOT=$TMP_DIR/case4.m

# Tests.

echo Check exit codes
test_exit_code_0 $CASE1_RETURN_CODE
test_exit_code_0 $CASE2_RETURN_CODE
test_exit_code_not_0 $CASE3_1_RETURN_CODE
test_exit_code_0 $CASE3_2_RETURN_CODE
test_exit_code_0 $CASE4_RETURN_CODE

echo Check that the expected output files exist
test_file_exists $CASE1_TEXTLOG
test_file_exists $CASE3_2_TEXTLOG
test_file_exists $CASE4_TEXTLOG
test_file_exists $CASE1_PYPLOT
test_file_exists $CASE3_2_PYPLOT
test_file_exists $CASE4_PYPLOT
test_file_exists $CASE1_MATPLOT
test_file_exists $CASE3_2_MATPLOT
test_file_exists $CASE4_MATPLOT

echo Check that the same WAV file is produced
test_file_checksums_match $CASE1_WAV $CASE4_WAV

echo Check that the same text log is produced
test_file_checksums_match $CASE1_TEXTLOG $CASE3_2_TEXTLOG
test_file_checksums_match $CASE1_TEXTLOG $CASE4_TEXTLOG

echo Check that the same python plot scripts is produced
test_file_checksums_match $CASE1_PYPLOT $CASE3_2_PYPLOT
test_file_checksums_match $CASE1_PYPLOT $CASE4_PYPLOT

echo Check that the same matlab plot scripts is produced
test_file_checksums_match $CASE1_MATPLOT $CASE3_2_MATPLOT
test_file_checksums_match $CASE1_MATPLOT $CASE4_MATPLOT

# Clean up
rm -fr $TMP_DIR

if [ $TEST_SUITE_RESULT -eq $PASS ]; then
  echo All tests passed.
  exit 0
else
  echo One or more tests failed.
  exit 1
fi
