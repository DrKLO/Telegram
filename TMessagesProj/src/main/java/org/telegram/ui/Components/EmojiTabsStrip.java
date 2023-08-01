package org.telegram.ui.Components;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;
import androidx.core.math.MathUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.Premium.PremiumLockIconView;
import org.telegram.ui.Components.Reactions.HwEmojis;
import org.telegram.ui.SelectAnimatedEmojiDialog;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class EmojiTabsStrip extends ScrollableHorizontalScrollView {

    private int recentDrawableId = R.drawable.msg_emoji_recent;
    private static int[] emojiTabsDrawableIds = {
            R.drawable.msg_emoji_smiles,
            R.drawable.msg_emoji_cat,
            R.drawable.msg_emoji_food,
            R.drawable.msg_emoji_activities,
            R.drawable.msg_emoji_travel,
            R.drawable.msg_emoji_objects,
            R.drawable.msg_emoji_other,
            R.drawable.msg_emoji_flags
    };
    private static int[] emojiTabsAnimatedDrawableIds = {
            R.raw.msg_emoji_smiles,
            R.raw.msg_emoji_cat,
            R.raw.msg_emoji_food,
            R.raw.msg_emoji_activities,
            R.raw.msg_emoji_travel,
            R.raw.msg_emoji_objects,
            R.raw.msg_emoji_other,
            R.raw.msg_emoji_flags
    };
    private int settingsDrawableId = R.drawable.smiles_tab_settings;

    private boolean forceTabsShow = !UserConfig.getInstance(UserConfig.selectedAccount).isPremium();
    private boolean showSelected = true;
    private AnimatedFloat showSelectedAlpha;

    private Theme.ResourcesProvider resourcesProvider;
    private boolean includeAnimated;

    public EmojiTabButton toggleEmojiStickersTab;
    public EmojiTabButton recentTab;
    private EmojiTabButton settingsTab;
    private EmojiTabsView emojiTabs;
    private HashMap<View, Rect> removingViews = new HashMap<>();

    private int packsIndexStart;

    private ValueAnimator selectAnimator;
    private float selectT = 0f;
    private float selectAnimationT = 0f;
    private int selected = 0;
    private int selectedFullIndex = 0;
    private int wasIndex = 0;

    public boolean animateAppear = true;

    private Runnable onSettingsOpenRunnable;
    private boolean wasDrawn;
    private int animatedEmojiCacheType = AnimatedEmojiDrawable.CACHE_TYPE_TAB_STRIP;
    private int currentType;
    public boolean updateButtonDrawables = true;

    public EmojiTabsStrip(Context context, Theme.ResourcesProvider resourcesProvider, boolean includeRecent, boolean includeStandard, boolean includeAnimated, int type, Runnable onSettingsOpen) {
        super(context);
        this.includeAnimated = includeAnimated;
        this.resourcesProvider = resourcesProvider;
        this.onSettingsOpenRunnable = onSettingsOpen;
        this.currentType = type;

        contentView = new LinearLayout(context) {

            HashMap<Integer, Integer> lastX = new HashMap<>();

            @Override
            protected void onLayout(boolean changed, int l, int t, int r, int b) {
                int cy = (b - t) / 2;
                if (includeAnimated) {
                    int x = getPaddingLeft() - (!recentIsShown ? AndroidUtilities.dp(30 + 3) : 0);
                    for (int i = 0; i < getChildCount(); ++i) {
                        View child = getChildAt(i);
                        if (child == settingsTab || removingViews.containsKey(child)) {
                            continue;
                        }
                        if (child != null) {
                            child.layout(x, cy - child.getMeasuredHeight() / 2, x + child.getMeasuredWidth(), cy + child.getMeasuredHeight() / 2);
                            Integer id = child instanceof EmojiTabButton ? ((EmojiTabButton) child).id : (child instanceof EmojiTabsView ? ((EmojiTabsView) child).id : null);
                            if (animateAppear && child instanceof EmojiTabButton && ((EmojiTabButton) child).newly) {
                                ((EmojiTabButton) child).newly = false;
                                child.setScaleX(0);
                                child.setScaleY(0);
                                child.setAlpha(0);
                                child.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(HwEmojis.isHwEnabledOrPreparing() ? 0 : 200).setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT).start();
                            }
                            if (id != null) {
                                if (lastX.get(id) != null && lastX.get(id) != x) {
                                    child.setTranslationX(lastX.get(id) - x);
                                    child.animate().translationX(0).setDuration(250).setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT).start();
                                }
                                lastX.put(id, x);
                            }
                            x += child.getMeasuredWidth() + AndroidUtilities.dp(3);
                        }
                    }
                    if (settingsTab != null) {
                        x += (!recentIsShown ? AndroidUtilities.dp(30 + 3) : 0);
                        Integer id = settingsTab.id;
                        if (x + settingsTab.getMeasuredWidth() + getPaddingRight() <= EmojiTabsStrip.this.getMeasuredWidth()) {
                            settingsTab.layout(x = (r - l - getPaddingRight() - settingsTab.getMeasuredWidth()), cy - settingsTab.getMeasuredHeight() / 2, r - l - getPaddingRight(), cy + settingsTab.getMeasuredHeight() / 2);
                        } else {
                            settingsTab.layout(x, cy - settingsTab.getMeasuredHeight() / 2, x + settingsTab.getMeasuredWidth(), cy + settingsTab.getMeasuredHeight() / 2);
                        }
                        if (id != null) {
                            if (lastX.get(id) != null && lastX.get(id) != x) {
                                settingsTab.setTranslationX(lastX.get(id) - x);
                                settingsTab.animate().translationX(0).setDuration(350).start();
                            }
                            lastX.put(id, x);
                        }
                    }
                } else {
                    final int childCount = getChildCount() - (!recentIsShown ? 1 : 0);
                    int margin = (int) ((r - l - getPaddingLeft() - getPaddingRight() - childCount * AndroidUtilities.dp(30)) / (float) Math.max(1, childCount - 1));
                    int x = getPaddingLeft();
                    for (int i = 0; i < childCount; ++i) {
                        View child = getChildAt((!recentIsShown ? 1 : 0) + i);
                        if (child != null) {
                            child.layout(x, cy - child.getMeasuredHeight() / 2, x + child.getMeasuredWidth(), cy + child.getMeasuredHeight() / 2);
                            x += child.getMeasuredWidth() + margin;
                        }
                    }
                }
            }

            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                final int AT_MOST = MeasureSpec.makeMeasureSpec(99999999, MeasureSpec.AT_MOST);
                int width = getPaddingLeft() + getPaddingRight() - (int) (recentIsShown ? 0 : recentTab.getAlpha() * AndroidUtilities.dp(30 + 3));
                for (int i = 0; i < getChildCount(); ++i) {
                    View child = getChildAt(i);
                    if (child != null) {
                        child.measure(AT_MOST, heightMeasureSpec);
                        width += child.getMeasuredWidth() + (i + 1 < getChildCount() ? AndroidUtilities.dp(3) : 0);
                    }
                }
                if (!includeAnimated) {
                    setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.getSize(heightMeasureSpec));
                    return;
                }
                setMeasuredDimension(Math.max(width, MeasureSpec.getSize(widthMeasureSpec)), MeasureSpec.getSize(heightMeasureSpec));
            }

            private Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            private RectF from = new RectF();
            private RectF to = new RectF();
            private RectF rect = new RectF();
            private Path path = new Path();

            @Override
            protected void dispatchDraw(Canvas canvas) {
                for (Map.Entry<View, Rect> entry : removingViews.entrySet()) {
                    View view = entry.getKey();
                    if (view != null) {
                        Rect bounds = entry.getValue();
                        canvas.save();
                        canvas.translate(bounds.left, bounds.top);
                        canvas.scale(view.getScaleX(), view.getScaleY(), bounds.width() / 2f, bounds.height() / 2f);
                        view.draw(canvas);
                        canvas.restore();
                    }
                }

                if (showSelectedAlpha == null) {
                    showSelectedAlpha = new AnimatedFloat(this, 350, CubicBezierInterpolator.EASE_OUT_QUINT);
                }
                float alpha = showSelectedAlpha.set(showSelected ? 1 : 0);

                int selectFrom = (int) Math.floor(selectT), selectTo = (int) Math.ceil(selectT);
                getChildBounds(selectFrom, from);
                getChildBounds(selectTo, to);
                AndroidUtilities.lerp(from, to, selectT - selectFrom, rect);
                float isEmojiTabs = emojiTabs == null ? 0 : MathUtils.clamp(1f - Math.abs(selectT - 1), 0, 1);
                float isMiddle = 4f * selectAnimationT * (1f - selectAnimationT);
                float hw = rect.width() / 2 * (1f + isMiddle * .3f);
                float hh = rect.height() / 2 * (1f - isMiddle * .05f);
                rect.set(rect.centerX() - hw, rect.centerY() - hh, rect.centerX() + hw, rect.centerY() + hh);
                float r = AndroidUtilities.dp(AndroidUtilities.lerp(8f, 16f, isEmojiTabs));
                paint.setColor(selectorColor());
                if (forceTabsShow) {
                    paint.setAlpha((int) (paint.getAlpha() * alpha * (1f - isEmojiTabs * .5f)));
                } else {
                    paint.setAlpha((int) (paint.getAlpha() * alpha));
                }

                path.rewind();
                path.addRoundRect(rect, r, r, Path.Direction.CW);
                canvas.drawPath(path, paint);

                if (forceTabsShow) {
                    path.rewind();
                    getChildBounds(1, rect);
                    path.addRoundRect(rect, AndroidUtilities.dpf2(16), AndroidUtilities.dpf2(16), Path.Direction.CW);
                    paint.setColor(selectorColor());
                    paint.setAlpha((int) (paint.getAlpha() * .5f));
                    canvas.drawPath(path, paint);
                }

                if (emojiTabs != null) {
                    path.addCircle(emojiTabs.getLeft() + AndroidUtilities.dp(15), (emojiTabs.getTop() + emojiTabs.getBottom()) / 2f, AndroidUtilities.dp(15), Path.Direction.CW);
                }

                super.dispatchDraw(canvas);
                wasDrawn = true;
            }

            @Override
            protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
                if (child == emojiTabs) {
                    canvas.save();
                    canvas.clipPath(path);
                    boolean res = super.drawChild(canvas, child, drawingTime);
                    canvas.restore();
                    return res;
                }
                return super.drawChild(canvas, child, drawingTime);
            }

            private void getChildBounds(int i, RectF out) {
                View child = getChildAt(MathUtils.clamp(i, 0, getChildCount() - 1));
                if (child == null) {
                    return;
                }
                out.set(child.getLeft(), child.getTop(), child.getRight(), child.getBottom());
                out.set(
                        out.centerX() - out.width() / 2f * child.getScaleX(),
                        out.centerY() - out.height() / 2f * child.getScaleY(),
                        out.centerX() + out.width() / 2f * child.getScaleX(),
                        out.centerY() + out.height() / 2f * child.getScaleY()
                );
//                out.offset(recentIsShown ? recentTab.getTranslationX() : AndroidUtilities.dp(30 + 3) - recentTab.getTranslationX(), 0);
            }
        };
        contentView.setClipToPadding(false);
        contentView.setOrientation(LinearLayout.HORIZONTAL);
        setVerticalScrollBarEnabled(false);
        setHorizontalScrollBarEnabled(false);
        addView(contentView);

        if (type == SelectAnimatedEmojiDialog.TYPE_AVATAR_CONSTRUCTOR) {
            contentView.addView(toggleEmojiStickersTab = new EmojiTabButton(context, R.drawable.msg_emoji_stickers, false, false));
        }
        if (type == SelectAnimatedEmojiDialog.TYPE_TOPIC_ICON) {
            recentDrawableId = R.drawable.msg_emoji_smiles;
        }
        if (includeRecent) {
            contentView.addView(recentTab = new EmojiTabButton(context, recentDrawableId, false, false));
            recentTab.id = "recent".hashCode();
        }
        if (!includeAnimated) {
            for (int i = 0; i < emojiTabsDrawableIds.length; ++i) {
                contentView.addView(new EmojiTabButton(context, emojiTabsDrawableIds[i], false, i == 0));
            }
            updateClickListeners();
        } else {
            if (includeStandard) {
                contentView.addView(emojiTabs = new EmojiTabsView(context));
                emojiTabs.id = "tabs".hashCode();
            }
            packsIndexStart = contentView.getChildCount();
            if (onSettingsOpen != null) {
                contentView.addView(settingsTab = new EmojiTabButton(context, settingsDrawableId, false, true));
                settingsTab.id = "settings".hashCode();
                settingsTab.setAlpha(0);
            }
            updateClickListeners();
        }
    }

    public void showRecentTabStub(boolean show) {
        if (recentTab == null) {
            return;
        }
        if (show) {
            recentTab.setBackground(new StabDrawable(selectorColor()));
        } else {
            recentTab.setBackground(null);
        }
    }

    public void showSelected(boolean show) {
        this.showSelected = show;
        this.contentView.invalidate();
    }

    private boolean recentFirstChange = true;
    private boolean recentIsShown = true;

    public void showRecent(boolean show) {
        if (recentIsShown == show) {
            return;
        }
        recentIsShown = show;
        if (recentFirstChange) {
            recentTab.setAlpha(show ? 1f : 0f);
        } else {
            recentTab.animate().alpha(show ? 1f : 0f).setDuration(200).setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT).start();
        }
        if (!show && selected == 0 || show && selected == 1) {
            select(0, !recentFirstChange);
        }
        contentView.requestLayout();
        recentFirstChange = false;
    }

    protected boolean doIncludeFeatured() {
        return true;
    }

    private boolean isFreeEmojiPack(TLRPC.StickerSet set, ArrayList<TLRPC.Document> documents) {
        if (set == null || documents == null) {
            return false;
        }
        for (int i = 0; i < documents.size(); ++i) {
            if (!MessageObject.isFreeEmoji(documents.get(i))) {
                return false;
            }
        }
        return true;
    }

    private TLRPC.Document getThumbDocument(TLRPC.StickerSet set, ArrayList<TLRPC.Document> documents) {
        if (set == null) {
            return null;
        }
        if (documents != null) {
            for (int i = 0; i < documents.size(); ++i) {
                TLRPC.Document d = documents.get(i);
                if (d.id == set.thumb_document_id) {
                    return d;
                }
            }
        }
        if (documents != null && documents.size() >= 1) {
            return documents.get(0);
        }
        return null;
    }

    private static class StabDrawable extends Drawable {
        private final Paint paint = new Paint();
        private final RectF rectF = new RectF();

        public StabDrawable(int color){
            paint.setAlpha(45);
            paint.setColor(color);
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            rectF.set(0, 0, AndroidUtilities.dp(30), AndroidUtilities.dp(30));
            canvas.drawRoundRect(rectF, AndroidUtilities.dpf2(8), AndroidUtilities.dpf2(8), paint);
        }

        @Override
        public void setAlpha(int alpha) {
            paint.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(@Nullable ColorFilter colorFilter) {

        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }

    private static class DelayedAnimatedEmojiDrawable extends Drawable {

        int account;
        int cacheType;
        TLRPC.Document document;
        long documentId;
        AnimatedEmojiDrawable drawable;

        int alpha = 0xFF;

        public DelayedAnimatedEmojiDrawable(int account, int cacheType, TLRPC.Document document) {
            this.account = account;
            this.cacheType = cacheType;
            this.document = document;
            if (this.document != null) {
                this.documentId = this.document.id;
            }
        }

        public DelayedAnimatedEmojiDrawable(int account, int cacheType, long documentId) {
            this.account = account;
            this.cacheType = cacheType;
            this.documentId = documentId;
        }

        public void load() {
            if (drawable != null) {
                return;
            }
            if (document != null) {
                drawable = AnimatedEmojiDrawable.make(account, cacheType, document);
            } else {
                drawable = AnimatedEmojiDrawable.make(account, cacheType, documentId);
            }
            if (lastColorFilter != null) {
                drawable.setColorFilter(lastColorFilter);
            }
            drawable.setAlpha(alpha);
            drawable.setCallback(new Callback() {
                @Override
                public void invalidateDrawable(@NonNull Drawable who) {
                    DelayedAnimatedEmojiDrawable.this.invalidateSelf();
                }
                @Override
                public void scheduleDrawable(@NonNull Drawable who, @NonNull Runnable what, long when) {
                    DelayedAnimatedEmojiDrawable.this.scheduleSelf(what, when);
                }
                @Override
                public void unscheduleDrawable(@NonNull Drawable who, @NonNull Runnable what) {
                    DelayedAnimatedEmojiDrawable.this.unscheduleSelf(what);
                }
            });
            if (view != null) {
                view.invalidate();
            }
            invalidateSelf();
        }

        public boolean equals(long documentId) {
            return this.documentId == documentId;
        }

        private View view;
        public void updateView(View view) {
            if (this.view == view) {
                return;
            }

            if (this.view != null && drawable != null) {
                drawable.removeView(this.view);
            }
            this.view = view;
            if (this.view != null && drawable != null) {
                drawable.addView(this.view);
            }
        }

        public void removeView() {
            if (view != null && drawable != null) {
                drawable.removeView(view);
            }
            view = null;
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            if (drawable != null) {
                drawable.setBounds(getBounds());
                drawable.draw(canvas);
            }
        }

        @Override
        public void setAlpha(int alpha) {
            this.alpha = alpha;
            if (drawable != null) {
                drawable.setAlpha(alpha);
            }
        }

        private ColorFilter lastColorFilter;

        @Override
        public void setColorFilter(@Nullable ColorFilter colorFilter) {
            lastColorFilter = colorFilter;
            if (drawable != null) {
                drawable.setColorFilter(lastColorFilter);
            }
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSPARENT;
        }
    }

    protected boolean isInstalled(EmojiView.EmojiPack pack) {
        return pack.installed;
    }

    protected void onTabCreate(EmojiTabButton button) {

    }

    protected boolean allowEmojisForNonPremium() {
        return false;
    }

    boolean first = true;
    private ValueAnimator appearAnimation;
    private int appearCount;

    public void updateEmojiPacks(ArrayList<EmojiView.EmojiPack> emojiPacks) {
        if (!includeAnimated) {
            return;
        }
        if (first && !MediaDataController.getInstance(UserConfig.selectedAccount).areStickersLoaded(MediaDataController.TYPE_EMOJIPACKS)) {
            return;
        }
        first = false;
        if (emojiPacks == null) {
            return;
        }
        int childCount = contentView.getChildCount() - packsIndexStart - (settingsTab != null ? 1 : 0);
        boolean first = childCount == 0 && emojiPacks.size() > 0 && appearCount != emojiPacks.size() && wasDrawn;
        boolean doAppearAnimation = false; // emojipackTabs.size() == 0 && emojiPacks.size() > 0 && appearCount != emojiPacks.size() && wasDrawn;
        if (appearAnimation != null && appearCount != emojiPacks.size()) {
            appearAnimation.cancel();
            appearAnimation = null;
        }
        appearCount = emojiPacks.size();
        final boolean includeFeatured = doIncludeFeatured();
        final boolean isPremium = UserConfig.getInstance(UserConfig.selectedAccount).isPremium() || allowEmojisForNonPremium();

        ArrayList<EmojiTabButton> attachedEmojiPacks = new ArrayList<>();

        for (int i = 0; i < Math.max(emojiPacks.size(), childCount); ++i) {
            EmojiTabButton currentPackButton = null;
            if (i < childCount) {
                currentPackButton = (EmojiTabButton) contentView.getChildAt(i + packsIndexStart);
            }
            EmojiView.EmojiPack newPack = null;
            if (i < emojiPacks.size()) {
                newPack = emojiPacks.get(i);
            }

            if (newPack == null) {
                if (currentPackButton != null) {
                    contentView.removeView(currentPackButton);
                }
            } else if (newPack.resId != 0) {
                if (currentPackButton == null) {
                    currentPackButton = new EmojiTabButton(getContext(), newPack.resId, false, false);
                    onTabCreate(currentPackButton);
                    contentView.addView(currentPackButton, packsIndexStart + i);
                } else {
                    currentPackButton.setDrawable(getResources().getDrawable(newPack.resId).mutate());
                    currentPackButton.updateColor();
                    currentPackButton.setLock(null);
                }
            } else {
                final boolean free = newPack.free; // isFreeEmojiPack(newPack.set, newPack.documents);
                DelayedAnimatedEmojiDrawable drawable = currentPackButton == null ? null : (DelayedAnimatedEmojiDrawable) currentPackButton.getDrawable();
                TLRPC.Document thumbDocument = getThumbDocument(newPack.set, newPack.documents);
                if (thumbDocument != null && (drawable == null || !drawable.equals(thumbDocument.id))) {
                    drawable = new DelayedAnimatedEmojiDrawable(UserConfig.selectedAccount, animatedEmojiCacheType, thumbDocument);
                }
                if (currentPackButton == null) {
                    currentPackButton = new EmojiTabButton(getContext(), drawable, free, false, false);
                    currentPackButton.setDrawable(drawable);
                    onTabCreate(currentPackButton);
                    contentView.addView(currentPackButton, packsIndexStart + i);
                } else if (currentPackButton.getDrawable() != drawable) {
                    currentPackButton.setDrawable(drawable);
                }
                if (currentType == SelectAnimatedEmojiDialog.TYPE_AVATAR_CONSTRUCTOR) {
                    currentPackButton.setLock(null);
                } else if (!isPremium && !free) {
                    currentPackButton.setLock(true);
                } else if (!this.isInstalled(newPack)) {
                    currentPackButton.setLock(false);
                } else {
                    currentPackButton.setLock(null);
                }
                if (doAppearAnimation && !first) {
                    currentPackButton.newly = false;
                }
            }
        }

//        for (int i = 0; i < emojipackTabs.size(); ++i) {
//            EmojiTabButton emojipackTab = emojipackTabs.get(i);
//            EmojiView.EmojiPack pack = null;
//            if (emojipackTab != null && emojipackTab.id != null) {
//                for (int j = 0; j < emojiPacks.size(); ++j) {
//                    EmojiView.EmojiPack p = emojiPacks.get(j);
//                    if (!includeFeatured && p.featured) {
//                        continue;
//                    }
//                    final int id = Objects.hash(p.set.id, p.featured);
//                    if (id == emojipackTab.id) {
//                        pack = p;
//                        break;
//                    }
//                }
//            }
//
//            if (pack == null && emojipackTab != null) {
//                Rect bounds = new Rect();
//                bounds.set(emojipackTab.getLeft(), emojipackTab.getTop(), emojipackTab.getRight(), emojipackTab.getBottom());
//                removingViews.put(emojipackTab, bounds);
//                ValueAnimator anm = ValueAnimator.ofFloat(emojipackTab.getAlpha(), 0f);
//                anm.addUpdateListener(a -> {
//                    float alpha = (float) a.getAnimatedValue();
//                    emojipackTab.setAlpha(alpha);
//                    emojipackTab.setScaleX(alpha);
//                    emojipackTab.setScaleY(alpha);
//                    contentView.invalidate();
//                });
//                anm.addListener(new AnimatorListenerAdapter() {
//                    @Override
//                    public void onAnimationEnd(Animator animation) {
//                        removingViews.remove(emojipackTab);
//                        contentView.invalidate();
//                    }
//                });
//                anm.setDuration(200);
//                anm.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
//                anm.start();
//                emojipackTabs.remove(i--);
//            }
//            if (emojipackTab != null) {
//                emojipackTab.keepAttached = true;
//                attachedEmojiPacks.add(emojipackTab);
//            }
//            contentView.removeView(emojipackTab);
//        }
//        for (int i = 0; i < emojiPacks.size(); ++i) {
//            EmojiView.EmojiPack pack = emojiPacks.get(i);
//            if (!includeFeatured && pack.featured) {
//                continue;
//            }
//            final int id = Objects.hash(pack.set.id, pack.featured);
//            EmojiTabButton emojipackTab = null;
//            for (int j = 0; j < emojipackTabs.size(); ++j) {
//                EmojiTabButton tab = emojipackTabs.get(j);
//                if (tab != null && tab.id != null && tab.id == id) {
//                    emojipackTab = tab;
//                    break;
//                }
//            }
//            final boolean free = isFreeEmojiPack(pack.set, pack.documents);
//            DelayedAnimatedEmojiDrawable drawable = emojipackTab == null ? null : (DelayedAnimatedEmojiDrawable) emojipackTab.getDrawable();
//            TLRPC.Document thumbDocument = getThumbDocument(pack.set, pack.documents);
//            if (thumbDocument != null && (drawable == null || !drawable.equals(thumbDocument.id))) {
//                drawable = new DelayedAnimatedEmojiDrawable(UserConfig.selectedAccount, animatedEmojiCacheType, thumbDocument);
//            }
//            if (emojipackTab == null) {
//                emojipackTab = new EmojiTabButton(getContext(), drawable, free, false, false);
//                emojipackTab.id = id;
//                emojipackTab.setDrawable(drawable);
//                onTabCreate(emojipackTab);
//                emojipackTabs.add(emojipackTab);
//            } else if (emojipackTab.getDrawable() != drawable) {
//                emojipackTab.setDrawable(drawable);
//            }
//            if (currentType == SelectAnimatedEmojiDialog.TYPE_AVATAR_CONSTRUCTOR) {
//                emojipackTab.setLock(null);
//            } else if (!isPremium && !free) {
//                emojipackTab.setLock(true);
//            } else if (!this.isInstalled(pack)) {
//                emojipackTab.setLock(false);
//            } else {
//                emojipackTab.setLock(null);
//            }
//            if (doAppearAnimation && !first) {
//                emojipackTab.newly = false;
//            }
//            if (emojipackTab.getParent() instanceof ViewGroup) {
//                ((ViewGroup) emojipackTab.getParent()).removeView(emojipackTab);
//            }
//            contentView.addView(emojipackTab);
//        }
        if (settingsTab != null) {
            settingsTab.bringToFront();
            if (settingsTab.getAlpha() < 1) {
                settingsTab.animate().alpha(1f).setDuration(HwEmojis.isHwEnabledOrPreparing() ? 0 : 200).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
            }
        }
//        if (doAppearAnimation) {
//            if (emojipackTabs != null) {
//                for (int i = 0; i < emojipackTabs.size(); ++i) {
//                    emojipackTabs.get(i).setScaleX(0);
//                    emojipackTabs.get(i).setScaleY(0);
//                }
//            }
//            appearAnimation = ValueAnimator.ofFloat(0, 1);
//            final OvershootInterpolator innerInterpolator = new OvershootInterpolator(3f);
//            appearAnimation.addUpdateListener(anm -> {
//                if (emojipackTabs == null) {
//                    return;
//                }
//                final float t = (float) anm.getAnimatedValue();
//                final int count = emojipackTabs.size();
//                final float dur = 1f / count * 4.5f;
//                for (int i = 0; i < count; ++i) {
//                    final float off = i / (float) count * (1f - dur);
//                    final float T = MathUtils.clamp((t - off) / dur, 0, 1);
//                    final float scale = innerInterpolator.getInterpolation(T);
//                    emojipackTabs.get(i).setScaleX(scale);
//                    emojipackTabs.get(i).setScaleY(scale);
//                }
//            });
//            appearAnimation.addListener(new AnimatorListenerAdapter() {
//                @Override
//                public void onAnimationCancel(Animator animation) {
//                    if (emojipackTabs == null) {
//                        return;
//                    }
//                    for (int i = 0; i < emojipackTabs.size(); ++i) {
//                        emojipackTabs.get(i).setScaleX(1);
//                        emojipackTabs.get(i).setScaleY(1);
//                    }
//                }
//            });
//            appearAnimation.setStartDelay(150);
//            appearAnimation.setDuration((emojipackTabs == null ? 0 : emojipackTabs.size()) * 75L);
//            appearAnimation.setInterpolator(CubicBezierInterpolator.EASE_OUT);
//            appearAnimation.start();
//        }
        for (int i = 0; i < attachedEmojiPacks.size(); i++) {
            attachedEmojiPacks.get(i).keepAttached = false;
            attachedEmojiPacks.get(i).updateAttachState();
        }
        updateClickListeners();
    }

    public void updateClickListeners() {
        for (int i = 0, j = 0; i < contentView.getChildCount(); ++i, ++j) {
            View child = contentView.getChildAt(i);
            if (child instanceof EmojiTabsView) {
                EmojiTabsView tabsView = (EmojiTabsView) child;
                for (int a = 0; a < tabsView.contentView.getChildCount(); ++a, ++j) {
                    final int index = j;
                    tabsView.contentView.getChildAt(a).setOnClickListener(e -> {
                        onTabClick(index);
                    });
                }
                --j;
            } else if (child != null) {
                final int index = j;
                child.setOnClickListener(e -> {
                    onTabClick(index);
                });
            }
        }
        if (settingsTab != null) {
            settingsTab.setOnClickListener(e -> {
                if (onSettingsOpenRunnable != null) {
                    onSettingsOpenRunnable.run();
                }
            });
        }
    }

    protected boolean onTabClick(int index) {
        return true;
    }

    private float paddingLeftDp = 5 + 6;

    public void setPaddingLeft(float paddingLeftDp) {
        this.paddingLeftDp = paddingLeftDp;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        contentView.setPadding(AndroidUtilities.dp(paddingLeftDp), 0, AndroidUtilities.dp(5 + 6), 0);
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    public void updateColors() {
        if (recentTab != null) {
            recentTab.updateColor();
        }
    }

    public void select(int index) {
        select(index, true);
    }

    public void select(int index, boolean animated) {
        animated = animated && !first;
        if (toggleEmojiStickersTab != null) {
            index++;
        }
        if (!recentIsShown || toggleEmojiStickersTab != null) {
            index = Math.max(1, index);
        }
        selectedFullIndex = index;
        final int wasSelected = selected;
        for (int i = 0, j = 0; i < contentView.getChildCount(); ++i, ++j) {
            View child = contentView.getChildAt(i);
            int from = j;
            if (child instanceof EmojiTabsView) {
                EmojiTabsView tabs = (EmojiTabsView) child;
                for (int a = 0; a < tabs.contentView.getChildCount(); ++a, ++j) {
                    View child2 = tabs.contentView.getChildAt(a);
                    if (child2 instanceof EmojiTabButton) {
                        ((EmojiTabButton) child2).updateSelect(index == j, animated);
                    }
                }
                --j;
            } else if (child instanceof EmojiTabButton) {
                ((EmojiTabButton) child).updateSelect(index == j, animated);
            }
            if (index >= from && index <= j) {
                selected = i;
            }
        }
        if (wasSelected != selected) {
            if (selectAnimator != null) {
                selectAnimator.cancel();
            }
            final float from = selectT, to = selected;
            if (animated) {
                selectAnimator = ValueAnimator.ofFloat(0, 1);
                selectAnimator.addUpdateListener(a -> {
                    selectAnimationT = (float) a.getAnimatedValue();
                    selectT = AndroidUtilities.lerp(from, to, selectAnimationT);
                    contentView.invalidate();
                });
                selectAnimator.setDuration(350);
                selectAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
                selectAnimator.start();
            } else {
                selectAnimationT = 1f;
                selectT = AndroidUtilities.lerp(from, to, selectAnimationT);
                contentView.invalidate();
            }

            if (emojiTabs != null) {
                emojiTabs.show(selected == 1 || forceTabsShow, animated);
            }

            View child = contentView.getChildAt(selected);
            if (selected >= 2) {
                scrollToVisible(child.getLeft(), child.getRight());
            } else {
                scrollTo(0);
            }
        }

        if (wasIndex != index) {
            if (emojiTabs != null && selected == 1 && index >= 1 && index <= 1 + emojiTabs.contentView.getChildCount()) {
                emojiTabs.scrollToVisible(AndroidUtilities.dp(36 * (index - 1) - 6), AndroidUtilities.dp(36 * (index - 1) - 6 + 30));
            }
            wasIndex = index;
        }
    }

    protected ColorFilter getEmojiColorFilter() {
        return Theme.getAnimatedEmojiColorFilter(resourcesProvider);
    }

    private int selectorColor() {
        return Theme.multAlpha(Theme.getColor(Theme.key_chat_emojiPanelIcon, resourcesProvider), .18f);
    }

    public void setAnimatedEmojiCacheType(int cacheType) {
        this.animatedEmojiCacheType = cacheType;
    }

    public class EmojiTabButton extends ViewGroup {
        public boolean shown = true;

        public Integer id;
        public boolean newly;
        public boolean keepAttached;
        private boolean isAnimatedEmoji;

        private ImageView imageView;
        private RLottieDrawable lottieDrawable;
        private PremiumLockIconView lockView;
        private boolean round, forceSelector;
        DelayedAnimatedEmojiDrawable animatedEmoji;
        boolean attached;

        public EmojiTabButton(Context context, int drawableId, int lottieId, boolean roundSelector, boolean forceSelector) {
            super(context);
            this.round = roundSelector;
            this.forceSelector = forceSelector;
            if (round) {
                setBackground(Theme.createCircleSelectorDrawable(selectorColor(), 0, 0));
            } else if (forceSelector) {
                setBackground(Theme.createRadSelectorDrawable(selectorColor(), 8, 8));
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                lottieDrawable = new RLottieDrawable(lottieId, "" + lottieId, AndroidUtilities.dp(24), AndroidUtilities.dp(24), false, null);
                lottieDrawable.setBounds(AndroidUtilities.dp(3), AndroidUtilities.dp(3), AndroidUtilities.dp(27), AndroidUtilities.dp(27));
                lottieDrawable.setMasterParent(this);
                lottieDrawable.setAllowDecodeSingleFrame(true);
                lottieDrawable.start();
            } else {
                imageView = new ImageView(context);
                imageView.setImageDrawable(context.getResources().getDrawable(drawableId).mutate());
                addView(imageView);
            }
            setColor(Theme.getColor(Theme.key_chat_emojiPanelIcon, resourcesProvider));
        }

        public EmojiTabButton(Context context, int drawableId, boolean roundSelector, boolean forceSelector) {
            super(context);
            this.round = roundSelector;
            this.forceSelector = forceSelector;
            if (round) {
                setBackground(Theme.createCircleSelectorDrawable(selectorColor(), 0, 0));
            } else if (forceSelector) {
                setBackground(Theme.createRadSelectorDrawable(selectorColor(), 8, 8));
            }

            imageView = new ImageView(context);
            imageView.setImageDrawable(context.getResources().getDrawable(drawableId).mutate());
            setColor(Theme.getColor(Theme.key_chat_emojiPanelIcon, resourcesProvider));

            addView(imageView);
        }

        public EmojiTabButton(Context context, Drawable drawable, boolean free, boolean roundSelector, boolean forceSelector) {
            super(context);
            this.newly = true;
            this.round = roundSelector;
            this.forceSelector = forceSelector;
            if (round) {
                setBackground(Theme.createCircleSelectorDrawable(selectorColor(), 0, 0));
            } else if (forceSelector) {
                setBackground(Theme.createRadSelectorDrawable(selectorColor(), 8, 8));
            }

            imageView = new ImageView(context) {
                @Override
                public void invalidate() {
                    if (HwEmojis.grab(this)) {
                        return;
                    }
                    super.invalidate();
                    updateLockImageReceiver();
                }

                @Override
                public void invalidate(int l, int t, int r, int b) {
                    if (HwEmojis.grab(this)) {
                        return;
                    }
                    super.invalidate(l, t, r, b);
                }

                @Override
                protected void onDraw(Canvas canvas) {

                }

                @Override
                protected void dispatchDraw(Canvas canvas) {
                    Drawable drawable = getDrawable();
                    if (drawable != null) {
                        drawable.setBounds(0, 0, getMeasuredWidth(), getMeasuredHeight());
                        drawable.setAlpha(255);
                        drawable.draw(canvas);
                    }
                }

                @Override
                public void setImageDrawable(@Nullable Drawable drawable) {
                    super.setImageDrawable(drawable);
                }
            };
            imageView.setImageDrawable(drawable);
            if (drawable instanceof AnimatedEmojiDrawable || drawable instanceof DelayedAnimatedEmojiDrawable) {
                isAnimatedEmoji = true;
                imageView.setColorFilter(getEmojiColorFilter());
            }
            addView(imageView);

            lockView = new PremiumLockIconView(context, PremiumLockIconView.TYPE_STICKERS_PREMIUM_LOCKED, resourcesProvider) {
                @Override
                public void invalidate() {
                    if (HwEmojis.grab(this)) {
                        return;
                    }
                    super.invalidate();
                }

                @Override
                public void invalidate(int l, int t, int r, int b) {
                    if (HwEmojis.grab(this)) {
                        return;
                    }
                    super.invalidate(l, t, r, b);
                }
            };
            lockView.setAlpha(0f);
            lockView.setScaleX(0);
            lockView.setScaleY(0);
            updateLockImageReceiver();
            addView(lockView);

            setColor(Theme.getColor(Theme.key_chat_emojiPanelIcon, resourcesProvider));
        }

        @Override
        public void invalidate() {
            if (HwEmojis.grab(this)) {
                return;
            }
            super.invalidate();
        }

        @Override
        public void invalidate(int l, int t, int r, int b) {
            if (HwEmojis.grab(this)) {
                return;
            }
            super.invalidate(l, t, r, b);
        }

        @Override
        protected void dispatchDraw(Canvas canvas) {
            super.dispatchDraw(canvas);
            if (lottieDrawable != null && wasVisible) {
                lottieDrawable.draw(canvas);
            }
        }

        @Override
        protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
            if (!wasVisible) {
                return true;
            }
            return super.drawChild(canvas, child, drawingTime);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (!wasVisible) {
                return;
            }
            super.onDraw(canvas);
        }

        @Override
        public boolean performClick() {
            playAnimation();
            return super.performClick();
        }

        private void playAnimation() {
            if (animatedEmoji != null && animatedEmoji.drawable != null) {
                ImageReceiver imageReceiver = animatedEmoji.drawable.getImageReceiver();
                if (imageReceiver != null) {
                    if (imageReceiver.getAnimation() != null) {
                        imageReceiver.getAnimation().seekTo(0, true);
                    }
                    imageReceiver.startAnimation();
                }
            }
        }

        private void stopAnimation() {
            if (animatedEmoji != null && animatedEmoji.drawable != null) {
                ImageReceiver imageReceiver = animatedEmoji.drawable.getImageReceiver();
                if (imageReceiver != null) {
                    if (imageReceiver.getLottieAnimation() != null) {
                        imageReceiver.getLottieAnimation().setCurrentFrame(0);
                        imageReceiver.getLottieAnimation().stop();
                    } else if (imageReceiver.getAnimation() != null) {
                        imageReceiver.getAnimation().stop();
                    }
                }
            }
        }

        private boolean wasVisible;

        public void updateVisibilityInbounds(boolean visible, boolean ignore) {
            if (!wasVisible && visible) {
                if (lottieDrawable != null && !lottieDrawable.isRunning() && !ignore) {
                    lottieDrawable.setProgress(0);
                    lottieDrawable.start();
                }
            }
            if (wasVisible != visible) {
                wasVisible = visible;
                if (visible) {
                    invalidate();
                    if (lockView != null) {
                        lockView.invalidate();
                    }
                    if (imageView != null && imageView.getDrawable() instanceof DelayedAnimatedEmojiDrawable) {
                        ((DelayedAnimatedEmojiDrawable) imageView.getDrawable()).load();
                    }
                    initLock();
                    if (imageView != null) {
                        imageView.invalidate();
                    }
                } else {
                    stopAnimation();
                }
                updateAttachState();
            }
        }

        private void initLock() {
            if (lockView != null && animatedEmoji != null && animatedEmoji.drawable != null) {
                ImageReceiver imageReceiver = animatedEmoji.drawable.getImageReceiver();
                if (imageReceiver != null) {
                    lockView.setImageReceiver(imageReceiver);
                }
            }
        }

        public void setLock(Boolean lock) {
            if (lockView == null) {
                return;
            }
            if (lock == null) {
                updateLock(false);
            } else {
                updateLock(true);
                if (lock) {
                    lockView.setImageResource(R.drawable.msg_mini_lockedemoji);
                } else {
                    Drawable addIcon = getResources().getDrawable(R.drawable.msg_mini_addemoji).mutate();
                    addIcon.setColorFilter(new PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.MULTIPLY));
                    lockView.setImageDrawable(addIcon);
                }
            }
        }

        private float lockT;
        private ValueAnimator lockAnimator;

        private void updateLock(boolean enable) {
            if (lockAnimator != null) {
                lockAnimator.cancel();
            }
            if (Math.abs(lockT - (enable ? 1f : 0f)) < 0.01f) {
                return;
            }
            lockView.setVisibility(View.VISIBLE);
            lockAnimator = ValueAnimator.ofFloat(lockT, enable ? 1f : 0f);
            lockAnimator.addUpdateListener(anm -> {
                lockT = (float) anm.getAnimatedValue();
                lockView.setScaleX(lockT);
                lockView.setScaleY(lockT);
                lockView.setAlpha(lockT);
            });
            lockAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (!enable) {
                        lockView.setVisibility(View.GONE);
                    }
                }
            });
            lockAnimator.setInterpolator(CubicBezierInterpolator.DEFAULT);
            lockAnimator.setDuration(HwEmojis.isHwEnabledOrPreparing() ? 0 : 200);
            lockAnimator.start();
        }

        public void updateLockImageReceiver() {
            if (lockView != null && !lockView.ready() && getDrawable() instanceof AnimatedEmojiDrawable) {
                ImageReceiver imageReceiver = ((AnimatedEmojiDrawable) getDrawable()).getImageReceiver();
                if (imageReceiver != null) {
                    lockView.setImageReceiver(imageReceiver);
                    lockView.invalidate();
                }
            }
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            setMeasuredDimension(AndroidUtilities.dp(30), AndroidUtilities.dp(30));
            if (imageView != null) {
                imageView.measure(
                        MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(24), MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(24), MeasureSpec.EXACTLY)
                );
            }
            if (lockView != null) {
                lockView.measure(
                        MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(12), MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(12), MeasureSpec.EXACTLY)
                );
            }
        }

        @Override
        protected void onLayout(boolean changed, int l, int t, int r, int b) {
            if (imageView != null) {
                int cx = (r - l) / 2, cy = (b - t) / 2;
                imageView.layout(cx - imageView.getMeasuredWidth() / 2, cy - imageView.getMeasuredHeight() / 2, cx + imageView.getMeasuredWidth() / 2, cy + imageView.getMeasuredHeight() / 2);
            }
            if (lockView != null) {
                lockView.layout(r - l - lockView.getMeasuredWidth(), b - t - lockView.getMeasuredHeight(), r - l, b - t);
            }
        }

        public Drawable getDrawable() {
            return imageView != null ? imageView.getDrawable() : null;
        }

        public void setDrawable(Drawable drawable) {
            DelayedAnimatedEmojiDrawable newEmoji = null;
            if (drawable instanceof DelayedAnimatedEmojiDrawable) {
                newEmoji = (DelayedAnimatedEmojiDrawable) drawable;
            }
            if (animatedEmoji != newEmoji) {
                if (animatedEmoji != null && attached && wasVisible) {
                    animatedEmoji.removeView();
                }
                animatedEmoji = newEmoji;
                if (animatedEmoji != null && attached && wasVisible) {
                    animatedEmoji.updateView(imageView);
                }
                if (wasVisible && animatedEmoji != null) {
                    animatedEmoji.load();
                }
                initLock();
            }
            if (imageView != null) {
                imageView.setImageDrawable(drawable);
            }
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            attached = true;
            updateAttachState();
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            attached = false;
            updateAttachState();
        }

        private void updateAttachState() {
            if (animatedEmoji != null) {
                if ((keepAttached || attached) && wasVisible) {
                    animatedEmoji.updateView(imageView);
                } else {
                    animatedEmoji.removeView();
                }
            }
        }

        private float selectT;
        private boolean selected;
        private ValueAnimator selectAnimator;

        public void updateSelect(boolean selected, boolean animated) {
            if (imageView != null && imageView.getDrawable() == null) {
                return;
            }
            if (this.selected == selected) {
                return;
            }
            this.selected = selected;
            if (selectAnimator != null) {
                selectAnimator.cancel();
                selectAnimator = null;
            }

            if (!selected) {
                stopAnimation();
            }

            if (animated) {
                selectAnimator = ValueAnimator.ofFloat(selectT, selected ? 1f : 0f);
                selectAnimator.addUpdateListener(a -> {
                    selectT = (float) a.getAnimatedValue();
                    setColor(ColorUtils.blendARGB(Theme.getColor(Theme.key_chat_emojiPanelIcon, resourcesProvider), Theme.getColor(Theme.key_chat_emojiPanelIconSelected, resourcesProvider), selectT));
                });
                selectAnimator.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if (updateButtonDrawables && !round) {
                            if (selected || forceSelector) {
                                if (getBackground() == null) {
                                    setBackground(Theme.createRadSelectorDrawable(selectorColor(), 8, 8));
                                }
                            } else {
                                setBackground(null);
                            }
                        }
                    }
                });
                selectAnimator.setDuration(HwEmojis.isHwEnabledOrPreparing() ? 0 : 350);
                selectAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
                selectAnimator.start();
            } else {
                selectT = selected ? 1f : 0f;
                updateColor();
            }
        }

        public void updateColor() {
            Theme.setSelectorDrawableColor(getBackground(), selectorColor(), false);
            setColor(
                    ColorUtils.blendARGB(
                            Theme.getColor(Theme.key_chat_emojiPanelIcon, resourcesProvider),
                            Theme.getColor(Theme.key_chat_emojiPanelIconSelected, resourcesProvider),
                            selectT
                    )
            );
        }

        private void setColor(int color) {
            PorterDuffColorFilter colorFilter = new PorterDuffColorFilter(color, PorterDuff.Mode.MULTIPLY);
            if (imageView != null && !isAnimatedEmoji) {
                imageView.setColorFilter(colorFilter);
                imageView.invalidate();
            }
            if (lottieDrawable != null) {
                lottieDrawable.setColorFilter(colorFilter);
                invalidate();
            }
        }
    }

    private class EmojiTabsView extends ScrollableHorizontalScrollView {
        public int id;

        public EmojiTabsView(Context context) {
            super(context);
            setSmoothScrollingEnabled(true);
            setHorizontalScrollBarEnabled(false);
            setVerticalScrollBarEnabled(false);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                setNestedScrollingEnabled(true);
            }
            contentView = new LinearLayout(context) {
                @Override
                protected void onLayout(boolean changed, int l, int t, int r, int b) {
                    int x = getPaddingLeft(), cy = (b - t) / 2;
                    for (int i = 0; i < getChildCount(); ++i) {
                        View child = getChildAt(i);
                        if (child == settingsTab) {
                            continue;
                        }
                        if (child != null) {
                            child.layout(x, cy - child.getMeasuredHeight() / 2, x + child.getMeasuredWidth(), cy + child.getMeasuredHeight() / 2);
                            x += child.getMeasuredWidth() + AndroidUtilities.dp(2);
                        }
                    }
                }

                @Override
                protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                    super.onMeasure(
                            Math.max(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp((30 + 2) * contentView.getChildCount()), MeasureSpec.EXACTLY)),
                            heightMeasureSpec
                    );
                }
            };
            contentView.setOrientation(LinearLayout.HORIZONTAL);
            addView(contentView, new LayoutParams(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT));

            for (int i = 0; i < emojiTabsDrawableIds.length; ++i) {
                contentView.addView(new EmojiTabButton(context, emojiTabsDrawableIds[i], emojiTabsAnimatedDrawableIds[i], true, false) {
                    @Override
                    public boolean onTouchEvent(MotionEvent ev) {
                        intercept(ev);
                        return super.onTouchEvent(ev);
                    }
                });
            }
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(
                    MeasureSpec.makeMeasureSpec(AndroidUtilities.lerp(AndroidUtilities.dp(30), maxWidth(), showT), MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(30), MeasureSpec.EXACTLY)
            );
        }

        public int maxWidth() {
//            return AndroidUtilities.dp((30 + 2) * (clip() ? Math.min(5.7f, contentView.getChildCount()) : contentView.getChildCount()));
            return AndroidUtilities.dp((30 + 2) * Math.min(5.7f, contentView.getChildCount()));
        }

        private void intercept(MotionEvent ev) {
            if (shown && !scrollingAnimation) {
                switch (ev.getAction()) {
                    case MotionEvent.ACTION_UP:
                        touching = false;
                        break;
                    case MotionEvent.ACTION_DOWN:
                    case MotionEvent.ACTION_MOVE:
                        touching = true;
                        if (!scrollingAnimation) {
                            resetScrollTo();
                        }
                        EmojiTabsStrip.this.requestDisallowInterceptTouchEvent(true);
                        break;
                }
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent ev) {
            intercept(ev);
            return super.onTouchEvent(ev);
        }

        private boolean shown = forceTabsShow;
        private float showT = forceTabsShow ? 1f : 0f;

        public void show(boolean show, boolean animated) {
            if (show == shown) {
                return;
            }
            shown = show;
            if (!show) {
                scrollTo(0);
            }

            if (showAnimator != null) {
                showAnimator.cancel();
            }
            if (animated) {
                showAnimator = ValueAnimator.ofFloat(showT, show ? 1f : 0f);
                showAnimator.addUpdateListener(a -> {
                    showT = (float) a.getAnimatedValue();
                    invalidate();
                    requestLayout();
                    updateButtonsVisibility();
                    EmojiTabsStrip.this.contentView.invalidate();
                });
                showAnimator.setDuration(475);
                showAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
                showAnimator.start();
            } else {
                showT = show ? 1f : 0f;
                invalidate();
                requestLayout();
                updateButtonsVisibility();
                EmojiTabsStrip.this.contentView.invalidate();
            }
        }
    }
}

class ScrollableHorizontalScrollView extends HorizontalScrollView {

    boolean touching;
    public LinearLayout contentView;
    ValueAnimator showAnimator;

    public ScrollableHorizontalScrollView(Context context) {
        super(context);
    }

    protected boolean scrollingAnimation;

    public boolean isScrolling() {
        return scrollingAnimation;
    }

    public boolean scrollToVisible(int left, int right) {
        if (getChildCount() <= 0) {
            return false;
        }
        final int padding = AndroidUtilities.dp(50);
        int to;
        if (left < getScrollX() + padding) {
            to = left - padding;
        } else if (right > getScrollX() + (getMeasuredWidth() - padding)) {
            to = right - getMeasuredWidth() + padding;
        } else {
            return false;
        }

        scrollTo(MathUtils.clamp(to, 0, getChildAt(0).getMeasuredWidth() - getMeasuredWidth()));
        return true;
    }

    private int scrollingTo = -1;
    private ValueAnimator scrollAnimator;

    public void scrollTo(int x) {
        if (scrollingTo == x) {
            return;
        }
        scrollingTo = x;
        if (scrollAnimator != null) {
            scrollAnimator.cancel();
        }
        if (this.getScrollX() == x) {
            return;
        }
        scrollAnimator = ValueAnimator.ofFloat(this.getScrollX(), x);
        scrollAnimator.addUpdateListener(a -> this.setScrollX((int) (float) a.getAnimatedValue()));
        scrollAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        scrollAnimator.setDuration(250);
        scrollAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                scrollingAnimation = false;
            }

            @Override
            public void onAnimationStart(Animator animation) {
                scrollingAnimation = true;
                if (getParent() instanceof HorizontalScrollView) {
                    ((HorizontalScrollView) getParent()).requestDisallowInterceptTouchEvent(false);
                }
            }
        });
        scrollAnimator.start();
    }

    public void resetScrollTo() {
        scrollingTo = -1;
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        updateButtonsVisibility();
    }

    @Override
    protected void onScrollChanged(int l, int t, int oldl, int oldt) {
        super.onScrollChanged(l, t, oldl, oldt);
        if (Math.abs(t - oldt) < 2 || t >= getMeasuredHeight() || t == 0) {
            if (!touching) {
                requestDisallowInterceptTouchEvent(false);
            }
        }
        updateButtonsVisibility();
    }

    void updateButtonsVisibility() {
        final int count = contentView.getChildCount();
        for (int i = 0; i < count; ++i) {
            View child = contentView.getChildAt(i);
            if (child instanceof EmojiTabsStrip.EmojiTabButton) {
                ((EmojiTabsStrip.EmojiTabButton) child).updateVisibilityInbounds(child.getRight() - getScrollX() > 0 && child.getLeft() - getScrollX() < getMeasuredWidth(), scrollingAnimation && !(showAnimator != null && showAnimator.isRunning()));
            }
        }
    }

    private boolean touch;

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            touch = true;
        } else if (ev.getAction() == MotionEvent.ACTION_UP || ev.getAction() == MotionEvent.ACTION_CANCEL) {
            touch = false;
        }
        return super.onTouchEvent(ev);
    }

    public boolean isTouch() {
        return touch;
    }
}
