/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2016.
 */

package org.telegram.ui.Components;

import android.content.Context;
import android.os.Build;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.telegram.SQLite.SQLiteCursor;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.R;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.NativeByteBuffer;
import org.telegram.tgnet.RequestDelegate;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.Adapters.BaseFragmentAdapter;
import org.telegram.ui.Cells.ShareDialogCell;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

public class ShareFrameLayout extends FrameLayout {

    private BottomSheet parentBottomSheet;
    private TextView doneButtonBadgeTextView;
    private TextView doneButtonTextView;
    private LinearLayout doneButton;
    private EditText nameTextView;
    private GridView gridView;
    private ShareDialogsAdapter listAdapter;
    private ShareSearchAdapter searchAdapter;
    private MessageObject sendingMessageObject;
    private EmptyTextProgressView searchEmptyView;
    private HashMap<Long, TLRPC.Dialog> selectedDialogs = new HashMap<>();

    private TLRPC.TL_exportedMessageLink exportedMessageLink;
    private boolean loadingLink;
    private boolean copyLinkOnEnd;

    private boolean isPublicChannel;

    public ShareFrameLayout(final Context context, BottomSheet bottomSheet, final MessageObject messageObject, boolean publicChannel) {
        super(context);

        parentBottomSheet = bottomSheet;
        sendingMessageObject = messageObject;
        searchAdapter = new ShareSearchAdapter(context);
        isPublicChannel = publicChannel;

        if (publicChannel) {
            loadingLink = true;
            TLRPC.TL_channels_exportMessageLink req = new TLRPC.TL_channels_exportMessageLink();
            req.id = messageObject.getId();
            req.channel = MessagesController.getInputChannel(messageObject.messageOwner.to_id.channel_id);
            ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
                @Override
                public void run(final TLObject response, TLRPC.TL_error error) {
                    AndroidUtilities.runOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            if (response != null) {
                                exportedMessageLink = (TLRPC.TL_exportedMessageLink) response;
                                if (copyLinkOnEnd) {
                                    copyLink(context);
                                }
                            }
                            loadingLink = false;
                        }
                    });

                }
            });
        }

        FrameLayout frameLayout = new FrameLayout(context);
        frameLayout.setBackgroundColor(0xffffffff);
        addView(frameLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.LEFT | Gravity.TOP));

        doneButton = new LinearLayout(context);
        doneButton.setOrientation(LinearLayout.HORIZONTAL);
        doneButton.setBackgroundResource(R.drawable.bar_selector_audio);
        doneButton.setPadding(AndroidUtilities.dp(21), 0, AndroidUtilities.dp(21), 0);
        frameLayout.addView(doneButton, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.RIGHT));
        doneButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (selectedDialogs.isEmpty() && isPublicChannel) {
                    if (loadingLink) {
                        copyLinkOnEnd = true;
                        Toast.makeText(ShareFrameLayout.this.getContext(), LocaleController.getString("Loading", R.string.Loading), Toast.LENGTH_SHORT).show();
                    } else {
                        copyLink(ShareFrameLayout.this.getContext());
                    }
                    parentBottomSheet.dismiss();
                } else {
                    ArrayList<MessageObject> arrayList = new ArrayList<>();
                    arrayList.add(sendingMessageObject);
                    for (HashMap.Entry<Long, TLRPC.Dialog> entry : selectedDialogs.entrySet()) {
                        TLRPC.Dialog dialog = entry.getValue();
                        boolean asAdmin = true;
                        int lower_id = (int) dialog.id;
                        if (lower_id < 0) {
                            TLRPC.Chat chat = MessagesController.getInstance().getChat(-lower_id);
                            if (chat.megagroup) {
                                asAdmin = false;
                            }
                        }
                        SendMessagesHelper.getInstance().sendMessage(arrayList, entry.getKey(), asAdmin);
                    }
                    parentBottomSheet.dismiss();
                }
            }
        });

        doneButtonBadgeTextView = new TextView(context);
        doneButtonBadgeTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        doneButtonBadgeTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        doneButtonBadgeTextView.setTextColor(0xffffffff);
        doneButtonBadgeTextView.setGravity(Gravity.CENTER);
        doneButtonBadgeTextView.setBackgroundResource(R.drawable.bluecounter);
        doneButtonBadgeTextView.setMinWidth(AndroidUtilities.dp(23));
        doneButtonBadgeTextView.setPadding(AndroidUtilities.dp(8), 0, AndroidUtilities.dp(8), AndroidUtilities.dp(1));
        doneButton.addView(doneButtonBadgeTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 23, Gravity.CENTER_VERTICAL, 0, 0, 10, 0));

        doneButtonTextView = new TextView(context);
        doneButtonTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        doneButtonTextView.setTextColor(0xff19a7e8);
        doneButtonTextView.setGravity(Gravity.CENTER);
        doneButtonTextView.setCompoundDrawablePadding(AndroidUtilities.dp(8));
        doneButtonTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        doneButton.addView(doneButtonTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL));

        ImageView imageView = new ImageView(context);
        imageView.setImageResource(R.drawable.search_share);
        imageView.setScaleType(ImageView.ScaleType.CENTER);
        imageView.setPadding(0, AndroidUtilities.dp(2), 0, 0);
        frameLayout.addView(imageView, LayoutHelper.createFrame(48, 48, Gravity.LEFT | Gravity.CENTER_VERTICAL));

        nameTextView = new EditText(context);
        nameTextView.setHint(LocaleController.getString("ShareSendTo", R.string.ShareSendTo));
        nameTextView.setMaxLines(1);
        nameTextView.setSingleLine(true);
        nameTextView.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);
        nameTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        nameTextView.setBackgroundDrawable(null);
        nameTextView.setHintTextColor(0xff979797);
        nameTextView.setImeOptions(EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        nameTextView.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        AndroidUtilities.clearCursorDrawable(nameTextView);
        nameTextView.setTextColor(0xff212121);
        frameLayout.addView(nameTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT, 48, 2, 96, 0));
        nameTextView.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void afterTextChanged(Editable s) {
                String text = nameTextView.getText().toString();
                if (text.length() != 0) {
                    if (gridView.getAdapter() != searchAdapter) {
                        gridView.setAdapter(searchAdapter);
                        searchAdapter.notifyDataSetChanged();
                    }
                    if (searchEmptyView != null) {
                        searchEmptyView.setText(LocaleController.getString("NoResult", R.string.NoResult));
                    }
                } else {
                    if (gridView.getAdapter() != listAdapter) {
                        searchEmptyView.setText(LocaleController.getString("NoChats", R.string.NoChats));
                        gridView.setAdapter(listAdapter);
                        listAdapter.notifyDataSetChanged();
                    }
                }
                if (searchAdapter != null) {
                    searchAdapter.searchDialogs(text);
                }
            }
        });

        View lineView = new View(context);
        lineView.setBackgroundResource(R.drawable.header_shadow);
        addView(lineView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 3, Gravity.TOP | Gravity.LEFT, 0, 48, 0, 0));

        setOnTouchListener(new OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                return true;
            }
        });

        gridView = new GridView(context);
        gridView.setDrawSelectorOnTop(true);
        gridView.setPadding(0, AndroidUtilities.dp(8), 0, AndroidUtilities.dp(8));
        gridView.setClipToPadding(false);
        gridView.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        gridView.setHorizontalScrollBarEnabled(false);
        gridView.setVerticalScrollBarEnabled(false);
        gridView.setNumColumns(4);
        gridView.setVerticalSpacing(AndroidUtilities.dp(4));
        gridView.setHorizontalSpacing(AndroidUtilities.dp(4));
        gridView.setSelector(R.drawable.list_selector);
        addView(gridView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT, 0, 48, 0, 0));
        gridView.setAdapter(listAdapter = new ShareDialogsAdapter(context));
        AndroidUtilities.setListViewEdgeEffectColor(gridView, 0xfff5f6f7);
        gridView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                TLRPC.Dialog dialog;
                if (gridView.getAdapter() == listAdapter) {
                    dialog = listAdapter.getItem(i);
                } else {
                    dialog = searchAdapter.getItem(i);
                }
                ShareDialogCell cell = (ShareDialogCell) view;
                if (selectedDialogs.containsKey(dialog.id)) {
                    selectedDialogs.remove(dialog.id);
                    cell.setChecked(false, true);
                } else {
                    selectedDialogs.put(dialog.id, dialog);
                    cell.setChecked(true, true);
                }
                updateSelectedCount();
            }
        });

        searchEmptyView = new EmptyTextProgressView(context);
        searchEmptyView.setShowAtCenter(true);
        searchEmptyView.showTextView();
        searchEmptyView.setText(LocaleController.getString("NoChats", R.string.NoChats));
        gridView.setEmptyView(searchEmptyView);
        addView(searchEmptyView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT, 0, 48, 0, 0));

        updateSelectedCount();
    }

    public void copyLink(Context context) {
        if (exportedMessageLink == null) {
            return;
        }
        try {
            if (Build.VERSION.SDK_INT < 11) {
                android.text.ClipboardManager clipboard = (android.text.ClipboardManager) ApplicationLoader.applicationContext.getSystemService(Context.CLIPBOARD_SERVICE);
                clipboard.setText(exportedMessageLink.link);
            } else {
                android.content.ClipboardManager clipboard = (android.content.ClipboardManager) ApplicationLoader.applicationContext.getSystemService(Context.CLIPBOARD_SERVICE);
                android.content.ClipData clip = android.content.ClipData.newPlainText("label", exportedMessageLink.link);
                clipboard.setPrimaryClip(clip);
            }
            Toast.makeText(context, LocaleController.getString("LinkCopied", R.string.LinkCopied), Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            FileLog.e("tmessages", e);
        }
    }

    public void updateSelectedCount() {
        if (selectedDialogs.isEmpty()) {
            doneButtonBadgeTextView.setVisibility(View.GONE);
            if (!isPublicChannel) {
                doneButtonTextView.setTextColor(0xffb3b3b3);
                doneButton.setEnabled(false);
                doneButtonTextView.setText(LocaleController.getString("Send", R.string.Send).toUpperCase());
            } else {
                doneButtonTextView.setTextColor(0xff517fad);
                doneButton.setEnabled(true);
                doneButtonTextView.setText(LocaleController.getString("CopyLink", R.string.CopyLink).toUpperCase());
            }
        } else {
            doneButtonTextView.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
            doneButtonBadgeTextView.setVisibility(View.VISIBLE);
            doneButtonBadgeTextView.setText(String.format("%d", selectedDialogs.size()));
            doneButtonTextView.setTextColor(0xff3ec1f9);
            doneButton.setEnabled(true);
            doneButtonTextView.setText(LocaleController.getString("Send", R.string.Send).toUpperCase());
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(310), MeasureSpec.EXACTLY));
    }

    private class ShareDialogsAdapter extends BaseFragmentAdapter {

        private Context mContext;
        private int currentCount;
        private ArrayList<TLRPC.Dialog> dialogs = new ArrayList<>();

        public ShareDialogsAdapter(Context context) {
            mContext = context;
            for (int a = 0; a < MessagesController.getInstance().dialogsServerOnly.size(); a++) {
                TLRPC.Dialog dialog = MessagesController.getInstance().dialogsServerOnly.get(a);
                int lower_id = (int) dialog.id;
                int high_id = (int) (dialog.id >> 32);
                if (lower_id != 0 && high_id != 1) {
                    if (lower_id > 0) {
                        dialogs.add(dialog);
                    } else {
                        TLRPC.Chat chat = MessagesController.getInstance().getChat(-lower_id);
                        if (!(chat == null || ChatObject.isNotInChat(chat) || ChatObject.isChannel(chat) && !chat.creator && !chat.editor && !chat.megagroup)) {
                            dialogs.add(dialog);
                        }
                    }
                }
            }
        }

        @Override
        public int getCount() {
            return dialogs.size();
        }

        public TLRPC.Dialog getItem(int i) {
            if (i < 0 || i >= dialogs.size()) {
                return null;
            }
            return dialogs.get(i);
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @Override
        public View getView(int i, View view, ViewGroup viewGroup) {
            if (view == null) {
                view = new ShareDialogCell(mContext);
            }
            ShareDialogCell cell = (ShareDialogCell) view;
            TLRPC.Dialog dialog = getItem(i);
            cell.setDialog(dialog, selectedDialogs.containsKey(dialog.id), null);
            return view;
        }

        @Override
        public int getItemViewType(int i) {
            return 0;
        }

        @Override
        public int getViewTypeCount() {
            return 1;
        }

        @Override
        public boolean isEmpty() {
            return getCount() == 0;
        }
    }

    public class ShareSearchAdapter extends BaseFragmentAdapter {

        private Context mContext;
        private Timer searchTimer;
        private ArrayList<DialogSearchResult> searchResult = new ArrayList<>();
        private String lastSearchText;
        private int reqId = 0;
        private int lastReqId;
        private int lastSearchId = 0;

        private class DialogSearchResult {
            public TLRPC.Dialog dialog = new TLRPC.Dialog();
            public TLObject object;
            public int date;
            public CharSequence name;
        }

        public ShareSearchAdapter(Context context) {
            mContext = context;
        }

        private void searchDialogsInternal(final String query, final int searchId) {
            MessagesStorage.getInstance().getStorageQueue().postRunnable(new Runnable() {
                @Override
                public void run() {
                    try {
                        String search1 = query.trim().toLowerCase();
                        if (search1.length() == 0) {
                            lastSearchId = -1;
                            updateSearchResults(new ArrayList<DialogSearchResult>(), lastSearchId);
                            return;
                        }
                        String search2 = LocaleController.getInstance().getTranslitString(search1);
                        if (search1.equals(search2) || search2.length() == 0) {
                            search2 = null;
                        }
                        String search[] = new String[1 + (search2 != null ? 1 : 0)];
                        search[0] = search1;
                        if (search2 != null) {
                            search[1] = search2;
                        }

                        ArrayList<Integer> usersToLoad = new ArrayList<>();
                        ArrayList<Integer> chatsToLoad = new ArrayList<>();
                        int resultCount = 0;

                        HashMap<Long, DialogSearchResult> dialogsResult = new HashMap<>();
                        SQLiteCursor cursor = MessagesStorage.getInstance().getDatabase().queryFinalized("SELECT did, date FROM dialogs ORDER BY date DESC LIMIT 400");
                        while (cursor.next()) {
                            long id = cursor.longValue(0);
                            DialogSearchResult dialogSearchResult = new DialogSearchResult();
                            dialogSearchResult.date = cursor.intValue(1);
                            dialogsResult.put(id, dialogSearchResult);

                            int lower_id = (int) id;
                            int high_id = (int) (id >> 32);
                            if (lower_id != 0 && high_id != 1) {
                                if (lower_id > 0) {
                                    if (!usersToLoad.contains(lower_id)) {
                                        usersToLoad.add(lower_id);
                                    }
                                } else {
                                    if (!chatsToLoad.contains(-lower_id)) {
                                        chatsToLoad.add(-lower_id);
                                    }
                                }
                            }
                        }
                        cursor.dispose();

                        if (!usersToLoad.isEmpty()) {
                            cursor = MessagesStorage.getInstance().getDatabase().queryFinalized(String.format(Locale.US, "SELECT data, status, name FROM users WHERE uid IN(%s)", TextUtils.join(",", usersToLoad)));
                            while (cursor.next()) {
                                String name = cursor.stringValue(2);
                                String tName = LocaleController.getInstance().getTranslitString(name);
                                if (name.equals(tName)) {
                                    tName = null;
                                }
                                String username = null;
                                int usernamePos = name.lastIndexOf(";;;");
                                if (usernamePos != -1) {
                                    username = name.substring(usernamePos + 3);
                                }
                                int found = 0;
                                for (String q : search) {
                                    if (name.startsWith(q) || name.contains(" " + q) || tName != null && (tName.startsWith(q) || tName.contains(" " + q))) {
                                        found = 1;
                                    } else if (username != null && username.startsWith(q)) {
                                        found = 2;
                                    }
                                    if (found != 0) {
                                        NativeByteBuffer data = new NativeByteBuffer(cursor.byteArrayLength(0));
                                        if (data != null && cursor.byteBufferValue(0, data) != 0) {
                                            TLRPC.User user = TLRPC.User.TLdeserialize(data, data.readInt32(false), false);
                                            DialogSearchResult dialogSearchResult = dialogsResult.get((long) user.id);
                                            if (user.status != null) {
                                                user.status.expires = cursor.intValue(1);
                                            }
                                            if (found == 1) {
                                                dialogSearchResult.name = AndroidUtilities.generateSearchName(user.first_name, user.last_name, q);
                                            } else {
                                                dialogSearchResult.name = AndroidUtilities.generateSearchName("@" + user.username, null, "@" + q);
                                            }
                                            dialogSearchResult.object = user;
                                            dialogSearchResult.dialog.id = user.id;
                                            resultCount++;
                                        }
                                        data.reuse();
                                        break;
                                    }
                                }
                            }
                            cursor.dispose();
                        }

                        if (!chatsToLoad.isEmpty()) {
                            cursor = MessagesStorage.getInstance().getDatabase().queryFinalized(String.format(Locale.US, "SELECT data, name FROM chats WHERE uid IN(%s)", TextUtils.join(",", chatsToLoad)));
                            while (cursor.next()) {
                                String name = cursor.stringValue(1);
                                String tName = LocaleController.getInstance().getTranslitString(name);
                                if (name.equals(tName)) {
                                    tName = null;
                                }
                                for (int a = 0; a < search.length; a++) {
                                    String q = search[a];
                                    if (name.startsWith(q) || name.contains(" " + q) || tName != null && (tName.startsWith(q) || tName.contains(" " + q))) {
                                        NativeByteBuffer data = new NativeByteBuffer(cursor.byteArrayLength(0));
                                        if (data != null && cursor.byteBufferValue(0, data) != 0) {
                                            TLRPC.Chat chat = TLRPC.Chat.TLdeserialize(data, data.readInt32(false), false);
                                            if (!(chat == null || ChatObject.isNotInChat(chat) || ChatObject.isChannel(chat) && !chat.creator && !chat.editor && !chat.megagroup)) {
                                                DialogSearchResult dialogSearchResult = dialogsResult.get(-(long) chat.id);
                                                dialogSearchResult.name = AndroidUtilities.generateSearchName(chat.title, null, q);
                                                dialogSearchResult.object = chat;
                                                dialogSearchResult.dialog.id = -chat.id;
                                                resultCount++;
                                            }
                                        }
                                        data.reuse();
                                        break;
                                    }
                                }
                            }
                            cursor.dispose();
                        }

                        ArrayList<DialogSearchResult> searchResults = new ArrayList<>(resultCount);
                        for (DialogSearchResult dialogSearchResult : dialogsResult.values()) {
                            if (dialogSearchResult.object != null && dialogSearchResult.name != null) {
                                searchResults.add(dialogSearchResult);
                            }
                        }

                        cursor = MessagesStorage.getInstance().getDatabase().queryFinalized("SELECT u.data, u.status, u.name, u.uid FROM users as u INNER JOIN contacts as c ON u.uid = c.uid");
                        while (cursor.next()) {
                            int uid = cursor.intValue(3);
                            if (dialogsResult.containsKey((long) uid)) {
                                continue;
                            }
                            String name = cursor.stringValue(2);
                            String tName = LocaleController.getInstance().getTranslitString(name);
                            if (name.equals(tName)) {
                                tName = null;
                            }
                            String username = null;
                            int usernamePos = name.lastIndexOf(";;;");
                            if (usernamePos != -1) {
                                username = name.substring(usernamePos + 3);
                            }
                            int found = 0;
                            for (String q : search) {
                                if (name.startsWith(q) || name.contains(" " + q) || tName != null && (tName.startsWith(q) || tName.contains(" " + q))) {
                                    found = 1;
                                } else if (username != null && username.startsWith(q)) {
                                    found = 2;
                                }
                                if (found != 0) {
                                    NativeByteBuffer data = new NativeByteBuffer(cursor.byteArrayLength(0));
                                    if (data != null && cursor.byteBufferValue(0, data) != 0) {
                                        TLRPC.User user = TLRPC.User.TLdeserialize(data, data.readInt32(false), false);
                                        DialogSearchResult dialogSearchResult = new DialogSearchResult();
                                        if (user.status != null) {
                                            user.status.expires = cursor.intValue(1);
                                        }
                                        dialogSearchResult.dialog.id = user.id;
                                        dialogSearchResult.object = user;
                                        if (found == 1) {
                                            dialogSearchResult.name = AndroidUtilities.generateSearchName(user.first_name, user.last_name, q);
                                        } else {
                                            dialogSearchResult.name = AndroidUtilities.generateSearchName("@" + user.username, null, "@" + q);
                                        }
                                        searchResults.add(dialogSearchResult);
                                    }
                                    data.reuse();
                                    break;
                                }
                            }
                        }
                        cursor.dispose();

                        Collections.sort(searchResults, new Comparator<DialogSearchResult>() {
                            @Override
                            public int compare(DialogSearchResult lhs, DialogSearchResult rhs) {
                                if (lhs.date < rhs.date) {
                                    return 1;
                                } else if (lhs.date > rhs.date) {
                                    return -1;
                                }
                                return 0;
                            }
                        });

                        updateSearchResults(searchResults, searchId);
                    } catch (Exception e) {
                        FileLog.e("tmessages", e);
                    }
                }
            });
        }

        private void updateSearchResults(final ArrayList<DialogSearchResult> result, final int searchId) {
            AndroidUtilities.runOnUIThread(new Runnable() {
                @Override
                public void run() {
                    if (searchId != lastSearchId) {
                        return;
                    }
                    for (int a = 0; a < result.size(); a++) {
                        DialogSearchResult obj = result.get(a);
                        if (obj.object instanceof TLRPC.User) {
                            TLRPC.User user = (TLRPC.User) obj.object;
                            MessagesController.getInstance().putUser(user, true);
                        } else if (obj.object instanceof TLRPC.Chat) {
                            TLRPC.Chat chat = (TLRPC.Chat) obj.object;
                            MessagesController.getInstance().putChat(chat, true);
                        }
                    }
                    searchResult = result;
                    notifyDataSetChanged();
                }
            });
        }

        public void searchDialogs(final String query) {
            if (query != null && lastSearchText != null && query.equals(lastSearchText)) {
                return;
            }
            lastSearchText = query;
            try {
                if (searchTimer != null) {
                    searchTimer.cancel();
                    searchTimer = null;
                }
            } catch (Exception e) {
                FileLog.e("tmessages", e);
            }
            if (query == null || query.length() == 0) {
                searchResult.clear();
                notifyDataSetChanged();
            } else {
                final int searchId = ++lastSearchId;
                searchTimer = new Timer();
                searchTimer.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        try {
                            cancel();
                            searchTimer.cancel();
                            searchTimer = null;
                        } catch (Exception e) {
                            FileLog.e("tmessages", e);
                        }
                        searchDialogsInternal(query, searchId);
                    }
                }, 200, 300);
            }
        }

        @Override
        public int getCount() {
            return searchResult.size();
        }

        public TLRPC.Dialog getItem(int i) {
            return searchResult.get(i).dialog;
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @Override
        public View getView(int i, View view, ViewGroup viewGroup) {
            if (view == null) {
                view = new ShareDialogCell(mContext);
            }
            ShareDialogCell cell = (ShareDialogCell) view;
            DialogSearchResult result = searchResult.get(i);
            cell.setDialog(result.dialog, selectedDialogs.containsKey(result.dialog.id), result.name);
            return view;
        }

        @Override
        public int getItemViewType(int i) {
            return 0;
        }

        @Override
        public boolean isEmpty() {
            return getCount() == 0;
        }
    }
}
