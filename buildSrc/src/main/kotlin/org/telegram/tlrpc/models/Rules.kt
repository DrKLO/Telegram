package org.telegram.tlrpc.models

object RULES {
    val rules = Rules(
        databaseTypes = setOf(
            "Message", "StoryItem", "Theme",
            "Chat", "User",
            "UserFull", "ChatFull",
            "StarsTransaction", "StarGift",
            "SavedStarGift", "InputStorePaymentPurpose", //, "ChatTheme"
            "ForumTopic"
        ),
        ignoredTypes = setOf(
            "True",
            "Vector t",
            "smsjobs.Status",
            "smsjobs.EligibilityToJoin",
            "SmsJob",

            "InputMessage",
            "BotBusinessConnection",
            "BotCommandScope",
            "CdnConfig",
            "CdnPublicKey",
            "InputBotInlineMessage",
            "InputBotInlineResult",
            "InputBusinessAwayMessage",
            "InputBusinessGreetingMessage",
            "RequestedPeer",
            "SavedContact",
            "messages.BotPreparedInlineMessage",
            "bots.BotInfo",
            "AutoSaveException",
            "AutoSaveSettings",
            "account.AutoSaveSettings",
            "account.Takeout",
            "InputClientProxy",
            "InputStarsTransaction",
            "InputStickerSetItem",
        ),
        ignoredConstructors = mapOf(
            "Update" to setOf(
                "updateAutoSaveSettings",
                "updateBotBusinessConnect",
                "updateBotCallbackQuery",
                "updateBotChatBoost",
                "updateBotChatInviteRequester",
                "updateBotDeleteBusinessMessage",
                "updateBotEditBusinessMessage",
                "updateBotInlineQuery",
                "updateBotInlineSend",
                "updateBotMessageReaction",
                "updateBotMessageReactions",
                "updateBotNewBusinessMessage",
                "updateBotPrecheckoutQuery",
                "updateBotShippingQuery",
                "updateBotStopped",
                "updateBotWebhookJSON",
                "updateBotWebhookJSONQuery",
                "updateBusinessBotCallbackQuery",
                "updateChatParticipant",
                "updateInlineBotCallbackQuery",
                "updatePtsChanged",
                "updateSmsJob"
            ),
            "MessageAction" to setOf(
                "messageActionRequestedPeerSentMe",
                "messageActionSecureValuesSentMe"
            ),
            "InputFileLocation" to setOf(
                "inputTakeoutFileLocation",
                "inputPhotoLegacyFileLocation"
            ),
            "KeyboardButton" to setOf("inputKeyboardButtonRequestPeer"),
            "InputPaymentCredentials" to setOf("inputPaymentCredentialsApplePay"),
            "InputStickerSet" to setOf("inputStickerSetAnimatedEmojiAnimations"),
            "InputWebFileLocation" to setOf("inputWebFileAudioAlbumThumbLocation"),
            "InputInvoice" to setOf("inputInvoiceBusinessBotTransferStars"),
            "InputBotInlineMessageID" to setOf("inputBotInlineMessageID64"),
            "InputPasskeyCredential" to setOf("inputPasskeyCredentialFirebasePNV"),
        )
    )
}

data class Rules(
    val databaseTypes: Set<String>,
    val ignoredTypes: Set<String>,
    val ignoredConstructors: Map<String, Set<String>>,
) {
    fun filterConstructor(key: TlTypeName): Boolean {
        if (key.type in ignoredTypes) {
            return false
        }

        val ignored = ignoredConstructors[key.type] ?: emptySet()
        if (key.predicate in ignored) {
            return false
        }

        return true
    }
}