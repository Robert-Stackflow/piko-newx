# 列表位置恢复 v3（dev14 候选）

状态：实现与构建验证中，尚未通过真机验收。X 固定 12.22.0-prod.01，原包 SHA256 `51d43aec02604d097677d0cfc68c7639046ceed6b007610aee143c007979b48f`。Piko 固定 bc03fce4352bcb7ee89299722d0e900af3fb8a82。不升级 X/Piko，不复活 listfix.4 的缓存合并。

## 调查更正

- For You/Latest Following 启用原生 index/offset holder；Ranked Following/固定列表默认关闭这条路径。Compose 实例自身状态是另一层。
- 首页手动下拉查找 Top cursor；List 默认无游标。无游标或服务端 ClearCache 会在普通数据处理中生成 Delete，再 Add 返回条目。
- For You 生产视口缓存，Following/List 没有走相同分支。Piko 仅把 For You/普通 Following 的无游标 AUTO_REFRESH 改为 VIEWPORT_AWARE_AUTO_REFRESH。
- FlashScrollIndicator 不回顶。服务器 NavigateToTop 与用户共用回顶 dispatcher；dev13 将共享入口误认成用户动作，因此可能删除列表书签。此为静态可达缺陷，不是用户每次失败的现场根因证明。

## 三层实现

1. 请求：仅对启用位置保护的 List 手动下拉，复用原生 Top cursor 查找。找不到 Top 时原生返回 null；自动刷新和其他页面原样。保留服务端 ClearCache、删除/排序指令、数据库合并和分页，不手工补回旧帖。List 服务端的增量行为仍需实测。
2. 导航：仅在 NavigateToTop 消费者处截获 List 自动回顶。用户主动回顶、非 List、关闭任一位置开关时走原生回调；不全局屏蔽共享 dispatcher。
3. 视口：账户 + List scope 保存最后确认的完整 native key/偏移。先精确匹配；不存在时仅允许长度前缀 entry identity 唯一匹配，保留新的完整 key 用于测量确认。重复身份拒绝猜测，不改 X 的 Compose key。

## 状态约定

- last confirmed bookmark 与 pending restore 分离；取消自动移动不删除最后书签。
- 中间批次缺少目标时保留 pending，不用新头部覆盖书签；后续批次重试。用户实际浏览后可建立新书签，不自动请求历史分页。
- resolve 不代表完成。确认要求同一 generation 的 measured key、index、offset 都与目标一致；确认前不保存请求目标。
- 原生已保持同帖同偏移时直接确认，不发冗余滚动请求。
- provider 回调只存弱引用并排队，禁止在 Compose provider 构建期间读取 snapshot、遍历 key 或滚动，保留 ANR 后的安全约束。
- 独立观察要求 measured key 与 provider 当前 index 的原始 key 一致；两个同代次一致观察后请求。80ms 仅为调度节流，不是网络完成信号。
- 请求 1 秒未确认则保留书签，停止同代次重试；新数据代次或用户动作才继续，避免无限回拉。不可达偏移不伪装成功。
- 实际滚动优先，允许记录一致的运动中画面；离开时不因惯性滚动丢弃位置。
- 主动回顶清除书签后，在顶部布局到达前禁止重新保存旧画面；新手势可接管。

## 冻结字节码与解析契约

以下混淆名只作分析证据，不用作生产版本路由。

- `repositories.urt.f1.invokeSuspend` 0393–03b2：TimelineNavigation(true) → NavigateToTop，viewport-aware 模式跳过；0581–05b9：ClearCache/cursorless 的数据删除分支。
- `urt.instructions.c.invokeSuspend` 001e–0027：instance-of NavigateToTop → IF_EQZ → callback 字段 → invoke → GOTO。根据模型 toString、分支、callback 持有的组件及 dispatcher 调用验证唯一性，只替换该 callsite。
- `urt.e.invoke` case 0 → `urt.y.i(a1)`；dev13 的后者 016d 调用 pikoCancelListAnchor。新桥接仅让服务器 List 调用绕过它。
- `urt.y.s` 0097–00e3：Top policy 查找 Top cursor。按静态参数关系、Top 枚举、policy 接口及 areEqual 分支解析，不写死 refresh.c.f。
- `googlessp.init.c.invokeSuspend` 0300–0318：PULL_TO_REFRESH → policy → cursor resolver → repo.i。只在已解析 dispatcher 构造的 SuspendLambda 中，按请求枚举和 resolver 验证唯一调用点；调用前仅替换启用 List 的 policy。

零/多匹配、参数/控制流变化时抛 PatchException；不静默跳过。只声明当前冻结目标，不声称跨版本通过。

## 验证边界

纯 Java 状态/真实运行时模拟覆盖前插、排序键变化、重复身份、非空中间批次、过期代次、用户接管、运动中离开、原生已保位、回顶未测量前离开、无补帖及 Compose 重入保护。

还需 CI 编译 MPP、固定 APK 完整打补丁、最终 DEX/ART 检查及真机验收。真机矩阵：多个列表快速切换、真实新增后刷新、目标暂缺、主动回顶、滚动中切换、重启、开关关闭对照，以及 For You/两种 Following 对照。

不保证恢复服务器已删除、未加载或被过滤的帖子；不显示其他帖子冒充恢复。横向模块、字体/卡片高度改变、极大量条目及离线重启仍有边界。未经完整验收不发布 Release 或覆盖手机。
