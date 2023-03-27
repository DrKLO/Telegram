require 'mkmf'
find_library('disasm', 'x86_init', "/usr/local/lib", "../..")
create_makefile('x86disasm')

