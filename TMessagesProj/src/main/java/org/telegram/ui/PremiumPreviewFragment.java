package org.telegram.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.ProductDetails;
import com.android.billingclient.api.Purchase;

import org.telegram.PhoneFormat.PhoneFormat;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BillingController;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.browser.Browser;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.CombinedDrawable;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.FillLastLinearLayoutManager;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.Premium.AboutPremiumView;
import org.telegram.ui.Components.Premium.GLIcon.GLIconRenderer;
import org.telegram.ui.Components.Premium.GLIcon.GLIconTextureView;
import org.telegram.ui.Components.Premium.PremiumButtonView;
import org.telegram.ui.Components.Premium.PremiumFeatureBottomSheet;
import org.telegram.ui.Components.Premium.PremiumGradient;
import org.telegram.ui.Components.Premium.PremiumNotAvailableBottomSheet;
import org.telegram.ui.Components.Premium.PremiumTierCell;
import org.telegram.ui.Components.Premium.StarParticlesView;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.SimpleThemeDescription;
import org.telegram.ui.Components.TextStyleSpan;
import org.telegram.ui.Components.URLSpanBotCommand;
import org.telegram.ui.Components.URLSpanBrowser;
import org.telegram.ui.Components.URLSpanMono;
import org.telegram.ui.Components.URLSpanNoUnderline;
import org.telegram.ui.Components.URLSpanReplacement;
import org.telegram.ui.Components.URLSpanUserMention;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class PremiumPreviewFragment extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {
    public final static String TRANSACTION_PATTERN = "^(.*?)(?:\\.\\.\\d*|)$";
    private final static boolean IS_PREMIUM_TIERS_UNAVAILABLE = false;

    RecyclerListView listView;
    ArrayList<PremiumFeatureData> premiumFeatures = new ArrayList<>();
    ArrayList<SubscriptionTier> subscriptionTiers = new ArrayList<>();
    int selectedTierIndex = 0;
    SubscriptionTier currentSubscriptionTier;

    int rowCount;
    int paddingRow;
    int featuresStartRow;
    int featuresEndRow;
    int sectionRow;
    int helpUsRow;
    int statusRow;
    int privacyRow;
    int lastPaddingRow;
    Drawable shadowDrawable;
    private FrameLayout buttonContainer;
    private View buttonDivider;

    PremiumFeatureCell dummyCell;
    PremiumTierCell dummyTierCell;
    int totalGradientHeight;
    int totalTiersGradientHeight;

    FillLastLinearLayoutManager layoutManager;
    //icons
    Shader shader;
    Matrix matrix = new Matrix();
    Paint gradientPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    BackgroundView backgroundView;
    StarParticlesView particlesView;
    boolean isLandscapeMode;

    public final static int PREMIUM_FEATURE_LIMITS = 0;
    public final static int PREMIUM_FEATURE_UPLOAD_LIMIT = 1;
    public final static int PREMIUM_FEATURE_DOWNLOAD_SPEED = 2;
    public final static int PREMIUM_FEATURE_ADS = 3;
    public final static int PREMIUM_FEATURE_REACTIONS = 4;
    public final static int PREMIUM_FEATURE_STICKERS = 5;
    public final static int PREMIUM_FEATURE_PROFILE_BADGE = 6;
    public final static int PREMIUM_FEATURE_ANIMATED_AVATARS = 7;
    public final static int PREMIUM_FEATURE_VOICE_TO_TEXT = 8;
    public final static int PREMIUM_FEATURE_ADVANCED_CHAT_MANAGEMENT = 9;
    public final static int PREMIUM_FEATURE_APPLICATION_ICONS = 10;
    public final static int PREMIUM_FEATURE_ANIMATED_EMOJI = 11;
    public final static int PREMIUM_FEATURE_EMOJI_STATUS = 12;
    public final static int PREMIUM_FEATURE_TRANSLATIONS = 13;
    private int statusBarHeight;
    private int firstViewHeight;
    private boolean isDialogVisible;

    boolean inc;
    float progress;
    private int currentYOffset;
    private FrameLayout contentView;
    private PremiumButtonView premiumButtonView;
    float totalProgress;
    private String source;

    private boolean selectAnnualByDefault;

    final Bitmap gradientTextureBitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888);
    final Canvas gradientCanvas = new Canvas(gradientTextureBitmap);
    PremiumGradient.PremiumGradientTools gradientTools = new PremiumGradient.PremiumGradientTools(Theme.key_premiumGradientBackground1, Theme.key_premiumGradientBackground2, Theme.key_premiumGradientBackground3, Theme.key_premiumGradientBackground4);
    PremiumGradient.PremiumGradientTools tiersGradientTools;

    private boolean forcePremium;
    float progressToFull;

    public static int serverStringToFeatureType(String s) {
        switch (s) {
            case "double_limits":
                return PREMIUM_FEATURE_LIMITS;
            case "more_upload":
                return PREMIUM_FEATURE_UPLOAD_LIMIT;
            case "faster_download":
                return PREMIUM_FEATURE_DOWNLOAD_SPEED;
            case "voice_to_text":
                return PREMIUM_FEATURE_VOICE_TO_TEXT;
            case "no_ads":
                return PREMIUM_FEATURE_ADS;
            case "infinite_reactions":
                return PREMIUM_FEATURE_REACTIONS;
            case "premium_stickers":
                return PREMIUM_FEATURE_STICKERS;
            case "advanced_chat_management":
                return PREMIUM_FEATURE_ADVANCED_CHAT_MANAGEMENT;
            case "profile_badge":
                return PREMIUM_FEATURE_PROFILE_BADGE;
            case "animated_userpics":
                return PREMIUM_FEATURE_ANIMATED_AVATARS;
            case "app_icons":
                return PREMIUM_FEATURE_APPLICATION_ICONS;
            case "animated_emoji":
                return PREMIUM_FEATURE_ANIMATED_EMOJI;
            case "emoji_status":
                return PREMIUM_FEATURE_EMOJI_STATUS;
            case "translations":
                return PREMIUM_FEATURE_TRANSLATIONS;
        }
        return -1;
    }

    public static String featureTypeToServerString(int type) {
        switch (type) {
            case PREMIUM_FEATURE_LIMITS:
                return "double_limits";
            case PREMIUM_FEATURE_UPLOAD_LIMIT:
                return "more_upload";
            case PREMIUM_FEATURE_DOWNLOAD_SPEED:
                return "faster_download";
            case PREMIUM_FEATURE_VOICE_TO_TEXT:
                return "voice_to_text";
            case PREMIUM_FEATURE_ADS:
                return "no_ads";
            case PREMIUM_FEATURE_REACTIONS:
                return "infinite_reactions";
            case PREMIUM_FEATURE_ANIMATED_EMOJI:
                return "animated_emoji";
            case PREMIUM_FEATURE_STICKERS:
                return "premium_stickers";
            case PREMIUM_FEATURE_ADVANCED_CHAT_MANAGEMENT:
                return "advanced_chat_management";
            case PREMIUM_FEATURE_PROFILE_BADGE:
                return "profile_badge";
            case PREMIUM_FEATURE_ANIMATED_AVATARS:
                return "animated_userpics";
            case PREMIUM_FEATURE_APPLICATION_ICONS:
                return "app_icons";
            case PREMIUM_FEATURE_EMOJI_STATUS:
                return "emoji_status";
            case PREMIUM_FEATURE_TRANSLATIONS:
                return "translations";
        }
        return null;
    }

    public PremiumPreviewFragment setForcePremium() {
        this.forcePremium = true;
        return this;
    }

    public PremiumPreviewFragment(String source) {
        super();
        this.source = source;
    }

    {
        tiersGradientTools = new PremiumGradient.PremiumGradientTools(Theme.key_premiumGradient1, Theme.key_premiumGradient2, -1, -1);
        tiersGradientTools.exactly = true;
        tiersGradientTools.x1 = 0;
        tiersGradientTools.y1 = 0f;
        tiersGradientTools.x2 = 0;
        tiersGradientTools.y2 = 1f;
        tiersGradientTools.cx = 0;
        tiersGradientTools.cy = 0;
    }

    public PremiumPreviewFragment setSelectAnnualByDefault() {
        this.selectAnnualByDefault = true;
        return this;
    }

    @SuppressLint("NotifyDataSetChanged")
    @Override
    public View createView(Context context) {
        hasOwnBackground = true;
        shader = new LinearGradient(
            0, 0, 0, 100,
            new int[]{
                Theme.getColor(Theme.key_premiumGradient4),
                Theme.getColor(Theme.key_premiumGradient3),
                Theme.getColor(Theme.key_premiumGradient2),
                Theme.getColor(Theme.key_premiumGradient1),
                Theme.getColor(Theme.key_premiumGradient0)
            },
            new float[]{0f, 0.32f, 0.5f, 0.7f, 1f},
            Shader.TileMode.CLAMP
        );
        shader.setLocalMatrix(matrix);
        gradientPaint.setShader(shader);

        dummyCell = new PremiumFeatureCell(context);
        dummyTierCell = new PremiumTierCell(context);

        premiumFeatures.clear();
        fillPremiumFeaturesList(premiumFeatures, currentAccount);

        Rect padding = new Rect();
        shadowDrawable = context.getResources().getDrawable(R.drawable.sheet_shadow_round).mutate();
        shadowDrawable.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_dialogBackground), PorterDuff.Mode.MULTIPLY));
        shadowDrawable.getPadding(padding);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            statusBarHeight = AndroidUtilities.isTablet() ? 0 : AndroidUtilities.statusBarHeight;
        }

        contentView = new FrameLayout(context) {

            int lastSize;
            boolean iconInterceptedTouch;
            boolean listInterceptedTouch;

            @Override
            public boolean dispatchTouchEvent(MotionEvent ev) {
                float iconX = backgroundView.getX() + backgroundView.imageFrameLayout.getX();
                float iconY = backgroundView.getY() + backgroundView.imageFrameLayout.getY();
                AndroidUtilities.rectTmp.set(iconX, iconY, iconX + backgroundView.imageView.getMeasuredWidth(), iconY + backgroundView.imageView.getMeasuredHeight());
                if ((AndroidUtilities.rectTmp.contains(ev.getX(), ev.getY()) || iconInterceptedTouch) && !listView.scrollingByUser) {
                    ev.offsetLocation(-iconX, -iconY);
                    if (ev.getAction() == MotionEvent.ACTION_DOWN || ev.getAction() == MotionEvent.ACTION_MOVE) {
                        iconInterceptedTouch = true;
                    } else if (ev.getAction() == MotionEvent.ACTION_UP || ev.getAction() == MotionEvent.ACTION_CANCEL) {
                        iconInterceptedTouch = false;
                    }
                    backgroundView.imageView.dispatchTouchEvent(ev);
                    return true;
                }

                float listX = backgroundView.getX() + backgroundView.tierListView.getX(), listY = backgroundView.getY() + backgroundView.tierListView.getY();
                AndroidUtilities.rectTmp.set(listX, listY, listX + backgroundView.tierListView.getWidth(), listY + backgroundView.tierListView.getHeight());
                if ((AndroidUtilities.rectTmp.contains(ev.getX(), ev.getY()) || listInterceptedTouch) && !listView.scrollingByUser) {
                    ev.offsetLocation(-listX, -listY);
                    if (ev.getAction() == MotionEvent.ACTION_DOWN) {
                        listInterceptedTouch = true;
                    } else if (ev.getAction() == MotionEvent.ACTION_UP || ev.getAction() == MotionEvent.ACTION_CANCEL) {
                        listInterceptedTouch = false;
                    }
                    backgroundView.tierListView.dispatchTouchEvent(ev);

                    if (listInterceptedTouch) {
                        return true;
                    }
                }
                return super.dispatchTouchEvent(ev);
            }

            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                if (MeasureSpec.getSize(widthMeasureSpec) > MeasureSpec.getSize(heightMeasureSpec)) {
                    isLandscapeMode = true;
                } else {
                    isLandscapeMode = false;
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    statusBarHeight = AndroidUtilities.isTablet() ? 0 : AndroidUtilities.statusBarHeight;
                }
                backgroundView.measure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
                particlesView.getLayoutParams().height = backgroundView.getMeasuredHeight();
                int buttonHeight = (buttonContainer == null || buttonContainer.getVisibility() == View.GONE ? 0 : AndroidUtilities.dp(68));
                layoutManager.setAdditionalHeight(buttonHeight + statusBarHeight - AndroidUtilities.dp(16));
                layoutManager.setMinimumLastViewHeight(buttonHeight);
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                int size = getMeasuredHeight() + getMeasuredWidth() << 16;
                if (lastSize != size) {
                    updateBackgroundImage();
                }
            }

            @Override
            protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
                super.onLayout(changed, left, top, right, bottom);
                backgroundView.imageView.mRenderer.gradientScaleX = backgroundView.imageView.getMeasuredWidth() / (float) getMeasuredWidth();
                backgroundView.imageView.mRenderer.gradientScaleY = backgroundView.imageView.getMeasuredHeight() / (float) getMeasuredHeight();
                backgroundView.imageView.mRenderer.gradientStartX = (backgroundView.getX() + backgroundView.imageView.getX()) / getMeasuredWidth();
                backgroundView.imageView.mRenderer.gradientStartY = (backgroundView.getY() + backgroundView.imageView.getY()) / getMeasuredHeight();
            }

            @Override
            protected void onSizeChanged(int w, int h, int oldw, int oldh) {
                super.onSizeChanged(w, h, oldw, oldh);
                measureGradient(w, h);
            }

            @Override
            protected void dispatchDraw(Canvas canvas) {
                if (!isDialogVisible) {
                    if (inc) {
                        progress += 16f / 1000f;
                        if (progress > 3) {
                            inc = false;
                        }
                    } else {
                        progress -= 16f / 1000f;
                        if (progress < 1) {
                            inc = true;
                        }
                    }
                }
                View firstView = null;
                if (listView.getLayoutManager() != null) {
                    firstView = listView.getLayoutManager().findViewByPosition(0);
                }

                currentYOffset = firstView == null ? 0 : firstView.getBottom();
                int h = actionBar.getBottom() + AndroidUtilities.dp(16);
                totalProgress = (1f - (currentYOffset - h) / (float) (firstViewHeight - h));
                totalProgress = Utilities.clamp(totalProgress, 1f, 0f);

                int maxTop = actionBar.getBottom() + AndroidUtilities.dp(16);
                if (currentYOffset < maxTop) {
                    currentYOffset = maxTop;
                }

                float oldProgress = progressToFull;
                progressToFull = 0;
                if (currentYOffset < maxTop + AndroidUtilities.dp(30)) {
                    progressToFull = (maxTop + AndroidUtilities.dp(30) - currentYOffset) / (float) AndroidUtilities.dp(30);
                }

                if (isLandscapeMode) {
                    progressToFull = 1f;
                    totalProgress = 1f;
                }
                if (oldProgress != progressToFull) {
                    listView.invalidate();
                }
                float fromTranslation = currentYOffset - (actionBar.getMeasuredHeight() + backgroundView.getMeasuredHeight() - statusBarHeight) + AndroidUtilities.dp(backgroundView.tierListView.getVisibility() == VISIBLE ? 24 : 16);
                float toTranslation = ((actionBar.getMeasuredHeight() - statusBarHeight - backgroundView.titleView.getMeasuredHeight()) / 2f) + statusBarHeight - backgroundView.getTop() - backgroundView.titleView.getTop();

                float translationsY = Math.max(toTranslation, fromTranslation);
                float iconTranslationsY = -translationsY / 4f + AndroidUtilities.dp(16);
                backgroundView.setTranslationY(translationsY);

                backgroundView.imageView.setTranslationY(iconTranslationsY + AndroidUtilities.dp(16));
                float s = 0.6f + (1f - totalProgress) * 0.4f;
                float alpha = 1f - (totalProgress > 0.5f ? (totalProgress - 0.5f) / 0.5f : 0f);
                backgroundView.imageView.setScaleX(s);
                backgroundView.imageView.setScaleY(s);
                backgroundView.imageView.setAlpha(alpha);
                backgroundView.subtitleView.setAlpha(alpha);
                backgroundView.tierListView.setAlpha(alpha);
                particlesView.setAlpha(1f - totalProgress);

                particlesView.setTranslationY(-(particlesView.getMeasuredHeight() - backgroundView.imageView.getMeasuredWidth()) / 2f + backgroundView.getY() + backgroundView.imageFrameLayout.getY());
                float toX = AndroidUtilities.dp(72) - backgroundView.titleView.getLeft();
                float f = totalProgress > 0.3f ? (totalProgress - 0.3f) / 0.7f : 0f;
                backgroundView.titleView.setTranslationX(toX * (1f - CubicBezierInterpolator.EASE_OUT_QUINT.getInterpolation(1 - f)));

                backgroundView.imageView.mRenderer.gradientStartX = (backgroundView.getX() + backgroundView.imageFrameLayout.getX() + getMeasuredWidth() * 0.1f * progress) / getMeasuredWidth();
                backgroundView.imageView.mRenderer.gradientStartY = (backgroundView.getY() + backgroundView.imageFrameLayout.getY()) / getMeasuredHeight();

                if (!isDialogVisible) {
                    invalidate();
                }
                gradientTools.gradientMatrix(0, 0, getMeasuredWidth(), getMeasuredHeight(), -getMeasuredWidth() * 0.1f * progress, 0);
                canvas.drawRect(0, 0, getMeasuredWidth(), currentYOffset + AndroidUtilities.dp(20), gradientTools.paint);

                super.dispatchDraw(canvas);
            }

            @Override
            protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
                if (child == listView) {
                    canvas.save();
                    canvas.clipRect(0, actionBar.getBottom(), getMeasuredWidth(), getMeasuredHeight());
                    super.drawChild(canvas, child, drawingTime);
                    canvas.restore();
                    return true;
                }
                return super.drawChild(canvas, child, drawingTime);
            }
        };
        contentView.setFitsSystemWindows(true);

        listView = new RecyclerListView(context) {
            @Override
            public void onDraw(Canvas canvas) {
                shadowDrawable.setBounds((int) (-padding.left - AndroidUtilities.dp(16) * progressToFull), currentYOffset - padding.top - AndroidUtilities.dp(16), (int) (getMeasuredWidth() + padding.right + AndroidUtilities.dp(16) * progressToFull), getMeasuredHeight());
                shadowDrawable.draw(canvas);
                super.onDraw(canvas);
            }
        };
        listView.setLayoutManager(layoutManager = new FillLastLinearLayoutManager(context, AndroidUtilities.dp(68) + statusBarHeight - AndroidUtilities.dp(16), listView));
        layoutManager.setFixedLastItemHeight();

        listView.setAdapter(new Adapter());
        listView.addOnScrollListener(new RecyclerView.OnScrollListener() {

            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    int maxTop = actionBar.getBottom() + AndroidUtilities.dp(16);
                    if (totalProgress > 0.5f) {
                        listView.smoothScrollBy(0, currentYOffset - maxTop);
                    } else {
                        View firstView = null;
                        if (listView.getLayoutManager() != null) {
                            firstView = listView.getLayoutManager().findViewByPosition(0);
                        }
                        if (firstView != null && firstView.getTop() < 0) {
                            listView.smoothScrollBy(0, firstView.getTop());
                        }
                    }
                }
                checkButtonDivider();
            }

            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                contentView.invalidate();
                checkButtonDivider();
            }
        });

        backgroundView = new BackgroundView(context) {
            @Override
            public boolean onInterceptTouchEvent(MotionEvent ev) {
                return true;
            }
        };
        particlesView = new StarParticlesView(context);
        backgroundView.imageView.setStarParticlesView(particlesView);
        contentView.addView(particlesView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        contentView.addView(backgroundView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        listView.setOnItemClickListener((view, position) -> {
            if (view instanceof PremiumFeatureCell) {
                PremiumFeatureCell cell = (PremiumFeatureCell) view;
                PremiumPreviewFragment.sentShowFeaturePreview(currentAccount, cell.data.type);
//                if (cell.data.type == PREMIUM_FEATURE_LIMITS) {
//                    DoubledLimitsBottomSheet bottomSheet = new DoubledLimitsBottomSheet(PremiumPreviewFragment.this, currentAccount, subscriptionTiers.get(selectedTierIndex));
//                    bottomSheet.setParentFragment(PremiumPreviewFragment.this);
//                    showDialog(bottomSheet);
//                } else {
//                if (subscriptionTiers.isEmpty()) {
//                    return;
//                }
               // }

                SubscriptionTier tier = selectedTierIndex < 0 || selectedTierIndex >= subscriptionTiers.size() ? null : subscriptionTiers.get(selectedTierIndex);
                showDialog(new PremiumFeatureBottomSheet(PremiumPreviewFragment.this, cell.data.type, false, tier));
            }
        });
        contentView.addView(listView);

        premiumButtonView = new PremiumButtonView(context, false);
        updateButtonText(false);
        buttonContainer = new FrameLayout(context);

        buttonDivider = new View(context);
        buttonDivider.setBackgroundColor(Theme.getColor(Theme.key_divider));
        buttonContainer.addView(buttonDivider, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 1));
        buttonDivider.getLayoutParams().height = 1;
        AndroidUtilities.updateViewVisibilityAnimated(buttonDivider, true, 1f, false);

        buttonContainer.addView(premiumButtonView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.CENTER_VERTICAL, 16, 0, 16, 0));
        buttonContainer.setBackgroundColor(getThemedColor(Theme.key_dialogBackground));
        contentView.addView(buttonContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 68, Gravity.BOTTOM));

        fragmentView = contentView;
        actionBar.setBackground(null);
        actionBar.setCastShadows(false);
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });
        actionBar.setForceSkipTouches(true);

        updateColors();
        updateRows();

        backgroundView.imageView.startEnterAnimation(-180, 200);
        if (forcePremium) {
            AndroidUtilities.runOnUIThread(() -> getMediaDataController().loadPremiumPromo(false), 400);
        }
        MediaDataController.getInstance(currentAccount).preloadPremiumPreviewStickers();

        sentShowScreenStat(source);
        return fragmentView;
    }

    @Override
    public boolean isActionBarCrossfadeEnabled() {
        return false;
    }

    public static void buyPremium(BaseFragment fragment) {
        buyPremium(fragment, "settings");
    }

    public static void fillPremiumFeaturesList(ArrayList<PremiumFeatureData> premiumFeatures, int currentAccount) {
        MessagesController messagesController = MessagesController.getInstance(currentAccount);
        premiumFeatures.add(new PremiumFeatureData(PREMIUM_FEATURE_LIMITS, R.drawable.msg_premium_limits, LocaleController.getString("PremiumPreviewLimits", R.string.PremiumPreviewLimits), LocaleController.formatString("PremiumPreviewLimitsDescription", R.string.PremiumPreviewLimitsDescription,
                messagesController.channelsLimitPremium, messagesController.dialogFiltersLimitPremium, messagesController.dialogFiltersPinnedLimitPremium, messagesController.publicLinksLimitPremium, 4)));
        premiumFeatures.add(new PremiumFeatureData(PREMIUM_FEATURE_UPLOAD_LIMIT, R.drawable.msg_premium_uploads, LocaleController.getString("PremiumPreviewUploads", R.string.PremiumPreviewUploads), LocaleController.getString("PremiumPreviewUploadsDescription", R.string.PremiumPreviewUploadsDescription)));
        premiumFeatures.add(new PremiumFeatureData(PREMIUM_FEATURE_DOWNLOAD_SPEED, R.drawable.msg_premium_speed, LocaleController.getString("PremiumPreviewDownloadSpeed", R.string.PremiumPreviewDownloadSpeed), LocaleController.getString("PremiumPreviewDownloadSpeedDescription", R.string.PremiumPreviewDownloadSpeedDescription)));
        premiumFeatures.add(new PremiumFeatureData(PREMIUM_FEATURE_VOICE_TO_TEXT, R.drawable.msg_premium_voice, LocaleController.getString("PremiumPreviewVoiceToText", R.string.PremiumPreviewVoiceToText), LocaleController.getString("PremiumPreviewVoiceToTextDescription", R.string.PremiumPreviewVoiceToTextDescription)));
        premiumFeatures.add(new PremiumFeatureData(PREMIUM_FEATURE_ADS, R.drawable.msg_premium_ads, LocaleController.getString("PremiumPreviewNoAds", R.string.PremiumPreviewNoAds), LocaleController.getString("PremiumPreviewNoAdsDescription", R.string.PremiumPreviewNoAdsDescription)));
        premiumFeatures.add(new PremiumFeatureData(PREMIUM_FEATURE_REACTIONS, R.drawable.msg_premium_reactions, LocaleController.getString("PremiumPreviewReactions2", R.string.PremiumPreviewReactions2), LocaleController.getString("PremiumPreviewReactions2Description", R.string.PremiumPreviewReactions2Description)));
        premiumFeatures.add(new PremiumFeatureData(PREMIUM_FEATURE_STICKERS, R.drawable.msg_premium_stickers, LocaleController.getString("PremiumPreviewStickers", R.string.PremiumPreviewStickers), LocaleController.getString("PremiumPreviewStickersDescription", R.string.PremiumPreviewStickersDescription)));
        premiumFeatures.add(new PremiumFeatureData(PREMIUM_FEATURE_ANIMATED_EMOJI, R.drawable.msg_premium_emoji, LocaleController.getString("PremiumPreviewEmoji", R.string.PremiumPreviewEmoji), LocaleController.getString("PremiumPreviewEmojiDescription", R.string.PremiumPreviewEmojiDescription)));
        premiumFeatures.add(new PremiumFeatureData(PREMIUM_FEATURE_ADVANCED_CHAT_MANAGEMENT, R.drawable.msg_premium_tools, LocaleController.getString("PremiumPreviewAdvancedChatManagement", R.string.PremiumPreviewAdvancedChatManagement), LocaleController.getString("PremiumPreviewAdvancedChatManagementDescription", R.string.PremiumPreviewAdvancedChatManagementDescription)));
        premiumFeatures.add(new PremiumFeatureData(PREMIUM_FEATURE_PROFILE_BADGE, R.drawable.msg_premium_badge, LocaleController.getString("PremiumPreviewProfileBadge", R.string.PremiumPreviewProfileBadge), LocaleController.getString("PremiumPreviewProfileBadgeDescription", R.string.PremiumPreviewProfileBadgeDescription)));
        premiumFeatures.add(new PremiumFeatureData(PREMIUM_FEATURE_ANIMATED_AVATARS, R.drawable.msg_premium_avatar, LocaleController.getString("PremiumPreviewAnimatedProfiles", R.string.PremiumPreviewAnimatedProfiles), LocaleController.getString("PremiumPreviewAnimatedProfilesDescription", R.string.PremiumPreviewAnimatedProfilesDescription)));
        premiumFeatures.add(new PremiumFeatureData(PREMIUM_FEATURE_APPLICATION_ICONS, R.drawable.msg_premium_icons, LocaleController.getString("PremiumPreviewAppIcon", R.string.PremiumPreviewAppIcon), LocaleController.getString("PremiumPreviewAppIconDescription", R.string.PremiumPreviewAppIconDescription)));
        premiumFeatures.add(new PremiumFeatureData(PREMIUM_FEATURE_EMOJI_STATUS, R.drawable.msg_premium_status, LocaleController.getString("PremiumPreviewEmojiStatus", R.string.PremiumPreviewEmojiStatus), LocaleController.getString("PremiumPreviewEmojiStatusDescription", R.string.PremiumPreviewEmojiStatusDescription)));
        premiumFeatures.add(new PremiumFeatureData(PREMIUM_FEATURE_TRANSLATIONS, R.drawable.msg_premium_translate, LocaleController.getString("PremiumPreviewTranslations", R.string.PremiumPreviewTranslations), LocaleController.getString("PremiumPreviewTranslationsDescription", R.string.PremiumPreviewTranslationsDescription)));

        if (messagesController.premiumFeaturesTypesToPosition.size() > 0) {
            for (int i = 0; i < premiumFeatures.size(); i++) {
                if (messagesController.premiumFeaturesTypesToPosition.get(premiumFeatures.get(i).type, -1) == -1) {
                    premiumFeatures.remove(i);
                    i--;
                }
            }
        }

        Collections.sort(premiumFeatures, (o1, o2) -> {
            int type1 = messagesController.premiumFeaturesTypesToPosition.get(o1.type, Integer.MAX_VALUE);
            int type2 = messagesController.premiumFeaturesTypesToPosition.get(o2.type, Integer.MAX_VALUE);
            return type1 - type2;
        });
    }

    private void updateBackgroundImage() {
        if (contentView.getMeasuredWidth() == 0 || contentView.getMeasuredHeight() == 0) {
            return;
        }
        gradientTools.gradientMatrix(0, 0, contentView.getMeasuredWidth(), contentView.getMeasuredHeight(), 0, 0);
        gradientCanvas.save();
        gradientCanvas.scale(100f / contentView.getMeasuredWidth(), 100f / contentView.getMeasuredHeight());
        gradientCanvas.drawRect(0, 0, contentView.getMeasuredWidth(), contentView.getMeasuredHeight(), gradientTools.paint);
        gradientCanvas.restore();
        backgroundView.imageView.setBackgroundBitmap(gradientTextureBitmap);
    }

    private void checkButtonDivider() {
        AndroidUtilities.updateViewVisibilityAnimated(buttonDivider, listView.canScrollVertically(1), 1f, true);
    }

    public static void buyPremium(BaseFragment fragment, String source) {
        buyPremium(fragment, null, source, true);
    }

    public static void buyPremium(BaseFragment fragment, String source, boolean forcePremium) {
        buyPremium(fragment, null, source, forcePremium);
    }

    public static void buyPremium(BaseFragment fragment, SubscriptionTier tier, String source) {
        buyPremium(fragment, tier, source, true);
    }

    public static void buyPremium(BaseFragment fragment, SubscriptionTier tier, String source, boolean forcePremium) {
        buyPremium(fragment, tier, source, forcePremium, null);
    }

    public static void buyPremium(BaseFragment fragment, SubscriptionTier tier, String source, boolean forcePremium, BillingFlowParams.SubscriptionUpdateParams updateParams) {
        if (BuildVars.IS_BILLING_UNAVAILABLE) {
            fragment.showDialog(new PremiumNotAvailableBottomSheet(fragment));
            return;
        }

        if (tier == null) {
            forcePremium = true;
            TLRPC.TL_help_premiumPromo promo = fragment.getAccountInstance().getMediaDataController().getPremiumPromo();
            if (promo != null) {
                for (TLRPC.TL_premiumSubscriptionOption option : promo.period_options) {
                    if (option.months == 1) {
                        tier = new SubscriptionTier(option);
                        break;
                    }
                }
            }
        }
        SubscriptionTier selectedTier = tier;

        PremiumPreviewFragment.sentPremiumButtonClick();

        if (BuildVars.useInvoiceBilling()) {
            Activity activity = fragment.getParentActivity();
            if (activity instanceof LaunchActivity) {
                LaunchActivity launchActivity = (LaunchActivity) activity;

                if (selectedTier == null || selectedTier.subscriptionOption == null || selectedTier.subscriptionOption.bot_url == null) {
                    if (!TextUtils.isEmpty(fragment.getMessagesController().premiumBotUsername)) {
                        launchActivity.setNavigateToPremiumBot(true);
                        launchActivity.onNewIntent(new Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/" + fragment.getMessagesController().premiumBotUsername + "?start=" + source)));
                    } else if (!TextUtils.isEmpty(fragment.getMessagesController().premiumInvoiceSlug)) {
                        launchActivity.onNewIntent(new Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/$" + fragment.getMessagesController().premiumInvoiceSlug)));
                    }
                } else {
                    Uri uri = Uri.parse(selectedTier.subscriptionOption.bot_url);
                    if (uri.getHost().equals("t.me")) {
                        if (!uri.getPath().startsWith("/$") && !uri.getPath().startsWith("/invoice/")) {
                            launchActivity.setNavigateToPremiumBot(true);
                        }
                    }
                    Browser.openUrl(launchActivity, tier.subscriptionOption.bot_url);
                }
                return;
            }
        }

        if (BillingController.PREMIUM_PRODUCT_DETAILS == null) {
            return;
        }

        List<ProductDetails.SubscriptionOfferDetails> offerDetails = BillingController.PREMIUM_PRODUCT_DETAILS.getSubscriptionOfferDetails();
        if (offerDetails.isEmpty()) {
            return;
        }

        if (selectedTier.getGooglePlayProductDetails() == null) {
            selectedTier.setGooglePlayProductDetails(BillingController.PREMIUM_PRODUCT_DETAILS);
        }

        if (selectedTier.getOfferDetails() == null) {
            return;
        }

        boolean finalForcePremium = forcePremium;
        BillingController.getInstance().queryPurchases(BillingClient.ProductType.SUBS, (billingResult1, list) -> AndroidUtilities.runOnUIThread(() -> {
            if (billingResult1.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                Runnable onSuccess = () -> {
                    if (fragment instanceof PremiumPreviewFragment) {
                        PremiumPreviewFragment premiumPreviewFragment = (PremiumPreviewFragment) fragment;
                        if (finalForcePremium) {
                            premiumPreviewFragment.setForcePremium();
                        }
                        premiumPreviewFragment.getMediaDataController().loadPremiumPromo(false);

                        premiumPreviewFragment.listView.smoothScrollToPosition(0);
                    } else {
                        PremiumPreviewFragment previewFragment = new PremiumPreviewFragment(null);
                        if (finalForcePremium) {
                            previewFragment.setForcePremium();
                        }
                        fragment.presentFragment(previewFragment);
                    }
                    if (fragment.getParentActivity() instanceof LaunchActivity) {
                        try {
                            fragment.getFragmentView().performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
                        } catch (Exception ignored) {
                        }
                        ((LaunchActivity) fragment.getParentActivity()).getFireworksOverlay().start();
                    }
                };
                if (list != null && !list.isEmpty() && !fragment.getUserConfig().isPremium()) {
                    for (Purchase purchase : list) {
                        if (purchase.getProducts().contains(BillingController.PREMIUM_PRODUCT_ID)) {
                            TLRPC.TL_payments_assignPlayMarketTransaction req = new TLRPC.TL_payments_assignPlayMarketTransaction();
                            req.receipt = new TLRPC.TL_dataJSON();
                            req.receipt.data = purchase.getOriginalJson();
                            TLRPC.TL_inputStorePaymentPremiumSubscription purpose = new TLRPC.TL_inputStorePaymentPremiumSubscription();
                            purpose.restore = true;
                            if (updateParams != null) {
                                purpose.upgrade = true;
                            }
                            req.purpose = purpose;
                            fragment.getConnectionsManager().sendRequest(req, (response, error) -> {
                                if (response instanceof TLRPC.Updates) {
                                    fragment.getMessagesController().processUpdates((TLRPC.Updates) response, false);

                                    AndroidUtilities.runOnUIThread(onSuccess);
                                } else if (error != null) {
                                    AndroidUtilities.runOnUIThread(() -> AlertsCreator.processError(fragment.getCurrentAccount(), error, fragment, req));
                                }
                            }, ConnectionsManager.RequestFlagFailOnServerErrors | ConnectionsManager.RequestFlagInvokeAfter);

                            return;
                        }
                    }
                }

                BillingController.getInstance().addResultListener(BillingController.PREMIUM_PRODUCT_ID, billingResult -> {
                    if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                        AndroidUtilities.runOnUIThread(onSuccess);
                    }
                });

                TLRPC.TL_payments_canPurchasePremium req = new TLRPC.TL_payments_canPurchasePremium();
                TLRPC.TL_inputStorePaymentPremiumSubscription purpose = new TLRPC.TL_inputStorePaymentPremiumSubscription();
                if (updateParams != null) {
                    purpose.upgrade = true;
                }
                req.purpose = purpose;
                fragment.getConnectionsManager().sendRequest(req, (response, error) -> {
                    AndroidUtilities.runOnUIThread(() -> {
                        if (response instanceof TLRPC.TL_boolTrue) {
                            BillingController.getInstance().launchBillingFlow(fragment.getParentActivity(), fragment.getAccountInstance(), purpose, Collections.singletonList(
                                    BillingFlowParams.ProductDetailsParams.newBuilder()
                                            .setProductDetails(BillingController.PREMIUM_PRODUCT_DETAILS)
                                            .setOfferToken(selectedTier.getOfferDetails().getOfferToken())
                                            .build()
                            ), updateParams, false);
                        } else {
                            AlertsCreator.processError(fragment.getCurrentAccount(), error, fragment, req);
                        }
                    });
                });
            }
        }));
    }

    public static String getPremiumButtonText(int currentAccount, SubscriptionTier tier) {
        if (BuildVars.IS_BILLING_UNAVAILABLE) {
            return LocaleController.getString(R.string.SubscribeToPremiumNotAvailable);
        }

        if (tier == null) {
            if (BuildVars.useInvoiceBilling()) {
                TLRPC.TL_help_premiumPromo premiumPromo = MediaDataController.getInstance(currentAccount).getPremiumPromo();
                if (premiumPromo != null) {
                    long amount = 0;
                    String currency = "USD";
                    for (TLRPC.TL_premiumSubscriptionOption option : premiumPromo.period_options) {
                        if (option.months == 1) {
                            amount = option.amount;
                            currency = option.currency;
                        }
                    }

                    return LocaleController.formatString(R.string.SubscribeToPremium, BillingController.getInstance().formatCurrency(amount, currency));
                }

                return LocaleController.getString(R.string.SubscribeToPremiumNoPrice);
            }

            String price = null;
            if (BillingController.PREMIUM_PRODUCT_DETAILS != null) {
                List<ProductDetails.SubscriptionOfferDetails> details = BillingController.PREMIUM_PRODUCT_DETAILS.getSubscriptionOfferDetails();
                if (!details.isEmpty()) {
                    ProductDetails.SubscriptionOfferDetails offerDetails = details.get(0);
                    for (ProductDetails.PricingPhase phase : offerDetails.getPricingPhases().getPricingPhaseList()) {
                        if (phase.getBillingPeriod().equals("P1M")) { // Once per month
                            price = phase.getFormattedPrice();
                            break;
                        }
                    }
                }
            }

            if (price == null) {
                return LocaleController.getString(R.string.Loading);
            }

            return LocaleController.formatString(R.string.SubscribeToPremium, price);
        } else {
            if (!BuildVars.useInvoiceBilling() && tier.getOfferDetails() == null) {
                return LocaleController.getString(R.string.Loading);
            }
            return LocaleController.formatString(UserConfig.getInstance(currentAccount).isPremium() ? tier.getMonths() == 12 ? R.string.UpgradePremiumPerYear : R.string.UpgradePremiumPerMonth :
                    tier.getMonths() == 12 ? R.string.SubscribeToPremiumPerYear : R.string.SubscribeToPremium, tier.getMonths() == 12 ? tier.getFormattedPricePerYear() : tier.getFormattedPricePerMonth());
        }
    }

    private void measureGradient(int w, int h) {
        int yOffset = 0;
        for (int i = 0; i < premiumFeatures.size(); i++) {
            dummyCell.setData(premiumFeatures.get(i), false);
            dummyCell.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.AT_MOST));
            premiumFeatures.get(i).yOffset = yOffset;
            yOffset += dummyCell.getMeasuredHeight();
        }

        totalGradientHeight = yOffset;
    }

    private void updateRows() {
        rowCount = 0;
        sectionRow = -1;
        privacyRow = -1;

        paddingRow = rowCount++;
        featuresStartRow = rowCount;
        rowCount += premiumFeatures.size();
        featuresEndRow = rowCount;
        statusRow = rowCount++;
        lastPaddingRow = rowCount++;

        AndroidUtilities.updateViewVisibilityAnimated(buttonContainer, !getUserConfig().isPremium() || currentSubscriptionTier != null && currentSubscriptionTier.getMonths() < subscriptionTiers.get(selectedTierIndex).getMonths() && !forcePremium, 1f, false);

        int buttonHeight = buttonContainer.getVisibility() == View.VISIBLE ? AndroidUtilities.dp(64) : 0;
        layoutManager.setAdditionalHeight(buttonHeight + statusBarHeight - AndroidUtilities.dp(16));
        layoutManager.setMinimumLastViewHeight(buttonHeight);
    }

    @Override
    public boolean isSwipeBackEnabled(MotionEvent event) {
        return true;
    }

    @Override
    public boolean onFragmentCreate() {
        if (getMessagesController().premiumLocked) {
            return false;
        }
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.billingProductDetailsUpdated);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.currentUserPremiumStatusChanged);
        getNotificationCenter().addObserver(this, NotificationCenter.premiumPromoUpdated);

        if (getMediaDataController().getPremiumPromo() != null) {
            for (TLRPC.Document document : getMediaDataController().getPremiumPromo().videos) {
                FileLoader.getInstance(currentAccount).loadFile(document, getMediaDataController().getPremiumPromo(), FileLoader.PRIORITY_HIGH, 0);
            }
        }

        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();

        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.billingProductDetailsUpdated);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.currentUserPremiumStatusChanged);
        getNotificationCenter().removeObserver(this, NotificationCenter.premiumPromoUpdated);
    }

    @SuppressLint("NotifyDataSetChanged")
    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.billingProductDetailsUpdated || id == NotificationCenter.premiumPromoUpdated) {
            updateButtonText(false);
            backgroundView.updatePremiumTiers();
        }
        if (id == NotificationCenter.currentUserPremiumStatusChanged || id == NotificationCenter.premiumPromoUpdated) {
            backgroundView.updateText();
            backgroundView.updatePremiumTiers();
            updateRows();
            listView.getAdapter().notifyDataSetChanged();
        }
    }

    private class Adapter extends RecyclerListView.SelectionAdapter {
        private final static int TYPE_PADDING = 0,
            TYPE_FEATURE = 1,
            TYPE_SHADOW_SECTION = 2,
            TYPE_BUTTON = 3,
            TYPE_HELP_US = 4,
            TYPE_STATUS_TEXT = 5,
            TYPE_BOTTOM_PADDING = 6;

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view;
            Context context = parent.getContext();
            switch (viewType) {
                default:
                case TYPE_PADDING:
                    view = new View(context) {
                        @Override
                        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                            if (isLandscapeMode) {
                                firstViewHeight = statusBarHeight + actionBar.getMeasuredHeight() - AndroidUtilities.dp(16);
                            } else {
                                int h = AndroidUtilities.dp(300) + statusBarHeight;
                                if (backgroundView.getMeasuredHeight() + AndroidUtilities.dp(24) > h) {
                                    h = backgroundView.getMeasuredHeight() + AndroidUtilities.dp(24);
                                }
                                firstViewHeight = h;
                            }
                            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(firstViewHeight, MeasureSpec.EXACTLY));
                        }
                    };
                    break;
                case TYPE_STATUS_TEXT:
                    view = new TextInfoPrivacyCell(context);
                    break;
                case TYPE_FEATURE:
                    view = new PremiumFeatureCell(context) {
                        @Override
                        protected void dispatchDraw(Canvas canvas) {
                            AndroidUtilities.rectTmp.set(imageView.getLeft(), imageView.getTop(), imageView.getRight(), imageView.getBottom());
                            matrix.reset();
                            matrix.postScale(1f, totalGradientHeight / 100f, 0, 0);
                            matrix.postTranslate(0, -data.yOffset);
                            shader.setLocalMatrix(matrix);
                            canvas.drawRoundRect(AndroidUtilities.rectTmp, AndroidUtilities.dp(8), AndroidUtilities.dp(8), gradientPaint);
                            super.dispatchDraw(canvas);
                        }
                    };
                    break;
                case TYPE_SHADOW_SECTION:
                    ShadowSectionCell shadowSectionCell = new ShadowSectionCell(context, 12, Theme.getColor(Theme.key_windowBackgroundGray));
                    Drawable shadowDrawable = Theme.getThemedDrawable(context, R.drawable.greydivider_bottom, Theme.getColor(Theme.key_windowBackgroundGrayShadow));
                    Drawable background = new ColorDrawable(Theme.getColor(Theme.key_windowBackgroundGray));
                    CombinedDrawable combinedDrawable = new CombinedDrawable(background, shadowDrawable, 0, 0);
                    combinedDrawable.setFullsize(true);
                    shadowSectionCell.setBackgroundDrawable(combinedDrawable);
                    view = shadowSectionCell;
                    break;
                case TYPE_HELP_US:
                    view = new AboutPremiumView(context);
                    break;
                case TYPE_BOTTOM_PADDING:
                    view = new View(context);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
                    break;
            }
            view.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            if (position >= featuresStartRow && position < featuresEndRow) {
                ((PremiumFeatureCell) holder.itemView).setData(premiumFeatures.get(position - featuresStartRow), position != featuresEndRow - 1);
            } else if (position == statusRow || position == privacyRow) {
                TextInfoPrivacyCell privacyCell = (TextInfoPrivacyCell) holder.itemView;

                Drawable shadowDrawable = Theme.getThemedDrawable(privacyCell.getContext(), R.drawable.greydivider, Theme.getColor(Theme.key_windowBackgroundGrayShadow));
                Drawable background = new ColorDrawable(Theme.getColor(Theme.key_windowBackgroundGray));
                CombinedDrawable combinedDrawable = new CombinedDrawable(background, shadowDrawable, 0, 0);
                combinedDrawable.setFullsize(true);
                privacyCell.setBackground(combinedDrawable);

                if (position == statusRow) {
                    TLRPC.TL_help_premiumPromo premiumPromo = getMediaDataController().getPremiumPromo();
                    if (premiumPromo == null) {
                        return;
                    }

                    SpannableString spannableString = new SpannableString(premiumPromo.status_text);
                    MediaDataController.addTextStyleRuns(premiumPromo.status_entities, premiumPromo.status_text, spannableString);
                    byte t = 0;
                    for (TextStyleSpan span : spannableString.getSpans(0, spannableString.length(), TextStyleSpan.class)) {
                        TextStyleSpan.TextStyleRun run = span.getTextStyleRun();
                        boolean setRun = false;
                        String url = run.urlEntity != null ? TextUtils.substring(premiumPromo.status_text, run.urlEntity.offset, run.urlEntity.offset + run.urlEntity.length) : null;
                        if (run.urlEntity instanceof TLRPC.TL_messageEntityBotCommand) {
                            spannableString.setSpan(new URLSpanBotCommand(url, t, run), run.start, run.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        } else if (run.urlEntity instanceof TLRPC.TL_messageEntityHashtag || run.urlEntity instanceof TLRPC.TL_messageEntityMention || run.urlEntity instanceof TLRPC.TL_messageEntityCashtag) {
                            spannableString.setSpan(new URLSpanNoUnderline(url, run), run.start, run.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        } else if (run.urlEntity instanceof TLRPC.TL_messageEntityEmail) {
                            spannableString.setSpan(new URLSpanReplacement("mailto:" + url, run), run.start, run.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        } else if (run.urlEntity instanceof TLRPC.TL_messageEntityUrl) {
                            String lowerCase = url.toLowerCase();
                            if (!lowerCase.contains("://")) {
                                spannableString.setSpan(new URLSpanBrowser("http://" + url, run), run.start, run.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                            } else {
                                spannableString.setSpan(new URLSpanBrowser(url, run), run.start, run.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                            }
                        } else if (run.urlEntity instanceof TLRPC.TL_messageEntityBankCard) {
                            spannableString.setSpan(new URLSpanNoUnderline("card:" + url, run), run.start, run.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        } else if (run.urlEntity instanceof TLRPC.TL_messageEntityPhone) {
                            String tel = PhoneFormat.stripExceptNumbers(url);
                            if (url.startsWith("+")) {
                                tel = "+" + tel;
                            }
                            spannableString.setSpan(new URLSpanBrowser("tel:" + tel, run), run.start, run.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        } else if (run.urlEntity instanceof TLRPC.TL_messageEntityTextUrl) {
                            URLSpanReplacement spanReplacement = new URLSpanReplacement(run.urlEntity.url, run);
                            spanReplacement.setNavigateToPremiumBot(true);
                            spannableString.setSpan(spanReplacement, run.start, run.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        } else if (run.urlEntity instanceof TLRPC.TL_messageEntityMentionName) {
                            spannableString.setSpan(new URLSpanUserMention("" + ((TLRPC.TL_messageEntityMentionName) run.urlEntity).user_id, t, run), run.start, run.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        } else if (run.urlEntity instanceof TLRPC.TL_inputMessageEntityMentionName) {
                            spannableString.setSpan(new URLSpanUserMention("" + ((TLRPC.TL_inputMessageEntityMentionName) run.urlEntity).user_id.user_id, t, run), run.start, run.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        } else if ((run.flags & TextStyleSpan.FLAG_STYLE_MONO) != 0) {
                            spannableString.setSpan(new URLSpanMono(spannableString, run.start, run.end, t, run), run.start, run.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        } else {
                            setRun = true;
                            spannableString.setSpan(new TextStyleSpan(run), run.start, run.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        }
                        if (!setRun && (run.flags & TextStyleSpan.FLAG_STYLE_SPOILER) != 0) {
                            spannableString.setSpan(new TextStyleSpan(run), run.start, run.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        }
                    }
                    privacyCell.setText(spannableString);
                }
            }
        }

        @Override
        public int getItemCount() {
            return rowCount;
        }

        @Override
        public int getItemViewType(int position) {
            if (position == paddingRow) {
                return TYPE_PADDING;
            } else if (position >= featuresStartRow && position < featuresEndRow) {
                return TYPE_FEATURE;
            } else if (position == sectionRow) {
                return TYPE_SHADOW_SECTION;
            } else if (position == helpUsRow) {
                return TYPE_HELP_US;
            } else if (position == statusRow || position == privacyRow) {
                return TYPE_STATUS_TEXT;
            } else if (position == lastPaddingRow) {
                return TYPE_BOTTOM_PADDING;
            }
            return TYPE_PADDING;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return holder.getItemViewType() == TYPE_FEATURE;
        }
    }

    public static class PremiumFeatureData {
        public final int type;
        public final int icon;
        public final String title;
        public final String description;
        public int yOffset;

        public PremiumFeatureData(int type, int icon, String title, String description) {
            this.type = type;
            this.icon = icon;
            this.title = title;
            this.description = description;
        }
    }

    FrameLayout settingsView;

    private class BackgroundView extends LinearLayout {

        TextView titleView;
        private final TextView subtitleView;
        private final FrameLayout imageFrameLayout;
        private final GLIconTextureView imageView;

        private RecyclerListView tierListView;

        public BackgroundView(Context context) {
            super(context);
            setOrientation(VERTICAL);
            imageFrameLayout = new FrameLayout(context);
            addView(imageFrameLayout, LayoutHelper.createLinear(190, 190, Gravity.CENTER_HORIZONTAL));
            imageView = new GLIconTextureView(context, GLIconRenderer.FRAGMENT_STYLE) {
                @Override
                public void onLongPress() {
                    super.onLongPress();
                    if (settingsView != null && !BuildVars.DEBUG_PRIVATE_VERSION) {
                        return;
                    }

                    settingsView = new FrameLayout(context);
                    ScrollView scrollView = new ScrollView(context);

                    LinearLayout linearLayout = new GLIconSettingsView(context, imageView.mRenderer);
                    scrollView.addView(linearLayout);
                    settingsView.addView(scrollView);
                    settingsView.setBackgroundColor(Theme.getColor(Theme.key_dialogBackground));
                    contentView.addView(settingsView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.BOTTOM));
                    ((MarginLayoutParams) settingsView.getLayoutParams()).topMargin = currentYOffset;

                    settingsView.setTranslationY(AndroidUtilities.dp(1000));
                    settingsView.animate().translationY(1).setDuration(300);
                }
            };
            imageFrameLayout.addView(imageView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
            imageFrameLayout.setClipChildren(false);
            setClipChildren(false);

            titleView = new TextView(context);
            titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 22);
            titleView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            titleView.setGravity(Gravity.CENTER_HORIZONTAL);
            addView(titleView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, Gravity.CENTER_HORIZONTAL, 16, 20, 16, 0));

            subtitleView = new TextView(context);
            subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            subtitleView.setLineSpacing(AndroidUtilities.dp(2), 1f);
            subtitleView.setGravity(Gravity.CENTER_HORIZONTAL);
            addView(subtitleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 16, 7, 16, 0));

            tierListView = new RecyclerListView(context) {
                Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

                {
                    paint.setColor(Theme.getColor(Theme.key_dialogBackground));
                }

                @Override
                public void draw(Canvas c) {
                    AndroidUtilities.rectTmp.set(0, 0, getWidth(), getHeight());
                    c.drawRoundRect(AndroidUtilities.rectTmp, AndroidUtilities.dp(12), AndroidUtilities.dp(12), paint);

                    super.draw(c);
                }

                @Override
                protected void onSizeChanged(int w, int h, int oldw, int oldh) {
                    super.onSizeChanged(w, h, oldw, oldh);
                    measureGradient(w, h);
                }
            };
            tierListView.setOverScrollMode(OVER_SCROLL_NEVER);
            tierListView.setLayoutManager(new LinearLayoutManager(context));
            tierListView.setAdapter(new RecyclerListView.SelectionAdapter() {
                @NonNull
                @Override
                public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                    PremiumTierCell premiumTierCell = new PremiumTierCell(context) {
                        @Override
                        protected void dispatchDraw(Canvas canvas) {
                            if (discountView.getVisibility() == VISIBLE) {
                                AndroidUtilities.rectTmp.set(discountView.getLeft(), discountView.getTop(), discountView.getRight(), discountView.getBottom());
                                tiersGradientTools.gradientMatrix(0, 0, getMeasuredWidth(), totalTiersGradientHeight, 0, -tier.yOffset);
                                canvas.drawRoundRect(AndroidUtilities.rectTmp, AndroidUtilities.dp(6), AndroidUtilities.dp(6), tiersGradientTools.paint);
                            }

                            super.dispatchDraw(canvas);
                        }
                    };
                    premiumTierCell.setCirclePaintProvider(obj -> {
                        tiersGradientTools.gradientMatrix(0, 0, premiumTierCell.getMeasuredWidth(), totalTiersGradientHeight, 0, -premiumTierCell.getTier().yOffset);
                        return tiersGradientTools.paint;
                    });
                    return new RecyclerListView.Holder(premiumTierCell);
                }

                @Override
                public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                    PremiumTierCell premiumTier = (PremiumTierCell) holder.itemView;
                    premiumTier.bind(subscriptionTiers.get(position), position != getItemCount() - 1);
                    premiumTier.setChecked(selectedTierIndex == position, false);
                }

                @Override
                public boolean isEnabled(RecyclerView.ViewHolder holder) {
                    return !subscriptionTiers.get(holder.getAdapterPosition()).subscriptionOption.current;
                }

                @Override
                public int getItemCount() {
                    return subscriptionTiers.size();
                }
            });
            tierListView.setOnItemClickListener((view, position) -> {
                if (!view.isEnabled()) {
                    return;
                }
                if (view instanceof PremiumTierCell) {
                    PremiumTierCell tierCell = (PremiumTierCell) view;
                    selectedTierIndex = subscriptionTiers.indexOf(tierCell.getTier());
                    updateButtonText(true);
                    tierCell.setChecked(true, true);

                    for (int i = 0; i < tierListView.getChildCount(); i++) {
                        View ch = tierListView.getChildAt(i);
                        if (ch instanceof PremiumTierCell) {
                            PremiumTierCell otherCell = (PremiumTierCell) ch;
                            if (otherCell.getTier() != tierCell.getTier()) {
                                otherCell.setChecked(false, true);
                            }
                        }
                    }

                    for (int i = 0; i < tierListView.getHiddenChildCount(); i++) {
                        View ch = tierListView.getHiddenChildAt(i);
                        if (ch instanceof PremiumTierCell) {
                            PremiumTierCell otherCell = (PremiumTierCell) ch;
                            if (otherCell.getTier() != tierCell.getTier()) {
                                otherCell.setChecked(false, true);
                            }
                        }
                    }

                    for (int i = 0; i < tierListView.getCachedChildCount(); i++) {
                        View ch = tierListView.getCachedChildAt(i);
                        if (ch instanceof PremiumTierCell) {
                            PremiumTierCell otherCell = (PremiumTierCell) ch;
                            if (otherCell.getTier() != tierCell.getTier()) {
                                otherCell.setChecked(false, true);
                            }
                        }
                    }

                    for (int i = 0; i < tierListView.getAttachedScrapChildCount(); i++) {
                        View ch = tierListView.getAttachedScrapChildAt(i);
                        if (ch instanceof PremiumTierCell) {
                            PremiumTierCell otherCell = (PremiumTierCell) ch;
                            if (otherCell.getTier() != tierCell.getTier()) {
                                otherCell.setChecked(false, true);
                            }
                        }
                    }

                    AndroidUtilities.updateViewVisibilityAnimated(buttonContainer, !getUserConfig().isPremium() || currentSubscriptionTier != null && currentSubscriptionTier.getMonths() < subscriptionTiers.get(selectedTierIndex).getMonths() && !forcePremium);
                }
            });
            Path path = new Path();
            float[] radii = new float[8];
            tierListView.setSelectorTransformer(canvas -> {
                View child = tierListView.getPressedChildView();
                int position = child == null ? -1 : tierListView.getChildViewHolder(child).getAdapterPosition();

                path.rewind();
                Rect selectorRect = tierListView.getSelectorRect();
                AndroidUtilities.rectTmp.set(selectorRect.left, selectorRect.top, selectorRect.right, selectorRect.bottom);
                Arrays.fill(radii, 0);
                if (position == 0) {
                    Arrays.fill(radii, 0, 4, AndroidUtilities.dp(12));
                }
                if (position == tierListView.getAdapter().getItemCount() - 1) {
                    Arrays.fill(radii, 4, 8, AndroidUtilities.dp(12));
                }
                path.addRoundRect(AndroidUtilities.rectTmp, radii, Path.Direction.CW);
                canvas.clipPath(path);
            });
            addView(tierListView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 12, 16, 12, 0));

            updatePremiumTiers();
            updateText();
        }

        private void measureGradient(int w, int h) {
            int yOffset = 0;
            for (int i = 0; i < subscriptionTiers.size(); i++) {
                dummyTierCell.bind(subscriptionTiers.get(i), false);
                dummyTierCell.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.AT_MOST));
                subscriptionTiers.get(i).yOffset = yOffset;
                yOffset += dummyTierCell.getMeasuredHeight();
            }

            totalTiersGradientHeight = yOffset;
        }

        @SuppressLint("NotifyDataSetChanged")
        public void updatePremiumTiers() {
            subscriptionTiers.clear();
            selectedTierIndex = -1;
            currentSubscriptionTier = null;
            long pricePerYearMax = 0;
            if (getMediaDataController().getPremiumPromo() != null) {
                for (TLRPC.TL_premiumSubscriptionOption option : getMediaDataController().getPremiumPromo().period_options) {
                    if (getUserConfig().isPremium() && !option.can_purchase_upgrade && !option.current) {
                        continue;
                    }

                    SubscriptionTier subscriptionTier = new SubscriptionTier(option);
                    subscriptionTiers.add(subscriptionTier);
                    if (selectAnnualByDefault) {
                        if (option.months == 12) {
                            selectedTierIndex = subscriptionTiers.size() - 1;
                        }
                    }
                    if (option.current) {
                        currentSubscriptionTier = subscriptionTier;
                    }
                    if (BuildVars.useInvoiceBilling()) {
                        if (subscriptionTier.getPricePerYear() > pricePerYearMax) {
                            pricePerYearMax = subscriptionTier.getPricePerYear();
                        }
                    }
                }
            }
            if (BuildVars.useInvoiceBilling() && getUserConfig().isPremium()) {
                subscriptionTiers.clear();
                currentSubscriptionTier = null;
            } else if (!BuildVars.useInvoiceBilling() && currentSubscriptionTier != null && !Objects.equals(BillingController.getInstance().getLastPremiumTransaction(),
                    currentSubscriptionTier.subscriptionOption != null ? currentSubscriptionTier.subscriptionOption.transaction != null ?
                            currentSubscriptionTier.subscriptionOption.transaction.replaceAll(TRANSACTION_PATTERN, "$1") : null : null) ||
                                currentSubscriptionTier != null && currentSubscriptionTier.getMonths() == 12) {
                subscriptionTiers.clear();
                currentSubscriptionTier = null;
            }

            if (BuildVars.useInvoiceBilling()) {
                for (SubscriptionTier tier : subscriptionTiers) {
                    tier.setPricePerYearRegular(pricePerYearMax);
                }
            } else if (BillingController.getInstance().isReady() && BillingController.PREMIUM_PRODUCT_DETAILS != null) {
                long pricePerMonthMaxStore = 0;

                for (SubscriptionTier subscriptionTier : subscriptionTiers) {
                    subscriptionTier.setGooglePlayProductDetails(BillingController.PREMIUM_PRODUCT_DETAILS);

                    if (subscriptionTier.getPricePerYear() > pricePerMonthMaxStore) {
                        pricePerMonthMaxStore = subscriptionTier.getPricePerYear();
                    }
                }

                for (SubscriptionTier subscriptionTier : subscriptionTiers) {
                    subscriptionTier.setPricePerYearRegular(pricePerMonthMaxStore);
                }
            }

            if (selectedTierIndex == -1) {
                for (int i = 0; i < subscriptionTiers.size(); i++) {
                    SubscriptionTier tier = subscriptionTiers.get(i);
                    if (tier.getMonths() == 12) {
                        selectedTierIndex = i;
                        break;
                    }
                }
                if (selectedTierIndex == -1) {
                    selectedTierIndex = 0;
                }
            }
            updateButtonText(false);
            tierListView.getAdapter().notifyDataSetChanged();
        }

        private boolean setTierListViewVisibility;
        private boolean tierListViewVisible;
        public void updateText() {
            titleView.setText(LocaleController.getString(forcePremium ? R.string.TelegramPremiumSubscribedTitle : R.string.TelegramPremium));
            subtitleView.setText(AndroidUtilities.replaceTags(LocaleController.getString(getUserConfig().isPremium() || forcePremium ? R.string.TelegramPremiumSubscribedSubtitle : R.string.TelegramPremiumSubtitle)));
            boolean tierNotVisible = forcePremium || BuildVars.IS_BILLING_UNAVAILABLE || IS_PREMIUM_TIERS_UNAVAILABLE || subscriptionTiers.size() <= 1;
            if (!setTierListViewVisibility || !tierNotVisible) {
                tierListView.setVisibility(tierNotVisible ? GONE : VISIBLE);
                setTierListViewVisibility = true;
            } else if (tierListView.getVisibility() == VISIBLE && tierNotVisible && tierListViewVisible == tierNotVisible) {
                View v = tierListView;
                ValueAnimator animator = ValueAnimator.ofFloat(1, 0).setDuration(250);
                animator.addUpdateListener(animation -> {
                    float val = (float) animation.getAnimatedValue();
                    v.setAlpha(val);
                    v.setScaleX(val);
                    v.setScaleY(val);

                    float f = animator.getAnimatedFraction();
                    for (int i = 0; i < backgroundView.getChildCount(); i++) {
                        View ch = backgroundView.getChildAt(i);
                        if (ch != tierListView) {
                            float offset = 0;
                            if (ch == imageFrameLayout) {
                                offset -= AndroidUtilities.dp(15) * f;
                            } else {
                                offset += AndroidUtilities.dp(8) * f;
                            }
                            ch.setTranslationY(f * v.getMeasuredHeight() + offset);
                        }
                    }
                });
                animator.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        v.setVisibility(GONE);

                        for (int i = 0; i < backgroundView.getChildCount(); i++) {
                            View ch = backgroundView.getChildAt(i);
                            if (ch != tierListView) {
                                ch.setTranslationY(0);
                            }
                        }
                    }
                });
                animator.setInterpolator(CubicBezierInterpolator.DEFAULT);
                animator.start();
            }
            tierListViewVisible = !tierNotVisible;
        }
    }

    private void updateButtonText(boolean animated) {
        if (premiumButtonView == null || getUserConfig().isPremium() && currentSubscriptionTier != null && subscriptionTiers.get(selectedTierIndex).getMonths() < currentSubscriptionTier.getMonths()) {
            return;
        }
        if (LocaleController.isRTL) {
            animated = false;
        }
        if (BuildVars.IS_BILLING_UNAVAILABLE) {
            premiumButtonView.setButton(getPremiumButtonText(currentAccount, subscriptionTiers.get(selectedTierIndex)), v -> buyPremium(this), animated);
            return;
        }
        if (!BuildVars.useInvoiceBilling() && (!BillingController.getInstance().isReady() || subscriptionTiers.isEmpty() || selectedTierIndex >= subscriptionTiers.size() || subscriptionTiers.get(selectedTierIndex).googlePlayProductDetails == null)) {
            premiumButtonView.setButton(LocaleController.getString(R.string.Loading), v -> {}, animated);
            premiumButtonView.setFlickerDisabled(true);
            return;
        }
        if (!subscriptionTiers.isEmpty()) {
            premiumButtonView.setButton(getPremiumButtonText(currentAccount, subscriptionTiers.get(selectedTierIndex)), v -> {
                SubscriptionTier tier = subscriptionTiers.get(selectedTierIndex);
                BillingFlowParams.SubscriptionUpdateParams updateParams = null;
                if (currentSubscriptionTier != null && currentSubscriptionTier.subscriptionOption != null && currentSubscriptionTier.subscriptionOption.transaction != null) {
                    updateParams = BillingFlowParams.SubscriptionUpdateParams.newBuilder()
                            .setOldPurchaseToken(BillingController.getInstance().getLastPremiumToken())
                            .setReplaceProrationMode(BillingFlowParams.ProrationMode.IMMEDIATE_AND_CHARGE_FULL_PRICE)
                            .build();
                }
                buyPremium(this, tier, "settings", true, updateParams);
            }, animated);
            premiumButtonView.setFlickerDisabled(false);
        }
    }

    @Override
    public boolean isLightStatusBar() {
        return false;
    }

    @Override
    public void onResume() {
        super.onResume();
        backgroundView.imageView.setPaused(false);
        backgroundView.imageView.setDialogVisible(false);
        particlesView.setPaused(false);
    }

    @Override
    public void onPause() {
        super.onPause();
        backgroundView.imageView.setDialogVisible(true);
        particlesView.setPaused(true);
    }

    @Override
    public boolean canBeginSlide() {
        return !backgroundView.imageView.touched;
    }

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        return SimpleThemeDescription.createThemeDescriptions(this::updateColors,
                Theme.key_premiumGradient1, Theme.key_premiumGradient2, Theme.key_premiumGradient3, Theme.key_premiumGradient4,
                Theme.key_premiumGradientBackground1, Theme.key_premiumGradientBackground2, Theme.key_premiumGradientBackground3, Theme.key_premiumGradientBackground4,
                Theme.key_premiumGradientBackgroundOverlay, Theme.key_premiumStartGradient1, Theme.key_premiumStartGradient2, Theme.key_premiumStartSmallStarsColor, Theme.key_premiumStartSmallStarsColor2
        );
    }

    private void updateColors() {
        if (backgroundView == null || actionBar == null) {
            return;
        }
        actionBar.setItemsColor(Theme.getColor(Theme.key_premiumGradientBackgroundOverlay), false);
        actionBar.setItemsBackgroundColor(ColorUtils.setAlphaComponent(Theme.getColor(Theme.key_premiumGradientBackgroundOverlay), 60), false);
        backgroundView.titleView.setTextColor(Theme.getColor(Theme.key_premiumGradientBackgroundOverlay));
        backgroundView.subtitleView.setTextColor(Theme.getColor(Theme.key_premiumGradientBackgroundOverlay));
        particlesView.drawable.updateColors();
        if (backgroundView.imageView.mRenderer != null) {
            backgroundView.imageView.mRenderer.updateColors();
        }
        updateBackgroundImage();
    }

    @Override
    public boolean onBackPressed() {
        if (settingsView != null) {
            closeSetting();
            return false;
        }
        return super.onBackPressed();
    }

    private void closeSetting() {
        settingsView.animate().translationY(AndroidUtilities.dp(1000)).setListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                contentView.removeView(settingsView);
                settingsView = null;
                super.onAnimationEnd(animation);
            }
        });
    }

    @Override
    public Dialog showDialog(Dialog dialog) {
        Dialog d = super.showDialog(dialog);
        updateDialogVisibility(d != null);
        return d;
    }

    @Override
    protected void onDialogDismiss(Dialog dialog) {
        super.onDialogDismiss(dialog);
        updateDialogVisibility(false);
    }

    private void updateDialogVisibility(boolean isVisible) {
        if (isVisible != isDialogVisible) {
            isDialogVisible = isVisible;
            backgroundView.imageView.setDialogVisible(isVisible);
            particlesView.setPaused(isVisible);
            contentView.invalidate();
        }
    }

    private void sentShowScreenStat() {
        if (source == null) {
            return;
        }
        sentShowScreenStat(source);
        source = null;
    }

    public static void sentShowScreenStat(String source) {
        ConnectionsManager connectionsManager = ConnectionsManager.getInstance(UserConfig.selectedAccount);
        TLRPC.TL_help_saveAppLog req = new TLRPC.TL_help_saveAppLog();
        TLRPC.TL_inputAppEvent event = new TLRPC.TL_inputAppEvent();
        event.time = connectionsManager.getCurrentTime();
        event.type = "premium.promo_screen_show";
        TLRPC.TL_jsonObject data = new TLRPC.TL_jsonObject();
        event.data = data;

        TLRPC.TL_jsonObjectValue sourceObj = new TLRPC.TL_jsonObjectValue();
        TLRPC.JSONValue sourceVal;
        if (source != null) {
            TLRPC.TL_jsonString jsonString = new TLRPC.TL_jsonString();
            jsonString.value = source;
            sourceVal = jsonString;
        } else {
            sourceVal = new TLRPC.TL_jsonNull();
        }

        sourceObj.key = "source";
        sourceObj.value = sourceVal;

        data.value.add(sourceObj);
        req.events.add(event);

        connectionsManager.sendRequest(req, (response, error) -> {

        });
    }

    public static void sentPremiumButtonClick() {
        TLRPC.TL_help_saveAppLog req = new TLRPC.TL_help_saveAppLog();
        TLRPC.TL_inputAppEvent event = new TLRPC.TL_inputAppEvent();
        event.time = ConnectionsManager.getInstance(UserConfig.selectedAccount).getCurrentTime();
        event.type = "premium.promo_screen_accept";
        event.data = new TLRPC.TL_jsonNull();
        req.events.add(event);

        ConnectionsManager.getInstance(UserConfig.selectedAccount).sendRequest(req, (response, error) -> {

        });
    }

    public static void sentPremiumBuyCanceled() {
        TLRPC.TL_help_saveAppLog req = new TLRPC.TL_help_saveAppLog();
        TLRPC.TL_inputAppEvent event = new TLRPC.TL_inputAppEvent();
        event.time = ConnectionsManager.getInstance(UserConfig.selectedAccount).getCurrentTime();
        event.type = "premium.promo_screen_fail";
        event.data = new TLRPC.TL_jsonNull();
        req.events.add(event);

        ConnectionsManager.getInstance(UserConfig.selectedAccount).sendRequest(req, (response, error) -> {

        });
    }

    public static void sentShowFeaturePreview(int currentAccount, int type) {
        TLRPC.TL_help_saveAppLog req = new TLRPC.TL_help_saveAppLog();
        TLRPC.TL_inputAppEvent event = new TLRPC.TL_inputAppEvent();
        event.time = ConnectionsManager.getInstance(currentAccount).getCurrentTime();
        event.type = "premium.promo_screen_tap";
        TLRPC.TL_jsonObject data = new TLRPC.TL_jsonObject();
        event.data = data;
        TLRPC.TL_jsonObjectValue item = new TLRPC.TL_jsonObjectValue();
        String value = PremiumPreviewFragment.featureTypeToServerString(type);
        if (value != null) {
            TLRPC.TL_jsonString jsonString = new TLRPC.TL_jsonString();
            jsonString.value = value;
            item.value = jsonString;
        } else {
            item.value = new TLRPC.TL_jsonNull();
        }
        item.key = "item";
        data.value.add(item);
        req.events.add(event);

        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {

        });
    }

    public final static class SubscriptionTier {
        public final TLRPC.TL_premiumSubscriptionOption subscriptionOption;
        private int discount;
        private long pricePerMonth;
        private long pricePerYear;

        private long pricePerYearRegular;
        private ProductDetails googlePlayProductDetails;
        private ProductDetails.SubscriptionOfferDetails offerDetails;

        public int yOffset;

        public SubscriptionTier(TLRPC.TL_premiumSubscriptionOption subscriptionOption) {
            this.subscriptionOption = subscriptionOption;
        }

        public ProductDetails getGooglePlayProductDetails() {
            return googlePlayProductDetails;
        }

        public ProductDetails.SubscriptionOfferDetails getOfferDetails() {
            checkOfferDetails();
            return offerDetails;
        }

        public void setGooglePlayProductDetails(ProductDetails googlePlayProductDetails) {
            this.googlePlayProductDetails = googlePlayProductDetails;
        }

        public void setPricePerYearRegular(long pricePerYearRegular) {
            this.pricePerYearRegular = pricePerYearRegular;
        }

        public int getMonths() {
            return subscriptionOption.months;
        }

        public int getDiscount() {
            if (discount == 0) {
                if (getPricePerMonth() == 0) {
                    return 0;
                }

                if (pricePerYearRegular != 0) {
                    discount = (int) ((1.0 - getPricePerYear() / (double) pricePerYearRegular) * 100);

                    if (discount == 0) {
                        discount = -1;
                    }
                }
            }
            return discount;
        }

        public long getPricePerYear() {
            if (pricePerYear == 0) {
                long price = getPrice();
                if (price != 0) {
                    pricePerYear = (long) ((double) price / subscriptionOption.months * 12);
                }
            }
            return pricePerYear;
        }

        public long getPricePerMonth() {
            if (pricePerMonth == 0) {
                long price = getPrice();
                if (price != 0) {
                    pricePerMonth = price / subscriptionOption.months;
                }
            }
            return pricePerMonth;
        }

        public String getFormattedPricePerYearRegular() {
            if (BuildVars.useInvoiceBilling() || subscriptionOption.store_product == null) {
                return BillingController.getInstance().formatCurrency(pricePerYearRegular, getCurrency());
            }

            return googlePlayProductDetails == null ? "" : BillingController.getInstance().formatCurrency(pricePerYearRegular, getCurrency(), 6);
        }

        public String getFormattedPricePerYear() {
            if (BuildVars.useInvoiceBilling() || subscriptionOption.store_product == null) {
                return BillingController.getInstance().formatCurrency(getPricePerYear(), getCurrency());
            }

            return googlePlayProductDetails == null ? "" : BillingController.getInstance().formatCurrency(getPricePerYear(), getCurrency(), 6);
        }

        public String getFormattedPricePerMonth() {
            if (BuildVars.useInvoiceBilling() || subscriptionOption.store_product == null) {
                return BillingController.getInstance().formatCurrency(getPricePerMonth(), getCurrency());
            }

            return googlePlayProductDetails == null ? "" : BillingController.getInstance().formatCurrency(getPricePerMonth(), getCurrency(), 6);
        }

        public String getFormattedPrice() {
            if (BuildVars.useInvoiceBilling() || subscriptionOption.store_product == null) {
                return BillingController.getInstance().formatCurrency(getPrice(), getCurrency());
            }

            return googlePlayProductDetails == null ? "" : BillingController.getInstance().formatCurrency(getPrice(), getCurrency(), 6);
        }

        public long getPrice() {
            if (BuildVars.useInvoiceBilling() || subscriptionOption.store_product == null) {
                return subscriptionOption.amount;
            }
            if (googlePlayProductDetails == null) {
                return 0;
            }
            checkOfferDetails();
            return offerDetails == null ? 0 : offerDetails.getPricingPhases().getPricingPhaseList().get(0).getPriceAmountMicros();
        }

        public String getCurrency() {
            if (BuildVars.useInvoiceBilling() || subscriptionOption.store_product == null) {
                return subscriptionOption.currency;
            }
            if (googlePlayProductDetails == null) {
                return "";
            }
            checkOfferDetails();
            return offerDetails == null ? "" : offerDetails.getPricingPhases().getPricingPhaseList().get(0).getPriceCurrencyCode();
        }

        private void checkOfferDetails() {
            if (googlePlayProductDetails == null) {
                return;
            }

            if (offerDetails == null) {
                for (ProductDetails.SubscriptionOfferDetails details : googlePlayProductDetails.getSubscriptionOfferDetails()) {
                    String period = details.getPricingPhases().getPricingPhaseList().get(0).getBillingPeriod();
                    if (getMonths() == 12 ? period.equals("P1Y") : period.equals(String.format(Locale.ROOT, "P%dM", getMonths()))) {
                        offerDetails = details;
                        break;
                    }
                }
            }
        }
    }
}
