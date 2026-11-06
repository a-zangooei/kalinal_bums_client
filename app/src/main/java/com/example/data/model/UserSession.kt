package com.example.data.model

data class UserSession(
    val isLoggedIn: Boolean = false,
    val username: String = "",
    val studentName: String = "",
    val studentNumber: String = "",
    val nationalId: String = "",
    val creditFormatted: String = ""
)

data class LoginPageState(
    val viewState: String = "",
    val viewStateGenerator: String = "C2EE9ABB",
    val publicKeyPem: String = "",
    val captchaBytes: ByteArray? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as LoginPageState
        return viewState == other.viewState &&
                viewStateGenerator == other.viewStateGenerator &&
                publicKeyPem == other.publicKeyPem &&
                ((captchaBytes == null && other.captchaBytes == null) ||
                        (captchaBytes != null && other.captchaBytes != null && captchaBytes.contentEquals(other.captchaBytes))) &&
                isLoading == other.isLoading &&
                errorMessage == other.errorMessage
    }

    override fun hashCode(): Int {
        var result = viewState.hashCode()
        result = 31 * result + viewStateGenerator.hashCode()
        result = 31 * result + publicKeyPem.hashCode()
        result = 31 * result + (captchaBytes?.contentHashCode() ?: 0)
        result = 31 * result + isLoading.hashCode()
        result = 31 * result + (errorMessage?.hashCode() ?: 0)
        return result
    }
}
