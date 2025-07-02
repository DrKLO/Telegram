#! /usr/bin/env perl
# Copyright 2005-2016 The OpenSSL Project Authors. All Rights Reserved.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     https://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.


# Ascetic x86_64 AT&T to MASM/NASM assembler translator by <appro>.
#
# Why AT&T to MASM and not vice versa? Several reasons. Because AT&T
# format is way easier to parse. Because it's simpler to "gear" from
# Unix ABI to Windows one [see cross-reference "card" at the end of
# file]. Because Linux targets were available first...
#
# In addition the script also "distills" code suitable for GNU
# assembler, so that it can be compiled with more rigid assemblers,
# such as Solaris /usr/ccs/bin/as.
#
# This translator is not designed to convert *arbitrary* assembler
# code from AT&T format to MASM one. It's designed to convert just
# enough to provide for dual-ABI OpenSSL modules development...
# There *are* limitations and you might have to modify your assembler
# code or this script to achieve the desired result...
#
# Currently recognized limitations:
#
# - can't use multiple ops per line;
#
# Dual-ABI styling rules.
#
# 1. Adhere to Unix register and stack layout [see cross-reference
#    ABI "card" at the end for explanation].
# 2. Forget about "red zone," stick to more traditional blended
#    stack frame allocation. If volatile storage is actually required
#    that is. If not, just leave the stack as is.
# 3. Functions tagged with ".type name,@function" get crafted with
#    unified Win64 prologue and epilogue automatically. If you want
#    to take care of ABI differences yourself, tag functions as
#    ".type name,@abi-omnipotent" instead.
# 4. To optimize the Win64 prologue you can specify number of input
#    arguments as ".type name,@function,N." Keep in mind that if N is
#    larger than 6, then you *have to* write "abi-omnipotent" code,
#    because >6 cases can't be addressed with unified prologue.
# 5. Name local labels as .L*, do *not* use dynamic labels such as 1:
#    (sorry about latter).
# 6. Don't use [or hand-code with .byte] "rep ret." "ret" mnemonic is
#    required to identify the spots, where to inject Win64 epilogue!
# 7. Stick to explicit ip-relative addressing. If you have to use
#    GOTPCREL addressing, stick to mov symbol@GOTPCREL(%rip),%r??.
#    Both are recognized and translated to proper Win64 addressing
#    modes.
#
# 8. In order to provide for structured exception handling unified
#    Win64 prologue copies %rsp value to %rax. For further details
#    see SEH paragraph at the end.
# 9. .init segment is allowed to contain calls to functions only.
# a. If function accepts more than 4 arguments *and* >4th argument
#    is declared as non 64-bit value, do clear its upper part.
#
# TODO(https://crbug.com/boringssl/259): The dual-ABI mechanism described here
# does not quite unwind correctly on Windows. The seh_directive logic below has
# the start of a new mechanism.


use strict;

my $flavour = shift;
my $output  = shift;
if ($flavour =~ /\./) { $output = $flavour; undef $flavour; }

open STDOUT,">$output" || die "can't open $output: $!"
	if (defined($output));

my $gas=1;	$gas=0 if ($output =~ /\.asm$/);
my $elf=1;	$elf=0 if (!$gas);
my $apple=0;
my $win64=0;
my $prefix="";
my $decor=".L";

my $masmref=8 + 50727*2**-32;	# 8.00.50727 shipped with VS2005
my $masm=0;
my $PTR=" PTR";

my $nasmref=2.03;
my $nasm=0;

if    ($flavour eq "mingw64")	{ $gas=1; $elf=0; $win64=1;
				  # TODO(davidben): Before supporting the
				  # mingw64 perlasm flavour, do away with this
				  # environment variable check.
                                  die "mingw64 not supported";
				  $prefix=`echo __USER_LABEL_PREFIX__ | $ENV{CC} -E -P -`;
				  $prefix =~ s|\R$||; # Better chomp
				}
elsif ($flavour eq "macosx")	{ $gas=1; $elf=0; $apple=1; $prefix="_"; $decor="L\$"; }
elsif ($flavour eq "masm")	{ $gas=0; $elf=0; $masm=$masmref; $win64=1; $decor="\$L\$"; }
elsif ($flavour eq "nasm")	{ $gas=0; $elf=0; $nasm=$nasmref; $win64=1; $decor="\$L\$"; $PTR=""; }
elsif (!$gas)			{ die "unknown flavour $flavour"; }

my $current_segment;
my $current_function;
my %globals;

{ package opcode;	# pick up opcodes
    sub re {
	my	($class, $line) = @_;
	my	$self = {};
	my	$ret;

	if ($$line =~ /^([a-z][a-z0-9]*)/i) {
	    bless $self,$class;
	    $self->{op} = $1;
	    $ret = $self;
	    $$line = substr($$line,@+[0]); $$line =~ s/^\s+//;

	    undef $self->{sz};
	    if ($self->{op} =~ /^(movz)x?([bw]).*/) {	# movz is pain...
		$self->{op} = $1;
		$self->{sz} = $2;
	    } elsif ($self->{op} =~ /call|jmp|^rdrand$/) {
		$self->{sz} = "";
	    } elsif ($self->{op} =~ /^p/ && $' !~ /^(ush|op|insrw)/) { # SSEn
		$self->{sz} = "";
	    } elsif ($self->{op} =~ /^[vk]/) { # VEX or k* such as kmov
		$self->{sz} = "";
	    } elsif ($self->{op} =~ /mov[dq]/ && $$line =~ /%xmm/) {
		$self->{sz} = "";
	    } elsif ($self->{op} =~ /^or([qlwb])$/) {
		$self->{op} = "or";
		$self->{sz} = $1;
	    } elsif ($self->{op} =~ /([a-z]{3,})([qlwb])$/) {
		$self->{op} = $1;
		$self->{sz} = $2;
	    }
	}
	$ret;
    }
    sub size {
	my ($self, $sz) = @_;
	$self->{sz} = $sz if (defined($sz) && !defined($self->{sz}));
	$self->{sz};
    }
    sub out {
	my $self = shift;
	if ($gas) {
	    if ($self->{op} eq "movz") {	# movz is pain...
		sprintf "%s%s%s",$self->{op},$self->{sz},shift;
	    } elsif ($self->{op} =~ /^set/) {
		"$self->{op}";
	    } elsif ($self->{op} eq "ret") {
		my $epilogue = "";
		if ($win64 && $current_function->{abi} eq "svr4") {
		    $epilogue = "movq	8(%rsp),%rdi\n\t" .
				"movq	16(%rsp),%rsi\n\t";
		}
	    	$epilogue . "ret";
	    } elsif ($self->{op} eq "call" && !$elf && $current_segment eq ".init") {
		".p2align\t3\n\t.quad";
	    } else {
		"$self->{op}$self->{sz}";
	    }
	} else {
	    $self->{op} =~ s/^movz/movzx/;
	    if ($self->{op} eq "ret") {
		$self->{op} = "";
		if ($win64 && $current_function->{abi} eq "svr4") {
		    $self->{op} = "mov	rdi,QWORD$PTR\[8+rsp\]\t;WIN64 epilogue\n\t".
				  "mov	rsi,QWORD$PTR\[16+rsp\]\n\t";
	    	}
		$self->{op} .= "ret";
	    } elsif ($self->{op} =~ /^(pop|push)f/) {
		$self->{op} .= $self->{sz};
	    } elsif ($self->{op} eq "call" && $current_segment eq ".CRT\$XCU") {
		$self->{op} = "\tDQ";
	    }
	    $self->{op};
	}
    }
    sub mnemonic {
	my ($self, $op) = @_;
	$self->{op}=$op if (defined($op));
	$self->{op};
    }
}
{ package const;	# pick up constants, which start with $
    sub re {
	my	($class, $line) = @_;
	my	$self = {};
	my	$ret;

	if ($$line =~ /^\$([^,]+)/) {
	    bless $self, $class;
	    $self->{value} = $1;
	    $ret = $self;
	    $$line = substr($$line,@+[0]); $$line =~ s/^\s+//;
	}
	$ret;
    }
    sub out {
    	my $self = shift;

	$self->{value} =~ s/\b(0b[0-1]+)/oct($1)/eig;
	if ($gas) {
	    # Solaris /usr/ccs/bin/as can't handle multiplications
	    # in $self->{value}
	    my $value = $self->{value};
	    no warnings;    # oct might complain about overflow, ignore here...
	    $value =~ s/(?<![\w\$\.])(0x?[0-9a-f]+)/oct($1)/egi;
	    if ($value =~ s/([0-9]+\s*[\*\/\%]\s*[0-9]+)/eval($1)/eg) {
		$self->{value} = $value;
	    }
	    sprintf "\$%s",$self->{value};
	} else {
	    my $value = $self->{value};
	    $value =~ s/0x([0-9a-f]+)/0$1h/ig if ($masm);
	    sprintf "%s",$value;
	}
    }
}
{ package ea;		# pick up effective addresses: expr(%reg,%reg,scale)

    my %szmap = (	b=>"BYTE$PTR",    w=>"WORD$PTR",
			l=>"DWORD$PTR",   d=>"DWORD$PTR",
			q=>"QWORD$PTR",   o=>"OWORD$PTR",
			x=>"XMMWORD$PTR", y=>"YMMWORD$PTR",
			z=>"ZMMWORD$PTR" ) if (!$gas);

    sub re {
	my	($class, $line, $opcode) = @_;
	my	$self = {};
	my	$ret;

	# optional * ----vvv--- appears in indirect jmp/call
	if ($$line =~ /^(\*?)([^\(,]*)\(([%\w,]+)\)((?:{[^}]+})*)/) {
	    bless $self, $class;
	    $self->{asterisk} = $1;
	    $self->{label} = $2;
	    ($self->{base},$self->{index},$self->{scale})=split(/,/,$3);
	    $self->{scale} = 1 if (!defined($self->{scale}));
	    $self->{opmask} = $4;
	    $ret = $self;
	    $$line = substr($$line,@+[0]); $$line =~ s/^\s+//;

	    if ($win64 && $self->{label} =~ s/\@GOTPCREL//) {
		die if ($opcode->mnemonic() ne "mov");
		$opcode->mnemonic("lea");
	    }
	    $self->{base}  =~ s/^%//;
	    $self->{index} =~ s/^%// if (defined($self->{index}));
	    $self->{opcode} = $opcode;
	}
	$ret;
    }
    sub size {}
    sub out {
	my ($self, $sz) = @_;

	$self->{label} =~ s/([_a-z][_a-z0-9]*)/$globals{$1} or $1/gei;
	$self->{label} =~ s/\.L/$decor/g;

	# Silently convert all EAs to 64-bit. This is required for
	# elder GNU assembler and results in more compact code,
	# *but* most importantly AES module depends on this feature!
	$self->{index} =~ s/^[er](.?[0-9xpi])[d]?$/r\1/;
	$self->{base}  =~ s/^[er](.?[0-9xpi])[d]?$/r\1/;

	# Solaris /usr/ccs/bin/as can't handle multiplications
	# in $self->{label}...
	use integer;
	$self->{label} =~ s/(?<![\w\$\.])(0x?[0-9a-f]+)/oct($1)/egi;
	$self->{label} =~ s/\b([0-9]+\s*[\*\/\%]\s*[0-9]+)\b/eval($1)/eg;

	# Some assemblers insist on signed presentation of 32-bit
	# offsets, but sign extension is a tricky business in perl...
	if ((1<<31)<<1) {
	    $self->{label} =~ s/\b([0-9]+)\b/$1<<32>>32/eg;
	} else {
	    $self->{label} =~ s/\b([0-9]+)\b/$1>>0/eg;
	}

	# if base register is %rbp or %r13, see if it's possible to
	# flip base and index registers [for better performance]
	if (!$self->{label} && $self->{index} && $self->{scale}==1 &&
	    $self->{base} =~ /(rbp|r13)/) {
		$self->{base} = $self->{index}; $self->{index} = $1;
	}

	if ($gas) {
	    $self->{label} =~ s/^___imp_/__imp__/   if ($flavour eq "mingw64");

	    if (defined($self->{index})) {
		sprintf "%s%s(%s,%%%s,%d)%s",
					$self->{asterisk},$self->{label},
					$self->{base}?"%$self->{base}":"",
					$self->{index},$self->{scale},
					$self->{opmask};
	    } else {
		sprintf "%s%s(%%%s)%s",	$self->{asterisk},$self->{label},
					$self->{base},$self->{opmask};
	    }
	} else {
	    $self->{label} =~ s/\./\$/g;
	    $self->{label} =~ s/(?<![\w\$\.])0x([0-9a-f]+)/0$1h/ig;
	    $self->{label} = "($self->{label})" if ($self->{label} =~ /[\*\+\-\/]/);

	    my $mnemonic = $self->{opcode}->mnemonic();
	    ($self->{asterisk})				&& ($sz="q") ||
	    ($mnemonic =~ /^v?mov([qd])$/)		&& ($sz=$1)  ||
	    ($mnemonic =~ /^v?pinsr([qdwb])$/)		&& ($sz=$1)  ||
	    ($mnemonic =~ /^vpbroadcast([qdwb])$/)	&& ($sz=$1)  ||
	    ($mnemonic =~ /^v(?!perm)[a-z]+[fi]128$/)	&& ($sz="x");

	    $self->{opmask}  =~ s/%(k[0-7])/$1/;

	    if (defined($self->{index})) {
		sprintf "%s[%s%s*%d%s]%s",$szmap{$sz},
					$self->{label}?"$self->{label}+":"",
					$self->{index},$self->{scale},
					$self->{base}?"+$self->{base}":"",
					$self->{opmask};
	    } elsif ($self->{base} eq "rip") {
		sprintf "%s[%s]",$szmap{$sz},$self->{label};
	    } else {
		sprintf "%s[%s%s]%s",	$szmap{$sz},
					$self->{label}?"$self->{label}+":"",
					$self->{base},$self->{opmask};
	    }
	}
    }
}
{ package register;	# pick up registers, which start with %.
    sub re {
	my	($class, $line, $opcode) = @_;
	my	$self = {};
	my	$ret;

	# optional * ----vvv--- appears in indirect jmp/call
	if ($$line =~ /^(\*?)%(\w+)((?:{[^}]+})*)/) {
	    bless $self,$class;
	    $self->{asterisk} = $1;
	    $self->{value} = $2;
	    $self->{opmask} = $3;
	    $opcode->size($self->size());
	    $ret = $self;
	    $$line = substr($$line,@+[0]); $$line =~ s/^\s+//;
	}
	$ret;
    }
    sub size {
	my	$self = shift;
	my	$ret;

	if    ($self->{value} =~ /^r[\d]+b$/i)	{ $ret="b"; }
	elsif ($self->{value} =~ /^r[\d]+w$/i)	{ $ret="w"; }
	elsif ($self->{value} =~ /^r[\d]+d$/i)	{ $ret="l"; }
	elsif ($self->{value} =~ /^r[\w]+$/i)	{ $ret="q"; }
	elsif ($self->{value} =~ /^[a-d][hl]$/i){ $ret="b"; }
	elsif ($self->{value} =~ /^[\w]{2}l$/i)	{ $ret="b"; }
	elsif ($self->{value} =~ /^[\w]{2}$/i)	{ $ret="w"; }
	elsif ($self->{value} =~ /^e[a-z]{2}$/i){ $ret="l"; }

	$ret;
    }
    sub out {
    	my $self = shift;
	if ($gas)	{ sprintf "%s%%%s%s",	$self->{asterisk},
						$self->{value},
						$self->{opmask}; }
	else		{ $self->{opmask} =~ s/%(k[0-7])/$1/;
			  $self->{value}.$self->{opmask}; }
    }
}
{ package label;	# pick up labels, which end with :
    sub re {
	my	($class, $line) = @_;
	my	$self = {};
	my	$ret;

	if ($$line =~ /(^[\.\w]+)\:/) {
	    bless $self,$class;
	    $self->{value} = $1;
	    $ret = $self;
	    $$line = substr($$line,@+[0]); $$line =~ s/^\s+//;

	    $self->{value} =~ s/^\.L/$decor/;
	}
	$ret;
    }
    sub out {
	my $self = shift;

	if ($gas) {
	    my $func = ($globals{$self->{value}} or $self->{value}) . ":";
	    if ($win64	&& $current_function->{name} eq $self->{value}
			&& $current_function->{abi} eq "svr4") {
		$func .= "\n";
		$func .= "	movq	%rdi,8(%rsp)\n";
		$func .= "	movq	%rsi,16(%rsp)\n";
		$func .= "	movq	%rsp,%rax\n";
		$func .= "${decor}SEH_begin_$current_function->{name}:\n";
		my $narg = $current_function->{narg};
		$narg=6 if (!defined($narg));
		$func .= "	movq	%rcx,%rdi\n" if ($narg>0);
		$func .= "	movq	%rdx,%rsi\n" if ($narg>1);
		$func .= "	movq	%r8,%rdx\n"  if ($narg>2);
		$func .= "	movq	%r9,%rcx\n"  if ($narg>3);
		$func .= "	movq	40(%rsp),%r8\n" if ($narg>4);
		$func .= "	movq	48(%rsp),%r9\n" if ($narg>5);
	    }
	    $func;
	} elsif ($self->{value} ne "$current_function->{name}") {
	    # Make all labels in masm global.
	    $self->{value} .= ":" if ($masm);
	    $self->{value} . ":";
	} elsif ($win64 && $current_function->{abi} eq "svr4") {
	    my $func =	"$current_function->{name}" .
			($nasm ? ":" : "\tPROC $current_function->{scope}") .
			"\n";
	    $func .= "	mov	QWORD$PTR\[8+rsp\],rdi\t;WIN64 prologue\n";
	    $func .= "	mov	QWORD$PTR\[16+rsp\],rsi\n";
	    $func .= "	mov	rax,rsp\n";
	    $func .= "${decor}SEH_begin_$current_function->{name}:";
	    $func .= ":" if ($masm);
	    $func .= "\n";
	    my $narg = $current_function->{narg};
	    $narg=6 if (!defined($narg));
	    $func .= "	mov	rdi,rcx\n" if ($narg>0);
	    $func .= "	mov	rsi,rdx\n" if ($narg>1);
	    $func .= "	mov	rdx,r8\n"  if ($narg>2);
	    $func .= "	mov	rcx,r9\n"  if ($narg>3);
	    $func .= "	mov	r8,QWORD$PTR\[40+rsp\]\n" if ($narg>4);
	    $func .= "	mov	r9,QWORD$PTR\[48+rsp\]\n" if ($narg>5);
	    $func .= "\n";
	} else {
	   "$current_function->{name}".
			($nasm ? ":" : "\tPROC $current_function->{scope}");
	}
    }
}
{ package expr;		# pick up expressions
    sub re {
	my	($class, $line, $opcode) = @_;
	my	$self = {};
	my	$ret;

	if ($$line =~ /(^[^,]+)/) {
	    bless $self,$class;
	    $self->{value} = $1;
	    $ret = $self;
	    $$line = substr($$line,@+[0]); $$line =~ s/^\s+//;

	    $self->{value} =~ s/\@PLT// if (!$elf);
	    $self->{value} =~ s/([_a-z][_a-z0-9]*)/$globals{$1} or $1/gei;
	    $self->{value} =~ s/\.L/$decor/g;
	    $self->{opcode} = $opcode;
	}
	$ret;
    }
    sub out {
	my $self = shift;
	if ($nasm && $self->{opcode}->mnemonic()=~m/^j(?![re]cxz)/) {
	    "NEAR ".$self->{value};
	} else {
	    $self->{value};
	}
    }
}
{ package cfi_directive;
    # CFI directives annotate instructions that are significant for
    # stack unwinding procedure compliant with DWARF specification,
    # see http://dwarfstd.org/. Besides naturally expected for this
    # script platform-specific filtering function, this module adds
    # three auxiliary synthetic directives not recognized by [GNU]
    # assembler:
    #
    # - .cfi_push to annotate push instructions in prologue, which
    #   translates to .cfi_adjust_cfa_offset (if needed) and
    #   .cfi_offset;
    # - .cfi_pop to annotate pop instructions in epilogue, which
    #   translates to .cfi_adjust_cfa_offset (if needed) and
    #   .cfi_restore;
    # - [and most notably] .cfi_cfa_expression which encodes
    #   DW_CFA_def_cfa_expression and passes it to .cfi_escape as
    #   byte vector;
    #
    # CFA expressions were introduced in DWARF specification version
    # 3 and describe how to deduce CFA, Canonical Frame Address. This
    # becomes handy if your stack frame is variable and you can't
    # spare register for [previous] frame pointer. Suggested directive
    # syntax is made-up mix of DWARF operator suffixes [subset of]
    # and references to registers with optional bias. Following example
    # describes offloaded *original* stack pointer at specific offset
    # from *current* stack pointer:
    #
    #   .cfi_cfa_expression     %rsp+40,deref,+8
    #
    # Final +8 has everything to do with the fact that CFA is defined
    # as reference to top of caller's stack, and on x86_64 call to
    # subroutine pushes 8-byte return address. In other words original
    # stack pointer upon entry to a subroutine is 8 bytes off from CFA.

    # Below constants are taken from "DWARF Expressions" section of the
    # DWARF specification, section is numbered 7.7 in versions 3 and 4.
    my %DW_OP_simple = (	# no-arg operators, mapped directly
	deref	=> 0x06,	dup	=> 0x12,
	drop	=> 0x13,	over	=> 0x14,
	pick	=> 0x15,	swap	=> 0x16,
	rot	=> 0x17,	xderef	=> 0x18,

	abs	=> 0x19,	and	=> 0x1a,
	div	=> 0x1b,	minus	=> 0x1c,
	mod	=> 0x1d,	mul	=> 0x1e,
	neg	=> 0x1f,	not	=> 0x20,
	or	=> 0x21,	plus	=> 0x22,
	shl	=> 0x24,	shr	=> 0x25,
	shra	=> 0x26,	xor	=> 0x27,
	);

    my %DW_OP_complex = (	# used in specific subroutines
	constu		=> 0x10,	# uleb128
	consts		=> 0x11,	# sleb128
	plus_uconst	=> 0x23,	# uleb128
	lit0 		=> 0x30,	# add 0-31 to opcode
	reg0		=> 0x50,	# add 0-31 to opcode
	breg0		=> 0x70,	# add 0-31 to opcole, sleb128
	regx		=> 0x90,	# uleb28
	fbreg		=> 0x91,	# sleb128
	bregx		=> 0x92,	# uleb128, sleb128
	piece		=> 0x93,	# uleb128
	);

    # Following constants are defined in x86_64 ABI supplement, for
    # example available at https://www.uclibc.org/docs/psABI-x86_64.pdf,
    # see section 3.7 "Stack Unwind Algorithm".
    my %DW_reg_idx = (
	"%rax"=>0,  "%rdx"=>1,  "%rcx"=>2,  "%rbx"=>3,
	"%rsi"=>4,  "%rdi"=>5,  "%rbp"=>6,  "%rsp"=>7,
	"%r8" =>8,  "%r9" =>9,  "%r10"=>10, "%r11"=>11,
	"%r12"=>12, "%r13"=>13, "%r14"=>14, "%r15"=>15
	);

    my ($cfa_reg, $cfa_rsp);
    my @cfa_stack;

    # [us]leb128 format is variable-length integer representation base
    # 2^128, with most significant bit of each byte being 0 denoting
    # *last* most significant digit. See "Variable Length Data" in the
    # DWARF specification, numbered 7.6 at least in versions 3 and 4.
    sub sleb128 {
	use integer;	# get right shift extend sign

	my $val = shift;
	my $sign = ($val < 0) ? -1 : 0;
	my @ret = ();

	while(1) {
	    push @ret, $val&0x7f;

	    # see if remaining bits are same and equal to most
	    # significant bit of the current digit, if so, it's
	    # last digit...
	    last if (($val>>6) == $sign);

	    @ret[-1] |= 0x80;
	    $val >>= 7;
	}

	return @ret;
    }
    sub uleb128 {
	my $val = shift;
	my @ret = ();

	while(1) {
	    push @ret, $val&0x7f;

	    # see if it's last significant digit...
	    last if (($val >>= 7) == 0);

	    @ret[-1] |= 0x80;
	}

	return @ret;
    }
    sub const {
	my $val = shift;

	if ($val >= 0 && $val < 32) {
            return ($DW_OP_complex{lit0}+$val);
	}
	return ($DW_OP_complex{consts}, sleb128($val));
    }
    sub reg {
	my $val = shift;

	return if ($val !~ m/^(%r\w+)(?:([\+\-])((?:0x)?[0-9a-f]+))?/);

	my $reg = $DW_reg_idx{$1};
	my $off = eval ("0 $2 $3");

	return (($DW_OP_complex{breg0} + $reg), sleb128($off));
	# Yes, we use DW_OP_bregX+0 to push register value and not
	# DW_OP_regX, because latter would require even DW_OP_piece,
	# which would be a waste under the circumstances. If you have
	# to use DWP_OP_reg, use "regx:N"...
    }
    sub cfa_expression {
	my $line = shift;
	my @ret;

	foreach my $token (split(/,\s*/,$line)) {
	    if ($token =~ /^%r/) {
		push @ret,reg($token);
	    } elsif ($token =~ /((?:0x)?[0-9a-f]+)\((%r\w+)\)/) {
		push @ret,reg("$2+$1");
	    } elsif ($token =~ /(\w+):(\-?(?:0x)?[0-9a-f]+)(U?)/i) {
		my $i = 1*eval($2);
		push @ret,$DW_OP_complex{$1}, ($3 ? uleb128($i) : sleb128($i));
	    } elsif (my $i = 1*eval($token) or $token eq "0") {
		if ($token =~ /^\+/) {
		    push @ret,$DW_OP_complex{plus_uconst},uleb128($i);
		} else {
		    push @ret,const($i);
		}
	    } else {
		push @ret,$DW_OP_simple{$token};
	    }
	}

	# Finally we return DW_CFA_def_cfa_expression, 15, followed by
	# length of the expression and of course the expression itself.
	return (15,scalar(@ret),@ret);
    }
    sub re {
	my	($class, $line) = @_;
	my	$self = {};
	my	$ret;

	if ($$line =~ s/^\s*\.cfi_(\w+)\s*//) {
	    bless $self,$class;
	    $ret = $self;
	    undef $self->{value};
	    my $dir = $1;

	    SWITCH: for ($dir) {
	    # What is $cfa_rsp? Effectively it's difference between %rsp
	    # value and current CFA, Canonical Frame Address, which is
	    # why it starts with -8. Recall that CFA is top of caller's
	    # stack...
	    /startproc/	&& do {	($cfa_reg, $cfa_rsp) = ("%rsp", -8); last; };
	    /endproc/	&& do {	($cfa_reg, $cfa_rsp) = ("%rsp",  0); last; };
	    /def_cfa_register/
			&& do {	$cfa_reg = $$line; last; };
	    /def_cfa_offset/
			&& do {	$cfa_rsp = -1*eval($$line) if ($cfa_reg eq "%rsp");
				last;
			      };
	    /adjust_cfa_offset/
			&& do {	$cfa_rsp -= 1*eval($$line) if ($cfa_reg eq "%rsp");
				last;
			      };
	    /def_cfa/	&& do {	if ($$line =~ /(%r\w+)\s*,\s*(.+)/) {
				    $cfa_reg = $1;
				    $cfa_rsp = -1*eval($2) if ($cfa_reg eq "%rsp");
				}
				last;
			      };
	    /push/	&& do {	$dir = undef;
				$cfa_rsp -= 8;
				if ($cfa_reg eq "%rsp") {
				    $self->{value} = ".cfi_adjust_cfa_offset\t8\n";
				}
				$self->{value} .= ".cfi_offset\t$$line,$cfa_rsp";
				last;
			      };
	    /pop/	&& do {	$dir = undef;
				$cfa_rsp += 8;
				if ($cfa_reg eq "%rsp") {
				    $self->{value} = ".cfi_adjust_cfa_offset\t-8\n";
				}
				$self->{value} .= ".cfi_restore\t$$line";
				last;
			      };
	    /cfa_expression/
			&& do {	$dir = undef;
				$self->{value} = ".cfi_escape\t" .
					join(",", map(sprintf("0x%02x", $_),
						      cfa_expression($$line)));
				last;
			      };
	    /remember_state/
			&& do {	push @cfa_stack, [$cfa_reg, $cfa_rsp];
				last;
			      };
	    /restore_state/
			&& do {	($cfa_reg, $cfa_rsp) = @{pop @cfa_stack};
				last;
			      };
	    }

	    $self->{value} = ".cfi_$dir\t$$line" if ($dir);

	    $$line = "";
	}

	return $ret;
    }
    sub out {
	my $self = shift;
	return ($elf ? $self->{value} : undef);
    }
}
{ package seh_directive;
    # This implements directives, like MASM, gas, and clang-assembler for
    # specifying Windows unwind codes. See
    # https://learn.microsoft.com/en-us/cpp/build/exception-handling-x64?view=msvc-170
    # for details on the Windows unwind mechanism. As perlasm generally uses gas
    # syntax, the syntax is patterned after the gas spelling, described in
    # https://sourceware.org/legacy-ml/binutils/2009-08/msg00193.html
    #
    # TODO(https://crbug.com/boringssl/571): Translate to the MASM directives
    # when using the MASM output. Emit as-is when using "mingw64" output, which
    # is Windows with gas syntax.
    #
    # TODO(https://crbug.com/boringssl/259): For now, SEH directives are ignored
    # on non-Windows platforms. This means functions need to specify both CFI
    # and SEH directives, often redundantly. Ideally we'd abstract between the
    # two. E.g., we can synthesize CFI from SEH prologues, but SEH does not
    # annotate epilogs, so we'd need to combine parts from both. Or we can
    # restrict ourselves to a subset of CFI and synthesize SEH from CFI.
    #
    # Additionally, this only supports @abi-omnipotent functions. It is
    # incompatible with the automatic calling convention conversion. The main
    # complication is the current scheme modifies RDI and RSI (non-volatile on
    # Windows) at the start of the function, and saves them in the parameter
    # stack area. This can be expressed with .seh_savereg, but .seh_savereg is
    # only usable late in the prologue. However, unwind information gives enough
    # information to locate the parameter stack area at any point in the
    # function, so we can defer conversion or implement other schemes.

    my $UWOP_PUSH_NONVOL = 0;
    my $UWOP_ALLOC_LARGE = 1;
    my $UWOP_ALLOC_SMALL = 2;
    my $UWOP_SET_FPREG = 3;
    my $UWOP_SAVE_NONVOL = 4;
    my $UWOP_SAVE_NONVOL_FAR = 5;
    my $UWOP_SAVE_XMM128 = 8;
    my $UWOP_SAVE_XMM128_FAR = 9;

    my %UWOP_REG_TO_NUMBER = ("%rax" => 0, "%rcx" => 1, "%rdx" => 2, "%rbx" => 3,
			      "%rsp" => 4, "%rbp" => 5, "%rsi" => 6, "%rdi" => 7,
			      map(("%r$_" => $_), (8..15)));
    my %UWOP_NUMBER_TO_REG = reverse %UWOP_REG_TO_NUMBER;

    # The contents of the pdata and xdata sections so far.
    my ($xdata, $pdata) = ("", "");

    my %info;

    my $next_label = 0;
    my $current_label_func = "";

    # _new_unwind_label allocates a new label, unique to the file.
    sub _new_unwind_label {
	my ($name) = (@_);
	# Labels only need to be unique, but to make diffs easier to read, scope
	# them all under the current function.
	my $func = $current_function->{name};
	if ($func ne $current_label_func) {
	    $current_label_func = $func;
	    $next_label = 0;
	}

	my $num = $next_label++;
	return ".LSEH_${name}_${func}_${num}";
    }

    sub _check_in_proc {
	die "Missing .seh_startproc directive" unless %info;
    }

    sub _check_in_prologue {
	_check_in_proc();
	die "Invalid SEH directive after .seh_endprologue" if defined($info{endprologue});
    }

    sub _check_not_in_proc {
	die "Missing .seh_endproc directive" if %info;
    }

    sub _startproc {
	_check_not_in_proc();
	if ($current_function->{abi} eq "svr4") {
	    die "SEH directives can only be used with \@abi-omnipotent";
	}

	my $info_label = _new_unwind_label("info");
	my $start_label = _new_unwind_label("begin");
	%info = (
	    # info_label is the label of the function's entry in .xdata.
	    info_label => $info_label,
	    # start_label is the start of the function.
	    start_label => $start_label,
	    # endprologue is the label of the end of the prologue.
	    endprologue => undef,
	    # unwind_codes contains the textual representation of the
	    # unwind codes in the function so far.
	    unwind_codes => "",
	    # num_codes is the number of 16-bit words in unwind_codes.
	    num_codes => 0,
	    # frame_reg is the number of the frame register, or zero if
	    # there is none.
	    frame_reg => 0,
	    # frame_offset is the offset into the fixed part of the stack that
	    # the frame register points into.
	    frame_offset => 0,
	    # has_offset is whether directives taking an offset have
	    # been used. This is used to check that such directives
	    # come after the fixed portion of the stack frame is established.
	    has_offset => 0,
	    # has_nonpushreg is whether directives other than
	    # .seh_pushreg have been used. This is used to check that
	    # .seh_pushreg directives are first.
	    has_nonpushreg => 0,
	);
	return $start_label;
    }

    sub _add_unwind_code {
	my ($op, $value, @extra) = @_;
	_check_in_prologue();
	if ($op != $UWOP_PUSH_NONVOL) {
	    $info{has_nonpushreg} = 1;
	} elsif ($info{has_nonpushreg}) {
	    die ".seh_pushreg directives must appear first in the prologue";
	}

	my $label = _new_unwind_label("prologue");
	# Encode an UNWIND_CODE structure. See
	# https://learn.microsoft.com/en-us/cpp/build/exception-handling-x64?view=msvc-170#struct-unwind_code
	my $encoded = $op | ($value << 4);
	my $codes = <<____;
	.byte	$label-$info{start_label}
	.byte	$encoded
____
	# Some opcodes need additional values to encode themselves.
	foreach (@extra) {
	    $codes .= "\t.value\t$_\n";
	}

	$info{num_codes} += 1 + scalar(@extra);
	# Unwind codes are listed in reverse order.
	$info{unwind_codes} = $codes . $info{unwind_codes};
	return $label;
    }

    sub _updating_fixed_allocation {
	_check_in_prologue();
	if ($info{frame_reg} != 0) {
	    # Windows documentation does not explicitly forbid .seh_stackalloc
	    # after .seh_setframe, but it appears to have no effect. Offsets are
	    # still relative to the fixed allocation when the frame register was
	    # established.
	    die "fixed allocation may not be increased after .seh_setframe";
	}
	if ($info{has_offset}) {
	    # Windows documentation does not explicitly forbid .seh_savereg
	    # before .seh_stackalloc, but it does not work very well. Offsets
	    # are relative to the top of the final fixed allocation, not where
	    # RSP currently is.
	    die "directives with an offset must come after the fixed allocation is established.";
	}
    }

    sub _endproc {
	_check_in_proc();
	if (!defined($info{endprologue})) {
	    die "Missing .seh_endprologue";
	}

	my $end_label = _new_unwind_label("end");
	# Encode a RUNTIME_FUNCTION. See
	# https://learn.microsoft.com/en-us/cpp/build/exception-handling-x64?view=msvc-170#struct-runtime_function
	$pdata .= <<____;
	.rva	$info{start_label}
	.rva	$end_label
	.rva	$info{info_label}

____

	# Encode an UNWIND_INFO. See
	# https://learn.microsoft.com/en-us/cpp/build/exception-handling-x64?view=msvc-170#struct-unwind_info
	my $frame_encoded = $info{frame_reg} | (($info{frame_offset} / 16) << 4);
	$xdata .= <<____;
$info{info_label}:
	.byte	1	# version 1, no flags
	.byte	$info{endprologue}-$info{start_label}
	.byte	$info{num_codes}
	.byte	$frame_encoded
$info{unwind_codes}
____

	# UNWIND_INFOs must be 4-byte aligned. If needed, we must add an extra
	# unwind code. This does not change the unwind code count. Windows
	# documentation says "For alignment purposes, this array always has an
	# even number of entries, and the final entry is potentially unused. In
	# that case, the array is one longer than indicated by the count of
	# unwind codes field."
	if ($info{num_codes} & 1) {
	    $xdata .= "\t.value\t0\n";
	}

	%info = ();
	return $end_label;
    }

    sub re {
	my ($class, $line) = @_;
	if ($$line =~ s/^\s*\.seh_(\w+)\s*//) {
	    my $dir = $1;
	    if (!$win64) {
		$$line = "";
		return;
	    }

	    my $label;
	    SWITCH: for ($dir) {
		/^startproc$/ && do {
		    $label = _startproc($1);
		    last;
		};
		/^pushreg$/ && do {
		    $$line =~ /^(%\w+)\s*$/ or die "could not parse .seh_$dir";
		    my $reg_num = $UWOP_REG_TO_NUMBER{$1} or die "unknown register $1";
		    _updating_fixed_allocation();
		    $label = _add_unwind_code($UWOP_PUSH_NONVOL, $reg_num);
		    last;
		};
		/^stackalloc$/ && do {
		    my $num = eval($$line);
		    if ($num <= 0 || $num % 8 != 0) {
			die "invalid stack allocation: $num";
		    }
		    _updating_fixed_allocation();
		    if ($num <= 128) {
			$label = _add_unwind_code($UWOP_ALLOC_SMALL, ($num - 8) / 8);
		    } elsif ($num < 512 * 1024) {
			$label = _add_unwind_code($UWOP_ALLOC_LARGE, 0, $num / 8);
		    } elsif ($num < 4 * 1024 * 1024 * 1024) {
			$label = _add_unwind_code($UWOP_ALLOC_LARGE, 1, $num >> 16, $num & 0xffff);
		    } else {
			die "stack allocation too large: $num"
		    }
		    last;
		};
		/^setframe$/ && do {
		    if ($info{frame_reg} != 0) {
			die "duplicate .seh_setframe directive";
		    }
		    if ($info{has_offset}) {
			die "directives with with an offset must come after .seh_setframe.";
		    }
		    $$line =~ /(%\w+)\s*,\s*(.+)/ or die "could not parse .seh_$dir";
		    my $reg_num = $UWOP_REG_TO_NUMBER{$1} or die "unknown register $1";
		    my $offset = eval($2);
		    if ($offset < 0 || $offset % 16 != 0 || $offset > 240) {
			die "invalid offset: $offset";
		    }
		    $info{frame_reg} = $reg_num;
		    $info{frame_offset} = $offset;
		    $label = _add_unwind_code($UWOP_SET_FPREG, 0);
		    last;
		};
		/^savereg$/ && do {
		    $$line =~ /(%\w+)\s*,\s*(.+)/ or die "could not parse .seh_$dir";
		    my $reg_num = $UWOP_REG_TO_NUMBER{$1} or die "unknown register $1";
		    my $offset = eval($2);
		    if ($offset < 0 || $offset % 8 != 0) {
			die "invalid offset: $offset";
		    }
		    if ($offset < 8 * 65536) {
			$label = _add_unwind_code($UWOP_SAVE_NONVOL, $reg_num, $offset / 8);
		    } else {
			$label = _add_unwind_code($UWOP_SAVE_NONVOL_FAR, $reg_num, $offset >> 16, $offset & 0xffff);
		    }
		    $info{has_offset} = 1;
		    last;
		};
		/^savexmm$/ && do {
		    $$line =~ /%xmm(\d+)\s*,\s*(.+)/ or die "could not parse .seh_$dir";
		    my $reg_num = $1;
		    my $offset = eval($2);
		    if ($offset < 0 || $offset % 16 != 0) {
			die "invalid offset: $offset";
		    }
		    if ($offset < 16 * 65536) {
			$label = _add_unwind_code($UWOP_SAVE_XMM128, $reg_num, $offset / 16);
		    } else {
			$label = _add_unwind_code($UWOP_SAVE_XMM128_FAR, $reg_num, $offset >> 16, $offset & 0xffff);
		    }
		    $info{has_offset} = 1;
		    last;
		};
		/^endprologue$/ && do {
		    _check_in_prologue();
		    if ($info{num_codes} == 0) {
			# If a Windows function has no directives (i.e. it
			# doesn't touch the stack), it is a leaf function and is
			# not expected to appear in .pdata or .xdata.
			die ".seh_endprologue found with no unwind codes";
		    }

		    $label = _new_unwind_label("endprologue");
		    $info{endprologue} = $label;
		    last;
		};
		/^endproc$/ && do {
		    $label = _endproc();
		    last;
		};
		die "unknown SEH directive .seh_$dir";
	    }

	    # All SEH directives compile to labels inline. The other data is
	    # emitted later.
	    $$line = "";
	    $label .= ":";
	    return label->re(\$label);
	}
    }

    sub pdata_and_xdata {
	return "" unless $win64;

	my $ret = "";
	if ($pdata ne "") {
	    $ret .= <<____;
.section	.pdata
.align	4
$pdata
____
	}
	if ($xdata ne "") {
	    $ret .= <<____;
.section	.xdata
.align	4
$xdata
____
	}
	return $ret;
    }
}
{ package directive;	# pick up directives, which start with .
    my %sections;
    sub nasm_section {
	my ($name, $qualifiers) = @_;
	my $ret = "section\t$name";
	if (exists $sections{$name}) {
	    # Work around https://bugzilla.nasm.us/show_bug.cgi?id=3392701. Only
	    # emit section qualifiers the first time a section is referenced.
	    # For all subsequent references, require the qualifiers match and
	    # omit them.
	    #
	    # See also https://crbug.com/1422018 and b/270643835.
	    my $old = $sections{$name};
	    die "Inconsistent qualifiers: $qualifiers vs $old" if ($qualifiers ne "" && $qualifiers ne $old);
	} else {
	    $sections{$name} = $qualifiers;
	    if ($qualifiers ne "") {
		$ret .= " $qualifiers";
	    }
	}
	return $ret;
    }
    sub re {
	my	($class, $line) = @_;
	my	$self = {};
	my	$ret;
	my	$dir;

	# chain-call to cfi_directive and seh_directive.
	$ret = cfi_directive->re($line) and return $ret;
	$ret = seh_directive->re($line) and return $ret;

	if ($$line =~ /^\s*(\.\w+)/) {
	    bless $self,$class;
	    $dir = $1;
	    $ret = $self;
	    undef $self->{value};
	    $$line = substr($$line,@+[0]); $$line =~ s/^\s+//;

	    SWITCH: for ($dir) {
		/\.global|\.globl|\.extern/
			    && do { $globals{$$line} = $prefix . $$line;
				    $$line = $globals{$$line} if ($prefix);
				    last;
				  };
		/\.type/    && do { my ($sym,$type,$narg) = split(/\s*,\s*/,$$line);
				    if ($type eq "\@function") {
					undef $current_function;
					$current_function->{name} = $sym;
					$current_function->{abi}  = "svr4";
					$current_function->{narg} = $narg;
					$current_function->{scope} = defined($globals{$sym})?"PUBLIC":"PRIVATE";
				    } elsif ($type eq "\@abi-omnipotent") {
					undef $current_function;
					$current_function->{name} = $sym;
					$current_function->{scope} = defined($globals{$sym})?"PUBLIC":"PRIVATE";
				    }
				    $$line =~ s/\@abi\-omnipotent/\@function/;
				    $$line =~ s/\@function.*/\@function/;
				    last;
				  };
		/\.asciz/   && do { if ($$line =~ /^"(.*)"$/) {
					$dir  = ".byte";
					$$line = join(",",unpack("C*",$1),0);
				    }
				    last;
				  };
		/\.rva|\.long|\.quad|\.byte/
			    && do { $$line =~ s/([_a-z][_a-z0-9]*)/$globals{$1} or $1/gei;
				    $$line =~ s/\.L/$decor/g;
				    last;
				  };
	    }

	    if ($gas) {
		$self->{value} = $dir . "\t" . $$line;

		if ($dir =~ /\.extern/) {
		    if ($flavour eq "elf") {
			$self->{value} .= "\n.hidden $$line";
		    } else {
			$self->{value} = "";
		    }
		} elsif (!$elf && $dir =~ /\.type/) {
		    $self->{value} = "";
		    $self->{value} = ".def\t" . ($globals{$1} or $1) . ";\t" .
				(defined($globals{$1})?".scl 2;":".scl 3;") .
				"\t.type 32;\t.endef"
				if ($win64 && $$line =~ /([^,]+),\@function/);
		} elsif (!$elf && $dir =~ /\.size/) {
		    $self->{value} = "";
		    if (defined($current_function)) {
			$self->{value} .= "${decor}SEH_end_$current_function->{name}:"
				if ($win64 && $current_function->{abi} eq "svr4");
			undef $current_function;
		    }
		} elsif (!$elf && $dir =~ /\.align/) {
		    $self->{value} = ".p2align\t" . (log($$line)/log(2));
		} elsif ($dir eq ".section") {
		    $current_segment=$$line;
		    if (!$elf && $current_segment eq ".rodata") {
			if	($flavour eq "macosx") { $self->{value} = ".section\t__DATA,__const"; }
		    }
		    if (!$elf && $current_segment eq ".init") {
			if	($flavour eq "macosx")	{ $self->{value} = ".mod_init_func"; }
			elsif	($flavour eq "mingw64")	{ $self->{value} = ".section\t.ctors"; }
		    }
		} elsif ($dir =~ /\.(text|data)/) {
		    $current_segment=".$1";
		} elsif ($dir =~ /\.global|\.globl|\.extern/) {
		    if ($flavour eq "macosx") {
		        $self->{value} .= "\n.private_extern $$line";
		    } else {
		        $self->{value} .= "\n.hidden $$line";
		    }
		} elsif ($dir =~ /\.hidden/) {
		    if    ($flavour eq "macosx")  { $self->{value} = ".private_extern\t$prefix$$line"; }
		    elsif ($flavour eq "mingw64") { $self->{value} = ""; }
		} elsif ($dir =~ /\.comm/) {
		    $self->{value} = "$dir\t$prefix$$line";
		    $self->{value} =~ s|,([0-9]+),([0-9]+)$|",$1,".log($2)/log(2)|e if ($flavour eq "macosx");
		}
		$$line = "";
		return $self;
	    }

	    # non-gas case or nasm/masm
	    SWITCH: for ($dir) {
		/\.text/    && do { my $v=undef;
				    if ($nasm) {
					$v=nasm_section(".text", "code align=64")."\n";
				    } else {
					$v="$current_segment\tENDS\n" if ($current_segment);
					$current_segment = ".text\$";
					$v.="$current_segment\tSEGMENT ";
					$v.=$masm>=$masmref ? "ALIGN(256)" : "PAGE";
					$v.=" 'CODE'";
				    }
				    $self->{value} = $v;
				    last;
				  };
		/\.data/    && do { my $v=undef;
				    if ($nasm) {
					$v=nasm_section(".data", "data align=8")."\n";
				    } else {
					$v="$current_segment\tENDS\n" if ($current_segment);
					$current_segment = "_DATA";
					$v.="$current_segment\tSEGMENT";
				    }
				    $self->{value} = $v;
				    last;
				  };
		/\.section/ && do { my $v=undef;
				    $$line =~ s/([^,]*).*/$1/;
				    $$line = ".CRT\$XCU" if ($$line eq ".init");
				    $$line = ".rdata" if ($$line eq ".rodata");
				    if ($nasm) {
					my $qualifiers = "";
					if ($$line=~/\.([prx])data/) {
					    $qualifiers = "rdata align=";
					    $qualifiers .= $1 eq "p"? 4 : 8;
					} elsif ($$line=~/\.CRT\$/i) {
					    $qualifiers = "rdata align=8";
					}
					$v = nasm_section($$line, $qualifiers);
				    } else {
					$v="$current_segment\tENDS\n" if ($current_segment);
					$v.="$$line\tSEGMENT";
					if ($$line=~/\.([prx])data/) {
					    $v.=" READONLY";
					    $v.=" ALIGN(".($1 eq "p" ? 4 : 8).")" if ($masm>=$masmref);
					} elsif ($$line=~/\.CRT\$/i) {
					    $v.=" READONLY ";
					    $v.=$masm>=$masmref ? "ALIGN(8)" : "DWORD";
					}
				    }
				    $current_segment = $$line;
				    $self->{value} = $v;
				    last;
				  };
		/\.extern/  && do { $self->{value}  = "EXTERN\t".$$line;
				    $self->{value} .= ":NEAR" if ($masm);
				    last;
				  };
		/\.globl|.global/
			    && do { $self->{value}  = $masm?"PUBLIC":"global";
				    $self->{value} .= "\t".$$line;
				    last;
				  };
		/\.size/    && do { if (defined($current_function)) {
					undef $self->{value};
					if ($current_function->{abi} eq "svr4") {
					    $self->{value}="${decor}SEH_end_$current_function->{name}:";
					    $self->{value}.=":\n" if($masm);
					}
					$self->{value}.="$current_function->{name}\tENDP" if($masm && $current_function->{name});
					undef $current_function;
				    }
				    last;
				  };
		/\.align/   && do { my $max = ($masm && $masm>=$masmref) ? 256 : 4096;
				    $self->{value} = "ALIGN\t".($$line>$max?$max:$$line);
				    last;
				  };
		/\.(value|long|rva|quad)/
			    && do { my $sz  = substr($1,0,1);
				    my @arr = split(/,\s*/,$$line);
				    my $last = pop(@arr);
				    my $conv = sub  {	my $var=shift;
							$var=~s/^(0b[0-1]+)/oct($1)/eig;
							$var=~s/^0x([0-9a-f]+)/0$1h/ig if ($masm);
							if ($sz eq "D" && ($current_segment=~/.[px]data/ || $dir eq ".rva"))
							{ $var=~s/^([_a-z\$\@][_a-z0-9\$\@]*)/$nasm?"$1 wrt ..imagebase":"imagerel $1"/egi; }
							$var;
						    };

				    $sz =~ tr/bvlrq/BWDDQ/;
				    $self->{value} = "\tD$sz\t";
				    for (@arr) { $self->{value} .= &$conv($_).","; }
				    $self->{value} .= &$conv($last);
				    last;
				  };
		/\.byte/    && do { my @str=split(/,\s*/,$$line);
				    map(s/(0b[0-1]+)/oct($1)/eig,@str);
				    map(s/0x([0-9a-f]+)/0$1h/ig,@str) if ($masm);
				    while ($#str>15) {
					$self->{value}.="\tDB\t"
						.join(",",@str[0..15])."\n";
					foreach (0..15) { shift @str; }
				    }
				    $self->{value}.="\tDB\t"
						.join(",",@str) if (@str);
				    last;
				  };
		/\.comm/    && do { my @str=split(/,\s*/,$$line);
				    my $v=undef;
				    if ($nasm) {
					$v.="common	$prefix@str[0] @str[1]";
				    } else {
					$v="$current_segment\tENDS\n" if ($current_segment);
					$current_segment = "_DATA";
					$v.="$current_segment\tSEGMENT\n";
					$v.="COMM	@str[0]:DWORD:".@str[1]/4;
				    }
				    $self->{value} = $v;
				    last;
				  };
	    }
	    $$line = "";
	}

	$ret;
    }
    sub out {
	my $self = shift;
	$self->{value};
    }
}

########################################################################

{
  my $comment = "//";
  $comment = ";" if ($masm || $nasm);
  print <<___;
$comment This file is generated from a similarly-named Perl script in the BoringSSL
$comment source tree. Do not edit by hand.

___
}

if ($nasm) {
    die "unknown target" unless ($win64);
    print <<___;
\%ifidn __OUTPUT_FORMAT__, win64
default	rel
\%define XMMWORD
\%define YMMWORD
\%define ZMMWORD
\%define _CET_ENDBR

\%ifdef BORINGSSL_PREFIX
\%include "boringssl_prefix_symbols_nasm.inc"
\%endif
___
} elsif ($masm) {
    print <<___;
OPTION	DOTNAME
___
}

if ($gas) {
    my $target;
    if ($elf) {
        # The "elf" target is really ELF with SysV ABI, but every ELF platform
        # uses the SysV ABI.
        $target = "defined(__ELF__)";
    } elsif ($apple) {
        $target = "defined(__APPLE__)";
    } else {
        die "unknown target: $flavour";
    }
    print <<___;
#include <openssl/asm_base.h>

#if !defined(OPENSSL_NO_ASM) && defined(OPENSSL_X86_64) && $target
___
}

sub process_line {
    my $line = shift;
    $line =~ s|\R$||;           # Better chomp

    if ($nasm) {
	$line =~ s|^#ifdef |%ifdef |;
	$line =~ s|^#ifndef |%ifndef |;
	$line =~ s|^#endif|%endif|;
	$line =~ s|[#!].*$||;	# get rid of asm-style comments...
    } else {
	# Get rid of asm-style comments but not preprocessor directives. The
	# former are identified by having a letter after the '#' and starting in
	# the first column.
	$line =~ s|!.*$||;
	$line =~ s|(?<=.)#.*$||;
	$line =~ s|^#([^a-z].*)?$||;
    }

    $line =~ s|/\*.*\*/||;	# ... and C-style comments...
    $line =~ s|^\s+||;		# ... and skip white spaces in beginning
    $line =~ s|\s+$||;		# ... and at the end

    if (my $label=label->re(\$line))	{ print $label->out(); }

    if (my $directive=directive->re(\$line)) {
	printf "%s",$directive->out();
    } elsif (my $opcode=opcode->re(\$line)) {
	my $asm = eval("\$".$opcode->mnemonic());

	if ((ref($asm) eq 'CODE') && scalar(my @bytes=&$asm($line))) {
	    print $gas?".byte\t":"DB\t",join(',',@bytes),"\n";
	    next;
	}

	my @args;
	ARGUMENT: while (1) {
	    my $arg;

	    ($arg=register->re(\$line, $opcode))||
	    ($arg=const->re(\$line))		||
	    ($arg=ea->re(\$line, $opcode))	||
	    ($arg=expr->re(\$line, $opcode))	||
	    last ARGUMENT;

	    push @args,$arg;

	    last ARGUMENT if ($line !~ /^,/);

	    $line =~ s/^,\s*//;
	} # ARGUMENT:

	if ($#args>=0) {
	    my $insn;
	    my $sz=$opcode->size();

	    if ($gas) {
		$insn = $opcode->out($#args>=1?$args[$#args]->size():$sz);
		@args = map($_->out($sz),@args);
		printf "\t%s\t%s",$insn,join(",",@args);
	    } else {
		$insn = $opcode->out();
		foreach (@args) {
		    my $arg = $_->out();
		    # $insn.=$sz compensates for movq, pinsrw, ...
		    if ($arg =~ /^xmm[0-9]+$/) { $insn.=$sz; $sz="x" if(!$sz); last; }
		    if ($arg =~ /^ymm[0-9]+$/) { $insn.=$sz; $sz="y" if(!$sz); last; }
		    if ($arg =~ /^zmm[0-9]+$/) { $insn.=$sz; $sz="z" if(!$sz); last; }
		    if ($arg =~ /^mm[0-9]+$/)  { $insn.=$sz; $sz="q" if(!$sz); last; }
		}
		@args = reverse(@args);
		undef $sz if ($nasm && $opcode->mnemonic() eq "lea");
		printf "\t%s\t%s",$insn,join(",",map($_->out($sz),@args));
	    }
	} else {
	    printf "\t%s",$opcode->out();
	}
    }

    print $line,"\n";
}

while(defined(my $line=<>)) {
    process_line($line);
}
foreach my $line (split(/\n/, seh_directive->pdata_and_xdata())) {
    process_line($line);
}

print "\n$current_segment\tENDS\n"	if ($current_segment && $masm);
if ($masm) {
    print "END\n";
} elsif ($gas) {
    print "#endif\n";
} elsif ($nasm) {
    print <<___;
\%else
; Work around https://bugzilla.nasm.us/show_bug.cgi?id=3392738
ret
\%endif
___
} else {
    die "unknown assembler";
}

close STDOUT or die "error closing STDOUT: $!";

#################################################
# Cross-reference x86_64 ABI "card"
#
# 		Unix		Win64
# %rax		*		*
# %rbx		-		-
# %rcx		#4		#1
# %rdx		#3		#2
# %rsi		#2		-
# %rdi		#1		-
# %rbp		-		-
# %rsp		-		-
# %r8		#5		#3
# %r9		#6		#4
# %r10		*		*
# %r11		*		*
# %r12		-		-
# %r13		-		-
# %r14		-		-
# %r15		-		-
#
# (*)	volatile register
# (-)	preserved by callee
# (#)	Nth argument, volatile
#
# In Unix terms top of stack is argument transfer area for arguments
# which could not be accommodated in registers. Or in other words 7th
# [integer] argument resides at 8(%rsp) upon function entry point.
# 128 bytes above %rsp constitute a "red zone" which is not touched
# by signal handlers and can be used as temporal storage without
# allocating a frame.
#
# In Win64 terms N*8 bytes on top of stack is argument transfer area,
# which belongs to/can be overwritten by callee. N is the number of
# arguments passed to callee, *but* not less than 4! This means that
# upon function entry point 5th argument resides at 40(%rsp), as well
# as that 32 bytes from 8(%rsp) can always be used as temporal
# storage [without allocating a frame]. One can actually argue that
# one can assume a "red zone" above stack pointer under Win64 as well.
# Point is that at apparently no occasion Windows kernel would alter
# the area above user stack pointer in true asynchronous manner...
#
# All the above means that if assembler programmer adheres to Unix
# register and stack layout, but disregards the "red zone" existence,
# it's possible to use following prologue and epilogue to "gear" from
# Unix to Win64 ABI in leaf functions with not more than 6 arguments.
#
# omnipotent_function:
# ifdef WIN64
#	movq	%rdi,8(%rsp)
#	movq	%rsi,16(%rsp)
#	movq	%rcx,%rdi	; if 1st argument is actually present
#	movq	%rdx,%rsi	; if 2nd argument is actually ...
#	movq	%r8,%rdx	; if 3rd argument is ...
#	movq	%r9,%rcx	; if 4th argument ...
#	movq	40(%rsp),%r8	; if 5th ...
#	movq	48(%rsp),%r9	; if 6th ...
# endif
#	...
# ifdef WIN64
#	movq	8(%rsp),%rdi
#	movq	16(%rsp),%rsi
# endif
#	ret
#
#################################################
# Win64 SEH, Structured Exception Handling.
#
# Unlike on Unix systems(*) lack of Win64 stack unwinding information
# has undesired side-effect at run-time: if an exception is raised in
# assembler subroutine such as those in question (basically we're
# referring to segmentation violations caused by malformed input
# parameters), the application is briskly terminated without invoking
# any exception handlers, most notably without generating memory dump
# or any user notification whatsoever. This poses a problem. It's
# possible to address it by registering custom language-specific
# handler that would restore processor context to the state at
# subroutine entry point and return "exception is not handled, keep
# unwinding" code. Writing such handler can be a challenge... But it's
# doable, though requires certain coding convention. Consider following
# snippet:
#
# .type	function,@function
# function:
#	movq	%rsp,%rax	# copy rsp to volatile register
#	pushq	%r15		# save non-volatile registers
#	pushq	%rbx
#	pushq	%rbp
#	movq	%rsp,%r11
#	subq	%rdi,%r11	# prepare [variable] stack frame
#	andq	$-64,%r11
#	movq	%rax,0(%r11)	# check for exceptions
#	movq	%r11,%rsp	# allocate [variable] stack frame
#	movq	%rax,0(%rsp)	# save original rsp value
# magic_point:
#	...
#	movq	0(%rsp),%rcx	# pull original rsp value
#	movq	-24(%rcx),%rbp	# restore non-volatile registers
#	movq	-16(%rcx),%rbx
#	movq	-8(%rcx),%r15
#	movq	%rcx,%rsp	# restore original rsp
# magic_epilogue:
#	ret
# .size function,.-function
#
# The key is that up to magic_point copy of original rsp value remains
# in chosen volatile register and no non-volatile register, except for
# rsp, is modified. While past magic_point rsp remains constant till
# the very end of the function. In this case custom language-specific
# exception handler would look like this:
#
# EXCEPTION_DISPOSITION handler (EXCEPTION_RECORD *rec,ULONG64 frame,
#		CONTEXT *context,DISPATCHER_CONTEXT *disp)
# {	ULONG64 *rsp = (ULONG64 *)context->Rax;
#	ULONG64  rip = context->Rip;
#
#	if (rip >= magic_point)
#	{   rsp = (ULONG64 *)context->Rsp;
#	    if (rip < magic_epilogue)
#	    {	rsp = (ULONG64 *)rsp[0];
#		context->Rbp = rsp[-3];
#		context->Rbx = rsp[-2];
#		context->R15 = rsp[-1];
#	    }
#	}
#	context->Rsp = (ULONG64)rsp;
#	context->Rdi = rsp[1];
#	context->Rsi = rsp[2];
#
#	memcpy (disp->ContextRecord,context,sizeof(CONTEXT));
#	RtlVirtualUnwind(UNW_FLAG_NHANDLER,disp->ImageBase,
#		dips->ControlPc,disp->FunctionEntry,disp->ContextRecord,
#		&disp->HandlerData,&disp->EstablisherFrame,NULL);
#	return ExceptionContinueSearch;
# }
#
# It's appropriate to implement this handler in assembler, directly in
# function's module. In order to do that one has to know members'
# offsets in CONTEXT and DISPATCHER_CONTEXT structures and some constant
# values. Here they are:
#
#	CONTEXT.Rax				120
#	CONTEXT.Rcx				128
#	CONTEXT.Rdx				136
#	CONTEXT.Rbx				144
#	CONTEXT.Rsp				152
#	CONTEXT.Rbp				160
#	CONTEXT.Rsi				168
#	CONTEXT.Rdi				176
#	CONTEXT.R8				184
#	CONTEXT.R9				192
#	CONTEXT.R10				200
#	CONTEXT.R11				208
#	CONTEXT.R12				216
#	CONTEXT.R13				224
#	CONTEXT.R14				232
#	CONTEXT.R15				240
#	CONTEXT.Rip				248
#	CONTEXT.Xmm6				512
#	sizeof(CONTEXT)				1232
#	DISPATCHER_CONTEXT.ControlPc		0
#	DISPATCHER_CONTEXT.ImageBase		8
#	DISPATCHER_CONTEXT.FunctionEntry	16
#	DISPATCHER_CONTEXT.EstablisherFrame	24
#	DISPATCHER_CONTEXT.TargetIp		32
#	DISPATCHER_CONTEXT.ContextRecord	40
#	DISPATCHER_CONTEXT.LanguageHandler	48
#	DISPATCHER_CONTEXT.HandlerData		56
#	UNW_FLAG_NHANDLER			0
#	ExceptionContinueSearch			1
#
# In order to tie the handler to the function one has to compose
# couple of structures: one for .xdata segment and one for .pdata.
#
# UNWIND_INFO structure for .xdata segment would be
#
# function_unwind_info:
#	.byte	9,0,0,0
#	.rva	handler
#
# This structure designates exception handler for a function with
# zero-length prologue, no stack frame or frame register.
#
# To facilitate composing of .pdata structures, auto-generated "gear"
# prologue copies rsp value to rax and denotes next instruction with
# .LSEH_begin_{function_name} label. This essentially defines the SEH
# styling rule mentioned in the beginning. Position of this label is
# chosen in such manner that possible exceptions raised in the "gear"
# prologue would be accounted to caller and unwound from latter's frame.
# End of function is marked with respective .LSEH_end_{function_name}
# label. To summarize, .pdata segment would contain
#
#	.rva	.LSEH_begin_function
#	.rva	.LSEH_end_function
#	.rva	function_unwind_info
#
# Reference to function_unwind_info from .xdata segment is the anchor.
# In case you wonder why references are 32-bit .rvas and not 64-bit
# .quads. References put into these two segments are required to be
# *relative* to the base address of the current binary module, a.k.a.
# image base. No Win64 module, be it .exe or .dll, can be larger than
# 2GB and thus such relative references can be and are accommodated in
# 32 bits.
#
# Having reviewed the example function code, one can argue that "movq
# %rsp,%rax" above is redundant. It is not! Keep in mind that on Unix
# rax would contain an undefined value. If this "offends" you, use
# another register and refrain from modifying rax till magic_point is
# reached, i.e. as if it was a non-volatile register. If more registers
# are required prior [variable] frame setup is completed, note that
# nobody says that you can have only one "magic point." You can
# "liberate" non-volatile registers by denoting last stack off-load
# instruction and reflecting it in finer grade unwind logic in handler.
# After all, isn't it why it's called *language-specific* handler...
#
# SE handlers are also involved in unwinding stack when executable is
# profiled or debugged. Profiling implies additional limitations that
# are too subtle to discuss here. For now it's sufficient to say that
# in order to simplify handlers one should either a) offload original
# %rsp to stack (like discussed above); or b) if you have a register to
# spare for frame pointer, choose volatile one.
#
# (*)	Note that we're talking about run-time, not debug-time. Lack of
#	unwind information makes debugging hard on both Windows and
#	Unix. "Unlike" refers to the fact that on Unix signal handler
#	will always be invoked, core dumped and appropriate exit code
#	returned to parent (for user notification).
