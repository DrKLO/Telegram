.text
.file 1 "inserted_by_delocate.c"
.loc 1 1 0
BORINGSSL_bcm_text_start:
	.text
.Lfoo_local_target:
foo:
	# leaq of OPENSSL_ia32cap_P is supported.
# WAS leaq OPENSSL_ia32cap_P(%rip), %r11
	leaq -128(%rsp), %rsp
	pushfq
	leaq	OPENSSL_ia32cap_addr_delta(%rip), %r11
	addq	(%r11), %r11
	popfq
	leaq 128(%rsp), %rsp

	# As is the equivalent GOTPCREL movq.
# WAS movq OPENSSL_ia32cap_P@GOTPCREL(%rip), %r12
	leaq -128(%rsp), %rsp
	pushfq
	leaq	OPENSSL_ia32cap_addr_delta(%rip), %r12
	addq	(%r12), %r12
	popfq
	leaq 128(%rsp), %rsp

	# And a non-movq instruction via the GOT.
# WAS orq OPENSSL_ia32cap_P@GOTPCREL(%rip), %r12
	leaq -128(%rsp), %rsp
	pushq %rax
	pushfq
	leaq	OPENSSL_ia32cap_addr_delta(%rip), %rax
	addq	(%rax), %rax
	popfq
	orq %rax, %r12
	popq %rax
	leaq 128(%rsp), %rsp

	# ... which targets the default temp register
# WAS orq OPENSSL_ia32cap_P@GOTPCREL(%rip), %rax
	leaq -128(%rsp), %rsp
	pushq %rbx
	pushfq
	leaq	OPENSSL_ia32cap_addr_delta(%rip), %rbx
	addq	(%rbx), %rbx
	popfq
	orq %rbx, %rax
	popq %rbx
	leaq 128(%rsp), %rsp

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
# WAS movq OPENSSL_ia32cap_get@GOTPCREL(%rip), %r11
	leaq	.LOPENSSL_ia32cap_get_local_target(%rip), %r11

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

.comm foobar,64,32
.text
.loc 1 2 0
BORINGSSL_bcm_text_end:
.type foobar_bss_get, @function
foobar_bss_get:
	leaq	foobar(%rip), %rax
	ret
.type stderr_GOTPCREL_external, @object
.size stderr_GOTPCREL_external, 8
stderr_GOTPCREL_external:
	.long stderr@GOTPCREL
	.long 0
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
