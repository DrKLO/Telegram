/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2016.
 */

package org.telegram.messenger;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Point;
import android.os.Environment;
import android.util.Log;
import android.util.Xml;
import android.widget.Toast;

import org.telegram.ui.LaunchActivity;
import org.xmlpull.v1.XmlPullParser;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Utilities {

    public static Pattern pattern = Pattern.compile("[0-9]+");
    public static SecureRandom random = new SecureRandom();

    public static volatile DispatchQueue stageQueue = new DispatchQueue("stageQueue");
    public static volatile DispatchQueue globalQueue = new DispatchQueue("globalQueue");
    public static volatile DispatchQueue searchQueue = new DispatchQueue("searchQueue");
    public static volatile DispatchQueue phoneBookQueue = new DispatchQueue("photoBookQueue");

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
    }

    public native static void loadBitmap(String path, Bitmap bitmap, int scale, int width, int height, int stride);
    public native static int pinBitmap(Bitmap bitmap);
    public native static void unpinBitmap(Bitmap bitmap);
    public native static void blurBitmap(Object bitmap, int radius, int unpin, int width, int height, int stride);
    public native static void calcCDT(ByteBuffer hsvBuffer, int width, int height, ByteBuffer buffer);
    public native static boolean loadWebpImage(Bitmap bitmap, ByteBuffer buffer, int len, BitmapFactory.Options options, boolean unpin);
    public native static int convertVideoFrame(ByteBuffer src, ByteBuffer dest, int destFormat, int width, int height, int padding, int swap);
    private native static void aesIgeEncryption(ByteBuffer buffer, byte[] key, byte[] iv, boolean encrypt, int offset, int length);
    public native static String readlink(String path);

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

    public static Long parseLong(String value) {
        if (value == null) {
            return 0L;
        }
        Long val = 0L;
        try {
            Matcher matcher = pattern.matcher(value);
            if (matcher.find()) {
                String num = matcher.group(0);
                val = Long.parseLong(num);
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
        if (hex.equals("C71CAEB9C6B1C9048E6C522F70F13F73980D40238E3E21C14934D037563D930F48198A0AA7C14058229493D22530F4DBFA336F6E0AC925139543AED44CCE7C3720FD51F69458705AC68CD4FE6B6B13ABDC9746512969328454F18FAF8C595F642477FE96BB2A941D5BCD1D4AC8CC49880708FA9B378E3C4F3A9060BEE67CF9A4A4A695811051907E162753B56B0F6B410DBA74D8A84B2A14B3144E0EF1284754FD17ED950D5965B4B9DD46582DB1178D169C6BC465B0D6FF9CA3928FEF5B9AE4E418FC15E83EBEA0F87FA9FF5EED70050DED2849F47BF959D956850CE929851F0D8115F635B105EE2E4E15D04B2454BF6F4FADF034B10403119CD8E3B92FCC5B")) {
            return true;
        }

        BigInteger dhBI2 = dhBI.subtract(BigInteger.valueOf(1)).divide(BigInteger.valueOf(2));
        return !(!dhBI.isProbablePrime(30) || !dhBI2.isProbablePrime(30));
    }

    public static boolean isGoodGaAndGb(BigInteger g_a, BigInteger p) {
        return !(g_a.compareTo(BigInteger.valueOf(1)) != 1 || g_a.compareTo(p.subtract(BigInteger.valueOf(1))) != -1);
    }

    public static boolean arraysEquals(byte[] arr1, int offset1, byte[] arr2, int offset2) {
        if (arr1 == null || arr2 == null || offset1 < 0 || offset2 < 0 || arr1.length - offset1 != arr2.length - offset2 || arr1.length - offset1 < 0 || arr2.length - offset2 < 0) {
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
            FileLog.e("tmessages", e);
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
            FileLog.e("tmessages", e);
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

    public static long bytesToLong(byte[] bytes) {
        return ((long) bytes[7] << 56) + (((long) bytes[6] & 0xFF) << 48) + (((long) bytes[5] & 0xFF) << 40) + (((long) bytes[4] & 0xFF) << 32)
                + (((long) bytes[3] & 0xFF) << 24) + (((long) bytes[2] & 0xFF) << 16) + (((long) bytes[1] & 0xFF) << 8) + ((long) bytes[0] & 0xFF);
    }

    public static String MD5(String md5) {
        if (md5 == null) {
            return null;
        }
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
            byte[] array = md.digest(md5.getBytes());
            StringBuilder sb = new StringBuilder();
            for (int a = 0; a < array.length; a++) {
                sb.append(Integer.toHexString((array[a] & 0xFF) | 0x100).substring(1, 3));
            }
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            FileLog.e("tmessages", e);
        }
        return null;
    }


    //plus
    public static void restartApp(){
        Intent mRestartApp = new Intent(ApplicationLoader.applicationContext, LaunchActivity.class);
        int mPendingIntentId = 123456;
        PendingIntent mPendingIntent = PendingIntent.getActivity(ApplicationLoader.applicationContext, mPendingIntentId, mRestartApp, PendingIntent.FLAG_CANCEL_CURRENT);
        AlarmManager mgr = (AlarmManager)ApplicationLoader.applicationContext.getSystemService(Context.ALARM_SERVICE);
        mgr.set(AlarmManager.RTC, System.currentTimeMillis() + 1000, mPendingIntent);
        System.exit(0);
    }

    public static void savePreferencesToSD(Context context, String folder, String prefName, String tName, boolean toast){
        //String folder = "/Telegram/Themes";
        File dataF = new File (findPrefFolder(context),prefName);
        if(checkSDStatus() > 1){
            File f = new File (Environment.getExternalStorageDirectory(), folder);
            f.mkdirs();
            File sdF = new File(f, tName);
            String s = getError(copyFile(dataF,sdF,true));
            if (s.equalsIgnoreCase("4")) {
                if(toast && sdF.getName()!="")
                    Toast.makeText(context, context.getString(R.string.SavedTo, sdF.getName(), folder), Toast.LENGTH_SHORT).show();
            }else if (s.contains("0")) {
                s = context.getString(R.string.SaveErrorMsg0);
                Toast.makeText(context,"ERROR: "+ s ,Toast.LENGTH_LONG ).show();
            }else{
                Toast.makeText(context,"ERROR: "+s,Toast.LENGTH_LONG ).show();
                Toast.makeText(context,dataF.getAbsolutePath(),Toast.LENGTH_LONG ).show();
            }
        }else{
            Toast.makeText(context,"ERROR: " + context.getString(R.string.NoMediaMessage) , Toast.LENGTH_LONG ).show();
        }
    }

    public static void copyWallpaperToSD(Context context, String tName, boolean toast){
        String folder = "/Telegram/Themes";
        String nFile = "wallpaper.jpg";
        if(checkSDStatus()>0){
            File f1 = context.getFilesDir();
            f1 = new File (f1.getAbsolutePath(), nFile);
            File f2 = new File (Environment.getExternalStorageDirectory(), folder);
            f2.mkdirs();
            f2 = new File(f2, tName+"_"+nFile);
            if(f1.length()>1){
                String s = getError(copyFile(f1,f2,true));
                if(s.contains("4")){
                    if(toast && f2.getName()!="" && folder !="")Toast.makeText(context,context.getString(R.string.SavedTo,f2.getName(),folder),Toast.LENGTH_SHORT ).show();
                    if(f2.getName()=="" || folder =="") Toast.makeText(context,"ERROR: "+s,Toast.LENGTH_SHORT ).show();

                }else{
                    Toast.makeText(context,"ERROR: "+s+"\n"+f1.getAbsolutePath(),Toast.LENGTH_LONG ).show();
                }
            }
        }
    }

    static String findPrefFolder(Context context){
        File f = context.getFilesDir();
        String appDir = f.getAbsolutePath();
        File SPDir = new File (appDir.substring(0,appDir.lastIndexOf('/')+1)+ "shared_prefs/");
        if(!SPDir.exists()) {// && SPDir.isDirectory()) {
            String pck = context.getPackageName();
            SPDir=new File ("/dbdata/databases/"+pck+"/shared_prefs/");
        }
        //Log.i("TAG", SPDir.getAbsolutePath());
        return SPDir.getAbsolutePath();
    }

    static int checkSDStatus(){
        int b=0;
        String s = Environment.getExternalStorageState();
        if (s.equals(Environment.MEDIA_MOUNTED))b=2;
        else if (s.equals(Environment.MEDIA_MOUNTED_READ_ONLY))b=1;
        return b;
    }

    static String getError(int i){
        String s="-1";
        if(i==0)s="0: SOURCE FILE DOESN'T EXIST";
        if(i==1)s="1: DESTINATION FILE DOESN'T EXIST";
        if(i==2)s="2: NULL SOURCE & DESTINATION FILES";
        if(i==3)s="3: NULL SOURCE FILE";
        if(i==4)s="4";
        return s;
    }

    //0: source file doesn't exist
    //1: dest file doesn't exist
    //2: source & dest = NULL
    //3: source = NULL
    //4: dest = NULL
    static int copyFile(File sourceFile, File destFile, boolean save) {
        int i=-1;
        try{
            if (!sourceFile.exists()) {
                return i+1;
            }
            if (!destFile.exists()) {
                if(save)i=i+2;
                destFile.createNewFile();
            }
            FileChannel source;
            FileChannel destination;
            FileInputStream fileInputStream = new FileInputStream(sourceFile);
            source = fileInputStream.getChannel();
            FileOutputStream fileOutputStream = new FileOutputStream(destFile);
            destination = fileOutputStream.getChannel();
            if (destination != null && source != null) {
                destination.transferFrom(source, 0, source.size());
                i=2;
            }
            if (source != null) {
                source.close();
                i=3;
            }
            if (destination != null) {
                destination.close();
                i=4;
            }
            fileInputStream.close();
            fileOutputStream.close();
        }catch (Exception e)
        {
            System.err.println("Error saving preferences: " + e.getMessage());
            Log.e(e.getMessage(), e.toString());
        }
        return i;
    }
/*
    public static int loadWallpaperFromSDPath(Context context, String wPath){
        String nFile = "wallpaper.jpg";
        File f1 = context.getFilesDir();
        f1= new File (f1.getAbsolutePath(), nFile);
        File wFile = new File (wPath);
        String s = "-1";
        if (wFile.exists()){
            s = getError(copyFile(wFile,f1,false));
            if (!s.contains("4")) {
                Toast.makeText(context,"ERROR: "+s+"\n"+ context.getString(R.string.restoreErrorMsg, wFile.getAbsolutePath()) ,Toast.LENGTH_LONG ).show();
            }else{
                //Toast.makeText(context,wFile.getAbsolutePath(),Toast.LENGTH_LONG ).show();
            }
        }
        return Integer.parseInt(s);
    }*/

    public static void applyWallpaper(String wPath){
        String nFile = "wallpaper.jpg";
        File wFile = new File (wPath);
        if (wFile.exists()){
            FileOutputStream stream = null;
            try {
                Point screenSize = AndroidUtilities.getRealScreenSize();
                //Log.e("applyWallpaper",""+wPath+" x: "+screenSize.x+" y: "+screenSize.y);
                Bitmap bitmap = ImageLoader.loadBitmap(wPath, null, screenSize.x, screenSize.y, true);

                File toFile = new File(ApplicationLoader.applicationContext.getFilesDir(), nFile);
                stream = new FileOutputStream(toFile);
                bitmap.compress(Bitmap.CompressFormat.JPEG, 87, stream);
            } catch (Exception e) {
                FileLog.e("tmessages", e);
            } finally {
                try {
                    if (stream != null) {
                        stream.close();
                    }
                } catch (Exception e) {
                    FileLog.e("tmessages", e);
                }
            }
        }
    }

    public static int loadPrefFromSD(Context context, String prefPath){
        File dataF = new File (findPrefFolder(context), "theme.xml");
        File prefFile = new File (prefPath);
        String s = getError(copyFile(prefFile, dataF, false));
        if (!s.contains("4")) {
            Toast.makeText(context, "ERROR: "+s+"\n"+ context.getString(R.string.restoreErrorMsg, prefFile.getAbsolutePath()), Toast.LENGTH_LONG).show();
        }
        return Integer.parseInt(s);
    }

    public static int loadPrefFromSD(Context context, String prefPath, String name){
        File dataF = new File (findPrefFolder(context), name + ".xml");
        Log.e("Utilities","dataF " + dataF.getAbsolutePath());
        File prefFile = new File (prefPath);
        Log.e("Utilities","prefFile " + prefFile.getAbsolutePath());
        String s = getError(copyFile(prefFile, dataF, false));
        if (!s.contains("4")) {
            Toast.makeText(context, "ERROR: "+s+"\n"+ context.getString(R.string.restoreErrorMsg, prefFile.getAbsolutePath()), Toast.LENGTH_LONG).show();
        }
        return Integer.parseInt(s);
    }

    public static String applyThemeFile(File file) {
        try {
            HashMap<String, String> stringMap = getXmlFileStrings(file);
            String xmlFile = file.getAbsolutePath();
            String themeName = stringMap.get("themeName");

            if (themeName != null && themeName.length() > 0) {

                if (themeName.contains("&") || themeName.contains("|")) {
                    return "";
                }

                if(loadPrefFromSD(ApplicationLoader.applicationContext, xmlFile) != 4){
                    return "";
                }

                String wName = xmlFile.substring(0, xmlFile.lastIndexOf(".")) + "_wallpaper.jpg";
                File wFile = new File(wName);
                if(wFile.exists()){
                    //Change Stock Background to set Custom Wallpaper
                    SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
                    int selectedBackground = preferences.getInt("selectedBackground", 1000001);
                    if (selectedBackground == 1000001) {
                        //File toFile = new File(ApplicationLoader.applicationContext.getFilesDir(), "wallpaper.jpg");
                        //if (!toFile.exists()) {
                        SharedPreferences.Editor editor = preferences.edit();
                        editor.putInt("selectedBackground", 113);
                        editor.putInt("selectedColor", 0);
                        editor.commit();
                        //}
                    }
                    //loadWallpaperFromSDPath(ApplicationLoader.applicationContext, wName);
                    applyWallpaper(wName);
                }
                return themeName;
            }
        } catch (Exception e) {
            FileLog.e("tmessages", e);
        }
        return "";
    }

    private static HashMap<String, String> getXmlFileStrings(File file) {
        FileInputStream stream = null;
        try {
            HashMap<String, String> stringMap = new HashMap<>();
            XmlPullParser parser = Xml.newPullParser();
            stream = new FileInputStream(file);
            parser.setInput(stream, "UTF-8");
            int eventType = parser.getEventType();
            String name = null;
            String value = null;
            String attrName = null;
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if(eventType == XmlPullParser.START_TAG) {
                    name = parser.getName();
                    int c = parser.getAttributeCount();
                    if (c > 0) {
                        attrName = parser.getAttributeValue(0);
                    }
                } else if(eventType == XmlPullParser.TEXT) {
                    if (attrName != null) {
                        value = parser.getText();
                        if (value != null) {
                            value = value.trim();
                            value = value.replace("\\n", "\n");
                            value = value.replace("\\", "");
                        }
                    }
                } else if (eventType == XmlPullParser.END_TAG) {
                    value = null;
                    attrName = null;
                    name = null;
                }
                if (name != null && name.equals("string") && value != null && attrName != null && value.length() != 0 && attrName.length() != 0) {
                    stringMap.put(attrName, value);
                    name = null;
                    value = null;
                    attrName = null;
                }
                eventType = parser.next();
            }
            return stringMap;
        } catch (Exception e) {
            FileLog.e("tmessages", e);
        } finally {
            try {
                if (stream != null) {
                    stream.close();
                    stream = null;
                }
            } catch (Exception e) {
                FileLog.e("tmessages", e);
            }
        }
        return null;
    }
    //
}
