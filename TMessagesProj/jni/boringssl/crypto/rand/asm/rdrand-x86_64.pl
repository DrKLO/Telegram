#!/usr/bin/env perl

# Copyright (c) 2015, Google Inc.
#
# Permission to use, copy, modify, and/or distribute this software for any
# purpose with or without fee is hereby granted, provided that the above
# copyright notice and this permission notice appear in all copies.
#
# THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES
# WITH REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF
# MERCHANTABILITY AND FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY
# SPECIAL, DIRECT, INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES
# WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN ACTION
# OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF OR IN
# CONNECTION WITH THE USE OR PERFORMANCE OF THIS SOFTWARE. */

$flavour = shift;
$output  = shift;
if ($flavour =~ /\./) { $output = $flavour; undef $flavour; }

$0 =~ m/(.*[\/\\])[^\/\\]+$/; $dir=$1;
( $xlate="${dir}../../perlasm/x86_64-xlate.pl" and -f $xlate) or
die "can't locate x86_64-xlate.pl";

open OUT,"| \"$^X\" $xlate $flavour $output";
*STDOUT=*OUT;

print<<___;
.text

# CRYPTO_rdrand writes eight bytes of random data from the hardware RNG to
# |out|. It returns one on success or zero on hardware failure.
# int CRYPTO_rdrand(uint8_t out[8]);
.globl	CRYPTO_rdrand
.type	CRYPTO_rdrand,\@function,1
.align	16
CRYPTO_rdrand:
	xorq %rax, %rax
	# This is rdrand %rcx. It sets rcx to a random value and sets the carry
	# flag on success.
	.byte 0x48, 0x0f, 0xc7, 0xf1
	# An add-with-carry of zero effectively sets %rax to the carry flag.
	adcq %rax, %rax
	movq %rcx, 0(%rdi)
	retq

# CRYPTO_rdrand_multiple8_buf fills |len| bytes at |buf| with random data from
# the hardware RNG. The |len| argument must be a multiple of eight. It returns
# one on success and zero on hardware failure.
# int CRYPTO_rdrand_multiple8_buf(uint8_t *buf, size_t len);
.globl CRYPTO_rdrand_multiple8_buf
.type CRYPTO_rdrand_multiple8_buf,\@function,2
.align 16
CRYPTO_rdrand_multiple8_buf:
	test %rsi, %rsi
	jz .Lout
	movq \$8, %rdx
.Lloop:
	# This is rdrand %rcx. It sets rcx to a random value and sets the carry
	# flag on success.
	.byte 0x48, 0x0f, 0xc7, 0xf1
	jnc .Lerr
	movq %rcx, 0(%rdi)
	addq %rdx, %rdi
	subq %rdx, %rsi
	jnz .Lloop
.Lout:
	movq \$1, %rax
	retq
.Lerr:
	xorq %rax, %rax
	retq
___

close STDOUT;	# flush
