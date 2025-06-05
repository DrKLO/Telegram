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

"""Certificate chain where the supposed root certificate is wrong:

  * The intermediate's "issuer" does not match the root's "subject"
  * The intermediate's signature was not generated using the root's key
"""

import sys
sys.path += ['../..']

import gencerts

# Self-signed root certificate, which actually signed the intermediate.
root = gencerts.create_self_signed_root_certificate('Root')

# Intermediate certificate.
intermediate = gencerts.create_intermediate_certificate('Intermediate', root)

# Target certificate.
target = gencerts.create_end_entity_certificate('Target', intermediate)

# Self-signed root certificate that has nothing to do with this chain, but will
# be saved as its root certificate.
bogus_root = gencerts.create_self_signed_root_certificate('BogusRoot')

chain = [target, intermediate, bogus_root]
gencerts.write_chain(__doc__, chain, 'chain.pem')
