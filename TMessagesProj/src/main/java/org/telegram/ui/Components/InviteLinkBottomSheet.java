package org.telegram.ui.Components;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.UserCell;
import org.telegram.ui.LinkEditActivity;
import org.telegram.ui.ManageLinksActivity;
import org.telegram.ui.ProfileActivity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

public class InviteLinkBottomSheet extends BottomSheet {

    TLRPC.TL_chatInviteExported invite;
    HashMap<Long, TLRPC.User> users;
    TLRPC.ChatFull info;

    int creatorHeaderRow;
    int creatorRow;
    int dividerRow;
    int divider2Row;
    int divider3Row;
    int joinedHeaderRow;
    int joinedStartRow;
    int joinedEndRow;
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
                int top = scrollOffsetY - backgroundPaddingTop - AndroidUtilities.dp(8);
                int height = getMeasuredHeight() + AndroidUtilities.dp(36) + backgroundPaddingTop;
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
                    rect.set(backgroundPaddingLeft, backgroundPaddingTop + top, getMeasuredWidth() - backgroundPaddingLeft, backgroundPaddingTop + top + AndroidUtilities.dp(24));
                    canvas.drawRoundRect(rect, AndroidUtilities.dp(12) * radProgress, AndroidUtilities.dp(12) * radProgress, Theme.dialogs_onlineCirclePaint);
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
        frameLayoutParams.topMargin = AndroidUtilities.dp(48);
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
                    if (padding < lastH - contentSize + AndroidUtilities.dp(60)) {
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
            boolean isRequestedUserRow = position >= requestedStartRow && position < requestedEndRow;
            if ((position == creatorRow || isJoinedUserRow || isRequestedUserRow) && users != null) {
                long userId = invite.admin_id;
                if (isJoinedUserRow) {
                    userId = joinedUsers.get(position - joinedStartRow).user_id;
                } else if (isRequestedUserRow) {
                    userId = requestedUsers.get(position - requestedStartRow).user_id;
                }
                TLRPC.User user = users.get(userId);
                if (user != null) {
                    MessagesController.getInstance(UserConfig.selectedAccount).putUser(user, false);
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
        });

        titleTextView = new TextView(context);
        titleTextView.setLines(1);
        titleTextView.setSingleLine(true);
        titleTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        titleTextView.setEllipsize(TextUtils.TruncateAt.END);
        titleTextView.setPadding(AndroidUtilities.dp(23), 0, AndroidUtilities.dp(23), 0);
        titleTextView.setGravity(Gravity.CENTER_VERTICAL);
        titleTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        if (!permanent) {
            if (invite.expired) {
                titleTextView.setText(LocaleController.getString("ExpiredLink", R.string.ExpiredLink));
            } else if (invite.revoked) {
                titleTextView.setText(LocaleController.getString("RevokedLink", R.string.RevokedLink));
            } else {
                titleTextView.setText(LocaleController.getString("InviteLink", R.string.InviteLink));
            }
            titleVisible = true;
        } else {
            titleTextView.setText(LocaleController.getString("InviteLink", R.string.InviteLink));
            titleVisible = false;
            titleTextView.setVisibility(View.INVISIBLE);
            titleTextView.setAlpha(0f);
        }
        if (!TextUtils.isEmpty(invite.title)) {
            SpannableStringBuilder builder = new SpannableStringBuilder(invite.title);
            Emoji.replaceEmoji(builder, titleTextView.getPaint().getFontMetricsInt(), (int) titleTextView.getPaint().getTextSize(), false);
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
            AndroidUtilities.runOnUIThread(new Runnable() {
                @Override
                public void run() {
                    if (error == null) {
                        TLRPC.Vector vector = (TLRPC.Vector) response;
                        TLRPC.User user = (TLRPC.User) vector.objects.get(0);
                        users.put(invite.admin_id, user);
                        adapter.notifyDataSetChanged();
                    }
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

        if (!permanent) {
            linkActionRow = rowCount++;
            linkInfoRow = rowCount++;
        }
        creatorHeaderRow = rowCount++;
        creatorRow = rowCount++;
        emptyView = rowCount++;

        boolean needUsers = invite.usage > 0 || invite.usage_limit > 0 || invite.requested > 0;
        boolean needLoadUsers = invite.usage > joinedUsers.size() || invite.request_needed && invite.requested > requestedUsers.size();
        boolean usersLoaded = false;
        if (!joinedUsers.isEmpty()) {
            dividerRow = rowCount++;
            joinedHeaderRow = rowCount++;
            joinedStartRow = rowCount;
            rowCount += joinedUsers.size();
            joinedEndRow = rowCount;
            emptyView2 = rowCount++;
            usersLoaded = true;
        }
        if (!requestedUsers.isEmpty()) {
            divider2Row = rowCount++;
            requestedHeaderRow = rowCount++;
            requestedStartRow = rowCount;
            rowCount += requestedUsers.size();
            requestedEndRow = rowCount;
            emptyView3 = rowCount++;
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
            divider3Row = rowCount++;
        }

        adapter.notifyDataSetChanged();
    }

    private class Adapter extends RecyclerListView.SelectionAdapter {

        @Override
        public int getItemViewType(int position) {
            if (position == creatorHeaderRow || position == requestedHeaderRow || position == joinedHeaderRow) {
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
                    HeaderCell headerCell = new HeaderCell(context, Theme.key_windowBackgroundWhiteBlueHeader, 21, 15, true);
                    headerCell.getTextView2().setTextColor(Theme.getColor(Theme.key_text_RedRegular));
                    headerCell.getTextView2().setTextSize(15);
                    headerCell.getTextView2().setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
                    view = headerCell;
                    break;
                case 1:
                    view = new UserCell(context, 12, 0, true);
                    break;
                case 2:
                    view = new ShadowSectionCell(context, 12, Theme.getColor(Theme.key_windowBackgroundGray));
                    break;
                case 3:
                    LinkActionView linkActionView = new LinkActionView(context, fragment, InviteLinkBottomSheet.this, chatId, false, isChannel);
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
                    flickerLoadingView.setPaddingLeft(AndroidUtilities.dp(10));
                    view = flickerLoadingView;
                    break;
                case 6:
                    view = new View(context) {
                        @Override
                        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(5), MeasureSpec.EXACTLY));
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
            }
            view.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            switch (holder.getItemViewType()) {
                case 0:
                    HeaderCell headerCell = (HeaderCell) holder.itemView;
                    if (position == creatorHeaderRow) {
                        headerCell.setText(LocaleController.getString("LinkCreatedeBy", R.string.LinkCreatedeBy));
                        headerCell.setText2(null);
                    } else if (position == joinedHeaderRow) {
                        if (invite.usage > 0) {
                            headerCell.setText(LocaleController.formatPluralString("PeopleJoined", invite.usage));
                        } else {
                            headerCell.setText(LocaleController.getString("NoOneJoined", R.string.NoOneJoined));
                        }
                        if (!invite.expired && !invite.revoked && invite.usage_limit > 0 && invite.usage > 0) {
                            headerCell.setText2(LocaleController.formatPluralString("PeopleJoinedRemaining", invite.usage_limit - invite.usage));
                        } else {
                            headerCell.setText2(null);
                        }
                    } else if (position == requestedHeaderRow) {
                        headerCell.setText(LocaleController.formatPluralString("JoinRequests", invite.requested));
                    }
                    break;
                case 1:
                    UserCell userCell = (UserCell) holder.itemView;
                    TLRPC.User user;
                    String role = null;
                    String status = null;
                    if (position == creatorRow) {
                        user = users.get(invite.admin_id);
                        if (user == null) {
                            user = MessagesController.getInstance(currentAccount).getUser(invite.admin_id);
                        }
                        if (user != null) {
                            status = LocaleController.formatDateAudio(invite.date, false);
                        }
                        if (info != null && user != null && info.participants != null) {
                            for (int i = 0; i < info.participants.participants.size(); i++) {
                                if (info.participants.participants.get(i).user_id == user.id) {
                                    TLRPC.ChatParticipant part = info.participants.participants.get(i);

                                    if (part instanceof TLRPC.TL_chatChannelParticipant) {
                                        TLRPC.ChannelParticipant channelParticipant = ((TLRPC.TL_chatChannelParticipant) part).channelParticipant;
                                        if (!TextUtils.isEmpty(channelParticipant.rank)) {
                                            role = channelParticipant.rank;
                                        } else {
                                            if (channelParticipant instanceof TLRPC.TL_channelParticipantCreator) {
                                                role = LocaleController.getString("ChannelCreator", R.string.ChannelCreator);
                                            } else if (channelParticipant instanceof TLRPC.TL_channelParticipantAdmin) {
                                                role = LocaleController.getString("ChannelAdmin", R.string.ChannelAdmin);
                                            } else {
                                                role = null;
                                            }
                                        }
                                    } else {
                                        if (part instanceof TLRPC.TL_chatParticipantCreator) {
                                            role = LocaleController.getString("ChannelCreator", R.string.ChannelCreator);
                                        } else if (part instanceof TLRPC.TL_chatParticipantAdmin) {
                                            role = LocaleController.getString("ChannelAdmin", R.string.ChannelAdmin);
                                        } else {
                                            role = null;
                                        }
                                    }
                                    break;
                                }
                            }
                        }
                    } else {
                        int startRow = joinedStartRow;
                        List<TLRPC.TL_chatInviteImporter> usersList = joinedUsers;
                        if (requestedStartRow != -1 && position >= requestedStartRow) {
                            startRow = requestedStartRow;
                            usersList = requestedUsers;
                        }
                        TLRPC.TL_chatInviteImporter invitedUser = usersList.get(position - startRow);
                        user = users.get(invitedUser.user_id);
                    }
                    userCell.setAdminRole(role);
                    userCell.setData(user, null, status, 0, false);
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
                        privacyCell.setText(LocaleController.getString("LinkIsNoActive", R.string.LinkIsNoActive));
                    } else if (invite.expired) {
                        if (invite.usage_limit > 0 && invite.usage_limit == invite.usage) {
                            privacyCell.setText(LocaleController.getString("LinkIsExpiredLimitReached", R.string.LinkIsExpiredLimitReached));
                        } else {
                            privacyCell.setText(LocaleController.getString("LinkIsExpired", R.string.LinkIsExpired));
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
                        privacyCell.setFixedSize(12);
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
        boolean hasMoreRequestedUsers = invite.request_needed && invite.requested > requestedUsers.size();
        boolean loadRequestedUsers;
        if (hasMoreJoinedUsers) {
            loadRequestedUsers = false;
        } else if (hasMoreRequestedUsers) {
            loadRequestedUsers = true;
        } else {
            return;
        }

        final List<TLRPC.TL_chatInviteImporter> importersList = loadRequestedUsers ? requestedUsers : joinedUsers;
        TLRPC.TL_messages_getChatInviteImporters req = new TLRPC.TL_messages_getChatInviteImporters();
        req.flags |= 2;
        req.link = invite.link;
        req.peer = MessagesController.getInstance(UserConfig.selectedAccount).getInputPeer(-chatId);
        req.requested = loadRequestedUsers;
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
                            : importersList.size() < inviteImporters.count || hasMoreRequestedUsers;
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
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(84), MeasureSpec.EXACTLY));
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
}
