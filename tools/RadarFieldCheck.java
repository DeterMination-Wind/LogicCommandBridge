import arc.struct.Seq;
import mindustry.logic.LStatement;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Parses a uradar line and dumps the resulting statement fields. */
public class RadarFieldCheck{

    public static void main(String[] args) throws Exception{
        String[] candidates = {
            "uradar enemy any any distance 1 result",
            "uradar enemy any any distance @unit 1 result",
            "uradar enemy any any distance 1 @unit result"
        };

        for(String code : candidates){
            Class<?> parserClass = Class.forName("mindustry.logic.LParser");
            Constructor<?> constructor = parserClass.getDeclaredConstructor(String.class, boolean.class);
            constructor.setAccessible(true);
            Object parser = constructor.newInstance(code, false);
            Method parse = parserClass.getDeclaredMethod("parse");
            parse.setAccessible(true);
            Seq<?> statements = (Seq<?>)parse.invoke(parser);
            Object st = statements.first();

            System.out.println("== " + code + " -> " + st.getClass().getSimpleName());
            Class<?> c = st.getClass();
            while(c != null && c != Object.class){
                for(Field f : c.getDeclaredFields()){
                    if(java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
                    if(f.getType().isPrimitive() || f.getType() == String.class){
                        f.setAccessible(true);
                        System.out.println("   " + f.getName() + " = " + f.get(st));
                    }
                }
                c = c.getSuperclass();
            }
        }
    }
}
