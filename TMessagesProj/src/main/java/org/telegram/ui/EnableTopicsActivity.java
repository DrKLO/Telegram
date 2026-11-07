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

import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.FileLog;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeBind;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RLottieDrawable;
import org.telegram.ui.Components.RecyclerListView;
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
    private boolean isSidebar;

    private UniversalRecyclerView listView;

    public EnableTopicsActivity(long dialogId) {
        this.dialogId = dialogId;
    }

    private Utilities.Callback3<Boolean, Boolean, Boolean> onForumChanged;
    public void setOnForumChanged(boolean forum, boolean tabs, boolean sidebar, Utilities.Callback3<Boolean, Boolean, Boolean> listener) {
        this.forum = forum;
        this.isTabs = tabs;
        this.isSidebar = sidebar;
        onForumChanged = listener;
    }

    private void notifyForumChanged() {
        if (onForumChanged != null) {
            onForumChanged.run(forum, isTabs, isSidebar);
        }
    }

    @Override
    public boolean onFragmentCreate() {
        currentChat = getMessagesController().getChat(-dialogId);
        return super.onFragmentCreate();
    }

    @Override
    protected boolean useSimpleActionBar() {
        return true;
    }

    @Override
    public View createView(Context context) {
        setHasOwnBackground(true);
        simpleActionBar.setBackButtonImage(R.drawable.ic_ab_back);
        simpleActionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });
        simpleActionBar.setTitle(getString(R.string.TopicsTitle));

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
        listView.setSections();
        listView.setSectionsDrawBackground(true);
        contentView.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));

        return fragmentView = contentView;
    }

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asTopView(getString(R.string.TopicsInfo), R.raw.topics_top));
        items.add(UItem.asCheck(1, getString(R.string.TopicsEnable)).setChecked(forum));
        if (forum) {
            items.add(UItem.asShadow(null));
            items.add(UItem.asHeader(getString(R.string.TopicsLayout)));
            final UItem switcher = TopicsLayoutSwitcher.Factory.asSwitcher(2, this::onLayoutSelected);
            switcher.intValue = getLayout();
            items.add(switcher);
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
            notifyForumChanged();
            ((TextCheckCell) view).setChecked(forum);
            listView.adapter.update(true);
        }
    }

    private static final int LAYOUT_TABS = 0, LAYOUT_LIST = 1, LAYOUT_SIDEBAR = 2;

    private int getLayout() {
        // the sidebar wins over the tabs: a chat with both shows only the sidebar
        return isSidebar ? LAYOUT_SIDEBAR : isTabs ? LAYOUT_TABS : LAYOUT_LIST;
    }

    // the sidebar is the third layout: with it the forum opens as the list, and the chats have the sidebar instead of tabs
    private void onLayoutSelected(int layout) {
        if (layout == getLayout()) return;
        isTabs = layout == LAYOUT_TABS;
        isSidebar = layout == LAYOUT_SIDEBAR;
        notifyForumChanged();
        topicsLayoutChanged();
    }

    @Override
    public boolean isSupportEdgeToEdge() {
        return true;
    }

    @Nullable
    @Override
    protected RecyclerListView getListViewForSimpleGlass() {
        return listView;
    }

    private static class TopicsLayoutSwitcher extends LinearLayout implements Theme.Colorable {

        private static final int[] ANIMATIONS = { R.raw.topics_tabs, R.raw.topics_list, R.raw.topics_sidebar };
        private static final int[] TITLES = { R.string.TopicsLayoutTabs, R.string.TopicsLayoutList, R.string.TopicsSidebar };
        private static final int MAX_IMAGE_SIZE = 160;
        // the drawing takes this much of the animation's width, the rest is empty
        private static final float ART_WIDTH = .61f;

        private final Theme.ResourcesProvider resourcesProvider;
        private final FrameLayout[] layouts = new FrameLayout[3];
        private final BackupImageView[] images = new BackupImageView[3];
        private final FrameLayout[] titleLayouts = new FrameLayout[3];
        private final FrameLayout[] titleBackgrounds = new FrameLayout[3];
        private final float[] checkedT = new float[3];
        private final int[] lastColors = new int[3];
        private Utilities.Callback<Integer> onSelected;
        private UItem item;
        private int selected = -1;
        private ValueAnimator animator;
        private int imageSize;

        public TopicsLayoutSwitcher(Context context, Theme.ResourcesProvider resourcesProvider) {
            super(context);
            this.resourcesProvider = resourcesProvider;
            setOrientation(HORIZONTAL);
            setPadding(dp(8), 0, dp(8), 0);
            setClipChildren(false);

            for (int i = 0; i < 3; i++) {
                final int index = i;
                final FrameLayout layout = new FrameLayout(context);
                layout.setClipChildren(false);
                ScaleStateListAnimator.apply(layout, .05f, 1.25f);
                layout.setOnClickListener(v -> {
                    if (onSelected != null && index != selected) {
                        // the item keeps the choice for rebinds, the list is not rebuilt: that would cut the animation short
                        if (item != null) item.intValue = index;
                        setSelected(index, true);
                        onSelected.run(index);
                    }
                });
                addView(layout, LayoutHelper.createLinear(0, LayoutHelper.MATCH_PARENT, 1, Gravity.FILL));
                layouts[i] = layout;

                final BackupImageView image = new BackupImageView(context);
                image.setImageDrawable(new RLottieDrawable(ANIMATIONS[i], dp(MAX_IMAGE_SIZE), dp(MAX_IMAGE_SIZE)));
                layout.addView(image, LayoutHelper.createFrame(MAX_IMAGE_SIZE, MAX_IMAGE_SIZE, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, 12, 0, 0));
                images[i] = image;

                final FrameLayout titleLayout = new FrameLayout(context);
                final TextView titleUnselected = TextHelper.makeTextView(context, 14, Theme.key_windowBackgroundWhiteGrayText2, true);
                titleUnselected.setText(getString(TITLES[i]));
                titleUnselected.setSingleLine();
                titleUnselected.setPadding(dp(10), 0, dp(10), 0);
                titleLayout.addView(titleUnselected, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));
                final FrameLayout titleBackground = new FrameLayout(context);
                titleBackground.setPadding(dp(10), 0, dp(10), 0);
                ThemeBind.fillBackground(titleBackground, Theme.createRoundRectDrawable(dp(13), Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider)), ThemeBind.of(Theme.key_featuredStickers_addButton), resourcesProvider);
                titleLayout.addView(titleBackground, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 26, Gravity.CENTER));
                final TextView titleSelected = TextHelper.makeTextView(context, 14, Theme.key_windowBackgroundCheckText, true);
                titleSelected.setText(getString(TITLES[i]));
                titleSelected.setSingleLine();
                titleBackground.addView(titleSelected, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));
                layout.addView(titleLayout, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 26, Gravity.CENTER_HORIZONTAL | Gravity.TOP));
                titleLayouts[i] = titleLayout;
                titleBackgrounds[i] = titleBackground;
            }

            setSelected(0, false);
        }

        // three in a row: the pictures' empty sides go past the columns, only the drawings have to fit
        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            final int width = MeasureSpec.getSize(widthMeasureSpec);
            final int column = (width - getPaddingLeft() - getPaddingRight()) / 3;
            final int size = Math.max(dp(48), Math.min(dp(MAX_IMAGE_SIZE), (int) ((column - dp(8)) / ART_WIDTH)));
            if (size != imageSize) {
                imageSize = size;
                for (int i = 0; i < 3; i++) {
                    final FrameLayout.LayoutParams imageParams = (FrameLayout.LayoutParams) images[i].getLayoutParams();
                    imageParams.width = imageParams.height = size;
                    ((FrameLayout.LayoutParams) titleLayouts[i].getLayoutParams()).topMargin = dp(12 + 10) + size;
                }
            }
            final int height = dp(12 + 10 + 26 + 18) + size;
            super.onMeasure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
        }

        public void setSelected(int index, boolean animated) {
            // the rebind after a pick finds it chosen already, in the middle of its animation
            if (selected == index) return;
            selected = index;
            if (animator != null) {
                animator.cancel();
                animator = null;
            }
            for (int i = 0; i < 3; i++) {
                final float to = i == index ? 1f : 0f;
                if (animated) {
                    titleBackgrounds[i].animate()
                        .scaleX(to).scaleY(to).alpha(to)
                        .setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT)
                        .setDuration(320)
                        .start();
                } else {
                    titleBackgrounds[i].animate().cancel();
                    titleBackgrounds[i].setScaleX(to);
                    titleBackgrounds[i].setScaleY(to);
                    titleBackgrounds[i].setAlpha(to);
                }
            }
            if (animated) {
                final float[] from = checkedT.clone();
                animator = ValueAnimator.ofFloat(0f, 1f);
                animator.addUpdateListener(anm -> {
                    final float t = (float) anm.getAnimatedValue();
                    for (int i = 0; i < 3; i++) {
                        checkedT[i] = AndroidUtilities.lerp(from[i], i == index ? 1f : 0f, t);
                    }
                    applyColors();
                });
                animator.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if (animator == animation) {
                            animator = null;
                        }
                    }
                });
                animator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
                animator.setDuration(320);
                animator.start();
            } else {
                for (int i = 0; i < 3; i++) {
                    checkedT[i] = i == index ? 1f : 0f;
                }
                applyColors();
            }
            final RLottieDrawable lottie = images[index].getImageReceiver().getLottieAnimation();
            if (lottie != null) {
                if (lottie.getProgress() > .80f) {
                    lottie.setProgress(0f, false);
                }
                lottie.restart(true);
            }
        }

        private void applyColors() {
            for (int i = 0; i < 3; i++) {
                final int color = ColorUtils.blendARGB(
                    Theme.getColor(Theme.key_windowBackgroundWhiteGrayText5, resourcesProvider),
                    Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider),
                    checkedT[i]
                );
                // only recreate the filters when the colors changed (runs on every theme frame)
                if (color != lastColors[i]) {
                    lastColors[i] = color;
                    images[i].setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN));
                    images[i].invalidate();
                }
            }
        }

        @Override
        public void updateColors() {
            try {
                if (animator == null || !animator.isRunning()) {
                    applyColors();
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        }

        @Override
        public void addColorKeys(java.util.HashSet<Integer> colors) {
            colors.add(Theme.key_windowBackgroundWhiteGrayText5);
        }

        public static final class Factory extends UItem.UItemFactory<TopicsLayoutSwitcher> {
            static { setup(new Factory()); }

            @Override
            public TopicsLayoutSwitcher createView(Context context, RecyclerListView listView, int currentAccount, int classGuid, Theme.ResourcesProvider resourcesProvider) {
                return new TopicsLayoutSwitcher(context, resourcesProvider);
            }

            @Override
            @SuppressWarnings("unchecked")
            public void bindView(View view, UItem item, boolean divider, UniversalAdapter adapter, UniversalRecyclerView listView) {
                ((TopicsLayoutSwitcher) view).onSelected = (Utilities.Callback<Integer>) item.object;
                ((TopicsLayoutSwitcher) view).item = item;
                ((TopicsLayoutSwitcher) view).setSelected(item.intValue, false);
            }

            public static UItem asSwitcher(int id, Utilities.Callback<Integer> onSelected) {
                UItem item = UItem.ofFactory(Factory.class);
                item.id = id;
                item.object = onSelected;
                return item;
            }
        }
    }
}
