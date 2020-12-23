/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Components;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.Editable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.util.Property;
import android.util.SparseArray;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.inputmethod.EditorInfo;
import android.widget.FrameLayout;
import android.widget.ImageView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Adapters.SearchAdapterHelper;
import org.telegram.ui.Cells.GraySectionCell;
import org.telegram.ui.Cells.GroupCallTextCell;
import org.telegram.ui.Cells.GroupCallUserCell;
import org.telegram.ui.Cells.ManageChatTextCell;
import org.telegram.ui.Cells.ManageChatUserCell;
import org.telegram.ui.ChatUsersActivity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public class GroupVoipInviteAlert extends BottomSheet {

    private FrameLayout frameLayout;
    private RecyclerListView listView;
    private SearchAdapter searchListViewAdapter;
    private ListAdapter listViewAdapter;
    private Drawable shadowDrawable;
    private View shadow;
    private AnimatorSet shadowAnimation;
    private StickerEmptyView emptyView;
    private FlickerLoadingView flickerLoadingView;
    private SearchField searchView;

    private RectF rect = new RectF();
    private Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private String linkToCopy;

    private int scrollOffsetY;

    private int delayResults;

    private float colorProgress;
    private int backgroundColor;

    private TLRPC.Chat currentChat;
    private TLRPC.ChatFull info;

    private ArrayList<TLObject> participants = new ArrayList<>();
    private ArrayList<TLObject> contacts = new ArrayList<>();
    private boolean contactsEndReached;
    private SparseArray<TLObject> participantsMap = new SparseArray<>();
    private SparseArray<TLObject> contactsMap = new SparseArray<>();
    private boolean loadingUsers;
    private boolean firstLoaded;

    private SparseArray<TLRPC.TL_groupCallParticipant> ignoredUsers;
    private HashSet<Integer> invitedUsers;

    private GroupVoipInviteAlertDelegate delegate;

    private int emptyRow;
    private int addNewRow;
    private int lastRow;
    private int participantsStartRow;
    private int participantsEndRow;
    private int contactsHeaderRow;
    private int contactsStartRow;
    private int contactsEndRow;
    private int membersHeaderRow;
    private int flickerProgressRow;
    private int rowCount;

    public interface GroupVoipInviteAlertDelegate {
        void copyInviteLink();
        void inviteUser(int id);
        void needOpenSearch(MotionEvent ev, EditTextBoldCursor editText);
    }

    @SuppressWarnings("FieldCanBeLocal")
    private class SearchField extends FrameLayout {

        private View searchBackground;
        private ImageView searchIconImageView;
        private ImageView clearSearchImageView;
        private CloseProgressDrawable2 progressDrawable;
        private EditTextBoldCursor searchEditText;
        private View backgroundView;

        public SearchField(Context context) {
            super(context);

            searchBackground = new View(context);
            searchBackground.setBackgroundDrawable(Theme.createRoundRectDrawable(AndroidUtilities.dp(18), Theme.getColor(Theme.key_voipgroup_searchBackground)));
            addView(searchBackground, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 36, Gravity.LEFT | Gravity.TOP, 14, 11, 14, 0));

            searchIconImageView = new ImageView(context);
            searchIconImageView.setScaleType(ImageView.ScaleType.CENTER);
            searchIconImageView.setImageResource(R.drawable.smiles_inputsearch);
            searchIconImageView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_voipgroup_searchPlaceholder), PorterDuff.Mode.MULTIPLY));
            addView(searchIconImageView, LayoutHelper.createFrame(36, 36, Gravity.LEFT | Gravity.TOP, 16, 11, 0, 0));

            clearSearchImageView = new ImageView(context);
            clearSearchImageView.setScaleType(ImageView.ScaleType.CENTER);
            clearSearchImageView.setImageDrawable(progressDrawable = new CloseProgressDrawable2());
            progressDrawable.setSide(AndroidUtilities.dp(7));
            clearSearchImageView.setScaleX(0.1f);
            clearSearchImageView.setScaleY(0.1f);
            clearSearchImageView.setAlpha(0.0f);
            clearSearchImageView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_voipgroup_searchPlaceholder), PorterDuff.Mode.MULTIPLY));
            addView(clearSearchImageView, LayoutHelper.createFrame(36, 36, Gravity.RIGHT | Gravity.TOP, 14, 11, 14, 0));
            clearSearchImageView.setOnClickListener(v -> {
                searchEditText.setText("");
                AndroidUtilities.showKeyboard(searchEditText);
            });

            searchEditText = new EditTextBoldCursor(context) {
                @Override
                public boolean dispatchTouchEvent(MotionEvent event) {
                    MotionEvent e = MotionEvent.obtain(event);
                    e.setLocation(e.getRawX(), e.getRawY() - containerView.getTranslationY());
                    if (e.getAction() == MotionEvent.ACTION_UP) {
                        e.setAction(MotionEvent.ACTION_CANCEL);
                    }
                    listView.dispatchTouchEvent(e);
                    e.recycle();
                    return super.dispatchTouchEvent(event);
                }
            };
            searchEditText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            searchEditText.setHintTextColor(Theme.getColor(Theme.key_voipgroup_searchPlaceholder));
            searchEditText.setTextColor(Theme.getColor(Theme.key_voipgroup_searchText));
            searchEditText.setBackgroundDrawable(null);
            searchEditText.setPadding(0, 0, 0, 0);
            searchEditText.setMaxLines(1);
            searchEditText.setLines(1);
            searchEditText.setSingleLine(true);
            searchEditText.setImeOptions(EditorInfo.IME_ACTION_SEARCH | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
            searchEditText.setHint(LocaleController.getString("VoipGroupSearchMembers", R.string.VoipGroupSearchMembers));
            searchEditText.setCursorColor(Theme.getColor(Theme.key_voipgroup_searchText));
            searchEditText.setCursorSize(AndroidUtilities.dp(20));
            searchEditText.setCursorWidth(1.5f);
            addView(searchEditText, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 40, Gravity.LEFT | Gravity.TOP, 16 + 38, 9, 16 + 30, 0));
            searchEditText.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {

                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {

                }

                @Override
                public void afterTextChanged(Editable s) {
                    boolean show = searchEditText.length() > 0;
                    boolean showed = clearSearchImageView.getAlpha() != 0;
                    if (show != showed) {
                        clearSearchImageView.animate()
                                .alpha(show ? 1.0f : 0.0f)
                                .setDuration(150)
                                .scaleX(show ? 1.0f : 0.1f)
                                .scaleY(show ? 1.0f : 0.1f)
                                .start();
                    }
                    String text = searchEditText.getText().toString();
                    int oldItemsCount = listView.getAdapter() == null ? 0 : listView.getAdapter().getItemCount();
                    searchListViewAdapter.searchUsers(text);
                    if (TextUtils.isEmpty(text) && listView != null && listView.getAdapter() != listViewAdapter) {
                        listView.setAnimateEmptyView(false, 0);
                        listView.setAdapter(listViewAdapter);
                        listView.setAnimateEmptyView(true, 0);
                        if (oldItemsCount == 0) {
                            showItemsAnimated(0);
                        }
                    }
                    flickerLoadingView.setVisibility(View.VISIBLE);
                }
            });
            searchEditText.setOnEditorActionListener((v, actionId, event) -> {
                if (event != null && (event.getAction() == KeyEvent.ACTION_UP && event.getKeyCode() == KeyEvent.KEYCODE_SEARCH || event.getAction() == KeyEvent.ACTION_DOWN && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                    AndroidUtilities.hideKeyboard(searchEditText);
                }
                return false;
            });
        }

        public void hideKeyboard() {
            AndroidUtilities.hideKeyboard(searchEditText);
        }

        @Override
        public boolean onInterceptTouchEvent(MotionEvent ev) {
            delegate.needOpenSearch(ev, searchEditText);
            return super.onInterceptTouchEvent(ev);
        }
    }

    public static final Property<GroupVoipInviteAlert, Float> COLOR_PROGRESS = new AnimationProperties.FloatProperty<GroupVoipInviteAlert>("colorProgress") {
        @Override
        public void setValue(GroupVoipInviteAlert object, float value) {
            object.setColorProgress(value);
        }

        @Override
        public Float get(GroupVoipInviteAlert object) {
            return object.getColorProgress();
        }
    };

    public GroupVoipInviteAlert(final Context context, int account, TLRPC.Chat chat, TLRPC.ChatFull chatFull, SparseArray<TLRPC.TL_groupCallParticipant> participants, HashSet<Integer> invited) {
        super(context, false);

        setDimBehindAlpha(75);

        currentAccount = account;
        currentChat = chat;
        info = chatFull;
        ignoredUsers = participants;
        invitedUsers = invited;

        shadowDrawable = context.getResources().getDrawable(R.drawable.sheet_shadow_round).mutate();

        containerView = new FrameLayout(context) {

            private boolean ignoreLayout = false;

            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                int totalHeight = MeasureSpec.getSize(heightMeasureSpec);

                if (Build.VERSION.SDK_INT >= 21) {
                    ignoreLayout = true;
                    setPadding(backgroundPaddingLeft, AndroidUtilities.statusBarHeight, backgroundPaddingLeft, 0);
                    ignoreLayout = false;
                }
                int availableHeight = totalHeight - getPaddingTop();
                int padding;
                if (keyboardVisible) {
                    padding = AndroidUtilities.dp(8);
                    setAllowNestedScroll(false);
                } else {
                    padding = availableHeight - (availableHeight / 5 * 3) + AndroidUtilities.dp(8);
                    setAllowNestedScroll(true);
                }
                if (listView.getPaddingTop() != padding) {
                    ignoreLayout = true;
                    listView.setPadding(0, padding, 0, 0);
                    ignoreLayout = false;
                }
                super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(totalHeight, MeasureSpec.EXACTLY));
            }

            @Override
            protected void onLayout(boolean changed, int l, int t, int r, int b) {
                super.onLayout(changed, l, t, r, b);
                updateLayout();
            }

            @Override
            public boolean onInterceptTouchEvent(MotionEvent ev) {
                if (ev.getAction() == MotionEvent.ACTION_DOWN && ev.getY() < scrollOffsetY) {
                    dismiss();
                    return true;
                }
                return super.onInterceptTouchEvent(ev);
            }

            @Override
            public boolean onTouchEvent(MotionEvent e) {
                return !isDismissed() && super.onTouchEvent(e);
            }

            @Override
            public void requestLayout() {
                if (ignoreLayout) {
                    return;
                }
                super.requestLayout();
            }

            @Override
            protected void onDraw(Canvas canvas) {
                canvas.save();
                int y = scrollOffsetY - backgroundPaddingTop + AndroidUtilities.dp(6);
                int top = scrollOffsetY - backgroundPaddingTop - AndroidUtilities.dp(13);
                int height = getMeasuredHeight() + AndroidUtilities.dp(30) + backgroundPaddingTop;
                int statusBarHeight = 0;
                float radProgress = 1.0f;
                if (Build.VERSION.SDK_INT >= 21) {
                    top += AndroidUtilities.statusBarHeight;
                    y += AndroidUtilities.statusBarHeight;
                    height -= AndroidUtilities.statusBarHeight;

                    if (top + backgroundPaddingTop < AndroidUtilities.statusBarHeight * 2) {
                        int diff = Math.min(AndroidUtilities.statusBarHeight, AndroidUtilities.statusBarHeight * 2 - top - backgroundPaddingTop);
                        top -= diff;
                        height += diff;
                        radProgress = 1.0f - Math.min(1.0f, (diff * 2) / (float) AndroidUtilities.statusBarHeight);
                    }
                    if (top + backgroundPaddingTop < AndroidUtilities.statusBarHeight) {
                        statusBarHeight = Math.min(AndroidUtilities.statusBarHeight, AndroidUtilities.statusBarHeight - top - backgroundPaddingTop);
                    }
                }

                shadowDrawable.setBounds(0, top, getMeasuredWidth(), height);
                shadowDrawable.draw(canvas);

                if (radProgress != 1.0f) {
                    Theme.dialogs_onlineCirclePaint.setColor(backgroundColor);
                    rect.set(backgroundPaddingLeft, backgroundPaddingTop + top, getMeasuredWidth() - backgroundPaddingLeft, backgroundPaddingTop + top + AndroidUtilities.dp(24));
                    canvas.drawRoundRect(rect, AndroidUtilities.dp(12) * radProgress, AndroidUtilities.dp(12) * radProgress, Theme.dialogs_onlineCirclePaint);
                }

                int w = AndroidUtilities.dp(36);
                rect.set((getMeasuredWidth() - w) / 2, y, (getMeasuredWidth() + w) / 2, y + AndroidUtilities.dp(4));
                Theme.dialogs_onlineCirclePaint.setColor(Theme.getColor(Theme.key_voipgroup_scrollUp));
                canvas.drawRoundRect(rect, AndroidUtilities.dp(2), AndroidUtilities.dp(2), Theme.dialogs_onlineCirclePaint);

                if (statusBarHeight > 0) {
                    int finalColor = Color.argb(0xff, (int) (Color.red(backgroundColor) * 0.8f), (int) (Color.green(backgroundColor) * 0.8f), (int) (Color.blue(backgroundColor) * 0.8f));
                    Theme.dialogs_onlineCirclePaint.setColor(finalColor);
                    canvas.drawRect(backgroundPaddingLeft, AndroidUtilities.statusBarHeight - statusBarHeight, getMeasuredWidth() - backgroundPaddingLeft, AndroidUtilities.statusBarHeight, Theme.dialogs_onlineCirclePaint);
                }
                canvas.restore();
            }

            @Override
            protected void dispatchDraw(Canvas canvas) {
                canvas.save();
                canvas.clipRect(0, getPaddingTop(), getMeasuredWidth(), getMeasuredHeight());
                super.dispatchDraw(canvas);
                canvas.restore();
            }
        };
        containerView.setWillNotDraw(false);
        containerView.setClipChildren(false);
        containerView.setPadding(backgroundPaddingLeft, 0, backgroundPaddingLeft, 0);

        frameLayout = new FrameLayout(context);

        searchView = new SearchField(context);
        frameLayout.addView(searchView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT));

        flickerLoadingView = new FlickerLoadingView(context);
        flickerLoadingView.setViewType(FlickerLoadingView.USERS_TYPE);
        flickerLoadingView.showDate(false);
        flickerLoadingView.setUseHeaderOffset(true);
        flickerLoadingView.setColors(Theme.key_voipgroup_inviteMembersBackground, Theme.key_voipgroup_searchBackground, Theme.key_voipgroup_actionBarUnscrolled);

        emptyView = new StickerEmptyView(context, flickerLoadingView, StickerEmptyView.STICKER_TYPE_SEARCH);
        emptyView.addView(flickerLoadingView, 0, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, 0,0,2,0,0));
        emptyView.title.setText(LocaleController.getString("NoResult", R.string.NoResult));
        emptyView.subtitle.setText(LocaleController.getString("SearchEmptyViewFilteredSubtitle2", R.string.SearchEmptyViewFilteredSubtitle2));
        emptyView.setVisibility(View.GONE);
        emptyView.setAnimateLayoutChange(true);
        emptyView.showProgress(true, false);
        emptyView.setColors(Theme.key_voipgroup_nameText, Theme.key_voipgroup_lastSeenText, Theme.key_voipgroup_inviteMembersBackground, Theme.key_voipgroup_searchBackground);
        containerView.addView(emptyView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.LEFT | Gravity.TOP, 0, 58 + 4, 0, 0));

        searchListViewAdapter = new SearchAdapter(context);

        listView = new RecyclerListView(context) {
            @Override
            protected boolean allowSelectChildAtPosition(float x, float y) {
                return y >= scrollOffsetY + AndroidUtilities.dp(48) + (Build.VERSION.SDK_INT >= 21 ? AndroidUtilities.statusBarHeight : 0);
            }

            @Override
            public void setTranslationY(float translationY) {
                super.setTranslationY(translationY);
                int[] ii = new int[2];
                getLocationInWindow(ii);
            }

            @Override
            protected boolean emptyViewIsVisible() {
                if (getAdapter() == null) {
                    return false;
                }
                return getAdapter().getItemCount() <= 2;
            }
        };
        listView.setTag(13);
        listView.setPadding(0, 0, 0, AndroidUtilities.dp(48));
        listView.setClipToPadding(false);
        listView.setHideIfEmpty(false);
        listView.setSelectorDrawableColor(Theme.getColor(Theme.key_voipgroup_listSelector));
        FillLastLinearLayoutManager layoutManager = new FillLastLinearLayoutManager(getContext(), LinearLayoutManager.VERTICAL, false, AndroidUtilities.dp(8), listView);
        layoutManager.setBind(false);
        listView.setLayoutManager(layoutManager);
        listView.setHorizontalScrollBarEnabled(false);
        listView.setVerticalScrollBarEnabled(false);
        containerView.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT, 0, 0, 0, 0));
        listView.setAdapter(listViewAdapter = new ListAdapter(context));
        listView.setOnItemClickListener((view, position) -> {
            if (position == addNewRow) {
                delegate.copyInviteLink();
                dismiss();
            } else if (view instanceof ManageChatUserCell) {
                ManageChatUserCell cell = (ManageChatUserCell) view;
                if (invitedUsers.contains(cell.getUserId())) {
                    return;
                }
                delegate.inviteUser(cell.getUserId());
            }
        });
        listView.setOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                updateLayout();
            }

            @Override
            public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    if (scrollOffsetY + backgroundPaddingTop + AndroidUtilities.dp(13) < AndroidUtilities.statusBarHeight * 2 && listView.canScrollVertically(1)) {
                        View child = listView.getChildAt(0);
                        RecyclerListView.Holder holder = (RecyclerListView.Holder) listView.findViewHolderForAdapterPosition(0);
                        if (holder != null && holder.itemView.getTop() > 0) {
                            listView.smoothScrollBy(0, holder.itemView.getTop());
                        }
                    }
                }
            }
        });

        FrameLayout.LayoutParams frameLayoutParams = new FrameLayout.LayoutParams(LayoutHelper.MATCH_PARENT, AndroidUtilities.getShadowHeight(), Gravity.TOP | Gravity.LEFT);
        frameLayoutParams.topMargin = AndroidUtilities.dp(58);
        shadow = new View(context);
        shadow.setBackgroundColor(Theme.getColor(Theme.key_dialogShadowLine));
        shadow.setAlpha(0.0f);
        shadow.setTag(1);
        containerView.addView(shadow, frameLayoutParams);

        containerView.addView(frameLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 58, Gravity.LEFT | Gravity.TOP));

        setColorProgress(0.0f);

        loadChatParticipants(0, 200);
        updateRows();

        listView.setEmptyView(emptyView);
        listView.setAnimateEmptyView(true, 0);
    }

    private float getColorProgress() {
        return colorProgress;
    }

    private void setColorProgress(float progress) {
        colorProgress = progress;
        backgroundColor = AndroidUtilities.getOffsetColor(Theme.getColor(Theme.key_voipgroup_inviteMembersBackground), Theme.getColor(Theme.key_voipgroup_listViewBackground), progress, 1.0f);
        shadowDrawable.setColorFilter(new PorterDuffColorFilter(backgroundColor, PorterDuff.Mode.MULTIPLY));
        frameLayout.setBackgroundColor(backgroundColor);
        navBarColor = backgroundColor;
        listView.setGlowColor(backgroundColor);

        int color = AndroidUtilities.getOffsetColor(Theme.getColor(Theme.key_voipgroup_lastSeenTextUnscrolled), Theme.getColor(Theme.key_voipgroup_lastSeenText), progress, 1.0f);
        int color2 = AndroidUtilities.getOffsetColor(Theme.getColor(Theme.key_voipgroup_mutedIconUnscrolled), Theme.getColor(Theme.key_voipgroup_mutedIcon), progress, 1.0f);//
        for (int a = 0, N = listView.getChildCount(); a < N; a++) {
            View child = listView.getChildAt(a);
            if (child instanceof GroupCallTextCell) {
                GroupCallTextCell cell = (GroupCallTextCell) child;
                cell.setColors(color, color);
            } else if (child instanceof GroupCallUserCell) {
                GroupCallUserCell cell = (GroupCallUserCell) child;
                cell.setGrayIconColor(shadow.getTag() != null ? Theme.key_voipgroup_mutedIcon : Theme.key_voipgroup_mutedIconUnscrolled, color2);
            }
        }
        containerView.invalidate();
        listView.invalidate();
        container.invalidate();
    }

    public void setDelegate(GroupVoipInviteAlertDelegate groupVoipInviteAlertDelegate) {
        delegate = groupVoipInviteAlertDelegate;
    }

    private int getCurrentTop() {
        if (listView.getChildCount() != 0) {
            View child = listView.getChildAt(0);
            RecyclerListView.Holder holder = (RecyclerListView.Holder) listView.findContainingViewHolder(child);
            if (holder != null) {
                return listView.getPaddingTop() - (holder.getAdapterPosition() == 0 && child.getTop() >= 0 ? child.getTop() : 0);
            }
        }
        return -1000;
    }

    private void updateRows() {
        addNewRow = -1;
        emptyRow = -1;
        participantsStartRow = -1;
        participantsEndRow = -1;
        contactsHeaderRow = -1;
        contactsStartRow = -1;
        contactsEndRow = -1;
        membersHeaderRow = -1;
        lastRow = -1;

        rowCount = 0;
        emptyRow = rowCount++;
        if (!TextUtils.isEmpty(currentChat.username) || ChatObject.canUserDoAdminAction(currentChat, ChatObject.ACTION_INVITE)) {
            addNewRow = rowCount++;
        }
        if (!loadingUsers || firstLoaded) {
            boolean hasAnyOther = false;
            if (!contacts.isEmpty()) {
                contactsHeaderRow = rowCount++;
                contactsStartRow = rowCount;
                rowCount += contacts.size();
                contactsEndRow = rowCount;
                hasAnyOther = true;
            }
            if (!participants.isEmpty()) {
                if (hasAnyOther) {
                    membersHeaderRow = rowCount++;
                }
                participantsStartRow = rowCount;
                rowCount += participants.size();
                participantsEndRow = rowCount;
            }
        }
        if (loadingUsers) {
            flickerProgressRow = rowCount++;
        }
        lastRow = rowCount++;
    }

    @Override
    protected boolean canDismissWithSwipe() {
        return false;
    }

    @Override
    public void dismiss() {
        AndroidUtilities.hideKeyboard(searchView.searchEditText);
        super.dismiss();
    }

    @SuppressLint("NewApi")
    private void updateLayout() {
        if (listView.getChildCount() <= 0) {
            return;
        }
        View child = listView.getChildAt(0);
        RecyclerListView.Holder holder = (RecyclerListView.Holder) listView.findContainingViewHolder(child);
        int top = child.getTop() - AndroidUtilities.dp(8);
        int newOffset = top > 0 && holder != null && holder.getAdapterPosition() == 0 ? top : 0;
        if (top >= 0 && holder != null && holder.getAdapterPosition() == 0) {
            newOffset = top;
            runShadowAnimation(false);
        } else {
            runShadowAnimation(true);
        }
        if (scrollOffsetY != newOffset) {
            listView.setTopGlowOffset(scrollOffsetY = (int) (newOffset));
            frameLayout.setTranslationY(scrollOffsetY);
            emptyView.setTranslationY(scrollOffsetY);
            containerView.invalidate();
        }
    }

    private void runShadowAnimation(final boolean show) {
        if (show && shadow.getTag() != null || !show && shadow.getTag() == null) {
            shadow.setTag(show ? null : 1);
            if (show) {
                shadow.setVisibility(View.VISIBLE);
            }
            if (shadowAnimation != null) {
                shadowAnimation.cancel();
            }
            shadowAnimation = new AnimatorSet();
            shadowAnimation.playTogether(ObjectAnimator.ofFloat(shadow, View.ALPHA, show ? 1.0f : 0.0f));
            shadowAnimation.setDuration(150);
            shadowAnimation.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (shadowAnimation != null && shadowAnimation.equals(animation)) {
                        if (!show) {
                            shadow.setVisibility(View.INVISIBLE);
                        }
                        shadowAnimation = null;
                    }
                }

                @Override
                public void onAnimationCancel(Animator animation) {
                    if (shadowAnimation != null && shadowAnimation.equals(animation)) {
                        shadowAnimation = null;
                    }
                }
            });
            shadowAnimation.start();
        }
    }

    private void showItemsAnimated(int from) {
        if (!isShowing()) {
            return;
        }
        listView.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                listView.getViewTreeObserver().removeOnPreDrawListener(this);
                int n = listView.getChildCount();
                AnimatorSet animatorSet = new AnimatorSet();
                for (int i = 0; i < n; i++) {
                    View child = listView.getChildAt(i);
                    int position = listView.getChildAdapterPosition(child);
                    if (position < from) {
                        continue;
                    }
                    if (position == 1 && listView.getAdapter() == searchListViewAdapter && child instanceof GraySectionCell) {
                        child = ((GraySectionCell) child).getTextView();
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
                return true;
            }
        });
    }

    private void loadChatParticipants(int offset, int count) {
        if (loadingUsers) {
            return;
        }
        contactsEndReached = false;
        loadChatParticipants(offset, count, true);
    }

    private void loadChatParticipants(int offset, int count, boolean reset) {
        if (!ChatObject.isChannel(currentChat)) {
            loadingUsers = false;
            participants.clear();
            contacts.clear();
            participantsMap.clear();
            contactsMap.clear();
            if (info != null) {
                int selfUserId = UserConfig.getInstance(currentAccount).clientUserId;
                for (int a = 0, size = info.participants.participants.size(); a < size; a++) {
                    TLRPC.ChatParticipant participant = info.participants.participants.get(a);
                    if (participant.user_id == selfUserId) {
                        continue;
                    }
                    if (ignoredUsers != null && ignoredUsers.indexOfKey(participant.user_id) >= 0) {
                        continue;
                    }
                    TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(participant.user_id);
                    if (user == null || !user.bot) {
                        participants.add(participant);
                        participantsMap.put(participant.user_id, participant);
                    }
                }
            }
            updateRows();
            if (listViewAdapter != null) {
                listViewAdapter.notifyDataSetChanged();
            }
        } else {
            loadingUsers = true;
            if (emptyView != null) {
                emptyView.showProgress(true, false);
            }
            if (listViewAdapter != null) {
                listViewAdapter.notifyDataSetChanged();
            }
            TLRPC.TL_channels_getParticipants req = new TLRPC.TL_channels_getParticipants();
            req.channel = MessagesController.getInputChannel(currentChat);
            if (info != null && info.participants_count <= 200) {
                req.filter = new TLRPC.TL_channelParticipantsRecent();
            } else {
                if (!contactsEndReached) {
                    delayResults = 2;
                    req.filter = new TLRPC.TL_channelParticipantsContacts();
                    contactsEndReached = true;
                    loadChatParticipants(0, 200, false);
                } else {
                    req.filter = new TLRPC.TL_channelParticipantsRecent();
                }
            }
            req.filter.q = "";
            req.offset = offset;
            req.limit = count;
            int reqId = ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                if (error == null) {
                    TLRPC.TL_channels_channelParticipants res = (TLRPC.TL_channels_channelParticipants) response;
                    MessagesController.getInstance(currentAccount).putUsers(res.users, false);
                    int selfId = UserConfig.getInstance(currentAccount).getClientUserId();
                    for (int a = 0; a < res.participants.size(); a++) {
                        if (res.participants.get(a).user_id == selfId) {
                            res.participants.remove(a);
                            break;
                        }
                    }
                    ArrayList<TLObject> objects;
                    SparseArray<TLObject> map;
                    delayResults--;
                    if (req.filter instanceof TLRPC.TL_channelParticipantsContacts) {
                        objects = contacts;
                        map = contactsMap;
                    } else {
                        objects = participants;
                        map = participantsMap;
                    }
                    objects.clear();
                    objects.addAll(res.participants);
                    for (int a = 0, size = res.participants.size(); a < size; a++) {
                        TLRPC.ChannelParticipant participant = res.participants.get(a);
                        map.put(participant.user_id, participant);
                    }
                    for (int a = 0, N = participants.size(); a < N; a++) {
                        TLRPC.ChannelParticipant participant = (TLRPC.ChannelParticipant) participants.get(a);
                        boolean remove = false;
                        if (contactsMap.get(participant.user_id) != null) {
                            remove = true;
                        } else if (ignoredUsers != null && ignoredUsers.indexOfKey(participant.user_id) >= 0) {
                            remove = true;
                        }
                        TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(participant.user_id);
                        if (user != null && user.bot) {
                            remove = true;
                        }
                        if (remove) {
                            participants.remove(a);
                            participantsMap.remove(participant.user_id);
                            a--;
                            N--;
                        }
                    }
                    try {
                        if (info.participants_count <= 200) {
                            int currentTime = ConnectionsManager.getInstance(currentAccount).getCurrentTime();
                            Collections.sort(objects, (lhs, rhs) -> {
                                TLRPC.ChannelParticipant p1 = (TLRPC.ChannelParticipant) lhs;
                                TLRPC.ChannelParticipant p2 = (TLRPC.ChannelParticipant) rhs;
                                TLRPC.User user1 = MessagesController.getInstance(currentAccount).getUser(p1.user_id);
                                TLRPC.User user2 = MessagesController.getInstance(currentAccount).getUser(p2.user_id);
                                int status1 = 0;
                                int status2 = 0;
                                if (user1 != null && user1.status != null) {
                                    if (user1.self) {
                                        status1 = currentTime + 50000;
                                    } else {
                                        status1 = user1.status.expires;
                                    }
                                }
                                if (user2 != null && user2.status != null) {
                                    if (user2.self) {
                                        status2 = currentTime + 50000;
                                    } else {
                                        status2 = user2.status.expires;
                                    }
                                }
                                if (status1 > 0 && status2 > 0) {
                                    if (status1 > status2) {
                                        return 1;
                                    } else if (status1 < status2) {
                                        return -1;
                                    }
                                    return 0;
                                } else if (status1 < 0 && status2 < 0) {
                                    if (status1 > status2) {
                                        return 1;
                                    } else if (status1 < status2) {
                                        return -1;
                                    }
                                    return 0;
                                } else if (status1 < 0 && status2 > 0 || status1 == 0 && status2 != 0) {
                                    return -1;
                                } else if (status2 < 0 && status1 > 0 || status2 == 0 && status1 != 0) {
                                    return 1;
                                }
                                return 0;
                            });
                        }
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                }
                if (delayResults <= 0) {
                    loadingUsers = false;
                    firstLoaded = true;
                    showItemsAnimated(listViewAdapter != null ? listViewAdapter.getItemCount() - 1 : 0);
                }
                updateRows();
                if (listViewAdapter != null) {
                    listViewAdapter.notifyDataSetChanged();
                    if (emptyView != null && listViewAdapter.getItemCount() == 0 && firstLoaded) {
                        emptyView.showProgress(false, true);
                    }
                }
            }));
        }
    }

    private class SearchAdapter extends RecyclerListView.SelectionAdapter {

        private Context mContext;
        private SearchAdapterHelper searchAdapterHelper;
        private Runnable searchRunnable;
        private int totalCount;

        private boolean searchInProgress;

        private int lastSearchId;

        private int emptyRow;
        private int lastRow;
        private int groupStartRow;
        private int globalStartRow;

        public SearchAdapter(Context context) {
            mContext = context;
            searchAdapterHelper = new SearchAdapterHelper(true);
            searchAdapterHelper.setDelegate(new SearchAdapterHelper.SearchAdapterHelperDelegate() {
                @Override
                public void onDataSetChanged(int searchId) {
                    if (searchId < 0 || searchId != lastSearchId || searchInProgress) {
                        return;
                    }
                    int oldItemCount = getItemCount() - 1;
                    boolean emptyViewWasVisible = emptyView.getVisibility() == View.VISIBLE;
                    notifyDataSetChanged();
                    if (getItemCount() > oldItemCount) {
                        showItemsAnimated(oldItemCount);
                    }
                    if (!searchAdapterHelper.isSearchInProgress()) {
                        if (listView.emptyViewIsVisible()) {
                            emptyView.showProgress(false, emptyViewWasVisible);
                        }
                    }
                }

                @Override
                public SparseArray<TLRPC.TL_groupCallParticipant> getExcludeCallParticipants() {
                    return ignoredUsers;
                }
            });
        }

        public void searchUsers(final String query) {
            if (searchRunnable != null) {
                AndroidUtilities.cancelRunOnUIThread(searchRunnable);
                searchRunnable = null;
            }
            searchAdapterHelper.mergeResults(null);
            searchAdapterHelper.queryServerSearch(null, true, false, true, false, false, currentChat.id, false, ChatUsersActivity.TYPE_USERS, -1);

            if (!TextUtils.isEmpty(query)) {
                emptyView.showProgress(true, true);
                listView.setAnimateEmptyView(false, 0);
                notifyDataSetChanged();
                listView.setAnimateEmptyView(true, 0);
                searchInProgress = true;
                int searchId = ++lastSearchId;
                AndroidUtilities.runOnUIThread(searchRunnable = () -> {
                    if (searchRunnable == null) {
                        return;
                    }
                    searchRunnable = null;
                    processSearch(query, searchId);
                }, 300);

                if (listView.getAdapter() != searchListViewAdapter) {
                    listView.setAdapter(searchListViewAdapter);
                }
            } else {
                lastSearchId = -1;
            }
        }

        private void processSearch(final String query, int searchId) {
            AndroidUtilities.runOnUIThread(() -> {
                searchRunnable = null;

                final ArrayList<TLObject> participantsCopy = !ChatObject.isChannel(currentChat) && info != null ? new ArrayList<>(info.participants.participants) : null;

                if (participantsCopy != null) {
                    Utilities.searchQueue.postRunnable(() -> {
                        String search1 = query.trim().toLowerCase();
                        if (search1.length() == 0) {
                            updateSearchResults(new ArrayList<>(), searchId);
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
                        ArrayList<TLObject> resultArray2 = new ArrayList<>();

                        if (participantsCopy != null) {
                            for (int a = 0, N = participantsCopy.size(); a < N; a++) {
                                int userId;
                                TLObject o = participantsCopy.get(a);
                                if (o instanceof TLRPC.ChatParticipant) {
                                    userId = ((TLRPC.ChatParticipant) o).user_id;
                                } else if (o instanceof TLRPC.ChannelParticipant) {
                                    userId = ((TLRPC.ChannelParticipant) o).user_id;
                                } else {
                                    continue;
                                }
                                TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(userId);
                                if (UserObject.isUserSelf(user)) {
                                    continue;
                                }

                                String name = UserObject.getUserName(user).toLowerCase();
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
                                        resultArray2.add(o);
                                        break;
                                    }
                                }
                            }
                        }
                        updateSearchResults(resultArray2, searchId);
                    });
                } else {
                    searchInProgress = false;
                }
                searchAdapterHelper.queryServerSearch(query, ChatObject.canAddUsers(currentChat), false, true, false, false, ChatObject.isChannel(currentChat) ? currentChat.id : 0, false, ChatUsersActivity.TYPE_USERS, searchId);
            });
        }

        private void updateSearchResults(final ArrayList<TLObject> participants, int searchId) {
            AndroidUtilities.runOnUIThread(() -> {
                if (searchId != lastSearchId) {
                    return;
                }
                searchInProgress = false;
                if (!ChatObject.isChannel(currentChat)) {
                    searchAdapterHelper.addGroupMembers(participants);
                }
                int oldItemCount = getItemCount() - 1;
                boolean emptyViewWasVisible = emptyView.getVisibility() == View.VISIBLE;
                notifyDataSetChanged();
                if (getItemCount() > oldItemCount) {
                    showItemsAnimated(oldItemCount);
                }
                if (!searchInProgress && !searchAdapterHelper.isSearchInProgress()) {
                    if (listView.emptyViewIsVisible()) {
                        emptyView.showProgress(false, emptyViewWasVisible);
                    }
                }
            });
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            if (holder.itemView instanceof ManageChatUserCell) {
                ManageChatUserCell cell = (ManageChatUserCell) holder.itemView;
                if (invitedUsers.contains(cell.getUserId())) {
                    return false;
                }
            }
            return holder.getItemViewType() == 0;
        }

        @Override
        public int getItemCount() {
            return totalCount;
        }

        @Override
        public void notifyDataSetChanged() {
            totalCount = 0;
            emptyRow = totalCount++;
            int count = searchAdapterHelper.getGroupSearch().size();
            if (count != 0) {
                groupStartRow = totalCount;
                totalCount += count + 1;
            } else {
                groupStartRow = -1;
            }
            count = searchAdapterHelper.getGlobalSearch().size();
            if (count != 0) {
                globalStartRow = totalCount;
                totalCount += count + 1;
            } else {
                globalStartRow = -1;
            }
            lastRow = totalCount++;
            super.notifyDataSetChanged();
        }

        public TLObject getItem(int i) {
            if (groupStartRow >= 0 && i > groupStartRow && i < groupStartRow + 1 + searchAdapterHelper.getGroupSearch().size()) {
                return searchAdapterHelper.getGroupSearch().get(i - groupStartRow - 1);
            }
            if (globalStartRow >= 0 && i > globalStartRow && i < globalStartRow + 1 + searchAdapterHelper.getGlobalSearch().size()) {
                return searchAdapterHelper.getGlobalSearch().get(i - globalStartRow - 1);
            }
            return null;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case 0:
                    ManageChatUserCell manageChatUserCell = new ManageChatUserCell(mContext, 2, 2, false);
                    manageChatUserCell.setCustomRightImage(R.drawable.msg_invited);
                    manageChatUserCell.setNameColor(Theme.getColor(Theme.key_voipgroup_nameText));
                    manageChatUserCell.setStatusColors(Theme.getColor(Theme.key_voipgroup_lastSeenTextUnscrolled), Theme.getColor(Theme.key_voipgroup_listeningText));
                    manageChatUserCell.setDividerColor(Theme.key_voipgroup_listViewBackground);
                    view = manageChatUserCell;
                    break;
                case 1:
                    GraySectionCell cell = new GraySectionCell(mContext);
                    cell.setBackgroundColor(Theme.getColor(Theme.key_voipgroup_actionBarUnscrolled));
                    cell.setTextColor(Theme.key_voipgroup_searchPlaceholder);
                    view = cell;
                    break;
                case 2:
                    view = new View(mContext);
                    view.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, AndroidUtilities.dp(56)));
                    break;
                case 3:
                default:
                    view = new View(mContext);
                    break;
            }
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            switch (holder.getItemViewType()) {
                case 0: {
                    TLObject object = getItem(position);
                    TLRPC.User user;
                    if (object instanceof TLRPC.User) {
                        user = (TLRPC.User) object;
                    } else if (object instanceof TLRPC.ChannelParticipant) {
                        user = MessagesController.getInstance(currentAccount).getUser(((TLRPC.ChannelParticipant) object).user_id);
                    } else if (object instanceof TLRPC.ChatParticipant) {
                        user = MessagesController.getInstance(currentAccount).getUser(((TLRPC.ChatParticipant) object).user_id);
                    } else {
                        return;
                    }

                    String un = user.username;
                    CharSequence username = null;
                    SpannableStringBuilder name = null;

                    int count = searchAdapterHelper.getGroupSearch().size();
                    boolean ok = false;
                    String nameSearch = null;
                    if (count != 0) {
                        if (count + 1 > position) {
                            nameSearch = searchAdapterHelper.getLastFoundChannel();
                            ok = true;
                        } else {
                            position -= count + 1;
                        }
                    }
                    if (!ok && un != null) {
                        count = searchAdapterHelper.getGlobalSearch().size();
                        if (count != 0) {
                            if (count + 1 > position) {
                                String foundUserName = searchAdapterHelper.getLastFoundUsername();
                                if (foundUserName.startsWith("@")) {
                                    foundUserName = foundUserName.substring(1);
                                }
                                try {
                                    int index;
                                    SpannableStringBuilder spannableStringBuilder = new SpannableStringBuilder();
                                    spannableStringBuilder.append("@");
                                    spannableStringBuilder.append(un);
                                    if ((index = AndroidUtilities.indexOfIgnoreCase(un, foundUserName)) != -1) {
                                        int len = foundUserName.length();
                                        if (index == 0) {
                                            len++;
                                        } else {
                                            index++;
                                        }
                                        spannableStringBuilder.setSpan(new ForegroundColorSpan(Theme.getColor(Theme.key_voipgroup_listeningText)), index, index + len, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                                    }
                                    username = spannableStringBuilder;
                                } catch (Exception e) {
                                    username = un;
                                    FileLog.e(e);
                                }
                            }
                        }
                    }

                    if (nameSearch != null) {
                        String u = UserObject.getUserName(user);
                        name = new SpannableStringBuilder(u);
                        int idx = AndroidUtilities.indexOfIgnoreCase(u, nameSearch);
                        if (idx != -1) {
                            name.setSpan(new ForegroundColorSpan(Theme.getColor(Theme.key_voipgroup_listeningText)), idx, idx + nameSearch.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        }
                    }

                    ManageChatUserCell userCell = (ManageChatUserCell) holder.itemView;
                    userCell.setTag(position);
                    userCell.setCustomImageVisible(invitedUsers.contains(user.id));
                    userCell.setData(user, name, username, false);

                    break;
                }
                case 1: {
                    GraySectionCell sectionCell = (GraySectionCell) holder.itemView;
                    if (position == groupStartRow) {
                        sectionCell.setText(LocaleController.getString("ChannelMembers", R.string.ChannelMembers));
                    } else if (position == globalStartRow) {
                        sectionCell.setText(LocaleController.getString("GlobalSearch", R.string.GlobalSearch));
                    }
                    break;
                }
            }
        }

        @Override
        public void onViewRecycled(RecyclerView.ViewHolder holder) {
            if (holder.itemView instanceof ManageChatUserCell) {
                ((ManageChatUserCell) holder.itemView).recycle();
            }
        }

        @Override
        public int getItemViewType(int i) {
            if (i == emptyRow) {
                return 2;
            } else if (i == lastRow) {
                return 3;
            }
            if (i == globalStartRow || i == groupStartRow) {
                return 1;
            }
            return 0;
        }
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {

        private Context mContext;

        public ListAdapter(Context context) {
            mContext = context;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            if (holder.itemView instanceof ManageChatUserCell) {
                ManageChatUserCell cell = (ManageChatUserCell) holder.itemView;
                if (invitedUsers.contains(cell.getUserId())) {
                    return false;
                }
            }
            int viewType = holder.getItemViewType();
            return viewType == 0 || viewType == 1;
        }

        @Override
        public int getItemCount() {
            return rowCount;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case 0:
                    ManageChatUserCell manageChatUserCell = new ManageChatUserCell(mContext, 6, 2, false);
                    manageChatUserCell.setCustomRightImage(R.drawable.msg_invited);
                    manageChatUserCell.setNameColor(Theme.getColor(Theme.key_voipgroup_nameText));
                    manageChatUserCell.setStatusColors(Theme.getColor(Theme.key_voipgroup_lastSeenTextUnscrolled), Theme.getColor(Theme.key_voipgroup_listeningText));
                    manageChatUserCell.setDividerColor(Theme.key_voipgroup_actionBar);
                    view = manageChatUserCell;
                    break;
                case 1:
                    ManageChatTextCell manageChatTextCell = new ManageChatTextCell(mContext);
                    manageChatTextCell.setColors(Theme.key_voipgroup_listeningText, Theme.key_voipgroup_listeningText);
                    manageChatTextCell.setDividerColor(Theme.key_voipgroup_actionBar);
                    view = manageChatTextCell;
                    break;
                case 2:
                    GraySectionCell cell = new GraySectionCell(mContext);
                    cell.setBackgroundColor(Theme.getColor(Theme.key_voipgroup_actionBarUnscrolled));
                    cell.setTextColor(Theme.key_voipgroup_searchPlaceholder);
                    view = cell;
                    break;
                case 3:
                    view = new View(mContext);
                    view.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, AndroidUtilities.dp(56)));
                    break;
                case 5:
                    FlickerLoadingView flickerLoadingView = new FlickerLoadingView(mContext);
                    flickerLoadingView.setViewType(FlickerLoadingView.USERS_TYPE);
                    flickerLoadingView.setIsSingleCell(true);
                    flickerLoadingView.setColors(Theme.key_voipgroup_inviteMembersBackground, Theme.key_voipgroup_searchBackground, Theme.key_voipgroup_actionBarUnscrolled);
                    view = flickerLoadingView;
                    break;
                case 4:
                default:
                    view = new View(mContext);
                    break;
            }
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            switch (holder.getItemViewType()) {
                case 0:
                    ManageChatUserCell userCell = (ManageChatUserCell) holder.itemView;
                    userCell.setTag(position);
                    TLObject item = getItem(position);
                    int lastRow;

                    if (position >= participantsStartRow && position < participantsEndRow) {
                        lastRow = participantsEndRow;
                    } else {
                        lastRow = contactsEndRow;
                    }

                    int userId;
                    if (item instanceof TLRPC.ChannelParticipant) {
                        TLRPC.ChannelParticipant participant = (TLRPC.ChannelParticipant) item;
                        userId = participant.user_id;
                    } else {
                        TLRPC.ChatParticipant participant = (TLRPC.ChatParticipant) item;
                        userId = participant.user_id;
                    }
                    TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(userId);
                    if (user != null) {
                        userCell.setCustomImageVisible(invitedUsers.contains(user.id));
                        userCell.setData(user, null, null, position != lastRow - 1);
                    }
                    break;
                case 1:
                    ManageChatTextCell actionCell = (ManageChatTextCell) holder.itemView;
                    if (position == addNewRow) {
                        boolean showDivider = !(loadingUsers && !firstLoaded) && membersHeaderRow == -1 && !participants.isEmpty();
                        actionCell.setText(LocaleController.getString("VoipGroupCopyInviteLink", R.string.VoipGroupCopyInviteLink), null, R.drawable.msg_link, 7, showDivider);
                    }
                    break;
                case 2:
                    GraySectionCell sectionCell = (GraySectionCell) holder.itemView;
                    if (position == membersHeaderRow) {
                        sectionCell.setText(LocaleController.getString("ChannelOtherMembers", R.string.ChannelOtherMembers));
                    } else if (position == contactsHeaderRow) {
                        sectionCell.setText(LocaleController.getString("GroupContacts", R.string.GroupContacts));
                    }
                    break;
            }
        }

        @Override
        public void onViewRecycled(RecyclerView.ViewHolder holder) {
            if (holder.itemView instanceof ManageChatUserCell) {
                ((ManageChatUserCell) holder.itemView).recycle();
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (position >= participantsStartRow && position < participantsEndRow ||
                    position >= contactsStartRow && position < contactsEndRow) {
                return 0;
            } else if (position == addNewRow) {
                return 1;
            } else if (position == membersHeaderRow || position == contactsHeaderRow) {
                return 2;
            } else if (position == emptyRow) {
                return 3;
            } else if (position == lastRow) {
                return 4;
            } else if (position == flickerProgressRow) {
                return 5;
            }
            return 0;
        }

        public TLObject getItem(int position) {
            if (position >= participantsStartRow && position < participantsEndRow) {
                return participants.get(position - participantsStartRow);
            } else if (position >= contactsStartRow && position < contactsEndRow) {
                return contacts.get(position - contactsStartRow);
            }
            return null;
        }
    }

}
