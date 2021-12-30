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
import android.graphics.Paint;
import android.graphics.Rect;

import com.carrotsearch.randomizedtesting.Xoroshiro128PlusRandom;

import java.io.File;
import java.io.FileInputStream;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Utilities {

    public static Pattern pattern = Pattern.compile("[\\-0-9]+");
    public static SecureRandom random = new SecureRandom();
    public static Random fastRandom = new Xoroshiro128PlusRandom(random.nextLong());

    public static volatile DispatchQueue stageQueue = new DispatchQueue("stageQueue");
    public static volatile DispatchQueue globalQueue = new DispatchQueue("globalQueue");
    public static volatile DispatchQueue cacheClearQueue = new DispatchQueue("cacheClearQueue");
    public static volatile DispatchQueue searchQueue = new DispatchQueue("searchQueue");
    public static volatile DispatchQueue phoneBookQueue = new DispatchQueue("phoneBookQueue");
    public static volatile DispatchQueue themeQueue = new DispatchQueue("themeQueue");

    final protected static char[] hexArray = "0123456789ABCDEF".toCharArray();

    static {
        try {
            File URANDOM_FILE = new File("/dev/urandom");
            FileInputStream sUrandomIn = new FileInputStream(URANDOM_FILE);
            byte[] buffer = new byte[1024];
            sUrandomIn.read(buffer);
            sUrandomIn.close();
            random.setSeed(buffer);
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    public native static int pinBitmap(Bitmap bitmap);
    public native static void unpinBitmap(Bitmap bitmap);
    public native static void blurBitmap(Object bitmap, int radius, int unpin, int width, int height, int stride);
    public native static int needInvert(Object bitmap, int unpin, int width, int height, int stride);
    public native static void calcCDT(ByteBuffer hsvBuffer, int width, int height, ByteBuffer buffer, ByteBuffer calcBuffer);
    public native static boolean loadWebpImage(Bitmap bitmap, ByteBuffer buffer, int len, BitmapFactory.Options options, boolean unpin);
    public native static int convertVideoFrame(ByteBuffer src, ByteBuffer dest, int destFormat, int width, int height, int padding, int swap);
    private native static void aesIgeEncryption(ByteBuffer buffer, byte[] key, byte[] iv, boolean encrypt, int offset, int length);
    private native static void aesIgeEncryptionByteArray(byte[] buffer, byte[] key, byte[] iv, boolean encrypt, int offset, int length);
    public native static void aesCtrDecryption(ByteBuffer buffer, byte[] key, byte[] iv, int offset, int length);
    public native static void aesCtrDecryptionByteArray(byte[] buffer, byte[] key, byte[] iv, int offset, int length, int n);
    private native static void aesCbcEncryptionByteArray(byte[] buffer, byte[] key, byte[] iv, int offset, int length, int n, int encrypt);
    public native static void aesCbcEncryption(ByteBuffer buffer, byte[] key, byte[] iv, int offset, int length, int encrypt);
    public native static String readlink(String path);
    public native static String readlinkFd(int fd);
    public native static long getDirSize(String path, int docType, boolean subdirs);
    public native static void clearDir(String path, int docType, long time, boolean subdirs);
    private native static int pbkdf2(byte[] password, byte[] salt, byte[] dst, int iterations);
    public static native void stackBlurBitmap(Bitmap bitmap, int radius);
    public static native void drawDitheredGradient(Bitmap bitmap, int[] colors, int startX, int startY, int endX, int endY);
    public static native int saveProgressiveJpeg(Bitmap bitmap, int width, int height, int stride, int quality, String path);
    public static native void generateGradient(Bitmap bitmap, boolean unpin, int phase, float progress, int width, int height, int stride, int[] colors);

    public static Bitmap blurWallpaper(Bitmap src) {
        if (src == null) {
            return null;
        }
        Bitmap b;
        if (src.getHeight() > src.getWidth()) {
            b = Bitmap.createBitmap(Math.round(450f * src.getWidth() / src.getHeight()), 450, Bitmap.Config.ARGB_8888);
        } else {
            b = Bitmap.createBitmap(450, Math.round(450f * src.getHeight() / src.getWidth()), Bitmap.Config.ARGB_8888);
        }
        Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
        Rect rect = new Rect(0, 0, b.getWidth(), b.getHeight());
        new Canvas(b).drawBitmap(src, null, rect, paint);
        stackBlurBitmap(b, 12);
        return b;
    }

    public static void aesIgeEncryption(ByteBuffer buffer, byte[] key, byte[] iv, boolean encrypt, boolean changeIv, int offset, int length) {
        aesIgeEncryption(buffer, key, changeIv ? iv : iv.clone(), encrypt, offset, length);
    }

    public static void aesIgeEncryptionByteArray(byte[] buffer, byte[] key, byte[] iv, boolean encrypt, boolean changeIv, int offset, int length) {
        aesIgeEncryptionByteArray(buffer, key, changeIv ? iv : iv.clone(), encrypt, offset, length);
    }

    public static void aesCbcEncryptionByteArraySafe(byte[] buffer, byte[] key, byte[] iv, int offset, int length, int n, int encrypt) {
        aesCbcEncryptionByteArray(buffer, key, iv.clone(), offset, length, n, encrypt);
    }

    public static Integer parseInt(CharSequence value) {
        if (value == null) {
            return 0;
        }
        int val = 0;
        try {
            Matcher matcher = pattern.matcher(value);
            if (matcher.find()) {
                String num = matcher.group(0);
                val = Integer.parseInt(num);
            }
        } catch (Exception ignore) {

        }
        return val;
    }

    public static Long parseLong(String value) {
        if (value == null) {
            return 0L;
        }
        long val = 0L;
        try {
            Matcher matcher = pattern.matcher(value);
            if (matcher.find()) {
                String num = matcher.group(0);
                val = Long.parseLong(num);
            }
        } catch (Exception ignore) {

        }
        return val;
    }

    public static String parseIntToString(String value) {
        Matcher matcher = pattern.matcher(value);
        if (matcher.find()) {
            return matcher.group(0);
        }
        return null;
    }

    public static String bytesToHex(byte[] bytes) {
        if (bytes == null) {
            return "";
        }
        char[] hexChars = new char[bytes.length * 2];
        int v;
        for (int j = 0; j < bytes.length; j++) {
            v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }

    public static byte[] hexToBytes(String hex) {
        if (hex == null) {
            return null;
        }
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4) + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }

    public static boolean isGoodPrime(byte[] prime, int g) {
        if (!(g >= 2 && g <= 7)) {
            return false;
        }

        if (prime.length != 256 || prime[0] >= 0) {
            return false;
        }

        BigInteger dhBI = new BigInteger(1, prime);

        if (g == 2) { // p mod 8 = 7 for g = 2;
            BigInteger res = dhBI.mod(BigInteger.valueOf(8));
            if (res.intValue() != 7) {
                return false;
            }
        } else if (g == 3) { // p mod 3 = 2 for g = 3;
            BigInteger res = dhBI.mod(BigInteger.valueOf(3));
            if (res.intValue() != 2) {
                return false;
            }
        } else if (g == 5) { // p mod 5 = 1 or 4 for g = 5;
            BigInteger res = dhBI.mod(BigInteger.valueOf(5));
            int val = res.intValue();
            if (val != 1 && val != 4) {
                return false;
            }
        } else if (g == 6) { // p mod 24 = 19 or 23 for g = 6;
            BigInteger res = dhBI.mod(BigInteger.valueOf(24));
            int val = res.intValue();
            if (val != 19 && val != 23) {
                return false;
            }
        } else if (g == 7) { // p mod 7 = 3, 5 or 6 for g = 7.
            BigInteger res = dhBI.mod(BigInteger.valueOf(7));
            int val = res.intValue();
            if (val != 3 && val != 5 && val != 6) {
                return false;
            }
        }

        String hex = bytesToHex(prime);
        if (hex.equals("C71CAEB9C6B1C9048E6C522F70F13F73980D40238E3E21C14934D037563D930F48198A0AA7C14058229493D22530F4DBFA336F6E0AC925139543AED44CCE7C3720FD51F69458705AC68CD4FE6B6B13ABDC9746512969328454F18FAF8C595F642477FE96BB2A941D5BCD1D4AC8CC49880708FA9B378E3C4F3A9060BEE67CF9A4A4A695811051907E162753B56B0F6B410DBA74D8A84B2A14B3144E0EF1284754FD17ED950D5965B4B9DD46582DB1178D169C6BC465B0D6FF9CA3928FEF5B9AE4E418FC15E83EBEA0F87FA9FF5EED70050DED2849F47BF959D956850CE929851F0D8115F635B105EE2E4E15D04B2454BF6F4FADF034B10403119CD8E3B92FCC5B")) {
            return true;
        }

        BigInteger dhBI2 = dhBI.subtract(BigInteger.valueOf(1)).divide(BigInteger.valueOf(2));
        return !(!dhBI.isProbablePrime(30) || !dhBI2.isProbablePrime(30));
    }

    public static boolean isGoodGaAndGb(BigInteger g_a, BigInteger p) {
        return !(g_a.compareTo(BigInteger.valueOf(1)) <= 0 || g_a.compareTo(p.subtract(BigInteger.valueOf(1))) >= 0);
    }

    public static boolean arraysEquals(byte[] arr1, int offset1, byte[] arr2, int offset2) {
        if (arr1 == null || arr2 == null || offset1 < 0 || offset2 < 0 || arr1.length - offset1 > arr2.length - offset2 || arr1.length - offset1 < 0 || arr2.length - offset2 < 0) {
            return false;
        }
        boolean result = true;
        for (int a = offset1; a < arr1.length; a++) {
            if (arr1[a + offset1] != arr2[a + offset2]) {
                result = false;
            }
        }
        return result;
    }

    public static byte[] computeSHA1(byte[] convertme, int offset, int len) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            md.update(convertme, offset, len);
            return md.digest();
        } catch (Exception e) {
            FileLog.e(e);
        }
        return new byte[20];
    }

    public static byte[] computeSHA1(ByteBuffer convertme, int offset, int len) {
        int oldp = convertme.position();
        int oldl = convertme.limit();
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            convertme.position(offset);
            convertme.limit(len);
            md.update(convertme);
            return md.digest();
        } catch (Exception e) {
            FileLog.e(e);
        } finally {
            convertme.limit(oldl);
            convertme.position(oldp);
        }
        return new byte[20];
    }

    public static byte[] computeSHA1(ByteBuffer convertme) {
        return computeSHA1(convertme, 0, convertme.limit());
    }

    public static byte[] computeSHA1(byte[] convertme) {
        return computeSHA1(convertme, 0, convertme.length);
    }

    public static byte[] computeSHA256(byte[] convertme) {
        return computeSHA256(convertme, 0, convertme.length);
    }

    public static byte[] computeSHA256(byte[] convertme, int offset, int len) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(convertme, offset, len);
            return md.digest();
        } catch (Exception e) {
            FileLog.e(e);
        }
        return new byte[32];
    }

    public static byte[] computeSHA256(byte[]... args) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            for (int a = 0; a < args.length; a++) {
                md.update(args[a], 0, args[a].length);
            }
            return md.digest();
        } catch (Exception e) {
            FileLog.e(e);
        }
        return new byte[32];
    }

    public static byte[] computeSHA512(byte[] convertme) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-512");
            md.update(convertme, 0, convertme.length);
            return md.digest();
        } catch (Exception e) {
            FileLog.e(e);
        }
        return new byte[64];
    }

    public static byte[] computeSHA512(byte[] convertme, byte[] convertme2) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-512");
            md.update(convertme, 0, convertme.length);
            md.update(convertme2, 0, convertme2.length);
            return md.digest();
        } catch (Exception e) {
            FileLog.e(e);
        }
        return new byte[64];
    }

    public static byte[] computePBKDF2(byte[] password, byte[] salt) {
        byte[] dst = new byte[64];
        Utilities.pbkdf2(password, salt, dst, 100000);
        return dst;
    }

    public static byte[] computeSHA512(byte[] convertme, byte[] convertme2, byte[] convertme3) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-512");
            md.update(convertme, 0, convertme.length);
            md.update(convertme2, 0, convertme2.length);
            md.update(convertme3, 0, convertme3.length);
            return md.digest();
        } catch (Exception e) {
            FileLog.e(e);
        }
        return new byte[64];
    }

    public static byte[] computeSHA256(byte[] b1, int o1, int l1, ByteBuffer b2, int o2, int l2) {
        int oldp = b2.position();
        int oldl = b2.limit();
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(b1, o1, l1);
            b2.position(o2);
            b2.limit(l2);
            md.update(b2);
            return md.digest();
        } catch (Exception e) {
            FileLog.e(e);
        } finally {
            b2.limit(oldl);
            b2.position(oldp);
        }
        return new byte[32];
    }

    public static long bytesToLong(byte[] bytes) {
        return ((long) bytes[7] << 56) + (((long) bytes[6] & 0xFF) << 48) + (((long) bytes[5] & 0xFF) << 40) + (((long) bytes[4] & 0xFF) << 32)
                + (((long) bytes[3] & 0xFF) << 24) + (((long) bytes[2] & 0xFF) << 16) + (((long) bytes[1] & 0xFF) << 8) + ((long) bytes[0] & 0xFF);
    }

    public static int bytesToInt(byte[] bytes) {
        return (((int) bytes[3] & 0xFF) << 24) + (((int) bytes[2] & 0xFF) << 16) + (((int) bytes[1] & 0xFF) << 8) + ((int) bytes[0] & 0xFF);
    }

    public static byte[] intToBytes(int value) {
        return new byte[]{
                (byte) (value >>> 24),
                (byte) (value >>> 16),
                (byte) (value >>> 8),
                (byte) value};
    }

    public static String MD5(String md5) {
        if (md5 == null) {
            return null;
        }
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
            byte[] array = md.digest(AndroidUtilities.getStringBytes(md5));
            StringBuilder sb = new StringBuilder();
            for (int a = 0; a < array.length; a++) {
                sb.append(Integer.toHexString((array[a] & 0xFF) | 0x100).substring(1, 3));
            }
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            FileLog.e(e);
        }
        return null;
    }
}
