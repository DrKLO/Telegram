package ua.itaysonlab.redesign.slides

import ua.itaysonlab.redesign.BaseActionedSwipeFragment

class TgxMessageMenuFragment(val act: List<Action>, val onClick: (Int) -> Unit) : BaseActionedSwipeFragment() {
    override fun getActions(): List<Action> {
        return act
    }

    override fun processActionClick(id: String) = onClick.invoke(id.toInt())
}