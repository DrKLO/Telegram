/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.StateListAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.Editable;
import android.text.InputType;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.util.TypedValue;
import android.view.ActionMode;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.inputmethod.EditorInfo;
import android.widget.ImageView;
import android.widget.ScrollView;

import androidx.annotation.Keep;
import androidx.collection.LongSparseArray;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Adapters.SearchAdapterHelper;
import org.telegram.ui.Business.BusinessRecipientsHelper;
import org.telegram.ui.Cells.GraySectionCell;
import org.telegram.ui.Cells.GroupCreateUserCell;
import org.telegram.ui.Components.AnimatedAvatarContainer;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.ColoredImageSpan;
import org.telegram.ui.Components.CombinedDrawable;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.EmptyTextProgressView;
import org.telegram.ui.Components.FlickerLoadingView;
import org.telegram.ui.Components.GroupCreateSpan;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.Premium.LimitReachedBottomSheet;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.StickerEmptyView;

import java.util.ArrayList;

public class UsersSelectActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate, View.OnClickListener {

    public final static int TYPE_FILTER = 0;
    public final static int TYPE_AUTO_DELETE_EXISTING_CHATS = 1;
    public final static int TYPE_PRIVATE = 2;

    private ScrollView scrollView;
    private SpansContainer spansContainer;
    private EditTextBoldCursor editText;
    private RecyclerListView listView;
    private FlickerLoadingView progressView;
    private StickerEmptyView emptyView;
    private GroupCreateAdapter adapter;
    private FilterUsersActivityDelegate delegate;
    private AnimatorSet currentDoneButtonAnimation;
    private ImageView floatingButton;
    private boolean ignoreScrollEvent;
    private int selectedCount;

    private int type;
    private int containerHeight;

    AnimatedAvatarContainer animatedAvatarContainer;

    public boolean noChatTypes;
    public boolean allowSelf;
    public boolean doNotNewChats;
    private boolean isInclude;
    private int filterFlags;
    private ArrayList<Long> initialIds;

    private boolean searchWas;
    private boolean searching;
    private LongSparseArray<GroupCreateSpan> selectedContacts = new LongSparseArray<>();
    private ArrayList<GroupCreateSpan> allSpans = new ArrayList<>();
    private GroupCreateSpan currentDeletingSpan;

    private int fieldY;
    private int ttlPeriod;

    private final static int done_button = 1;

    public void setTtlPeriod(int selectedTime) {
        ttlPeriod = selectedTime;
    }

    private static class ItemDecoration extends RecyclerView.ItemDecoration {

        private boolean single;
        private int skipRows;

        public void setSingle(boolean value) {
            single = value;
        }

        @Override
        public void onDraw(Canvas canvas, RecyclerView parent, RecyclerView.State state) {
            int width = parent.getWidth();
            int top;
            int childCount = parent.getChildCount() - (single ? 0 : 1);
            for (int i = 0; i < childCount; i++) {
                View child = parent.getChildAt(i);
                View nextChild = i < childCount - 1 ? parent.getChildAt(i + 1) : null;
                int position = parent.getChildAdapterPosition(child);
                if (position < skipRows || child instanceof GraySectionCell || nextChild instanceof GraySectionCell) {
                    continue;
                }
                top = child.getBottom();
                canvas.drawLine(LocaleController.isRTL ? 0 : AndroidUtilities.dp(72), top, width - (LocaleController.isRTL ? AndroidUtilities.dp(72) : 0), top, Theme.dividerPaint);
            }
        }

        @Override
        public void getItemOffsets(android.graphics.Rect outRect, View view, RecyclerView parent, RecyclerView.State state) {
            super.getItemOffsets(outRect, view, parent, state);
            /*int position = parent.getChildAdapterPosition(view);
            if (position == 0 || !searching && position == 1) {
                return;
            }*/
            outRect.top = 1;
        }
    }

    public interface FilterUsersActivityDelegate {
        void didSelectChats(ArrayList<Long> ids, int flags);
    }

    private class SpansContainer extends ViewGroup {

        private AnimatorSet currentAnimation;
        private boolean animationStarted;
        private ArrayList<Animator> animators = new ArrayList<>();
        private View addingSpan;
        private View removingSpan;

        public SpansContainer(Context context) {
            super(context);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int count = getChildCount();
            int width = MeasureSpec.getSize(widthMeasureSpec);
            int maxWidth = width - AndroidUtilities.dp(26);
            int currentLineWidth = 0;
            int y = AndroidUtilities.dp(10);
            int allCurrentLineWidth = 0;
            int allY = AndroidUtilities.dp(10);
            int x;
            for (int a = 0; a < count; a++) {
                View child = getChildAt(a);
                if (!(child instanceof GroupCreateSpan)) {
                    continue;
                }
                child.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(32), MeasureSpec.EXACTLY));
                if (child != removingSpan && currentLineWidth + child.getMeasuredWidth() > maxWidth) {
                    y += child.getMeasuredHeight() + AndroidUtilities.dp(8);
                    currentLineWidth = 0;
                }
                if (allCurrentLineWidth + child.getMeasuredWidth() > maxWidth) {
                    allY += child.getMeasuredHeight() + AndroidUtilities.dp(8);
                    allCurrentLineWidth = 0;
                }
                x = AndroidUtilities.dp(13) + currentLineWidth;
                if (!animationStarted) {
                    if (child == removingSpan) {
                        child.setTranslationX(AndroidUtilities.dp(13) + allCurrentLineWidth);
                        child.setTranslationY(allY);
                    } else if (removingSpan != null) {
                        if (child.getTranslationX() != x) {
                            animators.add(ObjectAnimator.ofFloat(child, View.TRANSLATION_X, x));
                        }
                        if (child.getTranslationY() != y) {
                            animators.add(ObjectAnimator.ofFloat(child, View.TRANSLATION_Y, y));
                        }
                    } else {
                        child.setTranslationX(x);
                        child.setTranslationY(y);
                    }
                }
                if (child != removingSpan) {
                    currentLineWidth += child.getMeasuredWidth() + AndroidUtilities.dp(9);
                }
                allCurrentLineWidth += child.getMeasuredWidth() + AndroidUtilities.dp(9);
            }
            int minWidth;
            if (AndroidUtilities.isTablet()) {
                minWidth = AndroidUtilities.dp(530 - 26 - 18 - 57 * 2) / 3;
            } else {
                minWidth = (Math.min(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y) - AndroidUtilities.dp(26 + 18 + 57 * 2)) / 3;
            }
            if (maxWidth - currentLineWidth < minWidth) {
                currentLineWidth = 0;
                y += AndroidUtilities.dp(32 + 8);
            }
            if (maxWidth - allCurrentLineWidth < minWidth) {
                allY += AndroidUtilities.dp(32 + 8);
            }
            editText.measure(MeasureSpec.makeMeasureSpec(maxWidth - currentLineWidth, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(32), MeasureSpec.EXACTLY));
            if (!animationStarted) {
                int currentHeight = allY + AndroidUtilities.dp(32 + 10);
                int fieldX = currentLineWidth + AndroidUtilities.dp(16);
                fieldY = y;
                if (currentAnimation != null) {
                    int resultHeight = y + AndroidUtilities.dp(32 + 10);
                    if (containerHeight != resultHeight) {
                        animators.add(ObjectAnimator.ofInt(UsersSelectActivity.this, "containerHeight", resultHeight));
                    }
                    if (editText.getTranslationX() != fieldX) {
                        animators.add(ObjectAnimator.ofFloat(editText, View.TRANSLATION_X, fieldX));
                    }
                    if (editText.getTranslationY() != fieldY) {
                        animators.add(ObjectAnimator.ofFloat(editText, View.TRANSLATION_Y, fieldY));
                    }
                    editText.setAllowDrawCursor(false);
                    currentAnimation.playTogether(animators);
                    currentAnimation.start();
                    animationStarted = true;
                } else {
                    containerHeight = currentHeight;
                    editText.setTranslationX(fieldX);
                    editText.setTranslationY(fieldY);
                }
            } else if (currentAnimation != null) {
                if (!ignoreScrollEvent && removingSpan == null) {
                    editText.bringPointIntoView(editText.getSelectionStart());
                }
            }
            setMeasuredDimension(width, containerHeight);
        }

        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            int count = getChildCount();
            for (int a = 0; a < count; a++) {
                View child = getChildAt(a);
                child.layout(0, 0, child.getMeasuredWidth(), child.getMeasuredHeight());
            }
        }

        public void addSpan(final GroupCreateSpan span, boolean animated) {
            allSpans.add(span);
            long uid = span.getUid();
            if (uid > Long.MIN_VALUE + 7) {
                selectedCount++;
            }
            selectedContacts.put(uid, span);

            editText.setHintVisible(false, TextUtils.isEmpty(editText.getText()));
            if (currentAnimation != null && currentAnimation.isRunning()) {
                currentAnimation.setupEndValues();
                currentAnimation.cancel();
            }
            animationStarted = false;
            if (animated) {
                currentAnimation = new AnimatorSet();
                currentAnimation.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animator) {
                        addingSpan = null;
                        currentAnimation = null;
                        animationStarted = false;
                        editText.setAllowDrawCursor(true);
                    }
                });
                currentAnimation.setDuration(150);
                addingSpan = span;
                animators.clear();
                animators.add(ObjectAnimator.ofFloat(addingSpan, View.SCALE_X, 0.01f, 1.0f));
                animators.add(ObjectAnimator.ofFloat(addingSpan, View.SCALE_Y, 0.01f, 1.0f));
                animators.add(ObjectAnimator.ofFloat(addingSpan, View.ALPHA, 0.0f, 1.0f));
            }
            addView(span);
        }

        public void removeSpan(final GroupCreateSpan span) {
            ignoreScrollEvent = true;
            long uid = span.getUid();
            if (uid > Long.MIN_VALUE + 7) {
                selectedCount--;
            }
            selectedContacts.remove(uid);
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
                    removingSpan = null;
                    currentAnimation = null;
                    animationStarted = false;
                    editText.setAllowDrawCursor(true);
                    if (allSpans.isEmpty()) {
                        editText.setHintVisible(true, true);
                    }
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



    public UsersSelectActivity(boolean include, ArrayList<Long> arrayList, int flags) {
        super();
        isInclude = include;
        filterFlags = flags;
        initialIds = arrayList;
        type = TYPE_FILTER;
        allowSelf = type != TYPE_AUTO_DELETE_EXISTING_CHATS;
    }

    public UsersSelectActivity asPrivateChats() {
        type = TYPE_PRIVATE;
        allowSelf = false;
        return this;
    }

    public UsersSelectActivity(int type) {
        super();
        this.type = type;
        allowSelf = type != TYPE_AUTO_DELETE_EXISTING_CHATS;
    }

    @Override
    public boolean onFragmentCreate() {
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.contactsDidLoad);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.updateInterfaces);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.chatDidCreated);
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.contactsDidLoad);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.updateInterfaces);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.chatDidCreated);
    }

    @Override
    public void onClick(View v) {
        GroupCreateSpan span = (GroupCreateSpan) v;
        if (span.isDeleting()) {
            currentDeletingSpan = null;
            spansContainer.removeSpan(span);
            if (type == TYPE_PRIVATE) {
                if (span.getUid() == Long.MIN_VALUE + 8) {
                    filterFlags &= ~BusinessRecipientsHelper.PRIVATE_FLAG_EXISTING_CHATS;
                } else if (span.getUid() == Long.MIN_VALUE + 9) {
                    filterFlags &= ~BusinessRecipientsHelper.PRIVATE_FLAG_NEW_CHATS;
                } else if (span.getUid() == Long.MIN_VALUE) {
                    filterFlags &= ~BusinessRecipientsHelper.PRIVATE_FLAG_CONTACTS;
                } else if (span.getUid() == Long.MIN_VALUE + 1) {
                    filterFlags &= ~BusinessRecipientsHelper.PRIVATE_FLAG_NON_CONTACTS;
                }
            } else {
                if (span.getUid() == Long.MIN_VALUE) {
                    filterFlags &= ~MessagesController.DIALOG_FILTER_FLAG_CONTACTS;
                } else if (span.getUid() == Long.MIN_VALUE + 1) {
                    filterFlags &= ~MessagesController.DIALOG_FILTER_FLAG_NON_CONTACTS;
                } else if (span.getUid() == Long.MIN_VALUE + 2) {
                    filterFlags &= ~MessagesController.DIALOG_FILTER_FLAG_GROUPS;
                } else if (span.getUid() == Long.MIN_VALUE + 3) {
                    filterFlags &= ~MessagesController.DIALOG_FILTER_FLAG_CHANNELS;
                } else if (span.getUid() == Long.MIN_VALUE + 4) {
                    filterFlags &= ~MessagesController.DIALOG_FILTER_FLAG_BOTS;
                } else if (span.getUid() == Long.MIN_VALUE + 5) {
                    filterFlags &= ~MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_MUTED;
                } else if (span.getUid() == Long.MIN_VALUE + 6) {
                    filterFlags &= ~MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_READ;
                } else if (span.getUid() == Long.MIN_VALUE + 7) {
                    filterFlags &= ~MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_ARCHIVED;
                }
            }
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

        if (type == TYPE_AUTO_DELETE_EXISTING_CHATS) {
            animatedAvatarContainer = new AnimatedAvatarContainer(getContext());
            actionBar.addView(animatedAvatarContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, 0, LocaleController.isRTL ? 0 : 64, 0,  LocaleController.isRTL ? 64 : 0, 0));
            actionBar.setAllowOverlayTitle(false);
        }
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        if (type == TYPE_FILTER || type == TYPE_PRIVATE) {
            if (isInclude) {
                actionBar.setTitle(LocaleController.getString("FilterAlwaysShow", R.string.FilterAlwaysShow));
            } else {
                actionBar.setTitle(LocaleController.getString("FilterNeverShow", R.string.FilterNeverShow));
            }
        } else if (type == TYPE_AUTO_DELETE_EXISTING_CHATS){
            updateHint();
        }
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                } else if (id == done_button) {
                    onDonePressed(true);
                }
            }
        });

        fragmentView = new ViewGroup(context) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                int width = MeasureSpec.getSize(widthMeasureSpec);
                int height = MeasureSpec.getSize(heightMeasureSpec);
                setMeasuredDimension(width, height);
                int maxSize;
                if (AndroidUtilities.isTablet() || height > width) {
                    maxSize = AndroidUtilities.dp(144);
                } else {
                    maxSize = AndroidUtilities.dp(56);
                }

                scrollView.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(maxSize, MeasureSpec.AT_MOST));
                listView.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height - scrollView.getMeasuredHeight(), MeasureSpec.EXACTLY));
                emptyView.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height - scrollView.getMeasuredHeight(), MeasureSpec.EXACTLY));
                progressView.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height - scrollView.getMeasuredHeight(), MeasureSpec.EXACTLY));
                if (floatingButton != null) {
                    int w = AndroidUtilities.dp(Build.VERSION.SDK_INT >= 21 ? 56 : 60);
                    floatingButton.measure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY));
                }
            }

            @Override
            protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
                scrollView.layout(0, 0, scrollView.getMeasuredWidth(), scrollView.getMeasuredHeight());
                listView.layout(0, scrollView.getMeasuredHeight(), listView.getMeasuredWidth(), scrollView.getMeasuredHeight() + listView.getMeasuredHeight());
                emptyView.layout(0, scrollView.getMeasuredHeight(), emptyView.getMeasuredWidth(), scrollView.getMeasuredHeight() + emptyView.getMeasuredHeight());
                progressView.layout(0, scrollView.getMeasuredHeight(), emptyView.getMeasuredWidth(), scrollView.getMeasuredHeight() + progressView.getMeasuredHeight());
                if (floatingButton != null) {
                    int l = LocaleController.isRTL ? AndroidUtilities.dp(14) : (right - left) - AndroidUtilities.dp(14) - floatingButton.getMeasuredWidth();
                    int t = bottom - top - AndroidUtilities.dp(14) - floatingButton.getMeasuredHeight();
                    floatingButton.layout(l, t, l + floatingButton.getMeasuredWidth(), t + floatingButton.getMeasuredHeight());
                }
            }

            @Override
            protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
                boolean result = super.drawChild(canvas, child, drawingTime);
                if (child == listView || child == emptyView) {
                    parentLayout.drawHeaderShadow(canvas, scrollView.getMeasuredHeight());
                }
                return result;
            }
        };
        ViewGroup frameLayout = (ViewGroup) fragmentView;

        scrollView = new ScrollView(context) {
            @Override
            public boolean requestChildRectangleOnScreen(View child, Rect rectangle, boolean immediate) {
                if (ignoreScrollEvent) {
                    ignoreScrollEvent = false;
                    return false;
                }
                rectangle.offset(child.getLeft() - child.getScrollX(), child.getTop() - child.getScrollY());
                rectangle.top += fieldY + AndroidUtilities.dp(20);
                rectangle.bottom += fieldY + AndroidUtilities.dp(50);
                return super.requestChildRectangleOnScreen(child, rectangle, immediate);
            }
        };
        scrollView.setVerticalScrollBarEnabled(false);
        AndroidUtilities.setScrollViewEdgeEffectColor(scrollView, Theme.getColor(Theme.key_windowBackgroundWhite));
        frameLayout.addView(scrollView);

        spansContainer = new SpansContainer(context);
        scrollView.addView(spansContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        spansContainer.setOnClickListener(v -> {
            editText.clearFocus();
            editText.requestFocus();
            AndroidUtilities.showKeyboard(editText);
        });

        editText = new EditTextBoldCursor(context) {
            @Override
            public boolean onTouchEvent(MotionEvent event) {
                if (currentDeletingSpan != null) {
                    currentDeletingSpan.cancelDeleteAnimation();
                    currentDeletingSpan = null;
                }
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    if (!AndroidUtilities.showKeyboard(this)) {
                        clearFocus();
                        requestFocus();
                    }
                }
                return super.onTouchEvent(event);
            }
        };
        editText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        editText.setHintColor(Theme.getColor(Theme.key_groupcreate_hintText));
        editText.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        editText.setCursorColor(Theme.getColor(Theme.key_groupcreate_cursor));
        editText.setCursorWidth(1.5f);
        editText.setInputType(InputType.TYPE_TEXT_VARIATION_FILTER | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        editText.setSingleLine(true);
        editText.setBackgroundDrawable(null);
        editText.setVerticalScrollBarEnabled(false);
        editText.setHorizontalScrollBarEnabled(false);
        editText.setTextIsSelectable(false);
        editText.setPadding(0, 0, 0, 0);
        editText.setImeOptions(EditorInfo.IME_ACTION_DONE | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        editText.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL);
        spansContainer.addView(editText);
        editText.setHintText(LocaleController.getString("SearchForPeopleAndGroups", R.string.SearchForPeopleAndGroups));

        editText.setCustomSelectionActionModeCallback(new ActionMode.Callback() {
            public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                return false;
            }

            public void onDestroyActionMode(ActionMode mode) {

            }

            public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                return false;
            }

            public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                return false;
            }
        });
        //editText.setOnEditorActionListener((v, actionId, event) -> actionId == EditorInfo.IME_ACTION_DONE && onDonePressed(true));
        editText.setOnKeyListener(new View.OnKeyListener() {

            private boolean wasEmpty;

            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (keyCode == KeyEvent.KEYCODE_DEL) {
                    if (event.getAction() == KeyEvent.ACTION_DOWN) {
                        wasEmpty = editText.length() == 0;
                    } else if (event.getAction() == KeyEvent.ACTION_UP && wasEmpty && !allSpans.isEmpty()) {
                        GroupCreateSpan span = allSpans.get(allSpans.size() - 1);
                        spansContainer.removeSpan(span);
                        if (type == TYPE_PRIVATE) {
                            if (span.getUid() == Long.MIN_VALUE + 8) {
                                filterFlags &= ~BusinessRecipientsHelper.PRIVATE_FLAG_EXISTING_CHATS;
                            } else if (span.getUid() == Long.MIN_VALUE + 9) {
                                filterFlags &= ~BusinessRecipientsHelper.PRIVATE_FLAG_NEW_CHATS;
                            } else if (span.getUid() == Long.MIN_VALUE) {
                                filterFlags &= ~BusinessRecipientsHelper.PRIVATE_FLAG_CONTACTS;
                            } else if (span.getUid() == Long.MIN_VALUE + 1) {
                                filterFlags &= ~BusinessRecipientsHelper.PRIVATE_FLAG_NON_CONTACTS;
                            }
                        } else {
                            if (span.getUid() == Long.MIN_VALUE) {
                                filterFlags &= ~MessagesController.DIALOG_FILTER_FLAG_CONTACTS;
                            } else if (span.getUid() == Long.MIN_VALUE + 1) {
                                filterFlags &= ~MessagesController.DIALOG_FILTER_FLAG_NON_CONTACTS;
                            } else if (span.getUid() == Long.MIN_VALUE + 2) {
                                filterFlags &= ~MessagesController.DIALOG_FILTER_FLAG_GROUPS;
                            } else if (span.getUid() == Long.MIN_VALUE + 3) {
                                filterFlags &= ~MessagesController.DIALOG_FILTER_FLAG_CHANNELS;
                            } else if (span.getUid() == Long.MIN_VALUE + 4) {
                                filterFlags &= ~MessagesController.DIALOG_FILTER_FLAG_BOTS;
                            } else if (span.getUid() == Long.MIN_VALUE + 5) {
                                filterFlags &= ~MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_MUTED;
                            } else if (span.getUid() == Long.MIN_VALUE + 6) {
                                filterFlags &= ~MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_READ;
                            } else if (span.getUid() == Long.MIN_VALUE + 7) {
                                filterFlags &= ~MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_ARCHIVED;
                            }
                        }
                        updateHint();
                        checkVisibleRows();
                        return true;
                    }
                }
                return false;
            }
        });
        editText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i2, int i3) {

            }

            @Override
            public void afterTextChanged(Editable editable) {
                if (editText.length() != 0) {
                    if (!adapter.searching) {
                        searching = true;
                        searchWas = true;
                        adapter.setSearching(true);
                        listView.setFastScrollVisible(false);
                        listView.setVerticalScrollBarEnabled(true);
                        emptyView.title.setText(LocaleController.getString("NoResult", R.string.NoResult));
                    }
                    emptyView.showProgress(true);
                    adapter.searchDialogs(editText.getText().toString());
                } else {
                    closeSearch();
                }
            }
        });

        progressView = new FlickerLoadingView(context);
        progressView.setViewType(FlickerLoadingView.USERS2_TYPE);
        progressView.showDate(false);
        progressView.setItemsCount(3);
        progressView.setColors(Theme.key_actionBarDefaultSubmenuBackground, Theme.key_listSelector, Theme.key_listSelector);
        frameLayout.addView(progressView);

        emptyView = new StickerEmptyView(context, progressView, StickerEmptyView.STICKER_TYPE_SEARCH) {
            @Override
            public void setVisibility(int visibility) {
                super.setVisibility(visibility);
                if (visibility != View.VISIBLE) {
                    showProgress(false, false);
                }
            }
        };
        emptyView.showProgress(ContactsController.getInstance(currentAccount).isLoadingContacts());
        emptyView.title.setText(LocaleController.getString("NoContacts", R.string.NoContacts));
        frameLayout.addView(emptyView);

        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false);

        listView = new RecyclerListView(context);
        listView.setFastScrollEnabled(RecyclerListView.FastScroll.LETTER_TYPE);
        listView.setEmptyView(emptyView);
        listView.setAdapter(adapter = new GroupCreateAdapter(context));
        listView.setLayoutManager(linearLayoutManager);
        listView.setVerticalScrollBarEnabled(false);
        listView.setVerticalScrollbarPosition(LocaleController.isRTL ? View.SCROLLBAR_POSITION_LEFT : View.SCROLLBAR_POSITION_RIGHT);
        listView.addItemDecoration(new ItemDecoration());
        frameLayout.addView(listView);
        listView.setOnItemClickListener((view, position) -> {
            if (view instanceof GroupCreateUserCell) {
                GroupCreateUserCell cell = (GroupCreateUserCell) view;
                Object object = cell.getObject();
                long id;
                if (object instanceof String) {
                    int flag;
                    if (type == TYPE_PRIVATE) {
                        if (position == 1) {
                            flag = BusinessRecipientsHelper.PRIVATE_FLAG_EXISTING_CHATS;
                            id = Long.MIN_VALUE + 8;
                        } else if (position == 2 && !doNotNewChats) {
                            flag = BusinessRecipientsHelper.PRIVATE_FLAG_NEW_CHATS;
                            id = Long.MIN_VALUE + 9;
                        } else if (position == 2 + (doNotNewChats ? 0 : 1)) {
                            flag = BusinessRecipientsHelper.PRIVATE_FLAG_CONTACTS;
                            id = Long.MIN_VALUE;
                        } else {
                            flag = BusinessRecipientsHelper.PRIVATE_FLAG_NON_CONTACTS;
                            id = Long.MIN_VALUE + 1;
                        }
                    } else if (isInclude) {
                        if (position == 1) {
                            flag = MessagesController.DIALOG_FILTER_FLAG_CONTACTS;
                            id = Long.MIN_VALUE;
                        } else if (position == 2) {
                            flag = MessagesController.DIALOG_FILTER_FLAG_NON_CONTACTS;
                            id = Long.MIN_VALUE + 1;
                        } else if (position == 3) {
                            flag = MessagesController.DIALOG_FILTER_FLAG_GROUPS;
                            id = Long.MIN_VALUE + 2;
                        } else if (position == 4) {
                            flag = MessagesController.DIALOG_FILTER_FLAG_CHANNELS;
                            id = Long.MIN_VALUE + 3;
                        } else {
                            flag = MessagesController.DIALOG_FILTER_FLAG_BOTS;
                            id = Long.MIN_VALUE + 4;
                        }
                    } else {
                        if (position == 1) {
                            flag = MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_MUTED;
                            id = Long.MIN_VALUE + 5;
                        } else if (position == 2) {
                            flag = MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_READ;
                            id = Long.MIN_VALUE + 6;
                        } else {
                            flag = MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_ARCHIVED;
                            id = Long.MIN_VALUE + 7;
                        }
                    }
                    if (cell.isChecked()) {
                        filterFlags &=~ flag;
                    } else {
                        filterFlags |= flag;
                    }
                } else {
                    if (object instanceof TLRPC.User) {
                        id = ((TLRPC.User) object).id;
                    } else if (object instanceof TLRPC.Chat) {
                        id = -((TLRPC.Chat) object).id;
                        if (type == TYPE_AUTO_DELETE_EXISTING_CHATS && !ChatObject.canUserDoAdminAction((TLRPC.Chat) object, ChatObject.ACTION_DELETE_MESSAGES)) {
                            BulletinFactory.of(this).createErrorBulletin(LocaleController.getString("NeedAdminRightForSetAutoDeleteTimer", R.string.NeedAdminRightForSetAutoDeleteTimer)).show();
                            return;
                        }
                    } else {
                        return;
                    }
                }
                boolean exists;
                if (exists = selectedContacts.indexOfKey(id) >= 0) {
                    GroupCreateSpan span = selectedContacts.get(id);
                    spansContainer.removeSpan(span);
                } else {
                    if (!(object instanceof String) && (!getUserConfig().isPremium() && selectedCount >= MessagesController.getInstance(currentAccount).dialogFiltersChatsLimitDefault) || selectedCount >= MessagesController.getInstance(currentAccount).dialogFiltersChatsLimitPremium) {
                        LimitReachedBottomSheet limitReachedBottomSheet = new LimitReachedBottomSheet(this, context, LimitReachedBottomSheet.TYPE_CHATS_IN_FOLDER, currentAccount, null);
                        limitReachedBottomSheet.setCurrentValue(selectedCount);
                        showDialog(limitReachedBottomSheet);
                        return;
                    }
                    if (object instanceof TLRPC.User) {
                        TLRPC.User user = (TLRPC.User) object;
                        MessagesController.getInstance(currentAccount).putUser(user, !searching);
                    } else if (object instanceof TLRPC.Chat) {
                        TLRPC.Chat chat = (TLRPC.Chat) object;
                        MessagesController.getInstance(currentAccount).putChat(chat, !searching);
                    }
                    GroupCreateSpan span = new GroupCreateSpan(editText.getContext(), object);
                    spansContainer.addSpan(span, true);
                    span.setOnClickListener(UsersSelectActivity.this);
                }
                updateHint();
                if (searching || searchWas) {
                    AndroidUtilities.showKeyboard(editText);
                } else {
                    cell.setChecked(!exists, true);
                }
                if (editText.length() > 0) {
                    editText.setText(null);
                }
            }
        });
        listView.setOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                    AndroidUtilities.hideKeyboard(editText);
                }
            }
        });

        floatingButton = new ImageView(context);
        floatingButton.setScaleType(ImageView.ScaleType.CENTER);

        Drawable drawable = Theme.createSimpleSelectorCircleDrawable(AndroidUtilities.dp(56), Theme.getColor(Theme.key_chats_actionBackground), Theme.getColor(Theme.key_chats_actionPressedBackground));
        if (Build.VERSION.SDK_INT < 21) {
            Drawable shadowDrawable = context.getResources().getDrawable(R.drawable.floating_shadow).mutate();
            shadowDrawable.setColorFilter(new PorterDuffColorFilter(0xff000000, PorterDuff.Mode.MULTIPLY));
            CombinedDrawable combinedDrawable = new CombinedDrawable(shadowDrawable, drawable, 0, 0);
            combinedDrawable.setIconSize(AndroidUtilities.dp(56), AndroidUtilities.dp(56));
            drawable = combinedDrawable;
        }
        floatingButton.setBackgroundDrawable(drawable);
        floatingButton.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chats_actionIcon), PorterDuff.Mode.MULTIPLY));
        floatingButton.setImageResource(R.drawable.floating_check);
        if (Build.VERSION.SDK_INT >= 21) {
            StateListAnimator animator = new StateListAnimator();
            animator.addState(new int[]{android.R.attr.state_pressed}, ObjectAnimator.ofFloat(floatingButton, View.TRANSLATION_Z, AndroidUtilities.dp(2), AndroidUtilities.dp(4)).setDuration(200));
            animator.addState(new int[]{}, ObjectAnimator.ofFloat(floatingButton, View.TRANSLATION_Z, AndroidUtilities.dp(4), AndroidUtilities.dp(2)).setDuration(200));
            floatingButton.setStateListAnimator(animator);
            floatingButton.setOutlineProvider(new ViewOutlineProvider() {
                @SuppressLint("NewApi")
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setOval(0, 0, AndroidUtilities.dp(56), AndroidUtilities.dp(56));
                }
            });
        }
        frameLayout.addView(floatingButton);
        floatingButton.setOnClickListener(v -> onDonePressed(true));
        /*floatingButton.setVisibility(View.INVISIBLE);
        floatingButton.setScaleX(0.0f);
        floatingButton.setScaleY(0.0f);
        floatingButton.setAlpha(0.0f);*/
        floatingButton.setContentDescription(LocaleController.getString("Next", R.string.Next));

        for (int position = 1, N = (isInclude ? 5 : 3); position <= N; position++) {
            int id;
            int flag;
            Object object;
            if (type == TYPE_PRIVATE) {
                if (position == 1) {
                    object = "existing_chats";
                    flag = BusinessRecipientsHelper.PRIVATE_FLAG_EXISTING_CHATS;
                } else if (position == 2 && !doNotNewChats) {
                    object = "new_chats";
                    flag = BusinessRecipientsHelper.PRIVATE_FLAG_NEW_CHATS;
                } else if (position == 2 + (doNotNewChats ? 0 : 1)) {
                    object = "contacts";
                    flag = BusinessRecipientsHelper.PRIVATE_FLAG_CONTACTS;
                } else {
                    object = "non_contacts";
                    flag = BusinessRecipientsHelper.PRIVATE_FLAG_NON_CONTACTS;
                }
            } else if (isInclude) {
                if (position == 1) {
                    object = "contacts";
                    flag = MessagesController.DIALOG_FILTER_FLAG_CONTACTS;
                } else if (position == 2) {
                    object = "non_contacts";
                    flag = MessagesController.DIALOG_FILTER_FLAG_NON_CONTACTS;
                } else if (position == 3) {
                    object = "groups";
                    flag = MessagesController.DIALOG_FILTER_FLAG_GROUPS;
                } else if (position == 4) {
                    object = "channels";
                    flag = MessagesController.DIALOG_FILTER_FLAG_CHANNELS;
                } else {
                    object = "bots";
                    flag = MessagesController.DIALOG_FILTER_FLAG_BOTS;
                }
            } else {
                if (position == 1) {
                    object = "muted";
                    flag = MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_MUTED;
                } else if (position == 2) {
                    object = "read";
                    flag = MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_READ;
                } else {
                    object = "archived";
                    flag = MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_ARCHIVED;
                }
            }
            if ((filterFlags & flag) != 0) {
                GroupCreateSpan span = new GroupCreateSpan(editText.getContext(), object);
                spansContainer.addSpan(span, false);
                span.setOnClickListener(UsersSelectActivity.this);
            }
        }
        if (initialIds != null && !initialIds.isEmpty()) {
            TLObject object;
            for (int a = 0, N = initialIds.size(); a < N; a++) {
                Long id = initialIds.get(a);
                if (id > 0) {
                    object = getMessagesController().getUser(id);
                } else {
                    object = getMessagesController().getChat(-id);
                }
                if (object == null) {
                    continue;
                }
                GroupCreateSpan span = new GroupCreateSpan(editText.getContext(), object);
                spansContainer.addSpan(span, false);
                span.setOnClickListener(UsersSelectActivity.this);
            }
        }

        updateHint();
        return fragmentView;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (editText != null) {
            editText.requestFocus();
        }
        AndroidUtilities.requestAdjustResize(getParentActivity(), classGuid);
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.contactsDidLoad) {
            if (emptyView != null) {
                emptyView.showProgress(false);
            }
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

    @Keep
    public void setContainerHeight(int value) {
        containerHeight = value;
        if (spansContainer != null) {
            spansContainer.requestLayout();
        }
    }

    @Keep
    public int getContainerHeight() {
        return containerHeight;
    }

    private void checkVisibleRows() {
        int count = listView.getChildCount();
        for (int a = 0; a < count; a++) {
            View child = listView.getChildAt(a);
            if (child instanceof GroupCreateUserCell) {
                GroupCreateUserCell cell = (GroupCreateUserCell) child;
                Object object = cell.getObject();
                long id;
                if (object instanceof String) {
                    String str = (String) object;
                    switch (str) {
                        case "contacts":
                            id = Long.MIN_VALUE;
                            break;
                        case "non_contacts":
                            id = Long.MIN_VALUE + 1;
                            break;
                        case "groups":
                            id = Long.MIN_VALUE + 2;
                            break;
                        case "channels":
                            id = Long.MIN_VALUE + 3;
                            break;
                        case "bots":
                            id = Long.MIN_VALUE + 4;
                            break;
                        case "muted":
                            id = Long.MIN_VALUE + 5;
                            break;
                        case "read":
                            id = Long.MIN_VALUE + 6;
                            break;
                        case "existing_chats":
                            id = Long.MIN_VALUE + 8;
                            break;
                        case "new_chats":
                            id = Long.MIN_VALUE + 8;
                            break;
                        case "archived":
                        default:
                            id = Long.MIN_VALUE + 7;
                            break;
                    }
                } else if (object instanceof TLRPC.User) {
                    id = ((TLRPC.User) object).id;
                } else if (object instanceof TLRPC.Chat) {
                    id = -((TLRPC.Chat) object).id;
                } else {
                    id = 0;
                }
                if (id != 0) {
                    cell.setChecked(selectedContacts.indexOfKey(id) >= 0, true);
                    cell.setCheckBoxEnabled(true);
                }
            }
        }
    }

    private boolean onDonePressed(boolean alert) {
        /*if (!doneButtonVisible || selectedContacts.size() == 0) {
            return false;
        }*/
        ArrayList<Long> result = new ArrayList<>();
        for (int a = 0; a < selectedContacts.size(); a++) {
            long uid = selectedContacts.keyAt(a);
            if (uid <= Long.MIN_VALUE + 9) {
                continue;
            }
            result.add(selectedContacts.keyAt(a));
        }
        if (delegate != null) {
            delegate.didSelectChats(result, filterFlags);
        }
        finishFragment();
        return true;
    }

    private void closeSearch() {
        searching = false;
        searchWas = false;
        adapter.setSearching(false);
        adapter.searchDialogs(null);
        listView.setFastScrollVisible(true);
        listView.setVerticalScrollBarEnabled(false);
        emptyView.title.setText(LocaleController.getString("NoContacts", R.string.NoContacts));
    }

    private void updateHint() {
        if (type == TYPE_FILTER) {
            int limit = getUserConfig().isPremium() ? getMessagesController().dialogFiltersChatsLimitPremium : getMessagesController().dialogFiltersChatsLimitDefault;
            if (selectedCount == 0) {
                actionBar.setSubtitle(LocaleController.formatString("MembersCountZero", R.string.MembersCountZero, LocaleController.formatPluralString("Chats", limit)));
            } else {
                actionBar.setSubtitle(String.format(LocaleController.getPluralString("MembersCountSelected", selectedCount), selectedCount, limit));
            }
        } else if (type == TYPE_AUTO_DELETE_EXISTING_CHATS) {
            actionBar.setTitle("");
            actionBar.setSubtitle("");

            if (selectedCount == 0) {
                animatedAvatarContainer.getTitle().setText(LocaleController.getString("SelectChats", R.string.SelectChats), true);
                if (ttlPeriod > 0) {
                    animatedAvatarContainer.getSubtitleTextView().setText(LocaleController.getString("SelectChatsForAutoDelete", R.string.SelectChatsForAutoDelete), true);
                } else {
                    animatedAvatarContainer.getSubtitleTextView().setText(LocaleController.getString("SelectChatsForDisableAutoDelete", R.string.SelectChatsForDisableAutoDelete), true);
                }
            } else {
                animatedAvatarContainer.getTitle().setText(LocaleController.formatPluralString("Chats", selectedCount, selectedCount));
                if (ttlPeriod > 0) {
                    animatedAvatarContainer.getSubtitleTextView().setText(LocaleController.getPluralString("SelectChatsForAutoDelete2", selectedCount));
                } else {
                    animatedAvatarContainer.getSubtitleTextView().setText(LocaleController.getPluralString("SelectChatsForDisableAutoDelete2", selectedCount));
                }
            }
        }
    }

    public void setDelegate(FilterUsersActivityDelegate filterUsersActivityDelegate) {
        delegate = filterUsersActivityDelegate;
    }

    public class GroupCreateAdapter extends RecyclerListView.FastScrollAdapter {

        private Context context;
        private ArrayList<Object> searchResult = new ArrayList<>();
        private ArrayList<CharSequence> searchResultNames = new ArrayList<>();
        private SearchAdapterHelper searchAdapterHelper;
        private Runnable searchRunnable;
        private boolean searching;
        private ArrayList<TLObject> contacts = new ArrayList<>();
        private final int usersStartRow;

        public GroupCreateAdapter(Context ctx) {
            context = ctx;

            if (noChatTypes) {
                usersStartRow = 0;
            } else if (type == TYPE_PRIVATE) {
                usersStartRow = 5 + (doNotNewChats ? 0 : 1);
            } else if (type == TYPE_FILTER) {
                if (isInclude) {
                    usersStartRow = 7;
                } else {
                    usersStartRow = 5;
                }
            } else {
                usersStartRow = 0;
            }

            final boolean allowBots = type != TYPE_PRIVATE;
            final boolean allowChats = type != TYPE_PRIVATE;

            boolean hasSelf = false;
            ArrayList<TLRPC.Dialog> dialogs = getMessagesController().getAllDialogs();
            for (int a = 0, N = dialogs.size(); a < N; a++) {
                TLRPC.Dialog dialog = dialogs.get(a);
                if (DialogObject.isEncryptedDialog(dialog.id)) {
                    continue;
                }
                if (DialogObject.isUserDialog(dialog.id)) {
                    TLRPC.User user = getMessagesController().getUser(dialog.id);
                    if (user != null) {
                        if (!allowSelf && UserObject.isUserSelf(user)) {
                            continue;
                        }
                        if (user.bot && !allowBots) {
                            continue;
                        }
                        contacts.add(user);
                        if (UserObject.isUserSelf(user)) {
                            hasSelf = true;
                        }
                    }
                } else {
                    TLRPC.Chat chat = getMessagesController().getChat(-dialog.id);
                    if (!allowChats) continue;
                    if (chat != null) {
                        contacts.add(chat);
                    }
                }
            }
            if (!hasSelf && allowSelf) {
                TLRPC.User user = getMessagesController().getUser(getUserConfig().clientUserId);
                contacts.add(0, user);
            }

            searchAdapterHelper = new SearchAdapterHelper(false);
            searchAdapterHelper.setAllowGlobalResults(false);
            searchAdapterHelper.setDelegate((searchId) -> {
                if (searchRunnable == null && !searchAdapterHelper.isSearchInProgress()) {
                    emptyView.showProgress(false);
                }
                notifyDataSetChanged();
            });
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
            return null;
        }

        @Override
        public int getItemCount() {
            int count;
            if (searching) {
                count = searchResult.size();
                int localServerCount = searchAdapterHelper.getLocalServerSearch().size();
                int globalCount = searchAdapterHelper.getGlobalSearch().size();
                count += localServerCount + globalCount;
                return count;
            } else {
                if (noChatTypes) {
                    count = 0;
                } else if (type == TYPE_PRIVATE) {
                    count = 3 + (doNotNewChats ? 0 : 1);
                } else if (type == TYPE_FILTER) {
                    if (isInclude) {
                        count = 7;
                    } else {
                        count = 5;
                    }
                } else {
                    count = 0;
                }
                count += contacts.size();
            }
            return count;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case 1:
                    view = new GroupCreateUserCell(context, 1, 0, true);
                    break;
                case 2:
                default:
                    view = new GraySectionCell(context);
                    break;
            }
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            switch (holder.getItemViewType()) {
                case 1: {
                    GroupCreateUserCell cell = (GroupCreateUserCell) holder.itemView;
                    Object object = null;
                    CharSequence username = null;
                    CharSequence name = null;
                    if (searching) {
                        int localCount = searchResult.size();
                        int globalCount = searchAdapterHelper.getGlobalSearch().size();
                        int localServerCount = searchAdapterHelper.getLocalServerSearch().size();

                        if (position >= 0 && position < localCount) {
                            object = searchResult.get(position);
                        } else if (position >= localCount && position < localServerCount + localCount) {
                            object = searchAdapterHelper.getLocalServerSearch().get(position - localCount);
                        } else if (position > localCount + localServerCount && position < globalCount + localCount + localServerCount) {
                            object = searchAdapterHelper.getGlobalSearch().get(position - localCount - localServerCount);
                        } else {
                            object = null;
                        }
                        if (object != null) {
                            String objectUserName;
                            if (object instanceof TLRPC.User) {
                                objectUserName = ((TLRPC.User) object).username;
                            } else {
                                objectUserName = ChatObject.getPublicUsername((TLRPC.Chat) object);
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
                        if (position < usersStartRow) {
                            int flag;
                            if (type == TYPE_PRIVATE) {
                                if (position == 1) {
                                    name = LocaleController.getString(R.string.FilterExistingChats);
                                    object = "existing_chats";
                                    flag = BusinessRecipientsHelper.PRIVATE_FLAG_EXISTING_CHATS;
                                } else if (position == 2 && !doNotNewChats) {
                                    name = LocaleController.getString(R.string.FilterNewChats);
                                    object = "new_chats";
                                    flag = BusinessRecipientsHelper.PRIVATE_FLAG_NEW_CHATS;
                                } else if (position == 2 + (doNotNewChats ? 0 : 1)) {
                                    name = LocaleController.getString(R.string.FilterContacts);
                                    object = "contacts";
                                    flag = BusinessRecipientsHelper.PRIVATE_FLAG_CONTACTS;
                                } else {
                                    name = LocaleController.getString(R.string.FilterNonContacts);
                                    object = "non_contacts";
                                    flag = BusinessRecipientsHelper.PRIVATE_FLAG_NON_CONTACTS;
                                }
                            } else if (isInclude) {
                                if (position == 1) {
                                    name = LocaleController.getString("FilterContacts", R.string.FilterContacts);
                                    object = "contacts";
                                    flag = MessagesController.DIALOG_FILTER_FLAG_CONTACTS;
                                } else if (position == 2) {
                                    name = LocaleController.getString("FilterNonContacts", R.string.FilterNonContacts);
                                    object = "non_contacts";
                                    flag = MessagesController.DIALOG_FILTER_FLAG_NON_CONTACTS;
                                } else if (position == 3) {
                                    name = LocaleController.getString("FilterGroups", R.string.FilterGroups);
                                    object = "groups";
                                    flag = MessagesController.DIALOG_FILTER_FLAG_GROUPS;
                                } else if (position == 4) {
                                    name = LocaleController.getString("FilterChannels", R.string.FilterChannels);
                                    object = "channels";
                                    flag = MessagesController.DIALOG_FILTER_FLAG_CHANNELS;
                                } else {
                                    name = LocaleController.getString("FilterBots", R.string.FilterBots);
                                    object = "bots";
                                    flag = MessagesController.DIALOG_FILTER_FLAG_BOTS;
                                }
                            } else {
                                if (position == 1) {
                                    name = LocaleController.getString("FilterMuted", R.string.FilterMuted);
                                    object = "muted";
                                    flag = MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_MUTED;
                                } else if (position == 2) {
                                    name = LocaleController.getString("FilterRead", R.string.FilterRead);
                                    object = "read";
                                    flag = MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_READ;
                                } else {
                                    name = LocaleController.getString("FilterArchived", R.string.FilterArchived);
                                    object = "archived";
                                    flag = MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_ARCHIVED;
                                }
                            }
                            cell.setObject(object, name, null);
                            cell.setChecked((filterFlags & flag) == flag, false);
                            cell.setCheckBoxEnabled(true);
                            return;
                        }
                        object = contacts.get(position - usersStartRow);
                    }
                    long id;
                    if (object instanceof TLRPC.User) {
                        id = ((TLRPC.User) object).id;
                    } else if (object instanceof TLRPC.Chat) {
                        id = -((TLRPC.Chat) object).id;
                    } else {
                        id = 0;
                    }
                    boolean blueText = false;
                    boolean enabled = true;
                    if (type == TYPE_PRIVATE) {

                    } else if (type == TYPE_FILTER) {
                        if (!searching) {
                            StringBuilder builder = new StringBuilder();
                            ArrayList<MessagesController.DialogFilter> filters = getMessagesController().dialogFilters;
                            for (int a = 0, N = filters.size(); a < N; a++) {
                                MessagesController.DialogFilter filter = filters.get(a);
                                if (filter.includesDialog(getAccountInstance(), id)) {
                                    if (builder.length() > 0) {
                                        builder.append(", ");
                                    }
                                    builder.append(filter.name);
                                }
                            }
                            username = builder;
                        }
                    } else {
                        int ttlPeriod = 0;
                        if (getMessagesController().dialogs_dict.get(id) != null) {
                            ttlPeriod = getMessagesController().dialogs_dict.get(id).ttl_period;
                        }
                        if (ttlPeriod > 0) {
                            blueText = true;
                            SpannableStringBuilder spannableStringBuilder = new SpannableStringBuilder();
                            spannableStringBuilder.append("d");
                            spannableStringBuilder.setSpan(new ColoredImageSpan(R.drawable.msg_mini_fireon), 0, 1, 0);
                            spannableStringBuilder.append(LocaleController.formatString("AutoDeleteAfter", R.string.AutoDeleteAfter, LocaleController.formatTTLString(ttlPeriod)).toLowerCase());
                            username = spannableStringBuilder;
                        } else {
                            SpannableStringBuilder spannableStringBuilder = new SpannableStringBuilder();
                            spannableStringBuilder.append("d");
                            spannableStringBuilder.setSpan(new ColoredImageSpan(R.drawable.msg_mini_fireoff), 0, 1, 0);
                            spannableStringBuilder.append(LocaleController.formatString("AutoDeleteDisabled", R.string.AutoDeleteDisabled));
                            username = spannableStringBuilder;
                        }
                        if (object instanceof TLRPC.Chat) {
                            enabled = ChatObject.canUserDoAdminAction((TLRPC.Chat) object, ChatObject.ACTION_DELETE_MESSAGES);
                        }
                    }

                    if (enabled) {
                        cell.setAlpha(1f);
                    } else {
                        cell.setAlpha(0.5f);
                    }
                    cell.setObject(object, name, username);
                    cell.getStatusTextView().setTextColor(Theme.getColor(blueText ? Theme.key_windowBackgroundWhiteBlueText : Theme.key_windowBackgroundWhiteGrayText));
                    if (id != 0) {
                        cell.setChecked(selectedContacts.indexOfKey(id) >= 0, false);
                        cell.setCheckBoxEnabled(true);
                    }
                    break;
                }
                case 2: {
                    GraySectionCell cell = (GraySectionCell) holder.itemView;
                    if (position == 0 && !noChatTypes) {
                        cell.setText(LocaleController.getString("FilterChatTypes", R.string.FilterChatTypes));
                    } else {
                        cell.setText(LocaleController.getString("FilterChats", R.string.FilterChats));
                    }
                    break;
                }
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (searching) {
                return 1;
            } else {
                if (noChatTypes) {
                    if (position == 0) {
                        return 2;
                    }
                } else if (type == TYPE_PRIVATE) {
                    if (position == 0 || position == 4 + (doNotNewChats ? 0 : 1)) {
                        return 2;
                    }
                } else if (type == TYPE_FILTER) {
                    if (isInclude) {
                        if (position == 0 || position == 6) {
                            return 2;
                        }
                    } else {
                        if (position == 0 || position == 4) {
                            return 2;
                        }
                    }
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
            return holder.getItemViewType() == 1;
        }

        public void searchDialogs(final String query) {
            if (searchRunnable != null) {
                Utilities.searchQueue.cancelRunnable(searchRunnable);
                searchRunnable = null;
            }
            final boolean allowBots = type != TYPE_PRIVATE;
            final boolean allowChats = type != TYPE_PRIVATE;
            if (query == null) {
                searchResult.clear();
                searchResultNames.clear();
                searchAdapterHelper.mergeResults(null);

                searchAdapterHelper.queryServerSearch(null, true, false, false, false, false, 0, false, 0, 0);
                notifyDataSetChanged();
            } else {
                Utilities.searchQueue.postRunnable(searchRunnable = () -> AndroidUtilities.runOnUIThread(() -> {
                    searchAdapterHelper.queryServerSearch(query, true, allowChats, allowChats, allowSelf, false, 0, false, 0, 0);
                    Utilities.searchQueue.postRunnable(searchRunnable = () -> {
                        String search1 = query.trim().toLowerCase();
                        if (search1.length() == 0) {
                            updateSearchResults(new ArrayList<>(), new ArrayList<>());
                            return;
                        }
                        String search2 = LocaleController.getInstance().getTranslitString(search1);
                        if (search1.equals(search2) || search2.length() == 0) {
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

                            String username;
                            final String[] names = new String[3];

                            if (object instanceof TLRPC.User) {
                                TLRPC.User user = (TLRPC.User) object;
                                names[0] = ContactsController.formatName(user.first_name, user.last_name).toLowerCase();
                                username = UserObject.getPublicUsername(user);
                                if (UserObject.isReplyUser(user)) {
                                    names[2] = LocaleController.getString("RepliesTitle", R.string.RepliesTitle).toLowerCase();
                                } else if (UserObject.isUserSelf(user)) {
                                    if (!allowSelf) continue;
                                    names[2] = LocaleController.getString("SavedMessages", R.string.SavedMessages).toLowerCase();
                                } else if (user.bot && !allowBots) {
                                    continue;
                                }
                            } else {
                                TLRPC.Chat chat = (TLRPC.Chat) object;
                                names[0] = chat.title.toLowerCase();
                                username = chat.username;
                                if (!allowChats)
                                    continue;
                            }
                            names[1] = LocaleController.getInstance().getTranslitString(names[0]);
                            if (names[0].equals(names[1])) {
                                names[1] = null;
                            }

                            int found = 0;
                            for (String q : search) {
                                for (int i = 0; i < names.length; i++) {
                                    final String name = names[i];
                                    if (name != null && (name.startsWith(q) || name.contains(" " + q))) {
                                        found = 1;
                                        break;
                                    }
                                }
                                if (found == 0 && username != null && username.toLowerCase().startsWith(q)) {
                                    found = 2;
                                }

                                if (found != 0) {
                                    if (found == 1) {
                                        if (object instanceof TLRPC.User) {
                                            TLRPC.User user = (TLRPC.User) object;
                                            resultArrayNames.add(AndroidUtilities.generateSearchName(user.first_name, user.last_name, q));
                                        } else {
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
                if (searching && !searchAdapterHelper.isSearchInProgress()) {
                    emptyView.showProgress(false);
                }
                notifyDataSetChanged();
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

        themeDescriptions.add(new ThemeDescription(editText, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(editText, ThemeDescription.FLAG_HINTTEXTCOLOR, null, null, null, null, Theme.key_groupcreate_hintText));
        themeDescriptions.add(new ThemeDescription(editText, ThemeDescription.FLAG_CURSORCOLOR, null, null, null, null, Theme.key_groupcreate_cursor));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{GraySectionCell.class}, new String[]{"textView"}, null, null, null, Theme.key_graySectionText));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_CELLBACKGROUNDCOLOR, new Class[]{GraySectionCell.class}, null, null, null, Theme.key_graySection));

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

        return themeDescriptions;
    }
}
