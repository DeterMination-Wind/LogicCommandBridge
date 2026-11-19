import arc.util.TextFormatter;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Reproduces arc's bundle formatting on every value of the mod bundles, to find a pattern that
 * MessageFormat rejects.
 */
public class BundleFormatCheck{

    public static void main(String[] args) throws Exception{
        check("C:/Users/华硕/Documents/codex/LogicCommandBridge/src/main/resources/bundles/bundle.properties");
        check("C:/Users/华硕/Documents/codex/LogicCommandBridge/src/main/resources/bundles/bundle_zh_CN.properties");
    }

    private static void check(String path) throws Exception{
        Properties props = new Properties();
        try(Reader r = new InputStreamReader(new FileInputStream(path), StandardCharsets.UTF_8)){
            props.load(r);
        }

        TextFormatter formatter = new TextFormatter(Locale.ROOT, true);
        int checked = 0;

        for(String key : props.stringPropertyNames()){
            String value = props.getProperty(key);
            if(value == null || !value.contains("{")) continue;
            checked++;
            try{
                formatter.format(value, "A", 1);
            }catch(Exception e){
                System.out.println("FAIL " + path + " :: " + key + " => " + value);
                System.out.println("     " + e);
            }
        }

        System.out.println("checked " + checked + " value(s) with braces in " + new File(path).getName());
    }
}
