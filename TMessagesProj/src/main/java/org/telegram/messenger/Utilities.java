/*
 * This is the source code of Telegram for Android v. 1.3.2.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013.
 */

package org.telegram.messenger;

import android.app.Activity;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.text.SpannableStringBuilder;
import android.util.Base64;

import net.hockeyapp.android.CrashManager;
import net.hockeyapp.android.CrashManagerListener;
import net.hockeyapp.android.UpdateManager;

import org.telegram.android.AndroidUtilities;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.RSAPublicKeySpec;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import javax.crypto.Cipher;

public class Utilities {
    public static Pattern pattern = Pattern.compile("[0-9]+");
    public static SecureRandom random = new SecureRandom();

    public static ArrayList<String> goodPrimes = new ArrayList<>();

    public static class TPFactorizedValue {
        public long p, q;
    }

    public static volatile DispatchQueue stageQueue = new DispatchQueue("stageQueue");
    public static volatile DispatchQueue globalQueue = new DispatchQueue("globalQueue");
    public static volatile DispatchQueue searchQueue = new DispatchQueue("searchQueue");
    public static volatile DispatchQueue photoBookQueue = new DispatchQueue("photoBookQueue");

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
            FileLog.e("tmessages", e);
        }

        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("primes", Context.MODE_PRIVATE);
        String primes = preferences.getString("primes", null);
        if (primes == null) {
            goodPrimes.add("C71CAEB9C6B1C9048E6C522F70F13F73980D40238E3E21C14934D037563D930F48198A0AA7C14058229493D22530F4DBFA336F6E0AC925139543AED44CCE7C3720FD51F69458705AC68CD4FE6B6B13ABDC9746512969328454F18FAF8C595F642477FE96BB2A941D5BCD1D4AC8CC49880708FA9B378E3C4F3A9060BEE67CF9A4A4A695811051907E162753B56B0F6B410DBA74D8A84B2A14B3144E0EF1284754FD17ED950D5965B4B9DD46582DB1178D169C6BC465B0D6FF9CA3928FEF5B9AE4E418FC15E83EBEA0F87FA9FF5EED70050DED2849F47BF959D956850CE929851F0D8115F635B105EE2E4E15D04B2454BF6F4FADF034B10403119CD8E3B92FCC5B");
        } else {
            try {
                byte[] bytes = Base64.decode(primes, Base64.DEFAULT);
                if (bytes != null) {
                    SerializedData data = new SerializedData(bytes);
                    int count = data.readInt32();
                    for (int a = 0; a < count; a++) {
                        goodPrimes.add(data.readString());
                    }
                    data.cleanup();
                }
            } catch (Exception e) {
                FileLog.e("tmessages", e);
                goodPrimes.clear();
                goodPrimes.add("C71CAEB9C6B1C9048E6C522F70F13F73980D40238E3E21C14934D037563D930F48198A0AA7C14058229493D22530F4DBFA336F6E0AC925139543AED44CCE7C3720FD51F69458705AC68CD4FE6B6B13ABDC9746512969328454F18FAF8C595F642477FE96BB2A941D5BCD1D4AC8CC49880708FA9B378E3C4F3A9060BEE67CF9A4A4A695811051907E162753B56B0F6B410DBA74D8A84B2A14B3144E0EF1284754FD17ED950D5965B4B9DD46582DB1178D169C6BC465B0D6FF9CA3928FEF5B9AE4E418FC15E83EBEA0F87FA9FF5EED70050DED2849F47BF959D956850CE929851F0D8115F635B105EE2E4E15D04B2454BF6F4FADF034B10403119CD8E3B92FCC5B");
            }
        }
    }

    public native static long doPQNative(long _what);
    public native static void loadBitmap(String path, Bitmap bitmap, int scale, int width, int height, int stride);
    public native static int pinBitmap(Bitmap bitmap);
    public native static void blurBitmap(Object bitmap, int radius);
    public native static void calcCDT(ByteBuffer hsvBuffer, int width, int height, ByteBuffer buffer);
    public native static Bitmap loadWebpImage(ByteBuffer buffer, int len, BitmapFactory.Options options);
    public native static Bitmap loadBpgImage(ByteBuffer buffer, int len, BitmapFactory.Options options);
    public native static int convertVideoFrame(ByteBuffer src, ByteBuffer dest, int destFormat, int width, int height, int padding, int swap);
    private native static void aesIgeEncryption(ByteBuffer buffer, byte[] key, byte[] iv, boolean encrypt, int offset, int length);

    public static void aesIgeEncryption(ByteBuffer buffer, byte[] key, byte[] iv, boolean encrypt, boolean changeIv, int offset, int length) {
        aesIgeEncryption(buffer, key, changeIv ? iv : iv.clone(), encrypt, offset, length);
    }

    public static Integer parseInt(String value) {
        if (value == null) {
            return 0;
        }
        Integer val = 0;
        try {
            Matcher matcher = pattern.matcher(value);
            if (matcher.find()) {
                String num = matcher.group(0);
                val = Integer.parseInt(num);
            }
        } catch (Exception e) {
            FileLog.e("tmessages", e);
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
        for (String cached : goodPrimes) {
            if (cached.equals(hex)) {
                return true;
            }
        }

        BigInteger dhBI2 = dhBI.subtract(BigInteger.valueOf(1)).divide(BigInteger.valueOf(2));
        if (!dhBI.isProbablePrime(30) || !dhBI2.isProbablePrime(30)) {
            return false;
        }

        goodPrimes.add(hex);

        globalQueue.postRunnable(new Runnable() {
            @Override
            public void run() {
                try {
                    SerializedData data = new SerializedData();
                    data.writeInt32(goodPrimes.size());
                    for (String pr : goodPrimes) {
                        data.writeString(pr);
                    }
                    byte[] bytes = data.toByteArray();
                    data.cleanup();
                    SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("primes", Context.MODE_PRIVATE);
                    SharedPreferences.Editor editor = preferences.edit();
                    editor.putString("primes", Base64.encodeToString(bytes, Base64.DEFAULT));
                    editor.commit();
                } catch (Exception e) {
                    FileLog.e("tmessages", e);
                }
            }
        });

        return true;
    }

    public static boolean isGoodGaAndGb(BigInteger g_a, BigInteger p) {
        return !(g_a.compareTo(BigInteger.valueOf(1)) != 1 || g_a.compareTo(p.subtract(BigInteger.valueOf(1))) != -1);
    }

    public static TPFactorizedValue getFactorizedValue(long what) {
        long g = doPQNative(what);
        if (g > 1 && g < what) {
            long p1 = g;
            long p2 = what / g;
            if (p1 > p2) {
                long tmp = p1;
                p1 = p2;
                p2 = tmp;
            }

            TPFactorizedValue result = new TPFactorizedValue();
            result.p = p1;
            result.q = p2;

            return result;
        } else {
            FileLog.e("tmessages", String.format("**** Factorization failed for %d", what));
            TPFactorizedValue result = new TPFactorizedValue();
            result.p = 0;
            result.q = 0;
            return result;
        }
    }

    public static boolean arraysEquals(byte[] arr1, int offset1, byte[] arr2, int offset2) {
        if (arr1 == null || arr2 == null || arr1.length - offset1 != arr2.length - offset2 || arr1.length - offset1 < 0) {
            return false;
        }
        for (int a = offset1; a < arr1.length; a++) {
            if (arr1[a + offset1] != arr2[a + offset2]) {
                return false;
            }
        }
        return true;
    }

    public static byte[] computeSHA1(byte[] convertme, int offset, int len) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            md.update(convertme, offset, len);
            return md.digest();
        } catch (Exception e) {
            FileLog.e("tmessages", e);
        }
        return null;
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
            FileLog.e("tmessages", e);
        } finally {
            convertme.limit(oldl);
            convertme.position(oldp);
        }
        return null;
    }

    public static byte[] computeSHA1(ByteBuffer convertme) {
        return computeSHA1(convertme, 0, convertme.limit());
    }

    public static byte[] computeSHA1(byte[] convertme) {
        return computeSHA1(convertme, 0, convertme.length);
    }

    public static byte[] computeSHA256(byte[] convertme, int offset, int len) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(convertme, offset, len);
            return md.digest();
        } catch (Exception e) {
            FileLog.e("tmessages", e);
        }
        return null;
    }

    public static byte[] encryptWithRSA(BigInteger[] key, byte[] data) {
        try {
            KeyFactory fact = KeyFactory.getInstance("RSA");
            RSAPublicKeySpec keySpec = new RSAPublicKeySpec(key[0], key[1]);
            PublicKey publicKey = fact.generatePublic(keySpec);
            final Cipher cipher = Cipher.getInstance("RSA");
            cipher.init(Cipher.ENCRYPT_MODE, publicKey);
            return cipher.doFinal(data);
        } catch (Exception e) {
            FileLog.e("tmessages", e);
        }
        return null;
    }

    public static long bytesToLong(byte[] bytes) {
        return ((long) bytes[7] << 56) + (((long) bytes[6] & 0xFF) << 48) + (((long) bytes[5] & 0xFF) << 40) + (((long) bytes[4] & 0xFF) << 32)
                + (((long) bytes[3] & 0xFF) << 24) + (((long) bytes[2] & 0xFF) << 16) + (((long) bytes[1] & 0xFF) << 8) + ((long) bytes[0] & 0xFF);
    }

    public static MessageKeyData generateMessageKeyData(byte[] authKey, byte[] messageKey, boolean incoming) {
        MessageKeyData keyData = new MessageKeyData();
        if (authKey == null || authKey.length == 0) {
            keyData.aesIv = null;
            keyData.aesKey = null;
            return keyData;
        }

        int x = incoming ? 8 : 0;

        SerializedData data = new SerializedData();
        data.writeRaw(messageKey);
        data.writeRaw(authKey, x, 32);
        byte[] sha1_a = Utilities.computeSHA1(data.toByteArray());
        data.cleanup();

        data = new SerializedData();
        data.writeRaw(authKey, 32 + x, 16);
        data.writeRaw(messageKey);
        data.writeRaw(authKey, 48 + x, 16);
        byte[] sha1_b = Utilities.computeSHA1(data.toByteArray());
        data.cleanup();

        data = new SerializedData();
        data.writeRaw(authKey, 64 + x, 32);
        data.writeRaw(messageKey);
        byte[] sha1_c = Utilities.computeSHA1(data.toByteArray());
        data.cleanup();

        data = new SerializedData();
        data.writeRaw(messageKey);
        data.writeRaw(authKey, 96 + x, 32);
        byte[] sha1_d = Utilities.computeSHA1(data.toByteArray());
        data.cleanup();

        data = new SerializedData();
        data.writeRaw(sha1_a, 0, 8);
        data.writeRaw(sha1_b, 8, 12);
        data.writeRaw(sha1_c, 4, 12);
        keyData.aesKey = data.toByteArray();
        data.cleanup();

        data = new SerializedData();
        data.writeRaw(sha1_a, 8, 12);
        data.writeRaw(sha1_b, 0, 8);
        data.writeRaw(sha1_c, 16, 4);
        data.writeRaw(sha1_d, 0, 8);
        keyData.aesIv = data.toByteArray();
        data.cleanup();

        return keyData;
    }

    public static TLObject decompress(byte[] data, TLObject parentObject) {
        final int BUFFER_SIZE = 512;
        ByteArrayInputStream is = new ByteArrayInputStream(data);
        GZIPInputStream gis;
        try {
            gis = new GZIPInputStream(is, BUFFER_SIZE);
            ByteArrayOutputStream bytesOutput = new ByteArrayOutputStream();
            data = new byte[BUFFER_SIZE];
            int bytesRead;
            while ((bytesRead = gis.read(data)) != -1) {
                bytesOutput.write(data, 0, bytesRead);
            }
            try {
                gis.close();
            } catch (Exception e) {
                FileLog.e("tmessages", e);
            }
            try {
                is.close();
            } catch (Exception e) {
                FileLog.e("tmessages", e);
            }
            SerializedData stream = new SerializedData(bytesOutput.toByteArray());
            try {
                bytesOutput.close();
            } catch (Exception e) {
                FileLog.e("tmessages", e);
            }
            TLObject object = TLClassStore.Instance().TLdeserialize(stream, stream.readInt32(), parentObject);
            stream.cleanup();
            return object;
        } catch (IOException e) {
            FileLog.e("tmessages", e);
        }
        return null;
    }

    public static byte[] compress(byte[] data) {
        if (data == null) {
            return null;
        }

        byte[] packedData = null;
        ByteArrayOutputStream bytesStream = new ByteArrayOutputStream();
        try {
            GZIPOutputStream zip = new GZIPOutputStream(bytesStream);
            zip.write(data);
            zip.close();
            packedData = bytesStream.toByteArray();
        } catch (IOException e) {
            FileLog.e("tmessages", e);
        } finally {
            try {
                bytesStream.close();
            } catch (Exception e) {
                FileLog.e("tmessages", e);
            }
        }
        return packedData;
    }

    public static boolean copyFile(InputStream sourceFile, File destFile) throws IOException {
        OutputStream out = new FileOutputStream(destFile);
        byte[] buf = new byte[4096];
        int len;
        while ((len = sourceFile.read(buf)) > 0) {
            Thread.yield();
            out.write(buf, 0, len);
        }
        out.close();
        return true;
    }

    public static boolean copyFile(File sourceFile, File destFile) throws IOException {
        if (!destFile.exists()) {
            destFile.createNewFile();
        }
        FileInputStream source = null;
        FileOutputStream destination = null;
        try {
            source = new FileInputStream(sourceFile);
            destination = new FileOutputStream(destFile);
            destination.getChannel().transferFrom(source.getChannel(), 0, source.getChannel().size());
        } catch (Exception e) {
            FileLog.e("tmessages", e);
            return false;
        } finally {
            if (source != null) {
                source.close();
            }
            if (destination != null) {
                destination.close();
            }
        }
        return true;
    }

    public static String MD5(String md5) {
        if (md5 == null) {
            return null;
        }
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
            byte[] array = md.digest(md5.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte anArray : array) {
                sb.append(Integer.toHexString((anArray & 0xFF) | 0x100).substring(1, 3));
            }
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            FileLog.e("tmessages", e);
        }
        return null;
    }

    public static void addMediaToGallery(String fromPath) {
        if (fromPath == null) {
            return;
        }
        File f = new File(fromPath);
        Uri contentUri = Uri.fromFile(f);
        addMediaToGallery(contentUri);
    }

    public static void addMediaToGallery(Uri uri) {
        if (uri == null) {
            return;
        }
        Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
        mediaScanIntent.setData(uri);
        ApplicationLoader.applicationContext.sendBroadcast(mediaScanIntent);
    }

    private static File getAlbumDir() {
        File storageDir = null;
        if (Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())) {
            storageDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Telegram");
            if (storageDir != null) {
                if (!storageDir.mkdirs()) {
                    if (!storageDir.exists()){
                        FileLog.d("tmessages", "failed to create directory");
                        return null;
                    }
                }
            }
        } else {
            FileLog.d("tmessages", "External storage is not mounted READ/WRITE.");
        }

        return storageDir;
    }

    public static String getPath(final Uri uri) {
        try {
            final boolean isKitKat = Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT;
            if (isKitKat && DocumentsContract.isDocumentUri(ApplicationLoader.applicationContext, uri)) {
                if (isExternalStorageDocument(uri)) {
                    final String docId = DocumentsContract.getDocumentId(uri);
                    final String[] split = docId.split(":");
                    final String type = split[0];
                    if ("primary".equalsIgnoreCase(type)) {
                        return Environment.getExternalStorageDirectory() + "/" + split[1];
                    }
                } else if (isDownloadsDocument(uri)) {
                    final String id = DocumentsContract.getDocumentId(uri);
                    final Uri contentUri = ContentUris.withAppendedId(Uri.parse("content://downloads/public_downloads"), Long.valueOf(id));
                    return getDataColumn(ApplicationLoader.applicationContext, contentUri, null, null);
                } else if (isMediaDocument(uri)) {
                    final String docId = DocumentsContract.getDocumentId(uri);
                    final String[] split = docId.split(":");
                    final String type = split[0];

                    Uri contentUri = null;
                    switch (type) {
                        case "image":
                            contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                            break;
                        case "video":
                            contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
                            break;
                        case "audio":
                            contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
                            break;
                    }

                    final String selection = "_id=?";
                    final String[] selectionArgs = new String[] {
                            split[1]
                    };

                    return getDataColumn(ApplicationLoader.applicationContext, contentUri, selection, selectionArgs);
                }
            } else if ("content".equalsIgnoreCase(uri.getScheme())) {
                return getDataColumn(ApplicationLoader.applicationContext, uri, null, null);
            } else if ("file".equalsIgnoreCase(uri.getScheme())) {
                return uri.getPath();
            }
        } catch (Exception e) {
            FileLog.e("tmessages", e);
        }
        return null;
    }

    public static String getDataColumn(Context context, Uri uri, String selection, String[] selectionArgs) {

        Cursor cursor = null;
        final String column = "_data";
        final String[] projection = {
                column
        };

        try {
            cursor = context.getContentResolver().query(uri, projection, selection, selectionArgs, null);
            if (cursor != null && cursor.moveToFirst()) {
                final int column_index = cursor.getColumnIndexOrThrow(column);
                return cursor.getString(column_index);
            }
        } catch (Exception e) {
            FileLog.e("tmessages", e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return null;
    }

    public static boolean isExternalStorageDocument(Uri uri) {
        return "com.android.externalstorage.documents".equals(uri.getAuthority());
    }

    public static boolean isDownloadsDocument(Uri uri) {
        return "com.android.providers.downloads.documents".equals(uri.getAuthority());
    }

    public static boolean isMediaDocument(Uri uri) {
        return "com.android.providers.media.documents".equals(uri.getAuthority());
    }

    public static File generatePicturePath() {
        try {
            File storageDir = getAlbumDir();
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
            return new File(storageDir, "IMG_" + timeStamp + ".jpg");
        } catch (Exception e) {
            FileLog.e("tmessages", e);
        }
        return null;
    }

    public static CharSequence generateSearchName(String name, String name2, String q) {
        if (name == null && name2 == null) {
            return "";
        }
        SpannableStringBuilder builder = new SpannableStringBuilder();
        String wholeString = name;
        if (wholeString == null || wholeString.length() == 0) {
            wholeString = name2;
        } else if (name2 != null && name2.length() != 0) {
            wholeString += " " + name2;
        }
        wholeString = wholeString.trim();
        String lower = " " + wholeString.toLowerCase();

        int index = -1;
        int lastIndex = 0;
        while ((index = lower.indexOf(" " + q, lastIndex)) != -1) {
            int idx = index - (index == 0 ? 0 : 1);
            int end = q.length() + (index == 0 ? 0 : 1) + idx;

            if (lastIndex != 0 && lastIndex != idx + 1) {
                builder.append(wholeString.substring(lastIndex, idx));
            } else if (lastIndex == 0 && idx != 0) {
                builder.append(wholeString.substring(0, idx));
            }

            String query = wholeString.substring(idx, end);
            if (query.startsWith(" ")) {
                builder.append(" ");
            }
            query.trim();
            builder.append(AndroidUtilities.replaceTags("<c#ff4d83b3>" + query + "</c>"));

            lastIndex = end;
        }

        if (lastIndex != -1 && lastIndex != wholeString.length()) {
            builder.append(wholeString.substring(lastIndex, wholeString.length()));
        }

        return builder;
    }

    public static File generateVideoPath() {
        try {
            File storageDir = getAlbumDir();
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
            return new File(storageDir, "VID_" + timeStamp + ".mp4");
        } catch (Exception e) {
            FileLog.e("tmessages", e);
        }
        return null;
    }

    public static String formatFileSize(long size) {
        if (size < 1024) {
            return String.format("%d B", size);
        } else if (size < 1024 * 1024) {
            return String.format("%.1f KB", size / 1024.0f);
        } else if (size < 1024 * 1024 * 1024) {
            return String.format("%.1f MB", size / 1024.0f / 1024.0f);
        } else {
            return String.format("%.1f GB", size / 1024.0f / 1024.0f / 1024.0f);
        }
    }

    public static byte[] decodeQuotedPrintable(final byte[] bytes) {
        if (bytes == null) {
            return null;
        }
        final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        for (int i = 0; i < bytes.length; i++) {
            final int b = bytes[i];
            if (b == '=') {
                try {
                    final int u = Character.digit((char) bytes[++i], 16);
                    final int l = Character.digit((char) bytes[++i], 16);
                    buffer.write((char) ((u << 4) + l));
                } catch (Exception e) {
                    FileLog.e("tmessages", e);
                    return null;
                }
            } else {
                buffer.write(b);
            }
        }
        byte[] array = buffer.toByteArray();
        try {
            buffer.close();
        } catch (Exception e) {
            FileLog.e("tmessages", e);
        }
        return array;
    }

    public static void checkForCrashes(Activity context) {
        CrashManager.register(context, BuildVars.HOCKEY_APP_HASH, new CrashManagerListener() {
            @Override
            public boolean includeDeviceData() {
                return true;
            }
        });
    }

    public static void checkForUpdates(Activity context) {
        if (BuildVars.DEBUG_VERSION) {
            UpdateManager.register(context, BuildVars.HOCKEY_APP_HASH);
        }
    }
}
