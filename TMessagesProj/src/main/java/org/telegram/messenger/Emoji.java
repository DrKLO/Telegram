/*
 * This is the source code of Telegram for Android v. 1.3.2.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013.
 */

package org.telegram.messenger;

import java.io.InputStream;
import java.util.HashMap;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.text.Spannable;
import android.text.style.DynamicDrawableSpan;
import android.text.style.ImageSpan;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.telegram.ui.ApplicationLoader;

public class Emoji {
	private static final int[] ROW_SIZES = {27, 29, 33, 34, 34};
	private static HashMap<Long, DrawableInfo> rects = new HashMap<Long, DrawableInfo>();
	private static int imgSize, drawImgSize, bigImgSize;
	private static boolean inited = false;
	private static Paint placeholderPaint;
	private static Bitmap[] bmps = new Bitmap[5];
	private static boolean[] loading = new boolean[5];

    private static final char[] emojiChars={
            0x00A9,
            0x00AE,
            0x203C,
            0x2049,
            0x2122,
            0x2139,
            0x2194,
            0x2195,
            0x2196,
            0x2197,
            0x2198,
            0x2199,
            0x21A9,
            0x21AA,
            0x231A,
            0x231B,
            0x23E9,
            0x23EA,
            0x23EB,
            0x23EC,
            0x23F0,
            0x23F3,
            0x24C2,
            0x25AA,
            0x25AB,
            0x25B6,
            0x25C0,
            0x25FB,
            0x25FC,
            0x25FD,
            0x25FE,
            0x2600,
            0x2601,
            0x260E,
            0x2611,
            0x2614,
            0x2615,
            0x261D,
            0x263A,
            0x2648,
            0x2649,
            0x264A,
            0x264B,
            0x264C,
            0x264D,
            0x264E,
            0x264F,
            0x2650,
            0x2651,
            0x2652,
            0x2653,
            0x2660,
            0x2663,
            0x2665,
            0x2666,
            0x2668,
            0x267B,
            0x267F,
            0x2693,
            0x26A0,
            0x26A1,
            0x26AA,
            0x26AB,
            0x26BD,
            0x26BE,
            0x26C4,
            0x26C5,
            0x26CE,
            0x26D4,
            0x26EA,
            0x26F2,
            0x26F3,
            0x26F5,
            0x26FA,
            0x26FD,
            0x2702,
            0x2705,
            0x2708,
            0x2709,
            0x270A,
            0x270B,
            0x270C,
            0x270F,
            0x2712,
            0x2714,
            0x2716,
            0x2728,
            0x2733,
            0x2734,
            0x2744,
            0x2747,
            0x274C,
            0x274E,
            0x2753,
            0x2754,
            0x2755,
            0x2757,
            0x2764,
            0x2795,
            0x2796,
            0x2797,
            0x27A1,
            0x27B0,
            0x27BF,
            0x2934,
            0x2935,
            0x2B05,
            0x2B06,
            0x2B07,
            0x2B1B,
            0x2B1C,
            0x2B50,
            0x2B55,
            0x3030,
            0x303D,
            0x3297,
            0x3299
    };

    public static  long[][] data = {
            new long[]
                    {},
            new long[]
                    {0x00000000D83DDE04L, 0x00000000D83DDE03L, 0x00000000D83DDE00L, 0x00000000D83DDE0AL, 0x000000000000263AL, 0x00000000D83DDE09L, 0x00000000D83DDE0DL,
                    0x00000000D83DDE18L, 0x00000000D83DDE1AL, 0x00000000D83DDE17L, 0x00000000D83DDE19L, 0x00000000D83DDE1CL, 0x00000000D83DDE1DL, 0x00000000D83DDE1BL,
                    0x00000000D83DDE33L, 0x00000000D83DDE01L, 0x00000000D83DDE14L, 0x00000000D83DDE0CL, 0x00000000D83DDE12L, 0x00000000D83DDE1EL, 0x00000000D83DDE23L,
                    0x00000000D83DDE22L, 0x00000000D83DDE02L, 0x00000000D83DDE2DL, 0x00000000D83DDE2AL, 0x00000000D83DDE25L, 0x00000000D83DDE30L, 0x00000000D83DDE05L,
                    0x00000000D83DDE13L, 0x00000000D83DDE29L, 0x00000000D83DDE2BL, 0x00000000D83DDE28L, 0x00000000D83DDE31L, 0x00000000D83DDE20L, 0x00000000D83DDE21L,
                    0x00000000D83DDE24L, 0x00000000D83DDE16L, 0x00000000D83DDE06L, 0x00000000D83DDE0BL, 0x00000000D83DDE37L, 0x00000000D83DDE0EL, 0x00000000D83DDE34L,
                    0x00000000D83DDE35L, 0x00000000D83DDE32L, 0x00000000D83DDE1FL, 0x00000000D83DDE26L, 0x00000000D83DDE27L, 0x00000000D83DDE08L, 0x00000000D83DDC7FL,
                    0x00000000D83DDE2EL, 0x00000000D83DDE2CL, 0x00000000D83DDE10L, 0x00000000D83DDE15L, 0x00000000D83DDE2FL, 0x00000000D83DDE36L, 0x00000000D83DDE07L,
                    0x00000000D83DDE0FL, 0x00000000D83DDE11L, 0x00000000D83DDC72L, 0x00000000D83DDC73L, 0x00000000D83DDC6EL, 0x00000000D83DDC77L, 0x00000000D83DDC82L,
                    0x00000000D83DDC76L, 0x00000000D83DDC66L, 0x00000000D83DDC67L, 0x00000000D83DDC68L, 0x00000000D83DDC69L, 0x00000000D83DDC74L, 0x00000000D83DDC75L,
                    0x00000000D83DDC71L, 0x00000000D83DDC7CL, 0x00000000D83DDC78L, 0x00000000D83DDE3AL, 0x00000000D83DDE38L, 0x00000000D83DDE3BL, 0x00000000D83DDE3DL,
                    0x00000000D83DDE3CL, 0x00000000D83DDE40L, 0x00000000D83DDE3FL, 0x00000000D83DDE39L, 0x00000000D83DDE3EL, 0x00000000D83DDC79L, 0x00000000D83DDC7AL,
                    0x00000000D83DDE48L, 0x00000000D83DDE49L, 0x00000000D83DDE4AL, 0x00000000D83DDC80L, 0x00000000D83DDC7DL, 0x00000000D83DDCA9L, 0x00000000D83DDD25L,
                    0x0000000000002728L, 0x00000000D83CDF1FL, 0x00000000D83DDCABL, 0x00000000D83DDCA5L, 0x00000000D83DDCA2L, 0x00000000D83DDCA6L, 0x00000000D83DDCA7L,
                    0x00000000D83DDCA4L, 0x00000000D83DDCA8L, 0x00000000D83DDC42L, 0x00000000D83DDC40L, 0x00000000D83DDC43L, 0x00000000D83DDC45L, 0x00000000D83DDC44L,
                    0x00000000D83DDC4DL, 0x00000000D83DDC4EL, 0x00000000D83DDC4CL, 0x00000000D83DDC4AL, 0x000000000000270AL, 0x000000000000270CL, 0x00000000D83DDC4BL,
                    0x000000000000270BL, 0x00000000D83DDC50L, 0x00000000D83DDC46L, 0x00000000D83DDC47L, 0x00000000D83DDC49L, 0x00000000D83DDC48L, 0x00000000D83DDE4CL,
                    0x00000000D83DDE4FL, 0x000000000000261DL, 0x00000000D83DDC4FL, 0x00000000D83DDCAAL, 0x00000000D83DDEB6L, 0x00000000D83CDFC3L, 0x00000000D83DDC83L,
                    0x00000000D83DDC6BL, 0x00000000D83DDC6AL, 0x00000000D83DDC6CL, 0x00000000D83DDC6DL, 0x00000000D83DDC8FL, 0x00000000D83DDC91L, 0x00000000D83DDC6FL,
                    0x00000000D83DDE46L, 0x00000000D83DDE45L, 0x00000000D83DDC81L, 0x00000000D83DDE4BL, 0x00000000D83DDC86L, 0x00000000D83DDC87L, 0x00000000D83DDC85L,
                    0x00000000D83DDC70L, 0x00000000D83DDE4EL, 0x00000000D83DDE4DL, 0x00000000D83DDE47L, 0x00000000D83CDFA9L, 0x00000000D83DDC51L, 0x00000000D83DDC52L,
                    0x00000000D83DDC5FL, 0x00000000D83DDC5EL, 0x00000000D83DDC61L, 0x00000000D83DDC60L, 0x00000000D83DDC62L, 0x00000000D83DDC55L, 0x00000000D83DDC54L,
                    0x00000000D83DDC5AL, 0x00000000D83DDC57L, 0x00000000D83CDFBDL, 0x00000000D83DDC56L, 0x00000000D83DDC58L, 0x00000000D83DDC59L, 0x00000000D83DDCBCL,
                    0x00000000D83DDC5CL, 0x00000000D83DDC5DL, 0x00000000D83DDC5BL, 0x00000000D83DDC53L, 0x00000000D83CDF80L, 0x00000000D83CDF02L, 0x00000000D83DDC84L,
                    0x00000000D83DDC9BL, 0x00000000D83DDC99L, 0x00000000D83DDC9CL, 0x00000000D83DDC9AL, 0x0000000000002764L, 0x00000000D83DDC94L, 0x00000000D83DDC97L,
                    0x00000000D83DDC93L, 0x00000000D83DDC95L, 0x00000000D83DDC96L, 0x00000000D83DDC9EL, 0x00000000D83DDC98L, 0x00000000D83DDC8CL, 0x00000000D83DDC8BL,
                    0x00000000D83DDC8DL, 0x00000000D83DDC8EL, 0x00000000D83DDC64L, 0x00000000D83DDC65L, 0x00000000D83DDCACL, 0x00000000D83DDC63L, 0x00000000D83DDCADL},
            new long[]
                    {0x00000000D83DDC36L, 0x00000000D83DDC3AL, 0x00000000D83DDC31L, 0x00000000D83DDC2DL, 0x00000000D83DDC39L, 0x00000000D83DDC30L, 0x00000000D83DDC38L, 0x00000000D83DDC2FL,
                    0x00000000D83DDC28L, 0x00000000D83DDC3BL, 0x00000000D83DDC37L, 0x00000000D83DDC3DL, 0x00000000D83DDC2EL, 0x00000000D83DDC17L, 0x00000000D83DDC35L,
                    0x00000000D83DDC12L, 0x00000000D83DDC34L, 0x00000000D83DDC11L, 0x00000000D83DDC18L, 0x00000000D83DDC3CL, 0x00000000D83DDC27L, 0x00000000D83DDC26L,
                    0x00000000D83DDC24L, 0x00000000D83DDC25L, 0x00000000D83DDC23L, 0x00000000D83DDC14L, 0x00000000D83DDC0DL, 0x00000000D83DDC22L, 0x00000000D83DDC1BL,
                    0x00000000D83DDC1DL, 0x00000000D83DDC1CL, 0x00000000D83DDC1EL, 0x00000000D83DDC0CL, 0x00000000D83DDC19L, 0x00000000D83DDC1AL, 0x00000000D83DDC20L,
                    0x00000000D83DDC1FL, 0x00000000D83DDC2CL, 0x00000000D83DDC33L, 0x00000000D83DDC0BL, 0x00000000D83DDC04L, 0x00000000D83DDC0FL, 0x00000000D83DDC00L,
                    0x00000000D83DDC03L, 0x00000000D83DDC05L, 0x00000000D83DDC07L, 0x00000000D83DDC09L, 0x00000000D83DDC0EL, 0x00000000D83DDC10L, 0x00000000D83DDC13L,
                    0x00000000D83DDC15L, 0x00000000D83DDC16L, 0x00000000D83DDC01L, 0x00000000D83DDC02L, 0x00000000D83DDC32L, 0x00000000D83DDC21L, 0x00000000D83DDC0AL,
                    0x00000000D83DDC2BL, 0x00000000D83DDC2AL, 0x00000000D83DDC06L, 0x00000000D83DDC08L, 0x00000000D83DDC29L, 0x00000000D83DDC3EL, 0x00000000D83DDC90L,
                    0x00000000D83CDF38L, 0x00000000D83CDF37L, 0x00000000D83CDF40L, 0x00000000D83CDF39L, 0x00000000D83CDF3BL, 0x00000000D83CDF3AL, 0x00000000D83CDF41L,
                    0x00000000D83CDF43L, 0x00000000D83CDF42L, 0x00000000D83CDF3FL, 0x00000000D83CDF3EL, 0x00000000D83CDF44L, 0x00000000D83CDF35L, 0x00000000D83CDF34L,
                    0x00000000D83CDF32L, 0x00000000D83CDF33L, 0x00000000D83CDF30L, 0x00000000D83CDF31L, 0x00000000D83CDF3CL, 0x00000000D83CDF10L, 0x00000000D83CDF1EL,
                    0x00000000D83CDF1DL, 0x00000000D83CDF1AL, 0x00000000D83CDF11L, 0x00000000D83CDF12L, 0x00000000D83CDF13L, 0x00000000D83CDF14L, 0x00000000D83CDF15L,
                    0x00000000D83CDF16L, 0x00000000D83CDF17L, 0x00000000D83CDF18L, 0x00000000D83CDF1CL, 0x00000000D83CDF1BL, 0x00000000D83CDF19L, 0x00000000D83CDF0DL,
                    0x00000000D83CDF0EL, 0x00000000D83CDF0FL, 0x00000000D83CDF0BL, 0x00000000D83CDF0CL, 0x00000000D83CDF20L, 0x0000000000002B50L, 0x0000000000002600L,
                    0x00000000000026C5L, 0x0000000000002601L, 0x00000000000026A1L, 0x0000000000002614L, 0x0000000000002744L, 0x00000000000026C4L, 0x00000000D83CDF00L,
                    0x00000000D83CDF01L, 0x00000000D83CDF08L, 0x00000000D83CDF0AL},
            new long[]
                    {0x00000000D83CDF8DL, 0x00000000D83DDC9DL, 0x00000000D83CDF8EL, 0x00000000D83CDF92L, 0x00000000D83CDF93L, 0x00000000D83CDF8FL, 0x00000000D83CDF86L, 0x00000000D83CDF87L,
                    0x00000000D83CDF90L, 0x00000000D83CDF91L, 0x00000000D83CDF83L, 0x00000000D83DDC7BL, 0x00000000D83CDF85L, 0x00000000D83CDF84L, 0x00000000D83CDF81L,
                    0x00000000D83CDF8BL, 0x00000000D83CDF89L, 0x00000000D83CDF8AL, 0x00000000D83CDF88L, 0x00000000D83CDF8CL, 0x00000000D83DDD2EL, 0x00000000D83CDFA5L,
                    0x00000000D83DDCF7L, 0x00000000D83DDCF9L, 0x00000000D83DDCFCL, 0x00000000D83DDCBFL, 0x00000000D83DDCC0L, 0x00000000D83DDCBDL, 0x00000000D83DDCBEL,
                    0x00000000D83DDCBBL, 0x00000000D83DDCF1L, 0x000000000000260EL, 0x00000000D83DDCDEL, 0x00000000D83DDCDFL, 0x00000000D83DDCE0L, 0x00000000D83DDCE1L,
                    0x00000000D83DDCFAL, 0x00000000D83DDCFBL, 0x00000000D83DDD0AL, 0x00000000D83DDD09L, 0x00000000D83DDD08L, 0x00000000D83DDD07L, 0x00000000D83DDD14L,
                    0x00000000D83DDD14L, 0x00000000D83DDCE2L, 0x00000000D83DDCE3L, 0x00000000000023F3L, 0x000000000000231BL, 0x00000000000023F0L, 0x000000000000231AL,
                    0x00000000D83DDD13L, 0x00000000D83DDD12L, 0x00000000D83DDD0FL, 0x00000000D83DDD10L, 0x00000000D83DDD11L, 0x00000000D83DDD0EL, 0x00000000D83DDCA1L,
                    0x00000000D83DDD26L, 0x00000000D83DDD06L, 0x00000000D83DDD05L, 0x00000000D83DDD0CL, 0x00000000D83DDD0BL, 0x00000000D83DDD0DL, 0x00000000D83DDEC0L,
                    0x00000000D83DDEBFL, 0x00000000D83DDEBDL, 0x00000000D83DDD27L, 0x00000000D83DDD29L, 0x00000000D83DDD28L, 0x00000000D83DDEAAL, 0x00000000D83DDEACL,
                    0x00000000D83DDCA3L, 0x00000000D83DDD2BL, 0x00000000D83DDD2AL, 0x00000000D83DDC8AL, 0x00000000D83DDC89L, 0x00000000D83DDCB0L, 0x00000000D83DDCB4L,
                    0x00000000D83DDCB5L, 0x00000000D83DDCB7L, 0x00000000D83DDCB6L, 0x00000000D83DDCB3L, 0x00000000D83DDCB8L, 0x00000000D83DDCF2L, 0x00000000D83DDCE7L,
                    0x00000000D83DDCE5L, 0x00000000D83DDCE4L, 0x0000000000002709L, 0x00000000D83DDCE9L, 0x00000000D83DDCE8L, 0x00000000D83DDCEFL, 0x00000000D83DDCEBL,
                    0x00000000D83DDCEAL, 0x00000000D83DDCECL, 0x00000000D83DDCEDL, 0x00000000D83DDCEEL, 0x00000000D83DDCE6L, 0x00000000D83DDCDDL, 0x00000000D83DDCC4L,
                    0x00000000D83DDCC3L, 0x00000000D83DDCD1L, 0x00000000D83DDCCAL, 0x00000000D83DDCC8L, 0x00000000D83DDCC9L, 0x00000000D83DDCDCL, 0x00000000D83DDCCBL,
                    0x00000000D83DDCC5L, 0x00000000D83DDCC6L, 0x00000000D83DDCC7L, 0x00000000D83DDCC1L, 0x00000000D83DDCC2L, 0x0000000000002702L, 0x00000000D83DDCCCL,
                    0x00000000D83DDCCEL, 0x0000000000002712L, 0x000000000000270FL, 0x00000000D83DDCCFL, 0x00000000D83DDCD0L, 0x00000000D83DDCD5L, 0x00000000D83DDCD7L,
                    0x00000000D83DDCD8L, 0x00000000D83DDCD9L, 0x00000000D83DDCD3L, 0x00000000D83DDCD4L, 0x00000000D83DDCD2L, 0x00000000D83DDCDAL, 0x00000000D83DDCD6L,
                    0x00000000D83DDD16L, 0x00000000D83DDCDBL, 0x00000000D83DDD2CL, 0x00000000D83DDD2DL, 0x00000000D83DDCF0L, 0x00000000D83CDFA8L, 0x00000000D83CDFACL,
                    0x00000000D83CDFA4L, 0x00000000D83CDFA7L, 0x00000000D83CDFBCL, 0x00000000D83CDFB5L, 0x00000000D83CDFB6L, 0x00000000D83CDFB9L, 0x00000000D83CDFBBL,
                    0x00000000D83CDFBAL, 0x00000000D83CDFB7L, 0x00000000D83CDFB8L, 0x00000000D83DDC7EL, 0x00000000D83CDFAEL, 0x00000000D83CDCCFL, 0x00000000D83CDFB4L,
                    0x00000000D83CDC04L, 0x00000000D83CDFB2L, 0x00000000D83CDFAFL, 0x00000000D83CDFC8L, 0x00000000D83CDFC0L, 0x00000000000026BDL, 0x00000000000026BEL,
                    0x00000000D83CDFBEL, 0x00000000D83CDFB1L, 0x00000000D83CDFC9L, 0x00000000D83CDFB3L, 0x00000000000026F3L, 0x00000000D83DDEB5L, 0x00000000D83DDEB4L,
                    0x00000000D83CDFC1L, 0x00000000D83CDFC7L, 0x00000000D83CDFC6L, 0x00000000D83CDFBFL, 0x00000000D83CDFC2L, 0x00000000D83CDFCAL, 0x00000000D83CDFC4L,
                    0x00000000D83CDFA3L, 0x0000000000002615L, 0x00000000D83CDF75L, 0x00000000D83CDF76L, 0x00000000D83CDF7CL, 0x00000000D83CDF7AL, 0x00000000D83CDF7BL,
                    0x00000000D83CDF78L, 0x00000000D83CDF79L, 0x00000000D83CDF77L, 0x00000000D83CDF74L, 0x00000000D83CDF55L, 0x00000000D83CDF54L, 0x00000000D83CDF5FL,
                    0x00000000D83CDF57L, 0x00000000D83CDF56L, 0x00000000D83CDF5DL, 0x00000000D83CDF5BL, 0x00000000D83CDF64L, 0x00000000D83CDF71L, 0x00000000D83CDF63L,
                    0x00000000D83CDF65L, 0x00000000D83CDF59L, 0x00000000D83CDF58L, 0x00000000D83CDF5AL, 0x00000000D83CDF5CL, 0x00000000D83CDF72L, 0x00000000D83CDF62L,
                    0x00000000D83CDF61L, 0x00000000D83CDF73L, 0x00000000D83CDF5EL, 0x00000000D83CDF69L, 0x00000000D83CDF6EL, 0x00000000D83CDF66L, 0x00000000D83CDF68L,
                    0x00000000D83CDF67L, 0x00000000D83CDF82L, 0x00000000D83CDF70L, 0x00000000D83CDF6AL, 0x00000000D83CDF6BL, 0x00000000D83CDF6CL, 0x00000000D83CDF6DL,
                    0x00000000D83CDF6FL, 0x00000000D83CDF4EL, 0x00000000D83CDF4FL, 0x00000000D83CDF4AL, 0x00000000D83CDF4BL, 0x00000000D83CDF52L, 0x00000000D83CDF47L,
                    0x00000000D83CDF49L, 0x00000000D83CDF53L, 0x00000000D83CDF51L, 0x00000000D83CDF48L, 0x00000000D83CDF4CL, 0x00000000D83CDF50L, 0x00000000D83CDF4DL,
                    0x00000000D83CDF60L, 0x00000000D83CDF46L, 0x00000000D83CDF45L, 0x00000000D83CDF3DL},
            new long[]
                    {0x00000000D83CDFE0L, 0x00000000D83CDFE1L, 0x00000000D83CDFEBL, 0x00000000D83CDFE2L, 0x00000000D83CDFE3L, 0x00000000D83CDFE5L, 0x00000000D83CDFE6L, 0x00000000D83CDFEAL,
                    0x00000000D83CDFE9L, 0x00000000D83CDFE8L, 0x00000000D83DDC92L, 0x00000000000026EAL, 0x00000000D83CDFECL, 0x00000000D83CDFE4L, 0x00000000D83CDF07L,
                    0x00000000D83CDF06L, 0x00000000D83CDFEFL, 0x00000000D83CDFF0L, 0x00000000000026FAL, 0x00000000D83CDFEDL, 0x00000000D83DDDFCL, 0x00000000D83DDDFEL,
                    0x00000000D83DDDFBL, 0x00000000D83CDF04L, 0x00000000D83CDF05L, 0x00000000D83CDF03L, 0x00000000D83DDDFDL, 0x00000000D83CDF09L, 0x00000000D83CDFA0L,
                    0x00000000D83CDFA1L, 0x00000000000026F2L, 0x00000000D83CDFA2L, 0x00000000D83DDEA2L, 0x00000000000026F5L, 0x00000000D83DDEA4L, 0x00000000D83DDEA3L,
                    0x0000000000002693L, 0x00000000D83DDE80L, 0x0000000000002708L, 0x00000000D83DDCBAL, 0x00000000D83DDE81L, 0x00000000D83DDE82L, 0x00000000D83DDE8AL,
                    0x00000000D83DDE89L, 0x00000000D83DDE9EL, 0x00000000D83DDE86L, 0x00000000D83DDE84L, 0x00000000D83DDE85L, 0x00000000D83DDE88L, 0x00000000D83DDE87L,
                    0x00000000D83DDE9DL, 0x00000000D83DDE8BL, 0x00000000D83DDE83L, 0x00000000D83DDE8EL, 0x00000000D83DDE8CL, 0x00000000D83DDE8DL, 0x00000000D83DDE99L,
                    0x00000000D83DDE98L, 0x00000000D83DDE97L, 0x00000000D83DDE95L, 0x00000000D83DDE96L, 0x00000000D83DDE9BL, 0x00000000D83DDE9AL, 0x00000000D83DDEA8L,
                    0x00000000D83DDE93L, 0x00000000D83DDE94L, 0x00000000D83DDE92L, 0x00000000D83DDE91L, 0x00000000D83DDE90L, 0x00000000D83DDEB2L, 0x00000000D83DDEA1L,
                    0x00000000D83DDE9FL, 0x00000000D83DDEA0L, 0x00000000D83DDE9CL, 0x00000000D83DDC88L, 0x00000000D83DDE8FL, 0x00000000D83CDFABL, 0x00000000D83DDEA6L,
                    0x00000000D83DDEA5L, 0x00000000000026A0L, 0x00000000D83DDEA7L, 0x00000000D83DDD30L, 0x00000000000026FDL, 0x00000000D83CDFEEL, 0x00000000D83CDFB0L,
                    0x0000000000002668L, 0x00000000D83DDDFFL, 0x00000000D83CDFAAL, 0x00000000D83CDFADL, 0x00000000D83DDCCDL, 0x00000000D83DDEA9L, 0xD83CDDEFD83CDDF5L,
                    0xD83CDDF0D83CDDF7L, 0xD83CDDE9D83CDDEAL, 0xD83CDDE8D83CDDF3L, 0xD83CDDFAD83CDDF8L, 0xD83CDDEBD83CDDF7L, 0xD83CDDEAD83CDDF8L, 0xD83CDDEED83CDDF9L,
                    0xD83CDDF7D83CDDFAL, 0xD83CDDECD83CDDE7L},
            new long[]
                    {0x00000000003120E3L, 0x00000000003220E3L, 0x00000000003320E3L, 0x00000000003420E3L, 0x00000000003520E3L, 0x00000000003620E3L, 0x00000000003720E3L, 0x00000000003820E3L,
                    0x00000000003920E3L, 0x00000000003020E3L, 0x00000000D83DDD1FL, 0x00000000D83DDD22L, 0x00000000002320E3L, 0x00000000D83DDD23L, 0x0000000000002B06L,
                    0x0000000000002B07L, 0x0000000000002B05L, 0x00000000000027A1L, 0x00000000D83DDD20L, 0x00000000D83DDD21L, 0x00000000D83DDD24L, 0x0000000000002197L,
                    0x0000000000002196L, 0x0000000000002198L, 0x0000000000002199L, 0x0000000000002194L, 0x0000000000002195L, 0x00000000D83DDD04L, 0x00000000000025C0L,
                    0x00000000000025B6L, 0x00000000D83DDD3CL, 0x00000000D83DDD3DL, 0x00000000000021A9L, 0x00000000000021AAL, 0x0000000000002139L, 0x00000000000023EAL,
                    0x00000000000023E9L, 0x00000000000023EBL, 0x00000000000023ECL, 0x0000000000002935L, 0x0000000000002934L, 0x00000000D83CDD97L, 0x00000000D83DDD00L,
                    0x00000000D83DDD01L, 0x00000000D83DDD02L, 0x00000000D83CDD95L, 0x00000000D83CDD99L, 0x00000000D83CDD92L, 0x00000000D83CDD93L, 0x00000000D83CDD96L,
                    0x00000000D83DDCF6L, 0x00000000D83CDFA6L, 0x00000000D83CDE01L, 0x00000000D83CDE2FL, 0x00000000D83CDE33L, 0x00000000D83CDE35L, 0x00000000D83CDE32L,
                    0x00000000D83CDE34L, 0x00000000D83CDE32L, 0x00000000D83CDE50L, 0x00000000D83CDE39L, 0x00000000D83CDE3AL, 0x00000000D83CDE36L, 0x00000000D83CDE1AL,
                    0x00000000D83DDEBBL, 0x00000000D83DDEB9L, 0x00000000D83DDEBAL, 0x00000000D83DDEBCL, 0x00000000D83DDEBEL, 0x00000000D83DDEB0L, 0x00000000D83DDEAEL,
                    0x00000000D83CDD7FL, 0x000000000000267FL, 0x00000000D83DDEADL, 0x00000000D83CDE37L, 0x00000000D83CDE38L, 0x00000000D83CDE02L, 0x00000000000024C2L,
                    0x00000000D83CDE51L, 0x0000000000003299L, 0x0000000000003297L, 0x00000000D83CDD91L, 0x00000000D83CDD98L, 0x00000000D83CDD94L, 0x00000000D83DDEABL,
                    0x00000000D83DDD1EL, 0x00000000D83DDCF5L, 0x00000000D83DDEAFL, 0x00000000D83DDEB1L, 0x00000000D83DDEB3L, 0x00000000D83DDEB7L, 0x00000000D83DDEB8L,
                    0x00000000000026D4L, 0x0000000000002733L, 0x0000000000002747L, 0x000000000000274EL, 0x0000000000002705L, 0x0000000000002734L, 0x00000000D83DDC9FL,
                    0x00000000D83CDD9AL, 0x00000000D83DDCF3L, 0x00000000D83DDCF4L, 0x00000000D83CDD70L, 0x00000000D83CDD71L, 0x00000000D83CDD8EL, 0x00000000D83CDD7EL,
                    0x00000000D83DDCA0L, 0x00000000000027BFL, 0x000000000000267BL, 0x0000000000002648L, 0x0000000000002649L, 0x000000000000264AL, 0x000000000000264BL,
                    0x000000000000264CL, 0x000000000000264DL, 0x000000000000264EL, 0x000000000000264FL, 0x0000000000002650L, 0x0000000000002651L, 0x0000000000002652L,
                    0x0000000000002653L, 0x00000000000026CEL, 0x00000000D83DDD2FL, 0x00000000D83CDFE7L, 0x00000000D83DDCB9L, 0x00000000D83DDCB2L, 0x00000000D83DDCB1L,
                    0x00000000000000A9L, 0x00000000000000AEL, 0x0000000000002122L, 0x000000000000303DL, 0x0000000000003030L, 0x00000000D83DDD1DL, 0x00000000D83DDD1AL,
                    0x00000000D83DDD19L, 0x00000000D83DDD1BL, 0x00000000D83DDD1CL, 0x000000000000274CL, 0x0000000000002B55L, 0x0000000000002757L, 0x0000000000002753L,
                    0x0000000000002755L, 0x0000000000002754L, 0x00000000D83DDD03L, 0x00000000D83DDD5BL, 0x00000000D83DDD67L, 0x00000000D83DDD50L, 0x00000000D83DDD5CL,
                    0x00000000D83DDD51L, 0x00000000D83DDD5DL, 0x00000000D83DDD52L, 0x00000000D83DDD5EL, 0x00000000D83DDD53L, 0x00000000D83DDD5FL, 0x00000000D83DDD54L,
                    0x00000000D83DDD60L, 0x00000000D83DDD55L, 0x00000000D83DDD56L, 0x00000000D83DDD57L, 0x00000000D83DDD58L, 0x00000000D83DDD59L, 0x00000000D83DDD5AL,
                    0x00000000D83DDD61L, 0x00000000D83DDD62L, 0x00000000D83DDD63L, 0x00000000D83DDD64L, 0x00000000D83DDD65L, 0x00000000D83DDD66L, 0x0000000000002716L,
                    0x0000000000002795L, 0x0000000000002796L, 0x0000000000002797L, 0x0000000000002660L, 0x0000000000002665L, 0x0000000000002663L, 0x0000000000002666L,
                    0x00000000D83DDCAEL, 0x00000000D83DDCAFL, 0x0000000000002714L, 0x0000000000002611L, 0x00000000D83DDD18L, 0x00000000D83DDD17L, 0x00000000000027B0L,
                    0x00000000D83DDD31L, 0x00000000D83DDD32L, 0x00000000D83DDD33L, 0x00000000000025FCL, 0x00000000000025FBL, 0x00000000000025FEL, 0x00000000000025FDL,
                    0x00000000000025AAL, 0x00000000000025ABL, 0x00000000D83DDD3AL, 0x0000000000002B1CL, 0x0000000000002B1BL, 0x00000000000026ABL, 0x00000000000026AAL,
                    0x00000000D83DDD34L, 0x00000000D83DDD35L, 0x00000000D83DDD3BL, 0x00000000D83DDD36L, 0x00000000D83DDD37L, 0x00000000D83DDD38L, 0x00000000D83DDD39L}};
	
	static {
		imgSize = Math.min(scale(30), Utilities.density < 1.5f ? 28 : 56);
		drawImgSize = scale(20);
		bigImgSize = scale(30);
		if(Math.abs(imgSize - bigImgSize) < 5) {
            bigImgSize = imgSize;
        }

		for (int j = 1; j < data.length; j++) {
			int rsize = ROW_SIZES[j - 1];
			for(int i = 0; i < data[j].length; i++) {
				Rect rect = new Rect((i % rsize) * imgSize, (i / rsize) * imgSize, (i % rsize + 1) * imgSize, (i / rsize + 1) * imgSize);
				rects.put(data[j][i], new DrawableInfo(rect, j - 1));
			}
		}
		placeholderPaint = new Paint();
		placeholderPaint.setColor(0x55000000);
	}

    public static int scale(float value) {
        return (int)(ApplicationLoader.applicationContext.getResources().getDisplayMetrics().density * value);
    }

	private static Bitmap loadPage(final int page){
		try {
			int rsize = ROW_SIZES[page];
			
			BitmapFactory.Options opts = new BitmapFactory.Options();
			opts.inPreferredConfig = Bitmap.Config.ARGB_8888;

			opts.inDither = false;
			if (Utilities.density < 1.5f) {
				opts.inSampleSize = 2;
            }

			int iw, ih;

            InputStream is = ApplicationLoader.applicationContext.getAssets().open("emojisprite_" + page + ".png");
            Bitmap color = BitmapFactory.decodeStream(is, null, opts);
            is.close();

            iw = color.getWidth();
            ih = color.getHeight();

			ih = (int)Math.round(((double)imgSize * rsize) * ((double)ih / iw));
			iw = imgSize * rsize;

			if(iw < color.getWidth() && ih < color.getWidth()) {
				color = Bitmap.createScaledBitmap(color, iw, ih, true);
            }

			bmps[page] = color;

            final Bitmap img = color;
            Utilities.RunOnUIThread(new Runnable() {
                @Override
                public void run() {
                    /*for (int a = 0; a < drawables.size(); a++) {
                        WeakReference<EmojiDrawable> it = drawables.get(a);
                        if (it.get() == null) {
                            drawables.remove(a);
                            a--;
                        } else {
                            EmojiDrawable drawable = it.get();
                            if (drawable.page == page) {
                                drawable.bmp = img;
                            }
                            drawable.invalidateSelf();
                        }
                    }*/
                    NotificationCenter.getInstance().postNotificationName(999);
                }
            });

			return color;
		} catch(Throwable x) {
            FileLog.e("tmessages", "Error loading emoji", x);
        }
		return null;
	}
	
	private static void loadPageAsync(final int page) {
		if(loading[page]) {
            return;
        }
		loading[page] = true;
		new Thread(new Runnable() {
            public void run() {
                loadPage(page);
                loading[page] = false;
            }
        }).start();
	}
	
	public static void invalidateAll(View view){
		if(view instanceof ViewGroup) {
			ViewGroup g = (ViewGroup)view;
			for(int i = 0; i < g.getChildCount(); i++){
				invalidateAll(g.getChildAt(i));
			}
		} else if(view instanceof TextView) {
			view.invalidate();
		}
	}
	
	public static Drawable getEmojiDrawable(long code){
		DrawableInfo info = rects.get(code);
		if(info == null){
            FileLog.e("tmessages", "No emoji drawable for code " + String.format("%016X", code));
			return null;
		}
		EmojiDrawable ed = new EmojiDrawable(info);
		ed.setBounds(0, 0, drawImgSize, drawImgSize);
		if(bmps[info.page] == null) {
            loadPageAsync(info.page);
        }
		return ed;
	}
	
	public static Drawable getEmojiBigDrawable(long code) {
		EmojiDrawable ed = (EmojiDrawable)getEmojiDrawable(code);
		if (ed == null) {
            return null;
        }
		ed.setBounds(0, 0, bigImgSize, bigImgSize);
		ed.fullSize = true;
		return ed;
	}
	
	public static class EmojiDrawable extends Drawable {
		Rect rect;
		int page;
		boolean fullSize = false;
		private static Paint paint;
		Bitmap bmp;
		
		static {
			paint = new Paint();
			paint.setFilterBitmap(true);
		}
		
		public EmojiDrawable(DrawableInfo info){
			rect = info.rect;
			page = info.page;
		}
		
		@Override
		public void draw(Canvas canvas) {
			if(bmps[page] == null){
				canvas.drawRect(getBounds(), placeholderPaint);
				return;
			}
			if(bmp == null) {
				bmp = bmps[page];
            }
			Rect b = copyBounds();
			int cX = b.centerX(), cY = b.centerY();
			b.left = cX - (fullSize ? bigImgSize : drawImgSize) / 2;
			b.right = cX + (fullSize ? bigImgSize : drawImgSize) / 2;
			b.top = cY - (fullSize ? bigImgSize : drawImgSize) / 2;
			b.bottom = cY + (fullSize ? bigImgSize : drawImgSize) / 2;
			canvas.drawBitmap(bmp, rect, b, paint);
		}

		@Override
		public int getOpacity() {
			return 0;
		}

		@Override
		public void setAlpha(int alpha) {

        }

		@Override
		public void setColorFilter(ColorFilter cf) {

        }
	}
	
	private static class DrawableInfo {
		Rect rect;
		int page;
		public DrawableInfo(Rect rect, int p) {
			this.rect = rect;
			page = p;
		}
	}

    private static boolean inArray(char c, char[] a) {
        for(char cc : a) {
            if(cc == c) {
                return true;
            }
        }
        return false;
    }

    public static CharSequence replaceEmoji(CharSequence cs) {
        if (cs == null || cs.length() == 0) {
            return cs;
        }
        Spannable s;
        if (cs instanceof Spannable){
            s = (Spannable)cs;
        } else {
            s = Spannable.Factory.getInstance().newSpannable(cs);
        }
        long buf = 0;
        int emojiCount = 0;
        for (int i = 0; i < cs.length(); i++) {
            char c = cs.charAt(i);
            if (c == 0xD83C || c == 0xD83D || (buf != 0 && (buf & 0xFFFFFFFF00000000L) == 0 && (c >= 0xDDE6 && c <= 0xDDFA))) {
                buf <<= 16;
                buf |= c;
            } else if (buf > 0 && (c & 0xF000) == 0xD000) {
                buf <<= 16;
                buf |= c;
                Drawable d = Emoji.getEmojiDrawable(buf);
                if (d != null){
                    EmojiSpan span = new EmojiSpan(d, DynamicDrawableSpan.ALIGN_BOTTOM);
                    emojiCount++;
                    if (c>= 0xDDE6 && c <= 0xDDFA) {
                        s.setSpan(span, i - 3, i + 1, 0);
                    } else {
                        s.setSpan(span, i - 1, i + 1, 0);
                    }
                }
                buf = 0;
            } else if (c == 0x20E3) {
                if (i > 0) {
                    char c2 = cs.charAt(i - 1);
                    if((c2 >= '0' && c2 <= '9') || c2 == '#') {
                        buf = c2;
                        buf <<= 16;
                        buf |= c;
                        Drawable d = Emoji.getEmojiDrawable(buf);
                        if(d != null) {
                            EmojiSpan span = new EmojiSpan(d, DynamicDrawableSpan.ALIGN_BOTTOM);
                            emojiCount++;
                            s.setSpan(span, i - 1, i + 1, 0);
                        }
                        buf = 0;
                    }
                }
            } else if(inArray(c, emojiChars)){
                Drawable d = Emoji.getEmojiDrawable(c);
                if(d != null){
                    EmojiSpan span = new EmojiSpan(d, DynamicDrawableSpan.ALIGN_BOTTOM);
                    emojiCount++;
                    s.setSpan(span, i, i + 1, 0);
                }
            }
            if (emojiCount >= 50) {
                break;
            }
        }
        return s;
    }

    public static class EmojiSpan extends ImageSpan {
        public EmojiSpan(Drawable d, int verticalAlignment) {
            super(d, verticalAlignment);
        }

        @Override
        public int getSize(Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt fm) {
            if (fm == null) {
                fm = new Paint.FontMetricsInt();
            }

            int sz = super.getSize(paint, text, start, end, fm);

            int offset = Utilities.dp(8);
            int w = Utilities.dp(10);
            fm.top = -w - offset;
            fm.bottom = w - offset;
            fm.ascent = -w - offset;
            fm.leading = 0;
            fm.descent = w - offset;

            return sz;
        }
    }

    public static class XImageSpan extends ImageSpan {
        public int uid;

        public XImageSpan(Drawable d, int verticalAlignment) {
            super(d, verticalAlignment);
        }

        @Override
        public int getSize(Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt fm) {
            if (fm == null) {
                fm = new Paint.FontMetricsInt();
            }

            int sz = super.getSize(paint, text, start, end, fm);

            int offset = Utilities.dp(6);
            int w = (fm.bottom - fm.top) / 2;
            fm.top = -w - offset;
            fm.bottom = w - offset;
            fm.ascent = -w - offset;
            fm.leading = 0;
            fm.descent = w - offset;

            return sz;
        }
    }

    //        @Override
//        public int getSize(Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt fm) {
//            if (fm == null) {
//                fm = new Paint.FontMetricsInt();
//            }
//            paint.getFontMetricsInt(fm);
//
//            int sz = super.getSize(paint, text, start, end, fm);
//            if(fm != null) {
//                fm.ascent = (int)paint.ascent();
//                fm.descent = (int)paint.descent();
//            }
//            return sz;
//        }
}
