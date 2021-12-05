.text
.file 1 "inserted_by_delocate.c"
.loc 1 1 0
BORINGSSL_bcm_text_start:
	.type foo, @function
	.globl foo
.Lfoo_local_target:
foo:
	movq $0, %rax
	ret

.Lbar_local_target:
bar:
	# References to globals must be rewritten to their local targets.
# WAS call foo
	call	.Lfoo_local_target
# WAS jmp foo
	jmp	.Lfoo_local_target
# WAS jbe foo
	jbe	.Lfoo_local_target
# WAS jne foo
	jne	.Lfoo_local_target

	# Jumps to PLT symbols are rewritten through redirectors.
# WAS call memcpy@PLT
	call	bcm_redirector_memcpy
# WAS jmp memcpy@PLT
	jmp	bcm_redirector_memcpy
# WAS jbe memcpy@PLT
	jbe	bcm_redirector_memcpy

	# Jumps to local PLT symbols use their local targets.
# WAS call foo@PLT
	call	.Lfoo_local_target
# WAS jmp foo@PLT
	jmp	.Lfoo_local_target
# WAS jbe foo@PLT
	jbe	.Lfoo_local_target

	# Synthesized symbols are treated as local ones.
# WAS call OPENSSL_ia32cap_get@PLT
	call	.LOPENSSL_ia32cap_get_local_target

	# References to local labels are left as-is in the first file.
.Llocal_label:

	jbe .Llocal_label
	leaq .Llocal_label+2048(%rip), %r14
	leaq .Llocal_label+2048+1024(%rip), %r14

# WAS .section .rodata
.text
.L1:

	.quad 42
.L2:

	.quad .L2-.L1
	.uleb128 .L2-.L1
	.sleb128 .L2-.L1

	# Local labels and their jumps are left alone.
	.text
	jmp 1f
1:

	jmp 1b
	# References to local labels are rewrittenn in subsequent files.
.Llocal_label_BCM_1:

# WAS jbe .Llocal_label
	jbe	.Llocal_label_BCM_1
# WAS leaq .Llocal_label+2048(%rip), %r14
	leaq	.Llocal_label_BCM_1+2048(%rip), %r14
# WAS leaq .Llocal_label+2048+1024(%rip), %r14
	leaq	.Llocal_label_BCM_1+2048+1024(%rip), %r14

# WAS .section .rodata
.text
.L1_BCM_1:

	.quad 42
.L2_BCM_1:

# WAS .quad .L2-.L1
	.quad	.L2_BCM_1-.L1_BCM_1
# WAS .uleb128 .L2-.L1
	.uleb128	.L2_BCM_1-.L1_BCM_1
# WAS .sleb128 .L2-.L1
	.sleb128	.L2_BCM_1-.L1_BCM_1

.text
.loc 1 2 0
BORINGSSL_bcm_text_end:
.type bcm_redirector_memcpy, @function
bcm_redirector_memcpy:
	jmp	memcpy@PLT
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
