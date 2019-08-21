/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.messenger;

import org.telegram.tgnet.TLRPC;

import java.util.Locale;

public class VideoEditedInfo {
    public long startTime;
    public long endTime;
    public float start;
    public float end;
    public int rotationValue;
    public int originalWidth;
    public int originalHeight;
    public int resultWidth;
    public int resultHeight;
    public int bitrate;
    public int framerate = 24;
    public String originalPath;
    public long estimatedSize;
    public long estimatedDuration;
    public boolean roundVideo;
    public boolean muted;
    public TLRPC.InputFile file;
    public TLRPC.InputEncryptedFile encryptedFile;
    public byte[] key;
    public byte[] iv;

    public String getString() {
        return String.format(Locale.US, "-1_%d_%d_%d_%d_%d_%d_%d_%d_%d_%s", startTime, endTime, rotationValue, originalWidth, originalHeight, bitrate, resultWidth, resultHeight, framerate, originalPath);
    }

    public boolean parseString(String string) {
        if (string.length() < 6) {
            return false;
        }
        try {
            String args[] = string.split("_");
            if (args.length >= 10) {
                startTime = Long.parseLong(args[1]);
                endTime = Long.parseLong(args[2]);
                rotationValue = Integer.parseInt(args[3]);
                originalWidth = Integer.parseInt(args[4]);
                originalHeight = Integer.parseInt(args[5]);
                bitrate = Integer.parseInt(args[6]);
                resultWidth = Integer.parseInt(args[7]);
                resultHeight = Integer.parseInt(args[8]);
                int pathStart;
                if (args.length >= 11) {
                    try {
                        framerate = Integer.parseInt(args[9]);
                    } catch (Exception ignore) {

                    }
                }
                if (framerate <= 0 || framerate > 400) {
                    pathStart = 9;
                    framerate = 25;
                } else {
                    pathStart = 10;
                }
                for (int a = pathStart; a < args.length; a++) {
                    if (originalPath == null) {
                        originalPath = args[a];
                    } else {
                        originalPath += "_" + args[a];
                    }
                }
            }
            return true;
        } catch (Exception e) {
            FileLog.e(e);
        }
        return false;
    }

    public boolean needConvert() {
        return !roundVideo || roundVideo && (startTime > 0 || endTime != -1 && endTime != estimatedDuration);
    }
}
