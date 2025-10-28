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

"""A certificate tree with two self-signed root certificates(oldroot, newroot),
and a third root certificate (newrootrollover) which has the same key as newroot
but is signed by oldroot, all with the same subject and issuer.
There are two intermediates with the same key, subject and issuer
(oldintermediate signed by oldroot, and newintermediate signed by newroot).
The target certificate is signed by the intermediate key.


In graphical form:

   oldroot-------->newrootrollover  newroot
      |                      |        |
      v                      v        v
oldintermediate           newintermediate
      |                          |
      +------------+-------------+
                   |
                   v
                 target


Several chains are output:
  key-rollover-oldchain.pem:
    target<-oldintermediate<-oldroot
  key-rollover-rolloverchain.pem:
    target<-newintermediate<-newrootrollover<-oldroot
  key-rollover-longrolloverchain.pem:
    target<-newintermediate<-newroot<-newrootrollover<-oldroot
  key-rollover-newchain.pem:
    target<-newintermediate<-newroot

All of these chains should verify successfully.
"""

import sys
sys.path += ['../..']

import gencerts

# The new certs should have a newer notbefore date than "old" certs. This should
# affect path builder sorting, but otherwise won't matter.
JANUARY_2_2015_UTC = '150102120000Z'

# Self-signed root certificates. Same name, different keys.
oldroot = gencerts.create_self_signed_root_certificate('Root')
oldroot.set_validity_range(gencerts.JANUARY_1_2015_UTC,
                           gencerts.JANUARY_1_2016_UTC)
newroot = gencerts.create_self_signed_root_certificate('Root')
newroot.set_validity_range(JANUARY_2_2015_UTC, gencerts.JANUARY_1_2016_UTC)
# Root with the new key signed by the old key.
newrootrollover = gencerts.create_intermediate_certificate('Root', oldroot)
newrootrollover.set_key(newroot.get_key())
newrootrollover.set_validity_range(JANUARY_2_2015_UTC,
                                   gencerts.JANUARY_1_2016_UTC)

# Intermediate signed by oldroot.
oldintermediate = gencerts.create_intermediate_certificate('Intermediate',
                                                         oldroot)
oldintermediate.set_validity_range(gencerts.JANUARY_1_2015_UTC,
                                   gencerts.JANUARY_1_2016_UTC)
# Intermediate signed by newroot. Same key as oldintermediate.
newintermediate = gencerts.create_intermediate_certificate('Intermediate',
                                                         newroot)
newintermediate.set_key(oldintermediate.get_key())
newintermediate.set_validity_range(JANUARY_2_2015_UTC,
                                   gencerts.JANUARY_1_2016_UTC)

# Target certificate.
target = gencerts.create_end_entity_certificate('Target', oldintermediate)
target.set_validity_range(gencerts.JANUARY_1_2015_UTC,
                          gencerts.JANUARY_1_2016_UTC)

gencerts.write_chain(__doc__,
    [target, oldintermediate, oldroot], out_pem="oldchain.pem")
gencerts.write_chain(__doc__,
    [target, newintermediate, newrootrollover, oldroot],
    out_pem="rolloverchain.pem")
gencerts.write_chain(__doc__,
    [target, newintermediate, newroot, newrootrollover, oldroot],
    out_pem="longrolloverchain.pem")
gencerts.write_chain(__doc__,
    [target, newintermediate, newroot], out_pem="newchain.pem")
