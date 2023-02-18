#include "ia32_modrm.h"
#include "ia32_reg.h"
#include "x86_imm.h"

/* NOTE: when decoding ModR/M and SIB, we have to add 1 to all register
 * values obtained from decoding the ModR/M or SIB byte, since they
 * are encoded with eAX = 0 and the tables in ia32_reg.c use eAX = 1.
 * ADDENDUM: this is only the case when the register value is used
 * directly as an index into the register table, not when it is added to
 * a genregs offset. */

/* -------------------------------- ModR/M, SIB */
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


#if 0
int modrm_rm[] = {0,1,2,3,MODRM_RM_SIB,MODRM_MOD_DISP32,6,7};
int modrm_reg[] = {0, 1, 2, 3, 4, 5, 6, 7};
int modrm_mod[]  = {0, MODRM_MOD_DISP8, MODRM_MOD_DISP32, MODRM_MOD_NOEA};
int sib_scl[] = {0, 2, 4, 8};
int sib_idx[] = {0, 1, 2, 3, SIB_INDEX_NONE, 5, 6, 7 };
int sib_bas[] = {0, 1, 2, 3, 4, SIB_SCALE_NOBASE, 6, 7 };
#endif

/* this is needed to replace x86_imm_signsized() which does not sign-extend
 * to dest */
static unsigned int imm32_signsized( unsigned char *buf, size_t buf_len,
				     int32_t *dest, unsigned int size ) {
	if ( size > buf_len ) {
		return 0;
	}

	switch (size) {
		case 1:
			*dest = *((signed char *) buf);
			break;
		case 2:
			*dest = *((signed short *) buf);
			break;
		case 4:
		default:
			*dest = *((signed int *) buf);
			break;
	}

	return size;
}



static void byte_decode(unsigned char b, struct modRM_byte *modrm) {
	/* generic bitfield-packing routine */

	modrm->mod = b >> 6;	/* top 2 bits */
	modrm->reg = (b & 56) >> 3;	/* middle 3 bits */
	modrm->rm = b & 7;	/* bottom 3 bits */
}


static size_t sib_decode( unsigned char *buf, size_t buf_len, x86_ea_t *ea, 
			  unsigned int mod ) {
	/* set Address Expression fields (scale, index, base, disp) 
	 * according to the contents of the SIB byte.
	 *  b points to the SIB byte in the instruction-stream buffer; the
	 *    byte after b[0] is therefore the byte after the SIB
	 *  returns number of bytes 'used', including the SIB byte */
	size_t size = 1;		/* start at 1 for SIB byte */
	struct SIB_byte sib;

	if ( buf_len < 1 ) {
		return 0;
	}

	byte_decode( *buf, (struct modRM_byte *)(void*)&sib );  /* get bit-fields */

	if ( sib.base == SIB_BASE_EBP && ! mod ) {  /* if base == 101 (ebp) */
	    /* IF BASE == EBP, deal with exception */
		/* IF (ModR/M did not create a Disp */
		/* ... create a 32-bit Displacement */
		imm32_signsized( &buf[1], buf_len, &ea->disp, sizeof(int32_t));
		ea->disp_size = sizeof(int32_t);
		ea->disp_sign = (ea->disp < 0) ? 1 : 0;
		size += 4;	/* add sizeof disp to count */

	} else {
		/* ELSE BASE refers to a General Register */
		ia32_handle_register( &ea->base, sib.base + 1 );
	}

	/* set scale to 1, 2, 4, 8 */
	ea->scale = 1 << sib.scale;

	if (sib.index != SIB_INDEX_NONE) {
		/* IF INDEX is not 'ESP' (100) */
		ia32_handle_register( &ea->index, sib.index + 1 );
	}

	return (size);		/* return number of bytes processed */
}

static size_t modrm_decode16( unsigned char *buf, unsigned int buf_len,
			    x86_op_t *op, struct modRM_byte *modrm ) {
	/* 16-bit mode: hackish, but not as hackish as 32-bit mode ;) */
	size_t size = 1; /* # of bytes decoded [1 for modR/M byte] */
	x86_ea_t * ea = &op->data.expression;

	switch( modrm->rm ) {
		case MOD16_RM_BXSI:
			ia32_handle_register(&ea->base, REG_WORD_OFFSET + 3);
			ia32_handle_register(&ea->index, REG_WORD_OFFSET + 6);
			break;
		case MOD16_RM_BXDI:
			ia32_handle_register(&ea->base, REG_WORD_OFFSET + 3);
			ia32_handle_register(&ea->index, REG_WORD_OFFSET + 7);
		case MOD16_RM_BPSI:
			op->flags |= op_ss_seg;
			ia32_handle_register(&ea->base, REG_WORD_OFFSET + 5);
			ia32_handle_register(&ea->index, REG_WORD_OFFSET + 6);
			break;
		case MOD16_RM_BPDI:
			op->flags |= op_ss_seg;
			ia32_handle_register(&ea->base, REG_WORD_OFFSET + 5);
			ia32_handle_register(&ea->index, REG_WORD_OFFSET + 7);
			break;
		case MOD16_RM_SI:
			ia32_handle_register(&ea->base, REG_WORD_OFFSET + 6);
			break;
		case MOD16_RM_DI:
			ia32_handle_register(&ea->base, REG_WORD_OFFSET + 7);
			break;
		case MOD16_RM_BP:
			if ( modrm->mod != MOD16_MOD_NODISP ) {
				op->flags |= op_ss_seg;
				ia32_handle_register(&ea->base, 
						     REG_WORD_OFFSET + 5);
			}
			break;
		case MOD16_RM_BX:
			ia32_handle_register(&ea->base, REG_WORD_OFFSET + 3);
			break;
	}

	/* move to byte after ModR/M */
	++buf;
	--buf_len;

	if ( modrm->mod == MOD16_MOD_DISP8 ) {
		imm32_signsized( buf, buf_len, &ea->disp, sizeof(char) );
		ea->disp_sign = (ea->disp < 0) ? 1 : 0;
		ea->disp_size = sizeof(char);
		size += sizeof(char);
	} else if ( modrm->mod == MOD16_MOD_DISP16 ) {
		imm32_signsized( buf, buf_len, &ea->disp, sizeof(short) );
		ea->disp_sign = (ea->disp < 0) ? 1 : 0;
		ea->disp_size = sizeof(short);
		size += sizeof(short);
	} 

	return size;
}

/* TODO : Mark index modes
    Use addressing mode flags to imply arrays (index), structure (disp),
    two-dimensional arrays [disp + index], classes [ea reg], and so on.
*/
size_t ia32_modrm_decode( unsigned char *buf, unsigned int buf_len,
			    x86_op_t *op, x86_insn_t *insn, size_t gen_regs ) {
	/* create address expression and/or fill operand based on value of
	 * ModR/M byte. Calls sib_decode as appropriate.
	 *    flags specifies whether Reg or mod+R/M fields are being decoded
	 *  returns the number of bytes in the instruction, including modR/M */
	struct modRM_byte modrm;
	size_t size = 1;	/* # of bytes decoded [1 for modR/M byte] */
	x86_ea_t * ea;


	byte_decode(*buf, &modrm);	/* get bitfields */

	/* first, handle the case where the mod field is a register only */
	if ( modrm.mod == MODRM_MOD_NOEA ) {
		op->type = op_register;
		ia32_handle_register(&op->data.reg, modrm.rm + gen_regs);
                /* increase insn size by 1 for modrm byte */
 		return 1;
 	}
 
	/* then deal with cases where there is an effective address */
	ea = &op->data.expression;
	op->type = op_expression;
	op->flags |= op_pointer;

	if ( insn->addr_size == 2 ) {
		/* gah! 16 bit mode! */
		return modrm_decode16( buf, buf_len, op, &modrm);
	}

	/* move to byte after ModR/M */
	++buf;
	--buf_len;

	if (modrm.mod == MODRM_MOD_NODISP) {	/* if mod == 00 */

		/* IF MOD == No displacement, just Indirect Register */
		if (modrm.rm == MODRM_RM_NOREG) {	/* if r/m == 101 */
			/* IF RM == No Register, just Displacement */
			/* This is an Intel Moronic Exception TM */
			imm32_signsized( buf, buf_len, &ea->disp, 
					sizeof(int32_t) );
			ea->disp_size = sizeof(int32_t);
			ea->disp_sign = (ea->disp < 0) ? 1 : 0;
			size += 4;	/* add sizeof disp to count */

		} else if (modrm.rm == MODRM_RM_SIB) {	/* if r/m == 100 */
			/* ELSE IF an SIB byte is present */
			/* TODO: check for 0 retval */
			size += sib_decode( buf, buf_len, ea, modrm.mod);
			/* move to byte after SIB for displacement */
			++buf;
			--buf_len;
		} else {	/* modR/M specifies base register */
			/* ELSE RM encodes a general register */
			ia32_handle_register( &ea->base, modrm.rm + 1 );
		}
	} else { 					/* mod is 01 or 10 */
		if (modrm.rm == MODRM_RM_SIB) {	/* rm == 100 */
			/* IF base is an AddrExpr specified by an SIB byte */
			/* TODO: check for 0 retval */
			size += sib_decode( buf, buf_len, ea, modrm.mod);
			/* move to byte after SIB for displacement */
			++buf;
			--buf_len;
		} else {
			/* ELSE base is a general register */
			ia32_handle_register( &ea->base, modrm.rm + 1 );
		}

		/* ELSE mod + r/m specify a disp##[base] or disp##(SIB) */
		if (modrm.mod == MODRM_MOD_DISP8) {		/* mod == 01 */
			/* If this is an 8-bit displacement */
			imm32_signsized( buf, buf_len, &ea->disp, 
					sizeof(char));
			ea->disp_size = sizeof(char);
			ea->disp_sign = (ea->disp < 0) ? 1 : 0;
			size += 1;	/* add sizeof disp to count */

		} else {
			/* Displacement is dependent on address size */
			imm32_signsized( buf, buf_len, &ea->disp, 
					insn->addr_size);
			ea->disp_size = insn->addr_size;
			ea->disp_sign = (ea->disp < 0) ? 1 : 0;
			size += 4;
		}
	}

	return size;		/* number of bytes found in instruction */
}

void ia32_reg_decode( unsigned char byte, x86_op_t *op, size_t gen_regs ) {
	struct modRM_byte modrm;
	byte_decode( byte, &modrm );	/* get bitfields */

 	/* set operand to register ID */
	op->type = op_register;
	ia32_handle_register(&op->data.reg, modrm.reg + gen_regs);

	return;
}
