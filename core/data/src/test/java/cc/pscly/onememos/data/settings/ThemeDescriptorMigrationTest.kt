package cc.pscly.onememos.data.settings

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ApplicationProvider
import cc.pscly.onememos.domain.model.ThemeDensity
import cc.pscly.onememos.domain.model.ThemeDescriptor
import cc.pscly.onememos.domain.model.ThemeFontFamily
import cc.pscly.onememos.domain.model.ThemePalette
import cc.pscly.onememos.domain.model.ThemeTexture
import cc.pscly.onememos.domain.model.ThemeTypeScale
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ThemeDescriptorMigrationTest {
    private val legacyThemePaletteKey = stringPreferencesKey("theme_palette")
    private val themeDescriptorKey = stringPreferencesKey("theme_descriptor")

    @Test
    fun fromLegacyPalette_paperInk_mapsToWenmoZhushaAxes() {
        val d = ThemeDescriptor.fromLegacyPalette(ThemePalette.PAPER_INK)
        assertEquals(ThemePalette.PAPER_INK, d.palette)
        assertEquals(ThemeTexture.SCROLL, d.texture)
        assertEquals(ThemeDensity.STANDARD, d.density)
        assertEquals(ThemeTypeScale.STANDARD, d.typeScale)
        assertEquals(ThemeFontFamily.WENKAI, d.fontFamily)
    }

    @Test
    fun fromLegacyPalette_indigo_mapsToIndigoScrollWenKai() {
        val d = ThemeDescriptor.fromLegacyPalette(ThemePalette.INDIGO)
        assertEquals(ThemePalette.INDIGO, d.palette)
        assertEquals(ThemeTexture.SCROLL, d.texture)
        assertEquals(ThemeDensity.STANDARD, d.density)
        assertEquals(ThemeTypeScale.STANDARD, d.typeScale)
        assertEquals(ThemeFontFamily.WENKAI, d.fontFamily)
    }

    @Test
    fun fromLegacyPalette_cyber_mapsToCyberScrollSystemFont() {
        val d = ThemeDescriptor.fromLegacyPalette(ThemePalette.CYBER)
        assertEquals(ThemePalette.CYBER, d.palette)
        assertEquals(ThemeTexture.SCROLL, d.texture)
        assertEquals(ThemeDensity.STANDARD, d.density)
        assertEquals(ThemeTypeScale.STANDARD, d.typeScale)
        assertEquals(ThemeFontFamily.SYSTEM, d.fontFamily)
    }

    @Test
    fun legacyPaperInk_readsAsMappedDescriptor_andClearsOldKey() =
        runBlocking {
            val context = isolatedContext("paper_ink")
            seedLegacyPalette(context, "PAPER_INK")

            val repo = SettingsRepositoryImpl(context, FakeTokenStorage())
            val expected = ThemeDescriptor.fromLegacyPalette(ThemePalette.PAPER_INK)

            assertEquals(expected, repo.settings.first().themeDescriptor)
            assertEquals(ThemePalette.PAPER_INK, repo.settings.first().themePalette)

            awaitLegacyCleared(context)
            val prefs = context.settingsDataStore.data.first()
            assertFalse(prefs.contains(legacyThemePaletteKey))
            assertTrue(prefs.contains(themeDescriptorKey))
            assertEquals(expected, repo.settings.first().themeDescriptor)
        }

    @Test
    fun legacyIndigo_readsAsMappedDescriptor_andClearsOldKey() =
        runBlocking {
            val context = isolatedContext("indigo")
            seedLegacyPalette(context, "INDIGO")

            val repo = SettingsRepositoryImpl(context, FakeTokenStorage())
            val expected = ThemeDescriptor.fromLegacyPalette(ThemePalette.INDIGO)

            assertEquals(expected, repo.settings.first().themeDescriptor)
            awaitLegacyCleared(context)
            assertFalse(context.settingsDataStore.data.first().contains(legacyThemePaletteKey))
        }

    @Test
    fun legacyCyber_readsAsMappedDescriptor_andClearsOldKey() =
        runBlocking {
            val context = isolatedContext("cyber")
            seedLegacyPalette(context, "CYBER")

            val repo = SettingsRepositoryImpl(context, FakeTokenStorage())
            val expected = ThemeDescriptor.fromLegacyPalette(ThemePalette.CYBER)

            assertEquals(expected, repo.settings.first().themeDescriptor)
            assertEquals(ThemeFontFamily.SYSTEM, repo.settings.first().themeDescriptor.fontFamily)
            awaitLegacyCleared(context)
            assertFalse(context.settingsDataStore.data.first().contains(legacyThemePaletteKey))
        }

    @Test
    fun unknownLegacyPalette_fallsBackToWenmoZhusha_andClearsOldKey() =
        runBlocking {
            val context = isolatedContext("unknown")
            seedLegacyPalette(context, "NOT_A_REAL_PALETTE")

            val repo = SettingsRepositoryImpl(context, FakeTokenStorage())

            assertEquals(ThemeDescriptor.WENMO_ZHUSHA, repo.settings.first().themeDescriptor)
            assertEquals(ThemePalette.PAPER_INK, repo.settings.first().themePalette)
            awaitLegacyCleared(context)
            assertFalse(context.settingsDataStore.data.first().contains(legacyThemePaletteKey))
            assertTrue(context.settingsDataStore.data.first().contains(themeDescriptorKey))
        }

    @Test
    fun setThemePalette_writesDescriptorAndKeepsCompatAccessor() =
        runBlocking {
            val context = isolatedContext("set_palette")
            val repo = SettingsRepositoryImpl(context, FakeTokenStorage())

            repo.setThemePalette(ThemePalette.CYBER)
            val s = repo.settings.first()
            assertEquals(ThemePalette.CYBER, s.themePalette)
            assertEquals(ThemeDescriptor.fromLegacyPalette(ThemePalette.CYBER), s.themeDescriptor)
            assertFalse(context.settingsDataStore.data.first().contains(legacyThemePaletteKey))
        }

    @Test
    fun m2m3SchemaDefaults_areSeededOnFreshInstall() =
        runBlocking {
            val context = isolatedContext("schema_defaults")
            val repo = SettingsRepositoryImpl(context, FakeTokenStorage())
            val s = repo.settings.first()
            assertEquals(cc.pscly.onememos.domain.model.ListLayout.AUTO, s.listLayout)
            assertEquals(true, s.swipeEnabled)
            assertEquals(cc.pscly.onememos.domain.model.SwipeAction.ADD_TO_TODO, s.swipeRightAction)
            assertEquals(cc.pscly.onememos.domain.model.SwipeAction.FAVORITE, s.swipeLeftAction)
            assertEquals(true, s.pageTransitionsEnabled)
            assertEquals(cc.pscly.onememos.domain.model.ReadingFontScale.STANDARD, s.readingFontScale)
            assertEquals(cc.pscly.onememos.domain.model.ReadingLineHeight.STANDARD, s.lineHeight)
            assertEquals(false, s.listMarkdownImmediateLoad)
            assertEquals(ThemeDescriptor.WENMO_ZHUSHA, s.themeDescriptor)
        }

    private fun isolatedContext(suffix: String): Context {
        val app = ApplicationProvider.getApplicationContext<Context>()
        return app.let { _ ->
            val appCtx = ApplicationProvider.getApplicationContext<Context>()
            runBlocking {
                appCtx.settingsDataStore.edit { prefs ->
                    prefs.remove(legacyThemePaletteKey)
                    prefs.remove(themeDescriptorKey)
                }
            }
            appCtx
        }
    }

    private suspend fun seedLegacyPalette(context: Context, value: String) {
        context.settingsDataStore.edit { prefs ->
            prefs.remove(themeDescriptorKey)
            prefs[legacyThemePaletteKey] = value
        }
    }

    private suspend fun awaitLegacyCleared(context: Context) {
        withTimeout(5_000) {
            while (context.settingsDataStore.data.first().contains(legacyThemePaletteKey)) {
                delay(20)
            }
        }
    }

    private class FakeTokenStorage : TokenStorage {
        @Volatile private var token: String = ""

        override fun getToken(): String = token

        override fun setToken(token: String) {
            this.token = token
        }
    }
}
