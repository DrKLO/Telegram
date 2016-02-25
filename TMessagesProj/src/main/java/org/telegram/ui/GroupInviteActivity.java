/*
 * This is the source code of Telegram for Android v. 3.x.x
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2016.
 */

package org.telegram.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.FrameLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
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
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Adapters.BaseFragmentAdapter;
import org.telegram.ui.Cells.TextBlockCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.LayoutHelper;

public class GroupInviteActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    private ListAdapter listAdapter;

    private int chat_id;
    private boolean loading;
    private TLRPC.ExportedChatInvite invite;

    private int linkRow;
    private int linkInfoRow;
    private int copyLinkRow;
    private int revokeLinkRow;
    private int shareLinkRow;
    private int shadowRow;
    private int rowCount;

    public GroupInviteActivity(int cid) {
        super();
        chat_id = cid;
    }

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();

        NotificationCenter.getInstance().addObserver(this, NotificationCenter.chatInfoDidLoaded);
        MessagesController.getInstance().loadFullChat(chat_id, classGuid, true);
        loading = true;

        rowCount = 0;
        linkRow = rowCount++;
        linkInfoRow = rowCount++;
        copyLinkRow = rowCount++;
        revokeLinkRow = rowCount++;
        shareLinkRow = rowCount++;
        shadowRow = rowCount++;

        return true;
    }

    @Override
    public void onFragmentDestroy() {
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.chatInfoDidLoaded);
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(LocaleController.getString("InviteLink", R.string.InviteLink));
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

        FrameLayout progressView = new FrameLayout(context);
        frameLayout.addView(progressView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        ProgressBar progressBar = new ProgressBar(context);
        progressView.addView(progressBar, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));

        ListView listView = new ListView(context);
        listView.setDivider(null);
        listView.setDividerHeight(0);
        listView.setEmptyView(progressView);
        listView.setVerticalScrollBarEnabled(false);
        listView.setDrawSelectorOnTop(true);
        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT));
        listView.setAdapter(listAdapter);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, final int i, long l) {
                if (getParentActivity() == null) {
                    return;
                }
                if (i == copyLinkRow || i == linkRow) {
                    if (invite == null) {
                        return;
                    }
                    try {
                        if (Build.VERSION.SDK_INT < 11) {
                            android.text.ClipboardManager clipboard = (android.text.ClipboardManager) ApplicationLoader.applicationContext.getSystemService(Context.CLIPBOARD_SERVICE);
                            clipboard.setText(invite.link);
                        } else {
                            android.content.ClipboardManager clipboard = (android.content.ClipboardManager) ApplicationLoader.applicationContext.getSystemService(Context.CLIPBOARD_SERVICE);
                            android.content.ClipData clip = android.content.ClipData.newPlainText("label", invite.link);
                            clipboard.setPrimaryClip(clip);
                        }
                        Toast.makeText(getParentActivity(), LocaleController.getString("LinkCopied", R.string.LinkCopied), Toast.LENGTH_SHORT).show();
                    } catch (Exception e) {
                        FileLog.e("tmessages", e);
                    }
                } else if (i == shareLinkRow) {
                    if (invite == null) {
                        return;
                    }
                    try {
                        Intent intent = new Intent(Intent.ACTION_SEND);
                        intent.setType("text/plain");
                        intent.putExtra(Intent.EXTRA_TEXT, invite.link);
                        getParentActivity().startActivityForResult(Intent.createChooser(intent, LocaleController.getString("InviteToGroupByLink", R.string.InviteToGroupByLink)), 500);
                    } catch (Exception e) {
                        FileLog.e("tmessages", e);
                    }
                } else if (i == revokeLinkRow) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                    builder.setMessage(LocaleController.getString("RevokeAlert", R.string.RevokeAlert));
                    builder.setTitle(LocaleController.getString("RevokeLink", R.string.RevokeLink));
                    builder.setPositiveButton(LocaleController.getString("RevokeButton", R.string.RevokeButton), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            generateLink(true);
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
    public void didReceivedNotification(int id, Object... args) {
        if (id == NotificationCenter.chatInfoDidLoaded) {
            TLRPC.ChatFull info = (TLRPC.ChatFull) args[0];
            int guid = (int) args[1];
            if (info.id == chat_id && guid == classGuid) {
                invite = MessagesController.getInstance().getExportedInvite(chat_id);
                if (!(invite instanceof TLRPC.TL_chatInviteExported)) {
                    generateLink(false);
                } else {
                    loading = false;
                    if (listAdapter != null) {
                        listAdapter.notifyDataSetChanged();
                    }
                }
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (listAdapter != null) {
            listAdapter.notifyDataSetChanged();
        }
    }

    private void generateLink(final boolean newRequest) {
        loading = true;
        TLObject request;
        if (ChatObject.isChannel(chat_id)) {
            TLRPC.TL_channels_exportInvite req = new TLRPC.TL_channels_exportInvite();
            req.channel = MessagesController.getInputChannel(chat_id);
            request = req;
        } else {
            TLRPC.TL_messages_exportChatInvite req = new TLRPC.TL_messages_exportChatInvite();
            req.chat_id = chat_id;
            request = req;
        }
        final int reqId = ConnectionsManager.getInstance().sendRequest(request, new RequestDelegate() {
            @Override
            public void run(final TLObject response, final TLRPC.TL_error error) {
                AndroidUtilities.runOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        if (error == null) {
                            invite = (TLRPC.ExportedChatInvite) response;
                            if (newRequest) {
                                if (getParentActivity() == null) {
                                    return;
                                }
                                AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                                builder.setMessage(LocaleController.getString("RevokeAlertNewLink", R.string.RevokeAlertNewLink));
                                builder.setTitle(LocaleController.getString("RevokeLink", R.string.RevokeLink));
                                builder.setNegativeButton(LocaleController.getString("OK", R.string.OK), null);
                                showDialog(builder.create());
                            }
                        }
                        loading = false;
                        listAdapter.notifyDataSetChanged();
                    }
                });
            }
        });
        ConnectionsManager.getInstance().bindRequestToGuid(reqId, classGuid);
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
            return i == revokeLinkRow || i == copyLinkRow || i == shareLinkRow || i == linkRow;
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
                if (i == copyLinkRow) {
                    textCell.setText(LocaleController.getString("CopyLink", R.string.CopyLink), true);
                } else if (i == shareLinkRow) {
                    textCell.setText(LocaleController.getString("ShareLink", R.string.ShareLink), false);
                } else if (i == revokeLinkRow) {
                    textCell.setText(LocaleController.getString("RevokeLink", R.string.RevokeLink), true);
                }
            } else if (type == 1) {
                if (view == null) {
                    view = new TextInfoPrivacyCell(mContext);
                }
                if (i == shadowRow) {
                    ((TextInfoPrivacyCell) view).setText("");
                    view.setBackgroundResource(R.drawable.greydivider_bottom);
                } else if (i == linkInfoRow) {
                    TLRPC.Chat chat = MessagesController.getInstance().getChat(chat_id);
                    if (ChatObject.isChannel(chat) && !chat.megagroup) {
                        ((TextInfoPrivacyCell) view).setText(LocaleController.getString("ChannelLinkInfo", R.string.ChannelLinkInfo));
                    } else {
                        ((TextInfoPrivacyCell) view).setText(LocaleController.getString("LinkInfo", R.string.LinkInfo));
                    }
                    view.setBackgroundResource(R.drawable.greydivider);
                }
            } else if (type == 2) {
                if (view == null) {
                    view = new TextBlockCell(mContext);
                    view.setBackgroundColor(0xffffffff);
                }
                ((TextBlockCell) view).setText(invite != null ? invite.link : "error", false);
            }
            return view;
        }

        @Override
        public int getItemViewType(int i) {
            if (i == copyLinkRow || i == shareLinkRow || i == revokeLinkRow) {
                return 0;
            } else if (i == shadowRow || i == linkInfoRow) {
                return 1;
            } else if (i == linkRow) {
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
            return loading;
        }
    }
}
