package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RLottieDrawable;
import org.telegram.ui.Components.ScaleStateListAnimator;
import org.telegram.ui.Components.TextHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;

import java.util.ArrayList;

public class EnableTopicsActivity extends BaseFragment {

    private long dialogId;
    private TLRPC.Chat currentChat;
    private boolean forum;
    private boolean isTabs;

    private UniversalRecyclerView listView;

    public EnableTopicsActivity(long dialogId) {
        this.dialogId = dialogId;
    }

    private Utilities.Callback2<Boolean, Boolean> onForumChanged;
    public void setOnForumChanged(boolean forum, boolean tabs, Utilities.Callback2<Boolean, Boolean> listener) {
        this.forum = forum;
        this.isTabs = tabs;
        onForumChanged = listener;
    }

    @Override
    public boolean onFragmentCreate() {
        currentChat = getMessagesController().getChat(-dialogId);
        return super.onFragmentCreate();
    }

    @Override
    public View createView(Context context) {

        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });
        actionBar.setTitle(getString(R.string.TopicsTitle));

        FrameLayout contentView = new FrameLayout(context);

        listView = new UniversalRecyclerView(this, this::fillItems, this::onItemClick, null) {
            @Override
            public Integer getSelectorColor(int position) {
                UItem item = adapter.getItem(position);
                if (item != null && item.id == 2)
                    return 0;
                return super.getSelectorColor(position);
            }
        };
        listView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray, resourceProvider));
        contentView.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));

        return fragmentView = contentView;
    }

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asTopView(getString(R.string.TopicsInfo), R.raw.topics_top));
        items.add(UItem.asCheck(1, getString(R.string.TopicsEnable)).setChecked(forum));
        if (forum) {
            items.add(UItem.asShadow(null));
            items.add(UItem.asHeader(getString(R.string.TopicsLayout)));
            items.add(TopicsLayoutSwitcher.Factory.asSwitcher(2, v -> {
                ((TopicsLayoutSwitcher) v.getParent()).setChecked(isTabs = true, true);
                if (onForumChanged != null) {
                    onForumChanged.run(forum, isTabs);
                }
                topicsLayoutChanged();
            }, v -> {
                ((TopicsLayoutSwitcher) v.getParent()).setChecked(isTabs = false, true);
                if (onForumChanged != null) {
                    onForumChanged.run(forum, isTabs);
                }
                topicsLayoutChanged();
            }).setChecked(isTabs));
            items.add(UItem.asShadow(getString(R.string.TopicsLayoutInfo)));
        }
    }

    private void topicsLayoutChanged() {
        if (isTabs && getParentLayout() != null) {
            for (BaseFragment fragment : getParentLayout().getFragmentStack()) {
                if (fragment instanceof DialogsActivity) {
                    final RightSlidingDialogContainer rightSlidingDialogContainer = ((DialogsActivity) fragment).rightSlidingDialogContainer;
                    if (rightSlidingDialogContainer.hasFragment()) {
                        rightSlidingDialogContainer.finishPreview();
                    }
                }
            }
        }
    }

    private void onItemClick(UItem item, View view, int position, float x, float y) {
        if (item.id == 1) {
            if (currentChat == null) {
                return;
            }

            forum = !forum;
            if (onForumChanged != null) {
                onForumChanged.run(forum, isTabs);
            }
            ((TextCheckCell) view).setChecked(forum);
            listView.adapter.update(true);
        }
    }

    private static class TopicsLayoutSwitcher extends LinearLayout {

        private final Theme.ResourcesProvider resourcesProvider;

        private final FrameLayout leftLayout;
        private final BackupImageView leftImageView;
        private final FrameLayout leftTitleLayout;
        private final TextView leftTitleUnselected;
        private final FrameLayout leftTitleBackground;
        private final TextView leftTitleSelected;

        private final FrameLayout rightLayout;
        private final BackupImageView rightImageView;
        private final FrameLayout rightTitleLayout;
        private final TextView rightTitleUnselected;
        private final FrameLayout rightTitleBackground;
        private final TextView rightTitleSelected;

        public TopicsLayoutSwitcher(Context context, Theme.ResourcesProvider resourcesProvider) {
            super(context);
            this.resourcesProvider = resourcesProvider;
            setOrientation(HORIZONTAL);
            setPadding(dp(8), 0, dp(8), 0);

            leftLayout = new FrameLayout(context);
            ScaleStateListAnimator.apply(leftLayout, .05f, 1.25f);
            addView(leftLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 226, 1, Gravity.FILL));
            leftImageView = new BackupImageView(context);
            leftImageView.setImageDrawable(new RLottieDrawable(R.raw.topics_tabs, "topics_tabs", dp(160), dp(160)));
            leftLayout.addView(leftImageView, LayoutHelper.createFrame(160, 160, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, 12.33f, 0, 0));

            leftTitleLayout = new FrameLayout(context);
            leftTitleUnselected = TextHelper.makeTextView(context, 14, Theme.key_windowBackgroundWhiteGrayText2, true);
            leftTitleUnselected.setText(getString(R.string.TopicsLayoutTabs));
            leftTitleUnselected.setPadding(dp(12), 0, dp(12), 0);
            leftTitleLayout.addView(leftTitleUnselected, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));
            leftTitleBackground = new FrameLayout(context);
            leftTitleBackground.setPadding(dp(12), 0, dp(12), 0);
            leftTitleBackground.setBackground(Theme.createRoundRectDrawable(dp(13), Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider)));
            leftTitleLayout.addView(leftTitleBackground, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 26, Gravity.CENTER));
            leftTitleSelected = TextHelper.makeTextView(context, 14, Theme.key_windowBackgroundCheckText, true);
            leftTitleSelected.setText(getString(R.string.TopicsLayoutTabs));
            leftTitleBackground.addView(leftTitleSelected, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));
            leftLayout.addView(leftTitleLayout, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 26, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, 182, 0, 0));

            rightLayout = new FrameLayout(context);
            ScaleStateListAnimator.apply(rightLayout, .05f, 1.25f);
            addView(rightLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 226, 1, Gravity.FILL));
            rightImageView = new BackupImageView(context);
            rightImageView.setImageDrawable(new RLottieDrawable(R.raw.topics_list, "topics_list", dp(160), dp(160)));
            rightLayout.addView(rightImageView, LayoutHelper.createFrame(160, 160, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, 12.33f, 0, 0));

            rightTitleLayout = new FrameLayout(context);
            rightTitleUnselected = TextHelper.makeTextView(context, 14, Theme.key_windowBackgroundWhiteGrayText2, true);
            rightTitleUnselected.setText(getString(R.string.TopicsLayoutList));
            rightTitleUnselected.setPadding(dp(12), 0, dp(12), 0);
            rightTitleLayout.addView(rightTitleUnselected, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));
            rightTitleBackground = new FrameLayout(context);
            rightTitleBackground.setPadding(dp(12), 0, dp(12), 0);
            rightTitleBackground.setBackground(Theme.createRoundRectDrawable(dp(13), Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider)));
            rightTitleLayout.addView(rightTitleBackground, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 26, Gravity.CENTER));
            rightTitleSelected = TextHelper.makeTextView(context, 14, Theme.key_windowBackgroundCheckText, true);
            rightTitleSelected.setText(getString(R.string.TopicsLayoutList));
            rightTitleBackground.addView(rightTitleSelected, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));
            rightLayout.addView(rightTitleLayout, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 26, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, 182, 0, 0));

            setChecked(false, false);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), heightMeasureSpec);
        }

        private ValueAnimator animator;
        private float tabsAlpha;
        public void setChecked(boolean checked, boolean animated) {
            if (animator != null) {
                animator.cancel();
                animator = null;
            }
            if (animated) {
                leftTitleBackground.animate()
                    .scaleX(!checked ? 0f : 1f)
                    .scaleY(!checked ? 0f : 1f)
                    .alpha(!checked ? 0f : 1f)
                    .setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT)
                    .setDuration(320)
                    .start();
                rightTitleBackground.animate()
                    .scaleX(checked ? 0f : 1f)
                    .scaleY(checked ? 0f : 1f)
                    .alpha(checked ? 0f : 1f)
                    .setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT)
                    .setDuration(320)
                    .start();
                animator = ValueAnimator.ofFloat(tabsAlpha, checked ? 1f : 0f);
                animator.addUpdateListener(anm -> {
                    tabsAlpha = (float) anm.getAnimatedValue();
                    leftImageView.setColorFilter(new PorterDuffColorFilter(ColorUtils.blendARGB(
                        Theme.getColor(Theme.key_windowBackgroundWhiteGrayText5, resourcesProvider),
                        Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider),
                        tabsAlpha
                    ), PorterDuff.Mode.SRC_IN));
                    leftImageView.invalidate();
                    rightImageView.setColorFilter(new PorterDuffColorFilter(ColorUtils.blendARGB(
                        Theme.getColor(Theme.key_windowBackgroundWhiteGrayText5, resourcesProvider),
                        Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider),
                        1f - tabsAlpha
                    ), PorterDuff.Mode.SRC_IN));
                    rightImageView.invalidate();
                });
                animator.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        tabsAlpha = checked ? 1.0f : 0.0f;
                        leftImageView.setColorFilter(new PorterDuffColorFilter(ColorUtils.blendARGB(
                            Theme.getColor(Theme.key_windowBackgroundWhiteGrayText5, resourcesProvider),
                            Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider),
                            tabsAlpha
                        ), PorterDuff.Mode.SRC_IN));
                        leftImageView.invalidate();
                        rightImageView.setColorFilter(new PorterDuffColorFilter(ColorUtils.blendARGB(
                            Theme.getColor(Theme.key_windowBackgroundWhiteGrayText5, resourcesProvider),
                            Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider),
                            1f - tabsAlpha
                        ), PorterDuff.Mode.SRC_IN));
                        rightImageView.invalidate();
                    }
                });
                animator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
                animator.setDuration(320);
                animator.start();
            } else {
                leftTitleBackground.animate().cancel();
                rightTitleBackground.animate().cancel();
                leftTitleBackground.setScaleX(!checked ? 0f : 1f);
                leftTitleBackground.setScaleY(!checked ? 0f : 1f);
                leftTitleBackground.setAlpha(!checked ? 0f : 1f);
                rightTitleBackground.setScaleX(checked ? 0f : 1f);
                rightTitleBackground.setScaleY(checked ? 0f : 1f);
                rightTitleBackground.setAlpha(checked ? 0f : 1f);

                tabsAlpha = checked ? 1.0f : 0.0f;
                leftImageView.setColorFilter(new PorterDuffColorFilter(ColorUtils.blendARGB(
                    Theme.getColor(Theme.key_windowBackgroundWhiteGrayText5, resourcesProvider),
                    Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider),
                    tabsAlpha
                ), PorterDuff.Mode.SRC_IN));
                leftImageView.invalidate();
                rightImageView.setColorFilter(new PorterDuffColorFilter(ColorUtils.blendARGB(
                    Theme.getColor(Theme.key_windowBackgroundWhiteGrayText5, resourcesProvider),
                    Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider),
                    1f - tabsAlpha
                ), PorterDuff.Mode.SRC_IN));
                rightImageView.invalidate();
            }
            final ImageReceiver imageReceiver = (checked ? leftImageView : rightImageView).getImageReceiver();
            final RLottieDrawable lottie = imageReceiver.getLottieAnimation();
            if (lottie != null) {
                if (lottie.getProgress() > (checked ? .85f : .80f)) {
                    lottie.setProgress(0f, false);
                }
                lottie.restart(true);
            }
        }

        public static final class Factory extends UItem.UItemFactory<TopicsLayoutSwitcher> {
            static { setup(new Factory()); }

            @Override
            public TopicsLayoutSwitcher createView(Context context, int currentAccount, int classGuid, Theme.ResourcesProvider resourcesProvider) {
                return new TopicsLayoutSwitcher(context, resourcesProvider);
            }

            @Override
            public void bindView(View view, UItem item, boolean divider, UniversalAdapter adapter, UniversalRecyclerView listView) {
                ((TopicsLayoutSwitcher) view).leftLayout.setOnClickListener((View.OnClickListener) item.object);
                ((TopicsLayoutSwitcher) view).rightLayout.setOnClickListener((View.OnClickListener) item.object2);
                ((TopicsLayoutSwitcher) view).setChecked(item.checked, false);
            }

            public static UItem asSwitcher(int id, View.OnClickListener leftClick, View.OnClickListener rightClick) {
                UItem item = UItem.ofFactory(Factory.class);
                item.id = id;
                item.object = leftClick;
                item.object2 = rightClick;
                return item;
            }
        }
    }
}
