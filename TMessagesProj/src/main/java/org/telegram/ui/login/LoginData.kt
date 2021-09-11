package org.telegram.ui.login

data class LoginData(
    val createdAt: String,
    val disabled: Boolean,
    val email: String,
    val id: String,
    val partnerId: String,
    val updatedAt: String
) {

}
data class LoginPuttingData (val email: String) {

} 