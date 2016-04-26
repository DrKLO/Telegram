/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2016.
 */

package org.telegram.ui.Components;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.database.DataSetObserver;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.GridView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.NotificationCenter;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Cells.StickerEmojiCell;
import org.telegram.ui.StickerPreviewViewer;

import java.util.ArrayList;

public class StickersAlert extends AlertDialog implements NotificationCenter.NotificationCenterDelegate {

    private ArrayList<TLRPC.Document> stickers;
    private GridView gridView;

    public StickersAlert(Context context, TLRPC.TL_messages_stickerSet set) {
        super(context);
        stickers = set.documents;

        FrameLayout container = new FrameLayout(context) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec((int) Math.min(Math.ceil(stickers.size() / 4.0f) * AndroidUtilities.dp(82), AndroidUtilities.displaySize.y / 5 * 3), MeasureSpec.EXACTLY));
            }
        };
        setView(container, AndroidUtilities.dp(16), 0, AndroidUtilities.dp(16), 0);

        gridView = new GridView(context) {
            @Override
            public boolean onInterceptTouchEvent(MotionEvent event) {
                boolean result = StickerPreviewViewer.getInstance().onInterceptTouchEvent(event, gridView, 0);
                return super.onInterceptTouchEvent(event) || result;
            }
        };
        gridView.setNumColumns(4);
        gridView.setAdapter(new GridAdapter(context));
        gridView.setVerticalScrollBarEnabled(false);
        gridView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                return StickerPreviewViewer.getInstance().onTouch(event, gridView, 0, null);
            }
        });
        container.addView(gridView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        setTitle(set.set.title);

        NotificationCenter.getInstance().addObserver(this, NotificationCenter.emojiDidLoaded);

        setOnShowListener(new OnShowListener() {
            @Override
            public void onShow(DialogInterface arg0) {
                if (getButton(AlertDialog.BUTTON_NEUTRAL) != null) {
                    getButton(AlertDialog.BUTTON_NEUTRAL).setTextColor(0xffcd5a5a);
                }
                if (getButton(AlertDialog.BUTTON_POSITIVE) != null) {
                    getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(0xff37a919);
                }
            }
        });
    }

    @Override
    public void dismiss() {
        super.dismiss();
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.emojiDidLoaded);
    }

    @Override
    public void didReceivedNotification(int id, Object... args) {
        if (id == NotificationCenter.emojiDidLoaded) {
            if (gridView != null) {
                gridView.invalidateViews();
            }
            if (StickerPreviewViewer.getInstance().isVisible()) {
                StickerPreviewViewer.getInstance().close();
            }
            StickerPreviewViewer.getInstance().reset();
        }
    }

    private class GridAdapter extends BaseAdapter {

        Context context;

        public GridAdapter(Context context) {
            this.context = context;
        }

        public int getCount() {
            return stickers.size();
        }

        public Object getItem(int i) {
            return stickers.get(i);
        }

        public long getItemId(int i) {
            return stickers.get(i).id;
        }

        @Override
        public boolean areAllItemsEnabled() {
            return false;
        }

        @Override
        public boolean isEnabled(int position) {
            return false;
        }

        public View getView(int i, View view, ViewGroup viewGroup) {
            if (view == null) {
                view = new StickerEmojiCell(context) {
                    public void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(82), MeasureSpec.EXACTLY));
                    }
                };
            }
            ((StickerEmojiCell) view).setSticker(stickers.get(i), true);
            return view;
        }

        @Override
        public void unregisterDataSetObserver(DataSetObserver observer) {
            if (observer != null) {
                super.unregisterDataSetObserver(observer);
            }
        }
    }
}
