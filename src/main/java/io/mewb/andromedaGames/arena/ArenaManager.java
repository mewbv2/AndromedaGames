package io.mewb.andromedaGames.arena;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardWriter;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.session.ClipboardHolder;
import com.sk89q.worldedit.function.mask.ExistingBlockMask; // For copying existing blocks (non-air)
// RecursiveVisitor is not directly used in this corrected version for the basic save, but good to know for advanced ops.

import io.mewb.andromedaGames.AndromedaGames;
import org.bukkit.Location;
import org.bukkit.World; // Bukkit World

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ArenaManager {

    private final AndromedaGames plugin;
    private final WorldEdit worldEdit; // Instance from FAWEProvider
    private final Logger logger;
    private final File schematicsDir; // Plugin-specific schematics directory

    public ArenaManager(AndromedaGames plugin) {
        this.plugin = plugin;
        // Ensure FAWEProvider and its WorldEdit instance are initialized before this
        if (plugin.getFaweProvider() == null || plugin.getFaweProvider().getFAWE() == null) {
            this.logger = plugin.getLogger(); // Use plugin's logger for this critical error
            logger.severe("FAWEProvider or its WorldEdit instance is null! ArenaManager cannot function.");
            this.worldEdit = null; // Explicitly set to null
        } else {
            this.worldEdit = plugin.getFaweProvider().getFAWE();
            this.logger = plugin.getLogger();
        }


        // Define a custom schematics directory within this plugin's data folder
        this.schematicsDir = new File(plugin.getDataFolder(), "schematics");
        if (!schematicsDir.exists()) {
            if (schematicsDir.mkdirs()) {
                logger.info("Created plugin schematics directory: " + schematicsDir.getAbsolutePath());
            } else {
                logger.severe("Could not create plugin schematics directory: " + schematicsDir.getAbsolutePath());
            }
        } else {
            logger.info("Plugin schematics directory already exists: " + schematicsDir.getAbsolutePath());
        }
    }

    /**
     * Pastes a schematic file into the world at the specified location.
     * The schematic is looked for in the plugin's dedicated schematics folder.
     *
     * @param schematicName The name of the schematic file (e.g., "koth_mountain" or "koth_mountain.schem").
     * @param pasteLocation The Bukkit Location where the schematic should be pasted (this is the origin of the paste).
     * @return True if pasting was successful or queued, false otherwise.
     */
    public boolean pasteSchematic(String schematicName, Location pasteLocation) {
        if (worldEdit == null) {
            logger.severe("WorldEdit is not available. Cannot paste schematic '" + schematicName + "'.");
            return false;
        }
        if (schematicName == null || schematicName.trim().isEmpty()) {
            logger.severe("Schematic name is null or empty. Cannot paste.");
            return false;
        }
        if (pasteLocation == null || pasteLocation.getWorld() == null) {
            logger.severe("Paste location or its world is null. Cannot paste schematic '" + schematicName + "'.");
            return false;
        }

        String fileName = schematicName.endsWith(".schem") ? schematicName : schematicName + ".schem";
        File schematicFile = new File(schematicsDir, fileName);

        if (!schematicFile.exists() || !schematicFile.isFile()) {
            logger.severe("Schematic file not found or is not a file: " + schematicFile.getAbsolutePath());
            return false;
        }

        ClipboardFormat format = ClipboardFormats.findByFile(schematicFile);
        if (format == null) {
            logger.severe("Could not determine clipboard format for: " + schematicFile.getName() + ". Ensure it's a valid .schem file.");
            return false;
        }

        try (FileInputStream fis = new FileInputStream(schematicFile);
             ClipboardReader reader = format.getReader(fis)) {

            Clipboard clipboard = reader.read();
            com.sk89q.worldedit.world.World weWorld = BukkitAdapter.adapt(pasteLocation.getWorld());

            // Using FAWE's EditSession for performance and proper operation handling
            try (EditSession editSession = worldEdit.newEditSession(weWorld)) {
                // Configure the EditSession as needed
                // editSession.setBypassHistory(true); // If undo is not needed for this operation

                Operation operation = new ClipboardHolder(clipboard)
                        .createPaste(editSession)
                        .to(BlockVector3.at(pasteLocation.getX(), pasteLocation.getY(), pasteLocation.getZ()))
                        .ignoreAirBlocks(false) // Set to false to paste air blocks from schematic, true to skip them
                        .build();
                Operations.complete(operation); // Execute the paste operation
                logger.info("Successfully pasted schematic '" + fileName + "' at " + pasteLocation.toString());
                return true;
            }
        } catch (IOException | WorldEditException e) {
            logger.log(Level.SEVERE, "Error pasting schematic '" + fileName + "': " + e.getMessage(), e);
            return false;
        }
    }

    /**
     * Saves a region of a Bukkit world to a schematic file in the plugin's schematics folder.
     *
     * @param world The Bukkit world from which to copy.
     * @param corner1 The first corner of the CuboidRegion to save.
     * @param corner2 The second corner of the CuboidRegion to save.
     * @param origin The Bukkit Location to use as the origin of the schematic. Often the player's location or a corner.
     * @param schematicName The name for the new schematic file (e.g., "my_arena"). ".schem" will be appended if not present.
     * @param copyAir If true, air blocks will be included in the schematic. If false, only non-air blocks are copied.
     * @return True if saving was successful, false otherwise.
     */
    public boolean saveSchematic(World world, Location corner1, Location corner2, Location origin, String schematicName, boolean copyAir) {
        if (worldEdit == null) {
            logger.severe("WorldEdit is not available. Cannot save schematic '" + schematicName + "'.");
            return false;
        }
        if (schematicName == null || schematicName.trim().isEmpty()) {
            logger.severe("Schematic name is null or empty. Cannot save.");
            return false;
        }

        String fileName = schematicName.endsWith(".schem") ? schematicName : schematicName + ".schem";
        File schematicFile = new File(schematicsDir, fileName);

        com.sk89q.worldedit.world.World weWorld = BukkitAdapter.adapt(world);
        BlockVector3 L1 = BlockVector3.at(corner1.getBlockX(), corner1.getBlockY(), corner1.getBlockZ());
        BlockVector3 L2 = BlockVector3.at(corner2.getBlockX(), corner2.getBlockY(), corner2.getBlockZ());
        BlockVector3 weOrigin = BlockVector3.at(origin.getBlockX(), origin.getBlockY(), origin.getBlockZ());

        CuboidRegion region = new CuboidRegion(weWorld, L1, L2);
        // Create a new clipboard. The origin specified here is the point in the clipboard that corresponds to (0,0,0)
        // when pasting. It's often set relative to the region's min point or a player's position.
        Clipboard clipboard = Clipboard.create(region);


        try (EditSession editSession = worldEdit.newEditSession(weWorld)) {
            com.sk89q.worldedit.function.operation.ForwardExtentCopy copyOperation =
                    new com.sk89q.worldedit.function.operation.ForwardExtentCopy(
                            editSession,             // Source Extent (the world)
                            region,                  // Source Region to copy from
                            clipboard,               // Destination Clipboard
                            region.getMinimumPoint() // Offset within the source region (usually minPoint)
                    );

            if (!copyAir) {
                // If we don't want to copy air, set a source mask that only includes existing blocks.
                copyOperation.setSourceMask(new ExistingBlockMask(editSession));
            }
            // If copyAir is true, no mask is needed, it will copy air by default.

            // If you need to copy entities: copyOperation.setCopyingEntities(true);

            Operations.complete(copyOperation); // Execute the copy operation into the clipboard

        } catch (WorldEditException e) {
            logger.log(Level.SEVERE, "Error copying region to clipboard for schematic '" + fileName + "': " + e.getMessage(), e);
            return false;
        }

        // Now write the clipboard to a file using Sponge V3 schematic format (.schem)
        // ClipboardFormats.SPONGE_SCHEMATIC is the correct static field.
        ClipboardFormat format = ClipboardFormats.findByFile(schematicFile);
        try (FileOutputStream fos = new FileOutputStream(schematicFile);
             ClipboardWriter writer = format.getWriter(fos)) {
            writer.write(clipboard);
            logger.info("Successfully saved schematic '" + fileName + "' to " + schematicFile.getAbsolutePath());
            return true;
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Error writing schematic file '" + fileName + "': " + e.getMessage(), e);
            return false;
        }
    }
}
