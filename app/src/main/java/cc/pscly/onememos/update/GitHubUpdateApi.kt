package cc.pscly.onememos.update

import retrofit2.http.GET

interface GitHubUpdateApi {
    @GET("repos/pscly/xinliu_android/releases/latest")
    suspend fun latestStableRelease(): GitHubReleaseDto
}
