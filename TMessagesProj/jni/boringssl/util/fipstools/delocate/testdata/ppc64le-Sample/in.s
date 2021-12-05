	.file	"foo.c"
	.abiversion 2
	.section	".toc","aw"
	.section	".text"
	.section	.rodata
	.align 3
	.type	kString, @object
	.size	kString, 12
kString:
	.string	"hello world"
	.globl kExportedString
	.align 3
	.type	kExportedString, @object
	.size	kExportedString, 26
kExportedString:
	.string	"hello world, more visibly"
	.align 2
	.type	kGiantArray, @object
	.size	kGiantArray, 400000
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
	.section	".text"
	.align 2
	.type	function, @function
function:
0:	addis 2,12,.TOC.-0b@ha
	addi 2,2,.TOC.-0b@l
	.localentry	function,.-function
	mflr 0
	std 0,16(1)
	std 31,-8(1)
	stdu 1,-112(1)
	mr 31,1
	addis 10,2,.LC0@toc@ha
	ld 9,.LC0@toc@l(10)
	ld 9,0(9)
	mr 3,9
	addis 4,2,.LC1@toc@ha
	addi 4,4,.LC1@toc@l
	addis 5,2,kString@toc@ha
	addi 5,5,kString@toc@l
	bl fprintf
	nop
	addis 10,2,.LC0@toc@ha
	ld 9,.LC0@toc@l(10)
	ld 9,0(9)
	mr 3,9
	addis 4,2,.LC2@toc@ha
	addi 4,4,.LC2@toc@l
	addis 9,2,.LC3@toc@ha
	ld 5,.LC3@toc@l(9)
	bl fprintf
	nop
	addis 10,2,.LC0@toc@ha
	ld 9,.LC0@toc@l(10)
	ld 9,0(9)
	mr 3,9
	addis 4,2,.LC4@toc@ha
	addi 4,4,.LC4@toc@l
	addis 5,2,function@toc@ha
	addi 5,5,function@toc@l
	bl fprintf
	nop
	addis 10,2,.LC0@toc@ha
	ld 9,.LC0@toc@l(10)
	ld 9,0(9)
	mr 3,9
	addis 4,2,.LC5@toc@ha
	addi 4,4,.LC5@toc@l
	addis 9,2,.LC6@toc@ha
	ld 5,.LC6@toc@l(9)
	bl fprintf
	nop
	addis 10,2,.LC0@toc@ha
	ld 9,.LC0@toc@l(10)
	ld 9,0(9)
	mr 3,9
	addis 4,2,.LC7@toc@ha
	addi 4,4,.LC7@toc@l
	addis 9,2,.LC8@toc@ha
	ld 5,.LC8@toc@l(9)
	bl fprintf
	nop
	addis 10,2,.LC0@toc@ha
	ld 9,.LC0@toc@l(10)
	ld 9,0(9)
	mr 3,9
	addis 4,2,.LC9@toc@ha
	addi 4,4,.LC9@toc@l
	addis 9,2,.LC10@toc@ha
	ld 5,.LC10@toc@l(9)
	bl fprintf
	nop
	bl exported_function
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
exported_function:
0:	addis 2,12,.TOC.-0b@ha
	addi 2,2,.TOC.-0b@l
	.localentry	exported_function,.-exported_function
	mflr 0
	std 0,16(1)
	std 31,-8(1)
	stdu 1,-48(1)
	mr 31,1
	bl function
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
