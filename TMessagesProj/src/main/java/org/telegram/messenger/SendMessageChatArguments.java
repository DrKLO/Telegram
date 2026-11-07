package org.telegram.messenger;

public class SendMessageChatArguments {
    public static final SendMessageChatArguments EMPTY = new SendMessageChatArguments.Builder().build();

    public final long welcomeMessageChatId;
    public final String quickReplyShortcut;
    public final int quickReplyShortcutId;

    private SendMessageChatArguments(Builder builder) {
        this.welcomeMessageChatId = builder.welcomeMessageChatId;
        this.quickReplyShortcut = builder.quickReplyShortcut;
        this.quickReplyShortcutId = builder.quickReplyShortcutId;
    }

    public static class Builder {
        private long welcomeMessageChatId;
        private String quickReplyShortcut;
        private int quickReplyShortcutId;

        public void setWelcomeMessageChatId(long welcomeMessageChatId) {
            this.welcomeMessageChatId = welcomeMessageChatId;
        }

        public void setQuickReplyShortcut(String quickReplyShortcut, int quickReplyShortcutId) {
            this.quickReplyShortcut = quickReplyShortcut;
            this.quickReplyShortcutId = quickReplyShortcutId;
        }

        public SendMessageChatArguments build() {
            return new SendMessageChatArguments(this);
        }
    }
}
