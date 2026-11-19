import arc.math.geom.Vec2;
import arc.struct.Seq;
import mindustry.logic.LStatement;
import logiccmd.Cfg;
import logiccmd.Mlog;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/**
 * Offline syntax check: generates mlog with the mod and feeds it back into Mindustry's own
 * parser (which also resolves jump labels) to make sure every statement parses.
 *
 * Run: java -cp "<core-release.jar>;<arc-core.jar>;build/classes/java/main" tools/MlogSyntaxCheck.java
 */
public class MlogSyntaxCheck{

    public static void main(String[] args) throws Exception{
        Cfg.hold = true;
        Cfg.pathfind = true;
        Mlog.holdWait = 5;

        // ---- single player: injected unit constants ----
        Seq<mindustry.gen.Unit> units = new Seq<>();
        units.add((mindustry.gen.Unit)null);
        units.add((mindustry.gen.Unit)null);

        Vec2[] dests = new Vec2[]{
            new Vec2(100f, 200f),
            new Vec2(180f, 240f)
        };

        check("sp moveExact", Mlog.moveExact(units, dests, 0.8f, 2f).code);
        check("sp attackExact", Mlog.attackExact(units, null, dests, 0.8f).code);

        // ---- multiplayer: position match + flag token ----
        check("mp moveMatch", Mlog.moveMatch(fakeGroups(false), Mlog.TOKEN_MIN,
            2f, 0.8f, 2f, true, true).code);
        check("mp attackMatch", Mlog.attackMatch(fakeGroups(false), Mlog.TOKEN_MIN,
            300f, 200f, 2f, 0.8f, true, true).code);
        check("mp moveAllOfType", Mlog.moveAllOfType(fakeGroups(true), Mlog.TOKEN_MIN,
            0.8f, 2f, true, true).code);
        check("mp moveFlagOnly", Mlog.moveFlagOnly(fakeGroups(false), Mlog.TOKEN_MIN,
            0.8f, 2f).code);

        // ---- approach mode (pathfinding disabled) ----
        Cfg.pathfind = false;
        check("mp moveMatch(approach)", Mlog.moveMatch(fakeGroups(false), Mlog.TOKEN_MIN,
            2f, 0.8f, 2f, true, true).code);
        Cfg.pathfind = true;

        // ---- release programs ----
        check("sp releaseExact", Mlog.releaseExact(units).code);
        check("mp releaseFlags", Mlog.releaseFlags(fakeGroups(false)).code);

        // ---- one shot mode (no hold loop) ----
        Cfg.hold = false;
        check("sp moveExact(one-shot)", Mlog.moveExact(units, dests, 0.8f, 2f).code);
        check("sp attackExact(one-shot)", Mlog.attackExact(units, null, dests, 0.8f).code);
        check("mp moveMatch(one-shot)", Mlog.moveMatch(fakeGroups(false), Mlog.TOKEN_MIN,
            2f, 0.8f, 2f, true, false).code);
    }

    private static Seq<Mlog.TypeGroup> fakeGroups(boolean allOfType){
        Seq<Mlog.TypeGroup> groups = new Seq<>();

        Mlog.TypeGroup daggers = new Mlog.TypeGroup();
        daggers.name = "dagger";
        daggers.teamCount = allOfType ? 3 : 12;
        daggers.prefilterTiles = allOfType ? 0f : 9f;
        daggers.prefilterCx = 120f;
        daggers.prefilterCy = 88f;
        daggers.cols = 2;
        daggers.x0 = 14f;
        daggers.y0 = 12f;
        daggers.spacingTiles = 2f;
        for(int i = 0; i < 3; i++){
            daggers.units.add(matchUnit(i, 100f + i * 8f, 80f + i * 6f));
        }
        groups.add(daggers);

        Mlog.TypeGroup flares = new Mlog.TypeGroup();
        flares.name = "flare";
        flares.teamCount = 4;
        flares.prefilterCx = 120f;
        flares.prefilterCy = 88f;
        flares.cols = 1;
        flares.x0 = 15f;
        flares.y0 = 11f;
        flares.spacingTiles = 0f;
        flares.units.add(matchUnit(0, 118f, 96f));
        groups.add(flares);

        return groups;
    }

    private static Mlog.MatchUnit matchUnit(int slot, float x, float y){
        Mlog.MatchUnit unit = new Mlog.MatchUnit();
        unit.type = "dagger";
        unit.x = x;
        unit.y = y;
        unit.slot = slot;
        unit.dest = new Vec2(120f + (slot % 2 - 0.5f) * 16f, 88f + (0.5f - slot / 2) * 16f);
        return unit;
    }

    @SuppressWarnings("unchecked")
    private static void check(String name, String code) throws Exception{
        System.out.println("==== " + name + " ====");
        System.out.println(code);

        Class<?> parserClass = Class.forName("mindustry.logic.LParser");
        Constructor<?> constructor = parserClass.getDeclaredConstructor(String.class, boolean.class);
        constructor.setAccessible(true);
        Object parser = constructor.newInstance(code, false);

        Method parse = parserClass.getDeclaredMethod("parse");
        parse.setAccessible(true);
        Seq<LStatement> statements = (Seq<LStatement>)parse.invoke(parser);

        int invalid = 0;
        for(int i = 0; i < statements.size; i++){
            if(statements.get(i).getClass().getName().contains("InvalidStatement")) invalid++;
        }

        System.out.println("[ok] statements=" + statements.size + " invalid=" + invalid);
        if(invalid > 0) throw new IllegalStateException(name + " produced " + invalid + " invalid statement(s)");
    }
}
