#include <stdlib.h>
#include <string.h>

#include "ia32_reg.h"
#include "ia32_insn.h"

#define NUM_X86_REGS	92

/* register sizes */
#define REG_DWORD_SIZE 4
#define REG_WORD_SIZE 2
#define REG_BYTE_SIZE 1
#define REG_MMX_SIZE 8
#define REG_SIMD_SIZE 16
#define REG_DEBUG_SIZE 4
#define REG_CTRL_SIZE 4
#define REG_TEST_SIZE 4
#define REG_SEG_SIZE 2
#define REG_FPU_SIZE 10
#define REG_FLAGS_SIZE 4
#define REG_FPCTRL_SIZE 2
#define REG_FPSTATUS_SIZE 2
#define REG_FPTAG_SIZE 2
#define REG_EIP_SIZE 4
#define REG_IP_SIZE 2

/* REGISTER ALIAS TABLE:
 *
 * NOTE: the MMX register mapping is fixed to the physical registers
 * used by the FPU. The floating FP stack does not effect the location
 * of the MMX registers, so this aliasing is not 100% accurate.
 * */
static struct {
	unsigned char alias;	/* id of register this is an alias for */
	unsigned char shift;	/* # of bits register must be shifted */
} ia32_reg_aliases[] = {
	{ 0,0 },
	{ REG_DWORD_OFFSET,	0 }, 	/* al :  1 */
	{ REG_DWORD_OFFSET,	8 }, 	/* ah :  2 */
	{ REG_DWORD_OFFSET,	0 }, 	/* ax :  3 */
	{ REG_DWORD_OFFSET + 1,	0 }, 	/* cl :  4 */
	{ REG_DWORD_OFFSET + 1,	8 }, 	/* ch :  5 */
	{ REG_DWORD_OFFSET + 1,	0 }, 	/* cx :  6 */
	{ REG_DWORD_OFFSET + 2,	0 }, 	/* dl :  7 */
	{ REG_DWORD_OFFSET + 2,	8 }, 	/* dh :  8 */
	{ REG_DWORD_OFFSET + 2,	0 }, 	/* dx :  9 */
	{ REG_DWORD_OFFSET + 3,	0 }, 	/* bl : 10 */
	{ REG_DWORD_OFFSET + 3,	8 }, 	/* bh : 11 */
	{ REG_DWORD_OFFSET + 3,	0 }, 	/* bx : 12 */
	{ REG_DWORD_OFFSET + 4,	0 }, 	/* sp : 13 */
	{ REG_DWORD_OFFSET + 5,	0 }, 	/* bp : 14 */
	{ REG_DWORD_OFFSET + 6,	0 }, 	/* si : 15 */
	{ REG_DWORD_OFFSET + 7,	0 }, 	/* di : 16 */
	{ REG_EIP_INDEX,	0 }, 	/* ip : 17 */
	{ REG_FPU_OFFSET,	0 }, 	/* mm0 : 18 */
	{ REG_FPU_OFFSET + 1,	0 }, 	/* mm1 : 19 */
	{ REG_FPU_OFFSET + 2,	0 }, 	/* mm2 : 20 */
	{ REG_FPU_OFFSET + 3,	0 }, 	/* mm3 : 21 */
	{ REG_FPU_OFFSET + 4,	0 }, 	/* mm4 : 22 */
	{ REG_FPU_OFFSET + 5,	0 }, 	/* mm5 : 23 */
	{ REG_FPU_OFFSET + 6,	0 }, 	/* mm6 : 24 */
	{ REG_FPU_OFFSET + 7,	0 } 	/* mm7 : 25 */
 };

/* REGISTER TABLE: size, type, and name of every register in the 
 *                 CPU. Does not include MSRs since the are, after all,
 *                 model specific. */
static struct {
	unsigned int size;
	enum x86_reg_type type;
	unsigned int alias;
	char mnemonic[8];
} ia32_reg_table[NUM_X86_REGS + 2] = {
	{ 0, 0, 0, "" },
	/* REG_DWORD_OFFSET */
	{ REG_DWORD_SIZE, reg_gen | reg_ret, 0, "eax" },
	{ REG_DWORD_SIZE, reg_gen | reg_count, 0, "ecx" },
	{ REG_DWORD_SIZE, reg_gen, 0, "edx" },
	{ REG_DWORD_SIZE, reg_gen, 0, "ebx" },
	/* REG_ESP_INDEX */
	{ REG_DWORD_SIZE, reg_gen | reg_sp, 0, "esp" },
	{ REG_DWORD_SIZE, reg_gen | reg_fp, 0, "ebp" },
	{ REG_DWORD_SIZE, reg_gen | reg_src, 0, "esi" },
	{ REG_DWORD_SIZE, reg_gen | reg_dest, 0, "edi" },
	/* REG_WORD_OFFSET */
	{ REG_WORD_SIZE, reg_gen | reg_ret, 3, "ax" },
	{ REG_WORD_SIZE, reg_gen | reg_count, 6, "cx" },
	{ REG_WORD_SIZE, reg_gen, 9, "dx" },
	{ REG_WORD_SIZE, reg_gen, 12, "bx" },
	{ REG_WORD_SIZE, reg_gen | reg_sp, 13, "sp" },
	{ REG_WORD_SIZE, reg_gen | reg_fp, 14, "bp" },
	{ REG_WORD_SIZE, reg_gen | reg_src, 15, "si" },
	{ REG_WORD_SIZE, reg_gen | reg_dest, 16, "di" },
	/* REG_BYTE_OFFSET */
	{ REG_BYTE_SIZE, reg_gen, 1, "al" },
	{ REG_BYTE_SIZE, reg_gen, 4, "cl" },
	{ REG_BYTE_SIZE, reg_gen, 7, "dl" },
	{ REG_BYTE_SIZE, reg_gen, 10, "bl" },
	{ REG_BYTE_SIZE, reg_gen, 2, "ah" },
	{ REG_BYTE_SIZE, reg_gen, 5, "ch" },
	{ REG_BYTE_SIZE, reg_gen, 8, "dh" },
	{ REG_BYTE_SIZE, reg_gen, 11, "bh" },
	/* REG_MMX_OFFSET */
	{ REG_MMX_SIZE, reg_simd, 18, "mm0" },
	{ REG_MMX_SIZE, reg_simd, 19, "mm1" },
	{ REG_MMX_SIZE, reg_simd, 20, "mm2" },
	{ REG_MMX_SIZE, reg_simd, 21, "mm3" },
	{ REG_MMX_SIZE, reg_simd, 22, "mm4" },
	{ REG_MMX_SIZE, reg_simd, 23, "mm5" },
	{ REG_MMX_SIZE, reg_simd, 24, "mm6" },
	{ REG_MMX_SIZE, reg_simd, 25, "mm7" },
	/* REG_SIMD_OFFSET */
	{ REG_SIMD_SIZE, reg_simd, 0, "xmm0" },
	{ REG_SIMD_SIZE, reg_simd, 0, "xmm1" },
	{ REG_SIMD_SIZE, reg_simd, 0, "xmm2" },
	{ REG_SIMD_SIZE, reg_simd, 0, "xmm3" },
	{ REG_SIMD_SIZE, reg_simd, 0, "xmm4" },
	{ REG_SIMD_SIZE, reg_simd, 0, "xmm5" },
	{ REG_SIMD_SIZE, reg_simd, 0, "xmm6" },
	{ REG_SIMD_SIZE, reg_simd, 0, "xmm7" },
	/* REG_DEBUG_OFFSET */
	{ REG_DEBUG_SIZE, reg_sys, 0, "dr0" },
	{ REG_DEBUG_SIZE, reg_sys, 0, "dr1" },
	{ REG_DEBUG_SIZE, reg_sys, 0, "dr2" },
	{ REG_DEBUG_SIZE, reg_sys, 0, "dr3" },
	{ REG_DEBUG_SIZE, reg_sys, 0, "dr4" },
	{ REG_DEBUG_SIZE, reg_sys, 0, "dr5" },
	{ REG_DEBUG_SIZE, reg_sys, 0, "dr6" },
	{ REG_DEBUG_SIZE, reg_sys, 0, "dr7" },
	/* REG_CTRL_OFFSET */
	{ REG_CTRL_SIZE, reg_sys, 0, "cr0" },
	{ REG_CTRL_SIZE, reg_sys, 0, "cr1" },
	{ REG_CTRL_SIZE, reg_sys, 0, "cr2" },
	{ REG_CTRL_SIZE, reg_sys, 0, "cr3" },
	{ REG_CTRL_SIZE, reg_sys, 0, "cr4" },
	{ REG_CTRL_SIZE, reg_sys, 0, "cr5" },
	{ REG_CTRL_SIZE, reg_sys, 0, "cr6" },
	{ REG_CTRL_SIZE, reg_sys, 0, "cr7" },
	/* REG_TEST_OFFSET */
	{ REG_TEST_SIZE, reg_sys, 0, "tr0" },
	{ REG_TEST_SIZE, reg_sys, 0, "tr1" },
	{ REG_TEST_SIZE, reg_sys, 0, "tr2" },
	{ REG_TEST_SIZE, reg_sys, 0, "tr3" },
	{ REG_TEST_SIZE, reg_sys, 0, "tr4" },
	{ REG_TEST_SIZE, reg_sys, 0, "tr5" },
	{ REG_TEST_SIZE, reg_sys, 0, "tr6" },
	{ REG_TEST_SIZE, reg_sys, 0, "tr7" },
	/* REG_SEG_OFFSET */
	{ REG_SEG_SIZE, reg_seg, 0, "es" },
	{ REG_SEG_SIZE, reg_seg, 0, "cs" },
	{ REG_SEG_SIZE, reg_seg, 0, "ss" },
	{ REG_SEG_SIZE, reg_seg, 0, "ds" },
	{ REG_SEG_SIZE, reg_seg, 0, "fs" },
	{ REG_SEG_SIZE, reg_seg, 0, "gs" },
	/* REG_LDTR_INDEX */
	{ REG_DWORD_SIZE, reg_sys, 0, "ldtr" },
	/* REG_GDTR_INDEX */
	{ REG_DWORD_SIZE, reg_sys, 0, "gdtr" },
	/* REG_FPU_OFFSET */
	{ REG_FPU_SIZE, reg_fpu, 0, "st(0)" },
	{ REG_FPU_SIZE, reg_fpu, 0, "st(1)" },
	{ REG_FPU_SIZE, reg_fpu, 0, "st(2)" },
	{ REG_FPU_SIZE, reg_fpu, 0, "st(3)" },
	{ REG_FPU_SIZE, reg_fpu, 0, "st(4)" },
	{ REG_FPU_SIZE, reg_fpu, 0, "st(5)" },
	{ REG_FPU_SIZE, reg_fpu, 0, "st(6)" },
	{ REG_FPU_SIZE, reg_fpu, 0, "st(7)" },
	/* REG_FLAGS_INDEX : 81 */
	{ REG_FLAGS_SIZE, reg_cond, 0, "eflags" }, 
	/* REG_FPCTRL_INDEX  : 82*/
	{ REG_FPCTRL_SIZE, reg_fpu | reg_sys, 0, "fpctrl" }, 
	/* REG_FPSTATUS_INDEX : 83*/
	{ REG_FPSTATUS_SIZE, reg_fpu | reg_sys, 0, "fpstat" },
	/* REG_FPTAG_INDEX : 84 */
	{ REG_FPTAG_SIZE, reg_fpu | reg_sys, 0, "fptag" }, 
	/* REG_EIP_INDEX : 85 */
	{ REG_EIP_SIZE, reg_pc, 0, "eip" },
	/* REG_IP_INDEX : 86 */
	{ REG_IP_SIZE, reg_pc, 17, "ip" },
	/* REG_IDTR_INDEX : 87 */
	{ REG_DWORD_SIZE, reg_sys, 0, "idtr" },
	/* REG_MXCSG_INDEX : SSE Control Reg : 88 */
	{ REG_DWORD_SIZE, reg_sys | reg_simd, 0, "mxcsr" },
	/* REG_TR_INDEX : Task Register : 89 */
	{ 16 + 64, reg_sys, 0, "tr" },
	/* REG_CSMSR_INDEX : SYSENTER_CS_MSR : 90 */
	{ REG_DWORD_SIZE, reg_sys, 0, "cs_msr" },
	/* REG_ESPMSR_INDEX : SYSENTER_ESP_MSR : 91 */
	{ REG_DWORD_SIZE, reg_sys, 0, "esp_msr" },
	/* REG_EIPMSR_INDEX : SYSENTER_EIP_MSR : 92 */
	{ REG_DWORD_SIZE, reg_sys, 0, "eip_msr" },
	{ 0 }
 };


static size_t sz_regtable = NUM_X86_REGS + 1;


void ia32_handle_register( x86_reg_t *reg, size_t id ) {
	unsigned int alias;
	if (! id || id > sz_regtable ) {
		return;
	}

	memset( reg, 0, sizeof(x86_reg_t) );

        strncpy( reg->name, ia32_reg_table[id].mnemonic, MAX_REGNAME );

        reg->type = ia32_reg_table[id].type;
        reg->size = ia32_reg_table[id].size;

	alias = ia32_reg_table[id].alias;
	if ( alias ) {
		reg->alias = ia32_reg_aliases[alias].alias;
		reg->shift = ia32_reg_aliases[alias].shift;
	}
        reg->id = id;

	return;
}

size_t ia32_true_register_id( size_t id ) {
	size_t reg;

	if (! id || id > sz_regtable ) {
		return 0;
	}

	reg = id;
	if (ia32_reg_table[reg].alias) {
		reg = ia32_reg_aliases[ia32_reg_table[reg].alias].alias;
	}
	return reg;
}
