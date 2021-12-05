	.text
foo:
	# leaq of OPENSSL_ia32cap_P is supported.
	leaq OPENSSL_ia32cap_P(%rip), %r11

	# As is the equivalent GOTPCREL movq.
	movq OPENSSL_ia32cap_P@GOTPCREL(%rip), %r12

	# And a non-movq instruction via the GOT.
	orq OPENSSL_ia32cap_P@GOTPCREL(%rip), %r12

	# ... which targets the default temp register
	orq OPENSSL_ia32cap_P@GOTPCREL(%rip), %rax

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
	movq OPENSSL_ia32cap_get@GOTPCREL(%rip), %r11

	# Transforming moves run the transform in-place after the load.
	vpbroadcastq stderr@GOTPCREL(%rip), %xmm0
	vpbroadcastq foo@GOTPCREL(%rip), %xmm0

.comm foobar,64,32
