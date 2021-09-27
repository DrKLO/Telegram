package org.telegram.ui.Components;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.LinearSmoothScroller;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatThemeController;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.tgnet.ResultCallback;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.ChatTheme;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.ChatActivity;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

public class ChatThemeBottomSheet extends BottomSheet implements NotificationCenter.NotificationCenterDelegate {

    private final Adapter adapter;
    private final ChatActivity.ThemeDelegate themeDelegate;
    private final ChatTheme originalTheme;
    private final boolean originalIsDark;
    private final ChatActivity chatActivity;
    private final RecyclerListView recyclerView;
    private final LinearLayoutManager layoutManager;
    private final FlickerLoadingView progressView;
    private final TextView titleView;
    private final RLottieDrawable darkThemeDrawable;
    private final RLottieImageView darkThemeView;
    private final LinearSmoothScroller scroller;
    private final View applyButton;
    private TextView applyTextView;
    private TextView resetTextView;
    private ChatThemeItem selectedItem;
    private boolean forceDark;
    private boolean isApplyClicked;
    private boolean isLightDarkChangeAnimation;
    private int prevSelectedPosition = -1;
    private View changeDayNightView;
    private float changeDayNightViewProgress;
    private ValueAnimator changeDayNightViewAnimator;
    HintView hintView;

    public ChatThemeBottomSheet(final ChatActivity chatActivity, ChatActivity.ThemeDelegate themeDelegate) {
        super(chatActivity.getParentActivity(), true, themeDelegate);
        this.chatActivity = chatActivity;
        this.themeDelegate = themeDelegate;
        this.originalTheme = themeDelegate.getCurrentTheme();
        this.originalIsDark = Theme.getActiveTheme().isDark();
        adapter = new Adapter(themeDelegate);
        setDimBehind(false);
        setCanDismissWithSwipe(false);
        setApplyBottomPadding(false);

        FrameLayout rootLayout = new FrameLayout(getContext());
        setCustomView(rootLayout);

        titleView = new TextView(getContext());
        titleView.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        titleView.setLines(1);
        titleView.setSingleLine(true);
        titleView.setText(LocaleController.getString("SelectTheme", R.string.SelectTheme));
        titleView.setTextColor(getThemedColor(Theme.key_dialogTextBlack));
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        titleView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        titleView.setPadding(AndroidUtilities.dp(21), AndroidUtilities.dp(6), AndroidUtilities.dp(21), AndroidUtilities.dp(8));
        rootLayout.addView(titleView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.START, 0, 0, 62, 0));

        int drawableColor = getThemedColor(Theme.key_featuredStickers_addButton);
        int drawableSize = AndroidUtilities.dp(28);
        darkThemeDrawable = new RLottieDrawable(R.raw.sun_outline, "" + R.raw.sun_outline, drawableSize, drawableSize, true, null);
        darkThemeDrawable.setPlayInDirectionOfCustomEndFrame(true);
        darkThemeDrawable.beginApplyLayerColors();
        setDarkButtonColor(drawableColor);
        darkThemeDrawable.commitApplyLayerColors();

        darkThemeView = new RLottieImageView(getContext());
        darkThemeView.setAnimation(darkThemeDrawable);
        darkThemeView.setScaleType(ImageView.ScaleType.CENTER);
        darkThemeView.setOnClickListener(view -> {
            if (changeDayNightViewAnimator != null) {
                return;
            }
            setupLightDarkTheme(!forceDark);
        });
        rootLayout.addView(darkThemeView, LayoutHelper.createFrame(44, 44, Gravity.TOP | Gravity.END, 0, 0, 7, 0));
        forceDark = !Theme.getActiveTheme().isDark();
        setForceDark(Theme.getActiveTheme().isDark(), false);

        scroller = new LinearSmoothScroller(getContext()) {
            @Override
            protected int calculateTimeForScrolling(int dx) {
                return super.calculateTimeForScrolling(dx) * 6;
            }
        };
        recyclerView = new RecyclerListView(getContext());
        recyclerView.setAdapter(adapter);
        recyclerView.setClipChildren(false);
        recyclerView.setClipToPadding(false);
        recyclerView.setHasFixedSize(true);
        recyclerView.setItemAnimator(null);
        recyclerView.setNestedScrollingEnabled(false);
        recyclerView.setLayoutManager(layoutManager = new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));
        recyclerView.setPadding(AndroidUtilities.dp(12), 0, AndroidUtilities.dp(12), 0);
        recyclerView.setOnItemClickListener((view, position) -> {
            if (adapter.items.get(position) == selectedItem || changeDayNightView != null) {
                return;
            }
            selectedItem = adapter.items.get(position);
            isLightDarkChangeAnimation = false;
            if (selectedItem.chatTheme == null || selectedItem.chatTheme.isDefault) {
                applyTextView.animate().alpha(0f).setDuration(300).start();
                resetTextView.animate().alpha(1f).setDuration(300).start();
            } else {
                resetTextView.animate().alpha(0f).setDuration(300).start();
                applyTextView.animate().alpha(1f).setDuration(300).start();
            }
            if (selectedItem.chatTheme.isDefault) {
                themeDelegate.setCurrentTheme(null, true, forceDark);
            } else {
                themeDelegate.setCurrentTheme(selectedItem.chatTheme, true, forceDark);
            }
            adapter.setSelectedItem(position);
            containerView.postDelayed(new Runnable() {
                @Override
                public void run() {
                    RecyclerView.LayoutManager layoutManager = recyclerView.getLayoutManager();
                    if (layoutManager != null) {
                        final int targetPosition = position > prevSelectedPosition
                                ? Math.min(position + 1, adapter.items.size() - 1)
                                : Math.max(position - 1, 0);
                        scroller.setTargetPosition(targetPosition);
                        layoutManager.startSmoothScroll(scroller);
                    }
                    prevSelectedPosition = position;
                }
            }, 100);
            for (int i = 0; i < recyclerView.getChildCount(); i++) {
                Adapter.ChatThemeView child = (Adapter.ChatThemeView) recyclerView.getChildAt(i);
                if (child != view) {
                    child.cancelAnimation();
                }
            }
            if (!adapter.items.get(position).chatTheme.isDefault) {
                ((Adapter.ChatThemeView) view).playEmojiAnimation();
            }
        });

        progressView = new FlickerLoadingView(getContext(), resourcesProvider);
        progressView.setViewType(FlickerLoadingView.CHAT_THEMES_TYPE);
        progressView.setVisibility(View.VISIBLE);
        rootLayout.addView(progressView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 104, Gravity.START, 0, 44, 0, 0));

        rootLayout.addView(recyclerView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 104, Gravity.START, 0, 44, 0, 0));

        applyButton = new View(getContext());
        applyButton.setBackground(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(6), getThemedColor(Theme.key_featuredStickers_addButton), getThemedColor(Theme.key_featuredStickers_addButtonPressed)));
        applyButton.setEnabled(false);
        applyButton.setOnClickListener((view) -> applySelectedTheme());
        rootLayout.addView(applyButton, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.START, 16, 162, 16, 16));

        resetTextView = new TextView(getContext());
        resetTextView.setAlpha(0f);
        resetTextView.setEllipsize(TextUtils.TruncateAt.END);
        resetTextView.setGravity(Gravity.CENTER);
        resetTextView.setLines(1);
        resetTextView.setSingleLine(true);
        resetTextView.setText(themeDelegate.getCurrentTheme() == null ? LocaleController.getString("DoNoSetTheme", R.string.DoNoSetTheme) : LocaleController.getString("ChatResetTheme", R.string.ChatResetTheme));
        resetTextView.setTextColor(getThemedColor(Theme.key_featuredStickers_buttonText));
        resetTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        resetTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        resetTextView.setVisibility(View.INVISIBLE);
        rootLayout.addView(resetTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.START, 16, 162, 16, 16));

        applyTextView = new TextView(getContext());
        applyTextView.setEllipsize(TextUtils.TruncateAt.END);
        applyTextView.setGravity(Gravity.CENTER);
        applyTextView.setLines(1);
        applyTextView.setSingleLine(true);
        applyTextView.setText(LocaleController.getString("ChatApplyTheme", R.string.ChatApplyTheme));
        applyTextView.setTextColor(getThemedColor(Theme.key_featuredStickers_buttonText));
        applyTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        applyTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        applyTextView.setVisibility(View.INVISIBLE);
        rootLayout.addView(applyTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.START, 16, 162, 16, 16));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ChatThemeController.preloadAllWallpaperThumbs(true);
        ChatThemeController.preloadAllWallpaperThumbs(false);
        ChatThemeController.preloadAllWallpaperImages(true);
        ChatThemeController.preloadAllWallpaperImages(false);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.emojiLoaded);

        isApplyClicked = false;
        List<ChatTheme> cachedThemes = themeDelegate.getCachedThemes();
        if (cachedThemes == null || cachedThemes.isEmpty()) {
            ChatThemeController.requestAllChatThemes(new ResultCallback<List<ChatTheme>>() {
                @Override
                public void onComplete(List<ChatTheme> result) {
                    if (result != null && !result.isEmpty()) {
                        themeDelegate.setCachedThemes(result);
                    }
                    onDataLoaded(result);
                }

                @Override
                public void onError(TLRPC.TL_error error) {
                    Toast.makeText(getContext(), error.text, Toast.LENGTH_SHORT).show();
                }
            }, true);
        } else {
            onDataLoaded(cachedThemes);
        }


        if (chatActivity.getCurrentUser() != null && SharedConfig.dayNightThemeSwitchHintCount > 0) {
            SharedConfig.updateDayNightThemeSwitchHintCount(SharedConfig.dayNightThemeSwitchHintCount - 1);
            hintView = new HintView(getContext(), 9, chatActivity.getResourceProvider());
            hintView.setVisibility(View.INVISIBLE);
            hintView.setShowingDuration(5000);
            hintView.setBottomOffset(-AndroidUtilities.dp(8));
            hintView.setText(AndroidUtilities.replaceTags(LocaleController.formatString("ChatThemeDayNightSwitchTooltip", R.string.ChatThemeDayNightSwitchTooltip, chatActivity.getCurrentUser().first_name)));
            AndroidUtilities.runOnUIThread(() -> {
                hintView.showForView(darkThemeView, true);
            }, 1500);

            container.addView(hintView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 10, 0, 10, 0));
        }
    }

    @Override
    public void onContainerTranslationYChanged(float y) {
        if (hintView != null) {
            hintView.hide();
        }
    }
    @Override
    public void onBackPressed() {
        close();
    }

    @Override
    public void dismiss() {
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.emojiLoaded);
        super.dismiss();
        if (!isApplyClicked) {
            themeDelegate.setCurrentTheme(originalTheme, true, originalIsDark);
        }
    }

    public void close() {
        if (hasChanges()) {
            AlertDialog.Builder builder = new AlertDialog.Builder(getContext(), resourcesProvider);
            builder.setTitle(LocaleController.getString("ChatThemeSaveDialogTitle", R.string.ChatThemeSaveDialogTitle));
            builder.setSubtitle(LocaleController.getString("ChatThemeSaveDialogText", R.string.ChatThemeSaveDialogText));
            builder.setPositiveButton(LocaleController.getString("ChatThemeSaveDialogApply", R.string.ChatThemeSaveDialogApply), (dialogInterface, i) -> applySelectedTheme());
            builder.setNegativeButton(LocaleController.getString("ChatThemeSaveDialogDiscard", R.string.ChatThemeSaveDialogDiscard), (dialogInterface, i) -> dismiss());
            builder.show();
        } else {
            dismiss();
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.emojiLoaded) {
            adapter.notifyDataSetChanged();
        }
    }

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        ThemeDescription.ThemeDescriptionDelegate descriptionDelegate = new ThemeDescription.ThemeDescriptionDelegate() {
            private boolean isAnimationStarted = false;

            @Override
            public void onAnimationProgress(float progress) {
                if (progress == 0f && !isAnimationStarted) {
                    onAnimationStart();
                    isAnimationStarted = true;
                }
                setDarkButtonColor(getThemedColor(Theme.key_featuredStickers_addButton));
                setOverlayNavBarColor(getThemedColor(Theme.key_dialogBackground));
                if (isLightDarkChangeAnimation) {
                    setItemsAnimationProgress(progress);
                }
                if (progress == 1f && isAnimationStarted) {
                    isLightDarkChangeAnimation = false;
                    onAnimationEnd();
                    isAnimationStarted = false;
                }
            }

            @Override
            public void didSetColor() {
            }
        };
        ArrayList<ThemeDescription> themeDescriptions = new ArrayList<>();
        themeDescriptions.add(new ThemeDescription(null, ThemeDescription.FLAG_BACKGROUNDFILTER, null, null, new Drawable[]{shadowDrawable}, descriptionDelegate, Theme.key_dialogBackground));
        themeDescriptions.add(new ThemeDescription(titleView, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_dialogTextBlack));
        themeDescriptions.add(new ThemeDescription(recyclerView, ThemeDescription.FLAG_CELLBACKGROUNDCOLOR, new Class[]{Adapter.ChatThemeView.class}, null, null, null, Theme.key_dialogBackgroundGray));
        themeDescriptions.add(new ThemeDescription(applyButton, ThemeDescription.FLAG_BACKGROUNDFILTER, null, null, null, null, Theme.key_featuredStickers_addButton));
        themeDescriptions.add(new ThemeDescription(applyButton, ThemeDescription.FLAG_BACKGROUNDFILTER | ThemeDescription.FLAG_DRAWABLESELECTEDSTATE, null, null, null, null, Theme.key_featuredStickers_addButtonPressed));
        for (ThemeDescription description : themeDescriptions) {
            description.resourcesProvider = themeDelegate;
        }
        return themeDescriptions;
    }

    @SuppressLint("NotifyDataSetChanged")
    public void setupLightDarkTheme(boolean isDark) {
        if (changeDayNightViewAnimator != null) {
            changeDayNightViewAnimator.cancel();
        }
        FrameLayout decorView1 = (FrameLayout) chatActivity.getParentActivity().getWindow().getDecorView();
        FrameLayout decorView2 = (FrameLayout) getWindow().getDecorView();
        Bitmap bitmap = Bitmap.createBitmap(decorView2.getWidth(), decorView2.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas bitmapCanvas = new Canvas(bitmap);
        darkThemeView.setAlpha(0f);
        decorView1.draw(bitmapCanvas);
        decorView2.draw(bitmapCanvas);
        darkThemeView.setAlpha(1f);

        Paint xRefPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        xRefPaint.setColor(0xff000000);
        xRefPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));

        Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bitmapPaint.setFilterBitmap(true);
        int[] position = new int[2];
        darkThemeView.getLocationInWindow(position);
        float x = position[0];
        float y = position[1];
        float cx = x + darkThemeView.getMeasuredWidth() / 2f;
        float cy = y + darkThemeView.getMeasuredHeight() / 2f;

        float r = Math.max(bitmap.getHeight(), bitmap.getWidth()) * 0.9f;

        Shader bitmapShader = new BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        bitmapPaint.setShader(bitmapShader);
        changeDayNightView = new View(getContext()) {

            @Override
            protected void onDraw(Canvas canvas) {
                super.onDraw(canvas);
                if (isDark) {
                    if (changeDayNightViewProgress > 0f) {
                        bitmapCanvas.drawCircle(cx, cy, r * changeDayNightViewProgress, xRefPaint);
                    }
                    canvas.drawBitmap(bitmap, 0, 0, bitmapPaint);
                } else {
                    canvas.drawCircle(cx, cy, r * (1f - changeDayNightViewProgress), bitmapPaint);
                }
                canvas.save();
                canvas.translate(x, y);
                darkThemeView.draw(canvas);
                canvas.restore();
            }
        };
        changeDayNightViewProgress = 0f;
        changeDayNightViewAnimator = ValueAnimator.ofFloat(0, 1f);
        changeDayNightViewAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator valueAnimator) {
                changeDayNightViewProgress = (float) valueAnimator.getAnimatedValue();
                changeDayNightView.invalidate();
            }
        });
        changeDayNightViewAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (changeDayNightView != null) {
                    if (changeDayNightView.getParent() != null) {
                        ((ViewGroup) changeDayNightView.getParent()).removeView(changeDayNightView);
                    }
                    changeDayNightView = null;
                }
                changeDayNightViewAnimator = null;
                super.onAnimationEnd(animation);
            }
        });
        changeDayNightViewAnimator.setDuration(400);
        changeDayNightViewAnimator.setInterpolator(Easings.easeInOutQuad);
        changeDayNightViewAnimator.start();

        decorView2.addView(changeDayNightView, new ViewGroup.LayoutParams(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        AndroidUtilities.runOnUIThread(() -> {
            if (adapter == null || adapter.items == null) {
                return;
            }
            setForceDark(isDark, true);
            if (selectedItem != null) {
                isLightDarkChangeAnimation = true;
                if (selectedItem.chatTheme.isDefault) {
                    themeDelegate.setCurrentTheme(null, false, isDark);
                } else {
                    themeDelegate.setCurrentTheme(selectedItem.chatTheme, false, isDark);
                }
            }
            if (adapter != null && adapter.items != null) {
                for (int i = 0; i < adapter.items.size(); i++) {
                    adapter.items.get(i).isDark = isDark;
                }
                adapter.notifyDataSetChanged();
            }
        });
    }

    @Override
    protected boolean onContainerTouchEvent(MotionEvent event) {
        if (event == null || !hasChanges()) {
            return false;
        }
        int x = (int) event.getX();
        int y = (int) event.getY();
        boolean touchInsideContainer = y >= containerView.getTop()
                && x >= containerView.getLeft()
                && x <= containerView.getRight();
        if (touchInsideContainer) {
            return false;
        } else {
            chatActivity.getFragmentView().dispatchTouchEvent(event);
            return true;
        }
    }

    private void onDataLoaded(List<ChatTheme> result) {
        if (result == null || result.isEmpty()) {
            return;
        }

        ChatThemeItem noThemeItem = new ChatThemeItem(result.get(0));
        List<ChatThemeItem> items = new ArrayList<>(result.size());
        ChatTheme currentTheme = themeDelegate.getCurrentTheme();

        items.add(0, noThemeItem);
        selectedItem = noThemeItem;

        for (int i = 1; i < result.size(); ++i) {
            ChatTheme chatTheme = result.get(i);
            ChatThemeItem item = new ChatThemeItem(chatTheme);

            HashMap<String, Integer> colorsMap = chatTheme.getCurrentColors(chatActivity.getCurrentAccount(), true);
            Integer color = colorsMap.get(Theme.key_chat_inBubble);
            if (color == null) {
                color = getThemedColor(Theme.key_chat_inBubble);
            }
            item.inBubbleColorDark = color;
            color = colorsMap.get(Theme.key_chat_outBubble);
            if (color == null) {
                color = getThemedColor(Theme.key_chat_outBubble);
            }
            item.outBubbleColorDark = color;
            color = colorsMap.get(Theme.key_featuredStickers_addButton);
            item.strokeColorLight = color != null ? color : 0;

            colorsMap = chatTheme.getCurrentColors(chatActivity.getCurrentAccount(), false);
            color = colorsMap.get(Theme.key_chat_inBubble);
            if (color == null) {
                color = getThemedColor(Theme.key_chat_inBubble);
            }
            item.inBubbleColorLight = color;
            color = colorsMap.get(Theme.key_chat_outBubble);
            if (color == null) {
                color = getThemedColor(Theme.key_chat_outBubble);
            }
            item.outBubbleColorLight = color;
            color = colorsMap.get(Theme.key_featuredStickers_addButton);
            item.strokeColorDark = color != null ? color : 0;

            item.isDark = forceDark;
            items.add(item);
        }
        adapter.setItems(items);
        applyButton.setEnabled(true);
        applyTextView.setAlpha(0f);
        resetTextView.setAlpha(0f);
        recyclerView.setAlpha(0f);

        applyTextView.setVisibility(View.VISIBLE);
        resetTextView.setVisibility(View.VISIBLE);
        darkThemeView.setVisibility(View.VISIBLE);

        boolean showRestText = false;
        if (currentTheme != null) {
            int selectedPosition = -1;
            for (int i = 0; i != items.size(); ++i) {
                if (items.get(i).chatTheme.getEmoticon().equals(currentTheme.getEmoticon())) {
                    selectedItem = items.get(i);
                    selectedPosition = i;
                    break;
                }
            }
            if (selectedPosition != -1) {
                prevSelectedPosition = selectedPosition;
                adapter.setSelectedItem(selectedPosition);
                int finalSelectedPosition = Math.min(selectedPosition, adapter.items.size() - 1);
                layoutManager.scrollToPositionWithOffset(finalSelectedPosition, 0);
            }
        } else {
            showRestText = true;
            adapter.setSelectedItem(0);
            layoutManager.scrollToPositionWithOffset(0, 0);
        }

        recyclerView.animate().alpha(1f).setDuration(150).start();
        resetTextView.animate().alpha(showRestText ? 1f : 0).setDuration(150).start();
        applyTextView.animate().alpha(showRestText ? 0f : 1).setDuration(150).start();
        progressView.animate().alpha(0f).setListener(new HideViewAfterAnimation(progressView)).setDuration(150).start();
    }

    private void onAnimationStart() {
        if (adapter != null && adapter.items != null) {
            for (ChatThemeItem item : adapter.items) {
                item.isDark = forceDark;
            }
        }
        if (!isLightDarkChangeAnimation) {
            setItemsAnimationProgress(1.0f);
        }
    }

    private void onAnimationEnd() {
        isLightDarkChangeAnimation = false;
    }

    private void setDarkButtonColor(int color) {
        darkThemeDrawable.setLayerColor("Sunny.**", color);
        darkThemeDrawable.setLayerColor("Path.**", color);
        darkThemeDrawable.setLayerColor("Path 10.**", color);
        darkThemeDrawable.setLayerColor("Path 11.**", color);
    }

    private void setForceDark(boolean isDark, boolean playAnimation) {
        useLightNavBar = isDark;
        useLightStatusBar = isDark;
        if (forceDark == isDark) {
            return;
        }
        forceDark = isDark;
        if (playAnimation) {
            darkThemeDrawable.setCustomEndFrame(isDark ? darkThemeDrawable.getFramesCount() : 0);
            darkThemeView.playAnimation();
        } else {
            darkThemeDrawable.setCurrentFrame(isDark ? darkThemeDrawable.getFramesCount() - 1 : 0, false, true);
            darkThemeView.invalidate();
        }
    }

    private void setItemsAnimationProgress(float progress) {
        for (int i = 0; i < adapter.getItemCount(); ++i) {
            adapter.items.get(i).animationProgress = progress;
        }
    }

    private void applySelectedTheme() {
        Bulletin bulletin = null;
        ChatTheme newTheme = selectedItem.chatTheme;
        if (newTheme.isDefault) {
            newTheme = null;
        }
        if (selectedItem != null && newTheme != originalTheme) {
            ChatTheme chatTheme = selectedItem.chatTheme;
            String emoticon = (chatTheme != null && !chatTheme.isDefault) ? chatTheme.getEmoticon() : null;
            ChatThemeController.getInstance(currentAccount).setDialogTheme(chatActivity.getDialogId(), emoticon, true);
            if (chatTheme != null && !chatTheme.isDefault) {
                themeDelegate.setCurrentTheme(chatTheme, true, originalIsDark);
            } else {
                themeDelegate.setCurrentTheme(null, true, originalIsDark);
            }
            isApplyClicked = true;

            TLRPC.User user = chatActivity.getCurrentUser();
            if (user != null && !user.self) {
                boolean themeDisabled = false;
                if (TextUtils.isEmpty(emoticon)) {
                    themeDisabled = true;
                    emoticon = "❌";
                }
                TLRPC.Document document = emoticon != null ? MediaDataController.getInstance(currentAccount).getEmojiAnimatedSticker(emoticon) : null;
                StickerSetBulletinLayout layout = new StickerSetBulletinLayout(getContext(), null, StickerSetBulletinLayout.TYPE_EMPTY, document, chatActivity.getResourceProvider());
                layout.subtitleTextView.setVisibility(View.GONE);
                if (themeDisabled) {
                    layout.titleTextView.setText(AndroidUtilities.replaceTags(LocaleController.formatString("ThemeAlsoDisabledForHint", R.string.ThemeAlsoDisabledForHint, user.first_name)));
                } else {
                    layout.titleTextView.setText(AndroidUtilities.replaceTags(LocaleController.formatString("ThemeAlsoAppliedForHint", R.string.ThemeAlsoAppliedForHint, user.first_name)));
                }
                layout.titleTextView.setTypeface(null);
                bulletin = Bulletin.make(chatActivity, layout, Bulletin.DURATION_LONG);
            }
        }
        dismiss();
        if (bulletin != null) {
            bulletin.show();
        }
    }

    private boolean hasChanges() {
        if (selectedItem == null) {
            return false;
        } else {
            String oldEmoticon = originalTheme != null ? originalTheme.getEmoticon() : null;
            if (TextUtils.isEmpty(oldEmoticon)) {
                oldEmoticon = "❌";
            }
            String newEmoticon = selectedItem.chatTheme != null ? selectedItem.chatTheme.getEmoticon() : null;
            if (TextUtils.isEmpty(newEmoticon)) {
                newEmoticon = "❌";
            }
            return !Objects.equals(oldEmoticon, newEmoticon);
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private class Adapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

        private final Theme.ResourcesProvider resourcesProvider;

        private List<ChatThemeItem> items;
        private WeakReference<ChatThemeView> selectedViewRef;
        private int selectedItemPosition = -1;

        public Adapter(Theme.ResourcesProvider resourcesProvider) {
            this.resourcesProvider = resourcesProvider;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new RecyclerListView.Holder(new ChatThemeView(parent.getContext(), resourcesProvider));
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            ChatThemeView view = (ChatThemeView) holder.itemView;
            view.setItem(items.get(position));
            view.setSelected(position == selectedItemPosition);
            if (position == selectedItemPosition) {
                selectedViewRef = new WeakReference<>(view);
            }
        }

        @Override
        public int getItemCount() {
            return items == null ? 0 : items.size();
        }

        public void setItems(List<ChatThemeItem> newItems) {
            items = newItems;
            notifyDataSetChanged();
        }

        public void setSelectedItem(int position) {
            if (selectedItemPosition == position) {
                return;
            }
            if (selectedItemPosition >= 0) {
                notifyItemChanged(selectedItemPosition);
                ChatThemeView view = selectedViewRef.get();
                if (view != null) {
                    view.setSelected(false);
                }
            }
            selectedItemPosition = position;
            notifyItemChanged(selectedItemPosition);
        }


        private class ChatThemeView extends FrameLayout implements Animator.AnimatorListener, ValueAnimator.AnimatorUpdateListener {

            private final float STROKE_RADIUS = AndroidUtilities.dp(8);
            private final float INNER_RADIUS = AndroidUtilities.dp(6);
            private final float INNER_RECT_SPACE = AndroidUtilities.dp(4);
            private final float BUBBLE_HEIGHT = AndroidUtilities.dp(21);
            private final float BUBBLE_WIDTH = AndroidUtilities.dp(41);

            private final Paint backgroundFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final Paint outBubblePaintFirst = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final Paint outBubblePaintSecond = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final Paint inBubblePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final RectF rectF = new RectF();
            private final Path clipPath = new Path();
            private final Theme.ResourcesProvider resourcesProvider;

            private ValueAnimator strokeAlphaAnimator;
            private TextPaint noThemeTextPaint;
            private StaticLayout textLayout;
            private ChatThemeItem chatThemeItem;
            private BackupImageView backupImageView;
            private boolean isDark;
            private boolean hasAnimatedEmoji;
            Runnable animationCancelRunnable;

            public ChatThemeView(Context context, Theme.ResourcesProvider resourcesProvider) {
                super(context);
                this.resourcesProvider = resourcesProvider;
                strokePaint.setStyle(Paint.Style.STROKE);
                strokePaint.setStrokeWidth(AndroidUtilities.dp(2));
                setBackgroundColor(getThemedColor(Theme.key_dialogBackgroundGray));
                backupImageView = new BackupImageView(context);
                backupImageView.getImageReceiver().setCrossfadeWithOldImage(true);
                backupImageView.getImageReceiver().setAllowStartLottieAnimation(false);
                backupImageView.getImageReceiver().setAutoRepeat(0);
                addView(backupImageView, LayoutHelper.createFrame(28, 28, Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM, 0, 0, 0, 12));
            }

            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                int height = MeasureSpec.getSize(heightMeasureSpec);
                int width = AndroidUtilities.dp(77);
                super.onMeasure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
            }

            @Override
            protected void onSizeChanged(int w, int h, int oldw, int oldh) {
                super.onSizeChanged(w, h, oldw, oldh);
                if (w == oldw && h == oldh) {
                    return;
                }
                rectF.set(INNER_RECT_SPACE, INNER_RECT_SPACE, w - INNER_RECT_SPACE, h - INNER_RECT_SPACE);
                clipPath.reset();
                clipPath.addRoundRect(rectF, INNER_RADIUS, INNER_RADIUS, Path.Direction.CW);
            }

            @Override
            protected void dispatchDraw(Canvas canvas) {
                if (chatThemeItem == null) {
                    super.dispatchDraw(canvas);
                    return;
                }
                if (chatThemeItem.isSelected || strokeAlphaAnimator != null) {
                    float rectSpace = strokePaint.getStrokeWidth() * 0.5f;
                    rectF.set(rectSpace, rectSpace, getWidth() - rectSpace, getHeight() - rectSpace);
                    canvas.drawRoundRect(rectF, STROKE_RADIUS, STROKE_RADIUS, strokePaint);
                }
                rectF.set(INNER_RECT_SPACE, INNER_RECT_SPACE, getWidth() - INNER_RECT_SPACE, getHeight() - INNER_RECT_SPACE);
                if (chatThemeItem.chatTheme == null || chatThemeItem.chatTheme.isDefault) {
                    canvas.drawRoundRect(rectF, INNER_RADIUS, INNER_RADIUS, backgroundFillPaint);
                    canvas.save();
                    StaticLayout textLayout = getNoThemeStaticLayout();
                    canvas.translate((getWidth() - textLayout.getWidth()) * 0.5f, AndroidUtilities.dp(18));
                    textLayout.draw(canvas);
                    canvas.restore();
                } else {
                    if (chatThemeItem.previewDrawable != null) {
                        canvas.save();
                        canvas.clipPath(clipPath);
                        chatThemeItem.previewDrawable.setBounds(0, 0, getWidth(), getHeight());
                        chatThemeItem.previewDrawable.draw(canvas);
                        canvas.restore();
                    } else {
                        canvas.drawRoundRect(rectF, INNER_RADIUS, INNER_RADIUS, backgroundFillPaint);
                    }
                    float bubbleTop = INNER_RECT_SPACE + AndroidUtilities.dp(8);
                    float bubbleLeft = INNER_RECT_SPACE + AndroidUtilities.dp(22);
                    rectF.set(bubbleLeft, bubbleTop, bubbleLeft + BUBBLE_WIDTH, bubbleTop + BUBBLE_HEIGHT);
                    canvas.drawRoundRect(rectF, rectF.height() * 0.5f, rectF.height() * 0.5f, outBubblePaintFirst);
                    canvas.drawRoundRect(rectF, rectF.height() * 0.5f, rectF.height() * 0.5f, outBubblePaintSecond);
                    bubbleLeft = INNER_RECT_SPACE + AndroidUtilities.dp(5);
                    bubbleTop += BUBBLE_HEIGHT + AndroidUtilities.dp(4);
                    rectF.set(bubbleLeft, bubbleTop, bubbleLeft + BUBBLE_WIDTH, bubbleTop + BUBBLE_HEIGHT);
                    canvas.drawRoundRect(rectF, rectF.height() * 0.5f, rectF.height() * 0.5f, inBubblePaint);
                }
                super.dispatchDraw(canvas);
            }


            public void setItem(ChatThemeItem item) {
                boolean itemChanged = chatThemeItem != item;
                boolean darkModeChanged = isDark != item.isDark;
                isDark = item.isDark;
                this.chatThemeItem = item;
                hasAnimatedEmoji = false;
                TLRPC.Document document = null;
                if (item.chatTheme.getEmoticon() != null) {
                    document = MediaDataController.getInstance(currentAccount).getEmojiAnimatedSticker(item.chatTheme.getEmoticon());
                }
                if (itemChanged) {
                    if (animationCancelRunnable != null) {
                        AndroidUtilities.cancelRunOnUIThread(animationCancelRunnable);
                        animationCancelRunnable = null;
                    }
                    backupImageView.animate().cancel();
                    backupImageView.setScaleX(1f);
                    backupImageView.setScaleY(1f);
                }

                backupImageView.setImage(ImageLocation.getForDocument(document), "50_50", Emoji.getEmojiDrawable(item.chatTheme == null ? "❌" : item.chatTheme.getEmoticon()), null);

                if (item.chatTheme != null && !item.chatTheme.isDefault) {
                    updatePreviewBackground();
                    if (itemChanged || darkModeChanged) {
                        final long themeId = item.chatTheme.getTlTheme(isDark).id;
                        final int intensity = item.chatTheme.getWallpaper(isDark).settings.intensity;
                        item.chatTheme.loadWallpaperThumb(isDark, result -> {
                            if (result != null && result.first == themeId) {
                                MotionBackgroundDrawable drawable = getPreviewDrawable();
                                if (drawable != null) {
                                    drawable.setPatternBitmap(intensity >= 0 ? 100 : -100, result.second);
                                    drawable.setPatternColorFilter(drawable.getPatternColor());
                                }
                                invalidate();
                            }
                        });
                    }
                }
                setBackgroundColor(0);
            }

            @Override
            public void setSelected(boolean selected) {
                super.setSelected(selected);
                if (chatThemeItem.isSelected != selected) {
                    if (strokeAlphaAnimator != null) {
                        strokeAlphaAnimator.cancel();
                    }
                    if (selected) {
                        strokePaint.setAlpha(0);
                    }
                    strokeAlphaAnimator = ValueAnimator.ofInt(selected ? 0 : 255, selected ? 255 : 0);
                    strokeAlphaAnimator.addUpdateListener(this);
                    strokeAlphaAnimator.addListener(this);
                    strokeAlphaAnimator.setDuration(350);
                    strokeAlphaAnimator.start();
                }
                chatThemeItem.isSelected = selected;
            }

            @Override
            public void setBackgroundColor(int color) {
                backgroundFillPaint.setColor(getThemedColor(Theme.key_dialogBackgroundGray));
                if (noThemeTextPaint != null) {
                    noThemeTextPaint.setColor(getThemedColor(Theme.key_chat_emojiPanelTrendingDescription));
                }
                invalidate();
            }

            private void fillOutBubblePaint(Paint paint, List<Integer> messageColors) {
                if (messageColors.size() > 1) {
                    int[] colors = new int[messageColors.size()];
                    for (int i = 0; i != messageColors.size(); ++i) {
                        colors[i] = messageColors.get(i);
                    }
                    float top = INNER_RECT_SPACE + AndroidUtilities.dp(8);
                    paint.setShader(new LinearGradient(0f, top, 0f, top + BUBBLE_HEIGHT, colors, null, Shader.TileMode.CLAMP));
                } else {
                    paint.setShader(null);
                }
            }

            public void updatePreviewBackground() {
                if (chatThemeItem == null || chatThemeItem.chatTheme == null || chatThemeItem.chatTheme.isDefault) {
                    return;
                }
                int color = chatThemeItem.isDark ? chatThemeItem.inBubbleColorDark : chatThemeItem.inBubbleColorLight;
                inBubblePaint.setColor(color);
                color = chatThemeItem.isDark ? chatThemeItem.outBubbleColorDark : chatThemeItem.outBubbleColorLight;
                outBubblePaintSecond.setColor(color);

                TLRPC.TL_theme firstTlTheme = chatThemeItem.chatTheme.getTlTheme(!chatThemeItem.isDark);
                fillOutBubblePaint(outBubblePaintFirst, firstTlTheme.settings.message_colors);
                TLRPC.TL_theme secondTlTheme = chatThemeItem.chatTheme.getTlTheme(chatThemeItem.isDark);
                fillOutBubblePaint(outBubblePaintSecond, secondTlTheme.settings.message_colors);
                outBubblePaintSecond.setAlpha(255);

                MotionBackgroundDrawable drawable = getPreviewDrawable();
                if (drawable != null) {
                    TLRPC.WallPaperSettings secondSettings = secondTlTheme.settings.wallpaper.settings;
                    int color1 = 0xff000000 | secondSettings.background_color;
                    if (color1 == 0xff000000) {
                        color1 = Color.TRANSPARENT;
                    }
                    int color2 = 0xff000000 | secondSettings.second_background_color;
                    if (color2 == 0xff000000) {
                        color2 = Color.TRANSPARENT;
                    }
                    int color3 = 0xff000000 | secondSettings.third_background_color;
                    if (color3 == 0xff000000) {
                        color3 = Color.TRANSPARENT;
                    }
                    int color4 = 0xff000000 | secondSettings.fourth_background_color;
                    if (color4 == 0xff000000) {
                        color4 = Color.TRANSPARENT;
                    }
                    drawable.setPatternBitmap(secondSettings.intensity >= 0 ? 100 : -100);
                    drawable.setColors(color1, color2, color3, color4, false);
                    drawable.setPatternColorFilter(drawable.getPatternColor());
                }
                invalidate();
            }

            private MotionBackgroundDrawable getPreviewDrawable() {
                if (chatThemeItem == null) {
                    return null;
                }
                MotionBackgroundDrawable drawable = chatThemeItem.previewDrawable;
                if (drawable == null) {
                    drawable = new MotionBackgroundDrawable();
                    chatThemeItem.previewDrawable = drawable;
                }
                return drawable;
            }

            private StaticLayout getNoThemeStaticLayout() {
                if (textLayout != null) {
                    return textLayout;
                }
                noThemeTextPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG + TextPaint.SUBPIXEL_TEXT_FLAG);
                noThemeTextPaint.setColor(getThemedColor(Theme.key_chat_emojiPanelTrendingDescription));
                noThemeTextPaint.setTextSize(AndroidUtilities.dp(14));
                noThemeTextPaint.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
                textLayout = StaticLayoutEx.createStaticLayout2(
                        LocaleController.getString("ChatNoTheme", R.string.ChatNoTheme),
                        noThemeTextPaint,
                        AndroidUtilities.dp(52),
                        Layout.Alignment.ALIGN_CENTER,
                        1f, 0f, true,
                        TextUtils.TruncateAt.END, AndroidUtilities.dp(52), 3
                );
                return textLayout;
            }

            private int getThemedColor(String key) {
                Integer color = resourcesProvider != null ? resourcesProvider.getColor(key) : null;
                return color != null ? color : Theme.getColor(key);
            }

            @Override
            public void onAnimationUpdate(ValueAnimator valueAnimator) {
                int strokeColor = chatThemeItem.chatTheme.isDefault
                        ? getThemedColor(Theme.key_featuredStickers_addButton)
                        : chatThemeItem.isDark ? chatThemeItem.strokeColorDark : chatThemeItem.strokeColorLight;
                strokePaint.setColor(strokeColor);
                strokePaint.setAlpha((int) valueAnimator.getAnimatedValue());
                invalidate();
            }

            @Override
            public void onAnimationEnd(Animator animator) {
                strokeAlphaAnimator = null;
                invalidate();
            }

            @Override
            public void onAnimationCancel(Animator animator) {
                strokeAlphaAnimator = null;
                invalidate();
            }

            @Override
            public void onAnimationStart(Animator animator) {
            }

            @Override
            public void onAnimationRepeat(Animator animator) {
            }

            public void playEmojiAnimation() {
                if (backupImageView.getImageReceiver().getLottieAnimation() != null) {
                    AndroidUtilities.cancelRunOnUIThread(animationCancelRunnable);
                    backupImageView.setVisibility(View.VISIBLE);
                    backupImageView.getImageReceiver().getLottieAnimation().setCurrentFrame(0, false);
                    backupImageView.getImageReceiver().getLottieAnimation().start();
                    backupImageView.setPivotY(AndroidUtilities.dp(24));
                    backupImageView.setPivotX(AndroidUtilities.dp(12));
                    backupImageView.animate().scaleX(2f).scaleY(2f).setDuration(300).setInterpolator(AndroidUtilities.overshootInterpolator).start();

                    AndroidUtilities.runOnUIThread(animationCancelRunnable = () -> {
                        animationCancelRunnable = null;
                        backupImageView.animate().scaleX(1f).scaleY(1f).setDuration(150).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
                    }, 2500);
                }
            }

            public void cancelAnimation() {
                if (animationCancelRunnable != null) {
                    AndroidUtilities.cancelRunOnUIThread(animationCancelRunnable);
                    animationCancelRunnable.run();
                }
            }
        }
    }

    private static class ChatThemeItem {

        public final ChatTheme chatTheme;
        public MotionBackgroundDrawable previewDrawable;
        public boolean isDark;
        public boolean isSelected;
        public float animationProgress = 1f;
        public int inBubbleColorLight;
        public int inBubbleColorDark;
        public int outBubbleColorLight;
        public int outBubbleColorDark;
        public int strokeColorDark;
        public int strokeColorLight;

        public ChatThemeItem(ChatTheme chatTheme) {
            this.chatTheme = chatTheme;
        }
    }

    @Override
    public void show() {
        super.show();
        resetTextView.setText(themeDelegate.getCurrentTheme() == null ? LocaleController.getString("DoNoSetTheme", R.string.DoNoSetTheme) : LocaleController.getString("ChatResetTheme", R.string.ChatResetTheme));
    }
}
