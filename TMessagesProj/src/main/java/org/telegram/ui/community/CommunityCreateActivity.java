package org.telegram.ui.community;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.formatPluralString;
import static org.telegram.messenger.LocaleController.getString;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.utils.DrawableUtils;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BackDrawable;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.ProfileSearchCell;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;
import org.telegram.ui.Components.blur3.BlurredBackgroundDrawableViewFactory;
import org.telegram.ui.Components.blur3.drawable.color.impl.BlurredBackgroundProviderImpl;
import org.telegram.ui.Components.blur3.source.BlurredBackgroundSourceColor;
import org.telegram.ui.community.sheet.CommunityAddOptionsSheet;

import java.util.ArrayList;

public class CommunityCreateActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {
    private long dialogId;
    private TLRPC.Chat currentChat;
    private TLRPC.User currentUser;

    private FrameLayout containerView;
    private UniversalRecyclerView listView;
    private CommunityHeaderView communityHeaderView;
    private ArrayList<TLRPC.Chat> joinedCommunities;

    private NotificationCenter.ObserversGroup observersGroup;

    public CommunityCreateActivity(Bundle args) {
        super(args);
    }

    @Override
    public boolean onFragmentCreate() {
        dialogId = arguments.getLong("dialog_id", 0);
        currentChat = getMessagesController().getChat(-dialogId);
        currentUser = getMessagesController().getUser(dialogId);

        joinedCommunities = getMessagesController().getJoinedCommunities();
        getMessagesController().fetchJoinedCommunities(c -> {
            final boolean animated = joinedCommunities == null || joinedCommunities.isEmpty();
            joinedCommunities = c;
            if (listView != null) {
                listView.adapter.update(animated);
            }
        }, classGuid);

        observersGroup = getNotificationCenter().createObserversGroup(this)
            .add(NotificationCenter.chatInfoDidLoad);

        return super.onFragmentCreate();
    }

    @Override
    public View createView(Context context) {
        setHasOwnBackground(true);

        actionBar.setAddToContainer(false);
        actionBar.setAllowOverlayTitle(false);
        actionBar.setBackButtonDrawable(new BackDrawable(false));
        actionBar.setAllowOverlayTitle(true);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });
        BlurredBackgroundSourceColor sourceColor = new BlurredBackgroundSourceColor();
        sourceColor.setColor(getThemedColor(Theme.key_windowBackgroundWhite));
        BlurredBackgroundDrawableViewFactory factory = new BlurredBackgroundDrawableViewFactory(sourceColor);
        actionBar.setBackground(null);
        actionBar.setupGlass(factory, BlurredBackgroundProviderImpl.topPanelChatActivity(resourceProvider));
        actionBar.setGlassOnlyBack();

        containerView = new FrameLayout(context);
        containerView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));


        communityHeaderView = new CommunityHeaderView(context, resourceProvider);
        communityHeaderView.setTitle(getString(R.string.CommunityTitle));
        communityHeaderView.setSubtitle(getString(currentUser != null ?
            R.string.CommunityDescriptionBot : ChatObject.isChannelAndNotMegaGroup(currentChat) ?
            R.string.CommunityDescriptionChannel :
            R.string.CommunityDescriptionGroup));
        communityHeaderView.setTag(RecyclerListView.TAG_NOT_SECTION);

        if (currentUser != null) {
            communityHeaderView.avatarView.setForUserOrChat(currentUser, new AvatarDrawable(currentUser));
        } else if (currentChat != null) {
            communityHeaderView.avatarView.setForUserOrChat(currentChat, new AvatarDrawable(currentChat));
        }

        listView = new UniversalRecyclerView(this, this::fillItems, this::onClick, this::onLongClick);
        listView.setClipToPadding(false);
        listView.adapter.setApplyBackground(false);
        listView.setSections();
        containerView.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        containerView.addView(actionBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP));

        return fragmentView = containerView;
    }

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asCustomShadow(0, communityHeaderView));
        items.add(UItem.asButton(1, R.drawable.msg_groups_create, getString(R.string.CommunityCreateCommunity)).accent());
        items.add(UItem.asSpace(2, dp(14)));

        if (joinedCommunities != null && !joinedCommunities.isEmpty()) {
            items.add(UItem.asHeader(3, getString(R.string.CommunityAddToExistingCommunity)));
            for (TLRPC.Chat community : joinedCommunities) {
                TLRPC.ChatFull chatFull = getMessagesController().getChatFull(community.id);
                UItem item = UItem.asProfileCell(community);
                item.id = Long.hashCode(community.id);
                item.subtext = chatFull != null ? formatPluralString("Chats", chatFull.linked_peers != null ? chatFull.linked_peers.size() : 0) : getString(R.string.Loading);
                items.add(item);
            }
        }
    }

    private void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == 1) {
            AlertsCreator.createSimpleTextInputAlert(getContext(), this,
                getString(R.string.CommunityNewCommunityTitle), null,
                getString(R.string.CommunityNewCommunityNameHint), null, Integer.MAX_VALUE,
                getString(R.string.Create), resourceProvider, name -> {
                    final TLRPC.Chat chat = getMessagesController().getChat(-dialogId);
                    showDialog(new CommunityAddOptionsSheet(getContext(), null, dialogId,
                        isHidden -> createNewCommunity(name, isHidden)));
            });
        }
        if (item.object instanceof TLRPC.Chat) {
            final TLRPC.Chat community = (TLRPC.Chat) item.object;
            final TLRPC.Chat chat = getMessagesController().getChat(-dialogId);
            showDialog(new CommunityAddOptionsSheet(getContext(), community, dialogId,
                isHidden -> linkToCommunity(community.id, isHidden)));
        }
    }


    private void createNewCommunity(String name, boolean isHidden) {
        if (!ChatObject.isChannel(currentChat) && currentUser == null) {
            final AlertDialog progressDialog = new AlertDialog(getContext(), AlertDialog.ALERT_TYPE_SPINNER);
            progressDialog.showDelayed(250);
            getMessagesController().convertToMegaGroup(getParentActivity(), -dialogId, this, param -> {
                progressDialog.dismiss();
                if (param == 0) {
                    return;
                }
                dialogId = -param;
                currentChat = getMessagesController().getChat(param);
                createNewCommunity(name, isHidden);
            });
        } else {
            getMessagesController().createCommunity(name, dialogId, isHidden, (res, err) -> {
                if (err != null) {
                    BulletinFactory.of(CommunityCreateActivity.this).showForError(err);
                    return;
                }
                CommunityUtils.onCommunityLinkSuccess(CommunityCreateActivity.this, dialogId, CommunityUtils.STATUS_CREATED);
            });
        }
    }

    private void linkToCommunity(long communityId, boolean isHidden) {
        if (!ChatObject.isChannel(currentChat) && currentUser == null) {
            final AlertDialog progressDialog = new AlertDialog(getContext(), AlertDialog.ALERT_TYPE_SPINNER);
            progressDialog.showDelayed(250);
            getMessagesController().convertToMegaGroup(getParentActivity(), -dialogId, this, param -> {
                progressDialog.dismiss();
                if (param == 0) {
                    return;
                }
                dialogId = -param;
                currentChat = getMessagesController().getChat(param);
                linkToCommunity(communityId, isHidden);
            });
        } else {
            CommunityUtils.linkToCommunityWithoutConvert(CommunityCreateActivity.this, currentAccount, -dialogId, communityId, isHidden);
        }
    }


    private boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }

    @Override
    public boolean isSupportEdgeToEdge() {
        return true;
    }

    @Override
    public void onInsets(int left, int top, int right, int bottom) {
        super.onInsets(left, top, right, bottom);
        listView.setPadding(0, top, 0, bottom);
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.chatInfoDidLoad) {
            TLRPC.ChatFull chatFull = (TLRPC.ChatFull) args[0];
            final int viewId = Long.hashCode(chatFull.id);
            final View view = listView.findViewByItemId(viewId);
            if (view instanceof ProfileSearchCell) {
                ProfileSearchCell cell = (ProfileSearchCell) view;
                cell.setSubLabel(formatPluralString("Chats", chatFull.linked_peers != null ? chatFull.linked_peers.size() : 0));
            } else {
                listView.adapter.update(false);
            }
        }
    }

    @Override
    public void onFragmentDestroy() {
        if (observersGroup != null) {
            observersGroup.removeAllObservers();
            observersGroup = null;
        }
        super.onFragmentDestroy();
    }

    @SuppressLint("ViewConstructor")
    public static class CommunityHeaderView extends FrameLayout implements Theme.Colorable {
        public final BackupImageView avatarView;
        private final Theme.ResourcesProvider resourcesProvider;
        private final TextView titleView;
        private final TextView subtitleView;

        private static final int AVATAR_SIZE = 72;

        public CommunityHeaderView(Context context, Theme.ResourcesProvider resourcesProvider) {
            super(context);
            this.resourcesProvider = resourcesProvider;

            avatarView = new BackupImageView(context);
            avatarView.setRoundRadius(dp(20));
            addView(avatarView, LayoutHelper.createFrame(AVATAR_SIZE, AVATAR_SIZE, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 36, 0, 0));

            titleView = new TextView(context);
            titleView.setTypeface(AndroidUtilities.bold());
            titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
            titleView.setGravity(Gravity.CENTER);
            addView(titleView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT,
                    Gravity.TOP | Gravity.CENTER_HORIZONTAL, 24, 123, 24, 0));

            subtitleView = new TextView(context);
            subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            subtitleView.setGravity(Gravity.CENTER);
            subtitleView.setLineSpacing(dp(2), 1f);
            addView(subtitleView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT,
                    Gravity.TOP | Gravity.CENTER_HORIZONTAL, 32, 157, 32, 0));

            updateColors();
        }

        @Override
        protected void dispatchDraw(@NonNull Canvas canvas) {
            super.dispatchDraw(canvas);
            DrawableUtils.drawCommunityCardDrawable(canvas, Theme.dialogs_communityCardsDrawable, avatarView.getLeft() + avatarView.getWidth() / 2f, avatarView.getTop() + avatarView.getHeight() / 2f, avatarView.getHeight());
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(dp(218), MeasureSpec.EXACTLY));
        }

        @Override
        public void updateColors() {
            titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
            subtitleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2, resourcesProvider));
        }

        public void setTitle(CharSequence text) {
            titleView.setText(text);
        }

        public void setSubtitle(CharSequence text) {
            subtitleView.setText(text);
        }
    }
}
