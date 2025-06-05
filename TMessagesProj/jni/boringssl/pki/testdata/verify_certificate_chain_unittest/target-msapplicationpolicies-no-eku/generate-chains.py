#!/usr/bin/env python3
# Copyright 2023 The Chromium Authors
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
"""Certificate chain where the target certificate contains an
MSApplicationPolicies extension that is marked as critical and
does not contain an extendedKeyUsage extension."""

import sys

sys.path += ['../..']

import gencerts

# Self-signed root certificate.
root = gencerts.create_self_signed_root_certificate('Root')

# Intermediate certificate.
intermediate = gencerts.create_intermediate_certificate('Intermediate', root)

# Target certificate (has unknown critical extension).
target = gencerts.create_end_entity_certificate('Target', intermediate)
target.get_extensions().add_property('1.3.6.1.4.1.311.21.10',
                                     'critical,DER:01:02:03:04')
target.get_extensions().remove_property('extendedKeyUsage')

chain = [target, intermediate, root]
gencerts.write_chain(__doc__, chain, 'chain.pem')
