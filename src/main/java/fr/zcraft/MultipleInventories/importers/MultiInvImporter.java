/*
 * Copyright or © or Copr. AmauryCarrade (2015)
 * 
 * http://amaury.carrade.eu
 * 
 * This software is governed by the CeCILL-B license under French law and
 * abiding by the rules of distribution of free software.  You can  use, 
 * modify and/ or redistribute the software under the terms of the CeCILL-B
 * license as circulated by CEA, CNRS and INRIA at the following URL
 * "http://www.cecill.info". 
 * 
 * As a counterpart to the access to the source code and  rights to copy,
 * modify and redistribute granted by the license, users are provided only
 * with a limited warranty  and the software's author,  the holder of the
 * economic rights,  and the successive licensors  have only  limited
 * liability. 
 * 
 * In this respect, the user's attention is drawn to the risks associated
 * with loading,  using,  modifying and/or developing or reproducing the
 * software by the user in light of its specific status of free software,
 * that may mean  that it is complicated to manipulate,  and  that  also
 * therefore means  that it is reserved for developers  and  experienced
 * professionals having in-depth computer knowledge. Users are therefore
 * encouraged to load and test the software's suitability as regards their
 * requirements in conditions enabling the security of their systems and/or 
 * data to be ensured and,  more generally, to use and operate it in the 
 * same conditions as regards security. 
 * 
 * The fact that you are presently reading this means that you have had
 * knowledge of the CeCILL-B license and that you accept its terms.
 */
package fr.zcraft.MultipleInventories.importers;

import fr.zcraft.MultipleInventories.snaphots.ItemStackSnapshot;
import fr.zcraft.MultipleInventories.snaphots.PlayerSnapshot;
import fr.zcraft.quartzlib.components.i18n.I;
import fr.zcraft.quartzlib.tools.PluginLogger;
import fr.zcraft.quartzlib.tools.reflection.Reflection;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;
import uk.co.tggl.pluckerpluck.multiinv.MultiInv;
import uk.co.tggl.pluckerpluck.multiinv.api.MIAPIPlayer;
import uk.co.tggl.pluckerpluck.multiinv.inventory.MIEnderchestInventory;
import uk.co.tggl.pluckerpluck.multiinv.inventory.MIInventory;
import uk.co.tggl.pluckerpluck.multiinv.inventory.MIItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;


public class MultiInvImporter implements Importer
{
    private MultiInv multiInv = null;

    private Map<String, Set<String>> groups = null;

    public MultiInvImporter()
    {
        final Plugin miPlugin = Bukkit.getPluginManager().getPlugin("MultiInv");

        if (miPlugin == null)
        {
            return;
        }
        else if (!miPlugin.isEnabled())
        {
            Bukkit.getServer().getPluginManager().enablePlugin(miPlugin);
            if (!miPlugin.isEnabled()) return;
        }

        multiInv = (MultiInv) miPlugin;
    }

    @Override
    public boolean canImport()
    {
        return multiInv != null;
    }

    @Override
    public void onBegin()
    {
        getWorldGroups();
    }

    @Override
    public void onEnd()
    {
        PluginLogger.info(I.t("The migration finished! Now, please remove MultiInv and reboot the server. Then, the players will be able to connect again, and the whole process will be done."));
    }

    @Override
    public Map<String, Set<String>> getWorldGroups()
    {
        if (groups != null) return groups;

        final Map<String, String> reversedGroups = multiInv.getAPI().getGroups();
        final Map<String, Set<String>> groups = new HashMap<>();

        for (final Map.Entry<String, String> group : reversedGroups.entrySet())
        {
            final Set<String> worldsInGroup = groups.containsKey(group.getValue()) ? groups.get(group.getValue()) : new HashSet<>();
            worldsInGroup.add(group.getKey());
            groups.put(group.getValue(), worldsInGroup);
        }

        this.groups = groups;
        return groups;
    }

    @Override
    public PlayerSnapshot importSnapshot(final OfflinePlayer player, final String group, final GameMode mode)
    {
        final Set<String> worldsInGroup = groups.get(group);
        if (worldsInGroup == null || worldsInGroup.isEmpty()) return null;

        final MIAPIPlayer miSnapshot;
        try
        {
            miSnapshot = multiInv.getAPI().getPlayerInstance(player, worldsInGroup.iterator().next(), mode);
            if (miSnapshot == null) return null;
        }
        catch (IllegalArgumentException e)
        {
            // If the player's name in the OfflinePlayer object is null, the MultiInvAPI
            // may throw an IllegalArgumentException. This can happens with very old servers
            // still having old players files in their playerdata folder.
            PluginLogger.error("Unable to import data for player “{0}” (UUID: {1}).", player.getName(), player.getUniqueId());
            return null;
        }

        final MIInventory inventory = miSnapshot.getInventory();
        final MIEnderchestInventory enderChest = miSnapshot.getEnderchest();

        // We check if it's a snapshot with no data, as MultiInv will return an object frequently even without anything in it.
        // The data then looks like this:
        // {"level":0,"exp":0.0,"expTotal":-1,"foodLevel":20,"exhaustion":0.0,"saturation":5.0,"health":20.0,"maxHealth":20.0,
        // "armor":[null,null,null,null],"inventory":{},"enderChest":{},"effects":[]}

        boolean hasInventory = false;
        boolean hasArmor = false;
        boolean hasEnderChest = false;
        boolean hasEffects = false;

        if (inventory != null)
        {
            hasInventory = !isInventoryEmpty(inventory.getInventoryContents());
            hasArmor = !isInventoryEmpty(inventory.getArmorContents());
            hasEffects = !inventory.getPotions().isEmpty();
        }

        if (enderChest != null)
        {
            hasEnderChest = !isInventoryEmpty(enderChest.getInventoryContents());
        }

        if (miSnapshot.getXpLevel() == 0 && miSnapshot.getXp() <= 0.1
                && miSnapshot.getFoodlevel() == 20 && miSnapshot.getHealth() >= 19.5
                && !hasInventory && !hasArmor && !hasEnderChest
                && !hasEffects)
        {
            return null;
        }

        return new PlayerSnapshot(
                miSnapshot.getXpLevel(),
                miSnapshot.getXp(),
                -1,
                miSnapshot.getFoodlevel(),
                0,
                miSnapshot.getSaturation(),
                miSnapshot.getHealth(),
                20,
                inventory  != null ? importInventory(inventory.getInventoryContents())    : new HashMap<>(),
                enderChest != null ? importInventory(enderChest.getInventoryContents())   : new HashMap<>(),
                inventory  != null ? importInventoryToArray(inventory.getArmorContents()) : new ItemStackSnapshot[] {null, null, null, null},
                inventory  != null ? inventory.getPotions()                               : new ArrayList<>()
        );
    }

    private boolean isInventoryEmpty(MIItemStack[] inventory)
    {
        for (MIItemStack miItemStack : inventory)
        {
            try
            {
                if (miItemStack != null && Reflection.getFieldValue(miItemStack, "item") != Material.AIR)
                {
                    return false;
                }
            }
            catch (NoSuchFieldException | IllegalAccessException e)
            {
                PluginLogger.error("Error while retrieving item type from MIItemStack", e);
            }
        }

        return true;
    }

    private Map<Integer, ItemStackSnapshot> importInventory(MIItemStack[] items)
    {
        final Map<Integer, ItemStackSnapshot> map = new HashMap<>();

        if (items == null || items.length == 0) return map;

        for (int i = 0; i < items.length; i++)
        {
            if (items[i] != null)
            {
                map.put(i, ItemStackSnapshot.snap(items[i].getItemStack()));
            }
        }

        return map;
    }

    private ItemStackSnapshot[] importInventoryToArray(MIItemStack[] items)
    {
        return Arrays.stream(items)
                     .map(item -> item != null ? ItemStackSnapshot.snap(item.getItemStack()) : null)
                     .toArray(ItemStackSnapshot[]::new);
    }
}
