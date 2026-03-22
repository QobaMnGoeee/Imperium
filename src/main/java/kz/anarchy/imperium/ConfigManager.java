package kz.anarchy.imperium;

import org.bukkit.ChatColor;

public class ConfigManager {

    private final Main plugin;

    public ConfigManager(Main plugin) {
        this.plugin = plugin;
    }

    // ── Команда атаулары ──
    public String getCommandName() {
        return plugin.getConfig().getString("command-name", "imperium").toLowerCase();
    }

    public String getChatCommandName() {
        return plugin.getConfig().getString("chat-command-name", "ichat").toLowerCase();
    }

    // ── Баптаулар ──
    public double getCreationCost() {
        return plugin.getConfig().getDouble("settings.creation-cost", 10000);
    }

    public int getMaxMembers() {
        return plugin.getConfig().getInt("settings.max-members", 50);
    }

    // ── Ранг ішкі атаулары ──
    public String getRankLeader() {
        return plugin.getConfig().getString("settings.ranks.leader", "patsha");
    }

    public String getRankOfficer() {
        return plugin.getConfig().getString("settings.ranks.officer", "wazir");
    }

    public String getRankMember() {
        return plugin.getConfig().getString("settings.ranks.member", "xalyq");
    }

    // ── Ранг атауын табу (ішкі → көрінетін) ──
    public String getRankName(String rang) {
        if (rang == null) return "Белгісіз";
        if (rang.equals(getRankLeader())) {
            return plugin.getConfig().getString("settings.rank-names.leader", "Патша");
        } else if (rang.equals(getRankOfficer())) {
            return plugin.getConfig().getString("settings.rank-names.officer", "Уәзір");
        } else {
            return plugin.getConfig().getString("settings.rank-names.member", "Халық");
        }
    }

    // ── Ранг префиксін табу ──
    public String getRankPrefix(String rang) {
        String path;
        if (rang == null) {
            path = "settings.rank-prefixes.member";
        } else if (rang.equals(getRankLeader())) {
            path = "settings.rank-prefixes.leader";
        } else if (rang.equals(getRankOfficer())) {
            path = "settings.rank-prefixes.officer";
        } else {
            path = "settings.rank-prefixes.member";
        }
        return colorize(plugin.getConfig().getString(path, "&f"));
    }

    // ── Хабарламалар ──
    /**
     * Хабарлама алу.
     * replacements жұп болып жазылады: "%key%", "мән", "%key2%", "мән2"
     */
    public String getMessage(String path, String... replacements) {
        String raw = plugin.getConfig().getString("messages." + path,
            "&cХабарлама табылмады: messages." + path);

        // /{cmd} → нақты команда атауымен алмастыру
        raw = raw.replace("{cmd}", getCommandName());

        // Жұп алмастыру: key → value
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            raw = raw.replace(replacements[i], replacements[i + 1]);
        }

        return colorize(raw);
    }

    // ── Префикс + хабарлама ──
    public String getPrefixedMessage(String path, String... replacements) {
        String prefix = colorize(plugin.getConfig().getString("messages.prefix", "&6&l⚔ IMPERIUM &r&8|"));
        return prefix + " " + getMessage(path, replacements);
    }

    // ── Бөлгіш сызық ──
    public String getDivider() {
        return ChatColor.DARK_GRAY + "▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬";
    }

    // ── Түс кодтарын қолдану ──
    public String colorize(String text) {
        if (text == null) return "";
        return ChatColor.translateAlternateColorCodes('&', text);
    }
}
