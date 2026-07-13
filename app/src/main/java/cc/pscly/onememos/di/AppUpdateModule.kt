package cc.pscly.onememos.di

import cc.pscly.onememos.update.GitHubUpdateApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

@Module
@InstallIn(SingletonComponent::class)
object AppUpdateModule {
    @Provides
    @Singleton
    fun provideGitHubUpdateApi(): GitHubUpdateApi {
        // 更新请求必须与 Memos 客户端隔离，避免把用户的 Bearer Token 发往 GitHub。
        val client =
            OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .addInterceptor { chain ->
                    val request =
                        chain.request().newBuilder()
                            .header("Accept", "application/vnd.github+json")
                            .header("X-GitHub-Api-Version", "2022-11-28")
                            .header("User-Agent", "1memos-android-updater")
                            .build()
                    chain.proceed(request)
                }
                .build()
        return Retrofit.Builder()
            .baseUrl("https://api.github.com/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(GitHubUpdateApi::class.java)
    }
}
