package org.Tajgram.ui.iv;

import static org.Tajgram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.icu.util.Measure;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;

import java.io.File;

import androidx.recyclerview.widget.RecyclerView;

import org.Tajgram.messenger.AndroidUtilities;
import org.Tajgram.messenger.FileLoader;
import org.Tajgram.messenger.ImageLocation;
import org.Tajgram.messenger.ImageReceiver;
import org.Tajgram.messenger.R;
import org.Tajgram.tgnet.TLRPC;
import org.Tajgram.ui.ActionBar.Theme;
import org.Tajgram.ui.Cells.TextSelectionHelper;
import org.Tajgram.ui.Components.LayoutHelper;
import org.Tajgram.ui.Components.RadialProgress2;
import org.Tajgram.ui.Components.RecyclerListView;
import org.Tajgram.ui.Components.UItem;
import org.Tajgram.ui.Components.UniversalAdapter;
import org.Tajgram.ui.Components.UniversalRecyclerView;

import java.util.ArrayList;

public class RichMediaCell extends FrameLayout
    implements Theme.Colorable, TextSelectionHelper.ArticleSelectableView {

    public interface Delegate {
        void onMediaPick(BlockRow row);
        TextSelectionHelper.ArticleTextSelectionHelper getSelectionHelper();
    }

    private static final int DEFAULT_HEIGHT_DP = 200;

    private final Theme.ResourcesProvider resourcesProvider;
    private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint selectionPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint stubPaint = new TextPaint();
    private final ImageReceiver imageReceiver;
    private final RadialProgress2 radialProgress;
    private final Drawable placeholderIcon;
    private final View clickView;

    private Layout stubLayout;
    private BlockRow currentRow;
    private Delegate delegate;

    public RichMediaCell(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;
        setWillNotDraw(false);

        stubPaint.setTextSize(1);

        imageReceiver = new ImageReceiver(this);

        radialProgress = new RadialProgress2(this, resourcesProvider);
        radialProgress.setProgressColor(0xffffffff);
        radialProgress.setColors(0x66000000, 0x7f000000, 0xffffffff, 0xffd9d9d9);
        radialProgress.setIcon(org.Tajgram.ui.Components.MediaActionDrawable.ICON_CANCEL, false, false);

        placeholderIcon = getContext().getResources().getDrawable(R.drawable.msg_filled_data_photos).mutate();

        setPadding(0, dp(6), 0, dp(4));

        clickView = new View(context);
        clickView.setOnClickListener(v -> {
            if (currentRow != null && currentRow.media != null && currentRow.media.state == MediaUploadState.STATE_EMPTY && delegate != null) {
                delegate.onMediaPick(currentRow);
            }
        });
        addView(clickView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));

        updateColors();
    }

    public void bind(BlockRow row, Delegate delegate) {
        this.currentRow = row;
        this.delegate = delegate;
        if (row != null && row.media == null) {
            row.media = new MediaUploadState();
        }
        applyMediaToImageReceiver();
        requestLayout();
        invalidate();
    }

    public BlockRow getRow() {
        return currentRow;
    }

    private void applyMediaToImageReceiver() {
        if (currentRow == null || currentRow.media == null) {
            imageReceiver.setImageBitmap((Drawable) null);
            return;
        }
        MediaUploadState ms = currentRow.media;
        String filter = (getMeasuredWidth() > 0 ? getMeasuredWidth() : AndroidUtilities.displaySize.x) + "_" + dp(DEFAULT_HEIGHT_DP);

        Drawable localThumbDrawable = ms.localThumbBitmap != null ? new android.graphics.drawable.BitmapDrawable(getResources(), ms.localThumbBitmap) : null;

        if (ms.isVideo) {
            ImageLocation mediaLoc = ms.localPath != null ? ImageLocation.getForVideoPath(ms.localPath) : null;
            ImageLocation imageLoc = null;
            ImageLocation thumbLoc = null;
            if (ms.isReady() && ms.document != null) {
                TLRPC.PhotoSize big = pickNonStrippedClosest(ms.document.thumbs, AndroidUtilities.getPhotoSize());
                TLRPC.PhotoSize stripped = pickStripped(ms.document.thumbs);
                imageLoc = ImageLocation.getForDocument(big, ms.document);
                thumbLoc = ImageLocation.getForDocument(stripped, ms.document);
            }
            android.util.Log.d("RICHED", "RichMediaCell.applyMedia VIDEO mediaLoc=" + (mediaLoc != null) + " imageLoc=" + (imageLoc != null) + " thumbLoc=" + (thumbLoc != null) + " localBitmap=" + (localThumbDrawable != null) + " path=" + ms.localPath);
            imageReceiver.setImage(
                mediaLoc, org.Tajgram.messenger.ImageLoader.AUTOPLAY_FILTER,
                imageLoc, filter,
                thumbLoc, filter,
                localThumbDrawable, 0, null, ms.document, 0
            );
        } else if (ms.isReady() && ms.photo != null) {
            TLRPC.PhotoSize big = FileLoader.getClosestPhotoSizeWithSize(ms.photo.sizes, AndroidUtilities.getPhotoSize());
            TLRPC.PhotoSize thumb = FileLoader.getClosestPhotoSizeWithSize(ms.photo.sizes, 100);
            imageReceiver.setImage(
                ImageLocation.getForPhoto(big, ms.photo), filter,
                ImageLocation.getForPhoto(thumb, ms.photo), filter,
                null, 0, null, ms.photo, 0
            );
        } else if (ms.localPath != null) {
            android.util.Log.d("RICHED", "RichMediaCell.applyMedia PHOTO preview path=" + ms.localPath);
            imageReceiver.setImage(ImageLocation.getForPath(ms.localPath), filter, null, null, null, 0);
        } else {
            imageReceiver.setImageBitmap((Drawable) null);
        }
    }

    private static TLRPC.PhotoSize pickNonStrippedClosest(ArrayList<TLRPC.PhotoSize> sizes, int target) {
        if (sizes == null) return null;
        TLRPC.PhotoSize best = null;
        int bestDist = Integer.MAX_VALUE;
        for (int i = 0; i < sizes.size(); i++) {
            TLRPC.PhotoSize s = sizes.get(i);
            if (s instanceof TLRPC.TL_photoStrippedSize || s instanceof TLRPC.TL_photoPathSize) continue;
            int side = Math.max(s.w, s.h);
            int dist = Math.abs(side - target);
            if (dist < bestDist) {
                bestDist = dist;
                best = s;
            }
        }
        return best;
    }

    private static TLRPC.PhotoSize pickStripped(ArrayList<TLRPC.PhotoSize> sizes) {
        if (sizes == null) return null;
        for (int i = 0; i < sizes.size(); i++) {
            if (sizes.get(i) instanceof TLRPC.TL_photoStrippedSize) return sizes.get(i);
        }
        return null;
    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        imageReceiver.onAttachedToWindow();
        applyMediaToImageReceiver();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        imageReceiver.onDetachedFromWindow();
    }

    @Override
    public void updateColors() {
        backgroundPaint.setColor(Theme.getColor(Theme.key_chat_inFileBackground, resourcesProvider));
        selectionPaint.setColor(Theme.getColor(Theme.key_chat_inTextSelectionHighlight, resourcesProvider));
        placeholderIcon.setColorFilter(new PorterDuffColorFilter(Theme.multAlpha(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider), 0.5f), PorterDuff.Mode.SRC_IN));
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int w = MeasureSpec.getSize(widthMeasureSpec);
        int h = dp(DEFAULT_HEIGHT_DP);
        if (currentRow != null && currentRow.media != null && currentRow.media.width > 0 && currentRow.media.height > 0) {
            int avail = w - dp(32);
            h = (int) ((long) avail * currentRow.media.height / currentRow.media.width);
            h = Math.min(h, dp(420));
            h = Math.max(h, dp(120));
            h += getPaddingTop() + getPaddingBottom();
        }
        super.onMeasure(
            MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY)
        );
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        int contentW = right - left;
        int contentH = bottom - top - getPaddingTop() - getPaddingBottom();
        imageReceiver.setImageCoords(0, getPaddingTop(), contentW, contentH);
        int btn = dp(48);
        radialProgress.setProgressRect((right - left - btn) / 2, (bottom - top - btn) / 2, (right - left + btn) / 2, (bottom - top + btn) / 2);
        applyMediaToImageReceiver();
    }

    private boolean isCellSelected() {
        if (delegate == null) return false;
        TextSelectionHelper.ArticleTextSelectionHelper helper = delegate.getSelectionHelper();
        if (helper == null || !helper.isInSelectionMode()) return false;
        if (!(getParent() instanceof RecyclerView)) return false;
        int myPos = ((RecyclerView) getParent()).getChildAdapterPosition(this);
        if (myPos < 0) return false;
        return myPos >= helper.getStartCell() && myPos <= helper.getEndCell();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        final int contentH = getMeasuredHeight() - getPaddingTop() - getPaddingBottom();
        final MediaUploadState ms = currentRow != null ? currentRow.media : null;
        final boolean hasImage = ms != null && (ms.localPath != null || ms.isReady());

        if (!hasImage) {
            canvas.drawRect(getPaddingLeft(), getPaddingTop(), getWidth() - getPaddingRight(), getHeight() - getPaddingBottom(), backgroundPaint);
            final int iconSize = dp(48);
            final int ix = (getMeasuredWidth() - iconSize) / 2;
            final int iy = (contentH - iconSize) / 2;
            placeholderIcon.setBounds(ix, iy, ix + iconSize, iy + iconSize);
            placeholderIcon.draw(canvas);
        } else {
            imageReceiver.draw(canvas);
        }

        if (ms != null && ms.isPending()) {
            radialProgress.setProgress(ms.progress, true);
            radialProgress.draw(canvas);
        }

        if (isCellSelected()) {
            canvas.drawRect(getPaddingLeft(), getPaddingTop(), getWidth() - getPaddingRight(), getHeight() - getPaddingBottom(), selectionPaint);
        }
    }

    @Override
    public void fillTextLayoutBlocks(ArrayList<TextSelectionHelper.TextLayoutBlock> out) {
        if (stubLayout == null) {
            int w = Math.max(1, getMeasuredWidth());
            stubLayout = new StaticLayout("•", stubPaint, w, Layout.Alignment.ALIGN_NORMAL, 1f, 0f, false);
        }
        final Layout layout = stubLayout;
        out.add(new TextSelectionHelper.TextLayoutBlock() {
            @Override public Layout getLayout() { return layout; }
            @Override public int getX() { return 0; }
            @Override public int getY() { return 0; }
            @Override public int getRow() { return 0; }
        });
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        stubLayout = null;
    }

    public static final class Factory extends UItem.UItemFactory<RichMediaCell> {
        static { setup(new Factory()); }

        @Override
        public RichMediaCell createView(Context context, RecyclerListView listView, int currentAccount, int classGuid, Theme.ResourcesProvider resourcesProvider) {
            return new RichMediaCell(context, resourcesProvider);
        }

        @Override
        public void bindView(View view, UItem item, boolean divider, UniversalAdapter adapter, UniversalRecyclerView listView) {
            ((RichMediaCell) view).bind((BlockRow) item.object, (Delegate) item.object2);
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
