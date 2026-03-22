package kz.anarchy.imperium;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * DataManager — барлық деректерді YAML файлда сақтайды.
 *
 * FIX #2 + #3: Мүшелер енді есімімен емес UUID→Есім Map-пен сақталады.
 * Бұл deprecated Bukkit.getOfflinePlayer(String) шақыруын жояды
 * және ойыншы атын өзгертсе де деректер дұрыс қалады.
 *
 * data.yml құрылымы:
 *   imperiums:
 *     "clanname":
 *       name: "ClanAty"
 *       leader-uuid: "uuid"
 *       leader-name: "ОйыншыАты"
 *       members:
 *         "uuid1": "Есім1"
 *         "uuid2": "Есім2"
 *       bank: 5000.0
 *       home: {world, x, y, z, yaw, pitch}
 *   players:
 *     "uuid":
 *       clan: "clanname"
 *       rang: "patsha"
 */
public class DataManager {

    private final Main plugin;
    private File dataFile;
    private FileConfiguration data;

    // Клан аты (кіші) → ImperiumData
    private final Map<String, ImperiumData> imperiums = new LinkedHashMap<>();
    // UUID → PlayerData
    private final Map<String, PlayerData> players = new HashMap<>();

    // ═══════════════════════════════════════════════
    // Деректер кластары
    // ═══════════════════════════════════════════════

    public static class ImperiumData {
        public String name;            // Нақты аты
        public String leaderUUID;      // Патша UUID
        public String leaderName;      // Патша есімі (көрсету үшін)
        // FIX #2: UUID → Есім Map (deprecated OfflinePlayer(name) жоқ)
        public Map<String, String> members = new LinkedHashMap<>();
        public double bank = 0;
        public Location home = null;

        // Есімі бойынша UUID іздеу
        public String findUUIDByName(String name) {
            for (Map.Entry<String, String> e : members.entrySet()) {
                if (e.getValue().equalsIgnoreCase(name)) {
                    return e.getKey();
                }
            }
            return null;
        }
    }

    public static class PlayerData {
        public String clan;
        public String rang;
    }

    // ═══════════════════════════════════════════════
    // Конструктор
    // ═══════════════════════════════════════════════

    public DataManager(Main plugin) {
        this.plugin = plugin;
        dataFile = new File(plugin.getDataFolder(), "data.yml");
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
        load();
    }

    // ═══════════════════════════════════════════════
    // Жүктеу / Сақтау
    // ═══════════════════════════════════════════════

    public void load() {
        if (!dataFile.exists()) {
            try {
                dataFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("data.yml жасалмады: " + e.getMessage());
            }
        }

        data = YamlConfiguration.loadConfiguration(dataFile);

        // ── Империяларды жүктеу ──
        if (data.contains("imperiums") && data.getConfigurationSection("imperiums") != null) {
            for (String key : data.getConfigurationSection("imperiums").getKeys(false)) {
                String base = "imperiums." + key;
                ImperiumData imp = new ImperiumData();
                imp.name = data.getString(base + ".name", key);
                imp.leaderUUID = data.getString(base + ".leader-uuid", "");
                imp.leaderName = data.getString(base + ".leader-name", "");
                imp.bank = data.getDouble(base + ".bank", 0);

                // Мүшелерді UUID→Есім жүктеу
                if (data.contains(base + ".members") &&
                    data.getConfigurationSection(base + ".members") != null) {
                    for (String mUUID : data.getConfigurationSection(base + ".members").getKeys(false)) {
                        String mName = data.getString(base + ".members." + mUUID, "");
                        imp.members.put(mUUID, mName);
                    }
                }

                // Базаны жүктеу
                if (data.contains(base + ".home.world")) {
                    String worldName = data.getString(base + ".home.world");
                    org.bukkit.World world = Bukkit.getWorld(worldName);
                    if (world != null) {
                        double x = data.getDouble(base + ".home.x");
                        double y = data.getDouble(base + ".home.y");
                        double z = data.getDouble(base + ".home.z");
                        float yaw = (float) data.getDouble(base + ".home.yaw", 0);
                        float pitch = (float) data.getDouble(base + ".home.pitch", 0);
                        imp.home = new Location(world, x, y, z, yaw, pitch);
                    }
                }

                imperiums.put(key.toLowerCase(), imp);
            }
        }

        // ── Ойыншыларды жүктеу ──
        if (data.contains("players") && data.getConfigurationSection("players") != null) {
            for (String uuid : data.getConfigurationSection("players").getKeys(false)) {
                String base = "players." + uuid;
                PlayerData pd = new PlayerData();
                pd.clan = data.getString(base + ".clan");
                pd.rang = data.getString(base + ".rang");
                players.put(uuid, pd);
            }
        }

        plugin.getLogger().info("Деректер жүктелді: " + imperiums.size() +
            " империя, " + players.size() + " ойыншы.");
    }

    public void saveAll() {
        data.set("imperiums", null);
        data.set("players", null);

        // ── Империяларды сақтау ──
        for (Map.Entry<String, ImperiumData> entry : imperiums.entrySet()) {
            String base = "imperiums." + entry.getKey();
            ImperiumData imp = entry.getValue();
            data.set(base + ".name", imp.name);
            data.set(base + ".leader-uuid", imp.leaderUUID);
            data.set(base + ".leader-name", imp.leaderName);
            data.set(base + ".bank", imp.bank);

            // Мүшелерді UUID→Есім сақтау
            for (Map.Entry<String, String> m : imp.members.entrySet()) {
                data.set(base + ".members." + m.getKey(), m.getValue());
            }

            // Базаны сақтау
            if (imp.home != null && imp.home.getWorld() != null) {
                data.set(base + ".home.world", imp.home.getWorld().getName());
                data.set(base + ".home.x", imp.home.getX());
                data.set(base + ".home.y", imp.home.getY());
                data.set(base + ".home.z", imp.home.getZ());
                data.set(base + ".home.yaw", imp.home.getYaw());
                data.set(base + ".home.pitch", imp.home.getPitch());
            }
        }

        // ── Ойыншыларды сақтау ──
        for (Map.Entry<String, PlayerData> entry : players.entrySet()) {
            if (entry.getValue().clan == null) continue;
            String base = "players." + entry.getKey();
            data.set(base + ".clan", entry.getValue().clan);
            data.set(base + ".rang", entry.getValue().rang);
        }

        try {
            data.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Деректер сақталмады: " + e.getMessage());
        }
    }

    // ═══════════════════════════════════════════════
    // Империя операциялары
    // ═══════════════════════════════════════════════

    public boolean clanExists(String name) {
        return imperiums.containsKey(name.toLowerCase());
    }

    public ImperiumData getClan(String name) {
        if (name == null) return null;
        return imperiums.get(name.toLowerCase());
    }

    public Collection<ImperiumData> getAllClans() {
        return imperiums.values();
    }

    public void createClan(String name, String leaderName, String leaderUUID) {
        ImperiumData imp = new ImperiumData();
        imp.name = name;
        imp.leaderUUID = leaderUUID;
        imp.leaderName = leaderName;
        imp.members.put(leaderUUID, leaderName);
        imp.bank = 0;
        imperiums.put(name.toLowerCase(), imp);
    }

    public void deleteClan(String name) {
        imperiums.remove(name.toLowerCase());
    }

    // ═══════════════════════════════════════════════
    // Ойыншы операциялары
    // ═══════════════════════════════════════════════

    public boolean playerInClan(String uuid) {
        PlayerData pd = players.get(uuid);
        return pd != null && pd.clan != null;
    }

    public PlayerData getPlayerData(String uuid) {
        return players.get(uuid);
    }

    public void setPlayerData(String uuid, String clan, String rang) {
        PlayerData pd = players.computeIfAbsent(uuid, k -> new PlayerData());
        pd.clan = clan;
        pd.rang = rang;
    }

    public void clearPlayerData(String uuid) {
        PlayerData pd = players.get(uuid);
        if (pd != null) {
            pd.clan = null;
            pd.rang = null;
        }
    }
}
