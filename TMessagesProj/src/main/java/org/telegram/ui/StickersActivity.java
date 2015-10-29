/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2015.
 */

package org.telegram.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Build;
import android.os.Message;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.FrameLayout;
import android.widget.ListView;
import android.widget.Toast;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.query.StickersQuery;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Adapters.BaseFragmentAdapter;
import org.telegram.ui.Cells.StickerSetCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.StickersAlert;
import org.telegram.ui.Components.URLSpanNoUnderline;

import java.util.ArrayList;
import java.util.Locale;

public class StickersActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    private ListAdapter listAdapter;

    private int stickersStartRow;
    private int stickersEndRow;
    private int stickersInfoRow;
    private int rowCount;

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
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.stickersDidLoaded);
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

        ListView listView = new ListView(context);
        listView.setDivider(null);
        listView.setDividerHeight(0);
        listView.setVerticalScrollBarEnabled(false);
        listView.setDrawSelectorOnTop(true);
        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        listView.setAdapter(listAdapter);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, final int i, long l) {
                if (i >= stickersStartRow && i < stickersEndRow && getParentActivity() != null) {
                    final TLRPC.TL_messages_stickerSet stickerSet = StickersQuery.getStickerSets().get(i);
                    ArrayList<TLRPC.Document> stickers = stickerSet.documents;
                    if (stickers == null || stickers.isEmpty()) {
                        return;
                    }
                    StickersAlert alert = new StickersAlert(getParentActivity(), stickerSet);
                    alert.setButton(AlertDialog.BUTTON_NEGATIVE, LocaleController.getString("Close", R.string.Close), (Message) null);
                    if ((stickerSet.set.flags & 4) == 0) {
                        alert.setButton(AlertDialog.BUTTON_NEUTRAL, LocaleController.getString("StickersRemove", R.string.StickersRemove), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                StickersQuery.removeStickersSet(getParentActivity(), stickerSet.set, 0);
                            }
                        });
                    }
                    setVisibleDialog(alert);
                    alert.show();
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
            return i >= stickersStartRow && i < stickersEndRow;
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

        private void processSelectionOption(int which, TLRPC.TL_messages_stickerSet stickerSet) {
            if (which == 0) {
                StickersQuery.removeStickersSet(getParentActivity(), stickerSet.set, (stickerSet.set.flags & 2) == 0 ? 1 : 2);
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
        public View getView(int i, View view, ViewGroup viewGroup) {
            int type = getItemViewType(i);
            if (type == 0) {
                if (view == null) {
                    view = new StickerSetCell(mContext);
                    view.setBackgroundColor(0xffffffff);
                    ((StickerSetCell) view).setOnOptionsClick(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            StickerSetCell cell = (StickerSetCell) v.getParent();
                            final TLRPC.TL_messages_stickerSet stickerSet = cell.getStickersSet();
                            AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                            builder.setTitle(stickerSet.set.title);
                            CharSequence[] items;
                            final int[] options;
                            if ((stickerSet.set.flags & 4) != 0) {
                                options = new int[]{0, 2, 3};
                                items = new CharSequence[]{
                                        (stickerSet.set.flags & 2) == 0 ? LocaleController.getString("StickersHide", R.string.StickersHide) : LocaleController.getString("StickersShow", R.string.StickersShow),
                                        LocaleController.getString("StickersShare", R.string.StickersShare),
                                        LocaleController.getString("StickersCopy", R.string.StickersCopy),
                                };
                            } else {
                                options = new int[]{0, 1, 2, 3};
                                items = new CharSequence[]{
                                        (stickerSet.set.flags & 2) == 0 ? LocaleController.getString("StickersHide", R.string.StickersHide) : LocaleController.getString("StickersShow", R.string.StickersShow),
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
                }
                ArrayList<TLRPC.TL_messages_stickerSet> arrayList = StickersQuery.getStickerSets();
                ((StickerSetCell) view).setStickersSet(arrayList.get(i), i != arrayList.size() - 1);
            } else if (type == 1) {
                if (view == null) {
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
                }
            }
            return view;
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

        @Override
        public int getViewTypeCount() {
            return 2;
        }

        @Override
        public boolean isEmpty() {
            return false;
        }
    }
}
