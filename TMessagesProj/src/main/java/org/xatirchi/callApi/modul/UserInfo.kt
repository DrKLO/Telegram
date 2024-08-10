package org.xatirchi.callApi.modul

class UserInfo {

    var uid: String? = null
    var password: String? = null

    constructor()

    constructor(uid: String, password: String) {
        this.uid = uid
        this.password = password
    }
}
