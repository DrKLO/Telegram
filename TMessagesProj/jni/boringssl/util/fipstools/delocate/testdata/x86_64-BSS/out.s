.text
.file 1 "inserted_by_delocate.c"
.loc 1 1 0
BORINGSSL_bcm_text_start:
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
.Lx_local_target:

	.zero 4
.Llocal:
	.quad 0
	.size .Llocal, 4

	# .bss handling is terminated by a .text directive.
	.text
.Lnot_bss1_local_target:
not_bss1:
	ret

	# The .bss directive can introduce BSS.
	.bss
test:
.Ltest_local_target:

	.quad 0
	.text
.Lnot_bss2_local_target:
not_bss2:
	ret

	.section .bss,"awT",@nobits
y:
.Ly_local_target:

	.quad 0

	# A .section directive also terminates BSS.
# WAS .section .rodata
.text
	.quad 0

	# The end of the file terminates BSS.
	.section .bss,"awT",@nobits
z:
.Lz_local_target:

	.quad 0
.text
.loc 1 2 0
BORINGSSL_bcm_text_end:
.type aes_128_ctr_generic_storage_bss_get, @function
aes_128_ctr_generic_storage_bss_get:
	leaq	aes_128_ctr_generic_storage(%rip), %rax
	ret
.type aes_128_ctr_generic_storage2_bss_get, @function
aes_128_ctr_generic_storage2_bss_get:
	leaq	aes_128_ctr_generic_storage2(%rip), %rax
	ret
.type test_bss_get, @function
test_bss_get:
	leaq	.Ltest_local_target(%rip), %rax
	ret
.type x_bss_get, @function
x_bss_get:
	leaq	.Lx_local_target(%rip), %rax
	ret
.type y_bss_get, @function
y_bss_get:
	leaq	.Ly_local_target(%rip), %rax
	ret
.type z_bss_get, @function
z_bss_get:
	leaq	.Lz_local_target(%rip), %rax
	ret
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
