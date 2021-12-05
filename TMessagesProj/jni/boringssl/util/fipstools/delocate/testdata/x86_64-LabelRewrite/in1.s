	.type foo, @function
	.globl foo
foo:
	movq $0, %rax
	ret

bar:
	# References to globals must be rewritten to their local targets.
	call foo
	jmp foo
	jbe foo
	jne foo

	# Jumps to PLT symbols are rewritten through redirectors.
	call memcpy@PLT
	jmp memcpy@PLT
	jbe memcpy@PLT

	# Jumps to local PLT symbols use their local targets.
	call foo@PLT
	jmp foo@PLT
	jbe foo@PLT

	# Synthesized symbols are treated as local ones.
	call OPENSSL_ia32cap_get@PLT

	# References to local labels are left as-is in the first file.
.Llocal_label:
	jbe .Llocal_label
	leaq .Llocal_label+2048(%rip), %r14
	leaq .Llocal_label+2048+1024(%rip), %r14

	.section .rodata
.L1:
	.quad 42
.L2:
	.quad .L2-.L1
	.uleb128 .L2-.L1
	.sleb128 .L2-.L1

	# Local labels and their jumps are left alone.
	.text
	jmp 1f
1:
	jmp 1b
