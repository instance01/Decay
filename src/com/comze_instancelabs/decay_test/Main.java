package com.comze_instancelabs.decay_test;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;

import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.DataStore;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import me.ryanhamshire.GriefPrevention.PlayerData;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;

/***
 * 
 * @author InstanceLabs
 *
 */

public class Main extends JavaPlugin implements Listener{

	HashMap<String, Integer> decay_ids = new HashMap<String, Integer>();
	HashMap<String, String> pdecays = new HashMap<String, String>();
	
	
	WorldGuardPlugin worldGuard;
	
	public Plugin getWorldGuard(){
    	return Bukkit.getPluginManager().getPlugin("WorldGuard");
    }
	
	@Override
	public void onEnable(){
		getServer().getPluginManager().registerEvents(this, this);
		
		worldGuard = (WorldGuardPlugin) getWorldGuard();
	}
	
	@Override
	public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args){
		if(cmd.getName().equalsIgnoreCase("decay")){
			if(args.length > 3){
				String regionname = args[0];
				int amplifier = Integer.parseInt(args[1]);
				boolean onlyground = Boolean.parseBoolean(args[2]);
				String mode = args[3];
				
				Player p = (Player)sender;
				World w = p.getWorld();
				
				if(!decay_ids.containsKey(regionname)){
					// normal worldguard region
					
					ProtectedRegion rg = worldGuard.getRegionManager(w).getRegion(regionname);
					decay(w, rg, amplifier, onlyground, mode);
					
					// grief prevention claim
					
					//Claim claim = GriefPrevention.instance.dataStore.getClaimAt(p.getLocation(), true, null);
					//decay(w, claim, amplifier);
				}
				sender.sendMessage("§2" + regionname + " will decay now. Amplifier: " + args[1]);
			}else{
				sender.sendMessage("§2Usage: /decay [region] [amplifier:NUMBER] [ONLYGROUND:TRUE/FALSE] [MODE:SPIDERWEB/NATURE]");
				sender.sendMessage("§2You can stop the decay with /stopdecay [region]");
			}
			return true;
		}else if(cmd.getName().equalsIgnoreCase("stopdecay")){
			if(args.length > 0){
				if(decay_ids.containsKey(args[0])){
					getServer().getScheduler().cancelTask(decay_ids.get(args[0]));
					sender.sendMessage("§2Stopped decay.");	
				}else{
					sender.sendMessage("§2No decay process running on this region right now.");
				}
			}else{
				sender.sendMessage("§2Usage: /stopdecay [region]");
			}
			return true;
		}
		return false;
	}
	
	
	@EventHandler
	public void onPlayerJoin(PlayerJoinEvent event){
		// save new player lastlogin date
		SimpleDateFormat sdfToDate = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
		StringBuilder test = new StringBuilder(sdfToDate.format(new Date()));
		getConfig().set(event.getPlayer().getName(), test.toString());
		this.saveConfig();
		
		// check all players
		for(String p_ : getConfig().getKeys(true)){
			PlayerData d = GriefPrevention.instance.dataStore.getPlayerData(p_);
			//Date lastlogin = d.lastLogin;
			ArrayList<Claim> claims = new ArrayList<Claim>(d.claims);
			
			// if lastlogin is 30 days away -> start decay for all claims
			if(checkDays(p_)){
				if(!pdecays.containsKey(p_)){
					for(Claim c : claims){
						// all 10 minutes something decays
						this.decay(event.getPlayer().getWorld(), c, 12000, false, "NATURE");
						getLogger().info("decay started for " + c.ownerName);
					}
					pdecays.put(p_, "");	
				}
			}
		}
	}
	
	
	/*
	 * Possible uses of decay function:
	 * - claims or regions
	 * - spiderweb mode or nature mode
	 * - only on ground blocks change or not
	 * 
	 */
	
	
	/***
	 * Main decay function
	 * @param w World where the region is
	 * @param rg The region which should be modified
	 * @param amplifier The time modifier
	 * @param onlyground If new blocks should also appear in the air or not
	 * @param mode spiderweb or nature mode
	 * 
	 * Spiderweb mode: Adds webs to the region
	 * Nature: Removes built blocks
	 */
	public void decay(World w, ProtectedRegion rg, int amplifier, boolean onlyground, String mode){
		Location l1 = new Location(w, rg.getMinimumPoint().getBlockX(), rg.getMinimumPoint().getBlockY(), rg.getMinimumPoint().getBlockZ());
		Location l2 = new Location(w, rg.getMaximumPoint().getBlockX(), rg.getMaximumPoint().getBlockY(), rg.getMaximumPoint().getBlockZ());
	
		final Cuboid c = new Cuboid(l1, l2);

		final boolean ground = onlyground;
		final World w_ = w;
		final ProtectedRegion rg_ = rg;
		
		if(mode.equalsIgnoreCase("spiderweb")){
			int id = Bukkit.getServer().getScheduler().scheduleSyncRepeatingTask(this, new Runnable() {
				@Override
	            public void run() {
					Location random = null;
					if(ground){
						boolean found = false;
						while(!found){
							Location random_ = c.getRandomLocation();
							if(w_.getBlockAt(new Location(w_, random_.getBlockX(), random_.getBlockY() - 1, random_.getBlockZ())).getType() != Material.AIR && w_.getBlockAt(random_).getType() == Material.AIR){
								found = true;
								random = random_;
							}
						}
					}
					
					if(random == null){
						getLogger().severe("RANDOM LOCATION IS NULL");
						getLogger().severe("SETTING NEW RANDOM LOCATION");
						random = c.getRandomLocation();
					}
					
					random.getBlock().setType(Material.WEB);
				}
	        }, amplifier, amplifier);
			
			decay_ids.put(rg.getId(), id);	
		}else if(mode.equalsIgnoreCase("nature")){
			final int id = Bukkit.getServer().getScheduler().scheduleSyncRepeatingTask(this, new Runnable() {
				@Override
	            public void run() {
					int count = 0;
					for(int x = c.getLowLoc().getBlockX(); x < c.getHighLoc().getBlockX() + 1; x++){
						for(int y = c.getLowLoc().getBlockY(); y < c.getHighLoc().getBlockY() + 1; y++){
							for(int z = c.getLowLoc().getBlockZ(); z < c.getHighLoc().getBlockZ() + 1; z++){
								Location current = new Location(w_, x, y, z);
								if(current.getBlock().getType() != Material.AIR && current.getBlock().getType() != Material.STONE && current.getBlock().getType() != Material.GRASS && current.getBlock().getType() != Material.DIRT && current.getBlock().getType() != Material.CHEST && current.getBlock().getType() != Material.WEB && !current.getBlock().isLiquid()){
									count += 1;
								}
							}
						}
					}
					
					getLogger().info(Integer.toString(count));
					
					if(count > 0){
						boolean found = false;
						Location random = null;
						while(!found){
							Location random_ = c.getRandomLocation();
							if(random_.getBlock().getType() != Material.AIR && random_.getBlock().getType() != Material.STONE && random_.getBlock().getType() != Material.GRASS && random_.getBlock().getType() != Material.DIRT && random_.getBlock().getType() != Material.CHEST && random_.getBlock().getType() != Material.WEB && !random_.getBlock().isLiquid()){
								found = true;
								random = random_;
								count -= 1;
							}
						}
	
						if(random == null){
							getLogger().severe("RANDOM LOCATION IS NULL");
							getLogger().severe("SETTING NEW RANDOM LOCATION");
							random = c.getRandomLocation();
						}
						
						if(random.getBlock().getType() != Material.WEB){
							random.getBlock().setType(Material.AIR);
						}	
					}else{
						getServer().getScheduler().cancelTask(decay_ids.get(rg_.getId()));
					}
					
				}
	        }, amplifier, amplifier);
			decay_ids.put(rg.getId(), id);
		}
		
	}
	
	
	
	/***
	 * Main decay function
	 * @param w World where the region is
	 * @param rg The region which should be modified
	 * @param amplifier The time modifier
	 * @param onlyground If new blocks should also appear in the air or not
	 * @param mode spiderweb or nature mode
	 * 
	 * Spiderweb mode: Adds webs to the region
	 * Nature: Removes built blocks
	 */
	public void decay(World w, Claim rg, int amplifier, boolean onlyground, String mode){
		
		Location l1 = new Location(w, rg.getLesserBoundaryCorner().getBlockX(), 3, rg.getLesserBoundaryCorner().getBlockZ());
		Location l2 = new Location(w, rg.getGreaterBoundaryCorner().getBlockX(), 255, rg.getGreaterBoundaryCorner().getBlockZ());
	
		final Cuboid c = new Cuboid(l1, l2);
		getLogger().info(Long.toString(c.getSize())); 

		
		final boolean ground = onlyground;
		final World w_ = w;
		final Claim rg_ = rg;
		
		if(mode.equalsIgnoreCase("spiderweb")){
			int id = Bukkit.getServer().getScheduler().scheduleSyncRepeatingTask(this, new Runnable() {
				@Override
	            public void run() {
					Location random = null;
					if(ground){
						boolean found = false;
						while(!found){
							Location random_ = c.getRandomLocation();
							if(w_.getBlockAt(new Location(w_, random_.getBlockX(), random_.getBlockY() - 1, random_.getBlockZ())).getType() != Material.AIR && w_.getBlockAt(random_).getType() == Material.AIR){
								found = true;
								random = random_;
							}
						}
					}
					
					if(random == null){
						getLogger().severe("RANDOM LOCATION IS NULL");
						getLogger().severe("SETTING NEW RANDOM LOCATION");
						random = c.getRandomLocation();
					}
					
					random.getBlock().setType(Material.WEB);
				}
	        }, amplifier, amplifier);
			
		}else if(mode.equalsIgnoreCase("nature")){
			final int id = Bukkit.getServer().getScheduler().scheduleSyncRepeatingTask(this, new Runnable() {
				@Override
	            public void run() {
					int count = 0;
					for(int x = c.getLowLoc().getBlockX(); x < c.getHighLoc().getBlockX() + 1; x++){
						for(int y = c.getLowLoc().getBlockY(); y < c.getHighLoc().getBlockY() + 1; y++){
							for(int z = c.getLowLoc().getBlockZ(); z < c.getHighLoc().getBlockZ() + 1; z++){
								Location current = new Location(w_, x, y, z);
								if(current.getBlock().getType() != Material.AIR && current.getBlock().getType() != Material.STONE && current.getBlock().getType() != Material.GRASS && current.getBlock().getType() != Material.DIRT && current.getBlock().getType() != Material.CHEST && current.getBlock().getType() != Material.WEB && !current.getBlock().isLiquid()){
									count += 1;
								}
							}
						}
					}
					
					getLogger().info(Integer.toString(count));
					
					if(count > 0){
						boolean found = false;
						Location random = null;
						while(!found){
							Location random_ = c.getRandomLocation();
							if(random_.getBlock().getType() != Material.AIR && random_.getBlock().getType() != Material.STONE && random_.getBlock().getType() != Material.GRASS && random_.getBlock().getType() != Material.DIRT && random_.getBlock().getType() != Material.CHEST && random_.getBlock().getType() != Material.WEB && !random_.getBlock().isLiquid()){
								found = true;
								random = random_;
								count -= 1;
							}
						}
	
						if(random == null){
							getLogger().severe("RANDOM LOCATION IS NULL");
							getLogger().severe("SETTING NEW RANDOM LOCATION");
							random = c.getRandomLocation();
						}
						
						if(random.getBlock().getType() != Material.WEB){
							random.getBlock().setType(Material.AIR);
						}	
					}else{
						getServer().getScheduler().cancelTask(decay_ids.get(Long.toString(rg_.getID())));
					}
					
				}
	        }, amplifier, amplifier);
			decay_ids.put(Long.toString(rg.getID()), id);
		}
	}
	
	
	
	
	
	
	
	
	/***
	 * Checks if player is able to use the action again
	 * @param p Player to check
	 * @return returns true if last use 1 hour ago, false if not
	 */
	public boolean checkDays(String p){
		SimpleDateFormat sdfToDate = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
		Date datecurrent = new Date();
		String daysdate = getConfig().getString(p);
		//p.sendMessage(daysdate);
		Date date1 = null;
		try {
			date1 = sdfToDate.parse(daysdate);
			System.out.println(date1);
		} catch (ParseException ex2){
			ex2.printStackTrace();
		}
		Integer between = this.daysBetween(datecurrent, date1);
		getLogger().info(Integer.toString(between));
		if(between > 30 || between < -30){
			return true;
		}else{
			return false;
		}
	}
	
		
	public int daysBetween(Date d1, Date d2){
	    long differenceMilliSeconds = d2.getTime() - d1.getTime();
	    long days = differenceMilliSeconds / 1000 / 60 / 60 / 24;
	    return (int) days;
	}
}
