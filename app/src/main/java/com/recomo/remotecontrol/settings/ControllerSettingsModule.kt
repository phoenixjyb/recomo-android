package com.recomo.remotecontrol.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

private val Context.controllerSettingsStore: DataStore<Preferences> by preferencesDataStore(
    name = "recomo_controller_settings"
)

@Module
@InstallIn(SingletonComponent::class)
object ControllerSettingsModule {
    @Provides
    @Singleton
    @RecomoControllerStore
    fun provideControllerSettingsStore(@ApplicationContext context: Context): DataStore<Preferences> {
        return context.controllerSettingsStore
    }
}
