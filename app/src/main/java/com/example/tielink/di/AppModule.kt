package com.example.tielink.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.example.tielink.data.local.AppPreferences
import com.example.tielink.data.local.db.AppDatabase
import com.example.tielink.data.local.db.dao.HistoryDao
import com.example.tielink.data.local.db.dao.InterviewDao
import com.example.tielink.data.local.db.dao.JdLibraryDao
import com.example.tielink.data.local.db.dao.ProviderDao
import com.example.tielink.data.local.db.dao.ResumeVersionDao
import com.example.tielink.data.local.db.dao.TrackingDao
import com.example.tielink.data.repository.ProviderRepository
import com.example.tielink.data.remote.DeepSeekApiService
import com.example.tielink.data.remote.DeepSeekApiServiceFactory
import com.example.tielink.data.remote.DeepSeekProvider
import com.example.tielink.data.remote.OllamaProvider
import com.example.tielink.data.remote.interceptor.ApiKeyInterceptor
import com.example.tielink.domain.nlp.KeywordClassifier
import com.example.tielink.domain.usecase.MatchAnalysisUseCase
import com.example.tielink.util.TextCleaner
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
            "tielink.db"
        )
            .addMigrations(AppDatabase.MIGRATION_3_4, AppDatabase.MIGRATION_4_5, AppDatabase.MIGRATION_6_7, AppDatabase.MIGRATION_8_9, AppDatabase.MIGRATION_9_10)
            .fallbackToDestructiveMigration(true)
            .build()
    }

    @Provides
    @Singleton
    fun provideHistoryDao(db: AppDatabase): HistoryDao {
        return db.historyDao()
    }

    @Provides
    @Singleton
    fun provideResumeVersionDao(db: AppDatabase): ResumeVersionDao {
        return db.resumeVersionDao()
    }

    @Provides
    @Singleton
    fun provideTrackingDao(db: AppDatabase): TrackingDao {
        return db.trackingDao()
    }

    @Provides
    @Singleton
    fun provideInterviewDao(db: AppDatabase): InterviewDao {
        return db.interviewDao()
    }

    @Provides
    @Singleton
    fun provideJdLibraryDao(db: AppDatabase): JdLibraryDao {
        return db.jdLibraryDao()
    }

    @Provides
    @Singleton
    fun provideProviderDao(db: AppDatabase): ProviderDao {
        return db.providerDao()
    }

    // ── Utilities ──────────────────────────────────────────────

    @Provides
    @Singleton
    fun provideTextCleaner(): TextCleaner {
        return TextCleaner
    }

    // ── AI Providers ───────────────────────────────────────────

    @Provides
    @Singleton
    fun provideDeepSeekProvider(
        apiServiceFactory: DeepSeekApiServiceFactory,
        appPreferences: AppPreferences
    ): DeepSeekProvider {
        return DeepSeekProvider(apiServiceFactory, appPreferences)
    }

    @Provides
    @Singleton
    fun provideOllamaProvider(
        appPreferences: AppPreferences
    ): OllamaProvider {
        return OllamaProvider(appPreferences)
    }
}
