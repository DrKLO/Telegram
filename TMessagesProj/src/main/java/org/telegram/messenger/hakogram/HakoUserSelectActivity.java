package org.telegram.messenger.hakogram;

import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

/**
 * Выбор человека/людей для слежки
 * Multi-select users/bots/non-contacts with checkmark over avatar, Save button at bottom.
 */
public class HakoUserSelectActivity extends BaseFragment {

    private UniversalRecyclerView listView;
    private Set<Long> selectedIds;
    private ArrayList<TLRPC.User> allUsers = new ArrayList<>();

    @Override
    public boolean onFragmentCreate() {
        selectedIds = new HashSet<>(HakoConfig.getSlezkaSelectedUserIds());
        loadUsers();
        return super.onFragmentCreate();
    }

    private void loadUsers() {
        allUsers.clear();
        HashSet<Long> seen = new HashSet<>();
        int currentAccount = UserConfig.selectedAccount;
        MessagesController mc = MessagesController.getInstance(currentAccount);
        ContactsController cc = ContactsController.getInstance(currentAccount);

        // From contacts
        try {
            ArrayList<TLRPC.TL_contact> contacts = cc.contacts;
            if (contacts != null) {
                for (TLRPC.TL_contact c : contacts) {
                    long uid = c.user_id;
                    if (seen.contains(uid)) continue;
                    TLRPC.User u = mc.getUser(uid);
                    if (u != null) {
                        allUsers.add(u);
                        seen.add(uid);
                    }
                }
            }
        } catch (Exception ignored) {}

        // From dialogs (covers bots, non-contacts, groups? we filter users only)
        try {
            for (int i = 0; i < mc.dialogs_dict.size(); i++) {
                long did = mc.dialogs_dict.keyAt(i);
                if (did <= 0) continue; // chats negative, ignore for this selection (need only users/bots)
                if (did == UserConfig.getInstance(currentAccount).getClientUserId()) continue;
                TLRPC.User u = mc.getUser(did);
                if (u != null && !seen.contains(u.id)) {
                    allUsers.add(u);
                    seen.add(u.id);
                }
            }
        } catch (Exception ignored) {}

        // From all messages users cache? Try to iterate over loaded users via brute force if still few
        // Use ContactsController contactsBook?
        // Also add users from MediaDataController bot list
        try {
            // include recently seen users via Notification? fallback add self test users
            // If still empty, add at least 2 dummy? but real app will have data
        } catch (Exception ignored) {}

        // Sort by name
        try {
            allUsers.sort((a, b) -> {
                String na = org.telegram.messenger.UserObject.getUserName(a);
                String nb = org.telegram.messenger.UserObject.getUserName(b);
                return na.compareToIgnoreCase(nb);
            });
        } catch (Exception ignored) {}
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle("Выбрать человека/людей");
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) finishFragment();
            }
        });

        FrameLayout container = new FrameLayout(context);
        container.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));

        listView = new UniversalRecyclerView(this, this::fillItems, this::onClick, null);
        container.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        // Bottom save button as overlay? Use actionBar menu or custom view.
        // We will add save button as last list item, but also add floating button for better UX.
        // For now, rely on list item 999 as Save.
        fragmentView = container;
        return fragmentView;
    }

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asHeader("Пользователи и боты"));
        if (allUsers.isEmpty()) {
            items.add(UItem.asHeader("Нет доступных пользователей"));
            items.add(UItem.asHeader("Откройте чаты чтобы загрузить список"));
        } else {
            for (int i = 0; i < allUsers.size(); i++) {
                TLRPC.User user = allUsers.get(i);
                // id = index; object = user; checked based on selectedIds
                UItem uitem = UItem.asUserCheckbox(i, user);
                uitem.checked = selectedIds.contains(user.id);
                // set id to index for click handling; we keep user in object
                items.add(uitem);
            }
        }
        items.add(UItem.asShadow(null));
        // Save button as TextCell bottom
        items.add(UItem.asButton(999, "Сохранить"));
        items.add(UItem.asShadow(null));
    }

    private void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == 999) {
            // Save
            HakoConfig.setSlezkaSelectedUserIds(selectedIds);
            finishFragment();
            return;
        }
        if (item.viewType == UniversalAdapter.VIEW_TYPE_USER_CHECKBOX) {
            if (item.object instanceof TLRPC.User) {
                TLRPC.User user = (TLRPC.User) item.object;
                long uid = user.id;
                if (selectedIds.contains(uid)) selectedIds.remove(uid);
                else selectedIds.add(uid);
                // update item checked visually without full rebuild? For simplicity full update.
                if (listView != null && listView.adapter != null) {
                    listView.adapter.update(true);
                }
            }
        }
    }
}
