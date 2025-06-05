%module x86disasm
%{
#include "../../libdis.h"
#include "../../../config.h"
%}

%rename(version_string) x86_version_string;
%include "../../libdis.h"
#include "../../../config.h"

%inline %{
	const char * x86_version_string( void ) {
		return PACKAGE_VERSION;
	}
%}

%rename(report_codes) x86_report_codes;
%rename(report_error) x86_report_error;
%rename(options) x86_options;
%rename(init) x86_init;
%rename(set_reporter) x86_set_reporter;
%rename(set_options) x86_set_options;
%rename(options) x86_get_options;
%rename(cleanup) x86_cleanup;
%rename(reg_type) x86_reg_type;
%rename(reg) x86_reg_t;
%rename(eaddr) x86_ea_t;
%rename(op_type) x86_op_type;
%rename(optype_is_address) x86_optype_is_address;
%rename(optype_is_relative) x86_optype_is_relative;
%rename(op_datatype) x86_op_datatype;
%rename(op_access) x86_op_access;
%rename(op_flags) x86_op_flags;
%rename(operand) x86_op_t;
%rename(insn_group) x86_insn_group; 
%rename(insn_type) x86_insn_type;
%rename(insn_note) x86_insn_note ;
%rename(flag_status) x86_flag_status; 
%rename(insn_cpu) x86_insn_cpu ;
%rename(insn_isa) x86_insn_isa ;
%rename(insn_prefix) x86_insn_prefix ;
%rename(insn) x86_insn_t;
%rename(insn_is_valid) x86_insn_is_valid;
%rename(i_disasm) x86_disasm;
%rename(i_disasm_range) x86_disasm_range;
%rename(i_disasm_forward) x86_disasm_forward;
%rename(insn_operand_count) x86_operand_count;
%rename(insn_operand_1st) x86_operand_1st;
%rename(insn_operand_2nd) x86_operand_2nd;
%rename(insn_operand_3rd) x86_operand_3rd;
%rename(insn_dest_operand) x86_get_dest_operand;
%rename(insn_src_operand) x86_get_src_operand;
%rename(insn_imm_operand) x86_get_imm_operand;
%rename(operand_size) x86_operand_size;
%rename(insn_rel_offset) x86_get_rel_offset;
%rename(insn_branch_target) x86_get_branch_target;
%rename(insn_imm) x86_get_imm;
%rename(insn_raw_imm) x86_get_raw_imm;
%rename(insn_set_addr) x86_set_insn_addr;
%rename(insn_set_offset) x86_set_insn_offset;
%rename(insn_set_function) x86_set_insn_function;
%rename(insn_set_block) x86_set_insn_block;
%rename(insn_tag) x86_tag_insn;
%rename(insn_untag) x86_untag_insn;
%rename(insn_is_tagged) x86_insn_is_tagged;
%rename(asm_format) x86_asm_format;
%rename(operand_format) x86_format_operand;
%rename(insn_format_mnemonic) x86_format_mnemonic;
%rename(insn_format) x86_format_insn;
%rename(header_format) x86_format_header;
%rename(endian) x86_endian;
%rename(size_default_address) x86_addr_size;
%rename(size_default_operand) x86_op_size;
%rename(size_machine_word) x86_word_size;
%rename(size_max_insn) x86_max_insn_size;
%rename(reg_sp) x86_sp_reg;
%rename(reg_fp) x86_fp_reg;
%rename(reg_ip) x86_ip_reg;
%rename(reg_from_id) x86_reg_from_id;
%rename(reg_from_alias) x86_get_aliased_reg;
%rename(invariant_op) x86_invariant_op_t;
%rename(invariant) x86_invariant_t;
%rename(disasm_invariant) x86_invariant_disasm;
%rename(disasm_size) x86_size_disasm;

%include "carrays.i"

%array_class( unsigned char, byteArray );


%apply (unsigned char *STRING, int LENGTH) { 
	(unsigned char *buf, size_t buf_len) 
};


%newobject x86_op_copy;
%inline %{
	x86_op_t * x86_op_copy( x86_op_t * src ) {
		x86_op_t *op;
		
		if (! src ) {
			return NULL;
		}

		op = (x86_op_t *) calloc( sizeof(x86_op_t), 1 );
		if ( op ) {
			memcpy( op, src, sizeof(x86_op_t) );
		}

		return op;
	}

	typedef struct x86_op_list_node {
		x86_op_t *op;
		struct x86_op_list_node *next, *prev;
	} x86_op_list_node;

	typedef struct x86_op_list {
		size_t count;
		x86_op_list_node *head, *tail, *curr;
	} x86_op_list;

	x86_op_list * x86_op_list_new () {
		x86_op_list *list = (x86_op_list *) 
				calloc( sizeof(x86_op_list), 1 );
		list->count = 0;
		return list;
	}

	void x86_op_list_free(x86_op_list *list) {
		x86_op_list_node *node, *next;

		node = list->head;
		while ( node ) {
			next = node->next;
			/* free( node->insn ); */
			free( node );
			node = next;
		}

		free( list );
	}

	x86_op_list_node * x86_op_list_first(x86_op_list *list) { 
		return list->head; 
	}

	x86_op_list_node * x86_op_list_last(x86_op_list *list) { 
		return list->tail; 
	}

	x86_op_list_node * x86_op_list_next(x86_op_list *list) { 
		if (! list->curr ) {
			list->curr = list->head;
			return list->head;
		}

		list->curr = list->curr->next;
		return list->curr;
	}

	x86_op_list_node * x86_op_list_prev(x86_op_list *list) { 
		if (! list->curr ) {
			list->curr = list->tail;
			return list->tail;
		}

		list->curr = list->curr->prev;
		return list->curr;
	}

%}

%newobject x86_op_list_append;

%inline %{
	void x86_op_list_append( x86_op_list * list, x86_op_t *op ) {
		x86_op_list_node *node = (x86_op_list_node *)
					calloc( sizeof(x86_op_list_node) , 1 );
		if (! node ) {
			return;
		}

		list->count++;
		if ( ! list->tail ) {
			list->head = list->tail = node;
		} else {
			list->tail->next = node;
			node->prev = list->tail;
			list->tail = node;
		}

		node->op = x86_op_copy( op );
	}

	x86_oplist_t * x86_op_list_node_copy( x86_oplist_t * list ) {
		x86_oplist_t *ptr;
		ptr = (x86_oplist_t *) calloc( sizeof(x86_oplist_t), 1 );
		if ( ptr ) {
			memcpy( &ptr->op, &list->op, sizeof(x86_op_t) );
		}

		return ptr;
	}

	x86_insn_t * x86_insn_new() {
		x86_insn_t *insn = (x86_insn_t *)
				   calloc( sizeof(x86_insn_t), 1 );
		return insn;
	}

	void x86_insn_free( x86_insn_t *insn ) {
		x86_oplist_free( insn );
		free( insn );
	}
%}

%newobject x86_insn_copy;

%inline %{
	x86_insn_t * x86_insn_copy( x86_insn_t *src) {
		x86_oplist_t *ptr, *list, *last = NULL;
		x86_insn_t *insn = (x86_insn_t *)
				   calloc( sizeof(x86_insn_t), 1 );

		if ( insn ) {
			memcpy( insn, src, sizeof(x86_insn_t) );
			insn->operands = NULL;
			insn->block = NULL;
			insn->function = NULL;

			/* copy operand list */
			for ( list = src->operands; list; list = list->next ) {
				ptr = x86_op_list_node_copy( list );

				if (! ptr ) {
					continue;
				}

				if ( insn->operands ) {
					last->next = ptr;
				} else {
					insn->operands = ptr;
				}
				last = ptr;
			}
		}

		return insn;
	}

	x86_op_list * x86_insn_op_list( x86_insn_t *insn ) {
		x86_oplist_t *list = insn->operands;
		x86_op_list *op_list = x86_op_list_new();

		for ( list = insn->operands; list; list = list->next ) {
			x86_op_list_append( op_list, &list->op );
		}

		return op_list;
	}

	typedef struct x86_insn_list_node {
		x86_insn_t *insn;
		struct x86_insn_list_node *next, *prev;
	} x86_insn_list_node;

	typedef struct x86_insn_list {
		size_t count;
		x86_insn_list_node *head, *tail, *curr;
	} x86_insn_list;

%}

%newobject x86_insn_list_new;

%inline %{
	x86_insn_list * x86_insn_list_new () {
		x86_insn_list *list = (x86_insn_list *) 
				calloc( sizeof(x86_insn_list), 1 );
		list->count = 0;
		return list;
	}

	void x86_insn_list_free( x86_insn_list * list ) {
		x86_insn_list_node *node, *next;

		if (! list ) {
			return;
		}

		node = list->head;
		while ( node ) {
			next = node->next;
			/* free( node->insn ); */
			free( node );
			node = next;
		}

		free( list );
	}

	x86_insn_list_node * x86_insn_list_first( x86_insn_list *list ) { 
		if (! list ) {
			return NULL;
		}
		return list->head; 
	}

	x86_insn_list_node * x86_insn_list_last( x86_insn_list *list ) { 
		if (! list ) {
			return NULL;
		}
		return list->tail; 
	}

	x86_insn_list_node * x86_insn_list_next( x86_insn_list *list ) { 
		if (! list ) {
			return NULL;
		}
		if (! list->curr ) {
			list->curr = list->head;
			return list->head;
		}

		list->curr = list->curr->next;
		return list->curr;
	}

	x86_insn_list_node * x86_insn_list_prev( x86_insn_list *list ) { 
		if (! list ) {
			return NULL;
		}
		if (! list->curr ) {
			list->curr = list->tail;
			return list->tail;
		}

		list->curr = list->curr->prev;
		return list->curr;
	}

%}

%newobject x86_insn_list_append;

%inline %{
	void x86_insn_list_append( x86_insn_list *list, x86_insn_t *insn ) {
		x86_insn_list_node *node;
		if (! list ) {
			return;
		}

		node = (x86_insn_list_node *)
					calloc( sizeof(x86_insn_list_node) , 1 );

		if (! node ) {
			return;
		}

		list->count++;
		if ( ! list->tail ) {
			list->head = list->tail = node;
		} else {
			list->tail->next = node;
			node->prev = list->tail;
			list->tail = node;
		}

		node->insn = x86_insn_copy( insn );
	}

	typedef struct {
		enum x86_report_codes last_error;
		void * last_error_data;
		void * disasm_callback;
		void * disasm_resolver;
	} x86disasm;

	void x86_default_reporter( enum x86_report_codes code, 
				   void *data, void *arg ) {
		x86disasm *dis = (x86disasm *) arg;
		if ( dis ) {
			dis->last_error = code;
			dis->last_error_data = data;
		}
	}

	void x86_default_callback( x86_insn_t *insn, void *arg ) {
		x86_insn_list *list = (x86_insn_list *) arg;
		if ( list ) {
			x86_insn_list_append( list, insn );
		}
	}

	/* TODO: resolver stack, maybe a callback */
	long x86_default_resolver( x86_op_t *op, x86_insn_t *insn, void *arg ) {
		x86disasm *dis = (x86disasm *) arg;
		if ( dis ) {
			//return dis->resolver( op, insn );
			return 0;
		}

		return 0;
	}

	
%}

%newobject x86disasm_new;

%inline %{
	x86disasm * x86disasm_new ( enum x86_options options ) {
		x86disasm * dis = (x86disasm *)
				calloc( sizeof( x86disasm ), 1 );
		x86_init( options, x86_default_reporter, dis );
		return dis;
	}

	void x86disasm_free( x86disasm * dis ) {
		x86_cleanup();
		free( dis );
	}
%}

%newobject x86_disasm;

%inline %{
	x86_insn_t * disasm( unsigned char *buf, size_t buf_len, 
		           unsigned long buf_rva, unsigned int offset ) {
		x86_insn_t *insn = calloc( sizeof( x86_insn_t ), 1 );
		x86_disasm( buf, buf_len, buf_rva, offset, insn );
		return insn;
	}

	int disasm_range( unsigned char *buf, size_t buf_len, 
	              unsigned long buf_rva, unsigned int offset,
		      unsigned int len ) {

		x86_insn_list *list = x86_insn_list_new();

		if ( len > buf_len ) {
			len = buf_len;
		}

		return x86_disasm_range( buf, buf_rva, offset, len, 
				x86_default_callback, list );
	}

	int disasm_forward( unsigned char *buf, size_t buf_len,
			    unsigned long buf_rva, unsigned int offset ) {
		x86_insn_list *list = x86_insn_list_new();

		/* use default resolver: damn SWIG callbacks! */
		return x86_disasm_forward( buf, buf_len, buf_rva, offset,
			                   x86_default_callback, list, 
					   x86_default_resolver, NULL );
	}

	size_t disasm_invariant( unsigned char *buf, size_t buf_len, 
			  x86_invariant_t *inv ) {
		return x86_invariant_disasm( buf, buf_len, inv );
	}

	size_t disasm_size( unsigned char *buf, size_t buf_len ) {
		return x86_size_disasm( buf, buf_len );
	}

	int x86_max_operand_string( enum x86_asm_format format ) {
		switch ( format ) {
			case xml_syntax:
				return  MAX_OP_XML_STRING;
				break;
			case raw_syntax:
				return MAX_OP_RAW_STRING;
				break;
			case native_syntax:
			case intel_syntax:
			case att_syntax:
			case unknown_syntax:
			default:
				return MAX_OP_STRING;
				break;
		}
	}


	int x86_max_insn_string( enum x86_asm_format format ) {
		switch ( format ) {
			case xml_syntax:
				return  MAX_INSN_XML_STRING;
				break;
			case raw_syntax:
				return MAX_INSN_RAW_STRING;
				break;
			case native_syntax:
			case intel_syntax:
			case att_syntax:
			case unknown_syntax:
			default:
				return MAX_INSN_STRING;
				break;
		}
	}

	int x86_max_num_operands( ) { return MAX_NUM_OPERANDS; }
%}

