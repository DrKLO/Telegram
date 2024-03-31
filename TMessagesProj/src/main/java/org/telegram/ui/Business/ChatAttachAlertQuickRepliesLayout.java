package org.telegram.ui.Business;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.os.Build;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.LinearSmoothScroller;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.PhoneFormat.PhoneFormat;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Components.ChatActivityInterface;
import org.telegram.ui.Components.ChatAttachAlert;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.FillLastLinearLayoutManager;
import org.telegram.ui.Components.EmptyTextProgressView;
import org.telegram.ui.Components.SearchField;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.CheckBox2;
import org.telegram.ui.Components.AvatarDrawable;

import java.util.ArrayList;
import java.util.HashSet;

public class ChatAttachAlertQuickRepliesLayout extends ChatAttachAlert.AttachAlertLayout implements NotificationCenter.NotificationCenterDelegate {

    private FrameLayout frameLayout;
    private RecyclerListView listView;
    private FillLastLinearLayoutManager layoutManager;
    private HashSet<Integer> selectedReplies = new HashSet<>();
    private ShareAdapter listAdapter;
    private ShareSearchAdapter searchAdapter;
    private EmptyTextProgressView emptyView;
    private View shadow;
    private AnimatorSet shadowAnimation;
    private SearchField searchField;

    private boolean ignoreLayout;

    public static class UserCell extends FrameLayout {

        private final Theme.ResourcesProvider resourcesProvider;
        private BackupImageView avatarImageView;
        private SimpleTextView nameTextView;
        private SimpleTextView statusTextView;
        private CheckBox2 checkBox;

        private AvatarDrawable avatarDrawable;
        private TLRPC.User currentUser;
        private int currentId;

        private CharSequence currentName;
        private CharSequence currentStatus;
        private TLRPC.User formattedPhoneNumberUser;
        private CharSequence formattedPhoneNumber;

        private String lastName;
        private int lastStatus;
        private TLRPC.FileLocation lastAvatar;

        private int currentAccount = UserConfig.selectedAccount;

        private boolean needDivider;

        public UserCell(Context context, Theme.ResourcesProvider resourcesProvider) {
            super(context);
            this.resourcesProvider = resourcesProvider;

            avatarDrawable = new AvatarDrawable(resourcesProvider);

            avatarImageView = new BackupImageView(context);
            avatarImageView.setRoundRadius(AndroidUtilities.dp(23));
            addView(avatarImageView, LayoutHelper.createFrame(46, 46, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, LocaleController.isRTL ? 0 : 14, 9, LocaleController.isRTL ? 14 : 0, 0));

            nameTextView = new SimpleTextView(context) {
                @Override
                public boolean setText(CharSequence value, boolean force) {
                    value = Emoji.replaceEmoji(value, getPaint().getFontMetricsInt(), AndroidUtilities.dp(14), false);
                    return super.setText(value, force);
                }
            };
            NotificationCenter.listenEmojiLoading(nameTextView);
            nameTextView.setTextColor(getThemedColor(Theme.key_dialogTextBlack));
            nameTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            nameTextView.setTextSize(16);
            nameTextView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP);
            addView(nameTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 20, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, LocaleController.isRTL ? 28 : 72, 12, LocaleController.isRTL ? 72 : 28, 0));

            statusTextView = new SimpleTextView(context);
            statusTextView.setTextSize(13);
            statusTextView.setTextColor(getThemedColor(Theme.key_dialogTextGray2));
            statusTextView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP);
            addView(statusTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 20, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, LocaleController.isRTL ? 28 : 72, 36, LocaleController.isRTL ? 72 : 28, 0));

            checkBox = new CheckBox2(context, 21, resourcesProvider);
            checkBox.setColor(-1, Theme.key_windowBackgroundWhite, Theme.key_checkboxCheck);
            checkBox.setDrawUnchecked(false);
            checkBox.setDrawBackgroundAsArc(3);
            addView(checkBox, LayoutHelper.createFrame(24, 24, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, LocaleController.isRTL ? 0 : 44, 37, LocaleController.isRTL ? 44 : 0, 0));
        }

        public void setCurrentId(int id) {
            currentId = id;
        }

        public void setData(TLRPC.User user, CharSequence name, CharSequence status, boolean divider) {
            if (user == null && name == null && status == null) {
                currentStatus = null;
                currentName = null;
                nameTextView.setText("");
                statusTextView.setText("");
                avatarImageView.setImageDrawable(null);
                return;
            }
            currentStatus = status;
            currentName = name;
            currentUser = user;
            needDivider = divider;
            setWillNotDraw(!needDivider);
            update(0);
        }

        public interface CharSequenceCallback {
            CharSequence run();
        }

        public void setData(TLRPC.User user, CharSequence name, CharSequenceCallback status, boolean divider) {
            setData(user, name, (CharSequence) null, divider);
            Utilities.globalQueue.postRunnable(() -> {
                final CharSequence newCurrentStatus = status.run();
                AndroidUtilities.runOnUIThread(() -> {
                    setStatus(newCurrentStatus);
                });
            });
        }

        public void setChecked(boolean checked, boolean animated) {
            if (checkBox.getVisibility() != VISIBLE) {
                checkBox.setVisibility(VISIBLE);
            }
            checkBox.setChecked(checked, animated);
        }

        public void setStatus(CharSequence status) {
            currentStatus = status;
            if (currentStatus != null) {
                statusTextView.setText(currentStatus);
            } else if (currentUser != null) {
                if (TextUtils.isEmpty(currentUser.phone)) {
                    statusTextView.setText(LocaleController.getString("NumberUnknown", R.string.NumberUnknown));
                } else {
                    if (formattedPhoneNumberUser != currentUser && formattedPhoneNumber != null) {
                        statusTextView.setText(formattedPhoneNumber);
                    } else {
                        statusTextView.setText("");
                        Utilities.globalQueue.postRunnable(() -> {
                            if (currentUser != null) {
                                formattedPhoneNumber = PhoneFormat.getInstance().format("+" + currentUser.phone);
                                formattedPhoneNumberUser = currentUser;
                                AndroidUtilities.runOnUIThread(() -> statusTextView.setText(formattedPhoneNumber));
                            }
                        });
                    }
                }
            }
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(
                    MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(64) + (needDivider ? 1 : 0), MeasureSpec.EXACTLY)
            );
        }

        public void update(int mask) {
            TLRPC.FileLocation photo = null;
            String newName = null;
            if (currentUser != null && currentUser.photo != null) {
                photo = currentUser.photo.photo_small;
            }

            if (mask != 0) {
                boolean continueUpdate = false;
                if ((mask & MessagesController.UPDATE_MASK_AVATAR) != 0) {
                    if (lastAvatar != null && photo == null || lastAvatar == null && photo != null || lastAvatar != null && photo != null && (lastAvatar.volume_id != photo.volume_id || lastAvatar.local_id != photo.local_id)) {
                        continueUpdate = true;
                    }
                }
                if (currentUser != null && !continueUpdate && (mask & MessagesController.UPDATE_MASK_STATUS) != 0) {
                    int newStatus = 0;
                    if (currentUser.status != null) {
                        newStatus = currentUser.status.expires;
                    }
                    if (newStatus != lastStatus) {
                        continueUpdate = true;
                    }
                }
                if (!continueUpdate && currentName == null && lastName != null && (mask & MessagesController.UPDATE_MASK_NAME) != 0) {
                    if (currentUser != null) {
                        newName = UserObject.getUserName(currentUser);
                    }
                    if (!newName.equals(lastName)) {
                        continueUpdate = true;
                    }
                }
                if (!continueUpdate) {
                    return;
                }
            }

            if (currentUser != null) {
                avatarDrawable.setInfo(currentAccount, currentUser);
                if (currentUser.status != null) {
                    lastStatus = currentUser.status.expires;
                } else {
                    lastStatus = 0;
                }
            } else if (currentName != null) {
                avatarDrawable.setInfo(currentId, currentName.toString(), null);
            } else {
                avatarDrawable.setInfo(currentId, "#", null);
            }

            if (currentName != null) {
                lastName = null;
                nameTextView.setText(currentName);
            } else {
                if (currentUser != null) {
                    lastName = newName == null ? UserObject.getUserName(currentUser) : newName;
                } else {
                    lastName = "";
                }
                nameTextView.setText(lastName);
            }

            setStatus(currentStatus);

            lastAvatar = photo;
            if (currentUser != null) {
                avatarImageView.setForUserOrChat(currentUser, avatarDrawable);
            } else {
                avatarImageView.setImageDrawable(avatarDrawable);
            }
        }

        @Override
        public boolean hasOverlappingRendering() {
            return false;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (needDivider) {
                canvas.drawLine(LocaleController.isRTL ? 0 : AndroidUtilities.dp(70), getMeasuredHeight() - 1, getMeasuredWidth() - (LocaleController.isRTL ? AndroidUtilities.dp(70) : 0), getMeasuredHeight() - 1, Theme.dividerPaint);
            }
        }

        protected int getThemedColor(int key) {
            return Theme.getColor(key, resourcesProvider);
        }
    }

    public ChatAttachAlertQuickRepliesLayout(ChatAttachAlert alert, Context context, Theme.ResourcesProvider resourcesProvider) {
        super(alert, context, resourcesProvider);

        searchAdapter = new ShareSearchAdapter(context);

        frameLayout = new FrameLayout(context);
        frameLayout.setBackgroundColor(getThemedColor(Theme.key_dialogBackground));

        searchField = new SearchField(context, false, resourcesProvider) {
            @Override
            public void onTextChange(String text) {
                if (text.length() != 0) {
                    if (emptyView != null) {
                        emptyView.setText(LocaleController.getString("NoResult", R.string.NoResult));
                    }
                } else {
                    if (listView.getAdapter() != listAdapter) {
                        int top = getCurrentTop();
                        emptyView.showTextView();
                        listView.setAdapter(listAdapter);
                        listAdapter.notifyDataSetChanged();
                        if (top > 0) {
                            layoutManager.scrollToPositionWithOffset(0, -top);
                        }
                    }
                }
                if (searchAdapter != null) {
                    searchAdapter.search(text);
                }
            }

            @Override
            public boolean onInterceptTouchEvent(MotionEvent ev) {
                parentAlert.makeFocusable(getSearchEditText(), true);
                return super.onInterceptTouchEvent(ev);
            }

            @Override
            public void processTouchEvent(MotionEvent event) {
                MotionEvent e = MotionEvent.obtain(event);
                e.setLocation(e.getRawX(), e.getRawY() - parentAlert.getSheetContainer().getTranslationY() - AndroidUtilities.dp(58));
                listView.dispatchTouchEvent(e);
                e.recycle();
            }

            @Override
            protected void onFieldTouchUp(EditTextBoldCursor editText) {
                parentAlert.makeFocusable(editText, true);
            }
        };
        searchField.setHint(LocaleController.getString(R.string.BusinessRepliesSearch));
        frameLayout.addView(searchField, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT));

        emptyView = new EmptyTextProgressView(context, null, resourcesProvider);
        emptyView.showTextView();
        addView(emptyView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT, 0, 52, 0, 0));

        listView = new RecyclerListView(context, resourcesProvider) {
            @Override
            protected boolean allowSelectChildAtPosition(float x, float y) {
                return y >= parentAlert.scrollOffsetY[0] + AndroidUtilities.dp(30) + (Build.VERSION.SDK_INT >= 21 && !parentAlert.inBubbleMode ? AndroidUtilities.statusBarHeight : 0);
            }

            @Override
            public void onScrolled(int dx, int dy) {
                super.onScrolled(dx, dy);
//                if (searchField != null) {
//                    AndroidUtilities.hideKeyboard(searchField.getSearchEditText());
//                }
            }
        };
        NotificationCenter.getInstance(UserConfig.selectedAccount).listenGlobal(listView, NotificationCenter.emojiLoaded, args -> {
            AndroidUtilities.forEachViews(listView, view -> {
                if (view instanceof QuickRepliesActivity.QuickReplyView) {
                    ((QuickRepliesActivity.QuickReplyView) view).invalidateEmojis();
                }
            });
        });
        listView.setClipToPadding(false);
        listView.setLayoutManager(layoutManager = new FillLastLinearLayoutManager(getContext(), LinearLayoutManager.VERTICAL, false, AndroidUtilities.dp(9), listView) {
            @Override
            public void smoothScrollToPosition(RecyclerView recyclerView, RecyclerView.State state, int position) {
                LinearSmoothScroller linearSmoothScroller = new LinearSmoothScroller(recyclerView.getContext()) {
                    @Override
                    public int calculateDyToMakeVisible(View view, int snapPreference) {
                        int dy = super.calculateDyToMakeVisible(view, snapPreference);
                        dy -= (listView.getPaddingTop() - AndroidUtilities.dp(8));
                        return dy;
                    }

                    @Override
                    protected int calculateTimeForDeceleration(int dx) {
                        return super.calculateTimeForDeceleration(dx) * 2;
                    }
                };
                linearSmoothScroller.setTargetPosition(position);
                startSmoothScroll(linearSmoothScroller);
            }
        });
        layoutManager.setBind(false);
        listView.setHorizontalScrollBarEnabled(false);
        listView.setVerticalScrollBarEnabled(false);
        listView.setClipToPadding(false);
        listView.setPadding(0, 0, 0, AndroidUtilities.dp(48));
        addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT, 0, 0, 0, 0));
        listView.setAdapter(listAdapter = new ShareAdapter(context));
        listView.setGlowColor(getThemedColor(Theme.key_dialogScrollGlow));
        listView.setOnItemClickListener((view, position) -> {
            Object object;
            if (listView.getAdapter() == searchAdapter) {
                object = searchAdapter.getItem(position);
            } else {
                int section = listAdapter.getSectionForPosition(position);
                int row = listAdapter.getPositionInSectionForPosition(position);
                if (row < 0 || section < 0) {
                    return;
                }
                object = listAdapter.getItem(section, row);
            }
            if (object instanceof QuickRepliesController.QuickReply) {
                long dialogId;
                if (parentAlert.baseFragment instanceof ChatActivityInterface) {
                    dialogId = ((ChatActivityInterface) parentAlert.baseFragment).getDialogId();
                } else return;
                QuickRepliesController.getInstance(UserConfig.selectedAccount).sendQuickReplyTo(dialogId, (QuickRepliesController.QuickReply) object);
                parentAlert.dismiss();
            }
        });
        listView.setOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                parentAlert.updateLayout(ChatAttachAlertQuickRepliesLayout.this, true, dy);
                updateEmptyViewPosition();
            }
        });

        FrameLayout.LayoutParams frameLayoutParams = new FrameLayout.LayoutParams(LayoutHelper.MATCH_PARENT, AndroidUtilities.getShadowHeight(), Gravity.TOP | Gravity.LEFT);
        frameLayoutParams.topMargin = AndroidUtilities.dp(58);
        shadow = new View(context);
        shadow.setBackgroundColor(getThemedColor(Theme.key_dialogShadowLine));
        shadow.setAlpha(0.0f);
        shadow.setTag(1);
        addView(shadow, frameLayoutParams);

        addView(frameLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 58, Gravity.LEFT | Gravity.TOP));

        updateEmptyView();
    }

    @Override
    public int getSelectedItemsCount() {
        return 0;
    }

    private void showErrorBox(String error) {
        new AlertDialog.Builder(getContext(), resourcesProvider).setTitle(LocaleController.getString("AppName", R.string.AppName)).setMessage(error).setPositiveButton(LocaleController.getString("OK", R.string.OK), null).show();
    }

    @Override
    public void sendSelectedItems(boolean notify, int scheduleDate) {

    }

    @Override
    public void scrollToTop() {
        listView.smoothScrollToPosition(0);
    }

    @Override
    public int getCurrentItemTop() {
        if (listView.getChildCount() <= 0) {
            return Integer.MAX_VALUE;
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
        frameLayout.setTranslationY(newOffset);
        return newOffset + AndroidUtilities.dp(12);
    }

    @Override
    public int getFirstOffset() {
        return getListTopPadding() + AndroidUtilities.dp(4);
    }

    @Override
    public void setTranslationY(float translationY) {
        super.setTranslationY(translationY);
        parentAlert.getSheetContainer().invalidate();
    }

    @Override
    public int getListTopPadding() {
        return listView.getPaddingTop();
    }

    @Override
    public void onPreMeasure(int availableWidth, int availableHeight) {
        int padding;
        if (parentAlert.sizeNotifierFrameLayout.measureKeyboardHeight() > AndroidUtilities.dp(20)) {
            padding = AndroidUtilities.dp(8);
            parentAlert.setAllowNestedScroll(false);
        } else {
            if (!AndroidUtilities.isTablet() && AndroidUtilities.displaySize.x > AndroidUtilities.displaySize.y) {
                padding = (int) (availableHeight / 3.5f);
            } else {
                padding = (availableHeight / 5 * 2);
            }
            parentAlert.setAllowNestedScroll(true);
        }
        if (listView.getPaddingTop() != padding) {
            ignoreLayout = true;
            listView.setPadding(0, padding, 0, AndroidUtilities.dp(48));
            ignoreLayout = false;
        }
    }

    @Override
    public void requestLayout() {
        if (ignoreLayout) {
            return;
        }
        super.requestLayout();
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

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
    }

    @Override
    public void onDestroy() {

    }

    @Override
    public void onShow(ChatAttachAlert.AttachAlertLayout previousLayout) {
        layoutManager.scrollToPositionWithOffset(0, 0);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        updateEmptyViewPosition();
    }

    private void updateEmptyViewPosition() {
        if (emptyView.getVisibility() != VISIBLE) {
            return;
        }
        View child = listView.getChildAt(0);
        if (child == null) {
            return;
        }
        emptyView.setTranslationY((emptyView.getMeasuredHeight() - getMeasuredHeight() + child.getTop()) / 2);
    }

    private void updateEmptyView() {
        boolean visible = listView.getAdapter().getItemCount() == 2;
        emptyView.setVisibility(visible ? VISIBLE : GONE);
        updateEmptyViewPosition();
    }

    public class ShareAdapter extends RecyclerListView.SectionsAdapter {

        private ArrayList<QuickRepliesController.QuickReply> replies = new ArrayList<>();
        private int currentAccount = UserConfig.selectedAccount;
        private Context mContext;

        public ShareAdapter(Context context) {
            mContext = context;
            replies.addAll(QuickRepliesController.getInstance(currentAccount).getFilteredReplies());
        }

        public Object getItem(int section, int position) {
            if (section == 0) {
                return null;
            }
            section--;
            if (position < 0 || position >= replies.size()) return null;
            return replies.get(position);
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder, int section, int row) {
            if (section == 0 || section == getSectionCount() - 1) {
                return false;
            }
            section--;
            return row < replies.size();
        }

        @Override
        public int getSectionCount() {
            return 3;
        }

        @Override
        public int getCountForSection(int section) {
            if (section == 0 || section == getSectionCount() - 1) {
                return 1;
            }
            section--;
            return replies.size();
        }

        @Override
        public View getSectionHeaderView(int section, View view) {
            return null;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case 0: {
                    view = new QuickRepliesActivity.QuickReplyView(mContext, false, resourcesProvider);
                    break;
                }
                case 1: {
                    view = new View(mContext);
                    view.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, AndroidUtilities.dp(56)));
                    break;
                }
                case 2:
                default: {
                    view = new View(mContext);
                    break;
                }
            }
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(int section, int position, RecyclerView.ViewHolder holder) {
            if (holder.getItemViewType() == 0) {
                QuickRepliesActivity.QuickReplyView cell = (QuickRepliesActivity.QuickReplyView) holder.itemView;
                Object object = getItem(section, position);
                boolean divider = section != getSectionCount() - 2 || position != getCountForSection(section) - 1;
                if (object instanceof QuickRepliesController.QuickReply) {
                    cell.set((QuickRepliesController.QuickReply) object, null, divider);
                    cell.setChecked(selectedReplies.contains(((QuickRepliesController.QuickReply) object).id), false);
                }
            }
        }

        @Override
        public int getItemViewType(int section, int position) {
            if (section == 0) {
                return 1;
            } else if (section == getSectionCount() - 1) {
                return 2;
            }
            return 0;
        }

        @Override
        public String getLetter(int position) {
            return null;
        }

        @Override
        public void getPositionForScrollProgress(RecyclerListView listView, float progress, int[] position) {
            position[0] = 0;
            position[1] = 0;
        }

        @Override
        public void notifyDataSetChanged() {
            replies.clear();
            replies.addAll(QuickRepliesController.getInstance(currentAccount).getFilteredReplies());
            super.notifyDataSetChanged();
            updateEmptyView();
        }
    }

    public class ShareSearchAdapter extends RecyclerListView.SelectionAdapter {

        private Context mContext;
        private ArrayList<QuickRepliesController.QuickReply> searchResult = new ArrayList<>();
        private Runnable searchRunnable;
        private int lastSearchId;
        public String lastQuery;

        public ShareSearchAdapter(Context context) {
            mContext = context;
        }

        public void search(final String query) {
            if (searchRunnable != null) {
                Utilities.searchQueue.cancelRunnable(searchRunnable);
                searchRunnable = null;
            }
            searchResult.clear();
            lastQuery = query;
            if (query != null) {
                String q = AndroidUtilities.translitSafe(query);
                if (q.startsWith("/")) q = q.substring(1);
                QuickRepliesController controller = QuickRepliesController.getInstance(UserConfig.selectedAccount);
                for (int i = 0; i < controller.replies.size(); ++i) {
                    QuickRepliesController.QuickReply reply = controller.replies.get(i);
                    if (reply.isSpecial()) continue;
                    String rq = AndroidUtilities.translitSafe(reply.name);
                    if (rq.startsWith(q) || rq.contains(" " + q)) {
                        searchResult.add(reply);
                    }
                }
            }
            if (listView.getAdapter() != searchAdapter) {
                listView.setAdapter(searchAdapter);
            }
            notifyDataSetChanged();
        }

        @Override
        public int getItemCount() {
            return searchResult.size() + 2;
        }

        public Object getItem(int position) {
            position--;
            if (position < 0 || position >= searchResult.size()) {
                return null;
            }
            return searchResult.get(position);
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case 0:
                    view = new QuickRepliesActivity.QuickReplyView(mContext, false, resourcesProvider);
                    break;
                case 1:
                    view = new View(mContext);
                    view.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, AndroidUtilities.dp(56)));
                    break;
                case 2:
                default:
                    view = new View(mContext);
                    break;
            }
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            if (holder.getItemViewType() == 0) {
                QuickRepliesActivity.QuickReplyView cell = (QuickRepliesActivity.QuickReplyView) holder.itemView;
                boolean divider = position != getItemCount() - 2;
                Object object = getItem(position);
                if (object instanceof QuickRepliesController.QuickReply) {
                    cell.set((QuickRepliesController.QuickReply) object, lastQuery, divider);
                    cell.setChecked(selectedReplies.contains(((QuickRepliesController.QuickReply) object).id), false);
                }
            }
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return holder.getItemViewType() == 0;
        }

        @Override
        public int getItemViewType(int position) {
            if (position == 0) {
                return 1;
            } else if (position == getItemCount() - 1) {
                return 2;
            }
            return 0;
        }

        @Override
        public void notifyDataSetChanged() {
            super.notifyDataSetChanged();
            updateEmptyView();
        }
    }

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {

        ThemeDescription.ThemeDescriptionDelegate cellDelegate = () -> {
            if (listView != null) {
                int count = listView.getChildCount();
                for (int a = 0; a < count; a++) {
                    View child = listView.getChildAt(a);
                    if (child instanceof UserCell) {
                        ((UserCell) child).update(0);
                    }
                }
            }
        };

        ArrayList<ThemeDescription> themeDescriptions = new ArrayList<>();

        themeDescriptions.add(new ThemeDescription(frameLayout, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_dialogBackground));

        themeDescriptions.add(new ThemeDescription(shadow, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_dialogShadowLine));

        themeDescriptions.add(new ThemeDescription(searchField.getSearchBackground(), ThemeDescription.FLAG_BACKGROUNDFILTER, null, null, null, null, Theme.key_dialogSearchBackground));
        themeDescriptions.add(new ThemeDescription(searchField, ThemeDescription.FLAG_IMAGECOLOR, new Class[]{SearchField.class}, new String[]{"searchIconImageView"}, null, null, null, Theme.key_dialogSearchIcon));
        themeDescriptions.add(new ThemeDescription(searchField, ThemeDescription.FLAG_IMAGECOLOR, new Class[]{SearchField.class}, new String[]{"clearSearchImageView"}, null, null, null, Theme.key_dialogSearchIcon));
        themeDescriptions.add(new ThemeDescription(searchField.getSearchEditText(), ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_dialogSearchText));
        themeDescriptions.add(new ThemeDescription(searchField.getSearchEditText(), ThemeDescription.FLAG_HINTTEXTCOLOR, null, null, null, null, Theme.key_dialogSearchHint));
        themeDescriptions.add(new ThemeDescription(searchField.getSearchEditText(), ThemeDescription.FLAG_CURSORCOLOR, null, null, null, null, Theme.key_featuredStickers_addedIcon));

        themeDescriptions.add(new ThemeDescription(emptyView, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_emptyListPlaceholder));
        themeDescriptions.add(new ThemeDescription(emptyView, ThemeDescription.FLAG_PROGRESSBAR, null, null, null, null, Theme.key_progressCircle));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_LISTGLOWCOLOR, null, null, null, null, Theme.key_dialogScrollGlow));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_SELECTOR, null, null, null, null, Theme.key_listSelector));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{View.class}, Theme.dividerPaint, null, null, Theme.key_divider));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{UserCell.class}, new String[]{"nameTextView"}, null, null, null, Theme.key_dialogTextGray2));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{UserCell.class}, new String[]{"statusTextView"}, null, null, cellDelegate, Theme.key_dialogTextGray2));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{UserCell.class}, null, Theme.avatarDrawables, null, Theme.key_avatar_text));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundRed));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundOrange));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundViolet));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundGreen));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundCyan));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundBlue));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundPink));

        return themeDescriptions;
    }
}
