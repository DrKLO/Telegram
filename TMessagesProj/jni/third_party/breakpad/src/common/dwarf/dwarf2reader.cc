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

// CFI reader author: Jim Blandy <jimb@mozilla.com> <jimb@red-bean.com>

// Implementation of dwarf2reader::LineInfo, dwarf2reader::CompilationUnit,
// and dwarf2reader::CallFrameInfo. See dwarf2reader.h for details.

#include "common/dwarf/dwarf2reader.h"

#include <assert.h>
#include <stdint.h>
#include <stdio.h>
#include <string.h>

#include <map>
#include <memory>
#include <stack>
#include <string>
#include <utility>

#include "common/dwarf/bytereader-inl.h"
#include "common/dwarf/bytereader.h"
#include "common/dwarf/line_state_machine.h"
#include "common/using_std_string.h"

namespace dwarf2reader {

CompilationUnit::CompilationUnit(const SectionMap& sections, uint64 offset,
                                 ByteReader* reader, Dwarf2Handler* handler)
    : offset_from_section_start_(offset), reader_(reader),
      sections_(sections), handler_(handler), abbrevs_(NULL),
      string_buffer_(NULL), string_buffer_length_(0) {}

// Read a DWARF2/3 abbreviation section.
// Each abbrev consists of a abbreviation number, a tag, a byte
// specifying whether the tag has children, and a list of
// attribute/form pairs.
// The list of forms is terminated by a 0 for the attribute, and a
// zero for the form.  The entire abbreviation section is terminated
// by a zero for the code.

void CompilationUnit::ReadAbbrevs() {
  if (abbrevs_)
    return;

  // First get the debug_abbrev section.  ".debug_abbrev" is the name
  // recommended in the DWARF spec, and used on Linux;
  // "__debug_abbrev" is the name used in Mac OS X Mach-O files.
  SectionMap::const_iterator iter = sections_.find(".debug_abbrev");
  if (iter == sections_.end())
    iter = sections_.find("__debug_abbrev");
  assert(iter != sections_.end());

  abbrevs_ = new std::vector<Abbrev>;
  abbrevs_->resize(1);

  // The only way to check whether we are reading over the end of the
  // buffer would be to first compute the size of the leb128 data by
  // reading it, then go back and read it again.
  const char* abbrev_start = iter->second.first +
                                      header_.abbrev_offset;
  const char* abbrevptr = abbrev_start;
#ifndef NDEBUG
  const uint64 abbrev_length = iter->second.second - header_.abbrev_offset;
#endif

  while (1) {
    CompilationUnit::Abbrev abbrev;
    size_t len;
    const uint64 number = reader_->ReadUnsignedLEB128(abbrevptr, &len);

    if (number == 0)
      break;
    abbrev.number = number;
    abbrevptr += len;

    assert(abbrevptr < abbrev_start + abbrev_length);
    const uint64 tag = reader_->ReadUnsignedLEB128(abbrevptr, &len);
    abbrevptr += len;
    abbrev.tag = static_cast<enum DwarfTag>(tag);

    assert(abbrevptr < abbrev_start + abbrev_length);
    abbrev.has_children = reader_->ReadOneByte(abbrevptr);
    abbrevptr += 1;

    assert(abbrevptr < abbrev_start + abbrev_length);

    while (1) {
      const uint64 nametemp = reader_->ReadUnsignedLEB128(abbrevptr, &len);
      abbrevptr += len;

      assert(abbrevptr < abbrev_start + abbrev_length);
      const uint64 formtemp = reader_->ReadUnsignedLEB128(abbrevptr, &len);
      abbrevptr += len;
      if (nametemp == 0 && formtemp == 0)
        break;

      const enum DwarfAttribute name =
        static_cast<enum DwarfAttribute>(nametemp);
      const enum DwarfForm form = static_cast<enum DwarfForm>(formtemp);
      abbrev.attributes.push_back(std::make_pair(name, form));
    }
    assert(abbrev.number == abbrevs_->size());
    abbrevs_->push_back(abbrev);
  }
}

// Skips a single DIE's attributes.
const char* CompilationUnit::SkipDIE(const char* start,
                                              const Abbrev& abbrev) {
  for (AttributeList::const_iterator i = abbrev.attributes.begin();
       i != abbrev.attributes.end();
       i++)  {
    start = SkipAttribute(start, i->second);
  }
  return start;
}

// Skips a single attribute form's data.
const char* CompilationUnit::SkipAttribute(const char* start,
                                                    enum DwarfForm form) {
  size_t len;

  switch (form) {
    case DW_FORM_indirect:
      form = static_cast<enum DwarfForm>(reader_->ReadUnsignedLEB128(start,
                                                                     &len));
      start += len;
      return SkipAttribute(start, form);

    case DW_FORM_flag_present:
      return start;
    case DW_FORM_data1:
    case DW_FORM_flag:
    case DW_FORM_ref1:
      return start + 1;
    case DW_FORM_ref2:
    case DW_FORM_data2:
      return start + 2;
    case DW_FORM_ref4:
    case DW_FORM_data4:
      return start + 4;
    case DW_FORM_ref8:
    case DW_FORM_data8:
    case DW_FORM_ref_sig8:
      return start + 8;
    case DW_FORM_string:
      return start + strlen(start) + 1;
    case DW_FORM_udata:
    case DW_FORM_ref_udata:
      reader_->ReadUnsignedLEB128(start, &len);
      return start + len;

    case DW_FORM_sdata:
      reader_->ReadSignedLEB128(start, &len);
      return start + len;
    case DW_FORM_addr:
      return start + reader_->AddressSize();
    case DW_FORM_ref_addr:
      // DWARF2 and 3/4 differ on whether ref_addr is address size or
      // offset size.
      assert(header_.version >= 2);
      if (header_.version == 2) {
        return start + reader_->AddressSize();
      } else if (header_.version >= 3) {
        return start + reader_->OffsetSize();
      }
      break;

    case DW_FORM_block1:
      return start + 1 + reader_->ReadOneByte(start);
    case DW_FORM_block2:
      return start + 2 + reader_->ReadTwoBytes(start);
    case DW_FORM_block4:
      return start + 4 + reader_->ReadFourBytes(start);
    case DW_FORM_block:
    case DW_FORM_exprloc: {
      uint64 size = reader_->ReadUnsignedLEB128(start, &len);
      return start + size + len;
    }
    case DW_FORM_strp:
    case DW_FORM_sec_offset:
      return start + reader_->OffsetSize();
  }
  fprintf(stderr,"Unhandled form type");
  return NULL;
}

// Read a DWARF2/3 header.
// The header is variable length in DWARF3 (and DWARF2 as extended by
// most compilers), and consists of an length field, a version number,
// the offset in the .debug_abbrev section for our abbrevs, and an
// address size.
void CompilationUnit::ReadHeader() {
  const char* headerptr = buffer_;
  size_t initial_length_size;

  assert(headerptr + 4 < buffer_ + buffer_length_);
  const uint64 initial_length
    = reader_->ReadInitialLength(headerptr, &initial_length_size);
  headerptr += initial_length_size;
  header_.length = initial_length;

  assert(headerptr + 2 < buffer_ + buffer_length_);
  header_.version = reader_->ReadTwoBytes(headerptr);
  headerptr += 2;

  assert(headerptr + reader_->OffsetSize() < buffer_ + buffer_length_);
  header_.abbrev_offset = reader_->ReadOffset(headerptr);
  headerptr += reader_->OffsetSize();

  assert(headerptr + 1 < buffer_ + buffer_length_);
  header_.address_size = reader_->ReadOneByte(headerptr);
  reader_->SetAddressSize(header_.address_size);
  headerptr += 1;

  after_header_ = headerptr;

  // This check ensures that we don't have to do checking during the
  // reading of DIEs. header_.length does not include the size of the
  // initial length.
  assert(buffer_ + initial_length_size + header_.length <=
        buffer_ + buffer_length_);
}

uint64 CompilationUnit::Start() {
  // First get the debug_info section.  ".debug_info" is the name
  // recommended in the DWARF spec, and used on Linux; "__debug_info"
  // is the name used in Mac OS X Mach-O files.
  SectionMap::const_iterator iter = sections_.find(".debug_info");
  if (iter == sections_.end())
    iter = sections_.find("__debug_info");
  assert(iter != sections_.end());

  // Set up our buffer
  buffer_ = iter->second.first + offset_from_section_start_;
  buffer_length_ = iter->second.second - offset_from_section_start_;

  // Read the header
  ReadHeader();

  // Figure out the real length from the end of the initial length to
  // the end of the compilation unit, since that is the value we
  // return.
  uint64 ourlength = header_.length;
  if (reader_->OffsetSize() == 8)
    ourlength += 12;
  else
    ourlength += 4;

  // See if the user wants this compilation unit, and if not, just return.
  if (!handler_->StartCompilationUnit(offset_from_section_start_,
                                      reader_->AddressSize(),
                                      reader_->OffsetSize(),
                                      header_.length,
                                      header_.version))
    return ourlength;

  // Otherwise, continue by reading our abbreviation entries.
  ReadAbbrevs();

  // Set the string section if we have one.  ".debug_str" is the name
  // recommended in the DWARF spec, and used on Linux; "__debug_str"
  // is the name used in Mac OS X Mach-O files.
  iter = sections_.find(".debug_str");
  if (iter == sections_.end())
    iter = sections_.find("__debug_str");
  if (iter != sections_.end()) {
    string_buffer_ = iter->second.first;
    string_buffer_length_ = iter->second.second;
  }

  // Now that we have our abbreviations, start processing DIE's.
  ProcessDIEs();

  return ourlength;
}

// If one really wanted, you could merge SkipAttribute and
// ProcessAttribute
// This is all boring data manipulation and calling of the handler.
const char* CompilationUnit::ProcessAttribute(
    uint64 dieoffset, const char* start, enum DwarfAttribute attr,
    enum DwarfForm form) {
  size_t len;

  switch (form) {
    // DW_FORM_indirect is never used because it is such a space
    // waster.
    case DW_FORM_indirect:
      form = static_cast<enum DwarfForm>(reader_->ReadUnsignedLEB128(start,
                                                                     &len));
      start += len;
      return ProcessAttribute(dieoffset, start, attr, form);

    case DW_FORM_flag_present:
      handler_->ProcessAttributeUnsigned(dieoffset, attr, form, 1);
      return start;
    case DW_FORM_data1:
    case DW_FORM_flag:
      handler_->ProcessAttributeUnsigned(dieoffset, attr, form,
                                         reader_->ReadOneByte(start));
      return start + 1;
    case DW_FORM_data2:
      handler_->ProcessAttributeUnsigned(dieoffset, attr, form,
                                         reader_->ReadTwoBytes(start));
      return start + 2;
    case DW_FORM_data4:
      handler_->ProcessAttributeUnsigned(dieoffset, attr, form,
                                         reader_->ReadFourBytes(start));
      return start + 4;
    case DW_FORM_data8:
      handler_->ProcessAttributeUnsigned(dieoffset, attr, form,
                                         reader_->ReadEightBytes(start));
      return start + 8;
    case DW_FORM_string: {
      const char* str = start;
      handler_->ProcessAttributeString(dieoffset, attr, form,
                                       str);
      return start + strlen(str) + 1;
    }
    case DW_FORM_udata:
      handler_->ProcessAttributeUnsigned(dieoffset, attr, form,
                                         reader_->ReadUnsignedLEB128(start,
                                                                     &len));
      return start + len;

    case DW_FORM_sdata:
      handler_->ProcessAttributeSigned(dieoffset, attr, form,
                                      reader_->ReadSignedLEB128(start, &len));
      return start + len;
    case DW_FORM_addr:
      handler_->ProcessAttributeUnsigned(dieoffset, attr, form,
                                         reader_->ReadAddress(start));
      return start + reader_->AddressSize();
    case DW_FORM_sec_offset:
      handler_->ProcessAttributeUnsigned(dieoffset, attr, form,
                                         reader_->ReadOffset(start));
      return start + reader_->OffsetSize();

    case DW_FORM_ref1:
      handler_->ProcessAttributeReference(dieoffset, attr, form,
                                          reader_->ReadOneByte(start)
                                          + offset_from_section_start_);
      return start + 1;
    case DW_FORM_ref2:
      handler_->ProcessAttributeReference(dieoffset, attr, form,
                                          reader_->ReadTwoBytes(start)
                                          + offset_from_section_start_);
      return start + 2;
    case DW_FORM_ref4:
      handler_->ProcessAttributeReference(dieoffset, attr, form,
                                          reader_->ReadFourBytes(start)
                                          + offset_from_section_start_);
      return start + 4;
    case DW_FORM_ref8:
      handler_->ProcessAttributeReference(dieoffset, attr, form,
                                          reader_->ReadEightBytes(start)
                                          + offset_from_section_start_);
      return start + 8;
    case DW_FORM_ref_udata:
      handler_->ProcessAttributeReference(dieoffset, attr, form,
                                          reader_->ReadUnsignedLEB128(start,
                                                                      &len)
                                          + offset_from_section_start_);
      return start + len;
    case DW_FORM_ref_addr:
      // DWARF2 and 3/4 differ on whether ref_addr is address size or
      // offset size.
      assert(header_.version >= 2);
      if (header_.version == 2) {
        handler_->ProcessAttributeReference(dieoffset, attr, form,
                                            reader_->ReadAddress(start));
        return start + reader_->AddressSize();
      } else if (header_.version >= 3) {
        handler_->ProcessAttributeReference(dieoffset, attr, form,
                                            reader_->ReadOffset(start));
        return start + reader_->OffsetSize();
      }
      break;
    case DW_FORM_ref_sig8:
      handler_->ProcessAttributeSignature(dieoffset, attr, form,
                                          reader_->ReadEightBytes(start));
      return start + 8;

    case DW_FORM_block1: {
      uint64 datalen = reader_->ReadOneByte(start);
      handler_->ProcessAttributeBuffer(dieoffset, attr, form, start + 1,
                                       datalen);
      return start + 1 + datalen;
    }
    case DW_FORM_block2: {
      uint64 datalen = reader_->ReadTwoBytes(start);
      handler_->ProcessAttributeBuffer(dieoffset, attr, form, start + 2,
                                       datalen);
      return start + 2 + datalen;
    }
    case DW_FORM_block4: {
      uint64 datalen = reader_->ReadFourBytes(start);
      handler_->ProcessAttributeBuffer(dieoffset, attr, form, start + 4,
                                       datalen);
      return start + 4 + datalen;
    }
    case DW_FORM_block:
    case DW_FORM_exprloc: {
      uint64 datalen = reader_->ReadUnsignedLEB128(start, &len);
      handler_->ProcessAttributeBuffer(dieoffset, attr, form, start + len,
                                       datalen);
      return start + datalen + len;
    }
    case DW_FORM_strp: {
      assert(string_buffer_ != NULL);

      const uint64 offset = reader_->ReadOffset(start);
      assert(string_buffer_ + offset < string_buffer_ + string_buffer_length_);

      const char* str = string_buffer_ + offset;
      handler_->ProcessAttributeString(dieoffset, attr, form,
                                       str);
      return start + reader_->OffsetSize();
    }
  }
  fprintf(stderr, "Unhandled form type\n");
  return NULL;
}

const char* CompilationUnit::ProcessDIE(uint64 dieoffset,
                                                 const char* start,
                                                 const Abbrev& abbrev) {
  for (AttributeList::const_iterator i = abbrev.attributes.begin();
       i != abbrev.attributes.end();
       i++)  {
    start = ProcessAttribute(dieoffset, start, i->first, i->second);
  }
  return start;
}

void CompilationUnit::ProcessDIEs() {
  const char* dieptr = after_header_;
  size_t len;

  // lengthstart is the place the length field is based on.
  // It is the point in the header after the initial length field
  const char* lengthstart = buffer_;

  // In 64 bit dwarf, the initial length is 12 bytes, because of the
  // 0xffffffff at the start.
  if (reader_->OffsetSize() == 8)
    lengthstart += 12;
  else
    lengthstart += 4;

  std::stack<uint64> die_stack;
  
  while (dieptr < (lengthstart + header_.length)) {
    // We give the user the absolute offset from the beginning of
    // debug_info, since they need it to deal with ref_addr forms.
    uint64 absolute_offset = (dieptr - buffer_) + offset_from_section_start_;

    uint64 abbrev_num = reader_->ReadUnsignedLEB128(dieptr, &len);

    dieptr += len;

    // Abbrev == 0 represents the end of a list of children, or padding
    // at the end of the compilation unit.
    if (abbrev_num == 0) {
      if (die_stack.size() == 0)
        // If it is padding, then we are done with the compilation unit's DIEs.
        return;
      const uint64 offset = die_stack.top();
      die_stack.pop();
      handler_->EndDIE(offset);
      continue;
    }

    const Abbrev& abbrev = abbrevs_->at(static_cast<size_t>(abbrev_num));
    const enum DwarfTag tag = abbrev.tag;
    if (!handler_->StartDIE(absolute_offset, tag)) {
      dieptr = SkipDIE(dieptr, abbrev);
    } else {
      dieptr = ProcessDIE(absolute_offset, dieptr, abbrev);
    }

    if (abbrev.has_children) {
      die_stack.push(absolute_offset);
    } else {
      handler_->EndDIE(absolute_offset);
    }
  }
}

LineInfo::LineInfo(const char* buffer, uint64 buffer_length,
                   ByteReader* reader, LineInfoHandler* handler):
    handler_(handler), reader_(reader), buffer_(buffer),
    buffer_length_(buffer_length) {
  header_.std_opcode_lengths = NULL;
}

uint64 LineInfo::Start() {
  ReadHeader();
  ReadLines();
  return after_header_ - buffer_;
}

// The header for a debug_line section is mildly complicated, because
// the line info is very tightly encoded.
void LineInfo::ReadHeader() {
  const char* lineptr = buffer_;
  size_t initial_length_size;

  const uint64 initial_length
    = reader_->ReadInitialLength(lineptr, &initial_length_size);

  lineptr += initial_length_size;
  header_.total_length = initial_length;
  assert(buffer_ + initial_length_size + header_.total_length <=
        buffer_ + buffer_length_);

  // Address size *must* be set by CU ahead of time.
  assert(reader_->AddressSize() != 0);

  header_.version = reader_->ReadTwoBytes(lineptr);
  lineptr += 2;

  header_.prologue_length = reader_->ReadOffset(lineptr);
  lineptr += reader_->OffsetSize();

  header_.min_insn_length = reader_->ReadOneByte(lineptr);
  lineptr += 1;

  header_.default_is_stmt = reader_->ReadOneByte(lineptr);
  lineptr += 1;

  header_.line_base = *reinterpret_cast<const int8*>(lineptr);
  lineptr += 1;

  header_.line_range = reader_->ReadOneByte(lineptr);
  lineptr += 1;

  header_.opcode_base = reader_->ReadOneByte(lineptr);
  lineptr += 1;

  header_.std_opcode_lengths = new std::vector<unsigned char>;
  header_.std_opcode_lengths->resize(header_.opcode_base + 1);
  (*header_.std_opcode_lengths)[0] = 0;
  for (int i = 1; i < header_.opcode_base; i++) {
    (*header_.std_opcode_lengths)[i] = reader_->ReadOneByte(lineptr);
    lineptr += 1;
  }

  // It is legal for the directory entry table to be empty.
  if (*lineptr) {
    uint32 dirindex = 1;
    while (*lineptr) {
      const char* dirname = lineptr;
      handler_->DefineDir(dirname, dirindex);
      lineptr += strlen(dirname) + 1;
      dirindex++;
    }
  }
  lineptr++;

  // It is also legal for the file entry table to be empty.
  if (*lineptr) {
    uint32 fileindex = 1;
    size_t len;
    while (*lineptr) {
      const char* filename = lineptr;
      lineptr += strlen(filename) + 1;

      uint64 dirindex = reader_->ReadUnsignedLEB128(lineptr, &len);
      lineptr += len;

      uint64 mod_time = reader_->ReadUnsignedLEB128(lineptr, &len);
      lineptr += len;

      uint64 filelength = reader_->ReadUnsignedLEB128(lineptr, &len);
      lineptr += len;
      handler_->DefineFile(filename, fileindex, static_cast<uint32>(dirindex), 
                           mod_time, filelength);
      fileindex++;
    }
  }
  lineptr++;

  after_header_ = lineptr;
}

/* static */
bool LineInfo::ProcessOneOpcode(ByteReader* reader,
                                LineInfoHandler* handler,
                                const struct LineInfoHeader &header,
                                const char* start,
                                struct LineStateMachine* lsm,
                                size_t* len,
                                uintptr pc,
                                bool *lsm_passes_pc) {
  size_t oplen = 0;
  size_t templen;
  uint8 opcode = reader->ReadOneByte(start);
  oplen++;
  start++;

  // If the opcode is great than the opcode_base, it is a special
  // opcode. Most line programs consist mainly of special opcodes.
  if (opcode >= header.opcode_base) {
    opcode -= header.opcode_base;
    const int64 advance_address = (opcode / header.line_range)
                                  * header.min_insn_length;
    const int32 advance_line = (opcode % header.line_range)
                               + header.line_base;

    // Check if the lsm passes "pc". If so, mark it as passed.
    if (lsm_passes_pc &&
        lsm->address <= pc && pc < lsm->address + advance_address) {
      *lsm_passes_pc = true;
    }

    lsm->address += advance_address;
    lsm->line_num += advance_line;
    lsm->basic_block = true;
    *len = oplen;
    return true;
  }

  // Otherwise, we have the regular opcodes
  switch (opcode) {
    case DW_LNS_copy: {
      lsm->basic_block = false;
      *len = oplen;
      return true;
    }

    case DW_LNS_advance_pc: {
      uint64 advance_address = reader->ReadUnsignedLEB128(start, &templen);
      oplen += templen;

      // Check if the lsm passes "pc". If so, mark it as passed.
      if (lsm_passes_pc && lsm->address <= pc &&
          pc < lsm->address + header.min_insn_length * advance_address) {
        *lsm_passes_pc = true;
      }

      lsm->address += header.min_insn_length * advance_address;
    }
      break;
    case DW_LNS_advance_line: {
      const int64 advance_line = reader->ReadSignedLEB128(start, &templen);
      oplen += templen;
      lsm->line_num += static_cast<int32>(advance_line);

      // With gcc 4.2.1, we can get the line_no here for the first time
      // since DW_LNS_advance_line is called after DW_LNE_set_address is
      // called. So we check if the lsm passes "pc" here, not in
      // DW_LNE_set_address.
      if (lsm_passes_pc && lsm->address == pc) {
        *lsm_passes_pc = true;
      }
    }
      break;
    case DW_LNS_set_file: {
      const uint64 fileno = reader->ReadUnsignedLEB128(start, &templen);
      oplen += templen;
      lsm->file_num = static_cast<uint32>(fileno);
    }
      break;
    case DW_LNS_set_column: {
      const uint64 colno = reader->ReadUnsignedLEB128(start, &templen);
      oplen += templen;
      lsm->column_num = static_cast<uint32>(colno);
    }
      break;
    case DW_LNS_negate_stmt: {
      lsm->is_stmt = !lsm->is_stmt;
    }
      break;
    case DW_LNS_set_basic_block: {
      lsm->basic_block = true;
    }
      break;
    case DW_LNS_fixed_advance_pc: {
      const uint16 advance_address = reader->ReadTwoBytes(start);
      oplen += 2;

      // Check if the lsm passes "pc". If so, mark it as passed.
      if (lsm_passes_pc &&
          lsm->address <= pc && pc < lsm->address + advance_address) {
        *lsm_passes_pc = true;
      }

      lsm->address += advance_address;
    }
      break;
    case DW_LNS_const_add_pc: {
      const int64 advance_address = header.min_insn_length
                                    * ((255 - header.opcode_base)
                                       / header.line_range);

      // Check if the lsm passes "pc". If so, mark it as passed.
      if (lsm_passes_pc &&
          lsm->address <= pc && pc < lsm->address + advance_address) {
        *lsm_passes_pc = true;
      }

      lsm->address += advance_address;
    }
      break;
    case DW_LNS_extended_op: {
      const uint64 extended_op_len = reader->ReadUnsignedLEB128(start,
                                                                &templen);
      start += templen;
      oplen += templen + extended_op_len;

      const uint64 extended_op = reader->ReadOneByte(start);
      start++;

      switch (extended_op) {
        case DW_LNE_end_sequence: {
          lsm->end_sequence = true;
          *len = oplen;
          return true;
        }
          break;
        case DW_LNE_set_address: {
          // With gcc 4.2.1, we cannot tell the line_no here since
          // DW_LNE_set_address is called before DW_LNS_advance_line is
          // called.  So we do not check if the lsm passes "pc" here.  See
          // also the comment in DW_LNS_advance_line.
          uint64 address = reader->ReadAddress(start);
          lsm->address = address;
        }
          break;
        case DW_LNE_define_file: {
          const char* filename  = start;

          templen = strlen(filename) + 1;
          start += templen;

          uint64 dirindex = reader->ReadUnsignedLEB128(start, &templen);
          oplen += templen;

          const uint64 mod_time = reader->ReadUnsignedLEB128(start,
                                                             &templen);
          oplen += templen;

          const uint64 filelength = reader->ReadUnsignedLEB128(start,
                                                               &templen);
          oplen += templen;

          if (handler) {
            handler->DefineFile(filename, -1, static_cast<uint32>(dirindex), 
                                mod_time, filelength);
          }
        }
          break;
      }
    }
      break;

    default: {
      // Ignore unknown opcode  silently
      if (header.std_opcode_lengths) {
        for (int i = 0; i < (*header.std_opcode_lengths)[opcode]; i++) {
          reader->ReadUnsignedLEB128(start, &templen);
          start += templen;
          oplen += templen;
        }
      }
    }
      break;
  }
  *len = oplen;
  return false;
}

void LineInfo::ReadLines() {
  struct LineStateMachine lsm;

  // lengthstart is the place the length field is based on.
  // It is the point in the header after the initial length field
  const char* lengthstart = buffer_;

  // In 64 bit dwarf, the initial length is 12 bytes, because of the
  // 0xffffffff at the start.
  if (reader_->OffsetSize() == 8)
    lengthstart += 12;
  else
    lengthstart += 4;

  const char* lineptr = after_header_;
  lsm.Reset(header_.default_is_stmt);

  // The LineInfoHandler interface expects each line's length along
  // with its address, but DWARF only provides addresses (sans
  // length), and an end-of-sequence address; one infers the length
  // from the next address. So we report a line only when we get the
  // next line's address, or the end-of-sequence address.
  bool have_pending_line = false;
  uint64 pending_address = 0;
  uint32 pending_file_num = 0, pending_line_num = 0, pending_column_num = 0;

  while (lineptr < lengthstart + header_.total_length) {
    size_t oplength;
    bool add_row = ProcessOneOpcode(reader_, handler_, header_,
                                    lineptr, &lsm, &oplength, (uintptr)-1,
                                    NULL);
    if (add_row) {
      if (have_pending_line)
        handler_->AddLine(pending_address, lsm.address - pending_address,
                          pending_file_num, pending_line_num,
                          pending_column_num);
      if (lsm.end_sequence) {
        lsm.Reset(header_.default_is_stmt);      
        have_pending_line = false;
      } else {
        pending_address = lsm.address;
        pending_file_num = lsm.file_num;
        pending_line_num = lsm.line_num;
        pending_column_num = lsm.column_num;
        have_pending_line = true;
      }
    }
    lineptr += oplength;
  }

  after_header_ = lengthstart + header_.total_length;
}

// A DWARF rule for recovering the address or value of a register, or
// computing the canonical frame address. There is one subclass of this for
// each '*Rule' member function in CallFrameInfo::Handler.
//
// It's annoying that we have to handle Rules using pointers (because
// the concrete instances can have an arbitrary size). They're small,
// so it would be much nicer if we could just handle them by value
// instead of fretting about ownership and destruction.
//
// It seems like all these could simply be instances of std::tr1::bind,
// except that we need instances to be EqualityComparable, too.
//
// This could logically be nested within State, but then the qualified names
// get horrendous.
class CallFrameInfo::Rule {
 public:
  virtual ~Rule() { }

  // Tell HANDLER that, at ADDRESS in the program, REGISTER can be
  // recovered using this rule. If REGISTER is kCFARegister, then this rule
  // describes how to compute the canonical frame address. Return what the
  // HANDLER member function returned.
  virtual bool Handle(Handler *handler,
                      uint64 address, int register) const = 0;

  // Equality on rules. We use these to decide which rules we need
  // to report after a DW_CFA_restore_state instruction.
  virtual bool operator==(const Rule &rhs) const = 0;

  bool operator!=(const Rule &rhs) const { return ! (*this == rhs); }

  // Return a pointer to a copy of this rule.
  virtual Rule *Copy() const = 0;

  // If this is a base+offset rule, change its base register to REG.
  // Otherwise, do nothing. (Ugly, but required for DW_CFA_def_cfa_register.)
  virtual void SetBaseRegister(unsigned reg) { }

  // If this is a base+offset rule, change its offset to OFFSET. Otherwise,
  // do nothing. (Ugly, but required for DW_CFA_def_cfa_offset.)
  virtual void SetOffset(long long offset) { }
};

// Rule: the value the register had in the caller cannot be recovered.
class CallFrameInfo::UndefinedRule: public CallFrameInfo::Rule {
 public:
  UndefinedRule() { }
  ~UndefinedRule() { }
  bool Handle(Handler *handler, uint64 address, int reg) const {
    return handler->UndefinedRule(address, reg);
  }
  bool operator==(const Rule &rhs) const {
    // dynamic_cast is allowed by the Google C++ Style Guide, if the use has
    // been carefully considered; cheap RTTI-like workarounds are forbidden.
    const UndefinedRule *our_rhs = dynamic_cast<const UndefinedRule *>(&rhs);
    return (our_rhs != NULL);
  }
  Rule *Copy() const { return new UndefinedRule(*this); }
};

// Rule: the register's value is the same as that it had in the caller.
class CallFrameInfo::SameValueRule: public CallFrameInfo::Rule {
 public:
  SameValueRule() { }
  ~SameValueRule() { }
  bool Handle(Handler *handler, uint64 address, int reg) const {
    return handler->SameValueRule(address, reg);
  }
  bool operator==(const Rule &rhs) const {
    // dynamic_cast is allowed by the Google C++ Style Guide, if the use has
    // been carefully considered; cheap RTTI-like workarounds are forbidden.
    const SameValueRule *our_rhs = dynamic_cast<const SameValueRule *>(&rhs);
    return (our_rhs != NULL);
  }
  Rule *Copy() const { return new SameValueRule(*this); }
};

// Rule: the register is saved at OFFSET from BASE_REGISTER.  BASE_REGISTER
// may be CallFrameInfo::Handler::kCFARegister.
class CallFrameInfo::OffsetRule: public CallFrameInfo::Rule {
 public:
  OffsetRule(int base_register, long offset)
      : base_register_(base_register), offset_(offset) { }
  ~OffsetRule() { }
  bool Handle(Handler *handler, uint64 address, int reg) const {
    return handler->OffsetRule(address, reg, base_register_, offset_);
  }
  bool operator==(const Rule &rhs) const {
    // dynamic_cast is allowed by the Google C++ Style Guide, if the use has
    // been carefully considered; cheap RTTI-like workarounds are forbidden.
    const OffsetRule *our_rhs = dynamic_cast<const OffsetRule *>(&rhs);
    return (our_rhs &&
            base_register_ == our_rhs->base_register_ &&
            offset_ == our_rhs->offset_);
  }
  Rule *Copy() const { return new OffsetRule(*this); }
  // We don't actually need SetBaseRegister or SetOffset here, since they
  // are only ever applied to CFA rules, for DW_CFA_def_cfa_offset, and it
  // doesn't make sense to use OffsetRule for computing the CFA: it
  // computes the address at which a register is saved, not a value.
 private:
  int base_register_;
  long offset_;
};

// Rule: the value the register had in the caller is the value of
// BASE_REGISTER plus offset. BASE_REGISTER may be
// CallFrameInfo::Handler::kCFARegister.
class CallFrameInfo::ValOffsetRule: public CallFrameInfo::Rule {
 public:
  ValOffsetRule(int base_register, long offset)
      : base_register_(base_register), offset_(offset) { }
  ~ValOffsetRule() { }
  bool Handle(Handler *handler, uint64 address, int reg) const {
    return handler->ValOffsetRule(address, reg, base_register_, offset_);
  }
  bool operator==(const Rule &rhs) const {
    // dynamic_cast is allowed by the Google C++ Style Guide, if the use has
    // been carefully considered; cheap RTTI-like workarounds are forbidden.
    const ValOffsetRule *our_rhs = dynamic_cast<const ValOffsetRule *>(&rhs);
    return (our_rhs &&
            base_register_ == our_rhs->base_register_ &&
            offset_ == our_rhs->offset_);
  }
  Rule *Copy() const { return new ValOffsetRule(*this); }
  void SetBaseRegister(unsigned reg) { base_register_ = reg; }
  void SetOffset(long long offset) { offset_ = offset; }
 private:
  int base_register_;
  long offset_;
};

// Rule: the register has been saved in another register REGISTER_NUMBER_.
class CallFrameInfo::RegisterRule: public CallFrameInfo::Rule {
 public:
  explicit RegisterRule(int register_number)
      : register_number_(register_number) { }
  ~RegisterRule() { }
  bool Handle(Handler *handler, uint64 address, int reg) const {
    return handler->RegisterRule(address, reg, register_number_);
  }
  bool operator==(const Rule &rhs) const {
    // dynamic_cast is allowed by the Google C++ Style Guide, if the use has
    // been carefully considered; cheap RTTI-like workarounds are forbidden.
    const RegisterRule *our_rhs = dynamic_cast<const RegisterRule *>(&rhs);
    return (our_rhs && register_number_ == our_rhs->register_number_);
  }
  Rule *Copy() const { return new RegisterRule(*this); }
 private:
  int register_number_;
};

// Rule: EXPRESSION evaluates to the address at which the register is saved.
class CallFrameInfo::ExpressionRule: public CallFrameInfo::Rule {
 public:
  explicit ExpressionRule(const string &expression)
      : expression_(expression) { }
  ~ExpressionRule() { }
  bool Handle(Handler *handler, uint64 address, int reg) const {
    return handler->ExpressionRule(address, reg, expression_);
  }
  bool operator==(const Rule &rhs) const {
    // dynamic_cast is allowed by the Google C++ Style Guide, if the use has
    // been carefully considered; cheap RTTI-like workarounds are forbidden.
    const ExpressionRule *our_rhs = dynamic_cast<const ExpressionRule *>(&rhs);
    return (our_rhs && expression_ == our_rhs->expression_);
  }
  Rule *Copy() const { return new ExpressionRule(*this); }
 private:
  string expression_;
};

// Rule: EXPRESSION evaluates to the address at which the register is saved.
class CallFrameInfo::ValExpressionRule: public CallFrameInfo::Rule {
 public:
  explicit ValExpressionRule(const string &expression)
      : expression_(expression) { }
  ~ValExpressionRule() { }
  bool Handle(Handler *handler, uint64 address, int reg) const {
    return handler->ValExpressionRule(address, reg, expression_);
  }
  bool operator==(const Rule &rhs) const {
    // dynamic_cast is allowed by the Google C++ Style Guide, if the use has
    // been carefully considered; cheap RTTI-like workarounds are forbidden.
    const ValExpressionRule *our_rhs =
        dynamic_cast<const ValExpressionRule *>(&rhs);
    return (our_rhs && expression_ == our_rhs->expression_);
  }
  Rule *Copy() const { return new ValExpressionRule(*this); }
 private:
  string expression_;
};

// A map from register numbers to rules.
class CallFrameInfo::RuleMap {
 public:
  RuleMap() : cfa_rule_(NULL) { }
  RuleMap(const RuleMap &rhs) : cfa_rule_(NULL) { *this = rhs; }
  ~RuleMap() { Clear(); }

  RuleMap &operator=(const RuleMap &rhs);

  // Set the rule for computing the CFA to RULE. Take ownership of RULE.
  void SetCFARule(Rule *rule) { delete cfa_rule_; cfa_rule_ = rule; }

  // Return the current CFA rule. Unlike RegisterRule, this RuleMap retains
  // ownership of the rule. We use this for DW_CFA_def_cfa_offset and
  // DW_CFA_def_cfa_register, and for detecting references to the CFA before
  // a rule for it has been established.
  Rule *CFARule() const { return cfa_rule_; }

  // Return the rule for REG, or NULL if there is none. The caller takes
  // ownership of the result.
  Rule *RegisterRule(int reg) const;

  // Set the rule for computing REG to RULE. Take ownership of RULE.
  void SetRegisterRule(int reg, Rule *rule);

  // Make all the appropriate calls to HANDLER as if we were changing from
  // this RuleMap to NEW_RULES at ADDRESS. We use this to implement
  // DW_CFA_restore_state, where lots of rules can change simultaneously.
  // Return true if all handlers returned true; otherwise, return false.
  bool HandleTransitionTo(Handler *handler, uint64 address,
                          const RuleMap &new_rules) const;

 private:
  // A map from register numbers to Rules.
  typedef std::map<int, Rule *> RuleByNumber;

  // Remove all register rules and clear cfa_rule_.
  void Clear();

  // The rule for computing the canonical frame address. This RuleMap owns
  // this rule.
  Rule *cfa_rule_;

  // A map from register numbers to postfix expressions to recover
  // their values. This RuleMap owns the Rules the map refers to.
  RuleByNumber registers_;
};

CallFrameInfo::RuleMap &CallFrameInfo::RuleMap::operator=(const RuleMap &rhs) {
  Clear();
  // Since each map owns the rules it refers to, assignment must copy them.
  if (rhs.cfa_rule_) cfa_rule_ = rhs.cfa_rule_->Copy();
  for (RuleByNumber::const_iterator it = rhs.registers_.begin();
       it != rhs.registers_.end(); it++)
    registers_[it->first] = it->second->Copy();
  return *this;
}

CallFrameInfo::Rule *CallFrameInfo::RuleMap::RegisterRule(int reg) const {
  assert(reg != Handler::kCFARegister);
  RuleByNumber::const_iterator it = registers_.find(reg);
  if (it != registers_.end())
    return it->second->Copy();
  else
    return NULL;
}

void CallFrameInfo::RuleMap::SetRegisterRule(int reg, Rule *rule) {
  assert(reg != Handler::kCFARegister);
  assert(rule);
  Rule **slot = &registers_[reg];
  delete *slot;
  *slot = rule;
}

bool CallFrameInfo::RuleMap::HandleTransitionTo(
    Handler *handler,
    uint64 address,
    const RuleMap &new_rules) const {
  // Transition from cfa_rule_ to new_rules.cfa_rule_.
  if (cfa_rule_ && new_rules.cfa_rule_) {
    if (*cfa_rule_ != *new_rules.cfa_rule_ &&
        !new_rules.cfa_rule_->Handle(handler, address,
                                     Handler::kCFARegister))
      return false;
  } else if (cfa_rule_) {
    // this RuleMap has a CFA rule but new_rules doesn't.
    // CallFrameInfo::Handler has no way to handle this --- and shouldn't;
    // it's garbage input. The instruction interpreter should have
    // detected this and warned, so take no action here.
  } else if (new_rules.cfa_rule_) {
    // This shouldn't be possible: NEW_RULES is some prior state, and
    // there's no way to remove entries.
    assert(0);
  } else {
    // Both CFA rules are empty.  No action needed.
  }

  // Traverse the two maps in order by register number, and report
  // whatever differences we find.
  RuleByNumber::const_iterator old_it = registers_.begin();
  RuleByNumber::const_iterator new_it = new_rules.registers_.begin();
  while (old_it != registers_.end() && new_it != new_rules.registers_.end()) {
    if (old_it->first < new_it->first) {
      // This RuleMap has an entry for old_it->first, but NEW_RULES
      // doesn't.
      //
      // This isn't really the right thing to do, but since CFI generally
      // only mentions callee-saves registers, and GCC's convention for
      // callee-saves registers is that they are unchanged, it's a good
      // approximation.
      if (!handler->SameValueRule(address, old_it->first))
        return false;
      old_it++;
    } else if (old_it->first > new_it->first) {
      // NEW_RULES has entry for new_it->first, but this RuleMap
      // doesn't. This shouldn't be possible: NEW_RULES is some prior
      // state, and there's no way to remove entries.
      assert(0);
    } else {
      // Both maps have an entry for this register. Report the new
      // rule if it is different.
      if (*old_it->second != *new_it->second &&
          !new_it->second->Handle(handler, address, new_it->first))
        return false;
      new_it++, old_it++;
    }
  }
  // Finish off entries from this RuleMap with no counterparts in new_rules.
  while (old_it != registers_.end()) {
    if (!handler->SameValueRule(address, old_it->first))
      return false;
    old_it++;
  }
  // Since we only make transitions from a rule set to some previously
  // saved rule set, and we can only add rules to the map, NEW_RULES
  // must have fewer rules than *this.
  assert(new_it == new_rules.registers_.end());

  return true;
}

// Remove all register rules and clear cfa_rule_.
void CallFrameInfo::RuleMap::Clear() {
  delete cfa_rule_;
  cfa_rule_ = NULL;
  for (RuleByNumber::iterator it = registers_.begin();
       it != registers_.end(); it++)
    delete it->second;
  registers_.clear();
}

// The state of the call frame information interpreter as it processes
// instructions from a CIE and FDE.
class CallFrameInfo::State {
 public:
  // Create a call frame information interpreter state with the given
  // reporter, reader, handler, and initial call frame info address.
  State(ByteReader *reader, Handler *handler, Reporter *reporter,
        uint64 address)
      : reader_(reader), handler_(handler), reporter_(reporter),
        address_(address), entry_(NULL), cursor_(NULL) { }

  // Interpret instructions from CIE, save the resulting rule set for
  // DW_CFA_restore instructions, and return true. On error, report
  // the problem to reporter_ and return false.
  bool InterpretCIE(const CIE &cie);

  // Interpret instructions from FDE, and return true. On error,
  // report the problem to reporter_ and return false.
  bool InterpretFDE(const FDE &fde);

 private:  
  // The operands of a CFI instruction, for ParseOperands.
  struct Operands {
    unsigned register_number;  // A register number.
    uint64 offset;             // An offset or address.
    long signed_offset;        // A signed offset.
    string expression;         // A DWARF expression.
  };

  // Parse CFI instruction operands from STATE's instruction stream as
  // described by FORMAT. On success, populate OPERANDS with the
  // results, and return true. On failure, report the problem and
  // return false.
  //
  // Each character of FORMAT should be one of the following:
  //
  //   'r'  unsigned LEB128 register number (OPERANDS->register_number)
  //   'o'  unsigned LEB128 offset          (OPERANDS->offset)
  //   's'  signed LEB128 offset            (OPERANDS->signed_offset)
  //   'a'  machine-size address            (OPERANDS->offset)
  //        (If the CIE has a 'z' augmentation string, 'a' uses the
  //        encoding specified by the 'R' argument.)
  //   '1'  a one-byte offset               (OPERANDS->offset)
  //   '2'  a two-byte offset               (OPERANDS->offset)
  //   '4'  a four-byte offset              (OPERANDS->offset)
  //   '8'  an eight-byte offset            (OPERANDS->offset)
  //   'e'  a DW_FORM_block holding a       (OPERANDS->expression)
  //        DWARF expression
  bool ParseOperands(const char *format, Operands *operands);

  // Interpret one CFI instruction from STATE's instruction stream, update
  // STATE, report any rule changes to handler_, and return true. On
  // failure, report the problem and return false.
  bool DoInstruction();

  // The following Do* member functions are subroutines of DoInstruction,
  // factoring out the actual work of operations that have several
  // different encodings.

  // Set the CFA rule to be the value of BASE_REGISTER plus OFFSET, and
  // return true. On failure, report and return false. (Used for
  // DW_CFA_def_cfa and DW_CFA_def_cfa_sf.)
  bool DoDefCFA(unsigned base_register, long offset);

  // Change the offset of the CFA rule to OFFSET, and return true. On
  // failure, report and return false. (Subroutine for
  // DW_CFA_def_cfa_offset and DW_CFA_def_cfa_offset_sf.)
  bool DoDefCFAOffset(long offset);

  // Specify that REG can be recovered using RULE, and return true. On
  // failure, report and return false.
  bool DoRule(unsigned reg, Rule *rule);

  // Specify that REG can be found at OFFSET from the CFA, and return true.
  // On failure, report and return false. (Subroutine for DW_CFA_offset,
  // DW_CFA_offset_extended, and DW_CFA_offset_extended_sf.)
  bool DoOffset(unsigned reg, long offset);

  // Specify that the caller's value for REG is the CFA plus OFFSET,
  // and return true. On failure, report and return false. (Subroutine
  // for DW_CFA_val_offset and DW_CFA_val_offset_sf.)
  bool DoValOffset(unsigned reg, long offset);

  // Restore REG to the rule established in the CIE, and return true. On
  // failure, report and return false. (Subroutine for DW_CFA_restore and
  // DW_CFA_restore_extended.)
  bool DoRestore(unsigned reg);

  // Return the section offset of the instruction at cursor. For use
  // in error messages.
  uint64 CursorOffset() { return entry_->offset + (cursor_ - entry_->start); }

  // Report that entry_ is incomplete, and return false. For brevity.
  bool ReportIncomplete() {
    reporter_->Incomplete(entry_->offset, entry_->kind);
    return false;
  }

  // For reading multi-byte values with the appropriate endianness.
  ByteReader *reader_;

  // The handler to which we should report the data we find.
  Handler *handler_;

  // For reporting problems in the info we're parsing.
  Reporter *reporter_;

  // The code address to which the next instruction in the stream applies.
  uint64 address_;

  // The entry whose instructions we are currently processing. This is
  // first a CIE, and then an FDE.
  const Entry *entry_;

  // The next instruction to process.
  const char *cursor_;

  // The current set of rules.
  RuleMap rules_;

  // The set of rules established by the CIE, used by DW_CFA_restore
  // and DW_CFA_restore_extended. We set this after interpreting the
  // CIE's instructions.
  RuleMap cie_rules_;

  // A stack of saved states, for DW_CFA_remember_state and
  // DW_CFA_restore_state.
  std::stack<RuleMap> saved_rules_;
};

bool CallFrameInfo::State::InterpretCIE(const CIE &cie) {
  entry_ = &cie;
  cursor_ = entry_->instructions;
  while (cursor_ < entry_->end)
    if (!DoInstruction())
      return false;
  // Note the rules established by the CIE, for use by DW_CFA_restore
  // and DW_CFA_restore_extended.
  cie_rules_ = rules_;
  return true;
}

bool CallFrameInfo::State::InterpretFDE(const FDE &fde) {
  entry_ = &fde;
  cursor_ = entry_->instructions;
  while (cursor_ < entry_->end)
    if (!DoInstruction())
      return false;
  return true;
}

bool CallFrameInfo::State::ParseOperands(const char *format,
                                         Operands *operands) {
  size_t len;
  const char *operand;

  for (operand = format; *operand; operand++) {
    size_t bytes_left = entry_->end - cursor_;
    switch (*operand) {
      case 'r':
        operands->register_number = reader_->ReadUnsignedLEB128(cursor_, &len);
        if (len > bytes_left) return ReportIncomplete();
        cursor_ += len;
        break;

      case 'o':
        operands->offset = reader_->ReadUnsignedLEB128(cursor_, &len);
        if (len > bytes_left) return ReportIncomplete();
        cursor_ += len;
        break;

      case 's':
        operands->signed_offset = reader_->ReadSignedLEB128(cursor_, &len);
        if (len > bytes_left) return ReportIncomplete();
        cursor_ += len;
        break;

      case 'a':
        operands->offset =
          reader_->ReadEncodedPointer(cursor_, entry_->cie->pointer_encoding,
                                      &len);
        if (len > bytes_left) return ReportIncomplete();
        cursor_ += len;
        break;

      case '1':
        if (1 > bytes_left) return ReportIncomplete();
        operands->offset = static_cast<unsigned char>(*cursor_++);
        break;

      case '2':
        if (2 > bytes_left) return ReportIncomplete();
        operands->offset = reader_->ReadTwoBytes(cursor_);
        cursor_ += 2;
        break;

      case '4':
        if (4 > bytes_left) return ReportIncomplete();
        operands->offset = reader_->ReadFourBytes(cursor_);
        cursor_ += 4;
        break;

      case '8':
        if (8 > bytes_left) return ReportIncomplete();
        operands->offset = reader_->ReadEightBytes(cursor_);
        cursor_ += 8;
        break;

      case 'e': {
        size_t expression_length = reader_->ReadUnsignedLEB128(cursor_, &len);
        if (len > bytes_left || expression_length > bytes_left - len)
          return ReportIncomplete();
        cursor_ += len;
        operands->expression = string(cursor_, expression_length);
        cursor_ += expression_length;
        break;
      }

      default:
          assert(0);
    }
  }

  return true;
}

bool CallFrameInfo::State::DoInstruction() {
  CIE *cie = entry_->cie;
  Operands ops;

  // Our entry's kind should have been set by now.
  assert(entry_->kind != kUnknown);

  // We shouldn't have been invoked unless there were more
  // instructions to parse.
  assert(cursor_ < entry_->end);

  unsigned opcode = *cursor_++;
  if ((opcode & 0xc0) != 0) {
    switch (opcode & 0xc0) {
      // Advance the address.
      case DW_CFA_advance_loc: {
        size_t code_offset = opcode & 0x3f;
        address_ += code_offset * cie->code_alignment_factor;
        break;
      }

      // Find a register at an offset from the CFA.
      case DW_CFA_offset:
        if (!ParseOperands("o", &ops) ||
            !DoOffset(opcode & 0x3f, ops.offset * cie->data_alignment_factor))
          return false;
        break;

      // Restore the rule established for a register by the CIE.
      case DW_CFA_restore:
        if (!DoRestore(opcode & 0x3f)) return false;
        break;

      // The 'if' above should have excluded this possibility.
      default:
        assert(0);
    }

    // Return here, so the big switch below won't be indented.
    return true;
  }

  switch (opcode) {
    // Set the address.
    case DW_CFA_set_loc:
      if (!ParseOperands("a", &ops)) return false;
      address_ = ops.offset;
      break;

    // Advance the address.
    case DW_CFA_advance_loc1:
      if (!ParseOperands("1", &ops)) return false;
      address_ += ops.offset * cie->code_alignment_factor;
      break;
      
    // Advance the address.
    case DW_CFA_advance_loc2:
      if (!ParseOperands("2", &ops)) return false;
      address_ += ops.offset * cie->code_alignment_factor;
      break;
      
    // Advance the address.
    case DW_CFA_advance_loc4:
      if (!ParseOperands("4", &ops)) return false;
      address_ += ops.offset * cie->code_alignment_factor;
      break;
      
    // Advance the address.
    case DW_CFA_MIPS_advance_loc8:
      if (!ParseOperands("8", &ops)) return false;
      address_ += ops.offset * cie->code_alignment_factor;
      break;

    // Compute the CFA by adding an offset to a register.
    case DW_CFA_def_cfa:
      if (!ParseOperands("ro", &ops) ||
          !DoDefCFA(ops.register_number, ops.offset))
        return false;
      break;

    // Compute the CFA by adding an offset to a register.
    case DW_CFA_def_cfa_sf:
      if (!ParseOperands("rs", &ops) ||
          !DoDefCFA(ops.register_number,
                    ops.signed_offset * cie->data_alignment_factor))
        return false;
      break;

    // Change the base register used to compute the CFA.
    case DW_CFA_def_cfa_register: {
      if (!ParseOperands("r", &ops)) return false;
      Rule *cfa_rule = rules_.CFARule();
      if (!cfa_rule) {
        if (!DoDefCFA(ops.register_number, ops.offset)) {
          reporter_->NoCFARule(entry_->offset, entry_->kind, CursorOffset());
          return false;
        }
      } else {
        cfa_rule->SetBaseRegister(ops.register_number);
        if (!cfa_rule->Handle(handler_, address_,
                              Handler::kCFARegister))
        return false;
      }
      break;
    }

    // Change the offset used to compute the CFA.
    case DW_CFA_def_cfa_offset:
      if (!ParseOperands("o", &ops) ||
          !DoDefCFAOffset(ops.offset))
        return false;
      break;

    // Change the offset used to compute the CFA.
    case DW_CFA_def_cfa_offset_sf:
      if (!ParseOperands("s", &ops) ||
          !DoDefCFAOffset(ops.signed_offset * cie->data_alignment_factor))
        return false;
      break;

    // Specify an expression whose value is the CFA.
    case DW_CFA_def_cfa_expression: {
      if (!ParseOperands("e", &ops))
        return false;
      Rule *rule = new ValExpressionRule(ops.expression);
      rules_.SetCFARule(rule);
      if (!rule->Handle(handler_, address_,
                        Handler::kCFARegister))
        return false;
      break;
    }

    // The register's value cannot be recovered.
    case DW_CFA_undefined: {
      if (!ParseOperands("r", &ops) ||
          !DoRule(ops.register_number, new UndefinedRule()))
        return false;
      break;
    }

    // The register's value is unchanged from its value in the caller.
    case DW_CFA_same_value: {
      if (!ParseOperands("r", &ops) ||
          !DoRule(ops.register_number, new SameValueRule()))
        return false;
      break;
    }

    // Find a register at an offset from the CFA.
    case DW_CFA_offset_extended:
      if (!ParseOperands("ro", &ops) ||
          !DoOffset(ops.register_number,
                    ops.offset * cie->data_alignment_factor))
        return false;
      break;

    // The register is saved at an offset from the CFA.
    case DW_CFA_offset_extended_sf:
      if (!ParseOperands("rs", &ops) ||
          !DoOffset(ops.register_number,
                    ops.signed_offset * cie->data_alignment_factor))
        return false;
      break;

    // The register is saved at an offset from the CFA.
    case DW_CFA_GNU_negative_offset_extended:
      if (!ParseOperands("ro", &ops) ||
          !DoOffset(ops.register_number,
                    -ops.offset * cie->data_alignment_factor))
        return false;
      break;

    // The register's value is the sum of the CFA plus an offset.
    case DW_CFA_val_offset:
      if (!ParseOperands("ro", &ops) ||
          !DoValOffset(ops.register_number,
                       ops.offset * cie->data_alignment_factor))
        return false;
      break;

    // The register's value is the sum of the CFA plus an offset.
    case DW_CFA_val_offset_sf:
      if (!ParseOperands("rs", &ops) ||
          !DoValOffset(ops.register_number,
                       ops.signed_offset * cie->data_alignment_factor))
        return false;
      break;

    // The register has been saved in another register.
    case DW_CFA_register: {
      if (!ParseOperands("ro", &ops) ||
          !DoRule(ops.register_number, new RegisterRule(ops.offset)))
        return false;
      break;
    }

    // An expression yields the address at which the register is saved.
    case DW_CFA_expression: {
      if (!ParseOperands("re", &ops) ||
          !DoRule(ops.register_number, new ExpressionRule(ops.expression)))
        return false;
      break;
    }

    // An expression yields the caller's value for the register.
    case DW_CFA_val_expression: {
      if (!ParseOperands("re", &ops) ||
          !DoRule(ops.register_number, new ValExpressionRule(ops.expression)))
        return false;
      break;
    }

    // Restore the rule established for a register by the CIE.
    case DW_CFA_restore_extended:
      if (!ParseOperands("r", &ops) ||
          !DoRestore( ops.register_number))
        return false;
      break;

    // Save the current set of rules on a stack.
    case DW_CFA_remember_state:
      saved_rules_.push(rules_);
      break;

    // Pop the current set of rules off the stack.
    case DW_CFA_restore_state: {
      if (saved_rules_.empty()) {
        reporter_->EmptyStateStack(entry_->offset, entry_->kind,
                                   CursorOffset());
        return false;
      }
      const RuleMap &new_rules = saved_rules_.top();
      if (rules_.CFARule() && !new_rules.CFARule()) {
        reporter_->ClearingCFARule(entry_->offset, entry_->kind,
                                   CursorOffset());
        return false;
      }
      rules_.HandleTransitionTo(handler_, address_, new_rules);
      rules_ = new_rules;
      saved_rules_.pop();
      break;
    }

    // No operation.  (Padding instruction.)
    case DW_CFA_nop:
      break;

    // A SPARC register window save: Registers 8 through 15 (%o0-%o7)
    // are saved in registers 24 through 31 (%i0-%i7), and registers
    // 16 through 31 (%l0-%l7 and %i0-%i7) are saved at CFA offsets
    // (0-15 * the register size). The register numbers must be
    // hard-coded. A GNU extension, and not a pretty one.
    case DW_CFA_GNU_window_save: {
      // Save %o0-%o7 in %i0-%i7.
      for (int i = 8; i < 16; i++)
        if (!DoRule(i, new RegisterRule(i + 16)))
          return false;
      // Save %l0-%l7 and %i0-%i7 at the CFA.
      for (int i = 16; i < 32; i++)
        // Assume that the byte reader's address size is the same as
        // the architecture's register size. !@#%*^ hilarious.
        if (!DoRule(i, new OffsetRule(Handler::kCFARegister,
                                      (i - 16) * reader_->AddressSize())))
          return false;
      break;
    }

    // I'm not sure what this is. GDB doesn't use it for unwinding.
    case DW_CFA_GNU_args_size:
      if (!ParseOperands("o", &ops)) return false;
      break;

    // An opcode we don't recognize.
    default: {
      reporter_->BadInstruction(entry_->offset, entry_->kind, CursorOffset());
      return false;
    }
  }

  return true;
}

bool CallFrameInfo::State::DoDefCFA(unsigned base_register, long offset) {
  Rule *rule = new ValOffsetRule(base_register, offset);
  rules_.SetCFARule(rule);
  return rule->Handle(handler_, address_,
                      Handler::kCFARegister);
}

bool CallFrameInfo::State::DoDefCFAOffset(long offset) {
  Rule *cfa_rule = rules_.CFARule();
  if (!cfa_rule) {
    reporter_->NoCFARule(entry_->offset, entry_->kind, CursorOffset());
    return false;
  }
  cfa_rule->SetOffset(offset);
  return cfa_rule->Handle(handler_, address_,
                          Handler::kCFARegister);
}

bool CallFrameInfo::State::DoRule(unsigned reg, Rule *rule) {
  rules_.SetRegisterRule(reg, rule);
  return rule->Handle(handler_, address_, reg);
}

bool CallFrameInfo::State::DoOffset(unsigned reg, long offset) {
  if (!rules_.CFARule()) {
    reporter_->NoCFARule(entry_->offset, entry_->kind, CursorOffset());
    return false;
  }
  return DoRule(reg,
                new OffsetRule(Handler::kCFARegister, offset));
}

bool CallFrameInfo::State::DoValOffset(unsigned reg, long offset) {
  if (!rules_.CFARule()) {
    reporter_->NoCFARule(entry_->offset, entry_->kind, CursorOffset());
    return false;
  }
  return DoRule(reg,
                new ValOffsetRule(Handler::kCFARegister, offset));
}

bool CallFrameInfo::State::DoRestore(unsigned reg) {
  // DW_CFA_restore and DW_CFA_restore_extended don't make sense in a CIE.
  if (entry_->kind == kCIE) {
    reporter_->RestoreInCIE(entry_->offset, CursorOffset());
    return false;
  }
  Rule *rule = cie_rules_.RegisterRule(reg);
  if (!rule) {
    // This isn't really the right thing to do, but since CFI generally
    // only mentions callee-saves registers, and GCC's convention for
    // callee-saves registers is that they are unchanged, it's a good
    // approximation.
    rule = new SameValueRule();
  }
  return DoRule(reg, rule);
}

bool CallFrameInfo::ReadEntryPrologue(const char *cursor, Entry *entry) {
  const char *buffer_end = buffer_ + buffer_length_;

  // Initialize enough of ENTRY for use in error reporting.
  entry->offset = cursor - buffer_;
  entry->start = cursor;
  entry->kind = kUnknown;
  entry->end = NULL;

  // Read the initial length. This sets reader_'s offset size.
  size_t length_size;
  uint64 length = reader_->ReadInitialLength(cursor, &length_size);
  if (length_size > size_t(buffer_end - cursor))
    return ReportIncomplete(entry);
  cursor += length_size;

  // In a .eh_frame section, a length of zero marks the end of the series
  // of entries.
  if (length == 0 && eh_frame_) {
    entry->kind = kTerminator;
    entry->end = cursor;
    return true;
  }

  // Validate the length.
  if (length > size_t(buffer_end - cursor))
    return ReportIncomplete(entry);
 
  // The length is the number of bytes after the initial length field;
  // we have that position handy at this point, so compute the end
  // now. (If we're parsing 64-bit-offset DWARF on a 32-bit machine,
  // and the length didn't fit in a size_t, we would have rejected it
  // above.)
  entry->end = cursor + length;

  // Parse the next field: either the offset of a CIE or a CIE id.
  size_t offset_size = reader_->OffsetSize();
  if (offset_size > size_t(entry->end - cursor)) return ReportIncomplete(entry);
  entry->id = reader_->ReadOffset(cursor);

  // Don't advance cursor past id field yet; in .eh_frame data we need
  // the id's position to compute the section offset of an FDE's CIE.

  // Now we can decide what kind of entry this is.
  if (eh_frame_) {
    // In .eh_frame data, an ID of zero marks the entry as a CIE, and
    // anything else is an offset from the id field of the FDE to the start
    // of the CIE.
    if (entry->id == 0) {
      entry->kind = kCIE;
    } else {
      entry->kind = kFDE;
      // Turn the offset from the id into an offset from the buffer's start.
      entry->id = (cursor - buffer_) - entry->id;
    }
  } else {
    // In DWARF CFI data, an ID of ~0 (of the appropriate width, given the
    // offset size for the entry) marks the entry as a CIE, and anything
    // else is the offset of the CIE from the beginning of the section.
    if (offset_size == 4)
      entry->kind = (entry->id == 0xffffffff) ? kCIE : kFDE;
    else {
      assert(offset_size == 8);
      entry->kind = (entry->id == 0xffffffffffffffffULL) ? kCIE : kFDE;
    }
  }

  // Now advance cursor past the id.
   cursor += offset_size;
 
  // The fields specific to this kind of entry start here.
  entry->fields = cursor;

  entry->cie = NULL;

  return true;
}

bool CallFrameInfo::ReadCIEFields(CIE *cie) {
  const char *cursor = cie->fields;
  size_t len;

  assert(cie->kind == kCIE);

  // Prepare for early exit.
  cie->version = 0;
  cie->augmentation.clear();
  cie->code_alignment_factor = 0;
  cie->data_alignment_factor = 0;
  cie->return_address_register = 0;
  cie->has_z_augmentation = false;
  cie->pointer_encoding = DW_EH_PE_absptr;
  cie->instructions = 0;

  // Parse the version number.
  if (cie->end - cursor < 1)
    return ReportIncomplete(cie);
  cie->version = reader_->ReadOneByte(cursor);
  cursor++;

  // If we don't recognize the version, we can't parse any more fields of the
  // CIE. For DWARF CFI, we handle versions 1 through 3 (there was never a
  // version 2 of CFI data). For .eh_frame, we handle versions 1 and 3 as well;
  // the difference between those versions seems to be the same as for
  // .debug_frame.
  if (cie->version < 1 || cie->version > 3) {
    reporter_->UnrecognizedVersion(cie->offset, cie->version);
    return false;
  }

  const char *augmentation_start = cursor;
  const void *augmentation_end =
      memchr(augmentation_start, '\0', cie->end - augmentation_start);
  if (! augmentation_end) return ReportIncomplete(cie);
  cursor = static_cast<const char *>(augmentation_end);
  cie->augmentation = string(augmentation_start,
                                  cursor - augmentation_start);
  // Skip the terminating '\0'.
  cursor++;

  // Is this CFI augmented?
  if (!cie->augmentation.empty()) {
    // Is it an augmentation we recognize?
    if (cie->augmentation[0] == DW_Z_augmentation_start) {
      // Linux C++ ABI 'z' augmentation, used for exception handling data.
      cie->has_z_augmentation = true;
    } else {
      // Not an augmentation we recognize. Augmentations can have arbitrary
      // effects on the form of rest of the content, so we have to give up.
      reporter_->UnrecognizedAugmentation(cie->offset, cie->augmentation);
      return false;
    }
  }

  // Parse the code alignment factor.
  cie->code_alignment_factor = reader_->ReadUnsignedLEB128(cursor, &len);
  if (size_t(cie->end - cursor) < len) return ReportIncomplete(cie);
  cursor += len;
  
  // Parse the data alignment factor.
  cie->data_alignment_factor = reader_->ReadSignedLEB128(cursor, &len);
  if (size_t(cie->end - cursor) < len) return ReportIncomplete(cie);
  cursor += len;
  
  // Parse the return address register. This is a ubyte in version 1, and
  // a ULEB128 in version 3.
  if (cie->version == 1) {
    if (cursor >= cie->end) return ReportIncomplete(cie);
    cie->return_address_register = uint8(*cursor++);
  } else {
    cie->return_address_register = reader_->ReadUnsignedLEB128(cursor, &len);
    if (size_t(cie->end - cursor) < len) return ReportIncomplete(cie);
    cursor += len;
  }

  // If we have a 'z' augmentation string, find the augmentation data and
  // use the augmentation string to parse it.
  if (cie->has_z_augmentation) {
    uint64_t data_size = reader_->ReadUnsignedLEB128(cursor, &len);
    if (size_t(cie->end - cursor) < len + data_size)
      return ReportIncomplete(cie);
    cursor += len;
    const char *data = cursor;
    cursor += data_size;
    const char *data_end = cursor;

    cie->has_z_lsda = false;
    cie->has_z_personality = false;
    cie->has_z_signal_frame = false;

    // Walk the augmentation string, and extract values from the
    // augmentation data as the string directs.
    for (size_t i = 1; i < cie->augmentation.size(); i++) {
      switch (cie->augmentation[i]) {
        case DW_Z_has_LSDA:
          // The CIE's augmentation data holds the language-specific data
          // area pointer's encoding, and the FDE's augmentation data holds
          // the pointer itself.
          cie->has_z_lsda = true;
          // Fetch the LSDA encoding from the augmentation data.
          if (data >= data_end) return ReportIncomplete(cie);
          cie->lsda_encoding = DwarfPointerEncoding(*data++);
          if (!reader_->ValidEncoding(cie->lsda_encoding)) {
            reporter_->InvalidPointerEncoding(cie->offset, cie->lsda_encoding);
            return false;
          }
          // Don't check if the encoding is usable here --- we haven't
          // read the FDE's fields yet, so we're not prepared for
          // DW_EH_PE_funcrel, although that's a fine encoding for the
          // LSDA to use, since it appears in the FDE.
          break;

        case DW_Z_has_personality_routine:
          // The CIE's augmentation data holds the personality routine
          // pointer's encoding, followed by the pointer itself.
          cie->has_z_personality = true;
          // Fetch the personality routine pointer's encoding from the
          // augmentation data.
          if (data >= data_end) return ReportIncomplete(cie);
          cie->personality_encoding = DwarfPointerEncoding(*data++);
          if (!reader_->ValidEncoding(cie->personality_encoding)) {
            reporter_->InvalidPointerEncoding(cie->offset,
                                              cie->personality_encoding);
            return false;
          }
          if (!reader_->UsableEncoding(cie->personality_encoding)) {
            reporter_->UnusablePointerEncoding(cie->offset,
                                               cie->personality_encoding);
            return false;
          }
          // Fetch the personality routine's pointer itself from the data.
          cie->personality_address =
            reader_->ReadEncodedPointer(data, cie->personality_encoding,
                                        &len);
          if (len > size_t(data_end - data))
            return ReportIncomplete(cie);
          data += len;
          break;

        case DW_Z_has_FDE_address_encoding:
          // The CIE's augmentation data holds the pointer encoding to use
          // for addresses in the FDE.
          if (data >= data_end) return ReportIncomplete(cie);
          cie->pointer_encoding = DwarfPointerEncoding(*data++);
          if (!reader_->ValidEncoding(cie->pointer_encoding)) {
            reporter_->InvalidPointerEncoding(cie->offset,
                                              cie->pointer_encoding);
            return false;
          }
          if (!reader_->UsableEncoding(cie->pointer_encoding)) {
            reporter_->UnusablePointerEncoding(cie->offset,
                                               cie->pointer_encoding);
            return false;
          }
          break;

        case DW_Z_is_signal_trampoline:
          // Frames using this CIE are signal delivery frames.
          cie->has_z_signal_frame = true;
          break;

        default:
          // An augmentation we don't recognize.
          reporter_->UnrecognizedAugmentation(cie->offset, cie->augmentation);
          return false;
      }
    }
  }

  // The CIE's instructions start here.
  cie->instructions = cursor;

  return true;
}
  
bool CallFrameInfo::ReadFDEFields(FDE *fde) {
  const char *cursor = fde->fields;
  size_t size;

  fde->address = reader_->ReadEncodedPointer(cursor, fde->cie->pointer_encoding,
                                             &size);
  if (size > size_t(fde->end - cursor))
    return ReportIncomplete(fde);
  cursor += size;
  reader_->SetFunctionBase(fde->address);

  // For the length, we strip off the upper nybble of the encoding used for
  // the starting address.
  DwarfPointerEncoding length_encoding =
    DwarfPointerEncoding(fde->cie->pointer_encoding & 0x0f);
  fde->size = reader_->ReadEncodedPointer(cursor, length_encoding, &size);
  if (size > size_t(fde->end - cursor))
    return ReportIncomplete(fde);
  cursor += size;

  // If the CIE has a 'z' augmentation string, then augmentation data
  // appears here.
  if (fde->cie->has_z_augmentation) {
    uint64_t data_size = reader_->ReadUnsignedLEB128(cursor, &size);
    if (size_t(fde->end - cursor) < size + data_size)
      return ReportIncomplete(fde);
    cursor += size;
    
    // In the abstract, we should walk the augmentation string, and extract
    // items from the FDE's augmentation data as we encounter augmentation
    // string characters that specify their presence: the ordering of items
    // in the augmentation string determines the arrangement of values in
    // the augmentation data.
    //
    // In practice, there's only ever one value in FDE augmentation data
    // that we support --- the LSDA pointer --- and we have to bail if we
    // see any unrecognized augmentation string characters. So if there is
    // anything here at all, we know what it is, and where it starts.
    if (fde->cie->has_z_lsda) {
      // Check whether the LSDA's pointer encoding is usable now: only once
      // we've parsed the FDE's starting address do we call reader_->
      // SetFunctionBase, so that the DW_EH_PE_funcrel encoding becomes
      // usable.
      if (!reader_->UsableEncoding(fde->cie->lsda_encoding)) {
        reporter_->UnusablePointerEncoding(fde->cie->offset,
                                           fde->cie->lsda_encoding);
        return false;
      }

      fde->lsda_address =
        reader_->ReadEncodedPointer(cursor, fde->cie->lsda_encoding, &size);
      if (size > data_size)
        return ReportIncomplete(fde);
      // Ideally, we would also complain here if there were unconsumed
      // augmentation data.
    }

    cursor += data_size;
  }

  // The FDE's instructions start after those.
  fde->instructions = cursor;

  return true;
}
  
bool CallFrameInfo::Start() {
  const char *buffer_end = buffer_ + buffer_length_;
  const char *cursor;
  bool all_ok = true;
  const char *entry_end;
  bool ok;

  // Traverse all the entries in buffer_, skipping CIEs and offering
  // FDEs to the handler.
  for (cursor = buffer_; cursor < buffer_end;
       cursor = entry_end, all_ok = all_ok && ok) {
    FDE fde;

    // Make it easy to skip this entry with 'continue': assume that
    // things are not okay until we've checked all the data, and
    // prepare the address of the next entry.
    ok = false;

    // Read the entry's prologue.
    if (!ReadEntryPrologue(cursor, &fde)) {
      if (!fde.end) {
        // If we couldn't even figure out this entry's extent, then we
        // must stop processing entries altogether.
        all_ok = false;
        break;
      }
      entry_end = fde.end;
      continue;
    }

    // The next iteration picks up after this entry.
    entry_end = fde.end;

    // Did we see an .eh_frame terminating mark?
    if (fde.kind == kTerminator) {
      // If there appears to be more data left in the section after the
      // terminating mark, warn the user. But this is just a warning;
      // we leave all_ok true.
      if (fde.end < buffer_end) reporter_->EarlyEHTerminator(fde.offset);
      break;
    }

    // In this loop, we skip CIEs. We only parse them fully when we
    // parse an FDE that refers to them. This limits our memory
    // consumption (beyond the buffer itself) to that needed to
    // process the largest single entry.
    if (fde.kind != kFDE) {
      ok = true;
      continue;
    }

    // Validate the CIE pointer.
    if (fde.id > buffer_length_) {
      reporter_->CIEPointerOutOfRange(fde.offset, fde.id);
      continue;
    }
      
    CIE cie;

    // Parse this FDE's CIE header.
    if (!ReadEntryPrologue(buffer_ + fde.id, &cie))
      continue;
    // This had better be an actual CIE.
    if (cie.kind != kCIE) {
      reporter_->BadCIEId(fde.offset, fde.id);
      continue;
    }
    if (!ReadCIEFields(&cie))
      continue;

    // We now have the values that govern both the CIE and the FDE.
    cie.cie = &cie;
    fde.cie = &cie;

    // Parse the FDE's header.
    if (!ReadFDEFields(&fde))
      continue;

    // Call Entry to ask the consumer if they're interested.
    if (!handler_->Entry(fde.offset, fde.address, fde.size,
                         cie.version, cie.augmentation,
                         cie.return_address_register)) {
      // The handler isn't interested in this entry. That's not an error.
      ok = true;
      continue;
    }
                         
    if (cie.has_z_augmentation) {
      // Report the personality routine address, if we have one.
      if (cie.has_z_personality) {
        if (!handler_
            ->PersonalityRoutine(cie.personality_address,
                                 IsIndirectEncoding(cie.personality_encoding)))
          continue;
      }

      // Report the language-specific data area address, if we have one.
      if (cie.has_z_lsda) {
        if (!handler_
            ->LanguageSpecificDataArea(fde.lsda_address,
                                       IsIndirectEncoding(cie.lsda_encoding)))
          continue;
      }

      // If this is a signal-handling frame, report that.
      if (cie.has_z_signal_frame) {
        if (!handler_->SignalHandler())
          continue;
      }
    }

    // Interpret the CIE's instructions, and then the FDE's instructions.
    State state(reader_, handler_, reporter_, fde.address);
    ok = state.InterpretCIE(cie) && state.InterpretFDE(fde);

    // Tell the ByteReader that the function start address from the
    // FDE header is no longer valid.
    reader_->ClearFunctionBase();

    // Report the end of the entry.
    handler_->End();
  }

  return all_ok;
}

const char *CallFrameInfo::KindName(EntryKind kind) {
  if (kind == CallFrameInfo::kUnknown)
    return "entry";
  else if (kind == CallFrameInfo::kCIE)
    return "common information entry";
  else if (kind == CallFrameInfo::kFDE)
    return "frame description entry";
  else {
    assert (kind == CallFrameInfo::kTerminator);
    return ".eh_frame sequence terminator";
  }
}

bool CallFrameInfo::ReportIncomplete(Entry *entry) {
  reporter_->Incomplete(entry->offset, entry->kind);
  return false;
}

void CallFrameInfo::Reporter::Incomplete(uint64 offset,
                                         CallFrameInfo::EntryKind kind) {
  fprintf(stderr,
          "%s: CFI %s at offset 0x%llx in '%s': entry ends early\n",
          filename_.c_str(), CallFrameInfo::KindName(kind), offset,
          section_.c_str());
}

void CallFrameInfo::Reporter::EarlyEHTerminator(uint64 offset) {
  fprintf(stderr,
          "%s: CFI at offset 0x%llx in '%s': saw end-of-data marker"
          " before end of section contents\n",
          filename_.c_str(), offset, section_.c_str());
}

void CallFrameInfo::Reporter::CIEPointerOutOfRange(uint64 offset,
                                                   uint64 cie_offset) {
  fprintf(stderr,
          "%s: CFI frame description entry at offset 0x%llx in '%s':"
          " CIE pointer is out of range: 0x%llx\n",
          filename_.c_str(), offset, section_.c_str(), cie_offset);
}

void CallFrameInfo::Reporter::BadCIEId(uint64 offset, uint64 cie_offset) {
  fprintf(stderr,
          "%s: CFI frame description entry at offset 0x%llx in '%s':"
          " CIE pointer does not point to a CIE: 0x%llx\n",
          filename_.c_str(), offset, section_.c_str(), cie_offset);
}

void CallFrameInfo::Reporter::UnrecognizedVersion(uint64 offset, int version) {
  fprintf(stderr,
          "%s: CFI frame description entry at offset 0x%llx in '%s':"
          " CIE specifies unrecognized version: %d\n",
          filename_.c_str(), offset, section_.c_str(), version);
}

void CallFrameInfo::Reporter::UnrecognizedAugmentation(uint64 offset,
                                                       const string &aug) {
  fprintf(stderr,
          "%s: CFI frame description entry at offset 0x%llx in '%s':"
          " CIE specifies unrecognized augmentation: '%s'\n",
          filename_.c_str(), offset, section_.c_str(), aug.c_str());
}

void CallFrameInfo::Reporter::InvalidPointerEncoding(uint64 offset,
                                                     uint8 encoding) {
  fprintf(stderr,
          "%s: CFI common information entry at offset 0x%llx in '%s':"
          " 'z' augmentation specifies invalid pointer encoding: 0x%02x\n",
          filename_.c_str(), offset, section_.c_str(), encoding);
}

void CallFrameInfo::Reporter::UnusablePointerEncoding(uint64 offset,
                                                      uint8 encoding) {
  fprintf(stderr,
          "%s: CFI common information entry at offset 0x%llx in '%s':"
          " 'z' augmentation specifies a pointer encoding for which"
          " we have no base address: 0x%02x\n",
          filename_.c_str(), offset, section_.c_str(), encoding);
}

void CallFrameInfo::Reporter::RestoreInCIE(uint64 offset, uint64 insn_offset) {
  fprintf(stderr,
          "%s: CFI common information entry at offset 0x%llx in '%s':"
          " the DW_CFA_restore instruction at offset 0x%llx"
          " cannot be used in a common information entry\n",
          filename_.c_str(), offset, section_.c_str(), insn_offset);
}

void CallFrameInfo::Reporter::BadInstruction(uint64 offset,
                                             CallFrameInfo::EntryKind kind,
                                             uint64 insn_offset) {
  fprintf(stderr,
          "%s: CFI %s at offset 0x%llx in section '%s':"
          " the instruction at offset 0x%llx is unrecognized\n",
          filename_.c_str(), CallFrameInfo::KindName(kind),
          offset, section_.c_str(), insn_offset);
}

void CallFrameInfo::Reporter::NoCFARule(uint64 offset,
                                        CallFrameInfo::EntryKind kind,
                                        uint64 insn_offset) {
  fprintf(stderr,
          "%s: CFI %s at offset 0x%llx in section '%s':"
          " the instruction at offset 0x%llx assumes that a CFA rule has"
          " been set, but none has been set\n",
          filename_.c_str(), CallFrameInfo::KindName(kind), offset,
          section_.c_str(), insn_offset);
}

void CallFrameInfo::Reporter::EmptyStateStack(uint64 offset,
                                              CallFrameInfo::EntryKind kind,
                                              uint64 insn_offset) {
  fprintf(stderr,
          "%s: CFI %s at offset 0x%llx in section '%s':"
          " the DW_CFA_restore_state instruction at offset 0x%llx"
          " should pop a saved state from the stack, but the stack is empty\n",
          filename_.c_str(), CallFrameInfo::KindName(kind), offset,
          section_.c_str(), insn_offset);
}

void CallFrameInfo::Reporter::ClearingCFARule(uint64 offset,
                                              CallFrameInfo::EntryKind kind,
                                              uint64 insn_offset) {
  fprintf(stderr,
          "%s: CFI %s at offset 0x%llx in section '%s':"
          " the DW_CFA_restore_state instruction at offset 0x%llx"
          " would clear the CFA rule in effect\n",
          filename_.c_str(), CallFrameInfo::KindName(kind), offset,
          section_.c_str(), insn_offset);
}

}  // namespace dwarf2reader
