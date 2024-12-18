package me.blubriu.asakaGiftcodes;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.text.ParseException;
import java.util.*;

import org.json.JSONObject;

public class AsakaGiftcodes extends JavaPlugin {

    private FileConfiguration giftCodesConfig;
    private File giftCodesFile;
    private Map<String, GiftCode> giftCodes = new HashMap<>();
    private File dataplayerFile;
    private FileConfiguration dataplayerConfig;
    private String currentVersion = getDescription().getVersion();

    @Override
    public void onEnable() {
        createConfigFiles();
        refreshGiftCodes();
        loadGiftCodes();
        loadDataplayerConfig();
        updateConfig();
        getCommand("giftcode").setExecutor(this);
        getCommand("code").setExecutor(this);
        sendFancyMessage();
        checkForUpdates();
    }

    private void createConfigFiles() {
        createFile("config.yml");
        createFile("giftcode.yml");
        createFile("dataplayer.yml");
    }

    private void createFile(String fileName) {
        File file = new File(getDataFolder(), fileName);
        if (!file.exists()) {
            file.getParentFile().mkdirs();
            saveResource(fileName, false);
        }
        if (fileName.equals("config.yml")) {
            getConfig().options().copyDefaults(true);
            saveConfig();
        } else if (fileName.equals("giftcode.yml")) {
            giftCodesFile = file;
            giftCodesConfig = YamlConfiguration.loadConfiguration(giftCodesFile);
        } else if (fileName.equals("dataplayer.yml")) {
            dataplayerFile = file;
            dataplayerConfig = YamlConfiguration.loadConfiguration(dataplayerFile);
        }
    }

    private void loadDataplayerConfig() {
        dataplayerConfig = YamlConfiguration.loadConfiguration(dataplayerFile);
    }

    private void sendFancyMessage() {
        ConsoleCommandSender console = Bukkit.getConsoleSender();
        console.sendMessage("§b");
        console.sendMessage("§9§lAsakaGiftcodes §e" + getDescription().getVersion());
        console.sendMessage("§bby §3Blub §7(_blueblub)");
        console.sendMessage("§b");
    }

    @Override
    public void onDisable() {
        saveGiftCodes();
    }

    private void updateConfig() {
        getConfig().options().copyDefaults(true);
        saveConfig();
    }

    private void loadGiftCodes() {
        for (String key : giftCodesConfig.getKeys(false)) {
            String message = giftCodesConfig.getString(key + ".message");
            List<String> commands = giftCodesConfig.getStringList(key + ".commands");
            int maxUses = giftCodesConfig.getInt(key + ".max-uses");
            String expiry = giftCodesConfig.getString(key + ".expiry");
            boolean enabled = giftCodesConfig.getBoolean(key + ".enabled");
            int playerMaxUses = giftCodesConfig.getInt(key + ".player-max-uses");
            GiftCode giftCode = new GiftCode(commands, message, maxUses, expiry, enabled, playerMaxUses);
            giftCodes.put(key, giftCode);
        }
    }

    private void saveGiftCodes() {
        for (Map.Entry<String, GiftCode> entry : giftCodes.entrySet()) {
            GiftCode giftCode = entry.getValue();
            giftCodesConfig.set(entry.getKey() + ".commands", giftCode.getCommands());
            giftCodesConfig.set(entry.getKey() + ".message", giftCode.getMessage());
            giftCodesConfig.set(entry.getKey() + ".max-uses", giftCode.getMaxUses());
            giftCodesConfig.set(entry.getKey() + ".expiry", giftCode.getExpiry());
            giftCodesConfig.set(entry.getKey() + ".enabled", giftCode.isEnabled());
            giftCodesConfig.set(entry.getKey() + ".player-max-uses", giftCode.getPlayerMaxUses());
        }
        try {
            giftCodesConfig.save(giftCodesFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void createGiftCode(String code, List<String> commands, String message, int maxUses, String expiry,
                                boolean enabled, int playerMaxUses) {
        if (giftCodes.containsKey(code)) {
            getLogger().warning("Mã quà tặng " + code + " đã tồn tại. Vui lòng tạo mã khác.");
            return;
        }

        GiftCode giftCode = new GiftCode(commands, message, maxUses, expiry, enabled, playerMaxUses);
        giftCodes.put(code, giftCode);
        giftCodesConfig.set(code + ".commands", commands);
        giftCodesConfig.set(code + ".message", message);
        giftCodesConfig.set(code + ".max-uses", maxUses);
        giftCodesConfig.set(code + ".expiry", expiry);
        giftCodesConfig.set(code + ".enabled", enabled);
        giftCodesConfig.set(code + ".player-max-uses", playerMaxUses);
        saveGiftCodes();
        getLogger().info("Mã quà tặng " + code + " đã được tạo thành công!");
    }

    private void deleteGiftCode(String code) {
        giftCodes.remove(code);
        giftCodesConfig.set(code, null);
        saveGiftCodes();
        refreshGiftCodes();
    }

    private List<String> listGiftCodes() {
        return new ArrayList<>(giftCodes.keySet());
    }

    private void assignGiftCodeToPlayer(CommandSender sender, String code, Player player) {
        if (giftCodes.containsKey(code)) {
            GiftCode giftCode = giftCodes.get(code);
            if (!giftCode.isEnabled()) {
                sender.sendMessage(ChatColor.RED + "Mã quà tặng " + code + " đã bị vô hiệu hóa.");
                return;
            }

            List<String> assignedCodes = dataplayerConfig
                    .getStringList("players." + player.getUniqueId() + ".assignedCodes");
            if (assignedCodes == null) {
                assignedCodes = new ArrayList<>();
            }
            if (!assignedCodes.contains(code)) {
                assignedCodes.add(code);
                dataplayerConfig.set("players." + player.getUniqueId() + ".assignedCodes", assignedCodes);
                saveDataplayerConfig();

                for (String cmd : giftCode.getCommands()) {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.replace("%player%", player.getName()));
                }
                player.sendMessage(ChatColor.GREEN + "Bạn đã được gán mã quà tặng " + code + "!");
                player.sendMessage(ChatColor.GREEN + giftCode.getMessage());
            } else {
                sender.sendMessage(ChatColor.RED + "Người chơi đã được gán hoặc đã nhập mã quà tặng này rồi.");
            }
        } else {
            sender.sendMessage(ChatColor.RED + "Không tìm thấy mã quà tặng!");
        }
    }

    private void createRandomGiftCodes(String baseName, int amount) {
        Random random = new Random();
        for (int i = 0; i < amount; i++) {
            String code = baseName + "_" + random.nextInt(1000000);
            createGiftCode(code, Collections.singletonList("give %player% diamond 1"),
                    "Bạn đã nhận được một viên kim cương!", 10, "2024-12-31T23:59:59", true, 1);
        }
    }

    private boolean checkPlayerHasUsedCode(Player player, String code) {
        List<String> usedCodes = dataplayerConfig.getStringList("players." + player.getUniqueId() + ".usedCodes");
        int playerMaxUses = getPlayerMaxUsesForCode(code);
        if (playerMaxUses == -1) {
            return false;
        }
        return Collections.frequency(usedCodes, code) >= playerMaxUses;
    }

    private void addPlayerUsedCode(Player player, String code) {
        List<String> usedCodes = dataplayerConfig.getStringList("players." + player.getUniqueId() + ".usedCodes");
        if (usedCodes == null) {
            usedCodes = new ArrayList<>();
        }
        usedCodes.add(code);
        dataplayerConfig.set("players." + player.getUniqueId() + ".usedCodes", usedCodes);
        saveDataplayerConfig();
    }

    private void saveDataplayerConfig() {
        try {
            dataplayerConfig.save(dataplayerFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void checkForUpdates() {
        boolean checkUpdate = getConfig().getBoolean("check-update", true);

        if (!checkUpdate) {
            return;
        }

        String url = "https://api.github.com/repos/blu3berry94/AsakaGiftcodes/releases/latest";
        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
                    connection.setRequestMethod("GET");
                    connection.setRequestProperty("Accept", "application/vnd.github.v3+json");
                    if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                        Scanner scanner = new Scanner(connection.getInputStream());
                        String response = scanner.useDelimiter("\\A").next();
                        scanner.close();

                        JSONObject jsonResponse = new JSONObject(response);
                        String latestVersion = jsonResponse.getString("tag_name");

                        if (latestVersion.startsWith("v")) {
                            latestVersion = latestVersion.substring(1);
                        }

                        final String finalLatestVersion = latestVersion;

                        if (!finalLatestVersion.equals(currentVersion)) {
                            ConsoleCommandSender console = Bukkit.getConsoleSender();
                            console.sendMessage("§aĐã có phiên bản mới của plugin! Phiên bản hiện tại: §9"
                                    + currentVersion + "§a, Phiên bản mới: §9" + finalLatestVersion);
                            Bukkit.getScheduler().runTaskTimer(AsakaGiftcodes.this, new BukkitRunnable() {
                                @Override
                                public void run() {
                                    console.sendMessage("§aPlugin đã có phiên bản mới: §9" + finalLatestVersion);
                                }
                            }, 0L, 1080L);
                        } else {
                            getLogger().info(
                                    ChatColor.GREEN + "Plugin đang ở phiên bản mới nhất: §9" + currentVersion);
                        }
                    } else {
                        getLogger().warning("Không thể kết nối để kiểm tra bản cập nhật.");
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }.runTaskLater(this, 0L);
    }

    private int getPlayerMaxUsesForCode(String code) {
        if (giftCodes.containsKey(code)) {
            int playerMaxUses = giftCodes.get(code).getPlayerMaxUses();
            return playerMaxUses;
        }
        return 1;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (label.equalsIgnoreCase("giftcode") || label.equalsIgnoreCase("gc")) {
            if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
                sender.sendMessage("§a");
                sender.sendMessage("§9 §9§lAsakaGiftcodes §7" + getDescription().getVersion());
                sender.sendMessage("§7 §8by §3Blub");
                sender.sendMessage("§a");
                sender.sendMessage("§b/gc create <code> §7- §fTạo mã quà tặng");
                sender.sendMessage("§b/gc create <name> random §7- §fTạo ngẫu nhiên 10 mã quà tặng");
                sender.sendMessage("§b/gc del <code> §7- §fXóa mã quà tặng");
                sender.sendMessage("§b/gc reload §7- §fTải lại pluins");
                sender.sendMessage("§b/gc enable <code> §7- §fKích hoạt mã quà tặng");
                sender.sendMessage("§b/gc disable <code> §7- §fVô hiệu hóa mã quà tặng");
                sender.sendMessage("§b/gc list §7- §fXem danh sách mã quà tặng");
                sender.sendMessage("§b/gc assign <code> <player> §7- §fGán mã quà tặng cho cho người chơi");
                sender.sendMessage("§a");
                sender.sendMessage("§3§l| §fRequired §c§l<> §7; §fOptional §c§l[]");
                return true;
            }

            if (!sender.hasPermission("giftcode.admin")) {
                sender.sendMessage(ChatColor.RED + "Bạn không có quyền xử dụng lệnh này.");
                return true;
            }

            switch (args[0].toLowerCase()) {
                case "create":
                    if (args.length == 2) {
                        if (giftCodes.containsKey(args[1])) {
                            sender.sendMessage(
                                    ChatColor.RED + "Mã quà tặng " + args[1] + " đã tồn tại. Vui lòng chọn mã khác.");
                        } else {
                            createGiftCode(args[1], Collections.singletonList("give %player% diamond 1"),
                                    "Bạn đã nhận 1 viên kim cương!", 10, "2024-12-31T23:59:59", true, 1);
                            sender.sendMessage(ChatColor.GREEN + "Mã quà tặng " + args[1] + " tạo thành công!");
                        }
                    } else if (args.length == 3 && args[2].equalsIgnoreCase("random")) {
                        createRandomGiftCodes(args[1], 10);
                        sender.sendMessage(ChatColor.GREEN + "Tạo 10 mã quà tặng ngẫu nhiên với tên cơ sở " + args[1]);
                    } else {
                        sender.sendMessage(
                                ChatColor.RED + "Sử dụng: /gc create <code> hoặc /gc create <name> random");
                    }
                    break;
                case "del":
                    if (args.length == 2) {
                        deleteGiftCode(args[1]);
                        sender.sendMessage(ChatColor.GREEN + "Mã quà tặng " + args[1] + " đã bị xóa!");
                    } else {
                        sender.sendMessage(ChatColor.RED + "Sử dụng: /gc del <code>");
                    }
                    break;
                case "reload":
                    reloadConfig();
                    createConfigFiles();
                    refreshGiftCodes();
                    loadGiftCodes();
                    loadDataplayerConfig();
                    sender.sendMessage(ChatColor.GREEN + "Đã tải lại tất cả các file cấu hình!");
                    break;
                case "enable":
                    if (args.length == 2) {
                        GiftCode codeToEnable = giftCodes.get(args[1]);
                        if (codeToEnable != null) {
                            codeToEnable.setEnabled(true);
                            saveGiftCodes();
                            sender.sendMessage(ChatColor.GREEN + "Mã quà tặng " + args[1] + " Đã kích hoạt!");
                        } else {
                            sender.sendMessage(ChatColor.RED + "Mã quà tặng không tồn tại!");
                        }
                    } else {
                        sender.sendMessage(ChatColor.RED + "Sử dụng: /gc enable <code>");
                    }
                    break;
                case "disable":
                    if (args.length == 2) {
                        GiftCode codeToDisable = giftCodes.get(args[1]);
                        if (codeToDisable != null) {
                            codeToDisable.setEnabled(false);
                            saveGiftCodes();
                            sender.sendMessage(ChatColor.GREEN + "Mã quà tặng " + args[1] + " Đã bị vô hiệu hóa!");
                        } else {
                            sender.sendMessage(ChatColor.RED + "Không tồn tài!");
                        }
                    } else {
                        sender.sendMessage(ChatColor.RED + "Sử dụng: /gc disable <code>");
                    }
                    break;
                case "list":
                    sender.sendMessage("§b§l| §3Danh sách mã quà tặng:");
                    for (String code : listGiftCodes()) {
                        GiftCode giftCode = giftCodes.get(code);
                        String expiry = giftCode.getExpiry();
                        String expiryMessage = expiry.isEmpty() ? "Vĩnh Viễn" : "Hết hạn vào:§c " + expiry;
                        sender.sendMessage("§a" + code + " §7- §f" + expiryMessage);
                    }
                    break;
                case "assign":
                    if (args.length == 3) {
                        String code = args[1];
                        Player targetPlayer = Bukkit.getPlayer(args[2]);
                        if (targetPlayer != null) {
                            assignGiftCodeToPlayer(sender, code, targetPlayer);
                            sender.sendMessage(ChatColor.GOLD + "Thất bại ⬆⬆⬆" + ChatColor.GRAY + " hoặc" + ChatColor.GREEN + " mã quà tặng " + code + " đã được gán cho "
                                    + targetPlayer.getName() + " thành công.");
                        } else {
                            sender.sendMessage(ChatColor.RED + "Không tìm thấy người chơi!");
                        }
                    } else {
                        sender.sendMessage(ChatColor.RED + "Sử dụng: /gc assign <code> <player>");
                    }
                    break;

            }
            return true;
        } else if (label.equalsIgnoreCase("code")) {
            if (sender instanceof Player) {
                Player player = (Player) sender;
                if (args.length == 1) {
                    String code = args[0];
                    GiftCode giftCode = giftCodes.get(code);
                    if (giftCode == null) {
                        player.sendMessage(ChatColor.RED + getConfig().getString("messages.invalid-code"));
                        return true;
                    }
                    if (!giftCode.isEnabled()) {
                        player.sendMessage(ChatColor.RED + getConfig().getString("messages.code-disabled"));
                        return true;
                    }
                    if (!giftCode.getExpiry().isEmpty()) {
                        try {
                            Date expiryDate = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").parse(giftCode.getExpiry());
                            if (new Date().after(expiryDate)) {
                                player.sendMessage(ChatColor.RED + getConfig().getString("messages.code-expired"));
                                return true;
                            }
                        } catch (ParseException e) {
                            e.printStackTrace();
                            player.sendMessage(ChatColor.RED + "Đã xảy ra lỗi khi kiểm tra thời gian hết hạn.");
                            return true;
                        }
                    }
                    if (giftCode.getMaxUses() <= 0) {
                        player.sendMessage(ChatColor.RED + getConfig().getString("messages.max-uses-reached"));
                        return true;
                    }
                    if (checkPlayerHasUsedCode(player, code)) {
                        player.sendMessage(ChatColor.RED + getConfig().getString("messages.code-already-redeemed"));
                        return true;
                    }

                    int playerMaxUses = getPlayerMaxUsesForCode(code);
                    if (playerMaxUses == -1) {
                    } else {
                        if (checkPlayerHasUsedCode(player, code)) {
                            player.sendMessage(ChatColor.RED + getConfig().getString("messages.code-already-redeemed"));
                            return true;
                        }
                    }
                    for (String cmd : giftCode.getCommands()) {
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.replace("%player%", player.getName()));
                    }
                    player.sendMessage(ChatColor.GREEN + giftCode.getMessage());
                    giftCode.setMaxUses(giftCode.getMaxUses() - 1);
                    addPlayerUsedCode(player, code);
                    saveGiftCodes();
                } else {
                    player.sendMessage(ChatColor.RED + "Sử dụng: /code <code>");
                }
            } else {
                sender.sendMessage(ChatColor.RED + "Chỉ người chơi mới có thể sử dụng lệnh này.");
            }
            return true;
        }
        return false;
    }

    private void refreshGiftCodes() {
        giftCodes.clear();
        loadGiftCodes();
    }

    public class GiftCode {
        private List<String> commands;
        private String message;
        private int maxUses;
        private String expiry;
        private boolean enabled;
        private int playerMaxUses;

        public GiftCode(List<String> commands, String message, int maxUses, String expiry, boolean enabled,
                        int playerMaxUses) {
            this.commands = commands;
            this.message = message;
            this.maxUses = maxUses;
            this.expiry = expiry;
            this.enabled = enabled;
            this.playerMaxUses = playerMaxUses;
        }

        public List<String> getCommands() {
            return commands;
        }

        public void setCommands(List<String> commands) {
            this.commands = commands;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public int getMaxUses() {
            return maxUses;
        }

        public void setMaxUses(int maxUses) {
            this.maxUses = maxUses;
        }

        public String getExpiry() {
            return expiry;
        }

        public void setExpiry(String expiry) {
            this.expiry = expiry;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getPlayerMaxUses() {
            return playerMaxUses;
        }

        public void setPlayerMaxUses(int playerMaxUses) {
            this.playerMaxUses = playerMaxUses;
        }
    }
}