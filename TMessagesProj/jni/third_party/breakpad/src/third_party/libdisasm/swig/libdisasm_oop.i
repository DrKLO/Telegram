%module x86disasm
%{
#ifdef _MSC_VER
	typedef __int64         qword;
#else
	typedef long long       qword;
#endif

#include <sys/types.h>

#define MAX_REGNAME 8
#define MAX_PREFIX_STR 32
#define MAX_MNEM_STR 16
#define MAX_INSN_SIZE 20
#define MAX_OP_STRING 32
#define MAX_OP_RAW_STRING 64
#define MAX_OP_XML_STRING 256
#define MAX_NUM_OPERANDS 8
#define MAX_INSN_STRING 512
#define MAX_INSN_RAW_STRING 1024
#define MAX_INSN_XML_STRING 4096

#include "../../../config.h"


const char * version_string( void ) {
	return PACKAGE_VERSION;
}

%}

const char * version_string( void );

%rename(X86_Register) x86_reg_t;
%rename(X86_EAddr) x86_ea_t;
%rename(X86_Operand) x86_op_t;
//%rename(X86_OpList) x86_oplist_t;
%rename(X86_Insn) x86_insn_t;
%rename(X86_InvOperand) x86_invariant_op_t;
%rename(X86_Invariant) x86_invariant_t;

%include "carrays.i"

%array_class( unsigned char, byteArray );


%apply (unsigned char *STRING, int LENGTH) { 
	(unsigned char *buf, size_t buf_len) 
};


%inline %{


enum x86_asm_format { 
	unknown_syntax = 0,		/* never use! */
	native_syntax, 			/* header: 35 bytes */
	intel_syntax, 			/* header: 23 bytes */
	att_syntax,  			/* header: 23 bytes */
	xml_syntax,			/* header: 679 bytes */
	raw_syntax			/* header: 172 bytes */
};
%}

/* ================================================================== */
/* operand class */
%inline %{
	enum x86_reg_type {
	        reg_gen         = 0x00001, reg_in          = 0x00002,
	        reg_out         = 0x00004, reg_local       = 0x00008,
	        reg_fpu         = 0x00010, reg_seg         = 0x00020,
	        reg_simd        = 0x00040, reg_sys         = 0x00080,
	        reg_sp          = 0x00100, reg_fp          = 0x00200,
	        reg_pc          = 0x00400, reg_retaddr     = 0x00800,
	        reg_cond        = 0x01000, reg_zero        = 0x02000,
	        reg_ret         = 0x04000, reg_src         = 0x10000,
	        reg_dest        = 0x20000, reg_count       = 0x40000
	};

	typedef struct {
       	 	char name[MAX_REGNAME];
	        enum x86_reg_type type;
	        unsigned int size;
	        unsigned int id;
		unsigned int alias;
		unsigned int shift;
	} x86_reg_t;

	void x86_reg_from_id( unsigned int id, x86_reg_t * reg );

	typedef struct {
	        unsigned int     scale;
	        x86_reg_t        index, base;
	        long             disp;
	        char             disp_sign;
	        char             disp_size;
	} x86_ea_t;

	enum x86_op_type {
	        op_unused = 0,
	        op_register = 1,
	        op_immediate = 2,
	        op_relative_near = 3,
	        op_relative_far = 4,
	        op_absolute = 5, 
	        op_expression = 6,
	        op_offset = 7,
	        op_unknown
	};

	enum x86_op_datatype {
	       	op_byte = 1, op_word = 2,
	        op_dword = 3, op_qword = 4,
	        op_dqword = 5, op_sreal = 6,
	        op_dreal = 7, op_extreal = 8,
	        op_bcd = 9,  op_ssimd = 10,
	        op_dsimd = 11, op_sssimd = 12,
	        op_sdsimd = 13, op_descr32 = 14,
		op_descr16 = 15, op_pdescr32 = 16,
		op_pdescr16 = 17, op_fpuenv = 18,
		op_fpregset = 19,
	};

	enum x86_op_access {
	        op_read = 1,
	        op_write = 2,
	        op_execute = 4
	};

	enum x86_op_flags {
	        op_signed = 1, op_string = 2, 
	        op_constant = 4, op_pointer = 8,  
		op_sysref = 0x010, op_implied = 0x020,
		op_hardcode = 0x40, op_es_seg = 0x100,
	        op_cs_seg = 0x200, op_ss_seg = 0x300,
	        op_ds_seg = 0x400, op_fs_seg = 0x500,
	        op_gs_seg = 0x600
	};

	typedef struct {
	        enum x86_op_type        type;
	        enum x86_op_datatype    datatype;
	        enum x86_op_access      access;
	        enum x86_op_flags       flags;
	        union {
	                char            sbyte;
	                short           sword;
	                long            sdword;
	                qword           sqword;
	                unsigned char   byte;
	       	         unsigned short  word;
	                unsigned long   dword;
	                qword           qword;
	                float           sreal;
	                double          dreal;
	                unsigned char   extreal[10];
	                unsigned char   bcd[10];
	                qword           dqword[2];
	                unsigned char   simd[16];
	                unsigned char   fpuenv[28];
	                void            * address;
	                unsigned long   offset;
	                x86_reg_t       reg;
	                char            relative_near;
	       	         long            relative_far;
       	         	x86_ea_t        expression;
        	} data;
		void * insn;
	} x86_op_t;

	unsigned int x86_operand_size( x86_op_t *op );

	int x86_format_operand(x86_op_t *op, char *buf, int len,
        	          enum x86_asm_format format);
%}

%extend x86_reg_t{
	x86_reg_t * aliased_reg( ) {
		x86_reg_t * reg = (x86_reg_t * )
				  calloc( sizeof(x86_reg_t), 1 );
		x86_reg_from_id( self->id, reg );
		return reg;
	}
}

%extend x86_op_t{
	size_t size() {
		return x86_operand_size( self );
	}
	char * format( enum x86_asm_format format ) {
		char *buf, *str;
		size_t len;

		switch ( format ) {
			case xml_syntax:
				len = MAX_OP_XML_STRING;
				break;
			case raw_syntax:
				len = MAX_OP_RAW_STRING;
				break;
			case native_syntax:
			case intel_syntax:
			case att_syntax:
			case unknown_syntax:
			default:
				len = MAX_OP_STRING;
				break;
		}

		buf = (char * ) calloc( len + 1, 1 );
		x86_format_operand( self, buf, len, format );

		/* drop buffer down to a reasonable size */
		str = strdup( buf );
		free(buf);
		return str;
	}
	
	int is_address( ) {
		if ( self->type == op_absolute ||
		     self->type == op_offset ) {
		     return 1;
		}

		return 0;
	}

	int is_relative( ) {
		if ( self->type == op_relative_near ||
		     self->type == op_relative_far ) {
		     return 1;
		}

		return 0;
	}

	%newobject copy;
	x86_op_t * copy() {
		x86_op_t *op = (x86_op_t *) calloc( sizeof(x86_op_t), 1 );

		if ( op ) {
			memcpy( op, self, sizeof(x86_op_t) );
		}

		return op;
	}
}

/* ================================================================== */
/* operand list class */
%inline %{
	typedef struct X86_OpListNode {
		x86_op_t *op;
		struct X86_OpListNode *next, *prev;
	} X86_OpListNode;

	typedef struct X86_OpList {
		size_t count;
		X86_OpListNode *head, *tail, *curr;
	} X86_OpList;
%}

%extend X86_OpList {
	X86_OpList () {
		X86_OpList *list = (X86_OpList *) 
				calloc( sizeof(X86_OpList), 1 );
		list->count = 0;
		return list;
	}

	~X86_OpList() {
		X86_OpListNode *node, *next;

		node = self->head;
		while ( node ) {
			next = node->next;
			/* free( node->insn ); */
			free( node );
			node = next;
		}

		free( self );
	}

	X86_OpListNode * first() { 
		self->curr = self->head;
		return self->head; 
	}

	X86_OpListNode * last() { 
		self->curr = self->tail;
		return self->tail; 
	}

	X86_OpListNode * next() { 
		if (! self->curr ) {
			self->curr = self->head;
			return self->head;
		}

		self->curr = self->curr->next;
		return self->curr;
	}

	X86_OpListNode * prev() { 
		if (! self->curr ) {
			self->curr = self->tail;
			return self->tail;
		}

		self->curr = self->curr->prev;
		return self->curr;
	}

	%newobject append;
	void append( x86_op_t *op ) {
		X86_OpListNode *node = (X86_OpListNode *)
					calloc( sizeof(X86_OpListNode) , 1 );
		if (! node ) {
			return;
		}

		self->count++;
		if ( ! self->tail ) {
			self->head = self->tail = node;
		} else {
			self->tail->next = node;
			node->prev = self->tail;
			self->tail = node;
		}

		node->op = x86_op_t_copy( op );
	}
}

%inline %{
	typedef struct x86_operand_list {
		x86_op_t op;
		struct x86_operand_list *next;
	} x86_oplist_t;
%}

%extend x86_oplist_t {
	%newobject x86_oplist_node_copy;
}

/* ================================================================== */
/* instruction class */
%inline %{
	x86_oplist_t * x86_oplist_node_copy( x86_oplist_t * list ) {
		x86_oplist_t *ptr;
		ptr = (x86_oplist_t *) calloc( sizeof(x86_oplist_t), 1 );
		if ( ptr ) {
			memcpy( &ptr->op, &list->op, sizeof(x86_op_t) );
		}

		return ptr;
	}

	enum x86_insn_group {
		insn_none = 0, insn_controlflow = 1,
	        insn_arithmetic = 2, insn_logic = 3,
	        insn_stack = 4, insn_comparison = 5,
	        insn_move = 6, insn_string = 7,
	        insn_bit_manip = 8, insn_flag_manip = 9,
	        insn_fpu = 10, insn_interrupt = 13,
	        insn_system = 14, insn_other = 15
	};

	enum x86_insn_type {
		insn_invalid = 0, insn_jmp = 0x1001,
	        insn_jcc = 0x1002, insn_call = 0x1003,
	        insn_callcc = 0x1004, insn_return = 0x1005,
	        insn_add = 0x2001, insn_sub = 0x2002,
	        insn_mul = 0x2003, insn_div = 0x2004,
	        insn_inc = 0x2005, insn_dec = 0x2006,
	        insn_shl = 0x2007, insn_shr = 0x2008,
	        insn_rol = 0x2009, insn_ror = 0x200A,
	        insn_and = 0x3001, insn_or = 0x3002,
	        insn_xor = 0x3003, insn_not = 0x3004,
	        insn_neg = 0x3005, insn_push = 0x4001,
	        insn_pop = 0x4002, insn_pushregs = 0x4003,
	        insn_popregs = 0x4004, insn_pushflags = 0x4005,
	        insn_popflags = 0x4006, insn_enter = 0x4007,
	        insn_leave = 0x4008, insn_test = 0x5001,
	        insn_cmp = 0x5002, insn_mov = 0x6001,
	        insn_movcc = 0x6002, insn_xchg = 0x6003,
	        insn_xchgcc = 0x6004, insn_strcmp = 0x7001,
	        insn_strload = 0x7002, insn_strmov = 0x7003,
	        insn_strstore = 0x7004, insn_translate = 0x7005,
	        insn_bittest = 0x8001, insn_bitset = 0x8002,
	        insn_bitclear = 0x8003, insn_clear_carry = 0x9001,
	        insn_clear_zero = 0x9002, insn_clear_oflow = 0x9003,
	        insn_clear_dir = 0x9004, insn_clear_sign = 0x9005,
	        insn_clear_parity = 0x9006, insn_set_carry = 0x9007,
	        insn_set_zero = 0x9008, insn_set_oflow = 0x9009,
	        insn_set_dir = 0x900A, insn_set_sign = 0x900B,
	        insn_set_parity = 0x900C, insn_tog_carry = 0x9010,
	        insn_tog_zero = 0x9020, insn_tog_oflow = 0x9030,
	        insn_tog_dir = 0x9040, insn_tog_sign = 0x9050,
	        insn_tog_parity = 0x9060, insn_fmov = 0xA001,
	        insn_fmovcc = 0xA002, insn_fneg = 0xA003,
	       	insn_fabs = 0xA004, insn_fadd = 0xA005,
	        insn_fsub = 0xA006, insn_fmul = 0xA007,
	        insn_fdiv = 0xA008, insn_fsqrt = 0xA009,
	        insn_fcmp = 0xA00A, insn_fcos = 0xA00C,
	        insn_fldpi = 0xA00D, insn_fldz = 0xA00E,
	        insn_ftan = 0xA00F, insn_fsine = 0xA010,
	        insn_fsys = 0xA020, insn_int = 0xD001,
	        insn_intcc = 0xD002,   insn_iret = 0xD003,
	        insn_bound = 0xD004, insn_debug = 0xD005,
	        insn_trace = 0xD006, insn_invalid_op = 0xD007,
	        insn_oflow = 0xD008, insn_halt = 0xE001,
	        insn_in = 0xE002, insn_out = 0xE003, 
	        insn_cpuid = 0xE004, insn_nop = 0xF001,
	        insn_bcdconv = 0xF002, insn_szconv = 0xF003 
	};

	enum x86_insn_note {
		insn_note_ring0		= 1,
		insn_note_smm		= 2,
		insn_note_serial	= 4
	};

	enum x86_flag_status {
	        insn_carry_set = 0x1,
	        insn_zero_set = 0x2,
	        insn_oflow_set = 0x4,
	        insn_dir_set = 0x8,
	        insn_sign_set = 0x10,
	        insn_parity_set = 0x20,	
	        insn_carry_or_zero_set = 0x40,
	        insn_zero_set_or_sign_ne_oflow = 0x80,
	        insn_carry_clear = 0x100,
	        insn_zero_clear = 0x200,
	        insn_oflow_clear = 0x400,
	        insn_dir_clear = 0x800,
	        insn_sign_clear = 0x1000,
	        insn_parity_clear = 0x2000,
	        insn_sign_eq_oflow = 0x4000,
	        insn_sign_ne_oflow = 0x8000
	};

	enum x86_insn_cpu {
		cpu_8086 	= 1, cpu_80286	= 2,
		cpu_80386	= 3, cpu_80387	= 4,
		cpu_80486	= 5, cpu_pentium	= 6,
		cpu_pentiumpro	= 7, cpu_pentium2	= 8,
		cpu_pentium3	= 9, cpu_pentium4	= 10,
		cpu_k6		= 16, cpu_k7		= 32,
		cpu_athlon	= 48
	};

	enum x86_insn_isa {
		isa_gp		= 1, isa_fp		= 2,
		isa_fpumgt	= 3, isa_mmx		= 4,
		isa_sse1	= 5, isa_sse2	= 6,
		isa_sse3	= 7, isa_3dnow	= 8,
		isa_sys		= 9	
	};
	
	enum x86_insn_prefix {
	        insn_no_prefix = 0,
	        insn_rep_zero = 1,
	        insn_rep_notzero = 2,
	        insn_lock = 4
	};


	typedef struct {
        	unsigned long addr;
	        unsigned long offset;
	        enum x86_insn_group group;
	        enum x86_insn_type type;
		enum x86_insn_note note;
	        unsigned char bytes[MAX_INSN_SIZE];
	        unsigned char size;
		unsigned char addr_size;
		unsigned char op_size;
		enum x86_insn_cpu cpu;
		enum x86_insn_isa isa;
	        enum x86_flag_status flags_set;
	        enum x86_flag_status flags_tested;
		unsigned char stack_mod;
		long stack_mod_val;
	        enum x86_insn_prefix prefix;
	        char prefix_string[MAX_PREFIX_STR];
	        char mnemonic[MAX_MNEM_STR];
	        x86_oplist_t *operands;
		size_t operand_count;
		size_t explicit_count;
	        void *block;
	        void *function;
	        int tag;
	} x86_insn_t;

	typedef void (*x86_operand_fn)(x86_op_t *op, x86_insn_t *insn, 
		      void *arg);

	enum x86_op_foreach_type {
		op_any 	= 0,
		op_dest = 1,
		op_src 	= 2,
		op_ro 	= 3,
		op_wo 	= 4,
		op_xo 	= 5,
		op_rw 	= 6,
		op_implicit = 0x10,
		op_explicit = 0x20
	};

	size_t x86_operand_count( x86_insn_t *insn, 
				enum x86_op_foreach_type type );
	x86_op_t * x86_operand_1st( x86_insn_t *insn );
	x86_op_t * x86_operand_2nd( x86_insn_t *insn );
	x86_op_t * x86_operand_3rd( x86_insn_t *insn );
	long x86_get_rel_offset( x86_insn_t *insn );
	x86_op_t * x86_get_branch_target( x86_insn_t *insn );
	x86_op_t * x86_get_imm( x86_insn_t *insn );
	unsigned char * x86_get_raw_imm( x86_insn_t *insn );
	void x86_set_insn_addr( x86_insn_t *insn, unsigned long addr );
	int x86_format_mnemonic(x86_insn_t *insn, char *buf, int len,
                        enum x86_asm_format format);
	int x86_format_insn(x86_insn_t *insn, char *buf, int len, 
				enum x86_asm_format);
	void x86_oplist_free( x86_insn_t *insn );
	int x86_insn_is_valid( x86_insn_t *insn );
%}

%extend x86_insn_t {
	x86_insn_t() {
		x86_insn_t *insn = (x86_insn_t *)
				   calloc( sizeof(x86_insn_t), 1 );
		return insn;
	}
	~x86_insn_t() {
		x86_oplist_free( self );
		free( self );
	}

	int is_valid( ) {
		return x86_insn_is_valid( self );
	}

	x86_op_t * operand_1st() {
		return x86_operand_1st( self );
	}

	x86_op_t * operand_2nd() {
		return x86_operand_2nd( self );
	}

	x86_op_t * operand_3rd() {
		return x86_operand_3rd( self );
	}

	x86_op_t * operand_dest() {
		return x86_operand_1st( self );
	}

	x86_op_t * operand_src() {
		return x86_operand_2nd( self );
	}

	size_t num_operands( enum x86_op_foreach_type type ) {
		return x86_operand_count( self, type );
	}

	long rel_offset() {
		return x86_get_rel_offset( self );
	}

	x86_op_t * branch_target() {
		return x86_get_branch_target( self );
	}

	x86_op_t * imm() {
		return x86_get_imm( self );
	}

	unsigned char * raw_imm() {
		return x86_get_raw_imm( self );
	}

	%newobject format;
	char * format( enum x86_asm_format format ) {
		char *buf, *str;
		size_t len;

		switch ( format ) {
			case xml_syntax:
				len = MAX_INSN_XML_STRING;
				break;
			case raw_syntax:
				len = MAX_INSN_RAW_STRING;
				break;
			case native_syntax:
			case intel_syntax:
			case att_syntax:
			case unknown_syntax:
			default:
				len = MAX_INSN_STRING;
				break;
		}

		buf = (char * ) calloc( len + 1, 1 );
		x86_format_insn( self, buf, len, format );

		/* drop buffer down to a reasonable size */
		str = strdup( buf );
		free(buf);
		return str;
	}

	%newobject format_mnemonic;
	char * format_mnemonic( enum x86_asm_format format ) {
		char *buf, *str;
		size_t len = MAX_MNEM_STR + MAX_PREFIX_STR + 4;

		buf = (char * ) calloc( len, 1 );
		x86_format_mnemonic( self, buf, len, format );

		/* drop buffer down to a reasonable size */
		str = strdup( buf );
		free(buf);

		return str;
	}

	%newobject copy;
	x86_insn_t * copy() {
		x86_oplist_t *ptr, *list, *last = NULL;
		x86_insn_t *insn = (x86_insn_t *)
				   calloc( sizeof(x86_insn_t), 1 );

		if ( insn ) {
			memcpy( insn, self, sizeof(x86_insn_t) );
			insn->operands = NULL;
			insn->block = NULL;
			insn->function = NULL;

			/* copy operand list */
			for ( list = self->operands; list; list = list->next ) {
				ptr = x86_oplist_node_copy( list );

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

	X86_OpList * operand_list( ) {
		x86_oplist_t *list = self->operands;
		X86_OpList *op_list = new_X86_OpList();

		for ( list = self->operands; list; list = list->next ) {
			X86_OpList_append( op_list, &list->op );
		}

		return op_list;
	}
}

/* ================================================================== */
/* invariant instruction class */
%inline %{
	#define X86_WILDCARD_BYTE 0xF4

	typedef struct {
        	enum x86_op_type        type;
        	enum x86_op_datatype    datatype;
        	enum x86_op_access      access;
        	enum x86_op_flags       flags;
	} x86_invariant_op_t;

	typedef struct {
		unsigned char bytes[64];
		unsigned int  size;
        	enum x86_insn_group group;
        	enum x86_insn_type type;
		x86_invariant_op_t operands[3];
	} x86_invariant_t;
%}

%extend x86_invariant_t {

	x86_invariant_t() {
		x86_invariant_t *inv = (x86_invariant_t *)
				calloc( sizeof(x86_invariant_t), 1 );
		return inv;
	}

	~x86_invariant_t() {
		free( self );
	}
}

/* ================================================================== */
/* instruction list class */
%inline %{
	typedef struct X86_InsnListNode {
		x86_insn_t *insn;
		struct X86_InsnListNode *next, *prev;
	} X86_InsnListNode;

	typedef struct X86_InsnList {
		size_t count;
		X86_InsnListNode *head, *tail, *curr;
	} X86_InsnList;
%}

%extend X86_InsnList {
	X86_InsnList () {
		X86_InsnList *list = (X86_InsnList *) 
				calloc( sizeof(X86_InsnList), 1 );
		list->count = 0;
		return list;
	}

	~X86_InsnList() {
		X86_InsnListNode *node, *next;

		node = self->head;
		while ( node ) {
			next = node->next;
			/* free( node->insn ); */
			free( node );
			node = next;
		}

		free( self );
	}

	X86_InsnListNode * first() { return self->head; }

	X86_InsnListNode * last() { return self->tail; }

	X86_InsnListNode * next() { 
		if (! self->curr ) {
			self->curr = self->head;
			return self->head;
		}

		self->curr = self->curr->next;
		return self->curr;
	}

	X86_InsnListNode * prev() { 
		if (! self->curr ) {
			self->curr = self->tail;
			return self->tail;
		}

		self->curr = self->curr->prev;
		return self->curr;
	}

	%newobject append;
	void append( x86_insn_t *insn ) {
		X86_InsnListNode *node = (X86_InsnListNode *)
					calloc( sizeof(X86_InsnListNode) , 1 );
		if (! node ) {
			return;
		}

		self->count++;
		if ( ! self->tail ) {
			self->head = self->tail = node;
		} else {
			self->tail->next = node;
			node->prev = self->tail;
			self->tail = node;
		}

		node->insn = x86_insn_t_copy( insn );
	}
}

/* ================================================================== */
/* address table class */
/* slight TODO */

/* ================================================================== */
/* Main disassembler class */
%inline %{

	enum x86_options {
		opt_none= 0,
		opt_ignore_nulls=1,
		opt_16_bit=2
		};
	enum x86_report_codes {
        	report_disasm_bounds,
        	report_insn_bounds,
        	report_invalid_insn,
        	report_unknown
	};


	typedef struct {
		enum x86_report_codes last_error;
		void * last_error_data;
		void * disasm_callback;
		void * disasm_resolver;
	} X86_Disasm;

	typedef void (*DISASM_REPORTER)( enum x86_report_codes code, 
				 	 void *data, void *arg );
	typedef void (*DISASM_CALLBACK)( x86_insn_t *insn, void * arg );
	typedef long (*DISASM_RESOLVER)( x86_op_t *op, 
					 x86_insn_t * current_insn,
				 	 void *arg );

	void x86_report_error( enum x86_report_codes code, void *data );
	int x86_init( enum x86_options options, DISASM_REPORTER reporter, 
		      void *arg);
	void x86_set_reporter( DISASM_REPORTER reporter, void *arg);
	void x86_set_options( enum x86_options options );
	enum x86_options x86_get_options( void );
	int x86_cleanup(void);
	int x86_format_header( char *buf, int len, enum x86_asm_format format);
	unsigned int x86_endian(void);
	unsigned int x86_addr_size(void);
	unsigned int x86_op_size(void);
	unsigned int x86_word_size(void);
	unsigned int x86_max_insn_size(void);
	unsigned int x86_sp_reg(void);
	unsigned int x86_fp_reg(void);
	unsigned int x86_ip_reg(void);
	size_t x86_invariant_disasm( unsigned char *buf, int buf_len, 
			  x86_invariant_t *inv );
	size_t x86_size_disasm( unsigned char *buf, unsigned int buf_len );
	int x86_disasm( unsigned char *buf, unsigned int buf_len,
	                unsigned long buf_rva, unsigned int offset,
	                x86_insn_t * insn );
	int x86_disasm_range( unsigned char *buf, unsigned long buf_rva,
	                      unsigned int offset, unsigned int len,
	                      DISASM_CALLBACK func, void *arg );
	int x86_disasm_forward( unsigned char *buf, unsigned int buf_len,
	                        unsigned long buf_rva, unsigned int offset,
	                        DISASM_CALLBACK func, void *arg,
	                        DISASM_RESOLVER resolver, void *r_arg );
	
	void x86_default_reporter( enum x86_report_codes code, 
				   void *data, void *arg ) {
		X86_Disasm *dis = (X86_Disasm *) arg;
		if ( dis ) {
			dis->last_error = code;
			dis->last_error_data = data;
		}
	}

	void x86_default_callback( x86_insn_t *insn, void *arg ) {
		X86_InsnList *list = (X86_InsnList *) arg;
		if ( list ) {
			X86_InsnList_append( list, insn );
		}
	}

	/* TODO: resolver stack, maybe a callback */
	long x86_default_resolver( x86_op_t *op, x86_insn_t *insn, void *arg ) {
		X86_Disasm *dis = (X86_Disasm *) arg;
		if ( dis ) {
			//return dis->resolver( op, insn );
			return 0;
		}

		return 0;
	}

%}

%extend X86_Disasm { 
	
	X86_Disasm( ) {
		X86_Disasm * dis = (X86_Disasm *)
				calloc( sizeof( X86_Disasm ), 1 );
		x86_init( opt_none, x86_default_reporter, dis );
		return dis;
	}

	X86_Disasm( enum x86_options options ) {
		X86_Disasm * dis = (X86_Disasm *)
				calloc( sizeof( X86_Disasm ), 1 );
		x86_init( options, x86_default_reporter, dis );
		return dis;
	}

	X86_Disasm( enum x86_options options, DISASM_REPORTER reporter ) {
		X86_Disasm * dis = (X86_Disasm *)
				calloc( sizeof( X86_Disasm ), 1 );
		x86_init( options, reporter, NULL );
		return dis;
	}

	X86_Disasm( enum x86_options options, DISASM_REPORTER reporter,
		    void * arg ) {
		X86_Disasm * dis = (X86_Disasm *)
				calloc( sizeof( X86_Disasm ), 1 );
		x86_init( options, reporter, arg );
		return dis;
	}

	~X86_Disasm() {
		x86_cleanup();
		free( self );
	}

	void set_options( enum x86_options options ) {
		return x86_set_options( options );
	}

	enum x86_options options() {
		return x86_get_options();
	}

	void set_callback( void * callback ) {
		self->disasm_callback = callback;
	}

	void set_resolver( void * callback ) {
		self->disasm_resolver = callback;
	}

	void report_error( enum x86_report_codes code ) {
		x86_report_error( code, NULL );
	}

	%newobject disasm;
	x86_insn_t * disasm( unsigned char *buf, size_t buf_len, 
		           unsigned long buf_rva, unsigned int offset ) {
		x86_insn_t *insn = calloc( sizeof( x86_insn_t ), 1 );
		x86_disasm( buf, buf_len, buf_rva, offset, insn );
		return insn;
	}

	int disasm_range( unsigned char *buf, size_t buf_len, 
	              unsigned long buf_rva, unsigned int offset,
		      unsigned int len ) {

		X86_InsnList *list = new_X86_InsnList();

		if ( len > buf_len ) {
			len = buf_len;
		}

		return x86_disasm_range( buf, buf_rva, offset, len, 
				x86_default_callback, list );
	}

	int disasm_forward( unsigned char *buf, size_t buf_len,
			    unsigned long buf_rva, unsigned int offset ) {
		X86_InsnList *list = new_X86_InsnList();

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

	%newobject format_header;
	char * format_header( enum x86_asm_format format) {
		char *buf, *str;
		size_t len;

		switch ( format ) {
			/* these were obtained from x86_format.c */
			case xml_syntax:
				len = 679; break;
			case raw_syntax:
				len = 172; break;
			case native_syntax:
				len = 35; break;
			case intel_syntax:
				len = 23; break;
			case att_syntax:
				len = 23; break;
			case unknown_syntax:
			default:
				len = 23; break;
		}

		buf = (char * ) calloc( len + 1, 1 );
		x86_format_header( buf, len, format );

		return buf;
	}

	unsigned int endian() {
		return x86_endian();
	}

	unsigned int addr_size() {
		return x86_addr_size();
	}

	unsigned int op_size() {
		return x86_op_size();
	}

	unsigned int word_size() {
		return x86_word_size();
	}

	unsigned int max_insn_size() {
		return x86_max_insn_size();
	}

	unsigned int sp_reg() {
		return x86_sp_reg();
	}

	unsigned int fp_reg() {
		return x86_fp_reg();
	}

	unsigned int ip_reg() {
		return x86_ip_reg();
	}

	%newobject reg_from_id;
	x86_reg_t * reg_from_id( unsigned int id ) {
		x86_reg_t * reg = calloc( sizeof(x86_reg_t), 1 );
		x86_reg_from_id( id, reg );
		return reg;
	}

	unsigned char wildcard_byte() { return X86_WILDCARD_BYTE; }

	int max_register_string() { return MAX_REGNAME; }

	int max_prefix_string() { return MAX_PREFIX_STR; }

	int max_mnemonic_string() { return MAX_MNEM_STR; }

	int max_operand_string( enum x86_asm_format format ) {
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


	int max_insn_string( enum x86_asm_format format ) {
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

	int max_num_operands( ) { return MAX_NUM_OPERANDS; }
}

/* python callback, per the manual */
/*%typemap(python,in) PyObject *pyfunc {
	if (!PyCallable_Check($source)) {
		PyErr_SetString(PyExc_TypeError, "Need a callable object!");
 		return NULL;
	}
	$target = $source;
}*/

/* python FILE * callback, per the manual */
/*
%typemap(python,in) FILE * {
  if (!PyFile_Check($source)) {
      PyErr_SetString(PyExc_TypeError, "Need a file!");
      return NULL;
  }
  $target = PyFile_AsFile($source);
}*/


