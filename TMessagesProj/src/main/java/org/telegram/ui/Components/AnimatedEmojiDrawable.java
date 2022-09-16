package org.telegram.ui.Components;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.view.animation.OvershootInterpolator;

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
import org.telegram.messenger.UserConfig;
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
    public static final int CACHE_TYPE_ALERT_PREVIEW_LARGE = 4;
    public static final int CACHE_TYPE_ALERT_PREVIEW_TAB_STRIP = 5;
    public static final int CACHE_TYPE_TAB_STRIP = 6;
    public static final int CACHE_TYPE_EMOJI_STATUS = 7;
    public static final int STANDARD_LOTTIE_FRAME = 8;
    public static final int CACHE_TYPE_ALERT_EMOJI_STATUS = 9;

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
    private ArrayList<AnimatedEmojiSpan.InvalidateHolder> holders;

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
        updateSize();
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
        updateSize();
        this.initDocument();
    }

    private void updateSize() {
        if (this.cacheType == CACHE_TYPE_MESSAGES) {
            sizedp = (int) ((Math.abs(Theme.chat_msgTextPaint.ascent()) + Math.abs(Theme.chat_msgTextPaint.descent())) * 1.15f / AndroidUtilities.density);
        } else if (this.cacheType == CACHE_TYPE_MESSAGES_LARGE || this.cacheType == CACHE_TYPE_ALERT_PREVIEW_LARGE) {
            sizedp = (int) ((Math.abs(Theme.chat_msgTextPaintEmoji[2].ascent()) + Math.abs(Theme.chat_msgTextPaintEmoji[2].descent())) * 1.15f / AndroidUtilities.density);
        } else if (this.cacheType == STANDARD_LOTTIE_FRAME) {
            sizedp = (int) ((Math.abs(Theme.chat_msgTextPaintEmoji[0].ascent()) + Math.abs(Theme.chat_msgTextPaintEmoji[0].descent())) * 1.15f / AndroidUtilities.density);
        } else {
            sizedp = 34;
        }
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
        if (colorFilterToSet != null && canOverrideColor()) {
            imageReceiver.setColorFilter(colorFilterToSet);
        }
        if (cacheType != 0) {
            imageReceiver.setUniqKeyPrefix(cacheType + "_");
        }
        imageReceiver.setVideoThumbIsSame(true);
        boolean onlyStaticPreview = SharedConfig.getDevicePerformanceClass() == SharedConfig.PERFORMANCE_CLASS_LOW && (cacheType == CACHE_TYPE_KEYBOARD || cacheType == CACHE_TYPE_ALERT_PREVIEW || cacheType == CACHE_TYPE_ALERT_PREVIEW_TAB_STRIP);

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
            SvgHelper.SvgDrawable svgThumb = DocumentObject.getSvgThumb(document.thumbs, Theme.key_windowBackgroundWhiteGrayIcon, 0.2f);
            thumbDrawable = svgThumb;
        } else if ("application/x-tgsticker".equals(document.mime_type)) {
            String probableCacheKey = (cacheType != 0 ? cacheType + "_" : "") + documentId + "@" + filter;
            if (cacheType == CACHE_TYPE_KEYBOARD || !ImageLoader.getInstance().hasLottieMemCache(probableCacheKey)) {
                SvgHelper.SvgDrawable svgThumb = DocumentObject.getSvgThumb(document.thumbs, Theme.key_windowBackgroundWhiteGrayIcon, 0.2f);
                if (svgThumb != null && MessageObject.isAnimatedStickerDocument(document, true)) {
                    svgThumb.overrideWidthAndHeight(512, 512);
                }
                thumbDrawable = svgThumb;
            }
            mediaLocation = ImageLocation.getForDocument(document);
            mediaFilter = filter;
        } else {
            SvgHelper.SvgDrawable svgThumb = DocumentObject.getSvgThumb(document.thumbs, Theme.key_windowBackgroundWhiteGrayIcon, 0.2f);
            if (svgThumb != null && MessageObject.isAnimatedStickerDocument(document, true)) {
                svgThumb.overrideWidthAndHeight(512, 512);
            }
            thumbDrawable = svgThumb;
            mediaLocation = null;
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

        if (cacheType == CACHE_TYPE_EMOJI_STATUS || cacheType == CACHE_TYPE_ALERT_EMOJI_STATUS) {
            imageReceiver.setAutoRepeatCount(2);
        }

        if (cacheType == CACHE_TYPE_ALERT_PREVIEW || cacheType == CACHE_TYPE_ALERT_PREVIEW_TAB_STRIP || cacheType == CACHE_TYPE_ALERT_PREVIEW_LARGE) {
            imageReceiver.setLayerNum(7);
        }
        if (cacheType == CACHE_TYPE_ALERT_EMOJI_STATUS) {
            imageReceiver.setLayerNum(6656);
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
        int roundRadius = 0;
        if (cacheType == CACHE_TYPE_ALERT_PREVIEW_TAB_STRIP || cacheType == CACHE_TYPE_TAB_STRIP) {
            roundRadius = AndroidUtilities.dp(6);
        }
        imageReceiver.setRoundRadius(roundRadius);
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
                AnimatedEmojiSpan.InvalidateHolder holder = holders.get(i);
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
        draw(canvas, true);
    }

    public void draw(@NonNull Canvas canvas, boolean canTranslate) {
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

    public void draw(Canvas canvas, ImageReceiver.BackgroundThreadDrawHolder backgroundThreadDrawHolder, boolean canTranslate) {
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

    public void addView(AnimatedEmojiSpan.InvalidateHolder holder) {
        if (holders == null) {
            holders = new ArrayList<>(10);
        }
        if (!holders.contains(holder)) {
            holders.add(holder);
        }
        updateAttachState();
    }

    public void removeView(AnimatedEmojiSpan.InvalidateHolder holder) {
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

    private void updateAttachState() {
        if (imageReceiver == null) {
            return;
        }
        boolean attach = (views != null && views.size() > 0) || (holders != null && holders.size() > 0);
        if (attach != attached) {
            attached = attach;
            if (attached) {
                imageReceiver.onAttachedToWindow();
            } else {
                imageReceiver.onDetachedFromWindow();
            }
        }

//        if (globalEmojiCache != null && (views == null || views.size() <= 0) && (holders == null || holders.size() <= 0) && globalEmojiCache.size() > 50) {
//            HashMap<Long, AnimatedEmojiDrawable> cache = globalEmojiCache.get(currentAccount);
//            if (cache != null) {
//                cache.remove(documentId);
//            }
//        }
    }

    private Boolean canOverrideColorCached = null;
    public boolean canOverrideColor() {
        if (canOverrideColorCached != null) {
            return canOverrideColorCached;
        }
        if (document != null) {
            TLRPC.InputStickerSet set = MessageObject.getInputStickerSet(document);
            return canOverrideColorCached = (
                set instanceof TLRPC.TL_inputStickerSetEmojiDefaultStatuses ||
                set instanceof TLRPC.TL_inputStickerSetID && set.id == 773947703670341676L
            );
        }
        return false;
    }

    @Override
    public int getAlpha() {
        return (int) (255 * this.alpha);
    }

    @Override
    public void setAlpha(int alpha) {
        this.alpha = alpha / 255f;
        if (imageReceiver != null) {
            imageReceiver.setAlpha(this.alpha);
        }
    }

    private ColorFilter colorFilterToSet;

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
        if (imageReceiver == null) {
            colorFilterToSet = colorFilter;
        } else if (canOverrideColor()) {
            imageReceiver.setColorFilter(colorFilter);
        }
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSPARENT;
    }

    public ImageReceiver getImageReceiver() {
        return imageReceiver;
    }

    public static class WrapSizeDrawable extends Drawable {

        private Drawable drawable;
        int width, height;

        public WrapSizeDrawable(Drawable drawable, int width, int height) {
            this.drawable = drawable;
            this.width = width;
            this.height = height;
        }

        public Drawable getDrawable() {
            return drawable;
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            if (this.drawable != null) {
                this.drawable.setBounds(getBounds());
                this.drawable.setAlpha(this.alpha);
                this.drawable.draw(canvas);
            }
        }

        @Override
        public int getIntrinsicWidth() {
            return this.width;
        }

        @Override
        public int getIntrinsicHeight() {
            return this.height;
        }

        private int alpha = 255;
        @Override
        public void setAlpha(int alpha) {
            this.alpha = alpha;
            if (this.drawable != null) {
                this.drawable.setAlpha(alpha);
            }
        }

        @Override
        public void setColorFilter(@Nullable ColorFilter colorFilter) {
            if (this.drawable != null) {
                this.drawable.setColorFilter(colorFilter);
            }
        }

        @Override
        public int getOpacity() {
            if (this.drawable != null) {
                return this.drawable.getOpacity();
            }
            return PixelFormat.TRANSPARENT;
        }
    }

    public static class SwapAnimatedEmojiDrawable extends Drawable {

        public boolean center = false;

        private int cacheType;
        private OvershootInterpolator overshootInterpolator = new OvershootInterpolator(2f);
        private AnimatedFloat changeProgress = new AnimatedFloat((View) null, 300, CubicBezierInterpolator.EASE_OUT);
        private Drawable[] drawables = new Drawable[2];
        private View parentView;
        private int size;
        private int alpha = 255;

        public SwapAnimatedEmojiDrawable(View parentView, int size) {
            this(parentView, size, CACHE_TYPE_EMOJI_STATUS);
        }

        public SwapAnimatedEmojiDrawable(View parentView, int size, int cacheType) {
            changeProgress.setParent(this.parentView = parentView);
            this.size = size;
            this.cacheType = cacheType;
        }

        public void setParentView(View parentView) {
            removeParentView(this.parentView);
            addParentView(this.parentView = parentView);
            changeProgress.setParent(parentView);
            this.parentView = parentView;
        }

        public void addParentView(View parentView) {
            if (drawables[0] instanceof AnimatedEmojiDrawable) {
                ((AnimatedEmojiDrawable) drawables[0]).addView(parentView);
            }
            if (drawables[1] instanceof AnimatedEmojiDrawable) {
                ((AnimatedEmojiDrawable) drawables[1]).addView(parentView);
            }
        }

        public void removeParentView(View parentView) {
            if (drawables[0] instanceof AnimatedEmojiDrawable) {
                ((AnimatedEmojiDrawable) drawables[0]).removeView(parentView);
            }
            if (drawables[1] instanceof AnimatedEmojiDrawable) {
                ((AnimatedEmojiDrawable) drawables[1]).removeView(parentView);
            }
        }

        public void play() {
            if (getDrawable() instanceof AnimatedEmojiDrawable) {
                AnimatedEmojiDrawable drawable = (AnimatedEmojiDrawable) getDrawable();
                ImageReceiver imageReceiver = drawable.getImageReceiver();
                if (imageReceiver != null) {
                    if (drawable.cacheType == CACHE_TYPE_EMOJI_STATUS || drawable.cacheType == CACHE_TYPE_ALERT_EMOJI_STATUS) {
                        imageReceiver.setAutoRepeatCount(2);
                    }
                    imageReceiver.startAnimation();
                }
            }
        }

        private Integer lastColor;
        private ColorFilter colorFilter;
        public void setColor(Integer color) {
            if (lastColor == null && color == null || lastColor != null && lastColor.equals(color)) {
                return;
            }
            lastColor = color;
            colorFilter = color != null ? new PorterDuffColorFilter(color, PorterDuff.Mode.MULTIPLY) : null;
        }

        public Integer getColor() {
            return lastColor;
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            float progress = changeProgress.set(1);
            Rect bounds = getBounds();
            if (drawables[1] != null && progress < 1) {
                drawables[1].setAlpha((int) (alpha * (1f - progress)));
                if (drawables[1] instanceof AnimatedEmojiDrawable) {
                    drawables[1].setBounds(bounds);
                } else if (center) {
                    drawables[1].setBounds(
                        bounds.centerX() - drawables[1].getIntrinsicWidth() / 2,
                        bounds.centerY() - drawables[1].getIntrinsicHeight() / 2,
                        bounds.centerX() + drawables[1].getIntrinsicWidth() / 2,
                        bounds.centerY() + drawables[1].getIntrinsicHeight() / 2
                    );
                } else { // left
                    drawables[1].setBounds(
                        bounds.left,
                        bounds.centerY() - drawables[1].getIntrinsicHeight() / 2,
                        bounds.left + drawables[1].getIntrinsicWidth(),
                        bounds.centerY() + drawables[1].getIntrinsicHeight() / 2
                    );
                }
                drawables[1].setColorFilter(colorFilter);
                drawables[1].draw(canvas);
                drawables[1].setColorFilter(null);
            }
            if (drawables[0] != null) {
                canvas.save();
                if (drawables[0] instanceof AnimatedEmojiDrawable) {
                    if (((AnimatedEmojiDrawable) drawables[0]).imageReceiver != null) {
                        ((AnimatedEmojiDrawable) drawables[0]).imageReceiver.setRoundRadius(AndroidUtilities.dp(4));
                    }
                    if (progress < 1) {
                        float scale = overshootInterpolator.getInterpolation(progress);
                        canvas.scale(scale, scale, bounds.centerX(), bounds.centerY());
                    }
                    drawables[0].setBounds(bounds);
                } else if (center) {
                    if (progress < 1) {
                        float scale = overshootInterpolator.getInterpolation(progress);
                        canvas.scale(scale, scale, bounds.centerX(), bounds.centerY());
                    }
                    drawables[0].setBounds(
                        bounds.centerX() - drawables[0].getIntrinsicWidth() / 2,
                        bounds.centerY() - drawables[0].getIntrinsicHeight() / 2,
                        bounds.centerX() + drawables[0].getIntrinsicWidth() / 2,
                        bounds.centerY() + drawables[0].getIntrinsicHeight() / 2
                    );
                } else { // left
                    if (progress < 1) {
                        float scale = overshootInterpolator.getInterpolation(progress);
                        canvas.scale(scale, scale, bounds.left + drawables[0].getIntrinsicWidth() / 2f, bounds.centerY());
                    }
                    drawables[0].setBounds(
                        bounds.left,
                        bounds.centerY() - drawables[0].getIntrinsicHeight() / 2,
                        bounds.left + drawables[0].getIntrinsicWidth(),
                        bounds.centerY() + drawables[0].getIntrinsicHeight() / 2
                    );
                }
                drawables[0].setAlpha(alpha);
                drawables[0].setColorFilter(colorFilter);
                drawables[0].draw(canvas);
                drawables[0].setColorFilter(null);
                canvas.restore();
            }
        }

        public Drawable getDrawable() {
            return drawables[0];
        }

        public void set(long documentId, boolean animated) {
            set(documentId, cacheType, animated);
        }

        public void set(long documentId, int cacheType, boolean animated) {
            if (drawables[0] instanceof AnimatedEmojiDrawable && ((AnimatedEmojiDrawable) drawables[0]).getDocumentId() == documentId) {
                return;
            }
            if (animated) {
                changeProgress.set(0, true);
                if (drawables[1] != null) {
                    if (drawables[1] instanceof AnimatedEmojiDrawable) {
                        ((AnimatedEmojiDrawable) drawables[1]).removeView(parentView);
                    }
                    drawables[1] = null;
                }
                drawables[1] = drawables[0];
                drawables[0] = AnimatedEmojiDrawable.make(UserConfig.selectedAccount, cacheType, documentId);
                ((AnimatedEmojiDrawable) drawables[0]).addView(parentView);
            } else {
                changeProgress.set(1, true);
                detach();
                drawables[0] = AnimatedEmojiDrawable.make(UserConfig.selectedAccount, cacheType, documentId);
                ((AnimatedEmojiDrawable) drawables[0]).addView(parentView);
            }
            lastColor = 0xffffffff;
            colorFilter = null;
            play();
            if (parentView != null) {
                parentView.invalidate();
            }
        }

        public void set(TLRPC.Document document, boolean animated) {
            set(document, cacheType, animated);
        }

        public void set(TLRPC.Document document, int cacheType, boolean animated) {
            if (drawables[0] instanceof AnimatedEmojiDrawable && document != null && ((AnimatedEmojiDrawable) drawables[0]).getDocumentId() == document.id) {
                return;
            }
            if (animated) {
                changeProgress.set(0, true);
                if (drawables[1] != null) {
                    if (drawables[1] instanceof AnimatedEmojiDrawable) {
                        ((AnimatedEmojiDrawable) drawables[1]).removeView(parentView);
                    }
                    drawables[1] = null;
                }
                drawables[1] = drawables[0];
                if (document != null) {
                    drawables[0] = AnimatedEmojiDrawable.make(UserConfig.selectedAccount, cacheType, document);
                    ((AnimatedEmojiDrawable) drawables[0]).addView(parentView);
                } else {
                    drawables[0] = null;
                }
            } else {
                changeProgress.set(1, true);
                detach();
                if (document != null) {
                    drawables[0] = AnimatedEmojiDrawable.make(UserConfig.selectedAccount, cacheType, document);
                    ((AnimatedEmojiDrawable) drawables[0]).addView(parentView);
                } else {
                    drawables[0] = null;
                }
            }
            lastColor = 0xffffffff;
            colorFilter = null;
            play();
            if (parentView != null) {
                parentView.invalidate();
            }
        }

        public void set(Drawable drawable, boolean animated) {
            if (drawables[0] == drawable) {
                return;
            }
            if (animated) {
                changeProgress.set(0, true);
                if (drawables[1] != null) {
                    if (drawables[1] instanceof AnimatedEmojiDrawable) {
                        ((AnimatedEmojiDrawable) drawables[1]).removeView(parentView);
                    }
                    drawables[1] = null;
                }
                drawables[1] = drawables[0];
                drawables[0] = drawable;
            } else {
                changeProgress.set(1, true);
                detach();
                drawables[0] = drawable;
            }
            lastColor = 0xffffffff;
            colorFilter = null;
            play();
            if (parentView != null) {
                parentView.invalidate();
            }
        }

        public void detach() {
            if (drawables[0] instanceof AnimatedEmojiDrawable) {
                ((AnimatedEmojiDrawable) drawables[0]).removeView(parentView);
            }
            if (drawables[1] instanceof AnimatedEmojiDrawable) {
                ((AnimatedEmojiDrawable) drawables[1]).removeView(parentView);
            }
        }

        public void attach() {
            if (drawables[0] instanceof AnimatedEmojiDrawable) {
                ((AnimatedEmojiDrawable) drawables[0]).addView(parentView);
            }
            if (drawables[1] instanceof AnimatedEmojiDrawable) {
                ((AnimatedEmojiDrawable) drawables[1]).addView(parentView);
            }
        }

        @Override
        public int getIntrinsicWidth() {
            return size;
        }

        @Override
        public int getIntrinsicHeight() {
            return size;
        }

        @Override
        public void setAlpha(int i) {
            alpha = i;
        }
        @Override
        public void setColorFilter(@Nullable ColorFilter colorFilter) {}
        @Override
        public int getOpacity() {
            return PixelFormat.TRANSPARENT;
        }
    }
}