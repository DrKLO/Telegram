# Copyright 2014 Google Inc. All rights reserved.
#
# Redistribution and use in source and binary forms, with or without
# modification, are permitted provided that the following conditions are
# met:
#
#     * Redistributions of source code must retain the above copyright
# notice, this list of conditions and the following disclaimer.
#     * Redistributions in binary form must reproduce the above
# copyright notice, this list of conditions and the following disclaimer
# in the documentation and/or other materials provided with the
# distribution.
#     * Neither the name of Google Inc. nor the names of its
# contributors may be used to endorse or promote products derived from
# this software without specific prior written permission.
#
# THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
# "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
# LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
# A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
# OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
# SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
# LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
# DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
# THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
# (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
# OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

{
  'targets': [
    {
      'target_name': 'libdisasm',
      'type': 'static_library',
      'sources': [
	'ia32_implicit.c',
	'ia32_implicit.h',
	'ia32_insn.c',
	'ia32_insn.h',
	'ia32_invariant.c',
	'ia32_invariant.h',
	'ia32_modrm.c',
	'ia32_modrm.h',
	'ia32_opcode_tables.c',
	'ia32_opcode_tables.h',
	'ia32_operand.c',
	'ia32_operand.h',
	'ia32_reg.c',
	'ia32_reg.h',
	'ia32_settings.c',
	'ia32_settings.h',
	'libdis.h',
	'qword.h',
	'x86_disasm.c',
	'x86_format.c',
	'x86_imm.c',
	'x86_imm.h',
	'x86_insn.c',
	'x86_misc.c',
	'x86_operand_list.c',
	'x86_operand_list.h',
      ],
    },
  ],
}
