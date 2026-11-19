# Logic Command Bridge / 逻辑指挥桥 (Mindustry Mod)

<h1 align="center">
  <a href="https://github.com/DeterMination-Wind/LogicCommandBridge/releases/latest"><img src="https://img.shields.io/github/v/release/DeterMination-Wind/LogicCommandBridge?display_name=release&label=Latest%20Release&color=green"></a>
  <a href="https://github.com/DeterMination-Wind/LogicCommandBridge/releases"><img src="https://img.shields.io/github/downloads/DeterMination-Wind/LogicCommandBridge/total?label=Downloads&color=blue"></a>
  <a href="LICENSE"><img src="https://img.shields.io/github/license/DeterMination-Wind/LogicCommandBridge?label=License"></a>
  <a href="https://github.com/DeterMination-Wind/LogicCommandBridge"><img src="https://img.shields.io/github/stars/DeterMination-Wind/LogicCommandBridge?style=flat&label=Star%20this%20mod!&color=yellow"></a>
</h1>

> [!NOTE]
> 有问题或建议请发到 bugsoc@qq.com / If you have any feedback, please send it to bugsoc@qq.com

- [中文](#中文)
- [English](#english)

## 中文

> 把 RTS 控兵变成逻辑：拖框铺处理器，右键即下令。

### Logic Command Bridge 是什么

Logic Command Bridge 是 Mindustry 的客户端模组：先拖框让模组自动铺一组处理器，之后每一次
RTS 控兵指令都会被实时编译成 mlog，覆写进这些处理器并立即执行。

它解决的是原版一个很具体的空白：单位一旦被逻辑接管，就不能再被框选和指挥。这个模组把
“框选 → 右键”重新接到逻辑上，并且支持把部队切片、分摊到多台处理器并行执行。

它不新增方块、单位或资源，只是把玩家已有的处理器变成可指挥的“逻辑指挥部”。

### 为什么使用它

- **逻辑单位重新可控**：被处理器接管的单位也能用框选 + 右键指挥，不用再改代码。
- **多处理器分工**：拖框自动铺满处理器，部队按 `i % N` 切片，总吞吐是各处理器 `ipt` 之和。
- **命令即时生效**：移动途中再右键立刻转向；到达后 `uncontrol unbind` 立即恢复可指挥。
- **地形与兵种适配**：`pathfind` 绕开地形，海军 / 陆军 / 空军分别夹到最近可站立点，
  海军打岸上目标会开到最近水面开火。
- **边走边打**：移动中自动索敌最近的敌方单位并开火（建筑需右键指定）。
- **适合 RTS 习惯**：到达形态可选“挤在一起 / 方阵”，编队可用快捷键切换并高亮。

### 快速开始

1. 从 [Releases](https://github.com/DeterMination-Wind/LogicCommandBridge/releases) 下载
   最新的 `LogicCommandBridge-<version>.jar`（或同内容的 `.zip`），放进 Mindustry 的 `mods` 目录并启用。
2. 在 `设置 → 控制` 绑定 `LCB: bind processor / command bridge`。
3. 按该键，按住左键拖出一个矩形：模组会按处理器尺寸自动铺满。
4. 框选单位 → 右键地面移动、右键敌方目标攻击。
5. 可选：绑定 `LCB: previous formation` / `LCB: next formation` 快速切换编队。
6. 一键解绑：绑定 `LCB: release formation from logic control`，按下后当前编队立刻脱离逻辑控制。

### 兼容性

- 需要 Mindustry v157 或更高版本；MindustryX 可用。
- 纯客户端模组，服务器不需要安装；多人下服务器需允许逻辑单位控制（`logicUnitControl`）。
- 单人使用精确的单位寻址（注入单位常量）；多人使用位置匹配 + `flag` 标记复用。
- 安卓请使用 Release 里的 `LogicCommandBridge-<version>.jar`（内含 `classes.dex`），
  不要使用只含桌面 class 的中间构建。

### 功能说明

完整功能、设置项和术语见 [docs/](docs/README.md)；发布说明见 [RELEASE_NOTES.md](RELEASE_NOTES.md)。

### 反馈

- 邮箱：bugsoc@qq.com

### 开发者构建

在项目目录执行：

```powershell
.\gradlew.bat clean buildMod zipMod dist distZip
```

主要产物：

- `dist/LogicCommandBridge-<version>.jar`
- `dist/LogicCommandBridge-<version>.zip`
- `../构建/LogicCommandBridge/LogicCommandBridge-<version>.jar`
- `../构建/LogicCommandBridge/LogicCommandBridge-<version>.zip`

## English

> Turn RTS commands into logic: drag a processor area, right-click to command.

### What is Logic Command Bridge?

Logic Command Bridge is a client-side Mindustry mod. Drag a rectangle and it lays out a group of
processors for you; from then on every RTS unit command is compiled into mlog, written into those
processors and executed immediately.

It fills one specific gap in vanilla: once a unit is taken over by logic, it can no longer be
box-selected or commanded. This mod reconnects "drag-select → right-click" to logic, and can split
the army across multiple processors for parallel execution.

It adds no blocks, units or resources; it turns the processors you already build into a
commandable logic headquarters.

### Why use it?

- **Logic units become commandable again**: box-select and right-click units that a processor took
  over, without editing any code.
- **Multi-processor dispatch**: a drag lays out processors automatically, units are sliced by
  `i % N`, and total throughput is the sum of every processor's `ipt`.
- **Commands apply immediately**: right-click again mid-move to turn instantly; `ucontrol unbind`
  on arrival restores RTS control right away.
- **Terrain and unit-type aware**: `pathfind` routes around terrain, and naval / ground / air units
  are clamped to their nearest reachable tile — a navy group ordered onto land shells from the
  nearest water instead of standing still.
- **Attack-move**: while moving, units auto-acquire the nearest enemy unit (buildings need an
  explicit attack order).
- **Built for RTS habits**: choose "crowd the target" or "keep a grid", and switch formations with
  hotkeys while the active group is highlighted.

### Quick start

1. Download the latest `LogicCommandBridge-<version>.jar` (or the identical `.zip`) from
   [Releases](https://github.com/DeterMination-Wind/LogicCommandBridge/releases), put it into
   Mindustry's `mods` folder and enable it.
2. Bind `LCB: bind processor / command bridge` in `Settings → Controls`.
3. Press it, then hold left mouse and drag a rectangle: processors are laid out automatically.
4. Select units → right-click ground to move, right-click an enemy target to attack.
5. Optional: bind `LCB: previous formation` / `LCB: next formation` to switch groups quickly.
6. One-key release: bind `LCB: release formation from logic control` to drop the current group out of
   logic control immediately.

### Compatibility

- Requires Mindustry v157 or later; works with MindustryX.
- Fully client-side; servers do not need it. Multiplayer servers must allow logic unit control
  (`logicUnitControl`).
- Single player uses exact unit addressing (injected unit constants); multiplayer uses position
  matching plus `flag` token reuse.
- On Android, use the release `LogicCommandBridge-<version>.jar` that contains `classes.dex`, not a
  desktop-only or intermediate build.

### Feature details

See [docs/](docs/README.md) for the full feature guide, settings and terminology; release notes are
in [RELEASE_NOTES.md](RELEASE_NOTES.md).

### Feedback

- Email: bugsoc@qq.com

### Development

From the project directory:

```powershell
.\gradlew.bat clean buildMod zipMod dist distZip
```

Main outputs:

- `dist/LogicCommandBridge-<version>.jar`
- `dist/LogicCommandBridge-<version>.zip`
- `../构建/LogicCommandBridge/LogicCommandBridge-<version>.jar`
- `../构建/LogicCommandBridge/LogicCommandBridge-<version>.zip`
