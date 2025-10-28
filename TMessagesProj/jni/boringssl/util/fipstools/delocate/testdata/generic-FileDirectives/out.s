.text
.file 1002 "inserted_by_delocate.c" md5 0x00000000000000000000000000000000
.loc 1002 1 0
BORINGSSL_bcm_text_start:
.file 10 "some/path/file.c" "file.c"
.file 1000 "some/path/file2.c" "file2.c"
.file 1001 "some/path/file_with_md5.c" "other_name.c" md5 0x5eba7844df6449a7f2fff1556fe7ba8d239f8e2f

# An instruction is needed to satisfy the architecture auto-detection.
        movq %rax, %rbx
.text
.loc 1002 2 0
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
