package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.dpf2;
import static org.telegram.messenger.AndroidUtilities.lerp;
import static org.telegram.messenger.AndroidUtilities.lerp3;
import static org.telegram.messenger.AndroidUtilities.lerpColor3;
import static org.telegram.messenger.AndroidUtilities.setRectD;
import static org.telegram.messenger.LocaleController.formatPluralString;
import static org.telegram.messenger.LocaleController.formatPluralStringComma;
import static org.telegram.messenger.LocaleController.getString;
import static org.telegram.ui.ActionBar.Theme.getColor;
import static org.telegram.ui.ActionBar.Theme.multAlpha;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.style.CharacterStyle;
import android.text.style.ClickableSpan;
import android.text.style.ForegroundColorSpan;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.core.view.NestedScrollingParent3;
import androidx.core.view.NestedScrollingParentHelper;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.LinearSmoothScroller;
import androidx.recyclerview.widget.LinearSnapHelper;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.PhoneFormat.PhoneFormat;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BirthdayController;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_fragment;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.BackDrawable;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Business.ProfileHoursCell;
import org.telegram.ui.Business.ProfileLocationCell;
import org.telegram.ui.Cells.TextDetailCell;
import org.telegram.ui.Components.AnimatedFloat;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.Forum.ForumUtilities;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.ProfileGalleryView;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.SharedMediaLayout;
import org.telegram.ui.Components.SizeNotifierFrameLayout;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;
import org.telegram.ui.Components.VectorAvatarThumbDrawable;

import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.HashMap;

public class ProfileActivity2 extends BaseFragment implements
    NotificationCenter.NotificationCenterDelegate,
    SharedMediaLayout.SharedMediaPreloaderDelegate,
    SharedMediaLayout.Delegate
{

    private SharedMediaLayout.SharedMediaPreloader sharedMediaPreloader;

    public long dialogId;
    public long topicId;
    public boolean self;
    public boolean isTopic;
    public boolean isMain;

    private boolean showAddToContacts;
    private String vcardPhone;
    private String vcardFirstName;
    private String vcardLastName;

    private TLRPC.EncryptedChat encryptedChat;
    private TLRPC.User user;
    private TLRPC.UserFull userInfo;
    private TLRPC.Chat chat;
    private TLRPC.ChatFull chatInfo;

    public static ProfileActivity2 of(long dialogId) {
        final Bundle args = new Bundle();
        args.putLong("dialog_id", dialogId);
        return new ProfileActivity2(args);
    }
    public ProfileActivity2(Bundle args) {
        this(args, null);
    }
    public ProfileActivity2(Bundle args, SharedMediaLayout.SharedMediaPreloader preloader) {
        super(args);
        this.sharedMediaPreloader = preloader;
    }

    public long getDialogId() {
        return dialogId;
    }
    public long getTopicId() {
        return topicId;
    }
    public boolean isSelf() {
        return self;
    }

    @Override
    public boolean onFragmentCreate() {
        final long selfId = getUserConfig().getClientUserId();
        if (arguments.containsKey("dialog_id")) {
            this.dialogId = arguments.getLong("dialog_id");
        } else if (arguments.containsKey("chat_id")) {
            this.dialogId = -arguments.getLong("chat_id");
        } else {
            this.dialogId = arguments.getLong("user_id", 0L);
        }
        if (this.dialogId == 0)
            this.dialogId = selfId;
        this.self = dialogId == selfId;
        this.topicId = arguments.getLong("topic_id", 0L);
        this.isTopic = topicId != 0;

        this.topicId = arguments.getLong("topic_id", 0L);
        this.isMain = arguments.getBoolean("is_main", false);
        this.showAddToContacts = arguments.getBoolean("show_add_to_contacts", true);
        this.vcardPhone = PhoneFormat.stripExceptNumbers(arguments.getString("vcard_phone"));
        this.vcardFirstName = arguments.getString("vcard_first_name");
        this.vcardLastName = arguments.getString("vcard_last_name");

        updateObservers(true);

        if (DialogObject.isEncryptedDialog(dialogId)) {
            encryptedChat = getMessagesController().getEncryptedChat(DialogObject.getEncryptedChatId(dialogId));
        } else if (DialogObject.isUserDialog(dialogId)) {
            user = getMessagesController().getUser(dialogId);

            userInfo = getMessagesController().getUserFull(dialogId);
            getMessagesController().loadFullUser(user, classGuid, true);
        } else if (DialogObject.isChatDialog(dialogId)) {
            chat = getMessagesController().getChat(-dialogId);

            if (ChatObject.isChannel(chat)) {
                getMessagesController().loadFullChat(-dialogId, classGuid, true);
            } else if (chatInfo == null) {
                chatInfo = getMessagesStorage().loadChatInfo(-dialogId, false, null, false, false);
            }
        }

        if (sharedMediaPreloader == null) {
            sharedMediaPreloader = new SharedMediaLayout.SharedMediaPreloader(this);
        }
        sharedMediaPreloader.addDelegate(this);

        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();

        if (sharedMediaPreloader != null) {
            sharedMediaPreloader.onDestroy(this);
        }
        if (sharedMediaPreloader != null) {
            sharedMediaPreloader.removeDelegate(this);
        }

        updateObservers(false);
    }

    private void updateObservers(boolean add) {
        getNotificationCenter().updateObserver(add, this, NotificationCenter.updateInterfaces);
        getNotificationCenter().updateObserver(add, this, NotificationCenter.userInfoDidLoad);
        getNotificationCenter().updateObserver(add, this, NotificationCenter.chatInfoDidLoad);
    }

    private UniversalRecyclerView listView;
    private LinearSnapHelper snapHelper;
    private ProfileGalleryView avatarsViewPager;
    private AvatarImage avatarImage;
    private SharedMediaLayout sharedMediaLayout;

    private boolean[] isOnline = new boolean[1];
    private int onlineCount = -1;
    private SimpleTextView title;
    private SimpleTextView subtitle;

    private ImageLocation prevLoadedImageLocation;

    private boolean hoursExpanded;
    private boolean hoursShownMine;

    @Override
    public View createView(Context context) {
        actionBar.setCastShadows(false);
        actionBar.setAddToContainer(false);
        actionBar.setClipContent(true);
        actionBar.setBackgroundColor(Color.TRANSPARENT);
        actionBar.setOccupyStatusBar(isMain || !AndroidUtilities.isTablet() && !inBubbleMode);

        actionBar.setBackButtonDrawable(new BackDrawable(false));
        final ActionBarMenu menu = actionBar.createMenu();

        final ContainerView container = new ContainerView(context);
        container.setWillNotDraw(false);
        container.setBackgroundColor(0);

        sharedMediaLayout = new SharedMediaLayout(
            context,
            dialogId,
            sharedMediaPreloader,
            userInfo != null ? userInfo.common_chats_count : 0,
            new ArrayList<>(),
            chatInfo,
            userInfo,
            -1,
            -1,
            this, this,
            SharedMediaLayout.VIEW_TYPE_PROFILE_ACTIVITY,
            resourceProvider,
            null
        ) {
            @Override
            protected void onSelectedTabChanged() {
                ProfileActivity2.this.updateSelectedMediaTabText();
            }
            @Override
            protected boolean isSelf() {
                return ProfileActivity2.this.isSelf();
            }
            @Override
            protected boolean isStoriesView() {
                return ProfileActivity2.this.isSelf();
            }
        };
        sharedMediaLayout.setUserInfo(userInfo);
        sharedMediaLayout.setChatInfo(chatInfo);

        listView = new UniversalRecyclerView(this, this::fillItems, this::onClick, this::onLongClick);
        listView.adapter.setApplyBackground(false);
        listView.setOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                updateScrollLayout();
            }
        });
        listView.setSections();
        container.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));

        snapHelper = new LinearSnapHelper() {
            @Override
            public View findSnapView(RecyclerView.LayoutManager layoutManager) {
                if (!(layoutManager instanceof LinearLayoutManager)) return null;
                LinearLayoutManager lm = (LinearLayoutManager) layoutManager;

                int first = lm.findFirstVisibleItemPosition();
                if (first == RecyclerView.NO_POSITION) return null;

                View firstView = lm.findViewByPosition(first);
                if (firstView == null) return null;
                if (
                    firstView.getId() != ID_TOP_EXPANDED &&
                    firstView.getId() != ID_TOP_DEFAULT
                ) return null;

                int visible = firstView.getBottom() - layoutManager.getPaddingTop();
                return (visible >= firstView.getHeight() / 2)
                        ? firstView
                        : lm.findViewByPosition(first + 1);
            }

            @Override
            public int[] calculateDistanceToFinalSnap(@NonNull RecyclerView.LayoutManager lm, @NonNull View target) {
                int[] out = new int[2];
                if (lm.canScrollVertically()) {
                    out[1] = target.getTop() - lm.getPaddingTop(); // <-- TOP edge snap
                }
                return out;
            }

            @Override
            public int findTargetSnapPosition(RecyclerView.LayoutManager lm, int velocityX, int velocityY) {
                View snapView = findSnapView(lm);
                if (snapView == null) return RecyclerView.NO_POSITION;

                int pos = lm.getPosition(snapView);

                // If flinging down with enough velocity, go to next item
                if (velocityY > 400) {
                    pos = Math.min(pos + 1, lm.getItemCount() - 1);
                }
                // If flinging up with enough velocity, go to previous
                else if (velocityY < -400) {
                    pos = Math.max(pos - 1, 0);
                }

                return pos;
            }
        };
        snapHelper.attachToRecyclerView(listView);

        avatarsViewPager = new ProfileGalleryView(context, dialogId, actionBar, listView, null, classGuid, null, null) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                final int w = MeasureSpec.getSize(widthMeasureSpec);
                setMeasuredDimension(w, w);
            }
        };
        avatarsViewPager.setChatInfo(chatInfo);
        container.addView(avatarsViewPager, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.FILL));

        avatarImage = new AvatarImage(context, resourceProvider);
        container.addView(avatarImage, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 400, Gravity.FILL_HORIZONTAL | Gravity.TOP));

        title = new SimpleTextView(context);
        title.setTextSize(25);
        title.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
        title.setGravity(Gravity.LEFT);
        title.setTypeface(AndroidUtilities.bold());
        title.setPivotX(0);
        title.setEllipsizeByGradient(true);
        title.setScrollNonFitText(true);
        container.addView(title, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.FILL_HORIZONTAL, 18.66f, 0, 18.66f, 0));

        subtitle = new SimpleTextView(context);
        subtitle.setTextSize(14);
        subtitle.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText));
        subtitle.setGravity(Gravity.LEFT);
        subtitle.setEllipsizeByGradient(true);
        subtitle.setScrollNonFitText(true);
        container.addView(subtitle, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.FILL_HORIZONTAL, 18.66f, 0, 18.66f, 0));

        container.addView(actionBar);

        updateLayout();
        updateInfo();
        updateColors();

        listView.adapter.update(false);
        listView.scrollToPosition(1);

        return fragmentView = container;
    }

    private void updateLayout() {
        final int statusBarHeight = AndroidUtilities.getStatusBarHeight(getContext());
        final int actionBarHeight = ActionBar.getCurrentActionBarHeight();
        FrameLayout.LayoutParams lp;

        lp = (FrameLayout.LayoutParams) listView.getLayoutParams();
        lp.topMargin = statusBarHeight + actionBarHeight;

        lp = (FrameLayout.LayoutParams) avatarImage.getLayoutParams();
        lp.topMargin = statusBarHeight;

        lp = (FrameLayout.LayoutParams) title.getLayoutParams();
        lp.topMargin = statusBarHeight;

        lp = (FrameLayout.LayoutParams) subtitle.getLayoutParams();
        lp.topMargin = statusBarHeight;

        updateScrollLayout();
    }

    // -1 — action bar
    // 0 — default
    // 1 — expanded
    private float getRelativeScrollTop() {
        if (!(listView.getLayoutManager() instanceof LinearLayoutManager)) return -1.0f;
        final LinearLayoutManager layoutManager = (LinearLayoutManager) listView.getLayoutManager();
        final int firstPosition = layoutManager.findFirstVisibleItemPosition();
        if (firstPosition == RecyclerView.NO_POSITION) return -1.0f;
        final View first = layoutManager.findViewByPosition(firstPosition);
        if (first == null) return -1.0f;

        if (first.getId() == ID_TOP_EXPANDED)
            return 1.0f - Utilities.clamp01(-first.getY() / first.getHeight());
        if (first.getId() == ID_TOP_DEFAULT)
            return -Utilities.clamp01(-first.getY() / first.getHeight());

        return -1.0f;
    }

    private AnimatedFloat expanded = new AnimatedFloat(() -> AndroidUtilities.runOnUIThread(this::updateScrollLayout), 0, 420, CubicBezierInterpolator.EASE_OUT_QUINT);

    private void updateScrollLayout() {
        final int statusBarHeight = AndroidUtilities.getStatusBarHeight(getContext());
        final int actionBarHeight = ActionBar.getCurrentActionBarHeight();
        final float width = fragmentView == null ? AndroidUtilities.displaySize.x : fragmentView.getWidth();

        float top = getRelativeScrollTop();
        float h = lerp3(actionBarHeight, actionBarHeight + dpf2(122), actionBarHeight + dpf2(152 + 122), top);
        final float expanded = this.expanded.set(top > 0.25f);
        top = top <= 0 ? top : expanded;

        avatarImage.setRelativeScrollTop(top);
        avatarImage.setExpandedHeight(h);

        final float titleScale = lerp3(dpf2(18), dpf2(22), dpf2(25), top) / dpf2(25);
        title.setScaleX(titleScale);
        title.setScaleY(titleScale);
        final float titleWidth = Math.min(title.getTextWidth(), title.getMeasuredWidth()) * titleScale;
        title.setTranslationX(lerp3(dpf2(118), (width - titleWidth) / 2f, dpf2(18.66f), top) - title.getLeft());
        title.setTranslationY(lerp3(dpf2(8.333f), dpf2(114), h - dpf2(32) - title.getHeight(), top));
        final float titleAvailableWidth = width - lerp3(dpf2(118 + 32), dpf2(32 + 32), dpf2(18.66f + 18.66f), top);
        title.setRightPadding((int) (Math.max(0, (width - dpf2(18.66f + 18.66f)) * titleScale - titleAvailableWidth) / titleScale));
        title.setTextColor(lerpColor3(
            getThemedColor(Theme.key_windowBackgroundWhiteBlackText),
            getThemedColor(Theme.key_windowBackgroundWhiteBlackText),
            0xFFFFFFFF,
            top
        ));

        final float subtitleWidth = Math.min(subtitle.getTextWidth(), subtitle.getMeasuredWidth());
        subtitle.setTranslationX(lerp3(dpf2(118), (width - subtitleWidth) / 2f, dpf2(18.66f), top) - title.getLeft());
        subtitle.setTranslationY(lerp3(dpf2(31), dpf2(143.66f), h - dpf2(12) - subtitle.getHeight(), top));
        final float subtitleAvailableWidth = width - lerp3(dpf2(118 + 32), dpf2(32 + 32), dpf2(18.66f + 18.66f), top);
        subtitle.setRightPadding((int) Math.max(0, (width - dpf2(18.66f + 18.66f)) - subtitleAvailableWidth));
        subtitle.setTextColor(lerpColor3(
            getThemedColor(Theme.key_windowBackgroundWhiteGrayText),
            getThemedColor(Theme.key_windowBackgroundWhiteGrayText),
            multAlpha(0xFFFFFFFF, 0.85f),
            top
        ));
    }

    @Override
    public boolean isSupportEdgeToEdge() {
        return true;
    }
    @Override
    public boolean drawEdgeNavigationBar() {
        return false;
    }

    private static final int ID_TOP_EXPANDED = 1;
    private static final int ID_TOP_DEFAULT = 2;
    private static final int ID_BUTTONS = 3;
    private static final int ID_MUSIC = 4;

    private static final int ID_PHONE = 5;
    private static final int ID_USERNAME = 6;
    private static final int ID_LOCATION = 7;
    private static final int ID_BIRTHDAY = 8;
    private static final int ID_BIZ_HOURS = 9;
    private static final int ID_BIZ_LOCATION = 10;
    private static final int ID_NOTE = 11;

    private boolean isSection(View view) {
        if (view == null) return false;
        final int id = view.getId();
        return id >= 5;
    }

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asSpace(ID_TOP_EXPANDED, dp(152)));
        items.add(UItem.asSpace(ID_TOP_DEFAULT, dp(122)));
        items.add(UItem.asSpace(ID_BUTTONS, dp(58)));
        items.add(UItem.asSpace(ID_MUSIC, dp(30)));

        if (user != null) {
            addPhoneRow(items);
            addUsernameRow(items);
            if (userInfo != null) {
                if (userInfo.birthday != null) {
                    final boolean today = BirthdayController.isToday(userInfo);
                    final boolean withYear = (userInfo.birthday.flags & 1) != 0;
                    final int age = withYear ? Period.between(LocalDate.of(userInfo.birthday.year, userInfo.birthday.month, userInfo.birthday.day), LocalDate.now()).getYears() : -1;

                    String text = UserInfoActivity.birthdayString(userInfo.birthday);
                    if (withYear) {
                        text = LocaleController.formatPluralString(today ? "ProfileBirthdayTodayValueYear" : "ProfileBirthdayValueYear", age, text);
                    } else {
                        text = LocaleController.formatString(today ? R.string.ProfileBirthdayTodayValue : R.string.ProfileBirthdayValue, text);
                    }

                    items.add(TextDetailCell.Factory.of(ID_BIRTHDAY, text, getString(today ? R.string.ProfileBirthdayToday : R.string.ProfileBirthday)));
                }
                if (userInfo.business_work_hours != null) {
                    items.add(ProfileHoursCell.Factory.of(ID_BIZ_HOURS, userInfo.business_work_hours, hoursExpanded, hoursShownMine, v -> {
                        hoursShownMine = !hoursShownMine;
                        if (!hoursExpanded) hoursExpanded = true;
                        listView.adapter.update(true);
                    }));
                }
                if (userInfo.business_location != null) {
                    items.add(ProfileLocationCell.Factory.of(ID_BIZ_LOCATION, userInfo.business_location));
                }
                if (userInfo.note != null) {

                }
            }
        } else if (chat != null) {
            if (chatInfo != null && (!TextUtils.isEmpty(chatInfo.about) || chatInfo.location instanceof TLRPC.TL_channelLocation) || ChatObject.isPublic(chat)) {
                if (chatInfo != null) {
                    if (!TextUtils.isEmpty(chatInfo.about)) {
//                        channelInfoRow = rowCount++;
                    }
                    if (chatInfo.location instanceof TLRPC.TL_channelLocation) {
                        final TLRPC.TL_channelLocation location = (TLRPC.TL_channelLocation) chatInfo.location;
                        items.add(TextDetailCell.Factory.of(ID_LOCATION, location.address, getString(R.string.AttachLocation)));
                    }
                }
                if (ChatObject.isPublic(chat)) {
                    addUsernameRow(items);
                }
            }
            items.add(UItem.asSpace(dp(10)));
        }

        items.add(UItem.asSpace(dp(10)));

        items.add(UItem.asCustom(sharedMediaLayout));
    }

    private void addPhoneRow(ArrayList<UItem> items) {
        if (user == null) return;

        final String username = UserObject.getPublicUsername(user);
        final boolean hasInfo = userInfo != null && !TextUtils.isEmpty(userInfo.about) || user != null && !TextUtils.isEmpty(username);
        final boolean hasPhone = user != null && (!TextUtils.isEmpty(user.phone) || !TextUtils.isEmpty(vcardPhone));
        if (user.bot || !hasPhone || !hasInfo)
            return;

        final String phoneNumber;
        if (!TextUtils.isEmpty(vcardPhone)) {
            phoneNumber = vcardPhone;
        } else if (!TextUtils.isEmpty(user.phone)) {
            phoneNumber = user.phone;
        } else {
            phoneNumber = null;
        }

        final boolean isFragment = phoneNumber != null && phoneNumber.matches("888\\d{8}");
        items.add(TextDetailCell.Factory.of(
            ID_PHONE,
            phoneNumber != null ? PhoneFormat.getInstance().format("+" + phoneNumber) : getString(R.string.PhoneHidden),
            isFragment ? getString(R.string.AnonymousNumber) : getString(R.string.PhoneMobile)
        ));
    }
    private void addUsernameRow(ArrayList<UItem> items) {
        String username = null;
        CharSequence text, value;
        ArrayList<TLRPC.TL_username> usernames;
        if (user != null) {
            usernames = new ArrayList<>(user.usernames);
            TLRPC.TL_username usernameObj = null;
            if (user != null && !TextUtils.isEmpty(user.username)) {
                usernameObj = DialogObject.findUsername(user.username, usernames);
                username = user.username;
            }
            if (TextUtils.isEmpty(username)) {
                for (int i = 0; i < usernames.size(); ++i) {
                    TLRPC.TL_username u = usernames.get(i);
                    if (u != null && u.active && !TextUtils.isEmpty(u.username)) {
                        usernameObj = u;
                        username = u.username;
                        break;
                    }
                }
            }
            if (username != null) {
                text = "@" + username;
                if (usernameObj != null && !usernameObj.editable) {
                    text = new SpannableString(text);
                    ((SpannableString) text).setSpan(makeUsernameLinkSpan(usernameObj), 0, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
            } else {
                text = "—";
            }
            value = getString(R.string.Username);
        } else if (chat != null) {
            usernames = new ArrayList<>(chat.usernames);
            username = ChatObject.getPublicUsername(chat);
            if (ChatObject.isPublic(chat)) {
                text = getMessagesController().linkPrefix + "/" + username + (topicId != 0 ? "/" + topicId : "");
                value = LocaleController.getString(R.string.InviteLink);
            } else {
                text = getMessagesController().linkPrefix + "/c/" + chat.id + (topicId != 0 ? "/" + topicId : "");
                value = LocaleController.getString(R.string.InviteLinkPrivate);
            }
        } else return;
        if (TextUtils.isEmpty(username))
            return;
        items.add(TextDetailCell.Factory.of(ID_USERNAME, text, alsoUsernamesString(username, usernames, value)));
    }

    private CharSequence alsoUsernamesString(String originalUsername, ArrayList<TLRPC.TL_username> alsoUsernames, CharSequence fallback) {
        if (alsoUsernames == null) {
            return fallback;
        }
        alsoUsernames = new ArrayList<>(alsoUsernames);
        for (int i = 0; i < alsoUsernames.size(); ++i) {
            if (
                !alsoUsernames.get(i).active ||
                originalUsername != null && originalUsername.equals(alsoUsernames.get(i).username)
            ) {
                alsoUsernames.remove(i--);
            }
        }
        if (alsoUsernames.size() > 0) {
            SpannableStringBuilder usernames = new SpannableStringBuilder();
            for (int i = 0; i < alsoUsernames.size(); ++i) {
                TLRPC.TL_username usernameObj = alsoUsernames.get(i);
                final String usernameRaw = usernameObj.username;
                SpannableString username = new SpannableString("@" + usernameRaw);
                username.setSpan(makeUsernameLinkSpan(usernameObj), 0, username.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                username.setSpan(new ForegroundColorSpan(getThemedColor(Theme.key_chat_messageLinkIn)), 0, username.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                usernames.append(username);
                if (i < alsoUsernames.size() - 1) {
                    usernames.append(", ");
                }
            }
            String string = getString(R.string.UsernameAlso);
            SpannableStringBuilder finalString = new SpannableStringBuilder(string);
            final String toFind = "%1$s";
            int index = string.indexOf(toFind);
            if (index >= 0) {
                finalString.replace(index, index + toFind.length(), usernames);
            }
            return finalString;
        } else {
            return fallback;
        }
    }
    private final HashMap<TLRPC.TL_username, ClickableSpan> usernameSpans = new HashMap<TLRPC.TL_username, ClickableSpan>();
    public ClickableSpan makeUsernameLinkSpan(TLRPC.TL_username usernameObj) {
        ClickableSpan span = usernameSpans.get(usernameObj);
        if (span != null) return span;

        final String usernameRaw = usernameObj.username;
        span = new ClickableSpan() {
            @Override
            public void onClick(@NonNull View view) {
                if (!usernameObj.editable) {
                    if (loadingSpan == this) return;
                    setLoadingSpan(this);
                    final TL_fragment.TL_getCollectibleInfo req = new TL_fragment.TL_getCollectibleInfo();
                    final TL_fragment.TL_inputCollectibleUsername input = new TL_fragment.TL_inputCollectibleUsername();
                    input.username = usernameObj.username;
                    req.collectible = input;
                    int reqId = getConnectionsManager().sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
                        setLoadingSpan(null);
                        if (res instanceof TL_fragment.TL_collectibleInfo) {
                            if (getContext() == null) return;
                            FragmentUsernameBottomSheet.open(getContext(), FragmentUsernameBottomSheet.TYPE_USERNAME, usernameObj.username, user != null ? user : chat, (TL_fragment.TL_collectibleInfo) res, getResourceProvider());
                        } else {
                            BulletinFactory.showError(err);
                        }
                    }));
                    getConnectionsManager().bindRequestToGuid(reqId, getClassGuid());
                } else {
                    setLoadingSpan(null);
                    String urlFinal = getMessagesController().linkPrefix + "/" + usernameRaw;
                    if (chat == null || !chat.noforwards) {
                        AndroidUtilities.addToClipboard(urlFinal);
                        BulletinFactory.of(ProfileActivity2.this).createCopyBulletin(getString(R.string.UsernameCopied)).show();
                    }
                }
            }

            @Override
            public void updateDrawState(@NonNull TextPaint ds) {
                ds.setUnderlineText(false);
                ds.setColor(ds.linkColor);
            }
        };
        usernameSpans.put(usernameObj, span);
        return span;
    }

    private CharacterStyle loadingSpan;
    public void setLoadingSpan(CharacterStyle span) {
        if (loadingSpan == span) return;
        loadingSpan = span;
        AndroidUtilities.forEachViews(listView, view -> {
            if (view instanceof TextDetailCell) {
                ((TextDetailCell) view).textView.setLoading(loadingSpan);
                ((TextDetailCell) view).valueTextView.setLoading(loadingSpan);
            }
        });
    }

    private void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == ID_BIZ_HOURS) {
            hoursExpanded = !hoursExpanded;
            listView.adapter.update(true);
        }
    }

    private boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }

    public void updateInfo() {

        if (user != null) {
            title.setText(UserObject.getUserName(user));
            if (self) {
                subtitle.setText(getString(R.string.Online));
            } else if (dialogId == UserObject.VERIFY) {
                subtitle.setText(getString(R.string.VerifyCodesNotifications));
            } else if (dialogId == 333000 || user.id == 777000 || user.id == 42777) {
                subtitle.setText(getString(R.string.ServiceNotifications));
            } else if (MessagesController.isSupportUser(user)) {
                subtitle.setText(getString(R.string.SupportStatus));
            } else if (user.bot) {
                if (user.bot_active_users != 0) {
                    subtitle.setText(formatPluralStringComma("BotUsers", user.bot_active_users, ','));
                } else {
                    subtitle.setText(getString(R.string.Bot));
                }
            } else {
                subtitle.setText(LocaleController.formatUserStatus(currentAccount, user, isOnline, null));
            }
        } else if (chat != null) {
            title.setText(chat.title);
            if (chat.megagroup) {
                if (onlineCount > 1 && chatInfo != null && chatInfo.participants_count != 0) {
                    subtitle.setText(String.format("%s, %s", formatPluralString("Members", chatInfo.participants_count), formatPluralString("OnlineCount", Math.min(onlineCount, chatInfo.participants_count))));
                } else if (chatInfo == null || chatInfo.participants_count == 0) {
                    if (chat.has_geo) {
                        subtitle.setText(getString(R.string.MegaLocation).toLowerCase());
                    } else if (ChatObject.isPublic(chat)) {
                        subtitle.setText(getString(R.string.MegaPublic).toLowerCase());
                    } else {
                        subtitle.setText(getString(R.string.MegaPrivate).toLowerCase());
                    }
                } else {
                    subtitle.setText(formatPluralString("Members", chatInfo.participants_count));
                }
            } else {
                subtitle.setText(formatPluralString("Subscribers", chatInfo == null ? 1 : chatInfo.participants_count));
            }
        }

//        title.setText(title.getText() + "fjskla jdsfklsd jasdj;lf sdjlf jlfj dls ffka ljdsfkasdfj;ldsf;lsdjfladsfkl;dsjklf;ads");
//        subtitle.setText(subtitle.getText() + "fjskla jdsfklsd jasdj;lf sdjlf jlfj dls fasjfkasdlfjdsajfkldsfkjssdfsdalkfjlaksdjfkl");

        updateAvatar();
    }

    public void updateColors() {
        sharedMediaLayout.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
    }

    public void updateAvatar() {
        updateAvatar(false);
    }

    public void updateAvatar(boolean reload) {
        if (user != null) {
            avatarImage.avatarDrawable.setInfo(user);
            final ImageLocation imageLocation = ImageLocation.getForUserOrChat(user, ImageLocation.TYPE_BIG);
            final ImageLocation thumbLocation = ImageLocation.getForUserOrChat(user, ImageLocation.TYPE_SMALL);
            final ImageLocation videoThumbLocation = ImageLocation.getForUserOrChat(user, ImageLocation.TYPE_VIDEO_BIG);
            VectorAvatarThumbDrawable vectorAvatarThumbDrawable = null;
            TLRPC.VideoSize vectorAvatar = null;
            if (userInfo != null) {
                vectorAvatar = FileLoader.getVectorMarkupVideoSize(user.photo != null && user.photo.personal ? userInfo.personal_photo : userInfo.profile_photo);
                if (vectorAvatar != null) {
                    vectorAvatarThumbDrawable = new VectorAvatarThumbDrawable(vectorAvatar, user.premium, VectorAvatarThumbDrawable.TYPE_PROFILE);
                }
            }
            final ImageLocation videoLocation = avatarsViewPager.getCurrentVideoLocation(thumbLocation, imageLocation);
            if (true/*avatar == null*/) {
                avatarsViewPager.initIfEmpty(vectorAvatarThumbDrawable, imageLocation, thumbLocation, reload);
            }
            if (vectorAvatar != null) {
                avatarImage.imageReceiver.setImageBitmap(vectorAvatarThumbDrawable);
            } else if (videoThumbLocation != null && !user.photo.personal) {
                avatarImage.imageReceiver.setVideoThumbIsSame(true);
                avatarImage.imageReceiver.setImage(videoThumbLocation, "avatar", imageLocation, "50_50", thumbLocation, "50_50", avatarImage.avatarDrawable, 0, null, user, 1);
            } else {
                avatarImage.imageReceiver.setImage(videoLocation, ImageLoader.AUTOPLAY_FILTER, imageLocation, "100_100", thumbLocation, "50_50", avatarImage.avatarDrawable, 0, null, user, 1);
            }
        } else if (chat != null) {
            avatarImage.avatarDrawable.setInfo(chat);

            TLRPC.TL_forumTopic topic = null;
            if (isTopic) {
                topic = getMessagesController().getTopicsController().findTopic(chat.id, topicId);
            }

            final ImageLocation imageLocation;
            final ImageLocation thumbLocation;
            final ImageLocation videoLocation;
            if (isTopic) {
                imageLocation = null;
                thumbLocation = null;
                videoLocation = null;
                ForumUtilities.setTopicIcon(getContext(), avatarImage.imageReceiver, topic, true, true, resourceProvider);
            } else if (ChatObject.isMonoForum(chat)) {
                TLRPC.Chat channel = getMessagesController().getMonoForumLinkedChat(chat.id);
                avatarImage.avatarDrawable.setInfo(currentAccount, channel);
                imageLocation = ImageLocation.getForUserOrChat(channel, ImageLocation.TYPE_BIG);
                thumbLocation = ImageLocation.getForUserOrChat(channel, ImageLocation.TYPE_SMALL);
                videoLocation = avatarsViewPager.getCurrentVideoLocation(thumbLocation, imageLocation);
            } else {
                avatarImage.avatarDrawable.setInfo(currentAccount, chat);
                imageLocation = ImageLocation.getForUserOrChat(chat, ImageLocation.TYPE_BIG);
                thumbLocation = ImageLocation.getForUserOrChat(chat, ImageLocation.TYPE_SMALL);
                if (avatarsViewPager != null) {
                    videoLocation = avatarsViewPager.getCurrentVideoLocation(thumbLocation, imageLocation);
                } else {
                    videoLocation = null;
                }
            }

            String filter;
            if (videoLocation != null && videoLocation.imageType == FileLoader.IMAGE_TYPE_ANIMATION) {
                filter = ImageLoader.AUTOPLAY_FILTER;
            } else {
                filter = null;
            }
            if (/*avatarBig == null && */!isTopic) {
                avatarImage.imageReceiver.setImage(videoLocation, filter, thumbLocation, "50_50", null, null, avatarImage.avatarDrawable, 0, null, chat, 1);
            }
            if (imageLocation != null && (prevLoadedImageLocation == null || imageLocation.photoId != prevLoadedImageLocation.photoId)) {
                prevLoadedImageLocation = imageLocation;
                getFileLoader().loadFile(imageLocation, chat, null, FileLoader.PRIORITY_LOW, 1);
            }
        }
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.updateInterfaces) {
            updateInfo();
        } else if (id == NotificationCenter.userInfoDidLoad) {
            final long uid = (Long) args[0];
            if (uid != dialogId) return;

            userInfo = (TLRPC.UserFull) args[1];

            updateInfo();
            if (sharedMediaLayout != null) {
                sharedMediaLayout.setUserInfo(userInfo);
            }

        } else if (id == NotificationCenter.chatInfoDidLoad) {
            final TLRPC.ChatFull chatFull = (TLRPC.ChatFull) args[0];
            if (chatFull.id != -dialogId) return;

            chatInfo = chatFull;

            updateInfo();
            if (sharedMediaLayout != null) {
                sharedMediaLayout.setChatInfo(chatInfo);
            }
            if (avatarsViewPager != null) {
                avatarsViewPager.setChatInfo(chatInfo);
            }
        }
    }

    private boolean isFragmentOpened;
    @Override
    public void onTransitionAnimationStart(boolean isOpen, boolean backward) {
        super.onTransitionAnimationStart(isOpen, backward);
        isFragmentOpened = isOpen;
    }

    public void drawBackground(Canvas canvas) {
        canvas.drawColor(getThemedColor(Theme.key_windowBackgroundGray));
    }

    private final Paint sectionPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    public void drawBackgroundRect(Canvas canvas, RectF rect, float r, float alpha) {
        sectionPaint.setShadowLayer(dpf2(1.5f), 0, 0, multAlpha(Color.BLACK & 0x20FFFFFF, alpha));
        sectionPaint.setColor(multAlpha(getThemedColor(Theme.key_windowBackgroundWhite), alpha));
        canvas.drawRoundRect(rect, r, r, sectionPaint);
    }

    @Override
    public void mediaCountUpdated() {

    }

    @Override
    public void scrollToSharedMedia() {

    }

    @Override
    public boolean onMemberClick(TLRPC.ChatParticipant participant, boolean b, boolean resultOnly, View view) {
        return false;
    }

    @Override
    public TLRPC.Chat getCurrentChat() {
        return chat;
    }

    @Override
    public boolean isFragmentOpened() {
        return isFragmentOpened;
    }

    @Override
    public RecyclerListView getListView() {
        return listView;
    }

    @Override
    public boolean canSearchMembers() {
        return false;
    }

    @Override
    public void updateSelectedMediaTabText() {
        // TODO
    }

    public final static class AvatarImage extends View {

        private final Theme.ResourcesProvider resourcesProvider;

        public final RectF avatarRect = new RectF();
        public final AvatarDrawable avatarDrawable = new AvatarDrawable();
        public final ImageReceiver imageReceiver = new ImageReceiver(this);

        public AvatarImage(Context context, Theme.ResourcesProvider resourcesProvider) {
            super(context);
            this.resourcesProvider = resourcesProvider;
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            imageReceiver.onAttachedToWindow();
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            imageReceiver.onDetachedFromWindow();
        }

        private float relativeTop, expandedHeight;
        public void setRelativeScrollTop(float relativeTop) {
            if (Math.abs(relativeTop - this.relativeTop) < 0.0001f) return;
            this.relativeTop = relativeTop;
            invalidate();
        }
        public void setExpandedHeight(float expandedHeight) {
            if (Math.abs(expandedHeight - this.expandedHeight) < 0.0001f) return;
            this.expandedHeight = expandedHeight;
            invalidate();
        }

        @Override
        protected void dispatchDraw(@NonNull Canvas canvas) {
            super.dispatchDraw(canvas);

            final float cx = getWidth() / 2.0f;

            final float avatarCx =     lerp3(dpf2(85), cx, cx, relativeTop);
            final float avatarCy =     lerp3(dpf2(28), dpf2(59), expandedHeight / 2.0f, relativeTop);
            final float avatarW =      lerp3(dpf2(42), dpf2(90), getWidth(), relativeTop);
            final float avatarH =      lerp3(dpf2(42), dpf2(90), expandedHeight, relativeTop);
            final float avatarRadius = lerp3(avatarW / 2.0f, avatarW / 2.0f, 0, relativeTop);

            avatarRect.set(
                avatarCx - avatarW / 2.0f,
                avatarCy - avatarH / 2.0f,
                avatarCx + avatarW / 2.0f,
                avatarCy + avatarH / 2.0f
            );

            imageReceiver.setImageCoords(avatarRect);
            imageReceiver.setRoundRadius((int) avatarRadius);
            imageReceiver.draw(canvas);
        }
    }

    private class ContainerView extends SizeNotifierFrameLayout implements NestedScrollingParent3 {

        private NestedScrollingParentHelper nestedScrollingParentHelper;

        public ContainerView(Context context) {
            super(context);
            nestedScrollingParentHelper = new NestedScrollingParentHelper(this);
        }

        @Override
        public void onNestedScroll(View target, int dxConsumed, int dyConsumed, int dxUnconsumed, int dyUnconsumed, int type, int[] consumed) {
            try {
                if (target == listView && sharedMediaLayout.isAttachedToWindow()) {
                    RecyclerListView innerListView = sharedMediaLayout.getCurrentListView();
                    int top = sharedMediaLayout.getTop();
                    if (top == 0) {
                        consumed[1] = dyUnconsumed;
                        innerListView.scrollBy(0, dyUnconsumed);
                    }
                }
            } catch (Throwable e) {
                FileLog.e(e);
                AndroidUtilities.runOnUIThread(() -> {
                    try {
                        RecyclerListView innerListView = sharedMediaLayout.getCurrentListView();
                        if (innerListView != null && innerListView.getAdapter() != null) {
                            innerListView.getAdapter().notifyDataSetChanged();
                        }
                    } catch (Throwable e2) {

                    }
                });
            }
        }


        @Override
        public void onNestedScroll(View target, int dxConsumed, int dyConsumed, int dxUnconsumed, int dyUnconsumed, int type) {

        }

        @Override
        public boolean onNestedPreFling(View target, float velocityX, float velocityY) {
            return super.onNestedPreFling(target, velocityX, velocityY);
        }

        @Override
        public void onNestedPreScroll(View target, int dx, int dy, int[] consumed, int type) {
            if (target == listView && sharedMediaLayout.isAttachedToWindow()) {
                boolean searchVisible = actionBar.isSearchFieldVisible();
                int t = sharedMediaLayout.getTop();
                if (dy < 0) {
                    boolean scrolledInner = false;
                    if (t <= 0) {
                        RecyclerListView innerListView = sharedMediaLayout.getCurrentListView();
                        if (innerListView != null) {
                            LinearLayoutManager linearLayoutManager = (LinearLayoutManager) innerListView.getLayoutManager();
                            int pos = linearLayoutManager.findFirstVisibleItemPosition();
                            if (pos != RecyclerView.NO_POSITION) {
                                RecyclerView.ViewHolder holder = innerListView.findViewHolderForAdapterPosition(pos);
                                int top = holder != null ? holder.itemView.getTop() : -1;
                                int paddingTop = innerListView.getPaddingTop();
                                if (top != paddingTop || pos != 0) {
                                    consumed[1] = pos != 0 ? dy : Math.max(dy, (top - paddingTop));
                                    innerListView.scrollBy(0, dy);
                                    scrolledInner = true;
                                }
                            }
                        }
                    }
                    if (searchVisible) {
                        if (!scrolledInner && t < 0) {
                            consumed[1] = dy - Math.max(t, dy);
                        } else {
                            consumed[1] = dy;
                        }
                    }
                } else {
                    if (searchVisible) {
                        RecyclerListView innerListView = sharedMediaLayout.getCurrentListView();
                        consumed[1] = dy;
                        if (t > 0) {
                            consumed[1] -= dy;
                        }
                        if (innerListView != null && consumed[1] > 0) {
                            innerListView.scrollBy(0, consumed[1]);
                        }
                    }
                }
            }
        }

        @Override
        public boolean onStartNestedScroll(View child, View target, int axes, int type) {
            return sharedMediaLayout.isAttachedToWindow() && axes == ViewCompat.SCROLL_AXIS_VERTICAL;
        }

        @Override
        public void onNestedScrollAccepted(View child, View target, int axes, int type) {
            nestedScrollingParentHelper.onNestedScrollAccepted(child, target, axes);
        }

        @Override
        public void onStopNestedScroll(View target, int type) {
            nestedScrollingParentHelper.onStopNestedScroll(target);
        }

        @Override
        public void onStopNestedScroll(View child) {

        }

        @Override
        protected void drawList(Canvas blurCanvas, boolean top, ArrayList<IViewWithInvalidateCallback> views) {
            super.drawList(blurCanvas, top, views);
            blurCanvas.save();
            blurCanvas.translate(0, listView.getY());
            sharedMediaLayout.drawListForBlur(blurCanvas, views);
            blurCanvas.restore();
        }

        @Override
        protected void dispatchDraw(@NonNull Canvas canvas) {
            drawBackground(canvas);
            super.dispatchDraw(canvas);
        }
    }


}
