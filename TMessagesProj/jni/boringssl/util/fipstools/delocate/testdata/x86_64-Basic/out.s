.text
.file 2 "inserted_by_delocate.c"
.loc 2 1 0
BORINGSSL_bcm_text_start:
	# Most instructions and lines should pass unaltered. This is made up of
	# copy-and-pasted bits of compiler output and likely does not actually
	# run.
	.file "bcm.c"
	.text

	.type foo, @function
	.globl foo
.Lfoo_local_target:
foo:
	.file 1 "../foo/bar.c"
	.loc 1 2 3
	.cfi_startproc
	pushq %rbp
	.cfi_def_cfa_offset 16
	.cfi_offset 6, -16
	.cfi_adjust_cfa_offset 32*5+8
	movq %rsp, %rbp
	movq %rdi, -24(%rbp)
	movq -24(%rbp), %rax
	.loc 1 168 0 is_stmt 0 discriminator 1
	cmpq	-8(%rbp), %rax
	jmpq *%rax
	notrack jmp *%rax
        movdqa  %xmm3,%xmm10
	psrlq   $1,%xmm3
	pxor    %xmm6,%xmm5
	pxor    %xmm4,%xmm3
	pand    %xmm7,%xmm5
	pand    %xmm7,%xmm3
        pxor    %xmm5,%xmm6
	paddd   112(%r11),%xmm15
	vmovdqa %xmm0,%xmm5
	vpunpckhqdq     %xmm0,%xmm0,%xmm3
	vpxor   %xmm0,%xmm3,%xmm3
	vpclmulqdq      $0x11,%xmm2,%xmm0,%xmm1
	vpclmulqdq      $0x00,%xmm2,%xmm0,%xmm0
	vpclmulqdq      $0x00,%xmm6,%xmm3,%xmm3
	vpxor   %xmm0,%xmm1,%xmm4
	vpxor   %xmm4,%xmm3,%xmm3
	vmovdqu8        %ymm1,%ymm6{%k1}{z}
	vmovdqu8        %ymm2,%ymm4{%k3}
	vpcmpneqq       .LCPI508_30(%rip){1to8}, %zmm1, %k0
	vmovdqu64       -88(%rbx), %zmm0 {%k1}
	vmovdqu64       352(%rsp,%rbx), %ymm1 {%k1}
	.byte   0xf3,0xc3
	movq %rax, %rbx # Comments can be on the same line as an instruction.
.L3:
 # Or on the same line as a label.
.L4:
.L5:
	movq %rbx, %rax # This is also legal.
.size	foo, .-foo
.type	foo, @function
.uleb128 .foo-1-.bar
.text
.loc 2 2 0
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
