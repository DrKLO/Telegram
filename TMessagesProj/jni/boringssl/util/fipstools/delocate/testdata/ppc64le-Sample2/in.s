	.file	"foo.c"
	.abiversion 2
	.section	".toc","aw"
	.section	".text"
	.section	".toc","aw"
.LC0:
	.quad	stderr
.LC3:
	.quad	kExportedString
.LC6:
	.quad	exported_function
	.section	".text"
	.align 2
	.p2align 4,,15
	.globl exported_function
	.type	exported_function, @function
exported_function:
0:	addis 2,12,.TOC.-0b@ha
	addi 2,2,.TOC.-0b@l
	.localentry	exported_function,.-exported_function
	mflr 0
	std 19,-104(1)
	std 20,-96(1)
	std 21,-88(1)
	std 22,-80(1)
	addis 21,2,.LC1@toc@ha
	addis 22,2,.LC2@toc@ha
	std 23,-72(1)
	std 24,-64(1)
	addis 23,2,.LC4@toc@ha
	addis 24,2,function@toc@ha
	std 25,-56(1)
	std 26,-48(1)
	addis 25,2,.LC5@toc@ha
	addis 26,2,.LC7@toc@ha
	std 27,-40(1)
	std 28,-32(1)
	addis 28,2,.LC8@toc@ha
	addi 21,21,.LC1@toc@l
	std 29,-24(1)
	std 30,-16(1)
	addis 29,2,.LANCHOR0@toc@ha
	addi 22,22,.LC2@toc@l
	std 31,-8(1)
	std 0,16(1)
	addi 29,29,.LANCHOR0@toc@l
	addi 23,23,.LC4@toc@l
	stdu 1,-208(1)
	addis 31,2,.LC0@toc@ha		# gpr load fusion, type long
	ld 31,.LC0@toc@l(31)
	addis 19,2,.LC3@toc@ha		# gpr load fusion, type long
	ld 19,.LC3@toc@l(19)
	addis 30,29,0x5
	addi 24,24,function@toc@l
	addis 20,2,.LC6@toc@ha		# gpr load fusion, type long
	ld 20,.LC6@toc@l(20)
	addi 25,25,.LC5@toc@l
	addi 26,26,.LC7@toc@l
	addi 27,29,5
	addi 28,28,.LC8@toc@l
	addi 30,30,-29404
	.p2align 4,,15
.L2:
	ld 3,0(31)
	mr 5,21
	mr 6,29
	li 4,1
	bl __fprintf_chk
	nop
	ld 3,0(31)
	mr 5,22
	mr 6,19
	li 4,1
	bl __fprintf_chk
	nop
	ld 3,0(31)
	mr 5,23
	mr 6,24
	li 4,1
	bl __fprintf_chk
	nop
	ld 3,0(31)
	mr 5,25
	mr 6,20
	li 4,1
	bl __fprintf_chk
	nop
	ld 3,0(31)
	mr 5,26
	mr 6,27
	li 4,1
	bl __fprintf_chk
	nop
	ld 3,0(31)
	li 4,1
	mr 5,28
	mr 6,30
	bl __fprintf_chk
	nop
	b .L2
	.long 0
	.byte 0,0,0,1,128,13,0,0
	.size	exported_function,.-exported_function
	.section	".toc","aw"
	.set .LC11,.LC0
	.set .LC12,.LC3
	.set .LC13,.LC6
	.section	".text"
	.align 2
	.p2align 4,,15
	.type	function, @function
function:
0:	addis 2,12,.TOC.-0b@ha
	addi 2,2,.TOC.-0b@l
	.localentry	function,.-function
	mflr 0
	std 31,-8(1)
	addis 31,2,.LC11@toc@ha		# gpr load fusion, type long
	ld 31,.LC11@toc@l(31)
	addis 5,2,.LC1@toc@ha
	std 30,-16(1)
	addis 30,2,.LANCHOR0@toc@ha
	addi 5,5,.LC1@toc@l
	addi 30,30,.LANCHOR0@toc@l
	li 4,1
	mr 6,30
	std 0,16(1)
	stdu 1,-112(1)
	ld 3,0(31)
	bl __fprintf_chk
	nop
	addis 6,2,.LC12@toc@ha		# gpr load fusion, type long
	ld 6,.LC12@toc@l(6)
	ld 3,0(31)
	addis 5,2,.LC2@toc@ha
	li 4,1
	addi 5,5,.LC2@toc@l
	bl __fprintf_chk
	nop
	ld 3,0(31)
	addis 5,2,.LC4@toc@ha
	addis 6,2,function@toc@ha
	addi 5,5,.LC4@toc@l
	addi 6,6,function@toc@l
	li 4,1
	bl __fprintf_chk
	nop
	addis 6,2,.LC13@toc@ha		# gpr load fusion, type long
	ld 6,.LC13@toc@l(6)
	ld 3,0(31)
	addis 5,2,.LC5@toc@ha
	li 4,1
	addi 5,5,.LC5@toc@l
	bl __fprintf_chk
	nop
	ld 3,0(31)
	addis 5,2,.LC7@toc@ha
	addi 6,30,5
	addi 5,5,.LC7@toc@l
	li 4,1
	bl __fprintf_chk
	nop
	ld 3,0(31)
	addis 6,30,0x5
	addis 5,2,.LC8@toc@ha
	li 4,1
	addi 5,5,.LC8@toc@l
	addi 6,6,-29404
	bl __fprintf_chk
	nop
	bl exported_function
	nop
	addi 1,1,112
	ld 0,16(1)
	ld 30,-16(1)
	ld 31,-8(1)
	mtlr 0
	blr
	.long 0
	.byte 0,0,0,1,128,2,0,0
	.size	function,.-function
	.globl kExportedString
	.section	.rodata
	.align 4
	.set	.LANCHOR0,. + 0
	.type	kString, @object
	.size	kString, 12
kString:
	.string	"hello world"
	.zero	4
	.type	kGiantArray, @object
	.size	kGiantArray, 400000
kGiantArray:
	.long	1
	.long	0
	.zero	399992
	.type	kExportedString, @object
	.size	kExportedString, 26
kExportedString:
	.string	"hello world, more visibly"
	.section	.rodata.str1.8,"aMS",@progbits,1
	.align 3
.LC1:
	.string	"kString is %p\n"
	.zero	1
.LC2:
	.string	"kExportedString is %p\n"
	.zero	1
.LC4:
	.string	"function is %p\n"
.LC5:
	.string	"exported_function is %p\n"
	.zero	7
.LC7:
	.string	"&kString[5] is %p\n"
	.zero	5
.LC8:
	.string	"&kGiantArray[0x12345] is %p\n"
	.section	".bss"
	.align 2
	.type	bss, @object
	.size	bss, 20
bss:
	.zero	20
	.ident	"GCC: (Ubuntu 4.9.2-10ubuntu13) 4.9.2"
	.section	.note.GNU-stack,"",@progbits
