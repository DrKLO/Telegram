/*
 * Regular Expression Engine
 *
 * Copyright (c) 2017-2018 Fabrice Bellard
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

#ifdef DEF

DEF(invalid, 1) /* never used */
DEF(char, 3)
DEF(char_i, 3)
DEF(char32, 5)
DEF(char32_i, 5)
DEF(dot, 1)
DEF(any, 1) /* same as dot but match any character including line terminator */
DEF(space, 1)
DEF(not_space, 1) /* must come after */
DEF(line_start, 1)
DEF(line_start_m, 1)
DEF(line_end, 1)
DEF(line_end_m, 1)
DEF(goto, 5)
DEF(split_goto_first, 5)
DEF(split_next_first, 5)
DEF(match, 1)
DEF(lookahead_match, 1)
DEF(negative_lookahead_match, 1) /* must come after */
DEF(save_start, 2) /* save start position */
DEF(save_end, 2) /* save end position, must come after saved_start */
DEF(save_reset, 3) /* reset save positions */
DEF(loop, 6) /* decrement the top the stack and goto if != 0 */
DEF(loop_split_goto_first, 10) /* loop and then split */
DEF(loop_split_next_first, 10)
DEF(loop_check_adv_split_goto_first, 10) /* loop and then check advance and split */
DEF(loop_check_adv_split_next_first, 10)
DEF(set_i32, 6) /* store the immediate value to a register */
DEF(word_boundary, 1)
DEF(word_boundary_i, 1)
DEF(not_word_boundary, 1)
DEF(not_word_boundary_i, 1)
DEF(back_reference, 2) /* variable length */
DEF(back_reference_i, 2) /* must come after */
DEF(backward_back_reference, 2) /* must come after */
DEF(backward_back_reference_i, 2) /* must come after */
DEF(range, 3) /* variable length */
DEF(range_i, 3) /* variable length */
DEF(range32, 3) /* variable length */
DEF(range32_i, 3) /* variable length */
DEF(lookahead, 5)
DEF(negative_lookahead, 5) /* must come after */
DEF(set_char_pos, 2) /* store the character position to a register */
DEF(check_advance, 2) /* check that the register is different from the character position */
DEF(prev, 1) /* go to the previous char */

#endif /* DEF */
