package com.kentjohnson.hyperble

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import com.kentjohnson.hyperble.utils.HyperBleThrowable
import com.kentjohnson.hyperble.utils.HyperBleThrowable.Companion.ERROR_CODE_ON_SERVICE_DC
import io.reactivex.Single
import io.reactivex.SingleEmitter

class HyperBle : ServiceConnection {
    private lateinit var mEmitter: SingleEmitter<HyperBleService>

    override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
        mEmitter.onSuccess((service as HyperBleService.LocalBinder).getService())
    }

    override fun onServiceDisconnected(name: ComponentName?) {
        mEmitter.onError(HyperBleThrowable(ERROR_CODE_ON_SERVICE_DC))
    }

    fun bindService(context: Context): Single<HyperBleService> {
        return Single.create { subscriber ->
            mEmitter = subscriber
            context.bindService(Intent(context, HyperBleService::class.java), this, Context.BIND_AUTO_CREATE)
        }
    }

    fun unbindService(context: Context) {
        context.unbindService(this)
    }
}