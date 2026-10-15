# ClassFlow 小米手环 10 / AstroBox 快应用小组件

基于 **Xiaomi Vela OS (HyperOS for Wearables)** 与 QuickApp 快应用架构构建的小米手环 9 / 10 离线课程表小程序。参考并对齐社区知名的 **AstroBox（星盒）Nexio 课程表** 交互与视觉规范。

---

## 核心设计与特性

1. **下节课高能英雄卡片 (Hero Card)**：
   - 居顶呈现当前教学周次与实时时间；
   - 动态状态提示（`上课中 🟢`、`⏱ 15分钟后`、`已结课 ☕`）；
   - 大字课程名称 + 醒目的教室定位 `📍 东上101` + 上课节次与精确起止时间。
2. **今日课程流 (Today Flow)**：
   - 滑动查看今日所有安排，左侧条状色块同步主 App 课程主题色。
3. **全周课表多日离线翻页 (Week Pager)**：
   - 顶部单字星期胶囊（`一 二 三 四 五 六 日`），点击瞬间切换任意日期的课程。
4. **脱机离线存储 (Offline Cache)**：
   - 使用 `@system.storage` 将课表完整缓存在手环本地闪存中，出门无需携带手机亦可随时腕上查课。

---

## 目录结构

```text
apps/band-xiaomi/
├── manifest.json       # 快应用元数据声明与权限配置
├── app.js              # 全局生命周期与存储同步
├── pages/
│   └── index/
│       └── index.ux    # Nexio 风格 AMOLED 192x490 课表面板
└── README.md           # 本文说明
```

---

## 安装与同步方式

### 方式一：通过 AstroBox（星盒）一键安装与导入（推荐）
1. 在 Android 手机上打开 ClassFlow，进入「我的」->「小米手环 10 联动」；
2. 点击「立即推送到手环」，ClassFlow 会自动将本周课程序列化为 `classflow_band_schedule.json`；
3. 打开 AstroBox App，选择安装或直接将本快应用包同步至小米手环 10；
4. 手环上打开「ClassFlow 课程表」，即刻脱机查看最新课表！

### 方式二：使用 Xiaomi 快应用开发者工具编译
```bash
npm install -g hap-toolkit
hap build --release
```
编译产物 `.rpk` 可通过小米运动健康（开发者模式）或 AstroBox 直推安装。
