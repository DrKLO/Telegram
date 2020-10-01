# Copyright 2018 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

# Define symbols that point to the start and end of the .text section.
PROVIDE_HIDDEN(linker_script_start_of_text = ADDR(.text));
PROVIDE_HIDDEN(linker_script_end_of_text = ADDR(.text) + SIZEOF(.text));
