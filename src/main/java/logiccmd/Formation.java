package logiccmd;

import arc.math.geom.Vec2;
import arc.struct.IntIntMap;
import arc.struct.Seq;
import mindustry.gen.Unit;
import mindustry.type.UnitType;

/**
 * Assigns a formation slot to every unit, per unit type, with terrain clamping.
 *
 * <p>Naval / ground / flying units get separate grids centred on the nearest tile they can
 * actually reach, so a click on land still produces a sensible water destination for a navy
 * group. Slots are remembered per unit id so a redirection keeps each unit's position in the
 * formation instead of reshuffling the whole group.</p>
 */
public final class Formation{

    /** Grid for one unit type. Slots are local to the group (0..units.size-1). */
    public static final class GroupPlan{
        public final UnitType type;
        public final Seq<Unit> units = new Seq<>();
        public Vec2[] dests;
        public int[] slots;
        public int cols, rows;
        public float spacing;
        public Vec2 center;

        GroupPlan(UnitType type){
            this.type = type;
        }

        public float x0(){
            return center.x / mindustry.Vars.tilesize - (cols - 1) / 2f * spacing / mindustry.Vars.tilesize;
        }

        public float y0(){
            return center.y / mindustry.Vars.tilesize + (rows - 1) / 2f * spacing / mindustry.Vars.tilesize;
        }

        public float spacingTiles(){
            return spacing / mindustry.Vars.tilesize;
        }
    }

    private Formation(){
    }

    public static float spacingPx(Seq<Unit> units){
        float max = 0f;
        for(int i = 0; i < units.size; i++){
            Unit unit = units.get(i);
            if(unit != null) max = Math.max(max, unit.hitSize);
        }

        float base = Math.max(12f, max + 8f);
        if(Cfg.spacingPercent <= 0) return base;
        return base * Cfg.spacingPercent / 100f;
    }

    /** Splits the selection by unit type and lays out one clamped grid per type. */
    public static Seq<GroupPlan> plan(Seq<Unit> units, Vec2 click, float spacing, IntIntMap previousSlots, boolean grid){
        Seq<GroupPlan> plans = new Seq<>();

        for(int i = 0; i < units.size; i++){
            Unit unit = units.get(i);
            GroupPlan plan = null;
            for(int p = 0; p < plans.size; p++){
                if(plans.get(p).type == unit.type){
                    plan = plans.get(p);
                    break;
                }
            }
            if(plan == null){
                plan = new GroupPlan(unit.type);
                plans.add(plan);
            }
            plan.units.add(unit);
        }

        for(int i = 0; i < plans.size; i++){
            layout(plans.get(i), click, grid ? spacing : 0f, previousSlots);
        }

        return plans;
    }

    private static void layout(GroupPlan plan, Vec2 click, float spacing, IntIntMap previousSlots){
        int n = plan.units.size;
        plan.dests = new Vec2[n];
        plan.slots = new int[n];

        int cols = Math.max(1, (int)Math.ceil(Math.sqrt(n)));
        int rows = Math.max(1, (int)Math.ceil(n / (float)cols));
        plan.cols = cols;
        plan.rows = rows;

        Vec2 center = Terrain.nearest(plan.type, click.x, click.y, 80);
        plan.center = center == null ? click.cpy() : center;

        // Tighten the grid until every slot lands on tiles this unit type can occupy. At
        // spacing 0 all units share the clamped centre, which is still reachable.
        float candidate = spacing;
        for(int attempt = 0; attempt < 8; attempt++){
            layoutGrid(plan, candidate, previousSlots);
            if(allStandable(plan)) return;

            candidate *= 0.6f;
            if(candidate < 1.5f){
                layoutGrid(plan, 0f, previousSlots);
                return;
            }
        }
    }

    private static void layoutGrid(GroupPlan plan, float spacing, IntIntMap previousSlots){
        int n = plan.units.size;
        int cols = plan.cols, rows = plan.rows;
        plan.spacing = spacing;

        Vec2[] grid = new Vec2[n];
        for(int slot = 0; slot < n; slot++){
            int col = slot % cols;
            int row = slot / cols;
            float x = plan.center.x + (col - (cols - 1) / 2f) * spacing;
            float y = plan.center.y + ((rows - 1) / 2f - row) * spacing;
            grid[slot] = new Vec2(x, y);
        }

        boolean[] used = new boolean[n];
        for(int i = 0; i < n; i++) plan.slots[i] = -1;

        if(previousSlots != null){
            for(int i = 0; i < n; i++){
                int previous = previousSlots.get(plan.units.get(i).id, -1);
                if(previous >= 0 && previous < n && !used[previous]){
                    used[previous] = true;
                    plan.slots[i] = previous;
                    plan.dests[i] = grid[previous].cpy();
                }
            }
        }

        for(int i = 0; i < n; i++){
            if(plan.slots[i] >= 0) continue;

            Unit unit = plan.units.get(i);
            int best = -1;
            float bestDist = Float.MAX_VALUE;

            for(int slot = 0; slot < n; slot++){
                if(used[slot]) continue;
                float dst = grid[slot].dst2(unit.x, unit.y);
                if(dst < bestDist){
                    bestDist = dst;
                    best = slot;
                }
            }

            used[best] = true;
            plan.slots[i] = best;
            plan.dests[i] = grid[best].cpy();
        }
    }

    private static boolean allStandable(GroupPlan plan){
        for(int i = 0; i < plan.dests.length; i++){
            Vec2 dest = plan.dests[i];
            if(dest == null || !Terrain.standable(plan.type, dest.x, dest.y)) return false;
        }
        return true;
    }
}
