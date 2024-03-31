package org.telegram.ui.Components.Premium;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Path;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Scroller;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SvgHelper;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.BottomPagesView;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RLottieDrawable;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.PremiumPreviewFragment;
import org.telegram.ui.ThemePreviewActivity;

import java.lang.reflect.Field;
import java.util.ArrayList;

public class PremiumFeatureBottomSheet extends BottomSheet implements NotificationCenter.NotificationCenterDelegate {

    private final BaseFragment baseFragment;
    private PremiumButtonView premiumButtonView;
    ArrayList<PremiumPreviewFragment.PremiumFeatureData> premiumFeatures = new ArrayList<>();

    float containerViewsProgress;
    float progressToFullscreenView;
    float progressToGradient;
    boolean fullscreenNext;
    boolean containerViewsForward;
    ViewPager viewPager;
    FrameLayout content;
    int contentHeight;

    private FrameLayout buttonContainer;
    FrameLayout closeLayout;
    boolean enterAnimationIsRunning;
    SvgHelper.SvgDrawable svgIcon;
    private final int startType;
    private final boolean onlySelectedType;
    private boolean forceAbout;

    int selectedPosition;
    int toPosition;
    float progress;

    private PremiumPreviewFragment.SubscriptionTier selectedTier;
    private int gradientAlpha = 255;
    int topGlobalOffset;
    int topCurrentOffset;
    ActionBar actionBar;

    public PremiumFeatureBottomSheet(BaseFragment fragment, int startType, boolean onlySelectedType) {
        this(fragment, startType, onlySelectedType, null);
    }

    public PremiumFeatureBottomSheet(BaseFragment fragment, int startType, boolean onlySelectedType, PremiumPreviewFragment.SubscriptionTier subscriptionTier) {
        this(fragment, fragment.getContext(), fragment.getCurrentAccount(), false, startType, onlySelectedType, subscriptionTier);
    }

    public PremiumFeatureBottomSheet(BaseFragment fragment, Context context, int currentAccount, int startType, boolean onlySelectedType) {
        this(fragment, context, currentAccount, false, startType, onlySelectedType, null);
    }

    public PremiumFeatureBottomSheet(BaseFragment fragment, Context context, int currentAccount, boolean business, int startType, boolean onlySelectedType, PremiumPreviewFragment.SubscriptionTier subscriptionTier) {
        super(context, false, getResourceProvider(fragment));
        this.baseFragment = fragment;
        if (fragment == null) {
            throw new RuntimeException("fragmnet can't be null");
        }
        selectedTier = subscriptionTier;

        fixNavigationBar(getThemedColor(Theme.key_dialogBackground));
        this.startType = startType;
        this.onlySelectedType = onlySelectedType;

        String svg = RLottieDrawable.readRes(null, R.raw.star_loader);
        svgIcon = SvgHelper.getDrawable(svg);
        FrameLayout frameLayout = new FrameLayout(getContext()) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                if (isPortrait) {
                    contentHeight = MeasureSpec.getSize(widthMeasureSpec);
                } else {
                    contentHeight = (int) (MeasureSpec.getSize(heightMeasureSpec) * 0.65f);
                }
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            }
        };

        if (business || startType == PremiumPreviewFragment.PREMIUM_FEATURE_FOLDER_TAGS) {
            PremiumPreviewFragment.fillBusinessFeaturesList(premiumFeatures, currentAccount, false);
            PremiumPreviewFragment.fillBusinessFeaturesList(premiumFeatures, currentAccount, true);
        } else {
            PremiumPreviewFragment.fillPremiumFeaturesList(premiumFeatures, currentAccount, false);
        }

        int selectedPositionLocal = 0;
        for (int i = 0; i < premiumFeatures.size(); i++) {
            if (premiumFeatures.get(i).type == startType) {
                selectedPositionLocal = i;
                break;
            }
        }

        if (onlySelectedType) {
            PremiumPreviewFragment.PremiumFeatureData selectedFeature = premiumFeatures.get(selectedPositionLocal);
            premiumFeatures.clear();
            premiumFeatures.add(selectedFeature);
            selectedPositionLocal = 0;
        }

        PremiumPreviewFragment.PremiumFeatureData featureData = premiumFeatures.get(selectedPositionLocal);

        setApplyTopPadding(false);
        setApplyBottomPadding(false);
        useBackgroundTopPadding = false;
        PremiumGradient.PremiumGradientTools gradientTools = new PremiumGradient.PremiumGradientTools(Theme.key_premiumGradientBottomSheet1, Theme.key_premiumGradientBottomSheet2, Theme.key_premiumGradientBottomSheet3, -1);
        gradientTools.x1 = 0;
        gradientTools.y1 = 1.1f;
        gradientTools.x2 = 1.5f;
        gradientTools.y2 = -0.2f;
        gradientTools.exactly = true;
        content = new FrameLayout(getContext()) {

            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                int h = contentHeight;
                super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(h + AndroidUtilities.dp(2), MeasureSpec.EXACTLY));
            }

            @Override
            protected void dispatchDraw(Canvas canvas) {
                gradientTools.gradientMatrix(0, 0, getMeasuredWidth(), getMeasuredHeight(), 0, 0);
                AndroidUtilities.rectTmp.set(0, AndroidUtilities.dp(2), getMeasuredWidth(), getMeasuredHeight() + AndroidUtilities.dp(18));
                canvas.save();
                canvas.clipRect(0, 0, getMeasuredWidth(), getMeasuredHeight());
                gradientTools.paint.setAlpha(gradientAlpha);
                canvas.drawRoundRect(AndroidUtilities.rectTmp, AndroidUtilities.dp(12) - 1, AndroidUtilities.dp(12) - 1, gradientTools.paint);
                canvas.restore();
                super.dispatchDraw(canvas);
            }
        };

        closeLayout = new FrameLayout(getContext());
        ImageView closeImage = new ImageView(getContext());
        closeImage.setImageResource(R.drawable.msg_close);
        closeImage.setBackground(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(12), ColorUtils.setAlphaComponent(Color.WHITE, 40), ColorUtils.setAlphaComponent(Color.WHITE, 100)));
        closeLayout.addView(closeImage, LayoutHelper.createFrame(24, 24, Gravity.CENTER));
        closeLayout.setOnClickListener(v -> dismiss());
        frameLayout.addView(content, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 16, 0, 0));

        viewPager = new ViewPager(getContext()) {

            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                int h = AndroidUtilities.dp(100);
                if (getChildCount() > 0) {
                    getChildAt(0).measure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
                    h = getChildAt(0).getMeasuredHeight();
                }
                super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(h + topGlobalOffset, MeasureSpec.EXACTLY));
            }

            private boolean processTap(MotionEvent ev, boolean intercept) {
                if (ev.getAction() == MotionEvent.ACTION_DOWN) {
                    lastTapTime = System.currentTimeMillis();
                    return true;
                } else if (ev.getAction() == MotionEvent.ACTION_UP) {
                    if ((System.currentTimeMillis() - lastTapTime) <= ViewConfiguration.getTapTimeout() && (scroller != null && scroller.isFinished())) {
                        smoothScroll = true;
                        if (ev.getX() > getWidth() * .45f) {
                            if (selectedPosition + 1 < premiumFeatures.size()) {
                                setCurrentItem(selectedPosition + 1, true);
                            }
                        } else {
                            if (selectedPosition - 1 >= 0) {
                                setCurrentItem(selectedPosition - 1, true);
                            }
                        }
                        smoothScroll = false;
                    }
                } else if (ev.getAction() == MotionEvent.ACTION_CANCEL) {
                    lastTapTime = -1;
                }
                return false;
            }

            @Override
            public boolean onInterceptTouchEvent(MotionEvent ev) {
                try {
                    processTap(ev, true);
                    return super.onInterceptTouchEvent(ev);
                } catch (Exception e) {
                    return false;
                }
            }

            long lastTapTime;
            @Override
            public boolean onTouchEvent(MotionEvent ev) {
                if (enterAnimationIsRunning) {
                    return false;
                }
                boolean r = processTap(ev, false);
                return super.onTouchEvent(ev) || r;
            }

            private boolean smoothScroll;
            private Scroller scroller;
            {
                try {
                    Class<?> viewpager = ViewPager.class;
                    Field scroller = viewpager.getDeclaredField("mScroller");
                    scroller.setAccessible(true);
                    this.scroller = new Scroller(getContext()) {
                        @Override
                        public void startScroll(int startX, int startY, int dx, int dy, int duration) {
                            super.startScroll(startX, startY, dx, dy, (smoothScroll ? 3 : 1) * duration);
                        }
                    };
                    scroller.set(this, this.scroller);
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
        };
        viewPager.setOverScrollMode(View.OVER_SCROLL_NEVER);
        viewPager.setOffscreenPageLimit(0);
        PagerAdapter pagerAdapter = new PagerAdapter() {
            @Override
            public int getCount() {
                return premiumFeatures.size();
            }

            @NonNull
            @Override
            public Object instantiateItem(@NonNull ViewGroup container, int position) {
                ViewPage viewPage = new ViewPage(getContext(), position);
                container.addView(viewPage);
                viewPage.position = position;
                viewPage.setFeatureDate(premiumFeatures.get(position));
                return viewPage;
            }

            @Override
            public void destroyItem(@NonNull ViewGroup container, int position, @NonNull Object object) {
                container.removeView((View) object);
            }

            @Override
            public boolean isViewFromObject(@NonNull View view, @NonNull Object object) {
                return view == object;
            }
        };
        viewPager.setAdapter(pagerAdapter);
        viewPager.setCurrentItem(selectedPosition = selectedPositionLocal);
        frameLayout.addView(viewPager, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 100, 0, 0, 18, 0, 0));

        frameLayout.addView(closeLayout, LayoutHelper.createFrame(52, 52, Gravity.RIGHT | Gravity.TOP, 0, 24, 0, 0));
        BottomPagesView bottomPages = new BottomPagesView(getContext(), viewPager, premiumFeatures.size());
        viewPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {

            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
                bottomPages.setPageOffset(position, positionOffset);
                selectedPosition = position;
                toPosition = positionOffsetPixels > 0 ? selectedPosition + 1 : selectedPosition - 1;
                progress = positionOffset;
                checkPage();
            }

            @Override
            public void onPageSelected(int i) {
                if (premiumFeatures.get(i).type == PremiumPreviewFragment.PREMIUM_FEATURE_LIMITS) {
                    actionBar.setTitle(LocaleController.getString("DoubledLimits", R.string.DoubledLimits));
                    actionBar.requestLayout();
                } else if (premiumFeatures.get(i).type == PremiumPreviewFragment.PREMIUM_FEATURE_STORIES) {
                    actionBar.setTitle(LocaleController.getString("UpgradedStories", R.string.UpgradedStories));
                    actionBar.requestLayout();
                } else if (premiumFeatures.get(i).type == PremiumPreviewFragment.PREMIUM_FEATURE_BUSINESS) {
                    actionBar.setTitle(LocaleController.getString(R.string.TelegramBusiness));
                    actionBar.requestLayout();
                }
                checkPage();
            }

            private void checkPage() {
                for (int i = 0; i < viewPager.getChildCount(); i++) {
                    ViewPage page = (ViewPage) viewPager.getChildAt(i);
                    float offset = 0;
                    if (!enterAnimationIsRunning || !(page.topView instanceof PremiumAppIconsPreviewView)) {
                        if (page.position == selectedPosition) {
                            page.topHeader.setOffset(offset = -page.getMeasuredWidth() * progress);
                        } else if (page.position == toPosition) {
                            page.topHeader.setOffset(offset = -page.getMeasuredWidth() * progress + page.getMeasuredWidth());
                        } else {
                            page.topHeader.setOffset(page.getMeasuredWidth());
                        }
                    }

                    if (page.topView instanceof PremiumAppIconsPreviewView) {
                        page.setTranslationX(-offset);
                        page.title.setTranslationX(offset);
                        page.description.setTranslationX(offset);
                    }
                }
                containerViewsProgress = progress;
                containerViewsForward = toPosition > selectedPosition;
                boolean selectedFullscreen = selectedPosition >= 0 && selectedPosition < premiumFeatures.size() && isFullscreenType(premiumFeatures.get(selectedPosition).type);
                boolean nextFullscreen = toPosition >= 0 && toPosition < premiumFeatures.size() && isFullscreenType(premiumFeatures.get(toPosition).type);
                if (selectedFullscreen && nextFullscreen) {
                    progressToGradient = 1f;
                    progressToFullscreenView = progress == 0 ? 1f : progress;
                    fullscreenNext = true;
                } else if (selectedFullscreen) {
                    progressToGradient = progressToFullscreenView = 1f - progress;
                    fullscreenNext = true;
                } else if (nextFullscreen) {
                    progressToGradient = progressToFullscreenView = progress;
                    fullscreenNext = false;
                } else {
                    progressToGradient = progressToFullscreenView = 0;
                    fullscreenNext = true;
                }

                int localGradientAlpha = (int) (255 * (1f - progressToFullscreenView));
                if (localGradientAlpha != gradientAlpha) {
                    gradientAlpha = localGradientAlpha;
                    content.invalidate();
                    AndroidUtilities.runOnUIThread(() -> {
                        checkTopOffset();
                    });
                }
            }

            @Override
            public void onPageScrollStateChanged(int i) {

            }
        });

        LinearLayout linearLayout = new LinearLayout(getContext());
        linearLayout.addView(frameLayout);
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        bottomPages.setColor(Theme.key_chats_unreadCounterMuted, Theme.key_chats_actionBackground);
        if (!onlySelectedType) {
            linearLayout.addView(bottomPages, LayoutHelper.createLinear(11 * premiumFeatures.size(), 5, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 10));
        }
        premiumButtonView = new PremiumButtonView(getContext(), true, resourcesProvider);
        premiumButtonView.buttonLayout.setOnClickListener(v -> {
            if (fragment instanceof ChatActivity) {
                ((ChatActivity) fragment).closeMenu();
                if (((ChatActivity) fragment).chatAttachAlert != null) {
                    ((ChatActivity) fragment).chatAttachAlert.dismiss(true);
                }
            }
            BaseFragment mainFragment = LaunchActivity.getLastFragment();
            for (int i = 0; i < 2; i++) {
                BaseFragment currentFragment = i == 0 ? fragment : mainFragment;
                if (currentFragment != null && currentFragment.getLastStoryViewer() != null) {
                    currentFragment.getLastStoryViewer().dismissVisibleDialogs();
                }
                if (currentFragment != null && currentFragment.getVisibleDialog() != null) {
                    currentFragment.getVisibleDialog().dismiss();
                }
            }
            if ((onlySelectedType || forceAbout) && fragment != null) {
                BaseFragment premiumFragment = new PremiumPreviewFragment(PremiumPreviewFragment.featureTypeToServerString(featureData.type));
                if (fragment instanceof ThemePreviewActivity) {
                    BaseFragment.BottomSheetParams params = new BaseFragment.BottomSheetParams();
                    params.transitionFromLeft = true;
                    params.allowNestedScroll = false;
                    fragment.showAsSheet(premiumFragment, params);
                } else {
                    fragment.presentFragment(premiumFragment);
                }
            } else {
                PremiumPreviewFragment.buyPremium(fragment, selectedTier, PremiumPreviewFragment.featureTypeToServerString(featureData.type));
            }
            dismiss();
        });
        premiumButtonView.overlayTextView.setOnClickListener(v -> {
            dismiss();
        });
        buttonContainer = new FrameLayout(getContext());

        buttonContainer.addView(premiumButtonView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.CENTER_VERTICAL, 16, 0, 16, 0));
        buttonContainer.setBackgroundColor(getThemedColor(Theme.key_dialogBackground));
        linearLayout.addView(buttonContainer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 68, Gravity.BOTTOM));

        if (UserConfig.getInstance(currentAccount).isPremium()) {
            premiumButtonView.setOverlayText(LocaleController.getString("OK", R.string.OK), false, false);
        }

        ScrollView scrollView = new ScrollView(getContext());
        scrollView.addView(linearLayout);
        setCustomView(scrollView);

        MediaDataController.getInstance(currentAccount).preloadPremiumPreviewStickers();
        setButtonText();
        customViewGravity = Gravity.LEFT | Gravity.BOTTOM;

        Drawable headerShadowDrawable = ContextCompat.getDrawable(getContext(), R.drawable.header_shadow).mutate();

        containerView = new FrameLayout(getContext()) {

            @Override
            public boolean hasOverlappingRendering() {
                return false;
            }

            @Override
            public void setTranslationY(float translationY) {
                super.setTranslationY(translationY);
                onContainerTranslationYChanged(translationY);
            }

            int lastSize;

            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                int size = widthMeasureSpec + heightMeasureSpec << 16;
                //  if (size != lastSize) {
                lastSize = size;
                topGlobalOffset = 0;
                scrollView.measure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(heightMeasureSpec), MeasureSpec.AT_MOST));

                topGlobalOffset = MeasureSpec.getSize(heightMeasureSpec) - scrollView.getMeasuredHeight() + backgroundPaddingTop;
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                checkTopOffset();
//                } else {
//                    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
//                }
            }

            @Override
            protected void dispatchDraw(Canvas canvas) {
                shadowDrawable.setBounds(0, topCurrentOffset + backgroundPaddingTop - AndroidUtilities.dp(2) + 1, getMeasuredWidth(), getMeasuredHeight());
                shadowDrawable.draw(canvas);
                super.dispatchDraw(canvas);
                if (actionBar != null && actionBar.getVisibility() == View.VISIBLE && actionBar.getAlpha() != 0) {
                    headerShadowDrawable.setBounds(0, actionBar.getBottom(), getMeasuredWidth(), actionBar.getBottom() + headerShadowDrawable.getIntrinsicHeight());
                    headerShadowDrawable.setAlpha((int) (255 * actionBar.getAlpha()));
                    headerShadowDrawable.draw(canvas);
                }
            }

            Path path = new Path();

            @Override
            protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
                if (child == scrollView) {
                    canvas.save();
                    path.rewind();
                    AndroidUtilities.rectTmp.set(0, topCurrentOffset + AndroidUtilities.dp(18), getMeasuredWidth(), getMeasuredHeight());
                    path.addRoundRect(AndroidUtilities.rectTmp, AndroidUtilities.dp(18), AndroidUtilities.dp(18), Path.Direction.CW);
                    canvas.clipPath(path);
                    super.drawChild(canvas, child, drawingTime);
                    canvas.restore();
                    return true;
                }
                return super.drawChild(canvas, child, drawingTime);
            }

            @Override
            public boolean dispatchTouchEvent(MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    if (event.getY() < topCurrentOffset - backgroundPaddingTop + AndroidUtilities.dp(2)) {
                        dismiss();
                    }
                }
                return super.dispatchTouchEvent(event);
            }
        };
        containerView.setPadding(backgroundPaddingLeft, backgroundPaddingTop - 1, backgroundPaddingLeft, 0);
    }

    private static Theme.ResourcesProvider getResourceProvider(BaseFragment fragment) {
        if (fragment != null) {
            if (fragment.getLastStoryViewer() != null && fragment.getLastStoryViewer().isShown()) {
                return fragment.getLastStoryViewer().getResourceProvider();
            }
            return fragment.getResourceProvider();
        }
        return null;
    }

    private boolean isFullscreenType(int type) {
        return type == PremiumPreviewFragment.PREMIUM_FEATURE_LIMITS || type == PremiumPreviewFragment.PREMIUM_FEATURE_STORIES || type == PremiumPreviewFragment.PREMIUM_FEATURE_BUSINESS;
    }

    public void hideButton() {
        buttonContainer.setVisibility(View.GONE);
    }

    public PremiumFeatureBottomSheet setForceAbout() {
        this.forceAbout = true;
        premiumButtonView.clearOverlayText();
        setButtonText();
        return this;
    }

    private void setButtonText() {
        if (forceAbout) {
            premiumButtonView.buttonTextView.setText(LocaleController.getString(R.string.AboutTelegramPremium));
        } else if (onlySelectedType) {
            if (startType == PremiumPreviewFragment.PREMIUM_FEATURE_REACTIONS) {
                premiumButtonView.buttonTextView.setText(LocaleController.getString(R.string.UnlockPremiumReactions));
                premiumButtonView.setIcon(R.raw.unlock_icon);
            } else if (startType == PremiumPreviewFragment.PREMIUM_FEATURE_APPLICATION_ICONS) {
                premiumButtonView.buttonTextView.setText(LocaleController.getString(R.string.UnlockPremiumIcons));
                premiumButtonView.setIcon(R.raw.unlock_icon);
            } else {
                premiumButtonView.buttonTextView.setText(LocaleController.getString(R.string.AboutTelegramPremium));
            }
        } else {
            premiumButtonView.buttonTextView.setText(PremiumPreviewFragment.getPremiumButtonText(currentAccount, selectedTier));
        }
    }

    @Override
    public void show() {
        super.show();
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.stopAllHeavyOperations, 16);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.billingProductDetailsUpdated);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.premiumPromoUpdated);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.currentUserPremiumStatusChanged);

        actionBar = new ActionBar(getContext()) {
            @Override
            public void setAlpha(float alpha) {
                if (getAlpha() != alpha) {
                    super.setAlpha(alpha);
                    containerView.invalidate();
                }
            }

            @Override
            public void setTag(Object tag) {
                super.setTag(tag);
                updateStatusBar();
            }
        };
        actionBar.setBackgroundColor(getThemedColor(Theme.key_dialogBackground));
        actionBar.setTitleColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
        actionBar.setItemsBackgroundColor(getThemedColor(Theme.key_actionBarActionModeDefaultSelector), false);
        actionBar.setItemsColor(getThemedColor(Theme.key_actionBarActionModeDefaultIcon), false);
        actionBar.setItemsColor(getThemedColor(Theme.key_actionBarActionModeDefaultIcon), true);

        actionBar.setCastShadows(true);
        actionBar.setExtraHeight(AndroidUtilities.dp(2));
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    dismiss();
                }
            }
        });
        containerView.addView(actionBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 0, 0));
        ((FrameLayout.LayoutParams) actionBar.getLayoutParams()).topMargin = -backgroundPaddingTop - AndroidUtilities.dp(2);
        AndroidUtilities.updateViewVisibilityAnimated(actionBar, false, 1f, false);

        if (premiumFeatures.get(selectedPosition).type == PremiumPreviewFragment.PREMIUM_FEATURE_STORIES) {
            actionBar.setTitle(LocaleController.getString("UpgradedStories", R.string.UpgradedStories));
            actionBar.requestLayout();
        } else if (premiumFeatures.get(selectedPosition).type == PremiumPreviewFragment.PREMIUM_FEATURE_BUSINESS) {
            actionBar.setTitle(LocaleController.getString(R.string.TelegramBusiness));
            actionBar.requestLayout();
        }  else {
            actionBar.setTitle(LocaleController.getString("DoubledLimits", R.string.DoubledLimits));
            actionBar.requestLayout();
        }
    }

    @Override
    public void dismiss() {
        super.dismiss();

        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.billingProductDetailsUpdated);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.premiumPromoUpdated);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.currentUserPremiumStatusChanged);

        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.startAllHeavyOperations, 16);
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.billingProductDetailsUpdated || id == NotificationCenter.premiumPromoUpdated) {
            setButtonText();
        } else if (id == NotificationCenter.currentUserPremiumStatusChanged) {
            if (UserConfig.getInstance(currentAccount).isPremium()) {
                premiumButtonView.setOverlayText(LocaleController.getString("OK", R.string.OK), false, true);
            } else {
                premiumButtonView.clearOverlayText();
            }
        }
    }


    private class ViewPage extends LinearLayout {

        public int position;
        TextView title;
        TextView description;
        PagerHeaderView topHeader;
        View topView;
        boolean topViewOnFullHeight;

        public ViewPage(Context context, int p) {
            super(context);
            setOrientation(VERTICAL);
            topView = getViewForPosition(context, p);
            addView(topView);
            topHeader = (PagerHeaderView) topView;

            title = new TextView(context);
            title.setGravity(Gravity.CENTER_HORIZONTAL);
            title.setTextColor(getThemedColor(Theme.key_dialogTextBlack));
            title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
            title.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));

            addView(title, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 21, 20, 21, 0));

            description = new TextView(context);
            description.setGravity(Gravity.CENTER_HORIZONTAL);
            description.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            description.setTextColor(getThemedColor(Theme.key_dialogTextBlack));
            if (!onlySelectedType) {
                description.setLines(2);
            }
            addView(description, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 21, 10, 21, 16));
            setClipChildren(false);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            title.setVisibility(View.VISIBLE);
            if (topView instanceof BaseListPageView) {
                ((BaseListPageView) topView).setTopOffset(topGlobalOffset);
            }
            topView.getLayoutParams().height = contentHeight;
            description.setVisibility(isPortrait ? View.VISIBLE : View.GONE);
            MarginLayoutParams layoutParams = (MarginLayoutParams) title.getLayoutParams();
            if (isPortrait) {
                layoutParams.topMargin = AndroidUtilities.dp(20);
                layoutParams.bottomMargin = 0;
            } else {
                layoutParams.topMargin = AndroidUtilities.dp(10);
                layoutParams.bottomMargin = AndroidUtilities.dp(10);
            }
            ((MarginLayoutParams) topView.getLayoutParams()).bottomMargin = 0;
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            if (topViewOnFullHeight) {
                topView.getLayoutParams().height = getMeasuredHeight() - AndroidUtilities.dp(16);
                ((MarginLayoutParams) topView.getLayoutParams()).bottomMargin = AndroidUtilities.dp(16);
                title.setVisibility(View.GONE);
                description.setVisibility(View.GONE);
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            }
        }

        @Override
        protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
            if (child == topView) {
                if (child instanceof BaseListPageView) {
                    setTranslationY(0);
                } else {
                    setTranslationY(topGlobalOffset);
                }
                if (child instanceof CarouselView || child instanceof BaseListPageView) {
                    return super.drawChild(canvas, child, drawingTime);
                }
                canvas.save();
                canvas.clipRect(0, 0, child.getMeasuredWidth(), child.getMeasuredHeight());
                boolean b = super.drawChild(canvas, child, drawingTime);
                canvas.restore();
                return b;
            } else {
                return super.drawChild(canvas, child, drawingTime);
            }
        }

        void setFeatureDate(PremiumPreviewFragment.PremiumFeatureData featureData) {
            if (featureData.type == PremiumPreviewFragment.PREMIUM_FEATURE_LIMITS || featureData.type == PremiumPreviewFragment.PREMIUM_FEATURE_STORIES || featureData.type == PremiumPreviewFragment.PREMIUM_FEATURE_BUSINESS) {
                title.setText("");
                description.setText("");
                topViewOnFullHeight = true;
            } else if (onlySelectedType) {
                if (startType == PremiumPreviewFragment.PREMIUM_FEATURE_REACTIONS) {
                    title.setText(LocaleController.getString("AdditionalReactions", R.string.AdditionalReactions));
                    description.setText(AndroidUtilities.replaceTags(LocaleController.getString("AdditionalReactionsDescription", R.string.AdditionalReactionsDescription)));
                } else if (startType == PremiumPreviewFragment.PREMIUM_FEATURE_ADS) {
                    title.setText(LocaleController.getString(R.string.PremiumPreviewNoAds));
                    description.setText(AndroidUtilities.replaceTags(LocaleController.getString(R.string.PremiumPreviewNoAdsDescription2)));
                } else if (startType == PremiumPreviewFragment.PREMIUM_FEATURE_SAVED_TAGS) {
                    title.setText(LocaleController.getString(R.string.PremiumPreviewTags));
                    description.setText(AndroidUtilities.replaceTags(LocaleController.getString(R.string.PremiumPreviewTagsDescription)));
                } else if (startType == PremiumPreviewFragment.PREMIUM_FEATURE_APPLICATION_ICONS) {
                    title.setText(LocaleController.getString("PremiumPreviewAppIcon", R.string.PremiumPreviewAppIcon));
                    description.setText(AndroidUtilities.replaceTags(LocaleController.getString("PremiumPreviewAppIconDescription2", R.string.PremiumPreviewAppIconDescription2)));
                } else if (startType == PremiumPreviewFragment.PREMIUM_FEATURE_DOWNLOAD_SPEED) {
                    title.setText(LocaleController.getString(R.string.PremiumPreviewDownloadSpeed));
                    description.setText(AndroidUtilities.replaceTags(LocaleController.getString(R.string.PremiumPreviewDownloadSpeedDescription2)));
                } else if (startType == PremiumPreviewFragment.PREMIUM_FEATURE_ADVANCED_CHAT_MANAGEMENT) {
                    title.setText(LocaleController.getString(R.string.PremiumPreviewAdvancedChatManagement));
                    description.setText(AndroidUtilities.replaceTags(LocaleController.getString(R.string.PremiumPreviewAdvancedChatManagementDescription2)));
                } else if (startType == PremiumPreviewFragment.PREMIUM_FEATURE_VOICE_TO_TEXT) {
                    title.setText(LocaleController.getString(R.string.PremiumPreviewVoiceToText));
                    description.setText(AndroidUtilities.replaceTags(LocaleController.getString(R.string.PremiumPreviewVoiceToTextDescription2)));
                } else if (startType == PremiumPreviewFragment.PREMIUM_FEATURE_TRANSLATIONS) {
                    title.setText(LocaleController.getString(R.string.PremiumPreviewTranslations));
                    description.setText(AndroidUtilities.replaceTags(LocaleController.getString(R.string.PremiumPreviewTranslationsDescription)));
                } else if (startType == PremiumPreviewFragment.PREMIUM_FEATURE_WALLPAPER) {
                    title.setText(LocaleController.getString(R.string.PremiumPreviewWallpaper));
                    description.setText(AndroidUtilities.replaceTags(LocaleController.getString(R.string.PremiumPreviewWallpaperDescription)));
                } else if (startType == PremiumPreviewFragment.PREMIUM_FEATURE_NAME_COLOR) {
                    title.setText(LocaleController.getString(R.string.PremiumPreviewProfileColor));
                    description.setText(AndroidUtilities.replaceTags(LocaleController.getString(R.string.PremiumPreviewProfileColorDescription)));
                } else {
                    title.setText(featureData.title);
                    description.setText(AndroidUtilities.replaceTags(featureData.description));
                }
                topViewOnFullHeight = false;
            } else {
                title.setText(featureData.title);
                description.setText(AndroidUtilities.replaceTags(featureData.description));
                topViewOnFullHeight = false;
            }
            requestLayout();

        }
    }

    View getViewForPosition(Context context, int position) {
        PremiumPreviewFragment.PremiumFeatureData featureData = premiumFeatures.get(position);
        if (featureData.type == PremiumPreviewFragment.PREMIUM_FEATURE_LIMITS) {
            DoubleLimitsPageView doubleLimitsPagerView = new DoubleLimitsPageView(context, resourcesProvider);
            doubleLimitsPagerView.recyclerListView.setOnScrollListener(new RecyclerView.OnScrollListener() {
                @Override
                public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                    super.onScrolled(recyclerView, dx, dy);
                    containerView.invalidate();
                    checkTopOffset();
                }
            });
            return doubleLimitsPagerView;
        }
        if (featureData.type == PremiumPreviewFragment.PREMIUM_FEATURE_STORIES || featureData.type == PremiumPreviewFragment.PREMIUM_FEATURE_BUSINESS) {
            final int type;
            if (featureData.type == PremiumPreviewFragment.PREMIUM_FEATURE_BUSINESS) {
                type = FeaturesPageView.FEATURES_BUSINESS;
            } else {
                type = FeaturesPageView.FEATURES_STORIES;
            }
            FeaturesPageView featuresPageView = new FeaturesPageView(context, type, resourcesProvider);
            featuresPageView.recyclerListView.setOnScrollListener(new RecyclerView.OnScrollListener() {
                @Override
                public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                    super.onScrolled(recyclerView, dx, dy);
                    containerView.invalidate();
                    checkTopOffset();
                }
            });
            return featuresPageView;
        }
        if (featureData.type == PremiumPreviewFragment.PREMIUM_FEATURE_STICKERS) {
            PremiumStickersPreviewRecycler recyclerListView = new PremiumStickersPreviewRecycler(context, currentAccount) {
                @Override
                public void setOffset(float v) {
                    setAutoPlayEnabled(v == 0);
                    super.setOffset(v);
                }
            };
            return recyclerListView;
        } else if (featureData.type == PremiumPreviewFragment.PREMIUM_FEATURE_APPLICATION_ICONS) {
            return new PremiumAppIconsPreviewView(context, resourcesProvider);
        }
        VideoScreenPreview preview = new VideoScreenPreview(context, svgIcon, currentAccount, featureData.type, resourcesProvider);
        return preview;
    }

    @Override
    protected boolean onCustomOpenAnimation() {
        if (viewPager.getChildCount() > 0) {
            ViewPage page = (ViewPage) viewPager.getChildAt(0);
            if (page.topView instanceof PremiumAppIconsPreviewView) {
                PremiumAppIconsPreviewView premiumAppIconsPreviewView = (PremiumAppIconsPreviewView) page.topView;
                ValueAnimator valueAnimator = ValueAnimator.ofFloat(page.getMeasuredWidth(), 0);
                premiumAppIconsPreviewView.setOffset(page.getMeasuredWidth());
                enterAnimationIsRunning = true;
                valueAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                    @Override
                    public void onAnimationUpdate(ValueAnimator animation) {
                        premiumAppIconsPreviewView.setOffset((Float) animation.getAnimatedValue());
                    }
                });
                valueAnimator.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        enterAnimationIsRunning = false;
                        premiumAppIconsPreviewView.setOffset(0);
                        super.onAnimationEnd(animation);
                    }
                });
                valueAnimator.setDuration(500);
                valueAnimator.setStartDelay(100);
                valueAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
                valueAnimator.start();
            }
        }
        return super.onCustomOpenAnimation();
    }

    void checkTopOffset() {
        int selectedViewOffset = -1;
        int toViewOffset = -1;
        for (int i = 0; i < viewPager.getChildCount(); i++) {
            ViewPage viewPage = (ViewPage) viewPager.getChildAt(i);
            if (viewPage.position == selectedPosition && viewPage.topView instanceof BaseListPageView) {
                BaseListPageView doubleLimitsPagerView = (BaseListPageView) viewPage.topView;
                View view = doubleLimitsPagerView.layoutManager.findViewByPosition(0);
                if (view == null) {
                    selectedViewOffset = 0;
                } else {
                    selectedViewOffset = view.getTop();
                    if (selectedViewOffset < 0) {
                        selectedViewOffset = 0;
                    }
                }
            }
            if (viewPage.position == toPosition && viewPage.topView instanceof BaseListPageView) {
                BaseListPageView doubleLimitsPagerView = (BaseListPageView) viewPage.topView;
                View view = doubleLimitsPagerView.layoutManager.findViewByPosition(0);
                if (view == null) {
                    toViewOffset = 0;
                } else {
                    toViewOffset = view.getTop();
                    if (toViewOffset < 0) {
                        toViewOffset = 0;
                    }
                }
            }
        }
        int localOffset = topGlobalOffset;
        if (selectedViewOffset >= 0 ) {
            float progressLocal = 1f - progress;
            localOffset = Math.min(localOffset, (int) (selectedViewOffset * progressLocal + topGlobalOffset * (1f - progressLocal)));
        }
        if (toViewOffset >= 0) {
            float progressLocal = progress;
            localOffset = Math.min(localOffset, (int) (toViewOffset * progressLocal + topGlobalOffset * (1f - progressLocal)));
        }
        closeLayout.setAlpha(1f - progressToGradient);
        if (progressToFullscreenView == 1) {
            closeLayout.setVisibility(View.INVISIBLE);
        } else {
            closeLayout.setVisibility(View.VISIBLE);
        }
        content.setTranslationX(fullscreenNext ? content.getMeasuredWidth() * progressToGradient : -content.getMeasuredWidth() * progressToGradient);
        if (localOffset != topCurrentOffset) {
            topCurrentOffset = localOffset;
            for (int i = 0; i < viewPager.getChildCount(); i++) {
                if (!((ViewPage) viewPager.getChildAt(i)).topViewOnFullHeight) {
                    viewPager.getChildAt(i).setTranslationY(topCurrentOffset);
                }
            }

            content.setTranslationY(topCurrentOffset);
            closeLayout.setTranslationY(topCurrentOffset);
            containerView.invalidate();
            boolean showActionBar = topCurrentOffset < AndroidUtilities.dp(30);
            AndroidUtilities.updateViewVisibilityAnimated(actionBar, showActionBar, 1f, true);
        }
    }

    private void updateStatusBar() {
        if (actionBar != null && actionBar.getTag() != null) {
            AndroidUtilities.setLightStatusBar(getWindow(), isLightStatusBar());
        } else if (baseFragment != null) {
            AndroidUtilities.setLightStatusBar(getWindow(), baseFragment.isLightStatusBar());
        }
    }

    private boolean isLightStatusBar() {
        return ColorUtils.calculateLuminance(getThemedColor(Theme.key_dialogBackground)) > 0.7f;
    }

    @Override
    protected boolean canDismissWithSwipe() {
        for (int i = 0; i < viewPager.getChildCount(); i++) {
            ViewPage viewPage = (ViewPage) viewPager.getChildAt(i);
            if (viewPage.position == selectedPosition && viewPage.topView instanceof BaseListPageView) {
                return !((BaseListPageView) viewPage.topView).recyclerListView.canScrollVertically(-1);
            }
        }
        return true;
    }
}
