/*
 * This is the source code of Telegram for Android v. 3.x.x
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2017.
 */

package org.telegram.ui;

import android.content.Context;
import android.content.DialogInterface;
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
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.SessionCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.EmptyTextProgressView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;

public class SessionsActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    private ListAdapter listAdapter;
    private RecyclerListView listView;
    private ImageView imageView;
    private TextView textView1;
    private TextView textView2;
    private EmptyTextProgressView emptyView;

    private ArrayList<TLRPC.TL_authorization> sessions = new ArrayList<>();
    private TLRPC.TL_authorization currentSession = null;
    private boolean loading;
    private LinearLayout emptyLayout;

    private int currentSessionSectionRow;
    private int currentSessionRow;
    private int terminateAllSessionsRow;
    private int terminateAllSessionsDetailRow;
    private int otherSessionsSectionRow;
    private int otherSessionsStartRow;
    private int otherSessionsEndRow;
    private int otherSessionsTerminateDetail;
    private int noOtherSessionsRow;
    private int rowCount;

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();
        updateRows();
        loadSessions(false);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.newSessionReceived);
        return true;
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.newSessionReceived);
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(LocaleController.getString("SessionsTitle", R.string.SessionsTitle));
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
        imageView.setImageResource(R.drawable.devices);
        imageView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_sessions_devicesImage), PorterDuff.Mode.MULTIPLY));
        emptyLayout.addView(imageView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        textView1 = new TextView(context);
        textView1.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2));
        textView1.setGravity(Gravity.CENTER);
        textView1.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17);
        textView1.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        textView1.setText(LocaleController.getString("NoOtherSessions", R.string.NoOtherSessions));
        emptyLayout.addView(textView1, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 0, 16, 0, 0));

        textView2 = new TextView(context);
        textView2.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2));
        textView2.setGravity(Gravity.CENTER);
        textView2.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17);
        textView2.setPadding(AndroidUtilities.dp(20), 0, AndroidUtilities.dp(20), 0);
        textView2.setText(LocaleController.getString("NoOtherSessionsInfo", R.string.NoOtherSessionsInfo));
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
        listView.setOnItemClickListener(new RecyclerListView.OnItemClickListener() {
            @Override
            public void onItemClick(View view, final int position) {
                if (position == terminateAllSessionsRow) {
                    if (getParentActivity() == null) {
                        return;
                    }
                    AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                    builder.setMessage(LocaleController.getString("AreYouSureSessions", R.string.AreYouSureSessions));
                    builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
                    builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            TLRPC.TL_auth_resetAuthorizations req = new TLRPC.TL_auth_resetAuthorizations();
                            ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
                                @Override
                                public void run(final TLObject response, final TLRPC.TL_error error) {
                                    AndroidUtilities.runOnUIThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            if (getParentActivity() == null) {
                                                return;
                                            }
                                            if (error == null && response instanceof TLRPC.TL_boolTrue) {
                                                Toast toast = Toast.makeText(getParentActivity(), LocaleController.getString("TerminateAllSessions", R.string.TerminateAllSessions), Toast.LENGTH_SHORT);
                                                toast.show();
                                            } else {
                                                Toast toast = Toast.makeText(getParentActivity(), LocaleController.getString("UnknownError", R.string.UnknownError), Toast.LENGTH_SHORT);
                                                toast.show();
                                            }
                                            finishFragment();
                                        }
                                    });
                                    UserConfig.registeredForPush = false;
                                    UserConfig.saveConfig(false);
                                    MessagesController.getInstance().registerForPush(UserConfig.pushString);
                                    ConnectionsManager.getInstance().setUserId(UserConfig.getClientUserId());
                                }
                            });
                        }
                    });
                    builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                    showDialog(builder.create());
                } else if (position >= otherSessionsStartRow && position < otherSessionsEndRow) {
                    if (getParentActivity() == null) {
                        return;
                    }
                    AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                    builder.setMessage(LocaleController.getString("TerminateSessionQuestion", R.string.TerminateSessionQuestion));
                    builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
                    builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int option) {
                            if (getParentActivity() == null) {
                                return;
                            }
                            final AlertDialog progressDialog = new AlertDialog(getParentActivity(), 1);
                            progressDialog.setMessage(LocaleController.getString("Loading", R.string.Loading));
                            progressDialog.setCanceledOnTouchOutside(false);
                            progressDialog.setCancelable(false);
                            progressDialog.show();

                            final TLRPC.TL_authorization authorization = sessions.get(position - otherSessionsStartRow);
                            TLRPC.TL_account_resetAuthorization req = new TLRPC.TL_account_resetAuthorization();
                            req.hash = authorization.hash;
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
                                            if (error == null) {
                                                sessions.remove(authorization);
                                                updateRows();
                                                if (listAdapter != null) {
                                                    listAdapter.notifyDataSetChanged();
                                                }
                                            }
                                        }
                                    });
                                }
                            });
                        }
                    });
                    builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                    showDialog(builder.create());
                }
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
    public void didReceivedNotification(int id, Object... args) {
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
        TLRPC.TL_account_getAuthorizations req = new TLRPC.TL_account_getAuthorizations();
        int reqId = ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
            @Override
            public void run(final TLObject response, final TLRPC.TL_error error) {
                AndroidUtilities.runOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        loading = false;
                        if (error == null) {
                            sessions.clear();
                            TLRPC.TL_account_authorizations res = (TLRPC.TL_account_authorizations) response;
                            for (TLRPC.TL_authorization authorization : res.authorizations) {
                                if ((authorization.flags & 1) != 0) {
                                    currentSession = authorization;
                                } else {
                                    sessions.add(authorization);
                                }
                            }
                            updateRows();
                        }
                        if (listAdapter != null) {
                            listAdapter.notifyDataSetChanged();
                        }
                    }
                });
            }
        });
        ConnectionsManager.getInstance().bindRequestToGuid(reqId, classGuid);
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
        if (sessions.isEmpty()) {
            if (currentSession != null) {
                noOtherSessionsRow = rowCount++;
            } else {
                noOtherSessionsRow = -1;
            }
            terminateAllSessionsRow = -1;
            terminateAllSessionsDetailRow = -1;
            otherSessionsSectionRow = -1;
            otherSessionsStartRow = -1;
            otherSessionsEndRow = -1;
            otherSessionsTerminateDetail = -1;
        } else {
            noOtherSessionsRow = -1;
            terminateAllSessionsRow = rowCount++;
            terminateAllSessionsDetailRow = rowCount++;
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
            return position == terminateAllSessionsRow || position >= otherSessionsStartRow && position < otherSessionsEndRow;
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
                    view = new SessionCell(mContext);
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
                        textCell.setText(LocaleController.getString("TerminateAllSessions", R.string.TerminateAllSessions), false);
                    }
                    break;
                case 1:
                    TextInfoPrivacyCell privacyCell = (TextInfoPrivacyCell) holder.itemView;
                    if (position == terminateAllSessionsDetailRow) {
                        privacyCell.setText(LocaleController.getString("ClearOtherSessionsHelp", R.string.ClearOtherSessionsHelp));
                        privacyCell.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
                    } else if (position == otherSessionsTerminateDetail) {
                        privacyCell.setText(LocaleController.getString("TerminateSessionInfo", R.string.TerminateSessionInfo));
                        privacyCell.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                    }
                    break;
                case 2:
                    HeaderCell headerCell = (HeaderCell) holder.itemView;
                    if (position == currentSessionSectionRow) {
                        headerCell.setText(LocaleController.getString("CurrentSession", R.string.CurrentSession));
                    } else if (position == otherSessionsSectionRow) {
                        headerCell.setText(LocaleController.getString("OtherSessions", R.string.OtherSessions));
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
                        sessionCell.setSession(currentSession, !sessions.isEmpty());
                    } else {
                        sessionCell.setSession(sessions.get(position - otherSessionsStartRow), position != otherSessionsEndRow - 1);
                    }
                    break;
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (position == terminateAllSessionsRow) {
                return 0;
            } else if (position == terminateAllSessionsDetailRow || position == otherSessionsTerminateDetail) {
                return 1;
            } else if (position == currentSessionSectionRow || position == otherSessionsSectionRow) {
                return 2;
            } else if (position == noOtherSessionsRow) {
                return 3;
            } else if (position == currentSessionRow || position >= otherSessionsStartRow && position < otherSessionsEndRow) {
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
