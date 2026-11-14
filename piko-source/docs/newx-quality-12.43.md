# X 12.43 播放质量重新适配

目标：`12.43.0-prod.01`，arm64。仅调整播放，不改变下载的流选择。

旧补丁依赖播放器回调中的日志和音轨正则；12.43 已把缺失音频码率的补全移到 HLS playlist parser。继续沿用旧回调指纹会失败，不应放宽成任意方法匹配。

新版通过 Media3 参数的 Bundle 序列化字段 ID 解析混淆后的字段，而非硬编码混淆类名/字段名：`9` 为视频码率上限，`j` 为音频码率上限，`l` 为最低码率开关，`m` 为最高受支持码率开关。定义对应 [Media3 TrackSelectionParameters 源码](https://github.com/androidx/media/blob/release/libraries/common/src/main/java/androidx/media3/common/TrackSelectionParameters.java)。解析必须唯一、完整且类型匹配，否则停止打包。

在参数构造器正常初始化之后、返回之前读取原有设置键 `newx.post_actions_media.force_highest_video_quality`。开启时解除两个码率上限，关闭强制最低码率并开启最高受支持码率选择；关闭时跳过全部覆盖，保留原生构造值。默认开启，修改后重启应用。保留设备解码、语言、音轨与手动选择等其他约束；不能生成服务端未提供的清晰度，也不保证网络不会缓冲。

注入使用扩展后的局部寄存器，禁止把旧寄存器总数当作新空闲局部寄存器起点，以免覆盖新的 `p0` / `p1`。`tools/QualityDexCheck.java` 针对冻结的 12.43 APK 检查实际字段值、关闭分支和对象寄存器安全；它含冻结目标类名，只是验收工具，不是升级指纹。

验证：107 项补丁单测、283 项扩展单测通过；4 项新增解析器测试覆盖混淆字段、Bundle/BaseBundle、缺失与重复键。与此前 51 项补丁一起加上恢复的图片分享和质量补丁，53 项全部应用成功、0 失败；最终 DEX 检查通过。

验收用未签名 APK SHA256：`37184779409EEBD945DC69F095A2C4A400C8351E20CF7CA3EAC85025AC235F16`。未安装此包，尚未完成真机播放、切换设置与实际请求码率的回归，不能将上述静态验证描述为实测最高画质。
