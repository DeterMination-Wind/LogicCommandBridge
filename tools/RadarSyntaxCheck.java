import arc.struct.Seq;
import mindustry.logic.LStatement;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/** Prints which uradar argument order the current parser accepts. */
public class RadarSyntaxCheck{

    public static void main(String[] args) throws Exception{
        String[] candidates = {
            "uradar enemy any any distance @unit 1 result",
            "uradar enemy any any distance 1 @unit result",
            "uradar enemy any any distance @unit result 1",
            "uradar enemy any any distance lc_en 1 @unit"
        };

        for(String code : candidates){
            Class<?> parserClass = Class.forName("mindustry.logic.LParser");
            Constructor<?> constructor = parserClass.getDeclaredConstructor(String.class, boolean.class);
            constructor.setAccessible(true);
            Object parser = constructor.newInstance(code, false);
            Method parse = parserClass.getDeclaredMethod("parse");
            parse.setAccessible(true);
            Seq<LStatement> statements = (Seq<LStatement>)parse.invoke(parser);
            String cls = statements.isEmpty() ? "none" : statements.get(0).getClass().getSimpleName();
            System.out.println((cls.contains("Invalid") ? "INVALID " : "ok      ") + code + "  -> " + cls);
        }
    }
}
