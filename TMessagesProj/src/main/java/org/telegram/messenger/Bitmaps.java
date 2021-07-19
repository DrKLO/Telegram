/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.messenger;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Build;

public class Bitmaps {

    private static volatile Matrix sScaleMatrix;

    private static final ThreadLocal<byte[]> jpegData = new ThreadLocal<byte[]>() {
        @Override
        protected byte[] initialValue() {
            return new byte[]{
                    (byte) 0xff, (byte) 0xd8, (byte) 0xff, (byte) 0xdb, (byte) 0x00, (byte) 0x43, (byte) 0x00,
                    (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff,
                    (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff,
                    (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff,
                    (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff,
                    (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff,
                    (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff,
                    (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff,
                    (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff,
                    (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff,
                    (byte) 0xff, (byte) 0xff, (byte) 0xc0, (byte) 0x00, (byte) 0x11, (byte) 0x08, (byte) 0x00,
                    (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x03, (byte) 0x01, (byte) 0x22, (byte) 0x00,
                    (byte) 0x02, (byte) 0x11, (byte) 0x00, (byte) 0x03, (byte) 0x11, (byte) 0x00, (byte) 0xff,
                    (byte) 0xc4, (byte) 0x00, (byte) 0x1f, (byte) 0x00, (byte) 0x00, (byte) 0x01, (byte) 0x05,
                    (byte) 0x01, (byte) 0x01, (byte) 0x01, (byte) 0x01, (byte) 0x01, (byte) 0x01, (byte) 0x00,
                    (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
                    (byte) 0x01, (byte) 0x02, (byte) 0x03, (byte) 0x04, (byte) 0x05, (byte) 0x06, (byte) 0x07,
                    (byte) 0x08, (byte) 0x09, (byte) 0x0a, (byte) 0x0b, (byte) 0xff, (byte) 0xc4, (byte) 0x00,
                    (byte) 0xb5, (byte) 0x10, (byte) 0x00, (byte) 0x02, (byte) 0x01, (byte) 0x03, (byte) 0x03,
                    (byte) 0x02, (byte) 0x04, (byte) 0x03, (byte) 0x05, (byte) 0x05, (byte) 0x04, (byte) 0x04,
                    (byte) 0x00, (byte) 0x00, (byte) 0x01, (byte) 0x7d, (byte) 0x01, (byte) 0x02, (byte) 0x03,
                    (byte) 0x00, (byte) 0x04, (byte) 0x11, (byte) 0x05, (byte) 0x12, (byte) 0x21, (byte) 0x31,
                    (byte) 0x41, (byte) 0x06, (byte) 0x13, (byte) 0x51, (byte) 0x61, (byte) 0x07, (byte) 0x22,
                    (byte) 0x71, (byte) 0x14, (byte) 0x32, (byte) 0x81, (byte) 0x91, (byte) 0xa1, (byte) 0x08,
                    (byte) 0x23, (byte) 0x42, (byte) 0xb1, (byte) 0xc1, (byte) 0x15, (byte) 0x52, (byte) 0xd1,
                    (byte) 0xf0, (byte) 0x24, (byte) 0x33, (byte) 0x62, (byte) 0x72, (byte) 0x82, (byte) 0x09,
                    (byte) 0x0a, (byte) 0x16, (byte) 0x17, (byte) 0x18, (byte) 0x19, (byte) 0x1a, (byte) 0x25,
                    (byte) 0x26, (byte) 0x27, (byte) 0x28, (byte) 0x29, (byte) 0x2a, (byte) 0x34, (byte) 0x35,
                    (byte) 0x36, (byte) 0x37, (byte) 0x38, (byte) 0x39, (byte) 0x3a, (byte) 0x43, (byte) 0x44,
                    (byte) 0x45, (byte) 0x46, (byte) 0x47, (byte) 0x48, (byte) 0x49, (byte) 0x4a, (byte) 0x53,
                    (byte) 0x54, (byte) 0x55, (byte) 0x56, (byte) 0x57, (byte) 0x58, (byte) 0x59, (byte) 0x5a,
                    (byte) 0x63, (byte) 0x64, (byte) 0x65, (byte) 0x66, (byte) 0x67, (byte) 0x68, (byte) 0x69,
                    (byte) 0x6a, (byte) 0x73, (byte) 0x74, (byte) 0x75, (byte) 0x76, (byte) 0x77, (byte) 0x78,
                    (byte) 0x79, (byte) 0x7a, (byte) 0x83, (byte) 0x84, (byte) 0x85, (byte) 0x86, (byte) 0x87,
                    (byte) 0x88, (byte) 0x89, (byte) 0x8a, (byte) 0x92, (byte) 0x93, (byte) 0x94, (byte) 0x95,
                    (byte) 0x96, (byte) 0x97, (byte) 0x98, (byte) 0x99, (byte) 0x9a, (byte) 0xa2, (byte) 0xa3,
                    (byte) 0xa4, (byte) 0xa5, (byte) 0xa6, (byte) 0xa7, (byte) 0xa8, (byte) 0xa9, (byte) 0xaa,
                    (byte) 0xb2, (byte) 0xb3, (byte) 0xb4, (byte) 0xb5, (byte) 0xb6, (byte) 0xb7, (byte) 0xb8,
                    (byte) 0xb9, (byte) 0xba, (byte) 0xc2, (byte) 0xc3, (byte) 0xc4, (byte) 0xc5, (byte) 0xc6,
                    (byte) 0xc7, (byte) 0xc8, (byte) 0xc9, (byte) 0xca, (byte) 0xd2, (byte) 0xd3, (byte) 0xd4,
                    (byte) 0xd5, (byte) 0xd6, (byte) 0xd7, (byte) 0xd8, (byte) 0xd9, (byte) 0xda, (byte) 0xe1,
                    (byte) 0xe2, (byte) 0xe3, (byte) 0xe4, (byte) 0xe5, (byte) 0xe6, (byte) 0xe7, (byte) 0xe8,
                    (byte) 0xe9, (byte) 0xea, (byte) 0xf1, (byte) 0xf2, (byte) 0xf3, (byte) 0xf4, (byte) 0xf5,
                    (byte) 0xf6, (byte) 0xf7, (byte) 0xf8, (byte) 0xf9, (byte) 0xfa, (byte) 0xff, (byte) 0xc4,
                    (byte) 0x00, (byte) 0x1f, (byte) 0x01, (byte) 0x00, (byte) 0x03, (byte) 0x01, (byte) 0x01,
                    (byte) 0x01, (byte) 0x01, (byte) 0x01, (byte) 0x01, (byte) 0x01, (byte) 0x01, (byte) 0x01,
                    (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x01,
                    (byte) 0x02, (byte) 0x03, (byte) 0x04, (byte) 0x05, (byte) 0x06, (byte) 0x07, (byte) 0x08,
                    (byte) 0x09, (byte) 0x0a, (byte) 0x0b, (byte) 0xff, (byte) 0xc4, (byte) 0x00, (byte) 0xb5,
                    (byte) 0x11, (byte) 0x00, (byte) 0x02, (byte) 0x01, (byte) 0x02, (byte) 0x04, (byte) 0x04,
                    (byte) 0x03, (byte) 0x04, (byte) 0x07, (byte) 0x05, (byte) 0x04, (byte) 0x04, (byte) 0x00,
                    (byte) 0x01, (byte) 0x02, (byte) 0x77, (byte) 0x00, (byte) 0x01, (byte) 0x02, (byte) 0x03,
                    (byte) 0x11, (byte) 0x04, (byte) 0x05, (byte) 0x21, (byte) 0x31, (byte) 0x06, (byte) 0x12,
                    (byte) 0x41, (byte) 0x51, (byte) 0x07, (byte) 0x61, (byte) 0x71, (byte) 0x13, (byte) 0x22,
                    (byte) 0x32, (byte) 0x81, (byte) 0x08, (byte) 0x14, (byte) 0x42, (byte) 0x91, (byte) 0xa1,
                    (byte) 0xb1, (byte) 0xc1, (byte) 0x09, (byte) 0x23, (byte) 0x33, (byte) 0x52, (byte) 0xf0,
                    (byte) 0x15, (byte) 0x62, (byte) 0x72, (byte) 0xd1, (byte) 0x0a, (byte) 0x16, (byte) 0x24,
                    (byte) 0x34, (byte) 0xe1, (byte) 0x25, (byte) 0xf1, (byte) 0x17, (byte) 0x18, (byte) 0x19,
                    (byte) 0x1a, (byte) 0x26, (byte) 0x27, (byte) 0x28, (byte) 0x29, (byte) 0x2a, (byte) 0x35,
                    (byte) 0x36, (byte) 0x37, (byte) 0x38, (byte) 0x39, (byte) 0x3a, (byte) 0x43, (byte) 0x44,
                    (byte) 0x45, (byte) 0x46, (byte) 0x47, (byte) 0x48, (byte) 0x49, (byte) 0x4a, (byte) 0x53,
                    (byte) 0x54, (byte) 0x55, (byte) 0x56, (byte) 0x57, (byte) 0x58, (byte) 0x59, (byte) 0x5a,
                    (byte) 0x63, (byte) 0x64, (byte) 0x65, (byte) 0x66, (byte) 0x67, (byte) 0x68, (byte) 0x69,
                    (byte) 0x6a, (byte) 0x73, (byte) 0x74, (byte) 0x75, (byte) 0x76, (byte) 0x77, (byte) 0x78,
                    (byte) 0x79, (byte) 0x7a, (byte) 0x82, (byte) 0x83, (byte) 0x84, (byte) 0x85, (byte) 0x86,
                    (byte) 0x87, (byte) 0x88, (byte) 0x89, (byte) 0x8a, (byte) 0x92, (byte) 0x93, (byte) 0x94,
                    (byte) 0x95, (byte) 0x96, (byte) 0x97, (byte) 0x98, (byte) 0x99, (byte) 0x9a, (byte) 0xa2,
                    (byte) 0xa3, (byte) 0xa4, (byte) 0xa5, (byte) 0xa6, (byte) 0xa7, (byte) 0xa8, (byte) 0xa9,
                    (byte) 0xaa, (byte) 0xb2, (byte) 0xb3, (byte) 0xb4, (byte) 0xb5, (byte) 0xb6, (byte) 0xb7,
                    (byte) 0xb8, (byte) 0xb9, (byte) 0xba, (byte) 0xc2, (byte) 0xc3, (byte) 0xc4, (byte) 0xc5,
                    (byte) 0xc6, (byte) 0xc7, (byte) 0xc8, (byte) 0xc9, (byte) 0xca, (byte) 0xd2, (byte) 0xd3,
                    (byte) 0xd4, (byte) 0xd5, (byte) 0xd6, (byte) 0xd7, (byte) 0xd8, (byte) 0xd9, (byte) 0xda,
                    (byte) 0xe2, (byte) 0xe3, (byte) 0xe4, (byte) 0xe5, (byte) 0xe6, (byte) 0xe7, (byte) 0xe8,
                    (byte) 0xe9, (byte) 0xea, (byte) 0xf2, (byte) 0xf3, (byte) 0xf4, (byte) 0xf5, (byte) 0xf6,
                    (byte) 0xf7, (byte) 0xf8, (byte) 0xf9, (byte) 0xfa, (byte) 0xff, (byte) 0xda, (byte) 0x00,
                    (byte) 0x0c, (byte) 0x03, (byte) 0x01, (byte) 0x00, (byte) 0x02, (byte) 0x11, (byte) 0x03,
                    (byte) 0x11, (byte) 0x00, (byte) 0x3f, (byte) 0x00, (byte) 0x8e, (byte) 0x8a, (byte) 0x28,
                    (byte) 0xa0, (byte) 0x0f, (byte) 0xff, (byte) 0xd9
            };
        }
    };

    protected static byte[] header = new byte[]{
            (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, (byte) 0x00, (byte) 0x10, (byte) 0x4A,
            (byte) 0x46, (byte) 0x49, (byte) 0x46, (byte) 0x00, (byte) 0x01, (byte) 0x01, (byte) 0x00,
            (byte) 0x00, (byte) 0x01, (byte) 0x00, (byte) 0x01, (byte) 0x00, (byte) 0x00, (byte) 0xFF,
            (byte) 0xDB, (byte) 0x00, (byte) 0x43, (byte) 0x00, (byte) 0x28, (byte) 0x1C, (byte) 0x1E,
            (byte) 0x23, (byte) 0x1E, (byte) 0x19, (byte) 0x28, (byte) 0x23, (byte) 0x21, (byte) 0x23,
            (byte) 0x2D, (byte) 0x2B, (byte) 0x28, (byte) 0x30, (byte) 0x3C, (byte) 0x64, (byte) 0x41,
            (byte) 0x3C, (byte) 0x37, (byte) 0x37, (byte) 0x3C, (byte) 0x7B, (byte) 0x58, (byte) 0x5D,
            (byte) 0x49, (byte) 0x64, (byte) 0x91, (byte) 0x80, (byte) 0x99, (byte) 0x96, (byte) 0x8F,
            (byte) 0x80, (byte) 0x8C, (byte) 0x8A, (byte) 0xA0, (byte) 0xB4, (byte) 0xE6, (byte) 0xC3,
            (byte) 0xA0, (byte) 0xAA, (byte) 0xDA, (byte) 0xAD, (byte) 0x8A, (byte) 0x8C, (byte) 0xC8,
            (byte) 0xFF, (byte) 0xCB, (byte) 0xDA, (byte) 0xEE, (byte) 0xF5, (byte) 0xFF, (byte) 0xFF,
            (byte) 0xFF, (byte) 0x9B, (byte) 0xC1, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFA,
            (byte) 0xFF, (byte) 0xE6, (byte) 0xFD, (byte) 0xFF, (byte) 0xF8, (byte) 0xFF, (byte) 0xDB,
            (byte) 0x00, (byte) 0x43, (byte) 0x01, (byte) 0x2B, (byte) 0x2D, (byte) 0x2D, (byte) 0x3C,
            (byte) 0x35, (byte) 0x3C, (byte) 0x76, (byte) 0x41, (byte) 0x41, (byte) 0x76, (byte) 0xF8,
            (byte) 0xA5, (byte) 0x8C, (byte) 0xA5, (byte) 0xF8, (byte) 0xF8, (byte) 0xF8, (byte) 0xF8,
            (byte) 0xF8, (byte) 0xF8, (byte) 0xF8, (byte) 0xF8, (byte) 0xF8, (byte) 0xF8, (byte) 0xF8,
            (byte) 0xF8, (byte) 0xF8, (byte) 0xF8, (byte) 0xF8, (byte) 0xF8, (byte) 0xF8, (byte) 0xF8,
            (byte) 0xF8, (byte) 0xF8, (byte) 0xF8, (byte) 0xF8, (byte) 0xF8, (byte) 0xF8, (byte) 0xF8,
            (byte) 0xF8, (byte) 0xF8, (byte) 0xF8, (byte) 0xF8, (byte) 0xF8, (byte) 0xF8, (byte) 0xF8,
            (byte) 0xF8, (byte) 0xF8, (byte) 0xF8, (byte) 0xF8, (byte) 0xF8, (byte) 0xF8, (byte) 0xF8,
            (byte) 0xF8, (byte) 0xF8, (byte) 0xF8, (byte) 0xF8, (byte) 0xF8, (byte) 0xF8, (byte) 0xF8,
            (byte) 0xF8, (byte) 0xF8, (byte) 0xF8, (byte) 0xF8, (byte) 0xFF, (byte) 0xC0, (byte) 0x00,
            (byte) 0x11, (byte) 0x08, (byte) 0x00, (byte) 0x1E, (byte) 0x00, (byte) 0x28, (byte) 0x03,
            (byte) 0x01, (byte) 0x22, (byte) 0x00, (byte) 0x02, (byte) 0x11, (byte) 0x01, (byte) 0x03,
            (byte) 0x11, (byte) 0x01, (byte) 0xFF, (byte) 0xC4, (byte) 0x00, (byte) 0x1F, (byte) 0x00,
            (byte) 0x00, (byte) 0x01, (byte) 0x05, (byte) 0x01, (byte) 0x01, (byte) 0x01, (byte) 0x01,
            (byte) 0x01, (byte) 0x01, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x01, (byte) 0x02, (byte) 0x03, (byte) 0x04,
            (byte) 0x05, (byte) 0x06, (byte) 0x07, (byte) 0x08, (byte) 0x09, (byte) 0x0A, (byte) 0x0B,
            (byte) 0xFF, (byte) 0xC4, (byte) 0x00, (byte) 0xB5, (byte) 0x10, (byte) 0x00, (byte) 0x02,
            (byte) 0x01, (byte) 0x03, (byte) 0x03, (byte) 0x02, (byte) 0x04, (byte) 0x03, (byte) 0x05,
            (byte) 0x05, (byte) 0x04, (byte) 0x04, (byte) 0x00, (byte) 0x00, (byte) 0x01, (byte) 0x7D,
            (byte) 0x01, (byte) 0x02, (byte) 0x03, (byte) 0x00, (byte) 0x04, (byte) 0x11, (byte) 0x05,
            (byte) 0x12, (byte) 0x21, (byte) 0x31, (byte) 0x41, (byte) 0x06, (byte) 0x13, (byte) 0x51,
            (byte) 0x61, (byte) 0x07, (byte) 0x22, (byte) 0x71, (byte) 0x14, (byte) 0x32, (byte) 0x81,
            (byte) 0x91, (byte) 0xA1, (byte) 0x08, (byte) 0x23, (byte) 0x42, (byte) 0xB1, (byte) 0xC1,
            (byte) 0x15, (byte) 0x52, (byte) 0xD1, (byte) 0xF0, (byte) 0x24, (byte) 0x33, (byte) 0x62,
            (byte) 0x72, (byte) 0x82, (byte) 0x09, (byte) 0x0A, (byte) 0x16, (byte) 0x17, (byte) 0x18,
            (byte) 0x19, (byte) 0x1A, (byte) 0x25, (byte) 0x26, (byte) 0x27, (byte) 0x28, (byte) 0x29,
            (byte) 0x2A, (byte) 0x34, (byte) 0x35, (byte) 0x36, (byte) 0x37, (byte) 0x38, (byte) 0x39,
            (byte) 0x3A, (byte) 0x43, (byte) 0x44, (byte) 0x45, (byte) 0x46, (byte) 0x47, (byte) 0x48,
            (byte) 0x49, (byte) 0x4A, (byte) 0x53, (byte) 0x54, (byte) 0x55, (byte) 0x56, (byte) 0x57,
            (byte) 0x58, (byte) 0x59, (byte) 0x5A, (byte) 0x63, (byte) 0x64, (byte) 0x65, (byte) 0x66,
            (byte) 0x67, (byte) 0x68, (byte) 0x69, (byte) 0x6A, (byte) 0x73, (byte) 0x74, (byte) 0x75,
            (byte) 0x76, (byte) 0x77, (byte) 0x78, (byte) 0x79, (byte) 0x7A, (byte) 0x83, (byte) 0x84,
            (byte) 0x85, (byte) 0x86, (byte) 0x87, (byte) 0x88, (byte) 0x89, (byte) 0x8A, (byte) 0x92,
            (byte) 0x93, (byte) 0x94, (byte) 0x95, (byte) 0x96, (byte) 0x97, (byte) 0x98, (byte) 0x99,
            (byte) 0x9A, (byte) 0xA2, (byte) 0xA3, (byte) 0xA4, (byte) 0xA5, (byte) 0xA6, (byte) 0xA7,
            (byte) 0xA8, (byte) 0xA9, (byte) 0xAA, (byte) 0xB2, (byte) 0xB3, (byte) 0xB4, (byte) 0xB5,
            (byte) 0xB6, (byte) 0xB7, (byte) 0xB8, (byte) 0xB9, (byte) 0xBA, (byte) 0xC2, (byte) 0xC3,
            (byte) 0xC4, (byte) 0xC5, (byte) 0xC6, (byte) 0xC7, (byte) 0xC8, (byte) 0xC9, (byte) 0xCA,
            (byte) 0xD2, (byte) 0xD3, (byte) 0xD4, (byte) 0xD5, (byte) 0xD6, (byte) 0xD7, (byte) 0xD8,
            (byte) 0xD9, (byte) 0xDA, (byte) 0xE1, (byte) 0xE2, (byte) 0xE3, (byte) 0xE4, (byte) 0xE5,
            (byte) 0xE6, (byte) 0xE7, (byte) 0xE8, (byte) 0xE9, (byte) 0xEA, (byte) 0xF1, (byte) 0xF2,
            (byte) 0xF3, (byte) 0xF4, (byte) 0xF5, (byte) 0xF6, (byte) 0xF7, (byte) 0xF8, (byte) 0xF9,
            (byte) 0xFA, (byte) 0xFF, (byte) 0xC4, (byte) 0x00, (byte) 0x1F, (byte) 0x01, (byte) 0x00,
            (byte) 0x03, (byte) 0x01, (byte) 0x01, (byte) 0x01, (byte) 0x01, (byte) 0x01, (byte) 0x01,
            (byte) 0x01, (byte) 0x01, (byte) 0x01, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x01, (byte) 0x02, (byte) 0x03, (byte) 0x04, (byte) 0x05,
            (byte) 0x06, (byte) 0x07, (byte) 0x08, (byte) 0x09, (byte) 0x0A, (byte) 0x0B, (byte) 0xFF,
            (byte) 0xC4, (byte) 0x00, (byte) 0xB5, (byte) 0x11, (byte) 0x00, (byte) 0x02, (byte) 0x01,
            (byte) 0x02, (byte) 0x04, (byte) 0x04, (byte) 0x03, (byte) 0x04, (byte) 0x07, (byte) 0x05,
            (byte) 0x04, (byte) 0x04, (byte) 0x00, (byte) 0x01, (byte) 0x02, (byte) 0x77, (byte) 0x00,
            (byte) 0x01, (byte) 0x02, (byte) 0x03, (byte) 0x11, (byte) 0x04, (byte) 0x05, (byte) 0x21,
            (byte) 0x31, (byte) 0x06, (byte) 0x12, (byte) 0x41, (byte) 0x51, (byte) 0x07, (byte) 0x61,
            (byte) 0x71, (byte) 0x13, (byte) 0x22, (byte) 0x32, (byte) 0x81, (byte) 0x08, (byte) 0x14,
            (byte) 0x42, (byte) 0x91, (byte) 0xA1, (byte) 0xB1, (byte) 0xC1, (byte) 0x09, (byte) 0x23,
            (byte) 0x33, (byte) 0x52, (byte) 0xF0, (byte) 0x15, (byte) 0x62, (byte) 0x72, (byte) 0xD1,
            (byte) 0x0A, (byte) 0x16, (byte) 0x24, (byte) 0x34, (byte) 0xE1, (byte) 0x25, (byte) 0xF1,
            (byte) 0x17, (byte) 0x18, (byte) 0x19, (byte) 0x1A, (byte) 0x26, (byte) 0x27, (byte) 0x28,
            (byte) 0x29, (byte) 0x2A, (byte) 0x35, (byte) 0x36, (byte) 0x37, (byte) 0x38, (byte) 0x39,
            (byte) 0x3A, (byte) 0x43, (byte) 0x44, (byte) 0x45, (byte) 0x46, (byte) 0x47, (byte) 0x48,
            (byte) 0x49, (byte) 0x4A, (byte) 0x53, (byte) 0x54, (byte) 0x55, (byte) 0x56, (byte) 0x57,
            (byte) 0x58, (byte) 0x59, (byte) 0x5A, (byte) 0x63, (byte) 0x64, (byte) 0x65, (byte) 0x66,
            (byte) 0x67, (byte) 0x68, (byte) 0x69, (byte) 0x6A, (byte) 0x73, (byte) 0x74, (byte) 0x75,
            (byte) 0x76, (byte) 0x77, (byte) 0x78, (byte) 0x79, (byte) 0x7A, (byte) 0x82, (byte) 0x83,
            (byte) 0x84, (byte) 0x85, (byte) 0x86, (byte) 0x87, (byte) 0x88, (byte) 0x89, (byte) 0x8A,
            (byte) 0x92, (byte) 0x93, (byte) 0x94, (byte) 0x95, (byte) 0x96, (byte) 0x97, (byte) 0x98,
            (byte) 0x99, (byte) 0x9A, (byte) 0xA2, (byte) 0xA3, (byte) 0xA4, (byte) 0xA5, (byte) 0xA6,
            (byte) 0xA7, (byte) 0xA8, (byte) 0xA9, (byte) 0xAA, (byte) 0xB2, (byte) 0xB3, (byte) 0xB4,
            (byte) 0xB5, (byte) 0xB6, (byte) 0xB7, (byte) 0xB8, (byte) 0xB9, (byte) 0xBA, (byte) 0xC2,
            (byte) 0xC3, (byte) 0xC4, (byte) 0xC5, (byte) 0xC6, (byte) 0xC7, (byte) 0xC8, (byte) 0xC9,
            (byte) 0xCA, (byte) 0xD2, (byte) 0xD3, (byte) 0xD4, (byte) 0xD5, (byte) 0xD6, (byte) 0xD7,
            (byte) 0xD8, (byte) 0xD9, (byte) 0xDA, (byte) 0xE2, (byte) 0xE3, (byte) 0xE4, (byte) 0xE5,
            (byte) 0xE6, (byte) 0xE7, (byte) 0xE8, (byte) 0xE9, (byte) 0xEA, (byte) 0xF2, (byte) 0xF3,
            (byte) 0xF4, (byte) 0xF5, (byte) 0xF6, (byte) 0xF7, (byte) 0xF8, (byte) 0xF9, (byte) 0xFA,
            (byte) 0xFF, (byte) 0xDA, (byte) 0x00, (byte) 0x0C, (byte) 0x03, (byte) 0x01, (byte) 0x00,
            (byte) 0x02, (byte) 0x11, (byte) 0x03, (byte) 0x11, (byte) 0x00, (byte) 0x3F, (byte) 0x00
    };

    protected static byte[] footer = new byte[]{
            (byte) 0xFF,
            (byte) 0xD9
    };

    public static Bitmap createBitmap(int width, int height, Bitmap.Config config) {
        Bitmap bitmap;
        if (Build.VERSION.SDK_INT < 21) {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inDither = true;
            options.inPreferredConfig = config;
            options.inPurgeable = true;
            options.inSampleSize = 1;
            options.inMutable = true;
            byte[] array = jpegData.get();
            array[76] = (byte) (height >> 8);
            array[77] = (byte) (height & 0x00ff);
            array[78] = (byte) (width >> 8);
            array[79] = (byte) (width & 0x00ff);
            bitmap = BitmapFactory.decodeByteArray(array, 0, array.length, options);
            Utilities.pinBitmap(bitmap);
            bitmap.setHasAlpha(true);
            bitmap.eraseColor(0);
        } else {
            bitmap = Bitmap.createBitmap(width, height, config);
        }
        if (config == Bitmap.Config.ARGB_8888 || config == Bitmap.Config.ARGB_4444) {
            bitmap.eraseColor(Color.TRANSPARENT);
        }
        return bitmap;
    }

    private static void checkXYSign(int x, int y) {
        if (x < 0) {
            throw new IllegalArgumentException("x must be >= 0");
        }
        if (y < 0) {
            throw new IllegalArgumentException("y must be >= 0");
        }
    }

    private static void checkWidthHeight(int width, int height) {
        if (width <= 0) {
            throw new IllegalArgumentException("width must be > 0");
        }
        if (height <= 0) {
            throw new IllegalArgumentException("height must be > 0");
        }
    }

    public static Bitmap createBitmap(Bitmap source, int x, int y, int width, int height, Matrix m, boolean filter) {
        if (Build.VERSION.SDK_INT >= 21) {
            return Bitmap.createBitmap(source, x, y, width, height, m, filter);
        }
        checkXYSign(x, y);
        checkWidthHeight(width, height);
        if (x + width > source.getWidth()) {
            throw new IllegalArgumentException("x + width must be <= bitmap.width()");
        }
        if (y + height > source.getHeight()) {
            throw new IllegalArgumentException("y + height must be <= bitmap.height()");
        }
        if (!source.isMutable() && x == 0 && y == 0 && width == source.getWidth() && height == source.getHeight() && (m == null || m.isIdentity())) {
            return source;
        }

        int neww = width;
        int newh = height;
        Canvas canvas = new Canvas();
        Bitmap bitmap;
        Paint paint;

        Rect srcR = new Rect(x, y, x + width, y + height);
        RectF dstR = new RectF(0, 0, width, height);

        Bitmap.Config newConfig = Bitmap.Config.ARGB_8888;
        final Bitmap.Config config = source.getConfig();
        if (config != null) {
            switch (config) {
                case RGB_565:
                    newConfig = Bitmap.Config.ARGB_8888;
                    break;
                case ALPHA_8:
                    newConfig = Bitmap.Config.ALPHA_8;
                    break;
                case ARGB_4444:
                case ARGB_8888:
                default:
                    newConfig = Bitmap.Config.ARGB_8888;
                    break;
            }
        }

        if (m == null || m.isIdentity()) {
            bitmap = createBitmap(neww, newh, newConfig);
            paint = null;
        } else {
            final boolean transformed = !m.rectStaysRect();
            RectF deviceR = new RectF();
            m.mapRect(deviceR, dstR);
            neww = Math.round(deviceR.width());
            newh = Math.round(deviceR.height());
            bitmap = createBitmap(neww, newh, transformed ? Bitmap.Config.ARGB_8888 : newConfig);
            canvas.translate(-deviceR.left, -deviceR.top);
            canvas.concat(m);
            paint = new Paint();
            paint.setFilterBitmap(filter);
            if (transformed) {
                paint.setAntiAlias(true);
            }
        }
        bitmap.setDensity(source.getDensity());
        bitmap.setHasAlpha(source.hasAlpha());
        if (Build.VERSION.SDK_INT >= 19) {
            bitmap.setPremultiplied(source.isPremultiplied());
        }
        canvas.setBitmap(bitmap);
        canvas.drawBitmap(source, srcR, dstR, paint);
        try {
            canvas.setBitmap(null);
        } catch (Exception e) {
            //don't promt, this will crash on 2.x
        }
        return bitmap;
    }

    public static Bitmap createBitmap(Bitmap source, int x, int y, int width, int height) {
        return createBitmap(source, x, y, width, height, null, false);
    }

    public static Bitmap createScaledBitmap(Bitmap src, int dstWidth, int dstHeight, boolean filter) {
        if (Build.VERSION.SDK_INT >= 21) {
            return Bitmap.createScaledBitmap(src, dstWidth, dstHeight, filter);
        }
        Matrix m;
        synchronized (Bitmap.class) {
            m = sScaleMatrix;
            sScaleMatrix = null;
        }
        if (m == null) {
            m = new Matrix();
        }
        final int width = src.getWidth();
        final int height = src.getHeight();
        final float sx = dstWidth / (float) width;
        final float sy = dstHeight / (float) height;
        m.setScale(sx, sy);
        Bitmap b = createBitmap(src, 0, 0, width, height, m, filter);
        synchronized (Bitmap.class) {
            if (sScaleMatrix == null) {
                sScaleMatrix = m;
            }
        }
        return b;
    }
}
