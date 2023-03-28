// -*- mode: c++ -*-

// Copyright (c) 2010 Google Inc. All Rights Reserved.
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

// dwarf2diehander_unittest.cc: Unit tests for google_breakpad::DIEDispatcher.

#include <string>
#include <utility>

#include "breakpad_googletest_includes.h"

#include "common/dwarf/dwarf2diehandler.h"
#include "common/using_std_string.h"

using std::make_pair;

using ::testing::_;
using ::testing::ContainerEq;
using ::testing::ElementsAreArray;
using ::testing::Eq;
using ::testing::InSequence;
using ::testing::Return;
using ::testing::Sequence;
using ::testing::StrEq;

using dwarf2reader::DIEDispatcher;
using dwarf2reader::DIEHandler;
using dwarf2reader::DwarfAttribute;
using dwarf2reader::DwarfForm;
using dwarf2reader::DwarfTag;
using dwarf2reader::RootDIEHandler;

class MockDIEHandler: public DIEHandler {
 public:
  MOCK_METHOD3(ProcessAttributeUnsigned,
               void(DwarfAttribute, DwarfForm, uint64));
  MOCK_METHOD3(ProcessAttributeSigned,
               void(DwarfAttribute, DwarfForm, int64));
  MOCK_METHOD3(ProcessAttributeReference,
               void(DwarfAttribute, DwarfForm, uint64));
  MOCK_METHOD4(ProcessAttributeBuffer,
               void(DwarfAttribute, DwarfForm, const char *, uint64));
  MOCK_METHOD3(ProcessAttributeString,
               void(DwarfAttribute, DwarfForm, const string &));
  MOCK_METHOD3(ProcessAttributeSignature,
               void(DwarfAttribute, DwarfForm, uint64));
  MOCK_METHOD0(EndAttributes, bool());
  MOCK_METHOD2(FindChildHandler, DIEHandler *(uint64, DwarfTag));
  MOCK_METHOD0(Finish, void());
};

class MockRootDIEHandler: public RootDIEHandler {
 public:
  MOCK_METHOD3(ProcessAttributeUnsigned,
               void(DwarfAttribute, DwarfForm, uint64));
  MOCK_METHOD3(ProcessAttributeSigned,
               void(DwarfAttribute, DwarfForm, int64));
  MOCK_METHOD3(ProcessAttributeReference,
               void(DwarfAttribute, DwarfForm, uint64));
  MOCK_METHOD4(ProcessAttributeBuffer,
               void(DwarfAttribute, DwarfForm, const char *, uint64));
  MOCK_METHOD3(ProcessAttributeString,
               void(DwarfAttribute, DwarfForm, const string &));
  MOCK_METHOD3(ProcessAttributeSignature,
               void(DwarfAttribute, DwarfForm, uint64));
  MOCK_METHOD0(EndAttributes, bool());
  MOCK_METHOD2(FindChildHandler, DIEHandler *(uint64, DwarfTag));
  MOCK_METHOD0(Finish, void());
  MOCK_METHOD5(StartCompilationUnit, bool(uint64, uint8, uint8, uint64, uint8));
  MOCK_METHOD2(StartRootDIE, bool(uint64, DwarfTag));
};

// If the handler elects to skip the compilation unit, the dispatcher
// should tell the reader so.
TEST(Dwarf2DIEHandler, SkipCompilationUnit) {
  Sequence s;
  MockRootDIEHandler mock_root_handler;
  DIEDispatcher die_dispatcher(&mock_root_handler);

  EXPECT_CALL(mock_root_handler,
              StartCompilationUnit(0x8d42aed77cfccf3eLL,
                                   0x89, 0xdc,
                                   0x2ecb4dc778a80f21LL,
                                   0x66))
      .InSequence(s)
      .WillOnce(Return(false));

  EXPECT_FALSE(die_dispatcher.StartCompilationUnit(0x8d42aed77cfccf3eLL,
                                                   0x89, 0xdc,
                                                   0x2ecb4dc778a80f21LL,
                                                   0x66));
}

// If the handler elects to skip the root DIE, the dispatcher should
// tell the reader so.
TEST(Dwarf2DIEHandler, SkipRootDIE) {
  Sequence s;
  MockRootDIEHandler mock_root_handler;
  DIEDispatcher die_dispatcher(&mock_root_handler);

  EXPECT_CALL(mock_root_handler,
              StartCompilationUnit(0xde8994029fc8b999LL, 0xf4, 0x02,
                                   0xb00febffa76e2b2bLL, 0x5c))
      .InSequence(s)
      .WillOnce(Return(true));
  EXPECT_CALL(mock_root_handler,
              StartRootDIE(0x7d08242b4b510cf2LL, (DwarfTag) 0xb4f98da6))
      .InSequence(s)
      .WillOnce(Return(false));

  EXPECT_TRUE(die_dispatcher.StartCompilationUnit(0xde8994029fc8b999LL, 
                                                  0xf4, 0x02,
                                                  0xb00febffa76e2b2bLL, 0x5c));
  EXPECT_FALSE(die_dispatcher.StartDIE(0x7d08242b4b510cf2LL,
                                       (DwarfTag) 0xb4f98da6));
  die_dispatcher.EndDIE(0x7d08242b4b510cf2LL);
}

// If the handler elects to skip the root DIE's children, the
// dispatcher should tell the reader so --- and avoid deleting the
// root handler.
TEST(Dwarf2DIEHandler, SkipRootDIEChildren) {
  MockRootDIEHandler mock_root_handler;
  DIEDispatcher die_dispatcher(&mock_root_handler);

  {
    InSequence s;

    EXPECT_CALL(mock_root_handler,
                StartCompilationUnit(0x15d6897480cc65a7LL, 0x26, 0xa0,
                                     0x09f8bf0767f91675LL, 0xdb))
      .WillOnce(Return(true));
    EXPECT_CALL(mock_root_handler,
                StartRootDIE(0x7d08242b4b510cf2LL, (DwarfTag) 0xb4f98da6))
      .WillOnce(Return(true));
    // Please don't tell me about my children.
    EXPECT_CALL(mock_root_handler, EndAttributes())
      .WillOnce(Return(false));
    EXPECT_CALL(mock_root_handler, Finish())
      .WillOnce(Return());
  }

  EXPECT_TRUE(die_dispatcher.StartCompilationUnit(0x15d6897480cc65a7LL,
                                                  0x26, 0xa0,
                                                  0x09f8bf0767f91675LL, 0xdb));
  EXPECT_TRUE(die_dispatcher.StartDIE(0x7d08242b4b510cf2LL,
                                      (DwarfTag) 0xb4f98da6));
  EXPECT_FALSE(die_dispatcher.StartDIE(0x435150ceedccda18LL,
                                       (DwarfTag) 0xc3a17bba));
  die_dispatcher.EndDIE(0x435150ceedccda18LL);
  die_dispatcher.EndDIE(0x7d08242b4b510cf2LL);
}

// The dispatcher should pass attribute values through to the die
// handler accurately.
TEST(Dwarf2DIEHandler, PassAttributeValues) {
  MockRootDIEHandler mock_root_handler;
  DIEDispatcher die_dispatcher(&mock_root_handler);

  const char buffer[10] = { 0x24, 0x24, 0x35, 0x9a, 0xca,
                            0xcf, 0xa8, 0x84, 0xa7, 0x18 };
  string str = "\xc8\x26\x2e\x0d\xa4\x9c\x37\xd6\xfb\x1d";

  // Set expectations.
  {
    InSequence s;

    // We'll like the compilation unit header.
    EXPECT_CALL(mock_root_handler,
                StartCompilationUnit(0x8d42aed77cfccf3eLL, 0x89, 0xdc,
                                     0x2ecb4dc778a80f21LL, 0x66))
      .WillOnce(Return(true));

    // We'll like the root DIE.
    EXPECT_CALL(mock_root_handler,
                StartRootDIE(0xe2222da01e29f2a9LL, (DwarfTag) 0x9829445c))
      .WillOnce(Return(true));

    // Expect some attribute values.
    EXPECT_CALL(mock_root_handler,
                ProcessAttributeUnsigned((DwarfAttribute) 0x1cc0bfed,
                                         (DwarfForm) 0x424f1468,
                                         0xa592571997facda1ULL))
      .WillOnce(Return());
    EXPECT_CALL(mock_root_handler,
                ProcessAttributeSigned((DwarfAttribute) 0x43694dc9,
                                       (DwarfForm) 0xf6f78901L,
                                       0x92602a4e3bf1f446LL))
      .WillOnce(Return());
    EXPECT_CALL(mock_root_handler,
                ProcessAttributeReference((DwarfAttribute) 0x4033e8cL,
                                          (DwarfForm) 0xf66fbe0bL,
                                          0x50fddef44734fdecULL))
      .WillOnce(Return());
    EXPECT_CALL(mock_root_handler,
                ProcessAttributeBuffer((DwarfAttribute) 0x25d7e0af,
                                       (DwarfForm) 0xe99a539a,
                                       buffer, sizeof(buffer)))
      .WillOnce(Return());
    EXPECT_CALL(mock_root_handler,
                ProcessAttributeString((DwarfAttribute) 0x310ed065,
                                       (DwarfForm) 0x15762fec,
                                       StrEq(str)))
      .WillOnce(Return());
    EXPECT_CALL(mock_root_handler,
                ProcessAttributeSignature((DwarfAttribute) 0x58790d72,
                                          (DwarfForm) 0x4159f138,
                                          0x94682463613e6a5fULL))
      .WillOnce(Return());
    EXPECT_CALL(mock_root_handler, EndAttributes())
      .WillOnce(Return(true));
    EXPECT_CALL(mock_root_handler, FindChildHandler(_, _))
      .Times(0);
    EXPECT_CALL(mock_root_handler, Finish())
      .WillOnce(Return());
  }

  // Drive the dispatcher.

  // Report the CU header.
  EXPECT_TRUE(die_dispatcher.StartCompilationUnit(0x8d42aed77cfccf3eLL,
                                                  0x89, 0xdc,
                                                  0x2ecb4dc778a80f21LL,
                                                  0x66));
  // Report the root DIE.
  EXPECT_TRUE(die_dispatcher.StartDIE(0xe2222da01e29f2a9LL,
                                      (DwarfTag) 0x9829445c));

  // Report some attribute values.
  die_dispatcher.ProcessAttributeUnsigned(0xe2222da01e29f2a9LL,
                                          (DwarfAttribute) 0x1cc0bfed,
                                          (DwarfForm) 0x424f1468,
                                          0xa592571997facda1ULL);
  die_dispatcher.ProcessAttributeSigned(0xe2222da01e29f2a9LL,
                                        (DwarfAttribute) 0x43694dc9,
                                        (DwarfForm) 0xf6f78901,
                                        0x92602a4e3bf1f446LL);
  die_dispatcher.ProcessAttributeReference(0xe2222da01e29f2a9LL,
                                           (DwarfAttribute) 0x4033e8c,
                                           (DwarfForm) 0xf66fbe0b,
                                           0x50fddef44734fdecULL);
  die_dispatcher.ProcessAttributeBuffer(0xe2222da01e29f2a9LL,
                                        (DwarfAttribute) 0x25d7e0af,
                                        (DwarfForm) 0xe99a539a,
                                        buffer, sizeof(buffer));
  die_dispatcher.ProcessAttributeString(0xe2222da01e29f2a9LL,
                                        (DwarfAttribute) 0x310ed065,
                                        (DwarfForm) 0x15762fec,
                                        str);
  die_dispatcher.ProcessAttributeSignature(0xe2222da01e29f2a9LL,
                                           (DwarfAttribute) 0x58790d72,
                                           (DwarfForm) 0x4159f138,
                                           0x94682463613e6a5fULL);

  // Finish the root DIE (and thus the CU).
  die_dispatcher.EndDIE(0xe2222da01e29f2a9LL);
}

TEST(Dwarf2DIEHandler, FindAndSkipChildren) {
  MockRootDIEHandler mock_root_handler;
  MockDIEHandler *mock_child1_handler = new(MockDIEHandler);
  MockDIEHandler *mock_child3_handler = new(MockDIEHandler);
  DIEDispatcher die_dispatcher(&mock_root_handler);

  {
    InSequence s;

    // We'll like the compilation unit header.
    EXPECT_CALL(mock_root_handler,
                StartCompilationUnit(0x9ec1e6d05e434a0eLL, 0xeb, 0x21,
                                     0x47dd3c764275a216LL, 0xa5))
      .WillOnce(Return(true));

    // Root DIE.
    {
      EXPECT_CALL(mock_root_handler,
                  StartRootDIE(0x15f0e06bdfe3c372LL, (DwarfTag) 0xf5d60c59))
        .WillOnce(Return(true));
      EXPECT_CALL(mock_root_handler,
                  ProcessAttributeSigned((DwarfAttribute) 0xf779a642,
                                         (DwarfForm) 0x2cb63027,
                                         0x18e744661769d08fLL))
        .WillOnce(Return());
      EXPECT_CALL(mock_root_handler, EndAttributes())
        .WillOnce(Return(true));

      // First child DIE.
      EXPECT_CALL(mock_root_handler,
                  FindChildHandler(0x149f644f8116fe8cLL,
                                   (DwarfTag) 0xac2cbd8c))
        .WillOnce(Return(mock_child1_handler));
      {
        EXPECT_CALL(*mock_child1_handler,
                    ProcessAttributeSigned((DwarfAttribute) 0xa6fd6f65,
                                           (DwarfForm) 0xe4f64c41,
                                           0x1b04e5444a55fe67LL))
          .WillOnce(Return());
        EXPECT_CALL(*mock_child1_handler, EndAttributes())
          .WillOnce(Return(false));
        // Skip first grandchild DIE and first great-grandchild DIE.
        EXPECT_CALL(*mock_child1_handler, Finish())
          .WillOnce(Return());
      }

      // Second child DIE.  Root handler will decline to return a handler
      // for this child.
      EXPECT_CALL(mock_root_handler,
                  FindChildHandler(0x97412be24875de9dLL,
                                   (DwarfTag) 0x505a068b))
        .WillOnce(Return((DIEHandler *) NULL));

      // Third child DIE.
      EXPECT_CALL(mock_root_handler,
                  FindChildHandler(0x753c964c8ab538aeLL,
                                   (DwarfTag) 0x8c22970e))
        .WillOnce(Return(mock_child3_handler));
      {
        EXPECT_CALL(*mock_child3_handler,
                    ProcessAttributeSigned((DwarfAttribute) 0x4e2b7cfb,
                                           (DwarfForm) 0x610b7ae1,
                                           0x3ea5c609d7d7560fLL))
          .WillOnce(Return());
        EXPECT_CALL(*mock_child3_handler, EndAttributes())
          .WillOnce(Return(true));
        EXPECT_CALL(*mock_child3_handler, Finish())
          .WillOnce(Return());
      }

      EXPECT_CALL(mock_root_handler, Finish())
        .WillOnce(Return());
    }
  }

    
  // Drive the dispatcher.

  // Report the CU header.
  EXPECT_TRUE(die_dispatcher
              .StartCompilationUnit(0x9ec1e6d05e434a0eLL, 0xeb, 0x21,
                                    0x47dd3c764275a216LL, 0xa5));
  // Report the root DIE.
  {
    EXPECT_TRUE(die_dispatcher.StartDIE(0x15f0e06bdfe3c372LL,
                                        (DwarfTag) 0xf5d60c59));
    die_dispatcher.ProcessAttributeSigned(0x15f0e06bdfe3c372LL,
                                          (DwarfAttribute) 0xf779a642,
                                          (DwarfForm) 0x2cb63027,
                                          0x18e744661769d08fLL);

    // First child DIE.
    {
      EXPECT_TRUE(die_dispatcher.StartDIE(0x149f644f8116fe8cLL,
                                          (DwarfTag) 0xac2cbd8c));
      die_dispatcher.ProcessAttributeSigned(0x149f644f8116fe8cLL,
                                            (DwarfAttribute) 0xa6fd6f65,
                                            (DwarfForm) 0xe4f64c41,
                                            0x1b04e5444a55fe67LL);

      // First grandchild DIE.  Will be skipped.
      {
        EXPECT_FALSE(die_dispatcher.StartDIE(0xd68de1ee0bd29419LL,
                                            (DwarfTag) 0x22f05a15));
        // First great-grandchild DIE.  Will be skipped without being
        // mentioned to any handler.
        {
          EXPECT_FALSE(die_dispatcher
                       .StartDIE(0xb3076285d25cac25LL,
                                 (DwarfTag) 0xcff4061b));
          die_dispatcher.EndDIE(0xb3076285d25cac25LL);          
        }
        die_dispatcher.EndDIE(0xd68de1ee0bd29419LL);
      }
      die_dispatcher.EndDIE(0x149f644f8116fe8cLL);
    }

    // Second child DIE.  Root handler will decline to find a handler for it.
    {
      EXPECT_FALSE(die_dispatcher.StartDIE(0x97412be24875de9dLL,
                                           (DwarfTag) 0x505a068b));
      die_dispatcher.EndDIE(0x97412be24875de9dLL);
    }
    
    // Third child DIE.
    {
      EXPECT_TRUE(die_dispatcher.StartDIE(0x753c964c8ab538aeLL,
                                          (DwarfTag) 0x8c22970e));
      die_dispatcher.ProcessAttributeSigned(0x753c964c8ab538aeLL,
                                            (DwarfAttribute) 0x4e2b7cfb,
                                            (DwarfForm) 0x610b7ae1,
                                            0x3ea5c609d7d7560fLL);
      die_dispatcher.EndDIE(0x753c964c8ab538aeLL);
    }
    
    // Finish the root DIE (and thus the CU).
    die_dispatcher.EndDIE(0x15f0e06bdfe3c372LL);
  }
}

// The DIEDispatcher destructor is supposed to delete all handlers on
// the stack, except for the root.
TEST(Dwarf2DIEHandler, FreeHandlersOnStack) {
  MockRootDIEHandler mock_root_handler;
  MockDIEHandler *mock_child_handler = new(MockDIEHandler);
  MockDIEHandler *mock_grandchild_handler = new(MockDIEHandler);

  {
    InSequence s;

    // We'll like the compilation unit header.
    EXPECT_CALL(mock_root_handler,
                StartCompilationUnit(0x87b41ba8381cd71cLL, 0xff, 0x89,
                                     0x76d392ff393ddda2LL, 0xbf))
      .WillOnce(Return(true));

    // Root DIE.
    {
      EXPECT_CALL(mock_root_handler,
                  StartRootDIE(0xbf13b761691ddc91LL, (DwarfTag) 0x98980361))
        .WillOnce(Return(true));
      EXPECT_CALL(mock_root_handler, EndAttributes())
        .WillOnce(Return(true));
      
      // Child DIE.
      EXPECT_CALL(mock_root_handler,
                  FindChildHandler(0x058f09240c5fc8c9LL,
                                   (DwarfTag) 0x898bf0d0))
        .WillOnce(Return(mock_child_handler));
      {
        EXPECT_CALL(*mock_child_handler, EndAttributes())
          .WillOnce(Return(true));

        // Grandchild DIE.
        EXPECT_CALL(*mock_child_handler,
                    FindChildHandler(0x32dc00c9945dc0c8LL,
                                     (DwarfTag) 0x2802d007))
          .WillOnce(Return(mock_grandchild_handler));
        {
          EXPECT_CALL(*mock_grandchild_handler,
                      ProcessAttributeSigned((DwarfAttribute) 0x4e2b7cfb,
                                             (DwarfForm) 0x610b7ae1,
                                             0x3ea5c609d7d7560fLL))
            .WillOnce(Return());

          // At this point, we abandon the traversal, so none of the
          // usual stuff should get called.
          EXPECT_CALL(*mock_grandchild_handler, EndAttributes())
            .Times(0);
          EXPECT_CALL(*mock_grandchild_handler, Finish())
            .Times(0);
        }

        EXPECT_CALL(*mock_child_handler, Finish())
          .Times(0);
      }

      EXPECT_CALL(mock_root_handler, Finish())
        .Times(0);
    }
  }

  // The dispatcher.
  DIEDispatcher die_dispatcher(&mock_root_handler);
  
  // Report the CU header.
  EXPECT_TRUE(die_dispatcher
              .StartCompilationUnit(0x87b41ba8381cd71cLL, 0xff, 0x89,
                                    0x76d392ff393ddda2LL, 0xbf));
  // Report the root DIE.
  {
    EXPECT_TRUE(die_dispatcher.StartDIE(0xbf13b761691ddc91LL,
                                        (DwarfTag) 0x98980361));

    // Child DIE.
    {
      EXPECT_TRUE(die_dispatcher.StartDIE(0x058f09240c5fc8c9LL,
                                          (DwarfTag) 0x898bf0d0));

      // Grandchild DIE.
      {
        EXPECT_TRUE(die_dispatcher.StartDIE(0x32dc00c9945dc0c8LL,
                                            (DwarfTag) 0x2802d007));
        die_dispatcher.ProcessAttributeSigned(0x32dc00c9945dc0c8LL,
                                              (DwarfAttribute) 0x4e2b7cfb,
                                              (DwarfForm) 0x610b7ae1,
                                              0x3ea5c609d7d7560fLL);

        // Stop the traversal abruptly, so that there will still be
        // handlers on the stack when the dispatcher is destructed.

        // No EndDIE call...
      }
      // No EndDIE call...
    }
    // No EndDIE call...
  }
}
