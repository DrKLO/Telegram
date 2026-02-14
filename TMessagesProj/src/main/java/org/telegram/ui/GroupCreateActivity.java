/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.collection.LongSparseArray;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BotWebViewVibrationEffect;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BackDrawable;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Adapters.SearchAdapterHelper;
import org.telegram.ui.Cells.CheckBoxCell;
import org.telegram.ui.Cells.GraySectionCell;
import org.telegram.ui.Cells.GroupCreateSectionCell;
import org.telegram.ui.Cells.GroupCreateUserCell;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Components.Bulletin;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.ColoredImageSpan;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.FlickerLoadingView;
import org.telegram.ui.Components.FragmentFloatingButton;
import org.telegram.ui.Components.FragmentSearchField;
import org.telegram.ui.Components.GroupCreateSpan;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.PermanentLinkBottomSheet;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.StickerEmptyView;
import org.telegram.ui.Components.TypefaceSpan;
import org.telegram.ui.Components.blur3.DownscaleScrollableNoiseSuppressor;
import org.telegram.ui.Components.blur3.ViewGroupPartRenderer;
import org.telegram.ui.Components.blur3.capture.IBlur3Capture;
import org.telegram.ui.Components.blur3.source.BlurredBackgroundSourceRenderNode;
import org.telegram.ui.Components.inset.WindowAnimatedInsetsProvider;
import org.telegram.ui.Stories.recorder.ButtonWithCounterView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;

import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.animator.FactorAnimator;

public class GroupCreateActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate, FactorAnimator.Target, View.OnClickListener, WindowAnimatedInsetsProvider.Listener {
    private final int ADDITIONAL_LIST_HEIGHT_DP = Build.VERSION.SDK_INT >= 31 ? 48 : 0;

    private static final int ANIMATOR_ID_SELECTED_CONTAINER_HEIGHT = 3;
    private static final int ANIMATOR_ID_CALL_BUTTONS_VISIBLE = 4;

    private final FactorAnimator animatorSelectorContainerHeight = new FactorAnimator(ANIMATOR_ID_SELECTED_CONTAINER_HEIGHT,
        this, CubicBezierInterpolator.EASE_OUT_QUINT, 350);
    private final BoolAnimator animatorCallButtonsVisible = new BoolAnimator(ANIMATOR_ID_CALL_BUTTONS_VISIBLE,
            this, CubicBezierInterpolator.EASE_OUT_QUINT, 350);

    private View actionBarBackgroundView;
    private HeaderShadowView headerShadowView;
    private FragmentSearchField searchField;

    private ScrollView scrollView;
    private SpansContainer spansContainer;
    private RecyclerListView listView;
    private LinearLayoutManager layoutManager;
    private StickerEmptyView emptyView;
    private GroupCreateAdapter adapter;
    private GroupCreateActivityDelegate delegate;
    private ContactsAddActivityDelegate delegate2;
    private FragmentFloatingButton floatingButton;
    private boolean doneButtonVisible;
    private boolean ignoreScrollEvent;
    private FrameLayout buttonsContainer;

    private final long chatId;
    private final long channelId;
    private TLRPC.ChatFull info;

    private LongSparseArray<TLObject> ignoreUsers;

    private final int maxCount;
    private String customTitle;
    private final int chatType;
    private final boolean forImport;
    private final boolean isAlwaysShare;
    private final boolean isNeverShare;
    private final boolean isCall;
    private final boolean addToGroup;
    private boolean searchWas;
    private boolean searching;
    private final int chatAddType;
    private final boolean allowPremium;
    private final boolean allowMiniApps;
    private GroupCreateSpan selectedPremium;
    private GroupCreateSpan selectedMiniApps;
    private final LongSparseArray<GroupCreateSpan> selectedContacts = new LongSparseArray<>();
    private final ArrayList<GroupCreateSpan> allSpans = new ArrayList<>();
    private GroupCreateSpan currentDeletingSpan;

    public void setTitle(String title) {
        this.customTitle = title;
    }

    private int fieldY;

    int maxSize;

    private final static int done_button = 1;
    private PermanentLinkBottomSheet sharedLinkBottomSheet;

    public interface GroupCreateActivityDelegate {
        void didSelectUsers(boolean withPremium, boolean withMiniApps, ArrayList<Long> ids);
    }

    public interface ContactsAddActivityDelegate {
        void didSelectUsers(ArrayList<TLRPC.User> users, int fwdCount);

        default void needAddBot(TLRPC.User user) {

        }
    }

    private boolean showDiscardConfirm;
    public void setShowDiscardConfirm(boolean show) {
        this.showDiscardConfirm = show;
    }

    private final HashSet<Long> initialIds = new HashSet<>();
    private boolean initialPremium, initialMiniApps;

    private ArrayList<Long> toSelectIds;
    private boolean toSelectPremium;
    private boolean toSelectMiniApps;
    public void select(ArrayList<Long> ids, boolean premium, boolean miniApps) {
        initialIds.clear();
        initialIds.addAll(ids);
        initialPremium = premium;
        initialMiniApps = miniApps;
        if (spansContainer == null) {
            toSelectIds = ids;
            toSelectPremium = premium;
            toSelectMiniApps = miniApps;
            return;
        }
        if (premium && selectedPremium == null) {
            selectedPremium = new GroupCreateSpan(getContext(), "premium");
            spansContainer.addSpan(selectedPremium);
            selectedPremium.setOnClickListener(GroupCreateActivity.this);
        } else if (!premium && selectedPremium != null) {
            spansContainer.removeSpan(selectedPremium);
            selectedPremium = null;
        }
        if (miniApps && selectedMiniApps == null) {
            selectedMiniApps = new GroupCreateSpan(getContext(), "miniApps");
            spansContainer.addSpan(selectedMiniApps);
            selectedMiniApps.setOnClickListener(GroupCreateActivity.this);
        } else if (!miniApps && selectedMiniApps != null) {
            spansContainer.removeSpan(selectedMiniApps);
            selectedMiniApps = null;
        }
        for (long id : ids) {
            TLObject obj;
            if (id < 0) {
                obj = getMessagesController().getChat(-id);
            } else {
                obj = getMessagesController().getUser(id);
            }
            if (obj == null) continue;
            GroupCreateSpan span = new GroupCreateSpan(getContext(), obj);
            spansContainer.addSpan(span);
            span.setOnClickListener(this);
        }
        spansContainer.endAnimation();
        AndroidUtilities.updateVisibleRows(listView);
    }

    private class SpansContainer extends ViewGroup {

        private AnimatorSet currentAnimation;
        private boolean animationStarted;
        private final ArrayList<Animator> animators = new ArrayList<>();
        private View addingSpan;
        private final ArrayList<View> removingSpans = new ArrayList<>();
        private int animationIndex = -1;
        private int containerHeight;

        public SpansContainer(Context context) {
            super(context);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int count = getChildCount();
            int width = MeasureSpec.getSize(widthMeasureSpec);
            int maxWidth = width - dp(26);
            int currentLineWidth = 0;
            int y = dp(10);
            int allCurrentLineWidth = 0;
            int allY = dp(10);
            int maxTy = 0;
            int x;
            for (int a = 0; a < count; a++) {
                View child = getChildAt(a);
                if (!(child instanceof GroupCreateSpan)) {
                    continue;
                }
                child.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(dp(32), MeasureSpec.EXACTLY));
                boolean isRemoving = removingSpans.contains(child);
                if (!isRemoving && currentLineWidth + child.getMeasuredWidth() > maxWidth) {
                    y += child.getMeasuredHeight() + dp(8);
                    currentLineWidth = 0;
                }
                if (allCurrentLineWidth + child.getMeasuredWidth() > maxWidth) {
                    allY += child.getMeasuredHeight() + dp(8);
                    allCurrentLineWidth = 0;
                }
                x = dp(13) + currentLineWidth;
                if (!animationStarted) {
                    if (isRemoving) {
                        child.setTranslationX(dp(13) + allCurrentLineWidth);
                        child.setTranslationY(allY);
                    } else if (!removingSpans.isEmpty()) {
                        if (child.getTranslationX() != x) {
                            animators.add(ObjectAnimator.ofFloat(child, View.TRANSLATION_X, x));
                        }
                        if (child.getTranslationY() != y) {
                            animators.add(ObjectAnimator.ofFloat(child, View.TRANSLATION_Y, y));
                        }
                        maxTy = Math.max(maxTy, y);
                    } else {
                        child.setTranslationX(x);
                        child.setTranslationY(y);
                        maxTy = Math.max(maxTy, y);
                    }
                }
                if (!isRemoving) {
                    currentLineWidth += child.getMeasuredWidth() + dp(9);
                }
                allCurrentLineWidth += child.getMeasuredWidth() + dp(9);
            }
            int minWidth;
            if (AndroidUtilities.isTablet()) {
                minWidth = dp(530 - 26 - 18 - 57 * 2) / 3;
            } else {
                minWidth = (Math.min(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y) - dp(26 + 18 + 57 * 2)) / 3;
            }
            if (maxWidth - currentLineWidth < minWidth) {
                currentLineWidth = 0;
                y += dp(32 + 8);
            }
            if (maxWidth - allCurrentLineWidth < minWidth) {
                allY += dp(32 + 8);
            }
            if (!animationStarted) {
                int currentHeight = allY + dp(32 + 10);
                fieldY = y;
                if (currentAnimation != null) {
                    containerHeight = y + dp(32 + 10);
                    currentAnimation.playTogether(animators);
                    currentAnimation.addListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            getNotificationCenter().onAnimationFinish(animationIndex);
                            requestLayout();
                        }
                    });
                    animationIndex = getNotificationCenter().setAnimationInProgress(animationIndex, null);
                    currentAnimation.start();
                    animationStarted = true;
                } else {
                    containerHeight = currentHeight;
                }
            }
            animatorSelectorContainerHeight.animateTo(Math.min(maxTy > 0 ? maxTy + dp(40) : 0, maxSize));
            setMeasuredDimension(width, containerHeight);
        }

        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            for (int a = 0, N = getChildCount(); a < N; a++) {
                final View child = getChildAt(a);
                child.layout(0, 0, child.getMeasuredWidth(), child.getMeasuredHeight());
            }
        }

        public void addSpan(final GroupCreateSpan span) {
            allSpans.add(span);
            if (!span.isFlag) {
                selectedContacts.put(span.getUid(), span);
            }

            if (currentAnimation != null && currentAnimation.isRunning()) {
                currentAnimation.setupEndValues();
                currentAnimation.cancel();
            }
            animationStarted = false;
            currentAnimation = new AnimatorSet();
            currentAnimation.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animator) {
                    addingSpan = null;
                    currentAnimation = null;
                    animationStarted = false;
                }
            });
            currentAnimation.setDuration(150);
            addingSpan = span;
            animators.clear();
            animators.add(ObjectAnimator.ofFloat(addingSpan, View.SCALE_X, 0.01f, 1.0f));
            animators.add(ObjectAnimator.ofFloat(addingSpan, View.SCALE_Y, 0.01f, 1.0f));
            animators.add(ObjectAnimator.ofFloat(addingSpan, View.ALPHA, 0.0f, 1.0f));
            addView(span);

            updateButtonsVisibility();
        }

        public void endAnimation() {
            if (currentAnimation != null && currentAnimation.isRunning()) {
                currentAnimation.setupEndValues();
                currentAnimation.cancel();
            }
        }

        public void removeSpan(final GroupCreateSpan span) {
            ignoreScrollEvent = true;
            if (!span.isFlag) {
                selectedContacts.remove(span.getUid());
            }
            if (span == selectedPremium) {
                selectedPremium = null;
            }
            if (span == selectedMiniApps) {
                selectedMiniApps = null;
            }
            allSpans.remove(span);
            span.setOnClickListener(null);

            if (currentAnimation != null) {
                currentAnimation.setupEndValues();
                currentAnimation.cancel();
            }
            animationStarted = false;
            currentAnimation = new AnimatorSet();
            currentAnimation.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animator) {
                    removeView(span);
                    removingSpans.clear();
                    currentAnimation = null;
                    animationStarted = false;
                }
            });
            currentAnimation.setDuration(150);
            removingSpans.clear();
            removingSpans.add(span);
            animators.clear();
            animators.add(ObjectAnimator.ofFloat(span, View.SCALE_X, 1.0f, 0.01f));
            animators.add(ObjectAnimator.ofFloat(span, View.SCALE_Y, 1.0f, 0.01f));
            animators.add(ObjectAnimator.ofFloat(span, View.ALPHA, 1.0f, 0.0f));
            requestLayout();

            updateButtonsVisibility();
        }

        public void removeAllSpans(boolean animated) {
            ignoreScrollEvent = true;

            ArrayList<GroupCreateSpan> spans = new ArrayList<>(allSpans);
            allSpans.clear();

            removingSpans.clear();
            removingSpans.addAll(spans);

            for (int i = 0; i < spans.size(); ++i) {
                spans.get(i).setOnClickListener(null);
            }

            endAnimation();
            if (animated) {
                animationStarted = false;
                currentAnimation = new AnimatorSet();
                currentAnimation.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animator) {
                        for (int i = 0; i < spans.size(); ++i) {
                            removeView(spans.get(i));
                        }
                        removingSpans.clear();
                        currentAnimation = null;
                        animationStarted = false;
                    }
                });
                animators.clear();
                for (int i = 0; i < spans.size(); ++i) {
                    GroupCreateSpan span = spans.get(i);
                    animators.add(ObjectAnimator.ofFloat(span, View.SCALE_X, 1.0f, 0.01f));
                    animators.add(ObjectAnimator.ofFloat(span, View.SCALE_Y, 1.0f, 0.01f));
                    animators.add(ObjectAnimator.ofFloat(span, View.ALPHA, 1.0f, 0.0f));
                }
            } else {
                for (int i = 0; i < spans.size(); ++i) {
                    removeView(spans.get(i));
                }
                removingSpans.clear();
                currentAnimation = null;
                animationStarted = false;
            }
            requestLayout();

            updateButtonsVisibility();
        }
    }

    public GroupCreateActivity(Bundle args) {
        super(args);
        chatType = args.getInt("chatType", ChatObject.CHAT_TYPE_CHAT);
        forImport = args.getBoolean("forImport", false);
        isAlwaysShare = args.getBoolean("isAlwaysShare", false);
        isNeverShare = args.getBoolean("isNeverShare", false);
        isCall = args.getBoolean("isCall", false);
        addToGroup = args.getBoolean("addToGroup", false);
        chatAddType = args.getInt("chatAddType", 0);
        allowPremium = args.getBoolean("allowPremium", false);
        allowMiniApps = args.getBoolean("allowMiniapps", false);
        chatId = args.getLong("chatId");
        channelId = args.getLong("channelId");
        if (isAlwaysShare || isNeverShare || addToGroup) {
            maxCount = 0;
        } else if (isCall) {
            maxCount = getMessagesController().conferenceCallSizeLimit - 1;
        } else {
            maxCount = chatType == ChatObject.CHAT_TYPE_CHAT ? getMessagesController().maxMegagroupCount : getMessagesController().maxBroadcastCount;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            scrollableViewNoiseSuppressor = new DownscaleScrollableNoiseSuppressor();
            iBlur3SourceGlassFrosted = new BlurredBackgroundSourceRenderNode(null);
        } else {
            scrollableViewNoiseSuppressor = null;
            iBlur3SourceGlassFrosted = null;
        }
    }

    @Override
    public boolean onFragmentCreate() {
        getNotificationCenter().addObserver(this, NotificationCenter.contactsDidLoad);
        getNotificationCenter().addObserver(this, NotificationCenter.updateInterfaces);
        getNotificationCenter().addObserver(this, NotificationCenter.chatDidCreated);

        getUserConfig().loadGlobalTTl();
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        getNotificationCenter().removeObserver(this, NotificationCenter.contactsDidLoad);
        getNotificationCenter().removeObserver(this, NotificationCenter.updateInterfaces);
        getNotificationCenter().removeObserver(this, NotificationCenter.chatDidCreated);
    }

    @Override
    public void onClick(View v) {
        GroupCreateSpan span = (GroupCreateSpan) v;
        if (span.isDeleting()) {
            currentDeletingSpan = null;
            spansContainer.removeSpan(span);
            updateHint();
            checkVisibleRows();
        } else {
            if (currentDeletingSpan != null) {
                currentDeletingSpan.cancelDeleteAnimation();
            }
            currentDeletingSpan = span;
            span.startDeleteAnimation();
        }
    }

    @Override
    public View createView(Context context) {
        searching = false;
        searchWas = false;
        allSpans.clear();
        selectedContacts.clear();
        currentDeletingSpan = null;
        if (chatType == ChatObject.CHAT_TYPE_CHANNEL) {
            doneButtonVisible = true;
        } else {
            doneButtonVisible = !addToGroup;
        }

        actionBar.setBackgroundColor(0);
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        if (!TextUtils.isEmpty(customTitle)) {
            actionBar.setTitle(customTitle);
        } else if (chatType == ChatObject.CHAT_TYPE_CHANNEL) {
            actionBar.setTitle(getString(R.string.ChannelAddSubscribers));
        } else if (isCall) {
            actionBar.setTitle(getString(R.string.NewCall));
        } else if (addToGroup) {
            if (channelId != 0) {
                actionBar.setTitle(getString(R.string.ChannelAddSubscribers));
            } else {
                actionBar.setTitle(getString(R.string.GroupAddMembers));
            }
        } else if (isAlwaysShare) {
            if (chatAddType == 2) {
                actionBar.setTitle(getString(R.string.FilterAlwaysShow));
            } else if (chatAddType == 1) {
                actionBar.setTitle(getString(R.string.AlwaysAllow));
            } else {
                actionBar.setTitle(getString(R.string.AlwaysShareWithTitle));
            }
        } else if (isNeverShare) {
            if (chatAddType == 2) {
                actionBar.setTitle(getString(R.string.FilterNeverShow));
            } else if (chatAddType == 1) {
                actionBar.setTitle(getString(R.string.NeverAllow));
            } else {
                actionBar.setTitle(getString(R.string.NeverShareWithTitle));
            }
        } else {
            actionBar.setTitle(chatType == ChatObject.CHAT_TYPE_CHAT ? getString(R.string.NewGroup) : getString(R.string.NewBroadcastList));
        }

        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    if (checkDiscard(true)) {
                        finishFragment();
                    }
                } else if (id == done_button) {
                    onDonePressed(true);
                }
            }
        });

        searchField = new FragmentSearchField(context, resourceProvider);
        FrameLayout contentView;
        fragmentView = contentView = new FrameLayout(context) {
            @Override
            protected void dispatchDraw(@NonNull Canvas canvas) {
                if (Build.VERSION.SDK_INT >= 31 && scrollableViewNoiseSuppressor != null) {
                    blur3_InvalidateBlur();
                    final int width = getMeasuredWidth();
                    final int height = getMeasuredHeight();
                    if (iBlur3SourceGlassFrosted != null && !iBlur3SourceGlassFrosted.inRecording()) {
                        if (iBlur3SourceGlassFrosted.needUpdateDisplayList(width, height) || iBlur3Invalidated) {
                            final Canvas c = iBlur3SourceGlassFrosted.beginRecording(width, height);
                            scrollableViewNoiseSuppressor.draw(c, DownscaleScrollableNoiseSuppressor.DRAW_FROSTED_GLASS);
                            iBlur3SourceGlassFrosted.endRecording();
                        }
                    }
                    iBlur3Invalidated = false;
                }

                super.dispatchDraw(canvas);
                AndroidUtilities.drawNavigationBarProtection(canvas, this, getThemedColor(Theme.key_windowBackgroundWhite), navigationBarHeight);
            }

            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                final int width = MeasureSpec.getSize(widthMeasureSpec);
                final int height = MeasureSpec.getSize(heightMeasureSpec);
                if (AndroidUtilities.isTablet() || height > width) {
                    maxSize = dp(144);
                } else {
                    maxSize = dp(56);
                }

                measureChildWithMargins(actionBar, widthMeasureSpec, 0, heightMeasureSpec, 0);
                ((MarginLayoutParams) emptyView.getLayoutParams()).topMargin = actionBar.getMeasuredHeight() + dp(DialogsActivity.SEARCH_FIELD_HEIGHT);
                ((MarginLayoutParams) headerShadowView.getLayoutParams()).topMargin = actionBar.getMeasuredHeight();
                ((MarginLayoutParams) searchField.getLayoutParams()).topMargin = actionBar.getMeasuredHeight();
                ((MarginLayoutParams) scrollView.getLayoutParams()).topMargin = actionBar.getMeasuredHeight();
                scrollView.getLayoutParams().height = maxSize;

                MarginLayoutParams lp = (MarginLayoutParams) actionBarBackgroundView.getLayoutParams();
                lp.height = actionBar.getMeasuredHeight() + dp(DialogsActivity.SEARCH_FIELD_HEIGHT + 5) + maxSize;

                checkUi_listViewPadding();
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            }

            @Override
            protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
                super.onLayout(changed, left, top, right, bottom);

                checkUi_bottomButtons();
                checkUi_floatingButton();
                checkUi_searchFieldY();
                checkUi_listClip();
                checkUi_headerShadowY();
            }
        };
        contentView.setFocusableInTouchMode(true);
        contentView.setDescendantFocusability(ViewGroup.FOCUS_BEFORE_DESCENDANTS);

        scrollView = new ScrollView(context) {
            @Override
            public boolean requestChildRectangleOnScreen(View child, Rect rectangle, boolean immediate) {
                if (ignoreScrollEvent) {
                    ignoreScrollEvent = false;
                    return false;
                }
                rectangle.offset(child.getLeft() - child.getScrollX(), child.getTop() - child.getScrollY());
                rectangle.top += fieldY + dp(20);
                rectangle.bottom += fieldY + dp(50);
                return super.requestChildRectangleOnScreen(child, rectangle, immediate);
            }

            @Override
            public boolean dispatchTouchEvent(MotionEvent ev) {
                final int action = ev.getAction();
                final float h = animatorSelectorContainerHeight.getFactor();
                final float y = ev.getY();
                if (action == MotionEvent.ACTION_DOWN && y > h) {
                    return false;
                }
                return super.dispatchTouchEvent(ev);
            }
        };
        scrollView.setVerticalScrollBarEnabled(false);

        spansContainer = new SpansContainer(context);
        scrollView.addView(spansContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        spansContainer.setOnClickListener(v -> {
            searchField.editText.clearFocus();
            searchField.editText.requestFocus();
            AndroidUtilities.showKeyboard(searchField.editText);
        });

        updateEditTextHint();
        searchField.editText.setOnEditorActionListener((v, actionId, event) -> actionId == EditorInfo.IME_ACTION_DONE && onDonePressed(true));
        searchField.editText.setOnKeyListener(new View.OnKeyListener() {

            private boolean wasEmpty;

            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (keyCode == KeyEvent.KEYCODE_DEL) {
                    if (event.getAction() == KeyEvent.ACTION_DOWN) {
                        wasEmpty = searchField.editText.length() == 0;
                    } else if (event.getAction() == KeyEvent.ACTION_UP && wasEmpty && !allSpans.isEmpty()){
                        spansContainer.removeSpan(allSpans.get(allSpans.size() - 1));
                        updateHint();
                        checkVisibleRows();
                        return true;
                    }
                }
                return false;
            }
        });
        searchField.editText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i2, int i3) {

            }

            @Override
            public void afterTextChanged(Editable editable) {
                if (searchField.editText.length() != 0) {
                    if (!adapter.searching) {
                        searching = true;
                        searchWas = true;
                        adapter.setSearching(true);
                        listView.setFastScrollVisible(false);
                        listView.setVerticalScrollBarEnabled(true);
                    }
                    adapter.searchDialogs(searchField.editText.getText().toString());
                    emptyView.showProgress(true, false);
                } else {
                    closeSearch();
                }
            }
        });

        if (toSelectIds != null) {
            select(toSelectIds, toSelectPremium, toSelectMiniApps);
        }

        FlickerLoadingView flickerLoadingView = new FlickerLoadingView(context);
        flickerLoadingView.setViewType(FlickerLoadingView.USERS_TYPE);
        flickerLoadingView.showDate(false);

        emptyView = new StickerEmptyView(context, flickerLoadingView, StickerEmptyView.STICKER_TYPE_SEARCH);
        emptyView.addView(flickerLoadingView);
        emptyView.showProgress(true, false);
        emptyView.title.setText(getString(R.string.NoResult));

        contentView.addView(emptyView);

        layoutManager = new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false);
        listView = new RecyclerListView(context);
        listView.setFastScrollEnabled(RecyclerListView.FastScroll.LETTER_TYPE);
        listView.setEmptyView(emptyView);
        listView.setAdapter(adapter = new GroupCreateAdapter(context));
        listView.setLayoutManager(layoutManager);
        listView.setVerticalScrollBarEnabled(false);
        listView.setClipToPadding(false);
        listView.setVerticalScrollbarPosition(LocaleController.isRTL ? View.SCROLLBAR_POSITION_LEFT : View.SCROLLBAR_POSITION_RIGHT);
        contentView.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL, 0, -ADDITIONAL_LIST_HEIGHT_DP, 0,  -ADDITIONAL_LIST_HEIGHT_DP));
        listView.setOnItemClickListener((view, position) -> {
            if (position == adapter.createCallLinkRow) {
                CallLogActivity.createCallLink(context, currentAccount, resourceProvider, this::finishFragment);
            } else if (position == 0 && adapter.inviteViaLink != 0 && !adapter.searching) {
                sharedLinkBottomSheet = new PermanentLinkBottomSheet(context, false, this, info, chatId, channelId != 0);
                showDialog(sharedLinkBottomSheet);
            } else if (view instanceof GroupCreateUserCell) {
                GroupCreateUserCell cell = (GroupCreateUserCell) view;
                if (cell.currentPremium) {
                    if (selectedPremium == null) {
                        selectedPremium = new GroupCreateSpan(searchField.editText.getContext(), "premium");
                        spansContainer.addSpan(selectedPremium);
                        selectedPremium.setOnClickListener(GroupCreateActivity.this);
                    } else {
                        spansContainer.removeSpan(selectedPremium);
                        selectedPremium = null;
                    }
                    checkVisibleRows();
                    return;
                }
                if (cell.currentMiniapps) {
                    if (selectedMiniApps == null) {
                        selectedMiniApps = new GroupCreateSpan(searchField.editText.getContext(), "miniapps");
                        spansContainer.addSpan(selectedMiniApps);
                        selectedMiniApps.setOnClickListener(GroupCreateActivity.this);
                    } else {
                        spansContainer.removeSpan(selectedMiniApps);
                        selectedMiniApps = null;
                    }
                    checkVisibleRows();
                    return;
                }
                Object object = cell.getObject();
                long id;
                if (object instanceof TLRPC.User) {
                    id = ((TLRPC.User) object).id;
                } else if (object instanceof TLRPC.Chat) {
                    id = -((TLRPC.Chat) object).id;
                } else {
                    return;
                }
                if (ignoreUsers != null && ignoreUsers.indexOfKey(id) >= 0) {
                    return;
                }
                if (cell.isBlocked()) {
                    showPremiumBlockedToast(cell, id);
                    return;
                }
                final GroupCreateSpan selectedSpan = selectedContacts.get(id);
                if (selectedSpan != null) {
                    spansContainer.removeSpan(selectedSpan);
                } else {
                    if (maxCount != 0 && selectedContacts.size() == maxCount) {
                        return;
                    }
                    if (chatType == ChatObject.CHAT_TYPE_CHAT && selectedContacts.size() == getMessagesController().maxGroupCount) {
                        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                        builder.setTitle(getString(R.string.AppName));
                        builder.setMessage(getString(R.string.SoftUserLimitAlert));
                        builder.setPositiveButton(getString(R.string.OK), null);
                        showDialog(builder.create());
                        return;
                    }
                    if (object instanceof TLRPC.User) {
                        TLRPC.User user = (TLRPC.User) object;
                        if (addToGroup && user.bot) {
                            if (channelId == 0 && user.bot_nochats) {
                                try {
                                    BulletinFactory.of(this).createErrorBulletin(getString(R.string.BotCantJoinGroups)).show();
                                } catch (Exception e) {
                                    FileLog.e(e);
                                }
                                return;
                            }
                            if (channelId != 0) {
                                TLRPC.Chat chat = getMessagesController().getChat(channelId);
                                AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                                if (ChatObject.canAddAdmins(chat)) {
                                    builder.setTitle(getString(R.string.AddBotAdminAlert));
                                    builder.setMessage(getString(R.string.AddBotAsAdmin));
                                    builder.setPositiveButton(getString(R.string.AddAsAdmin), (dialogInterface, i) -> {
                                        delegate2.needAddBot(user);
                                        if (searchField.editText.length() > 0) {
                                            searchField.editText.setText(null);
                                        }
                                    });
                                    builder.setNegativeButton(getString(R.string.Cancel), null);
                                } else {
                                    builder.setMessage(getString(R.string.CantAddBotAsAdmin));
                                    builder.setPositiveButton(getString(R.string.OK), null);
                                }
                                showDialog(builder.create());
                                return;
                            }
                        }
                        getMessagesController().putUser(user, !searching);
                    } else if (object instanceof TLRPC.Chat) {
                        TLRPC.Chat chat = (TLRPC.Chat) object;
                        getMessagesController().putChat(chat, !searching);
                    }
                    GroupCreateSpan span = new GroupCreateSpan(searchField.editText.getContext(), object);
                    spansContainer.addSpan(span);
                    span.setOnClickListener(GroupCreateActivity.this);
                }
                updateHint();
                if (searching || searchWas) {
                    AndroidUtilities.showKeyboard(searchField.editText);
                } else {
                    checkVisibleRows();
                }
                if (searchField.editText.length() > 0) {
                    searchField.editText.setText(null);
                }
            }
        });
        listView.setOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                final int firstVisibleItem = layoutManager.findFirstVisibleItemPosition();
                final View topChild = listView.getChildAt(0);
                final int firstViewTop = topChild != null ? topChild.getTop() : 0;

                final boolean shadowVisible = !(firstVisibleItem == 0 && firstViewTop >= listView.getPaddingTop());
                headerShadowView.setShadowVisible(shadowVisible, true);

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && scrollableViewNoiseSuppressor != null) {
                    scrollableViewNoiseSuppressor.onScrolled(dx, dy);
                    blur3_InvalidateBlur();
                }
            }

            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                    searchField.editText.hideActionMode();
                    AndroidUtilities.hideKeyboard(searchField.editText);
                }
            }
        });
        listView.setAnimateEmptyView(true, RecyclerListView.EMPTY_VIEW_ANIMATION_TYPE_ALPHA);

        floatingButton = new FragmentFloatingButton(context, resourceProvider);
        if (isNeverShare || isAlwaysShare || addToGroup) {
            floatingButton.imageView.setImageResource(R.drawable.floating_check);
        } else {
            BackDrawable backDrawable = new BackDrawable(false);
            backDrawable.setArrowRotation(180);
            floatingButton.imageView.setImageDrawable(backDrawable);
        }
        if (!isCall) {
            contentView.addView(floatingButton, FragmentFloatingButton.createDefaultLayoutParams());
        }
        floatingButton.setOnClickListener(v -> onDonePressed(true));
        floatingButton.setButtonVisible(doneButtonVisible, false);
        floatingButton.setContentDescription(getString(R.string.Next));

        if (isCall) {
            buttonsContainer = new FrameLayout(context) {
                private final RectF rectTmp = new RectF();
                private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

                @Override
                protected void dispatchDraw(@NonNull Canvas canvas) {
                    paint.setColor(getThemedColor(Theme.key_windowBackgroundWhite));
                    canvas.save();
                    canvas.translate(0, -getTop());
                    rectTmp.set(0, 0, getWidth(), getHeight());
                    rectTmp.offset(0, getTop());
                    drawBlurRect(canvas, rectTmp, paint);
                    canvas.restore();
                    super.dispatchDraw(canvas);
                }
            };
            View buttonShadow = new View(context);
            buttonShadow.setBackgroundColor(Theme.getColor(Theme.key_divider, resourceProvider));
            buttonsContainer.addView(buttonShadow, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 1f / AndroidUtilities.density, Gravity.TOP | Gravity.FILL_HORIZONTAL, 0, 0, 0, 0));

            LinearLayout buttonsLayout = new LinearLayout(context);
            buttonsLayout.setOrientation(LinearLayout.HORIZONTAL);
            buttonsLayout.setPadding(dp(14), dp(14), dp(14), dp(14));
            buttonsContainer.addView(buttonsLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM | Gravity.FILL_HORIZONTAL));

            ButtonWithCounterView voiceButton = new ButtonWithCounterView(context, resourceProvider);
            voiceButton.setRound();
            SpannableStringBuilder sb = new SpannableStringBuilder();
            sb.append("x  ");
            sb.setSpan(new ColoredImageSpan(R.drawable.profile_phone), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            sb.append(getString(R.string.GroupCallCreateVoice));
            voiceButton.setText(sb, false);
            buttonsLayout.addView(voiceButton, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, 1, Gravity.FILL, 0, 0, 6, 0));
            voiceButton.setOnClickListener(v -> onCallUsersSelected(getSelectedUsers(), false));

            ButtonWithCounterView videoButton = new ButtonWithCounterView(context, resourceProvider);
            videoButton.setRound();
            sb = new SpannableStringBuilder();
            sb.append("x  ");
            sb.setSpan(new ColoredImageSpan(R.drawable.profile_video), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            sb.append(getString(R.string.GroupCallCreateVideo));
            videoButton.setText(sb, false);
            buttonsLayout.addView(videoButton, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, 1, Gravity.FILL, 6, 0, 0, 0));
            videoButton.setOnClickListener(v -> onCallUsersSelected(getSelectedUsers(), false));

            contentView.addView(buttonsContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM | Gravity.FILL_HORIZONTAL));
            checkUi_bottomButtons();
        }

        updateHint();

        actionBarBackgroundView = new View(context) {
            private final RectF rectTmp = new RectF();
            private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

            @Override
            protected void dispatchDraw(@NonNull Canvas canvas) {
                super.dispatchDraw(canvas);
                final int searchH = dp(DialogsActivity.SEARCH_FIELD_HEIGHT) + (int) (animatorSelectorContainerHeight.getFactor());

                paint.setColor(getThemedColor(Theme.key_actionBarDefault));
                rectTmp.set(0, 0, getMeasuredWidth(), actionBar.getMeasuredHeight() + searchH);
                drawBlurRect(canvas, rectTmp, paint);
            }
        };
        contentView.addView(actionBarBackgroundView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 0, Gravity.TOP));
        contentView.addView(actionBar);
        contentView.addView(searchField, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 40, Gravity.TOP, 11, 0, 11, 0));
        contentView.addView(scrollView);

        iBlur3Capture = new ViewGroupPartRenderer(listView, contentView, listView::drawChild);
        listView.addEdgeEffectListener(() -> listView.postOnAnimation(() -> {
            checkUi_listClip();
            blur3_InvalidateBlur();
        }));

        headerShadowView = new HeaderShadowView(context, parentLayout);
        headerShadowView.setShadowVisible(false, false);
        contentView.addView(headerShadowView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 5, Gravity.TOP));

        if (LaunchActivity.instance != null) {
            LaunchActivity.instance.getRootAnimatedInsetsListener().subscribeToWindowInsetsAnimation(this);
        }
        ViewCompat.setOnApplyWindowInsetsListener(fragmentView, this::onApplyWindowInsets);
        return fragmentView;
    }

    private void drawBlurRect(Canvas canvas, RectF rect, Paint paint) {
        canvas.drawRect(rect, paint);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || !SharedConfig.chatBlurEnabled() || iBlur3SourceGlassFrosted == null) {
            return;
        }

        iBlur3SourceGlassFrosted.draw(canvas, rect.left, rect.top, rect.right, rect.bottom);
        final int oldScrimAlpha = paint.getAlpha();
        paint.setAlpha(ChatActivity.ACTION_BAR_BLUR_ALPHA);
        canvas.drawRect(rect, paint);
        paint.setAlpha(oldScrimAlpha);
    }

    @Override
    public ActionBar createActionBar(Context context) {
        ActionBar actionBar = super.createActionBar(context);
        actionBar.setAddToContainer(false);
        return actionBar;
    }

    private void updateButtonsVisibility() {
        if (buttonsContainer == null) return;
        final boolean show = !selectedContacts.isEmpty();
        animatorCallButtonsVisible.setValue(show, true);
    }

    private int shiftDp = -4;
    private void showPremiumBlockedToast(View view, long dialogId) {
        AndroidUtilities.shakeViewSpring(view, shiftDp = -shiftDp);
        BotWebViewVibrationEffect.APP_ERROR.vibrate();
        String username = "";
        if (dialogId >= 0) {
            username = UserObject.getUserName(MessagesController.getInstance(currentAccount).getUser(dialogId));
        }
        Bulletin bulletin;
        if (MessagesController.getInstance(currentAccount).premiumFeaturesBlocked()) {
            bulletin = BulletinFactory.of(this).createSimpleBulletin(R.raw.star_premium_2, AndroidUtilities.replaceTags(LocaleController.formatString(R.string.UserBlockedNonPremium, username)));
        } else {
            bulletin = BulletinFactory.of(this).createSimpleBulletin(R.raw.star_premium_2, AndroidUtilities.replaceTags(LocaleController.formatString(R.string.UserBlockedNonPremium, username)), getString(R.string.UserBlockedNonPremiumButton), () -> presentFragment(new PremiumPreviewFragment("noncontacts")));
        }
        bulletin.show();
    }

    private void updateEditTextHint() {
        if (searchField.editText == null) {
            return;
        }
        if (chatType == ChatObject.CHAT_TYPE_CHANNEL) {
            searchField.editText.setHint(getString(R.string.AddMutual));
        } else {
            if (addToGroup || (adapter != null && adapter.noContactsStubRow == 0)) {
                searchField.editText.setHint(getString(R.string.SearchForPeople));
            } else if (isAlwaysShare || isNeverShare) {
                searchField.editText.setHint(getString(R.string.SearchForPeopleAndGroups));
            } else if (isCall) {
                searchField.editText.setHint(getString(R.string.NewCallSearch));
            } else {
                searchField.editText.setHint(getString(R.string.SendMessageTo));
            }
        }
    }

    private void showItemsAnimated(int from) {
        if (isPaused) {
            return;
        }
        AndroidUtilities.doOnPreDraw(listView, () -> {
            final AnimatorSet animatorSet = new AnimatorSet();
            for (int i = 0, N = listView.getChildCount(); i < N; i++) {
                final View child = listView.getChildAt(i);
                if (listView.getChildAdapterPosition(child) < from) {
                    continue;
                }
                child.setAlpha(0);
                int s = Math.min(listView.getMeasuredHeight(), Math.max(0, child.getTop()));
                int delay = (int) ((s / (float) listView.getMeasuredHeight()) * 100);
                ObjectAnimator a = ObjectAnimator.ofFloat(child, View.ALPHA, 0, 1f);
                a.setStartDelay(delay);
                a.setDuration(200);
                animatorSet.playTogether(a);
            }
            animatorSet.start();
        });
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.contactsDidLoad) {
            if (adapter != null) {
                adapter.notifyDataSetChanged();
            }
        } else if (id == NotificationCenter.updateInterfaces) {
            if (listView != null) {
                int mask = (Integer) args[0];
                int count = listView.getChildCount();
                if ((mask & MessagesController.UPDATE_MASK_AVATAR) != 0 || (mask & MessagesController.UPDATE_MASK_NAME) != 0 || (mask & MessagesController.UPDATE_MASK_STATUS) != 0) {
                    for (int a = 0; a < count; a++) {
                        View child = listView.getChildAt(a);
                        if (child instanceof GroupCreateUserCell) {
                            ((GroupCreateUserCell) child).update(mask);
                        }
                    }
                }
            }
        } else if (id == NotificationCenter.chatDidCreated) {
            removeSelfFromStack();
        }
    }

    public void setIgnoreUsers(LongSparseArray<TLObject> users) {
        ignoreUsers = users;
    }

    public void setInfo(TLRPC.ChatFull chatFull) {
        info = chatFull;
    }

    private void checkVisibleRows() {
        int count = listView.getChildCount();
        for (int a = 0; a < count; a++) {
            View child = listView.getChildAt(a);
            if (child instanceof GroupCreateUserCell) {
                GroupCreateUserCell cell = (GroupCreateUserCell) child;
                Object object = cell.getObject();
                long id;
                if (object instanceof TLRPC.User) {
                    id = ((TLRPC.User) object).id;
                } else if (object instanceof TLRPC.Chat) {
                    id = -((TLRPC.Chat) object).id;
                } else if (object instanceof String && "premium".equalsIgnoreCase((String) object)) {
                    cell.setChecked(selectedPremium != null, true);
                    cell.setCheckBoxEnabled(true);
                    continue;
                } else if (object instanceof String && "miniapps".equalsIgnoreCase((String) object)) {
                    cell.setChecked(selectedMiniApps != null, true);
                    cell.setCheckBoxEnabled(true);
                    continue;
                } else {
                    id = 0;
                }
                if (id != 0) {
                    if (ignoreUsers != null && ignoreUsers.indexOfKey(id) >= 0) {
                        cell.setChecked(true, false);
                        cell.setCheckBoxEnabled(false);
                    } else {
                        cell.setChecked(selectedContacts.indexOfKey(id) >= 0, true);
                        cell.setCheckBoxEnabled(true);
                    }
                }
            } else if (child instanceof GraySectionCell) {
                int position = listView.getChildAdapterPosition(child);
                if (position == adapter.firstSectionRow) {
                    GraySectionCell cell = (GraySectionCell) child;
                    cell.setRightText(selectedPremium != null || !selectedContacts.isEmpty() ? getString(R.string.DeselectAll) : "", true, v -> {
                        selectedPremium = null;
                        selectedContacts.clear();
                        spansContainer.removeAllSpans(true);
                        checkVisibleRows();
                        updateEditTextHint();
                        updateHint();
                    });
                }
            }
        }
    }

    private void onAddToGroupDone(int count) {
        ArrayList<TLRPC.User> result = new ArrayList<>();
        for (int a = 0; a < selectedContacts.size(); a++) {
            TLRPC.User user = getMessagesController().getUser(selectedContacts.keyAt(a));
            result.add(user);
        }
        if (delegate2 != null) {
            delegate2.didSelectUsers(result, count);
        }
        finishFragment();
    }

    @Override
    public boolean canBeginSlide() {
        return checkDiscard(true);
    }

    @Override
    public boolean onBackPressed(boolean invoked) {
        if (!checkDiscard(invoked)) {
            return false;
        }
        return super.onBackPressed(invoked);
    }

    private boolean checkDiscard(boolean invoked) {
        if (!showDiscardConfirm)
            return true;
        final HashSet<Long> current = new HashSet<>();
        for (int a = 0; a < selectedContacts.size(); a++) {
            current.add(selectedContacts.keyAt(a));
        }
        boolean hasChanges = initialPremium == (selectedPremium == null) || initialMiniApps == (selectedMiniApps == null) || current.size() != initialIds.size();
        if (!hasChanges) {
            for (long id : current) {
                if (!initialIds.contains(id)) {
                    hasChanges = true;
                    break;
                }
            }
        }
        if (hasChanges) {
            if (invoked) {
                final AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                builder.setTitle(getString(R.string.UserRestrictionsApplyChanges));
                builder.setMessage(getString(R.string.PrivacySettingsChangedAlert));
                builder.setPositiveButton(getString(R.string.ApplyTheme), (dialogInterface, i) -> onDonePressed(true));
                builder.setNegativeButton(getString(R.string.PassportDiscard), (dialog, which) -> finishFragment());
                showDialog(builder.create());
            }
            return false;
        }
        return true;
    }

    private HashSet<Long> getSelectedUsers() {
        final HashSet<Long> set = new HashSet<>();
        for (int a = 0; a < selectedContacts.size(); a++) {
            set.add(selectedContacts.keyAt(a));
        }
        return set;
    }

    protected void onCallUsersSelected(HashSet<Long> users, boolean video) {

    }

    private boolean onDonePressed(boolean alert) {
        if (selectedContacts.isEmpty() && (chatType != ChatObject.CHAT_TYPE_CHANNEL && addToGroup)) {
            return false;
        }
        if (alert && addToGroup) {
            if (getParentActivity() == null) {
                return false;
            }
            AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
            builder.setTitle(LocaleController.formatPluralString("AddManyMembersAlertTitle", selectedContacts.size()));
            StringBuilder stringBuilder = new StringBuilder();
            for (int a = 0; a < selectedContacts.size(); a++) {
                long uid = selectedContacts.keyAt(a);
                TLRPC.User user = getMessagesController().getUser(uid);
                if (user == null) {
                    continue;
                }
                if (stringBuilder.length() > 0) {
                    stringBuilder.append(", ");
                }
                stringBuilder.append("**").append(ContactsController.formatName(user.first_name, user.last_name)).append("**");
            }
            TLRPC.Chat chat = getMessagesController().getChat(chatId != 0 ? chatId : channelId);
            if (selectedContacts.size() > 5) {
                SpannableStringBuilder spannableStringBuilder = new SpannableStringBuilder(AndroidUtilities.replaceTags(LocaleController.formatPluralString("AddManyMembersAlertNamesText", selectedContacts.size(), chat == null ? "" : chat.title)));
                String countString = String.format("%d", selectedContacts.size());
                int index = TextUtils.indexOf(spannableStringBuilder, countString);
                if (index >= 0) {
                    spannableStringBuilder.setSpan(new TypefaceSpan(AndroidUtilities.bold()), index, index + countString.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
                builder.setMessage(spannableStringBuilder);
            } else {
                builder.setMessage(AndroidUtilities.replaceTags(LocaleController.formatString(R.string.AddMembersAlertNamesText, stringBuilder, chat == null ? "" : chat.title)));
            }
            CheckBoxCell[] cells = new CheckBoxCell[1];
            if (!ChatObject.isChannel(chat)) {
                LinearLayout linearLayout = new LinearLayout(getParentActivity());
                linearLayout.setOrientation(LinearLayout.VERTICAL);
                cells[0] = new CheckBoxCell(getParentActivity(), 1, resourceProvider);
                cells[0].setBackground(Theme.getSelectorDrawable(false));
                cells[0].setMultiline(true);
                if (selectedContacts.size() == 1) {
                    TLRPC.User user = getMessagesController().getUser(selectedContacts.keyAt(0));
                    cells[0].setText(AndroidUtilities.replaceTags(LocaleController.formatString(R.string.AddOneMemberForwardMessages, UserObject.getFirstName(user))), "", true, false);
                } else {
                    cells[0].setText(getString(R.string.AddMembersForwardMessages), "", true, false);
                }
                cells[0].setPadding(LocaleController.isRTL ? dp(16) : dp(8), 0, LocaleController.isRTL ? dp(8) : dp(16), 0);
                linearLayout.addView(cells[0], LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
                cells[0].setOnClickListener(v -> cells[0].setChecked(!cells[0].isChecked(), true));

                builder.setView(linearLayout);
            }
            builder.setPositiveButton(getString(R.string.Add), (dialogInterface, i) -> onAddToGroupDone(cells[0] != null && cells[0].isChecked() ? 100 : 0));
            builder.setNegativeButton(getString(R.string.Cancel), null);
            showDialog(builder.create());
        } else {
            if (chatType == ChatObject.CHAT_TYPE_CHANNEL) {
                ArrayList<TLRPC.InputUser> result = new ArrayList<>();
                for (int a = 0; a < selectedContacts.size(); a++) {
                    TLRPC.InputUser user = getMessagesController().getInputUser(getMessagesController().getUser(selectedContacts.keyAt(a)));
                    if (user != null) {
                        result.add(user);
                    }
                }
                getMessagesController().addUsersToChannel(chatId, result, null);
                getNotificationCenter().postNotificationName(NotificationCenter.closeChats);
                Bundle args2 = new Bundle();
                args2.putLong("chat_id", chatId);
                args2.putBoolean("just_created_chat", true);
                presentFragment(new ChatActivity(args2), true);
            } else {
                if (!doneButtonVisible) {
                    return false;
                }
                if (addToGroup) {
                    onAddToGroupDone(0);
                } else {
                    ArrayList<Long> result = new ArrayList<>();
                    for (int a = 0; a < selectedContacts.size(); a++) {
                        result.add(selectedContacts.keyAt(a));
                    }
                    if (isAlwaysShare || isNeverShare) {
                        if (delegate != null) {
                            delegate.didSelectUsers(selectedPremium != null, selectedMiniApps != null, result);
                        }
                        finishFragment();
                    } else {
                        Bundle args = new Bundle();

                        long[] array = new long[result.size()];
                        for (int a = 0; a < array.length; a++) {
                            array[a] = result.get(a);
                        }
                        args.putLongArray("result", array);
                        args.putInt("chatType", chatType);
                        args.putBoolean("forImport", forImport);
                        presentFragment(new GroupCreateFinalActivity(args));
                    }
                }
            }
        }
        return true;
    }

    private void closeSearch() {
        searching = false;
        searchWas = false;
        adapter.setSearching(false);
        adapter.searchDialogs(null);
        listView.setFastScrollVisible(true);
        listView.setVerticalScrollBarEnabled(false);
        showItemsAnimated(0);
    }

    private void updateHint() {
        if (!isAlwaysShare && !isNeverShare && !addToGroup) {
            if (chatType == ChatObject.CHAT_TYPE_CHANNEL) {
                actionBar.setSubtitle(LocaleController.formatPluralString("Members", selectedContacts.size()));
            } else {
                if (selectedContacts.isEmpty()) {
                    actionBar.setSubtitle(LocaleController.formatString(R.string.MembersCountZero, LocaleController.formatPluralString("Members", maxCount + (isCall ? 1 : 0))));
                } else {
                    String str = LocaleController.getPluralString("MembersCountSelected", selectedContacts.size());
                    actionBar.setSubtitle(String.format(str, selectedContacts.size(), maxCount));
                }
            }
        }
        if (chatType != ChatObject.CHAT_TYPE_CHANNEL && addToGroup) {
            if (doneButtonVisible && allSpans.isEmpty()) {
                floatingButton.setButtonVisible(false, true);
                doneButtonVisible = false;
            } else if (!doneButtonVisible && !allSpans.isEmpty()) {
                floatingButton.setButtonVisible(true, true);
                doneButtonVisible = true;
            }
        }
    }

    public void setDelegate(GroupCreateActivityDelegate groupCreateActivityDelegate) {
        delegate = groupCreateActivityDelegate;
    }

    public void setDelegate2(ContactsAddActivityDelegate contactsAddActivityDelegate) {
        delegate2 = contactsAddActivityDelegate;
    }

    public static class Comparator implements java.util.Comparator<TLObject> {
        private static String getName(TLObject object) {
            if (object instanceof TLRPC.User) {
                TLRPC.User user = (TLRPC.User) object;
                return ContactsController.formatName(user.first_name, user.last_name);
            } else if (object instanceof TLRPC.Chat) {
                TLRPC.Chat chat = (TLRPC.Chat) object;
                return chat.title;
            }
            return "";
        }
        @Override
        public int compare(TLObject o1, TLObject o2) {
            return getName(o1).compareTo(getName(o2));
        }
    }

    public class GroupCreateAdapter extends RecyclerListView.FastScrollAdapter {

        private final Context context;
        private ArrayList<Object> searchResult = new ArrayList<>();
        private ArrayList<CharSequence> searchResultNames = new ArrayList<>();
        private final SearchAdapterHelper searchAdapterHelper;
        private Runnable searchRunnable;
        private boolean searching;
        private final ArrayList<TLObject> contacts = new ArrayList<>();
        private int userTypesHeaderRow;
        private int firstSectionRow;
        private int createCallLinkRow;
        private int premiumRow;
        private int miniAppsRow;
        private int usersStartRow;
        private int inviteViaLink;
        private int noContactsStubRow;
        private int currentItemsCount;

        @Override
        public void notifyDataSetChanged() {
            super.notifyDataSetChanged();
            updateEditTextHint();
        }

        public GroupCreateAdapter(Context ctx) {
            context = ctx;

            final HashSet<Long> addedContacts = new HashSet<>();
            ArrayList<TLRPC.TL_contact> arrayList = getContactsController().contacts;
            for (int a = 0; a < arrayList.size(); a++) {
                TLRPC.User user = getMessagesController().getUser(arrayList.get(a).user_id);
                if (user == null || user.self || user.deleted) {
                    continue;
                }
                contacts.add(user);
                addedContacts.add(user.id);
            }
            if (isNeverShare || isAlwaysShare || isCall) {
                ArrayList<TLRPC.Dialog> dialogs = getMessagesController().getAllDialogs();
                if (isCall) {
                    for (int a = 0, N = dialogs.size(); a < N; a++) {
                        TLRPC.Dialog dialog = dialogs.get(a);
                        if (!DialogObject.isUserDialog(dialog.id) || addedContacts.contains(dialog.id)) {
                            continue;
                        }
                        TLRPC.User user = getMessagesController().getUser(dialog.id);
                        if (user == null || UserObject.isDeleted(user) || UserObject.isUserSelf(user) || UserObject.isBot(user) || UserObject.isService(dialog.id) || MessagesController.isSupportUser(user)) {
                            continue;
                        }
                        contacts.add(user);
                        addedContacts.add(user.id);
                    }
                } else {
                    for (int a = 0, N = dialogs.size(); a < N; a++) {
                        TLRPC.Dialog dialog = dialogs.get(a);
                        if (!DialogObject.isChatDialog(dialog.id)) {
                            continue;
                        }
                        TLRPC.Chat chat = getMessagesController().getChat(-dialog.id);
                        if (chat == null || chat.migrated_to != null || ChatObject.isChannel(chat) && !chat.megagroup) {
                            continue;
                        }
                        contacts.add(chat);
                    }
                }
                Collections.sort(contacts, new Comparator());
                TLObject lastContact = null;
                for (int i = 0; i < contacts.size(); ++i) {
                    TLObject contact = contacts.get(i);
                    if (lastContact == null || !firstLetter(Comparator.getName(lastContact)).equals(firstLetter(Comparator.getName(contact)))) {
                        contacts.add(i, new Letter(firstLetter(Comparator.getName(contact))));
                    }
                    lastContact = contact;
                }
            }

            searchAdapterHelper = new SearchAdapterHelper(false);
            searchAdapterHelper.setDelegate((searchId) -> {
                showItemsAnimated(currentItemsCount);
                if (searchRunnable == null && !searchAdapterHelper.isSearchInProgress() && getItemCount() == 0) {
                    emptyView.showProgress(false, true);
                }
                notifyDataSetChanged();
            });
        }

        private String firstLetter(String string) {
            if (TextUtils.isEmpty(string)) return "";
            return string.substring(0, 1);
        }

        public void setSearching(boolean value) {
            if (searching == value) {
                return;
            }
            searching = value;
            notifyDataSetChanged();
        }

        @Override
        public String getLetter(int position) {
            if (searching || position < usersStartRow || position >= contacts.size() + usersStartRow) {
                return null;
            }
            TLObject object = contacts.get(position - usersStartRow);
            String firstName;
            String lastName;
            if (object instanceof Letter) {
                return ((Letter) object).letter;
            } else if (object instanceof TLRPC.User) {
                TLRPC.User user = (TLRPC.User) object;
                firstName = user.first_name;
                lastName = user.last_name;
            } else {
                TLRPC.Chat chat = (TLRPC.Chat) object;
                firstName = chat.title;
                lastName = "";
            }
            if (LocaleController.nameDisplayOrder == 1) {
                if (!TextUtils.isEmpty(firstName)) {
                    return firstName.substring(0, 1).toUpperCase();
                } else if (!TextUtils.isEmpty(lastName)) {
                    return lastName.substring(0, 1).toUpperCase();
                }
            } else {
                if (!TextUtils.isEmpty(lastName)) {
                    return lastName.substring(0, 1).toUpperCase();
                } else if (!TextUtils.isEmpty(firstName)) {
                    return firstName.substring(0, 1).toUpperCase();
                }
            }
            return "";
        }

        @Override
        public int getItemCount() {
            int count;
            noContactsStubRow = -1;
            userTypesHeaderRow = -1;
            createCallLinkRow = -1;
            firstSectionRow = -1;
            premiumRow = -1;
            miniAppsRow = -1;
            if (searching) {
                count = searchResult.size();
                int localServerCount = searchAdapterHelper.getLocalServerSearch().size();
                int globalCount = searchAdapterHelper.getGlobalSearch().size();
                count += localServerCount;
                if (globalCount != 0) {
                    count += globalCount + 1;
                }
                currentItemsCount = count;
                return count;
            } else {
                count = 0;
                if (isCall) {
                    createCallLinkRow = count++;
                }
                if (allowPremium) {
                    userTypesHeaderRow = firstSectionRow = count++;
                    premiumRow = count++;
                } else if (allowMiniApps) {
                    userTypesHeaderRow = firstSectionRow = count++;
                    miniAppsRow = count++;
                } else {
                    firstSectionRow = count;
                }
                usersStartRow = count;
                count += contacts.size();
                if (addToGroup) {
                    if (chatId != 0) {
                        TLRPC.Chat chat = getMessagesController().getChat(chatId);
                        inviteViaLink = ChatObject.canUserDoAdminAction(chat, ChatObject.ACTION_INVITE) ? 1 : 0;
                    } else if (channelId != 0) {
                        TLRPC.Chat chat = getMessagesController().getChat(channelId);
                        inviteViaLink = ChatObject.canUserDoAdminAction(chat, ChatObject.ACTION_INVITE) && !ChatObject.isPublic(chat) ? 2 : 0;
                    } else {
                        inviteViaLink = 0;
                    }
                    if (inviteViaLink != 0) {
                        usersStartRow++;
                        count++;
                    }
                }
                if (count == 0) {
                    noContactsStubRow = 0;
                    count++;
                }
            }
            currentItemsCount = count;
            return count;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case 0:
                    view = new GraySectionCell(context);
                    break;
                case 1:
                    view = new GroupCreateUserCell(context, 1, 0, false);//.showPremiumBlocked();
                    break;
                case 3:
                    StickerEmptyView stickerEmptyView = new StickerEmptyView(context, null, StickerEmptyView.STICKER_TYPE_NO_CONTACTS) {
                        @Override
                        protected void onAttachedToWindow() {
                            super.onAttachedToWindow();
                            stickerView.getImageReceiver().startAnimation();
                        }
                    };
                    stickerEmptyView.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                    stickerEmptyView.subtitle.setVisibility(View.GONE);
                    stickerEmptyView.title.setText(getString(R.string.NoContacts));
                    stickerEmptyView.setAnimateLayoutChange(true);
                    view = stickerEmptyView;
                    break;
                case 2:
                default:
                    view = new TextCell(context);
                    break;
            }
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            switch (holder.getItemViewType()) {
                case 0: {
                    GraySectionCell cell = (GraySectionCell) holder.itemView;
                    if (searching) {
                        cell.setText(getString(R.string.GlobalSearch));
                    } else if (position == userTypesHeaderRow) {
                        cell.setText(getString(R.string.PrivacyUserTypes));
                    } else if (position - usersStartRow >= 0 && position - usersStartRow < contacts.size()) {
                        TLObject object = contacts.get(position - usersStartRow);
                        if (object instanceof Letter) {
                            cell.setText(((Letter) object).letter.toUpperCase());
                        }
                    }
                    if (position == firstSectionRow) {
                        cell.setRightText(selectedPremium != null || !selectedContacts.isEmpty() ? getString(R.string.DeselectAll) : "", true, v -> {
                            selectedPremium = null;
                            selectedContacts.clear();
                            spansContainer.removeAllSpans(true);
                            checkVisibleRows();
                            updateEditTextHint();

                        });
                    }
                    break;
                }
                case 1: {
                    GroupCreateUserCell cell = (GroupCreateUserCell) holder.itemView;
                    TLObject object;
                    CharSequence username = null;
                    CharSequence name = null;
                    if (searching) {
                        int localCount = searchResult.size();
                        int globalCount = searchAdapterHelper.getGlobalSearch().size();
                        int localServerCount = searchAdapterHelper.getLocalServerSearch().size();

                        if (position >= 0 && position < localCount) {
                            object = (TLObject) searchResult.get(position);
                        } else if (position >= localCount && position < localServerCount + localCount) {
                            object = searchAdapterHelper.getLocalServerSearch().get(position - localCount);
                        } else if (position > localCount + localServerCount && position <= globalCount + localCount + localServerCount) {
                            object = searchAdapterHelper.getGlobalSearch().get(position - localCount - localServerCount - 1);
                        } else {
                            object = null;
                        }
                        if (object != null) {
                            String objectUserName;
                            if (object instanceof TLRPC.User) {
                                objectUserName = ((TLRPC.User) object).username;
                            } else if (object instanceof TLRPC.Chat) {
                                objectUserName = ChatObject.getPublicUsername((TLRPC.Chat) object);
                            } else {
                                return;
                            }
                            if (position < localCount) {
                                name = searchResultNames.get(position);
                                if (name != null && !TextUtils.isEmpty(objectUserName)) {
                                    if (name.toString().startsWith("@" + objectUserName)) {
                                        username = name;
                                        name = null;
                                    }
                                }
                            } else if (position > localCount && !TextUtils.isEmpty(objectUserName)) {
                                String foundUserName = searchAdapterHelper.getLastFoundUsername();
                                if (foundUserName.startsWith("@")) {
                                    foundUserName = foundUserName.substring(1);
                                }
                                try {
                                    int index;
                                    SpannableStringBuilder spannableStringBuilder = new SpannableStringBuilder();
                                    spannableStringBuilder.append("@");
                                    spannableStringBuilder.append(objectUserName);
                                    if ((index = AndroidUtilities.indexOfIgnoreCase(objectUserName, foundUserName)) != -1) {
                                        int len = foundUserName.length();
                                        if (index == 0) {
                                            len++;
                                        } else {
                                            index++;
                                        }
                                        spannableStringBuilder.setSpan(new ForegroundColorSpan(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4)), index, index + len, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                                    }
                                    username = spannableStringBuilder;
                                } catch (Exception e) {
                                    username = objectUserName;
                                }
                            }
                        }
                    } else {
                        if (position == premiumRow) {
                            cell.setPremium();
                            cell.setChecked(selectedPremium != null, false);
                            return;
                        } else if (position == miniAppsRow) {
                            cell.setMiniapps();
                            cell.setChecked(selectedMiniApps != null, false);
                            return;
                        }
                        object = contacts.get(position - usersStartRow);
                    }
                    cell.setObject(object, name, username);
                    long id;
                    if (object instanceof TLRPC.User) {
                        id = ((TLRPC.User) object).id;
                    } else if (object instanceof TLRPC.Chat) {
                        id = -((TLRPC.Chat) object).id;
                    } else {
                        id = 0;
                    }
                    if (id != 0) {
                        if (ignoreUsers != null && ignoreUsers.indexOfKey(id) >= 0) {
                            cell.setChecked(true, false);
                            cell.setCheckBoxEnabled(false);
                        } else {
                            cell.setChecked(selectedContacts.indexOfKey(id) >= 0, false);
                            cell.setCheckBoxEnabled(true);
                        }
                    }
                    break;
                }
                case 2: {
                    TextCell textCell = (TextCell) holder.itemView;
                    if (position == createCallLinkRow) {
                        textCell.setTextAndIcon(getString(R.string.GroupCallCreateLink), R.drawable.menu_link_create2, false);
                        textCell.setColors(Theme.key_windowBackgroundWhiteBlueIcon, Theme.key_windowBackgroundWhiteBlueButton);
                    } else if (inviteViaLink == 2) {
                        textCell.setTextAndIcon(getString(R.string.ChannelInviteViaLink), R.drawable.msg_link2, false);
                        textCell.setColors(Theme.key_windowBackgroundWhiteGrayIcon, Theme.key_windowBackgroundWhiteBlackText);
                    } else {
                        textCell.setTextAndIcon(getString(R.string.InviteToGroupByLink), R.drawable.msg_link2, false);
                        textCell.setColors(Theme.key_windowBackgroundWhiteGrayIcon, Theme.key_windowBackgroundWhiteBlackText);
                    }
                    break;
                }
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (searching) {
                if (position == searchResult.size() + searchAdapterHelper.getLocalServerSearch().size()) {
                    return 0;
                }
                return 1;
            } else {
                if (position == createCallLinkRow) {
                    return 2;
                }
                if (position == userTypesHeaderRow) {
                    return 0;
                }
                if (position == premiumRow || position == miniAppsRow) {
                    return 1;
                }
                if (inviteViaLink != 0 && position == 0) {
                    return 2;
                }
                if (noContactsStubRow == position) {
                    return 3;
                }
                if (position - usersStartRow >= 0 && position - usersStartRow < contacts.size()) {
                    if (contacts.get(position - usersStartRow) instanceof Letter)
                        return 0;
                }
                return 1;
            }
        }

        @Override
        public void getPositionForScrollProgress(RecyclerListView listView, float progress, int[] position) {
            position[0] = (int) (getItemCount() * progress);
            position[1] = 0;
        }

        @Override
        public void onViewRecycled(RecyclerView.ViewHolder holder) {
            if (holder.itemView instanceof GroupCreateUserCell) {
                ((GroupCreateUserCell) holder.itemView).recycle();
            }
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            if (holder.getItemViewType() == 0) return false;
            if (ignoreUsers != null && holder.itemView instanceof GroupCreateUserCell) {
                GroupCreateUserCell cell = (GroupCreateUserCell) holder.itemView;
                Object object = cell.getObject();
                if (object instanceof TLRPC.User) {
                    TLRPC.User user = (TLRPC.User) object;
                    return ignoreUsers.indexOfKey(user.id) < 0;
                }
            }
            return true;
        }

        public void searchDialogs(final String query) {
            if (searchRunnable != null) {
                Utilities.searchQueue.cancelRunnable(searchRunnable);
                searchRunnable = null;
            }

            searchResult.clear();
            searchResultNames.clear();
            searchAdapterHelper.mergeResults(null);
            searchAdapterHelper.queryServerSearch(null, true, isAlwaysShare || isNeverShare, false, false, false, 0, false, 0, 0);
            notifyDataSetChanged();

            if (!TextUtils.isEmpty(query)){
                Utilities.searchQueue.postRunnable(searchRunnable = () -> AndroidUtilities.runOnUIThread(() -> {
                    searchAdapterHelper.queryServerSearch(query, true, isAlwaysShare || isNeverShare, true, false, false, 0, false, 0, 0);
                    Utilities.searchQueue.postRunnable(searchRunnable = () -> {
                        String search1 = query.trim().toLowerCase();
                        if (search1.isEmpty()) {
                            updateSearchResults(new ArrayList<>(), new ArrayList<>());
                            return;
                        }
                        String search2 = LocaleController.getInstance().getTranslitString(search1);
                        if (search1.equals(search2) || search2.isEmpty()) {
                            search2 = null;
                        }
                        String[] search = new String[1 + (search2 != null ? 1 : 0)];
                        search[0] = search1;
                        if (search2 != null) {
                            search[1] = search2;
                        }

                        ArrayList<Object> resultArray = new ArrayList<>();
                        ArrayList<CharSequence> resultArrayNames = new ArrayList<>();

                        for (int a = 0; a < contacts.size(); a++) {
                            TLObject object = contacts.get(a);

                            String name;
                            String username;

                            if (object instanceof TLRPC.User) {
                                TLRPC.User user = (TLRPC.User) object;
                                name = ContactsController.formatName(user.first_name, user.last_name).toLowerCase();
                                username = UserObject.getPublicUsername(user);
                            } else if (object instanceof TLRPC.Chat) {
                                TLRPC.Chat chat = (TLRPC.Chat) object;
                                name = chat.title;
                                username = ChatObject.getPublicUsername(chat);
                            } else {
                                continue;
                            }
                            String tName = LocaleController.getInstance().getTranslitString(name);
                            if (name.equals(tName)) {
                                tName = null;
                            }

                            int found = 0;
                            for (String q : search) {
                                if (name.startsWith(q) || name.contains(" " + q) || tName != null && (tName.startsWith(q) || tName.contains(" " + q))) {
                                    found = 1;
                                } else if (username != null && username.startsWith(q)) {
                                    found = 2;
                                }

                                if (found != 0) {
                                    if (found == 1) {
                                        if (object instanceof TLRPC.User) {
                                            TLRPC.User user = (TLRPC.User) object;
                                            resultArrayNames.add(AndroidUtilities.generateSearchName(user.first_name, user.last_name, q));
                                        } else if (object instanceof TLRPC.Chat) {
                                            TLRPC.Chat chat = (TLRPC.Chat) object;
                                            resultArrayNames.add(AndroidUtilities.generateSearchName(chat.title, null, q));
                                        }
                                    } else {
                                        resultArrayNames.add(AndroidUtilities.generateSearchName("@" + username, null, "@" + q));
                                    }
                                    resultArray.add(object);
                                    break;
                                }
                            }
                        }
                        updateSearchResults(resultArray, resultArrayNames);
                    });
                }), 300);
            }
        }

        private void updateSearchResults(final ArrayList<Object> users, final ArrayList<CharSequence> names) {
            AndroidUtilities.runOnUIThread(() -> {
                if (!searching) {
                    return;
                }
                searchRunnable = null;
                searchResult = users;
                searchResultNames = names;
                searchAdapterHelper.mergeResults(searchResult);
                showItemsAnimated(currentItemsCount);
                notifyDataSetChanged();
                if (searching && !searchAdapterHelper.isSearchInProgress() && getItemCount() == 0) {
                    emptyView.showProgress(false, true);
                }
            });
        }
    }

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        ArrayList<ThemeDescription> themeDescriptions = new ArrayList<>();

        ThemeDescription.ThemeDescriptionDelegate cellDelegate = () -> {
            if (listView != null) {
                int count = listView.getChildCount();
                for (int a = 0; a < count; a++) {
                    View child = listView.getChildAt(a);
                    if (child instanceof GroupCreateUserCell) {
                        ((GroupCreateUserCell) child).update(0);
                    }
                }
            }
            if (searchField != null) {
                searchField.updateColors();
            }
            if (floatingButton != null) {
                floatingButton.updateColors();
            }
        };

        themeDescriptions.add(new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundWhite));

        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_actionBarDefault));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_LISTGLOWCOLOR, null, null, null, null, Theme.key_actionBarDefault));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_ITEMSCOLOR, null, null, null, null, Theme.key_actionBarDefaultIcon));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_TITLECOLOR, null, null, null, null, Theme.key_actionBarDefaultTitle));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_actionBarDefaultSelector));

        themeDescriptions.add(new ThemeDescription(scrollView, ThemeDescription.FLAG_LISTGLOWCOLOR, null, null, null, null, Theme.key_windowBackgroundWhite));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_SELECTOR, null, null, null, null, Theme.key_listSelector));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_FASTSCROLL, null, null, null, null, Theme.key_fastScrollActive));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_FASTSCROLL, null, null, null, null, Theme.key_fastScrollInactive));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_FASTSCROLL, null, null, null, null, Theme.key_fastScrollText));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{View.class}, Theme.dividerPaint, null, null, Theme.key_divider));

        themeDescriptions.add(new ThemeDescription(emptyView, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_emptyListPlaceholder));
        themeDescriptions.add(new ThemeDescription(emptyView, ThemeDescription.FLAG_PROGRESSBAR, null, null, null, null, Theme.key_progressCircle));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_CELLBACKGROUNDCOLOR, new Class[]{GroupCreateSectionCell.class}, null, null, null, Theme.key_graySection));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{GroupCreateSectionCell.class}, new String[]{"drawable"}, null, null, null, Theme.key_groupcreate_sectionShadow));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{GroupCreateSectionCell.class}, new String[]{"textView"}, null, null, null, Theme.key_groupcreate_sectionText));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{GroupCreateUserCell.class}, new String[]{"textView"}, null, null, null, Theme.key_groupcreate_sectionText));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{GroupCreateUserCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_checkbox));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{GroupCreateUserCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_checkboxDisabled));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{GroupCreateUserCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_checkboxCheck));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_TEXTCOLOR | ThemeDescription.FLAG_CHECKTAG, new Class[]{GroupCreateUserCell.class}, new String[]{"statusTextView"}, null, null, null, Theme.key_windowBackgroundWhiteBlueText));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_TEXTCOLOR | ThemeDescription.FLAG_CHECKTAG, new Class[]{GroupCreateUserCell.class}, new String[]{"statusTextView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{GroupCreateUserCell.class}, null, Theme.avatarDrawables, null, Theme.key_avatar_text));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundRed));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundOrange));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundViolet));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundGreen));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundCyan));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundBlue));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundPink));

        themeDescriptions.add(new ThemeDescription(spansContainer, 0, new Class[]{GroupCreateSpan.class}, null, null, null, Theme.key_groupcreate_spanBackground));
        themeDescriptions.add(new ThemeDescription(spansContainer, 0, new Class[]{GroupCreateSpan.class}, null, null, null, Theme.key_groupcreate_spanText));
        themeDescriptions.add(new ThemeDescription(spansContainer, 0, new Class[]{GroupCreateSpan.class}, null, null, null, Theme.key_groupcreate_spanDelete));
        themeDescriptions.add(new ThemeDescription(spansContainer, 0, new Class[]{GroupCreateSpan.class}, null, null, null, Theme.key_avatar_backgroundBlue));

        themeDescriptions.add(new ThemeDescription(emptyView.title, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(emptyView.subtitle, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteGrayText));

        if (sharedLinkBottomSheet != null) {
            themeDescriptions.addAll(sharedLinkBottomSheet.getThemeDescriptions());
        }

        return themeDescriptions;
    }

    private static class Letter extends TLRPC.TL_contact {
        public final String letter;
        public Letter(String letter) {
            this.letter = letter;
        }
    }

    @Override
    public void onFactorChanged(int id, float factor, float fraction, FactorAnimator callee) {
        if (id == ANIMATOR_ID_SELECTED_CONTAINER_HEIGHT) {
            final int oldPaddingTop = listView.getPaddingTop();

            checkUi_listViewPadding();
            checkUi_searchFieldY();
            checkUi_listClip();
            checkUi_headerShadowY();
            actionBarBackgroundView.invalidate();

            final int newPaddingTop = listView.getPaddingTop();
            if (newPaddingTop != oldPaddingTop && !headerShadowView.isShadowVisible()) {
                listView.scrollBy(0, oldPaddingTop - newPaddingTop);
            }
        } else if (id == ANIMATOR_ID_CALL_BUTTONS_VISIBLE) {
            checkUi_bottomButtons();
            checkUi_listClip();
        }
    }

    /* * */

    @Override
    public boolean isSupportEdgeToEdge() {
        return true;
    }
    @Override
    public boolean drawEdgeNavigationBar() {
        return false;
    }

    private int navigationBarHeight;
    private int imeInsetAnimatedHeight;

    @Override
    public View getAnimatedInsetsTargetView() {
        return fragmentView;
    }

    @Override
    public void onAnimatedInsetsChanged(View view, WindowInsetsCompat insets) {
        imeInsetAnimatedHeight = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom;
        checkUi_floatingButton();
    }

    @NonNull
    private WindowInsetsCompat onApplyWindowInsets(@NonNull View v, @NonNull WindowInsetsCompat insets) {
        navigationBarHeight = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom;
        if (buttonsContainer != null) {
            buttonsContainer.setPadding(0, 0, 0, navigationBarHeight);
        }

        checkUi_listViewPadding();
        checkUi_floatingButton();

        return WindowInsetsCompat.CONSUMED;
    }

    private void checkUi_listViewPadding() {
        final int buttonsH = isCall ? dp(14 + 48 + 14) : 0;

        listView.setPadding(
                0,
                dp(ADDITIONAL_LIST_HEIGHT_DP + DialogsActivity.SEARCH_FIELD_HEIGHT) + actionBar.getMeasuredHeight() + ((int) animatorSelectorContainerHeight.getFactor()),
                0,
                dp(ADDITIONAL_LIST_HEIGHT_DP) + navigationBarHeight + buttonsH);
        emptyView.setPadding(0, 0, 0, navigationBarHeight);
    }

    private void checkUi_searchFieldY() {
        searchField.setTranslationY(animatorSelectorContainerHeight.getFactor());
    }

    private void checkUi_headerShadowY() {
        headerShadowView.setTranslationY(dp(DialogsActivity.SEARCH_FIELD_HEIGHT) + animatorSelectorContainerHeight.getFactor());
    }

    private void checkUi_bottomButtons() {
        if (buttonsContainer == null) {
            return;
        }

        final float factor = animatorCallButtonsVisible.getFloatValue();
        buttonsContainer.setTranslationY(dp(12) * (1f - factor));
        buttonsContainer.setAlpha(factor);
        buttonsContainer.setVisibility(factor > 0 ? View.VISIBLE : View.GONE);
    }

    private void checkUi_floatingButton() {
        if (floatingButton != null) {
            floatingButton.setTranslationY(-Math.max(navigationBarHeight, imeInsetAnimatedHeight));
        }
    }

    private final Rect tmpClipRect = new Rect();
    private void checkUi_listClip() {
        if (listView.hasActiveEdgeEffects()) {
            listView.setClipBounds(null);
            return;
        }

        final int buttonsH = (int) ((navigationBarHeight + dp(14 + 48 + 14)) * animatorCallButtonsVisible.getFloatValue());

        tmpClipRect.set(
            0,
            dp(ADDITIONAL_LIST_HEIGHT_DP + DialogsActivity.SEARCH_FIELD_HEIGHT) + actionBar.getMeasuredHeight() + (int) (animatorSelectorContainerHeight.getFactor()),
            listView.getMeasuredWidth(),
            listView.getMeasuredHeight() - dp(ADDITIONAL_LIST_HEIGHT_DP) - buttonsH);
        listView.setClipBounds(tmpClipRect);
    }



    /* Blur */

    private final @Nullable DownscaleScrollableNoiseSuppressor scrollableViewNoiseSuppressor;
    private final @Nullable BlurredBackgroundSourceRenderNode iBlur3SourceGlassFrosted;

    private IBlur3Capture iBlur3Capture;
    private boolean iBlur3Invalidated;

    private final ArrayList<RectF> iBlur3Positions = new ArrayList<>(2);
    private final RectF iBlur3PositionActionBar = new RectF();
    private final RectF iBlur3PositionBottomBar = new RectF();
    {
        iBlur3Positions.add(iBlur3PositionActionBar);
        iBlur3Positions.add(iBlur3PositionBottomBar);
    }

    private void blur3_InvalidateBlur() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || scrollableViewNoiseSuppressor == null) {
            return;
        }

        final int additionalList = dp(48);
        final int additionalSearch = dp(DialogsActivity.SEARCH_FIELD_HEIGHT) + maxSize;

        iBlur3PositionActionBar.set(0, 0, fragmentView.getMeasuredWidth(), actionBar.getMeasuredHeight() + additionalSearch);
        iBlur3PositionActionBar.inset(0, -additionalList);
        if (buttonsContainer != null) {
            iBlur3PositionBottomBar.set(0, fragmentView.getMeasuredHeight() - buttonsContainer.getMeasuredHeight(), fragmentView.getMeasuredWidth(), fragmentView.getMeasuredHeight());
            iBlur3PositionBottomBar.inset(0, -additionalList);
        }

        scrollableViewNoiseSuppressor.setupRenderNodes(iBlur3Positions, buttonsContainer != null && animatorCallButtonsVisible.getFloatValue() > 0 ? 2 : 1);
        scrollableViewNoiseSuppressor.invalidateResultRenderNodes(iBlur3Capture, fragmentView.getMeasuredWidth(), fragmentView.getMeasuredHeight());
    }
}
