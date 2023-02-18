#include <stdlib.h>
#include <string.h>

#include "ia32_invariant.h"
#include "ia32_insn.h"
#include "ia32_settings.h"

extern ia32_table_desc_t *ia32_tables;
extern ia32_settings_t ia32_settings;

extern size_t ia32_table_lookup( unsigned char *buf, size_t buf_len,
		unsigned int table, ia32_insn_t **raw_insn,
		 unsigned int *prefixes );


/* -------------------------------- ModR/M, SIB */
/* Convenience flags */
#define MODRM_EA  1                     /* ModR/M is an effective addr */
#define MODRM_reg 2                     /* ModR/M is a register */

/* ModR/M flags */
#define MODRM_RM_SIB            0x04    /* R/M == 100 */
#define MODRM_RM_NOREG          0x05    /* R/B == 101 */
/* if (MODRM.MOD_NODISP && MODRM.RM_NOREG) then just disp32 */
#define MODRM_MOD_NODISP        0x00    /* mod == 00 */
#define MODRM_MOD_DISP8         0x01    /* mod == 01 */
#define MODRM_MOD_DISP32        0x02    /* mod == 10 */
#define MODRM_MOD_NOEA          0x03    /* mod == 11 */
/* 16-bit modrm flags */
#define MOD16_MOD_NODISP      0
#define MOD16_MOD_DISP8       1
#define MOD16_MOD_DISP16      2
#define MOD16_MOD_REG         3

#define MOD16_RM_BXSI         0
#define MOD16_RM_BXDI         1
#define MOD16_RM_BPSI         2
#define MOD16_RM_BPDI         3
#define MOD16_RM_SI           4
#define MOD16_RM_DI           5
#define MOD16_RM_BP           6
#define MOD16_RM_BX           7

/* SIB flags */
#define SIB_INDEX_NONE       0x04
#define SIB_BASE_EBP       0x05
#define SIB_SCALE_NOBASE    0x00

/* Convenience struct for modR/M bitfield */
struct modRM_byte {  
   unsigned int mod : 2;
   unsigned int reg : 3;
   unsigned int rm  : 3; 
};

/* Convenience struct for SIB bitfield */
struct SIB_byte {
   unsigned int scale : 2;
   unsigned int index : 3;
   unsigned int base  : 3;
};

#ifdef WIN32
static void byte_decode(unsigned char b, struct modRM_byte *modrm) {
#else
static inline void byte_decode(unsigned char b, struct modRM_byte *modrm) {
#endif
	/* generic bitfield-packing routine */

	modrm->mod = b >> 6;	/* top 2 bits */
	modrm->reg = (b & 56) >> 3;	/* middle 3 bits */
	modrm->rm = b & 7;	/* bottom 3 bits */
}
static int ia32_invariant_modrm( unsigned char *in, unsigned char *out,
				 unsigned int mode_16, x86_invariant_op_t *op) {
	struct modRM_byte modrm;
	struct SIB_byte sib;
	unsigned char *c, *cin;
	unsigned short *s;
	unsigned int *i;
	int size = 0;	/* modrm byte is already counted */


	byte_decode(*in, &modrm);	/* get bitfields */

	out[0] = in[0];	/* save modrm byte */
	cin = &in[1];
	c = &out[1];
	s = (unsigned short *)&out[1];
	i = (unsigned int *)&out[1];

	op->type = op_expression;
	op->flags |= op_pointer;
	if ( ! mode_16 && modrm.rm == MODRM_RM_SIB && 
			      modrm.mod != MODRM_MOD_NOEA ) {
		size ++;
		byte_decode(*cin, (struct modRM_byte *)(void*)&sib);

		out[1] = in[1];	/* save sib byte */
		cin = &in[2];
		c = &out[2];
		s = (unsigned short *)&out[2];
		i = (unsigned int *)&out[2];

		if ( sib.base == SIB_BASE_EBP && ! modrm.mod ) {
			/* disp 32 is variant! */
			memset( i, X86_WILDCARD_BYTE, 4 );
			size += 4;
		}
	}

	if (! modrm.mod && modrm.rm == 101) {
		if ( mode_16 ) {	/* straight RVA in disp */
			memset( s, X86_WILDCARD_BYTE, 2 );
			size += 2;
		} else {
			memset( i, X86_WILDCARD_BYTE, 2 );
			size += 4;
		}
	} else if (modrm.mod && modrm.mod < 3) {
		if (modrm.mod == MODRM_MOD_DISP8) {	 /* offset in disp */
			*c = *cin;	
			size += 1;
		} else if ( mode_16 ) {
			*s = (* ((unsigned short *) cin));
			size += 2;
		} else {
			*i = (*((unsigned int *) cin));
			size += 4;
		}
	} else if ( modrm.mod == 3 ) {
		op->type = op_register;
		op->flags &= ~op_pointer;
	}

	return (size);
}


static int ia32_decode_invariant( unsigned char *buf, size_t buf_len, 
				ia32_insn_t *t, unsigned char *out, 
				unsigned int prefixes, x86_invariant_t *inv) {

	unsigned int addr_size, op_size, mode_16;
	unsigned int op_flags[3] = { t->dest_flag, t->src_flag, t->aux_flag };
	int x, type, bytes = 0, size = 0, modrm = 0;

	/* set addressing mode */
	if (ia32_settings.options & opt_16_bit) {
		op_size = ( prefixes & PREFIX_OP_SIZE ) ? 4 : 2;
		addr_size = ( prefixes & PREFIX_ADDR_SIZE ) ? 4 : 2;
		mode_16 = ( prefixes & PREFIX_ADDR_SIZE ) ? 0 : 1;
	} else {
		op_size = ( prefixes & PREFIX_OP_SIZE ) ? 2 : 4;
		addr_size = ( prefixes & PREFIX_ADDR_SIZE ) ? 2 : 4;
		mode_16 = ( prefixes & PREFIX_ADDR_SIZE ) ? 1 : 0;
	}

	for (x = 0; x < 3; x++) {
		inv->operands[x].access = (enum x86_op_access) 
						OP_PERM(op_flags[x]);
		inv->operands[x].flags = (enum x86_op_flags) 
						(OP_FLAGS(op_flags[x]) >> 12);

		switch (op_flags[x] & OPTYPE_MASK) {
			case OPTYPE_c:
				size = (op_size == 4) ? 2 : 1;
				break;
			case OPTYPE_a: case OPTYPE_v:
				size = (op_size == 4) ? 4 : 2;
				break;
			case OPTYPE_p:
				size = (op_size == 4) ? 6 : 4;
				break;
			case OPTYPE_b:
				size = 1;
				break;
			case OPTYPE_w:
				size = 2;
				break;
			case OPTYPE_d: case OPTYPE_fs: case OPTYPE_fd:
			case OPTYPE_fe: case OPTYPE_fb: case OPTYPE_fv:
			case OPTYPE_si: case OPTYPE_fx:
				size = 4;
				break;
			case OPTYPE_s:
				size = 6;
				break;
			case OPTYPE_q: case OPTYPE_pi:
				size = 8;
				break;
			case OPTYPE_dq: case OPTYPE_ps: case OPTYPE_ss:
			case OPTYPE_pd: case OPTYPE_sd:
				size = 16;
				break;
			case OPTYPE_m:	
				size = (addr_size == 4) ? 4 : 2;
				break;
			default:
				break;
		}

		type = op_flags[x] & ADDRMETH_MASK;
		switch (type) {
			case ADDRMETH_E: case ADDRMETH_M: case ADDRMETH_Q:
			case ADDRMETH_R: case ADDRMETH_W:
				modrm = 1;	
				bytes += ia32_invariant_modrm( buf, out, 
						mode_16, &inv->operands[x]);
				break;
			case ADDRMETH_C: case ADDRMETH_D: case ADDRMETH_G:
			case ADDRMETH_P: case ADDRMETH_S: case ADDRMETH_T:
			case ADDRMETH_V:
				inv->operands[x].type = op_register;
				modrm = 1;
				break;
			case ADDRMETH_A: case ADDRMETH_O:
				/* pad with xF4's */
				memset( &out[bytes + modrm], X86_WILDCARD_BYTE, 
					size );
				bytes += size;
				inv->operands[x].type = op_offset;
				if ( type == ADDRMETH_O ) {
					inv->operands[x].flags |= op_signed |
								  op_pointer;
				}
				break;
			case ADDRMETH_I: case ADDRMETH_J:
				/* grab imm value */
				if ((op_flags[x] & OPTYPE_MASK) == OPTYPE_v) {
					/* assume this is an address */
					memset( &out[bytes + modrm], 
						X86_WILDCARD_BYTE, size );
				} else {
					memcpy( &out[bytes + modrm], 
						&buf[bytes + modrm], size );
				}
					
				bytes += size;
				if ( type == ADDRMETH_J ) {
					if ( size == 1 ) {
						inv->operands[x].type = 
							op_relative_near;
					} else {
						inv->operands[x].type = 
							op_relative_far;
					}
					inv->operands[x].flags |= op_signed;
				} else {
					inv->operands[x].type = op_immediate;
				}
				break;
			case ADDRMETH_F:
				inv->operands[x].type = op_register;
				break;
			case ADDRMETH_X:
				inv->operands[x].flags |= op_signed |
					  op_pointer | op_ds_seg | op_string;
				break;
			case ADDRMETH_Y:
				inv->operands[x].flags |= op_signed |
					  op_pointer | op_es_seg | op_string;
				break;
			case ADDRMETH_RR:	
				inv->operands[x].type = op_register;
				break;
			case ADDRMETH_II:	
				inv->operands[x].type = op_immediate;
				break;
			default:
				inv->operands[x].type = op_unused;
				break;
		}
	}

	return (bytes + modrm);
}

size_t ia32_disasm_invariant( unsigned char * buf, size_t buf_len, 
		x86_invariant_t *inv ) {
	ia32_insn_t *raw_insn = NULL;
	unsigned int prefixes;
	unsigned int type;
	size_t size;
	
	/* Perform recursive table lookup starting with main table (0) */
	size = ia32_table_lookup( buf, buf_len, 0, &raw_insn, &prefixes );
	if ( size == INVALID_INSN || size > buf_len ) {
		/* TODO: set errno */
		return 0;
	}

	/* copy opcode bytes to buffer */
	memcpy( inv->bytes, buf, size );

	/* set mnemonic type and group */
	type = raw_insn->mnem_flag & ~INS_FLAG_MASK;
        inv->group = (enum x86_insn_group) (INS_GROUP(type)) >> 12;
        inv->type = (enum x86_insn_type) INS_TYPE(type);

	/* handle operands */
	size += ia32_decode_invariant( buf + size, buf_len - size, raw_insn, 
					&buf[size - 1], prefixes, inv );

	inv->size = size;

	return size;		/* return size of instruction in bytes */
}

size_t ia32_disasm_size( unsigned char *buf, size_t buf_len ) {
	x86_invariant_t inv = { {0} };
	return( ia32_disasm_invariant( buf, buf_len, &inv ) );
}
