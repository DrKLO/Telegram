#!/usr/bin/env python
# Copyright 2016 The Chromium Authors
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
A chain with four possible intermediates with different notBefore and notAfter
dates, for testing path bulding prioritization.
"""

import sys
sys.path += ['../..']

import gencerts

DATE_A = '150101120000Z'
DATE_B = '150102120000Z'
DATE_C = '180101120000Z'
DATE_D = '180102120000Z'


root = gencerts.create_self_signed_root_certificate('Root')
root.set_validity_range(DATE_A, DATE_D)

int_ac = gencerts.create_intermediate_certificate('Intermediate', root)
int_ac.set_validity_range(DATE_A, DATE_C)

int_ad = gencerts.create_intermediate_certificate('Intermediate', root)
int_ad.set_validity_range(DATE_A, DATE_D)
int_ad.set_key(int_ac.get_key())

int_bc = gencerts.create_intermediate_certificate('Intermediate', root)
int_bc.set_validity_range(DATE_B, DATE_C)
int_bc.set_key(int_ac.get_key())

int_bd = gencerts.create_intermediate_certificate('Intermediate', root)
int_bd.set_validity_range(DATE_B, DATE_D)
int_bd.set_key(int_ac.get_key())

target = gencerts.create_end_entity_certificate('Target', int_ac)
target.set_validity_range(DATE_A, DATE_D)


gencerts.write_chain('The root', [root], out_pem='root.pem')
gencerts.write_chain('Intermediate with validity range A..C',
                     [int_ac], out_pem='int_ac.pem')
gencerts.write_chain('Intermediate with validity range A..D',
                     [int_ad], out_pem='int_ad.pem')
gencerts.write_chain('Intermediate with validity range B..C',
                     [int_bc], out_pem='int_bc.pem')
gencerts.write_chain('Intermediate with validity range B..D',
                     [int_bd], out_pem='int_bd.pem')
gencerts.write_chain('The target', [target], out_pem='target.pem')

