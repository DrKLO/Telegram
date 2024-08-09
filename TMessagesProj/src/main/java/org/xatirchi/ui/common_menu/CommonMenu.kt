package org.xatirchi.ui.common_menu

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import org.telegram.messenger.R
import org.telegram.messenger.databinding.CommonMenuBinding
import org.telegram.ui.ActionBar.ActionBar
import org.telegram.ui.ActionBar.BackDrawable
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.ActionBar.Theme
import org.xatirchi.ui.common_menu.adapters.MenuAdapter
import org.xatirchi.ui.common_menu.models.MenuModel
import org.xatirchi.ui.delete_menu.DeleteMsg
import org.xatirchi.ui.edit_menu.EditMenu
import org.xatirchi.ui.ghost_menu.GhostMenu
import org.xatirchi.ui.password.PasswordScreen
import org.xatirchi.ui.two_step_password_screen.TwoStepPasswordScreen
import org.xatirchi.utils.GhostVariable
import org.xatirchi.utils.LanguageCode


class CommonMenu : BaseFragment() {

    private lateinit var commonMenuBinding: CommonMenuBinding
    private lateinit var menuAdapter: MenuAdapter
    private lateinit var menus: ArrayList<MenuModel>

    override fun createView(context: Context?): View {
        commonMenuBinding = CommonMenuBinding.inflate(LayoutInflater.from(context))
        setActionBar()

        setData()

        setAdapter()

        return commonMenuBinding.root
    }

    private fun setAdapter() {

        menuAdapter = MenuAdapter(menus, object : MenuAdapter.MenuClick {
            override fun itemClick(id: Int) {
                if (id != -1) {
                    when (id) {
                        1 -> {
                            presentFragment(GhostMenu())
                        }

                        2 -> {
                            presentFragment(EditMenu())
                        }

                        3 -> {
                            presentFragment(DeleteMsg())
                        }

                        4 -> {
                            LanguageCode.showLanguageDialog(parentActivity, true)
                        }

                        5 -> {
                            presentFragment(TwoStepPasswordScreen(0L, ""))
                        }
                    }
                }
            }
        })

        val layoutManager = LinearLayoutManager(parentActivity, LinearLayoutManager.VERTICAL, false)
        commonMenuBinding.rv.layoutManager = layoutManager

        commonMenuBinding.rv.adapter = menuAdapter

    }

    private fun setData() {
        menus = ArrayList()
        menus.add(
            MenuModel(
                1,
                if (GhostVariable.ghostMode) R.drawable.ghost_on else R.drawable.ghost_off,
                LanguageCode.getMyTitles(32)
            )
        )
        menus.add(MenuModel(2, R.drawable.xatirchi_edit_ic, LanguageCode.getMyTitles(30)))
        menus.add(MenuModel(3, R.drawable.xatirchi_delete_ic, LanguageCode.getMyTitles(31)))
        menus.add(MenuModel(4, R.drawable.xatirchi_language_ic, LanguageCode.getMyTitles(29)))
        menus.add(MenuModel(5, R.drawable.xatirchi_password_ic, LanguageCode.getMyTitles(28)))
    }

    private fun setActionBar() {
        actionBar.setAllowOverlayTitle(true)
        actionBar.setTitle(LanguageCode.getMyTitles(33))
        var backDrawable = BackDrawable(false)
        backDrawable.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText))
        actionBar.backButtonDrawable = BackDrawable(false).also { backDrawable = it }

        actionBar.setActionBarMenuOnItemClick(object : ActionBar.ActionBarMenuOnItemClick() {
            override fun onItemClick(id: Int) {
                finishFragment()
            }
        })
    }

}