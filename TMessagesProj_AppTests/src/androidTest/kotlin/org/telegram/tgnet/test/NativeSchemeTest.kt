package org.telegram.tgnet.test

import com.appmattus.kotlinfixture.config.ConfigurationBuilder
import org.junit.Test
import org.telegram.tgnet.ConnectionsManager
import org.telegram.tgnet.model.generated.TlGen_EmojiStatus
import org.telegram.tgnet.model.generated.TlGen_MessageEntity
import org.telegram.tgnet.model.generated.TlGen_PeerColor
import org.telegram.tgnet.model.generated.TlGen_auth_Authorization

class NativeSchemeTest : BaseSchemeTest() {
    @Test
    fun test_authReq() {
        val configuration = ConfigurationBuilder().apply {
            filter<TlGen_MessageEntity> { filter {
                it is TlGen_MessageEntity.TL_messageEntityTextUrl ||
                it is TlGen_MessageEntity.TL_messageEntityBotCommand ||
                it is TlGen_MessageEntity.TL_messageEntityEmail ||
                it is TlGen_MessageEntity.TL_messageEntityPre ||
                it is TlGen_MessageEntity.TL_messageEntityUnknown ||
                it is TlGen_MessageEntity.TL_messageEntityUrl ||
                it is TlGen_MessageEntity.TL_messageEntityItalic ||
                it is TlGen_MessageEntity.TL_messageEntityMention ||
                it is TlGen_MessageEntity.TL_messageEntityMentionName ||
                it is TlGen_MessageEntity.TL_inputMessageEntityMentionName ||
                it is TlGen_MessageEntity.TL_messageEntityCashtag ||
                it is TlGen_MessageEntity.TL_messageEntityBold ||
                it is TlGen_MessageEntity.TL_messageEntityHashtag ||
                it is TlGen_MessageEntity.TL_messageEntityCode ||
                it is TlGen_MessageEntity.TL_messageEntityStrike ||
                it is TlGen_MessageEntity.TL_messageEntityBlockquote ||
                it is TlGen_MessageEntity.TL_messageEntityUnderline ||
                it is TlGen_MessageEntity.TL_messageEntityPhone
            } }
        }.build()

        test_TLdeserializeNative(TlGen_auth_Authorization.TL_auth_authorizationSignUpRequired::class, ConnectionsManager::native_test_AuthAuthorization) { b ->
            b.factory<TlGen_MessageEntity> {
                @Suppress("DEPRECATION_ERROR")
                fixture.create(TlGen_MessageEntity::class, configuration) as TlGen_MessageEntity
            }
        }
    }

    @Test
    fun test_auth() {
        val configuration = ConfigurationBuilder().apply {
            filter<TlGen_PeerColor> { filter { it !is TlGen_PeerColor.TL_inputPeerColorCollectible } }
            filter<TlGen_EmojiStatus> { filter { it !is TlGen_EmojiStatus.TL_inputEmojiStatusCollectible } }
        }.build()

        test_TLdeserializeNative(TlGen_auth_Authorization.TL_auth_authorization::class, ConnectionsManager::native_test_AuthAuthorization) { b ->
            b.factory<TlGen_EmojiStatus> {
                @Suppress("DEPRECATION_ERROR")
                fixture.create(TlGen_EmojiStatus::class, configuration) as TlGen_EmojiStatus
            }
            b.factory<TlGen_PeerColor> {
                @Suppress("DEPRECATION_ERROR")
                fixture.create(TlGen_PeerColor::class, configuration) as TlGen_PeerColor
            }
        };
    }
}