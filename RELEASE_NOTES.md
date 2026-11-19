> [!NOTE]
> 需要 **Mindustry v157+**（桌面 / Android）或对应的 MindustryX；服务器端无需安装。
> Requires **Mindustry v157+** (desktop / Android) or a matching MindustryX. Servers do not need it.

## 中文

### 新增

- **拖框铺处理器**：按绑定键后按住左键拖出一个矩形，按处理器尺寸（微型 / 中型 / 大型）自动铺满，数量上限可调（默认 8，最大 16）
- **多处理器分工**：部队按 `i % N` 切片，每台处理器只负责自己那批单位，总吞吐为各处理器 `ipt` 之和
- **RTS → mlog**：右键移动 / 攻击实时编译为 mlog，覆写进处理器并立即执行，程序以 `stop` 收尾
- **途中改向 + 到达即释放**：移动中再右键立刻转向；指令结束时 `ucontrol unbind`，单位立刻可再次框选指挥
- **寻路 + 地形适配**：`ucontrol pathfind` 绕开地形；海军 / 陆军 / 空军分别夹取各自最近的可行走格，海军打岸上目标会开到最近水面开火
- **边走边打**：`uradar` 自动索敌最近的敌方单位并开火（可关闭；建筑需右键指定攻击）
- **到达形态**：挤在目标点 / 走成方阵
- **编队快捷键**：上一个 / 下一个编队（基于原版控制组），当前编队单位在地图上高亮
- **多人兼容**：单人注入真实单位常量；多人用位置匹配 + `flag` 标记复用，编队不变时跳过重新匹配
- **中英双语**，桌面与 Android 共用同一个 jar（内含 `classes.dex`）

### 安装

1. 下载 `LogicCommandBridge-1.0.0.jar`，放入 `mods/`（Windows：`%APPDATA%\Mindustry\mods`）
2. 设置 → 控制 → 绑定 `LCB: bind processor / command bridge`
3. 按住左键拖矩形自动铺处理器；框选单位后右键下令
4. 可选：绑定 `LCB: previous formation` / `LCB: next formation` 快速切换编队

### 已知限制

- 多人位置匹配依赖客户端同步坐标，容差默认 2.00 格（设置可调）
- 编队标记占用 `@flag` 的 `[1e9, 2e9)` 区间，若自己的逻辑也使用该区间需注意冲突
- 建筑不会被自动索敌，需要右键指定

## English

### Added

- **Drag-to-lay processor area**: press the bind hotkey, drag a rectangle, and processors (micro / medium / large) are laid out automatically, capped by a configurable limit (default 8, max 16)
- **Multi-processor dispatch**: units are sliced by `i % N`; each processor only drives its own slice, so total throughput is the sum of every processor's `ipt`
- **RTS → mlog**: right-click move / attack is compiled into mlog, written into the processors and executed immediately, ending with `stop`
- **Mid-flight redirection + instant release**: right-click again to turn immediately; `ucontrol unbind` on completion makes units RTS-commandable right away
- **Pathfinding + terrain clamping**: `ucontrol pathfind` routes around terrain; naval / ground / air units are clamped to their nearest reachable tile, so a navy group ordered onto land still shells from the nearest water
- **Attack-move**: `uradar` auto-acquires the nearest enemy unit while moving (can be disabled; buildings require an explicit attack order)
- **Arrival shape**: crowd the target point or keep a formation grid
- **Formation hotkeys**: previous / next formation based on vanilla control groups, with an on-map highlight of the active group
- **Multiplayer support**: exact unit constants in single player; position matching plus `flag` token reuse in multiplayer, skipping re-matching when the group is unchanged
- **Bilingual UI**, one jar for both desktop and Android (`classes.dex` included)

### Install

1. Download `LogicCommandBridge-1.0.0.jar` and drop it into `mods/` (`%APPDATA%\Mindustry\mods` on Windows)
2. Settings → Controls → bind `LCB: bind processor / command bridge`
3. Drag a rectangle to lay processors, select units, then right-click to command
4. Optional: bind `LCB: previous formation` / `LCB: next formation` to switch groups quickly

### Known limitations

- Multiplayer position matching relies on client-synced coordinates; default tolerance is 2.00 tiles (configurable)
- Formation tokens occupy the `[1e9, 2e9)` range of `@flag`; avoid that range in your own logic
- Buildings are not auto-targeted; right-click them to issue an explicit attack order
