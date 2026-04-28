package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.dpf2;
import static org.telegram.messenger.LocaleController.formatString;
import static org.telegram.messenger.LocaleController.getString;
import static org.telegram.ui.Stars.StarGiftSheet.replaceUnderstood;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.text.Editable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.text.style.ReplacementSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.inputmethod.EditorInfo;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BotWebViewVibrationEffect;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.Cells.PollEditTextCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.GradientClip;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.Stories.recorder.ButtonWithCounterView;
import org.telegram.ui.Stories.recorder.PreviewView;

public class TagEditCell extends LinearLayout {

    private final int currentAccount;
    private final long dialogId;
    private final Theme.ResourcesProvider resourcesProvider;

    private final SizeNotifierFrameLayout chatView;
    private final ChatMessageCell messageCell;
    private final AvatarDrawable avatarDrawable;
    private final BackupImageView avatarImageView;
    private final PollEditTextCell editTextCell;
    private final ImageView clearImageView;
    private final AnimatedTextView limitTextView;

    private MessageObject messageObject;
    private boolean isAdmin, isOwner;
    private Utilities.Callback<String> onRankEdited;
    private boolean ignoreEdit;

    public TagEditCell(Context context, int currentAccount, long dialogId, Theme.ResourcesProvider resourcesProvider) {
        super(context);

        this.currentAccount = currentAccount;
        this.dialogId = dialogId;
        this.resourcesProvider = resourcesProvider;

        setOrientation(VERTICAL);

        chatView = new SizeNotifierFrameLayout(context) {
            @Override
            protected boolean isActionBarVisible() { return false; }
            @Override
            protected boolean isStatusBarVisible() { return false; }
            @Override
            protected Theme.ResourcesProvider getResourceProvider() {
                return TagEditCell.this.resourcesProvider;
            }
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), dp(24) + messageCell.getMeasuredHeight());
            }
        };
        final Drawable drawable = PreviewView.getBackgroundDrawable(null, currentAccount, dialogId, Theme.isCurrentThemeDark());
        chatView.setBackgroundImage(drawable, false);

        messageCell = new ChatMessageCell(context, currentAccount) {
            @Override
            public boolean isPressed() {
                return false;
            }
            @Override
            public int getParentWidth() {
                if (getMeasuredWidth() != 0) return getMeasuredWidth() - dp(24);
                return AndroidUtilities.displaySize.x - dp(24);
            }
        };
        chatView.addView(messageCell, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM | Gravity.FILL_HORIZONTAL, 0, 12, 0, 12));

        avatarDrawable = new AvatarDrawable();
        avatarImageView = new BackupImageView(context);
        avatarImageView.setRoundRadius(dp(21));
        chatView.addView(avatarImageView, LayoutHelper.createFrame(42, 42, Gravity.BOTTOM | Gravity.LEFT, 8, 0, 0, 12));

        addView(chatView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.FILL_HORIZONTAL));

        editTextCell = new PollEditTextCell(context, false, PollEditTextCell.TYPE_DEFAULT, null, resourcesProvider);
        final EditTextBoldCursor editText = editTextCell.getTextView();
        editText.setEnabled(true);
        editText.setSingleLine(true);
        editText.setImeOptions(EditorInfo.IME_ACTION_DONE);
        editTextCell.setTextRight(50 + 56 + 8);
        clearImageView = new ImageView(context);
        clearImageView.setImageResource(R.drawable.menu_delete_old);
        clearImageView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourcesProvider), PorterDuff.Mode.SRC_IN));
        editTextCell.addView(clearImageView, LayoutHelper.createFrame(24, 24, Gravity.CENTER_VERTICAL | Gravity.RIGHT, 0, 0, 20, 0));
        ScaleStateListAnimator.apply(clearImageView);
        clearImageView.setOnClickListener(v -> {
            editText.setText("");
        });
        limitTextView = new AnimatedTextView(context, false, true, false);
        limitTextView.adaptWidth = false;
        limitTextView.setTypeface(AndroidUtilities.bold());
        limitTextView.setTextColor(Theme.getColor(Theme.key_text_RedBold, resourcesProvider));
        limitTextView.setTextSize(dp(14));
        limitTextView.setGravity(Gravity.CENTER);
        limitTextView.setAllowCancel(true);
        limitTextView.setScaleProperty(.6f);
        editTextCell.addView(limitTextView, LayoutHelper.createFrame(56, 50, Gravity.RIGHT | Gravity.FILL_VERTICAL, 0, 0, 20 + 24, 0));
        editText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                if (ignoreEdit) return;

                String rank = s.toString().trim();
                if (rank.length() > 16) {
                    limitTextView.setText("-" + (rank.length() - 16));
                    rank = rank.substring(0, 16);
                } else {
                    limitTextView.setText("");
                }
                if (onRankEdited != null) {
                    onRankEdited.run(rank);
                }

                if (messageObject != null) {
                    messageObject.forceUpdate = true;
                    messageCell.setMessageObject(messageObject, null, false, false, false);
                }
            }
        });
        addView(editTextCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.FILL_HORIZONTAL));

        messageCell.setDelegate(new ChatMessageCell.ChatMessageCellDelegate() {
            @Override
            public boolean canPerformActions() {
                return false;
            }
            @Override
            public boolean isAdmin(long uid) {
                return isAdmin;
            }
            @Override
            public boolean isOwner(long uid) {
                return isOwner;
            }
            @Override
            public String getAdminRank(long uid) {
                String rank = editText.getText().toString().trim();
                if (rank.length() > 16) {
                    rank = rank.substring(0, 16);
                }
                if (!isAdmin && TextUtils.isEmpty(rank))
                    return null;
                return rank;
            }
        });
    }

    private float shakeDp = -6;
    public boolean isOverLimit() {
        final EditTextBoldCursor editText = editTextCell.getTextView();
        final String rank = editText.getText().toString().trim();
        if (rank.length() <= 16) {
            return false;
        }

        AndroidUtilities.shakeViewSpring(editText, shakeDp = -shakeDp);
        BotWebViewVibrationEffect.APP_ERROR.vibrate();
        return true;
    }

    public void set(TLRPC.User user, String rank, boolean admin, boolean owner, Utilities.Callback<String> onRankEdited) {
        final TLRPC.TL_message message = new TLRPC.TL_message();
        message.from_id = MessagesController.getInstance(currentAccount).getPeer(user.id);
        message.peer_id = MessagesController.getInstance(currentAccount).getPeer(dialogId);
        message.message = "";
        message.date = ConnectionsManager.getInstance(currentAccount).getCurrentTime();
        message.out = false;

        this.isAdmin = admin;
        this.isOwner = owner;
        messageObject = new MessageObject(currentAccount, message, true, false);
        messageObject.forceAvatar = true;
        final SpannableStringBuilder sb = new SpannableStringBuilder("_\n_  ");
        sb.setSpan(new LineSpan((int) Math.min(AndroidUtilities.displaySize.x * 0.5f, dp(200))), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.setSpan(new LineSpan((int) Math.min(AndroidUtilities.displaySize.x * 0.44f, dp(160))), 2, 3, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        messageObject.messageText = sb;

        final TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-dialogId);
        messageCell.isChat = chat != null;
        messageCell.isMegagroup = ChatObject.isChannel(chat) && chat.megagroup;

        messageObject.generateLayout(null);
        messageCell.setMessageObject(messageObject, null, false, false, false);

        avatarDrawable.setInfo(user);
        avatarImageView.setForUserOrChat(user, avatarDrawable);

        this.onRankEdited = onRankEdited;
        ignoreEdit = true;
        editTextCell.setTextAndHint(rank, getString(TextUtils.isEmpty(rank) && !admin ? R.string.MemberTagHintAdd : R.string.MemberTagHintEdit), false);
        ignoreEdit = false;
    }

    private static final class LineSpan extends ReplacementSpan {

        private final int width;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        public LineSpan(int width) {
            this.width = width;
            paint.setColor(Theme.multAlpha(Theme.getColor(Theme.key_chat_inTimeText), 0.30f));
        }

        @Override
        public int getSize(@NonNull Paint paint, CharSequence text, int start, int end, @Nullable Paint.FontMetricsInt fm) {
            return width;
        }

        @Override
        public void draw(@NonNull Canvas canvas, CharSequence text, int start, int end, float x, int top, int y, int bottom, @NonNull Paint paint) {
            final float cy = (top + bottom) / 2.0f + dp(1.33f);
            final float h = dp(6.66f);

            AndroidUtilities.rectTmp.set(x, cy - h / 2f, x + width, cy + h / 2f);
            canvas.drawRoundRect(AndroidUtilities.rectTmp, h / 2f, h / 2f, this.paint);
        }
    }

    public static void showSheet(Context context, int currentAccount, long dialogId, TLRPC.User user, String rank, boolean admin, boolean owner, Theme.ResourcesProvider resourcesProvider) {
        final MessagesController m = MessagesController.getInstance(currentAccount);
        final TLRPC.Chat chat = m.getChat(-dialogId);
        final BottomSheet.Builder b = new BottomSheet.Builder(context, true, resourcesProvider);

        final LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        b.setCustomView(layout);

        final LinearLayout topView = new LinearLayout(context);

        final TextView titleTextView = TextHelper.makeTextView(context, 20, Theme.key_windowBackgroundWhiteBlackText, true);
        titleTextView.setText(getString(R.string.MemberTagTitle));
        topView.addView(titleTextView, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1, Gravity.LEFT | Gravity.CENTER_VERTICAL, 22, 0, 22, 0));

        final ImageView closeView = new ImageView(context);
        closeView.setScaleType(ImageView.ScaleType.CENTER);
        closeView.setImageResource(R.drawable.ic_close_white);
        closeView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider), PorterDuff.Mode.SRC_IN));
        closeView.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector, resourcesProvider), Theme.RIPPLE_MASK_CIRCLE_20DP, dp(18)));
        topView.addView(closeView, LayoutHelper.createLinear(32, 32, Gravity.RIGHT | Gravity.CENTER_VERTICAL, 0, 0, 10, 0));

        layout.addView(topView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 6, 0, 6));

        final ButtonWithCounterView button = new ButtonWithCounterView(context, resourcesProvider).setRound();
        final boolean hadRank = !TextUtils.isEmpty(rank) || admin;
        button.setText(getString(TextUtils.isEmpty(rank) && !admin && hadRank ? R.string.MemberTagButtonRemove : hadRank ? R.string.MemberTagButtonEdit : R.string.MemberTagButtonAdd));

        final String[] currentRank = new String[] { rank == null ? "" : rank };
        final TagEditCell cell = new TagEditCell(context, currentAccount, dialogId, resourcesProvider);
        cell.setClipToOutline(true);
        cell.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dp(16));
            }
        });
        cell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider));
        cell.set(user, rank, admin, owner, newRank -> {
            currentRank[0] = newRank;
            button.setText(getString(TextUtils.isEmpty(newRank) && !admin && hadRank ? R.string.MemberTagButtonRemove : hadRank ? R.string.MemberTagButtonEdit : R.string.MemberTagButtonAdd), true);
        });
        layout.addView(cell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.FILL_HORIZONTAL, 12, 12, 12, 1.66f));

        final TextInfoPrivacyCell infoCell = new TextInfoPrivacyCell(context, 22, resourcesProvider);
        infoCell.setText(UserObject.isUserSelf(user) ? getString(R.string.MemberTagSelfInfo) : formatString(R.string.MemberTagTheirInfo, UserObject.getUserName(user)));
        layout.addView(infoCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.FILL_HORIZONTAL, 0, 0, 0, 0));

        layout.addView(button, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, Gravity.FILL_HORIZONTAL, 14, 19, 14, 12));

        final BottomSheet sheet = b.create();

        sheet.smoothKeyboardAnimationEnabled = true;
        sheet.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray, resourcesProvider));

        final boolean adding = TextUtils.isEmpty(rank) && !admin && !owner;
        button.setOnClickListener(v -> {
            if (button.isLoading()) return;
            if (cell.isOverLimit()) return;
            button.setLoading(true);

            AndroidUtilities.hideKeyboard(cell.editTextCell);

            final TLRPC.TL_messages_editChatParticipantRank req = new TLRPC.TL_messages_editChatParticipantRank();
            req.peer = m.getInputPeer(dialogId);
            req.participant = MessagesController.getInputPeer(user);
            req.rank = currentRank[0];
            ConnectionsManager.getInstance(currentAccount).sendRequestTyped(req, AndroidUtilities::runOnUIThread, (res, err) -> {
                if (res != null) {
                    m.updateRank(-dialogId, user.id, req.rank);
                    m.processUpdates(res, false);
                    sheet.dismiss();

                    final BaseFragment lastFragment = LaunchActivity.getSafeLastFragment();
                    if (!TextUtils.isEmpty(req.rank) && lastFragment != null) {
                        BulletinFactory.of(lastFragment)
                            .createSimpleBulletin(R.raw.contact_check, getString(adding ? R.string.TagAdded : R.string.TagEdited), req.rank)
                            .wrapContent()
                            .show();
                    }
                } else if (err != null) {
                    BulletinFactory.of(sheet.topBulletinContainer, resourcesProvider).showForError(err);
                    button.setLoading(false);
                }
            });
        });
        closeView.setOnClickListener(v -> sheet.dismiss());

        sheet.show();

        final EditTextBoldCursor editText = cell.editTextCell.getTextView();
        editText.post(() -> {
            editText.requestFocus();
            editText.setSelection(0, editText.length());
            AndroidUtilities.showKeyboard(editText);
        });
    }

    public static void showInfoSheet(Context context, int currentAccount, long dialogId, TLRPC.User user, String rank, boolean admin, boolean owner, boolean admin_can_edit, Theme.ResourcesProvider resourcesProvider) {
        final MessagesController m = MessagesController.getInstance(currentAccount);
        final TLRPC.Chat chat = m.getChat(-dialogId);
        if (chat == null) return;
        final BottomSheet.Builder b = new BottomSheet.Builder(context, true, resourcesProvider);

        final LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        b.setCustomView(layout);

        final int color = owner ? 0xFF955CDB : admin ? 0xFF40A920 : 0xFF96A2AD;
        final BackupImageView iconView = new BackupImageView(context);
        iconView.setImageResource(R.drawable.large_user_tag);
        iconView.setBackground(Theme.createCircleDrawable(dp(80), color));
        layout.addView(iconView, LayoutHelper.createLinear(80, 80, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, 18, 0, 0));

        final TextView titleView = TextHelper.makeTextView(context, 20, Theme.key_windowBackgroundWhiteBlackText, true);
        titleView.setGravity(Gravity.CENTER);
        titleView.setText(getString(owner ? R.string.TagInfoOwnerTitle : admin ? R.string.TagInfoAdminTitle : R.string.TagInfoMemberTitle));
        layout.addView(titleView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 32, 15, 32, 0));

        final TextView subtitleView = TextHelper.makeTextView(context, 14, Theme.key_windowBackgroundWhiteBlackText, false);
        subtitleView.setGravity(Gravity.CENTER);
        subtitleView.setLineSpacing(dp(3), 1.0f);
        final String rankStr = rank == null ? (owner ? getString(R.string.ChatTagOwner) : admin ? getString(R.string.ChatTagAdmin) : "") : rank;
        SpannableStringBuilder tag = new SpannableStringBuilder(rankStr);
        if (owner || admin) {
            final int textColor = owner ? 0xFF955CDB : 0xFF40A920;
            final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            bgPaint.setColor(Theme.multAlpha(textColor, 0.10f));
            tag.setSpan(new ReplacementSpan() {
                private float textWidth;
                @Override
                public int getSize(@NonNull Paint paint, CharSequence text, int start, int end, @Nullable Paint.FontMetricsInt fm) {
                    return (int) (dpf2(11.33f) + (textWidth = paint.measureText(rankStr)));
                }
                @Override
                public void draw(@NonNull Canvas canvas, CharSequence text, int start, int end, float x, int top, int y, int bottom, @NonNull Paint paint) {
                    final float cy = (top + bottom) / 2f, h = dp(19);
                    paint.setColor(textColor);
                    canvas.drawRoundRect(x, cy - h / 2f, x + textWidth + dp(11.33f), cy + h / 2f, h / 2f, h / 2f, bgPaint);
                    canvas.drawText(rankStr, x + dpf2(5.66f), bottom - dp(6), paint);
                }
            }, 0, tag.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        } else {
            tag.setSpan(new ForegroundColorSpan(Theme.getColor(Theme.key_chat_inTimeText)), 0, tag.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        subtitleView.setText(AndroidUtilities.replaceCharSequence("un1", AndroidUtilities.replaceTags(formatString(owner ? R.string.TagInfoOwnerText : admin ? R.string.TagInfoAdminText : R.string.TagInfoMemberText, UserObject.getFirstName(user), chat.title)), tag));
        layout.addView(subtitleView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 32, 10, 32, 25));

        final LinearLayout previewLayout = new LinearLayout(context);
        previewLayout.setOrientation(LinearLayout.HORIZONTAL);
        layout.addView(previewLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.FILL_HORIZONTAL, 16, 0, 16, 16));

        for (int i = 0; i < 2; ++i) {
            final ChatMessageCell messageCell = new ChatMessageCell(context, currentAccount) {
                @Override
                public void updateTranslation() {/* NO-OP */}
                @Override
                public int getParentWidth() {
                    return (AndroidUtilities.displaySize.x - dp(96 + 32)) / 2;
                }
                @Override
                public boolean isPressed() {
                    return false;
                }
            };
            final boolean forceAdmin = i == 1;
            messageCell.setDelegate(new ChatMessageCell.ChatMessageCellDelegate() {
                @Override
                public boolean canPerformActions() { return false; }
                @Override
                public boolean isAdmin(long uid) {
                    return forceAdmin;
                }
                @Override
                public boolean isOwner(long uid) {
                    return forceAdmin && owner;
                }
                @Override
                public String getAdminRank(long uid) { return getString(forceAdmin ? owner ? R.string.TagInfoOwnerTitle : R.string.TagInfoAdminTitle : R.string.TagInfoMemberTitle); }
            });
            final SizeNotifierFrameLayout chatView = new SizeNotifierFrameLayout(context) {
                @Override
                protected boolean isActionBarVisible() { return false; }
                @Override
                protected boolean isStatusBarVisible() { return false; }
                @Override
                protected Theme.ResourcesProvider getResourceProvider() { return resourcesProvider; }
                @Override
                protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                    messageCell.measure(MeasureSpec.makeMeasureSpec(AndroidUtilities.displaySize.x, MeasureSpec.EXACTLY), heightMeasureSpec);
                    setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), dp(24) + messageCell.getMeasuredHeight());
                }
                private GradientClip clip = new GradientClip();
                @Override
                protected boolean drawChild(@NonNull Canvas canvas, View child, long drawingTime) {
                    if (child == messageCell) {
                        canvas.saveLayerAlpha(0, 0, getWidth(), getHeight(), 0xFF, Canvas.ALL_SAVE_FLAG);
                        boolean r = super.drawChild(canvas, child, drawingTime);
                        canvas.save();
                        AndroidUtilities.rectTmp.set(0, 0, dp(45), getHeight());
                        clip.draw(canvas, AndroidUtilities.rectTmp, GradientClip.LEFT, 1.0f);
                        canvas.restore();
                        canvas.restore();
                        return r;
                    }
                    return super.drawChild(canvas, child, drawingTime);
                }
            };
            final Drawable drawable = PreviewView.getBackgroundDrawable(null, currentAccount, dialogId, Theme.isCurrentThemeDark());
            chatView.setBackgroundImage(drawable, false);

            chatView.addView(messageCell, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM | Gravity.FILL_HORIZONTAL, 0, 12, 0, 12));

            previewLayout.addView(chatView, LayoutHelper.createLinear(0, LayoutHelper.MATCH_PARENT, 1, Gravity.FILL, i == 1 ? 6 : 0, 0, i == 0 ? 6 : 0, 0));
            chatView.setClipToOutline(true);
            chatView.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dp(16));
                }
            });

            final TLRPC.TL_message message = new TLRPC.TL_message();
            message.from_id = MessagesController.getInstance(currentAccount).getPeer(user.id);
            message.peer_id = MessagesController.getInstance(currentAccount).getPeer(dialogId);
            message.message = "";
            message.date = ConnectionsManager.getInstance(currentAccount).getCurrentTime();
            message.out = false;

            final MessageObject messageObject = new MessageObject(currentAccount, message, true, false);
            messageObject.forceAvatar = true;
            final SpannableStringBuilder sb = new SpannableStringBuilder("_\n_  ");
            sb.setSpan(new LineSpan(dp(200)), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            sb.setSpan(new LineSpan(dp(160)), 2, 3, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            messageObject.messageText = sb;

            messageCell.isChat = chat != null;
            messageCell.isMegagroup = ChatObject.isChannel(chat) && chat.megagroup;

            messageObject.generateLayout(null);
            messageCell.setMessageObject(messageObject, null, false, false, false);
            messageCell.setTranslationX(-dp(140));
        }

        final ButtonWithCounterView button = new ButtonWithCounterView(context, resourcesProvider).setRound();

        final boolean canEdit = (
            ChatObject.canManageTags(chat) && (!admin || !owner && admin_can_edit || UserObject.isUserSelf(user)) ||
            ChatObject.canManageMyTag(chat) && UserObject.isUserSelf(user)
        );
        if (!canEdit && !ChatObject.canManageTags(chat) && chat != null && !chat.creator && chat.admin_rights == null && !owner) {
            final TextView footerView = TextHelper.makeTextView(context, 12, Theme.key_windowBackgroundWhiteGrayText, false);
            footerView.setGravity(Gravity.CENTER_HORIZONTAL);
            footerView.setText(getString(R.string.CantEditTagAdmins));
            layout.addView(footerView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 32, 0, 32, 0));
        }
        layout.addView(button, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, Gravity.FILL_HORIZONTAL, 16, 16, 16, 16));

        final boolean[] updatedPref = new boolean[1];
        final BottomSheet sheet = b.create();
        if (!canEdit) {
            button.setText(replaceUnderstood(getString(R.string.Understood)));
            button.setOnClickListener(v -> {
                sheet.dismiss();

                if (!updatedPref[0]) {
                    final SharedPreferences p = MessagesController.getGlobalMainSettings();
                    p.edit().putInt("showchattagsinfo", 0).apply();
                    updatedPref[0] = true;
                }
            });
        } else {
            button.setText(getString(UserObject.isUserSelf(user) ? TextUtils.isEmpty(rank) ? R.string.TagInfoButtonAddMyTag : R.string.TagInfoButtonEditMyTag : TextUtils.isEmpty(rank) ? R.string.TagInfoButtonAddTag : R.string.TagInfoButtonEditTag));
            button.setOnClickListener(v -> {
                sheet.dismiss();
                showSheet(context, currentAccount, dialogId, user, rank, admin, owner, resourcesProvider);

                if (!updatedPref[0]) {
                    final SharedPreferences p = MessagesController.getGlobalMainSettings();
                    p.edit().putInt("showchattagsinfo", 0).apply();
                    updatedPref[0] = true;
                }
            });
        }

        sheet.smoothKeyboardAnimationEnabled = true;
        sheet.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider));
        sheet.setOnDismissListener(() -> {
            if (!updatedPref[0]) {
                final SharedPreferences p = MessagesController.getGlobalMainSettings();
                p.edit().putInt("showchattagsinfo", p.getInt("showchattagsinfo", 3) - 1).apply();
                updatedPref[0] = true;
            }
        });

        if (MessagesController.getGlobalMainSettings().getInt("showchattagsinfo", 3) <= 0 && canEdit) {
            showSheet(context, currentAccount, dialogId, user, rank, admin, owner, resourcesProvider);
            return;
        }

        sheet.show();

    }

}
