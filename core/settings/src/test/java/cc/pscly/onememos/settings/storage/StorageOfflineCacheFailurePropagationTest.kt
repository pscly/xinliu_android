package cc.pscly.onememos.settings.storage

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import cc.pscly.onememos.domain.repository.CacheRepository
import cc.pscly.onememos.domain.repository.SettingsRepository
import cc.pscly.onememos.domain.settings.SettingsCapabilityError
import cc.pscly.onememos.domain.settings.StorageOfflineSettingsCommand
import cc.pscly.onememos.domain.settings.StorageOfflineSettingsResult
import java.io.File
import java.io.IOException
import java.lang.reflect.Proxy
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class StorageOfflineCacheFailurePropagationTest {
    @Test
    fun attachmentDaoFailure_isNotSwallowed_andReachesStorageFailure() =
        runBlocking {
            val daoCalls = AtomicInteger(0)
            val repository =
                createCacheRepository { methodName ->
                    if (methodName == "clearAllAttachmentCacheUris") {
                        daoCalls.incrementAndGet()
                        throw IOException("数据库写入失败")
                    }
                }
            val capability = StorageOfflineSettingsCapabilityImpl(unusedSettingsRepository(), repository)

            assertEquals(
                StorageOfflineSettingsResult.Failure(SettingsCapabilityError.StorageFailure),
                capability.execute(StorageOfflineSettingsCommand.ClearAttachmentCache),
            )
            assertEquals(1, daoCalls.get())
        }

    @Test
    fun deleteRecursivelyFalse_isNotReportedAsSuccessfulCleanup() =
        runBlocking {
            val repository = createCacheRepository()
            val attachmentDirectoryField =
                repository.javaClass.getDeclaredField("attachmentCacheRootDir").apply {
                    isAccessible = true
                }
            attachmentDirectoryField.set(
                repository,
                DeleteFailureFile(requireNotNull(attachmentDirectoryField.get(repository)).toString()),
            )
            val capability = StorageOfflineSettingsCapabilityImpl(unusedSettingsRepository(), repository)

            assertEquals(
                StorageOfflineSettingsResult.Failure(SettingsCapabilityError.StorageFailure),
                capability.execute(StorageOfflineSettingsCommand.ClearAttachmentCache),
            )
        }

    private fun createCacheRepository(
        onDaoMethod: (String) -> Unit = {},
    ): CacheRepository {
        val context = ApplicationProvider.getApplicationContext<Context>()
        File(context.filesDir, "one_memos_attachment_cache").deleteRecursively()
        val implementationClass = Class.forName("cc.pscly.onememos.data.cache.CacheRepositoryImpl")
        val constructor = implementationClass.declaredConstructors.single()
        val arguments =
            constructor.parameterTypes.map { parameterType ->
                when (parameterType.name) {
                    Context::class.java.name -> context
                    "coil.ImageLoader" -> createImageLoader(context)
                    "okhttp3.OkHttpClient" -> parameterType.getDeclaredConstructor().newInstance()
                    "cc.pscly.onememos.core.database.dao.MemoDao" ->
                        Proxy.newProxyInstance(
                            parameterType.classLoader,
                            arrayOf(parameterType),
                        ) { _, method, _ ->
                            onDaoMethod(method.name)
                            defaultValue(method.returnType)
                        }
                    SettingsRepository::class.java.name -> unusedSettingsRepository()
                    else -> error("未支持的 CacheRepositoryImpl 构造参数：${parameterType.name}")
                }
            }.toTypedArray()
        return constructor.newInstance(*arguments) as CacheRepository
    }

    private fun createImageLoader(context: Context): Any {
        val builderClass = Class.forName("coil.ImageLoader\$Builder")
        val builder = builderClass.getDeclaredConstructor(Context::class.java).newInstance(context)
        return requireNotNull(builderClass.getMethod("build").invoke(builder))
    }

    private fun unusedSettingsRepository(): SettingsRepository =
        Proxy.newProxyInstance(
            SettingsRepository::class.java.classLoader,
            arrayOf(SettingsRepository::class.java),
        ) { _, method, _ ->
            error("测试不应调用 SettingsRepository.${method.name}")
        } as SettingsRepository

    private fun defaultValue(returnType: Class<*>): Any? =
        when (returnType) {
            java.lang.Boolean.TYPE -> false
            java.lang.Byte.TYPE -> 0.toByte()
            java.lang.Short.TYPE -> 0.toShort()
            java.lang.Integer.TYPE -> 0
            java.lang.Long.TYPE -> 0L
            java.lang.Float.TYPE -> 0f
            java.lang.Double.TYPE -> 0.0
            java.lang.Character.TYPE -> '\u0000'
            else -> null
        }

    private class DeleteFailureFile(path: String) : File(path) {
        override fun exists(): Boolean = true

        override fun isDirectory(): Boolean = false

        override fun delete(): Boolean = false
    }
}
