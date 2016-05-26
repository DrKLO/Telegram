package org.telegram.messenger;

import android.app.Activity;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;

import java.io.File;

public class ModuleContentProvider extends ContentProvider {
    private static final String TAG = "ModuleContentProvider";

    private static final String AUTHORITY = "org.telegram.plus.android.provider.content";
    private static final String AUTHORITY_BETA = "org.telegram.plus.beta.android.provider.content";

    public static Uri THEME_URI = Uri.parse("content://" + AUTHORITY + "/theme");
    public static Uri GET_NAME = Uri.parse("content://" + AUTHORITY + "/name");
    public static Uri SET_NAME = Uri.parse("content://" + AUTHORITY + "/newname");

    /*private static final UriMatcher sUriMatcher;
    static {
        sUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
        sUriMatcher.addURI(AUTHORITY, "theme", 1);
        sUriMatcher.addURI(AUTHORITY, "name", 2);
    }*/

    @Override
    public boolean onCreate() {
        //Log.d(TAG, "onCreate");
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String orderBy) {
        Log.d(TAG, "query with uri: " + uri.toString());
        return null;
    }

    @Override
    public String getType(Uri uri) {
        if(BuildConfig.DEBUG)GET_NAME = Uri.parse("content://" + AUTHORITY_BETA + "/name");
        if(uri.equals(GET_NAME)){
            SharedPreferences themePrefs = ApplicationLoader.applicationContext.getSharedPreferences("theme", Activity.MODE_PRIVATE);
            return themePrefs.getString("themeName","empty");
        }else{
            throw new IllegalArgumentException("Unknown URI " + uri);
        }
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        Log.d(TAG, "insert uri: " + uri.toString());
        if(BuildConfig.DEBUG)SET_NAME = Uri.parse("content://" + AUTHORITY_BETA + "/newname");
        if(uri.toString().contains(SET_NAME.toString())){
            /*String sName = uri.toString();
            sName = sName.substring(sName.lastIndexOf(":")+1, sName.length());
            SharedPreferences themePrefs = ApplicationLoader.applicationContext.getSharedPreferences(AndroidUtilities.THEME_PREFS, AndroidUtilities.THEME_PREFS_MODE);
            SharedPreferences.Editor editor = themePrefs.edit();
            editor.putString("themeName", sName);
            editor.commit();*/

            AndroidUtilities.themeUpdated = true;
        }else{
            throw new UnsupportedOperationException();
        }
        return uri;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        //Log.d(TAG, "update uri: " + uri.toString());
        if(BuildConfig.DEBUG)THEME_URI = Uri.parse("content://" + AUTHORITY_BETA + "/theme");
        if(uri.toString().contains(THEME_URI.toString())){
            String theme = uri.toString();
            theme = theme.substring(theme.lastIndexOf(":")+1, theme.length());
            //Log.d(TAG, theme);
            if (Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())) {
                File themeFile = new File(theme);
                if(themeFile.exists()){
                    applyTheme(theme);
                    return 10;
                }
                return 20;//theme doesn't exists
            }
            return 30;// MEDIA no mounted
        }else{
            throw new UnsupportedOperationException();
        }
    }

    @Override
    public int delete(Uri uri, String where, String[] selectionArgs) {
        //Log.d(TAG, "delete uri: " + uri.toString());
        throw new UnsupportedOperationException();
    }

    private void applyTheme(final String xmlFile){
        String sName = xmlFile.substring(0, xmlFile.lastIndexOf("."));
        String wName = sName + "_wallpaper.jpg";
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
        }
        if(Utilities.loadPrefFromSD(ApplicationLoader.applicationContext, xmlFile) == 4){
            //Utilities.loadWallpaperFromSDPath(ApplicationLoader.applicationContext, wName);
            Utilities.applyWallpaper(wName);
            AndroidUtilities.needRestart = true;
        }
    }
}
