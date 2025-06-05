package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.formatString;
import static org.telegram.messenger.LocaleController.getString;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.style.ClickableSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BillingController;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.browser.Browser;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.Vector;
import org.telegram.tgnet.tl.TL_stars;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.AvatarSpan;
import org.telegram.ui.Cells.GraySectionCell;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.UserCell;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.LinkEditActivity;
import org.telegram.ui.ManageLinksActivity;
import org.telegram.ui.ProfileActivity;
import org.telegram.ui.Stars.StarsController;
import org.telegram.ui.Stars.StarsIntroActivity;
import org.telegram.ui.Stories.recorder.ButtonWithCounterView;
import org.telegram.ui.Stories.recorder.HintView2;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

public class InviteLinkBottomSheet extends BottomSheet {

    TLRPC.TL_chatInviteExported invite;
    HashMap<Long, TLRPC.User> users;
    TLRPC.ChatFull info;

    int revenueHeaderRow;
    int revenueRow;
    int creatorHeaderRow;
    int creatorRow;
    int dividerRow;
    int divider2Row;
    int divider3Row;
    int joinedHeaderRow;
    int joinedStartRow;
    int joinedEndRow;
    int expiredHeaderRow;
    int expiredStartRow;
    int expiredEndRow;
    int linkActionRow;
    int linkInfoRow;
    int loadingRow;
    int emptyView;
    int emptyView2;
    int emptyView3;
    int emptyHintRow;
    int requestedHeaderRow;
    int requestedStartRow;
    int requestedEndRow;

    boolean usersLoading;
    boolean hasMore;

    int rowCount;
    Adapter adapter;
    BaseFragment fragment;

    private RecyclerListView listView;
    private TextView titleTextView;
    private AnimatorSet shadowAnimation;
    private View shadow;

    private int scrollOffsetY;
    private boolean ignoreLayout;
    private boolean permanent;
    private boolean titleVisible;

    ArrayList<TLRPC.TL_chatInviteImporter> joinedUsers = new ArrayList<>();
    ArrayList<TLRPC.TL_chatInviteImporter> expiredUsers = new ArrayList<>();
    ArrayList<TLRPC.TL_chatInviteImporter> requestedUsers = new ArrayList<>();

    private long chatId;
    private boolean isChannel;
    private final long timeDif;

    InviteDelegate inviteDelegate;

    private boolean canEdit = true;
    public boolean isNeedReopen = false;

    public InviteLinkBottomSheet(Context context, TLRPC.TL_chatInviteExported invite, TLRPC.ChatFull info, HashMap<Long, TLRPC.User> users, BaseFragment fragment, long chatId, boolean permanent, boolean isChannel) {
        super(context, false);
        this.invite = invite;
        this.users = users;
        this.fragment = fragment;
        this.info = info;
        this.chatId = chatId;
        this.permanent = permanent;
        this.isChannel = isChannel;
        fixNavigationBar(getThemedColor(Theme.key_graySection));

        if (this.users == null) {
            this.users = new HashMap<>();
        }

        timeDif = ConnectionsManager.getInstance(currentAccount).getCurrentTime() - (System.currentTimeMillis() / 1000L);

        containerView = new FrameLayout(context) {

            private RectF rect = new RectF();
            private boolean fullHeight;

            @Override
            public boolean onInterceptTouchEvent(MotionEvent ev) {
                if (ev.getAction() == MotionEvent.ACTION_DOWN && scrollOffsetY != 0 && ev.getY() < scrollOffsetY) {
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
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                int height = MeasureSpec.getSize(heightMeasureSpec);
                if (Build.VERSION.SDK_INT >= 21) {
                    ignoreLayout = true;
                    setPadding(backgroundPaddingLeft, AndroidUtilities.statusBarHeight, backgroundPaddingLeft, 0);
                    ignoreLayout = false;
                }
                super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
                fullHeight = true;
            }

            @Override
            protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
                super.onLayout(changed, left, top, right, bottom);
                updateLayout();
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
                int top = scrollOffsetY - backgroundPaddingTop - dp(8);
                int height = getMeasuredHeight() + dp(36) + backgroundPaddingTop;
                int statusBarHeight = 0;
                float radProgress = 1.0f;
                if (Build.VERSION.SDK_INT >= 21) {
                    top += AndroidUtilities.statusBarHeight;
                    height -= AndroidUtilities.statusBarHeight;

                    if (fullHeight) {
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
                }

                shadowDrawable.setBounds(0, top, getMeasuredWidth(), height);
                shadowDrawable.draw(canvas);

                if (radProgress != 1.0f) {
                    Theme.dialogs_onlineCirclePaint.setColor(Theme.getColor(Theme.key_dialogBackground));
                    rect.set(backgroundPaddingLeft, backgroundPaddingTop + top, getMeasuredWidth() - backgroundPaddingLeft, backgroundPaddingTop + top + dp(24));
                    canvas.drawRoundRect(rect, dp(12) * radProgress, dp(12) * radProgress, Theme.dialogs_onlineCirclePaint);
                }

                if (statusBarHeight > 0) {
                    Theme.dialogs_onlineCirclePaint.setColor(Theme.getColor(Theme.key_dialogBackground));
                    canvas.drawRect(backgroundPaddingLeft, AndroidUtilities.statusBarHeight - statusBarHeight, getMeasuredWidth() - backgroundPaddingLeft, AndroidUtilities.statusBarHeight, Theme.dialogs_onlineCirclePaint);
                }
                updateLightStatusBar(statusBarHeight > AndroidUtilities.statusBarHeight / 2);
            }

            private Boolean statusBarOpen;
            private void updateLightStatusBar(boolean open) {
                if (statusBarOpen != null && statusBarOpen == open) {
                    return;
                }
                boolean openBgLight = AndroidUtilities.computePerceivedBrightness(getThemedColor(Theme.key_dialogBackground)) > .721f;
                boolean closedBgLight = AndroidUtilities.computePerceivedBrightness(Theme.blendOver(getThemedColor(Theme.key_actionBarDefault), 0x33000000)) > .721f;
                boolean isLight = (statusBarOpen = open) ? openBgLight : closedBgLight;
                AndroidUtilities.setLightStatusBar(getWindow(), isLight);
            }
        };
        containerView.setWillNotDraw(false);

        FrameLayout.LayoutParams frameLayoutParams = new FrameLayout.LayoutParams(LayoutHelper.MATCH_PARENT, AndroidUtilities.getShadowHeight(), Gravity.TOP | Gravity.LEFT);
        frameLayoutParams.topMargin = dp(48);
        shadow = new View(context);
        shadow.setAlpha(0.0f);
        shadow.setVisibility(View.INVISIBLE);
        shadow.setTag(1);
        containerView.addView(shadow, frameLayoutParams);

        listView = new RecyclerListView(context) {

            int lastH;

            @Override
            public void requestLayout() {
                if (ignoreLayout) {
                    return;
                }
                super.requestLayout();
            }

            @Override
            protected void onMeasure(int widthSpec, int heightSpec) {
                if (lastH != MeasureSpec.getSize(heightSpec)) {
                    lastH = MeasureSpec.getSize(heightSpec);
                    ignoreLayout = true;
                    listView.setPadding(0, 0, 0, 0);
                    ignoreLayout = false;

                    measure(widthSpec, View.MeasureSpec.makeMeasureSpec(heightSpec, MeasureSpec.AT_MOST));
                    int contentSize = getMeasuredHeight();

                    int padding = (int) (lastH / 5f * 2f);
                    if (padding < lastH - contentSize + dp(60)) {
                        padding = lastH - contentSize;
                    }
                    ignoreLayout = true;
                    listView.setPadding(0, padding, 0, 0);
                    ignoreLayout = false;

                    measure(widthSpec, View.MeasureSpec.makeMeasureSpec(heightSpec, MeasureSpec.AT_MOST));
                }

                super.onMeasure(widthSpec, heightSpec);
            }
        };
        listView.setTag(14);
        LinearLayoutManager layoutManager = new LinearLayoutManager(getContext(), LinearLayoutManager.VERTICAL, false);
        listView.setLayoutManager(layoutManager);
        listView.setAdapter(adapter = new Adapter());
        listView.setVerticalScrollBarEnabled(false);
        listView.setClipToPadding(false);
        listView.setNestedScrollingEnabled(true);
        listView.setOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                updateLayout();
                if (hasMore && !usersLoading) {
                    int lastPosition = layoutManager.findLastVisibleItemPosition();
                    if (rowCount - lastPosition < 10) {
                        loadUsers();
                    }
                }
            }
        });
        listView.setOnItemClickListener((view, position) -> {
            if (position == creatorRow && invite.admin_id == UserConfig.getInstance(currentAccount).clientUserId) {
                return;
            }
            boolean isJoinedUserRow = position >= joinedStartRow && position < joinedEndRow;
            boolean isExpiredUserRow = position >= expiredStartRow && position < expiredEndRow;
            boolean isRequestedUserRow = position >= requestedStartRow && position < requestedEndRow;
            if ((position == creatorRow || isJoinedUserRow || isRequestedUserRow) && users != null) {
                long userId = invite.admin_id;
                TLRPC.TL_chatInviteImporter importer = null;
                if (isJoinedUserRow) {
                    importer = joinedUsers.get(position - joinedStartRow);
                    userId = importer.user_id;
                } else if (isExpiredUserRow) {
                    importer = expiredUsers.get(position - expiredStartRow);
                    userId = importer.user_id;
                } else if (isRequestedUserRow) {
                    importer = requestedUsers.get(position - requestedStartRow);
                    userId = importer.user_id;
                }
                TLRPC.User user = users.get(userId);
                if (user != null) {
                    MessagesController.getInstance(UserConfig.selectedAccount).putUser(user, false);
                    if (isJoinedUserRow && invite.subscription_pricing != null) {
                        TLRPC.ChannelParticipant part = null;
                        if (info != null && info.participants != null) {
                            for (int i = 0; i < info.participants.participants.size(); i++) {
                                if (info.participants.participants.get(i).user_id == userId && info.participants.participants.get(i) instanceof TLRPC.TL_chatChannelParticipant) {
                                    part = ((TLRPC.TL_chatChannelParticipant) info.participants.participants.get(i)).channelParticipant;
                                    break;
                                }
                            }
                        }
                        if (part == null) {
                            AlertDialog progressDialog = new AlertDialog(context, AlertDialog.ALERT_TYPE_SPINNER);
                            progressDialog.showDelayed(120);
                            TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(chatId);
                            final TLRPC.TL_chatInviteImporter finalImporter = importer;
                            MessagesController.getInstance(currentAccount).getChannelParticipant(chat, user, participant -> AndroidUtilities.runOnUIThread(() -> {
                                progressDialog.dismissUnless(400);
//                                if (participant != null) {
                                    showSubscriptionSheet(context, currentAccount, -chatId, invite.subscription_pricing, finalImporter, participant, resourcesProvider);
//                                } else {
//                                    AndroidUtilities.runOnUIThread(() -> {
//                                        Bundle bundle = new Bundle();
//                                        bundle.putLong("user_id", user.id);
//                                        ProfileActivity profileActivity = new ProfileActivity(bundle);
//                                        fragment.presentFragment(profileActivity);
//                                        isNeedReopen = true;
//                                    }, 100);
//                                    dismiss();
//                                }
                            }));
                        } else {
                            showSubscriptionSheet(context, currentAccount, -chatId, invite.subscription_pricing, importer, part, resourcesProvider);
                        }
                    } else {
                        AndroidUtilities.runOnUIThread(() -> {
                            Bundle bundle = new Bundle();
                            bundle.putLong("user_id", user.id);
                            ProfileActivity profileActivity = new ProfileActivity(bundle);
                            fragment.presentFragment(profileActivity);
                            isNeedReopen = true;
                        }, 100);
                        dismiss();
                    }
                }
            }
        });

        titleTextView = new TextView(context);
        titleTextView.setLines(1);
        titleTextView.setSingleLine(true);
        titleTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        titleTextView.setEllipsize(TextUtils.TruncateAt.END);
        titleTextView.setPadding(dp(23), 0, dp(23), 0);
        titleTextView.setGravity(Gravity.CENTER_VERTICAL);
        titleTextView.setTypeface(AndroidUtilities.bold());
        if (!permanent) {
            if (invite.expired) {
                titleTextView.setText(LocaleController.getString(R.string.ExpiredLink));
            } else if (invite.revoked) {
                titleTextView.setText(LocaleController.getString(R.string.RevokedLink));
            } else {
                titleTextView.setText(LocaleController.getString(R.string.InviteLink));
            }
            titleVisible = true;
        } else {
            titleTextView.setText(LocaleController.getString(R.string.InviteLink));
            titleVisible = false;
            titleTextView.setVisibility(View.INVISIBLE);
            titleTextView.setAlpha(0f);
        }
        if (!TextUtils.isEmpty(invite.title)) {
            SpannableStringBuilder builder = new SpannableStringBuilder(invite.title);
            Emoji.replaceEmoji(builder, titleTextView.getPaint().getFontMetricsInt(), false);
            titleTextView.setText(builder);
        }

        containerView.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT, 0, !titleVisible ? 0 : 44, 0, 0));
        containerView.addView(titleTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, !titleVisible ? 44 : 50, Gravity.LEFT | Gravity.TOP, 0, 0, 0, 0));

        updateRows();
        loadUsers();
        if (users == null || users.get(invite.admin_id) == null) {
            loadCreator();
        }

        updateColors();
    }

    public void updateColors() {
        if (titleTextView != null) {
            titleTextView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
            titleTextView.setLinkTextColor(Theme.getColor(Theme.key_dialogTextLink));
            titleTextView.setHighlightColor(Theme.getColor(Theme.key_dialogLinkSelection));
            if (!titleVisible) {
                titleTextView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
            }
        }
        listView.setGlowColor(Theme.getColor(Theme.key_dialogScrollGlow));
        shadow.setBackgroundColor(Theme.getColor(Theme.key_dialogShadowLine));
        setBackgroundColor(Theme.getColor(Theme.key_dialogBackground));


        int count = listView.getHiddenChildCount();

        for (int i = 0; i < listView.getChildCount(); i++) {
            updateColorForView(listView.getChildAt(i));
        }
        for (int a = 0; a < count; a++) {
            updateColorForView(listView.getHiddenChildAt(a));
        }
        count = listView.getCachedChildCount();
        for (int a = 0; a < count; a++) {
            updateColorForView(listView.getCachedChildAt(a));
        }
        count = listView.getAttachedScrapChildCount();
        for (int a = 0; a < count; a++) {
            updateColorForView(listView.getAttachedScrapChildAt(a));
        }
        containerView.invalidate();
    }

    @Override
    public void show() {
        super.show();
        isNeedReopen = false;
    }

    private void updateColorForView(View view) {
        if (view instanceof HeaderCell) {
            ((HeaderCell) view).getTextView().setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueHeader));
        } else if (view instanceof LinkActionView) {
            ((LinkActionView) view).updateColors();
        } else if (view instanceof TextInfoPrivacyCell) {
            CombinedDrawable combinedDrawable = new CombinedDrawable(new ColorDrawable(Theme.getColor(Theme.key_windowBackgroundGray)), Theme.getThemedDrawableByKey(view.getContext(), R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
            combinedDrawable.setFullsize(true);
            view.setBackground(combinedDrawable);
            ((TextInfoPrivacyCell) view).setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText4));
        } else if (view instanceof UserCell) {
            ((UserCell) view).update(0);
        }
        RecyclerView.ViewHolder holder = listView.getChildViewHolder(view);
        if (holder != null) {
            if (holder.getItemViewType() == 7) {
                Drawable shadowDrawable = Theme.getThemedDrawableByKey(view.getContext(), R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow);
                Drawable background = new ColorDrawable(Theme.getColor(Theme.key_windowBackgroundGray));
                CombinedDrawable combinedDrawable = new CombinedDrawable(background, shadowDrawable, 0, 0);
                combinedDrawable.setFullsize(true);
                view.setBackgroundDrawable(combinedDrawable);
            } else if (holder.getItemViewType() == 2) {
                Drawable shadowDrawable = Theme.getThemedDrawableByKey(view.getContext(), R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow);
                Drawable background = new ColorDrawable(Theme.getColor(Theme.key_windowBackgroundGray));
                CombinedDrawable combinedDrawable = new CombinedDrawable(background, shadowDrawable, 0, 0);
                combinedDrawable.setFullsize(true);
                view.setBackgroundDrawable(combinedDrawable);
            }
        }
    }

    private void loadCreator() {
        TLRPC.TL_users_getUsers req = new TLRPC.TL_users_getUsers();
        req.id.add(MessagesController.getInstance(UserConfig.selectedAccount).getInputUser(invite.admin_id));
        ConnectionsManager.getInstance(UserConfig.selectedAccount).sendRequest(req, (response, error) -> {
            AndroidUtilities.runOnUIThread(() -> {
                if (response instanceof Vector) {
                    Vector<TLRPC.User> vector = (Vector<TLRPC.User>) response;
                    if (vector.objects.isEmpty()) return;
                    TLRPC.User user = vector.objects.get(0);
                    users.put(invite.admin_id, user);
                    adapter.notifyDataSetChanged();
                }
            });
        });
    }

    @Override
    protected boolean canDismissWithSwipe() {
        return false;
    }

    private void updateRows() {
        rowCount = 0;
        dividerRow = -1;
        divider2Row = -1;
        divider3Row = -1;
        joinedHeaderRow = -1;
        joinedStartRow = -1;
        joinedEndRow = -1;
        emptyView2 = -1;
        emptyView3 = -1;
        linkActionRow = -1;
        linkInfoRow = -1;
        emptyHintRow = -1;
        requestedHeaderRow = -1;
        requestedStartRow = -1;
        requestedEndRow = -1;
        loadingRow = -1;
        revenueHeaderRow = -1;
        revenueRow = -1;
        expiredHeaderRow = -1;
        expiredStartRow = -1;
        expiredEndRow = -1;

        if (!permanent) {
            linkActionRow = rowCount++;
            linkInfoRow = rowCount++;
        }
        if (invite.subscription_pricing != null) {
            revenueHeaderRow = rowCount++;
            revenueRow = rowCount++;
        }
        creatorHeaderRow = rowCount++;
        creatorRow = rowCount++;
//        emptyView = rowCount++;

        boolean needUsers = invite.usage > 0 || invite.usage_limit > 0 || invite.requested > 0 || invite.subscription_expired > 0;
        boolean needLoadUsers = invite.usage > joinedUsers.size() || invite.subscription_expired > expiredUsers.size() || invite.request_needed && invite.requested > requestedUsers.size();
        boolean usersLoaded = false;
        if (!joinedUsers.isEmpty()) {
//            dividerRow = rowCount++;
            joinedHeaderRow = rowCount++;
            joinedStartRow = rowCount;
            rowCount += joinedUsers.size();
            joinedEndRow = rowCount;
//            emptyView2 = rowCount++;
            usersLoaded = true;
        }
        if (!expiredUsers.isEmpty()) {
//            dividerRow = rowCount++;
            expiredHeaderRow = rowCount++;
            expiredStartRow = rowCount;
            rowCount += expiredUsers.size();
            expiredEndRow = rowCount;
//            emptyView2 = rowCount++;
            usersLoaded = true;
        }
        if (!requestedUsers.isEmpty()) {
//            divider2Row = rowCount++;
            requestedHeaderRow = rowCount++;
            requestedStartRow = rowCount;
            rowCount += requestedUsers.size();
            requestedEndRow = rowCount;
//            emptyView3 = rowCount++;
            usersLoaded = true;
        }
        if (needUsers || needLoadUsers) {
            if (!usersLoaded) {
                dividerRow = rowCount++;
                loadingRow = rowCount++;
                emptyView2 = rowCount++;
            }
        }
        if (emptyHintRow == -1) {
//            divider3Row = rowCount++;
        }

        adapter.notifyDataSetChanged();
    }

    private class Adapter extends RecyclerListView.SelectionAdapter {

        @Override
        public int getItemViewType(int position) {
            if (position == creatorHeaderRow || position == requestedHeaderRow || position == joinedHeaderRow || position == revenueHeaderRow) {
                return 0;
            } else if (position == creatorRow || position >= requestedStartRow && position < requestedEndRow || position >= joinedStartRow && position < joinedEndRow) {
                return 1;
            } else if (position == dividerRow || position == divider2Row) {
                return 2;
            } else if (position == linkActionRow) {
                return 3;
            } else if (position == linkInfoRow) {
                return 4;
            } else if (position == loadingRow) {
                return 5;
            } else if (position == emptyView || position == emptyView2 || position == emptyView3) {
                return 6;
            } else if (position == divider3Row) {
                return 7;
            } else if (position == emptyHintRow) {
                return 8;
            } else if (position == revenueRow) {
                return 9;
            }
            return 0;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view;
            Context context = parent.getContext();
            switch (viewType) {
                default:
                case 0:
                    GraySectionCell headerCell = new GraySectionCell(context, resourcesProvider);
                    view = headerCell;
                    break;
                case 1:
                    view = new RevenueUserCell(context);
                    break;
                case 2:
                    view = new ShadowSectionCell(context, 12, Theme.getColor(Theme.key_windowBackgroundGray));
                    break;
                case 3:
                    LinkActionView linkActionView = new LinkActionView(context, fragment, InviteLinkBottomSheet.this, chatId, false, isChannel) {
                        @Override
                        public void showBulletin(int resId, CharSequence str) {
                            Bulletin b = BulletinFactory.of(container, resourcesProvider).createSimpleBulletin(resId, str);
                            b.hideAfterBottomSheet = false;
                            b.show(true);
                        }
                    };
                    view = linkActionView;
                    linkActionView.setDelegate(new LinkActionView.Delegate() {
                        @Override
                        public void revokeLink() {
                            if (fragment instanceof ManageLinksActivity) {
                                ((ManageLinksActivity) fragment).revokeLink(invite);
                            } else {
                                TLRPC.TL_messages_editExportedChatInvite req = new TLRPC.TL_messages_editExportedChatInvite();
                                req.link = invite.link;
                                req.revoked = true;
                                req.peer = MessagesController.getInstance(currentAccount).getInputPeer(-chatId);
                                ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                                    if (error == null) {
                                        if (response instanceof TLRPC.TL_messages_exportedChatInviteReplaced) {
                                            TLRPC.TL_messages_exportedChatInviteReplaced replaced = (TLRPC.TL_messages_exportedChatInviteReplaced) response;
                                            if (info != null) {
                                                info.exported_invite = (TLRPC.TL_chatInviteExported) replaced.new_invite;
                                            }
                                            if (inviteDelegate != null) {
                                                inviteDelegate.permanentLinkReplaced(invite, info.exported_invite);
                                            }
                                        } else {
                                            if (info != null) {
                                                info.invitesCount--;
                                                if (info.invitesCount < 0) {
                                                    info.invitesCount = 0;
                                                }
                                                MessagesStorage.getInstance(currentAccount).saveChatLinksCount(chatId, info.invitesCount);
                                            }
                                            if (inviteDelegate != null) {
                                                inviteDelegate.linkRevoked(invite);
                                            }
                                        }
                                    }
                                }));
                            }
                            dismiss();
                        }

                        @Override
                        public void editLink() {
                            if (fragment instanceof ManageLinksActivity) {
                                ((ManageLinksActivity) fragment).editLink(invite);
                            } else {
                                LinkEditActivity activity = new LinkEditActivity(LinkEditActivity.EDIT_TYPE, chatId);
                                activity.setInviteToEdit(invite);
                                activity.setCallback(new LinkEditActivity.Callback() {
                                    @Override
                                    public void onLinkCreated(TLObject response) {

                                    }

                                    @Override
                                    public void onLinkEdited(TLRPC.TL_chatInviteExported inviteToEdit, TLObject response) {
                                        if (inviteDelegate != null) {
                                            inviteDelegate.onLinkEdited(inviteToEdit);
                                        }
                                    }

                                    @Override
                                    public void onLinkRemoved(TLRPC.TL_chatInviteExported inviteFinal) {

                                    }

                                    @Override
                                    public void revokeLink(TLRPC.TL_chatInviteExported inviteFinal) {

                                    }
                                });
                                fragment.presentFragment(activity);
                            }
                            dismiss();
                        }

                        @Override
                        public void removeLink() {
                            if (fragment instanceof ManageLinksActivity) {
                                ((ManageLinksActivity) fragment).deleteLink(invite);
                            } else {
                                TLRPC.TL_messages_deleteExportedChatInvite req = new TLRPC.TL_messages_deleteExportedChatInvite();
                                req.link = invite.link;
                                req.peer = MessagesController.getInstance(currentAccount).getInputPeer(-chatId);
                                ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                                    if (error == null) {
                                        if (inviteDelegate != null) {
                                            inviteDelegate.onLinkDeleted(invite);
                                        }
                                    }
                                }));
                            }
                            dismiss();
                        }
                    });
                    view.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                    break;
                case 4:
                    view = new TimerPrivacyCell(context);
                    CombinedDrawable combinedDrawable = new CombinedDrawable(new ColorDrawable(Theme.getColor(Theme.key_windowBackgroundGray)), Theme.getThemedDrawableByKey(context, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
                    combinedDrawable.setFullsize(true);
                    view.setBackground(combinedDrawable);
                    break;
                case 5:
                    FlickerLoadingView flickerLoadingView = new FlickerLoadingView(context);
                    flickerLoadingView.setIsSingleCell(true);
                    flickerLoadingView.setViewType(FlickerLoadingView.USERS2_TYPE);
                    flickerLoadingView.showDate(false);
                    flickerLoadingView.setPaddingLeft(dp(10));
                    view = flickerLoadingView;
                    break;
                case 6:
                    view = new View(context) {
                        @Override
                        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(dp(5), MeasureSpec.EXACTLY));
                        }
                    };
                    break;
                case 7:
                    view = new ShadowSectionCell(context, 12);
                    Drawable shadowDrawable = Theme.getThemedDrawableByKey(context, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow);
                    Drawable background = new ColorDrawable(Theme.getColor(Theme.key_windowBackgroundGray));
                    combinedDrawable = new CombinedDrawable(background, shadowDrawable, 0, 0);
                    combinedDrawable.setFullsize(true);
                    view.setBackgroundDrawable(combinedDrawable);
                    break;
                case 8:
                    view = new EmptyHintRow(context);
                    break;
                case 9:
                    view = new RevenueCell(context);
                    break;
            }
            view.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            switch (holder.getItemViewType()) {
                case 0:
                    GraySectionCell headerCell = (GraySectionCell) holder.itemView;
                    if (position == creatorHeaderRow) {
                        headerCell.setText(LocaleController.getString(R.string.LinkCreatedeBy));
                        headerCell.setRightText(null);
                    } else if (position == revenueHeaderRow) {
                        headerCell.setText(LocaleController.getString(R.string.LinkRevenue));
                        headerCell.setRightText(null);
                    } else if (position == joinedHeaderRow) {
                        if (invite.usage > 0) {
                            headerCell.setText(LocaleController.formatPluralString("PeopleJoined", invite.usage));
                        } else {
                            headerCell.setText(LocaleController.getString(invite.subscription_pricing != null ? R.string.NoOneSubscribed : R.string.NoOneJoined));
                        }
                        if (!invite.expired && !invite.revoked && invite.usage_limit > 0 && invite.usage > 0) {
                            headerCell.setRightText(LocaleController.formatPluralString("PeopleJoinedRemaining", invite.usage_limit - invite.usage));
                        } else {
                            headerCell.setRightText(null);
                        }
                    } else if (position == expiredHeaderRow) {
                        headerCell.setText(LocaleController.formatPluralString("PeopleSubscriptionExpired", invite.subscription_expired));
                        headerCell.setRightText(null);
                    } else if (position == requestedHeaderRow) {
                        headerCell.setText(LocaleController.formatPluralString("JoinRequests", invite.requested));
                        headerCell.setRightText(null);
                    }
                    break;
                case 1:
                    RevenueUserCell userCell = (RevenueUserCell) holder.itemView;
                    TLRPC.User user;
                    String role = null;
                    String status = null;
                    TLRPC.ChatParticipant part = null;
                    TLRPC.TL_chatInviteImporter invitedUser = null;
                    long userId;
                    if (position == creatorRow) {
                        userId = invite.admin_id;
                    } else {
                        int startRow = joinedStartRow;
                        List<TLRPC.TL_chatInviteImporter> usersList = joinedUsers;
                        if (expiredStartRow != -1 && position >= expiredStartRow) {
                            startRow = expiredStartRow;
                            usersList = expiredUsers;
                        }
                        if (requestedStartRow != -1 && position >= requestedStartRow) {
                            startRow = requestedStartRow;
                            usersList = requestedUsers;
                        }
                        invitedUser = usersList.get(position - startRow);
                        userId = invitedUser.user_id;
                    }
                    user = users.get(userId);
                    if (info != null && info.participants != null) {
                        for (int i = 0; i < info.participants.participants.size(); i++) {
                            if (info.participants.participants.get(i).user_id == userId) {
                                part = info.participants.participants.get(i);
                                break;
                            }
                        }
                    }
                    if (position == creatorRow) {
                        user = users.get(userId);
                        if (user == null) {
                            user = MessagesController.getInstance(currentAccount).getUser(invite.admin_id);
                        }
                        if (user != null) {
                            status = LocaleController.formatDateAudio(invite.date, false);
                        }
                    }
                    if (position == creatorRow && part != null) {
                        if (part instanceof TLRPC.TL_chatChannelParticipant) {
                            TLRPC.ChannelParticipant channelParticipant = ((TLRPC.TL_chatChannelParticipant) part).channelParticipant;
                            if (!TextUtils.isEmpty(channelParticipant.rank)) {
                                role = channelParticipant.rank;
                            } else {
                                if (channelParticipant instanceof TLRPC.TL_channelParticipantCreator) {
                                    role = LocaleController.getString(R.string.ChannelCreator);
                                } else if (channelParticipant instanceof TLRPC.TL_channelParticipantAdmin) {
                                    role = LocaleController.getString(R.string.ChannelAdmin);
                                } else {
                                    role = null;
                                }
                            }
                        } else {
                            if (part instanceof TLRPC.TL_chatParticipantCreator) {
                                role = LocaleController.getString(R.string.ChannelCreator);
                            } else if (part instanceof TLRPC.TL_chatParticipantAdmin) {
                                role = LocaleController.getString(R.string.ChannelAdmin);
                            } else {
                                role = null;
                            }
                        }
                    }
                    userCell.setAdminRole(role);
                    userCell.setData(user, null, status, 0, false);
                    if (position != creatorRow && invite.subscription_pricing != null && invitedUser != null) {
                        userCell.setRevenue(invite.subscription_pricing, invitedUser.date);
                    }
                    break;
                case 3:
                    LinkActionView actionView = (LinkActionView) holder.itemView;
                    actionView.setUsers(0, null);
                    actionView.setLink(invite.link);
                    actionView.setRevoke(invite.revoked);
                    actionView.setPermanent(invite.permanent);
                    actionView.setCanEdit(canEdit);
                    actionView.hideRevokeOption(!canEdit);
                    break;
                case 4:
                    TimerPrivacyCell privacyCell = (TimerPrivacyCell) holder.itemView;
                    privacyCell.cancelTimer();
                    privacyCell.timer = false;
                    privacyCell.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText4));
                    privacyCell.setFixedSize(0);
                    if (invite.revoked) {
                        privacyCell.setText(LocaleController.getString(R.string.LinkIsNoActive));
                    } else if (invite.expired) {
                        if (invite.usage_limit > 0 && invite.usage_limit == invite.usage) {
                            privacyCell.setText(LocaleController.getString(R.string.LinkIsExpiredLimitReached));
                        } else {
                            privacyCell.setText(LocaleController.getString(R.string.LinkIsExpired));
                            privacyCell.setTextColor(Theme.getColor(Theme.key_text_RedRegular));
                        }
                    } else if (invite.expire_date > 0) {
                        long currentTime = System.currentTimeMillis() + timeDif * 1000L;
                        long expireTime = invite.expire_date * 1000L;

                        long timeLeft = expireTime - currentTime;
                        if (timeLeft < 0) {
                            timeLeft = 0;
                        }
                        String time;
                        if (timeLeft > 86400000L) {
                            time = LocaleController.formatDateAudio(invite.expire_date, false);
                            privacyCell.setText(LocaleController.formatString("LinkExpiresIn", R.string.LinkExpiresIn, time));
                        } else {
                            int s = (int) ((timeLeft / 1000) % 60);
                            int m = (int) ((timeLeft / 1000 / 60) % 60);
                            int h = (int) ((timeLeft / 1000 / 60 / 60));
                            time = String.format(Locale.ENGLISH, "%02d", h) + String.format(Locale.ENGLISH, ":%02d", m) + String.format(Locale.ENGLISH, ":%02d", s);
                            privacyCell.timer = true;
                            privacyCell.runTimer();
                            privacyCell.setText(LocaleController.formatString("LinkExpiresInTime", R.string.LinkExpiresInTime, time));
                        }
                    } else {
                        privacyCell.setFixedSize(-1);
                        privacyCell.setText(null);
                    }
                    break;
                case 8:
                    EmptyHintRow emptyHintRow = (EmptyHintRow) holder.itemView;
                    if (invite.usage_limit > 0) {
                        emptyHintRow.textView.setText(LocaleController.formatPluralString("PeopleCanJoinViaLinkCount", invite.usage_limit));
                        emptyHintRow.textView.setVisibility(View.VISIBLE);
                    } else {
                        emptyHintRow.textView.setVisibility(View.GONE);
                    }
                    break;
                case 9:
                    ((RevenueCell) holder.itemView).set(invite.subscription_pricing, invite.usage);
                    break;
            }
        }

        @Override
        public int getItemCount() {
            return rowCount;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            int position = holder.getAdapterPosition();
            if (position == creatorRow) {
                if (invite.admin_id == UserConfig.getInstance(currentAccount).clientUserId) {
                    return false;
                }
                return true;
            } else if (position >= joinedStartRow && position < joinedEndRow || position >= requestedStartRow && position < requestedEndRow) {
                return true;
            }
            return false;
        }
    }

    private void updateLayout() {
        if (listView.getChildCount() <= 0) {
            listView.setTopGlowOffset(scrollOffsetY = listView.getPaddingTop());
            titleTextView.setTranslationY(scrollOffsetY);
            shadow.setTranslationY(scrollOffsetY);
            containerView.invalidate();
            return;
        }
        View child = listView.getChildAt(0);
        RecyclerListView.Holder holder = (RecyclerListView.Holder) listView.findContainingViewHolder(child);
        int top = child.getTop();
        int newOffset = 0;
        if (top >= 0 && holder != null && holder.getAdapterPosition() == 0) {
            newOffset = top;
            runShadowAnimation(false);
        } else {
            runShadowAnimation(true);
        }
        if (scrollOffsetY != newOffset) {
            listView.setTopGlowOffset(scrollOffsetY = newOffset);
            if (titleTextView != null) {
                titleTextView.setTranslationY(scrollOffsetY);
            }
            shadow.setTranslationY(scrollOffsetY);
            containerView.invalidate();
        }
    }

    private void runShadowAnimation(final boolean show) {
        if (show && shadow.getTag() != null || !show && shadow.getTag() == null) {
            shadow.setTag(show ? null : 1);
            if (show) {
                shadow.setVisibility(View.VISIBLE);
                titleTextView.setVisibility(View.VISIBLE);
            }
            if (shadowAnimation != null) {
                shadowAnimation.cancel();
            }
            shadowAnimation = new AnimatorSet();
            shadowAnimation.playTogether(ObjectAnimator.ofFloat(shadow, View.ALPHA, show ? 1.0f : 0.0f));
            if (!titleVisible) {
                shadowAnimation.playTogether(ObjectAnimator.ofFloat(titleTextView, View.ALPHA, show ? 1.0f : 0.0f));
            }
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

    public void loadUsers() {
        if (usersLoading) {
            return;
        }

        boolean hasMoreJoinedUsers = invite.usage > joinedUsers.size();
        boolean hasMoreExpiredUsers = invite.subscription_expired > expiredUsers.size();
        boolean hasMoreRequestedUsers = invite.request_needed && invite.requested > requestedUsers.size();
        boolean loadRequestedUsers;
        boolean loadExpiredUsers;
        if (hasMoreJoinedUsers) {
            loadRequestedUsers = false;
            loadExpiredUsers = false;
        } else if (hasMoreExpiredUsers) {
            loadRequestedUsers = false;
            loadExpiredUsers = true;
        } else if (hasMoreRequestedUsers) {
            loadRequestedUsers = true;
            loadExpiredUsers = false;
        } else {
            return;
        }

        final List<TLRPC.TL_chatInviteImporter> importersList = loadRequestedUsers ? requestedUsers : loadExpiredUsers ? expiredUsers : joinedUsers;
        TLRPC.TL_messages_getChatInviteImporters req = new TLRPC.TL_messages_getChatInviteImporters();
        req.flags |= 2;
        req.link = invite.link;
        req.peer = MessagesController.getInstance(UserConfig.selectedAccount).getInputPeer(-chatId);
        req.requested = loadRequestedUsers;
        req.subscription_expired = loadExpiredUsers;
        if (importersList.isEmpty()) {
            req.offset_user = new TLRPC.TL_inputUserEmpty();
        } else {
            TLRPC.TL_chatInviteImporter invitedUser = importersList.get(importersList.size() - 1);
            req.offset_user = MessagesController.getInstance(currentAccount).getInputUser(users.get(invitedUser.user_id));
            req.offset_date = invitedUser.date;
        }

        usersLoading = true;
        ConnectionsManager.getInstance(UserConfig.selectedAccount).sendRequest(req, (response, error) -> {
            AndroidUtilities.runOnUIThread(() -> {
                if (error == null) {
                    TLRPC.TL_messages_chatInviteImporters inviteImporters = (TLRPC.TL_messages_chatInviteImporters) response;
                    importersList.addAll(inviteImporters.importers);
                    for (int i = 0; i < inviteImporters.users.size(); i++) {
                        TLRPC.User user = inviteImporters.users.get(i);
                        users.put(user.id, user);
                    }
                    hasMore = loadRequestedUsers
                            ? importersList.size() < inviteImporters.count
                            : loadExpiredUsers
                            ? importersList.size() < inviteImporters.count || hasMoreRequestedUsers
                            : importersList.size() < inviteImporters.count || hasMoreRequestedUsers || hasMoreExpiredUsers;
                    updateRows();
                }
                usersLoading = false;
            });
        });
    }

    public void setInviteDelegate(InviteDelegate inviteDelegate) {
        this.inviteDelegate = inviteDelegate;
    }

    private class TimerPrivacyCell extends TextInfoPrivacyCell {

        Runnable timerRunnable = new Runnable() {
            @Override
            public void run() {
                if (listView != null && listView.getAdapter() != null) {
                    int p = listView.getChildAdapterPosition(TimerPrivacyCell.this);
                    if (p >= 0)
                        adapter.onBindViewHolder(listView.getChildViewHolder(TimerPrivacyCell.this), p);
                }
                AndroidUtilities.runOnUIThread(this);
            }
        };

        boolean timer;

        public TimerPrivacyCell(Context context) {
            super(context);
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            runTimer();
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            cancelTimer();
        }

        public void cancelTimer() {
            AndroidUtilities.cancelRunOnUIThread(timerRunnable);
        }

        public void runTimer() {
            cancelTimer();
            if (timer) {
                AndroidUtilities.runOnUIThread(timerRunnable, 500);
            }
        }
    }

    private class EmptyHintRow extends FrameLayout {

        TextView textView;

        public EmptyHintRow(@NonNull Context context) {
            super(context);
            textView = new TextView(context);
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            textView.setGravity(Gravity.CENTER_HORIZONTAL);
            addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, 60, 0, 60, 0));
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(dp(84), MeasureSpec.EXACTLY));
        }
    }

    public void setCanEdit(boolean canEdit) {
        this.canEdit = canEdit;
    }

    public interface InviteDelegate {
        void permanentLinkReplaced(TLRPC.TL_chatInviteExported oldLink, TLRPC.TL_chatInviteExported newLink);
        void linkRevoked(TLRPC.TL_chatInviteExported invite);
        void onLinkDeleted(TLRPC.TL_chatInviteExported invite);
        void onLinkEdited(TLRPC.TL_chatInviteExported invite);
    }

    private class RevenueUserCell extends UserCell {

        public final LinearLayout layout;
        public final TextView priceView;
        public final TextView periodView;

        public RevenueUserCell(Context context) {
            super(context, 6, 0, true);

            layout = new LinearLayout(context);
            layout.setOrientation(LinearLayout.VERTICAL);

            priceView = new TextView(context);
            priceView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            priceView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            priceView.setTypeface(AndroidUtilities.bold());
            layout.addView(priceView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.RIGHT));

            periodView = new TextView(context);
            periodView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2));
            periodView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            layout.addView(periodView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.RIGHT, 0, 1, 0, 0));

            addView(layout, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL | (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT), 18, 0, 18, 0));
        }

        public void setRevenue(TL_stars.TL_starsSubscriptionPricing pricing, int joined_date) {
            if (pricing == null) {
                priceView.setText(null);
                periodView.setText(null);
                setRightPadding(0, true, true);
            } else {
                final CharSequence amountText = StarsIntroActivity.replaceStarsWithPlain("⭐️" + pricing.amount, .7f);
                final CharSequence periodText = pricing.period == StarsController.PERIOD_MONTHLY ? LocaleController.getString(R.string.StarsParticipantSubscriptionPerMonth) : (pricing.period == StarsController.PERIOD_5MINUTES ? "per 5 minutes" : "per each minute");
                priceView.setText(amountText);
                periodView.setText(periodText);
                setRightPadding(
                    (int) Math.max(HintView2.measureCorrectly(amountText, priceView.getPaint()), HintView2.measureCorrectly(periodText, periodView.getPaint())),
                    true, true
                );
                statusTextView.setText(LocaleController.formatJoined(joined_date));
            }
        }

    }

    private class RevenueCell extends FrameLayout {

        public final ImageView imageView;
        public final TextView titleView;
        public final TextView subtitleView;

        public RevenueCell(Context context) {
            super(context);

            imageView = new ImageView(context);
            imageView.setBackground(Theme.createCircleDrawable(46, Theme.getColor(Theme.key_avatar_backgroundGreen), Theme.getColor(Theme.key_avatar_background2Green)));
            imageView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            imageView.setImageResource(R.drawable.large_income);
            imageView.setColorFilter(new PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN));
            addView(imageView, LayoutHelper.createFrame(46, 46, Gravity.LEFT | Gravity.CENTER_VERTICAL, 13, 0, 0, 0));

            titleView = new TextView(context);
            titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            titleView.setTypeface(AndroidUtilities.bold());
            titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            addView(titleView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 72, 9, 0, 0));

            subtitleView = new TextView(context);
            subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            subtitleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            addView(subtitleView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 72, 32, 0, 0));
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(
                MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(dp(58), MeasureSpec.EXACTLY)
            );
        }

        public void set(TL_stars.TL_starsSubscriptionPricing pricing, int count) {
            if (pricing == null) return;
            if (pricing.period == StarsController.PERIOD_MONTHLY) {
                titleView.setText(StarsIntroActivity.replaceStarsWithPlain(LocaleController.formatString(R.string.LinkRevenuePrice, pricing.amount) + (count > 0 ? " x " + count : ""), .8f));
                subtitleView.setText(count == 0 ? getString(R.string.NoOneSubscribed) : LocaleController.formatString(R.string.LinkRevenuePriceInfo, BillingController.getInstance().formatCurrency((long) (pricing.amount / 1000.0 * MessagesController.getInstance(currentAccount).starsUsdWithdrawRate1000 * count), "USD")));
            } else {
                final String period = pricing.period == StarsController.PERIOD_5MINUTES ? "5min" : "min";
                titleView.setText(StarsIntroActivity.replaceStarsWithPlain(String.format(Locale.US, "⭐%1$d/%2$s", pricing.amount, period) + (count > 0 ? " x " + count : ""), .8f));
                subtitleView.setText(count == 0 ? getString(R.string.NoOneSubscribed) : String.format(Locale.US, "you get approximately %1$s %2$s", BillingController.getInstance().formatCurrency((long) (pricing.amount / 1000.0 * MessagesController.getInstance(currentAccount).starsUsdWithdrawRate1000 * count), "USD"), "for " + period));
            }
        }

    }

    public static BottomSheet showSubscriptionSheet(
        Context context,
        int currentAccount,
        long dialogId,
        TL_stars.TL_starsSubscriptionPricing pricing,
        TLRPC.TL_chatInviteImporter importer,
        TLRPC.ChannelParticipant participant,
        Theme.ResourcesProvider resourcesProvider
    ) {
        final BottomSheet.Builder b = new BottomSheet.Builder(context, false, resourcesProvider);
        final BottomSheet[] sheet = new BottomSheet[1];

        LinearLayout linearLayout = new LinearLayout(context);
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        linearLayout.setPadding(dp(16), dp(20), dp(16), dp(4));
        linearLayout.setClipChildren(false);
        linearLayout.setClipToPadding(false);

        FrameLayout topView = new FrameLayout(context);
        linearLayout.addView(topView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.FILL_HORIZONTAL, 0, 0, 0, 10));

        BackupImageView imageView = new BackupImageView(context);
        imageView.setRoundRadius(dp(50));
        AvatarDrawable avatarDrawable = new AvatarDrawable();
        if (dialogId >= 0) {
            TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(dialogId);
            avatarDrawable.setInfo(user);
            imageView.setForUserOrChat(user, avatarDrawable);
        } else {
            TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-dialogId);
            avatarDrawable.setInfo(chat);
            imageView.setForUserOrChat(chat, avatarDrawable);
        }
        topView.addView(imageView, LayoutHelper.createFrame(100, 100, Gravity.CENTER));

        Drawable starBg = context.getResources().getDrawable(R.drawable.star_small_outline);
        starBg.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_dialogBackground, resourcesProvider), PorterDuff.Mode.SRC_IN));
        Drawable starFg = context.getResources().getDrawable(R.drawable.star_small_inner);

        ImageView starBgView = new ImageView(context);
        starBgView.setImageDrawable(starBg);
        topView.addView(starBgView, LayoutHelper.createFrame(28, 28, Gravity.CENTER));
        starBgView.setTranslationX(dp(34));
        starBgView.setTranslationY(dp(35));
        starBgView.setScaleX(1.1f);
        starBgView.setScaleY(1.1f);

        ImageView starFgView = new ImageView(context);
        starFgView.setImageDrawable(starFg);
        topView.addView(starFgView, LayoutHelper.createFrame(28, 28, Gravity.CENTER));
        starFgView.setTranslationX(dp(34));
        starFgView.setTranslationY(dp(35));

        TextView textView = new TextView(context);
        textView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider));
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        textView.setTypeface(AndroidUtilities.bold());
        textView.setGravity(Gravity.CENTER);
        textView.setText(getString(R.string.StarsSubscriptionTitle));
        linearLayout.addView(textView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 20, 0, 20, 4));

        textView = new TextView(context);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        textView.setGravity(Gravity.CENTER);
        textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText4, resourcesProvider));
        if (pricing.period == StarsController.PERIOD_MONTHLY) {
            textView.setText(StarsIntroActivity.replaceStarsWithPlain(formatString(R.string.StarsSubscriptionPrice, pricing.amount), .8f));
        } else {
            final String period = pricing.period == StarsController.PERIOD_5MINUTES ? "5min" : "min";
            textView.setText(StarsIntroActivity.replaceStarsWithPlain(String.format(Locale.US, "⭐%1$d/%2$s", pricing.amount, period), .8f));
        }
        linearLayout.addView(textView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 20, 0, 20, 4));

        textView = new TextView(context);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        textView.setGravity(Gravity.CENTER);
        textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText4, resourcesProvider));
        if (pricing.period == StarsController.PERIOD_MONTHLY) {
            textView.setText(formatString(R.string.StarsParticipantSubscriptionApproxMonth, BillingController.getInstance().formatCurrency((int) (pricing.amount / 1000.0 * MessagesController.getInstance(currentAccount).starsUsdWithdrawRate1000), "USD")));
        } else {
            final String period = pricing.period == StarsController.PERIOD_5MINUTES ? "5min" : "min";
            textView.setText(String.format(Locale.US, "appx. %1$s per %2$s", BillingController.getInstance().formatCurrency((int) (pricing.amount / 1000.0 * MessagesController.getInstance(currentAccount).starsUsdWithdrawRate1000), "USD"), period));
        }
        linearLayout.addView(textView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 20, 0, 20, 4));

        TableView tableView = new TableView(context, resourcesProvider);
        textView = new LinkSpanDrawable.LinksTextView(context, resourcesProvider);
        textView.setPadding(dp(12.66f), dp(9.33f), dp(12.66f), dp(9.33f));
        textView.setEllipsize(TextUtils.TruncateAt.END);
        textView.setTextColor(Theme.getColor(Theme.key_chat_messageLinkIn, resourcesProvider));
        textView.setLinkTextColor(Theme.getColor(Theme.key_chat_messageLinkIn, resourcesProvider));
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        textView.setSingleLine(true);
        ((LinkSpanDrawable.LinksTextView) textView).setDisablePaddingsOffsetY(true);
        AvatarSpan avatarSpan = new AvatarSpan(textView, currentAccount, 24);
        CharSequence username;
        boolean deleted = false;
        TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(importer.user_id);
        deleted = user == null;
        username = UserObject.getUserName(user);
        avatarSpan.setUser(user);
        SpannableStringBuilder ssb = new SpannableStringBuilder("x  " + username);
        ssb.setSpan(avatarSpan, 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        ssb.setSpan(new ClickableSpan() {
            @Override
            public void onClick(@NonNull View widget) {
                sheet[0].dismiss();
                BaseFragment lastFragment = LaunchActivity.getSafeLastFragment();
                if (lastFragment != null) {
                    lastFragment.presentFragment(ProfileActivity.of(importer.user_id));
                }
            }

            @Override
            public void updateDrawState(@NonNull TextPaint ds) {
                ds.setUnderlineText(false);
            }
        }, 3, ssb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        textView.setText(ssb);
        if (!deleted) {
            tableView.addRowUnpadded(getString(R.string.StarsParticipantSubscription), textView);
        }

        tableView.addRow(
            getString(R.string.StarsParticipantSubscriptionStart),
            LocaleController.formatString(R.string.formatDateAtTime, LocaleController.getInstance().getFormatterGiveawayCard().format(new Date(importer.date * 1000L)), LocaleController.getInstance().getFormatterDay().format(new Date(importer.date * 1000L)))
        );
        final int now = ConnectionsManager.getInstance(currentAccount).getCurrentTime();
        if (participant != null) {
            tableView.addRow(
                getString(participant.subscription_until_date > now ? R.string.StarsParticipantSubscriptionRenews : R.string.StarsParticipantSubscriptionExpired),
                LocaleController.formatString(R.string.formatDateAtTime, LocaleController.getInstance().getFormatterGiveawayCard().format(new Date(participant.subscription_until_date * 1000L)), LocaleController.getInstance().getFormatterDay().format(new Date(participant.subscription_until_date * 1000L)))
            );
        }
        linearLayout.addView(tableView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 17, 0, 0));

        textView = new LinkSpanDrawable.LinksTextView(context, resourcesProvider);
        textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2, resourcesProvider));
        textView.setLinkTextColor(Theme.getColor(Theme.key_chat_messageLinkIn, resourcesProvider));
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        textView.setText(AndroidUtilities.replaceSingleTag(getString(R.string.StarsTransactionTOS), () -> {
            Browser.openUrl(context, getString(R.string.StarsTOSLink));
        }));
        textView.setGravity(Gravity.CENTER);
        linearLayout.addView(textView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 14, 15, 14, 15));

        ButtonWithCounterView button = new ButtonWithCounterView(context, true, resourcesProvider);
        button.setText(getString(R.string.OK), false);
        linearLayout.addView(button, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48));
        button.setOnClickListener(v -> {
            sheet[0].dismiss();
        });

        b.setCustomView(linearLayout);
        sheet[0] = b.create();
        sheet[0].useBackgroundTopPadding = false;

        sheet[0].fixNavigationBar();
        sheet[0].show();
        return sheet[0];
    }
}
