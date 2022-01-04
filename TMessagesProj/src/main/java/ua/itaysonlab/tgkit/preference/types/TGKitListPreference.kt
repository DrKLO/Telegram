package ua.itaysonlab.tgkit.preference.types

import android.app.Activity
import android.view.View
import androidx.core.util.Pair
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.Components.AlertsCreator
import ua.itaysonlab.tgkit.preference.TGKitPreference

class TGKitListPreference : TGKitPreference() {
    var divider = false
    var contract: TGTLContract? = null

    override fun getType(): TGPType {
        return TGPType.LIST
    }

    fun callActionHueta(bf: BaseFragment, pr: Activity, view: View, x: Float, y: Float, ti: TempInterface) {
        var selected: Int = 0
        val titleArray = mutableListOf<String>()
        val idArray = mutableListOf<Int>()

        if (contract!!.hasIcons()) {
            contract!!.getOptionsIcons().forEachIndexed { index, triple ->
                titleArray.add(triple.second)
                idArray.add(triple.first)

                if (contract!!.getValue() == triple.second) selected = index
            }
        } else {
            contract!!.getOptions().forEachIndexed { index, pair ->
                titleArray.add(pair.second)
                idArray.add(pair.first)

                if (contract!!.getValue() == pair.second) selected = index
            }
        }

        val d = AlertsCreator.createSingleChoiceDialog(pr, titleArray.toTypedArray(), title, selected) { di, sel ->
            contract!!.setValue(idArray[sel])
            ti.update()
        }

        bf.visibleDialog = d

        d.show()
    }

    interface TGTLContract {
        fun setValue(id: Int)
        fun getValue(): String
        fun getOptions(): List<Pair<Int, String>>
        fun getOptionsIcons(): List<Triple<Int, String, Int?>>
        fun hasIcons(): Boolean
    }

    interface TempInterface {
        fun update()
    }
}