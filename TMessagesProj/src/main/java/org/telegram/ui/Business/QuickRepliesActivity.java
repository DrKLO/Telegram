package org.telegram.ui.Business;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.dpf2;
import static org.telegram.messenger.LocaleController.formatPluralString;
import static org.telegram.messenger.LocaleController.formatString;
import static org.telegram.messenger.LocaleController.getString;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.text.style.ReplacementSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.ImageLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.AlertDialogDecor;
import org.telegram.ui.ActionBar.BackDrawable;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.AnimatedColor;
import org.telegram.ui.Components.AnimatedTextView;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.CheckBox2;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.NumberTextView;
import org.telegram.ui.Components.SizeNotifierFrameLayout;
import org.telegram.ui.Components.Text;
import org.telegram.ui.Components.TypefaceSpan;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;
import org.telegram.ui.Components.spoilers.SpoilersTextView;
import org.telegram.ui.LaunchActivity;

import java.util.ArrayList;

public class QuickRepliesActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    private UniversalRecyclerView listView;

    public final ArrayList<Integer> selected = new ArrayList<>();

    private NumberTextView countText;
    private ActionBarMenuItem editItem;
    private ActionBarMenuItem deleteItem;

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonDrawable(new BackDrawable(false));
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(getString(R.string.BusinessReplies));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    if (selected.isEmpty()) {
                        finishFragment();
                    } else {
                        clearSelection();
                    }
                } else if (id == 1) {
                    if (selected.size() != 1) return;
//                    selected.get(0);
                    final int replyId = selected.get(0);
                    QuickRepliesController.QuickReply quickReply = QuickRepliesController.getInstance(currentAccount).findReply(replyId);
                    if (quickReply == null) return;
                    openRenameReplyAlert(getContext(), currentAccount, null, quickReply, resourceProvider, false, name -> {
                        clearSelection();
                        QuickRepliesController.getInstance(currentAccount).renameReply(replyId, name);
                    });
                } else if (id == 2) {
                    showDialog(
                        new AlertDialog.Builder(getContext(), getResourceProvider())
                            .setTitle(formatPluralString("BusinessRepliesDeleteTitle", selected.size()))
                            .setMessage(formatPluralString("BusinessRepliesDeleteMessage", selected.size()))
                            .setPositiveButton(getString(R.string.Remove), (di, w) -> {
                                QuickRepliesController.getInstance(currentAccount).deleteReplies(selected);
                                clearSelection();
                            })
                            .setNegativeButton(getString(R.string.Cancel), null)
                            .create()
                    );
                }
            }
        });

        ActionBarMenu actionModeMenu = actionBar.createActionMode();
        countText = new NumberTextView(getContext());
        countText.setTextSize(18);
        countText.setTypeface(AndroidUtilities.bold());
        countText.setTextColor(Theme.getColor(Theme.key_actionBarActionModeDefaultIcon));
        actionModeMenu.addView(countText, LayoutHelper.createLinear(0, LayoutHelper.MATCH_PARENT, 1.0f, 72, 0, 0, 0));
        countText.setOnTouchListener((v, event) -> true);
        editItem = actionModeMenu.addItem(1, R.drawable.msg_edit);
        editItem.setContentDescription(LocaleController.getString(R.string.Edit));
        deleteItem = actionModeMenu.addItem(2, R.drawable.msg_delete);
        deleteItem.setContentDescription(LocaleController.getString(R.string.Delete));

        FrameLayout contentView = new SizeNotifierFrameLayout(context) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(
                    MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(heightMeasureSpec), MeasureSpec.EXACTLY)
                );
            }
        };
        contentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));

        listView = new UniversalRecyclerView(this, this::fillItems, this::onClick, this::onLongClick);
        listView.listenReorder(this::whenReordered);
        contentView.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        return fragmentView = contentView;
    }

    private final static int BUTTON_ADD = 1;
    private int repliesOrderId;

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asTopView(getString(R.string.BusinessRepliesInfo), "RestrictedEmoji", "📝"));
        adapter.whiteSectionStart();
        if (QuickRepliesController.getInstance(currentAccount).canAddNew()) {
            items.add(UItem.asButton(BUTTON_ADD, R.drawable.msg_viewintopic, getString(R.string.BusinessRepliesAdd)).accent());
        }
//        for (QuickRepliesController.QuickReply reply : QuickRepliesController.getInstance(currentAccount).localReplies) {
//            items.add(UItem.asQuickReply(reply).setChecked(false));
//        }
        repliesOrderId = adapter.reorderSectionStart();
        for (QuickRepliesController.QuickReply reply : QuickRepliesController.getInstance(currentAccount).replies) {
            items.add(UItem.asQuickReply(reply).setChecked(selected.contains(reply.id)));
        }
        adapter.reorderSectionEnd();
        adapter.whiteSectionEnd();
        items.add(UItem.asShadow(getString(R.string.BusinessRepliesAddInfo)));
    }

    private void whenReordered(int id, ArrayList<UItem> items) {
        if (id == repliesOrderId) {
            for (int i = 0; i < items.size(); ++i) {
                if (items.get(i).object instanceof QuickRepliesController.QuickReply) {
                    QuickRepliesController.QuickReply quickReply = ((QuickRepliesController.QuickReply) items.get(i).object);
                    quickReply.order = i;
                }
            }
            QuickRepliesController.getInstance(currentAccount).reorder();
        }
    }

    private void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == BUTTON_ADD) {
            openRenameReplyAlert(getContext(), currentAccount, null, null, getResourceProvider(), false, name -> {
                Bundle args = new Bundle();
                args.putInt("chatMode", ChatActivity.MODE_QUICK_REPLIES);
                args.putLong("user_id", getUserConfig().getClientUserId());
                args.putString("quick_reply", name);
                ChatActivity chatActivity = new ChatActivity(args);
                chatActivity.forceEmptyHistory();
                presentFragment(chatActivity);
            });
        } else if (item.viewType == UniversalAdapter.VIEW_TYPE_QUICK_REPLY && item.object instanceof QuickRepliesController.QuickReply) {
            if (!selected.isEmpty()) {
                updateSelect(item, view);
                return;
            }
            QuickRepliesController.QuickReply reply = (QuickRepliesController.QuickReply) item.object;
            if (reply.local) {
                return;
            }
            Bundle args = new Bundle();
            args.putInt("chatMode", ChatActivity.MODE_QUICK_REPLIES);
            args.putLong("user_id", getUserConfig().getClientUserId());
            args.putString("quick_reply", reply.name);
            ChatActivity chatActivity = new ChatActivity(args);
            chatActivity.setQuickReplyId(reply.id);
            presentFragment(chatActivity);
        }
    }

    private void updateSelect(UItem item, View view) {
        QuickRepliesController.QuickReply quickReply = (QuickRepliesController.QuickReply) item.object;
        QuickReplyView cell = (QuickReplyView) view;
        if (selected.contains(quickReply.id)) {
            selected.remove((Integer) quickReply.id);
        } else {
            selected.add(quickReply.id);
        }
        listView.allowReorder(!selected.isEmpty());
        cell.setChecked(item.checked = selected.contains(quickReply.id), true);
        if (actionBar.isActionModeShowed() == selected.isEmpty()) {
            if (selected.isEmpty()) {
                actionBar.hideActionMode();
            } else {
                actionBar.showActionMode();
            }
        }
        countText.setNumber(Math.max(1, selected.size()), true);
        updateEditItem();
    }

    private boolean shownEditItem = true;
    private void updateEditItem() {
        boolean show = selected.size() == 1;
        if (show) {
            QuickRepliesController.QuickReply reply = QuickRepliesController.getInstance(currentAccount).findReply(selected.get(0));
            show = reply != null && !reply.isSpecial();
        }
        if (shownEditItem != show) {
            shownEditItem = show;
            editItem.animate().alpha(shownEditItem ? 1f : 0f).scaleX(shownEditItem ? 1f : .7f).scaleY(shownEditItem ? 1f : .7f).setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT).setDuration(340).start();
        }
    }

    private void clearSelection() {
        selected.clear();
        AndroidUtilities.forEachViews(listView, view -> {
            if (view instanceof QuickReplyView) {
                ((QuickReplyView) view).setChecked(false, true);
            }
        });
        actionBar.hideActionMode();
        listView.allowReorder(false);
    }

    private boolean onLongClick(UItem item, View view, int position, float x, float y) {
        if (item.viewType == UniversalAdapter.VIEW_TYPE_QUICK_REPLY) {
            if (item.object instanceof QuickRepliesController.QuickReply && ((QuickRepliesController.QuickReply) item.object).local) {
                return false;
            }
            updateSelect(item, view);
            return true;
        }
        return false;
    }

    private static AlertDialog currentDialog;
    public static void openRenameReplyAlert(Context context, int currentAccount, String currentName, QuickRepliesController.QuickReply currentReply, Theme.ResourcesProvider resourcesProvider, boolean forceNotAdaptive, Utilities.Callback<String> whenDone) {
        BaseFragment fragment = LaunchActivity.getLastFragment();
        Activity activity = AndroidUtilities.findActivity(context);
        View currentFocus = activity != null ? activity.getCurrentFocus() : null;
        final boolean isKeyboardVisible = fragment != null && fragment.getFragmentView() instanceof SizeNotifierFrameLayout && ((SizeNotifierFrameLayout) fragment.getFragmentView()).measureKeyboardHeight() > dp(20);
        final boolean adaptive = isKeyboardVisible && !forceNotAdaptive;
        AlertDialog[] dialog = new AlertDialog[1];
        AlertDialog.Builder builder;
        if (adaptive) {
            builder = new AlertDialogDecor.Builder(context, resourcesProvider);
        } else {
            builder = new AlertDialog.Builder(context, resourcesProvider);
        }
        builder.setTitle(getString(currentReply == null && currentName == null ? R.string.BusinessRepliesNewTitle : R.string.BusinessRepliesEditTitle));

        final int MAX_NAME_LENGTH = 32;
        EditTextBoldCursor editText = new EditTextBoldCursor(context) {
            AnimatedColor limitColor = new AnimatedColor(this);
            private int limitCount;
            AnimatedTextView.AnimatedTextDrawable limit = new AnimatedTextView.AnimatedTextDrawable(false, true, true); {
                limit.setAnimationProperties(.2f, 0, 160, CubicBezierInterpolator.EASE_OUT_QUINT);
                limit.setTextSize(dp(15.33f));
                limit.setCallback(this);
                limit.setGravity(Gravity.RIGHT);
            }

            @Override
            protected boolean verifyDrawable(@NonNull Drawable who) {
                return who == limit || super.verifyDrawable(who);
            }

            @Override
            protected void onTextChanged(CharSequence text, int start, int lengthBefore, int lengthAfter) {
                super.onTextChanged(text, start, lengthBefore, lengthAfter);

                if (limit != null) {
                    limitCount = MAX_NAME_LENGTH - text.length();
                    limit.cancelAnimation();
                    limit.setText(limitCount > 4 ? "" : "" + limitCount);
                }
            }

            @Override
            protected void dispatchDraw(Canvas canvas) {
                super.dispatchDraw(canvas);

                limit.setTextColor(limitColor.set(Theme.getColor(limitCount < 0 ? Theme.key_text_RedRegular : Theme.key_dialogSearchHint, resourcesProvider)));
                limit.setBounds(getScrollX(), 0, getScrollX() + getWidth(), getHeight());
                limit.draw(canvas);
            }

            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(36), MeasureSpec.EXACTLY));
            }
        };
        MediaDataController.getInstance(currentAccount).fetchNewEmojiKeywords(AndroidUtilities.getCurrentKeyboardLanguage(), true);
        editText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        editText.setText(currentReply == null ? (currentName == null ? "" : currentName) : currentReply.name);
        editText.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider));
        editText.setHintColor(Theme.getColor(Theme.key_groupcreate_hintText, resourcesProvider));
        editText.setHintText(LocaleController.getString(R.string.BusinessRepliesNamePlaceholder));
        editText.setSingleLine(true);
        editText.setFocusable(true);
        editText.setLineColors(Theme.getColor(Theme.key_windowBackgroundWhiteInputField, resourcesProvider), Theme.getColor(Theme.key_windowBackgroundWhiteInputFieldActivated, resourcesProvider), Theme.getColor(Theme.key_text_RedRegular, resourcesProvider));
        editText.setImeOptions(EditorInfo.IME_ACTION_DONE);
        editText.setBackgroundDrawable(null);
        editText.setPadding(0, 0, dp(42), 0);
        editText.setFilters(new InputFilter[]{
            new InputFilter() {
                @Override
                public CharSequence filter(CharSequence source, int start, int end, Spanned dest, int dstart, int dend) {
                    return String.valueOf(source).replaceAll("[^\\d_\\p{L}\\x{200c}\\x{00b7}\\x{0d80}-\\x{0dff}]", "");
                }
            }
        });

        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);

        FrameLayout textContainer = new FrameLayout(context);

        final TextView textView = new TextView(context);
        textView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider));
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        textView.setText(LocaleController.getString(currentReply == null && currentName == null ? R.string.BusinessRepliesNewMessage : R.string.BusinessRepliesEditMessage));
        textContainer.addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.BOTTOM));

        final TextView errorTextView = new TextView(context);
        errorTextView.setTextColor(Theme.getColor(Theme.key_text_RedBold, resourcesProvider));
        errorTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        errorTextView.setText(LocaleController.getString(R.string.BusinessRepliesNameBusy));
        errorTextView.setAlpha(0f);
        textContainer.addView(errorTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.BOTTOM));
        ValueAnimator[] errorAnimator = new ValueAnimator[1];
        final Runnable[] hideError = new Runnable[1];
        Utilities.Callback<Boolean> updateError = show -> {
            AndroidUtilities.cancelRunOnUIThread(hideError[0]);
            if (errorAnimator[0] != null) {
                errorAnimator[0].cancel();
            }
            errorAnimator[0] = ValueAnimator.ofFloat(errorTextView.getAlpha(), show ? 1f : 0f);
            errorAnimator[0].addUpdateListener(anm -> {
                errorTextView.setAlpha((float) anm.getAnimatedValue());
                textView.setAlpha(1f - (float) anm.getAnimatedValue());
            });
            errorAnimator[0].setDuration(320);
            errorAnimator[0].setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
            errorAnimator[0].start();
            if (show) {
                AndroidUtilities.runOnUIThread(hideError[0], 320 + 5000);
            }
        };
        hideError[0] = () -> updateError.run(false);
        editText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                if (errorTextView.getAlpha() > 0f) {
                    AndroidUtilities.cancelRunOnUIThread(hideError[0]);
                    AndroidUtilities.runOnUIThread(hideError[0]);
                }
            }
        });

        container.addView(textContainer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 24, 5, 24, 12));

        container.addView(editText, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 24, 0, 24, 10));
        builder.setView(container);
        builder.setWidth(dp(292));

        editText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    String text = editText.getText().toString();
                    if (text.length() <= 0 || text.length() > MAX_NAME_LENGTH) {
                        AndroidUtilities.shakeView(editText);
                        return true;
                    }
                    if (QuickRepliesController.getInstance(currentAccount).isNameBusy(text, currentReply == null ? -1 : currentReply.id)) {
                        AndroidUtilities.shakeView(editText);
                        errorTextView.setText(LocaleController.getString(R.string.BusinessRepliesNameBusy));
                        updateError.run(true);
                        return true;
                    }
                    if (whenDone != null) {
                        whenDone.run(text);
                    }
                    if (dialog[0] != null) {
                        dialog[0].dismiss();
                    }
                    if (dialog[0] == currentDialog) {
                        currentDialog = null;
                    }
                    if (currentFocus != null) {
                        currentFocus.requestFocus();
                    }
                    return true;
                }
                return false;
            }
        });
        builder.setPositiveButton(LocaleController.getString(R.string.Done), (dialogInterface, i) -> {
            String text = editText.getText().toString();
            if (text.length() <= 0 || text.length() > MAX_NAME_LENGTH) {
                AndroidUtilities.shakeView(editText);
                updateError.run(false);
                return;
            }
            if (QuickRepliesController.getInstance(currentAccount).isNameBusy(text, currentReply == null ? -1 : currentReply.id)) {
                AndroidUtilities.shakeView(editText);
                errorTextView.setText(LocaleController.getString(R.string.BusinessRepliesNameBusy));
                updateError.run(true);
                return;
            }
            if (whenDone != null) {
                whenDone.run(text);
            }
            dialogInterface.dismiss();
        });
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), (dialogInterface, i) -> {
            dialogInterface.dismiss();
        });
        if (adaptive) {
            dialog[0] = currentDialog = builder.create();
            currentDialog.setOnDismissListener(d -> {
                currentDialog = null;
                if (currentFocus != null) {
                    currentFocus.requestFocus();
                }
            });
            currentDialog.setOnShowListener(d -> {
                editText.requestFocus();
                AndroidUtilities.showKeyboard(editText);
            });
            currentDialog.showDelayed(250);
        } else {
            builder.overrideDismissListener(dismiss -> {
                AndroidUtilities.hideKeyboard(editText);
                AndroidUtilities.runOnUIThread(dismiss, 80);
            });
            dialog[0] = builder.create();
            dialog[0].setOnDismissListener(d -> {
                AndroidUtilities.hideKeyboard(editText);
            });
            dialog[0].setOnShowListener(d -> {
                editText.requestFocus();
                AndroidUtilities.showKeyboard(editText);
            });
            dialog[0].show();
        }
        dialog[0].setDismissDialogByButtons(false);
        editText.setSelection(editText.getText().length());
    }

    @Override
    public boolean onFragmentCreate() {
        getNotificationCenter().addObserver(this, NotificationCenter.quickRepliesUpdated);
        QuickRepliesController.getInstance(currentAccount).load();
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        getNotificationCenter().removeObserver(this, NotificationCenter.quickRepliesUpdated);
        super.onFragmentDestroy();
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.quickRepliesUpdated) {
            if (listView != null && listView.adapter != null) {
                listView.adapter.update(true);
            }
        }
    }

    private static class MoreSpan extends ReplacementSpan {
        private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Text text;
        public MoreSpan(int count) {
            text = new Text(formatPluralString("BusinessRepliesMore", count), 9.33f, AndroidUtilities.bold());
        }
        public static CharSequence of(int count, int[] width) {
            SpannableString ss = new SpannableString("+");
            MoreSpan span = new MoreSpan(count);
            width[0] = span.getSize();
            ss.setSpan(span, 0, ss.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            return ss;
        }

        public int getSize() {
            return (int) (this.text.getCurrentWidth() + dp(10));
        }

        @Override
        public int getSize(@NonNull Paint paint, CharSequence text, int start, int end, @Nullable Paint.FontMetricsInt fm) {
            return getSize();
        }

        @Override
        public void draw(@NonNull Canvas canvas, CharSequence text, int start, int end, float x, int top, int y, int bottom, @NonNull Paint paint) {
            final float h = dpf2(14.66f), cy = (top + bottom) / 2f;
            AndroidUtilities.rectTmp.set(x, cy - h / 2f, x + getSize(), cy + h / 2f);
            backgroundPaint.setColor(Theme.multAlpha(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2), .15f));
            canvas.drawRoundRect(AndroidUtilities.rectTmp, dp(4), dp(4), backgroundPaint);
            this.text.draw(canvas, x + dp(5), (top + bottom) / 2f, Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2), Utilities.clamp(2 * paint.getAlpha() / 255f, 1, 0));
        }
    }

    public static class QuickReplyView extends FrameLayout {

        private final AvatarDrawable avatarDrawable = new AvatarDrawable();
        private final ImageReceiver imageReceiver = new ImageReceiver(this);

        private final TextView textView;
        private final CheckBox2 checkBox;
        private final ImageView orderView;

        private final Theme.ResourcesProvider resourcesProvider;
        private boolean local;

        public QuickReplyView(Context context, boolean reorderable, Theme.ResourcesProvider resourcesProvider) {
            super(context);

            this.resourcesProvider = resourcesProvider;
            setWillNotDraw(false);

            int rightpadding = reorderable ? 42 : 16;

            textView = new SpoilersTextView(context);
            textView.setLines(2);
            textView.setEllipsize(TextUtils.TruncateAt.END);
            textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2, resourcesProvider));
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.FILL_HORIZONTAL, LocaleController.isRTL ? rightpadding : 64, 7, LocaleController.isRTL ? 64 : rightpadding, 0));

            if (reorderable) {
                orderView = new ImageView(context);
                orderView.setScaleType(ImageView.ScaleType.CENTER);
                orderView.setImageResource(R.drawable.list_reorder);
                orderView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_stickers_menu), PorterDuff.Mode.MULTIPLY));
                orderView.setAlpha(0f);
                addView(orderView, LayoutHelper.createFrame(50, 50, Gravity.FILL_VERTICAL | (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT)));
            } else {
                orderView = null;
            }

            checkBox = new CheckBox2(getContext(), 21, resourcesProvider);
            checkBox.setColor(-1, Theme.key_windowBackgroundWhite, Theme.key_checkboxCheck);
            checkBox.setDrawUnchecked(false);
            checkBox.setDrawBackgroundAsArc(3);
            addView(checkBox, LayoutHelper.createFrameRelatively(24, 24, Gravity.START | Gravity.TOP, 27 + 6, 19 + 6, 0, 0));
        }

        public void invalidateEmojis() {
            textView.invalidate();
        }

        public void setChecked(boolean checked, boolean animated) {
            checkBox.setChecked(checked, animated);
        }

        public void setReorder(boolean reorder) {
            orderView.animate().alpha(reorder && !local ? 1f : 0f).start();
        }

        private int[] spanWidth = new int[1];
        private boolean needDivider;
        public void set(QuickRepliesController.QuickReply quickReply, String highlight, boolean divider) {
            local = quickReply != null ? quickReply.local : false;
            SpannableStringBuilder ssb = new SpannableStringBuilder();
            if (highlight != null && highlight.length() > 0 && !highlight.startsWith("/")) {
                highlight = "/" + highlight;
            }
            ssb.append("/").append(quickReply.name);
            ssb.setSpan(new TypefaceSpan(AndroidUtilities.bold()), 0, ssb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            ssb.setSpan(new ForegroundColorSpan(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider)), 0, ssb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            if (highlight != null) {
                ssb.setSpan(new ForegroundColorSpan(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText2, resourcesProvider)), 0, Math.min(highlight.length() <= 0 ? 1 : highlight.length(), ssb.length()), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            if (quickReply.topMessage != null) {
                ssb.append(" ");
                CharSequence messageText = quickReply.topMessage.caption;
                if (TextUtils.isEmpty(messageText)) {
                    messageText = quickReply.topMessage.messageText;
                }
                CharSequence text = new SpannableStringBuilder(messageText);
                text = Emoji.replaceEmoji(text, textView.getPaint().getFontMetricsInt(), false);
                if (quickReply.topMessage.messageOwner != null) {
                    MessageObject.replaceAnimatedEmoji(text, quickReply.topMessage.messageOwner.entities, textView.getPaint().getFontMetricsInt());
                }
                ssb.append(text);
            }
            if (quickReply.getMessagesCount() > 1) {
                ssb.append("  ");

                int lineWidth = AndroidUtilities.displaySize.x - dp(64 + 16);
                CharSequence more = MoreSpan.of(quickReply.getMessagesCount() - 1, spanWidth);
                ssb = new SpannableStringBuilder(TextUtils.ellipsize(ssb, textView.getPaint(), 1.5f * lineWidth - spanWidth[0], TextUtils.TruncateAt.END));
                if (ssb.length() > 0 && ssb.charAt(ssb.length() - 1) == '\u2026') {
                    ssb.append("  ");
                }
                ssb.append(more);
            }
            textView.setText(ssb);

            final int currentAccount = UserConfig.selectedAccount;
            TLRPC.MessageMedia media = MessageObject.getMedia(quickReply.topMessage);
            if (media != null && media.photo != null) {
                TLRPC.PhotoSize currentPhotoObject = FileLoader.getClosestPhotoSizeWithSize(media.photo.sizes, dp(36), true, null, true);
                imageReceiver.setImage(ImageLocation.getForObject(currentPhotoObject, media.photo), "36_36", quickReply.topMessage.strippedThumb, currentPhotoObject == null ? 0 : currentPhotoObject.size, null, quickReply.topMessage, 0);
                imageReceiver.setRoundRadius(dp(4));
            } else if (media != null && media.document != null && (quickReply.topMessage.isVideo() || quickReply.topMessage.isSticker())) {
                ImageLocation location = null;
                long size;
                String filter = "36_36";
                TLRPC.PhotoSize currentPhotoObject = FileLoader.getClosestPhotoSizeWithSize(media.document.thumbs, dp(36), true, null, true);
                if (currentPhotoObject == null) {
                    location = ImageLocation.getForDocument(media.document);
                    size = media.document.size;
                    filter = ImageLoader.AUTOPLAY_FILTER;
                } else {
                    location = ImageLocation.getForObject(currentPhotoObject, media.document);
                    size = currentPhotoObject.size;
                }
                imageReceiver.setImage(location, filter, quickReply.topMessage.strippedThumb, size, null, quickReply.topMessage, 0);
                imageReceiver.setRoundRadius(dp(4));
            } else if (media != null && media.webpage != null && media.webpage.photo != null) {
                TLRPC.PhotoSize currentPhotoObject = FileLoader.getClosestPhotoSizeWithSize(media.webpage.photo.sizes, dp(36), true, null, true);
                imageReceiver.setImage(ImageLocation.getForObject(currentPhotoObject, media.webpage.photo), "36_36", quickReply.topMessage.strippedThumb, currentPhotoObject == null ? 0 : currentPhotoObject.size, null, media.webpage, 0);
                imageReceiver.setRoundRadius(dp(4));
            } else {
                avatarDrawable.setInfo(UserConfig.getInstance(currentAccount).getCurrentUser());
                imageReceiver.setForUserOrChat(UserConfig.getInstance(currentAccount).getCurrentUser(), avatarDrawable);
                imageReceiver.setRoundRadius(dp(36));
            }

            needDivider = divider;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            imageReceiver.setImageCoords(
                LocaleController.isRTL ? getMeasuredWidth() - dp(15 + 36) : dp(15),
                dp(7),
                dp(36), dp(36)
            );
            imageReceiver.draw(canvas);
            super.onDraw(canvas);
            if (needDivider) {
                Paint dividerPaint = Theme.getThemePaint(Theme.key_paint_divider, resourcesProvider);
                if (dividerPaint == null) dividerPaint = Theme.dividerPaint;
                canvas.drawRect(dp(LocaleController.isRTL ? 0 : 64), getMeasuredHeight() - 1, getWidth() - dp(LocaleController.isRTL ? 64 : 0), getMeasuredHeight(), dividerPaint);
            }
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(
                MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(dp(50) + (needDivider ? 1 : 0), MeasureSpec.EXACTLY)
            );
        }
    }

    public static class LargeQuickReplyView extends FrameLayout {

        private final AvatarDrawable avatarDrawable = new AvatarDrawable();
        private final ImageReceiver imageReceiver = new ImageReceiver(this);

        private final TextView titleView;
        private final TextView textView;
        private final CheckBox2 checkBox;

        private final Path arrowPath = new Path();
        private final Paint arrowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        private final Theme.ResourcesProvider resourcesProvider;
        public LargeQuickReplyView(Context context, Theme.ResourcesProvider resourcesProvider) {
            super(context);

            this.resourcesProvider = resourcesProvider;
            setWillNotDraw(false);

            titleView = new TextView(context);
            titleView.setSingleLine();
            titleView.setEllipsize(TextUtils.TruncateAt.END);
            titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
            titleView.setTypeface(AndroidUtilities.bold());
            titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            addView(titleView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.FILL_HORIZONTAL, LocaleController.isRTL ? 40 : 78, 10.33f, LocaleController.isRTL ? 78 : 40, 0));

            textView = new TextView(context);
            textView.setLines(2);
            textView.setEllipsize(TextUtils.TruncateAt.END);
            textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2, resourcesProvider));
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.FILL_HORIZONTAL, LocaleController.isRTL ? 40 : 78, 32, LocaleController.isRTL ? 78 : 40, 0));

            checkBox = new CheckBox2(getContext(), 21, resourcesProvider);
            checkBox.setColor(-1, Theme.key_windowBackgroundWhite, Theme.key_checkboxCheck);
            checkBox.setDrawUnchecked(false);
            checkBox.setDrawBackgroundAsArc(3);
            addView(checkBox, LayoutHelper.createFrameRelatively(24, 24, Gravity.START | Gravity.TOP, 27 + 6, 19 + 6, 0, 0));
        }

        public void invalidateEmojis() {
            textView.invalidate();
        }

        public void setChecked(boolean checked, boolean animated) {
            checkBox.setChecked(checked, animated);
        }

        private int[] spanWidth = new int[1];
        private boolean needDivider;
        public void set(QuickRepliesController.QuickReply quickReply, boolean divider) {
            final int currentAccount = UserConfig.selectedAccount;

            titleView.setText(MessagesController.getInstance(currentAccount).getPeerName(UserConfig.getInstance(currentAccount).getClientUserId()));

            SpannableStringBuilder ssb = new SpannableStringBuilder();
            if (quickReply.topMessage != null) {
                ssb.append(Emoji.replaceEmoji(quickReply.topMessage.messageText, textView.getPaint().getFontMetricsInt(), false));
            }
            if (quickReply.getMessagesCount() > 1) {
                ssb.append("  ");

                int lineWidth = AndroidUtilities.displaySize.x - dp(64 + 16);
                CharSequence more = MoreSpan.of(quickReply.getMessagesCount() - 1, spanWidth);
                ssb = new SpannableStringBuilder(TextUtils.ellipsize(ssb, textView.getPaint(), 1.5f * lineWidth - spanWidth[0], TextUtils.TruncateAt.END));
                if (ssb.length() > 0 && ssb.charAt(ssb.length() - 1) == '\u2026') {
                    ssb.append("  ");
                }
                ssb.append(more);
            }
            textView.setText(ssb);

            TLRPC.MessageMedia media = MessageObject.getMedia(quickReply.topMessage);
            if (media != null && media.photo != null) {
                TLRPC.PhotoSize currentPhotoObject = FileLoader.getClosestPhotoSizeWithSize(media.photo.sizes, dp(36), true, null, true);
                imageReceiver.setImage(ImageLocation.getForObject(currentPhotoObject, media.photo), "36_36", quickReply.topMessage.strippedThumb, currentPhotoObject == null ? 0 : currentPhotoObject.size, null, quickReply.topMessage, 0);
                imageReceiver.setRoundRadius(dp(6));
            } else if (media != null && media.document != null) {
                ImageLocation location = null;
                long size;
                String filter = "36_36";
                TLRPC.PhotoSize currentPhotoObject = FileLoader.getClosestPhotoSizeWithSize(media.document.thumbs, dp(36), true, null, true);
                if (currentPhotoObject == null) {
                    location = ImageLocation.getForDocument(media.document);
                    size = media.document.size;
                    filter = ImageLoader.AUTOPLAY_FILTER;
                } else {
                    location = ImageLocation.getForObject(currentPhotoObject, media.document);
                    size = currentPhotoObject.size;
                }
                imageReceiver.setImage(location, filter, quickReply.topMessage.strippedThumb, size, null, quickReply.topMessage, 0);
                imageReceiver.setRoundRadius(dp(6));
            } else {
                avatarDrawable.setInfo(UserConfig.getInstance(currentAccount).getCurrentUser());
                imageReceiver.setForUserOrChat(UserConfig.getInstance(currentAccount).getCurrentUser(), avatarDrawable);
                imageReceiver.setRoundRadius(dp(56));
            }

            needDivider = divider;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            imageReceiver.setImageCoords(
                    LocaleController.isRTL ? getMeasuredWidth() - dp(9 + 56) : dp(9),
                    dp(11.33f),
                    dp(56), dp(56)
            );
            imageReceiver.draw(canvas);
            super.onDraw(canvas);
            canvas.drawPath(arrowPath, arrowPaint);
            if (needDivider) {
                Paint dividerPaint = Theme.getThemePaint(Theme.key_paint_divider, resourcesProvider);
                if (dividerPaint == null) dividerPaint = Theme.dividerPaint;
                canvas.drawRect(dp(LocaleController.isRTL ? 0 : 78), getMeasuredHeight() - 1, getWidth() - dp(LocaleController.isRTL ? 78 : 0), getMeasuredHeight(), dividerPaint);
            }
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(
                MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(dp(78) + (needDivider ? 1 : 0), MeasureSpec.EXACTLY)
            );
            arrowPaint.setStyle(Paint.Style.STROKE);
            arrowPaint.setStrokeCap(Paint.Cap.ROUND);
            arrowPaint.setStrokeJoin(Paint.Join.ROUND);
            arrowPaint.setStrokeWidth(dpf2(1.66f));
            arrowPaint.setColor(Theme.multAlpha(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2, resourcesProvider), .85f));
            arrowPath.rewind();
            final float cy = getMeasuredHeight() / 2f, cx = LocaleController.isRTL ? dpf2(24.33f + 5.33f) : getMeasuredWidth() - dpf2(24.33f);
            arrowPath.moveTo(cx, cy - dpf2(5.66f));
            arrowPath.lineTo(cx + (LocaleController.isRTL ? -1 : 1) * dpf2(5.33f), cy);
            arrowPath.lineTo(cx, cy + dpf2(5.66f));
        }
    }
}
