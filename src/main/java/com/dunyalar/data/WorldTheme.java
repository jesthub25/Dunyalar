package com.dunyalar.data;

import com.dunyalar.util.Text;
import org.bukkit.World;

/** Dünya teması: ortam (gökyüzü, sis, ses, fizik) bunu belirler. */
public enum WorldTheme {

    WORLD("world", World.Environment.NORMAL),
    NETHER("nether", World.Environment.NETHER),
    END("end", World.Environment.THE_END);

    private final String key;
    private final World.Environment environment;

    WorldTheme(String key, World.Environment environment) {
        this.key = key;
        this.environment = environment;
    }

    public String key() {
        return key;
    }

    public World.Environment environment() {
        return environment;
    }

    public static WorldTheme parse(String input) {
        String n = Text.norm(input);
        switch (n) {
            case "world":
            case "overworld":
            case "dunya":
            case "normal":
                return WORLD;
            case "nether":
                return NETHER;
            case "end":
            case "the_end":
                return END;
            default:
                return null;
        }
    }
}
