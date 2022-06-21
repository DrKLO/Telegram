package org.telegram.ui.Components.Premium;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SvgHelper;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.BottomPagesView;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RLottieDrawable;
import org.telegram.ui.PremiumPreviewFragment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

public class PremiumFeatureBottomSheet extends BottomSheet implements NotificationCenter.NotificationCenterDelegate {

    BaseFragment fragment;

    private PremiumButtonView premiumButtonView;
    ArrayList<PremiumPreviewFragment.PremiumFeatureData> premiumFeatures = new ArrayList<>();

    float containerViewsProgress;
    boolean containerViewsForward;
    ViewPager viewPager;
    FrameLayout content;
    int contentHeight;

    private FrameLayout buttonContainer;
    boolean enterAnimationIsRunning;
    SvgHelper.SvgDrawable svgIcon;
    private final int startType;
    private final boolean onlySelectedType;

    public PremiumFeatureBottomSheet(BaseFragment fragment, int startType, boolean onlySelectedType) {
        super(fragment.getParentActivity(), false);
        this.fragment = fragment;
        this.startType = startType;
        this.onlySelectedType = onlySelectedType;

        String svg = RLottieDrawable.readRes(null, R.raw.star_loader);
        svgIcon = SvgHelper.getDrawable(svg);
        Context context = fragment.getParentActivity();
        FrameLayout frameLayout = new FrameLayout(context) {
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


        PremiumPreviewFragment.fillPremiumFeaturesList(premiumFeatures, fragment.getCurrentAccount());

        int selectedPosition = 0;
        for (int i = 0; i < premiumFeatures.size(); i++) {
            if (premiumFeatures.get(i).type == PremiumPreviewFragment.PREMIUM_FEATURE_LIMITS) {
                premiumFeatures.remove(i);
                i--;
                continue;
            }
            if (premiumFeatures.get(i).type == startType) {
                selectedPosition = i;
                break;
            }
        }

        if (onlySelectedType) {
            PremiumPreviewFragment.PremiumFeatureData selectedFeature = premiumFeatures.get(selectedPosition);
            premiumFeatures.clear();
            premiumFeatures.add(selectedFeature);
            selectedPosition = 0;
        }

        PremiumPreviewFragment.PremiumFeatureData featureData = premiumFeatures.get(selectedPosition);

        setApplyBottomPadding(false);
        useBackgroundTopPadding = false;
        PremiumGradient.GradientTools gradientTools = new PremiumGradient.GradientTools(Theme.key_premiumGradientBottomSheet1, Theme.key_premiumGradientBottomSheet2, Theme.key_premiumGradientBottomSheet3, null);
        gradientTools.x1 = 0;
        gradientTools.y1 = 1.1f;
        gradientTools.x2 = 1.5f;
        gradientTools.y2 = -0.2f;
        gradientTools.exactly = true;
        content = new FrameLayout(context) {

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
                canvas.drawRoundRect(AndroidUtilities.rectTmp, AndroidUtilities.dp(12) - 1, AndroidUtilities.dp(12) - 1, gradientTools.paint);
                canvas.restore();
                super.dispatchDraw(canvas);
            }
        };

        FrameLayout closeLayout = new FrameLayout(context);
        ImageView closeImage = new ImageView(context);
        closeImage.setImageResource(R.drawable.msg_close);
        closeImage.setBackground(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(12), ColorUtils.setAlphaComponent(Color.WHITE, 40), ColorUtils.setAlphaComponent(Color.WHITE, 100)));
        closeLayout.addView(closeImage, LayoutHelper.createFrame(24, 24, Gravity.CENTER));
        closeLayout.setOnClickListener(v -> dismiss());
        frameLayout.addView(content, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 16, 0, 0));

        viewPager = new ViewPager(context) {

            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                int h = AndroidUtilities.dp(100);
                if (getChildCount() > 0) {
                    getChildAt(0).measure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
                    h = getChildAt(0).getMeasuredHeight();
                }
                super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY));
            }

            @Override
            public boolean onInterceptTouchEvent(MotionEvent ev) {
                try {
                    return super.onInterceptTouchEvent(ev);
                } catch (Exception e) {
                    return false;
                }
            }

            @Override
            public boolean onTouchEvent(MotionEvent ev) {
                if (enterAnimationIsRunning) {
                    return false;
                }
                return super.onTouchEvent(ev);
            }
        };
        viewPager.setOffscreenPageLimit(0);
        PagerAdapter pagerAdapter = new PagerAdapter() {
            @Override
            public int getCount() {
                return premiumFeatures.size();
            }

            @NonNull
            @Override
            public Object instantiateItem(@NonNull ViewGroup container, int position) {
                ViewPage viewPage = new ViewPage(context, position);
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
        viewPager.setCurrentItem(selectedPosition);
        frameLayout.addView(viewPager, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 100, 0, 0, 18, 0, 0));

        frameLayout.addView(closeLayout, LayoutHelper.createFrame(52, 52, Gravity.RIGHT | Gravity.TOP, 0, 16, 0, 0));
        BottomPagesView bottomPages = new BottomPagesView(context, viewPager, premiumFeatures.size());
        viewPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {

            int selectedPosition;
            int toPosition;
            float progress;

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
            }

            @Override
            public void onPageScrollStateChanged(int i) {

            }
        });

        LinearLayout linearLayout = new LinearLayout(context);
        linearLayout.addView(frameLayout);
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        bottomPages.setColor(Theme.key_chats_unreadCounterMuted, Theme.key_chats_actionBackground);
        if (!onlySelectedType) {
            linearLayout.addView(bottomPages, LayoutHelper.createLinear(11 * premiumFeatures.size(), 5, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 10));
        }
        premiumButtonView = new PremiumButtonView(context, true);
        premiumButtonView.buttonLayout.setOnClickListener(v -> {
            if (fragment.getVisibleDialog() != null) {
                fragment.getVisibleDialog().dismiss();
            }
            if (fragment instanceof ChatActivity) {
                ((ChatActivity) fragment).closeMenu();
            }
            if (onlySelectedType) {
                fragment.presentFragment(new PremiumPreviewFragment(PremiumPreviewFragment.featureTypeToServerString(featureData.type)));
            } else {
                PremiumPreviewFragment.buyPremium(fragment, PremiumPreviewFragment.featureTypeToServerString(featureData.type));
            }
            dismiss();
        });
        premiumButtonView.overlayTextView.setOnClickListener(v -> {
            dismiss();
        });
        buttonContainer = new FrameLayout(context);

        buttonContainer.addView(premiumButtonView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.CENTER_VERTICAL, 16, 0, 16, 0));
        buttonContainer.setBackgroundColor(getThemedColor(Theme.key_dialogBackground));
        linearLayout.addView(buttonContainer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 68, Gravity.BOTTOM));

        if (UserConfig.getInstance(currentAccount).isPremium()) {
            premiumButtonView.setOverlayText(LocaleController.getString("OK", R.string.OK), false, false);
        }

        ScrollView scrollView = new ScrollView(context);
        scrollView.addView(linearLayout);
        setCustomView(scrollView);

        MediaDataController.getInstance(currentAccount).preloadPremiumPreviewStickers();
        setButtonText();
    }

    private void setButtonText() {
        if (onlySelectedType) {
            if (startType == PremiumPreviewFragment.PREMIUM_FEATURE_REACTIONS) {
                premiumButtonView.buttonTextView.setText(LocaleController.getString(R.string.UnlockPremiumReactions));
                premiumButtonView.setIcon(R.raw.unlock_icon);
            } else if (startType == PremiumPreviewFragment.PREMIUM_FEATURE_ADS) {
                premiumButtonView.buttonTextView.setText(LocaleController.getString(R.string.AboutTelegramPremium));
            } else if (startType == PremiumPreviewFragment.PREMIUM_FEATURE_APPLICATION_ICONS) {
                premiumButtonView.buttonTextView.setText(LocaleController.getString(R.string.UnlockPremiumIcons));
                premiumButtonView.setIcon(R.raw.unlock_icon);
            }
        } else {
            premiumButtonView.buttonTextView.setText(PremiumPreviewFragment.getPremiumButtonText(currentAccount));
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.billingProductDetailsUpdated);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.premiumPromoUpdated);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.currentUserPremiumStatusChanged);
    }

    @Override
    public void dismiss() {
        super.dismiss();

        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.billingProductDetailsUpdated);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.premiumPromoUpdated);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.currentUserPremiumStatusChanged);
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

        public ViewPage(Context context, int p) {
            super(context);
            setOrientation(VERTICAL);
            topView = getViewForPosition(context, p);
            addView(topView);
            topHeader = (PagerHeaderView) topView;

            title = new TextView(context);
            title.setGravity(Gravity.CENTER_HORIZONTAL);
            title.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
            title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
            title.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));

            addView(title, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 21, 20, 21, 0));

            description = new TextView(context);
            description.setGravity(Gravity.CENTER_HORIZONTAL);
            description.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            description.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
            if (!onlySelectedType) {
                description.setLines(2);
            }
            addView(description, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 21, 10, 21, 16));
            setClipChildren(false);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
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
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        }

        @Override
        protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
            if (child == topView) {
                if (child instanceof CarouselView) {
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

            if (onlySelectedType) {
                if (startType == PremiumPreviewFragment.PREMIUM_FEATURE_REACTIONS) {
                    title.setText(LocaleController.getString("AdditionalReactions", R.string.AdditionalReactions));
                    description.setText(LocaleController.getString("AdditionalReactionsDescription", R.string.AdditionalReactionsDescription));
                } else if (startType == PremiumPreviewFragment.PREMIUM_FEATURE_ADS) {
                    title.setText(LocaleController.getString("PremiumPreviewNoAds", R.string.PremiumPreviewNoAds));
                    description.setText(LocaleController.getString("PremiumPreviewNoAdsDescription2", R.string.PremiumPreviewNoAdsDescription2));
                }  else if (startType == PremiumPreviewFragment.PREMIUM_FEATURE_APPLICATION_ICONS) {
                    title.setText(LocaleController.getString("PremiumPreviewAppIcon", R.string.PremiumPreviewAppIcon));
                    description.setText(LocaleController.getString("PremiumPreviewAppIconDescription2", R.string.PremiumPreviewAppIconDescription2));
                }
            } else {
                title.setText(featureData.title);
                description.setText(featureData.description);
            }
        }
    }

    View getViewForPosition(Context context, int position) {
        PremiumPreviewFragment.PremiumFeatureData featureData = premiumFeatures.get(position);
        if (featureData.type == PremiumPreviewFragment.PREMIUM_FEATURE_REACTIONS) {
            ArrayList<ReactionDrawingObject> drawingObjects = new ArrayList<>();
            List<TLRPC.TL_availableReaction> list = MediaDataController.getInstance(currentAccount).getEnabledReactionsList();
            List<TLRPC.TL_availableReaction> premiumLockedReactions = new ArrayList<>();
            for (int i = 0; i < list.size(); i++) {
                if (list.get(i).premium) {
                    premiumLockedReactions.add(list.get(i));
                }
            }
            for (int i = 0; i < premiumLockedReactions.size(); i++) {
                ReactionDrawingObject drawingObject = new ReactionDrawingObject(i);
                drawingObject.set(premiumLockedReactions.get(i));
                drawingObjects.add(drawingObject);
            }

            HashMap<String, Integer> sortRulesMap = new HashMap<>();
            sortRulesMap.put("\uD83D\uDC4C", 1);
            sortRulesMap.put("\uD83D\uDE0D", 2);
            sortRulesMap.put("\uD83E\uDD21", 3);
            sortRulesMap.put("\uD83D\uDD4A", 4);
            sortRulesMap.put("\uD83E\uDD71", 5);
            sortRulesMap.put("\uD83E\uDD74", 6);
            sortRulesMap.put("\uD83D\uDC33", 7);
            Collections.sort(drawingObjects, (o1, o2) -> {
                int i1 = sortRulesMap.containsKey(o1.reaction.reaction) ? sortRulesMap.get(o1.reaction.reaction) : Integer.MAX_VALUE;
                int i2 = sortRulesMap.containsKey(o2.reaction.reaction) ? sortRulesMap.get(o2.reaction.reaction) : Integer.MAX_VALUE;
                return i2 - i1;
            });

            CarouselView carouselView = new CarouselView(context, drawingObjects);
            return carouselView;
        } else if (featureData.type == PremiumPreviewFragment.PREMIUM_FEATURE_STICKERS) {
            PremiumStickersPreviewRecycler recyclerListView = new PremiumStickersPreviewRecycler(context, currentAccount) {
                @Override
                public void setOffset(float v) {
                    setAutoPlayEnabled(v == 0);
                    super.setOffset(v);
                }
            };
            return recyclerListView;
        } else if (featureData.type == PremiumPreviewFragment.PREMIUM_FEATURE_APPLICATION_ICONS) {
            return new PremiumAppIconsPreviewView(context);
        }
        VideoScreenPreview preview = new VideoScreenPreview(context, svgIcon, currentAccount, featureData.type);
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
}
