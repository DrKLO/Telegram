.text
.file 1 "inserted_by_delocate.c"
.loc 1 1 0
BORINGSSL_bcm_text_start:
	.text
.Lfoo_local_target:
foo:
.Lbar_local_target:
bar:
	# Test that GOTPCREL accesses get translated. They are handled
	# differently for local and external symbols.

# WAS pushq stderr@GOTPCREL(%rip)
	pushq %rax
	leaq -128(%rsp), %rsp
	pushf
	leaq stderr_GOTPCREL_external(%rip), %rax
	addq (%rax), %rax
	movq (%rax), %rax
	popf
	leaq	128(%rsp), %rsp
	xchg %rax, (%rsp)
# WAS pushq foo@GOTPCREL(%rip)
	pushq %rax
	leaq	.Lfoo_local_target(%rip), %rax
	xchg %rax, (%rsp)

# WAS movq stderr@GOTPCREL(%rip), %r11
	leaq -128(%rsp), %rsp
	pushf
	leaq stderr_GOTPCREL_external(%rip), %r11
	addq (%r11), %r11
	movq (%r11), %r11
	popf
	leaq	128(%rsp), %rsp
# WAS movq foo@GOTPCREL(%rip), %r11
	leaq	.Lfoo_local_target(%rip), %r11

# WAS vmovq stderr@GOTPCREL(%rip), %xmm0
	leaq -128(%rsp), %rsp
	pushq %rax
	pushf
	leaq stderr_GOTPCREL_external(%rip), %rax
	addq (%rax), %rax
	movq (%rax), %rax
	popf
	vmovq %rax, %xmm0
	popq %rax
	leaq 128(%rsp), %rsp
# WAS vmovq foo@GOTPCREL(%rip), %xmm0
	leaq -128(%rsp), %rsp
	pushq %rax
	leaq	.Lfoo_local_target(%rip), %rax
	vmovq %rax, %xmm0
	popq %rax
	leaq 128(%rsp), %rsp

# WAS cmoveq stderr@GOTPCREL(%rip), %r11
	jne 999f
	leaq -128(%rsp), %rsp
	pushf
	leaq stderr_GOTPCREL_external(%rip), %r11
	addq (%r11), %r11
	movq (%r11), %r11
	popf
	leaq	128(%rsp), %rsp
999:
# WAS cmoveq foo@GOTPCREL(%rip), %r11
	jne 999f
	leaq	.Lfoo_local_target(%rip), %r11
999:
# WAS cmovneq stderr@GOTPCREL(%rip), %r11
	je 999f
	leaq -128(%rsp), %rsp
	pushf
	leaq stderr_GOTPCREL_external(%rip), %r11
	addq (%r11), %r11
	movq (%r11), %r11
	popf
	leaq	128(%rsp), %rsp
999:
# WAS cmovneq foo@GOTPCREL(%rip), %r11
	je 999f
	leaq	.Lfoo_local_target(%rip), %r11
999:

# WAS movsd foo@GOTPCREL(%rip), %xmm0
	leaq -128(%rsp), %rsp
	pushq %rax
	leaq	.Lfoo_local_target(%rip), %rax
	movq %rax, %xmm0
	popq %rax
	leaq 128(%rsp), %rsp
# WAS vmovsd foo@GOTPCREL(%rip), %xmm0
	leaq -128(%rsp), %rsp
	pushq %rax
	leaq	.Lfoo_local_target(%rip), %rax
	vmovq %rax, %xmm0
	popq %rax
	leaq 128(%rsp), %rsp

	# movsd without arguments should be left as-is.
	movsd

	# Synthesized symbols do not use the GOT.
# WAS movq BORINGSSL_bcm_text_start@GOTPCREL(%rip), %r11
	leaq	BORINGSSL_bcm_text_start(%rip), %r11
# WAS movq foobar_bss_get@GOTPCREL(%rip), %r11
	leaq	foobar_bss_get(%rip), %r11

	# Transforming moves run the transform in-place after the load.
# WAS vpbroadcastq stderr@GOTPCREL(%rip), %xmm0
	leaq -128(%rsp), %rsp
	pushq %rax
	pushf
	leaq stderr_GOTPCREL_external(%rip), %rax
	addq (%rax), %rax
	movq (%rax), %rax
	popf
	vmovq %rax, %xmm0
	popq %rax
	leaq 128(%rsp), %rsp
	vpbroadcastq %xmm0, %xmm0
# WAS vpbroadcastq foo@GOTPCREL(%rip), %xmm0
	leaq -128(%rsp), %rsp
	pushq %rax
	leaq	.Lfoo_local_target(%rip), %rax
	vmovq %rax, %xmm0
	popq %rax
	leaq 128(%rsp), %rsp
	vpbroadcastq %xmm0, %xmm0

	# GCC sometimes loads a pair of pointers into an XMM register and
	# writes them together.
# WAS movq gcm_gmult_clmul@GOTPCREL(%rip), %xmm0
	leaq -128(%rsp), %rsp
	pushq %rax
	pushf
	leaq gcm_gmult_clmul_GOTPCREL_external(%rip), %rax
	addq (%rax), %rax
	movq (%rax), %rax
	popf
	movq %rax, %xmm0
	popq %rax
	leaq 128(%rsp), %rsp
# WAS movhps gcm_ghash_clmul@GOTPCREL(%rip), %xmm0
	leaq -128(%rsp), %rsp
	pushq %rax
	pushf
	leaq gcm_ghash_clmul_GOTPCREL_external(%rip), %rax
	addq (%rax), %rax
	movq (%rax), %rax
	popf
	pushq %rax
	movhps (%rsp), %xmm0
	leaq 8(%rsp), %rsp
	popq %rax
	leaq 128(%rsp), %rsp
	movaps %xmm0, (%rsp)

	# We've yet to observe this, but the above could also have been written
	# with movlps.
# WAS movhps gcm_ghash_clmul@GOTPCREL(%rip), %xmm0
	leaq -128(%rsp), %rsp
	pushq %rax
	pushf
	leaq gcm_ghash_clmul_GOTPCREL_external(%rip), %rax
	addq (%rax), %rax
	movq (%rax), %rax
	popf
	pushq %rax
	movhps (%rsp), %xmm0
	leaq 8(%rsp), %rsp
	popq %rax
	leaq 128(%rsp), %rsp
# WAS movlps gcm_gmult_clmul@GOTPCREL(%rip), %xmm0
	leaq -128(%rsp), %rsp
	pushq %rax
	pushf
	leaq gcm_gmult_clmul_GOTPCREL_external(%rip), %rax
	addq (%rax), %rax
	movq (%rax), %rax
	popf
	pushq %rax
	movlps (%rsp), %xmm0
	leaq 8(%rsp), %rsp
	popq %rax
	leaq 128(%rsp), %rsp
	movaps %xmm0, (%rsp)

	# Same as above, but with a local symbol.
# WAS movhps foo@GOTPCREL(%rip), %xmm0
	leaq -128(%rsp), %rsp
	pushq %rax
	leaq	.Lfoo_local_target(%rip), %rax
	pushq %rax
	movhps (%rsp), %xmm0
	leaq 8(%rsp), %rsp
	popq %rax
	leaq 128(%rsp), %rsp
# WAS movlps bar@GOTPCREL(%rip), %xmm0
	leaq -128(%rsp), %rsp
	pushq %rax
	leaq	.Lbar_local_target(%rip), %rax
	pushq %rax
	movlps (%rsp), %xmm0
	leaq 8(%rsp), %rsp
	popq %rax
	leaq 128(%rsp), %rsp
	movaps %xmm0, (%rsp)

# WAS cmpq foo@GOTPCREL(%rip), %rax
	leaq -128(%rsp), %rsp
	pushq %rbx
	leaq	.Lfoo_local_target(%rip), %rbx
	cmpq %rbx, %rax
	popq %rbx
	leaq 128(%rsp), %rsp
# WAS cmpq %rax, foo@GOTPCREL(%rip)
	leaq -128(%rsp), %rsp
	pushq %rbx
	leaq	.Lfoo_local_target(%rip), %rbx
	cmpq %rax, %rbx
	popq %rbx
	leaq 128(%rsp), %rsp

	# With -mcmodel=medium, the code may load the address of the GOT directly.
# WAS leaq _GLOBAL_OFFSET_TABLE_(%rip), %rcx
	leaq	.Lboringssl_got_delta(%rip), %rcx
	addq .Lboringssl_got_delta(%rip), %rcx

.comm foobar,64,32
.text
.loc 1 2 0
BORINGSSL_bcm_text_end:
.type foobar_bss_get, @function
foobar_bss_get:
	leaq	foobar(%rip), %rax
	ret
.type gcm_ghash_clmul_GOTPCREL_external, @object
.size gcm_ghash_clmul_GOTPCREL_external, 8
gcm_ghash_clmul_GOTPCREL_external:
	.long gcm_ghash_clmul@GOTPCREL
	.long 0
.type gcm_gmult_clmul_GOTPCREL_external, @object
.size gcm_gmult_clmul_GOTPCREL_external, 8
gcm_gmult_clmul_GOTPCREL_external:
	.long gcm_gmult_clmul@GOTPCREL
	.long 0
.type stderr_GOTPCREL_external, @object
.size stderr_GOTPCREL_external, 8
stderr_GOTPCREL_external:
	.long stderr@GOTPCREL
	.long 0
.Lboringssl_got_delta:
	.quad _GLOBAL_OFFSET_TABLE_-.Lboringssl_got_delta
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
