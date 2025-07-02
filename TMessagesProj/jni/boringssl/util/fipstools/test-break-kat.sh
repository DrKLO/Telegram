# Copyright 2022 The BoringSSL Authors
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

# This script attempts to break each of the known KATs and checks that doing so
# seems to work and at least mentions the correct KAT in the output.

set -x
set -e

TEST_FIPS_BIN="build/util/fipstools/test_fips"

if [ ! -f $TEST_FIPS_BIN ]; then
  echo "$TEST_FIPS_BIN is missing. Run this script from the top level of a"
  echo "BoringSSL checkout and ensure that ./build-fips-break-test-binaries.sh"
  echo "has been run first."
  exit 1
fi

KATS=$(go run util/fipstools/break-kat.go --list-tests)

for kat in $KATS; do
  go run util/fipstools/break-kat.go $TEST_FIPS_BIN $kat > break-kat-bin
  chmod u+x ./break-kat-bin
  if ! (./break-kat-bin 2>&1 >/dev/null || true) | \
       egrep -q "^$kat[^a-zA-Z0-9]"; then
    echo "Failure for $kat did not mention that name in the output"
    exit 1
  fi
  rm ./break-kat-bin
done
