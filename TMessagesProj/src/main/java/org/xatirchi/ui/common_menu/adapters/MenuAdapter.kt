package org.xatirchi.ui.common_menu.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import org.telegram.messenger.databinding.MenuItemBinding
import org.telegram.ui.ActionBar.BackDrawable
import org.telegram.ui.ActionBar.Theme
import org.xatirchi.ui.common_menu.models.MenuModel

class MenuAdapter(var menus: ArrayList<MenuModel>, var menuClick: MenuClick) :
    RecyclerView.Adapter<MenuAdapter.MenuVH>() {

    inner class MenuVH(var menuItem: MenuItemBinding) : RecyclerView.ViewHolder(menuItem.root) {
        fun onBind(menuModel: MenuModel) {
            menuItem.menuItemIc.setImageResource(menuModel.menuIc ?: 0)
            menuItem.menuItemName.text = menuModel.menuName

            menuItem.root.setOnClickListener {
                menuClick.itemClick(menuModel.id ?: -1)
            }

            menuItem.menuItemName.setTextColor(Theme.getColor(Theme.key_chats_menuItemText))

        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MenuVH {
        return MenuVH(MenuItemBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun onBindViewHolder(holder: MenuVH, position: Int) {
        holder.onBind(menus[position])
    }

    override fun getItemCount(): Int = menus.size

    interface MenuClick {
        fun itemClick(id: Int)
    }

}