/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2017.
 */

package org.telegram.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Spannable;
import android.text.method.LinkMovementMethod;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.messenger.support.widget.LinearLayoutManager;
import org.telegram.messenger.support.widget.RecyclerView;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.RadioCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;

public class PrivacyControlActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    private ListAdapter listAdapter;
    private View doneButton;
    private RecyclerListView listView;

    private int rulesType;
    private ArrayList<Integer> currentPlus;
    private ArrayList<Integer> currentMinus;
    private int lastCheckedType = -1;

    private int currentType;

    private boolean enableAnimation;

    private int sectionRow;
    private int everybodyRow;
    private int myContactsRow;
    private int nobodyRow;
    private int detailRow;
    private int shareSectionRow;
    private int alwaysShareRow;
    private int neverShareRow;
    private int shareDetailRow;
    private int rowCount;

    private final static int done_button = 1;

    private static class LinkMovementMethodMy extends LinkMovementMethod {
        @Override
        public boolean onTouchEvent(TextView widget, Spannable buffer, MotionEvent event) {
            try {
                return super.onTouchEvent(widget, buffer, event);
            } catch (Exception e) {
                FileLog.e(e);
            }
            return false;
        }
    }

    public PrivacyControlActivity(int type) {
        super();
        rulesType = type;
    }

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();
        checkPrivacy();
        updateRows();
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.privacyRulesUpdated);
        return true;
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.privacyRulesUpdated);
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        if (rulesType == 2) {
            actionBar.setTitle(LocaleController.getString("Calls", R.string.Calls));
        } else if (rulesType == 1) {
            actionBar.setTitle(LocaleController.getString("GroupsAndChannels", R.string.GroupsAndChannels));
        } else {
            actionBar.setTitle(LocaleController.getString("PrivacyLastSeen", R.string.PrivacyLastSeen));
        }
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                } else if (id == done_button) {
                    if (getParentActivity() == null) {
                        return;
                    }

                    if (currentType != 0 && rulesType == 0) {
                        final SharedPreferences preferences = MessagesController.getGlobalMainSettings();
                        boolean showed = preferences.getBoolean("privacyAlertShowed", false);
                        if (!showed) {
                            AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                            if (rulesType == 1) {
                                builder.setMessage(LocaleController.getString("WhoCanAddMeInfo", R.string.WhoCanAddMeInfo));
                            } else {
                                builder.setMessage(LocaleController.getString("CustomHelp", R.string.CustomHelp));
                            }
                            builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
                            builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), (dialogInterface, i) -> {
                                applyCurrentPrivacySettings();
                                preferences.edit().putBoolean("privacyAlertShowed", true).commit();
                            });
                            builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                            showDialog(builder.create());
                            return;
                        }
                    }
                    applyCurrentPrivacySettings();
                }
            }
        });

        int visibility = doneButton != null ? doneButton.getVisibility() : View.GONE;
        ActionBarMenu menu = actionBar.createMenu();
        doneButton = menu.addItemWithWidth(done_button, R.drawable.ic_done, AndroidUtilities.dp(56));
        doneButton.setVisibility(visibility);

        listAdapter = new ListAdapter(context);

        fragmentView = new FrameLayout(context);
        FrameLayout frameLayout = (FrameLayout) fragmentView;
        frameLayout.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));

        listView = new RecyclerListView(context);
        listView.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
        listView.setVerticalScrollBarEnabled(false);
        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        listView.setAdapter(listAdapter);
        listView.setOnItemClickListener((view, position) -> {
            if (position == nobodyRow || position == everybodyRow || position == myContactsRow) {
                int newType = currentType;
                if (position == nobodyRow) {
                    newType = 1;
                } else if (position == everybodyRow) {
                    newType = 0;
                } else if (position == myContactsRow) {
                    newType = 2;
                }
                if (newType == currentType) {
                    return;
                }
                enableAnimation = true;
                doneButton.setVisibility(View.VISIBLE);
                lastCheckedType = currentType;
                currentType = newType;
                updateRows();
            } else if (position == neverShareRow || position == alwaysShareRow) {
                ArrayList<Integer> createFromArray;
                if (position == neverShareRow) {
                    createFromArray = currentMinus;
                } else {
                    createFromArray = currentPlus;
                }
                if (createFromArray.isEmpty()) {
                    Bundle args = new Bundle();
                    args.putBoolean(position == neverShareRow ? "isNeverShare" : "isAlwaysShare", true);
                    args.putBoolean("isGroup", rulesType != 0);
                    GroupCreateActivity fragment = new GroupCreateActivity(args);
                    fragment.setDelegate(ids -> {
                        if (position == neverShareRow) {
                            currentMinus = ids;
                            for (int a = 0; a < currentMinus.size(); a++) {
                                currentPlus.remove(currentMinus.get(a));
                            }
                        } else {
                            currentPlus = ids;
                            for (int a = 0; a < currentPlus.size(); a++) {
                                currentMinus.remove(currentPlus.get(a));
                            }
                        }
                        doneButton.setVisibility(View.VISIBLE);
                        lastCheckedType = -1;
                        listAdapter.notifyDataSetChanged();
                    });
                    presentFragment(fragment);
                } else {
                    PrivacyUsersActivity fragment = new PrivacyUsersActivity(createFromArray, rulesType != 0, position == alwaysShareRow);
                    fragment.setDelegate((ids, added) -> {
                        if (position == neverShareRow) {
                            currentMinus = ids;
                            if (added) {
                                for (int a = 0; a < currentMinus.size(); a++) {
                                    currentPlus.remove(currentMinus.get(a));
                                }
                            }
                        } else {
                            currentPlus = ids;
                            if (added) {
                                for (int a = 0; a < currentPlus.size(); a++) {
                                    currentMinus.remove(currentPlus.get(a));
                                }
                            }
                        }
                        doneButton.setVisibility(View.VISIBLE);
                        listAdapter.notifyDataSetChanged();
                    });
                    presentFragment(fragment);
                }
            }
        });

        return fragmentView;
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.privacyRulesUpdated) {
            checkPrivacy();
        }
    }

    private void applyCurrentPrivacySettings() {
        TLRPC.TL_account_setPrivacy req = new TLRPC.TL_account_setPrivacy();
        if (rulesType == 2) {
            req.key = new TLRPC.TL_inputPrivacyKeyPhoneCall();
        } else if (rulesType == 1) {
            req.key = new TLRPC.TL_inputPrivacyKeyChatInvite();
        } else {
            req.key = new TLRPC.TL_inputPrivacyKeyStatusTimestamp();
        }
        if (currentType != 0 && currentPlus.size() > 0) {
            TLRPC.TL_inputPrivacyValueAllowUsers rule = new TLRPC.TL_inputPrivacyValueAllowUsers();
            for (int a = 0; a < currentPlus.size(); a++) {
                TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(currentPlus.get(a));
                if (user != null) {
                    TLRPC.InputUser inputUser = MessagesController.getInstance(currentAccount).getInputUser(user);
                    if (inputUser != null) {
                        rule.users.add(inputUser);
                    }
                }
            }
            req.rules.add(rule);
        }
        if (currentType != 1 && currentMinus.size() > 0) {
            TLRPC.TL_inputPrivacyValueDisallowUsers rule = new TLRPC.TL_inputPrivacyValueDisallowUsers();
            for (int a = 0; a < currentMinus.size(); a++) {
                TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(currentMinus.get(a));
                if (user != null) {
                    TLRPC.InputUser inputUser = MessagesController.getInstance(currentAccount).getInputUser(user);
                    if (inputUser != null) {
                        rule.users.add(inputUser);
                    }
                }
            }
            req.rules.add(rule);
        }
        if (currentType == 0) {
            req.rules.add(new TLRPC.TL_inputPrivacyValueAllowAll());
        } else if (currentType == 1) {
            req.rules.add(new TLRPC.TL_inputPrivacyValueDisallowAll());
        } else if (currentType == 2) {
            req.rules.add(new TLRPC.TL_inputPrivacyValueAllowContacts());
        }
        AlertDialog progressDialog = null;
        if (getParentActivity() != null) {
            progressDialog = new AlertDialog(getParentActivity(), 1);
            progressDialog.setMessage(LocaleController.getString("Loading", R.string.Loading));
            progressDialog.setCanceledOnTouchOutside(false);
            progressDialog.setCancelable(false);
            progressDialog.show();
        }
        final AlertDialog progressDialogFinal = progressDialog;
        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            try {
                if (progressDialogFinal != null) {
                    progressDialogFinal.dismiss();
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
            if (error == null) {
                finishFragment();
                TLRPC.TL_account_privacyRules rules = (TLRPC.TL_account_privacyRules) response;
                MessagesController.getInstance(currentAccount).putUsers(rules.users, false);
                ContactsController.getInstance(currentAccount).setPrivacyRules(rules.rules, rulesType);
            } else {
                showErrorAlert();
            }
        }), ConnectionsManager.RequestFlagFailOnServerErrors);
    }

    private void showErrorAlert() {
        if (getParentActivity() == null) {
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
        builder.setMessage(LocaleController.getString("PrivacyFloodControlError", R.string.PrivacyFloodControlError));
        builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), null);
        showDialog(builder.create());
    }

    private void checkPrivacy() {
        currentPlus = new ArrayList<>();
        currentMinus = new ArrayList<>();
        ArrayList<TLRPC.PrivacyRule> privacyRules = ContactsController.getInstance(currentAccount).getPrivacyRules(rulesType);
        if (privacyRules == null || privacyRules.size() == 0) {
            currentType = 1;
            return;
        }
        int type = -1;
        for (int a = 0; a < privacyRules.size(); a++) {
            TLRPC.PrivacyRule rule = privacyRules.get(a);
            if (rule instanceof TLRPC.TL_privacyValueAllowUsers) {
                currentPlus.addAll(rule.users);
            } else if (rule instanceof TLRPC.TL_privacyValueDisallowUsers) {
                currentMinus.addAll(rule.users);
            } else if (rule instanceof TLRPC.TL_privacyValueAllowAll) {
                type = 0;
            } else if (rule instanceof TLRPC.TL_privacyValueDisallowAll) {
                type = 1;
            } else {
                type = 2;
            }
        }
        if (type == 0 || type == -1 && currentMinus.size() > 0) {
            currentType = 0;
        } else if (type == 2 || type == -1 && currentMinus.size() > 0 && currentPlus.size() > 0) {
            currentType = 2;
        } else if (type == 1 || type == -1 && currentPlus.size() > 0) {
            currentType = 1;
        }
        if (doneButton != null) {
            doneButton.setVisibility(View.GONE);
        }
        updateRows();
    }

    private void updateRows() {
        rowCount = 0;
        sectionRow = rowCount++;
        everybodyRow = rowCount++;
        myContactsRow = rowCount++;
        if (rulesType != 0 && rulesType != 2) {
            nobodyRow = -1;
        } else {
            nobodyRow = rowCount++;
        }
        detailRow = rowCount++;
        shareSectionRow = rowCount++;
        if (currentType == 1 || currentType == 2) {
            alwaysShareRow = rowCount++;
        } else {
            alwaysShareRow = -1;
        }
        if (currentType == 0 || currentType == 2) {
            neverShareRow = rowCount++;
        } else {
            neverShareRow = -1;
        }
        shareDetailRow = rowCount++;
        if (listAdapter != null) {
            listAdapter.notifyDataSetChanged();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        lastCheckedType = -1;
        enableAnimation = false;
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {
        private Context mContext;

        public ListAdapter(Context context) {
            mContext = context;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            int position = holder.getAdapterPosition();
            return position == nobodyRow || position == everybodyRow || position == myContactsRow || position == neverShareRow || position == alwaysShareRow;
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
                    view = new RadioCell(mContext);
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
                    if (position == alwaysShareRow) {
                        String value;
                        if (currentPlus.size() != 0) {
                            value = LocaleController.formatPluralString("Users", currentPlus.size());
                        } else {
                            value = LocaleController.getString("EmpryUsersPlaceholder", R.string.EmpryUsersPlaceholder);
                        }
                        if (rulesType != 0) {
                            textCell.setTextAndValue(LocaleController.getString("AlwaysAllow", R.string.AlwaysAllow), value, neverShareRow != -1);
                        } else {
                            textCell.setTextAndValue(LocaleController.getString("AlwaysShareWith", R.string.AlwaysShareWith), value, neverShareRow != -1);
                        }
                    } else if (position == neverShareRow) {
                        String value;
                        if (currentMinus.size() != 0) {
                            value = LocaleController.formatPluralString("Users", currentMinus.size());
                        } else {
                            value = LocaleController.getString("EmpryUsersPlaceholder", R.string.EmpryUsersPlaceholder);
                        }
                        if (rulesType != 0) {
                            textCell.setTextAndValue(LocaleController.getString("NeverAllow", R.string.NeverAllow), value, false);
                        } else {
                            textCell.setTextAndValue(LocaleController.getString("NeverShareWith", R.string.NeverShareWith), value, false);
                        }
                    }
                    break;
                case 1:
                    TextInfoPrivacyCell privacyCell = (TextInfoPrivacyCell) holder.itemView;
                    if (position == detailRow) {
                        if (rulesType == 2) {
                            privacyCell.setText(LocaleController.getString("WhoCanCallMeInfo", R.string.WhoCanCallMeInfo));
                        } else if (rulesType == 1) {
                            privacyCell.setText(LocaleController.getString("WhoCanAddMeInfo", R.string.WhoCanAddMeInfo));
                        } else {
                            privacyCell.setText(LocaleController.getString("CustomHelp", R.string.CustomHelp));
                        }
                        privacyCell.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
                    } else if (position == shareDetailRow) {
                        if (rulesType == 2) {
                            privacyCell.setText(LocaleController.getString("CustomCallInfo", R.string.CustomCallInfo));
                        } else if (rulesType == 1) {
                            privacyCell.setText(LocaleController.getString("CustomShareInfo", R.string.CustomShareInfo));
                        } else {
                            privacyCell.setText(LocaleController.getString("CustomShareSettingsHelp", R.string.CustomShareSettingsHelp));
                        }
                        privacyCell.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                    }
                    break;
                case 2:
                    HeaderCell headerCell = (HeaderCell) holder.itemView;
                    if (position == sectionRow) {
                        if (rulesType == 2) {
                            headerCell.setText(LocaleController.getString("WhoCanCallMe", R.string.WhoCanCallMe));
                        } else if (rulesType == 1) {
                            headerCell.setText(LocaleController.getString("WhoCanAddMe", R.string.WhoCanAddMe));
                        } else {
                            headerCell.setText(LocaleController.getString("LastSeenTitle", R.string.LastSeenTitle));
                        }
                    } else if (position == shareSectionRow) {
                        headerCell.setText(LocaleController.getString("AddExceptions", R.string.AddExceptions));
                    }
                    break;
                case 3:
                    RadioCell radioCell = (RadioCell) holder.itemView;
                    int checkedType = 0;
                    if (position == everybodyRow) {
                        radioCell.setText(LocaleController.getString("LastSeenEverybody", R.string.LastSeenEverybody), lastCheckedType == 0, true);
                        checkedType = 0;
                    } else if (position == myContactsRow) {
                        radioCell.setText(LocaleController.getString("LastSeenContacts", R.string.LastSeenContacts), lastCheckedType == 2, nobodyRow != -1);
                        checkedType = 2;
                    } else if (position == nobodyRow) {
                        radioCell.setText(LocaleController.getString("LastSeenNobody", R.string.LastSeenNobody), lastCheckedType == 1, false);
                        checkedType = 1;
                    }
                    if (lastCheckedType == checkedType) {
                        radioCell.setChecked(false, enableAnimation);
                    } else if (currentType == checkedType) {
                        radioCell.setChecked(true, enableAnimation);
                    }
                    break;
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (position == alwaysShareRow || position == neverShareRow) {
                return 0;
            } else if (position == shareDetailRow || position == detailRow) {
                return 1;
            } else if (position == sectionRow || position == shareSectionRow) {
                return 2;
            } else if (position == everybodyRow || position == myContactsRow || position == nobodyRow) {
                return 3;
            }
            return 0;
        }
    }

    @Override
    public ThemeDescription[] getThemeDescriptions() {
        return new ThemeDescription[]{
                new ThemeDescription(listView, ThemeDescription.FLAG_CELLBACKGROUNDCOLOR, new Class[]{TextSettingsCell.class, HeaderCell.class, RadioCell.class}, null, null, null, Theme.key_windowBackgroundWhite),
                new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundGray),

                new ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_actionBarDefault),
                new ThemeDescription(listView, ThemeDescription.FLAG_LISTGLOWCOLOR, null, null, null, null, Theme.key_actionBarDefault),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_ITEMSCOLOR, null, null, null, null, Theme.key_actionBarDefaultIcon),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_TITLECOLOR, null, null, null, null, Theme.key_actionBarDefaultTitle),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_actionBarDefaultSelector),

                new ThemeDescription(listView, ThemeDescription.FLAG_SELECTOR, null, null, null, null, Theme.key_listSelector),

                new ThemeDescription(listView, 0, new Class[]{View.class}, Theme.dividerPaint, null, null, Theme.key_divider),

                new ThemeDescription(listView, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{TextInfoPrivacyCell.class}, null, null, null, Theme.key_windowBackgroundGrayShadow),

                new ThemeDescription(listView, 0, new Class[]{TextSettingsCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText),
                new ThemeDescription(listView, 0, new Class[]{TextSettingsCell.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteValueText),

                new ThemeDescription(listView, 0, new Class[]{TextInfoPrivacyCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText4),

                new ThemeDescription(listView, 0, new Class[]{HeaderCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlueHeader),

                new ThemeDescription(listView, 0, new Class[]{RadioCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText),
                new ThemeDescription(listView, ThemeDescription.FLAG_CHECKBOX, new Class[]{RadioCell.class}, new String[]{"radioButton"}, null, null, null, Theme.key_radioBackground),
                new ThemeDescription(listView, ThemeDescription.FLAG_CHECKBOXCHECK, new Class[]{RadioCell.class}, new String[]{"radioButton"}, null, null, null, Theme.key_radioBackgroundChecked),
        };
    }
}
