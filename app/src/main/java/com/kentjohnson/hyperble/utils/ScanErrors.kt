package com.kentjohnson.hyperble.utils

import android.bluetooth.le.ScanCallback.*
import android.support.annotation.RequiresApi

@RequiresApi(21)
enum class ScanErrors(val id: Int){
    ALREADY_STARTED(SCAN_FAILED_ALREADY_STARTED),
    APP_REGISTRATION_FAILED(SCAN_FAILED_APPLICATION_REGISTRATION_FAILED),
    FEATURE_UNSUPPORTED(SCAN_FAILED_FEATURE_UNSUPPORTED),
    INTERNAL_ERROR(SCAN_FAILED_INTERNAL_ERROR);

    companion object {
        fun decode(errorCode: Int): String {
            return values().associateBy(ScanErrors::id)[errorCode]?.name ?: "Unknown Scan Error Code: $errorCode"
        }
    }
}