# 导航契约集中而目的地实现归属 Feature

`:core:navigation` 集中定义可序列化 `NavKey`、顶层分区、返回栈机制与窄的类型化 Navigator，不依赖任何 Feature；每个 `:feature:*` 只提供本模块目的地的 Navigation 3 `entry`，通过 Navigator 提交 `NavKey` 或语义明确的返回动作，不能直接操作全局 `NavBackStack`，也不依赖目标 Feature。Navigator 只能由 Compose/entry 层使用，ViewModel 只产出状态、业务结果与一次性 UI 结果，不持有导航器。`:app` 只聚合 entry 与平台级导航输入。这样共享路由契约保持唯一来源，同时页面构造、ViewModel 获取和模块内导航细节留在能力所有者，避免 `OneMemosApp` 继续成为全部页面实现的巨型注册表。
