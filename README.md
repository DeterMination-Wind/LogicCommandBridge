# Logic Command Bridge / 逻辑指挥桥

Mindustry 客户端模组：把玩家框选后的 RTS 控兵指令实时编译成 mlog，覆写进指定处理器并立即执行。

- 先按快捷键点选一个格子，模组会在那里放置（或复用）一台处理器作为“端口”。
- 之后每次右键控兵：移动 / 攻击 都会被翻译成 mlog，程序以 `stop` 收尾。
- 默认“保持到抵达”：循环下发 `ucontrol`，避免 10 秒 LogicAI 超时导致长途移动半路停下。
- 单位被逻辑接管后无法再框选，因此模组自己维护编队；空框选时复用上一批单位。
- 移动途中再右键，会**整段覆写并重启处理器**，单位立刻改向；同一编队的后续指令走
  “标记复用”路径（多人下不再重新匹配）。
- **到达即释放**：指令结束时 `ucontrol unbind`，单位立刻可被 RTS 操控。
- **寻路 + 地形适配**：`ucontrol pathfind` 绕地形；海军/陆军/空军分别夹到最近可站立点，
  海军打岸上目标也能开到最近水面开火。
- **两种到达形态**：挤在目标点，或走成方阵（设置里切换）。
- **边走边打**：移动时用 `uradar` 自动索敌最近的敌方单位并开火（建筑不会自动索敌，需右键指定）。
- **编队切换快捷键**：上一个/下一个编队，切换后地图上高亮当前编队单位。

## 构建

```powershell
cd C:\Users\华硕\Documents\codex\LogicCommandBridge
gradle classes        # 仅编译
gradle buildMod       # 生成 codex\构建\LogicCommandBridge\LogicCommandBridge-dev.jar（含 classes.dex）
gradle dist           # 额外复制到 dist\
```

开发构建固定为 `version = "0.0.0"`、`name = "LogicCommandBridge-dev"`。
`buildMod` 会自动跑 bundle 花括号校验与 D8，一个 jar 同时支持桌面与 Android。

## 使用

1. 设置 → 控制 → 绑定 `LCB: bind processor / command bridge`（默认未绑定）。
2. 按该键 → **按住左键拖出一个矩形** → 模组自动在矩形内铺处理器（微型/中型/大型，数量上限见设置）→ 松开完成。
3. 框选可指挥单位 → 右键地面 = 移动到该点（带编队落点）；右键敌方建筑/单位 = 攻击。
4. 再次右键可继续下令（编队会自动沿用，直到你重新框选或按“释放编队”）。
5. 用“上一个编队 / 下一个编队”快捷键在原版控制组之间切换；当前编队的单位会被高亮圈出。

多台处理器会**分摊部队**：每个处理器只负责自己那一份单位，总吞吐 = 各处理器 `ipt` 之和。

## 文档

- [docs/README.md](docs/README.md)
- [docs/architecture.md](docs/architecture.md)
- [docs/development.md](docs/development.md)
- [docs/release.md](docs/release.md)
- [docs/testing.md](docs/testing.md)
- [docs/glossary.md](docs/glossary.md)
