include_HEADERS = libdis.h
lib_LTLIBRARIES = libdisasm.la
libdisasm_la_SOURCES = \
	ia32_implicit.c \
	ia32_implicit.h \
	ia32_insn.c \
	ia32_insn.h \
	ia32_invariant.c \
	ia32_invariant.h \
	ia32_modrm.c \
	ia32_modrm.h \
	ia32_opcode_tables.c \
	ia32_opcode_tables.h \
	ia32_operand.c \
	ia32_operand.h \
	ia32_reg.c \
	ia32_reg.h \
	ia32_settings.c \
	ia32_settings.h \
	libdis.h \
	qword.h \
	x86_disasm.c \
	x86_format.c \
	x86_imm.c \
	x86_imm.h \
	x86_insn.c \
	x86_misc.c \
	x86_operand_list.c \
	x86_operand_list.h

# Cheat to get non-autoconf swig into tarball,
# even if it doesn't build by default.
EXTRA_DIST = \
swig/Makefile \
swig/libdisasm.i \
swig/libdisasm_oop.i \
swig/python/Makefile-swig \
swig/perl/Makefile-swig \
swig/perl/Makefile.PL \
swig/ruby/Makefile-swig \
swig/ruby/extconf.rb \
swig/tcl/Makefile-swig \
swig/README
