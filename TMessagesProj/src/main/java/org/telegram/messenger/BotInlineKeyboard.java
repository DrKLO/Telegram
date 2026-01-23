package org.telegram.messenger;

import androidx.annotation.DrawableRes;
import androidx.annotation.StringRes;

import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;

public class BotInlineKeyboard {
    public static abstract class Button {
        public abstract String getText();
        public abstract int getIcon();
    }

    public static class ButtonBot extends Button {
        public final TLRPC.KeyboardButton button;

        public ButtonBot(TLRPC.KeyboardButton button) {
            this.button = button;
        }

        @Override
        public String getText() {
            return button.text;
        }

        @Override
        public int getIcon() {
            return 0;
        }
    }

    public static class ButtonCustom extends Button {
        public static final int SUGGESTION_DECLINE = 1;
        public static final int SUGGESTION_ACCEPT = 2;
        public static final int SUGGESTION_EDIT = 3;
        public static final int OPEN_MESSAGE_THREAD = 4;
        public static final int GIFT_OFFER_DECLINE = 5;
        public static final int GIFT_OFFER_ACCEPT = 6;

        public final int id;
        public final @DrawableRes int icon;
        public final @StringRes int text;

        public ButtonCustom(int id, @StringRes int text, @DrawableRes int icon) {
            this.id = id;
            this.text = text;
            this.icon = icon;
        }

        @Override
        public String getText() {
            return LocaleController.getString(text);
        }

        @Override
        public int getIcon() {
            return icon;
        }
    }

    public interface Source {
        int getRowsCount();
        int getColumnsCount(int row);
        Button getButton(int row, int column);
        boolean hasSeparator(int row);
        default boolean isEmpty() {
            return getRowsCount() == 0;
        }
    }

    private static class KeyboardSourceArray implements Source {
        private final Button[][] buttons;
        private final int separators;

        private KeyboardSourceArray(Button[][] buttons, int separators) {
            this.buttons = buttons;
            this.separators = separators;
        }

        @Override
        public int getRowsCount() {
            return buttons.length;
        }

        @Override
        public int getColumnsCount(int row) {
            return buttons[row].length;
        }

        @Override
        public Button getButton(int row, int column) {
            return buttons[row][column];
        }

        @Override
        public boolean hasSeparator(int row) {
            return (separators & (1 << row)) != 0;
        }
    }

    public static class Builder {
        private final ArrayList<Button[]> buttons = new ArrayList<>();
        private int separators;

        public void addBotKeyboard(TLRPC.TL_replyInlineMarkup replyInlineMarkup) {
            for (int a = 0; a < replyInlineMarkup.rows.size(); a++) {
                ArrayList<TLRPC.KeyboardButton> row = replyInlineMarkup.rows.get(a).buttons;
                ButtonBot[] arr = new ButtonBot[row.size()];
                for (int b = 0; b < row.size(); b++) {
                    arr[b] = new ButtonBot(row.get(b));
                }
                buttons.add(arr);
            }
        }

        public void addSuggestionKeyboard() {
            buttons.add(new Button[]{
                new ButtonCustom(ButtonCustom.SUGGESTION_DECLINE, R.string.PostSuggestionsInlineDecline, R.drawable.filled_bot_decline_24),
                new ButtonCustom(ButtonCustom.SUGGESTION_ACCEPT, R.string.PostSuggestionsInlineAccept, R.drawable.filled_bot_approve_24),
            });
            buttons.add(new Button[]{
                    new ButtonCustom(ButtonCustom.SUGGESTION_EDIT, R.string.PostSuggestionsInlineEdit, R.drawable.filled_bot_suggest_24)
            });
        }

        public void addGiftOfferKeyboard() {
            buttons.add(new Button[]{
                new ButtonCustom(ButtonCustom.GIFT_OFFER_DECLINE, R.string.GiftOfferDecline, R.drawable.filled_bot_decline_24),
                new ButtonCustom(ButtonCustom.GIFT_OFFER_ACCEPT, R.string.GiftOfferAccept, R.drawable.filled_bot_approve_24),
            });
        }

        public void addContinueThreadKeyboard() {
            buttons.add(new Button[]{
                new ButtonCustom(ButtonCustom.OPEN_MESSAGE_THREAD, R.string.BotForumContinueChat, 0)
            });
        }

        public void addKeyboardSource(Source source) {
            if (source == null) {
                return;
            }

            int rows = source.getRowsCount();
            for (int a = 0; a < rows; a++) {
                Button[] buttons = new Button[source.getColumnsCount(a)];
                for (int b = 0; b < buttons.length; b++) {
                    buttons[b] = source.getButton(a, b);
                }
                this.buttons.add(buttons);
                if (source.hasSeparator(a)) {
                    addSeparator();
                }
            }
        }

        public void addSeparator() {
            if (!buttons.isEmpty()) {
                separators |= (1 << (buttons.size() - 1));
            }
        }

        public boolean isEmpty() {
            return buttons.isEmpty();
        }

        public boolean isNotEmpty() {
            return !buttons.isEmpty();
        }

        public Source build() {
            return new KeyboardSourceArray(buttons.toArray(new Button[buttons.size()][]), separators);
        }
    }
}
