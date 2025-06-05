#!/usr/bin/env python
# Copyright 2018 The Chromium Authors
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

"""Certificate chain where both the intermediate and target certificates have
incorrect signatures."""

import sys
sys.path += ['../..']

import gencerts

# Self-signed root certificate.
root = gencerts.create_self_signed_root_certificate('Root')

# Actual root that was used to sign the intermediate certificate. It has the
# same subject as expected, but a different RSA key from the certificate
# included in the actual chain.
wrong_root = gencerts.create_self_signed_root_certificate('Root')

# Intermediate certificate to include in the certificate chain.
intermediate = gencerts.create_intermediate_certificate('Intermediate',
                                                        wrong_root)

# Actual intermediate that was used to sign the target certificate. It has the
# same subject as expected, but a different RSA key from the certificate
# included in the actual chain.
wrong_intermediate = gencerts.create_intermediate_certificate('Intermediate',
                                                              root)

# Target certificate, signed using |wrong_intermediate| NOT |intermediate|.
target = gencerts.create_end_entity_certificate('Target', wrong_intermediate)

chain = [target, intermediate, root]
gencerts.write_chain(__doc__, chain, 'chain.pem')
