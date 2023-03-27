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
  'includes': [
    'processor_tools.gypi',
  ],
  'targets': [
    {
      'target_name': 'processor',
      'type': 'static_library',
      'sources': [
        'address_map-inl.h',
        'address_map.h',
        'basic_code_module.h',
        'basic_code_modules.cc',
        'basic_code_modules.h',
        'basic_source_line_resolver.cc',
        'basic_source_line_resolver_types.h',
        'binarystream.cc',
        'binarystream.h',
        'call_stack.cc',
        'cfi_frame_info-inl.h',
        'cfi_frame_info.cc',
        'cfi_frame_info.h',
        'contained_range_map-inl.h',
        'contained_range_map.h',
        'disassembler_x86.cc',
        'disassembler_x86.h',
        'dump_context.cc',
        'dump_object.cc',
        'exploitability.cc',
        'exploitability_linux.cc',
        'exploitability_linux.h',
        'exploitability_win.cc',
        'exploitability_win.h',
        'fast_source_line_resolver.cc',
        'fast_source_line_resolver_types.h',
        'linked_ptr.h',
        'logging.cc',
        'logging.h',
        'map_serializers-inl.h',
        'map_serializers.h',
        'microdump_processor.cc',
        'minidump.cc',
        'minidump_processor.cc',
        'module_comparer.cc',
        'module_comparer.h',
        'module_factory.h',
        'module_serializer.cc',
        'module_serializer.h',
        'pathname_stripper.cc',
        'pathname_stripper.h',
        'postfix_evaluator-inl.h',
        'postfix_evaluator.h',
        'process_state.cc',
        'range_map-inl.h',
        'range_map.h',
        'simple_serializer-inl.h',
        'simple_serializer.h',
        'simple_symbol_supplier.cc',
        'simple_symbol_supplier.h',
        'source_line_resolver_base.cc',
        'source_line_resolver_base_types.h',
        'stack_frame_cpu.cc',
        'stack_frame_symbolizer.cc',
        'stackwalk_common.cc',
        'stackwalk_common.h',
        'stackwalker.cc',
        'stackwalker_address_list.cc',
        'stackwalker_address_list.h',
        'stackwalker_amd64.cc',
        'stackwalker_amd64.h',
        'stackwalker_arm.cc',
        'stackwalker_arm.h',
        'stackwalker_arm64.cc',
        'stackwalker_arm64.h',
        'stackwalker_mips.cc',
        'stackwalker_mips.h',
        'stackwalker_ppc.cc',
        'stackwalker_ppc.h',
        'stackwalker_ppc64.cc',
        'stackwalker_ppc64.h',
        'stackwalker_selftest.cc',
        'stackwalker_sparc.cc',
        'stackwalker_sparc.h',
        'stackwalker_x86.cc',
        'stackwalker_x86.h',
        'static_address_map-inl.h',
        'static_address_map.h',
        'static_contained_range_map-inl.h',
        'static_contained_range_map.h',
        'static_map-inl.h',
        'static_map.h',
        'static_map_iterator-inl.h',
        'static_map_iterator.h',
        'static_range_map-inl.h',
        'static_range_map.h',
        'symbolic_constants_win.cc',
        'symbolic_constants_win.h',
        'synth_minidump.cc',
        'synth_minidump.h',
        'tokenize.cc',
        'tokenize.h',
        'windows_frame_info.h',
      ],
      'include_dirs': [
        '..',
      ],
      'dependencies': [
        '../common/common.gyp:common',
        '../third_party/libdisasm/libdisasm.gyp:libdisasm',
      ],
    },
    {
      'target_name': 'processor_unittests',
      'type': 'executable',
      'sources': [
        'address_map_unittest.cc',
        'basic_source_line_resolver_unittest.cc',
        'binarystream_unittest.cc',
        'cfi_frame_info_unittest.cc',
        'contained_range_map_unittest.cc',
        'disassembler_x86_unittest.cc',
        'exploitability_unittest.cc',
        'fast_source_line_resolver_unittest.cc',
        'map_serializers_unittest.cc',
        'microdump_processor_unittest.cc',
        'minidump_processor_unittest.cc',
        'minidump_unittest.cc',
        'pathname_stripper_unittest.cc',
        'postfix_evaluator_unittest.cc',
        'range_map_unittest.cc',
        'stackwalker_address_list_unittest.cc',
        'stackwalker_amd64_unittest.cc',
        'stackwalker_arm64_unittest.cc',
        'stackwalker_arm_unittest.cc',
        'stackwalker_mips_unittest.cc',
        'stackwalker_unittest_utils.h',
        'stackwalker_x86_unittest.cc',
        'static_address_map_unittest.cc',
        'static_contained_range_map_unittest.cc',
        'static_map_unittest.cc',
        'static_range_map_unittest.cc',
        'synth_minidump_unittest.cc',
        'synth_minidump_unittest_data.h',
      ],
      'include_dirs': [
        '..',
      ],
      'dependencies': [
        'processor',
        '../build/testing.gypi:gmock',
        '../build/testing.gypi:gtest',
      ],
    },
  ],
}
