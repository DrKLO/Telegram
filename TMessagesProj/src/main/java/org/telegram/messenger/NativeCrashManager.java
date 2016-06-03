package org.telegram.messenger;

import android.app.Activity;
import android.net.Uri;
import android.util.Log;

import net.hockeyapp.android.Constants;
import net.hockeyapp.android.utils.SimpleMultipartEntity;

import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Date;
import java.util.UUID;

public class NativeCrashManager {

    public static void handleDumpFiles(Activity activity) {
        String[] filenames = searchForDumpFiles();
        for (String dumpFilename : filenames) {
            String logFilename = createLogFile();
            if (logFilename != null) {
                uploadDumpAndLog(activity, BuildVars.DEBUG_VERSION ? BuildVars.HOCKEY_APP_HASH_DEBUG : BuildVars.HOCKEY_APP_HASH, dumpFilename, logFilename);
            }
        }
    }

    public static String createLogFile() {
        final Date now = new Date();

        try {
            String filename = UUID.randomUUID().toString();
            String path = Constants.FILES_PATH + "/" + filename + ".faketrace";
            BufferedWriter write = new BufferedWriter(new FileWriter(path));
            write.write("Package: " + Constants.APP_PACKAGE + "\n");
            write.write("Version Code: " + Constants.APP_VERSION + "\n");
            write.write("Version Name: " + Constants.APP_VERSION_NAME + "\n");
            write.write("Android: " + Constants.ANDROID_VERSION + "\n");
            write.write("Manufacturer: " + Constants.PHONE_MANUFACTURER + "\n");
            write.write("Model: " + Constants.PHONE_MODEL + "\n");
            write.write("Date: " + now + "\n");
            write.write("\n");
            write.write("MinidumpContainer");
            write.flush();
            write.close();
            return filename + ".faketrace";
        } catch (Exception e) {
            FileLog.e("tmessages", e);
        }

        return null;
    }

    public static void uploadDumpAndLog(final Activity activity, final String identifier, final String dumpFilename, final String logFilename) {
        new Thread() {
            @Override
            public void run() {
                try {
                    SimpleMultipartEntity entity = new SimpleMultipartEntity();
                    entity.writeFirstBoundaryIfNeeds();

                    Uri attachmentUri = Uri.fromFile(new File(Constants.FILES_PATH, dumpFilename));
                    InputStream input = activity.getContentResolver().openInputStream(attachmentUri);
                    entity.addPart("attachment0", attachmentUri.getLastPathSegment(), input, false);

                    attachmentUri = Uri.fromFile(new File(Constants.FILES_PATH, logFilename));
                    input = activity.getContentResolver().openInputStream(attachmentUri);
                    entity.addPart("log", attachmentUri.getLastPathSegment(), input, true);

                    entity.writeLastBoundaryIfNeeds();

                    HttpURLConnection urlConnection = (HttpURLConnection) new URL("https://rink.hockeyapp.net/api/2/apps/" + identifier + "/crashes/upload").openConnection();
                    urlConnection.setDoOutput(true);
                    urlConnection.setRequestMethod("POST");
                    urlConnection.setRequestProperty("Content-Type", entity.getContentType());
                    urlConnection.setRequestProperty("Content-Length", String.valueOf(entity.getContentLength()));

                    BufferedOutputStream outputStream = new BufferedOutputStream(urlConnection.getOutputStream());
                    outputStream.write(entity.getOutputStream().toByteArray());
                    outputStream.flush();
                    outputStream.close();

                    urlConnection.connect();

                    FileLog.e("tmessages", "response code = " + urlConnection.getResponseCode() + " message = " + urlConnection.getResponseMessage());
                } catch (IOException e) {
                    e.printStackTrace();
                } finally {
                    activity.deleteFile(logFilename);
                    activity.deleteFile(dumpFilename);
                }
            }
        }.start();
    }

    private static String[] searchForDumpFiles() {
        if (Constants.FILES_PATH != null) {
            File dir = new File(Constants.FILES_PATH + "/");
            boolean created = dir.mkdir();
            if (!created && !dir.exists()) {
                return new String[0];
            }
            FilenameFilter filter = new FilenameFilter() {
                public boolean accept(File dir, String name) {
                    return name.endsWith(".dmp");
                }
            };
            return dir.list(filter);
        } else {
            return new String[0];
        }
    }
}
