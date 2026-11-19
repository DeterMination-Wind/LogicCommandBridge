package logiccmd;

import arc.Core;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Lines;
import arc.input.KeyBind;
import arc.input.KeyCode;
import arc.math.geom.Vec2;
import arc.struct.IntSeq;
import arc.struct.Seq;
import mindustry.ai.types.CommandAI;
import mindustry.ai.UnitStance;
import mindustry.gen.Call;
import mindustry.graphics.Pal;
import mindustry.gen.Teamc;
import mindustry.gen.Unit;
import mindustry.world.blocks.logic.LogicBlock;
import mindustry.world.blocks.logic.LogicBlock.LogicBuild;

import static mindustry.Vars.control;
import static mindustry.Vars.headless;
import static mindustry.Vars.net;
import static mindustry.Vars.player;
import static mindustry.Vars.state;
import static mindustry.Vars.tilesize;
import static mindustry.Vars.ui;

/**
 * Runtime glue: hotkeys, port-area dragging, splitting the army across processors, and
 * applying the generated programs.
 */
public final class LogicCommandFeature{

    private static final Seq<Request> queue = new Seq<>();

    private static String lastCode = "";
    private static long tokenSeq;
    private static int activeGroup = -1;

    private static final class Request{
        final Vec2 target;
        final Teamc attack;
        final Seq<Unit> selection;

        Request(Vec2 target, Teamc attack, Seq<Unit> selection){
            this.target = target;
            this.attack = attack;
            this.selection = new Seq<>();
            if(selection != null) this.selection.addAll(selection);
        }
    }

    private LogicCommandFeature(){
    }

    public static void submit(Vec2 target, Teamc attack){
        Seq<Unit> selection = control == null || control.input == null ? null : control.input.selectedUnits;
        queue.add(new Request(target, attack, selection));
    }

    public static void update(){
        if(state == null || !state.isGame()){
            queue.clear();
            Ports.picking = false;
            Ports.dragging = false;
            return;
        }
        if(Core.scene == null) return;

        boolean blocked = Core.scene.hasField() || Core.scene.hasDialog();
        if(!blocked) handleKeys();

        Ports.updateDragFromMouse();
        UnitBook.prune();
        drain();
    }

    private static void handleKeys(){
        if(tapped(Cfg.keyBind)){
            if(Ports.picking){
                Ports.cancelPick();
            }else{
                Ports.beginPick();
            }
        }

        if(tapped(Cfg.keyRelease)){
            UnitBook.clear();
            hint("logiccmd.released");
        }

        if(tapped(Cfg.keyUnbind)){
            releaseLogicControl();
        }

        if(tapped(Cfg.keyPreview)){
            showPreview();
        }

        if(tapped(Cfg.keyPrevGroup)){
            cycleGroup(-1);
        }

        if(tapped(Cfg.keyNextGroup)){
            cycleGroup(1);
        }
    }

    /** Switches the active formation to the next/previous non-empty vanilla control group. */
    private static void cycleGroup(int direction){
        if(control == null || control.input == null) return;

        IntSeq[] groups = control.input.controlGroups;
        if(groups == null || groups.length == 0) return;

        int n = groups.length;
        for(int step = 1; step <= n; step++){
            int index = Math.floorMod(activeGroup + direction * step, n);
            IntSeq ids = groups[index];
            if(ids == null || ids.isEmpty()) continue;
            selectGroup(index, ids);
            return;
        }

        hint("logiccmd.group.empty");
    }

    private static void selectGroup(int index, IntSeq ids){
        activeGroup = index;
        UnitBook.setFromIds(ids);

        if(UnitBook.units.isEmpty()){
            hint("logiccmd.group.empty");
            return;
        }

        // Mirror into the vanilla selection for commandable units so the RTS UI matches.
        control.input.selectedUnits.clear();
        for(int i = 0; i < UnitBook.units.size; i++){
            Unit unit = UnitBook.units.get(i);
            if(unit.isCommandable()) control.input.selectedUnits.add(unit);
        }

        if(Cfg.hints && ui != null){
            ui.showInfoFade(Core.bundle.format("logiccmd.group.selected",
                String.valueOf(index + 1), String.valueOf(UnitBook.units.size)));
        }
    }

    /** One-key release: writes an unbind program to every ready processor. */
    private static void releaseLogicControl(){
        Seq<Ports.Port> ready = Ports.readyPorts();
        if(ready.isEmpty()){
            fail(Ports.hasPorts() ? "noPort" : "need.pick");
            return;
        }
        if(UnitBook.units.isEmpty()){
            hint("logiccmd.unbind.empty");
            return;
        }

        Seq<Unit> units = new Seq<>();
        units.addAll(UnitBook.units);

        Mlog.Gen gen = net.active()
            ? Mlog.releaseFlags(typesOnly(units))
            : Mlog.releaseExact(units);

        int applied = 0;
        for(int i = 0; i < ready.size; i++){
            if(Injector.apply(ready.get(i), gen)) applied++;
        }

        if(applied == 0){
            fail("noPort");
            return;
        }

        int count = units.size;
        UnitBook.clear();

        if(Cfg.hints && ui != null){
            ui.showInfoFade(Core.bundle.format("logiccmd.unbound", String.valueOf(count)));
        }
        if(Cfg.debug){
            arc.util.Log.info("[LCB] release: @ units across @ processor(s)", count, applied);
        }
    }

    /** Groups with only type/count information, for release sweeps. */
    private static Seq<Mlog.TypeGroup> typesOnly(Seq<Unit> units){
        Seq<Mlog.TypeGroup> groups = new Seq<>();
        for(int i = 0; i < units.size; i++){
            Unit unit = units.get(i);
            if(groupByName(groups, unit.type.name) != null) continue;

            Mlog.TypeGroup group = new Mlog.TypeGroup();
            group.name = unit.type.name;
            group.teamCount = UnitBook.teamCount(unit.type);
            groups.add(group);
        }
        return groups;
    }

    /** Clears transient state on world load. */
    public static void resetState(){
        queue.clear();
        UnitBook.clear();
        Ports.ports.clear();
        Ports.picking = false;
        Ports.dragging = false;
        activeGroup = -1;
    }

    private static boolean tapped(KeyBind bind){
        return bind != null && bind.value != null && bind.value.key != null
            && bind.value.key != KeyCode.unset && Core.input.keyTap(bind);
    }

    private static void drain(){
        if(queue.isEmpty()) return;

        if(!Ports.hasPorts()){
            queue.clear();
            fail("need.pick");
            return;
        }

        if(Ports.building()){
            // Keep the newest requests around and fire them once construction finishes.
            if(queue.size > 8) queue.removeRange(0, queue.size - 8);
            hint("logiccmd.pick.building");
            return;
        }

        if(Ports.readyPorts().isEmpty()){
            queue.clear();
            fail("noPort");
            return;
        }

        for(int i = 0; i < queue.size; i++) execute(queue.get(i));
        queue.clear();
    }

    private static void execute(Request request){
        Seq<Ports.Port> ready = Ports.readyPorts();
        if(ready.isEmpty()){
            fail("noPort");
            return;
        }

        if(!state.rules.logicUnitControl){
            fail("noUnitControl");
            return;
        }

        // A fresh box selection replaces the group; an empty one reuses it, which is what
        // makes repeated right clicks work after the units became logic-controlled.
        if(request.selection.size > 0) UnitBook.setFromSelection(request.selection);
        UnitBook.prune();

        if(UnitBook.units.isEmpty()){
            fail("noSelection");
            return;
        }

        boolean attack = request.attack != null;
        Vec2 click = attack ? new Vec2(request.attack.x(), request.attack.y()) : request.target;

        float spacing = Formation.spacingPx(UnitBook.units);
        float approachTiles = Math.max(0.6f, spacing / (4f * tilesize));
        float arrivalTiles = Math.max(1.8f, spacing / (2f * tilesize));

        Seq<Formation.GroupPlan> plans = Formation.plan(UnitBook.units, click, spacing, UnitBook.slots, !Cfg.crowdArrival);

        int slices = Math.min(ready.size, UnitBook.units.size);
        Seq<IntSeq> sliceOf = new Seq<>();
        for(int i = 0; i < slices; i++) sliceOf.add(new IntSeq());
        for(int i = 0; i < UnitBook.units.size; i++) sliceOf.get(i % slices).add(i);

        int perSlice = (UnitBook.units.size + slices - 1) / Math.max(1, slices);
        int ipt = 2;
        LogicBuild firstBuild = Ports.build(ready.get(0));
        if(firstBuild != null) ipt = Math.max(1, firstBuild.ipt);
        Mlog.holdWait = computeHoldWait(perSlice, ipt, net.active());

        // Vanilla may have already handed this click (or an older click) to CommandAI, and
        // CommandAI.isLogicControllable() is false while a command is active, which would make
        // every ucontrol a silent no-op. Clear those commands first.
        if(net.active()){
            int[] ids = new int[UnitBook.units.size];
            for(int i = 0; i < ids.length; i++) ids[i] = UnitBook.units.get(i).id;
            Call.setUnitStance(player, ids, UnitStance.stop, true);
        }else{
            for(int i = 0; i < UnitBook.units.size; i++){
                Unit unit = UnitBook.units.get(i);
                if(unit.controller() instanceof CommandAI){
                    ((CommandAI)unit.controller()).clearCommands();
                }
            }
        }

        boolean multiplayer = net.active();
        int applied = 0;

        if(multiplayer){
            Vec2 centroid = UnitBook.centroid(UnitBook.units);
            float prefilterRadius = UnitBook.maxRadius(UnitBook.units, centroid)
                + Cfg.mpToleranceTiles() * tilesize + Cfg.mpPadTiles * tilesize;

            boolean tag = Cfg.hold;
            boolean cleanupFirst = Cfg.mpFlagCleanup;
            boolean reuse = Cfg.mpReuse && Cfg.hold && !attack
                && UnitBook.reuseCount < 8 && UnitBook.sameGroup(UnitBook.units)
                && UnitBook.tokenCount == slices;

            long[] bases = new long[slices];
            for(int p = 0; p < slices; p++){
                bases[p] = reuse ? UnitBook.tokenBases[p] : nextTokenBase();
            }
            if(reuse) UnitBook.reuseCount++;

            for(int p = 0; p < slices; p++){
                Seq<Mlog.TypeGroup> groups = buildGroups(plans, sliceOf.get(p), centroid, prefilterRadius, attack);

                Mlog.Gen gen;
                if(reuse){
                    gen = Mlog.moveFlagOnly(groups, bases[p], approachTiles, arrivalTiles);
                }else if(attack){
                    gen = Mlog.attackMatch(groups, bases[p], click.x, click.y,
                        Cfg.mpToleranceTiles(), approachTiles, tag, cleanupFirst);
                }else if(allOfType(groups)){
                    gen = Mlog.moveAllOfType(groups, bases[p], approachTiles, arrivalTiles, tag, cleanupFirst);
                }else{
                    gen = Mlog.moveMatch(groups, bases[p], Cfg.mpToleranceTiles(),
                        approachTiles, arrivalTiles, tag, cleanupFirst);
                }

                if(Injector.apply(ready.get(p), gen)){
                    applied++;
                    lastCode = gen.code;
                }
            }

            if(!reuse && tag && applied > 0) UnitBook.markTagged(UnitBook.units, bases, slices);
        }else{
            for(int p = 0; p < slices; p++){
                IntSeq indices = sliceOf.get(p);
                Seq<Unit> units = new Seq<>();
                Vec2[] dests = new Vec2[indices.size];

                for(int j = 0; j < indices.size; j++){
                    Unit unit = UnitBook.units.get(indices.get(j));
                    units.add(unit);
                    dests[j] = destFor(plans, unit, attack);
                }

                Mlog.Gen gen = attack
                    ? Mlog.attackExact(units, request.attack, dests, approachTiles)
                    : Mlog.moveExact(units, dests, approachTiles, arrivalTiles);

                if(Injector.apply(ready.get(p), gen)){
                    applied++;
                    lastCode = gen.code;
                }
            }

            UnitBook.storeSlots(plans);
        }

        if(applied == 0){
            fail("noPort");
            return;
        }

        if(Cfg.debug){
            arc.util.Log.info("[LCB] @: @ units across @ processor(s), port=@",
                attack ? "attack" : "move", UnitBook.units.size, applied,
                Ports.ports.isEmpty() ? "-" : Ports.ports.get(0).label());
        }

        toast(request, applied);
    }

    /** Builds the groups for one processor slice. */
    private static Seq<Mlog.TypeGroup> buildGroups(Seq<Formation.GroupPlan> plans, IntSeq slice,
                                                   Vec2 centroid, float prefilterRadius, boolean attack){
        Seq<Mlog.TypeGroup> groups = new Seq<>();

        for(int s = 0; s < slice.size; s++){
            Unit unit = UnitBook.units.get(slice.get(s));
            Formation.GroupPlan plan = planFor(plans, unit);

            Mlog.TypeGroup group = groupByName(groups, unit.type.name);
            if(group == null){
                group = new Mlog.TypeGroup();
                group.name = unit.type.name;
                group.teamCount = UnitBook.teamCount(unit.type);
                if(plan != null){
                    group.cols = plan.cols;
                    group.x0 = plan.x0();
                    group.y0 = plan.y0();
                    group.spacingTiles = plan.spacingTiles();
                }
                group.prefilterCx = centroid.x;
                group.prefilterCy = centroid.y;
                group.prefilterTiles = UnitBook.countNear(unit.type, centroid, prefilterRadius) * 2
                    <= group.teamCount ? prefilterRadius / tilesize : 0f;
                groups.add(group);
            }

            int j = plan == null ? -1 : indexInPlan(plan, unit);
            Mlog.MatchUnit entry = new Mlog.MatchUnit();
            entry.type = unit.type.name;
            entry.x = unit.x;
            entry.y = unit.y;
            entry.slot = j >= 0 ? plan.slots[j] : 0;
            entry.dest = attack
                ? (plan != null ? plan.center.cpy() : new Vec2(unit.x, unit.y))
                : (j >= 0 ? plan.dests[j] : new Vec2(unit.x, unit.y));
            group.units.add(entry);
        }

        return groups;
    }

    private static Mlog.TypeGroup groupByName(Seq<Mlog.TypeGroup> groups, String name){
        for(int i = 0; i < groups.size; i++){
            if(groups.get(i).name.equals(name)) return groups.get(i);
        }
        return null;
    }

    private static Formation.GroupPlan planFor(Seq<Formation.GroupPlan> plans, Unit unit){
        for(int i = 0; i < plans.size; i++){
            if(plans.get(i).type == unit.type) return plans.get(i);
        }
        return null;
    }

    private static int indexInPlan(Formation.GroupPlan plan, Unit unit){
        for(int i = 0; i < plan.units.size; i++){
            if(plan.units.get(i) == unit) return i;
        }
        return -1;
    }

    private static Vec2 destFor(Seq<Formation.GroupPlan> plans, Unit unit, boolean attack){
        Formation.GroupPlan plan = planFor(plans, unit);
        if(plan != null){
            if(attack) return plan.center.cpy();
            int j = indexInPlan(plan, unit);
            if(j >= 0) return plan.dests[j];
        }
        return new Vec2(unit.x, unit.y);
    }

    /** True when every type in the group covers all units of that type on the team. */
    private static boolean allOfType(Seq<Mlog.TypeGroup> groups){
        for(int i = 0; i < groups.size; i++){
            Mlog.TypeGroup group = groups.get(i);
            if(group.units.size != group.teamCount) return false;
        }
        return groups.size > 0;
    }

    private static long nextTokenBase(){
        return Mlog.TOKEN_MIN + (tokenSeq++ % 1_000_000L) * Mlog.TOKEN_SLOTS;
    }

    /**
     * Shrinks the hold interval when one cycle would otherwise approach the 10s LogicAI
     * timeout (big groups on a small processor).
     */
    private static int computeHoldWait(int unitCount, int ipt, boolean multiplayer){
        float perUnit = multiplayer ? 14f : 4f;
        float cycleSeconds = perUnit * unitCount / Math.max(1, ipt * 60f);
        int max = (int)Math.floor(9f - cycleSeconds);
        return Math.max(1, Math.min(Cfg.holdWaitSec, max));
    }

    private static void toast(Request request, int processors){
        if(!Cfg.hints || ui == null) return;

        String key = request.attack != null ? "logiccmd.sent.attack" : "logiccmd.sent.move";
        StringBuilder text = new StringBuilder(Core.bundle.format(key,
            String.valueOf(UnitBook.units.size), String.valueOf(processors)));

        ui.showInfoFade(text.toString());
    }

    private static void showPreview(){
        if(lastCode == null || lastCode.isEmpty()){
            hint("logiccmd.preview.empty");
            return;
        }
        if(ui != null) ui.showText(Core.bundle.get("logiccmd.preview.title"), lastCode);
    }

    public static void hint(String key){
        if(!Cfg.hints || ui == null) return;
        ui.showInfoFade(Core.bundle.get(key));
    }

    public static void fail(String code){
        if(ui == null) return;
        ui.showErrorMessage(Core.bundle.get("logiccmd.err." + code, code));
    }

    public static void placed(int count, int skipped){
        if(ui == null) return;
        ui.showInfoFade(Core.bundle.format("logiccmd.pick.placed",
            String.valueOf(count), String.valueOf(skipped)));
    }

    // region drawing

    public static void draw(){
        if(headless || state == null || !state.isGame()) return;

        if(Ports.picking && Ports.dragging){
            drawDrag();
        }else if(Ports.picking && Ports.startX >= 0){
            drawCell(Ports.startX, Ports.startY, Cfg.processorBlock(), true);
        }

        for(int i = 0; i < Ports.ports.size; i++){
            Ports.Port port = Ports.ports.get(i);
            drawCell(port.x, port.y, port.block, false);
        }

        // Highlight the active formation's units.
        for(int i = 0; i < UnitBook.units.size; i++){
            Unit unit = UnitBook.units.get(i);
            if(unit == null || !unit.isValid()) continue;

            Draw.color(Pal.accent, 0.7f);
            Lines.stroke(1.5f);
            Lines.circle(unit.x, unit.y, unit.hitSize * 0.8f + 3f + arc.math.Mathf.absin(8f, 2f));
            Draw.reset();
        }
    }

    private static void drawDrag(){
        Vec2 min = new Vec2(), max = new Vec2();
        Ports.dragRect(min, max);

        int size = Cfg.processorBlock().size;
        int cols = Math.max(1, ((int)max.x - (int)min.x + 1) / size);
        int rows = Math.max(1, ((int)max.y - (int)min.y + 1) / size);

        float x0 = min.x * tilesize;
        float y0 = min.y * tilesize;
        float width = cols * size * tilesize;
        float height = rows * size * tilesize;

        Draw.color(Pal.accent, 0.35f);
        Lines.stroke(2f);
        Lines.rect(x0, y0, width, height);
        Draw.reset();

        int placed = 0;
        for(int ry = 0; ry < rows && placed < Cfg.maxProcessors; ry++){
            for(int rx = 0; rx < cols && placed < Cfg.maxProcessors; rx++){
                int px = (int)min.x + rx * size;
                int py = (int)min.y + ry * size;
                drawCell(px, py, Cfg.processorBlock(), true);
                placed++;
            }
        }
    }

    private static void drawCell(int x, int y, mindustry.world.Block block, boolean preview){
        int size = block.size;
        float cx = (x + size / 2f) * tilesize;
        float cy = (y + size / 2f) * tilesize;
        float width = size * tilesize;

        boolean ok = preview ? Ports.check(x, y, block) == null : Ports.ready(new Ports.Port(x, y, block));

        Draw.color(ok ? Pal.accent : Pal.remove, preview ? 0.85f : 0.5f);
        Lines.stroke(preview ? 1.6f : 1.2f);
        Lines.rect(cx - width / 2f, cy - width / 2f, width, width);
        Draw.reset();
    }

    // endregion
}
