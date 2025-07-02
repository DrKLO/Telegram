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

"""Certificate chain where the intermediate has a policies extension marked as
critical, and contains an unknown policy qualifer (1.2.3.4)."""

import sys
sys.path += ['../..']

import gencerts

# Self-signed root certificate.
root = gencerts.create_self_signed_root_certificate('Root')

# Intermediate that has a critical policies extension containing an unknown
# policy qualifer.
intermediate = gencerts.create_intermediate_certificate('Intermediate', root)
intermediate.get_extensions().add_property(
    '2.5.29.32', ('critical,DER:30:13:30:11:06:02:2a:03:30:0b:30:09:06:03:'
                  '2a:03:04:0c:02:68:69'))

# Target certificate.
target = gencerts.create_end_entity_certificate('Target', intermediate)

chain = [target, intermediate, root]
gencerts.write_chain(__doc__, chain, 'chain.pem')
