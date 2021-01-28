package org.telegram.ui.Components;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.RectF;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
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
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.UserCell;
import org.telegram.ui.ManageLinksActivity;
import org.telegram.ui.ProfileActivity;

import java.util.ArrayList;
import java.util.HashMap;

public class InviteLinkBottomSheet extends BottomSheet {

    TLRPC.TL_chatInviteExported invite;
    HashMap<Integer, TLRPC.User> users;
    TLRPC.ChatFull info;

    int creatorHeaderRow;
    int creatorRow;
    int dividerRow;
    int divider2Row;
    int usersHeaderRow;
    int usersStartRow;
    int usersEndRow;
    int linkActionRow;
    int linkInfoRow;
    int loadingRow;
    int emptyView;
    int emptyView2;

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

    ArrayList<TLRPC.TL_chatInviteImporter> invitedUsers = new ArrayList<>();

    private int chatId;

    public InviteLinkBottomSheet(Context context, TLRPC.TL_chatInviteExported invite, TLRPC.ChatFull info, HashMap<Integer, TLRPC.User> users, BaseFragment fragment, int chatId, boolean permanent) {
        super(context, false);
        this.invite = invite;
        this.users = users;
        this.fragment = fragment;
        this.info = info;
        this.chatId = chatId;
        this.permanent = permanent;

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
                    int color1 = Theme.getColor(Theme.key_dialogBackground);
                    int finalColor = Color.argb(0xff, (int) (Color.red(color1) * 0.8f), (int) (Color.green(color1) * 0.8f), (int) (Color.blue(color1) * 0.8f));
                    Theme.dialogs_onlineCirclePaint.setColor(finalColor);
                    canvas.drawRect(backgroundPaddingLeft, AndroidUtilities.statusBarHeight - statusBarHeight, getMeasuredWidth() - backgroundPaddingLeft, AndroidUtilities.statusBarHeight, Theme.dialogs_onlineCirclePaint);
                }
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
        listView.setOnItemClickListener(new RecyclerListView.OnItemClickListener() {
            @Override
            public void onItemClick(View view, int position) {
                if (position == creatorRow || (position >= usersStartRow && position < usersEndRow)) {
                    TLRPC.User user;
                    if (position == creatorRow) {
                        user = users.get(invite.admin_id);
                    } else {
                        TLRPC.TL_chatInviteImporter invitedUser = invitedUsers.get(position - usersStartRow);
                        user = users.get(invitedUser.user_id);
                    }
                    if (user != null) {
                        Bundle bundle = new Bundle();
                        bundle.putInt("user_id", user.id);
                        MessagesController.getInstance(UserConfig.selectedAccount).putUser(user, false);
                        ProfileActivity profileActivity = new ProfileActivity(bundle);
                        fragment.presentFragment(profileActivity);
                        dismiss();
                    }
                }
            }
        });
        containerView.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT, 0, permanent ? 0 : 48, 0, 0));

        if (!permanent) {
            titleTextView = new TextView(context);
            titleTextView.setLines(1);
            titleTextView.setSingleLine(true);
            titleTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
            titleTextView.setEllipsize(TextUtils.TruncateAt.END);
            titleTextView.setPadding(AndroidUtilities.dp(18), 0, AndroidUtilities.dp(18), 0);
            titleTextView.setGravity(Gravity.CENTER_VERTICAL);
            titleTextView.setText(invite.revoked ? LocaleController.getString("RevokedLink", R.string.RevokedLink) : LocaleController.getString("InviteLink", R.string.InviteLink));
            titleTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            containerView.addView(titleTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 50, Gravity.LEFT | Gravity.TOP, 0, 0, 40, 0));
        }

        updateRows();
        loadUsers();
        if (users.get(invite.admin_id) == null) {
            loadCreator();
        }

        updateColors();
    }

    public void updateColors() {
        if (titleTextView != null) {
            titleTextView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
            titleTextView.setLinkTextColor(Theme.getColor(Theme.key_dialogTextLink));
            titleTextView.setHighlightColor(Theme.getColor(Theme.key_dialogLinkSelection));
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

    private void updateColorForView(View view) {
        if (view instanceof HeaderCell) {
            ((HeaderCell) view).getTextView().setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueHeader));
        } else if (view instanceof LinkActionView){
            ((LinkActionView) view).updateColors();
        } else if (view instanceof TextInfoPrivacyCell) {
            CombinedDrawable combinedDrawable = new CombinedDrawable(new ColorDrawable(Theme.getColor(Theme.key_windowBackgroundGray)), Theme.getThemedDrawable(view.getContext(), R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
            combinedDrawable.setFullsize(true);
            view.setBackground(combinedDrawable);
            ((TextInfoPrivacyCell) view).setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText4));
        } else if (view instanceof UserCell) {
            ((UserCell) view).update(0);
        }
        RecyclerView.ViewHolder holder = listView.getChildViewHolder(view);
        if (holder != null){
            if (holder.getItemViewType() == 7) {
                Drawable shadowDrawable = Theme.getThemedDrawable(view.getContext(), R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow);
                Drawable background = new ColorDrawable(Theme.getColor(Theme.key_windowBackgroundGray));
                CombinedDrawable combinedDrawable = new CombinedDrawable(background, shadowDrawable, 0, 0);
                combinedDrawable.setFullsize(true);
                view.setBackgroundDrawable(combinedDrawable);
            } else if (holder.getItemViewType() == 2) {
                Drawable shadowDrawable = Theme.getThemedDrawable(view.getContext(), R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow);
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
        usersHeaderRow = -1;
        usersStartRow = -1;
        usersEndRow = -1;
        emptyView2 = -1;
        linkActionRow = -1;
        linkInfoRow = -1;


        if (!permanent) {
            linkActionRow = rowCount++;
            linkInfoRow = rowCount++;
        }
        creatorHeaderRow = rowCount++;
        creatorRow = rowCount++;
        emptyView = rowCount++;

        if (invite.usage > 0) {
            dividerRow = rowCount++;
            usersHeaderRow = rowCount++;
            if (!invitedUsers.isEmpty()) {
                usersStartRow = rowCount;
                rowCount += invitedUsers.size();
                usersEndRow = rowCount;
            } else {
                loadingRow = rowCount++;
            }
            emptyView2 = rowCount++;
        }
        divider2Row = rowCount++;

        adapter.notifyDataSetChanged();
    }

    private class Adapter extends RecyclerListView.SelectionAdapter {

        @Override
        public int getItemViewType(int position) {
            if (position == creatorHeaderRow) {
                return 0;
            } else if (position == creatorRow || position >= usersStartRow && position < usersEndRow) {
                return 1;
            } else if (position == dividerRow) {
                return 2;
            } else if (position == linkActionRow) {
                return 3;
            } else if (position == linkInfoRow) {
                return 4;
            } else if (position == loadingRow) {
                return 5;
            } else if (position == emptyView || position == emptyView2) {
                return 6;
            } else if (position == divider2Row) {
                return 7;
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
                    view = new HeaderCell(context);
                    break;
                case 1:
                    view = new UserCell(context, 12, 0, true);
                    break;
                case 2:
                    view = new ShadowSectionCell(context, 12, Theme.getColor(Theme.key_windowBackgroundGray));
                    break;
                case 3:
                    LinkActionView linkActionView = new LinkActionView(context, fragment, InviteLinkBottomSheet.this, chatId, false);
                    view = linkActionView;
                    linkActionView.setDelegate(new LinkActionView.Delegate() {
                        @Override
                        public void revokeLink() {
                            if (fragment instanceof ManageLinksActivity) {
                                ((ManageLinksActivity) fragment).revokeLink(invite);
                            }
                            dismiss();
                        }

                        @Override
                        public void editLink() {
                            if (fragment instanceof ManageLinksActivity) {
                                ((ManageLinksActivity) fragment).editLink(invite);
                            }
                            dismiss();
                        }

                        @Override
                        public void removeLink() {
                            if (fragment instanceof ManageLinksActivity) {
                                ((ManageLinksActivity) fragment).deleteLink(invite);
                            }
                            dismiss();
                        }
                    });
                    view.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                    break;
                case 4:
                    view = new TextInfoPrivacyCell(context);
                    CombinedDrawable combinedDrawable = new CombinedDrawable(new ColorDrawable(Theme.getColor(Theme.key_windowBackgroundGray)), Theme.getThemedDrawable(context, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
                    combinedDrawable.setFullsize(true);
                    view.setBackground(combinedDrawable);
                    break;
                case 5:
                    FlickerLoadingView flickerLoadingView = new FlickerLoadingView(context);
                    flickerLoadingView.setIsSingleCell(true);
                    flickerLoadingView.setViewType(FlickerLoadingView.USERS2_TYPE);
                    flickerLoadingView.showDate(false);
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
                    Drawable shadowDrawable = Theme.getThemedDrawable(context, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow);
                    Drawable background = new ColorDrawable(Theme.getColor(Theme.key_windowBackgroundGray));
                    combinedDrawable = new CombinedDrawable(background, shadowDrawable, 0, 0);
                    combinedDrawable.setFullsize(true);
                    view.setBackgroundDrawable(combinedDrawable);
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
                    } else if (position == usersHeaderRow) {
                        headerCell.setText(LocaleController.formatPluralString("PeopleJoined", invite.usage));
                    }
                    break;
                case 1:
                    UserCell userCell = (UserCell) holder.itemView;
                    TLRPC.User user;
                    String role = null;
                    String status = null;
                    if (position == creatorRow) {
                        user = users.get(invite.admin_id);
                        if (user != null) {
                            status = LocaleController.formatDateAudio(invite.date, false);
                        }
                        if (info != null && user != null) {
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
                        TLRPC.TL_chatInviteImporter invitedUser = invitedUsers.get(position - usersStartRow);
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
                    break;
                case 4:
                    TextInfoPrivacyCell privacyCell = (TextInfoPrivacyCell) holder.itemView;
                    if (invite.revoked) {
                        privacyCell.setText(LocaleController.getString("LinkIsNoActive", R.string.LinkIsNoActive));
                    } else if (invite.expired) {
                        privacyCell.setText(LocaleController.getString("LinkIsExpired", R.string.LinkIsExpired));
                    } else if (invite.expire_date > 0) {
                        privacyCell.setText(LocaleController.formatString("LinkExpiresIn", R.string.LinkExpiresIn, LocaleController.formatDateAudio(invite.expire_date, false)));
                    } else {
                        privacyCell.setText(null);
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
            if (position == creatorRow || (position >= usersStartRow && position < usersEndRow)) {
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

    public void loadUsers() {
        if (invite.usage <= 0 || usersLoading) {
            return;
        }
        TLRPC.TL_messages_getChatInviteImporters req = new TLRPC.TL_messages_getChatInviteImporters();
        req.link = invite.link;
        req.peer = MessagesController.getInstance(UserConfig.selectedAccount).getInputPeer(-chatId);
        if (invitedUsers.isEmpty()) {
            req.offset_user = new TLRPC.TL_inputUserEmpty();
        } else {
            TLRPC.TL_chatInviteImporter invitedUser = invitedUsers.get(invitedUsers.size() - 1);
            req.offset_user = MessagesController.getInstance(currentAccount).getInputUser(users.get(invitedUser.user_id));
            req.offset_date = invitedUser.date;
        }
        usersLoading = true;
        ConnectionsManager.getInstance(UserConfig.selectedAccount).sendRequest(req, (response, error) -> {
            AndroidUtilities.runOnUIThread(() -> {
                if (error == null) {
                    TLRPC.TL_messages_chatInviteImporters inviteImporters = (TLRPC.TL_messages_chatInviteImporters) response;

                    invitedUsers.addAll(inviteImporters.importers);

                    for (int i = 0; i < inviteImporters.users.size(); i++) {
                        TLRPC.User user = inviteImporters.users.get(i);
                        users.put(user.id, user);
                    }
                    hasMore = invitedUsers.size() < inviteImporters.count;
                    updateRows();
                }
                usersLoading = false;
            });
        });
    }
}
