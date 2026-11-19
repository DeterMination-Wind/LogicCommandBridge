# AGENTS.md - LogicCommandBridge

## 定位

Mindustry 客户端模组：选定一台逻辑处理器后，把玩家的 RTS 控兵指令实时编译成 mlog 覆写进去并立即执行。
独立 mod，**不参与 Neon 聚合**，因此仓库里没有 `bekBundled` / `bekBuildSettings` 契约。

## 文件结构

```text
LogicCommandBridge/
|-- docs/                        # 六份文档（README/architecture/development/release/testing/glossary）
|-- src/main/java/logiccmd/      # 模组源码
|-- src/main/resources/bundles/  # bundle.properties + bundle_zh_CN.properties
|-- tools/MlogSyntaxCheck.java   # 离线 mlog 语法校验（不是 mod 代码）
|-- build.gradle
|-- mod.hjson
|-- mod.json
\-- README.md
```

## 构建与产物

```powershell
cd C:\Users\华硕\Documents\codex\LogicCommandBridge
gradle classes      # 编译
gradle buildMod     # codex\构建\LogicCommandBridge\LogicCommandBridge-dev.jar
gradle dist         # 再复制到 dist\
```

- 编译依赖自动优先使用 `../MindustryX-main/work/core/build/libs/core-release.jar`，
  其次 `../Mindustry-master/...`，最后 JitPack。Arc 来自 `../Arc/arc-core/build/libs/arc-core-1.0.jar`。
- 本地开发固定 `version = "0.0.0"`、`name = "LogicCommandBridge-dev"`；当前仓库为 v1.0.0
  发布身份（`LogicCommandBridge` / `1.0.0`），发布前先本地构建验证再打 tag。
- 测试安装位置：`%APPDATA%\Mindustry\mods\LogicCommandBridge-dev.jar`。

## 代码约束

- Java 8 语法（`--release 8`）：不用 `var` / 记录类 / 新 API。
- 用户可见文案一律走 bundle，不在 Java 里写中文/英文句子。
- 指令只入队，统一在 `Trigger.update` 里写处理器代码；`InputProcessor` 里不做重活。
- 不替换 `control.input`；`InputProcessor` 返回 `true` 只在选点模式、`Cfg.swallow`、以及多人模式下端口就绪时的控兵点击。
- mlog 变量统一 `lc_` 前缀，标签只用字母数字下划线；程序最后一条必须是 `stop`。
- 覆写代码必须带 `links`（`LogicBlock.compress(code, build.links)`）。
- headless 下 `init()` 直接返回，不得触碰 `Core.input` / `ui`。

## 验证

1. `gradle buildMod`（自带 `checkBundles` + `dexAndroid`）。
2. `java -cp "<core-release.jar>;<arc-core.jar>;build\classes\java\main" tools\MlogSyntaxCheck.java`
   → 全部 `[ok] ... invalid=0`。
3. `java "-Dfile.encoding=UTF-8" -cp "<arc-core.jar>" tools\BundleFormatCheck.java` → 无 FAIL。
4. `jar tf` 确认产物含 `mod.json` / `mod.hjson` / `logiccmd/*.class` / `bundles/*.properties` / `classes.dex`。
5. 按 `docs/testing.md` 的游戏内清单手动过一遍（至少：拖框铺处理器、移动、连续右键、途中改向、
   海军打岸上目标、到达后可立即再指挥、攻击、禁用处理器）。

## bundle 文案约束

`Core.bundle.format` 走 `MessageFormat`：值里的 `{` 必须成对（`{0}`）。
一个 `{0]` 之类的笔误会抛 `Unmatched braces in the pattern` 并直接崩游戏，
`checkBundles` 会在构建期拦住它，不要绕过。

设置项标签必须用 `setting.<prefKey>.name`（可选 `.description`）；直接写 pref key 会显示原始键名。

## 提交与文档

- 提交信息前缀 `LogicCommandBridge: ...`，或 `feat:` / `fix:` / `docs:` / `build:`。
- 行为或构建变化时同步更新 `docs/` 与 `README.md` 中对应的事实描述。
- 不要提交 `build/`、`.gradle/`、`dist/`。
