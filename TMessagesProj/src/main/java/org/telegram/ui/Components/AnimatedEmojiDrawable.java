package org.telegram.ui.Components;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;
import android.view.animation.LinearInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.SQLite.SQLiteCursor;
import org.telegram.SQLite.SQLiteDatabase;
import org.telegram.SQLite.SQLiteException;
import org.telegram.SQLite.SQLitePreparedStatement;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.DocumentObject;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.SvgHelper;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.NativeByteBuffer;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;

public class AnimatedEmojiDrawable extends Drawable {

    public static final int CACHE_TYPE_MESSAGES = 0;
    public static final int CACHE_TYPE_MESSAGES_LARGE = 1;
    public static final int CACHE_TYPE_KEYBOARD = 2;
    public static final int CACHE_TYPE_ALERT_PREVIEW = 3;
    public static final int CACHE_TYPE_TAB_STRIP = 4;

    public static final int STANDARD_LOTTIE_FRAME = 5;

    private Paint debugCacheType;
    private static final boolean DEBUG_CACHE_TYPE = false;

    private static HashMap<Integer, HashMap<Long, AnimatedEmojiDrawable>> globalEmojiCache;
    @NonNull
    public static AnimatedEmojiDrawable make(int account, int cacheType, long documentId) {
        if (globalEmojiCache == null) {
            globalEmojiCache = new HashMap<>();
        }
        final int key = Objects.hash(account, cacheType);
        HashMap<Long, AnimatedEmojiDrawable> cache = globalEmojiCache.get(key);
        if (cache == null) {
            globalEmojiCache.put(key, cache = new HashMap<>());
        }
        AnimatedEmojiDrawable drawable = cache.get(documentId);
        if (drawable == null) {
            cache.put(documentId, drawable = new AnimatedEmojiDrawable(cacheType, account, documentId));
        }
        return drawable;
    }

    @NonNull
    public static AnimatedEmojiDrawable make(int account, int cacheType, @NonNull TLRPC.Document document) {
        if (globalEmojiCache == null) {
            globalEmojiCache = new HashMap<>();
        }
        final int key = Objects.hash(account, cacheType);
        HashMap<Long, AnimatedEmojiDrawable> cache = globalEmojiCache.get(key);
        if (cache == null) {
            globalEmojiCache.put(key, cache = new HashMap<>());
        }
        AnimatedEmojiDrawable drawable = cache.get(document.id);
        if (drawable == null) {
            cache.put(document.id, drawable = new AnimatedEmojiDrawable(cacheType, account, document));
        }
        return drawable;
    }

    public static int getCacheTypeForEnterView() {
        return SharedConfig.getDevicePerformanceClass() == SharedConfig.PERFORMANCE_CLASS_LOW ? CACHE_TYPE_MESSAGES : CACHE_TYPE_KEYBOARD;
    }

    public void setTime(long time) {
        if (imageReceiver != null) {
            if (cacheType == STANDARD_LOTTIE_FRAME) {
                time = 0;
            }
            imageReceiver.setCurrentTime(time);
        }
    }

    public void update(long time) {
        if (imageReceiver != null) {
            if (cacheType == STANDARD_LOTTIE_FRAME) {
                time = 0;
            }
            if (imageReceiver.getLottieAnimation() != null) {
                imageReceiver.getLottieAnimation().updateCurrentFrame(time, true);
            }
            if (imageReceiver.getAnimation() != null) {
                imageReceiver.getAnimation().updateCurrentFrame(time, true);
            }
        }
    }

    public interface ReceivedDocument {
        void run(TLRPC.Document document);
    }

    private static HashMap<Integer, EmojiDocumentFetcher> fetchers;
    public static EmojiDocumentFetcher getDocumentFetcher(int account) {
        if (fetchers == null) {
            fetchers = new HashMap<>();
        }
        EmojiDocumentFetcher fetcher = fetchers.get(account);
        if (fetcher == null) {
            fetchers.put(account, fetcher = new EmojiDocumentFetcher(account));
        }
        return fetcher;
    }
    public static class EmojiDocumentFetcher {
        private HashMap<Long, TLRPC.Document> emojiDocumentsCache;
        private HashMap<Long, ArrayList<ReceivedDocument>> loadingDocuments;
        private HashSet<Long> toFetchDocuments;
        private Runnable fetchRunnable;
        private final int currentAccount;

        public EmojiDocumentFetcher(int account) {
            currentAccount = account;
        }

        public void fetchDocument(long id, ReceivedDocument onDone) {
            checkThread();
            if (emojiDocumentsCache != null) {
                TLRPC.Document cacheDocument = emojiDocumentsCache.get(id);
                if (cacheDocument != null) {
                    onDone.run(cacheDocument);
                    return;
                }
            }
            if (onDone != null) {
                if (loadingDocuments == null) {
                    loadingDocuments = new HashMap<>();
                }
                ArrayList<ReceivedDocument> callbacks = loadingDocuments.get(id);
                if (callbacks != null) {
                    callbacks.add(onDone);
                    return;
                }
                callbacks = new ArrayList<>(1);
                callbacks.add(onDone);
                loadingDocuments.put(id, callbacks);
            }
            if (toFetchDocuments == null) {
                toFetchDocuments = new HashSet<>();
            }
            toFetchDocuments.add(id);
            if (fetchRunnable != null) {
                return;
            }
            AndroidUtilities.runOnUIThread(fetchRunnable = () -> {
                ArrayList<Long> emojiToLoad = new ArrayList<>(toFetchDocuments);
                toFetchDocuments.clear();
                loadFromDatabase(emojiToLoad);
                fetchRunnable = null;
            });
        }

        private void checkThread() {
            if (BuildVars.DEBUG_VERSION && Thread.currentThread() != Looper.getMainLooper().getThread()) {
                throw new IllegalStateException("Wrong thread");
            }
        }

        private void loadFromDatabase(ArrayList<Long> emojiToLoad) {
            MessagesStorage.getInstance(currentAccount).getStorageQueue().postRunnable(() -> {
                SQLiteDatabase database = MessagesStorage.getInstance(currentAccount).getDatabase();
                try {
                    String idsStr = TextUtils.join(",", emojiToLoad);
                    SQLiteCursor cursor = database.queryFinalized(String.format(Locale.US, "SELECT data FROM animated_emoji WHERE document_id IN (%s)", idsStr));
                    ArrayList<Object> documents = new ArrayList<>();
                    HashSet<Long> loadFromServerIds = new HashSet<>(emojiToLoad);
                    while (cursor.next()) {
                        NativeByteBuffer byteBuffer = cursor.byteBufferValue(0);
                        try {
                            TLRPC.Document document = TLRPC.Document.TLdeserialize(byteBuffer, byteBuffer.readInt32(true), true);
                            if (document != null && document.id != 0) {
                                documents.add(document);
                                loadFromServerIds.remove(document.id);
                            }
                        } catch (Exception e) {
                            FileLog.e(e);
                        }
                        if (byteBuffer != null) {
                            byteBuffer.reuse();
                        }
                    }

                    AndroidUtilities.runOnUIThread(() -> {
                        processDocuments(documents);
                        if (!loadFromServerIds.isEmpty()) {
                            loadFromServer(new ArrayList<>(loadFromServerIds));
                        }
                    });
                    cursor.dispose();
                } catch (SQLiteException e) {
                    FileLog.e(e);
                }
            });
        }

        private void loadFromServer(ArrayList<Long> loadFromServerIds) {
            final TLRPC.TL_messages_getCustomEmojiDocuments req = new TLRPC.TL_messages_getCustomEmojiDocuments();
            req.document_id = loadFromServerIds;
            ConnectionsManager.getInstance(currentAccount).sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
                HashSet<Long> loadedFromServer = new HashSet<>(loadFromServerIds);
                if (res instanceof TLRPC.Vector) {
                    ArrayList<Object> objects = ((TLRPC.Vector) res).objects;
                    putToStorage(objects);
                    processDocuments(objects);
                    for (int i = 0; i < objects.size(); i++) {
                        if (objects.get(i) instanceof TLRPC.Document) {
                            TLRPC.Document document = (TLRPC.Document) objects.get(i);
                            loadedFromServer.remove(document.id);
                        }
                    }

                    if (!loadedFromServer.isEmpty()) {
                        loadFromServer(new ArrayList<>(loadedFromServer));
                    }
                }
            }));
        }

        private void putToStorage(ArrayList<Object> objects) {
            MessagesStorage.getInstance(currentAccount).getStorageQueue().postRunnable(() -> {
                SQLiteDatabase database = MessagesStorage.getInstance(currentAccount).getDatabase();
                try {
                    SQLitePreparedStatement state = database.executeFast("REPLACE INTO animated_emoji VALUES(?, ?)");
                    for (int i = 0; i < objects.size(); i++) {
                        if (objects.get(i) instanceof TLRPC.Document) {
                            TLRPC.Document document = (TLRPC.Document) objects.get(i);
                            NativeByteBuffer data = null;
                            try {
                                data = new NativeByteBuffer(document.getObjectSize());
                                document.serializeToStream(data);
                                state.requery();
                                state.bindLong(1, document.id);
                                state.bindByteBuffer(2, data);
                                state.step();
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                            if (data != null) {
                                data.reuse();
                            }
                        }
                    }
                    state.dispose();
                } catch (SQLiteException e) {
                    FileLog.e(e);
                }
            });
        }

        public void processDocuments(ArrayList<?> documents) {
            checkThread();
            for (int i = 0; i < documents.size(); ++i) {
                if (documents.get(i) instanceof TLRPC.Document) {
                    TLRPC.Document document = (TLRPC.Document) documents.get(i);
                    if (emojiDocumentsCache == null) {
                        emojiDocumentsCache = new HashMap<>();
                    }
                    emojiDocumentsCache.put(document.id, document);
                    if (loadingDocuments != null) {
                        ArrayList<ReceivedDocument> loadingCallbacks = loadingDocuments.remove(document.id);
                        if (loadingCallbacks != null) {
                            for (int j = 0; j < loadingCallbacks.size(); ++j) {
                                loadingCallbacks.get(j).run(document);
                            }
                            loadingCallbacks.clear();
                        }
                    }
                }
            }
        }

        public TLRPC.InputStickerSet findStickerSet(long documentId) {
            if (emojiDocumentsCache == null) {
                return null;
            }
            TLRPC.Document document = emojiDocumentsCache.get(documentId);
            if (document == null) {
                return null;
            }
            return MessageObject.getInputStickerSet(document);
        }
    }

    public static TLRPC.Document findDocument(int account, long documentId) {
        EmojiDocumentFetcher fetcher = getDocumentFetcher(account);
        if (fetcher == null || fetcher.emojiDocumentsCache == null) {
            return null;
        }
        return fetcher.emojiDocumentsCache.get(documentId);
    }

    public static TLRPC.InputStickerSet findStickerSet(int account, long documentId) {
        TLRPC.Document document = findDocument(account, documentId);
        return document == null ? null : MessageObject.getInputStickerSet(document);
    }

    private boolean attached;
    private ArrayList<View> views;
    private ArrayList<AnimatedEmojiSpan.AnimatedEmojiHolder> holders;

    public int sizedp;

    private TLRPC.Document document;
    private long documentId;
    private int cacheType;
    private int currentAccount;

    private ImageReceiver imageReceiver;
    private float alpha = 1f;

    public AnimatedEmojiDrawable(int cacheType, int currentAccount, long documentId) {
        this.currentAccount = currentAccount;
        this.cacheType = cacheType;
        if (this.cacheType == CACHE_TYPE_MESSAGES) {
            sizedp = (int) ((Math.abs(Theme.chat_msgTextPaint.ascent()) + Math.abs(Theme.chat_msgTextPaint.descent())) * 1.15f / AndroidUtilities.density);
        } else if (this.cacheType == CACHE_TYPE_MESSAGES_LARGE) {
            sizedp = (int) ((Math.abs(Theme.chat_msgTextPaintEmoji[2].ascent()) + Math.abs(Theme.chat_msgTextPaintEmoji[2].descent())) * 1.15f / AndroidUtilities.density);
        } else if (this.cacheType == CACHE_TYPE_KEYBOARD || this.cacheType == CACHE_TYPE_TAB_STRIP || this.cacheType == CACHE_TYPE_ALERT_PREVIEW) {
            sizedp = 34;
        } else if (this.cacheType == STANDARD_LOTTIE_FRAME) {
            sizedp = (int) ((Math.abs(Theme.chat_msgTextPaintEmoji[0].ascent()) + Math.abs(Theme.chat_msgTextPaintEmoji[0].descent())) * 1.15f / AndroidUtilities.density);
        } else {
            sizedp = 34;
        }

        this.documentId = documentId;
        getDocumentFetcher(currentAccount).fetchDocument(documentId, document -> {
            this.document = document;
            this.initDocument();
        });
    }

    public AnimatedEmojiDrawable(int cacheType, int currentAccount, @NonNull TLRPC.Document document) {
        this.cacheType = cacheType;
        this.currentAccount = currentAccount;
        this.document = document;
        if (this.cacheType == CACHE_TYPE_MESSAGES) {
            sizedp = (int) ((Math.abs(Theme.chat_msgTextPaint.ascent()) + Math.abs(Theme.chat_msgTextPaint.descent())) * 1.15f / AndroidUtilities.density);
        } else if (this.cacheType == CACHE_TYPE_MESSAGES_LARGE) {
            sizedp = (int) ((Math.abs(Theme.chat_msgTextPaintEmoji[2].ascent()) + Math.abs(Theme.chat_msgTextPaintEmoji[2].descent())) * 1.15f / AndroidUtilities.density);
        } else if (this.cacheType == CACHE_TYPE_KEYBOARD || this.cacheType == CACHE_TYPE_TAB_STRIP || this.cacheType == CACHE_TYPE_ALERT_PREVIEW) {
            sizedp = 34;
        } else if (this.cacheType == STANDARD_LOTTIE_FRAME) {
            sizedp = (int) ((Math.abs(Theme.chat_msgTextPaintEmoji[0].ascent()) + Math.abs(Theme.chat_msgTextPaintEmoji[0].descent())) * 1.15f / AndroidUtilities.density);
        } else {
            sizedp = 34;
        }

        this.initDocument();
    }

    public long getDocumentId() {
        return this.document != null ? this.document.id : this.documentId;
    }

    public TLRPC.Document getDocument() {
        return this.document;
    }

    private void initDocument() {
        if (document == null || imageReceiver != null) {
            return;
        }
        imageReceiver = new ImageReceiver() {
            @Override
            public void invalidate() {
                AnimatedEmojiDrawable.this.invalidate();
                super.invalidate();
            }

            @Override
            protected boolean setImageBitmapByKey(Drawable drawable, String key, int type, boolean memCache, int guid) {
                AnimatedEmojiDrawable.this.invalidate();
                return super.setImageBitmapByKey(drawable, key, type, memCache, guid);
            }
        };
        if (cacheType != 0) {
            imageReceiver.setUniqKeyPrefix(cacheType + "_");
        }
        boolean onlyStaticPreview = SharedConfig.getDevicePerformanceClass() == SharedConfig.PERFORMANCE_CLASS_LOW && (cacheType == CACHE_TYPE_KEYBOARD || cacheType == CACHE_TYPE_ALERT_PREVIEW);

        String filter = sizedp + "_" + sizedp;
        if (cacheType != STANDARD_LOTTIE_FRAME && (cacheType != CACHE_TYPE_MESSAGES_LARGE || SharedConfig.getDevicePerformanceClass() < SharedConfig.PERFORMANCE_CLASS_HIGH)) {
            filter += "_pcache";
        }
        if (cacheType != CACHE_TYPE_MESSAGES && cacheType != CACHE_TYPE_MESSAGES_LARGE) {
            filter += "_compress";
        }
        if (cacheType == STANDARD_LOTTIE_FRAME) {
            filter += "firstframe";
        }

        ImageLocation mediaLocation;
        String mediaFilter;
        Drawable thumbDrawable = null;
        TLRPC.PhotoSize thumb = FileLoader.getClosestPhotoSizeWithSize(document.thumbs, 90);
        if ("video/webm".equals(document.mime_type)) {
            mediaLocation = ImageLocation.getForDocument(document);
            mediaFilter = filter + "_" + ImageLoader.AUTOPLAY_FILTER;
        } else {
            String probableCacheKey = (cacheType != 0 ? cacheType + "_" : "") + documentId + "@" + filter;
            if (cacheType == CACHE_TYPE_KEYBOARD || !ImageLoader.getInstance().hasLottieMemCache(probableCacheKey)) {
                SvgHelper.SvgDrawable svgThumb = DocumentObject.getSvgThumb(document.thumbs, Theme.key_windowBackgroundWhiteGrayIcon, 0.2f);
                if (svgThumb != null) {
                    svgThumb.overrideWidthAndHeight(512, 512);
                }
                thumbDrawable = svgThumb;
            }
            mediaLocation = ImageLocation.getForDocument(document);
            mediaFilter = filter;
        }
        if (onlyStaticPreview) {
            mediaLocation = null;
        }
        if (cacheType == STANDARD_LOTTIE_FRAME) {
            imageReceiver.setImage(null, null, mediaLocation, mediaFilter, null, null, thumbDrawable, document.size, null, document, 1);

        } else {
            imageReceiver.setImage(mediaLocation, mediaFilter, ImageLocation.getForDocument(thumb, document), sizedp + "_" + sizedp, null, null, thumbDrawable, document.size, null, document, 1);

        }

        if (cacheType == CACHE_TYPE_ALERT_PREVIEW) {
            imageReceiver.setLayerNum(7);
        }
        imageReceiver.setAspectFit(true);
        if (cacheType != STANDARD_LOTTIE_FRAME) {
            imageReceiver.setAllowStartLottieAnimation(true);
            imageReceiver.setAllowStartAnimation(true);
            imageReceiver.setAutoRepeat(1);
        } else {
            imageReceiver.setAllowStartAnimation(false);
            imageReceiver.setAllowStartLottieAnimation(false);
            imageReceiver.setAutoRepeat(0);
        }
        imageReceiver.setAllowDecodeSingleFrame(true);
        updateAttachState();
        invalidate();
    }

    void invalidate() {
        if (views != null) {
            for (int i = 0; i < views.size(); ++i) {
                View view = views.get(i);
                if (view != null) {
                    view.invalidate();
                }
            }
        }
        if (holders != null) {
            for (int i = 0; i < holders.size(); ++i) {
                AnimatedEmojiSpan.AnimatedEmojiHolder holder = holders.get(i);
                if (holder != null) {
                    holder.invalidate();
                }
            }
        }
    }

    @Override
    public String toString() {
        return "AnimatedEmojiDrawable{" + (document == null ? "null" : MessageObject.findAnimatedEmojiEmoticon(document, null)) + "}";
    }

    private static Paint placeholderPaint;
    public static void updatePlaceholderPaintColor() {
        if (placeholderPaint != null) {
            placeholderPaint.setColor(Theme.isCurrentThemeDark() ? 0x0fffffff : 0x0f000000);
        }
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        drawDebugCacheType(canvas);
        if (imageReceiver != null) {
            imageReceiver.setImageCoords(getBounds());
            imageReceiver.setAlpha(alpha);
            imageReceiver.draw(canvas);
        } else {
            shouldDrawPlaceholder = true;
        }
        drawPlaceholder(canvas, getBounds().centerX(), getBounds().centerY(), getBounds().width() / 2f);
    }

    public void draw(Canvas canvas, Rect drawableBounds, float alpha) {
        drawDebugCacheType(canvas);
        if (imageReceiver != null) {
            imageReceiver.setImageCoords(drawableBounds);
            imageReceiver.setAlpha(alpha);
            imageReceiver.draw(canvas);
        } else {
            shouldDrawPlaceholder = true;
        }
        if (drawableBounds != null) {
            drawPlaceholder(canvas, drawableBounds.centerX(), drawableBounds.centerY(), drawableBounds.width() / 2f);
        }
    }
    public void draw(Canvas canvas, ImageReceiver.BackgroundThreadDrawHolder backgroundThreadDrawHolder) {
        drawDebugCacheType(canvas);
        if (imageReceiver != null) {
            imageReceiver.setAlpha(alpha);
            imageReceiver.draw(canvas, backgroundThreadDrawHolder);
        } else {
            shouldDrawPlaceholder = true;
        }
        if (backgroundThreadDrawHolder != null) {
            drawPlaceholder(canvas, backgroundThreadDrawHolder.imageX + backgroundThreadDrawHolder.imageW / 2, backgroundThreadDrawHolder.imageY + backgroundThreadDrawHolder.imageH / 2, backgroundThreadDrawHolder.imageW / 2);
        }
    }

    private void drawDebugCacheType(Canvas canvas) {
        if (DEBUG_CACHE_TYPE) {
            if (debugCacheType == null) {
                debugCacheType = new Paint(Paint.ANTI_ALIAS_FLAG);
            }
            debugCacheType.setColor(0xff000000 | (cacheType % 2 == 0 ? 0x00ff0000 : 0x0000ff00) | (cacheType % 3 == 0 ? 0x000000ff : 0x00ff0000));
            canvas.drawRect(getBounds(), debugCacheType);
        }
    }

    private AnimatedFloat placeholderAlpha = new AnimatedFloat(1f, this::invalidate, 0, 150, new LinearInterpolator());
    private boolean shouldDrawPlaceholder = false;
    private void drawPlaceholder(Canvas canvas, float cx, float cy, float r) {
//        if (!shouldDrawPlaceholder) {
//            return;
//        }
//        float alpha = placeholderAlpha.set(imageReceiver == null ? 1f : 0f);
//        if (alpha < 0) {
//            if (imageReceiver != null) {
//                shouldDrawPlaceholder = false;
//            }
//            return;
//        }
//        if (placeholderPaint == null) {
//            placeholderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
//            placeholderPaint.setColor(Theme.isCurrentThemeDark() ? 0x0fffffff : 0x0f000000);
//        }
//        int wasAlpha = placeholderPaint.getAlpha();
//        placeholderPaint.setAlpha((int) (wasAlpha * alpha));
//        canvas.drawCircle(cx, cy, r, placeholderPaint);
//        placeholderPaint.setAlpha(wasAlpha);
    }

    public void addView(View callback) {
        if (views == null) {
            views = new ArrayList<>(10);
        }
        if (!views.contains(callback)) {
            views.add(callback);
        }
        updateAttachState();
    }

    public void addView(AnimatedEmojiSpan.AnimatedEmojiHolder holder) {
        if (holders == null) {
            holders = new ArrayList<>(10);
        }
        if (!holders.contains(holder)) {
            holders.add(holder);
        }
        updateAttachState();
    }

    public void removeView(AnimatedEmojiSpan.AnimatedEmojiHolder holder) {
        if (holders != null) {
            holders.remove(holder);
        }
        updateAttachState();
    }

    public void removeView(View view) {
        if (views != null) {
            views.remove(view);
        }
        updateAttachState();
    }

    int count;
    private void updateAttachState() {
        if (imageReceiver == null) {
            return;
        }
        boolean attach = (views != null && views.size() > 0) || (holders != null && holders.size() > 0);
        if (attach != attached) {
            attached = attach;
            if (attached) {
                count++;
                imageReceiver.onAttachedToWindow();
            } else {
                count--;
                imageReceiver.onDetachedFromWindow();
            }
        }

//        if (globalEmojiCache != null && (views == null || views.size() <= 0) && (holders == null || holders.size() <= 0) && globalEmojiCache.size() > 300) {
//            HashMap<Long, AnimatedEmojiDrawable> cache = globalEmojiCache.get(currentAccount);
//            if (cache != null) {
//                cache.remove(documentId);
//            }
//        }
    }

    @Override
    public void setAlpha(int alpha) {
        this.alpha = alpha / 255f;
        if (imageReceiver != null) {
            imageReceiver.setAlpha(this.alpha);
        }
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {

    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSPARENT;
    }

    public ImageReceiver getImageReceiver() {
        return imageReceiver;
    }

}