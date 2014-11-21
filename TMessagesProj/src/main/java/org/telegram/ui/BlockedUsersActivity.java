/*
 * This is the source code of Telegram for Android v. 1.3.2.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013.
 */

package org.telegram.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.FrameLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.telegram.PhoneFormat.PhoneFormat;
import org.telegram.android.LocaleController;
import org.telegram.messenger.TLRPC;
import org.telegram.android.MessagesController;
import org.telegram.android.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.ui.Adapters.BaseFragmentAdapter;
import org.telegram.ui.Cells.TextInfoCell;
import org.telegram.ui.Cells.UserCell;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.BaseFragment;

public class BlockedUsersActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate, ContactsActivity.ContactsActivityDelegate {

    private ListView listView;
    private ListAdapter listViewAdapter;
    private FrameLayout progressView;
    private TextView emptyTextView;
    private int selectedUserId;

    private final static int block_user = 1;

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.updateInterfaces);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.blockedUsersDidLoaded);
        return true;
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.updateInterfaces);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.blockedUsersDidLoaded);
        MessagesController.getInstance().getBlockedUsers(false);
    }

    @Override
    public View createView(LayoutInflater inflater, ViewGroup container) {
        if (fragmentView == null) {
            actionBar.setBackButtonImage(R.drawable.ic_ab_back);
            actionBar.setAllowOverlayTitle(true);
            actionBar.setTitle(LocaleController.getString("BlockedUsers", R.string.BlockedUsers));
            actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
                @Override
                public void onItemClick(int id) {
                    if (id == -1) {
                        finishFragment();
                    } else if (id == block_user) {
                        Bundle args = new Bundle();
                        args.putBoolean("onlyUsers", true);
                        args.putBoolean("destroyAfterSelect", true);
                        args.putBoolean("returnAsResult", true);
                        ContactsActivity fragment = new ContactsActivity(args);
                        fragment.setDelegate(BlockedUsersActivity.this);
                        presentFragment(fragment);
                    }
                }
            });

            ActionBarMenu menu = actionBar.createMenu();
            menu.addItem(block_user, R.drawable.plus);

            fragmentView = new FrameLayout(getParentActivity());
            FrameLayout frameLayout = (FrameLayout) fragmentView;

            emptyTextView = new TextView(getParentActivity());
            emptyTextView.setTextColor(0xff808080);
            emptyTextView.setTextSize(20);
            emptyTextView.setGravity(Gravity.CENTER);
            emptyTextView.setVisibility(View.INVISIBLE);
            emptyTextView.setText(LocaleController.getString("NoBlocked", R.string.NoBlocked));
            frameLayout.addView(emptyTextView);
            FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) emptyTextView.getLayoutParams();
            layoutParams.width = FrameLayout.LayoutParams.MATCH_PARENT;
            layoutParams.height = FrameLayout.LayoutParams.MATCH_PARENT;
            layoutParams.gravity = Gravity.TOP;
            emptyTextView.setLayoutParams(layoutParams);
            emptyTextView.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    return true;
                }
            });

            progressView = new FrameLayout(getParentActivity());
            frameLayout.addView(progressView);
            layoutParams = (FrameLayout.LayoutParams) progressView.getLayoutParams();
            layoutParams.width = FrameLayout.LayoutParams.MATCH_PARENT;
            layoutParams.height = FrameLayout.LayoutParams.MATCH_PARENT;
            progressView.setLayoutParams(layoutParams);

            ProgressBar progressBar = new ProgressBar(getParentActivity());
            progressView.addView(progressBar);
            layoutParams = (FrameLayout.LayoutParams) progressView.getLayoutParams();
            layoutParams.width = FrameLayout.LayoutParams.WRAP_CONTENT;
            layoutParams.height = FrameLayout.LayoutParams.WRAP_CONTENT;
            layoutParams.gravity = Gravity.CENTER;
            progressView.setLayoutParams(layoutParams);

            listView = new ListView(getParentActivity());
            listView.setEmptyView(emptyTextView);
            listView.setVerticalScrollBarEnabled(false);
            listView.setDivider(null);
            listView.setDividerHeight(0);
            listView.setAdapter(listViewAdapter = new ListAdapter(getParentActivity()));
            if (Build.VERSION.SDK_INT >= 11) {
                listView.setVerticalScrollbarPosition(LocaleController.isRTL ? ListView.SCROLLBAR_POSITION_LEFT : ListView.SCROLLBAR_POSITION_RIGHT);
            }
            frameLayout.addView(listView);
            layoutParams = (FrameLayout.LayoutParams) listView.getLayoutParams();
            layoutParams.width = FrameLayout.LayoutParams.MATCH_PARENT;
            layoutParams.height = FrameLayout.LayoutParams.MATCH_PARENT;
            listView.setLayoutParams(layoutParams);

            listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                    if (i < MessagesController.getInstance().blockedUsers.size()) {
                        Bundle args = new Bundle();
                        args.putInt("user_id", MessagesController.getInstance().blockedUsers.get(i));
                        presentFragment(new ProfileActivity(args));
                    }
                }
            });

            listView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
                @Override
                public boolean onItemLongClick(AdapterView<?> adapterView, View view, int i, long l) {
                    if (i < 0 || i >= MessagesController.getInstance().blockedUsers.size() || getParentActivity() == null) {
                        return true;
                    }
                    selectedUserId = MessagesController.getInstance().blockedUsers.get(i);

                    AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                    CharSequence[] items = new CharSequence[] {LocaleController.getString("Unblock", R.string.Unblock)};
                    builder.setItems(items, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            if (i == 0) {
                                MessagesController.getInstance().unblockUser(selectedUserId);
                            }
                        }
                    });
                    showAlertDialog(builder);

                    return true;
                }
            });

            if (MessagesController.getInstance().loadingBlockedUsers) {
                progressView.setVisibility(View.VISIBLE);
                emptyTextView.setVisibility(View.GONE);
                listView.setEmptyView(null);
            } else {
                progressView.setVisibility(View.GONE);
                listView.setEmptyView(emptyTextView);
            }
        } else {
            ViewGroup parent = (ViewGroup)fragmentView.getParent();
            if (parent != null) {
                parent.removeView(fragmentView);
            }
        }
        return fragmentView;
    }

    @Override
    public void didReceivedNotification(int id, Object... args) {
        if (id == NotificationCenter.updateInterfaces) {
            int mask = (Integer)args[0];
            if ((mask & MessagesController.UPDATE_MASK_AVATAR) != 0 || (mask & MessagesController.UPDATE_MASK_NAME) != 0) {
                updateVisibleRows(mask);
            }
        } else if (id == NotificationCenter.blockedUsersDidLoaded) {
            if (progressView != null) {
                progressView.setVisibility(View.GONE);
            }
            if (listView != null && listView.getEmptyView() == null) {
                listView.setEmptyView(emptyTextView);
            }
            if (listViewAdapter != null) {
                listViewAdapter.notifyDataSetChanged();
            }
        }
    }

    private void updateVisibleRows(int mask) {
        if (listView == null) {
            return;
        }
        int count = listView.getChildCount();
        for (int a = 0; a < count; a++) {
            View child = listView.getChildAt(a);
            if (child instanceof UserCell) {
                ((UserCell) child).update(mask);
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (listViewAdapter != null) {
            listViewAdapter.notifyDataSetChanged();
        }
    }

    @Override
    public void didSelectContact(final TLRPC.User user, String param) {
        if (user == null) {
            return;
        }
        MessagesController.getInstance().blockUser(user.id);
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
            return i != MessagesController.getInstance().blockedUsers.size();
        }

        @Override
        public int getCount() {
            if (MessagesController.getInstance().blockedUsers.isEmpty()) {
                return 0;
            }
            return MessagesController.getInstance().blockedUsers.size() + 1;
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
                    view = new UserCell(mContext, 1);
                }
                TLRPC.User user = MessagesController.getInstance().getUser(MessagesController.getInstance().blockedUsers.get(i));
                if (user != null) {
                    ((UserCell) view).setData(user, null, user.phone != null && user.phone.length() != 0 ? PhoneFormat.getInstance().format("+" + user.phone) : LocaleController.getString("NumberUnknown", R.string.NumberUnknown), 0);
                }
            } else if (type == 1) {
                if (view == null) {
                    view = new TextInfoCell(mContext);
                    ((TextInfoCell) view).setText(LocaleController.getString("UnblockText", R.string.UnblockText));
                }
            }
            return view;
        }

        @Override
        public int getItemViewType(int i) {
            if(i == MessagesController.getInstance().blockedUsers.size()) {
                return 1;
            }
            return 0;
        }

        @Override
        public int getViewTypeCount() {
            return 2;
        }

        @Override
        public boolean isEmpty() {
            return MessagesController.getInstance().blockedUsers.isEmpty();
        }
    }
}
