# Piko NewX 汉化与自定义补丁

这是 [crimera/piko-newx](https://github.com/crimera/piko-newx) 的非官方 fork。当前自定义开发分支为 `codex/list-scroll-fix`；`main` 与 `codex/zh-cn` 不代表本分支最新功能。上游构建仓库从 [Piko x-lite](https://github.com/crimera/piko/tree/x-lite) 拉取真正的补丁源码；本仓库在编译前应用经校验的中文翻译和附加源码。

当前补丁源码锁定 Piko NewX **3.10.6**（`bc03fce4352bcb7ee89299722d0e900af3fb8a82`），中文/自定义构建版本为 **3.10.6-zh.2-listfix.6.2**，目标 X **12.22.0-prod.01**，以 [source.json](localization/source.json) 为准。构建仓库已 rebase 到上游 **v3.19.3**，不代表中文补丁或手机 APK 已升级到该版本。

## 当前功能与状态

- 简体中文界面，356 条原有翻译加 8 条自定义资源，共 364 条。
- 为你推荐、正在关注、所有列表三类时间线分别控制转推展示。
- 首页固定列表标签只展示名称。
- 实验性列表位置恢复：按账号/列表保存显示项身份，不改刷新数据、不补回旧帖。

列表切换和两次重启恢复已完成受控真机检查，检查期间未复现卡死；大量新增内容等场景仍未全面验收。功能边界、模块说明、已知限制和回退信息见 [自定义补丁说明](fixes/README.md)。每张列表独立过滤配置已搁置，未包含在当前版本。

## 汉化范围

- 356 条简体中文文本：设置、说明、搜索、过滤规则、白名单、功能开关、字体、备份恢复，以及下载、图片合并、分享和话题选择弹窗。
- 使用 Android `values-zh-rCN` 资源，简体中文环境自动生效；默认英文资源保留。
- `localization/` 只处理语言资源和界面文本；额外的时间线行为位于 `fixes/`，两者分开维护。
- 服务器返回的话题名称、帖子内容、内部功能开关键名和诊断日志不翻译。可选对象浏览器内部的开发者字段也保留原文。
- 不保证绕过 X 的服务端限制或年龄验证；这些不是语言资源能改变的功能。
- Morphe 补丁选择列表的补丁名称保留英文，确保原有自动化和补丁选择参数继续可用。

## 获取和构建

在 [Actions](https://github.com/Robert-Stackflow/piko-newx/actions/workflows/localized.yaml) 中选择 `codex/list-scroll-fix` 分支成功的 **Build Simplified Chinese NewX** 运行，下载 `piko-newx-zh-CN` 构建产物。登录 GitHub 后即可下载，产物包含 `patches.mpp`、校验报告、对应源码和上游许可文件。将补丁包手动导入 Morphe 后使用；这不是 APK，也不是上游签名的官方 Release。历史产物可能已过保留期，构建成功也不等于通过真机验收。

自动构建使用 Actions 自带的令牌读取公开构建依赖，不需要个人密钥、GPG 私钥或发布权限。该工作流不会自动创建 Release。上游遗留的发布工作流和 `patches-bundle.json` 仅为保留 fork 历史，不是中文补丁下载入口。

本地构建需要 Java 21、Android SDK、Python 3.10+ 和具备 `read:packages` 权限的 GitHub 令牌（通过环境变量 `GITHUB_ACTOR`、`GITHUB_TOKEN` 提供，不要写入仓库）：

```sh
python build_localized.py
```

构建脚本会校验固定的源码提交、翻译覆盖率、重复键、格式占位符、源码替换的精确匹配次数和自定义七文件清单；上游结构改变时停止构建，不静默遗漏。它会运行翻译、策略、生命周期和构建输入测试，保留英文资源，并生成对应源码归档。生成目录 `.localized-source/` 不是编辑入口。

后续升级时先更新 `localization/source.json` 中的上游版本和提交，再检查新增文本、调整 `source-edits.json` 和测试预期，最后运行自动构建。不要只修改版本号就发布。

维护入口和测试方法见 [维护指南](docs/maintenance.md)。[VALIDATION.md](VALIDATION.md) 仅记录早期 `zh.1` 汉化包；当前 `listfix.6.2` 包的证据见 [fixes/README.md](fixes/README.md)，不能把不同包的验证结论混用。

## 许可与署名

Piko 源码及其派生的翻译遵循 GPL-3.0，并保留上游 `NOTICE` 的署名要求。见 [localization/LICENSE](localization/LICENSE) 和 [NOTICE](NOTICE)。本分支新增翻译及构建集成由 Robert-Stackflow 维护，与上游官方发行版无隶属关系。

# Credits
- [morphe](https://github.com/MorpheApp) - patcher
- [revanced](https://github.com/ReVanced) - previous patcher
- [j-hc](https://github.com/j-hc) - Project is inspired by j-hc's revanced builder template.
