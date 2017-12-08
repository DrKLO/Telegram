/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2017.
 */

package org.telegram.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
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
import android.view.inputmethod.EditorInfo;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.support.widget.LinearLayoutManager;
import org.telegram.messenger.support.widget.RecyclerView;
import org.telegram.tgnet.TLRPC;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Adapters.SearchAdapterHelper;
import org.telegram.ui.Cells.GroupCreateSectionCell;
import org.telegram.ui.Cells.GroupCreateUserCell;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.EmptyTextProgressView;
import org.telegram.ui.Components.GroupCreateDividerItemDecoration;
import org.telegram.ui.Components.GroupCreateSpan;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;

public class GroupCreateActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate, View.OnClickListener {

    private ScrollView scrollView;
    private SpansContainer spansContainer;
    private EditTextBoldCursor editText;
    private RecyclerListView listView;
    private EmptyTextProgressView emptyView;
    private GroupCreateAdapter adapter;
    private GroupCreateActivityDelegate delegate;
    private GroupCreateDividerItemDecoration itemDecoration;
    private AnimatorSet currentDoneButtonAnimation;
    private View doneButton;
    private boolean doneButtonVisible;
    private boolean ignoreScrollEvent;

    private int containerHeight;

    private int chatId;

    private int maxCount = MessagesController.getInstance().maxMegagroupCount;
    private int chatType = ChatObject.CHAT_TYPE_CHAT;
    private boolean isAlwaysShare;
    private boolean isNeverShare;
    private boolean searchWas;
    private boolean searching;
    private boolean isGroup;
    private HashMap<Integer, GroupCreateSpan> selectedContacts = new HashMap<>();
    private ArrayList<GroupCreateSpan> allSpans = new ArrayList<>();
    private GroupCreateSpan currentDeletingSpan;

    private int fieldY;

    private final static int done_button = 1;

    public interface GroupCreateActivityDelegate {
        void didSelectUsers(ArrayList<Integer> ids);
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
            int maxWidth = width - AndroidUtilities.dp(32);
            int currentLineWidth = 0;
            int y = AndroidUtilities.dp(12);
            int allCurrentLineWidth = 0;
            int allY = AndroidUtilities.dp(12);
            int x;
            for (int a = 0; a < count; a++) {
                View child = getChildAt(a);
                if (!(child instanceof GroupCreateSpan)) {
                    continue;
                }
                child.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(32), MeasureSpec.EXACTLY));
                if (child != removingSpan && currentLineWidth + child.getMeasuredWidth() > maxWidth) {
                    y += child.getMeasuredHeight() + AndroidUtilities.dp(12);
                    currentLineWidth = 0;
                }
                if (allCurrentLineWidth + child.getMeasuredWidth() > maxWidth) {
                    allY += child.getMeasuredHeight() + AndroidUtilities.dp(12);
                    allCurrentLineWidth = 0;
                }
                x = AndroidUtilities.dp(16) + currentLineWidth;
                if (!animationStarted) {
                    if (child == removingSpan) {
                        child.setTranslationX(AndroidUtilities.dp(16) + allCurrentLineWidth);
                        child.setTranslationY(allY);
                    } else if (removingSpan != null) {
                        if (child.getTranslationX() != x) {
                            animators.add(ObjectAnimator.ofFloat(child, "translationX", x));
                        }
                        if (child.getTranslationY() != y) {
                            animators.add(ObjectAnimator.ofFloat(child, "translationY", y));
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
                minWidth = AndroidUtilities.dp(530 - 32 - 18 - 57 * 2) / 3;
            } else {
                minWidth = (Math.min(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y) - AndroidUtilities.dp(32 + 18 + 57 * 2)) / 3;
            }
            if (maxWidth - currentLineWidth < minWidth) {
                currentLineWidth = 0;
                y += AndroidUtilities.dp(32 + 12);
            }
            if (maxWidth - allCurrentLineWidth < minWidth) {
                allY += AndroidUtilities.dp(32 + 12);
            }
            editText.measure(MeasureSpec.makeMeasureSpec(maxWidth - currentLineWidth, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(32), MeasureSpec.EXACTLY));
            if (!animationStarted) {
                int currentHeight = allY + AndroidUtilities.dp(32 + 12);
                int fieldX = currentLineWidth + AndroidUtilities.dp(16);
                fieldY = y;
                if (currentAnimation != null) {
                    int resultHeight = y + AndroidUtilities.dp(32 + 12);
                    if (containerHeight != resultHeight) {
                        animators.add(ObjectAnimator.ofInt(GroupCreateActivity.this, "containerHeight", resultHeight));
                    }
                    if (editText.getTranslationX() != fieldX) {
                        animators.add(ObjectAnimator.ofFloat(editText, "translationX", fieldX));
                    }
                    if (editText.getTranslationY() != fieldY) {
                        animators.add(ObjectAnimator.ofFloat(editText, "translationY", fieldY));
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

        public void addSpan(final GroupCreateSpan span) {
            allSpans.add(span);
            selectedContacts.put(span.getUid(), span);

            editText.setHintVisible(false);
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
                    editText.setAllowDrawCursor(true);
                }
            });
            currentAnimation.setDuration(150);
            addingSpan = span;
            animators.clear();
            animators.add(ObjectAnimator.ofFloat(addingSpan, "scaleX", 0.01f, 1.0f));
            animators.add(ObjectAnimator.ofFloat(addingSpan, "scaleY", 0.01f, 1.0f));
            animators.add(ObjectAnimator.ofFloat(addingSpan, "alpha", 0.0f, 1.0f));
            addView(span);
        }

        public void removeSpan(final GroupCreateSpan span) {
            ignoreScrollEvent = true;
            selectedContacts.remove(span.getUid());
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
                        editText.setHintVisible(true);
                    }
                }
            });
            currentAnimation.setDuration(150);
            removingSpan = span;
            animators.clear();
            animators.add(ObjectAnimator.ofFloat(removingSpan, "scaleX", 1.0f, 0.01f));
            animators.add(ObjectAnimator.ofFloat(removingSpan, "scaleY", 1.0f, 0.01f));
            animators.add(ObjectAnimator.ofFloat(removingSpan, "alpha", 1.0f, 0.0f));
            requestLayout();
        }
    }

    public GroupCreateActivity() {
        super();
    }

    public GroupCreateActivity(Bundle args) {
        super(args);
        chatType = args.getInt("chatType", ChatObject.CHAT_TYPE_CHAT);
        isAlwaysShare = args.getBoolean("isAlwaysShare", false);
        isNeverShare = args.getBoolean("isNeverShare", false);
        isGroup = args.getBoolean("isGroup", false);
        chatId = args.getInt("chatId");
        maxCount = chatType == ChatObject.CHAT_TYPE_CHAT ? MessagesController.getInstance().maxMegagroupCount : MessagesController.getInstance().maxBroadcastCount;
    }

    @Override
    public boolean onFragmentCreate() {
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.contactsDidLoaded);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.updateInterfaces);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.chatDidCreated);
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.contactsDidLoaded);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.updateInterfaces);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.chatDidCreated);
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
        doneButtonVisible = chatType == ChatObject.CHAT_TYPE_CHANNEL;

        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        if (chatType == ChatObject.CHAT_TYPE_CHANNEL) {
            actionBar.setTitle(LocaleController.getString("ChannelAddMembers", R.string.ChannelAddMembers));
        } else {
            if (isAlwaysShare) {
                if (isGroup) {
                    actionBar.setTitle(LocaleController.getString("AlwaysAllow", R.string.AlwaysAllow));
                } else {
                    actionBar.setTitle(LocaleController.getString("AlwaysShareWithTitle", R.string.AlwaysShareWithTitle));
                }
            } else if (isNeverShare) {
                if (isGroup) {
                    actionBar.setTitle(LocaleController.getString("NeverAllow", R.string.NeverAllow));
                } else {
                    actionBar.setTitle(LocaleController.getString("NeverShareWithTitle", R.string.NeverShareWithTitle));
                }
            } else {
                actionBar.setTitle(chatType == ChatObject.CHAT_TYPE_CHAT ? LocaleController.getString("NewGroup", R.string.NewGroup) : LocaleController.getString("NewBroadcastList", R.string.NewBroadcastList));
            }
        }

        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                } else if (id == done_button) {
                    onDonePressed();
                }
            }
        });

        ActionBarMenu menu = actionBar.createMenu();
        doneButton = menu.addItemWithWidth(done_button, R.drawable.ic_done, AndroidUtilities.dp(56));
        if (chatType != ChatObject.CHAT_TYPE_CHANNEL) {
            doneButton.setScaleX(0.0f);
            doneButton.setScaleY(0.0f);
            doneButton.setAlpha(0.0f);
        }

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
            }

            @Override
            protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
                scrollView.layout(0, 0, scrollView.getMeasuredWidth(), scrollView.getMeasuredHeight());
                listView.layout(0, scrollView.getMeasuredHeight(), listView.getMeasuredWidth(), scrollView.getMeasuredHeight() + listView.getMeasuredHeight());
                emptyView.layout(0, scrollView.getMeasuredHeight(), emptyView.getMeasuredWidth(), scrollView.getMeasuredHeight() + emptyView.getMeasuredHeight());
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

        editText = new EditTextBoldCursor(context) {
            @Override
            public boolean onTouchEvent(MotionEvent event) {
                if (currentDeletingSpan != null) {
                    currentDeletingSpan.cancelDeleteAnimation();
                    currentDeletingSpan = null;
                }
                return super.onTouchEvent(event);
            }
        };
        editText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
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
        if (chatType == ChatObject.CHAT_TYPE_CHANNEL) {
            editText.setHintText(LocaleController.getString("AddMutual", R.string.AddMutual));
        } else {
            if (isAlwaysShare) {
                if (isGroup) {
                    editText.setHintText(LocaleController.getString("AlwaysAllowPlaceholder", R.string.AlwaysAllowPlaceholder));
                } else {
                    editText.setHintText(LocaleController.getString("AlwaysShareWithPlaceholder", R.string.AlwaysShareWithPlaceholder));
                }
            } else if (isNeverShare) {
                if (isGroup) {
                    editText.setHintText(LocaleController.getString("NeverAllowPlaceholder", R.string.NeverAllowPlaceholder));
                } else {
                    editText.setHintText(LocaleController.getString("NeverShareWithPlaceholder", R.string.NeverShareWithPlaceholder));
                }
            } else {
                editText.setHintText(LocaleController.getString("SendMessageTo", R.string.SendMessageTo));
            }
        }
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
        editText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                return actionId == EditorInfo.IME_ACTION_DONE && onDonePressed();
            }
        });
        editText.setOnKeyListener(new View.OnKeyListener() {

            private boolean wasEmpty;

            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (keyCode == KeyEvent.KEYCODE_DEL) {
                    if (event.getAction() == KeyEvent.ACTION_DOWN) {
                        wasEmpty = editText.length() == 0;
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
                    searching = true;
                    searchWas = true;
                    adapter.setSearching(true);
                    itemDecoration.setSearching(true);
                    adapter.searchDialogs(editText.getText().toString());
                    listView.setFastScrollVisible(false);
                    listView.setVerticalScrollBarEnabled(true);
                    emptyView.setText(LocaleController.getString("NoResult", R.string.NoResult));
                } else {
                    closeSearch();
                }
            }
        });

        emptyView = new EmptyTextProgressView(context);
        if (ContactsController.getInstance().isLoadingContacts()) {
            emptyView.showProgress();
        } else {
            emptyView.showTextView();
        }
        emptyView.setShowAtCenter(true);
        emptyView.setText(LocaleController.getString("NoContacts", R.string.NoContacts));
        frameLayout.addView(emptyView);

        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false);

        listView = new RecyclerListView(context);
        listView.setFastScrollEnabled();
        listView.setEmptyView(emptyView);
        listView.setAdapter(adapter = new GroupCreateAdapter(context));
        listView.setLayoutManager(linearLayoutManager);
        listView.setVerticalScrollBarEnabled(false);
        listView.setVerticalScrollbarPosition(LocaleController.isRTL ? View.SCROLLBAR_POSITION_LEFT : View.SCROLLBAR_POSITION_RIGHT);
        listView.addItemDecoration(itemDecoration = new GroupCreateDividerItemDecoration());
        frameLayout.addView(listView);
        listView.setOnItemClickListener(new RecyclerListView.OnItemClickListener() {
            @Override
            public void onItemClick(View view, int position) {
                if (!(view instanceof GroupCreateUserCell)) {
                    return;
                }
                GroupCreateUserCell cell = (GroupCreateUserCell) view;
                TLRPC.User user = cell.getUser();
                if (user == null) {
                    return;
                }
                boolean exists;
                if (exists = selectedContacts.containsKey(user.id)) {
                    GroupCreateSpan span = selectedContacts.get(user.id);
                    spansContainer.removeSpan(span);
                } else {
                    if (maxCount != 0 && selectedContacts.size() == maxCount) {
                        return;
                    }
                    if (chatType == ChatObject.CHAT_TYPE_CHAT && selectedContacts.size() == MessagesController.getInstance().maxGroupCount) {
                        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                        builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
                        builder.setMessage(LocaleController.getString("SoftUserLimitAlert", R.string.SoftUserLimitAlert));
                        builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), null);
                        showDialog(builder.create());
                        return;
                    }
                    MessagesController.getInstance().putUser(user, !searching);
                    GroupCreateSpan span = new GroupCreateSpan(editText.getContext(), user);
                    spansContainer.addSpan(span);
                    span.setOnClickListener(GroupCreateActivity.this);
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

        updateHint();
        return fragmentView;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (editText != null) {
            editText.requestFocus();
        }
    }

    @Override
    public void didReceivedNotification(int id, Object... args) {
        if (id == NotificationCenter.contactsDidLoaded) {
            if (emptyView != null) {
                emptyView.showTextView();
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

    public void setContainerHeight(int value) {
        containerHeight = value;
        if (spansContainer != null) {
            spansContainer.requestLayout();
        }
    }

    public int getContainerHeight() {
        return containerHeight;
    }

    private void checkVisibleRows() {
        int count = listView.getChildCount();
        for (int a = 0; a < count; a++) {
            View child = listView.getChildAt(a);
            if (child instanceof GroupCreateUserCell) {
                GroupCreateUserCell cell = (GroupCreateUserCell) child;
                TLRPC.User user = cell.getUser();
                if (user != null) {
                    cell.setChecked(selectedContacts.containsKey(user.id), true);
                }
            }
        }
    }

    private boolean onDonePressed() {
        if (chatType == ChatObject.CHAT_TYPE_CHANNEL) {
            ArrayList<TLRPC.InputUser> result = new ArrayList<>();
            for (Integer uid : selectedContacts.keySet()) {
                TLRPC.InputUser user = MessagesController.getInputUser(MessagesController.getInstance().getUser(uid));
                if (user != null) {
                    result.add(user);
                }
            }
            MessagesController.getInstance().addUsersToChannel(chatId, result, null);
            NotificationCenter.getInstance().postNotificationName(NotificationCenter.closeChats);
            Bundle args2 = new Bundle();
            args2.putInt("chat_id", chatId);
            presentFragment(new ChatActivity(args2), true);
        } else {
            if (!doneButtonVisible || selectedContacts.isEmpty()) {
                return false;
            }
            ArrayList<Integer> result = new ArrayList<>();
            result.addAll(selectedContacts.keySet());
            if (isAlwaysShare || isNeverShare) {
                if (delegate != null) {
                    delegate.didSelectUsers(result);
                }
                finishFragment();
            } else {
                Bundle args = new Bundle();
                args.putIntegerArrayList("result", result);
                args.putInt("chatType", chatType);
                presentFragment(new GroupCreateFinalActivity(args));
            }
        }
        return true;
    }

    private void closeSearch() {
        searching = false;
        searchWas = false;
        itemDecoration.setSearching(false);
        adapter.setSearching(false);
        adapter.searchDialogs(null);
        listView.setFastScrollVisible(true);
        listView.setVerticalScrollBarEnabled(false);
        emptyView.setText(LocaleController.getString("NoContacts", R.string.NoContacts));
    }

    private void updateHint() {
        if (!isAlwaysShare && !isNeverShare) {
            if (chatType == ChatObject.CHAT_TYPE_CHANNEL) {
                actionBar.setSubtitle(LocaleController.formatPluralString("Members", selectedContacts.size()));
            } else {
                if (selectedContacts.isEmpty()) {
                    actionBar.setSubtitle(LocaleController.formatString("MembersCountZero", R.string.MembersCountZero, LocaleController.formatPluralString("Members", maxCount)));
                } else {
                    actionBar.setSubtitle(LocaleController.formatString("MembersCount", R.string.MembersCount, selectedContacts.size(), maxCount));
                }
            }
        }
        if (chatType != ChatObject.CHAT_TYPE_CHANNEL) {
            if (doneButtonVisible && allSpans.isEmpty()) {
                if (currentDoneButtonAnimation != null) {
                    currentDoneButtonAnimation.cancel();
                }
                currentDoneButtonAnimation = new AnimatorSet();
                currentDoneButtonAnimation.playTogether(ObjectAnimator.ofFloat(doneButton, "scaleX", 0.0f),
                        ObjectAnimator.ofFloat(doneButton, "scaleY", 0.0f),
                        ObjectAnimator.ofFloat(doneButton, "alpha", 0.0f));
                currentDoneButtonAnimation.setDuration(180);
                currentDoneButtonAnimation.start();
                doneButtonVisible = false;
            } else if (!doneButtonVisible && !allSpans.isEmpty()) {
                if (currentDoneButtonAnimation != null) {
                    currentDoneButtonAnimation.cancel();
                }
                currentDoneButtonAnimation = new AnimatorSet();
                currentDoneButtonAnimation.playTogether(ObjectAnimator.ofFloat(doneButton, "scaleX", 1.0f),
                        ObjectAnimator.ofFloat(doneButton, "scaleY", 1.0f),
                        ObjectAnimator.ofFloat(doneButton, "alpha", 1.0f));
                currentDoneButtonAnimation.setDuration(180);
                currentDoneButtonAnimation.start();
                doneButtonVisible = true;
            }
        }
    }

    public void setDelegate(GroupCreateActivityDelegate groupCreateActivityDelegate) {
        delegate = groupCreateActivityDelegate;
    }

    public class GroupCreateAdapter extends RecyclerListView.FastScrollAdapter {

        private Context context;
        private ArrayList<TLRPC.User> searchResult = new ArrayList<>();
        private ArrayList<CharSequence> searchResultNames = new ArrayList<>();
        private SearchAdapterHelper searchAdapterHelper;
        private Timer searchTimer;
        private boolean searching;
        private ArrayList<TLRPC.User> contacts = new ArrayList<>();

        public GroupCreateAdapter(Context ctx) {
            context = ctx;

            ArrayList<TLRPC.TL_contact> arrayList  = ContactsController.getInstance().contacts;
            for (int a = 0; a < arrayList.size(); a++) {
                TLRPC.User user = MessagesController.getInstance().getUser(arrayList.get(a).user_id);
                if (user == null || user.self || user.deleted) {
                    continue;
                }
                contacts.add(user);
            }

            searchAdapterHelper = new SearchAdapterHelper();
            searchAdapterHelper.setDelegate(new SearchAdapterHelper.SearchAdapterHelperDelegate() {
                @Override
                public void onDataSetChanged() {
                    notifyDataSetChanged();
                }

                @Override
                public void onSetHashtags(ArrayList<SearchAdapterHelper.HashtagObject> arrayList, HashMap<String, SearchAdapterHelper.HashtagObject> hashMap) {

                }
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
            if (position < 0 || position >= contacts.size()) {
                return null;
            }
            TLRPC.User user = contacts.get(position);
            if (user == null) {
                return null;
            }
            if (LocaleController.nameDisplayOrder == 1) {
                if (!TextUtils.isEmpty(user.first_name)) {
                    return user.first_name.substring(0, 1).toUpperCase();
                } else if (!TextUtils.isEmpty(user.last_name)) {
                    return user.last_name.substring(0, 1).toUpperCase();
                }
            } else {
                if (!TextUtils.isEmpty(user.last_name)) {
                    return user.last_name.substring(0, 1).toUpperCase();
                } else if (!TextUtils.isEmpty(user.first_name)) {
                    return user.first_name.substring(0, 1).toUpperCase();
                }
            }
            return "";
        }

        @Override
        public int getItemCount() {
            if (searching) {
                int count = searchResult.size();
                int globalCount = searchAdapterHelper.getGlobalSearch().size();
                if (globalCount != 0) {
                    count += globalCount + 1;
                }
                return count;
            }
            return contacts.size();
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case 0:
                    view = new GroupCreateSectionCell(context);
                    break;
                default:
                    view = new GroupCreateUserCell(context, true);
                    break;
            }
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            switch (holder.getItemViewType()) {
                case 0: {
                    GroupCreateSectionCell cell = (GroupCreateSectionCell) holder.itemView;
                    if (searching) {
                        cell.setText(LocaleController.getString("GlobalSearch", R.string.GlobalSearch));
                    }
                    break;
                }
                default: {
                    GroupCreateUserCell cell = (GroupCreateUserCell) holder.itemView;
                    TLRPC.User user;
                    CharSequence username = null;
                    CharSequence name = null;
                    if (searching) {
                        int localCount = searchResult.size();
                        int globalCount = searchAdapterHelper.getGlobalSearch().size();
                        if (position >= 0 && position < localCount) {
                            user = searchResult.get(position);
                        } else if (position > localCount && position <= globalCount + localCount) {
                            user = (TLRPC.User) searchAdapterHelper.getGlobalSearch().get(position - localCount - 1);
                        } else {
                            user = null;
                        }
                        if (user != null) {
                            if (position < localCount) {
                                name = searchResultNames.get(position);
                                if (name != null && !TextUtils.isEmpty(user.username)) {
                                    if (name.toString().startsWith("@" + user.username)) {
                                        username = name;
                                        name = null;
                                    }
                                }
                            } else if (position > localCount && !TextUtils.isEmpty(user.username)) {
                                String foundUserName = searchAdapterHelper.getLastFoundUsername();
                                if (foundUserName.startsWith("@")) {
                                    foundUserName = foundUserName.substring(1);
                                }
                                try {
                                    username = new SpannableStringBuilder(username);
                                    ((SpannableStringBuilder) username).setSpan(new ForegroundColorSpan(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4)), 0, foundUserName.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                                } catch (Exception e) {
                                    username = user.username;
                                }
                            }
                        }
                    } else {
                        user = contacts.get(position);
                    }
                    cell.setUser(user, name, username);
                    cell.setChecked(selectedContacts.containsKey(user.id), false);
                    break;
                }
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (searching) {
                if (position == searchResult.size()) {
                    return 0;
                }
                return 1;
            } else {
                return 1;
            }
        }

        @Override
        public int getPositionForScrollProgress(float progress) {
            return (int) (getItemCount() * progress);
        }

        @Override
        public void onViewRecycled(RecyclerView.ViewHolder holder) {
            if (holder.itemView instanceof GroupCreateUserCell) {
                ((GroupCreateUserCell) holder.itemView).recycle();
            }
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return true;
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
                searchAdapterHelper.queryServerSearch(null, true, false, false, false, 0, false);
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

                        AndroidUtilities.runOnUIThread(new Runnable() {
                            @Override
                            public void run() {
                                searchAdapterHelper.queryServerSearch(query, true, false, false, false, 0, false);
                                Utilities.searchQueue.postRunnable(new Runnable() {
                                    @Override
                                    public void run() {
                                        String search1 = query.trim().toLowerCase();
                                        if (search1.length() == 0) {
                                            updateSearchResults(new ArrayList<TLRPC.User>(), new ArrayList<CharSequence>());
                                            return;
                                        }
                                        String search2 = LocaleController.getInstance().getTranslitString(search1);
                                        if (search1.equals(search2) || search2.length() == 0) {
                                            search2 = null;
                                        }
                                        String search[] = new String[1 + (search2 != null ? 1 : 0)];
                                        search[0] = search1;
                                        if (search2 != null) {
                                            search[1] = search2;
                                        }

                                        ArrayList<TLRPC.User> resultArray = new ArrayList<>();
                                        ArrayList<CharSequence> resultArrayNames = new ArrayList<>();

                                        for (int a = 0; a < contacts.size(); a++) {
                                            TLRPC.User user = contacts.get(a);

                                            String name = ContactsController.formatName(user.first_name, user.last_name).toLowerCase();
                                            String tName = LocaleController.getInstance().getTranslitString(name);
                                            if (name.equals(tName)) {
                                                tName = null;
                                            }

                                            int found = 0;
                                            for (String q : search) {
                                                if (name.startsWith(q) || name.contains(" " + q) || tName != null && (tName.startsWith(q) || tName.contains(" " + q))) {
                                                    found = 1;
                                                } else if (user.username != null && user.username.startsWith(q)) {
                                                    found = 2;
                                                }

                                                if (found != 0) {
                                                    if (found == 1) {
                                                        resultArrayNames.add(AndroidUtilities.generateSearchName(user.first_name, user.last_name, q));
                                                    } else {
                                                        resultArrayNames.add(AndroidUtilities.generateSearchName("@" + user.username, null, "@" + q));
                                                    }
                                                    resultArray.add(user);
                                                    break;
                                                }
                                            }
                                        }
                                        updateSearchResults(resultArray, resultArrayNames);
                                    }
                                });
                            }
                        });

                    }
                }, 200, 300);
            }
        }

        private void updateSearchResults(final ArrayList<TLRPC.User> users, final ArrayList<CharSequence> names) {
            AndroidUtilities.runOnUIThread(new Runnable() {
                @Override
                public void run() {
                    searchResult = users;
                    searchResultNames = names;
                    notifyDataSetChanged();
                }
            });
        }
    }

    @Override
    public ThemeDescription[] getThemeDescriptions() {
        ThemeDescription.ThemeDescriptionDelegate сellDelegate = new ThemeDescription.ThemeDescriptionDelegate() {
            @Override
            public void didSetColor(int color) {
                int count = listView.getChildCount();
                for (int a = 0; a < count; a++) {
                    View child = listView.getChildAt(a);
                    if (child instanceof GroupCreateUserCell) {
                        ((GroupCreateUserCell) child).update(0);
                    }
                }
            }
        };

        return new ThemeDescription[]{
                new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundWhite),

                new ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_actionBarDefault),
                new ThemeDescription(listView, ThemeDescription.FLAG_LISTGLOWCOLOR, null, null, null, null, Theme.key_actionBarDefault),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_ITEMSCOLOR, null, null, null, null, Theme.key_actionBarDefaultIcon),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_TITLECOLOR, null, null, null, null, Theme.key_actionBarDefaultTitle),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_actionBarDefaultSelector),

                new ThemeDescription(scrollView, ThemeDescription.FLAG_LISTGLOWCOLOR, null, null, null, null, Theme.key_windowBackgroundWhite),

                new ThemeDescription(listView, ThemeDescription.FLAG_SELECTOR, null, null, null, null, Theme.key_listSelector),

                new ThemeDescription(listView, ThemeDescription.FLAG_FASTSCROLL, null, null, null, null, Theme.key_fastScrollActive),
                new ThemeDescription(listView, ThemeDescription.FLAG_FASTSCROLL, null, null, null, null, Theme.key_fastScrollInactive),
                new ThemeDescription(listView, ThemeDescription.FLAG_FASTSCROLL, null, null, null, null, Theme.key_fastScrollText),

                new ThemeDescription(listView, 0, new Class[]{View.class}, Theme.dividerPaint, null, null, Theme.key_divider),

                new ThemeDescription(emptyView, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_emptyListPlaceholder),
                new ThemeDescription(emptyView, ThemeDescription.FLAG_PROGRESSBAR, null, null, null, null, Theme.key_progressCircle),

                new ThemeDescription(editText, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteBlackText),
                new ThemeDescription(editText, ThemeDescription.FLAG_HINTTEXTCOLOR, null, null, null, null, Theme.key_groupcreate_hintText),
                new ThemeDescription(editText, ThemeDescription.FLAG_CURSORCOLOR, null, null, null, null, Theme.key_groupcreate_cursor),

                new ThemeDescription(listView, ThemeDescription.FLAG_CELLBACKGROUNDCOLOR, new Class[]{GroupCreateSectionCell.class}, null, null, null, Theme.key_graySection),
                new ThemeDescription(listView, 0, new Class[]{GroupCreateSectionCell.class}, new String[]{"drawable"}, null, null, null, Theme.key_groupcreate_sectionShadow),
                new ThemeDescription(listView, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{GroupCreateSectionCell.class}, new String[]{"textView"}, null, null, null, Theme.key_groupcreate_sectionText),

                new ThemeDescription(listView, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{GroupCreateUserCell.class}, new String[]{"textView"}, null, null, null, Theme.key_groupcreate_sectionText),
                new ThemeDescription(listView, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{GroupCreateUserCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_groupcreate_checkbox),
                new ThemeDescription(listView, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{GroupCreateUserCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_groupcreate_checkboxCheck),
                new ThemeDescription(listView, ThemeDescription.FLAG_TEXTCOLOR | ThemeDescription.FLAG_CHECKTAG, new Class[]{GroupCreateUserCell.class}, new String[]{"statusTextView"}, null, null, null, Theme.key_groupcreate_onlineText),
                new ThemeDescription(listView, ThemeDescription.FLAG_TEXTCOLOR | ThemeDescription.FLAG_CHECKTAG, new Class[]{GroupCreateUserCell.class}, new String[]{"statusTextView"}, null, null, null, Theme.key_groupcreate_offlineText),
                new ThemeDescription(listView, 0, new Class[]{GroupCreateUserCell.class}, null, new Drawable[]{Theme.avatar_photoDrawable, Theme.avatar_broadcastDrawable, Theme.avatar_savedDrawable}, null, Theme.key_avatar_text),
                new ThemeDescription(null, 0, null, null, null, сellDelegate, Theme.key_avatar_backgroundRed),
                new ThemeDescription(null, 0, null, null, null, сellDelegate, Theme.key_avatar_backgroundOrange),
                new ThemeDescription(null, 0, null, null, null, сellDelegate, Theme.key_avatar_backgroundViolet),
                new ThemeDescription(null, 0, null, null, null, сellDelegate, Theme.key_avatar_backgroundGreen),
                new ThemeDescription(null, 0, null, null, null, сellDelegate, Theme.key_avatar_backgroundCyan),
                new ThemeDescription(null, 0, null, null, null, сellDelegate, Theme.key_avatar_backgroundBlue),
                new ThemeDescription(null, 0, null, null, null, сellDelegate, Theme.key_avatar_backgroundPink),

                new ThemeDescription(spansContainer, 0, new Class[]{GroupCreateSpan.class}, null, null, null, Theme.key_avatar_backgroundGroupCreateSpanBlue),
                new ThemeDescription(spansContainer, 0, new Class[]{GroupCreateSpan.class}, null, null, null, Theme.key_groupcreate_spanBackground),
                new ThemeDescription(spansContainer, 0, new Class[]{GroupCreateSpan.class}, null, null, null, Theme.key_groupcreate_spanText),
                new ThemeDescription(spansContainer, 0, new Class[]{GroupCreateSpan.class}, null, null, null, Theme.key_avatar_backgroundBlue),
        };
    }
}
