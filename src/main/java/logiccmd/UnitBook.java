package logiccmd;

import arc.math.geom.Vec2;
import arc.struct.IntIntMap;
import arc.struct.IntSeq;
import arc.struct.IntSet;
import arc.struct.Seq;
import mindustry.gen.Groups;
import mindustry.gen.Unit;
import mindustry.type.UnitType;

import static mindustry.Vars.player;

/**
 * Remembers which units the last RTS selection contained, because units handed to a
 * processor become logic-controlled and can no longer be box-selected.
 */
public final class UnitBook{

    public static final Seq<Unit> units = new Seq<>();
    /** unit id -> formation slot, kept so redirections do not reshuffle the group. */
    public static final IntIntMap slots = new IntIntMap();
    /** Units tagged with the current multiplayer token, and that token. */
    public static final IntSet tagged = new IntSet();
    public static long tokenBase;
    /** One token window per processor in the port area. */
    public static final long[] tokenBases = new long[16];
    public static int tokenCount;
    /** How many commands reused the token; forces a re-match every few orders. */
    public static int reuseCount;

    private static int px = -1, py = -1;

    private UnitBook(){
    }

    public static void setFromSelection(Seq<Unit> selection){
        units.clear();
        if(selection == null) return;

        for(int i = 0; i < selection.size && units.size < Cfg.maxUnits; i++){
            Unit unit = selection.get(i);
            if(valid(unit)) units.add(unit);
        }
    }

    /** Fills the book from a vanilla control group (a list of unit ids). */
    public static void setFromIds(IntSeq ids){
        units.clear();
        if(ids == null) return;

        for(int i = 0; i < ids.size && units.size < Cfg.maxUnits; i++){
            Unit unit = Groups.unit.getByID(ids.get(i));
            if(valid(unit) && !units.contains(unit)) units.add(unit);
        }
    }

    public static void prune(){
        for(int i = units.size - 1; i >= 0; i--){
            if(!valid(units.get(i))) units.remove(i);
        }
    }

    public static boolean valid(Unit unit){
        return unit != null && unit.isValid() && (player == null || unit.team == player.team());
    }

    public static boolean bound(){
        return px >= 0 && py >= 0;
    }

    public static void bind(int x, int y){
        px = x;
        py = y;
    }

    public static void clear(){
        units.clear();
        slots.clear();
        tagged.clear();
        tokenBase = 0;
        tokenCount = 0;
        reuseCount = 0;
        px = py = -1;
    }

    /** True when the book is exactly the group that was tagged with the current tokens. */
    public static boolean sameGroup(Seq<Unit> list){
        if(tokenCount <= 0 || tagged.size != list.size) return false;
        for(int i = 0; i < list.size; i++){
            if(!tagged.contains(list.get(i).id)) return false;
        }
        return true;
    }

    public static void markTagged(Seq<Unit> list, long[] bases, int count){
        tagged.clear();
        for(int i = 0; i < list.size; i++) tagged.add(list.get(i).id);
        tokenCount = Math.min(count, tokenBases.length);
        for(int i = 0; i < tokenCount; i++) tokenBases[i] = bases[i];
        tokenBase = tokenCount > 0 ? tokenBases[0] : 0;
        reuseCount = 0;
    }

    public static void storeSlots(Seq<Formation.GroupPlan> plans){
        for(int p = 0; p < plans.size; p++){
            Formation.GroupPlan plan = plans.get(p);
            for(int j = 0; j < plan.units.size; j++){
                slots.put(plan.units.get(j).id, plan.slots[j]);
            }
        }
    }

    /** Count of the player team units of this type currently alive on the map. */
    public static int teamCount(UnitType type){
        int count = 0;
        for(int i = 0; i < Groups.unit.size(); i++){
            Unit unit = Groups.unit.index(i);
            if(unit.type == type && valid(unit)) count++;
        }
        return count;
    }

    /** Count of the player team units of this type inside a circle. */
    public static int countNear(UnitType type, Vec2 center, float radius){
        int count = 0;
        for(int i = 0; i < Groups.unit.size(); i++){
            Unit unit = Groups.unit.index(i);
            if(unit.type == type && valid(unit) && unit.within(center.x, center.y, radius)) count++;
        }
        return count;
    }

    public static Vec2 centroid(Seq<Unit> list){
        float x = 0f, y = 0f;
        for(int i = 0; i < list.size; i++){
            x += list.get(i).x;
            y += list.get(i).y;
        }
        return new Vec2(x / Math.max(1, list.size), y / Math.max(1, list.size));
    }

    /** Largest distance from the centre to any unit, in pixels. */
    public static float maxRadius(Seq<Unit> list, Vec2 center){
        float max = 0f;
        for(int i = 0; i < list.size; i++){
            max = Math.max(max, list.get(i).dst(center));
        }
        return max;
    }
}
