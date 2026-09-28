# 简记

完全本地的 Android 记账 App。最低 Android 10（API 29）。

## 功能

- 粘贴多日账单或输入一句话，解析日期、末尾金额、项目、收支类型和分类；确认页可编辑、删除和新增，再批量写入。
- 首页汇总；账单按日期分组，支持日、周、月、年、分类和关键词筛选，点击编辑、长按删除。
- 统计页可直接选择历史周、月、年，或用左右按钮逐期浏览；自定义日期仍可使用。日期标注趋势、彩色分类环图与排行、日均和本地计算的消费洞察均可查看，图表点按可跳到相应账单。
- 特殊支出可排除出日常统计；日、周、月预算；CSV 导出、JSON 备份和覆盖恢复。
- 中文 Material 3 界面、专属矢量应用图标、系统深色模式。数据只保存在本机 Room 数据库，不需要账号或网络权限。

## 项目结构

```text
app/src/main/java/com/ledger/app/
  Parser.kt             解析器、金额转换和分类规则
  Data.kt               Room 表、DAO、数据库和 Repository
  Backup.kt             JSON 备份、恢复校验与 CSV 导出
  LedgerViewModel.kt    StateFlow 状态与业务操作
  MainActivity.kt       导航与主题
  HomeScreen.kt         首页
  ConfirmScreen.kt      解析结果确认
  BillsScreen.kt        账单管理
  StatsScreen.kt        图表与洞察
  StatsPeriod.kt        历史周期选择与日期区间
  LedgerTheme.kt        统一明暗配色与分类颜色
  MeScreen.kt           预算与数据管理
app/src/test/java/com/ledger/app/TextExpenseParserTest.kt
app/schemas/             Room v1 schema
```

## 构建和安装

使用 Android Studio 打开项目，安装 JDK 17、Android SDK Platform 35 和 Build Tools 35，然后运行 `./gradlew :app:testDebugUnitTest :app:assembleDebug`。调试 APK 输出到 `app/build/outputs/apk/debug/app-debug.apk`。

用数据线连接已开启“USB 调试”的手机后，可执行 `adb install -r app/build/outputs/apk/debug/app-debug.apk`；也可以将 APK 复制到手机，在文件管理器中打开并允许安装此来源的应用。安装前请确认手机为 Android 10 或更新版本。

本仓库的 `.tools/` 只用于当前机器的命令行构建，不属于应用源码。`tools/bootstrap.py` 可在 Windows 上下载本地构建工具。首次通过 Android Studio 构建时无需运行它。

## 数据与限制

- 金额在数据库中按整数分保存；JSON 备份包含账单、分类、规则和预算。恢复与清空需要确认，恢复在数据库事务中完成。
- 规则解析适合常见中文流水，遇到复杂语句、多个金额或歧义日期时应在确认页核对。没有云端语义模型。
- 当前产物是调试签名 APK，可直接安装试用。长期更新或上架前应建立并妥善备份独立的发布签名密钥；丢失签名密钥无法覆盖安装旧版本。卸载应用会删除本机数据，请先导出 JSON。
- 当前环境无已连接的 Android 设备，因此未做真机安装与界面操作验证。
