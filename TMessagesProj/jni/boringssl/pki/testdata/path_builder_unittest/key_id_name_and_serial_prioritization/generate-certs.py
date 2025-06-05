#!/usr/bin/env python
# Copyright 2019 The Chromium Authors
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

"""
A chain with target using authorityKeyIdentifier:issuer and multiple
intermediates with different serial numbers and issuer names, for testing
path bulding prioritization.
"""

import sys
sys.path += ['../..']

import gencerts

DATE_A = '150101120000Z'
DATE_B = '150102120000Z'
DATE_C = '150103120000Z'
DATE_Z = '180101120000Z'

root = gencerts.create_self_signed_root_certificate('Root')
root.set_validity_range(DATE_A, DATE_Z)

root2 = gencerts.create_self_signed_root_certificate('Root2')
root2.set_validity_range(DATE_A, DATE_Z)

# Give the certs notBefore dates in reverse priority order so we can test that
# the issuer/serial key id didn't affect prioritization.
int_matching = gencerts.create_intermediate_certificate('Intermediate', root)
int_matching.set_validity_range(DATE_A, DATE_Z)

int_mismatch = gencerts.create_intermediate_certificate('Intermediate', root2)
int_mismatch.set_key(int_matching.get_key())
int_mismatch.set_validity_range(DATE_C, DATE_Z)

int_match_name_only = gencerts.create_intermediate_certificate(
    'Intermediate', root)
int_match_name_only.set_key(int_matching.get_key())
int_match_name_only.set_validity_range(DATE_B, DATE_Z)

section = int_matching.config.get_section('signing_ca_ext')
section.set_property('authorityKeyIdentifier', 'issuer:always')
target = gencerts.create_end_entity_certificate('Target', int_matching)
target.set_validity_range(DATE_A, DATE_Z)

gencerts.write_chain('The 1st root', [root], out_pem='root.pem')
gencerts.write_chain('The 2nd root', [root2], out_pem='root2.pem')

gencerts.write_chain(
    'Intermediate with matching issuer name & serial',
    [int_matching], out_pem='int_matching.pem')

gencerts.write_chain(
    'Intermediate with different issuer name & serial',
    [int_mismatch], out_pem='int_mismatch.pem')

gencerts.write_chain(
    'Intermediate with same issuer name & different serial',
    [int_match_name_only], out_pem='int_match_name_only.pem')

gencerts.write_chain('The target', [target], out_pem='target.pem')

