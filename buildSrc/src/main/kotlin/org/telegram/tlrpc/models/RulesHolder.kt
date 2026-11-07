package org.telegram.tlrpc.models

object RulesHolder {
    val rules = Rules(
        databaseTypes = setOf(
            "Message", "StoryItem", "Theme",
            "Chat", "User",
            "UserFull", "ChatFull",
            "StarsTransaction", "StarGift",
            "SavedStarGift", "InputStorePaymentPurpose", //, "ChatTheme"
            "ForumTopic", "EphemeralMessage"
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
            "bots.AccessSettings"
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
                "updateSmsJob",
                "updateBotGuestChatQuery",
                "updateBotStarsSubscription"
            ),
            "MessageAction" to setOf(
                "messageActionRequestedPeerSentMe",
                "messageActionSecureValuesSentMe"
            ),
            "InputFileLocation" to setOf(
                "inputTakeoutFileLocation",
                "inputPhotoLegacyFileLocation"
            ),
            "InputPaymentCredentials" to setOf("inputPaymentCredentialsApplePay"),
            "InputStickerSet" to setOf("inputStickerSetAnimatedEmojiAnimations"),
            "InputWebFileLocation" to setOf("inputWebFileAudioAlbumThumbLocation"),
            "InputInvoice" to setOf("inputInvoiceBusinessBotTransferStars"),
            "InputBotInlineMessageID" to setOf("inputBotInlineMessageID64"),
            "InputPasskeyCredential" to setOf("inputPasskeyCredentialFirebasePNV"),
            "InputRichMessage" to setOf("inputRichMessageHTML", "inputRichMessageMarkdown"),
            "SendMessageAction" to setOf("inputSendMessageRichMessageDraftAction")
        )
    )
}
