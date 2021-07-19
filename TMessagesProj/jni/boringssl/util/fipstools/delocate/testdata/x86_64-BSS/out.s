.text
.file 1 "inserted_by_delocate.c"
.loc 1 1 0
BORINGSSL_bcm_text_start:
	.text
	movq %rax, %rax
	
	# BSS declarations emit accessors.
	.comm	aes_128_ctr_generic_storage,64,32
	.lcomm	aes_128_ctr_generic_storage2,64,32
	
	# BSS symbols may also be emitted in .bss sections.
	.section .bss,"awT",@nobits
	.align 4
	.globl x
	.type   x, @object
	.size   x, 4
x:
.Lx_local_target:

	.zero 4
.Llocal:
	.quad 0
	.size .Llocal, 4

	# .bss handling is terminated by a .text directive.
	.text
	.section .bss,"awT",@nobits
y:
.Ly_local_target:

	.quad 0

	# Or a .section directive.
# WAS .section .rodata
.text
	.quad 0

	# Or the end of the file.
	.section .bss,"awT",@nobits
z:
.Lz_local_target:

	.quad 0
.text
.loc 1 2 0
BORINGSSL_bcm_text_end:
.type aes_128_ctr_generic_storage_bss_get, @function
aes_128_ctr_generic_storage_bss_get:
	leaq	aes_128_ctr_generic_storage(%rip), %rax
	ret
.type aes_128_ctr_generic_storage2_bss_get, @function
aes_128_ctr_generic_storage2_bss_get:
	leaq	aes_128_ctr_generic_storage2(%rip), %rax
	ret
.type x_bss_get, @function
x_bss_get:
	leaq	.Lx_local_target(%rip), %rax
	ret
.type y_bss_get, @function
y_bss_get:
	leaq	.Ly_local_target(%rip), %rax
	ret
.type z_bss_get, @function
z_bss_get:
	leaq	.Lz_local_target(%rip), %rax
	ret
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
