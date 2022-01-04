package ua.itaysonlab.redesign.sheet

import ua.itaysonlab.redesign.BaseActionedSwipeFragment

class TgxMessageMenuSheetFragment(val act: List<BaseActionedSwipeFragment.Action>, val onClick: (Int) -> Unit) : BaseActionedSheetFragment() {
    override fun getActions(): List<BaseActionedSwipeFragment.Action> {
        return act
    }

    override fun processActionClick(id: String) = onClick.invoke(id.toInt())
}