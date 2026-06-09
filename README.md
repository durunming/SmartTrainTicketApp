# SmartTrainTicketApp

基于 Kotlin + Jetpack Compose 的智能火车票购票 Android 应用，内置全国铁路网络模拟数据（60+ 条真实线路），支持站站查询、换乘方案、订单管理等功能。

## 功能概览

- **用户认证** — 登录 / 注册，使用 Firebase Realtime Database 存储账户信息
- **车票查询** — 站站查询、途经站点查询、车次号查询三种模式
- **换乘方案** — 自动计算中转路线，展示换乘站与换乘间隔
- **高级筛选** — 按发车/到达时间区间、车型（高铁/动车/普速）、直达/换乘、有无余票筛选
- **多维度排序** — 发车时间、到达时间、最低票价、余票最多
- **购票支付** — 支持二等座、一等座、无座，实时计价
- **订单管理** — 查看订单、完成支付、取消订单（自动恢复库存）
- **管理员面板** — 全局订单管理、车次数据管理、运营统计
- **个人中心** — 订单统计、修改密码、一键更新全国铁路数据

## 技术栈

| 类别 | 技术 |
|------|------|
| 语言 | Kotlin |
| UI 框架 | Jetpack Compose + Material 3 |
| 数据库 | Firebase Realtime Database |
| 构建工具 | Gradle (Kotlin DSL) |
| 最低 SDK | Android 7.0 (API 24) |
| 目标 SDK | Android 16 (API 36) |

## 环境要求

- **Android Studio** Ladybug (2024.2+) 或更高版本
- **JDK** 11 或更高
- **Gradle** 8.x（项目自带 Gradle Wrapper）
- **Firebase 项目** — 需要在 Firebase 控制台创建项目并启用 Realtime Database

## 快速开始

### 1. 克隆项目

```bash
git clone https://github.com/durunming/SmartTrainTicketApp.git
cd SmartTrainTicketApp
```

### 2. 配置 Firebase

1. 前往 [Firebase 控制台](https://console.firebase.google.com/) 创建项目
2. 添加 Android 应用，包名为 `com.example.ticket`
3. 下载 `google-services.json` 放入 `app/` 目录（已有占位文件需替换）
4. 在 Firebase 控制台启用 **Realtime Database**（测试模式下启动即可）

### 3. 构建运行

用 Android Studio 打开项目，Sync Gradle 后直接运行到模拟器或真机。

或使用命令行：

```bash
# Windows
gradlew assembleDebug

# macOS / Linux
./gradlew assembleDebug
```

### 4. 初始化数据

应用首次启动后：

1. 注册一个普通用户账号
2. 登录后进入「我的」→ 点击「更新全国铁路数据」
3. 系统将自动生成覆盖中国主要铁路线路的车次数据并上传至 Firebase

> 管理员账号：使用用户名 `admin` 注册，登录后底部导航会出现「管理」面板。

## 项目结构

```
ticket/
├── app/
│   ├── build.gradle.kts          # 应用构建配置
│   ├── google-services.json      # Firebase 配置文件
│   └── src/main/
│       ├── AndroidManifest.xml
│       └── java/com/example/ticket/
│           ├── MainActivity.kt           # 入口 Activity
│           ├── data/
│           │   ├── model/
│           │   │   └── TicketModels.kt   # 数据模型定义
│           │   ├── RailNetwork.kt        # 全国铁路线路数据
│           │   ├── TrainDataGenerator.kt # 车次数据生成器
│           │   ├── PriceCalculator.kt    # 票价计算引擎
│           │   └── TrainUploader.kt      # Firebase 上传服务
│           └── ui/
│               ├── TrainTicketApp.kt     # 应用根 Composable
│               ├── theme/                # Material 3 主题
│               ├── auth/AuthScreen.kt    # 登录注册页面
│               ├── main/MainScreen.kt    # 主页面（底部导航）
│               ├── purchase/             # 车票查询与购买
│               ├── orders/               # 订单管理
│               ├── profile/              # 个人中心
│               └── admin/                # 管理员面板
├── build.gradle.kts              # 项目级构建配置
├── gradle/libs.versions.toml     # 版本目录
└── settings.gradle.kts
```

## 票价计算规则

票价由以下因素综合决定：

```
票价 = 距离 × 基准单价(0.55元/km) × 车型系数 × 座位系数
```

| 车型 | 系数 | 时速 |
|------|------|------|
| G（高铁） | 1.00 | 350 km/h |
| D（动车） | 0.65 | 250 km/h |
| C（城际） | 0.55 | 160 km/h |
| Z（直达） | 0.45 | 120 km/h |
| T（特快） | 0.40 | 120 km/h |
| K（快速） | 0.35 | 120 km/h |

| 座位 | 系数 |
|------|------|
| 二等座 | 1.0 |
| 一等座 | 1.6 |
| 无座 | 0.8 |

最终票价四舍五入到最近的 5 元。

## 铁路网络覆盖

内置 60+ 条中国真实铁路线路，包括：

- **8 条国家主干线** — 京沪、京广、沿海、沪昆、徐兰、沪汉蓉等
- **12 条区域干线** — 哈大、济青、成渝、西成、贵广等
- **40+ 条城际线路** — 覆盖华北、东北、华东、华中、华南、西南、西北各区域

涵盖 100+ 个城市站点，站点间距离数据基于真实地理距离。

## 管理员功能

以 `admin` 用户名登录后可使用：

- **订单管理** — 查看全部用户订单，按状态筛选，删除订单
- **车次管理** — 搜索、编辑、删除车次数据，添加新车次
- **统计面板** — 总订单数、各状态分布、热门线路排行等运营数据

## 许可证

MIT License
