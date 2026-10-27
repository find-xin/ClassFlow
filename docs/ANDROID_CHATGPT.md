# Android 独立 ChatGPT 连接（2026-10-23）

本实现由 Kotlin 执行器直接读取、原子更新本 Android 设备的 CourseDataManager JSON 数据；Mac 可以关机。UI 继续使用原生 Compose，不使用 WebView。连接是可选的，离线课程管理不依赖它。

## 配置

1. 在 OpenAI Platform 创建**本 Android 设备专用** Secure MCP Tunnel，准备具有 Tunnels Read + Use 的运行密钥及对应工作区权限。不要与 Mac 共用 Tunnel ID，否则请求可能被不同设备竞争领取。
2. Android ClassFlow → 偏好设置 → AI / ChatGPT，输入 Tunnel ID、运行密钥，点击“保存并连接”。密钥使用 Android KeyStore 支持的 EncryptedSharedPreferences；不从 Mac 导入，不进入业务 JSON、BYOC 或 `.classflow` 导出。Android 系统自动备份已禁用，避免搬运不可跨设备解密的密钥。
3. ChatGPT 添加自定义 MCP，Connection 选择 Tunnel，并选择此设备隧道。刷新工具发现，新建聊天。
4. 实际调用 get_status，确认 platform=android、executor=this_device，随后 query_timetable。云端轮询正常仅代表传输可用；实际工具返回才是云端验收。
5. 停止连接可在设置页或常驻通知操作。默认不自动启用，不开机自启。网络、系统休眠、厂商后台策略或强制停止会影响可达性，不承诺全天候在线。

工具参数与结果进入所连接的聊天；没有额外的端到端加密声明。

## 四项工具及边界

- get_status：Android 执行器及实体数量。
- get_editing_context：学期、节次、课程、日程、待办、异常分页，最多1000项/页。
- query_timetable：指定学期/周次/星期，复用 Android 原生投影。
- batch_edit_items：1–1000项课程/日程 create/update/delete，完整系列作用域。字段以打包的 android_mcp_tools.json 为准；changes 使用 Android 模型字段名，如 semesterId、startDate、description。课程 schedules 为完整替换。没有待办写入、专注运行控制或单次/未来实例编辑能力，不等同于 Mac 的28项工具。

batch_id 与完整请求内容绑定；重试保持原ID及内容，新请求使用新ID。dry_run不落库；从预检改为正式提交需使用新ID。所有操作先在分离快照中校验，一项失败不提交；业务数据、墓碑和回执在同一次 SharedPreferences commit 中写入，失败报错并尝试恢复原快照，不报告成功。回执最多10000批，满后拒绝新写入以保留旧请求的幂等性。凭据和回执不进入显式业务备份或BYOC。

执行器在主线程串行执行；完整同步和强制拉取期间拒绝自动化，避免旧同步快照覆盖已确认写入。已打开主界面根据回执变更重新加载。常规持久化不再按标题/时间隐式合并不同UUID；显式导入合并函数仍保留。后台专注、提醒及微件硬件送达不属于此连接的验收范围。

## 传输与生命周期

实现依据官方公开的 [隧道 wire protocol](https://github.com/openai/tunnel-client/blob/main/docs/protocol.md)，通过固定 api.openai.com HTTPS 出站轮询/响应；不监听公网端口，不关闭TLS校验，不跟随重定向，不记录请求或原始错误正文。分片token只进入响应HTTP头。没有宣称或仿冒官方客户端的未实现能力。

前台服务使用 Android [specialUse](https://developer.android.com/develop/background-work/services/fgs/service-types#special-use) 类型，明确声明用户启用的私有 MCP 长连接用途并提供停止操作。Google Play 分发时仍需平台审核该用途；本地APK构建不代表商店审核通过。

[UNVERIFIED] 当前无已连接Android真机或模拟器；本机编译、隔离单元测试不能替代11英寸联想设备布局、后台存活、密钥持久化或真实ChatGPT调用验收。Mac已有连接的成功也不能代替Android验收。
