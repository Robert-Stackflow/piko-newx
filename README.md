# Piko NewX 简体中文支持

这是 [crimera/piko-newx](https://github.com/crimera/piko-newx) 的非官方汉化 fork，翻译位于 `codex/zh-cn` 分支。上游构建仓库从 [Piko x-lite](https://github.com/crimera/piko/tree/x-lite) 拉取真正的补丁源码；本仓库保留这一结构，在编译前自动应用经过校验的中文翻译。

当前锁定 Piko NewX **3.10.6**（源码 `bc03fce4352bcb7ee89299722d0e900af3fb8a82`），中文构建版本为 **3.10.6-zh.1**，上游目标 X 版本为 **12.22.0-prod.01**。

## 汉化范围

- 356 条简体中文文本：设置、说明、搜索、过滤规则、白名单、功能开关、字体、备份恢复，以及下载、图片合并、分享和话题选择弹窗。
- 使用 Android `values-zh-rCN` 资源，简体中文环境自动生效；默认英文资源保留。
- 仅将用户界面文字改为资源引用，不修改账号数据、设置键、下载逻辑或补丁匹配规则。
- 服务器返回的话题名称、帖子内容、内部功能开关键名和诊断日志不翻译。可选对象浏览器内部的开发者字段也保留原文。
- 不保证绕过 X 的服务端限制或年龄验证；这些不是语言资源能改变的功能。

## 获取和构建

在 [Actions](https://github.com/Robert-Stackflow/piko-newx/actions/workflows/localized.yaml) 中打开成功的 **Build Simplified Chinese NewX** 运行，下载 `piko-newx-zh-CN` 构建产物。登录 GitHub 后即可下载，产物包含 `patches.mpp`、校验报告、对应源码和上游许可文件。将补丁包手动导入 Morphe 后使用；这不是 APK，也不是上游签名的官方 Release。

自动构建使用 Actions 自带的令牌读取公开构建依赖，不需要个人密钥、GPG 私钥或发布权限。该工作流不会自动创建 Release。上游遗留的发布工作流和 `patches-bundle.json` 仅为保留 fork 历史，不是中文补丁下载入口。

本地构建需要 Java 21、Android SDK、Python 3.10+ 和具备 `read:packages` 权限的 GitHub 令牌（通过环境变量 `GITHUB_ACTOR`、`GITHUB_TOKEN` 提供，不要写入仓库）：

```sh
python build_localized.py
```

构建脚本会校验固定的源码提交、翻译覆盖率、重复键、格式占位符及每一处源码替换的精确匹配次数；上游结构改变时停止构建，不静默遗漏翻译。它还会运行翻译测试，保留英文资源，并生成可复现翻译修改的对应源码归档。

后续升级时先更新 `localization/source.json` 中的上游版本和提交，再检查新增文本、调整 `source-edits.json` 和测试预期，最后运行自动构建。不要只修改版本号就发布。

## 许可与署名

Piko 源码及其派生的翻译遵循 GPL-3.0，并保留上游 `NOTICE` 的署名要求。见 [localization/LICENSE](localization/LICENSE) 和 [NOTICE](NOTICE)。本分支新增翻译及构建集成由 Robert-Stackflow 维护，与上游官方发行版无隶属关系。

# Credits
- [morphe](https://github.com/MorpheApp) - patcher
- [revanced](https://github.com/ReVanced) - previous patcher
- [j-hc](https://github.com/j-hc) - Project is inspired by j-hc's revanced builder template.
