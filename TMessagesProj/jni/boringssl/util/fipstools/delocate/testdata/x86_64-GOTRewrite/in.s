	.text
foo:
bar:
	# Test that GOTPCREL accesses get translated. They are handled
	# differently for local and external symbols.

	pushq stderr@GOTPCREL(%rip)
	pushq foo@GOTPCREL(%rip)

	movq stderr@GOTPCREL(%rip), %r11
	movq foo@GOTPCREL(%rip), %r11

	vmovq stderr@GOTPCREL(%rip), %xmm0
	vmovq foo@GOTPCREL(%rip), %xmm0

	cmoveq stderr@GOTPCREL(%rip), %r11
	cmoveq foo@GOTPCREL(%rip), %r11
	cmovneq stderr@GOTPCREL(%rip), %r11
	cmovneq foo@GOTPCREL(%rip), %r11

	movsd foo@GOTPCREL(%rip), %xmm0
	vmovsd foo@GOTPCREL(%rip), %xmm0

	# movsd without arguments should be left as-is.
	movsd

	# Synthesized symbols do not use the GOT.
	movq BORINGSSL_bcm_text_start@GOTPCREL(%rip), %r11
	movq foobar_bss_get@GOTPCREL(%rip), %r11

	# Transforming moves run the transform in-place after the load.
	vpbroadcastq stderr@GOTPCREL(%rip), %xmm0
	vpbroadcastq foo@GOTPCREL(%rip), %xmm0

	# GCC sometimes loads a pair of pointers into an XMM register and
	# writes them together.
	movq gcm_gmult_clmul@GOTPCREL(%rip), %xmm0
	movhps gcm_ghash_clmul@GOTPCREL(%rip), %xmm0
	movaps %xmm0, (%rsp)

	# We've yet to observe this, but the above could also have been written
	# with movlps.
	movhps gcm_ghash_clmul@GOTPCREL(%rip), %xmm0
	movlps gcm_gmult_clmul@GOTPCREL(%rip), %xmm0
	movaps %xmm0, (%rsp)

	# Same as above, but with a local symbol.
	movhps foo@GOTPCREL(%rip), %xmm0
	movlps bar@GOTPCREL(%rip), %xmm0
	movaps %xmm0, (%rsp)

	cmpq foo@GOTPCREL(%rip), %rax
	cmpq %rax, foo@GOTPCREL(%rip)

	# With -mcmodel=medium, the code may load the address of the GOT directly.
	leaq _GLOBAL_OFFSET_TABLE_(%rip), %rcx

.comm foobar,64,32
