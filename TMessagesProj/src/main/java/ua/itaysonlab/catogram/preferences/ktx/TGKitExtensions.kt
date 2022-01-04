package ua.itaysonlab.catogram.preferences.ktx

import androidx.core.util.Pair
import ua.itaysonlab.tgkit.preference.TGKitCategory
import ua.itaysonlab.tgkit.preference.TGKitPreference
import ua.itaysonlab.tgkit.preference.TGKitSettings
import ua.itaysonlab.tgkit.preference.types.*

fun tgKitScreen(name: String, block: TGKitScreen.() -> Unit) = TGKitSettings(name, mutableListOf<TGKitCategory>().apply(block))

fun TGKitScreen.category(name: String, block: TGKitPreferences.() -> Unit) = add(
        TGKitCategory(name, mutableListOf<TGKitPreference>().apply(block))
)

fun TGKitPreferences.list(block: TGKitListPreference.() -> Unit) = add(TGKitListPreference().apply(block))
fun TGKitPreferences.switch(block: TGKitSwitchPreference.() -> Unit) = add(TGKitSwitchPreference().apply(block))
fun TGKitPreferences.slider(block: TGKitSliderPreference.() -> Unit) = add(TGKitSliderPreference().apply(block))
fun TGKitPreferences.textIcon(block: TGKitTextIconRow.() -> Unit) = add(TGKitTextIconRow().apply(block))
fun TGKitPreferences.textDetail(block: TGKitTextDetailRow.() -> Unit) = add(TGKitTextDetailRow().apply(block))
fun TGKitPreferences.hint(text: String) = add(TGKitTextHintRow().also { it.title = text })

fun TGKitSwitchPreference.contract(getValue: () -> Boolean, setValue: (Boolean) -> Unit) {
    contract = object : TGKitSwitchPreference.TGSPContract {
        override fun getPreferenceValue() = getValue()
        override fun toggleValue() = setValue(!getValue())
    }
}

fun TGKitListPreference.contract(getOptions: () -> List<Pair<Int, String>>, getValue: () -> String, setValue: (Int) -> Unit) {
    contract = object : TGKitListPreference.TGTLContract {
        override fun setValue(id: Int) {
            setValue(id)
        }

        override fun hasIcons(): Boolean {
            return false
        }

        override fun getOptionsIcons(): MutableList<Triple<Int, String, Int>> {
            return mutableListOf()
        }

        override fun getValue(): String {
            return getValue()
        }

        override fun getOptions(): List<Pair<Int, String>> {
            return getOptions()
        }
    }
}

fun TGKitListPreference.contractIcons(getOptions: () -> List<Triple<Int, String, Int>>, getValue: () -> String, setValue: (Int) -> Unit) {
    contract = object : TGKitListPreference.TGTLContract {
        override fun setValue(id: Int) {
            setValue(id)
        }

        override fun hasIcons(): Boolean {
            return true
        }

        override fun getOptionsIcons(): List<Triple<Int, String, Int>> {
            return getOptions()
        }

        override fun getValue(): String {
            return getValue()
        }

        override fun getOptions(): List<Pair<Int, String>> {
            return mutableListOf()
        }
    }
}

typealias TGKitScreen = MutableList<TGKitCategory>
typealias TGKitPreferences = MutableList<TGKitPreference>