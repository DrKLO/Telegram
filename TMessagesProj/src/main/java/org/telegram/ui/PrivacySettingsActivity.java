/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2017.
 */

package org.telegram.ui;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.messenger.support.widget.LinearLayoutManager;
import org.telegram.messenger.support.widget.RecyclerView;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.RequestDelegate;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.messenger.UserConfig;
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
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.voip.VoIPHelper;

import java.util.ArrayList;

public class PrivacySettingsActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    private ListAdapter listAdapter;
    private RecyclerListView listView;

    private int privacySectionRow;
    private int blockedRow;
    private int lastSeenRow;
    private int callsRow;
    private int groupsRow;
    private int groupsDetailRow;
    private int securitySectionRow;
    private int sessionsRow;
    private int passwordRow;
    private int passcodeRow;
    private int sessionsDetailRow;
    private int deleteAccountSectionRow;
    private int deleteAccountRow;
    private int deleteAccountDetailRow;
    private int paymentsSectionRow;
    private int paymentsClearRow;
    private int paymentsDetailRow;
    private int secretSectionRow;
    private int secretWebpageRow;
    private int secretDetailRow;
    private int callsSectionRow;
    private int callsP2PRow;
    private int callsDetailRow;
    private int rowCount;

    private boolean clear[] = new boolean[2];

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();

        ContactsController.getInstance().loadPrivacySettings();

        rowCount = 0;
        privacySectionRow = rowCount++;
        blockedRow = rowCount++;
        lastSeenRow = rowCount++;
        callsRow = rowCount++;
        groupsRow = rowCount++;
        groupsDetailRow = rowCount++;
        securitySectionRow = rowCount++;
        passcodeRow = rowCount++;
        passwordRow = rowCount++;
        sessionsRow = rowCount++;
        sessionsDetailRow = rowCount++;
        deleteAccountSectionRow = rowCount++;
        deleteAccountRow = rowCount++;
        deleteAccountDetailRow = rowCount++;
        paymentsSectionRow = rowCount++;
        paymentsClearRow = rowCount++;
        paymentsDetailRow = rowCount++;
        callsSectionRow = rowCount++;
        callsP2PRow = rowCount++;
        callsDetailRow = rowCount++;

        if (MessagesController.getInstance().secretWebpagePreview != 1) {
            secretSectionRow = rowCount++;
            secretWebpageRow = rowCount++;
            secretDetailRow = rowCount++;
        } else {
            secretSectionRow = -1;
            secretWebpageRow = -1;
            secretDetailRow = -1;
        }

        NotificationCenter.getInstance().addObserver(this, NotificationCenter.privacyRulesUpdated);
        VoIPHelper.upgradeP2pSetting();

        return true;
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.privacyRulesUpdated);
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
        listView.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
        listView.setVerticalScrollBarEnabled(false);
        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        listView.setAdapter(listAdapter);
        listView.setOnItemClickListener(new RecyclerListView.OnItemClickListener() {
            @Override
            public void onItemClick(View view, int position) {
                if (!view.isEnabled()) {
                    return;
                }
                if (position == blockedRow) {
                    presentFragment(new BlockedUsersActivity());
                } else if (position == sessionsRow) {
                    presentFragment(new SessionsActivity());
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
                    }, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
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
                            final AlertDialog progressDialog = new AlertDialog(getParentActivity(), 1);
                            progressDialog.setMessage(LocaleController.getString("Loading", R.string.Loading));
                            progressDialog.setCanceledOnTouchOutside(false);
                            progressDialog.setCancelable(false);
                            progressDialog.show();

                            final TLRPC.TL_account_setAccountTTL req = new TLRPC.TL_account_setAccountTTL();
                            req.ttl = new TLRPC.TL_accountDaysTTL();
                            req.ttl.days = value;
                            ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
                                @Override
                                public void run(final TLObject response, final TLRPC.TL_error error) {
                                    AndroidUtilities.runOnUIThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            try {
                                                progressDialog.dismiss();
                                            } catch (Exception e) {
                                                FileLog.e(e);
                                            }
                                            if (response instanceof TLRPC.TL_boolTrue) {
                                                ContactsController.getInstance().setDeleteAccountTTL(req.ttl.days);
                                                listAdapter.notifyDataSetChanged();
                                            }
                                        }
                                    });
                                }
                            });
                        }
                    });
                    builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                    showDialog(builder.create());
                } else if (position == lastSeenRow) {
                    presentFragment(new PrivacyControlActivity(0));
                } else if (position == callsRow) {
                    presentFragment(new PrivacyControlActivity(2));
                } else if (position == groupsRow) {
                    presentFragment(new PrivacyControlActivity(1));
                } else if (position == passwordRow) {
                    presentFragment(new TwoStepVerificationActivity(0));
                } else if (position == passcodeRow) {
                    if (UserConfig.passcodeHash.length() > 0) {
                        presentFragment(new PasscodeActivity(2));
                    } else {
                        presentFragment(new PasscodeActivity(0));
                    }
                } else if (position == secretWebpageRow) {
                    if (MessagesController.getInstance().secretWebpagePreview == 1) {
                        MessagesController.getInstance().secretWebpagePreview = 0;
                    } else {
                        MessagesController.getInstance().secretWebpagePreview = 1;
                    }
                    ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE).edit().putInt("secretWebpage2", MessagesController.getInstance().secretWebpagePreview).commit();
                    if (view instanceof TextCheckCell) {
                        ((TextCheckCell) view).setChecked(MessagesController.getInstance().secretWebpagePreview == 1);
                    }
                } else if (position == callsP2PRow) {
                    new AlertDialog.Builder(getParentActivity())
                            .setTitle(LocaleController.getString("PrivacyCallsP2PTitle", R.string.PrivacyCallsP2PTitle))
							.setItems(new String[]{
							        LocaleController.getString("LastSeenEverybody", R.string.LastSeenEverybody),
                                    LocaleController.getString("LastSeenContacts", R.string.LastSeenContacts),
                                    LocaleController.getString("LastSeenNobody", R.string.LastSeenNobody)
                            }, new DialogInterface.OnClickListener(){
                                @Override
                                public void onClick(DialogInterface dialog, int which){
                                    SharedPreferences prefs=getParentActivity().getSharedPreferences("mainconfig", Context.MODE_PRIVATE);
                                    prefs.edit().putInt("calls_p2p_new", which).apply();
                                    listAdapter.notifyDataSetChanged();
                                }
                            })
                            .setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null)
                            .show();
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
                        CheckBoxCell checkBoxCell = new CheckBoxCell(getParentActivity(), true);
                        checkBoxCell.setTag(a);
                        checkBoxCell.setBackgroundDrawable(Theme.getSelectorDrawable(false));
                        linearLayout.addView(checkBoxCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48));
                        checkBoxCell.setText(name, null, true, true);
                        checkBoxCell.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
                        checkBoxCell.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                CheckBoxCell cell = (CheckBoxCell) v;
                                int num = (Integer) cell.getTag();
                                clear[num] = !clear[num];
                                cell.setChecked(clear[num], true);
                            }
                        });
                    }
                    BottomSheet.BottomSheetCell cell = new BottomSheet.BottomSheetCell(getParentActivity(), 1);
                    cell.setBackgroundDrawable(Theme.getSelectorDrawable(false));
                    cell.setTextAndIcon(LocaleController.getString("ClearButton", R.string.ClearButton).toUpperCase(), 0);
                    cell.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteRedText));
                    cell.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            try {
                                if (visibleDialog != null) {
                                    visibleDialog.dismiss();
                                }
                            } catch (Exception e) {
                                FileLog.e(e);
                            }
                            TLRPC.TL_payments_clearSavedInfo req = new TLRPC.TL_payments_clearSavedInfo();
                            req.credentials = clear[1];
                            req.info = clear[0];
                            UserConfig.tmpPassword = null;
                            UserConfig.saveConfig(false);
                            ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
                                @Override
                                public void run(TLObject response, TLRPC.TL_error error) {

                                }
                            });
                        }
                    });
                    linearLayout.addView(cell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48));
                    builder.setCustomView(linearLayout);
                    showDialog(builder.create());
                }
            }
        });

        return fragmentView;
    }

    @Override
    public void didReceivedNotification(int id, Object... args) {
        if (id == NotificationCenter.privacyRulesUpdated) {
            if (listAdapter != null) {
                listAdapter.notifyDataSetChanged();
            }
        }
    }

    private String formatRulesString(int rulesType) {
        ArrayList<TLRPC.PrivacyRule> privacyRules = ContactsController.getInstance().getPrivacyRules(rulesType);
        if (privacyRules.size() == 0) {
            return LocaleController.getString("LastSeenNobody", R.string.LastSeenNobody);
        }
        int type = -1;
        int plus = 0;
        int minus = 0;
        for (int a = 0; a < privacyRules.size(); a++) {
            TLRPC.PrivacyRule rule = privacyRules.get(a);
            if (rule instanceof TLRPC.TL_privacyValueAllowUsers) {
                plus += rule.users.size();
            } else if (rule instanceof TLRPC.TL_privacyValueDisallowUsers) {
                minus += rule.users.size();
            } else if (rule instanceof TLRPC.TL_privacyValueAllowAll) {
                type = 0;
            } else if (rule instanceof TLRPC.TL_privacyValueDisallowAll) {
                type = 1;
            } else {
                type = 2;
            }
        }
        if (type == 0 || type == -1 && minus > 0) {
            if (minus == 0) {
                return LocaleController.getString("LastSeenEverybody", R.string.LastSeenEverybody);
            } else {
                return LocaleController.formatString("LastSeenEverybodyMinus", R.string.LastSeenEverybodyMinus, minus);
            }
        } else if (type == 2 || type == -1 && minus > 0 && plus > 0) {
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
        } else if (type == 1 || plus > 0) {
            if (plus == 0) {
                return LocaleController.getString("LastSeenNobody", R.string.LastSeenNobody);
            } else {
                return LocaleController.formatString("LastSeenNobodyPlus", R.string.LastSeenNobodyPlus, plus);
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
            return position == passcodeRow || position == passwordRow || position == blockedRow || position == sessionsRow || position == secretWebpageRow ||
                    position == groupsRow && !ContactsController.getInstance().getLoadingGroupInfo() ||
                    position == lastSeenRow && !ContactsController.getInstance().getLoadingLastSeenInfo() ||
                    position == callsRow && !ContactsController.getInstance().getLoadingCallsInfo() ||
                    position == deleteAccountRow && !ContactsController.getInstance().getLoadingDeleteInfo() ||
                    position == paymentsClearRow || position == callsP2PRow;
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
                        textCell.setText(LocaleController.getString("BlockedUsers", R.string.BlockedUsers), true);
                    } else if (position == sessionsRow) {
                        textCell.setText(LocaleController.getString("SessionsTitle", R.string.SessionsTitle), false);
                    } else if (position == passwordRow) {
                        textCell.setText(LocaleController.getString("TwoStepVerification", R.string.TwoStepVerification), true);
                    } else if (position == passcodeRow) {
                        textCell.setText(LocaleController.getString("Passcode", R.string.Passcode), true);
                    } else if (position == lastSeenRow) {
                        String value;
                        if (ContactsController.getInstance().getLoadingLastSeenInfo()) {
                            value = LocaleController.getString("Loading", R.string.Loading);
                        } else {
                            value = formatRulesString(0);
                        }
                        textCell.setTextAndValue(LocaleController.getString("PrivacyLastSeen", R.string.PrivacyLastSeen), value, true);
                    } else if (position == callsRow) {
                        String value;
                        if (ContactsController.getInstance().getLoadingCallsInfo()) {
                            value = LocaleController.getString("Loading", R.string.Loading);
                        } else {
                            value = formatRulesString(2);
                        }
                        textCell.setTextAndValue(LocaleController.getString("Calls", R.string.Calls), value, true);
                    } else if (position == groupsRow) {
                        String value;
                        if (ContactsController.getInstance().getLoadingGroupInfo()) {
                            value = LocaleController.getString("Loading", R.string.Loading);
                        } else {
                            value = formatRulesString(1);
                        }
                        textCell.setTextAndValue(LocaleController.getString("GroupsAndChannels", R.string.GroupsAndChannels), value, false);
                    } else if (position == deleteAccountRow) {
                        String value;
                        if (ContactsController.getInstance().getLoadingDeleteInfo()) {
                            value = LocaleController.getString("Loading", R.string.Loading);
                        } else {
                            int ttl = ContactsController.getInstance().getDeleteAccountTTL();
                            if (ttl <= 182) {
                                value = LocaleController.formatPluralString("Months", ttl / 30);
                            } else if (ttl == 365) {
                                value = LocaleController.formatPluralString("Years", ttl / 365);
                            } else {
                                value = LocaleController.formatPluralString("Days", ttl);
                            }
                        }
                        textCell.setTextAndValue(LocaleController.getString("DeleteAccountIfAwayFor", R.string.DeleteAccountIfAwayFor), value, false);
                    } else if (position == paymentsClearRow) {
                        textCell.setText(LocaleController.getString("PrivacyPaymentsClear", R.string.PrivacyPaymentsClear), false);
                    } else if (position == callsP2PRow) {
                        SharedPreferences prefs=getParentActivity().getSharedPreferences("mainconfig", Context.MODE_PRIVATE);
                        String value;
                        switch(prefs.getInt("calls_p2p_new", MessagesController.getInstance().defaultP2pContacts ? 1 : 0)){
                            case 1:
                                value=LocaleController.getString("LastSeenContacts", R.string.LastSeenContacts);
                                break;
                            case 2:
                                value=LocaleController.getString("LastSeenNobody", R.string.LastSeenNobody);
                                break;
                            case 0:
                            default:
                                value=LocaleController.getString("LastSeenEverybody", R.string.LastSeenEverybody);
                                break;
                        }
                        textCell.setTextAndValue(LocaleController.getString("PrivacyCallsP2PTitle", R.string.PrivacyCallsP2PTitle), value, false);
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
                        privacyCell.setText("");
                        privacyCell.setBackgroundDrawable(Theme.getThemedDrawable(mContext, callsSectionRow == -1 ? R.drawable.greydivider_bottom : R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
                    } else if (position == paymentsDetailRow) {
                        privacyCell.setText(LocaleController.getString("PrivacyPaymentsClearInfo", R.string.PrivacyPaymentsClearInfo));
                        privacyCell.setBackgroundDrawable(Theme.getThemedDrawable(mContext, secretSectionRow == -1 && callsSectionRow == -1 ? R.drawable.greydivider_bottom : R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
                    } else if (position == callsDetailRow) {
                        privacyCell.setText(LocaleController.getString("PrivacyCallsP2PHelp", R.string.PrivacyCallsP2PHelp));
                        privacyCell.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                    }
                    break;
                case 2:
                    HeaderCell headerCell = (HeaderCell) holder.itemView;
                    if (position == privacySectionRow) {
                        headerCell.setText(LocaleController.getString("PrivacyTitle", R.string.PrivacyTitle));
                    } else if (position == securitySectionRow) {
                        headerCell.setText(LocaleController.getString("SecurityTitle", R.string.SecurityTitle));
                    } else if (position == deleteAccountSectionRow) {
                        headerCell.setText(LocaleController.getString("DeleteAccountTitle", R.string.DeleteAccountTitle));
                    } else if (position == secretSectionRow) {
                        headerCell.setText(LocaleController.getString("SecretChat", R.string.SecretChat));
                    } else if (position == paymentsSectionRow) {
                        headerCell.setText(LocaleController.getString("PrivacyPayments", R.string.PrivacyPayments));
                    } else if (position == callsSectionRow) {
                        headerCell.setText(LocaleController.getString("Calls", R.string.Calls));
                    }
                    break;
                case 3:
                    TextCheckCell textCheckCell = (TextCheckCell) holder.itemView;
                    if (position == secretWebpageRow) {
                        textCheckCell.setTextAndCheck(LocaleController.getString("SecretWebPage", R.string.SecretWebPage), MessagesController.getInstance().secretWebpagePreview == 1, true);
                    }
                    break;
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (position == lastSeenRow || position == blockedRow || position == deleteAccountRow || position == sessionsRow || position == passwordRow || position == passcodeRow || position == groupsRow || position == paymentsClearRow || position == callsP2PRow) {
                return 0;
            } else if (position == deleteAccountDetailRow || position == groupsDetailRow || position == sessionsDetailRow || position == secretDetailRow || position == paymentsDetailRow || position==callsDetailRow) {
                return 1;
            } else if (position == securitySectionRow || position == deleteAccountSectionRow || position == privacySectionRow || position == secretSectionRow || position == paymentsSectionRow || position==callsSectionRow) {
                return 2;
            } else if (position == secretWebpageRow) {
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
                new ThemeDescription(listView, 0, new Class[]{TextCheckCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switchThumb),
                new ThemeDescription(listView, 0, new Class[]{TextCheckCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switchTrack),
                new ThemeDescription(listView, 0, new Class[]{TextCheckCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switchThumbChecked),
                new ThemeDescription(listView, 0, new Class[]{TextCheckCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switchTrackChecked),
        };
    }
}
