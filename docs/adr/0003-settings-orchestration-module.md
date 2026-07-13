# 新增设置能力编排模块

新增 `:core:settings` 作为设置能力的应用级编排模块：面向六个能力页实现定义在 `:core:domain` 的深接口，组合设置存储、网络、同步、更新等能力，并隐藏跨能力副作用和错误映射。`:feature:settings` 在 Gradle 层移除对 `:core:data`、`:core:network`、`:core:sync` 的直接依赖，`:app` 只负责 Hilt 装配，避免把设置业务逻辑转移到组合根。
