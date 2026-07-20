package org.telegram.ui.iv;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.Layout;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.SoundEffectConstants;
import android.view.View;
import android.widget.FrameLayout;

import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DownloadController;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.ImageLoader;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_iv;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextSelectionHelper;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.MediaActionDrawable;
import org.telegram.ui.Components.RadialProgress2;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.SeekBar;
import org.telegram.ui.Components.TypefaceSpan;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;

import java.io.File;
import java.util.ArrayList;

public class RichAudioCell extends RichBlockCell
    implements Theme.Colorable, TextSelectionHelper.ArticleSelectableView, RichCaptionHost,
               NotificationCenter.NotificationCenterDelegate, DownloadController.FileDownloadProgressListener {

    public interface Delegate {
        void onCancelUpload(BlockRow row);
        TextSelectionHelper.ArticleTextSelectionHelper getSelectionHelper();
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
    private final TextPaint audioTimePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);

    private final RadialProgress2 radialProgress;
    private final SeekBar seekBar;
    private final int observerTag;

    private int buttonX = dp(16);
    private final int buttonY = dp(10);
    private final int size = dp(44);
    private boolean blockRtl;
    private int seekBarX, seekBarY, seekBarWidth;

    private StaticLayout titleLayout;
    private StaticLayout durationLayout;
    private String lastTimeString;

    private Delegate delegate;
    private MessageObject messageObject;
    private TLRPC.Document boundDocument;

    private int buttonState;
    private boolean buttonPressed;
    private boolean attached;
    private final RichCaptionController caption;

    public RichAudioCell(Context context, int currentAccount, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.currentAccount = currentAccount;
        this.resourcesProvider = resourcesProvider;
        setWillNotDraw(false);

        observerTag = DownloadController.getInstance(currentAccount).generateObserverTag();

        radialProgress = new RadialProgress2(this, resourcesProvider);
        radialProgress.setCircleRadius(dp(24));
        radialProgress.setProgressRect(buttonX, buttonY, buttonX + size, buttonY + size);

        seekBar = new SeekBar(this);
        seekBar.setDelegate(new SeekBar.SeekBarDelegate() {
            @Override
            public void onSeekBarDrag(float progress) {
                if (messageObject == null) return;
                messageObject.audioProgress = progress;
                MediaController.getInstance().seekToProgress(messageObject, progress);
            }
            @Override
            public void onSeekBarContinuousDrag(float progress) {
                if (messageObject == null) return;
                messageObject.audioProgress = progress;
            }
        });

        setMinimumHeight(dp(66));

        caption = new RichCaptionController(context, resourcesProvider, new RichCaptionController.Host() {
            @Override public BlockRow currentRow() { return currentRow; }
            @Override public TextSelectionHelper.ArticleTextSelectionHelper selectionHelper() { return delegate != null ? delegate.getSelectionHelper() : null; }
            @Override public TextSelectionHelper.ArticleSelectableView cell() { return RichAudioCell.this; }
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

    @Override
    protected void onBlockInsetChanged(int px) {
        buttonX = dp(16) + (blockRtl ? 0 : px);
        radialProgress.setProgressRect(buttonX, buttonY, buttonX + size, buttonY + size);
        requestLayout();
        invalidate();
    }

    public void bind(BlockRow row, Delegate delegate) {
        this.currentRow = row;
        this.delegate = delegate;
        if (row != null && row.media == null) {
            row.media = new MediaUploadState();
        }
        blockRtl = RichBlockChrome.rtl();
        bindBlockInset(row);
        caption.bind();
        rebuildFromRow();
        requestLayout();
        invalidate();
    }

    @Override
    public BlockRow getRow() {
        return currentRow;
    }

    @Override
    public RichEditText getCaptionEditText() {
        return caption.editText;
    }

    @Override
    public void persistCaption() {
        caption.persist();
    }

    @Override
    public boolean isPressOnCaption(int localX, int localY) {
        return caption.isPressOnCaption(localX, localY);
    }

    private void rebuildFromRow() {
        final TLRPC.Document doc = getDisplayDocument();
        if (doc != boundDocument) {
            boundDocument = doc;
            messageObject = null;
            lastTimeString = null;
            durationLayout = null;
        }
        if (isReady() && messageObject == null && doc != null) {
            messageObject = buildMessageObject(doc);
        }
        layoutInner();
        if (attached) {
            updateButtonState(false);
        }
    }

    private boolean isReady() {
        return currentRow != null && currentRow.media != null && currentRow.media.isReady();
    }

    private boolean isUploading() {
        return currentRow != null && currentRow.media != null && currentRow.media.isPending();
    }

    private TLRPC.Document getDisplayDocument() {
        if (currentRow == null || currentRow.media == null) return null;
        if (currentRow.media.document != null) return currentRow.media.document;
        return currentRow.media.audioDisplayDocument;
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
        if (currentRow != null && currentRow.media != null && !TextUtils.isEmpty(currentRow.media.localPath)) {
            message.attachPath = currentRow.media.localPath;
        }
        return new MessageObject(currentAccount, message, false, true);
    }

    private void layoutInner() {
        seekBarX = buttonX + dp(50) + size;
        final int width = getMeasuredWidth() > 0 ? getMeasuredWidth() : AndroidUtilities.displaySize.x;
        final int insR = blockRtl ? blockInset() : 0;
        seekBarWidth = Math.max(0, width - seekBarX - dp(18) - insR);

        final String author = audioAuthor();
        final String title = audioTitle();
        if (!TextUtils.isEmpty(title) || !TextUtils.isEmpty(author)) {
            SpannableStringBuilder stringBuilder;
            if (!TextUtils.isEmpty(title) && !TextUtils.isEmpty(author)) {
                stringBuilder = new SpannableStringBuilder(String.format("%s - %s", author, title));
            } else if (!TextUtils.isEmpty(title)) {
                stringBuilder = new SpannableStringBuilder(title);
            } else {
                stringBuilder = new SpannableStringBuilder(author);
            }
            if (!TextUtils.isEmpty(author)) {
                stringBuilder.setSpan(new TypefaceSpan(AndroidUtilities.bold()), 0, author.length(), Spanned.SPAN_INCLUSIVE_INCLUSIVE);
            }
            audioTimePaint.setTextSize(dp(16));
            final CharSequence stringFinal = TextUtils.ellipsize(stringBuilder, audioTimePaint, seekBarWidth, TextUtils.TruncateAt.END);
            titleLayout = new StaticLayout(stringFinal, audioTimePaint, seekBarWidth + dp(50), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
            seekBarY = buttonY + (size - dp(30)) / 2 + dp(11);
        } else {
            titleLayout = null;
            seekBarY = buttonY + (size - dp(30)) / 2;
        }
        seekBar.setSize(seekBarWidth, dp(30));
    }

    private String audioAuthor() {
        if (messageObject != null) return messageObject.getMusicAuthor(false);
        return attribute() != null ? attribute().performer : null;
    }

    private String audioTitle() {
        if (messageObject != null) return messageObject.getMusicTitle(false);
        return attribute() != null ? attribute().title : null;
    }

    private TLRPC.TL_documentAttributeAudio attribute() {
        final TLRPC.Document doc = getDisplayDocument();
        if (doc == null) return null;
        for (int i = 0; i < doc.attributes.size(); i++) {
            if (doc.attributes.get(i) instanceof TLRPC.TL_documentAttributeAudio) {
                return (TLRPC.TL_documentAttributeAudio) doc.attributes.get(i);
            }
        }
        return null;
    }

    private int audioDuration() {
        if (messageObject != null && MediaController.getInstance().isPlayingMessage(messageObject)) {
            return messageObject.audioProgressSec;
        }
        final TLRPC.TL_documentAttributeAudio attr = attribute();
        return attr != null ? (int) attr.duration : 0;
    }

    @Override
    public void updateColors() {
        selectionPaint.setColor(Theme.getColor(Theme.key_chat_inTextSelectionHighlight, resourcesProvider));
        if (caption != null) caption.applyColors();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        final int w = MeasureSpec.getSize(widthMeasureSpec);
        final int capH = caption.measure(blockRtl ? 0 : blockInset(), blockRtl ? blockInset() : 0, w);
        setMeasuredDimension(w, dp(66) + capH);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        caption.layout(blockRtl ? 0 : blockInset(), blockRtl ? blockInset() : 0, right - left, dp(66));
        layoutInner();
    }

    private int getIconForCurrentState() {
        if (isUploading()) return MediaActionDrawable.ICON_CANCEL;
        if (buttonState == 1) return MediaActionDrawable.ICON_PAUSE;
        if (buttonState == 2) return MediaActionDrawable.ICON_DOWNLOAD;
        if (buttonState == 3) return MediaActionDrawable.ICON_CANCEL;
        return MediaActionDrawable.ICON_PLAY;
    }

    public void updateButtonState(boolean animated) {
        radialProgress.setColorKeys(
            Theme.key_chat_inLoader,
            Theme.key_chat_inLoaderSelected,
            Theme.key_chat_inMediaIcon,
            Theme.key_chat_inMediaIconSelected
        );
        radialProgress.setProgressColor(Theme.getColor(Theme.key_chat_inFileProgress, resourcesProvider));

        if (isUploading()) {
            DownloadController.getInstance(currentAccount).removeLoadingFileObserver(this);
            radialProgress.setProgress(currentRow.media.progress, animated);
            radialProgress.setIcon(MediaActionDrawable.ICON_CANCEL, false, animated);
            updatePlayingMessageProgress();
            return;
        }

        final TLRPC.Document document = isReady() ? currentRow.media.document : null;
        final String fileName = FileLoader.getAttachFileName(document);
        final boolean localExists = currentRow != null && currentRow.media != null
            && !TextUtils.isEmpty(currentRow.media.localPath) && new File(currentRow.media.localPath).exists();
        final File path = document == null ? null : FileLoader.getInstance(currentAccount).getPathToAttach(document, true);
        final boolean fileExists = localExists || (path != null && path.exists());
        if (TextUtils.isEmpty(fileName)) {
            radialProgress.setIcon(MediaActionDrawable.ICON_NONE, false, false);
            return;
        }
        if (fileExists) {
            DownloadController.getInstance(currentAccount).removeLoadingFileObserver(this);
            final boolean playing = MediaController.getInstance().isPlayingMessage(messageObject);
            buttonState = !playing || MediaController.getInstance().isMessagePaused() ? 0 : 1;
            radialProgress.setIcon(getIconForCurrentState(), false, animated);
        } else {
            DownloadController.getInstance(currentAccount).addLoadingFileObserver(fileName, null, this);
            if (!FileLoader.getInstance(currentAccount).isLoadingFile(fileName)) {
                buttonState = 2;
                radialProgress.setProgress(0, animated);
                radialProgress.setIcon(getIconForCurrentState(), false, animated);
            } else {
                buttonState = 3;
                final Float progress = ImageLoader.getInstance().getFileProgress(fileName);
                radialProgress.setProgress(progress != null ? progress : 0, animated);
                radialProgress.setIcon(getIconForCurrentState(), true, animated);
            }
        }
        updatePlayingMessageProgress();
    }

    private void didPressedButton(boolean animated) {
        if (isUploading()) {
            if (delegate != null) delegate.onCancelUpload(currentRow);
            return;
        }
        if (messageObject == null) return;
        final TLRPC.Document document = isReady() ? currentRow.media.document : null;
        if (buttonState == 0) {
            final ArrayList<MessageObject> playlist = new ArrayList<>();
            playlist.add(messageObject);
            if (MediaController.getInstance().setPlaylist(playlist, messageObject, 0, false, null)) {
                buttonState = 1;
                radialProgress.setIcon(getIconForCurrentState(), false, animated);
                invalidate();
            }
        } else if (buttonState == 1) {
            if (MediaController.getInstance().pauseMessage(messageObject)) {
                buttonState = 0;
                radialProgress.setIcon(getIconForCurrentState(), false, animated);
                invalidate();
            }
        } else if (buttonState == 2) {
            radialProgress.setProgress(0, false);
            FileLoader.getInstance(currentAccount).loadFile(document, messageObject, 1, 1);
            buttonState = 3;
            radialProgress.setIcon(getIconForCurrentState(), true, animated);
            invalidate();
        } else if (buttonState == 3) {
            FileLoader.getInstance(currentAccount).cancelLoadFile(document);
            buttonState = 2;
            radialProgress.setIcon(getIconForCurrentState(), false, animated);
            invalidate();
        }
    }

    public void updatePlayingMessageProgress() {
        if (!isUploading() && messageObject != null) {
            if (!seekBar.isDragging()) {
                seekBar.setProgress(messageObject.audioProgress);
            }
        }
        final String timeString = AndroidUtilities.formatShortDuration(isUploading() ? (attribute() != null ? (int) attribute().duration : 0) : audioDuration());
        if (lastTimeString == null || !lastTimeString.equals(timeString)) {
            lastTimeString = timeString;
            audioTimePaint.setTextSize(dp(16));
            final int timeWidth = (int) Math.ceil(audioTimePaint.measureText(timeString));
            durationLayout = new StaticLayout(timeString, audioTimePaint, timeWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
        }
        invalidate();
    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        attached = true;
        radialProgress.setParent(this);
        seekBar.setParent(this);
        updateButtonState(false);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.messagePlayingDidStart);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.messagePlayingDidReset);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.messagePlayingPlayStateChanged);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.messagePlayingProgressDidChanged);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        attached = false;
        DownloadController.getInstance(currentAccount).removeLoadingFileObserver(this);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.messagePlayingDidStart);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.messagePlayingDidReset);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.messagePlayingPlayStateChanged);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.messagePlayingProgressDidChanged);
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (messageObject == null || account != currentAccount) return;
        if (id == NotificationCenter.messagePlayingDidStart
            || id == NotificationCenter.messagePlayingDidReset
            || id == NotificationCenter.messagePlayingPlayStateChanged) {
            updateButtonState(true);
        } else if (id == NotificationCenter.messagePlayingProgressDidChanged) {
            final Integer mid = (Integer) args[0];
            if (messageObject.getId() == mid) {
                final MessageObject player = MediaController.getInstance().getPlayingMessageObject();
                if (player != null) {
                    messageObject.audioProgress = player.audioProgress;
                    messageObject.audioProgressSec = player.audioProgressSec;
                    messageObject.audioPlayerDuration = player.audioPlayerDuration;
                    updatePlayingMessageProgress();
                }
            }
        }
    }

    @Override public int getObserverTag() { return observerTag; }
    @Override public void onFailedDownload(String fileName, boolean canceled) { updateButtonState(true); }
    @Override public void onSuccessDownload(String fileName) {
        radialProgress.setProgress(1, true);
        updateButtonState(true);
    }
    @Override public void onProgressUpload(String fileName, long uploadedSize, long totalSize, boolean isEncrypted) {}
    @Override public void onProgressDownload(String fileName, long downloadSize, long totalSize) {
        radialProgress.setProgress(Math.min(1f, totalSize <= 0 ? 0 : downloadSize / (float) totalSize), true);
        if (buttonState != 3) updateButtonState(true);
    }

    private boolean isCellSelected() {
        if (delegate == null) return false;
        TextSelectionHelper.ArticleTextSelectionHelper helper = delegate.getSelectionHelper();
        if (helper == null || !helper.isInSelectionMode()) return false;
        if (!(getParent() instanceof RecyclerView)) return false;
        int myPos = ((RecyclerView) getParent()).getChildAdapterPosition(this);
        if (myPos < 0) return false;
        // The audio block sits above the caption: only light it up when the selection enters this
        // cell from a previous one, not when it is confined to (or starts at) this cell's caption.
        return myPos > helper.getStartCell() && myPos <= helper.getEndCell();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (getDisplayDocument() == null) return;

        radialProgress.draw(canvas);

        seekBar.setColors(
            Theme.getColor(Theme.key_chat_inAudioSeekbar, resourcesProvider),
            Theme.getColor(Theme.key_chat_inAudioCacheSeekbar, resourcesProvider),
            Theme.getColor(Theme.key_chat_inAudioSeekbarFill, resourcesProvider),
            Theme.getColor(Theme.key_chat_inAudioSeekbarFill, resourcesProvider),
            Theme.getColor(Theme.key_chat_inAudioSeekbarSelected, resourcesProvider)
        );

        if (!isUploading()) {
            canvas.save();
            canvas.translate(seekBarX, seekBarY);
            seekBar.draw(canvas);
            canvas.restore();
        }

        audioTimePaint.setColor(Theme.getColor(Theme.key_chat_inTimeText, resourcesProvider));
        if (durationLayout != null) {
            canvas.save();
            canvas.translate(buttonX + dp(54), seekBarY + dp(6));
            durationLayout.draw(canvas);
            canvas.restore();
        }
        if (titleLayout != null) {
            audioTimePaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
            canvas.save();
            canvas.translate(buttonX + dp(54), seekBarY - dp(16));
            titleLayout.draw(canvas);
            canvas.restore();
        }

        if (isCellSelected()) {
            canvas.drawRoundRect(
                (blockRtl ? 0 : blockInset()) + dp(8), dp(2), getWidth() - (blockRtl ? blockInset() : 0) - dp(8), dp(64),
                dp(8), dp(8),
                selectionPaint
            );
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        final int act = event.getActionMasked();
        final float x = event.getX();
        final float y = event.getY();

        if (!isUploading()) {
            final boolean seekHandled = seekBar.onTouch(act, x - seekBarX, y - seekBarY);
            if (seekHandled) {
                if (act == MotionEvent.ACTION_DOWN) {
                    getParent().requestDisallowInterceptTouchEvent(true);
                }
                invalidate();
                return true;
            }
        }

        if (act == MotionEvent.ACTION_DOWN) {
            if (x >= buttonX && x <= buttonX + size && y >= buttonY && y <= buttonY + size) {
                buttonPressed = true;
                invalidate();
                return true;
            }
        } else if (act == MotionEvent.ACTION_UP) {
            if (buttonPressed) {
                buttonPressed = false;
                playSoundEffect(SoundEffectConstants.CLICK);
                didPressedButton(true);
                invalidate();
                return true;
            }
        } else if (act == MotionEvent.ACTION_CANCEL) {
            buttonPressed = false;
        }
        return buttonPressed || super.onTouchEvent(event);
    }

    @Override
    public void fillTextLayoutBlocks(ArrayList<TextSelectionHelper.TextLayoutBlock> out) {
        caption.fillTextLayoutBlocks(out);
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        caption.drawSelection(canvas);
    }

    public static final class Factory extends UItem.UItemFactory<RichAudioCell> {
        static { setup(new Factory()); }

        @Override
        public RichAudioCell createView(Context context, RecyclerListView listView, int currentAccount, int classGuid, Theme.ResourcesProvider resourcesProvider) {
            final RichAudioCell cell = new RichAudioCell(context, currentAccount, resourcesProvider);
            cell.setBackground(new RichEditor.DraggingDrawable(Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider)));
            return cell;
        }

        @Override
        public void bindView(View view, UItem item, boolean divider, UniversalAdapter adapter, UniversalRecyclerView listView) {
            ((RichAudioCell) view).bind((BlockRow) item.object, (Delegate) item.object2);
        }

        public static UItem of(BlockRow row, Delegate delegate) {
            final UItem item = UItem.ofFactory(Factory.class);
            item.object = row;
            item.object2 = delegate;
            return item;
        }

        @Override
        public boolean isClickable() {
            return false;
        }
    }
}
