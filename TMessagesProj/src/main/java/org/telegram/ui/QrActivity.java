package org.telegram.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.Layout;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.collection.ArrayMap;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.LinearSmoothScroller;
import androidx.recyclerview.widget.RecyclerView;

import com.google.zxing.EncodeHintType;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.ChatThemeController;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SvgHelper;
import org.telegram.tgnet.ResultCallback;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBarLayout;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.EmojiThemes;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.SettingsSearchCell;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.ChatThemeBottomSheet;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.Easings;
import org.telegram.ui.Components.FlickerLoadingView;
import org.telegram.ui.Components.HideViewAfterAnimation;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.MotionBackgroundDrawable;
import org.telegram.ui.Components.RLottieDrawable;
import org.telegram.ui.Components.RLottieImageView;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.StaticLayoutEx;
import org.telegram.ui.Components.ThemeSmallPreviewView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class QrActivity extends BaseFragment {

    private static final ArrayMap<String, int[]> qrColorsMap = new ArrayMap<>();
    private static List<EmojiThemes> cachedThemes;

    static {
        qrColorsMap.put("\uD83C\uDFE0d",    new int[]{ 0xFF71B654, 0xFF2C9077, 0xFF9ABB3E, 0xFF68B55E });
        qrColorsMap.put("\uD83D\uDC25d",    new int[]{ 0xFF43A371, 0xFF8ABD4C, 0xFF9DB139, 0xFF85B950 });
        qrColorsMap.put("⛄d",              new int[]{ 0xFF66A1FF, 0xFF59B5EE, 0xFF41BAD2, 0xFF8A97FF });
        qrColorsMap.put("\uD83D\uDC8Ed",    new int[]{ 0xFF5198F5, 0xFF4BB7D2, 0xFFAD79FB, 0xFFDF86C7 });
        qrColorsMap.put("\uD83D\uDC68\u200D\uD83C\uDFEBd", new int[]{ 0xFF9AB955, 0xFF48A896, 0xFF369ADD, 0xFF5DC67B });
        qrColorsMap.put("\uD83C\uDF37d",    new int[]{ 0xFFEE8044, 0xFFE19B23, 0xFFE55D93, 0xFFCB75D7 });
        qrColorsMap.put("\uD83D\uDC9Cd",    new int[]{ 0xFFEE597E, 0xFFE35FB2, 0xFFAD69F2, 0xFFFF9257 });
        qrColorsMap.put("\uD83C\uDF84d",    new int[]{ 0xFFEC7046, 0xFFF79626, 0xFFE3761C, 0xFFF4AA2A });
        qrColorsMap.put("\uD83C\uDFAEd",    new int[]{ 0xFF19B3D2, 0xFFDC62F4, 0xFFE64C73, 0xFFECA222 });
        qrColorsMap.put("\uD83C\uDFE0n",    new int[]{ 0xFF157FD1, 0xFF4A6CF2, 0xFF1876CD, 0xFF2CA6CE });
        qrColorsMap.put("\uD83D\uDC25n",    new int[]{ 0xFF57A518, 0xFF1E7650, 0xFF6D9B17, 0xFF3FAB55 });
        qrColorsMap.put("⛄n",              new int[]{ 0xFF2B6EDA, 0xFF2F7CB6, 0xFF1DA6C9, 0xFF6B7CFF });
        qrColorsMap.put("\uD83D\uDC8En",    new int[]{ 0xFFB256B8, 0xFF6F52FF, 0xFF249AC2, 0xFF347AD5 });
        qrColorsMap.put("\uD83D\uDC68\u200D\uD83C\uDFEBn", new int[]{ 0xFF238B68, 0xFF73A163, 0xFF15AC7F, 0xFF0E8C95 });
        qrColorsMap.put("\uD83C\uDF37n",    new int[]{ 0xFFD95454, 0xFFD2770F, 0xFFCE4661, 0xFFAC5FC8 });
        qrColorsMap.put("\uD83D\uDC9Cn",    new int[]{ 0xFFD058AA, 0xFFE0743E, 0xFFD85568, 0xFFA369D3 });
        qrColorsMap.put("\uD83C\uDF84n",    new int[]{ 0xFFD6681F, 0xFFCE8625, 0xFFCE6D30, 0xFFC98A1D });
        qrColorsMap.put("\uD83C\uDFAEn",    new int[]{ 0xFFC74343, 0xFFEC7F36, 0xFF06B0F9, 0xFFA347FF });
    }

    private final ThemeResourcesProvider resourcesProvider = new ThemeResourcesProvider();
    private final EmojiThemes homeTheme = EmojiThemes.createHomeQrTheme();
    private final Rect logoRect = new Rect();
    private final ArrayMap<String, Bitmap> emojiThemeDarkIcons = new ArrayMap<>();
    private final int[] prevQrColors = new int[4];

    private ThemeListViewController themesViewController;
    private MotionBackgroundDrawable currMotionDrawable = new MotionBackgroundDrawable();
    private MotionBackgroundDrawable prevMotionDrawable;
    private MotionBackgroundDrawable tempMotionDrawable;
    private ValueAnimator patternAlphaAnimator;
    private ValueAnimator patternIntensityAnimator;

    private View backgroundView;
    private FrameLayout themeLayout;
    private BackupImageView avatarImageView;
    private QrView qrView;
    private RLottieImageView logoImageView;
    private ImageView closeImageView;

    private Bitmap emojiThemeIcon;
    private EmojiThemes currentTheme = homeTheme;
    private boolean isCurrentThemeDark;
    private long userId;
    private long chatId;
    private int prevSystemUiVisibility;
    private int selectedPosition = -1;

    public QrActivity(Bundle args) {
        super(args);
    }

    @Override
    public boolean onFragmentCreate() {
        userId = arguments.getLong("user_id");
        chatId = arguments.getLong("chat_id");
        return super.onFragmentCreate();
    }

    @Override
    public View createView(Context context) {
        homeTheme.loadPreviewColors(currentAccount);
        isCurrentThemeDark = Theme.getActiveTheme().isDark();
        actionBar.setAddToContainer(false);
        actionBar.setBackground(null);
        actionBar.setItemsColor(0xffffffff, false);

        FrameLayout rootLayout = new FrameLayout(context) {

            private boolean prevIsPortrait;

            @Override
            public boolean dispatchTouchEvent(MotionEvent ev) {
                super.dispatchTouchEvent(ev);
                return true;
            }

            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                int width = MeasureSpec.getSize(widthMeasureSpec);
                int height = MeasureSpec.getSize(heightMeasureSpec);
                boolean isPortrait = width < height;
                avatarImageView.setVisibility(isPortrait ? View.VISIBLE : View.GONE);
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                if (isPortrait) {
                    themeLayout.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(height, MeasureSpec.AT_MOST));
                    qrView.measure(MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(260), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(330), MeasureSpec.EXACTLY));
                } else {
                    themeLayout.measure(MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(256), MeasureSpec.EXACTLY), heightMeasureSpec);
                    qrView.measure(MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(260), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(310), MeasureSpec.EXACTLY));
                }
                if (prevIsPortrait != isPortrait) {
                    qrView.onSizeChanged(qrView.getMeasuredWidth(), qrView.getMeasuredHeight(), 0, 0);
                }
                prevIsPortrait = isPortrait;
            }

            @Override
            protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
                boolean isPortrait = getWidth() < getHeight();

                backgroundView.layout(0, 0, getWidth(), getHeight());

                int themeLayoutHeight = 0;
                if (themeLayout.getVisibility() == View.VISIBLE) {
                    themeLayoutHeight = themeLayout.getMeasuredHeight();
                }

                int qrLeft = isPortrait
                        ? (getWidth() - qrView.getMeasuredWidth()) / 2
                        : (getWidth() - themeLayout.getMeasuredWidth() - qrView.getMeasuredWidth()) / 2;
                int qrTop = isPortrait
                        ? (getHeight() - themeLayoutHeight - qrView.getMeasuredHeight() - AndroidUtilities.dp(48)) / 2 + AndroidUtilities.dp(48 + 4)
                        : (getHeight() - qrView.getMeasuredHeight()) / 2;
                qrView.layout(qrLeft, qrTop, qrLeft + qrView.getMeasuredWidth(), qrTop + qrView.getMeasuredHeight());

                if (isPortrait) {
                    int avatarLeft = (getWidth() - avatarImageView.getMeasuredWidth()) / 2;
                    int avatarTop = qrTop - AndroidUtilities.dp(48);
                    avatarImageView.layout(avatarLeft, avatarTop, avatarLeft + avatarImageView.getMeasuredWidth(), avatarTop + avatarImageView.getMeasuredHeight());
                }

                if (themeLayout.getVisibility() == View.VISIBLE) {
                    if (isPortrait) {
                        int themeLayoutLeft = (getWidth() - themeLayout.getMeasuredWidth()) / 2;
                        themeLayout.layout(themeLayoutLeft, bottom - themeLayoutHeight, themeLayoutLeft + themeLayout.getMeasuredWidth(), bottom);
                    } else {
                        int themeLayoutTop = (getHeight() - themeLayout.getMeasuredHeight()) / 2;
                        themeLayout.layout(right - themeLayout.getMeasuredWidth(), themeLayoutTop, right, themeLayoutTop + themeLayout.getMeasuredHeight());
                    }
                }

                logoImageView.layout(qrLeft + logoRect.left, qrTop + logoRect.top, qrLeft + logoRect.right, qrTop + logoRect.bottom);

                int closeLeft = isPortrait ? AndroidUtilities.dp(14) : AndroidUtilities.dp(17);
                int closeTop = AndroidUtilities.statusBarHeight + (isPortrait ? AndroidUtilities.dp(10) : AndroidUtilities.dp(5));
                closeImageView.layout(closeLeft, closeTop, closeLeft + closeImageView.getMeasuredWidth(), closeTop + closeImageView.getMeasuredHeight());
            }
        };

        backgroundView = new View(context) {
            @Override
            protected void onDraw(Canvas canvas) {
                if (prevMotionDrawable != null) {
                    prevMotionDrawable.setBounds(0, 0, getWidth(), getHeight());
                }
                currMotionDrawable.setBounds(0, 0, getWidth(), getHeight());

                if (prevMotionDrawable != null)
                    prevMotionDrawable.drawBackground(canvas);
                currMotionDrawable.drawBackground(canvas);
                if (prevMotionDrawable != null)
                    prevMotionDrawable.drawPattern(canvas);
                currMotionDrawable.drawPattern(canvas);
                super.onDraw(canvas);
            }
        };
        rootLayout.addView(backgroundView);

        AvatarDrawable avatarDrawable = null;
        String username = null;
        ImageLocation imageLocationSmall = null;
        ImageLocation imageLocation = null;
        if (userId != 0) {
            TLRPC.User user = getMessagesController().getUser(userId);
            if (user != null) {
                username = user.username;
                avatarDrawable = new AvatarDrawable(user);
                imageLocationSmall = ImageLocation.getForUser(user, ImageLocation.TYPE_SMALL);
                imageLocation = ImageLocation.getForUser(user, ImageLocation.TYPE_BIG);
            }
        } else if (chatId != 0) {
            TLRPC.Chat chat = getMessagesController().getChat(chatId);
            if (chat != null) {
                username = chat.username;
                avatarDrawable = new AvatarDrawable(chat);
                imageLocationSmall = ImageLocation.getForChat(chat, ImageLocation.TYPE_SMALL);
                imageLocation = ImageLocation.getForChat(chat, ImageLocation.TYPE_BIG);
            }
        }

        String link = "https://" + MessagesController.getInstance(currentAccount).linkPrefix + "/" + username;
        qrView = new QrView(context);
        qrView.setColors(0xFF71B654, 0xFF2C9077, 0xFF9ABB3E, 0xFF68B55E);
        qrView.setData(link, username);
        qrView.setCenterChangedListener((left, top, right, bottom) -> {
            logoRect.set(left, top, right, bottom);
            qrView.requestLayout();
        });
        rootLayout.addView(qrView);

        logoImageView = new RLottieImageView(context);
        logoImageView.setAutoRepeat(true);
        logoImageView.setAnimation(R.raw.qr_code_logo_2, 60, 60);
        logoImageView.playAnimation();
        rootLayout.addView(logoImageView);

        avatarImageView = new BackupImageView(context);
        avatarImageView.setRoundRadius(AndroidUtilities.dp(42));
        avatarImageView.setSize(AndroidUtilities.dp(84), AndroidUtilities.dp(84));
        rootLayout.addView(avatarImageView, LayoutHelper.createFrame(84, 84, Gravity.LEFT | Gravity.TOP));
        avatarImageView.setImage(imageLocation, "84_84", imageLocationSmall, "50_50", avatarDrawable, null, null, 0, null);

        closeImageView = new ImageView(context);
        closeImageView.setBackground(Theme.createSimpleSelectorCircleDrawable(AndroidUtilities.dp(34), 0x28000000, 0x28ffffff));
        closeImageView.setImageResource(R.drawable.ic_ab_back);
        closeImageView.setScaleType(ImageView.ScaleType.CENTER);
        closeImageView.setOnClickListener(v -> finishFragment());
        rootLayout.addView(closeImageView, LayoutHelper.createFrame(34, 34));

        emojiThemeIcon = Bitmap.createBitmap(AndroidUtilities.dp(32), AndroidUtilities.dp(32), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(emojiThemeIcon);
        AndroidUtilities.rectTmp.set(0f, 0f, emojiThemeIcon.getWidth(), emojiThemeIcon.getHeight());
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.WHITE);
        canvas.drawRoundRect(AndroidUtilities.rectTmp, AndroidUtilities.dp(5), AndroidUtilities.dp(5), paint);
        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));
        Bitmap bitmap = BitmapFactory.decodeResource(ApplicationLoader.applicationContext.getResources(), R.drawable.msg_qr_mini);
        canvas.drawBitmap(bitmap, (emojiThemeIcon.getWidth() - bitmap.getWidth()) * 0.5f, (emojiThemeIcon.getHeight() - bitmap.getHeight()) * 0.5f, paint);
        canvas.setBitmap(null);

        themesViewController = new ThemeListViewController(this, getParentActivity().getWindow()) {
            @Override
            protected void setDarkTheme(boolean isDark) {
                super.setDarkTheme(isDark);
                isCurrentThemeDark = isDark;
                onItemSelected(currentTheme, selectedPosition, false);
            }
        };
        themeLayout = themesViewController.rootLayout;

        themesViewController.onCreate();
        themesViewController.setItemSelectedListener((theme, position) -> QrActivity.this.onItemSelected(theme, position, true));
        themesViewController.titleView.setText(LocaleController.getString("QrCode", R.string.QrCode));
        themesViewController.progressView.setViewType(FlickerLoadingView.QR_TYPE);
        themesViewController.shareButton.setOnClickListener(v -> {
            themesViewController.shareButton.setClickable(false);
            performShare();
        });
        rootLayout.addView(themeLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM));

        currMotionDrawable.setIndeterminateAnimation(true);

        fragmentView = rootLayout;
        onItemSelected(currentTheme, 0, false);

        if (cachedThemes == null || cachedThemes.isEmpty()) {
            ChatThemeController.requestAllChatThemes(new ResultCallback<List<EmojiThemes>>() {
                @Override
                public void onComplete(List<EmojiThemes> result) {
                    onDataLoaded(result);
                    cachedThemes = result;
                }
                @Override
                public void onError(TLRPC.TL_error error) {
                    Toast.makeText(getParentActivity(), error.text, Toast.LENGTH_SHORT).show();
                }
            }, true);
        } else {
            onDataLoaded(cachedThemes);
        }

        prevSystemUiVisibility = getParentActivity().getWindow().getDecorView().getSystemUiVisibility();
        applyScreenSettings();
        return fragmentView;
    }

    @SuppressLint("SourceLockedOrientationActivity")
    @Override
    public void onResume() {
        super.onResume();
        applyScreenSettings();
    }

    @Override
    public void onPause() {
        restoreScreenSettings();
        super.onPause();
    }

    @Override
    public void onFragmentDestroy() {
        themesViewController.onDestroy();
        themesViewController = null;
        emojiThemeIcon.recycle();
        emojiThemeIcon = null;
        for (int i = 0; i < emojiThemeDarkIcons.size(); ++i) {
            Bitmap bitmap = emojiThemeDarkIcons.valueAt(i);
            if (bitmap != null) {
                bitmap.recycle();
            }
        }
        emojiThemeDarkIcons.clear();
        restoreScreenSettings();
        super.onFragmentDestroy();
    }

    private void applyScreenSettings() {
        if (getParentActivity() != null) {
            getParentActivity().getWindow().getDecorView().setSystemUiVisibility(prevSystemUiVisibility | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_FULLSCREEN);
        }
    }

    private void restoreScreenSettings() {
        if (getParentActivity() != null) {
            getParentActivity().getWindow().getDecorView().setSystemUiVisibility(prevSystemUiVisibility);
        }
    }

    @Override
    public int getNavigationBarColor() {
        return getThemedColor(Theme.key_windowBackgroundGray);
    }

    @Override
    public Theme.ResourcesProvider getResourceProvider() {
        return resourcesProvider;
    }

    private void onDataLoaded(List<EmojiThemes> result) {
        if (result == null || result.isEmpty() || themesViewController == null) {
            return;
        }
        result.set(0, homeTheme);
        List<ChatThemeBottomSheet.ChatThemeItem> items = new ArrayList<>(result.size());
        for (int i = 0; i < result.size(); ++i) {
            EmojiThemes chatTheme = result.get(i);
            chatTheme.loadPreviewColors(currentAccount);
            ChatThemeBottomSheet.ChatThemeItem item = new ChatThemeBottomSheet.ChatThemeItem(chatTheme);
            item.themeIndex = isCurrentThemeDark ? 1 : 0;
            item.icon = getEmojiThemeIcon(chatTheme, isCurrentThemeDark);
            items.add(item);
        }
        themesViewController.adapter.setItems(items);

        int selectedPosition = -1;
        for (int i = 0; i != items.size(); ++i) {
            if (items.get(i).chatTheme.getEmoticon().equals(currentTheme.getEmoticon())) {
                themesViewController.selectedItem = items.get(i);
                selectedPosition = i;
                break;
            }
        }
        if (selectedPosition != -1) {
            themesViewController.setSelectedPosition(selectedPosition);
        }

        themesViewController.onDataLoaded();
    }

    private Bitmap getEmojiThemeIcon(EmojiThemes theme, boolean isDark) {
        if (isDark) {
            Bitmap bitmap = emojiThemeDarkIcons.get(theme.emoji);
            if (bitmap == null) {
                bitmap = Bitmap.createBitmap(emojiThemeIcon.getWidth(), emojiThemeIcon.getHeight(), Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(bitmap);
                int[] colors = qrColorsMap.get(theme.emoji + "n");
                if (colors != null) {
                    if (tempMotionDrawable == null) {
                        tempMotionDrawable = new MotionBackgroundDrawable(0, 0, 0, 0, true);
                    }
                    tempMotionDrawable.setColors(colors[0], colors[1], colors[2], colors[3]);
                    tempMotionDrawable.setBounds(AndroidUtilities.dp(6), AndroidUtilities.dp(6), canvas.getWidth() - AndroidUtilities.dp(6), canvas.getHeight() - AndroidUtilities.dp(6));
                    tempMotionDrawable.draw(canvas);
                }
                canvas.drawBitmap(emojiThemeIcon, 0, 0, null);
                canvas.setBitmap(null);
                emojiThemeDarkIcons.put(theme.emoji, bitmap);
            }
            return bitmap;
        } else {
            return emojiThemeIcon;
        }
    }

    private void onPatternLoaded(Bitmap bitmap, int intensity, boolean withAnimation) {
        if (bitmap != null) {
            currMotionDrawable.setPatternBitmap(intensity, bitmap);
            if (patternIntensityAnimator != null) {
                patternIntensityAnimator.cancel();
            }
            if (withAnimation) {
                patternIntensityAnimator = ValueAnimator.ofFloat(0, 1f);
                patternIntensityAnimator.addUpdateListener(animator -> currMotionDrawable.setPatternAlpha((float) animator.getAnimatedValue()));
                patternIntensityAnimator.setDuration(250);
                patternIntensityAnimator.start();
            } else {
                currMotionDrawable.setPatternAlpha(1f);
            }
        }
    }

    @Override
    protected AnimatorSet onCustomTransitionAnimation(boolean isOpen, Runnable callback) {
        if (isOpen) {
            fragmentView.setAlpha(0f);
            fragmentView.setTranslationX(AndroidUtilities.dp(48));
        }

        AnimatorSet animator = new AnimatorSet();
        animator.playTogether(
            ObjectAnimator.ofFloat(fragmentView, View.TRANSLATION_X, isOpen ? 0 : AndroidUtilities.dp(48)),
            ObjectAnimator.ofFloat(fragmentView, View.ALPHA, isOpen ? 1f : 0f)
        );
        if (!isOpen)
            animator.setInterpolator(new DecelerateInterpolator(1.5f));
        else
            animator.setInterpolator(CubicBezierInterpolator.EASE_IN);
        animator.setDuration(isOpen ? 200 : 150);
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animator) {
                if (callback != null)
                    callback.run();
            }
        });
        animator.start();
        return animator;
    }

    private void onItemSelected(EmojiThemes newTheme, int position, boolean withAnimation) {
        selectedPosition = position;
        final EmojiThemes prevTheme = currentTheme;
        final boolean isDarkTheme = isCurrentThemeDark;
        currentTheme = newTheme;
        EmojiThemes.ThemeItem themeItem = currentTheme.getThemeItem(isDarkTheme ? 1 : 0);

        float duration = 1f;
        if (patternAlphaAnimator != null) {
//            from = (float) patternAlphaAnimator.getAnimatedValue();
            duration *= Math.max(.5f, 1f - (float) patternAlphaAnimator.getAnimatedValue());
            patternAlphaAnimator.cancel();
        }

        prevMotionDrawable = currMotionDrawable;
        prevMotionDrawable.setIndeterminateAnimation(false);
        prevMotionDrawable.setAlpha(255);

        currMotionDrawable = new MotionBackgroundDrawable();
        currMotionDrawable.setCallback(backgroundView);
        currMotionDrawable.setColors(themeItem.patternBgColor, themeItem.patternBgGradientColor1, themeItem.patternBgGradientColor2, themeItem.patternBgGradientColor3);
        currMotionDrawable.setParentView(backgroundView);
        currMotionDrawable.setPatternAlpha(1f);
        currMotionDrawable.setIndeterminateAnimation(true);
        if (prevMotionDrawable != null)
            currMotionDrawable.posAnimationProgress = prevMotionDrawable.posAnimationProgress;
        qrView.setPosAnimationProgress(currMotionDrawable.posAnimationProgress);

        TLRPC.WallPaper wallPaper = currentTheme.getWallpaper(isDarkTheme ? 1 : 0);
        if (wallPaper != null) {
            currMotionDrawable.setPatternBitmap(wallPaper.settings.intensity);
            final long startedLoading = SystemClock.elapsedRealtime();
            currentTheme.loadWallpaper(isDarkTheme ? 1 : 0, pair -> {
                if (pair != null && currentTheme.getTlTheme(isDarkTheme ? 1 : 0) != null) {
                    final long themeId = pair.first;
                    final Bitmap bitmap = pair.second;
                    if (themeId == currentTheme.getTlTheme(isDarkTheme ? 1 : 0).id && bitmap != null) {
                        long elapsed = SystemClock.elapsedRealtime() - startedLoading;
                        onPatternLoaded(bitmap, currMotionDrawable.getIntensity(), elapsed > 150);
                    }
                }
            });
        } else {
            currMotionDrawable.setPatternBitmap(34, SvgHelper.getBitmap(R.raw.default_pattern, backgroundView.getWidth(), backgroundView.getHeight(), Color.BLACK));
        }
        currMotionDrawable.setPatternColorFilter(currMotionDrawable.getPatternColor());

        int[] newQrColors = qrColorsMap.get(newTheme.emoji + (isDarkTheme ? "n" : "d"));
        if (withAnimation) {
            currMotionDrawable.setAlpha(255);
            currMotionDrawable.setBackgroundAlpha(0f);
            patternAlphaAnimator = ValueAnimator.ofFloat(0f, 1f);
            patternAlphaAnimator.addUpdateListener(animation -> {
                float progress = (float) animation.getAnimatedValue();
                if (prevMotionDrawable != null) {
                    prevMotionDrawable.setBackgroundAlpha(1f);
                    prevMotionDrawable.setPatternAlpha(1f - progress);
                }
                currMotionDrawable.setBackgroundAlpha(progress);
                currMotionDrawable.setPatternAlpha(progress);
//                currMotionDrawable.setAlpha((int) (255f * progress));
                if (newQrColors != null) {
                    int color1 = ColorUtils.blendARGB(prevQrColors[0], newQrColors[0], progress);
                    int color2 = ColorUtils.blendARGB(prevQrColors[1], newQrColors[1], progress);
                    int color3 = ColorUtils.blendARGB(prevQrColors[2], newQrColors[2], progress);
                    int color4 = ColorUtils.blendARGB(prevQrColors[3], newQrColors[3], progress);
                    qrView.setColors(color1, color2, color3, color4);
                }
                backgroundView.invalidate();
            });
            patternAlphaAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    super.onAnimationEnd(animation);
                    if (newQrColors != null) {
                        System.arraycopy(newQrColors, 0, prevQrColors, 0, 4);
                    }
                    prevMotionDrawable = null;
                    patternAlphaAnimator = null;
                    currMotionDrawable.setBackgroundAlpha(1f);
                    currMotionDrawable.setPatternAlpha(1f);
                }

                @Override
                public void onAnimationCancel(Animator animation) {
                    super.onAnimationCancel(animation);
                    float progress = (float) ((ValueAnimator) animation).getAnimatedValue();
                    if (newQrColors != null) {
                        int color1 = ColorUtils.blendARGB(prevQrColors[0], newQrColors[0], progress);
                        int color2 = ColorUtils.blendARGB(prevQrColors[1], newQrColors[1], progress);
                        int color3 = ColorUtils.blendARGB(prevQrColors[2], newQrColors[2], progress);
                        int color4 = ColorUtils.blendARGB(prevQrColors[3], newQrColors[3], progress);
                        int[] colors = new int[] { color1, color2, color3, color4 };
                        System.arraycopy(colors, 0, prevQrColors, 0, 4);
                    }
                }
            });
            patternAlphaAnimator.setDuration((int) (250 * duration));
            patternAlphaAnimator.start();
        } else {
            if (newQrColors != null) {
                qrView.setColors(newQrColors[0], newQrColors[1], newQrColors[2], newQrColors[3]);
                System.arraycopy(newQrColors, 0, prevQrColors, 0, 4);
            }
            prevMotionDrawable = null;
            backgroundView.invalidate();
        }

        Theme.ThemeInfo currentThemeInfo = isCurrentThemeDark ? Theme.getCurrentNightTheme() : Theme.getCurrentTheme();
        ActionBarLayout.ThemeAnimationSettings animationSettings = new ActionBarLayout.ThemeAnimationSettings(null, currentThemeInfo.currentAccentId, isCurrentThemeDark, !withAnimation);
        animationSettings.applyTheme = false;
        animationSettings.onlyTopFragment = true;
        animationSettings.resourcesProvider = getResourceProvider();
        animationSettings.duration = (int) (250 * duration);
        if (withAnimation) {
            resourcesProvider.initColors(prevTheme, isCurrentThemeDark);
        } else {
            resourcesProvider.initColors(currentTheme, isCurrentThemeDark);
        }
        animationSettings.afterStartDescriptionsAddedRunnable = () -> {
            resourcesProvider.initColors(currentTheme, isCurrentThemeDark);
        };
        parentLayout.animateThemedValues(animationSettings);
    }

    private void performShare() {
        int width = Math.min(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y);
        int height = Math.max(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y);
        if (height * 1f / width > 1.92f) {
            height = (int)(width * 1.92f);
        }
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        themeLayout.setVisibility(View.GONE);
        closeImageView.setVisibility(View.GONE);
        logoImageView.stopAnimation();
        RLottieDrawable drawable = logoImageView.getAnimatedDrawable();
        int currentFrame = drawable.getCurrentFrame();
        drawable.setCurrentFrame(33, false);

        fragmentView.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        fragmentView.layout(0, 0, width, height);
        fragmentView.draw(canvas);
        canvas.setBitmap(null);

        themeLayout.setVisibility(View.VISIBLE);
        closeImageView.setVisibility(View.VISIBLE);
        drawable.setCurrentFrame(currentFrame, false);
        logoImageView.playAnimation();

        ViewGroup parent = (ViewGroup) fragmentView.getParent();
        fragmentView.layout(0, 0, parent.getWidth(), parent.getHeight());

        Uri uri = AndroidUtilities.getBitmapShareUri(bitmap, "qr_tmp.jpg", Bitmap.CompressFormat.JPEG);
        if (uri != null) {
            Intent intent = new Intent(Intent.ACTION_SEND)
                    .setType("image/*")
                    .putExtra(Intent.EXTRA_STREAM, uri);
            try {
                Intent chooserIntent = Intent.createChooser(intent, LocaleController.getString("InviteByQRCode", R.string.InviteByQRCode));
                getParentActivity().startActivityForResult(chooserIntent, 500);
            } catch (ActivityNotFoundException ex) {
                ex.printStackTrace();
            }
        }
        AndroidUtilities.runOnUIThread(() -> themesViewController.shareButton.setClickable(true), 500);
    }

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        ArrayList<ThemeDescription> themeDescriptions = super.getThemeDescriptions();
        themeDescriptions.addAll(themesViewController.getThemeDescriptions());
        ThemeDescription.ThemeDescriptionDelegate delegate = () -> setNavigationBarColor(getThemedColor(Theme.key_windowBackgroundGray));
        themeDescriptions.add(new ThemeDescription(themesViewController.shareButton, ThemeDescription.FLAG_BACKGROUNDFILTER, null, null, null, delegate, Theme.key_featuredStickers_addButton));
        themeDescriptions.add(new ThemeDescription(themesViewController.shareButton, ThemeDescription.FLAG_BACKGROUNDFILTER | ThemeDescription.FLAG_DRAWABLESELECTEDSTATE, null, null, null, null, Theme.key_featuredStickers_addButtonPressed));
        for (ThemeDescription description : themeDescriptions) {
            description.resourcesProvider = getResourceProvider();
        }
        return themeDescriptions;
    }


    private class ThemeResourcesProvider implements Theme.ResourcesProvider {

        private HashMap<String, Integer> colors;

        void initColors(EmojiThemes theme, boolean isDark) {
            colors = theme.createColors(currentAccount, isDark ? 1 : 0);
        }

        @Override
        public Integer getColor(String key) {
            return colors != null ? colors.get(key) : null;
        }
    }


    private static class QrView extends View {

        private static final float SHADOW_SIZE = AndroidUtilities.dp(2);
        private static final float RADIUS = AndroidUtilities.dp(20);

        private final MotionBackgroundDrawable gradientDrawable = new MotionBackgroundDrawable();
        private final Paint bitmapGradientPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final BitmapShader gradientShader;
        private QrCenterChangedListener centerChangedListener;
        private Bitmap backgroundBitmap;
        private Bitmap contentBitmap;
        private String username;
        private String link;

        QrView(Context context) {
            super(context);
            gradientDrawable.setIndeterminateAnimation(true);
            gradientDrawable.setParentView(this);
            gradientShader = new BitmapShader(gradientDrawable.getBitmap(), Shader.TileMode.MIRROR, Shader.TileMode.MIRROR);
            bitmapGradientPaint.setShader(gradientShader);
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            if (w != oldw || h != oldh) {
                if (backgroundBitmap != null) {
                    backgroundBitmap.recycle();
                    backgroundBitmap = null;
                }
                Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                backgroundPaint.setColor(Color.WHITE);
                backgroundPaint.setShadowLayer(AndroidUtilities.dp(4), 0f, SHADOW_SIZE, 0x0F000000);
                backgroundBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(backgroundBitmap);
                RectF rect = new RectF(SHADOW_SIZE, SHADOW_SIZE, w - SHADOW_SIZE, getHeight() - SHADOW_SIZE);
                canvas.drawRoundRect(rect, RADIUS, RADIUS, backgroundPaint);
                prepareContent(w, h);

                float xScale = getWidth() * 1f / gradientDrawable.getBitmap().getWidth();
                float yScale = getHeight() * 1f / gradientDrawable.getBitmap().getHeight();
                float maxScale = Math.max(xScale, yScale);
                Matrix matrix = new Matrix();
                matrix.setScale(maxScale, maxScale);
                gradientShader.setLocalMatrix(matrix);
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (backgroundBitmap != null) {
                canvas.drawBitmap(backgroundBitmap, 0f, 0f, null);
            }
            if (contentBitmap != null) {
                canvas.drawBitmap(contentBitmap, 0f, 0f, bitmapGradientPaint);
                gradientDrawable.updateAnimation(true);
            }
        }

        void setCenterChangedListener(QrCenterChangedListener centerChangedListener) {
            this.centerChangedListener = centerChangedListener;
        }

        void setData(String link, String username) {
            this.username = username;
            this.link = link;
            prepareContent(getWidth(), getHeight());
            invalidate();
        }

        void setColors(int c1, int c2, int c3, int c4) {
            gradientDrawable.setColors(c1, c2, c3, c4);
            invalidate();
        }

        void setPosAnimationProgress(float progress) {
            gradientDrawable.posAnimationProgress = progress;
        }

        private void prepareContent(int w, int h) {
            if (TextUtils.isEmpty(username) || TextUtils.isEmpty(link) || w == 0 || h == 0) {
                return;
            }

            if (contentBitmap != null) {
                contentBitmap.recycle();
            }
            contentBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);

            int qrColor = 0xff000000;
            int backgroundColor = 0x00ffffff;
            TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.LINEAR_TEXT_FLAG);
            textPaint.setColor(qrColor);
            textPaint.setTypeface(AndroidUtilities.getTypeface("fonts/rcondensedbold.ttf"));
            StaticLayout staticLayout = null;
            Drawable drawable;
            int attemptsCount = 2;
            final int textMaxWidth = contentBitmap.getWidth() - AndroidUtilities.dp(20) * 2;
            for (int i = 0; i <= attemptsCount; ++i) {
                if (i == 0) {
                    drawable = ContextCompat.getDrawable(getContext(), R.drawable.qr_at_large);
                    textPaint.setTextSize(AndroidUtilities.dp(30));
                } else if (i == 1) {
                    drawable = ContextCompat.getDrawable(getContext(), R.drawable.qr_at_medium);
                    textPaint.setTextSize(AndroidUtilities.dp(25));
                } else {
                    drawable = ContextCompat.getDrawable(getContext(), R.drawable.qr_at_small);
                    textPaint.setTextSize(AndroidUtilities.dp(19));
                }
                if (drawable != null) {
                    drawable.setBounds(0, 0, drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight());
                    drawable.setColorFilter(new PorterDuffColorFilter(qrColor, PorterDuff.Mode.SRC_IN));
                }

                SpannableStringBuilder string = new SpannableStringBuilder(" " + username.toUpperCase());
                string.setSpan(new SettingsSearchCell.VerticalImageSpan(drawable), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                float textWidth = textPaint.measureText(string, 1, string.length()) + drawable.getBounds().width();
                if (i <= 1 && textWidth > textMaxWidth) {
                    continue;
                }
                int linesCount = textWidth > textMaxWidth ? 2 : 1;
                int layoutWidth = textMaxWidth;
                if (linesCount > 1) {
                    layoutWidth = (int)(textWidth + drawable.getBounds().width()) / 2 + AndroidUtilities.dp(2);
                }
                if (layoutWidth > textMaxWidth) {
                    linesCount = 3;
                    layoutWidth = (int)(textWidth + drawable.getBounds().width()) / 3 + AndroidUtilities.dp(4);
                }
                staticLayout = StaticLayoutEx.createStaticLayout(string, textPaint, layoutWidth, Layout.Alignment.ALIGN_CENTER, 1f, 0f, false, null, Math.min(layoutWidth + AndroidUtilities.dp(10), contentBitmap.getWidth()), linesCount);

                break;
            }
            float lineHeight = textPaint.descent() - textPaint.ascent();
            float textHeight = lineHeight * staticLayout.getLineCount();

            Bitmap qrBitmap = null;
            int imageSize = 0;
            int qrBitmapSize = w - AndroidUtilities.dp(30) * 2;
            HashMap<EncodeHintType, Object> hints = new HashMap<>();
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
            hints.put(EncodeHintType.MARGIN, 0);
            QRCodeWriter writer = new QRCodeWriter();
            int version;
            for (version = 3; version < 5; ++version) {
                try {
                    hints.put(EncodeHintType.QR_VERSION, version);
                    qrBitmap = writer.encode(link, qrBitmapSize, qrBitmapSize, hints, null, 0.75f, backgroundColor, qrColor);
                    imageSize = writer.getImageSize();
                } catch (Exception e) {
                    // ignore
                }
                if (qrBitmap != null) {
                    break;
                }
            }
            if (qrBitmap == null) {
                return;
            }

            Canvas canvas = new Canvas(contentBitmap);
            canvas.drawColor(backgroundColor);

            float left = (w - qrBitmap.getWidth()) / 2f;
            float qrTop = h * 0.15f;
            if (staticLayout.getLineCount() == 3) {
                qrTop = h * 0.13f;
            }
            boolean isPortrait = ((ViewGroup) getParent()).getMeasuredWidth() < ((ViewGroup) getParent()).getMeasuredHeight();
            if (!isPortrait) {
                qrTop = h * 0.09f;
            }
            canvas.drawBitmap(qrBitmap, left, qrTop, new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG));

            Paint circlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            circlePaint.setColor(qrColor);
            float xCenter = left + qrBitmap.getWidth() * 0.5f;
            float yCenter = qrTop + qrBitmap.getWidth() * 0.5f;
            canvas.drawCircle(xCenter, yCenter, imageSize * 0.5f, circlePaint);
            if (centerChangedListener != null) {
                centerChangedListener.onCenterChanged((int) (xCenter - imageSize * 0.5f), (int) (yCenter - imageSize * 0.5f), (int) (xCenter + imageSize * 0.5f), (int) (yCenter + imageSize * 0.5f));
            }

            float xTranslate = (canvas.getWidth() - staticLayout.getWidth()) * 0.5f;
            float yTranslate = qrTop + qrBitmap.getHeight() + (canvas.getHeight() - (qrTop + qrBitmap.getHeight()) - textHeight) * 0.5f - AndroidUtilities.dp(4);
            canvas.save();
            canvas.translate(xTranslate, yTranslate);
            staticLayout.draw(canvas);
            canvas.restore();
            qrBitmap.recycle();

            Bitmap oldBitmap = contentBitmap;
            contentBitmap = contentBitmap.extractAlpha();
            oldBitmap.recycle();
        }

        public interface QrCenterChangedListener {

            void onCenterChanged(int left, int top, int right, int bottom);
        }
    }


    private class ThemeListViewController implements NotificationCenter.NotificationCenterDelegate {

        private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        public final ChatThemeBottomSheet.Adapter adapter;
        private final LinearSmoothScroller scroller;
        private final BaseFragment fragment;
        private final Window window;
        private final Drawable backgroundDrawable;

        public final FrameLayout rootLayout;
        public final TextView titleView;
        public final FlickerLoadingView progressView;
        public final TextView shareButton;
        private final RecyclerListView recyclerView;
        private final RLottieDrawable darkThemeDrawable;
        private final RLottieImageView darkThemeView;
        private LinearLayoutManager layoutManager;
        private final View topShadow;
        private final View bottomShadow;

        private OnItemSelectedListener itemSelectedListener;
        public ChatThemeBottomSheet.ChatThemeItem selectedItem;
        public int prevSelectedPosition = -1;
        private boolean forceDark;
        private ValueAnimator changeDayNightViewAnimator;
        private View changeDayNightView;
        private float changeDayNightViewProgress;
        protected boolean isLightDarkChangeAnimation;
        private boolean prevIsPortrait;

        public ThemeListViewController(BaseFragment fragment, Window window) {
            this.fragment = fragment;
            this.window = window;

            Context context = fragment.getParentActivity();
            scroller = new LinearSmoothScroller(context) {
                @Override
                protected int calculateTimeForScrolling(int dx) {
                    return super.calculateTimeForScrolling(dx) * 6;
                }
            };

            backgroundDrawable = context.getResources().getDrawable(R.drawable.sheet_shadow_round).mutate();
            backgroundDrawable.setColorFilter(new PorterDuffColorFilter(fragment.getThemedColor(Theme.key_dialogBackground), PorterDuff.Mode.MULTIPLY));
            rootLayout = new FrameLayout(context) {

                private final Rect backgroundPadding = new Rect();

                {
                    backgroundPaint.setColor(fragment.getThemedColor(Theme.key_windowBackgroundWhite));
                    backgroundDrawable.setCallback(this);
                    backgroundDrawable.getPadding(backgroundPadding);
                    setPadding(0, backgroundPadding.top + AndroidUtilities.dp(8), 0, backgroundPadding.bottom);
                }

                @Override
                protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                    boolean isPortrait = AndroidUtilities.displaySize.x < AndroidUtilities.displaySize.y;
                    int recyclerPadding = AndroidUtilities.dp(12);
                    if (isPortrait) {
                        recyclerView.setLayoutParams(LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 104, Gravity.START, 0, 44, 0, 0));
                        recyclerView.setPadding(recyclerPadding, 0, recyclerPadding, 0);
                        shareButton.setLayoutParams(LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.START, 16, 162, 16, 16));
                    } else {
                        recyclerView.setLayoutParams(LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.START, 0, 44, 0, 80));
                        recyclerView.setPadding(recyclerPadding, recyclerPadding / 2, recyclerPadding, recyclerPadding);
                        shareButton.setLayoutParams(LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.BOTTOM, 16, 0, 16, 16));
                    }
                    if (isPortrait) {
                        bottomShadow.setVisibility(View.GONE);
                        topShadow.setVisibility(View.GONE);
                    } else {
                        bottomShadow.setVisibility(View.VISIBLE);
                        bottomShadow.setLayoutParams(LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, AndroidUtilities.dp(2), Gravity.BOTTOM, 0, 0, 0, 80));
                        topShadow.setVisibility(View.VISIBLE);
                        topShadow.setLayoutParams(LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, AndroidUtilities.dp(2), Gravity.TOP, 0, 44, 0, 0));
                    }
                    if (prevIsPortrait != isPortrait) {
                        recyclerView.setLayoutManager(layoutManager = getLayoutManager(isPortrait));
                        recyclerView.requestLayout();
                        if (prevSelectedPosition != -1) {
                            setSelectedPosition(prevSelectedPosition);
                        }
                        prevIsPortrait = isPortrait;
                    }
                    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                }

                @Override
                protected void dispatchDraw(Canvas canvas) {
                    if (prevIsPortrait) {
                        backgroundDrawable.setBounds(-backgroundPadding.left, 0, getWidth() + backgroundPadding.right, getHeight());
                        backgroundDrawable.draw(canvas);
                    } else {
                        AndroidUtilities.rectTmp.set(0, 0, getWidth() + AndroidUtilities.dp(14), getHeight());
                        canvas.drawRoundRect(AndroidUtilities.rectTmp, AndroidUtilities.dp(14), AndroidUtilities.dp(14), backgroundPaint);
                    }
                    super.dispatchDraw(canvas);
                }

                @Override
                protected boolean verifyDrawable(@NonNull Drawable who) {
                    return who == backgroundDrawable || super.verifyDrawable(who);
                }
            };

            titleView = new TextView(context);
            titleView.setEllipsize(TextUtils.TruncateAt.MIDDLE);
            titleView.setLines(1);
            titleView.setSingleLine(true);
            titleView.setTextColor(fragment.getThemedColor(Theme.key_dialogTextBlack));
            titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
            titleView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            titleView.setPadding(AndroidUtilities.dp(21), AndroidUtilities.dp(6), AndroidUtilities.dp(21), AndroidUtilities.dp(8));
            rootLayout.addView(titleView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.START, 0, 0, 62, 0));

            int drawableColor = fragment.getThemedColor(Theme.key_featuredStickers_addButton);
            int drawableSize = AndroidUtilities.dp(28);
            darkThemeDrawable = new RLottieDrawable(R.raw.sun_outline, "" + R.raw.sun_outline, drawableSize, drawableSize, true, null);
            darkThemeDrawable.setPlayInDirectionOfCustomEndFrame(true);
            darkThemeDrawable.beginApplyLayerColors();
            setDarkButtonColor(drawableColor);
            darkThemeDrawable.commitApplyLayerColors();

            darkThemeView = new RLottieImageView(context);
            darkThemeView.setAnimation(darkThemeDrawable);
            darkThemeView.setScaleType(ImageView.ScaleType.CENTER);
            darkThemeView.setOnClickListener(view -> {
                if (changeDayNightViewAnimator != null) {
                    return;
                }
                setupLightDarkTheme(!forceDark);
            });
            darkThemeView.setAlpha(0f);
            darkThemeView.setVisibility(View.INVISIBLE);
            rootLayout.addView(darkThemeView, LayoutHelper.createFrame(44, 44, Gravity.TOP | Gravity.END, 0, -2, 7, 0));
            forceDark = !Theme.getActiveTheme().isDark();
            setForceDark(Theme.getActiveTheme().isDark(), false);

            progressView = new FlickerLoadingView(context, fragment.getResourceProvider());
            progressView.setVisibility(View.VISIBLE);
            rootLayout.addView(progressView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 104, Gravity.START, 0, 44, 0, 0));

            prevIsPortrait = AndroidUtilities.displaySize.x < AndroidUtilities.displaySize.y;
            recyclerView = new RecyclerListView(context);
            recyclerView.setAdapter(adapter = new ChatThemeBottomSheet.Adapter(currentAccount, resourcesProvider, ThemeSmallPreviewView.TYPE_QR));
            recyclerView.setClipChildren(false);
            recyclerView.setClipToPadding(false);
            recyclerView.setItemAnimator(null);
            recyclerView.setNestedScrollingEnabled(false);
            recyclerView.setLayoutManager(layoutManager = getLayoutManager(prevIsPortrait));
            recyclerView.setOnItemClickListener(this::onItemClicked);
            recyclerView.setOnScrollListener(new RecyclerView.OnScrollListener() {
                private int yScroll = 0;
                @Override
                public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                    super.onScrolled(recyclerView, dx, dy);
                    yScroll += dy;
                    topShadow.setAlpha(yScroll * 1f / AndroidUtilities.dp(6));
                }
            });
            rootLayout.addView(recyclerView);

            topShadow = new View(context);
            topShadow.setAlpha(0f);
            topShadow.setBackground(ContextCompat.getDrawable(context, R.drawable.shadowdown));
            topShadow.setRotation(180);
            rootLayout.addView(topShadow);

            bottomShadow = new View(context);
            bottomShadow.setBackground(ContextCompat.getDrawable(context, R.drawable.shadowdown));
            rootLayout.addView(bottomShadow);

            shareButton = new TextView(context);
            shareButton.setBackground(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(6), fragment.getThemedColor(Theme.key_featuredStickers_addButton), fragment.getThemedColor(Theme.key_featuredStickers_addButtonPressed)));
            shareButton.setEllipsize(TextUtils.TruncateAt.END);
            shareButton.setGravity(Gravity.CENTER);
            shareButton.setLines(1);
            shareButton.setSingleLine(true);
            shareButton.setText(LocaleController.getString("ShareQrCode", R.string.ShareQrCode));
            shareButton.setTextColor(fragment.getThemedColor(Theme.key_featuredStickers_buttonText));
            shareButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            shareButton.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            rootLayout.addView(shareButton);
        }

        public void onCreate() {
            ChatThemeController.preloadAllWallpaperThumbs(true);
            ChatThemeController.preloadAllWallpaperThumbs(false);
            ChatThemeController.preloadAllWallpaperImages(true);
            ChatThemeController.preloadAllWallpaperImages(false);
            NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.emojiLoaded);
        }

        @SuppressLint("NotifyDataSetChanged")
        @Override
        public void didReceivedNotification(int id, int account, Object... args) {
            if (id == NotificationCenter.emojiLoaded) {
                adapter.notifyDataSetChanged();
            }
        }

        public void onDestroy() {
            NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.emojiLoaded);
        }

        public void setItemSelectedListener(OnItemSelectedListener itemSelectedListener) {
            this.itemSelectedListener = itemSelectedListener;
        }

        public void onDataLoaded() {
            darkThemeView.setAlpha(0f);
            darkThemeView.animate().alpha(1f).setDuration(150).start();
            darkThemeView.setVisibility(View.VISIBLE);
            progressView.animate().alpha(0f).setListener(new HideViewAfterAnimation(progressView)).setDuration(150).start();
            recyclerView.setAlpha(0f);
            recyclerView.animate().alpha(1f).setDuration(150).start();
        }

        public void setSelectedPosition(int selectedPosition) {
            prevSelectedPosition = selectedPosition;
            adapter.setSelectedItem(selectedPosition);
            if (selectedPosition > 0 && selectedPosition < adapter.items.size() / 2) {
                selectedPosition -= 1;
            }
            int finalSelectedPosition = Math.min(selectedPosition, adapter.items.size() - 1);
            layoutManager.scrollToPositionWithOffset(finalSelectedPosition, 0);
        }

        protected void onItemClicked(View view, int position) {
            if (adapter.items.get(position) == selectedItem || changeDayNightView != null) {
                return;
            }
            isLightDarkChangeAnimation = false;
            selectedItem = adapter.items.get(position);
            adapter.setSelectedItem(position);
            rootLayout.postDelayed(() -> {
                RecyclerView.LayoutManager layoutManager = recyclerView.getLayoutManager();
                if (layoutManager != null) {
                    final int targetPosition = position > prevSelectedPosition
                            ? Math.min(position + 1, adapter.items.size() - 1)
                            : Math.max(position - 1, 0);
                    scroller.setTargetPosition(targetPosition);
                    layoutManager.startSmoothScroll(scroller);
                }
                prevSelectedPosition = position;
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
            if (itemSelectedListener != null) {
                itemSelectedListener.onItemSelected(selectedItem.chatTheme, position);
            }
        }

        @SuppressLint("NotifyDataSetChanged")
        private void setupLightDarkTheme(boolean isDark) {
            if (changeDayNightViewAnimator != null) {
                changeDayNightViewAnimator.cancel();
            }
            FrameLayout decorView1 = (FrameLayout) fragment.getParentActivity().getWindow().getDecorView();
            FrameLayout decorView2 = (FrameLayout) window.getDecorView();
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
            changeDayNightView = new View(fragment.getParentActivity()) {
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
            changeDayNightViewAnimator.addUpdateListener(valueAnimator -> {
                changeDayNightViewProgress = (float) valueAnimator.getAnimatedValue();
                changeDayNightView.invalidate();
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
                    setDarkTheme(isDark);
                }
                if (adapter.items != null) {
                    for (int i = 0; i < adapter.items.size(); i++) {
                        adapter.items.get(i).themeIndex = isDark ? 1 : 0;
                        adapter.items.get(i).icon = getEmojiThemeIcon(adapter.items.get(i).chatTheme, isDark);
                    }
                    tempMotionDrawable = null;
                    adapter.notifyDataSetChanged();
                }
            });
        }

        protected void setDarkTheme(boolean isDark) { }

        public void setForceDark(boolean isDark, boolean playAnimation) {
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

        public void setDarkButtonColor(int color) {
            darkThemeDrawable.setLayerColor("Sunny.**", color);
            darkThemeDrawable.setLayerColor("Path.**", color);
            darkThemeDrawable.setLayerColor("Path 10.**", color);
            darkThemeDrawable.setLayerColor("Path 11.**", color);
        }

        private LinearLayoutManager getLayoutManager(boolean isPortrait) {
            return isPortrait
                    ? new LinearLayoutManager(fragment.getParentActivity(), LinearLayoutManager.HORIZONTAL, false)
                    : new GridLayoutManager(fragment.getParentActivity(), 3, LinearLayoutManager.VERTICAL, false);
        }

        private void onAnimationStart() {
            if (adapter != null && adapter.items != null) {
                for (ChatThemeBottomSheet.ChatThemeItem item : adapter.items) {
                    item.themeIndex = forceDark ? 1 : 0;
                }
            }
            if (!isLightDarkChangeAnimation) {
                setItemsAnimationProgress(1.0f);
            }
        }

        private void setItemsAnimationProgress(float progress) {
            for (int i = 0; i < adapter.getItemCount(); ++i) {
                adapter.items.get(i).animationProgress = progress;
            }
        }

        private void onAnimationEnd() {
            isLightDarkChangeAnimation = false;
        }

        public ArrayList<ThemeDescription> getThemeDescriptions() {
            ThemeDescription.ThemeDescriptionDelegate descriptionDelegate = new ThemeDescription.ThemeDescriptionDelegate() {

                private boolean isAnimationStarted = false;

                @Override
                public void onAnimationProgress(float progress) {
                    if (progress == 0f && !isAnimationStarted) {
                        onAnimationStart();
                        isAnimationStarted = true;
                    }
                    setDarkButtonColor(fragment.getThemedColor(Theme.key_featuredStickers_addButton));
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
                public void didSetColor() {}
            };
            ArrayList<ThemeDescription> themeDescriptions = new ArrayList<>();
            themeDescriptions.add(new ThemeDescription(null, ThemeDescription.FLAG_BACKGROUND, null, backgroundPaint, null, null, Theme.key_dialogBackground));
            themeDescriptions.add(new ThemeDescription(null, ThemeDescription.FLAG_BACKGROUNDFILTER, null, null, new Drawable[]{ backgroundDrawable }, descriptionDelegate, Theme.key_dialogBackground));
            themeDescriptions.add(new ThemeDescription(titleView, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_dialogTextBlack));
            themeDescriptions.add(new ThemeDescription(recyclerView, ThemeDescription.FLAG_CELLBACKGROUNDCOLOR, new Class[]{ThemeSmallPreviewView.class}, null, null, null, Theme.key_dialogBackgroundGray));
            for (ThemeDescription description : themeDescriptions) {
                description.resourcesProvider = fragment.getResourceProvider();
            }
            return themeDescriptions;
        }
    }

    interface OnItemSelectedListener {

        void onItemSelected(EmojiThemes theme, int position);
    }
}
