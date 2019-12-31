.file 10 "some/path/file.c" "file.c"
.file 1000 "some/path/file2.c" "file2.c"
.file 1001 "some/path/file_with_md5.c" "other_name.c" md5 0x5eba7844df6449a7f2fff1556fe7ba8d239f8e2f

# An instruction is needed to satisfy the architecture auto-detection.
        movq %rax, %rbx
