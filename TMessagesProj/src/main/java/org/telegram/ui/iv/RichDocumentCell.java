package org.telegram.ui.iv;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.SoundEffectConstants;
import android.view.View;

import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DownloadController;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.ImageLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextSelectionHelper;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.MediaActionDrawable;
import org.telegram.ui.Components.RadialProgress2;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;

import java.io.File;
import java.util.ArrayList;

public class RichDocumentCell extends RichBlockCell implements Theme.Colorable,
        TextSelectionHelper.ArticleSelectableView, RichCaptionHost,
        DownloadController.FileDownloadProgressListener {

    public interface Delegate {
        void onCancelUpload(BlockRow row);
        TextSelectionHelper.ArticleTextSelectionHelper getSelectionHelper();
        default MessageObject getFileRefParentObject() { return null; }
        default void onCaptionWillChange(BlockRow row, int removed, int added) {}
        default void onCaptionChanged(BlockRow row) {}
        default void onCaptionSpansChanged(BlockRow row) {}
        default void onCaptionEnter(BlockRow row) {}
        default void onRequestWindowFocusable(RichEditText et, boolean showKeyboard) {}
        default void onCaptionLockedInsert(CharSequence text) {}
        default boolean onCaptionSelectAll(BlockRow row) { return false; }
    }

    private final int currentAccount;
    private final Theme.ResourcesProvider resourcesProvider;
    private final Paint selectionPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint previewBackgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint sizePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final RadialProgress2 radialProgress;
    private final ImageReceiver previewImage;
    private final RichCaptionController caption;
    private final int observerTag;
    private final int buttonY = dp(10);
    private final int buttonSize = dp(44);
    private int buttonX = dp(16);
    private int mediaX = dp(16);
    private boolean hasPreview;
    private boolean blockRtl;
    private boolean attached;
    private boolean pressed;
    private int buttonState;
    private StaticLayout titleLayout;
    private StaticLayout sizeLayout;
    private Delegate delegate;
    private MessageObject messageObject;
    private TLRPC.Document boundDocument;

    public RichDocumentCell(Context context, int currentAccount, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.currentAccount = currentAccount;
        this.resourcesProvider = resourcesProvider;
        setWillNotDraw(false);
        setMinimumHeight(dp(66));
        observerTag = DownloadController.getInstance(currentAccount).generateObserverTag();
        radialProgress = new RadialProgress2(this, resourcesProvider);
        radialProgress.setCircleRadius(dp(24));
        radialProgress.setProgressRect(buttonX, buttonY, buttonX + buttonSize, buttonY + buttonSize);
        previewImage = new ImageReceiver(this);
        previewImage.setAllowLoadingOnAttachedOnly(true);
        previewImage.setRoundRadius(dp(6));
        caption = new RichCaptionController(context, resourcesProvider, new RichCaptionController.Host() {
            @Override public BlockRow currentRow() { return currentRow; }
            @Override public TextSelectionHelper.ArticleTextSelectionHelper selectionHelper() { return delegate == null ? null : delegate.getSelectionHelper(); }
            @Override public TextSelectionHelper.ArticleSelectableView cell() { return RichDocumentCell.this; }
            @Override public void onCaptionWillChange(int removed, int added) { if (delegate != null) delegate.onCaptionWillChange(currentRow, removed, added); }
            @Override public void onCaptionChanged() { if (delegate != null) delegate.onCaptionChanged(currentRow); }
            @Override public void onCaptionSpansChanged() { if (delegate != null) delegate.onCaptionSpansChanged(currentRow); }
            @Override public void onCaptionEnter() { if (delegate != null) delegate.onCaptionEnter(currentRow); }
            @Override public void onRequestWindowFocusable(RichEditText et, boolean showKeyboard) { if (delegate != null) delegate.onRequestWindowFocusable(et, showKeyboard); }
            @Override public void onCaptionLockedInsert(CharSequence text) { if (delegate != null) delegate.onCaptionLockedInsert(text); }
            @Override public boolean onCaptionSelectAll() { return delegate != null && delegate.onCaptionSelectAll(currentRow); }
        });
        addView(caption.editText, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP));
        updateColors();
    }

    public void bind(BlockRow row, Delegate delegate) {
        currentRow = row;
        this.delegate = delegate;
        blockRtl = RichBlockChrome.rtl();
        bindBlockInset(row);
        caption.bind();
        final TLRPC.Document document = document();
        if (boundDocument != document) {
            boundDocument = document;
            messageObject = document == null ? null : buildMessageObject(document);
        }
        bindPreview(document);
        rebuildLayouts();
        if (attached) updateButtonState(false);
        requestLayout();
        invalidate();
    }

    private TLRPC.Document document() {
        return currentRow == null || currentRow.media == null ? null : currentRow.media.document;
    }

    private boolean isUploading() {
        return currentRow != null && currentRow.media != null && currentRow.media.isPending();
    }

    private MessageObject buildMessageObject(TLRPC.Document document) {
        final TLRPC.TL_message message = new TLRPC.TL_message();
        message.out = true;
        message.id = -((Long) document.id).hashCode();
        message.peer_id = new TLRPC.TL_peerUser();
        message.from_id = new TLRPC.TL_peerUser();
        message.from_id.user_id = message.peer_id.user_id = UserConfig.getInstance(currentAccount).getClientUserId();
        message.date = (int) (System.currentTimeMillis() / 1000);
        message.message = "";
        message.media = new TLRPC.TL_messageMediaDocument();
        message.media.flags |= 3;
        message.media.document = document;
        message.flags |= TLRPC.MESSAGE_FLAG_HAS_MEDIA | TLRPC.MESSAGE_FLAG_HAS_FROM_ID;
        if (currentRow != null && currentRow.media != null && !TextUtils.isEmpty(currentRow.media.localPath)) message.attachPath = currentRow.media.localPath;
        return new MessageObject(currentAccount, message, false, true);
    }

    private void rebuildLayouts() {
        final TLRPC.Document document = document();
        if (document == null) return;
        final int textX = hasPreview ? mediaX + dp(86 + 11) : buttonX + dp(54);
        final int width = Math.max(dp(40), (getMeasuredWidth() > 0 ? getMeasuredWidth() : AndroidUtilities.displaySize.x) - textX - dp(16) - (blockRtl ? blockInset() : 0));
        textPaint.setTextSize(dp(15));
        textPaint.setTypeface(AndroidUtilities.bold());
        sizePaint.setTextSize(dp(13));
        String name = FileLoader.getDocumentFileName(document);
        if (TextUtils.isEmpty(name) && currentRow.media != null && !TextUtils.isEmpty(currentRow.media.localPath)) name = new File(currentRow.media.localPath).getName();
        final CharSequence title = TextUtils.ellipsize(name == null ? "" : name, textPaint, width, TextUtils.TruncateAt.END);
        titleLayout = new StaticLayout(title, textPaint, width, Layout.Alignment.ALIGN_NORMAL, 1f, 0f, false);
        final long size = document.size > 0 ? document.size : currentRow.media != null && !TextUtils.isEmpty(currentRow.media.localPath) ? new File(currentRow.media.localPath).length() : 0;
        final String sizeText = AndroidUtilities.formatFileSize(size);
        sizeLayout = new StaticLayout(sizeText, sizePaint, width, Layout.Alignment.ALIGN_NORMAL, 1f, 0f, false);
    }

    @Override protected void onBlockInsetChanged(int px) {
        mediaX = dp(16) + (blockRtl ? 0 : px);
        buttonX = hasPreview ? mediaX + dp(21) : mediaX;
        final int progressY = hasPreview ? dp(31) : buttonY;
        radialProgress.setProgressRect(buttonX, progressY, buttonX + buttonSize, progressY + buttonSize);
        requestLayout();
    }

    private void bindPreview(TLRPC.Document document) {
        final String localPath = currentRow != null && currentRow.media != null ? currentRow.media.localPath : null;
        final File localFile = TextUtils.isEmpty(localPath) ? null : new File(localPath);
        final String mime = document == null || document.mime_type == null ? "" : document.mime_type.toLowerCase();
        final boolean localPreview = isUploading() && localFile != null && localFile.exists()
                && (mime.startsWith("image/") || mime.equals("video/mp4"));
        final boolean documentPreview = MessageObject.isDocumentHasThumb(document);
        hasPreview = localPreview || documentPreview;
        mediaX = dp(16) + (blockRtl ? 0 : blockInset());
        buttonX = hasPreview ? mediaX + dp(21) : mediaX;
        final int progressY = hasPreview ? dp(31) : buttonY;
        radialProgress.setProgressRect(buttonX, progressY, buttonX + buttonSize, progressY + buttonSize);
        if (localPreview) {
            previewImage.setImageCoords(mediaX, dp(10), dp(86), dp(86));
            previewImage.setImage(ImageLocation.getForPath(localPath), "86_86", null, null, document, 1);
        } else if (documentPreview) {
            final TLRPC.PhotoSize thumb = FileLoader.getClosestPhotoSizeWithSize(document.thumbs, 320, false, null, true);
            final MessageObject fileRefParent = delegate == null ? null : delegate.getFileRefParentObject();
            previewImage.setImageCoords(mediaX, dp(10), dp(86), dp(86));
            previewImage.setImage(
                    thumb == null ? null : ImageLocation.getForDocument(thumb, document), "86_86",
                    ImageLoader.createStripedBitmap(document.thumbs), null,
                    fileRefParent != null ? fileRefParent : messageObject, 1);
        } else {
            previewImage.clearImage();
        }
    }

    public void refreshUploadState() {
        bindPreview(document());
        rebuildLayouts();
        updateButtonState(false);
        requestLayout();
        invalidate();
    }

    private File localFile() {
        if (currentRow != null && currentRow.media != null && !TextUtils.isEmpty(currentRow.media.localPath)) {
            final File local = new File(currentRow.media.localPath);
            if (local.exists()) return local;
        }
        if (document() == null) return null;
        final File stored = FileLoader.getInstance(currentAccount).getPathToAttach(document(), false);
        if (stored != null && stored.exists()) return stored;
        return FileLoader.getInstance(currentAccount).getPathToAttach(document(), true);
    }

    public void updateButtonState(boolean animated) {
        if (hasPreview) {
            radialProgress.setColorKeys(Theme.key_chat_mediaLoaderPhoto, Theme.key_chat_mediaLoaderPhotoSelected, Theme.key_chat_mediaLoaderPhotoIcon, Theme.key_chat_mediaLoaderPhotoIconSelected);
            radialProgress.setProgressColor(Theme.getColor(Theme.key_chat_mediaProgress, resourcesProvider));
        } else {
            radialProgress.setColorKeys(Theme.key_chat_inLoader, Theme.key_chat_inLoaderSelected, Theme.key_chat_inMediaIcon, Theme.key_chat_inMediaIconSelected);
            radialProgress.setProgressColor(Theme.getColor(Theme.key_chat_inFileProgress, resourcesProvider));
        }
        if (isUploading()) {
            DownloadController.getInstance(currentAccount).removeLoadingFileObserver(this);
            radialProgress.setProgress(currentRow.media.progress, animated);
            radialProgress.setIcon(MediaActionDrawable.ICON_CANCEL, false, animated);
            return;
        }
        final TLRPC.Document document = document();
        final String fileName = FileLoader.getAttachFileName(document);
        final File path = localFile();
        if (path != null && path.exists()) {
            DownloadController.getInstance(currentAccount).removeLoadingFileObserver(this);
            buttonState = 0;
            radialProgress.setIcon(hasPreview ? MediaActionDrawable.ICON_NONE : MediaActionDrawable.ICON_FILE, false, animated);
        } else if (!TextUtils.isEmpty(fileName)) {
            DownloadController.getInstance(currentAccount).addLoadingFileObserver(fileName, null, this);
            if (FileLoader.getInstance(currentAccount).isLoadingFile(fileName)) {
                buttonState = 2;
                final Float progress = ImageLoader.getInstance().getFileProgress(fileName);
                radialProgress.setProgress(progress == null ? 0 : progress, animated);
                radialProgress.setIcon(MediaActionDrawable.ICON_CANCEL, true, animated);
            } else {
                buttonState = 1;
                radialProgress.setProgress(0, animated);
                radialProgress.setIcon(MediaActionDrawable.ICON_DOWNLOAD, false, animated);
            }
        }
    }

    private void pressButton() {
        if (isUploading()) {
            if (delegate != null) delegate.onCancelUpload(currentRow);
        } else if (buttonState == 0) {
            final Activity activity = findActivity(getContext());
            if (activity != null && messageObject != null) AndroidUtilities.openForView(messageObject, activity, resourcesProvider, false);
        } else if (buttonState == 1 && document() != null) {
            final MessageObject fileRefParent = delegate == null ? null : delegate.getFileRefParentObject();
            FileLoader.getInstance(currentAccount).loadFile(document(), fileRefParent != null ? fileRefParent : messageObject, 1, 1);
            buttonState = 2;
            radialProgress.setIcon(MediaActionDrawable.ICON_CANCEL, true, true);
        } else if (buttonState == 2 && document() != null) {
            FileLoader.getInstance(currentAccount).cancelLoadFile(document());
            buttonState = 1;
            radialProgress.setIcon(MediaActionDrawable.ICON_DOWNLOAD, false, true);
        }
        invalidate();
    }

    private static Activity findActivity(Context context) {
        while (context instanceof ContextWrapper) {
            if (context instanceof Activity) return (Activity) context;
            context = ((ContextWrapper) context).getBaseContext();
        }
        return context instanceof Activity ? (Activity) context : null;
    }

    @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        final int w = MeasureSpec.getSize(widthMeasureSpec);
        final int capH = caption.measure(blockRtl ? 0 : blockInset(), blockRtl ? blockInset() : 0, w);
        setMeasuredDimension(w, dp(hasPreview ? 106 : 66) + capH);
    }

    @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        caption.layout(blockRtl ? 0 : blockInset(), blockRtl ? blockInset() : 0, right - left, dp(hasPreview ? 106 : 66));
        rebuildLayouts();
    }

    @Override protected void onDraw(Canvas canvas) {
        if (document() == null) return;
        if (hasPreview && !previewImage.draw(canvas)) {
            previewBackgroundPaint.setColor(Theme.getColor(Theme.key_chat_inFileBackground, resourcesProvider));
            canvas.drawRoundRect(mediaX, dp(10), mediaX + dp(86), dp(96), dp(6), dp(6), previewBackgroundPaint);
        }
        radialProgress.draw(canvas);
        final int x = hasPreview ? mediaX + dp(86 + 11) : buttonX + dp(54);
        textPaint.setColor(Theme.getColor(Theme.key_chat_inFileNameText, resourcesProvider));
        final int titleY = dp(12);
        if (titleLayout != null) { canvas.save(); canvas.translate(x, titleY); titleLayout.draw(canvas); canvas.restore(); }
        sizePaint.setColor(Theme.getColor(Theme.key_chat_inTimeText, resourcesProvider));
        final int sizeY = titleY + (titleLayout == null ? 0 : titleLayout.getHeight()) + dp(2);
        if (sizeLayout != null) { canvas.save(); canvas.translate(x, sizeY); sizeLayout.draw(canvas); canvas.restore(); }
        if (isCellSelected()) canvas.drawRoundRect((blockRtl ? 0 : blockInset()) + dp(8), dp(2), getWidth() - (blockRtl ? blockInset() : 0) - dp(8), dp(hasPreview ? 104 : 64), dp(8), dp(8), selectionPaint);
    }

    private boolean isCellSelected() {
        if (delegate == null || !(getParent() instanceof RecyclerView)) return false;
        final TextSelectionHelper.ArticleTextSelectionHelper helper = delegate.getSelectionHelper();
        if (helper == null || !helper.isInSelectionMode()) return false;
        final int pos = ((RecyclerView) getParent()).getChildAdapterPosition(this);
        return pos > helper.getStartCell() && pos <= helper.getEndCell();
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        final boolean inside = event.getX() >= mediaX && event.getX() <= getWidth() - dp(12) && event.getY() >= dp(10) && event.getY() <= dp(hasPreview ? 96 : 54);
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN && inside) { pressed = true; return true; }
        if (event.getActionMasked() == MotionEvent.ACTION_UP && pressed) { pressed = false; if (inside) { playSoundEffect(SoundEffectConstants.CLICK); pressButton(); } return true; }
        if (event.getActionMasked() == MotionEvent.ACTION_CANCEL) pressed = false;
        return pressed || super.onTouchEvent(event);
    }

    @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); attached = true; radialProgress.setParent(this); previewImage.onAttachedToWindow(); updateButtonState(false); }
    @Override protected void onDetachedFromWindow() { super.onDetachedFromWindow(); attached = false; previewImage.onDetachedFromWindow(); DownloadController.getInstance(currentAccount).removeLoadingFileObserver(this); }
    @Override public int getObserverTag() { return observerTag; }
    @Override public void onFailedDownload(String fileName, boolean canceled) { updateButtonState(true); }
    @Override public void onSuccessDownload(String fileName) { radialProgress.setProgress(1, true); updateButtonState(true); }
    @Override public void onProgressUpload(String fileName, long uploadedSize, long totalSize, boolean isEncrypted) {}
    @Override public void onProgressDownload(String fileName, long downloadSize, long totalSize) { radialProgress.setProgress(totalSize <= 0 ? 0 : Math.min(1f, downloadSize / (float) totalSize), true); }
    @Override public void updateColors() { selectionPaint.setColor(Theme.getColor(Theme.key_chat_inTextSelectionHighlight, resourcesProvider)); if (caption != null) caption.applyColors(); }
    @Override public BlockRow getRow() { return currentRow; }
    @Override public RichEditText getCaptionEditText() { return caption.editText; }
    @Override public void persistCaption() { caption.persist(); }
    @Override public boolean isPressOnCaption(int x, int y) { return caption.isPressOnCaption(x, y); }
    @Override public void fillTextLayoutBlocks(ArrayList<TextSelectionHelper.TextLayoutBlock> out) { caption.fillTextLayoutBlocks(out); }
    @Override protected void dispatchDraw(Canvas canvas) { super.dispatchDraw(canvas); caption.drawSelection(canvas); }

    public static final class Factory extends UItem.UItemFactory<RichDocumentCell> {
        static { setup(new Factory()); }
        @Override public RichDocumentCell createView(Context context, RecyclerListView listView, int currentAccount, int classGuid, Theme.ResourcesProvider resourcesProvider) {
            final RichDocumentCell cell = new RichDocumentCell(context, currentAccount, resourcesProvider);
            cell.setBackground(new RichEditor.DraggingDrawable(Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider)));
            return cell;
        }
        @Override public void bindView(View view, UItem item, boolean divider, UniversalAdapter adapter, UniversalRecyclerView listView) { ((RichDocumentCell) view).bind((BlockRow) item.object, (Delegate) item.object2); }
        public static UItem of(BlockRow row, Delegate delegate) { final UItem item = UItem.ofFactory(Factory.class); item.object = row; item.object2 = delegate; return item; }
        @Override public boolean isClickable() { return false; }
    }
}
