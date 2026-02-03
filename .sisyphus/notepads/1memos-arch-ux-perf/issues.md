# Issues

## 2026-02-03 Task: init
- Notepad initialized for plan `1memos-arch-ux-perf`.

## 2026-02-03 Task 16
- `SettingsRepository.setToken()` 仍可能在主线程被调用（取决于上层 ViewModel 调度）；当前任务聚焦冷启动与 settings 读取路径，写入路径如需进一步治理可单独跟进。
