package com.kentjohnson.hyperble.utils

class HyperBleThrowable(errorCode: Int, isScanError: Boolean = false) : Throwable() {
    override val message: String = when (errorCode) {
        ERROR_CODE_NO_CONNECTION -> ERROR_MSG_NO_CONNECTION
        ERROR_CODE_NO_CHAR_FOUND -> ERROR_MSG_NO_CHAR_FOUND
        ERROR_CODE_NO_DESC_FOUND -> ERROR_MSG_NO_DESC_FOUND
        ERROR_CODE_BAD_SVC_STATE -> ERROR_MSG_BAD_SVC_STATE
        ERROR_CODE_ON_SERVICE_DC -> ERROR_MSG_ON_SERVICE_DC
        else -> if(isScanError) ScanErrors.decode(errorCode) else GattStatuses.decode(errorCode)
    }

    companion object {
        const val ERROR_CODE_NO_CONNECTION: Int = 1000
        const val ERROR_CODE_NO_CHAR_FOUND: Int = 1001
        const val ERROR_CODE_NO_DESC_FOUND: Int = 1002
        const val ERROR_CODE_BAD_SVC_STATE: Int = 1003
        const val ERROR_CODE_ON_SERVICE_DC: Int = 1004
        const val ERROR_MSG_NO_CONNECTION: String = "Connection is null"
        const val ERROR_MSG_NO_CHAR_FOUND: String = "Characteristic not be found"
        const val ERROR_MSG_NO_DESC_FOUND: String = "Descriptor not found"
        const val ERROR_MSG_BAD_SVC_STATE: String = "Attempted to perform BLE operation before previous operation completed"
        const val ERROR_MSG_ON_SERVICE_DC: String = "Service Disconnected"
    }
}