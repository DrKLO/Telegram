package org.telegram.ui.iv;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.text.TextPaint;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;

import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.WebFile;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_iv;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextSelectionHelper;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;

import java.util.ArrayList;

public class RichMapCell extends RichBlockCell
    implements Theme.Colorable, TextSelectionHelper.ArticleSelectableView, RichCaptionHost {

    public interface Delegate {
        void onPickLocation(BlockRow row);
        TextSelectionHelper.ArticleTextSelectionHelper getSelectionHelper();
        default void onCaptionWillChange(BlockRow row, int removed, int added) {}
        default void onCaptionChanged(BlockRow row) {}
        default void onCaptionSpansChanged(BlockRow row) {}
        default void onCaptionEnter(BlockRow row) {}
        default void onRequestWindowFocusable(RichEditText et, boolean showKeyboard) {}
        default void onCaptionLockedInsert(CharSequence text) {}
        default boolean onCaptionSelectAll(BlockRow row) { return false; }
    }

    private static final int DEFAULT_HEIGHT_DP = 200;
    private static final int MAP_ZOOM = 15;

    private final int currentAccount;
    private final Theme.ResourcesProvider resourcesProvider;
    private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint placeholderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint selectionPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint hintPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final ImageReceiver imageReceiver;
    private final Drawable placeholderIcon;
    private final View clickView;

    private Delegate delegate;
    private int currentMapProvider;
    private String loadedKey;
    private Drawable redPinIcon;
    private int mapImageH;
    private final RichCaptionController caption;

    public RichMapCell(Context context, int currentAccount, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.currentAccount = currentAccount;
        this.resourcesProvider = resourcesProvider;
        setWillNotDraw(false);

        hintPaint.setTextSize(dp(15));
        hintPaint.setTextAlign(Paint.Align.CENTER);

        imageReceiver = new ImageReceiver(this);

        placeholderIcon = getContext().getResources().getDrawable(R.drawable.msg_map).mutate();

        setBlockPadding(0, dp(6), 0, dp(4));

        clickView = new View(context);
        clickView.setOnClickListener(v -> {
            if (currentRow != null && delegate != null) {
                delegate.onPickLocation(currentRow);
            }
        });
        addView(clickView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP));

        caption = new RichCaptionController(context, resourcesProvider, new RichCaptionController.Host() {
            @Override public BlockRow currentRow() { return currentRow; }
            @Override public TextSelectionHelper.ArticleTextSelectionHelper selectionHelper() { return delegate != null ? delegate.getSelectionHelper() : null; }
            @Override public TextSelectionHelper.ArticleSelectableView cell() { return RichMapCell.this; }
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
    protected int nestedContentMargin() {
        return dp(16); // edge-to-edge map at top level; inside a quote/list it needs the page margin
    }

    public void bind(BlockRow row, Delegate delegate) {
        this.currentRow = row;
        this.delegate = delegate;
        bindBlockInset(row);
        loadedKey = null;
        caption.bind();
        loadMapImage();
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

    private TL_iv.pageBlockMap getMap() {
        if (currentRow != null && currentRow.block instanceof TL_iv.pageBlockMap) {
            return (TL_iv.pageBlockMap) currentRow.block;
        }
        return null;
    }

    private boolean hasLocation() {
        return hasGeo(getMap());
    }

    public static boolean hasGeo(TL_iv.pageBlockMap map) {
        return map != null && map.geo instanceof TLRPC.TL_geoPoint;
    }

    private void loadMapImage() {
        final TL_iv.pageBlockMap map = getMap();
        if (!hasGeo(map)) {
            imageReceiver.setImageBitmap((Drawable) null);
            loadedKey = null;
            return;
        }
        final int contentW = getMeasuredWidth();
        final int contentH = mapImageH;
        if (contentW <= 0 || contentH <= 0) {
            return;
        }
        final int wDp = (int) (contentW / AndroidUtilities.density);
        final int hDp = (int) (contentH / AndroidUtilities.density);
        final String key = map.geo.lat + "_" + map.geo._long + "_" + wDp + "x" + hDp;
        if (key.equals(loadedKey)) {
            return;
        }
        loadedKey = key;
        currentMapProvider = MessagesController.getInstance(currentAccount).mapProvider;
        if (currentMapProvider == 2) {
            final WebFile webFile = WebFile.createWithGeoPoint(map.geo, wDp, hDp, MAP_ZOOM, Math.min(2, (int) Math.ceil(AndroidUtilities.density)));
            imageReceiver.setImage(ImageLocation.getForWebFile(webFile), null, null, null, null, 0);
        } else {
            final String url = AndroidUtilities.formapMapUrl(currentAccount, map.geo.lat, map.geo._long, wDp, hDp, true, MAP_ZOOM, -1);
            imageReceiver.setImage(url, null, null, null, 0);
        }
    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        imageReceiver.onAttachedToWindow();
        loadedKey = null;
        loadMapImage();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        imageReceiver.onDetachedFromWindow();
    }

    @Override
    public void updateColors() {
        backgroundPaint.setColor(Theme.getColor(Theme.key_chat_inLocationBackground, resourcesProvider));
        placeholderPaint.setColor(Theme.getColor(Theme.key_chat_inFileBackground, resourcesProvider));
        selectionPaint.setColor(Theme.getColor(Theme.key_chat_inTextSelectionHighlight, resourcesProvider));
        hintPaint.setColor(Theme.multAlpha(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider), 0.5f));
        placeholderIcon.setColorFilter(new PorterDuffColorFilter(Theme.multAlpha(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider), 0.5f), PorterDuff.Mode.SRC_IN));
        if (caption != null) caption.applyColors();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        final int w = MeasureSpec.getSize(widthMeasureSpec);
        final int insL = getPaddingLeft(), insR = getPaddingRight();
        final int contentW = Math.max(0, w - insL - insR);
        final TL_iv.pageBlockMap map = getMap();
        if (map != null && map.w > 0 && map.h > 0) {
            int avail = contentW - dp(32);
            int h = (int) ((long) avail * map.h / map.w);
            h = Math.min(h, dp(420));
            h = Math.max(h, dp(120));
            mapImageH = h;
        } else {
            mapImageH = dp(DEFAULT_HEIGHT_DP) - getPaddingTop() - getPaddingBottom();
        }
        final int capH = caption.measure(insL, insR, w);
        clickView.measure(
            MeasureSpec.makeMeasureSpec(contentW, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(mapImageH, MeasureSpec.EXACTLY)
        );
        setMeasuredDimension(w, getPaddingTop() + mapImageH + capH + getPaddingBottom());
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        final int insL = getPaddingLeft(), insR = getPaddingRight();
        final int contentW = Math.max(0, (right - left) - insL - insR);
        imageReceiver.setImageCoords(insL, getPaddingTop(), contentW, mapImageH);
        clickView.layout(insL, getPaddingTop(), insL + contentW, getPaddingTop() + mapImageH);
        caption.layout(insL, insR, right - left, getPaddingTop() + mapImageH);
        loadMapImage();
    }

    private boolean isCellSelected() {
        if (delegate == null) return false;
        TextSelectionHelper.ArticleTextSelectionHelper helper = delegate.getSelectionHelper();
        if (helper == null || !helper.isInSelectionMode()) return false;
        if (!(getParent() instanceof RecyclerView)) return false;
        int myPos = ((RecyclerView) getParent()).getChildAdapterPosition(this);
        if (myPos < 0) return false;
        return myPos > helper.getStartCell() && myPos <= helper.getEndCell();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (getMap() != null) {
            canvas.drawRect(imageReceiver.getImageX(), imageReceiver.getImageY(), imageReceiver.getImageX2(), imageReceiver.getImageY2(), backgroundPaint);

            int cx = (int) imageReceiver.getCenterX();
            int cy = (int) imageReceiver.getCenterY();
            if (placeholderIcon != null) {
                int l = cx - placeholderIcon.getIntrinsicWidth() / 2;
                int t = cy - placeholderIcon.getIntrinsicHeight() / 2;
                placeholderIcon.setBounds(l, t, l + placeholderIcon.getIntrinsicWidth(), t + placeholderIcon.getIntrinsicHeight());
                placeholderIcon.draw(canvas);
            }

            if (hasLocation()) {
                imageReceiver.draw(canvas);

                if (currentMapProvider == 2 && imageReceiver.hasNotThumb()) {
                    if (redPinIcon == null) {
                        redPinIcon = getContext().getResources().getDrawable(R.drawable.map_pin).mutate();
                    }
                    int w = (int) (redPinIcon.getIntrinsicWidth() * 0.8f);
                    int h = (int) (redPinIcon.getIntrinsicHeight() * 0.8f);
                    int x = (int) (imageReceiver.getCenterX() - w / 2f);
                    int y = (int) (imageReceiver.getCenterY() - h);
                    redPinIcon.setAlpha((int) (255 * imageReceiver.getCurrentAlpha()));
                    redPinIcon.setBounds(x, y, x + w, y + h);
                    redPinIcon.draw(canvas);
                }
            }
        }

        if (isCellSelected()) {
            canvas.drawRect(
                getPaddingLeft(), getPaddingTop(),
                getWidth() - getPaddingRight(), getPaddingTop() + mapImageH,
                selectionPaint
            );
        }
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

    public static final class Factory extends UItem.UItemFactory<RichMapCell> {
        static { setup(new Factory()); }

        @Override
        public RichMapCell createView(Context context, RecyclerListView listView, int currentAccount, int classGuid, Theme.ResourcesProvider resourcesProvider) {
            return new RichMapCell(context, currentAccount, resourcesProvider);
        }

        @Override
        public void bindView(View view, UItem item, boolean divider, UniversalAdapter adapter, UniversalRecyclerView listView) {
            ((RichMapCell) view).bind((BlockRow) item.object, (Delegate) item.object2);
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
