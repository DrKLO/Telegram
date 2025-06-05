// Copyright 1999-2016 The OpenSSL Project Authors. All Rights Reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     https://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

#include <stdio.h>
#include <string.h>

#include <openssl/asn1.h>
#include <openssl/asn1t.h>
#include <openssl/conf.h>
#include <openssl/err.h>
#include <openssl/mem.h>
#include <openssl/obj.h>
#include <openssl/x509.h>

#include "ext_dat.h"
#include "internal.h"


static STACK_OF(CONF_VALUE) *i2v_AUTHORITY_INFO_ACCESS(
    const X509V3_EXT_METHOD *method, void *ext, STACK_OF(CONF_VALUE) *ret);
static void *v2i_AUTHORITY_INFO_ACCESS(const X509V3_EXT_METHOD *method,
                                       const X509V3_CTX *ctx,
                                       const STACK_OF(CONF_VALUE) *nval);

const X509V3_EXT_METHOD v3_info = {
    NID_info_access,
    X509V3_EXT_MULTILINE,
    ASN1_ITEM_ref(AUTHORITY_INFO_ACCESS),
    0,
    0,
    0,
    0,
    0,
    0,
    i2v_AUTHORITY_INFO_ACCESS,
    v2i_AUTHORITY_INFO_ACCESS,
    0,
    0,
    NULL,
};

const X509V3_EXT_METHOD v3_sinfo = {
    NID_sinfo_access,
    X509V3_EXT_MULTILINE,
    ASN1_ITEM_ref(AUTHORITY_INFO_ACCESS),
    0,
    0,
    0,
    0,
    0,
    0,
    i2v_AUTHORITY_INFO_ACCESS,
    v2i_AUTHORITY_INFO_ACCESS,
    0,
    0,
    NULL,
};

ASN1_SEQUENCE(ACCESS_DESCRIPTION) = {
    ASN1_SIMPLE(ACCESS_DESCRIPTION, method, ASN1_OBJECT),
    ASN1_SIMPLE(ACCESS_DESCRIPTION, location, GENERAL_NAME),
} ASN1_SEQUENCE_END(ACCESS_DESCRIPTION)

IMPLEMENT_ASN1_ALLOC_FUNCTIONS(ACCESS_DESCRIPTION)

ASN1_ITEM_TEMPLATE(AUTHORITY_INFO_ACCESS) = ASN1_EX_TEMPLATE_TYPE(
    ASN1_TFLG_SEQUENCE_OF, 0, GeneralNames, ACCESS_DESCRIPTION)
ASN1_ITEM_TEMPLATE_END(AUTHORITY_INFO_ACCESS)

IMPLEMENT_ASN1_FUNCTIONS(AUTHORITY_INFO_ACCESS)

static STACK_OF(CONF_VALUE) *i2v_AUTHORITY_INFO_ACCESS(
    const X509V3_EXT_METHOD *method, void *ext, STACK_OF(CONF_VALUE) *ret) {
  const AUTHORITY_INFO_ACCESS *ainfo =
      reinterpret_cast<const AUTHORITY_INFO_ACCESS *>(ext);
  ACCESS_DESCRIPTION *desc;
  char objtmp[80], *name;
  CONF_VALUE *vtmp;
  STACK_OF(CONF_VALUE) *tret = ret;

  for (size_t i = 0; i < sk_ACCESS_DESCRIPTION_num(ainfo); i++) {
    STACK_OF(CONF_VALUE) *tmp;

    desc = sk_ACCESS_DESCRIPTION_value(ainfo, i);
    tmp = i2v_GENERAL_NAME(method, desc->location, tret);
    if (tmp == NULL) {
      goto err;
    }
    tret = tmp;
    vtmp = sk_CONF_VALUE_value(tret, i);
    i2t_ASN1_OBJECT(objtmp, sizeof objtmp, desc->method);

    if (OPENSSL_asprintf(&name, "%s - %s", objtmp, vtmp->name) == -1) {
      goto err;
    }
    OPENSSL_free(vtmp->name);
    vtmp->name = name;
  }
  if (ret == NULL && tret == NULL) {
    return sk_CONF_VALUE_new_null();
  }

  return tret;
err:
  if (ret == NULL && tret != NULL) {
    sk_CONF_VALUE_pop_free(tret, X509V3_conf_free);
  }
  return NULL;
}

static void *v2i_AUTHORITY_INFO_ACCESS(const X509V3_EXT_METHOD *method,
                                       const X509V3_CTX *ctx,
                                       const STACK_OF(CONF_VALUE) *nval) {
  AUTHORITY_INFO_ACCESS *ainfo = NULL;
  ACCESS_DESCRIPTION *acc;
  if (!(ainfo = sk_ACCESS_DESCRIPTION_new_null())) {
    return NULL;
  }
  for (size_t i = 0; i < sk_CONF_VALUE_num(nval); i++) {
    const CONF_VALUE *cnf = sk_CONF_VALUE_value(nval, i);
    if (!(acc = ACCESS_DESCRIPTION_new()) ||
        !sk_ACCESS_DESCRIPTION_push(ainfo, acc)) {
      goto err;
    }
    char *ptmp = strchr(cnf->name, ';');
    if (!ptmp) {
      OPENSSL_PUT_ERROR(X509V3, X509V3_R_INVALID_SYNTAX);
      goto err;
    }
    CONF_VALUE ctmp;
    ctmp.name = ptmp + 1;
    ctmp.value = cnf->value;
    if (!v2i_GENERAL_NAME_ex(acc->location, method, ctx, &ctmp, 0)) {
      goto err;
    }
    char *objtmp = OPENSSL_strndup(cnf->name, ptmp - cnf->name);
    if (objtmp == NULL) {
      goto err;
    }
    acc->method = OBJ_txt2obj(objtmp, 0);
    if (!acc->method) {
      OPENSSL_PUT_ERROR(X509V3, X509V3_R_BAD_OBJECT);
      ERR_add_error_data(2, "value=", objtmp);
      OPENSSL_free(objtmp);
      goto err;
    }
    OPENSSL_free(objtmp);
  }
  return ainfo;
err:
  sk_ACCESS_DESCRIPTION_pop_free(ainfo, ACCESS_DESCRIPTION_free);
  return NULL;
}
