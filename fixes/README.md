# 当前自定义补丁

当前配置：`localization/source.json`，版本 `3.10.6-zh.2-listfix.6.2`，目标 X `12.22.0-prod.01`。
构建仓库已 rebase 到上游 v3.19.3；实际补丁源码仍固定为 `crimera/piko@bc03fce4352bcb7ee89299722d0e900af3fb8a82`。两者不是同一个版本。

## 已实现功能

- 为你推荐、正在关注（含 ranked Following）、所有列表三组独立转推开关，默认展示；引用帖不因转推开关单独被隐藏。
- 首页固定列表标签只显示名称，其他标签类型保持原样。
- 实验性列表位置恢复：按账号和原生列表 ID 保存最终显示项的完整 key 与偏移。仅唯一匹配时恢复，缺失时不使用旧序号、不补回旧帖。

每张列表单独的转推/回复/关键词配置**尚未实现且已搁置**，不要与“三类时间线开关”混淆。

## 模块职责

Kotlin 补丁位于 `source/patches/src/main/kotlin/app/crimera/patches/newx/timeline/`；Java 扩展位于 `source/extensions/newx/src/main/java/app/morphe/extension/newx/timeline/`。

| 模块 | 职责 |
| --- | --- |
| `PreserveListReadingPositionPatch.kt` | 解析原生位置桥接；处理列表旧序号存取路径，注册设置 |
| `ListPositionUiHooks.kt` | 解析账号/列表身份、最终显示 key、生命周期、手势及回顶入口，生成直接调用 |
| `HomeTimelineOptionsPatch.kt` | 三类转推设置、模型适配及列表标签去图标 |
| `ListReadingPosition.java` | 受开关控制的旧序号路径禁用策略，**不保存位置** |
| `ListAnchorState.java` | 无 Android 依赖的身份匹配、代次及取消状态机 |
| `ListPositionRuntime.java` | 生命周期观察、独立 Handler 调度、账号/列表隔离的持久化 |
| `TimelineRepostFilter.java` | 显示层转推过滤，维护不可变集合及线程模块关联 ID |

`apply_fixes.py` 先核对源码版本、精确的七文件清单、目标不覆盖及资源不重复，再写入生成目录。清单变化必须显式审查，不允许只检查数量。

## 不可破坏的边界

- 不修改请求、仓库数据、刷新合并策略、分页游标或数据库；不复活已撤回的 listfix.4 合并逻辑。
- 位置 key 使用最终 `RegularItem(entryId, sortIndex)`，不是仓库序号或裸帖子 ID；完整 key 改变也可能导致无法恢复。
- 开关 `newx.timeline.list_reading_position` 和上游 `newx.timeline.restore_position` 共同控制列表身份恢复。非列表沿用上游逻辑。
- `render` 在 Compose provider 构建期间仅保存弱引用并排队；禁止同步读布局快照、遍历 provider 或请求滚动。相关工作在 Handler 执行。
- 持久化采用 `piko_newx_list_anchors_v2`，不迁移旧序号。参数、设置键、原生绑定语义和未知模型保留策略不能在整理时顺便改变。
- 所有混淆绑定在补丁阶段通过语义、类型关系及唯一性检查解析；工具中的固定类名只服务于冻结目标的验证。

## 验证与当前限制

已安装的 listfix.6.2 原始构建：Actions [34689191257](https://github.com/Robert-Stackflow/piko-newx/actions/runs/34689191257)，原始源码提交 `40184c3`（rebase 后对应 `7d8ba1b`）。
APK SHA-256：`c825e24b10b3b0296b513814deff843658cf2c03e8c7e0b5ec32a2f6b43f4c27`。

该构建通过 14 项单测、226 项最终 DEX 断言、14 类 ART 初始化检查、364 条中文资源、36 项补丁、签名一致性及 16 KiB 对齐检查。2026-09-12 受控真机测试覆盖三个列表切换、两次重启恢复、手动滚动不回拉及可见内容不变的下拉刷新；7 组可见文本/边界一致，检查期间无新 ANR/闪退。

这些结论仅对应上述已验证 APK。整理代码后新增的构建输入检查不能替代新 APK 的最终 DEX 和实机验证；未重新打包时，不将旧 APK 标为新提交的产物。

新增帖子插入前方、大量替换、分页、网络失败、显式回顶及账号/过滤变更仍需完整实机验收。没有匹配 key 时无法保证原地恢复；这仍是实验版本。

回退包为 listfix.5，SHA-256 `c6cc4eb336df5d25ca40ba11cc2cc8dde5c9e711128be5f79c9a1159a62bf75c`。禁止安装已拒绝的 listfix.1、listfix.4、listfix.6、listfix.6.1。

- [历史构建、回退与实测证据](history.md)：保留原始提交号和包校验值。
- [位置恢复设计](position-restoration-design.md)：实现边界与历史设计。
- [维护与验证流程](../docs/maintenance.md)：源码入口、测试和构建步骤。
