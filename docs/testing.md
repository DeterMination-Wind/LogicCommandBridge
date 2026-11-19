# 测试

## 1. 离线检查（不需要开游戏）

```powershell
cd C:\Users\华硕\Documents\codex\LogicCommandBridge
gradle checkBundles     # bundle 花括号校验（buildMod 会自动跑）
gradle checkI18n        # 各语言 bundle 的 key 必须与 bundle.properties 一致
gradle classes

$core = "..\MindustryX-main\work\core\build\libs\core-release.jar"
$arc  = "..\Arc\arc-core\build\libs\arc-core-1.0.jar"
java -cp "$core;$arc;build\classes\java\main" tools\MlogSyntaxCheck.java
java "-Dfile.encoding=UTF-8" -cp "$arc" tools\BundleFormatCheck.java
java "-Dfile.encoding=UTF-8" -cp "$arc" tools\BundleLocaleCheck.java
```

- `MlogSyntaxCheck` 用 Mindustry 自己的 `LParser` 解析生成的代码（含 jump 标签）。
- `BundleFormatCheck` 用 arc 的 `TextFormatter` 逐个校验 bundle 值，能捕获
  `Unmatched braces in the pattern` 这类会在游戏里崩溃的文案错误。
- `BundleLocaleCheck` 按 Mindustry 的加载方式（`I18NBundle.createBundle`）读出 zh_CN 文案，
  用来确认中文确实能解析出来。
- 期望：10 个 `[ok] ... invalid=0`；两个 bundle “checked N value(s)” 且没有 FAIL。

## 2. 产物检查

```powershell
& "C:\Program Files\Java\jdk-17\bin\jar.exe" tf "..\构建\LogicCommandBridge\LogicCommandBridge-dev.jar"
```

必须同时看到：`mod.json`、`mod.hjson`、`logiccmd/*.class`、`bundles/*.properties`、`classes.dex`。

## 2. 游戏内手工验收

准备：单机沙盒地图，解锁微型处理器，给自己刷 3~5 个单位（如 dagger）。

| 场景 | 期望 |
| --- | --- |
| 按绑定键 → 拖一个 3×2 格矩形 | 自动铺出多台处理器（受 `logiccmd-max-processors` 限制），HUD 提示“绑定 N 台、跳过 M 台” |
| 拖到水上/已有建筑上 | 不可用的格子被跳过；全不可用 → 报错不发码 |
| 超出建造范围拖框 | 超出部分被跳过，至少留下范围内的处理器 |
| 未框选直接右键 | 提示先框选单位 |
| 框选 12 个单位 → 右键地面 | 每台处理器分到 1/N 单位，整体移动到目标点，编队仍是一整块 |
| 连续右键 5 个不同位置 | 每次都生效（编队自动沿用；多人下走标记复用） |
| 右键敌方建筑 | 单位贴近并开火；目标没了程序停止 |
| 移动途中遇到敌方单位 | 自动索敌最近的敌方单位并开火（`uradar`）；没有目标时不开火 |
| 移动途中遇到敌方建筑 | 不会自动索敌（右键该建筑下达攻击指令才会打） |
| 关闭“移动时自动攻击” | 移动途中不再开火，只执行移动 |
| 按“解除编队逻辑控制” | 当前编队立刻变回普通单位（可框选、AI 恢复），HUD 显示解除数量 |
| 处理器类型选“世界处理器” | 编辑器/开启 allowEditWorldProcessors 时可铺；否则红框并提示原因 |
| 地图开启 disableWorldProcessors | 世界处理器直接报错，不写码 |
| 打开处理器 UI 手动看一眼 | 代码可读、链接未丢失、不会自循环 |
| 把处理器 `setenabled 0`（可用另一台处理器执行） | 提示“处理器已被禁用”，不写码 |
| 保存 → 读档 | 端口标记与处理器代码仍在 |
| 按“释放编队” | 清空编队，下一次右键提示先框选 |

## 3. 多人

前置：自建服务器（客户端装模组即可，服务器不必装），`logicUnitControl` 保持开启。

| 场景 | 期望 |
| --- | --- |
| 框选 3 个单位右键地面 | 只有这 3 个动（同类型第 4 个不动）；HUD 显示指令条数；提示首令延迟 |
| 移动途中右键另一个点 | 立刻改向；不会先走到旧点；队形不整队重排；第二条指令起走“标记复用”路径，明显更快 |
| 连续右键 5 次 | 每次都生效，编队自动沿用 |
| 单位到达目的地后 | **立刻**可以再次框选/指挥（程序结束时 `ucontrol unbind`），不用等 10 秒 |
| 切换到达形态设置（挤在一起 / 方阵） | 挤：全部落在夹取点；方阵：按间距铺开，遇地形自动收缩 |
| 按“上一个/下一个编队” | 在原版控制组之间循环，HUD 显示“编队 N：X 个单位”，地图上高亮这些单位 |
| 海军右键岸上地面 | 开到最近的水面，不会原地卡住；攻击时同样先到最近水面再开火 |
| 右键到墙上/水里（陆军） | 自动夹到最近可站立格，编队间距自动收缩 |
| 移动经过悬崖/建筑 | `pathfind` 绕行（设置里可关掉改成直线） |
| 右键敌方建筑 | 单位贴近并开火；目标消失后靠轮次超时停机 |
| 处理器换成中型/大型 | 首令延迟明显下降（微型 2 条/刻、中型 8、大型 25） |
| 检查单位 `@flag` | 默认指令结束后清理；关闭 `logiccmd-mp-flag-cleanup` 后会留标记 |
| 高延迟（>150ms） | 若匹配失败，调大 `logiccmd-mp-tolerance`（默认 2.00 格） |

- 被接管单位退控后（约 10 秒）可再次框选。
- 多人下框选是“精确位置匹配”，不再是类型+半径近似。

## 4. 回归点

- 与 Neon（RTS 编队、LogicSugar）、LockAttack 同时启用：右键原版指令仍生效（`swallow` 默认关）。
- 单位在保持循环中死亡：程序在超时轮次后停止，不会卡死。
