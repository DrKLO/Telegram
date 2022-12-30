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
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.text.Editable;
import android.text.InputType;
import android.text.Selection;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.text.style.ForegroundColorSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BotWebViewVibrationEffect;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.browser.Browser;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.AnimatedFloat;
import org.telegram.ui.Components.AnimatedTextView;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.CircularProgressDrawable;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.LinkSpanDrawable;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.TypefaceSpan;

import java.util.ArrayList;
import java.util.List;

public class ChangeUsernameActivity extends BaseFragment {

    private View doneButton;

    private RecyclerListView listView;
    private LinearLayoutManager layoutManager;
    private Adapter adapter;
    private ItemTouchHelper itemTouchHelper;

    private boolean needReorder;

    private int checkReqId;
    private String lastCheckName;
    private Runnable checkRunnable;
    private boolean lastNameAvailable;
    private boolean ignoreCheck;
    private CharSequence infoText;

    private String username = "";
    private ArrayList<TLRPC.TL_username> notEditableUsernames = new ArrayList<>();
    private ArrayList<TLRPC.TL_username> usernames = new ArrayList<>();
    private ArrayList<String> loadingUsernames = new ArrayList<>();

    private final static int done_button = 1;

    public class LinkSpan extends ClickableSpan {

        private String url;

        public LinkSpan(String value) {
            url = value;
        }

        @Override
        public void updateDrawState(TextPaint ds) {
            super.updateDrawState(ds);
            ds.setUnderlineText(false);
        }

        @Override
        public void onClick(View widget) {
            try {
                android.content.ClipboardManager clipboard = (android.content.ClipboardManager) ApplicationLoader.applicationContext.getSystemService(Context.CLIPBOARD_SERVICE);
                android.content.ClipData clip = android.content.ClipData.newPlainText("label", url);
                clipboard.setPrimaryClip(clip);
                if (BulletinFactory.canShowBulletin(ChangeUsernameActivity.this)) {
                    BulletinFactory.createCopyLinkBulletin(ChangeUsernameActivity.this).show();
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
    }
    private static class LinkMovementMethodMy extends LinkMovementMethod {
        @Override
        public boolean onTouchEvent(TextView widget, Spannable buffer, MotionEvent event) {
            try {
                boolean result = super.onTouchEvent(widget, buffer, event);
                if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                    Selection.removeSelection(buffer);
                }
                return result;
            } catch (Exception e) {
                FileLog.e(e);
            }
            return false;
        }
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(LocaleController.getString("Username", R.string.Username));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                } else if (id == done_button) {
                    sendReorder();
                    saveName();
                }
            }
        });

        ActionBarMenu menu = actionBar.createMenu();
        doneButton = menu.addItemWithWidth(done_button, R.drawable.ic_ab_done, AndroidUtilities.dp(56), LocaleController.getString("Done", R.string.Done));

        TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(UserConfig.getInstance(currentAccount).getClientUserId());
        if (user == null) {
            user = UserConfig.getInstance(currentAccount).getCurrentUser();
        }

        if (user != null) {
            username = null;
            if (user.usernames != null) {
                for (int i = 0; i < user.usernames.size(); ++i) {
                    TLRPC.TL_username u = user.usernames.get(i);
                    if (u != null && u.editable) {
                        username = u.username;
                        break;
                    }
                }
            }
            if (username == null && user.username != null) {
                username = user.username;
            }
            if (username == null) {
                username = "";
            }

            notEditableUsernames.clear();
            usernames.clear();
            for (int i = 0; i < user.usernames.size(); ++i) {
                if (user.usernames.get(i).active)
                    usernames.add(user.usernames.get(i));
            }
            for (int i = 0; i < user.usernames.size(); ++i) {
                if (!user.usernames.get(i).active)
                    usernames.add(user.usernames.get(i));
            }
//            for (int i = 0; i < usernames.size(); ++i) {
//                if (usernames.get(i) == null ||
//                    username != null && username.equals(usernames.get(i).username) ||
//                    usernames.get(i).editable) {
//                    notEditableUsernames.add(usernames.remove(i--));
//                }
//            }
        }

        fragmentView = new FrameLayout(context);
        listView = new RecyclerListView(context) {

            private Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            @Override
            protected void dispatchDraw(Canvas canvas) {
                int fromIndex = 4, toIndex = 4 + usernames.size() - 1;

                int top = Integer.MAX_VALUE;
                int bottom = Integer.MIN_VALUE;

                for (int i = 0; i < getChildCount(); ++i) {
                    View child = getChildAt(i);
                    if (child == null) {
                        continue;
                    }
                    int position = getChildAdapterPosition(child);
                    if (position >= fromIndex && position <= toIndex) {
                        top = Math.min(child.getTop(), top);
                        bottom = Math.max(child.getBottom(), bottom);
                    }
                }

                if (top < bottom) {
                    backgroundPaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider));
                    canvas.drawRect(0, top, getWidth(), bottom, backgroundPaint);
                }

                super.dispatchDraw(canvas);
            }
        };

        fragmentView.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundGray));
        listView.setLayoutManager(layoutManager = new LinearLayoutManager(context));
        listView.setAdapter(adapter = new Adapter());
        listView.setSelectorDrawableColor(getThemedColor(Theme.key_listSelector));
        itemTouchHelper = new ItemTouchHelper(new TouchHelperCallback());
        itemTouchHelper.attachToRecyclerView(listView);
        ((FrameLayout) fragmentView).addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        fragmentView.setOnTouchListener((v, event) -> true);

        listView.setOnItemClickListener(new RecyclerListView.OnItemClickListener() {
            @Override
            public void onItemClick(View view, int position) {
                if (view instanceof UsernameCell) {
                    TLRPC.TL_username username = ((UsernameCell) view).currentUsername;
                    if (username == null || ((UsernameCell) view).loading) {
                        return;
                    }
                    if (username.editable) {
                        listView.smoothScrollToPosition(0);
                        focusUsernameField(true);
                        return;
                    }
                    new AlertDialog.Builder(getContext(), getResourceProvider())
                        .setTitle(username.active ? LocaleController.getString("UsernameDeactivateLink", R.string.UsernameDeactivateLink) : LocaleController.getString("UsernameActivateLink", R.string.UsernameActivateLink))
                        .setMessage(username.active ? LocaleController.getString("UsernameDeactivateLinkProfileMessage", R.string.UsernameDeactivateLinkProfileMessage) : LocaleController.getString("UsernameActivateLinkProfileMessage", R.string.UsernameActivateLinkProfileMessage))
                        .setPositiveButton(username.active ? LocaleController.getString("Hide", R.string.Hide) : LocaleController.getString("Show", R.string.Show), (di, e) -> {

                            TLRPC.TL_account_toggleUsername req = new TLRPC.TL_account_toggleUsername();
                            req.username = username.username;
                            final boolean wasActive = username.active;
                            req.active = !username.active;
                            getConnectionsManager().sendRequest(req, (res, err) -> {
                                AndroidUtilities.runOnUIThread(() -> {
                                    loadingUsernames.remove(req.username);
                                    if (res instanceof TLRPC.TL_boolTrue) {
                                        toggleUsername(position, req.active);
                                    } else if (err != null && "USERNAMES_ACTIVE_TOO_MUCH".equals(err.text)) {
                                        username.active = req.active;
                                        toggleUsername(position, username.active);
                                        new AlertDialog.Builder(getContext(), getResourceProvider())
                                            .setTitle(LocaleController.getString("UsernameActivateErrorTitle", R.string.UsernameActivateErrorTitle))
                                            .setMessage(LocaleController.getString("UsernameActivateErrorMessage", R.string.UsernameActivateErrorMessage))
                                            .setPositiveButton(LocaleController.getString("OK", R.string.OK), (d, v) -> {
                                                toggleUsername(username, wasActive, true);
                                            })
                                            .show();
                                    } else {
                                        toggleUsername(username, wasActive, true);
                                    }
                                    TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(UserConfig.getInstance(currentAccount).getClientUserId());
                                    getMessagesController().updateUsernameActiveness(user, username.username, username.active);
                                });
                            });
                            loadingUsernames.add(username.username);
//                            toggleUsername(position, username.active);
                            ((UsernameCell) view).setLoading(true);
//                            updateUser();
                        })
                        .setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), (di, e) -> {
                            di.dismiss();
                        })
                        .show();
                } else if (view instanceof InputCell) {
                    focusUsernameField(true);
                }
            }
        });

        AndroidUtilities.runOnUIThread(() -> {
            if (username == null || username.length() > 0) {
                ignoreCheck = true;
                focusUsernameField(usernames.size() <= 0);
                ignoreCheck = false;
            }
        }, 40);

        return fragmentView;
    }


    public void toggleUsername(TLRPC.TL_username username, boolean newActive) {
        toggleUsername(username, newActive, false);
    }

    public void toggleUsername(TLRPC.TL_username username, boolean newActive, boolean shake) {
        for (int i = 0; i < usernames.size(); ++i) {
            if (usernames.get(i) == username) {
                toggleUsername(4 + i, newActive, shake);
                break;
            }
        }
    }

    public void toggleUsername(int position, boolean newActive) {
        toggleUsername(position, newActive, false);
    }

    public void toggleUsername(int position, boolean newActive, boolean shake) {
        if (position - 4 < 0 || position - 4 >= usernames.size()) {
            return;
        }
        TLRPC.TL_username username = usernames.get(position - 4);
        if (username == null) {
            return;
        }

        int toIndex = -1;
        if (username.active = newActive) {
            int firstInactive = -1;
            for (int i = 0; i < usernames.size(); ++i) {
                if (!usernames.get(i).active) {
                    firstInactive = i;
                    break;
                }
            }
            if (firstInactive >= 0) {
                toIndex = 4 + Math.max(0, firstInactive - 1);
            }
        } else {
            int lastActive = -1;
            for (int i = 0; i < usernames.size(); ++i) {
                if (usernames.get(i).active) {
                    lastActive = i;
                }
            }
            if (lastActive >= 0) {
                toIndex = 4 + Math.min(usernames.size() - 1, lastActive + 1);
            }
        }

        if (listView != null) {
            for (int i = 0; i < listView.getChildCount(); ++i) {
                View child = listView.getChildAt(i);
                if (listView.getChildAdapterPosition(child) == position) {
                    if (shake) {
                        AndroidUtilities.shakeView(child);
                    }
                    if (child instanceof ChangeUsernameActivity.UsernameCell) {
                        ((ChangeUsernameActivity.UsernameCell) child).setLoading(loadingUsernames.contains(username.username));
                        ((ChangeUsernameActivity.UsernameCell) child).update();
                    }
                    break;
                }
            }
        }

        if (toIndex >= 0 && position != toIndex) {
            adapter.moveElement(position, toIndex);
        }
    }

    private InputCell inputCell;
    private void focusUsernameField(boolean showKeyboard) {
        if (inputCell != null) {
            if (!inputCell.field.isFocused()) {
                inputCell.field.setSelection(inputCell.field.length());
            }
            inputCell.field.requestFocus();
            if (showKeyboard) {
                AndroidUtilities.showKeyboard(inputCell.field);
            }
        }
    }

    private static final int VIEW_TYPE_HEADER = 0;
    private static final int VIEW_TYPE_HELP1 = 1;
    private static final int VIEW_TYPE_HELP2 = 2;
    private static final int VIEW_TYPE_INPUT = 3;
    private static final int VIEW_TYPE_USERNAME = 4;

    private UsernameCell editableUsernameCell;

    private class Adapter extends RecyclerListView.SelectionAdapter {

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            switch (viewType) {
                case VIEW_TYPE_HEADER:
                    HeaderCell headerCell = new HeaderCell(getContext());
                    headerCell.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
                    return new RecyclerListView.Holder(headerCell);
                case VIEW_TYPE_HELP1:
                    return new RecyclerListView.Holder(new UsernameHelpCell(getContext()));
                case VIEW_TYPE_HELP2:
                    return new RecyclerListView.Holder(new TextInfoPrivacyCell(getContext()));
                case VIEW_TYPE_INPUT:
                    return new RecyclerListView.Holder(new InputCell(getContext()));
                case VIEW_TYPE_USERNAME:
                    return new RecyclerListView.Holder(new UsernameCell(getContext(), getResourceProvider()) {
                        {
                            isProfile = true;
                        }
                        @Override
                        protected String getUsernameEditable() {
                            return username;
                        }
                    });
            }
            return null;
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            switch (holder.getItemViewType()) {
                case VIEW_TYPE_INPUT:
                    ignoreCheck = true;
                    (inputCell = (InputCell) holder.itemView).field.setText(username);
                    ignoreCheck = false;
                    break;
                case VIEW_TYPE_HEADER:
                    ((HeaderCell) holder.itemView).setText(position == 0 ? LocaleController.getString("SetUsernameHeader", R.string.SetUsernameHeader) : LocaleController.getString("UsernamesProfileHeader", R.string.UsernamesProfileHeader));
                    break;
                case VIEW_TYPE_USERNAME:
                    TLRPC.TL_username username = usernames.get(position - 4);
                    UsernameCell cell = (UsernameCell) holder.itemView;
                    if (username.editable) {
                        editableUsernameCell = cell;
                    } else if (editableUsernameCell == cell) {
                        editableUsernameCell = null;
                    }
                    cell.set(username, position < getItemCount() - 2, false);
                    break;
                case VIEW_TYPE_HELP1:
                    break;
                case VIEW_TYPE_HELP2:
                    ((TextInfoPrivacyCell) holder.itemView).setText(LocaleController.getString("UsernamesProfileHelp", R.string.UsernamesProfileHelp));
                    ((TextInfoPrivacyCell) holder.itemView).setBackgroundDrawable(Theme.getThemedDrawable(getContext(), R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                    break;
            }
        }

        @Override
        public int getItemCount() {
            return 3 + (usernames.size() > 0 ? 1 + usernames.size() + 1 : 0);
        }

        @Override
        public int getItemViewType(int position) {
            if (position == 0) {
                return VIEW_TYPE_HEADER;
            } else if (position == 1) {
                return VIEW_TYPE_INPUT;
            } else if (position == 2) {
                return VIEW_TYPE_HELP1;
            } else if (position == 3) {
                return VIEW_TYPE_HEADER;
            } else if (position != getItemCount() - 1) {
                return VIEW_TYPE_USERNAME;
            } else {
                return VIEW_TYPE_HELP2;
            }
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return holder.getItemViewType() == VIEW_TYPE_USERNAME;
        }


        public void swapElements(int fromIndex, int toIndex) {
            int index1 = fromIndex - 4;
            int index2 = toIndex - 4;
            if (index1 >= usernames.size() || index2 >= usernames.size()) {
                return;
            }
            if (fromIndex != toIndex) {
                needReorder = true;
            }

            swapListElements(usernames, index1, index2);

            notifyItemMoved(fromIndex, toIndex);

            int end = 4 + usernames.size() - 1;
            if (fromIndex == end || toIndex == end) {
                notifyItemChanged(fromIndex, 3);
                notifyItemChanged(toIndex, 3);
            }
        }

        private void swapListElements(List<TLRPC.TL_username> list, int index1, int index2) {
            TLRPC.TL_username username1 = list.get(index1);
            list.set(index1, list.get(index2));
            list.set(index2, username1);
        }

        public void moveElement(int fromIndex, int toIndex) {
            int index1 = fromIndex - 4;
            int index2 = toIndex - 4;
            if (index1 >= usernames.size() || index2 >= usernames.size()) {
                return;
            }

            TLRPC.TL_username username = usernames.remove(index1);
            usernames.add(index2, username);

            notifyItemMoved(fromIndex, toIndex);

            for (int i = 0; i < usernames.size(); ++i)
                notifyItemChanged(4 + i);
        }
    }

    private void sendReorder() {
        if (!needReorder) {
            return;
        }
        needReorder = false;
        TLRPC.TL_account_reorderUsernames req = new TLRPC.TL_account_reorderUsernames();
        ArrayList<String> usernames = new ArrayList<>();
        for (int i = 0; i < notEditableUsernames.size(); ++i) {
            if (notEditableUsernames.get(i).active)
                usernames.add(notEditableUsernames.get(i).username);
        }
        for (int i = 0; i < this.usernames.size(); ++i) {
            if (this.usernames.get(i).active)
                usernames.add(this.usernames.get(i).username);
        }
        req.order = usernames;
        getConnectionsManager().sendRequest(req, (res, err) -> {
            if (res instanceof TLRPC.TL_boolTrue) {}
        });
        updateUser();
    }

    private void updateUser() {
        ArrayList<TLRPC.TL_username> newUsernames = new ArrayList<>();
        newUsernames.addAll(notEditableUsernames);
        newUsernames.addAll(usernames);

        TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(UserConfig.getInstance(currentAccount).getClientUserId());
        user.usernames = newUsernames;
        MessagesController.getInstance(currentAccount).putUser(user, false, true);
    }

    private UsernameHelpCell helpCell;
    private LinkSpanDrawable.LinksTextView statusTextView;

    private class UsernameHelpCell extends FrameLayout {

        private TextView text1View;
        private LinkSpanDrawable.LinksTextView text2View;

        public UsernameHelpCell(Context context) {
            super(context);

            helpCell = this;

            setPadding(AndroidUtilities.dp(18), AndroidUtilities.dp(10), AndroidUtilities.dp(18), AndroidUtilities.dp(17));
            setBackgroundDrawable(Theme.getThemedDrawable(context, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
            setClipChildren(false);

            text1View = new TextView(context);
            text1View.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            text1View.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText8));
            text1View.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
            text1View.setLinkTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteLinkText));
            text1View.setHighlightColor(Theme.getColor(Theme.key_windowBackgroundWhiteLinkSelection));
            text1View.setPadding(AndroidUtilities.dp(3), 0, AndroidUtilities.dp(3), 0);

            text2View = statusTextView = new LinkSpanDrawable.LinksTextView(context) {
                @Override
                public void setText(CharSequence text, BufferType type) {
                    if (text != null) {
                        SpannableStringBuilder tagsString = AndroidUtilities.replaceTags(text.toString());
                        int index = tagsString.toString().indexOf('\n');
                        if (index >= 0) {
                            tagsString.replace(index, index + 1, " ");
                            tagsString.setSpan(new ForegroundColorSpan(getThemedColor(Theme.key_windowBackgroundWhiteRedText4)), 0, index, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        }
                        TypefaceSpan[] spans = tagsString.getSpans(0, tagsString.length(), TypefaceSpan.class);
                        for (int i = 0; i < spans.length; ++i) {
                            tagsString.setSpan(
                                new ClickableSpan() {
                                    @Override
                                    public void onClick(@NonNull View view) {
                                        Browser.openUrl(getContext(), "https://fragment.com/username/" + username);
                                    }

                                    @Override
                                    public void updateDrawState(@NonNull TextPaint ds) {
                                        super.updateDrawState(ds);
                                        ds.setUnderlineText(false);
                                    }
                                },
                                tagsString.getSpanStart(spans[i]),
                                tagsString.getSpanEnd(spans[i]),
                                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                            );
                            tagsString.removeSpan(spans[i]);
                        }
                        text = tagsString;
                    }
                    super.setText(text, type);
                }
            };
            text2View.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            text2View.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText8));
            text2View.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
            text2View.setLinkTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteLinkText));
            text2View.setHighlightColor(Theme.getColor(Theme.key_windowBackgroundWhiteLinkSelection));
            text2View.setPadding(AndroidUtilities.dp(3), 0, AndroidUtilities.dp(3), 0);

            addView(text1View, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP));
            addView(text2View, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP));

            text1View.setText(AndroidUtilities.replaceTags(LocaleController.getString("UsernameHelp", R.string.UsernameHelp)));
        }

        private Integer height;

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, height == null ? heightMeasureSpec : MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
        }

        private ValueAnimator heightUpdateAnimator;
        private void update() {
            if (text2View.getVisibility() == View.VISIBLE) {
                text2View.measure(
                    MeasureSpec.makeMeasureSpec(getMeasuredWidth() - getPaddingLeft() - getPaddingRight(), MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(9999999, MeasureSpec.AT_MOST)
                );
            }
            if (heightUpdateAnimator != null) {
                heightUpdateAnimator.cancel();
            }
            int fromHeight = height == null ? getMeasuredHeight() : height;
            int newHeight = AndroidUtilities.dp(10 + 17) + text1View.getHeight() + (text2View.getVisibility() == View.VISIBLE && !TextUtils.isEmpty(text2View.getText()) ? text2View.getMeasuredHeight() + AndroidUtilities.dp(8) : 0);
            float fromTranslationY = text1View.getTranslationY();
            float newTranslationY = text2View.getVisibility() == View.VISIBLE && !TextUtils.isEmpty(text2View.getText()) ? text2View.getMeasuredHeight() + AndroidUtilities.dp(8) : 0;
            heightUpdateAnimator = ValueAnimator.ofFloat(0, 1);
            heightUpdateAnimator.addUpdateListener(anm -> {
                final float t = (float) anm.getAnimatedValue();
                text1View.setTranslationY(AndroidUtilities.lerp(fromTranslationY, newTranslationY, t));
                height = AndroidUtilities.lerp(fromHeight, newHeight, t);
                requestLayout();
            });
            heightUpdateAnimator.setDuration(200);
            heightUpdateAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
            heightUpdateAnimator.start();
        }
    }

    private class InputCell extends FrameLayout {
        public EditTextBoldCursor field;
        public TextView tme;

        public InputCell(Context context) {
            super(context);

            LinearLayout content = new LinearLayout(getContext());
            content.setOrientation(LinearLayout.HORIZONTAL);
            field = new EditTextBoldCursor(getContext());
            field.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17);
            field.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText));
            field.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            field.setBackgroundDrawable(null);
//            field.setLineColors(getThemedColor(Theme.key_windowBackgroundWhiteInputField), getThemedColor(Theme.key_windowBackgroundWhiteInputFieldActivated), getThemedColor(Theme.key_windowBackgroundWhiteRedText3));
            field.setMaxLines(1);
            field.setLines(1);
            field.setPadding(0, 0, 0, 0);
            field.setSingleLine(true);
            field.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP);
            field.setInputType(InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_AUTO_CORRECT);
            field.setImeOptions(EditorInfo.IME_ACTION_DONE);
            field.setHint(LocaleController.getString("UsernameLinkPlaceholder", R.string.UsernameLinkPlaceholder));
            field.setCursorColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            field.setCursorSize(AndroidUtilities.dp(19));
            field.setCursorWidth(1.5f);
            field.setOnEditorActionListener((textView, i, keyEvent) -> {
                if (i == EditorInfo.IME_ACTION_DONE && doneButton != null) {
                    doneButton.performClick();
                    return true;
                }
                return false;
            });
            field.setText(username);
            field.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence charSequence, int i, int i2, int i3) {
                    String wasUsername = username;
                    username = charSequence == null ? "" : charSequence.toString();
                    updateUsernameCell(wasUsername);
                }

                @Override
                public void onTextChanged(CharSequence charSequence, int i, int i2, int i3) {
                    String wasUsername = username;
                    username = charSequence == null ? "" : charSequence.toString();
                    updateUsernameCell(wasUsername);
                    if (ignoreCheck) {
                        return;
                    }
                    checkUserName(username, false);
                }

                @Override
                public void afterTextChanged(Editable editable) {
                    if (username.startsWith("@")) {
                        username = username.substring(1);
                    }
                    if (username.length() > 0) {
                        String url = "https://" + MessagesController.getInstance(currentAccount).linkPrefix + "/" + username;
                        String text = LocaleController.formatString("UsernameHelpLink", R.string.UsernameHelpLink, url);
                        int index = text.indexOf(url);
                        SpannableStringBuilder textSpan = new SpannableStringBuilder(text);
                        if (index >= 0) {
                            textSpan.setSpan(new LinkSpan(url), index, index + url.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        }
//                        helpTextView.setText(TextUtils.concat(infoText, "\n\n", textSpan));
                    } else {
//                        helpTextView.setText(infoText);
                    }
                }

                private void updateUsernameCell(String was) {
                    if (editableUsernameCell != null && was != null) {
                        editableUsernameCell.updateUsername(username);
                    }
                }
            });
            tme = new TextView(getContext());
            tme.setMaxLines(1);
            tme.setLines(1);
            tme.setPadding(0, 0, 0, 0);
            tme.setSingleLine(true);
            tme.setText(getMessagesController().linkPrefix + "/");
            tme.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17);
            tme.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            tme.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP);
            tme.setTranslationY(-AndroidUtilities.dp(3));
            content.addView(tme, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, Gravity.CENTER_VERTICAL, 21, 15, 0, 15));
            content.addView(field, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 1, Gravity.CENTER_VERTICAL, 0, 15, 21, 15));
            addView(content, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP));
            setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(
                MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(50), MeasureSpec.EXACTLY)
            );
        }
    }

    private static Paint linkBackgroundActive = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static Paint linkBackgroundInactive = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static Paint dragPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public static class UsernameCell extends FrameLayout {

        public boolean isProfile = false;
        private Theme.ResourcesProvider resourcesProvider;

        private SimpleTextView usernameView;
        private ImageView loadingView;
        private CircularProgressDrawable loadingDrawable;
        private AnimatedTextView activeView;

        private Drawable[] linkDrawables;

        public UsernameCell(Context context, Theme.ResourcesProvider resourcesProvider) {
            super(context);

            this.resourcesProvider = resourcesProvider;

            setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider));

            usernameView = new SimpleTextView(getContext());
            usernameView.setTextSize(16);
            usernameView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
            usernameView.setEllipsizeByGradient(true);
            addView(usernameView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP, 70, 9, 0, 50));

            loadingView = new ImageView(getContext());
            loadingDrawable = new CircularProgressDrawable(AndroidUtilities.dp(7), AndroidUtilities.dp(1.35f), Theme.getColor(Theme.key_windowBackgroundWhiteBlueText, resourcesProvider));
            loadingView.setImageDrawable(loadingDrawable);
            loadingView.setAlpha(0f);
            loadingView.setVisibility(View.VISIBLE);
            loadingDrawable.setBounds(0, 0, AndroidUtilities.dp(14), AndroidUtilities.dp(14));
            addView(loadingView, LayoutHelper.createFrame(14, 14, Gravity.TOP, 70, 23 + 12, 0, 0));

            activeView = new AnimatedTextView(getContext(), false, true, true);
            activeView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2, resourcesProvider));
            activeView.setAnimationProperties(0.4f, 0, 120, CubicBezierInterpolator.EASE_OUT);
            activeView.setTextSize(AndroidUtilities.dp(13));
            addView(activeView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP, 70, 23, 0, 0));

            linkDrawables = new Drawable[] {
                ContextCompat.getDrawable(context, R.drawable.msg_link_1).mutate(),
                ContextCompat.getDrawable(context, R.drawable.msg_link_2).mutate()
            };
            linkDrawables[0].setColorFilter(new PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.MULTIPLY));
            linkDrawables[1].setColorFilter(new PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.MULTIPLY));

            linkBackgroundActive.setColor(Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider));
            linkBackgroundInactive.setColor(Theme.getColor(Theme.key_chats_unreadCounterMuted, resourcesProvider));
        }

        public float loadingFloat;
        public boolean loading;
        public ValueAnimator loadingAnimator;
        public void setLoading(boolean loading) {
            if (this.loading != loading) {
                this.loading = loading;
                if (loadingAnimator != null) {
                    loadingAnimator.cancel();
                }
                loadingView.setVisibility(View.VISIBLE);
                loadingAnimator = ValueAnimator.ofFloat(loadingFloat, loading ? 1 : 0);
                loadingAnimator.addUpdateListener(anm -> {
                    loadingFloat = (float) anm.getAnimatedValue();
                    activeView.setTranslationX(loadingFloat * AndroidUtilities.dp(12 + 4));
                    loadingView.setAlpha(loadingFloat);
                });
                loadingAnimator.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        loadingView.setVisibility(loading ? View.VISIBLE : View.GONE);
                    }
                });
                loadingAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT);
                loadingAnimator.setDuration(200);
                loadingAnimator.start();
            }
        }

        public TLRPC.TL_username currentUsername;
        private boolean useDivider;
        private AnimatedFloat useDividerAlpha = new AnimatedFloat(this, 300, CubicBezierInterpolator.DEFAULT);

        private float activeViewTextColorT;
        private ValueAnimator activeViewTextColorAnimator;

        public boolean active;
        public boolean editable;
        private AnimatedFloat activeFloat = new AnimatedFloat(this, 400, CubicBezierInterpolator.EASE_OUT_QUINT);

        public void set(TLRPC.TL_username username, boolean useDivider, boolean animated) {
            currentUsername = username;
            this.useDivider = useDivider;
            invalidate();
            if (currentUsername == null) {
                active = false;
                editable = false;
                return;
            }

            active = username.active;
            editable = username.editable;
            updateUsername(username.username);
            if (isProfile) {
                activeView.setText(editable ? LocaleController.getString("UsernameProfileLinkEditable", R.string.UsernameProfileLinkEditable) : (active ? LocaleController.getString("UsernameProfileLinkActive", R.string.UsernameProfileLinkActive) : LocaleController.getString("UsernameProfileLinkInactive", R.string.UsernameProfileLinkInactive)), animated, !active);
            } else {
                activeView.setText(editable ? LocaleController.getString("UsernameLinkEditable", R.string.UsernameLinkEditable) : (active ? LocaleController.getString("UsernameLinkActive", R.string.UsernameLinkActive) : LocaleController.getString("UsernameLinkInactive", R.string.UsernameLinkInactive)), animated, !active);
            }
            animateValueTextColor(active || editable, animated);
        }

        protected String getUsernameEditable() {
            return null;
        }

        public void updateUsername(String username) {
            String usernameString = editable ? getUsernameEditable() : username;
            if (TextUtils.isEmpty(usernameString)) {
                SpannableStringBuilder ssb = new SpannableStringBuilder("@");
                SpannableString sb = new SpannableString(LocaleController.getString("UsernameLinkPlaceholder", R.string.UsernameLinkPlaceholder));
                sb.setSpan(new ForegroundColorSpan(Theme.getColor(Theme.key_windowBackgroundWhiteHintText, resourcesProvider)), 0, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                ssb.append(sb);
                usernameView.setText(ssb);
            } else {
                usernameView.setText("@" + usernameString);
            }
        }

        private void animateValueTextColor(boolean active, boolean animated) {
            if (activeViewTextColorAnimator != null) {
                activeViewTextColorAnimator.cancel();
                activeViewTextColorAnimator = null;
            }
            if (animated) {
                activeViewTextColorAnimator = ValueAnimator.ofFloat(activeViewTextColorT, active ? 1f : 0f);
                activeViewTextColorAnimator.addUpdateListener(anm -> {
                    activeViewTextColorT = (float) anm.getAnimatedValue();
                    int color = (
                        ColorUtils.blendARGB(
                            Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2, resourcesProvider),
                            Theme.getColor(Theme.key_windowBackgroundWhiteBlueText, resourcesProvider),
                            activeViewTextColorT
                        )
                    );
                    loadingDrawable.setColor(color);
                    activeView.setTextColor(color);
                });
                activeViewTextColorAnimator.setDuration(120);
                activeViewTextColorAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT);
                activeViewTextColorAnimator.start();
            } else {
                activeViewTextColorT = active ? 1 : 0;
                int color = (
                    ColorUtils.blendARGB(
                        Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2, resourcesProvider),
                        Theme.getColor(Theme.key_windowBackgroundWhiteBlueText, resourcesProvider),
                        activeViewTextColorT
                    )
                );
                loadingDrawable.setColor(color);
                activeView.setTextColor(color);
            }
        }

        public void update() {
            if (currentUsername != null) {
                set(currentUsername, useDivider, true);
            }
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(
                MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(58), MeasureSpec.EXACTLY)
            );
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);

            float activeValue = activeFloat.set(active ? 1f : 0f);
            if (activeValue < 1) {
                canvas.drawCircle(AndroidUtilities.dp(35), AndroidUtilities.dp(29), AndroidUtilities.dp(16), linkBackgroundInactive);

                linkDrawables[1].setAlpha((int) (255 * (1f - activeValue)));
                linkDrawables[1].setBounds(
                    AndroidUtilities.dp(35) -    linkDrawables[1].getIntrinsicWidth() / 2,
                    AndroidUtilities.dp(29) -    linkDrawables[1].getIntrinsicHeight() / 2,
                    AndroidUtilities.dp(35) +   linkDrawables[1].getIntrinsicWidth() / 2,
                    AndroidUtilities.dp(29) + linkDrawables[1].getIntrinsicHeight() / 2
                );
                linkDrawables[1].draw(canvas);
            }
            if (activeValue > 0) {
                linkBackgroundActive.setAlpha((int) (255 * activeValue));
                canvas.drawCircle(AndroidUtilities.dp(35), AndroidUtilities.dp(29), activeValue * AndroidUtilities.dp(16), linkBackgroundActive);

                linkDrawables[0].setAlpha((int) (255 * activeValue));
                linkDrawables[0].setBounds(
                    AndroidUtilities.dp(35) -    linkDrawables[0].getIntrinsicWidth() / 2,
                    AndroidUtilities.dp(29) -    linkDrawables[0].getIntrinsicHeight() / 2,
                    AndroidUtilities.dp(35) +   linkDrawables[0].getIntrinsicWidth() / 2,
                    AndroidUtilities.dp(29) + linkDrawables[0].getIntrinsicHeight() / 2
                );
                linkDrawables[0].draw(canvas);
            }

            float dividerAlpha = useDividerAlpha.set(useDivider ? 1f : 0f);
            if (dividerAlpha > 0) {
                int wasAlpha = Theme.dividerPaint.getAlpha();
                Theme.dividerPaint.setAlpha((int) (wasAlpha * dividerAlpha));
                canvas.drawRect(AndroidUtilities.dp(70), getHeight() - 1, getWidth(), getHeight(), Theme.dividerPaint);
                Theme.dividerPaint.setAlpha(wasAlpha);
            }

            dragPaint.setColor(Theme.getColor(Theme.key_stickers_menu));
            dragPaint.setAlpha((int) (dragPaint.getAlpha() * activeValue));
            AndroidUtilities.rectTmp.set(getWidth() - AndroidUtilities.dp(37), AndroidUtilities.dp(25), getWidth() - AndroidUtilities.dp(21), AndroidUtilities.dp(25 + 2));
            canvas.drawRoundRect(AndroidUtilities.rectTmp, AndroidUtilities.dp(.3f), AndroidUtilities.dp(.3f), dragPaint);

            AndroidUtilities.rectTmp.set(getWidth() - AndroidUtilities.dp(37), AndroidUtilities.dp(25 + 6), getWidth() - AndroidUtilities.dp(21), AndroidUtilities.dp(25 + 2 + 6));
            canvas.drawRoundRect(AndroidUtilities.rectTmp, AndroidUtilities.dp(.3f), AndroidUtilities.dp(.3f), dragPaint);
        }
    }


    public class TouchHelperCallback extends ItemTouchHelper.Callback {

        @Override
        public boolean isLongPressDragEnabled() {
            return true;
        }

        @Override
        public int getMovementFlags(@NonNull RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder) {
            if (viewHolder.getItemViewType() != VIEW_TYPE_USERNAME || !((UsernameCell) viewHolder.itemView).active) {
                return makeMovementFlags(0, 0);
            }
            return makeMovementFlags(ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0);
        }

        @Override
        public boolean onMove(@NonNull RecyclerView recyclerView, RecyclerView.ViewHolder source, RecyclerView.ViewHolder target) {
            if (source.getItemViewType() != target.getItemViewType() ||
                target.itemView instanceof UsernameCell && !((UsernameCell) target.itemView).active) {
                return false;
            }
            adapter.swapElements(source.getAdapterPosition(), target.getAdapterPosition());
            return true;
        }

        @Override
        public void onChildDraw(@NonNull Canvas c, @NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, float dX, float dY, int actionState, boolean isCurrentlyActive) {
            super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
        }

        @Override
        public void onSelectedChanged(RecyclerView.ViewHolder viewHolder, int actionState) {
            if (actionState == ItemTouchHelper.ACTION_STATE_IDLE) {
                sendReorder();
            } else {
                listView.cancelClickRunnables(false);
                viewHolder.itemView.setPressed(true);
            }
            super.onSelectedChanged(viewHolder, actionState);
        }

        @Override
        public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
        }

        @Override
        public void clearView(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
            super.clearView(recyclerView, viewHolder);
            viewHolder.itemView.setPressed(false);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        boolean animations = preferences.getBoolean("view_animations", true);
        if (!animations) {
            focusUsernameField(false);
        }
    }

    private boolean checkUserName(String name, boolean alert) {
        if (name != null && name.startsWith("@")) {
            name = name.substring(1);
        }
        if (statusTextView != null) {
            statusTextView.setVisibility(!TextUtils.isEmpty(name) ? View.VISIBLE : View.GONE);
            if (helpCell != null) {
                helpCell.update();
            }
        }
        if (alert && name.length() == 0) {
            return true;
        }
        if (checkRunnable != null) {
            AndroidUtilities.cancelRunOnUIThread(checkRunnable);
            checkRunnable = null;
            lastCheckName = null;
            if (checkReqId != 0) {
                ConnectionsManager.getInstance(currentAccount).cancelRequest(checkReqId, true);
            }
        }
        lastNameAvailable = false;
        if (name != null) {
            if (name.startsWith("_") || name.endsWith("_")) {
                if (statusTextView != null) {
                    statusTextView.setText(LocaleController.getString("UsernameInvalid", R.string.UsernameInvalid));
                    statusTextView.setTag(Theme.key_windowBackgroundWhiteRedText4);
                    statusTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteRedText4));
                    if (helpCell != null) {
                        helpCell.update();
                    }
                }
                return false;
            }
            for (int a = 0; a < name.length(); a++) {
                char ch = name.charAt(a);
                if (a == 0 && ch >= '0' && ch <= '9') {
                    if (alert) {
                        AlertsCreator.showSimpleAlert(this, LocaleController.getString("UsernameInvalidStartNumber", R.string.UsernameInvalidStartNumber));
                    } else {
                        if (statusTextView != null) {
                            statusTextView.setText(LocaleController.getString("UsernameInvalidStartNumber", R.string.UsernameInvalidStartNumber));
                            statusTextView.setTag(Theme.key_windowBackgroundWhiteRedText4);
                            statusTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteRedText4));
                            if (helpCell != null) {
                                helpCell.update();
                            }
                        }
                    }
                    return false;
                }
                if (!(ch >= '0' && ch <= '9' || ch >= 'a' && ch <= 'z' || ch >= 'A' && ch <= 'Z' || ch == '_')) {
                    if (alert) {
                        AlertsCreator.showSimpleAlert(this, LocaleController.getString("UsernameInvalid", R.string.UsernameInvalid));
                    } else {
                        if (statusTextView != null) {
                            statusTextView.setText(LocaleController.getString("UsernameInvalid", R.string.UsernameInvalid));
                            statusTextView.setTag(Theme.key_windowBackgroundWhiteRedText4);
                            statusTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteRedText4));
                            if (helpCell != null) {
                                helpCell.update();
                            }
                        }
                    }
                    return false;
                }
            }
        }
        if (name == null || name.length() < 4) {
            if (alert) {
                AlertsCreator.showSimpleAlert(this, LocaleController.getString("UsernameInvalidShort", R.string.UsernameInvalidShort));
            } else {
                if (statusTextView != null) {
                    statusTextView.setText(LocaleController.getString("UsernameInvalidShort", R.string.UsernameInvalidShort));
                    statusTextView.setTag(Theme.key_windowBackgroundWhiteRedText4);
                    statusTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteRedText4));
                    if (helpCell != null) {
                        helpCell.update();
                    }
                }
            }
            return false;
        }
        if (name.length() > 32) {
            if (alert) {
                AlertsCreator.showSimpleAlert(this, LocaleController.getString("UsernameInvalidLong", R.string.UsernameInvalidLong));
            } else {
                if (statusTextView != null) {
                    statusTextView.setText(LocaleController.getString("UsernameInvalidLong", R.string.UsernameInvalidLong));
                    statusTextView.setTag(Theme.key_windowBackgroundWhiteRedText4);
                    statusTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteRedText4));
                    if (helpCell != null) {
                        helpCell.update();
                    }
                }
            }
            return false;
        }

        if (!alert) {
            String currentName = UserConfig.getInstance(currentAccount).getCurrentUser().username;
            if (currentName == null) {
                currentName = "";
            }
            if (name.equals(currentName)) {
                if (statusTextView != null) {
                    statusTextView.setText(LocaleController.formatString("UsernameAvailable", R.string.UsernameAvailable, name));
                    statusTextView.setTag(Theme.key_windowBackgroundWhiteGreenText);
                    statusTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGreenText));
                    if (helpCell != null) {
                        helpCell.update();
                    }
                }
                return true;
            }

            if (statusTextView != null) {
                statusTextView.setText(LocaleController.getString("UsernameChecking", R.string.UsernameChecking));
                statusTextView.setTag(Theme.key_windowBackgroundWhiteGrayText8);
                statusTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText8));
                if (helpCell != null) {
                    helpCell.update();
                }
            }
            lastCheckName = name;
            final String nameFinal = name;
            checkRunnable = () -> {
                TLRPC.TL_account_checkUsername req = new TLRPC.TL_account_checkUsername();
                req.username = nameFinal;
                checkReqId = ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                    checkReqId = 0;
                    if (lastCheckName != null && lastCheckName.equals(nameFinal)) {
                        if (error == null && response instanceof TLRPC.TL_boolTrue) {
                            if (statusTextView != null) {
                                statusTextView.setText(LocaleController.formatString("UsernameAvailable", R.string.UsernameAvailable, nameFinal));
                                statusTextView.setTag(Theme.key_windowBackgroundWhiteGreenText);
                                statusTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGreenText));
                                if (helpCell != null) {
                                    helpCell.update();
                                }
                            }
                            lastNameAvailable = true;
                        } else {
                            if (statusTextView != null) {
                                if (error != null && "USERNAME_INVALID".equals(error.text) && req.username.length() == 4) {
                                    statusTextView.setText(LocaleController.getString("UsernameInvalidShort", R.string.UsernameInvalidShort));
                                    statusTextView.setTag(Theme.key_windowBackgroundWhiteRedText4);
                                    statusTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteRedText4));
                                } else if (error != null && "USERNAME_PURCHASE_AVAILABLE".equals(error.text)) {
                                    if (req.username.length() == 4) {
                                        statusTextView.setText(LocaleController.getString("UsernameInvalidShortPurchase", R.string.UsernameInvalidShortPurchase));
                                    } else {
                                        statusTextView.setText(LocaleController.getString("UsernameInUsePurchase", R.string.UsernameInUsePurchase));
                                    }
                                    statusTextView.setTag(Theme.key_windowBackgroundWhiteGrayText8);
                                    statusTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText8));
                                } else {
                                    statusTextView.setText(LocaleController.getString("UsernameInUse", R.string.UsernameInUse));
                                    statusTextView.setTag(Theme.key_windowBackgroundWhiteRedText4);
                                    statusTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteRedText4));
                                }
                                if (helpCell != null) {
                                    helpCell.update();
                                }
                            }
                            lastNameAvailable = false;
                        }
                    }
                }), ConnectionsManager.RequestFlagFailOnServerErrors);
            };
            AndroidUtilities.runOnUIThread(checkRunnable, 300);
        }
        return true;
    }

    private void saveName() {
        if (username.startsWith("@")) {
            username = username.substring(1);
        }
        if (!username.isEmpty() && !checkUserName(username, false)) {
            shakeIfOff();
            return;
        }
        TLRPC.User user = UserConfig.getInstance(currentAccount).getCurrentUser();
        if (getParentActivity() == null || user == null) {
            return;
        }
        String currentName = UserObject.getPublicUsername(user);
        if (currentName == null) {
            currentName = "";
        }
        if (currentName.equals(username)) {
            finishFragment();
            return;
        }

        final AlertDialog progressDialog = new AlertDialog(getParentActivity(), AlertDialog.ALERT_TYPE_SPINNER);

        final TLRPC.TL_account_updateUsername req = new TLRPC.TL_account_updateUsername();
        req.username = username;

        NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.updateInterfaces, MessagesController.UPDATE_MASK_NAME);
        final int reqId = ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {
            if (error == null) {
                final TLRPC.User user1 = (TLRPC.User) response;
                AndroidUtilities.runOnUIThread(() -> {
                    try {
                        progressDialog.dismiss();
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                    ArrayList<TLRPC.User> users = new ArrayList<>();
                    users.add(user1);
                    MessagesController.getInstance(currentAccount).putUsers(users, false);
                    MessagesStorage.getInstance(currentAccount).putUsersAndChats(users, null, false, true);
                    UserConfig.getInstance(currentAccount).saveConfig(true);
                    finishFragment();
                });
            } else if ("USERNAME_NOT_MODIFIED".equals(error.text)) {
                AndroidUtilities.runOnUIThread(() -> {
                    try {
                        progressDialog.dismiss();
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                    finishFragment();
                });
            } else if ("USERNAME_PURCHASE_AVAILABLE".equals(error.text) || "USERNAME_INVALID".equals(error.text)) {
                AndroidUtilities.runOnUIThread(() -> {
                    try {
                        progressDialog.dismiss();
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                    shakeIfOff();
                });
            } else {
                AndroidUtilities.runOnUIThread(() -> {
                    try {
                        progressDialog.dismiss();
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                    AlertsCreator.processError(currentAccount, error, ChangeUsernameActivity.this, req);
                    shakeIfOff();
                });
            }
        }, ConnectionsManager.RequestFlagFailOnServerErrors);
        ConnectionsManager.getInstance(currentAccount).bindRequestToGuid(reqId, classGuid);

        progressDialog.setOnCancelListener(dialog -> ConnectionsManager.getInstance(currentAccount).cancelRequest(reqId, true));
        progressDialog.show();
    }

    @Override
    public void onTransitionAnimationEnd(boolean isOpen, boolean backward) {
        if (isOpen) {
            focusUsernameField(false);
        }
    }

    public void shakeIfOff() {
        if (listView == null) {
            return;
        }
        for (int i = 0; i < listView.getChildCount(); ++i) {
            View child = listView.getChildAt(i);
            if (child instanceof HeaderCell && i == 0) {
                AndroidUtilities.shakeViewSpring(((HeaderCell) child).getTextView());
            } else if (child instanceof UsernameHelpCell) {
                AndroidUtilities.shakeViewSpring(child);
            } else if (child instanceof InputCell) {
                AndroidUtilities.shakeViewSpring(((InputCell) child).field);
                AndroidUtilities.shakeViewSpring(((InputCell) child).tme);
            }
        }
        BotWebViewVibrationEffect.APP_ERROR.vibrate();
    }

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        ArrayList<ThemeDescription> themeDescriptions = new ArrayList<>();

        themeDescriptions.add(new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundWhite));

        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_actionBarDefault));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_ITEMSCOLOR, null, null, null, null, Theme.key_actionBarDefaultIcon));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_TITLECOLOR, null, null, null, null, Theme.key_actionBarDefaultTitle));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_actionBarDefaultSelector));

//        themeDescriptions.add(new ThemeDescription(firstNameField, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
//        themeDescriptions.add(new ThemeDescription(firstNameField, ThemeDescription.FLAG_HINTTEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteHintText));
//        themeDescriptions.add(new ThemeDescription(firstNameField, ThemeDescription.FLAG_BACKGROUNDFILTER, null, null, null, null, Theme.key_windowBackgroundWhiteInputField));
//        themeDescriptions.add(new ThemeDescription(firstNameField, ThemeDescription.FLAG_BACKGROUNDFILTER | ThemeDescription.FLAG_DRAWABLESELECTEDSTATE, null, null, null, null, Theme.key_windowBackgroundWhiteInputFieldActivated));

//        themeDescriptions.add(new ThemeDescription(helpTextView, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteGrayText8));
//
//        themeDescriptions.add(new ThemeDescription(checkTextView, ThemeDescription.FLAG_TEXTCOLOR | ThemeDescription.FLAG_CHECKTAG, null, null, null, null, Theme.key_windowBackgroundWhiteRedText4));
//        themeDescriptions.add(new ThemeDescription(checkTextView, ThemeDescription.FLAG_TEXTCOLOR | ThemeDescription.FLAG_CHECKTAG, null, null, null, null, Theme.key_windowBackgroundWhiteGreenText));
//        themeDescriptions.add(new ThemeDescription(checkTextView, ThemeDescription.FLAG_TEXTCOLOR | ThemeDescription.FLAG_CHECKTAG, null, null, null, null, Theme.key_windowBackgroundWhiteGrayText8));

        return themeDescriptions;
    }

    @Override
    public void onBecomeFullyVisible() {
        super.onBecomeFullyVisible();
        if (parentLayout != null && parentLayout.getDrawerLayoutContainer() != null) {
            parentLayout.getDrawerLayoutContainer().setBehindKeyboardColor(getThemedColor(Theme.key_windowBackgroundGray));
        }
    }

    @Override
    public void onBecomeFullyHidden() {
        super.onBecomeFullyHidden();
        if (parentLayout != null && parentLayout.getDrawerLayoutContainer() != null) {
            parentLayout.getDrawerLayoutContainer().setBehindKeyboardColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        }
    }
}
