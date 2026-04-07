package com.recomo.user.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.recomo.common.network.OrinGatewayClient
import com.recomo.user.data.UserSettingsRepository
import com.recomo.user.data.system.UserOrinServiceRepository
import com.recomo.user.data.trajectory.LocalTrajectorySessionRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object UserAppModule {
    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    @Provides
    @Singleton
    fun providePreferencesDataStore(
        @ApplicationContext context: Context
    ): DataStore<Preferences> = PreferenceDataStoreFactory.create {
        context.preferencesDataStoreFile("user_app_settings.preferences_pb")
    }

    @Provides
    @Singleton
    fun provideOrinGatewayClient(json: Json): OrinGatewayClient = OrinGatewayClient(json)

    @Provides
    @Singleton
    fun provideUserSettingsRepository(
        dataStore: DataStore<Preferences>
    ): UserSettingsRepository = UserSettingsRepository(dataStore)

    @Provides
    @Singleton
    fun provideUserOrinServiceRepository(
        userSettingsRepository: UserSettingsRepository,
        json: Json
    ): UserOrinServiceRepository = UserOrinServiceRepository(userSettingsRepository, json)

    @Provides
    @Singleton
    fun provideLocalTrajectorySessionRepository(
        @ApplicationContext context: Context
    ): LocalTrajectorySessionRepository = LocalTrajectorySessionRepository(context)
}
