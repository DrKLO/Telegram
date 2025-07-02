.text
.file 1 "inserted_by_delocate.c"
.loc 1 1 0
BORINGSSL_bcm_text_start:
	.type foo, %function
	.globl foo
.Lfoo_local_target:
foo:
	// GOT load
// WAS adrp x1, :got:stderr
	sub sp, sp, 128
	stp x0, lr, [sp, #-16]!
	bl .Lboringssl_loadgot_stderr
	mov x1, x0
	ldp x0, lr, [sp], #16
	add sp, sp, 128
// WAS ldr x0, [x1, :got_lo12:stderr]
	mov x0, x1

	// GOT load to x0
// WAS adrp x0, :got:stderr
	sub sp, sp, 128
	stp x0, lr, [sp, #-16]!
	bl .Lboringssl_loadgot_stderr
	ldp xzr, lr, [sp], #16
	add sp, sp, 128
// WAS ldr x1, [x0, :got_lo12:stderr]
	mov x1, x0

	// GOT load with no register move
// WAS adrp x0, :got:stderr
	sub sp, sp, 128
	stp x0, lr, [sp, #-16]!
	bl .Lboringssl_loadgot_stderr
	ldp xzr, lr, [sp], #16
	add sp, sp, 128
// WAS ldr x0, [x0, :got_lo12:stderr]

	// Address load
// WAS adrp x0, .Llocal_data
	adr x0, .Llocal_data
// WAS add x1, x0, :lo12:.Llocal_data
	add	x1, x0, #0

	// Address of local symbol with offset
// WAS adrp x10, .Llocal_data2+16
	adr x10, .Llocal_data2+16
// WAS add x11, x10, :lo12:.Llocal_data2+16
	add	x11, x10, #0

	// Address load with no-op add instruction
// WAS adrp x0, .Llocal_data
	adr x0, .Llocal_data
// WAS add x0, x0, :lo12:.Llocal_data

	// Load from local symbol
// WAS adrp x10, .Llocal_data2
	adr x10, .Llocal_data2
// WAS ldr q0, [x10, :lo12:.Llocal_data2]
	ldr	q0, [x10]

	// Load from local symbol with offset
// WAS adrp x10, .Llocal_data2+16
	adr x10, .Llocal_data2+16
// WAS ldr q0, [x10, :lo12:.Llocal_data2+16]
	ldr	q0, [x10]

// WAS bl local_function
	bl	.Llocal_function_local_target

// WAS bl remote_function
	bl	bcm_redirector_remote_function

	bl bss_symbol_bss_get

	// Regression test for a two-digit index.
	ld1 { v1.b }[10], [x9]

	// Ensure that registers aren't interpreted as symbols.
	add x0, x0
	add x12, x12
	add w0, x0
	add w12, x12
	add d0, d0
	add d12, d12
	add q0, q0
	add q12, q12
	add s0, s0
	add s12, s12
	add h0, h0
	add h12, h12
	add b0, b0
	add b12, b12

	// But 'y' is not a register prefix so far, so these should be
	// processed as symbols.
// WAS add y0, y0
	add	bcm_redirector_y0, bcm_redirector_y0
// WAS add y12, y12
	add	bcm_redirector_y12, bcm_redirector_y12

	// Make sure that the magic extension constants are recognised rather
	// than being interpreted as symbols.
	add w0, w1, b2, uxtb
	add w0, w1, b2, uxth
	add w0, w1, b2, uxtw
	add w0, w1, b2, uxtx
	add w0, w1, b2, sxtb
	add w0, w1, b2, sxth
	add w0, w1, b2, sxtw
	add w0, w1, b2, sxtx
	movi v0.4s, #3, msl #8

	// Aarch64 SVE2 added these forms:
	ld1d { z1.d }, p91/z, [x13, x11, lsl #3]
	ld1b { z11.b }, p15/z, [x10, #1, mul vl]
	st2d { z6.d, z7.d }, p0, [x12]
	// Check that "p22" here isn't parsed as the "p22" register.
// WAS bl p224_point_add
	bl	bcm_redirector_p224_point_add
	ptrue p0.d, vl1
	// The "#7" here isn't a comment, it's now valid Aarch64 assembly.
	cnth x8, all, mul #7

	// fcmp can compare against zero, which is expressed with a floating-
	// point zero literal in the instruction. Again, this is not a
	// comment.
	fcmp d0, #0.0

.Llocal_function_local_target:
local_function:

// BSS data
.type bss_symbol,@object
.section .bss.bss_symbol,"aw",@nobits
bss_symbol:
.Lbss_symbol_local_target:

.word 0
.size bss_symbol, 4
.text
.loc 1 2 0
BORINGSSL_bcm_text_end:
.p2align 2
.hidden bcm_redirector_p224_point_add
.type bcm_redirector_p224_point_add, @function
bcm_redirector_p224_point_add:
.cfi_startproc
	hint #34 // bti c
	b p224_point_add
.cfi_endproc
.size bcm_redirector_p224_point_add, .-bcm_redirector_p224_point_add
.p2align 2
.hidden bcm_redirector_remote_function
.type bcm_redirector_remote_function, @function
bcm_redirector_remote_function:
.cfi_startproc
	hint #34 // bti c
	b remote_function
.cfi_endproc
.size bcm_redirector_remote_function, .-bcm_redirector_remote_function
.p2align 2
.hidden bcm_redirector_y0
.type bcm_redirector_y0, @function
bcm_redirector_y0:
.cfi_startproc
	hint #34 // bti c
	b y0
.cfi_endproc
.size bcm_redirector_y0, .-bcm_redirector_y0
.p2align 2
.hidden bcm_redirector_y12
.type bcm_redirector_y12, @function
bcm_redirector_y12:
.cfi_startproc
	hint #34 // bti c
	b y12
.cfi_endproc
.size bcm_redirector_y12, .-bcm_redirector_y12
.p2align 2
.hidden bss_symbol_bss_get
.type bss_symbol_bss_get, @function
bss_symbol_bss_get:
.cfi_startproc
	hint #34 // bti c
	adrp x0, .Lbss_symbol_local_target
	add x0, x0, :lo12:.Lbss_symbol_local_target
	ret
.cfi_endproc
.size bss_symbol_bss_get, .-bss_symbol_bss_get
.p2align 2
.hidden .Lboringssl_loadgot_stderr
.type .Lboringssl_loadgot_stderr, @function
.Lboringssl_loadgot_stderr:
.cfi_startproc
	hint #34 // bti c
	adrp x0, :got:stderr
	ldr x0, [x0, :got_lo12:stderr]
	ret
.cfi_endproc
.size .Lboringssl_loadgot_stderr, .-.Lboringssl_loadgot_stderr
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
