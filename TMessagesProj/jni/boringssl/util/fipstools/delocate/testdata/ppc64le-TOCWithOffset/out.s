.text
.file 1 "inserted_by_delocate.c"
.loc 1 1 0
BORINGSSL_bcm_text_start:
	.text
.Lfoo_local_target:
foo:
	# TOC references may have offsets.
# WAS addis 3, 2, 5+foo@toc@ha
# WAS addi 3, 3, 10+foo@toc@l
	addi 1, 1, -288
	mflr 3
	std 3, -8(1)
	bl .Lbcm_loadtoc__dot_Lfoo_local_target__plus_10
	std 3, -24(1)
	ld 3, -8(1)
	mtlr 3
	ld 3, -24(1)
	addi 1, 1, 288

# WAS addis 3, 2, 15+foo@toc@ha
# WAS addi 3, 3, 20+foo@toc@l
	addi 1, 1, -288
	mflr 3
	std 3, -8(1)
	bl .Lbcm_loadtoc__dot_Lfoo_local_target__plus_20
	std 3, -24(1)
	ld 3, -8(1)
	mtlr 3
	ld 3, -24(1)
	addi 1, 1, 288

# WAS addis 4, 2, foo@toc@ha
# WAS addi 4, 4, foo@toc@l
	addi 1, 1, -288
	mflr 4
	std 4, -8(1)
	std 3, -16(1)
	bl .Lbcm_loadtoc__dot_Lfoo_local_target
	std 3, -24(1)
	ld 3, -8(1)
	mtlr 3
	ld 4, -24(1)
	ld 3, -16(1)
	addi 1, 1, 288

# WAS addis 5, 2, 5+foo@toc@ha
# WAS ld 5, 10+foo@toc@l(5)
	addi 1, 1, -288
	mflr 5
	std 5, -8(1)
	std 3, -16(1)
	bl .Lbcm_loadtoc__dot_Lfoo_local_target__plus_10
	std 3, -24(1)
	ld 3, -8(1)
	mtlr 3
	ld 5, -24(1)
	ld 3, -16(1)
	addi 1, 1, 288
	ld 5, 0(5)

# WAS addis 4, 2, foo-10@toc@ha
# WAS addi 4, 4, foo-10@toc@l
	addi 1, 1, -288
	mflr 4
	std 4, -8(1)
	std 3, -16(1)
	bl .Lbcm_loadtoc__dot_Lfoo_local_target__minus_10
	std 3, -24(1)
	ld 3, -8(1)
	mtlr 3
	ld 4, -24(1)
	ld 3, -16(1)
	addi 1, 1, 288

# WAS addis 4, 2, foo@toc@ha+25
# WAS addi 4, 4, foo@toc@l+25
	addi 1, 1, -288
	mflr 4
	std 4, -8(1)
	std 3, -16(1)
	bl .Lbcm_loadtoc__dot_Lfoo_local_target__plus_25
	std 3, -24(1)
	ld 3, -8(1)
	mtlr 3
	ld 4, -24(1)
	ld 3, -16(1)
	addi 1, 1, 288

# WAS addis 4, 2, 1+foo-2@toc@ha+3
# WAS addi 4, 4, 1+foo-2@toc@l+3
	addi 1, 1, -288
	mflr 4
	std 4, -8(1)
	std 3, -16(1)
	bl .Lbcm_loadtoc__dot_Lfoo_local_target__plus_1_minus_2_plus_3
	std 3, -24(1)
	ld 3, -8(1)
	mtlr 3
	ld 4, -24(1)
	ld 3, -16(1)
	addi 1, 1, 288
.text
.loc 1 2 0
BORINGSSL_bcm_text_end:
.type bcm_loadtoc__dot_Lfoo_local_target, @function
bcm_loadtoc__dot_Lfoo_local_target:
.Lbcm_loadtoc__dot_Lfoo_local_target:
	addis 3, 2, .Lfoo_local_target@toc@ha
	addi 3, 3, .Lfoo_local_target@toc@l
	blr
.type bcm_loadtoc__dot_Lfoo_local_target__plus_1_minus_2_plus_3, @function
bcm_loadtoc__dot_Lfoo_local_target__plus_1_minus_2_plus_3:
.Lbcm_loadtoc__dot_Lfoo_local_target__plus_1_minus_2_plus_3:
	addis 3, 2, .Lfoo_local_target+1-2+3@toc@ha
	addi 3, 3, .Lfoo_local_target+1-2+3@toc@l
	blr
.type bcm_loadtoc__dot_Lfoo_local_target__plus_10, @function
bcm_loadtoc__dot_Lfoo_local_target__plus_10:
.Lbcm_loadtoc__dot_Lfoo_local_target__plus_10:
	addis 3, 2, .Lfoo_local_target+10@toc@ha
	addi 3, 3, .Lfoo_local_target+10@toc@l
	blr
.type bcm_loadtoc__dot_Lfoo_local_target__plus_20, @function
bcm_loadtoc__dot_Lfoo_local_target__plus_20:
.Lbcm_loadtoc__dot_Lfoo_local_target__plus_20:
	addis 3, 2, .Lfoo_local_target+20@toc@ha
	addi 3, 3, .Lfoo_local_target+20@toc@l
	blr
.type bcm_loadtoc__dot_Lfoo_local_target__plus_25, @function
bcm_loadtoc__dot_Lfoo_local_target__plus_25:
.Lbcm_loadtoc__dot_Lfoo_local_target__plus_25:
	addis 3, 2, .Lfoo_local_target+25@toc@ha
	addi 3, 3, .Lfoo_local_target+25@toc@l
	blr
.type bcm_loadtoc__dot_Lfoo_local_target__minus_10, @function
bcm_loadtoc__dot_Lfoo_local_target__minus_10:
.Lbcm_loadtoc__dot_Lfoo_local_target__minus_10:
	addis 3, 2, .Lfoo_local_target-10@toc@ha
	addi 3, 3, .Lfoo_local_target-10@toc@l
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
