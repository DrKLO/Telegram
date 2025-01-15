/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Components;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.SurfaceTexture;
import android.media.AudioManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.Base64;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.annotation.Keep;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.Bitmaps;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

public class WebPlayerView extends ViewGroup implements VideoPlayer.VideoPlayerDelegate, AudioManager.OnAudioFocusChangeListener {

    public interface WebPlayerViewDelegate {
        void onInitFailed();
        TextureView onSwitchToFullscreen(View controlsView, boolean fullscreen, float aspectRatio, int rotation, boolean byButton);
        TextureView onSwitchInlineMode(View controlsView, boolean inline, int width, int height, int rotation, boolean animated);
        void onInlineSurfaceTextureReady();
        void prepareToSwitchInlineMode(boolean inline, Runnable switchInlineModeRunnable, float aspectRatio, boolean animated);
        void onSharePressed();
        void onPlayStateChanged(WebPlayerView playerView, boolean playing);
        void onVideoSizeChanged(float aspectRatio, int rotation);
        ViewGroup getTextureViewContainer();
        boolean checkInlinePermissions();
    }

    private static int lastContainerId = 4001;
    private int fragment_container_id = lastContainerId++;

    private VideoPlayer videoPlayer;
    private WebView webView;
    private String interfaceName;
    private AspectRatioFrameLayout aspectRatioFrameLayout;
    private TextureView textureView;
    private ImageView textureImageView;
    private ViewGroup textureViewContainer;
    private Bitmap currentBitmap;
    private TextureView changedTextureView;
    private int waitingForFirstTextureUpload;
    private boolean isAutoplay;
    private WebPlayerViewDelegate delegate;
    private boolean initFailed;
    private boolean initied;
    private String playVideoUrl;
    private String playVideoType;
    private String playAudioUrl;
    private String playAudioType;
    private String currentYoutubeId;

    private boolean isStream;

    private boolean allowInlineAnimation = Build.VERSION.SDK_INT >= 21;

    private static final int AUDIO_NO_FOCUS_NO_DUCK = 0;
    private static final int AUDIO_NO_FOCUS_CAN_DUCK = 1;
    private static final int AUDIO_FOCUSED  = 2;
    private boolean hasAudioFocus;
    private int audioFocus;
    private boolean resumeAudioOnFocusGain;

    private long lastUpdateTime;
    private boolean firstFrameRendered;
    private float currentAlpha;

    private int seekToTime;

    private boolean drawImage;

    private Paint backgroundPaint = new Paint();

    private AsyncTask currentTask;

    private boolean changingTextureView;
    private boolean inFullscreen;
    private boolean isInline;
    private boolean isCompleted;
    private boolean isLoading;
    private boolean switchingInlineMode;

    private RadialProgressView progressView;
    private ImageView fullscreenButton;
    private ImageView playButton;
    private ImageView inlineButton;
    private ImageView shareButton;
    private AnimatorSet progressAnimation;

    private ControlsView controlsView;

    private int videoWidth, videoHeight;

    private Runnable progressRunnable = new Runnable() {
        @Override
        public void run() {
            if (videoPlayer == null || !videoPlayer.isPlaying()) {
                return;
            }
            controlsView.setProgress((int) (videoPlayer.getCurrentPosition() / 1000));
            controlsView.setBufferedProgress((int) (videoPlayer.getBufferedPosition() / 1000));

            AndroidUtilities.runOnUIThread(progressRunnable, 1000);
        }
    };

    private static final Pattern youtubeIdRegex = Pattern.compile("(?:youtube(?:-nocookie)?\\.com/(?:[^/\\n\\s]+/\\S+/|(?:v|e(?:mbed)?)/|\\S*?[?&]v=)|youtu\\.be/)([a-zA-Z0-9_-]{11})");
    private static final Pattern vimeoIdRegex = Pattern.compile("https?://(?:(?:www|(player))\\.)?vimeo(pro)?\\.com/(?!(?:channels|album)/[^/?#]+/?(?:$|[?#])|[^/]+/review/|ondemand/)(?:.*?/)?(?:(?:play_redirect_hls|moogaloop\\.swf)\\?clip_id=)?(?:videos?/)?([0-9]+)(?:/[\\da-f]+)?/?(?:[?&].*)?(?:[#].*)?$");
    private static final Pattern coubIdRegex = Pattern.compile("(?:coub:|https?://(?:coub\\.com/(?:view|embed|coubs)/|c-cdn\\.coub\\.com/fb-player\\.swf\\?.*\\bcoub(?:ID|id)=))([\\da-z]+)");
    private static final Pattern aparatIdRegex = Pattern.compile("^https?://(?:www\\.)?aparat\\.com/(?:v/|video/video/embed/videohash/)([a-zA-Z0-9]+)");
    private static final Pattern twitchClipIdRegex = Pattern.compile("https?://clips\\.twitch\\.tv/(?:[^/]+/)*([^/?#&]+)");
    private static final Pattern twitchStreamIdRegex = Pattern.compile("https?://(?:(?:www\\.)?twitch\\.tv/|player\\.twitch\\.tv/\\?.*?\\bchannel=)([^/#?]+)");

    private static final Pattern aparatFileListPattern = Pattern.compile("fileList\\s*=\\s*JSON\\.parse\\('([^']+)'\\)");

    private static final Pattern twitchClipFilePattern = Pattern.compile("clipInfo\\s*=\\s*(\\{[^']+\\});");

    private static final Pattern stsPattern = Pattern.compile("\"sts\"\\s*:\\s*(\\d+)");
    private static final Pattern jsPattern = Pattern.compile("\"assets\":.+?\"js\":\\s*(\"[^\"]+\")");
    private static final Pattern sigPattern = Pattern.compile("\\.sig\\|\\|([a-zA-Z0-9$]+)\\(");
    private static final Pattern sigPattern2 = Pattern.compile("[\"']signature[\"']\\s*,\\s*([a-zA-Z0-9$]+)\\(");
    private static final Pattern stmtVarPattern = Pattern.compile("var\\s");
    private static final Pattern stmtReturnPattern = Pattern.compile("return(?:\\s+|$)");
    private static final Pattern exprParensPattern = Pattern.compile("[()]");
    private static final Pattern playerIdPattern = Pattern.compile(".*?-([a-zA-Z0-9_-]+)(?:/watch_as3|/html5player(?:-new)?|(?:/[a-z]{2}_[A-Z]{2})?/base)?\\.([a-z]+)$");
    private static final String exprName = "[a-zA-Z_$][a-zA-Z_$0-9]*";

    private static abstract class function {
        public abstract Object run(Object[] args);
    }

    private static class JSExtractor {

        ArrayList<String> codeLines = new ArrayList<>();

        private String jsCode;
        private String[] operators = {"|", "^", "&", ">>", "<<", "-", "+", "%", "/", "*"};
        private String[] assign_operators = {"|=", "^=", "&=", ">>=", "<<=", "-=", "+=", "%=", "/=", "*=", "="};

        public JSExtractor(String js) {
            jsCode = js;
        }

        private void interpretExpression(String expr, HashMap<String, String> localVars, int allowRecursion) throws Exception {
            expr = expr.trim();
            if (TextUtils.isEmpty(expr)) {
                return;
            }
            if (expr.charAt(0) == '(') {
                int parens_count = 0;
                Matcher matcher = exprParensPattern.matcher(expr);
                while (matcher.find()) {
                    String group = matcher.group(0);
                    if (group.indexOf('0') == '(') {
                        parens_count++;
                    } else {
                        parens_count--;
                        if (parens_count == 0) {
                            String sub_expr = expr.substring(1, matcher.start());
                            interpretExpression(sub_expr, localVars, allowRecursion);
                            String remaining_expr = expr.substring(matcher.end()).trim();
                            if (TextUtils.isEmpty(remaining_expr)) {
                                return;
                            } else {
                                expr = remaining_expr;
                            }
                            break;
                        }
                    }
                }
                if (parens_count != 0) {
                    throw new Exception(String.format("Premature end of parens in %s", expr));
                }
            }
            for (int a = 0; a < assign_operators.length; a++) {
                String func = assign_operators[a];
                Matcher matcher = Pattern.compile(String.format(Locale.US, "(?x)(%s)(?:\\[([^\\]]+?)\\])?\\s*%s(.*)$", exprName, Pattern.quote(func))).matcher(expr);
                if (!matcher.find()) {
                    continue;
                }
                interpretExpression(matcher.group(3), localVars, allowRecursion - 1);
                String index = matcher.group(2);
                if (!TextUtils.isEmpty(index)) {
                    interpretExpression(index, localVars, allowRecursion);
                } else {
                    localVars.put(matcher.group(1), "");
                }
                return;
            }

            try {
                Integer.parseInt(expr);
                return;
            } catch (Exception e) {
                //ignore
            }

            Matcher matcher = Pattern.compile(String.format(Locale.US, "(?!if|return|true|false)(%s)$", exprName)).matcher(expr);
            if (matcher.find()) {
                return;
            }

            if (expr.charAt(0) == '"' && expr.charAt(expr.length() - 1) == '"') {
                return;
            }
            try {
                new JSONObject(expr).toString();
                return;
            } catch (Exception e) {
                //ignore
            }

            matcher = Pattern.compile(String.format(Locale.US, "(%s)\\[(.+)\\]$", exprName)).matcher(expr);
            if (matcher.find()) {
                String val = matcher.group(1);
                interpretExpression(matcher.group(2), localVars, allowRecursion - 1);
                return;
            }

            matcher = Pattern.compile(String.format(Locale.US, "(%s)(?:\\.([^(]+)|\\[([^]]+)\\])\\s*(?:\\(+([^()]*)\\))?$", exprName)).matcher(expr);
            if (matcher.find()) {
                String variable = matcher.group(1);
                String m1 = matcher.group(2);
                String m2 = matcher.group(3);
                String member = (TextUtils.isEmpty(m1) ? m2 : m1).replace("\"", "");
                String arg_str = matcher.group(4);
                if (localVars.get(variable) == null) {
                    extractObject(variable);
                }
                if (arg_str == null) {
                    return;
                }
                if (expr.charAt(expr.length() - 1) != ')') {
                    throw new Exception("last char not ')'");
                }
                String[] argvals;
                if (arg_str.length() != 0) {
                    String[] args = arg_str.split(",");
                    for (int a = 0; a < args.length; a++) {
                        interpretExpression(args[a], localVars, allowRecursion);
                    }
                }
                return;
            }

            matcher = Pattern.compile(String.format(Locale.US, "(%s)\\[(.+)\\]$", exprName)).matcher(expr);
            if (matcher.find()) {
                Object val = localVars.get(matcher.group(1));
                interpretExpression(matcher.group(2), localVars, allowRecursion - 1);
                return;
            }

            for (int a = 0; a < operators.length; a++) {
                String func = operators[a];
                matcher = Pattern.compile(String.format(Locale.US, "(.+?)%s(.+)", Pattern.quote(func))).matcher(expr);
                if (!matcher.find()) {
                    continue;
                }
                boolean[] abort = new boolean[1];
                interpretStatement(matcher.group(1), localVars, abort, allowRecursion - 1);
                if (abort[0]) {
                    throw new Exception(String.format("Premature left-side return of %s in %s", func, expr));
                }
                interpretStatement(matcher.group(2), localVars, abort, allowRecursion - 1);
                if (abort[0]) {
                    throw new Exception(String.format("Premature right-side return of %s in %s", func, expr));
                }
            }

            matcher = Pattern.compile(String.format(Locale.US, "^(%s)\\(([a-zA-Z0-9_$,]*)\\)$", exprName)).matcher(expr);
            if (matcher.find()) {
                String fname = matcher.group(1);
                extractFunction(fname);
            }
            throw new Exception(String.format("Unsupported JS expression %s", expr));
        }

        private void interpretStatement(String stmt, HashMap<String, String> localVars, boolean[] abort, int allowRecursion) throws Exception {
            if (allowRecursion < 0) {
                throw new Exception("recursion limit reached");
            }
            abort[0] = false;
            stmt = stmt.trim();
            Matcher matcher = stmtVarPattern.matcher(stmt);
            String expr;
            if (matcher.find()) {
                expr = stmt.substring(matcher.group(0).length());
            } else {
                matcher = stmtReturnPattern.matcher(stmt);
                if (matcher.find()) {
                    expr = stmt.substring(matcher.group(0).length());
                    abort[0] = true;
                } else {
                    expr = stmt;
                }
            }
            interpretExpression(expr, localVars, allowRecursion);
        }

        private HashMap<String, Object> extractObject(String objname) throws Exception {
            String funcName =  "(?:[a-zA-Z$0-9]+|\"[a-zA-Z$0-9]+\"|'[a-zA-Z$0-9]+')";
            HashMap<String, Object> obj = new HashMap<>();
            //                                                                                         ?P<fields>
            Matcher matcher = Pattern.compile(String.format(Locale.US, "(?:var\\s+)?%s\\s*=\\s*\\{\\s*((%s\\s*:\\s*function\\(.*?\\)\\s*\\{.*?\\}(?:,\\s*)?)*)\\}\\s*;", Pattern.quote(objname), funcName)).matcher(jsCode);
            String fields = null;
            while (matcher.find()) {
                String code = matcher.group();
                fields = matcher.group(2);
                if (TextUtils.isEmpty(fields)) {
                    continue;
                }
                if (!codeLines.contains(code)) {
                    codeLines.add(matcher.group());
                }
                break;
            }
            //                          ?P<key>                            ?P<args>     ?P<code>
            matcher = Pattern.compile(String.format("(%s)\\s*:\\s*function\\(([a-z,]+)\\)\\{([^}]+)\\}", funcName)).matcher(fields);
            while (matcher.find()) {
                String[] argnames = matcher.group(2).split(",");
                buildFunction(argnames, matcher.group(3));
            }
            return obj;
        }

        private void buildFunction(String[] argNames, String funcCode) throws Exception {
            HashMap<String, String> localVars = new HashMap<>();
            for (int a = 0; a < argNames.length; a++) {
                localVars.put(argNames[a], "");
            }
            String[] stmts = funcCode.split(";");
            boolean[] abort = new boolean[1];
            for (int a = 0; a < stmts.length; a++) {
                interpretStatement(stmts[a], localVars, abort, 100);
                if (abort[0]) {
                    return;
                }
            }
        }

        private String extractFunction(String funcName) {
            try {
                String quote = Pattern.quote(funcName);
                Pattern funcPattern = Pattern.compile(String.format(Locale.US, "(?x)(?:function\\s+%s|[{;,]\\s*%s\\s*=\\s*function|var\\s+%s\\s*=\\s*function)\\s*\\(([^)]*)\\)\\s*\\{([^}]+)\\}", quote, quote, quote));
                Matcher matcher = funcPattern.matcher(jsCode);
                if (matcher.find()) {
                    String group = matcher.group();
                    if (!codeLines.contains(group)) {
                        codeLines.add(group + ";");
                    }
                    buildFunction(matcher.group(1).split(","), matcher.group(2));
                }
            } catch (Exception e) {
                codeLines.clear();
                FileLog.e(e);
            }
            return TextUtils.join("", codeLines);
        }
    }

    public interface CallJavaResultInterface {
        void jsCallFinished(String value);
    }

    public static class JavaScriptInterface {
        private final CallJavaResultInterface callJavaResultInterface;

        public JavaScriptInterface(CallJavaResultInterface callJavaResult) {
            callJavaResultInterface = callJavaResult;
        }

        @Keep
        @JavascriptInterface
        public void returnResultToJava(String value) {
            callJavaResultInterface.jsCallFinished(value);
        }
    }

    protected String downloadUrlContent(AsyncTask parentTask, String url) {
        return downloadUrlContent(parentTask, url, null, true);
    }

    protected String downloadUrlContent(AsyncTask parentTask, String url, HashMap<String, String> headers, boolean tryGzip) {
        boolean canRetry = true;
        InputStream httpConnectionStream = null;
        boolean done = false;
        StringBuilder result = null;
        URLConnection httpConnection = null;
        try {
            URL downloadUrl = new URL(url);
            httpConnection = downloadUrl.openConnection();
            httpConnection.addRequestProperty("User-Agent", "Mozilla/5.0 (X11; Linux x86_64; rv:10.0) Gecko/20150101 Firefox/47.0 (Chrome)");
            if (tryGzip) {
                httpConnection.addRequestProperty("Accept-Encoding", "gzip, deflate");
            }
            httpConnection.addRequestProperty("Accept-Language", "en-us,en;q=0.5");
            httpConnection.addRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
            httpConnection.addRequestProperty("Accept-Charset", "ISO-8859-1,utf-8;q=0.7,*;q=0.7");
            if (headers != null) {
                for (HashMap.Entry<String, String> entry : headers.entrySet()) {
                    httpConnection.addRequestProperty(entry.getKey(), entry.getValue());
                }
            }
            httpConnection.setConnectTimeout(5000);
            httpConnection.setReadTimeout(5000);
            if (httpConnection instanceof HttpURLConnection) {
                HttpURLConnection httpURLConnection = (HttpURLConnection) httpConnection;
                httpURLConnection.setInstanceFollowRedirects(true);
                int status = httpURLConnection.getResponseCode();
                if (status == HttpURLConnection.HTTP_MOVED_TEMP || status == HttpURLConnection.HTTP_MOVED_PERM || status == HttpURLConnection.HTTP_SEE_OTHER) {
                    String newUrl = httpURLConnection.getHeaderField("Location");
                    String cookies = httpURLConnection.getHeaderField("Set-Cookie");
                    downloadUrl = new URL(newUrl);
                    httpConnection = downloadUrl.openConnection();
                    httpConnection.setRequestProperty("Cookie", cookies);
                    httpConnection.addRequestProperty("User-Agent", "Mozilla/5.0 (X11; Linux x86_64; rv:10.0) Gecko/20150101 Firefox/47.0 (Chrome)");
                    if (tryGzip) {
                        httpConnection.addRequestProperty("Accept-Encoding", "gzip, deflate");
                    }
                    httpConnection.addRequestProperty("Accept-Language", "en-us,en;q=0.5");
                    httpConnection.addRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
                    httpConnection.addRequestProperty("Accept-Charset", "ISO-8859-1,utf-8;q=0.7,*;q=0.7");
                    if (headers != null) {
                        for (HashMap.Entry<String, String> entry : headers.entrySet()) {
                            httpConnection.addRequestProperty(entry.getKey(), entry.getValue());
                        }
                    }
                }
            }
            httpConnection.connect();
            if (tryGzip) {
                try {
                    httpConnectionStream = new GZIPInputStream(httpConnection.getInputStream());
                } catch (Exception e) {
                    try {
                        if (httpConnectionStream != null) {
                            httpConnectionStream.close();
                        }
                    } catch (Exception ignore) {

                    }
                    httpConnection = downloadUrl.openConnection();
                    httpConnection.connect();
                    httpConnectionStream = httpConnection.getInputStream();
                }
            } else {
                httpConnectionStream = httpConnection.getInputStream();
            }
        } catch (Throwable e) {
            if (e instanceof SocketTimeoutException) {
                if (ApplicationLoader.isNetworkOnline()) {
                    canRetry = false;
                }
            } else if (e instanceof UnknownHostException) {
                canRetry = false;
            } else if (e instanceof SocketException) {
                if (e.getMessage() != null && e.getMessage().contains("ECONNRESET")) {
                    canRetry = false;
                }
            } else if (e instanceof FileNotFoundException) {
                canRetry = false;
            }
            FileLog.e(e);
        }

        if (canRetry) {
            try {
                if (httpConnection instanceof HttpURLConnection) {
                    int code = ((HttpURLConnection) httpConnection).getResponseCode();
                    if (code != HttpURLConnection.HTTP_OK && code != HttpURLConnection.HTTP_ACCEPTED && code != HttpURLConnection.HTTP_NOT_MODIFIED) {
                        //canRetry = false;
                    }
                }
            } catch (Exception e) {
                FileLog.e(e);
            }

            if (httpConnectionStream != null) {
                try {
                    byte[] data = new byte[1024 * 32];
                    while (true) {
                        if (parentTask.isCancelled()) {
                            break;
                        }
                        try {
                            int read = httpConnectionStream.read(data);
                            if (read > 0) {
                                if (result == null) {
                                    result = new StringBuilder();
                                }
                                result.append(new String(data, 0, read, "UTF-8"));
                            } else if (read == -1) {
                                done = true;
                                break;
                            } else {
                                break;
                            }
                        } catch (Exception e) {
                            FileLog.e(e);
                            break;
                        }
                    }
                } catch (Throwable e) {
                    FileLog.e(e);
                }
            }

            try {
                if (httpConnectionStream != null) {
                    httpConnectionStream.close();
                }
            } catch (Throwable e) {
                FileLog.e(e);
            }
        }
        return done ? result.toString() : null;
    }

    private class YoutubeVideoTask extends AsyncTask<Void, Void, String[]> {

        private String videoId;
        private boolean canRetry = true;
        private CountDownLatch countDownLatch = new CountDownLatch(1);
        private String[] result = new String[2];
        private String sig;

        public YoutubeVideoTask(String vid) {
            videoId = vid;
        }

        @Override
        protected String[] doInBackground(Void... voids) {
            Matcher matcher;
            String embedCode = downloadUrlContent(this, "https://www.youtube.com/embed/" + videoId);
            if (isCancelled()) {
                return null;
            }
            String params = "video_id=" + videoId + "&ps=default&gl=US&hl=en";
            try {
                params += "&eurl=" + URLEncoder.encode("https://youtube.googleapis.com/v/" + videoId, "UTF-8");
            } catch (Exception e) {
                FileLog.e(e);
            }
            if (embedCode != null) {
                matcher = stsPattern.matcher(embedCode);
                if (matcher.find()) {
                    params += "&sts=" + embedCode.substring(matcher.start() + 6, matcher.end());
                } else {
                    params += "&sts=";
                }
            }
            result[1] = "dash";

            boolean encrypted = false;
            String otherUrl = null;
            String[] extra = new String[]{"", "&el=leanback", "&el=embedded", "&el=detailpage", "&el=vevo"};
            for (int i = 0; i < extra.length; i++) {
                String videoInfo = downloadUrlContent(this, "https://www.youtube.com/get_video_info?" + params + extra[i]);
                if (isCancelled()) {
                    return null;
                }
                boolean exists = false;
                String hls = null;
                boolean isLive = false;
                if (videoInfo != null) {
                    String[] args = videoInfo.split("&");
                    for (int a = 0; a < args.length; a++) {
                        if (args[a].startsWith("dashmpd")) {
                            exists = true;
                            String[] args2 = args[a].split("=");
                            if (args2.length == 2) {
                                try {
                                    result[0] = URLDecoder.decode(args2[1], "UTF-8");
                                } catch (Exception e) {
                                    FileLog.e(e);
                                }
                            }
                        } else if (args[a].startsWith("url_encoded_fmt_stream_map")) {
                            String[] args2 = args[a].split("=");
                            if (args2.length == 2) {
                                try {
                                    String[] args3 = URLDecoder.decode(args2[1], "UTF-8").split("[&,]");
                                    String currentUrl = null;
                                    boolean isMp4 = false;
                                    for (int c = 0; c < args3.length; c++) {
                                        String[] args4 = args3[c].split("=");
                                        if (args4[0].startsWith("type")) {
                                            String type = URLDecoder.decode(args4[1], "UTF-8");
                                            if (type.contains("video/mp4")) {
                                                isMp4 = true;
                                            }
                                        } else if (args4[0].startsWith("url")) {
                                            currentUrl = URLDecoder.decode(args4[1], "UTF-8");
                                        } else if (args4[0].startsWith("itag")) {
                                            currentUrl = null;
                                            isMp4 = false;
                                        }
                                        if (isMp4 && currentUrl != null) {
                                            otherUrl = currentUrl;
                                            break;
                                        }
                                    }
                                } catch (Exception e) {
                                    FileLog.e(e);
                                }
                            }
                        } else if (args[a].startsWith("use_cipher_signature")) {
                            String[] args2 = args[a].split("=");
                            if (args2.length == 2) {
                                if (args2[1].toLowerCase().equals("true")) {
                                    encrypted = true;
                                }
                            }
                        } else if (args[a].startsWith("hlsvp")) {
                            String[] args2 = args[a].split("=");
                            if (args2.length == 2) {
                                try {
                                    hls = URLDecoder.decode(args2[1], "UTF-8");
                                } catch (Exception e) {
                                    FileLog.e(e);
                                }
                            }
                        } else if (args[a].startsWith("livestream")) {
                            String[] args2 = args[a].split("=");
                            if (args2.length == 2) {
                                if (args2[1].toLowerCase().equals("1")) {
                                    isLive = true;
                                }
                            }
                        }
                    }
                }
                if (isLive) {
                    if (hls == null || encrypted || hls.contains("/s/")) {
                        return null;
                    } else {
                        result[0] = hls;
                        result[1] = "hls";
                    }
                }
                if (exists) {
                    break;
                }
            }
            if (result[0] == null && otherUrl != null) {
                result[0] = otherUrl;
                result[1] = "other";
            }

            if (result[0] != null && (encrypted || result[0].contains("/s/")) && embedCode != null) {
                encrypted = true;
                int index = result[0].indexOf("/s/");
                int index2 = result[0].indexOf('/', index + 10);
                if (index != -1) {
                    if (index2 == -1) {
                        index2 = result[0].length();
                    }
                    sig = result[0].substring(index, index2);
                    String jsUrl = null;
                    matcher = jsPattern.matcher(embedCode);
                    if (matcher.find()) {
                        try {
                            JSONTokener tokener = new JSONTokener(matcher.group(1));
                            Object value = tokener.nextValue();
                            if (value instanceof String) {
                                jsUrl = (String) value;
                            }
                        } catch (Exception e) {
                            FileLog.e(e);
                        }
                    }

                    if (jsUrl != null) {
                        matcher = playerIdPattern.matcher(jsUrl);
                        String playerId;
                        if (matcher.find()) {
                            playerId = matcher.group(1) + matcher.group(2);
                        } else {
                            playerId = null;
                        }
                        String functionCode = null;
                        String functionName = null;
                        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("youtubecode", Activity.MODE_PRIVATE);
                        if (playerId != null) {
                            functionCode = preferences.getString(playerId, null);
                            functionName = preferences.getString(playerId + "n", null);
                        }
                        if (functionCode == null) {
                            if (jsUrl.startsWith("//")) {
                                jsUrl = "https:" + jsUrl;
                            } else if (jsUrl.startsWith("/")) {
                                jsUrl = "https://www.youtube.com" + jsUrl;
                            }
                            String jsCode = downloadUrlContent(this, jsUrl);
                            if (isCancelled()) {
                                return null;
                            }
                            if (jsCode != null) {
                                matcher = sigPattern.matcher(jsCode);
                                if (matcher.find()) {
                                    functionName = matcher.group(1);
                                } else {
                                    matcher = sigPattern2.matcher(jsCode);
                                    if (matcher.find()) {
                                        functionName = matcher.group(1);
                                    }
                                }
                                if (functionName != null) {
                                    try {
                                        JSExtractor extractor = new JSExtractor(jsCode);
                                        functionCode = extractor.extractFunction(functionName);
                                        if (!TextUtils.isEmpty(functionCode) && playerId != null) {
                                            preferences.edit().putString(playerId, functionCode).putString(playerId + "n", functionName).commit();
                                        }
                                    } catch (Exception e) {
                                        FileLog.e(e);
                                    }
                                }
                            }
                        }
                        if (!TextUtils.isEmpty(functionCode)) {
                            if (Build.VERSION.SDK_INT >= 21) {
                                functionCode += functionName + "('" + sig.substring(3) + "');";
                            } else {
                                functionCode += "window." + interfaceName + ".returnResultToJava(" + functionName + "('" + sig.substring(3) + "'));";
                            }
                            final String functionCodeFinal = functionCode;
                            try {
                                AndroidUtilities.runOnUIThread(() -> {
                                    if (Build.VERSION.SDK_INT >= 21) {
                                        webView.evaluateJavascript(functionCodeFinal, value -> {
                                            result[0] = result[0].replace(sig, "/signature/" + value.substring(1, value.length() - 1));
                                            countDownLatch.countDown();
                                        });
                                    } else {
                                        try {
                                            String javascript = "<script>" + functionCodeFinal + "</script>";
                                            byte[] data = javascript.getBytes("UTF-8");
                                            final String base64 = Base64.encodeToString(data, Base64.DEFAULT);
                                            webView.loadUrl("data:text/html;charset=utf-8;base64," + base64);
                                        } catch (Exception e) {
                                            FileLog.e(e);
                                        }
                                    }
                                });
                                countDownLatch.await();
                                encrypted = false;
                            } catch (Exception e) {
                                FileLog.e(e);
                            }
                        }
                    }
                }
            }
            return isCancelled() || encrypted ? null : result;
        }

        private void onInterfaceResult(String value) {
            result[0] = result[0].replace(sig, "/signature/" + value);
            countDownLatch.countDown();
        }

        @Override
        protected void onPostExecute(String[] result) {
            if (result[0] != null) {
                if (BuildVars.LOGS_ENABLED) {
                    FileLog.d("start play youtube video " + result[1] + " " + result[0]);
                }
                initied = true;
                playVideoUrl = result[0];
                playVideoType = result[1];
                if (playVideoType.equals("hls")) {
                    isStream = true;
                }
                if (isAutoplay) {
                    preparePlayer();
                }
                showProgress(false, true);
                controlsView.show(true, true);
            } else if (!isCancelled()) {
                onInitFailed();
            }
        }
    }

    private class VimeoVideoTask extends AsyncTask<Void, Void, String> {

        private String videoId;
        private boolean canRetry = true;
        private String[] results = new String[2];

        public VimeoVideoTask(String vid) {
            videoId = vid;
        }

        protected String doInBackground(Void... voids) {
            String playerCode = downloadUrlContent(this, String.format(Locale.US, "https://player.vimeo.com/video/%s/config", videoId));
            if (isCancelled()) {
                return null;
            }
            try {
                JSONObject json = new JSONObject(playerCode);
                JSONObject files = json.getJSONObject("request").getJSONObject("files");
                if (files.has("hls")) {
                    JSONObject hls = files.getJSONObject("hls");
                    try {
                        results[0] = hls.getString("url");
                    } catch (Exception e) {
                        String defaultCdn = hls.getString("default_cdn");
                        JSONObject cdns = hls.getJSONObject("cdns");
                        hls = cdns.getJSONObject(defaultCdn);
                        results[0] = hls.getString("url");
                    }
                    results[1] = "hls";
                } else if (files.has("progressive")) {
                    results[1] = "other";
                    JSONArray progressive = files.getJSONArray("progressive");
                    JSONObject format = progressive.getJSONObject(0);
                    results[0] = format.getString("url");
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
            return isCancelled() ? null : results[0];
        }

        @Override
        protected void onPostExecute(String result) {
            if (result != null) {
                initied = true;
                playVideoUrl = result;
                playVideoType = results[1];
                if (isAutoplay) {
                    preparePlayer();
                }
                showProgress(false, true);
                controlsView.show(true, true);
            } else if (!isCancelled()) {
                onInitFailed();
            }
        }
    }

    private class AparatVideoTask extends AsyncTask<Void, Void, String> {

        private String videoId;
        private boolean canRetry = true;
        private String[] results = new String[2];

        public AparatVideoTask(String vid) {
            videoId = vid;
        }

        protected String doInBackground(Void... voids) {
            String playerCode = downloadUrlContent(this, String.format(Locale.US, "http://www.aparat.com/video/video/embed/vt/frame/showvideo/yes/videohash/%s", videoId));
            if (isCancelled()) {
                return null;
            }
            try {
                Matcher filelist = aparatFileListPattern.matcher(playerCode);
                if (filelist.find()) {
                    String jsonCode = filelist.group(1);
                    JSONArray json = new JSONArray(jsonCode);
                    for (int a = 0; a < json.length(); a++) {
                        JSONArray array = json.getJSONArray(a);
                        if (array.length() == 0) {
                            continue;
                        }
                        JSONObject object = array.getJSONObject(0);
                        if (!object.has("file")) {
                            continue;
                        }
                        results[0] = object.getString("file");
                        results[1] = "other";
                    }
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
            return isCancelled() ? null : results[0];
        }

        @Override
        protected void onPostExecute(String result) {
            if (result != null) {
                initied = true;
                playVideoUrl = result;
                playVideoType = results[1];
                if (isAutoplay) {
                    preparePlayer();
                }
                showProgress(false, true);
                controlsView.show(true, true);
            } else if (!isCancelled()) {
                onInitFailed();
            }
        }
    }

    private class TwitchClipVideoTask extends AsyncTask<Void, Void, String> {

        private String videoId;
        private String currentUrl;
        private boolean canRetry = true;
        private String[] results = new String[2];

        public TwitchClipVideoTask(String url, String vid) {
            videoId = vid;
            currentUrl = url;
        }

        protected String doInBackground(Void... voids) {
            String playerCode = downloadUrlContent(this, currentUrl, null, false);
            if (isCancelled()) {
                return null;
            }
            try {
                Matcher filelist = twitchClipFilePattern.matcher(playerCode);
                if (filelist.find()) {
                    String jsonCode = filelist.group(1);
                    JSONObject json = new JSONObject(jsonCode);
                    JSONArray array = json.getJSONArray("quality_options");
                    JSONObject obj = array.getJSONObject(0);
                    results[0] = obj.getString("source");
                    results[1] = "other";
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
            return isCancelled() ? null : results[0];
        }

        @Override
        protected void onPostExecute(String result) {
            if (result != null) {
                initied = true;
                playVideoUrl = result;
                playVideoType = results[1];
                if (isAutoplay) {
                    preparePlayer();
                }
                showProgress(false, true);
                controlsView.show(true, true);
            } else if (!isCancelled()) {
                onInitFailed();
            }
        }
    }

    private class TwitchStreamVideoTask extends AsyncTask<Void, Void, String> {

        private String videoId;
        private String currentUrl;
        private boolean canRetry = true;
        private String[] results = new String[2];

        public TwitchStreamVideoTask(String url, String vid) {
            videoId = vid;
            currentUrl = url;
        }

        protected String doInBackground(Void... voids) {
            HashMap<String, String> headers = new HashMap<>();
            headers.put("Client-ID", "jzkbprff40iqj646a697cyrvl0zt2m6");
            int idx;
            if ((idx = videoId.indexOf('&')) > 0) {
                videoId = videoId.substring(0, idx);
            }
            String streamCode = downloadUrlContent(this, String.format(Locale.US, "https://api.twitch.tv/kraken/streams/%s?stream_type=all", videoId), headers, false);
            if (isCancelled()) {
                return null;
            }
            try {
                JSONObject obj = new JSONObject(streamCode);
                JSONObject stream = obj.getJSONObject("stream");
                String accessTokenCode = downloadUrlContent(this, String.format(Locale.US, "https://api.twitch.tv/api/channels/%s/access_token", videoId), headers, false);
                JSONObject accessToken = new JSONObject(accessTokenCode);
                String sig = URLEncoder.encode(accessToken.getString("sig"), "UTF-8");
                String token = URLEncoder.encode(accessToken.getString("token"), "UTF-8");
                URLEncoder.encode("https://youtube.googleapis.com/v/" + videoId, "UTF-8");
                String params = "allow_source=true&" +
                        "allow_audio_only=true&" +
                        "allow_spectre=true&" +
                        "player=twitchweb&" +
                        "segment_preference=4&" +
                        "p=" + (int) (Math.random() * 10000000) + "&" +
                        "sig=" + sig + "&" +
                        "token=" + token;
                String m3uUrl = String.format(Locale.US, "https://usher.ttvnw.net/api/channel/hls/%s.m3u8?%s", videoId, params);
                results[0] = m3uUrl;
                results[1] = "hls";
            } catch (Exception e) {
                FileLog.e(e);
            }
            return isCancelled() ? null : results[0];
        }

        @Override
        protected void onPostExecute(String result) {
            if (result != null) {
                initied = true;
                playVideoUrl = result;
                playVideoType = results[1];
                if (isAutoplay) {
                    preparePlayer();
                }
                showProgress(false, true);
                controlsView.show(true, true);
            } else if (!isCancelled()) {
                onInitFailed();
            }
        }
    }

    private class CoubVideoTask extends AsyncTask<Void, Void, String> {

        private String videoId;
        private boolean canRetry = true;
        private String[] results = new String[4];

        public CoubVideoTask(String vid) {
            videoId = vid;
        }

        private String decodeUrl(String input) {
            StringBuilder source = new StringBuilder(input);
            for (int a = 0; a < source.length(); a++) {
                char c = source.charAt(a);
                char lower = Character.toLowerCase(c);
                source.setCharAt(a, c == lower ? Character.toUpperCase(c) : lower);
            }
            try {
                return new String(Base64.decode(source.toString(), Base64.DEFAULT), "UTF-8");
            } catch (Exception ignore) {
                return null;
            }
        }

        protected String doInBackground(Void... voids) {
            String playerCode = downloadUrlContent(this, String.format(Locale.US, "https://coub.com/api/v2/coubs/%s.json", videoId));
            if (isCancelled()) {
                return null;
            }
            try {
                JSONObject json = new JSONObject(playerCode).getJSONObject("file_versions").getJSONObject("mobile");
                String video = json.getString("video");
                String audio = json.getJSONArray("audio").getString(0);
                if (video != null && audio != null) {
                    results[0] = video;
                    results[1] = "other";
                    results[2] = audio;
                    results[3] = "other";
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
            return isCancelled() ? null : results[0];
        }

        @Override
        protected void onPostExecute(String result) {
            if (result != null) {
                initied = true;
                playVideoUrl = result;
                playVideoType = results[1];
                playAudioUrl = results[2];
                playAudioType = results[3];
                if (isAutoplay) {
                    preparePlayer();
                }
                showProgress(false, true);
                controlsView.show(true, true);
            } else if (!isCancelled()) {
                onInitFailed();
            }
        }
    }

    private TextureView.SurfaceTextureListener surfaceTextureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {

        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {

        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            if (changingTextureView) {
                if (switchingInlineMode) {
                    waitingForFirstTextureUpload = 2;
                }
                textureView.setSurfaceTexture(surface);
                textureView.setVisibility(VISIBLE);
                changingTextureView = false;
                return false;
            }
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {
            if (waitingForFirstTextureUpload == 1) {
                changedTextureView.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                    @Override
                    public boolean onPreDraw() {
                        changedTextureView.getViewTreeObserver().removeOnPreDrawListener(this);
                        if (textureImageView != null) {
                            textureImageView.setVisibility(INVISIBLE);
                            textureImageView.setImageDrawable(null);
                            if (currentBitmap != null) {
                                currentBitmap.recycle();
                                currentBitmap = null;
                            }
                        }
                        AndroidUtilities.runOnUIThread(() -> delegate.onInlineSurfaceTextureReady());
                        waitingForFirstTextureUpload = 0;
                        return true;
                    }
                });
                changedTextureView.invalidate();
            }
        }
    };

    private Runnable switchToInlineRunnable = new Runnable() {
        @Override
        public void run() {
            switchingInlineMode = false;
            if (currentBitmap != null) {
                currentBitmap.recycle();
                currentBitmap = null;
            }

            changingTextureView = true;
            if (textureImageView != null) {
                try {
                    currentBitmap = Bitmaps.createBitmap(textureView.getWidth(), textureView.getHeight(), Bitmap.Config.ARGB_8888);
                    textureView.getBitmap(currentBitmap);
                } catch (Throwable e) {
                    if (currentBitmap != null) {
                        currentBitmap.recycle();
                        currentBitmap = null;
                    }
                    FileLog.e(e);
                }

                if (currentBitmap != null) {
                    textureImageView.setVisibility(VISIBLE);
                    textureImageView.setImageBitmap(currentBitmap);
                } else {
                    textureImageView.setImageDrawable(null);
                }
            }

            isInline = true;
            updatePlayButton();
            updateShareButton();
            updateFullscreenButton();
            updateInlineButton();

            ViewGroup viewGroup = (ViewGroup) controlsView.getParent();
            if (viewGroup != null) {
                viewGroup.removeView(controlsView);
            }
            changedTextureView = delegate.onSwitchInlineMode(controlsView, isInline, videoWidth, videoHeight, aspectRatioFrameLayout.getVideoRotation(), allowInlineAnimation);
            changedTextureView.setVisibility(INVISIBLE);
            ViewGroup parent = (ViewGroup) textureView.getParent();
            if (parent != null) {
                parent.removeView(textureView);
            }
            controlsView.show(false, false);
        }
    };

    private class ControlsView extends FrameLayout {

        private ImageReceiver imageReceiver;
        private boolean progressPressed;
        private TextPaint textPaint;
        private StaticLayout durationLayout;
        private StaticLayout progressLayout;
        private Paint progressPaint;
        private Paint progressInnerPaint;
        private Paint progressBufferedPaint;
        private int durationWidth;
        private int duration;
        private int progress;
        private int bufferedPosition;
        private boolean isVisible = true;
        private AnimatorSet currentAnimation;
        private int lastProgressX;
        private int currentProgressX;
        private Runnable hideRunnable = () -> show(false, true);

        public ControlsView(Context context) {
            super(context);
            setWillNotDraw(false);

            textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            textPaint.setColor(0xffffffff);
            textPaint.setTextSize(AndroidUtilities.dp(12));

            progressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            progressPaint.setColor(0xff19a7e8);

            progressInnerPaint = new Paint();
            progressInnerPaint.setColor(0xff959197);

            progressBufferedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            progressBufferedPaint.setColor(0xffffffff);

            imageReceiver = new ImageReceiver(this);
        }

        public void setDuration(int value) {
            if (duration == value || value < 0 || isStream) {
                return;
            }
            duration = value;
            durationLayout = new StaticLayout(AndroidUtilities.formatShortDuration(duration), textPaint, AndroidUtilities.dp(1000), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
            if (durationLayout.getLineCount() > 0) {
                durationWidth = (int) Math.ceil(durationLayout.getLineWidth(0));
            }
            invalidate();
        }

        public void setBufferedProgress(int position) {
            bufferedPosition = position;
            invalidate();
        }

        public void setProgress(int value) {
            if (progressPressed || value < 0 || isStream) {
                return;
            }
            progress = value;
            progressLayout = new StaticLayout(AndroidUtilities.formatShortDuration(progress), textPaint, AndroidUtilities.dp(1000), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
            invalidate();
        }

        public void show(boolean value, boolean animated) {
            if (isVisible == value) {
                return;
            }
            isVisible = value;
            if (currentAnimation != null) {
                currentAnimation.cancel();
            }
            if (isVisible) {
                if (animated) {
                    currentAnimation = new AnimatorSet();
                    currentAnimation.playTogether(ObjectAnimator.ofFloat(this, View.ALPHA, 1.0f));
                    currentAnimation.setDuration(150);
                    currentAnimation.addListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animator) {
                            currentAnimation = null;
                        }
                    });
                    currentAnimation.start();
                } else {
                    setAlpha(1.0f);
                }
            } else {
                if (animated) {
                    currentAnimation = new AnimatorSet();
                    currentAnimation.playTogether(ObjectAnimator.ofFloat(this, View.ALPHA, 0.0f));
                    currentAnimation.setDuration(150);
                    currentAnimation.addListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animator) {
                            currentAnimation = null;
                        }
                    });
                    currentAnimation.start();
                } else {
                    setAlpha(0.0f);
                }
            }
            checkNeedHide();
        }

        private void checkNeedHide() {
            AndroidUtilities.cancelRunOnUIThread(hideRunnable);
            if (isVisible && videoPlayer.isPlaying()) {
                AndroidUtilities.runOnUIThread(hideRunnable, 3000);
            }
        }

        @Override
        public boolean onInterceptTouchEvent(MotionEvent ev) {
            if (ev.getAction() == MotionEvent.ACTION_DOWN) {
                if (!isVisible) {
                    show(true, true);
                    return true;
                }
                onTouchEvent(ev);
                return progressPressed;
            }
            return super.onInterceptTouchEvent(ev);
        }

        @Override
        public void requestDisallowInterceptTouchEvent(boolean disallowIntercept) {
            super.requestDisallowInterceptTouchEvent(disallowIntercept);
            checkNeedHide();
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            int progressLineX;
            int progressLineEndX;
            int progressY;
            if (inFullscreen) {
                progressLineX = AndroidUtilities.dp(18 + 18) + durationWidth;
                progressLineEndX = getMeasuredWidth() - AndroidUtilities.dp(58 + 18) - durationWidth;
                progressY = getMeasuredHeight() - AndroidUtilities.dp(7 + 21);
            } else {
                progressLineX = 0;
                progressLineEndX = getMeasuredWidth();
                progressY = getMeasuredHeight() - AndroidUtilities.dp(2 + 10);
            }

            int progressX = progressLineX + (duration != 0 ? (int) ((progressLineEndX - progressLineX) * (progress / (float) duration)) : 0);

            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                if (isVisible && !isInline && !isStream) {
                    if (duration != 0) {
                        int x = (int) event.getX();
                        int y = (int) event.getY();
                        if (x >= progressX - AndroidUtilities.dp(10) && x <= progressX + AndroidUtilities.dp(10) && y >= progressY - AndroidUtilities.dp(10) && y <= progressY + AndroidUtilities.dp(10)) {
                            progressPressed = true;
                            lastProgressX = x;
                            currentProgressX = progressX;
                            getParent().requestDisallowInterceptTouchEvent(true);
                            invalidate();
                        }
                    }
                } else {
                    show(true, true);
                }
                AndroidUtilities.cancelRunOnUIThread(hideRunnable);
            } else if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                if (initied && videoPlayer.isPlaying()) {
                    AndroidUtilities.runOnUIThread(hideRunnable, 3000);
                }
                if (progressPressed) {
                    progressPressed = false;
                    if (initied) {
                        progress = (int) (duration * ((float) (currentProgressX - progressLineX) / (progressLineEndX - progressLineX)));
                        videoPlayer.seekTo((long) progress * 1000);
                    }
                }
            } else if (event.getAction() == MotionEvent.ACTION_MOVE) {
                if (progressPressed) {
                    int x = (int) event.getX();
                    currentProgressX -= (lastProgressX - x);
                    lastProgressX = x;
                    if (currentProgressX < progressLineX) {
                        currentProgressX = progressLineX;
                    } else if (currentProgressX > progressLineEndX) {
                        currentProgressX = progressLineEndX;
                    }
                    setProgress((int) (duration * 1000 * ((float) (currentProgressX - progressLineX) / (progressLineEndX - progressLineX))));
                    invalidate();
                }
            }
            super.onTouchEvent(event);
            return true;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (drawImage) {
                if (firstFrameRendered && currentAlpha != 0) {
                    long newTime = System.currentTimeMillis();
                    long dt = newTime - lastUpdateTime;
                    lastUpdateTime = newTime;
                    currentAlpha -= dt / 150.0f;
                    if (currentAlpha < 0) {
                        currentAlpha = 0.0f;
                    }
                    invalidate();
                }
                imageReceiver.setAlpha(currentAlpha);
                imageReceiver.draw(canvas);
            }
            if (videoPlayer.isPlayerPrepared() && !isStream) {
                int width = getMeasuredWidth();
                int height = getMeasuredHeight();
                if (!isInline) {
                    if (durationLayout != null) {
                        canvas.save();
                        canvas.translate(width - AndroidUtilities.dp(58) - durationWidth, height - AndroidUtilities.dp(29 + (inFullscreen ? 6 : 10)));
                        durationLayout.draw(canvas);
                        canvas.restore();
                    }

                    if (progressLayout != null) {
                        canvas.save();
                        canvas.translate(AndroidUtilities.dp(18), height - AndroidUtilities.dp(29 + (inFullscreen ? 6 : 10)));
                        progressLayout.draw(canvas);
                        canvas.restore();
                    }
                }

                if (duration != 0) {
                    int progressLineY;
                    int progressLineX;
                    int progressLineEndX;
                    int cy;

                    if (isInline) {
                        progressLineY = height - AndroidUtilities.dp(3);
                        progressLineX = 0;
                        progressLineEndX = width;
                        cy = height - AndroidUtilities.dp(7);
                    } else if (inFullscreen) {
                        progressLineY = height - AndroidUtilities.dp(26 + 3);
                        progressLineX = AndroidUtilities.dp(18 + 18) + durationWidth;
                        progressLineEndX = width - AndroidUtilities.dp(58 + 18) - durationWidth;
                        cy = height - AndroidUtilities.dp(7 + 21);
                    } else {
                        progressLineY = height - AndroidUtilities.dp(10 + 3);
                        progressLineX = 0;
                        progressLineEndX = width;
                        cy = height - AndroidUtilities.dp(2 + 10);
                    }
                    if (inFullscreen) {
                        canvas.drawRect(progressLineX, progressLineY, progressLineEndX, progressLineY + AndroidUtilities.dp(3), progressInnerPaint);
                    }
                    int progressX;
                    if (progressPressed) {
                        progressX = currentProgressX;
                    } else {
                        progressX = progressLineX + (int) ((progressLineEndX - progressLineX) * (progress / (float) duration));
                    }
                    if (bufferedPosition != 0 && duration != 0) {
                        canvas.drawRect(progressLineX, progressLineY, progressLineX + (progressLineEndX - progressLineX) * (bufferedPosition / (float) duration), progressLineY + AndroidUtilities.dp(3), inFullscreen ? progressBufferedPaint : progressInnerPaint);
                    }
                    canvas.drawRect(progressLineX, progressLineY, progressX, progressLineY + AndroidUtilities.dp(3), progressPaint);
                    if (!isInline) {
                        canvas.drawCircle(progressX, cy, AndroidUtilities.dp(progressPressed ? 7 : 5), progressPaint);
                    }
                }
            }
        }
    }

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    public WebPlayerView(Context context, boolean allowInline, boolean allowShare, WebPlayerViewDelegate webPlayerViewDelegate) {
        super(context);
        setWillNotDraw(false);
        delegate = webPlayerViewDelegate;

        backgroundPaint.setColor(0xff000000);

        aspectRatioFrameLayout = new AspectRatioFrameLayout(context) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                if (textureViewContainer != null) {
                    ViewGroup.LayoutParams layoutParams = textureView.getLayoutParams();
                    layoutParams.width = getMeasuredWidth();
                    layoutParams.height = getMeasuredHeight();

                    if (textureImageView != null) {
                        layoutParams = textureImageView.getLayoutParams();
                        layoutParams.width = getMeasuredWidth();
                        layoutParams.height = getMeasuredHeight();
                    }
                }
            }
        };
        addView(aspectRatioFrameLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.CENTER));

        interfaceName = "JavaScriptInterface";
        webView = new WebView(context) {
            @Override
            protected void onAttachedToWindow() {
                AndroidUtilities.checkAndroidTheme(context, true);
                super.onAttachedToWindow();
            }

            @Override
            protected void onDetachedFromWindow() {
                AndroidUtilities.checkAndroidTheme(context, false);
                super.onDetachedFromWindow();
            }
        };
        webView.addJavascriptInterface(new JavaScriptInterface(value -> {
            if (currentTask != null && !currentTask.isCancelled()) {
                if (currentTask instanceof YoutubeVideoTask) {
                    ((YoutubeVideoTask) currentTask).onInterfaceResult(value);
                }
            }
        }), interfaceName);
        final WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDefaultTextEncodingName("utf-8");

        textureViewContainer = delegate.getTextureViewContainer();

        textureView = new TextureView(context);
        textureView.setPivotX(0);
        textureView.setPivotY(0);
        if (textureViewContainer != null) {
            textureViewContainer.addView(textureView);
        } else {
            aspectRatioFrameLayout.addView(textureView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.CENTER));
        }

        if (allowInlineAnimation && textureViewContainer != null) {
            textureImageView = new ImageView(context);
            textureImageView.setBackgroundColor(0xffff0000);
            textureImageView.setPivotX(0);
            textureImageView.setPivotY(0);
            textureImageView.setVisibility(INVISIBLE);
            textureViewContainer.addView(textureImageView);
        }

        videoPlayer = new VideoPlayer();
        videoPlayer.setDelegate(this);
        videoPlayer.setTextureView(textureView);

        controlsView = new ControlsView(context);
        if (textureViewContainer != null) {
            textureViewContainer.addView(controlsView);
        } else {
            addView(controlsView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        }

        progressView = new RadialProgressView(context);
        progressView.setProgressColor(0xffffffff);
        addView(progressView, LayoutHelper.createFrame(48, 48, Gravity.CENTER));

        fullscreenButton = new ImageView(context);
        fullscreenButton.setScaleType(ImageView.ScaleType.CENTER);
        controlsView.addView(fullscreenButton, LayoutHelper.createFrame(56, 56, Gravity.RIGHT | Gravity.BOTTOM, 0, 0, 0, 5));
        fullscreenButton.setOnClickListener(v -> {
            if (!initied || changingTextureView || switchingInlineMode || !firstFrameRendered) {
                return;
            }
            inFullscreen = !inFullscreen;
            updateFullscreenState(true);
        });

        playButton = new ImageView(context);
        playButton.setScaleType(ImageView.ScaleType.CENTER);
        controlsView.addView(playButton, LayoutHelper.createFrame(48, 48, Gravity.CENTER));
        playButton.setOnClickListener(v -> {
            if (!initied || playVideoUrl == null) {
                return;
            }
            if (!videoPlayer.isPlayerPrepared()) {
                preparePlayer();
            }
            if (videoPlayer.isPlaying()) {
                videoPlayer.pause();
            } else {
                isCompleted = false;
                videoPlayer.play();
            }
            updatePlayButton();
        });

        if (allowInline) {
            inlineButton = new ImageView(context);
            inlineButton.setScaleType(ImageView.ScaleType.CENTER);
            controlsView.addView(inlineButton, LayoutHelper.createFrame(56, 48, Gravity.RIGHT | Gravity.TOP));
            inlineButton.setOnClickListener(v -> {
                if (textureView == null || !delegate.checkInlinePermissions() || changingTextureView || switchingInlineMode || !firstFrameRendered) {
                    return;
                }
                switchingInlineMode = true;
                if (!isInline) {
                    inFullscreen = false;
                    delegate.prepareToSwitchInlineMode(true, switchToInlineRunnable, aspectRatioFrameLayout.getAspectRatio(), allowInlineAnimation);
                } else {
                    ViewGroup parent = (ViewGroup) aspectRatioFrameLayout.getParent();
                    if (parent != WebPlayerView.this) {
                        if (parent != null) {
                            parent.removeView(aspectRatioFrameLayout);
                        }
                        addView(aspectRatioFrameLayout, 0, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.CENTER));
                        aspectRatioFrameLayout.measure(MeasureSpec.makeMeasureSpec(WebPlayerView.this.getMeasuredWidth(), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(WebPlayerView.this.getMeasuredHeight() - AndroidUtilities.dp(10), MeasureSpec.EXACTLY));
                    }
                    if (currentBitmap != null) {
                        currentBitmap.recycle();
                        currentBitmap = null;
                    }
                    changingTextureView = true;

                    isInline = false;
                    updatePlayButton();
                    updateShareButton();
                    updateFullscreenButton();
                    updateInlineButton();

                    textureView.setVisibility(INVISIBLE);
                    if (textureViewContainer != null) {
                        textureViewContainer.addView(textureView);
                    } else {
                        aspectRatioFrameLayout.addView(textureView);
                    }

                    parent = (ViewGroup) controlsView.getParent();
                    if (parent != WebPlayerView.this) {
                        if (parent != null) {
                            parent.removeView(controlsView);
                        }
                        if (textureViewContainer != null) {
                            textureViewContainer.addView(controlsView);
                        } else {
                            addView(controlsView, 1);
                        }
                    }

                    controlsView.show(false, false);
                    delegate.prepareToSwitchInlineMode(false, null, aspectRatioFrameLayout.getAspectRatio(), allowInlineAnimation);
                }
            });
        }

        if (allowShare) {
            shareButton = new ImageView(context);
            shareButton.setScaleType(ImageView.ScaleType.CENTER);
            shareButton.setImageResource(R.drawable.ic_share_video);
            controlsView.addView(shareButton, LayoutHelper.createFrame(56, 48, Gravity.RIGHT | Gravity.TOP));
            shareButton.setOnClickListener(v -> {
                if (delegate != null) {
                    delegate.onSharePressed();
                }
            });
        }

        updatePlayButton();
        updateFullscreenButton();
        updateInlineButton();
        updateShareButton();
    }

    private void onInitFailed() {
        if (controlsView.getParent() != this) {
            controlsView.setVisibility(GONE);
        }
        delegate.onInitFailed();
    }

    public void updateTextureImageView() {
        if (textureImageView == null) {
            return;
        }
        try {
            currentBitmap = Bitmaps.createBitmap(textureView.getWidth(), textureView.getHeight(), Bitmap.Config.ARGB_8888);
            changedTextureView.getBitmap(currentBitmap);
        } catch (Throwable e) {
            if (currentBitmap != null) {
                currentBitmap.recycle();
                currentBitmap = null;
            }
            FileLog.e(e);
        }
        if (currentBitmap != null) {
            textureImageView.setVisibility(VISIBLE);
            textureImageView.setImageBitmap(currentBitmap);
        } else {
            textureImageView.setImageDrawable(null);
        }
    }

    public String getYoutubeId() {
        return currentYoutubeId;
    }

    @Override
    public void onStateChanged(boolean playWhenReady, int playbackState) {
        if (playbackState != ExoPlayer.STATE_BUFFERING) {
            if (videoPlayer.getDuration() != C.TIME_UNSET) {
                controlsView.setDuration((int) (videoPlayer.getDuration() / 1000));
            } else {
                controlsView.setDuration(0);
            }
        }
        if (playbackState != ExoPlayer.STATE_ENDED && playbackState != ExoPlayer.STATE_IDLE && videoPlayer.isPlaying()) {
            delegate.onPlayStateChanged(this, true);
        } else {
            delegate.onPlayStateChanged(this, false);
        }
        if (videoPlayer.isPlaying() && playbackState != ExoPlayer.STATE_ENDED) {
            updatePlayButton();
        } else {
            if (playbackState == ExoPlayer.STATE_ENDED) {
                isCompleted = true;
                videoPlayer.pause();
                videoPlayer.seekTo(0);
                updatePlayButton();
                controlsView.show(true, true);
            }
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawRect(0, 0, getMeasuredWidth(), getMeasuredHeight() - AndroidUtilities.dp(10), backgroundPaint);
    }

    @Override
    public void onError(VideoPlayer player, Exception e) {
        FileLog.e(e);
        onInitFailed();
    }

    @Override
    public void onVideoSizeChanged(int width, int height, int unappliedRotationDegrees, float pixelWidthHeightRatio) {
        if (aspectRatioFrameLayout != null) {
            if (unappliedRotationDegrees == 90 || unappliedRotationDegrees == 270) {
                int temp = width;
                width = height;
                height = temp;
            }
            videoWidth = (int) (width * pixelWidthHeightRatio);
            videoHeight = height;
            float ratio = height == 0 ? 1 : (width * pixelWidthHeightRatio) / height;
            aspectRatioFrameLayout.setAspectRatio(ratio, unappliedRotationDegrees);
            if (inFullscreen) {
                delegate.onVideoSizeChanged(ratio, unappliedRotationDegrees);
            }
        }
    }

    @Override
    public void onRenderedFirstFrame() {
        firstFrameRendered = true;
        lastUpdateTime = System.currentTimeMillis();
        controlsView.invalidate();
    }

    @Override
    public boolean onSurfaceDestroyed(SurfaceTexture surfaceTexture) {
        if (changingTextureView) {
            changingTextureView = false;
            if (inFullscreen || isInline) {
                if (isInline) {
                    waitingForFirstTextureUpload = 1;
                }
                changedTextureView.setSurfaceTexture(surfaceTexture);
                changedTextureView.setSurfaceTextureListener(surfaceTextureListener);
                changedTextureView.setVisibility(VISIBLE);
                return true;
            }
        }
        return false;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {
        if (waitingForFirstTextureUpload == 2) {
            if (textureImageView != null) {
                textureImageView.setVisibility(INVISIBLE);
                textureImageView.setImageDrawable(null);
                if (currentBitmap != null) {
                    currentBitmap.recycle();
                    currentBitmap = null;
                }
            }
            switchingInlineMode = false;
            delegate.onSwitchInlineMode(controlsView, false, videoWidth, videoHeight, aspectRatioFrameLayout.getVideoRotation(), allowInlineAnimation);
            waitingForFirstTextureUpload = 0;
        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int x = ((r - l) - aspectRatioFrameLayout.getMeasuredWidth()) / 2;
        int y = ((b - t - AndroidUtilities.dp(10)) - aspectRatioFrameLayout.getMeasuredHeight()) / 2;
        aspectRatioFrameLayout.layout(x, y, x + aspectRatioFrameLayout.getMeasuredWidth(), y + aspectRatioFrameLayout.getMeasuredHeight());
        if (controlsView.getParent() == this) {
            controlsView.layout(0, 0, controlsView.getMeasuredWidth(), controlsView.getMeasuredHeight());
        }
        x = ((r - l) - progressView.getMeasuredWidth()) / 2;
        y = ((b - t) - progressView.getMeasuredHeight()) / 2;
        progressView.layout(x, y, x + progressView.getMeasuredWidth(), y + progressView.getMeasuredHeight());
        controlsView.imageReceiver.setImageCoords(0, 0, getMeasuredWidth(), getMeasuredHeight() - AndroidUtilities.dp(10));
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        aspectRatioFrameLayout.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height - AndroidUtilities.dp(10), MeasureSpec.EXACTLY));
        if (controlsView.getParent() == this) {
            controlsView.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
        }
        progressView.measure(MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(44), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(44), MeasureSpec.EXACTLY));
        setMeasuredDimension(width, height);
    }

    private void updatePlayButton() {
        controlsView.checkNeedHide();
        AndroidUtilities.cancelRunOnUIThread(progressRunnable);
        if (!videoPlayer.isPlaying()) {
            if (isCompleted) {
                playButton.setImageResource(isInline ? R.drawable.ic_againinline : R.drawable.ic_again);
            } else {
                playButton.setImageResource(isInline ? R.drawable.ic_playinline : R.drawable.ic_play);
            }
        } else {
            playButton.setImageResource(isInline ? R.drawable.ic_pauseinline : R.drawable.ic_pause);
            AndroidUtilities.runOnUIThread(progressRunnable, 500);
            checkAudioFocus();
        }
    }

    private void checkAudioFocus() {
        if (!hasAudioFocus) {
            AudioManager audioManager = (AudioManager) ApplicationLoader.applicationContext.getSystemService(Context.AUDIO_SERVICE);
            hasAudioFocus = true;
            if (audioManager.requestAudioFocus(this, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                audioFocus = 2;
            }
        }
    }

    @Override
    public void onAudioFocusChange(int focusChange) {
        AndroidUtilities.runOnUIThread(() -> {
            if (focusChange == AudioManager.AUDIOFOCUS_LOSS) {
                if (videoPlayer.isPlaying()) {
                    videoPlayer.pause();
                    updatePlayButton();
                }
                hasAudioFocus = false;
                audioFocus = AUDIO_NO_FOCUS_NO_DUCK;
            } else if (focusChange == AudioManager.AUDIOFOCUS_GAIN) {
                audioFocus = AUDIO_FOCUSED;
                if (resumeAudioOnFocusGain) {
                    resumeAudioOnFocusGain = false;
                    videoPlayer.play();
                }
            } else if (focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) {
                audioFocus = AUDIO_NO_FOCUS_CAN_DUCK;
            } else if (focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                audioFocus = AUDIO_NO_FOCUS_NO_DUCK;
                if (videoPlayer.isPlaying()) {
                    resumeAudioOnFocusGain = true;
                    videoPlayer.pause();
                    updatePlayButton();
                }
            }
        });
    }

    private void updateFullscreenButton() {
        if (!videoPlayer.isPlayerPrepared() || isInline) {
            fullscreenButton.setVisibility(GONE);
            return;
        }
        fullscreenButton.setVisibility(VISIBLE);
        if (!inFullscreen) {
            fullscreenButton.setImageResource(R.drawable.ic_gofullscreen);
            fullscreenButton.setLayoutParams(LayoutHelper.createFrame(56, 56, Gravity.RIGHT | Gravity.BOTTOM, 0, 0, 0, 5));
        } else {
            fullscreenButton.setImageResource(R.drawable.ic_outfullscreen);
            fullscreenButton.setLayoutParams(LayoutHelper.createFrame(56, 56, Gravity.RIGHT | Gravity.BOTTOM, 0, 0, 0, 1));
        }
    }

    private void updateShareButton() {
        if (shareButton == null) {
            return;
        }
        shareButton.setVisibility(isInline || !videoPlayer.isPlayerPrepared() ? GONE : VISIBLE);
    }

    private View getControlView() {
        return controlsView;
    }

    private View getProgressView() {
        return progressView;
    }

    private void updateInlineButton() {
        if (inlineButton == null) {
            return;
        }
        inlineButton.setImageResource(isInline ? R.drawable.ic_goinline : R.drawable.ic_outinline);
        inlineButton.setVisibility(videoPlayer.isPlayerPrepared() ? VISIBLE : GONE);
        if (isInline) {
            inlineButton.setLayoutParams(LayoutHelper.createFrame(40, 40, Gravity.RIGHT | Gravity.TOP));
        } else {
            inlineButton.setLayoutParams(LayoutHelper.createFrame(56, 50, Gravity.RIGHT | Gravity.TOP));
        }
    }

    private void preparePlayer() {
        if (playVideoUrl == null) {
            return;
        }
        if (playVideoUrl != null && playAudioUrl != null) {
            videoPlayer.preparePlayerLoop(Uri.parse(playVideoUrl), playVideoType, Uri.parse(playAudioUrl), playAudioType);
        } else {
            videoPlayer.preparePlayer(Uri.parse(playVideoUrl), playVideoType);
        }
        videoPlayer.setPlayWhenReady(isAutoplay);

        isLoading = false;

        if (videoPlayer.getDuration() != C.TIME_UNSET) {
            controlsView.setDuration((int) (videoPlayer.getDuration() / 1000));
        } else {
            controlsView.setDuration(0);
        }
        updateFullscreenButton();
        updateShareButton();
        updateInlineButton();
        controlsView.invalidate();
        if (seekToTime != -1) {
            videoPlayer.seekTo(seekToTime * 1000);
        }
    }

    public void pause() {
        videoPlayer.pause();
        updatePlayButton();
        controlsView.show(true, true);
    }

    private void updateFullscreenState(boolean byButton) {
        if (textureView == null) {
            return;
        }
        updateFullscreenButton();
        if (textureViewContainer == null) {
            changingTextureView = true;
            if (!inFullscreen) {
                if (textureViewContainer != null) {
                    textureViewContainer.addView(textureView);
                } else {
                    aspectRatioFrameLayout.addView(textureView);
                }
            }
            if (inFullscreen) {
                ViewGroup viewGroup = (ViewGroup) controlsView.getParent();
                if (viewGroup != null) {
                    viewGroup.removeView(controlsView);
                }
            } else {
                ViewGroup parent = (ViewGroup) controlsView.getParent();
                if (parent != this) {
                    if (parent != null) {
                        parent.removeView(controlsView);
                    }
                    if (textureViewContainer != null) {
                        textureViewContainer.addView(controlsView);
                    } else {
                        addView(controlsView, 1);
                    }
                }
            }
            changedTextureView = delegate.onSwitchToFullscreen(controlsView, inFullscreen, aspectRatioFrameLayout.getAspectRatio(), aspectRatioFrameLayout.getVideoRotation(), byButton);
            changedTextureView.setVisibility(INVISIBLE);
            if (inFullscreen && changedTextureView != null) {
                ViewGroup parent = (ViewGroup) textureView.getParent();
                if (parent != null) {
                    parent.removeView(textureView);
                }
            }
            controlsView.checkNeedHide();
        } else {
            if (inFullscreen) {
                ViewGroup viewGroup = (ViewGroup) aspectRatioFrameLayout.getParent();
                if (viewGroup != null) {
                    viewGroup.removeView(aspectRatioFrameLayout);
                }
            } else {
                ViewGroup parent = (ViewGroup) aspectRatioFrameLayout.getParent();
                if (parent != this) {
                    if (parent != null) {
                        parent.removeView(aspectRatioFrameLayout);
                    }
                    addView(aspectRatioFrameLayout, 0);
                }
            }
            delegate.onSwitchToFullscreen(controlsView, inFullscreen, aspectRatioFrameLayout.getAspectRatio(), aspectRatioFrameLayout.getVideoRotation(), byButton);
        }
    }

    public void exitFullscreen() {
        if (!inFullscreen) {
            return;
        }
        inFullscreen = false;
        updateInlineButton();
        updateFullscreenState(false);
    }

    public boolean isInitied() {
        return initied;
    }

    public boolean isInline() {
        return isInline || switchingInlineMode;
    }

    public void enterFullscreen() {
        if (inFullscreen) {
            return;
        }
        inFullscreen = true;
        updateInlineButton();
        updateFullscreenState(false);
    }

    public boolean isInFullscreen() {
        return inFullscreen;
    }

    public static String getYouTubeVideoId(String url) {
        Matcher matcher = youtubeIdRegex.matcher(url);
        String id = null;
        if (matcher.find()) {
            id = matcher.group(1);
        }
        return id;
    }

    public boolean canHandleUrl(String url) {
        if (url != null) {
            if (url.endsWith(".mp4")) {
                return true;
            } else {
                try {
                    Matcher matcher = youtubeIdRegex.matcher(url);
                    String id = null;
                    if (matcher.find()) {
                        id = matcher.group(1);
                    }
                    if (id != null) {
                        return true;
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                }
                try {
                    Matcher matcher = vimeoIdRegex.matcher(url);
                    String id = null;
                    if (matcher.find()) {
                        id = matcher.group(3);
                    }
                    if (id != null) {
                        return true;
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                }
                try {
                    Matcher matcher = aparatIdRegex.matcher(url);
                    String id = null;
                    if (matcher.find()) {
                        id = matcher.group(1);
                    }
                    if (id != null) {
                        return true;
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                }
                try {
                    Matcher matcher = twitchClipIdRegex.matcher(url);
                    String id = null;
                    if (matcher.find()) {
                        id = matcher.group(1);
                    }
                    if (id != null) {
                        return true;
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                }
                try {
                    Matcher matcher = twitchStreamIdRegex.matcher(url);
                    String id = null;
                    if (matcher.find()) {
                        id = matcher.group(1);
                    }
                    if (id != null) {
                        return true;
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                }
                try {
                    Matcher matcher = coubIdRegex.matcher(url);
                    String id = null;
                    if (matcher.find()) {
                        id = matcher.group(1);
                    }
                    if (id != null) {
                        return true;
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
        }
        return false;
    }

    public void willHandle() {
        controlsView.setVisibility(INVISIBLE);
        controlsView.show(false, false);
        showProgress(true, false);
    }

    public boolean loadVideo(String url, TLRPC.Photo thumb, Object parentObject, String originalUrl, boolean autoplay) {
        String youtubeId = null;
        String vimeoId = null;
        String coubId = getCoubId(url);
        if (coubId == null) {
            coubId = getCoubId(originalUrl);
        }
        String twitchClipId = null;
        String twitchStreamId = null;
        String mp4File = null;
        String aparatId = null;
        seekToTime = -1;
        if (coubId == null && url != null) {
            if (url.endsWith(".mp4")) {
                mp4File = url;
            } else {
                try {
                    if (originalUrl != null) {
                        try {
                            Uri uri = Uri.parse(originalUrl);
                            String t = uri.getQueryParameter("t");
                            if (t == null) {
                                t = uri.getQueryParameter("time_continue");
                            }
                            if (t != null) {
                                if (t.contains("m")) {
                                    String[] args = t.split("m");
                                    seekToTime = Utilities.parseInt(args[0]) * 60 + Utilities.parseInt(args[1]);
                                } else {
                                    seekToTime = Utilities.parseInt(t);
                                }
                            }
                        } catch (Exception e) {
                            FileLog.e(e);
                        }
                    }
                    Matcher matcher = youtubeIdRegex.matcher(url);
                    String id = null;
                    if (matcher.find()) {
                        id = matcher.group(1);
                    }
                    if (id != null) {
                        youtubeId = id;
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                }
                if (youtubeId == null) {
                    try {
                        Matcher matcher = vimeoIdRegex.matcher(url);
                        String id = null;
                        if (matcher.find()) {
                            id = matcher.group(3);
                        }
                        if (id != null) {
                            vimeoId = id;
                        }
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                }
                if (vimeoId == null) {
                    try {
                        Matcher matcher = aparatIdRegex.matcher(url);
                        String id = null;
                        if (matcher.find()) {
                            id = matcher.group(1);
                        }
                        if (id != null) {
                            aparatId = id;
                        }
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                }
                if (aparatId == null) {
                    try {
                        Matcher matcher = twitchClipIdRegex.matcher(url);
                        String id = null;
                        if (matcher.find()) {
                            id = matcher.group(1);
                        }
                        if (id != null) {
                            twitchClipId = id;
                        }
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                }
                if (twitchClipId == null) {
                    try {
                        Matcher matcher = twitchStreamIdRegex.matcher(url);
                        String id = null;
                        if (matcher.find()) {
                            id = matcher.group(1);
                        }
                        if (id != null) {
                            twitchStreamId = id;
                        }
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                }
                if (twitchStreamId == null) {
                    try {
                        Matcher matcher = coubIdRegex.matcher(url);
                        String id = null;
                        if (matcher.find()) {
                            id = matcher.group(1);
                        }
                        if (id != null) {
                            coubId = id;
                        }
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                }
            }
        }

        initied = false;
        isCompleted = false;
        isAutoplay = autoplay;
        playVideoUrl = null;
        playAudioUrl = null;
        destroy();
        firstFrameRendered = false;
        currentAlpha = 1.0f;
        if (currentTask != null) {
            currentTask.cancel(true);
            currentTask = null;
        }
        updateFullscreenButton();
        updateShareButton();
        updateInlineButton();
        updatePlayButton();
        if (thumb != null) {
            TLRPC.PhotoSize photoSize = FileLoader.getClosestPhotoSizeWithSize(thumb.sizes, 80, true);
            if (photoSize != null) {
                controlsView.imageReceiver.setImage(null, null, ImageLocation.getForPhoto(photoSize, thumb), "80_80_b", 0, null, parentObject, 1);
                drawImage = true;
            }
        } else {
            drawImage = false;
        }

        if (progressAnimation != null) {
            progressAnimation.cancel();
            progressAnimation = null;
        }
        isLoading = true;
        controlsView.setProgress(0);
        if (youtubeId != null) {
            currentYoutubeId = youtubeId;
            youtubeId = null;
        }
        if (mp4File != null) {
            initied = true;
            playVideoUrl = mp4File;
            playVideoType = "other";
            if (isAutoplay) {
                preparePlayer();
            }
            showProgress(false, false);
            controlsView.show(true, true);
        } else {
            if (youtubeId != null) {
                YoutubeVideoTask task = new YoutubeVideoTask(youtubeId);
                task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, null, null, null);
                currentTask = task;
            } else if (vimeoId != null) {
                VimeoVideoTask task = new VimeoVideoTask(vimeoId);
                task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, null, null, null);
                currentTask = task;
            } else if (coubId != null) {
                CoubVideoTask task = new CoubVideoTask(coubId);
                task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, null, null, null);
                currentTask = task;
                isStream = true;
            } else if (aparatId != null) {
                AparatVideoTask task = new AparatVideoTask(aparatId);
                task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, null, null, null);
                currentTask = task;
            } else if (twitchClipId != null) {
                TwitchClipVideoTask task = new TwitchClipVideoTask(url, twitchClipId);
                task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, null, null, null);
                currentTask = task;
            } else if (twitchStreamId != null) {
                TwitchStreamVideoTask task = new TwitchStreamVideoTask(url, twitchStreamId);
                task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, null, null, null);
                currentTask = task;
                isStream = true;
            }

            controlsView.show(false, false);
            showProgress(true, false);
        }
        if (youtubeId != null || vimeoId != null || coubId != null || aparatId != null || mp4File != null || twitchClipId != null || twitchStreamId != null) {
            controlsView.setVisibility(VISIBLE);
            return true;
        }
        controlsView.setVisibility(GONE);
        return false;
    }

    public String getCoubId(String url) {
        if (TextUtils.isEmpty(url)) {
            return null;
        }
        try {
            Matcher matcher = coubIdRegex.matcher(url);
            String id = null;
            if (matcher.find()) {
                id = matcher.group(1);
            }
            if (id != null) {
                return id;
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        return null;
    }

    public View getAspectRatioView() {
        return aspectRatioFrameLayout;
    }

    public TextureView getTextureView() {
        return textureView;
    }

    public ImageView getTextureImageView() {
        return textureImageView;
    }

    public View getControlsView() {
        return controlsView;
    }

    public void destroy() {
        videoPlayer.releasePlayer(false);
        if (currentTask != null) {
            currentTask.cancel(true);
            currentTask = null;
        }
        webView.stopLoading();
    }

    private void showProgress(boolean show, boolean animated) {
        if (animated) {
            if (progressAnimation != null) {
                progressAnimation.cancel();
            }
            progressAnimation = new AnimatorSet();
            progressAnimation.playTogether(ObjectAnimator.ofFloat(progressView, "alpha", show ? 1.0f : 0.0f));
            progressAnimation.setDuration(150);
            progressAnimation.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animator) {
                    progressAnimation = null;
                }
            });
            progressAnimation.start();
        } else {
            progressView.setAlpha(show ? 1.0f : 0.0f);
        }
    }
}
