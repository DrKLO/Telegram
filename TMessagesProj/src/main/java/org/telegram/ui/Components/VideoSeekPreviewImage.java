package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.text.TextPaint;
import android.view.View;
import android.view.ViewGroup;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Bitmaps;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;

import java.io.File;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

public class VideoSeekPreviewImage extends View implements NotificationCenter.NotificationCenterDelegate {
    public final static boolean IS_YOUTUBE_PREVIEWS_SUPPORTED = true;

    private boolean open;
    private boolean isQualities;
    private AnimatedFileDrawable fileDrawable;
    private long duration;
    private Uri videoUri;
    private Runnable loadRunnable;
    private Runnable progressRunnable;
    private float pendingProgress;
    private int currentPixel = -1;
    private int pixelWidth;
    private boolean ready;
    private Bitmap bitmapToRecycle;
    private Bitmap bitmapToDraw;
    private Drawable frameDrawable;

    private String frameTime;
    private int timeWidth;
    private final TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);

    private BitmapShader bitmapShader;
    private RectF dstR = new RectF();
    private Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private Paint bitmapPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private RectF bitmapRect = new RectF();
    private Matrix matrix = new Matrix();

    private VideoSeekPreviewImageDelegate delegate;

    private PhotoViewerWebView webView;
    private double lastPosition;
    private boolean isYoutube, drawStoryBoard;
    private ImageReceiver storyBoardsReceiver;
    private int ytImageX, ytImageY, ytImageWidth, ytImageHeight;
    private final Path ytPath = new Path();

    private static final class StoryBoardFrame {
        public final double pts;
        public final int left, top;
        public StoryBoardFrame(double pts, int left, int top) {
            this.pts = pts;
            this.left = left;
            this.top = top;
        }
    }

    private long storyBoardMapDocId;
    private long storyBoardPictureDocId;
    private int storyBoardFrameWidth, storyBoardFrameHeight;
    private ArrayList<StoryBoardFrame> storyBoardMap;

    private TLRPC.Document downloadingStoryboardMapDocument;
    private String downloadingStoryBoardMapFilename;

    public interface VideoSeekPreviewImageDelegate {
        void onReady();
    }

    public VideoSeekPreviewImage(Context context, VideoSeekPreviewImageDelegate videoSeekPreviewImageDelegate) {
        super(context);
        setVisibility(INVISIBLE);

        frameDrawable = context.getResources().getDrawable(R.drawable.videopreview);
        textPaint.setTextSize(dp(13));
        textPaint.setColor(0xffffffff);

        delegate = videoSeekPreviewImageDelegate;
        storyBoardsReceiver = new ImageReceiver();
        storyBoardsReceiver.setParentView(this);

        storyBoardsReceiver.setDelegate((imageReceiver, set, thumb, memCache) -> {
            if (set) {
                if (webView == null && storyBoardMap == null) {
                    return;
                }
                int viewSize = dp(150);

                if (webView != null) {
                    int imageCount = webView.getYoutubeStoryboardImageCount((int) lastPosition);
                    int rows = (int) Math.ceil(imageCount / 5f);
                    int columns = Math.min(imageCount, 5);

                    float bitmapWidth = storyBoardsReceiver.getBitmapWidth() / (float) columns;
                    float bitmapHeight = storyBoardsReceiver.getBitmapHeight() / (float) rows;

                    int imageIndex = Math.min(webView.getYoutubeStoryboardImageIndex((int) lastPosition), imageCount - 1);
                    int row = imageIndex / 5;
                    int column = imageIndex % 5;

                    ytImageX = (int) (column * bitmapWidth);
                    ytImageY = (int) (row * bitmapHeight);
                    ytImageWidth = (int) bitmapWidth;
                    ytImageHeight = (int) bitmapHeight;
                } else {
                    StoryBoardFrame frame = null;
                    for (int i = 0; i < storyBoardMap.size(); ++i) {
                        final StoryBoardFrame f = storyBoardMap.get(i);
                        final double left = i == 0 ? 0 : f.pts;
                        final double right = i == storyBoardMap.size() - 1 ? 99999999 : storyBoardMap.get(i + 1).pts;
                        if (lastPosition >= left && lastPosition <= right) {
                            frame = f;
                            break;
                        }
                    }
                    if (frame != null) {
                        ytImageX = frame.left;
                        ytImageY = frame.top;
                        ytImageWidth = storyBoardFrameWidth;
                        ytImageHeight = storyBoardFrameHeight;
                    } else return;
                }

                drawStoryBoard = true;
                float aspect = (float) ytImageWidth / ytImageHeight;
                int viewWidth;
                int viewHeight;
                if (aspect > 1.0f) {
                    viewWidth = viewSize;
                    viewHeight = (int) (viewSize / aspect);
                } else {
                    viewHeight = viewSize;
                    viewWidth = (int) (viewSize * aspect);
                }
                ViewGroup.LayoutParams layoutParams = getLayoutParams();
                if (getVisibility() != VISIBLE || layoutParams.width != viewWidth || layoutParams.height != viewHeight) {
                    layoutParams.width = viewWidth;
                    layoutParams.height = viewHeight;
                    setVisibility(VISIBLE);
                    requestLayout();
                }
            }
        });
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        storyBoardsReceiver.onAttachedToWindow();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        storyBoardsReceiver.onDetachedFromWindow();
    }

    public void setProgressForYouTube(PhotoViewerWebView webView, float progress, int w) {
        this.webView = webView;
        isYoutube = true;
        if (storyBoardMapDocId != 0) {
            storyBoardMapDocId = 0;
            downloadingStoryBoardMapFilename = null;
            downloadingStoryboardMapDocument = null;
            storyBoardMap = null;
            listen(-1);
        }

        if (w != 0) {
            pixelWidth = w;
            int pixel = (int) (w * progress) / 5;
            if (currentPixel == pixel) {
                return;
            }
            currentPixel = pixel;
        }
        long time = (long) (webView.getVideoDuration() * progress);
        frameTime = AndroidUtilities.formatShortDuration((int) (time / 1000));
        timeWidth = (int) Math.ceil(textPaint.measureText(frameTime));
        invalidate();

        if (progressRunnable != null) {
            Utilities.globalQueue.cancelRunnable(progressRunnable);
        }

        lastPosition = progress * webView.getVideoDuration() / 1000.0;
        String url = webView.getYoutubeStoryboard((int) lastPosition);
        if (url != null) {
            storyBoardsReceiver.setImage(url, null, null, null, 0);
        }
    }

    public void setProgress(MessageObject messageObject, float progress, int w) {
        webView = null;
        isYoutube = false;

        boolean usingStoryboard = false;
        if (storyBoardMap != null) {
            TLRPC.Document pictureDocument = findDocumentById(messageObject, storyBoardPictureDocId);
            if (pictureDocument != null) {
                usingStoryboard = true;
                lastPosition = progress * duration / 1000.0;
                storyBoardsReceiver.setImage(ImageLocation.getForDocument(pictureDocument), null, null, null, messageObject, 0);
            } else {
                storyBoardsReceiver.setImageBitmap((Drawable) null);
            }
        } else {
            storyBoardsReceiver.setImageBitmap((Drawable) null);
        }
        drawStoryBoard = usingStoryboard;

        if (w != 0) {
            pixelWidth = w;
            int pixel = (int) (w * progress) / 5;
            if (currentPixel == pixel) {
                return;
            }
            currentPixel = pixel;
        }
        long time = (long) (duration * progress);
        frameTime = AndroidUtilities.formatShortDuration((int) (time / 1000));
        timeWidth = (int) Math.ceil(textPaint.measureText(frameTime));
        invalidate();

        if (progressRunnable != null) {
            Utilities.globalQueue.cancelRunnable(progressRunnable);
        }
        if (usingStoryboard) return;
        AnimatedFileDrawable file = fileDrawable;
        if (file != null) {
            file.resetStream(false);
        }
        Utilities.globalQueue.postRunnable(progressRunnable = () -> {
            if (fileDrawable == null) {
                pendingProgress = progress;
                return;
            }
            final int bitmapSize = Math.max(200, dp(100));
            Bitmap bitmap = fileDrawable.getFrameAtTime(time, false);
//            final long resultedTime = fileDrawable.getProgressMs();
//            if (Math.abs(time - resultedTime) > Math.max(1500, 0.10f * duration)) {
//                bitmap = fileDrawable.getFrameAtTime(time, true);
//            }
            if (bitmap != null) {
                int width = bitmap.getWidth();
                int height = bitmap.getHeight();

                if (width > height) {
                    float scale = width / (float) bitmapSize;
                    width = bitmapSize;
                    height /= scale;
                } else {
                    float scale = height / (float) bitmapSize;
                    height = bitmapSize;
                    width /= scale;
                }
                try {
                    Bitmap backgroundBitmap = Bitmaps.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                    dstR.set(0, 0, width, height);
                    Canvas canvas = new Canvas(backgroundBitmap);
                    canvas.drawBitmap(bitmap, null, dstR, paint);
                    canvas.setBitmap(null);
                    bitmap = backgroundBitmap;
                } catch (Throwable ignore) {
                    bitmap = null;
                }
            }
            Bitmap bitmapFinal = bitmap;
            AndroidUtilities.runOnUIThread(() -> {
                if (bitmapFinal != null) {
                    if (bitmapToDraw != null) {
                        if (bitmapToRecycle != null) {
                            bitmapToRecycle.recycle();
                        }
                        bitmapToRecycle = bitmapToDraw;
                    }
                    bitmapToDraw = bitmapFinal;
                    bitmapShader = new BitmapShader(bitmapToDraw, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
                    bitmapShader.setLocalMatrix(matrix);
                    bitmapPaint.setShader(bitmapShader);
                    invalidate();
                    final int viewSize = dp(150);
                    final float bitmapWidth = bitmapFinal.getWidth();
                    final float bitmapHeight = bitmapFinal.getHeight();
                    final float aspect = bitmapWidth / bitmapHeight;
                    final int viewWidth;
                    final int viewHeight;
                    if (aspect > 1.0f) {
                        viewWidth = viewSize;
                        viewHeight = (int) (viewSize / aspect);
                    } else {
                        viewHeight = viewSize;
                        viewWidth = (int) (viewSize * aspect);
                    }
                    ViewGroup.LayoutParams layoutParams = getLayoutParams();
                    if (getVisibility() != VISIBLE || layoutParams.width != viewWidth || layoutParams.height != viewHeight) {
                        layoutParams.width = viewWidth;
                        layoutParams.height = viewHeight;
                        setVisibility(VISIBLE);
                        requestLayout();
                    }
                }
                progressRunnable = null;
            });
        });
    }

    private static final boolean FORCE_STORYBOARD_EVEN_ON_CACHED = true;
    public void open(MessageObject messageObject, VideoPlayer videoPlayer) {
        if (videoPlayer == null) return;
        final boolean isCached;
        if (videoPlayer.getQualitiesCount() > 0) {
            VideoPlayer.VideoUri suitableUri = null;
            for (int i = 0; i < videoPlayer.getQualitiesCount(); ++i) {
                final VideoPlayer.Quality q = videoPlayer.getQuality(i);
                for (final VideoPlayer.VideoUri uri : q.uris) {
                    if (suitableUri == null || !suitableUri.isCached() && uri.isCached() || (suitableUri.isCached() == uri.isCached()) && uri.width * uri.height < suitableUri.width * suitableUri.height) {
                        suitableUri = uri;
                    }
                }
            }
            if (suitableUri != null && !suitableUri.isCached()) {
                final VideoPlayer.Quality q = videoPlayer.getCurrentQuality();
                if (q != null) {
                    suitableUri = q.getDownloadUri();
                }
            }
            if (suitableUri != null && !suitableUri.isCached()) {
                // TODO
                close();
                return;
            }
            isCached = suitableUri == null || suitableUri.isCached();
            open(messageObject, suitableUri);
        } else {
            final Uri uri = videoPlayer.getCurrentUri();
            isCached = uri != null && "file".equalsIgnoreCase(uri.getScheme());
            open(messageObject, uri);
        }

        final TLRPC.Document storyboardMapDocument = findDocumentByMimeType(messageObject, "application/x-tgstoryboardmap");
        final long storyBoardMapDocId = (!FORCE_STORYBOARD_EVEN_ON_CACHED && isCached) || storyboardMapDocument == null ? 0 : storyboardMapDocument.id;
        if (this.storyBoardMapDocId != storyBoardMapDocId) {
            this.storyBoardMapDocId = storyBoardMapDocId;
            storyBoardMap = null;
            if ((FORCE_STORYBOARD_EVEN_ON_CACHED || !isCached) && storyboardMapDocument != null) {
                File file = FileLoader.getInstance(messageObject.currentAccount).getPathToAttach(storyboardMapDocument);
                if (file != null && file.exists()) {
                    downloadingStoryBoardMapFilename = null;
                    downloadingStoryboardMapDocument = null;
                    listen(-1);
                    parseStoryBoardMap(file);
                } else {
                    final String filename = FileLoader.getAttachFileName(storyboardMapDocument);
                    downloadingStoryBoardMapFilename = filename;
                    downloadingStoryboardMapDocument = storyboardMapDocument;
                    listen(messageObject.currentAccount);
                    FileLoader.getInstance(messageObject.currentAccount).loadFile(storyboardMapDocument, messageObject, FileLoader.PRIORITY_NORMAL_UP, 0);
                }
            } else {
                downloadingStoryBoardMapFilename = null;
                downloadingStoryboardMapDocument = null;
                listen(-1);
                parseStoryBoardMap(null);
            }
        }
    }

    private int listeningCurrentAccount = -1;
    private void listen(int account) {
        if (listeningCurrentAccount == account) return;
        if (account == -1) {
            NotificationCenter.getInstance(listeningCurrentAccount).removeObserver(this, NotificationCenter.fileLoaded);
            NotificationCenter.getInstance(listeningCurrentAccount).removeObserver(this, NotificationCenter.fileLoadFailed);
        } else {
            NotificationCenter.getInstance(account).addObserver(this, NotificationCenter.fileLoaded);
            NotificationCenter.getInstance(account).addObserver(this, NotificationCenter.fileLoadFailed);
        }
        listeningCurrentAccount = account;
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.fileLoaded) {
            final String location = (String) args[0];
            if (location.equals(downloadingStoryBoardMapFilename)) {
                File file = FileLoader.getInstance(account).getPathToAttach(downloadingStoryboardMapDocument);
                if (file != null && file.exists()) {
                    parseStoryBoardMap(file);
                }
                downloadingStoryBoardMapFilename = null;
                downloadingStoryboardMapDocument = null;
                listen(-1);
            }
        } else if (id == NotificationCenter.fileLoadFailed) {
            final String location = (String) args[0];
            if (location.equals(downloadingStoryBoardMapFilename)) {
                downloadingStoryBoardMapFilename = null;
                downloadingStoryboardMapDocument = null;
                listen(-1);
            }
        }
    }

    public void parseStoryBoardMap(File file) {
        if (file == null) {
            storyBoardMap = null;
            return;
        }
        try {
            RandomAccessFile ram = new RandomAccessFile(file, "r");

            long picId = 0;
            int frameWidth = 0;
            int frameHeight = 0;
            final ArrayList<StoryBoardFrame> frames = new ArrayList<>();

            String line;
            while ((line = ram.readLine()) != null) {
                if (line.startsWith("file=mtproto:")) {
                    picId = Long.parseLong(line.substring(13));
                } else if (line.startsWith("frame_width=")) {
                    frameWidth = Integer.parseInt(line.substring(12));
                } else if (line.startsWith("frame_height=")) {
                    frameHeight = Integer.parseInt(line.substring(13));
                } else {
                    String[] parts = line.split(",");
                    if (parts.length == 3) {
                        frames.add(new StoryBoardFrame(Double.parseDouble(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2])));
                    }
                }
            }
            Collections.sort(frames, Comparator.comparingDouble(o -> o.pts));

            storyBoardPictureDocId = picId;
            storyBoardFrameWidth = frameWidth;
            storyBoardFrameHeight = frameHeight;
            storyBoardMap = frames;

        } catch (Exception e) {
            FileLog.e(e);
            storyBoardMap = null;
        }
    }

    public void open(MessageObject messageObject, VideoPlayer.VideoUri qualityUri) {
        if (qualityUri == null) return;
        if (qualityUri.uri.equals(videoUri)) return;
        if (open) {
            close();
        }
        isQualities = true;
        videoUri = qualityUri.uri;
        Utilities.globalQueue.postRunnable(loadRunnable = () -> {
            if (qualityUri.isCached()) {
                fileDrawable = new AnimatedFileDrawable(new File(qualityUri.uri.getPath()), true, 0, 0, null, null, null, 0, 0, true, null);
            } else {
                int currentAccount = UserConfig.selectedAccount;
                try {
                    currentAccount = Utilities.parseInt(qualityUri.uri.getQueryParameter("account"));
                } catch (Exception e) {
                    FileLog.e(e);
                }
                Object parentObject = null;
                try {
                    parentObject = FileLoader.getInstance(currentAccount).getParentObject(Utilities.parseInt(qualityUri.uri.getQueryParameter("rid")));
                } catch (Exception e) {
                    FileLog.e(e);
                }
                final TLRPC.Document document = qualityUri.document;
                String path;
                final String name = FileLoader.getAttachFileName(document);
                if (FileLoader.getInstance(currentAccount).isLoadingFile(name)) {
                    path = new File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE), document.dc_id + "_" + document.id + ".temp").getAbsolutePath();
                } else {
                    path = FileLoader.getInstance(currentAccount).getPathToAttach(document, false).getAbsolutePath();
                }

                fileDrawable = new AnimatedFileDrawable(new File(path), true, document.size, FileLoader.PRIORITY_NORMAL, document, null, parentObject, 0, currentAccount, true, null);
            }
            duration = fileDrawable.getDurationMs();
            if (pendingProgress != 0.0f) {
                setProgress(messageObject, pendingProgress, pixelWidth);
                pendingProgress = 0.0f;
            }
            AndroidUtilities.runOnUIThread(() -> {
                open = true;
                loadRunnable = null;
                if (fileDrawable != null) {
                    ready = true;
                    delegate.onReady();
                }
            });
        });
    }

    public void open(MessageObject messageObject, Uri uri) {
        if (uri == null || uri.equals(videoUri)) {
            return;
        }
        if (open) {
            close();
        }
        isQualities = false;
        videoUri = uri;
        Utilities.globalQueue.postRunnable(loadRunnable = () -> {
            String scheme = uri.getScheme();
            String path;
            if ("tg".equals(scheme)) {
                int currentAccount = Utilities.parseInt(uri.getQueryParameter("account"));
                final Object parentObject = FileLoader.getInstance(currentAccount).getParentObject(Utilities.parseInt(uri.getQueryParameter("rid")));
                final TLRPC.TL_document document = new TLRPC.TL_document();
                document.access_hash = Utilities.parseLong(uri.getQueryParameter("hash"));
                document.id = Utilities.parseLong(uri.getQueryParameter("id"));
                document.size = Utilities.parseLong(uri.getQueryParameter("size"));
                document.dc_id = Utilities.parseInt(uri.getQueryParameter("dc"));
                document.mime_type = uri.getQueryParameter("mime");
                document.file_reference = Utilities.hexToBytes(uri.getQueryParameter("reference"));
                final TLRPC.TL_documentAttributeFilename filename = new TLRPC.TL_documentAttributeFilename();
                filename.file_name = uri.getQueryParameter("name");
                document.attributes.add(filename);
                document.attributes.add(new TLRPC.TL_documentAttributeVideo());
                final String name = FileLoader.getAttachFileName(document);
                if (FileLoader.getInstance(currentAccount).isLoadingFile(name)) {
                    path = new File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE), document.dc_id + "_" + document.id + ".temp").getAbsolutePath();
                } else {
                    path = FileLoader.getInstance(currentAccount).getPathToAttach(document, false).getAbsolutePath();
                }
                fileDrawable = new AnimatedFileDrawable(new File(path), true, document.size, FileLoader.PRIORITY_NORMAL, document, null, parentObject, 0, currentAccount, true, null);
            } else {
                path = uri.getPath();
                fileDrawable = new AnimatedFileDrawable(new File(path), true, 0, 0, null, null, null, 0, 0, true, null);
            }
            duration = fileDrawable.getDurationMs();
            if (pendingProgress != 0.0f) {
                setProgress(messageObject, pendingProgress, pixelWidth);
                pendingProgress = 0.0f;
            }
            AndroidUtilities.runOnUIThread(() -> {
                open = true;
                loadRunnable = null;
                if (fileDrawable != null) {
                    ready = true;
                    delegate.onReady();
                }
            });
        });
    }

    public static TLRPC.Document findDocumentByMimeType(MessageObject messageObject, final String mimeType) {
        final TLRPC.MessageMedia media = MessageObject.getMedia(messageObject);
        if (media == null) return null;
        if (media.document != null && mimeType.equalsIgnoreCase(media.document.mime_type))
            return media.document;
        for (TLRPC.Document document : media.alt_documents) {
            if (mimeType.equalsIgnoreCase(document.mime_type)) {
                return document;
            }
        }
        return null;
    }

    public static TLRPC.Document findDocumentById(MessageObject messageObject, final long id) {
        final TLRPC.MessageMedia media = MessageObject.getMedia(messageObject);
        if (media == null) return null;
        if (media.document != null && media.document.id == id)
            return media.document;
        for (TLRPC.Document document : media.alt_documents) {
            if (document.id == id) {
                return document;
            }
        }
        return null;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        setPivotY(getMeasuredHeight());
    }

    public boolean isReady() {
        return ready;
    }

    public boolean isOpen() {
        return open;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (bitmapToRecycle != null) {
            bitmapToRecycle.recycle();
            bitmapToRecycle = null;
        }
        if (drawStoryBoard) {
            canvas.save();
            ytPath.rewind();
            AndroidUtilities.rectTmp.set(0, 0, getMeasuredWidth(), getMeasuredHeight());
            ytPath.addRoundRect(AndroidUtilities.rectTmp, dp(6), dp(6), Path.Direction.CW);
            canvas.clipPath(ytPath);

            canvas.scale((float) getWidth() / ytImageWidth, (float) getHeight() / ytImageHeight);
            canvas.translate(-ytImageX, -ytImageY);
            storyBoardsReceiver.setImageCoords(0, 0, storyBoardsReceiver.getBitmapWidth(), storyBoardsReceiver.getBitmapHeight());
            storyBoardsReceiver.draw(canvas);
            canvas.restore();

            frameDrawable.setBounds(0, 0, getMeasuredWidth(), getMeasuredHeight());
            frameDrawable.draw(canvas);

            canvas.drawText(frameTime, (getMeasuredWidth() - timeWidth) / 2f, getMeasuredHeight() - dp(9), textPaint);
        } else if (bitmapToDraw != null && bitmapShader != null) {
            matrix.reset();
            float scale = getMeasuredWidth() / (float) bitmapToDraw.getWidth();
            matrix.preScale(scale, scale);
            bitmapRect.set(0, 0, getMeasuredWidth(), getMeasuredHeight());
            canvas.drawRoundRect(bitmapRect, dp(6), dp(6), bitmapPaint);
            frameDrawable.setBounds(0, 0, getMeasuredWidth(), getMeasuredHeight());
            frameDrawable.draw(canvas);

            canvas.drawText(frameTime, (getMeasuredWidth() - timeWidth) / 2f, getMeasuredHeight() - dp(9), textPaint);
        }
    }

    public void close() {
        if (loadRunnable != null) {
            Utilities.globalQueue.cancelRunnable(loadRunnable);
            loadRunnable = null;
        }
        if (progressRunnable != null) {
            Utilities.globalQueue.cancelRunnable(progressRunnable);
            progressRunnable = null;
        }
        AnimatedFileDrawable drawable = fileDrawable;
        if (drawable != null) {
            drawable.resetStream(true);
        }
        Utilities.globalQueue.postRunnable(() -> {
            pendingProgress = 0.0f;
            if (fileDrawable != null) {
                fileDrawable.recycle();
                fileDrawable = null;
            }
        });
        setVisibility(INVISIBLE);
        /*if (bitmapToDraw != null) {
            if (bitmapToRecycle != null) {
                bitmapToRecycle.recycle();
            }
            bitmapToRecycle = bitmapToDraw;
        }*/
        bitmapToDraw = null;
        bitmapShader = null;
        invalidate();

        currentPixel = -1;
        videoUri = null;
        ready = false;
        open = false;

        if (storyBoardMapDocId != 0) {
            storyBoardMapDocId = 0;
            downloadingStoryBoardMapFilename = null;
            downloadingStoryboardMapDocument = null;
            storyBoardMap = null;
            listen(-1);
        }
    }
}
