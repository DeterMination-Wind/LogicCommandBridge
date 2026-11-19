# 发布

## 版本与命名（本地开发默认）

按工作区约定，Mindustry mod 的默认任务是**本地开发构建**：

- `mod.json` / `mod.hjson`：`name = "LogicCommandBridge-dev"`、`version = "0.0.0"`。
- 产物：`codex\构建\LogicCommandBridge\LogicCommandBridge-dev.jar`（`gradle buildMod` 生成）。

## 正式发布时

1. 改 `build.gradle` 的 `version`，以及 `mod.json` / `mod.hjson` 的 `version` 与 `name`
   （去掉 `-dev` 后缀），重跑 `gradle buildMod dist`。
2. 产物结构：根目录含 `mod.json` 与 `mod.hjson`、`logiccmd/**.class`、`bundles/bundle*.properties`，
   以及 Android 用的 `classes.dex`（由 `dexAndroid` 生成并打进同一个 jar）。
3. 一个 jar 同时给桌面和 Android 用，不要额外发布 `*-android.jar` / `*-desktop.jar`。
4. GitHub Release 只允许一个 `.jar` 资产（Mindustry 安装器取第一个 `.jar`）。
5. 发布后查询 release assets 确认只有一个可安装 jar，且该 jar 内含 `classes.dex`。

## 可选：发到 QQ 群

```powershell
# 先看 CainBot 本地桥是否在线
curl.exe -s --connect-timeout 5 http://127.0.0.1:3186/codex/health
```

然后按 `cainbot-group-file-send` 技能里的 `scripts/send_group_file.ps1` 上传
`codex\构建\LogicCommandBridge\LogicCommandBridge-dev.jar`。
