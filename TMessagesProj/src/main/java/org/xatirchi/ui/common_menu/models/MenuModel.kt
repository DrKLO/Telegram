package org.xatirchi.ui.common_menu.models

class MenuModel {

    var id: Int? = null
    var menuIc: Int? = null
    var menuName: String? = null

    constructor()

    constructor(id: Int, menuIc: Int, menuName: String) {
        this.id = id
        this.menuIc = menuIc
        this.menuName = menuName
    }

}