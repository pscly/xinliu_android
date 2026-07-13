# 平台能力拆为五个 Core 模块

新增 `:core:update`、`:core:calendar`、`:core:quicktiles`、`:core:externalactions` 与 `:core:diagnostics`，分别拥有更新交付、系统日历、快捷开关、外部系统动作与诊断导出的 Android 组件及实现。模块不得依赖 `:app` 或任何 `:feature:*`；需要包身份、快捷记录目标、截图入口或应用内回退目标时，只调用由 `:app` 组合根注入的窄端口，避免文件下沉后形成 `core -> app/feature` 的反向依赖。
