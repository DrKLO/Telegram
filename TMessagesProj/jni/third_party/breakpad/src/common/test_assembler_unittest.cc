// Copyright (c) 2010, Google Inc.
// All rights reserved.
//
// Redistribution and use in source and binary forms, with or without
// modification, are permitted provided that the following conditions are
// met:
//
//     * Redistributions of source code must retain the above copyright
// notice, this list of conditions and the following disclaimer.
//     * Redistributions in binary form must reproduce the above
// copyright notice, this list of conditions and the following disclaimer
// in the documentation and/or other materials provided with the
// distribution.
//     * Neither the name of Google Inc. nor the names of its
// contributors may be used to endorse or promote products derived from
// this software without specific prior written permission.
//
// THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
// "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
// LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
// A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
// OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
// SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
// LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
// DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
// THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
// (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
// OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

// Original author: Jim Blandy <jimb@mozilla.com> <jimb@red-bean.com>

// test_assembler_unittest.cc: Unit tests for google_breakpad::TestAssembler.

#include <string>
#include <string.h>

#include "breakpad_googletest_includes.h"
#include "common/test_assembler.h"
#include "common/using_std_string.h"

using google_breakpad::test_assembler::Label;
using google_breakpad::test_assembler::Section;
using google_breakpad::test_assembler::kBigEndian;
using google_breakpad::test_assembler::kLittleEndian;
using testing::Test;

TEST(ConstructLabel, Simple) {
  Label l;
}

TEST(ConstructLabel, Undefined) {
  Label l;
  EXPECT_FALSE(l.IsKnownConstant());
}

TEST(ConstructLabelDeathTest, Undefined) {
  Label l;
  ASSERT_DEATH(l.Value(), "IsKnownConstant\\(&v\\)");
}

TEST(ConstructLabel, Constant) {
  Label l(0x060b9f974eaf301eULL);
  uint64_t v;
  EXPECT_TRUE(l.IsKnownConstant(&v));
  EXPECT_EQ(v, 0x060b9f974eaf301eULL);
  EXPECT_EQ(l.Value(), 0x060b9f974eaf301eULL);
}

TEST(ConstructLabel, Copy) {
  Label l;
  Label m(l);
  uint64_t v;
  EXPECT_TRUE(l.IsKnownOffsetFrom(m, &v));
  EXPECT_EQ(0U, v);
}

// The left-hand-side of a label assignment can be either
// unconstrained, related, or known. The right-hand-side can be any of
// those, or an integer.
TEST(Assignment, UnconstrainedToUnconstrained) {
  Label l, m;
  l = m;
  EXPECT_EQ(0U, l-m);
  EXPECT_TRUE(l.IsKnownOffsetFrom(m));
  uint64_t d;
  EXPECT_TRUE(l.IsKnownOffsetFrom(m, &d));
  EXPECT_EQ(0U, d);
  EXPECT_FALSE(l.IsKnownConstant());
}

TEST(Assignment, UnconstrainedToRelated) {
  Label l, m, n;
  l = n;
  l = m;
  EXPECT_EQ(0U, l-m);
  EXPECT_TRUE(l.IsKnownOffsetFrom(m));
  uint64_t d;
  EXPECT_TRUE(l.IsKnownOffsetFrom(m, &d));
  EXPECT_EQ(0U, d);
  EXPECT_FALSE(l.IsKnownConstant());
}

TEST(Assignment, UnconstrainedToKnown) {
  Label l, m;
  l = 0x8fd16e55b20a39c1ULL;
  l = m;
  EXPECT_EQ(0U, l-m);
  EXPECT_TRUE(l.IsKnownOffsetFrom(m));
  uint64_t d;
  EXPECT_TRUE(l.IsKnownOffsetFrom(m, &d));
  EXPECT_EQ(0U, d);
  EXPECT_TRUE(m.IsKnownConstant());
  EXPECT_EQ(0x8fd16e55b20a39c1ULL, m.Value());
}

TEST(Assignment, RelatedToUnconstrained) {
  Label l, m, n;
  m = n;
  l = m;
  EXPECT_EQ(0U, l-n);
  EXPECT_TRUE(l.IsKnownOffsetFrom(n));
  uint64_t d;
  EXPECT_TRUE(l.IsKnownOffsetFrom(n, &d));
  EXPECT_EQ(0U, d);
  EXPECT_FALSE(l.IsKnownConstant());
}

TEST(Assignment, RelatedToRelated) {
  Label l, m, n, o;
  l = n;
  m = o;
  l = m;
  EXPECT_EQ(0U, n-o);
  EXPECT_TRUE(n.IsKnownOffsetFrom(o));
  uint64_t d;
  EXPECT_TRUE(n.IsKnownOffsetFrom(o, &d));
  EXPECT_EQ(0U, d);
  EXPECT_FALSE(l.IsKnownConstant());
}

TEST(Assignment, RelatedToKnown) {
  Label l, m, n;
  m = n;
  l = 0xd2011f8c82ad56f2ULL;
  l = m;
  EXPECT_TRUE(l.IsKnownConstant());
  EXPECT_EQ(0xd2011f8c82ad56f2ULL, l.Value());
  EXPECT_TRUE(m.IsKnownConstant());
  EXPECT_EQ(0xd2011f8c82ad56f2ULL, m.Value());
  EXPECT_TRUE(n.IsKnownConstant());
  EXPECT_EQ(0xd2011f8c82ad56f2ULL, n.Value());
}

TEST(Assignment, KnownToUnconstrained) {
  Label l, m;
  m = 0x50b024c0d6073887ULL;
  l = m;
  EXPECT_TRUE(l.IsKnownConstant());
  EXPECT_EQ(0x50b024c0d6073887ULL, l.Value());
  EXPECT_TRUE(m.IsKnownConstant());
  EXPECT_EQ(0x50b024c0d6073887ULL, m.Value());
}

TEST(Assignment, KnownToRelated) {
  Label l, m, n;
  l = n;
  m = 0x5348883655c727e5ULL;
  l = m;
  EXPECT_TRUE(l.IsKnownConstant());
  EXPECT_EQ(0x5348883655c727e5ULL, l.Value());
  EXPECT_TRUE(m.IsKnownConstant());
  EXPECT_EQ(0x5348883655c727e5ULL, m.Value());
  EXPECT_TRUE(n.IsKnownConstant());
  EXPECT_EQ(0x5348883655c727e5ULL, n.Value());
}

TEST(Assignment, KnownToKnown) {
  Label l, m;
  l = 0x36c209c20987564eULL;
  m = 0x36c209c20987564eULL;
  l = m;
  EXPECT_TRUE(l.IsKnownConstant());
  EXPECT_EQ(0x36c209c20987564eULL, l.Value());
  EXPECT_TRUE(m.IsKnownConstant());
  EXPECT_EQ(0x36c209c20987564eULL, m.Value());
}

TEST(Assignment, ConstantToUnconstrained) {
  Label l;
  l = 0xc02495f4d7f5a957ULL;
  EXPECT_TRUE(l.IsKnownConstant());
  EXPECT_EQ(0xc02495f4d7f5a957ULL, l.Value());
}

TEST(Assignment, ConstantToRelated) {
  Label l, m;
  l = m;
  l = 0x4577901cf275488dULL;
  EXPECT_TRUE(l.IsKnownConstant());
  EXPECT_EQ(0x4577901cf275488dULL, l.Value());
  EXPECT_TRUE(m.IsKnownConstant());
  EXPECT_EQ(0x4577901cf275488dULL, m.Value());
}

TEST(Assignment, ConstantToKnown) {
  Label l;
  l = 0xec0b9c369b7e8ea7ULL;
  l = 0xec0b9c369b7e8ea7ULL;
  EXPECT_TRUE(l.IsKnownConstant());
  EXPECT_EQ(0xec0b9c369b7e8ea7ULL, l.Value());
}

TEST(AssignmentDeathTest, Self) {
  Label l;
  ASSERT_DEATH(l = l, "binding != this");
}

TEST(AssignmentDeathTest, IndirectCycle) {
  Label l, m, n;
  l = m;
  m = n;
  ASSERT_DEATH(n = l, "binding != this");
}

TEST(AssignmentDeathTest, Cycle) {
  Label l, m, n, o;
  l = m;
  m = n;
  o = n;
  ASSERT_DEATH(o = l, "binding != this");
}

TEST(Addition, LabelConstant) {
  Label l, m;
  m = l + 0x5248d93e8bbe9497ULL;
  EXPECT_TRUE(m.IsKnownOffsetFrom(l));
  uint64_t d;
  EXPECT_TRUE(m.IsKnownOffsetFrom(l, &d));
  EXPECT_EQ(0x5248d93e8bbe9497ULL, d);
  EXPECT_FALSE(m.IsKnownConstant());
}

TEST(Addition, ConstantLabel) {
  Label l, m;
  m = 0xf51e94e00d6e3c84ULL + l;
  EXPECT_TRUE(m.IsKnownOffsetFrom(l));
  uint64_t d;
  EXPECT_TRUE(m.IsKnownOffsetFrom(l, &d));
  EXPECT_EQ(0xf51e94e00d6e3c84ULL, d);
  EXPECT_FALSE(m.IsKnownConstant());
}

TEST(Addition, KnownLabelConstant) {
  Label l, m;
  l = 0x16286307042ce0d8ULL;
  m = l + 0x3fdddd91306719d7ULL;
  EXPECT_TRUE(m.IsKnownOffsetFrom(l));
  uint64_t d;
  EXPECT_TRUE(m.IsKnownOffsetFrom(l, &d));
  EXPECT_EQ(0x3fdddd91306719d7ULL, d);
  EXPECT_TRUE(m.IsKnownConstant());
  EXPECT_EQ(0x16286307042ce0d8ULL + 0x3fdddd91306719d7ULL, m.Value());
}

TEST(Addition, ConstantKnownLabel) {
  Label l, m;
  l = 0x50f62d0cdd1031deULL;
  m = 0x1b13462d8577c538ULL + l;
  EXPECT_TRUE(m.IsKnownOffsetFrom(l));
  uint64_t d;
  EXPECT_TRUE(m.IsKnownOffsetFrom(l, &d));
  EXPECT_EQ(0x1b13462d8577c538ULL, d);
  EXPECT_TRUE(m.IsKnownConstant());
  EXPECT_EQ(0x50f62d0cdd1031deULL + 0x1b13462d8577c538ULL, m.Value());
}

TEST(Subtraction, LabelConstant) {
  Label l, m;
  m = l - 0x0620884d21d3138eULL;
  EXPECT_TRUE(m.IsKnownOffsetFrom(l));
  uint64_t d;
  EXPECT_TRUE(m.IsKnownOffsetFrom(l, &d));
  EXPECT_EQ(-0x0620884d21d3138eULL, d);
  EXPECT_FALSE(m.IsKnownConstant());
}

TEST(Subtraction, KnownLabelConstant) {
  Label l, m;
  l = 0x6237fbaf9ef7929eULL;
  m = l - 0x317730995d2ab6eeULL;
  EXPECT_TRUE(m.IsKnownOffsetFrom(l));
  uint64_t d;
  EXPECT_TRUE(m.IsKnownOffsetFrom(l, &d));
  EXPECT_EQ(-0x317730995d2ab6eeULL, d);
  EXPECT_TRUE(m.IsKnownConstant());
  EXPECT_EQ(0x6237fbaf9ef7929eULL - 0x317730995d2ab6eeULL, m.Value());
}

TEST(SubtractionDeathTest, LabelLabel) {
  Label l, m;
  ASSERT_DEATH(l - m, "IsKnownOffsetFrom\\(label, &offset\\)");
}

TEST(Subtraction, LabelLabel) {
  Label l, m;
  l = m + 0x7fa77ec63e28a17aULL;
  EXPECT_EQ(0x7fa77ec63e28a17aULL, l - m);
  EXPECT_EQ(-0x7fa77ec63e28a17aULL, m - l);
}

TEST(IsKnownConstant, Undefined) {
  Label l;
  EXPECT_FALSE(l.IsKnownConstant());
}

TEST(IsKnownConstant, RelatedLabel) {
  Label l, m;
  l = m;
  EXPECT_FALSE(l.IsKnownConstant());
  EXPECT_FALSE(m.IsKnownConstant());
}

TEST(IsKnownConstant, Constant) {
  Label l;
  l = 0xf374b1bdd6a22576ULL;
  EXPECT_TRUE(l.IsKnownConstant());
}

TEST(IsKnownOffsetFrom, Unrelated) {
  Label l, m;
  EXPECT_FALSE(l.IsKnownOffsetFrom(m));
}

TEST(IsKnownOffsetFrom, Related) {
  Label l, m;
  l = m;
  EXPECT_TRUE(l.IsKnownOffsetFrom(m));
}

// Test the construction of chains of related labels, and the
// propagation of values through them.
//
// Although the relations between labels are supposed to behave
// symmetrically --- that is, 'a = b' should put a and b in
// indistinguishable states --- there's a distinction made internally
// between the target (a) and the source (b).
// 
// So there are five test axes to cover:
//
// - Do we construct the chain with assignment ("Assign") or with constructors
//   ("Construct")?
//
// - Do we set the value of the label at the start of the chain
//   ("Start") or the label at the end ("End")?
//
// - Are we testing the propagation of a relationship between variable
//   values ("Relation"), or the propagation of a known constant value
//   ("Value")?
//
// - Do we set the value before building the chain ("Before") or after
//   the chain has been built ("After")?
//
// - Do we add new relationships to the end of the existing chain
//   ("Forward") or to the beginning ("Backward")?
//
// Of course, "Construct" and "Backward" can't be combined, which
// eliminates eight combinations, and "Construct", "End", and "Before"
// can't be combined, which eliminates two more, so there are are 22
// combinations, not 32.

TEST(LabelChain, AssignStartRelationBeforeForward) {
  Label a, b, c, d;
  Label x;
  a = x;
  b = a + 0x1;
  c = b + 0x10;
  d = c + 0x100;
  EXPECT_EQ(0x111U, d-x);
  EXPECT_EQ(0x11U,  c-x);
  EXPECT_EQ(0x1U,   b-x);
  EXPECT_EQ(0U,     a-x);
}

TEST(LabelChain, AssignStartRelationBeforeBackward) {
  Label a, b, c, d;
  Label x;
  a = x;
  d = c + 0x100;
  c = b + 0x10;
  b = a + 0x1;
  EXPECT_EQ(0x111U, d-x);
  EXPECT_EQ(0x11U,  c-x);
  EXPECT_EQ(0x1U,   b-x);
  EXPECT_EQ(0U,     a-x);
}

TEST(LabelChain, AssignStartRelationAfterForward) {
  Label a, b, c, d;
  Label x;
  b = a + 0x1;
  c = b + 0x10;
  d = c + 0x100;
  a = x;
  EXPECT_EQ(0x111U, d-x);
  EXPECT_EQ(0x11U,  c-x);
  EXPECT_EQ(0x1U,   b-x);
  EXPECT_EQ(0U,     a-x);
}

TEST(LabelChain, AssignStartRelationAfterBackward) {
  Label a, b, c, d;
  Label x;
  d = c + 0x100;
  c = b + 0x10;
  b = a + 0x1;
  a = x;
  EXPECT_EQ(0x111U, d-x);
  EXPECT_EQ(0x11U,  c-x);
  EXPECT_EQ(0x1U,   b-x);
  EXPECT_EQ(0U,     a-x);
}

TEST(LabelChain, AssignStartValueBeforeForward) {
  Label a, b, c, d;
  a = 0xa131200190546ac2ULL;
  b = a + 0x1;
  c = b + 0x10;
  d = c + 0x100;
  EXPECT_EQ(0xa131200190546ac2ULL + 0x111U, d.Value());
  EXPECT_EQ(0xa131200190546ac2ULL + 0x11U,  c.Value());
  EXPECT_EQ(0xa131200190546ac2ULL + 0x1U,   b.Value());
  EXPECT_EQ(0xa131200190546ac2ULL + 0U,     a.Value());
}

TEST(LabelChain, AssignStartValueBeforeBackward) {
  Label a, b, c, d;
  a = 0x8da17e1670ad4fa2ULL;
  d = c + 0x100;
  c = b + 0x10;
  b = a + 0x1;
  EXPECT_EQ(0x8da17e1670ad4fa2ULL + 0x111U, d.Value());
  EXPECT_EQ(0x8da17e1670ad4fa2ULL + 0x11U,  c.Value());
  EXPECT_EQ(0x8da17e1670ad4fa2ULL + 0x1U,   b.Value());
  EXPECT_EQ(0x8da17e1670ad4fa2ULL + 0U,     a.Value());
}

TEST(LabelChain, AssignStartValueAfterForward) {
  Label a, b, c, d;
  b = a + 0x1;
  c = b + 0x10;
  d = c + 0x100;
  a = 0x99b8f51bafd41adaULL;
  EXPECT_EQ(0x99b8f51bafd41adaULL + 0x111U, d.Value());
  EXPECT_EQ(0x99b8f51bafd41adaULL + 0x11U,  c.Value());
  EXPECT_EQ(0x99b8f51bafd41adaULL + 0x1U,   b.Value());
  EXPECT_EQ(0x99b8f51bafd41adaULL + 0U,     a.Value());
}

TEST(LabelChain, AssignStartValueAfterBackward) {
  Label a, b, c, d;
  d = c + 0x100;
  c = b + 0x10;
  b = a + 0x1;
  a = 0xc86ca1d97ab5df6eULL;
  EXPECT_EQ(0xc86ca1d97ab5df6eULL + 0x111U, d.Value());
  EXPECT_EQ(0xc86ca1d97ab5df6eULL + 0x11U,  c.Value());
  EXPECT_EQ(0xc86ca1d97ab5df6eULL + 0x1U,   b.Value());
  EXPECT_EQ(0xc86ca1d97ab5df6eULL + 0U,     a.Value());
}

TEST(LabelChain, AssignEndRelationBeforeForward) {
  Label a, b, c, d;
  Label x;
  x = d;
  b = a + 0x1;
  c = b + 0x10;
  d = c + 0x100;
  EXPECT_EQ(-(uint64_t)0x111U, a-x);
  EXPECT_EQ(-(uint64_t)0x110U, b-x);
  EXPECT_EQ(-(uint64_t)0x100U, c-x);
  EXPECT_EQ(-(uint64_t)0U,     d-x);
}

TEST(LabelChain, AssignEndRelationBeforeBackward) {
  Label a, b, c, d;
  Label x;
  x = d;
  d = c + 0x100;
  c = b + 0x10;
  b = a + 0x1;
  EXPECT_EQ(-(uint64_t)0x111U, a-x);
  EXPECT_EQ(-(uint64_t)0x110U, b-x);
  EXPECT_EQ(-(uint64_t)0x100U, c-x);
  EXPECT_EQ(-(uint64_t)0U,     d-x);
}

TEST(LabelChain, AssignEndRelationAfterForward) {
  Label a, b, c, d;
  Label x;
  b = a + 0x1;
  c = b + 0x10;
  d = c + 0x100;
  x = d;
  EXPECT_EQ(-(uint64_t)0x111U, a-x);
  EXPECT_EQ(-(uint64_t)0x110U, b-x);
  EXPECT_EQ(-(uint64_t)0x100U, c-x);
  EXPECT_EQ(-(uint64_t)0x000U, d-x);
}

TEST(LabelChain, AssignEndRelationAfterBackward) {
  Label a, b, c, d;
  Label x;
  d = c + 0x100;
  c = b + 0x10;
  b = a + 0x1;
  x = d;
  EXPECT_EQ(-(uint64_t)0x111U, a-x);
  EXPECT_EQ(-(uint64_t)0x110U, b-x);
  EXPECT_EQ(-(uint64_t)0x100U, c-x);
  EXPECT_EQ(-(uint64_t)0x000U, d-x);
}

TEST(LabelChain, AssignEndValueBeforeForward) {
  Label a, b, c, d;
  d = 0xa131200190546ac2ULL;
  b = a + 0x1;
  c = b + 0x10;
  d = c + 0x100;
  EXPECT_EQ(0xa131200190546ac2ULL - 0x111, a.Value());
  EXPECT_EQ(0xa131200190546ac2ULL - 0x110, b.Value());
  EXPECT_EQ(0xa131200190546ac2ULL - 0x100, c.Value());
  EXPECT_EQ(0xa131200190546ac2ULL - 0x000, d.Value());
}

TEST(LabelChain, AssignEndValueBeforeBackward) {
  Label a, b, c, d;
  d = 0x8da17e1670ad4fa2ULL;
  d = c + 0x100;
  c = b + 0x10;
  b = a + 0x1;
  EXPECT_EQ(0x8da17e1670ad4fa2ULL - 0x111, a.Value());
  EXPECT_EQ(0x8da17e1670ad4fa2ULL - 0x110, b.Value());
  EXPECT_EQ(0x8da17e1670ad4fa2ULL - 0x100, c.Value());
  EXPECT_EQ(0x8da17e1670ad4fa2ULL - 0x000, d.Value());
}

TEST(LabelChain, AssignEndValueAfterForward) {
  Label a, b, c, d;
  b = a + 0x1;
  c = b + 0x10;
  d = c + 0x100;
  d = 0x99b8f51bafd41adaULL;
  EXPECT_EQ(0x99b8f51bafd41adaULL - 0x111, a.Value());
  EXPECT_EQ(0x99b8f51bafd41adaULL - 0x110, b.Value());
  EXPECT_EQ(0x99b8f51bafd41adaULL - 0x100, c.Value());
  EXPECT_EQ(0x99b8f51bafd41adaULL - 0x000, d.Value());
}

TEST(LabelChain, AssignEndValueAfterBackward) {
  Label a, b, c, d;
  d = c + 0x100;
  c = b + 0x10;
  b = a + 0x1;
  d = 0xc86ca1d97ab5df6eULL;
  EXPECT_EQ(0xc86ca1d97ab5df6eULL - 0x111, a.Value());
  EXPECT_EQ(0xc86ca1d97ab5df6eULL - 0x110, b.Value());
  EXPECT_EQ(0xc86ca1d97ab5df6eULL - 0x100, c.Value());
  EXPECT_EQ(0xc86ca1d97ab5df6eULL - 0x000, d.Value());
}

TEST(LabelChain, ConstructStartRelationBeforeForward) {
  Label x;
  Label a(x);
  Label b(a + 0x1);
  Label c(b + 0x10);
  Label d(c + 0x100);
  EXPECT_EQ(0x111U, d-x);
  EXPECT_EQ(0x11U,  c-x);
  EXPECT_EQ(0x1U,   b-x);
  EXPECT_EQ(0U,     a-x);
}

TEST(LabelChain, ConstructStartRelationAfterForward) {
  Label x;
  Label a;
  Label b(a + 0x1);
  Label c(b + 0x10);
  Label d(c + 0x100);
  a = x;
  EXPECT_EQ(0x111U, d-x);
  EXPECT_EQ(0x11U,  c-x);
  EXPECT_EQ(0x1U,   b-x);
  EXPECT_EQ(0U,     a-x);
}

TEST(LabelChain, ConstructStartValueBeforeForward) {
  Label a(0x5d234d177d01ccc8ULL);
  Label b(a + 0x1);
  Label c(b + 0x10);
  Label d(c + 0x100);
  EXPECT_EQ(0x5d234d177d01ccc8ULL + 0x111U, d.Value());
  EXPECT_EQ(0x5d234d177d01ccc8ULL + 0x011U, c.Value());
  EXPECT_EQ(0x5d234d177d01ccc8ULL + 0x001U, b.Value());
  EXPECT_EQ(0x5d234d177d01ccc8ULL + 0x000U, a.Value());
}

TEST(LabelChain, ConstructStartValueAfterForward) {
  Label a;
  Label b(a + 0x1);
  Label c(b + 0x10);
  Label d(c + 0x100);
  a = 0xded85d54586e84fcULL;
  EXPECT_EQ(0xded85d54586e84fcULL + 0x111U, d.Value());
  EXPECT_EQ(0xded85d54586e84fcULL + 0x011U, c.Value());
  EXPECT_EQ(0xded85d54586e84fcULL + 0x001U, b.Value());
  EXPECT_EQ(0xded85d54586e84fcULL + 0x000U, a.Value());
}

TEST(LabelChain, ConstructEndRelationAfterForward) {
  Label x;
  Label a;
  Label b(a + 0x1);
  Label c(b + 0x10);
  Label d(c + 0x100);
  x = d;
  EXPECT_EQ(-(uint64_t)0x111U, a-x);
  EXPECT_EQ(-(uint64_t)0x110U, b-x);
  EXPECT_EQ(-(uint64_t)0x100U, c-x);
  EXPECT_EQ(-(uint64_t)0x000U, d-x);
}

TEST(LabelChain, ConstructEndValueAfterForward) {
  Label a;
  Label b(a + 0x1);
  Label c(b + 0x10);
  Label d(c + 0x100);
  d = 0x99b8f51bafd41adaULL;
  EXPECT_EQ(0x99b8f51bafd41adaULL - 0x111, a.Value());
  EXPECT_EQ(0x99b8f51bafd41adaULL - 0x110, b.Value());
  EXPECT_EQ(0x99b8f51bafd41adaULL - 0x100, c.Value());
  EXPECT_EQ(0x99b8f51bafd41adaULL - 0x000, d.Value());
}

TEST(LabelTree, KnownValue) {
  Label l, m, n, o, p;
  l = m;
  m = n;
  o = p;
  p = n;
  l = 0x536b5de3d468a1b5ULL;
  EXPECT_EQ(0x536b5de3d468a1b5ULL, o.Value());
}

TEST(LabelTree, Related) {
  Label l, m, n, o, p;
  l = m - 1;
  m = n - 10;
  o = p + 100;
  p = n + 1000;
  EXPECT_EQ(1111U, o - l);
}

TEST(EquationDeathTest, EqualConstants) {
  Label m = 0x0d3962f280f07d24ULL;
  Label n = 0x0d3962f280f07d24ULL;
  m = n; // no death expected
}

TEST(EquationDeathTest, EqualIndirectConstants) {
  Label m = 0xa347f1e5238fe6a1ULL;
  Label n;
  Label o = n;
  n = 0xa347f1e5238fe6a1ULL;
  n = m; // no death expected
}

TEST(EquationDeathTest, ConstantClash) {
  Label m = 0xd4cc0f4f630ec741ULL;
  Label n = 0x934cd2d8254fc3eaULL;
  ASSERT_DEATH(m = n, "addend_ == addend");
}

TEST(EquationDeathTest, IndirectConstantClash) {
  Label m = 0xd4cc0f4f630ec741ULL;
  Label n, o;
  n = o;
  o = 0xcfbe3b83ac49ce86ULL;
  ASSERT_DEATH(m = n, "addend_ == addend");
}

// Assigning to a related label may free the next Binding on its
// chain. This test always passes; it is interesting to memory
// checkers and coverage analysis.
TEST(LabelReferenceCount, AssignmentFree) {
  Label l;
  {
    Label m;
    l = m;
  }
  // This should free m's Binding.
  l = 0xca8bae92f0376d4fULL;
  ASSERT_EQ(0xca8bae92f0376d4fULL, l.Value());
}

// Finding the value of a label may free the Binding it refers to. This test
// always passes; it is interesting to memory checkers and coverage analysis.
TEST(LabelReferenceCount, FindValueFree) {
  Label l;
  {
    Label m, n;
    l = m;
    m = n;
    n = 0x7a0b0c576672daafULL;
    // At this point, l's Binding refers to m's Binding, which refers
    // to n's binding.
  }
  // Now, l is the only reference keeping the three Bindings alive.
  // Resolving its value should free l's and m's original bindings.
  ASSERT_EQ(0x7a0b0c576672daafULL, l.Value());
}

TEST(ConstructSection, Simple) {
  Section s;
}

TEST(ConstructSection, WithEndian) {
  Section s(kBigEndian);
}

// A fixture class for TestAssembler::Section tests.
class SectionFixture {
 public:
  Section section;
  string contents;
  static const uint8_t data[];
  static const size_t data_size;
};

const uint8_t SectionFixture::data[] = {
  0x87, 0x4f, 0x43, 0x67, 0x30, 0xd0, 0xd4, 0x0e
};

#define I0()
#define I1(a) { a }
#define I2(a,b) { a,b }
#define I3(a,b,c) { a,b,c }
#define I4(a,b,c,d) { a,b,c,d }
#define I5(a,b,c,d,e) { a,b,c,d,e }
#define I6(a,b,c,d,e,f) { a,b,c,d,e,f }
#define I7(a,b,c,d,e,f,g) { a,b,c,d,e,f,g }
#define I8(a,b,c,d,e,f,g,h) { a,b,c,d,e,f,g,h }
#define I9(a,b,c,d,e,f,g,h,i) { a,b,c,d,e,f,g,h,i }
#define ASSERT_BYTES(s, b)                                              \
  do                                                                    \
    {                                                                   \
      static const uint8_t expected_bytes[] = b;                       \
      ASSERT_EQ(sizeof(expected_bytes), s.size());                      \
      ASSERT_TRUE(memcmp(s.data(), (const char *) expected_bytes,       \
                         sizeof(expected_bytes)) == 0);                 \
    }                                                                   \
  while(0)

class Append: public SectionFixture, public Test { };

TEST_F(Append, Bytes) {
  section.Append(data, sizeof(data));
  ASSERT_TRUE(section.GetContents(&contents));
  ASSERT_EQ(sizeof(data), contents.size());
  EXPECT_TRUE(0 == memcmp(contents.data(), (const char *) data, sizeof(data)));
}

TEST_F(Append, BytesTwice) {
  section.Append(data, sizeof(data));
  section.Append(data, sizeof(data));
  ASSERT_TRUE(section.GetContents(&contents));
  ASSERT_EQ(2 * sizeof(data), contents.size());
  ASSERT_TRUE(0 == memcmp(contents.data(), (const char *) data, sizeof(data)));
  ASSERT_TRUE(0 == memcmp(contents.data() + sizeof(data),
                          (const char *) data, sizeof(data)));
}

TEST_F(Append, String) {
  string s1 = "howdy ";
  string s2 = "there";
  section.Append(s1);
  section.Append(s2);
  ASSERT_TRUE(section.GetContents(&contents));
  ASSERT_STREQ(contents.c_str(), "howdy there");
}

TEST_F(Append, CString) {
  section.AppendCString("howdy");
  section.AppendCString("");
  section.AppendCString("there");
  ASSERT_TRUE(section.GetContents(&contents));
  ASSERT_EQ(string("howdy\0\0there\0", 13), contents);
}

TEST_F(Append, CStringSize) {
  section.AppendCString("howdy", 3);
  section.AppendCString("there", 5);
  section.AppendCString("fred", 6);
  section.AppendCString("natalie", 0);
  section.AppendCString("", 10);
  ASSERT_TRUE(section.GetContents(&contents));
  ASSERT_EQ(string("howtherefred\0\0\0\0\0\0\0\0\0\0\0\0", 24), contents);
}

TEST_F(Append, RepeatedBytes) {
  section.Append((size_t) 10, '*');
  ASSERT_TRUE(section.GetContents(&contents));
  ASSERT_STREQ(contents.c_str(), "**********");
}

TEST_F(Append, GeneralLE1) {
  section.Append(kLittleEndian, 1, 42);
  ASSERT_TRUE(section.GetContents(&contents));
  ASSERT_BYTES(contents, I1(42));
}

TEST_F(Append, GeneralLE2) {
  section.Append(kLittleEndian, 2, 0x15a1);
  ASSERT_TRUE(section.GetContents(&contents));
  ASSERT_BYTES(contents, I2(0xa1, 0x15));
}

TEST_F(Append, GeneralLE3) {
  section.Append(kLittleEndian, 3, 0x59ae8d);
  ASSERT_TRUE(section.GetContents(&contents));
  ASSERT_BYTES(contents, I3(0x8d, 0xae, 0x59));
}

TEST_F(Append, GeneralLE4) {
  section.Append(kLittleEndian, 4, 0x51603c56);
  ASSERT_TRUE(section.GetContents(&contents));
  ASSERT_BYTES(contents, I4(0x56, 0x3c, 0x60, 0x51));
}

TEST_F(Append, GeneralLE5) {
  section.Append(kLittleEndian, 5, 0x385e2803b4ULL);
  ASSERT_TRUE(section.GetContents(&contents));
  ASSERT_BYTES(contents, I5(0xb4, 0x03, 0x28, 0x5e, 0x38));
}

TEST_F(Append, GeneralLE6) {
  section.Append(kLittleEndian, 6, 0xc7db9534dd1fULL);
  ASSERT_TRUE(section.GetContents(&contents));
  ASSERT_BYTES(contents, I6(0x1f, 0xdd, 0x34, 0x95, 0xdb, 0xc7));
}

TEST_F(Append, GeneralLE7) {
  section.Append(kLittleEndian, 7, 0x1445c9f1b843e6ULL);
  ASSERT_TRUE(section.GetContents(&contents));
  ASSERT_BYTES(contents, I7(0xe6, 0x43, 0xb8, 0xf1, 0xc9, 0x45, 0x14));
}

TEST_F(Append, GeneralLE8) {
  section.Append(kLittleEndian, 8, 0xaf48019dfe5c01e5ULL);
  ASSERT_TRUE(section.GetContents(&contents));
  ASSERT_BYTES(contents, I8(0xe5, 0x01, 0x5c, 0xfe, 0x9d, 0x01, 0x48, 0xaf));
}

TEST_F(Append, GeneralBE1) {
  section.Append(kBigEndian, 1, 0xd0ULL);
  ASSERT_TRUE(section.GetContents(&contents));
  ASSERT_BYTES(contents, I1(0xd0));
}

TEST_F(Append, GeneralBE2) {
  section.Append(kBigEndian, 2, 0x2e7eULL);
  ASSERT_TRUE(section.GetContents(&contents));
  ASSERT_BYTES(contents, I2(0x2e, 0x7e));
}

TEST_F(Append, GeneralBE3) {
  section.Append(kBigEndian, 3, 0x37dad6ULL);
  ASSERT_TRUE(section.GetContents(&contents));
  ASSERT_BYTES(contents, I3(0x37, 0xda, 0xd6));
}

TEST_F(Append, GeneralBE4) {
  section.Append(kBigEndian, 4, 0x715935c7ULL);
  ASSERT_TRUE(section.GetContents(&contents));
  ASSERT_BYTES(contents, I4(0x71, 0x59, 0x35, 0xc7));
}

TEST_F(Append, GeneralBE5) {
  section.Append(kBigEndian, 5, 0x42baeb02b7ULL);
  ASSERT_TRUE(section.GetContents(&contents));
  ASSERT_BYTES(contents, I5(0x42, 0xba, 0xeb, 0x02, 0xb7));
}

TEST_F(Append, GeneralBE6) {
  section.Append(kBigEndian, 6, 0xf1cdf10e7b18ULL);
  ASSERT_TRUE(section.GetContents(&contents));
  ASSERT_BYTES(contents, I6(0xf1, 0xcd, 0xf1, 0x0e, 0x7b, 0x18));
}

TEST_F(Append, GeneralBE7) {
  section.Append(kBigEndian, 7, 0xf50a724f0b0d20ULL);
  ASSERT_TRUE(section.GetContents(&contents));
  ASSERT_BYTES(contents, I7(0xf5, 0x0a, 0x72, 0x4f, 0x0b, 0x0d, 0x20));
}

TEST_F(Append, GeneralBE8) {
  section.Append(kBigEndian, 8, 0xa6b2cb5e98dc9c16ULL);
  ASSERT_TRUE(section.GetContents(&contents));
  ASSERT_BYTES(contents, I8(0xa6, 0xb2, 0xcb, 0x5e, 0x98, 0xdc, 0x9c, 0x16));
}

TEST_F(Append, GeneralLE1Label) {
  Label l;
  section.Append(kLittleEndian, 1, l);
  l = 42;
  ASSERT_TRUE(section.GetContents(&contents));
  ASSERT_BYTES(contents, I1(42));
}

TEST_F(Append, GeneralLE2Label) {
  Label l;
  section.Append(kLittleEndian, 2, l);
  l = 0x15a1;
  ASSERT_TRUE(section.GetContents(&contents));
  ASSERT_BYTES(contents, I2(0xa1, 0x15));
}

TEST_F(Append, GeneralLE3Label) {
  Label l;
  section.Append(kLittleEndian, 3, l);
  l = 0x59ae8d;
  ASSERT_TRUE(section.GetContents(&contents));
  ASSERT_BYTES(contents, I3(0x8d, 0xae, 0x59));
}

TEST_F(Append, GeneralLE4Label) {
  Label l;
  section.Append(kLittleEndian, 4, l);
  l = 0x51603c56;
  ASSERT_TRUE(section.GetContents(&contents));
  ASSERT_BYTES(contents, I4(0x56, 0x3c, 0x60, 0x51));
}

TEST_F(Append, GeneralLE5Label) {
  Label l;
  section.Append(kLittleEndian, 5, l);
  l = 0x385e2803b4ULL;
  ASSERT_TRUE(section.GetContents(&contents));
  ASSERT_BYTES(contents, I5(0xb4, 0x03, 0x28, 0x5e, 0x38));
}

TEST_F(Append, GeneralLE6Label) {
  Label l;
  section.Append(kLittleEndian, 6, l);
  l = 0xc7db9534dd1fULL;
  ASSERT_TRUE(section.GetContents(&contents));
  ASSERT_BYTES(contents, I6(0x1f, 0xdd, 0x34, 0x95, 0xdb, 0xc7));
}

TEST_F(Append, GeneralLE7Label) {
  Label l;
  section.Append(kLittleEndian, 7, l);
  l = 0x1445c9f1b843e6ULL;
  ASSERT_TRUE(section.GetContents(&contents));
  ASSERT_BYTES(contents, I7(0xe6, 0x43, 0xb8, 0xf1, 0xc9, 0x45, 0x14));
}

TEST_F(Append, GeneralLE8Label) {
  Label l;
  section.Append(kLittleEndian, 8, l);
  l = 0xaf48019dfe5c01e5ULL;
  ASSERT_TRUE(section.GetContents(&contents));
  ASSERT_BYTES(contents, I8(0xe5, 0x01, 0x5c, 0xfe, 0x9d, 0x01, 0x48, 0xaf));
}

TEST_F(Append, GeneralBE1Label) {
  Label l;
  section.Append(kBigEndian, 1, l);
  l = 0xd0ULL;
  ASSERT_TRUE(section.GetContents(&contents));
  ASSERT_BYTES(contents, I1(0xd0));
}

TEST_F(Append, GeneralBE2Label) {
  Label l;
  section.Append(kBigEndian, 2, l);
  l = 0x2e7eULL;
  ASSERT_TRUE(section.GetContents(&contents));
  ASSERT_BYTES(contents, I2(0x2e, 0x7e));
}

TEST_F(Append, GeneralBE3Label) {
  Label l;
  section.Append(kBigEndian, 3, l);
  l = 0x37dad6ULL;
  ASSERT_TRUE(section.GetContents(&contents));
  ASSERT_BYTES(contents, I3(0x37, 0xda, 0xd6));
}

TEST_F(Append, GeneralBE4Label) {
  Label l;
  section.Append(kBigEndian, 4, l);
  l = 0x715935c7ULL;
  ASSERT_TRUE(section.GetContents(&contents));
  ASSERT_BYTES(contents, I4(0x71, 0x59, 0x35, 0xc7));
}

TEST_F(Append, GeneralBE5Label) {
  Label l;
  section.Append(kBigEndian, 5, l);
  l = 0x42baeb02b7ULL;
  ASSERT_TRUE(section.GetContents(&contents));
  ASSERT_BYTES(contents, I5(0x42, 0xba, 0xeb, 0x02, 0xb7));
}

TEST_F(Append, GeneralBE6Label) {
  Label l;
  section.Append(kBigEndian, 6, l);
  l = 0xf1cdf10e7b18ULL;
  ASSERT_TRUE(section.GetContents(&contents));
  ASSERT_BYTES(contents, I6(0xf1, 0xcd, 0xf1, 0x0e, 0x7b, 0x18));
}

TEST_F(Append, GeneralBE7Label) {
  Label l;
  section.Append(kBigEndian, 7, l);
  l = 0xf50a724f0b0d20ULL;
  ASSERT_TRUE(section.GetContents(&contents));
  ASSERT_BYTES(contents, I7(0xf5, 0x0a, 0x72, 0x4f, 0x0b, 0x0d, 0x20));
}

TEST_F(Append, GeneralBE8Label) {
  Label l;
  section.Append(kBigEndian, 8, l);
  l = 0xa6b2cb5e98dc9c16ULL;
  ASSERT_TRUE(section.GetContents(&contents));
  ASSERT_BYTES(contents, I8(0xa6, 0xb2, 0xcb, 0x5e, 0x98, 0xdc, 0x9c, 0x16));
}

TEST_F(Append, B8) {
  section.Append(1, 0x2a);
  section.B8(0xd3U);
  ASSERT_TRUE(section.GetContents(&contents));
  ASSERT_BYTES(contents, I2(0x2a, 0xd3));
}

TEST_F(Append, B8Label) {
  Label l;
  section.Append(1, 0x2a);
  section.B8(l);
  l = 0x4bU;
  ASSERT_TRUE(section.GetContents(&contents));
  ASSERT_BYTES(contents, I2(0x2a, 0x4b));
}

TEST_F(Append, B16) {
  section.Append(1, 0x2a);
  section.B16(0x472aU);
  ASSERT_TRUE(section.GetContents(&contents));
  ASSERT_BYTES(contents, I3(0x2a, 0x47, 0x2a));
}

TEST_F(Append, B16Label) {
  Label l;
  section.Append(1, 0x2a);
  section.B16(l);
  l = 0x55e8U;
  ASSERT_TRUE(section.GetContents(&contents));
  ASSERT_BYTES(contents, I3(0x2a, 0x55, 0xe8));
}

TEST_F(Append, B32) {
  section.Append(1, 0x2a);
  section.B32(0xbd412cbcU);
  ASSERT_TRUE(section.GetContents(&contents));
  ASSERT_BYTES(contents, I5(0x2a, 0xbd, 0x41, 0x2c, 0xbc));
}

TEST_F(Append, B32Label) {
  Label l;
  section.Append(1, 0x2a);
  section.B32(l);
  l = 0x208e37d5U;
  ASSERT_TRUE(section.GetContents(&contents));
  ASSERT_BYTES(contents, I5(0x2a, 0x20, 0x8e, 0x37, 0xd5));
}

TEST_F(Append, B64) {
  section.Append(1, 0x2a);
  section.B64(0x3402a013111e68adULL);
  ASSERT_TRUE(section.GetContents(&contents));
  ASSERT_BYTES(contents, 
               I9(0x2a, 0x34, 0x02, 0xa0, 0x13, 0x11, 0x1e, 0x68, 0xad));
}

TEST_F(Append, B64Label) {
  Label l;
  section.Append(1, 0x2a);
  section.B64(l);
  l = 0x355dbfbb4ac6d57fULL;
  ASSERT_TRUE(section.GetContents(&contents));
  ASSERT_BYTES(contents, 
               I9(0x2a, 0x35, 0x5d, 0xbf, 0xbb, 0x4a, 0xc6, 0xd5, 0x7f));
}

TEST_F(Append, L8) {
  section.Append(1, 0x2a);
  section.L8(0x26U);
  ASSERT_TRUE(section.GetContents(&contents));
  ASSERT_BYTES(contents, I2(0x2a, 0x26));
}

TEST_F(Append, L8Label) {
  Label l;
  section.Append(1, 0x2a);
  section.L8(l);
  l = 0xa8U;
  ASSERT_TRUE(section.GetContents(&contents));
  ASSERT_BYTES(contents, I2(0x2a, 0xa8));
}

TEST_F(Append, L16) {
  section.Append(1, 0x2a);
  section.L16(0xca6dU);
  ASSERT_TRUE(section.GetContents(&contents));
  ASSERT_BYTES(contents, I3(0x2a, 0x6d, 0xca));
}

TEST_F(Append, L16Label) {
  Label l;
  section.Append(1, 0x2a);
  section.L16(l);
  l = 0xd21fU;
  ASSERT_TRUE(section.GetContents(&contents));
  ASSERT_BYTES(contents, I3(0x2a, 0x1f, 0xd2));
}

TEST_F(Append, L32) {
  section.Append(1, 0x2a);
  section.L32(0x558f6181U);
  ASSERT_TRUE(section.GetContents(&contents));
  ASSERT_BYTES(contents, I5(0x2a, 0x81, 0x61, 0x8f, 0x55));
}

TEST_F(Append, L32Label) {
  Label l;
  section.Append(1, 0x2a);
  section.L32(l);
  l = 0x4b810f82U;
  ASSERT_TRUE(section.GetContents(&contents));
  ASSERT_BYTES(contents, I5(0x2a, 0x82, 0x0f, 0x81, 0x4b));
}

TEST_F(Append, L64) {
  section.Append(1, 0x2a);
  section.L64(0x564384f7579515bfULL);
  ASSERT_TRUE(section.GetContents(&contents));
  ASSERT_BYTES(contents, 
               I9(0x2a, 0xbf, 0x15, 0x95, 0x57, 0xf7, 0x84, 0x43, 0x56));
}

TEST_F(Append, L64Label) {
  Label l;
  section.Append(1, 0x2a);
  section.L64(l);
  l = 0x424b1d020667c8dbULL;
  ASSERT_TRUE(section.GetContents(&contents));
  ASSERT_BYTES(contents, 
               I9(0x2a, 0xdb, 0xc8, 0x67, 0x06, 0x02, 0x1d, 0x4b, 0x42));
}

TEST_F(Append, D8Big) {
  section.set_endianness(kBigEndian);
  section.Append(1, 0x2a);
  section.D8(0xe6U);
  ASSERT_TRUE(section.GetContents(&contents));
  ASSERT_BYTES(contents, I2(0x2a, 0xe6));
}

TEST_F(Append, D8BigLabel) {
  Label l;
  section.set_endianness(kBigEndian);
  section.Append(1, 0x2a);
  section.D8(l);
  l = 0xeeU;
  ASSERT_TRUE(section.GetContents(&contents));
  ASSERT_BYTES(contents, I2(0x2a, 0xee));
}

TEST_F(Append, D16Big) {
  section.set_endianness(kBigEndian);
  section.Append(1, 0x2a);
  section.D16(0x83b1U);
  ASSERT_TRUE(section.GetContents(&contents));
  ASSERT_BYTES(contents, I3(0x2a, 0x83, 0xb1));
}

TEST_F(Append, D16BigLabel) {
  Label l;
  section.set_endianness(kBigEndian);
  section.Append(1, 0x2a);
  section.D16(l);
  l = 0x5b55U;
  ASSERT_TRUE(section.GetContents(&contents));
  ASSERT_BYTES(contents, I3(0x2a, 0x5b, 0x55));
}

TEST_F(Append, D32Big) {
  section.set_endianness(kBigEndian);
  section.Append(1, 0x2a);
  section.D32(0xd0b0e431U);
  ASSERT_TRUE(section.GetContents(&contents));
  ASSERT_BYTES(contents, I5(0x2a, 0xd0, 0xb0, 0xe4, 0x31));
}

TEST_F(Append, D32BigLabel) {
  Label l;
  section.set_endianness(kBigEndian);
  section.Append(1, 0x2a);
  section.D32(l);
  l = 0x312fb340U;
  ASSERT_TRUE(section.GetContents(&contents));
  ASSERT_BYTES(contents, I5(0x2a, 0x31, 0x2f, 0xb3, 0x40));
}

TEST_F(Append, D64Big) {
  section.set_endianness(kBigEndian);
  section.Append(1, 0x2a);
  section.D64(0xb109843500dbcb16ULL);
  ASSERT_TRUE(section.GetContents(&contents));
  ASSERT_BYTES(contents, 
               I9(0x2a, 0xb1, 0x09, 0x84, 0x35, 0x00, 0xdb, 0xcb, 0x16));
}

TEST_F(Append, D64BigLabel) {
  Label l;
  section.set_endianness(kBigEndian);
  section.Append(1, 0x2a);
  section.D64(l);
  l = 0x9a0d61b70f671fd7ULL;
  ASSERT_TRUE(section.GetContents(&contents));
  ASSERT_BYTES(contents, 
               I9(0x2a, 0x9a, 0x0d, 0x61, 0xb7, 0x0f, 0x67, 0x1f, 0xd7));
}

TEST_F(Append, D8Little) {
  section.set_endianness(kLittleEndian);
  section.Append(1, 0x2a);
  section.D8(0x42U);
  ASSERT_TRUE(section.GetContents(&contents));
  ASSERT_BYTES(contents, I2(0x2a, 0x42));
}

TEST_F(Append, D8LittleLabel) {
  Label l;
  section.set_endianness(kLittleEndian);
  section.Append(1, 0x2a);
  section.D8(l);
  l = 0x05U;
  ASSERT_TRUE(section.GetContents(&contents));
  ASSERT_BYTES(contents, I2(0x2a, 0x05));
}

TEST_F(Append, D16Little) {
  section.set_endianness(kLittleEndian);
  section.Append(1, 0x2a);
  section.D16(0xc5c5U);
  ASSERT_TRUE(section.GetContents(&contents));
  ASSERT_BYTES(contents, I3(0x2a, 0xc5, 0xc5));
}

TEST_F(Append, D16LittleLabel) {
  Label l;
  section.set_endianness(kLittleEndian);
  section.Append(1, 0x2a);
  section.D16(l);
  l = 0xb620U;
  ASSERT_TRUE(section.GetContents(&contents));
  ASSERT_BYTES(contents, I3(0x2a, 0x20, 0xb6));
}

TEST_F(Append, D32Little) {
  section.set_endianness(kLittleEndian);
  section.Append(1, 0x2a);
  section.D32(0x1a87d0feU);
  ASSERT_TRUE(section.GetContents(&contents));
  ASSERT_BYTES(contents, I5(0x2a, 0xfe, 0xd0, 0x87, 0x1a));
}

TEST_F(Append, D32LittleLabel) {
  Label l;
  section.set_endianness(kLittleEndian);
  section.Append(1, 0x2a);
  section.D32(l);
  l = 0xb8012d6bU;
  ASSERT_TRUE(section.GetContents(&contents));
  ASSERT_BYTES(contents, I5(0x2a, 0x6b, 0x2d, 0x01, 0xb8));
}

TEST_F(Append, D64Little) {
  section.set_endianness(kLittleEndian);
  section.Append(1, 0x2a);
  section.D64(0x42de75c61375a1deULL);
  ASSERT_TRUE(section.GetContents(&contents));
  ASSERT_BYTES(contents, 
               I9(0x2a, 0xde, 0xa1, 0x75, 0x13, 0xc6, 0x75, 0xde, 0x42));
}

TEST_F(Append, D64LittleLabel) {
  Label l;
  section.set_endianness(kLittleEndian);
  section.Append(1, 0x2a);
  section.D64(l);
  l = 0x8b3bececf3fb5312ULL;
  ASSERT_TRUE(section.GetContents(&contents));
  ASSERT_BYTES(contents, 
               I9(0x2a, 0x12, 0x53, 0xfb, 0xf3, 0xec, 0xec, 0x3b, 0x8b));
}

TEST_F(Append, Variety) {
  Label a, b, c, d, e, f, g, h;
  section.Append(kBigEndian, 1, a)
      .Append(kLittleEndian, 8, h)
      .Append(kBigEndian, 1, 0x8bULL)
      .Append(kLittleEndian, 8, 0x0ea56540448f4439ULL)
      .Append(kBigEndian, 2, b)
      .Append(kLittleEndian, 7, g)
      .Append(kBigEndian, 2, 0xcf15ULL)
      .Append(kLittleEndian, 7, 0x29694f04c5724aULL)
      .Append(kBigEndian, 3, c)
      .Append(kLittleEndian, 6, f)
      .Append(kBigEndian, 3, 0x8c3ffdULL)
      .Append(kLittleEndian, 6, 0x6f11ba80187aULL)
      .Append(kBigEndian, 4, d)
      .Append(kLittleEndian, 5, e)
      .Append(kBigEndian, 4, 0x2fda2472ULL)
      .Append(kLittleEndian, 5, 0x0aa02d423fULL)
      .Append(kBigEndian, 5, e)
      .Append(kLittleEndian, 4, d)
      .Append(kBigEndian, 5, 0x53ba432138ULL)
      .Append(kLittleEndian, 4, 0xf139ae60ULL)
      .Append(kBigEndian, 6, f)
      .Append(kLittleEndian, 3, c)
      .Append(kBigEndian, 6, 0x168e436af716ULL)
      .Append(kLittleEndian, 3, 0x3ef189ULL)
      .Append(kBigEndian, 7, g)
      .Append(kLittleEndian, 2, b)
      .Append(kBigEndian, 7, 0xacd4ef233e47d9ULL)
      .Append(kLittleEndian, 2, 0x5311ULL)
      .Append(kBigEndian, 8, h)
      .Append(kLittleEndian, 1, a)
      .Append(kBigEndian, 8, 0x4668d5f1c93637a1ULL)
      .Append(kLittleEndian, 1, 0x65ULL);
  a = 0x79ac9bd8aa256b35ULL;
  b = 0x22d13097ef86c91cULL;
  c = 0xf204968b0a05862fULL;
  d = 0x163177f15a0eb4ecULL;
  e = 0xbd1b0f1d977f2246ULL;
  f = 0x2b0842eee83c6461ULL;
  g = 0x92f4b928a4bf875eULL;
  h = 0x61a199a8f7286ba6ULL;
  ASSERT_EQ(8 * 18U, section.Size());
  ASSERT_TRUE(section.GetContents(&contents));

  static const uint8_t expected[] = {
    0x35,    0xa6, 0x6b, 0x28, 0xf7, 0xa8, 0x99, 0xa1, 0x61,
    0x8b,    0x39, 0x44, 0x8f, 0x44, 0x40, 0x65, 0xa5, 0x0e,
    0xc9, 0x1c,    0x5e, 0x87, 0xbf, 0xa4, 0x28, 0xb9, 0xf4,
    0xcf, 0x15,    0x4a, 0x72, 0xc5, 0x04, 0x4f, 0x69, 0x29,
    0x05, 0x86, 0x2f,    0x61, 0x64, 0x3c, 0xe8, 0xee, 0x42,
    0x8c, 0x3f, 0xfd,    0x7a, 0x18, 0x80, 0xba, 0x11, 0x6f,
    0x5a, 0x0e, 0xb4, 0xec,    0x46, 0x22, 0x7f, 0x97, 0x1d,
    0x2f, 0xda, 0x24, 0x72,    0x3f, 0x42, 0x2d, 0xa0, 0x0a,
    0x1d, 0x97, 0x7f, 0x22, 0x46,    0xec, 0xb4, 0x0e, 0x5a,
    0x53, 0xba, 0x43, 0x21, 0x38,    0x60, 0xae, 0x39, 0xf1,
    0x42, 0xee, 0xe8, 0x3c, 0x64, 0x61,    0x2f, 0x86, 0x05,
    0x16, 0x8e, 0x43, 0x6a, 0xf7, 0x16,    0x89, 0xf1, 0x3e,
    0xf4, 0xb9, 0x28, 0xa4, 0xbf, 0x87, 0x5e,    0x1c, 0xc9,
    0xac, 0xd4, 0xef, 0x23, 0x3e, 0x47, 0xd9,    0x11, 0x53,
    0x61, 0xa1, 0x99, 0xa8, 0xf7, 0x28, 0x6b, 0xa6,    0x35,
    0x46, 0x68, 0xd5, 0xf1, 0xc9, 0x36, 0x37, 0xa1,    0x65,
  };

  ASSERT_TRUE(0 == memcmp(contents.data(), expected, sizeof(expected)));
}

TEST_F(Append, Section) {
  section.Append("murder");
  {
    Section middle;
    middle.Append(" she");
    section.Append(middle);
  }
  section.Append(" wrote");
  EXPECT_EQ(16U, section.Size());
  EXPECT_TRUE(section.GetContents(&contents));
  EXPECT_STREQ(contents.c_str(), "murder she wrote");
}

TEST_F(Append, SectionRefs) {
  section.Append("sugar ");
  Label l;
  {
    Section middle;
    Label m;
    middle.B32(m);
    section.Append(middle);
    m = 0x66726565;
  }
  section.Append(" jazz");
  EXPECT_EQ(15U, section.Size());
  EXPECT_TRUE(section.GetContents(&contents));
  EXPECT_STREQ(contents.c_str(), "sugar free jazz");
}

TEST_F(Append, LEB128_0) {
  section.LEB128(0);
  EXPECT_TRUE(section.GetContents(&contents));
  EXPECT_EQ(string("\0", 1), contents);
}

TEST_F(Append, LEB128_0x3f) {
  section.LEB128(0x3f);
  EXPECT_TRUE(section.GetContents(&contents));
  EXPECT_EQ(string("\x3f", 1), contents);
}

TEST_F(Append, LEB128_0x40) {
  section.LEB128(0x40);
  EXPECT_TRUE(section.GetContents(&contents));
  EXPECT_EQ(string("\xc0\x00", 2), contents);
}

TEST_F(Append, LEB128_0x7f) {
  section.LEB128(0x7f);
  EXPECT_TRUE(section.GetContents(&contents));
  EXPECT_EQ(string("\xff\x00", 2), contents);
}

TEST_F(Append, LEB128_0x80) {
  section.LEB128(0x80);
  EXPECT_TRUE(section.GetContents(&contents));
  EXPECT_EQ(string("\x80\x01", 2), contents);
}

TEST_F(Append, LEB128_0xff) {
  section.LEB128(0xff);
  EXPECT_TRUE(section.GetContents(&contents));
  EXPECT_EQ(string("\xff\x01", 2), contents);
}

TEST_F(Append, LEB128_0x1fff) {
  section.LEB128(0x1fff);
  EXPECT_TRUE(section.GetContents(&contents));
  EXPECT_EQ(string("\xff\x3f", 2), contents);
}

TEST_F(Append, LEB128_0x2000) {
  section.LEB128(0x2000);
  EXPECT_TRUE(section.GetContents(&contents));
  EXPECT_EQ(string("\x80\xc0\x00", 3), contents);
}

TEST_F(Append, LEB128_n1) {
  section.LEB128(-1);
  EXPECT_TRUE(section.GetContents(&contents));
  EXPECT_EQ(string("\x7f", 1), contents);
}

TEST_F(Append, LEB128_n0x40) {
  section.LEB128(-0x40);
  EXPECT_TRUE(section.GetContents(&contents));
  EXPECT_EQ(string("\x40", 1), contents);
}

TEST_F(Append, LEB128_n0x41) {
  section.LEB128(-0x41);
  EXPECT_TRUE(section.GetContents(&contents));
  EXPECT_EQ(string("\xbf\x7f", 2), contents);
}

TEST_F(Append, LEB128_n0x7f) {
  section.LEB128(-0x7f);
  EXPECT_TRUE(section.GetContents(&contents));
  EXPECT_EQ(string("\x81\x7f", 2), contents);
}

TEST_F(Append, LEB128_n0x80) {
  section.LEB128(-0x80);
  EXPECT_TRUE(section.GetContents(&contents));
  EXPECT_EQ(string("\x80\x7f", 2), contents);
}

TEST_F(Append, LEB128_n0x2000) {
  section.LEB128(-0x2000);
  EXPECT_TRUE(section.GetContents(&contents));
  EXPECT_EQ(string("\x80\x40", 2), contents);
}

TEST_F(Append, LEB128_n0x2001) {
  section.LEB128(-0x2001);
  EXPECT_TRUE(section.GetContents(&contents));
  EXPECT_EQ(string("\xff\xbf\x7f", 3), contents);
}

TEST_F(Append,ULEB128_0) {
  section.ULEB128(0);
  EXPECT_TRUE(section.GetContents(&contents));
  EXPECT_EQ(string("\0", 1), contents);
}

TEST_F(Append,ULEB128_1) {
  section.ULEB128(1);
  EXPECT_TRUE(section.GetContents(&contents));
  EXPECT_EQ(string("\x01", 1), contents);
}

TEST_F(Append,ULEB128_0x3f) {
  section.ULEB128(0x3f);
  EXPECT_TRUE(section.GetContents(&contents));
  EXPECT_EQ(string("\x3f", 1), contents);
}

TEST_F(Append,ULEB128_0x40) {
  section.ULEB128(0x40);
  EXPECT_TRUE(section.GetContents(&contents));
  EXPECT_EQ(string("\x40", 1), contents);
}

TEST_F(Append,ULEB128_0x7f) {
  section.ULEB128(0x7f);
  EXPECT_TRUE(section.GetContents(&contents));
  EXPECT_EQ(string("\x7f", 1), contents);
}

TEST_F(Append,ULEB128_0x80) {
  section.ULEB128(0x80);
  EXPECT_TRUE(section.GetContents(&contents));
  EXPECT_EQ(string("\x80\x01", 2), contents);
}

TEST_F(Append,ULEB128_0xff) {
  section.ULEB128(0xff);
  EXPECT_TRUE(section.GetContents(&contents));
  EXPECT_EQ(string("\xff\x01", 2), contents);
}

TEST_F(Append,ULEB128_0x100) {
  section.ULEB128(0x100);
  EXPECT_TRUE(section.GetContents(&contents));
  EXPECT_EQ(string("\x80\x02", 2), contents);
}

TEST_F(Append,ULEB128_0x1fff) {
  section.ULEB128(0x1fff);
  EXPECT_TRUE(section.GetContents(&contents));
  EXPECT_EQ(string("\xff\x3f", 2), contents);
}

TEST_F(Append,ULEB128_0x2000) {
  section.ULEB128(0x2000);
  EXPECT_TRUE(section.GetContents(&contents));
  EXPECT_EQ(string("\x80\x40", 2), contents);
}

TEST_F(Append,ULEB128_0x3fff) {
  section.ULEB128(0x3fff);
  EXPECT_TRUE(section.GetContents(&contents));
  EXPECT_EQ(string("\xff\x7f", 2), contents);
}

TEST_F(Append,ULEB128_0x4000) {
  section.ULEB128(0x4000);
  EXPECT_TRUE(section.GetContents(&contents));
  EXPECT_EQ(string("\x80\x80\x01", 3), contents);
}

TEST_F(Append,ULEB128_12857) {
  section.ULEB128(12857);
  EXPECT_TRUE(section.GetContents(&contents));
  EXPECT_EQ(string("\xb9\x64", 2), contents);
}

TEST_F(Append, LEBChain) {
  section.LEB128(-0x80).ULEB128(12857).Append("*");
  EXPECT_TRUE(section.GetContents(&contents));
  EXPECT_EQ(string("\x80\x7f\xb9\x64*", 5), contents);
}


class GetContents: public SectionFixture, public Test { };

TEST_F(GetContents, Undefined) {
  Label l;
  section.Append(kLittleEndian, 8, l);
  ASSERT_FALSE(section.GetContents(&contents));
}

TEST_F(GetContents, ClearsContents) {
  section.Append((size_t) 10, '*');
  EXPECT_EQ(10U, section.Size());
  EXPECT_TRUE(section.GetContents(&contents));
  EXPECT_EQ(0U, section.Size());
}

TEST_F(GetContents, ClearsReferences) {
  Label l;
  section.Append(kBigEndian, 1, l);
  l = 42;
  ASSERT_TRUE(section.GetContents(&contents));
  ASSERT_BYTES(contents, I1(42));
  ASSERT_TRUE(section.GetContents(&contents)); // should not die
}

class Miscellanea: public SectionFixture, public Test { };

TEST_F(Miscellanea, Clear) {
  section.Append("howdy");
  Label l;
  section.L32(l);
  EXPECT_EQ(9U, section.Size());
  section.Clear();
  EXPECT_EQ(0U, section.Size());
  l = 0x8d231bf0U;
  ASSERT_TRUE(section.GetContents(&contents)); // should not die
}

TEST_F(Miscellanea, Align) {
  section.Append("*");
  EXPECT_EQ(1U, section.Size());
  section.Align(4).Append("*");
  EXPECT_EQ(5U, section.Size());
  section.Append("*").Align(2);
  EXPECT_EQ(6U, section.Size());
}

TEST_F(Miscellanea, AlignPad) {
  section.Append("*");
  EXPECT_EQ(1U, section.Size());
  section.Align(4, ' ').Append("*");
  EXPECT_EQ(5U, section.Size());
  section.Append("*").Align(2, ' ');
  EXPECT_EQ(6U, section.Size());
  ASSERT_TRUE(section.GetContents(&contents));
  ASSERT_EQ(string("*   **"), contents);
}

TEST_F(Miscellanea, StartHereMark) {
  Label m;
  section.Append(42, ' ').Mark(&m).Append(13, '+');
  EXPECT_EQ(42U, m - section.start());
  EXPECT_EQ(42U + 13U, section.Here() - section.start());
  EXPECT_FALSE(section.start().IsKnownConstant());
  EXPECT_FALSE(m.IsKnownConstant());
  EXPECT_FALSE(section.Here().IsKnownConstant());
}

TEST_F(Miscellanea, Endianness) {
  section.set_endianness(kBigEndian);
  EXPECT_EQ(kBigEndian, section.endianness());
  section.set_endianness(kLittleEndian);
  EXPECT_EQ(kLittleEndian, section.endianness());
}
