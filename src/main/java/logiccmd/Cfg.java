package logiccmd;

import arc.Core;
import arc.input.KeyBind;
import arc.input.KeyCode;
import mindustry.content.Blocks;
import mindustry.ui.dialogs.SettingsMenuDialog;
import mindustry.world.Block;

/**
 * All persisted settings, hotkeys and the processor block pick.
 */
public final class Cfg{

    public static final String K_TYPE = "logiccmd-processor-type";
    public static final String K_AUTO_BUILD = "logiccmd-auto-build";
    public static final String K_HOLD = "logiccmd-hold";
    public static final String K_HOLD_WAIT = "logiccmd-hold-wait";
    public static final String K_HOLD_CYCLES = "logiccmd-hold-cycles";
    public static final String K_MAX_UNITS = "logiccmd-max-units";
    public static final String K_MAX_PROCESSORS = "logiccmd-max-processors";
    public static final String K_SPACING = "logiccmd-spacing";
    public static final String K_MP_TOLERANCE = "logiccmd-mp-tolerance";
    public static final String K_MP_PAD = "logiccmd-mp-pad";
    public static final String K_MP_CLEANUP = "logiccmd-mp-flag-cleanup";
    public static final String K_MP_REUSE = "logiccmd-mp-flag-reuse";
    public static final String K_PATHFIND = "logiccmd-pathfind";
    public static final String K_FORMATION = "logiccmd-formation-crowd";
    public static final String K_ATTACK_MOVE = "logiccmd-attack-move";
    public static final String K_DEBUG = "logiccmd-debug";
    public static final String K_SWALLOW = "logiccmd-swallow";
    public static final String K_HINTS = "logiccmd-hints";

    public static KeyBind keyBind;
    public static KeyBind keyRelease;
    public static KeyBind keyUnbind;
    public static KeyBind keyPreview;
    public static KeyBind keyPrevGroup;
    public static KeyBind keyNextGroup;

    public static int processorType = 0;
    public static boolean autoBuild = true;
    public static boolean hold = true;
    public static boolean swallow = false;
    public static boolean hints = true;
    public static boolean mpFlagCleanup = true;
    public static boolean mpReuse = true;
    public static boolean pathfind = true;
    public static boolean crowdArrival = false;
    public static boolean attackMove = true;
    public static boolean debug = false;
    public static int holdWaitSec = 5;
    public static int holdCycles = 24;
    public static int maxUnits = 80;
    public static int maxProcessors = 8;
    public static int spacingPercent = 0;
    /** Position match radius in percent of a tile (200 = 2.00 tiles). */
    public static int mpTolerancePercent = 200;
    /** Extra tiles around the group when pre-filtering the sweep. */
    public static int mpPadTiles = 3;

    private static boolean registered;
    private static boolean defaultsApplied;

    private Cfg(){
    }

    public static float mpToleranceTiles(){
        return mpTolerancePercent / 100f;
    }

    public static void load(){
        applyDefaults();

        processorType = clamp(Core.settings.getInt(K_TYPE, 0), 0, 3);
        autoBuild = Core.settings.getBool(K_AUTO_BUILD, true);
        hold = Core.settings.getBool(K_HOLD, true);
        swallow = Core.settings.getBool(K_SWALLOW, false);
        hints = Core.settings.getBool(K_HINTS, true);
        mpFlagCleanup = Core.settings.getBool(K_MP_CLEANUP, true);
        mpReuse = Core.settings.getBool(K_MP_REUSE, true);
        pathfind = Core.settings.getBool(K_PATHFIND, true);
        crowdArrival = Core.settings.getBool(K_FORMATION, false);
        attackMove = Core.settings.getBool(K_ATTACK_MOVE, true);
        debug = Core.settings.getBool(K_DEBUG, false);
        holdWaitSec = clamp(Core.settings.getInt(K_HOLD_WAIT, 5), 1, 15);
        holdCycles = clamp(Core.settings.getInt(K_HOLD_CYCLES, 24), 1, 600);
        maxUnits = clamp(Core.settings.getInt(K_MAX_UNITS, 80), 1, 300);
        maxProcessors = clamp(Core.settings.getInt(K_MAX_PROCESSORS, 8), 1, 16);
        spacingPercent = clamp(Core.settings.getInt(K_SPACING, 0), 0, 400);
        mpTolerancePercent = clamp(Core.settings.getInt(K_MP_TOLERANCE, 200), 100, 800);
        mpPadTiles = clamp(Core.settings.getInt(K_MP_PAD, 3), 0, 12);
    }

    private static void applyDefaults(){
        if(defaultsApplied) return;
        defaultsApplied = true;

        Core.settings.defaults(K_TYPE, 0);
        Core.settings.defaults(K_AUTO_BUILD, true);
        Core.settings.defaults(K_HOLD, true);
        Core.settings.defaults(K_HOLD_WAIT, 5);
        Core.settings.defaults(K_HOLD_CYCLES, 24);
        Core.settings.defaults(K_MAX_UNITS, 80);
        Core.settings.defaults(K_MAX_PROCESSORS, 8);
        Core.settings.defaults(K_SPACING, 0);
        Core.settings.defaults(K_MP_TOLERANCE, 200);
        Core.settings.defaults(K_MP_PAD, 3);
        Core.settings.defaults(K_MP_CLEANUP, true);
        Core.settings.defaults(K_MP_REUSE, true);
        Core.settings.defaults(K_PATHFIND, true);
        Core.settings.defaults(K_FORMATION, false);
        Core.settings.defaults(K_ATTACK_MOVE, true);
        Core.settings.defaults(K_DEBUG, false);
        Core.settings.defaults(K_SWALLOW, false);
        Core.settings.defaults(K_HINTS, true);
    }

    public static void registerKeybinds(){
        if(registered) return;
        registered = true;

        keyBind = KeyBind.add("logiccmd-bind", KeyCode.unset, "logiccmd");
        keyRelease = KeyBind.add("logiccmd-release", KeyCode.unset, "logiccmd");
        keyUnbind = KeyBind.add("logiccmd-unbind", KeyCode.unset, "logiccmd");
        keyPreview = KeyBind.add("logiccmd-preview", KeyCode.unset, "logiccmd");
        keyPrevGroup = KeyBind.add("logiccmd-prev-group", KeyCode.unset, "logiccmd");
        keyNextGroup = KeyBind.add("logiccmd-next-group", KeyCode.unset, "logiccmd");
    }

    /** Processor block used for new ports. */
    public static Block processorBlock(){
        if(processorType == 3) return Blocks.worldProcessor;
        if(processorType == 2) return Blocks.hyperProcessor;
        if(processorType == 1) return Blocks.logicProcessor;
        return Blocks.microProcessor;
    }

    public static void buildSettings(SettingsMenuDialog.SettingsTable table){
        load();

        table.sliderPref(K_TYPE, processorType, 0, 3,
            i -> Core.bundle.get("logiccmd.type." + i),
            i -> processorType = i);
        table.checkPref(K_AUTO_BUILD, autoBuild, b -> autoBuild = b);
        table.checkPref(K_HOLD, hold, b -> hold = b);
        table.sliderPref(K_HOLD_WAIT, holdWaitSec, 1, 15, i -> Core.bundle.format("logiccmd.unit.seconds", i), i -> holdWaitSec = i);
        table.sliderPref(K_HOLD_CYCLES, holdCycles, 1, 120, i -> String.valueOf(i), i -> holdCycles = i);
        table.sliderPref(K_MAX_UNITS, maxUnits, 1, 300, i -> String.valueOf(i), i -> maxUnits = i);
        table.sliderPref(K_MAX_PROCESSORS, maxProcessors, 1, 16, i -> String.valueOf(i), i -> maxProcessors = i);
        table.sliderPref(K_SPACING, spacingPercent, 0, 400, i -> i == 0 ? Core.bundle.get("logiccmd.unit.auto") : Core.bundle.format("logiccmd.unit.percent", i), i -> spacingPercent = i);
        table.sliderPref(K_MP_TOLERANCE, mpTolerancePercent, 100, 800,
            i -> Core.bundle.format("logiccmd.unit.tiles", String.format(java.util.Locale.ROOT, "%.2f", i / 100f)), i -> mpTolerancePercent = i);
        table.sliderPref(K_MP_PAD, mpPadTiles, 0, 12, i -> Core.bundle.format("logiccmd.unit.tiles", i), i -> mpPadTiles = i);
        table.checkPref(K_MP_CLEANUP, mpFlagCleanup, b -> mpFlagCleanup = b);
        table.checkPref(K_MP_REUSE, mpReuse, b -> mpReuse = b);
        table.checkPref(K_FORMATION, crowdArrival, b -> crowdArrival = b);
        table.checkPref(K_ATTACK_MOVE, attackMove, b -> attackMove = b);
        table.checkPref(K_PATHFIND, pathfind, b -> pathfind = b);
        table.checkPref(K_SWALLOW, swallow, b -> swallow = b);
        table.checkPref(K_DEBUG, debug, b -> debug = b);
        table.checkPref(K_HINTS, hints, b -> hints = b);
    }

    private static int clamp(int value, int min, int max){
        return Math.max(min, Math.min(max, value));
    }
}
