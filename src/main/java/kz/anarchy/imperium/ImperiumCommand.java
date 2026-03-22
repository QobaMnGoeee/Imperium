package kz.anarchy.imperium;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.*;

public class ImperiumCommand extends Command {

    private final Main plugin;
    private final ConfigManager config;
    private final DataManager data;

    public ImperiumCommand(Main plugin, ConfigManager config, DataManager data) {
        super(config.getCommandName());
        this.plugin = plugin;
        this.config = config;
        this.data = data;
        setDescription("Империя жүйесінің негізгі командасы");
        setUsage("/" + config.getCommandName() + " <команда>");
    }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(config.getMessage("only-players"));
            return true;
        }

        if (args.length == 0) {
            sendHelp(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "create"  -> handleCreate(player, args);
            case "info"    -> handleInfo(player);
            case "members" -> handleMembers(player);
            case "add"     -> handleAdd(player, args);
            case "remove"  -> handleRemove(player, args);
            case "rang"    -> handleRang(player, args);
            case "sethome" -> handleSetHome(player);
            case "home"    -> handleHome(player);
            case "bank"    -> handleBank(player, args);
            case "balance" -> handleBalance(player);
            case "top"     -> handleTop(player);
            case "leave"   -> handleLeave(player);
            case "delete"  -> handleDelete(player, args);
            default        -> sendHelp(player);
        }

        return true;
    }

    // ═══════════════════════════════════════════════
    // /imperium create <ат>
    // ═══════════════════════════════════════════════
    private void handleCreate(Player player, String[] args) {
        String uuid = player.getUniqueId().toString();

        if (data.playerInClan(uuid)) {
            player.sendMessage(config.getPrefixedMessage("already-in-clan"));
            return;
        }

        if (args.length < 2) {
            player.sendMessage(config.getPrefixedMessage("create-no-name"));
            return;
        }

        String clanName = args[1];

        if (clanName.length() > 20) {
            player.sendMessage(config.getPrefixedMessage("create-name-too-long"));
            return;
        }

        // FIX: YAML key бұзылмас үшін — тек әріп, сан, _, - рұқсат
        if (!clanName.matches("[a-zA-Z0-9_\\-]+")) {
            player.sendMessage(config.colorize(
                config.getMessage("prefix") + " &cИмперия атында тек &eәріп, сан, _, - &cтаңбалар болуы мүмкін!"
            ));
            return;
        }

        if (data.clanExists(clanName)) {
            player.sendMessage(config.getPrefixedMessage("create-already-exists"));
            return;
        }

        double cost = config.getCreationCost();
        if (cost > 0) {
            Economy eco = plugin.getEconomy();
            if (eco == null) {
                player.sendMessage(config.getPrefixedMessage("no-vault"));
                return;
            }
            double balance = eco.getBalance(player);
            if (balance < cost) {
                player.sendMessage(config.getPrefixedMessage("create-no-money",
                    "%amount%", format(cost),
                    "%balance%", format(balance),
                    "%missing%", format(cost - balance)
                ));
                return;
            }
            eco.withdrawPlayer(player, cost);
        }

        data.createClan(clanName, player.getName(), uuid);
        data.setPlayerData(uuid, clanName, config.getRankLeader());

        player.sendMessage(config.getPrefixedMessage("create-success", "%clan%", clanName));
        if (cost > 0) {
            player.sendMessage(config.getPrefixedMessage("create-cost-info", "%amount%", format(cost)));
        }
    }

    // ═══════════════════════════════════════════════
    // /imperium info
    // ═══════════════════════════════════════════════
    private void handleInfo(Player player) {
        String uuid = player.getUniqueId().toString();

        if (!data.playerInClan(uuid)) {
            player.sendMessage(config.getPrefixedMessage("not-in-clan"));
            return;
        }

        DataManager.PlayerData pd = data.getPlayerData(uuid);
        DataManager.ImperiumData imp = data.getClan(pd.clan);
        if (imp == null) return;

        player.sendMessage("");
        player.sendMessage(config.colorize(
            config.getMessage("prefix") + " &eℹ &6" + imp.name + " &eимпериясы"
        ));
        player.sendMessage(config.getDivider());
        player.sendMessage(config.colorize("&6Атауы: &f" + imp.name));
        player.sendMessage(config.colorize("&6👑 Патша: &f" + imp.leaderName));
        player.sendMessage(config.colorize("&6👥 Мүшелер: &f" + imp.members.size() +
            (config.getMaxMembers() > 0 ? "/" + config.getMaxMembers() : "")));
        player.sendMessage(config.colorize("&6💰 Банк: &f" + format(imp.bank) + " ₸"));
        player.sendMessage(config.colorize("&6🎖 Рангіңіз: &f" +
            config.getRankPrefix(pd.rang) + " " + config.getRankName(pd.rang)));
        player.sendMessage(config.getDivider());
    }

    // ═══════════════════════════════════════════════
    // /imperium members
    // ═══════════════════════════════════════════════
    private void handleMembers(Player player) {
        String uuid = player.getUniqueId().toString();

        if (!data.playerInClan(uuid)) {
            player.sendMessage(config.getPrefixedMessage("not-in-clan"));
            return;
        }

        DataManager.PlayerData pd = data.getPlayerData(uuid);
        DataManager.ImperiumData imp = data.getClan(pd.clan);
        if (imp == null) return;

        player.sendMessage("");
        player.sendMessage(config.colorize(
            config.getMessage("prefix") + " &e👥 &6" + imp.name + " &eмүшелері"
        ));
        player.sendMessage(config.getDivider());

        // FIX #2 + #3: OfflinePlayer цикл ішінде жоқ.
        // UUID-тен тікелей PlayerData оқиды — блокирующий вызов жоқ.
        for (Map.Entry<String, String> entry : imp.members.entrySet()) {
            String memberUUID = entry.getKey();
            String memberName = entry.getValue();

            DataManager.PlayerData mPd = data.getPlayerData(memberUUID);
            String rang = (mPd != null && mPd.rang != null) ? mPd.rang : config.getRankMember();

            // Онлайн тексеру — UUID бойынша, try-catch қауіпсіздік үшін
            boolean online = false;
            try {
                online = Bukkit.getPlayer(UUID.fromString(memberUUID)) != null;
            } catch (IllegalArgumentException ignored) {}

            player.sendMessage(config.colorize(
                config.getRankPrefix(rang) + " &f" + memberName +
                " &8(" + config.getRankName(rang) + ")" +
                (online ? " &a●" : " &8●")
            ));
        }
        player.sendMessage(config.getDivider());
    }

    // ═══════════════════════════════════════════════
    // /imperium add <ойыншы>
    // ═══════════════════════════════════════════════
    private void handleAdd(Player player, String[] args) {
        String uuid = player.getUniqueId().toString();

        if (!data.playerInClan(uuid)) {
            player.sendMessage(config.getPrefixedMessage("not-in-clan"));
            return;
        }

        DataManager.PlayerData pd = data.getPlayerData(uuid);
        if (!config.getRankLeader().equals(pd.rang) && !config.getRankOfficer().equals(pd.rang)) {
            player.sendMessage(config.getPrefixedMessage("no-permission"));
            return;
        }

        if (args.length < 2) {
            player.sendMessage(config.getPrefixedMessage("add-no-name"));
            return;
        }

        DataManager.ImperiumData imp = data.getClan(pd.clan);
        if (imp == null) return;

        int maxMembers = config.getMaxMembers();
        if (maxMembers > 0 && imp.members.size() >= maxMembers) {
            player.sendMessage(config.getPrefixedMessage("add-max-members",
                "%max%", String.valueOf(maxMembers)));
            return;
        }

        // FIX #2 + #3: тек онлайн ойыншыны қосуға болады (UUID дәл белгілі)
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            player.sendMessage(config.getPrefixedMessage("player-not-found", "%player%", args[1]));
            return;
        }

        String targetUUID = target.getUniqueId().toString();
        String targetName = target.getName();

        if (data.playerInClan(targetUUID)) {
            player.sendMessage(config.getPrefixedMessage("add-already-member",
                "%player%", targetName));
            return;
        }

        imp.members.put(targetUUID, targetName);
        data.setPlayerData(targetUUID, pd.clan, config.getRankMember());

        player.sendMessage(config.getPrefixedMessage("add-success",
            "%player%", targetName, "%clan%", pd.clan));
        target.sendMessage(config.getPrefixedMessage("add-joined", "%clan%", pd.clan));
    }

    // ═══════════════════════════════════════════════
    // /imperium remove <ойыншы>
    // ═══════════════════════════════════════════════
    private void handleRemove(Player player, String[] args) {
        String uuid = player.getUniqueId().toString();

        if (!data.playerInClan(uuid)) {
            player.sendMessage(config.getPrefixedMessage("not-in-clan"));
            return;
        }

        DataManager.PlayerData pd = data.getPlayerData(uuid);
        if (!config.getRankLeader().equals(pd.rang) && !config.getRankOfficer().equals(pd.rang)) {
            player.sendMessage(config.getPrefixedMessage("no-permission"));
            return;
        }

        if (args.length < 2) {
            player.sendMessage(config.getPrefixedMessage("remove-no-name"));
            return;
        }

        DataManager.ImperiumData imp = data.getClan(pd.clan);
        if (imp == null) return;

        // Кланның мүшелерінен аты бойынша UUID табамыз
        String targetUUID = imp.findUUIDByName(args[1]);
        if (targetUUID == null) {
            player.sendMessage(config.getPrefixedMessage("remove-not-member",
                "%player%", args[1]));
            return;
        }
        String targetName = imp.members.get(targetUUID);

        // Патшаны шығара алмайды
        if (targetUUID.equals(imp.leaderUUID)) {
            player.sendMessage(config.getPrefixedMessage("remove-cant-leader"));
            return;
        }

        imp.members.remove(targetUUID);
        data.clearPlayerData(targetUUID);

        player.sendMessage(config.getPrefixedMessage("remove-success", "%player%", targetName));

        Player targetPlayer = null;
        try {
            targetPlayer = Bukkit.getPlayer(UUID.fromString(targetUUID));
        } catch (IllegalArgumentException ignored) {}
        if (targetPlayer != null) {
            targetPlayer.sendMessage(config.getPrefixedMessage("remove-kicked", "%clan%", pd.clan));
        }
    }

    // ═══════════════════════════════════════════════
    // /imperium rang <ойыншы> <wazir|xalyq>
    // ═══════════════════════════════════════════════
    private void handleRang(Player player, String[] args) {
        String uuid = player.getUniqueId().toString();

        if (!data.playerInClan(uuid)) {
            player.sendMessage(config.getPrefixedMessage("not-in-clan"));
            return;
        }

        DataManager.PlayerData pd = data.getPlayerData(uuid);
        if (!config.getRankLeader().equals(pd.rang)) {
            player.sendMessage(config.getPrefixedMessage("no-permission"));
            return;
        }

        if (args.length < 3) {
            player.sendMessage(config.getPrefixedMessage("rang-no-args"));
            return;
        }

        String targetName = args[1];
        String newRang = args[2].toLowerCase();

        if (!newRang.equals(config.getRankOfficer()) && !newRang.equals(config.getRankMember())) {
            player.sendMessage(config.getPrefixedMessage("rang-invalid"));
            return;
        }

        if (targetName.equalsIgnoreCase(player.getName())) {
            player.sendMessage(config.getPrefixedMessage("rang-self"));
            return;
        }

        DataManager.ImperiumData imp = data.getClan(pd.clan);
        if (imp == null) return;

        String targetUUID = imp.findUUIDByName(targetName);
        if (targetUUID == null) {
            player.sendMessage(config.getPrefixedMessage("rang-not-member", "%player%", targetName));
            return;
        }

        DataManager.PlayerData targetPd = data.getPlayerData(targetUUID);
        if (targetPd == null) return;

        targetPd.rang = newRang;
        String rankName = config.getRankName(newRang);

        player.sendMessage(config.getPrefixedMessage("rang-success",
            "%player%", targetName, "%rang%", rankName));

        Player targetPlayer = null;
        try {
            targetPlayer = Bukkit.getPlayer(UUID.fromString(targetUUID));
        } catch (IllegalArgumentException ignored) {}
        if (targetPlayer != null) {
            targetPlayer.sendMessage(config.colorize(
                config.getMessage("prefix") + " &aSіздің рангіңіз &6" + rankName + " &aболып өзгерді!"
            ));
        }
    }

    // ═══════════════════════════════════════════════
    // /imperium sethome
    // ═══════════════════════════════════════════════
    private void handleSetHome(Player player) {
        String uuid = player.getUniqueId().toString();

        if (!data.playerInClan(uuid)) {
            player.sendMessage(config.getPrefixedMessage("not-in-clan"));
            return;
        }

        DataManager.PlayerData pd = data.getPlayerData(uuid);
        if (!config.getRankLeader().equals(pd.rang)) {
            player.sendMessage(config.getPrefixedMessage("no-permission"));
            return;
        }

        DataManager.ImperiumData imp = data.getClan(pd.clan);
        if (imp == null) return;

        imp.home = player.getLocation().clone();
        player.sendMessage(config.getPrefixedMessage("home-set"));
    }

    // ═══════════════════════════════════════════════
    // /imperium home
    // ═══════════════════════════════════════════════
    private void handleHome(Player player) {
        String uuid = player.getUniqueId().toString();

        if (!data.playerInClan(uuid)) {
            player.sendMessage(config.getPrefixedMessage("not-in-clan"));
            return;
        }

        DataManager.PlayerData pd = data.getPlayerData(uuid);
        DataManager.ImperiumData imp = data.getClan(pd.clan);
        if (imp == null) return;

        if (imp.home == null) {
            player.sendMessage(config.getPrefixedMessage("home-not-set"));
            return;
        }

        player.teleport(imp.home);
        player.sendMessage(config.getPrefixedMessage("home-teleport"));
    }

    // ═══════════════════════════════════════════════
    // /imperium bank <add|give> <сумма>
    // ═══════════════════════════════════════════════
    private void handleBank(Player player, String[] args) {
        String uuid = player.getUniqueId().toString();

        if (!data.playerInClan(uuid)) {
            player.sendMessage(config.getPrefixedMessage("not-in-clan"));
            return;
        }

        if (args.length < 3) {
            player.sendMessage(config.getPrefixedMessage("bank-usage"));
            return;
        }

        Economy eco = plugin.getEconomy();
        if (eco == null) {
            player.sendMessage(config.getPrefixedMessage("no-vault"));
            return;
        }

        DataManager.PlayerData pd = data.getPlayerData(uuid);
        DataManager.ImperiumData imp = data.getClan(pd.clan);
        if (imp == null) return;

        double amount;
        try {
            amount = Double.parseDouble(args[2]);
        } catch (NumberFormatException e) {
            player.sendMessage(config.getPrefixedMessage("bank-invalid-amount"));
            return;
        }

        if (amount < 1) {
            player.sendMessage(config.getPrefixedMessage("bank-invalid-amount"));
            return;
        }

        switch (args[1].toLowerCase()) {
            case "add" -> {
                if (eco.getBalance(player) < amount) {
                    player.sendMessage(config.getPrefixedMessage("bank-no-money"));
                    return;
                }
                eco.withdrawPlayer(player, amount);
                imp.bank += amount;
                player.sendMessage(config.getPrefixedMessage("bank-deposit",
                    "%amount%", format(amount)));
            }
            case "give" -> {
                if (!config.getRankLeader().equals(pd.rang)) {
                    player.sendMessage(config.getPrefixedMessage("bank-no-permission"));
                    return;
                }
                if (imp.bank < amount) {
                    player.sendMessage(config.getPrefixedMessage("bank-not-enough",
                        "%balance%", format(imp.bank)));
                    return;
                }
                imp.bank -= amount;
                eco.depositPlayer(player, amount);
                player.sendMessage(config.getPrefixedMessage("bank-withdraw",
                    "%amount%", format(amount)));
            }
            default -> player.sendMessage(config.getPrefixedMessage("bank-usage"));
        }
    }

    // ═══════════════════════════════════════════════
    // /imperium balance
    // ═══════════════════════════════════════════════
    private void handleBalance(Player player) {
        String uuid = player.getUniqueId().toString();

        if (!data.playerInClan(uuid)) {
            player.sendMessage(config.getPrefixedMessage("not-in-clan"));
            return;
        }

        DataManager.PlayerData pd = data.getPlayerData(uuid);
        DataManager.ImperiumData imp = data.getClan(pd.clan);
        if (imp == null) return;

        player.sendMessage(config.getPrefixedMessage("bank-balance",
            "%clan%", imp.name, "%amount%", format(imp.bank)));
    }

    // ═══════════════════════════════════════════════
    // /imperium top
    // ═══════════════════════════════════════════════
    private void handleTop(Player player) {
        List<DataManager.ImperiumData> sorted = new ArrayList<>(data.getAllClans());

        if (sorted.isEmpty()) {
            player.sendMessage(config.getPrefixedMessage("top-empty"));
            return;
        }

        sorted.sort((a, b) -> Double.compare(b.bank, a.bank));

        player.sendMessage("");
        player.sendMessage(config.getPrefixedMessage("top-header"));
        player.sendMessage(config.getDivider());

        int limit = Math.min(10, sorted.size());
        for (int i = 0; i < limit; i++) {
            DataManager.ImperiumData imp = sorted.get(i);
            player.sendMessage(config.colorize(
                config.getMessage("top-line",
                    "%pos%", String.valueOf(i + 1),
                    "%clan%", imp.name,
                    "%bank%", format(imp.bank),
                    "%members%", String.valueOf(imp.members.size())
                )
            ));
        }
        player.sendMessage(config.getDivider());
    }

    // ═══════════════════════════════════════════════
    // /imperium leave
    // ═══════════════════════════════════════════════
    private void handleLeave(Player player) {
        String uuid = player.getUniqueId().toString();

        if (!data.playerInClan(uuid)) {
            player.sendMessage(config.getPrefixedMessage("not-in-clan"));
            return;
        }

        DataManager.PlayerData pd = data.getPlayerData(uuid);

        if (config.getRankLeader().equals(pd.rang)) {
            player.sendMessage(config.getPrefixedMessage("leave-leader"));
            return;
        }

        DataManager.ImperiumData imp = data.getClan(pd.clan);
        String clanName = pd.clan;

        if (imp != null) {
            imp.members.remove(uuid);
        }

        data.clearPlayerData(uuid);
        player.sendMessage(config.getPrefixedMessage("leave-success", "%clan%", clanName));
    }

    // ═══════════════════════════════════════════════
    // /imperium delete confirm
    // ═══════════════════════════════════════════════
    private void handleDelete(Player player, String[] args) {
        String uuid = player.getUniqueId().toString();

        if (!data.playerInClan(uuid)) {
            player.sendMessage(config.getPrefixedMessage("not-in-clan"));
            return;
        }

        DataManager.PlayerData pd = data.getPlayerData(uuid);

        if (!config.getRankLeader().equals(pd.rang)) {
            player.sendMessage(config.getPrefixedMessage("delete-no-permission"));
            return;
        }

        if (args.length < 2 || !args[1].equalsIgnoreCase("confirm")) {
            player.sendMessage(config.getPrefixedMessage("delete-confirm-msg"));
            return;
        }

        DataManager.ImperiumData imp = data.getClan(pd.clan);
        if (imp == null) return;

        String clanName = imp.name;

        // Барлық мүшелерді UUID бойынша тазалаймыз
        for (String memberUUID : new ArrayList<>(imp.members.keySet())) {
            data.clearPlayerData(memberUUID);
        }

        data.deleteClan(clanName);
        player.sendMessage(config.getPrefixedMessage("delete-success", "%clan%", clanName));
    }

    // ═══════════════════════════════════════════════
    // Көмек хабарламасы
    // ═══════════════════════════════════════════════
    private void sendHelp(Player player) {
        String cmd = config.getCommandName();
        player.sendMessage("");
        player.sendMessage(config.colorize("&6&l⚔ IMPERIUM &r&8| &eКомандалар тізімі"));
        player.sendMessage(config.getDivider());
        player.sendMessage(config.colorize("&e/" + cmd + " create <ат> &8- &7Империя құру (&c" + format(config.getCreationCost()) + " ₸&7)"));
        player.sendMessage(config.colorize("&e/" + cmd + " info &8- &7Империя мәліметі"));
        player.sendMessage(config.colorize("&e/" + cmd + " members &8- &7Мүшелер тізімі"));
        player.sendMessage(config.colorize("&e/" + cmd + " add <ойыншы> &8- &7Мүше қосу &7(онлайн болуы керек)"));
        player.sendMessage(config.colorize("&e/" + cmd + " remove <ойыншы> &8- &7Мүше шығару"));
        player.sendMessage(config.colorize("&e/" + cmd + " rang <ойыншы> <wazir|xalyq> &8- &7Ранг беру"));
        player.sendMessage(config.colorize("&e/" + cmd + " sethome &8- &7База орнату &c(Патша)"));
        player.sendMessage(config.colorize("&e/" + cmd + " home &8- &7Базаға телепорт"));
        player.sendMessage(config.colorize("&e/" + cmd + " bank add <сумма> &8- &7Банкке салу"));
        player.sendMessage(config.colorize("&e/" + cmd + " bank give <сумма> &8- &7Банктен алу &c(Патша)"));
        player.sendMessage(config.colorize("&e/" + cmd + " balance &8- &7Банк балансын көру"));
        player.sendMessage(config.colorize("&e/" + cmd + " top &8- &7💰 Рейтинг"));
        player.sendMessage(config.colorize("&e/" + cmd + " leave &8- &7Империядан шығу"));
        player.sendMessage(config.colorize("&e/" + cmd + " delete confirm &8- &7Империяны жою &c(Патша)"));
        player.sendMessage(config.getDivider());
    }

    private String format(double amount) {
        if (amount == Math.floor(amount)) {
            return String.format("%,.0f", amount);
        }
        return String.format("%,.2f", amount);
    }
}
