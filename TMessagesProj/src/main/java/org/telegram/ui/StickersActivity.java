/*
 * This is the source code of Telegram for Android v. 3.x.x.
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
import android.graphics.Canvas;
import android.os.Build;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.Toast;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.query.StickersQuery;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.messenger.support.widget.LinearLayoutManager;
import org.telegram.messenger.support.widget.RecyclerView;
import org.telegram.messenger.support.widget.helper.ItemTouchHelper;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.RequestDelegate;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Cells.StickerSetCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.StickersAlert;
import org.telegram.ui.Components.URLSpanNoUnderline;

import java.util.ArrayList;
import java.util.Locale;

public class StickersActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    private RecyclerListView listView;
    private ListAdapter listAdapter;

    private boolean needReorder;

    private int stickersStartRow;
    private int stickersEndRow;
    private int stickersInfoRow;
    private int rowCount;

    public class TouchHelperCallback extends ItemTouchHelper.Callback {

        public static final float ALPHA_FULL = 1.0f;

        @Override
        public boolean isLongPressDragEnabled() {
            return true;
        }

        @Override
        public int getMovementFlags(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder) {
            if (viewHolder.getItemViewType() != 0) {
                return makeMovementFlags(0, 0);
            }
            return makeMovementFlags(ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0);
        }

        @Override
        public boolean onMove(RecyclerView recyclerView, RecyclerView.ViewHolder source, RecyclerView.ViewHolder target) {
            if (source.getItemViewType() != target.getItemViewType()) {
                return false;
            }
            listAdapter.swapElements(source.getAdapterPosition(), target.getAdapterPosition());
            return true;
        }

        @Override
        public void onChildDraw(Canvas c, RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, float dX, float dY, int actionState, boolean isCurrentlyActive) {
            super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
        }

        @Override
        public void onSelectedChanged(RecyclerView.ViewHolder viewHolder, int actionState) {
            if (actionState != ItemTouchHelper.ACTION_STATE_IDLE) {
                listView.cancelClickRunnables(false);
                viewHolder.itemView.setPressed(true);
            }
            super.onSelectedChanged(viewHolder, actionState);
        }

        @Override
        public void onSwiped(RecyclerView.ViewHolder viewHolder, int direction) {

        }

        @Override
        public void clearView(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder) {
            super.clearView(recyclerView, viewHolder);
            viewHolder.itemView.setPressed(false);
        }
    }

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();
        StickersQuery.checkStickers();
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.stickersDidLoaded);
        updateRows();
        return true;
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        sendReorder();
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(LocaleController.getString("Stickers", R.string.Stickers));
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

        listView = new RecyclerListView(context);
        listView.setFocusable(true);
        LinearLayoutManager layoutManager = new LinearLayoutManager(context);
        layoutManager.setOrientation(LinearLayoutManager.VERTICAL);
        listView.setLayoutManager(layoutManager);
        ItemTouchHelper itemTouchHelper = new ItemTouchHelper(new TouchHelperCallback());
        itemTouchHelper.attachToRecyclerView(listView);

        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        listView.setAdapter(listAdapter);
        listView.setOnItemClickListener(new RecyclerListView.OnItemClickListener() {
            @Override
            public void onItemClick(View view, int position) {
                if (position >= stickersStartRow && position < stickersEndRow && getParentActivity() != null) {
                    sendReorder();
                    final TLRPC.TL_messages_stickerSet stickerSet = StickersQuery.getStickerSets().get(position);
                    ArrayList<TLRPC.Document> stickers = stickerSet.documents;
                    if (stickers == null || stickers.isEmpty()) {
                        return;
                    }
                    showDialog(new StickersAlert(getParentActivity(), null, stickerSet, null));
                }
            }
        });

        return fragmentView;
    }

    @Override
    public void didReceivedNotification(int id, Object... args) {
        if (id == NotificationCenter.stickersDidLoaded) {
            updateRows();
        }
    }

    private void sendReorder() {
        if (!needReorder) {
            return;
        }
        StickersQuery.calcNewHash();
        needReorder = false;
        TLRPC.TL_messages_reorderStickerSets req = new TLRPC.TL_messages_reorderStickerSets();
        ArrayList<TLRPC.TL_messages_stickerSet> arrayList = StickersQuery.getStickerSets();
        for (int a = 0; a < arrayList.size(); a++) {
            req.order.add(arrayList.get(a).set.id);
        }
        ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
            @Override
            public void run(TLObject response, TLRPC.TL_error error) {

            }
        });
        NotificationCenter.getInstance().postNotificationName(NotificationCenter.stickersDidLoaded);
    }

    private void updateRows() {
        rowCount = 0;
        ArrayList<TLRPC.TL_messages_stickerSet> stickerSets = StickersQuery.getStickerSets();
        if (!stickerSets.isEmpty()) {
            stickersStartRow = 0;
            stickersEndRow = stickerSets.size();
            rowCount += stickerSets.size();
        } else {
            stickersStartRow = -1;
            stickersEndRow = -1;
        }
        stickersInfoRow = rowCount++;
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

    private class ListAdapter extends RecyclerListView.Adapter {
        private Context mContext;

        private class Holder extends RecyclerView.ViewHolder {

            public Holder(View itemView) {
                super(itemView);
            }
        }

        public ListAdapter(Context context) {
            mContext = context;
        }

        @Override
        public int getItemCount() {
            return rowCount;
        }

        @Override
        public long getItemId(int i) {
            if (i >= stickersStartRow && i < stickersEndRow) {
                ArrayList<TLRPC.TL_messages_stickerSet> arrayList = StickersQuery.getStickerSets();
                return arrayList.get(i).set.id;
            } else if (i == stickersInfoRow) {
                return Integer.MIN_VALUE;
            }
            return i;
        }

        private void processSelectionOption(int which, TLRPC.TL_messages_stickerSet stickerSet) {
            if (which == 0) {
                StickersQuery.removeStickersSet(getParentActivity(), stickerSet.set, !stickerSet.set.disabled ? 1 : 2);
            } else if (which == 1) {
                StickersQuery.removeStickersSet(getParentActivity(), stickerSet.set, 0);
            } else if (which == 2) {
                try {
                    Intent intent = new Intent(Intent.ACTION_SEND);
                    intent.setType("text/plain");
                    intent.putExtra(Intent.EXTRA_TEXT, String.format(Locale.US, "https://telegram.me/addstickers/%s", stickerSet.set.short_name));
                    getParentActivity().startActivityForResult(Intent.createChooser(intent, LocaleController.getString("StickersShare", R.string.StickersShare)), 500);
                } catch (Exception e) {
                    FileLog.e("tmessages", e);
                }
            } else if (which == 3) {
                try {
                    if (Build.VERSION.SDK_INT < 11) {
                        android.text.ClipboardManager clipboard = (android.text.ClipboardManager) ApplicationLoader.applicationContext.getSystemService(Context.CLIPBOARD_SERVICE);
                        clipboard.setText(String.format(Locale.US, "https://telegram.me/addstickers/%s", stickerSet.set.short_name));
                    } else {
                        android.content.ClipboardManager clipboard = (android.content.ClipboardManager) ApplicationLoader.applicationContext.getSystemService(Context.CLIPBOARD_SERVICE);
                        android.content.ClipData clip = android.content.ClipData.newPlainText("label", String.format(Locale.US, "https://telegram.me/addstickers/%s", stickerSet.set.short_name));
                        clipboard.setPrimaryClip(clip);
                    }
                    Toast.makeText(getParentActivity(), LocaleController.getString("LinkCopied", R.string.LinkCopied), Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    FileLog.e("tmessages", e);
                }
            }
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            if (holder.getItemViewType() == 0) {
                ArrayList<TLRPC.TL_messages_stickerSet> arrayList = StickersQuery.getStickerSets();
                ((StickerSetCell) holder.itemView).setStickersSet(arrayList.get(position), position != arrayList.size() - 1);
            }
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = null;
            switch (viewType) {
                case 0:
                    view = new StickerSetCell(mContext);
                    view.setBackgroundColor(0xffffffff);
                    view.setBackgroundResource(R.drawable.list_selector_white);
                    ((StickerSetCell) view).setOnOptionsClick(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            sendReorder();
                            StickerSetCell cell = (StickerSetCell) v.getParent();
                            final TLRPC.TL_messages_stickerSet stickerSet = cell.getStickersSet();
                            AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                            builder.setTitle(stickerSet.set.title);
                            CharSequence[] items;
                            final int[] options;
                            if (stickerSet.set.official) {
                                options = new int[]{0};
                                items = new CharSequence[]{
                                        !stickerSet.set.disabled ? LocaleController.getString("StickersHide", R.string.StickersHide) : LocaleController.getString("StickersShow", R.string.StickersShow)
                                };
                            } else {
                                options = new int[]{0, 1, 2, 3};
                                items = new CharSequence[]{
                                        !stickerSet.set.disabled ? LocaleController.getString("StickersHide", R.string.StickersHide) : LocaleController.getString("StickersShow", R.string.StickersShow),
                                        LocaleController.getString("StickersRemove", R.string.StickersRemove),
                                        LocaleController.getString("StickersShare", R.string.StickersShare),
                                        LocaleController.getString("StickersCopy", R.string.StickersCopy),
                                };
                            }
                            builder.setItems(items, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    processSelectionOption(options[which], stickerSet);
                                }
                            });
                            showDialog(builder.create());
                        }
                    });
                    break;
                case 1:
                    view = new TextInfoPrivacyCell(mContext);
                    String text = LocaleController.getString("StickersInfo", R.string.StickersInfo);
                    String botName = "@stickers";
                    int index = text.indexOf(botName);
                    if (index != -1) {
                        try {
                            SpannableStringBuilder stringBuilder = new SpannableStringBuilder(text);
                            URLSpanNoUnderline spanNoUnderline = new URLSpanNoUnderline("@stickers") {
                                @Override
                                public void onClick(View widget) {
                                    MessagesController.openByUserName("stickers", StickersActivity.this, 1);
                                }
                            };
                            stringBuilder.setSpan(spanNoUnderline, index, index + botName.length(), Spanned.SPAN_INCLUSIVE_INCLUSIVE);
                            ((TextInfoPrivacyCell) view).setText(stringBuilder);
                        } catch (Exception e) {
                            FileLog.e("tmessages", e);
                            ((TextInfoPrivacyCell) view).setText(text);
                        }
                    } else {
                        ((TextInfoPrivacyCell) view).setText(text);
                    }
                    view.setBackgroundResource(R.drawable.greydivider_bottom);
                    break;
            }
            view.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
            return new Holder(view);
        }

        @Override
        public int getItemViewType(int i) {
            if (i >= stickersStartRow && i < stickersEndRow) {
                return 0;
            } else if (i == stickersInfoRow) {
                return 1;
            }
            return 0;
        }

        public void swapElements(int fromIndex, int toIndex) {
            if (fromIndex != toIndex) {
                needReorder = true;
            }
            ArrayList<TLRPC.TL_messages_stickerSet> arrayList = StickersQuery.getStickerSets();
            TLRPC.TL_messages_stickerSet from = arrayList.get(fromIndex);
            arrayList.set(fromIndex, arrayList.get(toIndex));
            arrayList.set(toIndex, from);
            notifyItemMoved(fromIndex, toIndex);
        }
    }
}
