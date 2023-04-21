package org.telegram.ui.Components;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.PorterDuffXfermode;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.LinearSmoothScroller;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.ChatThemeController;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.ResultCallback;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BackDrawable;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.EmojiThemes;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.DrawerProfileCell;
import org.telegram.ui.Cells.ThemesHorizontalListCell;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.PhotoViewer;
import org.telegram.ui.ThemePreviewActivity;
import org.telegram.ui.WallpapersListActivity;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

public class ChatThemeBottomSheet extends BottomSheet implements NotificationCenter.NotificationCenterDelegate {

    private final ImageView backButtonView;
    private final BackDrawable backButtonDrawable;
    private TextView cancelOrResetTextView;
    private TextView themeHintTextView;
    private TLRPC.WallPaper currentWallpaper;
    private FrameLayout rootLayout;
    private final Adapter adapter;
    private final ChatActivity.ThemeDelegate themeDelegate;
    private final EmojiThemes originalTheme;
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
    private AnimatedTextView applyTextView;
    private TextView chooseBackgroundTextView;
    private ChatThemeItem selectedItem;
    private boolean forceDark;
    private boolean isApplyClicked;
    private boolean isLightDarkChangeAnimation;
    private int prevSelectedPosition = -1;
    private View changeDayNightView;
    private float changeDayNightViewProgress;
    private ValueAnimator changeDayNightViewAnimator;
    HintView hintView;
    private boolean dataLoaded;
    private EmojiThemes currentTheme;
    ThemePreviewActivity overlayFragment;
    public ChatAttachAlert chatAttachAlert;
    private FrameLayout chatAttachButton;
    private AnimatedTextView chatAttachButtonText;


    public ChatThemeBottomSheet(final ChatActivity chatActivity, ChatActivity.ThemeDelegate themeDelegate) {
        super(chatActivity.getParentActivity(), true, themeDelegate);
        this.chatActivity = chatActivity;
        this.themeDelegate = themeDelegate;
        this.originalTheme = themeDelegate.getCurrentTheme();
        this.currentWallpaper = themeDelegate.getCurrentWallpaper();
        this.originalIsDark = Theme.getActiveTheme().isDark();
        adapter = new Adapter(currentAccount, themeDelegate, ThemeSmallPreviewView.TYPE_DEFAULT);
        setDimBehind(false);
        setCanDismissWithSwipe(false);
        setApplyBottomPadding(false);
        if (Build.VERSION.SDK_INT >= 30) {
            navBarColorKey = null;
            navBarColor = getThemedColor(Theme.key_dialogBackgroundGray);
            AndroidUtilities.setNavigationBarColor(getWindow(), getThemedColor(Theme.key_dialogBackgroundGray), false);
            AndroidUtilities.setLightNavigationBar(getWindow(), AndroidUtilities.computePerceivedBrightness(navBarColor) > 0.721);
        } else {
            fixNavigationBar(getThemedColor(Theme.key_dialogBackgroundGray));
        }

        rootLayout = new FrameLayout(getContext());
        setCustomView(rootLayout);

        titleView = new TextView(getContext());
        titleView.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        titleView.setLines(1);
        titleView.setSingleLine(true);
        titleView.setText(LocaleController.getString("SelectTheme", R.string.SelectTheme));
        titleView.setTextColor(getThemedColor(Theme.key_dialogTextBlack));
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        titleView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        titleView.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(6), AndroidUtilities.dp(12), AndroidUtilities.dp(8));

        backButtonView = new ImageView(getContext());
        int padding = AndroidUtilities.dp(10);
        backButtonView.setPadding(padding, padding, padding, padding);
        backButtonDrawable = new BackDrawable(false);
        backButtonView.setImageDrawable(backButtonDrawable);
        backButtonView.setOnClickListener(v -> {
            if (hasChanges()) {
                resetToPrimaryState(true);
                updateState(true);
            } else {
                dismiss();
            }
        });
        rootLayout.addView(backButtonView, LayoutHelper.createFrame(44, 44, Gravity.TOP | Gravity.START, 4, -2, 62, 12));
        rootLayout.addView(titleView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.START, 44, 0, 62, 0));

        int drawableColor = getThemedColor(Theme.key_featuredStickers_addButton);
        int drawableSize = AndroidUtilities.dp(28);
        darkThemeDrawable = new RLottieDrawable(R.raw.sun_outline, "" + R.raw.sun_outline, drawableSize, drawableSize, false, null);
        forceDark = !Theme.getActiveTheme().isDark();
        setForceDark(Theme.getActiveTheme().isDark(), false);
        darkThemeDrawable.setAllowDecodeSingleFrame(true);
        darkThemeDrawable.setPlayInDirectionOfCustomEndFrame(true);
        darkThemeDrawable.setColorFilter(new PorterDuffColorFilter(drawableColor, PorterDuff.Mode.MULTIPLY));

        darkThemeView = new RLottieImageView(getContext()) {
            @Override
            public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(info);
                if (forceDark) {
                    info.setText(LocaleController.getString("AccDescrSwitchToDayTheme", R.string.AccDescrSwitchToDayTheme));
                } else {
                    info.setText(LocaleController.getString("AccDescrSwitchToNightTheme", R.string.AccDescrSwitchToNightTheme));
                }
            }
        };
        darkThemeView.setAnimation(darkThemeDrawable);
        darkThemeView.setScaleType(ImageView.ScaleType.CENTER);
        darkThemeView.setOnClickListener(view -> {
            if (changeDayNightViewAnimator != null) {
                return;
            }
            setupLightDarkTheme(!forceDark);
        });
        rootLayout.addView(darkThemeView, LayoutHelper.createFrame(44, 44, Gravity.TOP | Gravity.END, 0, -2, 7, 0));

        scroller = new LinearSmoothScroller(getContext()) {
            @Override
            protected int calculateTimeForScrolling(int dx) {
                return super.calculateTimeForScrolling(dx) * 6;
            }
        };
        recyclerView = new RecyclerListView(getContext());
        recyclerView.setAdapter(adapter);
        recyclerView.setDrawSelection(false);
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
            previewSelectedTheme();
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
                ThemeSmallPreviewView child = (ThemeSmallPreviewView) recyclerView.getChildAt(i);
                if (child != view) {
                    child.cancelAnimation();
                }
            }
            if (!adapter.items.get(position).chatTheme.showAsDefaultStub) {
                ((ThemeSmallPreviewView) view).playEmojiAnimation();
            }
            updateState(true);
        });

        progressView = new FlickerLoadingView(getContext(), resourcesProvider);
        progressView.setViewType(FlickerLoadingView.CHAT_THEMES_TYPE);
        progressView.setVisibility(View.VISIBLE);
        rootLayout.addView(progressView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 104, Gravity.START, 0, 44, 0, 0));

        rootLayout.addView(recyclerView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 104, Gravity.START, 0, 44, 0, 0));

        applyButton = new View(getContext());
        applyButton.setBackground(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(6), getThemedColor(Theme.key_featuredStickers_addButton), getThemedColor(Theme.key_featuredStickers_addButtonPressed)));
        applyButton.setOnClickListener((view) -> applySelectedTheme());
        rootLayout.addView(applyButton, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.START, 16, 162, 16, 16));

        chooseBackgroundTextView = new TextView(getContext());
        chooseBackgroundTextView.setEllipsize(TextUtils.TruncateAt.END);
        chooseBackgroundTextView.setGravity(Gravity.CENTER);
        chooseBackgroundTextView.setLines(1);
        chooseBackgroundTextView.setSingleLine(true);
//        SpannableStringBuilder spannableStringBuilder = new SpannableStringBuilder();
//        spannableStringBuilder
//                .append("d ")
//                .append(LocaleController.getString("ChooseBackgroundFromGallery", R.string.ChooseBackgroundFromGallery));
//        spannableStringBuilder.setSpan(new ColoredImageSpan(R.drawable.msg_button_wallpaper), 0, 1, 0);
//        chooseBackgroundTextView.setText(spannableStringBuilder);
        if (currentWallpaper == null) {
            chooseBackgroundTextView.setText(LocaleController.getString("ChooseBackgroundFromGallery", R.string.ChooseBackgroundFromGallery));
        } else {
            chooseBackgroundTextView.setText(LocaleController.getString("ChooseANewWallpaper", R.string.ChooseANewWallpaper));
        }

        chooseBackgroundTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        chooseBackgroundTextView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openGalleryForBackground();
            }
        });
        rootLayout.addView(chooseBackgroundTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.START, 16, 162, 16, 16));

        applyTextView = new AnimatedTextView(getContext(), true, true, true);
        applyTextView.getDrawable().setEllipsizeByGradient(true);
        applyTextView.adaptWidth = false;
        applyTextView.setGravity(Gravity.CENTER);
        applyTextView.setTextColor(getThemedColor(Theme.key_featuredStickers_buttonText));
        applyTextView.setTextSize(AndroidUtilities.dp(15));
        applyTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        rootLayout.addView(applyTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.START, 16, 162, 16, 16));


        if (currentWallpaper != null) {
            cancelOrResetTextView = new TextView(getContext());
            cancelOrResetTextView.setEllipsize(TextUtils.TruncateAt.END);
            cancelOrResetTextView.setGravity(Gravity.CENTER);
            cancelOrResetTextView.setLines(1);
            cancelOrResetTextView.setSingleLine(true);
            cancelOrResetTextView.setText(LocaleController.getString("RestToDefaultBackground", R.string.RestToDefaultBackground));

            cancelOrResetTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            cancelOrResetTextView.setOnClickListener(v -> {
                if (currentWallpaper != null) {
                    currentWallpaper = null;
                    dismiss();
                    ChatThemeController.getInstance(currentAccount).clearWallpaper(chatActivity.getDialogId());
                } else {
                    dismiss();
                }
            });

            rootLayout.addView(cancelOrResetTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.START, 16, 214, 16, 12));

            themeHintTextView = new TextView(getContext());
            themeHintTextView.setEllipsize(TextUtils.TruncateAt.END);
            themeHintTextView.setGravity(Gravity.CENTER);
            themeHintTextView.setLines(1);
            themeHintTextView.setSingleLine(true);
            themeHintTextView.setText(LocaleController.formatString("ChatThemeApplyHint", R.string.ChatThemeApplyHint, chatActivity.getCurrentUser().first_name));
            themeHintTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            rootLayout.addView(themeHintTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.START, 16, 214, 16, 12));
        }
        updateButtonColors();
        updateState(false);
    }

    private void updateButtonColors() {
        if (themeHintTextView != null) {
            themeHintTextView.setTextColor(getThemedColor(Theme.key_dialogTextGray));
            themeHintTextView.setBackground(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(6), Color.TRANSPARENT, ColorUtils.setAlphaComponent(getThemedColor(Theme.key_featuredStickers_addButton), (int) (0.3f * 255))));
        }
        if (cancelOrResetTextView != null) {
            cancelOrResetTextView.setTextColor(getThemedColor(Theme.key_text_RedRegular));
            cancelOrResetTextView.setBackground(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(6), Color.TRANSPARENT, ColorUtils.setAlphaComponent(getThemedColor(Theme.key_text_RedRegular), (int) (0.3f * 255))));
        }
        backButtonView.setBackground(Theme.createSelectorDrawable(ColorUtils.setAlphaComponent(getThemedColor(Theme.key_dialogTextBlack), 30), 1));
        backButtonDrawable.setColor(getThemedColor(Theme.key_dialogTextBlack));
        backButtonDrawable.setRotatedColor(getThemedColor(Theme.key_dialogTextBlack));
        backButtonView.invalidate();

        darkThemeView.setBackground(Theme.createSelectorDrawable(ColorUtils.setAlphaComponent(getThemedColor(Theme.key_featuredStickers_addButton), 30), 1));
        chooseBackgroundTextView.setTextColor(getThemedColor(Theme.key_dialogTextBlue));
        chooseBackgroundTextView.setBackground(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(6), Color.TRANSPARENT, ColorUtils.setAlphaComponent(getThemedColor(Theme.key_featuredStickers_addButton), (int) (0.3f * 255))));

    }

    private void previewSelectedTheme() {
        if (isDismissed() || isApplyClicked) {
            return;
        }
        isLightDarkChangeAnimation = false;
        chatActivity.forceDisallowApplyWallpeper = false;
        if (selectedItem.chatTheme.showAsDefaultStub) {
            themeDelegate.setCurrentTheme(null, themeDelegate.getCurrentWallpaper(), true, forceDark);
        } else {
            themeDelegate.setCurrentTheme(selectedItem.chatTheme, themeDelegate.getCurrentWallpaper(), true, forceDark);
        }
    }

    private void updateState(boolean animated) {
        if (!dataLoaded) {
            backButtonDrawable.setRotation(1f, animated);
            applyButton.setEnabled(false);
            AndroidUtilities.updateViewVisibilityAnimated(chooseBackgroundTextView, false, 0.9f, false, animated);
            AndroidUtilities.updateViewVisibilityAnimated(cancelOrResetTextView, false, 0.9f, false, animated);
            AndroidUtilities.updateViewVisibilityAnimated(applyButton, false, 1f, false, animated);
            AndroidUtilities.updateViewVisibilityAnimated(applyTextView, false, 0.9f, false, animated);
            AndroidUtilities.updateViewVisibilityAnimated(themeHintTextView, false, 0.9f, false, animated);
            AndroidUtilities.updateViewVisibilityAnimated(progressView, true, 1f, true, animated);
        } else {
            AndroidUtilities.updateViewVisibilityAnimated(progressView, false, 1f, true, animated);
            if (hasChanges()) {
                backButtonDrawable.setRotation(0, animated);
                applyButton.setEnabled(true);
                AndroidUtilities.updateViewVisibilityAnimated(chooseBackgroundTextView, false, 0.9f, false, animated);
                AndroidUtilities.updateViewVisibilityAnimated(cancelOrResetTextView, false, 0.9f, false, animated);
                AndroidUtilities.updateViewVisibilityAnimated(applyButton, true, 1f, false, animated);
                AndroidUtilities.updateViewVisibilityAnimated(applyTextView, true, 0.9f, false, animated);
                AndroidUtilities.updateViewVisibilityAnimated(themeHintTextView, true, 0.9f, false, animated);
                if (selectedItem != null && selectedItem.chatTheme != null && selectedItem.chatTheme.showAsDefaultStub && selectedItem.chatTheme.wallpaper == null) {
                    applyTextView.setText(LocaleController.getString("ChatResetTheme", R.string.ChatResetTheme));
                } else {
                    applyTextView.setText(LocaleController.getString("ChatApplyTheme", R.string.ChatApplyTheme));
                }
            } else {
                backButtonDrawable.setRotation(1f, animated);
                applyButton.setEnabled(false);
                AndroidUtilities.updateViewVisibilityAnimated(chooseBackgroundTextView, true, 0.9f, false, animated);
                AndroidUtilities.updateViewVisibilityAnimated(cancelOrResetTextView, true, 0.9f, false, animated);
                AndroidUtilities.updateViewVisibilityAnimated(applyButton, false, 1f, false, animated);
                AndroidUtilities.updateViewVisibilityAnimated(applyTextView, false, 0.9f, false, animated);
                AndroidUtilities.updateViewVisibilityAnimated(themeHintTextView, false, 0.9f, false, animated);
            }
        }
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
        List<EmojiThemes> cachedThemes = themeDelegate.getCachedThemes();
        if (cachedThemes == null || cachedThemes.isEmpty()) {
            ChatThemeController.requestAllChatThemes(new ResultCallback<List<EmojiThemes>>() {
                @Override
                public void onComplete(List<EmojiThemes> result) {
                    if (result != null && !result.isEmpty()) {
                        themeDelegate.setCachedThemes(result);
                    }
                    NotificationCenter.getInstance(currentAccount).doOnIdle(() -> {
                        onDataLoaded(result);
                    });
                }

                @Override
                public void onError(TLRPC.TL_error error) {
                    Toast.makeText(getContext(), error.text, Toast.LENGTH_SHORT).show();
                }
            }, true);
        } else {
            onDataLoaded(cachedThemes);
        }


        if (chatActivity.getCurrentUser() != null && SharedConfig.dayNightThemeSwitchHintCount > 0 && !chatActivity.getCurrentUser().self) {
            SharedConfig.updateDayNightThemeSwitchHintCount(SharedConfig.dayNightThemeSwitchHintCount - 1);
            hintView = new HintView(getContext(), 9, chatActivity.getResourceProvider());
            hintView.setVisibility(View.INVISIBLE);
            hintView.setShowingDuration(5000);
            hintView.setBottomOffset(-AndroidUtilities.dp(8));
            if (forceDark) {
                hintView.setText(AndroidUtilities.replaceTags(LocaleController.formatString("ChatThemeDaySwitchTooltip", R.string.ChatThemeDaySwitchTooltip)));
            } else {
                hintView.setText(AndroidUtilities.replaceTags(LocaleController.formatString("ChatThemeNightSwitchTooltip", R.string.ChatThemeNightSwitchTooltip)));
            }
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
        chatActivity.forceDisallowApplyWallpeper = false;
        if (!isApplyClicked) {
            themeDelegate.setCurrentTheme(originalTheme, themeDelegate.getCurrentWallpaper(), true, originalIsDark);
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
            NotificationCenter.getInstance(currentAccount).doOnIdle(() -> {
                adapter.notifyDataSetChanged();
            });
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
                darkThemeDrawable.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_featuredStickers_addButton), PorterDuff.Mode.MULTIPLY));
                setOverlayNavBarColor(getThemedColor(Theme.key_windowBackgroundGray));
                if (isLightDarkChangeAnimation) {
                    setItemsAnimationProgress(progress);
                }
                if (progress == 1f && isAnimationStarted) {
                    isLightDarkChangeAnimation = false;
                    onAnimationEnd();
                    isAnimationStarted = false;
                }
                updateButtonColors();
                if (chatAttachButton != null) {
                    chatAttachButton.setBackground(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(0), getThemedColor(Theme.key_windowBackgroundWhite), ColorUtils.setAlphaComponent(getThemedColor(Theme.key_featuredStickers_addButton), (int) (0.3f * 255))));
                }
                if (chatAttachButtonText != null) {
                    chatAttachButtonText.setTextColor(getThemedColor(Theme.key_featuredStickers_addButton));
                }
            }

            @Override
            public void didSetColor() {
            }
        };
        ArrayList<ThemeDescription> themeDescriptions = new ArrayList<>();
        if (chatActivity.forceDisallowRedrawThemeDescriptions && overlayFragment != null) {
            themeDescriptions.addAll(overlayFragment.getThemeDescriptionsInternal());
            return themeDescriptions;
        }
        if (chatAttachAlert != null) {
            themeDescriptions.addAll(chatAttachAlert.getThemeDescriptions());
        }
        themeDescriptions.add(new ThemeDescription(null, ThemeDescription.FLAG_BACKGROUNDFILTER, null, null, new Drawable[]{shadowDrawable}, descriptionDelegate, Theme.key_dialogBackground));
        themeDescriptions.add(new ThemeDescription(titleView, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_dialogTextBlack));
        themeDescriptions.add(new ThemeDescription(recyclerView, ThemeDescription.FLAG_CELLBACKGROUNDCOLOR, new Class[]{ThemeSmallPreviewView.class}, null, null, null, Theme.key_dialogBackgroundGray));
        themeDescriptions.add(new ThemeDescription(applyButton, ThemeDescription.FLAG_BACKGROUNDFILTER, null, null, null, null, Theme.key_featuredStickers_addButton));
        themeDescriptions.add(new ThemeDescription(applyButton, ThemeDescription.FLAG_BACKGROUNDFILTER | ThemeDescription.FLAG_DRAWABLESELECTEDSTATE, null, null, null, null, Theme.key_featuredStickers_addButtonPressed));
        for (ThemeDescription description : themeDescriptions) {
            description.resourcesProvider = themeDelegate;
        }

        return themeDescriptions;
    }

    @SuppressLint("NotifyDataSetChanged")
    public void setupLightDarkTheme(boolean isDark) {
        if (isDismissed()) {
            return;
        }
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
        changeDayNightView.setOnTouchListener((v, event) -> true);
        changeDayNightViewProgress = 0f;
        changeDayNightViewAnimator = ValueAnimator.ofFloat(0, 1f);
        changeDayNightViewAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            boolean changedNavigationBarColor = false;

            @Override
            public void onAnimationUpdate(ValueAnimator valueAnimator) {
                changeDayNightViewProgress = (float) valueAnimator.getAnimatedValue();
                changeDayNightView.invalidate();
                if (!changedNavigationBarColor && changeDayNightViewProgress > .5f) {
                    changedNavigationBarColor = true;
                  //  fixNavigationBar(getThemedColor(Theme.key_windowBackgroundGray));
//                    AndroidUtilities.setLightNavigationBar(getWindow(), !isDark);
//                    AndroidUtilities.setNavigationBarColor(getWindow(), );
                }
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
            if (adapter == null || adapter.items == null || isDismissed()) {
                return;
            }
            setForceDark(isDark, true);
            if (selectedItem != null) {
                isLightDarkChangeAnimation = true;
                if (selectedItem.chatTheme.showAsDefaultStub) {
                    themeDelegate.setCurrentTheme(null, themeDelegate.getCurrentWallpaper(), false, isDark);
                } else {
                    themeDelegate.setCurrentTheme(selectedItem.chatTheme, themeDelegate.getCurrentWallpaper(), false, isDark);
                }
            }
            if (adapter != null && adapter.items != null) {
                for (int i = 0; i < adapter.items.size(); i++) {
                    adapter.items.get(i).themeIndex = isDark ? 1 : 0;
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

    private void onDataLoaded(List<EmojiThemes> result) {
        if (result == null || result.isEmpty()) {
            return;
        }

        dataLoaded = true;
        ChatThemeItem noThemeItem = new ChatThemeItem(result.get(0));
        List<ChatThemeItem> items = new ArrayList<>(result.size());
        currentTheme = themeDelegate.getCurrentTheme();

        items.add(0, noThemeItem);
        selectedItem = noThemeItem;

//        if (chatActivity.getCurrentUserInfo() != null && chatActivity.getCurrentUserInfo().wallpaper != null) {
//            EmojiThemes wallpaperItem = EmojiThemes.createChatThemesDefault();
//            wallpaperItem.wallpaper = chatActivity.getCurrentUserInfo().wallpaper;
//            wallpaperItem.showAsDefaultStub = true;
//            wallpaperItem.emoji = "\uD83C\uDFA8";
//
//            for (int i = 0; i < wallpaperItem.items.size(); i++) {
//                EmojiThemes.ThemeItem item = wallpaperItem.items.get(i);
//                item.inBubbleColor = Color.WHITE;//Theme.getDefaultColor(Theme.key_chat_inBubble);
//                item.outBubbleColor = Color.GRAY;//Theme.getDefaultColor(Theme.key_chat_outBubble);
//                item.outLineColor = Color.BLACK;
//            }
//            if (currentTheme == null || currentTheme.showAsDefaultStub) {
//                currentTheme = wallpaperItem;
//            }
//            items.add(new ChatThemeItem(wallpaperItem));
//        }

        for (int i = 1; i < result.size(); ++i) {
            EmojiThemes chatTheme = result.get(i);
            ChatThemeItem item = new ChatThemeItem(chatTheme);

            chatTheme.loadPreviewColors(currentAccount);

            item.themeIndex = forceDark ? 1 : 0;
            items.add(item);
        }
        adapter.setItems(items);

        darkThemeView.setVisibility(View.VISIBLE);

        resetToPrimaryState(false);
        recyclerView.animate().alpha(1f).setDuration(150).start();
        updateState(true);
    }

    private void resetToPrimaryState(boolean animated) {
        List<ChatThemeItem> items = adapter.items;
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
                if (selectedPosition > 0 && selectedPosition < items.size() / 2) {
                    selectedPosition -= 1;
                }
                int finalSelectedPosition = Math.min(selectedPosition, adapter.items.size() - 1);
                if (animated) {
                    recyclerView.smoothScrollToPosition(finalSelectedPosition);
                } else {
                    layoutManager.scrollToPositionWithOffset(finalSelectedPosition, 0);
                }
            }
        } else {
            selectedItem = items.get(0);
            adapter.setSelectedItem(0);
            if (animated) {
                recyclerView.smoothScrollToPosition(0);
            } else {
                layoutManager.scrollToPositionWithOffset(0, 0);
            }
        }
    }

    private void onAnimationStart() {
        if (adapter != null && adapter.items != null) {
            for (ChatThemeItem item : adapter.items) {
                item.themeIndex = forceDark ? 1 : 0;
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
        if (forceDark == isDark) {
            return;
        }
        forceDark = isDark;
        if (playAnimation) {
            darkThemeDrawable.setCustomEndFrame(isDark ? darkThemeDrawable.getFramesCount() : 0);
            if (darkThemeView != null) {
                darkThemeView.playAnimation();
            }
        } else {
            int frame = isDark ? darkThemeDrawable.getFramesCount() - 1 : 0;
            darkThemeDrawable.setCurrentFrame(frame, false, true);
            darkThemeDrawable.setCustomEndFrame(frame);
            if (darkThemeView != null) {
                darkThemeView.invalidate();
            }
        }
    }

    private void setItemsAnimationProgress(float progress) {
        for (int i = 0; i < adapter.getItemCount(); ++i) {
            adapter.items.get(i).animationProgress = progress;
        }
    }

    private void applySelectedTheme() {
        Bulletin bulletin = null;
        EmojiThemes newTheme = selectedItem.chatTheme;
        if (selectedItem != null && newTheme != currentTheme) {
            EmojiThemes chatTheme = selectedItem.chatTheme;
            String emoticon = !chatTheme.showAsDefaultStub ? chatTheme.getEmoticon() : null;
            ChatThemeController.getInstance(currentAccount).setDialogTheme(chatActivity.getDialogId(), emoticon, true);
            if (!chatTheme.showAsDefaultStub) {
                themeDelegate.setCurrentTheme(chatTheme, themeDelegate.getCurrentWallpaper(), true, originalIsDark);
            } else {
                themeDelegate.setCurrentTheme(null, themeDelegate.getCurrentWallpaper(), true, originalIsDark);
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
            String oldEmoticon = currentTheme != null ? currentTheme.getEmoticon() : null;
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
    public static class Adapter extends RecyclerListView.SelectionAdapter {

        private final Theme.ResourcesProvider resourcesProvider;

        public List<ChatThemeItem> items;
        private WeakReference<ThemeSmallPreviewView> selectedViewRef;
        private int selectedItemPosition = -1;
        private final int currentAccount;
        private final int currentViewType;

        private HashMap<String, Theme.ThemeInfo> loadingThemes = new HashMap<>();
        private HashMap<Theme.ThemeInfo, String> loadingWallpapers = new HashMap<>();

        public Adapter(int currentAccount, Theme.ResourcesProvider resourcesProvider, int type) {
            this.currentViewType = type;
            this.resourcesProvider = resourcesProvider;
            this.currentAccount = currentAccount;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new RecyclerListView.Holder(new ThemeSmallPreviewView(parent.getContext(), currentAccount, resourcesProvider, currentViewType));
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            ThemeSmallPreviewView view = (ThemeSmallPreviewView) holder.itemView;
            Theme.ThemeInfo themeInfo = items.get(position).chatTheme.getThemeInfo(items.get(position).themeIndex);
            if (themeInfo != null && themeInfo.pathToFile != null && !themeInfo.previewParsed) {
                File file = new File(themeInfo.pathToFile);
                boolean fileExists = file.exists();
                if (fileExists) {
                    parseTheme(themeInfo);
                }
            }
            boolean animated = true;
            ChatThemeItem newItem = items.get(position);
            if (view.chatThemeItem == null || !view.chatThemeItem.chatTheme.getEmoticon().equals(newItem.chatTheme.getEmoticon()) || DrawerProfileCell.switchingTheme || view.lastThemeIndex != newItem.themeIndex) {
                animated = false;
            }

            view.setFocusable(true);
            view.setEnabled(true);

            view.setBackgroundColor(Theme.getColor(Theme.key_dialogBackgroundGray));
            view.setItem(newItem, animated);
            view.setSelected(position == selectedItemPosition, animated);
            if (position == selectedItemPosition) {
                selectedViewRef = new WeakReference<>(view);
            }
        }

        private boolean parseTheme(Theme.ThemeInfo themeInfo) {
            if (themeInfo == null || themeInfo.pathToFile == null) {
                return false;
            }
            boolean finished = false;
            File file = new File(themeInfo.pathToFile);
            try (FileInputStream stream = new FileInputStream(file)) {
                int currentPosition = 0;
                int idx;
                int read;
                int linesRead = 0;
                while ((read = stream.read(ThemesHorizontalListCell.bytes)) != -1) {
                    int previousPosition = currentPosition;
                    int start = 0;
                    for (int a = 0; a < read; a++) {
                        if (ThemesHorizontalListCell.bytes[a] == '\n') {
                            linesRead++;
                            int len = a - start + 1;
                            String line = new String(ThemesHorizontalListCell.bytes, start, len - 1, "UTF-8");
                            if (line.startsWith("WLS=")) {
                                String wallpaperLink = line.substring(4);
                                Uri uri = Uri.parse(wallpaperLink);
                                themeInfo.slug = uri.getQueryParameter("slug");
                                themeInfo.pathToWallpaper = new File(ApplicationLoader.getFilesDirFixed(), Utilities.MD5(wallpaperLink) + ".wp").getAbsolutePath();

                                String mode = uri.getQueryParameter("mode");
                                if (mode != null) {
                                    mode = mode.toLowerCase();
                                    String[] modes = mode.split(" ");
                                    if (modes != null && modes.length > 0) {
                                        for (int b = 0; b < modes.length; b++) {
                                            if ("blur".equals(modes[b])) {
                                                themeInfo.isBlured = true;
                                                break;
                                            }
                                        }
                                    }
                                }
                                String pattern = uri.getQueryParameter("pattern");
                                if (!TextUtils.isEmpty(pattern)) {
                                    try {
                                        String bgColor = uri.getQueryParameter("bg_color");
                                        if (!TextUtils.isEmpty(bgColor)) {
                                            themeInfo.patternBgColor = Integer.parseInt(bgColor.substring(0, 6), 16) | 0xff000000;
                                            if (bgColor.length() >= 13 && AndroidUtilities.isValidWallChar(bgColor.charAt(6))) {
                                                themeInfo.patternBgGradientColor1 = Integer.parseInt(bgColor.substring(7, 13), 16) | 0xff000000;
                                            }
                                            if (bgColor.length() >= 20 && AndroidUtilities.isValidWallChar(bgColor.charAt(13))) {
                                                themeInfo.patternBgGradientColor2 = Integer.parseInt(bgColor.substring(14, 20), 16) | 0xff000000;
                                            }
                                            if (bgColor.length() == 27 && AndroidUtilities.isValidWallChar(bgColor.charAt(20))) {
                                                themeInfo.patternBgGradientColor3 = Integer.parseInt(bgColor.substring(21), 16) | 0xff000000;
                                            }
                                        }
                                    } catch (Exception ignore) {

                                    }
                                    try {
                                        String rotation = uri.getQueryParameter("rotation");
                                        if (!TextUtils.isEmpty(rotation)) {
                                            themeInfo.patternBgGradientRotation = Utilities.parseInt(rotation);
                                        }
                                    } catch (Exception ignore) {

                                    }
                                    String intensity = uri.getQueryParameter("intensity");
                                    if (!TextUtils.isEmpty(intensity)) {
                                        themeInfo.patternIntensity = Utilities.parseInt(intensity);
                                    }
                                    if (themeInfo.patternIntensity == 0) {
                                        themeInfo.patternIntensity = 50;
                                    }
                                }
                            } else if (line.startsWith("WPS")) {
                                themeInfo.previewWallpaperOffset = currentPosition + len;
                                finished = true;
                                break;
                            } else {
                                if ((idx = line.indexOf('=')) != -1) {
                                    String key = line.substring(0, idx);
                                    if (key.equals(Theme.key_chat_inBubble) || key.equals(Theme.key_chat_outBubble) || key.equals(Theme.key_chat_wallpaper) || key.equals(Theme.key_chat_wallpaper_gradient_to1) || key.equals(Theme.key_chat_wallpaper_gradient_to2) || key.equals(Theme.key_chat_wallpaper_gradient_to3)) {
                                        String param = line.substring(idx + 1);
                                        int value;
                                        if (param.length() > 0 && param.charAt(0) == '#') {
                                            try {
                                                value = Color.parseColor(param);
                                            } catch (Exception ignore) {
                                                value = Utilities.parseInt(param);
                                            }
                                        } else {
                                            value = Utilities.parseInt(param);
                                        }
                                        switch (key) {
                                            case Theme.key_chat_inBubble:
                                                themeInfo.setPreviewInColor(value);
                                                break;
                                            case Theme.key_chat_outBubble:
                                                themeInfo.setPreviewOutColor(value);
                                                break;
                                            case Theme.key_chat_wallpaper:
                                                themeInfo.setPreviewBackgroundColor(value);
                                                break;
                                            case Theme.key_chat_wallpaper_gradient_to1:
                                                themeInfo.previewBackgroundGradientColor1 = value;
                                                break;
                                            case Theme.key_chat_wallpaper_gradient_to2:
                                                themeInfo.previewBackgroundGradientColor2 = value;
                                                break;
                                            case Theme.key_chat_wallpaper_gradient_to3:
                                                themeInfo.previewBackgroundGradientColor3 = value;
                                                break;
                                        }
                                    }
                                }
                            }
                            start += len;
                            currentPosition += len;
                        }
                    }
                    if (finished || previousPosition == currentPosition) {
                        break;
                    }
                    stream.getChannel().position(currentPosition);
                }
            } catch (Throwable e) {
                FileLog.e(e);
            }

            if (themeInfo.pathToWallpaper != null && !themeInfo.badWallpaper) {
                file = new File(themeInfo.pathToWallpaper);
                if (!file.exists()) {
                    if (!loadingWallpapers.containsKey(themeInfo)) {
                        loadingWallpapers.put(themeInfo, themeInfo.slug);
                        TLRPC.TL_account_getWallPaper req = new TLRPC.TL_account_getWallPaper();
                        TLRPC.TL_inputWallPaperSlug inputWallPaperSlug = new TLRPC.TL_inputWallPaperSlug();
                        inputWallPaperSlug.slug = themeInfo.slug;
                        req.wallpaper = inputWallPaperSlug;
                        ConnectionsManager.getInstance(themeInfo.account).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                            if (response instanceof TLRPC.TL_wallPaper) {
                                TLRPC.WallPaper wallPaper = (TLRPC.WallPaper) response;
                                String name = FileLoader.getAttachFileName(wallPaper.document);
                                if (!loadingThemes.containsKey(name)) {
                                    loadingThemes.put(name, themeInfo);
                                    FileLoader.getInstance(themeInfo.account).loadFile(wallPaper.document, wallPaper, FileLoader.PRIORITY_NORMAL, 1);
                                }
                            } else {
                                themeInfo.badWallpaper = true;
                            }
                        }));
                    }
                    return false;
                }
            }
            themeInfo.previewParsed = true;
            return true;
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
                ThemeSmallPreviewView view = selectedViewRef == null ? null : selectedViewRef.get();
                if (view != null) {
                    view.setSelected(false);
                }
            }
            selectedItemPosition = position;
            notifyItemChanged(selectedItemPosition);
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return false;
        }
    }

    private void openGalleryForBackground() {
        chatAttachAlert = new ChatAttachAlert(chatActivity.getParentActivity(), chatActivity, false, false, false, chatActivity.getResourceProvider());
        chatAttachAlert.drawNavigationBar = true;
        chatAttachAlert.setupPhotoPicker(LocaleController.getString("ChooseBackground", R.string.ChooseBackground));
        chatAttachAlert.setDelegate(new ChatAttachAlert.ChatAttachViewDelegate() {
            @Override
            public void didPressedButton(int button, boolean arg, boolean notify, int scheduleDate, boolean forceDocument) {
                try {
                    HashMap<Object, Object> photos = chatAttachAlert.getPhotoLayout().getSelectedPhotos();
                    if (!photos.isEmpty()) {
                        MediaController.PhotoEntry entry = (MediaController.PhotoEntry) photos.values().iterator().next();
                        String path;
                        if (entry.imagePath != null) {
                            path = entry.imagePath;
                        } else {
                            path = entry.path;
                        }
                        if (path != null) {
                            File currentWallpaperPath = new File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE), Utilities.random.nextInt() + ".jpg");
                            Point screenSize = AndroidUtilities.getRealScreenSize();
                            Bitmap bitmap = ImageLoader.loadBitmap(path, null, screenSize.x, screenSize.y, true);
                            FileOutputStream stream = new FileOutputStream(currentWallpaperPath);
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 87, stream);

                            ThemePreviewActivity themePreviewActivity = new ThemePreviewActivity(new WallpapersListActivity.FileWallpaper("", currentWallpaperPath, currentWallpaperPath), bitmap);
                            themePreviewActivity.setDialogId(chatActivity.getDialogId());
                            themePreviewActivity.setDelegate(() -> {
                                chatAttachAlert.dismissInternal();
                                dismiss();
                            });
                            showAsSheet(themePreviewActivity);
                        }
                    }
                } catch (Throwable e) {
                    FileLog.e(e);
                }
            }

            @Override
            public void onWallpaperSelected(Object object) {
                ThemePreviewActivity wallpaperActivity = new ThemePreviewActivity(object, null, true, false);
                wallpaperActivity.setDialogId(chatActivity.getDialogId());
                wallpaperActivity.setDelegate(() -> {
                    chatAttachAlert.dismissInternal();
                    dismiss();
                });
                showAsSheet(wallpaperActivity);
            }
        });
        chatAttachAlert.setMaxSelectedPhotos(1, false);
        chatAttachAlert.init();
        chatAttachAlert.getPhotoLayout().loadGalleryPhotos();
        chatAttachAlert.show();

        chatAttachButton = new FrameLayout(getContext()) {

            Paint paint = new Paint();

            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(48), MeasureSpec.EXACTLY));
            }

            @Override
            protected void dispatchDraw(Canvas canvas) {
                super.dispatchDraw(canvas);
                paint.setColor(getThemedColor(Theme.key_divider));
                canvas.drawRect(0, 0, getMeasuredWidth(), 1, paint);
            }
        };
        chatAttachButtonText = new AnimatedTextView(getContext(), true, true, true);
        chatAttachButtonText.setTextSize(AndroidUtilities.dp(14));
        chatAttachButtonText.setText(LocaleController.getString("SetColorAsBackground", R.string.SetColorAsBackground));
        chatAttachButtonText.setGravity(Gravity.CENTER);
        chatAttachButtonText.setTextColor(getThemedColor(Theme.key_featuredStickers_addButton));
        chatAttachButton.addView(chatAttachButtonText, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));
        chatAttachButton.setBackground(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(0), getThemedColor(Theme.key_windowBackgroundWhite), ColorUtils.setAlphaComponent(getThemedColor(Theme.key_featuredStickers_addButton), (int) (0.3f * 255))));
        chatAttachButton.setOnClickListener(v -> {
            if (chatAttachAlert.getCurrentAttachLayout() == chatAttachAlert.getPhotoLayout()) {
                chatAttachButtonText.setText(LocaleController.getString("ChooseBackgroundFromGallery", R.string.ChooseBackgroundFromGallery));
                chatAttachAlert.openColorsLayout();
                chatAttachAlert.colorsLayout.updateColors(forceDark);
            } else {
                chatAttachButtonText.setText(LocaleController.getString("SetColorAsBackground", R.string.SetColorAsBackground));
                chatAttachAlert.showLayout(chatAttachAlert.getPhotoLayout());
            }
//            WallpapersListActivity wallpapersListActivity = new WallpapersListActivity(WallpapersListActivity.TYPE_ALL, chatActivity.getDialogId());
//            chatActivity.presentFragment(wallpapersListActivity);
//            chatAttachAlert.dismiss();
//            dismiss();
        });
        chatAttachAlert.sizeNotifierFrameLayout.addView(chatAttachButton, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM));
    }

    private void fixColorsAfterAnotherWindow() {
        if (isDismissed() || isApplyClicked) {
            return;
        }
        Theme.disallowChangeServiceMessageColor = false;
        if (selectedItem.chatTheme.showAsDefaultStub) {
            themeDelegate.setCurrentTheme(null, themeDelegate.getCurrentWallpaper(), false, forceDark, true);
        } else {
            themeDelegate.setCurrentTheme(selectedItem.chatTheme, themeDelegate.getCurrentWallpaper(), false, forceDark, true);
        }
        if (chatAttachAlert != null) {
            if (chatAttachAlert.colorsLayout != null) {
                chatAttachAlert.colorsLayout.updateColors(forceDark);
            }
            chatAttachAlert.checkColors();
        }
        if (adapter != null && adapter.items != null) {
            for (int i = 0; i < adapter.items.size(); i++) {
                adapter.items.get(i).themeIndex = forceDark ? 1 : 0;
            }
            adapter.notifyDataSetChanged();
        }
    }

    private void showAsSheet(ThemePreviewActivity themePreviewActivity) {
        BaseFragment.BottomSheetParams params = new BaseFragment.BottomSheetParams();
        params.transitionFromLeft = true;
        params.allowNestedScroll = false;
        themePreviewActivity.setResourceProvider(chatActivity.getResourceProvider());
        themePreviewActivity.setOnSwitchDayNightDelegate(new ThemePreviewActivity.DayNightSwitchDelegate() {
            private Runnable fixRedraw;

            @Override
            public boolean isDark() {
                return forceDark;
            }

            @Override
            public void switchDayNight() {
                forceDark = !forceDark;
                if (selectedItem != null) {
                    isLightDarkChangeAnimation = true;
                    chatActivity.forceDisallowRedrawThemeDescriptions = true;
                    if (selectedItem.chatTheme.showAsDefaultStub) {
                        themeDelegate.setCurrentTheme(null, themeDelegate.getCurrentWallpaper(), true, forceDark);
                    } else {
                        themeDelegate.setCurrentTheme(selectedItem.chatTheme, themeDelegate.getCurrentWallpaper(), true, forceDark);
                    }
                    chatActivity.forceDisallowRedrawThemeDescriptions = false;
                }
            }
        });
        params.onOpenAnimationFinished = () -> {
            PhotoViewer.getInstance().closePhoto(false, false);
        };
        params.onPreFinished = () -> {
            fixColorsAfterAnotherWindow();
        };
        params.onDismiss = () -> {
            overlayFragment = null;
        };
        overlayFragment = themePreviewActivity;
        chatActivity.showAsSheet(themePreviewActivity, params);
    }

    public static class ChatThemeItem {

        public final EmojiThemes chatTheme;
        public Drawable previewDrawable;
        public int themeIndex;
        public boolean isSelected;
        public float animationProgress = 1f;
        public Bitmap icon;

        public ChatThemeItem(EmojiThemes chatTheme) {
            this.chatTheme = chatTheme;
        }
    }
}
