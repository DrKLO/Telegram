package org.telegram.ui.web;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.readRes;
import static org.telegram.messenger.LocaleController.getString;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.os.Build;
import android.text.TextUtils;
import android.util.JsonReader;
import android.util.Log;
import android.util.Pair;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import androidx.annotation.Keep;

import com.google.common.collect.Lists;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.SvgHelper;
import org.telegram.messenger.Timer;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.browser.Browser;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.LaunchActivity;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

public class WebInstantView {

    public String url;
    public MHTML mhtml;
    public TLRPC.WebPage webpage;

    public final HashMap<String, Bitmap> loadedPhotos = new HashMap<>();
    public static final HashMap<TLRPC.WebPage, WebInstantView> instants = new HashMap<>();

    public static Runnable generate(WebView webView, boolean unsafe, Utilities.Callback<WebInstantView> whenDone) {
        if (whenDone == null) return null;
        if (webView == null) {
            whenDone.run(null);
            return null;
        }

        final boolean[] cancelled = new boolean[] { false };

        final WebInstantView instant = new WebInstantView();
        instant.url = webView.getUrl();

        final Timer t = Timer.create("WebInstantView");
        final Timer.Task task_getHTML = Timer.start(t, "getHTML");
        instant.getHTML(webView, unsafe, dirty_html -> {
            Timer.done(task_getHTML);
            if (cancelled[0]) {
                return;
            }
            final Timer.Task task_readHTML = Timer.start(t, "readHTML");
            instant.readHTML(instant.url, dirty_html, json -> {
                Timer.done(task_readHTML);
                if (cancelled[0]) {
                    return;
                }
                final Timer.Task task_parseJSON = Timer.start(t, "parseJSON");
                try {
                    instant.webpage = instant.parseJSON(instant.url, json);
                } catch (Exception e) {
                    Timer.log(t, "error: " + e);
                    FileLog.e(e);
                }
                Timer.done(task_parseJSON);
                whenDone.run(instant);
                if (instant.webpage != null) {
                    instants.put(instant.webpage, instant);
                }
                Timer.finish(t);
            });
        });

        return () -> {
            cancelled[0] = true;
        };
    }

    public void recycle() {
        instants.remove(webpage);
        for (Map.Entry<String, Bitmap> e : loadedPhotos.entrySet()) {
            AndroidUtilities.recycleBitmap(e.getValue());
        }
        loadedPhotos.clear();
        if (webpage != null && webpage.cached_page != null && webpage.cached_page.photos != null) {
            for (TLRPC.Photo photo : webpage.cached_page.photos) {
                if (photo instanceof WebPhoto) {
                    WebPhoto webPhoto = (WebPhoto) photo;
                    if (loadingPhotos != null) {
                        loadingPhotos.remove(webPhoto.url);
                    }
                }
            }
        }
    }

    public class WebPhoto extends TLRPC.Photo {
        public WebInstantView instantView;

        public String url;
        public HashSet<String> urls = new HashSet<>();
        public int w, h;

        public TLRPC.TL_textImage inlineImage;
    }

    public static void loadPhoto(WebPhoto photo, ImageReceiver imageReceiver, Runnable receivedSize) {
        if (photo == null || photo.instantView == null) return;
        photo.instantView.loadPhotoInternal(photo, imageReceiver, receivedSize);
    }

    private static HashMap<String, ArrayList<Pair<ImageReceiver, Runnable>>> loadingPhotos;

    private void loadPhotoInternal(WebPhoto photo, ImageReceiver imageReceiver, Runnable receivedSize) {
        try {
            MHTML.Entry entry = null;
            if (mhtml != null) {
                for (String url : photo.urls) {
                    entry = mhtml.entriesByLocation.get(url);
                    if (entry != null) break;
                }
            }
            if (entry != null) {
                Bitmap bitmap;
                if (entry.getType().contains("svg")) {
                    if (photo.w <= 0 || photo.h <= 0) return;
                    bitmap = SvgHelper.getBitmap(entry.getInputStream(), dp(photo.w), dp(photo.h), false);
                } else {
                    if (photo.w <= 0 || photo.h <= 0) {
                        final BitmapFactory.Options opts = new BitmapFactory.Options();
                        opts.inJustDecodeBounds = true;
                        BitmapFactory.decodeStream(entry.getInputStream(), null, opts);

                        if (photo.w == 0 && photo.h == 0) {
                            photo.w = opts.outWidth;
                            photo.h = opts.outHeight;
                        } else if (photo.w == 0) {
                            photo.w = (int) ((float) opts.outWidth / opts.outHeight * photo.h);
                        } else if (photo.h == 0) {
                            photo.h = (int) ((float) opts.outHeight / opts.outWidth * photo.w);
                        }
                        if (photo.inlineImage != null) {
                            photo.inlineImage.w = photo.w;
                            photo.inlineImage.h = photo.h;
                        }
                        if (receivedSize != null) {
                            receivedSize.run();
                        }
                    }
                    bitmap = BitmapFactory.decodeStream(entry.getInputStream());
                }
                imageReceiver.setImageBitmap(bitmap);
                return;
            }
            if (loadedPhotos.containsKey(photo.url)) {
                final Bitmap bitmap = loadedPhotos.get(photo.url);
                imageReceiver.setImageBitmap(bitmap);
                return;
            }
            if (loadingPhotos == null) loadingPhotos = new HashMap<>();
            ArrayList<Pair<ImageReceiver, Runnable>> currentImageReceivers = loadingPhotos.get(photo.url);
            if (currentImageReceivers != null) {
                boolean contains = false;
                for (int i = 0; i < currentImageReceivers.size(); ++i) {
                    if (currentImageReceivers.get(i).first == imageReceiver) {
                        contains = true;
                        break;
                    }
                }
                if (!contains)
                    currentImageReceivers.add(new Pair<>(imageReceiver, receivedSize));
                return;
            }
            currentImageReceivers = new ArrayList<>();
            loadingPhotos.put(photo.url, currentImageReceivers);
            new HttpGetBitmapTask(bitmap -> AndroidUtilities.runOnUIThread(() -> {
                if (loadingPhotos == null) return;
                final boolean updatedSize = (photo.w <= 0 || photo.h <= 0) && bitmap != null;
                if (bitmap != null) {
                    loadedPhotos.put(photo.url, bitmap);
                    if (updatedSize) {
                        if (photo.w == 0 && photo.h == 0) {
                            photo.w = bitmap.getWidth();
                            photo.h = bitmap.getHeight();
                        } else if (photo.w == 0) {
                            photo.w = (int) ((float) bitmap.getWidth() / bitmap.getHeight() * photo.h);
                        } else if (photo.h == 0) {
                            photo.h = (int) ((float) bitmap.getHeight() / bitmap.getWidth() * photo.w);
                        }
                        if (photo.inlineImage != null) {
                            photo.inlineImage.w = photo.w;
                            photo.inlineImage.h = photo.h;
                        }
                    }
                }
                final ArrayList<Pair<ImageReceiver, Runnable>> receivingImageReceivers = loadingPhotos.remove(photo.url);
                if (receivingImageReceivers == null) return;
                for (Pair<ImageReceiver, Runnable> pair : receivingImageReceivers) {
                    pair.first.setImageBitmap(bitmap);
                    if (updatedSize && pair.second != null) {
                        pair.second.run();
                    }
                }

            })).execute(photo.url);
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    public static void cancelLoadPhoto(ImageReceiver imageReceiver) {
        if (loadingPhotos == null) return;
        for (Map.Entry<String, ArrayList<Pair<ImageReceiver, Runnable>>> e : loadingPhotos.entrySet()) {
            final String url = e.getKey();
            final ArrayList<Pair<ImageReceiver, Runnable>> imageReceivers = e.getValue();
            for (int i = 0; i < imageReceivers.size(); ++i) {
                if (imageReceivers.get(i).first == imageReceiver) {
                    imageReceivers.remove(i);
                    break;
                }
            }
            if (imageReceivers.isEmpty()) {
                loadingPhotos.remove(url);
                break;
            }
        }
    }

    public static void recycle(TLRPC.WebPage webPage) {
        WebInstantView webInstantView = instants.remove(webPage);
        if (webInstantView != null) {
            webInstantView.recycle();
        }
    }

    public void getHTML(WebView webView, boolean unsafe, Utilities.Callback<InputStream> whenDone) {
        if (whenDone == null) return;
        if (webView == null) {
            whenDone.run(null);
            return;
        }

        if (unsafe) {
            webView.evaluateJavascript("document.documentElement.outerHTML", str -> {
                try {
                    JsonReader reader = new JsonReader(new StringReader(str));
                    reader.setLenient(true);
                    String html = reader.nextString();
                    reader.close();
                    whenDone.run(new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8)));
                } catch (Exception e) {
                    FileLog.e(e);
                    whenDone.run(null);
                }
            });
            return;
        }

        final long start = System.currentTimeMillis();

        final File archiveFile = new File(AndroidUtilities.getCacheDir(), "archive.mht");
        webView.evaluateJavascript(readRes(R.raw.open_collapsed).replace("$OPEN$", "true"), v -> {
            webView.saveWebArchive(archiveFile.getAbsolutePath(), false, filename -> {
                webView.evaluateJavascript(readRes(R.raw.open_collapsed).replace("$OPEN$", "false"), v2 -> {});

                String html = null;

                try {
                    mhtml = new MHTML(archiveFile);
                    if (!mhtml.entries.isEmpty()) {
                        whenDone.run(mhtml.entries.get(0).getInputStream());
                        return;
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                }

                whenDone.run(null);
            });
        });
    }

    public void readHTML(String url, InputStream stream, Utilities.Callback<JSONObject> whenDone) {
        if (whenDone == null) return;
        if (stream == null) {
            whenDone.run(null);
            return;
        }

        Context context = LaunchActivity.instance;
        if (context == null) context = ApplicationLoader.applicationContext;

        final Activity activity = AndroidUtilities.findActivity(context);
        if (activity == null) {
            whenDone.run(null);
            return;
        }
        final View rootView = activity.findViewById(android.R.id.content).getRootView();
        if (!(rootView instanceof ViewGroup)) {
            whenDone.run(null);
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
        settings.setAllowContentAccess(false);
        settings.setDatabaseEnabled(false);
        settings.setAllowFileAccess(false);
        settings.setJavaScriptEnabled(true);
        settings.setSaveFormData(false);
        settings.setGeolocationEnabled(false);
        settings.setDomStorageEnabled(false);
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);

        webView.setWebViewClient(new WebViewClient() {
            private boolean firstLoad = true;
            private boolean streamLoaded;
            @androidx.annotation.Nullable
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, String url) {
                InputStream requestStream = null;
                String mime = "text/html";
                if (firstLoad) {
                    firstLoad = false;
                    final String script = readRes(R.raw.instant).replace("$DEBUG$", "" + BuildVars.DEBUG_VERSION);
                    final String html = "<script>\n" + script + "\n</script>";
                    return new WebResourceResponse("text/html", "UTF-8", new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8)));
                } else if (url != null && url.endsWith("/index.html")) {
                    mime = "application/octet-stream";
                    if (streamLoaded) {
                        MHTML.Entry entry = mhtml != null ? mhtml.entries.get(0) : null;
                        if (entry == null) {
                            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return new WebResourceResponse(mime, "UTF-8", null);
                            return new WebResourceResponse("text/plain", "utf-8", 404, "Not Found", null, null);
                        }
                        try {
                            requestStream = entry.getInputStream();
                        } catch (IOException e) {
                            FileLog.e(e);
                            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return new WebResourceResponse(mime, "UTF-8", null);
                            return new WebResourceResponse("text/plain", "utf-8", 503, "Server error", null, null);
                        }
                    } else {
                        requestStream = stream;
                        streamLoaded = true;
                    }
                } else {
                    MHTML.Entry entry = mhtml != null ? mhtml.entriesByLocation.get(url) : null;
                    if (entry == null) {
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return new WebResourceResponse(mime, "UTF-8", null);
                        return new WebResourceResponse("text/plain", "utf-8", 404, "Not Found", null, null);
                    }
                    mime = entry.getType();
                    if (!"text/html".equalsIgnoreCase(mime) && !"text/css".equalsIgnoreCase(mime)) {
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return new WebResourceResponse(mime, "UTF-8", null);
                        return new WebResourceResponse("text/plain", "utf-8", 404, "Not Found", null, null);
                    }
                    try {
                        requestStream = entry.getInputStream();
                    } catch (IOException e) {
                        FileLog.e(e);
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return new WebResourceResponse(mime, "UTF-8", null);
                        return new WebResourceResponse("text/plain", "utf-8", 503, "Server error", null, null);
                    }
                }
                return new WebResourceResponse(mime, null, requestStream);
            }
        });
        webView.setWebChromeClient(new WebChromeClient() {

        });
        webViewContainer.addView(webView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        final boolean[] done = new boolean[] { false };
        webView.addJavascriptInterface(new Object() {
            @Keep
            @JavascriptInterface
            public void done(String json) {
                AndroidUtilities.runOnUIThread(() -> {
                    if (done[0]) return;
                    done[0] = true;

                    if (!BuildVars.DEBUG_PRIVATE_VERSION) {
                        webView.onPause();
                        webView.destroy();
                        AndroidUtilities.removeFromParent(webView);
                        AndroidUtilities.removeFromParent(webViewContainer);
                    }

                    JSONObject rez = null;
                    try {
                        rez = new JSONObject(json);
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                    whenDone.run(rez);
                });
            }
        }, "Instant");
        webView.loadUrl(url);
    }

    public TLRPC.TL_webPage parseJSON(String url, JSONObject root) throws JSONException {
        TLRPC.TL_webPage webpage = new TLRPC.TL_webPage();

        webpage.id = 0L;
        webpage.url = url;
        webpage.display_url = url;

        String sitename = root.getString("siteName");
        if (sitename != null && !"null".equals(sitename)) {
            webpage.flags |= 2;
            webpage.site_name = sitename;
        }

        String title = root.optString("title");
        if (title != null && !"null".equals(title)) {
            webpage.flags |= 4;
            webpage.title = title;
        }

        String byline = root.optString("byline");
        if (byline != null && !"null".equals(byline) && !"by".equalsIgnoreCase(byline)) {
            webpage.flags |= 256;
            webpage.author = byline;
        }

        String excerpt = root.optString("excerpt");
        if (excerpt != null && !"null".equals(excerpt)) {
            webpage.flags |= 8;
            webpage.description = excerpt;
        }

        JSONArray content = root.optJSONArray("content");
        if (content != null && !"null".equals(content)) {
            webpage.flags |= 1024;
            webpage.cached_page = parsePage(url, root);
        }

        return webpage;
    }

    public TLRPC.TL_page parsePage(String url, JSONObject parsed) throws JSONException {
        String title = parsed.optString("title");
        if ("null".equals(title)) title = null;
//        String byline = parsed.optString("byline");
//        if ("null".equals(byline) || "by".equalsIgnoreCase(byline)) byline = null;
        String publishedTime = parsed.optString("publishedTime");
        if ("null".equals(publishedTime)) publishedTime = null;

        final JSONArray content = parsed.optJSONArray("content");

        final TLRPC.TL_page page = new TLRPC.TL_page();
        page.web = true;
        page.url = url;
        page.blocks.addAll(parsePageBlocks(url, content, page));
        if (page.blocks.isEmpty() || !(page.blocks.get(0) instanceof TLRPC.TL_pageBlockHeader)) {
//            if (byline != null || publishedTime != null) {
//                final TLRPC.TL_pageBlockAuthorDate authorDate = new TLRPC.TL_pageBlockAuthorDate();
//                authorDate.author = trim(parseRichText(byline));
//                page.blocks.add(0, authorDate);
//            }

            final TLRPC.TL_pageBlockTitle blockTitle = new TLRPC.TL_pageBlockTitle();
            blockTitle.text = trim(parseRichText(title));
            page.blocks.add(0, blockTitle);
        }
        return page;
    }

    public ArrayList<TLRPC.PageBlock> parsePageBlocks(String url, JSONArray json, TLRPC.TL_page page) throws JSONException {
        final ArrayList<TLRPC.PageBlock> blocks = new ArrayList<>();
        for (int i = 0; i < json.length(); ++i) {
            Object obj = json.get(i);
            TLRPC.PageBlock block = null;
            if (obj instanceof String) {
                TLRPC.TL_pageBlockParagraph p = new TLRPC.TL_pageBlockParagraph();
                p.text = parseRichText((String) obj);
                blocks.add(p);
            } else if (obj instanceof JSONObject) {
                JSONObject o = (JSONObject) obj;
                final String tag = o.optString("tag");
                final JSONArray content = o.optJSONArray("content");
                switch (tag) {
                    case "p":
                        TLRPC.TL_pageBlockParagraph p = new TLRPC.TL_pageBlockParagraph();
                        p.text = trim(parseRichText(o, page));
                        blocks.add(p);
                        break;
                    case "h1":
                    case "h2":
                        TLRPC.TL_pageBlockHeader h1 = new TLRPC.TL_pageBlockHeader();
                        h1.text = trim(parseRichText(o, page));
                        blocks.add(h1);
                        break;
                    case "h3":
                    case "h4":
                    case "h5":
                    case "h6":
                        TLRPC.TL_pageBlockSubheader h = new TLRPC.TL_pageBlockSubheader();
                        h.text = trim(parseRichText(o, page));
                        blocks.add(h);
                        break;
                    case "pre":
                        TLRPC.TL_pageBlockPreformatted pre = new TLRPC.TL_pageBlockPreformatted();
                        TLRPC.TL_textFixed text = new TLRPC.TL_textFixed();
                        text.text = trim(parseRichText(o, page));
                        pre.text = text;
                        pre.language = "";
                        blocks.add(pre);
                        break;
                    case "blockquote":
                        TLRPC.TL_pageBlockBlockquote blockquote = new TLRPC.TL_pageBlockBlockquote();
                        blockquote.text = trim(parseRichText(o, page));
                        TLRPC.TL_textItalic italic = new TLRPC.TL_textItalic();
                        italic.text = blockquote.text;
                        blockquote.text = italic;
                        blocks.add(blockquote);
                        break;
                    case "img":
                        TLRPC.TL_pageBlockPhoto img = parseImage(o, page);
                        if (img != null) {
                            blocks.add(img);
                        }
                        break;
                    case "figure":
                    case "picture":
                        TLRPC.TL_pageBlockPhoto figure = parseFigure(o, page);
                        if (figure != null) {
                            blocks.add(figure);
                        }
                        break;
                    case "table":
                        blocks.add(parseTable(url, o, page));
                        break;
                    case "ul":
                    case "ol":
                        blocks.add(parseList(url, o, page));
                        break;
                    case "hr":
                        blocks.add(new TLRPC.TL_pageBlockDivider());
                        break;
                    case "details":
                        TLRPC.TL_pageBlockDetails details = parseDetails(url, o, page);
                        if (details != null) {
                            blocks.add(details);
                        }
                        break;
                    case "b":
                    case "strong":
                    case "i":
                    case "s":
                    case "a":
                    case "code":
                    case "mark":
                    case "sub":
                    case "sup":
                    case "span":
                        JSONArray arr = new JSONArray();
                        arr.put(o);

                        TLRPC.TL_pageBlockParagraph p2 = new TLRPC.TL_pageBlockParagraph();
                        p2.text = parseRichText(arr, page);
                        blocks.add(p2);
                        break;
                    default:
                        if (content != null) {
                            ArrayList<TLRPC.PageBlock> subblocks = parsePageBlocks(url, content, page);
                            blocks.addAll(subblocks);
                        }
                        break;
                }
            }
        }
        return blocks;
    }

    public static TLRPC.RichText applyAnchor(TLRPC.RichText text, JSONObject tag) {
        if (tag == null) return text;
        final String id = tag.optString("id");
        if (TextUtils.isEmpty(id)) return text;
        TLRPC.TL_textAnchor anchor = new TLRPC.TL_textAnchor();
        anchor.text = text;
        anchor.name = id;
        return anchor;
    }

    @Nullable
    public TLRPC.TL_pageBlockPhoto parseFigure(JSONObject figure, TLRPC.TL_page page) throws JSONException {
        final JSONArray content = figure.optJSONArray("content");
        TLRPC.TL_pageBlockPhoto block = null;
        TLRPC.RichText caption = null;
        final ArrayList<String> urls = new ArrayList<>();
        for (int i = 0; i < content.length(); ++i) {
            Object o = content.get(i);
            if (o instanceof JSONObject) {
                JSONObject element = (JSONObject) o;
                final String tag = element.optString("tag");
                if ("figurecaption".equalsIgnoreCase(tag) || "caption".equalsIgnoreCase(tag)) {
                    caption = trim(parseRichText(element, page));
                } else if ("img".equalsIgnoreCase(tag)) {
                    block = parseImage(element, page);
                } else if ("source".equalsIgnoreCase(tag)) {
                    String src = element.optString("src");
                    if (!TextUtils.isEmpty(src)) {
                        urls.add(src);
                    } else {
                        src = element.optString("srcset");
                        if (!TextUtils.isEmpty(src)) {
                            String[] sets = src.split(",");
                            for (int j = 0; j < sets.length; ++j) {
                                String set = sets[j].trim();
                                set = set.split(" ")[0];
                                set = set.trim();
                                urls.add(set);
                            }
                        }
                    }
                }
            }
        }
        if (block == null) return null;
        if (caption != null) {
            block.caption = new TLRPC.TL_pageCaption();
            block.caption.text = caption;
            block.caption.credit = new TLRPC.TL_textEmpty();
        }
        WebPhoto photo = null;
        for (int i = 0; i < page.photos.size(); ++i) {
            if (page.photos.get(i) instanceof WebPhoto && page.photos.get(i).id == block.photo_id) {
                photo = ((WebPhoto) page.photos.get(i));
                break;
            }
        }
        if (photo != null) {
            photo.urls.addAll(urls);
        }
        return block;
    }

    public TLRPC.TL_pageBlockPhoto parseImage(JSONObject img, TLRPC.TL_page page) {
        TLRPC.TL_pageBlockPhoto block = new TLRPC.TL_pageBlockPhoto();
        block.caption = new TLRPC.TL_pageCaption();
        final String alt = img.optString("alt");
        if (alt != null) {
            block.caption.text = trim(parseRichText(alt));
            block.caption.credit = trim(parseRichText(""));
        }
        final String src = img.optString("src");
        if (src == null) return null;

        WebPhoto photo = new WebPhoto();
        photo.instantView = this;
        photo.id = -1 - page.photos.size();
        photo.url = src;
        photo.urls.add(src);
        try {
            photo.w = Integer.parseInt(img.optString("width"));
        } catch (Exception ignore) {}
        try {
            photo.h = Integer.parseInt(img.optString("height"));
        } catch (Exception ignore) {}
        if (photo.w == 0) photo.w = photo.h;
        if (photo.h == 0) photo.h = photo.w;
        block.photo_id = photo.id;
        block.url = src;
        page.photos.add(photo);

        return block;
    }

    public TLRPC.TL_textImage parseInlineImage(JSONObject o, TLRPC.TL_page page) {
        TLRPC.TL_textImage img = new TLRPC.TL_textImage();
        final String src = o.optString("src");
        if (src == null) {
            return null;
        }
        WebPhoto photo = new WebPhoto();
        photo.instantView = this;
        photo.id = -1 - page.photos.size();
        photo.url = src;
        photo.urls.add(src);
        try {
            photo.w = Integer.parseInt(o.optString("width"));
        } catch (Exception ignore) {}
        try {
            photo.h = Integer.parseInt(o.optString("height"));
        } catch (Exception ignore) {}
        img.url = src;
        page.photos.add(photo);
        if (photo.w == 0) photo.w = photo.h;
        if (photo.h == 0) photo.h = photo.w;

        try {
            img.w = Integer.parseInt(o.optString("width"));
        } catch (Exception ignore) {}
        try {
            img.h = Integer.parseInt(o.optString("height"));
        } catch (Exception ignore) {}
        if (img.w == 0) img.w = img.h;
        if (img.h == 0) img.h = img.w;
        img.photo_id = photo.id;
        return img;
    }

    public TLRPC.TL_pageBlockDetails parseDetails(String url, JSONObject details, TLRPC.TL_page page) throws JSONException {
        final TLRPC.TL_pageBlockDetails block = new TLRPC.TL_pageBlockDetails();
        final JSONArray content = details.optJSONArray("content");
        if (content == null) return null;
        for (int j = 0; j < content.length(); ++j) {
            Object o2 = content.get(j);
            if (o2 instanceof JSONObject) {
                final JSONObject obj = (JSONObject) o2;
                final String tag = obj.optString("tag");
                if ("summary".equals(tag)) {
                    block.title = trim(parseRichText(obj, page));
                    content.remove(j);
                    break;
                }
            }
        }
        block.blocks.addAll(parsePageBlocks(url, content, page));
        block.open = details.has("open");
        return block;
    }

    public TLRPC.RichText parseRichText(JSONObject tag, TLRPC.TL_page page) throws JSONException {
        TLRPC.RichText text = applyAnchor(parseRichText(tag.getJSONArray("content"), page), tag);
        if (tag.has("bold")) {
            TLRPC.TL_textBold bold = new TLRPC.TL_textBold();
            bold.text = text;
            text = bold;
        }
        if (tag.has("italic")) {
            TLRPC.TL_textItalic italic = new TLRPC.TL_textItalic();
            italic.text = text;
            text = italic;
        }
        return text;
    }

    public TLRPC.RichText parseRichText(JSONArray json, TLRPC.TL_page page) throws JSONException {
        ArrayList<TLRPC.RichText> texts = new ArrayList<>();
        for (int i = 0; i < json.length(); ++i) {
            Object obj = json.get(i);
            if (obj instanceof String) {
                texts.add(parseRichText((String) obj));
            } else {
                JSONObject o = (JSONObject) obj;
                final String tag = o.optString("tag");
                TLRPC.RichText text;
                switch (tag) {
                    case "b":
                    case "strong":
                        TLRPC.TL_textBold bold = new TLRPC.TL_textBold();
                        bold.text = parseRichText(o, page);
                        text = bold;
                        break;
                    case "i":
                        TLRPC.TL_textItalic italic = new TLRPC.TL_textItalic();
                        italic.text = parseRichText(o, page);
                        text = italic;
                        break;
                    case "s":
                        TLRPC.TL_textStrike strike = new TLRPC.TL_textStrike();
                        strike.text = parseRichText(o, page);
                        text = strike;
                        break;
                    case "p":
                        if (!texts.isEmpty()) {
                            addNewLine(texts.get(texts.size() - 1));
                        }
                        text = parseRichText(o, page);
                        break;
                    case "a":
                        final String href = o.optString("href");
                        if (href == null) {
                            text = parseRichText(o, page);
                        } else if (href.startsWith("tel:")) {
                            TLRPC.TL_textPhone phoneLink = new TLRPC.TL_textPhone();
                            phoneLink.phone = href.substring(4);
                            phoneLink.text = parseRichText(o, page);
                            text = phoneLink;
                        } else if (href.startsWith("mailto:")) {
                            TLRPC.TL_textEmail emailLink = new TLRPC.TL_textEmail();
                            emailLink.email = href.substring(7);
                            emailLink.text = parseRichText(o, page);
                            text = emailLink;
                        } else {
                            TLRPC.TL_textUrl urlLink = new TLRPC.TL_textUrl();
                            urlLink.url = href;
                            urlLink.text = parseRichText(o, page);
                            text = urlLink;
                        }
                        break;
                    case "pre":
                    case "code":
                        TLRPC.TL_textFixed code = new TLRPC.TL_textFixed();
                        code.text = parseRichText(o, page);
                        text = code;
                        break;
                    case "mark":
                        TLRPC.TL_textMarked marked = new TLRPC.TL_textMarked();
                        marked.text = parseRichText(o, page);
                        text = marked;
                        break;
                    case "sub":
                        TLRPC.TL_textSubscript sub = new TLRPC.TL_textSubscript();
                        sub.text = parseRichText(o, page);
                        text = sub;
                        break;
                    case "sup":
                        TLRPC.TL_textSuperscript sup = new TLRPC.TL_textSuperscript();
                        sup.text = parseRichText(o, page);
                        text = sup;
                        break;
                    case "img":
                        if (!texts.isEmpty()) {
                            addLastSpace(texts.get(texts.size() - 1));
                        }
                        text = parseInlineImage(o, page);
                        break;
                    case "br":
                        text = null;
                        if (!texts.isEmpty()) {
                            addNewLine(texts.get(texts.size() - 1));
                        }
                        break;
                    default:
                        text = parseRichText(o, page);
                        break;
                }
                if (text != null) {
                    text = applyAnchor(text, o);
                    texts.add(text);
                }
            }
        }
        if (texts.isEmpty()) {
            return new TLRPC.TL_textEmpty();
        } else if (texts.size() == 1) {
            return texts.get(0);
        } else {
            TLRPC.TL_textConcat concat = new TLRPC.TL_textConcat();
            concat.texts = texts;
            return concat;
        }
    }

    public static TLRPC.RichText addLastSpace(TLRPC.RichText text) {
        if (text == null) return text;
        if (text.text != null) {
            addLastSpace(text.text);
        } else if (!text.texts.isEmpty()) {
            addLastSpace(text.texts.get(text.texts.size() - 1));
        } else if (text instanceof TLRPC.TL_textPlain) {
            final TLRPC.TL_textPlain textPlain = (TLRPC.TL_textPlain) text;
            if (textPlain.text != null && !textPlain.text.endsWith(" "))
                textPlain.text += ' ';
        }
        return text;
    }

    public static TLRPC.RichText addNewLine(TLRPC.RichText text) {
        if (text == null) return text;
        if (text.text != null) {
            addNewLine(text.text);
        } else if (!text.texts.isEmpty()) {
            addNewLine(text.texts.get(text.texts.size() - 1));
        } else if (text instanceof TLRPC.TL_textPlain) {
            ((TLRPC.TL_textPlain) text).text += '\n';
        }
        return text;
    }

    public static TLRPC.RichText trimStart(TLRPC.RichText text) {
        if (text == null) return text;
        if (text.text != null) {
            trimStart(text.text);
        } else if (!text.texts.isEmpty()) {
            trimStart(text.texts.get(0));
        } else if (text instanceof TLRPC.TL_textPlain && ((TLRPC.TL_textPlain) text).text != null) {
            ((TLRPC.TL_textPlain) text).text = ((TLRPC.TL_textPlain) text).text.replaceAll("^\\s+", "");
        }
        return text;
    }

    public static TLRPC.RichText trim(TLRPC.RichText text) {
        if (text == null) return text;
        if (text.text != null) {
            trim(text.text);
        } else if (text.texts.size() == 1) {
            trim(text.texts.get(0));
        } else if (!text.texts.isEmpty()) {
            trimStart(text.texts.get(0));
            trimEnd(text.texts.get(text.texts.size() - 1));
        } else if (text instanceof TLRPC.TL_textPlain && ((TLRPC.TL_textPlain) text).text != null) {
            ((TLRPC.TL_textPlain) text).text = ((TLRPC.TL_textPlain) text).text.trim();
        }
        return text;
    }

    public static TLRPC.RichText trimEnd(TLRPC.RichText text) {
        if (text == null) return text;
        if (text.text != null) {
            trimEnd(text.text);
        } else if (!text.texts.isEmpty()) {
            trimEnd(text.texts.get(text.texts.size() - 1));
        } else if (text instanceof TLRPC.TL_textPlain && ((TLRPC.TL_textPlain) text).text != null) {
            ((TLRPC.TL_textPlain) text).text = ((TLRPC.TL_textPlain) text).text.replaceAll("\\s+$", "");
        }
        return text;
    }

    public static TLRPC.RichText parseRichText(String str) {
        TLRPC.TL_textPlain richText = new TLRPC.TL_textPlain();
        richText.text = str;
        return richText;
    }

    public TLRPC.TL_pageBlockTable parseTable(String url, JSONObject json, TLRPC.TL_page page) throws JSONException {
        TLRPC.TL_pageBlockTable table = new TLRPC.TL_pageBlockTable();
        table.bordered = true;
        table.striped = true;

        String title = json.optString("title");
        if (title == null) title = "";
        table.title = trim(applyAnchor(parseRichText(title), json));

        final JSONArray content = json.getJSONArray("content");
        table.rows.addAll(parseTableRows(url, content, page));

        return table;
    }

    public ArrayList<TLRPC.TL_pageTableRow> parseTableRows(String url, JSONArray table_content, TLRPC.TL_page page) throws JSONException {
        final ArrayList<TLRPC.TL_pageTableRow> rows = new ArrayList<>();
        ArrayList<Integer> rowuntil = new ArrayList<>();
        for (int y = 0; y < table_content.length(); ++y) {
            Object o = table_content.get(y);
            if (!(o instanceof JSONObject)) continue;

            JSONObject tr_json = (JSONObject) o;
            String tr_tag = tr_json.optString("tag");
            if ("tr".equals(tr_tag)) {
                final TLRPC.TL_pageTableRow row = parseTableRow(url, tr_json, page);
                rows.add(row);
            } else {
                JSONArray content = tr_json.optJSONArray("content");
                if (content != null) {
                    rows.addAll(parseTableRows(url, content, page));
                }
            }
        }
        return rows;
    }

    public TLRPC.TL_pageTableRow parseTableRow(String url, JSONObject tr_json, TLRPC.TL_page page) throws JSONException {
        TLRPC.TL_pageTableRow row = new TLRPC.TL_pageTableRow();

        JSONArray tr_content = tr_json.getJSONArray("content");
        for (int x = 0; x < tr_content.length(); ++x) {
            Object o2 = tr_content.get(x);
            if (!(o2 instanceof JSONObject)) continue;

            JSONObject td_json = (JSONObject) o2;
            String td_tag = td_json.optString("tag");
            if (td_tag == null || !("td".equals(td_tag) || "th".equals(td_tag))) continue;

            TLRPC.TL_pageTableCell cell = new TLRPC.TL_pageTableCell();
            cell.header = "th".equals(td_tag);
            try {
                cell.colspan = Integer.parseInt(td_json.optString("colspan"));
                cell.flags |= 2;
            } catch (Exception ignore) {}
            try {
                cell.rowspan = Integer.parseInt(td_json.optString("rowspan"));
                cell.flags |= 4;
            } catch (Exception ignore) {}
            cell.text = trim(parseRichText(td_json.getJSONArray("content"), page));
            if (td_json.has("bold") || cell.header) {
                TLRPC.TL_textBold bold = new TLRPC.TL_textBold();
                bold.text = cell.text;
                cell.text = bold;
            }
            if (td_json.has("italic")) {
                TLRPC.TL_textItalic italic = new TLRPC.TL_textItalic();
                italic.text = cell.text;
                cell.text = italic;
            }
            cell.align_center = td_json.has("xcenter");

            row.cells.add(cell);
        }

        return row;
    }

    public boolean isInline(JSONArray content) throws JSONException {
        final List<String> inlineTags = Arrays.asList("b", "strong", "span", "img", "i", "s", "a", "code", "mark", "sub", "sup");
        boolean isInline = true;
        for (int j = 0; j < content.length(); ++j) {
            Object o = content.get(j);
            if (o instanceof String) continue;
            if (o instanceof JSONObject) {
                JSONObject obj = (JSONObject) o;
                final String tagName = obj.optString("tag");
                if (inlineTags.contains(tagName))
                    continue;
                if ("div".equalsIgnoreCase(tagName) || "span".equalsIgnoreCase(tagName)) {
                    JSONArray content2 = obj.optJSONArray("content");
                    if (!isInline(content2))
                        return false;
                }
            }
            return false;
        }
        return true;
    }

    public TLRPC.PageBlock parseList(String url, JSONObject json, TLRPC.TL_page page) throws JSONException {
        final String tag = json.optString("tag");
        if ("ol".equals(tag)) {
            TLRPC.TL_pageBlockOrderedList list = new TLRPC.TL_pageBlockOrderedList();
            final JSONArray content = json.getJSONArray("content");
            for (int i = 0; i < content.length(); ++i) {
                Object o = content.get(i);
                if (!(o instanceof JSONObject)) continue;
                JSONObject li = (JSONObject) o;
                if (!"li".equals(li.optString("tag"))) continue;

                final JSONArray li_content = li.optJSONArray("content");
                final boolean isInline = isInline(li_content);

                if (isInline) {
                    final TLRPC.TL_pageListOrderedItemText item = new TLRPC.TL_pageListOrderedItemText();
                    item.text = parseRichText(li_content, page);
                    list.items.add(item);
                } else {
                    final TLRPC.TL_pageListOrderedItemBlocks item = new TLRPC.TL_pageListOrderedItemBlocks();
                    item.blocks.addAll(parsePageBlocks(url, li_content, page));
                    list.items.add(item);
                }
            }
            return list;
        } else {
            final TLRPC.TL_pageBlockList list = new TLRPC.TL_pageBlockList();
            final JSONArray content = json.getJSONArray("content");
            for (int i = 0; i < content.length(); ++i) {
                Object o = content.get(i);
                if (!(o instanceof JSONObject)) continue;
                JSONObject li = (JSONObject) o;
                if (!"li".equals(li.optString("tag"))) continue;

                final JSONArray li_content = li.optJSONArray("content");
                final boolean isInline = isInline(li_content);

                if (isInline) {
                    final TLRPC.TL_pageListItemText item = new TLRPC.TL_pageListItemText();
                    item.text = parseRichText(li_content, page);
                    list.items.add(item);
                } else {
                    final TLRPC.TL_pageListItemBlocks item = new TLRPC.TL_pageListItemBlocks();
                    item.blocks.addAll(parsePageBlocks(url, li_content, page));
                    list.items.add(item);
                }
            }
            return list;
        }
    }

    public static class Loader {

        private final int currentAccount;

        private boolean started;
        private boolean cancelled;

        public String currentUrl;
        public float currentProgress;
        public boolean currentIsLoaded;

        private boolean gotRemote;
        private TLRPC.WebPage remotePage;
        private boolean gotLocal;
        private TLRPC.WebPage localPage;

        private int reqId;
        private Runnable cancelLocal;

        public Loader(int currentAccount) {
            this.currentAccount = currentAccount;
        }

        public void retryLocal(BotWebViewContainer.MyWebView webView) {
            if (cancelled) return;
            if (localPage != null) {
                WebInstantView.recycle(localPage);
                localPage = null;
            }
            gotLocal = false;
            currentUrl = webView.getUrl();
            currentProgress = webView.getProgress();
            currentIsLoaded = webView.isPageLoaded();
            if (cancelLocal != null) {
                cancelLocal.run();
            }
            cancelLocal = WebInstantView.generate(webView, false, instant -> {
                cancelLocal = null;
                gotLocal = true;
                if (localPage != null) {
                    WebInstantView.recycle(localPage);
                }
                localPage = instant.webpage;
                notifyUpdate();
            });
        }

        public void start(BotWebViewContainer.MyWebView webView) {
            if (started) return;
            started = true;

            currentUrl = webView.getUrl();
            currentProgress = webView.getProgress();
            currentIsLoaded = webView.isPageLoaded();

            cancelLocal = WebInstantView.generate(webView, false, instant -> {
                cancelLocal = null;
                gotLocal = true;
                if (localPage != null) {
                    WebInstantView.recycle(localPage);
                }
                localPage = instant.webpage;
                notifyUpdate();
            });

            TLRPC.TL_messages_getWebPage req = new TLRPC.TL_messages_getWebPage();
            req.url = currentUrl;
            req.hash = 0;
            reqId = ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, err) -> AndroidUtilities.runOnUIThread(() -> {
                gotRemote = true;
                if (response instanceof TLRPC.TL_messages_webPage) {
                    TLRPC.TL_messages_webPage res = (TLRPC.TL_messages_webPage) response;
                    MessagesController.getInstance(currentAccount).putUsers(res.users, false);
                    MessagesController.getInstance(currentAccount).putChats(res.chats, false);
                    remotePage = res.webpage;
                } else if (response instanceof TLRPC.TL_webPage && ((TLRPC.TL_webPage) response).cached_page instanceof TLRPC.TL_page) {
                    remotePage = (TLRPC.TL_webPage) response;
                } else {
                    remotePage = null;
                }
                if (remotePage != null && remotePage.cached_page == null) {
                    remotePage = null;
                }
                if (!SharedConfig.onlyLocalInstantView && remotePage != null && cancelLocal != null) {
                    cancelLocal.run();
                }
                notifyUpdate();
            }));
        }

        public boolean isDone() {
            return gotRemote && gotLocal || remotePage != null || localPage != null || cancelled;
        }

        public TLRPC.WebPage getWebPage() {
            if (!SharedConfig.onlyLocalInstantView && remotePage != null) return remotePage;
            if (localPage != null) return localPage;
            return null;
        }

        public void cancel() {
            if (cancelled) return;
            cancelled = true;
            if (!gotRemote) {
                ConnectionsManager.getInstance(currentAccount).cancelRequest(reqId, true);
            }
            if (!gotLocal && cancelLocal != null) {
                cancelLocal.run();
            }
        }

        public void recycle() {
            if (localPage != null) {
                WebInstantView.recycle(localPage);
                localPage = null;
            }
        }

        private final ArrayList<Runnable> listeners = new ArrayList<>();
        public Runnable listen(Runnable whenUpdated) {
            listeners.add(whenUpdated);
            return () -> {
                listeners.remove(whenUpdated);
            };
        }

        private void notifyUpdate() {
            for (Runnable listener : listeners) {
                listener.run();
            }
        }

    }

    public static TLRPC.RichText filterRecursiveAnchorLinks(TLRPC.RichText text, String url, String anchor) {
        if (text == null) return text;
        if (text instanceof TLRPC.TL_textConcat) {
            TLRPC.TL_textConcat textConcat = (TLRPC.TL_textConcat) text;
            TLRPC.TL_textConcat newTextConcat = new TLRPC.TL_textConcat();
            for (int i = 0; i < textConcat.texts.size(); ++i) {
                TLRPC.RichText child = textConcat.texts.get(i);
                child = filterRecursiveAnchorLinks(child, url, anchor);
                if (child != null) {
                    newTextConcat.texts.add(child);
                }
            }
            return newTextConcat;
        } else if (text instanceof TLRPC.TL_textUrl) {
            TLRPC.TL_textUrl textUrl = (TLRPC.TL_textUrl) text;
            if (textUrl.url != null && (textUrl.url.toLowerCase().equals("#" + anchor) || TextUtils.equals(textUrl.url.toLowerCase(), url + "#" + anchor))) {
                return null;
            }
        }
        return text;
    }

}
