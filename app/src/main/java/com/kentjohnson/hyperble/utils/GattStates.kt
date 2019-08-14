package com.kentjohnson.hyperble.utils

import android.bluetooth.BluetoothProfile.STATE_CONNECTED
import android.bluetooth.BluetoothProfile.STATE_DISCONNECTED


enum class GattStates(val id: Int){
    CONNECTED(STATE_CONNECTED),
    DISCONNECTED(STATE_DISCONNECTED);

    companion object {
        fun decode(state: Int): String {
            return values().associateBy(GattStates::id)[state]?.name ?: "Unknown Gatt State: $state"
        }
    }
}

