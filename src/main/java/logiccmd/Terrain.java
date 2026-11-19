package logiccmd;

import arc.math.geom.Vec2;
import mindustry.type.UnitType;
import mindustry.world.Tile;

import static mindustry.Vars.tilesize;
import static mindustry.Vars.world;

/**
 * Terrain reachability: naval units need water, ground units need land, flying units go
 * anywhere. Destinations are clamped to the nearest tile a unit type can actually occupy,
 * otherwise a naval unit ordered onto land just sits at the shoreline.
 */
public final class Terrain{

    private Terrain(){
    }

    public static boolean standable(UnitType type, Tile tile){
        if(tile == null) return false;
        if(type.flying) return true;
        if(tile.solid()) return false;
        if(type.naval) return tile.floor().isLiquid;
        // Legs / mechs cannot cross liquids at all.
        return !tile.floor().isLiquid;
    }

    public static boolean standable(UnitType type, float wx, float wy){
        return standable(type, world.tileWorld(wx, wy));
    }

    /** Nearest tile centre this unit type can occupy, or null when none is found. */
    public static Vec2 nearest(UnitType type, float wx, float wy, int maxTiles){
        int cx = (int)(wx / tilesize);
        int cy = (int)(wy / tilesize);

        if(standable(type, world.tile(cx, cy))){
            return new Vec2((cx + 0.5f) * tilesize, (cy + 0.5f) * tilesize);
        }

        for(int r = 1; r <= maxTiles; r++){
            for(int dx = -r; dx <= r; dx++){
                Vec2 found = check(type, cx + dx, cy - r);
                if(found == null) found = check(type, cx + dx, cy + r);
                if(found != null) return found;
            }
            for(int dy = -r + 1; dy <= r - 1; dy++){
                Vec2 found = check(type, cx - r, cy + dy);
                if(found == null) found = check(type, cx + r, cy + dy);
                if(found != null) return found;
            }
        }

        return null;
    }

    private static Vec2 check(UnitType type, int x, int y){
        Tile tile = world.tile(x, y);
        if(tile != null && standable(type, tile)){
            return new Vec2((x + 0.5f) * tilesize, (y + 0.5f) * tilesize);
        }
        return null;
    }
}
