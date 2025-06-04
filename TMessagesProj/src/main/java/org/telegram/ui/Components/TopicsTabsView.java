package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.lerp;
import static org.telegram.messenger.LocaleController.formatString;
import static org.telegram.messenger.LocaleController.getPluralString;
import static org.telegram.messenger.LocaleController.getString;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.ChatListItemAnimator;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.NotificationsController;
import org.telegram.messenger.R;
import org.telegram.messenger.SavedMessagesController;
import org.telegram.messenger.TopicsController;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.support.LongSparseLongArray;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.AvatarSpan;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.Forum.ForumUtilities;
import org.telegram.ui.GradientClip;

import java.util.ArrayList;
import java.util.HashSet;

public class TopicsTabsView extends FrameLayout implements NotificationCenter.NotificationCenterDelegate {

    private final int currentAccount;
    private final long dialogId;
    private final Theme.ResourcesProvider resourcesProvider;
    private final boolean mono;
    private final BaseFragment fragment;
    private final boolean canShowProgress;

    private int lastTabId = 0;
    private final LongSparseLongArray tabToDialog = new LongSparseLongArray();

    private final BlurredFrameLayout topTabsContainer;
    private final View topTabsShadowView;
    private final UniversalRecyclerView topTabs;
    private final ImageView button;
    private final ImageView closeButton;
    private final FrameLayout sideTabsContainer;
    private final View sideTabsShadowView;
    private final UniversalRecyclerView sideTabs;

    private long lastSelectedTopicId;
    private long animateFromSelectedTopicId;

    public TopicsTabsView(Context context, BaseFragment fragment, SizeNotifierFrameLayout sizeNotifierFrameLayout, int currentAccount, long dialogId, Theme.ResourcesProvider resourcesProvider) {
        super(context);

        this.fragment = fragment;
        this.currentAccount = currentAccount;
        this.dialogId = dialogId;
        this.resourcesProvider = resourcesProvider;

        mono = ChatObject.isMonoForum(MessagesController.getInstance(currentAccount).getChat(-dialogId));
        canShowProgress = !UserConfig.getInstance(currentAccount).getPreferences().getBoolean("topics_end_reached_" + -dialogId, false);

        setClipChildren(true);
        setClipToPadding(true);
        setWillNotDraw(false);

        topTabsShadowView = new View(context);
        topTabsShadowView.setBackgroundResource(R.drawable.header_shadow);
        addView(topTabsShadowView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 3, Gravity.TOP | Gravity.FILL_HORIZONTAL, 0, 48, 0, 0));
        topTabsContainer = new BlurredFrameLayout(context, sizeNotifierFrameLayout);
        topTabsContainer.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider));
        addView(topTabsContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.TOP | Gravity.FILL_HORIZONTAL));

        sideTabsContainer = new BlurredFrameLayout(context, sizeNotifierFrameLayout);
        sideTabsContainer.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider));
        addView(sideTabsContainer, LayoutHelper.createFrame(64, LayoutHelper.MATCH_PARENT, Gravity.LEFT | Gravity.FILL_VERTICAL));
        sideTabsShadowView = new View(context);
        sideTabsShadowView.setBackgroundColor(Theme.getColor(Theme.key_divider, resourcesProvider));
        sideTabsContainer.addView(sideTabsShadowView, LayoutHelper.createFrame(1.0f / AndroidUtilities.density, LayoutHelper.MATCH_PARENT, Gravity.RIGHT | Gravity.FILL_VERTICAL));

        topTabs = new UniversalRecyclerView(context, currentAccount, 0, this::fillHorizontalTabs, this::onTabClick, this::onTabLongClick, resourcesProvider) {
            private final GradientClip clip = new GradientClip();
            private final AnimatedFloat animatedClip = new AnimatedFloat(this, 320, CubicBezierInterpolator.EASE_OUT_QUINT);
            private final RectF lineRect = new RectF();
            private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final AnimatedFloat animateTab = new AnimatedFloat(this, 420, CubicBezierInterpolator.EASE_OUT_QUINT);
            @Override
            protected void dispatchDraw(Canvas canvas) {
                final float clipAlpha = animatedClip.set(canScrollHorizontally(-1));
                if (clipAlpha > 0) {
                    canvas.saveLayerAlpha(0, 0, getWidth(), getHeight(), 0xFF, Canvas.ALL_SAVE_FLAG);
                }
                drawPinnedBackground(canvas);
                super.dispatchDraw(canvas);
                if (lastSelectedTopicId != currentTopicId) {
                    animateFromSelectedTopicId = lastSelectedTopicId;
                    animateTab.force(0.0f);
                }
                lastSelectedTopicId = currentTopicId;
                HorizontalTabView fromSelectedTab = null;
                HorizontalTabView selectedTab = null;
                for (int i = 0; i < getChildCount(); ++i) {
                    View child = getChildAt(i);
                    if (child instanceof HorizontalTabView) {
                        HorizontalTabView tab = (HorizontalTabView) child;
                        if (tab.getTopicId() == currentTopicId) {
                            selectedTab = tab;
                        }
                        if (tab.getTopicId() == animateFromSelectedTopicId) {
                            fromSelectedTab = tab;
                        }
                    }
                }
                if (selectedTab != null) {
                    lineRect.set(selectedTab.getX() + dp(6), getHeight() - dp(3), selectedTab.getX() + selectedTab.getWidth() - dp(6), selectedTab.getY() + getHeight() + dp(3));
                    if (fromSelectedTab != null) {
                        AndroidUtilities.rectTmp.set(fromSelectedTab.getX() + dp(6), getHeight() - dp(3), fromSelectedTab.getX() + fromSelectedTab.getWidth() - dp(6), fromSelectedTab.getY() + getHeight() + dp(3));
                        lerp(AndroidUtilities.rectTmp, lineRect, animateTab.set(1.0f), lineRect);
                    }
                    linePaint.setColor(Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider));
                    canvas.drawRoundRect(lineRect, dp(2), dp(2), linePaint);
                }
                if (clipAlpha > 0) {
                    canvas.save();
                    AndroidUtilities.rectTmp.set(0, 0, dp(12), getHeight());
                    clip.draw(canvas, AndroidUtilities.rectTmp, GradientClip.LEFT, clipAlpha);
                    canvas.restore();
                    canvas.restore();
                }
            }

            private Drawable pinIcon;
            private int pinIconColor;
            private final Paint pinnedBackgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            private void drawPinnedBackground(Canvas canvas) {
                int rpos = -1, lpos = -1;
                float r = 0, l = getWidth();
                for (int i = 0; i < getChildCount(); ++i) {
                    final View child = getChildAt(i);
                    if (!(child instanceof HorizontalTabView)) continue;
                    final HorizontalTabView tab = (HorizontalTabView) child;
                    if (tab.pinned) {
                        if (l > tab.getX()) {
                            l = tab.getX();
                            lpos = getChildAdapterPosition(tab);
                        }
                        if (r < tab.getX() + tab.getWidth()) {
                            r = tab.getX() + tab.getWidth();
                            rpos = getChildAdapterPosition(tab);
                        }
                    }
                }
                if (r > l) {
                    pinnedBackgroundPaint.setColor(Theme.getColor(Theme.key_chats_pinnedOverlay, resourcesProvider));
                    AndroidUtilities.rectTmp.set(l, (getHeight() - dp(38)) / 2f, r, (getHeight() + dp(38)) / 2f);
                    canvas.drawRoundRect(AndroidUtilities.rectTmp, dp(6), dp(6), pinnedBackgroundPaint);

                    if (pinIcon == null) {
                        pinIcon = getContext().getResources().getDrawable(R.drawable.msg_limit_pin).mutate();
                    }
                    final int pinColor = Theme.getColor(Theme.key_chats_pinnedIcon, resourcesProvider);
                    if (pinIconColor != pinColor) {
                        pinIcon.setColorFilter(new PorterDuffColorFilter(pinIconColor = pinColor, PorterDuff.Mode.SRC_IN));
                    }
                    pinIcon.setBounds((int) (l + dp(4)), (int) (AndroidUtilities.rectTmp.top + dp(2.66f)), (int) (l + dp(4 + 9.66f)), (int) (AndroidUtilities.rectTmp.top + dp(2.66f + 9.66f)));
                    pinIcon.draw(canvas);
                }
            }

            @Override
            public Integer getSelectorColor(int position) {
                return 0;
            }
        };
        topTabs.listenReorder(this::whenReordered);
        topTabs.setWillNotDraw(false);
        topTabs.adapter.setApplyBackground(false);
        topTabs.makeHorizontal();
        topTabsContainer.addView(topTabs, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL, 64, 0, 0, 0));
        topTabs.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                if (isLoadingVisible()) {
                    loadMore();
                }
            }
        });

        sideTabs = new UniversalRecyclerView(context, currentAccount, 0, this::fillVerticalTabs, this::onTabClick, this::onTabLongClick, resourcesProvider) {
            private final GradientClip clip = new GradientClip();
            private final AnimatedFloat animatedClip = new AnimatedFloat(this, 320, CubicBezierInterpolator.EASE_OUT_QUINT);
            @Override
            protected void dispatchDraw(Canvas canvas) {
                final float clipAlpha = animatedClip.set(canScrollVertically(-1));
                if (clipAlpha > 0) {
                    canvas.saveLayerAlpha(0, 0, getWidth(), getHeight(), 0xFF, Canvas.ALL_SAVE_FLAG);
                }
                drawPinnedBackground(canvas);
                super.dispatchDraw(canvas);
                if (clipAlpha > 0) {
                    canvas.save();
                    AndroidUtilities.rectTmp.set(0, 0, getWidth(), dp(12));
                    clip.draw(canvas, AndroidUtilities.rectTmp, GradientClip.TOP, clipAlpha);
                    canvas.restore();
                    canvas.restore();
                }
            }

            private Drawable pinIcon;
            private int pinIconColor;
            private final Paint pinnedBackgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            private void drawPinnedBackground(Canvas canvas) {
                int bpos = -1, tpos = -1;
                float b = 0, t = getHeight();
                for (int i = 0; i < getChildCount(); ++i) {
                    final View child = getChildAt(i);
                    if (!(child instanceof VerticalTabView)) continue;
                    final VerticalTabView tab = (VerticalTabView) child;
                    if (tab.pinned) {
                        if (t > tab.getY()) {
                            t = tab.getY();
                            tpos = getChildAdapterPosition(tab);
                        }
                        if (b < tab.getY() + tab.getHeight()) {
                            b = tab.getY() + tab.getHeight();
                            bpos = getChildAdapterPosition(tab);
                        }
                    }
                }
                if (b > t) {
                    pinnedBackgroundPaint.setColor(Theme.getColor(Theme.key_chats_pinnedOverlay, resourcesProvider));
                    AndroidUtilities.rectTmp.set((getWidth() - dp(56)) / 2f, t, (getWidth() + dp(56)) / 2f, b);
                    canvas.drawRoundRect(AndroidUtilities.rectTmp, dp(6), dp(6), pinnedBackgroundPaint);

                    if (pinIcon == null) {
                        pinIcon = getContext().getResources().getDrawable(R.drawable.msg_limit_pin).mutate();
                    }
                    final int pinColor = Theme.getColor(Theme.key_chats_pinnedIcon, resourcesProvider);
                    if (pinIconColor != pinColor) {
                        pinIcon.setColorFilter(new PorterDuffColorFilter(pinIconColor = pinColor, PorterDuff.Mode.SRC_IN));
                    }
                    pinIcon.setBounds((int) (AndroidUtilities.rectTmp.left + dp(4)), (int) (AndroidUtilities.rectTmp.top + dp(2.66f)), (int) (AndroidUtilities.rectTmp.left + dp(4 + 9.66f)), (int) (AndroidUtilities.rectTmp.top + dp(2.66f + 9.66f)));
                    pinIcon.draw(canvas);
                }
            }
        };
        sideTabs.listenReorder(this::whenReordered);
        sideTabs.adapter.setApplyBackground(false);
        sideTabs.setClipToPadding(false);
        sideTabs.setClipChildren(false);
        sideTabs.setPadding(0, 0, 0, 0);
        sideTabsContainer.addView(sideTabs, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL, 0, 48, 0, 0));
        sideTabs.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                if (isLoadingVisible()) {
                    loadMore();
                }
            }
        });

        button = new ImageView(context);
        button.setImageResource(R.drawable.menu_sidebar);
        button.setScaleType(ImageView.ScaleType.CENTER);
        addView(button, LayoutHelper.createFrame(64, 48, Gravity.LEFT | Gravity.TOP));
        ScaleStateListAnimator.apply(button);
        button.setOnClickListener(v -> {
            animateSidemenuTo(pendingSidemenu != null ? !pendingSidemenu : !sidemenuEnabled);
        });

        closeButton = new ImageView(context);
        closeButton.setImageResource(R.drawable.msg_select);
        closeButton.setScaleType(ImageView.ScaleType.CENTER);
        addView(closeButton, LayoutHelper.createFrame(64, 48, Gravity.LEFT | Gravity.TOP));
        ScaleStateListAnimator.apply(closeButton);
        closeButton.setOnClickListener(v -> {
            sideTabs.allowReorder(false);
            topTabs.allowReorder(false);
            animateButton(false);
            AndroidUtilities.updateVisibleRows(sideTabs);
            AndroidUtilities.updateVisibleRows(topTabs);
        });
        closeButton.setAlpha(0.0f);
        closeButton.setScaleX(0.4f);
        closeButton.setScaleY(0.4f);
        closeButton.setVisibility(View.GONE);

        //if (!mono) {
        //    MessagesController.getInstance(currentAccount).getTopicsController().preloadTopics(-dialogId);
        //}

        MessagesController.getInstance(currentAccount).getTopicsController().loadTopics(-dialogId, false, TopicsController.LOAD_TYPE_HASH_CHECK);

        if (MessagesController.getInstance(currentAccount).getMainSettings().getBoolean("topicssidetabs" + dialogId, false)) {
            sidemenuT = 1.0f;
            sidemenuEnabled = true;
        }

        updateSidemenuPosition();
        updateTabs();
    }

    private void animateButton(boolean close) {
        if (close) {
            closeButton.setVisibility(View.VISIBLE);
            closeButton.animate()
                .alpha(1.0f)
                .scaleX(1.0f)
                .scaleY(1.0f)
                .setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT)
                .setDuration(320)
                .start();
            button.setVisibility(View.VISIBLE);
            button.animate()
                .alpha(0.0f)
                .scaleX(0.4f)
                .scaleY(0.4f)
                .setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT)
                .setDuration(320)
                .withEndAction(() -> {
                    button.setVisibility(View.GONE);
                })
                .start();
        } else {
            button.setVisibility(View.VISIBLE);
            button.animate()
                .alpha(1.0f)
                .scaleX(1.0f)
                .scaleY(1.0f)
                .setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT)
                .setDuration(320)
                .start();
            closeButton.setVisibility(View.VISIBLE);
            closeButton.animate()
                .alpha(0.0f)
                .scaleX(0.4f)
                .scaleY(0.4f)
                .setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT)
                .setDuration(320)
                .withEndAction(() -> {
                    closeButton.setVisibility(View.GONE);
                })
                .start();
        }
    }

    public void setBottomMargin(int margin) {
        sideTabs.setPadding(0, 0, 0, margin);
    }

    @Override
    protected void dispatchDraw(@NonNull Canvas canvas) {
        canvas.save();
        canvas.clipRect(0, 0, getWidth(), getHeight());
        super.dispatchDraw(canvas);
        canvas.restore();
    }

    public boolean sidemenuEnabled;
    public float sidemenuT = 0.0f;
    public boolean sidemenuAnimating;
    public void updateSidemenuPosition() {
        topTabsContainer.setTranslationY(-dp(48) * sidemenuT);
        topTabsContainer.setAlpha(lerp(1.0f, 0.85f, sidemenuT));
        topTabsContainer.setVisibility(sidemenuT >= 1.0f ? View.GONE : View.VISIBLE);
        topTabsShadowView.setTranslationY(-dp(48 + 3) * sidemenuT);
        topTabsShadowView.setAlpha(1.0f - sidemenuT);
        topTabsShadowView.setVisibility(sidemenuT >= 1.0f ? View.GONE : View.VISIBLE);

        sideTabsContainer.setTranslationX(-dp(64) * (1.0f - sidemenuT));
        sideTabsContainer.setVisibility(sidemenuT <= 0.0f ? View.GONE : View.VISIBLE);
        sideTabsShadowView.setVisibility(sidemenuT <= 0.0f ? View.GONE : View.VISIBLE);

        button.setColorFilter(new PorterDuffColorFilter(
            ColorUtils.blendARGB(
                Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2, resourcesProvider),
                Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider),
                sidemenuT
            ),
            PorterDuff.Mode.SRC_IN
        ));
        closeButton.setColorFilter(new PorterDuffColorFilter(
            Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider),
            PorterDuff.Mode.SRC_IN
        ));
    }

    private Boolean pendingSidemenu;
    private ValueAnimator animator;
    private void animateSidemenuTo(boolean side) {
        if (sidemenuEnabled == side) return;
        if (animator != null) {
            animator.cancel();
            if (sidemenuAnimating) {
                pendingSidemenu = side;
                return;
            }
        }
        sidemenuEnabled = side;
        sidemenuAnimating = true;
        animator = ValueAnimator.ofFloat(sidemenuT, side ? 1.0f : 0.0f);
        animator.addUpdateListener(anm -> {
            sidemenuT = (float) anm.getAnimatedValue();
            updateSidemenuPosition();
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (animator == animation) {
                    sidemenuT = side ? 1.0f : 0.0f;
                    updateSidemenuPosition();
                    sidemenuAnimating = false;
                    animator = null;
                    MessagesController.getInstance(currentAccount).getMainSettings().edit().putBoolean("topicssidetabs" + dialogId, sidemenuEnabled).apply();
                    if (pendingSidemenu != null && side != pendingSidemenu) {
                        final boolean newValue = pendingSidemenu;
                        pendingSidemenu = null;
                        animateSidemenuTo(newValue);
                    }
                    AndroidUtilities.runOnUIThread(() -> {
                        if (isLoadingVisible()) {
                            loadMore();
                        }
                    });
                }
            }
        });
//        animator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
//        animator.setDuration(320);
        animator.setInterpolator(ChatListItemAnimator.DEFAULT_INTERPOLATOR);
        animator.setDuration(ChatListItemAnimator.DEFAULT_DURATION);
        animator.start();
    }

    private long currentTopicId;
    private void updateTabs() {
        boolean wasOnLeft = !topTabs.canScrollHorizontally(-1);
        topTabs.adapter.update(true);
        if (wasOnLeft) {
            topTabs.scrollToPosition(0);
        }

        boolean wasOnTop = !sideTabs.canScrollVertically(-1);
        sideTabs.adapter.update(true);
        if (wasOnTop) {
            sideTabs.scrollToPosition(0);
        }
        AndroidUtilities.runOnUIThread(() -> {
            if (isLoadingVisible()) {
                loadMore();
            }
        });
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.savedMessagesDialogsUpdate) {
            if ((Long) args[0] != dialogId)
                return;
            updateTabs();
        } else if (id == NotificationCenter.topicsDidLoaded) {
            if ((Long) args[0] != -dialogId)
                return;
            updateTabs();
        } else if (id == NotificationCenter.updateInterfaces) {
            int mask = (Integer) args[0];
            if (/*!mono &&*/ (mask & MessagesController.UPDATE_MASK_SELECT_DIALOG) > 0) {
                MessagesController.getInstance(currentAccount).getTopicsController().sortTopics(-dialogId, false);
                updateTabs();
            }
        }
    }

    private void whenReordered(int id, ArrayList<UItem> items) {
        // if (mono) return;
        final TopicsController controller = MessagesController.getInstance(currentAccount).getTopicsController();
        final ArrayList<Integer> topics = new ArrayList<>();
        for (int i = 0; i < items.size(); ++i) {
            topics.add(items.get(i).id);
        }
        controller.reorderPinnedTopics(-dialogId, topics);
        controller.sortTopics(-dialogId, false);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        setAttached(true);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        setAttached(false);
    }

    private boolean notificationsAttached;
    private void setAttached(boolean attach) {
        if (notificationsAttached == attach) return;
        if (notificationsAttached = attach) {
            NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.savedMessagesDialogsUpdate);
            NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.topicsDidLoaded);
            NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.updateInterfaces);
            MessagesController.getInstance(currentAccount).getTopicsController().onTopicFragmentResume(-dialogId);
        } else {
            MessagesController.getInstance(currentAccount).getTopicsController().onTopicFragmentPause(-dialogId);
            NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.savedMessagesDialogsUpdate);
            NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.topicsDidLoaded);
            NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.updateInterfaces);
        }
    }

    private void fillVerticalTabs(ArrayList<UItem> items, UniversalAdapter adapter) {
        final TopicsController controller = MessagesController.getInstance(currentAccount).getTopicsController();
        final ArrayList<TLRPC.TL_forumTopic> topics = controller.getTopics(-dialogId);
        items.add(VerticalTabView.Factory.asAll(mono).setChecked(currentTopicId == 0));
        boolean reorder = false;
        if (topics != null) {
            for (TLRPC.TL_forumTopic topic : topics) {
                if (excludeTopics.contains(topic.id)) continue;
                if (!topic.pinned && reorder) {
                    adapter.reorderSectionEnd();
                    reorder = false;
                } else if (topic.pinned && !reorder) {
                    adapter.reorderSectionStart();
                    reorder = true;
                }
                items.add(VerticalTabView.Factory.asTab(dialogId, topic, mono).setChecked(currentTopicId == getTopicId(topic)));
            }
        }
        if (reorder) {
            adapter.reorderSectionEnd();
        }
        if (topics != null && !topics.isEmpty() && !controller.endIsReached(-dialogId) && canShowProgress) {
            items.add(VerticalTabView.Factory.asLoading(-2));
            items.add(VerticalTabView.Factory.asLoading(-3));
            items.add(VerticalTabView.Factory.asLoading(-4));
        }
        if (!mono) {
            items.add(VerticalTabView.Factory.asAdd(false));
        }
    }

    private void fillHorizontalTabs(ArrayList<UItem> items, UniversalAdapter adapter) {
        final TopicsController controller = MessagesController.getInstance(currentAccount).getTopicsController();
        final ArrayList<TLRPC.TL_forumTopic> topics = controller.getTopics(-dialogId);
        items.add(HorizontalTabView.Factory.asAll(mono).setChecked(currentTopicId == 0));
        boolean reorder = false;
        if (topics != null) {
            for (TLRPC.TL_forumTopic topic : topics) {
                if (excludeTopics.contains(topic.id)) continue;
                if (!topic.pinned && reorder) {
                    adapter.reorderSectionEnd();
                    reorder = false;
                } else if (topic.pinned && !reorder) {
                    adapter.reorderSectionStart();
                    reorder = true;
                }
                items.add(HorizontalTabView.Factory.asTab(dialogId, topic, mono).setChecked(currentTopicId == getTopicId(topic)));
            }
        }
        if (reorder) {
            adapter.reorderSectionEnd();
        }
        if (topics != null && !topics.isEmpty() && !controller.endIsReached(-dialogId) && canShowProgress) {
            items.add(HorizontalTabView.Factory.asLoading(-2));
            items.add(HorizontalTabView.Factory.asLoading(-3));
            items.add(HorizontalTabView.Factory.asLoading(-4));
        }
    }

    private boolean isLoadingVisible() {
        if (sidemenuT > 0.5f) {
            for (int i = 0; i < sideTabs.getChildCount(); ++i) {
                final View child = sideTabs.getChildAt(i);
                final int position = sideTabs.getChildAdapterPosition(child);
                final UItem item = sideTabs.adapter.getItem(position);
                if (item != null && item.red) return true;
            }
        } else {
            for (int i = 0; i < topTabs.getChildCount(); ++i) {
                final View child = topTabs.getChildAt(i);
                final int position = topTabs.getChildAdapterPosition(child);
                final UItem item = topTabs.adapter.getItem(position);
                if (item != null && item.red) return true;
            }
        }
        return false;
    }

    private void loadMore() {
        final TopicsController controller = MessagesController.getInstance(currentAccount).getTopicsController();
        if (!controller.endIsReached(-dialogId)) {
            controller.loadTopics(-dialogId);
        }
    }

    private void onTabClick(UItem item, View view, int position, float x, float y) {
        if (mono) {
            if (onDialogSelected != null) {
                onDialogSelected.run(item.longValue, false);
            }
        } else {
            if (item.longValue == -2) {
                if (onTopicCreated != null) {
                    onTopicCreated.run();
                }
            } else if (onTopicSelected != null) {
                onTopicSelected.run(item.id, false);
            }
        }
    }

    private boolean onTabLongClick(UItem item, View view, int position, float x, float y) {
        if (sideTabs.isReorderAllowed() || topTabs.isReorderAllowed()) return false;
        if (item.object instanceof TLRPC.TL_forumTopic) {
            final TLRPC.TL_forumTopic topic = (TLRPC.TL_forumTopic) item.object;
            final MessagesController messagesController = MessagesController.getInstance(currentAccount);
            final TLRPC.Chat currentChat = messagesController.getChat(-dialogId);
            final ItemOptions options = ItemOptions.makeOptions(fragment, view, true);

            if (ChatObject.isMonoForum(currentChat)) {
                final long topicId = DialogObject.getPeerDialogId(topic.from_id);
                if (topicId == 0 || !ChatObject.canManageMonoForum(currentAccount, currentChat)) {
                    return false;
                };

                options.add(
                    R.drawable.msg_clear,
                    getString(R.string.ClearHistory),
                    () -> {
                        options.dismiss();
                        final TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(topicId);
                        if (user != null) {
                            AlertsCreator.createClearDaysDialogAlert(fragment, -1, user, currentChat, true, revoke -> {
                                if (fragment instanceof ChatActivity) {
                                    ((ChatActivity) fragment).performHistoryClear(topicId, false, true);
                                }
                            }, fragment.getResourceProvider());
                        }
                    }
                );
            } else {
                if (ChatObject.canManageTopics(currentChat)) {
                    options.add(
                            topic.pinned ? R.drawable.msg_unpin : R.drawable.msg_pin,
                            getString(topic.pinned ? R.string.DialogUnpin : R.string.DialogPin),
                            () -> {
                                options.dismiss();
                                messagesController.getTopicsController()
                                        .pinTopic(-dialogId, topic.id, !topic.pinned, fragment);
                            }
                    );

                    if (topic.pinned) {
                        options.add(
                                R.drawable.tabs_reorder,
                                getString(R.string.FilterReorder),
                                () -> {
                                    sideTabs.allowReorder(true);
                                    topTabs.allowReorder(true);
                                    animateButton(true);
                                    AndroidUtilities.updateVisibleRows(topTabs);
                                    AndroidUtilities.updateVisibleRows(sideTabs);
                                }
                        );
                    }
                }

                final ItemOptions muteOptions = ChatNotificationsPopupWrapper.addAsItemOptions(fragment, options, dialogId, topic.id);
                final boolean muted = messagesController.isDialogMuted(dialogId, topic.id);
                options.add(
                        muted ? R.drawable.msg_unmute : R.drawable.msg_mute,
                        muted ? getString(R.string.Unmute) : getString(R.string.Mute),
                        () -> {
                            if (messagesController.isDialogMuted(dialogId, topic.id)) {
                                options.dismiss();
                                NotificationsController.getInstance(currentAccount).muteDialog(dialogId, topic.id, false);
                                if (BulletinFactory.canShowBulletin(fragment)) {
                                    BulletinFactory.createMuteBulletin(fragment, NotificationsController.SETTING_MUTE_UNMUTE, 0, resourcesProvider).show();
                                }
                            } else {
                                options.openSwipeback(muteOptions);
                            }
                        }
                );
                if (ChatObject.canManageTopic(currentAccount, currentChat, topic)) {
                    options.add(
                            topic.closed ? R.drawable.msg_topic_restart : R.drawable.msg_topic_close,
                            topic.closed ? getString(R.string.RestartTopic) : getString(R.string.CloseTopic),
                            () -> {
                                options.dismiss();
                                MessagesController.getInstance(currentAccount).getTopicsController().toggleCloseTopic(-dialogId, topic.id, !topic.closed);
                            }
                    );
                }
                if (ChatObject.canDeleteTopic(currentAccount, currentChat, topic)) {
                    options.add(R.drawable.msg_delete, getPluralString("DeleteTopics", 1), () -> {
                        options.dismiss();
                        HashSet<Integer> hashSet = new HashSet();
                        hashSet.add(topic.id);
                        deleteTopics(hashSet, () -> {
                        });
                    });
                }
            }
            if (view instanceof HorizontalTabView) {
                options.setScrimViewBackground(Theme.createRoundRectDrawable(dp(5), dp(5), 0, 0, Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider)));
                options.translate(dp(16), 0);
            } else {
                options.setScrimViewBackground(Theme.createRoundRectDrawable(0, dp(5), dp(5), 0, Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider)));
            }
            options.show();
            return true;
        }
        return false;
    }

    public TLRPC.TL_forumTopic getTopic(long id) {
        final ArrayList<TLRPC.TL_forumTopic> topics = MessagesController.getInstance(currentAccount).getTopicsController().getTopics(-dialogId);
        if (topics != null) {
            for (TLRPC.TL_forumTopic topic : topics) {
                if (topic.id == id)
                    return topic;
            }
        }
        return null;
    }

    public void setCurrentTopic(long topicId) {
        this.currentTopicId = topicId;
        topTabs.adapter.update(true);
        topTabs.invalidate();
        sideTabs.adapter.update(true);
    }

    private Utilities.Callback2<Integer, Boolean> onTopicSelected;
    public void setOnTopicSelected(Utilities.Callback2<Integer, Boolean> listener) {
        onTopicSelected = listener;
    }
    private Runnable onTopicCreated;
    public void setOnNewTopicSelected(Runnable listener) {
        onTopicCreated = listener;
    }
    public void selectTopic(long topicId, boolean fromMessage) {
        if (mono) {
            if (onDialogSelected != null) {
                onDialogSelected.run(topicId, fromMessage);
            }
        } else {
            if (onTopicSelected != null) {
                onTopicSelected.run((int) topicId, fromMessage);
            }
        }
    }

    private Utilities.Callback2<Long, Boolean> onDialogSelected;
    public void setOnDialogSelected(Utilities.Callback2<Long, Boolean> listener) {
        onDialogSelected = listener;
    }

    public static class VerticalTabView extends FrameLayout {

        private final int currentAccount;
        private final Theme.ResourcesProvider resourcesProvider;
        private Shaker shaker;

        private final LinearLayout layout;
        private final FrameLayout.LayoutParams imageViewParams;
        private final AnimatedTextView.AnimatedTextDrawable counterText;
        private final FrameLayout imageLayoutView;
        private final BackupImageView imageView;
        private final AvatarDrawable avatarDrawable;
        private final TextView textView;
        private final View lineView;

        private boolean reorder;
        public void setReorder(boolean value) {
            this.reorder = value;
            layout.invalidate();
        }

        public VerticalTabView(Context context, int currentAccount, Theme.ResourcesProvider resourcesProvider) {
            super(context);
            this.currentAccount = currentAccount;
            this.resourcesProvider = resourcesProvider;

            layout = new LinearLayout(context) {
                private final AnimatedFloat shakeAlpha = new AnimatedFloat(this, 360, CubicBezierInterpolator.EASE_OUT_QUINT);
                @Override
                protected void dispatchDraw(@NonNull Canvas canvas) {
                    canvas.save();
                    final float shakeAlpha = this.shakeAlpha.set(reorder);
                    if (shakeAlpha > 0) {
                        if (shaker == null) shaker = new Shaker(this);
                        canvas.translate(getWidth() / 2f, getHeight() / 2f);
                        shaker.concat(canvas, shakeAlpha);
                        canvas.translate(-getWidth() / 2f, -getHeight() / 2f);
                    }
                    super.dispatchDraw(canvas);
                    canvas.restore();
                }
            };
            layout.setWillNotDraw(false);
            layout.setOrientation(LinearLayout.VERTICAL);
            addView(layout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL, 1, 0, 0, 0));
            ScaleStateListAnimator.apply(layout);

            counterText = new AnimatedTextView.AnimatedTextDrawable();
            counterText.setTextSize(dp(11));
            counterText.setTypeface(AndroidUtilities.bold());
            counterText.setTextColor(Theme.getColor(Theme.key_chats_unreadCounterText, resourcesProvider));
            counterText.setOverrideFullWidth(AndroidUtilities.displaySize.x);
            counterText.setGravity(Gravity.CENTER);
            imageLayoutView = new FrameLayout(context) {
                private final Paint clipPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                private final AnimatedPaint backgroundPaint = new AnimatedPaint(this, resourcesProvider);
                {
                    clipPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
                    counterText.setCallback(this);
                }
                @Override
                protected boolean verifyDrawable(@NonNull Drawable who) {
                    return counterText == who || super.verifyDrawable(who);
                }

                @Override
                protected void dispatchDraw(@NonNull Canvas canvas) {
                    final float counterAlpha = counterText.isNotEmpty();
                    final boolean counterVisible = counterAlpha > 0.0f;
                    final float counterScale = lerp(0.5f, 1.0f, counterAlpha) * countScale;
                    final float R = dp(10f), r = dp(8.33f);
                    final float cx = getWidth() / 2f + dp(12);
                    final float cy = dp(22 - 10);
                    final float w = Math.max(r + r, counterText.getCurrentWidth() + dp(10));

                    if (counterVisible) {
                        canvas.saveLayerAlpha(0, 0, getWidth(), getHeight(), 0xFF, Canvas.ALL_SAVE_FLAG);
                    }
                    super.dispatchDraw(canvas);
                    if (counterVisible) {
                        AndroidUtilities.rectTmp.set(cx - w / 2f - dp(1.33f), cy - R, cx + w / 2f + dp(1.33f), cy + R);
                        AndroidUtilities.scaleRect(AndroidUtilities.rectTmp, counterAlpha);
                        canvas.drawRoundRect(AndroidUtilities.rectTmp, R * counterAlpha, R * counterAlpha, clipPaint);
                        canvas.restore();
                    }

                    if (counterAlpha > 0.0f) {
                        canvas.save();
                        canvas.scale(counterScale, counterScale, cx, cy);
                        AndroidUtilities.rectTmp.set(cx - w / 2f, cy - r, cx + w / 2f, cy + r);
                        canvas.drawRoundRect(AndroidUtilities.rectTmp, r, r, backgroundPaint.setByKey(counterBackgroundColorKey, counterAlpha));
                        counterText.setBounds(AndroidUtilities.rectTmp);
                        counterText.setAlpha((int) (0xFF * counterAlpha));
                        counterText.draw(canvas);
                        canvas.restore();
                    }
                }
            };
            imageLayoutView.setWillNotDraw(false);
            imageLayoutView.setPadding(0, dp(4), 0, 0);
            layout.addView(imageLayoutView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));

            imageView = new BackupImageView(context);
            imageLayoutView.addView(imageView, imageViewParams = LayoutHelper.createFrame(30, 30, Gravity.CENTER));
            avatarDrawable = new AvatarDrawable();

            textView = new TextView(context);
            textView.setTextColor(ColorUtils.blendARGB(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2, resourcesProvider), Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider), selectT));
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 10);
            textView.setGravity(Gravity.CENTER);
            textView.setTypeface(AndroidUtilities.bold());
            textView.setMaxLines(3);
            textView.setEllipsize(TextUtils.TruncateAt.END);
            layout.addView(textView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 4, 0, 4, 4));

            lineView = new ImageView(context);
            lineView.setBackground(Theme.createRoundRectDrawable(dp(2.33f), Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider)));
            addView(lineView, LayoutHelper.createFrame(6, LayoutHelper.MATCH_PARENT, Gravity.FILL_VERTICAL | Gravity.LEFT, -3, 3, 0, 3));
            lineView.setTranslationX(-dp(3));
            lineView.setVisibility(View.GONE);
        }

        private boolean mono = false;
        private void setLayout(boolean mono) {
            if (this.mono == mono) return;
            this.mono = mono;
            imageView.setRoundRadius(dp(mono ? 36 : 3));
            imageLayoutView.setPadding(0, dp(mono ? 7 : 4), 0, 0);
            imageViewParams.width = mono ? dp(28) : dp(30);
            imageViewParams.height = mono ? dp(28) : dp(30);
        }

        private boolean pinned = false;
        private void setPinned(boolean pinned, boolean animated) {
            if (this.pinned != pinned) {
                this.pinned = pinned;
//                final int overlay = Theme.getColor(Theme.key_chats_pinnedOverlay, resourcesProvider);
//                if (Theme.isCurrentThemeDark()) {
//                    setBackgroundColor(pinned ? overlay : 0);
//                } else {
//                    setBackgroundColor(pinned ? Theme.blendOver(Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider), overlay) : 0);
//                }
            }
        }

        private int counterBackgroundColorKey = Theme.key_chats_unreadCounter;
        private CharSequence mentionString;
        private CharSequence reactionString;
        private int lastUnread;
        private boolean lastMuted, lastMention, lastReactions;
        private void setCounter(boolean muted, int unread, boolean mention, boolean reactions, boolean animated) {
            if (reactions) {
                counterBackgroundColorKey = Theme.key_dialogReactionMentionBackground;
                if (reactionString == null) {
                    final SpannableStringBuilder sb = new SpannableStringBuilder("❤️");
                    final ColoredImageSpan span = new ColoredImageSpan(R.drawable.reactionchatslist);
                    span.setScale(0.8f, 0.8f);
                    span.spaceScaleX = 0.5f;
                    span.translate(-dp(3), 0);
                    sb.setSpan(span, 0, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    reactionString = sb;
                }
                counterText.setText(reactionString, animated);
            } else if (mention) {
                counterBackgroundColorKey = muted ? Theme.key_chats_unreadCounterMuted : Theme.key_chats_unreadCounter;
                if (mentionString == null) {
                    final SpannableStringBuilder sb = new SpannableStringBuilder("@");
                    final ColoredImageSpan span = new ColoredImageSpan(R.drawable.mentionchatslist);
                    span.setScale(0.8f, 0.8f);
                    span.spaceScaleX = 0.5f;
                    span.translate(-dp(3), 0);
                    sb.setSpan(span, 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    mentionString = sb;
                }
                counterText.setText(mentionString, animated);
            } else if (unread > 0) {
                counterBackgroundColorKey = muted ? Theme.key_chats_unreadCounterMuted : Theme.key_chats_unreadCounter;
                counterText.setText(LocaleController.formatNumber(unread, ','), animated);
            } else {
                counterBackgroundColorKey = Theme.key_chats_unreadCounterMuted;
                counterText.setText("", animated);
            }
            if (animated && (lastUnread < unread || !lastMention && mention || !lastReactions && reactions)) {
                animateCounterBounce();
            }
            lastUnread = unread;
            lastMention = mention;
            lastReactions = reactions;
            imageLayoutView.invalidate();
        }

        private float countScale = 1;
        private ValueAnimator counterAnimator;
        private void animateCounterBounce() {
            if (counterAnimator != null) {
                counterAnimator.cancel();
                counterAnimator = null;
            }

            counterAnimator = ValueAnimator.ofFloat(0, 1);
            counterAnimator.addUpdateListener(anm -> {
                countScale = Math.max(1, (float) anm.getAnimatedValue());
                imageLayoutView.invalidate();
            });
            counterAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    countScale = 1;
                    imageLayoutView.invalidate();
                }
            });
            counterAnimator.setInterpolator(new OvershootInterpolator(2.0f));
            counterAnimator.setDuration(200);
            counterAnimator.start();
        }

        public void setAll(boolean mono, boolean selected) {
            setLayout(mono);
            this.topicId = -1;
            this.staticImage = true;
            this.isAdd = false;
            textView.setText(getString(R.string.AllTopicsSide));
            imageView.clearImage();
            imageView.setAnimatedEmojiDrawable(null);
            imageView.setImageResource(R.drawable.other_chats);
            imageView.setScaleX(1f);
            imageView.setScaleY(1f);
            setSelected(selected);
            updateImageColor();
            updateState();
            setCounter(true, 0, false, false, false);
            setPinned(false, false);
        }

        private long topicId = 0;
        private boolean isAdd = false;
        private boolean staticImage = false;
        public void setAdd(boolean mono, boolean selected) {
            setLayout(mono);
            this.staticImage = true;
            this.isAdd = true;
            textView.setText(getString(R.string.NewTopic));
            imageView.clearImage();
            imageView.setAnimatedEmojiDrawable(null);
            imageView.setImageResource(R.drawable.emoji_tabs_new3);
            imageView.setScaleX(1f);
            imageView.setScaleY(1f);
            setSelected(selected);
            updateImageColor();
            updateState();
            setCounter(true, 0, false, false, false);
            setPinned(false, false);
        }

        private LoadingDrawable loadingDrawable;
        public void setLoading() {
            setLayout(false);
            this.topicId = -1;
            this.staticImage = true;
            this.isAdd = false;
            final SpannableStringBuilder sb = new SpannableStringBuilder("x");
            final LoadingSpan span = new LoadingSpan(textView, dp(38));
            span.setScaleY(0.75f);
            sb.setSpan(span, 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            textView.setText(sb);
            imageView.clearImage();
            imageView.setAnimatedEmojiDrawable(null);
            if (loadingDrawable == null) {
                loadingDrawable = new LoadingDrawable(resourcesProvider);
                loadingDrawable.setRadiiDp(38);
                loadingDrawable.setCallback(imageView);
                final int textColor = Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2, resourcesProvider);
                loadingDrawable.setColors(
                    Theme.multAlpha(textColor, .15f),
                    Theme.multAlpha(textColor, .5f),
                    Theme.multAlpha(textColor, .6f),
                    Theme.multAlpha(textColor, .15f)
                );
                loadingDrawable.stroke = false;
            }
            imageView.setImageDrawable(loadingDrawable);
            imageView.setScaleX(1f);
            imageView.setScaleY(1f);
            setSelected(false);
            updateImageColor();
            setCounter(true, 0, false, false, false);
            setPinned(false, false);
            updateState();
        }

        public void set(long dialogId, TLRPC.TL_forumTopic topic, boolean selected) {
            setLayout(false);
            final boolean animated = topicId == topic.id;
            this.staticImage = false;
            this.topicId = topic.id;
            this.isAdd = false;
            textView.setText(topic.title);
            if (topic.id == 1) {
                this.staticImage = true;
                imageView.clearImage();
                imageView.setImageResource(R.drawable.msg_filled_general);
                imageView.setScaleX(0.66f);
                imageView.setScaleY(0.66f);
            } else if (topic.icon_emoji_id != 0) {
                imageView.setAnimatedEmojiDrawable(AnimatedEmojiDrawable.make(UserConfig.selectedAccount, AnimatedEmojiDrawable.CACHE_TYPE_ALERT_PREVIEW, topic.icon_emoji_id));
                imageView.setScaleX(1f);
                imageView.setScaleY(1f);
            } else {
                imageView.setImageDrawable(ForumUtilities.createTopicDrawable(topic, false));
                imageView.setScaleX(1f);
                imageView.setScaleY(1f);
            }
            setSelected(selected);
            updateImageColor();
            setCounter(
                MessagesController.getInstance(currentAccount).isDialogMuted(dialogId, topic.id),
                topic.unread_count,
                topic.unread_mentions_count > 0,
                topic.unread_reactions_count > 0,
                animated
            );
            setPinned(topic.pinned, animated);
            updateState();
        }

        private void updateImageColor() {
            final int color = ColorUtils.blendARGB(
                Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2, resourcesProvider),
                Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider),
                isAdd ? 1.0f : selectT
            );
            if (!staticImage) {
                imageView.setColorFilter(null);
            } else {
                imageView.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN));
            }
            imageView.setEmojiColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN));
            imageView.invalidate();
        }

        public void setMf(TLRPC.TL_forumTopic dialog, boolean selected) {
            setLayout(true);
            this.isAdd = false;
            this.staticImage = false;
            final long dialogId = DialogObject.getPeerDialogId(dialog.from_id);
            final boolean animated = dialogId == topicId;
            topicId = dialogId;
            textView.setText(DialogObject.getName(dialogId));
            if (dialogId >= 0) {
                TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(dialogId);
                avatarDrawable.setInfo(user);
                imageView.setForUserOrChat(user, avatarDrawable);
            } else {
                TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-dialogId);
                avatarDrawable.setInfo(chat);
                imageView.setForUserOrChat(chat, avatarDrawable);
            }
            imageView.setScaleX(1f);
            imageView.setScaleY(1f);
            updateState();
            setSelected(selected);
            setCounter(
                false, // MessagesController.getInstance(currentAccount).isDialogMuted(dialogId, dialog.dialogId),
                dialog.unread_count,
                false,
                dialog.unread_reactions_count > 0,
                animated
            );
            setPinned(false, animated);
        }

        private float selectT;
        private boolean selected;
        private ValueAnimator selectAnimator;
        public void setSelected(boolean selected) {
            if (this.selected == selected) return;
            this.selected = selected;
            if (selectAnimator != null) {
                selectAnimator.cancel();
            }
            selectAnimator = ValueAnimator.ofFloat(selectT, selected ? 1f : 0f);
            selectAnimator.addUpdateListener(anm -> {
                selectT = (float) anm.getAnimatedValue();
                updateState();
                updateImageColor();
            });
            selectAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    selectT = selected ? 1f : 0f;
                    updateState();
                    updateImageColor();
                }
            });
            selectAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
            selectAnimator.setDuration(320);
            selectAnimator.start();
        }

        private void updateState() {
            lineView.setTranslationX(-dp(3) * (1.0f - selectT));
            lineView.setVisibility(selectT <= 0.0f ? View.GONE : View.VISIBLE);
            textView.setTextColor(ColorUtils.blendARGB(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2, resourcesProvider), Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider), isAdd ? 1.0f : selectT));
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(
                MeasureSpec.makeMeasureSpec(dp(64), MeasureSpec.EXACTLY),
                heightMeasureSpec
            );
        }

        public static class Factory extends UItem.UItemFactory<VerticalTabView> {
            static { setup(new Factory()); }

            @Override
            public VerticalTabView createView(Context context, int currentAccount, int classGuid, Theme.ResourcesProvider resourcesProvider) {
                return new VerticalTabView(context, currentAccount, resourcesProvider);
            }

            @Override
            public void bindView(View view, UItem item, boolean divider, UniversalAdapter adapter, UniversalRecyclerView listView) {
                final VerticalTabView cell = (VerticalTabView) view;
                if (item.red) {
                    cell.setLoading();
                } else if (item.object == null) {
                    if (item.longValue == -2) {
                        cell.setAdd(item.accent, item.checked);
                    } else {
                        cell.setAll(item.accent, item.checked);
                    }
                } else if (item.object instanceof TLRPC.TL_forumTopic) {
                    if (!item.withUsername) {
                        cell.setMf((TLRPC.TL_forumTopic) item.object, item.checked);
                    } else {
                        cell.set(item.dialogId, (TLRPC.TL_forumTopic) item.object, item.checked);
                    }
                }
                cell.setReorder(listView != null && listView.isReorderAllowed() && cell.pinned);
            }

            public static UItem asAll(boolean monoforum) {
                UItem item = UItem.ofFactory(Factory.class);
                item.id = 0;
                item.longValue = 0;
                item.object = null;
                item.accent = monoforum;
                return item;
            }

            public static UItem asAdd(boolean monoforum) {
                UItem item = UItem.ofFactory(Factory.class);
                item.id = -2;
                item.longValue = -2;
                item.object = null;
                item.accent = monoforum;
                return item;
            }

            public static UItem asTab(long dialogId, TLRPC.TL_forumTopic topic, boolean mono) {
                UItem item = UItem.ofFactory(Factory.class);
                item.dialogId = dialogId;
                item.id = topic.id;
                item.object = topic;
                if (mono) {
                    item.longValue = DialogObject.getPeerDialogId(topic.from_id);
                    item.withUsername = false;
                }
                return item;
            }

            public static UItem asLoading(int id) {
                UItem item = UItem.ofFactory(Factory.class);
                item.id = id;
                item.red = true;
                item.checked = false;
                return item;
            }
        }
    }

    public static class HorizontalTabView extends FrameLayout {

        private final int currentAccount;
        private final Theme.ResourcesProvider resourcesProvider;

        private Shaker shaker;
        private final LinkSpanDrawable.LinksTextView textView;
        private final AnimatedTextView.AnimatedTextDrawable counterText;
        private final View counterView;

        private boolean reorder;
        public void setReorder(boolean value) {
            this.reorder = value;
            invalidate();
        }

        private final AnimatedFloat shakeAlpha = new AnimatedFloat(this, 360, CubicBezierInterpolator.EASE_OUT_QUINT);
        @Override
        protected boolean drawChild(@NonNull Canvas canvas, View child, long drawingTime) {
            if (child == textView) {
                canvas.save();
                final float shakeAlpha = this.shakeAlpha.set(reorder);
                if (shakeAlpha > 0) {
                    if (shaker == null) shaker = new Shaker(this);
                    canvas.translate(getWidth() / 2f, getHeight() / 2f);
                    shaker.concat(canvas, shakeAlpha);
                    canvas.translate(-getWidth() / 2f, -getHeight() / 2f);
                }
                boolean r = super.drawChild(canvas, child, drawingTime);
                canvas.restore();
                return r;
            }
            return super.drawChild(canvas, child, drawingTime);
        }

        public HorizontalTabView(Context context, int currentAccount, Theme.ResourcesProvider resourcesProvider) {
            super(context);
            this.currentAccount = currentAccount;
            this.resourcesProvider = resourcesProvider;
            setClipChildren(false);
            setClipToPadding(false);

            textView = new LinkSpanDrawable.LinksTextView(context, resourcesProvider);
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14.66f);
            textView.setTypeface(AndroidUtilities.bold());
            addView(textView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.CENTER_VERTICAL, 12, 0, 12, 0));
            ScaleStateListAnimator.apply(textView);

            counterText = new AnimatedTextView.AnimatedTextDrawable();
            counterText.setTextSize(dp(11));
            counterText.setTypeface(AndroidUtilities.bold());
            counterText.setOverrideFullWidth(AndroidUtilities.displaySize.x);
            counterText.setGravity(Gravity.CENTER);
            counterView = new View(context) {
                private final AnimatedPaint backgroundPaint = new AnimatedPaint(this, resourcesProvider);
                {
                    counterText.setCallback(this);
                }
                @Override
                protected boolean verifyDrawable(@NonNull Drawable who) {
                    return counterText == who || super.verifyDrawable(who);
                }

                @Override
                protected void dispatchDraw(@NonNull Canvas canvas) {
                    final float counterAlpha = counterText.isNotEmpty();
                    final float counterScale = lerp(.6f, 1.0f, counterAlpha);
                    final float width = Math.max(dp(16.66f), counterText.getCurrentWidth() + dp(10));
                    AndroidUtilities.rectTmp.set(0, 0, width, getHeight());
                    canvas.save();
                    canvas.scale(counterScale, counterScale, AndroidUtilities.rectTmp.centerX(), AndroidUtilities.rectTmp.centerY());
                    canvas.drawRoundRect(AndroidUtilities.rectTmp, dp(8.33f), dp(8.33f), backgroundPaint.setByKey(counterBackgroundColorKey).blendTo(getTextColor(), selectT).multAlpha(counterAlpha));
                    canvas.translate(0, -dp(1));
                    counterText.setBounds(AndroidUtilities.rectTmp);
                    counterText.setAlpha((int) (0xFF * counterAlpha));
                    counterText.setTextColor(Theme.getColor(Theme.key_chats_unreadCounterText, resourcesProvider));
                    counterText.draw(canvas);
                    canvas.restore();
                    super.dispatchDraw(canvas);
                }

                @Override
                protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                    final float width = Math.max(dp(16.66f), counterText.getAnimateToWidth() + dp(10));
                    super.onMeasure(
                        MeasureSpec.makeMeasureSpec((int) width, MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(dp(16.66f), MeasureSpec.EXACTLY)
                    );
                }
            };
            addView(counterView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.RIGHT | Gravity.CENTER_VERTICAL, 4.66f, 0, 12, 0));
            ScaleStateListAnimator.apply(counterView);

            updateTextColor();
        }

        private boolean pinned = false;
        private void setPinned(boolean pinned, boolean animated) {
            if (this.pinned != pinned) {
                this.pinned = pinned;
//                final int overlay = Theme.getColor(Theme.key_chats_pinnedOverlay, resourcesProvider);
//                if (Theme.isCurrentThemeDark()) {
//                    setBackgroundColor(pinned ? overlay : 0);
//                } else {
//                    setBackgroundColor(pinned ? Theme.blendOver(Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider), overlay) : 0);
//                }
            }
        }

        int counterViewX;
        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            final int w = right - left;
            final int h = bottom - top;
            textView.layout(dp(12), h / 2 - textView.getMeasuredHeight() / 2, dp(12) + textView.getMeasuredWidth(), h / 2 + textView.getMeasuredHeight() / 2);
            if (counterText.getAnimateToWidth() > 0) {
                counterView.layout(w - dp(12) - counterView.getMeasuredWidth(), h / 2 - counterView.getMeasuredHeight() / 2, w - dp(12), h / 2 + counterView.getMeasuredHeight() / 2);
            } else {
                counterView.layout(dp(12) + textView.getMeasuredWidth() + dp(4.66f), h / 2 - counterView.getMeasuredHeight() / 2, dp(12) + textView.getMeasuredWidth() + dp(4.66f) + counterView.getMeasuredWidth(), h / 2 + counterView.getMeasuredHeight() / 2);
            }
            if (counterViewX != 0 && counterView.getLeft() != counterViewX) {
                counterView.setTranslationX(-counterView.getLeft() + counterViewX);
                counterView.animate().translationX(0f).setDuration(320).setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT).start();
            }
            counterViewX = counterView.getLeft();
        }

        private long topicId;
        private boolean mono = false;
        private void setLayout(boolean mono) {
            if (this.mono == mono) return;
            this.mono = mono;
//            imageView.setRoundRadius(dp(mono ? 36 : 0));
//            imageViewParams.topMargin = mono ? dp(7) : dp(4);
//            imageViewParams.width = mono ? dp(28) : dp(36);
//            imageViewParams.height = mono ? dp(28) : dp(36);
        }

        public long getTopicId() {
            return topicId;
        }

        private boolean staticImage = false;
        public void setAll(boolean mono, boolean selected) {
            setLayout(mono);
            this.topicId = 0;
            this.staticImage = true;
            textView.setText(getString(R.string.AllTopicsShort));
            setSelected(selected);
            updateTextColor();
            setCounter(true, 0, false, false, false);
            setPinned(false, false);
        }

        public void setLoading() {
            setLayout(false);
            this.topicId = -1;
            this.staticImage = true;
            final SpannableStringBuilder sb = new SpannableStringBuilder("x");
            final LoadingSpan span = new LoadingSpan(textView, dp(42));
            span.setScaleY(.95f);
            sb.setSpan(span, 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            textView.setText(sb);
            setSelected(false);
            updateTextColor();
            setCounter(true, 0, false, false, false);
            setPinned(false, false);
        }

        public void set(long dialogId, TLRPC.TL_forumTopic topic, boolean selected) {
            setLayout(false);
            final boolean animated = this.topicId == topic.id;
            this.topicId = topic.id;
            this.staticImage = false;
            SpannableStringBuilder sb = new SpannableStringBuilder();
            if (topic.id == 1) {
                sb.append("#");
                sb.append(topic.hidden ? "\u200B" : " ");
                final ColoredImageSpan span = new ColoredImageSpan(R.drawable.msg_filled_general);
                span.setScale(.66f, .66f);
                sb.setSpan(span, 0, 1, Spanned.SPAN_INCLUSIVE_INCLUSIVE);
            } else if (topic.icon_emoji_id != 0) {
                sb.append("x ");
                sb.setSpan(new AnimatedEmojiSpan(topic.icon_emoji_id, textView.getPaint().getFontMetricsInt()), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            if (!topic.hidden) {
                sb.append(topic.title);
            }
            textView.setText(sb);
            setSelected(selected);
            updateTextColor();
            setCounter(
                MessagesController.getInstance(currentAccount).isDialogMuted(dialogId, topicId),
                topic.unread_count,
                false, false,
                animated
            );
            setPinned(topic.pinned, animated);
        }

        private void updateTextColor() {
            final int color = getTextColor();
            textView.setTextColor(color);
            textView.setEmojiColor(color);
            counterView.invalidate();
        }

        private int getTextColor() {
            return ColorUtils.blendARGB(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2, resourcesProvider), Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider), selectT);
        }

        private AvatarSpan avatarSpan;
        public void setMf(long chatDialogId, TLRPC.TL_forumTopic dialog, boolean selected) {
            setLayout(true);
            final long dialogId = DialogObject.getPeerDialogId(dialog.from_id);
            final boolean animated = this.topicId == dialogId;
            this.topicId = dialogId;
            this.staticImage = false;
            if (avatarSpan == null) {
                avatarSpan = new AvatarSpan(textView, currentAccount, 18);
                avatarSpan.usePaintAlpha = false;
            }
            SpannableStringBuilder sb = new SpannableStringBuilder();
            TLObject object = MessagesController.getInstance(currentAccount).getUserOrChat(dialogId);
            if (object != null) {
                sb.append("x  ");
                avatarSpan.setObject(object);
                sb.setSpan(avatarSpan, 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            sb.append(DialogObject.getName(dialogId));
            textView.setText(TextUtils.ellipsize(sb, textView.getPaint(), dp(150), TextUtils.TruncateAt.END));
            setSelected(selected);
            setCounter(
                MessagesController.getInstance(currentAccount).isDialogMuted(chatDialogId, dialogId),
                dialog.unread_count,
                false,
                false, // dialog.unread_reactions_count > 0,
                animated
            );
            setPinned(false, animated);
        }

        private float selectT;
        private boolean selected;
        private ValueAnimator selectAnimator;
        public void setSelected(boolean selected) {
            if (this.selected == selected) return;
            this.selected = selected;
            if (selectAnimator != null) {
                selectAnimator.cancel();
            }
            selectAnimator = ValueAnimator.ofFloat(selectT, selected ? 1f : 0f);
            selectAnimator.addUpdateListener(anm -> {
                selectT = (float) anm.getAnimatedValue();
                updateTextColor();
            });
            selectAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    selectT = selected ? 1f : 0f;
                    updateTextColor();
                }
            });
            selectAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
            selectAnimator.setDuration(320);
            selectAnimator.start();
        }


        private int counterBackgroundColorKey = Theme.key_chats_unreadCounter;
        private CharSequence mentionString;
        private CharSequence reactionString;
        private int lastUnread;
        private boolean lastMuted, lastMention, lastReactions;
        private void setCounter(boolean muted, int unread, boolean mention, boolean reactions, boolean animated) {
            if (reactions) {
                counterBackgroundColorKey = Theme.key_dialogReactionMentionBackground;
                if (reactionString == null) {
                    final SpannableStringBuilder sb = new SpannableStringBuilder("❤️");
                    final ColoredImageSpan span = new ColoredImageSpan(R.drawable.reactionchatslist);
                    span.setScale(0.8f, 0.8f);
                    span.spaceScaleX = 0.5f;
                    span.translate(-dp(3), 0);
                    sb.setSpan(span, 0, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    reactionString = sb;
                }
                counterText.setText(reactionString, animated);
            } else if (mention) {
                counterBackgroundColorKey = muted ? Theme.key_chats_unreadCounterMuted : Theme.key_chats_unreadCounter;
                if (mentionString == null) {
                    final SpannableStringBuilder sb = new SpannableStringBuilder("@");
                    final ColoredImageSpan span = new ColoredImageSpan(R.drawable.mentionchatslist);
                    span.setScale(0.8f, 0.8f);
                    span.spaceScaleX = 0.5f;
                    span.translate(-dp(3), 0);
                    sb.setSpan(span, 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    mentionString = sb;
                }
                counterText.setText(mentionString, animated);
            } else if (unread > 0) {
                counterBackgroundColorKey = muted ? Theme.key_chats_unreadCounterMuted : Theme.key_chats_unreadCounter;
                counterText.setText(LocaleController.formatNumber(unread, ','), animated);
            } else {
                counterBackgroundColorKey = Theme.key_chats_unreadCounterMuted;
                counterText.setText("", animated);
            }
            if (animated && (lastUnread < unread || !lastMention && mention || !lastReactions && reactions)) {
                animateCounterBounce();
            }
            lastUnread = unread;
            lastMention = mention;
            lastReactions = reactions;
            counterView.invalidate();
            if (getMeasuringWidth() != getMeasuredWidth()) {
                requestLayout();
            }
        }

        private ValueAnimator counterAnimator;
        private void animateCounterBounce() {
            if (counterAnimator != null) {
                counterAnimator.cancel();
                counterAnimator = null;
            }

            counterAnimator = ValueAnimator.ofFloat(0, 1);
            counterAnimator.addUpdateListener(anm -> {
                counterView.setScaleX(Math.max(1, (float) anm.getAnimatedValue()));
                counterView.setScaleY(Math.max(1, (float) anm.getAnimatedValue()));
                counterView.invalidate();
            });
            counterAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    counterView.setScaleX(1f);
                    counterView.setScaleY(1f);
                    counterView.invalidate();
                }
            });
            counterAnimator.setInterpolator(new OvershootInterpolator(2.0f));
            counterAnimator.setDuration(200);
            counterAnimator.start();
        }

        private int getMeasuringWidth() {
            final int counterWidth = (int) Math.max(dp(16.66f), counterText.getAnimateToWidth() + dp(10));
            return (int) (dp(12) + textView.getMeasuredWidth() + (counterText.getAnimateToWidth() > 0 ? dp(4.66f) + counterWidth : 0) + dp(12));
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            textView.measure(widthMeasureSpec, heightMeasureSpec);
            super.onMeasure(
                MeasureSpec.makeMeasureSpec(getMeasuringWidth(), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(dp(48), MeasureSpec.EXACTLY)
            );
        }

        public static class Factory extends UItem.UItemFactory<HorizontalTabView> {
            static { setup(new Factory()); }

            @Override
            public HorizontalTabView createView(Context context, int currentAccount, int classGuid, Theme.ResourcesProvider resourcesProvider) {
                return new HorizontalTabView(context, currentAccount, resourcesProvider);
            }

            @Override
            public void bindView(View view, UItem item, boolean divider, UniversalAdapter adapter, UniversalRecyclerView listView) {
                HorizontalTabView cell = (HorizontalTabView) view;
                if (item.red) {
                    cell.setLoading();
                } else if (item.object == null) {
                    cell.setAll(item.accent, item.checked);
                } else if (item.object instanceof TLRPC.TL_forumTopic) {
                    if (!item.withUsername) {
                        cell.setMf(item.dialogId, (TLRPC.TL_forumTopic) item.object, item.checked);
                    } else {
                        cell.set(item.dialogId, (TLRPC.TL_forumTopic) item.object, item.checked);
                    }
                }
                cell.setReorder(listView != null && listView.isReorderAllowed() && cell.pinned);
            }

            public static UItem asAll(boolean monoforum) {
                UItem item = UItem.ofFactory(Factory.class);
                item.id = 0;
                item.longValue = 0;
                item.object = null;
                item.accent = monoforum;
                return item;
            }

            public static UItem asTab(long dialogId, TLRPC.TL_forumTopic topic, boolean mono) {
                UItem item = UItem.ofFactory(Factory.class);
                item.dialogId = dialogId;
                item.id = topic.id;
                item.object = topic;
                if (mono) {
                    item.longValue = DialogObject.getPeerDialogId(topic.from_id);
                    item.withUsername = false;
                }
                return item;
            }

            public static UItem asLoading(int id) {
                UItem item = UItem.ofFactory(Factory.class);
                item.id = id;
                item.red = true;
                return item;
            }
        }
    }

    private final HashSet<Integer> excludeTopics = new HashSet<>();
    private void deleteTopics(HashSet<Integer> selectedTopics, Runnable runnable) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle(LocaleController.getPluralString("DeleteTopics", selectedTopics.size()));
        ArrayList<Integer> topicsToRemove = new ArrayList<>(selectedTopics);
        if (selectedTopics.size() == 1) {
            TLRPC.TL_forumTopic topic = MessagesController.getInstance(currentAccount).getTopicsController().findTopic(-dialogId, topicsToRemove.get(0));
            builder.setMessage(formatString(R.string.DeleteSelectedTopic, topic.title));
        } else {
            builder.setMessage(getString(R.string.DeleteSelectedTopics));
        }
        builder.setPositiveButton(LocaleController.getString(R.string.Delete), (dialog, which) -> {
            excludeTopics.addAll(selectedTopics);
            updateTabs();
            BulletinFactory.of(fragment).createUndoBulletin(LocaleController.getPluralString("TopicsDeleted", selectedTopics.size()), () -> {
                excludeTopics.removeAll(selectedTopics);
                updateTabs();
            }, () -> {
                MessagesController.getInstance(currentAccount).getTopicsController().deleteTopics(-dialogId, topicsToRemove);
                runnable.run();
            }).show();
            dialog.dismiss();
        });
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), (dialog, which) -> {
            dialog.dismiss();
        });
        AlertDialog alertDialog = builder.create();
        alertDialog.show();
        TextView button = (TextView) alertDialog.getButton(DialogInterface.BUTTON_POSITIVE);
        if (button != null) {
            button.setTextColor(Theme.getColor(Theme.key_text_RedBold));
        }
    }

    private long getTopicId(TLRPC.TL_forumTopic topic) {
        return mono ? DialogObject.getPeerDialogId(topic.from_id) : topic.id;
    }
}