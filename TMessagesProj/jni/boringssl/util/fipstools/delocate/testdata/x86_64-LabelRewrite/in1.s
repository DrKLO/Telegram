	.type foo, @function
	.globl foo
foo:
	movq $0, %rax
	ret

bar:
	# References to globals must be rewritten to their local targets.
	call foo
	jmp foo
	notrack jmp foo
	jbe foo
	jne foo

	# This also applies to symbols defined with .set
	call foo1
	call foo2
	call foo3

	# Jumps to PLT symbols are rewritten through redirectors.
	call memcpy@PLT
	jmp memcpy@PLT
	notrack jmp memcpy@PLT
	jbe memcpy@PLT

	# Jumps to local PLT symbols use their local targets.
	call foo@PLT
	jmp foo@PLT
	notrack jmp foo@PLT
	jbe foo@PLT

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
2:

	.quad 2b - 1b
	.quad 2b - .L2

	# .set directives should get local targets and have their references (above)
	# rewritten.
	.globl foo1
	.globl foo2
	.globl foo3
	.set foo1, foo
	.equ foo2, foo
	.equiv foo3, foo
