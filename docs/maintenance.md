# 维护与验证

## 编辑入口

| 路径 | 负责内容 |
| --- | --- |
| `localization/source.json` | 真正的补丁仓库、固定提交、中文构建版本及目标 X |
| `localization/zh-CN/`、`messages.json`、`source-edits.json` | 语言资源与文本替换规则 |
| `fixes/source/` | 三个 Kotlin 补丁文件和四个 Java 扩展文件；职责见 fixes/README.md |
| `fixes/apply_fixes.py` | 精确源码清单、附加资源及生成目录写入校验 |
| `build_localized.py` | 克隆固定源码 → 测试 → 汉化 → 附加补丁 → 编译 → 对应源码归档 |
| `tests/` | 翻译、纯 Java 策略、运行时模拟及构建输入回归测试 |
| `fixes/tools/AndroidTypeProbe.java` | X 12.22.0 冻结目标的 14 类 ART 初始化预检，不是跨版本解析器 |
| `fixes/history.md`、`VALIDATION.md` | 历史包与验证证据，不能当作当前功能说明 |
| `docs/deferred/` | 已搁置设计，不参与补丁源码注入，不是待自动执行任务 |

不要在生成的 `.localized-source/`、`bins/` 或外部固定源码检出目录里做正式修改。构建输出附对应源码；`fixes/` 中的文档/工具可随源码归档，但只有七个清单文件注入补丁项目。

## 本地测试

使用 Java 21 和 Python 3.10+，在仓库根目录执行：

```text
python -m unittest discover -s tests -v
```

完整测试需要将 `PIKO_TEST_SOURCE` 指向 `source.json` 指定提交的干净上游源码检出，并确保 `java`、`javac` 在 PATH 中。未提供这些条件时，部分测试会跳过；“OK (skipped=...)”不能算完整通过。Actions 构建会自动准备固定源码和 Java。

测试分工：

- `test_localization.py`：覆盖率、占位符、重复资源、精确替换、错误源码拒绝。
- `test_list_fix.py`：附加文件/资源集成、类型桥接回归、旧序号禁用和转推过滤。
- `test_anchor_state.py`：身份匹配、重复 key、目标缺失、代次及取消。
- `test_anchor_runtime.py`：生命周期/竞争模拟，禁止在 provider 构建期间读取快照。
- `test_overlay_manifest.py`：源码清单精确匹配，缺文件、额外文件及同数量替换拒绝，ART 工具覆盖扩展类。

这些测试不是 Android 真机验证，不覆盖全部混淆类的最终打补丁结果。

## 构建与发布边界

1. 检查干净工作区和 `source.json`；保留旧提交及已验包。
2. 执行完整测试；源码漂移应失败，不通过放宽数量或匹配条件绕过。
3. 在新检出目录运行 `python build_localized.py` 或手动启动 localized Actions。现有生成目录会拒绝覆盖，不要自动删除用户目录。
4. 编译成功后，MPP 还需要对固定 X 输入打补丁；检查最终 DEX、签名、16 KiB 对齐和 14 类 ART 初始化。
5. 新 APK 必须记录独立版本、来源、校验值和实际测试范围；不能沿用旧 APK 的“已验证”标签。
6. 真机覆盖安装须保留账号数据，并保留已知回退包；遇到卡死先取应用范围证据并回退，不清数据。

这轮整理只改构建校验、工具和说明，不改 `fixes/source/` 运行逻辑，不产生新 APK，也不操作手机。未请求时不自动发布 Release、改 main 或推送远程。

## 升级与历史提交

构建仓库的 rebase 不会更新 `localization/source.json`。升级真实补丁源码需单独审查翻译替换、模型/生命周期入口、声明兼容目标和新旧回归测试。`patches-bundle.json` 是上游构建元数据，不是中文构建版本的来源。

历史 APK 的记录保留构建时原始提交号；2026-09-12 rebase 后，`40184c3` 的对应补丁提交为 `7d8ba1b`，不改变原包来源。不要为配合新历史而重写旧包校验值。

测试截图、UI XML、日志、凭据和用户帖子不得加入仓库或构建产物。位置恢复诊断仅允许动作、代次及脱敏范围标识。
