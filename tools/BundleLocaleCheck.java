import arc.files.Fi;
import arc.util.I18NBundle;

import java.util.Locale;

/** Loads the mod bundles the same way Mindustry does and prints a few resolved strings. */
public class BundleLocaleCheck{

    public static void main(String[] args){
        String dir = "src/main/resources/bundles/";

        Fi base = new Fi(dir + "bundle");

        I18NBundle root = I18NBundle.createBundle(base, Locale.ROOT, "UTF-8");
        I18NBundle cn = I18NBundle.createBundle(base, Locale.SIMPLIFIED_CHINESE, "UTF-8");

        System.out.println("root: " + root.get("logiccmd-max-processors"));
        System.out.println("cn  : " + cn.get("logiccmd-max-processors"));
        System.out.println("cn  : " + cn.get("logiccmd.pick.start"));
        System.out.println("cn  : " + cn.format("logiccmd.sent.move", "12", "2"));
        System.out.println("cn  : " + cn.get("category.logiccmd.name"));
        System.out.println("cn  : " + cn.get("keybind.logiccmd-next-group.name"));
    }
}
