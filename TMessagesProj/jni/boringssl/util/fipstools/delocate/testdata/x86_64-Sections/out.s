.text
.file 1 "inserted_by_delocate.c"
.loc 1 1 0
BORINGSSL_bcm_text_start:
	# .text stays in .text
	.text
	movq %rax, %rax

	# -ffunction-sections is undone.
# WAS .section .text.foo,"ax",@progbits
.text
	.globl foo
.Lfoo_local_target:
foo:
	ret

	# .rodata is moved to .text.
# WAS .section .rodata
.text
	.long 42
	.string "Hello world, esc\ape characters are \"fun\"\\"

	# Compilers sometimes emit extra rodata sections.
# WAS .section .rodata.str1.1,"aMS",@progbits,1
.text
	.string "NIST P-256"
	.text

	# A number of sections are left alone.
	.section	.init_array,"aw"
	.align 8
	.quad	foo
# WAS .section	.rodata
.text
	.align 16
	.section	.debug_info,"",@progbits
.Ldebug_info0:

	.long	0x1b35e
	.value	0x4
	.long	.L1
	.byte	0x8
	.uleb128 0x1
	.long	.L2
	.byte	0x1
	.long	.L3
.text
.loc 1 2 0
BORINGSSL_bcm_text_end:
.type OPENSSL_ia32cap_get, @function
.globl OPENSSL_ia32cap_get
.LOPENSSL_ia32cap_get_local_target:
OPENSSL_ia32cap_get:
	leaq OPENSSL_ia32cap_P(%rip), %rax
	ret
.extern OPENSSL_ia32cap_P
.type OPENSSL_ia32cap_addr_delta, @object
.size OPENSSL_ia32cap_addr_delta, 8
OPENSSL_ia32cap_addr_delta:
.quad OPENSSL_ia32cap_P-OPENSSL_ia32cap_addr_delta
.type BORINGSSL_bcm_text_hash, @object
.size BORINGSSL_bcm_text_hash, 64
BORINGSSL_bcm_text_hash:
.byte 0xae
.byte 0x2c
.byte 0xea
.byte 0x2a
.byte 0xbd
.byte 0xa6
.byte 0xf3
.byte 0xec
.byte 0x97
.byte 0x7f
.byte 0x9b
.byte 0xf6
.byte 0x94
.byte 0x9a
.byte 0xfc
.byte 0x83
.byte 0x68
.byte 0x27
.byte 0xcb
.byte 0xa0
.byte 0xa0
.byte 0x9f
.byte 0x6b
.byte 0x6f
.byte 0xde
.byte 0x52
.byte 0xcd
.byte 0xe2
.byte 0xcd
.byte 0xff
.byte 0x31
.byte 0x80
.byte 0xa2
.byte 0xd4
.byte 0xc3
.byte 0x66
.byte 0xf
.byte 0xc2
.byte 0x6a
.byte 0x7b
.byte 0xf4
.byte 0xbe
.byte 0x39
.byte 0xa2
.byte 0xd7
.byte 0x25
.byte 0xdb
.byte 0x21
.byte 0x98
.byte 0xe9
.byte 0xd5
.byte 0x53
.byte 0xbf
.byte 0x5c
.byte 0x32
.byte 0x6
.byte 0x83
.byte 0x34
.byte 0xc
.byte 0x65
.byte 0x89
.byte 0x52
.byte 0xbd
.byte 0x1f
