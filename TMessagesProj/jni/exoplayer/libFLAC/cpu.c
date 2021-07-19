/* libFLAC - Free Lossless Audio Codec library
 * Copyright (C) 2001-2009  Josh Coalson
 * Copyright (C) 2011-2016  Xiph.Org Foundation
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * - Redistributions of source code must retain the above copyright
 * notice, this list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above copyright
 * notice, this list of conditions and the following disclaimer in the
 * documentation and/or other materials provided with the distribution.
 *
 * - Neither the name of the Xiph.org Foundation nor the names of its
 * contributors may be used to endorse or promote products derived from
 * this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * ``AS IS'' AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE FOUNDATION OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

#ifdef HAVE_CONFIG_H
#  include <config.h>
#endif

#include "private/cpu.h"
#include "share/compat.h"
#include <stdlib.h>
#include <string.h>

#if defined _MSC_VER
#include <intrin.h> /* for __cpuid() and _xgetbv() */
#elif defined __GNUC__ && defined HAVE_CPUID_H
#include <cpuid.h> /* for __get_cpuid() and __get_cpuid_max() */
#endif

#ifndef NDEBUG
#include <stdio.h>
#define dfprintf fprintf
#else
/* This is bad practice, it should be a static void empty function */
#define dfprintf(file, format, ...)
#endif


#if (defined FLAC__CPU_IA32 || defined FLAC__CPU_X86_64) && (defined FLAC__HAS_NASM || FLAC__HAS_X86INTRIN) && !defined FLAC__NO_ASM

/* these are flags in EDX of CPUID AX=00000001 */
static const uint32_t FLAC__CPUINFO_X86_CPUID_CMOV    = 0x00008000;
static const uint32_t FLAC__CPUINFO_X86_CPUID_MMX     = 0x00800000;
static const uint32_t FLAC__CPUINFO_X86_CPUID_SSE     = 0x02000000;
static const uint32_t FLAC__CPUINFO_X86_CPUID_SSE2    = 0x04000000;

/* these are flags in ECX of CPUID AX=00000001 */
static const uint32_t FLAC__CPUINFO_X86_CPUID_SSE3    = 0x00000001;
static const uint32_t FLAC__CPUINFO_X86_CPUID_SSSE3   = 0x00000200;
static const uint32_t FLAC__CPUINFO_X86_CPUID_SSE41   = 0x00080000;
static const uint32_t FLAC__CPUINFO_X86_CPUID_SSE42   = 0x00100000;
static const uint32_t FLAC__CPUINFO_X86_CPUID_OSXSAVE = 0x08000000;
static const uint32_t FLAC__CPUINFO_X86_CPUID_AVX     = 0x10000000;
static const uint32_t FLAC__CPUINFO_X86_CPUID_FMA     = 0x00001000;

/* these are flags in EBX of CPUID AX=00000007 */
static const uint32_t FLAC__CPUINFO_X86_CPUID_AVX2    = 0x00000020;

static uint32_t
cpu_xgetbv_x86(void)
{
#if (defined _MSC_VER || defined __INTEL_COMPILER) && FLAC__AVX_SUPPORTED
	return (uint32_t)_xgetbv(0);
#elif defined __GNUC__
	uint32_t lo, hi;
	__asm__ volatile (".byte 0x0f, 0x01, 0xd0" : "=a"(lo), "=d"(hi) : "c" (0));
	return lo;
#else
	return 0;
#endif
}

static uint32_t
cpu_have_cpuid(void)
{
#if defined FLAC__CPU_X86_64 || defined __i686__ || defined __SSE__ || (defined _M_IX86_FP && _M_IX86_FP > 0)
	/* target CPU does have CPUID instruction */
	return 1;
#elif defined FLAC__HAS_NASM
	return FLAC__cpu_have_cpuid_asm_ia32();
#elif defined __GNUC__ && defined HAVE_CPUID_H
	if (__get_cpuid_max(0, 0) != 0)
		return 1;
	else
		return 0;
#elif defined _MSC_VER
	FLAC__uint32 flags1, flags2;
	__asm {
		pushfd
		pushfd
		pop		eax
		mov		flags1, eax
		xor		eax, 0x200000
		push	eax
		popfd
		pushfd
		pop		eax
		mov		flags2, eax
		popfd
	}
	if (((flags1^flags2) & 0x200000) != 0)
		return 1;
	else
		return 0;
#else
	return 0;
#endif
}

static void
cpuinfo_x86(FLAC__uint32 level, FLAC__uint32 *eax, FLAC__uint32 *ebx, FLAC__uint32 *ecx, FLAC__uint32 *edx)
{
#if defined _MSC_VER
	int cpuinfo[4];
	int ext = level & 0x80000000;
	__cpuid(cpuinfo, ext);
	if ((uint32_t)cpuinfo[0] >= level) {
#if FLAC__AVX_SUPPORTED
		__cpuidex(cpuinfo, level, 0); /* for AVX2 detection */
#else
		__cpuid(cpuinfo, level); /* some old compilers don't support __cpuidex */
#endif
		*eax = cpuinfo[0]; *ebx = cpuinfo[1]; *ecx = cpuinfo[2]; *edx = cpuinfo[3];
		return;
	}
#elif defined __GNUC__ && defined HAVE_CPUID_H
	FLAC__uint32 ext = level & 0x80000000;
	__cpuid(ext, *eax, *ebx, *ecx, *edx);
	if (*eax >= level) {
		__cpuid_count(level, 0, *eax, *ebx, *ecx, *edx);
		return;
	}
#elif defined FLAC__HAS_NASM && defined FLAC__CPU_IA32
	FLAC__cpu_info_asm_ia32(level, eax, ebx, ecx, edx);
	return;
#endif
	*eax = *ebx = *ecx = *edx = 0;
}

#endif

static void
x86_cpu_info (FLAC__CPUInfo *info)
{
#if (defined FLAC__CPU_IA32 || defined FLAC__CPU_X86_64) && (defined FLAC__HAS_NASM || FLAC__HAS_X86INTRIN) && !defined FLAC__NO_ASM
	FLAC__bool x86_osxsave = false;
	FLAC__bool os_avx = false;
	FLAC__uint32 flags_eax, flags_ebx, flags_ecx, flags_edx;

	info->use_asm = true; /* we assume a minimum of 80386 */
	if (!cpu_have_cpuid())
		return;

	cpuinfo_x86(0, &flags_eax, &flags_ebx, &flags_ecx, &flags_edx);
	info->x86.intel = (flags_ebx == 0x756E6547 && flags_edx == 0x49656E69 && flags_ecx == 0x6C65746E) ? true : false; /* GenuineIntel */
	cpuinfo_x86(1, &flags_eax, &flags_ebx, &flags_ecx, &flags_edx);

	info->x86.cmov  = (flags_edx & FLAC__CPUINFO_X86_CPUID_CMOV ) ? true : false;
	info->x86.mmx   = (flags_edx & FLAC__CPUINFO_X86_CPUID_MMX  ) ? true : false;
	info->x86.sse   = (flags_edx & FLAC__CPUINFO_X86_CPUID_SSE  ) ? true : false;
	info->x86.sse2  = (flags_edx & FLAC__CPUINFO_X86_CPUID_SSE2 ) ? true : false;
	info->x86.sse3  = (flags_ecx & FLAC__CPUINFO_X86_CPUID_SSE3 ) ? true : false;
	info->x86.ssse3 = (flags_ecx & FLAC__CPUINFO_X86_CPUID_SSSE3) ? true : false;
	info->x86.sse41 = (flags_ecx & FLAC__CPUINFO_X86_CPUID_SSE41) ? true : false;
	info->x86.sse42 = (flags_ecx & FLAC__CPUINFO_X86_CPUID_SSE42) ? true : false;

	if (FLAC__AVX_SUPPORTED) {
		x86_osxsave     = (flags_ecx & FLAC__CPUINFO_X86_CPUID_OSXSAVE) ? true : false;
		info->x86.avx   = (flags_ecx & FLAC__CPUINFO_X86_CPUID_AVX    ) ? true : false;
		info->x86.fma   = (flags_ecx & FLAC__CPUINFO_X86_CPUID_FMA    ) ? true : false;
		cpuinfo_x86(7, &flags_eax, &flags_ebx, &flags_ecx, &flags_edx);
		info->x86.avx2  = (flags_ebx & FLAC__CPUINFO_X86_CPUID_AVX2   ) ? true : false;
	}

#if defined FLAC__CPU_IA32
	dfprintf(stderr, "CPU info (IA-32):\n");
#else
	dfprintf(stderr, "CPU info (x86-64):\n");
#endif
	dfprintf(stderr, "  CMOV ....... %c\n", info->x86.cmov    ? 'Y' : 'n');
	dfprintf(stderr, "  MMX ........ %c\n", info->x86.mmx     ? 'Y' : 'n');
	dfprintf(stderr, "  SSE ........ %c\n", info->x86.sse     ? 'Y' : 'n');
	dfprintf(stderr, "  SSE2 ....... %c\n", info->x86.sse2    ? 'Y' : 'n');
	dfprintf(stderr, "  SSE3 ....... %c\n", info->x86.sse3    ? 'Y' : 'n');
	dfprintf(stderr, "  SSSE3 ...... %c\n", info->x86.ssse3   ? 'Y' : 'n');
	dfprintf(stderr, "  SSE41 ...... %c\n", info->x86.sse41   ? 'Y' : 'n');
	dfprintf(stderr, "  SSE42 ...... %c\n", info->x86.sse42   ? 'Y' : 'n');

	if (FLAC__AVX_SUPPORTED) {
		dfprintf(stderr, "  AVX ........ %c\n", info->x86.avx     ? 'Y' : 'n');
		dfprintf(stderr, "  FMA ........ %c\n", info->x86.fma     ? 'Y' : 'n');
		dfprintf(stderr, "  AVX2 ....... %c\n", info->x86.avx2    ? 'Y' : 'n');
	}

	/*
	 * now have to check for OS support of AVX instructions
	 */
	if (FLAC__AVX_SUPPORTED && info->x86.avx && x86_osxsave && (cpu_xgetbv_x86() & 0x6) == 0x6) {
		os_avx = true;
	}
	if (os_avx) {
		dfprintf(stderr, "  AVX OS sup . %c\n", info->x86.avx ? 'Y' : 'n');
	}
	if (!os_avx) {
		/* no OS AVX support */
		info->x86.avx     = false;
		info->x86.avx2    = false;
		info->x86.fma     = false;
	}
#else
	info->use_asm = false;
#endif
}

void FLAC__cpu_info (FLAC__CPUInfo *info)
{
	memset(info, 0, sizeof(*info));

#ifdef FLAC__CPU_IA32
	info->type = FLAC__CPUINFO_TYPE_IA32;
#elif defined FLAC__CPU_X86_64
	info->type = FLAC__CPUINFO_TYPE_X86_64;
#else
	info->type = FLAC__CPUINFO_TYPE_UNKNOWN;
#endif

	switch (info->type) {
	case FLAC__CPUINFO_TYPE_IA32: /* fallthrough */
	case FLAC__CPUINFO_TYPE_X86_64:
		x86_cpu_info (info);
		break;
	default:
		info->use_asm = false;
		break;
	}
}
