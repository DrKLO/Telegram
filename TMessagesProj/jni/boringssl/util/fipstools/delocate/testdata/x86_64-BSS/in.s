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
not_bss1:
	ret

	# The .bss directive can introduce BSS.
	.bss
test:
	.quad 0
	.text
not_bss2:
	ret

	.section .bss,"awT",@nobits
y:
	.quad 0

	# A .section directive also terminates BSS.
	.section .rodata
	.quad 0

	# The end of the file terminates BSS.
	.section .bss,"awT",@nobits
z:
	.quad 0
