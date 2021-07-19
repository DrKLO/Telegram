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
	.zero 4
.Llocal:
	.quad 0
	.size .Llocal, 4

	# .bss handling is terminated by a .text directive.
	.text
	.section .bss,"awT",@nobits
y:
	.quad 0

	# Or a .section directive.
	.section .rodata
	.quad 0

	# Or the end of the file.
	.section .bss,"awT",@nobits
z:
	.quad 0
