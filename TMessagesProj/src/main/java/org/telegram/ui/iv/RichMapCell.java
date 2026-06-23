package org.Tajgram.ui.iv;

import static org.Tajgram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;

import androidx.recyclerview.widget.RecyclerView;

import org.Tajgram.messenger.AndroidUtilities;
import org.Tajgram.messenger.ImageLocation;
import org.Tajgram.messenger.ImageReceiver;
import org.Tajgram.messenger.MessagesController;
import org.Tajgram.messenger.R;
import org.Tajgram.messenger.WebFile;
import org.Tajgram.tgnet.tl.TL_iv;
import org.Tajgram.ui.ActionBar.Theme;
import org.Tajgram.ui.Cells.TextSelectionHelper;
import org.Tajgram.ui.Components.LayoutHelper;
import org.Tajgram.ui.Components.RecyclerListView;
import org.Tajgram.ui.Components.UItem;
import org.Tajgram.ui.Components.UniversalAdapter;
import org.Tajgram.ui.Components.UniversalRecyclerView;

import java.util.ArrayList;

public class RichMapCell extends FrameLayout
    implements Theme.Colorable, TextSelectionHelper.ArticleSelectableView {

    public interface Delegate {
        void onPickLocation(BlockRow row);
        TextSelectionHelper.ArticleTextSelectionHelper getSelectionHelper();
    }

    private static final int DEFAULT_HEIGHT_DP = 200;
    private static final int MAP_ZOOM = 15;

    private final int currentAccount;
    private final Theme.ResourcesProvider resourcesProvider;
    private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint placeholderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint selectionPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint stubPaint = new TextPaint();
    private final TextPaint hintPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final ImageReceiver imageReceiver;
    private final Drawable placeholderIcon;
    private final View clickView;

    private Layout stubLayout;
    private BlockRow currentRow;
    private Delegate delegate;
    private int currentMapProvider;
    private String loadedKey;

    public RichMapCell(Context context, int currentAccount, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.currentAccount = currentAccount;
        this.resourcesProvider = resourcesProvider;
        setWillNotDraw(false);

        stubPaint.setTextSize(1);
        hintPaint.setTextSize(dp(15));
        hintPaint.setTextAlign(Paint.Align.CENTER);

        imageReceiver = new ImageReceiver(this);

        placeholderIcon = getContext().getResources().getDrawable(R.drawable.msg_map).mutate();

        setPadding(0, dp(6), 0, dp(4));

        clickView = new View(context);
        clickView.setOnClickListener(v -> {
            if (currentRow != null && delegate != null) {
                delegate.onPickLocation(currentRow);
            }
        });
        addView(clickView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));

        updateColors();
    }

    public void bind(BlockRow row, Delegate delegate) {
        this.currentRow = row;
        this.delegate = delegate;
        loadedKey = null;
        loadMapImage();
        requestLayout();
        invalidate();
    }

    public BlockRow getRow() {
        return currentRow;
    }

    private TL_iv.pageBlockMap getMap() {
        if (currentRow != null && currentRow.block instanceof TL_iv.pageBlockMap) {
            return (TL_iv.pageBlockMap) currentRow.block;
        }
        return null;
    }

    private boolean hasLocation() {
        final TL_iv.pageBlockMap map = getMap();
        return map != null && map.geo != null;
    }

    private void loadMapImage() {
        final TL_iv.pageBlockMap map = getMap();
        if (map == null || map.geo == null) {
            imageReceiver.setImageBitmap((Drawable) null);
            loadedKey = null;
            return;
        }
        final int contentW = getMeasuredWidth();
        final int contentH = getMeasuredHeight() - getPaddingTop() - getPaddingBottom();
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
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int w = MeasureSpec.getSize(widthMeasureSpec);
        int h = dp(DEFAULT_HEIGHT_DP);
        final TL_iv.pageBlockMap map = getMap();
        if (map != null && map.w > 0 && map.h > 0) {
            int avail = w - dp(32);
            h = (int) ((long) avail * map.h / map.w);
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
        loadMapImage();
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

        if (!hasLocation()) {
            canvas.drawRect(getPaddingLeft(), getPaddingTop(), getWidth() - getPaddingRight(), getHeight() - getPaddingBottom(), placeholderPaint);
            final int iconSize = dp(40);
            final int ix = (getMeasuredWidth() - iconSize) / 2;
            final int iy = getPaddingTop() + (contentH - iconSize) / 2 - dp(12);
            placeholderIcon.setBounds(ix, iy, ix + iconSize, iy + iconSize);
            placeholderIcon.draw(canvas);
            final String hint = "Tap to pick a location";
            canvas.drawText(hint, getMeasuredWidth() / 2f, iy + iconSize + dp(20), hintPaint);
        } else {
            canvas.drawRect(imageReceiver.getImageX(), imageReceiver.getImageY(), imageReceiver.getImageX2(), imageReceiver.getImageY2(), backgroundPaint);
            int cx = (int) imageReceiver.getCenterX();
            int cy = (int) imageReceiver.getCenterY();
            Drawable pin = Theme.chat_locationDrawable[0];
            if (pin != null) {
                int l = cx - pin.getIntrinsicWidth() / 2;
                int t = cy - pin.getIntrinsicHeight() / 2;
                pin.setBounds(l, t, l + pin.getIntrinsicWidth(), t + pin.getIntrinsicHeight());
                pin.draw(canvas);
            }
            imageReceiver.draw(canvas);
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
