# Markdown 渲染换用 mikepenz/multiplatform-markdown-renderer 引擎

自研 `MarkdownPaper` 不支持列表/待办、行内图片与语法高亮，补齐等于重造 Markdown 解析器。决定以 **mikepenz/multiplatform-markdown-renderer** 作为解析与块结构引擎，所有视觉元素映射到纸墨皮肤组件（引用竖条、代码块、表格描边沿用现有令牌）——换发动机、保留车身。

**取舍**：放弃继续打磨自研渲染器（零新依赖但列表/高亮/嵌套全要自己写，测试矩阵暴涨）与 Markwon + AndroidView 桥接（View 体系与 Compose 纸面滚动、30sp 横线基线、主题切换硬冲突）。

**后果**：迁移期新旧实现并存，新引擎需在样板屏验证渲染矩阵（长文/表格/图片/待办/代码）达标后才全局替换并退役旧实现；新增一个第三方依赖，版本升级需跟进渲染矩阵回归。
