#include <stdlib.h>
#include "libdis.h"


static void x86_oplist_append( x86_insn_t *insn, x86_oplist_t *op ) {
	x86_oplist_t *list;

	if (! insn ) {	
		return;
	}

	list = insn->operands;
	if (! list ) {
		insn->operand_count = 1;
		/* Note that we have no way of knowing if this is an
		 * exlicit operand or not, since the caller fills 
		 * the x86_op_t after we return. We increase the
		 * explicit count automatically, and ia32_insn_implicit_ops
		 * decrements it */
		insn->explicit_count = 1;
		insn->operands = op;
		return;
	}

	/* get to end of list */
	for ( ; list->next; list = list->next ) 
		;

	insn->operand_count = insn->operand_count + 1;
	insn->explicit_count = insn->explicit_count + 1;
	list->next = op;

	return;
}
	
x86_op_t * x86_operand_new( x86_insn_t *insn ) {
	x86_oplist_t *op;

	if (! insn ) {	
		return(NULL);
	}
	op = calloc( sizeof(x86_oplist_t), 1 );
	op->op.insn = insn;
	x86_oplist_append( insn, op );
	return( &(op->op) );
}

void x86_oplist_free( x86_insn_t *insn ) {
	x86_oplist_t *op, *list;

	if (! insn ) {
		return;
	}

	for ( list = insn->operands; list; ) {
		op = list;
		list = list->next;
		free(op);
	}

	insn->operands = NULL;
	insn->operand_count = 0;
	insn->explicit_count = 0;

	return;
}

/* ================================================== LIBDISASM API */
/* these could probably just be #defines, but that means exposing the
   enum... yet one more confusing thing in the API */
int x86_operand_foreach( x86_insn_t *insn, x86_operand_fn func, void *arg, 
	       		enum x86_op_foreach_type type ){
	x86_oplist_t *list;
	char explicit = 1, implicit = 1;

	if (! insn || ! func ) {
		return 0;
	}
	
	/* note: explicit and implicit can be ORed together to
	 * allow an "all" limited by access type, even though the
	 * user is stupid to do this since it is default behavior :) */
	if ( (type & op_explicit) && ! (type & op_implicit) ) {
		implicit = 0;
	}
	if ( (type & op_implicit) && ! (type & op_explicit) ) {
		explicit = 0;
	}

	type = type & 0x0F; /* mask out explicit/implicit operands */

	for ( list = insn->operands; list; list = list->next ) {
		if (! implicit && (list->op.flags & op_implied) ) {
			/* operand is implicit */
			continue;
		}

		if (! explicit && ! (list->op.flags & op_implied) ) {
			/* operand is not implicit */
			continue;
		}

		switch ( type ) {
			case op_any:
				break;
			case op_dest:
				if (! (list->op.access & op_write) ) {
					continue;
				}
				break;
			case op_src:
				if (! (list->op.access & op_read) ) {
					continue;
				}
				break;
			case op_ro:
				if (! (list->op.access & op_read) ||
				      (list->op.access & op_write ) ) {
					continue;
				}
				break;
			case op_wo:
				if (! (list->op.access & op_write) ||
				      (list->op.access & op_read ) ) {
					continue;
				}
				break;
			case op_xo:
				if (! (list->op.access & op_execute) ) {
					continue;
				}
				break;
			case op_rw:
				if (! (list->op.access & op_write) ||
				    ! (list->op.access & op_read ) ) {
					continue;
				}
				break;
			case op_implicit: case op_explicit: /* make gcc happy */
					  break;
		}
		/* any non-continue ends up here: invoke the callback */
		(*func)( &list->op, insn, arg );
	}

	return 1;
}

static void count_operand( x86_op_t *op, x86_insn_t *insn, void *arg ) {
	size_t * count = (size_t *) arg;
	*count = *count + 1;
}

size_t x86_operand_count( x86_insn_t *insn, enum x86_op_foreach_type type ) {
	size_t count = 0;
	
	/* save us a list traversal for common counts... */
	if ( type == op_any ) {
		return insn->operand_count;
	} else if ( type == op_explicit ) {
		return insn->explicit_count;
	}

	x86_operand_foreach( insn, count_operand, &count, type );
	return count;
}

/* accessor functions */
x86_op_t * x86_operand_1st( x86_insn_t *insn ) {
	if (! insn->explicit_count ) {
		return NULL;
	}

	return &(insn->operands->op);
}

x86_op_t * x86_operand_2nd( x86_insn_t *insn ) {
	if ( insn->explicit_count < 2 ) {
		return NULL;
	}

	return &(insn->operands->next->op);
}

x86_op_t * x86_operand_3rd( x86_insn_t *insn ) {
	if ( insn->explicit_count < 3 ) {
		return NULL;
	}

	return &(insn->operands->next->next->op);
}
