package com.orthohin.alarmist.guard

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.orthohin.alarmist.core.ringing.PowerMenuGuard
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject

internal class AndroidPowerMenuGuard @Inject constructor(@ApplicationContext private val context: Context) :
    PowerMenuGuard {
    override fun isEnabled(): Boolean {
        val me = ComponentName(context, PowerMenuGuardService::class.java)
        val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        return enabled.orEmpty().split(':').any { ComponentName.unflattenFromString(it) == me }
    }

    override fun settingsIntent(): Intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class GuardModule {
    @Binds
    abstract fun powerMenuGuard(impl: AndroidPowerMenuGuard): PowerMenuGuard
}
