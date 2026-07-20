package org.telegram.ui.community.sheet;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.ProfileSearchCell;
import org.telegram.ui.Cells.RadioButtonCell;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.BottomSheetWithRecyclerListView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Stories.recorder.ButtonWithCounterView;

import java.util.ArrayList;

public class CommunityAddOptionsSheet extends BottomSheetWithRecyclerListView {
    private static final int ROW_ID_HIDDEN = 150;
    private static final int ROW_ID_VISIBLE = 151;

    private UniversalAdapter adapter;
    private boolean isHidden;
    private final TLRPC.User user;
    private final TLRPC.Chat chat;
    private final FrameLayout cell;
    private final ProfileSearchCell searchCell;
    private final boolean isChannel;
    private final boolean isBot;

    public CommunityAddOptionsSheet(Context context, TLRPC.Chat community, long dialogId, Utilities.Callback<Boolean> callback) {
        super(context, null, false, true, false, false, false, ActionBarType.SLIDING, null);
        user = MessagesController.getInstance(currentAccount).getUser(dialogId);
        chat = MessagesController.getInstance(currentAccount).getChat(-dialogId);

        isBot = UserObject.isBot(user);
        isChannel = ChatObject.isChannelAndNotMegaGroup(chat);

        ignoreTouchActionBar = false;
        headerMoveTop = dp(12);
        actionBar.setTitle(getTitle());

        setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray, resourcesProvider));

        cell = new FrameLayout(context);
        cell.setPadding(0, dp(3), 0, dp(3));
        cell.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        searchCell = new ProfileSearchCell(context);
        if (chat != null) {
            searchCell.setData(chat, null, chat.title, LocaleController.formatPluralStringSpaced("Members", chat.participants_count), false, false);
        } else if (user != null) {
            searchCell.setData(user, null, DialogObject.getName(user), getString(R.string.Bot), false, false);
        }
        cell.addView(searchCell, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));


        recyclerListView.setPadding(backgroundPaddingLeft, 0, backgroundPaddingLeft, AndroidUtilities.navigationBarHeight + dp(12 + 48 + 4));
        recyclerListView.setSections();
        recyclerListView.setClipToPadding(false);
        recyclerListView.setOnItemClickListener((view, position) -> {
            UItem item = adapter.getItem(position - 1);
            if (item.id == ROW_ID_VISIBLE) {
                setIsHidden(false);
            } else if (item.id == ROW_ID_HIDDEN) {
                setIsHidden(true);
            }
        });

        ButtonWithCounterView button = new ButtonWithCounterView(context, resourcesProvider);

        if (community != null) {
            button.setText(getString(ChatObject.canAddChatToCommunity(community) ?
                R.string.CommunityAddToCommunityButton : R.string.CommunityAddToCommunityRequestButton));
        } else {
            button.setText(getString(R.string.CommunityCreateCommunity));
        }

        button.setRound();
        button.setOnClickListener(v -> apply(callback, isHidden, community != null && !ChatObject.canAddChatToCommunity(community)));

        containerView.addView(button, LayoutHelper.createFrameMarginPx(
                LayoutHelper.MATCH_PARENT, 48, Gravity.BOTTOM,
                dp(12) + backgroundPaddingLeft, 0,
                dp(12) + backgroundPaddingLeft,
                dp(12) + AndroidUtilities.navigationBarHeight));

        adapter.update(false);
    }

    private void apply(Utilities.Callback<Boolean> callback, boolean isHidden, boolean ask) {
        if (ask && !isHidden && !isBot) {
            AlertsCreator.showSimpleConfirmAlert(getContext(), resourcesProvider,
                getString(R.string.CommunityAddToCommunityTitle),
                getString(isChannel ?
                    R.string.CommunityAddToCommunityChannelMessage :
                    R.string.CommunityAddToCommunityGroupMessage),
                getString(R.string.Add), false, () -> apply(callback, isHidden, false));
            return;
        }

        callback.run(isHidden);
        dismiss();
    }



    private void setIsHidden(boolean isHidden) {
        if (this.isHidden == isHidden) {
            return;
        }
        this.isHidden = isHidden;

        boolean needUpdate = false;
        {
            final View v = recyclerListView.findViewByPosition(visibleRow + 1);
            if (v instanceof RadioButtonCell) {
                RadioButtonCell cell = (RadioButtonCell) v;
                cell.setChecked(!isHidden, true);
            } else {
                needUpdate = true;
            }
        }
        {
            final View v = recyclerListView.findViewByPosition(visibleRow + 2);
            if (v instanceof RadioButtonCell) {
                RadioButtonCell cell = (RadioButtonCell) v;
                cell.setChecked(isHidden, true);
            } else {
                needUpdate = true;
            }
        }
        if (needUpdate) {
            adapter.update(true);
        }
    }

    @Override
    protected CharSequence getTitle() {
        return getString(isBot ? R.string.CommunityAddBotTitle : R.string.CommunityAddChatTitle);
    }

    @Override
    protected RecyclerListView.SelectionAdapter createAdapter(RecyclerListView listView) {
        adapter = new UniversalAdapter(recyclerListView, getContext(), currentAccount, 0, false, this::fillItems, resourcesProvider);
        adapter.setApplyBackground(false);
        return adapter;
    }

    private int visibleRow;

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asSpace(0, dp(12)));
        items.add(UItem.asCustom(1, cell));
        items.add(UItem.asSpace(2, dp(12)));

        items.add(UItem.asHeader(3, getString(R.string.CommunityChatVisibilitySection)));

        visibleRow = items.size();
        items.add(UItem.asRadio2(ROW_ID_VISIBLE,
            getString(R.string.CommunityChatVisibilityVisible),
            getString(isBot ? R.string.CommunityChatVisibilityVisibleBotInfo : R.string.CommunityChatVisibilityVisibleInfo)
        ).setChecked(!isHidden));
        items.add(UItem.asRadio2(ROW_ID_HIDDEN,
            getString(R.string.CommunityChatVisibilityHidden),
            getString(isBot ? R.string.CommunityChatVisibilityHiddenBotInfo : R.string.CommunityChatVisibilityHiddenInfo)
        ).setChecked(isHidden));
        items.add(UItem.asShadow(6, getString(R.string.CommunityChatVisibilityCannotChange)));
    }
}
