/*
 * This is the source code of Telegram for Android v. 5.x.x
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.os.Build;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.CheckBoxCell;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.SessionCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.EmptyTextProgressView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public class SessionsActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    private ListAdapter listAdapter;
    private RecyclerListView listView;
    private ImageView imageView;
    private TextView textView1;
    private TextView textView2;
    private EmptyTextProgressView emptyView;

    private ArrayList<TLObject> sessions = new ArrayList<>();
    private ArrayList<TLObject> passwordSessions = new ArrayList<>();
    private TLRPC.TL_authorization currentSession;
    private boolean loading;
    private LinearLayout emptyLayout;

    private int currentType;

    private int currentSessionSectionRow;
    private int currentSessionRow;
    private int terminateAllSessionsRow;
    private int terminateAllSessionsDetailRow;
    private int passwordSessionsSectionRow;
    private int passwordSessionsStartRow;
    private int passwordSessionsEndRow;
    private int passwordSessionsDetailRow;
    private int otherSessionsSectionRow;
    private int otherSessionsStartRow;
    private int otherSessionsEndRow;
    private int otherSessionsTerminateDetail;
    private int noOtherSessionsRow;
    private int rowCount;

    public SessionsActivity(int type) {
        currentType = type;
    }

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();
        updateRows();
        loadSessions(false);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.newSessionReceived);
        return true;
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.newSessionReceived);
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        if (currentType == 0) {
            actionBar.setTitle(LocaleController.getString("SessionsTitle", R.string.SessionsTitle));
        } else {
            actionBar.setTitle(LocaleController.getString("WebSessionsTitle", R.string.WebSessionsTitle));
        }
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

        emptyLayout = new LinearLayout(context);
        emptyLayout.setOrientation(LinearLayout.VERTICAL);
        emptyLayout.setGravity(Gravity.CENTER);
        emptyLayout.setBackgroundDrawable(Theme.getThemedDrawable(context, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
        emptyLayout.setLayoutParams(new AbsListView.LayoutParams(AbsListView.LayoutParams.MATCH_PARENT, AndroidUtilities.displaySize.y - ActionBar.getCurrentActionBarHeight()));

        imageView = new ImageView(context);
        if (currentType == 0) {
            imageView.setImageResource(R.drawable.devices);
        } else {
            imageView.setImageResource(R.drawable.no_apps);
        }
        imageView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_sessions_devicesImage), PorterDuff.Mode.MULTIPLY));
        emptyLayout.addView(imageView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        textView1 = new TextView(context);
        textView1.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2));
        textView1.setGravity(Gravity.CENTER);
        textView1.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17);
        textView1.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        if (currentType == 0) {
            textView1.setText(LocaleController.getString("NoOtherSessions", R.string.NoOtherSessions));
        } else {
            textView1.setText(LocaleController.getString("NoOtherWebSessions", R.string.NoOtherWebSessions));
        }
        emptyLayout.addView(textView1, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 0, 16, 0, 0));

        textView2 = new TextView(context);
        textView2.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2));
        textView2.setGravity(Gravity.CENTER);
        textView2.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17);
        textView2.setPadding(AndroidUtilities.dp(20), 0, AndroidUtilities.dp(20), 0);
        if (currentType == 0) {
            textView2.setText(LocaleController.getString("NoOtherSessionsInfo", R.string.NoOtherSessionsInfo));
        } else {
            textView2.setText(LocaleController.getString("NoOtherWebSessionsInfo", R.string.NoOtherWebSessionsInfo));
        }
        emptyLayout.addView(textView2, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 0, 14, 0, 0));

        emptyView = new EmptyTextProgressView(context);
        emptyView.showProgress();
        frameLayout.addView(emptyView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.CENTER));

        listView = new RecyclerListView(context);
        listView.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
        listView.setVerticalScrollBarEnabled(false);
        listView.setEmptyView(emptyView);
        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        listView.setAdapter(listAdapter);
        listView.setOnItemClickListener((view, position) -> {
            if (position == terminateAllSessionsRow) {
                if (getParentActivity() == null) {
                    return;
                }
                AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                if (currentType == 0) {
                    builder.setMessage(LocaleController.getString("AreYouSureSessions", R.string.AreYouSureSessions));
                } else {
                    builder.setMessage(LocaleController.getString("AreYouSureWebSessions", R.string.AreYouSureWebSessions));
                }
                builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
                builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), (dialogInterface, i) -> {
                    if (currentType == 0) {
                        TLRPC.TL_auth_resetAuthorizations req = new TLRPC.TL_auth_resetAuthorizations();
                        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {
                            AndroidUtilities.runOnUIThread(() -> {
                                if (getParentActivity() == null) {
                                    return;
                                }
                                if (error == null && response instanceof TLRPC.TL_boolTrue) {
                                    Toast toast = Toast.makeText(getParentActivity(), LocaleController.getString("TerminateAllSessions", R.string.TerminateAllSessions), Toast.LENGTH_SHORT);
                                    toast.show();
                                    finishFragment();
                                }
                            });

                            for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
                                UserConfig userConfig = UserConfig.getInstance(a);
                                if (!userConfig.isClientActivated()) {
                                    continue;
                                }
                                userConfig.registeredForPush = false;
                                userConfig.saveConfig(false);
                                MessagesController.getInstance(a).registerForPush(SharedConfig.pushString);
                                ConnectionsManager.getInstance(a).setUserId(userConfig.getClientUserId());
                            }
                        });
                    } else {
                        TLRPC.TL_account_resetWebAuthorizations req = new TLRPC.TL_account_resetWebAuthorizations();
                        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                            if (getParentActivity() == null) {
                                return;
                            }
                            if (error == null && response instanceof TLRPC.TL_boolTrue) {
                                Toast toast = Toast.makeText(getParentActivity(), LocaleController.getString("TerminateAllWebSessions", R.string.TerminateAllWebSessions), Toast.LENGTH_SHORT);
                                toast.show();
                            } else {
                                Toast toast = Toast.makeText(getParentActivity(), LocaleController.getString("UnknownError", R.string.UnknownError), Toast.LENGTH_SHORT);
                                toast.show();
                            }
                            finishFragment();
                        }));
                    }
                });
                builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                showDialog(builder.create());
            } else if (position >= otherSessionsStartRow && position < otherSessionsEndRow || position >= passwordSessionsStartRow && position < passwordSessionsEndRow) {
                if (getParentActivity() == null) {
                    return;
                }
                AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
                final boolean[] param = new boolean[1];
                if (currentType == 0) {
                    builder.setMessage(LocaleController.getString("TerminateSessionQuestion", R.string.TerminateSessionQuestion));
                } else {
                    final TLRPC.TL_webAuthorization authorization = (TLRPC.TL_webAuthorization) sessions.get(position - otherSessionsStartRow);
                    builder.setMessage(LocaleController.formatString("TerminateWebSessionQuestion", R.string.TerminateWebSessionQuestion, authorization.domain));
                    FrameLayout frameLayout1 = new FrameLayout(getParentActivity());

                    TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(authorization.bot_id);
                    String name;
                    if (user != null) {
                        name = UserObject.getFirstName(user);
                    } else {
                        name = "";
                    }

                    CheckBoxCell cell = new CheckBoxCell(getParentActivity(), 1);
                    cell.setBackgroundDrawable(Theme.getSelectorDrawable(false));
                    cell.setText(LocaleController.formatString("TerminateWebSessionStop", R.string.TerminateWebSessionStop, name), "", false, false);
                    cell.setPadding(LocaleController.isRTL ? AndroidUtilities.dp(16) : AndroidUtilities.dp(8), 0, LocaleController.isRTL ? AndroidUtilities.dp(8) : AndroidUtilities.dp(16), 0);
                    frameLayout1.addView(cell, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.TOP | Gravity.LEFT, 0, 0, 0, 0));
                    cell.setOnClickListener(v -> {
                        if (!v.isEnabled()) {
                            return;
                        }
                        CheckBoxCell cell1 = (CheckBoxCell) v;
                        param[0] = !param[0];
                        cell1.setChecked(param[0], true);
                    });
                    builder.setCustomViewOffset(16);
                    builder.setView(frameLayout1);
                }
                builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), (dialogInterface, option) -> {
                    if (getParentActivity() == null) {
                        return;
                    }
                    final AlertDialog progressDialog = new AlertDialog(getParentActivity(), 3);
                    progressDialog.setCanCacnel(false);
                    progressDialog.show();

                    if (currentType == 0) {
                        final TLRPC.TL_authorization authorization;
                        if (position >= otherSessionsStartRow && position < otherSessionsEndRow) {
                            authorization = (TLRPC.TL_authorization) sessions.get(position - otherSessionsStartRow);
                        } else {
                            authorization = (TLRPC.TL_authorization) passwordSessions.get(position - passwordSessionsStartRow);
                        }
                        TLRPC.TL_account_resetAuthorization req = new TLRPC.TL_account_resetAuthorization();
                        req.hash = authorization.hash;
                        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                            try {
                                progressDialog.dismiss();
                            } catch (Exception e) {
                                FileLog.e(e);
                            }
                            if (error == null) {
                                sessions.remove(authorization);
                                passwordSessions.remove(authorization);
                                updateRows();
                                if (listAdapter != null) {
                                    listAdapter.notifyDataSetChanged();
                                }
                            }
                        }));
                    } else {
                        final TLRPC.TL_webAuthorization authorization = (TLRPC.TL_webAuthorization) sessions.get(position - otherSessionsStartRow);
                        TLRPC.TL_account_resetWebAuthorization req = new TLRPC.TL_account_resetWebAuthorization();
                        req.hash = authorization.hash;
                        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                            try {
                                progressDialog.dismiss();
                            } catch (Exception e) {
                                FileLog.e(e);
                            }
                            if (error == null) {
                                sessions.remove(authorization);
                                updateRows();
                                if (listAdapter != null) {
                                    listAdapter.notifyDataSetChanged();
                                }
                            }
                        }));
                        if (param[0]) {
                            MessagesController.getInstance(currentAccount).blockUser(authorization.bot_id);
                        }
                    }
                });
                builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                showDialog(builder.create());
            }
        });

        return fragmentView;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (listAdapter != null) {
            listAdapter.notifyDataSetChanged();
        }
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.newSessionReceived) {
            loadSessions(true);
        }
    }

    private void loadSessions(boolean silent) {
        if (loading) {
            return;
        }
        if (!silent) {
            loading = true;
        }
        if (currentType == 0) {
            TLRPC.TL_account_getAuthorizations req = new TLRPC.TL_account_getAuthorizations();
            int reqId = ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                loading = false;
                if (error == null) {
                    sessions.clear();
                    passwordSessions.clear();
                    TLRPC.TL_account_authorizations res = (TLRPC.TL_account_authorizations) response;
                    for (int a = 0, N = res.authorizations.size(); a < N; a++) {
                        TLRPC.TL_authorization authorization = res.authorizations.get(a);
                        if ((authorization.flags & 1) != 0) {
                            currentSession = authorization;
                        } else if (authorization.password_pending) {
                            passwordSessions.add(authorization);
                        } else {
                            sessions.add(authorization);
                        }
                    }
                    updateRows();
                }
                if (listAdapter != null) {
                    listAdapter.notifyDataSetChanged();
                }
            }));
            ConnectionsManager.getInstance(currentAccount).bindRequestToGuid(reqId, classGuid);
        } else {
            TLRPC.TL_account_getWebAuthorizations req = new TLRPC.TL_account_getWebAuthorizations();
            int reqId = ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                loading = false;
                if (error == null) {
                    sessions.clear();
                    TLRPC.TL_account_webAuthorizations res = (TLRPC.TL_account_webAuthorizations) response;
                    MessagesController.getInstance(currentAccount).putUsers(res.users, false);
                    sessions.addAll(res.authorizations);
                    updateRows();
                }
                if (listAdapter != null) {
                    listAdapter.notifyDataSetChanged();
                }
            }));
            ConnectionsManager.getInstance(currentAccount).bindRequestToGuid(reqId, classGuid);
        }
    }

    private void updateRows() {
        rowCount = 0;
        if (currentSession != null) {
            currentSessionSectionRow = rowCount++;
            currentSessionRow = rowCount++;
        } else {
            currentSessionRow = -1;
            currentSessionSectionRow = -1;
        }
        if (!passwordSessions.isEmpty() || !sessions.isEmpty()) {
            terminateAllSessionsRow = rowCount++;
            terminateAllSessionsDetailRow = rowCount++;
            noOtherSessionsRow = -1;
        } else {
            terminateAllSessionsRow = -1;
            terminateAllSessionsDetailRow = -1;
            if (currentType == 1 || currentSession != null) {
                noOtherSessionsRow = rowCount++;
            } else {
                noOtherSessionsRow = -1;
            }
        }
        if (passwordSessions.isEmpty()) {
            passwordSessionsDetailRow = -1;
            passwordSessionsEndRow = -1;
            passwordSessionsStartRow = -1;
            passwordSessionsSectionRow = -1;
        } else {
            passwordSessionsSectionRow = rowCount++;
            passwordSessionsStartRow = rowCount;
            rowCount += passwordSessions.size();
            passwordSessionsEndRow = rowCount;
            passwordSessionsDetailRow = rowCount++;
        }
        if (sessions.isEmpty()) {
            otherSessionsSectionRow = -1;
            otherSessionsStartRow = -1;
            otherSessionsEndRow = -1;
            otherSessionsTerminateDetail = -1;
        } else {
            otherSessionsSectionRow = rowCount++;
            otherSessionsStartRow = otherSessionsSectionRow + 1;
            otherSessionsEndRow = otherSessionsStartRow + sessions.size();
            rowCount += sessions.size();
            otherSessionsTerminateDetail = rowCount++;
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
            return position == terminateAllSessionsRow || position >= otherSessionsStartRow && position < otherSessionsEndRow || position >= passwordSessionsStartRow && position < passwordSessionsEndRow;
        }

        @Override
        public int getItemCount() {
            return loading ? 0 : rowCount;
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
                    view = emptyLayout;
                    break;
                default:
                    view = new SessionCell(mContext, currentType);
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
                    if (position == terminateAllSessionsRow) {
                        textCell.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteRedText2));
                        if (currentType == 0) {
                            textCell.setText(LocaleController.getString("TerminateAllSessions", R.string.TerminateAllSessions), false);
                        } else {
                            textCell.setText(LocaleController.getString("TerminateAllWebSessions", R.string.TerminateAllWebSessions), false);
                        }
                    }
                    break;
                case 1:
                    TextInfoPrivacyCell privacyCell = (TextInfoPrivacyCell) holder.itemView;
                    if (position == terminateAllSessionsDetailRow) {
                        if (currentType == 0) {
                            privacyCell.setText(LocaleController.getString("ClearOtherSessionsHelp", R.string.ClearOtherSessionsHelp));
                        } else {
                            privacyCell.setText(LocaleController.getString("ClearOtherWebSessionsHelp", R.string.ClearOtherWebSessionsHelp));
                        }
                        privacyCell.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
                    } else if (position == otherSessionsTerminateDetail) {
                        if (currentType == 0) {
                            privacyCell.setText(LocaleController.getString("TerminateSessionInfo", R.string.TerminateSessionInfo));
                        } else {
                            privacyCell.setText(LocaleController.getString("TerminateWebSessionInfo", R.string.TerminateWebSessionInfo));
                        }
                        privacyCell.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                    } else if (position == passwordSessionsDetailRow) {
                        privacyCell.setText(LocaleController.getString("LoginAttemptsInfo", R.string.LoginAttemptsInfo));
                        if (otherSessionsTerminateDetail == -1) {
                            privacyCell.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                        } else {
                            privacyCell.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
                        }
                    }
                    break;
                case 2:
                    HeaderCell headerCell = (HeaderCell) holder.itemView;
                    if (position == currentSessionSectionRow) {
                        headerCell.setText(LocaleController.getString("CurrentSession", R.string.CurrentSession));
                    } else if (position == otherSessionsSectionRow) {
                        if (currentType == 0) {
                            headerCell.setText(LocaleController.getString("OtherSessions", R.string.OtherSessions));
                        } else {
                            headerCell.setText(LocaleController.getString("OtherWebSessions", R.string.OtherWebSessions));
                        }
                    } else if (position == passwordSessionsSectionRow) {
                        headerCell.setText(LocaleController.getString("LoginAttempts", R.string.LoginAttempts));
                    }
                    break;
                case 3:
                    ViewGroup.LayoutParams layoutParams = emptyLayout.getLayoutParams();
                    if (layoutParams != null) {
                        layoutParams.height = Math.max(AndroidUtilities.dp(220), AndroidUtilities.displaySize.y - ActionBar.getCurrentActionBarHeight() - AndroidUtilities.dp(128) - (Build.VERSION.SDK_INT >= 21 ? AndroidUtilities.statusBarHeight : 0));
                        emptyLayout.setLayoutParams(layoutParams);
                    }
                    break;
                default:
                    SessionCell sessionCell = (SessionCell) holder.itemView;
                    if (position == currentSessionRow) {
                        sessionCell.setSession(currentSession, !sessions.isEmpty() || !passwordSessions.isEmpty());
                    } else if (position >= otherSessionsStartRow && position < otherSessionsEndRow) {
                        sessionCell.setSession(sessions.get(position - otherSessionsStartRow), position != otherSessionsEndRow - 1);
                    } else if (position >= passwordSessionsStartRow && position < passwordSessionsEndRow) {
                        sessionCell.setSession(passwordSessions.get(position - passwordSessionsStartRow), position != passwordSessionsEndRow - 1);
                    }
                    break;
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (position == terminateAllSessionsRow) {
                return 0;
            } else if (position == terminateAllSessionsDetailRow || position == otherSessionsTerminateDetail || position == passwordSessionsDetailRow) {
                return 1;
            } else if (position == currentSessionSectionRow || position == otherSessionsSectionRow || position == passwordSessionsSectionRow) {
                return 2;
            } else if (position == noOtherSessionsRow) {
                return 3;
            } else if (position == currentSessionRow || position >= otherSessionsStartRow && position < otherSessionsEndRow || position >= passwordSessionsStartRow && position < passwordSessionsEndRow) {
                return 4;
            }
            return 0;
        }
    }

    @Override
    public ThemeDescription[] getThemeDescriptions() {
        return new ThemeDescription[]{
                new ThemeDescription(listView, ThemeDescription.FLAG_CELLBACKGROUNDCOLOR, new Class[]{TextSettingsCell.class, HeaderCell.class, SessionCell.class}, null, null, null, Theme.key_windowBackgroundWhite),
                new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundGray),

                new ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_actionBarDefault),
                new ThemeDescription(listView, ThemeDescription.FLAG_LISTGLOWCOLOR, null, null, null, null, Theme.key_actionBarDefault),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_ITEMSCOLOR, null, null, null, null, Theme.key_actionBarDefaultIcon),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_TITLECOLOR, null, null, null, null, Theme.key_actionBarDefaultTitle),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_actionBarDefaultSelector),

                new ThemeDescription(listView, ThemeDescription.FLAG_SELECTOR, null, null, null, null, Theme.key_listSelector),

                new ThemeDescription(listView, 0, new Class[]{View.class}, Theme.dividerPaint, null, null, Theme.key_divider),

                new ThemeDescription(imageView, ThemeDescription.FLAG_IMAGECOLOR, null, null, null, null, Theme.key_sessions_devicesImage),
                new ThemeDescription(textView1, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteGrayText2),
                new ThemeDescription(textView2, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteGrayText2),
                new ThemeDescription(emptyView, ThemeDescription.FLAG_PROGRESSBAR, null, null, null, null, Theme.key_progressCircle),

                new ThemeDescription(listView, 0, new Class[]{TextSettingsCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteRedText2),

                new ThemeDescription(listView, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{TextInfoPrivacyCell.class}, null, null, null, Theme.key_windowBackgroundGrayShadow),
                new ThemeDescription(listView, 0, new Class[]{TextInfoPrivacyCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText4),

                new ThemeDescription(listView, 0, new Class[]{HeaderCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlueHeader),

                new ThemeDescription(listView, 0, new Class[]{SessionCell.class}, new String[]{"nameTextView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText),
                new ThemeDescription(listView, ThemeDescription.FLAG_CHECKTAG, new Class[]{SessionCell.class}, new String[]{"onlineTextView"}, null, null, null, Theme.key_windowBackgroundWhiteValueText),
                new ThemeDescription(listView, ThemeDescription.FLAG_CHECKTAG, new Class[]{SessionCell.class}, new String[]{"onlineTextView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText3),
                new ThemeDescription(listView, 0, new Class[]{SessionCell.class}, new String[]{"detailTextView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText),
                new ThemeDescription(listView, 0, new Class[]{SessionCell.class}, new String[]{"detailExTextView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText3),
        };
    }
}
