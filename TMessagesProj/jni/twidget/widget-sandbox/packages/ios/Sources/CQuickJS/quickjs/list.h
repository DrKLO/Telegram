/*
 * Linux klist like system
 *
 * Copyright (c) 2016-2017 Fabrice Bellard
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
#ifndef LIST_H
#define LIST_H

#ifndef NULL
#include <stddef.h>
#endif

#ifdef __cplusplus
extern "C" {
#endif

struct list_head {
    struct list_head *prev;
    struct list_head *next;
};

#define LIST_HEAD_INIT(el) { &(el), &(el) }

/* return the pointer of type 'type *' containing 'el' as field 'member' */
#define list_entry(el, type, member) container_of(el, type, member)

static inline void init_list_head(struct list_head *head)
{
    head->prev = head;
    head->next = head;
}

/* insert 'el' between 'prev' and 'next' */
static inline void __list_add(struct list_head *el,
                              struct list_head *prev, struct list_head *next)
{
    prev->next = el;
    el->prev = prev;
    el->next = next;
    next->prev = el;
}

/* add 'el' at the head of the list 'head' (= after element head) */
static inline void list_add(struct list_head *el, struct list_head *head)
{
    __list_add(el, head, head->next);
}

/* add 'el' at the end of the list 'head' (= before element head) */
static inline void list_add_tail(struct list_head *el, struct list_head *head)
{
    __list_add(el, head->prev, head);
}

static inline void list_del(struct list_head *el)
{
    struct list_head *prev, *next;
    prev = el->prev;
    next = el->next;
    prev->next = next;
    next->prev = prev;
    el->prev = NULL; /* fail safe */
    el->next = NULL; /* fail safe */
}

static inline int list_empty(struct list_head *el)
{
    return el->next == el;
}

#define list_for_each(el, head) \
  for(el = (head)->next; el != (head); el = el->next)

#define list_for_each_safe(el, el1, head)                \
    for(el = (head)->next, el1 = el->next; el != (head); \
        el = el1, el1 = el->next)

#define list_for_each_prev(el, head) \
  for(el = (head)->prev; el != (head); el = el->prev)

#define list_for_each_prev_safe(el, el1, head)           \
    for(el = (head)->prev, el1 = el->prev; el != (head); \
        el = el1, el1 = el->prev)

#ifdef __cplusplus
} /* extern "C" { */
#endif

#endif /* LIST_H */
