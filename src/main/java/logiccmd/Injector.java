package logiccmd;

import arc.func.Cons;
import mindustry.gen.Unit;
import mindustry.logic.LAssembler;
import mindustry.world.blocks.logic.LogicBlock;
import mindustry.world.blocks.logic.LogicBlock.LogicBuild;

import static mindustry.Vars.net;

/**
 * Writes generated mlog into one processor of the port area and restarts it immediately.
 */
public final class Injector{

    private Injector(){
    }

    public static boolean apply(Ports.Port port, Mlog.Gen gen){
        LogicBuild build = Ports.build(port);
        if(build == null) return false;

        LogicBlock block = (LogicBlock)build.block;
        if(!block.accessible() || !build.enabled) return false;

        if(net.active()){
            // Multiplayer: the server owns the simulation, so ship the plain text code.
            build.configure(LogicBlock.compress(gen.code, build.links));
            return true;
        }

        injectLocal(build, gen);
        return true;
    }

    /**
     * Single player: inject real unit / target objects as constants so the program
     * addresses exactly the box-selected units instead of sweeping whole unit types.
     */
    private static void injectLocal(LogicBuild build, Mlog.Gen gen){
        final Mlog.Gen target = gen;

        build.updateCode(gen.code, false, new Cons<LAssembler>(){
            @Override
            public void get(LAssembler asm){
                for(int i = 0; i < target.units.size; i++){
                    Unit unit = target.units.get(i);
                    if(unit != null && unit.isValid()){
                        asm.putConst("lc_u" + i, unit);
                    }
                }
                if(target.target != null){
                    asm.putConst("lc_t", target.target);
                }
            }
        });

        // Drop any leftover instruction credit so the program starts on the next tick.
        build.accumulator = 0f;
    }
}
