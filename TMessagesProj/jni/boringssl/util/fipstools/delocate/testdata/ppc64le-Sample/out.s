.text
.file 1 "inserted_by_delocate.c"
.loc 1 1 0
BORINGSSL_bcm_text_start:
	.file	"foo.c"
	.abiversion 2
	.section	".toc","aw"
# WAS .section	".text"
.text
# WAS .section	.rodata
.text
	.align 3
	.type	kString, @object
	.size	kString, 12
.LkString_local_target:
kString:
	.string	"hello world"
	.globl kExportedString
	.align 3
	.type	kExportedString, @object
	.size	kExportedString, 26
.LkExportedString_local_target:
kExportedString:
	.string	"hello world, more visibly"
	.align 2
	.type	kGiantArray, @object
	.size	kGiantArray, 400000
.LkGiantArray_local_target:
kGiantArray:
	.long	1
	.long	0
	.zero	399992
	.lcomm	bss,20,4
	.type	bss, @object
	.align 3
.LC1:

	.string	"kString is %p\n"
	.align 3
.LC2:

	.string	"kExportedString is %p\n"
	.align 3
.LC4:

	.string	"function is %p\n"
	.align 3
.LC5:

	.string	"exported_function is %p\n"
	.align 3
.LC7:

	.string	"&kString[5] is %p\n"
	.align 3
.LC9:

	.string	"&kGiantArray[0x12345] is %p\n"
	.section	".toc","aw"
.LC0:

	.quad	stderr
.LC3:

	.quad	kExportedString
.LC6:

	.quad	exported_function
.LC8:

	.quad	kString+5
.LC10:

	.quad	kGiantArray+298260
# WAS .section	".text"
.text
	.align 2
	.type	function, @function
.Lfunction_local_target:
function:
0:
999:
	addis 2, 12, .LBORINGSSL_external_toc-999b@ha
	addi 2, 2, .LBORINGSSL_external_toc-999b@l
	ld 12, 0(2)
	add 2, 2, 12
# WAS addi 2,2,.TOC.-0b@l
	.localentry	function,.-function
.Lfunction_local_entry:
	mflr 0
	std 0,16(1)
	std 31,-8(1)
	stdu 1,-112(1)
	mr 31,1
# WAS addis 10,2,.LC0@toc@ha
# WAS ld 9,.LC0@toc@l(10)
	addi 1, 1, -288
	mflr 9
	std 9, -8(1)
	std 3, -16(1)
	bl .Lbcm_loadtoc__dot_LC0
	std 3, -24(1)
	ld 3, -8(1)
	mtlr 3
	ld 9, -24(1)
	ld 3, -16(1)
	addi 1, 1, 288
	ld 9, 0(9)
	ld 9,0(9)
	mr 3,9
# WAS addis 4,2,.LC1@toc@ha
# WAS addi 4,4,.LC1@toc@l
	addi 1, 1, -288
	mflr 4
	std 4, -8(1)
	std 3, -16(1)
	bl .Lbcm_loadtoc__dot_LC1
	std 3, -24(1)
	ld 3, -8(1)
	mtlr 3
	ld 4, -24(1)
	ld 3, -16(1)
	addi 1, 1, 288
# WAS addis 5,2,kString@toc@ha
# WAS addi 5,5,kString@toc@l
	addi 1, 1, -288
	mflr 5
	std 5, -8(1)
	std 3, -16(1)
	bl .Lbcm_loadtoc__dot_LkString_local_target
	std 3, -24(1)
	ld 3, -8(1)
	mtlr 3
	ld 5, -24(1)
	ld 3, -16(1)
	addi 1, 1, 288
# WAS bl fprintf
	bl	bcm_redirector_fprintf
	ld 2, 24(1)
	nop
# WAS addis 10,2,.LC0@toc@ha
# WAS ld 9,.LC0@toc@l(10)
	addi 1, 1, -288
	mflr 9
	std 9, -8(1)
	std 3, -16(1)
	bl .Lbcm_loadtoc__dot_LC0
	std 3, -24(1)
	ld 3, -8(1)
	mtlr 3
	ld 9, -24(1)
	ld 3, -16(1)
	addi 1, 1, 288
	ld 9, 0(9)
	ld 9,0(9)
	mr 3,9
# WAS addis 4,2,.LC2@toc@ha
# WAS addi 4,4,.LC2@toc@l
	addi 1, 1, -288
	mflr 4
	std 4, -8(1)
	std 3, -16(1)
	bl .Lbcm_loadtoc__dot_LC2
	std 3, -24(1)
	ld 3, -8(1)
	mtlr 3
	ld 4, -24(1)
	ld 3, -16(1)
	addi 1, 1, 288
# WAS addis 9,2,.LC3@toc@ha
# WAS ld 5,.LC3@toc@l(9)
	addi 1, 1, -288
	mflr 5
	std 5, -8(1)
	std 3, -16(1)
	bl .Lbcm_loadtoc__dot_LC3
	std 3, -24(1)
	ld 3, -8(1)
	mtlr 3
	ld 5, -24(1)
	ld 3, -16(1)
	addi 1, 1, 288
	ld 5, 0(5)
# WAS bl fprintf
	bl	bcm_redirector_fprintf
	ld 2, 24(1)
	nop
# WAS addis 10,2,.LC0@toc@ha
# WAS ld 9,.LC0@toc@l(10)
	addi 1, 1, -288
	mflr 9
	std 9, -8(1)
	std 3, -16(1)
	bl .Lbcm_loadtoc__dot_LC0
	std 3, -24(1)
	ld 3, -8(1)
	mtlr 3
	ld 9, -24(1)
	ld 3, -16(1)
	addi 1, 1, 288
	ld 9, 0(9)
	ld 9,0(9)
	mr 3,9
# WAS addis 4,2,.LC4@toc@ha
# WAS addi 4,4,.LC4@toc@l
	addi 1, 1, -288
	mflr 4
	std 4, -8(1)
	std 3, -16(1)
	bl .Lbcm_loadtoc__dot_LC4
	std 3, -24(1)
	ld 3, -8(1)
	mtlr 3
	ld 4, -24(1)
	ld 3, -16(1)
	addi 1, 1, 288
# WAS addis 5,2,function@toc@ha
# WAS addi 5,5,function@toc@l
	addi 1, 1, -288
	mflr 5
	std 5, -8(1)
	std 3, -16(1)
	bl .Lbcm_loadtoc__dot_Lfunction_local_target
	std 3, -24(1)
	ld 3, -8(1)
	mtlr 3
	ld 5, -24(1)
	ld 3, -16(1)
	addi 1, 1, 288
# WAS bl fprintf
	bl	bcm_redirector_fprintf
	ld 2, 24(1)
	nop
# WAS addis 10,2,.LC0@toc@ha
# WAS ld 9,.LC0@toc@l(10)
	addi 1, 1, -288
	mflr 9
	std 9, -8(1)
	std 3, -16(1)
	bl .Lbcm_loadtoc__dot_LC0
	std 3, -24(1)
	ld 3, -8(1)
	mtlr 3
	ld 9, -24(1)
	ld 3, -16(1)
	addi 1, 1, 288
	ld 9, 0(9)
	ld 9,0(9)
	mr 3,9
# WAS addis 4,2,.LC5@toc@ha
# WAS addi 4,4,.LC5@toc@l
	addi 1, 1, -288
	mflr 4
	std 4, -8(1)
	std 3, -16(1)
	bl .Lbcm_loadtoc__dot_LC5
	std 3, -24(1)
	ld 3, -8(1)
	mtlr 3
	ld 4, -24(1)
	ld 3, -16(1)
	addi 1, 1, 288
# WAS addis 9,2,.LC6@toc@ha
# WAS ld 5,.LC6@toc@l(9)
	addi 1, 1, -288
	mflr 5
	std 5, -8(1)
	std 3, -16(1)
	bl .Lbcm_loadtoc__dot_LC6
	std 3, -24(1)
	ld 3, -8(1)
	mtlr 3
	ld 5, -24(1)
	ld 3, -16(1)
	addi 1, 1, 288
	ld 5, 0(5)
# WAS bl fprintf
	bl	bcm_redirector_fprintf
	ld 2, 24(1)
	nop
# WAS addis 10,2,.LC0@toc@ha
# WAS ld 9,.LC0@toc@l(10)
	addi 1, 1, -288
	mflr 9
	std 9, -8(1)
	std 3, -16(1)
	bl .Lbcm_loadtoc__dot_LC0
	std 3, -24(1)
	ld 3, -8(1)
	mtlr 3
	ld 9, -24(1)
	ld 3, -16(1)
	addi 1, 1, 288
	ld 9, 0(9)
	ld 9,0(9)
	mr 3,9
# WAS addis 4,2,.LC7@toc@ha
# WAS addi 4,4,.LC7@toc@l
	addi 1, 1, -288
	mflr 4
	std 4, -8(1)
	std 3, -16(1)
	bl .Lbcm_loadtoc__dot_LC7
	std 3, -24(1)
	ld 3, -8(1)
	mtlr 3
	ld 4, -24(1)
	ld 3, -16(1)
	addi 1, 1, 288
# WAS addis 9,2,.LC8@toc@ha
# WAS ld 5,.LC8@toc@l(9)
	addi 1, 1, -288
	mflr 5
	std 5, -8(1)
	std 3, -16(1)
	bl .Lbcm_loadtoc__dot_LC8
	std 3, -24(1)
	ld 3, -8(1)
	mtlr 3
	ld 5, -24(1)
	ld 3, -16(1)
	addi 1, 1, 288
	ld 5, 0(5)
# WAS bl fprintf
	bl	bcm_redirector_fprintf
	ld 2, 24(1)
	nop
# WAS addis 10,2,.LC0@toc@ha
# WAS ld 9,.LC0@toc@l(10)
	addi 1, 1, -288
	mflr 9
	std 9, -8(1)
	std 3, -16(1)
	bl .Lbcm_loadtoc__dot_LC0
	std 3, -24(1)
	ld 3, -8(1)
	mtlr 3
	ld 9, -24(1)
	ld 3, -16(1)
	addi 1, 1, 288
	ld 9, 0(9)
	ld 9,0(9)
	mr 3,9
# WAS addis 4,2,.LC9@toc@ha
# WAS addi 4,4,.LC9@toc@l
	addi 1, 1, -288
	mflr 4
	std 4, -8(1)
	std 3, -16(1)
	bl .Lbcm_loadtoc__dot_LC9
	std 3, -24(1)
	ld 3, -8(1)
	mtlr 3
	ld 4, -24(1)
	ld 3, -16(1)
	addi 1, 1, 288
# WAS addis 9,2,.LC10@toc@ha
# WAS ld 5,.LC10@toc@l(9)
	addi 1, 1, -288
	mflr 5
	std 5, -8(1)
	std 3, -16(1)
	bl .Lbcm_loadtoc__dot_LC10
	std 3, -24(1)
	ld 3, -8(1)
	mtlr 3
	ld 5, -24(1)
	ld 3, -16(1)
	addi 1, 1, 288
	ld 5, 0(5)
# WAS bl fprintf
	bl	bcm_redirector_fprintf
	ld 2, 24(1)
	nop
# WAS bl exported_function
	bl	.Lexported_function_local_entry
	nop
	mr 3,9
	addi 1,31,112
	ld 0,16(1)
	mtlr 0
	ld 31,-8(1)
	blr
	.long 0
	.byte 0,0,0,1,128,1,0,1
	.size	function,.-function
	.align 2
	.globl exported_function
	.type	exported_function, @function
.Lexported_function_local_target:
exported_function:
0:
999:
	addis 2, 12, .LBORINGSSL_external_toc-999b@ha
	addi 2, 2, .LBORINGSSL_external_toc-999b@l
	ld 12, 0(2)
	add 2, 2, 12
# WAS addi 2,2,.TOC.-0b@l
	.localentry	exported_function,.-exported_function
.Lexported_function_local_entry:
	mflr 0
	std 0,16(1)
	std 31,-8(1)
	stdu 1,-48(1)
	mr 31,1
# WAS bl function
	bl	.Lfunction_local_entry
	mr 3,9
	addi 1,31,48
	ld 0,16(1)
	mtlr 0
	ld 31,-8(1)
	blr
	.long 0
	.byte 0,0,0,1,128,1,0,1
	.size	exported_function,.-exported_function
	.ident	"GCC: (Ubuntu 4.9.2-10ubuntu13) 4.9.2"
	.section	.note.GNU-stack,"",@progbits
.text
.loc 1 2 0
BORINGSSL_bcm_text_end:
.section ".toc", "aw"
.Lredirector_toc_fprintf:
.quad fprintf
.text
.type bcm_redirector_fprintf, @function
bcm_redirector_fprintf:
	std 2, 24(1)
	addis 12, 2, .Lredirector_toc_fprintf@toc@ha
	ld 12, .Lredirector_toc_fprintf@toc@l(12)
	mtctr 12
	bctr
.type bss_bss_get, @function
bss_bss_get:
	addis 3, 2, bss@toc@ha
	addi 3, 3, bss@toc@l
	blr
.type bcm_loadtoc__dot_LC0, @function
bcm_loadtoc__dot_LC0:
.Lbcm_loadtoc__dot_LC0:
	addis 3, 2, .LC0@toc@ha
	addi 3, 3, .LC0@toc@l
	blr
.type bcm_loadtoc__dot_LC1, @function
bcm_loadtoc__dot_LC1:
.Lbcm_loadtoc__dot_LC1:
	addis 3, 2, .LC1@toc@ha
	addi 3, 3, .LC1@toc@l
	blr
.type bcm_loadtoc__dot_LC10, @function
bcm_loadtoc__dot_LC10:
.Lbcm_loadtoc__dot_LC10:
	addis 3, 2, .LC10@toc@ha
	addi 3, 3, .LC10@toc@l
	blr
.type bcm_loadtoc__dot_LC2, @function
bcm_loadtoc__dot_LC2:
.Lbcm_loadtoc__dot_LC2:
	addis 3, 2, .LC2@toc@ha
	addi 3, 3, .LC2@toc@l
	blr
.type bcm_loadtoc__dot_LC3, @function
bcm_loadtoc__dot_LC3:
.Lbcm_loadtoc__dot_LC3:
	addis 3, 2, .LC3@toc@ha
	addi 3, 3, .LC3@toc@l
	blr
.type bcm_loadtoc__dot_LC4, @function
bcm_loadtoc__dot_LC4:
.Lbcm_loadtoc__dot_LC4:
	addis 3, 2, .LC4@toc@ha
	addi 3, 3, .LC4@toc@l
	blr
.type bcm_loadtoc__dot_LC5, @function
bcm_loadtoc__dot_LC5:
.Lbcm_loadtoc__dot_LC5:
	addis 3, 2, .LC5@toc@ha
	addi 3, 3, .LC5@toc@l
	blr
.type bcm_loadtoc__dot_LC6, @function
bcm_loadtoc__dot_LC6:
.Lbcm_loadtoc__dot_LC6:
	addis 3, 2, .LC6@toc@ha
	addi 3, 3, .LC6@toc@l
	blr
.type bcm_loadtoc__dot_LC7, @function
bcm_loadtoc__dot_LC7:
.Lbcm_loadtoc__dot_LC7:
	addis 3, 2, .LC7@toc@ha
	addi 3, 3, .LC7@toc@l
	blr
.type bcm_loadtoc__dot_LC8, @function
bcm_loadtoc__dot_LC8:
.Lbcm_loadtoc__dot_LC8:
	addis 3, 2, .LC8@toc@ha
	addi 3, 3, .LC8@toc@l
	blr
.type bcm_loadtoc__dot_LC9, @function
bcm_loadtoc__dot_LC9:
.Lbcm_loadtoc__dot_LC9:
	addis 3, 2, .LC9@toc@ha
	addi 3, 3, .LC9@toc@l
	blr
.type bcm_loadtoc__dot_Lfunction_local_target, @function
bcm_loadtoc__dot_Lfunction_local_target:
.Lbcm_loadtoc__dot_Lfunction_local_target:
	addis 3, 2, .Lfunction_local_target@toc@ha
	addi 3, 3, .Lfunction_local_target@toc@l
	blr
.type bcm_loadtoc__dot_LkString_local_target, @function
bcm_loadtoc__dot_LkString_local_target:
.Lbcm_loadtoc__dot_LkString_local_target:
	addis 3, 2, .LkString_local_target@toc@ha
	addi 3, 3, .LkString_local_target@toc@l
	blr
.LBORINGSSL_external_toc:
.quad .TOC.-.LBORINGSSL_external_toc
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
