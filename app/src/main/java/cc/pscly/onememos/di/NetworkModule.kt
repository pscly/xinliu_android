package cc.pscly.onememos.di

import cc.pscly.onememos.BuildConfig
import cc.pscly.onememos.core.network.AuthorizationInterceptor
import cc.pscly.onememos.core.network.IpFamilyDns
import cc.pscly.onememos.core.network.IpFamilyEventListener
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
    /**
     * 全局 DNS：按设置调整 IPv4/IPv6 优先顺序（默认 IPv4 优先，IPv6 保留兜底），
     * 并记录实际连通的地址族做自适应。开关值由 OneMemosApplication 持续同步。
     */
    @Provides
    @Singleton
    fun provideIpFamilyDns(): IpFamilyDns = IpFamilyDns()

    @Provides
    @Singleton
    fun provideOkHttpClient(
        authorizationInterceptor: AuthorizationInterceptor,
        ipFamilyDns: IpFamilyDns,
    ): OkHttpClient {
        val logging =
            HttpLoggingInterceptor().apply {
                // 仅 Debug 打开基础日志：Release 关闭，避免额外开销与潜在敏感信息暴露。
                level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC else HttpLoggingInterceptor.Level.NONE
            }
        return OkHttpClient.Builder()
            .dns(ipFamilyDns)
            // 诊断 + 自适应：记录真正连通的地址族；失败时打 MemoNet warning 日志。
            .eventListener(IpFamilyEventListener(ipFamilyDns))
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
