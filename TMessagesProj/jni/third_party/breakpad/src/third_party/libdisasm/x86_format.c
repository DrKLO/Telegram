#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "libdis.h"
#include <inttypes.h>

#ifdef _MSC_VER
        #define snprintf        _snprintf
        #define inline          __inline
#endif


/*
 * concatenation macros.  STRNCATF concatenates a format string, buf
 * only with one argument.
 */
#define STRNCAT( buf, str, len ) do {   				\
	int _i = strlen(str), _blen = strlen(buf), _len = len - 1;  	\
	if ( len ) {							\
        	strncat( buf, str, _len );  				\
		if ( _len <= _i ) {					\
			buf[_blen+_len] = '\0';				\
			len = 0;					\
		} else {						\
			len -= _i;					\
		}							\
	}								\
} while( 0 )

#define STRNCATF( buf, fmt, data, len ) do {        \
        char _tmp[MAX_OP_STRING];                   \
                                                    \
        snprintf( _tmp, sizeof _tmp, fmt, data );   \
        STRNCAT( buf, _tmp, len );                  \
} while( 0 )


#define PRINT_DISPLACEMENT( ea ) do {                            \
        if ( ea->disp_size && ea->disp ) {                       \
                if ( ea->disp_sign ) {    \
                        STRNCATF( buf, "-0x%" PRIX32, -ea->disp, len );    \
                } else {                                         \
                        STRNCATF( buf, "0x%" PRIX32, ea->disp, len );  \
                }                                                \
        }                                                        \
} while( 0 )

static const char *prefix_strings[] = {
	"",     /* no prefix */
	"repz ", /* the trailing spaces make it easy to prepend to mnemonic */
	"repnz ",
	"lock ",
	"branch delay " /* unused in x86 */
};

static int format_insn_prefix_str( enum x86_insn_prefix prefix, char *buf,
                                   int len ) {

        int len_orig = len;

        /* concat all prefix strings */
        if ( prefix & 1 ) { STRNCAT( buf, prefix_strings[1], len ); }
        if ( prefix & 2 ) { STRNCAT( buf, prefix_strings[2], len ); }
        if ( prefix & 4 ) { STRNCAT( buf, prefix_strings[3], len ); }
        if ( prefix & 8 ) { STRNCAT( buf, prefix_strings[4], len ); }

        /* return the number of characters added */
        return (len_orig - len);
}

/*
 * sprint's an operand's data to string str.
 */
static void get_operand_data_str( x86_op_t *op, char *str, int len ){

        if ( op->flags & op_signed ) {
                switch ( op->datatype ) {
                        case op_byte:
                                snprintf( str, len, "%" PRId8, op->data.sbyte );
                                return;
                        case op_word:
                                snprintf( str, len, "%" PRId16, op->data.sword );
                                return;
                        case op_qword:
                                snprintf( str, len, "%" PRId64, op->data.sqword );
                                return;
                        default:
                                snprintf( str, len, "%" PRId32, op->data.sdword );
                                return;
                }
        }

        //else
        switch ( op->datatype ) {
                case op_byte:
                        snprintf( str, len, "0x%02" PRIX8, op->data.byte );
                        return;
                case op_word:
                        snprintf( str, len, "0x%04" PRIX16, op->data.word );
                        return;
                case op_qword:
                        snprintf( str, len, "0x%08" PRIX64,op->data.sqword );
                        return;
                default:
                        snprintf( str, len, "0x%08" PRIX32, op->data.dword );
                        return;
        }
}

/*
 * sprints register types to a string.  the register types can be ORed
 * together.
 */
static void get_operand_regtype_str( int regtype, char *str, int len )
{
        static struct {
                const char *name;
                int value;
        } operand_regtypes[] = {
                {"reg_gen"    , 0x00001},
                {"reg_in"     , 0x00002},
                {"reg_out"    , 0x00004},
                {"reg_local"  , 0x00008},
                {"reg_fpu"    , 0x00010},
                {"reg_seg"    , 0x00020},
                {"reg_simd"   , 0x00040},
                {"reg_sys"    , 0x00080},
                {"reg_sp"     , 0x00100},
                {"reg_fp"     , 0x00200},
                {"reg_pc"     , 0x00400},
                {"reg_retaddr", 0x00800},
                {"reg_cond"   , 0x01000},
                {"reg_zero"   , 0x02000},
                {"reg_ret"    , 0x04000},
                {"reg_src"    , 0x10000},
                {"reg_dest"   , 0x20000},
                {"reg_count"  , 0x40000},
                {NULL, 0}, //end
        };

        unsigned int i;

        memset( str, 0, len );

        //go thru every type in the enum
        for ( i = 0; operand_regtypes[i].name; i++ ) {
                //skip if type is not set
                if(! (regtype & operand_regtypes[i].value) )
                        continue;

                //not the first time around
                if( str[0] ) {
                        STRNCAT( str, " ", len );
                }

                STRNCAT(str, operand_regtypes[i].name, len );
        }
}

static int format_expr( x86_ea_t *ea, char *buf, int len,
                        enum x86_asm_format format ) {
        char str[MAX_OP_STRING];

        if ( format == att_syntax ) {
		if (ea->base.name[0] || ea->index.name[0] || ea->scale) {
               		PRINT_DISPLACEMENT(ea);
	                STRNCAT( buf, "(", len );

	                if ( ea->base.name[0]) {
	                        STRNCATF( buf, "%%%s", ea->base.name, len );
	                }
	                if ( ea->index.name[0]) {
	                        STRNCATF( buf, ",%%%s", ea->index.name, len );
	                        if ( ea->scale > 1 ) {
	                                STRNCATF( buf, ",%d", ea->scale, len );
	                        }
	                }
	                /* handle the syntactic exception */
	                if ( ! ea->base.name[0] &&
	                     ! ea->index.name[0]   ) {
	                        STRNCATF( buf, ",%d", ea->scale, len );
	                }

	                STRNCAT( buf, ")", len );
		} else
			STRNCATF( buf, "0x%" PRIX32, ea->disp, len );

        } else if ( format == xml_syntax ){

                if ( ea->base.name[0]) {
                        STRNCAT (buf, "\t\t\t<base>\n", len);

                        get_operand_regtype_str (ea->base.type, str,
                                                 sizeof str);
                        STRNCAT (buf, "\t\t\t\t<register ", len);
                        STRNCATF (buf, "name=\"%s\" ", ea->base.name, len);
                        STRNCATF (buf, "type=\"%s\" ", str, len);
                        STRNCATF (buf, "size=%d/>\n", ea->base.size, len);

                        STRNCAT (buf, "\t\t\t</base>\n", len);
                }

                if ( ea->index.name[0]) {
                        STRNCAT (buf, "\t\t\t<index>\n", len);

                        get_operand_regtype_str (ea->index.type, str,
                                                 sizeof str);

                        STRNCAT (buf, "\t\t\t\t<register ", len);
                        STRNCATF (buf, "name=\"%s\" ", ea->index.name, len);
                        STRNCATF (buf, "type=\"%s\" ", str, len);
                        STRNCATF (buf, "size=%d/>\n", ea->index.size, len);

                        STRNCAT (buf, "\t\t\t</index>\n", len);
                }

                //scale
                STRNCAT (buf, "\t\t\t<scale>\n", len);
                STRNCAT (buf, "\t\t\t\t<immediate ", len);
                STRNCATF (buf, "value=\"%d\"/>\n", ea->scale, len);
                STRNCAT (buf, "\t\t\t</scale>\n", len);

                if ( ea->disp_size ) {

                        STRNCAT (buf, "\t\t\t<displacement>\n", len);

                        if ( ea->disp_size > 1 && ! ea->disp_sign ) {
                                STRNCAT (buf, "\t\t\t\t<address ", len);
                                STRNCATF (buf, "value=\"0x%" PRIX32 "\"/>\n", ea->disp,
                                          len);
                        } else {
                                STRNCAT (buf, "\t\t\t\t<immediate ", len);
                                STRNCATF (buf, "value=%" PRId32 "/>\n", ea->disp, len);
                        }

                        STRNCAT (buf, "\t\t\t</displacement>\n", len);
                }

        } else if ( format == raw_syntax ) {

                PRINT_DISPLACEMENT(ea);
                STRNCAT( buf, "(", len );

                STRNCATF( buf, "%s,", ea->base.name, len );
                STRNCATF( buf, "%s,", ea->index.name, len );
                STRNCATF( buf, "%d", ea->scale, len );
                STRNCAT( buf, ")", len );

        } else {

                STRNCAT( buf, "[", len );

                if ( ea->base.name[0] ) {
                        STRNCAT( buf, ea->base.name, len );
                        if ( ea->index.name[0] ||
                             (ea->disp_size && ! ea->disp_sign) ) {
                                STRNCAT( buf, "+", len );
                        }
                }
                if ( ea->index.name[0] ) {
                        STRNCAT( buf, ea->index.name, len );
                        if ( ea->scale > 1 )
                        {
                                STRNCATF( buf, "*%" PRId32, ea->scale, len );
                        }
                        if ( ea->disp_size && ! ea->disp_sign )
                        {
                                STRNCAT( buf, "+", len );
                        }
                }

                if ( ea->disp_size || (! ea->index.name[0] && 
					! ea->base.name[0] ) )
                {
                        PRINT_DISPLACEMENT(ea);
                }

                STRNCAT( buf, "]", len );
        }

        return( strlen(buf) );
}

static int format_seg( x86_op_t *op, char *buf, int len,
                       enum x86_asm_format format ) {
        int len_orig = len;
        const char *reg = "";

        if (! op || ! buf || ! len || ! op->flags) {
                return(0);
        }
        if ( op->type != op_offset && op->type != op_expression ){
                return(0);
        }
        if (! ((int) op->flags & 0xF00) ) {
                return(0);
        }

        switch (op->flags & 0xF00) {
                case op_es_seg: reg = "es"; break;
                case op_cs_seg: reg = "cs"; break;
                case op_ss_seg: reg = "ss"; break;
                case op_ds_seg: reg = "ds"; break;
                case op_fs_seg: reg = "fs"; break;
                case op_gs_seg: reg = "gs"; break;
                default:
                        break;
        }

        if (! reg[0] ) {
                return( 0 );
        }

        switch( format ) {
                case xml_syntax:
                        STRNCAT( buf, "\t\t\t<segment ", len );
                        STRNCATF( buf, "value=\"%s\"/>\n", reg, len );
                        break;
                case att_syntax:
                        STRNCATF( buf, "%%%s:", reg, len );
                        break;

                default:
                        STRNCATF( buf, "%s:", reg, len );
                        break;
        }

        return( len_orig - len ); /* return length of appended string */
}

static const char *get_operand_datatype_str( x86_op_t *op ){

        static const char *types[] = {
                "sbyte",		/* 0 */
                "sword",
                "sqword",
                "sdword",
                "sdqword",		/* 4 */
                "byte",
                "word",
                "qword",
                "dword",		/* 8 */
                "dqword",
		"sreal",
		"dreal",
		"extreal",		/* 12 */
		"bcd",
		"ssimd",
		"dsimd",
		"sssimd",		/* 16 */
		"sdsimd",
		"descr32",
		"descr16",
		"pdescr32",		/* 20 */
		"pdescr16",
		"bounds16",
		"bounds32",
		"fpu_env16",
		"fpu_env32",		/* 25 */
		"fpu_state16",
		"fpu_state32",
		"fp_reg_set"
        };

	/* handle signed values first */
        if ( op->flags & op_signed ) {
                switch (op->datatype) {
                        case op_byte:  return types[0];
                        case op_word:  return types[1];
                        case op_qword: return types[2];
                	case op_dqword: return types[4];
                        default:       return types[3];
                }
        }

        switch (op->datatype) {
                case op_byte:   	return types[5];
                case op_word:   	return types[6];
                case op_qword:  	return types[7];
                case op_dqword: 	return types[9];
		case op_sreal:		return types[10];
		case op_dreal:		return types[11];
		case op_extreal:	return types[12];
		case op_bcd:		return types[13];
		case op_ssimd:		return types[14];
		case op_dsimd:		return types[15];
		case op_sssimd:		return types[16];
		case op_sdsimd:		return types[17];
		case op_descr32:	return types[18];
		case op_descr16:	return types[19];
		case op_pdescr32:	return types[20];
		case op_pdescr16:	return types[21];
		case op_bounds16:	return types[22];
		case op_bounds32:	return types[23];
		case op_fpustate16: 	return types[24];
		case op_fpustate32: 	return types[25];
		case op_fpuenv16: 	return types[26];
		case op_fpuenv32: 	return types[27];
		case op_fpregset: 	return types[28];
                default:        	return types[8];
        }
}

static int format_insn_eflags_str( enum x86_flag_status flags, char *buf,
                                   int len) {

        static struct {
                const char *name;
                int  value;
        } insn_flags[] = {
                { "carry_set ",                 0x0001 },
                { "zero_set ",                  0x0002 },
                { "oflow_set ",                 0x0004 },
                { "dir_set ",                   0x0008 },
                { "sign_set ",                  0x0010 },
                { "parity_set ",                0x0020 },
                { "carry_or_zero_set ",         0x0040 },
                { "zero_set_or_sign_ne_oflow ", 0x0080 },
                { "carry_clear ",               0x0100 },
                { "zero_clear ",                0x0200 },
                { "oflow_clear ",               0x0400 },
                { "dir_clear ",                 0x0800 },
                { "sign_clear ",                0x1000 },
                { "parity_clear ",              0x2000 },
                { "sign_eq_oflow ",             0x4000 },
                { "sign_ne_oflow ",             0x8000 },
                { NULL,                         0x0000 }, //end
        };

        unsigned int i;
        int len_orig = len;

        for (i = 0; insn_flags[i].name; i++) {
                if (! (flags & insn_flags[i].value) )
                        continue;

                STRNCAT( buf, insn_flags[i].name, len );
        }

        return( len_orig - len );
}

static const char *get_insn_group_str( enum x86_insn_group gp ) {

        static const char *types[] = {
                "",           // 0
                "controlflow",// 1
                "arithmetic", // 2
                "logic",      // 3
                "stack",      // 4
                "comparison", // 5
                "move",       // 6
                "string",     // 7
                "bit_manip",  // 8
                "flag_manip", // 9
                "fpu",        // 10
                "",           // 11
                "",           // 12
                "interrupt",  // 13
                "system",     // 14
                "other",      // 15
        };

        if ( gp > sizeof (types)/sizeof(types[0]) )
                return "";

        return types[gp];
}

static const char *get_insn_type_str( enum x86_insn_type type ) {

        static struct {
                const char *name;
                int  value;
        } types[] = {
                /* insn_controlflow */
                { "jmp", 0x1001 },
                { "jcc", 0x1002 },
                { "call", 0x1003 },
                { "callcc", 0x1004 },
                { "return", 0x1005 },
                { "loop", 0x1006 },
                /* insn_arithmetic */
                { "add", 0x2001 },
                { "sub", 0x2002 },
                { "mul", 0x2003 },
                { "div", 0x2004 },
                { "inc", 0x2005 },
                { "dec", 0x2006 },
                { "shl", 0x2007 },
                { "shr", 0x2008 },
                { "rol", 0x2009 },
                { "ror", 0x200A },
                /* insn_logic */
                { "and", 0x3001 },
                { "or", 0x3002 },
                { "xor", 0x3003 },
                { "not", 0x3004 },
                { "neg", 0x3005 },
                /* insn_stack */
                { "push", 0x4001 },
                { "pop", 0x4002 },
                { "pushregs", 0x4003 },
                { "popregs", 0x4004 },
                { "pushflags", 0x4005 },
                { "popflags", 0x4006 },
                { "enter", 0x4007 },
                { "leave", 0x4008 },
                /* insn_comparison */
                { "test", 0x5001 },
                { "cmp", 0x5002 },
                /* insn_move */
                { "mov", 0x6001 },      /* move */
                { "movcc", 0x6002 },    /* conditional move */
                { "xchg", 0x6003 },     /* exchange */
                { "xchgcc", 0x6004 },   /* conditional exchange */
                /* insn_string */
                { "strcmp", 0x7001 },
                { "strload", 0x7002 },
                { "strmov", 0x7003 },
                { "strstore", 0x7004 },
                { "translate", 0x7005 },        /* xlat */
                /* insn_bit_manip */
                { "bittest", 0x8001 },
                { "bitset", 0x8002 },
                { "bitclear", 0x8003 },
                /* insn_flag_manip */
                { "clear_carry", 0x9001 },
                { "clear_zero", 0x9002 },
                { "clear_oflow", 0x9003 },
                { "clear_dir", 0x9004 },
                { "clear_sign", 0x9005 },
                { "clear_parity", 0x9006 },
                { "set_carry", 0x9007 },
                { "set_zero", 0x9008 },
                { "set_oflow", 0x9009 },
                { "set_dir", 0x900A },
                { "set_sign", 0x900B },
                { "set_parity", 0x900C },
                { "tog_carry", 0x9010 },
                { "tog_zero", 0x9020 },
                { "tog_oflow", 0x9030 },
                { "tog_dir", 0x9040 },
                { "tog_sign", 0x9050 },
                { "tog_parity", 0x9060 },
                /* insn_fpu */
                { "fmov", 0xA001 },
                { "fmovcc", 0xA002 },
                { "fneg", 0xA003 },
                { "fabs", 0xA004 },
                { "fadd", 0xA005 },
                { "fsub", 0xA006 },
                { "fmul", 0xA007 },
                { "fdiv", 0xA008 },
                { "fsqrt", 0xA009 },
                { "fcmp", 0xA00A },
                { "fcos", 0xA00C },
                { "fldpi", 0xA00D },
                { "fldz", 0xA00E },
                { "ftan", 0xA00F },
                { "fsine", 0xA010 },
                { "fsys", 0xA020 },
                /* insn_interrupt */
                { "int", 0xD001 },
                { "intcc", 0xD002 },    /* not present in x86 ISA */
                { "iret", 0xD003 },
                { "bound", 0xD004 },
                { "debug", 0xD005 },
                { "trace", 0xD006 },
                { "invalid_op", 0xD007 },
                { "oflow", 0xD008 },
                /* insn_system */
                { "halt", 0xE001 },
                { "in", 0xE002 },       /* input from port/bus */
                { "out", 0xE003 },      /* output to port/bus */
                { "cpuid", 0xE004 },
                /* insn_other */
                { "nop", 0xF001 },
                { "bcdconv", 0xF002 },  /* convert to or from BCD */
                { "szconv", 0xF003 },   /* change size of operand */
                { NULL, 0 }, //end
        };

        unsigned int i;

        //go thru every type in the enum
        for ( i = 0; types[i].name; i++ ) {
                if ( types[i].value == type )
                        return types[i].name;
        }

        return "";
}

static const char *get_insn_cpu_str( enum x86_insn_cpu cpu ) {
        static const char *intel[] = {
                "",           		// 0
                "8086",           	// 1
                "80286",           	// 2
                "80386",           	// 3
                "80387",           	// 4
                "80486",           	// 5
                "Pentium",           	// 6
                "Pentium Pro",          // 7
                "Pentium 2",           	// 8
                "Pentium 3",           	// 9
                "Pentium 4"           	// 10
        };

        if ( cpu < sizeof(intel)/sizeof(intel[0]) ) {
		return intel[cpu];
	} else if ( cpu == 16 ) {
		return "K6";
	} else if ( cpu == 32 ) {
		return "K7";
	} else if ( cpu == 48 ) {
		return "Athlon";
	}

        return "";
}

static const char *get_insn_isa_str( enum x86_insn_isa isa ) {
        static const char *subset[] = {
		NULL,				// 0
                "General Purpose",           	// 1
                "Floating Point",           	// 2
                "FPU Management",           	// 3
                "MMX",           		// 4
                "SSE",           		// 5
                "SSE2",           		// 6
                "SSE3",           		// 7
                "3DNow!",           		// 8
                "System"           		// 9
        };

        if ( isa > sizeof (subset)/sizeof(subset[0]) ) {
                return "";
	}

        return subset[isa];
}

static int format_operand_att( x86_op_t *op, x86_insn_t *insn, char *buf,
                               int len){

        char str[MAX_OP_STRING];

        memset (str, 0, sizeof str);

        switch ( op->type ) {
                case op_register:
                        STRNCATF( buf, "%%%s", op->data.reg.name, len );
                        break;

                case op_immediate:
                        get_operand_data_str( op, str, sizeof str );
                        STRNCATF( buf, "$%s", str, len );
                        break;

                case op_relative_near: 
                        STRNCATF( buf, "0x%08X",
                                 (unsigned int)(op->data.sbyte +
                                 insn->addr + insn->size), len );
                        break;

		case op_relative_far:
                        if (op->datatype == op_word) {
                                STRNCATF( buf, "0x%08X",
                                          (unsigned int)(op->data.sword +
                                          insn->addr + insn->size), len );
                        } else {
                        	STRNCATF( buf, "0x%08X",
                                  	(unsigned int)(op->data.sdword +
                                  	insn->addr + insn->size), len );
			}
                        break;

                case op_absolute:
			/* ATT uses the syntax $section, $offset */
                        STRNCATF( buf, "$0x%04" PRIX16 ", ", op->data.absolute.segment,
				len );
			if (op->datatype == op_descr16) {
                        	STRNCATF( buf, "$0x%04" PRIX16, 
					op->data.absolute.offset.off16, len );
			} else {
                        	STRNCATF( buf, "$0x%08" PRIX32, 
					op->data.absolute.offset.off32, len );
			}
                        break;
                case op_offset:
                        /* ATT requires a '*' before JMP/CALL ops */
                        if (insn->type == insn_jmp || insn->type == insn_call)
                                STRNCAT( buf, "*", len );

                        len -= format_seg( op, buf, len, att_syntax );
                        STRNCATF( buf, "0x%08" PRIX32, op->data.sdword, len );
                        break;

                case op_expression:
                        /* ATT requires a '*' before JMP/CALL ops */
                        if (insn->type == insn_jmp || insn->type == insn_call)
                                STRNCAT( buf, "*", len );

                        len -= format_seg( op, buf, len, att_syntax );
                        len -= format_expr( &op->data.expression, buf, len,
                                     att_syntax );
                        break;
                case op_unused:
                case op_unknown:
                        /* return 0-truncated buffer */
                        break;
        }

        return ( strlen( buf ) );
}

static int format_operand_native( x86_op_t *op, x86_insn_t *insn, char *buf,
                                  int len){

        char str[MAX_OP_STRING];

        switch (op->type) {
                case op_register:
                        STRNCAT( buf, op->data.reg.name, len );
                        break;

                case op_immediate:
                        get_operand_data_str( op, str, sizeof str );
                        STRNCAT( buf, str, len );
                        break;

                case op_relative_near:
                        STRNCATF( buf, "0x%08" PRIX32,
                                  (unsigned int)(op->data.sbyte +
                                  insn->addr + insn->size), len );
                        break;

                case op_relative_far:
                        if ( op->datatype == op_word ) {
                                STRNCATF( buf, "0x%08" PRIX32,
                                          (unsigned int)(op->data.sword +
                                          insn->addr + insn->size), len );
                                break;
                        } else {
                        	STRNCATF( buf, "0x%08" PRIX32, op->data.sdword +
                                	  insn->addr + insn->size, len );
			}
                        break;

                case op_absolute:
                        STRNCATF( buf, "$0x%04" PRIX16 ":", op->data.absolute.segment,
				len );
			if (op->datatype == op_descr16) {
                        	STRNCATF( buf, "0x%04" PRIX16, 
					op->data.absolute.offset.off16, len );
			} else {
                        	STRNCATF( buf, "0x%08" PRIX32, 
					op->data.absolute.offset.off32, len );
			}
                        break;

                case op_offset:
                        len -= format_seg( op, buf, len, native_syntax );
                        STRNCATF( buf, "[0x%08" PRIX32 "]", op->data.sdword, len );
                        break;

                case op_expression:
                        len -= format_seg( op, buf, len, native_syntax );
                        len -= format_expr( &op->data.expression, buf, len,
                                     native_syntax );
                        break;
                case op_unused:
                case op_unknown:
                        /* return 0-truncated buffer */
                        break;
        }

        return( strlen( buf ) );
}

static int format_operand_xml( x86_op_t *op, x86_insn_t *insn, char *buf,
                               int len){

        char str[MAX_OP_STRING] = "\0";

        switch (op->type) {
                case op_register:

                        get_operand_regtype_str( op->data.reg.type, str,
                                                 sizeof str );

                        STRNCAT( buf, "\t\t<register ", len );
                        STRNCATF( buf, "name=\"%s\" ", op->data.reg.name, len );
                        STRNCATF( buf, "type=\"%s\" ", str, len );
                        STRNCATF( buf, "size=%d/>\n", op->data.reg.size, len );
                        break;

                case op_immediate:

                        get_operand_data_str( op, str, sizeof str );

                        STRNCAT( buf, "\t\t<immediate ", len );
                        STRNCATF( buf, "type=\"%s\" ",
                                  get_operand_datatype_str (op), len );
                        STRNCATF( buf, "value=\"%s\"/>\n", str, len );
                        break;

                case op_relative_near:
                        STRNCAT( buf, "\t\t<relative_offset ", len );

                        STRNCATF( buf, "value=\"0x%08" PRIX32 "\"/>\n",
                                  (unsigned int)(op->data.sbyte +
                                  insn->addr + insn->size), len );
                        break;

                case op_relative_far:
                        STRNCAT( buf, "\t\t<relative_offset ", len );

                        if (op->datatype == op_word) {
                                STRNCATF( buf, "value=\"0x%08" PRIX32 "\"/>\n",
                                          (unsigned int)(op->data.sword +
                                          insn->addr + insn->size), len);
                                break;
                        } else {

                        	STRNCATF( buf, "value=\"0x%08" PRIX32 "\"/>\n",
                                      op->data.sdword + insn->addr + insn->size,
                                      len );
			}
                        break;

                case op_absolute:

                        STRNCATF( buf, 
				"\t\t<absolute_address segment=\"0x%04" PRIX16 "\"",
                        	op->data.absolute.segment, len );

			if (op->datatype == op_descr16) {
                        	STRNCATF( buf, "offset=\"0x%04" PRIX16 "\">", 
					op->data.absolute.offset.off16, len );
			} else {
                        	STRNCATF( buf, "offset=\"0x%08" PRIX32 "\">", 
					op->data.absolute.offset.off32, len );
			}

                        STRNCAT( buf, "\t\t</absolute_address>\n", len );
                        break;

                case op_expression:
			

                        STRNCAT( buf, "\t\t<address_expression>\n", len );

                        len -= format_seg( op, buf, len, xml_syntax );
                        len -= format_expr( &op->data.expression, buf, len,
                                     xml_syntax );

                        STRNCAT( buf, "\t\t</address_expression>\n", len );
                        break;

                case op_offset:

                        STRNCAT( buf, "\t\t<segment_offset>\n", len );

                        len -= format_seg( op, buf, len, xml_syntax );

                        STRNCAT( buf, "\t\t\t<address ", len);
                        STRNCATF( buf, "value=\"0x%08" PRIX32 "\"/>\n",
                                          op->data.sdword, len );
                        STRNCAT( buf, "\t\t</segment_offset>\n", len );
                        break;

                case op_unused:
                case op_unknown:
                        /* return 0-truncated buffer */
                        break;
        }

        return( strlen( buf ) );
}

static int format_operand_raw( x86_op_t *op, x86_insn_t *insn, char *buf,
                               int len){

        char str[MAX_OP_RAW_STRING];
	const char *datatype = get_operand_datatype_str(op);

        switch (op->type) {
                case op_register:

                        get_operand_regtype_str( op->data.reg.type, str,
                                                 sizeof str );

                        STRNCAT( buf, "reg|", len );
                        STRNCATF( buf, "%s|", datatype, len );
                        STRNCATF( buf, "%s:", op->data.reg.name, len );
                        STRNCATF( buf, "%s:", str, len );
                        STRNCATF( buf, "%d|", op->data.reg.size, len );
                        break;

                case op_immediate:

                        get_operand_data_str( op, str, sizeof str );

                        STRNCAT( buf, "immediate|", len );
                        STRNCATF( buf, "%s|", datatype, len );
                        STRNCATF( buf, "%s|", str, len );
                        break;

                case op_relative_near:
			/* NOTE: in raw format, we print the
			 * relative offset, not the actual
			 * address of the jump target */

                        STRNCAT( buf, "relative|", len );
                        STRNCATF( buf, "%s|", datatype, len );
                        STRNCATF( buf, "%" PRId8 "|", op->data.sbyte, len );
                        break;

                case op_relative_far:

                        STRNCAT( buf, "relative|", len );
                        STRNCATF( buf, "%s|", datatype, len );

                        if (op->datatype == op_word) {
                                STRNCATF( buf, "%" PRId16 "|", op->data.sword, len);
                                break;
                        } else {
                        	STRNCATF( buf, "%" PRId32 "|", op->data.sdword, len );
			}
                        break;

                case op_absolute:

                        STRNCAT( buf, "absolute_address|", len );
                        STRNCATF( buf, "%s|", datatype, len );

                        STRNCATF( buf, "$0x%04" PRIX16 ":", op->data.absolute.segment,
				len );
			if (op->datatype == op_descr16) {
                        	STRNCATF( buf, "0x%04" PRIX16 "|", 
					op->data.absolute.offset.off16, len );
			} else {
                        	STRNCATF( buf, "0x%08" PRIX32 "|", 
					op->data.absolute.offset.off32, len );
			}

                        break;

                case op_expression:

                        STRNCAT( buf, "address_expression|", len );
                        STRNCATF( buf, "%s|", datatype, len );

                        len -= format_seg( op, buf, len, native_syntax );
                        len -= format_expr( &op->data.expression, buf, len,
                                     raw_syntax );

                        STRNCAT( buf, "|", len );
                        break;

                case op_offset:

                        STRNCAT( buf, "segment_offset|", len );
                        STRNCATF( buf, "%s|", datatype, len );

                        len -= format_seg( op, buf, len, xml_syntax );

                        STRNCATF( buf, "%08" PRIX32 "|", op->data.sdword, len );
                        break;

                case op_unused:
                case op_unknown:
                        /* return 0-truncated buffer */
                        break;
        }

        return( strlen( buf ) );
}

int x86_format_operand( x86_op_t *op, char *buf, int len,
                        enum x86_asm_format format ){
	x86_insn_t *insn;

        if ( ! op || ! buf || len < 1 ) {
                return(0);
        }

	/* insn is stored in x86_op_t since .21-pre3 */
	insn = (x86_insn_t *) op->insn;

        memset( buf, 0, len );

        switch ( format ) {
                case att_syntax:
                        return format_operand_att( op, insn, buf, len );
                case xml_syntax:
                        return format_operand_xml( op, insn, buf, len );
                case raw_syntax:
                        return format_operand_raw( op, insn, buf, len );
                case native_syntax:
                case intel_syntax:
                default:
                        return format_operand_native( op, insn, buf, len );
        }
}

#define is_imm_jmp(op)   (op->type == op_absolute   || \
                          op->type == op_immediate  || \
                          op->type == op_offset)
#define is_memory_op(op) (op->type == op_absolute   || \
                          op->type == op_expression || \
                          op->type == op_offset)

static int format_att_mnemonic( x86_insn_t *insn, char *buf, int len) {
        int size = 0;
        const char *suffix;

        if (! insn || ! buf || ! len )
                return(0);

        memset( buf, 0, len );

        /* do long jump/call prefix */
        if ( insn->type == insn_jmp || insn->type == insn_call ) {
                if (! is_imm_jmp( x86_operand_1st(insn) ) ||
                     (x86_operand_1st(insn))->datatype != op_byte ) {
                    /* far jump/call, use "l" prefix */
                    STRNCAT( buf, "l", len );
                }
                STRNCAT( buf, insn->mnemonic, len );

                return ( strlen( buf ) );
        }

        /* do mnemonic */
        STRNCAT( buf, insn->mnemonic, len );

        /* do suffixes for memory operands */
        if (!(insn->note & insn_note_nosuffix) &&
            (insn->group == insn_arithmetic ||
             insn->group == insn_logic ||
             insn->group == insn_move ||
             insn->group == insn_stack ||
             insn->group == insn_string ||
             insn->group == insn_comparison ||
             insn->type == insn_in ||
             insn->type == insn_out
            )) {
            if ( x86_operand_count( insn, op_explicit ) > 0 &&
                 is_memory_op( x86_operand_1st(insn) ) ){
                size = x86_operand_size( x86_operand_1st( insn ) );
            } else if ( x86_operand_count( insn, op_explicit ) > 1 &&
                        is_memory_op( x86_operand_2nd(insn) ) ){
                size = x86_operand_size( x86_operand_2nd( insn ) );
            }
        }

        if ( size == 1 ) suffix = "b";
        else if ( size == 2 ) suffix = "w";
        else if ( size == 4 ) suffix = "l";
        else if ( size == 8 ) suffix = "q";
        else suffix = "";

        STRNCAT( buf, suffix, len );
        return ( strlen( buf ) );
}

int x86_format_mnemonic(x86_insn_t *insn, char *buf, int len,
                        enum x86_asm_format format){
        char str[MAX_OP_STRING];

        memset( buf, 0, len );
        STRNCAT( buf, insn->prefix_string, len );
        if ( format == att_syntax ) {
                format_att_mnemonic( insn, str, sizeof str );
                STRNCAT( buf, str, len );
        } else {
                STRNCAT( buf, insn->mnemonic, len );
        }

        return( strlen( buf ) );
}

struct op_string { char *buf; size_t len; };

static void format_op_raw( x86_op_t *op, x86_insn_t *insn, void *arg ) {
	struct op_string * opstr = (struct op_string *) arg;

        format_operand_raw(op, insn, opstr->buf, opstr->len);
}

static int format_insn_note(x86_insn_t *insn, char *buf, int len){
	char note[32] = {0};
	int len_orig = len, note_len = 32;

	if ( insn->note & insn_note_ring0 ) {
        	STRNCATF( note, "%s", "Ring0 ", note_len );
	}
	if ( insn->note & insn_note_smm ) {
        	STRNCATF( note, "%s", "SMM ", note_len );
	}
	if ( insn->note & insn_note_serial ) {
        	STRNCATF(note, "%s", "Serialize ", note_len );
	}
        STRNCATF( buf, "%s|", note, len );

        return( len_orig - len );
}

static int format_raw_insn( x86_insn_t *insn, char *buf, int len ){
	struct op_string opstr = { buf, len };
        int i;

        /* RAW style:
         * ADDRESS|OFFSET|SIZE|BYTES|
         * PREFIX|PREFIX_STRING|GROUP|TYPE|NOTES|
	 * MNEMONIC|CPU|ISA|FLAGS_SET|FLAGS_TESTED|
	 * STACK_MOD|STACK_MOD_VAL
	 * [|OP_TYPE|OP_DATATYPE|OP_ACCESS|OP_FLAGS|OP]*
         *
         * Register values are encoded as:
         * NAME:TYPE:SIZE
         *
         * Effective addresses are encoded as:
         * disp(base_reg,index_reg,scale)
         */
        STRNCATF( buf, "0x%08" PRIX32 "|", insn->addr  , len );
        STRNCATF( buf, "0x%08" PRIX32 "|", insn->offset, len );
        STRNCATF( buf, "%d|"    , insn->size  , len );

        /* print bytes */
        for ( i = 0; i < insn->size; i++ ) {
                STRNCATF( buf, "%02X ", insn->bytes[i], len );
        }
        STRNCAT( buf, "|", len );

        len -= format_insn_prefix_str( insn->prefix, buf, len );
        STRNCATF( buf, "|%s|", insn->prefix_string             , len );
        STRNCATF( buf, "%s|", get_insn_group_str( insn->group ), len );
        STRNCATF( buf, "%s|", get_insn_type_str( insn->type )  , len );
        STRNCATF( buf, "%s|", insn->mnemonic                   , len );
        STRNCATF( buf, "%s|", get_insn_cpu_str( insn->cpu )  , len );
        STRNCATF( buf, "%s|", get_insn_isa_str( insn->isa )  , len );

	/* insn note */
	len -= format_insn_note( insn, buf, len );

        len -= format_insn_eflags_str( insn->flags_set, buf, len );
        STRNCAT( buf, "|", len );
        len -= format_insn_eflags_str( insn->flags_tested, buf, len );
        STRNCAT( buf, "|", len );
        STRNCATF( buf, "%d|", insn->stack_mod, len );
        STRNCATF( buf, "%" PRId32 "|", insn->stack_mod_val, len );

	opstr.len = len;
	x86_operand_foreach( insn, format_op_raw, &opstr, op_any );

        return( strlen (buf) );
}

static int format_xml_insn( x86_insn_t *insn, char *buf, int len ) {
        char str[MAX_OP_XML_STRING];
        int i;

        STRNCAT( buf, "<x86_insn>\n", len );

        STRNCATF( buf, "\t<address rva=\"0x%08" PRIX32 "\" ", insn->addr, len );
        STRNCATF( buf, "offset=\"0x%08" PRIX32 "\" ", insn->offset, len );
        STRNCATF( buf, "size=%d bytes=\"", insn->size, len );

        for ( i = 0; i < insn->size; i++ ) {
                STRNCATF( buf, "%02X ", insn->bytes[i], len );
        }
        STRNCAT( buf, "\"/>\n", len );

        STRNCAT( buf, "\t<prefix type=\"", len );
        len -= format_insn_prefix_str( insn->prefix, buf, len );
        STRNCATF( buf, "\" string=\"%s\"/>\n", insn->prefix_string, len );

        STRNCATF( buf, "\t<mnemonic group=\"%s\" ",
                  get_insn_group_str (insn->group), len );
        STRNCATF( buf, "type=\"%s\" ", get_insn_type_str (insn->type), len );
        STRNCATF( buf, "string=\"%s\"/>\n", insn->mnemonic, len );

        STRNCAT( buf, "\t<flags type=set>\n", len );
        STRNCAT( buf, "\t\t<flag name=\"", len );
        len -= format_insn_eflags_str( insn->flags_set, buf, len );
        STRNCAT( buf, "\"/>\n\t</flags>\n", len );


        STRNCAT( buf, "\t<flags type=tested>\n", len );
        STRNCAT( buf, "\t\t<flag name=\"", len );
        len -= format_insn_eflags_str( insn->flags_tested, buf, len );
        STRNCAT( buf, "\"/>\n\t</flags>\n", len );

	if ( x86_operand_1st( insn ) ) {
        	x86_format_operand( x86_operand_1st(insn), str,
                           sizeof str, xml_syntax);
        	STRNCAT( buf, "\t<operand name=dest>\n", len );
        	STRNCAT( buf, str, len );
        	STRNCAT( buf, "\t</operand>\n", len );
	}

	if ( x86_operand_2nd( insn ) ) {
        	x86_format_operand( x86_operand_2nd( insn ), str,
                           sizeof str, xml_syntax);
        	STRNCAT( buf, "\t<operand name=src>\n", len );
        	STRNCAT( buf, str, len );
        	STRNCAT( buf, "\t</operand>\n", len );
	}

	if ( x86_operand_3rd( insn ) ) {
        	x86_format_operand( x86_operand_3rd(insn), str,
                           sizeof str, xml_syntax);
        	STRNCAT( buf, "\t<operand name=imm>\n", len );
        	STRNCAT( buf, str, len );
        	STRNCAT( buf, "\t</operand>\n", len );
	}

        STRNCAT( buf, "</x86_insn>\n", len );

        return strlen (buf);
}

int x86_format_header( char *buf, int len, enum x86_asm_format format ) {
        switch (format) {
                case att_syntax:
                        snprintf( buf, len, "MNEMONIC\tSRC, DEST, IMM" );
                        break;
                case intel_syntax:
                        snprintf( buf, len, "MNEMONIC\tDEST, SRC, IMM" );
                        break;
                case native_syntax:
                        snprintf( buf, len, "ADDRESS\tBYTES\tMNEMONIC\t"
                                            "DEST\tSRC\tIMM" );
                        break;
                case raw_syntax:
                        snprintf( buf, len, "ADDRESS|OFFSET|SIZE|BYTES|"
                               "PREFIX|PREFIX_STRING|GROUP|TYPE|NOTES|"
			       "MNEMONIC|CPU|ISA|FLAGS_SET|FLAGS_TESTED|"
			       "STACK_MOD|STACK_MOD_VAL"
			       "[|OP_TYPE|OP_DATATYPE|OP_ACCESS|OP_FLAGS|OP]*"
                                );
                        break;
                case xml_syntax:
                        snprintf( buf, len,
                                  "<x86_insn>"
                                      "<address rva= offset= size= bytes=/>"
                                      "<prefix type= string=/>"
                                      "<mnemonic group= type= string= "
				      "cpu= isa= note= />"
                                      "<flags type=set>"
                                          "<flag name=>"
                                      "</flags>"
				      "<stack_mod val= >"
                                      "<flags type=tested>"
                                          "<flag name=>"
                                      "</flags>"
                                      "<operand name=>"
                                          "<register name= type= size=/>"
                                          "<immediate type= value=/>"
                                          "<relative_offset value=/>"
                                          "<absolute_address value=>"
                                              "<segment value=/>"
                                          "</absolute_address>"
                                          "<address_expression>"
                                              "<segment value=/>"
                                              "<base>"
                                                  "<register name= type= size=/>"
                                              "</base>"
                                              "<index>"
                                                  "<register name= type= size=/>"
                                              "</index>"
                                              "<scale>"
                                                  "<immediate value=/>"
                                              "</scale>"
                                              "<displacement>"
                                                  "<immediate value=/>"
                                                  "<address value=/>"
                                              "</displacement>"
                                          "</address_expression>"
                                          "<segment_offset>"
                                              "<address value=/>"
                                          "</segment_offset>"
                                      "</operand>"
                                  "</x86_insn>"
                                );
                        break;
		case unknown_syntax:
			if ( len ) {
				buf[0] = '\0';
			}
			break;
        }

        return( strlen(buf) );
}

int x86_format_insn( x86_insn_t *insn, char *buf, int len,
                     enum x86_asm_format format ){
        char str[MAX_OP_STRING];
        x86_op_t *src, *dst;
        int i;

        memset(buf, 0, len);
        if ( format == intel_syntax ) {
                /* INTEL STYLE: mnemonic dest, src, imm */
                STRNCAT( buf, insn->prefix_string, len );
                STRNCAT( buf, insn->mnemonic, len );
                STRNCAT( buf, "\t", len );

                /* dest */
		if ( (dst = x86_operand_1st( insn )) && !(dst->flags & op_implied) ) {
        		x86_format_operand( dst, str, MAX_OP_STRING, format);
                	STRNCAT( buf, str, len );
                }

                /* src */
		if ( (src = x86_operand_2nd( insn )) ) {
                        if ( !(dst->flags & op_implied) ) {
                	        STRNCAT( buf, ", ", len );
                        }
        		x86_format_operand( src, str, MAX_OP_STRING, format);
                	STRNCAT( buf, str, len );
                }

                /* imm */
		if ( x86_operand_3rd( insn )) {
                	STRNCAT( buf, ", ", len );
        		x86_format_operand( x86_operand_3rd( insn ), 
				str, MAX_OP_STRING, format);
                	STRNCAT( buf, str, len );
		}

        } else if ( format == att_syntax ) {
                /* ATT STYLE: mnemonic src, dest, imm */
                STRNCAT( buf, insn->prefix_string, len );
                format_att_mnemonic(insn, str, MAX_OP_STRING);
                STRNCATF( buf, "%s\t", str, len);


		/* not sure which is correct? sometimes GNU as requires
		 * an imm as the first operand, sometimes as the third... */
                /* imm */
		if ( x86_operand_3rd( insn ) ) {
        		x86_format_operand(x86_operand_3rd( insn ), 
				str, MAX_OP_STRING, format);
                	STRNCAT( buf, str, len );
			/* there is always 'dest' operand if there is 'src' */
			STRNCAT( buf, ", ", len );
		}

                if ( (insn->note & insn_note_nonswap ) == 0 ) {
                        /* regular AT&T style swap */
                        src = x86_operand_2nd( insn );
                        dst = x86_operand_1st( insn );
                }
                else {
                        /* special-case instructions */
                        src = x86_operand_1st( insn );
                        dst = x86_operand_2nd( insn );
                }

                /* src */
                if ( src ) {
                        x86_format_operand(src, str, MAX_OP_STRING, format);
                        STRNCAT( buf, str, len );
                        /* there is always 'dest' operand if there is 'src' */
                        if ( dst && !(dst->flags & op_implied) ) {
                                STRNCAT( buf, ", ", len );
                        }
                }

                /* dest */
                if ( dst && !(dst->flags & op_implied) ) {
                        x86_format_operand( dst, str, MAX_OP_STRING, format);
                        STRNCAT( buf, str, len );
                }


        } else if ( format == raw_syntax ) {
                format_raw_insn( insn, buf, len );
        } else if ( format == xml_syntax ) {
                format_xml_insn( insn, buf, len );
        } else { /* default to native */
                /* NATIVE style: RVA\tBYTES\tMNEMONIC\tOPERANDS */
                /* print address */
                STRNCATF( buf, "%08" PRIX32 "\t", insn->addr, len );

                /* print bytes */
                for ( i = 0; i < insn->size; i++ ) {
                        STRNCATF( buf, "%02X ", insn->bytes[i], len );
                }

                STRNCAT( buf, "\t", len );

                /* print mnemonic */
                STRNCAT( buf, insn->prefix_string, len );
                STRNCAT( buf, insn->mnemonic, len );
                STRNCAT( buf, "\t", len );

                /* print operands */
                /* dest */
		if ( x86_operand_1st( insn )  ) {
        		x86_format_operand( x86_operand_1st( insn ), 
				str, MAX_OP_STRING, format);
                	STRNCATF( buf, "%s\t", str, len );
		}

                /* src */
		if ( x86_operand_2nd( insn ) ) {
        		x86_format_operand(x86_operand_2nd( insn ), 
				str, MAX_OP_STRING, format);
                	STRNCATF( buf, "%s\t", str, len );
		}

                /* imm */
		if ( x86_operand_3rd( insn )) {
        		x86_format_operand( x86_operand_3rd( insn ), 
				str, MAX_OP_STRING, format);
                	STRNCAT( buf, str, len );
		}
        }

        return( strlen( buf ) );
}

