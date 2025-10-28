#!/usr/bin/env python
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

"""Certificate chain where the root certificate holds an RSA key, intermediate
certificate holds an EC key, and target certificate holds an RSA key. The
target certificate has a valid signature using ECDSA."""

import sys
sys.path += ['../..']

import gencerts

# Self-signed root certificate using an RSA key.
root = gencerts.create_self_signed_root_certificate('Root')

# Intermediate using an EC key for the P-384 curve.
intermediate = gencerts.create_intermediate_certificate('Intermediate', root)
intermediate.set_key(gencerts.get_or_generate_ec_key(
    'secp384r1', gencerts.create_key_path(intermediate.name)))

# Target certificate contains an RSA key (but is signed using ECDSA).
target = gencerts.create_end_entity_certificate('Target', intermediate)

chain = [target, intermediate, root]
gencerts.write_chain(__doc__, chain, 'chain.pem')
