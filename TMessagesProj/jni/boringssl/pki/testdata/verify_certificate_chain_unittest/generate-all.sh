#!/bin/bash

# Copyright 2015 The Chromium Authors
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

set -e

# As generate-chains.py calls out to the openssl command under the hood
# this is suboptimal if you don't have openssl installed. We should
# replace generate-chains.py
# TODO(bbe): crbug.com/402461221

for dir in */ ; do
  cd "$dir"

  if [ -f generate-chains.py ]; then
    python3 generate-chains.py

    # Cleanup temporary files.
    rm -rf */*.pyc
    rm -rf out/
  fi

  cd ..
done
