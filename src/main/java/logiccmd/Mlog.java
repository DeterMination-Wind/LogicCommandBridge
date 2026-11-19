package logiccmd;

import arc.math.geom.Vec2;
import arc.struct.Seq;
import mindustry.gen.Teamc;
import mindustry.gen.Unit;
import mindustry.type.UnitType;

import java.util.Locale;

import static mindustry.Vars.tilesize;

/**
 * Turns a captured RTS command into mlog text.
 *
 * <p>Program layout (hold mode):</p>
 * <ol>
 *   <li><b>order pass</b> - the shortest possible pass that reaches every unit, so a
 *       redirection takes effect immediately;</li>
 *   <li><b>hold loop</b> - re-issues the order every few seconds (LogicAI drops control after
 *       10s) and counts arrivals;</li>
 *   <li><b>release</b> - {@code ucontrol unbind} on arrival, so units become RTS commandable
 *       immediately instead of waiting out the 10s LogicAI timeout.</li>
 * </ol>
 *
 * <p>Single player injects real unit objects as {@code lc_uN} constants. Multiplayer matches
 * units by their synced position ({@code sensor @x/@y}, never {@code ucontrol within}, which
 * would hijack every same-type unit it touches) and tags them with a flag token so follow-up
 * commands skip matching.</p>
 */
public final class Mlog{

    /** Start of this mod's unit flag token range. */
    public static final long TOKEN_MIN = 1_000_000_000L;
    /** Width of the whole token range; also used by the cleanup sweep. */
    public static final long TOKEN_SPAN = 1_000_000_000L;
    /** Slots per command; also the width of a single command's token window. */
    public static final int TOKEN_SLOTS = 1000;

    /** Hold refresh interval in seconds; recomputed per command to stay under the 10s timeout. */
    public static int holdWait = 5;

    public static final class Gen{
        public String code = "";
        public String kind = "move";
        public boolean exact;
        public final Seq<Unit> units = new Seq<>();
        public Teamc target;
        public int count;
        public int instructions;
        /** Instructions before the first order reaches the last unit; used for the latency hint. */
        public int matchInstructions;
        public long tokenBase;
    }

    /** Unit data needed by the multiplayer generator (no live Unit reference required). */
    public static final class MatchUnit{
        public String type;
        public float x, y;
        public int slot;
        /** Formation destination, or the approach point for attack orders. */
        public Vec2 dest;
    }

    /** One unit type: its own clamped formation grid and sweep metadata. */
    public static final class TypeGroup{
        public String name;
        public int teamCount;
        public float prefilterTiles;
        public float prefilterCx, prefilterCy;

        public int cols = 1;
        public float x0, y0;
        public float spacingTiles;

        public final Seq<MatchUnit> units = new Seq<>();
    }

    private Mlog(){
    }

    // region single player (injected unit constants)

    /** Exact formation move: order pass, hold loop, release on arrival. */
    public static Gen moveExact(Seq<Unit> units, Vec2[] dests, float approachTiles, float arrivalTiles){
        Gen gen = new Gen();
        gen.kind = "move";
        gen.exact = true;
        gen.units.addAll(units);
        gen.count = units.size;

        boolean hold = Cfg.hold;
        int n = units.size;
        StringBuilder s = new StringBuilder();

        for(int i = 0; i < n; i++){
            Vec2 dest = dests[i];
            s.append("ubind lc_u").append(i).append('\n');
            s.append("ucontrol target 0 0 0\n");
            appendMoveLiteral(s, dest.x / tilesize, dest.y / tilesize, approachTiles);
        }
        gen.matchInstructions = countInstructions(s.toString());

        if(hold){
            s.append("lc_hold:\n");
            s.append("set lc_c 0\n");

            for(int i = 0; i < n; i++){
                Vec2 dest = dests[i];
                float x = dest.x / tilesize;
                float y = dest.y / tilesize;

                s.append("ubind lc_u").append(i).append('\n');
                appendMoveLiteral(s, x, y, approachTiles);
                if(Cfg.attackMove) appendAutoAttack(s, "lc_am" + i + "_n", "lc_am" + i + "_d");
                s.append("ucontrol within ").append(f(x)).append(' ').append(f(y)).append(' ')
                    .append(f(arrivalTiles)).append(" lc_b\n");
                s.append("op add lc_c lc_c lc_b\n");
            }

            s.append("op add lc_tm lc_tm 1\n");
            s.append("jump lc_done greaterThan lc_tm ").append(Cfg.holdCycles).append('\n');
            s.append("jump lc_done greaterThanEq lc_c ").append(n).append('\n');
            s.append("wait ").append(holdWait).append('\n');
            s.append("jump lc_hold always\n");
            s.append("lc_done:\n");

            for(int i = 0; i < n; i++){
                s.append("ubind lc_u").append(i).append('\n');
                s.append("ucontrol unbind\n");
            }
        }

        s.append("stop\n");
        gen.code = s.toString();
        gen.instructions = countInstructions(gen.code);
        return gen;
    }

    /** Exact attack order: move to the terrain-clamped approach point, aim, hold, release. */
    public static Gen attackExact(Seq<Unit> units, Teamc target, Vec2[] dests, float approachTiles){
        Gen gen = new Gen();
        gen.kind = "attack";
        gen.exact = true;
        gen.target = target;
        gen.units.addAll(units);
        gen.count = units.size;

        boolean hold = Cfg.hold;
        StringBuilder s = new StringBuilder();

        for(int i = 0; i < units.size; i++){
            Vec2 dest = dests[i];
            s.append("ubind lc_u").append(i).append('\n');
            appendMoveLiteral(s, dest.x / tilesize, dest.y / tilesize, approachTiles);
            s.append("ucontrol targetp lc_t 1\n");
        }
        gen.matchInstructions = countInstructions(s.toString());

        if(hold){
            s.append("lc_hold:\n");
            s.append("op add lc_tm lc_tm 1\n");
            s.append("wait ").append(holdWait).append('\n');
            // A destroyed target drops to 0 health and ends the hold early.
            s.append("sensor lc_h lc_t @health\n");
            s.append("jump lc_done lessThan lc_h 1\n");

            for(int i = 0; i < units.size; i++){
                s.append("ubind lc_u").append(i).append('\n');
                s.append("ucontrol targetp lc_t 1\n");
            }

            s.append("jump lc_done greaterThan lc_tm ").append(Cfg.holdCycles).append('\n');
            s.append("jump lc_hold always\n");
            s.append("lc_done:\n");

            for(int i = 0; i < units.size; i++){
                s.append("ubind lc_u").append(i).append('\n');
                s.append("ucontrol unbind\n");
            }
        }

        s.append("stop\n");
        gen.code = s.toString();
        gen.instructions = countInstructions(gen.code);
        return gen;
    }

    // endregion

    // region release (unbind current formation)

    /** One-shot release for the current book in single player. */
    public static Gen releaseExact(Seq<Unit> units){
        Gen gen = new Gen();
        gen.kind = "release";
        gen.exact = true;
        gen.units.addAll(units);
        gen.count = units.size;

        StringBuilder s = new StringBuilder();
        for(int i = 0; i < units.size; i++){
            s.append("ubind lc_u").append(i).append('\n');
            s.append("ucontrol unbind\n");
        }
        s.append("stop\n");

        gen.code = s.toString();
        gen.instructions = countInstructions(gen.code);
        gen.matchInstructions = gen.instructions;
        return gen;
    }

    /** One-shot release for multiplayer: clear our flags and unbind, per unit type. */
    public static Gen releaseFlags(Seq<TypeGroup> groups){
        Gen gen = new Gen();
        gen.kind = "release";

        StringBuilder s = new StringBuilder();
        for(int t = 0; t < groups.size; t++){
            TypeGroup group = groups.get(t);
            if(group.teamCount <= 0) continue;

            s.append("set lc_i 0\n");
            s.append("rel").append(t).append(":\n");
            s.append("ubind @").append(group.name).append('\n');
            s.append("jump ren").append(t).append(" equal @unit null\n");
            s.append("sensor lc_f @unit @flag\n");
            s.append("jump rnx").append(t).append(" lessThan lc_f ").append(TOKEN_MIN).append('\n');
            s.append("jump rnx").append(t).append(" greaterThanEq lc_f ")
                .append(TOKEN_MIN + TOKEN_SPAN).append('\n');
            // Clear the token first: after unbind the unit is no longer logic-controllable.
            s.append("ucontrol flag 0\n");
            s.append("ucontrol unbind\n");
            s.append("rnx").append(t).append(":\n");
            s.append("op add lc_i lc_i 1\n");
            s.append("jump rel").append(t).append(" lessThan lc_i ")
                .append(Math.max(1, group.teamCount)).append('\n');
            s.append("ren").append(t).append(":\n");
        }
        s.append("stop\n");

        gen.count = totalUnits(groups);
        gen.code = s.toString();
        gen.instructions = countInstructions(gen.code);
        gen.matchInstructions = gen.instructions;
        return gen;
    }

    // endregion

    // region multiplayer (position match + flag token)

    /** First move command of a group: cleanup, match + tag + order, hold, release. */
    public static Gen moveMatch(Seq<TypeGroup> groups, long tokenBase,
                                float toleranceTiles, float approachTiles, float arrivalTiles,
                                boolean tag, boolean cleanupFirst){
        Gen gen = new Gen();
        gen.kind = "move";
        gen.tokenBase = tokenBase;

        StringBuilder order = new StringBuilder();
        if(cleanupFirst) appendCleanup(order, groups);
        appendMatchPhase(order, groups, tokenBase, toleranceTiles, approachTiles, tag, false, 0f, 0f);

        StringBuilder rest = new StringBuilder();
        if(Cfg.hold) appendHold(rest, groups, tokenBase, approachTiles, arrivalTiles, false, 0f, 0f, false);
        rest.append("done:\n");
        if(Cfg.hold && tag) appendRelease(rest, groups, tokenBase);
        rest.append("stop\n");

        gen.count = totalUnits(groups);
        gen.matchInstructions = countInstructions(order.toString());
        gen.code = order.append(rest).toString();
        gen.instructions = countInstructions(gen.code);
        return gen;
    }

    /** First attack command of a group: approach the clamped point and aim at the target. */
    public static Gen attackMatch(Seq<TypeGroup> groups, long tokenBase, float targetX, float targetY,
                                  float toleranceTiles, float approachTiles, boolean tag, boolean cleanupFirst){
        Gen gen = new Gen();
        gen.kind = "attack";
        gen.tokenBase = tokenBase;

        float tx = targetX / tilesize;
        float ty = targetY / tilesize;

        StringBuilder order = new StringBuilder();
        if(cleanupFirst) appendCleanup(order, groups);
        appendMatchPhase(order, groups, tokenBase, toleranceTiles, approachTiles, tag, true, tx, ty);

        StringBuilder rest = new StringBuilder();
        if(Cfg.hold) appendHold(rest, groups, tokenBase, approachTiles, 0f, true, tx, ty, true);
        rest.append("done:\n");
        if(Cfg.hold && tag) appendRelease(rest, groups, tokenBase);
        rest.append("stop\n");

        gen.count = totalUnits(groups);
        gen.matchInstructions = countInstructions(order.toString());
        gen.code = order.append(rest).toString();
        gen.instructions = countInstructions(gen.code);
        return gen;
    }

    /** Fast path: the group is every unit of its type, so no position matching is needed. */
    public static Gen moveAllOfType(Seq<TypeGroup> groups, long tokenBase,
                                    float approachTiles, float arrivalTiles,
                                    boolean tag, boolean cleanupFirst){
        Gen gen = new Gen();
        gen.kind = "move";
        gen.tokenBase = tokenBase;

        StringBuilder order = new StringBuilder();
        if(cleanupFirst) appendCleanup(order, groups);

        for(int t = 0; t < groups.size; t++){
            TypeGroup group = groups.get(t);
            for(int j = 0; j < group.units.size; j++){
                MatchUnit unit = group.units.get(j);
                order.append("ubind @").append(group.name).append('\n');
                if(tag) order.append("ucontrol flag ").append(tokenBase + unit.slot).append('\n');
                order.append("ucontrol target 0 0 0\n");
                appendMoveLiteral(order, unit.dest.x / tilesize, unit.dest.y / tilesize, approachTiles);
            }
        }

        StringBuilder rest = new StringBuilder();
        if(Cfg.hold) appendHold(rest, groups, tokenBase, approachTiles, arrivalTiles, false, 0f, 0f, false);
        rest.append("done:\n");
        if(Cfg.hold && tag) appendRelease(rest, groups, tokenBase);
        rest.append("stop\n");

        gen.count = totalUnits(groups);
        gen.matchInstructions = countInstructions(order.toString());
        gen.code = order.append(rest).toString();
        gen.instructions = countInstructions(gen.code);
        return gen;
    }

    /** Follow-up move for an unchanged group: no match, no tagging, orders first. */
    public static Gen moveFlagOnly(Seq<TypeGroup> groups, long tokenBase,
                                   float approachTiles, float arrivalTiles){
        Gen gen = new Gen();
        gen.kind = "move";
        gen.tokenBase = tokenBase;

        StringBuilder s = new StringBuilder();
        if(Cfg.hold){
            appendHold(s, groups, tokenBase, approachTiles, arrivalTiles, false, 0f, 0f, false);
        }else{
            appendHoldBody(s, groups, tokenBase, approachTiles, arrivalTiles, false, 0f, 0f);
        }
        s.append("done:\n");
        if(Cfg.hold) appendRelease(s, groups, tokenBase);
        s.append("stop\n");

        gen.count = totalUnits(groups);
        gen.code = s.toString();
        gen.instructions = countInstructions(gen.code);
        gen.matchInstructions = countInstructions(gen.code);
        return gen;
    }

    // endregion

    // region shared multiplayer phases

    private static void appendMatchPhase(StringBuilder s, Seq<TypeGroup> groups, long tokenBase,
                                         float toleranceTiles, float approachTiles,
                                         boolean tag, boolean attack, float tx, float ty){
        float tolerance2 = toleranceTiles * toleranceTiles;

        for(int t = 0; t < groups.size; t++){
            TypeGroup group = groups.get(t);
            if(group.units.isEmpty()) continue;

            s.append("set lc_i 0\n");
            s.append("sw").append(t).append(":\n");
            s.append("ubind @").append(group.name).append('\n');
            s.append("jump en").append(t).append(" equal @unit null\n");
            // Sensor based position read: ucontrol within would take the unit over.
            s.append("sensor lc_px @unit @x\n");
            s.append("sensor lc_py @unit @y\n");

            if(group.prefilterTiles > 0f){
                s.append("op sub lc_dx lc_px ").append(f(group.prefilterCx / tilesize)).append('\n');
                s.append("op sub lc_dy lc_py ").append(f(group.prefilterCy / tilesize)).append('\n');
                s.append("op mul lc_dx lc_dx lc_dx\n");
                s.append("op mul lc_dy lc_dy lc_dy\n");
                s.append("op add lc_d lc_dx lc_dy\n");
                s.append("jump nx").append(t).append(" greaterThan lc_d ")
                    .append(f(group.prefilterTiles * group.prefilterTiles)).append('\n');
            }

            for(int j = 0; j < group.units.size; j++){
                MatchUnit unit = group.units.get(j);
                s.append("op sub lc_dx lc_px ").append(f(unit.x / tilesize)).append('\n');
                s.append("op sub lc_dy lc_py ").append(f(unit.y / tilesize)).append('\n');
                s.append("op mul lc_dx lc_dx lc_dx\n");
                s.append("op mul lc_dy lc_dy lc_dy\n");
                s.append("op add lc_d lc_dx lc_dy\n");
                s.append("jump m").append(t).append('_').append(j).append(" lessThan lc_d ")
                    .append(f(tolerance2)).append('\n');
            }

            s.append("jump nx").append(t).append(" always\n");

            for(int j = 0; j < group.units.size; j++){
                MatchUnit unit = group.units.get(j);
                s.append("m").append(t).append('_').append(j).append(":\n");
                if(tag) s.append("ucontrol flag ").append(tokenBase + unit.slot).append('\n');
                if(attack){
                    appendMoveLiteral(s, unit.dest.x / tilesize, unit.dest.y / tilesize, approachTiles);
                    s.append("ucontrol target ").append(f(tx)).append(' ').append(f(ty)).append(" 1\n");
                }else{
                    s.append("ucontrol target 0 0 0\n");
                    appendMoveLiteral(s, unit.dest.x / tilesize, unit.dest.y / tilesize, approachTiles);
                }
                s.append("jump nx").append(t).append(" always\n");
            }

            s.append("nx").append(t).append(":\n");
            s.append("op add lc_i lc_i 1\n");
            s.append("jump sw").append(t).append(" lessThan lc_i ")
                .append(Math.max(1, group.teamCount)).append('\n');
            s.append("en").append(t).append(":\n");
        }
    }

    /** Emits the full hold loop; waitFirst is used when the order pass already ran. */
    private static void appendHold(StringBuilder s, Seq<TypeGroup> groups, long tokenBase,
                                   float approachTiles, float arrivalTiles,
                                   boolean attack, float tx, float ty, boolean waitFirst){
        s.append("hold:\n");

        if(waitFirst){
            s.append("op add lc_tm lc_tm 1\n");
            s.append("wait ").append(holdWait).append('\n');
            appendHoldBody(s, groups, tokenBase, approachTiles, arrivalTiles, attack, tx, ty);
            s.append("jump done greaterThan lc_tm ").append(Cfg.holdCycles).append('\n');
            if(attack){
                s.append("jump hold always\n");
            }else{
                s.append("jump hold lessThan lc_c lc_n\n");
            }
        }else{
            appendHoldBody(s, groups, tokenBase, approachTiles, arrivalTiles, attack, tx, ty);
            s.append("op add lc_tm lc_tm 1\n");
            s.append("jump done greaterThan lc_tm ").append(Cfg.holdCycles).append('\n');
            if(attack){
                s.append("wait ").append(holdWait).append('\n');
                s.append("jump hold always\n");
            }else{
                s.append("jump done greaterThanEq lc_c lc_n\n");
                s.append("wait ").append(holdWait).append('\n');
                s.append("jump hold always\n");
            }
        }
    }

    private static void appendHoldBody(StringBuilder s, Seq<TypeGroup> groups, long tokenBase,
                                       float approachTiles, float arrivalTiles,
                                       boolean attack, float tx, float ty){
        s.append("set lc_c 0\n");
        s.append("set lc_n 0\n");

        for(int t = 0; t < groups.size; t++){
            TypeGroup group = groups.get(t);
            if(group.units.isEmpty()) continue;

            s.append("set lc_i 0\n");
            s.append("hsw").append(t).append(":\n");
            s.append("ubind @").append(group.name).append('\n');
            s.append("jump hen").append(t).append(" equal @unit null\n");
            s.append("sensor lc_f @unit @flag\n");
            s.append("jump hnx").append(t).append(" lessThan lc_f ").append(tokenBase).append('\n');
            s.append("jump hnx").append(t).append(" greaterThanEq lc_f ")
                .append(tokenBase + TOKEN_SLOTS).append('\n');

            if(attack){
                s.append("ucontrol target ").append(f(tx)).append(' ').append(f(ty)).append(" 1\n");
            }else{
                s.append("op sub lc_s lc_f ").append(tokenBase).append('\n');
                s.append("op idiv lc_r lc_s ").append(group.cols).append('\n');
                s.append("op mod lc_k lc_s ").append(group.cols).append('\n');
                s.append("op mul lc_dx lc_k ").append(f(group.spacingTiles)).append('\n');
                s.append("op add lc_x ").append(f(group.x0)).append(" lc_dx\n");
                s.append("op mul lc_dy lc_r ").append(f(group.spacingTiles)).append('\n');
                s.append("op sub lc_y ").append(f(group.y0)).append(" lc_dy\n");
                appendMoveDynamic(s, approachTiles);
                if(Cfg.attackMove) appendAutoAttack(s, "am" + t + "_n", "am" + t + "_d");
                s.append("op add lc_n lc_n 1\n");
                s.append("ucontrol within lc_x lc_y ").append(f(arrivalTiles)).append(" lc_b\n");
                s.append("op add lc_c lc_c lc_b\n");
            }

            s.append("hnx").append(t).append(":\n");
            s.append("op add lc_i lc_i 1\n");
            s.append("jump hsw").append(t).append(" lessThan lc_i ")
                .append(Math.max(1, group.teamCount)).append('\n');
            s.append("hen").append(t).append(":\n");
        }
    }

    private static void appendCleanup(StringBuilder s, Seq<TypeGroup> groups){
        for(int t = 0; t < groups.size; t++){
            TypeGroup group = groups.get(t);
            if(group.units.isEmpty()) continue;

            s.append("set lc_i 0\n");
            s.append("clr").append(t).append(":\n");
            s.append("ubind @").append(group.name).append('\n');
            s.append("jump cen").append(t).append(" equal @unit null\n");
            s.append("sensor lc_f @unit @flag\n");
            s.append("jump cnx").append(t).append(" lessThan lc_f ").append(TOKEN_MIN).append('\n');
            s.append("jump cnx").append(t).append(" greaterThanEq lc_f ")
                .append(TOKEN_MIN + TOKEN_SPAN).append('\n');
            s.append("ucontrol flag 0\n");
            s.append("cnx").append(t).append(":\n");
            s.append("op add lc_i lc_i 1\n");
            s.append("jump clr").append(t).append(" lessThan lc_i ")
                .append(Math.max(1, group.teamCount)).append('\n');
            s.append("cen").append(t).append(":\n");
        }
    }

    /** Releases tagged units so they are RTS commandable immediately. */
    private static void appendRelease(StringBuilder s, Seq<TypeGroup> groups, long tokenBase){
        for(int t = 0; t < groups.size; t++){
            TypeGroup group = groups.get(t);
            if(group.units.isEmpty()) continue;

            s.append("set lc_i 0\n");
            s.append("rel").append(t).append(":\n");
            s.append("ubind @").append(group.name).append('\n');
            s.append("jump ren").append(t).append(" equal @unit null\n");
            s.append("sensor lc_f @unit @flag\n");
            s.append("jump rnx").append(t).append(" lessThan lc_f ").append(tokenBase).append('\n');
            s.append("jump rnx").append(t).append(" greaterThanEq lc_f ")
                .append(tokenBase + TOKEN_SLOTS).append('\n');
            s.append("ucontrol unbind\n");
            s.append("rnx").append(t).append(":\n");
            s.append("op add lc_i lc_i 1\n");
            s.append("jump rel").append(t).append(" lessThan lc_i ")
                .append(Math.max(1, group.teamCount)).append('\n');
            s.append("ren").append(t).append(":\n");
        }
    }

    // endregion

    private static void appendMoveLiteral(StringBuilder s, float x, float y, float approachTiles){
        if(Cfg.pathfind){
            s.append("ucontrol pathfind ").append(f(x)).append(' ').append(f(y)).append('\n');
        }else{
            s.append("ucontrol approach ").append(f(x)).append(' ').append(f(y)).append(' ')
                .append(f(approachTiles)).append('\n');
        }
    }

    private static void appendMoveDynamic(StringBuilder s, float approachTiles){
        if(Cfg.pathfind){
            s.append("ucontrol pathfind lc_x lc_y\n");
        }else{
            s.append("ucontrol approach lc_x lc_y ").append(f(approachTiles)).append('\n');
        }
    }

    /**
     * Attack-move: acquire the nearest enemy unit through {@code uradar} and shoot it while the
     * movement order keeps running. Buildings cannot be auto-targeted (no general mlog query).
     */
    private static void appendAutoAttack(StringBuilder s, String noneLabel, String doneLabel){
        s.append("uradar enemy any any distance @unit 1 lc_en\n");
        s.append("jump ").append(noneLabel).append(" equal lc_en null\n");
        s.append("ucontrol targetp lc_en 1\n");
        s.append("jump ").append(doneLabel).append(" always\n");
        s.append(noneLabel).append(":\n");
        s.append("ucontrol target 0 0 0\n");
        s.append(doneLabel).append(":\n");
    }

    private static int totalUnits(Seq<TypeGroup> groups){
        int total = 0;
        for(int i = 0; i < groups.size; i++) total += groups.get(i).units.size;
        return total;
    }

    private static int countInstructions(String code){
        int count = 0;
        String[] lines = code.split("\n");
        for(String line : lines){
            String trimmed = line.trim();
            if(trimmed.isEmpty() || trimmed.endsWith(":")) continue;
            count++;
        }
        return count;
    }

    private static String f(float value){
        if(Float.isNaN(value) || Float.isInfinite(value)) value = 0f;
        return String.format(Locale.ROOT, "%.2f", value);
    }
}
