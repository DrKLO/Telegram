	.type foo, @function
	.globl foo
foo:
	movq	%rax, %rax
	shrxq	%rbx, kBoringSSLRSASqrtTwo@GOTPCREL(%rip), %rax
	shrxq	kBoringSSLRSASqrtTwo@GOTPCREL(%rip), %rbx, %rax


	.type	kBoringSSLRSASqrtTwo,@object # @kBoringSSLRSASqrtTwo
	.section	.rodata,"a",@progbits,unique,760
	.globl	kBoringSSLRSASqrtTwo
	.p2align	4
kBoringSSLRSASqrtTwo:
	.quad	-2404814165548301886    # 0xdea06241f7aa81c2
