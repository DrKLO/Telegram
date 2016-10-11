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
import android.view.MotionEvent;
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
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.StickerSetCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
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
    private int currentType;

    private int featuredRow;
    private int featuredInfoRow;
    private int masksRow;
    private int masksInfoRow;
    private int archivedRow;
    private int archivedInfoRow;
    private int stickersStartRow;
    private int stickersEndRow;
    private int stickersShadowRow;
    private int rowCount;

    public class TouchHelperCallback extends ItemTouchHelper.Callback {

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

    public StickersActivity(int type) {
        super();
        currentType = type;
    }

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();
        StickersQuery.checkStickers(currentType);
        if (currentType == StickersQuery.TYPE_IMAGE) {
            StickersQuery.checkFeaturedStickers();
        }
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.stickersDidLoaded);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.featuredStickersDidLoaded);
        updateRows();
        return true;
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.stickersDidLoaded);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.featuredStickersDidLoaded);
        sendReorder();
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        if (currentType == StickersQuery.TYPE_IMAGE) {
            actionBar.setTitle(LocaleController.getString("Stickers", R.string.Stickers));
        } else {
            actionBar.setTitle(LocaleController.getString("Masks", R.string.Masks));
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
        frameLayout.setBackgroundColor(0xfff0f0f0);

        listView = new RecyclerListView(context);
        listView.setFocusable(true);
        listView.setTag(7);
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
                    final TLRPC.TL_messages_stickerSet stickerSet = StickersQuery.getStickerSets(currentType).get(position - stickersStartRow);
                    ArrayList<TLRPC.Document> stickers = stickerSet.documents;
                    if (stickers == null || stickers.isEmpty()) {
                        return;
                    }
                    showDialog(new StickersAlert(getParentActivity(), StickersActivity.this, null, stickerSet, null));
                } else if (position == featuredRow) {
                    presentFragment(new FeaturedStickersActivity());
                } else if (position == archivedRow) {
                    presentFragment(new ArchivedStickersActivity(currentType));
                } else if (position == masksRow) {
                    presentFragment(new StickersActivity(StickersQuery.TYPE_MASK));
                }
            }
        });

        return fragmentView;
    }

    @Override
    public void didReceivedNotification(int id, Object... args) {
        if (id == NotificationCenter.stickersDidLoaded) {
            if ((Integer) args[0] == currentType) {
                updateRows();
            }
        } else if (id == NotificationCenter.featuredStickersDidLoaded) {
            if (listAdapter != null) {
                listAdapter.notifyItemChanged(0);
            }
        }
    }

    private void sendReorder() {
        if (!needReorder) {
            return;
        }
        StickersQuery.calcNewHash(currentType);
        needReorder = false;
        TLRPC.TL_messages_reorderStickerSets req = new TLRPC.TL_messages_reorderStickerSets();
        ArrayList<TLRPC.TL_messages_stickerSet> arrayList = StickersQuery.getStickerSets(currentType);
        for (int a = 0; a < arrayList.size(); a++) {
            req.order.add(arrayList.get(a).set.id);
        }
        ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
            @Override
            public void run(TLObject response, TLRPC.TL_error error) {

            }
        });
        NotificationCenter.getInstance().postNotificationName(NotificationCenter.stickersDidLoaded, currentType);
    }

    private void updateRows() {
        rowCount = 0;
        if (currentType == StickersQuery.TYPE_IMAGE) {
            featuredRow = rowCount++;
            featuredInfoRow = rowCount++;
            masksRow = rowCount++;
            masksInfoRow = rowCount++;
            archivedRow = rowCount++;
            archivedInfoRow = rowCount++;
        } else {
            featuredRow = -1;
            featuredInfoRow = -1;
            masksRow = -1;
            masksInfoRow = -1;
            archivedRow = rowCount++;
            archivedInfoRow = rowCount++;
        }
        ArrayList<TLRPC.TL_messages_stickerSet> stickerSets = StickersQuery.getStickerSets(currentType);
        if (!stickerSets.isEmpty()) {
            stickersStartRow = rowCount;
            stickersEndRow = rowCount + stickerSets.size();
            rowCount += stickerSets.size();
            stickersShadowRow = rowCount++;
        } else {
            stickersStartRow = -1;
            stickersEndRow = -1;
            stickersShadowRow = -1;
        }
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
                ArrayList<TLRPC.TL_messages_stickerSet> arrayList = StickersQuery.getStickerSets(currentType);
                return arrayList.get(i - stickersStartRow).set.id;
            } else if (i == archivedRow || i == archivedInfoRow || i == featuredRow || i == featuredInfoRow || i == masksRow || i == masksInfoRow) {
                return Integer.MIN_VALUE;
            }
            return i;
        }

        private void processSelectionOption(int which, TLRPC.TL_messages_stickerSet stickerSet) {
            if (which == 0) {
                StickersQuery.removeStickersSet(getParentActivity(), stickerSet.set, !stickerSet.set.archived ? 1 : 2, StickersActivity.this, true);
            } else if (which == 1) {
                StickersQuery.removeStickersSet(getParentActivity(), stickerSet.set, 0, StickersActivity.this, true);
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
                    android.content.ClipboardManager clipboard = (android.content.ClipboardManager) ApplicationLoader.applicationContext.getSystemService(Context.CLIPBOARD_SERVICE);
                    android.content.ClipData clip = android.content.ClipData.newPlainText("label", String.format(Locale.US, "https://telegram.me/addstickers/%s", stickerSet.set.short_name));
                    clipboard.setPrimaryClip(clip);
                    Toast.makeText(getParentActivity(), LocaleController.getString("LinkCopied", R.string.LinkCopied), Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    FileLog.e("tmessages", e);
                }
            }
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            switch (holder.getItemViewType()) {
                case 0:
                    ArrayList<TLRPC.TL_messages_stickerSet> arrayList = StickersQuery.getStickerSets(currentType);
                    int row = position - stickersStartRow;
                    ((StickerSetCell) holder.itemView).setStickersSet(arrayList.get(row), row != arrayList.size() - 1);
                    break;
                case 1:
                    if (position == featuredInfoRow) {
                        String text = LocaleController.getString("FeaturedStickersInfo", R.string.FeaturedStickersInfo);
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
                                ((TextInfoPrivacyCell) holder.itemView).setText(stringBuilder);
                            } catch (Exception e) {
                                FileLog.e("tmessages", e);
                                ((TextInfoPrivacyCell) holder.itemView).setText(text);
                            }
                        } else {
                            ((TextInfoPrivacyCell) holder.itemView).setText(text);
                        }
                    } else if (position == archivedInfoRow) {
                        if (currentType == StickersQuery.TYPE_IMAGE) {
                            ((TextInfoPrivacyCell) holder.itemView).setText(LocaleController.getString("ArchivedStickersInfo", R.string.ArchivedStickersInfo));
                        } else {
                            ((TextInfoPrivacyCell) holder.itemView).setText(LocaleController.getString("ArchivedMasksInfo", R.string.ArchivedMasksInfo));
                        }
                    } else if (position == masksInfoRow) {
                        ((TextInfoPrivacyCell) holder.itemView).setText(LocaleController.getString("MasksInfo", R.string.MasksInfo));
                    }
                    break;
                case 2:
                    if (position == featuredRow) {
                        int count = StickersQuery.getUnreadStickerSets().size();
                        ((TextSettingsCell) holder.itemView).setTextAndValue(LocaleController.getString("FeaturedStickers", R.string.FeaturedStickers), count != 0 ? String.format("%d", count) : "", false);
                    } else if (position == archivedRow) {
                        if (currentType == StickersQuery.TYPE_IMAGE) {
                            ((TextSettingsCell) holder.itemView).setText(LocaleController.getString("ArchivedStickers", R.string.ArchivedStickers), false);
                        } else {
                            ((TextSettingsCell) holder.itemView).setText(LocaleController.getString("ArchivedMasks", R.string.ArchivedMasks), false);
                        }
                    } else if (position == masksRow) {
                        ((TextSettingsCell) holder.itemView).setText(LocaleController.getString("Masks", R.string.Masks), true);
                    }
                    break;
            }
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = null;
            switch (viewType) {
                case 0:
                    view = new StickerSetCell(mContext);
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
                            if (currentType == StickersQuery.TYPE_IMAGE) {
                                if (stickerSet.set.official) {
                                    options = new int[]{0};
                                    items = new CharSequence[]{
                                            LocaleController.getString("StickersHide", R.string.StickersHide)
                                    };
                                } else {
                                    options = new int[]{0, 1, 2, 3};
                                    items = new CharSequence[]{
                                            LocaleController.getString("StickersHide", R.string.StickersHide),
                                            LocaleController.getString("StickersRemove", R.string.StickersRemove),
                                            LocaleController.getString("StickersShare", R.string.StickersShare),
                                            LocaleController.getString("StickersCopy", R.string.StickersCopy),
                                    };
                                }
                            } else {
                                if (stickerSet.set.official) {
                                    options = new int[]{0};
                                    items = new CharSequence[]{
                                            LocaleController.getString("StickersRemove", R.string.StickersHide)
                                    };
                                } else {
                                    options = new int[]{0, 1, 2, 3};
                                    items = new CharSequence[]{
                                            LocaleController.getString("StickersHide", R.string.StickersHide),
                                            LocaleController.getString("StickersRemove", R.string.StickersRemove),
                                            LocaleController.getString("StickersShare", R.string.StickersShare),
                                            LocaleController.getString("StickersCopy", R.string.StickersCopy)
                                    };
                                }
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
                    view.setBackgroundResource(R.drawable.greydivider_bottom);
                    break;
                case 2:
                    view = new TextSettingsCell(mContext) {
                        @Override
                        public boolean onTouchEvent(MotionEvent event) {
                            if (Build.VERSION.SDK_INT >= 21 && getBackground() != null) {
                                if (event.getAction() == MotionEvent.ACTION_DOWN || event.getAction() == MotionEvent.ACTION_MOVE) {
                                    getBackground().setHotspot(event.getX(), event.getY());
                                }
                            }
                            return super.onTouchEvent(event);
                        }
                    };
                    view.setBackgroundResource(R.drawable.list_selector_white);
                    break;
                case 3:
                    view = new ShadowSectionCell(mContext);
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
            } else if (i == featuredInfoRow || i == archivedInfoRow || i == masksInfoRow) {
                return 1;
            } else if (i == featuredRow || i == archivedRow || i == masksRow) {
                return 2;
            } else if (i == stickersShadowRow) {
                return 3;
            }
            return 0;
        }

        public void swapElements(int fromIndex, int toIndex) {
            if (fromIndex != toIndex) {
                needReorder = true;
            }
            ArrayList<TLRPC.TL_messages_stickerSet> arrayList = StickersQuery.getStickerSets(currentType);
            TLRPC.TL_messages_stickerSet from = arrayList.get(fromIndex - stickersStartRow);
            arrayList.set(fromIndex - stickersStartRow, arrayList.get(toIndex - stickersStartRow));
            arrayList.set(toIndex - stickersStartRow, from);
            notifyItemMoved(fromIndex, toIndex);
        }
    }
}
