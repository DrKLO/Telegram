#include <stdio.h>
#include <stdlib.h>

#include "libdis.h"

#ifdef _MSC_VER
        #define snprintf        _snprintf
        #define inline          __inline
#endif

int x86_insn_is_valid( x86_insn_t *insn ) {
	if ( insn && insn->type != insn_invalid && insn->size > 0 ) {
		return 1;
	}

	return 0;
}

uint32_t x86_get_address( x86_insn_t *insn ) {
	x86_oplist_t *op_lst;
        if (! insn || ! insn->operands ) {
        	return 0;
        }

	for (op_lst = insn->operands; op_lst; op_lst = op_lst->next ) {
		if ( op_lst->op.type == op_offset ) {
			return op_lst->op.data.offset;
		} else if ( op_lst->op.type == op_absolute ) {
			if ( op_lst->op.datatype == op_descr16 ) {
				return (uint32_t)
					op_lst->op.data.absolute.offset.off16;
			}
			return op_lst->op.data.absolute.offset.off32;
		}
	}
	
	return 0;
}

int32_t x86_get_rel_offset( x86_insn_t *insn ) {
	x86_oplist_t *op_lst;
        if (! insn || ! insn->operands ) {
        	return 0;
        }

	for (op_lst = insn->operands; op_lst; op_lst = op_lst->next ) {
		if ( op_lst->op.type == op_relative_near ) {
			return (int32_t) op_lst->op.data.relative_near;
		} else if ( op_lst->op.type == op_relative_far ) {
			return op_lst->op.data.relative_far;
		}
	}
	
	return 0;
}

x86_op_t * x86_get_branch_target( x86_insn_t *insn ) {
	x86_oplist_t *op_lst;
        if (! insn || ! insn->operands ) {
        	return NULL;
        }

	for (op_lst = insn->operands; op_lst; op_lst = op_lst->next ) {
		if ( op_lst->op.access & op_execute ) {
			return &(op_lst->op);
		}
	}
	
	return NULL;
}
x86_op_t * x86_get_imm( x86_insn_t *insn ) {
	x86_oplist_t *op_lst;
        if (! insn || ! insn->operands ) {
        	return NULL;
        }

	for (op_lst = insn->operands; op_lst; op_lst = op_lst->next ) {
		if ( op_lst->op.type == op_immediate ) {
			return &(op_lst->op);
		}
	}
	
	return NULL;
}

#define IS_PROPER_IMM( x ) \
	x->op.type == op_immediate && ! (x->op.flags & op_hardcode)
							   

/* if there is an immediate value in the instruction, return a pointer to
 * it */
unsigned char * x86_get_raw_imm( x86_insn_t *insn ) {
        int size, offset;
        x86_op_t *op  = NULL;

        if (! insn || ! insn->operands ) {
        	return(NULL);
        }

	/* a bit inelegant, but oh well... */
	if ( IS_PROPER_IMM( insn->operands ) ) {
		op = &insn->operands->op;
	} else if ( insn->operands->next ) {
		if ( IS_PROPER_IMM( insn->operands->next ) ) {
			op = &insn->operands->next->op;
		} else if ( insn->operands->next->next && 
			    IS_PROPER_IMM( insn->operands->next->next ) ) {
			op = &insn->operands->next->next->op;
		}
	}
	
	if (! op ) {
		return( NULL );
	}

	/* immediate data is at the end of the insn */
	size = x86_operand_size( op );
	offset = insn->size - size;
	return( &insn->bytes[offset] );
}


unsigned int x86_operand_size( x86_op_t *op ) {
        switch (op->datatype ) {
                case op_byte:    return 1;
                case op_word:    return 2;
                case op_dword:   return 4;
                case op_qword:   return 8;
                case op_dqword:  return 16;
                case op_sreal:   return 4;
                case op_dreal:   return 8;
                case op_extreal: return 10;
                case op_bcd:     return 10;
                case op_ssimd:   return 16;
                case op_dsimd:   return 16;
                case op_sssimd:  return 4;
                case op_sdsimd:  return 8;
                case op_descr32: return 6;
                case op_descr16: return 4;
                case op_pdescr32: return 6;
                case op_pdescr16: return 6;
		case op_bounds16: return 4;
		case op_bounds32: return 8;
                case op_fpuenv16:  return 14;
                case op_fpuenv32:  return 28;
                case op_fpustate16:  return 94;
                case op_fpustate32:  return 108;
                case op_fpregset: return 512;
		case op_fpreg: return 10;
		case op_none: return 0;
        }
        return(4);      /* default size */
}

void x86_set_insn_addr( x86_insn_t *insn, uint32_t addr ) {
        if ( insn ) insn->addr = addr;
}

void x86_set_insn_offset( x86_insn_t *insn, unsigned int offset ){
        if ( insn ) insn->offset = offset;
}

void x86_set_insn_function( x86_insn_t *insn, void * func ){
        if ( insn ) insn->function = func;
}

void x86_set_insn_block( x86_insn_t *insn, void * block ){
        if ( insn ) insn->block = block;
}

void x86_tag_insn( x86_insn_t *insn ){
        if ( insn ) insn->tag = 1;
}

void x86_untag_insn( x86_insn_t *insn ){
        if ( insn ) insn->tag = 0;
}

int x86_insn_is_tagged( x86_insn_t *insn ){
        return insn->tag;
}

