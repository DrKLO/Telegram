package org.telegram.ui.web;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.os.Build;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import androidx.annotation.Keep;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.AbstractSerializedData;
import org.telegram.tgnet.InputSerializedData;
import org.telegram.tgnet.OutputSerializedData;
import org.telegram.tgnet.SerializedData;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.LaunchActivity;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;

public class WebMetadataCache {

    private static WebMetadataCache instance;
    public static WebMetadataCache getInstance() {
        if (instance == null) {
            instance = new WebMetadataCache();
        }
        return instance;
    }

    public static final int MAX_COUNT = 100;
    public static final int EXPIRATION = 1000 * 60 * 60 * 24 * 7;

    private HashMap<String, WebMetadata> cache;

    public static class WebMetadata extends TLObject {

        public long time = System.currentTimeMillis();
        public String domain;
        public String title;
        public String sitename;
        public int actionBarColor;
        public int backgroundColor;
        public Bitmap favicon;

        public byte[] faviconBytes;

        public static WebMetadata from(BotWebViewContainer.MyWebView webView) {
            WebMetadata metadata = new WebMetadata();
            metadata.domain = AndroidUtilities.getHostAuthority(webView.getUrl(), true);
            if (TextUtils.isEmpty(metadata.domain)) return null;
            if (webView.lastTitleGot) {
                metadata.title = webView.lastTitle;
            }
            metadata.sitename = webView.lastSiteName;
            if (webView.lastActionBarColorGot) {
                metadata.actionBarColor = webView.lastActionBarColor;
            }
            if (webView.lastBackgroundColorGot) {
                metadata.backgroundColor = webView.lastBackgroundColor;
            }
            if (webView.lastFaviconGot) {
                metadata.favicon = webView.lastFavicon;
            }
            return metadata;
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt64(time);
            stream.writeString(domain == null ? "" : domain);
            stream.writeString(title == null ? "" : title);
            stream.writeString(sitename == null ? "" : sitename);
            stream.writeInt32(actionBarColor);
            stream.writeInt32(backgroundColor);
            if (favicon == null) {
                stream.writeInt32(TLRPC.TL_null.constructor);
            } else {
                stream.writeInt32(0x38da9893);
                if (faviconBytes != null) {
                    stream.writeByteArray(faviconBytes);
                } else {
                    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        favicon.compress(Bitmap.CompressFormat.WEBP_LOSSY, 80, byteArrayOutputStream);
                    } else {
                        favicon.compress(Bitmap.CompressFormat.WEBP, 80, byteArrayOutputStream);
                    }
                    stream.writeByteArray(faviconBytes = byteArrayOutputStream.toByteArray());
                    try {
                        byteArrayOutputStream.close();
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                }
            }
        }

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            time = stream.readInt64(exception);
            domain = stream.readString(exception);
            title = stream.readString(exception);
            sitename = stream.readString(exception);
            actionBarColor = stream.readInt32(exception);
            backgroundColor = stream.readInt32(exception);
            int magic = stream.readInt32(exception);
            if (magic == TLRPC.TL_null.constructor) {
                favicon = null;
            } else {
                faviconBytes = stream.readByteArray(exception);
                favicon = BitmapFactory.decodeStream(new ByteArrayInputStream(faviconBytes));
            }
        }
    }

    private final static class MetadataFile extends TLObject {
        public final ArrayList<WebMetadata> array = new ArrayList<>();

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(array.size());
            for (int i = 0; i < array.size(); ++i) {
                array.get(i).serializeToStream(stream);
            }
        }

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            final int count = stream.readInt32(exception);
            for (int i = 0; i < count; ++i) {
                WebMetadata metadata = new WebMetadata();
                metadata.readParams(stream, exception);
                if (TextUtils.isEmpty(metadata.domain)) return;
                array.add(metadata);
            }
        }
    }

    public File getCacheFile() {
        return new File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE), "webmetacache.dat");
    }

    public WebMetadata get(String domain) {
        load();
        WebMetadata meta = cache.get(domain);
        if (meta == null) return null;
        meta.time = Math.max(meta.time, System.currentTimeMillis());
        scheduleSave();
        return meta;
    }

    public void save(WebMetadata meta) {
        if (meta == null) return;
        if (cache == null) {
            cache = new HashMap<>();
        }
        if (TextUtils.isEmpty(meta.domain)) return;
        cache.put(meta.domain, meta);
        load();
        scheduleSave();
    }

    private boolean loaded;
    private boolean loading;
    private boolean saving;

    public void load() {
        if (loaded || loading) return;
        loading = true;
        if (cache == null) {
            cache = new HashMap<>();
        }
        Utilities.globalQueue.postRunnable(() -> {
            final File file = getCacheFile();
            if (!file.exists()) {
                loaded = true;
                return;
            }

            final ArrayList<WebMetadata> result = new ArrayList<>();

            try {
                SerializedData stream = new SerializedData(file);
                MetadataFile data = new MetadataFile();
                data.readParams(stream, true);
                result.addAll(data.array);
            } catch (Exception e) {
                FileLog.e(e);
            }

            AndroidUtilities.runOnUIThread(() -> {
                for (int i = 0; i < result.size(); ++i) {
                    final WebMetadata meta = result.get(i);
                    cache.put(meta.domain, meta);
                }
                loaded = true;
                loading = false;
            });
        });
    }

    public void scheduleSave() {
        AndroidUtilities.cancelRunOnUIThread(this::save);
        if (saving) return;
        AndroidUtilities.runOnUIThread(this::save, BuildVars.DEBUG_PRIVATE_VERSION ? 1 : 1_000);
    }

    public void save() {
        if (saving) return;
        saving = true;

        final long now = System.currentTimeMillis();
        final ArrayList<WebMetadata> toBeSaved = new ArrayList<>();
        for (WebMetadata meta : cache.values()) {
            if (TextUtils.isEmpty(meta.domain) || now - meta.time > EXPIRATION)
                continue;
            toBeSaved.add(0, meta);
            if (toBeSaved.size() >= MAX_COUNT)
                break;
        }

        Utilities.globalQueue.postRunnable(() -> {
            final File file = getCacheFile();
            if (!file.exists()) {
                try {
                    file.createNewFile();
                } catch (Exception e) {
                    FileLog.e(e);
                    saving = false;
                    return;
                }
            }

            MetadataFile data = new MetadataFile();
            data.array.addAll(toBeSaved);
            final int size = data.getObjectSize();
            SerializedData stream = new SerializedData(size);
            data.serializeToStream(stream);

            try {
                FileOutputStream os = new FileOutputStream(file);
                os.write(stream.toByteArray());
                os.close();
            } catch (Exception e) {
                FileLog.e(e);
            }

            AndroidUtilities.runOnUIThread(() -> {
                saving = false;
            });
        });
    }

    public void clear() {
        if (cache == null) {
            loading = false;
            loaded = true;
            cache = new HashMap<>();
        } else {
            cache.clear();
        }
        scheduleSave();
    }

    private static class SitenameProxy {
        private final Utilities.Callback<String> whenReceived;
        public SitenameProxy(Utilities.Callback<String> whenReceived) {
            this.whenReceived = whenReceived;
        }
        @Keep
        @JavascriptInterface
        public void post(String type, String data) {
            AndroidUtilities.runOnUIThread(() -> {
                switch (type) {
                    case "siteName": {
                        whenReceived.run(data);
                        break;
                    }
                    case "siteNameEmpty": {
                        whenReceived.run(null);
                        break;
                    }
                }
            });
        }
    }

    public static void retrieveFaviconAndSitename(String url, Utilities.Callback2<String, Bitmap> whenDone) {
        if (whenDone == null) return;

        Context context = LaunchActivity.instance;
        if (context == null) context = ApplicationLoader.applicationContext;

        final Activity activity = AndroidUtilities.findActivity(context);
        if (activity == null) {
            whenDone.run(null, null);
            return;
        }
        final View rootView = activity.findViewById(android.R.id.content).getRootView();
        if (!(rootView instanceof ViewGroup)) {
            whenDone.run(null, null);
            return;
        }
        final ViewGroup container = (ViewGroup) rootView;
        final FrameLayout webViewContainer = new FrameLayout(context) {
            @Override
            protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
                return false;
            }
            @Override
            public boolean onTouchEvent(MotionEvent event) {
                return false;
            }
            @Override
            public boolean dispatchTouchEvent(MotionEvent ev) {
                return false;
            }
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(MeasureSpec.makeMeasureSpec(dp(500), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(dp(500), MeasureSpec.EXACTLY));
            }
        };
        container.addView(webViewContainer);

        final WebView webView = new WebView(context);
        final WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setGeolocationEnabled(false);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(false);
        settings.setSupportMultipleWindows(false);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setCacheMode(WebSettings.LOAD_CACHE_ELSE_NETWORK);
        settings.setSaveFormData(false);
        settings.setSavePassword(false);
        webView.setVerticalScrollBarEnabled(false);

        try {
            settings.setUserAgentString(settings.getUserAgentString().replace("; wv)", ")"));
        } catch (Exception e) {
            FileLog.e(e);
        }

        final boolean[] done = new boolean[] { false };
        final String[] sitename = new String[] { null };
        final Bitmap[] favicon =  new Bitmap[] { null };

        final Utilities.Callback<Boolean> checkDone = force -> {
            if (done[0]) return;

            if (
                force ||
                !TextUtils.isEmpty(sitename[0]) &&
                (favicon[0] != null && favicon[0].getWidth() > dp(28) && favicon[0].getHeight() > dp(28))
            ) {
                done[0] = true;

                WebMetadataCache.WebMetadata meta = new WebMetadata();
                meta.domain = AndroidUtilities.getHostAuthority(url, true);
                meta.sitename = sitename[0];
                if (favicon[0] != null) {
                    meta.favicon = Bitmap.createBitmap(favicon[0]);
                }
                getInstance().save(meta);

                webView.destroy();
                AndroidUtilities.removeFromParent(webView);
                AndroidUtilities.removeFromParent(webViewContainer);

                whenDone.run(sitename[0], favicon[0]);

                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.webViewResolved, url);
            }
        };

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onReceivedIcon(WebView view, Bitmap icon) {
                if (icon == null) return;
                if (favicon[0] == null || favicon[0].getWidth() < icon.getWidth() && favicon[0].getHeight() < icon.getHeight()) {
                    favicon[0] = icon;
                    checkDone.run(false);
                }
            }
        });
        Runnable putJS = () -> {
            final String js = AndroidUtilities.readRes(R.raw.webview_ext).replace("$DEBUG$", "" + BuildVars.DEBUG_VERSION);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                webView.evaluateJavascript(js, value -> {});
            } else {
                try {
                    webView.loadUrl("javascript:" + URLEncoder.encode(js, "UTF-8"));
                } catch (UnsupportedEncodingException e) {
                    webView.loadUrl("javascript:" + URLEncoder.encode(js));
                }
            }
        };
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                putJS.run();
            }
        });
        webView.addJavascriptInterface(new SitenameProxy(name -> {
            sitename[0] = name;
            checkDone.run(false);
        }), "TelegramWebview");

        webViewContainer.addView(webView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        webView.loadUrl(url);
        putJS.run();

        AndroidUtilities.runOnUIThread(() -> {
            checkDone.run(true);
        }, 10_000);
    }
}
