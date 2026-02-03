package cc.pscly.onememos.domain.model

/**
 * 主题调色板（存储层/领域层可识别，UI 层负责把它渲染成具体配色与质感）。
 */
enum class ThemePalette {
    /** 宣纸 + 朱砂（偏国风经典） */
    PAPER_INK,

    /** 黛蓝 + 宣纸（更清冷克制） */
    INDIGO,

    /** 玄青 + 荧光青（赛博国漫感） */
    CYBER,
}
