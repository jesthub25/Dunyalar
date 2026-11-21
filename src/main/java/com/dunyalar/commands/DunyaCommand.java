package com.dunyalar.commands;

import com.dunyalar.Dunyalar;
import com.dunyalar.config.Settings;
import com.dunyalar.data.WorldData;
import com.dunyalar.data.WorldKind;
import com.dunyalar.data.WorldTheme;
import com.dunyalar.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * /dünya oluştur <isim> <world|nether|end> <normal|void|flat> <XxZ>
 * /dünya sil <dünya>
 * /dünya setspawn <dünya>
 * /dünya ışınlan <dünya>
 * /dünya liste
 * /dünya reload
 */
public final class DunyaCommand implements TabExecutor {

    public static final String PERM_USE = "dunyalar.use";

    private static final Pattern SIZE = Pattern.compile("^(\\d{1,9})[xX](\\d{1,9})$");
    private static final List<String> THEMES = Arrays.asList("world", "nether", "end");
    private static final List<String> KINDS = Arrays.asList("normal", "void", "flat");
    private static final List<String> SIZE_EXAMPLE = Collections.singletonList("100x100");

    private enum Sub {
        CREATE("oluştur", "dunyalar.olustur", "yardim-olustur", "olustur", "create"),
        DELETE("sil", "dunyalar.sil", "yardim-sil", "delete"),
        SETSPAWN("setspawn", "dunyalar.setspawn", "yardim-setspawn"),
        TELEPORT("ışınlan", "dunyalar.isinlan", "yardim-isinlan", "isinlan", "tp", "git"),
        LIST("liste", "dunyalar.liste", "yardim-liste", "list"),
        RELOAD("reload", "dunyalar.reload", "yardim-reload", "yenile");

        final String label;
        final String perm;
        final String help;
        final List<String> keys = new ArrayList<String>();

        Sub(String label, String perm, String help, String... aliases) {
            this.label = label;
            this.perm = perm;
            this.help = help;
            keys.add(Text.norm(label));
            for (String a : aliases) {
                keys.add(Text.norm(a));
            }
        }

        static Sub from(String input) {
            String n = Text.norm(input);
            for (Sub s : values()) {
                if (s.keys.contains(n)) {
                    return s;
                }
            }
            return null;
        }
    }

    private final Dunyalar plugin;

    public DunyaCommand(Dunyalar plugin) {
        this.plugin = plugin;
    }

    private Settings s() {
        return plugin.settings();
    }

    // =================================================================== çalıştırma

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        try {
            execute(sender, args);
        } catch (Throwable t) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Komut çalıştırılırken hata: /" + label + " " + String.join(" ", args), t);
            sender.sendMessage(s().msg("komut-hatasi"));
        }
        return true;
    }

    private void execute(CommandSender sender, String[] args) {
        if (!sender.hasPermission(PERM_USE)) {
            sender.sendMessage(s().msg("yetki-yok"));
            return;
        }
        if (args.length == 0) {
            help(sender);
            return;
        }
        Sub sub = Sub.from(args[0]);
        if (sub == null) {
            sender.sendMessage(s().msg("bilinmeyen-alt-komut"));
            return;
        }
        if (!sender.hasPermission(sub.perm)) {
            sender.sendMessage(s().msg("yetki-yok"));
            return;
        }
        switch (sub) {
            case CREATE:
                create(sender, args);
                break;
            case DELETE:
                delete(sender, args);
                break;
            case SETSPAWN:
                setSpawn(sender, args);
                break;
            case TELEPORT:
                teleport(sender, args);
                break;
            case LIST:
                list(sender);
                break;
            case RELOAD:
                reload(sender);
                break;
            default:
                help(sender);
        }
    }

    private void create(CommandSender sender, String[] args) {
        Settings s = s();
        if (args.length != 5) {
            sender.sendMessage(s.msg("kullanim", "kullanim", "/dünya oluştur <isim> <world|nether|end> <normal|void|flat> <XxZ>"));
            return;
        }
        String name = args[1];
        if (plugin.worlds().isBusy()) {
            sender.sendMessage(s.msg("islem-suruyor", "dunya", plugin.worlds().currentName()));
            return;
        }
        String problem = plugin.worlds().checkName(name);
        if (problem != null) {
            sender.sendMessage(s.msg(problem, "dunya", name));
            return;
        }
        WorldTheme theme = WorldTheme.parse(args[2]);
        if (theme == null) {
            sender.sendMessage(s.msg("gecersiz-tema", "deger", args[2]));
            return;
        }
        WorldKind kind = WorldKind.parse(args[3]);
        if (kind == null) {
            sender.sendMessage(s.msg("gecersiz-tip", "deger", args[3]));
            return;
        }
        Matcher m = SIZE.matcher(args[4].trim());
        if (!m.matches()) {
            sender.sendMessage(s.msg("gecersiz-boyut", "deger", args[4]));
            return;
        }
        long a = Long.parseLong(m.group(1));
        long b = Long.parseLong(m.group(2));
        if (a < s.minSize() || b < s.minSize()) {
            sender.sendMessage(s.msg("boyut-kucuk", "min", String.valueOf(s.minSize())));
            return;
        }
        if (a > s.maxSize() || b > s.maxSize()) {
            sender.sendMessage(s.msg("boyut-buyuk", "max", String.valueOf(s.maxSize())));
            return;
        }
        long rx = s.radiusMode() ? a : Math.max(1L, a / 2L);
        long rz = s.radiusMode() ? b : Math.max(1L, b / 2L);
        if (rx > Settings.ABSOLUTE_MAX_RADIUS || rz > Settings.ABSOLUTE_MAX_RADIUS) {
            sender.sendMessage(s.msg("boyut-buyuk", "max", String.valueOf(Settings.ABSOLUTE_MAX_RADIUS)));
            return;
        }
        plugin.worlds().create(sender, name, theme, kind, (int) rx, (int) rz, a + "x" + b);
    }

    private void delete(CommandSender sender, String[] args) {
        if (args.length != 2) {
            sender.sendMessage(s().msg("kullanim", "kullanim", "/dünya sil <dünya>"));
            return;
        }
        WorldData d = plugin.registry().get(args[1]);
        if (d == null) {
            sender.sendMessage(s().msg("dunya-bulunamadi", "dunya", args[1]));
            return;
        }
        plugin.worlds().delete(sender, d);
    }

    private void setSpawn(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(s().msg("sadece-oyuncu"));
            return;
        }
        if (args.length != 2) {
            sender.sendMessage(s().msg("kullanim", "kullanim", "/dünya setspawn <dünya>"));
            return;
        }
        WorldData d = plugin.registry().get(args[1]);
        if (d == null) {
            sender.sendMessage(s().msg("dunya-bulunamadi", "dunya", args[1]));
            return;
        }
        plugin.worlds().setSpawn((Player) sender, d);
    }

    private void teleport(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(s().msg("sadece-oyuncu"));
            return;
        }
        if (args.length != 2) {
            sender.sendMessage(s().msg("kullanim", "kullanim", "/dünya ışınlan <dünya>"));
            return;
        }
        WorldData d = plugin.registry().get(args[1]);
        if (d == null) {
            sender.sendMessage(s().msg("dunya-bulunamadi", "dunya", args[1]));
            return;
        }
        plugin.worlds().teleport((Player) sender, d);
    }

    private void list(CommandSender sender) {
        Settings s = s();
        if (plugin.registry().size() == 0) {
            sender.sendMessage(s.msg("liste-bos"));
            return;
        }
        sender.sendMessage(s.msg("liste-baslik", "sayi", String.valueOf(plugin.registry().size())));
        for (WorldData d : plugin.registry().all()) {
            World w = Bukkit.getWorld(d.name());
            String state;
            if (plugin.worlds().isPreparing(d.name())) {
                state = s.rawText("durum-hazirlaniyor");
            } else if (w != null) {
                state = s.rawText("durum-yuklu");
            } else {
                state = s.rawText("durum-yuklenmedi");
            }
            sender.sendMessage(s.plain("liste-satir", "dunya", d.name(), "tema", d.theme().key(), "tip", d.kind().key(),
                    "alan", d.areaText(), "oyuncu", String.valueOf(w == null ? 0 : w.getPlayers().size()), "durum", state));
        }
    }

    private void reload(CommandSender sender) {
        if (plugin.worlds().isBusy()) {
            sender.sendMessage(s().msg("reload-mesgul"));
            return;
        }
        int count = plugin.reloadAll();
        sender.sendMessage(s().msg("reload-tamam", "sayi", String.valueOf(count)));
    }

    private void help(CommandSender sender) {
        Settings s = s();
        sender.sendMessage(s.plain("yardim-baslik"));
        for (Sub sub : Sub.values()) {
            if (sender.hasPermission(sub.perm)) {
                sender.sendMessage(s.plain(sub.help));
            }
        }
    }

    // =================================================================== tab tamamlama

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission(PERM_USE) || args.length == 0) {
            return Collections.emptyList();
        }
        if (args.length == 1) {
            List<String> labels = new ArrayList<String>();
            for (Sub sub : Sub.values()) {
                if (sender.hasPermission(sub.perm)) {
                    labels.add(sub.label);
                }
            }
            return partial(args[0], labels);
        }
        Sub sub = Sub.from(args[0]);
        if (sub == null || !sender.hasPermission(sub.perm)) {
            return Collections.emptyList();
        }
        String last = args[args.length - 1];
        switch (sub) {
            case CREATE:
                if (args.length == 2) {
                    return last.isEmpty() ? Collections.singletonList("<isim>") : Collections.<String>emptyList();
                }
                if (args.length == 3) {
                    return partial(last, THEMES);
                }
                if (args.length == 4) {
                    return partial(last, KINDS);
                }
                if (args.length == 5) {
                    return partial(last, SIZE_EXAMPLE);
                }
                return Collections.emptyList();
            case DELETE:
                return args.length == 2 ? partial(last, plugin.registry().names()) : Collections.<String>emptyList();
            case SETSPAWN:
            case TELEPORT:
                return args.length == 2 ? partial(last, loadedNames()) : Collections.<String>emptyList();
            default:
                return Collections.emptyList();
        }
    }

    private List<String> loadedNames() {
        List<String> out = new ArrayList<String>();
        for (WorldData d : plugin.registry().all()) {
            if (Bukkit.getWorld(d.name()) != null && !plugin.worlds().isPreparing(d.name())) {
                out.add(d.name());
            }
        }
        return out;
    }

    /** Türkçe karakter duyarsız önek eşleştirme. */
    private static List<String> partial(String token, List<String> options) {
        String t = Text.norm(token);
        List<String> out = new ArrayList<String>();
        for (String o : options) {
            if (Text.norm(o).startsWith(t)) {
                out.add(o);
            }
        }
        return out;
    }
}
