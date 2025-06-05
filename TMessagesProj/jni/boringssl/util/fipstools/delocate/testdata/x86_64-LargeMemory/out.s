.text
.file 1 "inserted_by_delocate.c"
.loc 1 1 0
BORINGSSL_bcm_text_start:
	.text

        # PIC function call
.L0:

        leaq    .L0(%rip), %rax
# WAS movabsq $_GLOBAL_OFFSET_TABLE_-.L0, %rcx
	movq	.Lboringssl_got_delta(%rip), %rcx
	addq $.Lboringssl_got_delta-.L0, %rcx
        addq    %rax, %rcx
# WAS movabsq $_Z1gv@GOTOFF, %rax
	movq .Lboringssl_gotoff__Z1gv(%rip), %rax
        addq    %rcx, %rax
        jmpq    *%rax


        # PIC global variable load.
.L0$pb:

        leaq    .L0$pb(%rip), %rax
# WAS movabsq $_GLOBAL_OFFSET_TABLE_-.L0$pb, %rcx
	movq	.Lboringssl_got_delta(%rip), %rcx
	addq $.Lboringssl_got_delta-.L0$pb, %rcx
        addq    %rax, %rcx
# WAS movabsq $h@GOT, %rax
	movq .Lboringssl_got_h(%rip), %rax
        movq    (%rcx,%rax), %rax
        movl    (%rax), %eax
        retq

        # Non-PIC function call. Not yet handled. Doesn't appear to be used in
        # configurations that we care about.
        #
        # movabsq $_Z1gv, %rax
        # jmpq    *%rax

.text
.loc 1 2 0
BORINGSSL_bcm_text_end:
.Lboringssl_got_delta:
	.quad _GLOBAL_OFFSET_TABLE_-.Lboringssl_got_delta
.Lboringssl_got_h:
	.quad h@GOT
.Lboringssl_gotoff__Z1gv:
	.quad _Z1gv@GOTOFF
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
