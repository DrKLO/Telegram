# Copyright (c) 2018, Google Inc.
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
# CONNECTION WITH THE USE OR PERFORMANCE OF THIS SOFTWARE. */

# This script exists to exercise breaking each of the FIPS tests. It builds
# BoringSSL differently for each test and that can take a long time. Thus it's
# run twice: once, from a BoringSSL source tree, with "build" as the sole
# argument to run the builds, and then (from the same location) with no
# arguments to run each script.
#
# Run it with /bin/bash, not /bin/sh, otherwise "read" may fail.

set -x

TESTS="NONE ECDSA_PWCT CRNG RSA_PWCT AES_CBC AES_GCM DES SHA_1 SHA_256 SHA_512 RSA_SIG DRBG ECDSA_SIG"

if [ "x$1" = "xbuild" ]; then
	for test in $TESTS; do
		rm -Rf build-$test
		mkdir build-$test
		pushd build-$test
		cmake -GNinja -DCMAKE_TOOLCHAIN_FILE=${HOME}/toolchain -DFIPS=1 -DFIPS_BREAK_TEST=${test} -DCMAKE_BUILD_TYPE=Release ..
		ninja test_fips
		popd
	done

	exit 0
fi

for test in $TESTS; do
	pushd build-$test
	printf "\n\n\\x1b[1m$test\\x1b[0m\n"
	./util/fipstools/cavp/test_fips
	echo "Waiting for keypress..."
	read
	popd
done

pushd build-NONE
printf "\\x1b[1mIntegrity\\x1b[0m\n"
go run ../util/fipstools/break-hash.go ./util/fipstools/cavp/test_fips ./util/fipstools/cavp/test_fips_broken
./util/fipstools/cavp/test_fips_broken
popd
