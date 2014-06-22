/*
 * This is the source code of Telegram for Android v. 1.3.2.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013.
 */

package org.telegram.messenger;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.Point;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.text.Html;
import android.text.SpannableStringBuilder;
import android.util.Base64;
import android.view.Display;
import android.view.Surface;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;

import net.hockeyapp.android.CrashManager;
import net.hockeyapp.android.CrashManagerListener;
import net.hockeyapp.android.UpdateManager;

import org.telegram.ui.ApplicationLoader;

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
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.RSAPublicKeySpec;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Hashtable;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import javax.crypto.Cipher;

public class Utilities {
    public static int statusBarHeight = 0;
    public static float density = 1;
    public static Point displaySize = new Point();
    public static Pattern pattern = Pattern.compile("[0-9]+");
    public static SecureRandom random = new SecureRandom();
    private final static Integer lock = 1;
    private static int prevOrientation = -10;

    private static boolean waitingForSms = false;
    private static final Integer smsLock = 2;

    public static ArrayList<String> goodPrimes = new ArrayList<String>();

    public static class TPFactorizedValue {
        public long p, q;
    }

    public static volatile DispatchQueue stageQueue = new DispatchQueue("stageQueue");
    public static volatile DispatchQueue globalQueue = new DispatchQueue("globalQueue");
    public static volatile DispatchQueue searchQueue = new DispatchQueue("searchQueue");
    public static volatile DispatchQueue photoBookQueue = new DispatchQueue("photoBookQueue");

    public static int[] arrColors = {0xffee4928, 0xff41a903, 0xffe09602, 0xff0f94ed, 0xff8f3bf7, 0xfffc4380, 0xff00a1c4, 0xffeb7002};
    public static int[] arrUsersAvatars = {
            R.drawable.user_red,
            R.drawable.user_green,
            R.drawable.user_yellow,
            R.drawable.user_blue,
            R.drawable.user_violet,
            R.drawable.user_pink,
            R.drawable.user_aqua,
            R.drawable.user_orange};

    public static int[] arrGroupsAvatars = {
            R.drawable.group_green,
            R.drawable.group_red,
            R.drawable.group_blue,
            R.drawable.group_yellow};

    public static int externalCacheNotAvailableState = 0;

    final protected static char[] hexArray = "0123456789ABCDEF".toCharArray();

    private static final Hashtable<String, Typeface> cache = new Hashtable<String, Typeface>();

    public static ProgressDialog progressDialog;

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

        density = ApplicationLoader.applicationContext.getResources().getDisplayMetrics().density;
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
                }
            } catch (Exception e) {
                FileLog.e("tmessages", e);
                goodPrimes.clear();
                goodPrimes.add("C71CAEB9C6B1C9048E6C522F70F13F73980D40238E3E21C14934D037563D930F48198A0AA7C14058229493D22530F4DBFA336F6E0AC925139543AED44CCE7C3720FD51F69458705AC68CD4FE6B6B13ABDC9746512969328454F18FAF8C595F642477FE96BB2A941D5BCD1D4AC8CC49880708FA9B378E3C4F3A9060BEE67CF9A4A4A695811051907E162753B56B0F6B410DBA74D8A84B2A14B3144E0EF1284754FD17ED950D5965B4B9DD46582DB1178D169C6BC465B0D6FF9CA3928FEF5B9AE4E418FC15E83EBEA0F87FA9FF5EED70050DED2849F47BF959D956850CE929851F0D8115F635B105EE2E4E15D04B2454BF6F4FADF034B10403119CD8E3B92FCC5B");
            }
        }

        checkDisplaySize();
    }

    public native static long doPQNative(long _what);
    public native static byte[] aesIgeEncryption(byte[] _what, byte[] _key, byte[] _iv, boolean encrypt, boolean changeIv, int len);
    public native static void aesIgeEncryption2(ByteBuffer _what, byte[] _key, byte[] _iv, boolean encrypt, boolean changeIv, int len);
    public native static void loadBitmap(String path, int[] bitmap, int scale, int format, int width, int height);

    public static void lockOrientation(Activity activity) {
        if (prevOrientation != -10) {
            return;
        }
        try {
            prevOrientation = activity.getRequestedOrientation();
            WindowManager manager = (WindowManager)activity.getSystemService(Activity.WINDOW_SERVICE);
            if (manager != null && manager.getDefaultDisplay() != null) {
                int rotation = manager.getDefaultDisplay().getRotation();
                int orientation = activity.getResources().getConfiguration().orientation;

                if (rotation == Surface.ROTATION_270) {
                    if (orientation == Configuration.ORIENTATION_PORTRAIT) {
                        activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                    } else {
                        if (Build.VERSION.SDK_INT >= 9) {
                            activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE);
                        } else {
                            activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
                        }
                    }
                } else if (rotation == Surface.ROTATION_90) {
                    if (orientation == Configuration.ORIENTATION_PORTRAIT) {
                        if (Build.VERSION.SDK_INT >= 9) {
                            activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT);
                        } else {
                            activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                        }
                    } else {
                        activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
                    }
                } else if (rotation == Surface.ROTATION_0) {
                    if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                        activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
                    } else {
                        activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                    }
                } else {
                    if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                        if (Build.VERSION.SDK_INT >= 9) {
                            activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE);
                        } else {
                            activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
                        }
                    } else {
                        if (Build.VERSION.SDK_INT >= 9) {
                            activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT);
                        } else {
                            activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                        }
                    }
                }
            }
        } catch (Exception e) {
            FileLog.e("tmessages", e);
        }
    }

    public static void unlockOrientation(Activity activity) {
        try {
            if (prevOrientation != -10) {
                activity.setRequestedOrientation(prevOrientation);
                prevOrientation = -10;
            }
        } catch (Exception e) {
            FileLog.e("tmessages", e);
        }
    }

    public static boolean isWaitingForSms() {
        boolean value = false;
        synchronized (smsLock) {
            value = waitingForSms;
        }
        return value;
    }

    public static void setWaitingForSms(boolean value) {
        synchronized (smsLock) {
            waitingForSms = value;
        }
    }

    public static Integer parseInt(String value) {
        Integer val = 0;
        Matcher matcher = pattern.matcher(value);
        if (matcher.find()) {
            String num = matcher.group(0);
            val = Integer.parseInt(num);
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

    public static File getCacheDir() {
        if (externalCacheNotAvailableState == 1 || externalCacheNotAvailableState == 0 && Environment.getExternalStorageState().startsWith(Environment.MEDIA_MOUNTED)) {
            externalCacheNotAvailableState = 1;
            File file = ApplicationLoader.applicationContext.getExternalCacheDir();
            if (file != null) {
                return file;
            }
        }
        externalCacheNotAvailableState = 2;
        File file = ApplicationLoader.applicationContext.getCacheDir();
        if (file != null) {
            return file;
        }
        return new File("");
    }

    public static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        int v;
        for ( int j = 0; j < bytes.length; j++ ) {
            v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }

    public static int dp(int value) {
        return (int)(Math.max(1, density * value));
    }

    public static int dpf(float value) {
        return (int)Math.ceil(density * value);
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
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            int oldp = convertme.position();
            int oldl = convertme.limit();
            convertme.position(offset);
            convertme.limit(len);
            md.update(convertme);
            convertme.position(oldp);
            convertme.limit(oldl);
            return md.digest();
        } catch (Exception e) {
            FileLog.e("tmessages", e);
        }
        return null;
    }

    public static byte[] computeSHA1(byte[] convertme) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            return md.digest(convertme);
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

    public static byte[] longToBytes(long x) {
        ByteBuffer buffer = ByteBuffer.allocate(8);
        buffer.putLong(x);
        return buffer.array();
    }

    public static long bytesToLong(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.allocate(8);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        buffer.put(bytes);
        buffer.flip();
        return buffer.getLong();
    }

    public static int bytesToInt(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.allocate(4);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        buffer.put(bytes);
        buffer.flip();
        return buffer.getInt();
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

        data = new SerializedData();
        data.writeRaw(authKey, 32 + x, 16);
        data.writeRaw(messageKey);
        data.writeRaw(authKey, 48 + x, 16);
        byte[] sha1_b = Utilities.computeSHA1(data.toByteArray());

        data = new SerializedData();
        data.writeRaw(authKey, 64 + x, 32);
        data.writeRaw(messageKey);
        byte[] sha1_c = Utilities.computeSHA1(data.toByteArray());

        data = new SerializedData();
        data.writeRaw(messageKey);
        data.writeRaw(authKey, 96 + x, 32);
        byte[] sha1_d = Utilities.computeSHA1(data.toByteArray());

        SerializedData aesKey = new SerializedData();
        aesKey.writeRaw(sha1_a, 0, 8);
        aesKey.writeRaw(sha1_b, 8, 12);
        aesKey.writeRaw(sha1_c, 4, 12);
        keyData.aesKey = aesKey.toByteArray();

        SerializedData aesIv = new SerializedData();
        aesIv.writeRaw(sha1_a, 8, 12);
        aesIv.writeRaw(sha1_b, 0, 8);
        aesIv.writeRaw(sha1_c, 16, 4);
        aesIv.writeRaw(sha1_d, 0, 8);
        keyData.aesIv = aesIv.toByteArray();

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
            gis.close();
            is.close();
            SerializedData stream = new SerializedData(bytesOutput.toByteArray());
            return TLClassStore.Instance().TLdeserialize(stream, stream.readInt32(), parentObject);
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
        }
        return packedData;
    }

    public static Typeface getTypeface(String assetPath) {
        synchronized (cache) {
            if (!cache.containsKey(assetPath)) {
                try {
                    Typeface t = Typeface.createFromAsset(ApplicationLoader.applicationContext.getAssets(),
                            assetPath);
                    cache.put(assetPath, t);
                } catch (Exception e) {
                    FileLog.e("Typefaces", "Could not get typeface '" + assetPath + "' because " + e.getMessage());
                    return null;
                }
            }
            return cache.get(assetPath);
        }
    }

    public static void showKeyboard(View view) {
        if (view == null) {
            return;
        }
        InputMethodManager inputManager = (InputMethodManager)view.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        inputManager.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT);

        ((InputMethodManager) view.getContext().getSystemService(Context.INPUT_METHOD_SERVICE)).showSoftInput(view, 0);
    }

    public static boolean isKeyboardShowed(View view) {
        if (view == null) {
            return false;
        }
        InputMethodManager inputManager = (InputMethodManager) view.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        return inputManager.isActive(view);
    }

    public static void hideKeyboard(View view) {
        if (view == null) {
            return;
        }
        InputMethodManager imm = (InputMethodManager) view.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (!imm.isActive()) {
            return;
        }
        imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }

    public static void ShowProgressDialog(final Activity activity, final String message) {
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if(!activity.isFinishing()) {
                    progressDialog = new ProgressDialog(activity);
                    if (message != null) {
                        progressDialog.setMessage(message);
                    }
                    progressDialog.setCanceledOnTouchOutside(false);
                    progressDialog.setCancelable(false);
                    progressDialog.show();
                }
            }
        });
    }

    public static void checkDisplaySize() {
        try {
            WindowManager manager = (WindowManager)ApplicationLoader.applicationContext.getSystemService(Context.WINDOW_SERVICE);
            if (manager != null) {
                Display display = manager.getDefaultDisplay();
                if (display != null) {
                    if(android.os.Build.VERSION.SDK_INT < 13) {
                        displaySize.set(display.getWidth(), display.getHeight());
                    } else {
                        display.getSize(displaySize);
                    }
                    FileLog.e("tmessages", "display size = " + displaySize.x + " " + displaySize.y);
                }
            }
        } catch (Exception e) {
            FileLog.e("tmessages", e);
        }
    }

    public static void HideProgressDialog(Activity activity) {
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (progressDialog != null) {
                    progressDialog.dismiss();
                }
            }
        });
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
        if(!destFile.exists()) {
            destFile.createNewFile();
        }
        FileChannel source = null;
        FileChannel destination = null;
        try {
            source = new FileInputStream(sourceFile).getChannel();
            destination = new FileOutputStream(destFile).getChannel();
            destination.transferFrom(source, 0, source.size());
        } catch (Exception e) {
            FileLog.e("tmessages", e);
            return false;
        } finally {
            if(source != null) {
                source.close();
            }
            if(destination != null) {
                destination.close();
            }
        }
        return true;
    }

    public static void RunOnUIThread(Runnable runnable) {
        synchronized (lock) {
            ApplicationLoader.applicationHandler.post(runnable);
        }
    }

    public static int getColorIndex(int id) {
        int[] arr;
        if (id >= 0) {
            arr = arrUsersAvatars;
        } else {
            arr = arrGroupsAvatars;
        }
        try {
            String str;
            if (id >= 0) {
                str = String.format(Locale.US, "%d%d", id, UserConfig.getClientUserId());
            } else {
                str = String.format(Locale.US, "%d", id);
            }
            if (str.length() > 15) {
                str = str.substring(0, 15);
            }
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(str.getBytes());
            int b = digest[Math.abs(id % 16)];
            if (b < 0) {
                b += 256;
            }
            return Math.abs(b) % arr.length;
        } catch (Exception e) {
            FileLog.e("tmessages", e);
        }
        return id % arr.length;
    }

    public static int getColorForId(int id) {
        if (id / 1000 == 333) {
            return 0xff0f94ed;
        }
        return arrColors[getColorIndex(id)];
    }

    public static int getUserAvatarForId(int id) {
        if (id / 1000 == 333) {
            return R.drawable.telegram_avatar;
        }
        return arrUsersAvatars[getColorIndex(id)];
    }

    public static int getGroupAvatarForId(int id) {
        return arrGroupsAvatars[getColorIndex(-id)];
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
            storageDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), LocaleController.getString("AppName", R.string.AppName));
            if (storageDir != null) {
                if (! storageDir.mkdirs()) {
                    if (! storageDir.exists()){
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
                    if ("image".equals(type)) {
                        contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                    } else if ("video".equals(type)) {
                        contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
                    } else if ("audio".equals(type)) {
                        contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
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
            String imageFileName = "IMG_" + timeStamp + "_";
            return File.createTempFile(imageFileName, ".jpg", storageDir);
        } catch (Exception e) {
            FileLog.e("tmessages", e);
        }
        return null;
    }

    public static CharSequence generateSearchName(String name, String name2, String q) {
        if (name == null && name2 == null) {
            return "";
        }
        int index;
        SpannableStringBuilder builder = new SpannableStringBuilder();
        String wholeString = name;
        if (wholeString == null || wholeString.length() == 0) {
            wholeString = name2;
        } else if (name2 != null && name2.length() != 0) {
            wholeString += " " + name2;
        }
        wholeString = wholeString.trim();
        String[] args = wholeString.split(" ");

        for (String arg : args) {
            String str = arg;
            if (str != null) {
                String lower = str.toLowerCase();
                if (lower.startsWith(q)) {
                    if (builder.length() != 0) {
                        builder.append(" ");
                    }
                    String query = str.substring(0, q.length());
                    builder.append(Html.fromHtml("<font color=\"#357aa8\">" + query + "</font>"));
                    str = str.substring(q.length());
                    builder.append(str);
                } else {
                    if (builder.length() != 0) {
                        builder.append(" ");
                    }
                    builder.append(str);
                }
            }
        }
        return builder;
    }

    public static File generateVideoPath() {
        try {
            File storageDir = getAlbumDir();
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
            String imageFileName = "VID_" + timeStamp + "_";
            return File.createTempFile(imageFileName, ".mp4", storageDir);
        } catch (Exception e) {
            FileLog.e("tmessages", e);
        }
        return null;
    }

    public static String formatName(String firstName, String lastName) {
        String result = firstName;
        if (result == null || result.length() == 0) {
            result = lastName;
        } else if (result.length() != 0 && lastName.length() != 0) {
            result += " " + lastName;
        }
        return result.trim();
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
        return buffer.toByteArray();
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

    public static boolean isTablet(Context context) {
        return (context.getResources().getConfiguration().screenLayout & Configuration.SCREENLAYOUT_SIZE_MASK) >= Configuration.SCREENLAYOUT_SIZE_LARGE;
    }
}
