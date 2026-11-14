# dev14 列表位置恢复候选：构建与静态验证

2026-09-15。未安装到手机，未做本版 ART 或交互验收，未创建 Release。设备仍保留此前 dev13。

设计与限制见 [位置恢复 v3](../../fixes/position-restoration-v3.md)。本版不是“所有情况下均能恢复”的承诺：目标未加载、删除、过滤、不可达偏移以及列表服务端的增量语义仍需实测。

## 精确来源

- 实际构建源码：`959c2e5c396835104ece82b1585b5f9f561347e0`。
- [Actions 34979451943](https://github.com/Robert-Stackflow/piko-newx/actions/runs/34979451943)：成功。
- X：12.22.0-prod.01；Piko：3.10.6 / bc03fce4352bcb7ee89299722d0e900af3fb8a82。
- 自定义版本：3.10.6-zh.3-media.dev14。
- MPP SHA256：`42d5a1c0cf80cc2549ea040c3f2cf6b666b07eaf343ab0c89febf91e1e35d835`。
- 对应源码 ZIP SHA256：`9976f3e2a489cd4347faa7ae3692f0944c5102db3adc1a7762ee19f9fb804c3d`。
- 最终 APK：`x-piko-newx-v12.22.0-prod.01-piko-v3.10.6-zh.3-media.dev14.apk`，176604534 字节。
- APK SHA256：`7c687dbae30328e2d2c867a009d9b068eb1151f1b88475432983ca5507f6873a`。

源码 ZIP 中七个时间线覆盖文件与实际推送源码逐一比较一致，MPP 校验值与 CI 报告一致。后续仅说明文档提交不代表另一次二进制构建。

## 已完成

- 36 项本地完整测试（Java 21 + 固定上游源码，无跳过），CI 再次测试成功。包括 564 项锚点状态断言、26 项运行时模拟断言；其他媒体/翻译/转推回归也通过。
- 固定 APK 上全部 37 项补丁 Applied，生成 Saved to，无跳过失败补丁。
- 423 条中文资源核验。
- 签名与旧包一致，16 KiB 对齐、包名/版本/ABI 元数据核验。
- 228 项原有最终 DEX 检查，66 项 v3 专项 DEX 检查。

专项检查确认：服务器指令消费者只替换了经过证明的一个 callback 调用；开启 List 保护时不进入原回顶，非 List/关闭时仍调用原回调。只有手动刷新协程的一个 policy 参数被替换，之后调用原生 Top cursor 查找。actual MeasureResult 的首项 index 与 offset 被直接读取，不再用请求位置冒充测量结果。

与 dev13 逐指令比较保持不变：数据合并处理器、视口缓存生产、原 Piko 自动刷新改写、刷新结果提示接收、原生滚动消费者、For You/Following policy 工厂、原生游标查找、原生 applyMeasureResult 和 requestScrollToItem。

## 过程中的拒绝产物

首次 CI 34978327558 虽编译成功，但 APK 应用在协程候选枚举时尝试读取平台 StringBuilder 类，被校验拒绝；没有安装或交付该产物。163e8fa 收紧为 APK 内可解析的 SuspendLambda 候选；随后 959c2e5 又将位置快照改为真实测量结果。只有上面列出的最终 CI/哈希是本次候选。

## 接下来需要受控真机验证

多个固定列表快速切换；实际新增内容后的手动刷新；原目标暂缺；主动回顶；运动中离开；重启；两个位置开关关闭的对照；For You/Latest/Ranked 对照。需核对实际条目和偏移、是否循环拉回、旧数据是否由服务端替换、是否出现 ANR/崩溃。

手机未接受本次覆盖安装，不能把静态通过表述为问题已全面解决。
