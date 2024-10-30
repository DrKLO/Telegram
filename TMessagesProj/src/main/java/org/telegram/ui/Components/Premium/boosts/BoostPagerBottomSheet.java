package org.telegram.ui.Components.Premium.boosts;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.isTablet;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.MotionEvent;
import android.view.View;

import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_stories;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.Bulletin;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.ViewPagerFixed;
import org.telegram.ui.Stories.DarkThemeResourceProvider;

import java.util.List;

public class BoostPagerBottomSheet extends BottomSheet {

    private static BoostPagerBottomSheet instance;
    private final ViewPagerFixed viewPager;
    private final SelectorBottomSheet rightSheet;

    public static void show(BaseFragment fragment, long dialogId, Theme.ResourcesProvider resourcesProvider) {
        show(fragment, resourcesProvider, dialogId, null);
    }

    public static void show(BaseFragment fragment, Theme.ResourcesProvider resourcesProvider, long dialogId, TL_stories.PrepaidGiveaway prepaidGiveaway) {
        if (instance != null) {
            return;
        }
        boolean forceDark = resourcesProvider instanceof DarkThemeResourceProvider;
        BaseFragment fragmentWrapper = forceDark ? new DarkFragmentWrapper(fragment) : fragment;
        resourcesProvider = fragmentWrapper.getResourceProvider();
        BoostPagerBottomSheet alert = new BoostPagerBottomSheet(fragment.getParentActivity(), true,
                new BoostViaGiftsBottomSheet(fragmentWrapper, false, false, dialogId, prepaidGiveaway),
                new SelectorBottomSheet(fragmentWrapper, false, dialogId),
                resourcesProvider, forceDark);
        alert.show();
        instance = alert;
    }

    public static BoostPagerBottomSheet getInstance() {
        return instance;
    }

    private boolean isLandscapeOrientation;

    public BoostPagerBottomSheet(Context context, boolean needFocus, BoostViaGiftsBottomSheet leftSheet, SelectorBottomSheet rightSheet, Theme.ResourcesProvider resourcesProvider, boolean forceDark) {
        super(context, needFocus, resourcesProvider);
        this.rightSheet = rightSheet;
        setApplyBottomPadding(false);
        setApplyTopPadding(false);
        useBackgroundTopPadding = false;
        setBackgroundColor(Color.TRANSPARENT);
        fixNavigationBar();
        AndroidUtilities.setLightStatusBar(getWindow(), isLightStatusBar());
        checkScreenOrientation();

        viewPager = new ViewPagerFixed(getContext()) {

            private final Path path = new Path();
            private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            private boolean isScrolling;
            private boolean isKeyboardVisible;
            private final boolean isTablet = isTablet();

            @Override
            protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
                super.onLayout(changed, left, top, right, bottom);
                if (isKeyboardVisible != isKeyboardVisible()) {
                    isKeyboardVisible = isKeyboardVisible();
                    if (isKeyboardVisible) {
                        rightSheet.scrollToTop(true);
                    }
                }
            }

            @Override
            protected void onTabAnimationUpdate(boolean manual) {
                float percent = viewPager.getPositionAnimated();
                if (percent > 0f && percent < 1f) {
                    if (!isScrolling) {
                        isScrolling = true;
                        hideKeyboardIfVisible();
                    }
                } else {
                    isScrolling = false;
                }
                viewPager.invalidate();
            }

            @Override
            protected void onScrollEnd() {
                isScrolling = false;
                viewPager.invalidate();
            }

            @Override
            protected void dispatchDraw(Canvas canvas) {
                backgroundPaint.setColor(Theme.getColor(Theme.key_dialogBackground, resourcesProvider));
                if (isScrolling) {
                    int top1 = leftSheet.getTop() + AndroidUtilities.dp(10);
                    int top2 = rightSheet.getTop();
                    int diffTop = Math.abs(top1 - top2);
                    int currentTop;
                    if (viewPager.getCurrentPosition() == 0) {
                        float diffFloat = diffTop * viewPager.getPositionAnimated();
                        currentTop = top1 < top2 ? (int) (top1 + diffFloat) : (int) (top1 - diffFloat);
                    } else {
                        float diffFloat = diffTop * (1 - viewPager.getPositionAnimated());
                        currentTop = top2 < top1 ? (int) (top2 + diffFloat) : (int) (top2 - diffFloat);
                    }
                    final float r = dp(14);
                    AndroidUtilities.rectTmp.set(0, currentTop, getWidth(), getHeight() + dp(8));
                    canvas.drawRoundRect(AndroidUtilities.rectTmp, r, r, backgroundPaint);
                    canvas.save();
                    path.rewind();
                    path.addRoundRect(AndroidUtilities.rectTmp, r, r, Path.Direction.CW);
                    canvas.clipPath(path);
                    super.dispatchDraw(canvas);
                    canvas.restore();
                } else {
                    if (isTablet || isLandscapeOrientation) {
                        canvas.clipRect(0, 0, getMeasuredWidth(), getMeasuredHeight());
                    }
                    super.dispatchDraw(canvas);
                }
            }

            @Override
            protected float getAvailableTranslationX() {
                if (isTablet || isLandscapeOrientation) {
                    return getMeasuredWidth();
                }
                return super.getAvailableTranslationX();
            }

            @Override
            protected boolean canScroll(MotionEvent e) {
                return viewPager.getCurrentPosition() == 1;
            }
        };
        viewPager.setOverScrollMode(View.OVER_SCROLL_NEVER);
        viewPager.setClipToPadding(false);

        ViewPagerFixed.Adapter adapter = new ViewPagerFixed.Adapter() {
            @Override
            public int getItemCount() {
                return 2;
            }

            @Override
            public View createView(int viewType) {
                if (viewType == 0) {
                    return leftSheet.getContainerView();
                } else {
                    return rightSheet.getContainerView();
                }
            }

            @Override
            public int getItemViewType(int position) {
                return position;
            }

            @Override
            public void bindView(View view, int position, int viewType) {

            }
        };
        viewPager.setAdapter(adapter);
        viewPager.setPosition(0);
        setCustomView(viewPager);
        leftSheet.setOnCloseClick(this::dismiss);
        leftSheet.setActionListener(new BoostViaGiftsBottomSheet.ActionListener() {
            @Override
            public void onAddChat(List<TLObject> chats) {
                rightSheet.prepare(chats, SelectorBottomSheet.TYPE_CHANNEL);
                viewPager.scrollToPosition(1);
            }

            @Override
            public void onSelectUser(List<TLObject> users) {
                rightSheet.prepare(users, SelectorBottomSheet.TYPE_USER);
                viewPager.scrollToPosition(1);
            }

            @Override
            public void onSelectCountries(List<TLObject> countries) {
                rightSheet.prepare(countries, SelectorBottomSheet.TYPE_COUNTRY);
                viewPager.scrollToPosition(1);
            }
        });
        rightSheet.setSelectedObjectsListener(new SelectorBottomSheet.SelectedObjectsListener() {
            @Override
            public void onChatsSelected(List<TLRPC.Chat> chats, boolean animated) {
                viewPager.scrollToPosition(0);
                leftSheet.onChatsSelected(chats, !isKeyboardVisible());
            }

            @Override
            public void onUsersSelected(List<TLRPC.User> users) {
                viewPager.scrollToPosition(0);
                leftSheet.onUsersSelected(users);
            }

            @Override
            public void onCountrySelected(List<TLRPC.TL_help_country> countries) {
                viewPager.scrollToPosition(0);
                leftSheet.onCountrySelected(countries);
            }

            @Override
            public void onShowToast(String text) {
                BulletinFactory.of(container, resourcesProvider).createSimpleBulletin(R.raw.chats_infotip, text).show(true);
            }
        });
        rightSheet.setOnCloseClick(this::onBackPressed);
        loadData(forceDark);
        Bulletin.addDelegate(container, new Bulletin.Delegate() {
            @Override
            public int getTopOffset(int tag) {
                return AndroidUtilities.statusBarHeight;
            }
        });
    }

    private void checkScreenOrientation() {
        isLandscapeOrientation = getContext().getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
    }

    @Override
    public void dismissInternal() {
        super.dismissInternal();
        instance = null;
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        rightSheet.onConfigurationChanged(newConfig);
        checkScreenOrientation();
        super.onConfigurationChanged(newConfig);
    }

    private void loadData(boolean forceDark) {
        if (!forceDark) {
            MessagesController.getInstance(currentAccount).getStoriesController().loadSendAs();
        }
    }

    private void hideKeyboardIfVisible() {
        if (isKeyboardVisible()) {
            AndroidUtilities.hideKeyboard(rightSheet.getContainerView());
        }
    }

    @Override
    public void onBackPressed() {
        if (viewPager.getCurrentPosition() > 0) {
            if (rightSheet.hasChanges()) {
                return;
            }
            hideKeyboardIfVisible();
            viewPager.scrollToPosition(0);
            return;
        }
        super.onBackPressed();
    }

    private boolean isLightStatusBar() {
        return ColorUtils.calculateLuminance(Theme.getColor(Theme.key_dialogBackground, resourcesProvider)) > 0.7f;
    }

    @Override
    protected boolean canDismissWithSwipe() {
        return false;
    }
}
