package cc.pscly.onememos.di

import cc.pscly.onememos.BuildConfig
import cc.pscly.onememos.core.network.FlowBackendApi
import cc.pscly.onememos.core.network.FlowDeviceHeadersInterceptor
import cc.pscly.onememos.core.network.FlowDeviceInfoProvider
import cc.pscly.onememos.core.network.AndroidFlowDeviceInfoProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import android.content.Context
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object FlowBackendModule {
    @Provides
    @Singleton
    fun provideFlowDeviceInfoProvider(
        @ApplicationContext context: Context,
    ): FlowDeviceInfoProvider = AndroidFlowDeviceInfoProvider(context)

    @Provides
    @Singleton
    fun provideFlowBackendApi(
        flowDeviceHeadersInterceptor: FlowDeviceHeadersInterceptor,
    ): FlowBackendApi {
        val logging =
            HttpLoggingInterceptor().apply {
                // 账号登录/注册不打印敏感信息；Release 也不需要网络日志。
                level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC else HttpLoggingInterceptor.Level.NONE
            }
        val client =
            okhttp3.OkHttpClient.Builder()
                .addInterceptor(flowDeviceHeadersInterceptor)
                .addInterceptor(logging)
                .build()

        val base = BuildConfig.FLOW_BACKEND_BASE_URL.trim().let { if (it.endsWith("/")) it else "$it/" }
        val retrofit =
            Retrofit.Builder()
                .baseUrl(base)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()

        return retrofit.create(FlowBackendApi::class.java)
    }
}
