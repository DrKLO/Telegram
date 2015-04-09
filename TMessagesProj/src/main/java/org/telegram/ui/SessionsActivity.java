/*
 * This is the source code of Telegram for Android v. 2.x
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2015.
 */

package org.telegram.ui;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Build;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import org.telegram.android.AndroidUtilities;
import org.telegram.android.LocaleController;
import org.telegram.android.MessagesController;
import org.telegram.android.NotificationCenter;
import org.telegram.messenger.ConnectionsManager;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.messenger.RPCRequest;
import org.telegram.messenger.TLObject;
import org.telegram.messenger.TLRPC;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Adapters.BaseFragmentAdapter;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.SessionCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;

import java.util.ArrayList;

public class SessionsActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    private ListAdapter listAdapter;
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
    public View createView(Context context, LayoutInflater inflater) {
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
        frameLayout.setBackgroundColor(0xfff0f0f0);

        emptyLayout = new LinearLayout(context);
        emptyLayout.setOrientation(LinearLayout.VERTICAL);
        emptyLayout.setGravity(Gravity.CENTER);
        emptyLayout.setBackgroundResource(R.drawable.greydivider_bottom);
        emptyLayout.setLayoutParams(new AbsListView.LayoutParams(AbsListView.LayoutParams.MATCH_PARENT, AndroidUtilities.displaySize.y - AndroidUtilities.getCurrentActionBarHeight()));

        ImageView imageView = new ImageView(context);
        imageView.setImageResource(R.drawable.devices);
        emptyLayout.addView(imageView);
        LinearLayout.LayoutParams layoutParams2 = (LinearLayout.LayoutParams) imageView.getLayoutParams();
        layoutParams2.width = LinearLayout.LayoutParams.WRAP_CONTENT;
        layoutParams2.height = LinearLayout.LayoutParams.WRAP_CONTENT;
        imageView.setLayoutParams(layoutParams2);

        TextView textView = new TextView(context);
        textView.setTextColor(0xff8a8a8a);
        textView.setGravity(Gravity.CENTER);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17);
        textView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        textView.setText(LocaleController.getString("NoOtherSessions", R.string.NoOtherSessions));
        emptyLayout.addView(textView);
        layoutParams2 = (LinearLayout.LayoutParams) textView.getLayoutParams();
        layoutParams2.topMargin = AndroidUtilities.dp(16);
        layoutParams2.width = FrameLayout.LayoutParams.WRAP_CONTENT;
        layoutParams2.height = FrameLayout.LayoutParams.WRAP_CONTENT;
        layoutParams2.gravity = Gravity.CENTER;
        textView.setLayoutParams(layoutParams2);

        textView = new TextView(context);
        textView.setTextColor(0xff8a8a8a);
        textView.setGravity(Gravity.CENTER);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17);
        textView.setPadding(AndroidUtilities.dp(20), 0, AndroidUtilities.dp(20), 0);
        textView.setText(LocaleController.getString("NoOtherSessionsInfo", R.string.NoOtherSessionsInfo));
        emptyLayout.addView(textView);
        layoutParams2 = (LinearLayout.LayoutParams) textView.getLayoutParams();
        layoutParams2.topMargin = AndroidUtilities.dp(14);
        layoutParams2.width = FrameLayout.LayoutParams.WRAP_CONTENT;
        layoutParams2.height = FrameLayout.LayoutParams.WRAP_CONTENT;
        layoutParams2.gravity = Gravity.CENTER;
        textView.setLayoutParams(layoutParams2);

        FrameLayout progressView = new FrameLayout(context);
        frameLayout.addView(progressView);
        FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) progressView.getLayoutParams();
        layoutParams.width = FrameLayout.LayoutParams.MATCH_PARENT;
        layoutParams.height = FrameLayout.LayoutParams.MATCH_PARENT;
        progressView.setLayoutParams(layoutParams);
        progressView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                return true;
            }
        });

        ProgressBar progressBar = new ProgressBar(context);
        progressView.addView(progressBar);
        layoutParams = (FrameLayout.LayoutParams) progressView.getLayoutParams();
        layoutParams.width = FrameLayout.LayoutParams.WRAP_CONTENT;
        layoutParams.height = FrameLayout.LayoutParams.WRAP_CONTENT;
        layoutParams.gravity = Gravity.CENTER;
        progressView.setLayoutParams(layoutParams);

        ListView listView = new ListView(context);
        listView.setDivider(null);
        listView.setDividerHeight(0);
        listView.setVerticalScrollBarEnabled(false);
        listView.setDrawSelectorOnTop(true);
        listView.setEmptyView(progressView);
        frameLayout.addView(listView);
        layoutParams = (FrameLayout.LayoutParams) listView.getLayoutParams();
        layoutParams.width = FrameLayout.LayoutParams.MATCH_PARENT;
        layoutParams.height = FrameLayout.LayoutParams.MATCH_PARENT;
        layoutParams.gravity = Gravity.TOP;
        listView.setLayoutParams(layoutParams);
        listView.setAdapter(listAdapter);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, final int i, long l) {
                if (i == terminateAllSessionsRow) {
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
                            ConnectionsManager.getInstance().performRpc(req, new RPCRequest.RPCRequestDelegate() {
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
                                    UserConfig.registeredForInternalPush = false;
                                    UserConfig.saveConfig(false);
                                    MessagesController.getInstance().registerForPush(UserConfig.pushString);
                                    ConnectionsManager.getInstance().initPushConnection();
                                }
                            });
                        }
                    });
                    builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                    showAlertDialog(builder);
                } else if (i >= otherSessionsStartRow && i < otherSessionsEndRow) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                    builder.setMessage(LocaleController.getString("TerminateSessionQuestion", R.string.TerminateSessionQuestion));
                    builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
                    builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int option) {
                            final ProgressDialog progressDialog = new ProgressDialog(getParentActivity());
                            progressDialog.setMessage(LocaleController.getString("Loading", R.string.Loading));
                            progressDialog.setCanceledOnTouchOutside(false);
                            progressDialog.setCancelable(false);
                            progressDialog.show();

                            final TLRPC.TL_authorization authorization = sessions.get(i - otherSessionsStartRow);
                            TLRPC.TL_account_resetAuthorization req = new TLRPC.TL_account_resetAuthorization();
                            req.hash = authorization.hash;
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
                    showAlertDialog(builder);
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
        long reqId = ConnectionsManager.getInstance().performRpc(req, new RPCRequest.RPCRequestDelegate() {
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
            noOtherSessionsRow = -1;
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
            return i == terminateAllSessionsRow || i >= otherSessionsStartRow && i < otherSessionsEndRow;
        }

        @Override
        public int getCount() {
            return loading ? 0 : rowCount;
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
                if (i == terminateAllSessionsRow) {
                    textCell.setTextColor(0xffdb5151);
                    textCell.setText(LocaleController.getString("TerminateAllSessions", R.string.TerminateAllSessions), false);
                }
            } else if (type == 1) {
                if (view == null) {
                    view = new TextInfoPrivacyCell(mContext);
                }
                if (i == terminateAllSessionsDetailRow) {
                    ((TextInfoPrivacyCell) view).setText(LocaleController.getString("ClearOtherSessionsHelp", R.string.ClearOtherSessionsHelp));
                    view.setBackgroundResource(R.drawable.greydivider);
                } else if (i == otherSessionsTerminateDetail) {
                    ((TextInfoPrivacyCell) view).setText(LocaleController.getString("TerminateSessionInfo", R.string.TerminateSessionInfo));
                    view.setBackgroundResource(R.drawable.greydivider_bottom);
                }
            } else if (type == 2) {
                if (view == null) {
                    view = new HeaderCell(mContext);
                    view.setBackgroundColor(0xffffffff);
                }
                if (i == currentSessionSectionRow) {
                    ((HeaderCell) view).setText(LocaleController.getString("CurrentSession", R.string.CurrentSession));
                } else if (i == otherSessionsSectionRow) {
                    ((HeaderCell) view).setText(LocaleController.getString("OtherSessions", R.string.OtherSessions));
                }
            } else if (type == 3) {
                ViewGroup.LayoutParams layoutParams = emptyLayout.getLayoutParams();
                if (layoutParams != null) {
                    layoutParams.height = Math.max(AndroidUtilities.dp(220), AndroidUtilities.displaySize.y - AndroidUtilities.getCurrentActionBarHeight() - AndroidUtilities.dp(128) - (Build.VERSION.SDK_INT >= 21 ? AndroidUtilities.statusBarHeight : 0));
                    emptyLayout.setLayoutParams(layoutParams);
                }
                return emptyLayout;
            } else if (type == 4) {
                if (view == null) {
                    view = new SessionCell(mContext);
                    view.setBackgroundColor(0xffffffff);
                }
                if (i == currentSessionRow) {
                    ((SessionCell) view).setSession(currentSession, !sessions.isEmpty());
                } else {
                    ((SessionCell) view).setSession(sessions.get(i - otherSessionsStartRow), i != otherSessionsEndRow - 1);
                }
            }
            return view;
        }

        @Override
        public int getItemViewType(int i) {
            if (i == terminateAllSessionsRow) {
                return 0;
            } else if (i == terminateAllSessionsDetailRow || i == otherSessionsTerminateDetail) {
                return 1;
            } else if (i == currentSessionSectionRow || i == otherSessionsSectionRow) {
                return 2;
            } else if (i == noOtherSessionsRow) {
                return 3;
            } else if (i == currentSessionRow || i >= otherSessionsStartRow && i < otherSessionsEndRow) {
                return 4;
            }
            return 0;
        }

        @Override
        public int getViewTypeCount() {
            return 5;
        }

        @Override
        public boolean isEmpty() {
            return loading;
        }
    }
}
