package org.telegram.ui.bots;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Environment;
import android.os.LocaleList;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.util.Pair;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.webkit.MimeTypeMap;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;

import com.google.android.exoplayer2.util.MimeTypes;

import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedFloat;
import org.telegram.ui.Components.AnimatedTextView;
import org.telegram.ui.Components.Bulletin;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.CircularProgressDrawable;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.LoadingSpan;
import org.telegram.ui.Components.RLottieDrawable;
import org.telegram.ui.GradientClip;
import org.telegram.ui.LaunchActivity;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

public class BotDownloads {

    private static final String PREF = "botdownloads_";

    public final Context context;
    public final int currentAccount;
    public final long botId;
    public final DownloadManager downloadManager;

    private final ArrayList<FileDownload> files = new ArrayList<>();
    private FileDownload currentFile;

    private final static HashMap<Pair<Integer, Long>, BotDownloads> instances = new HashMap<>();

    public static BotDownloads get(Context context, int currentAccount, long botId) {
        final Pair<Integer, Long> key = new Pair<>(currentAccount, botId);
        BotDownloads instance = instances.get(key);
        if (instance == null) {
            instances.put(key, instance = new BotDownloads(context, currentAccount, botId));
        }
        return instance;
    }

    private BotDownloads(Context context, int currentAccount, long botId) {
        this.context = context;
        this.currentAccount = currentAccount;
        this.botId = botId;
        this.downloadManager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);

        final SharedPreferences prefs = context.getSharedPreferences(PREF + currentAccount, Activity.MODE_PRIVATE);
        final Set<String> jsons = prefs.getStringSet("" + botId, null);
        if (jsons != null) {
            for (String json : jsons) {
                try {
                    final FileDownload file = new FileDownload(new JSONObject(json));
                    if (file.file != null && file.file.exists()) {
                        files.add(file);
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
        }
    }

    public FileDownload getCached(String url) {
        for (FileDownload file : files) {
            if (TextUtils.equals(file.url, url) && file.done)
                return file;
        }
        return null;
    }

    public FileDownload download(String url, String file_name) {
        final FileDownload cached = getCached(url);
        if (cached != null) {
            currentFile = cached;
            currentFile.resaved = true;
            postNotify();
            return cached;
        }

        final FileDownload file = new FileDownload(url, file_name);
        currentFile = file;
        file.shown = false;
        files.add(file);
        save();
        postNotify();

        return file;
    }

    public FileDownload getCurrent() {
        return currentFile;
    }

    public boolean isDownloading() {
        for (FileDownload file : files) {
            if (file.isDownloading())
                return true;
        }
        return false;
    }

    public boolean hasFiles() {
        return !files.isEmpty();
    }

    public ArrayList<FileDownload> getFiles() {
        return files;
    }

    public void cancel(FileDownload file) {
        if (file == null) return;
        file.cancelled = true;
        if (file.id != null) {
            downloadManager.remove(file.id);
            file.id = null;
        }
        files.remove(file);
        postNotify();
    }

    public void save() {
        final SharedPreferences prefs = context.getSharedPreferences(PREF + currentAccount, Activity.MODE_PRIVATE);
        final SharedPreferences.Editor edit = prefs.edit();
        edit.clear();
        final HashSet<String> set = new HashSet<>();
        for (FileDownload file : files) {
            set.add(file.toJSON().toString());
        }
        edit.putStringSet("" + botId, set);
        edit.apply();
    }

    private void postNotify() {
        NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.botDownloadsUpdate);
    }

    public class FileDownload {
        public Long id;

        public String url;
        public String file_name;
        public File file;
        public String mime;
        public long loaded_size;
        public long size;
        public boolean done;
        public boolean cancelled;

        public long last_progress_time;

        public boolean resaved;
        public boolean shown;

        public FileDownload(String url, String file_name) {
            this.url = url;
            this.file_name = file_name;

            final TLRPC.User bot = MessagesController.getInstance(currentAccount).getUser(botId);

            final DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
            request.setTitle(UserObject.getUserName(bot));
            request.setDescription(TextUtils.isEmpty(file_name) ? "Downloading file..." : "Downloading " + file_name + "...");
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE);
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, file_name);

            id = downloadManager.enqueue(request);
        }

        public FileDownload(JSONObject json) {
            url = json.optString("url");
            file_name = json.optString("file_name");
            size = json.optLong("size");
            done = json.optBoolean("done");
            mime = json.optString("mime");
            final String path = json.optString("path");
            if (!TextUtils.isEmpty(path))
                file = new File(path);
        }

        public JSONObject toJSON() {
            final JSONObject json = new JSONObject();
            try {
                json.put("url", url);
                json.put("file_name", file_name);;
                json.put("size", size);
                json.put("path", file == null ? null : file.getAbsolutePath());
                json.put("done", done);
                json.put("mime", mime);
            } catch (Exception e) {
                FileLog.e(e);
            }
            return json;
        }

        private final Runnable updateProgressRunnable = this::updateProgress;
        private void updateProgress() {
            if (done || cancelled)
                return;
            AndroidUtilities.cancelRunOnUIThread(updateProgressRunnable);
            last_progress_time = System.currentTimeMillis();
            final DownloadManager.Query query = new DownloadManager.Query();
            query.setFilterById(id);
            Cursor cursor = null;
            try {
                cursor = downloadManager.query(query);
                if (cursor.moveToFirst()) {
                    int status = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS));
                    if (status == DownloadManager.STATUS_SUCCESSFUL) {
                        String localUri = cursor.getString(cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI));
                        this.file = new File(Uri.parse(localUri).getPath());
                        done = true;
                        size = this.file.length();
                        if (size <= 0) {
                            cancel();
                        }
                        save();
                    } else if (status == DownloadManager.STATUS_FAILED) {
                        cancel();
                        return;
                    } else {
                        loaded_size = cursor.getLong(cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
                        size = cursor.getLong(cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES));
                        AndroidUtilities.runOnUIThread(updateProgressRunnable, 160L);
                    }
                } else {
                    if (!done) {
                        cancel();
                    }
                }
            } catch (Exception e) {
                FileLog.e(e);
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
            postNotify();
        }

        public Pair<Long, Long> getProgress() {
            if (done) return new Pair<>(size, size);
            if (id == null || cancelled) return new Pair<>(loaded_size, size);
            if ((System.currentTimeMillis() - last_progress_time) < 150L)
                return new Pair<>(loaded_size, size);
            updateProgress();
            return new Pair<>(loaded_size, size);
        }

        public void cancel() {
            BotDownloads.this.cancel(this);
        }

        public void open() {
            if (file != null && file.exists()) {
                AndroidUtilities.openForView(file, file.getName(), null, LaunchActivity.instance, null, true);
            }
        }

        public boolean isDownloading() {
            return !done && id != null;
        }

        public boolean isOver() {
            return done || cancelled;
        }

        public boolean isDownloaded() {
            return done;
        }

    }

    private static HashMap<String, Pair<String, Long>> cachedMimeAndSizes = new HashMap<>();
    public static void getMimeAndSize(final String url, Utilities.Callback2<String, Long> whenDone) {
        if (cachedMimeAndSizes.containsKey(url)) {
            final Pair<String, Long> pair = cachedMimeAndSizes.get(url);
            whenDone.run(pair.first, pair.second);
            return;
        }
        new AsyncTask<String, Void, String>() {

            String mime;
            long size;

            @Override
            protected String doInBackground(String... strings) {
                try {

                    HttpURLConnection urlConnection = (HttpURLConnection) new URL(url).openConnection();
                    urlConnection.setRequestMethod("GET");
                    urlConnection.setRequestProperty("Accept-Encoding", "identity");
                    urlConnection.setConnectTimeout(1000);
                    urlConnection.setReadTimeout(1000);
                    urlConnection.setUseCaches(false);
                    urlConnection.setDefaultUseCaches(false);
                    urlConnection.setDoOutput(false);
                    urlConnection.setDoInput(false);

                    urlConnection.getResponseCode();
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        size = urlConnection.getContentLengthLong();
                    } else {
                        size = urlConnection.getContentLength();
                    }
                    mime = urlConnection.getContentType();
                    if (mime.contains("; "))
                        mime = mime.substring(0, mime.indexOf("; "));
                    urlConnection.getInputStream().close();

                    return null;
                } catch (Exception e) {
                    FileLog.e(e);
                    return null;
                }
            }

            @Override
            protected void onPostExecute(String s) {
                cachedMimeAndSizes.put(url, new Pair<>(mime, size));
                if (whenDone != null) {
                    whenDone.run(mime, size);
                }
            }
        }.execute(url);
    }

    public static void showAlert(Context context, String url, String file_name, String botname, Utilities.Callback<Boolean> whenDone) {
        if (whenDone == null) return;
        showAlert(context, url, file_name, botname, whenDone, 0, "");
//
//        final AlertDialog progressDialog = new AlertDialog(context, AlertDialog.ALERT_TYPE_SPINNER);
//        progressDialog.showDelayed(300);
//        getMimeAndSize(url, (mime, size) -> {
//            progressDialog.dismiss();
//            showAlert(context, url, file_name, botname, whenDone, size, mime);
//        });
    }

    public static AlertDialog showAlert(Context context, String url, String file_name, String botname, Utilities.Callback<Boolean> whenDone, final long size, final String mime) {
        if (whenDone == null) return null;

        final AlertDialog.Builder b = new AlertDialog.Builder(context);

        b.setTitle(LocaleController.getString(R.string.BotDownloadFileTitle));
        b.setMessage(AndroidUtilities.replaceTags(LocaleController.formatString(R.string.BotDownloadFileText, botname)));

        final LinearLayout layout = new LinearLayout(context);
        layout.setPadding(dp(22), 0, dp(22), 0);
        layout.setOrientation(LinearLayout.HORIZONTAL);

        final ImageView imageView = new ImageView(context);
        imageView.setScaleType(ImageView.ScaleType.CENTER);
        imageView.setBackground(Theme.createCircleDrawable(dp(44), Theme.getColor(Theme.key_featuredStickers_addButton)));
        imageView.setImageResource(R.drawable.msg_round_file_s);
        layout.addView(imageView, LayoutHelper.createLinear(44, 44, Gravity.LEFT | Gravity.CENTER_VERTICAL, 0, 0, 10, 0));

        final LinearLayout textLayout = new LinearLayout(context);
        textLayout.setOrientation(LinearLayout.VERTICAL);

        final TextView titleView = new TextView(context);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        titleView.setTypeface(AndroidUtilities.bold());
        titleView.setText(file_name);
        titleView.setTextColor(Theme.getColor(Theme.key_dialogTextBlue2));
        textLayout.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 3));

        final AnimatedTextView subtitleView = new AnimatedTextView(context, true, true, true);
        subtitleView.setTextSize(dp(12));
        final SpannableString ss = new SpannableString("l");
        final LoadingSpan loadingSpan = new LoadingSpan(subtitleView, dp(55));
        loadingSpan.setColors(
            Theme.multAlpha(Theme.getColor(Theme.key_chat_inFileInfoText), .35f),
            Theme.multAlpha(Theme.getColor(Theme.key_chat_inFileInfoText), .075f)
        );
        ss.setSpan(loadingSpan, 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        subtitleView.setText(ss);
        getMimeAndSize(url, (_mime, _size) -> {
            StringBuilder sb = new StringBuilder();
            if (_size > 0) {
                sb.append("~").append(AndroidUtilities.formatFileSize(_size));
            }
            final String ext = _mime == null ? null : getExt(_mime).toUpperCase();
            if (!TextUtils.isEmpty(ext)) {
                if (sb.length() > 0) sb.append(" ");
                sb.append(ext.toUpperCase());
            }
            if (sb.length() <= 0) sb.append(LocaleController.getString(R.string.AttachDocument));
            subtitleView.setText(sb);
        });
        subtitleView.setTextColor(Theme.getColor(Theme.key_chat_inFileInfoText));
        textLayout.addView(subtitleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 15));

        layout.addView(textLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL | Gravity.FILL_HORIZONTAL, 0, 0, 0, 2));

        b.setView(layout);

        final boolean[] sent = new boolean[1];
        b.setNegativeButton(LocaleController.getString(R.string.Cancel), (di, w) -> {
            if (!sent[0]) {
                whenDone.run(false);
                sent[0] = true;
            }
        });
        b.setPositiveButton(LocaleController.getString(R.string.BotDownloadFileDownload), (di, w) -> {
            if (!sent[0]) {
                whenDone.run(true);
                sent[0] = true;
            }
        });

        AlertDialog d = b.create();
        d.setOnDismissListener(di -> {
            if (!sent[0]) {
                whenDone.run(false);
                sent[0] = true;
            }
        });
        d.show();

        return d;
    }

    public static String getExt(String mime) {
        if (mime == null || mime.isEmpty()) return "";
        switch (mime) {
            case "application/octet-stream": return "bin";
            case "application/x-abiword": return "abw";
            case "application/x-freearc": return "arc";
            case "video/x-msvideo": return "avi";
            case "application/vnd.amazon.ebook": return "azw";
            case "application/x-bzip": return "bz";
            case "application/x-bzip2": return "bz2";
            case "application/x-cdf": return "cda";
            case "application/x-csh": return "csh";
            case "application/msword": return "doc";
            case "application/vnd.openxmlformats-officedocument.wordprocessingml.document": return "docx";
            case "application/vnd.rar": return "rar";
            case "application/x-sh": return "sh";
            case "application/vnd.ms-fontobject": return "eot";
            case "application/epub+zip": return "epub";
            case "application/gzip":
            case "application/x-gzip": return "gz";
            case "image/vnd.microsoft.icon": return "ico";
            case "application/java-archive": return "jar";
            case "text/calendar": return "ics";
            case "text/javascript": return "js";
            case "application/ld+json": return "jsonld";
            case "audio/x-midi": return "midi";
            case "audio/mpeg": return "mp3";
            case "application/vnd.apple.installer+xml": return "mpkg";
            case "application/vnd.oasis.opendocument.presentation": return "odp";
            case "application/vnd.oasis.opendocument.spreadsheet": return "ods";
            case "application/vnd.oasis.opendocument.text": return "odt";
            case "audio/ogg": return "opus";
            case "application/x-httpd-php": return "php";
            case "application/vnd.ms-powerpoint": return "ppt";
            case "application/vnd.openxmlformats-officedocument.presentationml.presentation": return "pptx";
            case "application/vnd.ms-excel": return "xls";
            case "text/plain": return "txt";
            case "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet": return "xlsx";
            case "video/3gpp": case "audio/3gpp": return "3gp";
            case "video/3gpp2": case "audio/3gpp2": return "3g2";
            case "application/x-7z-compressed": return "7z";
            default:
                if (mime.contains("/"))
                    mime = mime.substring(mime.indexOf("/")+1);
                if (mime.contains("-"))
                    mime = mime.substring(mime.indexOf("-")+1);
                if (mime.contains("+"))
                    mime = mime.substring(0, mime.indexOf("+"));
                return mime.toLowerCase();
        }
    }

    public static class DownloadBulletin extends Bulletin.ButtonLayout {

        private final Theme.ResourcesProvider resourcesProvider;
        public final BackgroundDrawable background;
        public final StatusDrawable status;

        private final LinearLayout textLayout;
        private final ImageView imageView;
        private final TextView titleView;
        private final TextView subtitleView;

        public DownloadBulletin(@NonNull Context context, Theme.ResourcesProvider resourcesProvider) {
            super(context, resourcesProvider);

            this.resourcesProvider = resourcesProvider;
            setBackground(background = new BackgroundDrawable(dp(10)).setColor(Theme.getColor(Theme.key_undo_background, resourcesProvider)));

            imageView = new ImageView(context);
            imageView.setScaleType(ImageView.ScaleType.CENTER);
            imageView.setImageDrawable(status = new StatusDrawable(context, imageView));
            addView(imageView, LayoutHelper.createFrame(40, 40, Gravity.CENTER_VERTICAL | Gravity.FILL_HORIZONTAL, 7, 0, 0, 0));

            textLayout = new LinearLayout(context);
            textLayout.setOrientation(LinearLayout.VERTICAL);
            addView(textLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL | Gravity.FILL_HORIZONTAL, 54, 0, 0, 0));

            titleView = new TextView(context);
            titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            titleView.setTextColor(Theme.getColor(Theme.key_undo_infoColor, resourcesProvider));
            titleView.setTypeface(AndroidUtilities.bold());
            textLayout.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.FILL_HORIZONTAL | Gravity.TOP, 0, 0, 0, 2));

            subtitleView = new TextView(context);
            subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            subtitleView.setTextColor(Theme.getColor(Theme.key_undo_infoColor, resourcesProvider));
            textLayout.addView(subtitleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.FILL_HORIZONTAL | Gravity.TOP, 0, 0, 0, 0));
        }

        private FileDownload file;
        public boolean set(FileDownload file) {
            if (this.file != file) {
                status.reset();
            }
            this.file = file;
            titleView.setText(file.file_name);
            if (file.isDownloading()) {
                final Pair<Long, Long> progress = file.getProgress();
                status.setProgress(progress);
                if (progress.first <= 0) {
                    subtitleView.setText(LocaleController.getString(R.string.BotFileDownloading));
                } else if (progress.second <= 0) {
                    subtitleView.setText(AndroidUtilities.formatFileSize(progress.first));
                } else {
                    subtitleView.setText(AndroidUtilities.formatFileSize(progress.first) + " / " + AndroidUtilities.formatFileSize(progress.second));
                }
                setButton(1);
            } else if (file.cancelled) {
                Bulletin b = getBulletin();
                if (b != null) {
                    b.hide();
                }
                return true;
            } else if (file.done) {
                subtitleView.setText(LocaleController.getString(R.string.BotFileDownloaded));
                setButton(2);
                status.setDone(false);
                Bulletin b = getBulletin();
                if (b != null) {
                    b.setCanHide(false);
                    b.setDuration(Bulletin.DURATION_PROLONG);
                    b.setCanHide(true);
                }
            }
            return false;
        }

        private int currentButtonType = 0;
        private void setButton(int type) {
            if (currentButtonType == type) return;
            currentButtonType = type;
            if (type == 0) {
                setButton(null);
            } else if (type == 1) {
                final Bulletin.UndoButton btn = new Bulletin.UndoButton(getContext(), true, resourcesProvider).setText(LocaleController.getString(R.string.BotFileDownloadCancel)).setUndoAction(() -> {
                    Bulletin b = getBulletin();
                    if (b != null) {
                        b.setDuration(Bulletin.DURATION_LONG);
                        b.setCanHide(true);
                    }
                    if (file != null) {
                        file.cancel();
                    }
                });
                if (getBulletin() != null) {
                    btn.onAttach(this, getBulletin());
                }
                setButton(btn);
            } else if (type == 2) {
                final Bulletin.UndoButton btn = new Bulletin.UndoButton(getContext(), true, resourcesProvider).setText(LocaleController.getString(R.string.BotFileDownloadOpen)).setUndoAction(() -> {
                    Bulletin b = getBulletin();
                    if (b != null) {
                        b.hide();
                    }
                    if (file != null) {
                        file.open();
                    }
                });
                if (getBulletin() != null) {
                    btn.onAttach(this, getBulletin());
                }
                setButton(btn);
            }
        }

        public void setArrow(int rightMargin) {
            background.setArrow(rightMargin);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(dp(8 + 52 + 8), MeasureSpec.EXACTLY));
        }

        private static class StatusDrawable extends Drawable {

            private final View view;
            private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final RectF rect = new RectF();
            private final long start;

            private final Drawable doc;
            private boolean hasPercent;
            private float progress;
            private boolean done = false;
            private boolean cancelled;
            private AnimatedFloat animatedHasPercent = new AnimatedFloat(this::invalidateSelf, 0, 320, CubicBezierInterpolator.EASE_OUT_QUINT);
            private AnimatedFloat animatedProgress = new AnimatedFloat(this::invalidateSelf, 0, 320, CubicBezierInterpolator.EASE_OUT_QUINT);
            private AnimatedFloat animatedDone = new AnimatedFloat(this::invalidateSelf, 0, 320, CubicBezierInterpolator.EASE_OUT_QUINT);

            private RLottieDrawable doneDrawable;

            public StatusDrawable(Context context, View view) {
                this.view = view;
                start = System.currentTimeMillis();
                doc = context.getResources().getDrawable(R.drawable.search_files_filled).mutate();
                strokePaint.setStyle(Paint.Style.STROKE);
                strokePaint.setStrokeWidth(dp(2));
                strokePaint.setStrokeCap(Paint.Cap.ROUND);
                strokePaint.setStrokeJoin(Paint.Join.ROUND);
            }

            public void reset() {
                this.animatedDone.set(this.done = false, true);
                this.cancelled = false;
                if (doneDrawable != null) {
                    doneDrawable.recycle(true);
                    doneDrawable = null;
                }
                this.animatedHasPercent.set(this.hasPercent = false, true);
            }

            public void setProgress(Pair<Long, Long> progress) {
                hasPercent = progress != null && progress.second > 0;
                if (hasPercent) {
                    this.progress = Utilities.clamp((float) progress.first / progress.second, 1, 0);
                }
                invalidateSelf();
            }

            public void setDone(boolean cancelled) {
                if (this.done) return;
                this.done = true;
                this.cancelled = cancelled;
                doneDrawable = new RLottieDrawable(cancelled ? R.raw.error : R.raw.contact_check, cancelled ? "error" : "contact_check", dp(40), dp(40));
                doneDrawable.setMasterParent(view);
                doneDrawable.setAllowDecodeSingleFrame(true);
                doneDrawable.start();
                if (!cancelled) {
                    progress = 1.0f;
                }
            }

            @Override
            public void draw(@NonNull Canvas canvas) {
                final Rect bounds = getBounds();
                final int cx = bounds.centerX(), cy = bounds.centerY();

                final float done = this.animatedDone.set(this.done);

                if (done < 1) {
                    final float s = .6f + .4f * (1.0f - done);
                    canvas.save();
                    canvas.scale(s, s, cx, cy);
                    doc.setBounds(
                        cx - doc.getIntrinsicWidth() / 2,
                        cy - doc.getIntrinsicHeight() / 2,
                        cx + doc.getIntrinsicWidth() / 2,
                        cy + doc.getIntrinsicHeight() / 2
                    );
                    doc.setAlpha((int) (0xFF * (1.0f - done)));
                    doc.draw(canvas);

                    final float r = dp(14);
                    strokePaint.setColor(Theme.multAlpha(0xFFFFFFFF, .20f * (1.0f - done)));
                    canvas.drawCircle(cx, cy, r, strokePaint);
                    strokePaint.setColor(Theme.multAlpha(0xFFFFFFFF, 1.0f * (1.0f - done)));
                    rect.set(cx - r, cy - r, cx + r, cy + r);

                    final float hasPercent = this.animatedHasPercent.set(this.hasPercent);

                    strokePaint.setColor(Theme.multAlpha(0xFFFFFFFF, .15f * (1.0f - done) * (1.0f - hasPercent)));
                    canvas.drawArc(rect, (-90 + -((-1.0f + ((System.currentTimeMillis() - start) % 600) / 600.0f) * 360)), -90.0f, false, strokePaint);

                    float t = ((System.currentTimeMillis() - start) * .45f) % 5400;
                    float segment0 = Math.max(0, 1520 * t / 5400f - 20);
                    float segment1 = 1520 * t / 5400f;
                    for (int i = 0; i < 4; ++i) {
                        segment1 += CircularProgressDrawable.interpolator.getInterpolation((t - i * 1350) / 667f) * 250;
                        segment0 += CircularProgressDrawable.interpolator.getInterpolation((t - (667 + i * 1350)) / 667f) * 250;
                    }
//
////                    float offset = 0, length = 0;
////                    if (hasPercent < 1) {
////                        offset += (-90 + -((-1.0f + ((System.currentTimeMillis() - start) % 600) / 600.0f) * 360)) * (1.0f - hasPercent);
////                        length += -90.0f * (1.0f - hasPercent);
////                    }
////                    if (hasPercent > 0) {
////                        offset += -90 * hasPercent;
////                        length += -animatedProgress.set(progress) * 360 * hasPercent;
////                    }
                    strokePaint.setColor(Theme.multAlpha(0xFFFFFFFF, 1.0f * (1.0f - done)));
                    canvas.drawArc(rect, -90 - segment0, -360 * Math.max(.02f, animatedProgress.set(progress)) * hasPercent, false, strokePaint);
                    invalidateSelf();
                    canvas.restore();
                }

                if (done > 0) {
                    final float s = .6f + .4f * done;
                    if (cancelled) {
                        canvas.save();
                        canvas.scale(s, s, cx, cy);
                    }
                    if (doneDrawable != null) {
                        doneDrawable.setBounds(
                            cx - doneDrawable.getIntrinsicWidth() / 2,
                            cy - doneDrawable.getIntrinsicHeight() / 2,
                            cx + doneDrawable.getIntrinsicWidth() / 2,
                            cy + doneDrawable.getIntrinsicHeight() / 2
                        );
                        doneDrawable.setAlpha((int) (0xFF * done));
                        doneDrawable.draw(canvas);
                    }

                    if (cancelled) {
                        canvas.restore();
                    }
                }
            }

            @Override
            public int getIntrinsicWidth() {
                return dp(40);
            }
            @Override
            public int getIntrinsicHeight() {
                return dp(40);
            }

            @Override
            public void setAlpha(int alpha) {}
            @Override
            public void setColorFilter(@Nullable ColorFilter colorFilter) {}
            @Override
            public int getOpacity() {
                return PixelFormat.TRANSPARENT;
            }
        }

        private static class BackgroundDrawable extends Drawable {

            private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final RectF rect = new RectF();
            private final int r;
            private final Path path = new Path();

            private boolean arrow;
            private int arrowMargin;
            private final AnimatedFloat arrowProgress = new AnimatedFloat(this::invalidateSelf, 0, 320, CubicBezierInterpolator.EASE_OUT_QUINT);
            private final AnimatedFloat arrowX = new AnimatedFloat(this::invalidateSelf, 0, 320, CubicBezierInterpolator.EASE_OUT_QUINT);

            public BackgroundDrawable(int r) {
                this.r = r;
                path.moveTo(-dp(6.5f), 0);
                path.lineTo(dp(6.5f), 0);
                path.lineTo(0, -dp(6.16f));
                path.close();
            }

            public void setArrow(int rightMargin) {
//                if (arrow == (rightMargin >= 0) && (!arrow || arrowMargin == rightMargin))
//                    return;
                arrow = rightMargin >= 0;
                if (arrow) {
                    arrowMargin = rightMargin;
                }
                invalidateSelf();
            }

            public BackgroundDrawable setColor(int color) {
                paint.setColor(color);
                return this;
            }

            @Override
            public void draw(@NonNull Canvas canvas) {
                rect.set(getBounds());
                rect.inset(dp(8), dp(8));
                canvas.drawRoundRect(rect, r, r, paint);

                final float arrowAlpha = this.arrowProgress.set(arrow);
                final float arrowX = rect.right + dp(8) - this.arrowX.set(arrowMargin);

                if (arrowAlpha > 0) {
                    canvas.save();
                    canvas.translate(arrowX, dp(8) + dp(6.16f) * (1.0f - arrowAlpha));
                    canvas.drawPath(path, paint);
                    canvas.restore();
                }
            }

            @Override
            public void setAlpha(int alpha) {}
            @Override
            public void setColorFilter(@Nullable ColorFilter colorFilter) {}
            @Override
            public int getOpacity() {
                return PixelFormat.TRANSPARENT;
            }
        }

    }

    public static void clear() {
        Context context = ApplicationLoader.applicationContext;
        if (context == null) return;
        for (int i = 0; i < UserConfig.MAX_ACCOUNT_COUNT; ++i) {
            final SharedPreferences prefs = context.getSharedPreferences(PREF + i, Activity.MODE_PRIVATE);
            prefs.edit().clear().apply();
        }
        instances.clear();
    }

}
