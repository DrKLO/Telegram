.text
.file 1 "inserted_by_delocate.c"
.loc 1 1 0
BORINGSSL_bcm_text_start:
	.type foo, @function
	.globl foo
.Lfoo_local_target:
foo:
	movq	%rax, %rax
# WAS shrxq	%rbx, kBoringSSLRSASqrtTwo@GOTPCREL(%rip), %rax
	leaq -128(%rsp), %rsp
	pushq %rcx
	leaq	.LkBoringSSLRSASqrtTwo_local_target(%rip), %rcx
	shrxq %rbx, %rcx, %rax
	popq %rcx
	leaq 128(%rsp), %rsp
# WAS shrxq	kBoringSSLRSASqrtTwo@GOTPCREL(%rip), %rbx, %rax
	leaq -128(%rsp), %rsp
	pushq %rcx
	leaq	.LkBoringSSLRSASqrtTwo_local_target(%rip), %rcx
	shrxq %rcx, %rbx, %rax
	popq %rcx
	leaq 128(%rsp), %rsp


	.type	kBoringSSLRSASqrtTwo,@object # @kBoringSSLRSASqrtTwo
# WAS .section	.rodata,"a",@progbits,unique,760
.text
	.globl	kBoringSSLRSASqrtTwo
	.p2align	4
.LkBoringSSLRSASqrtTwo_local_target:
kBoringSSLRSASqrtTwo:
	.quad	-2404814165548301886    # 0xdea06241f7aa81c2
.text
.loc 1 2 0
BORINGSSL_bcm_text_end:
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
