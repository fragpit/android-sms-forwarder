package dev.local.smsforwarder.battery

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences

internal class BatteryTestContext(context: Context) : ContextWrapper(context) {
    override fun getApplicationContext(): Context = this

    override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
        super.getSharedPreferences("battery_test_$name", mode)
}
