package org.telegram.ui.Cells;

import static org.telegram.ui.ProfileActivity.COLLAPSE_HEIGHT_DP;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BlurringShader;
import org.telegram.ui.Components.FallbackBlurManager2;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.PaddingItemDecoration;
import org.telegram.ui.Components.RLottieDrawable;
import org.telegram.ui.Components.RLottieImageView;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.SpanningLinearLayoutManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

import org.telegram.messenger.R;
import org.telegram.ui.ProfileActivity;

public class ShortcutsCell extends FrameLayout {

    public enum ButtonType {
        STOP(R.string.Stop, R.drawable.profile_block, 0, 0, 0),
        CALL(R.string.Call, R.drawable.profile_call, 0, 0, 0),
        GIFT(R.string.ActionStarGift, R.drawable.profile_gift, 0, 0, 0),
        JOIN(R.string.VoipChatJoin, R.drawable.profile_join, 0, 0, 0),
        LEAVE(R.string.VoipGroupLeave, R.drawable.profile_leave, 0, 0, 0),
        LIVE_STREAM(R.string.StartVoipChannelTitle, R.drawable.profile_live_stream, 0, 0, 0),
        VOICE_CHAT(R.string.StartVoipChatTitle, R.drawable.profile_live_stream, 0, 0, 0),
        MESSAGE(R.string.Message, R.drawable.profile_message, 0, 0, 0),
        DISCUSS(R.string.ProfileDiscuss, R.drawable.profile_message, 0, 0, 0),
        MUTE(R.string.Mute, R.drawable.profile_mute, R.raw.anim_profilemute, R.raw.anim_profileunmute, R.string.Unmute),
        REPORT(R.string.ReportChat, R.drawable.profile_report, 0, 0, 0),
        SHARE(R.string.LinkActionShare, R.drawable.profile_share, 0, 0, 0),
        STORY(R.string.AddStory, R.drawable.profile_story, 0, 0, 0),
        CAMERA(R.string.SetPhoto, R.drawable.profile_camera, 0, 0, 0),
        QR(R.string.QrCode, R.drawable.msg_qr_mini, 0, 0, 0),
        VIDEO(R.string.GroupCallCreateVideo, R.drawable.profile_video, 0, 0, 0);

        private final int titleResId;
        private final int iconResId;
        private final int lottieResId;
        private final int lottieResId2;
        private final int titleResId2;

        private boolean state = false;

        ButtonType(int titleResId, int iconResId, int lottieResId, int lottieResId2, int titleResId2) {
            this.titleResId = titleResId;
            this.iconResId = iconResId;
            this.lottieResId = lottieResId;
            this.lottieResId2 = lottieResId2;
            this.titleResId2 = titleResId2;
        }

        public void setState(boolean newState) {
            state = newState;
        }
    }

    public interface OnButtonClickListener {
        void onClick(View v, int index, ButtonType type);
    }

    private final Theme.ResourcesProvider resourcesProvider;
    private final int height = AndroidUtilities.dp(65);
    private final int spacing = AndroidUtilities.dp(4);

    private OnButtonClickListener onButtonClickListener = null;

    private final RecyclerListView listView;
    private final Adapter adapter;

    private final int sidePadding = AndroidUtilities.dp(8);

    private final BlurringShader.BlurManager blurManager;
    protected BlurringShader.StoryBlurDrawer backgroundBlur;
    private FallbackBlurManager2 fallbackBlurManager2;
    private float diff = 0f;
    private float extraHeight = 0f;

    private float expandProgress = 0f;

    public ShortcutsCell(Context context, BlurringShader.BlurManager blurManager, FallbackBlurManager2 fallbackBlurManager2) {
        this(context, null, blurManager, fallbackBlurManager2);
    }

    public void setDiffAndExtraHeight(float diff, float extraHeight, float expandProgress) {
        this.diff = diff;
        this.extraHeight = extraHeight;
        this.expandProgress = expandProgress;
    }

    @SuppressLint("ClickableViewAccessibility")
    public ShortcutsCell(Context context, Theme.ResourcesProvider resourcesProvider, BlurringShader.BlurManager blurManager, FallbackBlurManager2 fallbackBlurManager2) {
        super(context);
        this.resourcesProvider = resourcesProvider;
        this.blurManager = blurManager;
        this.fallbackBlurManager2 = fallbackBlurManager2;

        if (blurManager != null) {
            backgroundBlur = new BlurringShader.StoryBlurDrawer(blurManager, this, BlurringShader.StoryBlurDrawer.BLUR_TYPE_PROFILE_SHORTCUT, !customBlur());
        }
        listView = new RecyclerListView(context);
        SpanningLinearLayoutManager lm = new SpanningLinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false, listView);
        lm.setMinimumItemWidth(AndroidUtilities.dp(36));

        lm.setItemPadding(spacing);
        lm.setItemHeight(height - spacing * 2);

        adapter = new Adapter();

        listView.setSelectorRadius(AndroidUtilities.dp(16));
        listView.setSelectorType(9);
        listView.setOverScrollMode(OVER_SCROLL_NEVER);
        listView.setSelectorDrawableColor(getThemedColor(Theme.key_actionBarTabSelector));

        listView.setAdapter(adapter);
        listView.setLayoutManager(lm);
        listView.addItemDecoration(new PaddingItemDecoration(spacing));
        listView.setPadding(sidePadding, 0, sidePadding, 0);
        listView.setEnabled(true);
        listView.setOnItemClickListener((view, position) -> {
            if (onButtonClickListener != null) {
                ButtonType tp = adapter.items.get(position).type;
                if (view instanceof ShortcutButton) {
                    ShortcutButton btn = ((ShortcutButton) view);
                    if (btn.isAnimationPlaying()) {
                        return;
                    }
                }
                onButtonClickListener.onClick(view, position, tp);
            }
        });

        SpanningLinearLayoutManager.SpanningItemAnimator itemAnimator = lm.new SpanningItemAnimator() {
            @Override
            public boolean animateAdd(RecyclerView.ViewHolder holder) {
                int count = lm.getItemCount();
                if (count == 4) {
                    this.translateMultiplier = 2f;
                } else if (count == 3) {
                    this.translateMultiplier = 1.5f;
                }

                boolean result = super.animateAdd(holder);
                this.translateMultiplier = 0;
                return result;
            }

            @Override
            public boolean animateRemove(RecyclerView.ViewHolder holder, RecyclerView.ItemAnimator.ItemHolderInfo info) {
                return super.animateRemove(holder, info);
            }
        };

        listView.setItemAnimator(itemAnimator);
        itemAnimator.setSupportsChangeAnimations(true);

        addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 80, Gravity.LEFT));
    }

    protected boolean customBlur() {
        return !ProfileActivity.FORCE_MY_BLUR && blurManager.hasRenderNode();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(
                MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY)
        );
    }

    private int getThemedColor(int key) {
        return Theme.getColor(key, resourcesProvider);
    }

    private void changeItems(boolean cacheAlso, Consumer<ShortcutButton> func) {
        listView.getRecycledViewPool().clear();
        int count = 0;
        if (cacheAlso) {
            count = listView.getHiddenChildCount();
            for (int a = 0; a < count; a++) {
                func.accept((ShortcutButton) listView.getHiddenChildAt(a));
            }
            count = listView.getCachedChildCount();
            for (int a = 0; a < count; a++) {
                func.accept((ShortcutButton) listView.getCachedChildAt(a));
            }
            count = listView.getAttachedScrapChildCount();
            for (int a = 0; a < count; a++) {
                func.accept((ShortcutButton) listView.getAttachedScrapChildAt(a));
            }
        }
        count = listView.getChildCount();
        for (int a = 0; a < count; a++) {
            func.accept((ShortcutButton) listView.getChildAt(a));
        }
    }

    public Boolean getStateForButton(ButtonType buttonType) {
        int count = listView.getChildCount();
        for (int a = 0; a < count; a++) {
            ShortcutButton button = (ShortcutButton) listView.getChildAt(a);
            if (button.currentItem.type == buttonType) {
                return button.currentItem.type.state;
            }
        }
        return null;
    }

    public void setStateForButton(ButtonType buttonType, boolean newState) {
        int count = listView.getChildCount();
        for (int a = 0; a < count; a++) {
            ShortcutButton button = (ShortcutButton) listView.getChildAt(a);
            if (button.currentItem.type == buttonType) {
                button.animateTextTo(newState);
                long clickDelay = button.getClickDelay(true);
                if (clickDelay > 0) {
                    this.postDelayed(() -> {
                        button.setState(newState);
                    }, clickDelay);
                }
                break;
            }
        }
    }

    private float showProgress = 0f;
    private boolean openingAnimation = false;
    private int actionBarColor = -1;

    public float setShowProgress(float pr, boolean openingAnimation, int actionBarColor) {
        showProgress = pr;
        this.openingAnimation = openingAnimation;
        this.actionBarColor = actionBarColor;
        listView.setEnabled(pr > 0.5f);
        changeItems(false, shortcutButton -> {
            int bWidth = Math.min(shortcutButton.buttonText.getTextWidth(), shortcutButton.buttonText.getMeasuredWidth());
            shortcutButton.buttonText.setPivotY(-height / 2f);
            shortcutButton.buttonText.setPivotX(bWidth / 2f);
            shortcutButton.buttonImage.setPivotY(-height / 2f + shortcutButton.buttonImage.getMeasuredHeight() + AndroidUtilities.dp(5));
            shortcutButton.buttonImage.setPivotX(shortcutButton.buttonImage.getMeasuredHeight() / 2f);
            shortcutButton.buttonImage.setScaleX(pr);
            shortcutButton.buttonImage.setScaleY(pr);
            shortcutButton.buttonText.setScaleX(pr);
            shortcutButton.buttonText.setScaleY(pr);
            shortcutButton.setAlpha(Math.max(pr * 1.45f - 0.45f, 0f));
            RecyclerView.LayoutParams lp = (RecyclerView.LayoutParams)shortcutButton.getLayoutParams();
            if (lp != null) {
                lp.height = AndroidUtilities.lerp(1, height - spacing * 2, pr);
                shortcutButton.requestLayout();
            }
        });

        return height - spacing * 2 - (AndroidUtilities.lerp(1, height - spacing * 2, pr));
    }

    public Adapter getAdapter() {
        return adapter;
    }

    public RecyclerListView getListView() { return listView; };

    public void setOnButtonClickListener(OnButtonClickListener onButtonClickListener) {
        this.onButtonClickListener = onButtonClickListener;
    }

    public static class ListItem {
        public ButtonType type;
        public String text;
        public String text2;
        public int iconResource;
        public int animatedIconResource;
        public int animatedIconResource2;

        public ListItem(ButtonType type) {
            this.type = type;
            this.text = LocaleController.getString(type.titleResId);
            if (type.titleResId2 != 0) {
                this.text2 = LocaleController.getString(type.titleResId2);
            } else {
                this.text2 = null;
            }
            this.iconResource = type.iconResId;
            this.animatedIconResource = type.lottieResId;
            this.animatedIconResource2 = type.lottieResId2;
        }

        public boolean compare(ListItem other) {
            return other != null
                    && type == other.type
                    && Objects.equals(text, other.text)
                    && Objects.equals(text2, other.text2)
                    && iconResource == other.iconResource
                    && animatedIconResource == other.animatedIconResource
                    && animatedIconResource2 == other.animatedIconResource2;
        }
    }

    public class ShortcutButton extends FrameLayout {

        RLottieImageView buttonImage;
        SimpleTextView buttonText;

        public ShortcutButton(Context context) {
            super(context);

            buttonText = new SimpleTextView(getContext());
            buttonText.setTextColor(getThemedColor(Theme.key_actionBarDefaultIcon));
            buttonText.setTextSize(11);
            buttonText.setTypeface(AndroidUtilities.bold());
            buttonText.setScrollNonFitText(true);

            buttonImage = new RLottieImageView(getContext());
            buttonImage.setScaleType(ImageView.ScaleType.CENTER);

            addView(buttonImage, LayoutHelper.createFrame(24, 24, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 8, 0, 0));
            addView(buttonText, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 36, 0, 0));

            //setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(16), 0x17000000));
        }

        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            super.onLayout(changed, left, top, right, bottom);
            int center = this.getMeasuredWidth() / 2;
            int bWidth = Math.min(buttonText.getTextWidth(), buttonText.getMeasuredWidth());
            int l = center - (bWidth / 2);
            buttonText.layout(l, buttonText.getTop(), l + bWidth, buttonText.getBottom());
        }

        private ListItem currentItem;
        private int currentIndex;

        public void setData(int index, ListItem item) {
            currentIndex = index;
            if (item == null || item.compare(currentItem)) {
                currentItem = item;

                if (buttonImage != null) {
                    RLottieDrawable drawable = buttonImage.getAnimatedDrawable();
                    if (drawable != null) {
                        buttonImage.stopAnimation();
                        drawable.setCurrentFrame(0);
                    }
                }

                return;
            }

            currentItem = item;

            if (item.animatedIconResource != 0) {
                drawableUnstate = getAnimatedIcon(item.animatedIconResource);
                drawableState = getAnimatedIcon(item.animatedIconResource2);
                buttonImage.setAnimation(currentItem.type.state ? drawableState : drawableUnstate);
            } else {
                Drawable drawable = ContextCompat.getDrawable(getContext(), item.iconResource);
                if (drawable != null) {
                    drawable.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_actionBarDefaultIcon), PorterDuff.Mode.SRC_IN));
                }
                buttonImage.setImageDrawable(drawable);
            }

            buttonText.setText(currentItem.type.state ? item.text2 : item.text);
        }

        private void setState(boolean newState) {
            if (currentItem.type.state == newState) {
                return;
            }
            buttonImage.stopAnimation();
            RLottieDrawable drawable = buttonImage.getAnimatedDrawable();
            drawable.setCurrentFrame(0);
            currentItem.type.state = newState;
            RLottieDrawable newDrawable = currentItem.type.state ? drawableState : drawableUnstate;
            newDrawable.setCurrentFrame(0);
            buttonImage.setAnimation(newDrawable);
        }

        public void animateTextTo(boolean newState) {
            buttonText.setText(newState ? currentItem.text2 : currentItem.text);
            buttonText.requestLayout();
        }

        private RLottieDrawable drawableUnstate, drawableState;

        private RLottieDrawable getAnimatedIcon(int res) {
            RLottieDrawable aDrawable = new RLottieDrawable(res, "" + res, AndroidUtilities.dp(36), AndroidUtilities.dp(36), true, null);
            aDrawable.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_actionBarDefaultIcon), PorterDuff.Mode.SRC_IN));
            return aDrawable;
        }

        public long getClickDelay(boolean startAnimation) {
            boolean animationsEnabled = MessagesController.getGlobalMainSettings().getBoolean("view_animations", true);
            if (!animationsEnabled) {
                return 0;
            }

            if (buttonImage.getAnimatedDrawable() != null) {
                if (startAnimation) {
                    buttonImage.playAnimation();
                }

                return buttonImage.getAnimatedDrawable().getDuration();
            }
            return 0;
        }

        public boolean isAnimationPlaying() {
            return buttonImage.isPlaying();
        }
    }

    public class Adapter extends RecyclerListView.SelectionAdapter {
        ArrayList<ListItem> items = new ArrayList<>(4);

        @SuppressLint("ClickableViewAccessibility")
        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ShortcutButton view = new ShortcutButton(parent.getContext()) {

                private final Path path = new Path();
                protected Paint backgroundPaint, fallbackPaint;
                protected Paint clipPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

                float radius = AndroidUtilities.dp(10);

                RectF bounds = new RectF();

                @Override
                protected void dispatchDraw(@NonNull Canvas canvas) {

                    boolean canDrawBlur = showProgress > 0.8f ;

                    if (backgroundPaint == null) {
                        backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                        if (customBlur()) {
                            if (Theme.isCurrentThemeDark()) {
                                backgroundPaint.setColor(0x11000000);
                            } else {
                                backgroundPaint.setColor(0x00000000);
                            }
                        } else {
                            backgroundPaint.setColor(0x25000000);
                            ColorMatrix cm = new ColorMatrix();
                            AndroidUtilities.adjustSaturationColorMatrix(cm, +0.75f);
                            AndroidUtilities.adjustBrightnessColorMatrix(cm, +0.245f);
                            AndroidUtilities.multiplyBrightnessColorMatrix(cm, 0.595f);
                            backgroundPaint.setColorFilter(new ColorMatrixColorFilter(cm));
                        }
                    }

                    if (fallbackPaint == null && actionBarColor != -1) {
                        fallbackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                        fallbackPaint.setColor(actionBarColor);
                        ColorMatrix cm = new ColorMatrix();
                        AndroidUtilities.adjustSaturationColorMatrix(cm, +0.75f);
                        AndroidUtilities.adjustBrightnessColorMatrix(cm, +0.245f);
                        AndroidUtilities.multiplyBrightnessColorMatrix(cm, 0.595f);
                        fallbackPaint.setColorFilter(new ColorMatrixColorFilter(cm));
                    }

                    if (backgroundBlur != null) {
                        int w = this.getWidth();
                        int h = this.getHeight();
                        float diffX = w - w * this.getScaleX();
                        float diffH = h - h * this.getScaleY();
                        bounds.set(diffX / 2f, diffH / 2f, w - diffX / 2, h - diffH / 2);
                        if (customBlur()) {
                            if (canDrawBlur) {
                                drawBlur(backgroundBlur, canvas, bounds, -getX(), AndroidUtilities.dp(80), 1.0f);
                                canvas.drawRoundRect(bounds, radius, radius, backgroundPaint);
                            } else {
                                canvas.drawRoundRect(bounds, radius, radius, fallbackPaint);
                            }
                        } else {
                            if (canDrawBlur) {
                                drawBlur(backgroundBlur, canvas, bounds, -getX(), -ShortcutsCell.this.getTranslationY() + AndroidUtilities.dp(80), 1.0f);
                                canvas.drawRoundRect(bounds, radius, radius, backgroundPaint);
                            } else {
                                canvas.drawRoundRect(bounds, radius, radius, fallbackPaint);
                            }
                        }
                    }
                    super.dispatchDraw(canvas);
                    invalidate();
                }

                private void drawBlur(BlurringShader.StoryBlurDrawer blur, Canvas canvas, RectF rect, float ox, float oy, float alpha) {
                    if (!canvas.isHardwareAccelerated() && customBlur()) {
                        return;
                    }
                    canvas.save();
                    path.rewind();
                    path.addRoundRect(rect, radius, radius, Path.Direction.CW);
                    canvas.clipPath(path);
                    if (extraHeight > AndroidUtilities.dp(COLLAPSE_HEIGHT_DP + 28)) {
                        canvas.translate(ox, oy);
                    } else {
                        canvas.translate(ox, oy - height);
                    }
                    if (customBlur()) {
                        blur.drawRect(canvas, 0, 0, alpha);
                    } else {
                        fallbackBlurManager2.draw(this, canvas);
                    }
                    canvas.restore();
                }
            };

            view.setOnTouchListener((v, event) -> {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        animateScaleSafely(v, 1f, 0.95f, 150);
                        return true;

                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        animateScaleSafely(v, v.getScaleX(), 1f, 150);
                        break;
                }
                return false;
            });
            return new RecyclerListView.Holder(view);
        }

        Map<View, ValueAnimator> runningAnimators = new HashMap<>();

        private void animateScaleSafely(View view, float from, float to, long duration) {
            ValueAnimator current = runningAnimators.get(view);
            if (current != null && current.isRunning()) {
                current.cancel();
            }

            ValueAnimator animator = ValueAnimator.ofFloat(from, to);
            animator.setDuration(duration);
            animator.setInterpolator(new AccelerateDecelerateInterpolator());
            animator.addUpdateListener(a -> {
                float value = (float) a.getAnimatedValue();
                view.setScaleX(value);
                view.setScaleY(value);
            });
            animator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    runningAnimators.remove(view);
                }

                @Override
                public void onAnimationCancel(Animator animation) {
                    runningAnimators.remove(view);
                }
            });

            runningAnimators.put(view, animator);
            animator.start();
        }

        @Override
        public void onViewRecycled(@NonNull RecyclerView.ViewHolder holder) {
            ViewGroup.LayoutParams lp = holder.itemView.getLayoutParams();
            if (lp != null) {
                lp.width = -1;
                lp.height = -1;
            }
            holder.itemView.animate().cancel();
            holder.itemView.setScaleX(1f);
            holder.itemView.setScaleY(1f);
            super.onViewRecycled(holder);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            ListItem item = items.get(position);
            ShortcutButton button = (ShortcutButton) holder.itemView;
            button.setData(position, item);
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        public void setItems(ArrayList<ListItem> items) {
            this.items = items;
        }

        public ArrayList<ListItem> getItems() {
            return items;
        }

        @Override
        public boolean isEnabled(@NonNull RecyclerView.ViewHolder holder) {
            return true;
        }

        public ListItem createButton(ButtonType type) {
            return new ListItem(type);
        }
    }

    public static class DiffUtilCallback extends DiffUtil.Callback {

        ArrayList<ListItem> oldItems;
        ArrayList<ListItem> newItems;
        boolean sizeChanged;

        public DiffUtilCallback(ArrayList<ListItem> oldItems, ArrayList<ListItem> newItems) {
            this.oldItems = oldItems;
            this.newItems = newItems;
            sizeChanged = oldItems.size() != newItems.size();
        }

        @Override
        public int getOldListSize() {
            return oldItems.size();
        }

        @Override
        public int getNewListSize() {
            return newItems.size();
        }

        @Override
        public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
            return oldItems.get(oldItemPosition).type == newItems.get(newItemPosition).type;
        }

        @Override
        public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
            if (sizeChanged) return false;

            ListItem oldItem = oldItems.get(oldItemPosition);
            ListItem newItem = newItems.get(newItemPosition);

            return oldItem.iconResource == newItem.iconResource && Objects.equals(oldItem.text, newItem.text);
        }
    }
}
