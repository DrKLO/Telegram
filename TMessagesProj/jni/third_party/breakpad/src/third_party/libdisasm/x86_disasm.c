#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "libdis.h"
#include "ia32_insn.h"
#include "ia32_invariant.h"
#include "x86_operand_list.h"


#ifdef _MSC_VER
        #define snprintf        _snprintf
        #define inline          __inline
#endif

unsigned int x86_disasm( unsigned char *buf, unsigned int buf_len,
                uint32_t buf_rva, unsigned int offset,
                x86_insn_t *insn ){
        int len, size;
	unsigned char bytes[MAX_INSTRUCTION_SIZE];

        if ( ! buf || ! insn || ! buf_len ) {
                /* caller screwed up somehow */
                return 0;
        }


	/* ensure we are all NULLed up */
	memset( insn, 0, sizeof(x86_insn_t) );
        insn->addr = buf_rva + offset;
        insn->offset = offset;
	/* default to invalid insn */
	insn->type = insn_invalid;
	insn->group = insn_none;

        if ( offset >= buf_len ) {
                /* another caller screwup ;) */
                x86_report_error(report_disasm_bounds, (void*)(long)buf_rva+offset);
                return 0;
        }

        len = buf_len - offset;

	/* copy enough bytes for disassembly into buffer : this
	 * helps prevent buffer overruns at the end of a file */
	memset( bytes, 0, MAX_INSTRUCTION_SIZE );
	memcpy( bytes, &buf[offset], (len < MAX_INSTRUCTION_SIZE) ? len : 
		MAX_INSTRUCTION_SIZE );

        /* actually do the disassembly */
	/* TODO: allow switching when more disassemblers are added */
        size = ia32_disasm_addr( bytes, len, insn);

        /* check and see if we had an invalid instruction */
        if (! size ) {
                x86_report_error(report_invalid_insn, (void*)(long)buf_rva+offset );
                return 0;
        }

        /* check if we overran the end of the buffer */
        if ( size > len ) {
                x86_report_error( report_insn_bounds, (void*)(long)buf_rva + offset );
		MAKE_INVALID( insn, bytes );
		return 0;
	}

        /* fill bytes field of insn */
        memcpy( insn->bytes, bytes, size );

        return size;
}

unsigned int x86_disasm_range( unsigned char *buf, uint32_t buf_rva,
                      unsigned int offset, unsigned int len,
                      DISASM_CALLBACK func, void *arg ) {
        x86_insn_t insn;
        unsigned int buf_len, size, count = 0, bytes = 0;

        /* buf_len is implied by the arguments */
        buf_len = len + offset;

        while ( bytes < len ) {
                size = x86_disasm( buf, buf_len, buf_rva, offset + bytes,
                                   &insn );
                if ( size ) {
                        /* invoke callback if it exists */
                        if ( func ) {
                                (*func)( &insn, arg );
                        }
                        bytes += size;
                        count ++;
                } else {
                        /* error */
                        bytes++;        /* try next byte */
                }

		x86_oplist_free( &insn );
        }

        return( count );
}

static inline int follow_insn_dest( x86_insn_t *insn ) {
        if ( insn->type == insn_jmp || insn->type == insn_jcc ||
             insn->type == insn_call || insn->type == insn_callcc ) {
                return(1);
        }
        return(0);
}

static inline int insn_doesnt_return( x86_insn_t *insn ) {
        return( (insn->type == insn_jmp || insn->type == insn_return) ? 1: 0 );
}

static int32_t internal_resolver( x86_op_t *op, x86_insn_t *insn ){
        int32_t next_addr = -1;
        if ( x86_optype_is_address(op->type) ) {
                next_addr = op->data.sdword;
        } else if ( op->type == op_relative_near ) {
		next_addr = insn->addr + insn->size + op->data.relative_near;
        } else if ( op->type == op_relative_far ) {
		next_addr = insn->addr + insn->size + op->data.relative_far;
        }
        return( next_addr );
}

unsigned int x86_disasm_forward( unsigned char *buf, unsigned int buf_len,
                        uint32_t buf_rva, unsigned int offset,
                        DISASM_CALLBACK func, void *arg,
                        DISASM_RESOLVER resolver, void *r_arg ){
        x86_insn_t insn;
        x86_op_t *op;
        int32_t next_addr;
        uint32_t next_offset;
        unsigned int size, count = 0, bytes = 0, cont = 1;

        while ( cont && bytes < buf_len ) {
                size = x86_disasm( buf, buf_len, buf_rva, offset + bytes,
                           &insn );

                if ( size ) {
                        /* invoke callback if it exists */
                        if ( func ) {
                                (*func)( &insn, arg );
                        }
                        bytes += size;
                        count ++;
                } else {
                        /* error */
                        bytes++;        /* try next byte */
                }

                if ( follow_insn_dest(&insn) ) {
                        op = x86_get_dest_operand( &insn );
                        next_addr = -1;

                        /* if caller supplied a resolver, use it to determine
                         * the address to disassemble */
                        if ( resolver ) {
                                next_addr = resolver(op, &insn, r_arg);
                        } else {
                                next_addr = internal_resolver(op, &insn);
                        }

                        if (next_addr != -1 ) {
                                next_offset = next_addr - buf_rva;
                                /* if offset is in this buffer... */
                                if ( (uint32_t)next_addr >= buf_rva &&
                                     next_offset < buf_len ) {
                                        /* go ahead and disassemble */
                                        count += x86_disasm_forward( buf,
                                                            buf_len,
                                                            buf_rva,
                                                            next_offset,
                                                            func, arg,
                                                            resolver, r_arg );
                                } else  {
                                        /* report unresolved address */
                                        x86_report_error( report_disasm_bounds,
                                                     (void*)(long)next_addr );
                                }
                        }
                } /* end follow_insn */

                if ( insn_doesnt_return(&insn) ) {
                        /* stop disassembling */
                        cont = 0;
                }

		x86_oplist_free( &insn );
        }
        return( count );
}

/* invariant instruction representation */
size_t x86_invariant_disasm( unsigned char *buf, int buf_len, 
		x86_invariant_t *inv ){
	if (! buf || ! buf_len || ! inv  ) {
		return(0);
	}

	return ia32_disasm_invariant(buf, buf_len, inv);
}
size_t x86_size_disasm( unsigned char *buf, unsigned int buf_len ) {
	if (! buf || ! buf_len  ) {
		return(0);
	}

	return ia32_disasm_size(buf, buf_len);
}
