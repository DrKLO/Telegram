#!/usr/bin/env python
# Copyright 2017 The Chromium Authors
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

"""Generates a variety of chains where the target certificate varies in its key
type and key usages."""

import sys
sys.path += ['../..']

import gencerts

# Self-signed root certificate (used as trust anchor).
root = gencerts.create_self_signed_root_certificate('Root')

# Intermediate certificate.
intermediate = gencerts.create_intermediate_certificate('Intermediate', root)

# Use either an RSA key, or an EC key for the target certificate. Generate the
# possible keys ahead of time so as not to duplicate the work.

KEYS = {
  'rsa': gencerts.get_or_generate_rsa_key(
      2048, gencerts.create_key_path('Target-rsa')),
  'ec': gencerts.get_or_generate_ec_key(
      'secp384r1', gencerts.create_key_path('Target-ec'))
};

KEY_USAGES = [ 'decipherOnly',
               'digitalSignature',
               'keyAgreement',
               'keyEncipherment' ]

# The proper key usage depends on the key purpose (serverAuth in this case),
# and the key type. Generate a variety of combinations.
for key_type in sorted(KEYS.keys()):
  for key_usage in KEY_USAGES:
    # Target certificate.
    target = gencerts.create_end_entity_certificate('Target', intermediate)
    target.get_extensions().set_property('extendedKeyUsage', 'serverAuth')
    target.get_extensions().set_property('keyUsage',
                                         'critical,%s' % (key_usage))

    # Set the key.
    target.set_key(KEYS[key_type])

    # Write the chain.
    chain = [target, intermediate, root]
    description = ('Certificate chain where the target certificate uses a %s '
                   'key and has the single key usage %s') % (key_type.upper(),
                                                             key_usage)
    gencerts.write_chain(description, chain,
                         '%s-%s.pem' % (key_type, key_usage))
