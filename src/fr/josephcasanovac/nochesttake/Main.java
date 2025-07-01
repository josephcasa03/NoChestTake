package fr.josephcasanovac.nochesttake;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.type.Chest;
import org.bukkit.block.data.type.Door;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.block.Action;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.stream.Collectors;
import java.util.Arrays;

public class Main extends JavaPlugin implements Listener {

    private final Map<Location, Location> protectedBlockToSignMap = new HashMap<>();
    private final Map<Location, Set<UUID>> signAuthorizedPlayersMap = new HashMap<>();

    private static final Set<Material> protectableBlocks = Set.of(
            Material.CHEST,
            Material.TRAPPED_CHEST,
            Material.FURNACE,
            Material.SMOKER,
            Material.BLAST_FURNACE,

            Material.OAK_FENCE_GATE,
            Material.BIRCH_FENCE_GATE,
            Material.SPRUCE_FENCE_GATE,
            Material.JUNGLE_FENCE_GATE,
            Material.ACACIA_FENCE_GATE,
            Material.DARK_OAK_FENCE_GATE,
            Material.CRIMSON_FENCE_GATE,
            Material.WARPED_FENCE_GATE,

            Material.OAK_DOOR,
            Material.BIRCH_DOOR,
            Material.SPRUCE_DOOR,
            Material.JUNGLE_DOOR,
            Material.ACACIA_DOOR,
            Material.DARK_OAK_DOOR,
            Material.CRIMSON_DOOR,
            Material.WARPED_DOOR,

            Material.OAK_TRAPDOOR,
            Material.BIRCH_TRAPDOOR,
            Material.SPRUCE_TRAPDOOR,
            Material.JUNGLE_TRAPDOOR,
            Material.ACACIA_TRAPDOOR,
            Material.DARK_OAK_TRAPDOOR,
            Material.CRIMSON_TRAPDOOR,
            Material.WARPED_TRAPDOOR
    );

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        loadProtectedBlocks();
    }

    @Override
    public void onDisable() {
    }

    private void loadProtectedBlocks() {
        protectedBlockToSignMap.clear();
        signAuthorizedPlayersMap.clear();

        for (World world : Bukkit.getWorlds()) {
            for (org.bukkit.Chunk chunk : world.getLoadedChunks()) {
                for (BlockState state : chunk.getTileEntities()) {
                    if (state instanceof Sign sign) {
                        String firstLine = ChatColor.stripColor(sign.getLine(0));
                        if ("[PRIVATE]".equalsIgnoreCase(firstLine)) {
                            Block signBlock = sign.getBlock();
                            Block supportingBlock = getSupportingBlock(signBlock);
                            if (supportingBlock != null && protectableBlocks.contains(supportingBlock.getType())) {
                                addProtection(supportingBlock, sign);
                            } else {
                                new BukkitRunnable() {
                                    @Override
                                    public void run() {
                                        if (signBlock.getState() instanceof Sign s) {
                                            s.setLine(0, "");
                                            s.setLine(1, "");
                                            s.setLine(2, "");
                                            s.setLine(3, "");
                                            s.update();
                                        }
                                    }
                                }.runTaskLater(this, 1L);
                            }
                        }
                    }
                }
            }
        }
    }

    private void addProtection(Block protectedBlock, Sign sign) {
        Location signLoc = sign.getLocation();
        Location blockLoc = protectedBlock.getLocation();

        protectedBlockToSignMap.put(blockLoc, signLoc);

        if (protectedBlock.getType() == Material.CHEST || protectedBlock.getType() == Material.TRAPPED_CHEST) {
            BlockData data = protectedBlock.getBlockData();
            if (data instanceof Chest chestData) {
                if (chestData.getType() != Chest.Type.SINGLE) {
                    Block otherHalf = getOtherHalfOfDoubleChest(protectedBlock, chestData);
                    if (otherHalf != null) {
                        protectedBlockToSignMap.put(otherHalf.getLocation(), signLoc);
                    }
                }
            }
        }

        if (protectedBlock.getType().name().endsWith("_DOOR")) {
            BlockData data = protectedBlock.getBlockData();
            if (data instanceof Door doorData) {
                Block bottomHalf = doorData.getHalf() == Door.Half.TOP ? protectedBlock.getRelative(BlockFace.DOWN) : protectedBlock;
                Block topHalf = doorData.getHalf() == Door.Half.BOTTOM ? protectedBlock.getRelative(BlockFace.UP) : protectedBlock;

                protectedBlockToSignMap.put(bottomHalf.getLocation(), signLoc);
                protectedBlockToSignMap.put(topHalf.getLocation(), signLoc);
            }
        }

        Set<UUID> authorizedPlayers = new HashSet<>();
        for (int i = 1; i <= 3; i++) {
            String playerNameOnSign = ChatColor.stripColor(sign.getLine(i));
            if (playerNameOnSign != null && !playerNameOnSign.isEmpty()) {
                UUID foundUuid = null;

                for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                    if (onlinePlayer.getName().equalsIgnoreCase(playerNameOnSign)) {
                        foundUuid = onlinePlayer.getUniqueId();
                        break;
                    }
                }

                if (foundUuid == null) {
                    for (org.bukkit.OfflinePlayer offlinePlayer : Bukkit.getOfflinePlayers()) {
                        if (offlinePlayer.getName() != null && offlinePlayer.getName().equalsIgnoreCase(playerNameOnSign)) {
                            foundUuid = offlinePlayer.getUniqueId();
                            break;
                        }
                    }
                }

                if (foundUuid != null) {
                    authorizedPlayers.add(foundUuid);
                }
            }
        }
        signAuthorizedPlayersMap.put(signLoc, authorizedPlayers);
    }

    private void removeProtection(Location signLocation) {
        signAuthorizedPlayersMap.remove(signLocation);
        protectedBlockToSignMap.entrySet().removeIf(entry -> entry.getValue().equals(signLocation));
    }

    private Block getOtherHalfOfDoubleChest(Block chestBlock, Chest chestData) {
        for (BlockFace face : List.of(BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST)) {
            Block relative = chestBlock.getRelative(face);
            if (relative.getType() == chestBlock.getType()) {
                BlockData relativeData = relative.getBlockData();
                if (relativeData instanceof Chest relativeChest) {
                    if (relativeChest.getType() != Chest.Type.SINGLE && relativeChest.getFacing() == chestData.getFacing()) {
                        if ((chestData.getType() == Chest.Type.LEFT && relativeChest.getType() == Chest.Type.RIGHT) ||
                                (chestData.getType() == Chest.Type.RIGHT && relativeChest.getType() == Chest.Type.LEFT)) {
                            return relative;
                        }
                    }
                }
            }
        }
        return null;
    }

    private Block getSupportingBlock(Block signBlock) {
        BlockData data = signBlock.getBlockData();
        if (data instanceof Directional directional) {
            BlockFace facing = directional.getFacing();
            return signBlock.getRelative(facing.getOppositeFace());
        }
        return null;
    }

    private boolean isPlayerAuthorized(Location signLocation, Player player) {
        if (player.isOp()) {
            return true;
        }

        Set<UUID> authorizedPlayers = signAuthorizedPlayersMap.get(signLocation);
        return authorizedPlayers != null && authorizedPlayers.contains(player.getUniqueId());
    }

    private boolean isBlockProtected(Block block, Player player) {
        Location signLoc = protectedBlockToSignMap.get(block.getLocation());
        if (signLoc == null) {
            return false;
        }

        BlockState signState = signLoc.getBlock().getState();
        if (!(signState instanceof Sign)) {
            removeProtection(signLoc);
            return false;
        }

        return !isPlayerAuthorized(signLoc, player);
    }

    private boolean isPrivateSign(Block block) {
        // This method checks if a block is a [PRIVATE] sign.
        if (block == null || !(block.getState() instanceof Sign)) {
            return false;
        }
        Sign sign = (Sign) block.getState();
        String firstLine = ChatColor.stripColor(sign.getLine(0));
        return "[PRIVATE]".equalsIgnoreCase(firstLine);
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();

        if (player.isOp()) {
            return;
        }

        if (isPrivateSign(block)) {
            Sign sign = (Sign) block.getState();
            if (!isPlayerAuthorized(sign.getLocation(), player)) {
                event.setCancelled(true);
                return;
            } else {
                removeProtection(sign.getLocation());
            }
        }

        if (isBlockProtected(block, player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK && event.getAction() != Action.LEFT_CLICK_BLOCK) {
            return;
        }
        Block clickedBlock = event.getClickedBlock();
        if (clickedBlock == null) {
            return;
        }

        if (player.isOp()) {
            return;
        }

        if (isPrivateSign(clickedBlock)) {
            if (!isPlayerAuthorized(clickedBlock.getLocation(), player)) {
                event.setCancelled(true);
            }
            return;
        }

        if (isBlockProtected(clickedBlock, player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onSignChange(SignChangeEvent event) {
        Player player = event.getPlayer();
        Block signBlock = event.getBlock();
        String newFirstLine = ChatColor.stripColor(event.getLine(0));

        if ("[PRIVATE]".equalsIgnoreCase(newFirstLine)) {
            Block supportingBlock = getSupportingBlock(signBlock);

            if (supportingBlock == null || !protectableBlocks.contains(supportingBlock.getType())) {
                event.setCancelled(true);
                return;
            }

            boolean isExistingPrivateSign = signAuthorizedPlayersMap.containsKey(signBlock.getLocation());

            if (!isExistingPrivateSign) {
                event.setLine(0, ChatColor.DARK_BLUE + "[PRIVATE]");
                event.setLine(1, player.getName());
            } else {
                if (!isPlayerAuthorized(signBlock.getLocation(), player)) {
                    event.setCancelled(true);
                    return;
                }
            }

            new BukkitRunnable() {
                @Override
                public void run() {
                    BlockState state = signBlock.getState();
                    if (!(state instanceof Sign sign)) {
                        removeProtection(signBlock.getLocation());
                        return;
                    }

                    Set<UUID> currentAuthorizedPlayers = new HashSet<>();
                    for (int i = 1; i <= 3; i++) {
                        String playerNameOnSign = ChatColor.stripColor(sign.getLine(i));
                        if (playerNameOnSign != null && !playerNameOnSign.isEmpty()) {
                            UUID foundUuid = null;

                            for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                                if (onlinePlayer.getName().equalsIgnoreCase(playerNameOnSign)) {
                                    foundUuid = onlinePlayer.getUniqueId();
                                    break;
                                }
                            }

                            if (foundUuid == null) {
                                for (org.bukkit.OfflinePlayer offlinePlayer : Bukkit.getOfflinePlayers()) {
                                    if (offlinePlayer.getName() != null && offlinePlayer.getName().equalsIgnoreCase(playerNameOnSign)) {
                                        foundUuid = offlinePlayer.getUniqueId();
                                        break;
                                    }
                                }
                            }

                            if (foundUuid != null) {
                                currentAuthorizedPlayers.add(foundUuid);
                            }
                        }
                    }

                    boolean shouldBreakSign = false;

                    if (currentAuthorizedPlayers.isEmpty()) {
                        shouldBreakSign = true;
                    }

                    if (!shouldBreakSign && currentAuthorizedPlayers.size() == 1) {
                        UUID singleAuthorizedUUID = currentAuthorizedPlayers.iterator().next();
                        Player singleAuthorizedPlayer = Bukkit.getPlayer(singleAuthorizedUUID);

                        if (singleAuthorizedPlayer == null) {
                            shouldBreakSign = true;
                        }
                    }

                    if (shouldBreakSign) {
                        removeProtection(sign.getLocation());
                        signBlock.setType(Material.AIR);
                    } else {
                        addProtection(supportingBlock, sign);
                    }
                }
            }.runTaskLater(this, 1L);
        } else {
            if (isPrivateSign(signBlock)) {
                if (isPlayerAuthorized(signBlock.getLocation(), player)) {
                    removeProtection(signBlock.getLocation());
                } else {
                    event.setCancelled(true);
                    event.setLine(0, ChatColor.DARK_BLUE + "[PRIVATE]");
                }
            }
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        Block placedBlock = event.getBlockPlaced();

        if (!placedBlock.getType().name().contains("SIGN")) {
            return;
        }

        Block supportingBlock = getSupportingBlock(placedBlock);
        if (supportingBlock == null) {
            return;
        }

        if (!protectableBlocks.contains(supportingBlock.getType())) {
            return;
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                player.closeInventory();

                new BukkitRunnable() {
                    @Override
                    public void run() {
                        BlockState state = placedBlock.getState();
                        if (!(state instanceof Sign sign)) {
                            return;
                        }

                        sign.setLine(0, ChatColor.DARK_BLUE + "[PRIVATE]");
                        sign.setLine(1, player.getName());
                        sign.setLine(2, "");
                        sign.setLine(3, "");
                        sign.update();

                        addProtection(supportingBlock, sign);
                    }
                }.runTaskLater(Main.this, 4L);
            }
        }.runTaskLater(this, 1L);
    }
}
