/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2016.
 */

package org.telegram.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Spannable;
import android.text.method.LinkMovementMethod;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.FrameLayout;
import android.widget.ListView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.RequestDelegate;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Adapters.BaseFragmentAdapter;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.RadioCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.LayoutHelper;

import java.util.ArrayList;

public class PrivacyControlActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    private ListAdapter listAdapter;
    private View doneButton;

    private int currentType = 0;
    private ArrayList<Integer> currentPlus;
    private ArrayList<Integer> currentMinus;
    private int lastCheckedType = -1;

    private boolean isGroup;

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
                FileLog.e("tmessages", e);
            }
            return false;
        }
    }

    public PrivacyControlActivity(boolean group) {
        super();
        isGroup = group;
    }

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();
        checkPrivacy();
        updateRows();
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.privacyRulesUpdated);
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
        if (isGroup) {
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

                    if (currentType != 0 && !isGroup) {
                        final SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
                        boolean showed = preferences.getBoolean("privacyAlertShowed", false);
                        if (!showed) {
                            AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                            if (isGroup) {
                                builder.setMessage(LocaleController.getString("WhoCanAddMeInfo", R.string.WhoCanAddMeInfo));
                            } else {
                                builder.setMessage(LocaleController.getString("CustomHelp", R.string.CustomHelp));
                            }
                            builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
                            builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialogInterface, int i) {
                                    applyCurrentPrivacySettings();
                                    preferences.edit().putBoolean("privacyAlertShowed", true).commit();
                                }
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

        ActionBarMenu menu = actionBar.createMenu();
        doneButton = menu.addItemWithWidth(done_button, R.drawable.ic_done, AndroidUtilities.dp(56));
        doneButton.setVisibility(View.GONE);

        listAdapter = new ListAdapter(context);

        fragmentView = new FrameLayout(context);
        FrameLayout frameLayout = (FrameLayout) fragmentView;
        frameLayout.setBackgroundColor(0xfff0f0f0);

        ListView listView = new ListView(context);
        listView.setDivider(null);
        listView.setDividerHeight(0);
        listView.setVerticalScrollBarEnabled(false);
        listView.setDrawSelectorOnTop(true);
        frameLayout.addView(listView);
        FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) listView.getLayoutParams();
        layoutParams.width = LayoutHelper.MATCH_PARENT;
        layoutParams.height = LayoutHelper.MATCH_PARENT;
        layoutParams.gravity = Gravity.TOP;
        listView.setLayoutParams(layoutParams);
        listView.setAdapter(listAdapter);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, final int i, long l) {
                if (i == nobodyRow || i == everybodyRow || i == myContactsRow) {
                    int newType = currentType;
                    if (i == nobodyRow) {
                        newType = 1;
                    } else if (i == everybodyRow) {
                        newType = 0;
                    } else if (i == myContactsRow) {
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
                } else if (i == neverShareRow || i == alwaysShareRow) {
                    ArrayList<Integer> createFromArray;
                    if (i == neverShareRow) {
                        createFromArray = currentMinus;
                    } else {
                        createFromArray = currentPlus;
                    }
                    if (createFromArray.isEmpty()) {
                        Bundle args = new Bundle();
                        args.putBoolean(i == neverShareRow ? "isNeverShare" : "isAlwaysShare", true);
                        args.putBoolean("isGroup", isGroup);
                        GroupCreateActivity fragment = new GroupCreateActivity(args);
                        fragment.setDelegate(new GroupCreateActivity.GroupCreateActivityDelegate() {
                            @Override
                            public void didSelectUsers(ArrayList<Integer> ids) {
                                if (i == neverShareRow) {
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
                            }
                        });
                        presentFragment(fragment);
                    } else {
                        PrivacyUsersActivity fragment = new PrivacyUsersActivity(createFromArray, isGroup, i == alwaysShareRow);
                        fragment.setDelegate(new PrivacyUsersActivity.PrivacyActivityDelegate() {
                            @Override
                            public void didUpdatedUserList(ArrayList<Integer> ids, boolean added) {
                                if (i == neverShareRow) {
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
                            }
                        });
                        presentFragment(fragment);
                    }
                }
            }
        });

        return fragmentView;
    }

    @Override
    public void didReceivedNotification(int id, Object... args) {
        if (id == NotificationCenter.privacyRulesUpdated) {
            checkPrivacy();
        }
    }

    private void applyCurrentPrivacySettings() {
        TLRPC.TL_account_setPrivacy req = new TLRPC.TL_account_setPrivacy();
        if (isGroup) {
            req.key = new TLRPC.TL_inputPrivacyKeyChatInvite();
        } else {
            req.key = new TLRPC.TL_inputPrivacyKeyStatusTimestamp();
        }
        if (currentType != 0 && currentPlus.size() > 0) {
            TLRPC.TL_inputPrivacyValueAllowUsers rule = new TLRPC.TL_inputPrivacyValueAllowUsers();
            for (int a = 0; a < currentPlus.size(); a++) {
                TLRPC.User user = MessagesController.getInstance().getUser(currentPlus.get(a));
                if (user != null) {
                    TLRPC.InputUser inputUser = MessagesController.getInputUser(user);
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
                TLRPC.User user = MessagesController.getInstance().getUser(currentMinus.get(a));
                if (user != null) {
                    TLRPC.InputUser inputUser = MessagesController.getInputUser(user);
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
        ProgressDialog progressDialog = null;
        if (getParentActivity() != null) {
            progressDialog = new ProgressDialog(getParentActivity());
            progressDialog.setMessage(LocaleController.getString("Loading", R.string.Loading));
            progressDialog.setCanceledOnTouchOutside(false);
            progressDialog.setCancelable(false);
            progressDialog.show();
        }
        final ProgressDialog progressDialogFinal = progressDialog;
        ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
            @Override
            public void run(final TLObject response, final TLRPC.TL_error error) {
                AndroidUtilities.runOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            if (progressDialogFinal != null) {
                                progressDialogFinal.dismiss();
                            }
                        } catch (Exception e) {
                            FileLog.e("tmessages", e);
                        }
                        if (error == null) {
                            finishFragment();
                            TLRPC.TL_account_privacyRules rules = (TLRPC.TL_account_privacyRules) response;
                            MessagesController.getInstance().putUsers(rules.users, false);
                            ContactsController.getInstance().setPrivacyRules(rules.rules, isGroup);
                        } else {
                            showErrorAlert();
                        }
                    }
                });
            }
        }, ConnectionsManager.RequestFlagFailOnServerErrors);
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
        ArrayList<TLRPC.PrivacyRule> privacyRules = ContactsController.getInstance().getPrivacyRules(isGroup);
        if (privacyRules.size() == 0) {
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
        if (isGroup) {
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

    private class ListAdapter extends BaseFragmentAdapter {
        private Context mContext;

        public ListAdapter(Context context) {
            mContext = context;
        }

        @Override
        public boolean areAllItemsEnabled() {
            return false;
        }

        @Override
        public boolean isEnabled(int i) {
            return i == nobodyRow || i == everybodyRow || i == myContactsRow || i == neverShareRow || i == alwaysShareRow;
        }

        @Override
        public int getCount() {
            return rowCount;
        }

        @Override
        public Object getItem(int i) {
            return null;
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @Override
        public boolean hasStableIds() {
            return false;
        }

        @Override
        public View getView(int i, View view, ViewGroup viewGroup) {
            int type = getItemViewType(i);
            if (type == 0) {
                if (view == null) {
                    view = new TextSettingsCell(mContext);
                    view.setBackgroundColor(0xffffffff);
                }
                TextSettingsCell textCell = (TextSettingsCell) view;
                if (i == alwaysShareRow) {
                    String value;
                    if (currentPlus.size() != 0) {
                        value = LocaleController.formatPluralString("Users", currentPlus.size());
                    } else {
                        value = LocaleController.getString("EmpryUsersPlaceholder", R.string.EmpryUsersPlaceholder);
                    }
                    if (isGroup) {
                        textCell.setTextAndValue(LocaleController.getString("AlwaysAllow", R.string.AlwaysAllow), value, neverShareRow != -1);
                    } else {
                        textCell.setTextAndValue(LocaleController.getString("AlwaysShareWith", R.string.AlwaysShareWith), value, neverShareRow != -1);
                    }
                } else if (i == neverShareRow) {
                    String value;
                    if (currentMinus.size() != 0) {
                        value = LocaleController.formatPluralString("Users", currentMinus.size());
                    } else {
                        value = LocaleController.getString("EmpryUsersPlaceholder", R.string.EmpryUsersPlaceholder);
                    }
                    if (isGroup) {
                        textCell.setTextAndValue(LocaleController.getString("NeverAllow", R.string.NeverAllow), value, false);
                    } else {
                        textCell.setTextAndValue(LocaleController.getString("NeverShareWith", R.string.NeverShareWith), value, false);
                    }
                }
            } else if (type == 1) {
                if (view == null) {
                    view = new TextInfoPrivacyCell(mContext);
                    view.setBackgroundColor(0xffffffff);
                }
                if (i == detailRow) {
                    if (isGroup) {
                        ((TextInfoPrivacyCell) view).setText(LocaleController.getString("WhoCanAddMeInfo", R.string.WhoCanAddMeInfo));
                    } else {
                        ((TextInfoPrivacyCell) view).setText(LocaleController.getString("CustomHelp", R.string.CustomHelp));
                    }
                    view.setBackgroundResource(R.drawable.greydivider);
                } else if (i == shareDetailRow) {
                    if (isGroup) {
                        ((TextInfoPrivacyCell) view).setText(LocaleController.getString("CustomShareInfo", R.string.CustomShareInfo));
                    } else {
                        ((TextInfoPrivacyCell) view).setText(LocaleController.getString("CustomShareSettingsHelp", R.string.CustomShareSettingsHelp));
                    }
                    view.setBackgroundResource(R.drawable.greydivider_bottom);
                }
            } else if (type == 2) {
                if (view == null) {
                    view = new HeaderCell(mContext);
                    view.setBackgroundColor(0xffffffff);
                }
                if (i == sectionRow) {
                    if (isGroup) {
                        ((HeaderCell) view).setText(LocaleController.getString("WhoCanAddMe", R.string.WhoCanAddMe));
                    } else {
                        ((HeaderCell) view).setText(LocaleController.getString("LastSeenTitle", R.string.LastSeenTitle));
                    }
                } else if (i == shareSectionRow) {
                    ((HeaderCell) view).setText(LocaleController.getString("AddExceptions", R.string.AddExceptions));
                }
            } else if (type == 3) {
                if (view == null) {
                    view = new RadioCell(mContext);
                    view.setBackgroundColor(0xffffffff);
                }
                RadioCell textCell = (RadioCell) view;
                int checkedType = 0;
                if (i == everybodyRow) {
                    textCell.setText(LocaleController.getString("LastSeenEverybody", R.string.LastSeenEverybody), lastCheckedType == 0, true);
                    checkedType = 0;
                } else if (i == myContactsRow) {
                    textCell.setText(LocaleController.getString("LastSeenContacts", R.string.LastSeenContacts), lastCheckedType == 2, nobodyRow != -1);
                    checkedType = 2;
                } else if (i == nobodyRow) {
                    textCell.setText(LocaleController.getString("LastSeenNobody", R.string.LastSeenNobody), lastCheckedType == 1, false);
                    checkedType = 1;
                }
                if (lastCheckedType == checkedType) {
                    textCell.setChecked(false, enableAnimation);
                } else if (currentType == checkedType) {
                    textCell.setChecked(true, enableAnimation);
                }
            }
            return view;
        }

        @Override
        public int getItemViewType(int i) {
            if (i == alwaysShareRow || i == neverShareRow) {
                return 0;
            } else if (i == shareDetailRow || i == detailRow) {
                return 1;
            } else if (i == sectionRow || i == shareSectionRow) {
                return 2;
            } else if (i == everybodyRow || i == myContactsRow || i == nobodyRow) {
                return 3;
            }
            return 0;
        }

        @Override
        public int getViewTypeCount() {
            return 4;
        }

        @Override
        public boolean isEmpty() {
            return false;
        }
    }
}
