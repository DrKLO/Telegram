/*
 * This is the source code of Telegram for Android v. 1.7.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2014.
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
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.FrameLayout;
import android.widget.ListView;
import android.widget.TextView;

import org.telegram.android.AndroidUtilities;
import org.telegram.android.ContactsController;
import org.telegram.android.LocaleController;
import org.telegram.android.MessagesController;
import org.telegram.android.NotificationCenter;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.ConnectionsManager;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.messenger.RPCRequest;
import org.telegram.messenger.TLObject;
import org.telegram.messenger.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Adapters.BaseFragmentAdapter;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;

import java.util.ArrayList;

public class LastSeenActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    private ListAdapter listAdapter;
    private View doneButton;

    private int currentType = 0;
    private ArrayList<Integer> currentPlus;
    private ArrayList<Integer> currentMinus;

    private int lastSeenSectionRow;
    private int everybodyRow;
    private int myContactsRow;
    private int nobodyRow;
    private int lastSeenDetailRow;
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
    public View createView(LayoutInflater inflater, ViewGroup container) {
        if (fragmentView == null) {
            actionBar.setBackButtonImage(R.drawable.ic_ab_back);
            actionBar.setAllowOverlayTitle(true);
            actionBar.setTitle(LocaleController.getString("PrivacyLastSeen", R.string.PrivacyLastSeen));
            actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
                @Override
                public void onItemClick(int id) {
                    if (id == -1) {
                        finishFragment();
                    } else if (id == done_button) {
                        if (getParentActivity() == null) {
                            return;
                        }

                        if (currentType != 0) {
                            final SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
                            boolean showed = preferences.getBoolean("privacyAlertShowed", false);
                            if (!showed) {
                                AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                                builder.setMessage(LocaleController.getString("CustomHelp", R.string.CustomHelp));
                                builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
                                builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialogInterface, int i) {
                                        applyCurrentPrivacySettings();
                                        preferences.edit().putBoolean("privacyAlertShowed", true).commit();
                                    }
                                });
                                builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                                showAlertDialog(builder);
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

            listAdapter = new ListAdapter(getParentActivity());

            fragmentView = new FrameLayout(getParentActivity());
            FrameLayout frameLayout = (FrameLayout) fragmentView;
            frameLayout.setBackgroundColor(0xfff0f0f0);

            ListView listView = new ListView(getParentActivity());
            listView.setDivider(null);
            listView.setDividerHeight(0);
            listView.setVerticalScrollBarEnabled(false);
            listView.setDrawSelectorOnTop(true);
            frameLayout.addView(listView);
            FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) listView.getLayoutParams();
            layoutParams.width = FrameLayout.LayoutParams.MATCH_PARENT;
            layoutParams.height = FrameLayout.LayoutParams.MATCH_PARENT;
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
                        doneButton.setVisibility(View.VISIBLE);
                        currentType = newType;
                        updateRows();
                    } else if (i == neverShareRow || i == alwaysShareRow) {
                        ArrayList<Integer> createFromArray = null;
                        if (i == neverShareRow) {
                            createFromArray = currentMinus;
                        } else {
                            createFromArray = currentPlus;
                        }
                        if (createFromArray.isEmpty()) {
                            Bundle args = new Bundle();
                            args.putBoolean(i == neverShareRow ? "isNeverShare" : "isAlwaysShare", true);
                            GroupCreateActivity fragment = new GroupCreateActivity(args);
                            fragment.setDelegate(new GroupCreateActivity.GroupCreateActivityDelegate() {
                                @Override
                                public void didSelectUsers(ArrayList<Integer> ids) {
                                    if (i == neverShareRow) {
                                        currentMinus = ids;
                                        for (Integer id : currentMinus) {
                                            currentPlus.remove(id);
                                        }
                                    } else {
                                        currentPlus = ids;
                                        for (Integer id : currentPlus) {
                                            currentMinus.remove(id);
                                        }
                                    }
                                    doneButton.setVisibility(View.VISIBLE);
                                    listAdapter.notifyDataSetChanged();
                                }
                            });
                            presentFragment(fragment);
                        } else {
                            LastSeenUsersActivity fragment = new LastSeenUsersActivity(createFromArray, i == alwaysShareRow);
                            fragment.setDelegate(new LastSeenUsersActivity.LastSeenUsersActivityDelegate() {
                                @Override
                                public void didUpdatedUserList(ArrayList<Integer> ids, boolean added) {
                                    if (i == neverShareRow) {
                                        currentMinus = ids;
                                        if (added) {
                                            for (Integer id : currentMinus) {
                                                currentPlus.remove(id);
                                            }
                                        }
                                    } else {
                                        currentPlus = ids;
                                        if (added) {
                                            for (Integer id : currentPlus) {
                                                currentMinus.remove(id);
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
        } else {
            ViewGroup parent = (ViewGroup) fragmentView.getParent();
            if (parent != null) {
                parent.removeView(fragmentView);
            }
        }
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
        req.key = new TLRPC.TL_inputPrivacyKeyStatusTimestamp();
        if (currentType != 0 && currentPlus.size() > 0) {
            TLRPC.TL_inputPrivacyValueAllowUsers rule = new TLRPC.TL_inputPrivacyValueAllowUsers();
            for (Integer uid : currentPlus) {
                TLRPC.User user = MessagesController.getInstance().getUser(uid);
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
            for (Integer uid : currentMinus) {
                TLRPC.User user = MessagesController.getInstance().getUser(uid);
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
        final ProgressDialog progressDialog = new ProgressDialog(getParentActivity());
        progressDialog.setMessage(LocaleController.getString("Loading", R.string.Loading));
        progressDialog.setCanceledOnTouchOutside(false);
        progressDialog.setCancelable(false);
        progressDialog.show();

        ConnectionsManager.getInstance().performRpc(req, new RPCRequest.RPCRequestDelegate() {
            @Override
            public void run(final TLObject response, final TLRPC.TL_error error) {
                AndroidUtilities.runOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            progressDialog.dismiss();
                        } catch (Exception e) {
                            FileLog.e("tmessages", e);
                        }
                        if (error == null) {
                            finishFragment();
                            TLRPC.TL_account_privacyRules rules = (TLRPC.TL_account_privacyRules) response;
                            MessagesController.getInstance().putUsers(rules.users, false);
                            ContactsController.getInstance().setPrivacyRules(rules.rules);
                        } else {
                            showErrorAlert();
                        }
                    }
                });
            }
        }, true, RPCRequest.RPCRequestClassGeneric | RPCRequest.RPCRequestClassFailOnServerErrors);
    }

    private void showErrorAlert() {
        if (getParentActivity() == null) {
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
        builder.setMessage(LocaleController.getString("PrivacyFloodControlError", R.string.PrivacyFloodControlError));
        builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), null);
        showAlertDialog(builder);
    }

    private void checkPrivacy() {
        currentPlus = new ArrayList<Integer>();
        currentMinus = new ArrayList<Integer>();
        ArrayList<TLRPC.PrivacyRule> privacyRules = ContactsController.getInstance().getPrivacyRules();
        if (privacyRules.size() == 0) {
            currentType = 1;
            return;
        }
        int type = -1;
        for (TLRPC.PrivacyRule rule : privacyRules) {
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
        lastSeenSectionRow = rowCount++;
        everybodyRow = rowCount++;
        myContactsRow = rowCount++;
        nobodyRow = rowCount++;
        lastSeenDetailRow = rowCount++;
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
        if (listAdapter != null) {
            listAdapter.notifyDataSetChanged();
        }
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
                if (i == everybodyRow) {
                    textCell.setTextAndIcon(LocaleController.getString("LastSeenEverybody", R.string.LastSeenEverybody), currentType == 0 ? R.drawable.check_blue : 0, true);
                } else if (i == myContactsRow) {
                    textCell.setTextAndIcon(LocaleController.getString("LastSeenContacts", R.string.LastSeenContacts), currentType == 2 ? R.drawable.check_blue : 0, true);
                } else if (i == nobodyRow) {
                    textCell.setTextAndIcon(LocaleController.getString("LastSeenNobody", R.string.LastSeenNobody), currentType == 1 ? R.drawable.check_blue : 0, false);
                } else if (i == alwaysShareRow) {
                    String value;
                    if (currentPlus.size() != 0) {
                        value = LocaleController.formatPluralString("Users", currentPlus.size());
                    } else {
                        value = LocaleController.getString("EmpryUsersPlaceholder", R.string.EmpryUsersPlaceholder);
                    }
                    textCell.setTextAndValue(LocaleController.getString("AlwaysShareWith", R.string.AlwaysShareWith), value, neverShareRow != -1);
                } else if (i == neverShareRow) {
                    String value;
                    if (currentMinus.size() != 0) {
                        value = LocaleController.formatPluralString("Users", currentMinus.size());
                    } else {
                        value = LocaleController.getString("EmpryUsersPlaceholder", R.string.EmpryUsersPlaceholder);
                    }
                    textCell.setTextAndValue(LocaleController.getString("NeverShareWith", R.string.NeverShareWith), value, false);
                }
            } else if (type == 1) {
                if (view == null) {
                    view = new TextInfoPrivacyCell(mContext);
                    view.setBackgroundColor(0xffffffff);
                }
                if (i == lastSeenDetailRow) {
                    ((TextInfoPrivacyCell) view).setText(LocaleController.getString("CustomHelp", R.string.CustomHelp));
                    view.setBackgroundResource(R.drawable.greydivider);
                } else if (i == shareDetailRow) {
                    ((TextInfoPrivacyCell) view).setText(LocaleController.getString("CustomShareSettingsHelp", R.string.CustomShareSettingsHelp));
                    view.setBackgroundResource(R.drawable.greydivider_bottom);
                }
            } else if (type == 2) {
                if (view == null) {
                    view = new HeaderCell(mContext);
                    view.setBackgroundColor(0xffffffff);
                }
                if (i == lastSeenSectionRow) {
                    ((HeaderCell) view).setText(LocaleController.getString("LastSeenTitle", R.string.LastSeenTitle));
                } else if (i == shareSectionRow) {
                    ((HeaderCell) view).setText(LocaleController.getString("AddExceptions", R.string.AddExceptions));
                }
            }
            return view;
        }

        @Override
        public int getItemViewType(int i) {
            if (i == alwaysShareRow || i == neverShareRow || i == everybodyRow || i == myContactsRow || i == nobodyRow) {
                return 0;
            } else if (i == shareDetailRow || i == lastSeenDetailRow) {
                return 1;
            } else if (i == lastSeenSectionRow || i == shareSectionRow) {
                return 2;
            }
            return 0;
        }

        @Override
        public int getViewTypeCount() {
            return 3;
        }

        @Override
        public boolean isEmpty() {
            return false;
        }
    }
}
