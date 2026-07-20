package org.telegram.ui.community;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BackDrawable;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.Bulletin;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.StickerEmptyView;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;
import org.telegram.ui.Components.chat.layouts.ChatActivityFadeView;
import org.telegram.ui.Stories.recorder.ButtonWithCounterView;
import org.telegram.ui.community.cells.CommunityPendingRequestCell;
import org.telegram.ui.community.sheet.CommunityInviteOnlySheet;

import java.util.ArrayList;

import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.animator.FactorAnimator;

public class CommunityPendingRequestsActivity extends BaseFragment implements FactorAnimator.Target {
    private final BoolAnimator animatorIsRequestsEmpty = new BoolAnimator(0, this, CubicBezierInterpolator.EASE_OUT_QUINT, 320);

    private long communityId;

    private FrameLayout containerView;
    private UniversalRecyclerView listView;
    private ChatActivityFadeView fadeView;

    private LinearLayout buttonsLayout;
    private ButtonWithCounterView buttonAddAllView;
    private ButtonWithCounterView buttonDeclineAllView;

    private StickerEmptyView emptyView;
    private TLRPC.Chat currentChat;
    private TLRPC.ChatFull info;

    private CommunityUtils.PendingRequests pendingRequestsList;

    public CommunityPendingRequestsActivity(Bundle args) {
        super(args);
    }

    @Override
    public boolean onFragmentCreate() {
        communityId = arguments.getLong("community_id", 0);
        currentChat = getMessagesController().getChat(communityId);
        info = getMessagesController().getChatFull(communityId);

        return super.onFragmentCreate();
    }


    @Override
    public View createView(Context context) {
        setHasOwnBackground(true);

        pendingRequestsList = new CommunityUtils.PendingRequests(getContext(), resourceProvider, BulletinFactory.of(this), currentAccount, communityId);
        pendingRequestsList.setDelegate(new CommunityUtils.PendingRequests.Delegate() {
            @Override
            public void updateAdapter() {
                animatorIsRequestsEmpty.setValue(pendingRequestsList.isFinished() && pendingRequestsList.getTotalCount() == 0, true);
                listView.adapter.update(true);
            }

            @Override
            public void close() {
                finishFragment();
            }

            @Override
            public void onClickGroupOwner(long dialogId) {
                presentFragment(ChatActivity.of(dialogId));
            }
        });
        pendingRequestsList.loadNext();
        pendingRequestsList.markAsViewed();




        actionBar.setBackButtonDrawable(new BackDrawable(false));
        // actionBar.setAddToContainer(false);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });
        actionBar.setTitle(getString(R.string.CommunityPendingRequests));

        containerView = new FrameLayout(context);
        containerView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));

        listView = new UniversalRecyclerView(this, this::fillItems, this::onClick, this::onLongClick);
        listView.setClipToPadding(false);
        listView.adapter.setApplyBackground(false);
        listView.setSections();
        listView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                pendingRequestsList.checkLoadNext(listView);
            }
        });
        actionBar.setAdaptiveBackground(listView);
        containerView.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        fadeView = new ChatActivityFadeView(context);
        fadeView.setupColorKey(Theme.key_windowBackgroundGray);
        fadeView.setFadeZoneBottom(dp(72));
        fadeView.setFadeHeightBottom(dp(24));
        containerView.addView(fadeView, LayoutHelper.createFrameMatchParent());
        containerView.addView(actionBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP));

        buttonsLayout = new LinearLayout(context);
        buttonsLayout.setOrientation(LinearLayout.HORIZONTAL);
        buttonsLayout.setPadding(dp(7), 0, dp(7), dp(12));

        buttonDeclineAllView = new ButtonWithCounterView(context, resourceProvider);
        buttonDeclineAllView.setNeutral();
        buttonDeclineAllView.setColor(ColorUtils.blendARGB(getThemedColor(Theme.key_windowBackgroundWhite), getThemedColor(Theme.key_windowBackgroundWhiteBlackText), 0.125f));
        buttonDeclineAllView.setText(getString(R.string.CommunityPendingRequestDeclineAll));
        buttonDeclineAllView.setRound();
        buttonDeclineAllView.setOnClickListener(v -> pendingRequestsList.onResolveAllJoinRequests(false));
        buttonsLayout.addView(buttonDeclineAllView, LayoutHelper.createLinear(0, 48, 1f, Gravity.NO_GRAVITY, 4, 0, 4, 0));

        buttonAddAllView = new ButtonWithCounterView(context, resourceProvider);
        buttonAddAllView.setText(getString(R.string.CommunityPendingRequestAddAll));
        buttonAddAllView.setRound();
        buttonAddAllView.setOnClickListener(v -> pendingRequestsList.onResolveAllJoinRequests(true));
        buttonsLayout.addView(buttonAddAllView, LayoutHelper.createLinear(0, 48, 1f, Gravity.NO_GRAVITY, 4, 0, 4, 0));

        containerView.addView(buttonsLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM));

        emptyView = new StickerEmptyView(getContext(), null, StickerEmptyView.STICKER_TYPE_DONE, resourceProvider);
        emptyView.title.setText(getString(R.string.NoCommunityJoinRequests));
        emptyView.subtitle.setText(getString(R.string.NoCommunityJoinRequestsDescription));
        emptyView.setAnimateLayoutChange(true);
        emptyView.setVisibility(View.GONE);
        containerView.addView(emptyView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));

        animatorIsRequestsEmpty.setValue(info == null || info.requests_pending == 0, false);

        checkPaddings(0);
        ViewCompat.setOnApplyWindowInsetsListener(containerView, this::onApplyWindowInsets);
        Bulletin.addDelegate(this, new Bulletin.Delegate() {
            @Override
            public int getBottomOffset(int tag) {
                return AndroidUtilities.navigationBarHeight + dp(64);
            }
        });

        return fragmentView = containerView;
    }

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        pendingRequestsList.fillItems(items);
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        pendingRequestsList.commit();
    }

    private void onClick(UItem item, View view, int position, float x, float y) {
        if (item.object instanceof CommunityPendingRequestCell.Data) {
            final CommunityPendingRequestCell.Data data = (CommunityPendingRequestCell.Data) item.object;
            final TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-data.dialogToAdd);
            final TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(data.dialogToAdd);
            if (user != null) {
                presentFragment(ChatActivity.of(user.id));
            } else if (ChatObject.isPublic(chat) || ChatObject.isInChat(chat)) {
                presentFragment(ChatActivity.of(-chat.id));
            } else {
                new CommunityInviteOnlySheet(getContext(), chat, data.requestFromUser, () -> {
                    presentFragment(ChatActivity.of(data.requestFromUser.id));
                }).show();
            }
        }
    }

    private boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }

    @NonNull
    private WindowInsetsCompat onApplyWindowInsets(@NonNull View v, @NonNull WindowInsetsCompat insets) {
        final int bottom = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom;
        checkPaddings(bottom);
        return WindowInsetsCompat.CONSUMED;
    }

    private void checkPaddings(int bottom) {
        listView.setPadding(0, 0, 0, dp(60) + bottom);
        buttonsLayout.setPadding(dp(7), 0, dp(7), bottom + dp(12));
        emptyView.setTranslationY((listView.getPaddingTop() - listView.getPaddingBottom()) / 2f);
        fadeView.setFadeZoneBottom(dp(72) + bottom);
    }


    @Override
    public boolean isSupportEdgeToEdge() {
        return true;
    }

    @Override
    public boolean drawEdgeNavigationBar() {
        return false;
    }

    @Override
    public void onFactorChanged(int id, float factor, float fraction, FactorAnimator callee) {
        buttonsLayout.setAlpha(1f - factor);
        buttonsLayout.setVisibility(1f - factor > 0 ? View.VISIBLE : View.GONE);
        emptyView.setAlpha(factor);
        emptyView.setVisibility(factor > 0 ? View.VISIBLE : View.GONE);
    }
}
