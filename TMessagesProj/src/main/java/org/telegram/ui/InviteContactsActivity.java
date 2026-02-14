/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.dpf2;
import static org.telegram.messenger.LocaleController.getString;
import static org.telegram.ui.ActionBar.Theme.multAlpha;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.FrameLayout;
import android.widget.ImageView;
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
import org.telegram.ui.Adapters.FiltersView;
import org.telegram.ui.Cells.GroupCreateSectionCell;
import org.telegram.ui.Cells.InviteUserCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Components.AnimatedFloat;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.EditTextBoldCursor;
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
            this, CubicBezierInterpolator.EASE_OUT_QUINT, 350, dp(40 - 3));

    private FragmentFloatingButton floatingButton;
    private SearchField searchField;

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
            int y = dp(6);
            int allCurrentLineWidth = 0;
            int allY = dp(6);
            int maxTy = 0;
            int lineCount = 1;
            int x;
            for (int a = 0; a < count; a++) {
                View child = getChildAt(a);
                if (!(child instanceof GroupCreateSpan)) {
                    continue;
                }
                child.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(dp(28), MeasureSpec.EXACTLY));
                if (child != removingSpan && currentLineWidth + child.getMeasuredWidth() > maxWidth) {
                    y += dp(28 + 6);
                    currentLineWidth = 0;
                    lineCount++;
                }
                if (allCurrentLineWidth + child.getMeasuredWidth() > maxWidth) {
                    allY += dp(28 + 6);
                    allCurrentLineWidth = 0;
                }
                x = dp(5) + currentLineWidth;
                if (!animationStarted) {
                    if (child == removingSpan) {
                        child.setTranslationX(dp(5) + allCurrentLineWidth);
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
            boolean over = (maxTy > 0 ? maxTy + dp(28 + 6) : 0) > maxSize - dp(12);
            if (maxWidth - currentLineWidth < minWidth && !over) {
                currentLineWidth = 0;
                y += dp(28 + 6);
                lineCount++;
                maxTy = Math.max(maxTy, y);
            }
            over = (maxTy > 0 ? maxTy + dp(28 + 6) : 0) > maxSize - dp(12);
            if (!animationStarted) {
                int currentHeight = allY + dp(28);
                fieldY = y;
                if (currentAnimation != null) {
                    containerHeight = y + dp(28);
                    currentAnimation.playTogether(animators);
                    currentAnimation.start();
                    animationStarted = true;
                } else {
                    containerHeight = currentHeight;
                }
            }
            final float h = over ? maxSize - dp(12) : Math.max(dp(40 - 3), Math.min(maxTy > 0 ? maxTy + dp(28 + 3) : 0, maxSize - dp(12)));
            animatorSelectorContainerHeight.animateTo(h);
            if (searchField != null) {
                searchField.setSpansBounds(Math.max(0, count - (removingSpan != null ? 1 : 0)), Math.max(0, maxTy - dp(6)), currentLineWidth, over);
            }
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
            currentAnimation.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
            currentAnimation.setDuration(320);
            addingSpan = span;
            animators.clear();
            animators.add(ObjectAnimator.ofFloat(addingSpan, View.SCALE_X, 0.75f, 1.0f));
            animators.add(ObjectAnimator.ofFloat(addingSpan, View.SCALE_Y, 0.75f, 1.0f));
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
            currentAnimation.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
            currentAnimation.setDuration(320);
            removingSpan = span;
            animators.clear();
            animators.add(ObjectAnimator.ofFloat(removingSpan, View.SCALE_X, 1.0f, 0.75f));
            animators.add(ObjectAnimator.ofFloat(removingSpan, View.SCALE_Y, 1.0f, 0.75f));
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
//                ((MarginLayoutParams) headerShadowView.getLayoutParams()).topMargin = actionBar.getMeasuredHeight();
//                ((MarginLayoutParams) searchField.getLayoutParams()).topMargin = actionBar.getMeasuredHeight();
//                ((MarginLayoutParams) scrollView.getLayoutParams()).topMargin = actionBar.getMeasuredHeight();
//                scrollView.getLayoutParams().height = maxSize;
                searchField.getLayoutParams().height = maxSize + dp(12 + 6);

                checkUi_listViewPadding();
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            }

            @Override
            protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
                super.onLayout(changed, left, top, right, bottom);

                checkUi_floatingButton();
                checkUi_searchFieldY();
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
        scrollView.addView(spansContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 108));

        searchField = new SearchField(context, scrollView);

        FlickerLoadingView flickerLoadingView = new FlickerLoadingView(context);
        flickerLoadingView.setViewType(FlickerLoadingView.USERS_TYPE);
        flickerLoadingView.showDate(false);

        emptyView = new StickerEmptyView(context, flickerLoadingView, StickerEmptyView.STICKER_TYPE_NO_CONTACTS);
        emptyView.addView(flickerLoadingView, 0);
        emptyView.setAnimateLayoutChange(true);
        emptyView.title.setText(LocaleController.getString(R.string.NoContacts));
        emptyView.subtitle.setText("");
        emptyView.showProgress(ContactsController.getInstance(currentAccount).isLoadingContacts());

        contentView.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundGray));
        contentView.addView(emptyView);

        layoutManager = new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false);

        adapter = new InviteAdapter(context);

        listView = new RecyclerListView(context);
        listView.setSections(true);
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
                GroupCreateSpan span = new GroupCreateSpan(getContext(), null, contact, true, resourceProvider);
                spansContainer.addSpan(span);
                span.setOnClickListener(InviteContactsActivity.this);
            }
            updateHint();
            if (searching || searchWas) {
//                AndroidUtilities.showKeyboard(searchField.editText);
            } else {
                cell.setChecked(selectedSpan == null, true);
            }
//            if (searchField.editText.length() > 0) {
//                searchField.editText.setText(null);
//            }
        });
        listView.setOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                final int firstVisibleItem = layoutManager.findFirstVisibleItemPosition();
                final View topChild = listView.getChildAt(0);
                final int firstViewTop = topChild != null ? topChild.getTop() : 0;

//                final boolean shadowVisible = !(firstVisibleItem == 0 && firstViewTop >= listView.getPaddingTop());
//                headerShadowView.setShadowVisible(shadowVisible, true);

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

        actionBar.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundGray));
        iBlur3Capture = new ViewGroupPartRenderer(listView, contentView, listView::drawChild);
        listView.addEdgeEffectListener(() -> listView.postOnAnimation(() -> {
            blur3_InvalidateBlur();
        }));

        checkUi_emptyViewVisible();

        contentView.addView(floatingButton, FragmentFloatingButton.createDefaultLayoutParams());
        contentView.addView(actionBar);
        contentView.addView(searchField, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP, 0, 0, 0, 0));

        if (LaunchActivity.instance != null) {
            LaunchActivity.instance.getRootAnimatedInsetsListener().subscribeToWindowInsetsAnimation(this);
        }
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

    private class SearchField extends FrameLayout implements Theme.Colorable {

        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();

        private final ImageView iconView;
        private final EditTextBoldCursor editText;
        private GradientDrawable gradient;

        public SearchField(Context context, ScrollView scrollView) {
            super(context);
            setPadding(dp(12), dp(3), dp(12), dp(3));
            setClipChildren(false);
            setClipToPadding(false);

            iconView = new ImageView(context);
            iconView.setImageResource(R.drawable.outline_search_1_24);
            iconView.setColorFilter(new PorterDuffColorFilter(Theme.multAlpha(getThemedColor(Theme.key_windowBackgroundWhiteBlackText), 0.6f), PorterDuff.Mode.SRC_IN));
            addView(iconView, LayoutHelper.createFrame(24, 24, Gravity.TOP | Gravity.LEFT, 11, 8, 11, 8));

            scrollView.setClipChildren(true);
            addView(scrollView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL, 0, 0, 0, 40));

            editText = new EditTextBoldCursor(context) {
                @Override
                public boolean onKeyDown(int keyCode, KeyEvent event) {
                    if (keyCode == KeyEvent.KEYCODE_DEL && editText.length() == 0 && !allSpans.isEmpty()) {
                        final GroupCreateSpan span = allSpans.get(allSpans.size() - 1);
                        spansContainer.removeSpan(span);
                        updateHint();
                        checkVisibleRows();
                        return true;
                    }
                    return super.onKeyDown(keyCode, event);
                }
            };
            editText.setHint(LocaleController.getString(R.string.Search));
            editText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            editText.setCursorWidth(1.5f);
            editText.setInputType(InputType.TYPE_TEXT_VARIATION_FILTER | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
            editText.setSingleLine(true);
            editText.setBackground(null);
            editText.setVerticalScrollBarEnabled(false);
            editText.setHorizontalScrollBarEnabled(false);
            editText.setClipToPadding(true);
            editText.setPadding(dp(46), 0, dp(46), 0);
            editText.setEllipsizeByGradient(true);
            editText.setImeOptions(EditorInfo.IME_ACTION_DONE | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
            editText.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL);
            editText.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {

                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {

                }

                @Override
                public void afterTextChanged(Editable s) {
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                editText.setLocalePreferredLineHeightForMinimumUsed(false);
            }
            editText.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
            editText.setHintTextColor(getThemedColor(Theme.key_windowBackgroundWhiteHintText));
            addView(editText, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 40, Gravity.TOP | Gravity.FILL_HORIZONTAL, 0, 0, 0, 0));

            updateColors();
        }

        @Override
        public void updateColors() {
            final int bg = getThemedColor(Theme.key_windowBackgroundGray);
            gradient = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[] { Theme.multAlpha(bg, 1), Theme.multAlpha(bg, 0) });
        }

        @Override
        protected void dispatchDraw(@NonNull Canvas canvas) {
            paint.setShadowLayer(dpf2(2), 0, dpf2(0.33f), 0x11000000);
            paint.setColor(getThemedColor(Theme.key_windowBackgroundWhite));

            AndroidUtilities.rectTmp.set(dp(12), dp(3), getWidth() - dp(12), dp(3) + animatorSelectorContainerHeight.getFactor() + dp(3));
            path.rewind();
            path.addRoundRect(AndroidUtilities.rectTmp, dp(20), dp(20), Path.Direction.CW);

            if (gradient != null) {
                gradient.setBounds(0, 0, getWidth(), Math.min(getHeight(), (int) animatorSelectorContainerHeight.getFactor() + dp(24)));
                gradient.draw(canvas);
            }

            canvas.save();
            canvas.drawPath(path, paint);
            canvas.clipPath(path);
            super.dispatchDraw(canvas);
            canvas.restore();
        }

        @Override
        protected boolean drawChild(@NonNull Canvas canvas, View child, long drawingTime) {
            if (child == scrollView) {
                canvas.save();
                canvas.clipRect(
                    child.getX(),
                    child.getY(),
                    child.getX() + child.getWidth(),
                    child.getY() + child.getHeight()
                );
                boolean r = super.drawChild(canvas, child, drawingTime);
                canvas.restore();
                return r;
            }
            return super.drawChild(canvas, child, drawingTime);
        }

        public void setSpansBounds(int spansCount, float height, float lastLineWidth, boolean over) {
            final boolean showIcon = spansCount <= 0;
            iconView.animate()
                .alpha(showIcon ? 1.0f : 0.0f)
                .scaleX(showIcon ? 1.0f : 0.5f)
                .scaleY(showIcon ? 1.0f : 0.5f)
                .setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT)
                .setDuration(320)
                .start();
            editText.animate()
                .translationY(over ? getHeight() - getPaddingTop() - getPaddingBottom() - dp(44) : height)
                .translationX(over ? dp(-36) : spansCount <= 0 ? 0 : Math.max(-dp(36), lastLineWidth - dp(46)))
                .setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT)
                .setDuration(320)
                .start();

            scrollView.post(() -> {
                scrollView.smoothScrollTo(0, (int) height);
            });
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(
                MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(dp(144), MeasureSpec.EXACTLY)
            );
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
                textCell.setColors(Theme.key_windowBackgroundWhiteBlackText, Theme.key_windowBackgroundWhiteBlackText);
                textCell.setTextAndValueAndIcon(LocaleController.getString(R.string.ShareTelegram2), "", R.drawable.msg_shareout, false);
                view = textCell;
            } else if (viewType == 2) {
                view = new ShadowSectionCell(context, 12);
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
            checkUi_emptyViewVisible();
        }
    }

    private void checkUi_emptyViewVisible() {
        if (adapter == null) {
            return;
        }

        if (!searching) {
            final int count = adapter.getItemCount();
            emptyView.setVisibility(count == 2 ? View.VISIBLE : View.INVISIBLE);
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

        themeDescriptions.add(new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundGray));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_LISTGLOWCOLOR, null, null, null, null, Theme.key_actionBarDefault));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_ITEMSCOLOR, null, null, null, null, Theme.key_actionBarDefaultIcon));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_TITLECOLOR, null, null, null, null, Theme.key_actionBarDefaultTitle));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_actionBarDefaultSelector));

//        themeDescriptions.add(new ThemeDescription(scrollView, ThemeDescription.FLAG_LISTGLOWCOLOR, null, null, null, null, Theme.key_windowBackgroundWhite));

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

    @Override
    public void onInsets(int left, int top, int right, int bottom) {
        navigationBarHeight = bottom;

        checkUi_listViewPadding();
        checkUi_floatingButton();
    }

    @Override
    public void onFactorChanged(int id, float factor, float fraction, FactorAnimator callee) {
        if (id == ANIMATOR_ID_SELECTED_CONTAINER_HEIGHT) {
            final int oldPaddingTop = listView.getPaddingTop();

            searchField.invalidate();
            checkUi_listViewPadding();
            checkUi_searchFieldY();

            final int newPaddingTop = listView.getPaddingTop();
            if (newPaddingTop != oldPaddingTop/* && !headerShadowView.isShadowVisible()*/) {
               listView.scrollBy(0, oldPaddingTop - newPaddingTop);
            }
        }
    }

    @Override
    public boolean isSupportEdgeToEdge() {
        return true;
    }

    private void checkUi_searchFieldY() {
        searchField.setTranslationY(actionBar.getMeasuredHeight());
    }

    private void checkUi_listViewPadding() {
        final int top = (
            dp(ADDITIONAL_LIST_HEIGHT_DP)
            + actionBar.getMeasuredHeight()
            + dp(4)
            + ((int) animatorSelectorContainerHeight.getFactor())
        );

        listView.setPadding(0, top, 0, navigationBarHeight);
        emptyView.setPadding(0, 0, 0, navigationBarHeight);
    }

    private void checkUi_floatingButton() {
        if (floatingButton != null) {
            floatingButton.setTranslationY(-Math.max(navigationBarHeight, imeInsetAnimatedHeight));
        }
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
