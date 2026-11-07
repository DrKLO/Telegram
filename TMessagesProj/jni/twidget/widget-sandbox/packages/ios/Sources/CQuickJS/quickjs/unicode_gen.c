/*
 * Generation of Unicode tables
 *
 * Copyright (c) 2017-2018 Fabrice Bellard
 * Copyright (c) 2017-2018 Charlie Gordon
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL
 * THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
#include <stdlib.h>
#include <stdio.h>
#include <stdarg.h>
#include <inttypes.h>
#include <string.h>
#include <assert.h>
#include <ctype.h>
#include <time.h>

#include "cutils.h"

/* define it to be able to test unicode.c */
//#define USE_TEST
/* profile tests */
//#define PROFILE

//#define DUMP_CASE_CONV_TABLE
//#define DUMP_TABLE_SIZE
//#define DUMP_CC_TABLE
//#define DUMP_DECOMP_TABLE
//#define DUMP_CASE_FOLDING_SPECIAL_CASES

/* Ideas:
   - Generalize run length encoding + index for all tables
   - remove redundant tables for ID_start, ID_continue, Case_Ignorable, Cased

   Case conversion:
   - use a single entry for consecutive U/LF runs
   - allow EXT runs of length > 1

   Decomposition:
   - Greek lower case (+1f10/1f10) ?
   - allow holes in B runs
   - suppress more upper / lower case redundancy
*/

#ifdef USE_TEST
#include "libunicode.c"
#endif

#define CHARCODE_MAX 0x10ffff
#define CC_LEN_MAX 3

void *mallocz(size_t size)
{
    void *ptr;
    ptr = malloc(size);
    memset(ptr, 0, size);
    return ptr;
}

const char *get_field(const char *p, int n)
{
    int i;
    for(i = 0; i < n; i++) {
        while (*p != ';' && *p != '\0')
            p++;
        if (*p == '\0')
            return NULL;
        p++;
    }
    return p;
}

const char *get_field_buf(char *buf, size_t buf_size, const char *p, int n)
{
    char *q;
    p = get_field(p, n);
    q = buf;
    while (*p != ';' && *p != '\0') {
        if ((q - buf) < buf_size - 1)
            *q++ = *p;
        p++;
    }
    *q = '\0';
    return buf;
}

void add_char(int **pbuf, int *psize, int *plen, int c)
{
    int len, size, *buf;
    buf = *pbuf;
    size = *psize;
    len = *plen;
    if (len >= size) {
        size = *psize;
        size = max_int(len + 1, size * 3 / 2);
        buf = realloc(buf, sizeof(buf[0]) * size);
        *pbuf = buf;
        *psize = size;
    }
    buf[len++] = c;
    *plen = len;
}

int *get_field_str(int *plen, const char *str, int n)
{
    const char *p;
    int *buf, len, size;
    p = get_field(str, n);
    if (!p) {
        *plen = 0;
        return NULL;
    }
    len = 0;
    size = 0;
    buf = NULL;
    for(;;) {
        while (isspace(*p))
            p++;
        if (!isxdigit(*p))
            break;
        add_char(&buf, &size, &len, strtoul(p, (char **)&p, 16));
    }
    *plen = len;
    return buf;
}

char *get_line(char *buf, int buf_size, FILE *f)
{
    int len;
    if (!fgets(buf, buf_size, f))
        return NULL;
    len = strlen(buf);
    if (len > 0 && buf[len - 1] == '\n')
        buf[len - 1] = '\0';
    return buf;
}

/* hashed set of code point sequences, used to collect the RGI emoji ZWJ
   sequences */
typedef struct REString {
    struct REString *next;
    uint32_t hash;
    uint32_t len;
    uint32_t flags;
    uint32_t buf[];
} REString;

typedef struct {
    uint32_t n_strings;
    uint32_t hash_size;
    int hash_bits;
    REString **hash_table;
} REStringList;

static uint32_t re_string_hash(int len, const uint32_t *buf)
{
    int i;
    uint32_t h;
    h = 1;
    for(i = 0; i < len; i++)
        h = h * 263 + buf[i];
    return h * 0x61C88647;
}

static void re_string_list_init(REStringList *s)
{
    s->n_strings = 0;
    s->hash_size = 0;
    s->hash_bits = 0;
    s->hash_table = NULL;
}

static void re_string_list_free(REStringList *s)
{
    REString *p, *p_next;
    uint32_t i;
    for(i = 0; i < s->hash_size; i++) {
        for(p = s->hash_table[i]; p != NULL; p = p_next) {
            p_next = p->next;
            free(p);
        }
    }
    free(s->hash_table);
}

static REString *re_string_find2(REStringList *s, int len, const uint32_t *buf,
                                uint32_t h0, bool add_flag)
{
    uint32_t h = 0; /* avoid warning */
    REString *p;
    if (s->n_strings != 0) {
        h = h0 >> (32 - s->hash_bits);
        for(p = s->hash_table[h]; p != NULL; p = p->next) {
            if (p->hash == h0 && p->len == (uint32_t)len &&
                !memcmp(p->buf, buf, len * sizeof(buf[0]))) {
                return p;
            }
        }
    }
    /* not found */
    if (!add_flag)
        return NULL;
    /* increase the size of the hash table if needed */
    if ((s->n_strings + 1) > s->hash_size) {
        REString **new_hash_table, *p_next;
        int new_hash_bits;
        uint32_t new_hash_size, i;
        new_hash_bits = max_int(s->hash_bits + 1, 4);
        new_hash_size = 1 << new_hash_bits;
        new_hash_table = mallocz(sizeof(new_hash_table[0]) * new_hash_size);
        for(i = 0; i < s->hash_size; i++) {
            for(p = s->hash_table[i]; p != NULL; p = p_next) {
                p_next = p->next;
                h = p->hash >> (32 - new_hash_bits);
                p->next = new_hash_table[h];
                new_hash_table[h] = p;
            }
        }
        free(s->hash_table);
        s->hash_bits = new_hash_bits;
        s->hash_size = new_hash_size;
        s->hash_table = new_hash_table;
        h = h0 >> (32 - s->hash_bits);
    }

    p = malloc(sizeof(REString) + len * sizeof(buf[0]));
    if (!p) {
        fprintf(stderr, "memory allocation error\n");
        exit(1);
    }
    p->next = s->hash_table[h];
    s->hash_table[h] = p;
    s->n_strings++;
    p->hash = h0;
    p->len = len;
    p->flags = 0;
    memcpy(p->buf, buf, sizeof(buf[0]) * len);
    return p;
}

static REString *re_string_find(REStringList *s, int len, const uint32_t *buf,
                               bool add_flag)
{
    return re_string_find2(s, len, buf, re_string_hash(len, buf), add_flag);
}

static void re_string_add(REStringList *s, int len, const uint32_t *buf)
{
    re_string_find(s, len, buf, true);
}

#define UNICODE_GENERAL_CATEGORY

typedef enum {
#define DEF(id, str) GCAT_ ## id,
#include "unicode_gen_def.h"
#undef DEF
    GCAT_COUNT,
} UnicodeGCEnum1;

static const char *unicode_gc_name[] = {
#define DEF(id, str) #id,
#include "unicode_gen_def.h"
#undef DEF
};

static const char *unicode_gc_short_name[] = {
#define DEF(id, str) str,
#include "unicode_gen_def.h"
#undef DEF
};

#undef UNICODE_GENERAL_CATEGORY

#define UNICODE_SCRIPT

typedef enum {
#define DEF(id, str) SCRIPT_ ## id,
#include "unicode_gen_def.h"
#undef DEF
    SCRIPT_COUNT,
} UnicodeScriptEnum1;

static const char *unicode_script_name[] = {
#define DEF(id, str) #id,
#include "unicode_gen_def.h"
#undef DEF
};

const char *unicode_script_short_name[] = {
#define DEF(id, str) str,
#include "unicode_gen_def.h"
#undef DEF
};

#undef UNICODE_SCRIPT

#define UNICODE_PROP_LIST

typedef enum {
#define DEF(id, str) PROP_ ## id,
#include "unicode_gen_def.h"
#undef DEF
    PROP_COUNT,
} UnicodePropEnum1;

static const char *unicode_prop_name[] = {
#define DEF(id, str) #id,
#include "unicode_gen_def.h"
#undef DEF
};

static const char *unicode_prop_short_name[] = {
#define DEF(id, str) str,
#include "unicode_gen_def.h"
#undef DEF
};

#undef UNICODE_PROP_LIST

#define UNICODE_SEQUENCE_PROP_LIST

typedef enum {
#define DEF(id) SEQUENCE_PROP_ ## id,
#include "unicode_gen_def.h"
#undef DEF
    SEQUENCE_PROP_COUNT,
} UnicodeSequencePropEnum1;

static const char *unicode_sequence_prop_name[] = {
#define DEF(id) #id,
#include "unicode_gen_def.h"
#undef DEF
};

#undef UNICODE_SEQUENCE_PROP_LIST

typedef struct {
    /* case conv */
    uint8_t u_len;
    uint8_t l_len;
    uint8_t f_len;
    int u_data[CC_LEN_MAX]; /* to upper case */
    int l_data[CC_LEN_MAX]; /* to lower case */
    int f_data[CC_LEN_MAX]; /* to case folding */

    uint8_t combining_class;
    uint8_t is_compat:1;
    uint8_t is_excluded:1;
    uint8_t general_category;
    uint8_t script;
    uint8_t script_ext_len;
    uint8_t *script_ext;
    uint32_t prop_bitmap_tab[3];
    /* decomposition */
    int decomp_len;
    int *decomp_data;
} CCInfo;

CCInfo *unicode_db;
REStringList rgi_emoji_zwj_sequence;
DynBuf rgi_emoji_tag_sequence;

int find_name(const char **tab, int tab_len, const char *name)
{
    int i, len, name_len;
    const char *p, *r;

    name_len = strlen(name);
    for(i = 0; i < tab_len; i++) {
        p = tab[i];
        for(;;) {
            r = strchr(p, ',');
            if (!r)
                len = strlen(p);
            else
                len = r - p;
            if (len == name_len && memcmp(p, name, len) == 0)
                return i;
            if (!r)
                break;
            p = r + 1;
        }
    }
    return -1;
}

static int get_prop(uint32_t c, int prop_idx)
{
    return (unicode_db[c].prop_bitmap_tab[prop_idx >> 5] >> (prop_idx & 0x1f)) & 1;
}

static void set_prop(uint32_t c, int prop_idx, int val)
{
    uint32_t mask;
    mask = 1U << (prop_idx & 0x1f);
    if (val)
        unicode_db[c].prop_bitmap_tab[prop_idx >> 5] |= mask;
    else
        unicode_db[c].prop_bitmap_tab[prop_idx >> 5]  &= ~mask;
}

void parse_unicode_data(const char *filename)
{
    FILE *f;
    char line[1024];
    char buf1[256];
    const char *p;
    int code, lc, uc, last_code;
    CCInfo *ci, *tab = unicode_db;

    f = fopen(filename, "rb");
    if (!f) {
        perror(filename);
        exit(1);
    }

    last_code = 0;
    for(;;) {
        if (!get_line(line, sizeof(line), f))
            break;
        p = line;
        while (isspace(*p))
            p++;
        if (*p == '#')
            continue;

        p = get_field(line, 0);
        if (!p)
            continue;
        code = strtoul(p, NULL, 16);
        lc = 0;
        uc = 0;

        p = get_field(line, 12);
        if (p && *p != ';') {
            uc = strtoul(p, NULL, 16);
        }

        p = get_field(line, 13);
        if (p && *p != ';') {
            lc = strtoul(p, NULL, 16);
        }
        ci = &tab[code];
        if (uc > 0 || lc > 0) {
            assert(code <= CHARCODE_MAX);
            if (uc > 0) {
                assert(ci->u_len == 0);
                ci->u_len = 1;
                ci->u_data[0] = uc;
            }
            if (lc > 0) {
                assert(ci->l_len == 0);
                ci->l_len = 1;
                ci->l_data[0] = lc;
            }
        }

        {
            int i;
            get_field_buf(buf1, sizeof(buf1), line, 2);
            i = find_name(unicode_gc_name, countof(unicode_gc_name), buf1);
            if (i < 0) {
                fprintf(stderr, "General category '%s' not found\n",
                        buf1);
                exit(1);
            }
            ci->general_category = i;
        }

        p = get_field(line, 3);
        if (p && *p != ';' && *p != '\0') {
            int cc;
            cc = strtoul(p, NULL, 0);
            if (cc != 0) {
                assert(code <= CHARCODE_MAX);
                ci->combining_class = cc;
                //                printf("%05x: %d\n", code, ci->combining_class);
            }
        }

        p = get_field(line, 5);
        if (p && *p != ';' && *p != '\0') {
            int size;
            assert(code <= CHARCODE_MAX);
            ci->is_compat = 0;
            if (*p == '<') {
                while (*p != '\0' && *p != '>')
                    p++;
                if (*p == '>')
                    p++;
                ci->is_compat = 1;
            }
            size = 0;
            for(;;) {
                while (isspace(*p))
                    p++;
                if (!isxdigit(*p))
                    break;
                add_char(&ci->decomp_data, &size, &ci->decomp_len, strtoul(p, (char **)&p, 16));
            }
        }

        p = get_field(line, 9);
        if (p && *p == 'Y') {
            set_prop(code, PROP_Bidi_Mirrored, 1);
        }

        /* handle ranges */
        get_field_buf(buf1, sizeof(buf1), line, 1);
        if (strstr(buf1, " Last>")) {
            int i;
            //            printf("range: 0x%x-%0x\n", last_code, code);
            assert(ci->decomp_len == 0);
            assert(ci->script_ext_len == 0);
            for(i = last_code + 1; i < code; i++) {
                unicode_db[i] = *ci;
            }
        }
        last_code = code;
    }

    fclose(f);
}

void parse_special_casing(CCInfo *tab, const char *filename)
{
    FILE *f;
    char line[1024];
    const char *p;
    int code;
    CCInfo *ci;

    f = fopen(filename, "rb");
    if (!f) {
        perror(filename);
        exit(1);
    }

    for(;;) {
        if (!get_line(line, sizeof(line), f))
            break;
        p = line;
        while (isspace(*p))
            p++;
        if (*p == '#')
            continue;

        p = get_field(line, 0);
        if (!p)
            continue;
        code = strtoul(p, NULL, 16);
        assert(code <= CHARCODE_MAX);
        ci = &tab[code];

        p = get_field(line, 4);
        if (p) {
            /* locale dependent casing */
            while (isspace(*p))
                p++;
            if (*p != '#' && *p != '\0')
                continue;
        }


        p = get_field(line, 1);
        if (p && *p != ';') {
            ci->l_len = 0;
            for(;;) {
                while (isspace(*p))
                    p++;
                if (*p == ';')
                    break;
                assert(ci->l_len < CC_LEN_MAX);
                ci->l_data[ci->l_len++] = strtoul(p, (char **)&p, 16);
            }

            if (ci->l_len == 1 && ci->l_data[0] == code)
                ci->l_len = 0;
        }

        p = get_field(line, 3);
        if (p && *p != ';') {
            ci->u_len = 0;
            for(;;) {
                while (isspace(*p))
                    p++;
                if (*p == ';')
                    break;
                assert(ci->u_len < CC_LEN_MAX);
                ci->u_data[ci->u_len++] = strtoul(p, (char **)&p, 16);
            }

            if (ci->u_len == 1 && ci->u_data[0] == code)
                ci->u_len = 0;
        }
    }

    fclose(f);
}

void parse_case_folding(CCInfo *tab, const char *filename)
{
    FILE *f;
    char line[1024];
    const char *p;
    int code, status;
    CCInfo *ci;

    f = fopen(filename, "rb");
    if (!f) {
        perror(filename);
        exit(1);
    }

    for(;;) {
        if (!get_line(line, sizeof(line), f))
            break;
        p = line;
        while (isspace(*p))
            p++;
        if (*p == '#')
            continue;

        p = get_field(line, 0);
        if (!p)
            continue;
        code = strtoul(p, NULL, 16);
        assert(code <= CHARCODE_MAX);
        ci = &tab[code];

        p = get_field(line, 1);
        if (!p)
            continue;
        /* locale dependent casing */
        while (isspace(*p))
            p++;
        status = *p;
        if (status != 'C' && status != 'S' && status != 'F')
            continue;

        p = get_field(line, 2);
        assert(p != NULL);
        if (status == 'S') {
            /* we always select the simple case folding and assume it
             * comes after the full case folding case */
            assert(ci->f_len >= 2);
            ci->f_len = 0;
        } else {
            assert(ci->f_len == 0);
        }
        for(;;) {
            while (isspace(*p))
                p++;
            if (*p == ';')
                break;
            assert(ci->l_len < CC_LEN_MAX);
            ci->f_data[ci->f_len++] = strtoul(p, (char **)&p, 16);
        }
    }

    fclose(f);
}

void parse_composition_exclusions(const char *filename)
{
    FILE *f;
    char line[4096], *p;
    uint32_t c0;

    f = fopen(filename, "rb");
    if (!f) {
        perror(filename);
        exit(1);
    }

    for(;;) {
        if (!get_line(line, sizeof(line), f))
            break;
        p = line;
        while (isspace(*p))
            p++;
        if (*p == '#' || *p == '@' || *p == '\0')
            continue;
        c0 = strtoul(p, (char **)&p, 16);
        assert(c0 > 0 && c0 <= CHARCODE_MAX);
        unicode_db[c0].is_excluded = true;
    }
    fclose(f);
}

void parse_derived_core_properties(const char *filename)
{
    FILE *f;
    char line[4096], *p, buf[256], *q;
    uint32_t c0, c1, c;
    int i;

    f = fopen(filename, "rb");
    if (!f) {
        perror(filename);
        exit(1);
    }

    for(;;) {
        if (!get_line(line, sizeof(line), f))
            break;
        p = line;
        while (isspace(*p))
            p++;
        if (*p == '#' || *p == '@' || *p == '\0')
            continue;
        c0 = strtoul(p, (char **)&p, 16);
        if (*p == '.' && p[1] == '.') {
            p += 2;
            c1 = strtoul(p, (char **)&p, 16);
        } else {
            c1 = c0;
        }
        assert(c1 <= CHARCODE_MAX);
        p += strspn(p, " \t");
        if (*p == ';') {
            p++;
            p += strspn(p, " \t");
            q = buf;
            static const char ignore[] = "\t #;"; // includes \0
            while (!memchr(ignore, *p, sizeof(ignore))) {
                if ((q - buf) < sizeof(buf) - 1)
                    *q++ = *p;
                p++;
            }
            *q = '\0';
            i = find_name(unicode_prop_name,
                          countof(unicode_prop_name), buf);
            if (i < 0) {
                if (!strcmp(buf, "Grapheme_Link"))
                    goto next;
                fprintf(stderr, "Property not found: %s\n", buf);
                exit(1);
            }
            for(c = c0; c <= c1; c++) {
                set_prop(c, i, 1);
            }
next: ;
        }
    }
    fclose(f);
}

void parse_derived_norm_properties(const char *filename)
{
    FILE *f;
    char line[4096], *p, buf[256], *q;
    uint32_t c0, c1, c;

    f = fopen(filename, "rb");
    if (!f) {
        perror(filename);
        exit(1);
    }

    for(;;) {
        if (!get_line(line, sizeof(line), f))
            break;
        p = line;
        while (isspace(*p))
            p++;
        if (*p == '#' || *p == '@' || *p == '\0')
            continue;
        c0 = strtoul(p, (char **)&p, 16);
        if (*p == '.' && p[1] == '.') {
            p += 2;
            c1 = strtoul(p, (char **)&p, 16);
        } else {
            c1 = c0;
        }
        assert(c1 <= CHARCODE_MAX);
        p += strspn(p, " \t");
        if (*p == ';') {
            p++;
            p += strspn(p, " \t");
            q = buf;
            while (*p != '\0' && *p != ' ' && *p != '#' && *p != '\t') {
                if ((q - buf) < sizeof(buf) - 1)
                    *q++ = *p;
                p++;
            }
            *q = '\0';
            if (!strcmp(buf, "Changes_When_NFKC_Casefolded")) {
                for(c = c0; c <= c1; c++) {
                    set_prop(c, PROP_Changes_When_NFKC_Casefolded, 1);
                }
            }
        }
    }
    fclose(f);
}

void parse_prop_list(const char *filename)
{
    FILE *f;
    char line[4096], *p, buf[256], *q;
    uint32_t c0, c1, c;
    int i;

    f = fopen(filename, "rb");
    if (!f) {
        perror(filename);
        exit(1);
    }

    for(;;) {
        if (!get_line(line, sizeof(line), f))
            break;
        p = line;
        while (isspace(*p))
            p++;
        if (*p == '#' || *p == '@' || *p == '\0')
            continue;
        c0 = strtoul(p, (char **)&p, 16);
        if (*p == '.' && p[1] == '.') {
            p += 2;
            c1 = strtoul(p, (char **)&p, 16);
        } else {
            c1 = c0;
        }
        assert(c1 <= CHARCODE_MAX);
        p += strspn(p, " \t");
        if (*p == ';') {
            p++;
            p += strspn(p, " \t");
            q = buf;
            while (*p != '\0' && *p != ' ' && *p != '#' && *p != '\t') {
                if ((q - buf) < sizeof(buf) - 1)
                    *q++ = *p;
                p++;
            }
            *q = '\0';
            i = find_name(unicode_prop_name,
                          countof(unicode_prop_name), buf);
            if (i < 0) {
                fprintf(stderr, "Property not found: %s\n", buf);
                exit(1);
            }
            for(c = c0; c <= c1; c++) {
                set_prop(c, i, 1);
            }
        }
    }
    fclose(f);
}

#define SEQ_MAX_LEN 16

static bool is_emoji_modifier(uint32_t c)
{
    return (c >= 0x1f3fb && c <= 0x1f3ff);
}

/* the sequence properties are stored either as a code point property
   (when the sequence can be derived from its first code point) or in a
   dedicated table */
static void add_sequence_prop(int idx, int seq_len, int *seq)
{
    int i;

    assert(idx < SEQUENCE_PROP_COUNT);
    switch(idx) {
    case SEQUENCE_PROP_Basic_Emoji:
        /* convert to 2 properties lists */
        if (seq_len == 1) {
            set_prop(seq[0], PROP_Basic_Emoji1, 1);
        } else if (seq_len == 2 && seq[1] == 0xfe0f) {
            set_prop(seq[0], PROP_Basic_Emoji2, 1);
        } else {
            abort();
        }
        break;
    case SEQUENCE_PROP_RGI_Emoji_Modifier_Sequence:
        /* no table needed: the sequences are Emoji_Modifier_Base followed
           by one of the 5 emoji modifiers */
        assert(seq_len == 2);
        assert(is_emoji_modifier(seq[1]));
        assert(get_prop(seq[0], PROP_Emoji_Modifier_Base));
        break;
    case SEQUENCE_PROP_RGI_Emoji_Flag_Sequence:
        {
            int code;
            assert(seq_len == 2);
            assert(seq[0] >= 0x1F1E6 && seq[0] <= 0x1F1FF);
            assert(seq[1] >= 0x1F1E6 && seq[1] <= 0x1F1FF);
            code = (seq[0] - 0x1F1E6) * 26 + (seq[1] - 0x1F1E6);
            /* XXX: would be more compact with a simple bitmap -> 676 bits */
            set_prop(code, PROP_RGI_Emoji_Flag_Sequence, 1);
        }
        break;
    case SEQUENCE_PROP_RGI_Emoji_ZWJ_Sequence:
        re_string_add(&rgi_emoji_zwj_sequence, seq_len, (uint32_t *)seq);
        break;
    case SEQUENCE_PROP_RGI_Emoji_Tag_Sequence:
        {
            assert(seq_len >= 3);
            assert(seq[0] == 0x1F3F4);
            assert(seq[seq_len - 1] == 0xE007F);
            for(i = 1; i < seq_len - 1; i++) {
                assert(seq[i] >= 0xe0001 && seq[i] <= 0xe007e);
                dbuf_putc(&rgi_emoji_tag_sequence, seq[i] - 0xe0000);
            }
            dbuf_putc(&rgi_emoji_tag_sequence, 0);
        }
        break;
    case SEQUENCE_PROP_Emoji_Keycap_Sequence:
        assert(seq_len == 3);
        assert(seq[1] == 0xfe0f);
        assert(seq[2] == 0x20e3);
        set_prop(seq[0], PROP_Emoji_Keycap_Sequence, 1);
        break;
    default:
        assert(0);
    }
}

/* parse emoji-sequences.txt and emoji-zwj-sequences.txt */
void parse_sequence_prop_list(const char *filename)
{
    FILE *f;
    char line[4096], *p, buf[256], *q, *p_start;
    uint32_t c0, c1, c;
    int idx, seq_len;
    int seq[SEQ_MAX_LEN];

    f = fopen(filename, "rb");
    if (!f) {
        perror(filename);
        exit(1);
    }

    for(;;) {
        if (!get_line(line, sizeof(line), f))
            break;
        p = line;
        while (isspace(*p))
            p++;
        if (*p == '#' || *p == '@' || *p == '\0')
            continue;
        p_start = p;

        /* find the sequence property name */
        p = strchr(p, ';');
        if (!p)
            continue;
        p++;
        p += strspn(p, " \t");
        q = buf;
        while (*p != '\0' && *p != ' ' && *p != '#' && *p != '\t' && *p != ';') {
            if ((size_t)(q - buf) < sizeof(buf) - 1)
                *q++ = *p;
            p++;
        }
        *q = '\0';
        idx = find_name(unicode_sequence_prop_name,
                        countof(unicode_sequence_prop_name), buf);
        if (idx < 0) {
            fprintf(stderr, "Property not found: %s\n", buf);
            exit(1);
        }

        p = p_start;
        c0 = strtoul(p, (char **)&p, 16);
        assert(c0 <= CHARCODE_MAX);

        if (*p == '.' && p[1] == '.') {
            p += 2;
            c1 = strtoul(p, (char **)&p, 16);
            assert(c1 <= CHARCODE_MAX);
            for(c = c0; c <= c1; c++) {
                seq[0] = c;
                add_sequence_prop(idx, 1, seq);
            }
        } else {
            seq_len = 0;
            seq[seq_len++] = c0;
            for(;;) {
                while (isspace(*p))
                    p++;
                if (*p == ';' || *p == '\0')
                    break;
                c0 = strtoul(p, (char **)&p, 16);
                assert(c0 <= CHARCODE_MAX);
                assert(seq_len < countof(seq));
                seq[seq_len++] = c0;
            }
            add_sequence_prop(idx, seq_len, seq);
        }
    }
    fclose(f);
}

void parse_scripts(const char *filename)
{
    FILE *f;
    char line[4096], *p, buf[256], *q;
    uint32_t c0, c1, c;
    int i;

    f = fopen(filename, "rb");
    if (!f) {
        perror(filename);
        exit(1);
    }

    for(;;) {
        if (!get_line(line, sizeof(line), f))
            break;
        p = line;
        while (isspace(*p))
            p++;
        if (*p == '#' || *p == '@' || *p == '\0')
            continue;
        c0 = strtoul(p, (char **)&p, 16);
        if (*p == '.' && p[1] == '.') {
            p += 2;
            c1 = strtoul(p, (char **)&p, 16);
        } else {
            c1 = c0;
        }
        assert(c1 <= CHARCODE_MAX);
        p += strspn(p, " \t");
        if (*p == ';') {
            p++;
            p += strspn(p, " \t");
            q = buf;
            while (*p != '\0' && *p != ' ' && *p != '#' && *p != '\t') {
                if ((q - buf) < sizeof(buf) - 1)
                    *q++ = *p;
                p++;
            }
            *q = '\0';
            i = find_name(unicode_script_name,
                          countof(unicode_script_name), buf);
            if (i < 0) {
                fprintf(stderr, "Unknown script: '%s'\n", buf);
                exit(1);
            }
            for(c = c0; c <= c1; c++)
                unicode_db[c].script = i;
        }
    }
    fclose(f);
}

void parse_script_extensions(const char *filename)
{
    FILE *f;
    char line[4096], *p, buf[256], *q;
    uint32_t c0, c1, c;
    int i;
    uint8_t script_ext[255];
    int script_ext_len;

    f = fopen(filename, "rb");
    if (!f) {
        perror(filename);
        exit(1);
    }

    for(;;) {
        if (!get_line(line, sizeof(line), f))
            break;
        p = line;
        while (isspace(*p))
            p++;
        if (*p == '#' || *p == '@' || *p == '\0')
            continue;
        c0 = strtoul(p, (char **)&p, 16);
        if (*p == '.' && p[1] == '.') {
            p += 2;
            c1 = strtoul(p, (char **)&p, 16);
        } else {
            c1 = c0;
        }
        assert(c1 <= CHARCODE_MAX);
        p += strspn(p, " \t");
        script_ext_len = 0;
        if (*p == ';') {
            p++;
            for(;;) {
                p += strspn(p, " \t");
                q = buf;
                while (*p != '\0' && *p != ' ' && *p != '#' && *p != '\t') {
                    if ((q - buf) < sizeof(buf) - 1)
                        *q++ = *p;
                    p++;
                }
                *q = '\0';
                if (buf[0] == '\0')
                    break;
                i = find_name(unicode_script_short_name,
                              countof(unicode_script_short_name), buf);
                if (i < 0) {
                    fprintf(stderr, "Script not found: %s\n", buf);
                    exit(1);
                }
                assert(script_ext_len < sizeof(script_ext));
                script_ext[script_ext_len++] = i;
            }
            for(c = c0; c <= c1; c++) {
                CCInfo *ci = &unicode_db[c];
                ci->script_ext_len = script_ext_len;
                ci->script_ext = malloc(sizeof(ci->script_ext[0]) * script_ext_len);
                for(i = 0; i < script_ext_len; i++)
                    ci->script_ext[i] = script_ext[i];
            }
        }
    }
    fclose(f);
}

void dump_cc_info(CCInfo *ci, int i)
{
    int j;
    printf("%05x:", i);
    if (ci->u_len != 0) {
        printf(" U:");
        for(j = 0; j < ci->u_len; j++)
            printf(" %05x", ci->u_data[j]);
    }
    if (ci->l_len != 0) {
        printf(" L:");
        for(j = 0; j < ci->l_len; j++)
            printf(" %05x", ci->l_data[j]);
    }
    if (ci->f_len != 0) {
        printf(" F:");
        for(j = 0; j < ci->f_len; j++)
            printf(" %05x", ci->f_data[j]);
    }
    printf("\n");
}

void dump_unicode_data(CCInfo *tab)
{
    int i;
    CCInfo *ci;
    for(i = 0; i <= CHARCODE_MAX; i++) {
        ci = &tab[i];
        if (ci->u_len != 0 || ci->l_len != 0 || ci->f_len != 0) {
            dump_cc_info(ci, i);
        }
    }
}

bool is_complicated_case(const CCInfo *ci)
{
    return (ci->u_len > 1 || ci->l_len > 1 ||
            (ci->u_len > 0 && ci->l_len > 0) ||
            (ci->f_len != ci->l_len) ||
            (memcmp(ci->f_data, ci->l_data, ci->f_len * sizeof(ci->f_data[0])) != 0));
}

#ifndef USE_TEST
enum {
    RUN_TYPE_U,
    RUN_TYPE_L,
    RUN_TYPE_UF,
    RUN_TYPE_LF,
    RUN_TYPE_UL,
    RUN_TYPE_LSU,
    RUN_TYPE_U2L_399_EXT2,
    RUN_TYPE_UF_D20,
    RUN_TYPE_UF_D1_EXT,
    RUN_TYPE_U_EXT,
    RUN_TYPE_LF_EXT,
    RUN_TYPE_UF_EXT2,
    RUN_TYPE_LF_EXT2,
    RUN_TYPE_UF_EXT3,
};
#endif

const char *run_type_str[] = {
    "U",
    "L",
    "UF",
    "LF",
    "UL",
    "LSU",
    "U2L_399_EXT2",
    "UF_D20",
    "UF_D1_EXT",
    "U_EXT",
    "LF_EXT",
    "UF_EXT2",
    "LF_EXT2",
    "UF_EXT3",
};

typedef struct {
    int code;
    int len;
    int type;
    int data;
    int ext_len;
    int ext_data[3];
    int data_index; /* 'data' coming from the table */
} TableEntry;

static int simple_to_lower(CCInfo *tab, int c)
{
    if (tab[c].l_len != 1)
        return c;
    return tab[c].l_data[0];
}

/* code (17), len (7), type (4) */

void find_run_type(TableEntry *te, CCInfo *tab, int code)
{
    int is_lower, len;
    CCInfo *ci, *ci1, *ci2;

    ci = &tab[code];
    ci1 = &tab[code + 1];
    ci2 = &tab[code + 2];
    te->code = code;

    if (ci->l_len == 1 && ci->l_data[0] == code + 2 &&
        ci->f_len == 1 && ci->f_data[0] == ci->l_data[0] &&
        ci->u_len == 0 &&

        ci1->l_len == 1 && ci1->l_data[0] == code + 2 &&
        ci1->f_len == 1 && ci1->f_data[0] == ci1->l_data[0] &&
        ci1->u_len == 1 && ci1->u_data[0] == code &&

        ci2->l_len == 0 &&
        ci2->f_len == 0 &&
        ci2->u_len == 1 && ci2->u_data[0] == code) {
        te->len = 3;
        te->data = 0;
        te->type = RUN_TYPE_LSU;
        return;
    }

    if (is_complicated_case(ci)) {
        len = 1;
        while (code + len <= CHARCODE_MAX) {
            ci1 = &tab[code + len];
            if (ci1->u_len != 1 ||
                ci1->u_data[0] != ci->u_data[0] + len ||
                ci1->l_len != 0 ||
                ci1->f_len != 1 || ci1->f_data[0] != ci1->u_data[0])
                break;
            len++;
        }
        if (len > 1) {
            te->len = len;
            te->type = RUN_TYPE_UF;
            te->data = ci->u_data[0];
            return;
        }

        if (ci->l_len == 0 &&
            ci->u_len == 2 && ci->u_data[1] == 0x399 &&
            ci->f_len == 2 && ci->f_data[1] == 0x3B9 &&
            ci->f_data[0] == simple_to_lower(tab, ci->u_data[0])) {
            len = 1;
            while (code + len <= CHARCODE_MAX) {
                ci1 = &tab[code + len];
                if (!(ci1->u_len == 2 &&
                      ci1->u_data[1] == ci->u_data[1] &&
                      ci1->u_data[0] == ci->u_data[0] + len &&
                      ci1->f_len == 2 &&
                      ci1->f_data[1] == ci->f_data[1] &&
                      ci1->f_data[0] == ci->f_data[0] + len &&
                      ci1->l_len == 0))
                    break;
                len++;
            }
            te->len = len;
            te->type = RUN_TYPE_UF_EXT2;
            te->ext_data[0] = ci->u_data[0];
            te->ext_data[1] = ci->u_data[1];
            te->ext_len = 2;
            return;
        }

        if (ci->u_len == 2 && ci->u_data[1] == 0x399 &&
            ci->l_len == 1 &&
            ci->f_len == 1 && ci->f_data[0] == ci->l_data[0]) {
            len = 1;
            while (code + len <= CHARCODE_MAX) {
                ci1 = &tab[code + len];
                if (!(ci1->u_len == 2 &&
                      ci1->u_data[1] == 0x399 &&
                      ci1->u_data[0] == ci->u_data[0] + len &&
                      ci1->l_len == 1 &&
                      ci1->l_data[0] == ci->l_data[0] + len &&
                      ci1->f_len == 1 && ci1->f_data[0] == ci1->l_data[0]))
                    break;
                len++;
            }
            te->len = len;
            te->type = RUN_TYPE_U2L_399_EXT2;
            te->ext_data[0] = ci->u_data[0];
            te->ext_data[1] = ci->l_data[0];
            te->ext_len = 2;
            return;
        }

        if (ci->l_len == 1 && ci->u_len == 0 && ci->f_len == 0) {
            len = 1;
            while (code + len <= CHARCODE_MAX) {
                ci1 = &tab[code + len];
                if (!(ci1->l_len == 1 &&
                      ci1->l_data[0] == ci->l_data[0] + len &&
                      ci1->u_len == 0 && ci1->f_len == 0))
                    break;
                len++;
            }
            te->len = len;
            te->type = RUN_TYPE_L;
            te->data = ci->l_data[0];
            return;
        }

        if (ci->l_len == 0 &&
            ci->u_len == 1 &&
            ci->u_data[0] < 0x1000 &&
            ci->f_len == 1 && ci->f_data[0] == ci->u_data[0] + 0x20) {
            te->len = 1;
            te->type = RUN_TYPE_UF_D20;
            te->data = ci->u_data[0];
        } else if (ci->l_len == 0 &&
                   ci->u_len == 1 &&
                   ci->f_len == 1 && ci->f_data[0] == ci->u_data[0] + 1) {
            te->len = 1;
            te->type = RUN_TYPE_UF_D1_EXT;
            te->ext_data[0] = ci->u_data[0];
            te->ext_len = 1;
        } else if (ci->l_len == 2 && ci->u_len == 0 && ci->f_len == 2 &&
                   ci->l_data[0] == ci->f_data[0] &&
                   ci->l_data[1] == ci->f_data[1]) {
            te->len = 1;
            te->type = RUN_TYPE_LF_EXT2;
            te->ext_data[0] = ci->l_data[0];
            te->ext_data[1] = ci->l_data[1];
            te->ext_len = 2;
        } else if (ci->u_len == 2 && ci->l_len == 0 && ci->f_len == 2 &&
                   ci->f_data[0] == simple_to_lower(tab, ci->u_data[0]) &&
                   ci->f_data[1] == simple_to_lower(tab, ci->u_data[1])) {
            te->len = 1;
            te->type = RUN_TYPE_UF_EXT2;
            te->ext_data[0] = ci->u_data[0];
            te->ext_data[1] = ci->u_data[1];
            te->ext_len = 2;
        } else if (ci->u_len == 3 && ci->l_len == 0 && ci->f_len == 3 &&
                   ci->f_data[0] == simple_to_lower(tab, ci->u_data[0]) &&
                   ci->f_data[1] == simple_to_lower(tab, ci->u_data[1]) &&
                   ci->f_data[2] == simple_to_lower(tab, ci->u_data[2])) {
            te->len = 1;
            te->type = RUN_TYPE_UF_EXT3;
            te->ext_data[0] = ci->u_data[0];
            te->ext_data[1] = ci->u_data[1];
            te->ext_data[2] = ci->u_data[2];
            te->ext_len = 3;
        } else if (ci->u_len == 2 && ci->l_len == 0 && ci->f_len == 1) {
            // U+FB05 LATIN SMALL LIGATURE LONG S T
            assert(code == 0xFB05);
            te->len = 1;
            te->type = RUN_TYPE_UF_EXT2;
            te->ext_data[0] = ci->u_data[0];
            te->ext_data[1] = ci->u_data[1];
            te->ext_len = 2;
        } else if (ci->u_len == 3 && ci->l_len == 0 && ci->f_len == 1) {
            // U+1FD3 GREEK SMALL LETTER IOTA WITH DIALYTIKA AND OXIA or
            // U+1FE3 GREEK SMALL LETTER UPSILON WITH DIALYTIKA AND OXIA
            assert(code == 0x1FD3 || code == 0x1FE3);
            te->len = 1;
            te->type = RUN_TYPE_UF_EXT3;
            te->ext_data[0] = ci->u_data[0];
            te->ext_data[1] = ci->u_data[1];
            te->ext_data[2] = ci->u_data[2];
            te->ext_len = 3;
        } else {
            printf("unsupported encoding case:\n");
            dump_cc_info(ci, code);
            abort();
        }
    } else {
        /* look for a run of identical conversions */
        len = 0;
        for(;;) {
            if (code >= CHARCODE_MAX || len >= 126)
                break;
            ci = &tab[code + len];
            ci1 = &tab[code + len + 1];
            if (is_complicated_case(ci) || is_complicated_case(ci1)) {
                break;
            }
            if (ci->l_len != 1 || ci->l_data[0] != code + len + 1)
                break;
            if (ci1->u_len != 1 || ci1->u_data[0] != code + len)
                break;
            len += 2;
        }
        if (len > 0) {
            te->len = len;
            te->type = RUN_TYPE_UL;
            te->data = 0;
            return;
        }

        ci = &tab[code];
        is_lower = ci->l_len > 0;
        len = 1;
        while (code + len <= CHARCODE_MAX) {
            ci1 = &tab[code + len];
            if (is_complicated_case(ci1))
                break;
            if (is_lower) {
                if (ci1->l_len != 1 ||
                    ci1->l_data[0] != ci->l_data[0] + len)
                    break;
            } else {
                if (ci1->u_len != 1 ||
                    ci1->u_data[0] != ci->u_data[0] + len)
                    break;
            }
            len++;
        }
        te->len = len;
        if (is_lower) {
            te->type = RUN_TYPE_LF;
            te->data = ci->l_data[0];
        } else {
            te->type = RUN_TYPE_U;
            te->data = ci->u_data[0];
        }
    }
}

TableEntry conv_table[1000];
int conv_table_len;
int ext_data[1000];
int ext_data_len;

void dump_case_conv_table1(void)
{
    int i, j;
    const TableEntry *te;

    for(i = 0; i < conv_table_len; i++) {
        te = &conv_table[i];
        printf("%05x %02x %-10s %05x",
               te->code, te->len, run_type_str[te->type], te->data);
        for(j = 0; j < te->ext_len; j++) {
            printf(" %05x", te->ext_data[j]);
        }
        printf("\n");
    }
    printf("table_len=%d ext_len=%d\n", conv_table_len, ext_data_len);
}

int find_data_index(const TableEntry *conv_table, int len, int data)
{
    int i;
    const TableEntry *te;
    for(i = 0; i < len; i++) {
        te = &conv_table[i];
        if (te->code == data)
            return i;
    }
    return -1;
}

int find_ext_data_index(int data)
{
    int i;
    for(i = 0; i < ext_data_len; i++) {
        if (ext_data[i] == data)
            return i;
    }
    assert(ext_data_len < countof(ext_data));
    ext_data[ext_data_len++] = data;
    return ext_data_len - 1;
}

void build_conv_table(CCInfo *tab)
{
    int code, i, j;
    CCInfo *ci;
    TableEntry *te;

    te = conv_table;
    for(code = 0; code <= CHARCODE_MAX; code++) {
        ci = &tab[code];
        if (ci->u_len == 0 && ci->l_len == 0 && ci->f_len == 0)
            continue;
        assert(te - conv_table < countof(conv_table));
        find_run_type(te, tab, code);
        assert(te->len <= 127);
        code += te->len - 1;
        te++;
    }
    conv_table_len = te - conv_table;

    /* find the data index */
    for(i = 0; i < conv_table_len; i++) {
        int data_index;
        te = &conv_table[i];

        switch(te->type) {
        case RUN_TYPE_U:
        case RUN_TYPE_L:
        case RUN_TYPE_UF:
        case RUN_TYPE_LF:
            data_index = find_data_index(conv_table, conv_table_len, te->data);
            if (data_index < 0) {
                switch(te->type) {
                case RUN_TYPE_U:
                    te->type = RUN_TYPE_U_EXT;
                    te->ext_len = 1;
                    te->ext_data[0] = te->data;
                    break;
                case RUN_TYPE_LF:
                    te->type = RUN_TYPE_LF_EXT;
                    te->ext_len = 1;
                    te->ext_data[0] = te->data;
                    break;
                default:
                    printf("%05x: index not found\n", te->code);
                    exit(1);
                }
            } else {
                te->data_index = data_index;
            }
            break;
        case RUN_TYPE_UF_D20:
            te->data_index = te->data;
            break;
        }
    }

    /* find the data index for ext_data */
    for(i = 0; i < conv_table_len; i++) {
        te = &conv_table[i];
        if (te->type == RUN_TYPE_UF_EXT3) {
            int p, v;
            v = 0;
            for(j = 0; j < 3; j++) {
                p = find_ext_data_index(te->ext_data[j]);
                assert(p < 16);
                v = (v << 4) | p;
            }
            te->data_index = v;
        }
    }

    for(i = 0; i < conv_table_len; i++) {
        te = &conv_table[i];
        if (te->type == RUN_TYPE_LF_EXT2 ||
            te->type == RUN_TYPE_UF_EXT2 ||
            te->type == RUN_TYPE_U2L_399_EXT2) {
            int p, v;
            v = 0;
            for(j = 0; j < 2; j++) {
                p = find_ext_data_index(te->ext_data[j]);
                assert(p < 64);
                v = (v << 6) | p;
            }
            te->data_index = v;
        }
    }

    for(i = 0; i < conv_table_len; i++) {
        te = &conv_table[i];
        if (te->type == RUN_TYPE_UF_D1_EXT ||
            te->type == RUN_TYPE_U_EXT ||
            te->type == RUN_TYPE_LF_EXT) {
            te->data_index = find_ext_data_index(te->ext_data[0]);
        }
    }
#ifdef DUMP_CASE_CONV_TABLE
    dump_case_conv_table1();
#endif
}

void dump_case_conv_table(FILE *f)
{
    int i;
    uint32_t v;
    const TableEntry *te;

    fprintf(f, "static const uint32_t case_conv_table1[%u] = {", conv_table_len);
    for(i = 0; i < conv_table_len; i++) {
        if (i % 4 == 0)
            fprintf(f, "\n   ");
        te = &conv_table[i];
        v = te->code << (32 - 17);
        v |= te->len << (32 - 17 - 7);
        v |= te->type << (32 - 17 - 7 - 4);
        v |= te->data_index >> 8;
        fprintf(f, " 0x%08x,", v);
    }
    fprintf(f, "\n};\n\n");

    fprintf(f, "static const uint8_t case_conv_table2[%u] = {", conv_table_len);
    for(i = 0; i < conv_table_len; i++) {
        if (i % 8 == 0)
            fprintf(f, "\n   ");
        te = &conv_table[i];
        fprintf(f, " 0x%02x,", te->data_index & 0xff);
    }
    fprintf(f, "\n};\n\n");

    fprintf(f, "static const uint16_t case_conv_ext[%u] = {", ext_data_len);
    for(i = 0; i < ext_data_len; i++) {
        if (i % 8 == 0)
            fprintf(f, "\n   ");
        fprintf(f, " 0x%04x,", ext_data[i]);
    }
    fprintf(f, "\n};\n\n");
}


static CCInfo *global_tab;

static int sp_cc_cmp(const void *p1, const void *p2)
{
    CCInfo *c1 = &global_tab[*(const int *)p1];
    CCInfo *c2 = &global_tab[*(const int *)p2];
    if (c1->f_len < c2->f_len) {
        return -1;
    } else if (c2->f_len < c1->f_len) {
        return 1;
    } else {
        return memcmp(c1->f_data, c2->f_data, sizeof(c1->f_data[0]) * c1->f_len);
    }
}

/* dump the case special cases (multi character results which are
   identical and need specific handling in lre_canonicalize() */
void dump_case_folding_special_cases(CCInfo *tab)
{
    int i, len, j;
    int *perm;

    perm = malloc(sizeof(perm[0]) * (CHARCODE_MAX + 1));
    for(i = 0; i <= CHARCODE_MAX; i++)
        perm[i] = i;
    global_tab = tab;
    qsort(perm, CHARCODE_MAX + 1, sizeof(perm[0]), sp_cc_cmp);
    for(i = 0; i <= CHARCODE_MAX;) {
        if (tab[perm[i]].f_len <= 1) {
            i++;
        } else {
            len = 1;
            while ((i + len) <= CHARCODE_MAX && !sp_cc_cmp(&perm[i], &perm[i + len]))
                len++;

            if (len > 1) {
                for(j = i; j < i + len; j++)
                    dump_cc_info(&tab[perm[j]], perm[j]);
            }
            i += len;
        }
    }
    free(perm);
    global_tab = NULL;
}


int tabcmp(const int *tab1, const int *tab2, int n)
{
    int i;
    for(i = 0; i < n; i++) {
        if (tab1[i] != tab2[i])
            return -1;
    }
    return 0;
}

void dump_str(const char *str, const int *buf, int len)
{
    int i;
    printf("%s=", str);
    for(i = 0; i < len; i++)
        printf(" %05x", buf[i]);
    printf("\n");
}

void compute_internal_props(void)
{
    int i;
    bool has_ul;

    for(i = 0; i <= CHARCODE_MAX; i++) {
        CCInfo *ci = &unicode_db[i];
        has_ul = (ci->u_len != 0 || ci->l_len != 0 || ci->f_len != 0);
        if (has_ul) {
            assert(get_prop(i, PROP_Cased));
        } else {
            set_prop(i, PROP_Cased1, get_prop(i, PROP_Cased));
        }
        set_prop(i, PROP_ID_Continue1,
                 get_prop(i, PROP_ID_Continue) & (get_prop(i, PROP_ID_Start) ^ 1));
        set_prop(i, PROP_XID_Start1,
                 get_prop(i, PROP_ID_Start) ^ get_prop(i, PROP_XID_Start));
        set_prop(i, PROP_XID_Continue1,
                 get_prop(i, PROP_ID_Continue) ^ get_prop(i, PROP_XID_Continue));
        set_prop(i, PROP_Changes_When_Titlecased1,
                 get_prop(i, PROP_Changes_When_Titlecased) ^ (ci->u_len != 0));
        set_prop(i, PROP_Changes_When_Casefolded1,
                 get_prop(i, PROP_Changes_When_Casefolded) ^ (ci->f_len != 0));
        /* XXX: reduce table size (438 bytes) */
        set_prop(i, PROP_Changes_When_NFKC_Casefolded1,
                 get_prop(i, PROP_Changes_When_NFKC_Casefolded) ^ (ci->f_len != 0));
    }
}

void dump_byte_table(FILE *f, const char *cname, const uint8_t *tab, int len)
{
    int i;
    fprintf(f, "static const uint8_t %s[%d] = {", cname, len);
    for(i = 0; i < len; i++) {
        if (i % 8 == 0)
            fprintf(f, "\n   ");
        fprintf(f, " 0x%02x,", tab[i]);
    }
    fprintf(f, "\n};\n\n");
}

#define PROP_BLOCK_LEN 32

void build_prop_table(FILE *f, int prop_index, bool add_index)
{
    int i, j, n, v, offset, code;
    DynBuf dbuf_s, *dbuf = &dbuf_s;
    DynBuf dbuf1_s, *dbuf1 = &dbuf1_s;
    DynBuf dbuf2_s, *dbuf2 = &dbuf2_s;
    const uint32_t *buf;
    int buf_len, block_end_pos, bit;
    char cname[128];

    dbuf_init(dbuf1);

    for(i = 0; i <= CHARCODE_MAX;) {
        v = get_prop(i, prop_index);
        j = i + 1;
        while (j <= CHARCODE_MAX && get_prop(j, prop_index) == v) {
            j++;
        }
        n = j - i;
        if (j == (CHARCODE_MAX + 1) && v == 0)
            break; /* no need to encode last zero run */
        //printf("%05x: %d %d\n", i, n, v);
        dbuf_put_u32(dbuf1, n - 1);
        i += n;
    }

    dbuf_init(dbuf);
    dbuf_init(dbuf2);
    buf = (uint32_t *)dbuf1->buf;
    buf_len = dbuf1->size / sizeof(buf[0]);

    /* the first value is assumed to be 0 */
    assert(get_prop(0, prop_index) == 0);

    block_end_pos = PROP_BLOCK_LEN;
    i = 0;
    code = 0;
    bit = 0;
    while (i < buf_len) {
        if (add_index && dbuf->size >= block_end_pos && bit == 0) {
            offset = (dbuf->size - block_end_pos);
            /* XXX: offset could be larger in case of runs of small
               lengths. Could add code to change the encoding to
               prevent it at the expense of one byte loss */
            assert(offset <= 7);
            v = code | (offset << 21);
            dbuf_putc(dbuf2, v);
            dbuf_putc(dbuf2, v >> 8);
            dbuf_putc(dbuf2, v >> 16);
            block_end_pos += PROP_BLOCK_LEN;
        }

        v = buf[i];
        code += v + 1;
        bit ^= 1;
        if (v < 8 && (i + 1) < buf_len && buf[i + 1] < 8) {
            code += buf[i + 1] + 1;
            bit ^= 1;
            dbuf_putc(dbuf, (v << 3) | buf[i + 1]);
            i += 2;
        } else if (v < 128) {
            dbuf_putc(dbuf, 0x80 + v);
            i++;
        } else if (v < (1 << 13)) {
            dbuf_putc(dbuf, 0x40 + (v >> 8));
            dbuf_putc(dbuf, v);
            i++;
        } else {
            assert(v < (1 << 21));
            dbuf_putc(dbuf, 0x60 + (v >> 16));
            dbuf_putc(dbuf, v >> 8);
            dbuf_putc(dbuf, v);
            i++;
        }
    }

    if (add_index) {
        /* last index entry */
        v = code;
        dbuf_putc(dbuf2, v);
        dbuf_putc(dbuf2, v >> 8);
        dbuf_putc(dbuf2, v >> 16);
    }

#ifdef DUMP_TABLE_SIZE
    printf("prop %s: length=%d bytes\n", unicode_prop_name[prop_index],
           (int)(dbuf->size + dbuf2->size));
#endif
    snprintf(cname, sizeof(cname), "unicode_prop_%s_table", unicode_prop_name[prop_index]);
    dump_byte_table(f, cname, dbuf->buf, dbuf->size);
    if (add_index) {
        snprintf(cname, sizeof(cname), "unicode_prop_%s_index", unicode_prop_name[prop_index]);
        dump_byte_table(f, cname, dbuf2->buf, dbuf2->size);
    }

    dbuf_free(dbuf);
    dbuf_free(dbuf1);
    dbuf_free(dbuf2);
}

void build_flags_tables(FILE *f)
{
    build_prop_table(f, PROP_Cased1, true);
    build_prop_table(f, PROP_Case_Ignorable, true);
    build_prop_table(f, PROP_ID_Start, true);
    build_prop_table(f, PROP_ID_Continue1, true);
    build_prop_table(f, PROP_White_Space, true);
}

void dump_name_table(FILE *f, const char *cname, const char **tab_name, int len,
                     const char **tab_short_name)
{
    int i, w, maxw;

    maxw = 0;
    for(i = 0; i < len; i++) {
        w = strlen(tab_name[i]);
        if (tab_short_name && tab_short_name[i][0] != '\0') {
            w += 1 + strlen(tab_short_name[i]);
        }
        if (maxw < w)
            maxw = w;
    }

    /* generate a sequence of strings terminated by an empty string */
    fprintf(f, "static const char %s[] =\n", cname);
    for(i = 0; i < len; i++) {
        fprintf(f, "    \"");
        w = fprintf(f, "%s", tab_name[i]);
        if (tab_short_name && tab_short_name[i][0] != '\0') {
            w += fprintf(f, ",%s", tab_short_name[i]);
        }
        fprintf(f, "\"%*s\"\\0\"\n", 1 + maxw - w, "");
    }
    fprintf(f, ";\n\n");
}

void build_general_category_table(FILE *f)
{
    int i, v, j, n, n1;
    DynBuf dbuf_s, *dbuf = &dbuf_s;
    int cw_count, cw_len_count[4], cw_start;

    fprintf(f, "typedef enum {\n");
    for(i = 0; i < GCAT_COUNT; i++)
        fprintf(f, "    UNICODE_GC_%s,\n", unicode_gc_name[i]);
    fprintf(f, "    UNICODE_GC_COUNT,\n");
    fprintf(f, "} UnicodeGCEnum;\n\n");

    dump_name_table(f, "unicode_gc_name_table",
                    unicode_gc_name, GCAT_COUNT,
                    unicode_gc_short_name);


    dbuf_init(dbuf);
    cw_count = 0;
    for(i = 0; i < 4; i++)
        cw_len_count[i] = 0;
    for(i = 0; i <= CHARCODE_MAX;) {
        v = unicode_db[i].general_category;
        j = i + 1;
        while (j <= CHARCODE_MAX && unicode_db[j].general_category == v)
            j++;
        n = j - i;
        /* compress Lu/Ll runs */
        if (v == GCAT_Lu) {
            n1 = 1;
            while ((i + n1) <= CHARCODE_MAX && unicode_db[i + n1].general_category == (v + (n1 & 1))) {
                n1++;
            }
            if (n1 > n) {
                v = 31;
                n = n1;
            }
        }
        //        printf("%05x %05x %d\n", i, n, v);
        cw_count++;
        n--;
        cw_start = dbuf->size;
        if (n < 7) {
            dbuf_putc(dbuf, (n << 5) | v);
        } else if (n < 7 + 128) {
            n1 = n - 7;
            assert(n1 < 128);
            dbuf_putc(dbuf, (0xf << 5) | v);
            dbuf_putc(dbuf, n1);
        } else if (n < 7 + 128 + (1 << 14)) {
            n1 = n - (7 + 128);
            assert(n1 < (1 << 14));
            dbuf_putc(dbuf, (0xf << 5) | v);
            dbuf_putc(dbuf, (n1 >> 8) + 128);
            dbuf_putc(dbuf, n1);
        } else {
            n1 = n - (7 + 128 + (1 << 14));
            assert(n1 < (1 << 22));
            dbuf_putc(dbuf, (0xf << 5) | v);
            dbuf_putc(dbuf, (n1 >> 16) + 128 + 64);
            dbuf_putc(dbuf, n1 >> 8);
            dbuf_putc(dbuf, n1);
        }
        cw_len_count[dbuf->size - cw_start - 1]++;
        i += n + 1;
    }
#ifdef DUMP_TABLE_SIZE
    printf("general category: %d entries [",
           cw_count);
    for(i = 0; i < 4; i++)
        printf(" %d", cw_len_count[i]);
    printf(" ], length=%d bytes\n", (int)dbuf->size);
#endif

    dump_byte_table(f, "unicode_gc_table", dbuf->buf, dbuf->size);

    dbuf_free(dbuf);
}

void build_script_table(FILE *f)
{
    int i, v, j, n, n1, type;
    DynBuf dbuf_s, *dbuf = &dbuf_s;
    int cw_count, cw_len_count[4], cw_start;

    fprintf(f, "typedef enum {\n");
    for(i = 0; i < SCRIPT_COUNT; i++)
        fprintf(f, "    UNICODE_SCRIPT_%s,\n", unicode_script_name[i]);
    fprintf(f, "    UNICODE_SCRIPT_COUNT,\n");
    fprintf(f, "} UnicodeScriptEnum;\n\n");

    i = 1;
    dump_name_table(f, "unicode_script_name_table",
                    unicode_script_name + i, SCRIPT_COUNT - i,
                    unicode_script_short_name + i);

    dbuf_init(dbuf);
    cw_count = 0;
    for(i = 0; i < 4; i++)
        cw_len_count[i] = 0;
    for(i = 0; i <= CHARCODE_MAX;) {
        v = unicode_db[i].script;
        j = i + 1;
        while (j <= CHARCODE_MAX && unicode_db[j].script == v)
            j++;
        n = j - i;
        if (v == 0 && j == (CHARCODE_MAX + 1))
            break;
        //        printf("%05x %05x %d\n", i, n, v);
        cw_count++;
        n--;
        cw_start = dbuf->size;
        if (v == 0)
            type = 0;
        else
            type = 1;
        if (n < 96) {
            dbuf_putc(dbuf, n | (type << 7));
        } else if (n < 96 + (1 << 12)) {
            n1 = n - 96;
            assert(n1 < (1 << 12));
            dbuf_putc(dbuf, ((n1 >> 8) + 96) | (type << 7));
            dbuf_putc(dbuf, n1);
        } else {
            n1 = n - (96 + (1 << 12));
            assert(n1 < (1 << 20));
            dbuf_putc(dbuf, ((n1 >> 16) + 112) | (type << 7));
            dbuf_putc(dbuf, n1 >> 8);
            dbuf_putc(dbuf, n1);
        }
        if (type != 0)
            dbuf_putc(dbuf, v);

        cw_len_count[dbuf->size - cw_start - 1]++;
        i += n + 1;
    }
#if defined(DUMP_TABLE_SIZE)
    printf("script: %d entries [",
           cw_count);
    for(i = 0; i < 4; i++)
        printf(" %d", cw_len_count[i]);
    printf(" ], length=%d bytes\n", (int)dbuf->size);
#endif

    dump_byte_table(f, "unicode_script_table", dbuf->buf, dbuf->size);

    dbuf_free(dbuf);
}

void build_script_ext_table(FILE *f)
{
    int i, j, n, n1, script_ext_len;
    DynBuf dbuf_s, *dbuf = &dbuf_s;
    int cw_count;

    dbuf_init(dbuf);
    cw_count = 0;
    for(i = 0; i <= CHARCODE_MAX;) {
        script_ext_len = unicode_db[i].script_ext_len;
        j = i + 1;
        while (j <= CHARCODE_MAX &&
               unicode_db[j].script_ext_len == script_ext_len &&
               !memcmp(unicode_db[j].script_ext, unicode_db[i].script_ext,
                       script_ext_len)) {
            j++;
        }
        n = j - i;
        cw_count++;
        n--;
        if (n < 128) {
            dbuf_putc(dbuf, n);
        } else if (n < 128 + (1 << 14)) {
            n1 = n - 128;
            assert(n1 < (1 << 14));
            dbuf_putc(dbuf, (n1 >> 8) + 128);
            dbuf_putc(dbuf, n1);
        } else {
            n1 = n - (128 + (1 << 14));
            assert(n1 < (1 << 22));
            dbuf_putc(dbuf, (n1 >> 16) + 128 + 64);
            dbuf_putc(dbuf, n1 >> 8);
            dbuf_putc(dbuf, n1);
        }
        dbuf_putc(dbuf, script_ext_len);
        for(j = 0; j < script_ext_len; j++)
            dbuf_putc(dbuf, unicode_db[i].script_ext[j]);
        i += n + 1;
    }
#ifdef DUMP_TABLE_SIZE
    printf("script_ext: %d entries",
           cw_count);
    printf(", length=%d bytes\n", (int)dbuf->size);
#endif

    dump_byte_table(f, "unicode_script_ext_table", dbuf->buf, dbuf->size);

    dbuf_free(dbuf);
}

/* the following properties are synthetized so no table is necessary */
#define PROP_TABLE_COUNT PROP_ASCII

void build_prop_list_table(FILE *f)
{
    int i;

    for(i = 0; i < PROP_TABLE_COUNT; i++) {
        if (i == PROP_ID_Start ||
            i == PROP_Case_Ignorable ||
            i == PROP_ID_Continue1 ||
            i == PROP_White_Space) {
            /* already generated */
        } else {
            build_prop_table(f, i, false);
        }
    }

    fprintf(f, "typedef enum {\n");
    for(i = 0; i < PROP_COUNT; i++)
        fprintf(f, "    UNICODE_PROP_%s,\n", unicode_prop_name[i]);
    fprintf(f, "    UNICODE_PROP_COUNT,\n");
    fprintf(f, "} UnicodePropertyEnum;\n\n");

    i = PROP_ASCII_Hex_Digit;
    dump_name_table(f, "unicode_prop_name_table",
                    unicode_prop_name + i, PROP_XID_Start - i + 1,
                    unicode_prop_short_name + i);

    fprintf(f, "static const uint8_t * const unicode_prop_table[] = {\n");
    for(i = 0; i < PROP_TABLE_COUNT; i++) {
        fprintf(f, "    unicode_prop_%s_table,\n", unicode_prop_name[i]);
    }
    fprintf(f, "};\n\n");

    fprintf(f, "static const uint16_t unicode_prop_len_table[] = {\n");
    for(i = 0; i < PROP_TABLE_COUNT; i++) {
        fprintf(f, "    countof(unicode_prop_%s_table),\n", unicode_prop_name[i]);
    }
    fprintf(f, "};\n\n");
}

static bool is_emoji_hair_color(uint32_t c)
{
    return (c >= 0x1F9B0 && c <= 0x1F9B3);
}

#define EMOJI_MOD_NONE   0
#define EMOJI_MOD_TYPE1  1
#define EMOJI_MOD_TYPE2  2
#define EMOJI_MOD_TYPE2D 3

/* Test whether all the variants of 'buf' obtained by substituting the emoji
   modifiers at 'mod_pos' and the hair color at 'hc_pos' are present in 'sl'.
   If 'mark_flag' is true, they are marked as already handled. 'buf' is
   modified. */
static bool mark_zwj_string(REStringList *sl, uint32_t *buf, int len,
                           int mod_type, int *mod_pos, int hc_pos,
                           bool mark_flag)
{
    REString *p;
    int i, n_mod, i0, i1, hc_count, j;

    switch(mod_type) {
    case EMOJI_MOD_NONE:
        n_mod = 1;
        break;
    case EMOJI_MOD_TYPE1:
        n_mod = 5;
        break;
    case EMOJI_MOD_TYPE2:
        n_mod = 25;
        break;
    case EMOJI_MOD_TYPE2D:
        n_mod = 20;
        break;
    default:
        abort();
    }
    if (hc_pos >= 0)
        hc_count = 4;
    else
        hc_count = 1;
    /* check that all the related strings are present */
    for(j = 0; j < hc_count; j++) {
        for(i = 0; i < n_mod; i++) {
            switch(mod_type) {
            case EMOJI_MOD_NONE:
                break;
            case EMOJI_MOD_TYPE1:
                buf[mod_pos[0]] = 0x1f3fb + i;
                break;
            case EMOJI_MOD_TYPE2:
            case EMOJI_MOD_TYPE2D:
                i0 = i / 5;
                i1 = i % 5;
                /* avoid identical values */
                if (mod_type == EMOJI_MOD_TYPE2D && i0 >= i1)
                    i0++;
                buf[mod_pos[0]] = 0x1f3fb + i0;
                buf[mod_pos[1]] = 0x1f3fb + i1;
                break;
            default:
                abort();
            }

            if (hc_pos >= 0)
                buf[hc_pos] = 0x1F9B0 + j;

            p = re_string_find(sl, len, buf, false);
            if (!p)
                return false;
            if (mark_flag)
                p->flags |= 1;
        }
    }
    return true;
}

/* encode a ZWJ sequence as a length followed by 16 bit code points. The
   ZWJ separators, the presentation selectors and the emoji modifiers are
   encoded in the code points, see unicode_sequence_prop1() in
   libunicode.c */
static void zwj_encode_string(DynBuf *dbuf, const uint32_t *buf, int len,
                              int mod_type)
{
    int i, j;
    int c, code;
    uint32_t buf1[SEQ_MAX_LEN];

    j = 0;
    for(i = 0; i < len;) {
        c = buf[i++];
        if (c >= 0x2000 && c <= 0x2fff) {
            code = c - 0x2000;
        } else if (c >= 0x1f000 && c <= 0x1ffff) {
            code = c - 0x1f000 + 0x1000;
        } else {
            abort();
        }
        if (i < len && is_emoji_modifier(buf[i])) {
            /* modifier */
            code |= (mod_type << 13);
            i++;
        }
        if (i < len && buf[i] == 0xfe0f) {
            /* presentation selector present */
            code |= 0x8000;
            i++;
        }
        if (i < len) {
            /* zero width joiner */
            assert(buf[i] == 0x200d);
            i++;
        }
        buf1[j++] = code;
    }
    dbuf_putc(dbuf, j);
    for(i = 0; i < j; i++) {
        dbuf_putc(dbuf, buf1[i]);
        dbuf_putc(dbuf, buf1[i] >> 8);
    }
}

static void build_rgi_emoji_zwj_sequence(FILE *f, REStringList *sl)
{
    int mod_pos[2], mod_count, hair_color_pos;
    uint32_t buf[SEQ_MAX_LEN], h, j;
    REString *p;
    DynBuf dbuf;

    dbuf_init(&dbuf);

    /* avoid duplicating strings with emoji modifiers or hair colors */
    for(h = 0; h < sl->hash_size; h++) {
        for(p = sl->hash_table[h]; p != NULL; p = p->next) {
            if (p->flags) /* already examined */
                continue;
            mod_count = 0;
            hair_color_pos = -1;
            for(j = 0; j < p->len; j++) {
                if (is_emoji_modifier(p->buf[j])) {
                    assert(mod_count < 2);
                    mod_pos[mod_count++] = j;
                } else if (is_emoji_hair_color(p->buf[j])) {
                    hair_color_pos = j;
                }
                buf[j] = p->buf[j];
            }

            if (mod_count != 0 || hair_color_pos >= 0) {
                int mod_type;
                if (mod_count == 0)
                    mod_type = EMOJI_MOD_NONE;
                else if (mod_count == 1)
                    mod_type = EMOJI_MOD_TYPE1;
                else
                    mod_type = EMOJI_MOD_TYPE2;

                if (mark_zwj_string(sl, buf, p->len, mod_type, mod_pos, hair_color_pos, false)) {
                    mark_zwj_string(sl, buf, p->len, mod_type, mod_pos, hair_color_pos, true);
                } else if (mod_type == EMOJI_MOD_TYPE2) {
                    mod_type = EMOJI_MOD_TYPE2D;
                    if (mark_zwj_string(sl, buf, p->len, mod_type, mod_pos, hair_color_pos, false)) {
                        mark_zwj_string(sl, buf, p->len, mod_type, mod_pos, hair_color_pos, true);
                    } else {
                        dump_str("not_found", (int *)p->buf, p->len);
                        goto keep;
                    }
                }
                if (hair_color_pos >= 0)
                    buf[hair_color_pos] = 0x1f9b0;
                /* encode the string */
                zwj_encode_string(&dbuf, buf, p->len, mod_type);
            } else {
            keep:
                zwj_encode_string(&dbuf, buf, p->len, EMOJI_MOD_NONE);
            }
        }
    }

    dump_byte_table(f, "unicode_rgi_emoji_zwj_sequence", dbuf.buf, dbuf.size);

    dbuf_free(&dbuf);
}

void build_sequence_prop_list_table(FILE *f)
{
    int i;

    fprintf(f, "typedef enum {\n");
    for(i = 0; i < SEQUENCE_PROP_COUNT; i++)
        fprintf(f, "    UNICODE_SEQUENCE_PROP_%s,\n", unicode_sequence_prop_name[i]);
    fprintf(f, "    UNICODE_SEQUENCE_PROP_COUNT,\n");
    fprintf(f, "} UnicodeSequencePropertyEnum;\n\n");

    dump_name_table(f, "unicode_sequence_prop_name_table",
                    unicode_sequence_prop_name, SEQUENCE_PROP_COUNT, NULL);

    dump_byte_table(f, "unicode_rgi_emoji_tag_sequence",
                    rgi_emoji_tag_sequence.buf, rgi_emoji_tag_sequence.size);

    build_rgi_emoji_zwj_sequence(f, &rgi_emoji_zwj_sequence);
}

#ifdef USE_TEST
int check_conv(uint32_t *res, uint32_t c, int conv_type)
{
    return lre_case_conv(res, c, conv_type);
}

void check_case_conv(void)
{
    CCInfo *tab = unicode_db;
    uint32_t res[3];
    int l, error;
    CCInfo ci_s, *ci1, *ci = &ci_s;
    int code;

    for(code = 0; code <= CHARCODE_MAX; code++) {
        ci1 = &tab[code];
        *ci = *ci1;
        if (ci->l_len == 0) {
            ci->l_len = 1;
            ci->l_data[0] = code;
        }
        if (ci->u_len == 0) {
            ci->u_len = 1;
            ci->u_data[0] = code;
        }
        if (ci->f_len == 0) {
            ci->f_len = 1;
            ci->f_data[0] = code;
        }

        error = 0;
        l = check_conv(res, code, 0);
        if (l != ci->u_len || tabcmp((int *)res, ci->u_data, l)) {
            printf("ERROR: L\n");
            error++;
        }
        l = check_conv(res, code, 1);
        if (l != ci->l_len || tabcmp((int *)res, ci->l_data, l)) {
            printf("ERROR: U\n");
            error++;
        }
        l = check_conv(res, code, 2);
        if (l != ci->f_len || tabcmp((int *)res, ci->f_data, l)) {
            printf("ERROR: F\n");
            error++;
        }
        if (error) {
            dump_cc_info(ci, code);
            exit(1);
        }
    }
}

#ifdef PROFILE
static int64_t get_time_ns(void)
{
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return (int64_t)ts.tv_sec * 1000000000 + ts.tv_nsec;
}
#endif


void check_flags(void)
{
    int c;
    bool flag_ref, flag;
    for(c = 0; c <= CHARCODE_MAX; c++) {
        flag_ref = get_prop(c, PROP_Cased);
        flag = lre_is_cased(c);
        if (flag != flag_ref) {
            printf("ERROR: c=%05x cased=%d ref=%d\n",
                   c, flag, flag_ref);
            exit(1);
        }

        flag_ref = get_prop(c, PROP_Case_Ignorable);
        flag = lre_is_case_ignorable(c);
        if (flag != flag_ref) {
            printf("ERROR: c=%05x case_ignorable=%d ref=%d\n",
                   c, flag, flag_ref);
            exit(1);
        }

        flag_ref = get_prop(c, PROP_ID_Start);
        flag = lre_is_id_start(c);
        if (flag != flag_ref) {
            printf("ERROR: c=%05x id_start=%d ref=%d\n",
                   c, flag, flag_ref);
            exit(1);
        }

        flag_ref = get_prop(c, PROP_ID_Continue);
        flag = lre_is_id_continue(c);
        if (flag != flag_ref) {
            printf("ERROR: c=%05x id_cont=%d ref=%d\n",
                   c, flag, flag_ref);
            exit(1);
        }
    }
#ifdef PROFILE
    {
        int64_t ti, count;
        ti = get_time_ns();
        count = 0;
        for(c = 0x20; c <= 0xffff; c++) {
            flag_ref = get_prop(c, PROP_ID_Start);
            flag = lre_is_id_start(c);
            assert(flag == flag_ref);
            count++;
        }
        ti = get_time_ns() - ti;
        printf("flags time=%0.1f ns/char\n",
               (double)ti / count);
    }
#endif
}

#endif

#define CC_BLOCK_LEN 32

void build_cc_table(FILE *f)
{
    int i, cc, n, cc_table_len, type, n1;
    DynBuf dbuf_s, *dbuf = &dbuf_s;
    DynBuf dbuf1_s, *dbuf1 = &dbuf1_s;
    int cw_len_tab[3], cw_start, block_end_pos;
    uint32_t v;

    dbuf_init(dbuf);
    dbuf_init(dbuf1);
    cc_table_len = 0;
    for(i = 0; i < countof(cw_len_tab); i++)
        cw_len_tab[i] = 0;
    block_end_pos = CC_BLOCK_LEN;
    for(i = 0; i <= CHARCODE_MAX;) {
        cc = unicode_db[i].combining_class;
        assert(cc <= 255);
        /* check increasing values */
        n = 1;
        while ((i + n) <= CHARCODE_MAX &&
               unicode_db[i + n].combining_class == (cc + n))
            n++;
        if (n >= 2) {
            type = 1;
        } else {
            type = 0;
            n = 1;
            while ((i + n) <= CHARCODE_MAX &&
                   unicode_db[i + n].combining_class == cc)
                n++;
        }
        /* no need to encode the last run */
        if (cc == 0 && (i + n - 1) == CHARCODE_MAX)
            break;
#ifdef DUMP_CC_TABLE
        printf("%05x %6d %d %d\n", i, n, type, cc);
#endif
        if (type == 0) {
            if (cc == 0)
                type = 2;
            else if (cc == 230)
                type = 3;
        }
        n1 = n - 1;

        /* add an entry to the index if necessary */
        if (dbuf->size >= block_end_pos) {
            v = i | ((dbuf->size - block_end_pos) << 21);
            dbuf_putc(dbuf1, v);
            dbuf_putc(dbuf1, v >> 8);
            dbuf_putc(dbuf1, v >> 16);
            block_end_pos += CC_BLOCK_LEN;
        }
        cw_start = dbuf->size;
        if (n1 < 48) {
            dbuf_putc(dbuf, n1 | (type << 6));
        } else if (n1 < 48 + (1 << 11)) {
            n1 -= 48;
            dbuf_putc(dbuf, ((n1 >> 8) + 48) | (type << 6));
            dbuf_putc(dbuf, n1);
        } else {
            n1 -= 48 + (1 << 11);
            assert(n1 < (1 << 20));
            dbuf_putc(dbuf, ((n1 >> 16) + 56) | (type << 6));
            dbuf_putc(dbuf, n1 >> 8);
            dbuf_putc(dbuf, n1);
        }
        cw_len_tab[dbuf->size - cw_start - 1]++;
        if (type == 0 || type == 1)
            dbuf_putc(dbuf, cc);
        cc_table_len++;
        i += n;
    }

    /* last index entry */
    v = i;
    dbuf_putc(dbuf1, v);
    dbuf_putc(dbuf1, v >> 8);
    dbuf_putc(dbuf1, v >> 16);

    dump_byte_table(f, "unicode_cc_table", dbuf->buf, dbuf->size);
    dump_byte_table(f, "unicode_cc_index", dbuf1->buf, dbuf1->size);

#if defined(DUMP_CC_TABLE) || defined(DUMP_TABLE_SIZE)
    printf("CC table: size=%d (%d entries) [",
           (int)(dbuf->size + dbuf1->size),
           cc_table_len);
    for(i = 0; i < countof(cw_len_tab); i++)
        printf(" %d", cw_len_tab[i]);
    printf(" ]\n");
#endif
    dbuf_free(dbuf);
    dbuf_free(dbuf1);
}

/* maximum length of decomposition: 18 chars (1), then 8 */
#ifndef USE_TEST
typedef enum {
    DECOMP_TYPE_C1, /* 16 bit char */
    DECOMP_TYPE_L1, /* 16 bit char table */
    DECOMP_TYPE_L2,
    DECOMP_TYPE_L3,
    DECOMP_TYPE_L4,
    DECOMP_TYPE_L5, /* XXX: not used */
    DECOMP_TYPE_L6, /* XXX: could remove */
    DECOMP_TYPE_L7, /* XXX: could remove */
    DECOMP_TYPE_LL1, /* 18 bit char table */
    DECOMP_TYPE_LL2,
    DECOMP_TYPE_S1, /* 8 bit char table */
    DECOMP_TYPE_S2,
    DECOMP_TYPE_S3,
    DECOMP_TYPE_S4,
    DECOMP_TYPE_S5,
    DECOMP_TYPE_I1, /* increment 16 bit char value */
    DECOMP_TYPE_I2_0,
    DECOMP_TYPE_I2_1,
    DECOMP_TYPE_I3_1,
    DECOMP_TYPE_I3_2,
    DECOMP_TYPE_I4_1,
    DECOMP_TYPE_I4_2,
    DECOMP_TYPE_B1, /* 16 bit base + 8 bit offset */
    DECOMP_TYPE_B2,
    DECOMP_TYPE_B3,
    DECOMP_TYPE_B4,
    DECOMP_TYPE_B5,
    DECOMP_TYPE_B6,
    DECOMP_TYPE_B7,
    DECOMP_TYPE_B8,
    DECOMP_TYPE_B18,
    DECOMP_TYPE_LS2,
    DECOMP_TYPE_PAT3,
    DECOMP_TYPE_S2_UL,
    DECOMP_TYPE_LS2_UL,
} DecompTypeEnum;
#endif

const char *decomp_type_str[] = {
    "C1",
    "L1",
    "L2",
    "L3",
    "L4",
    "L5",
    "L6",
    "L7",
    "LL1",
    "LL2",
    "S1",
    "S2",
    "S3",
    "S4",
    "S5",
    "I1",
    "I2_0",
    "I2_1",
    "I3_1",
    "I3_2",
    "I4_1",
    "I4_2",
    "B1",
    "B2",
    "B3",
    "B4",
    "B5",
    "B6",
    "B7",
    "B8",
    "B18",
    "LS2",
    "PAT3",
    "S2_UL",
    "LS2_UL",
};

const int decomp_incr_tab[4][4] = {
    { DECOMP_TYPE_I1, 0, -1 },
    { DECOMP_TYPE_I2_0, 0, 1, -1 },
    { DECOMP_TYPE_I3_1, 1, 2, -1 },
    { DECOMP_TYPE_I4_1, 1, 2, -1 },
};

/*
  entry size:
  type   bits
  code   18
  len    7
  compat 1
  type   5
  index  16
  total  47
*/

typedef struct {
    int code;
    uint8_t len;
    uint8_t type;
    uint8_t c_len;
    uint16_t c_min;
    uint16_t data_index;
    int cost; /* size in bytes from this entry to the end */
} DecompEntry;

int get_decomp_run_size(const DecompEntry *de)
{
    int s;
    s = 6;
    if (de->type <= DECOMP_TYPE_C1) {
        /* nothing more */
    } else if (de->type <= DECOMP_TYPE_L7) {
        s += de->len * de->c_len * 2;
    } else if (de->type <= DECOMP_TYPE_LL2) {
        /* 18 bits per char */
        s += (de->len * de->c_len * 18 + 7) / 8;
    } else if (de->type <= DECOMP_TYPE_S5) {
        s += de->len * de->c_len;
    } else if (de->type <= DECOMP_TYPE_I4_2) {
        s += de->c_len * 2;
    } else if (de->type <= DECOMP_TYPE_B18) {
        s += 2 + de->len * de->c_len;
    } else if (de->type <= DECOMP_TYPE_LS2) {
        s += de->len * 3;
    } else if (de->type <= DECOMP_TYPE_PAT3) {
        s += 4 + de->len * 2;
    } else if (de->type <= DECOMP_TYPE_S2_UL) {
        s += de->len;
    } else if (de->type <= DECOMP_TYPE_LS2_UL) {
        s += (de->len / 2) * 3;
    } else {
        abort();
    }
    return s;
}

static const uint16_t unicode_short_table[2] = { 0x2044, 0x2215 };

/* return -1 if not found */
int get_short_code(int c)
{
    int i;
    if (c < 0x80) {
        return c;
    } else if (c >= 0x300 && c < 0x350) {
        return c - 0x300 + 0x80;
    } else {
        for(i = 0; i < countof(unicode_short_table); i++) {
            if (c == unicode_short_table[i])
                return i + 0x80 + 0x50;
        }
        return -1;
    }
}

static bool is_short(int code)
{
    return get_short_code(code) >= 0;
}

static bool is_short_tab(const int *tab, int len)
{
    int i;
    for(i = 0; i < len; i++) {
        if (!is_short(tab[i]))
            return false;
    }
    return true;
}

static bool is_16bit(const int *tab, int len)
{
    int i;
    for(i = 0; i < len; i++) {
        if (tab[i] > 0xffff)
            return false;
    }
    return true;
}

static uint32_t to_lower_simple(uint32_t c)
{
    /* Latin1 and Cyrillic */
    if (c < 0x100 || (c >= 0x410 && c <= 0x42f))
        c += 0x20;
    else
        c++;
    return c;
}

/* select best encoding with dynamic programming */
void find_decomp_run(DecompEntry *tab_de, int i)
{
    DecompEntry de_s, *de = &de_s;
    CCInfo *ci, *ci1, *ci2;
    int l, j, n, len_max;

    ci = &unicode_db[i];
    l = ci->decomp_len;
    if (l == 0) {
        tab_de[i].cost = tab_de[i + 1].cost;
        return;
    }

    /* the offset for the compose table has only 6 bits, so we must
       limit if it can be used by the compose table */
    if (!ci->is_compat && !ci->is_excluded && l == 2)
        len_max = 64;
    else
        len_max = 127;

    tab_de[i].cost = 0x7fffffff;

    if (!is_16bit(ci->decomp_data, l)) {
        assert(l <= 2);

        n = 1;
        for(;;) {
            de->code = i;
            de->len = n;
            de->type = DECOMP_TYPE_LL1 + l - 1;
            de->c_len = l;
            de->cost = get_decomp_run_size(de) + tab_de[i + n].cost;
            if (de->cost < tab_de[i].cost) {
                tab_de[i] = *de;
            }
            if (!((i + n) <= CHARCODE_MAX && n < len_max))
                break;
            ci1 = &unicode_db[i + n];
            /* Note: we accept a hole */
            if (!(ci1->decomp_len == 0 ||
                  (ci1->decomp_len == l &&
                   ci1->is_compat == ci->is_compat)))
                break;
            n++;
        }
        return;
    }

    if (l <= 7) {
        n = 1;
        for(;;) {
            de->code = i;
            de->len = n;
            if (l == 1 && n == 1) {
                de->type = DECOMP_TYPE_C1;
            } else {
                assert(l <= 8);
                de->type = DECOMP_TYPE_L1 + l - 1;
            }
            de->c_len = l;
            de->cost = get_decomp_run_size(de) + tab_de[i + n].cost;
            if (de->cost < tab_de[i].cost) {
                tab_de[i] = *de;
            }

            if (!((i + n) <= CHARCODE_MAX && n < len_max))
                break;
            ci1 = &unicode_db[i + n];
            /* Note: we accept a hole */
            if (!(ci1->decomp_len == 0 ||
                  (ci1->decomp_len == l &&
                   ci1->is_compat == ci->is_compat &&
                   is_16bit(ci1->decomp_data, l))))
                break;
            n++;
        }
    }

    if (l <= 8 || l == 18) {
        int c_min, c_max, c;
        c_min = c_max = -1;
        n = 1;
        for(;;) {
            ci1 = &unicode_db[i + n - 1];
            for(j = 0; j < l; j++) {
                c = ci1->decomp_data[j];
                if (c == 0x20) {
                    /* we accept space for Arabic */
                } else if (c_min == -1) {
                    c_min = c_max = c;
                } else {
                    c_min = min_int(c_min, c);
                    c_max = max_int(c_max, c);
                }
            }
            if ((c_max - c_min) > 254)
                break;
            de->code = i;
            de->len = n;
            if (l == 18)
                de->type = DECOMP_TYPE_B18;
            else
                de->type = DECOMP_TYPE_B1 + l - 1;
            de->c_len = l;
            de->c_min = c_min;
            de->cost = get_decomp_run_size(de) + tab_de[i + n].cost;
            if (de->cost < tab_de[i].cost) {
                tab_de[i] = *de;
            }
            if (!((i + n) <= CHARCODE_MAX && n < len_max))
                break;
            ci1 = &unicode_db[i + n];
            if (!(ci1->decomp_len == l &&
                  ci1->is_compat == ci->is_compat))
                break;
            n++;
        }
    }

    /* find an ascii run */
    if (l <= 5 && is_short_tab(ci->decomp_data, l)) {
        n = 1;
        for(;;) {
            de->code = i;
            de->len = n;
            de->type = DECOMP_TYPE_S1 + l - 1;
            de->c_len = l;
            de->cost = get_decomp_run_size(de) + tab_de[i + n].cost;
            if (de->cost < tab_de[i].cost) {
                tab_de[i] = *de;
            }

            if (!((i + n) <= CHARCODE_MAX && n < len_max))
                break;
            ci1 = &unicode_db[i + n];
            /* Note: we accept a hole */
            if (!(ci1->decomp_len == 0 ||
                  (ci1->decomp_len == l &&
                   ci1->is_compat == ci->is_compat &&
                   is_short_tab(ci1->decomp_data, l))))
                break;
            n++;
        }
    }

    /* check if a single char is increasing */
    if (l <= 4) {
        int idx1, idx;

        for(idx1 = 1; (idx = decomp_incr_tab[l - 1][idx1]) >= 0; idx1++) {
            n = 1;
            for(;;) {
                de->code = i;
                de->len = n;
                de->type = decomp_incr_tab[l - 1][0] + idx1 - 1;
                de->c_len = l;
                de->cost = get_decomp_run_size(de) + tab_de[i + n].cost;
                if (de->cost < tab_de[i].cost) {
                    tab_de[i] = *de;
                }

                if (!((i + n) <= CHARCODE_MAX && n < len_max))
                    break;
                ci1 = &unicode_db[i + n];
                if (!(ci1->decomp_len == l &&
                      ci1->is_compat == ci->is_compat))
                    goto next1;
                for(j = 0; j < l; j++) {
                    if (j == idx) {
                        if (ci1->decomp_data[j] != ci->decomp_data[j] + n)
                            goto next1;
                    } else {
                        if (ci1->decomp_data[j] != ci->decomp_data[j])
                            goto next1;
                    }
                }
                n++;
            }
        next1: ;
        }
    }

    if (l == 3) {
        n = 1;
        for(;;) {
            de->code = i;
            de->len = n;
            de->type = DECOMP_TYPE_PAT3;
            de->c_len = l;
            de->cost = get_decomp_run_size(de) + tab_de[i + n].cost;
            if (de->cost < tab_de[i].cost) {
                tab_de[i] = *de;
            }
            if (!((i + n) <= CHARCODE_MAX && n < len_max))
                break;
            ci1 = &unicode_db[i + n];
            if (!(ci1->decomp_len == l &&
                  ci1->is_compat == ci->is_compat &&
                  ci1->decomp_data[1] <= 0xffff &&
                  ci1->decomp_data[0] == ci->decomp_data[0] &&
                  ci1->decomp_data[l - 1] == ci->decomp_data[l - 1]))
                break;
            n++;
        }
    }

    if (l == 2 && is_short(ci->decomp_data[1])) {
        n = 1;
        for(;;) {
            de->code = i;
            de->len = n;
            de->type = DECOMP_TYPE_LS2;
            de->c_len = l;
            de->cost = get_decomp_run_size(de) + tab_de[i + n].cost;
            if (de->cost < tab_de[i].cost) {
                tab_de[i] = *de;
            }
            if (!((i + n) <= CHARCODE_MAX && n < len_max))
                break;
            ci1 = &unicode_db[i + n];
            if (!(ci1->decomp_len == 0 ||
                  (ci1->decomp_len == l &&
                   ci1->is_compat == ci->is_compat &&
                   ci1->decomp_data[0] <= 0xffff &&
                   is_short(ci1->decomp_data[1]))))
                break;
            n++;
        }
    }

    if (l == 2) {
        bool is_16bit;

        n = 0;
        is_16bit = false;
        for(;;) {
            if (!((i + n + 1) <= CHARCODE_MAX && n + 2 <= len_max))
                break;
            ci1 = &unicode_db[i + n];
            if (!(ci1->decomp_len == l &&
                  ci1->is_compat == ci->is_compat &&
                  is_short(ci1->decomp_data[1])))
                break;
            if (!is_16bit && !is_short(ci1->decomp_data[0]))
                is_16bit = true;
            ci2 = &unicode_db[i + n + 1];
            if (!(ci2->decomp_len == l &&
                  ci2->is_compat == ci->is_compat &&
                  ci2->decomp_data[0] == to_lower_simple(ci1->decomp_data[0])  &&
                  ci2->decomp_data[1] == ci1->decomp_data[1]))
                break;
            n += 2;
            de->code = i;
            de->len = n;
            de->type = DECOMP_TYPE_S2_UL + is_16bit;
            de->c_len = l;
            de->cost = get_decomp_run_size(de) + tab_de[i + n].cost;
            if (de->cost < tab_de[i].cost) {
                tab_de[i] = *de;
            }
        }
    }
}

void put16(uint8_t *data_buf, int *pidx, uint16_t c)
{
    int idx;
    idx = *pidx;
    data_buf[idx++] = c;
    data_buf[idx++] = c >> 8;
    *pidx = idx;
}

void add_decomp_data(uint8_t *data_buf, int *pidx, DecompEntry *de)
{
    int i, j, idx, c;
    CCInfo *ci;

    idx = *pidx;
    de->data_index = idx;
    if (de->type <= DECOMP_TYPE_C1) {
        ci = &unicode_db[de->code];
        assert(ci->decomp_len == 1);
        de->data_index = ci->decomp_data[0];
    } else if (de->type <= DECOMP_TYPE_L7) {
        for(i = 0; i < de->len; i++) {
            ci = &unicode_db[de->code + i];
            for(j = 0; j < de->c_len; j++) {
                if (ci->decomp_len == 0)
                    c = 0;
                else
                    c = ci->decomp_data[j];
                put16(data_buf, &idx,  c);
            }
        }
    } else if (de->type <= DECOMP_TYPE_LL2) {
        int n, p, k;
        n = (de->len * de->c_len * 18 + 7) / 8;
        p = de->len * de->c_len * 2;
        memset(data_buf + idx, 0, n);
        k = 0;
        for(i = 0; i < de->len; i++) {
            ci = &unicode_db[de->code + i];
            for(j = 0; j < de->c_len; j++) {
                if (ci->decomp_len == 0)
                    c = 0;
                else
                    c = ci->decomp_data[j];
                data_buf[idx + k * 2] = c;
                data_buf[idx + k * 2 + 1] = c >> 8;
                data_buf[idx + p + (k / 4)] |= (c >> 16) << ((k % 4) * 2);
                k++;
            }
        }
        idx += n;
    } else if (de->type <= DECOMP_TYPE_S5) {
        for(i = 0; i < de->len; i++) {
            ci = &unicode_db[de->code + i];
            for(j = 0; j < de->c_len; j++) {
                if (ci->decomp_len == 0)
                    c = 0;
                else
                    c = ci->decomp_data[j];
                c = get_short_code(c);
                assert(c >= 0);
                data_buf[idx++] = c;
            }
        }
    } else if (de->type <= DECOMP_TYPE_I4_2) {
        ci = &unicode_db[de->code];
        assert(ci->decomp_len == de->c_len);
        for(j = 0; j < de->c_len; j++)
            put16(data_buf, &idx, ci->decomp_data[j]);
    } else if (de->type <= DECOMP_TYPE_B18) {
        c = de->c_min;
        data_buf[idx++] = c;
        data_buf[idx++] = c >> 8;
        for(i = 0; i < de->len; i++) {
            ci = &unicode_db[de->code + i];
            for(j = 0; j < de->c_len; j++) {
                assert(ci->decomp_len == de->c_len);
                c = ci->decomp_data[j];
                if (c == 0x20) {
                    c = 0xff;
                } else {
                    c -= de->c_min;
                    assert((uint32_t)c <= 254);
                }
                data_buf[idx++] = c;
            }
        }
    } else if (de->type <= DECOMP_TYPE_LS2) {
        assert(de->c_len == 2);
        for(i = 0; i < de->len; i++) {
            ci = &unicode_db[de->code + i];
            if (ci->decomp_len == 0)
                c = 0;
            else
                c = ci->decomp_data[0];
            put16(data_buf, &idx,  c);

            if (ci->decomp_len == 0)
                c = 0;
            else
                c = ci->decomp_data[1];
            c = get_short_code(c);
            assert(c >= 0);
            data_buf[idx++] = c;
        }
    } else if (de->type <= DECOMP_TYPE_PAT3) {
        ci = &unicode_db[de->code];
        assert(ci->decomp_len == 3);
        put16(data_buf, &idx,  ci->decomp_data[0]);
        put16(data_buf, &idx,  ci->decomp_data[2]);
        for(i = 0; i < de->len; i++) {
            ci = &unicode_db[de->code + i];
            assert(ci->decomp_len == 3);
            put16(data_buf, &idx,  ci->decomp_data[1]);
        }
    } else if (de->type <= DECOMP_TYPE_S2_UL) {
        for(i = 0; i < de->len; i += 2) {
            ci = &unicode_db[de->code + i];
            c = ci->decomp_data[0];
            c = get_short_code(c);
            assert(c >= 0);
            data_buf[idx++] = c;
            c = ci->decomp_data[1];
            c = get_short_code(c);
            assert(c >= 0);
            data_buf[idx++] = c;
        }
    } else if (de->type <= DECOMP_TYPE_LS2_UL) {
        for(i = 0; i < de->len; i += 2) {
            ci = &unicode_db[de->code + i];
            c = ci->decomp_data[0];
            put16(data_buf, &idx,  c);
            c = ci->decomp_data[1];
            c = get_short_code(c);
            assert(c >= 0);
            data_buf[idx++] = c;
        }
    } else {
        abort();
    }
    *pidx = idx;
}

void build_compose_table(FILE *f, const DecompEntry *tab_de);

void build_decompose_table(FILE *f)
{
    int i, array_len, code_max, data_len, count;
    DecompEntry *tab_de, de_s, *de = &de_s;
    uint8_t *data_buf;

    code_max = CHARCODE_MAX;

    tab_de = mallocz((code_max + 2) * sizeof(*tab_de));

    for(i = code_max; i >= 0; i--) {
        find_decomp_run(tab_de, i);
    }

    /* build the data buffer */
    data_buf = malloc(100000);
    data_len = 0;
    array_len = 0;
    for(i = 0; i <= code_max; i++) {
        de = &tab_de[i];
        if (de->len != 0) {
            add_decomp_data(data_buf, &data_len, de);
            i += de->len - 1;
            array_len++;
        }
    }

#ifdef DUMP_DECOMP_TABLE
    /* dump */
    {
        int size, size1;

        printf("START LEN   TYPE  L C SIZE\n");
        size = 0;
        for(i = 0; i <= code_max; i++) {
            de = &tab_de[i];
            if (de->len != 0) {
                size1 = get_decomp_run_size(de);
                printf("%05x %3d %6s %2d %1d %4d\n", i, de->len,
                       decomp_type_str[de->type], de->c_len,
                       unicode_db[i].is_compat, size1);
                i += de->len - 1;
                size += size1;
            }
        }

        printf("array_len=%d estimated size=%d bytes actual=%d bytes\n",
               array_len, size, array_len * 6 + data_len);
    }
#endif

    fprintf(f, "static const uint32_t unicode_decomp_table1[%u] = {",
            array_len);
    count = 0;
    for(i = 0; i <= code_max; i++) {
        de = &tab_de[i];
        if (de->len != 0) {
            uint32_t v;
            if (count++ % 4 == 0)
                fprintf(f, "\n   ");
            v = (de->code << (32 - 18)) |
                (de->len << (32 - 18 - 7)) |
                (de->type << (32 - 18 - 7 - 6)) |
                unicode_db[de->code].is_compat;
            fprintf(f, " 0x%08x,", v);
            i += de->len - 1;
        }
    }
    fprintf(f, "\n};\n\n");

    fprintf(f, "static const uint16_t unicode_decomp_table2[%u] = {",
            array_len);
    count = 0;
    for(i = 0; i <= code_max; i++) {
        de = &tab_de[i];
        if (de->len != 0) {
            if (count++ % 8 == 0)
                fprintf(f, "\n   ");
            fprintf(f, " 0x%04x,", de->data_index);
            i += de->len - 1;
        }
    }
    fprintf(f, "\n};\n\n");

    fprintf(f, "static const uint8_t unicode_decomp_data[%u] = {",
            data_len);
    for(i = 0; i < data_len; i++) {
        if (i % 8 == 0)
            fprintf(f, "\n   ");
        fprintf(f, " 0x%02x,", data_buf[i]);
    }
    fprintf(f, "\n};\n\n");

    build_compose_table(f, tab_de);

    free(data_buf);

    free(tab_de);
}

typedef struct {
    uint32_t c[2];
    uint32_t p;
} ComposeEntry;

#define COMPOSE_LEN_MAX 10000

static int ce_cmp(const void *p1, const void *p2)
{
    const ComposeEntry *ce1 = p1;
    const ComposeEntry *ce2 = p2;
    int i;

    for(i = 0; i < 2; i++) {
        if (ce1->c[i] < ce2->c[i])
            return -1;
        else if (ce1->c[i] > ce2->c[i])
            return 1;
    }
    return 0;
}


static int get_decomp_pos(const DecompEntry *tab_de, int c)
{
    int i, v, k;
    const DecompEntry *de;

    k = 0;
    for(i = 0; i <= CHARCODE_MAX; i++) {
        de = &tab_de[i];
        if (de->len != 0) {
            if (c >= de->code && c < de->code + de->len) {
                v = c - de->code;
                assert(v < 64);
                v |= k << 6;
                assert(v < 65536);
                return v;
            }
            i += de->len - 1;
            k++;
        }
    }
    return -1;
}

void build_compose_table(FILE *f, const DecompEntry *tab_de)
{
    int i, v, tab_ce_len;
    ComposeEntry *ce, *tab_ce;

    tab_ce = malloc(sizeof(*tab_ce) * COMPOSE_LEN_MAX);
    tab_ce_len = 0;
    for(i = 0; i <= CHARCODE_MAX; i++) {
        CCInfo *ci = &unicode_db[i];
        if (ci->decomp_len == 2 && !ci->is_compat &&
            !ci->is_excluded) {
            assert(tab_ce_len < COMPOSE_LEN_MAX);
            ce = &tab_ce[tab_ce_len++];
            ce->c[0] = ci->decomp_data[0];
            ce->c[1] = ci->decomp_data[1];
            ce->p = i;
        }
    }
    qsort(tab_ce, tab_ce_len, sizeof(*tab_ce), ce_cmp);

    fprintf(f, "static const uint16_t unicode_comp_table[%u] = {",
            tab_ce_len);
    for(i = 0; i < tab_ce_len; i++) {
        if (i % 8 == 0)
            fprintf(f, "\n   ");
        v = get_decomp_pos(tab_de, tab_ce[i].p);
        if (v < 0) {
            printf("ERROR: entry for c=%04x not found\n",
                   tab_ce[i].p);
            exit(1);
        }
        fprintf(f, " 0x%04x,", v);
    }
    fprintf(f, "\n};\n\n");

    free(tab_ce);
}

#ifdef USE_TEST
void check_decompose_table(void)
{
    int c;
    CCInfo *ci;
    int res[UNICODE_DECOMP_LEN_MAX], *ref;
    int len, ref_len, is_compat;

    for(is_compat = 0; is_compat <= 1; is_compat++) {
        for(c = 0; c < CHARCODE_MAX; c++) {
            ci = &unicode_db[c];
            ref_len = ci->decomp_len;
            ref = ci->decomp_data;
            if (!is_compat && ci->is_compat) {
                ref_len = 0;
            }
            len = unicode_decomp_char((uint32_t *)res, c, is_compat);
            if (len != ref_len ||
                tabcmp(res, ref, ref_len) != 0) {
                printf("ERROR c=%05x compat=%d\n", c, is_compat);
                dump_str("res", res, len);
                dump_str("ref", ref, ref_len);
                exit(1);
            }
        }
    }
}

void check_compose_table(void)
{
    int i, p;
    /* XXX: we don't test all the cases */

    for(i = 0; i <= CHARCODE_MAX; i++) {
        CCInfo *ci = &unicode_db[i];
        if (ci->decomp_len == 2 && !ci->is_compat &&
            !ci->is_excluded) {
            p = unicode_compose_pair(ci->decomp_data[0], ci->decomp_data[1]);
            if (p != i) {
                printf("ERROR compose: c=%05x %05x -> %05x ref=%05x\n",
                       ci->decomp_data[0], ci->decomp_data[1], p, i);
                exit(1);
            }
        }
    }



}

#endif



#ifdef USE_TEST

void check_str(const char *msg, int num, const int *in_buf, int in_len,
               const int *buf1, int len1,
               const int *buf2, int len2)
{
    if (len1 != len2 || tabcmp(buf1, buf2, len1) != 0) {
        printf("%d: ERROR %s:\n", num, msg);
        dump_str(" in", in_buf, in_len);
        dump_str("res", buf1, len1);
        dump_str("ref", buf2, len2);
        exit(1);
    }
}

void check_cc_table(void)
{
    int cc, cc_ref, c;

    for(c = 0; c <= CHARCODE_MAX; c++) {
        cc_ref = unicode_db[c].combining_class;
        cc = unicode_get_cc(c);
        if (cc != cc_ref) {
            printf("ERROR: c=%04x cc=%d cc_ref=%d\n",
                   c, cc, cc_ref);
            exit(1);
        }
    }
#ifdef PROFILE
    {
        int64_t ti, count;

        ti = get_time_ns();
        count = 0;
        /* only do it on meaningful chars */
        for(c = 0x20; c <= 0xffff; c++) {
            cc_ref = unicode_db[c].combining_class;
            cc = unicode_get_cc(c);
            count++;
        }
        ti = get_time_ns() - ti;
        printf("cc time=%0.1f ns/char\n",
               (double)ti / count);
    }
#endif
}

void normalization_test(const char *filename)
{
    FILE *f;
    char line[4096], *p;
    int *in_str, *nfc_str, *nfd_str, *nfkc_str, *nfkd_str;
    int in_len, nfc_len, nfd_len, nfkc_len, nfkd_len;
    int *buf, buf_len, pos;

    f = fopen(filename, "rb");
    if (!f) {
        perror(filename);
        exit(1);
    }
    pos = 0;
    for(;;) {
        if (!get_line(line, sizeof(line), f))
            break;
        pos++;
        p = line;
        while (isspace(*p))
            p++;
        if (*p == '#' || *p == '@')
            continue;
        in_str = get_field_str(&in_len, p, 0);
        nfc_str = get_field_str(&nfc_len, p, 1);
        nfd_str = get_field_str(&nfd_len, p, 2);
        nfkc_str = get_field_str(&nfkc_len, p, 3);
        nfkd_str = get_field_str(&nfkd_len, p, 4);

        //        dump_str("in", in_str, in_len);

        buf_len = unicode_normalize((uint32_t **)&buf, (uint32_t *)in_str, in_len, UNICODE_NFD, NULL, NULL);
        check_str("nfd", pos, in_str, in_len, buf, buf_len, nfd_str, nfd_len);
        free(buf);

        buf_len = unicode_normalize((uint32_t **)&buf, (uint32_t *)in_str, in_len, UNICODE_NFKD, NULL, NULL);
        check_str("nfkd", pos, in_str, in_len, buf, buf_len, nfkd_str, nfkd_len);
        free(buf);

        buf_len = unicode_normalize((uint32_t **)&buf, (uint32_t *)in_str, in_len, UNICODE_NFC, NULL, NULL);
        check_str("nfc", pos, in_str, in_len, buf, buf_len, nfc_str, nfc_len);
        free(buf);

        buf_len = unicode_normalize((uint32_t **)&buf, (uint32_t *)in_str, in_len, UNICODE_NFKC, NULL, NULL);
        check_str("nfkc", pos, in_str, in_len, buf, buf_len, nfkc_str, nfkc_len);
        free(buf);

        free(in_str);
        free(nfc_str);
        free(nfd_str);
        free(nfkc_str);
        free(nfkd_str);
    }
    fclose(f);
}
#endif

int main(int argc, char **argv)
{
    const char *unicode_db_path, *outfilename;
    char filename[1024];

    if (argc < 2) {
        printf("usage: %s unicode_db_path [output_file]\n"
               "\n"
               "If no output_file is given, a self test is done using the current unicode library\n",
               argv[0]);
        exit(1);
    }
    unicode_db_path = argv[1];
    outfilename = NULL;
    if (argc >= 3)
        outfilename = argv[2];

    unicode_db = mallocz(sizeof(unicode_db[0]) * (CHARCODE_MAX + 1));
    re_string_list_init(&rgi_emoji_zwj_sequence);
    dbuf_init(&rgi_emoji_tag_sequence);

    snprintf(filename, sizeof(filename), "%s/UnicodeData.txt", unicode_db_path);

    parse_unicode_data(filename);

    snprintf(filename, sizeof(filename), "%s/SpecialCasing.txt", unicode_db_path);
    parse_special_casing(unicode_db, filename);

    snprintf(filename, sizeof(filename), "%s/CaseFolding.txt", unicode_db_path);
    parse_case_folding(unicode_db, filename);

    snprintf(filename, sizeof(filename), "%s/CompositionExclusions.txt", unicode_db_path);
    parse_composition_exclusions(filename);

    snprintf(filename, sizeof(filename), "%s/DerivedCoreProperties.txt", unicode_db_path);
    parse_derived_core_properties(filename);

    snprintf(filename, sizeof(filename), "%s/DerivedNormalizationProps.txt", unicode_db_path);
    parse_derived_norm_properties(filename);

    snprintf(filename, sizeof(filename), "%s/PropList.txt", unicode_db_path);
    parse_prop_list(filename);

    snprintf(filename, sizeof(filename), "%s/Scripts.txt", unicode_db_path);
    parse_scripts(filename);

    snprintf(filename, sizeof(filename), "%s/ScriptExtensions.txt",
             unicode_db_path);
    parse_script_extensions(filename);

    snprintf(filename, sizeof(filename), "%s/emoji-data.txt",
             unicode_db_path);
    parse_prop_list(filename);

    snprintf(filename, sizeof(filename), "%s/emoji-sequences.txt",
             unicode_db_path);
    parse_sequence_prop_list(filename);

    snprintf(filename, sizeof(filename), "%s/emoji-zwj-sequences.txt",
             unicode_db_path);
    parse_sequence_prop_list(filename);

    //    dump_unicode_data(unicode_db);
    build_conv_table(unicode_db);

#ifdef DUMP_CASE_FOLDING_SPECIAL_CASES
    dump_case_folding_special_cases(unicode_db);
#endif

    if (!outfilename) {
#ifdef USE_TEST
        check_case_conv();
        check_flags();
        check_decompose_table();
        check_compose_table();
        check_cc_table();
        snprintf(filename, sizeof(filename), "%s/NormalizationTest.txt", unicode_db_path);
        normalization_test(filename);
#else
        fprintf(stderr, "Tests are not compiled\n");
        exit(1);
#endif
    } else
    {
        FILE *fo;
        char license[8192] = {0};

        snprintf(filename, sizeof(filename), "%s/license.txt", unicode_db_path);
        fo = fopen(filename, "r");
        if (!fo) {
            perror(filename);
            exit(1);
        }
        fread(license, 1, sizeof(license)-1, fo);
        fclose(fo);

        fo = fopen(outfilename, "wb");
        if (!fo) {
            perror(outfilename);
            exit(1);
        }
        fprintf(fo,
                "/* Compressed unicode tables */\n"
                "/* Automatically generated file - do not edit */\n"
                "\n"
                "/*\n%s*/\n"
                "\n"
                "#include <stdint.h>\n"
                "\n", license);
        dump_case_conv_table(fo);
        compute_internal_props();
        build_flags_tables(fo);
        build_cc_table(fo);
        build_decompose_table(fo);
        build_general_category_table(fo);
        build_script_table(fo);
        build_script_ext_table(fo);
        build_prop_list_table(fo);
        build_sequence_prop_list_table(fo);
        fclose(fo);
    }

    re_string_list_free(&rgi_emoji_zwj_sequence);
    dbuf_free(&rgi_emoji_tag_sequence);
    return 0;
}
