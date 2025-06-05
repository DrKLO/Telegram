#!/bin/bash
# Copyright 2016 The BoringSSL Authors
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     https://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

set -ex

if [[ $# -ne 1 ]]; then
  echo "Usage: $0 build_dir"
  exit 1
fi

build_dir=$1


# Sanity-check the build directory.

if ! grep -q '^FUZZ:' "$build_dir/CMakeCache.txt"; then
  echo "$build_dir was not built with -DFUZZ=1"
  exit 1
fi

# Sanity-check the current working directory.

assert_directory() {
  if [[ ! -d $1 ]]; then
    echo "$1 not found."
    exit 1
  fi
}

assert_directory client_corpus
assert_directory client_no_fuzzer_mode_corpus
assert_directory server_corpus
assert_directory server_no_fuzzer_mode_corpus
assert_directory dtls_client_corpus
assert_directory dtls_server_corpus


# Gather new transcripts. Ignore errors in running the tests.

shim="$(readlink -f "$build_dir")/ssl/test/bssl_shim"
handshaker="$(readlink -f "$build_dir")/ssl/test/handshaker"

fuzzer_mode_transcripts=$(mktemp -d '/tmp/boringssl-transcript-fuzzer-mode.XXXXXX')
no_fuzzer_mode_transcripts=$(mktemp -d '/tmp/boringssl-transcript-no-fuzzer-mode.XXXXXX')

echo Recording fuzzer-mode transcripts
(cd ../ssl/test/runner/ && go test \
    -shim-path "$shim" \
    -handshaker-path "$handshaker" \
    -transcript-dir "$fuzzer_mode_transcripts" \
    -fuzzer \
    -deterministic) || true

echo Recording non-fuzzer-mode transcripts
(cd ../ssl/test/runner/ && go test \
    -shim-path "$shim" \
    -handshaker-path "$handshaker" \
    -transcript-dir "$no_fuzzer_mode_transcripts" \
    -deterministic)


# Update corpora.

update_corpus() {
  local fuzzer_name="$1"
  local transcript_dir="$2"

  local fuzzer="$build_dir/fuzz/$fuzzer_name"
  local corpus="${fuzzer_name}_corpus"

  echo "Minimizing ${corpus}"
  mv "$corpus" "${corpus}_old"
  mkdir "$corpus"
  "$fuzzer" -max_len=50000 -merge=1 "$corpus" "${corpus}_old"
  rm -Rf "${corpus}_old"

  echo "Merging transcripts from ${transcript_dir} into ${corpus}"
  "$fuzzer" -max_len=50000 -merge=1 "$corpus" "$transcript_dir"
}

update_corpus client "${fuzzer_mode_transcripts}/tls/client"
update_corpus server "${fuzzer_mode_transcripts}/tls/server"
update_corpus client_no_fuzzer_mode "${no_fuzzer_mode_transcripts}/tls/client"
update_corpus server_no_fuzzer_mode "${no_fuzzer_mode_transcripts}/tls/server"
update_corpus dtls_client "${fuzzer_mode_transcripts}/dtls/client"
update_corpus dtls_server "${fuzzer_mode_transcripts}/dtls/server"
update_corpus decode_client_hello_inner "${fuzzer_mode_transcripts}/decode_client_hello_inner"
