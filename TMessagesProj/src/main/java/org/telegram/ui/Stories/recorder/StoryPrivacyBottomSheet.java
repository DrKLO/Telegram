package org.telegram.ui.Stories.recorder;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.dpf2;
import static org.telegram.messenger.AndroidUtilities.translitSafe;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.Editable;
import android.text.InputType;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.LinearInterpolator;
import android.view.inputmethod.EditorInfo;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.collection.LongSparseArray;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.LinearSmoothScrollerCustom;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AdjustPanLayoutHelper;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BackDrawable;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.GraySectionCell;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Components.AnimatedFloat;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.Bulletin;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.CheckBox2;
import org.telegram.ui.Components.ColoredImageSpan;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.GroupCreateSpan;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.ListView.AdapterWithDiffUtils;
import org.telegram.ui.Components.RadioButton;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.StickerEmptyView;
import org.telegram.ui.Components.TypefaceSpan;
import org.telegram.ui.Components.ViewPagerFixed;
import org.telegram.ui.Stories.StoriesController;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class StoryPrivacyBottomSheet extends BottomSheet implements NotificationCenter.NotificationCenterDelegate {

    private ViewPagerFixed viewPager;

    private static final int PAGE_TYPE_SHARE = 0;
    private static final int PAGE_TYPE_CLOSE_FRIENDS = 1;
    private static final int PAGE_TYPE_EXCLUDE_CONTACTS = 2;
    private static final int PAGE_TYPE_SELECT_CONTACTS = 3;
    private static final int PAGE_TYPE_EXCLUDE_EVERYONE = 4;
    private static final int PAGE_TYPE_SEND_AS_MESSAGE = 5;
    private static final int PAGE_TYPE_BLOCKLIST = 6;

    public static final int TYPE_CLOSE_FRIENDS = 1;
    public static final int TYPE_CONTACTS = 2;
    public static final int TYPE_SELECTED_CONTACTS = 3;
    public static final int TYPE_EVERYONE = 4;
    public static final int TYPE_AS_MESSAGE = 5;

    public TLRPC.InputPeer selectedPeer;

    private final ArrayList<Long> excludedEveryone = new ArrayList<>();
    private final HashMap<Long, ArrayList<Long>> excludedEveryoneByGroup = new HashMap<>();
    private int excludedEveryoneCount = 0;

    private final ArrayList<Long> excludedContacts = new ArrayList<>();
    private final ArrayList<Long> selectedContacts = new ArrayList<>();
    private final HashMap<Long, ArrayList<Long>> selectedContactsByGroup = new HashMap<>();
    private int selectedContactsCount = 0;

    private boolean allowScreenshots = true;
    private boolean keepOnMyPage = false;
    private boolean canChangePeer = true;

    private HashSet<Long> mergeUsers(ArrayList<Long> users, HashMap<Long, ArrayList<Long>> usersByGroup) {
        HashSet<Long> set = new HashSet<>();
        if (users != null) {
            set.addAll(users);
        }
        if (usersByGroup != null) {
            for (ArrayList<Long> userIds : usersByGroup.values()) {
                set.addAll(userIds);
            }
        }
        return set;
    }

    private final ArrayList<Long> messageUsers = new ArrayList<>();

    private int activePage = PAGE_TYPE_CLOSE_FRIENDS;
    private int selectedType = TYPE_EVERYONE;
    private boolean startedFromSendAsMessage;
    private boolean sendAsMessageEnabled = false;

    private HashMap<Long, Integer> smallChatsParticipantsCount = new HashMap<>();

    private class Page extends FrameLayout implements View.OnClickListener, NotificationCenter.NotificationCenterDelegate {
        public int pageType;

        private final LongSparseArray<Boolean> changelog = new LongSparseArray<>();
        private final ArrayList<Long> selectedUsers = new ArrayList<>();
        private final HashMap<Long, ArrayList<Long>> selectedUsersByGroup = new HashMap<>();

        private final FrameLayout contentView;
        private RecyclerListView listView;
        private LinearLayoutManager layoutManager;
        private Adapter adapter;

        private final ButtonContainer buttonContainer;
        private final View underKeyboardView;
        private final ButtonWithCounterView button;
        private final ButtonWithCounterView button2;

        private SearchUsersCell searchField;
        private GraySectionCell sectionCell;
        private HeaderCell headerView;

        private int searchPosition = -1;

        private boolean containsHeader;

        public Page(Context context, int pageType) {
            this(context);
            bind(pageType);
        }

        public Page(Context context) {
            super(context);

            sectionCell = new GraySectionCell(context, resourcesProvider);

            searchField = new SearchUsersCell(context, resourcesProvider, () -> {
                adapter.notifyItemChanged(2);
                this.listView.forceLayout();
                updateTops();
            }) {
                @Override
                public void setContainerHeight(float value) {
                    super.setContainerHeight(value);
                    sectionCell.setTranslationY(getY() - (contentView == null ? 0 : contentView.getPaddingTop()) + Math.min(dp(150), containerHeight) - 1);
                    if (contentView != null) {
                        contentView.invalidate();
                    }
                }

                @Override
                public void setTranslationY(float translationY) {
                    super.setTranslationY(translationY);
                    sectionCell.setTranslationY(getY() - (contentView == null ? 0 : contentView.getPaddingTop()) + Math.min(dp(150), containerHeight) - 1);
                    if (contentView != null) {
                        contentView.invalidate();
                    }
                }
            };
            searchField.setBackgroundColor(getThemedColor(Theme.key_dialogBackground));
            searchField.setOnSearchTextChange(this::onSearch);

            headerView = new HeaderCell(context, resourcesProvider);
            headerView.setOnCloseClickListener(() -> {
                if (pageType == PAGE_TYPE_SHARE) {
                    dismiss();
                } else {
                    onBackPressed();
                }
            });

            contentView = new FrameLayout(context);
            contentView.setPadding(0, AndroidUtilities.statusBarHeight + AndroidUtilities.dp(56), 0, 0);
            contentView.setClipToPadding(true);
            addView(contentView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));

            listView = new RecyclerListView(context, resourcesProvider);
            listView.setClipToPadding(false);
            listView.setTranslateSelector(true);
            listView.setAdapter(adapter = new Adapter(context, resourcesProvider, searchField, StoryPrivacyBottomSheet.this::onBackPressed));
            adapter.listView = listView;
            listView.setLayoutManager(layoutManager = new LinearLayoutManager(context));
            listView.setOnScrollListener(new RecyclerView.OnScrollListener() {
                private boolean canScrollDown;
                @Override
                public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                    boolean canScrollDown = listView.canScrollVertically(1);
                    if (canScrollDown != this.canScrollDown) {
                        buttonContainer.invalidate();
                        this.canScrollDown = canScrollDown;
                    }
                    contentView.invalidate();
                    containerView.invalidate();

                    if (pageType == PAGE_TYPE_BLOCKLIST && listView.getChildCount() > 0) {
                        int position = listView.getChildAdapterPosition(listView.getChildAt(0));
                        if (position >= MessagesController.getInstance(currentAccount).getStoriesController().blocklist.size()) {
                            MessagesController.getInstance(currentAccount).getStoriesController().loadBlocklist(false);
                        }
                    }
                }

                @Override
                public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                    if (newState == RecyclerView.SCROLL_STATE_DRAGGING && keyboardVisible && searchField != null) {
                        closeKeyboard();
                    }
                    scrolling = newState != RecyclerView.SCROLL_STATE_IDLE;
                }
            });
            listView.setOnItemClickListener((view, position, x, y) -> {
                if (position < 0 || position >= items.size()) {
                    return;
                }
                ItemInner item = items.get(position);
                if (item.viewType == VIEW_TYPE_USER) {
                    if (item.sendAs && canChangePeer) {
                        new ChoosePeerSheet(context, currentAccount, selectedPeer, peer -> {
                            selectedPeer = peer;
                            if (onSelectedPeer != null) {
                                onSelectedPeer.run(selectedPeer);
                            }
                            updateItems(true);
                        }, resourcesProvider).show();
                        return;
                    }
//                    boolean subtitle = LocaleController.isRTL ? x < view.getWidth() - AndroidUtilities.dp(100) : x > AndroidUtilities.dp(100);
                    if (item.type == TYPE_CLOSE_FRIENDS) {
                        if (selectedType == TYPE_CLOSE_FRIENDS || getCloseFriends().isEmpty()) {
                            activePage = PAGE_TYPE_CLOSE_FRIENDS;
                            viewPager.scrollToPosition(1);
                        }
                        selectedType = TYPE_CLOSE_FRIENDS;
                        updateCheckboxes(true);
                        return;
                    } else if (item.type == TYPE_SELECTED_CONTACTS) {
                        if (selectedType == TYPE_SELECTED_CONTACTS || selectedContacts.isEmpty() && selectedContactsByGroup.isEmpty()) {
                            activePage = PAGE_TYPE_SELECT_CONTACTS;
                            viewPager.scrollToPosition(1);
                        }
                        selectedType = TYPE_SELECTED_CONTACTS;
                        updateCheckboxes(true);
                        return;
                    } else if (item.type == TYPE_CONTACTS) {
                        if (selectedType == TYPE_CONTACTS) {
                            activePage = PAGE_TYPE_EXCLUDE_CONTACTS;
                            viewPager.scrollToPosition(1);
                        }
                        selectedType = TYPE_CONTACTS;
                        updateCheckboxes(true);
                        return;
                    } else if (item.type == TYPE_EVERYONE) {
                        if (selectedType == TYPE_EVERYONE) {
                            activePage = PAGE_TYPE_EXCLUDE_EVERYONE;
                            viewPager.scrollToPosition(1);
                        }
                        selectedType = TYPE_EVERYONE;
                        updateCheckboxes(true);
                        return;
                    }
                    if (item.type > 0) {
                        selectedUsers.clear();
                        selectedUsersByGroup.clear();
                        selectedType = item.type;
                        searchField.spansContainer.removeAllSpans(true);
                    } else if (item.chat != null) {
                        final long id = item.chat.id;
                        if (getParticipantsCount(item.chat) > 200) {
//                            AndroidUtilities.shakeViewSpring(view, shiftDp = -shiftDp);
//                            BotWebViewVibrationEffect.APP_ERROR.vibrate();
                            try {
                                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING);
                            } catch (Throwable ignore) {}
                            new AlertDialog.Builder(getContext(), resourcesProvider)
                                .setTitle(LocaleController.getString("GroupTooLarge", R.string.GroupTooLarge))
                                .setMessage(LocaleController.getString("GroupTooLargeMessage", R.string.GroupTooLargeMessage))
                                .setPositiveButton(LocaleController.getString("OK", R.string.OK), null)
                                .show();
                        } else if (selectedUsersByGroup.containsKey(id)) {
                            ArrayList<Long> userIds = selectedUsersByGroup.get(id);
                            if (userIds != null) {
                                for (long userId : userIds) {
                                    changelog.put(userId, false);
                                }
                            }
                            selectedUsersByGroup.remove(id);
                            updateSpans(true);
                        } else {
                            TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(id);
                            TLRPC.ChatFull chatFull = MessagesController.getInstance(currentAccount).getChatFull(id);
                            if (chatFull != null && chatFull.participants != null && chatFull.participants.participants != null && !chatFull.participants.participants.isEmpty() && chatFull.participants.participants.size() >= (chatFull.participants_count - 1)) {
                                selectChat(id, chatFull.participants);
                            } else {
                                if (progressDialog != null) {
                                    progressDialog.dismiss();
                                    progressDialog = null;
                                }
                                waitingForChatId = id;
                                progressDialog = new AlertDialog(getContext(), AlertDialog.ALERT_TYPE_SPINNER, resourcesProvider);
                                progressDialog.showDelayed(50);
                                MessagesStorage storage = MessagesStorage.getInstance(currentAccount);
                                storage.getStorageQueue().postRunnable(() -> {
                                    boolean isChannel = ChatObject.isChannel(chat);
                                    TLRPC.ChatFull info = storage.loadChatInfoInQueue(id, isChannel, true, true, 0);
                                    if (info == null || info.participants == null || info.participants.participants != null && info.participants.participants.size() < (info.participants_count - 1)) {
                                        AndroidUtilities.runOnUIThread(() -> {
                                            if (isChannel) {
                                                MessagesController.getInstance(currentAccount).loadChannelParticipants(id, participants -> {
                                                    if (progressDialog != null) {
                                                        progressDialog.dismissUnless(350);
                                                        progressDialog = null;
                                                    }
                                                    if (participants == null || participants.participants.isEmpty()) {
                                                        return;
                                                    }
                                                    TLRPC.ChatParticipants participantsObj = new TLRPC.TL_chatParticipants();
                                                    for (int i = 0; i < participants.participants.size(); ++i) {
                                                        TLRPC.ChannelParticipant participant = participants.participants.get(i);
                                                        TLRPC.TL_chatParticipant chatParticipant = new TLRPC.TL_chatParticipant();
                                                        long userId;
                                                        if (participant.peer != null) {
                                                            long did = DialogObject.getPeerDialogId(participant.peer);
                                                            if (did < 0) {
                                                                continue;
                                                            }
                                                            userId = did;
                                                        } else {
                                                            userId = participant.user_id;
                                                        }
                                                        chatParticipant.user_id = userId;
                                                        participantsObj.participants.add(chatParticipant);
                                                    }
                                                    selectChat(id, participantsObj);
                                                }, 200);
                                            } else {
                                                MessagesController.getInstance(currentAccount).loadFullChat(id, 0, true);
                                            }
                                        });
                                    } else {
                                        AndroidUtilities.runOnUIThread(() -> selectChat(id, info.participants));
                                    }
                                });
                            }
                            if (!TextUtils.isEmpty(query)) {
                                searchField.setText("");
                                query = null;
                                updateItems(false);
                            }
                        }
                    } else if (item.user != null) {
                        if (pageType == PAGE_TYPE_SHARE) {
                            selectedType = 0;
                        }
                        final long id = item.user.id;
                        HashSet<Long> userIds = new HashSet<>(selectedUsers);
                        if (selectedUsers.contains(id)) {
                            Iterator<Map.Entry<Long, ArrayList<Long>>> iterator = selectedUsersByGroup.entrySet().iterator();
                            while (iterator.hasNext()) {
                                Map.Entry<Long, ArrayList<Long>> entry = iterator.next();
                                if (entry.getValue().contains(id)) {
                                    iterator.remove();
                                    userIds.addAll(entry.getValue());
                                }
                            }
                            userIds.remove(id);
                            changelog.put(id, false);
                        } else {
                            Iterator<Map.Entry<Long, ArrayList<Long>>> iterator = selectedUsersByGroup.entrySet().iterator();
                            while (iterator.hasNext()) {
                                Map.Entry<Long, ArrayList<Long>> entry = iterator.next();
                                if (entry.getValue().contains(id)) {
                                    iterator.remove();
                                    userIds.addAll(entry.getValue());
                                }
                            }
                            userIds.add(id);
                            if (!TextUtils.isEmpty(query)) {
                                searchField.setText("");
                                query = null;
                                updateItems(false);
                            }
                            changelog.put(id, true);
                        }
                        selectedUsers.clear();
                        selectedUsers.addAll(userIds);
                        updateSpans(true);
                    }
                    updateCheckboxes(true);
                    updateButton(true);
                    searchField.scrollToBottom();
                } else if (item.viewType == VIEW_TYPE_CHECK) {
                    if (!(view instanceof TextCell)) {
                        return;
                    }
                    TextCell cell = (TextCell) view;
                    cell.setChecked(!cell.isChecked());
                    item.checked = cell.isChecked();
                    if (item.resId == 0) {
                        allowScreenshots = cell.isChecked();
                        boolean allowShare = selectedType == TYPE_EVERYONE;
                        if (allowScreenshots) {
                            BulletinFactory.of(container, resourcesProvider)
                                .createSimpleBulletin(R.raw.ic_save_to_gallery, LocaleController.getString(allowShare ? R.string.StoryEnabledScreenshotsShare : R.string.StoryEnabledScreenshots), 4)
                                    .setDuration(5000)
                                .show(true);
                        } else {
                            BulletinFactory.of(container, resourcesProvider)
                                .createSimpleBulletin(R.raw.passcode_lock_close, LocaleController.getString(allowShare ? R.string.StoryDisabledScreenshotsShare : R.string.StoryDisabledScreenshots), 4)
                                    .setDuration(5000)
                                .show(true);
                        }
                    } else {
                        keepOnMyPage = cell.isChecked();
                        final boolean isChannel = selectedPeer instanceof TLRPC.TL_inputPeerChannel;
                        if (keepOnMyPage) {
                            BulletinFactory.of(container, resourcesProvider)
                                    .createSimpleBulletin(R.raw.msg_story_keep, LocaleController.getString(isChannel ? R.string.StoryChannelEnableKeep : R.string.StoryEnableKeep), 4)
                                    .setDuration(5000)
                                    .show(true);
                        } else {
                            BulletinFactory.of(container, resourcesProvider)
                                    .createSimpleBulletin(R.raw.fire_on, LocaleController.getString(isChannel ? R.string.StoryChannelDisableKeep : R.string.StoryDisableKeep), 4)
                                    .setDuration(5000)
                                    .show(true);
                        }
                    }
                }
            });
            contentView.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

            DefaultItemAnimator itemAnimator = new DefaultItemAnimator() {
                @Override
                protected void onMoveAnimationUpdate(RecyclerView.ViewHolder holder) {
                    containerView.invalidate();
                    contentView.invalidate();
                    listView.invalidate();
                }

                @Override
                protected void onChangeAnimationUpdate(RecyclerView.ViewHolder holder) {
                    containerView.invalidate();
                    contentView.invalidate();
                }

                @Override
                protected void onAddAnimationUpdate(RecyclerView.ViewHolder holder) {
                    containerView.invalidate();
                    contentView.invalidate();
                }

                @Override
                protected void onRemoveAnimationUpdate(RecyclerView.ViewHolder holder) {
                    containerView.invalidate();
                    contentView.invalidate();
                }

                @Override
                public boolean canReuseUpdatedViewHolder(RecyclerView.ViewHolder viewHolder) {
                    return true;
                }
            };
            itemAnimator.setDurations(350);
            itemAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
            itemAnimator.setDelayAnimations(false);
            itemAnimator.setSupportsChangeAnimations(false);
            listView.setItemAnimator(itemAnimator);

            contentView.addView(searchField, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.FILL_HORIZONTAL));
            contentView.addView(sectionCell, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 32, Gravity.TOP | Gravity.FILL_HORIZONTAL));
            addView(headerView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.FILL_HORIZONTAL));

            buttonContainer = new ButtonContainer(context);
            buttonContainer.setClickable(true);
            buttonContainer.setOrientation(LinearLayout.VERTICAL);
            buttonContainer.setPadding(dp(10) + backgroundPaddingLeft, dp(10), dp(10) + backgroundPaddingLeft, dp(10));
            buttonContainer.setBackgroundColor(Theme.getColor(Theme.key_dialogBackground, resourcesProvider));

            button = new ButtonWithCounterView(context, resourcesProvider);
            button.setOnClickListener(this::onButton1Click);
            buttonContainer.addView(button, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, Gravity.BOTTOM | Gravity.FILL_HORIZONTAL));

            button2 = new ButtonWithCounterView(context, false, resourcesProvider);
            button2.setOnClickListener(this::onButton2Click);
            buttonContainer.addView(button2, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, Gravity.BOTTOM | Gravity.FILL_HORIZONTAL, 0, 8, 0, 0));

            underKeyboardView = new View(context);
            underKeyboardView.setBackgroundColor(Theme.getColor(Theme.key_dialogBackground, resourcesProvider));
            addView(underKeyboardView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 500, Gravity.BOTTOM | Gravity.FILL_HORIZONTAL, 0, 0, 0, -500));

            addView(buttonContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM | Gravity.FILL_HORIZONTAL));
        }

        private class ButtonContainer extends LinearLayout {
            public ButtonContainer(Context context) {
                super(context);
            }

            private float translationY, translationY2;
            private ValueAnimator hideAnimator;
            public void hide(boolean hide, boolean animated) {
                if (hideAnimator != null) {
                    hideAnimator.cancel();
                }
                if (animated) {
                    setVisibility(View.VISIBLE);
                    hideAnimator = ValueAnimator.ofFloat(translationY2, hide ? getMeasuredHeight() : 0);
                    hideAnimator.addUpdateListener(anm -> {
                        translationY2 = (float) anm.getAnimatedValue();
                        super.setTranslationY(translationY2 + translationY);
                    });
                    hideAnimator.addListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            if (hide) {
                                setVisibility(View.GONE);
                            }
                            hideAnimator = null;
                        }
                    });
                    hideAnimator.setDuration(320);
                    hideAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
                    hideAnimator.start();
                } else {
                    setVisibility(hide ? View.GONE : View.VISIBLE);
                    translationY2 = hide ? getMeasuredHeight() : 0;
                    super.setTranslationY(translationY2 + translationY);
                }
            }

            private ValueAnimator animator;
            public void translateY(float from, float to) {
                if (animator != null) {
                    animator.cancel();
                    animator = null;
                }
                animator = ValueAnimator.ofFloat(from, to);
                animator.addUpdateListener(anm -> {
                    setTranslationY((float) anm.getAnimatedValue());
                });
                animator.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        setTranslationY(to);
                        animator = null;
                    }
                });
                animator.setDuration(AdjustPanLayoutHelper.keyboardDuration);
                animator.setInterpolator(AdjustPanLayoutHelper.keyboardInterpolator);
                animator.start();
            }

            @Override
            public void setTranslationY(float translationY) {
                super.setTranslationY(translationY2 + (this.translationY = translationY));
            }

//            @Override
//            public float getTranslationY() {
//                return translationY;
//            }

            final Paint dividerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            final AnimatedFloat alpha = new AnimatedFloat(this);
            @Override
            protected void dispatchDraw(Canvas canvas) {
                super.dispatchDraw(canvas);
                dividerPaint.setColor(Theme.getColor(Theme.key_windowBackgroundGray, resourcesProvider));
                dividerPaint.setAlpha((int) (0xFF * alpha.set(listView.canScrollVertically(1) ? 1 : 0)));
                canvas.drawRect(0, 0, getWidth(), 1, dividerPaint);
            }
        }

        private AlertDialog progressDialog;
        private long waitingForChatId;

        @Override
        public void didReceivedNotification(int id, int account, Object... args) {
            if (id == NotificationCenter.chatInfoDidLoad) {
                TLRPC.ChatFull chatFull = (TLRPC.ChatFull) args[0];
                if (chatFull == null || progressDialog == null || waitingForChatId != chatFull.id) {
                    return;
                }
                progressDialog.dismissUnless(350);
                progressDialog = null;
                waitingForChatId = -1;
                selectChat(chatFull.id, chatFull.participants);
            }
        }

        private void selectChat(long id, TLRPC.ChatParticipants participants) {
            ArrayList<Long> groupUsers = new ArrayList<>();
            ArrayList<Long> nonContactsUsers = new ArrayList<>();
            final boolean mustBeContacts = pageType == PAGE_TYPE_CLOSE_FRIENDS || pageType == PAGE_TYPE_EXCLUDE_CONTACTS;
            if (participants != null && participants.participants != null) {
                for (int i = 0; i < participants.participants.size(); ++i) {
                    long userId = participants.participants.get(i).user_id;
                    TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(userId);
                    if (user != null && !UserObject.isUserSelf(user) && !user.bot && user.id != 777000 && userId != 0) {
                        if (mustBeContacts && !user.contact) {
                            nonContactsUsers.add(userId);
                        } else {
                            groupUsers.add(userId);
                        }
                        selectedUsers.remove(userId);
                    }
                }
            }
            if (!nonContactsUsers.isEmpty()) {
                if (groupUsers.isEmpty()) {
                    new AlertDialog.Builder(getContext(), resourcesProvider)
                        .setMessage("All group members are not in your contact list.")
                        .setNegativeButton("Cancel", null)
                        .show();
                } else {
                    new AlertDialog.Builder(getContext(), resourcesProvider)
                        .setMessage(nonContactsUsers.size() + " members are not in your contact list")
                        .setPositiveButton("Add " + groupUsers.size() + " contacts", (di, a) -> {
                            selectedUsersByGroup.put(id, groupUsers);
                            for (long userId : groupUsers) {
                                changelog.put(userId, true);
                            }
                            updateSpans(true);
                            updateButton(true);
                            updateCheckboxes(true);
                            di.dismiss();
                            searchField.scrollToBottom();
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
                }
            } else {
                selectedUsersByGroup.put(id, groupUsers);
                for (long userId : groupUsers) {
                    changelog.put(userId, true);
                }
                updateSpans(true);
                updateButton(true);
                updateCheckboxes(true);
                searchField.scrollToBottom();
            }
        }

        private void updateSpans(boolean animated) {
            HashSet<Long> userIds = mergeUsers(selectedUsers, selectedUsersByGroup);
            if (pageType == PAGE_TYPE_SELECT_CONTACTS) {
                selectedContactsCount = userIds.size();
            } else if (pageType == PAGE_TYPE_EXCLUDE_EVERYONE) {
                excludedEveryoneCount = userIds.size();
            }

            final MessagesController messagesController = MessagesController.getInstance(currentAccount);
            final ArrayList<GroupCreateSpan> toDelete = new ArrayList<>();
            final ArrayList<GroupCreateSpan> toAdd = new ArrayList<>();

            // remove spans
            for (int i = 0; i < searchField.allSpans.size(); ++i) {
                GroupCreateSpan span = searchField.allSpans.get(i);
                if (!userIds.contains(span.getUid())) {
                    toDelete.add(span);
                }
            }

            for (long id : userIds) {
                boolean found = false;
                for (int j = 0; j < searchField.allSpans.size(); ++j) {
                    GroupCreateSpan span = searchField.allSpans.get(j);
                    if (span.getUid() == id) {
                        found = true;
                        break;
                    }
                }

                if (!found) {
                    TLObject obj = null;
                    if (id >= 0) {
                        obj = messagesController.getUser(id);
                    } else {
                        obj = messagesController.getChat(id);
                    }
                    if (obj == null) {
                        continue;
                    }
                    GroupCreateSpan span = new GroupCreateSpan(getContext(), obj, null, true, resourcesProvider);
                    span.setOnClickListener(this);
                    toAdd.add(span);
                }
            }

            if (!toDelete.isEmpty() || !toAdd.isEmpty()) {
                searchField.spansContainer.updateSpans(toDelete, toAdd, animated);
            }
        }

        private void onButton1Click(View v) {
            if (button.isLoading()) {
                return;
            }
            final MessagesController messagesController = MessagesController.getInstance(currentAccount);
            if (pageType == PAGE_TYPE_SEND_AS_MESSAGE) {
                if (onDone2 != null) {
                    onDone2.run(selectedUsers);
                }
                dismiss();
            } else if (pageType == PAGE_TYPE_CLOSE_FRIENDS) {
                TLRPC.TL_editCloseFriends req = new TLRPC.TL_editCloseFriends();
                req.id.addAll(selectedUsers);
                button.setLoading(true);
                ConnectionsManager.getInstance(currentAccount).sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
                    button.setLoading(false);
                    if (res != null) {
                        ArrayList<TLObject> users = getContacts();
                        for (int i = 0; i < users.size(); ++i) {
                            TLRPC.User u = (TLRPC.User) users.get(i);
                            if (u == null) {
                                continue;
                            }
                            boolean shouldBeCloseFriend = selectedUsers.contains(u.id);
                            if (shouldBeCloseFriend != u.close_friend) {
                                u.flags2 = (u.close_friend = shouldBeCloseFriend) ? (u.flags2 | 4) : (u.flags2 &~ 4);
                                messagesController.putUser(u, false);
                            }
                        }
                    }
                    closeKeyboard();
                    if (isEdit) {
                        done(new StoryPrivacy(TYPE_CLOSE_FRIENDS, currentAccount, null), StoryPrivacyBottomSheet.this::dismiss);
                    } else {
                        closeKeyboard();
                        viewPager.scrollToPosition(0);
                    }
                }));
            } else if (pageType == PAGE_TYPE_SHARE) {
                if (!applyWhenDismiss) {
                    StoryPrivacy privacy;
                    if (selectedType == TYPE_SELECTED_CONTACTS) {
                        HashSet<Long> users = mergeUsers(selectedContacts, selectedContactsByGroup);
                        privacy = new StoryPrivacy(selectedType, currentAccount, new ArrayList<>(users));
                        privacy.selectedUserIds.clear();
                        privacy.selectedUserIds.addAll(selectedContacts);
                        privacy.selectedUserIdsByGroup.clear();
                        privacy.selectedUserIdsByGroup.putAll(selectedContactsByGroup);
                    } else if (selectedType == TYPE_CONTACTS) {
                        privacy = new StoryPrivacy(selectedType, currentAccount, excludedContacts);
                    } else if (selectedType == TYPE_EVERYONE) {
                        HashSet<Long> users = mergeUsers(excludedEveryone, excludedEveryoneByGroup);
                        privacy = new StoryPrivacy(selectedType, currentAccount, new ArrayList<>(users));
                        privacy.selectedUserIds.clear();
                        privacy.selectedUserIds.addAll(excludedEveryone);
                        privacy.selectedUserIdsByGroup.clear();
                        privacy.selectedUserIdsByGroup.putAll(excludedEveryoneByGroup);
                    } else {
                        privacy = new StoryPrivacy(selectedType, currentAccount, null);
                    }
                    done(privacy, StoryPrivacyBottomSheet.this::dismiss);
                } else {
                    dismiss();
                }
            } else if (pageType == PAGE_TYPE_EXCLUDE_CONTACTS) {
                if (isEdit) {
                    closeKeyboard();
                    done(new StoryPrivacy(TYPE_CONTACTS, currentAccount, selectedUsers), StoryPrivacyBottomSheet.this::dismiss);
                } else {
                    closeKeyboard();
                    viewPager.scrollToPosition(0);
                }
            } else if (pageType == PAGE_TYPE_SELECT_CONTACTS) {
                if (isEdit) {
                    HashSet<Long> users = mergeUsers(selectedUsers, selectedUsersByGroup);
                    if (users.isEmpty()) {
                        return;
                    }
                    closeKeyboard();
                    StoryPrivacy privacy = new StoryPrivacy(TYPE_SELECTED_CONTACTS, currentAccount, new ArrayList<>(users));
                    privacy.selectedUserIds.clear();
                    privacy.selectedUserIds.addAll(selectedUsers);
                    privacy.selectedUserIdsByGroup.clear();
                    privacy.selectedUserIdsByGroup.putAll(selectedUsersByGroup);
                    done(privacy, () -> {
                        Bulletin.removeDelegate(container);
                        StoryPrivacyBottomSheet.super.dismiss();
                    });
                } else {
                    HashSet<Long> users = mergeUsers(selectedUsers, selectedUsersByGroup);
                    if (users.isEmpty()) {
                        return;
                    }
                    selectedType = PAGE_TYPE_SELECT_CONTACTS;
                    closeKeyboard();
                    viewPager.scrollToPosition(0);
                }
            } else if (pageType == PAGE_TYPE_BLOCKLIST) {
                HashSet<Long> users = mergeUsers(selectedUsers, selectedUsersByGroup);
                button.setLoading(true);
                MessagesController.getInstance(currentAccount).getStoriesController().updateBlockedUsers(users, () -> {
                    button.setLoading(false);
                    closeKeyboard();
                    viewPager.scrollToPosition(0);
                });
            } else {
                selectedType = pageType;
                closeKeyboard();
                viewPager.scrollToPosition(0);
            }
        }

        private void onButton2Click(View v) {
            if (startedFromSendAsMessage) {
                activePage = PAGE_TYPE_SEND_AS_MESSAGE;
                viewPager.scrollToPosition(1);
            } else {
                StoryPrivacyBottomSheet sheet = new StoryPrivacyBottomSheet(PAGE_TYPE_SEND_AS_MESSAGE, getContext(), resourcesProvider)
                    .whenSelectedShare(users -> {
                        done(new StoryPrivacy(TYPE_AS_MESSAGE, currentAccount, users), StoryPrivacyBottomSheet.this::dismiss);
                    });
                sheet.storyPeriod = storyPeriod;
                sheet.show();
            }
        }

        public float top() {
            float top = layoutManager.getReverseLayout() ? AndroidUtilities.displaySize.y : 0;
            for (int i = 0; i < listView.getChildCount(); ++i) {
                View child = listView.getChildAt(i);
                if (layoutManager.getReverseLayout()) {
                    final float childTop = contentView.getPaddingTop() + child.getY();
                    final float a = child.getAlpha();
                    if (childTop < top) {
                        top = AndroidUtilities.lerp(top, childTop, a);
                    }
                } else if (child.getTag() instanceof Integer && (int) child.getTag() == 33) {
                    return contentView.getPaddingTop() + child.getBottom() + child.getTranslationY();
                } else if (child.getTag() instanceof Integer && (int) child.getTag() == 35) {
                    return contentView.getPaddingTop() + child.getY();
                }
            }
            return top;
        }

        public void bind(int pageType) {
            this.pageType = pageType;
            changelog.clear();
            selectedUsers.clear();
            selectedUsersByGroup.clear();
            if (pageType == PAGE_TYPE_EXCLUDE_EVERYONE) {
                selectedUsers.addAll(excludedEveryone);
                selectedUsersByGroup.putAll(excludedEveryoneByGroup);
            } else if (pageType == PAGE_TYPE_SEND_AS_MESSAGE) {
                selectedUsers.addAll(messageUsers);
            } else if (pageType == PAGE_TYPE_CLOSE_FRIENDS) {
                ArrayList<TLObject> closeFriends = getCloseFriends();
                for (int i = 0; i < closeFriends.size(); ++i) {
                    selectedUsers.add(((TLRPC.User) closeFriends.get(i)).id);
                }
            } else if (pageType == PAGE_TYPE_EXCLUDE_CONTACTS) {
                selectedUsers.addAll(excludedContacts);
            } else if (pageType == PAGE_TYPE_SELECT_CONTACTS) {
                selectedUsers.addAll(selectedContacts);
                selectedUsersByGroup.putAll(selectedContactsByGroup);
            } else if (pageType == PAGE_TYPE_BLOCKLIST) {
                applyBlocklist(false);
            }
            layoutManager.setReverseLayout(adapter.reversedLayout = pageType == PAGE_TYPE_SHARE);
            updateSpans(false);
            searchField.setText("");
            searchField.setVisibility(pageType == PAGE_TYPE_SHARE ? View.GONE : View.VISIBLE);
            searchField.scrollToBottom();
            query = null;
            updateItems(false);
            updateButton(false);
            updateCheckboxes(false);
            scrollToTop();
            listView.requestLayout();
            lastSelectedType = -1;
        }

        public void applyBlocklist(boolean notify) {
            if (pageType != PAGE_TYPE_BLOCKLIST) {
                return;
            }

            selectedUsers.clear();
            HashSet<Long> blocklist = MessagesController.getInstance(currentAccount).getStoriesController().blocklist;
            selectedUsers.addAll(blocklist);
            for (int i = 0; i < changelog.size(); ++i) {
                long id = changelog.keyAt(i);
                boolean blocked = changelog.valueAt(i);
                if (blocked) {
                    if (!selectedUsers.contains(id)) {
                        selectedUsers.add(id);
                    }
                } else {
                    selectedUsers.remove(id);
                }
            }

            if (notify) {
                updateItems(true);
                updateButton(true);
                updateCheckboxes(true);
            }
        }

        private String query;

        private final ArrayList<TLObject> atTop = new ArrayList<>();
        private final ArrayList<ItemInner> oldItems = new ArrayList<>();
        private final ArrayList<ItemInner> items = new ArrayList<>();

        public void updateItems(boolean animated) {
            updateItems(animated, true);
        }
        public void updateItems(boolean animated, boolean notify) {
            oldItems.clear();
            oldItems.addAll(items);

            items.clear();

            float h = 0;
            if (pageType == PAGE_TYPE_SHARE) {
                ItemInner item;
                containsHeader = false;
                sectionCell.setVisibility(View.GONE);
//                items.add(ItemInner.asPad(dp(84) + 4 * dp(56) + (sendAsMessageEnabled ? dp(120) : dp(64))));
                List<TLRPC.InputPeer> sendAs = MessagesController.getInstance(currentAccount).getStoriesController().sendAs;
                boolean containsPrivacy = true;
                boolean isChannel = false;
                if (canChangePeer && (isEdit || sendAs == null || sendAs.size() <= 1)) {
                    items.add(ItemInner.asHeader2(
                        isEdit ?
                                LocaleController.getString("StoryPrivacyAlertEditTitle", R.string.StoryPrivacyAlertEditTitle) :
                                LocaleController.getString("StoryPrivacyAlertTitle", R.string.StoryPrivacyAlertTitle),
                        storyPeriod != Integer.MAX_VALUE ?
                                LocaleController.formatPluralString("StoryPrivacyAlertSubtitle", storyPeriod / 3600) :
                                LocaleController.getString("StoryPrivacyAlertSubtitleProfile", R.string.StoryPrivacyAlertSubtitleProfile)
                    ));
                } else {
                    items.add(ItemInner.asHeaderCell(LocaleController.getString(R.string.StoryPrivacyPublishAs)));
                    if (selectedPeer == null || selectedPeer instanceof TLRPC.TL_inputPeerSelf) {
                        TLRPC.User me = UserConfig.getInstance(currentAccount).getCurrentUser();
                        items.add(ItemInner.asUser(me, false, false).asSendAs());
                    } else if (selectedPeer instanceof TLRPC.TL_inputPeerUser) {
                        TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(selectedPeer.user_id);
                        items.add(ItemInner.asUser(user, false, false).asSendAs());
                    } else if (selectedPeer instanceof TLRPC.TL_inputPeerChannel) {
                        TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(selectedPeer.channel_id);
                        items.add(ItemInner.asChat(chat, false).asSendAs());
                        containsPrivacy = false;
                        isChannel = ChatObject.isChannelAndNotMegaGroup(chat);
                    } else if (selectedPeer instanceof TLRPC.TL_inputPeerChat) {
                        TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(selectedPeer.chat_id);
                        items.add(ItemInner.asChat(chat, false).asSendAs());
                        containsPrivacy = false;
                    }
                    ItemInner section = ItemInner.asShadow(null);
                    section.resId = containsPrivacy ? 1 : 2;
                    items.add(section);
                    if (containsPrivacy) {
                        items.add(ItemInner.asHeaderCell(LocaleController.getString(R.string.StoryPrivacyWhoCanView)));
                    }
                }
                if (containsPrivacy) {
                    items.add(item = ItemInner.asType(TYPE_EVERYONE, selectedType == TYPE_EVERYONE, excludedEveryoneCount));
                    if (excludedEveryoneCount == 1) {
                        if (excludedEveryone.size() == 1) {
                            item.user = MessagesController.getInstance(currentAccount).getUser(excludedEveryone.get(0));
                        } else {
                            for (ArrayList<Long> userIds : excludedEveryoneByGroup.values()) {
                                if (userIds.size() >= 1) {
                                    item.user = MessagesController.getInstance(currentAccount).getUser(userIds.get(0));
                                    break;
                                }
                            }
                        }
                    }
                    items.add(item = ItemInner.asType(TYPE_CONTACTS, selectedType == TYPE_CONTACTS, excludedContacts.size()));
                    if (excludedContacts.size() == 1) {
                        item.user = MessagesController.getInstance(currentAccount).getUser(excludedContacts.get(0));
                    }
                    ArrayList<TLObject> closeFriends = getCloseFriends();
                    items.add(item = ItemInner.asType(TYPE_CLOSE_FRIENDS, selectedType == TYPE_CLOSE_FRIENDS, closeFriends.size()));
                    if (closeFriends.size() == 1 && closeFriends.get(0) instanceof TLRPC.User) {
                        item.user = (TLRPC.User) closeFriends.get(0);
                    }
                    items.add(item = ItemInner.asType(TYPE_SELECTED_CONTACTS, selectedType == TYPE_SELECTED_CONTACTS, selectedContactsCount));
                    if (selectedContactsCount == 1) {
                        if (selectedContacts.size() == 1) {
                            item.user = MessagesController.getInstance(currentAccount).getUser(selectedContacts.get(0));
                        } else {
                            for (ArrayList<Long> userIds : selectedContactsByGroup.values()) {
                                if (userIds.size() >= 1) {
                                    item.user = MessagesController.getInstance(currentAccount).getUser(userIds.get(0));
                                    break;
                                }
                            }
                        }
                    }
                    int blocklistCount = MessagesController.getInstance(currentAccount).getStoriesController().getBlocklistCount();
                    items.add(ItemInner.asShadow(AndroidUtilities.replaceSingleTag(
                        blocklistCount <= 0 ?
                            LocaleController.getString("StoryBlockListEmpty") :
                            LocaleController.formatPluralString("StoryBlockList", blocklistCount),
                        Theme.key_chat_messageLinkIn, 0,
                        () -> {
                            activePage = PAGE_TYPE_BLOCKLIST;
                            viewPager.scrollToPosition(1);
                        },
                        resourcesProvider
                    )));
                }
                if (!isEdit) {
                    items.add(ItemInner.asCheck(LocaleController.getString(R.string.StoryAllowScreenshots), 0, allowScreenshots));
                    items.add(ItemInner.asCheck(LocaleController.getString(containsPrivacy ? R.string.StoryKeep : (isChannel ? R.string.StoryKeepChannel : R.string.StoryKeepGroup)), 1, keepOnMyPage));
                    items.add(ItemInner.asShadow(LocaleController.formatPluralString(containsPrivacy ? "StoryKeepInfo" : (isChannel ? "StoryKeepChannelInfo" : "StoryKeepGroupInfo"), (storyPeriod == Integer.MAX_VALUE ? 86400 : storyPeriod) / 3600)));
                }
            } else if (pageType == PAGE_TYPE_CLOSE_FRIENDS) {
                headerView.setText(LocaleController.getString("StoryPrivacyAlertCloseFriendsTitle", R.string.StoryPrivacyAlertCloseFriendsTitle));
                headerView.setCloseImageVisible(true);
                headerView.backDrawable.setRotation(0f, false);
                items.add(ItemInner.asPad());
                items.add(ItemInner.asHeader());
                h += dp(56);
                searchPosition = items.size();
                items.add(ItemInner.asSearchField());
                h += dp(150);
                items.add(ItemInner.asSection());
                h += dp(32);
                sectionCell.setText(LocaleController.getString("StoryPrivacyAlertCloseFriendsSubtitle", R.string.StoryPrivacyAlertCloseFriendsSubtitle));
                updateSectionCell(animated);
                containsHeader = true;
            } else if (pageType == PAGE_TYPE_EXCLUDE_CONTACTS) {
                headerView.setText(LocaleController.getString("StoryPrivacyAlertExcludedContactsTitle", R.string.StoryPrivacyAlertExcludedContactsTitle));
                headerView.setCloseImageVisible(true);
                headerView.backDrawable.setRotation(0f, false);
                items.add(ItemInner.asPad());
                items.add(ItemInner.asHeader());
                h += dp(56);
                searchPosition = items.size();
                items.add(ItemInner.asSearchField());
                h += dp(150);
                items.add(ItemInner.asSection());
                h += dp(32);
                sectionCell.setText(LocaleController.getString("StoryPrivacyAlertExcludedContactsSubtitle", R.string.StoryPrivacyAlertExcludedContactsSubtitle));
                updateSectionCell(animated);
                containsHeader = true;
            } else if (pageType == PAGE_TYPE_SELECT_CONTACTS) {
                headerView.setText(LocaleController.getString("StoryPrivacyAlertSelectContactsTitle", R.string.StoryPrivacyAlertSelectContactsTitle));
                headerView.setCloseImageVisible(true);
                headerView.backDrawable.setRotation(0f, false);
                items.add(ItemInner.asPad());
                items.add(ItemInner.asHeader());
                h += dp(56);
                searchPosition = items.size();
                items.add(ItemInner.asSearchField());
                h += dp(150);
                items.add(ItemInner.asSection());
                h += dp(32);
                sectionCell.setText(LocaleController.getString("StoryPrivacyAlertSelectContactsSubtitle", R.string.StoryPrivacyAlertSelectContactsSubtitle));
                updateSectionCell(animated);
                containsHeader = true;
            } else if (pageType == PAGE_TYPE_SEND_AS_MESSAGE) {
                headerView.setText(LocaleController.getString("StoryPrivacyAlertAsMessageTitle", R.string.StoryPrivacyAlertAsMessageTitle));
                headerView.setCloseImageVisible(startedFromSendAsMessage);
                headerView.backDrawable.setRotation(0f, false);
                items.add(ItemInner.asPad());
                items.add(ItemInner.asHeader());
                h += dp(56);
                searchPosition = items.size();
                items.add(ItemInner.asSearchField());
                h += dp(150);
                items.add(ItemInner.asSection());
                h += dp(32);
                sectionCell.setText(LocaleController.getString("StoryPrivacyAlertAsMessageSubtitle", R.string.StoryPrivacyAlertAsMessageSubtitle));
                updateSectionCell(animated);
                containsHeader = true;
            } else if (pageType == PAGE_TYPE_BLOCKLIST) {
                headerView.setText(LocaleController.getString("StoryPrivacyAlertBlocklistTitle", R.string.StoryPrivacyAlertBlocklistTitle));
                headerView.setCloseImageVisible(true);
                headerView.backDrawable.setRotation(0f, false);
                items.add(ItemInner.asPad());
                items.add(ItemInner.asHeader());
                h += dp(56);
                searchPosition = items.size();
                items.add(ItemInner.asSearchField());
                h += dp(150);
                items.add(ItemInner.asSection());
                h += dp(32);
                sectionCell.setText(LocaleController.getString("StoryPrivacyAlertBlocklistSubtitle", R.string.StoryPrivacyAlertBlocklistSubtitle));
                updateSectionCell(animated);
                containsHeader = true;
            } else if (pageType == PAGE_TYPE_EXCLUDE_EVERYONE) {
                headerView.setText(LocaleController.getString("StoryPrivacyAlertExcludeFromEveryoneTitle", R.string.StoryPrivacyAlertExcludeFromEveryoneTitle));
                headerView.setCloseImageVisible(true);
                headerView.backDrawable.setRotation(0f, false);
                items.add(ItemInner.asPad());
                items.add(ItemInner.asHeader());
                h += dp(56);
                searchPosition = items.size();
                items.add(ItemInner.asSearchField());
                h += dp(150);
                items.add(ItemInner.asSection());
                h += dp(32);
                sectionCell.setText(LocaleController.getString("StoryPrivacyAlertExcludeFromEveryoneSubtitle", R.string.StoryPrivacyAlertExcludeFromEveryoneSubtitle));
                updateSectionCell(animated);
                containsHeader = true;
            }

            boolean searching = !TextUtils.isEmpty(query);
            if (pageType != PAGE_TYPE_SHARE) {
                int count = 0;
                String q = translitSafe(query).toLowerCase();
                ArrayList<TLObject> allOtherUsers;
                if (pageType == PAGE_TYPE_SEND_AS_MESSAGE) {
                    allOtherUsers = getChats();
                } else {
                    allOtherUsers = getUsers(pageType == PAGE_TYPE_CLOSE_FRIENDS || pageType == PAGE_TYPE_EXCLUDE_CONTACTS, allowSmallChats && (pageType == PAGE_TYPE_SELECT_CONTACTS || pageType == PAGE_TYPE_BLOCKLIST));
                }
                HashSet<Long> allSelectedUsers = mergeUsers(selectedUsers, selectedUsersByGroup);
                if (!searching) {
                    if (!animated) {
                        atTop.clear();
                        for (int i = 0; i < allOtherUsers.size(); ++i) {
                            TLObject object = allOtherUsers.get(i);
                            boolean selected = false;
                            if (object instanceof TLRPC.User) {
                                TLRPC.User user = (TLRPC.User) object;
                                selected = selectedUsers.contains(user.id);
                            } else if (object instanceof TLRPC.Chat) {
                                TLRPC.Chat chat = (TLRPC.Chat) object;
                                selected = selectedUsersByGroup.containsKey(chat.id);
                            }
                            if (selected) {
                                atTop.add(object);
                            }
                        }
                    }
                    for (int i = 0; i < atTop.size(); ++i) {
                        TLObject object = atTop.get(i);
                        if (object instanceof TLRPC.User) {
                            TLRPC.User user = (TLRPC.User) object;
                            final boolean checked = selectedUsers.contains(user.id);
                            final boolean halfChecked = !checked && allSelectedUsers.contains(user.id);
                            items.add(ItemInner.asUser(user, checked, halfChecked).red(pageType == PAGE_TYPE_EXCLUDE_CONTACTS || pageType == PAGE_TYPE_EXCLUDE_EVERYONE));
                            h += dp(56);
                            count++;
                        } else if (object instanceof TLRPC.Chat) {
                            TLRPC.Chat chat = (TLRPC.Chat) object;
                            items.add(ItemInner.asChat(chat, selectedUsersByGroup.containsKey(chat.id)).red(pageType == PAGE_TYPE_EXCLUDE_CONTACTS || pageType == PAGE_TYPE_EXCLUDE_EVERYONE));
                            h += dp(56);
                            count++;
                        }
                    }
                }
                for (int i = 0; i < allOtherUsers.size(); ++i) {
                    TLObject object = allOtherUsers.get(i);
                    if (!searching && atTop.contains(object) || !match(object, q)) {
                        continue;
                    }
                    if (object instanceof TLRPC.User) {
                        TLRPC.User user = (TLRPC.User) object;
                        final boolean checked = selectedUsers.contains(user.id);
                        final boolean halfChecked = !checked && allSelectedUsers.contains(user.id);
                        items.add(ItemInner.asUser(user, checked, halfChecked).red(pageType == PAGE_TYPE_EXCLUDE_CONTACTS || pageType == PAGE_TYPE_EXCLUDE_EVERYONE));
                        h += dp(56);
                        count++;
                    } else if (object instanceof TLRPC.Chat) {
                        TLRPC.Chat chat = (TLRPC.Chat) object;
                        items.add(ItemInner.asChat(chat, selectedUsersByGroup.containsKey(chat.id)).red(pageType == PAGE_TYPE_EXCLUDE_CONTACTS || pageType == PAGE_TYPE_EXCLUDE_EVERYONE));
                        h += dp(56);
                        count++;
                    }
                }
                if (searching) {
                    if (count == 0) {
                        items.add(ItemInner.asNoUsers());
                        h += dp(150);
                    }
                    float lh;
                    if (listView != null) {
                        lh = listView.getMeasuredHeight() - listView.getPaddingTop() - listView.getPaddingBottom() + (keyboardVisible ? keyboardHeight : 0);
                    } else {
                        lh = AndroidUtilities.displaySize.y - dp(56) - AndroidUtilities.navigationBarHeight - dp(42);
                    }
                    if (lh - h > 0) {
                        items.add(ItemInner.asPadding((int) (lh - h)));
                    }
                }
            }

            if (layoutManager.getReverseLayout()) {
                Collections.reverse(items);
            }

            if (notify && adapter != null) {
                if (animated && selectedType != PAGE_TYPE_SHARE) {
                    adapter.setItems(oldItems, items);
                } else {
                    adapter.notifyDataSetChanged();
                }
            }

            this.contentView.invalidate();
        }

        private boolean match(TLObject obj, String q) {
            if (TextUtils.isEmpty(q)) {
                return true;
            }
            if (obj instanceof TLRPC.User) {
                TLRPC.User user = (TLRPC.User) obj;
                String name = translitSafe(UserObject.getUserName(user)).toLowerCase();
                if (name.startsWith(q) || name.contains(" " + q)) {
                    return true;
                }
                String username = translitSafe(UserObject.getPublicUsername(user)).toLowerCase();
                if (username.startsWith(q) || username.contains(" " + q)) {
                    return true;
                }
                if (user.usernames != null) {
                    ArrayList<TLRPC.TL_username> usernames = user.usernames;
                    for (int j = 0; j < usernames.size(); ++j) {
                        TLRPC.TL_username username2 = usernames.get(j);
                        if (!username2.active) {
                            continue;
                        }
                        String u = translitSafe(username2.username).toLowerCase();
                        if (u.startsWith(q)) {
                            return true;
                        }
                    }
                }
            } else if (obj instanceof TLRPC.Chat) {
                TLRPC.Chat chat = (TLRPC.Chat) obj;
                String name = translitSafe(chat.title).toLowerCase();
                if (name.startsWith(q) || name.contains(" " + q)) {
                    return true;
                }
                String username = translitSafe(ChatObject.getPublicUsername(chat)).toLowerCase();
                if (username.startsWith(q) || username.contains(" " + q)) {
                    return true;
                }
                if (chat.usernames != null) {
                    ArrayList<TLRPC.TL_username> usernames = chat.usernames;
                    for (int j = 0; j < usernames.size(); ++j) {
                        TLRPC.TL_username username2 = usernames.get(j);
                        if (!username2.active) {
                            continue;
                        }
                        String u = translitSafe(username2.username).toLowerCase();
                        if (u.startsWith(q)) {
                            return true;
                        }
                    }
                }
            }
            return false;
        }

        public void onSearch(String text) {
            if (text != null && text.isEmpty()) {
                text = null;
            }
            this.query = text;
            updateItems(false);
        }

        public void updateTops() {
            updateSearchFieldTop();
            updateHeaderTop();
        }

        private float getSearchFieldTop() {
            float top = -Math.max(0, Math.min(dp(150), searchField.resultContainerHeight) - dp(150));
            for (int i = 0; i < listView.getChildCount(); ++i) {
                View child = listView.getChildAt(i);
                if (child.getTag() instanceof Integer && (int) child.getTag() == 34) {
                    top = Math.max(top, child.getY());
                    break;
                }
            }
            return top;
        }

        private boolean scrolling;
        private boolean searchTranslationAnimating;
        private float searchTranslationAnimatingTo;
        private ValueAnimator searchFieldAnimator;
        private void updateSearchFieldTop() {
            float ty = getSearchFieldTop();
            if (scrolling || keyboardMoving || getTranslationX() != 0) {
                searchTranslationAnimating = false;
                if (searchFieldAnimator != null) {
                    searchFieldAnimator.cancel();
                    searchFieldAnimator = null;
                }
                searchField.setTranslationY(ty);
            } else if (!searchTranslationAnimating || Math.abs(searchTranslationAnimatingTo - ty) > 1) {
                searchTranslationAnimating = true;
                if (searchFieldAnimator != null) {
                    searchFieldAnimator.cancel();
                    searchFieldAnimator = null;
                }
                searchFieldAnimator = ValueAnimator.ofFloat(searchField.getTranslationY(), searchTranslationAnimatingTo = ty);
                searchFieldAnimator.addUpdateListener(anm -> {
                    searchField.setTranslationY((float) anm.getAnimatedValue());
                });
                searchFieldAnimator.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        searchTranslationAnimating = false;
                    }
                });
                searchFieldAnimator.setInterpolator(new LinearInterpolator());
                searchFieldAnimator.setDuration(180);
                searchFieldAnimator.start();
            }
        }

        private boolean isActionBar;
        private void updateHeaderTop() {
            if (!containsHeader) {
                headerView.setVisibility(View.GONE);
                return;
            } else {
                headerView.setVisibility(View.VISIBLE);
            }
            boolean isActionBar = true;
            float top = -headerView.getHeight();
            for (int i = 0; i < listView.getChildCount(); ++i) {
                View child = listView.getChildAt(i);
                if (child.getTag() instanceof Integer && (int) child.getTag() == 35) {
                    top = contentView.getPaddingTop() + child.getY();
                    isActionBar = false;
                    break;
                }
            }
            if (this.isActionBar != isActionBar) {
                this.isActionBar = isActionBar;
                headerView.backDrawable.setRotation(isActionBar || pageType != PAGE_TYPE_SHARE ? 0f : 1f, true);
            }
            headerView.setTranslationY(Math.max(AndroidUtilities.statusBarHeight, top));
        }

        public void updateButton(boolean animated) {
            if (pageType == PAGE_TYPE_SHARE) {
                button.setShowZero(false);
                button.setEnabled(true);
                button.setCount(0, animated);
                if (isEdit) {
                    button.setText(LocaleController.getString("StoryPrivacyButtonSave"), animated);
                } else {
                    button.setText(LocaleController.getString("StoryPrivacyButtonPost", R.string.StoryPrivacyButtonPost), animated);
//                    if (selectedType == TYPE_CLOSE_FRIENDS) {
//                        button.setText(LocaleController.getString("StoryPrivacyButtonCloseFriends", R.string.StoryPrivacyButtonCloseFriends), animated);
//                        button.setCount(getCloseFriends().size(), animated);
//                    } else if (selectedType == TYPE_CONTACTS) {
//                        if (excludedContacts.isEmpty()) {
//                            button.setText(LocaleController.getString("StoryPrivacyButtonAllContacts", R.string.StoryPrivacyButtonAllContacts), animated);
//                            button.setCount(0, animated);
//                        } else {
//                            button.setText(LocaleController.formatPluralString("StoryPrivacyButtonContacts", 99), animated);
//                            button.setCount("-" + excludedContacts.size(), animated);
//                        }
//                    } else if (selectedType == TYPE_SELECTED_CONTACTS) {
//                        button.setText(LocaleController.formatPluralString("StoryPrivacyButtonContacts", selectedContacts.size()), animated);
//                        button.setCount(selectedContacts.size(), animated);
//                    } else if (selectedType == 0 && !selectedUsers.isEmpty()) {
//                        button.setText(LocaleController.formatPluralString("StoryPrivacyButtonSelectedContacts", selectedUsers.size()), animated);
//                        button.setCount(selectedUsers.size(), animated);
//                    } else {
//                        button.setText(LocaleController.getString("StoryPrivacyButtonEveryone", R.string.StoryPrivacyButtonEveryone), animated);
//                        button.setCount(selectedUsers.size(), animated);
//                    }
                }
                button2.setVisibility(sendAsMessageEnabled ? View.VISIBLE : View.GONE);
//                button2.setText(LocaleController.getString("StoryPrivacyButtonMessage", R.string.StoryPrivacyButtonMessage), animated);
            } else if (pageType == PAGE_TYPE_CLOSE_FRIENDS) {
                button.setShowZero(false);
                button.setEnabled(true); // button.setEnabled(!selectedUsers.isEmpty());
                button.setText(LocaleController.getString("StoryPrivacyButtonSaveCloseFriends", R.string.StoryPrivacyButtonSaveCloseFriends), animated);
                button.setCount(selectedUsers.size(), animated);
                button2.setVisibility(View.GONE);
            } else if (pageType == PAGE_TYPE_SELECT_CONTACTS) {
                int count = selectedContactsCount = mergeUsers(selectedUsers, selectedUsersByGroup).size();
//                button.setText(LocaleController.formatPluralString("StoryPrivacyButtonContacts", count), animated);
                button.setText(LocaleController.getString("StoryPrivacyButtonSave"), animated);
                button.setShowZero(false);
                buttonContainer.hide(count <= 0, animated);
                button.setCount(count, animated);
                button.setEnabled(count > 0);
                button2.setVisibility(View.GONE);
            } else if (pageType == PAGE_TYPE_EXCLUDE_CONTACTS) {
                button.setShowZero(false);
                button.setEnabled(true);
                if (selectedUsers.isEmpty()) {
                    button.setText(LocaleController.getString("StoryPrivacyButtonSave"), animated);
                    button.setCount(0, animated);
                } else {
                    button.setText(LocaleController.getString("StoryPrivacyButtonExcludeContacts", R.string.StoryPrivacyButtonExcludeContacts), animated);
                    button.setCount(selectedUsers.size(), animated);
                }
                button2.setVisibility(View.GONE);
            } else if (pageType == PAGE_TYPE_SEND_AS_MESSAGE) {
                button.setShowZero(true);
                button.setEnabled(!selectedUsers.isEmpty());
//                button.setText(LocaleController.formatPluralString("StoryPrivacyButtonMessageChats", selectedUsers.size()), animated);
                button.setCount(selectedUsers.size(), animated);
                button2.setVisibility(View.GONE);
            } else if (pageType == PAGE_TYPE_BLOCKLIST) {
                button.setShowZero(false);
                button.setEnabled(true); // button.setEnabled(!selectedUsers.isEmpty());
                button.setText(LocaleController.getString("StoryPrivacyButtonSaveCloseFriends", R.string.StoryPrivacyButtonSaveCloseFriends), animated);
                StoriesController storiesController = MessagesController.getInstance(currentAccount).getStoriesController();
                if (storiesController.blocklistFull) {
                    button.setCount(selectedUsers.size(), animated);
                } else {
                    int count = storiesController.getBlocklistCount();
                    for (int i = 0; i < changelog.size(); ++i) {
                        long id = changelog.keyAt(i);
                        boolean block = changelog.valueAt(i);
                        if (storiesController.blocklist.contains(id)) {
                            if (!block) {
                                count--;
                            }
                        } else {
                            if (block) {
                                count++;
                            } else {
                                count--;
                            }
                        }
                    }
                }
                button2.setVisibility(View.GONE);
            } else if (pageType == PAGE_TYPE_EXCLUDE_EVERYONE) {
                int count = excludedEveryoneCount = mergeUsers(excludedEveryone, excludedEveryoneByGroup).size();
//                button.setText(LocaleController.formatPluralString("StoryPrivacyButtonContacts", count), animated);
                button.setText(LocaleController.getString("StoryPrivacyButtonSave"), animated);
                button.setShowZero(false);
                buttonContainer.hide(false, animated);
                button.setCount(count, animated);
                button.setEnabled(true);
                button2.setVisibility(View.GONE);
            }
        }

        private void updateSectionCell(boolean animated) {
            if (sectionCell == null) {
                return;
            }
            if (mergeUsers(selectedUsers, selectedUsersByGroup).size() > 0) {
                sectionCell.setRightText(LocaleController.getString(R.string.UsersDeselectAll), true, v -> {
                    for (long userId : selectedUsers) {
                        changelog.put(userId, false);
                    }
                    for (ArrayList<Long> userIds : selectedUsersByGroup.values()) {
                        for (long userId : userIds) {
                            changelog.put(userId, false);
                        }
                    }
                    selectedUsers.clear();
                    selectedUsersByGroup.clear();
                    messageUsers.clear();
                    searchField.spansContainer.removeAllSpans(true);
                    updateCheckboxes(true);
                    updateButton(true);
                });
            } else {
                if (animated) {
                    sectionCell.setRightText(null);
                } else {
                    sectionCell.setRightText(null, null);
                }
            }
        }

        private int lastSelectedType = -1;

        public void updateCheckboxes(boolean animated) {
            if (pageType == PAGE_TYPE_EXCLUDE_EVERYONE) {
                excludedEveryone.clear();
                excludedEveryoneByGroup.clear();
                excludedEveryone.addAll(selectedUsers);
                excludedEveryoneByGroup.putAll(selectedUsersByGroup);
            } else if (pageType == PAGE_TYPE_EXCLUDE_CONTACTS) {
                excludedContacts.clear();
                excludedContacts.addAll(selectedUsers);
            } else if (pageType == PAGE_TYPE_SELECT_CONTACTS) {
                selectedContacts.clear();
                selectedContactsByGroup.clear();
                selectedContacts.addAll(selectedUsers);
                selectedContactsByGroup.putAll(selectedUsersByGroup);
            } else if (pageType == PAGE_TYPE_SHARE) {
                messageUsers.clear();
                messageUsers.addAll(selectedUsers);
            }

            if (pageType == PAGE_TYPE_SELECT_CONTACTS && (selectedType != TYPE_SELECTED_CONTACTS || selectedUsers.isEmpty() && selectedUsersByGroup.isEmpty())) {
                if (!(selectedUsers.isEmpty() && selectedUsersByGroup.isEmpty())) {
                    lastSelectedType = selectedType;
                    selectedType = TYPE_SELECTED_CONTACTS;
                } else if (lastSelectedType != -1) {
                    selectedType = lastSelectedType;
                }
            }

            HashSet<Long> allSelectedUsers = mergeUsers(selectedUsers, selectedUsersByGroup);

            for (int position = 0; position < items.size(); ++position) {
                ItemInner item = items.get(position);
                if (item != null) {
                    if (item.type > 0) {
                        item.checked = selectedType == item.type;
                        item.halfChecked = false;
                    } else if (item.user != null) {
                        item.checked = selectedUsers.contains(item.user.id);
                        item.halfChecked = !item.checked && allSelectedUsers.contains(item.user.id);
                    } else if (item.chat != null) {
                        item.checked = selectedUsersByGroup.containsKey(item.chat.id);
                        item.halfChecked = false;
                    }
                }
            }

            for (int i = 0; i < listView.getChildCount(); ++i) {
                View child = listView.getChildAt(i);
                if (child instanceof UserCell) {
                    int position = listView.getChildAdapterPosition(child);
                    if (position < 0 || position >= items.size() || !(child instanceof UserCell)) {
                        continue;
                    }
                    ItemInner item = items.get(position);
                    UserCell cell = (UserCell) child;
                    cell.setChecked(item.checked || item.halfChecked, animated);
                    if (item.chat != null) {
                        cell.setCheckboxAlpha(getParticipantsCount(item.chat) > 200 ? .3f : 1f, animated);
                    } else {
                        cell.setCheckboxAlpha(item.halfChecked && !item.checked ? .5f : 1f, animated);
                    }
                }
            }

            updateSectionCell(animated);
        }
        
        public void updateLastSeen() {
            for (int i = 0; i < listView.getChildCount(); ++i) {
                View child = listView.getChildAt(i);
                if (child instanceof UserCell) {
                    int position = listView.getChildAdapterPosition(child);
                    if (position < 0 || position >= items.size()) {
                        continue;
                    }
                    ItemInner item = items.get(position);
                    if (item.user != null) {
                        ((UserCell) child).setUser(item.user);
                    } else if (item.chat != null) {
                        ((UserCell) child).setChat(item.chat, getParticipantsCount(item.chat));
                    }
                }
            }
        }

        public void scrollToTopSmoothly() {
            LinearSmoothScrollerCustom linearSmoothScroller = new LinearSmoothScrollerCustom(getContext(), LinearSmoothScrollerCustom.POSITION_TOP, .7f);
            linearSmoothScroller.setTargetPosition(1);
            linearSmoothScroller.setOffset(-AndroidUtilities.dp(56));
            layoutManager.startSmoothScroll(linearSmoothScroller);
        }

        public void scrollToTop() {
            if (pageType != PAGE_TYPE_SHARE) {
                listView.scrollToPosition(0);
//                layoutManager.scrollToPositionWithOffset(0, (int) (-AndroidUtilities.displaySize.y * .4f));
            }
        }

        public int getTypeOn(MotionEvent e) {
            if (pageType != PAGE_TYPE_SHARE || e == null) {
                return -1;
            }
            View child = listView.findChildViewUnder(e.getX(), e.getY() - contentView.getPaddingTop());
            if (child == null) {
                return -1;
            }
            int position = listView.getChildAdapterPosition(child);
            if (position < 0 || position >= items.size()) {
                return -1;
            }
            ItemInner item = items.get(position);
            if (item.viewType != VIEW_TYPE_USER || item.sendAs) {
                return -1;
            }
            if (LocaleController.isRTL ? e.getX() < getWidth() - AndroidUtilities.dp(100) : e.getX() > AndroidUtilities.dp(100)) {
                return item.type;
            }
            return -1;
        }

        public boolean atTop() {
            return !listView.canScrollVertically(-1);
        }

        private int keyboardHeight;
        private boolean keyboardMoving;

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            if (StoryPrivacyBottomSheet.super.keyboardHeight > 0) {
                keyboardHeight = StoryPrivacyBottomSheet.super.keyboardHeight;
            }
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            contentView.setPadding(0, AndroidUtilities.statusBarHeight + (pageType == PAGE_TYPE_SHARE ? 0 : dp(56)), 0, 0);
            if (wasKeyboardVisible != keyboardVisible) {
                float searchFieldTop = getSearchFieldTop();
                if (keyboardVisible && searchFieldTop + Math.min(dp(150), searchField.resultContainerHeight) > listView.getPaddingTop()) {
                    scrollToTopSmoothly();
                }
                if (pageType == PAGE_TYPE_SHARE) {
                    buttonContainer.setTranslationY(keyboardVisible ? keyboardHeight : 0);
                    underKeyboardView.setTranslationY(keyboardVisible ? keyboardHeight : 0);
                } else {
                    buttonContainer.translateY(keyboardVisible ? keyboardHeight : -keyboardHeight, 0);
                    underKeyboardView.setTranslationY(keyboardVisible ? keyboardHeight : -keyboardHeight);
                    keyboardMoving = true;
                    underKeyboardView.animate().translationY(0).setDuration(AdjustPanLayoutHelper.keyboardDuration).setInterpolator(AdjustPanLayoutHelper.keyboardInterpolator).withEndAction(() -> {
                        keyboardMoving = false;
                    }).start();
                }
                wasKeyboardVisible = keyboardVisible;
            }
            listView.setPadding(0, 0, 0, buttonContainer.getMeasuredHeight());
        }

        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            super.onLayout(changed, left, top, right, bottom);
        }

        private boolean wasKeyboardVisible;

        @Override
        public void onClick(View v) {
            if (!searchField.allSpans.contains(v)) {
                return;
            }
            GroupCreateSpan span = (GroupCreateSpan) v;
            if (span.isDeleting()) {
                searchField.currentDeletingSpan = null;
                searchField.spansContainer.removeSpan(span);
                long id = span.getUid();
                Iterator<Map.Entry<Long, ArrayList<Long>>> iterator = selectedUsersByGroup.entrySet().iterator();
                while (iterator.hasNext()) {
                    Map.Entry<Long, ArrayList<Long>> entry = iterator.next();
                    if (entry.getValue().contains(id)) {
                        iterator.remove();
                        selectedUsers.addAll(entry.getValue());
                        selectedUsers.remove(id);
                    }
                }
                selectedUsers.remove(id);
                updateCheckboxes(true);
                updateButton(true);
            } else {
                if (searchField.currentDeletingSpan != null) {
                    searchField.currentDeletingSpan.cancelDeleteAnimation();
                    searchField.currentDeletingSpan = null;
                }
                searchField.currentDeletingSpan = span;
                span.startDeleteAnimation();
            }
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.chatInfoDidLoad);
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.chatInfoDidLoad);
        }

        private class Adapter extends AdapterWithDiffUtils {

            private Context context;
            private Theme.ResourcesProvider resourcesProvider;
            private Runnable onBack;
            private SearchUsersCell searchField;

            public Adapter(Context context, Theme.ResourcesProvider resourcesProvider, SearchUsersCell searchField, Runnable onBack) {
                this.context = context;
                this.resourcesProvider = resourcesProvider;
                this.searchField = searchField;
                this.onBack = onBack;
            }

            private RecyclerListView listView;
            public boolean reversedLayout;

            @Override
            public boolean isEnabled(RecyclerView.ViewHolder holder) {
                return (holder.getItemViewType() == VIEW_TYPE_USER && canChangePeer) || holder.getItemViewType() == VIEW_TYPE_CHECK;
            }

            @NonNull
            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                View view;
                if (viewType == VIEW_TYPE_PAD) {
                    view = new View(context);
                } else if (viewType == VIEW_TYPE_HEADER) {
                    view = new View(context);
                    view.setTag(35);
                } else if (viewType == VIEW_TYPE_SEARCH) {
                    view = new View(context);
                    view.setTag(34);
                } else if (viewType == VIEW_TYPE_USER) {
                    view = new UserCell(context, resourcesProvider);
                } else if (viewType == VIEW_TYPE_HEADER2) {
                    view = new HeaderCell2(context, resourcesProvider);
                } else if (viewType == VIEW_TYPE_HEADER_CELL) {
                    view = new org.telegram.ui.Cells.HeaderCell(context, resourcesProvider);
                    view.setBackgroundColor(Theme.getColor(Theme.key_dialogBackground, resourcesProvider));
                } else if (viewType == VIEW_TYPE_NO_USERS) {
                    StickerEmptyView searchEmptyView = new StickerEmptyView(context, null, StickerEmptyView.STICKER_TYPE_SEARCH, resourcesProvider);
                    searchEmptyView.title.setText(LocaleController.getString("NoResult", R.string.NoResult));
                    searchEmptyView.subtitle.setText(LocaleController.getString("SearchEmptyViewFilteredSubtitle2", R.string.SearchEmptyViewFilteredSubtitle2));
                    searchEmptyView.linearLayout.setTranslationY(AndroidUtilities.dp(24));
                    view = searchEmptyView;
                } else if (viewType == VIEW_TYPE_SHADOW) {
                    view = new TextInfoPrivacyCell(context, resourcesProvider);
                    view.setBackgroundColor(0xFF0D0D0D);
                } else if (viewType == VIEW_TYPE_CHECK) {
                    view = new TextCell(context, 23, true, true, resourcesProvider);
                } else {
                    view = new View(context) {
                        @Override
                        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(dp(32), MeasureSpec.EXACTLY));
                        }
                    };
                }
                return new RecyclerListView.Holder(view);
            }

            @Override
            public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                if (items == null || position < 0 || position >= items.size()) {
                    return;
                }
                final ItemInner item = items.get(position);
                final int viewType = holder.getItemViewType();
                final ItemInner neighbour = reversedLayout ? (position > 0 ? items.get(position - 1) : null) : (position + 1 < items.size() ? items.get(position + 1) : null);
                final boolean divider = neighbour != null && neighbour.viewType == viewType;
                if (viewType == VIEW_TYPE_USER) {
                    UserCell userCell = (UserCell) holder.itemView;
                    userCell.setIsSendAs(item.sendAs, !item.sendAs);
                    if (item.type > 0) {
                        userCell.setType(item.type, item.typeCount, item.user);
                        userCell.setCheckboxAlpha(1f, false);
                    } else if (item.user != null) {
                        userCell.setUser(item.user);
                        userCell.setCheckboxAlpha(item.halfChecked && !item.checked ? .5f : 1f, false);
                    } else if (item.chat != null) {
                        userCell.setChat(item.chat, getParticipantsCount(item.chat));
                    }
                    userCell.setChecked(item.checked || item.halfChecked, false);
                    userCell.setDivider(divider);
                    userCell.setRedCheckbox(item.red);
                    userCell.drawArrow = canChangePeer;
                } else if (viewType == VIEW_TYPE_SECTION) {

                } else if (viewType == VIEW_TYPE_HEADER) {
                    holder.itemView.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, AndroidUtilities.dp(56)));
                } else if (viewType == VIEW_TYPE_PAD) {
                    int height;
                    if (item.subtractHeight > 0) {
                        int h = listView != null && listView.getMeasuredHeight() > 0 ? listView.getMeasuredHeight() + keyboardHeight : AndroidUtilities.displaySize.y;
                        height = h - item.subtractHeight;
                        holder.itemView.setTag(33);
                    } else if (item.padHeight >= 0) {
                        height = item.padHeight;
                        holder.itemView.setTag(null);
                    } else {
                        height = (int) (AndroidUtilities.displaySize.y * .3f);
                        holder.itemView.setTag(33);
                    }
                    holder.itemView.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height));
                } else if (viewType == VIEW_TYPE_SEARCH) {
                    holder.itemView.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.min(dp(150), searchField.resultContainerHeight)));
                } else if (viewType == VIEW_TYPE_HEADER2) {
                    ((HeaderCell2) holder.itemView).setText(item.text, item.text2);
                } else if (viewType == VIEW_TYPE_NO_USERS) {
                    try {
                        ((StickerEmptyView) holder.itemView).stickerView.getImageReceiver().startAnimation();
                    } catch (Exception ignore) {}
                } else if (viewType == VIEW_TYPE_SHADOW) {
                    TextInfoPrivacyCell cell = (TextInfoPrivacyCell) holder.itemView;
                    if (item.text == null) {
                        cell.setFixedSize(12);
                        cell.setText(null);
                    } else {
                        cell.setFixedSize(0);
                        cell.setText(item.text);
                    }
                } else if (viewType == VIEW_TYPE_CHECK) {
                    ((TextCell) holder.itemView).setTextAndCheck(item.text, item.resId == 0 ? allowScreenshots : keepOnMyPage, divider);
                } else if (viewType == VIEW_TYPE_HEADER_CELL) {
                    ((org.telegram.ui.Cells.HeaderCell) holder.itemView).setText(item.text);
                }
            }

            @Override
            public int getItemViewType(int position) {
                if (items == null || position < 0 || position >= items.size()) {
                    return VIEW_TYPE_PAD;
                }
                return items.get(position).viewType;
            }

            @Override
            public int getItemCount() {
                return items == null ? 0 : items.size();
            }
        };
    }

    private int shiftDp = -6;
    private int storyPeriod = 86400;

    private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public StoryPrivacyBottomSheet(Context context, int period, Theme.ResourcesProvider resourcesProvider) {
        super(context, true, resourcesProvider);
        this.storyPeriod = period;
        pullSaved();
        init(context);
        viewPager.setAdapter(new ViewPagerFixed.Adapter() {
            @Override
            public int getItemCount() {
                return 2;
            }

            @Override
            public View createView(int viewType) {
                return new Page(context);
            }

            @Override
            public int getItemViewType(int position) {
                return position == 0 ? PAGE_TYPE_SHARE : activePage;
            }

            @Override
            public void bindView(View view, int position, int viewType) {
                ((Page) view).bind(viewType);
            }
        });

        MessagesStorage storage = MessagesStorage.getInstance(currentAccount);
        storage.getStorageQueue().postRunnable(() -> {
            HashMap<Long, Integer> participantsCountByChat = storage.getSmallGroupsParticipantsCount();
            if (participantsCountByChat == null || participantsCountByChat.isEmpty()) {
                return;
            }
            AndroidUtilities.runOnUIThread(() -> {
                if (smallChatsParticipantsCount == null) {
                    smallChatsParticipantsCount = new HashMap<>();
                }
                smallChatsParticipantsCount.putAll(participantsCountByChat);
            });
        });

        MessagesController.getInstance(currentAccount).getStoriesController().loadBlocklist(false);
        MessagesController.getInstance(currentAccount).getStoriesController().loadSendAs();
    }

    private void init(Context context) {
//        useSmoothKeyboard = true;
//        smoothKeyboardAnimationEnabled = true;
        Bulletin.addDelegate(container, new Bulletin.Delegate() {
            @Override
            public int getTopOffset(int tag) {
                return AndroidUtilities.statusBarHeight;
            }
        });

        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.contactsDidLoad);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.storiesBlocklistUpdate);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.storiesSendAsUpdate);

        backgroundPaint.setColor(Theme.getColor(Theme.key_dialogBackground, resourcesProvider));
        fixNavigationBar(Theme.getColor(Theme.key_dialogBackground, resourcesProvider));

        containerView = new ContainerView(context);

        viewPager = new ViewPagerFixed(context) {
            @Override
            protected void onTabAnimationUpdate(boolean manual) {
                containerView.invalidate();
            }

            @Override
            protected boolean canScroll(MotionEvent e) {
                View currentView = viewPager.getCurrentView();
                if (currentView instanceof Page) {
                    if (getCurrentPosition() > 0) {
                        closeKeyboard();
                        return true;
                    }
                    int page = ((Page) currentView).getTypeOn(e);
                    if (page != -1) {
                        activePage = page;
                        if (page == TYPE_SELECTED_CONTACTS) {
                            if (!selectedContacts.isEmpty() && !selectedContactsByGroup.isEmpty()) {
                                selectedType = page;
                            }
                        } else if (page == TYPE_EVERYONE) {
                            if (!excludedEveryone.isEmpty() && !excludedEveryoneByGroup.isEmpty()) {
                                selectedType = page;
                            }
                        } else {
                            selectedType = page;
                        }
                        ((Page) currentView).updateCheckboxes(true);
                        ((Page) currentView).updateButton(true);
                    }
                    if (page != -1) {
                        closeKeyboard();
                    }
                    return page != -1;
                }
                return true;
            }

            @Override
            protected void onItemSelected(View currentPage, View oldPage, int position, int oldPosition) {
                if (keyboardVisible) {
                    closeKeyboard();
                }
            }
        };
        viewPager.setPadding(backgroundPaddingLeft, 0, backgroundPaddingLeft, 0);
        containerView.addView(viewPager, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));
    }

    @Override
    public void dismissInternal() {
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.contactsDidLoad);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.storiesBlocklistUpdate);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.storiesSendAsUpdate);
        super.dismissInternal();
    }

    private StoryPrivacyBottomSheet(int singlePageType, Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context, true, resourcesProvider);
        init(context);
        viewPager.setAdapter(new ViewPagerFixed.Adapter() {
            @Override
            public int getItemCount() {
                return 1;
            }
            @Override
            public View createView(int viewType) {
                return new Page(context);
            }

            @Override
            public int getItemViewType(int position) {
                return singlePageType;
            }
            @Override
            public void bindView(View view, int position, int viewType) {
                ((Page) view).bind(viewType);
            }
        });
    }

    public void closeKeyboard() {
        View[] pages = viewPager.getViewPages();
        for (int i = 0; i < pages.length; ++i) {
            View page = pages[i];
            if (page instanceof Page && ((Page) page).searchField != null) {
                AndroidUtilities.hideKeyboard(((Page) page).searchField.editText);
            }
        }
    }

    private void done(StoryPrivacy privacy, Runnable loaded) {
        done(privacy, loaded, false);
    }

    private void done(StoryPrivacy privacy, Runnable loaded, boolean ignoreRestriction) {
        ArrayList<String> restrictedUsers = new ArrayList<>();
        if (warnUsers != null && privacy != null) {
            MessagesController messagesController = MessagesController.getInstance(currentAccount);
            for (int i = 0; i < warnUsers.size(); ++i) {
                String username = warnUsers.get(i);
                TLObject obj = messagesController.getUserOrChat(username);
                if (!(obj instanceof TLRPC.User)) {
                    continue;
                }
                TLRPC.User user = (TLRPC.User) obj;
                TLRPC.User user2 = messagesController.getUser(user.id);
                if (user2 != null) {
                    user = user2;
                }
                if (!user.bot && !privacy.containsUser(user)) {
                    restrictedUsers.add(username);
                }
            }
        }
        if (!restrictedUsers.isEmpty() && !ignoreRestriction) {
            SpannableStringBuilder usersString = new SpannableStringBuilder();
            for (int i = 0; i < Math.min(2, restrictedUsers.size()); ++i) {
                if (i > 0) {
                    usersString.append(", ");
                }
                SpannableString username = new SpannableString("@" + restrictedUsers.get(i));
                username.setSpan(new TypefaceSpan(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM)), 0, username.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                usersString.append(username);
            }
            new AlertDialog.Builder(getContext(), resourcesProvider)
                .setTitle(LocaleController.getString(R.string.StoryRestrictions))
                .setMessage(AndroidUtilities.replaceCharSequence("%s", LocaleController.getString(R.string.StoryRestrictionsInfo), usersString))
                .setPositiveButton(LocaleController.getString(R.string.Proceed), (di, i) -> {
                    done(privacy, loaded, true);
                })
                .setNegativeButton(LocaleController.getString(R.string.Cancel), null)
                .show();
            return;
        }
        View[] pages = viewPager.getViewPages();
        ButtonWithCounterView button = pages[0] instanceof Page ? ((Page) pages[0]).button : null;
        if (loaded != null && button != null) {
            button.setLoading(true);
        }
        if (onDone != null) {
            onDone.done(privacy, allowScreenshots, keepOnMyPage, selectedPeer, loaded != null ? () -> {
                if (button != null) {
                    button.setLoading(false);
                }
                if (loaded != null) {
                    loaded.run();
                }
            } : null);
        } else if (loaded != null) {
            loaded.run();
        }
    }

    @Override
    public void dismiss() {
        if (onDismiss != null) {
            StoryPrivacy privacy;
            if (selectedType == TYPE_SELECTED_CONTACTS) {
                HashSet<Long> users = mergeUsers(selectedContacts, selectedContactsByGroup);
                privacy = new StoryPrivacy(selectedType, currentAccount, new ArrayList<>(users));
                privacy.selectedUserIds.clear();
                privacy.selectedUserIds.addAll(selectedContacts);
                privacy.selectedUserIdsByGroup.clear();
                privacy.selectedUserIdsByGroup.putAll(selectedContactsByGroup);
            } else if (selectedType == TYPE_EVERYONE) {
                HashSet<Long> users = mergeUsers(excludedEveryone, excludedEveryoneByGroup);
                privacy = new StoryPrivacy(selectedType, currentAccount, new ArrayList<>(users));
                privacy.selectedUserIds.clear();
                privacy.selectedUserIds.addAll(excludedEveryone);
                privacy.selectedUserIdsByGroup.clear();
                privacy.selectedUserIdsByGroup.putAll(excludedEveryoneByGroup);
            } else if (selectedType == TYPE_CONTACTS) {
                privacy = new StoryPrivacy(selectedType, currentAccount, excludedContacts);
            } else {
                privacy = new StoryPrivacy(selectedType, currentAccount, null);
            }
            onDismiss.run(privacy);
            onDismiss = null;
        }
        Bulletin.removeDelegate(container);
        save();
        super.dismiss();
    }

    private class ContainerView extends FrameLayout {

        public ContainerView(Context context) {
            super(context);
        }

        private final AnimatedFloat isActionBar = new AnimatedFloat(this, 250, CubicBezierInterpolator.EASE_OUT_QUINT);
        private float top;

        private final Path path = new Path();

        @Override
        protected void dispatchDraw(Canvas canvas) {
            View[] views = viewPager.getViewPages();
            top = 0;
            float keyboardT = 0;
            for (int i = 0; i < views.length; ++i) {
                if (views[i] == null) {
                    continue;
                }
                final Page page = (Page) views[i];
                float t = Utilities.clamp(1f - Math.abs(page.getTranslationX() / (float) page.getMeasuredWidth()), 1, 0);
                top += page.top() * t;
                keyboardT += (keyboardVisible && page.pageType != PAGE_TYPE_SHARE ? 1 : 0) * t;
                if (page.getVisibility() == View.VISIBLE) {
                    page.updateTops();
                }
            }
            float actionBarT = isActionBar.set(top <= AndroidUtilities.statusBarHeight ? 1f : 0f);
            top = Math.max(AndroidUtilities.statusBarHeight, top) - AndroidUtilities.statusBarHeight * actionBarT;
            AndroidUtilities.rectTmp.set(backgroundPaddingLeft, top, getWidth() - backgroundPaddingLeft, getHeight() + dp(8));
            final float r = AndroidUtilities.lerp(dp(14), 0, actionBarT);
            canvas.drawRoundRect(AndroidUtilities.rectTmp, r, r, backgroundPaint);
            canvas.save();
            path.rewind();
            path.addRoundRect(AndroidUtilities.rectTmp, r, r, Path.Direction.CW);
            canvas.clipPath(path);
            super.dispatchDraw(canvas);
            canvas.restore();
        }

        @Override
        public boolean dispatchTouchEvent(MotionEvent event) {
            if (event.getAction() == MotionEvent.ACTION_DOWN && event.getY() < top) {
                dismiss();
                return true;
            }
            return super.dispatchTouchEvent(event);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(
                    MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(heightMeasureSpec), MeasureSpec.EXACTLY)
            );
        }
    }

    @Override
    public void onBackPressed() {
        if (viewPager.getCurrentPosition() > 0) {
            closeKeyboard();
            viewPager.scrollToPosition(viewPager.getCurrentPosition() - 1);
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected boolean canDismissWithSwipe() {
        View currentView = viewPager.getCurrentView();
        if (currentView instanceof Page) {
            return ((Page) currentView).atTop();
        }
        return true;
    }

    public interface DoneCallback {
        public void done(StoryPrivacy privacy, boolean allowScreenshots, boolean keepInProfile, TLRPC.InputPeer peer, Runnable loaded);
    }

    private ArrayList<String> warnUsers;
    private DoneCallback onDone;
    private Utilities.Callback<StoryPrivacy> onDismiss;
    private Utilities.Callback<ArrayList<Long>> onDone2;
    private Utilities.Callback<TLRPC.InputPeer> onSelectedPeer;
    private boolean applyWhenDismiss = false;
    private boolean allowSmallChats = true;
    private boolean isEdit = false;
    public StoryPrivacyBottomSheet whenDismiss(Utilities.Callback<StoryPrivacy> listener) {
        this.onDismiss = listener;
        return this;
    }
    public StoryPrivacyBottomSheet whenSelectedRules(DoneCallback onDone, boolean whenDismiss) {
        this.onDone = onDone;
        this.applyWhenDismiss = whenDismiss;
        return this;
    }
    private StoryPrivacyBottomSheet whenSelectedShare(Utilities.Callback<ArrayList<Long>> onDone2) {
        this.onDone2 = onDone2;
        return this;
    }
    public StoryPrivacyBottomSheet whenSelectedPeer(Utilities.Callback<TLRPC.InputPeer> onSelectedPeer) {
        this.onSelectedPeer = onSelectedPeer;
        return this;
    }
    public StoryPrivacyBottomSheet enableSharing(boolean enable) {
        this.sendAsMessageEnabled = enable;
        if (viewPager != null) {
            View[] viewPages = viewPager.getViewPages();
            for (int i = 0; i < viewPages.length; ++i) {
                View view = viewPages[i];
                if (view instanceof Page) {
                    ((Page) view).updateButton(false);
                }
            }
        }
        return this;
    }
    public StoryPrivacyBottomSheet allowSmallChats(boolean allowSmallChats) {
        this.allowSmallChats = allowSmallChats;
        return this;
    }
    public StoryPrivacyBottomSheet isEdit(boolean isEdit) {
        this.isEdit = isEdit;
        if (viewPager != null) {
            View[] viewPages = viewPager.getViewPages();
            for (int i = 0; i < viewPages.length; ++i) {
                View view = viewPages[i];
                if (view instanceof Page) {
                    ((Page) view).updateItems(false);
                    ((Page) view).updateButton(false);
                }
            }
        }
        return this;
    }
    public StoryPrivacyBottomSheet setWarnUsers(ArrayList<String> users) {
        this.warnUsers = users;
        return this;
    }

    public StoryPrivacyBottomSheet setPeer(TLRPC.InputPeer inputPeer) {
        selectedPeer = inputPeer;
        View[] viewPages = viewPager.getViewPages();
        if (viewPages[0] instanceof Page) {
            ((Page) viewPages[0]).bind(((Page) viewPages[0]).pageType);
        }
        if (viewPages[1] instanceof Page) {
            ((Page) viewPages[1]).bind(((Page) viewPages[1]).pageType);
        }
        return this;
    }

    public StoryPrivacyBottomSheet setValue(StoryPrivacy privacy) {
        if (privacy == null) {
            return this;
        }
        selectedType = privacy.type;
        if (selectedType == TYPE_CONTACTS) {
            excludedContacts.clear();
            excludedContacts.addAll(privacy.selectedUserIds);
        } else if (selectedType == TYPE_SELECTED_CONTACTS) {
            selectedContacts.clear();
            selectedContacts.addAll(privacy.selectedUserIds);
            selectedContactsByGroup.clear();
            selectedContactsByGroup.putAll(privacy.selectedUserIdsByGroup);
            selectedContactsCount = mergeUsers(selectedContacts, selectedContactsByGroup).size();
        } else if (selectedType == TYPE_EVERYONE) {
            excludedEveryone.clear();
            excludedEveryone.addAll(privacy.selectedUserIds);
            excludedEveryoneByGroup.clear();
            excludedEveryoneByGroup.putAll(privacy.selectedUserIdsByGroup);
            excludedEveryoneCount = mergeUsers(excludedEveryone, excludedEveryoneByGroup).size();
        }
        if (privacy.isShare()) {
            startedFromSendAsMessage = true;
            activePage = PAGE_TYPE_SEND_AS_MESSAGE;
            messageUsers.clear();
            messageUsers.addAll(privacy.sendToUsers);
            viewPager.setPosition(1);
        }
        View[] viewPages = viewPager.getViewPages();
        if (viewPages[0] instanceof Page) {
            ((Page) viewPages[0]).bind(((Page) viewPages[0]).pageType);
        }
        if (viewPages[1] instanceof Page) {
            ((Page) viewPages[1]).bind(((Page) viewPages[1]).pageType);
        }
        return this;
    }

    public static final int VIEW_TYPE_PAD = -1;
    public static final int VIEW_TYPE_HEADER = 0;
    public static final int VIEW_TYPE_SEARCH = 1;
    public static final int VIEW_TYPE_SECTION = 2;
    public static final int VIEW_TYPE_USER = 3;
    public static final int VIEW_TYPE_HEADER2 = 4;
    public static final int VIEW_TYPE_NO_USERS = 5;
    public static final int VIEW_TYPE_EMPTY_VIEW = 5;
    public static final int VIEW_TYPE_SHADOW = 6;
    public static final int VIEW_TYPE_CHECK = 7;
    public static final int VIEW_TYPE_HEADER_CELL = 8;

    private static class ItemInner extends AdapterWithDiffUtils.Item {

        public int resId;
        public CharSequence text, text2;
        public TLRPC.User user;
        public TLRPC.Chat chat;
        public int type;
        public int typeCount;
        public boolean checked;
        public boolean halfChecked;
        public boolean red;
        public boolean sendAs;
        public int subtractHeight;
        public int padHeight = -1;

        private ItemInner(int viewType, boolean selectable) {
            super(viewType, selectable);
        }

        public static ItemInner asPad() {
            return asPad(-1);
        }
        public static ItemInner asPad(int subtractHeight) {
            ItemInner item = new ItemInner(VIEW_TYPE_PAD, false);
            item.subtractHeight = subtractHeight;
            return item;
        }
        public static ItemInner asHeader() {
            return new ItemInner(VIEW_TYPE_HEADER, false);
        }
        public static ItemInner asHeader2(CharSequence title, CharSequence subtitle) {
            ItemInner item = new ItemInner(VIEW_TYPE_HEADER2, false);
            item.text = title;
            item.text2 = subtitle;
            return item;
        }
        public static ItemInner asHeaderCell(CharSequence text) {
            ItemInner item = new ItemInner(VIEW_TYPE_HEADER_CELL, false);
            item.text = text;
            return item;
        }
        public static ItemInner asSearchField() {
            return new ItemInner(VIEW_TYPE_SEARCH, false);
        }
        public static ItemInner asSection() {
            ItemInner item = new ItemInner(VIEW_TYPE_SECTION, false);
            return item;
        }
        public static ItemInner asUser(TLRPC.User user, boolean checked, boolean halfChecked) {
            ItemInner item = new ItemInner(VIEW_TYPE_USER, true);
            item.user = user;
            item.checked = checked;
            item.halfChecked = halfChecked;
            return item;
        }
        public static ItemInner asChat(TLRPC.Chat chat, boolean checked) {
            ItemInner item = new ItemInner(VIEW_TYPE_USER, true);
            item.chat = chat;
            item.checked = checked;
            return item;
        }
        public static ItemInner asType(int type, boolean checked) {
            ItemInner item = new ItemInner(VIEW_TYPE_USER, false);
            item.type = type;
            item.checked = checked;
            return item;
        }
        public static ItemInner asType(int type, boolean checked, int count) {
            ItemInner item = new ItemInner(VIEW_TYPE_USER, false);
            item.type = type;
            item.checked = checked;
            item.typeCount = count;
            return item;
        }
        public static ItemInner asShadow(CharSequence text) {
            ItemInner item = new ItemInner(VIEW_TYPE_SHADOW, false);
            item.text = text;
            return item;
        }
        public static ItemInner asCheck(CharSequence text, int id, boolean checked) {
            ItemInner item = new ItemInner(VIEW_TYPE_CHECK, false);
            item.resId = id;
            item.text = text;
            item.checked = checked;
            return item;
        }

        public static ItemInner asNoUsers() {
            return new ItemInner(VIEW_TYPE_NO_USERS, false);
        }
        public static ItemInner asPadding(int padHeight) {
            ItemInner item = new ItemInner(VIEW_TYPE_PAD, false);
            item.padHeight = padHeight;
            return item;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ItemInner i = (ItemInner) o;
            if (viewType != i.viewType) {
                return false;
            }
            if (viewType == VIEW_TYPE_PAD && (subtractHeight != i.subtractHeight || padHeight != i.padHeight)) {
                return false;
            } else if (viewType == VIEW_TYPE_USER && (user != i.user || chat != i.chat || type != i.type || typeCount != i.typeCount || checked != i.checked || red != i.red || sendAs != i.sendAs)) {
                return false;
            } else if (viewType == VIEW_TYPE_HEADER && resId != i.resId) {
                return false;
            } else if (viewType == VIEW_TYPE_SECTION && !TextUtils.equals(text, i.text)) {
                return false;
            } else if (viewType == VIEW_TYPE_HEADER_CELL && !TextUtils.equals(text, i.text)) {
                return false;
            } else if (viewType == VIEW_TYPE_HEADER2 && (!TextUtils.equals(text, i.text) || !TextUtils.equals(text2, i.text2))) {
                return false;
            } else if (viewType == VIEW_TYPE_SHADOW && (!TextUtils.equals(text, i.text) || resId != i.resId)) {
                return false;
            } else if (viewType == VIEW_TYPE_CHECK && (resId != i.resId || !TextUtils.equals(text, i.text) || checked != i.checked)) {
                return false;
            }
            return true;
        }

        public ItemInner red(boolean red) {
            this.red = red;
            return this;
        }

        public ItemInner asSendAs() {
            sendAs = true;
            return this;
        }
    }

    private boolean loadedContacts;

    private ArrayList<TLObject> getContacts() {
        final ArrayList<TLObject> chats = new ArrayList<>();
        final ArrayList<TLRPC.TL_contact> contacts = ContactsController.getInstance(currentAccount).contacts;
        if (contacts == null || contacts.isEmpty()) {
            ContactsController.getInstance(currentAccount).loadContacts(false, 0);
        }
        final MessagesController messagesController = MessagesController.getInstance(currentAccount);
        if (contacts != null) {
            for (int i = 0; i < contacts.size(); ++i) {
                final TLRPC.TL_contact contact = contacts.get(i);
                if (contact != null) {
                    final TLRPC.User user = messagesController.getUser(contact.user_id);
                    if (user != null && !UserObject.isUserSelf(user) && !user.bot && user.id != 777000) {
                        chats.add(user);
                    }
                }
            }
        }
        return chats;
    }

    private ArrayList<TLObject> getCloseFriends() {
        final ArrayList<TLObject> contacts = getContacts();
        for (int i = 0; i < contacts.size(); ++i) {
            final TLObject chat = contacts.get(i);
            if (!(chat instanceof TLRPC.User)) {
                continue;
            }
            final TLRPC.User user = (TLRPC.User) chat;
            if (user == null || !user.close_friend) {
                contacts.remove(i);
                i--;
            }
        }
        return contacts;
    }

    private ArrayList<TLObject> getUsers(boolean onlyContacts, boolean includeSmallChats) {
        final MessagesController messagesController = MessagesController.getInstance(currentAccount);
        final HashMap<Long, Boolean> contains = new HashMap<>();
        final ArrayList<TLObject> users = new ArrayList<>();
        final ArrayList<TLRPC.Dialog> dialogs = messagesController.getAllDialogs();
        final ConcurrentHashMap<Long, TLRPC.TL_contact> contacts = ContactsController.getInstance(currentAccount).contactsDict;
        if (contacts == null || contacts.isEmpty()) {
            if (!loadedContacts) {
                ContactsController.getInstance(currentAccount).loadContacts(false, 0);
            }
            loadedContacts = true;
        }
        for (int i = 0; i < dialogs.size(); ++i) {
            TLRPC.Dialog dialog = dialogs.get(i);
            if (DialogObject.isUserDialog(dialog.id)) {
                TLRPC.User user = messagesController.getUser(dialog.id);
                if (user != null && !user.bot && user.id != 777000 && !UserObject.isUserSelf(user) && !user.deleted) {
                    if (onlyContacts && (contacts == null || contacts.get(user.id) == null)) {
                        continue;
                    }
                    contains.put(user.id, true);
                    users.add(user);
                }
            } else if (includeSmallChats && DialogObject.isChatDialog(dialog.id)) {
                TLRPC.Chat chat = messagesController.getChat(-dialog.id);
                if (chat == null || ChatObject.isChannelAndNotMegaGroup(chat)) {
                    continue;
                }
//                int participants_count = getParticipantsCount(chat);
//                if (participants_count > 1) {
                    contains.put(-chat.id, true);
                    users.add(chat);
//                }
            }
        }
        if (contacts != null) {
            for (Map.Entry<Long, TLRPC.TL_contact> e : contacts.entrySet()) {
                long id = e.getKey();
                if (!contains.containsKey(id)) {
                    TLRPC.User user = messagesController.getUser(id);
                    if (user != null && !user.bot && user.id != 777000 && !UserObject.isUserSelf(user)) {
                        users.add(user);
                        contains.put(user.id, true);
                    }
                }
            }
        }
        return users;
    }

    private int getParticipantsCount(TLRPC.Chat chat) {
        TLRPC.ChatFull chatFull = MessagesController.getInstance(currentAccount).getChatFull(chat.id);
        if (chatFull != null && chatFull.participants_count > 0) {
            return chatFull.participants_count;
        } else if (smallChatsParticipantsCount != null) {
            Integer count = smallChatsParticipantsCount.get(chat.id);
            if (count != null) {
                return count;
            }
        }
        return chat.participants_count;
    }

    private ArrayList<TLObject> getChats() {
        final ArrayList<TLObject> chats = new ArrayList<>();
        final MessagesController messagesController = MessagesController.getInstance(currentAccount);
        final ArrayList<TLRPC.Dialog> dialogs = messagesController.getAllDialogs();
        for (int i = 0; i < dialogs.size(); ++i) {
            TLRPC.Dialog dialog = dialogs.get(i);
            if (!messagesController.canAddToForward(dialog)) {
                continue;
            }
            if (DialogObject.isUserDialog(dialog.id)) {
                TLRPC.User user = messagesController.getUser(dialog.id);
                if (user != null && !user.bot && user.id != 777000 && !UserObject.isUserSelf(user)) {
                    chats.add(user);
                }
            } else if (DialogObject.isChatDialog(dialog.id)) {
                TLRPC.Chat chat = messagesController.getChat(-dialog.id);
                if (chat != null && !ChatObject.isForum(chat)) {
                    chats.add(chat);
                }
            }
        }
        return chats;
    }

    public static class UserCell extends FrameLayout {

        private final Theme.ResourcesProvider resourcesProvider;

        private final AvatarDrawable avatarDrawable = new AvatarDrawable();
        private final BackupImageView imageView;

        private final SimpleTextView titleTextView;
        private final SimpleTextView subtitleTextView;

        public final CheckBox2 checkBox;
        public final RadioButton radioButton;

        private final Paint dividerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        private boolean sendAs = false;
        private boolean needCheck = true;
        private boolean drawArrow = true;

        public UserCell(Context context, Theme.ResourcesProvider resourcesProvider) {
            super(context);
            this.resourcesProvider = resourcesProvider;

            avatarDrawable.setRoundRadius(AndroidUtilities.dp(40));

            imageView = new BackupImageView(context);
            imageView.setRoundRadius(AndroidUtilities.dp(20));
            addView(imageView);

            titleTextView = new SimpleTextView(context);
            titleTextView.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM));
            titleTextView.setTextSize(16);
            titleTextView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider));
            titleTextView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
            NotificationCenter.listenEmojiLoading(titleTextView);
            addView(titleTextView);

            subtitleTextView = new SimpleTextView(context);
            subtitleTextView.setTextSize(14);
            subtitleTextView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider));
            subtitleTextView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
            NotificationCenter.listenEmojiLoading(subtitleTextView);
            addView(subtitleTextView);

            checkBox = new CheckBox2(context, 21, resourcesProvider);
            checkBox.setColor(Theme.key_dialogRoundCheckBox, Theme.key_checkboxDisabled, Theme.key_dialogRoundCheckBoxCheck);
            checkBox.setDrawUnchecked(true);
            checkBox.setDrawBackgroundAsArc(10);
            addView(checkBox);
            checkBox.setChecked(false, false);
            checkBox.setVisibility(View.GONE);

            radioButton = new RadioButton(context);
            radioButton.setSize(AndroidUtilities.dp(20));
            radioButton.setColor(Theme.getColor(Theme.key_checkboxDisabled, resourcesProvider), Theme.getColor(Theme.key_dialogRadioBackgroundChecked, resourcesProvider));
            addView(radioButton);
            radioButton.setVisibility(View.GONE);

            updateLayouts();
        }

        private void updateLayouts() {
            imageView.setLayoutParams(LayoutHelper.createFrame(40, 40, Gravity.CENTER_VERTICAL | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT), needCheck ? 53 : 16, 0, needCheck ? 53 : 16, 0));
            titleTextView.setLayoutParams(LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT), LocaleController.isRTL ? 20 : (needCheck ? 105 : 68), 0, LocaleController.isRTL ? (needCheck ? 105 : 68) : 20, 0));
            subtitleTextView.setLayoutParams(LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT), LocaleController.isRTL ? 20 : (needCheck ? 105 : 68), 0, LocaleController.isRTL ? (needCheck ? 105 : 68) : 20, 0));
            checkBox.setLayoutParams(LayoutHelper.createFrame(24, 24, Gravity.CENTER_VERTICAL | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT), 13, 0, 14, 0));
            radioButton.setLayoutParams(LayoutHelper.createFrame(22, 22, Gravity.CENTER_VERTICAL | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT), 14, 0, 15, 0));
        }

        public void setIsSendAs(boolean isSendAs, boolean needsCheck) {
            sendAs = isSendAs;
            if (needsCheck != needCheck) {
                this.needCheck = needsCheck;
                updateLayouts();
            }
            if (!needCheck) {
                radioButton.setVisibility(View.GONE);
                checkBox.setVisibility(View.GONE);
            }
            setWillNotDraw(!(needDivider || (!needCheck && sendAs)));
        }

        public void setRedCheckbox(boolean red) {
            checkBox.setColor(red ? Theme.key_color_red : Theme.key_dialogRoundCheckBox, Theme.key_checkboxDisabled, Theme.key_dialogRoundCheckBoxCheck);
        }

        public void setChecked(boolean checked, boolean animated) {
            if (checkBox.getVisibility() == View.VISIBLE) {
                checkBox.setChecked(checked, animated);
            }
            if (radioButton.getVisibility() == View.VISIBLE) {
                radioButton.setChecked(checked, animated);
            }
        }

        public void setCheckboxAlpha(float alpha, boolean animated) {
            if (animated) {
                if (Math.abs(checkBox.getAlpha() - alpha) > .1) {
                    checkBox.animate().cancel();
                    checkBox.animate().alpha(alpha).start();
                }
                if (Math.abs(radioButton.getAlpha() - alpha) > .1) {
                    radioButton.animate().cancel();
                    radioButton.animate().alpha(alpha).start();
                }
            } else {
                checkBox.animate().cancel();
                checkBox.setAlpha(alpha);
                radioButton.animate().cancel();
                radioButton.setAlpha(alpha);
            }
        }

        private boolean[] isOnline = new boolean[1];


        public void set(Object object) {
            if (object instanceof TLRPC.User) {
                titleTextView.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM));
                titleTextView.setTranslationX(0);
                setUser((TLRPC.User) object);
            } else if (object instanceof TLRPC.Chat) {
                titleTextView.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM));
                titleTextView.setTranslationX(0);
                setChat((TLRPC.Chat) object, 0);
            } else if (object instanceof String) {
                titleTextView.setTypeface(null);
                titleTextView.setTranslationX(-dp(52) * (LocaleController.isRTL ? -1 : 1));
                titleTextView.setText((String) object);
            }
        }

        public long dialogId;

        public void setUser(TLRPC.User user) {
            dialogId = user == null ? 0 : user.id;

            avatarDrawable.setInfo(user);
            imageView.setRoundRadius(dp(20));
            imageView.setForUserOrChat(user, avatarDrawable);

            CharSequence text = UserObject.getUserName(user);
            text = Emoji.replaceEmoji(text, titleTextView.getPaint().getFontMetricsInt(), false);
            titleTextView.setText(text);
            isOnline[0] = false;
            if (sendAs) {
                setSubtitle(LocaleController.getString(R.string.VoipGroupPersonalAccount));
                subtitleTextView.setTextColor(Theme.getColor(Theme.key_dialogTextGray3, resourcesProvider));
            } else {
                setSubtitle(LocaleController.formatUserStatus(UserConfig.selectedAccount, user, isOnline));
                subtitleTextView.setTextColor(Theme.getColor(isOnline[0] ? Theme.key_dialogTextBlue2 : Theme.key_dialogTextGray3, resourcesProvider));
            }

            checkBox.setVisibility(needCheck ? View.VISIBLE : View.GONE);
            checkBox.setAlpha(1f);
            radioButton.setVisibility(View.GONE);
        }

        public void setChat(TLRPC.Chat chat, int participants_count) {
            dialogId = chat == null ? 0 : -chat.id;

            avatarDrawable.setInfo(chat);
            imageView.setRoundRadius(dp(ChatObject.isForum(chat) ? 12 : 20));
            imageView.setForUserOrChat(chat, avatarDrawable);

            CharSequence text = chat.title;
            text = Emoji.replaceEmoji(text, titleTextView.getPaint().getFontMetricsInt(), false);
            titleTextView.setText(text);

            isOnline[0] = false;
            String subtitle;
            if (sendAs) {
                if (participants_count <= 0) {
                    participants_count = chat.participants_count;
                }
                boolean isChannel = ChatObject.isChannelAndNotMegaGroup(chat);
                if (participants_count >= 1) {
                    subtitle = LocaleController.formatPluralString(isChannel ? "Subscribers" : "Members", participants_count);
                } else {
                    subtitle = LocaleController.getString(isChannel ? R.string.DiscussChannel : R.string.AccDescrGroup);
                }
            } else if (ChatObject.isChannel(chat) && !chat.megagroup) {
                if (participants_count >= 1) {
                    subtitle = LocaleController.formatPluralStringComma("Subscribers", participants_count - 1);
                } else {
                    if (!ChatObject.isPublic(chat)) {
                        subtitle = LocaleController.getString("ChannelPrivate", R.string.ChannelPrivate).toLowerCase();
                    } else {
                        subtitle = LocaleController.getString("ChannelPublic", R.string.ChannelPublic).toLowerCase();
                    }
                }
            } else {
                if (participants_count >= 1) {
                    subtitle = LocaleController.formatPluralStringComma("Members", participants_count - 1);
                } else {
                    if (chat.has_geo) {
                        subtitle = LocaleController.getString("MegaLocation", R.string.MegaLocation);
                    } else if (!ChatObject.isPublic(chat)) {
                        subtitle = LocaleController.getString("MegaPrivate", R.string.MegaPrivate).toLowerCase();
                    } else {
                        subtitle = LocaleController.getString("MegaPublic", R.string.MegaPublic).toLowerCase();
                    }
                }
            }
            setSubtitle(subtitle);
            subtitleTextView.setTextColor(Theme.getColor(isOnline[0] ? Theme.key_dialogTextBlue2 : Theme.key_dialogTextGray3, resourcesProvider));

            checkBox.setVisibility(needCheck ? View.VISIBLE : View.GONE);
            radioButton.setVisibility(View.GONE);
            setCheckboxAlpha(participants_count > 200 ? .3f : 1f, false);
        }

        private CharSequence withArrow(CharSequence text) {
            SpannableString arrow = new SpannableString(">");
            Drawable arrowDrawable = getContext().getResources().getDrawable(R.drawable.attach_arrow_right);
            ColoredImageSpan span = new ColoredImageSpan(arrowDrawable, ColoredImageSpan.ALIGN_CENTER);
            arrowDrawable.setBounds(0, dp(1), dp(11), dp(1 + 11));
            arrow.setSpan(span, 0, arrow.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            SpannableStringBuilder finalText = new SpannableStringBuilder();
            finalText.append(text).append(" ").append(arrow);
            return finalText;
        }

        public void setType(int type, int count, TLRPC.User singleUser) {
            if (type == TYPE_EVERYONE) {
                titleTextView.setText(LocaleController.getString("StoryPrivacyOptionEveryone", R.string.StoryPrivacyOptionEveryone));
                if (count == 1 && singleUser != null) {
                    CharSequence text = LocaleController.formatString(R.string.StoryPrivacyOptionExcludePerson, UserObject.getUserName(singleUser));
                    text = Emoji.replaceEmoji(text, subtitleTextView.getPaint().getFontMetricsInt(), false);
                    setSubtitle(withArrow(text));
                } else if (count > 0) {
                    setSubtitle(withArrow(LocaleController.formatPluralString("StoryPrivacyOptionExcludePeople", count)));
                } else {
                    setSubtitle(withArrow(LocaleController.getString("StoryPrivacyOptionContactsDetail", R.string.StoryPrivacyOptionContactsDetail)));
                }
                subtitleTextView.setTextColor(Theme.getColor(Theme.key_dialogTextBlue2, resourcesProvider));
                avatarDrawable.setAvatarType(AvatarDrawable.AVATAR_TYPE_FILTER_CHANNELS);
                avatarDrawable.setColor(0xFF16A5F2, 0xFF1180F7);
            } else if (type == TYPE_CONTACTS) {
                titleTextView.setText(LocaleController.getString("StoryPrivacyOptionContacts", R.string.StoryPrivacyOptionContacts));
                if (count == 1 && singleUser != null) {
                    CharSequence text = LocaleController.formatString(R.string.StoryPrivacyOptionExcludePerson, UserObject.getUserName(singleUser));
                    text = Emoji.replaceEmoji(text, subtitleTextView.getPaint().getFontMetricsInt(), false);
                    setSubtitle(withArrow(text));
                } else if (count > 0) {
                    setSubtitle(withArrow(LocaleController.formatPluralString("StoryPrivacyOptionExcludePeople", count)));
                } else {
                    setSubtitle(withArrow(LocaleController.getString("StoryPrivacyOptionContactsDetail", R.string.StoryPrivacyOptionContactsDetail)));
                }
                subtitleTextView.setTextColor(Theme.getColor(Theme.key_dialogTextBlue2, resourcesProvider));
                avatarDrawable.setAvatarType(AvatarDrawable.AVATAR_TYPE_FILTER_CONTACTS);
                avatarDrawable.setColor(0xFFC468F2, 0xFF965CFA);
            } else if (type == TYPE_CLOSE_FRIENDS) {
                titleTextView.setText(LocaleController.getString("StoryPrivacyOptionCloseFriends", R.string.StoryPrivacyOptionCloseFriends));
                if (count == 1 && singleUser != null) {
                    CharSequence text = UserObject.getUserName(singleUser);
                    text = Emoji.replaceEmoji(text, subtitleTextView.getPaint().getFontMetricsInt(), false);
                    setSubtitle(withArrow(text));
                } else if (count > 0) {
                    setSubtitle(withArrow(LocaleController.formatPluralString("StoryPrivacyOptionPeople", count)));
                } else {
                    setSubtitle(withArrow(LocaleController.getString("StoryPrivacyOptionCloseFriendsDetail", R.string.StoryPrivacyOptionCloseFriendsDetail)));
                }
                subtitleTextView.setTextColor(Theme.getColor(Theme.key_dialogTextBlue2, resourcesProvider));
                avatarDrawable.setAvatarType(AvatarDrawable.AVATAR_TYPE_CLOSE_FRIENDS);
                avatarDrawable.setColor(0xFF88D93A, 0xFF2DB63B);
            } else if (type == TYPE_SELECTED_CONTACTS) {
                titleTextView.setText(LocaleController.getString("StoryPrivacyOptionSelectedContacts", R.string.StoryPrivacyOptionSelectedContacts));
                if (count == 1 && singleUser != null) {
                    CharSequence text = UserObject.getUserName(singleUser);
                    text = Emoji.replaceEmoji(text, subtitleTextView.getPaint().getFontMetricsInt(), false);
                    setSubtitle(withArrow(text));
                } else if (count > 0) {
                    setSubtitle(withArrow(LocaleController.formatPluralString("StoryPrivacyOptionPeople", count)));
                } else {
                    setSubtitle(withArrow(LocaleController.getString("StoryPrivacyOptionSelectedContactsDetail", R.string.StoryPrivacyOptionSelectedContactsDetail)));
                }
                subtitleTextView.setTextColor(Theme.getColor(Theme.key_dialogTextBlue2, resourcesProvider));
                avatarDrawable.setAvatarType(AvatarDrawable.AVATAR_TYPE_FILTER_GROUPS);
                avatarDrawable.setColor(0xFFFFB743, 0xFFF68E34);
            }
            checkBox.setVisibility(View.GONE);
            radioButton.setVisibility(needCheck ? View.VISIBLE : View.GONE);
            imageView.setImageDrawable(avatarDrawable);
            imageView.setRoundRadius(dp(20));
        }

        private void setSubtitle(CharSequence text) {
            if (text == null) {
                titleTextView.setTranslationY(0);
                subtitleTextView.setVisibility(View.GONE);
            } else {
                titleTextView.setTranslationY(AndroidUtilities.dp(-9));
                subtitleTextView.setTranslationY(AndroidUtilities.dp(12));
                subtitleTextView.setText(text);
                subtitleTextView.setVisibility(View.VISIBLE);
            }
        }

        private boolean needDivider;
        public void setDivider(boolean divider) {
            setWillNotDraw(!((needDivider = divider) || (!needCheck && sendAs)));
        }

        private Path arrowPath;
        private Paint arrowPaint;

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(
                MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(sendAs && !needCheck ? 62 : 56), MeasureSpec.EXACTLY)
            );

            if (!needCheck && sendAs) {
                if (arrowPath == null) {
                    arrowPath = new Path();
                } else {
                    arrowPath.rewind();
                }
                final float cx = LocaleController.isRTL ? dp(31) : getMeasuredWidth() - dp(31);
                final float cy = getMeasuredHeight() / 2f;
                final float m = LocaleController.isRTL ? -1 : 1;
                arrowPath.moveTo(cx, cy - dp(6));
                arrowPath.lineTo(cx + m * dp(6), cy);
                arrowPath.lineTo(cx, cy + dp(6));
                if (arrowPaint == null) {
                    arrowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                    arrowPaint.setStyle(Paint.Style.STROKE);
                    arrowPaint.setStrokeCap(Paint.Cap.ROUND);
                }
                arrowPaint.setStrokeWidth(dpf2(1.86f));
                arrowPaint.setColor(Theme.multAlpha(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider), .3f));
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (needDivider) {
                dividerPaint.setColor(Theme.getColor(Theme.key_divider, resourcesProvider));
                if (LocaleController.isRTL) {
                    canvas.drawRect(0, getHeight() - 1, getWidth() - dp(105), getHeight(), dividerPaint);
                } else {
                    canvas.drawRect(dp(105), getHeight() - 1, getWidth(), getHeight(), dividerPaint);
                }
            }
            if (arrowPath != null && arrowPaint != null && !needCheck && sendAs && drawArrow) {
                canvas.drawPath(arrowPath, arrowPaint);
            }
        }
    }

    private static class HeaderCell2 extends LinearLayout {

        private final Theme.ResourcesProvider resourcesProvider;
        private final TextView titleTextView;
        private final TextView subtitleTextView;

        public HeaderCell2(Context context, Theme.ResourcesProvider resourcesProvider) {
            super(context);
            setOrientation(VERTICAL);
            this.resourcesProvider = resourcesProvider;

            titleTextView = new TextView(context);
            titleTextView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider));
            titleTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
            titleTextView.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM));
            addView(titleTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.FILL_HORIZONTAL, 27, 16, 27, 0));

            subtitleTextView = new TextView(context);
            subtitleTextView.setTextColor(Theme.getColor(Theme.key_dialogTextGray2, resourcesProvider));
            subtitleTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            addView(subtitleTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.FILL_HORIZONTAL, 27, 5, 27, 13));
        }

        public void setText(CharSequence title, CharSequence subtitle) {
            titleTextView.setText(title);
            subtitleTextView.setText(subtitle);
        }
    }

    private static class HeaderCell extends FrameLayout {

        private final Theme.ResourcesProvider resourcesProvider;

        private ImageView closeView;
        private TextView textView;
        public BackDrawable backDrawable;

        private final Paint dividerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        public HeaderCell(Context context, Theme.ResourcesProvider resourcesProvider) {
            super(context);
            this.resourcesProvider = resourcesProvider;

            textView = new TextView(context);
            textView.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM));
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
            textView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
            textView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider));
            addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL | Gravity.FILL_HORIZONTAL, LocaleController.isRTL ? 16 : 53, 0, LocaleController.isRTL ? 53 : 16, 0));

            closeView = new ImageView(context);
            closeView.setImageDrawable(backDrawable = new BackDrawable(false));
            backDrawable.setColor(0xffffffff);
            backDrawable.setRotatedColor(0xffffffff);
            backDrawable.setAnimationTime(220);
//            closeView.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_dialogButtonSelector, resourcesProvider)));
            addView(closeView, LayoutHelper.createFrame(24, 24, Gravity.CENTER_VERTICAL | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT), 16, 0, 16, 0));
            closeView.setOnClickListener(e -> {
                if (onCloseClickListener != null) {
                    onCloseClickListener.run();
                }
            });
        }

        @Override
        protected void dispatchDraw(Canvas canvas) {
            super.dispatchDraw(canvas);

            dividerPaint.setColor(Theme.getColor(Theme.key_divider, resourcesProvider));
            canvas.drawRect(0, getHeight() - AndroidUtilities.getShadowHeight(), getWidth(), getHeight(), dividerPaint);
        }

        public void setText(CharSequence text) {
            textView.setText(text);
        }

        public void setCloseImageVisible(boolean visible) {
            closeView.setVisibility(visible ? View.VISIBLE : View.GONE);
            textView.setLayoutParams(LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL | Gravity.FILL_HORIZONTAL, LocaleController.isRTL || !visible ? 22 : 53, 0, LocaleController.isRTL && visible ? 53 : 22, 0));
        }

        public void setBackImage(int resId) {
            closeView.setImageResource(resId);
        }

        private Runnable onCloseClickListener;
        public void setOnCloseClickListener(Runnable onCloseClickListener) {
            this.onCloseClickListener = onCloseClickListener;
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(
                MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(56), MeasureSpec.EXACTLY)
            );
        }
    }

    public class SearchUsersCell extends ScrollView {

        private final Theme.ResourcesProvider resourcesProvider;

        private EditTextBoldCursor editText;
        private int hintTextWidth;

        public SpansContainer spansContainer;
        private int selectedCount;
        public ArrayList<GroupCreateSpan> allSpans = new ArrayList<>();
        private GroupCreateSpan currentDeletingSpan;
        private Runnable updateHeight;

        private boolean ignoreTextChange;
        private Utilities.Callback<String> onSearchTextChange;

        public SearchUsersCell(Context context, Theme.ResourcesProvider resourcesProvider, Runnable updateHeight) {
            super(context);
            this.resourcesProvider = resourcesProvider;
            this.updateHeight = updateHeight;

            setVerticalScrollBarEnabled(false);
            AndroidUtilities.setScrollViewEdgeEffectColor(this, Theme.getColor(Theme.key_windowBackgroundWhite));

            spansContainer = new SpansContainer(context);
            addView(spansContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
//            spansContainer.setOnClickListener(v -> {
//                editText.clearFocus();
//                editText.requestFocus();
//                AndroidUtilities.showKeyboard(editText);
//            });

            editText = new EditTextBoldCursor(context) {
                @Override
                public boolean onTouchEvent(MotionEvent event) {
                    if (currentDeletingSpan != null) {
                        currentDeletingSpan.cancelDeleteAnimation();
                        currentDeletingSpan = null;
                    }
                    if (event.getAction() == MotionEvent.ACTION_DOWN) {
                        if (!AndroidUtilities.showKeyboard(this)) {
                            fullScroll(View.FOCUS_DOWN);
                            clearFocus();
                            requestFocus();
                        }
                    }
                    return super.onTouchEvent(event);
                }
            };
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
                editText.setRevealOnFocusHint(false);
            }
            editText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            editText.setHintColor(Theme.getColor(Theme.key_groupcreate_hintText, resourcesProvider));
            editText.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
            editText.setCursorColor(Theme.getColor(Theme.key_groupcreate_cursor, resourcesProvider));
            editText.setHandlesColor(Theme.getColor(Theme.key_groupcreate_cursor, resourcesProvider));
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
            editText.setHintText(LocaleController.getString("Search", R.string.Search));
            hintTextWidth = (int) editText.getPaint().measureText(LocaleController.getString("Search", R.string.Search));
            editText.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {

                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {

                }

                @Override
                public void afterTextChanged(Editable s) {
                    if (ignoreTextChange) {
                        return;
                    }
                    if (onSearchTextChange != null && s != null) {
                        onSearchTextChange.run(s.toString());
                    }
                }
            });
//            editText.setOnKeyListener(new OnKeyListener() {
//                boolean wasEmpty;
//                @Override
//                public boolean onKey(View v, int keyCode, KeyEvent event) {
//                    if (keyCode == KeyEvent.KEYCODE_DEL) {
//                        if (event.getAction() == KeyEvent.ACTION_DOWN) {
//                            wasEmpty = editText.length() == 0;
//                        } else if (event.getAction() == KeyEvent.ACTION_UP && wasEmpty) {
//                            if (!allSpans.isEmpty()) {
//                                GroupCreateSpan lastSpan = allSpans.get(allSpans.size() - 1);
//                                if (lastSpan == null) {
//                                    return false;
//                                }
//                                View[] viewPages = viewPager.getViewPages();
//                                if (viewPages[0] instanceof Page) {
//                                    ((Page) viewPages[0]).onClick(lastSpan);
//                                }
//                                if (viewPages[1] instanceof Page) {
//                                    ((Page) viewPages[1]).onClick(lastSpan);
//                                }
//                                return true;
//                            }
//                        }
//                    }
//                    return false;
//                }
//            });
        }

        @Override
        public boolean dispatchKeyEvent(KeyEvent event) {
            return super.dispatchKeyEvent(event);
        }

        private final AnimatedFloat topGradientAlpha = new AnimatedFloat(this, 0, 300, CubicBezierInterpolator.EASE_OUT_QUINT);
        private final LinearGradient topGradient = new LinearGradient(0, 0, 0, dp(8), new int[] { 0xff000000, 0x00000000 }, new float[] { 0, 1 }, Shader.TileMode.CLAMP);
        private final Paint topGradientPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Matrix topGradientMatrix = new Matrix();

        private final AnimatedFloat bottomGradientAlpha = new AnimatedFloat(this, 0, 300, CubicBezierInterpolator.EASE_OUT_QUINT);
        private final LinearGradient bottomGradient = new LinearGradient(0, 0, 0, dp(8), new int[] { 0x00000000, 0xff000000 }, new float[] { 0, 1 }, Shader.TileMode.CLAMP);
        private final Paint bottomGradientPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Matrix bottomGradientMatrix = new Matrix();
        {
            topGradientPaint.setShader(topGradient);
            topGradientPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));
            bottomGradientPaint.setShader(bottomGradient);
            bottomGradientPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));
        }

        @Override
        protected void dispatchDraw(Canvas canvas) {
            final int y = getScrollY();

            canvas.saveLayerAlpha(0, y, getWidth(), y + getHeight(), 0xFF, Canvas.ALL_SAVE_FLAG);
            super.dispatchDraw(canvas);

            canvas.save();

            float alpha = topGradientAlpha.set(canScrollVertically(-1));
            topGradientMatrix.reset();
            topGradientMatrix.postTranslate(0, y);
            topGradient.setLocalMatrix(topGradientMatrix);
            topGradientPaint.setAlpha((int) (0xFF * alpha));
            canvas.drawRect(0, y, getWidth(), y + dp(8), topGradientPaint);

            alpha = bottomGradientAlpha.set(canScrollVertically(1));
            bottomGradientMatrix.reset();
            bottomGradientMatrix.postTranslate(0, y + getHeight() - dp(8));
            bottomGradient.setLocalMatrix(bottomGradientMatrix);
            bottomGradientPaint.setAlpha((int) (0xFF * alpha));
            canvas.drawRect(0, y + getHeight() - dp(8), getWidth(), y + getHeight(), bottomGradientPaint);

            canvas.restore();

            canvas.restore();
        }

        @Override
        public boolean dispatchTouchEvent(MotionEvent ev) {
//            if (!AndroidUtilities.findClickableView(this, ev.getX(), ev.getY())) {
//                return false;
//            }
            return super.dispatchTouchEvent(ev);
        }

        public void setText(CharSequence text) {
            ignoreTextChange = true;
            editText.setText(text);
            ignoreTextChange = false;
        }

        public void setOnSearchTextChange(Utilities.Callback<String> listener) {
            this.onSearchTextChange = listener;
        }

        private boolean ignoreScrollEvent;
        private int fieldY;
        public float containerHeight;
        public int resultContainerHeight;
        private int prevResultContainerHeight;

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

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(
                MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(dp(150), MeasureSpec.AT_MOST)
            );
        }

        public void setContainerHeight(float value) {
            containerHeight = value;
            if (spansContainer != null) {
                spansContainer.requestLayout();
            }
        }

        private Animator getContainerHeightAnimator(float newHeight) {
            ValueAnimator animator = ValueAnimator.ofFloat(this.containerHeight, newHeight);
            animator.addUpdateListener(anm -> setContainerHeight((float) anm.getAnimatedValue()));
            return animator;
        }

        private boolean scroll;
        public void scrollToBottom() {
            scroll = true;
        }

        public class SpansContainer extends ViewGroup {

            private AnimatorSet currentAnimation;
            private boolean animationStarted;
            private ArrayList<View> animAddingSpans = new ArrayList<>();
            private ArrayList<View> animRemovingSpans = new ArrayList<>();
            private ArrayList<Animator> animators = new ArrayList<>();
            private View addingSpan;
            private final ArrayList<View> removingSpans = new ArrayList<>();

            private final int padDp = 7;
            private final int padYDp = 4;
            private final int padXDp = 4;
            private final int heightDp = 28; // 32;

            public SpansContainer(Context context) {
                super(context);
            }

            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                int count = getChildCount();
                int width = MeasureSpec.getSize(widthMeasureSpec);
                int maxWidth = width - AndroidUtilities.dp(padDp * 2);
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
                    child.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(dp(heightDp), MeasureSpec.EXACTLY));
                    boolean isRemoving = removingSpans.contains(child);
                    if (!isRemoving && currentLineWidth + child.getMeasuredWidth() > maxWidth) {
                        y += child.getMeasuredHeight() + dp(padYDp);
                        currentLineWidth = 0;
                    }
                    if (allCurrentLineWidth + child.getMeasuredWidth() > maxWidth) {
                        allY += child.getMeasuredHeight() + AndroidUtilities.dp(padYDp);
                        allCurrentLineWidth = 0;
                    }
                    x = AndroidUtilities.dp(padDp) + currentLineWidth;
                    if (!animationStarted) {
                        if (isRemoving) {
                            child.setTranslationX(AndroidUtilities.dp(padDp) + allCurrentLineWidth);
                            child.setTranslationY(allY);
                        } else if (!removingSpans.isEmpty()) {
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
                    if (!isRemoving) {
                        currentLineWidth += child.getMeasuredWidth() + AndroidUtilities.dp(padXDp);
                    }
                    allCurrentLineWidth += child.getMeasuredWidth() + AndroidUtilities.dp(padXDp);
                }
                int minWidth;
                if (AndroidUtilities.isTablet()) {
                    minWidth = AndroidUtilities.dp(530 - padDp * 2 - padXDp * 2 - 57 * 2) / 3;
                } else {
                    minWidth = (Math.min(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y) - AndroidUtilities.dp(padDp * 2 + padXDp * 2 + 57 * 2)) / 3;
                }
                if (maxWidth - currentLineWidth < minWidth) {
                    currentLineWidth = 0;
                    y += AndroidUtilities.dp(heightDp + 8);
                }
                if (maxWidth - allCurrentLineWidth < minWidth) {
                    allY += AndroidUtilities.dp(heightDp + 8);
                }
                editText.measure(MeasureSpec.makeMeasureSpec(maxWidth - currentLineWidth, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(heightDp), MeasureSpec.EXACTLY));
                editText.setHintVisible(editText.getMeasuredWidth() > hintTextWidth, true);
                if (!animationStarted) {
                    int currentHeight = allY + AndroidUtilities.dp(heightDp + 10);
                    int fieldX = currentLineWidth + AndroidUtilities.dp(16);
                    fieldY = y;
                    if (currentAnimation != null) {
                        int resultHeight = y + AndroidUtilities.dp(heightDp + 10);
                        resultContainerHeight = resultHeight;
                        if (containerHeight != resultHeight) {
                            animators.add(getContainerHeightAnimator(resultHeight));
                        }
                        if (editText.getTranslationX() != fieldX) {
                            animators.add(ObjectAnimator.ofFloat(editText, View.TRANSLATION_X, fieldX));
                        }
                        if (editText.getTranslationY() != fieldY) {
                            animators.add(ObjectAnimator.ofFloat(editText, View.TRANSLATION_Y, fieldY));
                        }
                        editText.setAllowDrawCursor(false);
                        currentAnimation.playTogether(animators);
                        currentAnimation.setDuration(180);
                        currentAnimation.setInterpolator(new LinearInterpolator());
                        currentAnimation.start();
                        animationStarted = true;
                        if (updateHeight != null) {
                            updateHeight.run();
                        }
                    } else {
                        containerHeight = resultContainerHeight = currentHeight;
                        editText.setTranslationX(fieldX);
                        editText.setTranslationY(fieldY);
                        if (updateHeight != null) {
                            updateHeight.run();
                        }
                        if (scroll) {
                            post(() -> fullScroll(View.FOCUS_DOWN));
                            scroll = false;
                        }
                    }
                    prevResultContainerHeight = resultContainerHeight;
                } else if (currentAnimation != null) {
                    if (!ignoreScrollEvent && removingSpans.isEmpty()) {
                        editText.bringPointIntoView(editText.getSelectionStart());
                    }
                    if (scroll) {
                        fullScroll(View.FOCUS_DOWN);
                        scroll = false;
                    }
                }
                setMeasuredDimension(width, (int) containerHeight);
            }

            @Override
            protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
                int count = getChildCount();
                for (int a = 0; a < count; a++) {
                    View child = getChildAt(a);
                    child.layout(0, 0, child.getMeasuredWidth(), child.getMeasuredHeight());
                }
            }

            public void removeSpan(final GroupCreateSpan span) {
                ignoreScrollEvent = true;
                allSpans.remove(span);
                span.setOnClickListener(null);

                setupEndValues();
                animationStarted = false;
                currentAnimation = new AnimatorSet();
                currentAnimation.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animator) {
                        removeView(span);
                        removingSpans.clear();
                        currentAnimation = null;
                        animationStarted = false;
                        editText.setAllowDrawCursor(true);
                        if (updateHeight != null) {
                            updateHeight.run();
                        }
                        if (scroll) {
                            fullScroll(View.FOCUS_DOWN);
                            scroll = false;
                        }
                    }
                });
                removingSpans.clear();
                removingSpans.add(span);
                animAddingSpans.clear();
                animRemovingSpans.clear();
                animAddingSpans.add(span);
                animators.clear();
                animators.add(ObjectAnimator.ofFloat(span, View.SCALE_X, 1.0f, 0.01f));
                animators.add(ObjectAnimator.ofFloat(span, View.SCALE_Y, 1.0f, 0.01f));
                animators.add(ObjectAnimator.ofFloat(span, View.ALPHA, 1.0f, 0.0f));
                requestLayout();
            }

            public void updateSpans(ArrayList<GroupCreateSpan> toDelete, ArrayList<GroupCreateSpan> toAdd, boolean animated) {
                ignoreScrollEvent = true;

                allSpans.removeAll(toDelete);
                allSpans.addAll(toAdd);

                removingSpans.clear();
                removingSpans.addAll(toDelete);

                for (int i = 0; i < toDelete.size(); ++i) {
                    toDelete.get(i).setOnClickListener(null);
                }

                setupEndValues();
                if (animated) {
                    animationStarted = false;
                    currentAnimation = new AnimatorSet();
                    currentAnimation.addListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animator) {
                            for (int i = 0; i < toDelete.size(); ++i) {
                                removeView(toDelete.get(i));
                            }
                            addingSpan = null;
                            removingSpans.clear();
                            currentAnimation = null;
                            animationStarted = false;
                            editText.setAllowDrawCursor(true);
                            if (updateHeight != null) {
                                updateHeight.run();
                            }
                            if (scroll) {
                                fullScroll(View.FOCUS_DOWN);
                                scroll = false;
                            }
                        }
                    });
                    animators.clear();
                    animAddingSpans.clear();
                    animRemovingSpans.clear();
                    for (int i = 0; i < toDelete.size(); ++i) {
                        GroupCreateSpan span = toDelete.get(i);
                        animRemovingSpans.add(span);
                        animators.add(ObjectAnimator.ofFloat(span, View.SCALE_X, 1.0f, 0.01f));
                        animators.add(ObjectAnimator.ofFloat(span, View.SCALE_Y, 1.0f, 0.01f));
                        animators.add(ObjectAnimator.ofFloat(span, View.ALPHA, 1.0f, 0.0f));
                    }
                    for (int i = 0; i < toAdd.size(); ++i) {
                        GroupCreateSpan addingSpan = toAdd.get(i);
                        animAddingSpans.add(addingSpan);
                        animators.add(ObjectAnimator.ofFloat(addingSpan, View.SCALE_X, 0.01f, 1.0f));
                        animators.add(ObjectAnimator.ofFloat(addingSpan, View.SCALE_Y, 0.01f, 1.0f));
                        animators.add(ObjectAnimator.ofFloat(addingSpan, View.ALPHA, 0.0f, 1.0f));
                    }
                } else {
                    for (int i = 0; i < toDelete.size(); ++i) {
                        removeView(toDelete.get(i));
                    }
                    addingSpan = null;
                    removingSpans.clear();
                    currentAnimation = null;
                    animationStarted = false;
                    editText.setAllowDrawCursor(true);
                }
                for (int i = 0; i < toAdd.size(); ++i) {
                    addView(toAdd.get(i));
                }
                requestLayout();
            }

            public void removeAllSpans(boolean animated) {
                ignoreScrollEvent = true;

                ArrayList<GroupCreateSpan> spans = new ArrayList<>(allSpans);
                removingSpans.clear();
                removingSpans.addAll(allSpans);
                allSpans.clear();

                for (int i = 0; i < spans.size(); ++i) {
                    spans.get(i).setOnClickListener(null);
                }

                setupEndValues();
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
                            editText.setAllowDrawCursor(true);
                            if (updateHeight != null) {
                                updateHeight.run();
                            }
                            if (scroll) {
                                fullScroll(View.FOCUS_DOWN);
                                scroll = false;
                            }
                        }
                    });
                    animators.clear();
                    animAddingSpans.clear();
                    animRemovingSpans.clear();
                    for (int i = 0; i < spans.size(); ++i) {
                        GroupCreateSpan span = spans.get(i);
                        animAddingSpans.add(span);
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
                    editText.setAllowDrawCursor(true);
                }
                requestLayout();
            }

            private void setupEndValues() {
                if (currentAnimation != null) {
                    currentAnimation.cancel();
                }
                for (int i = 0; i < animAddingSpans.size(); ++i) {
                    animAddingSpans.get(i).setScaleX(1f);
                    animAddingSpans.get(i).setScaleY(1f);
                    animAddingSpans.get(i).setAlpha(1f);
                }
                for (int i = 0; i < animRemovingSpans.size(); ++i) {
                    animRemovingSpans.get(i).setScaleX(0f);
                    animRemovingSpans.get(i).setScaleY(0f);
                    animRemovingSpans.get(i).setAlpha(0f);
                }
                animAddingSpans.clear();
                animRemovingSpans.clear();
            }
        }
    }

    public static class StoryPrivacy {
        public final int type;

        public final ArrayList<TLRPC.InputPrivacyRule> rules = new ArrayList<>();
        public final ArrayList<Long> selectedUserIds = new ArrayList<>();
        public final HashMap<Long, ArrayList<Long>> selectedUserIdsByGroup = new HashMap();
        public final ArrayList<TLRPC.InputUser> selectedInputUsers = new ArrayList<>();
        public final ArrayList<Long> sendToUsers = new ArrayList<>();

        public StoryPrivacy(int currentAccount, ArrayList<TLRPC.PrivacyRule> rules) {
            TLRPC.TL_privacyValueAllowUsers allowUsers;
            if (containsRule(rules, TLRPC.TL_privacyValueAllowAll.class) != null) {
                type = TYPE_EVERYONE;
                this.rules.add(new TLRPC.TL_inputPrivacyValueAllowAll());

                TLRPC.TL_privacyValueDisallowUsers disallowUsers = containsRule(rules, TLRPC.TL_privacyValueDisallowUsers.class);
                if (disallowUsers != null) {
                    final TLRPC.TL_inputPrivacyValueDisallowUsers rule = new TLRPC.TL_inputPrivacyValueDisallowUsers();
                    final MessagesController messagesController = MessagesController.getInstance(currentAccount);
                    for (int i = 0; i < disallowUsers.users.size(); ++i) {
                        long userId = disallowUsers.users.get(i);
                        TLRPC.InputUser inputUser = messagesController.getInputUser(userId);
                        if (!(inputUser instanceof TLRPC.TL_inputUserEmpty)) {
                            rule.users.add(inputUser);
                            selectedUserIds.add(userId);
                            selectedInputUsers.add(inputUser);
                        }
                    }
                    this.rules.add(rule);
                }
            } else if (containsRule(rules, TLRPC.TL_privacyValueAllowCloseFriends.class) != null) {
                type = TYPE_CLOSE_FRIENDS;
                this.rules.add(new TLRPC.TL_inputPrivacyValueAllowCloseFriends());
            } else if ((allowUsers = containsRule(rules, TLRPC.TL_privacyValueAllowUsers.class)) != null) {
                type = TYPE_SELECTED_CONTACTS;

                final TLRPC.TL_inputPrivacyValueAllowUsers rule = new TLRPC.TL_inputPrivacyValueAllowUsers();
                final MessagesController messagesController = MessagesController.getInstance(currentAccount);
                for (int i = 0; i < allowUsers.users.size(); ++i) {
                    long userId = allowUsers.users.get(i);
                    TLRPC.InputUser inputUser = messagesController.getInputUser(userId);
                    if (inputUser != null && !(inputUser instanceof TLRPC.TL_inputUserEmpty)) {
                        rule.users.add(inputUser);
                        selectedUserIds.add(userId);
                        selectedInputUsers.add(inputUser);
                    }
                }
                this.rules.add(rule);
            } else if (containsRule(rules, TLRPC.TL_privacyValueAllowContacts.class) != null) {
                type = TYPE_CONTACTS;
                this.rules.add(new TLRPC.TL_inputPrivacyValueAllowContacts());

                TLRPC.TL_privacyValueDisallowUsers disallowUsers = containsRule(rules, TLRPC.TL_privacyValueDisallowUsers.class);
                if (disallowUsers != null) {
                    final TLRPC.TL_inputPrivacyValueDisallowUsers rule = new TLRPC.TL_inputPrivacyValueDisallowUsers();
                    final MessagesController messagesController = MessagesController.getInstance(currentAccount);
                    for (int i = 0; i < disallowUsers.users.size(); ++i) {
                        long userId = disallowUsers.users.get(i);
                        TLRPC.InputUser inputUser = messagesController.getInputUser(userId);
                        if (!(inputUser instanceof TLRPC.TL_inputUserEmpty)) {
                            rule.users.add(inputUser);
                            selectedUserIds.add(userId);
                            selectedInputUsers.add(inputUser);
                        }
                    }
                    this.rules.add(rule);
                }
            } else {
                type = TYPE_EVERYONE;
            }
        }

        public StoryPrivacy(ArrayList<TLRPC.InputPrivacyRule> rules) {
            this.rules.addAll(rules);
            TLRPC.TL_inputPrivacyValueAllowUsers allowUsers;
            if (containsInputRule(rules, TLRPC.TL_inputPrivacyValueAllowAll.class) != null) {
                type = TYPE_EVERYONE;
                TLRPC.TL_inputPrivacyValueDisallowUsers disallowUsers = containsInputRule(rules, TLRPC.TL_inputPrivacyValueDisallowUsers.class);
                if (disallowUsers != null) {
                    for (int i = 0; i < disallowUsers.users.size(); ++i) {
                        TLRPC.InputUser inputUser = disallowUsers.users.get(i);
                        if (inputUser != null) {
                            selectedUserIds.add(inputUser.user_id);
                            selectedInputUsers.add(inputUser);
                        }
                    }
                }
            } else if (containsInputRule(rules, TLRPC.TL_inputPrivacyValueAllowCloseFriends.class) != null) {
                type = TYPE_CLOSE_FRIENDS;
            } else if ((allowUsers = containsInputRule(rules, TLRPC.TL_inputPrivacyValueAllowUsers.class)) != null) {
                type = TYPE_SELECTED_CONTACTS;
                for (int i = 0; i < allowUsers.users.size(); ++i) {
                    TLRPC.InputUser inputUser = allowUsers.users.get(i);
                    if (inputUser != null) {
                        selectedUserIds.add(inputUser.user_id);
                        selectedInputUsers.add(inputUser);
                    }
                }
            } else if (containsInputRule(rules, TLRPC.TL_inputPrivacyValueAllowContacts.class) != null) {
                type = TYPE_CONTACTS;
                TLRPC.TL_inputPrivacyValueDisallowUsers disallowUsers = containsInputRule(rules, TLRPC.TL_inputPrivacyValueDisallowUsers.class);
                if (disallowUsers != null) {
                    for (int i = 0; i < disallowUsers.users.size(); ++i) {
                        TLRPC.InputUser inputUser = disallowUsers.users.get(i);
                        if (inputUser != null) {
                            selectedUserIds.add(inputUser.user_id);
                            selectedInputUsers.add(inputUser);
                        }
                    }
                }
            } else {
                type = TYPE_EVERYONE;
            }
        }

        private <T> T containsRule(ArrayList<TLRPC.PrivacyRule> rules, Class<T> clazz) {
            for (int i = 0; i < rules.size(); ++i) {
                TLRPC.PrivacyRule rule = rules.get(i);
                if (clazz.isInstance(rule)) {
                    return (T) rule;
                }
            }
            return null;
        }

        private <T> T containsInputRule(ArrayList<TLRPC.InputPrivacyRule> rules, Class<T> clazz) {
            for (int i = 0; i < rules.size(); ++i) {
                TLRPC.InputPrivacyRule rule = rules.get(i);
                if (clazz.isInstance(rule)) {
                    return (T) rule;
                }
            }
            return null;
        }

        public StoryPrivacy() {
            this.type = TYPE_EVERYONE;
            this.rules.add(new TLRPC.TL_inputPrivacyValueAllowAll());
        }

        public StoryPrivacy(int type, int currentAccount, ArrayList<Long> userIds) {
            this.type = type;
            if (type == TYPE_EVERYONE) {
                this.rules.add(new TLRPC.TL_inputPrivacyValueAllowAll());
                if (currentAccount >= 0 && userIds != null && !userIds.isEmpty()) {
                    final TLRPC.TL_inputPrivacyValueDisallowUsers rule = new TLRPC.TL_inputPrivacyValueDisallowUsers();
                    for (int i = 0; i < userIds.size(); ++i) {
                        long userId = userIds.get(i);
                        selectedUserIds.add(userId);
                        TLRPC.InputUser user = MessagesController.getInstance(currentAccount).getInputUser(userId);
                        if (user != null && !(user instanceof TLRPC.TL_inputUserEmpty)) {
                            rule.users.add(user);
                            selectedInputUsers.add(user);
                        }
                    }
                    this.rules.add(rule);
                }
            } else if (type == TYPE_CLOSE_FRIENDS) {
                this.rules.add(new TLRPC.TL_inputPrivacyValueAllowCloseFriends());
            } else if (type == TYPE_CONTACTS) {
                this.rules.add(new TLRPC.TL_inputPrivacyValueAllowContacts());
                if (currentAccount >= 0 && userIds != null && !userIds.isEmpty()) {
                    final TLRPC.TL_inputPrivacyValueDisallowUsers rule = new TLRPC.TL_inputPrivacyValueDisallowUsers();
                    for (int i = 0; i < userIds.size(); ++i) {
                        long userId = userIds.get(i);
                        selectedUserIds.add(userId);
                        TLRPC.InputUser user = MessagesController.getInstance(currentAccount).getInputUser(userId);
                        if (user != null && !(user instanceof TLRPC.TL_inputUserEmpty)) {
                            rule.users.add(user);
                            selectedInputUsers.add(user);
                        }
                    }
                    this.rules.add(rule);
                }
            } else if (type == TYPE_SELECTED_CONTACTS) {
                if (currentAccount >= 0 && userIds != null && !userIds.isEmpty()) {
                    final TLRPC.TL_inputPrivacyValueAllowUsers rule = new TLRPC.TL_inputPrivacyValueAllowUsers();
                    for (int i = 0; i < userIds.size(); ++i) {
                        long userId = userIds.get(i);
                        selectedUserIds.add(userId);
                        TLRPC.InputUser user = MessagesController.getInstance(currentAccount).getInputUser(userId);
                        if (user != null && !(user instanceof TLRPC.TL_inputUserEmpty)) {
                            rule.users.add(user);
                            selectedInputUsers.add(user);
                        }
                    }
                    this.rules.add(rule);
                }
            } else if (type == TYPE_AS_MESSAGE) {
                if (userIds != null) {
                    this.sendToUsers.addAll(userIds);
                }
            }
        }

        public StoryPrivacy(int type, ArrayList<TLRPC.InputUser> inputUserIds, int a) {
            this.type = type;
            if (type == TYPE_EVERYONE) {
                this.rules.add(new TLRPC.TL_inputPrivacyValueAllowAll());
                if (inputUserIds != null && !inputUserIds.isEmpty()) {
                    final TLRPC.TL_inputPrivacyValueDisallowUsers rule = new TLRPC.TL_inputPrivacyValueDisallowUsers();
                    for (int i = 0; i < inputUserIds.size(); ++i) {
                        TLRPC.InputUser user = inputUserIds.get(i);
                        if (user != null) {
                            rule.users.add(user);
                            selectedUserIds.add(user.user_id);
                            selectedInputUsers.add(user);
                        }
                    }
                    this.rules.add(rule);
                }
            } else if (type == TYPE_CLOSE_FRIENDS) {
                this.rules.add(new TLRPC.TL_inputPrivacyValueAllowCloseFriends());
            } else if (type == TYPE_CONTACTS) {
                this.rules.add(new TLRPC.TL_inputPrivacyValueAllowContacts());
                if (inputUserIds != null && !inputUserIds.isEmpty()) {
                    final TLRPC.TL_inputPrivacyValueDisallowUsers rule = new TLRPC.TL_inputPrivacyValueDisallowUsers();
                    for (int i = 0; i < inputUserIds.size(); ++i) {
                        TLRPC.InputUser user = inputUserIds.get(i);
                        if (user != null) {
                            rule.users.add(user);
                            selectedUserIds.add(user.user_id);
                            selectedInputUsers.add(user);
                        }
                    }
                    this.rules.add(rule);
                }
            } else if (type == TYPE_SELECTED_CONTACTS) {
                if (inputUserIds != null && !inputUserIds.isEmpty()) {
                    final TLRPC.TL_inputPrivacyValueAllowUsers rule = new TLRPC.TL_inputPrivacyValueAllowUsers();
                    for (int i = 0; i < inputUserIds.size(); ++i) {
                        TLRPC.InputUser user = inputUserIds.get(i);
                        if (user != null) {
                            rule.users.add(user);
                            selectedUserIds.add(user.user_id);
                            selectedInputUsers.add(user);
                        }
                    }
                    this.rules.add(rule);
                }
            } else if (type == TYPE_AS_MESSAGE) {
                if (inputUserIds != null) {
                    for (int i = 0; i < inputUserIds.size(); ++i) {
                        TLRPC.InputUser user = inputUserIds.get(i);
                        if (user != null) {
                            this.sendToUsers.add(user.user_id);
                        }
                    }
                }
            }
        }

        public boolean isShare() {
            return type == TYPE_AS_MESSAGE;
        }

        public boolean isNone() {
            return sendToUsers.isEmpty() && rules.isEmpty();
        }

        public boolean isCloseFriends() {
            return type == TYPE_CLOSE_FRIENDS;
        }

        @NonNull
        @Override
        public String toString() {
            if (!sendToUsers.isEmpty()) {
                return LocaleController.formatPluralString("StoryPrivacyRecipients", sendToUsers.size());
            }
            if (rules.isEmpty()) {
                return LocaleController.getString("StoryPrivacyNone", R.string.StoryPrivacyNone);
            }
            TLRPC.InputPrivacyRule rule1 = rules.get(0);
            if (type == TYPE_EVERYONE) {
                TLRPC.InputPrivacyRule rule2 = rules.size() >= 2 ? rules.get(1) : null;
                if (rule2 instanceof TLRPC.TL_inputPrivacyValueDisallowUsers) {
                    final int usersCount = ((TLRPC.TL_inputPrivacyValueDisallowUsers) rule2).users.size();
                    if (usersCount > 0) {
                        return LocaleController.formatPluralString("StoryPrivacyEveryoneExclude", usersCount);
                    }
                }
                return LocaleController.getString("StoryPrivacyEveryone", R.string.StoryPrivacyEveryone);
            } else if (type == TYPE_CLOSE_FRIENDS) {
                return LocaleController.getString("StoryPrivacyCloseFriends", R.string.StoryPrivacyCloseFriends);
            } else if (type == TYPE_SELECTED_CONTACTS && rule1 instanceof TLRPC.TL_inputPrivacyValueAllowUsers) {
                final int usersCount = ((TLRPC.TL_inputPrivacyValueAllowUsers) rule1).users.size();
                return LocaleController.formatPluralString("StoryPrivacyContacts", usersCount);
            } else if (type == TYPE_CONTACTS) {
                TLRPC.InputPrivacyRule rule2 = rules.size() >= 2 ? rules.get(1) : null;
                if (rule2 instanceof TLRPC.TL_inputPrivacyValueDisallowUsers) {
                    final int usersCount = ((TLRPC.TL_inputPrivacyValueDisallowUsers) rule2).users.size();
                    if (usersCount > 0) {
                        return LocaleController.formatPluralString("StoryPrivacyContactsExclude", usersCount);
                    } else {
                        return LocaleController.getString("StoryPrivacyAllContacts", R.string.StoryPrivacyAllContacts);
                    }
                } else {
                    return LocaleController.getString("StoryPrivacyAllContacts", R.string.StoryPrivacyAllContacts);
                }
            } else if (type == 0) {
                if (rule1 instanceof TLRPC.TL_inputPrivacyValueAllowUsers) {
                    final int usersCount = ((TLRPC.TL_inputPrivacyValueAllowUsers) rule1).users.size();
                    if (usersCount <= 0) {
                        return LocaleController.getString("StoryPrivacyNone", R.string.StoryPrivacyNone);
                    } else {
                        return LocaleController.formatPluralString("StoryPrivacyContacts", usersCount);
                    }
                } else {
                    return LocaleController.getString("StoryPrivacyNone", R.string.StoryPrivacyNone);
                }
            }
            return LocaleController.getString("StoryPrivacyNone", R.string.StoryPrivacyNone);
        }

        public ArrayList<TLRPC.PrivacyRule> toValue() {
            ArrayList<TLRPC.PrivacyRule> result = new ArrayList<>();
            for (int i = 0; i < rules.size(); ++i) {
                TLRPC.InputPrivacyRule inputPrivacyRule = rules.get(i);
                if (inputPrivacyRule instanceof TLRPC.TL_inputPrivacyValueAllowAll) {
                    result.add(new TLRPC.TL_privacyValueAllowAll());
                } else if (inputPrivacyRule instanceof TLRPC.TL_inputPrivacyValueAllowCloseFriends) {
                    result.add(new TLRPC.TL_privacyValueAllowCloseFriends());
                } else if (inputPrivacyRule instanceof TLRPC.TL_inputPrivacyValueAllowContacts) {
                    result.add(new TLRPC.TL_privacyValueAllowContacts());
                } else if (inputPrivacyRule instanceof TLRPC.TL_inputPrivacyValueDisallowUsers) {
                    TLRPC.TL_inputPrivacyValueDisallowUsers inputRule = (TLRPC.TL_inputPrivacyValueDisallowUsers) inputPrivacyRule;
                    TLRPC.TL_privacyValueDisallowUsers rule = new TLRPC.TL_privacyValueDisallowUsers();
                    for (int j = 0; j < inputRule.users.size(); ++j) {
                        rule.users.add(inputRule.users.get(j).user_id);
                    }
                    result.add(rule);
                } else if (inputPrivacyRule instanceof TLRPC.TL_inputPrivacyValueAllowUsers) {
                    TLRPC.TL_inputPrivacyValueAllowUsers inputRule = (TLRPC.TL_inputPrivacyValueAllowUsers) inputPrivacyRule;
                    TLRPC.TL_privacyValueAllowUsers rule = new TLRPC.TL_privacyValueAllowUsers();
                    for (int j = 0; j < inputRule.users.size(); ++j) {
                        rule.users.add(inputRule.users.get(j).user_id);
                    }
                    result.add(rule);
                }
            }
            return result;
        }

        public static ArrayList<TLRPC.InputPrivacyRule> toInput(int currentAccount, ArrayList<TLRPC.PrivacyRule> rules) {
            MessagesController messagesController = MessagesController.getInstance(currentAccount);
            final ArrayList<TLRPC.InputPrivacyRule> arr = new ArrayList<>();
            for (int i = 0; i < rules.size(); ++i) {
                TLRPC.PrivacyRule rule = rules.get(i);
                if (rule == null) {
                    continue;
                }
                if (rule instanceof TLRPC.TL_privacyValueAllowAll) {
                    arr.add(new TLRPC.TL_inputPrivacyValueAllowAll());
                } else if (rule instanceof TLRPC.TL_privacyValueAllowCloseFriends) {
                    arr.add(new TLRPC.TL_inputPrivacyValueAllowCloseFriends());
                } else if (rule instanceof TLRPC.TL_privacyValueAllowContacts) {
                    arr.add(new TLRPC.TL_inputPrivacyValueAllowContacts());
                } else if (rule instanceof TLRPC.TL_privacyValueDisallowUsers) {
                    TLRPC.TL_privacyValueDisallowUsers rule2 = (TLRPC.TL_privacyValueDisallowUsers) rule;
                    TLRPC.TL_inputPrivacyValueDisallowUsers inputRule = new TLRPC.TL_inputPrivacyValueDisallowUsers();
                    for (int j = 0; j < rule2.users.size(); ++j) {
                        TLRPC.InputUser user = messagesController.getInputUser(rule2.users.get(j));
                        if (!(user instanceof TLRPC.TL_inputUserEmpty)) {
                            inputRule.users.add(user);
                        }
                    }
                    arr.add(inputRule);
                } else if (rule instanceof TLRPC.TL_privacyValueAllowUsers) {
                    TLRPC.TL_privacyValueAllowUsers rule2 = (TLRPC.TL_privacyValueAllowUsers) rule;
                    TLRPC.TL_inputPrivacyValueAllowUsers inputRule = new TLRPC.TL_inputPrivacyValueAllowUsers();
                    for (int j = 0; j < rule2.users.size(); ++j) {
                        TLRPC.InputUser user = messagesController.getInputUser(rule2.users.get(j));
                        if (!(user instanceof TLRPC.TL_inputUserEmpty)) {
                            inputRule.users.add(user);
                        }
                    }
                    arr.add(inputRule);
                }
            }
            return arr;
        }

        public static ArrayList<TLRPC.PrivacyRule> toOutput(ArrayList<TLRPC.InputPrivacyRule> rules) {
            final ArrayList<TLRPC.PrivacyRule> arr = new ArrayList<>();
            for (int i = 0; i < rules.size(); ++i) {
                TLRPC.InputPrivacyRule rule = rules.get(i);
                if (rule == null) {
                    continue;
                }
                if (rule instanceof TLRPC.TL_inputPrivacyValueAllowAll) {
                    arr.add(new TLRPC.TL_privacyValueAllowAll());
                } else if (rule instanceof TLRPC.TL_inputPrivacyValueAllowCloseFriends) {
                    arr.add(new TLRPC.TL_privacyValueAllowCloseFriends());
                } else if (rule instanceof TLRPC.TL_inputPrivacyValueAllowContacts) {
                    arr.add(new TLRPC.TL_privacyValueAllowContacts());
                } else if (rule instanceof TLRPC.TL_inputPrivacyValueDisallowUsers) {
                    TLRPC.TL_inputPrivacyValueDisallowUsers rule2 = (TLRPC.TL_inputPrivacyValueDisallowUsers) rule;
                    TLRPC.TL_privacyValueDisallowUsers outputRule = new TLRPC.TL_privacyValueDisallowUsers();
                    for (int j = 0; j < rule2.users.size(); ++j) {
                        outputRule.users.add(rule2.users.get(j).user_id);
                    }
                    arr.add(outputRule);
                } else if (rule instanceof TLRPC.TL_inputPrivacyValueAllowUsers) {
                    TLRPC.TL_inputPrivacyValueAllowUsers rule2 = (TLRPC.TL_inputPrivacyValueAllowUsers) rule;
                    TLRPC.TL_privacyValueAllowUsers outputRule = new TLRPC.TL_privacyValueAllowUsers();
                    for (int j = 0; j < rule2.users.size(); ++j) {
                        outputRule.users.add(rule2.users.get(j).user_id);
                    }
                    arr.add(outputRule);
                }
            }
            return arr;
        }

        public boolean containsUser(TLRPC.User user) {
            if (user == null) {
                return false;
            }
            if (type == TYPE_EVERYONE) {
                return !selectedUserIds.contains(user.id);
            } else if (type == TYPE_CONTACTS) {
                return !selectedUserIds.contains(user.id) && user.contact;
            } else if (type == TYPE_CLOSE_FRIENDS) {
                return user.close_friend;
            } else if (type == TYPE_SELECTED_CONTACTS) {
                if (selectedUserIds.contains(user.id)) {
                    return true;
                }
                for (ArrayList<Long> userIds : selectedUserIdsByGroup.values()) {
                    if (userIds.contains(user.id)) {
                        return true;
                    }
                }
            }
            return false;
        }
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (viewPager == null) {
            return;
        }
        if (id == NotificationCenter.contactsDidLoad) {
            View[] views = viewPager.getViewPages();
            if (views[0] instanceof Page) {
                ((Page) views[0]).updateItems(true);
            }
            if (views[1] instanceof Page) {
                ((Page) views[1]).updateItems(true);
            }
        } else if (id == NotificationCenter.storiesBlocklistUpdate) {
            View[] views = viewPager.getViewPages();
            for (int i = 0; i < views.length; ++i) {
                if (views[i] instanceof Page) {
                    Page page = (Page) views[i];
                    if (page.pageType == PAGE_TYPE_BLOCKLIST) {
                        page.applyBlocklist(true);
                    } else if (page.pageType == PAGE_TYPE_SHARE) {
                        page.updateItems(true);
                    }
                }
            }
        } else if (id == NotificationCenter.storiesSendAsUpdate) {
            View[] views = viewPager.getViewPages();
            for (int i = 0; i < views.length; ++i) {
                if (views[i] instanceof Page) {
                    Page page = (Page) views[i];
                    if (page.pageType == PAGE_TYPE_SHARE) {
                        page.updateItems(true);
                    }
                }
            }
        }
    }

    private void pullSaved() {
        String selectedContactsString = MessagesController.getInstance(currentAccount).getMainSettings().getString("story_prv_contacts", null);
        if (selectedContactsString != null) {
            String[] parts = selectedContactsString.split(",");
            selectedContacts.clear();
            for (int i = 0; i < parts.length; ++i) {
                try {
                    selectedContacts.add(Long.parseLong(parts[i]));
                } catch (Exception ignore) {}
            }
        }

        String selectedContactsGroupsString = MessagesController.getInstance(currentAccount).getMainSettings().getString("story_prv_grpcontacts", null);
        if (selectedContactsGroupsString != null) {
            String[] parts = selectedContactsGroupsString.split(";");
            selectedContactsByGroup.clear();
            for (int i = 0; i < parts.length; ++i) {
                String[] parts2 = parts[i].split(",");
                if (parts2.length <= 0) {
                    continue;
                }
                long id;
                try {
                    id = Long.parseLong(parts2[0]);
                } catch (Exception ignore) {
                    continue;
                }
                ArrayList<Long> userIds = new ArrayList<>();
                for (int j = 1; j < parts2.length; ++j) {
                    userIds.add(Long.parseLong(parts2[j]));
                }
                selectedContactsByGroup.put(id, userIds);
            }
        }

        String excludedEveryoneString = MessagesController.getInstance(currentAccount).getMainSettings().getString("story_prv_everyoneexcept", null);
        if (excludedEveryoneString != null) {
            String[] parts = excludedEveryoneString.split(",");
            excludedEveryone.clear();
            for (int i = 0; i < parts.length; ++i) {
                try {
                    excludedEveryone.add(Long.parseLong(parts[i]));
                } catch (Exception ignore) {}
            }
        }

        String excludedEveryoneGroupsString = MessagesController.getInstance(currentAccount).getMainSettings().getString("story_prv_grpeveryoneexcept", null);
        if (excludedEveryoneGroupsString != null) {
            String[] parts = excludedEveryoneGroupsString.split(";");
            excludedEveryoneByGroup.clear();
            for (int i = 0; i < parts.length; ++i) {
                String[] parts2 = parts[i].split(",");
                if (parts2.length <= 0) {
                    continue;
                }
                long id;
                try {
                    id = Long.parseLong(parts2[0]);
                } catch (Exception ignore) {
                    continue;
                }
                ArrayList<Long> userIds = new ArrayList<>();
                for (int j = 1; j < parts2.length; ++j) {
                    userIds.add(Long.parseLong(parts2[j]));
                }
                excludedEveryoneByGroup.put(id, userIds);
            }
        }

        String excludedContactsString = MessagesController.getInstance(currentAccount).getMainSettings().getString("story_prv_excluded", null);
        if (excludedContactsString != null) {
            String[] parts = excludedContactsString.split(",");
            excludedContacts.clear();
            for (int i = 0; i < parts.length; ++i) {
                try {
                    excludedContacts.add(Long.parseLong(parts[i]));
                } catch (Exception ignore) {}
            }
        }

        selectedContactsCount = mergeUsers(selectedContacts, selectedContactsByGroup).size();
        excludedEveryoneCount = mergeUsers(excludedEveryone, excludedEveryoneByGroup).size();

        allowScreenshots = !MessagesController.getInstance(currentAccount).getMainSettings().getBoolean("story_noforwards", false);
        keepOnMyPage = MessagesController.getInstance(currentAccount).getMainSettings().getBoolean("story_keep", true);
//
//        long peerId = MessagesController.getInstance(currentAccount).getMainSettings().getLong("story_sendas", 0L);
//        if (peerId != 0) {
//            selectedPeer = new TLRPC.TL_inputPeerChannel();
//            selectedPeer.channel_id = peerId;
//        }
    }

    private void save() {
        StringBuilder stringBuilder = new StringBuilder();
        for (Map.Entry<Long, ArrayList<Long>> entry : selectedContactsByGroup.entrySet()) {
            if (stringBuilder.length() > 0) {
                stringBuilder.append(";");
            }
            stringBuilder.append(entry.getKey()).append(",").append(TextUtils.join(",", entry.getValue()));
        }
        StringBuilder stringBuilder2 = new StringBuilder();
        for (Map.Entry<Long, ArrayList<Long>> entry : excludedEveryoneByGroup.entrySet()) {
            if (stringBuilder2.length() > 0) {
                stringBuilder2.append(";");
            }
            stringBuilder2.append(entry.getKey()).append(",").append(TextUtils.join(",", entry.getValue()));
        }
        MessagesController.getInstance(currentAccount).getMainSettings().edit()
            .putString("story_prv_everyoneexcept", TextUtils.join(",", excludedEveryone))
            .putString("story_prv_grpeveryoneexcept", stringBuilder2.toString())
            .putString("story_prv_contacts", TextUtils.join(",", selectedContacts))
            .putString("story_prv_grpcontacts", stringBuilder.toString())
            .putString("story_prv_excluded", TextUtils.join(",", excludedContacts))
            .putBoolean("story_noforwards", !allowScreenshots)
            .putBoolean("story_keep", keepOnMyPage)
//            .putLong("story_sendas", selectedPeer instanceof TLRPC.TL_inputPeerChannel ? selectedPeer.channel_id : 0)
            .apply();
    }

    public StoryPrivacyBottomSheet setCanChangePeer(boolean canChangePeer) {
        this.canChangePeer = canChangePeer;
        return this;
    }

    public static class ChoosePeerSheet extends BottomSheet {

        private final int currentAccount;
        private final List<TLRPC.InputPeer> peers;
        private final TLRPC.InputPeer selectedPeer;
        private final Utilities.Callback<TLRPC.InputPeer> onPeerSelected;

        private final RecyclerListView listView;
        private final TextView headerView;

        public ChoosePeerSheet(Context context, int currentAccount, TLRPC.InputPeer selected, Utilities.Callback<TLRPC.InputPeer> onPeerSelected, Theme.ResourcesProvider resourcesProvider) {
            super(context, false, resourcesProvider);
            fixNavigationBar();

            this.currentAccount = currentAccount;
            this.peers = MessagesController.getInstance(currentAccount).getStoriesController().sendAs;
            this.selectedPeer = selected;
            this.onPeerSelected = onPeerSelected;

            containerView = new FrameLayout(context) {
                private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                private final AnimatedFloat statusBarT = new AnimatedFloat(this, 0, 350, CubicBezierInterpolator.EASE_OUT_QUINT);

                @Override
                protected void dispatchDraw(Canvas canvas) {
                    backgroundPaint.setColor(Theme.getColor(Theme.key_dialogBackground, resourcesProvider));
                    float top = Math.max(0, top());
                    top = AndroidUtilities.lerp(top, 0, statusBarT.set(top < AndroidUtilities.statusBarHeight));
                    AndroidUtilities.rectTmp.set(backgroundPaddingLeft, top, getWidth() - backgroundPaddingLeft, getHeight() + dp(14));
                    final float r = dp(14) * (1f - statusBarT.get());
                    canvas.drawRoundRect(AndroidUtilities.rectTmp, r, r, backgroundPaint);
                    headerView.setTranslationY(Math.max(AndroidUtilities.statusBarHeight + dp(8), dp(14) + top));

                    canvas.save();
                    canvas.clipRect(backgroundPaddingLeft, AndroidUtilities.statusBarHeight + dp(14), getWidth() - backgroundPaddingLeft, getHeight());
                    super.dispatchDraw(canvas);
                    canvas.restore();
                }

                @Override
                public boolean dispatchTouchEvent(MotionEvent ev) {
                    if (ev.getY() < top()) {
                        dismiss();
                        return true;
                    }
                    return super.dispatchTouchEvent(ev);
                }
            };

            listView = new RecyclerListView(context, resourcesProvider);
            listView.setPadding(backgroundPaddingLeft, 0, backgroundPaddingLeft, 0);
            listView.setAdapter(new Adapter());
            listView.setLayoutManager(new LinearLayoutManager(context));
            containerView.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));
            listView.setOnItemClickListener((view, pos) -> {
                if (pos <= 1) {
                    return;
                }
                TLRPC.InputPeer peer = peers.get(pos - 2);
                if (peer.channel_id == 0 && peer.chat_id == 0) {
                    onPeerSelected.run(peer);
                    dismiss();
                    return;
                }
                AlertDialog progressDialog = new AlertDialog(getContext(), AlertDialog.ALERT_TYPE_SPINNER, resourcesProvider);
                progressDialog.showDelayed(200);
                MessagesController.getInstance(currentAccount).getStoriesController().canSendStoryFor(DialogObject.getPeerDialogId(peer), aBoolean -> {
                    progressDialog.dismiss();
                    if (aBoolean && onPeerSelected != null) {
                        onPeerSelected.run(peer);
                    }
                }, true, resourcesProvider);
                dismiss();

            });
            listView.setOnScrollListener(new RecyclerView.OnScrollListener() {
                @Override
                public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                    containerView.invalidate();
                }
            });

            headerView = new TextView(getContext());
            headerView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
            headerView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
            headerView.setPadding(backgroundPaddingLeft + dp(22), dp(2), backgroundPaddingLeft + dp(22), dp(14));
            headerView.setBackgroundColor(Theme.getColor(Theme.key_dialogBackground, resourcesProvider));
            headerView.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM));
            headerView.setText(LocaleController.getString(R.string.StoryPrivacyPublishAs));
            containerView.addView(headerView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        }

        private float top() {
            float top = containerView.getMeasuredHeight();
            for (int i = 0; i < listView.getChildCount(); ++i) {
                View child = listView.getChildAt(i);
                if (child == null) continue;
                int position = listView.getChildAdapterPosition(child);
                if (position == RecyclerView.NO_POSITION) continue;
                if (position > 0) {
                    top = Math.min(child.getY(), top);
                }
            }
            return top;
        }

        @Override
        protected boolean canDismissWithSwipe() {
            return top() > (int) (AndroidUtilities.displaySize.y * .5f);
        }

        private class Adapter extends RecyclerListView.SelectionAdapter {
            @Override
            public boolean isEnabled(RecyclerView.ViewHolder holder) {
                return holder.getItemViewType() == 2;
            }

            @NonNull
            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                View view;
                if (viewType == 0 || viewType == 1) {
                    view = new View(getContext());
                    int height;
                    if (viewType == 0) {
                        height = AndroidUtilities.displaySize.y + AndroidUtilities.statusBarHeight - dp(54 + 56 * 4.5f);
                    } else {
                        height = dp(54);
                    }
                    view.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height));
                } else {
                    view = new UserCell(getContext(), resourcesProvider);
                }
                return new RecyclerListView.Holder(view);
            }

            @Override
            public int getItemViewType(int position) {
                if (position == 0) {
                    return 0;
                } else if (position == 1) {
                    return 1;
                }
                return 2;
            }

            @Override
            public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                if (holder.getItemViewType() == 2) {
                    UserCell cell = (UserCell) holder.itemView;
                    cell.setIsSendAs(true, true);
                    TLRPC.InputPeer peer = peers.get(position - 2);
                    if (peer instanceof TLRPC.TL_inputPeerSelf) {
                        cell.setUser(UserConfig.getInstance(currentAccount).getCurrentUser());
                    } else if (peer instanceof TLRPC.TL_inputPeerUser) {
                        cell.setUser(MessagesController.getInstance(currentAccount).getUser(peer.user_id));
                    } else if (peer instanceof TLRPC.TL_inputPeerChat) {
                        cell.setChat(MessagesController.getInstance(currentAccount).getChat(peer.chat_id), 0);
                    } else if (peer instanceof TLRPC.TL_inputPeerChannel) {
                        cell.setChat(MessagesController.getInstance(currentAccount).getChat(peer.channel_id), 0);
                    }
                    cell.checkBox.setVisibility(View.GONE);
                    cell.radioButton.setVisibility(View.VISIBLE);
                    cell.setChecked(selectedPeer == null && position == 2 || did(selectedPeer) == did(peer), false);
                    cell.setDivider(position != getItemCount() - 1);
                }
            }

            private long did(TLRPC.InputPeer peer) {
                if (peer instanceof TLRPC.TL_inputPeerSelf) {
                    return UserConfig.getInstance(currentAccount).getClientUserId();
                }
                return DialogObject.getPeerDialogId(peer);
            }

            @Override
            public int getItemCount() {
                return 2 + peers.size();
            }
        }
    }
}
