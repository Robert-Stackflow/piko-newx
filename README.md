# Piko NewX 简体中文与自维护功能

这是 [Piko](https://github.com/crimera/piko) 的非官方定制构建。`main` 同时保存旧版构建覆盖层（`localization/`、`fixes/`、`features/`）和当前 12.43 移植源码（`piko-source/`）。当前构建以 `piko-source/` 为准；旧覆盖层只用于追溯和升级比对，**不会再叠加到当前源码**。它不是 Piko 官方发行版。

构建输入由 [localization/source.json](localization/source.json) 锁定：Piko NewX 3.11.1 的自维护移植源码，目标 X `12.43.0-prod.01`。`source_mode: vendored` 表示源码直接保存在 `main`，而非在构建时临时拉取一个可能变化的分支。源码提交号和 `piko-source/` 的 Git tree 必须一致；[功能契约测试](tests/test_current_feature_contract.py) 会检查这一点。版本号、已安装 APK、补丁源码是三件不同的事，不能仅凭其中一项推断另外两项已更新。

## 我们维护的功能

以下是相对上游自行维护或移植的部分，不把上游原有功能算作本项目独创：

| 范围 | 当前实现 |
| --- | --- |
| 简体中文 | Piko NewX 设置及相关界面中文资源；下载、图片合并和错误状态等运行时文本也已适配。服务器返回的帖子内容不翻译。 |
| 时间线 | “为你推荐”“正在关注”“列表”分别控制是否显示转推；首页固定列表标签只显示名称。 |
| 阅读位置 | 按账号和列表保存帖子身份与偏移，切换列表、重启后按可见项恢复；列表刷新保留阅读锚点，“为你推荐”手动刷新使用 X 原生的视口感知刷新，以让新帖插入上方而非直接回顶。不会为恢复位置补回旧帖。 |
| 首页入口 | 顶部更多菜单进入 Piko 设置、下载管理、浏览历史。 |
| 下载管理 | 本地任务、重试、清空和媒体预览；页面样式尽量贴近 X。实际下载清晰度由 X 提供的媒体流和下载逻辑决定，不能承诺始终为最高质量。 |
| 浏览历史 | 帖子详情、点击图片，以及视频页前台当前视频均可记录；视频上滑后逐条记录，预加载不计入。支持本地帖子卡片、媒体预览、搜索、全部/帖子/视频切换与清空。 |
| 视频工具 | 独立的观看进度与返回续播记录，以及视频页标题栏的防误触图标；原有播放速度等上游能力仍由上游实现。 |

这些功能在 `piko-source/` 中的主要入口是 `timeline/`、`mediatools/` 补丁与同名运行时包。具体旧版设计和边界见 [列表位置说明](fixes/position-restoration-v3.md) 与 [媒体工具说明](features/README.md)；其中旧文档的版本号与验证结果只对应各自历史构建，不是 12.43 的验收报告。

上游目前用 `NewX: Theme` 提供 Material You/高对比度等主题能力，旧的“动态配色”补丁名称消失不等于功能消失。相反，下列旧补丁**尚未证明在 12.43 中等效**，不能在升级报告中默认为已保留：

- `NewX: Share post as image`：上游已移除。旧实现可在隔离工作区编译，但尚未通过 12.43 补丁应用与真机验证。
- `NewX: Force highest video/audio quality`：上游已退役；新 X 的媒体策略可能已改变，但尚无等效性证据。

在补齐验证或明确决定接受功能变化之前，不要将“所有旧功能无损迁移”作为发布结论。`codex/list-scroll-fix` 等早期实验分支不能直接合并成生产功能；应比对最终行为和测试，再选择当前实现。

## 升级新 X / Piko 时的保功能流程

1. 以当前 `main` 和上一份**实际可用 APK** 为基线，逐项核对上表及两个未确认项；记录旧包哈希、X 版本、补丁源码提交与已选补丁清单。不要仅修改 `source.json` 的版本号。
2. 在独立分支移植上游，逐文件检查 `timeline/`、`mediatools/`、中文资源与调用点。上游删除、重命名或改变默认值时，先判断行为是否等效；需要删减自维护功能时明确记录并征得用户同意。
3. 把完成的源码更新到 `piko-source/`，更新 [source.json](localization/source.json) 中的源码提交、X/Piko 版本，并扩充 [功能契约测试](tests/test_current_feature_contract.py)。契约测试防止入口、运行时类或中文资源意外消失；它**不能代替**字节码补丁和真机行为测试。
4. 运行 `python -m unittest discover -s tests -p test_current_feature_contract.py -v`、`python build_localized.py`，检查构建报告和所有预期补丁的应用结果。补丁跳过、指纹不唯一、资源缺失或测试失败均应停止发布，不靠减少补丁数量“修复”构建。
5. 基于准确的 X 原包制作 APK，校验签名、目标架构、版本及 APK 哈希。覆盖安装只用 `adb install -r`，先确认签名兼容；不要清除应用数据来绕过安装失败。真机回归至少覆盖：列表切换/重启/刷新、“为你推荐”下拉刷新、转推过滤、首页入口、图片点击后的历史、视频页连续上滑历史、续播、防误触、下载管理与中文显示。
6. 将版本对应的测试结论、已知差异和产物哈希写入发行说明。源码编译成功、补丁应用成功、真机可用必须分别陈述；旧包的验收不能自动沿用到新包。

## 构建与产物

需要 Java 21、Android SDK、Python 3.10+，以及可读取构建依赖的 GitHub Packages 凭据（通过 `GITHUB_ACTOR`、`GITHUB_TOKEN` 环境变量提供，不要提交令牌）。运行：

```sh
python build_localized.py
```

脚本从 `piko-source/` 复制 Git 跟踪的文件到临时 `.localized-source/`，先执行功能契约测试，再构建 `bins/patches.mpp`、源码归档和报告。`bins/` 与 `.localized-source/` 是生成目录，不应提交。Actions 的 [Build Simplified Chinese NewX](https://github.com/Robert-Stackflow/piko-newx/actions/workflows/localized.yaml) 只生成补丁包，**不会自动生成或安装 X APK，也不会自动发布 Release**。

补丁包是 Morphe 输入；APK 是另行将补丁应用到特定 X 原包后的产物。两者不可混称。旧版覆盖层的原始构建流程仍留在脚本的非 `vendored` 路径中，供审计和必要时回退使用。

## 许可

保留上游 `LICENSE`、`NOTICE` 及各文件原始署名。中文翻译和自维护功能由 Robert-Stackflow 维护，与 Piko、X 官方无隶属关系。
