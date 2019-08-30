/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Toast;

import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.CheckBoxCell;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public class PrivacySettingsActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    private ListAdapter listAdapter;
    private RecyclerListView listView;
    private AlertDialog progressDialog;
    @SuppressWarnings("FieldCanBeLocal")
    private LinearLayoutManager layoutManager;

    private int privacySectionRow;
    private int blockedRow;
    private int phoneNumberRow;
    private int lastSeenRow;
    private int profilePhotoRow;
    private int forwardsRow;
    private int callsRow;
    private int groupsRow;
    private int groupsDetailRow;
    private int securitySectionRow;
    private int sessionsRow;
    private int passwordRow;
    private int passcodeRow;
    private int sessionsDetailRow;
    private int advancedSectionRow;
    private int clearDraftsRow;
    private int deleteAccountRow;
    private int deleteAccountDetailRow;
    private int botsSectionRow;
    private int passportRow;
    private int paymentsClearRow;
    private int webSessionsRow;
    private int botsDetailRow;
    private int contactsSectionRow;
    private int contactsDeleteRow;
    private int contactsSuggestRow;
    private int contactsSyncRow;
    private int contactsDetailRow;
    private int secretSectionRow;
    private int secretMapRow;
    private int secretWebpageRow;
    private int secretDetailRow;
    private int rowCount;

    private boolean currentSync;
    private boolean newSync;
    private boolean currentSuggest;
    private boolean newSuggest;

    private boolean[] clear = new boolean[2];

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();

        getContactsController().loadPrivacySettings();
        getMessagesController().getBlockedUsers(true);
        currentSync = newSync = getUserConfig().syncContacts;
        currentSuggest = newSuggest = getUserConfig().suggestContacts;

        updateRows();
        loadPasswordSettings();

        getNotificationCenter().addObserver(this, NotificationCenter.privacyRulesUpdated);
        getNotificationCenter().addObserver(this, NotificationCenter.blockedUsersDidLoad);

        return true;
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        getNotificationCenter().removeObserver(this, NotificationCenter.privacyRulesUpdated);
        getNotificationCenter().removeObserver(this, NotificationCenter.blockedUsersDidLoad);
        if (currentSync != newSync) {
            getUserConfig().syncContacts = newSync;
            getUserConfig().saveConfig(false);
            if (newSync) {
                getContactsController().forceImportContacts();
                if (getParentActivity() != null) {
                    Toast.makeText(getParentActivity(), LocaleController.getString("SyncContactsAdded", R.string.SyncContactsAdded), Toast.LENGTH_SHORT).show();
                }
            }
        }
        if (newSuggest != currentSuggest) {
            if (!newSuggest) {
                getMediaDataController().clearTopPeers();
            }
            getUserConfig().suggestContacts = newSuggest;
            getUserConfig().saveConfig(false);
            TLRPC.TL_contacts_toggleTopPeers req = new TLRPC.TL_contacts_toggleTopPeers();
            req.enabled = newSuggest;
            getConnectionsManager().sendRequest(req, (response, error) -> {

            });
        }
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(LocaleController.getString("PrivacySettings", R.string.PrivacySettings));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        listAdapter = new ListAdapter(context);

        fragmentView = new FrameLayout(context);
        FrameLayout frameLayout = (FrameLayout) fragmentView;
        frameLayout.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));

        listView = new RecyclerListView(context);
        listView.setLayoutManager(layoutManager = new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false) {
            @Override
            public boolean supportsPredictiveItemAnimations() {
                return false;
            }
        });
        listView.setVerticalScrollBarEnabled(false);
        listView.setItemAnimator(null);
        listView.setLayoutAnimation(null);
        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        listView.setAdapter(listAdapter);
        listView.setOnItemClickListener((view, position) -> {
            if (!view.isEnabled()) {
                return;
            }
            if (position == blockedRow) {
                presentFragment(new PrivacyUsersActivity());
            } else if (position == sessionsRow) {
                presentFragment(new SessionsActivity(0));
            } else if (position == webSessionsRow) {
                presentFragment(new SessionsActivity(1));
            } else if (position == clearDraftsRow) {
                AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
                builder.setMessage(LocaleController.getString("AreYouSureClearDrafts", R.string.AreYouSureClearDrafts));
                builder.setPositiveButton(LocaleController.getString("Delete", R.string.Delete), (dialogInterface, i) -> {
                    TLRPC.TL_messages_clearAllDrafts req = new TLRPC.TL_messages_clearAllDrafts();
                    getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> getMediaDataController().clearAllDrafts()));
                });
                builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                showDialog(builder.create());
            } else if (position == deleteAccountRow) {
                if (getParentActivity() == null) {
                    return;
                }
                AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                builder.setTitle(LocaleController.getString("DeleteAccountTitle", R.string.DeleteAccountTitle));
                builder.setItems(new CharSequence[]{
                        LocaleController.formatPluralString("Months", 1),
                        LocaleController.formatPluralString("Months", 3),
                        LocaleController.formatPluralString("Months", 6),
                        LocaleController.formatPluralString("Years", 1)
                }, (dialog, which) -> {
                    int value = 0;
                    if (which == 0) {
                        value = 30;
                    } else if (which == 1) {
                        value = 90;
                    } else if (which == 2) {
                        value = 182;
                    } else if (which == 3) {
                        value = 365;
                    }
                    final AlertDialog progressDialog = new AlertDialog(getParentActivity(), 3);
                    progressDialog.setCanCacnel(false);
                    progressDialog.show();

                    final TLRPC.TL_account_setAccountTTL req = new TLRPC.TL_account_setAccountTTL();
                    req.ttl = new TLRPC.TL_accountDaysTTL();
                    req.ttl.days = value;
                    getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                        try {
                            progressDialog.dismiss();
                        } catch (Exception e) {
                            FileLog.e(e);
                        }
                        if (response instanceof TLRPC.TL_boolTrue) {
                            getContactsController().setDeleteAccountTTL(req.ttl.days);
                            listAdapter.notifyDataSetChanged();
                        }
                    }));
                });
                builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                showDialog(builder.create());
            } else if (position == lastSeenRow) {
                presentFragment(new PrivacyControlActivity(ContactsController.PRIVACY_RULES_TYPE_LASTSEEN));
            } else if (position == phoneNumberRow) {
                presentFragment(new PrivacyControlActivity(ContactsController.PRIVACY_RULES_TYPE_PHONE));
            } else if (position == groupsRow) {
                presentFragment(new PrivacyControlActivity(ContactsController.PRIVACY_RULES_TYPE_INVITE));
            } else if (position == callsRow) {
                presentFragment(new PrivacyControlActivity(ContactsController.PRIVACY_RULES_TYPE_CALLS));
            } else if (position == profilePhotoRow) {
                presentFragment(new PrivacyControlActivity(ContactsController.PRIVACY_RULES_TYPE_PHOTO));
            } else if (position == forwardsRow) {
                presentFragment(new PrivacyControlActivity(ContactsController.PRIVACY_RULES_TYPE_FORWARDS));
            } else if (position == passwordRow) {
                presentFragment(new TwoStepVerificationActivity(0));
            } else if (position == passcodeRow) {
                if (SharedConfig.passcodeHash.length() > 0) {
                    presentFragment(new PasscodeActivity(2));
                } else {
                    presentFragment(new PasscodeActivity(0));
                }
            } else if (position == secretWebpageRow) {
                if (getMessagesController().secretWebpagePreview == 1) {
                    getMessagesController().secretWebpagePreview = 0;
                } else {
                    getMessagesController().secretWebpagePreview = 1;
                }
                MessagesController.getGlobalMainSettings().edit().putInt("secretWebpage2", getMessagesController().secretWebpagePreview).commit();
                if (view instanceof TextCheckCell) {
                    ((TextCheckCell) view).setChecked(getMessagesController().secretWebpagePreview == 1);
                }
            } else if (position == contactsDeleteRow) {
                if (getParentActivity() == null) {
                    return;
                }
                AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                builder.setTitle(LocaleController.getString("Contacts", R.string.Contacts));
                builder.setMessage(LocaleController.getString("SyncContactsDeleteInfo", R.string.SyncContactsDeleteInfo));
                builder.setPositiveButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                builder.setNegativeButton(LocaleController.getString("OK", R.string.OK), (dialogInterface, i) -> {
                    AlertDialog.Builder builder12 = new AlertDialog.Builder(getParentActivity(), 3);
                    progressDialog = builder12.show();
                    progressDialog.setCanCacnel(false);

                    if (currentSync != newSync) {
                        currentSync = getUserConfig().syncContacts = newSync;
                        getUserConfig().saveConfig(false);
                    }
                    getContactsController().deleteAllContacts(() -> progressDialog.dismiss());
                });
                showDialog(builder.create());
            } else if (position == contactsSuggestRow) {
                final TextCheckCell cell = (TextCheckCell) view;
                if (newSuggest) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                    builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
                    builder.setMessage(LocaleController.getString("SuggestContactsAlert", R.string.SuggestContactsAlert));
                    builder.setPositiveButton(LocaleController.getString("MuteDisable", R.string.MuteDisable), (dialogInterface, i) -> {
                        TLRPC.TL_payments_clearSavedInfo req = new TLRPC.TL_payments_clearSavedInfo();
                        req.credentials = clear[1];
                        req.info = clear[0];
                        getUserConfig().tmpPassword = null;
                        getUserConfig().saveConfig(false);
                        getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                            newSuggest = !newSuggest;
                            cell.setChecked(newSuggest);
                        }));
                    });
                    builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                    showDialog(builder.create());
                } else {
                    newSuggest = !newSuggest;
                    cell.setChecked(newSuggest);
                }
            } else if (position == contactsSyncRow) {
                newSync = !newSync;
                if (view instanceof TextCheckCell) {
                    ((TextCheckCell) view).setChecked(newSync);
                }
            } else if (position == secretMapRow) {
                AlertsCreator.showSecretLocationAlert(getParentActivity(), currentAccount, () -> listAdapter.notifyDataSetChanged(), false);
            } else if (position == paymentsClearRow) {
                BottomSheet.Builder builder = new BottomSheet.Builder(getParentActivity());
                builder.setApplyTopPadding(false);
                builder.setApplyBottomPadding(false);
                LinearLayout linearLayout = new LinearLayout(getParentActivity());
                linearLayout.setOrientation(LinearLayout.VERTICAL);
                for (int a = 0; a < 2; a++) {
                    String name = null;
                    if (a == 0) {
                        name = LocaleController.getString("PrivacyClearShipping", R.string.PrivacyClearShipping);
                    } else if (a == 1) {
                        name = LocaleController.getString("PrivacyClearPayment", R.string.PrivacyClearPayment);
                    }
                    clear[a] = true;
                    CheckBoxCell checkBoxCell = new CheckBoxCell(getParentActivity(), 1, 21);
                    checkBoxCell.setTag(a);
                    checkBoxCell.setBackgroundDrawable(Theme.getSelectorDrawable(false));
                    linearLayout.addView(checkBoxCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 50));
                    checkBoxCell.setText(name, null, true, true);
                    checkBoxCell.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
                    checkBoxCell.setOnClickListener(v -> {
                        CheckBoxCell cell = (CheckBoxCell) v;
                        int num = (Integer) cell.getTag();
                        clear[num] = !clear[num];
                        cell.setChecked(clear[num], true);
                    });
                }
                BottomSheet.BottomSheetCell cell = new BottomSheet.BottomSheetCell(getParentActivity(), 1);
                cell.setBackgroundDrawable(Theme.getSelectorDrawable(false));
                cell.setTextAndIcon(LocaleController.getString("ClearButton", R.string.ClearButton).toUpperCase(), 0);
                cell.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteRedText));
                cell.setOnClickListener(v -> {
                    try {
                        if (visibleDialog != null) {
                            visibleDialog.dismiss();
                        }
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                    AlertDialog.Builder builder1 = new AlertDialog.Builder(getParentActivity());
                    builder1.setTitle(LocaleController.getString("AppName", R.string.AppName));
                    builder1.setMessage(LocaleController.getString("PrivacyPaymentsClearAlert", R.string.PrivacyPaymentsClearAlert));
                    builder1.setPositiveButton(LocaleController.getString("OK", R.string.OK), (dialogInterface, i) -> {
                        TLRPC.TL_payments_clearSavedInfo req = new TLRPC.TL_payments_clearSavedInfo();
                        req.credentials = clear[1];
                        req.info = clear[0];
                        getUserConfig().tmpPassword = null;
                        getUserConfig().saveConfig(false);
                        getConnectionsManager().sendRequest(req, (response, error) -> {

                        });
                    });
                    builder1.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                    showDialog(builder1.create());
                });
                linearLayout.addView(cell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 50));
                builder.setCustomView(linearLayout);
                showDialog(builder.create());
            } else if (position == passportRow) {
                presentFragment(new PassportActivity(PassportActivity.TYPE_PASSWORD, 0, "", "", null, null, null, null, null));
            }
        });

        return fragmentView;
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.privacyRulesUpdated) {
            if (listAdapter != null) {
                listAdapter.notifyDataSetChanged();
            }
        } else if (id == NotificationCenter.blockedUsersDidLoad) {
            listAdapter.notifyItemChanged(blockedRow);
        }
    }

    private void updateRows() {
        rowCount = 0;
        privacySectionRow = rowCount++;
        blockedRow = rowCount++;
        phoneNumberRow = rowCount++;
        lastSeenRow = rowCount++;
        profilePhotoRow = rowCount++;
        forwardsRow = rowCount++;
        callsRow = rowCount++;
        groupsRow = rowCount++;
        groupsDetailRow = rowCount++;
        securitySectionRow = rowCount++;
        passcodeRow = rowCount++;
        passwordRow = rowCount++;
        sessionsRow = rowCount++;
        sessionsDetailRow = rowCount++;
        advancedSectionRow = rowCount++;
        clearDraftsRow = rowCount++;
        deleteAccountRow = rowCount++;
        deleteAccountDetailRow = rowCount++;
        botsSectionRow = rowCount++;
        if (getUserConfig().hasSecureData) {
            passportRow = rowCount++;
        } else {
            passportRow = -1;
        }
        paymentsClearRow = rowCount++;
        webSessionsRow = rowCount++;
        botsDetailRow = rowCount++;
        contactsSectionRow = rowCount++;
        contactsDeleteRow = rowCount++;
        contactsSyncRow = rowCount++;
        contactsSuggestRow = rowCount++;
        contactsDetailRow = rowCount++;
        secretSectionRow = rowCount++;
        secretMapRow = rowCount++;
        secretWebpageRow = rowCount++;
        secretDetailRow = rowCount++;
        if (listAdapter != null) {
            listAdapter.notifyDataSetChanged();
        }
    }

    private void loadPasswordSettings() {
        if (getUserConfig().hasSecureData) {
            return;
        }
        TLRPC.TL_account_getPassword req = new TLRPC.TL_account_getPassword();
        getConnectionsManager().sendRequest(req, (response, error) -> {
            if (response != null) {
                TLRPC.TL_account_password password = (TLRPC.TL_account_password) response;
                if (password.has_secure_values) {
                    AndroidUtilities.runOnUIThread(() -> {
                        getUserConfig().hasSecureData = true;
                        getUserConfig().saveConfig(false);
                        updateRows();
                    });
                }
            }
        }, ConnectionsManager.RequestFlagFailOnServerErrors | ConnectionsManager.RequestFlagWithoutLogin);
    }

    public static String formatRulesString(AccountInstance accountInstance, int rulesType) {
        ArrayList<TLRPC.PrivacyRule> privacyRules = accountInstance.getContactsController().getPrivacyRules(rulesType);
        if (privacyRules.size() == 0) {
            if (rulesType == 3) {
                return LocaleController.getString("P2PNobody", R.string.P2PNobody);
            } else {
                return LocaleController.getString("LastSeenNobody", R.string.LastSeenNobody);
            }
        }
        int type = -1;
        int plus = 0;
        int minus = 0;
        for (int a = 0; a < privacyRules.size(); a++) {
            TLRPC.PrivacyRule rule = privacyRules.get(a);
            if (rule instanceof TLRPC.TL_privacyValueAllowChatParticipants) {
                TLRPC.TL_privacyValueAllowChatParticipants participants = (TLRPC.TL_privacyValueAllowChatParticipants) rule;
                for (int b = 0, N = participants.chats.size(); b < N; b++) {
                    TLRPC.Chat chat = accountInstance.getMessagesController().getChat(participants.chats.get(b));
                    if (chat == null) {
                        continue;
                    }
                    plus += chat.participants_count;
                }
            } else if (rule instanceof TLRPC.TL_privacyValueDisallowChatParticipants) {
                TLRPC.TL_privacyValueDisallowChatParticipants participants = (TLRPC.TL_privacyValueDisallowChatParticipants) rule;
                for (int b = 0, N = participants.chats.size(); b < N; b++) {
                    TLRPC.Chat chat = accountInstance.getMessagesController().getChat(participants.chats.get(b));
                    if (chat == null) {
                        continue;
                    }
                    minus += chat.participants_count;
                }
            } else if (rule instanceof TLRPC.TL_privacyValueAllowUsers) {
                TLRPC.TL_privacyValueAllowUsers privacyValueAllowUsers = (TLRPC.TL_privacyValueAllowUsers) rule;
                plus += privacyValueAllowUsers.users.size();
            } else if (rule instanceof TLRPC.TL_privacyValueDisallowUsers) {
                TLRPC.TL_privacyValueDisallowUsers privacyValueDisallowUsers = (TLRPC.TL_privacyValueDisallowUsers) rule;
                minus += privacyValueDisallowUsers.users.size();
            } else if (type == -1) {
                if (rule instanceof TLRPC.TL_privacyValueAllowAll) {
                    type = 0;
                } else if (rule instanceof TLRPC.TL_privacyValueDisallowAll) {
                    type = 1;
                } else {
                    type = 2;
                }
            }
        }
        if (type == 0 || type == -1 && minus > 0) {
            if (rulesType == 3) {
                if (minus == 0) {
                    return LocaleController.getString("P2PEverybody", R.string.P2PEverybody);
                } else {
                    return LocaleController.formatString("P2PEverybodyMinus", R.string.P2PEverybodyMinus, minus);
                }
            } else {
                if (minus == 0) {
                    return LocaleController.getString("LastSeenEverybody", R.string.LastSeenEverybody);
                } else {
                    return LocaleController.formatString("LastSeenEverybodyMinus", R.string.LastSeenEverybodyMinus, minus);
                }
            }
        } else if (type == 2 || type == -1 && minus > 0 && plus > 0) {
            if (rulesType == 3) {
                if (plus == 0 && minus == 0) {
                    return LocaleController.getString("P2PContacts", R.string.P2PContacts);
                } else {
                    if (plus != 0 && minus != 0) {
                        return LocaleController.formatString("P2PContactsMinusPlus", R.string.P2PContactsMinusPlus, minus, plus);
                    } else if (minus != 0) {
                        return LocaleController.formatString("P2PContactsMinus", R.string.P2PContactsMinus, minus);
                    } else {
                        return LocaleController.formatString("P2PContactsPlus", R.string.P2PContactsPlus, plus);
                    }
                }
            } else {
                if (plus == 0 && minus == 0) {
                    return LocaleController.getString("LastSeenContacts", R.string.LastSeenContacts);
                } else {
                    if (plus != 0 && minus != 0) {
                        return LocaleController.formatString("LastSeenContactsMinusPlus", R.string.LastSeenContactsMinusPlus, minus, plus);
                    } else if (minus != 0) {
                        return LocaleController.formatString("LastSeenContactsMinus", R.string.LastSeenContactsMinus, minus);
                    } else {
                        return LocaleController.formatString("LastSeenContactsPlus", R.string.LastSeenContactsPlus, plus);
                    }
                }
            }
        } else if (type == 1 || plus > 0) {
            if (rulesType == 3) {
                if (plus == 0) {
                    return LocaleController.getString("P2PNobody", R.string.P2PNobody);
                } else {
                    return LocaleController.formatString("P2PNobodyPlus", R.string.P2PNobodyPlus, plus);
                }
            } else {
                if (plus == 0) {
                    return LocaleController.getString("LastSeenNobody", R.string.LastSeenNobody);
                } else {
                    return LocaleController.formatString("LastSeenNobodyPlus", R.string.LastSeenNobodyPlus, plus);
                }
            }
        }
        return "unknown";
    }

    @Override
    public void onResume() {
        super.onResume();
        if (listAdapter != null) {
            listAdapter.notifyDataSetChanged();
        }
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {

        private Context mContext;

        public ListAdapter(Context context) {
            mContext = context;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            int position = holder.getAdapterPosition();
            return position == passcodeRow || position == passwordRow || position == blockedRow || position == sessionsRow || position == secretWebpageRow || position == webSessionsRow || position == clearDraftsRow ||
                    position == groupsRow && !getContactsController().getLoadingPrivicyInfo(ContactsController.PRIVACY_RULES_TYPE_INVITE) ||
                    position == lastSeenRow && !getContactsController().getLoadingPrivicyInfo(ContactsController.PRIVACY_RULES_TYPE_LASTSEEN) ||
                    position == callsRow && !getContactsController().getLoadingPrivicyInfo(ContactsController.PRIVACY_RULES_TYPE_CALLS) ||
                    position == profilePhotoRow && !getContactsController().getLoadingPrivicyInfo(ContactsController.PRIVACY_RULES_TYPE_PHOTO) ||
                    position == forwardsRow && !getContactsController().getLoadingPrivicyInfo(ContactsController.PRIVACY_RULES_TYPE_FORWARDS) ||
                    position == phoneNumberRow && !getContactsController().getLoadingPrivicyInfo(ContactsController.PRIVACY_RULES_TYPE_PHONE) ||
                    position == deleteAccountRow && !getContactsController().getLoadingDeleteInfo() ||
                    position == paymentsClearRow || position == secretMapRow || position == contactsSyncRow || position == passportRow || position == contactsDeleteRow || position == contactsSuggestRow;
        }

        @Override
        public int getItemCount() {
            return rowCount;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case 0:
                    view = new TextSettingsCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 1:
                    view = new TextInfoPrivacyCell(mContext);
                    break;
                case 2:
                    view = new HeaderCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 3:
                default:
                    view = new TextCheckCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
            }
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            switch (holder.getItemViewType()) {
                case 0:
                    TextSettingsCell textCell = (TextSettingsCell) holder.itemView;
                    if (position == blockedRow) {
                        int totalCount = getMessagesController().totalBlockedCount;
                        if (totalCount == 0) {
                            textCell.setTextAndValue(LocaleController.getString("BlockedUsers", R.string.BlockedUsers), LocaleController.getString("BlockedEmpty", R.string.BlockedEmpty), true);
                        } else if (totalCount > 0) {
                            textCell.setTextAndValue(LocaleController.getString("BlockedUsers", R.string.BlockedUsers), String.format("%d", totalCount), true);
                        } else {
                            textCell.setText(LocaleController.getString("BlockedUsers", R.string.BlockedUsers), true);
                        }
                    } else if (position == sessionsRow) {
                        textCell.setText(LocaleController.getString("SessionsTitle", R.string.SessionsTitle), false);
                    } else if (position == webSessionsRow) {
                        textCell.setText(LocaleController.getString("WebSessionsTitle", R.string.WebSessionsTitle), false);
                    } else if (position == passwordRow) {
                        textCell.setText(LocaleController.getString("TwoStepVerification", R.string.TwoStepVerification), true);
                    } else if (position == passcodeRow) {
                        textCell.setText(LocaleController.getString("Passcode", R.string.Passcode), true);
                    } else if (position == phoneNumberRow) {
                        String value;
                        if (getContactsController().getLoadingPrivicyInfo(ContactsController.PRIVACY_RULES_TYPE_PHONE)) {
                            value = LocaleController.getString("Loading", R.string.Loading);
                        } else {
                            value = formatRulesString(getAccountInstance(), ContactsController.PRIVACY_RULES_TYPE_PHONE);
                        }
                        textCell.setTextAndValue(LocaleController.getString("PrivacyPhone", R.string.PrivacyPhone), value, true);
                    } else if (position == lastSeenRow) {
                        String value;
                        if (getContactsController().getLoadingPrivicyInfo(ContactsController.PRIVACY_RULES_TYPE_LASTSEEN)) {
                            value = LocaleController.getString("Loading", R.string.Loading);
                        } else {
                            value = formatRulesString(getAccountInstance(), ContactsController.PRIVACY_RULES_TYPE_LASTSEEN);
                        }
                        textCell.setTextAndValue(LocaleController.getString("PrivacyLastSeen", R.string.PrivacyLastSeen), value, true);
                    } else if (position == groupsRow) {
                        String value;
                        if (getContactsController().getLoadingPrivicyInfo(ContactsController.PRIVACY_RULES_TYPE_INVITE)) {
                            value = LocaleController.getString("Loading", R.string.Loading);
                        } else {
                            value = formatRulesString(getAccountInstance(), ContactsController.PRIVACY_RULES_TYPE_INVITE);
                        }
                        textCell.setTextAndValue(LocaleController.getString("GroupsAndChannels", R.string.GroupsAndChannels), value, false);
                    } else if (position == callsRow) {
                        String value;
                        if (getContactsController().getLoadingPrivicyInfo(ContactsController.PRIVACY_RULES_TYPE_CALLS)) {
                            value = LocaleController.getString("Loading", R.string.Loading);
                        } else {
                            value = formatRulesString(getAccountInstance(), ContactsController.PRIVACY_RULES_TYPE_CALLS);
                        }
                        textCell.setTextAndValue(LocaleController.getString("Calls", R.string.Calls), value, true);
                    } else if (position == profilePhotoRow) {
                        String value;
                        if (getContactsController().getLoadingPrivicyInfo(ContactsController.PRIVACY_RULES_TYPE_PHOTO)) {
                            value = LocaleController.getString("Loading", R.string.Loading);
                        } else {
                            value = formatRulesString(getAccountInstance(), ContactsController.PRIVACY_RULES_TYPE_PHOTO);
                        }
                        textCell.setTextAndValue(LocaleController.getString("PrivacyProfilePhoto", R.string.PrivacyProfilePhoto), value, true);
                    } else if (position == forwardsRow) {
                        String value;
                        if (getContactsController().getLoadingPrivicyInfo(ContactsController.PRIVACY_RULES_TYPE_FORWARDS)) {
                            value = LocaleController.getString("Loading", R.string.Loading);
                        } else {
                            value = formatRulesString(getAccountInstance(), ContactsController.PRIVACY_RULES_TYPE_FORWARDS);
                        }
                        textCell.setTextAndValue(LocaleController.getString("PrivacyForwards", R.string.PrivacyForwards), value, true);
                    } else if (position == passportRow) {
                        textCell.setText(LocaleController.getString("TelegramPassport", R.string.TelegramPassport), true);
                    } else if (position == deleteAccountRow) {
                        String value;
                        if (getContactsController().getLoadingDeleteInfo()) {
                            value = LocaleController.getString("Loading", R.string.Loading);
                        } else {
                            int ttl = getContactsController().getDeleteAccountTTL();
                            if (ttl <= 182) {
                                value = LocaleController.formatPluralString("Months", ttl / 30);
                            } else if (ttl == 365) {
                                value = LocaleController.formatPluralString("Years", ttl / 365);
                            } else {
                                value = LocaleController.formatPluralString("Days", ttl);
                            }
                        }
                        textCell.setTextAndValue(LocaleController.getString("DeleteAccountIfAwayFor2", R.string.DeleteAccountIfAwayFor2), value, false);
                    } else if (position == clearDraftsRow) {
                        textCell.setText(LocaleController.getString("PrivacyDeleteCloudDrafts", R.string.PrivacyDeleteCloudDrafts), true);
                    } else if (position == paymentsClearRow) {
                        textCell.setText(LocaleController.getString("PrivacyPaymentsClear", R.string.PrivacyPaymentsClear), true);
                    } else if (position == secretMapRow) {
                        String value;
                        switch (SharedConfig.mapPreviewType) {
                            case 0:
                                value = LocaleController.getString("MapPreviewProviderTelegram", R.string.MapPreviewProviderTelegram);
                                break;
                            case 1:
                                value = LocaleController.getString("MapPreviewProviderGoogle", R.string.MapPreviewProviderGoogle);
                                break;
                            case 2:
                            default:
                                value = LocaleController.getString("MapPreviewProviderNobody", R.string.MapPreviewProviderNobody);
                                break;
                        }
                        textCell.setTextAndValue(LocaleController.getString("MapPreviewProvider", R.string.MapPreviewProvider), value, true);
                    } else if (position == contactsDeleteRow) {
                        textCell.setText(LocaleController.getString("SyncContactsDelete", R.string.SyncContactsDelete), true);
                    }
                    break;
                case 1:
                    TextInfoPrivacyCell privacyCell = (TextInfoPrivacyCell) holder.itemView;
                    if (position == deleteAccountDetailRow) {
                        privacyCell.setText(LocaleController.getString("DeleteAccountHelp", R.string.DeleteAccountHelp));
                        privacyCell.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
                    } else if (position == groupsDetailRow) {
                        privacyCell.setText(LocaleController.getString("GroupsAndChannelsHelp", R.string.GroupsAndChannelsHelp));
                        privacyCell.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
                    } else if (position == sessionsDetailRow) {
                        privacyCell.setText(LocaleController.getString("SessionsInfo", R.string.SessionsInfo));
                        privacyCell.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
                    } else if (position == secretDetailRow) {
                        privacyCell.setText(LocaleController.getString("SecretWebPageInfo", R.string.SecretWebPageInfo));
                        privacyCell.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
                    } else if (position == botsDetailRow) {
                        privacyCell.setText(LocaleController.getString("PrivacyBotsInfo", R.string.PrivacyBotsInfo));
                        privacyCell.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
                    } else if (position == contactsDetailRow) {
                        /*if (newSync) {
                            privacyCell.setText(LocaleController.getString("SyncContactsInfoOn", R.string.SyncContactsInfoOn));
                        } else {
                            privacyCell.setText(LocaleController.getString("SyncContactsInfoOff", R.string.SyncContactsInfoOff));
                        }*/
                        privacyCell.setText(LocaleController.getString("SuggestContactsInfo", R.string.SuggestContactsInfo));
                        privacyCell.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
                    }
                    break;
                case 2:
                    HeaderCell headerCell = (HeaderCell) holder.itemView;
                    if (position == privacySectionRow) {
                        headerCell.setText(LocaleController.getString("PrivacyTitle", R.string.PrivacyTitle));
                    } else if (position == securitySectionRow) {
                        headerCell.setText(LocaleController.getString("SecurityTitle", R.string.SecurityTitle));
                    } else if (position == advancedSectionRow) {
                        headerCell.setText(LocaleController.getString("PrivacyAdvanced", R.string.PrivacyAdvanced));
                    } else if (position == secretSectionRow) {
                        headerCell.setText(LocaleController.getString("SecretChat", R.string.SecretChat));
                    } else if (position == botsSectionRow) {
                        headerCell.setText(LocaleController.getString("PrivacyBots", R.string.PrivacyBots));
                    } else if (position == contactsSectionRow) {
                        headerCell.setText(LocaleController.getString("Contacts", R.string.Contacts));
                    }
                    break;
                case 3:
                    TextCheckCell textCheckCell = (TextCheckCell) holder.itemView;
                    if (position == secretWebpageRow) {
                        textCheckCell.setTextAndCheck(LocaleController.getString("SecretWebPage", R.string.SecretWebPage), getMessagesController().secretWebpagePreview == 1, false);
                    } else if (position == contactsSyncRow) {
                        textCheckCell.setTextAndCheck(LocaleController.getString("SyncContacts", R.string.SyncContacts), newSync, true);
                    } else if (position == contactsSuggestRow) {
                        textCheckCell.setTextAndCheck(LocaleController.getString("SuggestContacts", R.string.SuggestContacts), newSuggest, false);
                    }
                    break;
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (position == passportRow || position == lastSeenRow || position == phoneNumberRow || position == blockedRow || position == deleteAccountRow || position == sessionsRow || position == webSessionsRow || position == passwordRow || position == passcodeRow || position == groupsRow || position == paymentsClearRow || position == secretMapRow || position == contactsDeleteRow || position == clearDraftsRow) {
                return 0;
            } else if (position == deleteAccountDetailRow || position == groupsDetailRow || position == sessionsDetailRow || position == secretDetailRow || position == botsDetailRow || position == contactsDetailRow) {
                return 1;
            } else if (position == securitySectionRow || position == advancedSectionRow || position == privacySectionRow || position == secretSectionRow || position == botsSectionRow || position == contactsSectionRow) {
                return 2;
            } else if (position == secretWebpageRow || position == contactsSyncRow || position == contactsSuggestRow) {
                return 3;
            }
            return 0;
        }
    }

    @Override
    public ThemeDescription[] getThemeDescriptions() {
        return new ThemeDescription[]{
                new ThemeDescription(listView, ThemeDescription.FLAG_CELLBACKGROUNDCOLOR, new Class[]{TextSettingsCell.class, HeaderCell.class, TextCheckCell.class}, null, null, null, Theme.key_windowBackgroundWhite),
                new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundGray),

                new ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_actionBarDefault),
                new ThemeDescription(listView, ThemeDescription.FLAG_LISTGLOWCOLOR, null, null, null, null, Theme.key_actionBarDefault),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_ITEMSCOLOR, null, null, null, null, Theme.key_actionBarDefaultIcon),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_TITLECOLOR, null, null, null, null, Theme.key_actionBarDefaultTitle),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_actionBarDefaultSelector),

                new ThemeDescription(listView, ThemeDescription.FLAG_SELECTOR, null, null, null, null, Theme.key_listSelector),

                new ThemeDescription(listView, 0, new Class[]{View.class}, Theme.dividerPaint, null, null, Theme.key_divider),

                new ThemeDescription(listView, 0, new Class[]{TextSettingsCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText),
                new ThemeDescription(listView, 0, new Class[]{TextSettingsCell.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteValueText),

                new ThemeDescription(listView, 0, new Class[]{HeaderCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlueHeader),

                new ThemeDescription(listView, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{TextInfoPrivacyCell.class}, null, null, null, Theme.key_windowBackgroundGrayShadow),
                new ThemeDescription(listView, 0, new Class[]{TextInfoPrivacyCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText4),

                new ThemeDescription(listView, 0, new Class[]{TextCheckCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText),
                new ThemeDescription(listView, 0, new Class[]{TextCheckCell.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText2),
                new ThemeDescription(listView, 0, new Class[]{TextCheckCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switchTrack),
                new ThemeDescription(listView, 0, new Class[]{TextCheckCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switchTrackChecked),
        };
    }
}
