/* Copyright (C) 1995-1998 Eric Young (eay@cryptsoft.com)
 * All rights reserved.
 *
 * This package is an SSL implementation written
 * by Eric Young (eay@cryptsoft.com).
 * The implementation was written so as to conform with Netscapes SSL.
 *
 * This library is free for commercial and non-commercial use as long as
 * the following conditions are aheared to.  The following conditions
 * apply to all code found in this distribution, be it the RC4, RSA,
 * lhash, DES, etc., code; not just the SSL code.  The SSL documentation
 * included with this distribution is covered by the same copyright terms
 * except that the holder is Tim Hudson (tjh@cryptsoft.com).
 *
 * Copyright remains Eric Young's, and as such any Copyright notices in
 * the code are not to be removed.
 * If this package is used in a product, Eric Young should be given attribution
 * as the author of the parts of the library used.
 * This can be in the form of a textual message at program startup or
 * in documentation (online or textual) provided with the package.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 * 1. Redistributions of source code must retain the copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 * 3. All advertising materials mentioning features or use of this software
 *    must display the following acknowledgement:
 *    "This product includes cryptographic software written by
 *     Eric Young (eay@cryptsoft.com)"
 *    The word 'cryptographic' can be left out if the rouines from the library
 *    being used are not cryptographic related :-).
 * 4. If you include any Windows specific code (or a derivative thereof) from
 *    the apps directory (application code) you must include an acknowledgement:
 *    "This product includes software written by Tim Hudson (tjh@cryptsoft.com)"
 *
 * THIS SOFTWARE IS PROVIDED BY ERIC YOUNG ``AS IS'' AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE AUTHOR OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS
 * OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
 * LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY
 * OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE.
 *
 * The licence and distribution terms for any publically available version or
 * derivative of this code cannot be changed.  i.e. this code cannot simply be
 * copied and put under another distribution licence
 * [including the GNU Public Licence.] */

#include <openssl/conf.h>

#include <string.h>
#include <ctype.h>

#include <openssl/bio.h>
#include <openssl/buf.h>
#include <openssl/err.h>
#include <openssl/mem.h>

#include "conf_def.h"
#include "internal.h"
#include "../internal.h"


// The maximum length we can grow a value to after variable expansion. 64k
// should be more than enough for all reasonable uses.
#define MAX_CONF_VALUE_LENGTH 65536

static uint32_t conf_value_hash(const CONF_VALUE *v) {
  return (lh_strhash(v->section) << 2) ^ lh_strhash(v->name);
}

static int conf_value_cmp(const CONF_VALUE *a, const CONF_VALUE *b) {
  int i;

  if (a->section != b->section) {
    i = strcmp(a->section, b->section);
    if (i) {
      return i;
    }
  }

  if (a->name != NULL && b->name != NULL) {
    return strcmp(a->name, b->name);
  } else if (a->name == b->name) {
    return 0;
  } else {
    return (a->name == NULL) ? -1 : 1;
  }
}

CONF *NCONF_new(void *method) {
  CONF *conf;

  if (method != NULL) {
    return NULL;
  }

  conf = OPENSSL_malloc(sizeof(CONF));
  if (conf == NULL) {
    return NULL;
  }

  conf->data = lh_CONF_VALUE_new(conf_value_hash, conf_value_cmp);
  if (conf->data == NULL) {
    OPENSSL_free(conf);
    return NULL;
  }

  return conf;
}

CONF_VALUE *CONF_VALUE_new(void) {
  CONF_VALUE *v = OPENSSL_malloc(sizeof(CONF_VALUE));
  if (!v) {
    OPENSSL_PUT_ERROR(CONF, ERR_R_MALLOC_FAILURE);
    return NULL;
  }
  OPENSSL_memset(v, 0, sizeof(CONF_VALUE));
  return v;
}

static void value_free_contents(CONF_VALUE *value) {
  if (value->section) {
    OPENSSL_free(value->section);
  }
  if (value->name) {
    OPENSSL_free(value->name);
    if (value->value) {
      OPENSSL_free(value->value);
    }
  } else {
    if (value->value) {
      sk_CONF_VALUE_free((STACK_OF(CONF_VALUE)*)value->value);
    }
  }
}

static void value_free(CONF_VALUE *value) {
  value_free_contents(value);
  OPENSSL_free(value);
}

void NCONF_free(CONF *conf) {
  if (conf == NULL || conf->data == NULL) {
    return;
  }

  lh_CONF_VALUE_doall(conf->data, value_free);
  lh_CONF_VALUE_free(conf->data);
  OPENSSL_free(conf);
}

static CONF_VALUE *NCONF_new_section(const CONF *conf, const char *section) {
  STACK_OF(CONF_VALUE) *sk = NULL;
  int ok = 0;
  CONF_VALUE *v = NULL, *old_value;

  sk = sk_CONF_VALUE_new_null();
  v = CONF_VALUE_new();
  if (sk == NULL || v == NULL) {
    goto err;
  }
  v->section = OPENSSL_strdup(section);
  if (v->section == NULL) {
    goto err;
  }

  v->name = NULL;
  v->value = (char *)sk;

  if (!lh_CONF_VALUE_insert(conf->data, &old_value, v)) {
    goto err;
  }
  if (old_value) {
    value_free(old_value);
  }
  ok = 1;

err:
  if (!ok) {
    if (sk != NULL) {
      sk_CONF_VALUE_free(sk);
    }
    if (v != NULL) {
      OPENSSL_free(v);
    }
    v = NULL;
  }
  return v;
}

static int str_copy(CONF *conf, char *section, char **pto, char *from) {
  int q, r, rr = 0, to = 0, len = 0;
  char *s, *e, *rp, *rrp, *np, *cp, v;
  const char *p;
  BUF_MEM *buf;

  buf = BUF_MEM_new();
  if (buf == NULL) {
    return 0;
  }

  len = strlen(from) + 1;
  if (!BUF_MEM_grow(buf, len)) {
    goto err;
  }

  for (;;) {
    if (IS_QUOTE(conf, *from)) {
      q = *from;
      from++;
      while (!IS_EOF(conf, *from) && (*from != q)) {
        if (IS_ESC(conf, *from)) {
          from++;
          if (IS_EOF(conf, *from)) {
            break;
          }
        }
        buf->data[to++] = *(from++);
      }
      if (*from == q) {
        from++;
      }
    } else if (IS_DQUOTE(conf, *from)) {
      q = *from;
      from++;
      while (!IS_EOF(conf, *from)) {
        if (*from == q) {
          if (*(from + 1) == q) {
            from++;
          } else {
            break;
          }
        }
        buf->data[to++] = *(from++);
      }
      if (*from == q) {
        from++;
      }
    } else if (IS_ESC(conf, *from)) {
      from++;
      v = *(from++);
      if (IS_EOF(conf, v)) {
        break;
      } else if (v == 'r') {
        v = '\r';
      } else if (v == 'n') {
        v = '\n';
      } else if (v == 'b') {
        v = '\b';
      } else if (v == 't') {
        v = '\t';
      }
      buf->data[to++] = v;
    } else if (IS_EOF(conf, *from)) {
      break;
    } else if (*from == '$') {
      // try to expand it
      rrp = NULL;
      s = &(from[1]);
      if (*s == '{') {
        q = '}';
      } else if (*s == '(') {
        q = ')';
      } else {
        q = 0;
      }

      if (q) {
        s++;
      }
      cp = section;
      e = np = s;
      while (IS_ALPHA_NUMERIC(conf, *e)) {
        e++;
      }
      if (e[0] == ':' && e[1] == ':') {
        cp = np;
        rrp = e;
        rr = *e;
        *rrp = '\0';
        e += 2;
        np = e;
        while (IS_ALPHA_NUMERIC(conf, *e)) {
          e++;
        }
      }
      r = *e;
      *e = '\0';
      rp = e;
      if (q) {
        if (r != q) {
          OPENSSL_PUT_ERROR(CONF, CONF_R_NO_CLOSE_BRACE);
          goto err;
        }
        e++;
      }
      // So at this point we have
      // np which is the start of the name string which is
      //   '\0' terminated.
      // cp which is the start of the section string which is
      //   '\0' terminated.
      // e is the 'next point after'.
      // r and rr are the chars replaced by the '\0'
      // rp and rrp is where 'r' and 'rr' came from.
      p = NCONF_get_string(conf, cp, np);
      if (rrp != NULL) {
        *rrp = rr;
      }
      *rp = r;
      if (p == NULL) {
        OPENSSL_PUT_ERROR(CONF, CONF_R_VARIABLE_HAS_NO_VALUE);
        goto err;
      }
      size_t newsize = strlen(p) + buf->length - (e - from);
      if (newsize > MAX_CONF_VALUE_LENGTH) {
        OPENSSL_PUT_ERROR(CONF, CONF_R_VARIABLE_EXPANSION_TOO_LONG);
        goto err;
      }
      if (!BUF_MEM_grow_clean(buf, newsize)) {
        OPENSSL_PUT_ERROR(CONF, ERR_R_MALLOC_FAILURE);
        goto err;
      }
      while (*p) {
        buf->data[to++] = *(p++);
      }

      /* Since we change the pointer 'from', we also have
         to change the perceived length of the string it
         points at.  /RL */
      len -= e - from;
      from = e;

      /* In case there were no braces or parenthesis around
         the variable reference, we have to put back the
         character that was replaced with a '\0'.  /RL */
      *rp = r;
    } else {
      buf->data[to++] = *(from++);
    }
  }

  buf->data[to] = '\0';
  if (*pto != NULL) {
    OPENSSL_free(*pto);
  }
  *pto = buf->data;
  OPENSSL_free(buf);
  return 1;

err:
  if (buf != NULL) {
    BUF_MEM_free(buf);
  }
  return 0;
}

static CONF_VALUE *get_section(const CONF *conf, const char *section) {
  CONF_VALUE template;

  OPENSSL_memset(&template, 0, sizeof(template));
  template.section = (char *) section;
  return lh_CONF_VALUE_retrieve(conf->data, &template);
}

STACK_OF(CONF_VALUE) *NCONF_get_section(const CONF *conf, const char *section) {
  CONF_VALUE *section_value = get_section(conf, section);
  if (section_value == NULL) {
    return NULL;
  }
  return (STACK_OF(CONF_VALUE)*) section_value->value;
}

const char *NCONF_get_string(const CONF *conf, const char *section,
                             const char *name) {
  CONF_VALUE template, *value;

  OPENSSL_memset(&template, 0, sizeof(template));
  template.section = (char *) section;
  template.name = (char *) name;
  value = lh_CONF_VALUE_retrieve(conf->data, &template);
  if (value == NULL) {
    return NULL;
  }
  return value->value;
}

static int add_string(const CONF *conf, CONF_VALUE *section,
                      CONF_VALUE *value) {
  STACK_OF(CONF_VALUE) *section_stack = (STACK_OF(CONF_VALUE)*) section->value;
  CONF_VALUE *old_value;

  value->section = OPENSSL_strdup(section->section);
  if (!sk_CONF_VALUE_push(section_stack, value)) {
    return 0;
  }

  if (!lh_CONF_VALUE_insert(conf->data, &old_value, value)) {
    return 0;
  }
  if (old_value != NULL) {
    (void)sk_CONF_VALUE_delete_ptr(section_stack, old_value);
    value_free(old_value);
  }

  return 1;
}

static char *eat_ws(CONF *conf, char *p) {
  while (IS_WS(conf, *p) && !IS_EOF(conf, *p)) {
    p++;
  }
  return p;
}

#define scan_esc(conf, p) (((IS_EOF((conf), (p)[1])) ? ((p) + 1) : ((p) + 2)))

static char *eat_alpha_numeric(CONF *conf, char *p) {
  for (;;) {
    if (IS_ESC(conf, *p)) {
      p = scan_esc(conf, p);
      continue;
    }
    if (!IS_ALPHA_NUMERIC_PUNCT(conf, *p)) {
      return p;
    }
    p++;
  }
}

static char *scan_quote(CONF *conf, char *p) {
  int q = *p;

  p++;
  while (!IS_EOF(conf, *p) && *p != q) {
    if (IS_ESC(conf, *p)) {
      p++;
      if (IS_EOF(conf, *p)) {
        return p;
      }
    }
    p++;
  }
  if (*p == q) {
    p++;
  }
  return p;
}


static char *scan_dquote(CONF *conf, char *p) {
  int q = *p;

  p++;
  while (!(IS_EOF(conf, *p))) {
    if (*p == q) {
      if (*(p + 1) == q) {
        p++;
      } else {
        break;
      }
    }
    p++;
  }
  if (*p == q) {
    p++;
  }
  return p;
}

static void clear_comments(CONF *conf, char *p) {
  for (;;) {
    if (IS_FCOMMENT(conf, *p)) {
      *p = '\0';
      return;
    }
    if (!IS_WS(conf, *p)) {
      break;
    }
    p++;
  }

  for (;;) {
    if (IS_COMMENT(conf, *p)) {
      *p = '\0';
      return;
    }
    if (IS_DQUOTE(conf, *p)) {
      p = scan_dquote(conf, p);
      continue;
    }
    if (IS_QUOTE(conf, *p)) {
      p = scan_quote(conf, p);
      continue;
    }
    if (IS_ESC(conf, *p)) {
      p = scan_esc(conf, p);
      continue;
    }
    if (IS_EOF(conf, *p)) {
      return;
    } else {
      p++;
    }
  }
}

static int def_load_bio(CONF *conf, BIO *in, long *out_error_line) {
  static const size_t CONFBUFSIZE = 512;
  int bufnum = 0, i, ii;
  BUF_MEM *buff = NULL;
  char *s, *p, *end;
  int again;
  long eline = 0;
  char btmp[DECIMAL_SIZE(eline) + 1];
  CONF_VALUE *v = NULL, *tv;
  CONF_VALUE *sv = NULL;
  char *section = NULL, *buf;
  char *start, *psection, *pname;

  if ((buff = BUF_MEM_new()) == NULL) {
    OPENSSL_PUT_ERROR(CONF, ERR_R_BUF_LIB);
    goto err;
  }

  section = OPENSSL_strdup("default");
  if (section == NULL) {
    OPENSSL_PUT_ERROR(CONF, ERR_R_MALLOC_FAILURE);
    goto err;
  }

  sv = NCONF_new_section(conf, section);
  if (sv == NULL) {
    OPENSSL_PUT_ERROR(CONF, CONF_R_UNABLE_TO_CREATE_NEW_SECTION);
    goto err;
  }

  bufnum = 0;
  again = 0;
  for (;;) {
    if (!BUF_MEM_grow(buff, bufnum + CONFBUFSIZE)) {
      OPENSSL_PUT_ERROR(CONF, ERR_R_BUF_LIB);
      goto err;
    }
    p = &(buff->data[bufnum]);
    *p = '\0';
    BIO_gets(in, p, CONFBUFSIZE - 1);
    p[CONFBUFSIZE - 1] = '\0';
    ii = i = strlen(p);
    if (i == 0 && !again) {
      break;
    }
    again = 0;
    while (i > 0) {
      if ((p[i - 1] != '\r') && (p[i - 1] != '\n')) {
        break;
      } else {
        i--;
      }
    }
    // we removed some trailing stuff so there is a new
    // line on the end.
    if (ii && i == ii) {
      again = 1;  // long line
    } else {
      p[i] = '\0';
      eline++;  // another input line
    }

    // we now have a line with trailing \r\n removed

    // i is the number of bytes
    bufnum += i;

    v = NULL;
    // check for line continuation
    if (bufnum >= 1) {
      // If we have bytes and the last char '\\' and
      // second last char is not '\\'
      p = &(buff->data[bufnum - 1]);
      if (IS_ESC(conf, p[0]) && ((bufnum <= 1) || !IS_ESC(conf, p[-1]))) {
        bufnum--;
        again = 1;
      }
    }
    if (again) {
      continue;
    }
    bufnum = 0;
    buf = buff->data;

    clear_comments(conf, buf);
    s = eat_ws(conf, buf);
    if (IS_EOF(conf, *s)) {
      continue;  // blank line
    }
    if (*s == '[') {
      char *ss;

      s++;
      start = eat_ws(conf, s);
      ss = start;
    again:
      end = eat_alpha_numeric(conf, ss);
      p = eat_ws(conf, end);
      if (*p != ']') {
        if (*p != '\0' && ss != p) {
          ss = p;
          goto again;
        }
        OPENSSL_PUT_ERROR(CONF, CONF_R_MISSING_CLOSE_SQUARE_BRACKET);
        goto err;
      }
      *end = '\0';
      if (!str_copy(conf, NULL, &section, start)) {
        goto err;
      }
      if ((sv = get_section(conf, section)) == NULL) {
        sv = NCONF_new_section(conf, section);
      }
      if (sv == NULL) {
        OPENSSL_PUT_ERROR(CONF, CONF_R_UNABLE_TO_CREATE_NEW_SECTION);
        goto err;
      }
      continue;
    } else {
      pname = s;
      psection = NULL;
      end = eat_alpha_numeric(conf, s);
      if ((end[0] == ':') && (end[1] == ':')) {
        *end = '\0';
        end += 2;
        psection = pname;
        pname = end;
        end = eat_alpha_numeric(conf, end);
      }
      p = eat_ws(conf, end);
      if (*p != '=') {
        OPENSSL_PUT_ERROR(CONF, CONF_R_MISSING_EQUAL_SIGN);
        goto err;
      }
      *end = '\0';
      p++;
      start = eat_ws(conf, p);
      while (!IS_EOF(conf, *p)) {
        p++;
      }
      p--;
      while ((p != start) && (IS_WS(conf, *p))) {
        p--;
      }
      p++;
      *p = '\0';

      if (!(v = CONF_VALUE_new())) {
        goto err;
      }
      if (psection == NULL) {
        psection = section;
      }
      v->name = OPENSSL_strdup(pname);
      if (v->name == NULL) {
        OPENSSL_PUT_ERROR(CONF, ERR_R_MALLOC_FAILURE);
        goto err;
      }
      if (!str_copy(conf, psection, &(v->value), start)) {
        goto err;
      }

      if (strcmp(psection, section) != 0) {
        if ((tv = get_section(conf, psection)) == NULL) {
          tv = NCONF_new_section(conf, psection);
        }
        if (tv == NULL) {
          OPENSSL_PUT_ERROR(CONF, CONF_R_UNABLE_TO_CREATE_NEW_SECTION);
          goto err;
        }
      } else {
        tv = sv;
      }
      if (add_string(conf, tv, v) == 0) {
        OPENSSL_PUT_ERROR(CONF, ERR_R_MALLOC_FAILURE);
        goto err;
      }
      v = NULL;
    }
  }
  if (buff != NULL) {
    BUF_MEM_free(buff);
  }
  if (section != NULL) {
    OPENSSL_free(section);
  }
  return 1;

err:
  if (buff != NULL) {
    BUF_MEM_free(buff);
  }
  if (section != NULL) {
    OPENSSL_free(section);
  }
  if (out_error_line != NULL) {
    *out_error_line = eline;
  }
  BIO_snprintf(btmp, sizeof btmp, "%ld", eline);
  ERR_add_error_data(2, "line ", btmp);

  if (v != NULL) {
    if (v->name != NULL) {
      OPENSSL_free(v->name);
    }
    if (v->value != NULL) {
      OPENSSL_free(v->value);
    }
    if (v != NULL) {
      OPENSSL_free(v);
    }
  }
  return 0;
}

int NCONF_load(CONF *conf, const char *filename, long *out_error_line) {
  BIO *in = BIO_new_file(filename, "rb");
  int ret;

  if (in == NULL) {
    OPENSSL_PUT_ERROR(CONF, ERR_R_SYS_LIB);
    return 0;
  }

  ret = def_load_bio(conf, in, out_error_line);
  BIO_free(in);

  return ret;
}

int NCONF_load_bio(CONF *conf, BIO *bio, long *out_error_line) {
  return def_load_bio(conf, bio, out_error_line);
}

int CONF_parse_list(const char *list, char sep, int remove_whitespace,
                    int (*list_cb)(const char *elem, int len, void *usr),
                    void *arg) {
  int ret;
  const char *lstart, *tmpend, *p;

  if (list == NULL) {
    OPENSSL_PUT_ERROR(CONF, CONF_R_LIST_CANNOT_BE_NULL);
    return 0;
  }

  lstart = list;
  for (;;) {
    if (remove_whitespace) {
      while (*lstart && isspace((unsigned char)*lstart)) {
        lstart++;
      }
    }
    p = strchr(lstart, sep);
    if (p == lstart || !*lstart) {
      ret = list_cb(NULL, 0, arg);
    } else {
      if (p) {
        tmpend = p - 1;
      } else {
        tmpend = lstart + strlen(lstart) - 1;
      }
      if (remove_whitespace) {
        while (isspace((unsigned char)*tmpend)) {
          tmpend--;
        }
      }
      ret = list_cb(lstart, tmpend - lstart + 1, arg);
    }
    if (ret <= 0) {
      return ret;
    }
    if (p == NULL) {
      return 1;
    }
    lstart = p + 1;
  }
}

int CONF_modules_load_file(const char *filename, const char *appname,
                           unsigned long flags) {
  return 1;
}

void CONF_modules_free(void) {}

void OPENSSL_config(const char *config_name) {}

void OPENSSL_no_config(void) {}
