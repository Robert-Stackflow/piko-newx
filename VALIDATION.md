# 3.10.6-zh.1 验证记录

验证时间：2026-09-08（Asia/Shanghai）。

## 来源与自动构建

- 上游：`crimera/piko-newx v3.10.6`。
- 实际 Piko 源码：`crimera/piko@bc03fce4352bcb7ee89299722d0e900af3fb8a82`。
- 汉化实现提交：`c472aa9c3ffce3931e878f2f7a3cfa43571dbafd`。
- [成功的完整构建](https://github.com/Robert-Stackflow/piko-newx/actions/runs/34141811933)：Java 21，Gradle 9.6.1，`buildAndroid`。
- 8 项翻译单元及源码集成测试通过；检查覆盖率、重复键、格式参数、英文回退资源、设置键不变、源码漂移及重复应用时安全失败。
- 356 条中文资源（NewX 354 条，共享资源 2 条），9 个 Java 文件的 94 处界面文本替换。
- 构建产物附对应的完整源码、汉化文件、GPL-3.0 许可和上游 NOTICE；不使用上游官方签名冒充官方发布。

该次 `patches.mpp`（1,941,954 字节）SHA-256：

```text
38225437409519eb56de290b54da9cab9891d1c2485837a95e049f5cbecac382
```

## 实际 APK 验证

使用本地保存、已核验原始签名的 X `12.22.0-prod.01` ARM64 + ARMv7 拆分包，合并输入 SHA-256：

```text
aaec52c96a991234dc35730cc84ef16678201a27e88352edb83b8e152353d301
```

Morphe Desktop 1.15.0 应用了 32 个默认 NewX 补丁，以及 Morphe patches 1.41.0 的 `Disable Play Store updates`。33 个补丁全部成功，所有构建步骤成功。

检查结果：

- 最终 APK 中全部 356 条 `zh-rCN` 资源逐项匹配翻译；英文回退资源保留。例：`Piko NewX 设置`、`下载并合并`、`已选择 %1$d 项，共 %2$d 项`。
- 包名 `com.twitter.android`，versionName `12.22.0-prod.01`，versionCode `2147483647`，最低 Android 9 / API 28，ARM64 + ARMv7。
- 最终 APK 签名验证通过，与此前本地安装包使用的证书一致。
- `zipalign -c -P 16 4` 检查通过。签名时必须使用 `--alignment-preserved true`，避免已对齐的原生库在重新签名后失去 16 KiB 对齐。
- 原有 APK 保留，没有覆盖旧版产物。此记录中的测试 APK 未上传到 GitHub。

最终测试 APK：`x-piko-newx-v12.22.0-prod.01-piko-v3.10.6-zh.1.apk`（176,563,574 字节），SHA-256：

```text
9360c2a3c5c99c53dac30d02284707f997710d7593bc74c627d6c15df46d7758
```

签名证书 SHA-256（这是构建模板中的公共签名，不是 X 官方签名）：

```text
637c226c67aec0cdbc6f49cd476d5247f999122606286273e16233a913a088b4
```

## 未验证项目

没有连接安卓设备，因此未做真机安装、实际界面排版、语言切换或联网功能测试。编译及资源校验通过不等于这些运行时行为已验证。服务端年龄认证、媒体接口可用性及账户策略不在本次汉化修改范围内。
