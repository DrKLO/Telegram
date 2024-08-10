package org.xatirchi.callApi.modul

class QR {
    var message: String? = null
    var uid: String? = null
    var url: String? = null
    var token: String? = null
    var exp: String? = null
    var now: String? = null

    constructor(
        message: String,
        uid: String,
        url: String,
        token: String,
        exp: String,
        now: String
    ) {
        this.message = message
        this.uid = uid
        this.url = url
        this.token = token
        this.exp = exp
        this.now = now
    }

    constructor()
}