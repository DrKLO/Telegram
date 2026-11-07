/*
 * QuickJS opcode definitions
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

#ifdef FMT
FMT(none)
FMT(none_int)
FMT(none_loc)
FMT(none_arg)
FMT(none_var_ref)
FMT(u8)
FMT(i8)
FMT(loc8)
FMT(const8)
FMT(label8)
FMT(u16)
FMT(i16)
FMT(label16)
FMT(npop)
FMT(npopx)
FMT(npop_u16)
FMT(loc)
FMT(arg)
FMT(var_ref)
FMT(u32)
FMT(u32x2)
FMT(i32)
FMT(const)
FMT(label)
FMT(atom)
FMT(atom_u8)
FMT(atom_u16)
FMT(atom_label_u8)
FMT(atom_label_u16)
FMT(label_u16)
#undef FMT
#endif /* FMT */

#ifdef DEF

#ifndef def
#define def(id, size, n_pop, n_push, f) DEF(id, size, n_pop, n_push, f)
#endif

DEF(invalid, 1, 0, 0, none) /* never emitted */

/* push values */
DEF(       push_i32, 5, 0, 1, i32)
DEF(     push_const, 5, 0, 1, const)
DEF(       fclosure, 5, 0, 1, const) /* must follow push_const */
DEF(push_atom_value, 5, 0, 1, atom)
DEF( private_symbol, 5, 0, 1, atom)
DEF(      undefined, 1, 0, 1, none)
DEF(           null, 1, 0, 1, none)
DEF(      push_this, 1, 0, 1, none) /* only used at the start of a function */
DEF(     push_false, 1, 0, 1, none)
DEF(      push_true, 1, 0, 1, none)
DEF(         object, 1, 0, 1, none)
DEF( special_object, 2, 0, 1, u8) /* only used at the start of a function */
DEF(           rest, 3, 0, 1, u16) /* only used at the start of a function */

DEF(           drop, 1, 1, 0, none) /* a -> */
DEF(            nip, 1, 2, 1, none) /* a b -> b */
DEF(           nip1, 1, 3, 2, none) /* a b c -> b c */
DEF(            dup, 1, 1, 2, none) /* a -> a a */
DEF(           dup1, 1, 2, 3, none) /* a b -> a a b */
DEF(           dup2, 1, 2, 4, none) /* a b -> a b a b */
DEF(           dup3, 1, 3, 6, none) /* a b c -> a b c a b c */
DEF(        insert2, 1, 2, 3, none) /* obj a -> a obj a (dup_x1) */
DEF(        insert3, 1, 3, 4, none) /* obj prop a -> a obj prop a (dup_x2) */
DEF(        insert4, 1, 4, 5, none) /* this obj prop a -> a this obj prop a */
DEF(          perm3, 1, 3, 3, none) /* obj a b -> a obj b */
DEF(          perm4, 1, 4, 4, none) /* obj prop a b -> a obj prop b */
DEF(          perm5, 1, 5, 5, none) /* this obj prop a b -> a this obj prop b */
DEF(           swap, 1, 2, 2, none) /* a b -> b a */
DEF(          swap2, 1, 4, 4, none) /* a b c d -> c d a b */
DEF(          rot3l, 1, 3, 3, none) /* x a b -> a b x */
DEF(          rot3r, 1, 3, 3, none) /* a b x -> x a b */
DEF(          rot4l, 1, 4, 4, none) /* x a b c -> a b c x */
DEF(          rot5l, 1, 5, 5, none) /* x a b c d -> a b c d x */

DEF(call_constructor, 3, 2, 1, npop) /* func new.target args -> ret. arguments are not counted in n_pop */
DEF(           call, 3, 1, 1, npop) /* arguments are not counted in n_pop */
DEF(      tail_call, 3, 1, 0, npop) /* arguments are not counted in n_pop */
DEF(    call_method, 3, 2, 1, npop) /* arguments are not counted in n_pop */
DEF(tail_call_method, 3, 2, 0, npop) /* arguments are not counted in n_pop */
DEF(     array_from, 3, 0, 1, npop) /* arguments are not counted in n_pop */
DEF(          apply, 3, 3, 1, u16)
DEF(         return, 1, 1, 0, none)
DEF(   return_undef, 1, 0, 0, none)
DEF(check_ctor_return, 1, 1, 2, none)
DEF(     check_ctor, 1, 0, 0, none)
DEF(      init_ctor, 1, 0, 1, none)
DEF(    check_brand, 1, 2, 2, none) /* this_obj func -> this_obj func */
DEF(      add_brand, 1, 2, 0, none) /* this_obj home_obj -> */
DEF(   return_async, 1, 1, 0, none)
DEF(          throw, 1, 1, 0, none)
DEF(    throw_error, 6, 0, 0, atom_u8)
DEF(           eval, 5, 1, 1, npop_u16) /* func args... -> ret_val */
DEF(     apply_eval, 3, 2, 1, u16) /* func array -> ret_eval */
DEF(         regexp, 1, 2, 1, none) /* create a RegExp object from the pattern and a
                                       bytecode string */
DEF(      get_super, 1, 1, 1, none)
DEF(         import, 1, 2, 1, none) /* dynamic module import */

DEF(  get_var_undef, 5, 0, 1, atom) /* push undefined if the variable does not exist */
DEF(        get_var, 5, 0, 1, atom) /* throw an exception if the variable does not exist */
DEF(        put_var, 5, 1, 0, atom) /* must come after get_var */
DEF(   put_var_init, 5, 1, 0, atom) /* must come after put_var. Used to initialize a global lexical variable */

DEF(  get_ref_value, 1, 2, 3, none)
DEF(  put_ref_value, 1, 3, 0, none)

DEF(     define_var, 6, 0, 0, atom_u8)
DEF(check_define_var, 6, 0, 0, atom_u8)
DEF(    define_func, 6, 1, 0, atom_u8)

// order matters, see IC counterparts
DEF(      get_field, 5, 1, 1, atom)
DEF(     get_field2, 5, 1, 2, atom)
DEF(      put_field, 5, 2, 0, atom)

DEF( get_private_field, 1, 2, 1, none) /* obj prop -> value */
DEF( put_private_field, 1, 3, 0, none) /* obj value prop -> */
DEF(define_private_field, 1, 3, 1, none) /* obj prop value -> obj */
DEF(   get_array_el, 1, 2, 1, none)
DEF(  get_array_el2, 1, 2, 2, none) /* obj prop -> obj value */
DEF(   put_array_el, 1, 3, 0, none)
DEF(get_super_value, 1, 3, 1, none) /* this obj prop -> value */
DEF(put_super_value, 1, 4, 0, none) /* this obj prop value -> */
DEF(   define_field, 5, 2, 1, atom)
DEF(       set_name, 5, 1, 1, atom)
DEF(set_name_computed, 1, 2, 2, none)
DEF(      set_proto, 1, 2, 1, none)
DEF(set_home_object, 1, 2, 2, none)
DEF(define_array_el, 1, 3, 2, none)
DEF(         append, 1, 3, 2, none) /* append enumerated object, update length */
DEF(copy_data_properties, 2, 3, 3, u8)
DEF(  define_method, 6, 2, 1, atom_u8)
DEF(define_method_computed, 2, 3, 1, u8) /* must come after define_method */
DEF(   define_class, 6, 2, 2, atom_u8) /* parent ctor -> ctor proto */
DEF(   define_class_computed, 6, 3, 3, atom_u8) /* field_name parent ctor -> field_name ctor proto (class with computed name) */

DEF(        get_loc, 3, 0, 1, loc)
DEF(        put_loc, 3, 1, 0, loc) /* must come after get_loc */
DEF(        set_loc, 3, 1, 1, loc) /* must come after put_loc */
DEF(        get_arg, 3, 0, 1, arg)
DEF(        put_arg, 3, 1, 0, arg) /* must come after get_arg */
DEF(        set_arg, 3, 1, 1, arg) /* must come after put_arg */
DEF(    get_var_ref, 3, 0, 1, var_ref)
DEF(    put_var_ref, 3, 1, 0, var_ref) /* must come after get_var_ref */
DEF(    set_var_ref, 3, 1, 1, var_ref) /* must come after put_var_ref */
DEF(set_loc_uninitialized, 3, 0, 0, loc)
DEF(  get_loc_check, 3, 0, 1, loc)
DEF(  put_loc_check, 3, 1, 0, loc) /* must come after get_loc_check */
DEF(  put_loc_check_init, 3, 1, 0, loc)
DEF(get_var_ref_check, 3, 0, 1, var_ref)
DEF(put_var_ref_check, 3, 1, 0, var_ref) /* must come after get_var_ref_check */
DEF(put_var_ref_check_init, 3, 1, 0, var_ref)
DEF(      close_loc, 3, 0, 0, loc)
DEF(       if_false, 5, 1, 0, label)
DEF(        if_true, 5, 1, 0, label) /* must come after if_false */
DEF(           goto, 5, 0, 0, label) /* must come after if_true */
DEF(          catch, 5, 0, 1, label)
DEF(          gosub, 5, 0, 0, label) /* used to execute the finally block */
DEF(            ret, 1, 1, 0, none) /* used to return from the finally block */
DEF(      nip_catch, 1, 2, 1, none) /* catch ... a -> a */

DEF(   check_object, 1, 1, 1, none)
DEF(      to_object, 1, 1, 1, none)
//DEF(      to_string, 1, 1, 1, none)
DEF(     to_propkey, 1, 1, 1, none)
DEF(    to_propkey2, 1, 2, 2, none)

DEF(   with_get_var, 10, 1, 0, atom_label_u8)     /* must be in the same order as scope_xxx */
DEF(   with_put_var, 10, 2, 1, atom_label_u8)     /* must be in the same order as scope_xxx */
DEF(with_delete_var, 10, 1, 0, atom_label_u8)     /* must be in the same order as scope_xxx */
DEF(  with_make_ref, 10, 1, 0, atom_label_u8)     /* must be in the same order as scope_xxx */
DEF(   with_get_ref, 10, 1, 0, atom_label_u8)     /* must be in the same order as scope_xxx */
DEF(with_get_ref_undef, 10, 1, 0, atom_label_u8)

DEF(   make_loc_ref, 7, 0, 2, atom_u16)
DEF(   make_arg_ref, 7, 0, 2, atom_u16)
DEF(make_var_ref_ref, 7, 0, 2, atom_u16)
DEF(   make_var_ref, 5, 0, 2, atom)

DEF(   for_in_start, 1, 1, 1, none)
DEF(   for_of_start, 1, 1, 3, none)
DEF(for_await_of_start, 1, 1, 3, none)
DEF(    for_in_next, 1, 1, 3, none)
DEF(    for_of_next, 2, 3, 5, u8)
DEF(iterator_get_value_done, 1, 1, 2, none)
DEF( iterator_close, 1, 3, 0, none)
DEF(  iterator_next, 1, 4, 4, none)
DEF(  iterator_call, 2, 4, 5, u8)
DEF(  initial_yield, 1, 0, 0, none)
DEF(          yield, 1, 1, 2, none)
DEF(     yield_star, 1, 1, 2, none)
DEF(async_yield_star, 1, 1, 2, none)
DEF(          await, 1, 1, 1, none)

/* arithmetic/logic operations */
DEF(            neg, 1, 1, 1, none)
DEF(           plus, 1, 1, 1, none)
DEF(            dec, 1, 1, 1, none)
DEF(            inc, 1, 1, 1, none)
DEF(       post_dec, 1, 1, 2, none)
DEF(       post_inc, 1, 1, 2, none)
DEF(        dec_loc, 2, 0, 0, loc8)
DEF(        inc_loc, 2, 0, 0, loc8)
DEF(        add_loc, 2, 1, 0, loc8)
DEF(            not, 1, 1, 1, none)
DEF(           lnot, 1, 1, 1, none)
DEF(         typeof, 1, 1, 1, none)
DEF(         delete, 1, 2, 1, none)
DEF(     delete_var, 5, 0, 1, atom)

/* warning: order matters (see js_parse_assign_expr) */
DEF(            mul, 1, 2, 1, none)
DEF(            div, 1, 2, 1, none)
DEF(            mod, 1, 2, 1, none)
DEF(            add, 1, 2, 1, none)
DEF(            sub, 1, 2, 1, none)
DEF(            shl, 1, 2, 1, none)
DEF(            sar, 1, 2, 1, none)
DEF(            shr, 1, 2, 1, none)
DEF(            and, 1, 2, 1, none)
DEF(            xor, 1, 2, 1, none)
DEF(             or, 1, 2, 1, none)
DEF(            pow, 1, 2, 1, none)

DEF(             lt, 1, 2, 1, none)
DEF(            lte, 1, 2, 1, none)
DEF(             gt, 1, 2, 1, none)
DEF(            gte, 1, 2, 1, none)
DEF(     instanceof, 1, 2, 1, none)
DEF(             in, 1, 2, 1, none)
DEF(             eq, 1, 2, 1, none)
DEF(            neq, 1, 2, 1, none)
DEF(      strict_eq, 1, 2, 1, none)
DEF(     strict_neq, 1, 2, 1, none)
DEF(is_undefined_or_null, 1, 1, 1, none)
DEF(     private_in, 1, 2, 1, none)
DEF(push_bigint_i32, 5, 0, 1, i32)
DEF( using_dispose_init, 1, 0, 1, none)
DEF(      using_dispose, 3, 1, 1, loc)
DEF(using_dispose_async, 3, 0, 1, loc)
DEF(using_dispose_merge, 1, 2, 1, none)
DEF(  using_dispose_end, 1, 1, 0, none)
DEF(        using_check, 2, 1, 2, u8)
/* must be the last non short and non temporary opcode */
DEF(            nop, 1, 0, 0, none)

/* temporary opcodes: never emitted in the final bytecode */

def(    enter_scope, 3, 0, 0, u16)  /* emitted in phase 1, removed in phase 2 */
def(    leave_scope, 3, 0, 0, u16)  /* emitted in phase 1, removed in phase 2 */

def(          label, 5, 0, 0, label) /* emitted in phase 1, removed in phase 3 */

def(scope_get_var_undef, 7, 0, 1, atom_u16) /* emitted in phase 1, removed in phase 2 */
def(  scope_get_var, 7, 0, 1, atom_u16) /* emitted in phase 1, removed in phase 2 */
def(  scope_put_var, 7, 1, 0, atom_u16) /* emitted in phase 1, removed in phase 2 */
def(scope_delete_var, 7, 0, 1, atom_u16) /* emitted in phase 1, removed in phase 2 */
def( scope_make_ref, 11, 0, 2, atom_label_u16) /* emitted in phase 1, removed in phase 2 */
def(  scope_get_ref, 7, 0, 2, atom_u16) /* emitted in phase 1, removed in phase 2 */
def(scope_put_var_init, 7, 0, 2, atom_u16) /* emitted in phase 1, removed in phase 2 */
def(scope_get_private_field, 7, 1, 1, atom_u16) /* obj -> value, emitted in phase 1, removed in phase 2 */
def(scope_get_private_field2, 7, 1, 2, atom_u16) /* obj -> obj value, emitted in phase 1, removed in phase 2 */
def(scope_put_private_field, 7, 2, 0, atom_u16) /* obj value ->, emitted in phase 1, removed in phase 2 */
def(scope_in_private_field, 7, 1, 1, atom_u16) /* obj -> res emitted in phase 1, removed in phase 2 */
def(get_field_opt_chain, 5, 1, 1, atom) /* emitted in phase 1, removed in phase 2 */
def(get_array_el_opt_chain, 1, 2, 1, none) /* emitted in phase 1, removed in phase 2 */
def( set_class_name, 5, 1, 1, u32) /* emitted in phase 1, removed in phase 2 */

def( dispose_scope, 3, 0, 0, u16) /* emitted in phase 1, removed in phase 2 */

def(     source_loc, 9, 0, 0, u32x2) /* emitted in phase 1, removed in phase 3 */

DEF(    push_minus1, 1, 0, 1, none_int)
DEF(         push_0, 1, 0, 1, none_int)
DEF(         push_1, 1, 0, 1, none_int)
DEF(         push_2, 1, 0, 1, none_int)
DEF(         push_3, 1, 0, 1, none_int)
DEF(         push_4, 1, 0, 1, none_int)
DEF(         push_5, 1, 0, 1, none_int)
DEF(         push_6, 1, 0, 1, none_int)
DEF(         push_7, 1, 0, 1, none_int)
DEF(        push_i8, 2, 0, 1, i8)
DEF(       push_i16, 3, 0, 1, i16)
DEF(    push_const8, 2, 0, 1, const8)
DEF(      fclosure8, 2, 0, 1, const8) /* must follow push_const8 */
DEF(push_empty_string, 1, 0, 1, none)

DEF(       get_loc8, 2, 0, 1, loc8)
DEF(       put_loc8, 2, 1, 0, loc8)
DEF(       set_loc8, 2, 1, 1, loc8)

DEF(  get_loc0_loc1, 1, 0, 2, none_loc)
DEF(       get_loc0, 1, 0, 1, none_loc)
DEF(       get_loc1, 1, 0, 1, none_loc)
DEF(       get_loc2, 1, 0, 1, none_loc)
DEF(       get_loc3, 1, 0, 1, none_loc)
DEF(       put_loc0, 1, 1, 0, none_loc)
DEF(       put_loc1, 1, 1, 0, none_loc)
DEF(       put_loc2, 1, 1, 0, none_loc)
DEF(       put_loc3, 1, 1, 0, none_loc)
DEF(       set_loc0, 1, 1, 1, none_loc)
DEF(       set_loc1, 1, 1, 1, none_loc)
DEF(       set_loc2, 1, 1, 1, none_loc)
DEF(       set_loc3, 1, 1, 1, none_loc)
DEF(       get_arg0, 1, 0, 1, none_arg)
DEF(       get_arg1, 1, 0, 1, none_arg)
DEF(       get_arg2, 1, 0, 1, none_arg)
DEF(       get_arg3, 1, 0, 1, none_arg)
DEF(       put_arg0, 1, 1, 0, none_arg)
DEF(       put_arg1, 1, 1, 0, none_arg)
DEF(       put_arg2, 1, 1, 0, none_arg)
DEF(       put_arg3, 1, 1, 0, none_arg)
DEF(       set_arg0, 1, 1, 1, none_arg)
DEF(       set_arg1, 1, 1, 1, none_arg)
DEF(       set_arg2, 1, 1, 1, none_arg)
DEF(       set_arg3, 1, 1, 1, none_arg)
DEF(   get_var_ref0, 1, 0, 1, none_var_ref)
DEF(   get_var_ref1, 1, 0, 1, none_var_ref)
DEF(   get_var_ref2, 1, 0, 1, none_var_ref)
DEF(   get_var_ref3, 1, 0, 1, none_var_ref)
DEF(   put_var_ref0, 1, 1, 0, none_var_ref)
DEF(   put_var_ref1, 1, 1, 0, none_var_ref)
DEF(   put_var_ref2, 1, 1, 0, none_var_ref)
DEF(   put_var_ref3, 1, 1, 0, none_var_ref)
DEF(   set_var_ref0, 1, 1, 1, none_var_ref)
DEF(   set_var_ref1, 1, 1, 1, none_var_ref)
DEF(   set_var_ref2, 1, 1, 1, none_var_ref)
DEF(   set_var_ref3, 1, 1, 1, none_var_ref)

DEF(     get_length, 1, 1, 1, none)

DEF(      if_false8, 2, 1, 0, label8)
DEF(       if_true8, 2, 1, 0, label8) /* must come after if_false8 */
DEF(          goto8, 2, 0, 0, label8) /* must come after if_true8 */
DEF(         goto16, 3, 0, 0, label16)

DEF(          call0, 1, 1, 1, npopx)
DEF(          call1, 1, 1, 1, npopx)
DEF(          call2, 1, 1, 1, npopx)
DEF(          call3, 1, 1, 1, npopx)

DEF(   is_undefined, 1, 1, 1, none)
DEF(        is_null, 1, 1, 1, none)
DEF(typeof_is_undefined, 1, 1, 1, none)
DEF( typeof_is_function, 1, 1, 1, none)

#undef DEF
#undef def
#endif  /* DEF */
