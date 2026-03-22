package kz.anarchy.imperium;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;

public class IChatCommand extends Command {

    private final Main plugin;
    private final ConfigManager config;
    private final DataManager data;

    public IChatCommand(Main plugin, ConfigManager config, DataManager data) {
        super(config.getChatCommandName());
        this.plugin = plugin;
        this.config = config;
        this.data = data;
        setDescription("Империя ішіндегі чат");
        setUsage("/" + config.getChatCommandName() + " <хабарлама>");
    }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(config.getMessage("only-players"));
            return true;
        }

        String uuid = player.getUniqueId().toString();

        if (!data.playerInClan(uuid)) {
            player.sendMessage(config.getPrefixedMessage("not-in-clan"));
            return true;
        }

        if (args.length == 0) {
            player.sendMessage(config.colorize("&cПайдаланылуы: &e/" + label + " <хабарлама>"));
            return true;
        }

        DataManager.PlayerData pd = data.getPlayerData(uuid);
        DataManager.ImperiumData imp = data.getClan(pd.clan);
        if (imp == null) return true;

        // Хабарламаны жинаймыз
        StringBuilder sb = new StringBuilder();
        for (String word : args) sb.append(word).append(" ");
        String message = sb.toString().trim();

        String formatted = config.colorize(
            "&8[&6" + imp.name + "&8] " +
            config.getRankPrefix(pd.rang) + " &f" + player.getName() +
            "&8: &7" + message
        );

        // FIX #2 + #3: UUID арқылы тікелей Bukkit.getPlayer(UUID) — deprecated жоқ
        for (Map.Entry<String, String> entry : imp.members.entrySet()) {
            try {
                Player member = Bukkit.getPlayer(UUID.fromString(entry.getKey()));
                if (member != null) {
                    member.sendMessage(formatted);
                }
            } catch (IllegalArgumentException ignored) {
                // Дұрыс емес UUID болса өткізіп жіберемыз
            }
        }

        plugin.getLogger().info("[ImperiumChat][" + imp.name + "] " +
            player.getName() + ": " + message);

        return true;
    }
}
