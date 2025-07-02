package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;
import static org.telegram.ui.Stars.StarsIntroActivity.StarsTransactionView.getPlatformDrawable;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.style.ClickableSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.browser.Browser;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.TextViewWithLoading;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.AvatarSpan;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.spoilers.SpoilersTextView;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.Stories.recorder.HintView2;

import java.util.ArrayList;
import java.util.Date;

public class TableView extends TableLayout {

    private final Theme.ResourcesProvider resourcesProvider;

    private final Path path = new Path();
    private final float[] radii = new float[8];
    private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final float w = Math.max(1, dp(.66f));
    private final float hw = w / 2f;

    public TableView(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;
        setClipToPadding(false);
        setColumnStretchable(1, true);
    }

    public void clear() {
        removeAllViews();
    }

    public TableRow addRow(CharSequence title, View content) {
        TableRow row = new TableRow(getContext());
        TableRow.LayoutParams lp;
        lp = new TableRow.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT);
        row.addView(new TableRowTitle(this, title), lp);
        lp = new TableRow.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        row.addView(new TableRowContent(this, content), lp);
        addView(row);
        return row;
    }

    public TableRow addRowUnpadded(CharSequence title, View content) {
        TableRow row = new TableRow(getContext());
        TableRow.LayoutParams lp;
        lp = new TableRow.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT);
        row.addView(new TableRowTitle(this, title), lp);
        lp = new TableRow.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        row.addView(new TableRowContent(this, content, true), lp);
        addView(row);
        return row;
    }

    public TableRow addRowMonospaced(CharSequence title, CharSequence text, int textFontSizeDp, Runnable copyButton) {
        FrameLayout idLayout = new FrameLayout(getContext());
        idLayout.setPadding(dp(12.66f), dp(9.33f), dp(10.66f), dp(9.33f));
        TextView textView = new TextView(getContext());
        textView.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MONO));
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, textFontSizeDp);
        textView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider));
        textView.setMaxLines(4);
        textView.setSingleLine(false);
        textView.setText(text);
        idLayout.addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL, 0, 0, 34, 0));
        if (copyButton != null) {
            ImageView copyView = new ImageView(getContext());
            copyView.setImageResource(R.drawable.msg_copy);
            copyView.setScaleType(ImageView.ScaleType.CENTER);
            copyView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteBlueIcon, resourcesProvider), PorterDuff.Mode.SRC_IN));
            copyView.setOnClickListener(v -> {
                AndroidUtilities.addToClipboard(text);
                copyButton.run();
            });
            ScaleStateListAnimator.apply(copyView);
            copyView.setBackground(Theme.createSelectorDrawable(Theme.multAlpha(Theme.getColor(Theme.key_windowBackgroundWhiteBlueIcon, resourcesProvider), .10f), Theme.RIPPLE_MASK_ROUNDRECT_6DP));
            idLayout.addView(copyView, LayoutHelper.createFrame(30, 30, Gravity.RIGHT | Gravity.CENTER_VERTICAL));
        }
        return addRowUnpadded(title, idLayout);
    }

    public TableRow addWalletAddressRow(CharSequence title, CharSequence text, Runnable onCopy) {
        FrameLayout idLayout = new FrameLayout(getContext());
        LinkSpanDrawable.LinksTextView textView = new LinkSpanDrawable.LinksTextView(getContext());
        textView.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MONO));
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        textView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider));
        textView.setLinkTextColor(Theme.getColor(Theme.key_chat_messageLinkIn, resourcesProvider));
        textView.setMaxLines(4);
//        textView.setMaxLines(1);
//        textView.setSingleLine();
//        textView.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        SpannableStringBuilder sb = new SpannableStringBuilder(text);
        sb.insert(sb.length() / 2, "\n");
        if (onCopy != null) {
            sb.setSpan(new ClickableSpan() {
                @Override
                public void onClick(@NonNull View widget) {
                    AndroidUtilities.addToClipboard(text);
                    onCopy.run();
                }
                @Override
                public void updateDrawState(@NonNull TextPaint ds) {
                    ds.setColor(ds.linkColor);
                }
            }, 0, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        textView.setText(sb);
        textView.setDisablePaddingsOffsetY(true);
        textView.setPadding(dp(12.66f), dp(9.33f), dp(10.66f), dp(9.33f));
        idLayout.addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL, 0, 0, 0, 0));
//        textView.setMaxWidth(HintView2.cutInFancyHalf(textView.getText(), textView.getPaint()) + dp(12.66f + 12.66f));
        return addRowUnpadded(title, idLayout);
    }

    public TableRow addRowUserWithEmojiStatus(CharSequence title, final int currentAccount, final long did, Runnable onClick) {
        final LinkSpanDrawable.LinksSimpleTextView textView = new LinkSpanDrawable.LinksSimpleTextView(getContext(), resourcesProvider);
        textView.setPadding(dp(12.66f), dp(9.33f), dp(12.66f), dp(9.33f));
        textView.setTextColor(Theme.getColor(Theme.key_chat_messageLinkIn, resourcesProvider));
        textView.setLinkTextColor(Theme.getColor(Theme.key_chat_messageLinkIn, resourcesProvider));
        textView.setTextSize(14);
        AvatarSpan avatarSpan = new AvatarSpan(textView, currentAccount, 24);
        CharSequence username;
        boolean clickable = true;
        if (did == UserObject.ANONYMOUS) {
            clickable = false;
            username = getString(R.string.StarsTransactionHidden);
            CombinedDrawable iconDrawable = getPlatformDrawable("anonymous");
            iconDrawable.setIconSize(dp(16), dp(16));
            avatarSpan.setImageDrawable(iconDrawable);
        } else if (UserObject.isService(did)) {
            username = getString(R.string.StarsTransactionUnknown);
            CombinedDrawable iconDrawable = getPlatformDrawable("fragment");
            iconDrawable.setIconSize(dp(16), dp(16));
            avatarSpan.setImageDrawable(iconDrawable);
        } else if (did >= 0) {
            final TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(did);
            username = UserObject.getUserName(user);
            avatarSpan.setUser(user);
        } else {
            final TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-did);
            username = chat == null ? "" : chat.title;
            avatarSpan.setChat(chat);
        }
        final SpannableStringBuilder ssb = new SpannableStringBuilder("x  " + username);
        ssb.setSpan(avatarSpan, 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        if (clickable) {
            textView.setClickable(true);
            ssb.setSpan(new ClickableSpan() {
                @Override
                public void onClick(@NonNull View widget) {
                    if (onClick != null) {
                        onClick.run();
                    }
                }
                @Override
                public void updateDrawState(@NonNull TextPaint ds) {
                    ds.setUnderlineText(false);
                }
            }, 3, ssb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        final int color = Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider);
        final AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable emojiDrawable = new AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable(textView, dp(20));
        emojiDrawable.setColor(color);
        emojiDrawable.offset(dp(12), 0);
        textView.addOnAttachStateChangeListener(new OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(@NonNull View v) {
                emojiDrawable.attach();
            }

            @Override
            public void onViewDetachedFromWindow(@NonNull View v) {
                emojiDrawable.detach();
            }
        });
        final Drawable premiumDrawable = getContext().getResources().getDrawable(R.drawable.msg_premium_liststar).mutate();
        premiumDrawable.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN));
        final Utilities.Callback<Object[]> updateStatus = args -> {
            if (did == UserObject.ANONYMOUS || UserObject.isService(did)) {
                return;
            }
            boolean isPremium;
            TLRPC.EmojiStatus emoji_status;
            if (did > 0) {
                TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(did);
                emoji_status = user != null ? user.emoji_status : null;
                isPremium = user != null && user.premium;
            } else {
                TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-did);
                emoji_status = chat != null ? chat.emoji_status : null;
                isPremium = false;
            }
            final long emojiStatusDocumentId2 = DialogObject.getEmojiStatusDocumentId(emoji_status);
            if (emojiStatusDocumentId2 != 0) {
                emojiDrawable.set(emojiStatusDocumentId2, true);
                emojiDrawable.setParticles(DialogObject.isEmojiStatusCollectible(emoji_status), true);
                textView.setRightDrawable(emojiDrawable);
            } else if (isPremium) {
                emojiDrawable.set(premiumDrawable, true);
                emojiDrawable.setParticles(false, true);
                textView.setRightDrawable(emojiDrawable);
            } else {
                textView.setRightDrawable(null);
            }
            emojiDrawable.setColor(color);
        };
        updateStatus.run(null);
        textView.setRightDrawable(emojiDrawable);
        NotificationCenter.getInstance(currentAccount).listen(textView, NotificationCenter.updateInterfaces, updateStatus);
        NotificationCenter.getInstance(currentAccount).listen(textView, NotificationCenter.userEmojiStatusUpdated, updateStatus);
        textView.setText(ssb);
        return addRowUnpadded(title, textView);
    }

    public TableRow addRowUser(CharSequence title, final int currentAccount, final long did, Runnable onClick) {
        return addRowUser(title, currentAccount, did, onClick, null, null);
    }

    public TableRow addRowUser(CharSequence title, final int currentAccount, final long did, Runnable onClick, CharSequence buttonText, Runnable buttonOnClick) {
        final ButtonSpan.TextViewButtons textView = new ButtonSpan.TextViewButtons(getContext(), resourcesProvider);
        textView.setPadding(dp(12.66f), dp(9.33f), dp(12.66f), dp(9.33f));
        textView.setEllipsize(TextUtils.TruncateAt.END);
        textView.setTextColor(Theme.getColor(Theme.key_chat_messageLinkIn, resourcesProvider));
        textView.setLinkTextColor(Theme.getColor(Theme.key_chat_messageLinkIn, resourcesProvider));
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        textView.setSingleLine(true);
        textView.setDisablePaddingsOffsetY(true);
        AvatarSpan avatarSpan = new AvatarSpan(textView, currentAccount, 24);
        CharSequence username;
        boolean deleted = false;
        boolean clickable = true;
        final boolean unknown;
        if (did == UserObject.ANONYMOUS) {
            deleted = false;
            clickable = false;
            unknown = true;
            username = getString(R.string.StarsTransactionHidden);
            CombinedDrawable iconDrawable = getPlatformDrawable("anonymous");
            iconDrawable.setIconSize(dp(16), dp(16));
            avatarSpan.setImageDrawable(iconDrawable);
        } else if (UserObject.isService(did)) {
            deleted = false;
            unknown = true;
            username = getString(R.string.StarsTransactionUnknown);
            CombinedDrawable iconDrawable = getPlatformDrawable("fragment");
            iconDrawable.setIconSize(dp(16), dp(16));
            avatarSpan.setImageDrawable(iconDrawable);
        } else if (did >= 0) {
            unknown = false;
            TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(did);
            deleted = user == null;
            username = UserObject.getUserName(user);
            avatarSpan.setUser(user);
        } else {
            unknown = false;
            TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-did);
            deleted = chat == null;
            username = chat == null ? "" : chat.title;
            avatarSpan.setChat(chat);
        }
        SpannableStringBuilder ssb = new SpannableStringBuilder("x  " + username);
        ssb.setSpan(avatarSpan, 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        if (clickable) {
            ssb.setSpan(new ClickableSpan() {
                @Override
                public void onClick(@NonNull View widget) {
                    if (onClick != null) {
                        onClick.run();
                    }
                }
                @Override
                public void updateDrawState(@NonNull TextPaint ds) {
                    ds.setUnderlineText(false);
                }
            }, 3, ssb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        if (buttonText != null) {
            textView.addButton(new ButtonSpan(buttonText, buttonOnClick, resourcesProvider));
        }
        textView.setText(ssb);
        if (!deleted) {
            return addRowUnpadded(title, textView);
        }
        return null;
    }

    public TableRow addRowDateTime(CharSequence title, int date) {
        return addRow(title, LocaleController.formatString(R.string.formatDateAtTime, LocaleController.getInstance().getFormatterGiveawayCard().format(new Date(date * 1000L)), LocaleController.getInstance().getFormatterDay().format(new Date(date * 1000L))));
    }

    public TableRow addRowLink(CharSequence title, CharSequence value, Runnable onClick) {
        final LinkSpanDrawable.LinksTextView textView = new LinkSpanDrawable.LinksTextView(getContext(), resourcesProvider);
        textView.setPadding(dp(12.66f), dp(9.33f), dp(12.66f), dp(9.33f));
        textView.setEllipsize(TextUtils.TruncateAt.END);
        textView.setTextColor(Theme.getColor(Theme.key_chat_messageLinkIn, resourcesProvider));
        textView.setLinkTextColor(Theme.getColor(Theme.key_chat_messageLinkIn, resourcesProvider));
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        textView.setSingleLine(true);
        ((LinkSpanDrawable.LinksTextView) textView).setDisablePaddingsOffsetY(true);
        SpannableStringBuilder ssb = new SpannableStringBuilder(value);
        ssb.setSpan(new ClickableSpan() {
            @Override
            public void onClick(@NonNull View widget) {
                if (onClick != null) {
                    onClick.run();
                }
            }
            @Override
            public void updateDrawState(@NonNull TextPaint ds) {
                ds.setUnderlineText(false);
            }
        }, 0, ssb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        textView.setText(ssb);
        return addRowUnpadded(title, textView);
    }

    public TableRow addRow(CharSequence title, CharSequence text) {
        ButtonSpan.TextViewButtons textView = new ButtonSpan.TextViewButtons(getContext());
        textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        textView.setText(Emoji.replaceEmoji(text, textView.getPaint().getFontMetricsInt(), false));
        NotificationCenter.listenEmojiLoading(textView);

        TableRow row = new TableRow(getContext());
        TableRow.LayoutParams lp;
        lp = new TableRow.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT);
        row.addView(new TableRowTitle(this, title), lp);
        lp = new TableRow.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        row.addView(new TableRowContent(this, textView), lp);
        addView(row);

        return row;
    }

    public TableRow addRow(CharSequence title, CharSequence text, CharSequence buttonText, Runnable buttonOnClick) {
        ButtonSpan.TextViewButtons textView = new ButtonSpan.TextViewButtons(getContext());
        textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        SpannableStringBuilder ssb = new SpannableStringBuilder(Emoji.replaceEmoji(text, textView.getPaint().getFontMetricsInt(), false));
        if (buttonText != null) {
            ssb.append(" ").append(ButtonSpan.make(buttonText, buttonOnClick, resourcesProvider));
        }
        textView.setText(ssb);
        NotificationCenter.listenEmojiLoading(textView);

        TableRow row = new TableRow(getContext());
        TableRow.LayoutParams lp;
        lp = new TableRow.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT);
        row.addView(new TableRowTitle(this, title), lp);
        lp = new TableRow.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        row.addView(new TableRowContent(this, textView), lp);
        addView(row);

        return row;
    }

    public TableRowFullContent addFullRow(CharSequence text) {
        SpoilersTextView textView = new SpoilersTextView(getContext());
        textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
        textView.setLinkTextColor(Theme.getColor(Theme.key_chat_messageLinkIn, resourcesProvider));
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        text = Emoji.replaceEmoji(text, textView.getPaint().getFontMetricsInt(), false);
        textView.setText(text);
        NotificationCenter.listenEmojiLoading(textView);
        textView.setPadding(dp(12.66f), dp(9.33f), dp(12.66f), dp(9.33f));

        final TableRow row = new TableRow(getContext());
        TableRow.LayoutParams lp;
        lp = new TableRow.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT);
        lp.span = 2;
        final TableRowFullContent cell = new TableRowFullContent(this, textView, true);
        row.addView(cell, lp);
        addView(row);
        return cell;
    }

    public void addFullRow(CharSequence text, ArrayList<TLRPC.MessageEntity> entities) {
        AnimatedEmojiSpan.TextViewEmojis textView = new AnimatedEmojiSpan.TextViewEmojis(getContext());
        textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        text = new SpannableStringBuilder(text);
        MessageObject.addEntitiesToText(text, entities, false, false, false, false);
        text = Emoji.replaceEmoji(text, textView.getPaint().getFontMetricsInt(), false);
        text = MessageObject.replaceAnimatedEmoji(text, entities, textView.getPaint().getFontMetricsInt());
        textView.setText(text);
        NotificationCenter.listenEmojiLoading(textView);

        TableRow row = new TableRow(getContext());
        TableRow.LayoutParams lp;
        lp = new TableRow.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT);
        lp.span = 2;
        row.addView(new TableRowFullContent(this, textView), lp);
        addView(row);
    }

    public static class TableRowTitle extends TextView {

        private final TableView table;
        private final Theme.ResourcesProvider resourcesProvider;

        public TableRowTitle(TableView table, CharSequence title) {
            super(table.getContext());
            this.table = table;
            this.resourcesProvider = table.resourcesProvider;

            setPadding(dp(12.66f), dp(9.33f), dp(12.66f), dp(9.33f));
            setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
            setTypeface(AndroidUtilities.bold());
            setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            setText(title);
        }

        private boolean first, last;

        public void setFirstLast(boolean first, boolean last) {
            if (this.first != first || this.last != last) {
                this.first = first;
                this.last = last;
                invalidate();
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (first || last) {
                final float r = dp(4);
                table.radii[0] = table.radii[1] = first ? r : 0; // top left
                table.radii[2] = table.radii[3] = 0; // top right
                table.radii[4] = table.radii[5] = 0; // bottom right
                table.radii[6] = table.radii[7] = last ? r : 0; // bottom left
                table.path.rewind();
                AndroidUtilities.rectTmp.set(table.hw, table.hw, getWidth() + table.hw, getHeight() + table.hw * dp(last ? -1 : +1));
                table.path.addRoundRect(AndroidUtilities.rectTmp, table.radii, Path.Direction.CW);
                canvas.drawPath(table.path, table.backgroundPaint);
                canvas.drawPath(table.path, table.borderPaint);
            } else {
                canvas.drawRect(table.hw, table.hw, getWidth() + table.hw, getHeight() + table.hw, table.backgroundPaint);
                canvas.drawRect(table.hw, table.hw, getWidth() + table.hw, getHeight() + table.hw, table.borderPaint);
            }
            super.onDraw(canvas);
        }
    }

    public static class TableRowFullContent extends FrameLayout {

        private final TableView table;
        private final Theme.ResourcesProvider resourcesProvider;
        private boolean filled;

        public TableRowFullContent(TableView table, View content) {
            this(table, content, false);
        }

        public TableRowFullContent(TableView table, View content, boolean unpadded) {
            super(table.getContext());
            this.table = table;
            this.resourcesProvider = table.resourcesProvider;

            setWillNotDraw(false);
            if (!unpadded) {
                setPadding(dp(12.66f), dp(9.33f), dp(12.66f), dp(9.33f));
            }
            addView(content, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        }

        private boolean first, last;

        public void setFirstLast(boolean first, boolean last) {
            if (this.first != first || this.last != last) {
                this.first = first;
                this.last = last;
                invalidate();
            }
        }

        public void setFilled(boolean filled) {
            this.filled = filled;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (first || last) {
                final float r = dp(4);
                table.radii[0] = table.radii[1] = first ? r : 0; // top left
                table.radii[2] = table.radii[3] = first ? r : 0; // top right
                table.radii[4] = table.radii[5] = last ? r : 0; // bottom right
                table.radii[6] = table.radii[7] = last ? r : 0; // bottom left
                table.path.rewind();
                AndroidUtilities.rectTmp.set(table.hw, table.hw, getWidth() - table.hw, getHeight() + table.hw * dp(last ? -1f : +1f));
                table.path.addRoundRect(AndroidUtilities.rectTmp, table.radii, Path.Direction.CW);
                if (filled) canvas.drawPath(table.path, table.backgroundPaint);
                canvas.drawPath(table.path, table.borderPaint);
            } else {
                if (filled) canvas.drawRect(table.hw, table.hw, getWidth() + table.hw, getHeight() + table.hw, table.backgroundPaint);
                canvas.drawRect(table.hw, table.hw, getWidth() - table.hw, getHeight() + table.hw, table.borderPaint);
            }
            super.onDraw(canvas);
        }
    }

    public static class TableRowContent extends FrameLayout {

        private final TableView table;
        private final Theme.ResourcesProvider resourcesProvider;

        public TableRowContent(TableView table, View content) {
            this(table, content, false);
        }

        public TableRowContent(TableView table, View content, boolean unpadded) {
            super(table.getContext());
            this.table = table;
            this.resourcesProvider = table.resourcesProvider;

            setWillNotDraw(false);
            if (!unpadded) {
                setPadding(dp(12.66f), dp(9.33f), dp(12.66f), dp(9.33f));
            }
            addView(content, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        }

        private boolean first, last;

        public void setFirstLast(boolean first, boolean last) {
            if (this.first != first || this.last != last) {
                this.first = first;
                this.last = last;
                invalidate();
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (first || last) {
                final float r = dp(4);
                table.radii[0] = table.radii[1] = 0; // top left
                table.radii[2] = table.radii[3] = first ? r : 0; // top right
                table.radii[4] = table.radii[5] = last ? r : 0; // bottom right
                table.radii[6] = table.radii[7] = 0; // bottom left
                table.path.rewind();
                AndroidUtilities.rectTmp.set(table.hw, table.hw, getWidth() - table.hw, getHeight() + table.hw * dp(last ? -1f : +1f));
                table.path.addRoundRect(AndroidUtilities.rectTmp, table.radii, Path.Direction.CW);
                canvas.drawPath(table.path, table.borderPaint);
            } else {
                canvas.drawRect(table.hw, table.hw, getWidth() - table.hw, getHeight() + table.hw, table.borderPaint);
            }
            super.onDraw(canvas);
        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);

        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(w);
        borderPaint.setColor(Theme.getColor(Theme.key_table_border, resourcesProvider));
        backgroundPaint.setStyle(Paint.Style.FILL);
        backgroundPaint.setColor(Theme.getColor(Theme.key_table_background, resourcesProvider));

        final int height = getChildCount();
        for (int y = 0; y < height; ++y) {
            if (!(getChildAt(y) instanceof TableRow))
                continue;
            TableRow row = (TableRow) getChildAt(y);
            final int width = row.getChildCount();
            for (int x = 0; x < width; ++x) {
                View child = row.getChildAt(x);
                if (child instanceof TableRowTitle) {
                    ((TableRowTitle) child).setFirstLast(y == 0, y == height - 1);
                } else if (child instanceof TableRowContent) {
                    ((TableRowContent) child).setFirstLast(y == 0, y == height - 1);
                } else if (child instanceof TableRowFullContent) {
                    ((TableRowFullContent) child).setFirstLast(y == 0, y == height - 1);
                }
            }
        }
    }

}
