#!/bin/sh

include_dir=../../include/openssl
out=${include_dir}/lhash_macros.h

cat > $out << EOF
/* Copyright (c) 2014, Google Inc.
 *
 * Permission to use, copy, modify, and/or distribute this software for any
 * purpose with or without fee is hereby granted, provided that the above
 * copyright notice and this permission notice appear in all copies.
 *
 * THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES
 * WITH REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY
 * SPECIAL, DIRECT, INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES
 * WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN ACTION
 * OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF OR IN
 * CONNECTION WITH THE USE OR PERFORMANCE OF THIS SOFTWARE. */

#if !defined(IN_LHASH_H)
#error "Don't include this file directly. Include lhash.h"
#endif

EOF

output_lhash () {
  type=$1

  cat >> $out << EOF
/* ${type} */
#define lh_${type}_new(hash, comp)\\
((LHASH_OF(${type})*) lh_new(CHECKED_CAST(lhash_hash_func, uint32_t (*) (const ${type} *), hash), CHECKED_CAST(lhash_cmp_func, int (*) (const ${type} *a, const ${type} *b), comp)))

#define lh_${type}_free(lh)\\
  lh_free(CHECKED_CAST(_LHASH*, LHASH_OF(${type})*, lh));

#define lh_${type}_num_items(lh)\\
  lh_num_items(CHECKED_CAST(_LHASH*, LHASH_OF(${type})*, lh))

#define lh_${type}_retrieve(lh, data)\\
  ((${type}*) lh_retrieve(CHECKED_CAST(_LHASH*, LHASH_OF(${type})*, lh), CHECKED_CAST(void*, ${type}*, data)))

#define lh_${type}_insert(lh, old_data, data)\\
  lh_insert(CHECKED_CAST(_LHASH*, LHASH_OF(${type})*, lh), CHECKED_CAST(void**, ${type}**, old_data), CHECKED_CAST(void*, ${type}*, data))

#define lh_${type}_delete(lh, data)\\
  ((${type}*) lh_delete(CHECKED_CAST(_LHASH*, LHASH_OF(${type})*, lh), CHECKED_CAST(void*, ${type}*, data)))

#define lh_${type}_doall(lh, func)\\
  lh_doall(CHECKED_CAST(_LHASH*, LHASH_OF(${type})*, lh), CHECKED_CAST(void (*)(void*), void (*) (${type}*), func));

#define lh_${type}_doall_arg(lh, func, arg)\\
  lh_doall_arg(CHECKED_CAST(_LHASH*, LHASH_OF(${type})*, lh), CHECKED_CAST(void (*)(void*, void*), void (*) (${type}*, void*), func), arg);


EOF
}

lhash_types=$(cat ${include_dir}/lhash.h | grep '^ \* LHASH_OF:' | sed -e 's/.*LHASH_OF://' -e 's/ .*//')

for type in $lhash_types; do
  echo Hash of ${type}
  output_lhash "${type}"
done

clang-format -i $out
