package com.sonar.core

data class SonarError(
    val code: Int,
    val message: String,
) {
    val isOk: Boolean
        get() = code == OK.code

    companion object {
        val OK = SonarError(0, "ok")
        val ERR_FILE_NOT_FOUND = SonarError(-1, "file not found")
        val ERR_FILE_READ = SonarError(-2, "file read error")
        val ERR_UNSUPPORTED_FORMAT = SonarError(-3, "unsupported audio format")
        val ERR_DECODER_INIT = SonarError(-4, "decoder initialization failed")
        val ERR_DECODER_DECODE = SonarError(-5, "decoder error")
        val ERR_INVALID_STATE = SonarError(-6, "invalid player state")
        val ERR_SEEK_FAILED = SonarError(-7, "seek failed")
        val ERR_OUTPUT_FORMAT = SonarError(-8, "unsupported output format")
        val ERR_INTERNAL = SonarError(-9, "internal error")

        fun fromCode(code: Int, detail: String? = null): SonarError {
            val known = when (code) {
                0 -> OK
                -1 -> ERR_FILE_NOT_FOUND
                -2 -> ERR_FILE_READ
                -3 -> ERR_UNSUPPORTED_FORMAT
                -4 -> ERR_DECODER_INIT
                -5 -> ERR_DECODER_DECODE
                -6 -> ERR_INVALID_STATE
                -7 -> ERR_SEEK_FAILED
                -8 -> ERR_OUTPUT_FORMAT
                -9 -> ERR_INTERNAL
                else -> SonarError(code, "unknown error")
            }
            return if (detail.isNullOrBlank() || known.code == 0) known else known.copy(message = detail)
        }
    }
}
