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
# WAS notrack jmp foo
	notrack	jmp	.Lfoo_local_target
# WAS jbe foo
	jbe	.Lfoo_local_target
# WAS jne foo
	jne	.Lfoo_local_target

	# This also applies to symbols defined with .set
# WAS call foo1
	call	.Lfoo1_local_target
# WAS call foo2
	call	.Lfoo2_local_target
# WAS call foo3
	call	.Lfoo3_local_target

	# Jumps to PLT symbols are rewritten through redirectors.
# WAS call memcpy@PLT
	call	bcm_redirector_memcpy
# WAS jmp memcpy@PLT
	jmp	bcm_redirector_memcpy
# WAS notrack jmp memcpy@PLT
	notrack	jmp	bcm_redirector_memcpy
# WAS jbe memcpy@PLT
	jbe	bcm_redirector_memcpy

	# Jumps to local PLT symbols use their local targets.
# WAS call foo@PLT
	call	.Lfoo_local_target
# WAS jmp foo@PLT
	jmp	.Lfoo_local_target
# WAS notrack jmp foo@PLT
	notrack	jmp	.Lfoo_local_target
# WAS jbe foo@PLT
	jbe	.Lfoo_local_target

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
2:


	.quad 2b - 1b
	.quad 2b - .L2

	# .set directives should get local targets and have their references (above)
	# rewritten.
	.globl foo1
	.globl foo2
	.globl foo3
	.set foo1, foo
	.set	.Lfoo1_local_target, foo
	.equ foo2, foo
	.equ	.Lfoo2_local_target, foo
	.equiv foo3, foo
	.equiv	.Lfoo3_local_target, foo
	# References to local labels are rewritten in subsequent files.
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

# .byte was not parsed as a symbol-containing directive on the
# assumption that it's too small to hold a pointer. But Clang
# will store offsets in it.
# WAS .byte   (.LBB231_40-.LBB231_19)>>2, 4, .Lfoo, (.Lfoo), .Lfoo<<400, (   .Lfoo ) <<  66
	.byte	(.LBB231_40_BCM_1-.LBB231_19_BCM_1)>>2, 4, .Lfoo_BCM_1, (.Lfoo_BCM_1), .Lfoo_BCM_1<<400, (.Lfoo_BCM_1)<<66
.byte   421

# .set directives defining local symbols should be rewritten.
# WAS .set .Llocally_set_symbol1, 1
	.set	.Llocally_set_symbol1_BCM_1, 1
# WAS .equ .Llocally_set_symbol2, 2
	.equ	.Llocally_set_symbol2_BCM_1, 2
# WAS .equiv .Llocally_set_symbol3, 3
	.equiv	.Llocally_set_symbol3_BCM_1, 3

# References to local symbols in .set directives should be rewritten.
# WAS .set alias_to_local_label, .Llocal_label
	.set	alias_to_local_label, .Llocal_label_BCM_1
	.set	.Lalias_to_local_label_local_target, .Llocal_label_BCM_1
# WAS .equ alias_to_local_label, .Llocal_label
	.equ	alias_to_local_label, .Llocal_label_BCM_1
	.equ	.Lalias_to_local_label_local_target, .Llocal_label_BCM_1
# WAS .equiv alias_to_local_label, .Llocal_label
	.equiv	alias_to_local_label, .Llocal_label_BCM_1
	.equiv	.Lalias_to_local_label_local_target, .Llocal_label_BCM_1
# WAS .set .Llocal_alias_to_local_label, .Llocal_label
	.set	.Llocal_alias_to_local_label_BCM_1, .Llocal_label_BCM_1
# WAS .equ .Llocal_alias_to_local_label, .Llocal_label
	.equ	.Llocal_alias_to_local_label_BCM_1, .Llocal_label_BCM_1
# WAS .equiv .Llocal_alias_to_local_label, .Llocal_label
	.equiv	.Llocal_alias_to_local_label_BCM_1, .Llocal_label_BCM_1

	# When rewritten, AVX-512 tokens are preserved.
# WAS vpcmpneqq .Llabel(%rip){1to8}, %zmm1, %k0
	vpcmpneqq	.Llabel_BCM_1(%rip){1to8}, %zmm1, %k0
.text
.loc 1 2 0
BORINGSSL_bcm_text_end:
.type bcm_redirector_memcpy, @function
bcm_redirector_memcpy:
	jmp	memcpy@PLT
.type BORINGSSL_bcm_text_hash, @object
.size BORINGSSL_bcm_text_hash, 32
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
