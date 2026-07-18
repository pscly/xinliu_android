package cc.pscly.onememos.domain.model

/**
 * 主题调色板（存储层/领域层可识别，UI 层负责把它渲染成具体配色与质感）。
 *
 * 五档：三套旧策展色 + 月白中性 + Material You 动态（API 31+）。
 */
enum class ThemePalette {
    /** 宣纸 + 朱砂（偏国风经典） */
    PAPER_INK,

    /** 黛蓝 + 宣纸（更清冷克制） */
    INDIGO,

    /** 玄青 + 荧光青（赛博国漫感） */
    CYBER,

    /** 月白中性：焦茶主色 + 雅灰次色 + 草白/米宣纸底 */
    MOON_WHITE,

    /**
     * 随系统动态色（Material You）。
     * API 31+ 使用 dynamicLight/DarkColorScheme(Context)；以下回退 [PAPER_INK]。
     * 对比度尽力而为，不进 WCAG 自动断言矩阵。
     */
    DYNAMIC,
}
