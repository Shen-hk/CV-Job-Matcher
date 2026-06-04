package com.example.cv_jobmatcher.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.example.cv_jobmatcher.data.local.AppPreferences
import com.example.cv_jobmatcher.data.local.db.AppDatabase
import com.example.cv_jobmatcher.data.local.db.dao.HistoryDao
import com.example.cv_jobmatcher.data.remote.DeepSeekApiService
import com.example.cv_jobmatcher.data.remote.interceptor.ApiKeyInterceptor
import com.example.cv_jobmatcher.domain.nlp.KeywordClassifier
import com.example.cv_jobmatcher.domain.usecase.MatchAnalysisUseCase
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_settings")

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    // ── DataStore ──────────────────────────────────────────────

    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> {
        return context.dataStore
    }

    @Provides
    @Singleton
    fun provideAppPreferences(dataStore: DataStore<Preferences>): AppPreferences {
        return AppPreferences(dataStore)
    }

    // ── Moshi ──────────────────────────────────────────────────

    @Provides
    @Singleton
    fun provideMoshi(): Moshi {
        return Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
    }

    // ── OkHttp + Interceptor ───────────────────────────────────

    @Provides
    @Singleton
    fun provideApiKeyInterceptor(appPreferences: AppPreferences): ApiKeyInterceptor {
        return ApiKeyInterceptor(appPreferences)
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(apiKeyInterceptor: ApiKeyInterceptor): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        return OkHttpClient.Builder()
            .addInterceptor(apiKeyInterceptor)
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    // ── Retrofit ───────────────────────────────────────────────

    @Provides
    @Singleton
    fun provideDeepSeekApiService(
        okHttpClient: OkHttpClient,
        moshi: Moshi,
        appPreferences: AppPreferences
    ): DeepSeekApiService {
        return Retrofit.Builder()
            .baseUrl(appPreferences.getBaseUrlSync())
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(DeepSeekApiService::class.java)
    }

    // ── Room ───────────────────────────────────────────────────

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "cv_jobmatcher.db"
        ).fallbackToDestructiveMigration(false)
            .build()
    }

    @Provides
    @Singleton
    fun provideHistoryDao(db: AppDatabase): HistoryDao {
        return db.historyDao()
    }
}
