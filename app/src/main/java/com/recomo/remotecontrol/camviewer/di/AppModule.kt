package com.recomo.remotecontrol.camviewer.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.recomo.common.chat.ChatTransportConfig
import com.recomo.common.chat.ChatTransportConfigProvider
import com.recomo.common.chat.voice.WhisperModelRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "camviewer_settings")

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> {
        return context.dataStore
    }

    /**
     * Engineering app doesn't ship the AI chat settings UI — fall back to the
     * WS-bridge default. ChatViewModel is pulled into this Hilt graph
     * transitively via :common, so the binding has to exist even if the
     * engineering app never instantiates the ViewModel.
     */
    @Provides
    @Singleton
    fun provideChatTransportConfigProvider(): ChatTransportConfigProvider =
        object : ChatTransportConfigProvider {
            override fun currentConfig(): ChatTransportConfig = ChatTransportConfig()
        }

    /** Same pattern as ChatTransportConfigProvider — :common exports
     *  WhisperModelRepository in the Hilt graph transitively; :app never
     *  uses it but Dagger needs the binding to compile. */
    @Provides
    @Singleton
    fun provideWhisperModelRepository(
        @ApplicationContext context: Context
    ): WhisperModelRepository = WhisperModelRepository(context)
}
