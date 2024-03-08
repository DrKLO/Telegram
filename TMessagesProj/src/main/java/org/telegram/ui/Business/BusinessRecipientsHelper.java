package org.telegram.ui.Business;

import static org.telegram.messenger.LocaleController.formatString;
import static org.telegram.messenger.LocaleController.getString;

import android.text.TextUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BotWebViewVibrationEffect;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;
import org.telegram.ui.UsersSelectActivity;

import java.util.ArrayList;

public class BusinessRecipientsHelper {

    public final BaseFragment fragment;
    public final Runnable update;
    public BusinessRecipientsHelper(BaseFragment fragment, Runnable update) {
        this.fragment = fragment;
        this.update = update;
    }

    public int includeFlags, excludeFlags;
    public boolean exclude;
    public final ArrayList<Long> alwaysShow = new ArrayList<>();
    public final ArrayList<Long> neverShow = new ArrayList<>();

    public boolean includeExpanded, excludeExpanded;

    public static final int BUTTON_ADD_INCLUDED = 101;
    public static final int BUTTON_EXPAND_INCLUDED = 102;
    public static final int BUTTON_ADD_EXCLUDED = 103;
    public static final int BUTTON_EXPAND_EXCLUDED = 104;

    public int getFlags() {
        return exclude ? excludeFlags : includeFlags;
    }


    private TLRPC.TL_businessRecipients currentValue;
    public boolean hasChanges() {
        if (currentValue == null) return true;
        if (currentValue.exclude_selected != exclude) return true;
        if ((currentValue.flags &~ (32 | 16)) != getFlags()) return true;
        ArrayList<Long> array = exclude ? neverShow : alwaysShow;
        if (array.size() != currentValue.users.size()) return true;
        for (int i = 0; i < array.size(); ++i) {
            if (!currentValue.users.contains(array.get(i))) {
                return true;
            }
        }
        return false;
    }

    public void setValue(TLRPC.TL_businessRecipients recipients) {
        currentValue = recipients;
        if (currentValue == null) {
            exclude = true;
            excludeFlags = 0;
            includeFlags = 0;
            alwaysShow.clear();
            neverShow.clear();
        } else {
            exclude = currentValue.exclude_selected;
            if (exclude) {
                includeFlags = 0;
                excludeFlags = currentValue.flags &~ (32 | 16);
                alwaysShow.clear();
                neverShow.addAll(currentValue.users);
            } else {
                includeFlags = currentValue.flags &~ (32 | 16);
                excludeFlags = 0;
                alwaysShow.addAll(currentValue.users);
                neverShow.clear();
            }
        }
    }

    public TLRPC.TL_businessRecipients getValue() {
        TLRPC.TL_businessRecipients value = new TLRPC.TL_businessRecipients();
        final int flags = getFlags();
        value.flags = flags &~ (32 | 16);
        value.existing_chats = (flags & PRIVATE_FLAG_EXISTING_CHATS) != 0;
        value.new_chats =      (flags & PRIVATE_FLAG_NEW_CHATS) != 0;
        value.contacts =       (flags & PRIVATE_FLAG_CONTACTS) != 0;
        value.non_contacts =   (flags & PRIVATE_FLAG_NON_CONTACTS) != 0;
        value.exclude_selected = exclude;
        ArrayList<Long> array = exclude ? neverShow : alwaysShow;
        if (!array.isEmpty()) {
            final int currentAccount = UserConfig.selectedAccount;
            MessagesController controller = MessagesController.getInstance(currentAccount);

            value.flags |= 16;
            for (int i = 0; i < array.size(); ++i) {
                TLRPC.InputUser inputUser = controller.getInputUser(array.get(i));
                if (inputUser == null) {
                    FileLog.e("businessRecipientsHelper: user not found " + array.get(i));
                } else {
                    value.users.add(array.get(i));
                }
            }
        }
        return value;
    }

    public TLRPC.TL_inputBusinessRecipients getInputValue() {
        TLRPC.TL_inputBusinessRecipients value = new TLRPC.TL_inputBusinessRecipients();
        final int flags = getFlags();
        value.flags = flags &~ (32 | 16);
        value.existing_chats = (flags & PRIVATE_FLAG_EXISTING_CHATS) != 0;
        value.new_chats =      (flags & PRIVATE_FLAG_NEW_CHATS) != 0;
        value.contacts =       (flags & PRIVATE_FLAG_CONTACTS) != 0;
        value.non_contacts =   (flags & PRIVATE_FLAG_NON_CONTACTS) != 0;
        value.exclude_selected = exclude;
        ArrayList<Long> array = exclude ? neverShow : alwaysShow;
        if (!array.isEmpty()) {
            final int currentAccount = UserConfig.selectedAccount;
            MessagesController controller = MessagesController.getInstance(currentAccount);

            value.flags |= 16;
            for (int i = 0; i < array.size(); ++i) {
                TLRPC.InputUser inputUser = controller.getInputUser(array.get(i));
                if (inputUser == null) {
                    FileLog.e("businessRecipientsHelper: user not found " + array.get(i));
                } else {
                    value.users.add(inputUser);
                }
            }
        }
        return value;
    }

    private int shiftDp = -4;
    public boolean validate(UniversalRecyclerView listView) {
        if (!exclude && alwaysShow.isEmpty() && includeFlags == 0) {
            BotWebViewVibrationEffect.APP_ERROR.vibrate();
            AndroidUtilities.shakeViewSpring(listView.findViewByItemId(BUTTON_ADD_INCLUDED), shiftDp = -shiftDp);
            listView.smoothScrollToPosition(listView.findPositionByItemId(BUTTON_ADD_INCLUDED));
            return false;
        }
        return true;
    }

    public void setExclude(boolean value) {
        exclude = value;
    }

    public void fillItems(ArrayList<UItem> items) {
        final int flags = getFlags();
        if (!exclude) {
            items.add(UItem.asHeader(getString(R.string.BusinessChatsIncluded)));
            items.add(UItem.asButton(BUTTON_ADD_INCLUDED, R.drawable.msg2_chats_add, getString(R.string.BusinessChatsIncludedAdd)).accent());
            if ((flags & PRIVATE_FLAG_EXISTING_CHATS) != 0) {
                items.add(UItem.asFilterChat(true, LocaleController.getString(R.string.FilterExistingChats), "existing_chats", PRIVATE_FLAG_EXISTING_CHATS));
            }
            if ((flags & PRIVATE_FLAG_NEW_CHATS) != 0) {
                items.add(UItem.asFilterChat(true, LocaleController.getString(R.string.FilterNewChats), "new_chats", PRIVATE_FLAG_NEW_CHATS));
            }
            if ((flags & PRIVATE_FLAG_CONTACTS) != 0) {
                items.add(UItem.asFilterChat(true, LocaleController.getString(R.string.FilterContacts), "contacts", PRIVATE_FLAG_CONTACTS));
            }
            if ((flags & PRIVATE_FLAG_NON_CONTACTS) != 0) {
                items.add(UItem.asFilterChat(true, LocaleController.getString(R.string.FilterNonContacts), "non_contacts", PRIVATE_FLAG_NON_CONTACTS));
            }
            if (!alwaysShow.isEmpty()) {
                int count = includeExpanded || alwaysShow.size() < 8 ? alwaysShow.size() : Math.min(5, alwaysShow.size());
                for (int i = 0; i < count; ++i) {
                    items.add(UItem.asFilterChat(true, alwaysShow.get(i)));
                }
                if (count != alwaysShow.size()) {
                    items.add(UItem.asButton(BUTTON_EXPAND_INCLUDED, R.drawable.arrow_more, LocaleController.formatPluralString("FilterShowMoreChats", alwaysShow.size() - 5)).accent());
                }
            }
        } else {
            items.add(UItem.asHeader(getString(R.string.BusinessChatsExcluded)));
            items.add(UItem.asButton(BUTTON_ADD_EXCLUDED, R.drawable.msg2_chats_add, getString(R.string.BusinessChatsExcludedAdd)).accent());
            if ((flags & PRIVATE_FLAG_EXISTING_CHATS) != 0) {
                items.add(UItem.asFilterChat(false, getString(R.string.FilterExistingChats), "existing_chats", PRIVATE_FLAG_EXISTING_CHATS));
            }
            if ((flags & PRIVATE_FLAG_NEW_CHATS) != 0) {
                items.add(UItem.asFilterChat(false, getString(R.string.FilterNewChats), "new_chats", PRIVATE_FLAG_NEW_CHATS));
            }
            if ((flags & PRIVATE_FLAG_CONTACTS) != 0) {
                items.add(UItem.asFilterChat(false, getString(R.string.FilterContacts), "contacts", PRIVATE_FLAG_CONTACTS));
            }
            if ((flags & PRIVATE_FLAG_NON_CONTACTS) != 0) {
                items.add(UItem.asFilterChat(false, getString(R.string.FilterNonContacts), "non_contacts", PRIVATE_FLAG_NON_CONTACTS));
            }
            if (!neverShow.isEmpty()) {
                int count = excludeExpanded || neverShow.size() < 8 ? neverShow.size() : Math.min(5, neverShow.size());
                for (int i = 0; i < count; ++i) {
                    items.add(UItem.asFilterChat(false, neverShow.get(i)));
                }
                if (count != neverShow.size()) {
                    items.add(UItem.asButton(BUTTON_EXPAND_EXCLUDED, R.drawable.arrow_more, LocaleController.formatPluralString("FilterShowMoreChats", neverShow.size() - 5)).accent());
                }
            }
        }
    }

    public boolean onClick(UItem item) {
        if (item.id == BUTTON_ADD_INCLUDED || item.id == BUTTON_ADD_EXCLUDED) {
            selectChatsFor(item.id == BUTTON_ADD_INCLUDED);
            return true;
        } else if (item.id == BUTTON_EXPAND_INCLUDED) {
            includeExpanded = true;
            update.run();
            return true;
        } else if (item.id == BUTTON_EXPAND_EXCLUDED) {
            excludeExpanded = true;
            update.run();
            return true;
        } else if (item.viewType == UniversalAdapter.VIEW_TYPE_FILTER_CHAT) {
            if (fragment == null) return false;
            final int flag = item.chatType == null ? 0 : getFlag(item.chatType);
            final String name = flag == 0 ? fragment.getMessagesController().getPeerName(item.dialogId) : getFlagName(flag);
            fragment.showDialog(
                new AlertDialog.Builder(fragment.getContext(), fragment.getResourceProvider())
                    .setTitle(getString(exclude ? R.string.BusinessRecipientsRemoveExcludeTitle : R.string.BusinessRecipientsRemoveIncludeTitle))
                    .setMessage(formatString(exclude ? R.string.BusinessRecipientsRemoveExcludeMessage : R.string.BusinessRecipientsRemoveIncludeMessage, name))
                    .setPositiveButton(getString(R.string.Remove), (di, w) -> {
                        if (flag != 0) {
                            if (exclude) {
                                excludeFlags &=~ flag;
                            } else {
                                includeFlags &=~ flag;
                            }
                        } else {
                            (exclude ? neverShow : alwaysShow).remove(item.dialogId);
                        }
                        update.run();
                    })
                    .setNegativeButton(getString(R.string.Cancel), null)
                    .create()
            );
            return true;
        }
        return false;
    }

    private int getFlag(String chatType) {
        switch (chatType) {
            case "existing_chats": return PRIVATE_FLAG_EXISTING_CHATS;
            case "new_chats": return PRIVATE_FLAG_NEW_CHATS;
            case "contacts": return PRIVATE_FLAG_CONTACTS;
            case "non_contacts": return PRIVATE_FLAG_NON_CONTACTS;
        }
        return 0;
    }

    private String getFlagName(int flag) {
        switch (flag) {
            case PRIVATE_FLAG_EXISTING_CHATS: return getString(R.string.FilterExistingChats);
            case PRIVATE_FLAG_NEW_CHATS: return getString(R.string.FilterNewChats);
            case PRIVATE_FLAG_CONTACTS: return getString(R.string.FilterContacts);
            default:
            case PRIVATE_FLAG_NON_CONTACTS: return getString(R.string.FilterNonContacts);
        }
    }

    public static final int PRIVATE_FLAG_EXISTING_CHATS = 1;
    public static final int PRIVATE_FLAG_NEW_CHATS = 2;
    public static final int PRIVATE_FLAG_CONTACTS = 4;
    public static final int PRIVATE_FLAG_NON_CONTACTS = 8;

    private boolean doNotExcludeNewChats;
    public void doNotExcludeNewChats() {
        doNotExcludeNewChats = true;
    }

    private void selectChatsFor(boolean include) {
        ArrayList<Long> arrayList = include ? alwaysShow : neverShow;
        UsersSelectActivity fragment = new UsersSelectActivity(include, arrayList, getFlags()).asPrivateChats();
        fragment.noChatTypes = false;
        fragment.allowSelf = false;
        fragment.doNotNewChats = !include && doNotExcludeNewChats;
        fragment.setDelegate((ids, flags) -> {
            if (include) {
                includeFlags = flags;
                alwaysShow.clear();
                alwaysShow.addAll(ids);
                for (int a = 0; a < alwaysShow.size(); a++) {
                    neverShow.remove(alwaysShow.get(a));
                }
            } else {
                excludeFlags = flags;
                neverShow.clear();
                neverShow.addAll(ids);
                for (int a = 0; a < neverShow.size(); a++) {
                    alwaysShow.remove(neverShow.get(a));
                }
            }
            update.run();
        });
        this.fragment.presentFragment(fragment);
    }

}
