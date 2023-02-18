#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "libdis.h"
#include "ia32_insn.h"
#include "ia32_operand.h"
#include "ia32_modrm.h"
#include "ia32_reg.h"
#include "x86_imm.h"
#include "x86_operand_list.h"



/* apply segment override to memory operand in insn */
static void apply_seg( x86_op_t *op, unsigned int prefixes ) {
	if (! prefixes ) return;

	/* apply overrides from prefix */
	switch ( prefixes & PREFIX_REG_MASK ) {
		case PREFIX_CS:
			op->flags |= op_cs_seg; break;
		case PREFIX_SS:
			op->flags |= op_ss_seg; break;
		case PREFIX_DS:
			op->flags |= op_ds_seg; break;
		case PREFIX_ES:
			op->flags |= op_es_seg; break;
		case PREFIX_FS:
			op->flags |= op_fs_seg; break;
		case PREFIX_GS:
			op->flags |= op_gs_seg; break;
	}

	return;
}

static size_t decode_operand_value( unsigned char *buf, size_t buf_len,
			    x86_op_t *op, x86_insn_t *insn, 
			    unsigned int addr_meth, size_t op_size, 
			    unsigned int op_value, unsigned char modrm, 
			    size_t gen_regs ) {
	size_t size = 0;

	/* ++ Do Operand Addressing Method / Decode operand ++ */
	switch (addr_meth) {
		/* This sets the operand Size based on the Intel Opcode Map
		 * (Vol 2, Appendix A). Letter encodings are from section
		 * A.1.1, 'Codes for Addressing Method' */

		/* ---------------------- Addressing Method -------------- */
		/* Note that decoding mod ModR/M operand adjusts the size of
		 * the instruction, but decoding the reg operand does not. 
		 * This should not cause any problems, as every 'reg' operand 
		 * has an associated 'mod' operand. 
		 * Goddamn-Intel-Note:
		 *   Some Intel addressing methods [M, R] specify that modR/M
		 *   byte may only refer to a memory address/may only refer to
		 *   a register -- however Intel provides no clues on what to do
		 *   if, say, the modR/M for an M opcode decodes to a register
		 *   rather than a memory address ... returning 0 is out of the
		 *   question, as this would be an Immediate or a RelOffset, so
		 *   instead these modR/Ms are decoded with total disregard to 
		 *   the M, R constraints. */

		/* MODRM -- mod operand. sets size to at least 1! */
		case ADDRMETH_E:	/* ModR/M present, Gen reg or memory  */
			size = ia32_modrm_decode( buf, buf_len, op, insn, 
						  gen_regs );
			break;
		case ADDRMETH_M:	/* ModR/M only refers to memory */
			size = ia32_modrm_decode( buf, buf_len, op, insn, 
						  gen_regs );
			break;
		case ADDRMETH_Q:	/* ModR/M present, MMX or Memory */
			size = ia32_modrm_decode( buf, buf_len, op, insn, 
						  REG_MMX_OFFSET );
			break;
		case ADDRMETH_R:	/* ModR/M mod == gen reg */
			size = ia32_modrm_decode( buf, buf_len, op, insn, 
						  gen_regs );
			break;
		case ADDRMETH_W:	/* ModR/M present, mem or SIMD reg */
			size = ia32_modrm_decode( buf, buf_len, op, insn, 
						  REG_SIMD_OFFSET );
			break;

		/* MODRM -- reg operand. does not effect size! */
		case ADDRMETH_C:	/* ModR/M reg == control reg */
			ia32_reg_decode( modrm, op, REG_CTRL_OFFSET );
			break;
		case ADDRMETH_D:	/* ModR/M reg == debug reg */
			ia32_reg_decode( modrm, op, REG_DEBUG_OFFSET );
			break;
		case ADDRMETH_G:	/* ModR/M reg == gen-purpose reg */
			ia32_reg_decode( modrm, op, gen_regs );
			break;
		case ADDRMETH_P:	/* ModR/M reg == qword MMX reg */
			ia32_reg_decode( modrm, op, REG_MMX_OFFSET );
			break;
		case ADDRMETH_S:	/* ModR/M reg == segment reg */
			ia32_reg_decode( modrm, op, REG_SEG_OFFSET );
			break;
		case ADDRMETH_T:	/* ModR/M reg == test reg */
			ia32_reg_decode( modrm, op, REG_TEST_OFFSET );
			break;
		case ADDRMETH_V:	/* ModR/M reg == SIMD reg */
			ia32_reg_decode( modrm, op, REG_SIMD_OFFSET );
			break;

		/* No MODRM : note these set operand type explicitly */
		case ADDRMETH_A:	/* No modR/M -- direct addr */
			op->type = op_absolute;

			/* segment:offset address used in far calls */
			x86_imm_sized( buf, buf_len, 
				       &op->data.absolute.segment, 2 );
			if ( insn->addr_size == 4 ) {
				x86_imm_sized( buf, buf_len, 
				    &op->data.absolute.offset.off32, 4 );
				size = 6;
			} else {
				x86_imm_sized( buf, buf_len, 
				    &op->data.absolute.offset.off16, 2 );
				size = 4;
			}

			break;
		case ADDRMETH_I:	/* Immediate val */
			op->type = op_immediate;
			/* if it ever becomes legal to have imm as dest and
			 * there is a src ModR/M operand, we are screwed! */
			if ( op->flags & op_signed ) {
				x86_imm_signsized(buf, buf_len, &op->data.byte, 
						op_size);
			} else {
				x86_imm_sized(buf, buf_len, &op->data.byte, 
						op_size);
			}
			size = op_size;
			break;
		case ADDRMETH_J:	/* Rel offset to add to IP [jmp] */
			/* this fills op->data.near_offset or
			   op->data.far_offset depending on the size of
			   the operand */
			op->flags |= op_signed;
			if ( op_size == 1 ) {
				/* one-byte near offset */
				op->type = op_relative_near;
				x86_imm_signsized(buf, buf_len, 
						&op->data.relative_near, 1);
			} else {
				/* far offset...is this truly signed? */
				op->type = op_relative_far;
				x86_imm_signsized(buf, buf_len, 
					&op->data.relative_far, op_size );
			}
			size = op_size;
			break;
		case ADDRMETH_O:	/* No ModR/M; op is word/dword offset */
			/* NOTE: these are actually RVAs not offsets to seg!! */
			/* note bene: 'O' ADDR_METH uses addr_size  to
			   determine operand size */
			op->type = op_offset;
			op->flags |= op_pointer;
			x86_imm_sized( buf, buf_len, &op->data.offset, 
					insn->addr_size );

			size = insn->addr_size;
			break;

		/* Hard-coded: these are specified in the insn definition */
		case ADDRMETH_F:	/* EFLAGS register */
			op->type = op_register;
			op->flags |= op_hardcode;
			ia32_handle_register( &op->data.reg, REG_FLAGS_INDEX );
			break;
		case ADDRMETH_X:	/* Memory addressed by DS:SI [string] */
			op->type = op_expression;
			op->flags |= op_hardcode;
			op->flags |= op_ds_seg | op_pointer | op_string;
			ia32_handle_register( &op->data.expression.base, 
					     REG_DWORD_OFFSET + 6 );
			break;
		case ADDRMETH_Y:	/* Memory addressed by ES:DI [string] */
			op->type = op_expression;
			op->flags |= op_hardcode;
			op->flags |= op_es_seg | op_pointer | op_string;
			ia32_handle_register( &op->data.expression.base, 
					     REG_DWORD_OFFSET + 7 );
			break;
		case ADDRMETH_RR:	/* Gen Register hard-coded in opcode */
			op->type = op_register;
			op->flags |= op_hardcode;
			ia32_handle_register( &op->data.reg, 
						op_value + gen_regs );
			break;
		case ADDRMETH_RS:	/* Seg Register hard-coded in opcode */
			op->type = op_register;
			op->flags |= op_hardcode;
			ia32_handle_register( &op->data.reg, 
						op_value + REG_SEG_OFFSET );
			break;
		case ADDRMETH_RF:	/* FPU Register hard-coded in opcode */
			op->type = op_register;
			op->flags |= op_hardcode;
			ia32_handle_register( &op->data.reg, 
						op_value + REG_FPU_OFFSET );
			break;
		case ADDRMETH_RT:	/* TST Register hard-coded in opcode */
			op->type = op_register;
			op->flags |= op_hardcode;
			ia32_handle_register( &op->data.reg, 
						op_value + REG_TEST_OFFSET );
			break;
		case ADDRMETH_II:	/* Immediate hard-coded in opcode */
			op->type = op_immediate;
			op->data.dword = op_value;
			op->flags |= op_hardcode;
			break;

		case 0:	/* Operand is not used */
		default:
			/* ignore -- operand not used in this insn */
			op->type = op_unused;	/* this shouldn't happen! */
			break;
	}

	return size;
}

static size_t decode_operand_size( unsigned int op_type, x86_insn_t *insn, 
				   x86_op_t *op ){
	size_t size;

	/* ++ Do Operand Type ++ */
	switch (op_type) {
		/* This sets the operand Size based on the Intel Opcode Map
		 * (Vol 2, Appendix A). Letter encodings are from section
		 * A.1.2, 'Codes for Operand Type' */
		/* NOTE: in this routines, 'size' refers to the size
		 *       of the operand in the raw (encoded) instruction;
		 *       'datatype' stores the actual size and datatype
		 *       of the operand */

		/* ------------------------ Operand Type ----------------- */
		case OPTYPE_c:	/* byte or word [op size attr] */
			size = (insn->op_size == 4) ? 2 : 1;
			op->datatype = (size == 4) ? op_word : op_byte;
			break;
		case OPTYPE_a:	/* 2 word or 2 dword [op size attr] */
			/* pointer to a 16:16 or 32:32 BOUNDS operand */
			size = (insn->op_size == 4) ? 8 : 4;
			op->datatype = (size == 4) ? op_bounds32 : op_bounds16;
			break;
		case OPTYPE_v:	/* word or dword [op size attr] */
			size = (insn->op_size == 4) ? 4 : 2;
			op->datatype = (size == 4) ? op_dword : op_word;
			break;
		case OPTYPE_p:	/* 32/48-bit ptr [op size attr] */
			/* technically these flags are not accurate: the
			 * value s a 16:16 pointer or a 16:32 pointer, where
			 * the first '16' is a segment */
			size = (insn->addr_size == 4) ? 6 : 4;
			op->datatype = (size == 4) ? op_descr32 : op_descr16;
			break;
		case OPTYPE_b:	/* byte, ignore op-size */
			size = 1;
			op->datatype = op_byte;
			break;
		case OPTYPE_w:	/* word, ignore op-size */
			size = 2;
			op->datatype = op_word;
			break;
		case OPTYPE_d:	/* dword , ignore op-size */
			size = 4;
			op->datatype = op_dword;
			break;
		case OPTYPE_s:	/* 6-byte psuedo-descriptor */
			/* ptr to 6-byte value which is 32:16 in 32-bit
			 * mode, or 8:24:16 in 16-bit mode. The high byte
			 * is ignored in 16-bit mode. */
			size = 6;
			op->datatype = (insn->addr_size == 4) ? 
				op_pdescr32 : op_pdescr16;
			break;
		case OPTYPE_q:	/* qword, ignore op-size */
			size = 8;
			op->datatype = op_qword;
			break;
		case OPTYPE_dq:	/* d-qword, ignore op-size */
			size = 16;
			op->datatype = op_dqword;
			break;
		case OPTYPE_ps:	/* 128-bit FP data */
			size = 16;
			/* really this is 4 packed SP FP values */
			op->datatype = op_ssimd;
			break;
		case OPTYPE_pd:	/* 128-bit FP data */
			size = 16;
			/* really this is 2 packed DP FP values */
			op->datatype = op_dsimd;
			break;
		case OPTYPE_ss:	/* Scalar elem of 128-bit FP data */
			size = 16;
			/* this only looks at the low dword (4 bytes)
			 * of the xmmm register passed as a param. 
			 * This is a 16-byte register where only 4 bytes
			 * are used in the insn. Painful, ain't it? */
			op->datatype = op_sssimd;
			break;
		case OPTYPE_sd:	/* Scalar elem of 128-bit FP data */
			size = 16;
			/* this only looks at the low qword (8 bytes)
			 * of the xmmm register passed as a param. 
			 * This is a 16-byte register where only 8 bytes
			 * are used in the insn. Painful, again... */
			op->datatype = op_sdsimd;
			break;
		case OPTYPE_pi:	/* qword mmx register */
			size = 8;
			op->datatype = op_qword;
			break;
		case OPTYPE_si:	/* dword integer register */
			size = 4;
			op->datatype = op_dword;
			break;
		case OPTYPE_fs:	/* single-real */
			size = 4;
			op->datatype = op_sreal;
			break;
		case OPTYPE_fd:	/* double real */
			size = 8;
			op->datatype = op_dreal;
			break;
		case OPTYPE_fe:	/* extended real */
			size = 10;
			op->datatype = op_extreal;
			break;
		case OPTYPE_fb:	/* packed BCD */
			size = 10;
			op->datatype = op_bcd;
			break;
		case OPTYPE_fv:	/* pointer to FPU env: 14 or 28-bytes */
			size = (insn->addr_size == 4)? 28 : 14;
			op->datatype = (size == 28)?  op_fpuenv32: op_fpuenv16;
			break;
		case OPTYPE_ft:	/* pointer to FPU env: 94 or 108 bytes */
			size = (insn->addr_size == 4)? 108 : 94;
			op->datatype = (size == 108)? 
				op_fpustate32: op_fpustate16;
			break;
		case OPTYPE_fx:	/* 512-byte register stack */
			size = 512;
			op->datatype = op_fpregset;
			break;
		case OPTYPE_fp:	/* floating point register */
			size = 10;	/* double extended precision */
			op->datatype = op_fpreg;
			break;
		case OPTYPE_m:	/* fake operand type used for "lea Gv, M" */
			size = insn->addr_size;
			op->datatype = (size == 4) ?  op_dword : op_word;
			break;
		case OPTYPE_none: /* handle weird instructions that have no encoding but use a dword datatype, like invlpg */
			size = 0;
			op->datatype = op_none;
			break;
		case 0:
		default:
			size = insn->op_size;
			op->datatype = (size == 4) ? op_dword : op_word;
			break;
		}
	return size;
}

size_t ia32_decode_operand( unsigned char *buf, size_t buf_len,
			      x86_insn_t *insn, unsigned int raw_op, 
			      unsigned int raw_flags, unsigned int prefixes, 
			      unsigned char modrm ) {
	unsigned int addr_meth, op_type, op_size, gen_regs;
	x86_op_t *op;
	size_t size;

	/* ++ Yank optype and addr mode out of operand flags */
	addr_meth = raw_flags & ADDRMETH_MASK;
	op_type = raw_flags & OPTYPE_MASK;

	if ( raw_flags == ARG_NONE ) {
		/* operand is not used in this instruction */
		return 0;
	}

	/* allocate a new operand */
	op = x86_operand_new( insn );

	/* ++ Copy flags from opcode table to x86_insn_t */
	op->access = (enum x86_op_access) OP_PERM(raw_flags);
	op->flags = (enum x86_op_flags) (OP_FLAGS(raw_flags) >> 12);

	/* Get size (for decoding)  and datatype of operand */
	op_size = decode_operand_size(op_type, insn, op);

	/* override default register set based on Operand Type */
	/* this allows mixing of 8, 16, and 32 bit regs in insn */
	if (op_size == 1) {
		gen_regs = REG_BYTE_OFFSET;
	} else if (op_size == 2) {
		gen_regs = REG_WORD_OFFSET;
	} else {
		gen_regs = REG_DWORD_OFFSET;
	}

	size = decode_operand_value( buf, buf_len, op, insn, addr_meth, 
				      op_size, raw_op, modrm, gen_regs );

	/* if operand is an address, apply any segment override prefixes */
	if ( op->type == op_expression || op->type == op_offset ) {
		apply_seg(op, prefixes);
	}

	return size;		/* return number of bytes in instruction */
}
