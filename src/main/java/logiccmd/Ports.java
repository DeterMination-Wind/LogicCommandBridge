package logiccmd;

import arc.Core;
import arc.math.geom.Vec2;
import arc.struct.Seq;
import mindustry.entities.units.BuildPlan;
import mindustry.gen.Building;
import mindustry.world.Block;
import mindustry.world.Build;
import mindustry.world.Tile;
import mindustry.world.blocks.ConstructBlock;
import mindustry.world.blocks.logic.LogicBlock;
import mindustry.world.blocks.logic.LogicBlock.LogicBuild;

import static mindustry.Vars.*;

/**
 * The "port": a rectangle of processors that every RTS command is routed into.
 *
 * <p>The player presses the bind hotkey, drags a rectangle in the world, and this class tiles
 * that rectangle with processors of the configured type (micro 2x2 / medium 3x3 / large 4x4),
 * clamping the count to {@link Cfg#maxProcessors}.</p>
 */
public final class Ports{

    public static final class Port{
        public final int x, y;
        public final Block block;

        Port(int x, int y, Block block){
            this.x = x;
            this.y = y;
            this.block = block;
        }

        public String label(){
            return x + ", " + y;
        }
    }

    public static final Seq<Port> ports = new Seq<>();

    public static boolean picking;
    public static boolean dragging;
    public static int startX = -1, startY = -1;
    public static int endX = -1, endY = -1;

    private Ports(){
    }

    public static void beginPick(){
        picking = true;
        dragging = false;
        startX = startY = endX = endY = -1;
        LogicCommandFeature.hint("logiccmd.pick.start");
    }

    public static void beginDrag(int tileX, int tileY){
        if(!picking) return;
        dragging = true;
        startX = endX = tileX;
        startY = endY = tileY;
    }

    public static void updateDrag(int tileX, int tileY){
        if(!picking || !dragging) return;
        endX = tileX;
        endY = tileY;
    }

    public static void cancelPick(){
        if(!picking) return;
        picking = false;
        dragging = false;
        startX = startY = endX = endY = -1;
        LogicCommandFeature.hint("logiccmd.pick.cancelled");
    }

    public static void clearPorts(){
        ports.clear();
        UnitBook.clear();
    }

    public static boolean hasPorts(){
        return !ports.isEmpty();
    }

    /** Resolves a port's logic build, or null when it is gone/replaced. */
    public static LogicBuild build(Port port){
        Building b = world.build(port.x, port.y);
        if(b instanceof LogicBuild && b.block == port.block) return (LogicBuild)b;
        return null;
    }

    /** True while at least one port is still a construction site. */
    public static boolean building(){
        for(int i = 0; i < ports.size; i++){
            Port port = ports.get(i);
            Building b = world.build(port.x, port.y);
            if(b instanceof ConstructBlock.ConstructBuild) return true;
        }
        return false;
    }

    public static boolean ready(Port port){
        LogicBuild b = build(port);
        if(b == null || !b.isValid()) return false;
        return b.enabled && ((LogicBlock)b.block).accessible();
    }

    /** Ports that are fully built and usable right now. */
    public static Seq<Port> readyPorts(){
        Seq<Port> out = new Seq<>();
        for(int i = 0; i < ports.size; i++){
            if(ready(ports.get(i))) out.add(ports.get(i));
        }
        return out;
    }

    /** Completes the drag: places processors across the selected rectangle. */
    public static void finishDrag(){
        if(!picking) return;
        picking = false;
        dragging = false;

        if(startX < 0 || startY < 0){
            LogicCommandFeature.hint("logiccmd.pick.cancelled");
            return;
        }

        Block block = Cfg.processorBlock();
        int size = block.size;
        int minX = Math.min(startX, endX);
        int minY = Math.min(startY, endY);
        int maxX = Math.max(startX, endX);
        int maxY = Math.max(startY, endY);

        int cols = Math.max(1, (maxX - minX + 1) / size);
        int rows = Math.max(1, (maxY - minY + 1) / size);

        Seq<Port> placed = new Seq<>();
        int skipped = 0;

        outer:
        for(int ry = 0; ry < rows; ry++){
            for(int rx = 0; rx < cols; rx++){
                int px = minX + rx * size;
                int py = minY + ry * size;

                if(placed.size >= Cfg.maxProcessors) break outer;

                Port existing = findPort(px, py);
                if(existing != null){
                    placed.add(existing);
                    continue;
                }

                String error = check(px, py, block);
                if(error != null){
                    skipped++;
                    continue;
                }

                if(!place(px, py, block)){
                    skipped++;
                    continue;
                }

                placed.add(new Port(px, py, block));
            }
        }

        if(placed.isEmpty()){
            LogicCommandFeature.fail(skipped > 0 ? "invalidPlace" : "outOfMap");
            return;
        }

        ports.clear();
        ports.addAll(placed);
        UnitBook.clear();
        for(int i = 0; i < ports.size; i++) UnitBook.bind(ports.get(i).x, ports.get(i).y);

        LogicCommandFeature.placed(ports.size, skipped);
    }

    private static Port findPort(int x, int y){
        for(int i = 0; i < ports.size; i++){
            Port port = ports.get(i);
            if(port.x == x && port.y == y) return port;
        }
        return null;
    }

    private static boolean place(int x, int y, Block block){
        Building existing = world.build(x, y);
        if(existing instanceof LogicBuild) return true;

        if(!Cfg.autoBuild) return false;

        if(net.active()){
            player.unit().addBuild(new BuildPlan(x, y, 0, block));
        }else{
            Tile tile = world.tile(x, y);
            if(tile == null) return false;
            tile.setBlock(block, player.team(), 0);
        }
        return true;
    }

    /** @return null when the tile can host the given processor, otherwise an error code. */
    public static String check(int x, int y, Block block){        if(state == null || !state.isGame()) return "notPlaying";
        if(player == null || player.dead() || player.unit() == null) return "noPlayer";
        if(block == null) return "noBlock";
        if(block.privileged && !worldProcessorAllowed()) return "worldLocked";
        if(block.privileged && state.rules.disableWorldProcessors) return "worldDisabled";
        if(block.isBanned()) return "blockBanned";
        if(!block.unlockedNow()) return "blockLocked";
        if(!block.environmentBuildable()) return "badEnv";

        Tile tile = world.tile(x, y);
        if(tile == null) return "outOfMap";

        Building existing = world.build(x, y);
        if(existing != null){
            if(existing instanceof LogicBuild) return null;
            return "occupied";
        }

        if(!Build.validPlace(block, player.team(), x, y, 0)) return "invalidPlace";

        if(!state.rules.infiniteResources){
            float cx = (x + block.size / 2f) * tilesize;
            float cy = (y + block.size / 2f) * tilesize;
            if(!player.unit().within(cx, cy, player.unit().type.buildRange)) return "outOfRange";
        }

        return null;
    }

    /** Tile under the mouse, using the live cursor instead of event coordinates. */
    public static Tile mouseTile(){
        return world.tileWorld(Core.input.mouseWorldX(), Core.input.mouseWorldY());
    }

    /** World processors need editor mode or the allowEditWorldProcessors rule. */
    public static boolean worldProcessorAllowed(){
        return state != null && (state.rules.editor || state.playtestingMap != null
            || state.rules.allowEditWorldProcessors);
    }

    /** Updates the drag preview from the current mouse tile. */
    public static void updateDragFromMouse(){
        if(!picking) return;
        Tile tile = world.tileWorld(Core.input.mouseWorldX(), Core.input.mouseWorldY());
        if(tile == null) return;
        if(dragging){
            updateDrag(tile.x, tile.y);
        }
    }

    /** Tile rectangle currently being dragged, for drawing. */
    public static void dragRect(Vec2 outMin, Vec2 outMax){
        int minX = Math.min(startX, endX), minY = Math.min(startY, endY);
        int maxX = Math.max(startX, endX), maxY = Math.max(startY, endY);
        outMin.set(minX, minY);
        outMax.set(maxX, maxY);
    }
}
