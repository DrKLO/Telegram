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

// disassembler_x86.cc: simple x86 disassembler.
//
// Provides single step disassembly of x86 bytecode and flags instructions
// that utilize known bad register values.
//
// Author: Cris Neckar

#include "processor/disassembler_x86.h"

#include <string.h>
#include <unistd.h>

namespace google_breakpad {

DisassemblerX86::DisassemblerX86(const uint8_t *bytecode,
                                 uint32_t size,
                                 uint32_t virtual_address) :
                                     bytecode_(bytecode),
                                     size_(size),
                                     virtual_address_(virtual_address),
                                     current_byte_offset_(0),
                                     current_inst_offset_(0),
                                     instr_valid_(false),
                                     register_valid_(false),
                                     pushed_bad_value_(false),
                                     end_of_block_(false),
                                     flags_(0) {
  libdis::x86_init(libdis::opt_none, NULL, NULL);
}

DisassemblerX86::~DisassemblerX86() {
  if (instr_valid_)
    libdis::x86_oplist_free(&current_instr_);

  libdis::x86_cleanup();
}

uint32_t DisassemblerX86::NextInstruction() {
  if (instr_valid_)
    libdis::x86_oplist_free(&current_instr_);

  if (current_byte_offset_ >= size_) {
    instr_valid_ = false;
    return 0;
  }
  uint32_t instr_size = 0;
  instr_size = libdis::x86_disasm((unsigned char *)bytecode_, size_,
                          virtual_address_, current_byte_offset_,
                          &current_instr_);
  if (instr_size == 0) {
    instr_valid_ = false;
    return 0;
  }

  current_byte_offset_ += instr_size;
  current_inst_offset_++;
  instr_valid_ = libdis::x86_insn_is_valid(&current_instr_);
  if (!instr_valid_)
    return 0;

  if (current_instr_.type == libdis::insn_return)
    end_of_block_ = true;
  libdis::x86_op_t *src = libdis::x86_get_src_operand(&current_instr_);
  libdis::x86_op_t *dest = libdis::x86_get_dest_operand(&current_instr_);

  if (register_valid_) {
    switch (current_instr_.group) {
      // Flag branches based off of bad registers and calls that occur
      // after pushing bad values.
      case libdis::insn_controlflow:
        switch (current_instr_.type) {
          case libdis::insn_jmp:
          case libdis::insn_jcc:
          case libdis::insn_call:
          case libdis::insn_callcc:
            if (dest) {
              switch (dest->type) {
                case libdis::op_expression:
                  if (dest->data.expression.base.id == bad_register_.id)
                    flags_ |= DISX86_BAD_BRANCH_TARGET;
                  break;
                case libdis::op_register:
                  if (dest->data.reg.id == bad_register_.id)
                    flags_ |= DISX86_BAD_BRANCH_TARGET;
                  break;
                default:
                  if (pushed_bad_value_ &&
                      (current_instr_.type == libdis::insn_call ||
                      current_instr_.type == libdis::insn_callcc))
                    flags_ |= DISX86_BAD_ARGUMENT_PASSED;
                  break;
              }
            }
            break;
          default:
            break;
        }
        break;

      // Flag block data operations that use bad registers for src or dest.
      case libdis::insn_string:
        if (dest && dest->type == libdis::op_expression &&
            dest->data.expression.base.id == bad_register_.id)
          flags_ |= DISX86_BAD_BLOCK_WRITE;
        if (src && src->type == libdis::op_expression &&
            src->data.expression.base.id == bad_register_.id)
          flags_ |= DISX86_BAD_BLOCK_READ;
        break;

      // Flag comparisons based on bad data.
      case libdis::insn_comparison:
        if ((dest && dest->type == libdis::op_expression &&
            dest->data.expression.base.id == bad_register_.id) ||
            (src && src->type == libdis::op_expression &&
            src->data.expression.base.id == bad_register_.id) ||
            (dest && dest->type == libdis::op_register &&
            dest->data.reg.id == bad_register_.id) ||
            (src && src->type == libdis::op_register &&
            src->data.reg.id == bad_register_.id))
          flags_ |= DISX86_BAD_COMPARISON;
        break;

      // Flag any other instruction which derefs a bad register for
      // src or dest.
      default:
        if (dest && dest->type == libdis::op_expression &&
            dest->data.expression.base.id == bad_register_.id)
          flags_ |= DISX86_BAD_WRITE;
        if (src && src->type == libdis::op_expression &&
            src->data.expression.base.id == bad_register_.id)
          flags_ |= DISX86_BAD_READ;
        break;
    }
  }

  // When a register is marked as tainted check if it is pushed.
  // TODO(cdn): may also want to check for MOVs into EBP offsets.
  if (register_valid_ && dest && current_instr_.type == libdis::insn_push) {
    switch (dest->type) {
      case libdis::op_expression:
        if (dest->data.expression.base.id == bad_register_.id ||
            dest->data.expression.index.id == bad_register_.id)
          pushed_bad_value_ = true;
        break;
      case libdis::op_register:
        if (dest->data.reg.id == bad_register_.id)
          pushed_bad_value_ = true;
        break;
      default:
        break;
    }
  }

  // Check if a tainted register value is clobbered.
  // For conditional MOVs and XCHGs assume that
  // there is a hit.
  if (register_valid_) {
    switch (current_instr_.type) {
      case libdis::insn_xor:
        if (src && src->type == libdis::op_register &&
            dest && dest->type == libdis::op_register &&
            src->data.reg.id == bad_register_.id &&
            src->data.reg.id == dest->data.reg.id)
          register_valid_ = false;
        break;
      case libdis::insn_pop:
      case libdis::insn_mov:
      case libdis::insn_movcc:
        if (dest && dest->type == libdis::op_register &&
            dest->data.reg.id == bad_register_.id)
          register_valid_ = false;
        break;
      case libdis::insn_popregs:
        register_valid_ = false;
        break;
      case libdis::insn_xchg:
      case libdis::insn_xchgcc:
        if (dest && dest->type == libdis::op_register &&
            src && src->type == libdis::op_register) {
          if (dest->data.reg.id == bad_register_.id)
            memcpy(&bad_register_, &src->data.reg, sizeof(libdis::x86_reg_t));
          else if (src->data.reg.id == bad_register_.id)
            memcpy(&bad_register_, &dest->data.reg, sizeof(libdis::x86_reg_t));
        }
        break;
      default:
        break;
    }
  }

  return instr_size;
}

bool DisassemblerX86::setBadRead() {
  if (!instr_valid_)
    return false;

  libdis::x86_op_t *operand = libdis::x86_get_src_operand(&current_instr_);
  if (!operand || operand->type != libdis::op_expression)
    return false;

  memcpy(&bad_register_, &operand->data.expression.base,
         sizeof(libdis::x86_reg_t));
  register_valid_ = true;
  return true;
}

bool DisassemblerX86::setBadWrite() {
  if (!instr_valid_)
    return false;

  libdis::x86_op_t *operand = libdis::x86_get_dest_operand(&current_instr_);
  if (!operand || operand->type != libdis::op_expression)
    return false;

  memcpy(&bad_register_, &operand->data.expression.base,
         sizeof(libdis::x86_reg_t));
  register_valid_ = true;
  return true;
}

}  // namespace google_breakpad
