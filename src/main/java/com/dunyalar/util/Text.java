package com.dunyalar.util;

import org.bukkit.ChatColor;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Metin yardımcıları. Türkçe sunucu dil ayarında (tr_TR) "I".toLowerCase() = "ı" olduğundan
 * tüm karşılaştırmalar Locale.ROOT ve ASCII normalizasyonu ile yapılır.
 */
public final class Text {

    private static final Pattern HEX = Pattern.compile("&#([0-9a-fA-F]{6})");
    private static volatile boolean hexSupported = true;

    private Text() {
    }

    /** &-renk kodlarını ve &#RRGGBB hex renklerini çevirir. */
    public static String color(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        String s = input;
        if (hexSupported && s.contains("&#")) {
            try {
                Matcher m = HEX.matcher(s);
                StringBuffer sb = new StringBuffer();
                while (m.find()) {
                    String rep = net.md_5.bungee.api.ChatColor.of("#" + m.group(1)).toString();
                    m.appendReplacement(sb, Matcher.quoteReplacement(rep));
                }
                m.appendTail(sb);
                s = sb.toString();
            } catch (Throwable t) {
                // Hex renk desteklenmiyor: kodları sessizce kaldır
                hexSupported = false;
                s = HEX.matcher(input).replaceAll("");
            }
        }
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    /** {anahtar} -> değer değişimi. kv: anahtar1, değer1, anahtar2, değer2 ... */
    public static String replace(String msg, String... kv) {
        if (msg == null) {
            return "";
        }
        String out = msg;
        if (kv != null) {
            for (int i = 0; i + 1 < kv.length; i += 2) {
                String value = kv[i + 1] == null ? "" : kv[i + 1];
                out = out.replace("{" + kv[i] + "}", value);
            }
        }
        return out;
    }

    /** Dil ayarından bağımsız küçük harf. */
    public static String lower(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT);
    }

    /** Türkçe karakterleri ASCII'ye indirip küçük harfe çevirir (komut eşleştirme için). */
    public static String norm(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case 'ı': case 'I': case 'İ': case 'i':
                    b.append('i');
                    break;
                case 'ş': case 'Ş':
                    b.append('s');
                    break;
                case 'ğ': case 'Ğ':
                    b.append('g');
                    break;
                case 'ü': case 'Ü':
                    b.append('u');
                    break;
                case 'ö': case 'Ö':
                    b.append('o');
                    break;
                case 'ç': case 'Ç':
                    b.append('c');
                    break;
                default:
                    b.append(Character.toLowerCase(c));
            }
        }
        return b.toString();
    }

    public static String repeat(char c, int count) {
        if (count <= 0) {
            return "";
        }
        StringBuilder b = new StringBuilder(count);
        for (int i = 0; i < count; i++) {
            b.append(c);
        }
        return b.toString();
    }

    public static String oneDecimal(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }
}
