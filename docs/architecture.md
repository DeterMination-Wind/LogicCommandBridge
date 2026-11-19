# 架构

## 一句话

玩家的一次右键控兵 = 一次“编译 + 覆写 + 重启处理器”的操作。

## 模块

| 文件 | 职责 |
| --- | --- |
| `LogicCommandMod` | `Mod.init()`：加载设置、注册 KeyBind、装 `InputProcessor`、挂 `Trigger.update/draw`、加设置分类。headless 直接返回。 |
| `Cfg` | 设置项 key、热键对象、处理器方块选择、设置页构建。 |
| `Ports` | 端口状态机：选点模式、放置校验、自动建造、`LogicBuild` 解析、悬停格。 |
| `UnitBook` | 编队（`Seq<Unit>`）：从原版框选同步、剔除失效、按处理器绑定、MP 下统计同类型单位数量。 |
| `Formation` | 把点击点展开为编队槽位，并按“到槽位距离”贪心匹配单位。 |
| `CommandCapture` | `InputProcessor`（index 0）：先于原版抓右键，算出目标点与攻击目标，入队。 |
| `LogicCommandFeature` | 运行时主循环：热键、悬停、编队维护、请求队列、编写代码、绘制足迹、提示。 |
| `Mlog` | 指令 → mlog 文本（单机精确 / 多人近似）。 |
| `Injector` | 写码与重启：单机 `updateCode(..., assemble)` 注入常量；多人 `configure(compress(code, links))`。 |

## 数据流

```
右键 (InputProcessor.touchDown)
  → 计算 target = Core.input.mouseWorld(x, y)
  → attack = world.buildWorld(target) 若为敌方建筑；否则 input.selectedEnemyUnit(target)
  → 入队 Request(target, attack, input.selectedUnits)
Trigger.update
  → 端口检查（存在 / 建造中 / accessible / enabled / rules.logicUnitControl）
  → 编队：有框选就覆盖，没框选就沿用
  → Formation.assign(...)
  → Mlog.moveExact / attackExact（单机） 或 moveMatch / moveFlagOnly（net.active()）
  → Injector.apply(...)
```

## mlog 策略

### 单机精确（默认）

`LogicBuild.updateCode(code, false, asm -> asm.putConst("lc_uN", unit))` 把真实单位对象注入成
常量，程序用 `ubind lc_uN` + `ucontrol` 寻址，每个单位 2 条指令。

- 变量：`lc_u0..lc_uN`（单位）、`lc_t`（攻击目标）、`lc_b/lc_c/lc_n/lc_i/lc_tm/lc_h`（运行时）。
- 标签：`lc_hold` / `lc_sN` / `lc_done` / `lc_swN` / `lc_nxN` / `lc_enN`，只用字母数字下划线。
- `stop` 必须是最后一条：`LExecutor.runOnce()` 在越过末尾时会回到 0，不写 `stop` 就是死循环。

### 多人（位置匹配 + 标记复用）

mlog 无法引用单位 id，客户端也写不了服务端 `flag`（`flag` 没有 `@SyncField`，不在单位同步里），所以：

1. **第一次给某个编队下令**（换选/换端口后）：
   - 先按 token 全域 `[1e9, 2e9)` 清理旧标记（`ucontrol flag 0`）；
   - 逐类型 `ubind @type` 扫一遍，用 `sensor @x/@y` 读位置、算平方距离匹配记录坐标
     （**不用 `ucontrol within`**：它会把每个被检查到的单位都变成 LogicAI，误伤未选中的同类单位）；
   - 命中就 `ucontrol flag <token+槽位>` + 下发 `ucontrol approach <该槽位落点>`。
2. **同一编队再次下令**（改向、连续点击）：跳过匹配，直接按 `@flag` 过滤 + 槽位算术下发
   （`op idiv` / `op mod` / `op mul` / `op add`，落点现算），首条指令立即生效。
3. **快路径**：若编队恰好等于该类型全部单位，跳过匹配，直接 `ubind` + `ucontrol` 连发
   （2 条指令/单位）。
4. **清指令**：下发前调用 `Call.setUnitStance(player, ids, UnitStance.stop, true)`，让服务端
   清掉 `CommandAI` 的旧指令；否则 `CommandAI.isLogicControllable()` 为 false，`ucontrol`
   会被静默忽略（单机则直接 `CommandAI.clearCommands()`）。

槽位号编码在 flag 里（`token + slot`），所以保持循环不需要记住“哪个单位在哪个格子”，
单位跑动、被改向都不会失配。位置容差、预筛外扩、标记复用/清理都在设置里（`logiccmd-mp-*`）。
预估首令延迟会按处理器 `ipt` 显示在提示里（微型 2 条/刻、中型 8、大型 25）。

### 程序结构：order pass / hold / release

1. **order pass**（最短路径）：每条单位 1~2 条指令（单机 `ubind` + `ucontrol pathfind`），
   保证“右键→全部单位转向”的延迟最小；
2. **hold**：每 `holdWait` 秒重下发一次（刷新 10 秒 LogicAI 计时），同时统计抵达数量；
3. **release**：抵达或超时后对每单位 `ucontrol unbind`，单位**立刻**回到可被 RTS 框选/
   指挥的状态，不用再等 10 秒超时。

### 地形适配（海军/陆军/空军）

`Formation.plan` 按兵种分组，每组用 `Terrain.nearest(type, click)` 把目标点夹到该兵种
**实际能站的最近格子**（海军→水面、陆军→陆地、空军→任意），再在该点周围铺编队网格：

- 网格槽位逐个校验可站立性；不满足就按 0.6 倍收缩间距，最后退化为“全部到夹取点”；
- 移动用 `ucontrol pathfind`（地面单位走 `controlPath` 绕地形；飞行单位自动退化为直线）；
- 攻击命令先 `pathfind` 到夹取点，再 `ucontrol target` 瞄准——这样海军不会因为目标在岸上
  就原地不动，而是开到最近的水面开火。

`logiccmd-pathfind` 可切回旧的 `ucontrol approach`（纯直线）。

### 边走边打

`logiccmd-attack-move`（默认开）在移动的保持循环里给每个单位插入：

```mlog
uradar enemy any any distance @unit 1 lc_en
jump none equal lc_en null
ucontrol targetp lc_en 1
jump done always
none:
ucontrol target 0 0 0
done:
```

- 以**单位自身**为雷达源，所以射程自动匹配；命中就开火，没目标就 `shoot=0`；
- `uradar` 只能找**单位**，没有通用的“最近敌方建筑”查询，所以建筑需要右键指定；
- 移动指令的 order pass 会先 `ucontrol target 0 0 0`，避免上一条攻击指令残留的瞄准点继续开火。

### 到达形态

`logiccmd-formation-crowd`（默认关）：

- 开：所有单位落点 = 夹取点，到达后挤成一团；
- 关：按 `Formation` 的方阵铺开，遇地形自动收缩间距。

### 编队切换与高亮

- `logiccmd-prev-group` / `logiccmd-next-group`：在原版 `InputHandler.controlGroups` 之间循环，
  只跳过空组；
- 切换后会把该组的单位装进 `UnitBook`，可指挥的那部分同时镜像到 `input.selectedUnits`；
- `Trigger.draw` 里给 `UnitBook.units` 画高亮圈（青色脉冲圈），换图/读档时自动清空。

### 多处理器分工

绑定端口现在是“区域”而不是单格：拖一个矩形，模组按处理器尺寸（2/3/4 格）自动铺满，
数量上限 `logiccmd-max-processors`（默认 8，最大 16）。下发指令时：

1. 编队按全局顺序**轮转切片**（第 i 个单位分给第 `i % N` 台处理器）；
2. 每台处理器各生成一份 mlog，只包含自己那一份单位，**共享同一套全局编队网格**
   （`cols/x0/y0/spacing` 相同），所以各切片拼起来仍是一个完整编队；
3. 多人下每台处理器有**独立的 token 窗口**（`TOKEN_MIN + p*1000`），保持循环只认自己的
   flag；槽位号仍是全局槽位，所以落点算术不需要按处理器重新编号；
4. 总吞吐 = 各处理器 `ipt` 之和（8 台微型 ≈ 1 台大型），首令延迟随之下降。

处理器放置遵循原版规则：`Build.validPlace` + 建造范围 + 未 ban/未锁定；放不下的格子会跳过，
最后提示“绑定 N 台、跳过 M 台”。

## 关键限制

- `ucontrol` 会把单位变成 `LogicAI`，`logicControlTimeout` 约 10 秒后 `resetController()`
  → 所以默认开启“保持到抵达”，每次循环重下发，顺带刷新控制计时。
- **指令拦截顺序**：原版先把这次右键交给 `CommandAI`，而 `CommandAI.isLogicControllable()`
  在 `hasCommand()` 为真时返回 `false`，`ucontrol` 会直接无效。因此单机注入成功后必须立刻
  `CommandAI.clearCommands()`；多人下改用 `Call.setUnitStance(..., UnitStance.stop, true)`
  让服务端清，并且吞掉这次右键避免指令发到服务端。
- 被接管的单位会从 `input.selectedUnits` 消失 → `UnitBook` 是“重复 2~3”的必要条件。
- 每次覆写都会重置 executor（counter 归 0）；`accumulator` 会被清零以立刻开始执行。
- 处理器链接表通过 `compress(code, links)` 一起带上，覆写不会丢链接。
- 若该处理器本身有链接建筑、且链接发生变化，原版会用纯文本重新编译一次代码，
  注入的单位常量会丢失（程序变成空操作）。重新右键下令即可恢复。
- 多人位置匹配依赖客户端同步到的坐标，容差默认 2 格；高延迟/高速单位可在设置里加大。
- 多人标记会占用 `@flag` 的高位段 `[1e9, 2e9)`；如果你自己的逻辑也用这个区间的 flag，
  请关闭“清理编队标记”之外还要注意冲突。
- 地形适配只保证“落点可站立”，不保证整条路径连通；`pathfind` 会绕，绕不过去就停在障碍前。
- 游戏模拟是单线程的：不要试图把 `ucontrol`/单位更新放到别的线程，会破坏确定性并掉同步。
  真正的“并行”只能是多个处理器分工。瓶颈是处理器的 `ipt`，不是 mod 的 Java 计算。

## 引用的原版 API

- `KeyBind.add(name, KeyCode, category)`：出现在 设置 → 控制；标签 key `keybind.<name>.name`，
  分类标题 key `category.<category>.name`。
- `Build.validPlace(Block, Team, x, y, rotation)` + `player.unit().within(..., type.buildRange)`。
- `LogicBlock.compress(String, Seq<LogicLink>)`、`LogicBuild.updateCode(String, boolean, Cons<LAssembler>)`。
- `InputHandler.commandMode` / `selectedUnits` / `selectedEnemyUnit(float, float)`。
