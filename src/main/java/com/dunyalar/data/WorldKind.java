package com.dunyalar.data;

import com.dunyalar.util.Text;

/** Dünya üretim tipi. */
public enum WorldKind {

    NORMAL("normal"),
    VOID("void"),
    FLAT("flat");

    private final String key;

    WorldKind(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    public static WorldKind parse(String input) {
        String n = Text.norm(input);
        switch (n) {
            case "normal":
            case "default":
                return NORMAL;
            case "void":
            case "bos":
            case "empty":
                return VOID;
            case "flat":
            case "duz":
                return FLAT;
            default:
                return null;
        }
    }
}
