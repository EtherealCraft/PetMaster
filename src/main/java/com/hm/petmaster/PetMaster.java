package com.hm.petmaster;

import com.hm.mcshared.file.CommentedYamlConfiguration;
import com.hm.mcshared.update.UpdateChecker;
import com.hm.petmaster.command.*;
import com.hm.petmaster.files.PetAbilityFile;
import com.hm.petmaster.listener.*;
import com.hm.petmaster.utils.MessageSender;
import com.hm.petmaster.utils.TabCompleterPetm;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;

/**
 * Manage pets and display useful information via holograms, action bar or chat messages!
 *
 * PetMaster is under GNU General Public License version 3. Please visit the plugin's GitHub for more information :
 * https://github.com/PyvesB/PetMaster
 *
 * Official plugin's server: hellominecraft.fr
 *
 * Bukkit project page: dev.bukkit.org/bukkit-plugins/pet-master
 *
 * Spigot project page: spigotmc.org/resources/pet-master.15904
 *
 * @since December 2015.
 * @version 1.12.5
 * @author DarkPyves
 */
public class PetMaster extends JavaPlugin {

	// Plugin options and various parameters.
	private String chatHeader;
	private boolean updatePerformed;
	private int serverVersion;

	// Fields related to file handling.
	private CommentedYamlConfiguration config;
	private CommentedYamlConfiguration lang;

	// Plugin listeners.
	private PlayerInteractListener playerInteractListener;
	private PlayerLeashListener playerLeashListener;
	private PlayerQuitListener playerQuitListener;
	private PlayerAttackListener playerAttackListener;
	private PlayerTameListener playerTameListener;
	private PlayerBreedListener playerBreedListener;

	// Used to check for plugin updates.
	private UpdateChecker updateChecker;

	// Additional classes related to plugin commands.
	private HelpCommand helpCommand;
	private InfoCommand infoCommand;
	private SetOwnerCommand setOwnerCommand;
	private FreeCommand freeCommand;
	private EnableDisableCommand enableDisableCommand;
	private ReloadCommand reloadCommand;
	private SetColorCommand setColorCommand;
	private ShareCommand shareCommand;
	private PetInvincibleCommand petInvincibleCommand;
	private PetSkillCommand petSkillCommand;

	//Messageing System
	private BukkitAudiences adventure;
	private MessageSender messageSender;

	public @NotNull BukkitAudiences adventure() {
		if(this.adventure == null) {
			throw new IllegalStateException("Tried to access Adventure when the plugin was disabled!");
		}
		return this.adventure;
	}

	/**
	 * Called when server is launched or reloaded.
	 */
	@Override
	public void onEnable() {
		// Start enabling plugin.
		long startTime = System.currentTimeMillis();

		// Initializing the Messaging System
		this.adventure = BukkitAudiences.create(this);
		this.messageSender = new MessageSender(this);

		getLogger().info("Server version..." + Bukkit.getServer().getBukkitVersion());
		getLogger().info("Registered subVersion..." + Bukkit.getServer().getBukkitVersion().replace(".", ",").split(",")[1].split("-")[0]);
		getLogger().info("Registering listeners...");

		serverVersion = Integer.parseInt(
				Bukkit.getServer().getBukkitVersion().replace(".", ",").split(",")[1].split("-")[0]);

		playerInteractListener = new PlayerInteractListener(this);
		playerLeashListener = new PlayerLeashListener(this);
		playerQuitListener = new PlayerQuitListener(this);
		playerTameListener = new PlayerTameListener(this);
		playerBreedListener = new PlayerBreedListener(this);

		PluginManager pm = getServer().getPluginManager();
		// Register listeners.
		pm.registerEvents(playerInteractListener, this);
		pm.registerEvents(playerLeashListener, this);
		pm.registerEvents(playerQuitListener, this);
		pm.registerEvents(playerTameListener, this);
		if (getServerVersion() >= 10) {
			pm.registerEvents(playerBreedListener, this);
		}

		extractParametersFromConfig(true);

		PetAbilityFile.petAbilitySetup();
		PetAbilityFile.getPetAbilities().options().copyDefaults(true);
		PetAbilityFile.petAbilitySave();

		chatHeader = ChatColor.GRAY + "[" + ChatColor.GOLD + "\u265E" + ChatColor.GRAY + "] ";

		File playerColorConfig = new File(getDataFolder() + File.separator + "playersettings.yml");

		helpCommand = new HelpCommand(this);
		infoCommand = new InfoCommand(this);
		setOwnerCommand = new SetOwnerCommand(this);
		freeCommand = new FreeCommand(this);
		enableDisableCommand = new EnableDisableCommand(this);
		reloadCommand = new ReloadCommand(this);
		setColorCommand = new SetColorCommand(this, playerColorConfig);
		shareCommand = new ShareCommand(this);
		petInvincibleCommand = new PetInvincibleCommand(this);
		petSkillCommand = new PetSkillCommand(this);

		// Warn if an outdated entry is contained in the language file
		if (lang.contains("petmaster-command-info-hover")){
			getLogger().log(Level.WARNING, "Your language file contains outdated entrys! It is highly reccomended to delete it and let it regenerate so that all messages appear correctly.");
		}

		getCommand("petm").setTabCompleter(new TabCompleterPetm());
		if (getServer().getPluginManager().isPluginEnabled(this)) {
			getLogger().info("Plugin enabled and ready to run! Took " + (System.currentTimeMillis() - startTime) + "ms.");
		}
	}

	/**
	 * Extracts plugin parameters from the configuration file.
	 *
	 * @param attemptUpdate
	 */
	public void extractParametersFromConfig(boolean attemptUpdate) {
		getLogger().info("Backing up and loading configuration files...");

		config = loadAndBackupYamlConfiguration("config.yml");
		lang = loadAndBackupYamlConfiguration(config.getString("languageFileName", "lang.yml"));

		if (!getServer().getPluginManager().isPluginEnabled(this)) {
			return;
		}

		// Update configurations from previous versions of the plugin if server reloads or restarts.
		if (attemptUpdate) {
			updateOldConfiguration();
			updateOldLanguage();
		}

		playerInteractListener.extractParameters();
		playerLeashListener.extractParameters();

		if (config.getBoolean("checkForUpdate", true)) {
			if (updateChecker == null) {
				updateChecker = new UpdateChecker(this, "https://raw.githubusercontent.com/PyvesB/PetMaster/master/pom.xml",
						"petmaster.admin", chatHeader, "spigotmc.org/resources/pet-master.15904");
				getServer().getPluginManager().registerEvents(updateChecker, this);
				updateChecker.launchUpdateCheckerTask();
			}
		} else {
			PlayerJoinEvent.getHandlerList().unregister(updateChecker);
			updateChecker = null;
		}

		if (config.getBoolean("disablePlayerDamage", false)) {
			if (playerAttackListener == null) {
				playerAttackListener = new PlayerAttackListener(this);
				getServer().getPluginManager().registerEvents(playerAttackListener, this);
				playerAttackListener.extractParameters();
			}

		} else {
			if (playerAttackListener != null) {
				HandlerList.unregisterAll(playerAttackListener);
				playerAttackListener = null;
			}
		}
	}

	/**
	 * Loads and backs up file fileName.
	 *
	 * @param fileName
	 * @return the loaded CommentedYamlConfiguration
	 */
	private CommentedYamlConfiguration loadAndBackupYamlConfiguration(String fileName) {
		CommentedYamlConfiguration yamlConfiguration = new CommentedYamlConfiguration(fileName, this);
		try {
			yamlConfiguration.loadConfiguration();
		} catch (IOException | InvalidConfigurationException e) {
			getLogger().severe("Error while loading " + fileName + " file, disabling plugin.");
			getLogger().log(Level.SEVERE,
					"Verify your syntax by visiting yaml-online-parser.appspot.com and using the following logs: ", e);
			getServer().getPluginManager().disablePlugin(this);
		}

		try {
			yamlConfiguration.backupConfiguration();
		} catch (IOException e) {
			getLogger().log(Level.SEVERE, "Error while backing up configuration file: ", e);
		}
		return yamlConfiguration;
	}

	/**
	 * Updates configuration file from older plugin versions by adding missing parameters. Upgrades from versions prior
	 * to 1.2 are not supported.
	 */
	private void updateOldConfiguration() {
		updatePerformed = false;

		updateSetting(config, "languageFileName", "lang.yml", "Name of the language file.");
		updateSetting(config, "checkForUpdate", true,
				"Check for update on plugin launch and notify when an OP joins the game.");
		updateSetting(config, "changeOwnerPrice", 0, "Price of the /petm setowner command (requires Vault).");
		updateSetting(config, "displayDog", true, "Take dogs into account.");
		updateSetting(config, "displayCat", true, "Take cats into account.");
		updateSetting(config, "displayHorse", true, "Take horses into account.");
		updateSetting(config, "displayLlama", true, "Take llamas into account.");
		updateSetting(config, "displayParrot", true, "Take parrots into account.");
		updateSetting(config, "actionBarMessage", false,
				"Enable or disable action bar messages when right-clicking on a pet.");
		updateSetting(config, "displayToOwner", false,
				"Enable or disable showing ownership information for a player's own pets.");
		updateSetting(config, "freePetPrice", 0, "Price of the /petm free command (requires Vault).");
		updateSetting(config, "showHealth", true,
				"Show health next to owner in chat and action bar messages (not holograms).");
		updateSetting(config, "disablePlayerDamage", false, "Protect pets to avoid being hurt by other player.");
		updateSetting(config, "enableAngryMobPlayerDamage", true,
				"Allows players to defend themselves against angry tamed mobs (e.g. dogs) even if disablePlayerDamage is true.");
		updateSetting(config, "disableLeash", false, "Prevent others from using leash on pet.");
		updateSetting(config, "disableRiding", false, "Prevent others from mounting pet (horse/donkey).");

		if (updatePerformed) {
			// Changes in the configuration: save and do a fresh load.
			try {
				config.saveConfiguration();
				config.loadConfiguration();
			} catch (IOException | InvalidConfigurationException e) {
				getLogger().log(Level.SEVERE, "Error while saving changes to the configuration file: ", e);
			}
		}
	}

	/**
	 * Updates language file from older plugin versions by adding missing parameters. Upgrades from versions prior to
	 * 1.2 are not supported.
	 */
	private void updateOldLanguage() {
		updatePerformed = false;

		updateSetting(lang, "petmaster-help-header", "<prefix> <gold>------------------ ♞<bold>PetMaster</bold>♞  ------------------");
		updateSetting(lang, "petmaster-prefix", "<gray>[<gold>♞<gray>] ");

		if (updatePerformed) {
			// Changes in the language file: save and do a fresh load.
			try {
				lang.saveConfiguration();
				lang.loadConfiguration();
			} catch (IOException | InvalidConfigurationException e) {
				getLogger().log(Level.SEVERE, "Error while saving changes to the language file: ", e);
			}
		}
	}

	/**
	 * Called when server is stopped or reloaded.
	 */
	@Override
	public void onDisable() {
		// Closing Adventure API
		if(this.adventure != null) {
			this.adventure.close();
			this.adventure = null;
		}
		getLogger().info("PetMaster has been disabled.");
	}

	/**
	 * Called when a player or the console enters a command.
	 */
	@Override
	public boolean onCommand(CommandSender sender, Command cmd, String commandLabel, String[] args) {
		if (!"petm".equalsIgnoreCase(cmd.getName())) {
			return false;
		}

		if (args.length == 0 || args.length == 1 && "help".equalsIgnoreCase(args[0])) {
			helpCommand.getHelp(sender);
		} else if ("info".equalsIgnoreCase(args[0])) {
			infoCommand.getInfo(sender);
		} else if ("reload".equalsIgnoreCase(args[0])) {
			reloadCommand.reload(sender);
		} else if ("disable".equalsIgnoreCase(args[0])) {
			enableDisableCommand.setState(sender, true);
		} else if ("enable".equalsIgnoreCase(args[0])) {
			enableDisableCommand.setState(sender, false);
		} else if ("setowner".equalsIgnoreCase(args[0]) && sender instanceof Player) {
			setOwnerCommand.setOwner(((Player) sender), args);
		} else if ("free".equalsIgnoreCase(args[0]) && sender instanceof Player) {
			freeCommand.freePet(((Player) sender), args);
		} else if ("setcolor".equalsIgnoreCase(args[0]) && sender instanceof Player) {
			setColorCommand.setColor(((Player) sender), args);
		} else if("sharepet".equalsIgnoreCase(args[0]) && sender instanceof Player) {
			shareCommand.sharePetCommand((Player) sender);
		} else if("godpet".equalsIgnoreCase(args[0]) && sender instanceof Player) {
			petInvincibleCommand.godPetCommand((Player)sender);
		} else if ("petskill".equalsIgnoreCase(args[0]) && sender instanceof Player) {
			petSkillCommand.petSkillCommand((Player)sender);
		} else {
			getMessageSender().sendMessage(sender, "misused-command");
		}
		return true;
	}

	/**
	 * Updates the configuration file to include a new setting with its default value and its comments.
	 *
	 * @param file
	 * @param name
	 * @param value
	 * @param comments
	 */
	private void updateSetting(CommentedYamlConfiguration file, String name, Object value, String... comments) {
		if (!file.getKeys(false).contains(name)) {
			file.set(name, value, comments);
			updatePerformed = true;
		}
	}

	public int getServerVersion() {
		return serverVersion;
	}

	@Deprecated
	public String getChatHeader() {
		return chatHeader;
	}

	public CommentedYamlConfiguration getPluginConfig() {
		return config;
	}

	public CommentedYamlConfiguration getPluginLang() {
		return lang;
	}

	public SetOwnerCommand getSetOwnerCommand() {
		return setOwnerCommand;
	}

	public FreeCommand getFreeCommand() {
		return freeCommand;
	}

	public EnableDisableCommand getEnableDisableCommand() {
		return enableDisableCommand;
	}

	public SetColorCommand getSetColorCommand() {
		return setColorCommand;
	}

	public MessageSender getMessageSender(){
		return messageSender;
	}
}
