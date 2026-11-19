# 开发

## 环境

- JDK：仓库按 Java 8 目标编译（`options.release = 8`），可用 JDK 17/25 运行 Gradle。
- Gradle：系统 `gradle`（本机 9.7.1），仓库不带 wrapper。

## 编译依赖来源（`build.gradle` 自动挑选）

1. `../MindustryX-main/work/core/build/libs/core-release.jar`（本机 MindustryX 构建产物，优先）
2. `../Mindustry-master/core/build/libs/core-release.jar`（上游构建产物）
3. JitPack `com.github.Anuken.MindustryJitpack:core:v154.3`（离线兜底）

Arc 来自 `../Arc/arc-core/build/libs/arc-core-1.0.jar`。

## 常用命令

```powershell
cd C:\Users\华硕\Documents\codex\LogicCommandBridge
.\gradlew.bat classes                 # 快速编译
.\gradlew.bat buildMod                # 生成 codex\构建\LogicCommandBridge\LogicCommandBridge-dev.jar（含 classes.dex）
.\gradlew.bat zipMod                  # 同上内容的 .zip（可选，发布时用）
.\gradlew.bat dist distZip            # 复制 jar / zip 到 dist\
.\gradlew.bat dexAndroid              # 只跑 D8
.\gradlew.bat clean
```

- `buildMod` 会自动执行 `checkBundles`（bundle 花括号校验）与 `dexAndroid`（D8）。
- D8 自动探测：`D8_PATH` → `ANDROID_SDK_ROOT/ANDROID_HOME/build-tools/*/d8.bat`
  → `../android-sdk-codex/build-tools/*/d8.bat` → `../../android-sdk-codex/...`
  → `Documents/commandlinetools-win-*/cmdline-tools/bin/d8.bat`；
  `--lib` 用同 SDK 下最新的 `platforms/*/android.jar`。
- 找不到 D8 时 `dexAndroid` 自动跳过（只影响 Android），桌面 jar 仍会生成。

安装到本地测试：把 `codex\构建\LogicCommandBridge\LogicCommandBridge-dev.jar`
复制到 `%APPDATA%\Mindustry\mods\`。

## 改代码入口

| 想改什么 | 去哪 |
| --- | --- |
| 指令类型（采矿/维修/装载） | `CommandCapture` 判定 + `Mlog` 新增生成方法 |
| 编队形状/间距 | `Formation` |
| 生成的指令条数/保持策略 | `Mlog`（`Cfg.hold`、`Cfg.holdWaitSec`、`Cfg.holdCycles`） |
| 端口放置规则 | `Ports.check` / `Ports.finishDrag`（拖框铺处理器） |
| 处理器数量上限 | `Cfg.maxProcessors` / `logiccmd-max-processors` |
| 部队切片/分工 | `LogicCommandFeature.execute`（`i % slices` 轮转）+ `buildGroups` |
| 多人匹配容差/预筛 | `Cfg.mpTolerancePercent` / `Cfg.mpPadTiles` + `Mlog.moveMatch` |
| 文案 | `src/main/resources/bundles/bundle*.properties` |

## 约束

- **设置项标签**必须写成 `setting.<prefKey>.name`，可选 `setting.<prefKey>.description`；
  Mindustry 的 `SettingsTable` 只查这两个键，直接写 `logiccmd-xxx = ...` 会显示原始 key。
- 滑条取值文本（如 `logiccmd.type.*`）由我们自己的 `StringProcessor` 读取，可以用普通键。
- bundle 值里的 `{}` 必须成对：`Core.bundle.format` 走 `MessageFormat`，
  一个 `{0]` 这样的笔误就会抛 `Unmatched braces in the pattern` 并把游戏崩掉。
  `gradle buildMod` 会先跑 `checkBundles` 拦住这种错误。
- 不要在 `InputProcessor` 里直接改处理器代码：只入队，统一在 `Trigger.update` 里写。
- 不要替换 `control.input`（会和 Neon RTS 编队、LockAttack 冲突）。
- 单机下原版会先发出这次控兵指令，`LogicCommandFeature` 在注入前用
  `CommandAI.clearCommands()` 清掉；否则 `CommandAI.isLogicControllable()` 为 false，
  `ucontrol` 会被静默忽略。
- 多人下用 `Call.setUnitStance(player, ids, UnitStance.stop, true)` 让服务端清指令，并吞掉
  这次右键（返回 true），否则服务端 `CommandAI` 同样会挡住 `ucontrol`。
  `Cfg.swallow` 只是“无条件吞掉”的开关。
- 失败时至少要让玩家看到原因（`LogicCommandFeature.fail`），不要静默失败。
- 多人生成器：`Mlog.moveMatch` / `Mlog.attackMatch` / `Mlog.moveAllOfType`；单机仍用
  `Mlog.moveExact` / `Mlog.attackExact`（注入 `lc_uN`）。
- 槽位记忆在 `UnitBook.slots`（unit id → slot），改向时应尽量沿用，避免队形重排。
- mlog 标签只用 `[a-zA-Z0-9_]`，变量统一 `lc_` 前缀。
- 处理器链接必须随代码一起写回（`compress(code, links)`）。
