package org.telegram.ui;

import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;

import androidx.annotation.Nullable;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

import java.util.ArrayList;

class ChooseChannelFragment extends UniversalFragment {

    private final UserInfoActivity.AdminedChannelsFetcher channels;
    private final long selectedChannel;
    private final Utilities.Callback<TLRPC.Chat> whenSelected;

    private String query;

    public ChooseChannelFragment(
            UserInfoActivity.AdminedChannelsFetcher channels,
            long selectedChannel,
            Utilities.Callback<TLRPC.Chat> whenSelected
    ) {
        super();
        this.channels = channels;
        this.selectedChannel = selectedChannel;
        this.whenSelected = whenSelected;
        channels.subscribe(() -> {
            if (listView != null) {
                listView.adapter.update(true);
            }
        });
    }

    private ActionBarMenuItem searchItem;

    @Override
    protected boolean useSimpleActionBar() {
        return true;
    }

    @Override
    public View createView(Context context) {
        searchItem = simpleActionBar.createMenu().addItem(0, R.drawable.outline_header_search, getResourceProvider()).setIsSearchField(true).setActionBarMenuItemSearchListener(new ActionBarMenuItem.ActionBarMenuItemSearchListener() {
            @Override
            public void onSearchExpand() {
            }

            @Override
            public void onSearchCollapse() {
                query = null;
                if (listView != null) {
                    listView.adapter.update(true);
                }
            }

            @Override
            public void onTextChanged(EditText editText) {
                query = editText.getText().toString();
                if (listView != null) {
                    listView.adapter.update(true);
                }
            }
        });
        searchItem.setSearchFieldHint(LocaleController.getString(R.string.Search));
        searchItem.setContentDescription(LocaleController.getString(R.string.Search));
        searchItem.setVisibility(View.GONE);

        setHasOwnBackground(true);
        super.createView(context);

        listView.setSections();
        listView.setSectionsDrawBackground(true);

        return fragmentView;
    }

    @Override
    protected CharSequence getTitle() {
        return getString(R.string.EditProfileChannelTitle);
    }

    private final static int BUTTON_REMOVE = 1;
    private final static int BUTTON_CREATE = 2;

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        if (TextUtils.isEmpty(query) && selectedChannel != 0) {
            items.add(UItem.asButton(BUTTON_REMOVE, R.drawable.msg_archive_hide, getString(R.string.EditProfileChannelHide)).red());
            items.add(UItem.asShadow(null));
        }
        if (TextUtils.isEmpty(query)) {
            items.add(UItem.asHeader(getString(R.string.EditProfileChannelSelect)));
        }
        int count = 0;
        for (TLRPC.Chat chat : channels.chats) {
            if (chat == null || ChatObject.isMegagroup(chat)) continue;
            count++;
            if (!TextUtils.isEmpty(query)) {
                String lq = query.toLowerCase(), lq2 = AndroidUtilities.translitSafe(lq);
                String c = chat.title.toLowerCase(), c2 = AndroidUtilities.translitSafe(c);
                if (!(
                        c.startsWith(lq) || c.contains(" " + lq) ||
                                c2.startsWith(lq2) || c2.contains(" " + lq2)
                )) {
                    continue;
                }
            }
            items.add(UItem.asFilterChat(true, -chat.id).setChecked(selectedChannel == chat.id));
        }
        if (TextUtils.isEmpty(query) && count == 0) {
            items.add(UItem.asButton(BUTTON_CREATE, R.drawable.msg_channel_create, getString(R.string.EditProfileChannelStartNew)).accent());
        }
        items.add(UItem.asShadow(null));
        if (searchItem != null) {
            searchItem.setVisibility(count > 5 ? View.VISIBLE : View.GONE);
        }
    }

    private boolean invalidateAfterPause = false;

    @Override
    public void onResume() {
        super.onResume();
        if (invalidateAfterPause) {
            channels.invalidate();
            channels.subscribe(() -> {
                if (listView != null) {
                    listView.adapter.update(true);
                }
            });
            invalidateAfterPause = false;
        }
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == BUTTON_REMOVE) {
            whenSelected.run(null);
            finishFragment();
        } else if (item.id == BUTTON_CREATE) {
            invalidateAfterPause = true;
            SharedPreferences preferences = MessagesController.getGlobalMainSettings();
            if (!BuildVars.DEBUG_VERSION && preferences.getBoolean("channel_intro", false)) {
                Bundle args = new Bundle();
                args.putInt("step", 0);
                presentFragment(new ChannelCreateActivity(args));
            } else {
                presentFragment(new ActionIntroActivity(ActionIntroActivity.ACTION_TYPE_CHANNEL_CREATE));
                preferences.edit().putBoolean("channel_intro", true).apply();
            }
        } else if (item.viewType == UniversalAdapter.VIEW_TYPE_FILTER_CHAT_CHECK) {
            finishFragment();
            whenSelected.run(getMessagesController().getChat(-item.dialogId));
        }
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }

    @Override
    public boolean isSupportEdgeToEdge() {
        return true;
    }

    @Nullable
    @Override
    protected RecyclerListView getListViewForSimpleGlass() {
        return listView;
    }
}
