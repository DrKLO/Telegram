/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.net.Uri;
import android.text.Editable;
import android.text.InputType;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.StickerSetCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.ContextProgressView;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.StickersAlert;
import org.telegram.ui.Components.URLSpanNoUnderline;

import java.util.ArrayList;
import java.util.List;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public class GroupStickersActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    private RecyclerListView listView;
    private ListAdapter listAdapter;
    private ActionBarMenuItem doneItem;
    private ContextProgressView progressView;
    private AnimatorSet doneItemAnimation;
    private LinearLayout nameContainer;
    private EditTextBoldCursor editText;
    private EditTextBoldCursor usernameTextView;
    private LinearLayoutManager layoutManager;
    private ImageView eraseImageView;

    private Runnable queryRunnable;

    private boolean searchWas;
    private boolean searching;

    private boolean ignoreTextChanges;

    private int reqId;

    private TLRPC.TL_messages_stickerSet selectedStickerSet;

    private TLRPC.ChatFull info;
    private long chatId;

    private boolean donePressed;

    private int nameRow;
    private int infoRow;
    private int selectedStickerRow;
    private int headerRow;
    private int stickersStartRow;
    private int stickersEndRow;
    private int stickersShadowRow;
    private int rowCount;

    private final static int done_button = 1;

    public GroupStickersActivity(long id) {
        super();
        chatId = id;
    }

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();
        MediaDataController.getInstance(currentAccount).checkStickers(MediaDataController.TYPE_IMAGE);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.stickersDidLoad);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.chatInfoDidLoad);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.groupStickersDidLoad);
        updateRows();
        return true;
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.stickersDidLoad);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.chatInfoDidLoad);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.groupStickersDidLoad);
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(LocaleController.getString("GroupStickers", R.string.GroupStickers));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                } else if (id == done_button) {
                    if (donePressed) {
                        return;
                    }
                    donePressed = true;
                    if (searching) {
                        showEditDoneProgress(true);
                        return;
                    }
                    saveStickerSet();
                }
            }
        });

        ActionBarMenu menu = actionBar.createMenu();
        doneItem = menu.addItemWithWidth(done_button, R.drawable.ic_done, AndroidUtilities.dp(56), LocaleController.getString("Done", R.string.Done));
        progressView = new ContextProgressView(context, 1);
        progressView.setAlpha(0.0f);
        progressView.setScaleX(0.1f);
        progressView.setScaleY(0.1f);
        progressView.setVisibility(View.INVISIBLE);
        doneItem.addView(progressView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        nameContainer = new LinearLayout(context) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(42), MeasureSpec.EXACTLY));
            }

            @Override
            protected void onDraw(Canvas canvas) {
                if (selectedStickerSet != null) {
                    canvas.drawLine(0, getHeight() - 1, getWidth(), getHeight() - 1, Theme.dividerPaint);
                }
            }
        };
        nameContainer.setWeightSum(1.0f);
        nameContainer.setWillNotDraw(false);
        nameContainer.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        nameContainer.setOrientation(LinearLayout.HORIZONTAL);
        nameContainer.setPadding(AndroidUtilities.dp(17), 0, AndroidUtilities.dp(14), 0);

        editText = new EditTextBoldCursor(context);
        editText.setText(MessagesController.getInstance(currentAccount).linkPrefix + "/addstickers/");
        editText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17);
        editText.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText));
        editText.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        editText.setMaxLines(1);
        editText.setLines(1);
        editText.setEnabled(false);
        editText.setFocusable(false);
        editText.setBackgroundDrawable(null);
        editText.setPadding(0, 0, 0, 0);
        editText.setGravity(Gravity.CENTER_VERTICAL);
        editText.setSingleLine(true);
        editText.setInputType(InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_AUTO_CORRECT);
        editText.setImeOptions(EditorInfo.IME_ACTION_DONE);
        nameContainer.addView(editText, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 42));

        usernameTextView = new EditTextBoldCursor(context);
        usernameTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17);
        usernameTextView.setCursorColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        usernameTextView.setCursorSize(AndroidUtilities.dp(20));
        usernameTextView.setCursorWidth(1.5f);
        usernameTextView.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText));
        usernameTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        usernameTextView.setMaxLines(1);
        usernameTextView.setLines(1);
        usernameTextView.setBackgroundDrawable(null);
        usernameTextView.setPadding(0, 0, 0, 0);
        usernameTextView.setSingleLine(true);
        usernameTextView.setGravity(Gravity.CENTER_VERTICAL);
        usernameTextView.setInputType(InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_AUTO_CORRECT);
        usernameTextView.setImeOptions(EditorInfo.IME_ACTION_DONE);
        usernameTextView.setHint(LocaleController.getString("ChooseStickerSetPlaceholder", R.string.ChooseStickerSetPlaceholder));
        usernameTextView.addTextChangedListener(new TextWatcher() {

            boolean ignoreTextChange;

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void afterTextChanged(Editable s) {
                if (eraseImageView != null) {
                    eraseImageView.setVisibility(s.length() > 0 ? View.VISIBLE : View.INVISIBLE);
                }
                if (ignoreTextChange || ignoreTextChanges) {
                    return;
                }
                if (s.length() > 5) {
                    ignoreTextChange = true;
                    try {
                        Uri uri = Uri.parse(s.toString());
                        if (uri != null) {
                            List<String> segments = uri.getPathSegments();
                            if (segments.size() == 2) {
                                if (segments.get(0).toLowerCase().equals("addstickers")) {
                                    usernameTextView.setText(segments.get(1));
                                    usernameTextView.setSelection(usernameTextView.length());
                                }
                            }
                        }
                    } catch (Exception ignore) {

                    }
                    ignoreTextChange = false;
                }
                resolveStickerSet();
            }
        });

        nameContainer.addView(usernameTextView, LayoutHelper.createLinear(0, 42, 1.0f));

        eraseImageView = new ImageView(context);
        eraseImageView.setScaleType(ImageView.ScaleType.CENTER);
        eraseImageView.setImageResource(R.drawable.ic_close_white);
        eraseImageView.setPadding(AndroidUtilities.dp(16), 0, 0, 0);
        eraseImageView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText3), PorterDuff.Mode.MULTIPLY));
        eraseImageView.setVisibility(View.INVISIBLE);
        eraseImageView.setOnClickListener(v -> {
            searchWas = false;
            selectedStickerSet = null;
            usernameTextView.setText("");
            updateRows();
        });
        nameContainer.addView(eraseImageView, LayoutHelper.createLinear(42, 42, 0.0f));

        if (info != null && info.stickerset != null) {
            ignoreTextChanges = true;
            usernameTextView.setText(info.stickerset.short_name);
            usernameTextView.setSelection(usernameTextView.length());
            ignoreTextChanges = false;
        }

        listAdapter = new ListAdapter(context);

        fragmentView = new FrameLayout(context);
        FrameLayout frameLayout = (FrameLayout) fragmentView;
        frameLayout.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));

        listView = new RecyclerListView(context);
        listView.setFocusable(true);
        listView.setItemAnimator(null);
        listView.setLayoutAnimation(null);
        layoutManager = new LinearLayoutManager(context) {
            @Override
            public boolean requestChildRectangleOnScreen(RecyclerView parent, View child, Rect rect, boolean immediate, boolean focusedChildVisible) {
                return false;
            }

            @Override
            public boolean supportsPredictiveItemAnimations() {
                return false;
            }
        };
        layoutManager.setOrientation(LinearLayoutManager.VERTICAL);
        listView.setLayoutManager(layoutManager);

        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        listView.setAdapter(listAdapter);
        listView.setOnItemClickListener((view, position) -> {
            if (getParentActivity() == null) {
                return;
            }
            if (position == selectedStickerRow) {
                if (selectedStickerSet == null) {
                    return;
                }
                showDialog(new StickersAlert(getParentActivity(), GroupStickersActivity.this, null, selectedStickerSet, null));
            } else if (position >= stickersStartRow && position < stickersEndRow) {
                boolean needScroll = selectedStickerRow == -1;
                int row = layoutManager.findFirstVisibleItemPosition();
                int top = Integer.MAX_VALUE;
                RecyclerListView.Holder holder = (RecyclerListView.Holder) listView.findViewHolderForAdapterPosition(row);
                if (holder != null) {
                    top = holder.itemView.getTop();
                }
                selectedStickerSet = MediaDataController.getInstance(currentAccount).getStickerSets(MediaDataController.TYPE_IMAGE).get(position - stickersStartRow);
                ignoreTextChanges = true;
                usernameTextView.setText(selectedStickerSet.set.short_name);
                usernameTextView.setSelection(usernameTextView.length());
                ignoreTextChanges = false;
                AndroidUtilities.hideKeyboard(usernameTextView);
                updateRows();
                if (needScroll && top != Integer.MAX_VALUE) {
                    layoutManager.scrollToPositionWithOffset(row + 1, top);
                }
            }
        });
        listView.setOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                    AndroidUtilities.hideKeyboard(getParentActivity().getCurrentFocus());
                }
            }

            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {

            }
        });

        return fragmentView;
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.stickersDidLoad) {
            if ((Integer) args[0] == MediaDataController.TYPE_IMAGE) {
                updateRows();
            }
        } else if (id == NotificationCenter.chatInfoDidLoad) {
            TLRPC.ChatFull chatFull = (TLRPC.ChatFull) args[0];
            if (chatFull.id == chatId) {
                if (info == null && chatFull.stickerset != null) {
                    selectedStickerSet = MediaDataController.getInstance(currentAccount).getGroupStickerSetById(chatFull.stickerset);
                }
                info = chatFull;
                updateRows();
            }
        } else if (id == NotificationCenter.groupStickersDidLoad) {
            long setId = (Long) args[0];
            if (info != null && info.stickerset != null && info.stickerset.id == id) {
                updateRows();
            }
        }
    }

    public void setInfo(TLRPC.ChatFull chatFull) {
        info = chatFull;
        if (info != null && info.stickerset != null) {
            selectedStickerSet = MediaDataController.getInstance(currentAccount).getGroupStickerSetById(info.stickerset);
        }
    }

    private void resolveStickerSet() {
        if (listAdapter == null) {
            return;
        }
        if (reqId != 0) {
            ConnectionsManager.getInstance(currentAccount).cancelRequest(reqId, true);
            reqId = 0;
        }
        if (queryRunnable != null) {
            AndroidUtilities.cancelRunOnUIThread(queryRunnable);
            queryRunnable = null;
        }
        selectedStickerSet = null;
        if (usernameTextView.length() <= 0) {
            searching = false;
            searchWas = false;
            if (selectedStickerRow != -1) {
                updateRows();
            }
            return;
        }
        searching = true;
        searchWas = true;
        final String query = usernameTextView.getText().toString();
        TLRPC.TL_messages_stickerSet existingSet = MediaDataController.getInstance(currentAccount).getStickerSetByName(query);
        if (existingSet != null) {
            selectedStickerSet = existingSet;
        }
        if (selectedStickerRow == -1) {
            updateRows();
        } else {
            listAdapter.notifyItemChanged(selectedStickerRow);
        }
        if (existingSet != null) {
            searching = false;
            return;
        }
        AndroidUtilities.runOnUIThread(queryRunnable = () -> {
            if (queryRunnable == null) {
                return;
            }
            TLRPC.TL_messages_getStickerSet req = new TLRPC.TL_messages_getStickerSet();
            req.stickerset = new TLRPC.TL_inputStickerSetShortName();
            req.stickerset.short_name = query;
            reqId = ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                searching = false;
                if (response instanceof TLRPC.TL_messages_stickerSet) {
                    selectedStickerSet = (TLRPC.TL_messages_stickerSet) response;
                    if (donePressed) {
                        saveStickerSet();
                    } else {
                        if (selectedStickerRow != -1) {
                            listAdapter.notifyItemChanged(selectedStickerRow);
                        } else {
                            updateRows();
                        }
                    }
                } else {
                    if (selectedStickerRow != -1) {
                        listAdapter.notifyItemChanged(selectedStickerRow);
                    }
                    if (donePressed) {
                        donePressed = false;
                        showEditDoneProgress(false);
                        if (getParentActivity() != null) {
                            Toast.makeText(getParentActivity(), LocaleController.getString("AddStickersNotFound", R.string.AddStickersNotFound), Toast.LENGTH_SHORT).show();
                        }
                    }
                }
                reqId = 0;
            }));
        }, 500);
    }

    @Override
    public void onTransitionAnimationEnd(boolean isOpen, boolean backward) {
        if (isOpen) {
            AndroidUtilities.runOnUIThread(() -> {
                if (usernameTextView != null) {
                    usernameTextView.requestFocus();
                    AndroidUtilities.showKeyboard(usernameTextView);
                }
            }, 100);
        }
    }

    private void saveStickerSet() {
        if (info == null || info.stickerset != null && selectedStickerSet != null && selectedStickerSet.set.id == info.stickerset.id || info.stickerset == null && selectedStickerSet == null) {
            finishFragment();
            return;
        }
        showEditDoneProgress(true);
        TLRPC.TL_channels_setStickers req = new TLRPC.TL_channels_setStickers();
        req.channel = MessagesController.getInstance(currentAccount).getInputChannel(chatId);
        if (selectedStickerSet == null) {
            req.stickerset = new TLRPC.TL_inputStickerSetEmpty();
        } else {
            MessagesController.getEmojiSettings(currentAccount).edit().remove("group_hide_stickers_" + info.id).commit();
            req.stickerset = new TLRPC.TL_inputStickerSetID();
            req.stickerset.id = selectedStickerSet.set.id;
            req.stickerset.access_hash = selectedStickerSet.set.access_hash;
        }
        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (error == null) {
                if (selectedStickerSet == null) {
                    info.stickerset = null;
                } else {
                    info.stickerset = selectedStickerSet.set;
                    MediaDataController.getInstance(currentAccount).putGroupStickerSet(selectedStickerSet);
                }
                if (info.stickerset == null) {
                    info.flags |= 256;
                } else {
                    info.flags = info.flags &~ 256;
                }
                MessagesStorage.getInstance(currentAccount).updateChatInfo(info, false);
                NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.chatInfoDidLoad, info, 0, true, false);
                finishFragment();
            } else {
                Toast.makeText(getParentActivity(), LocaleController.getString("ErrorOccurred", R.string.ErrorOccurred) + "\n" + error.text, Toast.LENGTH_SHORT).show();
                donePressed = false;
                showEditDoneProgress(false);
            }
        }));
    }

    private void updateRows() {
        rowCount = 0;
        nameRow = rowCount++;
        if (selectedStickerSet != null || searchWas) {
            selectedStickerRow = rowCount++;
        } else {
            selectedStickerRow = -1;
        }
        infoRow = rowCount++;
        ArrayList<TLRPC.TL_messages_stickerSet> stickerSets = MediaDataController.getInstance(currentAccount).getStickerSets(MediaDataController.TYPE_IMAGE);
        if (!stickerSets.isEmpty()) {
            headerRow = rowCount++;
            stickersStartRow = rowCount;
            stickersEndRow = rowCount + stickerSets.size();
            rowCount += stickerSets.size();
            stickersShadowRow = rowCount++;
        } else {
            headerRow = -1;
            stickersStartRow = -1;
            stickersEndRow = -1;
            stickersShadowRow = -1;
        }
        if (nameContainer != null) {
            nameContainer.invalidate();
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
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        boolean animations = preferences.getBoolean("view_animations", true);
        if (!animations) {
            usernameTextView.requestFocus();
            AndroidUtilities.showKeyboard(usernameTextView);
        }
    }

    private void showEditDoneProgress(final boolean show) {
        if (doneItem == null) {
            return;
        }
        if (doneItemAnimation != null) {
            doneItemAnimation.cancel();
        }
        doneItemAnimation = new AnimatorSet();
        if (show) {
            progressView.setVisibility(View.VISIBLE);
            doneItem.setEnabled(false);
            doneItemAnimation.playTogether(
                    ObjectAnimator.ofFloat(doneItem.getContentView(), "scaleX", 0.1f),
                    ObjectAnimator.ofFloat(doneItem.getContentView(), "scaleY", 0.1f),
                    ObjectAnimator.ofFloat(doneItem.getContentView(), "alpha", 0.0f),
                    ObjectAnimator.ofFloat(progressView, "scaleX", 1.0f),
                    ObjectAnimator.ofFloat(progressView, "scaleY", 1.0f),
                    ObjectAnimator.ofFloat(progressView, "alpha", 1.0f));
        } else {
            doneItem.getContentView().setVisibility(View.VISIBLE);
            doneItem.setEnabled(true);
            doneItemAnimation.playTogether(
                    ObjectAnimator.ofFloat(progressView, "scaleX", 0.1f),
                    ObjectAnimator.ofFloat(progressView, "scaleY", 0.1f),
                    ObjectAnimator.ofFloat(progressView, "alpha", 0.0f),
                    ObjectAnimator.ofFloat(doneItem.getContentView(), "scaleX", 1.0f),
                    ObjectAnimator.ofFloat(doneItem.getContentView(), "scaleY", 1.0f),
                    ObjectAnimator.ofFloat(doneItem.getContentView(), "alpha", 1.0f));

        }
        doneItemAnimation.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (doneItemAnimation != null && doneItemAnimation.equals(animation)) {
                    if (!show) {
                        progressView.setVisibility(View.INVISIBLE);
                    } else {
                        doneItem.getContentView().setVisibility(View.INVISIBLE);
                    }
                }
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                if (doneItemAnimation != null && doneItemAnimation.equals(animation)) {
                    doneItemAnimation = null;
                }
            }
        });
        doneItemAnimation.setDuration(150);
        doneItemAnimation.start();
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {

        private Context mContext;

        public ListAdapter(Context context) {
            mContext = context;
        }

        @Override
        public int getItemCount() {
            return rowCount;
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            switch (holder.getItemViewType()) {
                case 0: {
                    ArrayList<TLRPC.TL_messages_stickerSet> arrayList = MediaDataController.getInstance(currentAccount).getStickerSets(MediaDataController.TYPE_IMAGE);
                    int row = position - stickersStartRow;
                    StickerSetCell cell = (StickerSetCell) holder.itemView;
                    TLRPC.TL_messages_stickerSet set = arrayList.get(row);
                    cell.setStickersSet(arrayList.get(row), row != arrayList.size() - 1);
                    long id;
                    if (selectedStickerSet != null) {
                        id = selectedStickerSet.set.id;
                    } else if (info != null && info.stickerset != null) {
                        id = info.stickerset.id;
                    } else {
                        id = 0;
                    }
                    cell.setChecked(set.set.id == id);
                    break;
                }
                case 1: {
                    if (position == infoRow) {
                        String text = LocaleController.getString("ChooseStickerSetMy", R.string.ChooseStickerSetMy);
                        String botName = "@stickers";
                        int index = text.indexOf(botName);
                        if (index != -1) {
                            try {
                                SpannableStringBuilder stringBuilder = new SpannableStringBuilder(text);
                                URLSpanNoUnderline spanNoUnderline = new URLSpanNoUnderline("@stickers") {
                                    @Override
                                    public void onClick(View widget) {
                                        MessagesController.getInstance(currentAccount).openByUserName("stickers", GroupStickersActivity.this, 1);
                                    }
                                };
                                stringBuilder.setSpan(spanNoUnderline, index, index + botName.length(), Spanned.SPAN_INCLUSIVE_INCLUSIVE);
                                ((TextInfoPrivacyCell) holder.itemView).setText(stringBuilder);
                            } catch (Exception e) {
                                FileLog.e(e);
                                ((TextInfoPrivacyCell) holder.itemView).setText(text);
                            }
                        } else {
                            ((TextInfoPrivacyCell) holder.itemView).setText(text);
                        }
                    }
                    break;
                }
                case 4: {
                    ((HeaderCell) holder.itemView).setText(LocaleController.getString("ChooseFromYourStickers", R.string.ChooseFromYourStickers));
                    break;
                }
                case 5: {
                    StickerSetCell cell = (StickerSetCell) holder.itemView;
                    if (selectedStickerSet != null) {
                        cell.setStickersSet(selectedStickerSet, false);
                    } else {
                        if (searching) {
                            cell.setText(LocaleController.getString("Loading", R.string.Loading), null, 0, false);
                        } else {
                            cell.setText(LocaleController.getString("ChooseStickerSetNotFound", R.string.ChooseStickerSetNotFound), LocaleController.getString("ChooseStickerSetNotFoundInfo", R.string.ChooseStickerSetNotFoundInfo), R.drawable.ic_smiles2_sad, false);
                        }
                    }
                    break;
                }
            }
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            int type = holder.getItemViewType();
            return type == 0 || type == 2 || type == 5;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case 0:
                case 5:
                    view = new StickerSetCell(mContext, viewType == 0 ? 3 : 2);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 1:
                    view = new TextInfoPrivacyCell(mContext);
                    view.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                    break;
                case 2:
                    view = nameContainer;
                    break;
                case 3:
                    view = new ShadowSectionCell(mContext);
                    view.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                    break;
                case 4:
                default:
                    view = new HeaderCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
            }
            view.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
            return new RecyclerListView.Holder(view);
        }

        @Override
        public int getItemViewType(int i) {
            if (i >= stickersStartRow && i < stickersEndRow) {
                return 0;
            } else if (i == infoRow) {
                return 1;
            } else if (i == nameRow) {
                return 2;
            } else if (i == stickersShadowRow) {
                return 3;
            } else if (i == headerRow) {
                return 4;
            } else if (i == selectedStickerRow) {
                return 5;
            }
            return 0;
        }
    }

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        ArrayList<ThemeDescription> themeDescriptions = new ArrayList<>();

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_CELLBACKGROUNDCOLOR, new Class[]{StickerSetCell.class, TextSettingsCell.class}, null, null, null, Theme.key_windowBackgroundWhite));
        themeDescriptions.add(new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundGray));

        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_actionBarDefault));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_LISTGLOWCOLOR, null, null, null, null, Theme.key_actionBarDefault));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_ITEMSCOLOR, null, null, null, null, Theme.key_actionBarDefaultIcon));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_TITLECOLOR, null, null, null, null, Theme.key_actionBarDefaultTitle));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_actionBarDefaultSelector));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_SELECTOR, null, null, null, null, Theme.key_listSelector));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{View.class}, Theme.dividerPaint, null, null, Theme.key_divider));

        themeDescriptions.add(new ThemeDescription(editText, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(editText, ThemeDescription.FLAG_HINTTEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteHintText));
        themeDescriptions.add(new ThemeDescription(usernameTextView, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(usernameTextView, ThemeDescription.FLAG_HINTTEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteHintText));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{TextInfoPrivacyCell.class}, null, null, null, Theme.key_windowBackgroundGrayShadow));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextInfoPrivacyCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText4));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_LINKCOLOR, new Class[]{TextInfoPrivacyCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteLinkText));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextSettingsCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextSettingsCell.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteValueText));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{ShadowSectionCell.class}, null, null, null, Theme.key_windowBackgroundGrayShadow));

        themeDescriptions.add(new ThemeDescription(nameContainer, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundWhite));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{StickerSetCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{StickerSetCell.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText2));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_USEBACKGROUNDDRAWABLE | ThemeDescription.FLAG_DRAWABLESELECTEDSTATE, new Class[]{StickerSetCell.class}, new String[]{"optionsButton"}, null, null, null, Theme.key_stickers_menuSelector));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{StickerSetCell.class}, new String[]{"optionsButton"}, null, null, null, Theme.key_stickers_menu));

        return themeDescriptions;
    }
}
