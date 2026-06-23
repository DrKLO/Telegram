package org.Tajgram.tgnet.test.generated

import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.experimental.runners.Enclosed
import org.junit.runner.RunWith
import org.Tajgram.tgnet.model.TlGen_Object
import org.Tajgram.tgnet.model.TlGen_Vector
import org.Tajgram.tgnet.test.BaseSchemeTest

@RunWith(Enclosed::class)
public class Test_All {
  public class Test_Actual : BaseSchemeTest() {
    @Test
    public fun test_000000_AccountDaysTTL_TL_accountDaysTTL() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_AccountDaysTTL.TL_accountDaysTTL::class,
          org.Tajgram.tgnet.TLRPC.TL_accountDaysTTL::TLdeserialize, null)

    }

    @Test
    public fun test_000001_AiComposeTone_TL_aiComposeTone() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_AiComposeTone.TL_aiComposeTone::class,
          org.Tajgram.tgnet.tl.TL_aicompose.AiComposeTone::TLdeserialize, null)

    }

    @Test
    public fun test_000002_AiComposeTone_TL_aiComposeToneDefault() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_AiComposeTone.TL_aiComposeToneDefault::class,
          org.Tajgram.tgnet.tl.TL_aicompose.AiComposeTone::TLdeserialize, null)

    }

    @Test
    public fun test_000003_AiComposeToneExample_TL_aiComposeToneExample() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_AiComposeToneExample.TL_aiComposeToneExample::class,
          org.Tajgram.tgnet.tl.TL_aicompose.aiComposeToneExample::TLdeserialize, null)

    }

    @Test
    public fun test_000004_AttachMenuBot_TL_attachMenuBot() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_AttachMenuBot.TL_attachMenuBot::class,
          org.Tajgram.tgnet.TLRPC.TL_attachMenuBot_layer140::TLdeserialize, null)
          test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_AttachMenuBot.TL_attachMenuBot::class,
              org.Tajgram.tgnet.TLRPC.AttachMenuBot::TLdeserialize, null)

    }

    @Test
    public fun test_000005_AttachMenuBotIcon_TL_attachMenuBotIcon() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_AttachMenuBotIcon.TL_attachMenuBotIcon::class,
          org.Tajgram.tgnet.TLRPC.TL_attachMenuBotIcon::TLdeserialize, null)

    }

    @Test
    public fun test_000006_AttachMenuBotIconColor_TL_attachMenuBotIconColor() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_AttachMenuBotIconColor.TL_attachMenuBotIconColor::class,
          org.Tajgram.tgnet.TLRPC.TL_attachMenuBotIconColor::TLdeserialize, null)

    }

    @Test
    public fun test_000007_AttachMenuBots_TL_attachMenuBots() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_AttachMenuBots.TL_attachMenuBots::class,
          org.Tajgram.tgnet.TLRPC.AttachMenuBots::TLdeserialize, null)

    }

    @Test
    public fun test_000008_AttachMenuBots_TL_attachMenuBotsNotModified() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_AttachMenuBots.TL_attachMenuBotsNotModified::class,
          org.Tajgram.tgnet.TLRPC.AttachMenuBots::TLdeserialize, null)

    }

    @Test
    public fun test_000009_AttachMenuBotsBot_TL_attachMenuBotsBot() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_AttachMenuBotsBot.TL_attachMenuBotsBot::class,
          org.Tajgram.tgnet.TLRPC.TL_attachMenuBotsBot::TLdeserialize, null)

    }

    @Test
    public fun test_000010_AttachMenuPeerType_TL_attachMenuPeerTypeBotPM() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_AttachMenuPeerType.TL_attachMenuPeerTypeBotPM::class,
          org.Tajgram.tgnet.TLRPC.AttachMenuPeerType::TLdeserialize, null)

    }

    @Test
    public fun test_000011_AttachMenuPeerType_TL_attachMenuPeerTypeBroadcast() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_AttachMenuPeerType.TL_attachMenuPeerTypeBroadcast::class,
          org.Tajgram.tgnet.TLRPC.AttachMenuPeerType::TLdeserialize, null)

    }

    @Test
    public fun test_000012_AttachMenuPeerType_TL_attachMenuPeerTypeChat() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_AttachMenuPeerType.TL_attachMenuPeerTypeChat::class,
          org.Tajgram.tgnet.TLRPC.AttachMenuPeerType::TLdeserialize, null)

    }

    @Test
    public fun test_000013_AttachMenuPeerType_TL_attachMenuPeerTypePM() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_AttachMenuPeerType.TL_attachMenuPeerTypePM::class,
          org.Tajgram.tgnet.TLRPC.AttachMenuPeerType::TLdeserialize, null)

    }

    @Test
    public fun test_000014_AttachMenuPeerType_TL_attachMenuPeerTypeSameBotPM() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_AttachMenuPeerType.TL_attachMenuPeerTypeSameBotPM::class,
          org.Tajgram.tgnet.TLRPC.AttachMenuPeerType::TLdeserialize, null)

    }

    @Test
    public fun test_000015_AuctionBidLevel_TL_auctionBidLevel() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_AuctionBidLevel.TL_auctionBidLevel::class,
          org.Tajgram.tgnet.tl.TL_stars.TL_AuctionBidLevel::TLdeserialize, null)

    }

    @Test
    public fun test_000016_Authorization_TL_authorization() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Authorization.TL_authorization::class,
          org.Tajgram.tgnet.TLRPC.TL_authorization::TLdeserialize, null)

    }

    @Test
    public fun test_000017_AutoDownloadSettings_TL_autoDownloadSettings() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_AutoDownloadSettings.TL_autoDownloadSettings::class,
          org.Tajgram.tgnet.TLRPC.TL_autoDownloadSettings::TLdeserialize, null)

    }

    @Test
    public fun test_000018_AvailableEffect_TL_availableEffect() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_AvailableEffect.TL_availableEffect::class,
          org.Tajgram.tgnet.TLRPC.TL_availableEffect::TLdeserialize, null)

    }

    @Test
    public fun test_000019_AvailableReaction_TL_availableReaction() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_AvailableReaction.TL_availableReaction::class,
          org.Tajgram.tgnet.TLRPC.TL_availableReaction::TLdeserialize, null)

    }

    @Test
    public fun test_000020_BankCardOpenUrl_TL_bankCardOpenUrl() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_BankCardOpenUrl.TL_bankCardOpenUrl::class,
          org.Tajgram.tgnet.TLRPC.TL_bankCardOpenUrl::TLdeserialize, null)

    }

    @Test
    public fun test_000021_BaseTheme_TL_baseThemeArctic() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_BaseTheme.TL_baseThemeArctic::class,
          org.Tajgram.tgnet.TLRPC.BaseTheme::TLdeserialize, null)

    }

    @Test
    public fun test_000022_BaseTheme_TL_baseThemeClassic() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_BaseTheme.TL_baseThemeClassic::class,
          org.Tajgram.tgnet.TLRPC.BaseTheme::TLdeserialize, null)

    }

    @Test
    public fun test_000023_BaseTheme_TL_baseThemeDay() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_BaseTheme.TL_baseThemeDay::class,
          org.Tajgram.tgnet.TLRPC.BaseTheme::TLdeserialize, null)

    }

    @Test
    public fun test_000024_BaseTheme_TL_baseThemeNight() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_BaseTheme.TL_baseThemeNight::class,
          org.Tajgram.tgnet.TLRPC.BaseTheme::TLdeserialize, null)

    }

    @Test
    public fun test_000025_BaseTheme_TL_baseThemeTinted() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_BaseTheme.TL_baseThemeTinted::class,
          org.Tajgram.tgnet.TLRPC.BaseTheme::TLdeserialize, null)

    }

    @Test
    public fun test_000026_Birthday_TL_birthday() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Birthday.TL_birthday::class,
          org.Tajgram.tgnet.tl.TL_account.TL_birthday::TLdeserialize, null)

    }

    @Test
    public fun test_000027_Bool_TL_boolFalse() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Bool.TL_boolFalse::class,
          org.Tajgram.tgnet.TLRPC.Bool::TLdeserialize, null)

    }

    @Test
    public fun test_000028_Bool_TL_boolTrue() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Bool.TL_boolTrue::class,
          org.Tajgram.tgnet.TLRPC.Bool::TLdeserialize, null)

    }

    @Test
    public fun test_000029_Boost_TL_boost() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Boost.TL_boost::class,
          org.Tajgram.tgnet.tl.TL_stories.Boost::TLdeserialize, null)

    }

    @Test
    public fun test_000030_BotApp_TL_botApp() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_BotApp.TL_botApp::class,
          org.Tajgram.tgnet.TLRPC.BotApp::TLdeserialize, null)

    }

    @Test
    public fun test_000031_BotApp_TL_botAppNotModified() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_BotApp.TL_botAppNotModified::class,
          org.Tajgram.tgnet.TLRPC.BotApp::TLdeserialize, null)

    }

    @Test
    public fun test_000032_BotAppSettings_TL_botAppSettings() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_BotAppSettings.TL_botAppSettings::class,
          org.Tajgram.tgnet.tl.TL_bots.botAppSettings::TLdeserialize, null)

    }

    @Test
    public fun test_000033_BotCommand_TL_botCommand() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_BotCommand.TL_botCommand::class,
          org.Tajgram.tgnet.TLRPC.TL_botCommand::TLdeserialize, null)

    }

    @Test
    public fun test_000034_BotInfo_TL_botInfo() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_BotInfo.TL_botInfo::class,
          org.Tajgram.tgnet.tl.TL_bots.BotInfo::TLdeserialize, null)

    }

    @Test
    public fun test_000035_BotInlineMessage_TL_botInlineMessageMediaAuto() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_BotInlineMessage.TL_botInlineMessageMediaAuto::class,
          org.Tajgram.tgnet.TLRPC.BotInlineMessage::TLdeserialize, null)

    }

    @Test
    public fun test_000036_BotInlineMessage_TL_botInlineMessageMediaContact() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_BotInlineMessage.TL_botInlineMessageMediaContact::class,
          org.Tajgram.tgnet.TLRPC.BotInlineMessage::TLdeserialize, null)

    }

    @Test
    public fun test_000037_BotInlineMessage_TL_botInlineMessageMediaGeo() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_BotInlineMessage.TL_botInlineMessageMediaGeo::class,
          org.Tajgram.tgnet.TLRPC.BotInlineMessage::TLdeserialize, null)

    }

    @Test
    public fun test_000038_BotInlineMessage_TL_botInlineMessageMediaInvoice() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_BotInlineMessage.TL_botInlineMessageMediaInvoice::class,
          org.Tajgram.tgnet.TLRPC.BotInlineMessage::TLdeserialize, null)

    }

    @Test
    public fun test_000039_BotInlineMessage_TL_botInlineMessageMediaVenue() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_BotInlineMessage.TL_botInlineMessageMediaVenue::class,
          org.Tajgram.tgnet.TLRPC.BotInlineMessage::TLdeserialize, null)

    }

    @Test
    public fun test_000040_BotInlineMessage_TL_botInlineMessageMediaWebPage() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_BotInlineMessage.TL_botInlineMessageMediaWebPage::class,
          org.Tajgram.tgnet.TLRPC.BotInlineMessage::TLdeserialize, null)

    }

    @Test
    public fun test_000041_BotInlineMessage_TL_botInlineMessageRichMessage() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_BotInlineMessage.TL_botInlineMessageRichMessage::class,
          org.Tajgram.tgnet.TLRPC.BotInlineMessage::TLdeserialize, null)

    }

    @Test
    public fun test_000042_BotInlineMessage_TL_botInlineMessageText() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_BotInlineMessage.TL_botInlineMessageText::class,
          org.Tajgram.tgnet.TLRPC.BotInlineMessage::TLdeserialize, null)

    }

    @Test
    public fun test_000043_BotInlineResult_TL_botInlineMediaResult() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_BotInlineResult.TL_botInlineMediaResult::class,
          org.Tajgram.tgnet.TLRPC.BotInlineResult::TLdeserialize, null)

    }

    @Test
    public fun test_000044_BotInlineResult_TL_botInlineResult() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_BotInlineResult.TL_botInlineResult::class,
          org.Tajgram.tgnet.TLRPC.BotInlineResult::TLdeserialize, null)

    }

    @Test
    public fun test_000045_BotMenuButton_TL_botMenuButton() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_BotMenuButton.TL_botMenuButton::class,
          org.Tajgram.tgnet.tl.TL_bots.BotMenuButton::TLdeserialize, null)

    }

    @Test
    public fun test_000046_BotMenuButton_TL_botMenuButtonCommands() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_BotMenuButton.TL_botMenuButtonCommands::class,
          org.Tajgram.tgnet.tl.TL_bots.BotMenuButton::TLdeserialize, null)

    }

    @Test
    public fun test_000047_BotMenuButton_TL_botMenuButtonDefault() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_BotMenuButton.TL_botMenuButtonDefault::class,
          org.Tajgram.tgnet.tl.TL_bots.BotMenuButton::TLdeserialize, null)

    }

    @Test
    public fun test_000048_BotPreviewMedia_TL_botPreviewMedia() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_BotPreviewMedia.TL_botPreviewMedia::class,
          org.Tajgram.tgnet.tl.TL_bots.botPreviewMedia::TLdeserialize, null)

    }

    @Test
    public fun test_000049_BotVerification_TL_botVerification() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_BotVerification.TL_botVerification::class,
          org.Tajgram.tgnet.tl.TL_bots.botVerification::TLdeserialize, null)

    }

    @Test
    public fun test_000050_BotVerifierSettings_TL_botVerifierSettings() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_BotVerifierSettings.TL_botVerifierSettings::class,
          org.Tajgram.tgnet.tl.TL_bots.botVerifierSettings::TLdeserialize, null)

    }

    @Test
    public fun test_000051_BusinessAwayMessage_TL_businessAwayMessage() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_BusinessAwayMessage.TL_businessAwayMessage::class,
          org.Tajgram.tgnet.tl.TL_account.TL_businessAwayMessage::TLdeserialize, null)

    }

    @Test
    public fun test_000052_BusinessAwayMessageSchedule_TL_businessAwayMessageScheduleAlways() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_BusinessAwayMessageSchedule.TL_businessAwayMessageScheduleAlways::class,
          org.Tajgram.tgnet.tl.TL_account.BusinessAwayMessageSchedule::TLdeserialize, null)

    }

    @Test
    public fun test_000053_BusinessAwayMessageSchedule_TL_businessAwayMessageScheduleCustom() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_BusinessAwayMessageSchedule.TL_businessAwayMessageScheduleCustom::class,
          org.Tajgram.tgnet.tl.TL_account.BusinessAwayMessageSchedule::TLdeserialize, null)

    }

    @Test
    public
        fun test_000054_BusinessAwayMessageSchedule_TL_businessAwayMessageScheduleOutsideWorkHours() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_BusinessAwayMessageSchedule.TL_businessAwayMessageScheduleOutsideWorkHours::class,
          org.Tajgram.tgnet.tl.TL_account.BusinessAwayMessageSchedule::TLdeserialize, null)

    }

    @Test
    public fun test_000055_BusinessBotRecipients_TL_businessBotRecipients() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_BusinessBotRecipients.TL_businessBotRecipients::class,
          org.Tajgram.tgnet.tl.TL_account.TL_businessBotRecipients::TLdeserialize, null)

    }

    @Test
    public fun test_000056_BusinessBotRights_TL_businessBotRights() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_BusinessBotRights.TL_businessBotRights::class,
          org.Tajgram.tgnet.tl.TL_account.TL_businessBotRights::TLdeserialize, null)

    }

    @Test
    public fun test_000057_BusinessChatLink_TL_businessChatLink() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_BusinessChatLink.TL_businessChatLink::class,
          org.Tajgram.tgnet.tl.TL_account.TL_businessChatLink::TLdeserialize, null)

    }

    @Test
    public fun test_000058_BusinessGreetingMessage_TL_businessGreetingMessage() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_BusinessGreetingMessage.TL_businessGreetingMessage::class,
          org.Tajgram.tgnet.tl.TL_account.TL_businessGreetingMessage::TLdeserialize, null)

    }

    @Test
    public fun test_000059_BusinessIntro_TL_businessIntro() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_BusinessIntro.TL_businessIntro::class,
          org.Tajgram.tgnet.tl.TL_account.TL_businessIntro::TLdeserialize, null)

    }

    @Test
    public fun test_000060_BusinessLocation_TL_businessLocation() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_BusinessLocation.TL_businessLocation::class,
          org.Tajgram.tgnet.TLRPC.TL_businessLocation::TLdeserialize, null)

    }

    @Test
    public fun test_000061_BusinessRecipients_TL_businessRecipients() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_BusinessRecipients.TL_businessRecipients::class,
          org.Tajgram.tgnet.tl.TL_account.TL_businessRecipients::TLdeserialize, null)

    }

    @Test
    public fun test_000062_BusinessWeeklyOpen_TL_businessWeeklyOpen() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_BusinessWeeklyOpen.TL_businessWeeklyOpen::class,
          org.Tajgram.tgnet.tl.TL_account.TL_businessWeeklyOpen::TLdeserialize, null)

    }

    @Test
    public fun test_000063_BusinessWorkHours_TL_businessWorkHours() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_BusinessWorkHours.TL_businessWorkHours::class,
          org.Tajgram.tgnet.tl.TL_account.TL_businessWorkHours::TLdeserialize, null)

    }

    @Test
    public fun test_000064_ChannelAdminLogEvent_TL_channelAdminLogEvent() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ChannelAdminLogEvent.TL_channelAdminLogEvent::class,
          org.Tajgram.tgnet.TLRPC.TL_channelAdminLogEvent::TLdeserialize, null)

    }

    @Test
    public fun test_000065_ChannelAdminLogEventAction_TL_channelAdminLogEventActionChangeAbout() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ChannelAdminLogEventAction.TL_channelAdminLogEventActionChangeAbout::class,
          org.Tajgram.tgnet.TLRPC.ChannelAdminLogEventAction::TLdeserialize, null)

    }

    @Test
    public
        fun test_000066_ChannelAdminLogEventAction_TL_channelAdminLogEventActionChangeAvailableReactions() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ChannelAdminLogEventAction.TL_channelAdminLogEventActionChangeAvailableReactions::class,
          org.Tajgram.tgnet.TLRPC.ChannelAdminLogEventAction::TLdeserialize, null)

    }

    @Test
    public
        fun test_000067_ChannelAdminLogEventAction_TL_channelAdminLogEventActionChangeEmojiStatus() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ChannelAdminLogEventAction.TL_channelAdminLogEventActionChangeEmojiStatus::class,
          org.Tajgram.tgnet.TLRPC.ChannelAdminLogEventAction::TLdeserialize, null)

    }

    @Test
    public
        fun test_000068_ChannelAdminLogEventAction_TL_channelAdminLogEventActionChangeEmojiStickerSet() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ChannelAdminLogEventAction.TL_channelAdminLogEventActionChangeEmojiStickerSet::class,
          org.Tajgram.tgnet.TLRPC.ChannelAdminLogEventAction::TLdeserialize, null)

    }

    @Test
    public
        fun test_000069_ChannelAdminLogEventAction_TL_channelAdminLogEventActionChangeHistoryTTL() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ChannelAdminLogEventAction.TL_channelAdminLogEventActionChangeHistoryTTL::class,
          org.Tajgram.tgnet.TLRPC.ChannelAdminLogEventAction::TLdeserialize, null)

    }

    @Test
    public
        fun test_000070_ChannelAdminLogEventAction_TL_channelAdminLogEventActionChangeLinkedChat() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ChannelAdminLogEventAction.TL_channelAdminLogEventActionChangeLinkedChat::class,
          org.Tajgram.tgnet.TLRPC.ChannelAdminLogEventAction::TLdeserialize, null)

    }

    @Test
    public
        fun test_000071_ChannelAdminLogEventAction_TL_channelAdminLogEventActionChangeLocation() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ChannelAdminLogEventAction.TL_channelAdminLogEventActionChangeLocation::class,
          org.Tajgram.tgnet.TLRPC.ChannelAdminLogEventAction::TLdeserialize, null)

    }

    @Test
    public
        fun test_000072_ChannelAdminLogEventAction_TL_channelAdminLogEventActionChangePeerColor() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ChannelAdminLogEventAction.TL_channelAdminLogEventActionChangePeerColor::class,
          org.Tajgram.tgnet.TLRPC.ChannelAdminLogEventAction::TLdeserialize, null)

    }

    @Test
    public fun test_000073_ChannelAdminLogEventAction_TL_channelAdminLogEventActionChangePhoto() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ChannelAdminLogEventAction.TL_channelAdminLogEventActionChangePhoto::class,
          org.Tajgram.tgnet.TLRPC.ChannelAdminLogEventAction::TLdeserialize, null)

    }

    @Test
    public
        fun test_000074_ChannelAdminLogEventAction_TL_channelAdminLogEventActionChangeProfilePeerColor() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ChannelAdminLogEventAction.TL_channelAdminLogEventActionChangeProfilePeerColor::class,
          org.Tajgram.tgnet.TLRPC.ChannelAdminLogEventAction::TLdeserialize, null)

    }

    @Test
    public
        fun test_000075_ChannelAdminLogEventAction_TL_channelAdminLogEventActionChangeStickerSet() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ChannelAdminLogEventAction.TL_channelAdminLogEventActionChangeStickerSet::class,
          org.Tajgram.tgnet.TLRPC.ChannelAdminLogEventAction::TLdeserialize, null)

    }

    @Test
    public fun test_000076_ChannelAdminLogEventAction_TL_channelAdminLogEventActionChangeTitle() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ChannelAdminLogEventAction.TL_channelAdminLogEventActionChangeTitle::class,
          org.Tajgram.tgnet.TLRPC.ChannelAdminLogEventAction::TLdeserialize, null)

    }

    @Test
    public
        fun test_000077_ChannelAdminLogEventAction_TL_channelAdminLogEventActionChangeUsername() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ChannelAdminLogEventAction.TL_channelAdminLogEventActionChangeUsername::class,
          org.Tajgram.tgnet.TLRPC.ChannelAdminLogEventAction::TLdeserialize, null)

    }

    @Test
    public
        fun test_000078_ChannelAdminLogEventAction_TL_channelAdminLogEventActionChangeUsernames() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ChannelAdminLogEventAction.TL_channelAdminLogEventActionChangeUsernames::class,
          org.Tajgram.tgnet.TLRPC.ChannelAdminLogEventAction::TLdeserialize, null)

    }

    @Test
    public
        fun test_000079_ChannelAdminLogEventAction_TL_channelAdminLogEventActionChangeWallpaper() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ChannelAdminLogEventAction.TL_channelAdminLogEventActionChangeWallpaper::class,
          org.Tajgram.tgnet.TLRPC.ChannelAdminLogEventAction::TLdeserialize, null)

    }

    @Test
    public fun test_000080_ChannelAdminLogEventAction_TL_channelAdminLogEventActionCreateTopic() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ChannelAdminLogEventAction.TL_channelAdminLogEventActionCreateTopic::class,
          org.Tajgram.tgnet.TLRPC.ChannelAdminLogEventAction::TLdeserialize, null)

    }

    @Test
    public
        fun test_000081_ChannelAdminLogEventAction_TL_channelAdminLogEventActionDefaultBannedRights() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ChannelAdminLogEventAction.TL_channelAdminLogEventActionDefaultBannedRights::class,
          org.Tajgram.tgnet.TLRPC.ChannelAdminLogEventAction::TLdeserialize, null)

    }

    @Test
    public fun test_000082_ChannelAdminLogEventAction_TL_channelAdminLogEventActionDeleteMessage() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ChannelAdminLogEventAction.TL_channelAdminLogEventActionDeleteMessage::class,
          org.Tajgram.tgnet.TLRPC.ChannelAdminLogEventAction::TLdeserialize, null)

    }

    @Test
    public fun test_000083_ChannelAdminLogEventAction_TL_channelAdminLogEventActionDeleteTopic() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ChannelAdminLogEventAction.TL_channelAdminLogEventActionDeleteTopic::class,
          org.Tajgram.tgnet.TLRPC.ChannelAdminLogEventAction::TLdeserialize, null)

    }

    @Test
    public
        fun test_000084_ChannelAdminLogEventAction_TL_channelAdminLogEventActionDiscardGroupCall() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ChannelAdminLogEventAction.TL_channelAdminLogEventActionDiscardGroupCall::class,
          org.Tajgram.tgnet.TLRPC.ChannelAdminLogEventAction::TLdeserialize, null)

    }

    @Test
    public fun test_000085_ChannelAdminLogEventAction_TL_channelAdminLogEventActionEditMessage() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ChannelAdminLogEventAction.TL_channelAdminLogEventActionEditMessage::class,
          org.Tajgram.tgnet.TLRPC.ChannelAdminLogEventAction::TLdeserialize, null)

    }

    @Test
    public fun test_000086_ChannelAdminLogEventAction_TL_channelAdminLogEventActionEditTopic() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ChannelAdminLogEventAction.TL_channelAdminLogEventActionEditTopic::class,
          org.Tajgram.tgnet.TLRPC.ChannelAdminLogEventAction::TLdeserialize, null)

    }

    @Test
    public
        fun test_000087_ChannelAdminLogEventAction_TL_channelAdminLogEventActionExportedInviteDelete() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ChannelAdminLogEventAction.TL_channelAdminLogEventActionExportedInviteDelete::class,
          org.Tajgram.tgnet.TLRPC.ChannelAdminLogEventAction::TLdeserialize, null)

    }

    @Test
    public
        fun test_000088_ChannelAdminLogEventAction_TL_channelAdminLogEventActionExportedInviteEdit() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ChannelAdminLogEventAction.TL_channelAdminLogEventActionExportedInviteEdit::class,
          org.Tajgram.tgnet.TLRPC.ChannelAdminLogEventAction::TLdeserialize, null)

    }

    @Test
    public
        fun test_000089_ChannelAdminLogEventAction_TL_channelAdminLogEventActionExportedInviteRevoke() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ChannelAdminLogEventAction.TL_channelAdminLogEventActionExportedInviteRevoke::class,
          org.Tajgram.tgnet.TLRPC.ChannelAdminLogEventAction::TLdeserialize, null)

    }

    @Test
    public
        fun test_000090_ChannelAdminLogEventAction_TL_channelAdminLogEventActionParticipantEditRank() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ChannelAdminLogEventAction.TL_channelAdminLogEventActionParticipantEditRank::class,
          org.Tajgram.tgnet.TLRPC.ChannelAdminLogEventAction::TLdeserialize, null)

    }

    @Test
    public
        fun test_000091_ChannelAdminLogEventAction_TL_channelAdminLogEventActionParticipantInvite() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ChannelAdminLogEventAction.TL_channelAdminLogEventActionParticipantInvite::class,
          org.Tajgram.tgnet.TLRPC.ChannelAdminLogEventAction::TLdeserialize, null)

    }

    @Test
    public
        fun test_000092_ChannelAdminLogEventAction_TL_channelAdminLogEventActionParticipantJoin() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ChannelAdminLogEventAction.TL_channelAdminLogEventActionParticipantJoin::class,
          org.Tajgram.tgnet.TLRPC.ChannelAdminLogEventAction::TLdeserialize, null)

    }

    @Test
    public
        fun test_000093_ChannelAdminLogEventAction_TL_channelAdminLogEventActionParticipantJoinByInvite() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ChannelAdminLogEventAction.TL_channelAdminLogEventActionParticipantJoinByInvite::class,
          org.Tajgram.tgnet.TLRPC.ChannelAdminLogEventAction::TLdeserialize, null)

    }

    @Test
    public
        fun test_000094_ChannelAdminLogEventAction_TL_channelAdminLogEventActionParticipantJoinByRequest() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ChannelAdminLogEventAction.TL_channelAdminLogEventActionParticipantJoinByRequest::class,
          org.Tajgram.tgnet.TLRPC.ChannelAdminLogEventAction::TLdeserialize, null)

    }

    @Test
    public
        fun test_000095_ChannelAdminLogEventAction_TL_channelAdminLogEventActionParticipantLeave() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ChannelAdminLogEventAction.TL_channelAdminLogEventActionParticipantLeave::class,
          org.Tajgram.tgnet.TLRPC.ChannelAdminLogEventAction::TLdeserialize, null)

    }

    @Test
    public
        fun test_000096_ChannelAdminLogEventAction_TL_channelAdminLogEventActionParticipantMute() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ChannelAdminLogEventAction.TL_channelAdminLogEventActionParticipantMute::class,
          org.Tajgram.tgnet.TLRPC.ChannelAdminLogEventAction::TLdeserialize, null)

    }

    @Test
    public
        fun test_000097_ChannelAdminLogEventAction_TL_channelAdminLogEventActionParticipantSubExtend() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ChannelAdminLogEventAction.TL_channelAdminLogEventActionParticipantSubExtend::class,
          org.Tajgram.tgnet.TLRPC.ChannelAdminLogEventAction::TLdeserialize, null)

    }

    @Test
    public
        fun test_000098_ChannelAdminLogEventAction_TL_channelAdminLogEventActionParticipantToggleAdmin() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ChannelAdminLogEventAction.TL_channelAdminLogEventActionParticipantToggleAdmin::class,
          org.Tajgram.tgnet.TLRPC.ChannelAdminLogEventAction::TLdeserialize, null)

    }

    @Test
    public
        fun test_000099_ChannelAdminLogEventAction_TL_channelAdminLogEventActionParticipantToggleBan() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ChannelAdminLogEventAction.TL_channelAdminLogEventActionParticipantToggleBan::class,
          org.Tajgram.tgnet.TLRPC.ChannelAdminLogEventAction::TLdeserialize, null)

    }

    @Test
    public
        fun test_000100_ChannelAdminLogEventAction_TL_channelAdminLogEventActionParticipantUnmute() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ChannelAdminLogEventAction.TL_channelAdminLogEventActionParticipantUnmute::class,
          org.Tajgram.tgnet.TLRPC.ChannelAdminLogEventAction::TLdeserialize, null)

    }

    @Test
    public
        fun test_000101_ChannelAdminLogEventAction_TL_channelAdminLogEventActionParticipantVolume() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ChannelAdminLogEventAction.TL_channelAdminLogEventActionParticipantVolume::class,
          org.Tajgram.tgnet.TLRPC.ChannelAdminLogEventAction::TLdeserialize, null)

    }

    @Test
    public fun test_000102_ChannelAdminLogEventAction_TL_channelAdminLogEventActionPinTopic() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ChannelAdminLogEventAction.TL_channelAdminLogEventActionPinTopic::class,
          org.Tajgram.tgnet.TLRPC.ChannelAdminLogEventAction::TLdeserialize, null)

    }

    @Test
    public fun test_000103_ChannelAdminLogEventAction_TL_channelAdminLogEventActionSendMessage() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ChannelAdminLogEventAction.TL_channelAdminLogEventActionSendMessage::class,
          org.Tajgram.tgnet.TLRPC.ChannelAdminLogEventAction::TLdeserialize, null)

    }

    @Test
    public
        fun test_000104_ChannelAdminLogEventAction_TL_channelAdminLogEventActionStartGroupCall() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ChannelAdminLogEventAction.TL_channelAdminLogEventActionStartGroupCall::class,
          org.Tajgram.tgnet.TLRPC.ChannelAdminLogEventAction::TLdeserialize, null)

    }

    @Test
    public fun test_000105_ChannelAdminLogEventAction_TL_channelAdminLogEventActionStopPoll() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ChannelAdminLogEventAction.TL_channelAdminLogEventActionStopPoll::class,
          org.Tajgram.tgnet.TLRPC.ChannelAdminLogEventAction::TLdeserialize, null)

    }

    @Test
    public
        fun test_000106_ChannelAdminLogEventAction_TL_channelAdminLogEventActionToggleAntiSpam() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ChannelAdminLogEventAction.TL_channelAdminLogEventActionToggleAntiSpam::class,
          org.Tajgram.tgnet.TLRPC.ChannelAdminLogEventAction::TLdeserialize, null)

    }

    @Test
    public
        fun test_000107_ChannelAdminLogEventAction_TL_channelAdminLogEventActionToggleAutotranslation() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ChannelAdminLogEventAction.TL_channelAdminLogEventActionToggleAutotranslation::class,
          org.Tajgram.tgnet.TLRPC.ChannelAdminLogEventAction::TLdeserialize, null)

    }

    @Test
    public fun test_000108_ChannelAdminLogEventAction_TL_channelAdminLogEventActionToggleForum() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ChannelAdminLogEventAction.TL_channelAdminLogEventActionToggleForum::class,
          org.Tajgram.tgnet.TLRPC.ChannelAdminLogEventAction::TLdeserialize, null)

    }

    @Test
    public
        fun test_000109_ChannelAdminLogEventAction_TL_channelAdminLogEventActionToggleGroupCallSetting() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ChannelAdminLogEventAction.TL_channelAdminLogEventActionToggleGroupCallSetting::class,
          org.Tajgram.tgnet.TLRPC.ChannelAdminLogEventAction::TLdeserialize, null)

    }

    @Test
    public fun test_000110_ChannelAdminLogEventAction_TL_channelAdminLogEventActionToggleInvites() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ChannelAdminLogEventAction.TL_channelAdminLogEventActionToggleInvites::class,
          org.Tajgram.tgnet.TLRPC.ChannelAdminLogEventAction::TLdeserialize, null)

    }

    @Test
    public
        fun test_000111_ChannelAdminLogEventAction_TL_channelAdminLogEventActionToggleNoForwards() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ChannelAdminLogEventAction.TL_channelAdminLogEventActionToggleNoForwards::class,
          org.Tajgram.tgnet.TLRPC.ChannelAdminLogEventAction::TLdeserialize, null)

    }

    @Test
    public
        fun test_000112_ChannelAdminLogEventAction_TL_channelAdminLogEventActionTogglePreHistoryHidden() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ChannelAdminLogEventAction.TL_channelAdminLogEventActionTogglePreHistoryHidden::class,
          org.Tajgram.tgnet.TLRPC.ChannelAdminLogEventAction::TLdeserialize, null)

    }

    @Test
    public
        fun test_000113_ChannelAdminLogEventAction_TL_channelAdminLogEventActionToggleSignatureProfiles() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ChannelAdminLogEventAction.TL_channelAdminLogEventActionToggleSignatureProfiles::class,
          org.Tajgram.tgnet.TLRPC.ChannelAdminLogEventAction::TLdeserialize, null)

    }

    @Test
    public
        fun test_000114_ChannelAdminLogEventAction_TL_channelAdminLogEventActionToggleSignatures() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ChannelAdminLogEventAction.TL_channelAdminLogEventActionToggleSignatures::class,
          org.Tajgram.tgnet.TLRPC.ChannelAdminLogEventAction::TLdeserialize, null)

    }

    @Test
    public
        fun test_000115_ChannelAdminLogEventAction_TL_channelAdminLogEventActionToggleSlowMode() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ChannelAdminLogEventAction.TL_channelAdminLogEventActionToggleSlowMode::class,
          org.Tajgram.tgnet.TLRPC.ChannelAdminLogEventAction::TLdeserialize, null)

    }

    @Test
    public fun test_000116_ChannelAdminLogEventAction_TL_channelAdminLogEventActionUpdatePinned() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ChannelAdminLogEventAction.TL_channelAdminLogEventActionUpdatePinned::class,
          org.Tajgram.tgnet.TLRPC.ChannelAdminLogEventAction::TLdeserialize, null)

    }

    @Test
    public fun test_000117_ChannelAdminLogEventsFilter_TL_channelAdminLogEventsFilter() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ChannelAdminLogEventsFilter.TL_channelAdminLogEventsFilter::class,
          org.Tajgram.tgnet.TLRPC.TL_channelAdminLogEventsFilter::TLdeserialize, null)

    }

    @Test
    public fun test_000118_ChannelLocation_TL_channelLocation() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ChannelLocation.TL_channelLocation::class,
          org.Tajgram.tgnet.TLRPC.ChannelLocation::TLdeserialize, null)

    }

    @Test
    public fun test_000119_ChannelLocation_TL_channelLocationEmpty() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ChannelLocation.TL_channelLocationEmpty::class,
          org.Tajgram.tgnet.TLRPC.ChannelLocation::TLdeserialize, null)

    }

    @Test
    public fun test_000120_ChannelMessagesFilter_TL_channelMessagesFilter() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ChannelMessagesFilter.TL_channelMessagesFilter::class,
          org.Tajgram.tgnet.TLRPC.ChannelMessagesFilter::TLdeserialize, null)

    }

    @Test
    public fun test_000121_ChannelMessagesFilter_TL_channelMessagesFilterEmpty() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ChannelMessagesFilter.TL_channelMessagesFilterEmpty::class,
          org.Tajgram.tgnet.TLRPC.ChannelMessagesFilter::TLdeserialize, null)

    }

    @Test
    public fun test_000122_ChannelParticipant_TL_channelParticipant() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ChannelParticipant.TL_channelParticipant::class,
          org.Tajgram.tgnet.TLRPC.ChannelParticipant::TLdeserialize, null)

    }

    @Test
    public fun test_000123_ChannelParticipant_TL_channelParticipantAdmin() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ChannelParticipant.TL_channelParticipantAdmin::class,
          org.Tajgram.tgnet.TLRPC.ChannelParticipant::TLdeserialize, null)

    }

    @Test
    public fun test_000124_ChannelParticipant_TL_channelParticipantBanned() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ChannelParticipant.TL_channelParticipantBanned::class,
          org.Tajgram.tgnet.TLRPC.ChannelParticipant::TLdeserialize, null)

    }

    @Test
    public fun test_000125_ChannelParticipant_TL_channelParticipantCreator() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ChannelParticipant.TL_channelParticipantCreator::class,
          org.Tajgram.tgnet.TLRPC.ChannelParticipant::TLdeserialize, null)

    }

    @Test
    public fun test_000126_ChannelParticipant_TL_channelParticipantLeft() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ChannelParticipant.TL_channelParticipantLeft::class,
          org.Tajgram.tgnet.TLRPC.ChannelParticipant::TLdeserialize, null)

    }

    @Test
    public fun test_000127_ChannelParticipant_TL_channelParticipantSelf() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ChannelParticipant.TL_channelParticipantSelf::class,
          org.Tajgram.tgnet.TLRPC.ChannelParticipant::TLdeserialize, null)

    }

    @Test
    public fun test_000128_ChannelParticipantsFilter_TL_channelParticipantsAdmins() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ChannelParticipantsFilter.TL_channelParticipantsAdmins::class,
          org.Tajgram.tgnet.TLRPC.ChannelParticipantsFilter::TLdeserialize, null)

    }

    @Test
    public fun test_000129_ChannelParticipantsFilter_TL_channelParticipantsBanned() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ChannelParticipantsFilter.TL_channelParticipantsBanned::class,
          org.Tajgram.tgnet.TLRPC.ChannelParticipantsFilter::TLdeserialize, null)

    }

    @Test
    public fun test_000130_ChannelParticipantsFilter_TL_channelParticipantsBots() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ChannelParticipantsFilter.TL_channelParticipantsBots::class,
          org.Tajgram.tgnet.TLRPC.ChannelParticipantsFilter::TLdeserialize, null)

    }

    @Test
    public fun test_000131_ChannelParticipantsFilter_TL_channelParticipantsContacts() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ChannelParticipantsFilter.TL_channelParticipantsContacts::class,
          org.Tajgram.tgnet.TLRPC.ChannelParticipantsFilter::TLdeserialize, null)

    }

    @Test
    public fun test_000132_ChannelParticipantsFilter_TL_channelParticipantsKicked() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ChannelParticipantsFilter.TL_channelParticipantsKicked::class,
          org.Tajgram.tgnet.TLRPC.ChannelParticipantsFilter::TLdeserialize, null)

    }

    @Test
    public fun test_000133_ChannelParticipantsFilter_TL_channelParticipantsMentions() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ChannelParticipantsFilter.TL_channelParticipantsMentions::class,
          org.Tajgram.tgnet.TLRPC.ChannelParticipantsFilter::TLdeserialize, null)

    }

    @Test
    public fun test_000134_ChannelParticipantsFilter_TL_channelParticipantsRecent() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ChannelParticipantsFilter.TL_channelParticipantsRecent::class,
          org.Tajgram.tgnet.TLRPC.ChannelParticipantsFilter::TLdeserialize, null)

    }

    @Test
    public fun test_000135_ChannelParticipantsFilter_TL_channelParticipantsSearch() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ChannelParticipantsFilter.TL_channelParticipantsSearch::class,
          org.Tajgram.tgnet.TLRPC.ChannelParticipantsFilter::TLdeserialize, null)

    }

    @Test
    public fun test_000136_Chat_TL_channel() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Chat.TL_channel::class,
          org.Tajgram.tgnet.TLRPC.Chat::TLdeserialize, null)

    }

    @Test
    public fun test_000137_Chat_TL_channelForbidden() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Chat.TL_channelForbidden::class,
          org.Tajgram.tgnet.TLRPC.Chat::TLdeserialize, null)

    }

    @Test
    public fun test_000138_Chat_TL_chat() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Chat.TL_chat::class,
          org.Tajgram.tgnet.TLRPC.Chat::TLdeserialize, null)

    }

    @Test
    public fun test_000139_Chat_TL_chatEmpty() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Chat.TL_chatEmpty::class,
          org.Tajgram.tgnet.TLRPC.Chat::TLdeserialize, null)

    }

    @Test
    public fun test_000140_Chat_TL_chatForbidden() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Chat.TL_chatForbidden::class,
          org.Tajgram.tgnet.TLRPC.Chat::TLdeserialize, null)

    }

    @Test
    public fun test_000141_ChatAdminRights_TL_chatAdminRights() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ChatAdminRights.TL_chatAdminRights::class,
          org.Tajgram.tgnet.TLRPC.TL_chatAdminRights::TLdeserialize, null)

    }

    @Test
    public fun test_000142_ChatAdminWithInvites_TL_chatAdminWithInvites() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ChatAdminWithInvites.TL_chatAdminWithInvites::class,
          org.Tajgram.tgnet.TLRPC.TL_chatAdminWithInvites::TLdeserialize, null)

    }

    @Test
    public fun test_000143_ChatBannedRights_TL_chatBannedRights() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ChatBannedRights.TL_chatBannedRights::class,
          org.Tajgram.tgnet.TLRPC.TL_chatBannedRights::TLdeserialize, null)

    }

    @Test
    public fun test_000144_ChatFull_TL_channelFull() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ChatFull.TL_channelFull::class,
          org.Tajgram.tgnet.TLRPC.ChatFull::TLdeserialize, null)

    }

    @Test
    public fun test_000145_ChatFull_TL_chatFull() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ChatFull.TL_chatFull::class,
          org.Tajgram.tgnet.TLRPC.ChatFull::TLdeserialize, null)

    }

    @Test
    public fun test_000146_ChatInvite_TL_chatInvite() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ChatInvite.TL_chatInvite::class,
          org.Tajgram.tgnet.TLRPC.ChatInvite::TLdeserialize, null)

    }

    @Test
    public fun test_000147_ChatInvite_TL_chatInviteAlready() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ChatInvite.TL_chatInviteAlready::class,
          org.Tajgram.tgnet.TLRPC.ChatInvite::TLdeserialize, null)

    }

    @Test
    public fun test_000148_ChatInvite_TL_chatInvitePeek() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ChatInvite.TL_chatInvitePeek::class,
          org.Tajgram.tgnet.TLRPC.ChatInvite::TLdeserialize, null)

    }

    @Test
    public fun test_000149_ChatInviteImporter_TL_chatInviteImporter() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ChatInviteImporter.TL_chatInviteImporter::class,
          org.Tajgram.tgnet.TLRPC.TL_chatInviteImporter::TLdeserialize, null)

    }

    @Test
    public fun test_000150_ChatOnlines_TL_chatOnlines() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ChatOnlines.TL_chatOnlines::class,
          org.Tajgram.tgnet.TLRPC.TL_chatOnlines::TLdeserialize, null)

    }

    @Test
    public fun test_000151_ChatParticipant_TL_chatParticipant() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ChatParticipant.TL_chatParticipant::class,
          org.Tajgram.tgnet.TLRPC.ChatParticipant::TLdeserialize, null)

    }

    @Test
    public fun test_000152_ChatParticipant_TL_chatParticipantAdmin() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ChatParticipant.TL_chatParticipantAdmin::class,
          org.Tajgram.tgnet.TLRPC.ChatParticipant::TLdeserialize, null)

    }

    @Test
    public fun test_000153_ChatParticipant_TL_chatParticipantCreator() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ChatParticipant.TL_chatParticipantCreator::class,
          org.Tajgram.tgnet.TLRPC.ChatParticipant::TLdeserialize, null)

    }

    @Test
    public fun test_000154_ChatParticipants_TL_chatParticipants() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ChatParticipants.TL_chatParticipants::class,
          org.Tajgram.tgnet.TLRPC.ChatParticipants::TLdeserialize, null)

    }

    @Test
    public fun test_000155_ChatParticipants_TL_chatParticipantsForbidden() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ChatParticipants.TL_chatParticipantsForbidden::class,
          org.Tajgram.tgnet.TLRPC.ChatParticipants::TLdeserialize, null)

    }

    @Test
    public fun test_000156_ChatPhoto_TL_chatPhoto() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ChatPhoto.TL_chatPhoto::class,
          org.Tajgram.tgnet.TLRPC.ChatPhoto::TLdeserialize, null)

    }

    @Test
    public fun test_000157_ChatPhoto_TL_chatPhotoEmpty() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ChatPhoto.TL_chatPhotoEmpty::class,
          org.Tajgram.tgnet.TLRPC.ChatPhoto::TLdeserialize, null)

    }

    @Test
    public fun test_000158_ChatReactions_TL_chatReactionsAll() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ChatReactions.TL_chatReactionsAll::class,
          org.Tajgram.tgnet.TLRPC.ChatReactions::TLdeserialize, null)

    }

    @Test
    public fun test_000159_ChatReactions_TL_chatReactionsNone() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ChatReactions.TL_chatReactionsNone::class,
          org.Tajgram.tgnet.TLRPC.ChatReactions::TLdeserialize, null)

    }

    @Test
    public fun test_000160_ChatReactions_TL_chatReactionsSome() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ChatReactions.TL_chatReactionsSome::class,
          org.Tajgram.tgnet.TLRPC.ChatReactions::TLdeserialize, null)

    }

    @Test
    public fun test_000161_ChatTheme_TL_chatTheme() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ChatTheme.TL_chatTheme::class,
          org.Tajgram.tgnet.TLRPC.ChatTheme::TLdeserialize, null)

    }

    @Test
    public fun test_000162_ChatTheme_TL_chatThemeUniqueGift() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ChatTheme.TL_chatThemeUniqueGift::class,
          org.Tajgram.tgnet.TLRPC.ChatTheme::TLdeserialize, null)

    }

    @Test
    public fun test_000163_CodeSettings_TL_codeSettings() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_CodeSettings.TL_codeSettings::class,
          org.Tajgram.tgnet.TLRPC.TL_codeSettings::TLdeserialize, null)

    }

    @Test
    public fun test_000164_Config_TL_config() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Config.TL_config::class,
          org.Tajgram.tgnet.TLRPC.TL_config::TLdeserialize, null)

    }

    @Test
    public fun test_000165_ConnectedBot_TL_connectedBot() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ConnectedBot.TL_connectedBot::class,
          org.Tajgram.tgnet.tl.TL_account.TL_connectedBot::TLdeserialize, null)

    }

    @Test
    public fun test_000166_ConnectedBotStarRef_TL_connectedBotStarRef() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ConnectedBotStarRef.TL_connectedBotStarRef::class,
          org.Tajgram.tgnet.tl.TL_payments.connectedBotStarRef::TLdeserialize, null)

    }

    @Test
    public fun test_000167_Contact_TL_contact() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Contact.TL_contact::class,
          org.Tajgram.tgnet.TLRPC.TL_contact::TLdeserialize, null)

    }

    @Test
    public fun test_000168_ContactBirthday_TL_contactBirthday() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ContactBirthday.TL_contactBirthday::class,
          org.Tajgram.tgnet.tl.TL_account.TL_contactBirthday::TLdeserialize, null)

    }

    @Test
    public fun test_000169_ContactStatus_TL_contactStatus() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ContactStatus.TL_contactStatus::class,
          org.Tajgram.tgnet.TLRPC.TL_contactStatus::TLdeserialize, null)

    }

    @Test
    public fun test_000170_DataJSON_TL_dataJSON() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_DataJSON.TL_dataJSON::class,
          org.Tajgram.tgnet.TLRPC.TL_dataJSON::TLdeserialize, null)

    }

    @Test
    public fun test_000171_DcOption_TL_dcOption() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_DcOption.TL_dcOption::class,
          org.Tajgram.tgnet.TLRPC.TL_dcOption::TLdeserialize, null)

    }

    @Test
    public fun test_000172_DefaultHistoryTTL_TL_defaultHistoryTTL() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_DefaultHistoryTTL.TL_defaultHistoryTTL::class,
          org.Tajgram.tgnet.TLRPC.TL_defaultHistoryTTL::TLdeserialize, null)

    }

    @Test
    public fun test_000173_Dialog_TL_dialog() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Dialog.TL_dialog::class,
          org.Tajgram.tgnet.TLRPC.Dialog::TLdeserialize, null)

    }

    @Test
    public fun test_000174_Dialog_TL_dialogFolder() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Dialog.TL_dialogFolder::class,
          org.Tajgram.tgnet.TLRPC.Dialog::TLdeserialize, null)

    }

    @Test
    public fun test_000175_DialogFilter_TL_dialogFilter() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_DialogFilter.TL_dialogFilter::class,
          org.Tajgram.tgnet.TLRPC.DialogFilter::TLdeserialize, null)

    }

    @Test
    public fun test_000176_DialogFilter_TL_dialogFilterChatlist() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_DialogFilter.TL_dialogFilterChatlist::class,
          org.Tajgram.tgnet.TLRPC.DialogFilter::TLdeserialize, null)

    }

    @Test
    public fun test_000177_DialogFilter_TL_dialogFilterDefault() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_DialogFilter.TL_dialogFilterDefault::class,
          org.Tajgram.tgnet.TLRPC.DialogFilter::TLdeserialize, null)

    }

    @Test
    public fun test_000178_DialogFilterSuggested_TL_dialogFilterSuggested() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_DialogFilterSuggested.TL_dialogFilterSuggested::class,
          org.Tajgram.tgnet.TLRPC.TL_dialogFilterSuggested::TLdeserialize, null)

    }

    @Test
    public fun test_000179_DialogPeer_TL_dialogPeer() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_DialogPeer.TL_dialogPeer::class,
          org.Tajgram.tgnet.TLRPC.DialogPeer::TLdeserialize, null)

    }

    @Test
    public fun test_000180_DialogPeer_TL_dialogPeerFolder() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_DialogPeer.TL_dialogPeerFolder::class,
          org.Tajgram.tgnet.TLRPC.DialogPeer::TLdeserialize, null)

    }

    @Test
    public fun test_000181_DisallowedGiftsSettings_TL_disallowedGiftsSettings() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_DisallowedGiftsSettings.TL_disallowedGiftsSettings::class,
          org.Tajgram.tgnet.TLRPC.DisallowedGiftsSettings::TLdeserialize, null)

    }

    @Test
    public fun test_000182_Document_TL_document() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Document.TL_document::class,
          org.Tajgram.tgnet.TLRPC.Document::TLdeserialize, null)

    }

    @Test
    public fun test_000183_Document_TL_documentEmpty() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Document.TL_documentEmpty::class,
          org.Tajgram.tgnet.TLRPC.Document::TLdeserialize, null)

    }

    @Test
    public fun test_000184_DocumentAttribute_TL_documentAttributeAnimated() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_DocumentAttribute.TL_documentAttributeAnimated::class,
          org.Tajgram.tgnet.TLRPC.DocumentAttribute::TLdeserialize, null)

    }

    @Test
    public fun test_000185_DocumentAttribute_TL_documentAttributeAudio() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_DocumentAttribute.TL_documentAttributeAudio::class,
          org.Tajgram.tgnet.TLRPC.DocumentAttribute::TLdeserialize, null)

    }

    @Test
    public fun test_000186_DocumentAttribute_TL_documentAttributeCustomEmoji() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_DocumentAttribute.TL_documentAttributeCustomEmoji::class,
          org.Tajgram.tgnet.TLRPC.DocumentAttribute::TLdeserialize, null)

    }

    @Test
    public fun test_000187_DocumentAttribute_TL_documentAttributeFilename() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_DocumentAttribute.TL_documentAttributeFilename::class,
          org.Tajgram.tgnet.TLRPC.DocumentAttribute::TLdeserialize, null)

    }

    @Test
    public fun test_000188_DocumentAttribute_TL_documentAttributeHasStickers() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_DocumentAttribute.TL_documentAttributeHasStickers::class,
          org.Tajgram.tgnet.TLRPC.DocumentAttribute::TLdeserialize, null)

    }

    @Test
    public fun test_000189_DocumentAttribute_TL_documentAttributeImageSize() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_DocumentAttribute.TL_documentAttributeImageSize::class,
          org.Tajgram.tgnet.TLRPC.DocumentAttribute::TLdeserialize, null)

    }

    @Test
    public fun test_000190_DocumentAttribute_TL_documentAttributeSticker() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_DocumentAttribute.TL_documentAttributeSticker::class,
          org.Tajgram.tgnet.TLRPC.DocumentAttribute::TLdeserialize, null)

    }

    @Test
    public fun test_000191_DocumentAttribute_TL_documentAttributeVideo() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_DocumentAttribute.TL_documentAttributeVideo::class,
          org.Tajgram.tgnet.TLRPC.DocumentAttribute::TLdeserialize, null)

    }

    @Test
    public fun test_000192_DraftMessage_TL_draftMessage() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_DraftMessage.TL_draftMessage::class,
          org.Tajgram.tgnet.TLRPC.DraftMessage::TLdeserialize, null)

    }

    @Test
    public fun test_000193_DraftMessage_TL_draftMessageEmpty() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_DraftMessage.TL_draftMessageEmpty::class,
          org.Tajgram.tgnet.TLRPC.DraftMessage::TLdeserialize, null)

    }

    @Test
    public fun test_000194_EmailVerification_TL_emailVerificationApple() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_EmailVerification.TL_emailVerificationApple::class,
          org.Tajgram.tgnet.TLRPC.EmailVerification::TLdeserialize, null)

    }

    @Test
    public fun test_000195_EmailVerification_TL_emailVerificationCode() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_EmailVerification.TL_emailVerificationCode::class,
          org.Tajgram.tgnet.TLRPC.EmailVerification::TLdeserialize, null)

    }

    @Test
    public fun test_000196_EmailVerification_TL_emailVerificationGoogle() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_EmailVerification.TL_emailVerificationGoogle::class,
          org.Tajgram.tgnet.TLRPC.EmailVerification::TLdeserialize, null)

    }

    @Test
    public fun test_000197_EmailVerifyPurpose_TL_emailVerifyPurposeLoginChange() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_EmailVerifyPurpose.TL_emailVerifyPurposeLoginChange::class,
          org.Tajgram.tgnet.TLRPC.EmailVerifyPurpose::TLdeserialize, null)

    }

    @Test
    public fun test_000198_EmailVerifyPurpose_TL_emailVerifyPurposeLoginSetup() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_EmailVerifyPurpose.TL_emailVerifyPurposeLoginSetup::class,
          org.Tajgram.tgnet.TLRPC.EmailVerifyPurpose::TLdeserialize, null)

    }

    @Test
    public fun test_000199_EmailVerifyPurpose_TL_emailVerifyPurposePassport() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_EmailVerifyPurpose.TL_emailVerifyPurposePassport::class,
          org.Tajgram.tgnet.TLRPC.EmailVerifyPurpose::TLdeserialize, null)

    }

    @Test
    public fun test_000200_EmojiGroup_TL_emojiGroup() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_EmojiGroup.TL_emojiGroup::class,
          org.Tajgram.tgnet.TLRPC.EmojiGroup::TLdeserialize, null)

    }

    @Test
    public fun test_000201_EmojiGroup_TL_emojiGroupGreeting() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_EmojiGroup.TL_emojiGroupGreeting::class,
          org.Tajgram.tgnet.TLRPC.EmojiGroup::TLdeserialize, null)

    }

    @Test
    public fun test_000202_EmojiGroup_TL_emojiGroupPremium() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_EmojiGroup.TL_emojiGroupPremium::class,
          org.Tajgram.tgnet.TLRPC.EmojiGroup::TLdeserialize, null)

    }

    @Test
    public fun test_000203_EmojiKeyword_TL_emojiKeyword() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_EmojiKeyword.TL_emojiKeyword::class,
          org.Tajgram.tgnet.TLRPC.EmojiKeyword::TLdeserialize, null)

    }

    @Test
    public fun test_000204_EmojiKeyword_TL_emojiKeywordDeleted() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_EmojiKeyword.TL_emojiKeywordDeleted::class,
          org.Tajgram.tgnet.TLRPC.EmojiKeyword::TLdeserialize, null)

    }

    @Test
    public fun test_000205_EmojiKeywordsDifference_TL_emojiKeywordsDifference() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_EmojiKeywordsDifference.TL_emojiKeywordsDifference::class,
          org.Tajgram.tgnet.TLRPC.TL_emojiKeywordsDifference::TLdeserialize, null)

    }

    @Test
    public fun test_000206_EmojiLanguage_TL_emojiLanguage() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_EmojiLanguage.TL_emojiLanguage::class,
          org.Tajgram.tgnet.TLRPC.TL_emojiLanguage::TLdeserialize, null)

    }

    @Test
    public fun test_000207_EmojiList_TL_emojiList() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_EmojiList.TL_emojiList::class,
          org.Tajgram.tgnet.TLRPC.EmojiList::TLdeserialize, null)

    }

    @Test
    public fun test_000208_EmojiList_TL_emojiListNotModified() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_EmojiList.TL_emojiListNotModified::class,
          org.Tajgram.tgnet.TLRPC.EmojiList::TLdeserialize, null)

    }

    @Test
    public fun test_000209_EmojiStatus_TL_emojiStatus() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_EmojiStatus.TL_emojiStatus::class,
          org.Tajgram.tgnet.TLRPC.EmojiStatus::TLdeserialize, null)

    }

    @Test
    public fun test_000210_EmojiStatus_TL_emojiStatusCollectible() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_EmojiStatus.TL_emojiStatusCollectible::class,
          org.Tajgram.tgnet.TLRPC.EmojiStatus::TLdeserialize, null)

    }

    @Test
    public fun test_000211_EmojiStatus_TL_emojiStatusEmpty() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_EmojiStatus.TL_emojiStatusEmpty::class,
          org.Tajgram.tgnet.TLRPC.EmojiStatus::TLdeserialize, null)

    }

    @Test
    public fun test_000212_EmojiStatus_TL_inputEmojiStatusCollectible() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_EmojiStatus.TL_inputEmojiStatusCollectible::class,
          org.Tajgram.tgnet.TLRPC.EmojiStatus::TLdeserialize, null)

    }

    @Test
    public fun test_000213_EmojiURL_TL_emojiURL() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_EmojiURL.TL_emojiURL::class,
          org.Tajgram.tgnet.TLRPC.TL_emojiURL::TLdeserialize, null)

    }

    @Test
    public fun test_000214_EncryptedChat_TL_encryptedChat() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_EncryptedChat.TL_encryptedChat::class,
          org.Tajgram.tgnet.TLRPC.EncryptedChat::TLdeserialize, null)

    }

    @Test
    public fun test_000215_EncryptedChat_TL_encryptedChatDiscarded() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_EncryptedChat.TL_encryptedChatDiscarded::class,
          org.Tajgram.tgnet.TLRPC.EncryptedChat::TLdeserialize, null)

    }

    @Test
    public fun test_000216_EncryptedChat_TL_encryptedChatEmpty() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_EncryptedChat.TL_encryptedChatEmpty::class,
          org.Tajgram.tgnet.TLRPC.EncryptedChat::TLdeserialize, null)

    }

    @Test
    public fun test_000217_EncryptedChat_TL_encryptedChatRequested() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_EncryptedChat.TL_encryptedChatRequested::class,
          org.Tajgram.tgnet.TLRPC.EncryptedChat::TLdeserialize, null)

    }

    @Test
    public fun test_000218_EncryptedChat_TL_encryptedChatWaiting() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_EncryptedChat.TL_encryptedChatWaiting::class,
          org.Tajgram.tgnet.TLRPC.EncryptedChat::TLdeserialize, null)

    }

    @Test
    public fun test_000219_EncryptedFile_TL_encryptedFile() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_EncryptedFile.TL_encryptedFile::class,
          org.Tajgram.tgnet.TLRPC.EncryptedFile::TLdeserialize, null)

    }

    @Test
    public fun test_000220_EncryptedFile_TL_encryptedFileEmpty() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_EncryptedFile.TL_encryptedFileEmpty::class,
          org.Tajgram.tgnet.TLRPC.EncryptedFile::TLdeserialize, null)

    }

    @Test
    public fun test_000221_EncryptedMessage_TL_encryptedMessage() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_EncryptedMessage.TL_encryptedMessage::class,
          org.Tajgram.tgnet.TLRPC.EncryptedMessage::TLdeserialize, null)

    }

    @Test
    public fun test_000222_EncryptedMessage_TL_encryptedMessageService() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_EncryptedMessage.TL_encryptedMessageService::class,
          org.Tajgram.tgnet.TLRPC.EncryptedMessage::TLdeserialize, null)

    }

    @Test
    public fun test_000223_Error_TL_error() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Error.TL_error::class,
          org.Tajgram.tgnet.TLRPC.TL_error::TLdeserialize, null)

    }

    @Test
    public fun test_000224_ExportedChatInvite_TL_chatInviteExported() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ExportedChatInvite.TL_chatInviteExported::class,
          org.Tajgram.tgnet.TLRPC.ExportedChatInvite::TLdeserialize, null)

    }

    @Test
    public fun test_000225_ExportedChatInvite_TL_chatInvitePublicJoinRequests() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ExportedChatInvite.TL_chatInvitePublicJoinRequests::class,
          org.Tajgram.tgnet.TLRPC.ExportedChatInvite::TLdeserialize, null)

    }

    @Test
    public fun test_000226_ExportedChatlistInvite_TL_exportedChatlistInvite() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ExportedChatlistInvite.TL_exportedChatlistInvite::class,
          org.Tajgram.tgnet.tl.TL_chatlists.TL_exportedChatlistInvite::TLdeserialize, null)

    }

    @Test
    public fun test_000227_ExportedContactToken_TL_exportedContactToken() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ExportedContactToken.TL_exportedContactToken::class,
          org.Tajgram.tgnet.TLRPC.TL_exportedContactToken::TLdeserialize, null)

    }

    @Test
    public fun test_000228_ExportedMessageLink_TL_exportedMessageLink() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ExportedMessageLink.TL_exportedMessageLink::class,
          org.Tajgram.tgnet.TLRPC.TL_exportedMessageLink::TLdeserialize, null)

    }

    @Test
    public fun test_000229_ExportedStoryLink_TL_exportedStoryLink() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ExportedStoryLink.TL_exportedStoryLink::class,
          org.Tajgram.tgnet.tl.TL_stories.TL_exportedStoryLink::TLdeserialize, null)

    }

    @Test
    public fun test_000230_FactCheck_TL_factCheck() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_FactCheck.TL_factCheck::class,
          org.Tajgram.tgnet.TLRPC.TL_factCheck::TLdeserialize, null)

    }

    @Test
    public fun test_000231_FileHash_TL_fileHash() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_FileHash.TL_fileHash::class,
          org.Tajgram.tgnet.TLRPC.TL_fileHash::TLdeserialize, null)

    }

    @Test
    public fun test_000232_Folder_TL_folder() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Folder.TL_folder::class,
          org.Tajgram.tgnet.TLRPC.TL_folder::TLdeserialize, null)

    }

    @Test
    public fun test_000233_FolderPeer_TL_folderPeer() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_FolderPeer.TL_folderPeer::class,
          org.Tajgram.tgnet.TLRPC.TL_folderPeer::TLdeserialize, null)

    }

    @Test
    public fun test_000234_ForumTopic_TL_forumTopic() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ForumTopic.TL_forumTopic::class,
          org.Tajgram.tgnet.TLRPC.ForumTopic::TLdeserialize, null)

    }

    @Test
    public fun test_000235_ForumTopic_TL_forumTopicDeleted() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ForumTopic.TL_forumTopicDeleted::class,
          org.Tajgram.tgnet.TLRPC.ForumTopic::TLdeserialize, null)

    }

    @Test
    public fun test_000236_FoundStory_TL_foundStory() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_FoundStory.TL_foundStory::class,
          org.Tajgram.tgnet.tl.TL_stories.TL_foundStory::TLdeserialize, null)

    }

    @Test
    public fun test_000237_Game_TL_game() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Game.TL_game::class,
          org.Tajgram.tgnet.TLRPC.TL_game::TLdeserialize, null)

    }

    @Test
    public fun test_000238_GeoPoint_TL_geoPoint() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_GeoPoint.TL_geoPoint::class,
          org.Tajgram.tgnet.TLRPC.GeoPoint::TLdeserialize, null)

    }

    @Test
    public fun test_000239_GeoPoint_TL_geoPointEmpty() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_GeoPoint.TL_geoPointEmpty::class,
          org.Tajgram.tgnet.TLRPC.GeoPoint::TLdeserialize, null)

    }

    @Test
    public fun test_000240_GeoPointAddress_TL_geoPointAddress() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_GeoPointAddress.TL_geoPointAddress::class,
          org.Tajgram.tgnet.tl.TL_stories.TL_geoPointAddress::TLdeserialize, null)

    }

    @Test
    public fun test_000241_GlobalPrivacySettings_TL_globalPrivacySettings() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_GlobalPrivacySettings.TL_globalPrivacySettings::class,
          org.Tajgram.tgnet.TLRPC.GlobalPrivacySettings::TLdeserialize, null)

    }

    @Test
    public fun test_000242_GroupCall_TL_groupCall() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_GroupCall.TL_groupCall::class,
          org.Tajgram.tgnet.TLRPC.GroupCall::TLdeserialize, null)

    }

    @Test
    public fun test_000243_GroupCall_TL_groupCallDiscarded() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_GroupCall.TL_groupCallDiscarded::class,
          org.Tajgram.tgnet.TLRPC.GroupCall::TLdeserialize, null)

    }

    @Test
    public fun test_000244_GroupCallDonor_TL_groupCallDonor() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_GroupCallDonor.TL_groupCallDonor::class,
          org.Tajgram.tgnet.tl.TL_phone.groupCallDonor::TLdeserialize, null)

    }

    @Test
    public fun test_000245_GroupCallMessage_TL_groupCallMessage() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_GroupCallMessage.TL_groupCallMessage::class,
          org.Tajgram.tgnet.TLRPC.GroupCallMessage::TLdeserialize, null)

    }

    @Test
    public fun test_000246_GroupCallParticipant_TL_groupCallParticipant() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_GroupCallParticipant.TL_groupCallParticipant::class,
          org.Tajgram.tgnet.TLRPC.GroupCallParticipant::TLdeserialize, null)

    }

    @Test
    public fun test_000247_GroupCallParticipantVideo_TL_groupCallParticipantVideo() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_GroupCallParticipantVideo.TL_groupCallParticipantVideo::class,
          org.Tajgram.tgnet.TLRPC.TL_groupCallParticipantVideo::TLdeserialize, null)

    }

    @Test
    public
        fun test_000248_GroupCallParticipantVideoSourceGroup_TL_groupCallParticipantVideoSourceGroup() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_GroupCallParticipantVideoSourceGroup.TL_groupCallParticipantVideoSourceGroup::class,
          org.Tajgram.tgnet.TLRPC.TL_groupCallParticipantVideoSourceGroup::TLdeserialize, null)

    }

    @Test
    public fun test_000249_GroupCallStreamChannel_TL_groupCallStreamChannel() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_GroupCallStreamChannel.TL_groupCallStreamChannel::class,
          org.Tajgram.tgnet.tl.TL_phone.TL_groupCallStreamChannel::TLdeserialize, null)

    }

    @Test
    public fun test_000250_HighScore_TL_highScore() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_HighScore.TL_highScore::class,
          org.Tajgram.tgnet.TLRPC.TL_highScore::TLdeserialize, null)

    }

    @Test
    public fun test_000251_ImportedContact_TL_importedContact() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ImportedContact.TL_importedContact::class,
          org.Tajgram.tgnet.TLRPC.TL_importedContact::TLdeserialize, null)

    }

    @Test
    public fun test_000252_InlineBotSwitchPM_TL_inlineBotSwitchPM() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InlineBotSwitchPM.TL_inlineBotSwitchPM::class,
          org.Tajgram.tgnet.TLRPC.TL_inlineBotSwitchPM::TLdeserialize, null)

    }

    @Test
    public fun test_000253_InlineBotWebView_TL_inlineBotWebView() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InlineBotWebView.TL_inlineBotWebView::class,
          org.Tajgram.tgnet.TLRPC.TL_inlineBotWebView::TLdeserialize, null)

    }

    @Test
    public fun test_000254_InlineQueryPeerType_TL_inlineQueryPeerTypeBotPM() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InlineQueryPeerType.TL_inlineQueryPeerTypeBotPM::class,
          org.Tajgram.tgnet.TLRPC.InlineQueryPeerType::TLdeserialize, null)

    }

    @Test
    public fun test_000255_InlineQueryPeerType_TL_inlineQueryPeerTypeBroadcast() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InlineQueryPeerType.TL_inlineQueryPeerTypeBroadcast::class,
          org.Tajgram.tgnet.TLRPC.InlineQueryPeerType::TLdeserialize, null)

    }

    @Test
    public fun test_000256_InlineQueryPeerType_TL_inlineQueryPeerTypeChat() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InlineQueryPeerType.TL_inlineQueryPeerTypeChat::class,
          org.Tajgram.tgnet.TLRPC.InlineQueryPeerType::TLdeserialize, null)

    }

    @Test
    public fun test_000257_InlineQueryPeerType_TL_inlineQueryPeerTypeMegagroup() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InlineQueryPeerType.TL_inlineQueryPeerTypeMegagroup::class,
          org.Tajgram.tgnet.TLRPC.InlineQueryPeerType::TLdeserialize, null)

    }

    @Test
    public fun test_000258_InlineQueryPeerType_TL_inlineQueryPeerTypePM() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InlineQueryPeerType.TL_inlineQueryPeerTypePM::class,
          org.Tajgram.tgnet.TLRPC.InlineQueryPeerType::TLdeserialize, null)

    }

    @Test
    public fun test_000259_InlineQueryPeerType_TL_inlineQueryPeerTypeSameBotPM() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InlineQueryPeerType.TL_inlineQueryPeerTypeSameBotPM::class,
          org.Tajgram.tgnet.TLRPC.InlineQueryPeerType::TLdeserialize, null)

    }

    @Test
    public fun test_000260_InputAiComposeTone_TL_inputAiComposeToneDefault() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputAiComposeTone.TL_inputAiComposeToneDefault::class,
          org.Tajgram.tgnet.tl.TL_aicompose.InputAiComposeTone::TLdeserialize, null)

    }

    @Test
    public fun test_000261_InputAiComposeTone_TL_inputAiComposeToneID() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputAiComposeTone.TL_inputAiComposeToneID::class,
          org.Tajgram.tgnet.tl.TL_aicompose.InputAiComposeTone::TLdeserialize, null)

    }

    @Test
    public fun test_000262_InputAiComposeTone_TL_inputAiComposeToneSlug() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputAiComposeTone.TL_inputAiComposeToneSlug::class,
          org.Tajgram.tgnet.tl.TL_aicompose.InputAiComposeTone::TLdeserialize, null)

    }

    @Test
    public fun test_000263_InputAppEvent_TL_inputAppEvent() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputAppEvent.TL_inputAppEvent::class,
          org.Tajgram.tgnet.TLRPC.TL_inputAppEvent::TLdeserialize, null)

    }

    @Test
    public fun test_000264_InputBotApp_TL_inputBotAppID() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputBotApp.TL_inputBotAppID::class,
          org.Tajgram.tgnet.TLRPC.InputBotApp::TLdeserialize, null)

    }

    @Test
    public fun test_000265_InputBotApp_TL_inputBotAppShortName() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputBotApp.TL_inputBotAppShortName::class,
          org.Tajgram.tgnet.TLRPC.InputBotApp::TLdeserialize, null)

    }

    @Test
    public fun test_000266_InputBotInlineMessageID_TL_inputBotInlineMessageID() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputBotInlineMessageID.TL_inputBotInlineMessageID::class,
          org.Tajgram.tgnet.TLRPC.TL_inputBotInlineMessageID::TLdeserialize, null)

    }

    @Test
    public fun test_000267_InputBusinessBotRecipients_TL_inputBusinessBotRecipients() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputBusinessBotRecipients.TL_inputBusinessBotRecipients::class,
          org.Tajgram.tgnet.tl.TL_account.TL_inputBusinessBotRecipients::TLdeserialize, null)

    }

    @Test
    public fun test_000268_InputBusinessChatLink_TL_inputBusinessChatLink() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputBusinessChatLink.TL_inputBusinessChatLink::class,
          org.Tajgram.tgnet.tl.TL_account.TL_inputBusinessChatLink::TLdeserialize, null)

    }

    @Test
    public fun test_000269_InputBusinessIntro_TL_inputBusinessIntro() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputBusinessIntro.TL_inputBusinessIntro::class,
          org.Tajgram.tgnet.tl.TL_account.TL_inputBusinessIntro::TLdeserialize, null)

    }

    @Test
    public fun test_000270_InputBusinessRecipients_TL_inputBusinessRecipients() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputBusinessRecipients.TL_inputBusinessRecipients::class,
          org.Tajgram.tgnet.tl.TL_account.TL_inputBusinessRecipients::TLdeserialize, null)

    }

    @Test
    public fun test_000271_InputChannel_TL_inputChannel() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputChannel.TL_inputChannel::class,
          org.Tajgram.tgnet.TLRPC.InputChannel::TLdeserialize, null)

    }

    @Test
    public fun test_000272_InputChannel_TL_inputChannelEmpty() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputChannel.TL_inputChannelEmpty::class,
          org.Tajgram.tgnet.TLRPC.InputChannel::TLdeserialize, null)

    }

    @Test
    public fun test_000273_InputChannel_TL_inputChannelFromMessage() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputChannel.TL_inputChannelFromMessage::class,
          org.Tajgram.tgnet.TLRPC.InputChannel::TLdeserialize, null)

    }

    @Test
    public fun test_000274_InputChatPhoto_TL_inputChatPhoto() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputChatPhoto.TL_inputChatPhoto::class,
          org.Tajgram.tgnet.TLRPC.InputChatPhoto::TLdeserialize, null)

    }

    @Test
    public fun test_000275_InputChatPhoto_TL_inputChatPhotoEmpty() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputChatPhoto.TL_inputChatPhotoEmpty::class,
          org.Tajgram.tgnet.TLRPC.InputChatPhoto::TLdeserialize, null)

    }

    @Test
    public fun test_000276_InputChatPhoto_TL_inputChatUploadedPhoto() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputChatPhoto.TL_inputChatUploadedPhoto::class,
          org.Tajgram.tgnet.TLRPC.InputChatPhoto::TLdeserialize, null)

    }

    @Test
    public fun test_000277_InputChatTheme_TL_inputChatTheme() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputChatTheme.TL_inputChatTheme::class,
          org.Tajgram.tgnet.TLRPC.InputChatTheme::TLdeserialize, null)

    }

    @Test
    public fun test_000278_InputChatTheme_TL_inputChatThemeEmpty() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputChatTheme.TL_inputChatThemeEmpty::class,
          org.Tajgram.tgnet.TLRPC.InputChatTheme::TLdeserialize, null)

    }

    @Test
    public fun test_000279_InputChatTheme_TL_inputChatThemeUniqueGift() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputChatTheme.TL_inputChatThemeUniqueGift::class,
          org.Tajgram.tgnet.TLRPC.InputChatTheme::TLdeserialize, null)

    }

    @Test
    public fun test_000280_InputChatlist_TL_inputChatlistDialogFilter() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputChatlist.TL_inputChatlistDialogFilter::class,
          org.Tajgram.tgnet.tl.TL_chatlists.TL_inputChatlistDialogFilter::TLdeserialize, null)

    }

    @Test
    public fun test_000281_InputCheckPasswordSRP_TL_inputCheckPasswordEmpty() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputCheckPasswordSRP.TL_inputCheckPasswordEmpty::class,
          org.Tajgram.tgnet.TLRPC.InputCheckPasswordSRP::TLdeserialize, null)

    }

    @Test
    public fun test_000282_InputCheckPasswordSRP_TL_inputCheckPasswordSRP() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputCheckPasswordSRP.TL_inputCheckPasswordSRP::class,
          org.Tajgram.tgnet.TLRPC.InputCheckPasswordSRP::TLdeserialize, null)

    }

    @Test
    public fun test_000283_InputCollectible_TL_inputCollectiblePhone() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputCollectible.TL_inputCollectiblePhone::class,
          org.Tajgram.tgnet.tl.TL_fragment.InputCollectible::TLdeserialize, null)

    }

    @Test
    public fun test_000284_InputCollectible_TL_inputCollectibleUsername() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputCollectible.TL_inputCollectibleUsername::class,
          org.Tajgram.tgnet.tl.TL_fragment.InputCollectible::TLdeserialize, null)

    }

    @Test
    public fun test_000285_InputContact_TL_inputPhoneContact() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputContact.TL_inputPhoneContact::class,
          org.Tajgram.tgnet.TLRPC.TL_inputPhoneContact::TLdeserialize, null)

    }

    @Test
    public fun test_000286_InputDialogPeer_TL_inputDialogPeer() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputDialogPeer.TL_inputDialogPeer::class,
          org.Tajgram.tgnet.TLRPC.InputDialogPeer::TLdeserialize, null)

    }

    @Test
    public fun test_000287_InputDialogPeer_TL_inputDialogPeerFolder() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputDialogPeer.TL_inputDialogPeerFolder::class,
          org.Tajgram.tgnet.TLRPC.InputDialogPeer::TLdeserialize, null)

    }

    @Test
    public fun test_000288_InputDocument_TL_inputDocument() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputDocument.TL_inputDocument::class,
          org.Tajgram.tgnet.TLRPC.InputDocument::TLdeserialize, null)

    }

    @Test
    public fun test_000289_InputDocument_TL_inputDocumentEmpty() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputDocument.TL_inputDocumentEmpty::class,
          org.Tajgram.tgnet.TLRPC.InputDocument::TLdeserialize, null)

    }

    @Test
    public fun test_000290_InputEncryptedChat_TL_inputEncryptedChat() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputEncryptedChat.TL_inputEncryptedChat::class,
          org.Tajgram.tgnet.TLRPC.TL_inputEncryptedChat::TLdeserialize, null)

    }

    @Test
    public fun test_000291_InputEncryptedFile_TL_inputEncryptedFile() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputEncryptedFile.TL_inputEncryptedFile::class,
          org.Tajgram.tgnet.TLRPC.InputEncryptedFile::TLdeserialize, null)

    }

    @Test
    public fun test_000292_InputEncryptedFile_TL_inputEncryptedFileBigUploaded() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputEncryptedFile.TL_inputEncryptedFileBigUploaded::class,
          org.Tajgram.tgnet.TLRPC.InputEncryptedFile::TLdeserialize, null)

    }

    @Test
    public fun test_000293_InputEncryptedFile_TL_inputEncryptedFileEmpty() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputEncryptedFile.TL_inputEncryptedFileEmpty::class,
          org.Tajgram.tgnet.TLRPC.InputEncryptedFile::TLdeserialize, null)

    }

    @Test
    public fun test_000294_InputEncryptedFile_TL_inputEncryptedFileUploaded() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputEncryptedFile.TL_inputEncryptedFileUploaded::class,
          org.Tajgram.tgnet.TLRPC.InputEncryptedFile::TLdeserialize, null)

    }

    @Test
    public fun test_000295_InputFile_TL_inputFile() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputFile.TL_inputFile::class,
          org.Tajgram.tgnet.TLRPC.InputFile::TLdeserialize, null)

    }

    @Test
    public fun test_000296_InputFile_TL_inputFileBig() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputFile.TL_inputFileBig::class,
          org.Tajgram.tgnet.TLRPC.InputFile::TLdeserialize, null)

    }

    @Test
    public fun test_000297_InputFile_TL_inputFileStoryDocument() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputFile.TL_inputFileStoryDocument::class,
          org.Tajgram.tgnet.TLRPC.InputFile::TLdeserialize, null)

    }

    @Test
    public fun test_000298_InputFileLocation_TL_inputDocumentFileLocation() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputFileLocation.TL_inputDocumentFileLocation::class,
          org.Tajgram.tgnet.TLRPC.InputFileLocation::TLdeserialize, null)

    }

    @Test
    public fun test_000299_InputFileLocation_TL_inputEncryptedFileLocation() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputFileLocation.TL_inputEncryptedFileLocation::class,
          org.Tajgram.tgnet.TLRPC.InputFileLocation::TLdeserialize, null)

    }

    @Test
    public fun test_000300_InputFileLocation_TL_inputFileLocation() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputFileLocation.TL_inputFileLocation::class,
          org.Tajgram.tgnet.TLRPC.InputFileLocation::TLdeserialize, null)

    }

    @Test
    public fun test_000301_InputFileLocation_TL_inputGroupCallStream() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputFileLocation.TL_inputGroupCallStream::class,
          org.Tajgram.tgnet.TLRPC.InputFileLocation::TLdeserialize, null)

    }

    @Test
    public fun test_000302_InputFileLocation_TL_inputPeerPhotoFileLocation() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputFileLocation.TL_inputPeerPhotoFileLocation::class,
          org.Tajgram.tgnet.TLRPC.InputFileLocation::TLdeserialize, null)

    }

    @Test
    public fun test_000303_InputFileLocation_TL_inputPhotoFileLocation() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputFileLocation.TL_inputPhotoFileLocation::class,
          org.Tajgram.tgnet.TLRPC.InputFileLocation::TLdeserialize, null)

    }

    @Test
    public fun test_000304_InputFileLocation_TL_inputSecureFileLocation() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputFileLocation.TL_inputSecureFileLocation::class,
          org.Tajgram.tgnet.TLRPC.InputFileLocation::TLdeserialize, null)

    }

    @Test
    public fun test_000305_InputFileLocation_TL_inputStickerSetThumb() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputFileLocation.TL_inputStickerSetThumb::class,
          org.Tajgram.tgnet.TLRPC.InputFileLocation::TLdeserialize, null)

    }

    @Test
    public fun test_000306_InputFolderPeer_TL_inputFolderPeer() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputFolderPeer.TL_inputFolderPeer::class,
          org.Tajgram.tgnet.TLRPC.TL_inputFolderPeer::TLdeserialize, null)

    }

    @Test
    public fun test_000307_InputGame_TL_inputGameID() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputGame.TL_inputGameID::class,
          org.Tajgram.tgnet.TLRPC.InputGame::TLdeserialize, null)

    }

    @Test
    public fun test_000308_InputGame_TL_inputGameShortName() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputGame.TL_inputGameShortName::class,
          org.Tajgram.tgnet.TLRPC.InputGame::TLdeserialize, null)

    }

    @Test
    public fun test_000309_InputGeoPoint_TL_inputGeoPoint() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputGeoPoint.TL_inputGeoPoint::class,
          org.Tajgram.tgnet.TLRPC.InputGeoPoint::TLdeserialize, null)

    }

    @Test
    public fun test_000310_InputGeoPoint_TL_inputGeoPointEmpty() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputGeoPoint.TL_inputGeoPointEmpty::class,
          org.Tajgram.tgnet.TLRPC.InputGeoPoint::TLdeserialize, null)

    }

    @Test
    public fun test_000311_InputGroupCall_TL_inputGroupCall() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputGroupCall.TL_inputGroupCall::class,
          org.Tajgram.tgnet.TLRPC.InputGroupCall::TLdeserialize, null)

    }

    @Test
    public fun test_000312_InputGroupCall_TL_inputGroupCallInviteMessage() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputGroupCall.TL_inputGroupCallInviteMessage::class,
          org.Tajgram.tgnet.TLRPC.InputGroupCall::TLdeserialize, null)

    }

    @Test
    public fun test_000313_InputGroupCall_TL_inputGroupCallSlug() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputGroupCall.TL_inputGroupCallSlug::class,
          org.Tajgram.tgnet.TLRPC.InputGroupCall::TLdeserialize, null)

    }

    @Test
    public fun test_000314_InputInvoice_TL_inputInvoiceChatInviteSubscription() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputInvoice.TL_inputInvoiceChatInviteSubscription::class,
          org.Tajgram.tgnet.TLRPC.InputInvoice::TLdeserialize, null)

    }

    @Test
    public fun test_000315_InputInvoice_TL_inputInvoiceMessage() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputInvoice.TL_inputInvoiceMessage::class,
          org.Tajgram.tgnet.TLRPC.InputInvoice::TLdeserialize, null)

    }

    @Test
    public fun test_000316_InputInvoice_TL_inputInvoicePremiumAuthCode() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputInvoice.TL_inputInvoicePremiumAuthCode::class,
          org.Tajgram.tgnet.TLRPC.InputInvoice::TLdeserialize, null)

    }

    @Test
    public fun test_000317_InputInvoice_TL_inputInvoicePremiumGiftCode() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputInvoice.TL_inputInvoicePremiumGiftCode::class,
          org.Tajgram.tgnet.TLRPC.InputInvoice::TLdeserialize, null)

    }

    @Test
    public fun test_000318_InputInvoice_TL_inputInvoicePremiumGiftStars() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputInvoice.TL_inputInvoicePremiumGiftStars::class,
          org.Tajgram.tgnet.TLRPC.InputInvoice::TLdeserialize, null)

    }

    @Test
    public fun test_000319_InputInvoice_TL_inputInvoiceSlug() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputInvoice.TL_inputInvoiceSlug::class,
          org.Tajgram.tgnet.TLRPC.InputInvoice::TLdeserialize, null)

    }

    @Test
    public fun test_000320_InputInvoice_TL_inputInvoiceStarGift() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputInvoice.TL_inputInvoiceStarGift::class,
          org.Tajgram.tgnet.TLRPC.InputInvoice::TLdeserialize, null)

    }

    @Test
    public fun test_000321_InputInvoice_TL_inputInvoiceStarGiftAuctionBid() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputInvoice.TL_inputInvoiceStarGiftAuctionBid::class,
          org.Tajgram.tgnet.TLRPC.InputInvoice::TLdeserialize, null)

    }

    @Test
    public fun test_000322_InputInvoice_TL_inputInvoiceStarGiftDropOriginalDetails() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputInvoice.TL_inputInvoiceStarGiftDropOriginalDetails::class,
          org.Tajgram.tgnet.TLRPC.InputInvoice::TLdeserialize, null)

    }

    @Test
    public fun test_000323_InputInvoice_TL_inputInvoiceStarGiftPrepaidUpgrade() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputInvoice.TL_inputInvoiceStarGiftPrepaidUpgrade::class,
          org.Tajgram.tgnet.TLRPC.InputInvoice::TLdeserialize, null)

    }

    @Test
    public fun test_000324_InputInvoice_TL_inputInvoiceStarGiftResale() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputInvoice.TL_inputInvoiceStarGiftResale::class,
          org.Tajgram.tgnet.TLRPC.InputInvoice::TLdeserialize, null)

    }

    @Test
    public fun test_000325_InputInvoice_TL_inputInvoiceStarGiftTransfer() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputInvoice.TL_inputInvoiceStarGiftTransfer::class,
          org.Tajgram.tgnet.TLRPC.InputInvoice::TLdeserialize, null)

    }

    @Test
    public fun test_000326_InputInvoice_TL_inputInvoiceStarGiftUpgrade() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputInvoice.TL_inputInvoiceStarGiftUpgrade::class,
          org.Tajgram.tgnet.TLRPC.InputInvoice::TLdeserialize, null)

    }

    @Test
    public fun test_000327_InputInvoice_TL_inputInvoiceStars() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputInvoice.TL_inputInvoiceStars::class,
          org.Tajgram.tgnet.TLRPC.InputInvoice::TLdeserialize, null)

    }

    @Test
    public fun test_000328_InputMedia_TL_inputMediaContact() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputMedia.TL_inputMediaContact::class,
          org.Tajgram.tgnet.TLRPC.InputMedia::TLdeserialize, null)

    }

    @Test
    public fun test_000329_InputMedia_TL_inputMediaDice() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputMedia.TL_inputMediaDice::class,
          org.Tajgram.tgnet.TLRPC.InputMedia::TLdeserialize, null)

    }

    @Test
    public fun test_000330_InputMedia_TL_inputMediaDocument() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputMedia.TL_inputMediaDocument::class,
          org.Tajgram.tgnet.TLRPC.InputMedia::TLdeserialize, null)

    }

    @Test
    public fun test_000331_InputMedia_TL_inputMediaDocumentExternal() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputMedia.TL_inputMediaDocumentExternal::class,
          org.Tajgram.tgnet.TLRPC.InputMedia::TLdeserialize, null)

    }

    @Test
    public fun test_000332_InputMedia_TL_inputMediaEmpty() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputMedia.TL_inputMediaEmpty::class,
          org.Tajgram.tgnet.TLRPC.InputMedia::TLdeserialize, null)

    }

    @Test
    public fun test_000333_InputMedia_TL_inputMediaGame() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputMedia.TL_inputMediaGame::class,
          org.Tajgram.tgnet.TLRPC.InputMedia::TLdeserialize, null)

    }

    @Test
    public fun test_000334_InputMedia_TL_inputMediaGeoLive() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputMedia.TL_inputMediaGeoLive::class,
          org.Tajgram.tgnet.TLRPC.InputMedia::TLdeserialize, null)

    }

    @Test
    public fun test_000335_InputMedia_TL_inputMediaGeoPoint() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputMedia.TL_inputMediaGeoPoint::class,
          org.Tajgram.tgnet.TLRPC.InputMedia::TLdeserialize, null)

    }

    @Test
    public fun test_000336_InputMedia_TL_inputMediaInvoice() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputMedia.TL_inputMediaInvoice::class,
          org.Tajgram.tgnet.TLRPC.InputMedia::TLdeserialize, null)

    }

    @Test
    public fun test_000337_InputMedia_TL_inputMediaPaidMedia() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputMedia.TL_inputMediaPaidMedia::class,
          org.Tajgram.tgnet.TLRPC.InputMedia::TLdeserialize, null)

    }

    @Test
    public fun test_000338_InputMedia_TL_inputMediaPhoto() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputMedia.TL_inputMediaPhoto::class,
          org.Tajgram.tgnet.TLRPC.InputMedia::TLdeserialize, null)

    }

    @Test
    public fun test_000339_InputMedia_TL_inputMediaPhotoExternal() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputMedia.TL_inputMediaPhotoExternal::class,
          org.Tajgram.tgnet.TLRPC.InputMedia::TLdeserialize, null)

    }

    @Test
    public fun test_000340_InputMedia_TL_inputMediaPoll() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputMedia.TL_inputMediaPoll::class,
          org.Tajgram.tgnet.TLRPC.InputMedia::TLdeserialize, null)

    }

    @Test
    public fun test_000341_InputMedia_TL_inputMediaStakeDice() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputMedia.TL_inputMediaStakeDice::class,
          org.Tajgram.tgnet.TLRPC.InputMedia::TLdeserialize, null)

    }

    @Test
    public fun test_000342_InputMedia_TL_inputMediaStory() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputMedia.TL_inputMediaStory::class,
          org.Tajgram.tgnet.TLRPC.InputMedia::TLdeserialize, null)

    }

    @Test
    public fun test_000343_InputMedia_TL_inputMediaTodo() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputMedia.TL_inputMediaTodo::class,
          org.Tajgram.tgnet.TLRPC.InputMedia::TLdeserialize, null)

    }

    @Test
    public fun test_000344_InputMedia_TL_inputMediaUploadedDocument() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputMedia.TL_inputMediaUploadedDocument::class,
          org.Tajgram.tgnet.TLRPC.InputMedia::TLdeserialize, null)

    }

    @Test
    public fun test_000345_InputMedia_TL_inputMediaUploadedPhoto() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputMedia.TL_inputMediaUploadedPhoto::class,
          org.Tajgram.tgnet.TLRPC.InputMedia::TLdeserialize, null)

    }

    @Test
    public fun test_000346_InputMedia_TL_inputMediaVenue() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputMedia.TL_inputMediaVenue::class,
          org.Tajgram.tgnet.TLRPC.InputMedia::TLdeserialize, null)

    }

    @Test
    public fun test_000347_InputMedia_TL_inputMediaWebPage() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputMedia.TL_inputMediaWebPage::class,
          org.Tajgram.tgnet.TLRPC.InputMedia::TLdeserialize, null)

    }

    @Test
    public fun test_000348_InputMessageReadMetric_TL_inputMessageReadMetric() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputMessageReadMetric.TL_inputMessageReadMetric::class,
          org.Tajgram.tgnet.TLRPC.TL_inputMessageReadMetric::TLdeserialize, null)

    }

    @Test
    public fun test_000349_InputNotifyPeer_TL_inputNotifyBroadcasts() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputNotifyPeer.TL_inputNotifyBroadcasts::class,
          org.Tajgram.tgnet.TLRPC.InputNotifyPeer::TLdeserialize, null)

    }

    @Test
    public fun test_000350_InputNotifyPeer_TL_inputNotifyChats() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputNotifyPeer.TL_inputNotifyChats::class,
          org.Tajgram.tgnet.TLRPC.InputNotifyPeer::TLdeserialize, null)

    }

    @Test
    public fun test_000351_InputNotifyPeer_TL_inputNotifyForumTopic() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputNotifyPeer.TL_inputNotifyForumTopic::class,
          org.Tajgram.tgnet.TLRPC.InputNotifyPeer::TLdeserialize, null)

    }

    @Test
    public fun test_000352_InputNotifyPeer_TL_inputNotifyPeer() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputNotifyPeer.TL_inputNotifyPeer::class,
          org.Tajgram.tgnet.TLRPC.InputNotifyPeer::TLdeserialize, null)

    }

    @Test
    public fun test_000353_InputNotifyPeer_TL_inputNotifyUsers() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputNotifyPeer.TL_inputNotifyUsers::class,
          org.Tajgram.tgnet.TLRPC.InputNotifyPeer::TLdeserialize, null)

    }

    @Test
    public fun test_000354_InputPasskeyCredential_TL_inputPasskeyCredentialPublicKey() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputPasskeyCredential.TL_inputPasskeyCredentialPublicKey::class,
          org.Tajgram.tgnet.tl.TL_account.inputPasskeyCredentialPublicKey::TLdeserialize, null)

    }

    @Test
    public fun test_000355_InputPasskeyResponse_TL_inputPasskeyResponseLogin() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputPasskeyResponse.TL_inputPasskeyResponseLogin::class,
          org.Tajgram.tgnet.tl.TL_account.InputPasskeyResponse::TLdeserialize, null)

    }

    @Test
    public fun test_000356_InputPasskeyResponse_TL_inputPasskeyResponseRegister() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputPasskeyResponse.TL_inputPasskeyResponseRegister::class,
          org.Tajgram.tgnet.tl.TL_account.InputPasskeyResponse::TLdeserialize, null)

    }

    @Test
    public fun test_000357_InputPaymentCredentials_TL_inputPaymentCredentials() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputPaymentCredentials.TL_inputPaymentCredentials::class,
          org.Tajgram.tgnet.TLRPC.InputPaymentCredentials::TLdeserialize, null)

    }

    @Test
    public fun test_000358_InputPaymentCredentials_TL_inputPaymentCredentialsGooglePay() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputPaymentCredentials.TL_inputPaymentCredentialsGooglePay::class,
          org.Tajgram.tgnet.TLRPC.InputPaymentCredentials::TLdeserialize, null)

    }

    @Test
    public fun test_000359_InputPaymentCredentials_TL_inputPaymentCredentialsSaved() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputPaymentCredentials.TL_inputPaymentCredentialsSaved::class,
          org.Tajgram.tgnet.TLRPC.InputPaymentCredentials::TLdeserialize, null)

    }

    @Test
    public fun test_000360_InputPeer_TL_inputPeerChannel() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputPeer.TL_inputPeerChannel::class,
          org.Tajgram.tgnet.TLRPC.InputPeer::TLdeserialize, null)

    }

    @Test
    public fun test_000361_InputPeer_TL_inputPeerChannelFromMessage() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputPeer.TL_inputPeerChannelFromMessage::class,
          org.Tajgram.tgnet.TLRPC.InputPeer::TLdeserialize, null)

    }

    @Test
    public fun test_000362_InputPeer_TL_inputPeerChat() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputPeer.TL_inputPeerChat::class,
          org.Tajgram.tgnet.TLRPC.InputPeer::TLdeserialize, null)

    }

    @Test
    public fun test_000363_InputPeer_TL_inputPeerEmpty() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputPeer.TL_inputPeerEmpty::class,
          org.Tajgram.tgnet.TLRPC.InputPeer::TLdeserialize, null)

    }

    @Test
    public fun test_000364_InputPeer_TL_inputPeerSelf() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputPeer.TL_inputPeerSelf::class,
          org.Tajgram.tgnet.TLRPC.InputPeer::TLdeserialize, null)

    }

    @Test
    public fun test_000365_InputPeer_TL_inputPeerUser() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputPeer.TL_inputPeerUser::class,
          org.Tajgram.tgnet.TLRPC.InputPeer::TLdeserialize, null)

    }

    @Test
    public fun test_000366_InputPeer_TL_inputPeerUserFromMessage() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputPeer.TL_inputPeerUserFromMessage::class,
          org.Tajgram.tgnet.TLRPC.InputPeer::TLdeserialize, null)

    }

    @Test
    public fun test_000367_InputPeerNotifySettings_TL_inputPeerNotifySettings() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputPeerNotifySettings.TL_inputPeerNotifySettings::class,
          org.Tajgram.tgnet.TLRPC.TL_inputPeerNotifySettings::TLdeserialize, null)

    }

    @Test
    public fun test_000368_InputPhoneCall_TL_inputPhoneCall() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputPhoneCall.TL_inputPhoneCall::class,
          org.Tajgram.tgnet.TLRPC.TL_inputPhoneCall::TLdeserialize, null)

    }

    @Test
    public fun test_000369_InputPhoto_TL_inputPhoto() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputPhoto.TL_inputPhoto::class,
          org.Tajgram.tgnet.TLRPC.InputPhoto::TLdeserialize, null)

    }

    @Test
    public fun test_000370_InputPhoto_TL_inputPhotoEmpty() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputPhoto.TL_inputPhotoEmpty::class,
          org.Tajgram.tgnet.TLRPC.InputPhoto::TLdeserialize, null)

    }

    @Test
    public fun test_000371_InputPrivacyKey_TL_inputPrivacyKeyAbout() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputPrivacyKey.TL_inputPrivacyKeyAbout::class,
          org.Tajgram.tgnet.TLRPC.InputPrivacyKey::TLdeserialize, null)

    }

    @Test
    public fun test_000372_InputPrivacyKey_TL_inputPrivacyKeyAddedByPhone() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputPrivacyKey.TL_inputPrivacyKeyAddedByPhone::class,
          org.Tajgram.tgnet.TLRPC.InputPrivacyKey::TLdeserialize, null)

    }

    @Test
    public fun test_000373_InputPrivacyKey_TL_inputPrivacyKeyBirthday() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputPrivacyKey.TL_inputPrivacyKeyBirthday::class,
          org.Tajgram.tgnet.TLRPC.InputPrivacyKey::TLdeserialize, null)

    }

    @Test
    public fun test_000374_InputPrivacyKey_TL_inputPrivacyKeyChatInvite() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputPrivacyKey.TL_inputPrivacyKeyChatInvite::class,
          org.Tajgram.tgnet.TLRPC.InputPrivacyKey::TLdeserialize, null)

    }

    @Test
    public fun test_000375_InputPrivacyKey_TL_inputPrivacyKeyForwards() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputPrivacyKey.TL_inputPrivacyKeyForwards::class,
          org.Tajgram.tgnet.TLRPC.InputPrivacyKey::TLdeserialize, null)

    }

    @Test
    public fun test_000376_InputPrivacyKey_TL_inputPrivacyKeyNoPaidMessages() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputPrivacyKey.TL_inputPrivacyKeyNoPaidMessages::class,
          org.Tajgram.tgnet.TLRPC.InputPrivacyKey::TLdeserialize, null)

    }

    @Test
    public fun test_000377_InputPrivacyKey_TL_inputPrivacyKeyPhoneCall() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputPrivacyKey.TL_inputPrivacyKeyPhoneCall::class,
          org.Tajgram.tgnet.TLRPC.InputPrivacyKey::TLdeserialize, null)

    }

    @Test
    public fun test_000378_InputPrivacyKey_TL_inputPrivacyKeyPhoneNumber() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputPrivacyKey.TL_inputPrivacyKeyPhoneNumber::class,
          org.Tajgram.tgnet.TLRPC.InputPrivacyKey::TLdeserialize, null)

    }

    @Test
    public fun test_000379_InputPrivacyKey_TL_inputPrivacyKeyPhoneP2P() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputPrivacyKey.TL_inputPrivacyKeyPhoneP2P::class,
          org.Tajgram.tgnet.TLRPC.InputPrivacyKey::TLdeserialize, null)

    }

    @Test
    public fun test_000380_InputPrivacyKey_TL_inputPrivacyKeyProfilePhoto() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputPrivacyKey.TL_inputPrivacyKeyProfilePhoto::class,
          org.Tajgram.tgnet.TLRPC.InputPrivacyKey::TLdeserialize, null)

    }

    @Test
    public fun test_000381_InputPrivacyKey_TL_inputPrivacyKeySavedMusic() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputPrivacyKey.TL_inputPrivacyKeySavedMusic::class,
          org.Tajgram.tgnet.TLRPC.InputPrivacyKey::TLdeserialize, null)

    }

    @Test
    public fun test_000382_InputPrivacyKey_TL_inputPrivacyKeyStarGiftsAutoSave() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputPrivacyKey.TL_inputPrivacyKeyStarGiftsAutoSave::class,
          org.Tajgram.tgnet.TLRPC.InputPrivacyKey::TLdeserialize, null)

    }

    @Test
    public fun test_000383_InputPrivacyKey_TL_inputPrivacyKeyStatusTimestamp() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputPrivacyKey.TL_inputPrivacyKeyStatusTimestamp::class,
          org.Tajgram.tgnet.TLRPC.InputPrivacyKey::TLdeserialize, null)

    }

    @Test
    public fun test_000384_InputPrivacyKey_TL_inputPrivacyKeyVoiceMessages() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputPrivacyKey.TL_inputPrivacyKeyVoiceMessages::class,
          org.Tajgram.tgnet.TLRPC.InputPrivacyKey::TLdeserialize, null)

    }

    @Test
    public fun test_000385_InputPrivacyRule_TL_inputPrivacyValueAllowAll() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputPrivacyRule.TL_inputPrivacyValueAllowAll::class,
          org.Tajgram.tgnet.TLRPC.InputPrivacyRule::TLdeserialize, null)

    }

    @Test
    public fun test_000386_InputPrivacyRule_TL_inputPrivacyValueAllowBots() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputPrivacyRule.TL_inputPrivacyValueAllowBots::class,
          org.Tajgram.tgnet.TLRPC.InputPrivacyRule::TLdeserialize, null)

    }

    @Test
    public fun test_000387_InputPrivacyRule_TL_inputPrivacyValueAllowChatParticipants() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputPrivacyRule.TL_inputPrivacyValueAllowChatParticipants::class,
          org.Tajgram.tgnet.TLRPC.InputPrivacyRule::TLdeserialize, null)

    }

    @Test
    public fun test_000388_InputPrivacyRule_TL_inputPrivacyValueAllowCloseFriends() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputPrivacyRule.TL_inputPrivacyValueAllowCloseFriends::class,
          org.Tajgram.tgnet.TLRPC.InputPrivacyRule::TLdeserialize, null)

    }

    @Test
    public fun test_000389_InputPrivacyRule_TL_inputPrivacyValueAllowContacts() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputPrivacyRule.TL_inputPrivacyValueAllowContacts::class,
          org.Tajgram.tgnet.TLRPC.InputPrivacyRule::TLdeserialize, null)

    }

    @Test
    public fun test_000390_InputPrivacyRule_TL_inputPrivacyValueAllowPremium() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputPrivacyRule.TL_inputPrivacyValueAllowPremium::class,
          org.Tajgram.tgnet.TLRPC.InputPrivacyRule::TLdeserialize, null)

    }

    @Test
    public fun test_000391_InputPrivacyRule_TL_inputPrivacyValueAllowUsers() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputPrivacyRule.TL_inputPrivacyValueAllowUsers::class,
          org.Tajgram.tgnet.TLRPC.InputPrivacyRule::TLdeserialize, null)

    }

    @Test
    public fun test_000392_InputPrivacyRule_TL_inputPrivacyValueDisallowAll() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputPrivacyRule.TL_inputPrivacyValueDisallowAll::class,
          org.Tajgram.tgnet.TLRPC.InputPrivacyRule::TLdeserialize, null)

    }

    @Test
    public fun test_000393_InputPrivacyRule_TL_inputPrivacyValueDisallowBots() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputPrivacyRule.TL_inputPrivacyValueDisallowBots::class,
          org.Tajgram.tgnet.TLRPC.InputPrivacyRule::TLdeserialize, null)

    }

    @Test
    public fun test_000394_InputPrivacyRule_TL_inputPrivacyValueDisallowChatParticipants() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputPrivacyRule.TL_inputPrivacyValueDisallowChatParticipants::class,
          org.Tajgram.tgnet.TLRPC.InputPrivacyRule::TLdeserialize, null)

    }

    @Test
    public fun test_000395_InputPrivacyRule_TL_inputPrivacyValueDisallowContacts() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputPrivacyRule.TL_inputPrivacyValueDisallowContacts::class,
          org.Tajgram.tgnet.TLRPC.InputPrivacyRule::TLdeserialize, null)

    }

    @Test
    public fun test_000396_InputPrivacyRule_TL_inputPrivacyValueDisallowUsers() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputPrivacyRule.TL_inputPrivacyValueDisallowUsers::class,
          org.Tajgram.tgnet.TLRPC.InputPrivacyRule::TLdeserialize, null)

    }

    @Test
    public fun test_000397_InputQuickReplyShortcut_TL_inputQuickReplyShortcut() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputQuickReplyShortcut.TL_inputQuickReplyShortcut::class,
          org.Tajgram.tgnet.TLRPC.InputQuickReplyShortcut::TLdeserialize, null)

    }

    @Test
    public fun test_000398_InputQuickReplyShortcut_TL_inputQuickReplyShortcutId() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputQuickReplyShortcut.TL_inputQuickReplyShortcutId::class,
          org.Tajgram.tgnet.TLRPC.InputQuickReplyShortcut::TLdeserialize, null)

    }

    @Test
    public fun test_000399_InputReplyTo_TL_inputReplyToMessage() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputReplyTo.TL_inputReplyToMessage::class,
          org.Tajgram.tgnet.TLRPC.InputReplyTo::TLdeserialize, null)

    }

    @Test
    public fun test_000400_InputReplyTo_TL_inputReplyToMonoForum() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputReplyTo.TL_inputReplyToMonoForum::class,
          org.Tajgram.tgnet.TLRPC.InputReplyTo::TLdeserialize, null)

    }

    @Test
    public fun test_000401_InputReplyTo_TL_inputReplyToStory() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputReplyTo.TL_inputReplyToStory::class,
          org.Tajgram.tgnet.TLRPC.InputReplyTo::TLdeserialize, null)

    }

    @Test
    public fun test_000402_InputRichMessage_TL_inputRichMessage() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputRichMessage.TL_inputRichMessage::class,
          org.Tajgram.tgnet.tl.TL_iv.TL_inputRichMessage::TLdeserialize, null)

    }

    @Test
    public fun test_000403_InputSavedStarGift_TL_inputSavedStarGiftChat() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputSavedStarGift.TL_inputSavedStarGiftChat::class,
          org.Tajgram.tgnet.tl.TL_stars.InputSavedStarGift::TLdeserialize, null)

    }

    @Test
    public fun test_000404_InputSavedStarGift_TL_inputSavedStarGiftSlug() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputSavedStarGift.TL_inputSavedStarGiftSlug::class,
          org.Tajgram.tgnet.tl.TL_stars.InputSavedStarGift::TLdeserialize, null)

    }

    @Test
    public fun test_000405_InputSavedStarGift_TL_inputSavedStarGiftUser() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputSavedStarGift.TL_inputSavedStarGiftUser::class,
          org.Tajgram.tgnet.tl.TL_stars.InputSavedStarGift::TLdeserialize, null)

    }

    @Test
    public fun test_000406_InputSecureFile_TL_inputSecureFile() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputSecureFile.TL_inputSecureFile::class,
          org.Tajgram.tgnet.TLRPC.InputSecureFile::TLdeserialize, null)

    }

    @Test
    public fun test_000407_InputSecureFile_TL_inputSecureFileUploaded() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputSecureFile.TL_inputSecureFileUploaded::class,
          org.Tajgram.tgnet.TLRPC.InputSecureFile::TLdeserialize, null)

    }

    @Test
    public fun test_000408_InputSecureValue_TL_inputSecureValue() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputSecureValue.TL_inputSecureValue::class,
          org.Tajgram.tgnet.TLRPC.TL_inputSecureValue::TLdeserialize, null)

    }

    @Test
    public fun test_000409_InputSingleMedia_TL_inputSingleMedia() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputSingleMedia.TL_inputSingleMedia::class,
          org.Tajgram.tgnet.TLRPC.TL_inputSingleMedia::TLdeserialize, null)

    }

    @Test
    public fun test_000410_InputStarGiftAuction_TL_inputStarGiftAuction() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputStarGiftAuction.TL_inputStarGiftAuction::class,
          org.Tajgram.tgnet.tl.TL_stars.InputStarGiftAuction::TLdeserialize, null)

    }

    @Test
    public fun test_000411_InputStarGiftAuction_TL_inputStarGiftAuctionSlug() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputStarGiftAuction.TL_inputStarGiftAuctionSlug::class,
          org.Tajgram.tgnet.tl.TL_stars.InputStarGiftAuction::TLdeserialize, null)

    }

    @Test
    public fun test_000412_InputStickerSet_TL_inputStickerSetAnimatedEmoji() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputStickerSet.TL_inputStickerSetAnimatedEmoji::class,
          org.Tajgram.tgnet.TLRPC.InputStickerSet::TLdeserialize, null)

    }

    @Test
    public fun test_000413_InputStickerSet_TL_inputStickerSetDice() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputStickerSet.TL_inputStickerSetDice::class,
          org.Tajgram.tgnet.TLRPC.InputStickerSet::TLdeserialize, null)

    }

    @Test
    public fun test_000414_InputStickerSet_TL_inputStickerSetEmojiChannelDefaultStatuses() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputStickerSet.TL_inputStickerSetEmojiChannelDefaultStatuses::class,
          org.Tajgram.tgnet.TLRPC.InputStickerSet::TLdeserialize, null)

    }

    @Test
    public fun test_000415_InputStickerSet_TL_inputStickerSetEmojiDefaultStatuses() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputStickerSet.TL_inputStickerSetEmojiDefaultStatuses::class,
          org.Tajgram.tgnet.TLRPC.InputStickerSet::TLdeserialize, null)

    }

    @Test
    public fun test_000416_InputStickerSet_TL_inputStickerSetEmojiDefaultTopicIcons() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputStickerSet.TL_inputStickerSetEmojiDefaultTopicIcons::class,
          org.Tajgram.tgnet.TLRPC.InputStickerSet::TLdeserialize, null)

    }

    @Test
    public fun test_000417_InputStickerSet_TL_inputStickerSetEmojiGenericAnimations() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputStickerSet.TL_inputStickerSetEmojiGenericAnimations::class,
          org.Tajgram.tgnet.TLRPC.InputStickerSet::TLdeserialize, null)

    }

    @Test
    public fun test_000418_InputStickerSet_TL_inputStickerSetEmpty() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputStickerSet.TL_inputStickerSetEmpty::class,
          org.Tajgram.tgnet.TLRPC.InputStickerSet::TLdeserialize, null)

    }

    @Test
    public fun test_000419_InputStickerSet_TL_inputStickerSetID() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputStickerSet.TL_inputStickerSetID::class,
          org.Tajgram.tgnet.TLRPC.InputStickerSet::TLdeserialize, null)

    }

    @Test
    public fun test_000420_InputStickerSet_TL_inputStickerSetPremiumGifts() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputStickerSet.TL_inputStickerSetPremiumGifts::class,
          org.Tajgram.tgnet.TLRPC.InputStickerSet::TLdeserialize, null)

    }

    @Test
    public fun test_000421_InputStickerSet_TL_inputStickerSetShortName() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputStickerSet.TL_inputStickerSetShortName::class,
          org.Tajgram.tgnet.TLRPC.InputStickerSet::TLdeserialize, null)

    }

    @Test
    public fun test_000422_InputStickerSet_TL_inputStickerSetTonGifts() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputStickerSet.TL_inputStickerSetTonGifts::class,
          org.Tajgram.tgnet.TLRPC.InputStickerSet::TLdeserialize, null)

    }

    @Test
    public fun test_000423_InputStickeredMedia_TL_inputStickeredMediaDocument() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputStickeredMedia.TL_inputStickeredMediaDocument::class,
          org.Tajgram.tgnet.TLRPC.InputStickeredMedia::TLdeserialize, null)

    }

    @Test
    public fun test_000424_InputStickeredMedia_TL_inputStickeredMediaPhoto() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputStickeredMedia.TL_inputStickeredMediaPhoto::class,
          org.Tajgram.tgnet.TLRPC.InputStickeredMedia::TLdeserialize, null)

    }

    @Test
    public fun test_000425_InputStorePaymentPurpose_TL_inputStorePaymentAuthCode() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputStorePaymentPurpose.TL_inputStorePaymentAuthCode::class,
          org.Tajgram.tgnet.TLRPC.InputStorePaymentPurpose::TLdeserialize, null)

    }

    @Test
    public fun test_000426_InputStorePaymentPurpose_TL_inputStorePaymentGiftPremium() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputStorePaymentPurpose.TL_inputStorePaymentGiftPremium::class,
          org.Tajgram.tgnet.TLRPC.InputStorePaymentPurpose::TLdeserialize, null)

    }

    @Test
    public fun test_000427_InputStorePaymentPurpose_TL_inputStorePaymentPremiumGiftCode() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputStorePaymentPurpose.TL_inputStorePaymentPremiumGiftCode::class,
          org.Tajgram.tgnet.TLRPC.InputStorePaymentPurpose::TLdeserialize, null)

    }

    @Test
    public fun test_000428_InputStorePaymentPurpose_TL_inputStorePaymentPremiumGiveaway() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputStorePaymentPurpose.TL_inputStorePaymentPremiumGiveaway::class,
          org.Tajgram.tgnet.TLRPC.InputStorePaymentPurpose::TLdeserialize, null)

    }

    @Test
    public fun test_000429_InputStorePaymentPurpose_TL_inputStorePaymentPremiumSubscription() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputStorePaymentPurpose.TL_inputStorePaymentPremiumSubscription::class,
          org.Tajgram.tgnet.TLRPC.InputStorePaymentPurpose::TLdeserialize, null)

    }

    @Test
    public fun test_000430_InputStorePaymentPurpose_TL_inputStorePaymentStarsGift() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputStorePaymentPurpose.TL_inputStorePaymentStarsGift::class,
          org.Tajgram.tgnet.TLRPC.InputStorePaymentPurpose::TLdeserialize, null)

    }

    @Test
    public fun test_000431_InputStorePaymentPurpose_TL_inputStorePaymentStarsGiveaway() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputStorePaymentPurpose.TL_inputStorePaymentStarsGiveaway::class,
          org.Tajgram.tgnet.TLRPC.InputStorePaymentPurpose::TLdeserialize, null)

    }

    @Test
    public fun test_000432_InputStorePaymentPurpose_TL_inputStorePaymentStarsTopup() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputStorePaymentPurpose.TL_inputStorePaymentStarsTopup::class,
          org.Tajgram.tgnet.TLRPC.InputStorePaymentPurpose::TLdeserialize, null)

    }

    @Test
    public fun test_000433_InputTheme_TL_inputTheme() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputTheme.TL_inputTheme::class,
          org.Tajgram.tgnet.TLRPC.InputTheme::TLdeserialize, null)

    }

    @Test
    public fun test_000434_InputTheme_TL_inputThemeSlug() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputTheme.TL_inputThemeSlug::class,
          org.Tajgram.tgnet.TLRPC.InputTheme::TLdeserialize, null)

    }

    @Test
    public fun test_000435_InputThemeSettings_TL_inputThemeSettings() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputThemeSettings.TL_inputThemeSettings::class,
          org.Tajgram.tgnet.TLRPC.TL_inputThemeSettings::TLdeserialize, null)

    }

    @Test
    public fun test_000436_InputUser_TL_inputUser() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputUser.TL_inputUser::class,
          org.Tajgram.tgnet.TLRPC.InputUser::TLdeserialize, null)

    }

    @Test
    public fun test_000437_InputUser_TL_inputUserEmpty() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputUser.TL_inputUserEmpty::class,
          org.Tajgram.tgnet.TLRPC.InputUser::TLdeserialize, null)

    }

    @Test
    public fun test_000438_InputUser_TL_inputUserFromMessage() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputUser.TL_inputUserFromMessage::class,
          org.Tajgram.tgnet.TLRPC.InputUser::TLdeserialize, null)

    }

    @Test
    public fun test_000439_InputUser_TL_inputUserSelf() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputUser.TL_inputUserSelf::class,
          org.Tajgram.tgnet.TLRPC.InputUser::TLdeserialize, null)

    }

    @Test
    public fun test_000440_InputWallPaper_TL_inputWallPaper() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputWallPaper.TL_inputWallPaper::class,
          org.Tajgram.tgnet.TLRPC.InputWallPaper::TLdeserialize, null)

    }

    @Test
    public fun test_000441_InputWallPaper_TL_inputWallPaperNoFile() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputWallPaper.TL_inputWallPaperNoFile::class,
          org.Tajgram.tgnet.TLRPC.InputWallPaper::TLdeserialize, null)

    }

    @Test
    public fun test_000442_InputWallPaper_TL_inputWallPaperSlug() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputWallPaper.TL_inputWallPaperSlug::class,
          org.Tajgram.tgnet.TLRPC.InputWallPaper::TLdeserialize, null)

    }

    @Test
    public fun test_000443_InputWebDocument_TL_inputWebDocument() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputWebDocument.TL_inputWebDocument::class,
          org.Tajgram.tgnet.TLRPC.TL_inputWebDocument::TLdeserialize, null)

    }

    @Test
    public fun test_000444_InputWebFileLocation_TL_inputWebFileGeoPointLocation() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputWebFileLocation.TL_inputWebFileGeoPointLocation::class,
          org.Tajgram.tgnet.TLRPC.InputWebFileLocation::TLdeserialize, null)

    }

    @Test
    public fun test_000445_InputWebFileLocation_TL_inputWebFileLocation() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputWebFileLocation.TL_inputWebFileLocation::class,
          org.Tajgram.tgnet.TLRPC.InputWebFileLocation::TLdeserialize, null)

    }

    @Test
    public fun test_000446_Invoice_TL_invoice() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Invoice.TL_invoice::class,
          org.Tajgram.tgnet.TLRPC.TL_invoice::TLdeserialize, null)

    }

    @Test
    public fun test_000447_JSONObjectValue_TL_jsonObjectValue() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_JSONObjectValue.TL_jsonObjectValue::class,
          org.Tajgram.tgnet.TLRPC.TL_jsonObjectValue::TLdeserialize, null)

    }

    @Test
    public fun test_000448_JSONValue_TL_jsonArray() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_JSONValue.TL_jsonArray::class,
          org.Tajgram.tgnet.TLRPC.JSONValue::TLdeserialize, null)

    }

    @Test
    public fun test_000449_JSONValue_TL_jsonBool() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_JSONValue.TL_jsonBool::class,
          org.Tajgram.tgnet.TLRPC.JSONValue::TLdeserialize, null)

    }

    @Test
    public fun test_000450_JSONValue_TL_jsonNull() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_JSONValue.TL_jsonNull::class,
          org.Tajgram.tgnet.TLRPC.JSONValue::TLdeserialize, null)

    }

    @Test
    public fun test_000451_JSONValue_TL_jsonNumber() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_JSONValue.TL_jsonNumber::class,
          org.Tajgram.tgnet.TLRPC.JSONValue::TLdeserialize, null)

    }

    @Test
    public fun test_000452_JSONValue_TL_jsonObject() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_JSONValue.TL_jsonObject::class,
          org.Tajgram.tgnet.TLRPC.JSONValue::TLdeserialize, null)

    }

    @Test
    public fun test_000453_JSONValue_TL_jsonString() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_JSONValue.TL_jsonString::class,
          org.Tajgram.tgnet.TLRPC.JSONValue::TLdeserialize, null)

    }

    @Test
    public fun test_000454_JoinChatBotResult_TL_joinChatBotResultApproved() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_JoinChatBotResult.TL_joinChatBotResultApproved::class,
          org.Tajgram.tgnet.TLRPC.JoinChatBotResult::TLdeserialize, null)

    }

    @Test
    public fun test_000455_JoinChatBotResult_TL_joinChatBotResultDeclined() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_JoinChatBotResult.TL_joinChatBotResultDeclined::class,
          org.Tajgram.tgnet.TLRPC.JoinChatBotResult::TLdeserialize, null)

    }

    @Test
    public fun test_000456_JoinChatBotResult_TL_joinChatBotResultQueued() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_JoinChatBotResult.TL_joinChatBotResultQueued::class,
          org.Tajgram.tgnet.TLRPC.JoinChatBotResult::TLdeserialize, null)

    }

    @Test
    public fun test_000457_JoinChatBotResult_TL_joinChatBotResultWebView() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_JoinChatBotResult.TL_joinChatBotResultWebView::class,
          org.Tajgram.tgnet.TLRPC.JoinChatBotResult::TLdeserialize, null)

    }

    @Test
    public fun test_000458_KeyboardButton_TL_inputKeyboardButtonUrlAuth() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_KeyboardButton.TL_inputKeyboardButtonUrlAuth::class,
          org.Tajgram.tgnet.TLRPC.KeyboardButton::TLdeserialize, null)

    }

    @Test
    public fun test_000459_KeyboardButton_TL_inputKeyboardButtonUserProfile() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_KeyboardButton.TL_inputKeyboardButtonUserProfile::class,
          org.Tajgram.tgnet.TLRPC.KeyboardButton::TLdeserialize, null)

    }

    @Test
    public fun test_000460_KeyboardButton_TL_keyboardButton() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_KeyboardButton.TL_keyboardButton::class,
          org.Tajgram.tgnet.TLRPC.KeyboardButton::TLdeserialize, null)

    }

    @Test
    public fun test_000461_KeyboardButton_TL_keyboardButtonBuy() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_KeyboardButton.TL_keyboardButtonBuy::class,
          org.Tajgram.tgnet.TLRPC.KeyboardButton::TLdeserialize, null)

    }

    @Test
    public fun test_000462_KeyboardButton_TL_keyboardButtonCallback() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_KeyboardButton.TL_keyboardButtonCallback::class,
          org.Tajgram.tgnet.TLRPC.KeyboardButton::TLdeserialize, null)

    }

    @Test
    public fun test_000463_KeyboardButton_TL_keyboardButtonCopy() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_KeyboardButton.TL_keyboardButtonCopy::class,
          org.Tajgram.tgnet.TLRPC.KeyboardButton::TLdeserialize, null)

    }

    @Test
    public fun test_000464_KeyboardButton_TL_keyboardButtonGame() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_KeyboardButton.TL_keyboardButtonGame::class,
          org.Tajgram.tgnet.TLRPC.KeyboardButton::TLdeserialize, null)

    }

    @Test
    public fun test_000465_KeyboardButton_TL_keyboardButtonRequestGeoLocation() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_KeyboardButton.TL_keyboardButtonRequestGeoLocation::class,
          org.Tajgram.tgnet.TLRPC.KeyboardButton::TLdeserialize, null)

    }

    @Test
    public fun test_000466_KeyboardButton_TL_keyboardButtonRequestPeer() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_KeyboardButton.TL_keyboardButtonRequestPeer::class,
          org.Tajgram.tgnet.TLRPC.KeyboardButton::TLdeserialize, null)

    }

    @Test
    public fun test_000467_KeyboardButton_TL_keyboardButtonRequestPhone() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_KeyboardButton.TL_keyboardButtonRequestPhone::class,
          org.Tajgram.tgnet.TLRPC.KeyboardButton::TLdeserialize, null)

    }

    @Test
    public fun test_000468_KeyboardButton_TL_keyboardButtonRequestPoll() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_KeyboardButton.TL_keyboardButtonRequestPoll::class,
          org.Tajgram.tgnet.TLRPC.KeyboardButton::TLdeserialize, null)

    }

    @Test
    public fun test_000469_KeyboardButton_TL_keyboardButtonSimpleWebView() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_KeyboardButton.TL_keyboardButtonSimpleWebView::class,
          org.Tajgram.tgnet.TLRPC.KeyboardButton::TLdeserialize, null)

    }

    @Test
    public fun test_000470_KeyboardButton_TL_keyboardButtonSwitchInline() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_KeyboardButton.TL_keyboardButtonSwitchInline::class,
          org.Tajgram.tgnet.TLRPC.KeyboardButton::TLdeserialize, null)

    }

    @Test
    public fun test_000471_KeyboardButton_TL_keyboardButtonUrl() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_KeyboardButton.TL_keyboardButtonUrl::class,
          org.Tajgram.tgnet.TLRPC.KeyboardButton::TLdeserialize, null)

    }

    @Test
    public fun test_000472_KeyboardButton_TL_keyboardButtonUrlAuth() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_KeyboardButton.TL_keyboardButtonUrlAuth::class,
          org.Tajgram.tgnet.TLRPC.KeyboardButton::TLdeserialize, null)

    }

    @Test
    public fun test_000473_KeyboardButton_TL_keyboardButtonUserProfile() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_KeyboardButton.TL_keyboardButtonUserProfile::class,
          org.Tajgram.tgnet.TLRPC.KeyboardButton::TLdeserialize, null)

    }

    @Test
    public fun test_000474_KeyboardButton_TL_keyboardButtonWebView() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_KeyboardButton.TL_keyboardButtonWebView::class,
          org.Tajgram.tgnet.TLRPC.KeyboardButton::TLdeserialize, null)

    }

    @Test
    public fun test_000475_KeyboardButtonRow_TL_keyboardButtonRow() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_KeyboardButtonRow.TL_keyboardButtonRow::class,
          org.Tajgram.tgnet.TLRPC.TL_keyboardButtonRow::TLdeserialize, null)

    }

    @Test
    public fun test_000476_KeyboardButtonStyle_TL_keyboardButtonStyle() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_KeyboardButtonStyle.TL_keyboardButtonStyle::class,
          org.Tajgram.tgnet.TLRPC.TL_keyboardButtonStyle::TLdeserialize, null)

    }

    @Test
    public fun test_000477_LabeledPrice_TL_labeledPrice() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_LabeledPrice.TL_labeledPrice::class,
          org.Tajgram.tgnet.TLRPC.TL_labeledPrice::TLdeserialize, null)

    }

    @Test
    public fun test_000478_LangPackDifference_TL_langPackDifference() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_LangPackDifference.TL_langPackDifference::class,
          org.Tajgram.tgnet.TLRPC.TL_langPackDifference::TLdeserialize, null)

    }

    @Test
    public fun test_000479_LangPackLanguage_TL_langPackLanguage() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_LangPackLanguage.TL_langPackLanguage::class,
          org.Tajgram.tgnet.TLRPC.TL_langPackLanguage::TLdeserialize, null)

    }

    @Test
    public fun test_000480_LangPackString_TL_langPackString() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_LangPackString.TL_langPackString::class,
          org.Tajgram.tgnet.TLRPC.LangPackString::TLdeserialize, null)

    }

    @Test
    public fun test_000481_LangPackString_TL_langPackStringDeleted() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_LangPackString.TL_langPackStringDeleted::class,
          org.Tajgram.tgnet.TLRPC.LangPackString::TLdeserialize, null)

    }

    @Test
    public fun test_000482_LangPackString_TL_langPackStringPluralized() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_LangPackString.TL_langPackStringPluralized::class,
          org.Tajgram.tgnet.TLRPC.LangPackString::TLdeserialize, null)

    }

    @Test
    public fun test_000483_MaskCoords_TL_maskCoords() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MaskCoords.TL_maskCoords::class,
          org.Tajgram.tgnet.TLRPC.TL_maskCoords::TLdeserialize, null)

    }

    @Test
    public fun test_000484_MediaArea_TL_inputMediaAreaChannelPost() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MediaArea.TL_inputMediaAreaChannelPost::class,
          org.Tajgram.tgnet.tl.TL_stories.MediaArea::TLdeserialize, null)

    }

    @Test
    public fun test_000485_MediaArea_TL_inputMediaAreaVenue() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MediaArea.TL_inputMediaAreaVenue::class,
          org.Tajgram.tgnet.tl.TL_stories.MediaArea::TLdeserialize, null)

    }

    @Test
    public fun test_000486_MediaArea_TL_mediaAreaChannelPost() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MediaArea.TL_mediaAreaChannelPost::class,
          org.Tajgram.tgnet.tl.TL_stories.MediaArea::TLdeserialize, null)

    }

    @Test
    public fun test_000487_MediaArea_TL_mediaAreaGeoPoint() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MediaArea.TL_mediaAreaGeoPoint::class,
          org.Tajgram.tgnet.tl.TL_stories.MediaArea::TLdeserialize, null)

    }

    @Test
    public fun test_000488_MediaArea_TL_mediaAreaStarGift() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MediaArea.TL_mediaAreaStarGift::class,
          org.Tajgram.tgnet.tl.TL_stories.MediaArea::TLdeserialize, null)

    }

    @Test
    public fun test_000489_MediaArea_TL_mediaAreaSuggestedReaction() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MediaArea.TL_mediaAreaSuggestedReaction::class,
          org.Tajgram.tgnet.tl.TL_stories.MediaArea::TLdeserialize, null)

    }

    @Test
    public fun test_000490_MediaArea_TL_mediaAreaUrl() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MediaArea.TL_mediaAreaUrl::class,
          org.Tajgram.tgnet.tl.TL_stories.MediaArea::TLdeserialize, null)

    }

    @Test
    public fun test_000491_MediaArea_TL_mediaAreaVenue() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MediaArea.TL_mediaAreaVenue::class,
          org.Tajgram.tgnet.tl.TL_stories.MediaArea::TLdeserialize, null)

    }

    @Test
    public fun test_000492_MediaArea_TL_mediaAreaWeather() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MediaArea.TL_mediaAreaWeather::class,
          org.Tajgram.tgnet.tl.TL_stories.MediaArea::TLdeserialize, null)

    }

    @Test
    public fun test_000493_MediaAreaCoordinates_TL_mediaAreaCoordinates() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MediaAreaCoordinates.TL_mediaAreaCoordinates::class,
          org.Tajgram.tgnet.tl.TL_stories.MediaAreaCoordinates::TLdeserialize, null)

    }

    @Test
    public fun test_000494_Message_TL_message() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Message.TL_message::class,
          org.Tajgram.tgnet.TLRPC.Message::TLdeserialize, null)

    }

    @Test
    public fun test_000495_Message_TL_messageEmpty() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Message.TL_messageEmpty::class,
          org.Tajgram.tgnet.TLRPC.Message::TLdeserialize, null)

    }

    @Test
    public fun test_000496_Message_TL_messageService() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Message.TL_messageService::class,
          org.Tajgram.tgnet.TLRPC.Message::TLdeserialize, null)

    }

    @Test
    public fun test_000497_MessageAction_TL_messageActionBoostApply() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageAction.TL_messageActionBoostApply::class,
          org.Tajgram.tgnet.TLRPC.MessageAction::TLdeserialize, null)

    }

    @Test
    public fun test_000498_MessageAction_TL_messageActionBotAllowed() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageAction.TL_messageActionBotAllowed::class,
          org.Tajgram.tgnet.TLRPC.MessageAction::TLdeserialize, null)

    }

    @Test
    public fun test_000499_MessageAction_TL_messageActionChangeCreator() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageAction.TL_messageActionChangeCreator::class,
          org.Tajgram.tgnet.TLRPC.MessageAction::TLdeserialize, null)

    }

    @Test
    public fun test_000500_MessageAction_TL_messageActionChannelCreate() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageAction.TL_messageActionChannelCreate::class,
          org.Tajgram.tgnet.TLRPC.MessageAction::TLdeserialize, null)

    }

    @Test
    public fun test_000501_MessageAction_TL_messageActionChannelMigrateFrom() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageAction.TL_messageActionChannelMigrateFrom::class,
          org.Tajgram.tgnet.TLRPC.MessageAction::TLdeserialize, null)

    }

    @Test
    public fun test_000502_MessageAction_TL_messageActionChatAddUser() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageAction.TL_messageActionChatAddUser::class,
          org.Tajgram.tgnet.TLRPC.MessageAction::TLdeserialize, null)

    }

    @Test
    public fun test_000503_MessageAction_TL_messageActionChatCreate() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageAction.TL_messageActionChatCreate::class,
          org.Tajgram.tgnet.TLRPC.MessageAction::TLdeserialize, null)

    }

    @Test
    public fun test_000504_MessageAction_TL_messageActionChatDeletePhoto() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageAction.TL_messageActionChatDeletePhoto::class,
          org.Tajgram.tgnet.TLRPC.MessageAction::TLdeserialize, null)

    }

    @Test
    public fun test_000505_MessageAction_TL_messageActionChatDeleteUser() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageAction.TL_messageActionChatDeleteUser::class,
          org.Tajgram.tgnet.TLRPC.MessageAction::TLdeserialize, null)

    }

    @Test
    public fun test_000506_MessageAction_TL_messageActionChatEditPhoto() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageAction.TL_messageActionChatEditPhoto::class,
          org.Tajgram.tgnet.TLRPC.MessageAction::TLdeserialize, null)

    }

    @Test
    public fun test_000507_MessageAction_TL_messageActionChatEditTitle() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageAction.TL_messageActionChatEditTitle::class,
          org.Tajgram.tgnet.TLRPC.MessageAction::TLdeserialize, null)

    }

    @Test
    public fun test_000508_MessageAction_TL_messageActionChatJoinedByLink() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageAction.TL_messageActionChatJoinedByLink::class,
          org.Tajgram.tgnet.TLRPC.MessageAction::TLdeserialize, null)

    }

    @Test
    public fun test_000509_MessageAction_TL_messageActionChatJoinedByRequest() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageAction.TL_messageActionChatJoinedByRequest::class,
          org.Tajgram.tgnet.TLRPC.MessageAction::TLdeserialize, null)

    }

    @Test
    public fun test_000510_MessageAction_TL_messageActionChatMigrateTo() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageAction.TL_messageActionChatMigrateTo::class,
          org.Tajgram.tgnet.TLRPC.MessageAction::TLdeserialize, null)

    }

    @Test
    public fun test_000511_MessageAction_TL_messageActionConferenceCall() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageAction.TL_messageActionConferenceCall::class,
          org.Tajgram.tgnet.TLRPC.MessageAction::TLdeserialize, null)

    }

    @Test
    public fun test_000512_MessageAction_TL_messageActionContactSignUp() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageAction.TL_messageActionContactSignUp::class,
          org.Tajgram.tgnet.TLRPC.MessageAction::TLdeserialize, null)

    }

    @Test
    public fun test_000513_MessageAction_TL_messageActionCustomAction() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageAction.TL_messageActionCustomAction::class,
          org.Tajgram.tgnet.TLRPC.MessageAction::TLdeserialize, null)

    }

    @Test
    public fun test_000514_MessageAction_TL_messageActionEmpty() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageAction.TL_messageActionEmpty::class,
          org.Tajgram.tgnet.TLRPC.MessageAction::TLdeserialize, null)

    }

    @Test
    public fun test_000515_MessageAction_TL_messageActionGameScore() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageAction.TL_messageActionGameScore::class,
          org.Tajgram.tgnet.TLRPC.MessageAction::TLdeserialize, null)

    }

    @Test
    public fun test_000516_MessageAction_TL_messageActionGeoProximityReached() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageAction.TL_messageActionGeoProximityReached::class,
          org.Tajgram.tgnet.TLRPC.MessageAction::TLdeserialize, null)

    }

    @Test
    public fun test_000517_MessageAction_TL_messageActionGiftCode() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageAction.TL_messageActionGiftCode::class,
          org.Tajgram.tgnet.TLRPC.MessageAction::TLdeserialize, null)

    }

    @Test
    public fun test_000518_MessageAction_TL_messageActionGiftPremium() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageAction.TL_messageActionGiftPremium::class,
          org.Tajgram.tgnet.TLRPC.MessageAction::TLdeserialize, null)

    }

    @Test
    public fun test_000519_MessageAction_TL_messageActionGiftStars() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageAction.TL_messageActionGiftStars::class,
          org.Tajgram.tgnet.TLRPC.MessageAction::TLdeserialize, null)

    }

    @Test
    public fun test_000520_MessageAction_TL_messageActionGiftTon() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageAction.TL_messageActionGiftTon::class,
          org.Tajgram.tgnet.TLRPC.MessageAction::TLdeserialize, null)

    }

    @Test
    public fun test_000521_MessageAction_TL_messageActionGiveawayLaunch() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageAction.TL_messageActionGiveawayLaunch::class,
          org.Tajgram.tgnet.TLRPC.MessageAction::TLdeserialize, null)

    }

    @Test
    public fun test_000522_MessageAction_TL_messageActionGiveawayResults() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageAction.TL_messageActionGiveawayResults::class,
          org.Tajgram.tgnet.TLRPC.MessageAction::TLdeserialize, null)

    }

    @Test
    public fun test_000523_MessageAction_TL_messageActionGroupCall() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageAction.TL_messageActionGroupCall::class,
          org.Tajgram.tgnet.TLRPC.MessageAction::TLdeserialize, null)

    }

    @Test
    public fun test_000524_MessageAction_TL_messageActionGroupCallScheduled() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageAction.TL_messageActionGroupCallScheduled::class,
          org.Tajgram.tgnet.TLRPC.MessageAction::TLdeserialize, null)

    }

    @Test
    public fun test_000525_MessageAction_TL_messageActionHistoryClear() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageAction.TL_messageActionHistoryClear::class,
          org.Tajgram.tgnet.TLRPC.MessageAction::TLdeserialize, null)

    }

    @Test
    public fun test_000526_MessageAction_TL_messageActionInviteToGroupCall() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageAction.TL_messageActionInviteToGroupCall::class,
          org.Tajgram.tgnet.TLRPC.MessageAction::TLdeserialize, null)

    }

    @Test
    public fun test_000527_MessageAction_TL_messageActionManagedBotCreated() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageAction.TL_messageActionManagedBotCreated::class,
          org.Tajgram.tgnet.TLRPC.MessageAction::TLdeserialize, null)

    }

    @Test
    public fun test_000528_MessageAction_TL_messageActionNewCreatorPending() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageAction.TL_messageActionNewCreatorPending::class,
          org.Tajgram.tgnet.TLRPC.MessageAction::TLdeserialize, null)

    }

    @Test
    public fun test_000529_MessageAction_TL_messageActionNoForwardsRequest() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageAction.TL_messageActionNoForwardsRequest::class,
          org.Tajgram.tgnet.TLRPC.MessageAction::TLdeserialize, null)

    }

    @Test
    public fun test_000530_MessageAction_TL_messageActionNoForwardsToggle() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageAction.TL_messageActionNoForwardsToggle::class,
          org.Tajgram.tgnet.TLRPC.MessageAction::TLdeserialize, null)

    }

    @Test
    public fun test_000531_MessageAction_TL_messageActionPaidMessagesPrice() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageAction.TL_messageActionPaidMessagesPrice::class,
          org.Tajgram.tgnet.TLRPC.MessageAction::TLdeserialize, null)

    }

    @Test
    public fun test_000532_MessageAction_TL_messageActionPaidMessagesRefunded() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageAction.TL_messageActionPaidMessagesRefunded::class,
          org.Tajgram.tgnet.TLRPC.MessageAction::TLdeserialize, null)

    }

    @Test
    public fun test_000533_MessageAction_TL_messageActionPaymentRefunded() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageAction.TL_messageActionPaymentRefunded::class,
          org.Tajgram.tgnet.TLRPC.MessageAction::TLdeserialize, null)

    }

    @Test
    public fun test_000534_MessageAction_TL_messageActionPaymentSent() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageAction.TL_messageActionPaymentSent::class,
          org.Tajgram.tgnet.TLRPC.MessageAction::TLdeserialize, null)

    }

    @Test
    public fun test_000535_MessageAction_TL_messageActionPaymentSentMe() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageAction.TL_messageActionPaymentSentMe::class,
          org.Tajgram.tgnet.TLRPC.MessageAction::TLdeserialize, null)

    }

    @Test
    public fun test_000536_MessageAction_TL_messageActionPhoneCall() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageAction.TL_messageActionPhoneCall::class,
          org.Tajgram.tgnet.TLRPC.MessageAction::TLdeserialize, null)

    }

    @Test
    public fun test_000537_MessageAction_TL_messageActionPinMessage() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageAction.TL_messageActionPinMessage::class,
          org.Tajgram.tgnet.TLRPC.MessageAction::TLdeserialize, null)

    }

    @Test
    public fun test_000538_MessageAction_TL_messageActionPollAppendAnswer() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageAction.TL_messageActionPollAppendAnswer::class,
          org.Tajgram.tgnet.TLRPC.MessageAction::TLdeserialize, null)

    }

    @Test
    public fun test_000539_MessageAction_TL_messageActionPollDeleteAnswer() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageAction.TL_messageActionPollDeleteAnswer::class,
          org.Tajgram.tgnet.TLRPC.MessageAction::TLdeserialize, null)

    }

    @Test
    public fun test_000540_MessageAction_TL_messageActionPrizeStars() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageAction.TL_messageActionPrizeStars::class,
          org.Tajgram.tgnet.TLRPC.MessageAction::TLdeserialize, null)

    }

    @Test
    public fun test_000541_MessageAction_TL_messageActionRequestedPeer() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageAction.TL_messageActionRequestedPeer::class,
          org.Tajgram.tgnet.TLRPC.MessageAction::TLdeserialize, null)

    }

    @Test
    public fun test_000542_MessageAction_TL_messageActionScreenshotTaken() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageAction.TL_messageActionScreenshotTaken::class,
          org.Tajgram.tgnet.TLRPC.MessageAction::TLdeserialize, null)

    }

    @Test
    public fun test_000543_MessageAction_TL_messageActionSecureValuesSent() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageAction.TL_messageActionSecureValuesSent::class,
          org.Tajgram.tgnet.TLRPC.MessageAction::TLdeserialize, null)

    }

    @Test
    public fun test_000544_MessageAction_TL_messageActionSetChatTheme() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageAction.TL_messageActionSetChatTheme::class,
          org.Tajgram.tgnet.TLRPC.MessageAction::TLdeserialize, null)

    }

    @Test
    public fun test_000545_MessageAction_TL_messageActionSetChatWallPaper() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageAction.TL_messageActionSetChatWallPaper::class,
          org.Tajgram.tgnet.TLRPC.MessageAction::TLdeserialize, null)

    }

    @Test
    public fun test_000546_MessageAction_TL_messageActionSetMessagesTTL() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageAction.TL_messageActionSetMessagesTTL::class,
          org.Tajgram.tgnet.TLRPC.MessageAction::TLdeserialize, null)

    }

    @Test
    public fun test_000547_MessageAction_TL_messageActionStarGift() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageAction.TL_messageActionStarGift::class,
          org.Tajgram.tgnet.TLRPC.MessageAction::TLdeserialize, null)

    }

    @Test
    public fun test_000548_MessageAction_TL_messageActionStarGiftPurchaseOffer() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageAction.TL_messageActionStarGiftPurchaseOffer::class,
          org.Tajgram.tgnet.TLRPC.MessageAction::TLdeserialize, null)

    }

    @Test
    public fun test_000549_MessageAction_TL_messageActionStarGiftPurchaseOfferDeclined() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageAction.TL_messageActionStarGiftPurchaseOfferDeclined::class,
          org.Tajgram.tgnet.TLRPC.MessageAction::TLdeserialize, null)

    }

    @Test
    public fun test_000550_MessageAction_TL_messageActionStarGiftUnique() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageAction.TL_messageActionStarGiftUnique::class,
          org.Tajgram.tgnet.TLRPC.MessageAction::TLdeserialize, null)

    }

    @Test
    public fun test_000551_MessageAction_TL_messageActionSuggestBirthday() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageAction.TL_messageActionSuggestBirthday::class,
          org.Tajgram.tgnet.TLRPC.MessageAction::TLdeserialize, null)

    }

    @Test
    public fun test_000552_MessageAction_TL_messageActionSuggestProfilePhoto() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageAction.TL_messageActionSuggestProfilePhoto::class,
          org.Tajgram.tgnet.TLRPC.MessageAction::TLdeserialize, null)

    }

    @Test
    public fun test_000553_MessageAction_TL_messageActionSuggestedPostApproval() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageAction.TL_messageActionSuggestedPostApproval::class,
          org.Tajgram.tgnet.TLRPC.MessageAction::TLdeserialize, null)

    }

    @Test
    public fun test_000554_MessageAction_TL_messageActionSuggestedPostRefund() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageAction.TL_messageActionSuggestedPostRefund::class,
          org.Tajgram.tgnet.TLRPC.MessageAction::TLdeserialize, null)

    }

    @Test
    public fun test_000555_MessageAction_TL_messageActionSuggestedPostSuccess() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageAction.TL_messageActionSuggestedPostSuccess::class,
          org.Tajgram.tgnet.TLRPC.MessageAction::TLdeserialize, null)

    }

    @Test
    public fun test_000556_MessageAction_TL_messageActionTodoAppendTasks() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageAction.TL_messageActionTodoAppendTasks::class,
          org.Tajgram.tgnet.TLRPC.MessageAction::TLdeserialize, null)

    }

    @Test
    public fun test_000557_MessageAction_TL_messageActionTodoCompletions() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageAction.TL_messageActionTodoCompletions::class,
          org.Tajgram.tgnet.TLRPC.MessageAction::TLdeserialize, null)

    }

    @Test
    public fun test_000558_MessageAction_TL_messageActionTopicCreate() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageAction.TL_messageActionTopicCreate::class,
          org.Tajgram.tgnet.TLRPC.MessageAction::TLdeserialize, null)

    }

    @Test
    public fun test_000559_MessageAction_TL_messageActionTopicEdit() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageAction.TL_messageActionTopicEdit::class,
          org.Tajgram.tgnet.TLRPC.MessageAction::TLdeserialize, null)

    }

    @Test
    public fun test_000560_MessageAction_TL_messageActionWebViewDataSent() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageAction.TL_messageActionWebViewDataSent::class,
          org.Tajgram.tgnet.TLRPC.MessageAction::TLdeserialize, null)

    }

    @Test
    public fun test_000561_MessageAction_TL_messageActionWebViewDataSentMe() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageAction.TL_messageActionWebViewDataSentMe::class,
          org.Tajgram.tgnet.TLRPC.MessageAction::TLdeserialize, null)

    }

    @Test
    public fun test_000562_MessageEntity_TL_inputMessageEntityMentionName() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageEntity.TL_inputMessageEntityMentionName::class,
          org.Tajgram.tgnet.TLRPC.MessageEntity::TLdeserialize, null)

    }

    @Test
    public fun test_000563_MessageEntity_TL_messageEntityBankCard() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageEntity.TL_messageEntityBankCard::class,
          org.Tajgram.tgnet.TLRPC.MessageEntity::TLdeserialize, null)

    }

    @Test
    public fun test_000564_MessageEntity_TL_messageEntityBlockquote() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageEntity.TL_messageEntityBlockquote::class,
          org.Tajgram.tgnet.TLRPC.MessageEntity::TLdeserialize, null)

    }

    @Test
    public fun test_000565_MessageEntity_TL_messageEntityBold() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageEntity.TL_messageEntityBold::class,
          org.Tajgram.tgnet.TLRPC.MessageEntity::TLdeserialize, null)

    }

    @Test
    public fun test_000566_MessageEntity_TL_messageEntityBotCommand() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageEntity.TL_messageEntityBotCommand::class,
          org.Tajgram.tgnet.TLRPC.MessageEntity::TLdeserialize, null)

    }

    @Test
    public fun test_000567_MessageEntity_TL_messageEntityCashtag() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageEntity.TL_messageEntityCashtag::class,
          org.Tajgram.tgnet.TLRPC.MessageEntity::TLdeserialize, null)

    }

    @Test
    public fun test_000568_MessageEntity_TL_messageEntityCode() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageEntity.TL_messageEntityCode::class,
          org.Tajgram.tgnet.TLRPC.MessageEntity::TLdeserialize, null)

    }

    @Test
    public fun test_000569_MessageEntity_TL_messageEntityCustomEmoji() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageEntity.TL_messageEntityCustomEmoji::class,
          org.Tajgram.tgnet.TLRPC.MessageEntity::TLdeserialize, null)

    }

    @Test
    public fun test_000570_MessageEntity_TL_messageEntityDiffDelete() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageEntity.TL_messageEntityDiffDelete::class,
          org.Tajgram.tgnet.TLRPC.MessageEntity::TLdeserialize, null)

    }

    @Test
    public fun test_000571_MessageEntity_TL_messageEntityDiffInsert() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageEntity.TL_messageEntityDiffInsert::class,
          org.Tajgram.tgnet.TLRPC.MessageEntity::TLdeserialize, null)

    }

    @Test
    public fun test_000572_MessageEntity_TL_messageEntityDiffReplace() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageEntity.TL_messageEntityDiffReplace::class,
          org.Tajgram.tgnet.TLRPC.MessageEntity::TLdeserialize, null)

    }

    @Test
    public fun test_000573_MessageEntity_TL_messageEntityEmail() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageEntity.TL_messageEntityEmail::class,
          org.Tajgram.tgnet.TLRPC.MessageEntity::TLdeserialize, null)

    }

    @Test
    public fun test_000574_MessageEntity_TL_messageEntityFormattedDate() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageEntity.TL_messageEntityFormattedDate::class,
          org.Tajgram.tgnet.TLRPC.MessageEntity::TLdeserialize, null)

    }

    @Test
    public fun test_000575_MessageEntity_TL_messageEntityHashtag() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageEntity.TL_messageEntityHashtag::class,
          org.Tajgram.tgnet.TLRPC.MessageEntity::TLdeserialize, null)

    }

    @Test
    public fun test_000576_MessageEntity_TL_messageEntityItalic() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageEntity.TL_messageEntityItalic::class,
          org.Tajgram.tgnet.TLRPC.MessageEntity::TLdeserialize, null)

    }

    @Test
    public fun test_000577_MessageEntity_TL_messageEntityMention() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageEntity.TL_messageEntityMention::class,
          org.Tajgram.tgnet.TLRPC.MessageEntity::TLdeserialize, null)

    }

    @Test
    public fun test_000578_MessageEntity_TL_messageEntityMentionName() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageEntity.TL_messageEntityMentionName::class,
          org.Tajgram.tgnet.TLRPC.MessageEntity::TLdeserialize, null)

    }

    @Test
    public fun test_000579_MessageEntity_TL_messageEntityPhone() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageEntity.TL_messageEntityPhone::class,
          org.Tajgram.tgnet.TLRPC.MessageEntity::TLdeserialize, null)

    }

    @Test
    public fun test_000580_MessageEntity_TL_messageEntityPre() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageEntity.TL_messageEntityPre::class,
          org.Tajgram.tgnet.TLRPC.MessageEntity::TLdeserialize, null)

    }

    @Test
    public fun test_000581_MessageEntity_TL_messageEntitySpoiler() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageEntity.TL_messageEntitySpoiler::class,
          org.Tajgram.tgnet.TLRPC.MessageEntity::TLdeserialize, null)

    }

    @Test
    public fun test_000582_MessageEntity_TL_messageEntityStrike() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageEntity.TL_messageEntityStrike::class,
          org.Tajgram.tgnet.TLRPC.MessageEntity::TLdeserialize, null)

    }

    @Test
    public fun test_000583_MessageEntity_TL_messageEntityTextUrl() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageEntity.TL_messageEntityTextUrl::class,
          org.Tajgram.tgnet.TLRPC.MessageEntity::TLdeserialize, null)

    }

    @Test
    public fun test_000584_MessageEntity_TL_messageEntityUnderline() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageEntity.TL_messageEntityUnderline::class,
          org.Tajgram.tgnet.TLRPC.MessageEntity::TLdeserialize, null)

    }

    @Test
    public fun test_000585_MessageEntity_TL_messageEntityUnknown() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageEntity.TL_messageEntityUnknown::class,
          org.Tajgram.tgnet.TLRPC.MessageEntity::TLdeserialize, null)

    }

    @Test
    public fun test_000586_MessageEntity_TL_messageEntityUrl() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageEntity.TL_messageEntityUrl::class,
          org.Tajgram.tgnet.TLRPC.MessageEntity::TLdeserialize, null)

    }

    @Test
    public fun test_000587_MessageExtendedMedia_TL_messageExtendedMedia() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageExtendedMedia.TL_messageExtendedMedia::class,
          org.Tajgram.tgnet.TLRPC.MessageExtendedMedia::TLdeserialize, null)

    }

    @Test
    public fun test_000588_MessageExtendedMedia_TL_messageExtendedMediaPreview() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageExtendedMedia.TL_messageExtendedMediaPreview::class,
          org.Tajgram.tgnet.TLRPC.MessageExtendedMedia::TLdeserialize, null)

    }

    @Test
    public fun test_000589_MessageFwdHeader_TL_messageFwdHeader() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageFwdHeader.TL_messageFwdHeader::class,
          org.Tajgram.tgnet.TLRPC.MessageFwdHeader::TLdeserialize, null)

    }

    @Test
    public fun test_000590_MessageMedia_TL_messageMediaContact() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageMedia.TL_messageMediaContact::class,
          org.Tajgram.tgnet.TLRPC.MessageMedia::TLdeserialize, null)

    }

    @Test
    public fun test_000591_MessageMedia_TL_messageMediaDice() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageMedia.TL_messageMediaDice::class,
          org.Tajgram.tgnet.TLRPC.MessageMedia::TLdeserialize, null)

    }

    @Test
    public fun test_000592_MessageMedia_TL_messageMediaDocument() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageMedia.TL_messageMediaDocument::class,
          org.Tajgram.tgnet.TLRPC.MessageMedia::TLdeserialize, null)

    }

    @Test
    public fun test_000593_MessageMedia_TL_messageMediaEmpty() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageMedia.TL_messageMediaEmpty::class,
          org.Tajgram.tgnet.TLRPC.MessageMedia::TLdeserialize, null)

    }

    @Test
    public fun test_000594_MessageMedia_TL_messageMediaGame() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageMedia.TL_messageMediaGame::class,
          org.Tajgram.tgnet.TLRPC.MessageMedia::TLdeserialize, null)

    }

    @Test
    public fun test_000595_MessageMedia_TL_messageMediaGeo() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageMedia.TL_messageMediaGeo::class,
          org.Tajgram.tgnet.TLRPC.MessageMedia::TLdeserialize, null)

    }

    @Test
    public fun test_000596_MessageMedia_TL_messageMediaGeoLive() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageMedia.TL_messageMediaGeoLive::class,
          org.Tajgram.tgnet.TLRPC.MessageMedia::TLdeserialize, null)

    }

    @Test
    public fun test_000597_MessageMedia_TL_messageMediaGiveaway() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageMedia.TL_messageMediaGiveaway::class,
          org.Tajgram.tgnet.TLRPC.MessageMedia::TLdeserialize, null)

    }

    @Test
    public fun test_000598_MessageMedia_TL_messageMediaGiveawayResults() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageMedia.TL_messageMediaGiveawayResults::class,
          org.Tajgram.tgnet.TLRPC.MessageMedia::TLdeserialize, null)

    }

    @Test
    public fun test_000599_MessageMedia_TL_messageMediaInvoice() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageMedia.TL_messageMediaInvoice::class,
          org.Tajgram.tgnet.TLRPC.MessageMedia::TLdeserialize, null)

    }

    @Test
    public fun test_000600_MessageMedia_TL_messageMediaPaidMedia() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageMedia.TL_messageMediaPaidMedia::class,
          org.Tajgram.tgnet.TLRPC.MessageMedia::TLdeserialize, null)

    }

    @Test
    public fun test_000601_MessageMedia_TL_messageMediaPhoto() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageMedia.TL_messageMediaPhoto::class,
          org.Tajgram.tgnet.TLRPC.MessageMedia::TLdeserialize, null)

    }

    @Test
    public fun test_000602_MessageMedia_TL_messageMediaPoll() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageMedia.TL_messageMediaPoll::class,
          org.Tajgram.tgnet.TLRPC.MessageMedia::TLdeserialize, null)

    }

    @Test
    public fun test_000603_MessageMedia_TL_messageMediaStory() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageMedia.TL_messageMediaStory::class,
          org.Tajgram.tgnet.TLRPC.MessageMedia::TLdeserialize, null)

    }

    @Test
    public fun test_000604_MessageMedia_TL_messageMediaToDo() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageMedia.TL_messageMediaToDo::class,
          org.Tajgram.tgnet.TLRPC.MessageMedia::TLdeserialize, null)

    }

    @Test
    public fun test_000605_MessageMedia_TL_messageMediaUnsupported() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageMedia.TL_messageMediaUnsupported::class,
          org.Tajgram.tgnet.TLRPC.MessageMedia::TLdeserialize, null)

    }

    @Test
    public fun test_000606_MessageMedia_TL_messageMediaVenue() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageMedia.TL_messageMediaVenue::class,
          org.Tajgram.tgnet.TLRPC.MessageMedia::TLdeserialize, null)

    }

    @Test
    public fun test_000607_MessageMedia_TL_messageMediaVideoStream() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageMedia.TL_messageMediaVideoStream::class,
          org.Tajgram.tgnet.TLRPC.MessageMedia::TLdeserialize, null)

    }

    @Test
    public fun test_000608_MessageMedia_TL_messageMediaWebPage() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageMedia.TL_messageMediaWebPage::class,
          org.Tajgram.tgnet.TLRPC.MessageMedia::TLdeserialize, null)

    }

    @Test
    public fun test_000609_MessagePeerReaction_TL_messagePeerReaction() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessagePeerReaction.TL_messagePeerReaction::class,
          org.Tajgram.tgnet.TLRPC.MessagePeerReaction::TLdeserialize, null)

    }

    @Test
    public fun test_000610_MessagePeerVote_TL_messagePeerVote() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessagePeerVote.TL_messagePeerVote::class,
          org.Tajgram.tgnet.TLRPC.MessagePeerVote::TLdeserialize, null)

    }

    @Test
    public fun test_000611_MessagePeerVote_TL_messagePeerVoteInputOption() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessagePeerVote.TL_messagePeerVoteInputOption::class,
          org.Tajgram.tgnet.TLRPC.MessagePeerVote::TLdeserialize, null)

    }

    @Test
    public fun test_000612_MessagePeerVote_TL_messagePeerVoteMultiple() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessagePeerVote.TL_messagePeerVoteMultiple::class,
          org.Tajgram.tgnet.TLRPC.MessagePeerVote::TLdeserialize, null)

    }

    @Test
    public fun test_000613_MessageRange_TL_messageRange() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageRange.TL_messageRange::class,
          org.Tajgram.tgnet.TLRPC.TL_messageRange::TLdeserialize, null)

    }

    @Test
    public fun test_000614_MessageReactions_TL_messageReactions() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageReactions.TL_messageReactions::class,
          org.Tajgram.tgnet.TLRPC.MessageReactions::TLdeserialize, null)

    }

    @Test
    public fun test_000615_MessageReactor_TL_messageReactor() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageReactor.TL_messageReactor::class,
          org.Tajgram.tgnet.TLRPC.MessageReactor::TLdeserialize, null)

    }

    @Test
    public fun test_000616_MessageReplies_TL_messageReplies() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageReplies.TL_messageReplies::class,
          org.Tajgram.tgnet.TLRPC.MessageReplies::TLdeserialize, null)

    }

    @Test
    public fun test_000617_MessageReplyHeader_TL_messageReplyHeader() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageReplyHeader.TL_messageReplyHeader::class,
          org.Tajgram.tgnet.TLRPC.MessageReplyHeader::TLdeserialize, null)

    }

    @Test
    public fun test_000618_MessageReplyHeader_TL_messageReplyStoryHeader() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageReplyHeader.TL_messageReplyStoryHeader::class,
          org.Tajgram.tgnet.TLRPC.MessageReplyHeader::TLdeserialize, null)

    }

    @Test
    public fun test_000619_MessageReportOption_TL_messageReportOption() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageReportOption.TL_messageReportOption::class,
          org.Tajgram.tgnet.TLRPC.TL_messageReportOption::TLdeserialize, null)

    }

    @Test
    public fun test_000620_MessageViews_TL_messageViews() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageViews.TL_messageViews::class,
          org.Tajgram.tgnet.TLRPC.TL_messageViews::TLdeserialize, null)

    }

    @Test
    public fun test_000621_MessagesFilter_TL_inputMessagesFilterChatPhotos() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessagesFilter.TL_inputMessagesFilterChatPhotos::class,
          org.Tajgram.tgnet.TLRPC.MessagesFilter::TLdeserialize, null)

    }

    @Test
    public fun test_000622_MessagesFilter_TL_inputMessagesFilterContacts() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessagesFilter.TL_inputMessagesFilterContacts::class,
          org.Tajgram.tgnet.TLRPC.MessagesFilter::TLdeserialize, null)

    }

    @Test
    public fun test_000623_MessagesFilter_TL_inputMessagesFilterDocument() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessagesFilter.TL_inputMessagesFilterDocument::class,
          org.Tajgram.tgnet.TLRPC.MessagesFilter::TLdeserialize, null)

    }

    @Test
    public fun test_000624_MessagesFilter_TL_inputMessagesFilterEmpty() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessagesFilter.TL_inputMessagesFilterEmpty::class,
          org.Tajgram.tgnet.TLRPC.MessagesFilter::TLdeserialize, null)

    }

    @Test
    public fun test_000625_MessagesFilter_TL_inputMessagesFilterGeo() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessagesFilter.TL_inputMessagesFilterGeo::class,
          org.Tajgram.tgnet.TLRPC.MessagesFilter::TLdeserialize, null)

    }

    @Test
    public fun test_000626_MessagesFilter_TL_inputMessagesFilterGif() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessagesFilter.TL_inputMessagesFilterGif::class,
          org.Tajgram.tgnet.TLRPC.MessagesFilter::TLdeserialize, null)

    }

    @Test
    public fun test_000627_MessagesFilter_TL_inputMessagesFilterMusic() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessagesFilter.TL_inputMessagesFilterMusic::class,
          org.Tajgram.tgnet.TLRPC.MessagesFilter::TLdeserialize, null)

    }

    @Test
    public fun test_000628_MessagesFilter_TL_inputMessagesFilterMyMentions() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessagesFilter.TL_inputMessagesFilterMyMentions::class,
          org.Tajgram.tgnet.TLRPC.MessagesFilter::TLdeserialize, null)

    }

    @Test
    public fun test_000629_MessagesFilter_TL_inputMessagesFilterPhoneCalls() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessagesFilter.TL_inputMessagesFilterPhoneCalls::class,
          org.Tajgram.tgnet.TLRPC.MessagesFilter::TLdeserialize, null)

    }

    @Test
    public fun test_000630_MessagesFilter_TL_inputMessagesFilterPhotoVideo() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessagesFilter.TL_inputMessagesFilterPhotoVideo::class,
          org.Tajgram.tgnet.TLRPC.MessagesFilter::TLdeserialize, null)

    }

    @Test
    public fun test_000631_MessagesFilter_TL_inputMessagesFilterPhotos() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessagesFilter.TL_inputMessagesFilterPhotos::class,
          org.Tajgram.tgnet.TLRPC.MessagesFilter::TLdeserialize, null)

    }

    @Test
    public fun test_000632_MessagesFilter_TL_inputMessagesFilterPinned() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessagesFilter.TL_inputMessagesFilterPinned::class,
          org.Tajgram.tgnet.TLRPC.MessagesFilter::TLdeserialize, null)

    }

    @Test
    public fun test_000633_MessagesFilter_TL_inputMessagesFilterPoll() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessagesFilter.TL_inputMessagesFilterPoll::class,
          org.Tajgram.tgnet.TLRPC.MessagesFilter::TLdeserialize, null)

    }

    @Test
    public fun test_000634_MessagesFilter_TL_inputMessagesFilterRoundVideo() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessagesFilter.TL_inputMessagesFilterRoundVideo::class,
          org.Tajgram.tgnet.TLRPC.MessagesFilter::TLdeserialize, null)

    }

    @Test
    public fun test_000635_MessagesFilter_TL_inputMessagesFilterRoundVoice() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessagesFilter.TL_inputMessagesFilterRoundVoice::class,
          org.Tajgram.tgnet.TLRPC.MessagesFilter::TLdeserialize, null)

    }

    @Test
    public fun test_000636_MessagesFilter_TL_inputMessagesFilterUrl() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessagesFilter.TL_inputMessagesFilterUrl::class,
          org.Tajgram.tgnet.TLRPC.MessagesFilter::TLdeserialize, null)

    }

    @Test
    public fun test_000637_MessagesFilter_TL_inputMessagesFilterVideo() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessagesFilter.TL_inputMessagesFilterVideo::class,
          org.Tajgram.tgnet.TLRPC.MessagesFilter::TLdeserialize, null)

    }

    @Test
    public fun test_000638_MessagesFilter_TL_inputMessagesFilterVoice() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessagesFilter.TL_inputMessagesFilterVoice::class,
          org.Tajgram.tgnet.TLRPC.MessagesFilter::TLdeserialize, null)

    }

    @Test
    public fun test_000639_MissingInvitee_TL_missingInvitee() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MissingInvitee.TL_missingInvitee::class,
          org.Tajgram.tgnet.TLRPC.TL_missingInvitee::TLdeserialize, null)

    }

    @Test
    public fun test_000640_MyBoost_TL_myBoost() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MyBoost.TL_myBoost::class,
          org.Tajgram.tgnet.tl.TL_stories.TL_myBoost::TLdeserialize, null)

    }

    @Test
    public fun test_000641_NearestDc_TL_nearestDc() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_NearestDc.TL_nearestDc::class,
          org.Tajgram.tgnet.TLRPC.TL_nearestDc::TLdeserialize, null)

    }

    @Test
    public fun test_000642_NotificationSound_TL_notificationSoundDefault() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_NotificationSound.TL_notificationSoundDefault::class,
          org.Tajgram.tgnet.TLRPC.NotificationSound::TLdeserialize, null)

    }

    @Test
    public fun test_000643_NotificationSound_TL_notificationSoundLocal() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_NotificationSound.TL_notificationSoundLocal::class,
          org.Tajgram.tgnet.TLRPC.NotificationSound::TLdeserialize, null)

    }

    @Test
    public fun test_000644_NotificationSound_TL_notificationSoundNone() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_NotificationSound.TL_notificationSoundNone::class,
          org.Tajgram.tgnet.TLRPC.NotificationSound::TLdeserialize, null)

    }

    @Test
    public fun test_000645_NotificationSound_TL_notificationSoundRingtone() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_NotificationSound.TL_notificationSoundRingtone::class,
          org.Tajgram.tgnet.TLRPC.NotificationSound::TLdeserialize, null)

    }

    @Test
    public fun test_000646_NotifyPeer_TL_notifyBroadcasts() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_NotifyPeer.TL_notifyBroadcasts::class,
          org.Tajgram.tgnet.TLRPC.NotifyPeer::TLdeserialize, null)

    }

    @Test
    public fun test_000647_NotifyPeer_TL_notifyChats() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_NotifyPeer.TL_notifyChats::class,
          org.Tajgram.tgnet.TLRPC.NotifyPeer::TLdeserialize, null)

    }

    @Test
    public fun test_000648_NotifyPeer_TL_notifyForumTopic() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_NotifyPeer.TL_notifyForumTopic::class,
          org.Tajgram.tgnet.TLRPC.NotifyPeer::TLdeserialize, null)

    }

    @Test
    public fun test_000649_NotifyPeer_TL_notifyPeer() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_NotifyPeer.TL_notifyPeer::class,
          org.Tajgram.tgnet.TLRPC.NotifyPeer::TLdeserialize, null)

    }

    @Test
    public fun test_000650_NotifyPeer_TL_notifyUsers() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_NotifyPeer.TL_notifyUsers::class,
          org.Tajgram.tgnet.TLRPC.NotifyPeer::TLdeserialize, null)

    }

    @Test
    public fun test_000651_Null_TL_null() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Null.TL_null::class,
          org.Tajgram.tgnet.TLRPC.TL_null::TLdeserialize, null)

    }

    @Test
    public fun test_000652_OutboxReadDate_TL_outboxReadDate() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_OutboxReadDate.TL_outboxReadDate::class,
          org.Tajgram.tgnet.TLRPC.TL_outboxReadDate::TLdeserialize, null)

    }

    @Test
    public fun test_000653_Page_TL_page() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Page.TL_page::class,
          org.Tajgram.tgnet.tl.TL_iv.Page::TLdeserialize, null)

    }

    @Test
    public fun test_000654_PageBlock_TL_inputPageBlockMap() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_PageBlock.TL_inputPageBlockMap::class,
          org.Tajgram.tgnet.tl.TL_iv.PageBlock::TLdeserialize, null)

    }

    @Test
    public fun test_000655_PageBlock_TL_pageBlockAnchor() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_PageBlock.TL_pageBlockAnchor::class,
          org.Tajgram.tgnet.tl.TL_iv.PageBlock::TLdeserialize, null)

    }

    @Test
    public fun test_000656_PageBlock_TL_pageBlockAudio() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_PageBlock.TL_pageBlockAudio::class,
          org.Tajgram.tgnet.tl.TL_iv.PageBlock::TLdeserialize, null)

    }

    @Test
    public fun test_000657_PageBlock_TL_pageBlockAuthorDate() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_PageBlock.TL_pageBlockAuthorDate::class,
          org.Tajgram.tgnet.tl.TL_iv.PageBlock::TLdeserialize, null)

    }

    @Test
    public fun test_000658_PageBlock_TL_pageBlockBlockquote() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_PageBlock.TL_pageBlockBlockquote::class,
          org.Tajgram.tgnet.tl.TL_iv.PageBlock::TLdeserialize, null)

    }

    @Test
    public fun test_000659_PageBlock_TL_pageBlockBlockquoteBlocks() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_PageBlock.TL_pageBlockBlockquoteBlocks::class,
          org.Tajgram.tgnet.tl.TL_iv.PageBlock::TLdeserialize, null)

    }

    @Test
    public fun test_000660_PageBlock_TL_pageBlockChannel() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_PageBlock.TL_pageBlockChannel::class,
          org.Tajgram.tgnet.tl.TL_iv.PageBlock::TLdeserialize, null)

    }

    @Test
    public fun test_000661_PageBlock_TL_pageBlockCollage() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_PageBlock.TL_pageBlockCollage::class,
          org.Tajgram.tgnet.tl.TL_iv.PageBlock::TLdeserialize, null)

    }

    @Test
    public fun test_000662_PageBlock_TL_pageBlockCover() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_PageBlock.TL_pageBlockCover::class,
          org.Tajgram.tgnet.tl.TL_iv.PageBlock::TLdeserialize, null)

    }

    @Test
    public fun test_000663_PageBlock_TL_pageBlockDetails() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_PageBlock.TL_pageBlockDetails::class,
          org.Tajgram.tgnet.tl.TL_iv.PageBlock::TLdeserialize, null)

    }

    @Test
    public fun test_000664_PageBlock_TL_pageBlockDivider() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_PageBlock.TL_pageBlockDivider::class,
          org.Tajgram.tgnet.tl.TL_iv.PageBlock::TLdeserialize, null)

    }

    @Test
    public fun test_000665_PageBlock_TL_pageBlockEmbed() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_PageBlock.TL_pageBlockEmbed::class,
          org.Tajgram.tgnet.tl.TL_iv.PageBlock::TLdeserialize, null)

    }

    @Test
    public fun test_000666_PageBlock_TL_pageBlockEmbedPost() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_PageBlock.TL_pageBlockEmbedPost::class,
          org.Tajgram.tgnet.tl.TL_iv.PageBlock::TLdeserialize, null)

    }

    @Test
    public fun test_000667_PageBlock_TL_pageBlockFooter() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_PageBlock.TL_pageBlockFooter::class,
          org.Tajgram.tgnet.tl.TL_iv.PageBlock::TLdeserialize, null)

    }

    @Test
    public fun test_000668_PageBlock_TL_pageBlockHeader() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_PageBlock.TL_pageBlockHeader::class,
          org.Tajgram.tgnet.tl.TL_iv.PageBlock::TLdeserialize, null)

    }

    @Test
    public fun test_000669_PageBlock_TL_pageBlockHeading1() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_PageBlock.TL_pageBlockHeading1::class,
          org.Tajgram.tgnet.tl.TL_iv.PageBlock::TLdeserialize, null)

    }

    @Test
    public fun test_000670_PageBlock_TL_pageBlockHeading2() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_PageBlock.TL_pageBlockHeading2::class,
          org.Tajgram.tgnet.tl.TL_iv.PageBlock::TLdeserialize, null)

    }

    @Test
    public fun test_000671_PageBlock_TL_pageBlockHeading3() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_PageBlock.TL_pageBlockHeading3::class,
          org.Tajgram.tgnet.tl.TL_iv.PageBlock::TLdeserialize, null)

    }

    @Test
    public fun test_000672_PageBlock_TL_pageBlockHeading4() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_PageBlock.TL_pageBlockHeading4::class,
          org.Tajgram.tgnet.tl.TL_iv.PageBlock::TLdeserialize, null)

    }

    @Test
    public fun test_000673_PageBlock_TL_pageBlockHeading5() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_PageBlock.TL_pageBlockHeading5::class,
          org.Tajgram.tgnet.tl.TL_iv.PageBlock::TLdeserialize, null)

    }

    @Test
    public fun test_000674_PageBlock_TL_pageBlockHeading6() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_PageBlock.TL_pageBlockHeading6::class,
          org.Tajgram.tgnet.tl.TL_iv.PageBlock::TLdeserialize, null)

    }

    @Test
    public fun test_000675_PageBlock_TL_pageBlockKicker() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_PageBlock.TL_pageBlockKicker::class,
          org.Tajgram.tgnet.tl.TL_iv.PageBlock::TLdeserialize, null)

    }

    @Test
    public fun test_000676_PageBlock_TL_pageBlockList() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_PageBlock.TL_pageBlockList::class,
          org.Tajgram.tgnet.tl.TL_iv.PageBlock::TLdeserialize, null)

    }

    @Test
    public fun test_000677_PageBlock_TL_pageBlockMap() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_PageBlock.TL_pageBlockMap::class,
          org.Tajgram.tgnet.tl.TL_iv.PageBlock::TLdeserialize, null)

    }

    @Test
    public fun test_000678_PageBlock_TL_pageBlockMath() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_PageBlock.TL_pageBlockMath::class,
          org.Tajgram.tgnet.tl.TL_iv.PageBlock::TLdeserialize, null)

    }

    @Test
    public fun test_000679_PageBlock_TL_pageBlockOrderedList() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_PageBlock.TL_pageBlockOrderedList::class,
          org.Tajgram.tgnet.tl.TL_iv.PageBlock::TLdeserialize, null)

    }

    @Test
    public fun test_000680_PageBlock_TL_pageBlockParagraph() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_PageBlock.TL_pageBlockParagraph::class,
          org.Tajgram.tgnet.tl.TL_iv.PageBlock::TLdeserialize, null)

    }

    @Test
    public fun test_000681_PageBlock_TL_pageBlockPhoto() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_PageBlock.TL_pageBlockPhoto::class,
          org.Tajgram.tgnet.tl.TL_iv.PageBlock::TLdeserialize, null)

    }

    @Test
    public fun test_000682_PageBlock_TL_pageBlockPreformatted() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_PageBlock.TL_pageBlockPreformatted::class,
          org.Tajgram.tgnet.tl.TL_iv.PageBlock::TLdeserialize, null)

    }

    @Test
    public fun test_000683_PageBlock_TL_pageBlockPullquote() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_PageBlock.TL_pageBlockPullquote::class,
          org.Tajgram.tgnet.tl.TL_iv.PageBlock::TLdeserialize, null)

    }

    @Test
    public fun test_000684_PageBlock_TL_pageBlockRelatedArticles() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_PageBlock.TL_pageBlockRelatedArticles::class,
          org.Tajgram.tgnet.tl.TL_iv.PageBlock::TLdeserialize, null)

    }

    @Test
    public fun test_000685_PageBlock_TL_pageBlockSlideshow() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_PageBlock.TL_pageBlockSlideshow::class,
          org.Tajgram.tgnet.tl.TL_iv.PageBlock::TLdeserialize, null)

    }

    @Test
    public fun test_000686_PageBlock_TL_pageBlockSubheader() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_PageBlock.TL_pageBlockSubheader::class,
          org.Tajgram.tgnet.tl.TL_iv.PageBlock::TLdeserialize, null)

    }

    @Test
    public fun test_000687_PageBlock_TL_pageBlockSubtitle() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_PageBlock.TL_pageBlockSubtitle::class,
          org.Tajgram.tgnet.tl.TL_iv.PageBlock::TLdeserialize, null)

    }

    @Test
    public fun test_000688_PageBlock_TL_pageBlockTable() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_PageBlock.TL_pageBlockTable::class,
          org.Tajgram.tgnet.tl.TL_iv.PageBlock::TLdeserialize, null)

    }

    @Test
    public fun test_000689_PageBlock_TL_pageBlockThinking() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_PageBlock.TL_pageBlockThinking::class,
          org.Tajgram.tgnet.tl.TL_iv.PageBlock::TLdeserialize, null)

    }

    @Test
    public fun test_000690_PageBlock_TL_pageBlockTitle() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_PageBlock.TL_pageBlockTitle::class,
          org.Tajgram.tgnet.tl.TL_iv.PageBlock::TLdeserialize, null)

    }

    @Test
    public fun test_000691_PageBlock_TL_pageBlockUnsupported() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_PageBlock.TL_pageBlockUnsupported::class,
          org.Tajgram.tgnet.tl.TL_iv.PageBlock::TLdeserialize, null)

    }

    @Test
    public fun test_000692_PageBlock_TL_pageBlockVideo() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_PageBlock.TL_pageBlockVideo::class,
          org.Tajgram.tgnet.tl.TL_iv.PageBlock::TLdeserialize, null)

    }

    @Test
    public fun test_000693_PageCaption_TL_pageCaption() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_PageCaption.TL_pageCaption::class,
          org.Tajgram.tgnet.tl.TL_iv.PageCaption::TLdeserialize, null)

    }

    @Test
    public fun test_000694_PageListItem_TL_pageListItemBlocks() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_PageListItem.TL_pageListItemBlocks::class,
          org.Tajgram.tgnet.tl.TL_iv.PageListItem::TLdeserialize, null)

    }

    @Test
    public fun test_000695_PageListItem_TL_pageListItemText() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_PageListItem.TL_pageListItemText::class,
          org.Tajgram.tgnet.tl.TL_iv.PageListItem::TLdeserialize, null)

    }

    @Test
    public fun test_000696_PageListOrderedItem_TL_pageListOrderedItemBlocks() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_PageListOrderedItem.TL_pageListOrderedItemBlocks::class,
          org.Tajgram.tgnet.tl.TL_iv.PageListOrderedItem::TLdeserialize, null)

    }

    @Test
    public fun test_000697_PageListOrderedItem_TL_pageListOrderedItemText() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_PageListOrderedItem.TL_pageListOrderedItemText::class,
          org.Tajgram.tgnet.tl.TL_iv.PageListOrderedItem::TLdeserialize, null)

    }

    @Test
    public fun test_000698_PageRelatedArticle_TL_pageRelatedArticle() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_PageRelatedArticle.TL_pageRelatedArticle::class,
          org.Tajgram.tgnet.tl.TL_iv.pageRelatedArticle::TLdeserialize, null)

    }

    @Test
    public fun test_000699_PageTableCell_TL_pageTableCell() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_PageTableCell.TL_pageTableCell::class,
          org.Tajgram.tgnet.tl.TL_iv.pageTableCell::TLdeserialize, null)

    }

    @Test
    public fun test_000700_PageTableRow_TL_pageTableRow() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_PageTableRow.TL_pageTableRow::class,
          org.Tajgram.tgnet.tl.TL_iv.pageTableRow::TLdeserialize, null)

    }

    @Test
    public fun test_000701_PaidReactionPrivacy_TL_paidReactionPrivacyAnonymous() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_PaidReactionPrivacy.TL_paidReactionPrivacyAnonymous::class,
          org.Tajgram.tgnet.tl.TL_stars.PaidReactionPrivacy::TLdeserialize, null)

    }

    @Test
    public fun test_000702_PaidReactionPrivacy_TL_paidReactionPrivacyDefault() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_PaidReactionPrivacy.TL_paidReactionPrivacyDefault::class,
          org.Tajgram.tgnet.tl.TL_stars.PaidReactionPrivacy::TLdeserialize, null)

    }

    @Test
    public fun test_000703_PaidReactionPrivacy_TL_paidReactionPrivacyPeer() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_PaidReactionPrivacy.TL_paidReactionPrivacyPeer::class,
          org.Tajgram.tgnet.tl.TL_stars.PaidReactionPrivacy::TLdeserialize, null)

    }

    @Test
    public fun test_000704_Passkey_TL_passkey() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Passkey.TL_passkey::class,
          org.Tajgram.tgnet.tl.TL_account.Passkey::TLdeserialize, null)

    }

    @Test
    public
        fun test_000705_PasswordKdfAlgo_TL_passwordKdfAlgoSHA256SHA256PBKDF2HMACSHA512iter100000SHA256ModPow() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_PasswordKdfAlgo.TL_passwordKdfAlgoSHA256SHA256PBKDF2HMACSHA512iter100000SHA256ModPow::class,
          org.Tajgram.tgnet.TLRPC.PasswordKdfAlgo::TLdeserialize, null)

    }

    @Test
    public fun test_000706_PasswordKdfAlgo_TL_passwordKdfAlgoUnknown() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_PasswordKdfAlgo.TL_passwordKdfAlgoUnknown::class,
          org.Tajgram.tgnet.TLRPC.PasswordKdfAlgo::TLdeserialize, null)

    }

    @Test
    public fun test_000707_PaymentCharge_TL_paymentCharge() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_PaymentCharge.TL_paymentCharge::class,
          org.Tajgram.tgnet.TLRPC.TL_paymentCharge::TLdeserialize, null)

    }

    @Test
    public fun test_000708_PaymentFormMethod_TL_paymentFormMethod() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_PaymentFormMethod.TL_paymentFormMethod::class,
          org.Tajgram.tgnet.TLRPC.TL_paymentFormMethod::TLdeserialize, null)

    }

    @Test
    public fun test_000709_PaymentRequestedInfo_TL_paymentRequestedInfo() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_PaymentRequestedInfo.TL_paymentRequestedInfo::class,
          org.Tajgram.tgnet.TLRPC.TL_paymentRequestedInfo::TLdeserialize, null)

    }

    @Test
    public fun test_000710_PaymentSavedCredentials_TL_paymentSavedCredentialsCard() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_PaymentSavedCredentials.TL_paymentSavedCredentialsCard::class,
          org.Tajgram.tgnet.TLRPC.TL_paymentSavedCredentialsCard::TLdeserialize, null)

    }

    @Test
    public fun test_000711_Peer_TL_peerChannel() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Peer.TL_peerChannel::class,
          org.Tajgram.tgnet.TLRPC.Peer::TLdeserialize, null)

    }

    @Test
    public fun test_000712_Peer_TL_peerChat() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Peer.TL_peerChat::class,
          org.Tajgram.tgnet.TLRPC.Peer::TLdeserialize, null)

    }

    @Test
    public fun test_000713_Peer_TL_peerUser() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Peer.TL_peerUser::class,
          org.Tajgram.tgnet.TLRPC.Peer::TLdeserialize, null)

    }

    @Test
    public fun test_000714_PeerBlocked_TL_peerBlocked() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_PeerBlocked.TL_peerBlocked::class,
          org.Tajgram.tgnet.TLRPC.TL_peerBlocked::TLdeserialize, null)

    }

    @Test
    public fun test_000715_PeerColor_TL_inputPeerColorCollectible() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_PeerColor.TL_inputPeerColorCollectible::class,
          org.Tajgram.tgnet.TLRPC.PeerColor::TLdeserialize, null)

    }

    @Test
    public fun test_000716_PeerColor_TL_peerColor() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_PeerColor.TL_peerColor::class,
          org.Tajgram.tgnet.TLRPC.PeerColor::TLdeserialize, null)

    }

    @Test
    public fun test_000717_PeerColor_TL_peerColorCollectible() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_PeerColor.TL_peerColorCollectible::class,
          org.Tajgram.tgnet.TLRPC.PeerColor::TLdeserialize, null)

    }

    @Test
    public fun test_000718_PeerLocated_TL_peerLocated() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_PeerLocated.TL_peerLocated::class,
          org.Tajgram.tgnet.TLRPC.PeerLocated::TLdeserialize, null)

    }

    @Test
    public fun test_000719_PeerLocated_TL_peerSelfLocated() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_PeerLocated.TL_peerSelfLocated::class,
          org.Tajgram.tgnet.TLRPC.PeerLocated::TLdeserialize, null)

    }

    @Test
    public fun test_000720_PeerNotifySettings_TL_peerNotifySettings() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_PeerNotifySettings.TL_peerNotifySettings::class,
          org.Tajgram.tgnet.TLRPC.PeerNotifySettings::TLdeserialize, null)

    }

    @Test
    public fun test_000721_PeerSettings_TL_peerSettings() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_PeerSettings.TL_peerSettings::class,
          org.Tajgram.tgnet.TLRPC.PeerSettings::TLdeserialize, null)

    }

    @Test
    public fun test_000722_PeerStories_TL_peerStories() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_PeerStories.TL_peerStories::class,
          org.Tajgram.tgnet.tl.TL_stories.PeerStories::TLdeserialize, null)

    }

    @Test
    public fun test_000723_PendingSuggestion_TL_pendingSuggestion() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_PendingSuggestion.TL_pendingSuggestion::class,
          org.Tajgram.tgnet.TLRPC.TL_pendingSuggestion::TLdeserialize, null)

    }

    @Test
    public fun test_000724_PhoneCall_TL_phoneCall() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_PhoneCall.TL_phoneCall::class,
          org.Tajgram.tgnet.tl.TL_phone.PhoneCall::TLdeserialize, null)

    }

    @Test
    public fun test_000725_PhoneCall_TL_phoneCallAccepted() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_PhoneCall.TL_phoneCallAccepted::class,
          org.Tajgram.tgnet.tl.TL_phone.PhoneCall::TLdeserialize, null)

    }

    @Test
    public fun test_000726_PhoneCall_TL_phoneCallDiscarded() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_PhoneCall.TL_phoneCallDiscarded::class,
          org.Tajgram.tgnet.tl.TL_phone.PhoneCall::TLdeserialize, null)

    }

    @Test
    public fun test_000727_PhoneCall_TL_phoneCallEmpty() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_PhoneCall.TL_phoneCallEmpty::class,
          org.Tajgram.tgnet.tl.TL_phone.PhoneCall::TLdeserialize, null)

    }

    @Test
    public fun test_000728_PhoneCall_TL_phoneCallRequested() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_PhoneCall.TL_phoneCallRequested::class,
          org.Tajgram.tgnet.tl.TL_phone.PhoneCall::TLdeserialize, null)

    }

    @Test
    public fun test_000729_PhoneCall_TL_phoneCallWaiting() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_PhoneCall.TL_phoneCallWaiting::class,
          org.Tajgram.tgnet.tl.TL_phone.PhoneCall::TLdeserialize, null)

    }

    @Test
    public fun test_000730_PhoneCallDiscardReason_TL_phoneCallDiscardReasonBusy() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_PhoneCallDiscardReason.TL_phoneCallDiscardReasonBusy::class,
          org.Tajgram.tgnet.TLRPC.PhoneCallDiscardReason::TLdeserialize, null)

    }

    @Test
    public fun test_000731_PhoneCallDiscardReason_TL_phoneCallDiscardReasonDisconnect() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_PhoneCallDiscardReason.TL_phoneCallDiscardReasonDisconnect::class,
          org.Tajgram.tgnet.TLRPC.PhoneCallDiscardReason::TLdeserialize, null)

    }

    @Test
    public fun test_000732_PhoneCallDiscardReason_TL_phoneCallDiscardReasonHangup() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_PhoneCallDiscardReason.TL_phoneCallDiscardReasonHangup::class,
          org.Tajgram.tgnet.TLRPC.PhoneCallDiscardReason::TLdeserialize, null)

    }

    @Test
    public fun test_000733_PhoneCallDiscardReason_TL_phoneCallDiscardReasonMigrateConferenceCall() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_PhoneCallDiscardReason.TL_phoneCallDiscardReasonMigrateConferenceCall::class,
          org.Tajgram.tgnet.TLRPC.PhoneCallDiscardReason::TLdeserialize, null)

    }

    @Test
    public fun test_000734_PhoneCallDiscardReason_TL_phoneCallDiscardReasonMissed() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_PhoneCallDiscardReason.TL_phoneCallDiscardReasonMissed::class,
          org.Tajgram.tgnet.TLRPC.PhoneCallDiscardReason::TLdeserialize, null)

    }

    @Test
    public fun test_000735_PhoneCallProtocol_TL_phoneCallProtocol() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_PhoneCallProtocol.TL_phoneCallProtocol::class,
          org.Tajgram.tgnet.tl.TL_phone.PhoneCallProtocol::TLdeserialize, null)

    }

    @Test
    public fun test_000736_PhoneConnection_TL_phoneConnection() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_PhoneConnection.TL_phoneConnection::class,
          org.Tajgram.tgnet.TLRPC.PhoneConnection::TLdeserialize, null)

    }

    @Test
    public fun test_000737_PhoneConnection_TL_phoneConnectionWebrtc() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_PhoneConnection.TL_phoneConnectionWebrtc::class,
          org.Tajgram.tgnet.TLRPC.PhoneConnection::TLdeserialize, null)

    }

    @Test
    public fun test_000738_Photo_TL_photo() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Photo.TL_photo::class,
          org.Tajgram.tgnet.TLRPC.Photo::TLdeserialize, null)

    }

    @Test
    public fun test_000739_Photo_TL_photoEmpty() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Photo.TL_photoEmpty::class,
          org.Tajgram.tgnet.TLRPC.Photo::TLdeserialize, null)

    }

    @Test
    public fun test_000740_PhotoSize_TL_photoCachedSize() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_PhotoSize.TL_photoCachedSize::class,
          org.Tajgram.tgnet.TLRPC.PhotoSize::TLdeserialize, null)

    }

    @Test
    public fun test_000741_PhotoSize_TL_photoPathSize() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_PhotoSize.TL_photoPathSize::class,
          org.Tajgram.tgnet.TLRPC.PhotoSize::TLdeserialize, null)

    }

    @Test
    public fun test_000742_PhotoSize_TL_photoSize() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_PhotoSize.TL_photoSize::class,
          org.Tajgram.tgnet.TLRPC.PhotoSize::TLdeserialize, null)

    }

    @Test
    public fun test_000743_PhotoSize_TL_photoSizeEmpty() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_PhotoSize.TL_photoSizeEmpty::class,
          org.Tajgram.tgnet.TLRPC.PhotoSize::TLdeserialize, null)

    }

    @Test
    public fun test_000744_PhotoSize_TL_photoSizeProgressive() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_PhotoSize.TL_photoSizeProgressive::class,
          org.Tajgram.tgnet.TLRPC.PhotoSize::TLdeserialize, null)

    }

    @Test
    public fun test_000745_PhotoSize_TL_photoStrippedSize() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_PhotoSize.TL_photoStrippedSize::class,
          org.Tajgram.tgnet.TLRPC.PhotoSize::TLdeserialize, null)

    }

    @Test
    public fun test_000746_Poll_TL_poll() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Poll.TL_poll::class,
          org.Tajgram.tgnet.TLRPC.Poll::TLdeserialize, null)

    }

    @Test
    public fun test_000747_PollAnswer_TL_inputPollAnswer() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_PollAnswer.TL_inputPollAnswer::class,
          org.Tajgram.tgnet.TLRPC.PollAnswer::TLdeserialize, null)

    }

    @Test
    public fun test_000748_PollAnswer_TL_pollAnswer() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_PollAnswer.TL_pollAnswer::class,
          org.Tajgram.tgnet.TLRPC.PollAnswer::TLdeserialize, null)

    }

    @Test
    public fun test_000749_PollAnswerVoters_TL_pollAnswerVoters() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_PollAnswerVoters.TL_pollAnswerVoters::class,
          org.Tajgram.tgnet.TLRPC.PollAnswerVoters::TLdeserialize, null)

    }

    @Test
    public fun test_000750_PollResults_TL_pollResults() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_PollResults.TL_pollResults::class,
          org.Tajgram.tgnet.TLRPC.PollResults::TLdeserialize, null)

    }

    @Test
    public fun test_000751_PopularContact_TL_popularContact() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_PopularContact.TL_popularContact::class,
          org.Tajgram.tgnet.TLRPC.TL_popularContact::TLdeserialize, null)

    }

    @Test
    public fun test_000752_PostAddress_TL_postAddress() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_PostAddress.TL_postAddress::class,
          org.Tajgram.tgnet.TLRPC.TL_postAddress::TLdeserialize, null)

    }

    @Test
    public fun test_000753_PostInteractionCounters_TL_postInteractionCountersMessage() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_PostInteractionCounters.TL_postInteractionCountersMessage::class,
          org.Tajgram.tgnet.tl.TL_stats.PostInteractionCounters::TLdeserialize, null)

    }

    @Test
    public fun test_000754_PostInteractionCounters_TL_postInteractionCountersStory() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_PostInteractionCounters.TL_postInteractionCountersStory::class,
          org.Tajgram.tgnet.tl.TL_stats.PostInteractionCounters::TLdeserialize, null)

    }

    @Test
    public fun test_000755_PremiumGiftCodeOption_TL_premiumGiftCodeOption() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_PremiumGiftCodeOption.TL_premiumGiftCodeOption::class,
          org.Tajgram.tgnet.TLRPC.TL_premiumGiftCodeOption::TLdeserialize, null)

    }

    @Test
    public fun test_000756_PremiumSubscriptionOption_TL_premiumSubscriptionOption() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_PremiumSubscriptionOption.TL_premiumSubscriptionOption::class,
          org.Tajgram.tgnet.TLRPC.TL_premiumSubscriptionOption::TLdeserialize, null)

    }

    @Test
    public fun test_000757_PrepaidGiveaway_TL_prepaidGiveaway() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_PrepaidGiveaway.TL_prepaidGiveaway::class,
          org.Tajgram.tgnet.tl.TL_stories.PrepaidGiveaway::TLdeserialize, null)

    }

    @Test
    public fun test_000758_PrepaidGiveaway_TL_prepaidStarsGiveaway() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_PrepaidGiveaway.TL_prepaidStarsGiveaway::class,
          org.Tajgram.tgnet.tl.TL_stories.PrepaidGiveaway::TLdeserialize, null)

    }

    @Test
    public fun test_000759_PrivacyKey_TL_privacyKeyAbout() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_PrivacyKey.TL_privacyKeyAbout::class,
          org.Tajgram.tgnet.TLRPC.PrivacyKey::TLdeserialize, null)

    }

    @Test
    public fun test_000760_PrivacyKey_TL_privacyKeyAddedByPhone() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_PrivacyKey.TL_privacyKeyAddedByPhone::class,
          org.Tajgram.tgnet.TLRPC.PrivacyKey::TLdeserialize, null)

    }

    @Test
    public fun test_000761_PrivacyKey_TL_privacyKeyBirthday() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_PrivacyKey.TL_privacyKeyBirthday::class,
          org.Tajgram.tgnet.TLRPC.PrivacyKey::TLdeserialize, null)

    }

    @Test
    public fun test_000762_PrivacyKey_TL_privacyKeyChatInvite() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_PrivacyKey.TL_privacyKeyChatInvite::class,
          org.Tajgram.tgnet.TLRPC.PrivacyKey::TLdeserialize, null)

    }

    @Test
    public fun test_000763_PrivacyKey_TL_privacyKeyForwards() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_PrivacyKey.TL_privacyKeyForwards::class,
          org.Tajgram.tgnet.TLRPC.PrivacyKey::TLdeserialize, null)

    }

    @Test
    public fun test_000764_PrivacyKey_TL_privacyKeyNoPaidMessages() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_PrivacyKey.TL_privacyKeyNoPaidMessages::class,
          org.Tajgram.tgnet.TLRPC.PrivacyKey::TLdeserialize, null)

    }

    @Test
    public fun test_000765_PrivacyKey_TL_privacyKeyPhoneCall() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_PrivacyKey.TL_privacyKeyPhoneCall::class,
          org.Tajgram.tgnet.TLRPC.PrivacyKey::TLdeserialize, null)

    }

    @Test
    public fun test_000766_PrivacyKey_TL_privacyKeyPhoneNumber() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_PrivacyKey.TL_privacyKeyPhoneNumber::class,
          org.Tajgram.tgnet.TLRPC.PrivacyKey::TLdeserialize, null)

    }

    @Test
    public fun test_000767_PrivacyKey_TL_privacyKeyPhoneP2P() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_PrivacyKey.TL_privacyKeyPhoneP2P::class,
          org.Tajgram.tgnet.TLRPC.PrivacyKey::TLdeserialize, null)

    }

    @Test
    public fun test_000768_PrivacyKey_TL_privacyKeyProfilePhoto() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_PrivacyKey.TL_privacyKeyProfilePhoto::class,
          org.Tajgram.tgnet.TLRPC.PrivacyKey::TLdeserialize, null)

    }

    @Test
    public fun test_000769_PrivacyKey_TL_privacyKeySavedMusic() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_PrivacyKey.TL_privacyKeySavedMusic::class,
          org.Tajgram.tgnet.TLRPC.PrivacyKey::TLdeserialize, null)

    }

    @Test
    public fun test_000770_PrivacyKey_TL_privacyKeyStarGiftsAutoSave() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_PrivacyKey.TL_privacyKeyStarGiftsAutoSave::class,
          org.Tajgram.tgnet.TLRPC.PrivacyKey::TLdeserialize, null)

    }

    @Test
    public fun test_000771_PrivacyKey_TL_privacyKeyStatusTimestamp() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_PrivacyKey.TL_privacyKeyStatusTimestamp::class,
          org.Tajgram.tgnet.TLRPC.PrivacyKey::TLdeserialize, null)

    }

    @Test
    public fun test_000772_PrivacyKey_TL_privacyKeyVoiceMessages() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_PrivacyKey.TL_privacyKeyVoiceMessages::class,
          org.Tajgram.tgnet.TLRPC.PrivacyKey::TLdeserialize, null)

    }

    @Test
    public fun test_000773_PrivacyRule_TL_privacyValueAllowAll() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_PrivacyRule.TL_privacyValueAllowAll::class,
          org.Tajgram.tgnet.TLRPC.PrivacyRule::TLdeserialize, null)

    }

    @Test
    public fun test_000774_PrivacyRule_TL_privacyValueAllowBots() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_PrivacyRule.TL_privacyValueAllowBots::class,
          org.Tajgram.tgnet.TLRPC.PrivacyRule::TLdeserialize, null)

    }

    @Test
    public fun test_000775_PrivacyRule_TL_privacyValueAllowChatParticipants() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_PrivacyRule.TL_privacyValueAllowChatParticipants::class,
          org.Tajgram.tgnet.TLRPC.PrivacyRule::TLdeserialize, null)

    }

    @Test
    public fun test_000776_PrivacyRule_TL_privacyValueAllowCloseFriends() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_PrivacyRule.TL_privacyValueAllowCloseFriends::class,
          org.Tajgram.tgnet.TLRPC.PrivacyRule::TLdeserialize, null)

    }

    @Test
    public fun test_000777_PrivacyRule_TL_privacyValueAllowContacts() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_PrivacyRule.TL_privacyValueAllowContacts::class,
          org.Tajgram.tgnet.TLRPC.PrivacyRule::TLdeserialize, null)

    }

    @Test
    public fun test_000778_PrivacyRule_TL_privacyValueAllowPremium() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_PrivacyRule.TL_privacyValueAllowPremium::class,
          org.Tajgram.tgnet.TLRPC.PrivacyRule::TLdeserialize, null)

    }

    @Test
    public fun test_000779_PrivacyRule_TL_privacyValueAllowUsers() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_PrivacyRule.TL_privacyValueAllowUsers::class,
          org.Tajgram.tgnet.TLRPC.PrivacyRule::TLdeserialize, null)

    }

    @Test
    public fun test_000780_PrivacyRule_TL_privacyValueDisallowAll() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_PrivacyRule.TL_privacyValueDisallowAll::class,
          org.Tajgram.tgnet.TLRPC.PrivacyRule::TLdeserialize, null)

    }

    @Test
    public fun test_000781_PrivacyRule_TL_privacyValueDisallowBots() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_PrivacyRule.TL_privacyValueDisallowBots::class,
          org.Tajgram.tgnet.TLRPC.PrivacyRule::TLdeserialize, null)

    }

    @Test
    public fun test_000782_PrivacyRule_TL_privacyValueDisallowChatParticipants() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_PrivacyRule.TL_privacyValueDisallowChatParticipants::class,
          org.Tajgram.tgnet.TLRPC.PrivacyRule::TLdeserialize, null)

    }

    @Test
    public fun test_000783_PrivacyRule_TL_privacyValueDisallowContacts() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_PrivacyRule.TL_privacyValueDisallowContacts::class,
          org.Tajgram.tgnet.TLRPC.PrivacyRule::TLdeserialize, null)

    }

    @Test
    public fun test_000784_PrivacyRule_TL_privacyValueDisallowUsers() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_PrivacyRule.TL_privacyValueDisallowUsers::class,
          org.Tajgram.tgnet.TLRPC.PrivacyRule::TLdeserialize, null)

    }

    @Test
    public fun test_000785_ProfileTab_TL_profileTabFiles() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ProfileTab.TL_profileTabFiles::class,
          org.Tajgram.tgnet.TLRPC.ProfileTab::TLdeserialize, null)

    }

    @Test
    public fun test_000786_ProfileTab_TL_profileTabGifs() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ProfileTab.TL_profileTabGifs::class,
          org.Tajgram.tgnet.TLRPC.ProfileTab::TLdeserialize, null)

    }

    @Test
    public fun test_000787_ProfileTab_TL_profileTabGifts() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ProfileTab.TL_profileTabGifts::class,
          org.Tajgram.tgnet.TLRPC.ProfileTab::TLdeserialize, null)

    }

    @Test
    public fun test_000788_ProfileTab_TL_profileTabLinks() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ProfileTab.TL_profileTabLinks::class,
          org.Tajgram.tgnet.TLRPC.ProfileTab::TLdeserialize, null)

    }

    @Test
    public fun test_000789_ProfileTab_TL_profileTabMedia() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ProfileTab.TL_profileTabMedia::class,
          org.Tajgram.tgnet.TLRPC.ProfileTab::TLdeserialize, null)

    }

    @Test
    public fun test_000790_ProfileTab_TL_profileTabMusic() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ProfileTab.TL_profileTabMusic::class,
          org.Tajgram.tgnet.TLRPC.ProfileTab::TLdeserialize, null)

    }

    @Test
    public fun test_000791_ProfileTab_TL_profileTabPosts() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ProfileTab.TL_profileTabPosts::class,
          org.Tajgram.tgnet.TLRPC.ProfileTab::TLdeserialize, null)

    }

    @Test
    public fun test_000792_ProfileTab_TL_profileTabVoice() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ProfileTab.TL_profileTabVoice::class,
          org.Tajgram.tgnet.TLRPC.ProfileTab::TLdeserialize, null)

    }

    @Test
    public fun test_000793_PublicForward_TL_publicForwardMessage() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_PublicForward.TL_publicForwardMessage::class,
          org.Tajgram.tgnet.tl.TL_stats.PublicForward::TLdeserialize, null)

    }

    @Test
    public fun test_000794_PublicForward_TL_publicForwardStory() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_PublicForward.TL_publicForwardStory::class,
          org.Tajgram.tgnet.tl.TL_stats.PublicForward::TLdeserialize, null)

    }

    @Test
    public fun test_000795_QuickReply_TL_quickReply() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_QuickReply.TL_quickReply::class,
          org.Tajgram.tgnet.TLRPC.TL_quickReply::TLdeserialize, null)

    }

    @Test
    public fun test_000796_Reaction_TL_reactionCustomEmoji() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Reaction.TL_reactionCustomEmoji::class,
          org.Tajgram.tgnet.TLRPC.Reaction::TLdeserialize, null)

    }

    @Test
    public fun test_000797_Reaction_TL_reactionEmoji() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Reaction.TL_reactionEmoji::class,
          org.Tajgram.tgnet.TLRPC.Reaction::TLdeserialize, null)

    }

    @Test
    public fun test_000798_Reaction_TL_reactionEmpty() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Reaction.TL_reactionEmpty::class,
          org.Tajgram.tgnet.TLRPC.Reaction::TLdeserialize, null)

    }

    @Test
    public fun test_000799_Reaction_TL_reactionPaid() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Reaction.TL_reactionPaid::class,
          org.Tajgram.tgnet.TLRPC.Reaction::TLdeserialize, null)

    }

    @Test
    public fun test_000800_ReactionCount_TL_reactionCount() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ReactionCount.TL_reactionCount::class,
          org.Tajgram.tgnet.TLRPC.ReactionCount::TLdeserialize, null)

    }

    @Test
    public fun test_000801_ReactionNotificationsFrom_TL_reactionNotificationsFromAll() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ReactionNotificationsFrom.TL_reactionNotificationsFromAll::class,
          org.Tajgram.tgnet.tl.TL_account.ReactionNotificationsFrom::TLdeserialize, null)

    }

    @Test
    public fun test_000802_ReactionNotificationsFrom_TL_reactionNotificationsFromContacts() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ReactionNotificationsFrom.TL_reactionNotificationsFromContacts::class,
          org.Tajgram.tgnet.tl.TL_account.ReactionNotificationsFrom::TLdeserialize, null)

    }

    @Test
    public fun test_000803_ReactionsNotifySettings_TL_reactionsNotifySettings() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ReactionsNotifySettings.TL_reactionsNotifySettings::class,
          org.Tajgram.tgnet.tl.TL_account.TL_reactionsNotifySettings::TLdeserialize, null)

    }

    @Test
    public fun test_000804_ReadParticipantDate_TL_readParticipantDate() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ReadParticipantDate.TL_readParticipantDate::class,
          org.Tajgram.tgnet.TLRPC.TL_readParticipantDate::TLdeserialize, null)

    }

    @Test
    public fun test_000805_ReceivedNotifyMessage_TL_receivedNotifyMessage() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ReceivedNotifyMessage.TL_receivedNotifyMessage::class,
          org.Tajgram.tgnet.TLRPC.TL_receivedNotifyMessage::TLdeserialize, null)

    }

    @Test
    public fun test_000806_RecentMeUrl_TL_recentMeUrlChat() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_RecentMeUrl.TL_recentMeUrlChat::class,
          org.Tajgram.tgnet.TLRPC.RecentMeUrl::TLdeserialize, null)

    }

    @Test
    public fun test_000807_RecentMeUrl_TL_recentMeUrlChatInvite() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_RecentMeUrl.TL_recentMeUrlChatInvite::class,
          org.Tajgram.tgnet.TLRPC.RecentMeUrl::TLdeserialize, null)

    }

    @Test
    public fun test_000808_RecentMeUrl_TL_recentMeUrlStickerSet() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_RecentMeUrl.TL_recentMeUrlStickerSet::class,
          org.Tajgram.tgnet.TLRPC.RecentMeUrl::TLdeserialize, null)

    }

    @Test
    public fun test_000809_RecentMeUrl_TL_recentMeUrlUnknown() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_RecentMeUrl.TL_recentMeUrlUnknown::class,
          org.Tajgram.tgnet.TLRPC.RecentMeUrl::TLdeserialize, null)

    }

    @Test
    public fun test_000810_RecentMeUrl_TL_recentMeUrlUser() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_RecentMeUrl.TL_recentMeUrlUser::class,
          org.Tajgram.tgnet.TLRPC.RecentMeUrl::TLdeserialize, null)

    }

    @Test
    public fun test_000811_RecentStory_TL_recentStory() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_RecentStory.TL_recentStory::class,
          org.Tajgram.tgnet.TLRPC.TL_recentStory::TLdeserialize, null)

    }

    @Test
    public fun test_000812_ReplyMarkup_TL_replyInlineMarkup() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ReplyMarkup.TL_replyInlineMarkup::class,
          org.Tajgram.tgnet.TLRPC.ReplyMarkup::TLdeserialize, null)

    }

    @Test
    public fun test_000813_ReplyMarkup_TL_replyKeyboardForceReply() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ReplyMarkup.TL_replyKeyboardForceReply::class,
          org.Tajgram.tgnet.TLRPC.ReplyMarkup::TLdeserialize, null)

    }

    @Test
    public fun test_000814_ReplyMarkup_TL_replyKeyboardHide() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ReplyMarkup.TL_replyKeyboardHide::class,
          org.Tajgram.tgnet.TLRPC.ReplyMarkup::TLdeserialize, null)

    }

    @Test
    public fun test_000815_ReplyMarkup_TL_replyKeyboardMarkup() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ReplyMarkup.TL_replyKeyboardMarkup::class,
          org.Tajgram.tgnet.TLRPC.ReplyMarkup::TLdeserialize, null)

    }

    @Test
    public fun test_000816_ReportReason_TL_inputReportReasonChildAbuse() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ReportReason.TL_inputReportReasonChildAbuse::class,
          org.Tajgram.tgnet.TLRPC.ReportReason::TLdeserialize, null)

    }

    @Test
    public fun test_000817_ReportReason_TL_inputReportReasonCopyright() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ReportReason.TL_inputReportReasonCopyright::class,
          org.Tajgram.tgnet.TLRPC.ReportReason::TLdeserialize, null)

    }

    @Test
    public fun test_000818_ReportReason_TL_inputReportReasonFake() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ReportReason.TL_inputReportReasonFake::class,
          org.Tajgram.tgnet.TLRPC.ReportReason::TLdeserialize, null)

    }

    @Test
    public fun test_000819_ReportReason_TL_inputReportReasonGeoIrrelevant() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ReportReason.TL_inputReportReasonGeoIrrelevant::class,
          org.Tajgram.tgnet.TLRPC.ReportReason::TLdeserialize, null)

    }

    @Test
    public fun test_000820_ReportReason_TL_inputReportReasonIllegalDrugs() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ReportReason.TL_inputReportReasonIllegalDrugs::class,
          org.Tajgram.tgnet.TLRPC.ReportReason::TLdeserialize, null)

    }

    @Test
    public fun test_000821_ReportReason_TL_inputReportReasonOther() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ReportReason.TL_inputReportReasonOther::class,
          org.Tajgram.tgnet.TLRPC.ReportReason::TLdeserialize, null)

    }

    @Test
    public fun test_000822_ReportReason_TL_inputReportReasonPersonalDetails() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ReportReason.TL_inputReportReasonPersonalDetails::class,
          org.Tajgram.tgnet.TLRPC.ReportReason::TLdeserialize, null)

    }

    @Test
    public fun test_000823_ReportReason_TL_inputReportReasonPornography() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ReportReason.TL_inputReportReasonPornography::class,
          org.Tajgram.tgnet.TLRPC.ReportReason::TLdeserialize, null)

    }

    @Test
    public fun test_000824_ReportReason_TL_inputReportReasonSpam() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ReportReason.TL_inputReportReasonSpam::class,
          org.Tajgram.tgnet.TLRPC.ReportReason::TLdeserialize, null)

    }

    @Test
    public fun test_000825_ReportReason_TL_inputReportReasonViolence() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ReportReason.TL_inputReportReasonViolence::class,
          org.Tajgram.tgnet.TLRPC.ReportReason::TLdeserialize, null)

    }

    @Test
    public fun test_000826_ReportResult_TL_reportResultAddComment() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ReportResult.TL_reportResultAddComment::class,
          org.Tajgram.tgnet.TLRPC.ReportResult::TLdeserialize, null)

    }

    @Test
    public fun test_000827_ReportResult_TL_reportResultChooseOption() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ReportResult.TL_reportResultChooseOption::class,
          org.Tajgram.tgnet.TLRPC.ReportResult::TLdeserialize, null)

    }

    @Test
    public fun test_000828_ReportResult_TL_reportResultReported() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ReportResult.TL_reportResultReported::class,
          org.Tajgram.tgnet.TLRPC.ReportResult::TLdeserialize, null)

    }

    @Test
    public fun test_000829_RequestPeerType_TL_requestPeerTypeBroadcast() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_RequestPeerType.TL_requestPeerTypeBroadcast::class,
          org.Tajgram.tgnet.TLRPC.RequestPeerType::TLdeserialize, null)

    }

    @Test
    public fun test_000830_RequestPeerType_TL_requestPeerTypeChat() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_RequestPeerType.TL_requestPeerTypeChat::class,
          org.Tajgram.tgnet.TLRPC.RequestPeerType::TLdeserialize, null)

    }

    @Test
    public fun test_000831_RequestPeerType_TL_requestPeerTypeCreateBot() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_RequestPeerType.TL_requestPeerTypeCreateBot::class,
          org.Tajgram.tgnet.TLRPC.RequestPeerType::TLdeserialize, null)

    }

    @Test
    public fun test_000832_RequestPeerType_TL_requestPeerTypeUser() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_RequestPeerType.TL_requestPeerTypeUser::class,
          org.Tajgram.tgnet.TLRPC.RequestPeerType::TLdeserialize, null)

    }

    @Test
    public fun test_000833_RequirementToContact_TL_requirementToContactEmpty() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_RequirementToContact.TL_requirementToContactEmpty::class,
          org.Tajgram.tgnet.tl.TL_account.RequirementToContact::TLdeserialize, null)

    }

    @Test
    public fun test_000834_RequirementToContact_TL_requirementToContactPaidMessages() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_RequirementToContact.TL_requirementToContactPaidMessages::class,
          org.Tajgram.tgnet.tl.TL_account.RequirementToContact::TLdeserialize, null)

    }

    @Test
    public fun test_000835_RequirementToContact_TL_requirementToContactPremium() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_RequirementToContact.TL_requirementToContactPremium::class,
          org.Tajgram.tgnet.tl.TL_account.RequirementToContact::TLdeserialize, null)

    }

    @Test
    public fun test_000836_RestrictionReason_TL_restrictionReason() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_RestrictionReason.TL_restrictionReason::class,
          org.Tajgram.tgnet.TLRPC.RestrictionReason::TLdeserialize, null)

    }

    @Test
    public fun test_000837_RichMessage_TL_richMessage() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_RichMessage.TL_richMessage::class,
          org.Tajgram.tgnet.tl.TL_iv.RichMessage::TLdeserialize, null)

    }

    @Test
    public fun test_000838_RichText_TL_textAnchor() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_RichText.TL_textAnchor::class,
          org.Tajgram.tgnet.tl.TL_iv.RichText::TLdeserialize, null)

    }

    @Test
    public fun test_000839_RichText_TL_textAutoEmail() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_RichText.TL_textAutoEmail::class,
          org.Tajgram.tgnet.tl.TL_iv.RichText::TLdeserialize, null)

    }

    @Test
    public fun test_000840_RichText_TL_textAutoPhone() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_RichText.TL_textAutoPhone::class,
          org.Tajgram.tgnet.tl.TL_iv.RichText::TLdeserialize, null)

    }

    @Test
    public fun test_000841_RichText_TL_textAutoUrl() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_RichText.TL_textAutoUrl::class,
          org.Tajgram.tgnet.tl.TL_iv.RichText::TLdeserialize, null)

    }

    @Test
    public fun test_000842_RichText_TL_textBankCard() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_RichText.TL_textBankCard::class,
          org.Tajgram.tgnet.tl.TL_iv.RichText::TLdeserialize, null)

    }

    @Test
    public fun test_000843_RichText_TL_textBold() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_RichText.TL_textBold::class,
          org.Tajgram.tgnet.tl.TL_iv.RichText::TLdeserialize, null)

    }

    @Test
    public fun test_000844_RichText_TL_textBotCommand() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_RichText.TL_textBotCommand::class,
          org.Tajgram.tgnet.tl.TL_iv.RichText::TLdeserialize, null)

    }

    @Test
    public fun test_000845_RichText_TL_textCashtag() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_RichText.TL_textCashtag::class,
          org.Tajgram.tgnet.tl.TL_iv.RichText::TLdeserialize, null)

    }

    @Test
    public fun test_000846_RichText_TL_textConcat() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_RichText.TL_textConcat::class,
          org.Tajgram.tgnet.tl.TL_iv.RichText::TLdeserialize, null)

    }

    @Test
    public fun test_000847_RichText_TL_textCustomEmoji() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_RichText.TL_textCustomEmoji::class,
          org.Tajgram.tgnet.tl.TL_iv.RichText::TLdeserialize, null)

    }

    @Test
    public fun test_000848_RichText_TL_textDate() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_RichText.TL_textDate::class,
          org.Tajgram.tgnet.tl.TL_iv.RichText::TLdeserialize, null)

    }

    @Test
    public fun test_000849_RichText_TL_textEmail() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_RichText.TL_textEmail::class,
          org.Tajgram.tgnet.tl.TL_iv.RichText::TLdeserialize, null)

    }

    @Test
    public fun test_000850_RichText_TL_textEmpty() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_RichText.TL_textEmpty::class,
          org.Tajgram.tgnet.tl.TL_iv.RichText::TLdeserialize, null)

    }

    @Test
    public fun test_000851_RichText_TL_textFixed() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_RichText.TL_textFixed::class,
          org.Tajgram.tgnet.tl.TL_iv.RichText::TLdeserialize, null)

    }

    @Test
    public fun test_000852_RichText_TL_textHashtag() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_RichText.TL_textHashtag::class,
          org.Tajgram.tgnet.tl.TL_iv.RichText::TLdeserialize, null)

    }

    @Test
    public fun test_000853_RichText_TL_textImage() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_RichText.TL_textImage::class,
          org.Tajgram.tgnet.tl.TL_iv.RichText::TLdeserialize, null)

    }

    @Test
    public fun test_000854_RichText_TL_textItalic() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_RichText.TL_textItalic::class,
          org.Tajgram.tgnet.tl.TL_iv.RichText::TLdeserialize, null)

    }

    @Test
    public fun test_000855_RichText_TL_textMarked() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_RichText.TL_textMarked::class,
          org.Tajgram.tgnet.tl.TL_iv.RichText::TLdeserialize, null)

    }

    @Test
    public fun test_000856_RichText_TL_textMath() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_RichText.TL_textMath::class,
          org.Tajgram.tgnet.tl.TL_iv.RichText::TLdeserialize, null)

    }

    @Test
    public fun test_000857_RichText_TL_textMention() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_RichText.TL_textMention::class,
          org.Tajgram.tgnet.tl.TL_iv.RichText::TLdeserialize, null)

    }

    @Test
    public fun test_000858_RichText_TL_textMentionName() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_RichText.TL_textMentionName::class,
          org.Tajgram.tgnet.tl.TL_iv.RichText::TLdeserialize, null)

    }

    @Test
    public fun test_000859_RichText_TL_textPhone() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_RichText.TL_textPhone::class,
          org.Tajgram.tgnet.tl.TL_iv.RichText::TLdeserialize, null)

    }

    @Test
    public fun test_000860_RichText_TL_textPlain() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_RichText.TL_textPlain::class,
          org.Tajgram.tgnet.tl.TL_iv.RichText::TLdeserialize, null)

    }

    @Test
    public fun test_000861_RichText_TL_textSpoiler() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_RichText.TL_textSpoiler::class,
          org.Tajgram.tgnet.tl.TL_iv.RichText::TLdeserialize, null)

    }

    @Test
    public fun test_000862_RichText_TL_textStrike() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_RichText.TL_textStrike::class,
          org.Tajgram.tgnet.tl.TL_iv.RichText::TLdeserialize, null)

    }

    @Test
    public fun test_000863_RichText_TL_textSubscript() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_RichText.TL_textSubscript::class,
          org.Tajgram.tgnet.tl.TL_iv.RichText::TLdeserialize, null)

    }

    @Test
    public fun test_000864_RichText_TL_textSuperscript() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_RichText.TL_textSuperscript::class,
          org.Tajgram.tgnet.tl.TL_iv.RichText::TLdeserialize, null)

    }

    @Test
    public fun test_000865_RichText_TL_textUnderline() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_RichText.TL_textUnderline::class,
          org.Tajgram.tgnet.tl.TL_iv.RichText::TLdeserialize, null)

    }

    @Test
    public fun test_000866_RichText_TL_textUrl() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_RichText.TL_textUrl::class,
          org.Tajgram.tgnet.tl.TL_iv.RichText::TLdeserialize, null)

    }

    @Test
    public fun test_000867_SavedDialog_TL_monoForumDialog() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_SavedDialog.TL_monoForumDialog::class,
          org.Tajgram.tgnet.TLRPC.savedDialog::TLdeserialize, null)

    }

    @Test
    public fun test_000868_SavedDialog_TL_savedDialog() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_SavedDialog.TL_savedDialog::class,
          org.Tajgram.tgnet.TLRPC.savedDialog::TLdeserialize, null)

    }

    @Test
    public fun test_000869_SavedReactionTag_TL_savedReactionTag() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_SavedReactionTag.TL_savedReactionTag::class,
          org.Tajgram.tgnet.TLRPC.TL_savedReactionTag::TLdeserialize, null)

    }

    @Test
    public fun test_000870_SavedStarGift_TL_savedStarGift() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_SavedStarGift.TL_savedStarGift::class,
          org.Tajgram.tgnet.tl.TL_stars.SavedStarGift::TLdeserialize, null)

    }

    @Test
    public fun test_000871_SearchPostsFlood_TL_searchPostsFlood() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_SearchPostsFlood.TL_searchPostsFlood::class,
          org.Tajgram.tgnet.TLRPC.SearchPostsFlood::TLdeserialize, null)

    }

    @Test
    public fun test_000872_SearchResultsCalendarPeriod_TL_searchResultsCalendarPeriod() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_SearchResultsCalendarPeriod.TL_searchResultsCalendarPeriod::class,
          org.Tajgram.tgnet.TLRPC.TL_searchResultsCalendarPeriod::TLdeserialize, null)

    }

    @Test
    public fun test_000873_SearchResultsPosition_TL_searchResultPosition() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_SearchResultsPosition.TL_searchResultPosition::class,
          org.Tajgram.tgnet.TLRPC.TL_searchResultPosition::TLdeserialize, null)

    }

    @Test
    public fun test_000874_SecureCredentialsEncrypted_TL_secureCredentialsEncrypted() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_SecureCredentialsEncrypted.TL_secureCredentialsEncrypted::class,
          org.Tajgram.tgnet.TLRPC.TL_secureCredentialsEncrypted::TLdeserialize, null)

    }

    @Test
    public fun test_000875_SecureData_TL_secureData() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_SecureData.TL_secureData::class,
          org.Tajgram.tgnet.TLRPC.TL_secureData::TLdeserialize, null)

    }

    @Test
    public fun test_000876_SecureFile_TL_secureFile() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_SecureFile.TL_secureFile::class,
          org.Tajgram.tgnet.TLRPC.SecureFile::TLdeserialize, null)

    }

    @Test
    public fun test_000877_SecureFile_TL_secureFileEmpty() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_SecureFile.TL_secureFileEmpty::class,
          org.Tajgram.tgnet.TLRPC.SecureFile::TLdeserialize, null)

    }

    @Test
    public
        fun test_000878_SecurePasswordKdfAlgo_TL_securePasswordKdfAlgoPBKDF2HMACSHA512iter100000() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_SecurePasswordKdfAlgo.TL_securePasswordKdfAlgoPBKDF2HMACSHA512iter100000::class,
          org.Tajgram.tgnet.TLRPC.SecurePasswordKdfAlgo::TLdeserialize, null)

    }

    @Test
    public fun test_000879_SecurePasswordKdfAlgo_TL_securePasswordKdfAlgoSHA512() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_SecurePasswordKdfAlgo.TL_securePasswordKdfAlgoSHA512::class,
          org.Tajgram.tgnet.TLRPC.SecurePasswordKdfAlgo::TLdeserialize, null)

    }

    @Test
    public fun test_000880_SecurePasswordKdfAlgo_TL_securePasswordKdfAlgoUnknown() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_SecurePasswordKdfAlgo.TL_securePasswordKdfAlgoUnknown::class,
          org.Tajgram.tgnet.TLRPC.SecurePasswordKdfAlgo::TLdeserialize, null)

    }

    @Test
    public fun test_000881_SecurePlainData_TL_securePlainEmail() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_SecurePlainData.TL_securePlainEmail::class,
          org.Tajgram.tgnet.TLRPC.SecurePlainData::TLdeserialize, null)

    }

    @Test
    public fun test_000882_SecurePlainData_TL_securePlainPhone() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_SecurePlainData.TL_securePlainPhone::class,
          org.Tajgram.tgnet.TLRPC.SecurePlainData::TLdeserialize, null)

    }

    @Test
    public fun test_000883_SecureRequiredType_TL_secureRequiredType() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_SecureRequiredType.TL_secureRequiredType::class,
          org.Tajgram.tgnet.TLRPC.SecureRequiredType::TLdeserialize, null)

    }

    @Test
    public fun test_000884_SecureRequiredType_TL_secureRequiredTypeOneOf() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_SecureRequiredType.TL_secureRequiredTypeOneOf::class,
          org.Tajgram.tgnet.TLRPC.SecureRequiredType::TLdeserialize, null)

    }

    @Test
    public fun test_000885_SecureSecretSettings_TL_secureSecretSettings() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_SecureSecretSettings.TL_secureSecretSettings::class,
          org.Tajgram.tgnet.TLRPC.TL_secureSecretSettings::TLdeserialize, null)

    }

    @Test
    public fun test_000886_SecureValue_TL_secureValue() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_SecureValue.TL_secureValue::class,
          org.Tajgram.tgnet.TLRPC.TL_secureValue::TLdeserialize, null)

    }

    @Test
    public fun test_000887_SecureValueError_TL_secureValueError() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_SecureValueError.TL_secureValueError::class,
          org.Tajgram.tgnet.TLRPC.SecureValueError::TLdeserialize, null)

    }

    @Test
    public fun test_000888_SecureValueError_TL_secureValueErrorData() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_SecureValueError.TL_secureValueErrorData::class,
          org.Tajgram.tgnet.TLRPC.SecureValueError::TLdeserialize, null)

    }

    @Test
    public fun test_000889_SecureValueError_TL_secureValueErrorFile() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_SecureValueError.TL_secureValueErrorFile::class,
          org.Tajgram.tgnet.TLRPC.SecureValueError::TLdeserialize, null)

    }

    @Test
    public fun test_000890_SecureValueError_TL_secureValueErrorFiles() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_SecureValueError.TL_secureValueErrorFiles::class,
          org.Tajgram.tgnet.TLRPC.SecureValueError::TLdeserialize, null)

    }

    @Test
    public fun test_000891_SecureValueError_TL_secureValueErrorFrontSide() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_SecureValueError.TL_secureValueErrorFrontSide::class,
          org.Tajgram.tgnet.TLRPC.SecureValueError::TLdeserialize, null)

    }

    @Test
    public fun test_000892_SecureValueError_TL_secureValueErrorReverseSide() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_SecureValueError.TL_secureValueErrorReverseSide::class,
          org.Tajgram.tgnet.TLRPC.SecureValueError::TLdeserialize, null)

    }

    @Test
    public fun test_000893_SecureValueError_TL_secureValueErrorSelfie() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_SecureValueError.TL_secureValueErrorSelfie::class,
          org.Tajgram.tgnet.TLRPC.SecureValueError::TLdeserialize, null)

    }

    @Test
    public fun test_000894_SecureValueError_TL_secureValueErrorTranslationFile() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_SecureValueError.TL_secureValueErrorTranslationFile::class,
          org.Tajgram.tgnet.TLRPC.SecureValueError::TLdeserialize, null)

    }

    @Test
    public fun test_000895_SecureValueError_TL_secureValueErrorTranslationFiles() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_SecureValueError.TL_secureValueErrorTranslationFiles::class,
          org.Tajgram.tgnet.TLRPC.SecureValueError::TLdeserialize, null)

    }

    @Test
    public fun test_000896_SecureValueHash_TL_secureValueHash() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_SecureValueHash.TL_secureValueHash::class,
          org.Tajgram.tgnet.TLRPC.TL_secureValueHash::TLdeserialize, null)

    }

    @Test
    public fun test_000897_SecureValueType_TL_secureValueTypeAddress() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_SecureValueType.TL_secureValueTypeAddress::class,
          org.Tajgram.tgnet.TLRPC.SecureValueType::TLdeserialize, null)

    }

    @Test
    public fun test_000898_SecureValueType_TL_secureValueTypeBankStatement() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_SecureValueType.TL_secureValueTypeBankStatement::class,
          org.Tajgram.tgnet.TLRPC.SecureValueType::TLdeserialize, null)

    }

    @Test
    public fun test_000899_SecureValueType_TL_secureValueTypeDriverLicense() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_SecureValueType.TL_secureValueTypeDriverLicense::class,
          org.Tajgram.tgnet.TLRPC.SecureValueType::TLdeserialize, null)

    }

    @Test
    public fun test_000900_SecureValueType_TL_secureValueTypeEmail() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_SecureValueType.TL_secureValueTypeEmail::class,
          org.Tajgram.tgnet.TLRPC.SecureValueType::TLdeserialize, null)

    }

    @Test
    public fun test_000901_SecureValueType_TL_secureValueTypeIdentityCard() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_SecureValueType.TL_secureValueTypeIdentityCard::class,
          org.Tajgram.tgnet.TLRPC.SecureValueType::TLdeserialize, null)

    }

    @Test
    public fun test_000902_SecureValueType_TL_secureValueTypeInternalPassport() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_SecureValueType.TL_secureValueTypeInternalPassport::class,
          org.Tajgram.tgnet.TLRPC.SecureValueType::TLdeserialize, null)

    }

    @Test
    public fun test_000903_SecureValueType_TL_secureValueTypePassport() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_SecureValueType.TL_secureValueTypePassport::class,
          org.Tajgram.tgnet.TLRPC.SecureValueType::TLdeserialize, null)

    }

    @Test
    public fun test_000904_SecureValueType_TL_secureValueTypePassportRegistration() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_SecureValueType.TL_secureValueTypePassportRegistration::class,
          org.Tajgram.tgnet.TLRPC.SecureValueType::TLdeserialize, null)

    }

    @Test
    public fun test_000905_SecureValueType_TL_secureValueTypePersonalDetails() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_SecureValueType.TL_secureValueTypePersonalDetails::class,
          org.Tajgram.tgnet.TLRPC.SecureValueType::TLdeserialize, null)

    }

    @Test
    public fun test_000906_SecureValueType_TL_secureValueTypePhone() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_SecureValueType.TL_secureValueTypePhone::class,
          org.Tajgram.tgnet.TLRPC.SecureValueType::TLdeserialize, null)

    }

    @Test
    public fun test_000907_SecureValueType_TL_secureValueTypeRentalAgreement() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_SecureValueType.TL_secureValueTypeRentalAgreement::class,
          org.Tajgram.tgnet.TLRPC.SecureValueType::TLdeserialize, null)

    }

    @Test
    public fun test_000908_SecureValueType_TL_secureValueTypeTemporaryRegistration() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_SecureValueType.TL_secureValueTypeTemporaryRegistration::class,
          org.Tajgram.tgnet.TLRPC.SecureValueType::TLdeserialize, null)

    }

    @Test
    public fun test_000909_SecureValueType_TL_secureValueTypeUtilityBill() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_SecureValueType.TL_secureValueTypeUtilityBill::class,
          org.Tajgram.tgnet.TLRPC.SecureValueType::TLdeserialize, null)

    }

    @Test
    public fun test_000910_SendAsPeer_TL_sendAsPeer() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_SendAsPeer.TL_sendAsPeer::class,
          org.Tajgram.tgnet.TLRPC.TL_sendAsPeer::TLdeserialize, null)

    }

    @Test
    public fun test_000911_SendMessageAction_TL_sendMessageCancelAction() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_SendMessageAction.TL_sendMessageCancelAction::class,
          org.Tajgram.tgnet.TLRPC.SendMessageAction::TLdeserialize, null)

    }

    @Test
    public fun test_000912_SendMessageAction_TL_sendMessageChooseContactAction() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_SendMessageAction.TL_sendMessageChooseContactAction::class,
          org.Tajgram.tgnet.TLRPC.SendMessageAction::TLdeserialize, null)

    }

    @Test
    public fun test_000913_SendMessageAction_TL_sendMessageChooseStickerAction() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_SendMessageAction.TL_sendMessageChooseStickerAction::class,
          org.Tajgram.tgnet.TLRPC.SendMessageAction::TLdeserialize, null)

    }

    @Test
    public fun test_000914_SendMessageAction_TL_sendMessageEmojiInteraction() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_SendMessageAction.TL_sendMessageEmojiInteraction::class,
          org.Tajgram.tgnet.TLRPC.SendMessageAction::TLdeserialize, null)

    }

    @Test
    public fun test_000915_SendMessageAction_TL_sendMessageEmojiInteractionSeen() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_SendMessageAction.TL_sendMessageEmojiInteractionSeen::class,
          org.Tajgram.tgnet.TLRPC.SendMessageAction::TLdeserialize, null)

    }

    @Test
    public fun test_000916_SendMessageAction_TL_sendMessageGamePlayAction() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_SendMessageAction.TL_sendMessageGamePlayAction::class,
          org.Tajgram.tgnet.TLRPC.SendMessageAction::TLdeserialize, null)

    }

    @Test
    public fun test_000917_SendMessageAction_TL_sendMessageGeoLocationAction() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_SendMessageAction.TL_sendMessageGeoLocationAction::class,
          org.Tajgram.tgnet.TLRPC.SendMessageAction::TLdeserialize, null)

    }

    @Test
    public fun test_000918_SendMessageAction_TL_sendMessageHistoryImportAction() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_SendMessageAction.TL_sendMessageHistoryImportAction::class,
          org.Tajgram.tgnet.TLRPC.SendMessageAction::TLdeserialize, null)

    }

    @Test
    public fun test_000919_SendMessageAction_TL_sendMessageRecordAudioAction() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_SendMessageAction.TL_sendMessageRecordAudioAction::class,
          org.Tajgram.tgnet.TLRPC.SendMessageAction::TLdeserialize, null)

    }

    @Test
    public fun test_000920_SendMessageAction_TL_sendMessageRecordRoundAction() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_SendMessageAction.TL_sendMessageRecordRoundAction::class,
          org.Tajgram.tgnet.TLRPC.SendMessageAction::TLdeserialize, null)

    }

    @Test
    public fun test_000921_SendMessageAction_TL_sendMessageRecordVideoAction() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_SendMessageAction.TL_sendMessageRecordVideoAction::class,
          org.Tajgram.tgnet.TLRPC.SendMessageAction::TLdeserialize, null)

    }

    @Test
    public fun test_000922_SendMessageAction_TL_sendMessageRichMessageDraftAction() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_SendMessageAction.TL_sendMessageRichMessageDraftAction::class,
          org.Tajgram.tgnet.TLRPC.SendMessageAction::TLdeserialize, null)

    }

    @Test
    public fun test_000923_SendMessageAction_TL_sendMessageTextDraftAction() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_SendMessageAction.TL_sendMessageTextDraftAction::class,
          org.Tajgram.tgnet.TLRPC.SendMessageAction::TLdeserialize, null)

    }

    @Test
    public fun test_000924_SendMessageAction_TL_sendMessageTypingAction() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_SendMessageAction.TL_sendMessageTypingAction::class,
          org.Tajgram.tgnet.TLRPC.SendMessageAction::TLdeserialize, null)

    }

    @Test
    public fun test_000925_SendMessageAction_TL_sendMessageUploadAudioAction() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_SendMessageAction.TL_sendMessageUploadAudioAction::class,
          org.Tajgram.tgnet.TLRPC.SendMessageAction::TLdeserialize, null)

    }

    @Test
    public fun test_000926_SendMessageAction_TL_sendMessageUploadDocumentAction() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_SendMessageAction.TL_sendMessageUploadDocumentAction::class,
          org.Tajgram.tgnet.TLRPC.SendMessageAction::TLdeserialize, null)

    }

    @Test
    public fun test_000927_SendMessageAction_TL_sendMessageUploadPhotoAction() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_SendMessageAction.TL_sendMessageUploadPhotoAction::class,
          org.Tajgram.tgnet.TLRPC.SendMessageAction::TLdeserialize, null)

    }

    @Test
    public fun test_000928_SendMessageAction_TL_sendMessageUploadRoundAction() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_SendMessageAction.TL_sendMessageUploadRoundAction::class,
          org.Tajgram.tgnet.TLRPC.SendMessageAction::TLdeserialize, null)

    }

    @Test
    public fun test_000929_SendMessageAction_TL_sendMessageUploadVideoAction() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_SendMessageAction.TL_sendMessageUploadVideoAction::class,
          org.Tajgram.tgnet.TLRPC.SendMessageAction::TLdeserialize, null)

    }

    @Test
    public fun test_000930_SendMessageAction_TL_speakingInGroupCallAction() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_SendMessageAction.TL_speakingInGroupCallAction::class,
          org.Tajgram.tgnet.TLRPC.SendMessageAction::TLdeserialize, null)

    }

    @Test
    public fun test_000931_ShippingOption_TL_shippingOption() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ShippingOption.TL_shippingOption::class,
          org.Tajgram.tgnet.TLRPC.TL_shippingOption::TLdeserialize, null)

    }

    @Test
    public fun test_000932_SponsoredMessage_TL_sponsoredMessage() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_SponsoredMessage.TL_sponsoredMessage::class,
          org.Tajgram.tgnet.TLRPC.TL_sponsoredMessage::TLdeserialize, null)

    }

    @Test
    public fun test_000933_SponsoredMessageReportOption_TL_sponsoredMessageReportOption() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_SponsoredMessageReportOption.TL_sponsoredMessageReportOption::class,
          org.Tajgram.tgnet.TLRPC.TL_sponsoredMessageReportOption::TLdeserialize, null)

    }

    @Test
    public fun test_000934_SponsoredPeer_TL_sponsoredPeer() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_SponsoredPeer.TL_sponsoredPeer::class,
          org.Tajgram.tgnet.TLRPC.TL_sponsoredPeer::TLdeserialize, null)

    }

    @Test
    public fun test_000935_StarGift_TL_starGift() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_StarGift.TL_starGift::class,
          org.Tajgram.tgnet.tl.TL_stars.StarGift::TLdeserialize, null)

    }

    @Test
    public fun test_000936_StarGift_TL_starGiftUnique() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_StarGift.TL_starGiftUnique::class,
          org.Tajgram.tgnet.tl.TL_stars.StarGift::TLdeserialize, null)

    }

    @Test
    public fun test_000937_StarGiftActiveAuctionState_TL_starGiftActiveAuctionState() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_StarGiftActiveAuctionState.TL_starGiftActiveAuctionState::class,
          org.Tajgram.tgnet.tl.TL_stars.TL_StarGiftActiveAuctionState::TLdeserialize, null)

    }

    @Test
    public fun test_000938_StarGiftAttribute_TL_starGiftAttributeBackdrop() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_StarGiftAttribute.TL_starGiftAttributeBackdrop::class,
          org.Tajgram.tgnet.tl.TL_stars.StarGiftAttribute::TLdeserialize, null)

    }

    @Test
    public fun test_000939_StarGiftAttribute_TL_starGiftAttributeModel() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_StarGiftAttribute.TL_starGiftAttributeModel::class,
          org.Tajgram.tgnet.tl.TL_stars.StarGiftAttribute::TLdeserialize, null)

    }

    @Test
    public fun test_000940_StarGiftAttribute_TL_starGiftAttributeOriginalDetails() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_StarGiftAttribute.TL_starGiftAttributeOriginalDetails::class,
          org.Tajgram.tgnet.tl.TL_stars.StarGiftAttribute::TLdeserialize, null)

    }

    @Test
    public fun test_000941_StarGiftAttribute_TL_starGiftAttributePattern() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_StarGiftAttribute.TL_starGiftAttributePattern::class,
          org.Tajgram.tgnet.tl.TL_stars.StarGiftAttribute::TLdeserialize, null)

    }

    @Test
    public fun test_000942_StarGiftAttributeCounter_TL_starGiftAttributeCounter() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_StarGiftAttributeCounter.TL_starGiftAttributeCounter::class,
          org.Tajgram.tgnet.tl.TL_stars.starGiftAttributeCounter::TLdeserialize, null)

    }

    @Test
    public fun test_000943_StarGiftAttributeId_TL_starGiftAttributeIdBackdrop() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_StarGiftAttributeId.TL_starGiftAttributeIdBackdrop::class,
          org.Tajgram.tgnet.tl.TL_stars.StarGiftAttributeId::TLdeserialize, null)

    }

    @Test
    public fun test_000944_StarGiftAttributeId_TL_starGiftAttributeIdModel() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_StarGiftAttributeId.TL_starGiftAttributeIdModel::class,
          org.Tajgram.tgnet.tl.TL_stars.StarGiftAttributeId::TLdeserialize, null)

    }

    @Test
    public fun test_000945_StarGiftAttributeId_TL_starGiftAttributeIdPattern() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_StarGiftAttributeId.TL_starGiftAttributeIdPattern::class,
          org.Tajgram.tgnet.tl.TL_stars.StarGiftAttributeId::TLdeserialize, null)

    }

    @Test
    public fun test_000946_StarGiftAttributeRarity_TL_starGiftAttributeRarity() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_StarGiftAttributeRarity.TL_starGiftAttributeRarity::class,
          org.Tajgram.tgnet.tl.TL_stars.StarGiftAttributeRarity::TLdeserialize, null)

    }

    @Test
    public fun test_000947_StarGiftAttributeRarity_TL_starGiftAttributeRarityEpic() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_StarGiftAttributeRarity.TL_starGiftAttributeRarityEpic::class,
          org.Tajgram.tgnet.tl.TL_stars.StarGiftAttributeRarity::TLdeserialize, null)

    }

    @Test
    public fun test_000948_StarGiftAttributeRarity_TL_starGiftAttributeRarityLegendary() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_StarGiftAttributeRarity.TL_starGiftAttributeRarityLegendary::class,
          org.Tajgram.tgnet.tl.TL_stars.StarGiftAttributeRarity::TLdeserialize, null)

    }

    @Test
    public fun test_000949_StarGiftAttributeRarity_TL_starGiftAttributeRarityRare() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_StarGiftAttributeRarity.TL_starGiftAttributeRarityRare::class,
          org.Tajgram.tgnet.tl.TL_stars.StarGiftAttributeRarity::TLdeserialize, null)

    }

    @Test
    public fun test_000950_StarGiftAttributeRarity_TL_starGiftAttributeRarityUncommon() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_StarGiftAttributeRarity.TL_starGiftAttributeRarityUncommon::class,
          org.Tajgram.tgnet.tl.TL_stars.StarGiftAttributeRarity::TLdeserialize, null)

    }

    @Test
    public fun test_000951_StarGiftAuctionAcquiredGift_TL_starGiftAuctionAcquiredGift() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_StarGiftAuctionAcquiredGift.TL_starGiftAuctionAcquiredGift::class,
          org.Tajgram.tgnet.tl.TL_stars.TL_StarGiftAuctionAcquiredGift::TLdeserialize, null)

    }

    @Test
    public fun test_000952_StarGiftAuctionRound_TL_starGiftAuctionRound() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_StarGiftAuctionRound.TL_starGiftAuctionRound::class,
          org.Tajgram.tgnet.tl.TL_stars.StarGiftAuctionRound::TLdeserialize, null)

    }

    @Test
    public fun test_000953_StarGiftAuctionRound_TL_starGiftAuctionRoundExtendable() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_StarGiftAuctionRound.TL_starGiftAuctionRoundExtendable::class,
          org.Tajgram.tgnet.tl.TL_stars.StarGiftAuctionRound::TLdeserialize, null)

    }

    @Test
    public fun test_000954_StarGiftAuctionState_TL_starGiftAuctionState() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_StarGiftAuctionState.TL_starGiftAuctionState::class,
          org.Tajgram.tgnet.tl.TL_stars.StarGiftAuctionState::TLdeserialize, null)

    }

    @Test
    public fun test_000955_StarGiftAuctionState_TL_starGiftAuctionStateFinished() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_StarGiftAuctionState.TL_starGiftAuctionStateFinished::class,
          org.Tajgram.tgnet.tl.TL_stars.StarGiftAuctionState::TLdeserialize, null)

    }

    @Test
    public fun test_000956_StarGiftAuctionState_TL_starGiftAuctionStateNotModified() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_StarGiftAuctionState.TL_starGiftAuctionStateNotModified::class,
          org.Tajgram.tgnet.tl.TL_stars.StarGiftAuctionState::TLdeserialize, null)

    }

    @Test
    public fun test_000957_StarGiftAuctionUserState_TL_starGiftAuctionUserState() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_StarGiftAuctionUserState.TL_starGiftAuctionUserState::class,
          org.Tajgram.tgnet.tl.TL_stars.TL_StarGiftAuctionUserState::TLdeserialize, null)

    }

    @Test
    public fun test_000958_StarGiftBackground_TL_starGiftBackground() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_StarGiftBackground.TL_starGiftBackground::class,
          org.Tajgram.tgnet.tl.TL_stars.TL_starGiftBackground::TLdeserialize, null)

    }

    @Test
    public fun test_000959_StarGiftCollection_TL_starGiftCollection() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_StarGiftCollection.TL_starGiftCollection::class,
          org.Tajgram.tgnet.tl.TL_stars.TL_starGiftCollection::TLdeserialize, null)

    }

    @Test
    public fun test_000960_StarGiftUpgradePrice_TL_starGiftUpgradePrice() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_StarGiftUpgradePrice.TL_starGiftUpgradePrice::class,
          org.Tajgram.tgnet.tl.TL_stars.StarGiftUpgradePrice::TLdeserialize, null)

    }

    @Test
    public fun test_000961_StarRefProgram_TL_starRefProgram() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_StarRefProgram.TL_starRefProgram::class,
          org.Tajgram.tgnet.tl.TL_payments.starRefProgram::TLdeserialize, null)

    }

    @Test
    public fun test_000962_StarsAmount_TL_starsAmount() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_StarsAmount.TL_starsAmount::class,
          org.Tajgram.tgnet.tl.TL_stars.StarsAmount::TLdeserialize, null)

    }

    @Test
    public fun test_000963_StarsAmount_TL_starsTonAmount() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_StarsAmount.TL_starsTonAmount::class,
          org.Tajgram.tgnet.tl.TL_stars.StarsAmount::TLdeserialize, null)

    }

    @Test
    public fun test_000964_StarsGiftOption_TL_starsGiftOption() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_StarsGiftOption.TL_starsGiftOption::class,
          org.Tajgram.tgnet.tl.TL_stars.TL_starsGiftOption::TLdeserialize, null)

    }

    @Test
    public fun test_000965_StarsGiveawayOption_TL_starsGiveawayOption() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_StarsGiveawayOption.TL_starsGiveawayOption::class,
          org.Tajgram.tgnet.tl.TL_stars.TL_starsGiveawayOption::TLdeserialize, null)

    }

    @Test
    public fun test_000966_StarsGiveawayWinnersOption_TL_starsGiveawayWinnersOption() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_StarsGiveawayWinnersOption.TL_starsGiveawayWinnersOption::class,
          org.Tajgram.tgnet.tl.TL_stars.TL_starsGiveawayWinnersOption::TLdeserialize, null)

    }

    @Test
    public fun test_000967_StarsRating_TL_starsRating() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_StarsRating.TL_starsRating::class,
          org.Tajgram.tgnet.tl.TL_stars.Tl_starsRating::TLdeserialize, null)

    }

    @Test
    public fun test_000968_StarsRevenueStatus_TL_starsRevenueStatus() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_StarsRevenueStatus.TL_starsRevenueStatus::class,
          org.Tajgram.tgnet.TLRPC.TL_starsRevenueStatus::TLdeserialize, null)

    }

    @Test
    public fun test_000969_StarsSubscription_TL_starsSubscription() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_StarsSubscription.TL_starsSubscription::class,
          org.Tajgram.tgnet.tl.TL_stars.StarsSubscription::TLdeserialize, null)

    }

    @Test
    public fun test_000970_StarsSubscriptionPricing_TL_starsSubscriptionPricing() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_StarsSubscriptionPricing.TL_starsSubscriptionPricing::class,
          org.Tajgram.tgnet.tl.TL_stars.TL_starsSubscriptionPricing::TLdeserialize, null)

    }

    @Test
    public fun test_000971_StarsTopupOption_TL_starsTopupOption() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_StarsTopupOption.TL_starsTopupOption::class,
          org.Tajgram.tgnet.tl.TL_stars.TL_starsTopupOption::TLdeserialize, null)

    }

    @Test
    public fun test_000972_StarsTransaction_TL_starsTransaction() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_StarsTransaction.TL_starsTransaction::class,
          org.Tajgram.tgnet.tl.TL_stars.StarsTransaction::TLdeserialize, null)

    }

    @Test
    public fun test_000973_StarsTransactionPeer_TL_starsTransactionPeer() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_StarsTransactionPeer.TL_starsTransactionPeer::class,
          org.Tajgram.tgnet.tl.TL_stars.StarsTransactionPeer::TLdeserialize, null)

    }

    @Test
    public fun test_000974_StarsTransactionPeer_TL_starsTransactionPeerAPI() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_StarsTransactionPeer.TL_starsTransactionPeerAPI::class,
          org.Tajgram.tgnet.tl.TL_stars.StarsTransactionPeer::TLdeserialize, null)

    }

    @Test
    public fun test_000975_StarsTransactionPeer_TL_starsTransactionPeerAds() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_StarsTransactionPeer.TL_starsTransactionPeerAds::class,
          org.Tajgram.tgnet.tl.TL_stars.StarsTransactionPeer::TLdeserialize, null)

    }

    @Test
    public fun test_000976_StarsTransactionPeer_TL_starsTransactionPeerAppStore() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_StarsTransactionPeer.TL_starsTransactionPeerAppStore::class,
          org.Tajgram.tgnet.tl.TL_stars.StarsTransactionPeer::TLdeserialize, null)

    }

    @Test
    public fun test_000977_StarsTransactionPeer_TL_starsTransactionPeerFragment() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_StarsTransactionPeer.TL_starsTransactionPeerFragment::class,
          org.Tajgram.tgnet.tl.TL_stars.StarsTransactionPeer::TLdeserialize, null)

    }

    @Test
    public fun test_000978_StarsTransactionPeer_TL_starsTransactionPeerPlayMarket() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_StarsTransactionPeer.TL_starsTransactionPeerPlayMarket::class,
          org.Tajgram.tgnet.tl.TL_stars.StarsTransactionPeer::TLdeserialize, null)

    }

    @Test
    public fun test_000979_StarsTransactionPeer_TL_starsTransactionPeerPremiumBot() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_StarsTransactionPeer.TL_starsTransactionPeerPremiumBot::class,
          org.Tajgram.tgnet.tl.TL_stars.StarsTransactionPeer::TLdeserialize, null)

    }

    @Test
    public fun test_000980_StarsTransactionPeer_TL_starsTransactionPeerUnsupported() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_StarsTransactionPeer.TL_starsTransactionPeerUnsupported::class,
          org.Tajgram.tgnet.tl.TL_stars.StarsTransactionPeer::TLdeserialize, null)

    }

    @Test
    public fun test_000981_StatsAbsValueAndPrev_TL_statsAbsValueAndPrev() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_StatsAbsValueAndPrev.TL_statsAbsValueAndPrev::class,
          org.Tajgram.tgnet.tl.TL_stats.TL_statsAbsValueAndPrev::TLdeserialize, null)

    }

    @Test
    public fun test_000982_StatsDateRangeDays_TL_statsDateRangeDays() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_StatsDateRangeDays.TL_statsDateRangeDays::class,
          org.Tajgram.tgnet.tl.TL_stats.TL_statsDateRangeDays::TLdeserialize, null)

    }

    @Test
    public fun test_000983_StatsGraph_TL_statsGraph() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_StatsGraph.TL_statsGraph::class,
          org.Tajgram.tgnet.tl.TL_stats.StatsGraph::TLdeserialize, null)

    }

    @Test
    public fun test_000984_StatsGraph_TL_statsGraphAsync() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_StatsGraph.TL_statsGraphAsync::class,
          org.Tajgram.tgnet.tl.TL_stats.StatsGraph::TLdeserialize, null)

    }

    @Test
    public fun test_000985_StatsGraph_TL_statsGraphError() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_StatsGraph.TL_statsGraphError::class,
          org.Tajgram.tgnet.tl.TL_stats.StatsGraph::TLdeserialize, null)

    }

    @Test
    public fun test_000986_StatsGroupTopAdmin_TL_statsGroupTopAdmin() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_StatsGroupTopAdmin.TL_statsGroupTopAdmin::class,
          org.Tajgram.tgnet.tl.TL_stats.TL_statsGroupTopAdmin::TLdeserialize, null)

    }

    @Test
    public fun test_000987_StatsGroupTopInviter_TL_statsGroupTopInviter() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_StatsGroupTopInviter.TL_statsGroupTopInviter::class,
          org.Tajgram.tgnet.tl.TL_stats.TL_statsGroupTopInviter::TLdeserialize, null)

    }

    @Test
    public fun test_000988_StatsGroupTopPoster_TL_statsGroupTopPoster() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_StatsGroupTopPoster.TL_statsGroupTopPoster::class,
          org.Tajgram.tgnet.tl.TL_stats.TL_statsGroupTopPoster::TLdeserialize, null)

    }

    @Test
    public fun test_000989_StatsPercentValue_TL_statsPercentValue() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_StatsPercentValue.TL_statsPercentValue::class,
          org.Tajgram.tgnet.tl.TL_stats.TL_statsPercentValue::TLdeserialize, null)

    }

    @Test
    public fun test_000990_StatsURL_TL_statsURL() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_StatsURL.TL_statsURL::class,
          org.Tajgram.tgnet.TLRPC.TL_statsURL::TLdeserialize, null)

    }

    @Test
    public fun test_000991_StickerKeyword_TL_stickerKeyword() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_StickerKeyword.TL_stickerKeyword::class,
          org.Tajgram.tgnet.TLRPC.TL_stickerKeyword::TLdeserialize, null)

    }

    @Test
    public fun test_000992_StickerPack_TL_stickerPack() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_StickerPack.TL_stickerPack::class,
          org.Tajgram.tgnet.TLRPC.TL_stickerPack::TLdeserialize, null)

    }

    @Test
    public fun test_000993_StickerSet_TL_stickerSet() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_StickerSet.TL_stickerSet::class,
          org.Tajgram.tgnet.TLRPC.StickerSet::TLdeserialize, null)

    }

    @Test
    public fun test_000994_StickerSetCovered_TL_stickerSetCovered() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_StickerSetCovered.TL_stickerSetCovered::class,
          org.Tajgram.tgnet.TLRPC.StickerSetCovered::TLdeserialize, null)

    }

    @Test
    public fun test_000995_StickerSetCovered_TL_stickerSetFullCovered() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_StickerSetCovered.TL_stickerSetFullCovered::class,
          org.Tajgram.tgnet.TLRPC.StickerSetCovered::TLdeserialize, null)

    }

    @Test
    public fun test_000996_StickerSetCovered_TL_stickerSetMultiCovered() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_StickerSetCovered.TL_stickerSetMultiCovered::class,
          org.Tajgram.tgnet.TLRPC.StickerSetCovered::TLdeserialize, null)

    }

    @Test
    public fun test_000997_StickerSetCovered_TL_stickerSetNoCovered() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_StickerSetCovered.TL_stickerSetNoCovered::class,
          org.Tajgram.tgnet.TLRPC.StickerSetCovered::TLdeserialize, null)

    }

    @Test
    public fun test_000998_StoriesStealthMode_TL_storiesStealthMode() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_StoriesStealthMode.TL_storiesStealthMode::class,
          org.Tajgram.tgnet.tl.TL_stories.TL_storiesStealthMode::TLdeserialize, null)

    }

    @Test
    public fun test_000999_StoryAlbum_TL_storyAlbum() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_StoryAlbum.TL_storyAlbum::class,
          org.Tajgram.tgnet.tl.TL_stories.TL_storyAlbum::TLdeserialize, null)

    }

    @Test
    public fun test_001000_StoryFwdHeader_TL_storyFwdHeader() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_StoryFwdHeader.TL_storyFwdHeader::class,
          org.Tajgram.tgnet.tl.TL_stories.StoryFwdHeader::TLdeserialize, null)

    }

    @Test
    public fun test_001001_StoryItem_TL_storyItem() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_StoryItem.TL_storyItem::class,
          org.Tajgram.tgnet.tl.TL_stories.StoryItem::TLdeserialize, null)

    }

    @Test
    public fun test_001002_StoryItem_TL_storyItemDeleted() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_StoryItem.TL_storyItemDeleted::class,
          org.Tajgram.tgnet.tl.TL_stories.StoryItem::TLdeserialize, null)

    }

    @Test
    public fun test_001003_StoryItem_TL_storyItemSkipped() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_StoryItem.TL_storyItemSkipped::class,
          org.Tajgram.tgnet.tl.TL_stories.StoryItem::TLdeserialize, null)

    }

    @Test
    public fun test_001004_StoryReaction_TL_storyReaction() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_StoryReaction.TL_storyReaction::class,
          org.Tajgram.tgnet.tl.TL_stories.StoryReaction::TLdeserialize, null)

    }

    @Test
    public fun test_001005_StoryReaction_TL_storyReactionPublicForward() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_StoryReaction.TL_storyReactionPublicForward::class,
          org.Tajgram.tgnet.tl.TL_stories.StoryReaction::TLdeserialize, null)

    }

    @Test
    public fun test_001006_StoryReaction_TL_storyReactionPublicRepost() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_StoryReaction.TL_storyReactionPublicRepost::class,
          org.Tajgram.tgnet.tl.TL_stories.StoryReaction::TLdeserialize, null)

    }

    @Test
    public fun test_001007_StoryView_TL_storyView() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_StoryView.TL_storyView::class,
          org.Tajgram.tgnet.tl.TL_stories.StoryView::TLdeserialize, null)

    }

    @Test
    public fun test_001008_StoryView_TL_storyViewPublicForward() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_StoryView.TL_storyViewPublicForward::class,
          org.Tajgram.tgnet.tl.TL_stories.StoryView::TLdeserialize, null)

    }

    @Test
    public fun test_001009_StoryView_TL_storyViewPublicRepost() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_StoryView.TL_storyViewPublicRepost::class,
          org.Tajgram.tgnet.tl.TL_stories.StoryView::TLdeserialize, null)

    }

    @Test
    public fun test_001010_StoryViews_TL_storyViews() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_StoryViews.TL_storyViews::class,
          org.Tajgram.tgnet.tl.TL_stories.StoryViews::TLdeserialize, null)

    }

    @Test
    public fun test_001011_SuggestedPost_TL_suggestedPost() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_SuggestedPost.TL_suggestedPost::class,
          org.Tajgram.tgnet.TLRPC.SuggestedPost::TLdeserialize, null)

    }

    @Test
    public fun test_001012_TextWithEntities_TL_textWithEntities() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_TextWithEntities.TL_textWithEntities::class,
          org.Tajgram.tgnet.TLRPC.TL_textWithEntities::TLdeserialize, null)

    }

    @Test
    public fun test_001013_Theme_TL_theme() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Theme.TL_theme::class,
          org.Tajgram.tgnet.TLRPC.Theme::TLdeserialize, null)

    }

    @Test
    public fun test_001014_ThemeSettings_TL_themeSettings() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ThemeSettings.TL_themeSettings::class,
          org.Tajgram.tgnet.TLRPC.ThemeSettings::TLdeserialize, null)
          test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ThemeSettings.TL_themeSettings::class,
              org.Tajgram.tgnet.TLRPC.TL_themeSettings::TLdeserialize, null)

    }

    @Test
    public fun test_001015_Timezone_TL_timezone() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Timezone.TL_timezone::class,
          org.Tajgram.tgnet.TLRPC.TL_timezone::TLdeserialize, null)

    }

    @Test
    public fun test_001016_TodoCompletion_TL_todoCompletion() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_TodoCompletion.TL_todoCompletion::class,
          org.Tajgram.tgnet.TLRPC.TodoCompletion::TLdeserialize, null)

    }

    @Test
    public fun test_001017_TodoItem_TL_todoItem() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_TodoItem.TL_todoItem::class,
          org.Tajgram.tgnet.TLRPC.TodoItem::TLdeserialize, null)

    }

    @Test
    public fun test_001018_TodoList_TL_todoList() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_TodoList.TL_todoList::class,
          org.Tajgram.tgnet.TLRPC.TodoList::TLdeserialize, null)

    }

    @Test
    public fun test_001019_TopPeer_TL_topPeer() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_TopPeer.TL_topPeer::class,
          org.Tajgram.tgnet.TLRPC.TL_topPeer::TLdeserialize, null)

    }

    @Test
    public fun test_001020_TopPeerCategory_TL_topPeerCategoryBotsApp() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_TopPeerCategory.TL_topPeerCategoryBotsApp::class,
          org.Tajgram.tgnet.TLRPC.TopPeerCategory::TLdeserialize, null)

    }

    @Test
    public fun test_001021_TopPeerCategory_TL_topPeerCategoryBotsGuestChat() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_TopPeerCategory.TL_topPeerCategoryBotsGuestChat::class,
          org.Tajgram.tgnet.TLRPC.TopPeerCategory::TLdeserialize, null)

    }

    @Test
    public fun test_001022_TopPeerCategory_TL_topPeerCategoryBotsInline() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_TopPeerCategory.TL_topPeerCategoryBotsInline::class,
          org.Tajgram.tgnet.TLRPC.TopPeerCategory::TLdeserialize, null)

    }

    @Test
    public fun test_001023_TopPeerCategory_TL_topPeerCategoryBotsPM() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_TopPeerCategory.TL_topPeerCategoryBotsPM::class,
          org.Tajgram.tgnet.TLRPC.TopPeerCategory::TLdeserialize, null)

    }

    @Test
    public fun test_001024_TopPeerCategory_TL_topPeerCategoryChannels() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_TopPeerCategory.TL_topPeerCategoryChannels::class,
          org.Tajgram.tgnet.TLRPC.TopPeerCategory::TLdeserialize, null)

    }

    @Test
    public fun test_001025_TopPeerCategory_TL_topPeerCategoryCorrespondents() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_TopPeerCategory.TL_topPeerCategoryCorrespondents::class,
          org.Tajgram.tgnet.TLRPC.TopPeerCategory::TLdeserialize, null)

    }

    @Test
    public fun test_001026_TopPeerCategory_TL_topPeerCategoryForwardChats() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_TopPeerCategory.TL_topPeerCategoryForwardChats::class,
          org.Tajgram.tgnet.TLRPC.TopPeerCategory::TLdeserialize, null)

    }

    @Test
    public fun test_001027_TopPeerCategory_TL_topPeerCategoryForwardUsers() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_TopPeerCategory.TL_topPeerCategoryForwardUsers::class,
          org.Tajgram.tgnet.TLRPC.TopPeerCategory::TLdeserialize, null)

    }

    @Test
    public fun test_001028_TopPeerCategory_TL_topPeerCategoryGroups() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_TopPeerCategory.TL_topPeerCategoryGroups::class,
          org.Tajgram.tgnet.TLRPC.TopPeerCategory::TLdeserialize, null)

    }

    @Test
    public fun test_001029_TopPeerCategory_TL_topPeerCategoryPhoneCalls() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_TopPeerCategory.TL_topPeerCategoryPhoneCalls::class,
          org.Tajgram.tgnet.TLRPC.TopPeerCategory::TLdeserialize, null)

    }

    @Test
    public fun test_001030_TopPeerCategoryPeers_TL_topPeerCategoryPeers() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_TopPeerCategoryPeers.TL_topPeerCategoryPeers::class,
          org.Tajgram.tgnet.TLRPC.TL_topPeerCategoryPeers::TLdeserialize, null)

    }

    @Test
    public fun test_001031_Update_TL_updateAiComposeTones() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Update.TL_updateAiComposeTones::class,
          org.Tajgram.tgnet.TLRPC.Update::TLdeserialize, null)

    }

    @Test
    public fun test_001032_Update_TL_updateAttachMenuBots() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Update.TL_updateAttachMenuBots::class,
          org.Tajgram.tgnet.TLRPC.Update::TLdeserialize, null)

    }

    @Test
    public fun test_001033_Update_TL_updateBotCommands() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Update.TL_updateBotCommands::class,
          org.Tajgram.tgnet.TLRPC.Update::TLdeserialize, null)

    }

    @Test
    public fun test_001034_Update_TL_updateBotMenuButton() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Update.TL_updateBotMenuButton::class,
          org.Tajgram.tgnet.TLRPC.Update::TLdeserialize, null)

    }

    @Test
    public fun test_001035_Update_TL_updateBotPurchasedPaidMedia() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Update.TL_updateBotPurchasedPaidMedia::class,
          org.Tajgram.tgnet.TLRPC.Update::TLdeserialize, null)

    }

    @Test
    public fun test_001036_Update_TL_updateChannel() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Update.TL_updateChannel::class,
          org.Tajgram.tgnet.TLRPC.Update::TLdeserialize, null)

    }

    @Test
    public fun test_001037_Update_TL_updateChannelAvailableMessages() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Update.TL_updateChannelAvailableMessages::class,
          org.Tajgram.tgnet.TLRPC.Update::TLdeserialize, null)

    }

    @Test
    public fun test_001038_Update_TL_updateChannelMessageForwards() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Update.TL_updateChannelMessageForwards::class,
          org.Tajgram.tgnet.TLRPC.Update::TLdeserialize, null)

    }

    @Test
    public fun test_001039_Update_TL_updateChannelMessageViews() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Update.TL_updateChannelMessageViews::class,
          org.Tajgram.tgnet.TLRPC.Update::TLdeserialize, null)

    }

    @Test
    public fun test_001040_Update_TL_updateChannelParticipant() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Update.TL_updateChannelParticipant::class,
          org.Tajgram.tgnet.TLRPC.Update::TLdeserialize, null)

    }

    @Test
    public fun test_001041_Update_TL_updateChannelReadMessagesContents() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Update.TL_updateChannelReadMessagesContents::class,
          org.Tajgram.tgnet.TLRPC.Update::TLdeserialize, null)

    }

    @Test
    public fun test_001042_Update_TL_updateChannelTooLong() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Update.TL_updateChannelTooLong::class,
          org.Tajgram.tgnet.TLRPC.Update::TLdeserialize, null)

    }

    @Test
    public fun test_001043_Update_TL_updateChannelUserTyping() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Update.TL_updateChannelUserTyping::class,
          org.Tajgram.tgnet.TLRPC.Update::TLdeserialize, null)

    }

    @Test
    public fun test_001044_Update_TL_updateChannelViewForumAsMessages() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Update.TL_updateChannelViewForumAsMessages::class,
          org.Tajgram.tgnet.TLRPC.Update::TLdeserialize, null)

    }

    @Test
    public fun test_001045_Update_TL_updateChannelWebPage() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Update.TL_updateChannelWebPage::class,
          org.Tajgram.tgnet.TLRPC.Update::TLdeserialize, null)

    }

    @Test
    public fun test_001046_Update_TL_updateChat() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Update.TL_updateChat::class,
          org.Tajgram.tgnet.TLRPC.Update::TLdeserialize, null)

    }

    @Test
    public fun test_001047_Update_TL_updateChatDefaultBannedRights() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Update.TL_updateChatDefaultBannedRights::class,
          org.Tajgram.tgnet.TLRPC.Update::TLdeserialize, null)

    }

    @Test
    public fun test_001048_Update_TL_updateChatParticipantAdd() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Update.TL_updateChatParticipantAdd::class,
          org.Tajgram.tgnet.TLRPC.Update::TLdeserialize, null)

    }

    @Test
    public fun test_001049_Update_TL_updateChatParticipantAdmin() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Update.TL_updateChatParticipantAdmin::class,
          org.Tajgram.tgnet.TLRPC.Update::TLdeserialize, null)

    }

    @Test
    public fun test_001050_Update_TL_updateChatParticipantDelete() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Update.TL_updateChatParticipantDelete::class,
          org.Tajgram.tgnet.TLRPC.Update::TLdeserialize, null)

    }

    @Test
    public fun test_001051_Update_TL_updateChatParticipantRank() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Update.TL_updateChatParticipantRank::class,
          org.Tajgram.tgnet.TLRPC.Update::TLdeserialize, null)

    }

    @Test
    public fun test_001052_Update_TL_updateChatParticipants() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Update.TL_updateChatParticipants::class,
          org.Tajgram.tgnet.TLRPC.Update::TLdeserialize, null)

    }

    @Test
    public fun test_001053_Update_TL_updateChatUserTyping() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Update.TL_updateChatUserTyping::class,
          org.Tajgram.tgnet.TLRPC.Update::TLdeserialize, null)

    }

    @Test
    public fun test_001054_Update_TL_updateConfig() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Update.TL_updateConfig::class,
          org.Tajgram.tgnet.TLRPC.Update::TLdeserialize, null)

    }

    @Test
    public fun test_001055_Update_TL_updateContactsReset() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Update.TL_updateContactsReset::class,
          org.Tajgram.tgnet.TLRPC.Update::TLdeserialize, null)

    }

    @Test
    public fun test_001056_Update_TL_updateDcOptions() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Update.TL_updateDcOptions::class,
          org.Tajgram.tgnet.TLRPC.Update::TLdeserialize, null)

    }

    @Test
    public fun test_001057_Update_TL_updateDeleteChannelMessages() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Update.TL_updateDeleteChannelMessages::class,
          org.Tajgram.tgnet.TLRPC.Update::TLdeserialize, null)

    }

    @Test
    public fun test_001058_Update_TL_updateDeleteGroupCallMessages() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Update.TL_updateDeleteGroupCallMessages::class,
          org.Tajgram.tgnet.TLRPC.Update::TLdeserialize, null)

    }

    @Test
    public fun test_001059_Update_TL_updateDeleteMessages() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Update.TL_updateDeleteMessages::class,
          org.Tajgram.tgnet.TLRPC.Update::TLdeserialize, null)

    }

    @Test
    public fun test_001060_Update_TL_updateDeleteQuickReply() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Update.TL_updateDeleteQuickReply::class,
          org.Tajgram.tgnet.TLRPC.Update::TLdeserialize, null)

    }

    @Test
    public fun test_001061_Update_TL_updateDeleteQuickReplyMessages() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Update.TL_updateDeleteQuickReplyMessages::class,
          org.Tajgram.tgnet.TLRPC.Update::TLdeserialize, null)

    }

    @Test
    public fun test_001062_Update_TL_updateDeleteScheduledMessages() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Update.TL_updateDeleteScheduledMessages::class,
          org.Tajgram.tgnet.TLRPC.Update::TLdeserialize, null)

    }

    @Test
    public fun test_001063_Update_TL_updateDialogFilter() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Update.TL_updateDialogFilter::class,
          org.Tajgram.tgnet.TLRPC.Update::TLdeserialize, null)

    }

    @Test
    public fun test_001064_Update_TL_updateDialogFilterOrder() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Update.TL_updateDialogFilterOrder::class,
          org.Tajgram.tgnet.TLRPC.Update::TLdeserialize, null)

    }

    @Test
    public fun test_001065_Update_TL_updateDialogFilters() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Update.TL_updateDialogFilters::class,
          org.Tajgram.tgnet.TLRPC.Update::TLdeserialize, null)

    }

    @Test
    public fun test_001066_Update_TL_updateDialogPinned() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Update.TL_updateDialogPinned::class,
          org.Tajgram.tgnet.TLRPC.Update::TLdeserialize, null)

    }

    @Test
    public fun test_001067_Update_TL_updateDialogUnreadMark() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Update.TL_updateDialogUnreadMark::class,
          org.Tajgram.tgnet.TLRPC.Update::TLdeserialize, null)

    }

    @Test
    public fun test_001068_Update_TL_updateDraftMessage() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Update.TL_updateDraftMessage::class,
          org.Tajgram.tgnet.TLRPC.Update::TLdeserialize, null)

    }

    @Test
    public fun test_001069_Update_TL_updateEditChannelMessage() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Update.TL_updateEditChannelMessage::class,
          org.Tajgram.tgnet.TLRPC.Update::TLdeserialize, null)

    }

    @Test
    public fun test_001070_Update_TL_updateEditMessage() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Update.TL_updateEditMessage::class,
          org.Tajgram.tgnet.TLRPC.Update::TLdeserialize, null)

    }

    @Test
    public fun test_001071_Update_TL_updateEmojiGameInfo() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Update.TL_updateEmojiGameInfo::class,
          org.Tajgram.tgnet.TLRPC.Update::TLdeserialize, null)

    }

    @Test
    public fun test_001072_Update_TL_updateEncryptedChatTyping() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Update.TL_updateEncryptedChatTyping::class,
          org.Tajgram.tgnet.TLRPC.Update::TLdeserialize, null)

    }

    @Test
    public fun test_001073_Update_TL_updateEncryptedMessagesRead() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Update.TL_updateEncryptedMessagesRead::class,
          org.Tajgram.tgnet.TLRPC.Update::TLdeserialize, null)

    }

    @Test
    public fun test_001074_Update_TL_updateEncryption() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Update.TL_updateEncryption::class,
          org.Tajgram.tgnet.TLRPC.Update::TLdeserialize, null)

    }

    @Test
    public fun test_001075_Update_TL_updateFavedStickers() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Update.TL_updateFavedStickers::class,
          org.Tajgram.tgnet.TLRPC.Update::TLdeserialize, null)

    }

    @Test
    public fun test_001076_Update_TL_updateFolderPeers() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Update.TL_updateFolderPeers::class,
          org.Tajgram.tgnet.TLRPC.Update::TLdeserialize, null)

    }

    @Test
    public fun test_001077_Update_TL_updateGeoLiveViewed() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Update.TL_updateGeoLiveViewed::class,
          org.Tajgram.tgnet.TLRPC.Update::TLdeserialize, null)

    }

    @Test
    public fun test_001078_Update_TL_updateGroupCall() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Update.TL_updateGroupCall::class,
          org.Tajgram.tgnet.TLRPC.Update::TLdeserialize, null)

    }

    @Test
    public fun test_001079_Update_TL_updateGroupCallChainBlocks() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Update.TL_updateGroupCallChainBlocks::class,
          org.Tajgram.tgnet.TLRPC.Update::TLdeserialize, null)

    }

    @Test
    public fun test_001080_Update_TL_updateGroupCallConnection() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Update.TL_updateGroupCallConnection::class,
          org.Tajgram.tgnet.TLRPC.Update::TLdeserialize, null)

    }

    @Test
    public fun test_001081_Update_TL_updateGroupCallEncryptedMessage() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Update.TL_updateGroupCallEncryptedMessage::class,
          org.Tajgram.tgnet.TLRPC.Update::TLdeserialize, null)

    }

    @Test
    public fun test_001082_Update_TL_updateGroupCallMessage() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Update.TL_updateGroupCallMessage::class,
          org.Tajgram.tgnet.TLRPC.Update::TLdeserialize, null)

    }

    @Test
    public fun test_001083_Update_TL_updateGroupCallParticipants() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Update.TL_updateGroupCallParticipants::class,
          org.Tajgram.tgnet.TLRPC.Update::TLdeserialize, null)

    }

    @Test
    public fun test_001084_Update_TL_updateJoinChatWebViewDecision() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Update.TL_updateJoinChatWebViewDecision::class,
          org.Tajgram.tgnet.TLRPC.Update::TLdeserialize, null)

    }

    @Test
    public fun test_001085_Update_TL_updateLangPack() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Update.TL_updateLangPack::class,
          org.Tajgram.tgnet.TLRPC.Update::TLdeserialize, null)

    }

    @Test
    public fun test_001086_Update_TL_updateLangPackTooLong() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Update.TL_updateLangPackTooLong::class,
          org.Tajgram.tgnet.TLRPC.Update::TLdeserialize, null)

    }

    @Test
    public fun test_001087_Update_TL_updateLoginToken() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Update.TL_updateLoginToken::class,
          org.Tajgram.tgnet.TLRPC.Update::TLdeserialize, null)

    }

    @Test
    public fun test_001088_Update_TL_updateManagedBot() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Update.TL_updateManagedBot::class,
          org.Tajgram.tgnet.TLRPC.Update::TLdeserialize, null)

    }

    @Test
    public fun test_001089_Update_TL_updateMessageExtendedMedia() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Update.TL_updateMessageExtendedMedia::class,
          org.Tajgram.tgnet.TLRPC.Update::TLdeserialize, null)

    }

    @Test
    public fun test_001090_Update_TL_updateMessageID() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Update.TL_updateMessageID::class,
          org.Tajgram.tgnet.TLRPC.Update::TLdeserialize, null)

    }

    @Test
    public fun test_001091_Update_TL_updateMessagePoll() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Update.TL_updateMessagePoll::class,
          org.Tajgram.tgnet.TLRPC.Update::TLdeserialize, null)

    }

    @Test
    public fun test_001092_Update_TL_updateMessagePollVote() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Update.TL_updateMessagePollVote::class,
          org.Tajgram.tgnet.TLRPC.Update::TLdeserialize, null)

    }

    @Test
    public fun test_001093_Update_TL_updateMessageReactions() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Update.TL_updateMessageReactions::class,
          org.Tajgram.tgnet.TLRPC.Update::TLdeserialize, null)

    }

    @Test
    public fun test_001094_Update_TL_updateMonoForumNoPaidException() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Update.TL_updateMonoForumNoPaidException::class,
          org.Tajgram.tgnet.TLRPC.Update::TLdeserialize, null)

    }

    @Test
    public fun test_001095_Update_TL_updateMoveStickerSetToTop() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Update.TL_updateMoveStickerSetToTop::class,
          org.Tajgram.tgnet.TLRPC.Update::TLdeserialize, null)

    }

    @Test
    public fun test_001096_Update_TL_updateNewAuthorization() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Update.TL_updateNewAuthorization::class,
          org.Tajgram.tgnet.TLRPC.Update::TLdeserialize, null)

    }

    @Test
    public fun test_001097_Update_TL_updateNewBotConnection() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Update.TL_updateNewBotConnection::class,
          org.Tajgram.tgnet.TLRPC.Update::TLdeserialize, null)

    }

    @Test
    public fun test_001098_Update_TL_updateNewChannelMessage() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Update.TL_updateNewChannelMessage::class,
          org.Tajgram.tgnet.TLRPC.Update::TLdeserialize, null)

    }

    @Test
    public fun test_001099_Update_TL_updateNewEncryptedMessage() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Update.TL_updateNewEncryptedMessage::class,
          org.Tajgram.tgnet.TLRPC.Update::TLdeserialize, null)

    }

    @Test
    public fun test_001100_Update_TL_updateNewMessage() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Update.TL_updateNewMessage::class,
          org.Tajgram.tgnet.TLRPC.Update::TLdeserialize, null)

    }

    @Test
    public fun test_001101_Update_TL_updateNewQuickReply() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Update.TL_updateNewQuickReply::class,
          org.Tajgram.tgnet.TLRPC.Update::TLdeserialize, null)

    }

    @Test
    public fun test_001102_Update_TL_updateNewScheduledMessage() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Update.TL_updateNewScheduledMessage::class,
          org.Tajgram.tgnet.TLRPC.Update::TLdeserialize, null)

    }

    @Test
    public fun test_001103_Update_TL_updateNewStickerSet() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Update.TL_updateNewStickerSet::class,
          org.Tajgram.tgnet.TLRPC.Update::TLdeserialize, null)

    }

    @Test
    public fun test_001104_Update_TL_updateNewStoryReaction() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Update.TL_updateNewStoryReaction::class,
          org.Tajgram.tgnet.TLRPC.Update::TLdeserialize, null)

    }

    @Test
    public fun test_001105_Update_TL_updateNotifySettings() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Update.TL_updateNotifySettings::class,
          org.Tajgram.tgnet.TLRPC.Update::TLdeserialize, null)

    }

    @Test
    public fun test_001106_Update_TL_updatePaidReactionPrivacy() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Update.TL_updatePaidReactionPrivacy::class,
          org.Tajgram.tgnet.TLRPC.Update::TLdeserialize, null)

    }

    @Test
    public fun test_001107_Update_TL_updatePeerBlocked() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Update.TL_updatePeerBlocked::class,
          org.Tajgram.tgnet.TLRPC.Update::TLdeserialize, null)

    }

    @Test
    public fun test_001108_Update_TL_updatePeerHistoryTTL() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Update.TL_updatePeerHistoryTTL::class,
          org.Tajgram.tgnet.TLRPC.Update::TLdeserialize, null)

    }

    @Test
    public fun test_001109_Update_TL_updatePeerLocated() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Update.TL_updatePeerLocated::class,
          org.Tajgram.tgnet.TLRPC.Update::TLdeserialize, null)

    }

    @Test
    public fun test_001110_Update_TL_updatePeerSettings() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Update.TL_updatePeerSettings::class,
          org.Tajgram.tgnet.TLRPC.Update::TLdeserialize, null)

    }

    @Test
    public fun test_001111_Update_TL_updatePeerWallpaper() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Update.TL_updatePeerWallpaper::class,
          org.Tajgram.tgnet.TLRPC.Update::TLdeserialize, null)

    }

    @Test
    public fun test_001112_Update_TL_updatePendingJoinRequests() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Update.TL_updatePendingJoinRequests::class,
          org.Tajgram.tgnet.TLRPC.Update::TLdeserialize, null)

    }

    @Test
    public fun test_001113_Update_TL_updatePhoneCall() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Update.TL_updatePhoneCall::class,
          org.Tajgram.tgnet.TLRPC.Update::TLdeserialize, null)

    }

    @Test
    public fun test_001114_Update_TL_updatePhoneCallSignalingData() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Update.TL_updatePhoneCallSignalingData::class,
          org.Tajgram.tgnet.TLRPC.Update::TLdeserialize, null)

    }

    @Test
    public fun test_001115_Update_TL_updatePinnedChannelMessages() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Update.TL_updatePinnedChannelMessages::class,
          org.Tajgram.tgnet.TLRPC.Update::TLdeserialize, null)

    }

    @Test
    public fun test_001116_Update_TL_updatePinnedDialogs() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Update.TL_updatePinnedDialogs::class,
          org.Tajgram.tgnet.TLRPC.Update::TLdeserialize, null)

    }

    @Test
    public fun test_001117_Update_TL_updatePinnedForumTopic() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Update.TL_updatePinnedForumTopic::class,
          org.Tajgram.tgnet.TLRPC.Update::TLdeserialize, null)

    }

    @Test
    public fun test_001118_Update_TL_updatePinnedForumTopics() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Update.TL_updatePinnedForumTopics::class,
          org.Tajgram.tgnet.TLRPC.Update::TLdeserialize, null)

    }

    @Test
    public fun test_001119_Update_TL_updatePinnedMessages() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Update.TL_updatePinnedMessages::class,
          org.Tajgram.tgnet.TLRPC.Update::TLdeserialize, null)

    }

    @Test
    public fun test_001120_Update_TL_updatePinnedSavedDialogs() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Update.TL_updatePinnedSavedDialogs::class,
          org.Tajgram.tgnet.TLRPC.Update::TLdeserialize, null)

    }

    @Test
    public fun test_001121_Update_TL_updatePrivacy() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Update.TL_updatePrivacy::class,
          org.Tajgram.tgnet.TLRPC.Update::TLdeserialize, null)

    }

    @Test
    public fun test_001122_Update_TL_updateQuickReplies() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Update.TL_updateQuickReplies::class,
          org.Tajgram.tgnet.TLRPC.Update::TLdeserialize, null)

    }

    @Test
    public fun test_001123_Update_TL_updateQuickReplyMessage() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Update.TL_updateQuickReplyMessage::class,
          org.Tajgram.tgnet.TLRPC.Update::TLdeserialize, null)

    }

    @Test
    public fun test_001124_Update_TL_updateReadChannelDiscussionInbox() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Update.TL_updateReadChannelDiscussionInbox::class,
          org.Tajgram.tgnet.TLRPC.Update::TLdeserialize, null)

    }

    @Test
    public fun test_001125_Update_TL_updateReadChannelDiscussionOutbox() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Update.TL_updateReadChannelDiscussionOutbox::class,
          org.Tajgram.tgnet.TLRPC.Update::TLdeserialize, null)

    }

    @Test
    public fun test_001126_Update_TL_updateReadChannelInbox() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Update.TL_updateReadChannelInbox::class,
          org.Tajgram.tgnet.TLRPC.Update::TLdeserialize, null)

    }

    @Test
    public fun test_001127_Update_TL_updateReadChannelOutbox() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Update.TL_updateReadChannelOutbox::class,
          org.Tajgram.tgnet.TLRPC.Update::TLdeserialize, null)

    }

    @Test
    public fun test_001128_Update_TL_updateReadFeaturedEmojiStickers() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Update.TL_updateReadFeaturedEmojiStickers::class,
          org.Tajgram.tgnet.TLRPC.Update::TLdeserialize, null)

    }

    @Test
    public fun test_001129_Update_TL_updateReadFeaturedStickers() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Update.TL_updateReadFeaturedStickers::class,
          org.Tajgram.tgnet.TLRPC.Update::TLdeserialize, null)

    }

    @Test
    public fun test_001130_Update_TL_updateReadHistoryInbox() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Update.TL_updateReadHistoryInbox::class,
          org.Tajgram.tgnet.TLRPC.Update::TLdeserialize, null)

    }

    @Test
    public fun test_001131_Update_TL_updateReadHistoryOutbox() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Update.TL_updateReadHistoryOutbox::class,
          org.Tajgram.tgnet.TLRPC.Update::TLdeserialize, null)

    }

    @Test
    public fun test_001132_Update_TL_updateReadMessagesContents() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Update.TL_updateReadMessagesContents::class,
          org.Tajgram.tgnet.TLRPC.Update::TLdeserialize, null)

    }

    @Test
    public fun test_001133_Update_TL_updateReadMonoForumInbox() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Update.TL_updateReadMonoForumInbox::class,
          org.Tajgram.tgnet.TLRPC.Update::TLdeserialize, null)

    }

    @Test
    public fun test_001134_Update_TL_updateReadMonoForumOutbox() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Update.TL_updateReadMonoForumOutbox::class,
          org.Tajgram.tgnet.TLRPC.Update::TLdeserialize, null)

    }

    @Test
    public fun test_001135_Update_TL_updateReadStories() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Update.TL_updateReadStories::class,
          org.Tajgram.tgnet.TLRPC.Update::TLdeserialize, null)

    }

    @Test
    public fun test_001136_Update_TL_updateRecentEmojiStatuses() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Update.TL_updateRecentEmojiStatuses::class,
          org.Tajgram.tgnet.TLRPC.Update::TLdeserialize, null)

    }

    @Test
    public fun test_001137_Update_TL_updateRecentReactions() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Update.TL_updateRecentReactions::class,
          org.Tajgram.tgnet.TLRPC.Update::TLdeserialize, null)

    }

    @Test
    public fun test_001138_Update_TL_updateRecentStickers() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Update.TL_updateRecentStickers::class,
          org.Tajgram.tgnet.TLRPC.Update::TLdeserialize, null)

    }

    @Test
    public fun test_001139_Update_TL_updateSavedDialogPinned() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Update.TL_updateSavedDialogPinned::class,
          org.Tajgram.tgnet.TLRPC.Update::TLdeserialize, null)

    }

    @Test
    public fun test_001140_Update_TL_updateSavedGifs() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Update.TL_updateSavedGifs::class,
          org.Tajgram.tgnet.TLRPC.Update::TLdeserialize, null)

    }

    @Test
    public fun test_001141_Update_TL_updateSavedReactionTags() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Update.TL_updateSavedReactionTags::class,
          org.Tajgram.tgnet.TLRPC.Update::TLdeserialize, null)

    }

    @Test
    public fun test_001142_Update_TL_updateSavedRingtones() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Update.TL_updateSavedRingtones::class,
          org.Tajgram.tgnet.TLRPC.Update::TLdeserialize, null)

    }

    @Test
    public fun test_001143_Update_TL_updateSentPhoneCode() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Update.TL_updateSentPhoneCode::class,
          org.Tajgram.tgnet.TLRPC.Update::TLdeserialize, null)

    }

    @Test
    public fun test_001144_Update_TL_updateSentStoryReaction() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Update.TL_updateSentStoryReaction::class,
          org.Tajgram.tgnet.TLRPC.Update::TLdeserialize, null)

    }

    @Test
    public fun test_001145_Update_TL_updateServiceNotification() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Update.TL_updateServiceNotification::class,
          org.Tajgram.tgnet.TLRPC.Update::TLdeserialize, null)

    }

    @Test
    public fun test_001146_Update_TL_updateStarGiftAuctionState() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Update.TL_updateStarGiftAuctionState::class,
          org.Tajgram.tgnet.TLRPC.Update::TLdeserialize, null)

    }

    @Test
    public fun test_001147_Update_TL_updateStarGiftAuctionUserState() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Update.TL_updateStarGiftAuctionUserState::class,
          org.Tajgram.tgnet.TLRPC.Update::TLdeserialize, null)

    }

    @Test
    public fun test_001148_Update_TL_updateStarGiftCraftFail() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Update.TL_updateStarGiftCraftFail::class,
          org.Tajgram.tgnet.TLRPC.Update::TLdeserialize, null)

    }

    @Test
    public fun test_001149_Update_TL_updateStarsBalance() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Update.TL_updateStarsBalance::class,
          org.Tajgram.tgnet.TLRPC.Update::TLdeserialize, null)

    }

    @Test
    public fun test_001150_Update_TL_updateStarsRevenueStatus() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Update.TL_updateStarsRevenueStatus::class,
          org.Tajgram.tgnet.TLRPC.Update::TLdeserialize, null)

    }

    @Test
    public fun test_001151_Update_TL_updateStickerSets() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Update.TL_updateStickerSets::class,
          org.Tajgram.tgnet.TLRPC.Update::TLdeserialize, null)

    }

    @Test
    public fun test_001152_Update_TL_updateStickerSetsOrder() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Update.TL_updateStickerSetsOrder::class,
          org.Tajgram.tgnet.TLRPC.Update::TLdeserialize, null)

    }

    @Test
    public fun test_001153_Update_TL_updateStoriesStealthMode() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Update.TL_updateStoriesStealthMode::class,
          org.Tajgram.tgnet.TLRPC.Update::TLdeserialize, null)

    }

    @Test
    public fun test_001154_Update_TL_updateStory() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Update.TL_updateStory::class,
          org.Tajgram.tgnet.TLRPC.Update::TLdeserialize, null)

    }

    @Test
    public fun test_001155_Update_TL_updateStoryID() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Update.TL_updateStoryID::class,
          org.Tajgram.tgnet.TLRPC.Update::TLdeserialize, null)

    }

    @Test
    public fun test_001156_Update_TL_updateTheme() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Update.TL_updateTheme::class,
          org.Tajgram.tgnet.TLRPC.Update::TLdeserialize, null)

    }

    @Test
    public fun test_001157_Update_TL_updateTranscribedAudio() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Update.TL_updateTranscribedAudio::class,
          org.Tajgram.tgnet.TLRPC.Update::TLdeserialize, null)

    }

    @Test
    public fun test_001158_Update_TL_updateUser() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Update.TL_updateUser::class,
          org.Tajgram.tgnet.TLRPC.Update::TLdeserialize, null)

    }

    @Test
    public fun test_001159_Update_TL_updateUserEmojiStatus() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Update.TL_updateUserEmojiStatus::class,
          org.Tajgram.tgnet.TLRPC.Update::TLdeserialize, null)

    }

    @Test
    public fun test_001160_Update_TL_updateUserName() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Update.TL_updateUserName::class,
          org.Tajgram.tgnet.TLRPC.Update::TLdeserialize, null)

    }

    @Test
    public fun test_001161_Update_TL_updateUserPhone() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Update.TL_updateUserPhone::class,
          org.Tajgram.tgnet.TLRPC.Update::TLdeserialize, null)

    }

    @Test
    public fun test_001162_Update_TL_updateUserStatus() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Update.TL_updateUserStatus::class,
          org.Tajgram.tgnet.TLRPC.Update::TLdeserialize, null)

    }

    @Test
    public fun test_001163_Update_TL_updateUserTyping() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Update.TL_updateUserTyping::class,
          org.Tajgram.tgnet.TLRPC.Update::TLdeserialize, null)

    }

    @Test
    public fun test_001164_Update_TL_updateWebBrowserException() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Update.TL_updateWebBrowserException::class,
          org.Tajgram.tgnet.TLRPC.Update::TLdeserialize, null)

    }

    @Test
    public fun test_001165_Update_TL_updateWebBrowserSettings() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Update.TL_updateWebBrowserSettings::class,
          org.Tajgram.tgnet.TLRPC.Update::TLdeserialize, null)

    }

    @Test
    public fun test_001166_Update_TL_updateWebPage() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Update.TL_updateWebPage::class,
          org.Tajgram.tgnet.TLRPC.Update::TLdeserialize, null)

    }

    @Test
    public fun test_001167_Update_TL_updateWebViewResultSent() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Update.TL_updateWebViewResultSent::class,
          org.Tajgram.tgnet.TLRPC.Update::TLdeserialize, null)

    }

    @Test
    public fun test_001168_Updates_TL_updateShort() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Updates.TL_updateShort::class,
          org.Tajgram.tgnet.TLRPC.Updates::TLdeserialize, null)

    }

    @Test
    public fun test_001169_Updates_TL_updateShortChatMessage() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Updates.TL_updateShortChatMessage::class,
          org.Tajgram.tgnet.TLRPC.Updates::TLdeserialize, null)

    }

    @Test
    public fun test_001170_Updates_TL_updateShortMessage() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Updates.TL_updateShortMessage::class,
          org.Tajgram.tgnet.TLRPC.Updates::TLdeserialize, null)

    }

    @Test
    public fun test_001171_Updates_TL_updateShortSentMessage() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Updates.TL_updateShortSentMessage::class,
          org.Tajgram.tgnet.TLRPC.Updates::TLdeserialize, null)

    }

    @Test
    public fun test_001172_Updates_TL_updates() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Updates.TL_updates::class,
          org.Tajgram.tgnet.TLRPC.Updates::TLdeserialize, null)

    }

    @Test
    public fun test_001173_Updates_TL_updatesCombined() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Updates.TL_updatesCombined::class,
          org.Tajgram.tgnet.TLRPC.Updates::TLdeserialize, null)

    }

    @Test
    public fun test_001174_Updates_TL_updatesTooLong() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Updates.TL_updatesTooLong::class,
          org.Tajgram.tgnet.TLRPC.Updates::TLdeserialize, null)

    }

    @Test
    public fun test_001175_UrlAuthResult_TL_urlAuthResultAccepted() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_UrlAuthResult.TL_urlAuthResultAccepted::class,
          org.Tajgram.tgnet.TLRPC.UrlAuthResult::TLdeserialize, null)

    }

    @Test
    public fun test_001176_UrlAuthResult_TL_urlAuthResultDefault() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_UrlAuthResult.TL_urlAuthResultDefault::class,
          org.Tajgram.tgnet.TLRPC.UrlAuthResult::TLdeserialize, null)

    }

    @Test
    public fun test_001177_UrlAuthResult_TL_urlAuthResultRequest() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_UrlAuthResult.TL_urlAuthResultRequest::class,
          org.Tajgram.tgnet.TLRPC.UrlAuthResult::TLdeserialize, null)

    }

    @Test
    public fun test_001178_User_TL_user() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_User.TL_user::class,
          org.Tajgram.tgnet.TLRPC.User::TLdeserialize, null)

    }

    @Test
    public fun test_001179_User_TL_userEmpty() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_User.TL_userEmpty::class,
          org.Tajgram.tgnet.TLRPC.User::TLdeserialize, null)

    }

    @Test
    public fun test_001180_UserFull_TL_userFull() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_UserFull.TL_userFull::class,
          org.Tajgram.tgnet.TLRPC.UserFull::TLdeserialize, null)

    }

    @Test
    public fun test_001181_UserProfilePhoto_TL_userProfilePhoto() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_UserProfilePhoto.TL_userProfilePhoto::class,
          org.Tajgram.tgnet.TLRPC.UserProfilePhoto::TLdeserialize, null)

    }

    @Test
    public fun test_001182_UserProfilePhoto_TL_userProfilePhotoEmpty() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_UserProfilePhoto.TL_userProfilePhotoEmpty::class,
          org.Tajgram.tgnet.TLRPC.UserProfilePhoto::TLdeserialize, null)

    }

    @Test
    public fun test_001183_UserStatus_TL_userStatusEmpty() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_UserStatus.TL_userStatusEmpty::class,
          org.Tajgram.tgnet.TLRPC.UserStatus::TLdeserialize, null)

    }

    @Test
    public fun test_001184_UserStatus_TL_userStatusLastMonth() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_UserStatus.TL_userStatusLastMonth::class,
          org.Tajgram.tgnet.TLRPC.UserStatus::TLdeserialize, null)

    }

    @Test
    public fun test_001185_UserStatus_TL_userStatusLastWeek() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_UserStatus.TL_userStatusLastWeek::class,
          org.Tajgram.tgnet.TLRPC.UserStatus::TLdeserialize, null)

    }

    @Test
    public fun test_001186_UserStatus_TL_userStatusOffline() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_UserStatus.TL_userStatusOffline::class,
          org.Tajgram.tgnet.TLRPC.UserStatus::TLdeserialize, null)

    }

    @Test
    public fun test_001187_UserStatus_TL_userStatusOnline() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_UserStatus.TL_userStatusOnline::class,
          org.Tajgram.tgnet.TLRPC.UserStatus::TLdeserialize, null)

    }

    @Test
    public fun test_001188_UserStatus_TL_userStatusRecently() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_UserStatus.TL_userStatusRecently::class,
          org.Tajgram.tgnet.TLRPC.UserStatus::TLdeserialize, null)

    }

    @Test
    public fun test_001189_Username_TL_username() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Username.TL_username::class,
          org.Tajgram.tgnet.TLRPC.TL_username::TLdeserialize, null)

    }

    @Test
    public fun test_001190_VideoSize_TL_videoSize() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_VideoSize.TL_videoSize::class,
          org.Tajgram.tgnet.TLRPC.VideoSize::TLdeserialize, null)

    }

    @Test
    public fun test_001191_VideoSize_TL_videoSizeEmojiMarkup() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_VideoSize.TL_videoSizeEmojiMarkup::class,
          org.Tajgram.tgnet.TLRPC.VideoSize::TLdeserialize, null)

    }

    @Test
    public fun test_001192_VideoSize_TL_videoSizeStickerMarkup() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_VideoSize.TL_videoSizeStickerMarkup::class,
          org.Tajgram.tgnet.TLRPC.VideoSize::TLdeserialize, null)

    }

    @Test
    public fun test_001193_WallPaper_TL_wallPaper() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_WallPaper.TL_wallPaper::class,
          org.Tajgram.tgnet.TLRPC.WallPaper::TLdeserialize, null)

    }

    @Test
    public fun test_001194_WallPaper_TL_wallPaperNoFile() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_WallPaper.TL_wallPaperNoFile::class,
          org.Tajgram.tgnet.TLRPC.WallPaper::TLdeserialize, null)

    }

    @Test
    public fun test_001195_WallPaperSettings_TL_wallPaperSettings() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_WallPaperSettings.TL_wallPaperSettings::class,
          org.Tajgram.tgnet.TLRPC.WallPaperSettings::TLdeserialize, null)

    }

    @Test
    public fun test_001196_WebAuthorization_TL_webAuthorization() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_WebAuthorization.TL_webAuthorization::class,
          org.Tajgram.tgnet.TLRPC.TL_webAuthorization::TLdeserialize, null)

    }

    @Test
    public fun test_001197_WebDocument_TL_webDocument() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_WebDocument.TL_webDocument::class,
          org.Tajgram.tgnet.TLRPC.WebDocument::TLdeserialize, null)

    }

    @Test
    public fun test_001198_WebDocument_TL_webDocumentNoProxy() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_WebDocument.TL_webDocumentNoProxy::class,
          org.Tajgram.tgnet.TLRPC.WebDocument::TLdeserialize, null)

    }

    @Test
    public fun test_001199_WebDomainException_TL_webDomainException() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_WebDomainException.TL_webDomainException::class,
          org.Tajgram.tgnet.tl.TL_account.WebDomainException::TLdeserialize, null)

    }

    @Test
    public fun test_001200_WebPage_TL_webPage() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_WebPage.TL_webPage::class,
          org.Tajgram.tgnet.TLRPC.WebPage::TLdeserialize, null)

    }

    @Test
    public fun test_001201_WebPage_TL_webPageEmpty() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_WebPage.TL_webPageEmpty::class,
          org.Tajgram.tgnet.TLRPC.WebPage::TLdeserialize, null)

    }

    @Test
    public fun test_001202_WebPage_TL_webPageNotModified() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_WebPage.TL_webPageNotModified::class,
          org.Tajgram.tgnet.TLRPC.WebPage::TLdeserialize, null)

    }

    @Test
    public fun test_001203_WebPage_TL_webPagePending() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_WebPage.TL_webPagePending::class,
          org.Tajgram.tgnet.TLRPC.WebPage::TLdeserialize, null)

    }

    @Test
    public fun test_001204_WebPageAttribute_TL_webPageAttributeAiComposeTone() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_WebPageAttribute.TL_webPageAttributeAiComposeTone::class,
          org.Tajgram.tgnet.TLRPC.WebPageAttribute::TLdeserialize, null)

    }

    @Test
    public fun test_001205_WebPageAttribute_TL_webPageAttributeStarGiftAuction() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_WebPageAttribute.TL_webPageAttributeStarGiftAuction::class,
          org.Tajgram.tgnet.TLRPC.WebPageAttribute::TLdeserialize, null)

    }

    @Test
    public fun test_001206_WebPageAttribute_TL_webPageAttributeStarGiftCollection() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_WebPageAttribute.TL_webPageAttributeStarGiftCollection::class,
          org.Tajgram.tgnet.TLRPC.WebPageAttribute::TLdeserialize, null)

    }

    @Test
    public fun test_001207_WebPageAttribute_TL_webPageAttributeStickerSet() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_WebPageAttribute.TL_webPageAttributeStickerSet::class,
          org.Tajgram.tgnet.TLRPC.WebPageAttribute::TLdeserialize, null)

    }

    @Test
    public fun test_001208_WebPageAttribute_TL_webPageAttributeStory() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_WebPageAttribute.TL_webPageAttributeStory::class,
          org.Tajgram.tgnet.TLRPC.WebPageAttribute::TLdeserialize, null)

    }

    @Test
    public fun test_001209_WebPageAttribute_TL_webPageAttributeTheme() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_WebPageAttribute.TL_webPageAttributeTheme::class,
          org.Tajgram.tgnet.TLRPC.WebPageAttribute::TLdeserialize, null)

    }

    @Test
    public fun test_001210_WebPageAttribute_TL_webPageAttributeUniqueStarGift() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_WebPageAttribute.TL_webPageAttributeUniqueStarGift::class,
          org.Tajgram.tgnet.TLRPC.WebPageAttribute::TLdeserialize, null)

    }

    @Test
    public fun test_001211_WebViewMessageSent_TL_webViewMessageSent() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_WebViewMessageSent.TL_webViewMessageSent::class,
          org.Tajgram.tgnet.TLRPC.TL_webViewMessageSent::TLdeserialize, null)

    }

    @Test
    public fun test_001212_WebViewResult_TL_webViewResultUrl() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_WebViewResult.TL_webViewResultUrl::class,
          org.Tajgram.tgnet.TLRPC.TL_webViewResultUrl::TLdeserialize, null)

    }

    @Test
    public fun test_001213_account_AuthorizationForm_TL_account_authorizationForm() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_account_AuthorizationForm.TL_account_authorizationForm::class,
          org.Tajgram.tgnet.tl.TL_account.authorizationForm::TLdeserialize, null)

    }

    @Test
    public fun test_001214_account_Authorizations_TL_account_authorizations() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_account_Authorizations.TL_account_authorizations::class,
          org.Tajgram.tgnet.tl.TL_account.authorizations::TLdeserialize, null)

    }

    @Test
    public fun test_001215_account_AutoDownloadSettings_TL_account_autoDownloadSettings() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_account_AutoDownloadSettings.TL_account_autoDownloadSettings::class,
          org.Tajgram.tgnet.tl.TL_account.autoDownloadSettings::TLdeserialize, null)

    }

    @Test
    public fun test_001216_account_BusinessChatLinks_TL_account_businessChatLinks() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_account_BusinessChatLinks.TL_account_businessChatLinks::class,
          org.Tajgram.tgnet.tl.TL_account.businessChatLinks::TLdeserialize, null)

    }

    @Test
    public fun test_001217_account_ChatThemes_TL_account_chatThemes() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_account_ChatThemes.TL_account_chatThemes::class,
          org.Tajgram.tgnet.tl.TL_account.ChatThemes::TLdeserialize, null)

    }

    @Test
    public fun test_001218_account_ChatThemes_TL_account_chatThemesNotModified() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_account_ChatThemes.TL_account_chatThemesNotModified::class,
          org.Tajgram.tgnet.tl.TL_account.ChatThemes::TLdeserialize, null)

    }

    @Test
    public fun test_001219_account_ConnectedBots_TL_account_connectedBots() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_account_ConnectedBots.TL_account_connectedBots::class,
          org.Tajgram.tgnet.tl.TL_account.connectedBots::TLdeserialize, null)

    }

    @Test
    public fun test_001220_account_ContentSettings_TL_account_contentSettings() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_account_ContentSettings.TL_account_contentSettings::class,
          org.Tajgram.tgnet.tl.TL_account.contentSettings::TLdeserialize, null)

    }

    @Test
    public fun test_001221_account_EmailVerified_TL_account_emailVerified() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_account_EmailVerified.TL_account_emailVerified::class,
          org.Tajgram.tgnet.tl.TL_account.EmailVerified::TLdeserialize, null)

    }

    @Test
    public fun test_001222_account_EmailVerified_TL_account_emailVerifiedLogin() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_account_EmailVerified.TL_account_emailVerifiedLogin::class,
          org.Tajgram.tgnet.tl.TL_account.EmailVerified::TLdeserialize, null)

    }

    @Test
    public fun test_001223_account_EmojiStatuses_TL_account_emojiStatuses() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_account_EmojiStatuses.TL_account_emojiStatuses::class,
          org.Tajgram.tgnet.tl.TL_account.EmojiStatuses::TLdeserialize, null)

    }

    @Test
    public fun test_001224_account_EmojiStatuses_TL_account_emojiStatusesNotModified() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_account_EmojiStatuses.TL_account_emojiStatusesNotModified::class,
          org.Tajgram.tgnet.tl.TL_account.EmojiStatuses::TLdeserialize, null)

    }

    @Test
    public fun test_001225_account_PaidMessagesRevenue_TL_account_paidMessagesRevenue() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_account_PaidMessagesRevenue.TL_account_paidMessagesRevenue::class,
          org.Tajgram.tgnet.tl.TL_account.paidMessagesRevenue::TLdeserialize, null)

    }

    @Test
    public
        fun test_001226_account_PasskeyRegistrationOptions_TL_account_passkeyRegistrationOptions() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_account_PasskeyRegistrationOptions.TL_account_passkeyRegistrationOptions::class,
          org.Tajgram.tgnet.tl.TL_account.passkeyRegistrationOptions::TLdeserialize, null)

    }

    @Test
    public fun test_001227_account_Passkeys_TL_account_passkeys() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_account_Passkeys.TL_account_passkeys::class,
          org.Tajgram.tgnet.tl.TL_account.Passkeys::TLdeserialize, null)

    }

    @Test
    public fun test_001228_account_Password_TL_account_password() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_account_Password.TL_account_password::class,
          org.Tajgram.tgnet.tl.TL_account.Password::TLdeserialize, null)

    }

    @Test
    public fun test_001229_account_PasswordInputSettings_TL_account_passwordInputSettings() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_account_PasswordInputSettings.TL_account_passwordInputSettings::class,
          org.Tajgram.tgnet.tl.TL_account.passwordInputSettings::TLdeserialize, null)

    }

    @Test
    public fun test_001230_account_PasswordSettings_TL_account_passwordSettings() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_account_PasswordSettings.TL_account_passwordSettings::class,
          org.Tajgram.tgnet.tl.TL_account.passwordSettings::TLdeserialize, null)

    }

    @Test
    public fun test_001231_account_PrivacyRules_TL_account_privacyRules() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_account_PrivacyRules.TL_account_privacyRules::class,
          org.Tajgram.tgnet.tl.TL_account.privacyRules::TLdeserialize, null)

    }

    @Test
    public fun test_001232_account_ResetPasswordResult_TL_account_resetPasswordFailedWait() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_account_ResetPasswordResult.TL_account_resetPasswordFailedWait::class,
          org.Tajgram.tgnet.tl.TL_account.ResetPasswordResult::TLdeserialize, null)

    }

    @Test
    public fun test_001233_account_ResetPasswordResult_TL_account_resetPasswordOk() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_account_ResetPasswordResult.TL_account_resetPasswordOk::class,
          org.Tajgram.tgnet.tl.TL_account.ResetPasswordResult::TLdeserialize, null)

    }

    @Test
    public fun test_001234_account_ResetPasswordResult_TL_account_resetPasswordRequestedWait() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_account_ResetPasswordResult.TL_account_resetPasswordRequestedWait::class,
          org.Tajgram.tgnet.tl.TL_account.ResetPasswordResult::TLdeserialize, null)

    }

    @Test
    public
        fun test_001235_account_ResolvedBusinessChatLinks_TL_account_resolvedBusinessChatLinks() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_account_ResolvedBusinessChatLinks.TL_account_resolvedBusinessChatLinks::class,
          org.Tajgram.tgnet.tl.TL_account.resolvedBusinessChatLinks::TLdeserialize, null)

    }

    @Test
    public fun test_001236_account_SavedMusicIds_TL_account_savedMusicIds() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_account_SavedMusicIds.TL_account_savedMusicIds::class,
          org.Tajgram.tgnet.tl.TL_account.SavedMusicIds::TLdeserialize, null)

    }

    @Test
    public fun test_001237_account_SavedMusicIds_TL_account_savedMusicIdsNotModified() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_account_SavedMusicIds.TL_account_savedMusicIdsNotModified::class,
          org.Tajgram.tgnet.tl.TL_account.SavedMusicIds::TLdeserialize, null)

    }

    @Test
    public fun test_001238_account_SavedRingtone_TL_account_savedRingtone() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_account_SavedRingtone.TL_account_savedRingtone::class,
          org.Tajgram.tgnet.tl.TL_account.SavedRingtone::TLdeserialize, null)

    }

    @Test
    public fun test_001239_account_SavedRingtone_TL_account_savedRingtoneConverted() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_account_SavedRingtone.TL_account_savedRingtoneConverted::class,
          org.Tajgram.tgnet.tl.TL_account.SavedRingtone::TLdeserialize, null)

    }

    @Test
    public fun test_001240_account_SavedRingtones_TL_account_savedRingtones() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_account_SavedRingtones.TL_account_savedRingtones::class,
          org.Tajgram.tgnet.tl.TL_account.SavedRingtones::TLdeserialize, null)

    }

    @Test
    public fun test_001241_account_SavedRingtones_TL_account_savedRingtonesNotModified() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_account_SavedRingtones.TL_account_savedRingtonesNotModified::class,
          org.Tajgram.tgnet.tl.TL_account.SavedRingtones::TLdeserialize, null)

    }

    @Test
    public fun test_001242_account_SentEmailCode_TL_account_sentEmailCode() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_account_SentEmailCode.TL_account_sentEmailCode::class,
          org.Tajgram.tgnet.tl.TL_account.sentEmailCode::TLdeserialize, null)

    }

    @Test
    public fun test_001243_account_Themes_TL_account_themes() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_account_Themes.TL_account_themes::class,
          org.Tajgram.tgnet.tl.TL_account.Themes::TLdeserialize, null)

    }

    @Test
    public fun test_001244_account_Themes_TL_account_themesNotModified() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_account_Themes.TL_account_themesNotModified::class,
          org.Tajgram.tgnet.tl.TL_account.Themes::TLdeserialize, null)

    }

    @Test
    public fun test_001245_account_TmpPassword_TL_account_tmpPassword() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_account_TmpPassword.TL_account_tmpPassword::class,
          org.Tajgram.tgnet.tl.TL_account.tmpPassword::TLdeserialize, null)

    }

    @Test
    public fun test_001246_account_WallPapers_TL_account_wallPapers() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_account_WallPapers.TL_account_wallPapers::class,
          org.Tajgram.tgnet.tl.TL_account.WallPapers::TLdeserialize, null)

    }

    @Test
    public fun test_001247_account_WallPapers_TL_account_wallPapersNotModified() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_account_WallPapers.TL_account_wallPapersNotModified::class,
          org.Tajgram.tgnet.tl.TL_account.WallPapers::TLdeserialize, null)

    }

    @Test
    public fun test_001248_account_WebAuthorizations_TL_account_webAuthorizations() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_account_WebAuthorizations.TL_account_webAuthorizations::class,
          org.Tajgram.tgnet.tl.TL_account.webAuthorizations::TLdeserialize, null)

    }

    @Test
    public fun test_001249_account_WebBrowserSettings_TL_account_webBrowserSettings() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_account_WebBrowserSettings.TL_account_webBrowserSettings::class,
          org.Tajgram.tgnet.tl.TL_account.WebBrowserSettings::TLdeserialize, null)

    }

    @Test
    public fun test_001250_account_WebBrowserSettings_TL_account_webBrowserSettingsNotModified() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_account_WebBrowserSettings.TL_account_webBrowserSettingsNotModified::class,
          org.Tajgram.tgnet.tl.TL_account.WebBrowserSettings::TLdeserialize, null)

    }

    @Test
    public fun test_001251_aicompose_Tones_TL_aicompose_tones() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_aicompose_Tones.TL_aicompose_tones::class,
          org.Tajgram.tgnet.tl.TL_aicompose.Tones::TLdeserialize, null)

    }

    @Test
    public fun test_001252_aicompose_Tones_TL_aicompose_tonesNotModified() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_aicompose_Tones.TL_aicompose_tonesNotModified::class,
          org.Tajgram.tgnet.tl.TL_aicompose.Tones::TLdeserialize, null)

    }

    @Test
    public fun test_001253_auth_Authorization_TL_auth_authorization() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_auth_Authorization.TL_auth_authorization::class,
          org.Tajgram.tgnet.TLRPC.auth_Authorization::TLdeserialize, null)

    }

    @Test
    public fun test_001254_auth_Authorization_TL_auth_authorizationSignUpRequired() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_auth_Authorization.TL_auth_authorizationSignUpRequired::class,
          org.Tajgram.tgnet.TLRPC.auth_Authorization::TLdeserialize, null)

    }

    @Test
    public fun test_001255_auth_CodeType_TL_auth_codeTypeCall() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_auth_CodeType.TL_auth_codeTypeCall::class,
          org.Tajgram.tgnet.TLRPC.auth_CodeType::TLdeserialize, null)

    }

    @Test
    public fun test_001256_auth_CodeType_TL_auth_codeTypeFlashCall() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_auth_CodeType.TL_auth_codeTypeFlashCall::class,
          org.Tajgram.tgnet.TLRPC.auth_CodeType::TLdeserialize, null)

    }

    @Test
    public fun test_001257_auth_CodeType_TL_auth_codeTypeFragmentSms() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_auth_CodeType.TL_auth_codeTypeFragmentSms::class,
          org.Tajgram.tgnet.TLRPC.auth_CodeType::TLdeserialize, null)

    }

    @Test
    public fun test_001258_auth_CodeType_TL_auth_codeTypeMissedCall() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_auth_CodeType.TL_auth_codeTypeMissedCall::class,
          org.Tajgram.tgnet.TLRPC.auth_CodeType::TLdeserialize, null)

    }

    @Test
    public fun test_001259_auth_CodeType_TL_auth_codeTypeSms() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_auth_CodeType.TL_auth_codeTypeSms::class,
          org.Tajgram.tgnet.TLRPC.auth_CodeType::TLdeserialize, null)

    }

    @Test
    public fun test_001260_auth_ExportedAuthorization_TL_auth_exportedAuthorization() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_auth_ExportedAuthorization.TL_auth_exportedAuthorization::class,
          org.Tajgram.tgnet.TLRPC.TL_auth_exportedAuthorization::TLdeserialize, null)

    }

    @Test
    public fun test_001261_auth_LoggedOut_TL_auth_loggedOut() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_auth_LoggedOut.TL_auth_loggedOut::class,
          org.Tajgram.tgnet.TLRPC.TL_auth_loggedOut::TLdeserialize, null)

    }

    @Test
    public fun test_001262_auth_LoginToken_TL_auth_loginToken() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_auth_LoginToken.TL_auth_loginToken::class,
          org.Tajgram.tgnet.TLRPC.auth_LoginToken::TLdeserialize, null)

    }

    @Test
    public fun test_001263_auth_LoginToken_TL_auth_loginTokenMigrateTo() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_auth_LoginToken.TL_auth_loginTokenMigrateTo::class,
          org.Tajgram.tgnet.TLRPC.auth_LoginToken::TLdeserialize, null)

    }

    @Test
    public fun test_001264_auth_LoginToken_TL_auth_loginTokenSuccess() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_auth_LoginToken.TL_auth_loginTokenSuccess::class,
          org.Tajgram.tgnet.TLRPC.auth_LoginToken::TLdeserialize, null)

    }

    @Test
    public fun test_001265_auth_PasskeyLoginOptions_TL_auth_passkeyLoginOptions() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_auth_PasskeyLoginOptions.TL_auth_passkeyLoginOptions::class,
          org.Tajgram.tgnet.tl.TL_account.passkeyLoginOptions::TLdeserialize, null)

    }

    @Test
    public fun test_001266_auth_PasswordRecovery_TL_auth_passwordRecovery() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_auth_PasswordRecovery.TL_auth_passwordRecovery::class,
          org.Tajgram.tgnet.TLRPC.TL_auth_passwordRecovery::TLdeserialize, null)

    }

    @Test
    public fun test_001267_auth_SentCode_TL_auth_sentCode() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_auth_SentCode.TL_auth_sentCode::class,
          org.Tajgram.tgnet.TLRPC.auth_SentCode::TLdeserialize, null)

    }

    @Test
    public fun test_001268_auth_SentCode_TL_auth_sentCodePaymentRequired() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_auth_SentCode.TL_auth_sentCodePaymentRequired::class,
          org.Tajgram.tgnet.TLRPC.auth_SentCode::TLdeserialize, null)

    }

    @Test
    public fun test_001269_auth_SentCode_TL_auth_sentCodeSuccess() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_auth_SentCode.TL_auth_sentCodeSuccess::class,
          org.Tajgram.tgnet.TLRPC.auth_SentCode::TLdeserialize, null)

    }

    @Test
    public fun test_001270_auth_SentCodeType_TL_auth_sentCodeTypeApp() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_auth_SentCodeType.TL_auth_sentCodeTypeApp::class,
          org.Tajgram.tgnet.TLRPC.auth_SentCodeType::TLdeserialize, null)

    }

    @Test
    public fun test_001271_auth_SentCodeType_TL_auth_sentCodeTypeCall() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_auth_SentCodeType.TL_auth_sentCodeTypeCall::class,
          org.Tajgram.tgnet.TLRPC.auth_SentCodeType::TLdeserialize, null)

    }

    @Test
    public fun test_001272_auth_SentCodeType_TL_auth_sentCodeTypeEmailCode() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_auth_SentCodeType.TL_auth_sentCodeTypeEmailCode::class,
          org.Tajgram.tgnet.TLRPC.auth_SentCodeType::TLdeserialize, null)

    }

    @Test
    public fun test_001273_auth_SentCodeType_TL_auth_sentCodeTypeFirebaseSms() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_auth_SentCodeType.TL_auth_sentCodeTypeFirebaseSms::class,
          org.Tajgram.tgnet.TLRPC.auth_SentCodeType::TLdeserialize, null)

    }

    @Test
    public fun test_001274_auth_SentCodeType_TL_auth_sentCodeTypeFlashCall() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_auth_SentCodeType.TL_auth_sentCodeTypeFlashCall::class,
          org.Tajgram.tgnet.TLRPC.auth_SentCodeType::TLdeserialize, null)

    }

    @Test
    public fun test_001275_auth_SentCodeType_TL_auth_sentCodeTypeFragmentSms() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_auth_SentCodeType.TL_auth_sentCodeTypeFragmentSms::class,
          org.Tajgram.tgnet.TLRPC.auth_SentCodeType::TLdeserialize, null)

    }

    @Test
    public fun test_001276_auth_SentCodeType_TL_auth_sentCodeTypeMissedCall() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_auth_SentCodeType.TL_auth_sentCodeTypeMissedCall::class,
          org.Tajgram.tgnet.TLRPC.auth_SentCodeType::TLdeserialize, null)

    }

    @Test
    public fun test_001277_auth_SentCodeType_TL_auth_sentCodeTypeSetUpEmailRequired() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_auth_SentCodeType.TL_auth_sentCodeTypeSetUpEmailRequired::class,
          org.Tajgram.tgnet.TLRPC.auth_SentCodeType::TLdeserialize, null)

    }

    @Test
    public fun test_001278_auth_SentCodeType_TL_auth_sentCodeTypeSms() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_auth_SentCodeType.TL_auth_sentCodeTypeSms::class,
          org.Tajgram.tgnet.TLRPC.auth_SentCodeType::TLdeserialize, null)

    }

    @Test
    public fun test_001279_auth_SentCodeType_TL_auth_sentCodeTypeSmsPhrase() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_auth_SentCodeType.TL_auth_sentCodeTypeSmsPhrase::class,
          org.Tajgram.tgnet.TLRPC.auth_SentCodeType::TLdeserialize, null)

    }

    @Test
    public fun test_001280_auth_SentCodeType_TL_auth_sentCodeTypeSmsWord() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_auth_SentCodeType.TL_auth_sentCodeTypeSmsWord::class,
          org.Tajgram.tgnet.TLRPC.auth_SentCodeType::TLdeserialize, null)

    }

    @Test
    public fun test_001281_bots_ExportedBotToken_TL_bots_exportedBotToken() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_bots_ExportedBotToken.TL_bots_exportedBotToken::class,
          org.Tajgram.tgnet.tl.TL_bots.exportedBotToken::TLdeserialize, null)

    }

    @Test
    public fun test_001282_bots_PopularAppBots_TL_bots_popularAppBots() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_bots_PopularAppBots.TL_bots_popularAppBots::class,
          org.Tajgram.tgnet.tl.TL_bots.popularAppBots::TLdeserialize, null)

    }

    @Test
    public fun test_001283_bots_PreviewInfo_TL_bots_previewInfo() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_bots_PreviewInfo.TL_bots_previewInfo::class,
          org.Tajgram.tgnet.tl.TL_bots.previewInfo::TLdeserialize, null)

    }

    @Test
    public fun test_001284_bots_RequestedButton_TL_bots_requestedButton() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_bots_RequestedButton.TL_bots_requestedButton::class,
          org.Tajgram.tgnet.tl.TL_bots.requestedButton::TLdeserialize, null)

    }

    @Test
    public fun test_001285_channels_AdminLogResults_TL_channels_adminLogResults() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_channels_AdminLogResults.TL_channels_adminLogResults::class,
          org.Tajgram.tgnet.TLRPC.TL_channels_adminLogResults::TLdeserialize, null)

    }

    @Test
    public fun test_001286_channels_ChannelParticipant_TL_channels_channelParticipant() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_channels_ChannelParticipant.TL_channels_channelParticipant::class,
          org.Tajgram.tgnet.TLRPC.TL_channels_channelParticipant::TLdeserialize, null)

    }

    @Test
    public fun test_001287_channels_ChannelParticipants_TL_channels_channelParticipants() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_channels_ChannelParticipants.TL_channels_channelParticipants::class,
          org.Tajgram.tgnet.TLRPC.channels_ChannelParticipants::TLdeserialize, null)

    }

    @Test
    public
        fun test_001288_channels_ChannelParticipants_TL_channels_channelParticipantsNotModified() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_channels_ChannelParticipants.TL_channels_channelParticipantsNotModified::class,
          org.Tajgram.tgnet.TLRPC.channels_ChannelParticipants::TLdeserialize, null)

    }

    @Test
    public fun test_001289_channels_SendAsPeers_TL_channels_sendAsPeers() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_channels_SendAsPeers.TL_channels_sendAsPeers::class,
          org.Tajgram.tgnet.TLRPC.TL_channels_sendAsPeers::TLdeserialize, null)

    }

    @Test
    public
        fun test_001290_channels_SponsoredMessageReportResult_TL_channels_sponsoredMessageReportResultAdsHidden() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_channels_SponsoredMessageReportResult.TL_channels_sponsoredMessageReportResultAdsHidden::class,
          org.Tajgram.tgnet.TLRPC.channels_SponsoredMessageReportResult::TLdeserialize, null)

    }

    @Test
    public
        fun test_001291_channels_SponsoredMessageReportResult_TL_channels_sponsoredMessageReportResultChooseOption() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_channels_SponsoredMessageReportResult.TL_channels_sponsoredMessageReportResultChooseOption::class,
          org.Tajgram.tgnet.TLRPC.channels_SponsoredMessageReportResult::TLdeserialize, null)

    }

    @Test
    public
        fun test_001292_channels_SponsoredMessageReportResult_TL_channels_sponsoredMessageReportResultReported() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_channels_SponsoredMessageReportResult.TL_channels_sponsoredMessageReportResultReported::class,
          org.Tajgram.tgnet.TLRPC.channels_SponsoredMessageReportResult::TLdeserialize, null)

    }

    @Test
    public fun test_001293_chatlists_ChatlistInvite_TL_chatlists_chatlistInvite() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_chatlists_ChatlistInvite.TL_chatlists_chatlistInvite::class,
          org.Tajgram.tgnet.tl.TL_chatlists.chatlist_ChatlistInvite::TLdeserialize, null)

    }

    @Test
    public fun test_001294_chatlists_ChatlistInvite_TL_chatlists_chatlistInviteAlready() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_chatlists_ChatlistInvite.TL_chatlists_chatlistInviteAlready::class,
          org.Tajgram.tgnet.tl.TL_chatlists.chatlist_ChatlistInvite::TLdeserialize, null)

    }

    @Test
    public fun test_001295_chatlists_ChatlistUpdates_TL_chatlists_chatlistUpdates() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_chatlists_ChatlistUpdates.TL_chatlists_chatlistUpdates::class,
          org.Tajgram.tgnet.tl.TL_chatlists.TL_chatlists_chatlistUpdates::TLdeserialize, null)

    }

    @Test
    public fun test_001296_chatlists_ExportedChatlistInvite_TL_chatlists_exportedChatlistInvite() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_chatlists_ExportedChatlistInvite.TL_chatlists_exportedChatlistInvite::class,
          org.Tajgram.tgnet.tl.TL_chatlists.TL_chatlists_exportedChatlistInvite::TLdeserialize,
          null)

    }

    @Test
    public fun test_001297_chatlists_ExportedInvites_TL_chatlists_exportedInvites() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_chatlists_ExportedInvites.TL_chatlists_exportedInvites::class,
          org.Tajgram.tgnet.tl.TL_chatlists.TL_chatlists_exportedInvites::TLdeserialize, null)

    }

    @Test
    public fun test_001298_contacts_Blocked_TL_contacts_blocked() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_contacts_Blocked.TL_contacts_blocked::class,
          org.Tajgram.tgnet.TLRPC.contacts_Blocked::TLdeserialize, null)

    }

    @Test
    public fun test_001299_contacts_Blocked_TL_contacts_blockedSlice() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_contacts_Blocked.TL_contacts_blockedSlice::class,
          org.Tajgram.tgnet.TLRPC.contacts_Blocked::TLdeserialize, null)

    }

    @Test
    public fun test_001300_contacts_ContactBirthdays_TL_contacts_contactBirthdays() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_contacts_ContactBirthdays.TL_contacts_contactBirthdays::class,
          org.Tajgram.tgnet.tl.TL_account.contactBirthdays::TLdeserialize, null)

    }

    @Test
    public fun test_001301_contacts_Contacts_TL_contacts_contacts() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_contacts_Contacts.TL_contacts_contacts::class,
          org.Tajgram.tgnet.TLRPC.contacts_Contacts::TLdeserialize, null)

    }

    @Test
    public fun test_001302_contacts_Contacts_TL_contacts_contactsNotModified() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_contacts_Contacts.TL_contacts_contactsNotModified::class,
          org.Tajgram.tgnet.TLRPC.contacts_Contacts::TLdeserialize, null)

    }

    @Test
    public fun test_001303_contacts_Found_TL_contacts_found() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_contacts_Found.TL_contacts_found::class,
          org.Tajgram.tgnet.TLRPC.TL_contacts_found::TLdeserialize, null)

    }

    @Test
    public fun test_001304_contacts_ImportedContacts_TL_contacts_importedContacts() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_contacts_ImportedContacts.TL_contacts_importedContacts::class,
          org.Tajgram.tgnet.TLRPC.TL_contacts_importedContacts::TLdeserialize, null)

    }

    @Test
    public fun test_001305_contacts_ResolvedPeer_TL_contacts_resolvedPeer() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_contacts_ResolvedPeer.TL_contacts_resolvedPeer::class,
          org.Tajgram.tgnet.TLRPC.TL_contacts_resolvedPeer::TLdeserialize, null)

    }

    @Test
    public fun test_001306_contacts_SponsoredPeers_TL_contacts_sponsoredPeers() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_contacts_SponsoredPeers.TL_contacts_sponsoredPeers::class,
          org.Tajgram.tgnet.TLRPC.contacts_SponsoredPeers::TLdeserialize, null)

    }

    @Test
    public fun test_001307_contacts_SponsoredPeers_TL_contacts_sponsoredPeersEmpty() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_contacts_SponsoredPeers.TL_contacts_sponsoredPeersEmpty::class,
          org.Tajgram.tgnet.TLRPC.contacts_SponsoredPeers::TLdeserialize, null)

    }

    @Test
    public fun test_001308_contacts_TopPeers_TL_contacts_topPeers() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_contacts_TopPeers.TL_contacts_topPeers::class,
          org.Tajgram.tgnet.TLRPC.contacts_TopPeers::TLdeserialize, null)

    }

    @Test
    public fun test_001309_contacts_TopPeers_TL_contacts_topPeersDisabled() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_contacts_TopPeers.TL_contacts_topPeersDisabled::class,
          org.Tajgram.tgnet.TLRPC.contacts_TopPeers::TLdeserialize, null)

    }

    @Test
    public fun test_001310_contacts_TopPeers_TL_contacts_topPeersNotModified() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_contacts_TopPeers.TL_contacts_topPeersNotModified::class,
          org.Tajgram.tgnet.TLRPC.contacts_TopPeers::TLdeserialize, null)

    }

    @Test
    public fun test_001311_fragment_CollectibleInfo_TL_fragment_collectibleInfo() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_fragment_CollectibleInfo.TL_fragment_collectibleInfo::class,
          org.Tajgram.tgnet.tl.TL_fragment.TL_collectibleInfo::TLdeserialize, null)

    }

    @Test
    public fun test_001312_help_AppConfig_TL_help_appConfig() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_help_AppConfig.TL_help_appConfig::class,
          org.Tajgram.tgnet.TLRPC.help_AppConfig::TLdeserialize, null)

    }

    @Test
    public fun test_001313_help_AppConfig_TL_help_appConfigNotModified() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_help_AppConfig.TL_help_appConfigNotModified::class,
          org.Tajgram.tgnet.TLRPC.help_AppConfig::TLdeserialize, null)

    }

    @Test
    public fun test_001314_help_AppUpdate_TL_help_appUpdate() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_help_AppUpdate.TL_help_appUpdate::class,
          org.Tajgram.tgnet.TLRPC.help_AppUpdate::TLdeserialize, null)

    }

    @Test
    public fun test_001315_help_AppUpdate_TL_help_noAppUpdate() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_help_AppUpdate.TL_help_noAppUpdate::class,
          org.Tajgram.tgnet.TLRPC.help_AppUpdate::TLdeserialize, null)

    }

    @Test
    public fun test_001316_help_CountriesList_TL_help_countriesList() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_help_CountriesList.TL_help_countriesList::class,
          org.Tajgram.tgnet.TLRPC.help_CountriesList::TLdeserialize, null)

    }

    @Test
    public fun test_001317_help_CountriesList_TL_help_countriesListNotModified() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_help_CountriesList.TL_help_countriesListNotModified::class,
          org.Tajgram.tgnet.TLRPC.help_CountriesList::TLdeserialize, null)

    }

    @Test
    public fun test_001318_help_Country_TL_help_country() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_help_Country.TL_help_country::class,
          org.Tajgram.tgnet.TLRPC.TL_help_country::TLdeserialize, null)

    }

    @Test
    public fun test_001319_help_CountryCode_TL_help_countryCode() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_help_CountryCode.TL_help_countryCode::class,
          org.Tajgram.tgnet.TLRPC.TL_help_countryCode::TLdeserialize, null)

    }

    @Test
    public fun test_001320_help_DeepLinkInfo_TL_help_deepLinkInfo() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_help_DeepLinkInfo.TL_help_deepLinkInfo::class,
          org.Tajgram.tgnet.TLRPC.help_DeepLinkInfo::TLdeserialize, null)

    }

    @Test
    public fun test_001321_help_DeepLinkInfo_TL_help_deepLinkInfoEmpty() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_help_DeepLinkInfo.TL_help_deepLinkInfoEmpty::class,
          org.Tajgram.tgnet.TLRPC.help_DeepLinkInfo::TLdeserialize, null)

    }

    @Test
    public fun test_001322_help_InviteText_TL_help_inviteText() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_help_InviteText.TL_help_inviteText::class,
          org.Tajgram.tgnet.TLRPC.TL_help_inviteText::TLdeserialize, null)

    }

    @Test
    public fun test_001323_help_PassportConfig_TL_help_passportConfig() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_help_PassportConfig.TL_help_passportConfig::class,
          org.Tajgram.tgnet.TLRPC.help_PassportConfig::TLdeserialize, null)

    }

    @Test
    public fun test_001324_help_PassportConfig_TL_help_passportConfigNotModified() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_help_PassportConfig.TL_help_passportConfigNotModified::class,
          org.Tajgram.tgnet.TLRPC.help_PassportConfig::TLdeserialize, null)

    }

    @Test
    public fun test_001325_help_PeerColorOption_TL_help_peerColorOption() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_help_PeerColorOption.TL_help_peerColorOption::class,
          org.Tajgram.tgnet.TLRPC.TL_help_peerColorOption::TLdeserialize, null)

    }

    @Test
    public fun test_001326_help_PeerColorSet_TL_help_peerColorProfileSet() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_help_PeerColorSet.TL_help_peerColorProfileSet::class,
          org.Tajgram.tgnet.TLRPC.help_PeerColorSet::TLdeserialize, null)

    }

    @Test
    public fun test_001327_help_PeerColorSet_TL_help_peerColorSet() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_help_PeerColorSet.TL_help_peerColorSet::class,
          org.Tajgram.tgnet.TLRPC.help_PeerColorSet::TLdeserialize, null)

    }

    @Test
    public fun test_001328_help_PeerColors_TL_help_peerColors() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_help_PeerColors.TL_help_peerColors::class,
          org.Tajgram.tgnet.TLRPC.help_PeerColors::TLdeserialize, null)

    }

    @Test
    public fun test_001329_help_PeerColors_TL_help_peerColorsNotModified() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_help_PeerColors.TL_help_peerColorsNotModified::class,
          org.Tajgram.tgnet.TLRPC.help_PeerColors::TLdeserialize, null)

    }

    @Test
    public fun test_001330_help_PremiumPromo_TL_help_premiumPromo() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_help_PremiumPromo.TL_help_premiumPromo::class,
          org.Tajgram.tgnet.TLRPC.TL_help_premiumPromo::TLdeserialize, null)

    }

    @Test
    public fun test_001331_help_PromoData_TL_help_promoData() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_help_PromoData.TL_help_promoData::class,
          org.Tajgram.tgnet.TLRPC.help_PromoData::TLdeserialize, null)

    }

    @Test
    public fun test_001332_help_PromoData_TL_help_promoDataEmpty() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_help_PromoData.TL_help_promoDataEmpty::class,
          org.Tajgram.tgnet.TLRPC.help_PromoData::TLdeserialize, null)

    }

    @Test
    public fun test_001333_help_RecentMeUrls_TL_help_recentMeUrls() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_help_RecentMeUrls.TL_help_recentMeUrls::class,
          org.Tajgram.tgnet.TLRPC.TL_help_recentMeUrls::TLdeserialize, null)

    }

    @Test
    public fun test_001334_help_Support_TL_help_support() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_help_Support.TL_help_support::class,
          org.Tajgram.tgnet.TLRPC.TL_help_support::TLdeserialize, null)

    }

    @Test
    public fun test_001335_help_SupportName_TL_help_supportName() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_help_SupportName.TL_help_supportName::class,
          org.Tajgram.tgnet.TLRPC.TL_help_supportName::TLdeserialize, null)

    }

    @Test
    public fun test_001336_help_TermsOfService_TL_help_termsOfService() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_help_TermsOfService.TL_help_termsOfService::class,
          org.Tajgram.tgnet.TLRPC.TL_help_termsOfService::TLdeserialize, null)

    }

    @Test
    public fun test_001337_help_TermsOfServiceUpdate_TL_help_termsOfServiceUpdate() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_help_TermsOfServiceUpdate.TL_help_termsOfServiceUpdate::class,
          org.Tajgram.tgnet.TLRPC.help_TermsOfServiceUpdate::TLdeserialize, null)

    }

    @Test
    public fun test_001338_help_TermsOfServiceUpdate_TL_help_termsOfServiceUpdateEmpty() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_help_TermsOfServiceUpdate.TL_help_termsOfServiceUpdateEmpty::class,
          org.Tajgram.tgnet.TLRPC.help_TermsOfServiceUpdate::TLdeserialize, null)

    }

    @Test
    public fun test_001339_help_TimezonesList_TL_help_timezonesList() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_help_TimezonesList.TL_help_timezonesList::class,
          org.Tajgram.tgnet.TLRPC.help_timezonesList::TLdeserialize, null)

    }

    @Test
    public fun test_001340_help_TimezonesList_TL_help_timezonesListNotModified() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_help_TimezonesList.TL_help_timezonesListNotModified::class,
          org.Tajgram.tgnet.TLRPC.help_timezonesList::TLdeserialize, null)

    }

    @Test
    public fun test_001341_help_UserInfo_TL_help_userInfo() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_help_UserInfo.TL_help_userInfo::class,
          org.Tajgram.tgnet.TLRPC.help_UserInfo::TLdeserialize, null)

    }

    @Test
    public fun test_001342_help_UserInfo_TL_help_userInfoEmpty() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_help_UserInfo.TL_help_userInfoEmpty::class,
          org.Tajgram.tgnet.TLRPC.help_UserInfo::TLdeserialize, null)

    }

    @Test
    public fun test_001343_messages_AffectedFoundMessages_TL_messages_affectedFoundMessages() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_messages_AffectedFoundMessages.TL_messages_affectedFoundMessages::class,
          org.Tajgram.tgnet.TLRPC.TL_messages_affectedFoundMessages::TLdeserialize, null)

    }

    @Test
    public fun test_001344_messages_AffectedHistory_TL_messages_affectedHistory() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_messages_AffectedHistory.TL_messages_affectedHistory::class,
          org.Tajgram.tgnet.TLRPC.TL_messages_affectedHistory::TLdeserialize, null)

    }

    @Test
    public fun test_001345_messages_AffectedMessages_TL_messages_affectedMessages() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_messages_AffectedMessages.TL_messages_affectedMessages::class,
          org.Tajgram.tgnet.TLRPC.TL_messages_affectedMessages::TLdeserialize, null)

    }

    @Test
    public fun test_001346_messages_AllStickers_TL_messages_allStickers() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_messages_AllStickers.TL_messages_allStickers::class,
          org.Tajgram.tgnet.TLRPC.messages_AllStickers::TLdeserialize, null)

    }

    @Test
    public fun test_001347_messages_AllStickers_TL_messages_allStickersNotModified() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_messages_AllStickers.TL_messages_allStickersNotModified::class,
          org.Tajgram.tgnet.TLRPC.messages_AllStickers::TLdeserialize, null)

    }

    @Test
    public fun test_001348_messages_ArchivedStickers_TL_messages_archivedStickers() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_messages_ArchivedStickers.TL_messages_archivedStickers::class,
          org.Tajgram.tgnet.TLRPC.TL_messages_archivedStickers::TLdeserialize, null)

    }

    @Test
    public fun test_001349_messages_AvailableEffects_TL_messages_availableEffects() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_messages_AvailableEffects.TL_messages_availableEffects::class,
          org.Tajgram.tgnet.TLRPC.messages_AvailableEffects::TLdeserialize, null)

    }

    @Test
    public fun test_001350_messages_AvailableEffects_TL_messages_availableEffectsNotModified() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_messages_AvailableEffects.TL_messages_availableEffectsNotModified::class,
          org.Tajgram.tgnet.TLRPC.messages_AvailableEffects::TLdeserialize, null)

    }

    @Test
    public fun test_001351_messages_AvailableReactions_TL_messages_availableReactions() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_messages_AvailableReactions.TL_messages_availableReactions::class,
          org.Tajgram.tgnet.TLRPC.messages_AvailableReactions::TLdeserialize, null)

    }

    @Test
    public fun test_001352_messages_AvailableReactions_TL_messages_availableReactionsNotModified() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_messages_AvailableReactions.TL_messages_availableReactionsNotModified::class,
          org.Tajgram.tgnet.TLRPC.messages_AvailableReactions::TLdeserialize, null)

    }

    @Test
    public fun test_001353_messages_BotApp_TL_messages_botApp() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_messages_BotApp.TL_messages_botApp::class,
          org.Tajgram.tgnet.TLRPC.TL_messages_botApp::TLdeserialize, null)

    }

    @Test
    public fun test_001354_messages_BotCallbackAnswer_TL_messages_botCallbackAnswer() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_messages_BotCallbackAnswer.TL_messages_botCallbackAnswer::class,
          org.Tajgram.tgnet.TLRPC.TL_messages_botCallbackAnswer::TLdeserialize, null)

    }

    @Test
    public fun test_001355_messages_BotResults_TL_messages_botResults() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_messages_BotResults.TL_messages_botResults::class,
          org.Tajgram.tgnet.TLRPC.TL_messages_botResults::TLdeserialize, null)
          test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_messages_BotResults.TL_messages_botResults::class,
              org.Tajgram.tgnet.TLRPC.messages_BotResults::TLdeserialize, null)

    }

    @Test
    public fun test_001356_messages_ChatAdminsWithInvites_TL_messages_chatAdminsWithInvites() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_messages_ChatAdminsWithInvites.TL_messages_chatAdminsWithInvites::class,
          org.Tajgram.tgnet.TLRPC.TL_messages_chatAdminsWithInvites::TLdeserialize, null)

    }

    @Test
    public fun test_001357_messages_ChatFull_TL_messages_chatFull() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_messages_ChatFull.TL_messages_chatFull::class,
          org.Tajgram.tgnet.TLRPC.TL_messages_chatFull::TLdeserialize, null)

    }

    @Test
    public fun test_001358_messages_ChatInviteImporters_TL_messages_chatInviteImporters() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_messages_ChatInviteImporters.TL_messages_chatInviteImporters::class,
          org.Tajgram.tgnet.TLRPC.TL_messages_chatInviteImporters::TLdeserialize, null)

    }

    @Test
    public fun test_001359_messages_ChatInviteJoinResult_TL_messages_chatInviteJoinResultOk() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_messages_ChatInviteJoinResult.TL_messages_chatInviteJoinResultOk::class,
          org.Tajgram.tgnet.TLRPC.ChatInviteJoinResult::TLdeserialize, null)

    }

    @Test
    public fun test_001360_messages_ChatInviteJoinResult_TL_messages_chatInviteJoinResultWebView() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_messages_ChatInviteJoinResult.TL_messages_chatInviteJoinResultWebView::class,
          org.Tajgram.tgnet.TLRPC.ChatInviteJoinResult::TLdeserialize, null)

    }

    @Test
    public fun test_001361_messages_Chats_TL_messages_chats() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_messages_Chats.TL_messages_chats::class,
          org.Tajgram.tgnet.TLRPC.messages_Chats::TLdeserialize, null)

    }

    @Test
    public fun test_001362_messages_Chats_TL_messages_chatsSlice() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_messages_Chats.TL_messages_chatsSlice::class,
          org.Tajgram.tgnet.TLRPC.messages_Chats::TLdeserialize, null)

    }

    @Test
    public
        fun test_001363_messages_CheckedHistoryImportPeer_TL_messages_checkedHistoryImportPeer() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_messages_CheckedHistoryImportPeer.TL_messages_checkedHistoryImportPeer::class,
          org.Tajgram.tgnet.TLRPC.TL_messages_checkedHistoryImportPeer::TLdeserialize, null)

    }

    @Test
    public fun test_001364_messages_ComposedMessageWithAI_TL_messages_composedMessageWithAI() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_messages_ComposedMessageWithAI.TL_messages_composedMessageWithAI::class,
          org.Tajgram.tgnet.TLRPC.TL_composedMessageWithAI::TLdeserialize, null)

    }

    @Test
    public fun test_001365_messages_DhConfig_TL_messages_dhConfig() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_messages_DhConfig.TL_messages_dhConfig::class,
          org.Tajgram.tgnet.TLRPC.messages_DhConfig::TLdeserialize, null)

    }

    @Test
    public fun test_001366_messages_DhConfig_TL_messages_dhConfigNotModified() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_messages_DhConfig.TL_messages_dhConfigNotModified::class,
          org.Tajgram.tgnet.TLRPC.messages_DhConfig::TLdeserialize, null)

    }

    @Test
    public fun test_001367_messages_DialogFilters_TL_messages_dialogFilters() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_messages_DialogFilters.TL_messages_dialogFilters::class,
          org.Tajgram.tgnet.TLRPC.TL_messages_dialogFilters::TLdeserialize, null)

    }

    @Test
    public fun test_001368_messages_Dialogs_TL_messages_dialogs() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_messages_Dialogs.TL_messages_dialogs::class,
          org.Tajgram.tgnet.TLRPC.messages_Dialogs::TLdeserialize, null)

    }

    @Test
    public fun test_001369_messages_Dialogs_TL_messages_dialogsNotModified() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_messages_Dialogs.TL_messages_dialogsNotModified::class,
          org.Tajgram.tgnet.TLRPC.messages_Dialogs::TLdeserialize, null)

    }

    @Test
    public fun test_001370_messages_Dialogs_TL_messages_dialogsSlice() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_messages_Dialogs.TL_messages_dialogsSlice::class,
          org.Tajgram.tgnet.TLRPC.messages_Dialogs::TLdeserialize, null)

    }

    @Test
    public fun test_001371_messages_DiscussionMessage_TL_messages_discussionMessage() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_messages_DiscussionMessage.TL_messages_discussionMessage::class,
          org.Tajgram.tgnet.TLRPC.TL_messages_discussionMessage::TLdeserialize, null)

    }

    @Test
    public fun test_001372_messages_EmojiGameInfo_TL_messages_emojiGameDiceInfo() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_messages_EmojiGameInfo.TL_messages_emojiGameDiceInfo::class,
          org.Tajgram.tgnet.TLRPC.EmojiGameInfo::TLdeserialize, null)

    }

    @Test
    public fun test_001373_messages_EmojiGameInfo_TL_messages_emojiGameUnavailable() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_messages_EmojiGameInfo.TL_messages_emojiGameUnavailable::class,
          org.Tajgram.tgnet.TLRPC.EmojiGameInfo::TLdeserialize, null)

    }

    @Test
    public fun test_001374_messages_EmojiGameOutcome_TL_messages_emojiGameOutcome() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_messages_EmojiGameOutcome.TL_messages_emojiGameOutcome::class,
          org.Tajgram.tgnet.TLRPC.TL_messages_emojiGameOutcome::TLdeserialize, null)

    }

    @Test
    public fun test_001375_messages_EmojiGroups_TL_messages_emojiGroups() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_messages_EmojiGroups.TL_messages_emojiGroups::class,
          org.Tajgram.tgnet.TLRPC.messages_EmojiGroups::TLdeserialize, null)

    }

    @Test
    public fun test_001376_messages_EmojiGroups_TL_messages_emojiGroupsNotModified() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_messages_EmojiGroups.TL_messages_emojiGroupsNotModified::class,
          org.Tajgram.tgnet.TLRPC.messages_EmojiGroups::TLdeserialize, null)

    }

    @Test
    public fun test_001377_messages_ExportedChatInvite_TL_messages_exportedChatInvite() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_messages_ExportedChatInvite.TL_messages_exportedChatInvite::class,
          org.Tajgram.tgnet.TLRPC.messages_ExportedChatInvite::TLdeserialize, null)

    }

    @Test
    public fun test_001378_messages_ExportedChatInvite_TL_messages_exportedChatInviteReplaced() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_messages_ExportedChatInvite.TL_messages_exportedChatInviteReplaced::class,
          org.Tajgram.tgnet.TLRPC.messages_ExportedChatInvite::TLdeserialize, null)

    }

    @Test
    public fun test_001379_messages_ExportedChatInvites_TL_messages_exportedChatInvites() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_messages_ExportedChatInvites.TL_messages_exportedChatInvites::class,
          org.Tajgram.tgnet.TLRPC.TL_messages_exportedChatInvites::TLdeserialize, null)

    }

    @Test
    public fun test_001380_messages_FavedStickers_TL_messages_favedStickers() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_messages_FavedStickers.TL_messages_favedStickers::class,
          org.Tajgram.tgnet.TLRPC.messages_FavedStickers::TLdeserialize, null)

    }

    @Test
    public fun test_001381_messages_FavedStickers_TL_messages_favedStickersNotModified() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_messages_FavedStickers.TL_messages_favedStickersNotModified::class,
          org.Tajgram.tgnet.TLRPC.messages_FavedStickers::TLdeserialize, null)

    }

    @Test
    public fun test_001382_messages_FeaturedStickers_TL_messages_featuredStickers() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_messages_FeaturedStickers.TL_messages_featuredStickers::class,
          org.Tajgram.tgnet.TLRPC.messages_FeaturedStickers::TLdeserialize, null)

    }

    @Test
    public fun test_001383_messages_FeaturedStickers_TL_messages_featuredStickersNotModified() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_messages_FeaturedStickers.TL_messages_featuredStickersNotModified::class,
          org.Tajgram.tgnet.TLRPC.messages_FeaturedStickers::TLdeserialize, null)

    }

    @Test
    public fun test_001384_messages_ForumTopics_TL_messages_forumTopics() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_messages_ForumTopics.TL_messages_forumTopics::class,
          org.Tajgram.tgnet.TLRPC.TL_messages_forumTopics::TLdeserialize, null)

    }

    @Test
    public fun test_001385_messages_FoundStickerSets_TL_messages_foundStickerSets() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_messages_FoundStickerSets.TL_messages_foundStickerSets::class,
          org.Tajgram.tgnet.TLRPC.messages_FoundStickerSets::TLdeserialize, null)

    }

    @Test
    public fun test_001386_messages_FoundStickerSets_TL_messages_foundStickerSetsNotModified() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_messages_FoundStickerSets.TL_messages_foundStickerSetsNotModified::class,
          org.Tajgram.tgnet.TLRPC.messages_FoundStickerSets::TLdeserialize, null)

    }

    @Test
    public fun test_001387_messages_FoundStickers_TL_messages_foundStickers() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_messages_FoundStickers.TL_messages_foundStickers::class,
          org.Tajgram.tgnet.TLRPC.messages_FoundStickers::TLdeserialize, null)

    }

    @Test
    public fun test_001388_messages_FoundStickers_TL_messages_foundStickersNotModified() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_messages_FoundStickers.TL_messages_foundStickersNotModified::class,
          org.Tajgram.tgnet.TLRPC.messages_FoundStickers::TLdeserialize, null)

    }

    @Test
    public fun test_001389_messages_HighScores_TL_messages_highScores() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_messages_HighScores.TL_messages_highScores::class,
          org.Tajgram.tgnet.TLRPC.TL_messages_highScores::TLdeserialize, null)

    }

    @Test
    public fun test_001390_messages_HistoryImport_TL_messages_historyImport() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_messages_HistoryImport.TL_messages_historyImport::class,
          org.Tajgram.tgnet.TLRPC.TL_messages_historyImport::TLdeserialize, null)

    }

    @Test
    public fun test_001391_messages_HistoryImportParsed_TL_messages_historyImportParsed() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_messages_HistoryImportParsed.TL_messages_historyImportParsed::class,
          org.Tajgram.tgnet.TLRPC.TL_messages_historyImportParsed::TLdeserialize, null)

    }

    @Test
    public fun test_001392_messages_InactiveChats_TL_messages_inactiveChats() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_messages_InactiveChats.TL_messages_inactiveChats::class,
          org.Tajgram.tgnet.TLRPC.TL_messages_inactiveChats::TLdeserialize, null)

    }

    @Test
    public fun test_001393_messages_InvitedUsers_TL_messages_invitedUsers() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_messages_InvitedUsers.TL_messages_invitedUsers::class,
          org.Tajgram.tgnet.TLRPC.TL_messages_invitedUsers::TLdeserialize, null)

    }

    @Test
    public fun test_001394_messages_MessageEditData_TL_messages_messageEditData() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_messages_MessageEditData.TL_messages_messageEditData::class,
          org.Tajgram.tgnet.TLRPC.TL_messages_messageEditData::TLdeserialize, null)

    }

    @Test
    public fun test_001395_messages_MessageReactionsList_TL_messages_messageReactionsList() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_messages_MessageReactionsList.TL_messages_messageReactionsList::class,
          org.Tajgram.tgnet.TLRPC.TL_messages_messageReactionsList::TLdeserialize, null)

    }

    @Test
    public fun test_001396_messages_MessageViews_TL_messages_messageViews() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_messages_MessageViews.TL_messages_messageViews::class,
          org.Tajgram.tgnet.TLRPC.TL_messages_messageViews::TLdeserialize, null)

    }

    @Test
    public fun test_001397_messages_Messages_TL_messages_channelMessages() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_messages_Messages.TL_messages_channelMessages::class,
          org.Tajgram.tgnet.TLRPC.messages_Messages::TLdeserialize, null)

    }

    @Test
    public fun test_001398_messages_Messages_TL_messages_messages() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_messages_Messages.TL_messages_messages::class,
          org.Tajgram.tgnet.TLRPC.messages_Messages::TLdeserialize, null)

    }

    @Test
    public fun test_001399_messages_Messages_TL_messages_messagesNotModified() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_messages_Messages.TL_messages_messagesNotModified::class,
          org.Tajgram.tgnet.TLRPC.messages_Messages::TLdeserialize, null)

    }

    @Test
    public fun test_001400_messages_Messages_TL_messages_messagesSlice() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_messages_Messages.TL_messages_messagesSlice::class,
          org.Tajgram.tgnet.TLRPC.messages_Messages::TLdeserialize, null)

    }

    @Test
    public fun test_001401_messages_MyStickers_TL_messages_myStickers() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_messages_MyStickers.TL_messages_myStickers::class,
          org.Tajgram.tgnet.TLRPC.TL_messages_myStickers::TLdeserialize, null)

    }

    @Test
    public fun test_001402_messages_PeerDialogs_TL_messages_peerDialogs() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_messages_PeerDialogs.TL_messages_peerDialogs::class,
          org.Tajgram.tgnet.TLRPC.TL_messages_peerDialogs::TLdeserialize, null)

    }

    @Test
    public fun test_001403_messages_PeerSettings_TL_messages_peerSettings() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_messages_PeerSettings.TL_messages_peerSettings::class,
          org.Tajgram.tgnet.TLRPC.TL_messages_peerSettings::TLdeserialize, null)

    }

    @Test
    public fun test_001404_messages_PreparedInlineMessage_TL_messages_preparedInlineMessage() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_messages_PreparedInlineMessage.TL_messages_preparedInlineMessage::class,
          org.Tajgram.tgnet.TLRPC.TL_messages_preparedInlineMessage::TLdeserialize, null)

    }

    @Test
    public fun test_001405_messages_QuickReplies_TL_messages_quickReplies() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_messages_QuickReplies.TL_messages_quickReplies::class,
          org.Tajgram.tgnet.TLRPC.messages_quickReplies::TLdeserialize, null)

    }

    @Test
    public fun test_001406_messages_QuickReplies_TL_messages_quickRepliesNotModified() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_messages_QuickReplies.TL_messages_quickRepliesNotModified::class,
          org.Tajgram.tgnet.TLRPC.messages_quickReplies::TLdeserialize, null)

    }

    @Test
    public fun test_001407_messages_Reactions_TL_messages_reactions() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_messages_Reactions.TL_messages_reactions::class,
          org.Tajgram.tgnet.TLRPC.messages_Reactions::TLdeserialize, null)

    }

    @Test
    public fun test_001408_messages_Reactions_TL_messages_reactionsNotModified() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_messages_Reactions.TL_messages_reactionsNotModified::class,
          org.Tajgram.tgnet.TLRPC.messages_Reactions::TLdeserialize, null)

    }

    @Test
    public fun test_001409_messages_RecentStickers_TL_messages_recentStickers() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_messages_RecentStickers.TL_messages_recentStickers::class,
          org.Tajgram.tgnet.TLRPC.messages_RecentStickers::TLdeserialize, null)

    }

    @Test
    public fun test_001410_messages_RecentStickers_TL_messages_recentStickersNotModified() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_messages_RecentStickers.TL_messages_recentStickersNotModified::class,
          org.Tajgram.tgnet.TLRPC.messages_RecentStickers::TLdeserialize, null)

    }

    @Test
    public fun test_001411_messages_SavedDialogs_TL_messages_savedDialogs() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_messages_SavedDialogs.TL_messages_savedDialogs::class,
          org.Tajgram.tgnet.TLRPC.messages_SavedDialogs::TLdeserialize, null)

    }

    @Test
    public fun test_001412_messages_SavedDialogs_TL_messages_savedDialogsNotModified() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_messages_SavedDialogs.TL_messages_savedDialogsNotModified::class,
          org.Tajgram.tgnet.TLRPC.messages_SavedDialogs::TLdeserialize, null)

    }

    @Test
    public fun test_001413_messages_SavedDialogs_TL_messages_savedDialogsSlice() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_messages_SavedDialogs.TL_messages_savedDialogsSlice::class,
          org.Tajgram.tgnet.TLRPC.messages_SavedDialogs::TLdeserialize, null)

    }

    @Test
    public fun test_001414_messages_SavedGifs_TL_messages_savedGifs() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_messages_SavedGifs.TL_messages_savedGifs::class,
          org.Tajgram.tgnet.TLRPC.messages_SavedGifs::TLdeserialize, null)

    }

    @Test
    public fun test_001415_messages_SavedGifs_TL_messages_savedGifsNotModified() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_messages_SavedGifs.TL_messages_savedGifsNotModified::class,
          org.Tajgram.tgnet.TLRPC.messages_SavedGifs::TLdeserialize, null)

    }

    @Test
    public fun test_001416_messages_SavedReactionTags_TL_messages_savedReactionTags() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_messages_SavedReactionTags.TL_messages_savedReactionTags::class,
          org.Tajgram.tgnet.TLRPC.messages_SavedReactionTags::TLdeserialize, null)

    }

    @Test
    public fun test_001417_messages_SavedReactionTags_TL_messages_savedReactionTagsNotModified() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_messages_SavedReactionTags.TL_messages_savedReactionTagsNotModified::class,
          org.Tajgram.tgnet.TLRPC.messages_SavedReactionTags::TLdeserialize, null)

    }

    @Test
    public fun test_001418_messages_SearchCounter_TL_messages_searchCounter() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_messages_SearchCounter.TL_messages_searchCounter::class,
          org.Tajgram.tgnet.TLRPC.TL_messages_searchCounter::TLdeserialize, null)

    }

    @Test
    public fun test_001419_messages_SearchResultsCalendar_TL_messages_searchResultsCalendar() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_messages_SearchResultsCalendar.TL_messages_searchResultsCalendar::class,
          org.Tajgram.tgnet.TLRPC.TL_messages_searchResultsCalendar::TLdeserialize, null)

    }

    @Test
    public fun test_001420_messages_SearchResultsPositions_TL_messages_searchResultsPositions() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_messages_SearchResultsPositions.TL_messages_searchResultsPositions::class,
          org.Tajgram.tgnet.TLRPC.TL_messages_searchResultsPositions::TLdeserialize, null)

    }

    @Test
    public fun test_001421_messages_SentEncryptedMessage_TL_messages_sentEncryptedFile() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_messages_SentEncryptedMessage.TL_messages_sentEncryptedFile::class,
          org.Tajgram.tgnet.TLRPC.messages_SentEncryptedMessage::TLdeserialize, null)

    }

    @Test
    public fun test_001422_messages_SentEncryptedMessage_TL_messages_sentEncryptedMessage() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_messages_SentEncryptedMessage.TL_messages_sentEncryptedMessage::class,
          org.Tajgram.tgnet.TLRPC.messages_SentEncryptedMessage::TLdeserialize, null)

    }

    @Test
    public fun test_001423_messages_SponsoredMessages_TL_messages_sponsoredMessages() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_messages_SponsoredMessages.TL_messages_sponsoredMessages::class,
          org.Tajgram.tgnet.TLRPC.messages_SponsoredMessages::TLdeserialize, null)

    }

    @Test
    public fun test_001424_messages_SponsoredMessages_TL_messages_sponsoredMessagesEmpty() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_messages_SponsoredMessages.TL_messages_sponsoredMessagesEmpty::class,
          org.Tajgram.tgnet.TLRPC.messages_SponsoredMessages::TLdeserialize, null)

    }

    @Test
    public fun test_001425_messages_StickerSet_TL_messages_stickerSet() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_messages_StickerSet.TL_messages_stickerSet::class,
          org.Tajgram.tgnet.TLRPC.messages_StickerSet::TLdeserialize, null)

    }

    @Test
    public fun test_001426_messages_StickerSet_TL_messages_stickerSetNotModified() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_messages_StickerSet.TL_messages_stickerSetNotModified::class,
          org.Tajgram.tgnet.TLRPC.messages_StickerSet::TLdeserialize, null)

    }

    @Test
    public
        fun test_001427_messages_StickerSetInstallResult_TL_messages_stickerSetInstallResultArchive() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_messages_StickerSetInstallResult.TL_messages_stickerSetInstallResultArchive::class,
          org.Tajgram.tgnet.TLRPC.messages_StickerSetInstallResult::TLdeserialize, null)

    }

    @Test
    public
        fun test_001428_messages_StickerSetInstallResult_TL_messages_stickerSetInstallResultSuccess() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_messages_StickerSetInstallResult.TL_messages_stickerSetInstallResultSuccess::class,
          org.Tajgram.tgnet.TLRPC.messages_StickerSetInstallResult::TLdeserialize, null)

    }

    @Test
    public fun test_001429_messages_Stickers_TL_messages_stickers() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_messages_Stickers.TL_messages_stickers::class,
          org.Tajgram.tgnet.TLRPC.messages_Stickers::TLdeserialize, null)

    }

    @Test
    public fun test_001430_messages_Stickers_TL_messages_stickersNotModified() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_messages_Stickers.TL_messages_stickersNotModified::class,
          org.Tajgram.tgnet.TLRPC.messages_Stickers::TLdeserialize, null)

    }

    @Test
    public fun test_001431_messages_TranscribedAudio_TL_messages_transcribedAudio() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_messages_TranscribedAudio.TL_messages_transcribedAudio::class,
          org.Tajgram.tgnet.TLRPC.TL_messages_transcribedAudio::TLdeserialize, null)

    }

    @Test
    public fun test_001432_messages_TranslatedText_TL_messages_translateResult() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_messages_TranslatedText.TL_messages_translateResult::class,
          org.Tajgram.tgnet.TLRPC.TL_messages_translateResult::TLdeserialize, null)

    }

    @Test
    public fun test_001433_messages_VotesList_TL_messages_votesList() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_messages_VotesList.TL_messages_votesList::class,
          org.Tajgram.tgnet.TLRPC.TL_messages_votesList::TLdeserialize, null)

    }

    @Test
    public fun test_001434_messages_WebPage_TL_messages_webPage() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_messages_WebPage.TL_messages_webPage::class,
          org.Tajgram.tgnet.TLRPC.TL_messages_webPage::TLdeserialize, null)

    }

    @Test
    public fun test_001435_messages_WebPagePreview_TL_messages_webPagePreview() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_messages_WebPagePreview.TL_messages_webPagePreview::class,
          org.Tajgram.tgnet.tl.TL_account.webPagePreview::TLdeserialize, null)

    }

    @Test
    public fun test_001436_payments_BankCardData_TL_payments_bankCardData() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_payments_BankCardData.TL_payments_bankCardData::class,
          org.Tajgram.tgnet.TLRPC.TL_payments_bankCardData::TLdeserialize, null)

    }

    @Test
    public
        fun test_001437_payments_CheckCanSendGiftResult_TL_payments_checkCanSendGiftResultFail() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_payments_CheckCanSendGiftResult.TL_payments_checkCanSendGiftResultFail::class,
          org.Tajgram.tgnet.tl.TL_stars.CheckCanSendGiftResult::TLdeserialize, null)

    }

    @Test
    public fun test_001438_payments_CheckCanSendGiftResult_TL_payments_checkCanSendGiftResultOk() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_payments_CheckCanSendGiftResult.TL_payments_checkCanSendGiftResultOk::class,
          org.Tajgram.tgnet.tl.TL_stars.CheckCanSendGiftResult::TLdeserialize, null)

    }

    @Test
    public fun test_001439_payments_CheckedGiftCode_TL_payments_checkedGiftCode() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_payments_CheckedGiftCode.TL_payments_checkedGiftCode::class,
          org.Tajgram.tgnet.TLRPC.TL_payments_checkedGiftCode::TLdeserialize, null)

    }

    @Test
    public fun test_001440_payments_ConnectedStarRefBots_TL_payments_connectedStarRefBots() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_payments_ConnectedStarRefBots.TL_payments_connectedStarRefBots::class,
          org.Tajgram.tgnet.tl.TL_payments.connectedStarRefBots::TLdeserialize, null)

    }

    @Test
    public fun test_001441_payments_ExportedInvoice_TL_payments_exportedInvoice() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_payments_ExportedInvoice.TL_payments_exportedInvoice::class,
          org.Tajgram.tgnet.TLRPC.TL_payments_exportedInvoice::TLdeserialize, null)

    }

    @Test
    public fun test_001442_payments_GiveawayInfo_TL_payments_giveawayInfo() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_payments_GiveawayInfo.TL_payments_giveawayInfo::class,
          org.Tajgram.tgnet.TLRPC.payments_GiveawayInfo::TLdeserialize, null)

    }

    @Test
    public fun test_001443_payments_GiveawayInfo_TL_payments_giveawayInfoResults() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_payments_GiveawayInfo.TL_payments_giveawayInfoResults::class,
          org.Tajgram.tgnet.TLRPC.payments_GiveawayInfo::TLdeserialize, null)

    }

    @Test
    public fun test_001444_payments_PaymentForm_TL_payments_paymentForm() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_payments_PaymentForm.TL_payments_paymentForm::class,
          org.Tajgram.tgnet.TLRPC.PaymentForm::TLdeserialize, null)

    }

    @Test
    public fun test_001445_payments_PaymentForm_TL_payments_paymentFormStarGift() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_payments_PaymentForm.TL_payments_paymentFormStarGift::class,
          org.Tajgram.tgnet.TLRPC.PaymentForm::TLdeserialize, null)

    }

    @Test
    public fun test_001446_payments_PaymentForm_TL_payments_paymentFormStars() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_payments_PaymentForm.TL_payments_paymentFormStars::class,
          org.Tajgram.tgnet.TLRPC.PaymentForm::TLdeserialize, null)

    }

    @Test
    public fun test_001447_payments_PaymentReceipt_TL_payments_paymentReceipt() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_payments_PaymentReceipt.TL_payments_paymentReceipt::class,
          org.Tajgram.tgnet.TLRPC.PaymentReceipt::TLdeserialize, null)

    }

    @Test
    public fun test_001448_payments_PaymentReceipt_TL_payments_paymentReceiptStars() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_payments_PaymentReceipt.TL_payments_paymentReceiptStars::class,
          org.Tajgram.tgnet.TLRPC.PaymentReceipt::TLdeserialize, null)

    }

    @Test
    public fun test_001449_payments_PaymentResult_TL_payments_paymentResult() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_payments_PaymentResult.TL_payments_paymentResult::class,
          org.Tajgram.tgnet.TLRPC.payments_PaymentResult::TLdeserialize, null)

    }

    @Test
    public fun test_001450_payments_PaymentResult_TL_payments_paymentVerificationNeeded() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_payments_PaymentResult.TL_payments_paymentVerificationNeeded::class,
          org.Tajgram.tgnet.TLRPC.payments_PaymentResult::TLdeserialize, null)

    }

    @Test
    public fun test_001451_payments_ResaleStarGifts_TL_payments_resaleStarGifts() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_payments_ResaleStarGifts.TL_payments_resaleStarGifts::class,
          org.Tajgram.tgnet.tl.TL_stars.resaleStarGifts::TLdeserialize, null)

    }

    @Test
    public fun test_001452_payments_SavedInfo_TL_payments_savedInfo() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_payments_SavedInfo.TL_payments_savedInfo::class,
          org.Tajgram.tgnet.TLRPC.TL_payments_savedInfo::TLdeserialize, null)

    }

    @Test
    public fun test_001453_payments_SavedStarGifts_TL_payments_savedStarGifts() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_payments_SavedStarGifts.TL_payments_savedStarGifts::class,
          org.Tajgram.tgnet.tl.TL_stars.TL_payments_savedStarGifts::TLdeserialize, null)

    }

    @Test
    public fun test_001454_payments_StarGiftActiveAuctions_TL_payments_starGiftActiveAuctions() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_payments_StarGiftActiveAuctions.TL_payments_starGiftActiveAuctions::class,
          org.Tajgram.tgnet.tl.TL_payments.StarGiftActiveAuctions::TLdeserialize, null)

    }

    @Test
    public
        fun test_001455_payments_StarGiftActiveAuctions_TL_payments_starGiftActiveAuctionsNotModified() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_payments_StarGiftActiveAuctions.TL_payments_starGiftActiveAuctionsNotModified::class,
          org.Tajgram.tgnet.tl.TL_payments.StarGiftActiveAuctions::TLdeserialize, null)

    }

    @Test
    public
        fun test_001456_payments_StarGiftAuctionAcquiredGifts_TL_payments_starGiftAuctionAcquiredGifts() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_payments_StarGiftAuctionAcquiredGifts.TL_payments_starGiftAuctionAcquiredGifts::class,
          org.Tajgram.tgnet.tl.TL_payments.TL_StarGiftAuctionAcquiredGifts::TLdeserialize, null)

    }

    @Test
    public fun test_001457_payments_StarGiftAuctionState_TL_payments_starGiftAuctionState() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_payments_StarGiftAuctionState.TL_payments_starGiftAuctionState::class,
          org.Tajgram.tgnet.tl.TL_payments.TL_StarGiftAuctionState::TLdeserialize, null)

    }

    @Test
    public fun test_001458_payments_StarGiftCollections_TL_payments_starGiftCollections() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_payments_StarGiftCollections.TL_payments_starGiftCollections::class,
          org.Tajgram.tgnet.tl.TL_stars.StarGiftCollections::TLdeserialize, null)

    }

    @Test
    public
        fun test_001459_payments_StarGiftCollections_TL_payments_starGiftCollectionsNotModified() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_payments_StarGiftCollections.TL_payments_starGiftCollectionsNotModified::class,
          org.Tajgram.tgnet.tl.TL_stars.StarGiftCollections::TLdeserialize, null)

    }

    @Test
    public
        fun test_001460_payments_StarGiftUpgradeAttributes_TL_payments_starGiftUpgradeAttributes() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_payments_StarGiftUpgradeAttributes.TL_payments_starGiftUpgradeAttributes::class,
          org.Tajgram.tgnet.tl.TL_stars.starGiftUpgradeAttributes::TLdeserialize, null)

    }

    @Test
    public fun test_001461_payments_StarGiftUpgradePreview_TL_payments_starGiftUpgradePreview() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_payments_StarGiftUpgradePreview.TL_payments_starGiftUpgradePreview::class,
          org.Tajgram.tgnet.tl.TL_stars.starGiftUpgradePreview::TLdeserialize, null)

    }

    @Test
    public fun test_001462_payments_StarGiftWithdrawalUrl_TL_payments_starGiftWithdrawalUrl() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_payments_StarGiftWithdrawalUrl.TL_payments_starGiftWithdrawalUrl::class,
          org.Tajgram.tgnet.tl.TL_stars.starGiftWithdrawalUrl::TLdeserialize, null)

    }

    @Test
    public fun test_001463_payments_StarGifts_TL_payments_starGifts() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_payments_StarGifts.TL_payments_starGifts::class,
          org.Tajgram.tgnet.tl.TL_stars.StarGifts::TLdeserialize, null)

    }

    @Test
    public fun test_001464_payments_StarGifts_TL_payments_starGiftsNotModified() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_payments_StarGifts.TL_payments_starGiftsNotModified::class,
          org.Tajgram.tgnet.tl.TL_stars.StarGifts::TLdeserialize, null)

    }

    @Test
    public
        fun test_001465_payments_StarsRevenueAdsAccountUrl_TL_payments_starsRevenueAdsAccountUrl() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_payments_StarsRevenueAdsAccountUrl.TL_payments_starsRevenueAdsAccountUrl::class,
          org.Tajgram.tgnet.TLRPC.TL_payments_starsRevenueAdsAccountUrl::TLdeserialize, null)

    }

    @Test
    public fun test_001466_payments_StarsRevenueStats_TL_payments_starsRevenueStats() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_payments_StarsRevenueStats.TL_payments_starsRevenueStats::class,
          org.Tajgram.tgnet.TLRPC.TL_payments_starsRevenueStats::TLdeserialize, null)

    }

    @Test
    public
        fun test_001467_payments_StarsRevenueWithdrawalUrl_TL_payments_starsRevenueWithdrawalUrl() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_payments_StarsRevenueWithdrawalUrl.TL_payments_starsRevenueWithdrawalUrl::class,
          org.Tajgram.tgnet.TLRPC.TL_payments_starsRevenueWithdrawalUrl::TLdeserialize, null)

    }

    @Test
    public fun test_001468_payments_StarsStatus_TL_payments_starsStatus() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_payments_StarsStatus.TL_payments_starsStatus::class,
          org.Tajgram.tgnet.tl.TL_stars.StarsStatus::TLdeserialize, null)

    }

    @Test
    public fun test_001469_payments_SuggestedStarRefBots_TL_payments_suggestedStarRefBots() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_payments_SuggestedStarRefBots.TL_payments_suggestedStarRefBots::class,
          org.Tajgram.tgnet.tl.TL_payments.suggestedStarRefBots::TLdeserialize, null)

    }

    @Test
    public fun test_001470_payments_UniqueStarGift_TL_payments_uniqueStarGift() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_payments_UniqueStarGift.TL_payments_uniqueStarGift::class,
          org.Tajgram.tgnet.tl.TL_stars.TL_payments_uniqueStarGift::TLdeserialize, null)

    }

    @Test
    public fun test_001471_payments_UniqueStarGiftValueInfo_TL_payments_uniqueStarGiftValueInfo() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_payments_UniqueStarGiftValueInfo.TL_payments_uniqueStarGiftValueInfo::class,
          org.Tajgram.tgnet.tl.TL_stars.UniqueStarGiftValueInfo::TLdeserialize, null)

    }

    @Test
    public fun test_001472_payments_ValidatedRequestedInfo_TL_payments_validatedRequestedInfo() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_payments_ValidatedRequestedInfo.TL_payments_validatedRequestedInfo::class,
          org.Tajgram.tgnet.TLRPC.TL_payments_validatedRequestedInfo::TLdeserialize, null)

    }

    @Test
    public fun test_001473_phone_ExportedGroupCallInvite_TL_phone_exportedGroupCallInvite() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_phone_ExportedGroupCallInvite.TL_phone_exportedGroupCallInvite::class,
          org.Tajgram.tgnet.tl.TL_phone.exportedGroupCallInvite::TLdeserialize, null)

    }

    @Test
    public fun test_001474_phone_GroupCall_TL_phone_groupCall() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_phone_GroupCall.TL_phone_groupCall::class,
          org.Tajgram.tgnet.tl.TL_phone.groupCall::TLdeserialize, null)

    }

    @Test
    public fun test_001475_phone_GroupCallStars_TL_phone_groupCallStars() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_phone_GroupCallStars.TL_phone_groupCallStars::class,
          org.Tajgram.tgnet.tl.TL_phone.groupCallStars::TLdeserialize, null)

    }

    @Test
    public fun test_001476_phone_GroupCallStreamChannels_TL_phone_groupCallStreamChannels() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_phone_GroupCallStreamChannels.TL_phone_groupCallStreamChannels::class,
          org.Tajgram.tgnet.tl.TL_phone.groupCallStreamChannels::TLdeserialize, null)

    }

    @Test
    public fun test_001477_phone_GroupCallStreamRtmpUrl_TL_phone_groupCallStreamRtmpUrl() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_phone_GroupCallStreamRtmpUrl.TL_phone_groupCallStreamRtmpUrl::class,
          org.Tajgram.tgnet.tl.TL_phone.groupCallStreamRtmpUrl::TLdeserialize, null)

    }

    @Test
    public fun test_001478_phone_GroupParticipants_TL_phone_groupParticipants() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_phone_GroupParticipants.TL_phone_groupParticipants::class,
          org.Tajgram.tgnet.tl.TL_phone.groupParticipants::TLdeserialize, null)

    }

    @Test
    public fun test_001479_phone_JoinAsPeers_TL_phone_joinAsPeers() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_phone_JoinAsPeers.TL_phone_joinAsPeers::class,
          org.Tajgram.tgnet.tl.TL_phone.joinAsPeers::TLdeserialize, null)

    }

    @Test
    public fun test_001480_phone_PhoneCall_TL_phone_phoneCall() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_phone_PhoneCall.TL_phone_phoneCall::class,
          org.Tajgram.tgnet.tl.TL_phone.TL_phone_phoneCall::TLdeserialize, null)

    }

    @Test
    public fun test_001481_photos_Photo_TL_photos_photo() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_photos_Photo.TL_photos_photo::class,
          org.Tajgram.tgnet.TLRPC.TL_photos_photo::TLdeserialize, null)

    }

    @Test
    public fun test_001482_photos_Photos_TL_photos_photos() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_photos_Photos.TL_photos_photos::class,
          org.Tajgram.tgnet.TLRPC.photos_Photos::TLdeserialize, null)

    }

    @Test
    public fun test_001483_photos_Photos_TL_photos_photosSlice() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_photos_Photos.TL_photos_photosSlice::class,
          org.Tajgram.tgnet.TLRPC.photos_Photos::TLdeserialize, null)

    }

    @Test
    public fun test_001484_premium_BoostsList_TL_premium_boostsList() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_premium_BoostsList.TL_premium_boostsList::class,
          org.Tajgram.tgnet.tl.TL_stories.TL_premium_boostsList::TLdeserialize, null)

    }

    @Test
    public fun test_001485_premium_BoostsStatus_TL_premium_boostsStatus() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_premium_BoostsStatus.TL_premium_boostsStatus::class,
          org.Tajgram.tgnet.tl.TL_stories.TL_premium_boostsStatus::TLdeserialize, null)

    }

    @Test
    public fun test_001486_premium_MyBoosts_TL_premium_myBoosts() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_premium_MyBoosts.TL_premium_myBoosts::class,
          org.Tajgram.tgnet.tl.TL_stories.TL_premium_myBoosts::TLdeserialize, null)

    }

    @Test
    public fun test_001487_stats_BroadcastStats_TL_stats_broadcastStats() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_stats_BroadcastStats.TL_stats_broadcastStats::class,
          org.Tajgram.tgnet.tl.TL_stats.TL_broadcastStats::TLdeserialize, null)

    }

    @Test
    public fun test_001488_stats_MegagroupStats_TL_stats_megagroupStats() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_stats_MegagroupStats.TL_stats_megagroupStats::class,
          org.Tajgram.tgnet.tl.TL_stats.TL_megagroupStats::TLdeserialize, null)

    }

    @Test
    public fun test_001489_stats_MessageStats_TL_stats_messageStats() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_stats_MessageStats.TL_stats_messageStats::class,
          org.Tajgram.tgnet.tl.TL_stats.TL_messageStats::TLdeserialize, null)

    }

    @Test
    public fun test_001490_stats_PollStats_TL_stats_pollStats() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_stats_PollStats.TL_stats_pollStats::class,
          org.Tajgram.tgnet.tl.TL_stats.TL_statsPollStats::TLdeserialize, null)

    }

    @Test
    public fun test_001491_stats_PublicForwards_TL_stats_publicForwards() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_stats_PublicForwards.TL_stats_publicForwards::class,
          org.Tajgram.tgnet.tl.TL_stats.TL_publicForwards::TLdeserialize, null)

    }

    @Test
    public fun test_001492_stats_StoryStats_TL_stats_storyStats() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_stats_StoryStats.TL_stats_storyStats::class,
          org.Tajgram.tgnet.tl.TL_stories.TL_stats_storyStats::TLdeserialize, null)

    }

    @Test
    public fun test_001493_stickers_SuggestedShortName_TL_stickers_suggestedShortName() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_stickers_SuggestedShortName.TL_stickers_suggestedShortName::class,
          org.Tajgram.tgnet.TLRPC.TL_stickers_suggestedShortName::TLdeserialize, null)

    }

    @Test
    public fun test_001494_storage_FileType_TL_storage_fileGif() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_storage_FileType.TL_storage_fileGif::class,
          org.Tajgram.tgnet.TLRPC.storage_FileType::TLdeserialize, null)

    }

    @Test
    public fun test_001495_storage_FileType_TL_storage_fileJpeg() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_storage_FileType.TL_storage_fileJpeg::class,
          org.Tajgram.tgnet.TLRPC.storage_FileType::TLdeserialize, null)

    }

    @Test
    public fun test_001496_storage_FileType_TL_storage_fileMov() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_storage_FileType.TL_storage_fileMov::class,
          org.Tajgram.tgnet.TLRPC.storage_FileType::TLdeserialize, null)

    }

    @Test
    public fun test_001497_storage_FileType_TL_storage_fileMp3() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_storage_FileType.TL_storage_fileMp3::class,
          org.Tajgram.tgnet.TLRPC.storage_FileType::TLdeserialize, null)

    }

    @Test
    public fun test_001498_storage_FileType_TL_storage_fileMp4() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_storage_FileType.TL_storage_fileMp4::class,
          org.Tajgram.tgnet.TLRPC.storage_FileType::TLdeserialize, null)

    }

    @Test
    public fun test_001499_storage_FileType_TL_storage_filePartial() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_storage_FileType.TL_storage_filePartial::class,
          org.Tajgram.tgnet.TLRPC.storage_FileType::TLdeserialize, null)

    }

    @Test
    public fun test_001500_storage_FileType_TL_storage_filePdf() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_storage_FileType.TL_storage_filePdf::class,
          org.Tajgram.tgnet.TLRPC.storage_FileType::TLdeserialize, null)

    }

    @Test
    public fun test_001501_storage_FileType_TL_storage_filePng() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_storage_FileType.TL_storage_filePng::class,
          org.Tajgram.tgnet.TLRPC.storage_FileType::TLdeserialize, null)

    }

    @Test
    public fun test_001502_storage_FileType_TL_storage_fileUnknown() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_storage_FileType.TL_storage_fileUnknown::class,
          org.Tajgram.tgnet.TLRPC.storage_FileType::TLdeserialize, null)

    }

    @Test
    public fun test_001503_storage_FileType_TL_storage_fileWebp() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_storage_FileType.TL_storage_fileWebp::class,
          org.Tajgram.tgnet.TLRPC.storage_FileType::TLdeserialize, null)

    }

    @Test
    public fun test_001504_stories_Albums_TL_stories_albums() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_stories_Albums.TL_stories_albums::class,
          org.Tajgram.tgnet.tl.TL_stories.Albums::TLdeserialize, null)

    }

    @Test
    public fun test_001505_stories_Albums_TL_stories_albumsNotModified() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_stories_Albums.TL_stories_albumsNotModified::class,
          org.Tajgram.tgnet.tl.TL_stories.Albums::TLdeserialize, null)

    }

    @Test
    public fun test_001506_stories_AllStories_TL_stories_allStories() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_stories_AllStories.TL_stories_allStories::class,
          org.Tajgram.tgnet.tl.TL_stories.stories_AllStories::TLdeserialize, null)

    }

    @Test
    public fun test_001507_stories_AllStories_TL_stories_allStoriesNotModified() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_stories_AllStories.TL_stories_allStoriesNotModified::class,
          org.Tajgram.tgnet.tl.TL_stories.stories_AllStories::TLdeserialize, null)

    }

    @Test
    public fun test_001508_stories_CanSendStoryCount_TL_stories_canSendStoryCount() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_stories_CanSendStoryCount.TL_stories_canSendStoryCount::class,
          org.Tajgram.tgnet.tl.TL_stories.canSendStoryCount::TLdeserialize, null)

    }

    @Test
    public fun test_001509_stories_FoundStories_TL_stories_foundStories() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_stories_FoundStories.TL_stories_foundStories::class,
          org.Tajgram.tgnet.tl.TL_stories.TL_foundStories::TLdeserialize, null)

    }

    @Test
    public fun test_001510_stories_PeerStories_TL_stories_peerStories() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_stories_PeerStories.TL_stories_peerStories::class,
          org.Tajgram.tgnet.tl.TL_stories.TL_stories_peerStories::TLdeserialize, null)

    }

    @Test
    public fun test_001511_stories_Stories_TL_stories_stories() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_stories_Stories.TL_stories_stories::class,
          org.Tajgram.tgnet.tl.TL_stories.TL_stories_stories::TLdeserialize, null)

    }

    @Test
    public fun test_001512_stories_StoryReactionsList_TL_stories_storyReactionsList() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_stories_StoryReactionsList.TL_stories_storyReactionsList::class,
          org.Tajgram.tgnet.tl.TL_stories.TL_storyReactionsList::TLdeserialize, null)

    }

    @Test
    public fun test_001513_stories_StoryViews_TL_stories_storyViews() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_stories_StoryViews.TL_stories_storyViews::class,
          org.Tajgram.tgnet.tl.TL_stories.TL_stories_storyViews::TLdeserialize, null)

    }

    @Test
    public fun test_001514_stories_StoryViewsList_TL_stories_storyViewsList() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_stories_StoryViewsList.TL_stories_storyViewsList::class,
          org.Tajgram.tgnet.tl.TL_stories.StoryViewsList::TLdeserialize, null)

    }

    @Test
    public fun test_001515_updates_ChannelDifference_TL_updates_channelDifference() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_updates_ChannelDifference.TL_updates_channelDifference::class,
          org.Tajgram.tgnet.TLRPC.updates_ChannelDifference::TLdeserialize, null)

    }

    @Test
    public fun test_001516_updates_ChannelDifference_TL_updates_channelDifferenceEmpty() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_updates_ChannelDifference.TL_updates_channelDifferenceEmpty::class,
          org.Tajgram.tgnet.TLRPC.updates_ChannelDifference::TLdeserialize, null)

    }

    @Test
    public fun test_001517_updates_ChannelDifference_TL_updates_channelDifferenceTooLong() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_updates_ChannelDifference.TL_updates_channelDifferenceTooLong::class,
          org.Tajgram.tgnet.TLRPC.updates_ChannelDifference::TLdeserialize, null)

    }

    @Test
    public fun test_001518_updates_Difference_TL_updates_difference() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_updates_Difference.TL_updates_difference::class,
          org.Tajgram.tgnet.TLRPC.updates_Difference::TLdeserialize, null)

    }

    @Test
    public fun test_001519_updates_Difference_TL_updates_differenceEmpty() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_updates_Difference.TL_updates_differenceEmpty::class,
          org.Tajgram.tgnet.TLRPC.updates_Difference::TLdeserialize, null)

    }

    @Test
    public fun test_001520_updates_Difference_TL_updates_differenceSlice() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_updates_Difference.TL_updates_differenceSlice::class,
          org.Tajgram.tgnet.TLRPC.updates_Difference::TLdeserialize, null)

    }

    @Test
    public fun test_001521_updates_Difference_TL_updates_differenceTooLong() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_updates_Difference.TL_updates_differenceTooLong::class,
          org.Tajgram.tgnet.TLRPC.updates_Difference::TLdeserialize, null)

    }

    @Test
    public fun test_001522_updates_State_TL_updates_state() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_updates_State.TL_updates_state::class,
          org.Tajgram.tgnet.TLRPC.TL_updates_state::TLdeserialize, null)

    }

    @Test
    public fun test_001523_upload_CdnFile_TL_upload_cdnFile() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_upload_CdnFile.TL_upload_cdnFile::class,
          org.Tajgram.tgnet.TLRPC.upload_CdnFile::TLdeserialize, null)

    }

    @Test
    public fun test_001524_upload_CdnFile_TL_upload_cdnFileReuploadNeeded() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_upload_CdnFile.TL_upload_cdnFileReuploadNeeded::class,
          org.Tajgram.tgnet.TLRPC.upload_CdnFile::TLdeserialize, null)

    }

    @Test
    public fun test_001525_upload_File_TL_upload_file() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_upload_File.TL_upload_file::class,
          org.Tajgram.tgnet.TLRPC.upload_File::TLdeserialize, null)

    }

    @Test
    public fun test_001526_upload_File_TL_upload_fileCdnRedirect() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_upload_File.TL_upload_fileCdnRedirect::class,
          org.Tajgram.tgnet.TLRPC.upload_File::TLdeserialize, null)

    }

    @Test
    public fun test_001527_upload_WebFile_TL_upload_webFile() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_upload_WebFile.TL_upload_webFile::class,
          org.Tajgram.tgnet.TLRPC.TL_upload_webFile::TLdeserialize, null)

    }

    @Test
    public fun test_001528_users_SavedMusic_TL_users_savedMusic() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_users_SavedMusic.TL_users_savedMusic::class,
          org.Tajgram.tgnet.TLRPC.SavedMusic::TLdeserialize, null)

    }

    @Test
    public fun test_001529_users_SavedMusic_TL_users_savedMusicNotModified() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_users_SavedMusic.TL_users_savedMusicNotModified::class,
          org.Tajgram.tgnet.TLRPC.SavedMusic::TLdeserialize, null)

    }

    @Test
    public fun test_001530_users_UserFull_TL_users_userFull() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_users_UserFull.TL_users_userFull::class,
          org.Tajgram.tgnet.TLRPC.TL_users_userFull::TLdeserialize, null)

    }

    @Test
    public fun test_001531_users_Users_TL_users_users() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_users_Users.TL_users_users::class,
          org.Tajgram.tgnet.TLRPC.Users::TLdeserialize, null)

    }

    @Test
    public fun test_001532_users_Users_TL_users_usersSlice() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_users_Users.TL_users_usersSlice::class,
          org.Tajgram.tgnet.TLRPC.Users::TLdeserialize, null)

    }
  }

  public class Test_Encrypred : BaseSchemeTest() {
    @Test
    public fun test_001782_DecryptedMessageMedia_TL_decryptedMessageMediaDocument_layer143() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_DecryptedMessageMedia.TL_decryptedMessageMediaDocument_layer143::class,
          org.Tajgram.tgnet.TLRPC.DecryptedMessageMedia::TLdeserialize, null)

    }

    @Test
    public fun test_001964_DecryptedMessage_TL_decryptedMessage_layer73() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_DecryptedMessage.TL_decryptedMessage_layer73::class,
          org.Tajgram.tgnet.TLRPC.DecryptedMessage::TLdeserialize, null)

    }

    @Test
    public fun test_001980_SendMessageAction_TL_sendMessageUploadRoundAction_layer66() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_SendMessageAction.TL_sendMessageUploadRoundAction_layer66::class,
          org.Tajgram.tgnet.TLRPC.SendMessageAction::TLdeserialize, null)

    }

    @Test
    public fun test_002006_DecryptedMessage_TL_decryptedMessage_layer45() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_DecryptedMessage.TL_decryptedMessage_layer45::class,
          org.Tajgram.tgnet.TLRPC.DecryptedMessage::TLdeserialize, null)

    }

    @Test
    public fun test_002007_DecryptedMessageMedia_TL_decryptedMessageMediaDocument_layer45() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_DecryptedMessageMedia.TL_decryptedMessageMediaDocument_layer45::class,
          org.Tajgram.tgnet.TLRPC.DecryptedMessageMedia::TLdeserialize, null)

    }

    @Test
    public fun test_002008_DecryptedMessageMedia_TL_decryptedMessageMediaPhoto_layer45() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_DecryptedMessageMedia.TL_decryptedMessageMediaPhoto_layer45::class,
          org.Tajgram.tgnet.TLRPC.DecryptedMessageMedia::TLdeserialize, null)

    }

    @Test
    public fun test_002009_DecryptedMessageMedia_TL_decryptedMessageMediaVenue_layer45() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_DecryptedMessageMedia.TL_decryptedMessageMediaVenue_layer45::class,
          org.Tajgram.tgnet.TLRPC.DecryptedMessageMedia::TLdeserialize, null)

    }

    @Test
    public fun test_002010_DecryptedMessageMedia_TL_decryptedMessageMediaVideo_layer45() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_DecryptedMessageMedia.TL_decryptedMessageMediaVideo_layer45::class,
          org.Tajgram.tgnet.TLRPC.DecryptedMessageMedia::TLdeserialize, null)

    }

    @Test
    public fun test_002011_DecryptedMessageMedia_TL_decryptedMessageMediaWebPage_layer45() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_DecryptedMessageMedia.TL_decryptedMessageMediaWebPage_layer45::class,
          org.Tajgram.tgnet.TLRPC.DecryptedMessageMedia::TLdeserialize, null)

    }

    @Test
    public
        fun test_002052_DecryptedMessageMedia_TL_decryptedMessageMediaExternalDocument_layer23() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_DecryptedMessageMedia.TL_decryptedMessageMediaExternalDocument_layer23::class,
          org.Tajgram.tgnet.TLRPC.DecryptedMessageMedia::TLdeserialize, null)

    }

    @Test
    public fun test_002062_DecryptedMessageAction_TL_decryptedMessageActionAbortKey_layer20() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_DecryptedMessageAction.TL_decryptedMessageActionAbortKey_layer20::class,
          org.Tajgram.tgnet.TLRPC.DecryptedMessageAction::TLdeserialize, null)

    }

    @Test
    public fun test_002063_DecryptedMessageAction_TL_decryptedMessageActionAcceptKey_layer20() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_DecryptedMessageAction.TL_decryptedMessageActionAcceptKey_layer20::class,
          org.Tajgram.tgnet.TLRPC.DecryptedMessageAction::TLdeserialize, null)

    }

    @Test
    public fun test_002064_DecryptedMessageAction_TL_decryptedMessageActionCommitKey_layer20() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_DecryptedMessageAction.TL_decryptedMessageActionCommitKey_layer20::class,
          org.Tajgram.tgnet.TLRPC.DecryptedMessageAction::TLdeserialize, null)

    }

    @Test
    public fun test_002065_DecryptedMessageAction_TL_decryptedMessageActionNoop_layer20() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_DecryptedMessageAction.TL_decryptedMessageActionNoop_layer20::class,
          org.Tajgram.tgnet.TLRPC.DecryptedMessageAction::TLdeserialize, null)

    }

    @Test
    public fun test_002066_DecryptedMessageAction_TL_decryptedMessageActionRequestKey_layer20() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_DecryptedMessageAction.TL_decryptedMessageActionRequestKey_layer20::class,
          org.Tajgram.tgnet.TLRPC.DecryptedMessageAction::TLdeserialize, null)

    }

    @Test
    public fun test_002067_DecryptedMessage_TL_decryptedMessage_layer17() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_DecryptedMessage.TL_decryptedMessage_layer17::class,
          org.Tajgram.tgnet.TLRPC.DecryptedMessage::TLdeserialize, null)

    }

    @Test
    public fun test_002068_DecryptedMessage_TL_decryptedMessageService_layer17() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_DecryptedMessage.TL_decryptedMessageService_layer17::class,
          org.Tajgram.tgnet.TLRPC.DecryptedMessage::TLdeserialize, null)

    }

    @Test
    public fun test_002069_DecryptedMessageAction_TL_decryptedMessageActionNotifyLayer_layer17() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_DecryptedMessageAction.TL_decryptedMessageActionNotifyLayer_layer17::class,
          org.Tajgram.tgnet.TLRPC.DecryptedMessageAction::TLdeserialize, null)

    }

    @Test
    public fun test_002070_DecryptedMessageAction_TL_decryptedMessageActionResend_layer17() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_DecryptedMessageAction.TL_decryptedMessageActionResend_layer17::class,
          org.Tajgram.tgnet.TLRPC.DecryptedMessageAction::TLdeserialize, null)

    }

    @Test
    public fun test_002071_DecryptedMessageAction_TL_decryptedMessageActionTyping_layer17() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_DecryptedMessageAction.TL_decryptedMessageActionTyping_layer17::class,
          org.Tajgram.tgnet.TLRPC.DecryptedMessageAction::TLdeserialize, null)

    }

    @Test
    public fun test_002072_DecryptedMessageLayer_TL_decryptedMessageLayer_layer17() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_DecryptedMessageLayer.TL_decryptedMessageLayer_layer17::class,
          org.Tajgram.tgnet.TLRPC.TL_decryptedMessageLayer::TLdeserialize, null)

    }

    @Test
    public fun test_002073_DecryptedMessageMedia_TL_decryptedMessageMediaAudio_layer17() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_DecryptedMessageMedia.TL_decryptedMessageMediaAudio_layer17::class,
          org.Tajgram.tgnet.TLRPC.DecryptedMessageMedia::TLdeserialize, null)

    }

    @Test
    public fun test_002074_DecryptedMessageMedia_TL_decryptedMessageMediaVideo_layer17() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_DecryptedMessageMedia.TL_decryptedMessageMediaVideo_layer17::class,
          org.Tajgram.tgnet.TLRPC.DecryptedMessageMedia::TLdeserialize, null)

    }

    @Test
    public fun test_002075_SendMessageAction_TL_sendMessageUploadAudioAction_layer17() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_SendMessageAction.TL_sendMessageUploadAudioAction_layer17::class,
          org.Tajgram.tgnet.TLRPC.SendMessageAction::TLdeserialize, null)

    }

    @Test
    public fun test_002076_SendMessageAction_TL_sendMessageUploadDocumentAction_layer17() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_SendMessageAction.TL_sendMessageUploadDocumentAction_layer17::class,
          org.Tajgram.tgnet.TLRPC.SendMessageAction::TLdeserialize, null)

    }

    @Test
    public fun test_002077_SendMessageAction_TL_sendMessageUploadPhotoAction_layer17() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_SendMessageAction.TL_sendMessageUploadPhotoAction_layer17::class,
          org.Tajgram.tgnet.TLRPC.SendMessageAction::TLdeserialize, null)

    }

    @Test
    public fun test_002078_SendMessageAction_TL_sendMessageUploadVideoAction_layer17() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_SendMessageAction.TL_sendMessageUploadVideoAction_layer17::class,
          org.Tajgram.tgnet.TLRPC.SendMessageAction::TLdeserialize, null)

    }

    @Test
    public fun test_002089_DecryptedMessage_TL_decryptedMessage_layer8() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_DecryptedMessage.TL_decryptedMessage_layer8::class,
          org.Tajgram.tgnet.TLRPC.DecryptedMessage::TLdeserialize, null)

    }

    @Test
    public fun test_002090_DecryptedMessage_TL_decryptedMessageService_layer8() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_DecryptedMessage.TL_decryptedMessageService_layer8::class,
          org.Tajgram.tgnet.TLRPC.DecryptedMessage::TLdeserialize, null)

    }

    @Test
    public fun test_002091_DecryptedMessageAction_TL_decryptedMessageActionDeleteMessages_layer8() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_DecryptedMessageAction.TL_decryptedMessageActionDeleteMessages_layer8::class,
          org.Tajgram.tgnet.TLRPC.DecryptedMessageAction::TLdeserialize, null)

    }

    @Test
    public fun test_002092_DecryptedMessageAction_TL_decryptedMessageActionFlushHistory_layer8() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_DecryptedMessageAction.TL_decryptedMessageActionFlushHistory_layer8::class,
          org.Tajgram.tgnet.TLRPC.DecryptedMessageAction::TLdeserialize, null)

    }

    @Test
    public fun test_002093_DecryptedMessageAction_TL_decryptedMessageActionReadMessages_layer8() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_DecryptedMessageAction.TL_decryptedMessageActionReadMessages_layer8::class,
          org.Tajgram.tgnet.TLRPC.DecryptedMessageAction::TLdeserialize, null)

    }

    @Test
    public
        fun test_002094_DecryptedMessageAction_TL_decryptedMessageActionScreenshotMessages_layer8() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_DecryptedMessageAction.TL_decryptedMessageActionScreenshotMessages_layer8::class,
          org.Tajgram.tgnet.TLRPC.DecryptedMessageAction::TLdeserialize, null)

    }

    @Test
    public fun test_002095_DecryptedMessageAction_TL_decryptedMessageActionSetMessageTTL_layer8() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_DecryptedMessageAction.TL_decryptedMessageActionSetMessageTTL_layer8::class,
          org.Tajgram.tgnet.TLRPC.DecryptedMessageAction::TLdeserialize, null)

    }

    @Test
    public fun test_002096_DecryptedMessageMedia_TL_decryptedMessageMediaAudio_layer8() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_DecryptedMessageMedia.TL_decryptedMessageMediaAudio_layer8::class,
          org.Tajgram.tgnet.TLRPC.DecryptedMessageMedia::TLdeserialize, null)

    }

    @Test
    public fun test_002097_DecryptedMessageMedia_TL_decryptedMessageMediaContact_layer8() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_DecryptedMessageMedia.TL_decryptedMessageMediaContact_layer8::class,
          org.Tajgram.tgnet.TLRPC.DecryptedMessageMedia::TLdeserialize, null)

    }

    @Test
    public fun test_002098_DecryptedMessageMedia_TL_decryptedMessageMediaDocument_layer8() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_DecryptedMessageMedia.TL_decryptedMessageMediaDocument_layer8::class,
          org.Tajgram.tgnet.TLRPC.DecryptedMessageMedia::TLdeserialize, null)

    }

    @Test
    public fun test_002099_DecryptedMessageMedia_TL_decryptedMessageMediaEmpty_layer8() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_DecryptedMessageMedia.TL_decryptedMessageMediaEmpty_layer8::class,
          org.Tajgram.tgnet.TLRPC.DecryptedMessageMedia::TLdeserialize, null)

    }

    @Test
    public fun test_002100_DecryptedMessageMedia_TL_decryptedMessageMediaGeoPoint_layer8() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_DecryptedMessageMedia.TL_decryptedMessageMediaGeoPoint_layer8::class,
          org.Tajgram.tgnet.TLRPC.DecryptedMessageMedia::TLdeserialize, null)

    }

    @Test
    public fun test_002101_DecryptedMessageMedia_TL_decryptedMessageMediaPhoto_layer8() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_DecryptedMessageMedia.TL_decryptedMessageMediaPhoto_layer8::class,
          org.Tajgram.tgnet.TLRPC.DecryptedMessageMedia::TLdeserialize, null)

    }

    @Test
    public fun test_002102_DecryptedMessageMedia_TL_decryptedMessageMediaVideo_layer8() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_DecryptedMessageMedia.TL_decryptedMessageMediaVideo_layer8::class,
          org.Tajgram.tgnet.TLRPC.DecryptedMessageMedia::TLdeserialize, null)

    }
  }

  public class Test_Legacy : BaseSchemeTest() {
    /**
     * ForumTopic-DraftMessage
     */
    @Test
    public fun test_001533_DraftMessage_TL_draftMessage_layer226() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_DraftMessage.TL_draftMessage_layer226::class,
          org.Tajgram.tgnet.TLRPC.DraftMessage::TLdeserialize, 226)

    }

    /**
     * Message
     */
    @Test
    public fun test_001534_Message_TL_message_layer226() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Message.TL_message_layer226::class,
          org.Tajgram.tgnet.TLRPC.Message::TLdeserialize, 226)

    }

    /**
     * Message-MessageMedia-WebPage-Page-PageBlock
     * StoryItem-MessageMedia-WebPage-Page-PageBlock
     * UserFull-PeerStories-StoryItem-MessageMedia-WebPage-Page-PageBlock
     * ChatFull-PeerStories-StoryItem-MessageMedia-WebPage-Page-PageBlock
     * StarsTransaction-MessageMedia-WebPage-Page-PageBlock
     * ForumTopic-DraftMessage-InputMedia-Poll-PollAnswer-MessageMedia-WebPage-Page-PageBlock
     */
    @Test
    public fun test_001535_PageBlock_TL_pageBlockOrderedList_layer226() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_PageBlock.TL_pageBlockOrderedList_layer226::class,
          org.Tajgram.tgnet.tl.TL_iv.PageBlock::TLdeserialize, 226)

    }

    /**
     * Message-MessageMedia-WebPage-Page-PageBlock-PageListItem
     * StoryItem-MessageMedia-WebPage-Page-PageBlock-PageListItem
     * UserFull-PeerStories-StoryItem-MessageMedia-WebPage-Page-PageBlock-PageListItem
     * ChatFull-PeerStories-StoryItem-MessageMedia-WebPage-Page-PageBlock-PageListItem
     * StarsTransaction-MessageMedia-WebPage-Page-PageBlock-PageListItem
     * ForumTopic-DraftMessage-InputMedia-Poll-PollAnswer-MessageMedia-WebPage-Page-PageBlock-PageListItem
     */
    @Test
    public fun test_001536_PageListItem_TL_pageListItemBlocks_layer226() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_PageListItem.TL_pageListItemBlocks_layer226::class,
          org.Tajgram.tgnet.tl.TL_iv.PageListItem::TLdeserialize, 226)

    }

    /**
     * Message-MessageMedia-WebPage-Page-PageBlock-PageListItem
     * StoryItem-MessageMedia-WebPage-Page-PageBlock-PageListItem
     * UserFull-PeerStories-StoryItem-MessageMedia-WebPage-Page-PageBlock-PageListItem
     * ChatFull-PeerStories-StoryItem-MessageMedia-WebPage-Page-PageBlock-PageListItem
     * StarsTransaction-MessageMedia-WebPage-Page-PageBlock-PageListItem
     * ForumTopic-DraftMessage-InputMedia-Poll-PollAnswer-MessageMedia-WebPage-Page-PageBlock-PageListItem
     */
    @Test
    public fun test_001537_PageListItem_TL_pageListItemText_layer226() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_PageListItem.TL_pageListItemText_layer226::class,
          org.Tajgram.tgnet.tl.TL_iv.PageListItem::TLdeserialize, 226)

    }

    /**
     * Message-MessageMedia-WebPage-Page-PageBlock-PageListOrderedItem
     * StoryItem-MessageMedia-WebPage-Page-PageBlock-PageListOrderedItem
     * UserFull-PeerStories-StoryItem-MessageMedia-WebPage-Page-PageBlock-PageListOrderedItem
     * ChatFull-PeerStories-StoryItem-MessageMedia-WebPage-Page-PageBlock-PageListOrderedItem
     * StarsTransaction-MessageMedia-WebPage-Page-PageBlock-PageListOrderedItem
     * ForumTopic-DraftMessage-InputMedia-Poll-PollAnswer-MessageMedia-WebPage-Page-PageBlock-PageListOrderedItem
     */
    @Test
    public fun test_001538_PageListOrderedItem_TL_pageListOrderedItemBlocks_layer226() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_PageListOrderedItem.TL_pageListOrderedItemBlocks_layer226::class,
          org.Tajgram.tgnet.tl.TL_iv.PageListOrderedItem::TLdeserialize, 226)

    }

    /**
     * Message-MessageMedia-WebPage-Page-PageBlock-PageListOrderedItem
     * StoryItem-MessageMedia-WebPage-Page-PageBlock-PageListOrderedItem
     * UserFull-PeerStories-StoryItem-MessageMedia-WebPage-Page-PageBlock-PageListOrderedItem
     * ChatFull-PeerStories-StoryItem-MessageMedia-WebPage-Page-PageBlock-PageListOrderedItem
     * StarsTransaction-MessageMedia-WebPage-Page-PageBlock-PageListOrderedItem
     * ForumTopic-DraftMessage-InputMedia-Poll-PollAnswer-MessageMedia-WebPage-Page-PageBlock-PageListOrderedItem
     */
    @Test
    public fun test_001539_PageListOrderedItem_TL_pageListOrderedItemText_layer226() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_PageListOrderedItem.TL_pageListOrderedItemText_layer226::class,
          org.Tajgram.tgnet.tl.TL_iv.PageListOrderedItem::TLdeserialize, 226)

    }

    /**
     * ChatFull
     */
    @Test
    public fun test_001540_ChatFull_TL_channelFull_layer225() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ChatFull.TL_channelFull_layer225::class,
          org.Tajgram.tgnet.TLRPC.ChatFull::TLdeserialize, 225)

    }

    /**
     * InputStorePaymentPurpose
     */
    @Test
    public fun test_001541_InputStorePaymentPurpose_TL_inputStorePaymentAuthCode_layer224() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputStorePaymentPurpose.TL_inputStorePaymentAuthCode_layer224::class,
          org.Tajgram.tgnet.TLRPC.InputStorePaymentPurpose::TLdeserialize, 224)

    }

    /**
     * Message
     */
    @Test
    public fun test_001542_Message_TL_message_layer224() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Message.TL_message_layer224::class,
          org.Tajgram.tgnet.TLRPC.Message::TLdeserialize, 224)

    }

    /**
     * Message-MessageMedia-Poll
     * StoryItem-MessageMedia-Poll
     * UserFull-PeerStories-StoryItem-MessageMedia-Poll
     * ChatFull-PeerStories-StoryItem-MessageMedia-Poll
     * StarsTransaction-MessageMedia-Poll
     * ForumTopic-DraftMessage-InputMedia-Poll
     */
    @Test
    public fun test_001543_Poll_TL_poll_layer224() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Poll.TL_poll_layer224::class,
          org.Tajgram.tgnet.TLRPC.Poll::TLdeserialize, 224)

    }

    /**
     * ForumTopic
     */
    @Test
    public fun test_001544_ForumTopic_TL_forumTopic_layer223() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ForumTopic.TL_forumTopic_layer223::class,
          org.Tajgram.tgnet.TLRPC.ForumTopic::TLdeserialize, 223)

    }

    /**
     * ForumTopic-DraftMessage-InputMedia
     */
    @Test
    public fun test_001545_InputMedia_TL_inputMediaPhoto_layer223() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputMedia.TL_inputMediaPhoto_layer223::class,
          org.Tajgram.tgnet.TLRPC.InputMedia::TLdeserialize, 223)

    }

    /**
     * ForumTopic-DraftMessage-InputMedia
     */
    @Test
    public fun test_001546_InputMedia_TL_inputMediaPoll_layer223() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputMedia.TL_inputMediaPoll_layer223::class,
          org.Tajgram.tgnet.TLRPC.InputMedia::TLdeserialize, 223)

    }

    /**
     * ForumTopic-DraftMessage-InputMedia
     */
    @Test
    public fun test_001547_InputMedia_TL_inputMediaUploadedPhoto_layer223() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputMedia.TL_inputMediaUploadedPhoto_layer223::class,
          org.Tajgram.tgnet.TLRPC.InputMedia::TLdeserialize, 223)

    }

    /**
     * ForumTopic-DraftMessage-InputReplyTo
     */
    @Test
    public fun test_001548_InputReplyTo_TL_inputReplyToMessage_layer223() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputReplyTo.TL_inputReplyToMessage_layer223::class,
          org.Tajgram.tgnet.TLRPC.InputReplyTo::TLdeserialize, 223)

    }

    /**
     * Message-MessageMedia
     * StoryItem-MessageMedia
     * UserFull-PeerStories-StoryItem-MessageMedia
     * ChatFull-PeerStories-StoryItem-MessageMedia
     * StarsTransaction-MessageMedia
     */
    @Test
    public fun test_001549_MessageMedia_TL_messageMediaPhoto_layer223() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageMedia.TL_messageMediaPhoto_layer223::class,
          org.Tajgram.tgnet.TLRPC.MessageMedia::TLdeserialize, 223)

    }

    /**
     * Message-MessageMedia
     * StoryItem-MessageMedia
     * UserFull-PeerStories-StoryItem-MessageMedia
     * ChatFull-PeerStories-StoryItem-MessageMedia
     * StarsTransaction-MessageMedia
     */
    @Test
    public fun test_001550_MessageMedia_TL_messageMediaPoll_layer223() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageMedia.TL_messageMediaPoll_layer223::class,
          org.Tajgram.tgnet.TLRPC.MessageMedia::TLdeserialize, 223)

    }

    /**
     * Message-MessageReplyHeader
     */
    @Test
    public fun test_001551_MessageReplyHeader_TL_messageReplyHeader_layer223() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageReplyHeader.TL_messageReplyHeader_layer223::class,
          org.Tajgram.tgnet.TLRPC.MessageReplyHeader::TLdeserialize, 223)

    }

    /**
     * Message-MessageMedia-Poll
     * StoryItem-MessageMedia-Poll
     * UserFull-PeerStories-StoryItem-MessageMedia-Poll
     * ChatFull-PeerStories-StoryItem-MessageMedia-Poll
     * StarsTransaction-MessageMedia-Poll
     * ForumTopic-DraftMessage-InputMedia-Poll
     */
    @Test
    public fun test_001552_Poll_TL_poll_layer223() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Poll.TL_poll_layer223::class,
          org.Tajgram.tgnet.TLRPC.Poll::TLdeserialize, 223)

    }

    /**
     * Message-MessageMedia-Poll-PollAnswer
     * StoryItem-MessageMedia-Poll-PollAnswer
     * UserFull-PeerStories-StoryItem-MessageMedia-Poll-PollAnswer
     * ChatFull-PeerStories-StoryItem-MessageMedia-Poll-PollAnswer
     * StarsTransaction-MessageMedia-Poll-PollAnswer
     * ForumTopic-DraftMessage-InputMedia-Poll-PollAnswer
     */
    @Test
    public fun test_001553_PollAnswer_TL_pollAnswer_layer223() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_PollAnswer.TL_pollAnswer_layer223::class,
          org.Tajgram.tgnet.TLRPC.PollAnswer::TLdeserialize, 223)

    }

    /**
     * Message-MessageMedia-PollResults-PollAnswerVoters
     * StoryItem-MessageMedia-PollResults-PollAnswerVoters
     * UserFull-PeerStories-StoryItem-MessageMedia-PollResults-PollAnswerVoters
     * ChatFull-PeerStories-StoryItem-MessageMedia-PollResults-PollAnswerVoters
     * StarsTransaction-MessageMedia-PollResults-PollAnswerVoters
     */
    @Test
    public fun test_001554_PollAnswerVoters_TL_pollAnswerVoters_layer223() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_PollAnswerVoters.TL_pollAnswerVoters_layer223::class,
          org.Tajgram.tgnet.TLRPC.PollAnswerVoters::TLdeserialize, 223)

    }

    /**
     * Message-MessageMedia-PollResults
     * StoryItem-MessageMedia-PollResults
     * UserFull-PeerStories-StoryItem-MessageMedia-PollResults
     * ChatFull-PeerStories-StoryItem-MessageMedia-PollResults
     * StarsTransaction-MessageMedia-PollResults
     */
    @Test
    public fun test_001555_PollResults_TL_pollResults_layer223() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_PollResults.TL_pollResults_layer223::class,
          org.Tajgram.tgnet.TLRPC.PollResults::TLdeserialize, 223)

    }

    /**
     * Message-MessageMedia-StoryItem
     * StoryItem
     * UserFull-PeerStories-StoryItem
     * ChatFull-PeerStories-StoryItem
     * StarsTransaction-MessageMedia-StoryItem
     */
    @Test
    public fun test_001556_StoryItem_TL_storyItem_layer223() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_StoryItem.TL_storyItem_layer223::class,
          org.Tajgram.tgnet.tl.TL_stories.StoryItem::TLdeserialize, 223)

    }

    /**
     * UserFull
     */
    @Test
    public fun test_001557_UserFull_TL_userFull_layer223() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_UserFull.TL_userFull_layer223::class,
          org.Tajgram.tgnet.TLRPC.UserFull::TLdeserialize, 223)

    }

    /**
     * ChatFull-ChatParticipants-ChatParticipant
     */
    @Test
    public fun test_001558_ChatParticipant_TL_chatParticipant_layer222() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ChatParticipant.TL_chatParticipant_layer222::class,
          org.Tajgram.tgnet.TLRPC.ChatParticipant::TLdeserialize, 222)

    }

    /**
     * ChatFull-ChatParticipants-ChatParticipant
     */
    @Test
    public fun test_001559_ChatParticipant_TL_chatParticipantAdmin_layer222() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ChatParticipant.TL_chatParticipantAdmin_layer222::class,
          org.Tajgram.tgnet.TLRPC.ChatParticipant::TLdeserialize, 222)

    }

    /**
     * ChatFull-ChatParticipants-ChatParticipant
     */
    @Test
    public fun test_001560_ChatParticipant_TL_chatParticipantCreator_layer222() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ChatParticipant.TL_chatParticipantCreator_layer222::class,
          org.Tajgram.tgnet.TLRPC.ChatParticipant::TLdeserialize, 222)

    }

    /**
     * Message
     */
    @Test
    public fun test_001561_Message_TL_message_layer222() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Message.TL_message_layer222::class,
          org.Tajgram.tgnet.TLRPC.Message::TLdeserialize, 222)

    }

    /**
     * Message-ReplyMarkup-KeyboardButtonRow-KeyboardButton
     */
    @Test
    public fun test_001562_KeyboardButton_TL_inputKeyboardButtonUrlAuth_layer221() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_KeyboardButton.TL_inputKeyboardButtonUrlAuth_layer221::class,
          org.Tajgram.tgnet.TLRPC.KeyboardButton::TLdeserialize, 221)

    }

    /**
     * Message-ReplyMarkup-KeyboardButtonRow-KeyboardButton
     */
    @Test
    public fun test_001563_KeyboardButton_TL_inputKeyboardButtonUserProfile_layer221() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_KeyboardButton.TL_inputKeyboardButtonUserProfile_layer221::class,
          org.Tajgram.tgnet.TLRPC.KeyboardButton::TLdeserialize, 221)

    }

    /**
     * Message-ReplyMarkup-KeyboardButtonRow-KeyboardButton
     */
    @Test
    public fun test_001564_KeyboardButton_TL_keyboardButton_layer221() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_KeyboardButton.TL_keyboardButton_layer221::class,
          org.Tajgram.tgnet.TLRPC.KeyboardButton::TLdeserialize, 221)

    }

    /**
     * Message-ReplyMarkup-KeyboardButtonRow-KeyboardButton
     */
    @Test
    public fun test_001565_KeyboardButton_TL_keyboardButtonBuy_layer221() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_KeyboardButton.TL_keyboardButtonBuy_layer221::class,
          org.Tajgram.tgnet.TLRPC.KeyboardButton::TLdeserialize, 221)

    }

    /**
     * Message-ReplyMarkup-KeyboardButtonRow-KeyboardButton
     */
    @Test
    public fun test_001566_KeyboardButton_TL_keyboardButtonCallback_layer221() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_KeyboardButton.TL_keyboardButtonCallback_layer221::class,
          org.Tajgram.tgnet.TLRPC.KeyboardButton::TLdeserialize, 221)

    }

    /**
     * Message-ReplyMarkup-KeyboardButtonRow-KeyboardButton
     */
    @Test
    public fun test_001567_KeyboardButton_TL_keyboardButtonCopy_layer221() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_KeyboardButton.TL_keyboardButtonCopy_layer221::class,
          org.Tajgram.tgnet.TLRPC.KeyboardButton::TLdeserialize, 221)

    }

    /**
     * Message-ReplyMarkup-KeyboardButtonRow-KeyboardButton
     */
    @Test
    public fun test_001568_KeyboardButton_TL_keyboardButtonGame_layer221() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_KeyboardButton.TL_keyboardButtonGame_layer221::class,
          org.Tajgram.tgnet.TLRPC.KeyboardButton::TLdeserialize, 221)

    }

    /**
     * Message-ReplyMarkup-KeyboardButtonRow-KeyboardButton
     */
    @Test
    public fun test_001569_KeyboardButton_TL_keyboardButtonRequestGeoLocation_layer221() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_KeyboardButton.TL_keyboardButtonRequestGeoLocation_layer221::class,
          org.Tajgram.tgnet.TLRPC.KeyboardButton::TLdeserialize, 221)

    }

    /**
     * Message-ReplyMarkup-KeyboardButtonRow-KeyboardButton
     */
    @Test
    public fun test_001570_KeyboardButton_TL_keyboardButtonRequestPeer_layer221() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_KeyboardButton.TL_keyboardButtonRequestPeer_layer221::class,
          org.Tajgram.tgnet.TLRPC.KeyboardButton::TLdeserialize, 221)

    }

    /**
     * Message-ReplyMarkup-KeyboardButtonRow-KeyboardButton
     */
    @Test
    public fun test_001571_KeyboardButton_TL_keyboardButtonRequestPhone_layer221() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_KeyboardButton.TL_keyboardButtonRequestPhone_layer221::class,
          org.Tajgram.tgnet.TLRPC.KeyboardButton::TLdeserialize, 221)

    }

    /**
     * Message-ReplyMarkup-KeyboardButtonRow-KeyboardButton
     */
    @Test
    public fun test_001572_KeyboardButton_TL_keyboardButtonRequestPoll_layer221() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_KeyboardButton.TL_keyboardButtonRequestPoll_layer221::class,
          org.Tajgram.tgnet.TLRPC.KeyboardButton::TLdeserialize, 221)

    }

    /**
     * Message-ReplyMarkup-KeyboardButtonRow-KeyboardButton
     */
    @Test
    public fun test_001573_KeyboardButton_TL_keyboardButtonSimpleWebView_layer221() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_KeyboardButton.TL_keyboardButtonSimpleWebView_layer221::class,
          org.Tajgram.tgnet.TLRPC.KeyboardButton::TLdeserialize, 221)

    }

    /**
     * Message-ReplyMarkup-KeyboardButtonRow-KeyboardButton
     */
    @Test
    public fun test_001574_KeyboardButton_TL_keyboardButtonSwitchInline_layer221() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_KeyboardButton.TL_keyboardButtonSwitchInline_layer221::class,
          org.Tajgram.tgnet.TLRPC.KeyboardButton::TLdeserialize, 221)

    }

    /**
     * Message-ReplyMarkup-KeyboardButtonRow-KeyboardButton
     */
    @Test
    public fun test_001575_KeyboardButton_TL_keyboardButtonUrl_layer221() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_KeyboardButton.TL_keyboardButtonUrl_layer221::class,
          org.Tajgram.tgnet.TLRPC.KeyboardButton::TLdeserialize, 221)

    }

    /**
     * Message-ReplyMarkup-KeyboardButtonRow-KeyboardButton
     */
    @Test
    public fun test_001576_KeyboardButton_TL_keyboardButtonUrlAuth_layer221() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_KeyboardButton.TL_keyboardButtonUrlAuth_layer221::class,
          org.Tajgram.tgnet.TLRPC.KeyboardButton::TLdeserialize, 221)

    }

    /**
     * Message-ReplyMarkup-KeyboardButtonRow-KeyboardButton
     */
    @Test
    public fun test_001577_KeyboardButton_TL_keyboardButtonUserProfile_layer221() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_KeyboardButton.TL_keyboardButtonUserProfile_layer221::class,
          org.Tajgram.tgnet.TLRPC.KeyboardButton::TLdeserialize, 221)

    }

    /**
     * Message-ReplyMarkup-KeyboardButtonRow-KeyboardButton
     */
    @Test
    public fun test_001578_KeyboardButton_TL_keyboardButtonWebView_layer221() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_KeyboardButton.TL_keyboardButtonWebView_layer221::class,
          org.Tajgram.tgnet.TLRPC.KeyboardButton::TLdeserialize, 221)

    }

    /**
     * Message-MessageAction
     */
    @Test
    public fun test_001579_MessageAction_TL_messageActionStarGiftUnique_layer221() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageAction.TL_messageActionStarGiftUnique_layer221::class,
          org.Tajgram.tgnet.TLRPC.MessageAction::TLdeserialize, 221)

    }

    /**
     * SavedStarGift
     */
    @Test
    public fun test_001580_SavedStarGift_TL_savedStarGift_layer221() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_SavedStarGift.TL_savedStarGift_layer221::class,
          org.Tajgram.tgnet.tl.TL_stars.SavedStarGift::TLdeserialize, 221)

    }

    /**
     * Message-MessageAction-StarGift
     * StoryItem-MessageMedia-WebPage-WebPageAttribute-StarGift
     * UserFull-ChatTheme-StarGift
     * ChatFull-PeerStories-StoryItem-MessageMedia-WebPage-WebPageAttribute-StarGift
     * StarsTransaction-StarGift
     * StarGift
     * SavedStarGift-StarGift
     */
    @Test
    public fun test_001581_StarGift_TL_starGiftUnique_layer221() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_StarGift.TL_starGiftUnique_layer221::class,
          org.Tajgram.tgnet.tl.TL_stars.StarGift::TLdeserialize, 221)

    }

    /**
     * Message-MessageAction-StarGift-StarGiftAttribute
     * StoryItem-MessageMedia-WebPage-WebPageAttribute-StarGift-StarGiftAttribute
     * UserFull-ChatTheme-StarGift-StarGiftAttribute
     * ChatFull-PeerStories-StoryItem-MessageMedia-WebPage-WebPageAttribute-StarGift-StarGiftAttribute
     * StarsTransaction-StarGift-StarGiftAttribute
     * StarGift-StarGiftAttribute
     * SavedStarGift-StarGift-StarGiftAttribute
     */
    @Test
    public fun test_001582_StarGiftAttribute_TL_starGiftAttributeBackdrop_layer221() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_StarGiftAttribute.TL_starGiftAttributeBackdrop_layer221::class,
          org.Tajgram.tgnet.tl.TL_stars.StarGiftAttribute::TLdeserialize, 221)

    }

    /**
     * Message-MessageAction-StarGift-StarGiftAttribute
     * StoryItem-MessageMedia-WebPage-WebPageAttribute-StarGift-StarGiftAttribute
     * UserFull-ChatTheme-StarGift-StarGiftAttribute
     * ChatFull-PeerStories-StoryItem-MessageMedia-WebPage-WebPageAttribute-StarGift-StarGiftAttribute
     * StarsTransaction-StarGift-StarGiftAttribute
     * StarGift-StarGiftAttribute
     * SavedStarGift-StarGift-StarGiftAttribute
     */
    @Test
    public fun test_001583_StarGiftAttribute_TL_starGiftAttributeModel_layer221() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_StarGiftAttribute.TL_starGiftAttributeModel_layer221::class,
          org.Tajgram.tgnet.tl.TL_stars.StarGiftAttribute::TLdeserialize, 221)

    }

    /**
     * Message-MessageAction-StarGift-StarGiftAttribute
     * StoryItem-MessageMedia-WebPage-WebPageAttribute-StarGift-StarGiftAttribute
     * UserFull-ChatTheme-StarGift-StarGiftAttribute
     * ChatFull-PeerStories-StoryItem-MessageMedia-WebPage-WebPageAttribute-StarGift-StarGiftAttribute
     * StarsTransaction-StarGift-StarGiftAttribute
     * StarGift-StarGiftAttribute
     * SavedStarGift-StarGift-StarGiftAttribute
     */
    @Test
    public fun test_001584_StarGiftAttribute_TL_starGiftAttributePattern_layer221() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_StarGiftAttribute.TL_starGiftAttributePattern_layer221::class,
          org.Tajgram.tgnet.tl.TL_stars.StarGiftAttribute::TLdeserialize, 221)

    }

    /**
     * Message
     */
    @Test
    public fun test_001585_Message_TL_message_layer220() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Message.TL_message_layer220::class,
          org.Tajgram.tgnet.TLRPC.Message::TLdeserialize, 220)

    }

    /**
     * Message-MessageAction
     */
    @Test
    public fun test_001586_MessageAction_TL_messageActionChatDeletePhoto_layer220() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageAction.TL_messageActionChatDeletePhoto_layer220::class,
          org.Tajgram.tgnet.TLRPC.MessageAction::TLdeserialize, 220)

    }

    /**
     * Message-MessageMedia
     * StoryItem-MessageMedia
     * UserFull-PeerStories-StoryItem-MessageMedia
     * ChatFull-PeerStories-StoryItem-MessageMedia
     * StarsTransaction-MessageMedia
     */
    @Test
    public fun test_001587_MessageMedia_TL_messageMediaDice_layer220() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageMedia.TL_messageMediaDice_layer220::class,
          org.Tajgram.tgnet.TLRPC.MessageMedia::TLdeserialize, 220)

    }

    /**
     * Message-MessageAction
     */
    @Test
    public fun test_001588_MessageAction_TL_messageActionStarGift_layer219() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageAction.TL_messageActionStarGift_layer219::class,
          org.Tajgram.tgnet.TLRPC.MessageAction::TLdeserialize, 219)

    }

    /**
     * SavedStarGift
     */
    @Test
    public fun test_001589_SavedStarGift_TL_savedStarGift_layer219() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_SavedStarGift.TL_savedStarGift_layer219::class,
          org.Tajgram.tgnet.tl.TL_stars.SavedStarGift::TLdeserialize, 219)

    }

    /**
     * Message-MessageAction-StarGift
     * StoryItem-MessageMedia-WebPage-WebPageAttribute-StarGift
     * UserFull-ChatTheme-StarGift
     * ChatFull-PeerStories-StoryItem-MessageMedia-WebPage-WebPageAttribute-StarGift
     * StarsTransaction-StarGift
     * StarGift
     * SavedStarGift-StarGift
     */
    @Test
    public fun test_001590_StarGift_TL_starGift_layer219() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_StarGift.TL_starGift_layer219::class,
          org.Tajgram.tgnet.tl.TL_stars.StarGift::TLdeserialize, 219)

    }

    /**
     * Message-MessageAction-StarGift
     * StoryItem-MessageMedia-WebPage-WebPageAttribute-StarGift
     * UserFull-ChatTheme-StarGift
     * ChatFull-PeerStories-StoryItem-MessageMedia-WebPage-WebPageAttribute-StarGift
     * StarsTransaction-StarGift
     * StarGift
     * SavedStarGift-StarGift
     */
    @Test
    public fun test_001591_StarGift_TL_starGiftUnique_layer219() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_StarGift.TL_starGiftUnique_layer219::class,
          org.Tajgram.tgnet.tl.TL_stars.StarGift::TLdeserialize, 219)

    }

    /**
     * Message-MessageMedia-WebPage-WebPageAttribute
     * StoryItem-MessageMedia-WebPage-WebPageAttribute
     * UserFull-PeerStories-StoryItem-MessageMedia-WebPage-WebPageAttribute
     * ChatFull-PeerStories-StoryItem-MessageMedia-WebPage-WebPageAttribute
     * StarsTransaction-MessageMedia-WebPage-WebPageAttribute
     */
    @Test
    public fun test_001592_WebPageAttribute_TL_webPageAttributeStarGiftAuction_layer219() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_WebPageAttribute.TL_webPageAttributeStarGiftAuction_layer219::class,
          org.Tajgram.tgnet.TLRPC.WebPageAttribute::TLdeserialize, 219)

    }

    /**
     * Message-MessageAction
     */
    @Test
    public fun test_001593_MessageAction_TL_messageActionStarGift_layer217() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageAction.TL_messageActionStarGift_layer217::class,
          org.Tajgram.tgnet.TLRPC.MessageAction::TLdeserialize, 217)

    }

    /**
     * Message-MessageAction-StarGift
     * StoryItem-MessageMedia-WebPage-WebPageAttribute-StarGift
     * UserFull-ChatTheme-StarGift
     * ChatFull-PeerStories-StoryItem-MessageMedia-WebPage-WebPageAttribute-StarGift
     * StarsTransaction-StarGift
     * StarGift
     * SavedStarGift-StarGift
     */
    @Test
    public fun test_001594_StarGift_TL_starGift_layer217() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_StarGift.TL_starGift_layer217::class,
          org.Tajgram.tgnet.tl.TL_stars.StarGift::TLdeserialize, 217)

    }

    /**
     * Message-MessageMedia-WebPage-Page-PageBlock-Chat
     * StoryItem-MessageMedia-WebPage-Page-PageBlock-Chat
     * Chat
     * UserFull-PeerStories-StoryItem-MessageMedia-WebPage-Page-PageBlock-Chat
     * ChatFull-PeerStories-StoryItem-MessageMedia-WebPage-Page-PageBlock-Chat
     * StarsTransaction-MessageMedia-WebPage-Page-PageBlock-Chat
     */
    @Test
    public fun test_001595_Chat_TL_channel_layer216() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Chat.TL_channel_layer216::class,
          org.Tajgram.tgnet.TLRPC.Chat::TLdeserialize, 216)

    }

    /**
     * Message
     */
    @Test
    public fun test_001596_Message_TL_message_layer216() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Message.TL_message_layer216::class,
          org.Tajgram.tgnet.TLRPC.Message::TLdeserialize, 216)

    }

    /**
     * Message-MessageAction
     */
    @Test
    public fun test_001597_MessageAction_TL_messageActionGiftCode_layer216() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageAction.TL_messageActionGiftCode_layer216::class,
          org.Tajgram.tgnet.TLRPC.MessageAction::TLdeserialize, 216)

    }

    /**
     * Message-MessageAction
     */
    @Test
    public fun test_001598_MessageAction_TL_messageActionGiftPremium_layer216() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageAction.TL_messageActionGiftPremium_layer216::class,
          org.Tajgram.tgnet.TLRPC.MessageAction::TLdeserialize, 216)

    }

    /**
     * Message-MessageMedia-TodoCompletion
     * StoryItem-MessageMedia-TodoCompletion
     * UserFull-PeerStories-StoryItem-MessageMedia-TodoCompletion
     * ChatFull-PeerStories-StoryItem-MessageMedia-TodoCompletion
     * StarsTransaction-MessageMedia-TodoCompletion
     */
    @Test
    public fun test_001599_TodoCompletion_TL_todoCompletion_layer216() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_TodoCompletion.TL_todoCompletion_layer216::class,
          org.Tajgram.tgnet.TLRPC.TodoCompletion::TLdeserialize, 216)

    }

    /**
     * User
     */
    @Test
    public fun test_001600_User_TL_user_layer216() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_User.TL_user_layer216::class,
          org.Tajgram.tgnet.TLRPC.User::TLdeserialize, 216)

    }

    /**
     * ForumTopic
     */
    @Test
    public fun test_001601_ForumTopic_TL_forumTopic_layer215() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ForumTopic.TL_forumTopic_layer215::class,
          org.Tajgram.tgnet.TLRPC.ForumTopic::TLdeserialize, 215)

    }

    /**
     * Message-MessageAction-StarGift
     * StoryItem-MessageMedia-WebPage-WebPageAttribute-StarGift
     * UserFull-ChatTheme-StarGift
     * ChatFull-PeerStories-StoryItem-MessageMedia-WebPage-WebPageAttribute-StarGift
     * StarsTransaction-StarGift
     * StarGift
     * SavedStarGift-StarGift
     */
    @Test
    public fun test_001602_StarGift_TL_starGiftUnique_layer215() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_StarGift.TL_starGiftUnique_layer215::class,
          org.Tajgram.tgnet.tl.TL_stars.StarGift::TLdeserialize, 215)

    }

    /**
     * UserFull
     */
    @Test
    public fun test_001603_UserFull_TL_userFull_layer215() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_UserFull.TL_userFull_layer215::class,
          org.Tajgram.tgnet.TLRPC.UserFull::TLdeserialize, 215)

    }

    /**
     * Message-MessageAction
     */
    @Test
    public fun test_001604_MessageAction_TL_messageActionStarGiftUnique_layer214() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageAction.TL_messageActionStarGiftUnique_layer214::class,
          org.Tajgram.tgnet.TLRPC.MessageAction::TLdeserialize, 214)

    }

    /**
     * SavedStarGift
     */
    @Test
    public fun test_001605_SavedStarGift_TL_savedStarGift_layer214() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_SavedStarGift.TL_savedStarGift_layer214::class,
          org.Tajgram.tgnet.tl.TL_stars.SavedStarGift::TLdeserialize, 214)

    }

    /**
     * Message-MessageAction
     */
    @Test
    public fun test_001606_MessageAction_TL_messageActionSetChatTheme_layer213() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageAction.TL_messageActionSetChatTheme_layer213::class,
          org.Tajgram.tgnet.TLRPC.MessageAction::TLdeserialize, 213)

    }

    /**
     * Message-MessageAction-StarGift
     * StoryItem-MessageMedia-WebPage-WebPageAttribute-StarGift
     * UserFull-PeerStories-StoryItem-MessageMedia-WebPage-WebPageAttribute-StarGift
     * ChatFull-PeerStories-StoryItem-MessageMedia-WebPage-WebPageAttribute-StarGift
     * StarsTransaction-StarGift
     * StarGift
     * SavedStarGift-StarGift
     */
    @Test
    public fun test_001607_StarGift_TL_starGiftUnique_layer213() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_StarGift.TL_starGiftUnique_layer213::class,
          org.Tajgram.tgnet.tl.TL_stars.StarGift::TLdeserialize, 213)

    }

    /**
     * UserFull
     */
    @Test
    public fun test_001608_UserFull_TL_userFull_layer213() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_UserFull.TL_userFull_layer213::class,
          org.Tajgram.tgnet.TLRPC.UserFull::TLdeserialize, 213)

    }

    /**
     * ChatFull
     */
    @Test
    public fun test_001609_ChatFull_TL_channelFull_layer212() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ChatFull.TL_channelFull_layer212::class,
          org.Tajgram.tgnet.TLRPC.ChatFull::TLdeserialize, 212)

    }

    /**
     * InputStorePaymentPurpose
     */
    @Test
    public fun test_001610_InputStorePaymentPurpose_TL_inputStorePaymentStarsTopup_layer212() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputStorePaymentPurpose.TL_inputStorePaymentStarsTopup_layer212::class,
          org.Tajgram.tgnet.TLRPC.InputStorePaymentPurpose::TLdeserialize, 212)

    }

    /**
     * Message-MessageAction-StarGift
     * StoryItem-MessageMedia-WebPage-WebPageAttribute-StarGift
     * UserFull-PeerStories-StoryItem-MessageMedia-WebPage-WebPageAttribute-StarGift
     * ChatFull-PeerStories-StoryItem-MessageMedia-WebPage-WebPageAttribute-StarGift
     * StarsTransaction-StarGift
     * StarGift
     * SavedStarGift-StarGift
     */
    @Test
    public fun test_001611_StarGift_TL_starGift_layer212() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_StarGift.TL_starGift_layer212::class,
          org.Tajgram.tgnet.tl.TL_stars.StarGift::TLdeserialize, 212)

    }

    /**
     * UserFull
     */
    @Test
    public fun test_001612_UserFull_TL_userFull_layer212() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_UserFull.TL_userFull_layer212::class,
          org.Tajgram.tgnet.TLRPC.UserFull::TLdeserialize, 212)

    }

    /**
     * Message-MessageAction
     */
    @Test
    public fun test_001613_MessageAction_TL_messageActionStarGift_layer211() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageAction.TL_messageActionStarGift_layer211::class,
          org.Tajgram.tgnet.TLRPC.MessageAction::TLdeserialize, 211)

    }

    /**
     * SavedStarGift
     */
    @Test
    public fun test_001614_SavedStarGift_TL_savedStarGift_layer211() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_SavedStarGift.TL_savedStarGift_layer211::class,
          org.Tajgram.tgnet.tl.TL_stars.SavedStarGift::TLdeserialize, 211)

    }

    /**
     * Message-MessageAction-StarGift
     * StoryItem-MessageMedia-WebPage-WebPageAttribute-StarGift
     * UserFull-PeerStories-StoryItem-MessageMedia-WebPage-WebPageAttribute-StarGift
     * ChatFull-PeerStories-StoryItem-MessageMedia-WebPage-WebPageAttribute-StarGift
     * StarsTransaction-StarGift
     * StarGift
     * SavedStarGift-StarGift
     */
    @Test
    public fun test_001615_StarGift_TL_starGiftUnique_layer211() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_StarGift.TL_starGiftUnique_layer211::class,
          org.Tajgram.tgnet.tl.TL_stars.StarGift::TLdeserialize, 211)

    }

    /**
     * Message-MessageAction
     */
    @Test
    public fun test_001616_MessageAction_TL_messageActionStarGiftUnique_layer210() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageAction.TL_messageActionStarGiftUnique_layer210::class,
          org.Tajgram.tgnet.TLRPC.MessageAction::TLdeserialize, 210)

    }

    /**
     * Message-MessageAction-StarGift
     * StoryItem-MessageMedia-WebPage-WebPageAttribute-StarGift
     * UserFull-PeerStories-StoryItem-MessageMedia-WebPage-WebPageAttribute-StarGift
     * ChatFull-PeerStories-StoryItem-MessageMedia-WebPage-WebPageAttribute-StarGift
     * StarsTransaction-StarGift
     * StarGift
     * SavedStarGift-StarGift
     */
    @Test
    public fun test_001617_StarGift_TL_starGiftUnique_layer210() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_StarGift.TL_starGiftUnique_layer210::class,
          org.Tajgram.tgnet.tl.TL_stars.StarGift::TLdeserialize, 210)

    }

    /**
     * Message-MessageMedia-StoryItem
     * StoryItem
     * UserFull-PeerStories-StoryItem
     * ChatFull-PeerStories-StoryItem
     * StarsTransaction-MessageMedia-StoryItem
     */
    @Test
    public fun test_001618_StoryItem_TL_storyItem_layer210() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_StoryItem.TL_storyItem_layer210::class,
          org.Tajgram.tgnet.tl.TL_stories.StoryItem::TLdeserialize, 210)

    }

    /**
     * UserFull
     */
    @Test
    public fun test_001619_UserFull_TL_userFull_layer210() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_UserFull.TL_userFull_layer210::class,
          org.Tajgram.tgnet.TLRPC.UserFull::TLdeserialize, 210)

    }

    /**
     * SavedStarGift
     */
    @Test
    public fun test_001620_SavedStarGift_TL_savedStarGift_layer209() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_SavedStarGift.TL_savedStarGift_layer209::class,
          org.Tajgram.tgnet.tl.TL_stars.SavedStarGift::TLdeserialize, 209)

    }

    /**
     * Message-MessageAction-StarGift
     * StoryItem-MessageMedia-WebPage-WebPageAttribute-StarGift
     * UserFull-PeerStories-StoryItem-MessageMedia-WebPage-WebPageAttribute-StarGift
     * ChatFull-PeerStories-StoryItem-MessageMedia-WebPage-WebPageAttribute-StarGift
     * StarsTransaction-StarGift
     * StarGift
     * SavedStarGift-StarGift
     */
    @Test
    public fun test_001621_StarGift_TL_starGift_layer209() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_StarGift.TL_starGift_layer209::class,
          org.Tajgram.tgnet.tl.TL_stars.StarGift::TLdeserialize, 209)

    }

    /**
     * UserFull
     */
    @Test
    public fun test_001622_UserFull_TL_userFull_layer209() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_UserFull.TL_userFull_layer209::class,
          org.Tajgram.tgnet.TLRPC.UserFull::TLdeserialize, 209)

    }

    /**
     * ForumTopic-DraftMessage-InputReplyTo
     */
    @Test
    public fun test_001623_InputReplyTo_TL_inputReplyToMessage_layer207() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputReplyTo.TL_inputReplyToMessage_layer207::class,
          org.Tajgram.tgnet.TLRPC.InputReplyTo::TLdeserialize, 207)

    }

    /**
     * Message-MessageReplyHeader
     */
    @Test
    public fun test_001624_MessageReplyHeader_TL_messageReplyHeader_layer207() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageReplyHeader.TL_messageReplyHeader_layer207::class,
          org.Tajgram.tgnet.TLRPC.MessageReplyHeader::TLdeserialize, 207)

    }

    /**
     * Message-MessageAction-StarGift
     * StoryItem-MessageMedia-WebPage-WebPageAttribute-StarGift
     * UserFull-PeerStories-StoryItem-MessageMedia-WebPage-WebPageAttribute-StarGift
     * ChatFull-PeerStories-StoryItem-MessageMedia-WebPage-WebPageAttribute-StarGift
     * StarsTransaction-StarGift
     * StarGift
     * SavedStarGift-StarGift
     */
    @Test
    public fun test_001625_StarGift_TL_starGift_layer206() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_StarGift.TL_starGift_layer206::class,
          org.Tajgram.tgnet.tl.TL_stars.StarGift::TLdeserialize, 206)

    }

    /**
     * Message-MessageAction-StarGift
     * StoryItem-MessageMedia-WebPage-WebPageAttribute-StarGift
     * UserFull-PeerStories-StoryItem-MessageMedia-WebPage-WebPageAttribute-StarGift
     * ChatFull-PeerStories-StoryItem-MessageMedia-WebPage-WebPageAttribute-StarGift
     * StarsTransaction-StarGift
     * StarGift
     * SavedStarGift-StarGift
     */
    @Test
    public fun test_001626_StarGift_TL_starGiftUnique_layer206() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_StarGift.TL_starGiftUnique_layer206::class,
          org.Tajgram.tgnet.tl.TL_stars.StarGift::TLdeserialize, 206)

    }

    /**
     * ForumTopic-DraftMessage
     */
    @Test
    public fun test_001627_DraftMessage_TL_draftMessage_layer205() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_DraftMessage.TL_draftMessage_layer205::class,
          org.Tajgram.tgnet.TLRPC.DraftMessage::TLdeserialize, 205)

    }

    /**
     * Message
     */
    @Test
    public fun test_001628_Message_TL_message_layer205() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Message.TL_message_layer205::class,
          org.Tajgram.tgnet.TLRPC.Message::TLdeserialize, 205)

    }

    /**
     * StarsTransaction
     */
    @Test
    public fun test_001629_StarsTransaction_TL_starsTransaction_layer205() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_StarsTransaction.TL_starsTransaction_layer205::class,
          org.Tajgram.tgnet.tl.TL_stars.StarsTransaction::TLdeserialize, 205)

    }

    /**
     * ChatFull
     */
    @Test
    public fun test_001630_ChatFull_TL_channelFull_layer204() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ChatFull.TL_channelFull_layer204::class,
          org.Tajgram.tgnet.TLRPC.ChatFull::TLdeserialize, 204)

    }

    /**
     * Message
     */
    @Test
    public fun test_001631_Message_TL_messageService_layer204() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Message.TL_messageService_layer204::class,
          org.Tajgram.tgnet.TLRPC.Message::TLdeserialize, 204)

    }

    /**
     * Message-MessageMedia-WebPage-Page-PageBlock-Chat
     * StoryItem-MessageMedia-WebPage-Page-PageBlock-Chat
     * Chat
     * UserFull-PeerStories-StoryItem-MessageMedia-WebPage-Page-PageBlock-Chat
     * ChatFull-PeerStories-StoryItem-MessageMedia-WebPage-Page-PageBlock-Chat
     * StarsTransaction-MessageMedia-WebPage-Page-PageBlock-Chat
     */
    @Test
    public fun test_001632_Chat_TL_channel_layer203() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Chat.TL_channel_layer203::class,
          org.Tajgram.tgnet.TLRPC.Chat::TLdeserialize, 203)

    }

    /**
     * ForumTopic-DraftMessage-InputReplyTo
     */
    @Test
    public fun test_001633_InputReplyTo_TL_inputReplyToMessage_layer203() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputReplyTo.TL_inputReplyToMessage_layer203::class,
          org.Tajgram.tgnet.TLRPC.InputReplyTo::TLdeserialize, 203)

    }

    /**
     * Message-MessageAction
     */
    @Test
    public fun test_001634_MessageAction_TL_messageActionPaidMessagesPrice_layer203() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageAction.TL_messageActionPaidMessagesPrice_layer203::class,
          org.Tajgram.tgnet.TLRPC.MessageAction::TLdeserialize, 203)

    }

    /**
     * Message-MessageAction
     */
    @Test
    public fun test_001635_MessageAction_TL_messageActionStarGiftUnique_layer202() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageAction.TL_messageActionStarGiftUnique_layer202::class,
          org.Tajgram.tgnet.TLRPC.MessageAction::TLdeserialize, 202)

    }

    /**
     * SavedStarGift
     */
    @Test
    public fun test_001636_SavedStarGift_TL_savedStarGift_layer202() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_SavedStarGift.TL_savedStarGift_layer202::class,
          org.Tajgram.tgnet.tl.TL_stars.SavedStarGift::TLdeserialize, 202)

    }

    /**
     * Message-MessageAction-StarGift
     * StoryItem-MessageMedia-WebPage-WebPageAttribute-StarGift
     * UserFull-PeerStories-StoryItem-MessageMedia-WebPage-WebPageAttribute-StarGift
     * ChatFull-PeerStories-StoryItem-MessageMedia-WebPage-WebPageAttribute-StarGift
     * StarsTransaction-StarGift
     * StarGift
     * SavedStarGift-StarGift
     */
    @Test
    public fun test_001637_StarGift_TL_starGift_layer202() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_StarGift.TL_starGift_layer202::class,
          org.Tajgram.tgnet.tl.TL_stars.StarGift::TLdeserialize, 202)

    }

    /**
     * Message-MessageAction-StarGift
     * StoryItem-MessageMedia-WebPage-WebPageAttribute-StarGift
     * UserFull-PeerStories-StoryItem-MessageMedia-WebPage-WebPageAttribute-StarGift
     * ChatFull-PeerStories-StoryItem-MessageMedia-WebPage-WebPageAttribute-StarGift
     * StarsTransaction-StarGift
     * StarGift
     * SavedStarGift-StarGift
     */
    @Test
    public fun test_001638_StarGift_TL_starGiftUnique_layer202() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_StarGift.TL_starGiftUnique_layer202::class,
          org.Tajgram.tgnet.tl.TL_stars.StarGift::TLdeserialize, 202)

    }

    /**
     * Message-MessageAction-StarGift-StarGiftAttribute
     * StoryItem-MessageMedia-WebPage-WebPageAttribute-StarGift-StarGiftAttribute
     * UserFull-PeerStories-StoryItem-MessageMedia-WebPage-WebPageAttribute-StarGift-StarGiftAttribute
     * ChatFull-PeerStories-StoryItem-MessageMedia-WebPage-WebPageAttribute-StarGift-StarGiftAttribute
     * StarsTransaction-StarGift-StarGiftAttribute
     * StarGift-StarGiftAttribute
     * SavedStarGift-StarGift-StarGiftAttribute
     */
    @Test
    public fun test_001639_StarGiftAttribute_TL_starGiftAttributeBackdrop_layer202() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_StarGiftAttribute.TL_starGiftAttributeBackdrop_layer202::class,
          org.Tajgram.tgnet.tl.TL_stars.StarGiftAttribute::TLdeserialize, 202)

    }

    /**
     * UserFull
     */
    @Test
    public fun test_001640_UserFull_TL_userFull_layer200() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_UserFull.TL_userFull_layer200::class,
          org.Tajgram.tgnet.TLRPC.UserFull::TLdeserialize, 200)

    }

    /**
     * Message-MessageMedia-WebPage-Page-PageBlock-Chat
     * StoryItem-MessageMedia-WebPage-Page-PageBlock-Chat
     * Chat
     * UserFull-PeerStories-StoryItem-MessageMedia-WebPage-Page-PageBlock-Chat
     * ChatFull-PeerStories-StoryItem-MessageMedia-WebPage-Page-PageBlock-Chat
     * StarsTransaction-MessageMedia-WebPage-Page-PageBlock-Chat
     */
    @Test
    public fun test_001641_Chat_TL_channel_layer199() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Chat.TL_channel_layer199::class,
          org.Tajgram.tgnet.TLRPC.Chat::TLdeserialize, 199)

    }

    /**
     * Message
     */
    @Test
    public fun test_001642_Message_TL_message_layer199() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Message.TL_message_layer199::class,
          org.Tajgram.tgnet.TLRPC.Message::TLdeserialize, 199)

    }

    /**
     * UserFull-PeerSettings
     */
    @Test
    public fun test_001643_PeerSettings_TL_peerSettings_layer199() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_PeerSettings.TL_peerSettings_layer199::class,
          org.Tajgram.tgnet.TLRPC.PeerSettings::TLdeserialize, 199)

    }

    /**
     * UserFull-PremiumGiftOption
     */
    @Test
    public fun test_001644_PremiumGiftOption_TL_premiumGiftOption_layer199() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_PremiumGiftOption.TL_premiumGiftOption_layer199::class,
          org.Tajgram.tgnet.TLRPC.TL_premiumGiftOption::TLdeserialize, 199)

    }

    /**
     * StarsTransaction
     */
    @Test
    public fun test_001645_StarsTransaction_TL_starsTransaction_layer199() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_StarsTransaction.TL_starsTransaction_layer199::class,
          org.Tajgram.tgnet.tl.TL_stars.StarsTransaction::TLdeserialize, 199)

    }

    /**
     * User
     */
    @Test
    public fun test_001646_User_TL_user_layer199() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_User.TL_user_layer199::class,
          org.Tajgram.tgnet.TLRPC.User::TLdeserialize, 199)

    }

    /**
     * UserFull
     */
    @Test
    public fun test_001647_UserFull_TL_userFull_layer199() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_UserFull.TL_userFull_layer199::class,
          org.Tajgram.tgnet.TLRPC.UserFull::TLdeserialize, 199)

    }

    /**
     * Message-MessageAction-StarGift
     * StoryItem-MessageMedia-WebPage-WebPageAttribute-StarGift
     * UserFull-PeerStories-StoryItem-MessageMedia-WebPage-WebPageAttribute-StarGift
     * ChatFull-PeerStories-StoryItem-MessageMedia-WebPage-WebPageAttribute-StarGift
     * StarsTransaction-StarGift
     * StarGift
     * SavedStarGift-StarGift
     */
    @Test
    public fun test_001648_StarGift_TL_starGiftUnique_layer198() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_StarGift.TL_starGiftUnique_layer198::class,
          org.Tajgram.tgnet.tl.TL_stars.StarGift::TLdeserialize, 198)

    }

    /**
     * ChatFull
     */
    @Test
    public fun test_001649_ChatFull_TL_channelFull_layer197() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ChatFull.TL_channelFull_layer197::class,
          org.Tajgram.tgnet.TLRPC.ChatFull::TLdeserialize, 197)

    }

    /**
     * Message-MessageMedia-WebPage-Page-PageBlock-Chat-EmojiStatus
     * StoryItem-MessageMedia-WebPage-Page-PageBlock-Chat-EmojiStatus
     * Chat-EmojiStatus
     * User-EmojiStatus
     * UserFull-PeerStories-StoryItem-MessageMedia-WebPage-Page-PageBlock-Chat-EmojiStatus
     * ChatFull-PeerStories-StoryItem-MessageMedia-WebPage-Page-PageBlock-Chat-EmojiStatus
     * StarsTransaction-MessageMedia-WebPage-Page-PageBlock-Chat-EmojiStatus
     */
    @Test
    public fun test_001650_EmojiStatus_TL_emojiStatus_layer197() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_EmojiStatus.TL_emojiStatus_layer197::class,
          org.Tajgram.tgnet.TLRPC.EmojiStatus::TLdeserialize, 197)

    }

    /**
     * Message-MessageMedia-WebPage-Page-PageBlock-Chat-EmojiStatus
     * StoryItem-MessageMedia-WebPage-Page-PageBlock-Chat-EmojiStatus
     * Chat-EmojiStatus
     * User-EmojiStatus
     * UserFull-PeerStories-StoryItem-MessageMedia-WebPage-Page-PageBlock-Chat-EmojiStatus
     * ChatFull-PeerStories-StoryItem-MessageMedia-WebPage-Page-PageBlock-Chat-EmojiStatus
     * StarsTransaction-MessageMedia-WebPage-Page-PageBlock-Chat-EmojiStatus
     */
    @Test
    public fun test_001651_EmojiStatus_TL_emojiStatusUntil_layer197() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_EmojiStatus.TL_emojiStatusUntil_layer197::class,
          org.Tajgram.tgnet.TLRPC.EmojiStatus::TLdeserialize, 197)

    }

    /**
     * ForumTopic-DraftMessage-InputMedia
     */
    @Test
    public fun test_001652_InputMedia_TL_inputMediaDocument_layer197() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputMedia.TL_inputMediaDocument_layer197::class,
          org.Tajgram.tgnet.TLRPC.InputMedia::TLdeserialize, 197)

    }

    /**
     * ForumTopic-DraftMessage-InputMedia
     */
    @Test
    public fun test_001653_InputMedia_TL_inputMediaDocumentExternal_layer197() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputMedia.TL_inputMediaDocumentExternal_layer197::class,
          org.Tajgram.tgnet.TLRPC.InputMedia::TLdeserialize, 197)

    }

    /**
     * ForumTopic-DraftMessage-InputMedia
     */
    @Test
    public fun test_001654_InputMedia_TL_inputMediaUploadedDocument_layer197() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputMedia.TL_inputMediaUploadedDocument_layer197::class,
          org.Tajgram.tgnet.TLRPC.InputMedia::TLdeserialize, 197)

    }

    /**
     * Message-MessageAction
     */
    @Test
    public fun test_001655_MessageAction_TL_messageActionStarGift_layer197() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageAction.TL_messageActionStarGift_layer197::class,
          org.Tajgram.tgnet.TLRPC.MessageAction::TLdeserialize, 197)

    }

    /**
     * Message-MessageAction
     */
    @Test
    public fun test_001656_MessageAction_TL_messageActionStarGiftUnique_layer197() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageAction.TL_messageActionStarGiftUnique_layer197::class,
          org.Tajgram.tgnet.TLRPC.MessageAction::TLdeserialize, 197)

    }

    /**
     * Message-MessageMedia
     * StoryItem-MessageMedia
     * UserFull-PeerStories-StoryItem-MessageMedia
     * ChatFull-PeerStories-StoryItem-MessageMedia
     * StarsTransaction-MessageMedia
     */
    @Test
    public fun test_001657_MessageMedia_TL_messageMediaDocument_layer197() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageMedia.TL_messageMediaDocument_layer197::class,
          org.Tajgram.tgnet.TLRPC.MessageMedia::TLdeserialize, 197)

    }

    /**
     * Message-MessageAction-StarGift
     * StoryItem-MessageMedia-WebPage-WebPageAttribute-StarGift
     * UserFull-PeerStories-StoryItem-MessageMedia-WebPage-WebPageAttribute-StarGift
     * ChatFull-PeerStories-StoryItem-MessageMedia-WebPage-WebPageAttribute-StarGift
     * StarsTransaction-StarGift
     * StarGift
     */
    @Test
    public fun test_001658_StarGift_TL_starGiftUnique_layer197() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_StarGift.TL_starGiftUnique_layer197::class,
          org.Tajgram.tgnet.tl.TL_stars.StarGift::TLdeserialize, 197)

    }

    /**
     * Message-MessageAction-StarGift-StarGiftAttribute
     * StoryItem-MessageMedia-WebPage-WebPageAttribute-StarGift-StarGiftAttribute
     * UserFull-PeerStories-StoryItem-MessageMedia-WebPage-WebPageAttribute-StarGift-StarGiftAttribute
     * ChatFull-PeerStories-StoryItem-MessageMedia-WebPage-WebPageAttribute-StarGift-StarGiftAttribute
     * StarsTransaction-StarGift-StarGiftAttribute
     * StarGift-StarGiftAttribute
     */
    @Test
    public fun test_001659_StarGiftAttribute_TL_starGiftAttributeOriginalDetails_layer197() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_StarGiftAttribute.TL_starGiftAttributeOriginalDetails_layer197::class,
          org.Tajgram.tgnet.tl.TL_stars.StarGiftAttribute::TLdeserialize, 197)

    }

    /**
     * Message-MessageAction-StarGift
     * StarsTransaction-StarGift
     * StarGift
     */
    @Test
    public fun test_001660_StarGift_TL_starGiftUnique_layer196() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_StarGift.TL_starGiftUnique_layer196::class,
          org.Tajgram.tgnet.tl.TL_stars.StarGift::TLdeserialize, 196)

    }

    /**
     * UserFull-BotInfo
     * ChatFull-BotInfo
     */
    @Test
    public fun test_001661_BotInfo_TL_botInfo_layer195() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_BotInfo.TL_botInfo_layer195::class,
          org.Tajgram.tgnet.tl.TL_bots.BotInfo::TLdeserialize, 195)

    }

    /**
     * Message-MessageMedia-WebPage-Page-PageBlock-Chat
     * StoryItem-MessageMedia-WebPage-Page-PageBlock-Chat
     * Chat
     * UserFull-PeerStories-StoryItem-MessageMedia-WebPage-Page-PageBlock-Chat
     * ChatFull-PeerStories-StoryItem-MessageMedia-WebPage-Page-PageBlock-Chat
     * StarsTransaction-MessageMedia-WebPage-Page-PageBlock-Chat
     */
    @Test
    public fun test_001662_Chat_TL_channel_layer195() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Chat.TL_channel_layer195::class,
          org.Tajgram.tgnet.TLRPC.Chat::TLdeserialize, 195)

    }

    /**
     * ChatFull
     */
    @Test
    public fun test_001663_ChatFull_TL_channelFull_layer195() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ChatFull.TL_channelFull_layer195::class,
          org.Tajgram.tgnet.TLRPC.ChatFull::TLdeserialize, 195)

    }

    /**
     * Message
     */
    @Test
    public fun test_001664_Message_TL_message_layer195() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Message.TL_message_layer195::class,
          org.Tajgram.tgnet.TLRPC.Message::TLdeserialize, 195)

    }

    /**
     * Message
     */
    @Test
    public fun test_001665_Message_TL_messageService_layer195() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Message.TL_messageService_layer195::class,
          org.Tajgram.tgnet.TLRPC.Message::TLdeserialize, 195)

    }

    /**
     * Message-MessageAction
     */
    @Test
    public fun test_001666_MessageAction_TL_messageActionStarGift_layer195() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageAction.TL_messageActionStarGift_layer195::class,
          org.Tajgram.tgnet.TLRPC.MessageAction::TLdeserialize, 195)

    }

    /**
     * Message-MessageAction-StarGift
     * StarsTransaction-StarGift
     * StarGift
     */
    @Test
    public fun test_001667_StarGift_TL_starGift_layer195() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_StarGift.TL_starGift_layer195::class,
          org.Tajgram.tgnet.tl.TL_stars.StarGift::TLdeserialize, 195)

    }

    /**
     * User
     */
    @Test
    public fun test_001668_User_TL_user_layer195() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_User.TL_user_layer195::class,
          org.Tajgram.tgnet.TLRPC.User::TLdeserialize, 195)

    }

    /**
     * UserFull
     */
    @Test
    public fun test_001669_UserFull_TL_userFull_layer195() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_UserFull.TL_userFull_layer195::class,
          org.Tajgram.tgnet.TLRPC.UserFull::TLdeserialize, 195)

    }

    /**
     * StarsTransaction
     */
    @Test
    public fun test_001670_StarsTransaction_TL_starsTransaction_layer194() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_StarsTransaction.TL_starsTransaction_layer194::class,
          org.Tajgram.tgnet.tl.TL_stars.StarsTransaction::TLdeserialize, 194)

    }

    /**
     * UserFull
     */
    @Test
    public fun test_001671_UserFull_TL_userFull_layer194() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_UserFull.TL_userFull_layer194::class,
          org.Tajgram.tgnet.TLRPC.UserFull::TLdeserialize, 194)

    }

    /**
     * Message-MessageAction
     */
    @Test
    public fun test_001672_MessageAction_TL_messageActionPaymentSent_layer193() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageAction.TL_messageActionPaymentSent_layer193::class,
          org.Tajgram.tgnet.TLRPC.MessageAction::TLdeserialize, 193)

    }

    /**
     * Message-MessageAction
     */
    @Test
    public fun test_001673_MessageAction_TL_messageActionPaymentSentMe_layer193() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageAction.TL_messageActionPaymentSentMe_layer193::class,
          org.Tajgram.tgnet.TLRPC.MessageAction::TLdeserialize, 193)

    }

    /**
     * UserFull-BotInfo
     * ChatFull-BotInfo
     */
    @Test
    public fun test_001674_BotInfo_TL_botInfo_layer192() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_BotInfo.TL_botInfo_layer192::class,
          org.Tajgram.tgnet.tl.TL_bots.BotInfo::TLdeserialize, 192)

    }

    /**
     * ForumTopic-DraftMessage-InputMedia-Invoice
     */
    @Test
    public fun test_001675_Invoice_TL_invoice_layer192() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Invoice.TL_invoice_layer192::class,
          org.Tajgram.tgnet.TLRPC.TL_invoice::TLdeserialize, 192)

    }

    /**
     * Message-MessageAction
     */
    @Test
    public fun test_001676_MessageAction_TL_messageActionStarGift_layer192() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageAction.TL_messageActionStarGift_layer192::class,
          org.Tajgram.tgnet.TLRPC.MessageAction::TLdeserialize, 192)

    }

    /**
     * StarsTransaction
     */
    @Test
    public fun test_001677_StarsTransaction_TL_starsTransaction_layer191() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_StarsTransaction.TL_starsTransaction_layer191::class,
          org.Tajgram.tgnet.tl.TL_stars.StarsTransaction::TLdeserialize, 191)

    }

    /**
     * Message-MessageAction-StarGift
     * StarsTransaction-StarGift
     * StarGift
     */
    @Test
    public fun test_001678_StarGift_TL_starGift_layer190() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_StarGift.TL_starGift_layer190::class,
          org.Tajgram.tgnet.tl.TL_stars.StarGift::TLdeserialize, 190)

    }

    /**
     * InputStorePaymentPurpose
     */
    @Test
    public fun test_001679_InputStorePaymentPurpose_TL_inputStorePaymentPremiumGiftCode_layer189() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputStorePaymentPurpose.TL_inputStorePaymentPremiumGiftCode_layer189::class,
          org.Tajgram.tgnet.TLRPC.InputStorePaymentPurpose::TLdeserialize, 189)

    }

    /**
     * Message-MessageAction
     */
    @Test
    public fun test_001680_MessageAction_TL_messageActionGiftCode_layer189() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageAction.TL_messageActionGiftCode_layer189::class,
          org.Tajgram.tgnet.TLRPC.MessageAction::TLdeserialize, 189)

    }

    /**
     * Message-MessageAction
     */
    @Test
    public fun test_001681_MessageAction_TL_messageActionGiftPremium_layer189() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageAction.TL_messageActionGiftPremium_layer189::class,
          org.Tajgram.tgnet.TLRPC.MessageAction::TLdeserialize, 189)

    }

    /**
     * StarsTransaction
     */
    @Test
    public fun test_001682_StarsTransaction_TL_starsTransaction_layer188() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_StarsTransaction.TL_starsTransaction_layer188::class,
          org.Tajgram.tgnet.tl.TL_stars.StarsTransaction::TLdeserialize, 188)

    }

    /**
     * UserFull
     */
    @Test
    public fun test_001683_UserFull_TL_userFull_layer188() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_UserFull.TL_userFull_layer188::class,
          org.Tajgram.tgnet.TLRPC.UserFull::TLdeserialize, 188)

    }

    /**
     * Message-MessageMedia-Document-DocumentAttribute
     * StoryItem-MessageMedia-Document-DocumentAttribute
     * Theme-Document-DocumentAttribute
     * UserFull-BotInfo-Document-DocumentAttribute
     * ChatFull-BotInfo-Document-DocumentAttribute
     * StarsTransaction-WebDocument-DocumentAttribute
     * ForumTopic-DraftMessage-InputMedia-DocumentAttribute
     */
    @Test
    public fun test_001684_DocumentAttribute_TL_documentAttributeVideo_layer187() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_DocumentAttribute.TL_documentAttributeVideo_layer187::class,
          org.Tajgram.tgnet.TLRPC.DocumentAttribute::TLdeserialize, 187)

    }

    /**
     * Message-MessageMedia
     * StoryItem-MessageMedia
     * UserFull-PeerStories-StoryItem-MessageMedia
     * ChatFull-PeerStories-StoryItem-MessageMedia
     * StarsTransaction-MessageMedia
     */
    @Test
    public fun test_001685_MessageMedia_TL_messageMediaDocument_layer187() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageMedia.TL_messageMediaDocument_layer187::class,
          org.Tajgram.tgnet.TLRPC.MessageMedia::TLdeserialize, 187)

    }

    /**
     * ForumTopic-DraftMessage-InputMedia
     */
    @Test
    public fun test_001686_InputMedia_TL_inputMediaPaidMedia_layer186() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputMedia.TL_inputMediaPaidMedia_layer186::class,
          org.Tajgram.tgnet.TLRPC.InputMedia::TLdeserialize, 186)

    }

    /**
     * Message-MessageAction
     */
    @Test
    public fun test_001687_MessageAction_TL_messageActionGiveawayLaunch_layer186() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageAction.TL_messageActionGiveawayLaunch_layer186::class,
          org.Tajgram.tgnet.TLRPC.MessageAction::TLdeserialize, 186)

    }

    /**
     * Message-MessageAction
     */
    @Test
    public fun test_001688_MessageAction_TL_messageActionGiveawayResults_layer186() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageAction.TL_messageActionGiveawayResults_layer186::class,
          org.Tajgram.tgnet.TLRPC.MessageAction::TLdeserialize, 186)

    }

    /**
     * Message-MessageMedia
     * StoryItem-MessageMedia
     * UserFull-PeerStories-StoryItem-MessageMedia
     * ChatFull-PeerStories-StoryItem-MessageMedia
     * StarsTransaction-MessageMedia
     */
    @Test
    public fun test_001689_MessageMedia_TL_messageMediaGiveaway_layer186() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageMedia.TL_messageMediaGiveaway_layer186::class,
          org.Tajgram.tgnet.TLRPC.MessageMedia::TLdeserialize, 186)

    }

    /**
     * Message-MessageMedia
     * StoryItem-MessageMedia
     * UserFull-PeerStories-StoryItem-MessageMedia
     * ChatFull-PeerStories-StoryItem-MessageMedia
     * StarsTransaction-MessageMedia
     */
    @Test
    public fun test_001690_MessageMedia_TL_messageMediaGiveawayResults_layer186() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageMedia.TL_messageMediaGiveawayResults_layer186::class,
          org.Tajgram.tgnet.TLRPC.MessageMedia::TLdeserialize, 186)

    }

    /**
     * StarsTransaction
     */
    @Test
    public fun test_001691_StarsTransaction_TL_starsTransaction_layer186() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_StarsTransaction.TL_starsTransaction_layer186::class,
          org.Tajgram.tgnet.tl.TL_stars.StarsTransaction::TLdeserialize, 186)

    }

    /**
     * UserFull-BotInfo
     * ChatFull-BotInfo
     */
    @Test
    public fun test_001692_BotInfo_TL_botInfo_layer185() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_BotInfo.TL_botInfo_layer185::class,
          org.Tajgram.tgnet.tl.TL_bots.BotInfo::TLdeserialize, 185)

    }

    /**
     * Message-MessageMedia-WebPage-Page-PageBlock-Chat
     * StoryItem-MessageMedia-WebPage-Page-PageBlock-Chat
     * Chat
     * UserFull-PeerStories-StoryItem-MessageMedia-WebPage-Page-PageBlock-Chat
     * ChatFull-PeerStories-StoryItem-MessageMedia-WebPage-Page-PageBlock-Chat
     * StarsTransaction-MessageMedia-WebPage-Page-PageBlock-Chat
     */
    @Test
    public fun test_001693_Chat_TL_channel_layer185() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Chat.TL_channel_layer185::class,
          org.Tajgram.tgnet.TLRPC.Chat::TLdeserialize, 185)

    }

    /**
     * ChatFull-ExportedChatInvite
     */
    @Test
    public fun test_001694_ExportedChatInvite_TL_chatInviteExported_layer185() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ExportedChatInvite.TL_chatInviteExported_layer185::class,
          org.Tajgram.tgnet.TLRPC.ExportedChatInvite::TLdeserialize, 185)

    }

    /**
     * Message-MessageReactions
     */
    @Test
    public fun test_001695_MessageReactions_TL_messageReactions_layer185() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageReactions.TL_messageReactions_layer185::class,
          org.Tajgram.tgnet.TLRPC.MessageReactions::TLdeserialize, 185)

    }

    /**
     * StarsTransaction
     */
    @Test
    public fun test_001696_StarsTransaction_TL_starsTransaction_layer185() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_StarsTransaction.TL_starsTransaction_layer185::class,
          org.Tajgram.tgnet.tl.TL_stars.StarsTransaction::TLdeserialize, 185)

    }

    /**
     * Message-MessageMedia-Document-DocumentAttribute
     * StoryItem-MessageMedia-Document-DocumentAttribute
     * Theme-Document-DocumentAttribute
     * UserFull-BotInfo-Document-DocumentAttribute
     * ChatFull-BotInfo-Document-DocumentAttribute
     * StarsTransaction-WebDocument-DocumentAttribute
     * ForumTopic-DraftMessage-InputMedia-DocumentAttribute
     */
    @Test
    public fun test_001697_DocumentAttribute_TL_documentAttributeVideo_layer184() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_DocumentAttribute.TL_documentAttributeVideo_layer184::class,
          org.Tajgram.tgnet.TLRPC.DocumentAttribute::TLdeserialize, 184)

    }

    /**
     * InputStorePaymentPurpose
     */
    @Test
    public fun test_001698_InputStorePaymentPurpose_TL_inputStorePaymentStars_layer184() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputStorePaymentPurpose.TL_inputStorePaymentStars_layer184::class,
          org.Tajgram.tgnet.TLRPC.InputStorePaymentPurpose::TLdeserialize, 184)

    }

    /**
     * User
     */
    @Test
    public fun test_001699_User_TL_user_layer184() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_User.TL_user_layer184::class,
          org.Tajgram.tgnet.TLRPC.User::TLdeserialize, 184)

    }

    /**
     * StarsTransaction
     */
    @Test
    public fun test_001700_StarsTransaction_TL_starsTransaction_layer182() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_StarsTransaction.TL_starsTransaction_layer182::class,
          org.Tajgram.tgnet.tl.TL_stars.StarsTransaction::TLdeserialize, 182)

    }

    /**
     * ForumTopic-DraftMessage
     */
    @Test
    public fun test_001701_DraftMessage_TL_draftMessage_layer181() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_DraftMessage.TL_draftMessage_layer181::class,
          org.Tajgram.tgnet.TLRPC.DraftMessage::TLdeserialize, 181)

    }

    /**
     * Message-MessageMedia-StoryItem-MediaArea
     * StoryItem-MediaArea
     * UserFull-PeerStories-StoryItem-MediaArea
     * ChatFull-PeerStories-StoryItem-MediaArea
     */
    @Test
    public fun test_001702_MediaArea_TL_mediaAreaGeoPoint_layer181() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MediaArea.TL_mediaAreaGeoPoint_layer181::class,
          org.Tajgram.tgnet.tl.TL_stories.MediaArea::TLdeserialize, 181)

    }

    /**
     * Message-MessageMedia-StoryItem-MediaArea-MediaAreaCoordinates
     * StoryItem-MediaArea-MediaAreaCoordinates
     * UserFull-PeerStories-StoryItem-MediaArea-MediaAreaCoordinates
     * ChatFull-PeerStories-StoryItem-MediaArea-MediaAreaCoordinates
     */
    @Test
    public fun test_001703_MediaAreaCoordinates_TL_mediaAreaCoordinates_layer181() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MediaAreaCoordinates.TL_mediaAreaCoordinates_layer181::class,
          org.Tajgram.tgnet.tl.TL_stories.MediaAreaCoordinates::TLdeserialize, 181)

    }

    /**
     * StarsTransaction
     */
    @Test
    public fun test_001704_StarsTransaction_TL_starsTransaction_layer181() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_StarsTransaction.TL_starsTransaction_layer181::class,
          org.Tajgram.tgnet.tl.TL_stars.StarsTransaction::TLdeserialize, 181)

    }

    /**
     * ForumTopic-DraftMessage-InputMedia
     */
    @Test
    public fun test_001705_InputMedia_TL_inputMediaInvoice_layer180() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputMedia.TL_inputMediaInvoice_layer180::class,
          org.Tajgram.tgnet.TLRPC.InputMedia::TLdeserialize, 180)

    }

    /**
     * Message
     */
    @Test
    public fun test_001706_Message_TL_message_layer180() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Message.TL_message_layer180::class,
          org.Tajgram.tgnet.TLRPC.Message::TLdeserialize, 180)

    }

    /**
     * Message-MessageEntity
     * StoryItem-MessageEntity
     * UserFull-PeerStories-StoryItem-MessageEntity
     * ChatFull-PeerStories-StoryItem-MessageEntity
     * ForumTopic-DraftMessage-MessageEntity
     */
    @Test
    public fun test_001707_MessageEntity_TL_messageEntityBlockquote_layer180() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageEntity.TL_messageEntityBlockquote_layer180::class,
          org.Tajgram.tgnet.TLRPC.MessageEntity::TLdeserialize, 180)

    }

    /**
     * Message
     */
    @Test
    public fun test_001708_Message_TL_message_layer179() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Message.TL_message_layer179::class,
          org.Tajgram.tgnet.TLRPC.Message::TLdeserialize, 179)

    }

    /**
     * Message-MessageMedia-Poll
     * StoryItem-MessageMedia-Poll
     * UserFull-PeerStories-StoryItem-MessageMedia-Poll
     * ChatFull-PeerStories-StoryItem-MessageMedia-Poll
     * ForumTopic-DraftMessage-InputMedia-Poll
     */
    @Test
    public fun test_001709_Poll_TL_poll_layer178() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Poll.TL_poll_layer178::class,
          org.Tajgram.tgnet.TLRPC.Poll::TLdeserialize, 178)

    }

    /**
     * Message-MessageMedia-Poll-PollAnswer
     * StoryItem-MessageMedia-Poll-PollAnswer
     * UserFull-PeerStories-StoryItem-MessageMedia-Poll-PollAnswer
     * ChatFull-PeerStories-StoryItem-MessageMedia-Poll-PollAnswer
     * ForumTopic-DraftMessage-InputMedia-Poll-PollAnswer
     */
    @Test
    public fun test_001710_PollAnswer_TL_pollAnswer_layer178() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_PollAnswer.TL_pollAnswer_layer178::class,
          org.Tajgram.tgnet.TLRPC.PollAnswer::TLdeserialize, 178)

    }

    /**
     * ChatFull
     */
    @Test
    public fun test_001711_ChatFull_TL_channelFull_layer177() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ChatFull.TL_channelFull_layer177::class,
          org.Tajgram.tgnet.TLRPC.ChatFull::TLdeserialize, 177)

    }

    /**
     * ChatFull
     */
    @Test
    public fun test_001712_ChatFull_TL_chatFull_layer177() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ChatFull.TL_chatFull_layer177::class,
          org.Tajgram.tgnet.TLRPC.ChatFull::TLdeserialize, 177)

    }

    /**
     * Message
     */
    @Test
    public fun test_001713_Message_TL_message_layer176() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Message.TL_message_layer176::class,
          org.Tajgram.tgnet.TLRPC.Message::TLdeserialize, 176)

    }

    /**
     * UserFull-PeerSettings
     */
    @Test
    public fun test_001714_PeerSettings_TL_peerSettings_layer176() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_PeerSettings.TL_peerSettings_layer176::class,
          org.Tajgram.tgnet.TLRPC.PeerSettings::TLdeserialize, 176)

    }

    /**
     * UserFull
     */
    @Test
    public fun test_001715_UserFull_TL_userFull_layer176() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_UserFull.TL_userFull_layer176::class,
          org.Tajgram.tgnet.TLRPC.UserFull::TLdeserialize, 176)

    }

    /**
     * Message
     */
    @Test
    public fun test_001716_Message_TL_message_layer175() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Message.TL_message_layer175::class,
          org.Tajgram.tgnet.TLRPC.Message::TLdeserialize, 175)

    }

    /**
     * UserFull
     */
    @Test
    public fun test_001717_UserFull_TL_userFull_layer175() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_UserFull.TL_userFull_layer175::class,
          org.Tajgram.tgnet.TLRPC.UserFull::TLdeserialize, 175)

    }

    /**
     * ChatFull
     */
    @Test
    public fun test_001718_ChatFull_TL_channelFull_layer173() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ChatFull.TL_channelFull_layer173::class,
          org.Tajgram.tgnet.TLRPC.ChatFull::TLdeserialize, 173)

    }

    /**
     * ForumTopic-DraftMessage-InputReplyTo
     */
    @Test
    public fun test_001719_InputReplyTo_TL_inputReplyToStory_layer173() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputReplyTo.TL_inputReplyToStory_layer173::class,
          org.Tajgram.tgnet.TLRPC.InputReplyTo::TLdeserialize, 173)

    }

    /**
     * Message
     */
    @Test
    public fun test_001720_Message_TL_message_layer173() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Message.TL_message_layer173::class,
          org.Tajgram.tgnet.TLRPC.Message::TLdeserialize, 173)

    }

    /**
     * Message-MessageReplyHeader
     */
    @Test
    public fun test_001721_MessageReplyHeader_TL_messageReplyStoryHeader_layer173() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageReplyHeader.TL_messageReplyStoryHeader_layer173::class,
          org.Tajgram.tgnet.TLRPC.MessageReplyHeader::TLdeserialize, 173)

    }

    /**
     * Message-MessageMedia-StoryItem
     * StoryItem
     * UserFull-PeerStories-StoryItem
     * ChatFull-PeerStories-StoryItem
     */
    @Test
    public fun test_001722_StoryItem_TL_storyItem_layer173() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_StoryItem.TL_storyItem_layer173::class,
          org.Tajgram.tgnet.tl.TL_stories.StoryItem::TLdeserialize, 173)

    }

    /**
     * User-UserStatus
     */
    @Test
    public fun test_001723_UserStatus_TL_userStatusLastMonth_layer171() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_UserStatus.TL_userStatusLastMonth_layer171::class,
          org.Tajgram.tgnet.TLRPC.UserStatus::TLdeserialize, 171)

    }

    /**
     * User-UserStatus
     */
    @Test
    public fun test_001724_UserStatus_TL_userStatusLastWeek_layer171() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_UserStatus.TL_userStatusLastWeek_layer171::class,
          org.Tajgram.tgnet.TLRPC.UserStatus::TLdeserialize, 171)

    }

    /**
     * User-UserStatus
     */
    @Test
    public fun test_001725_UserStatus_TL_userStatusRecently_layer171() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_UserStatus.TL_userStatusRecently_layer171::class,
          org.Tajgram.tgnet.TLRPC.UserStatus::TLdeserialize, 171)

    }

    /**
     * Message
     */
    @Test
    public fun test_001726_Message_TL_message_layer169() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Message.TL_message_layer169::class,
          org.Tajgram.tgnet.TLRPC.Message::TLdeserialize, 169)

    }

    /**
     * Message-MessageFwdHeader
     */
    @Test
    public fun test_001727_MessageFwdHeader_TL_messageFwdHeader_layer169() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageFwdHeader.TL_messageFwdHeader_layer169::class,
          org.Tajgram.tgnet.TLRPC.MessageFwdHeader::TLdeserialize, 169)

    }

    /**
     * Message-ReplyMarkup-KeyboardButtonRow-KeyboardButton
     */
    @Test
    public fun test_001728_KeyboardButton_TL_keyboardButtonRequestPeer_layer168() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_KeyboardButton.TL_keyboardButtonRequestPeer_layer168::class,
          org.Tajgram.tgnet.TLRPC.KeyboardButton::TLdeserialize, 168)

    }

    /**
     * Message-MessageAction
     */
    @Test
    public fun test_001729_MessageAction_TL_messageActionRequestedPeer_layer168() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageAction.TL_messageActionRequestedPeer_layer168::class,
          org.Tajgram.tgnet.TLRPC.MessageAction::TLdeserialize, 168)

    }

    /**
     * Message-MessageMedia-WebPage-Page-PageBlock-Chat
     * StoryItem-MessageMedia-WebPage-Page-PageBlock-Chat
     * Chat
     * UserFull-PeerStories-StoryItem-MessageMedia-WebPage-Page-PageBlock-Chat
     * ChatFull-PeerStories-StoryItem-MessageMedia-WebPage-Page-PageBlock-Chat
     */
    @Test
    public fun test_001730_Chat_TL_channel_layer167() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Chat.TL_channel_layer167::class,
          org.Tajgram.tgnet.TLRPC.Chat::TLdeserialize, 167)

    }

    /**
     * ChatFull
     */
    @Test
    public fun test_001731_ChatFull_TL_channelFull_layer167() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ChatFull.TL_channelFull_layer167::class,
          org.Tajgram.tgnet.TLRPC.ChatFull::TLdeserialize, 167)

    }

    /**
     * InputStorePaymentPurpose
     */
    @Test
    public fun test_001732_InputStorePaymentPurpose_TL_inputStorePaymentPremiumGiveaway_layer167() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputStorePaymentPurpose.TL_inputStorePaymentPremiumGiveaway_layer167::class,
          org.Tajgram.tgnet.TLRPC.InputStorePaymentPurpose::TLdeserialize, 167)

    }

    /**
     * Message-MessageAction
     */
    @Test
    public fun test_001733_MessageAction_TL_messageActionGiftCode_layer167() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageAction.TL_messageActionGiftCode_layer167::class,
          org.Tajgram.tgnet.TLRPC.MessageAction::TLdeserialize, 167)

    }

    /**
     * Message-MessageMedia
     * StoryItem-MessageMedia
     * UserFull-PeerStories-StoryItem-MessageMedia
     * ChatFull-PeerStories-StoryItem-MessageMedia
     */
    @Test
    public fun test_001734_MessageMedia_TL_messageMediaGiveaway_layer167() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageMedia.TL_messageMediaGiveaway_layer167::class,
          org.Tajgram.tgnet.TLRPC.MessageMedia::TLdeserialize, 167)

    }

    /**
     * Message-MessageAction-WallPaper-WallPaperSettings
     * StoryItem-MessageMedia-WebPage-WebPageAttribute-ThemeSettings-WallPaper-WallPaperSettings
     * Theme-ThemeSettings-WallPaper-WallPaperSettings
     * UserFull-WallPaper-WallPaperSettings
     * ChatFull-PeerStories-StoryItem-MessageMedia-WebPage-WebPageAttribute-ThemeSettings-WallPaper-WallPaperSettings
     */
    @Test
    public fun test_001735_WallPaperSettings_TL_wallPaperSettings_layer167() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_WallPaperSettings.TL_wallPaperSettings_layer167::class,
          org.Tajgram.tgnet.TLRPC.WallPaperSettings::TLdeserialize, 167)

    }

    /**
     * Message-MessageMedia-WebPage-Page-PageBlock-Chat
     * StoryItem-MessageMedia-WebPage-Page-PageBlock-Chat
     * Chat
     * UserFull-PeerStories-StoryItem-MessageMedia-WebPage-Page-PageBlock-Chat
     * ChatFull-PeerStories-StoryItem-MessageMedia-WebPage-Page-PageBlock-Chat
     */
    @Test
    public fun test_001736_Chat_TL_channel_layer166() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Chat.TL_channel_layer166::class,
          org.Tajgram.tgnet.TLRPC.Chat::TLdeserialize, 166)

    }

    /**
     * ForumTopic-DraftMessage-InputReplyTo
     */
    @Test
    public fun test_001737_InputReplyTo_TL_inputReplyToMessage_layer166() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputReplyTo.TL_inputReplyToMessage_layer166::class,
          org.Tajgram.tgnet.TLRPC.InputReplyTo::TLdeserialize, 166)

    }

    /**
     * Message-MessageAction
     */
    @Test
    public fun test_001738_MessageAction_TL_messageActionSetChatWallPaper_layer166() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageAction.TL_messageActionSetChatWallPaper_layer166::class,
          org.Tajgram.tgnet.TLRPC.MessageAction::TLdeserialize, 166)

    }

    /**
     * Message-MessageAction
     */
    @Test
    public fun test_001739_MessageAction_TL_messageActionSetSameChatWallPaper_layer166() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageAction.TL_messageActionSetSameChatWallPaper_layer166::class,
          org.Tajgram.tgnet.TLRPC.MessageAction::TLdeserialize, 166)

    }

    /**
     * Message-MessageReplyHeader
     */
    @Test
    public fun test_001740_MessageReplyHeader_TL_messageReplyHeader_layer166() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageReplyHeader.TL_messageReplyHeader_layer166::class,
          org.Tajgram.tgnet.TLRPC.MessageReplyHeader::TLdeserialize, 166)

    }

    /**
     * Message-MessageMedia-StoryItem
     * StoryItem
     * UserFull-PeerStories-StoryItem
     * ChatFull-PeerStories-StoryItem
     */
    @Test
    public fun test_001741_StoryItem_TL_storyItem_layer166() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_StoryItem.TL_storyItem_layer166::class,
          org.Tajgram.tgnet.tl.TL_stories.StoryItem::TLdeserialize, 166)

    }

    /**
     * User
     */
    @Test
    public fun test_001742_User_TL_user_layer166() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_User.TL_user_layer166::class,
          org.Tajgram.tgnet.TLRPC.User::TLdeserialize, 166)

    }

    /**
     * Message-MessageMedia-WebPage-Page-PageBlock-Chat
     * StoryItem-MessageMedia-WebPage-Page-PageBlock-Chat
     * Chat
     * UserFull-PeerStories-StoryItem-MessageMedia-WebPage-Page-PageBlock-Chat
     * ChatFull-PeerStories-StoryItem-MessageMedia-WebPage-Page-PageBlock-Chat
     */
    @Test
    public fun test_001743_Chat_TL_channel_layer165() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Chat.TL_channel_layer165::class,
          org.Tajgram.tgnet.TLRPC.Chat::TLdeserialize, 165)

    }

    /**
     * ForumTopic-DraftMessage
     */
    @Test
    public fun test_001744_DraftMessage_TL_draftMessage_layer165() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_DraftMessage.TL_draftMessage_layer165::class,
          org.Tajgram.tgnet.TLRPC.DraftMessage::TLdeserialize, 165)

    }

    /**
     * Message-MessageMedia
     * StoryItem-MessageMedia
     * UserFull-PeerStories-StoryItem-MessageMedia
     * ChatFull-PeerStories-StoryItem-MessageMedia
     */
    @Test
    public fun test_001745_MessageMedia_TL_messageMediaWebPage_layer165() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageMedia.TL_messageMediaWebPage_layer165::class,
          org.Tajgram.tgnet.TLRPC.MessageMedia::TLdeserialize, 165)

    }

    /**
     * Message-MessageReplyHeader
     */
    @Test
    public fun test_001746_MessageReplyHeader_TL_messageReplyHeader_layer165() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageReplyHeader.TL_messageReplyHeader_layer165::class,
          org.Tajgram.tgnet.TLRPC.MessageReplyHeader::TLdeserialize, 165)

    }

    /**
     * User
     */
    @Test
    public fun test_001747_User_TL_user_layer165() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_User.TL_user_layer165::class,
          org.Tajgram.tgnet.TLRPC.User::TLdeserialize, 165)

    }

    /**
     * Message-MessageMedia-WebPage
     * StoryItem-MessageMedia-WebPage
     * UserFull-PeerStories-StoryItem-MessageMedia-WebPage
     * ChatFull-PeerStories-StoryItem-MessageMedia-WebPage
     */
    @Test
    public fun test_001748_WebPage_TL_webPageEmpty_layer165() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_WebPage.TL_webPageEmpty_layer165::class,
          org.Tajgram.tgnet.TLRPC.WebPage::TLdeserialize, 165)

    }

    /**
     * Message-MessageMedia-WebPage
     * StoryItem-MessageMedia-WebPage
     * UserFull-PeerStories-StoryItem-MessageMedia-WebPage
     * ChatFull-PeerStories-StoryItem-MessageMedia-WebPage
     */
    @Test
    public fun test_001749_WebPage_TL_webPagePending_layer165() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_WebPage.TL_webPagePending_layer165::class,
          org.Tajgram.tgnet.TLRPC.WebPage::TLdeserialize, 165)

    }

    /**
     * Message-MessageMedia-WebPage-Page-PageBlock-Chat
     * StoryItem-MessageMedia-WebPage-Page-PageBlock-Chat
     * Chat
     * UserFull-UserStories-StoryItem-MessageMedia-WebPage-Page-PageBlock-Chat
     */
    @Test
    public fun test_001750_Chat_TL_channel_layer163() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Chat.TL_channel_layer163::class,
          org.Tajgram.tgnet.TLRPC.Chat::TLdeserialize, 163)

    }

    /**
     * ChatFull
     */
    @Test
    public fun test_001751_ChatFull_TL_channelFull_layer163() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ChatFull.TL_channelFull_layer163::class,
          org.Tajgram.tgnet.TLRPC.ChatFull::TLdeserialize, 163)

    }

    /**
     * Message-MessageMedia
     * StoryItem-MessageMedia
     * UserFull-UserStories-StoryItem-MessageMedia
     */
    @Test
    public fun test_001752_MessageMedia_TL_messageMediaStory_layer163() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageMedia.TL_messageMediaStory_layer163::class,
          org.Tajgram.tgnet.TLRPC.MessageMedia::TLdeserialize, 163)

    }

    /**
     * Message-MessageMedia-StoryItem-StoryViews
     * StoryItem-StoryViews
     * UserFull-UserStories-StoryItem-StoryViews
     */
    @Test
    public fun test_001753_StoryViews_TL_storyViews_layer163() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_StoryViews.TL_storyViews_layer163::class,
          org.Tajgram.tgnet.tl.TL_stories.StoryViews::TLdeserialize, 163)

    }

    /**
     * UserFull
     */
    @Test
    public fun test_001754_UserFull_TL_userFull_layer163() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_UserFull.TL_userFull_layer163::class,
          org.Tajgram.tgnet.TLRPC.UserFull::TLdeserialize, 163)

    }

    /**
     * UserFull-UserStories
     */
    @Test
    public fun test_001755_UserStories_TL_userStories_layer163() {
      assumeTrue("Test skipped, link error", false)

    }

    /**
     * Message-MessageMedia-WebPage-WebPageAttribute
     * StoryItem-MessageMedia-WebPage-WebPageAttribute
     * UserFull-UserStories-StoryItem-MessageMedia-WebPage-WebPageAttribute
     */
    @Test
    public fun test_001756_WebPageAttribute_TL_webPageAttributeStory_layer163() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_WebPageAttribute.TL_webPageAttributeStory_layer163::class,
          org.Tajgram.tgnet.TLRPC.WebPageAttribute::TLdeserialize, 163)

    }

    /**
     * Message-MessageMedia-StoryItem
     * StoryItem
     * UserFull-UserStories-StoryItem
     */
    @Test
    public fun test_001757_StoryItem_TL_storyItem_layer160() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_StoryItem.TL_storyItem_layer160::class,
          org.Tajgram.tgnet.tl.TL_stories.StoryItem::TLdeserialize, 160)

    }

    /**
     * Message-MessageMedia-StoryItem-StoryViews
     * StoryItem-StoryViews
     * UserFull-UserStories-StoryItem-StoryViews
     */
    @Test
    public fun test_001758_StoryViews_TL_storyViews_layer160() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_StoryViews.TL_storyViews_layer160::class,
          org.Tajgram.tgnet.tl.TL_stories.StoryViews::TLdeserialize, 160)

    }

    /**
     * Message-MessageMedia-Document-DocumentAttribute
     * Theme-Document-DocumentAttribute
     * UserFull-BotInfo-Document-DocumentAttribute
     * ChatFull-BotInfo-Document-DocumentAttribute
     */
    @Test
    public fun test_001759_DocumentAttribute_TL_documentAttributeVideo_layer159() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_DocumentAttribute.TL_documentAttributeVideo_layer159::class,
          org.Tajgram.tgnet.TLRPC.DocumentAttribute::TLdeserialize, 159)

    }

    /**
     * Message-MessageMedia
     */
    @Test
    public fun test_001760_MessageMedia_TL_messageMediaDocument_layer159() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageMedia.TL_messageMediaDocument_layer159::class,
          org.Tajgram.tgnet.TLRPC.MessageMedia::TLdeserialize, 159)

    }

    /**
     * UserFull-PeerNotifySettings
     * ChatFull-PeerNotifySettings
     * ForumTopic-PeerNotifySettings
     */
    @Test
    public fun test_001761_PeerNotifySettings_TL_peerNotifySettings_layer159() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_PeerNotifySettings.TL_peerNotifySettings_layer159::class,
          org.Tajgram.tgnet.TLRPC.PeerNotifySettings::TLdeserialize, 159)

    }

    /**
     * User
     */
    @Test
    public fun test_001762_User_TL_user_layer159() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_User.TL_user_layer159::class,
          org.Tajgram.tgnet.TLRPC.User::TLdeserialize, 159)

    }

    /**
     * UserFull
     */
    @Test
    public fun test_001763_UserFull_TL_userFull_layer159() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_UserFull.TL_userFull_layer159::class,
          org.Tajgram.tgnet.TLRPC.UserFull::TLdeserialize, 159)

    }

    /**
     * Message-MessageMedia-PollResults
     */
    @Test
    public fun test_001764_PollResults_TL_pollResults_layer158() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_PollResults.TL_pollResults_layer158::class,
          org.Tajgram.tgnet.TLRPC.PollResults::TLdeserialize, 158)

    }

    /**
     * Message-ReplyMarkup-KeyboardButtonRow-KeyboardButton
     */
    @Test
    public fun test_001765_KeyboardButton_TL_keyboardButtonSwitchInline_layer157() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_KeyboardButton.TL_keyboardButtonSwitchInline_layer157::class,
          org.Tajgram.tgnet.TLRPC.KeyboardButton::TLdeserialize, 157)

    }

    /**
     * UserFull
     */
    @Test
    public fun test_001766_UserFull_TL_userFull_layer157() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_UserFull.TL_userFull_layer157::class,
          org.Tajgram.tgnet.TLRPC.UserFull::TLdeserialize, 157)

    }

    /**
     * Message-MessageAction
     */
    @Test
    public fun test_001767_MessageAction_TL_messageActionGiftPremium_layer156() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageAction.TL_messageActionGiftPremium_layer156::class,
          org.Tajgram.tgnet.TLRPC.MessageAction::TLdeserialize, 156)

    }

    /**
     * Message-MessageReactions-MessagePeerReaction
     */
    @Test
    public fun test_001768_MessagePeerReaction_TL_messagePeerReaction_layer154() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessagePeerReaction.TL_messagePeerReaction_layer154::class,
          org.Tajgram.tgnet.TLRPC.MessagePeerReaction::TLdeserialize, 154)

    }

    /**
     * Message-MessageAction
     */
    @Test
    public fun test_001769_MessageAction_TL_messageActionAttachMenuBotAllowed_layer153() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageAction.TL_messageActionAttachMenuBotAllowed_layer153::class,
          org.Tajgram.tgnet.TLRPC.MessageAction::TLdeserialize, 153)

    }

    /**
     * Message-MessageAction
     */
    @Test
    public fun test_001770_MessageAction_TL_messageActionBotAllowed_layer153() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageAction.TL_messageActionBotAllowed_layer153::class,
          org.Tajgram.tgnet.TLRPC.MessageAction::TLdeserialize, 153)

    }

    /**
     * UserFull
     */
    @Test
    public fun test_001771_UserFull_TL_userFull_layer150() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_UserFull.TL_userFull_layer150::class,
          org.Tajgram.tgnet.TLRPC.UserFull::TLdeserialize, 150)

    }

    /**
     * Message-MessageAction
     */
    @Test
    public fun test_001772_MessageAction_TL_messageActionSetMessagesTTL_layer149() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageAction.TL_messageActionSetMessagesTTL_layer149::class,
          org.Tajgram.tgnet.TLRPC.MessageAction::TLdeserialize, 149)

    }

    /**
     * Message-MessageAction
     */
    @Test
    public fun test_001773_MessageAction_TL_messageActionTopicEdit_layer149() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageAction.TL_messageActionTopicEdit_layer149::class,
          org.Tajgram.tgnet.TLRPC.MessageAction::TLdeserialize, 149)

    }

    /**
     * Message-MessageMedia-WebPage-Page-PageBlock-Chat
     * Chat
     */
    @Test
    public fun test_001774_Chat_TL_channel_layer147() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Chat.TL_channel_layer147::class,
          org.Tajgram.tgnet.TLRPC.Chat::TLdeserialize, 147)

    }

    /**
     * User
     */
    @Test
    public fun test_001775_User_TL_user_layer147() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_User.TL_user_layer147::class,
          org.Tajgram.tgnet.TLRPC.User::TLdeserialize, 147)

    }

    /**
     * Message-MessageMedia
     */
    @Test
    public fun test_001776_MessageMedia_TL_messageMediaInvoice_layer145() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageMedia.TL_messageMediaInvoice_layer145::class,
          org.Tajgram.tgnet.TLRPC.MessageMedia::TLdeserialize, 145)

    }

    /**
     * ChatFull
     */
    @Test
    public fun test_001777_ChatFull_TL_channelFull_layer144() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ChatFull.TL_channelFull_layer144::class,
          org.Tajgram.tgnet.TLRPC.ChatFull::TLdeserialize, 144)

    }

    /**
     * ChatFull
     */
    @Test
    public fun test_001778_ChatFull_TL_chatFull_layer144() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ChatFull.TL_chatFull_layer144::class,
          org.Tajgram.tgnet.TLRPC.ChatFull::TLdeserialize, 144)

    }

    /**
     * Message-MessageReactions-MessagePeerReaction
     */
    @Test
    public fun test_001779_MessagePeerReaction_TL_messagePeerReaction_layer144() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessagePeerReaction.TL_messagePeerReaction_layer144::class,
          org.Tajgram.tgnet.TLRPC.MessagePeerReaction::TLdeserialize, 144)

    }

    /**
     * Message-MessageReactions-ReactionCount
     */
    @Test
    public fun test_001780_ReactionCount_TL_reactionCount_layer144() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ReactionCount.TL_reactionCount_layer144::class,
          org.Tajgram.tgnet.TLRPC.ReactionCount::TLdeserialize, 144)

    }

    /**
     * User
     */
    @Test
    public fun test_001781_User_TL_user_layer144() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_User.TL_user_layer144::class,
          org.Tajgram.tgnet.TLRPC.User::TLdeserialize, 144)

    }

    /**
     * ChatFull-StickerSet
     */
    @Test
    public fun test_001783_StickerSet_TL_stickerSet_layer143() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_StickerSet.TL_stickerSet_layer143::class,
          org.Tajgram.tgnet.TLRPC.StickerSet::TLdeserialize, 143)

    }

    /**
     * UserFull
     */
    @Test
    public fun test_001784_UserFull_TL_userFull_layer143() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_UserFull.TL_userFull_layer143::class,
          org.Tajgram.tgnet.TLRPC.UserFull::TLdeserialize, 143)

    }

    /**
     * UserFull-BotInfo
     * ChatFull-BotInfo
     */
    @Test
    public fun test_001785_BotInfo_TL_botInfo_layer142() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_BotInfo.TL_botInfo_layer142::class,
          org.Tajgram.tgnet.tl.TL_bots.BotInfo::TLdeserialize, 142)

    }

    /**
     * Message-MessageMedia-Document
     * Theme-Document
     */
    @Test
    public fun test_001786_Document_TL_document_layer142() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Document.TL_document_layer142::class,
          org.Tajgram.tgnet.TLRPC.Document::TLdeserialize, 142)

    }

    /**
     * Message-MessageAction
     */
    @Test
    public fun test_001787_MessageAction_TL_messageActionPaymentSent_layer142() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageAction.TL_messageActionPaymentSent_layer142::class,
          org.Tajgram.tgnet.TLRPC.MessageAction::TLdeserialize, 142)

    }

    /**
     * Message-MessageAction-SecureValue-SecureFile
     */
    @Test
    public fun test_001788_SecureFile_TL_secureFile_layer142() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_SecureFile.TL_secureFile_layer142::class,
          org.Tajgram.tgnet.TLRPC.SecureFile::TLdeserialize, 142)

    }

    /**
     * UserFull-BotInfo
     * ChatFull-BotInfo
     */
    @Test
    public fun test_001789_BotInfo_TL_botInfo_layer139() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_BotInfo.TL_botInfo_layer139::class,
          org.Tajgram.tgnet.tl.TL_bots.BotInfo::TLdeserialize, 139)

    }

    /**
     * ChatFull
     */
    @Test
    public fun test_001790_ChatFull_TL_channelFull_layer139() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ChatFull.TL_channelFull_layer139::class,
          org.Tajgram.tgnet.TLRPC.ChatFull::TLdeserialize, 139)

    }

    /**
     * UserFull-PeerNotifySettings
     * ChatFull-PeerNotifySettings
     */
    @Test
    public fun test_001791_PeerNotifySettings_TL_peerNotifySettings_layer139() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_PeerNotifySettings.TL_peerNotifySettings_layer139::class,
          org.Tajgram.tgnet.TLRPC.PeerNotifySettings::TLdeserialize, 139)

    }

    /**
     * UserFull
     */
    @Test
    public fun test_001792_UserFull_TL_userFull_layer139() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_UserFull.TL_userFull_layer139::class,
          org.Tajgram.tgnet.TLRPC.UserFull::TLdeserialize, 139)

    }

    /**
     * Message-MessageReactions
     */
    @Test
    public fun test_001793_MessageReactions_TL_messageReactions_layer137() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageReactions.TL_messageReactions_layer137::class,
          org.Tajgram.tgnet.TLRPC.MessageReactions::TLdeserialize, 137)

    }

    /**
     * Message-MessageReactions-MessageUserReaction
     */
    @Test
    public fun test_001794_MessageUserReaction_TL_messageUserReaction_layer137() {
      assumeTrue("Test skipped, link error", false)

    }

    /**
     * ChatFull
     */
    @Test
    public fun test_001795_ChatFull_TL_channelFull_layer135() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ChatFull.TL_channelFull_layer135::class,
          org.Tajgram.tgnet.TLRPC.ChatFull::TLdeserialize, 135)

    }

    /**
     * ChatFull
     */
    @Test
    public fun test_001796_ChatFull_TL_chatFull_layer135() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ChatFull.TL_chatFull_layer135::class,
          org.Tajgram.tgnet.TLRPC.ChatFull::TLdeserialize, 135)

    }

    /**
     * Message
     */
    @Test
    public fun test_001797_Message_TL_message_layer135() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Message.TL_message_layer135::class,
          org.Tajgram.tgnet.TLRPC.Message::TLdeserialize, 135)

    }

    /**
     * ChatFull
     */
    @Test
    public fun test_001798_ChatFull_TL_channelFull_layer134() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ChatFull.TL_channelFull_layer134::class,
          org.Tajgram.tgnet.TLRPC.ChatFull::TLdeserialize, 134)

    }

    /**
     * UserFull-PeerSettings
     */
    @Test
    public fun test_001799_PeerSettings_TL_peerSettings_layer134() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_PeerSettings.TL_peerSettings_layer134::class,
          org.Tajgram.tgnet.TLRPC.PeerSettings::TLdeserialize, 134)

    }

    /**
     * UserFull
     */
    @Test
    public fun test_001800_UserFull_TL_userFull_layer134() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_UserFull.TL_userFull_layer134::class,
          org.Tajgram.tgnet.TLRPC.UserFull::TLdeserialize, 134)

    }

    /**
     * ChatFull
     */
    @Test
    public fun test_001801_ChatFull_TL_channelFull_layer133() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ChatFull.TL_channelFull_layer133::class,
          org.Tajgram.tgnet.TLRPC.ChatFull::TLdeserialize, 133)

    }

    /**
     * ChatFull
     */
    @Test
    public fun test_001802_ChatFull_TL_chatFull_layer133() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ChatFull.TL_chatFull_layer133::class,
          org.Tajgram.tgnet.TLRPC.ChatFull::TLdeserialize, 133)

    }

    /**
     * ChatFull-ExportedChatInvite
     */
    @Test
    public fun test_001803_ExportedChatInvite_TL_chatInviteExported_layer133() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ExportedChatInvite.TL_chatInviteExported_layer133::class,
          org.Tajgram.tgnet.TLRPC.ExportedChatInvite::TLdeserialize, 133)

    }

    /**
     * Theme
     */
    @Test
    public fun test_001804_Theme_TL_theme_layer133() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Theme.TL_theme_layer133::class,
          org.Tajgram.tgnet.TLRPC.Theme::TLdeserialize, 133)

    }

    /**
     * UserFull-BotInfo
     * ChatFull-BotInfo
     */
    @Test
    public fun test_001805_BotInfo_TL_botInfo_layer132() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_BotInfo.TL_botInfo_layer132::class,
          org.Tajgram.tgnet.tl.TL_bots.BotInfo::TLdeserialize, 132)

    }

    /**
     * Message-MessageMedia-WebPage-Page-PageBlock-Chat
     * Chat
     */
    @Test
    public fun test_001806_Chat_TL_channel_layer132() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Chat.TL_channel_layer132::class,
          org.Tajgram.tgnet.TLRPC.Chat::TLdeserialize, 132)

    }

    /**
     * Message-MessageMedia-WebPage-Page-PageBlock-Chat
     * Chat
     */
    @Test
    public fun test_001807_Chat_TL_channelForbidden_layer132() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Chat.TL_channelForbidden_layer132::class,
          org.Tajgram.tgnet.TLRPC.Chat::TLdeserialize, 132)

    }

    /**
     * Message-MessageMedia-WebPage-Page-PageBlock-Chat
     * Chat
     */
    @Test
    public fun test_001808_Chat_TL_chat_layer132() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Chat.TL_chat_layer132::class,
          org.Tajgram.tgnet.TLRPC.Chat::TLdeserialize, 132)

    }

    /**
     * Message-MessageMedia-WebPage-Page-PageBlock-Chat
     * Chat
     */
    @Test
    public fun test_001809_Chat_TL_chatEmpty_layer132() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Chat.TL_chatEmpty_layer132::class,
          org.Tajgram.tgnet.TLRPC.Chat::TLdeserialize, 132)

    }

    /**
     * Message-MessageMedia-WebPage-Page-PageBlock-Chat
     * Chat
     */
    @Test
    public fun test_001810_Chat_TL_chatForbidden_layer132() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Chat.TL_chatForbidden_layer132::class,
          org.Tajgram.tgnet.TLRPC.Chat::TLdeserialize, 132)

    }

    /**
     * ChatFull
     */
    @Test
    public fun test_001811_ChatFull_TL_channelFull_layer132() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ChatFull.TL_channelFull_layer132::class,
          org.Tajgram.tgnet.TLRPC.ChatFull::TLdeserialize, 132)

    }

    /**
     * ChatFull
     */
    @Test
    public fun test_001812_ChatFull_TL_chatFull_layer132() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ChatFull.TL_chatFull_layer132::class,
          org.Tajgram.tgnet.TLRPC.ChatFull::TLdeserialize, 132)

    }

    /**
     * ChatFull-ChatParticipants-ChatParticipant
     */
    @Test
    public fun test_001813_ChatParticipant_TL_chatParticipant_layer132() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ChatParticipant.TL_chatParticipant_layer132::class,
          org.Tajgram.tgnet.TLRPC.ChatParticipant::TLdeserialize, 132)

    }

    /**
     * ChatFull-ChatParticipants-ChatParticipant
     */
    @Test
    public fun test_001814_ChatParticipant_TL_chatParticipantAdmin_layer132() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ChatParticipant.TL_chatParticipantAdmin_layer132::class,
          org.Tajgram.tgnet.TLRPC.ChatParticipant::TLdeserialize, 132)

    }

    /**
     * ChatFull-ChatParticipants-ChatParticipant
     */
    @Test
    public fun test_001815_ChatParticipant_TL_chatParticipantCreator_layer132() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ChatParticipant.TL_chatParticipantCreator_layer132::class,
          org.Tajgram.tgnet.TLRPC.ChatParticipant::TLdeserialize, 132)

    }

    /**
     * ChatFull-ChatParticipants
     */
    @Test
    public fun test_001816_ChatParticipants_TL_chatParticipants_layer132() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ChatParticipants.TL_chatParticipants_layer132::class,
          org.Tajgram.tgnet.TLRPC.ChatParticipants::TLdeserialize, 132)

    }

    /**
     * ChatFull-ChatParticipants
     */
    @Test
    public fun test_001817_ChatParticipants_TL_chatParticipantsForbidden_layer132() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ChatParticipants.TL_chatParticipantsForbidden_layer132::class,
          org.Tajgram.tgnet.TLRPC.ChatParticipants::TLdeserialize, 132)

    }

    /**
     * ChatFull-ExportedChatInvite
     */
    @Test
    public fun test_001818_ExportedChatInvite_TL_chatInviteExported_layer132() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ExportedChatInvite.TL_chatInviteExported_layer132::class,
          org.Tajgram.tgnet.TLRPC.ExportedChatInvite::TLdeserialize, 132)

    }

    /**
     * Message-MessageMedia-WebPage-Page-PageBlock-Chat-InputChannel
     * Chat-InputChannel
     */
    @Test
    public fun test_001819_InputChannel_TL_inputChannel_layer132() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputChannel.TL_inputChannel_layer132::class,
          org.Tajgram.tgnet.TLRPC.InputChannel::TLdeserialize, 132)

    }

    /**
     * Message-MessageMedia-WebPage-Page-PageBlock-Chat-InputChannel
     * Chat-InputChannel
     */
    @Test
    public fun test_001820_InputChannel_TL_inputChannelFromMessage_layer132() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputChannel.TL_inputChannelFromMessage_layer132::class,
          org.Tajgram.tgnet.TLRPC.InputChannel::TLdeserialize, 132)

    }

    /**
     * Message-MessageEntity-InputUser-InputPeer
     * Chat-InputChannel-InputPeer
     */
    @Test
    public fun test_001821_InputPeer_TL_inputPeerChannel_layer132() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputPeer.TL_inputPeerChannel_layer132::class,
          org.Tajgram.tgnet.TLRPC.InputPeer::TLdeserialize, 132)

    }

    /**
     * Message-MessageEntity-InputUser-InputPeer
     * Chat-InputChannel-InputPeer
     */
    @Test
    public fun test_001822_InputPeer_TL_inputPeerChannelFromMessage_layer132() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputPeer.TL_inputPeerChannelFromMessage_layer132::class,
          org.Tajgram.tgnet.TLRPC.InputPeer::TLdeserialize, 132)

    }

    /**
     * Message-MessageEntity-InputUser-InputPeer
     * Chat-InputChannel-InputPeer
     */
    @Test
    public fun test_001823_InputPeer_TL_inputPeerChat_layer132() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputPeer.TL_inputPeerChat_layer132::class,
          org.Tajgram.tgnet.TLRPC.InputPeer::TLdeserialize, 132)

    }

    /**
     * Message-MessageEntity-InputUser-InputPeer
     * Chat-InputChannel-InputPeer
     */
    @Test
    public fun test_001824_InputPeer_TL_inputPeerUser_layer132() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputPeer.TL_inputPeerUser_layer132::class,
          org.Tajgram.tgnet.TLRPC.InputPeer::TLdeserialize, 132)

    }

    /**
     * Message-MessageEntity-InputUser-InputPeer
     * Chat-InputChannel-InputPeer
     */
    @Test
    public fun test_001825_InputPeer_TL_inputPeerUserFromMessage_layer132() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputPeer.TL_inputPeerUserFromMessage_layer132::class,
          org.Tajgram.tgnet.TLRPC.InputPeer::TLdeserialize, 132)

    }

    /**
     * Message-MessageEntity-InputUser
     */
    @Test
    public fun test_001826_InputUser_TL_inputUser_layer132() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputUser.TL_inputUser_layer132::class,
          org.Tajgram.tgnet.TLRPC.InputUser::TLdeserialize, 132)

    }

    /**
     * Message-MessageEntity-InputUser
     */
    @Test
    public fun test_001827_InputUser_TL_inputUserFromMessage_layer132() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputUser.TL_inputUserFromMessage_layer132::class,
          org.Tajgram.tgnet.TLRPC.InputUser::TLdeserialize, 132)

    }

    /**
     * Message
     */
    @Test
    public fun test_001828_Message_TL_message_layer132() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Message.TL_message_layer132::class,
          org.Tajgram.tgnet.TLRPC.Message::TLdeserialize, 132)

    }

    /**
     * Message-MessageAction
     */
    @Test
    public fun test_001829_MessageAction_TL_messageActionChannelMigrateFrom_layer132() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageAction.TL_messageActionChannelMigrateFrom_layer132::class,
          org.Tajgram.tgnet.TLRPC.MessageAction::TLdeserialize, 132)

    }

    /**
     * Message-MessageAction
     */
    @Test
    public fun test_001830_MessageAction_TL_messageActionChatAddUser_layer132() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageAction.TL_messageActionChatAddUser_layer132::class,
          org.Tajgram.tgnet.TLRPC.MessageAction::TLdeserialize, 132)

    }

    /**
     * Message-MessageAction
     */
    @Test
    public fun test_001831_MessageAction_TL_messageActionChatCreate_layer132() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageAction.TL_messageActionChatCreate_layer132::class,
          org.Tajgram.tgnet.TLRPC.MessageAction::TLdeserialize, 132)

    }

    /**
     * Message-MessageAction
     */
    @Test
    public fun test_001832_MessageAction_TL_messageActionChatDeleteUser_layer132() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageAction.TL_messageActionChatDeleteUser_layer132::class,
          org.Tajgram.tgnet.TLRPC.MessageAction::TLdeserialize, 132)

    }

    /**
     * Message-MessageAction
     */
    @Test
    public fun test_001833_MessageAction_TL_messageActionChatJoinedByLink_layer132() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageAction.TL_messageActionChatJoinedByLink_layer132::class,
          org.Tajgram.tgnet.TLRPC.MessageAction::TLdeserialize, 132)

    }

    /**
     * Message-MessageAction
     */
    @Test
    public fun test_001834_MessageAction_TL_messageActionChatMigrateTo_layer132() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageAction.TL_messageActionChatMigrateTo_layer132::class,
          org.Tajgram.tgnet.TLRPC.MessageAction::TLdeserialize, 132)

    }

    /**
     * Message-MessageAction
     */
    @Test
    public fun test_001835_MessageAction_TL_messageActionInviteToGroupCall_layer132() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageAction.TL_messageActionInviteToGroupCall_layer132::class,
          org.Tajgram.tgnet.TLRPC.MessageAction::TLdeserialize, 132)

    }

    /**
     * Message-MessageEntity
     */
    @Test
    public fun test_001836_MessageEntity_TL_messageEntityMentionName_layer132() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageEntity.TL_messageEntityMentionName_layer132::class,
          org.Tajgram.tgnet.TLRPC.MessageEntity::TLdeserialize, 132)

    }

    /**
     * Message-MessageMedia
     */
    @Test
    public fun test_001837_MessageMedia_TL_messageMediaContact_layer132() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageMedia.TL_messageMediaContact_layer132::class,
          org.Tajgram.tgnet.TLRPC.MessageMedia::TLdeserialize, 132)

    }

    /**
     * Message-MessageReplies
     */
    @Test
    public fun test_001838_MessageReplies_TL_messageReplies_layer132() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageReplies.TL_messageReplies_layer132::class,
          org.Tajgram.tgnet.TLRPC.MessageReplies::TLdeserialize, 132)

    }

    /**
     * Message-Peer
     * ChatFull-Peer
     */
    @Test
    public fun test_001839_Peer_TL_peerChannel_layer132() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Peer.TL_peerChannel_layer132::class,
          org.Tajgram.tgnet.TLRPC.Peer::TLdeserialize, 132)

    }

    /**
     * Message-Peer
     * ChatFull-Peer
     */
    @Test
    public fun test_001840_Peer_TL_peerChat_layer132() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Peer.TL_peerChat_layer132::class,
          org.Tajgram.tgnet.TLRPC.Peer::TLdeserialize, 132)

    }

    /**
     * Message-Peer
     * ChatFull-Peer
     */
    @Test
    public fun test_001841_Peer_TL_peerUser_layer132() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Peer.TL_peerUser_layer132::class,
          org.Tajgram.tgnet.TLRPC.Peer::TLdeserialize, 132)

    }

    /**
     * Message-MessageMedia-PollResults
     */
    @Test
    public fun test_001842_PollResults_TL_pollResults_layer132() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_PollResults.TL_pollResults_layer132::class,
          org.Tajgram.tgnet.TLRPC.PollResults::TLdeserialize, 132)

    }

    /**
     * Message-MessageMedia-WebPage-WebPageAttribute-ThemeSettings
     * Theme-ThemeSettings
     */
    @Test
    public fun test_001843_ThemeSettings_TL_themeSettings_layer132() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ThemeSettings.TL_themeSettings_layer132::class,
          org.Tajgram.tgnet.TLRPC.ThemeSettings::TLdeserialize, 132)
          test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ThemeSettings.TL_themeSettings_layer132::class,
              org.Tajgram.tgnet.TLRPC.TL_themeSettings::TLdeserialize, 132)

    }

    /**
     * User
     * UserFull-User
     */
    @Test
    public fun test_001844_User_TL_user_layer132() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_User.TL_user_layer132::class,
          org.Tajgram.tgnet.TLRPC.User::TLdeserialize, 132)

    }

    /**
     * User
     * UserFull-User
     */
    @Test
    public fun test_001845_User_TL_userEmpty_layer132() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_User.TL_userEmpty_layer132::class,
          org.Tajgram.tgnet.TLRPC.User::TLdeserialize, 132)

    }

    /**
     * ChatFull
     */
    @Test
    public fun test_001846_ChatFull_TL_channelFull_layer131() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ChatFull.TL_channelFull_layer131::class,
          org.Tajgram.tgnet.TLRPC.ChatFull::TLdeserialize, 131)

    }

    /**
     * ChatFull
     */
    @Test
    public fun test_001847_ChatFull_TL_chatFull_layer131() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ChatFull.TL_chatFull_layer131::class,
          org.Tajgram.tgnet.TLRPC.ChatFull::TLdeserialize, 131)

    }

    /**
     * Theme
     */
    @Test
    public fun test_001848_Theme_TL_theme_layer131() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Theme.TL_theme_layer131::class,
          org.Tajgram.tgnet.TLRPC.Theme::TLdeserialize, 131)

    }

    /**
     * Message-MessageMedia-WebPage-WebPageAttribute-ThemeSettings
     * Theme-ThemeSettings
     */
    @Test
    public fun test_001849_ThemeSettings_TL_themeSettings_layer131() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ThemeSettings.TL_themeSettings_layer131::class,
          org.Tajgram.tgnet.TLRPC.ThemeSettings::TLdeserialize, 131)
          test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ThemeSettings.TL_themeSettings_layer131::class,
              org.Tajgram.tgnet.TLRPC.TL_themeSettings::TLdeserialize, 131)

    }

    /**
     * UserFull
     */
    @Test
    public fun test_001850_UserFull_TL_userFull_layer131() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_UserFull.TL_userFull_layer131::class,
          org.Tajgram.tgnet.TLRPC.UserFull::TLdeserialize, 131)

    }

    /**
     * Message-ReplyMarkup
     */
    @Test
    public fun test_001851_ReplyMarkup_TL_replyKeyboardForceReply_layer129() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ReplyMarkup.TL_replyKeyboardForceReply_layer129::class,
          org.Tajgram.tgnet.TLRPC.ReplyMarkup::TLdeserialize, 129)

    }

    /**
     * Message-ReplyMarkup
     */
    @Test
    public fun test_001852_ReplyMarkup_TL_replyKeyboardMarkup_layer129() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ReplyMarkup.TL_replyKeyboardMarkup_layer129::class,
          org.Tajgram.tgnet.TLRPC.ReplyMarkup::TLdeserialize, 129)

    }

    /**
     * Message-MessageMedia-WebPage-WebPageAttribute-ThemeSettings-WallPaper
     * Theme-ThemeSettings-WallPaper
     */
    @Test
    public fun test_001853_WallPaper_TL_wallPaperNoFile_layer128() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_WallPaper.TL_wallPaperNoFile_layer128::class,
          org.Tajgram.tgnet.TLRPC.WallPaper::TLdeserialize, 128)

    }

    /**
     * Message-MessageMedia-WebPage-WebPageAttribute-ThemeSettings-WallPaper-WallPaperSettings
     * Theme-ThemeSettings-WallPaper-WallPaperSettings
     */
    @Test
    public fun test_001854_WallPaperSettings_TL_wallPaperSettings_layer128() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_WallPaperSettings.TL_wallPaperSettings_layer128::class,
          org.Tajgram.tgnet.TLRPC.WallPaperSettings::TLdeserialize, 128)

    }

    /**
     * Message-MessageMedia-WebPage-Page-PageBlock-Chat-ChatPhoto
     * Chat-ChatPhoto
     */
    @Test
    public fun test_001855_ChatPhoto_TL_chatPhoto_layer127() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ChatPhoto.TL_chatPhoto_layer127::class,
          org.Tajgram.tgnet.TLRPC.ChatPhoto::TLdeserialize, 127)

    }

    /**
     * Message-MessageMedia-Photo-PhotoSize-FileLocation
     * Theme-Document-PhotoSize-FileLocation
     * Chat-ChatPhoto-FileLocation
     * User-UserProfilePhoto-FileLocation
     * UserFull-User-UserProfilePhoto-FileLocation
     * ChatFull-Photo-PhotoSize-FileLocation
     */
    @Test
    public fun test_001856_FileLocation_TL_fileLocationToBeDeprecated_layer127() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_FileLocation.TL_fileLocationToBeDeprecated_layer127::class,
          org.Tajgram.tgnet.TLRPC.FileLocation::TLdeserialize, 127)

    }

    /**
     * Message-MessageMedia-Photo-PhotoSize
     * Theme-Document-PhotoSize
     * UserFull-Photo-PhotoSize
     * ChatFull-Photo-PhotoSize
     */
    @Test
    public fun test_001857_PhotoSize_TL_photoCachedSize_layer127() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_PhotoSize.TL_photoCachedSize_layer127::class,
          org.Tajgram.tgnet.TLRPC.PhotoSize::TLdeserialize, 127)

    }

    /**
     * Message-MessageMedia-Photo-PhotoSize
     * Theme-Document-PhotoSize
     * UserFull-Photo-PhotoSize
     * ChatFull-Photo-PhotoSize
     */
    @Test
    public fun test_001858_PhotoSize_TL_photoSize_layer127() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_PhotoSize.TL_photoSize_layer127::class,
          org.Tajgram.tgnet.TLRPC.PhotoSize::TLdeserialize, 127)

    }

    /**
     * Message-MessageMedia-Photo-PhotoSize
     * Theme-Document-PhotoSize
     * UserFull-Photo-PhotoSize
     * ChatFull-Photo-PhotoSize
     */
    @Test
    public fun test_001859_PhotoSize_TL_photoSizeProgressive_layer127() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_PhotoSize.TL_photoSizeProgressive_layer127::class,
          org.Tajgram.tgnet.TLRPC.PhotoSize::TLdeserialize, 127)

    }

    /**
     * User-UserProfilePhoto
     * UserFull-User-UserProfilePhoto
     */
    @Test
    public fun test_001860_UserProfilePhoto_TL_userProfilePhoto_layer127() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_UserProfilePhoto.TL_userProfilePhoto_layer127::class,
          org.Tajgram.tgnet.TLRPC.UserProfilePhoto::TLdeserialize, 127)

    }

    /**
     * Message-MessageMedia-Photo-VideoSize
     * Theme-Document-VideoSize
     * UserFull-Photo-VideoSize
     * ChatFull-Photo-VideoSize
     */
    @Test
    public fun test_001861_VideoSize_TL_videoSize_layer127() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_VideoSize.TL_videoSize_layer127::class,
          org.Tajgram.tgnet.TLRPC.VideoSize::TLdeserialize, 127)

    }

    /**
     * Message-MessageMedia-WebPage-Page-PageBlock-Chat-ChatPhoto
     * Chat-ChatPhoto
     */
    @Test
    public fun test_001862_ChatPhoto_TL_chatPhoto_layer126() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ChatPhoto.TL_chatPhoto_layer126::class,
          org.Tajgram.tgnet.TLRPC.ChatPhoto::TLdeserialize, 126)

    }

    /**
     * ChatFull-StickerSet
     */
    @Test
    public fun test_001863_StickerSet_TL_stickerSet_layer126() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_StickerSet.TL_stickerSet_layer126::class,
          org.Tajgram.tgnet.TLRPC.StickerSet::TLdeserialize, 126)

    }

    /**
     * User-UserProfilePhoto
     * UserFull-User-UserProfilePhoto
     */
    @Test
    public fun test_001864_UserProfilePhoto_TL_userProfilePhoto_layer126() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_UserProfilePhoto.TL_userProfilePhoto_layer126::class,
          org.Tajgram.tgnet.TLRPC.UserProfilePhoto::TLdeserialize, 126)

    }

    /**
     * ChatFull
     */
    @Test
    public fun test_001865_ChatFull_TL_channelFull_layer124() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ChatFull.TL_channelFull_layer124::class,
          org.Tajgram.tgnet.TLRPC.ChatFull::TLdeserialize, 124)

    }

    /**
     * ChatFull
     */
    @Test
    public fun test_001866_ChatFull_TL_chatFull_layer124() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ChatFull.TL_chatFull_layer124::class,
          org.Tajgram.tgnet.TLRPC.ChatFull::TLdeserialize, 124)

    }

    /**
     * ChatFull
     */
    @Test
    public fun test_001867_ChatFull_TL_channelFull_layer123() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ChatFull.TL_channelFull_layer123::class,
          org.Tajgram.tgnet.TLRPC.ChatFull::TLdeserialize, 123)

    }

    /**
     * ChatFull
     */
    @Test
    public fun test_001868_ChatFull_TL_chatFull_layer123() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ChatFull.TL_chatFull_layer123::class,
          org.Tajgram.tgnet.TLRPC.ChatFull::TLdeserialize, 123)

    }

    /**
     * Message
     */
    @Test
    public fun test_001869_Message_TL_message_layer123() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Message.TL_message_layer123::class,
          org.Tajgram.tgnet.TLRPC.Message::TLdeserialize, 123)

    }

    /**
     * Message
     */
    @Test
    public fun test_001870_Message_TL_messageService_layer123() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Message.TL_messageService_layer123::class,
          org.Tajgram.tgnet.TLRPC.Message::TLdeserialize, 123)

    }

    /**
     * UserFull
     */
    @Test
    public fun test_001871_UserFull_TL_userFull_layer123() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_UserFull.TL_userFull_layer123::class,
          org.Tajgram.tgnet.TLRPC.UserFull::TLdeserialize, 123)

    }

    /**
     * ChatFull
     */
    @Test
    public fun test_001872_ChatFull_TL_channelFull_layer122() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ChatFull.TL_channelFull_layer122::class,
          org.Tajgram.tgnet.TLRPC.ChatFull::TLdeserialize, 122)

    }

    /**
     * ChatFull
     */
    @Test
    public fun test_001873_ChatFull_TL_chatFull_layer122() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ChatFull.TL_chatFull_layer122::class,
          org.Tajgram.tgnet.TLRPC.ChatFull::TLdeserialize, 122)

    }

    /**
     * ChatFull-ExportedChatInvite
     */
    @Test
    public fun test_001874_ExportedChatInvite_TL_chatInviteEmpty_layer122() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ExportedChatInvite.TL_chatInviteEmpty_layer122::class,
          org.Tajgram.tgnet.TLRPC.ExportedChatInvite::TLdeserialize, 122)

    }

    /**
     * ChatFull-ExportedChatInvite
     */
    @Test
    public fun test_001875_ExportedChatInvite_TL_chatInviteExported_layer122() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ExportedChatInvite.TL_chatInviteExported_layer122::class,
          org.Tajgram.tgnet.TLRPC.ExportedChatInvite::TLdeserialize, 122)

    }

    /**
     * Message
     */
    @Test
    public fun test_001876_Message_TL_messageEmpty_layer122() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Message.TL_messageEmpty_layer122::class,
          org.Tajgram.tgnet.TLRPC.Message::TLdeserialize, 122)

    }

    /**
     * ChatFull
     */
    @Test
    public fun test_001877_ChatFull_TL_channelFull_layer121() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ChatFull.TL_channelFull_layer121::class,
          org.Tajgram.tgnet.TLRPC.ChatFull::TLdeserialize, 121)

    }

    /**
     * ChatFull
     */
    @Test
    public fun test_001878_ChatFull_TL_chatFull_layer121() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ChatFull.TL_chatFull_layer121::class,
          org.Tajgram.tgnet.TLRPC.ChatFull::TLdeserialize, 121)

    }

    /**
     * ChatFull-StickerSet
     */
    @Test
    public fun test_001879_StickerSet_TL_stickerSet_layer121() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_StickerSet.TL_stickerSet_layer121::class,
          org.Tajgram.tgnet.TLRPC.StickerSet::TLdeserialize, 121)

    }

    /**
     * Message-MessageMedia-GeoPoint
     * ChatFull-ChannelLocation-GeoPoint
     */
    @Test
    public fun test_001880_GeoPoint_TL_geoPoint_layer119() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_GeoPoint.TL_geoPoint_layer119::class,
          org.Tajgram.tgnet.TLRPC.GeoPoint::TLdeserialize, 119)

    }

    /**
     * Message-MessageMedia
     */
    @Test
    public fun test_001881_MessageMedia_TL_messageMediaGeoLive_layer119() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageMedia.TL_messageMediaGeoLive_layer119::class,
          org.Tajgram.tgnet.TLRPC.MessageMedia::TLdeserialize, 119)

    }

    /**
     * Message
     */
    @Test
    public fun test_001882_Message_TL_message_layer118() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Message.TL_message_layer118::class,
          org.Tajgram.tgnet.TLRPC.Message::TLdeserialize, 118)

    }

    /**
     * Message
     */
    @Test
    public fun test_001883_Message_TL_messageService_layer118() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Message.TL_messageService_layer118::class,
          org.Tajgram.tgnet.TLRPC.Message::TLdeserialize, 118)

    }

    /**
     * Message-MessageFwdHeader
     */
    @Test
    public fun test_001884_MessageFwdHeader_TL_messageFwdHeader_layer118() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageFwdHeader.TL_messageFwdHeader_layer118::class,
          org.Tajgram.tgnet.TLRPC.MessageFwdHeader::TLdeserialize, 118)

    }

    /**
     * Message-ReplyMarkup-KeyboardButtonRow-KeyboardButton
     */
    @Test
    public fun test_001885_KeyboardButton_TL_keyboardButtonCallback_layer117() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_KeyboardButton.TL_keyboardButtonCallback_layer117::class,
          org.Tajgram.tgnet.TLRPC.KeyboardButton::TLdeserialize, 117)

    }

    /**
     * Message-MessageMedia-WebPage-Page-PageBlock-Chat-ChatPhoto
     * Chat-ChatPhoto
     */
    @Test
    public fun test_001886_ChatPhoto_TL_chatPhoto_layer115() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ChatPhoto.TL_chatPhoto_layer115::class,
          org.Tajgram.tgnet.TLRPC.ChatPhoto::TLdeserialize, 115)

    }

    /**
     * UserFull-PeerSettings
     */
    @Test
    public fun test_001887_PeerSettings_TL_peerSettings_layer115() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_PeerSettings.TL_peerSettings_layer115::class,
          org.Tajgram.tgnet.TLRPC.PeerSettings::TLdeserialize, 115)

    }

    /**
     * Message-MessageMedia-Photo
     * UserFull-Photo
     * ChatFull-Photo
     */
    @Test
    public fun test_001888_Photo_TL_photo_layer115() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Photo.TL_photo_layer115::class,
          org.Tajgram.tgnet.TLRPC.Photo::TLdeserialize, 115)

    }

    /**
     * User-UserProfilePhoto
     * UserFull-User-UserProfilePhoto
     */
    @Test
    public fun test_001889_UserProfilePhoto_TL_userProfilePhoto_layer115() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_UserProfilePhoto.TL_userProfilePhoto_layer115::class,
          org.Tajgram.tgnet.TLRPC.UserProfilePhoto::TLdeserialize, 115)

    }

    /**
     * Message-MessageMedia-Document-VideoSize
     * Theme-Document-VideoSize
     */
    @Test
    public fun test_001890_VideoSize_TL_videoSize_layer115() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_VideoSize.TL_videoSize_layer115::class,
          org.Tajgram.tgnet.TLRPC.VideoSize::TLdeserialize, 115)

    }

    /**
     * Message-MessageMedia-Document
     * Theme-Document
     */
    @Test
    public fun test_001891_Document_TL_document_layer113() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Document.TL_document_layer113::class,
          org.Tajgram.tgnet.TLRPC.Document::TLdeserialize, 113)

    }

    /**
     * Message-MessageFwdHeader
     */
    @Test
    public fun test_001892_MessageFwdHeader_TL_messageFwdHeader_layer112() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageFwdHeader.TL_messageFwdHeader_layer112::class,
          org.Tajgram.tgnet.TLRPC.MessageFwdHeader::TLdeserialize, 112)

    }

    /**
     * Message-MessageMedia-Document-DocumentAttribute-InputStickerSet
     * Theme-Document-DocumentAttribute-InputStickerSet
     */
    @Test
    public fun test_001893_InputStickerSet_TL_inputStickerSetDice_layer111() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_InputStickerSet.TL_inputStickerSetDice_layer111::class,
          org.Tajgram.tgnet.TLRPC.InputStickerSet::TLdeserialize, 111)

    }

    /**
     * Message-MessageMedia
     */
    @Test
    public fun test_001894_MessageMedia_TL_messageMediaDice_layer111() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageMedia.TL_messageMediaDice_layer111::class,
          org.Tajgram.tgnet.TLRPC.MessageMedia::TLdeserialize, 111)

    }

    /**
     * Message-MessageMedia-Poll
     */
    @Test
    public fun test_001895_Poll_TL_poll_layer111() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Poll.TL_poll_layer111::class,
          org.Tajgram.tgnet.TLRPC.Poll::TLdeserialize, 111)

    }

    /**
     * Message-MessageMedia-PollResults
     */
    @Test
    public fun test_001896_PollResults_TL_pollResults_layer111() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_PollResults.TL_pollResults_layer111::class,
          org.Tajgram.tgnet.TLRPC.PollResults::TLdeserialize, 111)

    }

    /**
     * ChatFull
     */
    @Test
    public fun test_001897_ChatFull_TL_channelFull_layer110() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ChatFull.TL_channelFull_layer110::class,
          org.Tajgram.tgnet.TLRPC.ChatFull::TLdeserialize, 110)

    }

    /**
     * Message-MessageMedia-WebPage-Page
     */
    @Test
    public fun test_001898_Page_TL_page_layer110() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Page.TL_page_layer110::class,
          org.Tajgram.tgnet.tl.TL_iv.Page::TLdeserialize, 110)

    }

    /**
     * Message-MessageMedia-WebPage
     */
    @Test
    public fun test_001899_WebPage_TL_webPageNotModified_layer110() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_WebPage.TL_webPageNotModified_layer110::class,
          org.Tajgram.tgnet.TLRPC.WebPage::TLdeserialize, 110)

    }

    /**
     * Message-MessageMedia-PollResults
     */
    @Test
    public fun test_001900_PollResults_TL_pollResults_layer108() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_PollResults.TL_pollResults_layer108::class,
          org.Tajgram.tgnet.TLRPC.PollResults::TLdeserialize, 108)

    }

    /**
     * Message-MessageMedia-WebPage
     */
    @Test
    public fun test_001901_WebPage_TL_webPage_layer107() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_WebPage.TL_webPage_layer107::class,
          org.Tajgram.tgnet.TLRPC.WebPage::TLdeserialize, 107)

    }

    /**
     * Theme
     */
    @Test
    public fun test_001902_Theme_TL_theme_layer106() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Theme.TL_theme_layer106::class,
          org.Tajgram.tgnet.TLRPC.Theme::TLdeserialize, 106)

    }

    /**
     * Theme
     */
    @Test
    public fun test_001903_Theme_TL_themeDocumentNotModified_layer106() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Theme.TL_themeDocumentNotModified_layer106::class,
          org.Tajgram.tgnet.TLRPC.Theme::TLdeserialize, 106)

    }

    /**
     * Message-MessageMedia-WebPage-Page-PageBlock-Chat
     * Chat
     */
    @Test
    public fun test_001904_Chat_TL_channel_layer104() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Chat.TL_channel_layer104::class,
          org.Tajgram.tgnet.TLRPC.Chat::TLdeserialize, 104)

    }

    /**
     * Message
     */
    @Test
    public fun test_001905_Message_TL_message_layer104() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Message.TL_message_layer104::class,
          org.Tajgram.tgnet.TLRPC.Message::TLdeserialize, 104)

    }

    /**
     * User
     * UserFull-User
     */
    @Test
    public fun test_001906_User_TL_user_layer104() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_User.TL_user_layer104::class,
          org.Tajgram.tgnet.TLRPC.User::TLdeserialize, 104)

    }

    /**
     * Message-MessageMedia-WebPage
     */
    @Test
    public fun test_001907_WebPage_TL_webPage_layer104() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_WebPage.TL_webPage_layer104::class,
          org.Tajgram.tgnet.TLRPC.WebPage::TLdeserialize, 104)

    }

    /**
     * ChatFull
     */
    @Test
    public fun test_001908_ChatFull_TL_channelFull_layer103() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ChatFull.TL_channelFull_layer103::class,
          org.Tajgram.tgnet.TLRPC.ChatFull::TLdeserialize, 103)

    }

    /**
     * ChatFull
     */
    @Test
    public fun test_001909_ChatFull_TL_channelFull_layer101() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ChatFull.TL_channelFull_layer101::class,
          org.Tajgram.tgnet.TLRPC.ChatFull::TLdeserialize, 101)

    }

    /**
     * UserFull-contacts.Link-ContactLink
     */
    @Test
    public fun test_001910_ContactLink_TL_contactLinkContact_layer101() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ContactLink.TL_contactLinkContact_layer101::class,
          org.Tajgram.tgnet.TLRPC.ContactLink_layer101::TLdeserialize, 101)

    }

    /**
     * UserFull-contacts.Link-ContactLink
     */
    @Test
    public fun test_001911_ContactLink_TL_contactLinkNone_layer101() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ContactLink.TL_contactLinkNone_layer101::class,
          org.Tajgram.tgnet.TLRPC.ContactLink_layer101::TLdeserialize, 101)

    }

    /**
     * UserFull-contacts.Link-ContactLink
     */
    @Test
    public fun test_001912_ContactLink_TL_contactLinkUnknown_layer101() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ContactLink.TL_contactLinkUnknown_layer101::class,
          org.Tajgram.tgnet.TLRPC.ContactLink_layer101::TLdeserialize, 101)

    }

    /**
     * UserFull
     */
    @Test
    public fun test_001913_UserFull_TL_userFull_layer101() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_UserFull.TL_userFull_layer101::class,
          org.Tajgram.tgnet.TLRPC.UserFull::TLdeserialize, 101)

    }

    /**
     * UserFull-contacts.Link
     */
    @Test
    public fun test_001914_contacts_Link_TL_contacts_link_layer101() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_contacts_Link.TL_contacts_link_layer101::class,
          org.Tajgram.tgnet.TLRPC.TL_contacts_link_layer101::TLdeserialize, 101)

    }

    /**
     * ChatFull
     */
    @Test
    public fun test_001915_ChatFull_TL_channelFull_layer99() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ChatFull.TL_channelFull_layer99::class,
          org.Tajgram.tgnet.TLRPC.ChatFull::TLdeserialize, 99)

    }

    /**
     * UserFull-contacts.Link-ContactLink
     */
    @Test
    public fun test_001916_ContactLink_TL_contactLinkHasPhone_layer99() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ContactLink.TL_contactLinkHasPhone_layer99::class,
          org.Tajgram.tgnet.TLRPC.ContactLink_layer101::TLdeserialize, 99)

    }

    /**
     * ChatFull
     */
    @Test
    public fun test_001917_ChatFull_TL_channelFull_layer98() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ChatFull.TL_channelFull_layer98::class,
          org.Tajgram.tgnet.TLRPC.ChatFull::TLdeserialize, 98)

    }

    /**
     * ChatFull
     */
    @Test
    public fun test_001918_ChatFull_TL_chatFull_layer98() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ChatFull.TL_chatFull_layer98::class,
          org.Tajgram.tgnet.TLRPC.ChatFull::TLdeserialize, 98)

    }

    /**
     * UserFull
     */
    @Test
    public fun test_001919_UserFull_TL_userFull_layer98() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_UserFull.TL_userFull_layer98::class,
          org.Tajgram.tgnet.TLRPC.UserFull::TLdeserialize, 98)

    }

    /**
     * Message-MessageMedia-WebPage-Page-PageBlock-Chat-ChatPhoto
     * Chat-ChatPhoto
     */
    @Test
    public fun test_001920_ChatPhoto_TL_chatPhoto_layer97() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ChatPhoto.TL_chatPhoto_layer97::class,
          org.Tajgram.tgnet.TLRPC.ChatPhoto::TLdeserialize, 97)

    }

    /**
     * Message-MessageMedia-Photo-PhotoSize-FileLocation
     * Chat-ChatPhoto-FileLocation
     * User-UserProfilePhoto-FileLocation
     * UserFull-User-UserProfilePhoto-FileLocation
     * ChatFull-Photo-PhotoSize-FileLocation
     */
    @Test
    public fun test_001921_FileLocation_TL_fileLocation_layer97() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_FileLocation.TL_fileLocation_layer97::class,
          org.Tajgram.tgnet.TLRPC.FileLocation::TLdeserialize, 97)

    }

    /**
     * Message-MessageMedia-Photo-PhotoSize-FileLocation
     * Chat-ChatPhoto-FileLocation
     * User-UserProfilePhoto-FileLocation
     * UserFull-User-UserProfilePhoto-FileLocation
     * ChatFull-Photo-PhotoSize-FileLocation
     */
    @Test
    public fun test_001922_FileLocation_TL_fileLocationUnavailable_layer97() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_FileLocation.TL_fileLocationUnavailable_layer97::class,
          org.Tajgram.tgnet.TLRPC.FileLocation::TLdeserialize, 97)

    }

    /**
     * Message-MessageMedia-Photo
     * UserFull-Photo
     * ChatFull-Photo
     */
    @Test
    public fun test_001923_Photo_TL_photo_layer97() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Photo.TL_photo_layer97::class,
          org.Tajgram.tgnet.TLRPC.Photo::TLdeserialize, 97)

    }

    /**
     * ChatFull-StickerSet
     */
    @Test
    public fun test_001924_StickerSet_TL_stickerSet_layer97() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_StickerSet.TL_stickerSet_layer97::class,
          org.Tajgram.tgnet.TLRPC.StickerSet::TLdeserialize, 97)

    }

    /**
     * User-UserProfilePhoto
     * UserFull-User-UserProfilePhoto
     */
    @Test
    public fun test_001925_UserProfilePhoto_TL_userProfilePhoto_layer97() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_UserProfilePhoto.TL_userProfilePhoto_layer97::class,
          org.Tajgram.tgnet.TLRPC.UserProfilePhoto::TLdeserialize, 97)

    }

    /**
     * Message-MessageFwdHeader
     */
    @Test
    public fun test_001926_MessageFwdHeader_TL_messageFwdHeader_layer96() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageFwdHeader.TL_messageFwdHeader_layer96::class,
          org.Tajgram.tgnet.TLRPC.MessageFwdHeader::TLdeserialize, 96)

    }

    /**
     * ChatFull-StickerSet
     */
    @Test
    public fun test_001927_StickerSet_TL_stickerSet_layer96() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_StickerSet.TL_stickerSet_layer96::class,
          org.Tajgram.tgnet.TLRPC.StickerSet::TLdeserialize, 96)

    }

    /**
     * Message-MessageMedia-WebPage-Page-PageBlock-Chat-ChannelAdminRights
     * Chat-ChannelAdminRights
     */
    @Test
    public fun test_001928_ChannelAdminRights_TL_channelAdminRights_layer92() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ChannelAdminRights.TL_channelAdminRights_layer92::class,
          org.Tajgram.tgnet.TLRPC.TL_channelAdminRights_layer92::TLdeserialize, 92)

    }

    /**
     * Message-MessageMedia-WebPage-Page-PageBlock-Chat-ChannelBannedRights
     * Chat-ChannelBannedRights
     */
    @Test
    public fun test_001929_ChannelBannedRights_TL_channelBannedRights_layer92() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ChannelBannedRights.TL_channelBannedRights_layer92::class,
          org.Tajgram.tgnet.TLRPC.TL_channelBannedRights_layer92::TLdeserialize, 92)

    }

    /**
     * Message-MessageMedia-WebPage-Page-PageBlock-Chat
     * Chat
     */
    @Test
    public fun test_001930_Chat_TL_channel_layer92() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Chat.TL_channel_layer92::class,
          org.Tajgram.tgnet.TLRPC.Chat::TLdeserialize, 92)

    }

    /**
     * Message-MessageMedia-WebPage-Page-PageBlock-Chat
     * Chat
     */
    @Test
    public fun test_001931_Chat_TL_chat_layer92() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Chat.TL_chat_layer92::class,
          org.Tajgram.tgnet.TLRPC.Chat::TLdeserialize, 92)

    }

    /**
     * ChatFull
     */
    @Test
    public fun test_001932_ChatFull_TL_chatFull_layer92() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ChatFull.TL_chatFull_layer92::class,
          org.Tajgram.tgnet.TLRPC.ChatFull::TLdeserialize, 92)

    }

    /**
     * Message-MessageMedia-Document
     */
    @Test
    public fun test_001933_Document_TL_document_layer92() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Document.TL_document_layer92::class,
          org.Tajgram.tgnet.TLRPC.Document::TLdeserialize, 92)

    }

    /**
     * ChatFull
     */
    @Test
    public fun test_001934_ChatFull_TL_channelFull_layer90() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ChatFull.TL_channelFull_layer90::class,
          org.Tajgram.tgnet.TLRPC.ChatFull::TLdeserialize, 90)

    }

    /**
     * Message-MessageAction
     */
    @Test
    public fun test_001935_MessageAction_TL_messageActionContactSignUp_layer90() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageAction.TL_messageActionContactSignUp_layer90::class,
          org.Tajgram.tgnet.TLRPC.MessageAction::TLdeserialize, 90)

    }

    /**
     * Message-MessageMedia-WebPage-Page
     */
    @Test
    public fun test_001936_Page_TL_page_layer88() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Page.TL_page_layer88::class,
          org.Tajgram.tgnet.tl.TL_iv.Page::TLdeserialize, 88)

    }

    /**
     * Message-MessageMedia-WebPage-Page-PageBlock-PageRelatedArticle
     */
    @Test
    public fun test_001937_PageRelatedArticle_TL_pageRelatedArticle_layer88() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_PageRelatedArticle.TL_pageRelatedArticle_layer88::class,
          org.Tajgram.tgnet.tl.TL_iv.pageRelatedArticle::TLdeserialize, 88)

    }

    /**
     * Message-MessageMedia-WebPage-Page
     */
    @Test
    public fun test_001938_Page_TL_pageFull_layer87() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Page.TL_pageFull_layer87::class,
          org.Tajgram.tgnet.tl.TL_iv.Page::TLdeserialize, 87)

    }

    /**
     * Message-MessageMedia-WebPage-Page
     */
    @Test
    public fun test_001939_Page_TL_pagePart_layer87() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Page.TL_pagePart_layer87::class,
          org.Tajgram.tgnet.tl.TL_iv.Page::TLdeserialize, 87)

    }

    /**
     * Message-MessageMedia-WebPage-Page-PageBlock
     */
    @Test
    public fun test_001940_PageBlock_TL_pageBlockAudio_layer87() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_PageBlock.TL_pageBlockAudio_layer87::class,
          org.Tajgram.tgnet.tl.TL_iv.PageBlock::TLdeserialize, 87)

    }

    /**
     * Message-MessageMedia-WebPage-Page-PageBlock
     */
    @Test
    public fun test_001941_PageBlock_TL_pageBlockCollage_layer87() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_PageBlock.TL_pageBlockCollage_layer87::class,
          org.Tajgram.tgnet.tl.TL_iv.PageBlock::TLdeserialize, 87)

    }

    /**
     * Message-MessageMedia-WebPage-Page-PageBlock
     */
    @Test
    public fun test_001942_PageBlock_TL_pageBlockEmbed_layer87() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_PageBlock.TL_pageBlockEmbed_layer87::class,
          org.Tajgram.tgnet.tl.TL_iv.PageBlock::TLdeserialize, 87)

    }

    /**
     * Message-MessageMedia-WebPage-Page-PageBlock
     */
    @Test
    public fun test_001943_PageBlock_TL_pageBlockEmbedPost_layer87() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_PageBlock.TL_pageBlockEmbedPost_layer87::class,
          org.Tajgram.tgnet.tl.TL_iv.PageBlock::TLdeserialize, 87)

    }

    /**
     * Message-MessageMedia-WebPage-Page-PageBlock
     */
    @Test
    public fun test_001944_PageBlock_TL_pageBlockList_layer87() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_PageBlock.TL_pageBlockList_layer87::class,
          org.Tajgram.tgnet.tl.TL_iv.PageBlock::TLdeserialize, 87)

    }

    /**
     * Message-MessageMedia-WebPage-Page-PageBlock
     */
    @Test
    public fun test_001945_PageBlock_TL_pageBlockPhoto_layer87() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_PageBlock.TL_pageBlockPhoto_layer87::class,
          org.Tajgram.tgnet.tl.TL_iv.PageBlock::TLdeserialize, 87)

    }

    /**
     * Message-MessageMedia-WebPage-Page-PageBlock
     */
    @Test
    public fun test_001946_PageBlock_TL_pageBlockSlideshow_layer87() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_PageBlock.TL_pageBlockSlideshow_layer87::class,
          org.Tajgram.tgnet.tl.TL_iv.PageBlock::TLdeserialize, 87)

    }

    /**
     * Message-MessageMedia-WebPage-Page-PageBlock
     */
    @Test
    public fun test_001947_PageBlock_TL_pageBlockVideo_layer87() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_PageBlock.TL_pageBlockVideo_layer87::class,
          org.Tajgram.tgnet.tl.TL_iv.PageBlock::TLdeserialize, 87)

    }

    /**
     * ChatFull
     */
    @Test
    public fun test_001948_ChatFull_TL_chatFull_layer86() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ChatFull.TL_chatFull_layer86::class,
          org.Tajgram.tgnet.TLRPC.ChatFull::TLdeserialize, 86)

    }

    /**
     * UserFull
     */
    @Test
    public fun test_001949_UserFull_TL_userFull_layer86() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_UserFull.TL_userFull_layer86::class,
          org.Tajgram.tgnet.TLRPC.UserFull::TLdeserialize, 86)

    }

    /**
     * Message-MessageMedia-Document
     */
    @Test
    public fun test_001950_Document_TL_document_layer85() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Document.TL_document_layer85::class,
          org.Tajgram.tgnet.TLRPC.Document::TLdeserialize, 85)

    }

    /**
     * Message-MessageMedia-Photo-PhotoSize-FileLocation
     * Chat-ChatPhoto-FileLocation
     * User-UserProfilePhoto-FileLocation
     * UserFull-User-UserProfilePhoto-FileLocation
     * ChatFull-Photo-PhotoSize-FileLocation
     */
    @Test
    public fun test_001951_FileLocation_TL_fileLocation_layer85() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_FileLocation.TL_fileLocation_layer85::class,
          org.Tajgram.tgnet.TLRPC.FileLocation::TLdeserialize, 85)

    }

    /**
     * Message-MessageMedia-Photo
     * UserFull-Photo
     * ChatFull-Photo
     */
    @Test
    public fun test_001952_Photo_TL_photo_layer85() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Photo.TL_photo_layer85::class,
          org.Tajgram.tgnet.TLRPC.Photo::TLdeserialize, 85)

    }

    /**
     * Message-MessageAction-SecureValue
     */
    @Test
    public fun test_001953_SecureValue_TL_secureValue_layer84() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_SecureValue.TL_secureValue_layer84::class,
          org.Tajgram.tgnet.TLRPC.TL_secureValue::TLdeserialize, 84)

    }

    /**
     * Message-MessageMedia-GeoPoint
     */
    @Test
    public fun test_001954_GeoPoint_TL_geoPoint_layer81() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_GeoPoint.TL_geoPoint_layer81::class,
          org.Tajgram.tgnet.TLRPC.GeoPoint::TLdeserialize, 81)

    }

    /**
     * Message-MessageMedia
     */
    @Test
    public fun test_001955_MessageMedia_TL_messageMediaContact_layer81() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageMedia.TL_messageMediaContact_layer81::class,
          org.Tajgram.tgnet.TLRPC.MessageMedia::TLdeserialize, 81)

    }

    /**
     * Message-MessageMedia-WebDocument
     */
    @Test
    public fun test_001956_WebDocument_TL_webDocument_layer81() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_WebDocument.TL_webDocument_layer81::class,
          org.Tajgram.tgnet.TLRPC.WebDocument::TLdeserialize, 81)

    }

    @Test
    public fun test_001957_PeerNotifyEvents_TL_peerNotifyEventsAll_layer78() {
      assumeTrue("Test skipped, link error", false)

    }

    @Test
    public fun test_001958_PeerNotifyEvents_TL_peerNotifyEventsEmpty_layer78() {
      assumeTrue("Test skipped, link error", false)

    }

    /**
     * UserFull-PeerNotifySettings
     * ChatFull-PeerNotifySettings
     */
    @Test
    public fun test_001959_PeerNotifySettings_TL_peerNotifySettings_layer78() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_PeerNotifySettings.TL_peerNotifySettings_layer78::class,
          org.Tajgram.tgnet.TLRPC.PeerNotifySettings::TLdeserialize, 78)

    }

    /**
     * Message-MessageMedia-WebPage-Page-PageBlock-Chat
     * Chat
     */
    @Test
    public fun test_001960_Chat_TL_channel_layer76() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Chat.TL_channel_layer76::class,
          org.Tajgram.tgnet.TLRPC.Chat::TLdeserialize, 76)

    }

    /**
     * ChatFull-StickerSet
     */
    @Test
    public fun test_001961_StickerSet_TL_stickerSet_layer75() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_StickerSet.TL_stickerSet_layer75::class,
          org.Tajgram.tgnet.TLRPC.StickerSet::TLdeserialize, 75)

    }

    /**
     * Message-MessageMedia
     */
    @Test
    public fun test_001962_MessageMedia_TL_messageMediaDocument_layer74() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageMedia.TL_messageMediaDocument_layer74::class,
          org.Tajgram.tgnet.TLRPC.MessageMedia::TLdeserialize, 74)

    }

    /**
     * Message-MessageMedia
     */
    @Test
    public fun test_001963_MessageMedia_TL_messageMediaPhoto_layer74() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageMedia.TL_messageMediaPhoto_layer74::class,
          org.Tajgram.tgnet.TLRPC.MessageMedia::TLdeserialize, 74)

    }

    /**
     * Message-MessageMedia-WebPage-Page-PageBlock-Chat
     * Chat
     */
    @Test
    public fun test_001965_Chat_TL_channel_layer72() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Chat.TL_channel_layer72::class,
          org.Tajgram.tgnet.TLRPC.Chat::TLdeserialize, 72)

    }

    /**
     * Message
     */
    @Test
    public fun test_001966_Message_TL_message_layer72() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Message.TL_message_layer72::class,
          org.Tajgram.tgnet.TLRPC.Message::TLdeserialize, 72)

    }

    /**
     * Message-MessageFwdHeader
     */
    @Test
    public fun test_001967_MessageFwdHeader_TL_messageFwdHeader_layer72() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageFwdHeader.TL_messageFwdHeader_layer72::class,
          org.Tajgram.tgnet.TLRPC.MessageFwdHeader::TLdeserialize, 72)

    }

    /**
     * ChatFull
     */
    @Test
    public fun test_001968_ChatFull_TL_channelFull_layer71() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ChatFull.TL_channelFull_layer71::class,
          org.Tajgram.tgnet.TLRPC.ChatFull::TLdeserialize, 71)

    }

    /**
     * Message-MessageMedia
     */
    @Test
    public fun test_001969_MessageMedia_TL_messageMediaVenue_layer71() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageMedia.TL_messageMediaVenue_layer71::class,
          org.Tajgram.tgnet.TLRPC.MessageMedia::TLdeserialize, 71)

    }

    /**
     * ChatFull
     */
    @Test
    public fun test_001970_ChatFull_TL_channelFull_layer70() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ChatFull.TL_channelFull_layer70::class,
          org.Tajgram.tgnet.TLRPC.ChatFull::TLdeserialize, 70)

    }

    /**
     * Message
     */
    @Test
    public fun test_001971_Message_TL_message_layer69() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Message.TL_message_layer69::class,
          org.Tajgram.tgnet.TLRPC.Message::TLdeserialize, 69)

    }

    /**
     * Message-MessageFwdHeader
     */
    @Test
    public fun test_001972_MessageFwdHeader_TL_messageFwdHeader_layer69() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageFwdHeader.TL_messageFwdHeader_layer69::class,
          org.Tajgram.tgnet.TLRPC.MessageFwdHeader::TLdeserialize, 69)

    }

    /**
     * Message-MessageMedia
     */
    @Test
    public fun test_001973_MessageMedia_TL_messageMediaDocument_layer69() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageMedia.TL_messageMediaDocument_layer69::class,
          org.Tajgram.tgnet.TLRPC.MessageMedia::TLdeserialize, 69)

    }

    /**
     * Message-MessageMedia
     */
    @Test
    public fun test_001974_MessageMedia_TL_messageMediaPhoto_layer69() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageMedia.TL_messageMediaPhoto_layer69::class,
          org.Tajgram.tgnet.TLRPC.MessageMedia::TLdeserialize, 69)

    }

    /**
     * Message-MessageMedia-WebPage-Page-PageBlock-Chat
     * Chat
     */
    @Test
    public fun test_001975_Chat_TL_channel_layer67() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Chat.TL_channel_layer67::class,
          org.Tajgram.tgnet.TLRPC.Chat::TLdeserialize, 67)

    }

    /**
     * Message-MessageMedia-WebPage-Page-PageBlock-Chat
     * Chat
     */
    @Test
    public fun test_001976_Chat_TL_channelForbidden_layer67() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Chat.TL_channelForbidden_layer67::class,
          org.Tajgram.tgnet.TLRPC.Chat::TLdeserialize, 67)

    }

    /**
     * ChatFull
     */
    @Test
    public fun test_001977_ChatFull_TL_channelFull_layer67() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ChatFull.TL_channelFull_layer67::class,
          org.Tajgram.tgnet.TLRPC.ChatFull::TLdeserialize, 67)

    }

    /**
     * Message-MessageMedia-WebPage-Page
     */
    @Test
    public fun test_001978_Page_TL_pageFull_layer67() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Page.TL_pageFull_layer67::class,
          org.Tajgram.tgnet.tl.TL_iv.Page::TLdeserialize, 67)

    }

    /**
     * Message-MessageMedia-WebPage-Page
     */
    @Test
    public fun test_001979_Page_TL_pagePart_layer67() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Page.TL_pagePart_layer67::class,
          org.Tajgram.tgnet.tl.TL_iv.Page::TLdeserialize, 67)

    }

    /**
     * Message-MessageMedia-Document-DocumentAttribute
     */
    @Test
    public fun test_001981_DocumentAttribute_TL_documentAttributeVideo_layer65() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_DocumentAttribute.TL_documentAttributeVideo_layer65::class,
          org.Tajgram.tgnet.TLRPC.DocumentAttribute::TLdeserialize, 65)

    }

    /**
     * User
     * UserFull-User
     */
    @Test
    public fun test_001982_User_TL_user_layer65() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_User.TL_user_layer65::class,
          org.Tajgram.tgnet.TLRPC.User::TLdeserialize, 65)

    }

    /**
     * Message-MessageMedia-WebPage-Page-PageBlock
     */
    @Test
    public fun test_001983_PageBlock_TL_pageBlockAuthorDate_layer60() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_PageBlock.TL_pageBlockAuthorDate_layer60::class,
          org.Tajgram.tgnet.tl.TL_iv.PageBlock::TLdeserialize, 60)

    }

    /**
     * Message-MessageMedia-WebPage-Page-PageBlock
     */
    @Test
    public fun test_001984_PageBlock_TL_pageBlockEmbed_layer60() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_PageBlock.TL_pageBlockEmbed_layer60::class,
          org.Tajgram.tgnet.tl.TL_iv.PageBlock::TLdeserialize, 60)

    }

    /**
     * Message-MessageMedia-WebPage
     */
    @Test
    public fun test_001985_WebPage_TL_webPage_layer58() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_WebPage.TL_webPage_layer58::class,
          org.Tajgram.tgnet.TLRPC.WebPage::TLdeserialize, 58)

    }

    /**
     * UserFull
     */
    @Test
    public fun test_001986_UserFull_TL_userFull_layer57() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_UserFull.TL_userFull_layer57::class,
          org.Tajgram.tgnet.TLRPC.UserFull::TLdeserialize, 57)

    }

    /**
     * Message-MessageMedia-Document-DocumentAttribute
     */
    @Test
    public fun test_001987_DocumentAttribute_TL_documentAttributeSticker_layer55() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_DocumentAttribute.TL_documentAttributeSticker_layer55::class,
          org.Tajgram.tgnet.TLRPC.DocumentAttribute::TLdeserialize, 55)

    }

    /**
     * Message-MessageMedia-Photo
     * UserFull-Photo
     * ChatFull-Photo
     */
    @Test
    public fun test_001988_Photo_TL_photo_layer55() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Photo.TL_photo_layer55::class,
          org.Tajgram.tgnet.TLRPC.Photo::TLdeserialize, 55)

    }

    /**
     * Message-ReplyMarkup-KeyboardButtonRow-KeyboardButton
     */
    @Test
    public fun test_001989_KeyboardButton_TL_keyboardButtonSwitchInline_layer54() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_KeyboardButton.TL_keyboardButtonSwitchInline_layer54::class,
          org.Tajgram.tgnet.TLRPC.KeyboardButton::TLdeserialize, 54)

    }

    /**
     * Message-MessageMedia-Document
     */
    @Test
    public fun test_001990_Document_TL_document_layer53() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Document.TL_document_layer53::class,
          org.Tajgram.tgnet.TLRPC.Document::TLdeserialize, 53)

    }

    /**
     * Chat
     */
    @Test
    public fun test_001991_Chat_TL_channelForbidden_layer52() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Chat.TL_channelForbidden_layer52::class,
          org.Tajgram.tgnet.TLRPC.Chat::TLdeserialize, 52)

    }

    /**
     * ChatFull
     */
    @Test
    public fun test_001992_ChatFull_TL_channelFull_layer52() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ChatFull.TL_channelFull_layer52::class,
          org.Tajgram.tgnet.TLRPC.ChatFull::TLdeserialize, 52)

    }

    /**
     * UserFull-BotInfo
     * ChatFull-BotInfo
     */
    @Test
    public fun test_001993_BotInfo_TL_botInfo_layer48() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_BotInfo.TL_botInfo_layer48::class,
          org.Tajgram.tgnet.tl.TL_bots.BotInfo::TLdeserialize, 48)

    }

    /**
     * UserFull-BotInfo
     * ChatFull-BotInfo
     */
    @Test
    public fun test_001994_BotInfo_TL_botInfoEmpty_layer48() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_BotInfo.TL_botInfoEmpty_layer48::class,
          org.Tajgram.tgnet.tl.TL_bots.BotInfo::TLdeserialize, 48)

    }

    /**
     * Chat
     */
    @Test
    public fun test_001995_Chat_TL_channel_layer48() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Chat.TL_channel_layer48::class,
          org.Tajgram.tgnet.TLRPC.Chat::TLdeserialize, 48)

    }

    /**
     * ChatFull
     */
    @Test
    public fun test_001996_ChatFull_TL_channelFull_layer48() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ChatFull.TL_channelFull_layer48::class,
          org.Tajgram.tgnet.TLRPC.ChatFull::TLdeserialize, 48)

    }

    /**
     * Message
     */
    @Test
    public fun test_001997_Message_TL_messageService_layer48() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Message.TL_messageService_layer48::class,
          org.Tajgram.tgnet.TLRPC.Message::TLdeserialize, 48)

    }

    /**
     * UserFull
     */
    @Test
    public fun test_001998_UserFull_TL_userFull_layer48() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_UserFull.TL_userFull_layer48::class,
          org.Tajgram.tgnet.TLRPC.UserFull::TLdeserialize, 48)

    }

    /**
     * Message
     */
    @Test
    public fun test_001999_Message_TL_message_layer47() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Message.TL_message_layer47::class,
          org.Tajgram.tgnet.TLRPC.Message::TLdeserialize, 47)

    }

    /**
     * UserFull-PeerNotifySettings
     * ChatFull-PeerNotifySettings
     */
    @Test
    public fun test_002000_PeerNotifySettings_TL_peerNotifySettings_layer47() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_PeerNotifySettings.TL_peerNotifySettings_layer47::class,
          org.Tajgram.tgnet.TLRPC.PeerNotifySettings::TLdeserialize, 47)

    }

    /**
     * Message-MessageMedia
     */
    @Test
    public fun test_002001_MessageMedia_TL_messageMediaVideo_layer46() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageMedia.TL_messageMediaVideo_layer46::class,
          org.Tajgram.tgnet.TLRPC.MessageMedia::TLdeserialize, 46)

    }

    /**
     * Message-MessageMedia-Video
     */
    @Test
    public fun test_002002_Video_TL_video_layer46() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Video.TL_video_layer46::class,
          org.Tajgram.tgnet.TLRPC.Video::TLdeserialize, 46)

    }

    /**
     * Message-MessageMedia-Video
     */
    @Test
    public fun test_002003_Video_TL_videoEmpty_layer46() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Video.TL_videoEmpty_layer46::class,
          org.Tajgram.tgnet.TLRPC.Video::TLdeserialize, 46)

    }

    /**
     * Message-MessageMedia-Audio
     */
    @Test
    public fun test_002004_Audio_TL_audio_layer45() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Audio.TL_audio_layer45::class,
          org.Tajgram.tgnet.TLRPC.Audio::TLdeserialize, 45)

    }

    /**
     * Message-MessageMedia-Audio
     */
    @Test
    public fun test_002005_Audio_TL_audioEmpty_layer45() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Audio.TL_audioEmpty_layer45::class,
          org.Tajgram.tgnet.TLRPC.Audio::TLdeserialize, 45)

    }

    /**
     * Message-MessageMedia-Document-DocumentAttribute
     */
    @Test
    public fun test_002012_DocumentAttribute_TL_documentAttributeAudio_layer45() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_DocumentAttribute.TL_documentAttributeAudio_layer45::class,
          org.Tajgram.tgnet.TLRPC.DocumentAttribute::TLdeserialize, 45)

    }

    /**
     * Message-MessageMedia
     */
    @Test
    public fun test_002013_MessageMedia_TL_messageMediaAudio_layer45() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageMedia.TL_messageMediaAudio_layer45::class,
          org.Tajgram.tgnet.TLRPC.MessageMedia::TLdeserialize, 45)

    }

    /**
     * Message
     */
    @Test
    public fun test_002014_Message_TL_message_layer44() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Message.TL_message_layer44::class,
          org.Tajgram.tgnet.TLRPC.Message::TLdeserialize, 44)

    }

    /**
     * Message-MessageMedia
     */
    @Test
    public fun test_002015_MessageMedia_TL_messageMediaDocument_layer44() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageMedia.TL_messageMediaDocument_layer44::class,
          org.Tajgram.tgnet.TLRPC.MessageMedia::TLdeserialize, 44)

    }

    /**
     * User
     * UserFull-User
     */
    @Test
    public fun test_002016_User_TL_user_layer44() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_User.TL_user_layer44::class,
          org.Tajgram.tgnet.TLRPC.User::TLdeserialize, 44)

    }

    /**
     * Chat
     */
    @Test
    public fun test_002017_Chat_TL_channel_layer43() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Chat.TL_channel_layer43::class,
          org.Tajgram.tgnet.TLRPC.Chat::TLdeserialize, 43)

    }

    /**
     * User
     * UserFull-User
     */
    @Test
    public fun test_002018_User_TL_user_layer43() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_User.TL_user_layer43::class,
          org.Tajgram.tgnet.TLRPC.User::TLdeserialize, 43)

    }

    /**
     * Chat
     */
    @Test
    public fun test_002019_Chat_TL_chat_layer40() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Chat.TL_chat_layer40::class,
          org.Tajgram.tgnet.TLRPC.Chat::TLdeserialize, 40)

    }

    /**
     * ChatFull
     */
    @Test
    public fun test_002020_ChatFull_TL_channelFull_layer40() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ChatFull.TL_channelFull_layer40::class,
          org.Tajgram.tgnet.TLRPC.ChatFull::TLdeserialize, 40)

    }

    /**
     * Message-MessageAction
     */
    @Test
    public fun test_002021_MessageAction_TL_messageActionChatAddUser_layer40() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageAction.TL_messageActionChatAddUser_layer40::class,
          org.Tajgram.tgnet.TLRPC.MessageAction::TLdeserialize, 40)

    }

    /**
     * ChatFull-ChatParticipants
     */
    @Test
    public fun test_002022_ChatParticipants_TL_chatParticipants_layer39() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ChatParticipants.TL_chatParticipants_layer39::class,
          org.Tajgram.tgnet.TLRPC.ChatParticipants::TLdeserialize, 39)

    }

    /**
     * Chat
     */
    @Test
    public fun test_002023_Chat_TL_chat_layer37() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Chat.TL_chat_layer37::class,
          org.Tajgram.tgnet.TLRPC.Chat::TLdeserialize, 37)

    }

    /**
     * Chat
     */
    @Test
    public fun test_002024_Chat_TL_chatForbidden_layer37() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Chat.TL_chatForbidden_layer37::class,
          org.Tajgram.tgnet.TLRPC.Chat::TLdeserialize, 37)

    }

    /**
     * Message
     */
    @Test
    public fun test_002025_Message_TL_message_layer37() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Message.TL_message_layer37::class,
          org.Tajgram.tgnet.TLRPC.Message::TLdeserialize, 37)

    }

    /**
     * Message
     */
    @Test
    public fun test_002026_Message_TL_messageService_layer37() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Message.TL_messageService_layer37::class,
          org.Tajgram.tgnet.TLRPC.Message::TLdeserialize, 37)

    }

    /**
     * ChatFull-ChatParticipants
     */
    @Test
    public fun test_002027_ChatParticipants_TL_chatParticipantsForbidden_layer36() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ChatParticipants.TL_chatParticipantsForbidden_layer36::class,
          org.Tajgram.tgnet.TLRPC.ChatParticipants::TLdeserialize, 36)

    }

    /**
     * Message
     */
    @Test
    public fun test_002028_Message_TL_message_layer35() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Message.TL_message_layer35::class,
          org.Tajgram.tgnet.TLRPC.Message::TLdeserialize, 35)

    }

    /**
     * Message-MessageMedia-WebPage
     */
    @Test
    public fun test_002029_WebPage_TL_webPage_layer34() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_WebPage.TL_webPage_layer34::class,
          org.Tajgram.tgnet.TLRPC.WebPage::TLdeserialize, 34)

    }

    /**
     * Message
     */
    @Test
    public fun test_002030_Message_TL_message_layer33() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Message.TL_message_layer33::class,
          org.Tajgram.tgnet.TLRPC.Message::TLdeserialize, 33)

    }

    /**
     * Message-MessageMedia-Audio
     */
    @Test
    public fun test_002031_Audio_TL_audio_layer32() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Audio.TL_audio_layer32::class,
          org.Tajgram.tgnet.TLRPC.Audio::TLdeserialize, 32)

    }

    /**
     * Message-MessageMedia-Photo
     * UserFull-Photo
     * ChatFull-Photo
     */
    @Test
    public fun test_002032_Photo_TL_photo_layer32() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Photo.TL_photo_layer32::class,
          org.Tajgram.tgnet.TLRPC.Photo::TLdeserialize, 32)

    }

    /**
     * Message-MessageMedia-Video
     */
    @Test
    public fun test_002033_Video_TL_video_layer32() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Video.TL_video_layer32::class,
          org.Tajgram.tgnet.TLRPC.Video::TLdeserialize, 32)

    }

    /**
     * Message-MessageMedia-Document-DocumentAttribute
     */
    @Test
    public fun test_002034_DocumentAttribute_TL_documentAttributeAudio_layer31() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_DocumentAttribute.TL_documentAttributeAudio_layer31::class,
          org.Tajgram.tgnet.TLRPC.DocumentAttribute::TLdeserialize, 31)

    }

    /**
     * ChatFull
     */
    @Test
    public fun test_002035_ChatFull_TL_chatFull_layer30() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ChatFull.TL_chatFull_layer30::class,
          org.Tajgram.tgnet.TLRPC.ChatFull::TLdeserialize, 30)

    }

    /**
     * Message
     */
    @Test
    public fun test_002036_Message_TL_message_layer30() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Message.TL_message_layer30::class,
          org.Tajgram.tgnet.TLRPC.Message::TLdeserialize, 30)

    }

    /**
     * User
     * UserFull-User
     */
    @Test
    public fun test_002037_User_TL_userContact_layer30() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_User.TL_userContact_layer30::class,
          org.Tajgram.tgnet.TLRPC.User::TLdeserialize, 30)

    }

    /**
     * User
     * UserFull-User
     */
    @Test
    public fun test_002038_User_TL_userDeleted_layer30() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_User.TL_userDeleted_layer30::class,
          org.Tajgram.tgnet.TLRPC.User::TLdeserialize, 30)

    }

    /**
     * User
     * UserFull-User
     */
    @Test
    public fun test_002039_User_TL_userForeign_layer30() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_User.TL_userForeign_layer30::class,
          org.Tajgram.tgnet.TLRPC.User::TLdeserialize, 30)

    }

    /**
     * User
     * UserFull-User
     */
    @Test
    public fun test_002040_User_TL_userRequest_layer30() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_User.TL_userRequest_layer30::class,
          org.Tajgram.tgnet.TLRPC.User::TLdeserialize, 30)

    }

    /**
     * User
     * UserFull-User
     */
    @Test
    public fun test_002041_User_TL_userSelf_layer30() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_User.TL_userSelf_layer30::class,
          org.Tajgram.tgnet.TLRPC.User::TLdeserialize, 30)

    }

    /**
     * UserFull
     */
    @Test
    public fun test_002042_UserFull_TL_userFull_layer30() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_UserFull.TL_userFull_layer30::class,
          org.Tajgram.tgnet.TLRPC.UserFull::TLdeserialize, 30)

    }

    /**
     * Message-MessageMedia-Document-DocumentAttribute
     */
    @Test
    public fun test_002043_DocumentAttribute_TL_documentAttributeSticker_layer28() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_DocumentAttribute.TL_documentAttributeSticker_layer28::class,
          org.Tajgram.tgnet.TLRPC.DocumentAttribute::TLdeserialize, 28)

    }

    /**
     * ChatFull
     */
    @Test
    public fun test_002044_ChatFull_TL_chatFull_layer27() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_ChatFull.TL_chatFull_layer27::class,
          org.Tajgram.tgnet.TLRPC.ChatFull::TLdeserialize, 27)

    }

    /**
     * Message-MessageMedia
     */
    @Test
    public fun test_002045_MessageMedia_TL_messageMediaPhoto_layer27() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageMedia.TL_messageMediaPhoto_layer27::class,
          org.Tajgram.tgnet.TLRPC.MessageMedia::TLdeserialize, 27)

    }

    /**
     * Message-MessageMedia
     */
    @Test
    public fun test_002046_MessageMedia_TL_messageMediaVideo_layer27() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_MessageMedia.TL_messageMediaVideo_layer27::class,
          org.Tajgram.tgnet.TLRPC.MessageMedia::TLdeserialize, 27)

    }

    /**
     * Message-MessageMedia-Photo
     * UserFull-Photo
     * ChatFull-Photo
     */
    @Test
    public fun test_002047_Photo_TL_photo_layer27() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Photo.TL_photo_layer27::class,
          org.Tajgram.tgnet.TLRPC.Photo::TLdeserialize, 27)

    }

    /**
     * Message-MessageMedia-Video
     */
    @Test
    public fun test_002048_Video_TL_video_layer27() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Video.TL_video_layer27::class,
          org.Tajgram.tgnet.TLRPC.Video::TLdeserialize, 27)

    }

    /**
     * Message-MessageMedia-Document-DocumentAttribute
     */
    @Test
    public fun test_002049_DocumentAttribute_TL_documentAttributeSticker_layer24() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_DocumentAttribute.TL_documentAttributeSticker_layer24::class,
          org.Tajgram.tgnet.TLRPC.DocumentAttribute::TLdeserialize, 24)

    }

    /**
     * Message
     */
    @Test
    public fun test_002050_Message_TL_message_layer24() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Message.TL_message_layer24::class,
          org.Tajgram.tgnet.TLRPC.Message::TLdeserialize, 24)

    }

    /**
     * Message
     */
    @Test
    public fun test_002051_Message_TL_messageForwarded_layer24() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Message.TL_messageForwarded_layer24::class,
          org.Tajgram.tgnet.TLRPC.Message::TLdeserialize, 24)

    }

    /**
     * User
     * UserFull-User
     */
    @Test
    public fun test_002053_User_TL_userSelf_layer23() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_User.TL_userSelf_layer23::class,
          org.Tajgram.tgnet.TLRPC.User::TLdeserialize, 23)

    }

    /**
     * UserFull-contacts.Link-contacts.ForeignLink
     */
    @Test
    public fun test_002054_contacts_ForeignLink_TL_contacts_foreignLinkMutual_layer23() {
      assumeTrue("Test skipped, link error", false)

    }

    /**
     * UserFull-contacts.Link-contacts.ForeignLink
     */
    @Test
    public fun test_002055_contacts_ForeignLink_TL_contacts_foreignLinkRequested_layer23() {
      assumeTrue("Test skipped, link error", false)

    }

    /**
     * UserFull-contacts.Link-contacts.ForeignLink
     */
    @Test
    public fun test_002056_contacts_ForeignLink_TL_contacts_foreignLinkUnknown_layer23() {
      assumeTrue("Test skipped, link error", false)

    }

    /**
     * UserFull-contacts.Link
     */
    @Test
    public fun test_002057_contacts_Link_TL_contacts_link_layer23() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_contacts_Link.TL_contacts_link_layer23::class,
          org.Tajgram.tgnet.TLRPC.TL_contacts_link_layer101::TLdeserialize, 23)

    }

    /**
     * UserFull-contacts.Link-contacts.MyLink
     */
    @Test
    public fun test_002058_contacts_MyLink_TL_contacts_myLinkContact_layer23() {
      assumeTrue("Test skipped, link error", false)

    }

    /**
     * UserFull-contacts.Link-contacts.MyLink
     */
    @Test
    public fun test_002059_contacts_MyLink_TL_contacts_myLinkEmpty_layer23() {
      assumeTrue("Test skipped, link error", false)

    }

    /**
     * UserFull-contacts.Link-contacts.MyLink
     */
    @Test
    public fun test_002060_contacts_MyLink_TL_contacts_myLinkRequested_layer23() {
      assumeTrue("Test skipped, link error", false)

    }

    /**
     * Message-MessageMedia-Document
     */
    @Test
    public fun test_002061_Document_TL_document_layer21() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Document.TL_document_layer21::class,
          org.Tajgram.tgnet.TLRPC.Document::TLdeserialize, 21)

    }

    /**
     * User
     * UserFull-User
     */
    @Test
    public fun test_002079_User_TL_userContact_layer17() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_User.TL_userContact_layer17::class,
          org.Tajgram.tgnet.TLRPC.User::TLdeserialize, 17)

    }

    /**
     * User
     * UserFull-User
     */
    @Test
    public fun test_002080_User_TL_userDeleted_layer17() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_User.TL_userDeleted_layer17::class,
          org.Tajgram.tgnet.TLRPC.User::TLdeserialize, 17)

    }

    /**
     * User
     * UserFull-User
     */
    @Test
    public fun test_002081_User_TL_userForeign_layer17() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_User.TL_userForeign_layer17::class,
          org.Tajgram.tgnet.TLRPC.User::TLdeserialize, 17)

    }

    /**
     * User
     * UserFull-User
     */
    @Test
    public fun test_002082_User_TL_userRequest_layer17() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_User.TL_userRequest_layer17::class,
          org.Tajgram.tgnet.TLRPC.User::TLdeserialize, 17)

    }

    /**
     * User
     * UserFull-User
     */
    @Test
    public fun test_002083_User_TL_userSelf_layer17() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_User.TL_userSelf_layer17::class,
          org.Tajgram.tgnet.TLRPC.User::TLdeserialize, 17)

    }

    /**
     * Message
     */
    @Test
    public fun test_002084_Message_TL_message_layer16() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Message.TL_message_layer16::class,
          org.Tajgram.tgnet.TLRPC.Message::TLdeserialize, 16)

    }

    /**
     * Message
     */
    @Test
    public fun test_002085_Message_TL_messageForwarded_layer16() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Message.TL_messageForwarded_layer16::class,
          org.Tajgram.tgnet.TLRPC.Message::TLdeserialize, 16)

    }

    /**
     * Message
     */
    @Test
    public fun test_002086_Message_TL_messageService_layer16() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Message.TL_messageService_layer16::class,
          org.Tajgram.tgnet.TLRPC.Message::TLdeserialize, 16)

    }

    /**
     * Message-MessageMedia-Audio
     */
    @Test
    public fun test_002087_Audio_TL_audio_layer12() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Audio.TL_audio_layer12::class,
          org.Tajgram.tgnet.TLRPC.Audio::TLdeserialize, 12)

    }

    /**
     * Message-MessageMedia-Video
     */
    @Test
    public fun test_002088_Video_TL_video_layer12() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_Video.TL_video_layer12::class,
          org.Tajgram.tgnet.TLRPC.Video::TLdeserialize, 12)

    }

    /**
     * UserFull-PeerNotifySettings
     * ChatFull-PeerNotifySettings
     */
    @Test
    public fun test_002103_PeerNotifySettings_TL_peerNotifySettings_layer1() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_PeerNotifySettings.TL_peerNotifySettings_layer1::class,
          org.Tajgram.tgnet.TLRPC.PeerNotifySettings::TLdeserialize, 1)

    }

    /**
     * User-UserProfilePhoto
     * UserFull-User-UserProfilePhoto
     */
    @Test
    public fun test_002104_UserProfilePhoto_TL_userProfilePhoto_layer1() {
      test_TLdeserialize(org.Tajgram.tgnet.model.generated.TlGen_UserProfilePhoto.TL_userProfilePhoto_layer1::class,
          org.Tajgram.tgnet.TLRPC.UserProfilePhoto::TLdeserialize, 1)

    }
  }
}
