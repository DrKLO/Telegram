#!/bin/sh
# Copyright (c) 2016, Google Inc.
#
# Permission to use, copy, modify, and/or distribute this software for any
# purpose with or without fee is hereby granted, provided that the above
# copyright notice and this permission notice appear in all copies.
#
# THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES
# WITH REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF
# MERCHANTABILITY AND FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY
# SPECIAL, DIRECT, INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES
# WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN ACTION
# OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF OR IN
# CONNECTION WITH THE USE OR PERFORMANCE OF THIS SOFTWARE.

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
