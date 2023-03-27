#include <stdlib.h>

#include "ia32_implicit.h"
#include "ia32_insn.h"
#include "ia32_reg.h"
#include "x86_operand_list.h"

/* Conventions: Register operands which are aliases of another register 
 *   operand (e.g. AX in one operand and AL in another) assume that the
 *   operands are different registers and that alias tracking will resolve
 *   data flow. This means that something like
 *   	mov ax, al
 *   would have 'write only' access for AX and 'read only' access for AL,
 *   even though both AL and AX are read and written */
typedef struct {
	uint32_t type;
	uint32_t operand;
} op_implicit_list_t;

static op_implicit_list_t list_aaa[] = 
	/* 37 : AAA : rw AL */
	/* 3F : AAS : rw AL */
	{{ OP_R | OP_W, REG_BYTE_OFFSET }, {0}};	/* aaa */

static op_implicit_list_t list_aad[] = 
	/* D5 0A, D5 (ib) : AAD : rw AX */
	/* D4 0A, D4 (ib) : AAM : rw AX */
	{{ OP_R | OP_W, REG_WORD_OFFSET }, {0}};	/* aad */

static op_implicit_list_t list_call[] = 
	/* E8, FF, 9A, FF : CALL : rw ESP, rw EIP */
	/* C2, C3, CA, CB : RET  : rw ESP, rw EIP */
	{{ OP_R | OP_W, REG_EIP_INDEX }, 
	 { OP_R | OP_W, REG_ESP_INDEX }, {0}};	/* call, ret */

static op_implicit_list_t list_cbw[] = 
	/* 98 : CBW : r AL, rw AX */
	{{ OP_R | OP_W, REG_WORD_OFFSET },
	 { OP_R, REG_BYTE_OFFSET}, {0}};		/* cbw */

static op_implicit_list_t list_cwde[] = 
	/* 98 : CWDE : r AX, rw EAX */
	{{ OP_R | OP_W, REG_DWORD_OFFSET },
	 { OP_R, REG_WORD_OFFSET }, {0}};		/* cwde */

static op_implicit_list_t list_clts[] = 
	/* 0F 06 : CLTS : rw CR0 */
	{{ OP_R | OP_W, REG_CTRL_OFFSET}, {0}};	/* clts */

static op_implicit_list_t list_cmpxchg[] = 
	/* 0F B0 : CMPXCHG : rw AL */
	{{ OP_R | OP_W, REG_BYTE_OFFSET }, {0}};	/* cmpxchg */

static op_implicit_list_t list_cmpxchgb[] = 
	/* 0F B1 : CMPXCHG : rw EAX */
	{{ OP_R | OP_W, REG_DWORD_OFFSET }, {0}};	/* cmpxchg */

static op_implicit_list_t list_cmpxchg8b[] = 
	/* 0F C7 : CMPXCHG8B : rw EDX, rw EAX, r ECX, r EBX */
	{{ OP_R | OP_W, REG_DWORD_OFFSET }, 
	 { OP_R | OP_W, REG_DWORD_OFFSET + 2 }, 
	 { OP_R, REG_DWORD_OFFSET + 1 }, 
	 { OP_R, REG_DWORD_OFFSET + 3 }, {0}};	/* cmpxchg8b */

static op_implicit_list_t list_cpuid[] = 
	/* 0F A2 : CPUID : rw EAX, w EBX, w ECX, w EDX */
	{{ OP_R | OP_W, REG_DWORD_OFFSET }, 
	 { OP_W, REG_DWORD_OFFSET + 1 }, 
	 { OP_W, REG_DWORD_OFFSET + 2 }, 
	 { OP_W, REG_DWORD_OFFSET + 3 }, {0}};	/* cpuid */

static op_implicit_list_t list_cwd[] = 
	/* 99 : CWD/CWQ : rw EAX, w EDX */
	{{ OP_R | OP_W, REG_DWORD_OFFSET }, 
	 { OP_W, REG_DWORD_OFFSET + 2 }, {0}};	/* cwd */

static op_implicit_list_t list_daa[] = 
	/* 27 : DAA : rw AL */
	/* 2F : DAS : rw AL */
	{{ OP_R | OP_W, REG_BYTE_OFFSET }, {0}};	/* daa */

static op_implicit_list_t list_idiv[] = 
	/* F6 : DIV, IDIV : r AX, w AL, w AH */
	/* FIXED: first op was EAX, not Aw. TODO: verify! */
	{{ OP_R, REG_WORD_OFFSET }, 
	  { OP_W, REG_BYTE_OFFSET },
	  { OP_W, REG_BYTE_OFFSET + 4 }, {0}};	/* div */

static op_implicit_list_t list_div[] = 
	/* F7 : DIV, IDIV : rw EDX, rw EAX */
	{{ OP_R | OP_W, REG_DWORD_OFFSET + 2 }, 
	  { OP_R | OP_W, REG_DWORD_OFFSET }, {0}};	/* div */

static op_implicit_list_t list_enter[] = 
	/* C8 : ENTER : rw ESP w EBP */
	{{ OP_R | OP_W, REG_DWORD_OFFSET + 4 }, 
	 { OP_R, REG_DWORD_OFFSET + 5 }, {0}};	/* enter */

static op_implicit_list_t list_f2xm1[] = 
	/* D9 F0 : F2XM1 : rw ST(0) */
	/* D9 E1 : FABS : rw ST(0) */
	/* D9 E0 : FCHS : rw ST(0) */
	/* D9 FF : FCOS : rw ST(0)*/
	/* D8, DA : FDIV : rw ST(0) */
	/* D8, DA : FDIVR : rw ST(0) */
	/* D9 F2 : FPTAN : rw ST(0) */
	/* D9 FC : FRNDINT : rw ST(0) */
	/* D9 FB : FSINCOS : rw ST(0) */
	/* D9 FE : FSIN : rw ST(0) */
	/* D9 FA : FSQRT : rw ST(0) */
	/* D9 F4 : FXTRACT : rw ST(0) */
	{{ OP_R | OP_W, REG_FPU_OFFSET }, {0}};	/* f2xm1 */

static op_implicit_list_t list_fcom[] = 
	/* D8, DC, DE D9 : FCOM : r ST(0) */
	/* DE, DA : FICOM : r ST(0) */
	/* DF, D8 : FIST : r ST(0) */
	/* D9 E4 : FTST : r ST(0) */
	/* D9 E5 : FXAM : r ST(0) */
	{{ OP_R, REG_FPU_OFFSET }, {0}};		/* fcom */

static op_implicit_list_t list_fpatan[] = 
	/* D9 F3 : FPATAN : r ST(0), rw ST(1) */
	{{ OP_R, REG_FPU_OFFSET }, {0}};		/* fpatan */

static op_implicit_list_t list_fprem[] = 
	/* D9 F8, D9 F5 : FPREM : rw ST(0) r ST(1) */
	/* D9 FD : FSCALE : rw ST(0), r ST(1) */
	{{ OP_R | OP_W, REG_FPU_OFFSET }, 	
	 { OP_R, REG_FPU_OFFSET + 1 }, {0}};	/* fprem */

static op_implicit_list_t list_faddp[] = 
	/* DE C1 : FADDP : r ST(0), rw ST(1) */
	/* DE E9 : FSUBP : r ST(0), rw ST(1) */
	/* D9 F1 : FYL2X : r ST(0), rw ST(1) */
	/* D9 F9 : FYL2XP1 : r ST(0), rw ST(1) */
	{{ OP_R, REG_FPU_OFFSET },
	 { OP_R | OP_W, REG_FPU_OFFSET + 1 }, {0}};	/* faddp */

static op_implicit_list_t list_fucompp[] = 
	/* DA E9 : FUCOMPP : r ST(0), r ST(1) */
	{{ OP_R, REG_FPU_OFFSET },
	 { OP_R, REG_FPU_OFFSET + 1 }, {0}};	/* fucompp */

static op_implicit_list_t list_imul[] = 
	/* F6 : IMUL : r AL, w AX */
	/* F6 : MUL : r AL, w AX */
	{{ OP_R, REG_BYTE_OFFSET },
	 { OP_W, REG_WORD_OFFSET }, {0}};		/* imul */

static op_implicit_list_t list_mul[] = 
	/* F7 : IMUL : rw EAX, w EDX */
	/* F7 : MUL : rw EAX, w EDX */
	{{ OP_R | OP_W, REG_DWORD_OFFSET },
	 { OP_W, REG_DWORD_OFFSET + 2 }, {0}};	/* imul */

static op_implicit_list_t list_lahf[] = 
	/* 9F : LAHF : r EFLAGS, w AH */
	{{ OP_R, REG_FLAGS_INDEX },
	 { OP_W, REG_BYTE_OFFSET + 4 }, {0}};	/* lahf */

static op_implicit_list_t list_ldmxcsr[] = 
	/* 0F AE : LDMXCSR : w MXCSR SSE Control Status Reg */
	{{ OP_W, REG_MXCSG_INDEX }, {0}};		/* ldmxcsr */

static op_implicit_list_t list_leave[] = 
	/* C9 : LEAVE :  rw ESP, w EBP */
	{{ OP_R | OP_W, REG_ESP_INDEX },
	 { OP_W, REG_DWORD_OFFSET + 5 }, {0}};	/* leave */

static op_implicit_list_t list_lgdt[] = 
	/* 0F 01 : LGDT : w GDTR */
	{{ OP_W, REG_GDTR_INDEX }, {0}};		/* lgdt */

static op_implicit_list_t list_lidt[] = 
	/* 0F 01 : LIDT : w IDTR */
	{{ OP_W, REG_IDTR_INDEX }, {0}};		/* lidt */

static op_implicit_list_t list_lldt[] = 
	/* 0F 00 : LLDT : w LDTR */
	{{ OP_W, REG_LDTR_INDEX }, {0}};		/* lldt */

static op_implicit_list_t list_lmsw[] = 
	/* 0F 01 : LMSW : w CR0 */
	{{ OP_W, REG_CTRL_OFFSET }, {0}};		/* lmsw */

static op_implicit_list_t list_loop[] = 
	/* E0, E1, E2 : LOOP : rw ECX */
	{{ OP_R | OP_W, REG_DWORD_OFFSET + 1 }, {0}};/* loop */

static op_implicit_list_t list_ltr[] = 
	/* 0F 00 : LTR : w Task Register */
	{{ OP_W, REG_TR_INDEX }, {0}};		/* ltr */

static op_implicit_list_t list_pop[] = 
	/* 8F, 58, 1F, 07, 17, 0F A1, 0F A9 : POP : rw ESP */
	/* FF, 50, 6A, 68, 0E, 16, 1E, 06, 0F A0, 0F A8 : PUSH : rw ESP */
	{{ OP_R | OP_W, REG_ESP_INDEX }, {0}};	/* pop, push */

static op_implicit_list_t list_popad[] = 
	/* 61 : POPAD : rw esp, w edi esi ebp ebx edx ecx eax */
	{{ OP_R | OP_W, REG_ESP_INDEX },
	 { OP_W, REG_DWORD_OFFSET + 7 },
	 { OP_W, REG_DWORD_OFFSET + 6 },
	 { OP_W, REG_DWORD_OFFSET + 5 },
	 { OP_W, REG_DWORD_OFFSET + 3 },
	 { OP_W, REG_DWORD_OFFSET + 2 },
	 { OP_W, REG_DWORD_OFFSET + 1 },
	 { OP_W, REG_DWORD_OFFSET }, {0}};		/* popad */

static op_implicit_list_t list_popfd[] = 
	/* 9D : POPFD : rw esp, w eflags */
	{{ OP_R | OP_W, REG_ESP_INDEX },
	 { OP_W, REG_FLAGS_INDEX }, {0}};		/* popfd */

static op_implicit_list_t list_pushad[] = 
	/* FF, 50, 6A, 68, 0E, 16, 1E, 06, 0F A0, 0F A8 : PUSH : rw ESP */
	/* 60 : PUSHAD : rw esp, r eax ecx edx ebx esp ebp esi edi */
	{{ OP_R | OP_W, REG_ESP_INDEX },
	 { OP_R, REG_DWORD_OFFSET },
	 { OP_R, REG_DWORD_OFFSET + 1 },
	 { OP_R, REG_DWORD_OFFSET + 2 },
	 { OP_R, REG_DWORD_OFFSET + 3 },
	 { OP_R, REG_DWORD_OFFSET + 5 },
	 { OP_R, REG_DWORD_OFFSET + 6 },
	 { OP_R, REG_DWORD_OFFSET + 7 }, {0}};	/* pushad */

static op_implicit_list_t list_pushfd[] = 
	/* 9C : PUSHFD : rw esp, r eflags */
	{{ OP_R | OP_W, REG_ESP_INDEX },
	 { OP_R, REG_FLAGS_INDEX }, {0}};		/* pushfd */

static op_implicit_list_t list_rdmsr[] = 
	/* 0F 32 : RDMSR : r ECX, w EDX, w EAX */
	{{ OP_R, REG_DWORD_OFFSET + 1 },
	 { OP_W, REG_DWORD_OFFSET + 2 },
	 { OP_W, REG_DWORD_OFFSET }, {0}};	/* rdmsr */

static op_implicit_list_t list_rdpmc[] = 
	/* 0F 33 : RDPMC : r ECX, w EDX, w EAX */
	{{ OP_R, REG_DWORD_OFFSET + 1 },
	 { OP_W, REG_DWORD_OFFSET + 2 },
	 { OP_W, REG_DWORD_OFFSET }, {0}};		/* rdpmc */

static op_implicit_list_t list_rdtsc[] = 
	/* 0F 31 : RDTSC : rw EDX, rw EAX */
	{{ OP_R | OP_W, REG_DWORD_OFFSET + 2 },
	 { OP_R | OP_W, REG_DWORD_OFFSET }, {0}};	/* rdtsc */

static op_implicit_list_t list_rep[] = 
	/* F3, F2 ... : REP : rw ECX */
	{{ OP_R | OP_W, REG_DWORD_OFFSET + 1 }, {0}};/* rep */

static op_implicit_list_t list_rsm[] = 
	/* 0F AA : RSM : r CR4, r CR0 */
	{{ OP_R, REG_CTRL_OFFSET + 4 }, 
	 { OP_R, REG_CTRL_OFFSET }, {0}};		/* rsm */

static op_implicit_list_t list_sahf[] = 
	/* 9E : SAHF : r ah, rw eflags (set SF ZF AF PF CF) */
	{{ OP_R, REG_DWORD_OFFSET }, {0}};		/* sahf */

static op_implicit_list_t list_sgdt[] = 
	/* 0F : SGDT : r gdtr */
	/* TODO: finish this! */
	{{ OP_R, REG_DWORD_OFFSET }, {0}};		/* sgdt */

static op_implicit_list_t list_sidt[] = 
	/* 0F : SIDT : r idtr */
	/* TODO: finish this! */
	{{ OP_R, REG_DWORD_OFFSET }, {0}};		/* sidt */

static op_implicit_list_t list_sldt[] = 
	/* 0F : SLDT : r ldtr */
	/* TODO: finish this! */
	{{ OP_R, REG_DWORD_OFFSET }, {0}};		/* sldt */

static op_implicit_list_t list_smsw[] = 
	/* 0F : SMSW : r CR0 */
	/* TODO: finish this! */
	{{ OP_R, REG_DWORD_OFFSET }, {0}};		/* smsw */

static op_implicit_list_t list_stmxcsr[] = 
	/* 0F AE : STMXCSR : r MXCSR */
	/* TODO: finish this! */
	{{ OP_R, REG_DWORD_OFFSET }, {0}};		/* stmxcsr */

static op_implicit_list_t list_str[] = 
	/* 0F 00 : STR : r TR (task register) */
	/* TODO: finish this! */
	{{ OP_R, REG_DWORD_OFFSET }, {0}};		/* str */

static op_implicit_list_t list_sysenter[] = 
	/* 0F 34 : SYSENTER : w cs, w eip, w ss, w esp, r CR0, w eflags
	 *         r sysenter_cs_msr, sysenter_esp_msr, sysenter_eip_msr */
	/* TODO: finish this! */
	{{ OP_R, REG_DWORD_OFFSET }, {0}};		/* sysenter */

static op_implicit_list_t list_sysexit[] = 
	/* 0F 35 : SYSEXIT : r edx, r ecx, w cs, w eip, w ss, w esp
	 * 	   r sysenter_cs_msr */
	/* TODO: finish this! */
	{{ OP_R, REG_DWORD_OFFSET }, {0}};		/* sysexit */

static op_implicit_list_t list_wrmsr[] = 
	/* 0F 30 : WRMST : r edx, r eax, r ecx */
	/* TODO: finish this! */
	{{ OP_R, REG_DWORD_OFFSET }, {0}};		/* wrmsr */

static op_implicit_list_t list_xlat[] = 
	/* D7 : XLAT : rw al r ebx (ptr) */
	/* TODO: finish this! */
	{{ OP_R, REG_DWORD_OFFSET }, {0}};		/* xlat */
/* TODO:
 * monitor 0f 01 c8 eax OP_R ecx OP_R edx OP_R
 * mwait 0f 01 c9 eax OP_R ecx OP_R
 */
static op_implicit_list_t list_monitor[] = 
	{{ OP_R, REG_DWORD_OFFSET }, {0}};		/* monitor */
static op_implicit_list_t list_mwait[] = 
	{{ OP_R, REG_DWORD_OFFSET }, {0}};		/* mwait */

op_implicit_list_t *op_implicit_list[] = {
	/* This is a list of implicit operands which are read/written by
	 * various x86 instructions. Note that modifications to the stack
	 * register are mentioned here, but that additional information on
	 * the effect an instruction has on the stack is contained in the
	 * x86_insn_t 'stack_mod' and 'stack_mod_val' fields. Use of the
	 * eflags register, i.e. setting, clearing, and testing flags, is
	 * not recorded here but rather in the flags_set and flags_tested
	 * fields of the x86_insn_t.*/
	NULL,
	list_aaa, list_aad, list_call, list_cbw,		/* 1 - 4 */
	list_cwde, list_clts, list_cmpxchg, list_cmpxchgb,	/* 5 - 8 */
	list_cmpxchg8b, list_cpuid, list_cwd, list_daa,		/* 9 - 12 */
	list_idiv, list_div, list_enter, list_f2xm1,		/* 13 - 16 */
	list_fcom, list_fpatan, list_fprem, list_faddp,		/* 17 - 20 */
	list_fucompp, list_imul, list_mul, list_lahf,		/* 21 - 24 */
	list_ldmxcsr, list_leave, list_lgdt, list_lidt,		/* 25 - 28 */
	list_lldt, list_lmsw, list_loop, list_ltr,		/* 29 - 32 */
	list_pop, list_popad, list_popfd, list_pushad,		/* 33 - 36 */
	list_pushfd, list_rdmsr, list_rdpmc, list_rdtsc,	/* 37 - 40 */
	/* NOTE: 'REP' is a hack since it is a prefix: if its position
	 * in the table changes, then change IDX_IMPLICIT_REP in the .h */
	list_rep, list_rsm, list_sahf, list_sgdt,		/* 41 - 44 */
	list_sidt, list_sldt, list_smsw, list_stmxcsr,		/* 45 - 48 */
	list_str, list_sysenter, list_sysexit, list_wrmsr,	/* 49 - 52 */
	list_xlat, list_monitor, list_mwait,			/* 53 - 55*/
	NULL						/* end of list */
 };

#define LAST_IMPL_IDX 55

static void handle_impl_reg( x86_op_t *op, uint32_t val ) {
	x86_reg_t *reg = &op->data.reg;
	op->type = op_register;
	ia32_handle_register( reg, (unsigned int) val );
	switch (reg->size) {
		case 1:
			op->datatype = op_byte; break;
		case 2:
			op->datatype = op_word; break;
		case 4:
			op->datatype = op_dword; break;
		case 8:
			op->datatype = op_qword; break;
		case 10:
			op->datatype = op_extreal; break;
		case 16:
			op->datatype = op_dqword; break;
	}
	return;
}

/* 'impl_idx' is the value from the opcode table: between 1 and LAST_IMPL_IDX */
/* returns number of operands added */
unsigned int ia32_insn_implicit_ops( x86_insn_t *insn, unsigned int impl_idx ) {
	op_implicit_list_t *list;
	x86_op_t *op;
	unsigned int num = 0;

	if (! impl_idx || impl_idx > LAST_IMPL_IDX ) {
		return 0;
	}

	for ( list = op_implicit_list[impl_idx]; list->type; list++, num++ ) {
		enum x86_op_access access = (enum x86_op_access) OP_PERM(list->type);
		enum x86_op_flags  flags  = (enum x86_op_flags) (OP_FLAGS(list->type) >> 12);

		op = NULL;
		/* In some cases (MUL), EAX is an implicit operand hardcoded in
                 * the instruction without being explicitly listed in assembly.
                 * For this situation, find the hardcoded operand and add the
                 * implied flag rather than adding a new implicit operand. */
		x86_oplist_t * existing;
		if (ia32_true_register_id(list->operand) == REG_DWORD_OFFSET) {
			for ( existing = insn->operands; existing; existing = existing->next ) {
				if (existing->op.type == op_register &&
	                            existing->op.data.reg.id == list->operand) {
					op = &existing->op;
					break;
				}
			}
		}
		if (!op) {
			op = x86_operand_new( insn );
			/* all implicit operands are registers */
			handle_impl_reg( op, list->operand );
			/* decrement the 'explicit count' incremented by default in
			 * x86_operand_new */
			insn->explicit_count = insn->explicit_count -1;
		}
		if (!op) {
			return num;	/* gah! return early */
		}
		op->access |= access;
		op->flags |= flags;
		op->flags |= op_implied;
	}
	
	return num;
}
