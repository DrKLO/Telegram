/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2017.
 */

package org.telegram.ui.Adapters;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.support.widget.RecyclerView;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.DialogCell;
import org.telegram.ui.Cells.DialogMeUrlCell;
import org.telegram.ui.Cells.DialogsEmptyCell;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.LoadingCell;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;

public class DialogsAdapter extends RecyclerListView.SelectionAdapter {

    private Context mContext;
    private int dialogsType;
    private long openedDialogId;
    private int currentCount;
    private boolean isOnlySelect;
    private ArrayList<Long> selectedDialogs;
    private boolean hasHints;

    public DialogsAdapter(Context context, int type, boolean onlySelect) {
        mContext = context;
        dialogsType = type;
        isOnlySelect = onlySelect;
        hasHints = type == 0 && !onlySelect;
        if (onlySelect) {
            selectedDialogs = new ArrayList<>();
        }
    }

    public void setOpenedDialogId(long id) {
        openedDialogId = id;
    }

    public boolean hasSelectedDialogs() {
        return selectedDialogs != null && !selectedDialogs.isEmpty();
    }

    public void addOrRemoveSelectedDialog(long did, View cell) {
        if (selectedDialogs.contains(did)) {
            selectedDialogs.remove(did);
            if (cell instanceof DialogCell) {
                ((DialogCell) cell).setChecked(false, true);
            }
        } else {
            selectedDialogs.add(did);
            if (cell instanceof DialogCell) {
                ((DialogCell) cell).setChecked(true, true);
            }
        }
    }

    public ArrayList<Long> getSelectedDialogs() {
        return selectedDialogs;
    }

    public boolean isDataSetChanged() {
        int current = currentCount;
        return current != getItemCount() || current == 1;
    }

    private ArrayList<TLRPC.TL_dialog> getDialogsArray() {
        if (dialogsType == 0) {
            return MessagesController.getInstance().dialogs;
        } else if (dialogsType == 1) {
            return MessagesController.getInstance().dialogsServerOnly;
        } else if (dialogsType == 2) {
            return MessagesController.getInstance().dialogsGroupsOnly;
        } else if (dialogsType == 3) {
            return MessagesController.getInstance().dialogsForward;
        }
        return null;
    }

    @Override
    public int getItemCount() {
        int count = getDialogsArray().size();
        if (count == 0 && MessagesController.getInstance().loadingDialogs) {
            return 0;
        }
        if (!MessagesController.getInstance().dialogsEndReached || count == 0) {
            count++;
        }
        if (hasHints) {
            count += 2 + MessagesController.getInstance().hintDialogs.size();
        }
        currentCount = count;
        return count;
    }

    public TLObject getItem(int i) {
        ArrayList<TLRPC.TL_dialog> arrayList = getDialogsArray();
        if (hasHints) {
            int count = MessagesController.getInstance().hintDialogs.size();
            if (i < 2 + count) {
                return MessagesController.getInstance().hintDialogs.get(i - 1);
            } else {
                i -= count + 2;
            }
        }
        if (i < 0 || i >= arrayList.size()) {
            return null;
        }
        return arrayList.get(i);
    }

    @Override
    public void notifyDataSetChanged() {
        hasHints = dialogsType == 0 && !isOnlySelect && !MessagesController.getInstance().hintDialogs.isEmpty();
        super.notifyDataSetChanged();
    }

    @Override
    public void onViewAttachedToWindow(RecyclerView.ViewHolder holder) {
        if (holder.itemView instanceof DialogCell) {
            ((DialogCell) holder.itemView).checkCurrentDialogIndex();
        }
    }

    @Override
    public boolean isEnabled(RecyclerView.ViewHolder holder) {
        int viewType = holder.getItemViewType();
        return viewType != 1 && viewType != 5 && viewType != 3;
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup viewGroup, int viewType) {
        View view;
        switch (viewType) {
            case 0:
                view = new DialogCell(mContext, isOnlySelect);
                break;
            case 1:
                view = new LoadingCell(mContext);
                break;
            case 2:
                HeaderCell headerCell = new HeaderCell(mContext);
                headerCell.setText(LocaleController.getString("RecentlyViewed", R.string.RecentlyViewed));

                TextView textView = new TextView(mContext);
                textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
                textView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
                textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueHeader));
                textView.setText(LocaleController.getString("RecentlyViewedHide", R.string.RecentlyViewedHide));
                textView.setGravity((LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.CENTER_VERTICAL);
                headerCell.addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.TOP, 17, 15, 17, 0));
                textView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        MessagesController.getInstance().hintDialogs.clear();
                        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
                        preferences.edit().remove("installReferer").commit();
                        notifyDataSetChanged();
                    }
                });

                view = headerCell;
                break;
            case 3:
                FrameLayout frameLayout = new FrameLayout(mContext) {
                    @Override
                    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                        super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(12), MeasureSpec.EXACTLY));
                    }
                };
                frameLayout.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
                View v = new View(mContext);
                v.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
                frameLayout.addView(v, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
                view = frameLayout;
                break;
            case 4:
                view = new DialogMeUrlCell(mContext);
                break;
            case 5:
            default:
                view = new DialogsEmptyCell(mContext);
                break;
        }
        view.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, viewType == 5 ? RecyclerView.LayoutParams.MATCH_PARENT : RecyclerView.LayoutParams.WRAP_CONTENT));
        return new RecyclerListView.Holder(view);
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder, int i) {
        switch (holder.getItemViewType()) {
            case 0: {
                DialogCell cell = (DialogCell) holder.itemView;
                TLRPC.TL_dialog dialog = (TLRPC.TL_dialog) getItem(i);
                if (hasHints) {
                    i -= 2 + MessagesController.getInstance().hintDialogs.size();
                }
                cell.useSeparator = (i != getItemCount() - 1);
                if (dialogsType == 0) {
                    if (AndroidUtilities.isTablet()) {
                        cell.setDialogSelected(dialog.id == openedDialogId);
                    }
                }
                if (selectedDialogs != null) {
                    cell.setChecked(selectedDialogs.contains(dialog.id), false);
                }
                cell.setDialog(dialog, i, dialogsType);
                break;
            }
            case 4: {
                DialogMeUrlCell cell = (DialogMeUrlCell) holder.itemView;
                cell.setRecentMeUrl((TLRPC.RecentMeUrl) getItem(i));
                break;
            }
        }
    }

    @Override
    public int getItemViewType(int i) {
        if (hasHints) {
            int count = MessagesController.getInstance().hintDialogs.size();
            if (i < 2 + count) {
                if (i == 0) {
                    return 2;
                } else if (i == 1 + count) {
                    return 3;
                }
                return 4;
            } else {
                i -= 2 + count;
            }
        }
        if (i == getDialogsArray().size()) {
            if (!MessagesController.getInstance().dialogsEndReached) {
                return 1;
            } else {
                return 5;
            }
        }
        return 0;
    }
}
