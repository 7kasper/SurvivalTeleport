package nl.kaspermuller.survivalteleport;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;

public class SurvivalTeleport extends JavaPlugin implements Listener {

	NamespacedKey teleportClockKey = new NamespacedKey(this, "teleport_clock");
	NamespacedKey targetKey = new NamespacedKey(this, "teleport_target");
	public Map<UUID, Integer> sneakTasks = new HashMap<UUID, Integer>();
	public static int requiredXpLevels = 3;
	
	@Override
	public void onEnable() {
		getServer().getPluginManager().registerEvents(this, this);
		
		// Add recipe for the special clock!
		ItemStack teleportClock = new ItemStack(Material.CLOCK);
		ItemMeta clockMeta = teleportClock.getItemMeta();
		clockMeta.setDisplayName(ChatColor.RESET + "Clock to Nobody");
		clockMeta.setLore(Arrays.asList("Hit a player with the clock bind target"));
		// TODO no hack? For now we just add random enchantment and remove its visibility:
		clockMeta.addEnchant(Enchantment.THORNS, 1, true);
		clockMeta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
		clockMeta.getPersistentDataContainer().set(teleportClockKey, PersistentDataType.BOOLEAN, true);
		teleportClock.setItemMeta(clockMeta);
		ShapedRecipe recipe = new ShapedRecipe(teleportClockKey, teleportClock);
		recipe.shape("FEF", "PCP", "FEF");
		recipe.setIngredient('F', Material.POPPED_CHORUS_FRUIT);
		recipe.setIngredient('E', Material.ENDER_EYE);
		recipe.setIngredient('P', Material.ENDER_PEARL);
		recipe.setIngredient('C', Material.CLOCK);
		Bukkit.addRecipe(recipe);
	}
	
	@EventHandler
	public void playerHitEvent(EntityDamageByEntityEvent e) {
		if (e.getDamager().getType() == EntityType.PLAYER && e.getEntity().getType() == EntityType.PLAYER) {
			Player p = (Player) e.getDamager();
			ItemStack wielding = p.getInventory().getItemInMainHand();
			if (wielding.getType() == Material.CLOCK) {
				if (wielding.hasItemMeta() && wielding.getItemMeta().getPersistentDataContainer().has(teleportClockKey)) {
					if (p.hasPermission("survivalteleport.bind")) {
						try {
							ItemMeta clockMeta = wielding.getItemMeta();
							Player target = (Player) e.getEntity();
							clockMeta.setDisplayName(ChatColor.RESET + "Clock to " + target.getName());
							clockMeta.setLore(Arrays.asList("Crouch to attempt teleport"));
							clockMeta.getPersistentDataContainer().set(targetKey, PersistentDataType.STRING, target.getUniqueId().toString());
							p.getWorld().playSound(p.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 1.0f, 1.0f);
							p.getWorld().spawnParticle(Particle.SPELL_WITCH, target.getLocation(), 10);
							p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent("Target set! Crouch for a few seconds to teleport."));
							wielding.setItemMeta(clockMeta);
							p.getInventory().setItemInMainHand(wielding);
							p.updateInventory();
						} catch (Exception ex) { ex.printStackTrace(); }
					} else {
						p.getWorld().playSound(p.getLocation(), Sound.BLOCK_LAVA_POP, 1.0f, 1.0f);
					}
				}
			}
		}
	}
	
	@EventHandler
	public void onSneakToggle(PlayerToggleSneakEvent e) {
		Player p = e.getPlayer();
		if (!e.isSneaking()) {
			if (sneakTasks.containsKey(p.getUniqueId())) {
				Bukkit.getScheduler().cancelTask(sneakTasks.get(p.getUniqueId()));
				// Just to be sure also cancel the charging sound so we get no duplicates.
				p.getWorld().getNearbyEntities(p.getLocation(), 32, 32, 32).stream().filter(en -> en.getType() == EntityType.PLAYER).map(en -> (Player) en).forEach(np -> {
					np.stopSound(Sound.ENTITY_GUARDIAN_ATTACK);
				});
				p.getWorld().playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 0.1f);
				sneakTasks.remove(p.getUniqueId());
			}
			return;
		}
		ItemStack clock = null;
		if (p.getInventory().getItemInMainHand().getType() == Material.CLOCK && p.getInventory().getItemInMainHand().hasItemMeta() && p.getInventory().getItemInMainHand().getItemMeta().getPersistentDataContainer().has(teleportClockKey)) {
			clock = p.getInventory().getItemInMainHand();
		} else if (p.getInventory().getItemInOffHand().getType() == Material.CLOCK && p.getInventory().getItemInOffHand().hasItemMeta() && p.getInventory().getItemInOffHand().getItemMeta().getPersistentDataContainer().has(teleportClockKey)) {
			clock = p.getInventory().getItemInOffHand();
		} else {
			return;
		}
		// Also check if the clock even has a target.
		ItemMeta clockMeta = clock.getItemMeta();
		if (!clockMeta.getPersistentDataContainer().has(targetKey)) return;
		// Check if the player has permission
		if (!p.hasPermission("survivalteleport.use")) {
			p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(ChatColor.RED + "No permission to use teleporter Clock!"));
			return;
		}
		// Start teleportation process.
		p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent("Honing in on target.. Keep crouching to teleport"));
		p.getWorld().playSound(p.getLocation(), Sound.ENTITY_GUARDIAN_ATTACK, 1.0f, 1.0f);
		UUID teleportingPlayer = p.getUniqueId();
		sneakTasks.put(p.getUniqueId(), Bukkit.getScheduler().scheduleSyncDelayedTask(this, () -> {
			Player tpl = Bukkit.getPlayer(teleportingPlayer);
			if (tpl != null) { // If teleporting player is still online.
				ItemStack nClock = null;
				boolean clockInMainHand = true;
				if (tpl.getInventory().getItemInMainHand().getType() == Material.CLOCK && tpl.getInventory().getItemInMainHand().hasItemMeta() && tpl.getInventory().getItemInMainHand().getItemMeta().getPersistentDataContainer().has(teleportClockKey)) {
					nClock = tpl.getInventory().getItemInMainHand();
				} else if (tpl.getInventory().getItemInOffHand().getType() == Material.CLOCK && tpl.getInventory().getItemInOffHand().hasItemMeta() && tpl.getInventory().getItemInOffHand().getItemMeta().getPersistentDataContainer().has(teleportClockKey)) {
					nClock = p.getInventory().getItemInOffHand();
					clockInMainHand = false;
				}
				// Still holding the clock?
				if (nClock != null && nClock.getItemMeta().getPersistentDataContainer().has(teleportClockKey)) {
					ItemMeta nClockMeta = nClock.getItemMeta();
					// If the player has enough experience
					if (tpl.getLevel() < requiredXpLevels) {
						tpl.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(ChatColor.RED + "You need " + requiredXpLevels + " XP levels to teleport!"));
						tpl.getWorld().playSound(tpl.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 0.1f);
					} else {
						// Check if target is available
						UUID targetPlayer = UUID.fromString(nClockMeta.getPersistentDataContainer().get(targetKey, PersistentDataType.STRING));
						Player target = Bukkit.getPlayer(targetPlayer);
						if (target == null || target.getGameMode() == GameMode.SPECTATOR) { // Also no spectator...
							tpl.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(ChatColor.RED + "Target not found!"));
							tpl.getWorld().playSound(tpl.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 0.1f);
						} else {
							// Do the process :-)
							tpl.setLevel(tpl.getLevel() - requiredXpLevels);
							tpl.getWorld().playSound(tpl.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
							tpl.getWorld().spawnParticle(Particle.PORTAL, tpl.getLocation(), 40);
							tpl.teleport(target);
							tpl.getWorld().playSound(tpl.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
							tpl.getWorld().spawnParticle(Particle.PORTAL, tpl.getLocation(), 40);
							// Also reset the clock:
							nClockMeta.setDisplayName(ChatColor.RESET + "Clock to Nobody");
							nClockMeta.setLore(Arrays.asList("Hit a player with the clock bind target"));
							nClockMeta.getPersistentDataContainer().remove(targetKey);
							nClock.setItemMeta(nClockMeta);
							if (clockInMainHand) {
								tpl.getInventory().setItemInMainHand(nClock);
							} else {
								tpl.getInventory().setItemInOffHand(nClock);
							}
							tpl.updateInventory();
						}	
					}	
				}
			}
			// Remove task (need to reactivate sneak to do it again)
			sneakTasks.remove(teleportingPlayer);
		}, 40));
	}
}
