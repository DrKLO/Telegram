#ifdef NDEBUG
#undef NDEBUG
#endif
#include <assert.h>
#include <stdlib.h>
#include <string.h>
#include "libregexp.h"

// If >= 0, the next 'alloc_countdown' allocations succeed and the one after
// that fails. Used to inject out-of-memory conditions in the compiler.
// When alloc_fail_persist is true, every subsequent allocation fails too.
static int alloc_countdown = -1;
static bool alloc_fail_persist;

bool lre_check_stack_overflow(void *opaque, size_t alloca_size)
{
    return false;
}

int lre_check_timeout(void *opaque)
{
    return 0;
}

void *lre_realloc(void *opaque, void *ptr, size_t size)
{
    if (size == 0) {
        free(ptr);
        return NULL;
    }
    if (alloc_countdown >= 0) {
        if (alloc_countdown == 0) {
            if (!alloc_fail_persist)
                alloc_countdown = -1;
            return NULL;
        }
        alloc_countdown--;
    }
    return realloc(ptr, size);
}

// https://github.com/quickjs-ng/quickjs/issues/1375
static void oob_save_index(void)
{
    // Bytecode with REOP_save_start index=100, but capture_count=1.
    // Without validation this causes a heap-buffer-overflow in lre_exec_backtrack.
    uint8_t bc[] = {
        0x00, 0x00,              // RE_HEADER_FLAGS = 0
        0x01,                    // RE_HEADER_CAPTURE_COUNT = 1
        0x00,                    // RE_HEADER_REGISTER_COUNT = 0
        0x04, 0x00, 0x00, 0x00, // RE_HEADER_BYTECODE_LEN = 4 (little-endian)
        0x06,                    // REOP_any
        0x13, 0x64,             // REOP_save_start, index=100
        0x10,                    // REOP_match
    };

    uint8_t *capture[2] = {NULL, NULL};
    int ret = lre_exec(capture, bc, (const uint8_t *)"a", 0, 1, 0, NULL);
    assert(ret < 0);
}

static void oom_no_oob_write(void)
{
    static const struct {
        const char *prefix;
        const char *suffix;
        int flags;
    } templates[] = {
        { "",      "|b",        0                      },
        { "",      "*b",        0                      },
        { "",      "{2,4}b",    0                      },
        { "(",     "|b)",       0                      },
        { "(?:",   "|b){2,7}c", 0                      },
        { "(?=",   "|b)c",      0                      },
        { "(?<=",  "|b)c",      0                      },
        { "[\\q{", "|bc}]",     LRE_FLAG_UNICODE_SETS  },
    };
    char pattern[128], error_msg[128];
    size_t i, prefix_len;
    int k, n, len, persist;
    uint8_t *bc;

    for (i = 0; i < sizeof(templates) / sizeof(templates[0]); i++) {
        prefix_len = strlen(templates[i].prefix);
        for (k = 0; k < 64; k++) {
            memcpy(pattern, templates[i].prefix, prefix_len);
            memset(pattern + prefix_len, 'a', k);
            strcpy(pattern + prefix_len + k, templates[i].suffix);
            for (n = 0; n < 128; n++) {
                for (persist = 0; persist < 2; persist++) {
                    alloc_fail_persist = persist;
                    alloc_countdown = n;
                    error_msg[0] = '\0';
                    bc = lre_compile(&len, error_msg, sizeof(error_msg),
                                     pattern, strlen(pattern),
                                     templates[i].flags, NULL);
                    alloc_countdown = -1;
                    // Failure must always come with an error message.
                    assert(bc || error_msg[0] != '\0');
                    lre_realloc(NULL, bc, 0);
                }
            }
        }
    }
}

int main(void)
{
    oob_save_index();
    oom_no_oob_write();
    return 0;
}
