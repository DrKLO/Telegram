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
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Build;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ScrollView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BackDrawable;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.GroupCreateSectionCell;
import org.telegram.ui.Cells.InviteUserCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.FlickerLoadingView;
import org.telegram.ui.Components.FragmentFloatingButton;
import org.telegram.ui.Components.FragmentSearchField;
import org.telegram.ui.Components.GroupCreateSpan;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.StickerEmptyView;
import org.telegram.ui.Components.blur3.DownscaleScrollableNoiseSuppressor;
import org.telegram.ui.Components.blur3.ViewGroupPartRenderer;
import org.telegram.ui.Components.blur3.capture.IBlur3Capture;
import org.telegram.ui.Components.blur3.source.BlurredBackgroundSourceRenderNode;
import org.telegram.ui.Components.inset.WindowAnimatedInsetsProvider;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;

import me.vkryl.android.animator.FactorAnimator;

public class InviteContactsActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate, View.OnClickListener, FactorAnimator.Target, WindowAnimatedInsetsProvider.Listener {
    private final int ADDITIONAL_LIST_HEIGHT_DP = Build.VERSION.SDK_INT >= 31 ? 48 : 0;

    private static final int ANIMATOR_ID_SELECTED_CONTAINER_HEIGHT = 3;
    private final FactorAnimator animatorSelectorContainerHeight = new FactorAnimator(ANIMATOR_ID_SELECTED_CONTAINER_HEIGHT,
            this, CubicBezierInterpolator.EASE_OUT_QUINT, 350);

    private View actionBarBackgroundView;
    private HeaderShadowView headerShadowView;
    private FragmentFloatingButton floatingButton;
    private FragmentSearchField searchField;

    private ScrollView scrollView;
    private SpansContainer spansContainer;
    private RecyclerListView listView;
    private LinearLayoutManager layoutManager;
    private StickerEmptyView emptyView;
    private InviteAdapter adapter;
    private boolean ignoreScrollEvent;
    private ArrayList<ContactsController.Contact> phoneBookContacts;
    private int maxSize;

    private boolean searchWas;
    private boolean searching;
    private final HashMap<String, GroupCreateSpan> selectedContacts = new HashMap<>();
    private final ArrayList<GroupCreateSpan> allSpans = new ArrayList<>();
    private GroupCreateSpan currentDeletingSpan;

    private int fieldY;

    private class SpansContainer extends ViewGroup {

        private AnimatorSet currentAnimation;
        private boolean animationStarted;
        private final ArrayList<Animator> animators = new ArrayList<>();
        private View addingSpan;
        private View removingSpan;
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
                if (child != removingSpan && currentLineWidth + child.getMeasuredWidth() > maxWidth) {
                    y += child.getMeasuredHeight() + dp(8);
                    currentLineWidth = 0;
                }
                if (allCurrentLineWidth + child.getMeasuredWidth() > maxWidth) {
                    allY += child.getMeasuredHeight() + dp(8);
                    allCurrentLineWidth = 0;
                }
                x = dp(13) + currentLineWidth;
                if (!animationStarted) {
                    if (child == removingSpan) {
                        child.setTranslationX(dp(13) + allCurrentLineWidth);
                        child.setTranslationY(allY);
                    } else if (removingSpan != null) {
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
                if (child != removingSpan) {
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
            selectedContacts.put(span.getKey(), span);

            if (currentAnimation != null) {
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
        }

        public void removeSpan(final GroupCreateSpan span) {
            ignoreScrollEvent = true;
            selectedContacts.remove(span.getKey());
            allSpans.remove(span);
            span.setOnClickListener(null);

            if (currentAnimation != null && currentAnimation.isRunning()) {
                currentAnimation.setupEndValues();
                currentAnimation.cancel();
            }
            animationStarted = false;
            currentAnimation = new AnimatorSet();
            currentAnimation.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animator) {
                    removeView(span);
                    removingSpan = null;
                    currentAnimation = null;
                    animationStarted = false;
                }
            });
            currentAnimation.setDuration(150);
            removingSpan = span;
            animators.clear();
            animators.add(ObjectAnimator.ofFloat(removingSpan, View.SCALE_X, 1.0f, 0.01f));
            animators.add(ObjectAnimator.ofFloat(removingSpan, View.SCALE_Y, 1.0f, 0.01f));
            animators.add(ObjectAnimator.ofFloat(removingSpan, View.ALPHA, 1.0f, 0.0f));
            requestLayout();
        }
    }

    public InviteContactsActivity() {
        super();

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
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.contactsImported);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.contactsDidLoad);
        fetchContacts();
        if (!UserConfig.getInstance(currentAccount).contactsReimported) {
            ContactsController.getInstance(currentAccount).forceImportContacts();
            UserConfig.getInstance(currentAccount).contactsReimported = true;
            UserConfig.getInstance(currentAccount).saveConfig(false);
        }
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.contactsImported);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.contactsDidLoad);
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
    public ActionBar createActionBar(Context context) {
        ActionBar actionBar = super.createActionBar(context);
        actionBar.setAddToContainer(false);
        return actionBar;
    }

    @Override
    public View createView(Context context) {
        // hasOwnBackground = true;

        searching = false;
        searchWas = false;
        allSpans.clear();
        selectedContacts.clear();
        currentDeletingSpan = null;

        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(LocaleController.getString(R.string.InviteFriends));

        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
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

                checkUi_floatingButton();
                checkUi_searchFieldY();
                checkUi_listClip();
                checkUi_headerShadowY();
            }
        };

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

        searchField.editText.setHint(LocaleController.getString(R.string.SearchFriends));
        searchField.editText.setOnKeyListener(new View.OnKeyListener() {

            private boolean wasEmpty;

            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    wasEmpty = searchField.editText.length() == 0;
                } else if (event.getAction() == KeyEvent.ACTION_UP && wasEmpty && !allSpans.isEmpty()){
                    spansContainer.removeSpan(allSpans.get(allSpans.size() - 1));
                    updateHint();
                    checkVisibleRows();
                    return true;
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
                    searching = true;
                    searchWas = true;
                    adapter.setSearching(true);
                    adapter.searchDialogs(searchField.editText.toString());
                    listView.setFastScrollVisible(false);
                    listView.setVerticalScrollBarEnabled(true);
                    emptyView.showProgress(true);
                    emptyView.setStickerType(StickerEmptyView.STICKER_TYPE_SEARCH);
                    emptyView.title.setText(LocaleController.getString(R.string.NoResult));
                    emptyView.subtitle.setText(LocaleController.getString(R.string.SearchEmptyViewFilteredSubtitle2));
                } else {
                    closeSearch();
                }
            }
        });

        FlickerLoadingView flickerLoadingView = new FlickerLoadingView(context);
        flickerLoadingView.setViewType(FlickerLoadingView.USERS_TYPE);
        flickerLoadingView.showDate(false);

        emptyView = new StickerEmptyView(context, flickerLoadingView, StickerEmptyView.STICKER_TYPE_NO_CONTACTS);
        emptyView.addView(flickerLoadingView, 0);
        emptyView.setAnimateLayoutChange(true);
        emptyView.title.setText(LocaleController.getString(R.string.NoContacts));
        emptyView.subtitle.setText("");
        emptyView.showProgress(ContactsController.getInstance(currentAccount).isLoadingContacts());

        contentView.addView(emptyView);

        layoutManager = new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false);

        adapter = new InviteAdapter(context);

        listView = new RecyclerListView(context);
        listView.setEmptyView(emptyView);
        listView.setAdapter(adapter);
        listView.setLayoutManager(layoutManager);
        listView.setVerticalScrollBarEnabled(true);
        listView.setVerticalScrollbarPosition(LocaleController.isRTL ? View.SCROLLBAR_POSITION_LEFT : View.SCROLLBAR_POSITION_RIGHT);
        listView.setClipToPadding(false);
        contentView.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL, 0, -ADDITIONAL_LIST_HEIGHT_DP, 0, 0));
        listView.setOnItemClickListener((view, position) -> {
            if (position == 0 && !searching) {
                try {
                    Intent intent = new Intent(Intent.ACTION_SEND);
                    intent.setType("text/plain");
                    String text = ContactsController.getInstance(currentAccount).getInviteText(0);
                    intent.putExtra(Intent.EXTRA_TEXT, text);
                    getParentActivity().startActivityForResult(Intent.createChooser(intent, text), 500);
                } catch (Exception e) {
                    FileLog.e(e);
                }
                return;
            }
            if (!(view instanceof InviteUserCell)) {
                return;
            }
            InviteUserCell cell = (InviteUserCell) view;
            ContactsController.Contact contact = cell.getContact();
            if (contact == null) {
                return;
            }
            final GroupCreateSpan selectedSpan = selectedContacts.get(contact.key);
            if (selectedSpan != null) {
                spansContainer.removeSpan(selectedSpan);
            } else {
                GroupCreateSpan span = new GroupCreateSpan(getContext(), contact);
                spansContainer.addSpan(span);
                span.setOnClickListener(InviteContactsActivity.this);
            }
            updateHint();
            if (searching || searchWas) {
                AndroidUtilities.showKeyboard(searchField.editText);
            } else {
                cell.setChecked(selectedSpan == null, true);
            }
            if (searchField.editText.length() > 0) {
                searchField.editText.setText(null);
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

        BackDrawable backDrawable = new BackDrawable(false);
        backDrawable.setArrowRotation(180);
        floatingButton = new FragmentFloatingButton(context, resourceProvider);
        floatingButton.imageView.setImageDrawable(backDrawable);
        floatingButton.setButtonVisible(false, false);
        floatingButton.setContentDescription(getString(R.string.Next));
        floatingButton.setOnClickListener(v -> {
            try {
                StringBuilder builder = new StringBuilder();
                int num = 0;
                for (int a = 0; a < allSpans.size(); a++) {
                    ContactsController.Contact contact = allSpans.get(a).getContact();
                    if (builder.length() != 0) {
                        builder.append(';');
                    }
                    builder.append(contact.phones.get(0));
                    if (a == 0 && allSpans.size() == 1) {
                        num = contact.imported;
                    }
                }
                Intent intent = new Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + builder.toString()));
                intent.putExtra("sms_body", ContactsController.getInstance(currentAccount).getInviteText(num));
                getParentActivity().startActivityForResult(intent, 500);
            } catch (Exception e) {
                FileLog.e(e);
            }
            finishFragment();
        });

        actionBarBackgroundView = new View(context) {
            private final Rect rectTmp = new Rect();
            private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

            @Override
            protected void dispatchDraw(@NonNull Canvas canvas) {
                super.dispatchDraw(canvas);
                final int searchH = dp(DialogsActivity.SEARCH_FIELD_HEIGHT) + (int) (animatorSelectorContainerHeight.getFactor());

                paint.setColor(getThemedColor(Theme.key_actionBarDefault));
                rectTmp.set(0, 0, getMeasuredWidth(), actionBar.getMeasuredHeight() + searchH);

                canvas.drawRect(rectTmp, paint);
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || !SharedConfig.chatBlurEnabled() || iBlur3SourceGlassFrosted == null) {
                    return;
                }

                iBlur3SourceGlassFrosted.draw(canvas, rectTmp.left, rectTmp.top, rectTmp.right, rectTmp.bottom);
                paint.setAlpha(ChatActivity.ACTION_BAR_BLUR_ALPHA);
                canvas.drawRect(rectTmp, paint);
            }
        };
        headerShadowView = new HeaderShadowView(context, parentLayout);
        headerShadowView.setShadowVisible(false, false);
        actionBar.setBackgroundColor(0);
        iBlur3Capture = new ViewGroupPartRenderer(listView, contentView, listView::drawChild);
        listView.setOverScrollListener(() -> listView.postOnAnimation(() -> {
            checkUi_listClip();
            blur3_InvalidateBlur();
        }));

        contentView.addView(floatingButton, FragmentFloatingButton.createDefaultLayoutParams());
        contentView.addView(actionBarBackgroundView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 0, Gravity.TOP));
        contentView.addView(actionBar);
        contentView.addView(searchField, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 40, Gravity.TOP, 11, 0, 11, 0));
        contentView.addView(scrollView);
        contentView.addView(headerShadowView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 5, Gravity.TOP));

        if (LaunchActivity.instance != null) {
            LaunchActivity.instance.getRootAnimatedInsetsListener().subscribeToWindowInsetsAnimation(this);
        }
        ViewCompat.setOnApplyWindowInsetsListener(fragmentView, this::onApplyWindowInsets);
        return fragmentView;
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.contactsImported) {
            fetchContacts();
        } else if (id == NotificationCenter.contactsDidLoad) {
            if (emptyView != null) {
                emptyView.showProgress(false);
            }
        }
    }

    private void checkVisibleRows() {
        int count = listView.getChildCount();
        for (int a = 0; a < count; a++) {
            View child = listView.getChildAt(a);
            if (child instanceof InviteUserCell) {
                InviteUserCell cell = (InviteUserCell) child;
                ContactsController.Contact contact = cell.getContact();
                if (contact != null) {
                    cell.setChecked(selectedContacts.containsKey(contact.key), true);
                }
            }
        }
    }

    private void updateHint() {
        floatingButton.setButtonVisible(!allSpans.isEmpty(), true);
    }

    private void closeSearch() {
        searching = false;
        searchWas = false;
        adapter.setSearching(false);
        adapter.searchDialogs(null);
        listView.setFastScrollVisible(true);
        listView.setVerticalScrollBarEnabled(false);
        emptyView.showProgress(false);
        emptyView.setStickerType(StickerEmptyView.STICKER_TYPE_NO_CONTACTS);
        emptyView.title.setText(LocaleController.getString(R.string.NoContacts));
        emptyView.subtitle.setText("");
    }

    private void fetchContacts() {
        phoneBookContacts = new ArrayList<>(ContactsController.getInstance(currentAccount).phoneBookContacts);
        Collections.sort(phoneBookContacts, (o1, o2) -> {
            if (o1.imported > o2.imported) {
                return -1;
            } else if (o1.imported < o2.imported) {
                return 1;
            }
            return 0;
        });
        if (emptyView != null) {
            emptyView.showProgress(false);
        }
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    public class InviteAdapter extends RecyclerListView.SelectionAdapter {

        private final Context context;
        private ArrayList<ContactsController.Contact> searchResult = new ArrayList<>();
        private ArrayList<CharSequence> searchResultNames = new ArrayList<>();
        private Timer searchTimer;
        private boolean searching;

        public InviteAdapter(Context ctx) {
            context = ctx;
        }

        public void setSearching(boolean value) {
            if (searching == value) {
                return;
            }
            searching = value;
            notifyDataSetChanged();
        }

        @Override
        public int getItemCount() {
            if (searching) {
                return searchResult.size();
            }
            return phoneBookContacts.size() + 2;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view;
            if (viewType == 1) {
                final TextCell textCell = new TextCell(context);
                textCell.setTextAndValueAndColorfulIcon(LocaleController.getString(R.string.ShareTelegram), "", false, R.drawable.msg_filled_shareout, 0xFF4F85F6, 0xFF3568E8, false);
                view = textCell;
            } else if (viewType == 2) {
                view = new ShadowSectionCell(context, 12, Theme.getColor(Theme.key_windowBackgroundGray));
            } else {
                view = new InviteUserCell(context, true);
            }
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            if (holder.getItemViewType() == 0) {
                InviteUserCell cell = (InviteUserCell) holder.itemView;
                ContactsController.Contact contact;
                CharSequence name;
                if (searching) {
                    contact = searchResult.get(position);
                    name = searchResultNames.get(position);
                } else {
                    contact = phoneBookContacts.get(position - 2);
                    name = null;
                }
                cell.setUser(contact, name);
                cell.setChecked(selectedContacts.containsKey(contact.key), false);
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (!searching) {
                if (position == 0) {
                    return 1;
                }
                if (position == 1) {
                    return 2;
                }
            }
            return 0;
        }

        @Override
        public void onViewRecycled(RecyclerView.ViewHolder holder) {
            if (holder.itemView instanceof InviteUserCell) {
                ((InviteUserCell) holder.itemView).recycle();
            }
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return holder.getItemViewType() != 2;
        }

        public void searchDialogs(final String query) {
            try {
                if (searchTimer != null) {
                    searchTimer.cancel();
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
            if (query == null) {
                searchResult.clear();
                searchResultNames.clear();
                notifyDataSetChanged();
            } else {
                searchTimer = new Timer();
                searchTimer.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        try {
                            searchTimer.cancel();
                            searchTimer = null;
                        } catch (Exception e) {
                            FileLog.e(e);
                        }

                        AndroidUtilities.runOnUIThread(() -> Utilities.searchQueue.postRunnable(() -> {
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

                            ArrayList<ContactsController.Contact> resultArray = new ArrayList<>();
                            ArrayList<CharSequence> resultArrayNames = new ArrayList<>();

                            for (int a = 0; a < phoneBookContacts.size(); a++) {
                                ContactsController.Contact contact = phoneBookContacts.get(a);

                                String name = ContactsController.formatName(contact.first_name, contact.last_name).toLowerCase();
                                String tName = LocaleController.getInstance().getTranslitString(name);
                                if (name.equals(tName)) {
                                    tName = null;
                                }

                                int found = 0;
                                for (String q : search) {
                                    if (name.startsWith(q) || name.contains(" " + q) || tName != null && (tName.startsWith(q) || tName.contains(" " + q))) {
                                        found = 1;
                                    }

                                    if (found != 0) {
                                        resultArrayNames.add(AndroidUtilities.generateSearchName(contact.first_name, contact.last_name, q));
                                        resultArray.add(contact);
                                        break;
                                    }
                                }
                            }
                            updateSearchResults(resultArray, resultArrayNames);
                        }));

                    }
                }, 200, 300);
            }
        }

        private void updateSearchResults(final ArrayList<ContactsController.Contact> users, final ArrayList<CharSequence> names) {
            AndroidUtilities.runOnUIThread(() -> {
                if (!searching) {
                    return;
                }
                searchResult = users;
                searchResultNames = names;
                notifyDataSetChanged();
                emptyView.showProgress(false);
            });
        }

        @Override
        public void notifyDataSetChanged() {
            super.notifyDataSetChanged();
            int count = getItemCount();
            if (!searching) {
                emptyView.setVisibility(count == 1 ? View.VISIBLE : View.INVISIBLE);
            }
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
                    if (child instanceof InviteUserCell) {
                        ((InviteUserCell) child).update(0);
                    }
                }
            }
        };

        themeDescriptions.add(new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundWhite));

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

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_CELLBACKGROUNDCOLOR, new Class[]{GroupCreateSectionCell.class}, null, null, null, Theme.key_graySection));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{GroupCreateSectionCell.class}, new String[]{"drawable"}, null, null, null, Theme.key_groupcreate_sectionShadow));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{GroupCreateSectionCell.class}, new String[]{"textView"}, null, null, null, Theme.key_groupcreate_sectionText));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{InviteUserCell.class}, new String[]{"textView"}, null, null, null, Theme.key_groupcreate_sectionText));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{InviteUserCell.class}, new String[]{"nameTextView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{InviteUserCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_checkbox));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{InviteUserCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_checkboxCheck));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_TEXTCOLOR | ThemeDescription.FLAG_CHECKTAG, new Class[]{InviteUserCell.class}, new String[]{"statusTextView"}, null, null, null, Theme.key_windowBackgroundWhiteBlueText));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_TEXTCOLOR | ThemeDescription.FLAG_CHECKTAG, new Class[]{InviteUserCell.class}, new String[]{"statusTextView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{InviteUserCell.class}, null, Theme.avatarDrawables, null, Theme.key_avatar_text));
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

        return themeDescriptions;
    }

    /* * */

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

        checkUi_listViewPadding();
        checkUi_floatingButton();

        return WindowInsetsCompat.CONSUMED;
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
        }
    }

    @Override
    public boolean isSupportEdgeToEdge() {
        return true;
    }

    private void checkUi_searchFieldY() {
        searchField.setTranslationY(animatorSelectorContainerHeight.getFactor());
    }

    private void checkUi_headerShadowY() {
        headerShadowView.setTranslationY(dp(DialogsActivity.SEARCH_FIELD_HEIGHT) + animatorSelectorContainerHeight.getFactor());
    }

    private void checkUi_listViewPadding() {
        final int top = dp(ADDITIONAL_LIST_HEIGHT_DP + DialogsActivity.SEARCH_FIELD_HEIGHT)
            + actionBar.getMeasuredHeight()
            + ((int) animatorSelectorContainerHeight.getFactor());

        listView.setPadding(0, top, 0, navigationBarHeight);
        emptyView.setPadding(0, 0, 0, navigationBarHeight);
    }

    private void checkUi_floatingButton() {
        if (floatingButton != null) {
            floatingButton.setTranslationY(-Math.max(navigationBarHeight, imeInsetAnimatedHeight));
        }
    }

    private final Rect tmpClipRect = new Rect();
    private void checkUi_listClip() {
        if (listView.hasActiveOverScroll()) {
            listView.setClipBounds(null);
            return;
        }

        final int top = dp(ADDITIONAL_LIST_HEIGHT_DP + DialogsActivity.SEARCH_FIELD_HEIGHT)
            + actionBar.getMeasuredHeight()
            + ((int) animatorSelectorContainerHeight.getFactor());

        tmpClipRect.set(0, top, listView.getMeasuredWidth(), listView.getMeasuredHeight());
        listView.setClipBounds(tmpClipRect);
    }



    /* Blur */

    private final @Nullable DownscaleScrollableNoiseSuppressor scrollableViewNoiseSuppressor;
    private final @Nullable BlurredBackgroundSourceRenderNode iBlur3SourceGlassFrosted;

    private IBlur3Capture iBlur3Capture;
    private boolean iBlur3Invalidated;

    private final ArrayList<RectF> iBlur3Positions = new ArrayList<>();
    private final RectF iBlur3PositionActionBar = new RectF(); {
        iBlur3Positions.add(iBlur3PositionActionBar);
    }

    private void blur3_InvalidateBlur() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || scrollableViewNoiseSuppressor == null) {
            return;
        }

        final int additionalList = dp(48);
        final int additionalSearch = dp(DialogsActivity.SEARCH_FIELD_HEIGHT) + maxSize;

        iBlur3PositionActionBar.set(0, -additionalList, fragmentView.getMeasuredWidth(), actionBar.getMeasuredHeight() + additionalList + additionalSearch );
        scrollableViewNoiseSuppressor.setupRenderNodes(iBlur3Positions, 1);
        scrollableViewNoiseSuppressor.invalidateResultRenderNodes(iBlur3Capture, fragmentView.getMeasuredWidth(), fragmentView.getMeasuredHeight());
    }
}
