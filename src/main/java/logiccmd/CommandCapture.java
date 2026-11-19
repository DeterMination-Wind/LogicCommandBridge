package logiccmd;

import arc.Core;
import arc.input.InputProcessor;
import arc.input.KeyCode;
import arc.math.geom.Vec2;
import mindustry.gen.Building;
import mindustry.gen.Teamc;
import mindustry.world.Tile;

import static mindustry.Vars.control;
import static mindustry.Vars.net;
import static mindustry.Vars.player;
import static mindustry.Vars.state;
import static mindustry.Vars.world;

/**
 * Sits in front of the vanilla input handler (processor index 0).
 *
 * <p>In port-picking mode it consumes left-drag events and forwards them to {@link Ports}, so
 * a rectangle drag selects the processor area instead of selecting units. In command mode,
 * right clicks are mirrored into mlog before the vanilla command is issued.</p>
 */
public final class CommandCapture implements InputProcessor{

    public static void install(){
        Core.input.getInputMultiplexer().addProcessor(0, new CommandCapture());
    }

    @Override
    public boolean touchDown(int screenX, int screenY, int pointer, KeyCode button){
        if(state == null || !state.isGame()) return false;
        if(player == null || player.dead() || player.unit() == null) return false;
        if(control == null || control.input == null) return false;
        if(Core.scene == null || Core.scene.hasMouse() || Core.scene.hasField() || Core.scene.hasDialog()) return false;

        if(Ports.picking){
            if(button == KeyCode.mouseLeft){
                Tile tile = tileAt(screenX, screenY);
                if(tile != null) Ports.beginDrag(tile.x, tile.y);
                return true;
            }
            if(button == KeyCode.mouseRight){
                Ports.cancelPick();
                return true;
            }
            return false;
        }

        // Only the plain right-click command is mirrored for now; queue/waypoints are
        // intentionally left to vanilla.
        if(button != KeyCode.mouseRight) return false;
        if(!control.input.commandMode) return false;

        Vec2 target = Core.input.mouseWorld(screenX, screenY).cpy();

        Building building = world.buildWorld(target.x, target.y);
        Teamc attack = (building != null && building.team() != player.team()) ? building : null;
        if(attack == null) attack = control.input.selectedEnemyUnit(target.x, target.y);

        LogicCommandFeature.submit(target, attack);

        // Single player: vanilla may still issue its command for this click; it is cleared
        // right before the generated program runs. Multiplayer: the command must never reach
        // the server, otherwise the server-side CommandAI blocks logic unit control.
        boolean bridge = Ports.hasPorts() && state.rules.logicUnitControl
            && (control.input.selectedUnits.size > 0 || !UnitBook.units.isEmpty());

        return Cfg.swallow || (bridge && net.active());
    }

    @Override
    public boolean touchUp(int screenX, int screenY, int pointer, KeyCode button){
        if(!Ports.picking) return false;
        if(button != KeyCode.mouseLeft) return false;

        Tile tile = tileAt(screenX, screenY);
        if(tile != null) Ports.updateDrag(tile.x, tile.y);
        Ports.finishDrag();
        return true;
    }

    @Override
    public boolean touchDragged(int screenX, int screenY, int pointer){
        if(!Ports.picking || !Ports.dragging) return false;

        Tile tile = tileAt(screenX, screenY);
        if(tile != null) Ports.updateDrag(tile.x, tile.y);
        return true;
    }

    private static Tile tileAt(int screenX, int screenY){
        Vec2 pos = Core.input.mouseWorld(screenX, screenY);
        return world.tileWorld(pos.x, pos.y);
    }

    // Arc's InputProcessor methods are default on desktop but stay abstract on some
    // Android builds, so implement them explicitly (same workaround as other mods).

    @Override
    public boolean keyDown(KeyCode keycode){
        return false;
    }

    @Override
    public boolean keyUp(KeyCode keycode){
        return false;
    }

    @Override
    public boolean keyTyped(char character){
        return false;
    }

    @Override
    public boolean mouseMoved(int screenX, int screenY){
        return false;
    }

    @Override
    public boolean scrolled(float amountX, float amountY){
        return false;
    }
}
