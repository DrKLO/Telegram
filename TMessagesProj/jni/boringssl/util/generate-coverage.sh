#!/bin/sh
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

set -xe

SRC=$PWD

BUILD=$(mktemp -d '/tmp/boringssl.XXXXXX')
BUILD_SRC=$(mktemp -d '/tmp/boringssl-src.XXXXXX')
LCOV=$(mktemp -d '/tmp/boringssl-lcov.XXXXXX')

if [ -n "$1" ]; then
  LCOV=$(readlink -f "$1")
  mkdir -p "$LCOV"
fi

cd "$BUILD"
cmake "$SRC" -GNinja -DGCOV=1
ninja

cp -r "$SRC/crypto" "$SRC/decrepit" "$SRC/include" "$SRC/ssl" "$SRC/tool" \
  "$BUILD_SRC"
cp -r "$BUILD"/* "$BUILD_SRC"
mkdir "$BUILD/callgrind/"

cd "$SRC"
go run "$SRC/util/all_tests.go" -build-dir "$BUILD" -callgrind -num-workers 16
util/generate-asm-lcov.py "$BUILD/callgrind" "$BUILD" > "$BUILD/asm.info"

go run "util/all_tests.go" -build-dir "$BUILD"

cd "$SRC/ssl/test/runner"
go test -shim-path "$BUILD/ssl/test/bssl_shim" -num-workers 1

cd "$LCOV"
lcov -c -d "$BUILD" -b "$BUILD" -o "$BUILD/lcov.info"
lcov -r "$BUILD/lcov.info" -o "$BUILD/filtered.info" "*_test.c" "*_test.cc" "*/third_party/googletest/*"
cat "$BUILD/filtered.info" "$BUILD/asm.info" > "$BUILD/final.info"
sed -i "s;$BUILD;$BUILD_SRC;g" "$BUILD/final.info"
sed -i "s;$SRC;$BUILD_SRC;g" "$BUILD/final.info"
genhtml -p "$BUILD_SRC" "$BUILD/final.info"

rm -rf "$BUILD"
rm -rf "$BUILD_SRC"

xdg-open index.html
