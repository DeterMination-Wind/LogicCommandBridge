package logiccmd;

import arc.Events;
import mindustry.game.EventType;
import mindustry.gen.Icon;
import mindustry.mod.Mod;

import static mindustry.Vars.headless;
import static mindustry.Vars.ui;

public class LogicCommandMod extends Mod{

    @Override
    public void init(){
        Cfg.load();

        if(headless) return;

        Cfg.registerKeybinds();
        CommandCapture.install();

        Events.run(EventType.Trigger.update, LogicCommandFeature::update);
        Events.run(EventType.Trigger.draw, LogicCommandFeature::draw);
        Events.on(EventType.WorldLoadEvent.class, e -> LogicCommandFeature.resetState());

        if(ui != null){
            ui.settings.addCategory("@settings.logiccmd", Icon.logicSmall, Cfg::buildSettings);
        }
    }
}
