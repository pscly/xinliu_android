package cc.pscly.onememos.di

import cc.pscly.onememos.BuildConfig
import cc.pscly.onememos.core.network.AuthorizationInterceptor
import cc.pscly.onememos.core.network.MemosApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @Singleton
    fun provideOkHttpClient(
        authorizationInterceptor: AuthorizationInterceptor,
    ): OkHttpClient {
        val logging =
            HttpLoggingInterceptor().apply {
                // 仅 Debug 打开基础日志：Release 关闭，避免额外开销与潜在敏感信息暴露。
                level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC else HttpLoggingInterceptor.Level.NONE
            }
        return OkHttpClient.Builder()
            .addInterceptor(authorizationInterceptor)
            .addInterceptor(logging)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient): Retrofit =
        Retrofit.Builder()
            // baseUrl 只是占位，实际请求都会通过 @Url 传入完整 URL
            .baseUrl("https://example.invalid/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

    @Provides
    @Singleton
    fun provideMemosApi(retrofit: Retrofit): MemosApi =
        retrofit.create(MemosApi::class.java)
}
