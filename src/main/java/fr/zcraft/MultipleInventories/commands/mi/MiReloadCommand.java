package fr.zcraft.MultipleInventories.commands.mi;

import fr.zcraft.MultipleInventories.Permissions;
import fr.zcraft.MultipleInventories.snaphots.PlayerSnapshot;
import fr.zcraft.zlib.components.commands.Command;
import fr.zcraft.zlib.components.commands.CommandException;
import fr.zcraft.zlib.components.commands.CommandInfo;
import fr.zcraft.zlib.tools.PluginLogger;
import fr.zcraft.zlib.tools.runners.RunTask;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;


@CommandInfo (name = "reload", usageParameters = "")
public final class MiReloadCommand extends Command
{
    @Override
    protected void run() throws CommandException
    {
        final Player player = playerSender();
        final PlayerSnapshot snapshot = PlayerSnapshot.snap(player);
        final String jsonDump = snapshot.toJSONString();

        PluginLogger.info(jsonDump);

        player.getInventory().clear();
        player.getEnderChest().clear();
        for (PotionEffect effect : player.getActivePotionEffects()) player.removePotionEffect(effect.getType());

        RunTask.later(new Runnable() {
            @Override
            public void run()
            {
                PlayerSnapshot.fromJSONString(jsonDump).reconstruct(player);
            }
        }, 20l);
    }

    @Override
    public boolean canExecute(CommandSender sender)
    {
        return Permissions.RELOAD.grantedTo(sender);
    }
}