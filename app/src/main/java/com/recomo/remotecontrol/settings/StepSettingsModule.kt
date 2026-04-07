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

private val Context.stepSettingsStore: DataStore<Preferences> by preferencesDataStore(
    name = "recomo_step_settings"
)

@Module
@InstallIn(SingletonComponent::class)
object StepSettingsModule {
    @Provides
    @Singleton
    @RecomoStepStore
    fun provideStepSettingsStore(@ApplicationContext context: Context): DataStore<Preferences> {
        return context.stepSettingsStore
    }
}
