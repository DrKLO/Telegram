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

    public static Source fromBot(TLRPC.TL_replyInlineMarkup replyInlineMarkup, boolean withSuggestion) {
        final Button[][] buttons = new Button[replyInlineMarkup.rows.size() + (withSuggestion ? 2 : 0)][];
        int separators = 0;
        for (int a = 0; a < replyInlineMarkup.rows.size(); a++) {
            ArrayList<TLRPC.KeyboardButton> row = replyInlineMarkup.rows.get(a).buttons;
            buttons[a] = new ButtonBot[row.size()];
            for (int b = 0; b < row.size(); b++) {
                buttons[a][b] = new ButtonBot(row.get(b));
            }
        }

        if (withSuggestion) {
            if (!replyInlineMarkup.rows.isEmpty()) {
                separators |= (1 << (replyInlineMarkup.rows.size() - 1));
            }

            buttons[replyInlineMarkup.rows.size()] = new Button[]{
                new ButtonCustom(ButtonCustom.SUGGESTION_DECLINE, R.string.PostSuggestionsInlineDecline, R.drawable.filled_bot_decline_24),
                new ButtonCustom(ButtonCustom.SUGGESTION_ACCEPT, R.string.PostSuggestionsInlineAccept, R.drawable.filled_bot_approve_24),
            };
            buttons[replyInlineMarkup.rows.size() + 1] = new Button[]{
                new ButtonCustom(ButtonCustom.SUGGESTION_EDIT, R.string.PostSuggestionsInlineEdit, R.drawable.filled_bot_suggest_24)
            };
        }

        return new KeyboardSourceArray(buttons, separators);
    }

    public static Source fromSuggestion() {
        return new KeyboardSourceArray(new Button[][]{
            new Button[]{
                new ButtonCustom(ButtonCustom.SUGGESTION_DECLINE, R.string.PostSuggestionsInlineDecline, R.drawable.filled_bot_decline_24),
                new ButtonCustom(ButtonCustom.SUGGESTION_ACCEPT, R.string.PostSuggestionsInlineAccept, R.drawable.filled_bot_approve_24),
            },
            new Button[]{
                new ButtonCustom(ButtonCustom.SUGGESTION_EDIT, R.string.PostSuggestionsInlineEdit, R.drawable.filled_bot_suggest_24)
            }
        }, 0);
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
}
