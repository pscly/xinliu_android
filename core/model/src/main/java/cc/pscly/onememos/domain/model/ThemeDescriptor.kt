package cc.pscly.onememos.domain.model

/**
 * 主题描述符：色板 × 质感 × 密度 × 字阶 × 字体 五元组（ADR 0008 四轴 + 字体档）。
 *
 * 出厂风格预设是一组策展好的描述符；高级用户可在设置中单独调节某一轴。
 * 存储层以 JSON 写入 DataStore key `theme_descriptor`。
 */
data class ThemeDescriptor(
    val palette: ThemePalette = ThemePalette.PAPER_INK,
    val texture: ThemeTexture = ThemeTexture.SCROLL,
    val density: ThemeDensity = ThemeDensity.STANDARD,
    val typeScale: ThemeTypeScale = ThemeTypeScale.STANDARD,
    val fontFamily: ThemeFontFamily = ThemeFontFamily.WENKAI,
) {
    companion object {
        /**
         * 文墨·朱砂：默认出厂预设。
         * 朱砂色板 × 文墨卷轴 × 标准密度 × 标准字阶 × 文楷。
         */
        val WENMO_ZHUSHA: ThemeDescriptor = ThemeDescriptor(
            palette = ThemePalette.PAPER_INK,
            texture = ThemeTexture.SCROLL,
            density = ThemeDensity.STANDARD,
            typeScale = ThemeTypeScale.STANDARD,
            fontFamily = ThemeFontFamily.WENKAI,
        )

        /** 与 [WENMO_ZHUSHA] 相同，便于调用点语义化默认值。 */
        val DEFAULT: ThemeDescriptor = WENMO_ZHUSHA

        /**
         * 旧 `theme_palette` 枚举 → 描述符迁移映射（亦作色板→默认轴组合）。
         *
         * - PAPER_INK → 朱砂 × 文墨卷轴 × 标准 × 标准 × 文楷
         * - INDIGO → 黛蓝 × 文墨卷轴 × 标准 × 标准 × 文楷
         * - CYBER → 赛博 × 文墨卷轴 × 标准 × 标准 × 系统字体
         * - MOON_WHITE → 月白 × 清简 × 宽松 × 标准 × 系统字体（清简·月白预设）
         * - DYNAMIC → 动态 × 文墨卷轴 × 标准 × 标准 × 系统字体
         *
         * 未知/缺失由调用方回退到 [WENMO_ZHUSHA]。
         */
        fun fromLegacyPalette(palette: ThemePalette): ThemeDescriptor =
            when (palette) {
                ThemePalette.PAPER_INK ->
                    ThemeDescriptor(
                        palette = ThemePalette.PAPER_INK,
                        texture = ThemeTexture.SCROLL,
                        density = ThemeDensity.STANDARD,
                        typeScale = ThemeTypeScale.STANDARD,
                        fontFamily = ThemeFontFamily.WENKAI,
                    )

                ThemePalette.INDIGO ->
                    ThemeDescriptor(
                        palette = ThemePalette.INDIGO,
                        texture = ThemeTexture.SCROLL,
                        density = ThemeDensity.STANDARD,
                        typeScale = ThemeTypeScale.STANDARD,
                        fontFamily = ThemeFontFamily.WENKAI,
                    )

                ThemePalette.CYBER ->
                    ThemeDescriptor(
                        palette = ThemePalette.CYBER,
                        texture = ThemeTexture.SCROLL,
                        density = ThemeDensity.STANDARD,
                        typeScale = ThemeTypeScale.STANDARD,
                        fontFamily = ThemeFontFamily.SYSTEM,
                    )

                ThemePalette.MOON_WHITE ->
                    ThemeDescriptor(
                        palette = ThemePalette.MOON_WHITE,
                        texture = ThemeTexture.MINIMAL,
                        density = ThemeDensity.RELAXED,
                        typeScale = ThemeTypeScale.STANDARD,
                        fontFamily = ThemeFontFamily.SYSTEM,
                    )

                ThemePalette.DYNAMIC ->
                    ThemeDescriptor(
                        palette = ThemePalette.DYNAMIC,
                        texture = ThemeTexture.SCROLL,
                        density = ThemeDensity.STANDARD,
                        typeScale = ThemeTypeScale.STANDARD,
                        fontFamily = ThemeFontFamily.SYSTEM,
                    )
            }
    }
}

/**
 * 质感轴：表面与气质（纸面横线/印章/标签表现等）。
 */
enum class ThemeTexture {
    /** 文墨卷轴（横线 + 朱砂竖线 + 印章气质） */
    SCROLL,

    /** flomo 式清简（无横线、大留白、细描边、标签退后） */
    MINIMAL,
}

/**
 * 密度轴：留白与内容松紧。
 */
enum class ThemeDensity {
    /** 标准 */
    STANDARD,

    /** 宽松（呼吸感更强） */
    RELAXED,

    /** 紧凑 */
    COMPACT,
}

/**
 * 字阶轴：字号层级（阅读模式等会扩展更多档）。
 */
enum class ThemeTypeScale {
    STANDARD,
}

/**
 * 字体档：标题/正文所用字体家族。
 */
enum class ThemeFontFamily {
    /** 霞鹜文楷（标题书卷气） */
    WENKAI,

    /** 系统默认字体 */
    SYSTEM,
}

/**
 * 列表形态（M2 宽屏自适应；M1 仅进 schema 默认值）。
 */
enum class ListLayout {
    /** 随窗口宽度自动单列/双列 */
    AUTO,

    /** 强制单列 */
    SINGLE,

    /** 强制双列 */
    DOUBLE,
}

/**
 * 滑动手势动作池（M2；M1 仅进 schema 默认值）。
 */
enum class SwipeAction {
    ADD_TO_TODO,
    FAVORITE,
    ARCHIVE,
    PIN,
}

/**
 * 阅读字号档（M3；M1 仅进 schema 默认值）。
 */
enum class ReadingFontScale {
    SMALL,
    STANDARD,
    LARGE,
    EXTRA_LARGE,
}

/**
 * 阅读行距档（M3；M1 仅进 schema 默认值）。
 */
enum class ReadingLineHeight {
    COMPACT,
    STANDARD,
    RELAXED,
}
