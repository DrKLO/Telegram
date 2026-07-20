package org.telegram.ui.iv;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RecordingCanvas;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.messenger.RichMessageLayout;
import org.telegram.messenger.SharedConfig;
import org.telegram.tgnet.tl.TL_iv;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.blur3.drawable.BlurredBackgroundDrawableRenderNode;
import org.telegram.ui.Components.blur3.drawable.color.BlurredBackgroundColorProvider;
import org.telegram.ui.Components.blur3.source.BlurredBackgroundSourceColor;
import org.telegram.ui.Components.blur3.source.BlurredBackgroundSourceRenderNode;
import org.telegram.ui.Cells.TextSelectionHelper;
import org.telegram.ui.Components.AnimatedFloat;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.ItemOptions;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.ScaleStateListAnimator;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;
import org.telegram.ui.Components.spoilers.SpoilerEffect2;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

public class RichMediaCell extends RichBlockCell
    implements Theme.Colorable, TextSelectionHelper.ArticleSelectableView, RichCaptionHost {

    public interface Delegate {
        void onMediaPick(BlockRow row);
        void onAddMedia(BlockRow row);
        default void onSwitchMode(BlockRow row) {}
        void onCancelUpload(BlockRow row, MediaUploadState media);
        default void onDeleteMedia(BlockRow row, MediaUploadState media) {}
        default void onToggleSpoiler(BlockRow row, MediaUploadState media) {}
        default ItemOptions makeMenu(View anchor) { return null; }
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
    private static final int GAP_DP = 2;

    private final Theme.ResourcesProvider resourcesProvider;
    private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint selectionPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RichCaptionController caption;
    private final ImageView addButton;
    private final ImageView switchModeButton;

    private final ArrayList<RichMediaItem> items = new ArrayList<>();
    private final ArrayList<ImageView> menuButtons = new ArrayList<>();
    private final ArrayList<RectF> collageRects = new ArrayList<>();
    private final ArrayList<RectF> itemRects = new ArrayList<>();

    private final boolean glass = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && SharedConfig.chatBlurEnabled();
    private BlurredBackgroundSourceRenderNode blurSource;
    private BlurredBackgroundColorProvider blurColors;
    private final ArrayList<ImageView> circleButtons = new ArrayList<>();
    private final HashMap<ImageView, BlurredBackgroundDrawableRenderNode> circleButtonBg = new HashMap<>();

    private SpoilerEffect2 spoilerEffect;

    private Delegate delegate;
    private boolean attached;
    private int imageW;
    private int imageH;
    private int collageH;
    private int slideW;
    private int slideH;
    private int pressedItem = -1;

    // mode transition: 0 = collage, 1 = slideshow
    private final AnimatedFloat modeProgress = new AnimatedFloat(this, 0, 320, CubicBezierInterpolator.EASE_OUT_QUINT);
    private int lastSwitchIconRes;

    private int currentPage;
    private float pageOffset;
    private boolean dragging;
    private float downX, downY;
    private int touchSlop;
    private ValueAnimator settleAnimator;

    private static Drawable slideDotDrawable;
    private static Drawable slideDotBigDrawable;

    public RichMediaCell(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;
        setWillNotDraw(false);

        setBlockPadding(0, dp(6), 0, dp(4));

        caption = new RichCaptionController(context, resourcesProvider, new RichCaptionController.Host() {
            @Override public BlockRow currentRow() { return currentRow; }
            @Override public TextSelectionHelper.ArticleTextSelectionHelper selectionHelper() { return delegate != null ? delegate.getSelectionHelper() : null; }
            @Override public TextSelectionHelper.ArticleSelectableView cell() { return RichMediaCell.this; }
            @Override public void onCaptionWillChange(int removed, int added) { if (delegate != null) delegate.onCaptionWillChange(currentRow, removed, added); }
            @Override public void onCaptionChanged() { if (delegate != null) delegate.onCaptionChanged(currentRow); }
            @Override public void onCaptionSpansChanged() { if (delegate != null) delegate.onCaptionSpansChanged(currentRow); }
            @Override public void onCaptionEnter() { if (delegate != null) delegate.onCaptionEnter(currentRow); }
            @Override public void onRequestWindowFocusable(RichEditText et, boolean showKeyboard) { if (delegate != null) delegate.onRequestWindowFocusable(et, showKeyboard); }
            @Override public void onCaptionLockedInsert(CharSequence text) { if (delegate != null) delegate.onCaptionLockedInsert(text); }
            @Override public boolean onCaptionSelectAll() { return delegate != null && delegate.onCaptionSelectAll(currentRow); }
        });
        addView(caption.editText, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP));

        setClipChildren(false);
        setClipToPadding(false);

        if (glass && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            blurSource = new BlurredBackgroundSourceRenderNode(new BlurredBackgroundSourceColor());
            blurSource.setBlur(dp(24));
            blurColors = new BlurredBackgroundColorProvider() {
                @Override public int getShadowColor() { return 0; }
                @Override public int getBackgroundColor() { return 0x66000000; }
                @Override public int getStrokeColorTop() { return 0x33FFFFFF; }
                @Override public int getStrokeColorBottom() { return 0x14FFFFFF; }
            };
        }

        addButton = createCircleButton();
        addButton.setImageResource(R.drawable.iv_media_add);
        addView(addButton, LayoutHelper.createFrame(32, 32, Gravity.RIGHT | Gravity.TOP, 12, 12, 12, 12));
        addButton.setOnClickListener(v -> {
            if (delegate != null && currentRow != null) delegate.onAddMedia(currentRow);
        });

        switchModeButton = createCircleButton();
        switchModeButton.setVisibility(View.GONE);
        addView(switchModeButton, LayoutHelper.createFrame(32, 32, Gravity.RIGHT | Gravity.TOP, 12, 12, 12 + 42 + 12, 12));
        switchModeButton.setOnClickListener(v -> {
            if (delegate != null && currentRow != null) delegate.onSwitchMode(currentRow);
        });

        updateColors();
    }

    private ImageView createCircleButton() {
        final ImageView b = new ImageView(getContext());
        b.setScaleType(ImageView.ScaleType.CENTER);
        if (glass && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            b.setColorFilter(new PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN));
            final BlurredBackgroundDrawableRenderNode bg = (BlurredBackgroundDrawableRenderNode) blurSource.createDrawable();
            bg.setColorProvider(blurColors);
            bg.setRadius(dp(16));
            circleButtonBg.put(b, bg);
        } else {
            b.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider), PorterDuff.Mode.SRC_IN));
            b.setBackground(RichEditor.withShadow(Theme.createRadSelectorDrawable(Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider), Theme.blendOver(Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider), Theme.getColor(Theme.key_listSelector, resourcesProvider)), 20, 20)));
        }
        ScaleStateListAnimator.apply(b);
        circleButtons.add(b);
        return b;
    }

    private boolean isSlideshow() {
        return currentRow != null && currentRow.block instanceof TL_iv.pageBlockSlideshow;
    }

    @Override
    protected int nestedContentMargin() {
        return dp(16); // the image is edge-to-edge at top level; inside a quote/list it needs the page margin
    }

    /**
     * The page margin that {@link #nestedContentMargin()} added to this cell's padding when nested — the caption
     * already carries its own equivalent inset, so it must NOT double up: subtract this from the caption's insets.
     */
    private int captionMargin() {
        return RichBlockChrome.insetFor(currentRow) > 0 ? nestedContentMargin() : 0;
    }

    public void bind(BlockRow row, Delegate delegate) {
        this.currentRow = row;
        this.delegate = delegate;
        bindBlockInset(row);
        if (row != null && !isGalleryRow() && row.media == null) {
            row.media = new MediaUploadState();
        }
        rebuildItems();
        caption.bind();
        if (currentPage >= items.size()) currentPage = Math.max(0, items.size() - 1);
        pageOffset = 0;
        modeProgress.set(isSlideshow() ? 1f : 0f, true);
        updateSwitchButton(false);
        requestLayout();
        invalidate();
    }

    public void refresh() {
        if (currentRow == null) return;
        rebuildItems();
        if (currentPage >= items.size()) currentPage = Math.max(0, items.size() - 1);
        updateSwitchButton(false);
        requestLayout();
        invalidate();
    }

    private boolean isGalleryRow() {
        return currentRow != null
            && (currentRow.block instanceof TL_iv.pageBlockCollage || currentRow.block instanceof TL_iv.pageBlockSlideshow);
    }

    private List<MediaUploadState> medias() {
        if (currentRow == null) return Collections.emptyList();
        if (isGalleryRow()) {
            return currentRow.medias != null ? currentRow.medias : Collections.emptyList();
        }
        if (currentRow.media != null) return Collections.singletonList(currentRow.media);
        return Collections.emptyList();
    }

    private void updateSwitchButton(boolean animated) {
        final boolean show = items.size() >= 2;
        switchModeButton.setVisibility(show ? VISIBLE : GONE);
        if (!show) return;
        final int icon = isSlideshow() ? R.drawable.iv_media_slideshow : R.drawable.iv_media_collage;
        if (icon == lastSwitchIconRes) return;
        lastSwitchIconRes = icon;
        if (animated) {
            AndroidUtilities.updateImageViewImageAnimated(switchModeButton, icon);
        } else {
            switchModeButton.setImageResource(icon);
        }
    }

    public void onModeChanged() {
        if (settleAnimator != null) { settleAnimator.cancel(); settleAnimator = null; }
        currentPage = 0;
        pageOffset = 0;
        updateSwitchButton(true);
        requestLayout();
        invalidate();
    }

    private void rebuildItems() {
        final List<MediaUploadState> ms = medias();
        while (items.size() < ms.size()) {
            final RichMediaItem item = new RichMediaItem(this, resourcesProvider);
            if (attached) item.attach();
            items.add(item);
        }
        while (items.size() > ms.size()) {
            items.remove(items.size() - 1).detach();
        }
        for (int i = 0; i < ms.size(); i++) {
            items.get(i).setMedia(ms.get(i));
        }
        while (menuButtons.size() < ms.size()) {
            final ImageView b = createCircleButton();
            b.setImageResource(R.drawable.iv_media_dots);
            b.setOnClickListener(v -> onMenuClicked(menuButtons.indexOf(v)));
            addView(b, LayoutHelper.createFrame(32, 32, Gravity.LEFT | Gravity.TOP));
            menuButtons.add(b);
        }
        addButton.bringToFront();
        switchModeButton.bringToFront();
        while (menuButtons.size() > ms.size()) {
            final ImageView b = menuButtons.remove(menuButtons.size() - 1);
            removeView(b);
            circleButtons.remove(b);
            circleButtonBg.remove(b);
        }
        if (spoilerEffect != null && !hasAnySpoiler()) {
            spoilerEffect.detach(this);
            spoilerEffect = null;
        }
    }

    private boolean hasAnySpoiler() {
        for (int i = 0; i < items.size(); i++) {
            final MediaUploadState m = items.get(i).getMedia();
            if (m != null && m.hasSpoiler) return true;
        }
        return false;
    }

    private void onMenuClicked(int index) {
        if (delegate == null || currentRow == null) return;
        final List<MediaUploadState> ms = medias();
        if (index < 0 || index >= ms.size() || index >= menuButtons.size()) return;
        final MediaUploadState media = ms.get(index);
        final ItemOptions o = delegate.makeMenu(menuButtons.get(index));
        if (o == null) return;
        o.add(
            media.hasSpoiler ? R.drawable.msg_spoiler_off : R.drawable.msg_spoiler,
            LocaleController.getString(media.hasSpoiler ? R.string.DisablePhotoSpoiler : R.string.EnablePhotoSpoiler),
            () -> { if (delegate != null && currentRow != null) delegate.onToggleSpoiler(currentRow, media); }
        );
        o.add(
            R.drawable.msg_delete,
            LocaleController.getString(R.string.Delete),
            true,
            () -> { if (delegate != null && currentRow != null) delegate.onDeleteMedia(currentRow, media); }
        );
        o.translate(0, -dp(38));
        if (glass) {
            o.setBlur(false, true);
            o.setDimAlpha(0);
        }
        o.show();
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

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        attached = true;
        for (int i = 0; i < items.size(); i++) items.get(i).attach();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        attached = false;
        for (int i = 0; i < items.size(); i++) items.get(i).detach();
        if (spoilerEffect != null) {
            spoilerEffect.detach(this);
            spoilerEffect = null;
        }
        if (settleAnimator != null) { settleAnimator.cancel(); settleAnimator = null; }
    }

    private SpoilerEffect2 getSpoilerEffect() {
        if (!attached || !SpoilerEffect2.supports()) return null;
        if (spoilerEffect != null && spoilerEffect.destroyed) spoilerEffect = null;
        if (spoilerEffect == null) spoilerEffect = SpoilerEffect2.getInstance(this);
        return spoilerEffect;
    }

    @Override
    public void updateColors() {
        backgroundPaint.setColor(Theme.getColor(Theme.key_chat_inFileBackground, resourcesProvider));
        selectionPaint.setColor(Theme.getColor(Theme.key_chat_inTextSelectionHighlight, resourcesProvider));
        if (caption != null) caption.applyColors();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        final int w = MeasureSpec.getSize(widthMeasureSpec);
        final int insL = getPaddingLeft(), insR = getPaddingRight();
        final int contentW = Math.max(0, w - insL - insR);
        final float p = modeProgress.set(isSlideshow() ? 1f : 0f);
        computeGeometry(contentW);
        imageW = contentW;
        imageH = Math.round(AndroidUtilities.lerp(collageH, slideH, p));
        buildItemRects(p);
        final int cm = captionMargin();
        final int capH = caption.measure(insL - cm, insR - cm, w);
        setMeasuredDimension(w, getPaddingTop() + imageH + capH + getPaddingBottom());
        addButton.measure(MeasureSpec.makeMeasureSpec(dp(32), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(dp(32), MeasureSpec.EXACTLY));
        switchModeButton.measure(MeasureSpec.makeMeasureSpec(dp(32), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(dp(32), MeasureSpec.EXACTLY));
        for (int i = 0; i < menuButtons.size(); i++) {
            menuButtons.get(i).measure(MeasureSpec.makeMeasureSpec(dp(32), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(dp(32), MeasureSpec.EXACTLY));
        }
        if (modeProgress.isInProgress()) requestLayout();
    }

    private void computeGeometry(int w) {
        collageRects.clear();
        final int top = getPaddingTop();
        final int n = items.size();
        if (n == 0) {
            collageH = slideH = dp(DEFAULT_HEIGHT_DP) - getPaddingTop() - getPaddingBottom();
            slideW = w;
            return;
        }
        if (n == 1) {
            final RichMediaItem it = items.get(0);
            final int natW = it.getWidth();
            final int natH = it.getHeight();
            int width = w;
            int height;
            if (natW > 0 && natH > 0) {
                float scale = width / (float) Math.max(1, natW);
                height = (int) (scale * natH);
                final int maxH = (int) (Math.max(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y) * 0.55f);
                if (height > maxH) {
                    height = maxH;
                    scale = height / (float) Math.max(1, natH);
                    width = (int) (scale * natW);
                }
            } else {
                height = dp(DEFAULT_HEIGHT_DP) - getPaddingTop() - getPaddingBottom();
            }
            final int left = (w - width) / 2;
            collageRects.add(new RectF(left, top, left + width, top + height));
            collageH = height;
            slideW = width;
            slideH = height;
            return;
        }

        final float[] ratios = new float[n];
        for (int i = 0; i < n; i++) ratios[i] = aspectRatio(items.get(i));
        final MessageObject.GroupedMessagePosition[] positions = RichMessageLayout.computeGrouped(ratios);

        final int maxSizeWidth = 1000;
        int maxRow = 0;
        for (MessageObject.GroupedMessagePosition p : positions) maxRow = Math.max(maxRow, p.maxY);

        final float[] rowH = new float[maxRow + 1];
        for (MessageObject.GroupedMessagePosition p : positions) {
            if (p.minY == p.maxY) rowH[p.minY] = Math.max(rowH[p.minY], p.ph);
        }
        for (MessageObject.GroupedMessagePosition p : positions) {
            if (p.minY != p.maxY) {
                final int span = p.maxY - p.minY + 1;
                if (p.siblingHeights != null && p.siblingHeights.length == span) {
                    for (int r = 0; r < span; ++r) {
                        rowH[p.minY + r] = Math.max(rowH[p.minY + r], p.siblingHeights[r]);
                    }
                } else {
                    final float per = p.ph / span;
                    for (int r = p.minY; r <= p.maxY; ++r) rowH[r] = Math.max(rowH[r], per);
                }
            }
        }

        final float pixelMaxHeight = Math.max(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y) * 0.5f;
        final int[] rowYPx = new int[maxRow + 2];
        float acc = 0f;
        for (int r = 0; r <= maxRow; ++r) {
            rowYPx[r] = Math.round(acc * pixelMaxHeight);
            acc += rowH[r];
        }
        rowYPx[maxRow + 1] = Math.round(acc * pixelMaxHeight);

        final int gap = dp(GAP_DP);
        for (int i = 0; i < positions.length; ++i) {
            final MessageObject.GroupedMessagePosition p = positions[i];
            final int yPx = rowYPx[p.minY];
            int hPx = rowYPx[p.maxY + 1] - yPx;

            int xPx;
            if (p.leftSpanOffset > 0) {
                xPx = Math.round(p.leftSpanOffset * w / (float) maxSizeWidth);
            } else {
                int leftUnits = 0;
                for (int j = 0; j < positions.length; ++j) {
                    if (j == i) continue;
                    final MessageObject.GroupedMessagePosition q = positions[j];
                    if (q.minY <= p.minY && q.maxY >= p.minY && q.minX < p.minX) {
                        leftUnits += q.pw;
                    }
                }
                xPx = Math.round(leftUnits * w / (float) maxSizeWidth);
            }

            int wPx;
            if ((p.flags & MessageObject.POSITION_FLAG_RIGHT) != 0) {
                wPx = w - xPx;
            } else {
                wPx = Math.round(p.pw * w / (float) maxSizeWidth);
                wPx -= gap;
            }
            if ((p.flags & MessageObject.POSITION_FLAG_BOTTOM) == 0) {
                hPx -= gap;
            }

            collageRects.add(new RectF(xPx, top + yPx, xPx + Math.max(0, wPx), top + yPx + Math.max(0, hPx)));
        }
        collageH = rowYPx[maxRow + 1];

        slideW = w;
        float avg = 0f;
        for (int i = 0; i < n; i++) avg += aspectRatio(items.get(i));
        avg /= n;
        int sh = (int) (slideW / Math.max(0.5f, avg));
        final int maxH = (int) (Math.max(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y) * 0.55f);
        if (sh > maxH) sh = maxH;
        slideH = sh;
    }

    private void buildItemRects(float p) {
        itemRects.clear();
        final int n = items.size();
        if (n == 0) return;
        final int top = getPaddingTop();
        if (n == 1) {
            itemRects.add(new RectF(collageRects.get(0)));
            return;
        }
        final float dx = -pageOffset * slideW;
        for (int i = 0; i < n && i < collageRects.size(); i++) {
            final RectF c = collageRects.get(i);
            final float sx = (i - currentPage) * slideW + dx;
            itemRects.add(new RectF(
                AndroidUtilities.lerp(c.left, sx, p),
                AndroidUtilities.lerp(c.top, top, p),
                AndroidUtilities.lerp(c.right, sx + slideW, p),
                AndroidUtilities.lerp(c.bottom, top + slideH, p)
            ));
        }
    }

    private static float aspectRatio(RichMediaItem item) {
        final int w = item.getWidth();
        final int h = item.getHeight();
        return (w > 0 && h > 0) ? w / (float) h : 1f;
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        final int insL = getPaddingLeft(), insR = getPaddingRight();
        final int fullW = right - left;
        final int contentW = Math.max(0, fullW - insL - insR);
        final int contentRight = fullW - insR;
        final int cm = captionMargin();
        caption.layout(insL - cm, insR - cm, fullW, getPaddingTop() + imageH);

        final int inset = dp(6);
        addButton.layout(contentRight - inset - addButton.getMeasuredWidth(), getPaddingTop() + inset, contentRight - inset, getPaddingTop() + inset + addButton.getMeasuredHeight());
        switchModeButton.layout(contentRight - inset - inset - addButton.getMeasuredWidth() - switchModeButton.getMeasuredWidth(), getPaddingTop() + inset, contentRight - inset - inset - addButton.getMeasuredWidth(), getPaddingTop() + inset + addButton.getMeasuredHeight());

        final List<MediaUploadState> ms = medias();
        for (int i = 0; i < menuButtons.size(); i++) {
            final ImageView b = menuButtons.get(i);
            final boolean hasContent = i < ms.size() && ms.get(i).state != MediaUploadState.STATE_EMPTY;
            if (!hasContent || i >= itemRects.size()) { b.setVisibility(GONE); continue; }
            final RectF r = itemRects.get(i);
            final boolean visible = r.right > 0 && r.left < contentW && r.bottom > getPaddingTop() && r.top < getPaddingTop() + imageH;
            if (!visible) { b.setVisibility(GONE); continue; }
            final int bx = (int) r.left + inset + insL;
            final int by = (int) r.top + inset;
            b.layout(bx, by, bx + b.getMeasuredWidth(), by + b.getMeasuredHeight());

            float alpha = 1f;
            if (glass) {
                final int clusterLeft = (switchModeButton.getVisibility() == VISIBLE ? switchModeButton.getLeft() : addButton.getLeft()) - dp(4);
                final int br = bx + b.getMeasuredWidth();
                if (br > clusterLeft) {
                    alpha = Math.max(0f, 1f - (br - clusterLeft) / (float) b.getMeasuredWidth());
                }
            }
            b.setAlpha(alpha);
            b.setVisibility(alpha <= 0.01f ? GONE : VISIBLE);
        }
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
        final int top = getPaddingTop();
        drawMedia(canvas);

        final float p = modeProgress.get();
        if (items.size() >= 2 && p > 0.001f) {
            drawDots(canvas, p);
        }

        if (isCellSelected()) {
            final int insL = getPaddingLeft();
            canvas.drawRect(insL, top, getWidth() - getPaddingRight(), top + imageH, selectionPaint);
        }

        drawGlassButtons(canvas);

        if (modeProgress.isInProgress()) {
            requestLayout();
        }
    }

    @Override
    protected boolean drawChild(@NonNull Canvas canvas, View child, long drawingTime) {
        if (menuButtons.contains(child) && RichBlockChrome.quoteDepth(currentRow) > 0) {
            canvas.save();
            clipPath.rewind();
            clipPath.addRoundRect(getPaddingLeft(), getPaddingTop(), getPaddingLeft() + Math.max(0, getWidth() - getPaddingLeft() - getPaddingRight()), getPaddingTop() + imageH, dp(8), dp(8), Path.Direction.CW);
            canvas.clipPath(clipPath);
            boolean r = super.drawChild(canvas, child, drawingTime);
            canvas.restore();
            return r;
        }
        return super.drawChild(canvas, child, drawingTime);
    }

    private void drawMedia(Canvas canvas) {
        final int top = getPaddingTop();
        final int insL = getPaddingLeft();
        final int contentW = Math.max(0, getWidth() - insL - getPaddingRight());
        canvas.save();
        canvas.translate(insL, 0);
        if (RichBlockChrome.quoteDepth(currentRow) > 0) {
            clipPath.rewind();
            clipPath.addRoundRect(0, top, contentW, top + imageH, dp(8), dp(8), Path.Direction.CW);
            canvas.clipPath(clipPath);
        } else {
            canvas.clipRect(0, top, contentW, top + imageH);
        }
        // singular media that doesn't fill the cell width: fill the side gaps with a blurred copy
        if (items.size() == 1 && itemRects.size() == 1) {
            final RectF r = itemRects.get(0);
            if (r.left > 0.5f || r.right < contentW - 0.5f) {
                AndroidUtilities.rectTmp.set(0, top, contentW, top + imageH);
                items.get(0).drawBlurBackground(canvas, AndroidUtilities.rectTmp);
            }
        }
        for (int i = 0; i < items.size() && i < itemRects.size(); i++) {
            final RichMediaItem item = items.get(i);
            final RectF r = itemRects.get(i);
            if (!item.hasImage()) {
                canvas.drawRect(r, backgroundPaint);
            }
            item.draw(canvas, r);
            final MediaUploadState m = item.getMedia();
            if (m != null && m.hasSpoiler && item.hasImage()) {
                // overlay the blurred copy + particles on top of the just-drawn image to hide it
                item.drawSpoiler(canvas, r, getSpoilerEffect(), this);
            }
        }
        canvas.restore();
    }

    private void drawGlassButtons(Canvas canvas) {
        if (!glass || blurSource == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return;
        final int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;

        if (canvas.isHardwareAccelerated() && !blurSource.inRecording()) {
            final RecordingCanvas rc = blurSource.beginRecording(w, h);
            try {
                drawMedia(rc);
            } finally {
                blurSource.endRecording();
            }
        }

        for (int i = 0; i < circleButtons.size(); i++) {
            final ImageView b = circleButtons.get(i);
            if (b == addButton || b == switchModeButton) continue;
            drawGlassButton(canvas, b);
        }
        drawGlassButton(canvas, switchModeButton);
        drawGlassButton(canvas, addButton);
    }

    private void drawGlassButton(Canvas canvas, ImageView b) {
        if (b.getVisibility() != VISIBLE) return;
        final BlurredBackgroundDrawableRenderNode bg = circleButtonBg.get(b);
        if (bg == null) return;
        bg.setBounds(b.getLeft(), b.getTop(), b.getRight(), b.getBottom());
        bg.setAlpha((int) (b.getAlpha() * 255));
        bg.invalidateDisplayList();
        bg.draw(canvas);
    }

    private void drawDots(Canvas canvas, float progress) {
        if (slideDotDrawable == null) {
            slideDotDrawable = getResources().getDrawable(R.drawable.slide_dot_small);
            slideDotBigDrawable = getResources().getDrawable(R.drawable.slide_dot_big);
        }
        final int n = items.size();
        final int alpha = (int) (255 * progress);
        final int dotsY = getPaddingTop() + imageH - dp(7 + 16);
        final int totalWidth = n * dp(7) + (n - 1) * dp(6) + dp(4);
        final int insL = getPaddingLeft();
        final int contentW = Math.max(0, getWidth() - insL - getPaddingRight());
        final int xOffset = insL + (contentW - totalWidth) / 2;
        for (int a = 0; a < n; a++) {
            final int cx = xOffset + dp(4) + dp(13) * a;
            final Drawable d = currentPage == a ? slideDotBigDrawable : slideDotDrawable;
            d.setAlpha(alpha);
            d.setBounds(cx - dp(5), dotsY, cx + dp(5), dotsY + dp(10));
            d.draw(canvas);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        final float x = event.getX();
        final float y = event.getY();
        final int act = event.getActionMasked();
        final boolean inImage = y >= getPaddingTop() && y < getPaddingTop() + imageH;
        final boolean slideMode = isSlideshow() && !modeProgress.isInProgress() && items.size() >= 2;

        if (slideMode) {
            if (act == MotionEvent.ACTION_DOWN) {
                if (!inImage) return super.onTouchEvent(event);
                if (touchSlop == 0) touchSlop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
                downX = x; downY = y;
                dragging = false;
                if (settleAnimator != null) { settleAnimator.cancel(); settleAnimator = null; }
                pressedItem = currentPage;
                return true;
            } else if (act == MotionEvent.ACTION_MOVE) {
                final float ddx = x - downX, ddy = y - downY;
                if (!dragging && Math.abs(ddx) > touchSlop && Math.abs(ddx) > Math.abs(ddy)) {
                    dragging = true;
                    pressedItem = -1;
                    if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                }
                if (dragging) {
                    float off = -ddx / slideW;
                    if (currentPage == 0 && off < 0) off *= 0.3f;
                    if (currentPage == items.size() - 1 && off > 0) off *= 0.3f;
                    pageOffset = off;
                    requestLayout();
                    invalidate();
                }
                return true;
            } else if (act == MotionEvent.ACTION_UP || act == MotionEvent.ACTION_CANCEL) {
                if (dragging) {
                    dragging = false;
                    if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
                    settle();
                } else if (act == MotionEvent.ACTION_UP && pressedItem == currentPage) {
                    handleTap(currentPage);
                }
                pressedItem = -1;
                return true;
            }
            return true;
        }

        if (act == MotionEvent.ACTION_DOWN) {
            if (!inImage) return super.onTouchEvent(event);
            pressedItem = itemIndexAt(x, y);
            return true;
        } else if (act == MotionEvent.ACTION_UP) {
            if (inImage && itemIndexAt(x, y) == pressedItem) {
                handleTap(pressedItem);
            }
            final boolean wasPressed = pressedItem != -1 || inImage;
            pressedItem = -1;
            return wasPressed || super.onTouchEvent(event);
        } else if (act == MotionEvent.ACTION_CANCEL) {
            pressedItem = -1;
        }
        return super.onTouchEvent(event);
    }

    private void settle() {
        final int n = items.size();
        int targetDelta = 0;
        if (pageOffset > 0.5f && currentPage < n - 1) targetDelta = 1;
        else if (pageOffset < -0.5f && currentPage > 0) targetDelta = -1;
        final int target = currentPage + targetDelta;
        final float from = pageOffset;
        final float to = target - currentPage;
        settleAnimator = ValueAnimator.ofFloat(from, to);
        settleAnimator.setDuration(220);
        settleAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        settleAnimator.addUpdateListener(a -> {
            pageOffset = (float) a.getAnimatedValue();
            requestLayout();
            invalidate();
        });
        settleAnimator.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator animation) {
                currentPage = target;
                pageOffset = 0;
                requestLayout();
                invalidate();
            }
        });
        settleAnimator.start();
    }

    private int itemIndexAt(float x, float y) {
        for (int i = 0; i < itemRects.size(); i++) {
            if (itemRects.get(i).contains(x, y)) return i;
        }
        return -1;
    }

    private void handleTap(int index) {
        if (delegate == null || currentRow == null) return;
        final List<MediaUploadState> ms = medias();
        if (ms.isEmpty() || (ms.size() == 1 && ms.get(0).state == MediaUploadState.STATE_EMPTY)) {
            delegate.onMediaPick(currentRow);
            return;
        }
        if (index >= 0 && index < ms.size() && ms.get(index).isPending()) {
            delegate.onCancelUpload(currentRow, ms.get(index));
        }
    }

    @Override
    public void fillTextLayoutBlocks(ArrayList<TextSelectionHelper.TextLayoutBlock> out) {
        caption.fillTextLayoutBlocks(out);
    }

    private final Path clipPath = new Path();

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        caption.drawSelection(canvas);
    }

    public static final class Factory extends UItem.UItemFactory<RichMediaCell> {
        static { setup(new Factory()); }

        @Override
        public RichMediaCell createView(Context context, RecyclerListView listView, int currentAccount, int classGuid, Theme.ResourcesProvider resourcesProvider) {
            final RichMediaCell cell = new RichMediaCell(context, resourcesProvider);
            cell.setBackground(new RichEditor.DraggingDrawable(Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider)));
            return cell;
        }

        @Override
        public void bindView(View view, UItem item, boolean divider, UniversalAdapter adapter, UniversalRecyclerView listView) {
            ((RichMediaCell) view).bind((BlockRow) item.object, (Delegate) item.object2);
        }

        public static UItem of(BlockRow row, Delegate delegate) {
            final UItem item = UItem.ofFactory(Factory.class);
            item.id = (int) row.id;
            item.object = row;
            item.object2 = delegate;
            return item;
        }

        @Override
        public boolean equals(UItem a, UItem b) {
            return a.id == b.id;
        }

        @Override
        public boolean contentsEquals(UItem a, UItem b) {
            return a.id == b.id;
        }

        @Override
        public boolean isClickable() {
            return false;
        }
    }
}
