package ua.itaysonlab.catogram

import ua.itaysonlab.catogram.preferences.*
import ua.itaysonlab.tgkit.TGKitSettingsFragment

object CatogramPreferencesNavigator {
    @JvmStatic
    fun createMainMenu() = TGKitSettingsFragment(MainPreferencesEntry())

    fun createChats() = TGKitSettingsFragment(ChatsPreferencesEntry())
    fun createAppearance() = TGKitSettingsFragment(AppearancePreferencesEntry())
    fun createSecurity() = TGKitSettingsFragment(SecurityPreferencesEntry())

    fun createDB() = TGKitSettingsFragment(DoubleBottomPreferencesEntry())
    fun createUpdates() = TGKitSettingsFragment(UpdatesPreferenceEntry())
    fun createDonate() = TGKitSettingsFragment(DonatePreferenceEntry())
}