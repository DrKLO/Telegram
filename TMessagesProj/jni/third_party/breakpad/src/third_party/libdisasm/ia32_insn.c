#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "qword.h"

#include "ia32_insn.h"
#include "ia32_opcode_tables.h"

#include "ia32_reg.h"
#include "ia32_operand.h"
#include "ia32_implicit.h"
#include "ia32_settings.h"

#include "libdis.h"

extern ia32_table_desc_t ia32_tables[];
extern ia32_settings_t ia32_settings;

#define IS_SP( op )  (op->type == op_register && 	\
		(op->data.reg.id == REG_ESP_INDEX || 	\
		 op->data.reg.alias == REG_ESP_INDEX) )
#define IS_IMM( op ) (op->type == op_immediate )

#ifdef WIN32
#  define INLINE 
#else
#  define INLINE inline
#endif

/* for calculating stack modification based on an operand */
static INLINE int32_t long_from_operand( x86_op_t *op ) {

	if (! IS_IMM(op) ) {
		return 0L;
	}

	switch ( op->datatype ) {
		case op_byte:
			return (int32_t) op->data.sbyte;
		case op_word:
			return (int32_t) op->data.sword;
		case op_qword:
			return (int32_t) op->data.sqword;
		case op_dword:
			return op->data.sdword;
		default:
			/* these are not used in stack insn */
			break;
	}

	return 0L;
}
		

/* determine what this insn does to the stack */
static void ia32_stack_mod(x86_insn_t *insn) {
	x86_op_t *dest, *src = NULL;

	if (! insn || ! insn->operands ) {
		return;
	}
       
	dest = &insn->operands->op;
	if ( dest ) {
		src = &insn->operands->next->op;
	}

	insn->stack_mod = 0; 
	insn->stack_mod_val = 0;

	switch ( insn->type ) {
		case insn_call:
		case insn_callcc:
			insn->stack_mod = 1;
			insn->stack_mod_val = insn->addr_size * -1;
			break;
		case insn_push:
			insn->stack_mod = 1;
			insn->stack_mod_val = insn->addr_size * -1;
			break;
		case insn_return:
			insn->stack_mod = 1;
			insn->stack_mod_val = insn->addr_size;
		case insn_int: case insn_intcc:
		case insn_iret:
			break;
		case insn_pop:
			insn->stack_mod = 1;
			if (! IS_SP( dest ) ) {
				insn->stack_mod_val = insn->op_size;
			} /* else we don't know the stack change in a pop esp */
			break;
		case insn_enter:
			insn->stack_mod = 1;
			insn->stack_mod_val = 0; /* TODO : FIX */
			break;
		case insn_leave:
			insn->stack_mod = 1;
			insn->stack_mod_val = 0; /* TODO : FIX */
			break;
		case insn_pushregs:
			insn->stack_mod = 1;
			insn->stack_mod_val = 0; /* TODO : FIX */
			break;
		case insn_popregs:
			insn->stack_mod = 1;
			insn->stack_mod_val = 0; /* TODO : FIX */
			break;
		case insn_pushflags:
			insn->stack_mod = 1;
			insn->stack_mod_val = 0; /* TODO : FIX */
			break;
		case insn_popflags:
			insn->stack_mod = 1;
			insn->stack_mod_val = 0; /* TODO : FIX */
			break;
		case insn_add:
			if ( IS_SP( dest ) ) {
				insn->stack_mod = 1;
				insn->stack_mod_val = long_from_operand( src ); 
			}
			break;
		case insn_sub:
			if ( IS_SP( dest ) ) {
				insn->stack_mod = 1;
				insn->stack_mod_val = long_from_operand( src ); 
				insn->stack_mod_val *= -1;
			}
			break;
		case insn_inc:
			if ( IS_SP( dest ) ) {
				insn->stack_mod = 1;
				insn->stack_mod_val = 1;
			}
			break;
		case insn_dec:
			if ( IS_SP( dest ) ) {
				insn->stack_mod = 1;
				insn->stack_mod_val = 1;
			}
			break;
		case insn_mov: case insn_movcc:
		case insn_xchg: case insn_xchgcc:
		case insn_mul: case insn_div:
		case insn_shl: case insn_shr:
		case insn_rol: case insn_ror:
		case insn_and: case insn_or:
		case insn_not: case insn_neg:
		case insn_xor:
			if ( IS_SP( dest ) ) {
				insn->stack_mod = 1;
			}
			break;
		default:
			break;
	}
	if (! strcmp("enter", insn->mnemonic) ) {
		insn->stack_mod = 1;
	} else if (! strcmp("leave", insn->mnemonic) ) {
		insn->stack_mod = 1;
	}

	/* for mov, etc we return 0 -- unknown stack mod */

	return;
}

/* get the cpu details for this insn from cpu flags int */
static void ia32_handle_cpu( x86_insn_t *insn, unsigned int cpu ) {
	insn->cpu = (enum x86_insn_cpu) CPU_MODEL(cpu);
	insn->isa = (enum x86_insn_isa) (ISA_SUBSET(cpu)) >> 16;
	return;
}

/* handle mnemonic type and group */
static void ia32_handle_mnemtype(x86_insn_t *insn, unsigned int mnemtype) {
	unsigned int type = mnemtype & ~INS_FLAG_MASK;
        insn->group = (enum x86_insn_group) (INS_GROUP(type)) >> 12;
        insn->type = (enum x86_insn_type) INS_TYPE(type);

	return;
}

static void ia32_handle_notes(x86_insn_t *insn, unsigned int notes) {
	insn->note = (enum x86_insn_note) notes;
	return;
}

static void ia32_handle_eflags( x86_insn_t *insn, unsigned int eflags) {
        unsigned int flags;

        /* handle flags effected */
        flags = INS_FLAGS_TEST(eflags);
        /* handle weird OR cases */
        /* these are either JLE (ZF | SF<>OF) or JBE (CF | ZF) */
        if (flags & INS_TEST_OR) {
                flags &= ~INS_TEST_OR;
                if ( flags & INS_TEST_ZERO ) {
                        flags &= ~INS_TEST_ZERO;
                        if ( flags & INS_TEST_CARRY ) {
                                flags &= ~INS_TEST_CARRY ;
                                flags |= (int)insn_carry_or_zero_set;
                        } else if ( flags & INS_TEST_SFNEOF ) {
                                flags &= ~INS_TEST_SFNEOF;
                                flags |= (int)insn_zero_set_or_sign_ne_oflow;
                        }
                }
        }
        insn->flags_tested = (enum x86_flag_status) flags;

        insn->flags_set = (enum x86_flag_status) INS_FLAGS_SET(eflags) >> 16;

	return;
}

static void ia32_handle_prefix( x86_insn_t *insn, unsigned int prefixes ) {

        insn->prefix = (enum x86_insn_prefix) prefixes & PREFIX_MASK; // >> 20;
        if (! (insn->prefix & PREFIX_PRINT_MASK) ) {
		/* no printable prefixes */
                insn->prefix = insn_no_prefix;
        }

        /* concat all prefix strings */
        if ( (unsigned int)insn->prefix & PREFIX_LOCK ) {
                strncat(insn->prefix_string, "lock ", 32 - 
				strlen(insn->prefix_string));
        }

        if ( (unsigned int)insn->prefix & PREFIX_REPNZ ) {
                strncat(insn->prefix_string, "repnz ", 32  - 
				strlen(insn->prefix_string));
        } else if ( (unsigned int)insn->prefix & PREFIX_REPZ ) {
                strncat(insn->prefix_string, "repz ", 32 - 
				strlen(insn->prefix_string));
        }

        return;
}


static void reg_32_to_16( x86_op_t *op, x86_insn_t *insn, void *arg ) {

	/* if this is a 32-bit register and it is a general register ... */
	if ( op->type == op_register && op->data.reg.size == 4 && 
	     (op->data.reg.type & reg_gen) ) {
		/* WORD registers are 8 indices off from DWORD registers */
		ia32_handle_register( &(op->data.reg), 
				op->data.reg.id + 8 );
	}
}

static void handle_insn_metadata( x86_insn_t *insn, ia32_insn_t *raw_insn ) {
	ia32_handle_mnemtype( insn, raw_insn->mnem_flag );
	ia32_handle_notes( insn, raw_insn->notes );
	ia32_handle_eflags( insn, raw_insn->flags_effected );
	ia32_handle_cpu( insn, raw_insn->cpu );
	ia32_stack_mod( insn );
}

static size_t ia32_decode_insn( unsigned char *buf, size_t buf_len, 
			   ia32_insn_t *raw_insn, x86_insn_t *insn,
			   unsigned int prefixes ) {
	size_t size, op_size;
	unsigned char modrm;

	/* this should never happen, but just in case... */
	if ( raw_insn->mnem_flag == INS_INVALID ) {
		return 0;
	}

	if (ia32_settings.options & opt_16_bit) {
		insn->op_size = ( prefixes & PREFIX_OP_SIZE ) ? 4 : 2;
		insn->addr_size = ( prefixes & PREFIX_ADDR_SIZE ) ? 4 : 2;
	} else {
		insn->op_size = ( prefixes & PREFIX_OP_SIZE ) ? 2 : 4;
		insn->addr_size = ( prefixes & PREFIX_ADDR_SIZE ) ? 2 : 4;
	}


	/*  ++++   1. Copy mnemonic and mnemonic-flags to CODE struct */
	if ((ia32_settings.options & opt_att_mnemonics) && raw_insn->mnemonic_att[0]) {
		strncpy( insn->mnemonic, raw_insn->mnemonic_att, 16 );
	}
	else {
		strncpy( insn->mnemonic, raw_insn->mnemonic, 16 );
	}
	ia32_handle_prefix( insn, prefixes );

	handle_insn_metadata( insn, raw_insn );

	/* prefetch the next byte in case it is a modr/m byte -- saves
	 * worrying about whether the 'mod/rm' operand or the 'reg' operand
	 * occurs first */
	modrm = GET_BYTE( buf, buf_len );

	/*  ++++   2. Decode Explicit Operands */
	/* Intel uses up to 3 explicit operands in its instructions;
	 * the first is 'dest', the second is 'src', and the third
	 * is an additional source value (usually an immediate value,
	 * e.g. in the MUL instructions). These three explicit operands
	 * are encoded in the opcode tables, even if they are not used 
	 * by the instruction. Additional implicit operands are stored
	 * in a supplemental table and are handled later. */

	op_size = ia32_decode_operand( buf, buf_len, insn, raw_insn->dest, 
					raw_insn->dest_flag, prefixes, modrm );
	/* advance buffer, increase size if necessary */
	buf += op_size;
	buf_len -= op_size;
	size = op_size;

	op_size = ia32_decode_operand( buf, buf_len, insn, raw_insn->src, 
					raw_insn->src_flag, prefixes, modrm );
	buf += op_size;
	buf_len -= op_size;
	size += op_size;

	op_size = ia32_decode_operand( buf, buf_len, insn, raw_insn->aux, 
					raw_insn->aux_flag, prefixes, modrm );
	size += op_size;


	/*  ++++   3. Decode Implicit Operands */
	/* apply implicit operands */
	ia32_insn_implicit_ops( insn, raw_insn->implicit_ops );
	/* we have one small inelegant hack here, to deal with 
	 * the two prefixes that have implicit operands. If Intel
	 * adds more, we'll change the algorithm to suit :) */
	if ( (prefixes & PREFIX_REPZ) || (prefixes & PREFIX_REPNZ) ) {
		ia32_insn_implicit_ops( insn, IDX_IMPLICIT_REP );
	}


	/* 16-bit hack: foreach operand, if 32-bit reg, make 16-bit reg */
	if ( insn->op_size == 2 ) {
		x86_operand_foreach( insn, reg_32_to_16, NULL, op_any );
	}

	return size;
}


/* convenience routine */
#define USES_MOD_RM(flag) \
	(flag == ADDRMETH_E || flag == ADDRMETH_M || flag == ADDRMETH_Q || \
	 flag == ADDRMETH_W || flag == ADDRMETH_R)

static int uses_modrm_flag( unsigned int flag ) {
	unsigned int meth;
	if ( flag == ARG_NONE ) {
		return 0;
	}
	meth = (flag & ADDRMETH_MASK);
	if ( USES_MOD_RM(meth) ) {
		return 1;
	}

	return 0;
}

/* This routine performs the actual byte-by-byte opcode table lookup.
 * Originally it was pretty simple: get a byte, adjust it to a proper
 * index into the table, then check the table row at that index to
 * determine what to do next. But is anything that simple with Intel?
 * This is now a huge, convoluted mess, mostly of bitter comments. */
/* buf: pointer to next byte to read from stream 
 * buf_len: length of buf
 * table: index of table to use for lookups
 * raw_insn: output pointer that receives opcode definition
 * prefixes: output integer that is encoded with prefixes in insn 
 * returns : number of bytes consumed from stream during lookup */ 
size_t ia32_table_lookup( unsigned char *buf, size_t buf_len,
				 unsigned int table, ia32_insn_t **raw_insn,
				 unsigned int *prefixes ) {
	unsigned char *next, op = buf[0];	/* byte value -- 'opcode' */
	size_t size = 1, sub_size = 0, next_len;
	ia32_table_desc_t *table_desc;
	unsigned int subtable, prefix = 0, recurse_table = 0;

	table_desc = &ia32_tables[table];

	op = GET_BYTE( buf, buf_len );

	if ( table_desc->type == tbl_fpu && op > table_desc->maxlim) {
		/* one of the fucking FPU tables out of the 00-BH range */
		/* OK,. this is a bit of a hack -- the proper way would
		 * have been to use subtables in the 00-BF FPU opcode tables,
		 * but that is rather wasteful of space... */
		table_desc = &ia32_tables[table +1];
	}

	/* PERFORM TABLE LOOKUP */

	/* ModR/M trick: shift extension bits into lowest bits of byte */
	/* Note: non-ModR/M tables have a shift value of 0 */
	op >>= table_desc->shift;

	/* ModR/M trick: mask out high bits to turn extension into an index */
	/* Note: non-ModR/M tables have a mask value of 0xFF */
	op &= table_desc->mask;


	/* Sparse table trick: check that byte is <= max value */
	/* Note: full (256-entry) tables have a maxlim of 155 */
	if ( op > table_desc->maxlim ) {
		/* this is a partial table, truncated at the tail,
		   and op is out of range! */
		return INVALID_INSN;
	}

	/* Sparse table trick: check that byte is >= min value */
	/* Note: full (256-entry) tables have a minlim of 0 */
	if ( table_desc->minlim > op ) {
		/* this is a partial table, truncated at the head,
		   and op is out of range! */
		return INVALID_INSN;
	}
	/* adjust op to be an offset from table index 0 */
	op -= table_desc->minlim;

	/* Yay! 'op' is now fully adjusted to be an index into 'table' */
	*raw_insn = &(table_desc->table[op]);
	//printf("BYTE %X TABLE %d OP %X\n", buf[0], table, op ); 

	if ( (*raw_insn)->mnem_flag & INS_FLAG_PREFIX ) {
		prefix = (*raw_insn)->mnem_flag & PREFIX_MASK;
	}


	/* handle escape to a multibyte/coproc/extension/etc table */
	/* NOTE: if insn is a prefix and has a subtable, then we
	 *       only recurse if this is the first prefix byte --
	 *       that is, if *prefixes is 0. 
	 * NOTE also that suffix tables are handled later */
	subtable = (*raw_insn)->table;

	if ( subtable && ia32_tables[subtable].type != tbl_suffix &&
	     (! prefix || ! *prefixes) ) {

	     	if ( ia32_tables[subtable].type == tbl_ext_ext ||
	     	     ia32_tables[subtable].type == tbl_fpu_ext ) {
			/* opcode extension: reuse current byte in buffer */
			next = buf;
			next_len = buf_len;
		} else {
			/* "normal" opcode: advance to next byte in buffer */
			if ( buf_len > 1 ) {
				next = &buf[1];
				next_len = buf_len - 1;
			}
			else {
				// buffer is truncated 
				return INVALID_INSN;
			}
		}
		/* we encountered a multibyte opcode: recurse using the
		 * table specified in the opcode definition */
		sub_size = ia32_table_lookup( next, next_len, subtable, 
				raw_insn, prefixes );

		/* SSE/prefix hack: if the original opcode def was a 
		 * prefix that specified a subtable, and the subtable
		 * lookup returned a valid insn, then we have encountered
		 * an SSE opcode definition; otherwise, we pretend we
		 * never did the subtable lookup, and deal with the 
		 * prefix normally later */
		if ( prefix && ( sub_size == INVALID_INSN  ||
		       INS_TYPE((*raw_insn)->mnem_flag) == INS_INVALID ) ) {
			/* this is a prefix, not an SSE insn :
			 * lookup next byte in main table,
			 * subsize will be reset during the
			 * main table lookup */
			recurse_table = 1;
		} else {
			/* this is either a subtable (two-byte) insn
			 * or an invalid insn: either way, set prefix
			 * to NULL and end the opcode lookup */
			prefix = 0;
			// short-circuit lookup on invalid insn
			if (sub_size == INVALID_INSN) return INVALID_INSN;
		}
	} else if ( prefix ) {
		recurse_table = 1;
	}

	/* by default, we assume that we have the opcode definition,
	 * and there is no need to recurse on the same table, but
	 * if we do then a prefix was encountered... */
	if ( recurse_table ) {
		/* this must have been a prefix: use the same table for
		 * lookup of the next byte */
		sub_size = ia32_table_lookup( &buf[1], buf_len - 1, table, 
				raw_insn, prefixes );

		// short-circuit lookup on invalid insn
		if (sub_size == INVALID_INSN) return INVALID_INSN;

		/* a bit of a hack for branch hints */
		if ( prefix & BRANCH_HINT_MASK ) {
			if ( INS_GROUP((*raw_insn)->mnem_flag) == INS_EXEC ) {
				/* segment override prefixes are invalid for
			 	* all branch instructions, so delete them */
				prefix &= ~PREFIX_REG_MASK;
			} else {
				prefix &= ~BRANCH_HINT_MASK;
			}
		}

		/* apply prefix to instruction */

		/* TODO: implement something enforcing prefix groups */
		(*prefixes) |= prefix;
	}

	/* if this lookup was in a ModR/M table, then an opcode byte is 
	 * NOT consumed: subtract accordingly. NOTE that if none of the
	 * operands used the ModR/M, then we need to consume the byte
	 * here, but ONLY in the 'top-level' opcode extension table */

	if ( table_desc->type == tbl_ext_ext ) {
		/* extensions-to-extensions never consume a byte */
		--size;
	} else if ( (table_desc->type == tbl_extension || 
	       	     table_desc->type == tbl_fpu ||
		     table_desc->type == tbl_fpu_ext ) && 
		/* extensions that have an operand encoded in ModR/M
		 * never consume a byte */
	      	    (uses_modrm_flag((*raw_insn)->dest_flag) || 
	             uses_modrm_flag((*raw_insn)->src_flag) )  	) {
		--size;
	}

	size += sub_size;

	return size;
}

static size_t handle_insn_suffix( unsigned char *buf, size_t buf_len,
			   ia32_insn_t *raw_insn, x86_insn_t * insn ) {
	ia32_insn_t *sfx_insn;
	size_t size;
	unsigned int prefixes = 0;

	size = ia32_table_lookup( buf, buf_len, raw_insn->table, &sfx_insn,
				 &prefixes );
	if (size == INVALID_INSN || sfx_insn->mnem_flag == INS_INVALID ) {
		return 0;
	}

	strncpy( insn->mnemonic, sfx_insn->mnemonic, 16 );
	handle_insn_metadata( insn, sfx_insn );

	return 1;
}

/* invalid instructions are handled by returning 0 [error] from the
 * function, setting the size of the insn to 1 byte, and copying
 * the byte at the start of the invalid insn into the x86_insn_t.
 * if the caller is saving the x86_insn_t for invalid instructions,
 * instead of discarding them, this will maintain a consistent
 * address space in the x86_insn_ts */

/* this function is called by the controlling disassembler, so its name and
 * calling convention cannot be changed */
/*    buf   points to the loc of the current opcode (start of the 
 *          instruction) in the instruction stream. The instruction 
 *          stream is assumed to be a buffer of bytes read directly 
 *          from the file for the purpose of disassembly; a mem-mapped 
 *          file is ideal for *        this.
 *    insn points to a code structure to be filled by instr_decode
 *    returns the size of the decoded instruction in bytes */
size_t ia32_disasm_addr( unsigned char * buf, size_t buf_len, 
		x86_insn_t *insn ) {
	ia32_insn_t *raw_insn = NULL;
	unsigned int prefixes = 0;
	size_t size, sfx_size;
	
	if ( (ia32_settings.options & opt_ignore_nulls) && buf_len > 3 &&
	    !buf[0] && !buf[1] && !buf[2] && !buf[3]) {
		/* IF IGNORE_NULLS is set AND
		 * first 4 bytes in the intruction stream are NULL
		 * THEN return 0 (END_OF_DISASSEMBLY) */
		/* TODO: set errno */
		MAKE_INVALID( insn, buf );
		return 0;	/* 4 00 bytes in a row? This isn't code! */
	}

	/* Perform recursive table lookup starting with main table (0) */
	size = ia32_table_lookup(buf, buf_len, idx_Main, &raw_insn, &prefixes);
	if ( size == INVALID_INSN || size > buf_len || raw_insn->mnem_flag == INS_INVALID ) {
		MAKE_INVALID( insn, buf );
		/* TODO: set errno */
		return 0;
	}

	/* We now have the opcode itself figured out: we can decode
	 * the rest of the instruction. */
	size += ia32_decode_insn( &buf[size], buf_len - size, raw_insn, insn, 
				  prefixes );
	if ( raw_insn->mnem_flag & INS_FLAG_SUFFIX ) {
		/* AMD 3DNow! suffix -- get proper operand type here */
		sfx_size = handle_insn_suffix( &buf[size], buf_len - size,
				raw_insn, insn );
		if (! sfx_size ) {
			/* TODO: set errno */
			MAKE_INVALID( insn, buf );
			return 0;
		}

		size += sfx_size;
	}

	if (! size ) {
		/* invalid insn */
		MAKE_INVALID( insn, buf );
		return 0;
	}


	insn->size = size;
	return size;		/* return size of instruction in bytes */
}
