package com.forgeessentials.economy.commands;

import net.minecraft.command.ICommandSender;
import net.minecraftforge.permissions.PermissionsManager.RegisteredPermValue;

import com.forgeessentials.api.APIRegistry;
import com.forgeessentials.api.UserIdent;
import com.forgeessentials.api.economy.Wallet;
import com.forgeessentials.core.commands.ParserCommandBase;
import com.forgeessentials.core.misc.TranslatedCommandException;
import com.forgeessentials.core.misc.Translator;
import com.forgeessentials.economy.ModuleEconomy;
import com.forgeessentials.util.CommandParserArgs;
import com.forgeessentials.util.FunctionHelper;
import com.forgeessentials.util.OutputHandler;

public class CommandPay extends ParserCommandBase
{

    @Override
    public String getCommandName()
    {
        return "pay";
    }

    @Override
    public String getPermissionNode()
    {
        return ModuleEconomy.PERM_COMMAND + ".pay";
    }

    @Override
    public RegisteredPermValue getDefaultPermission()
    {
        return RegisteredPermValue.TRUE;
    }

    @Override
    public String getCommandUsage(ICommandSender sender)
    {
        return "/pay <player> <amount>: Pay another player from your wallet";
    }

    @Override
    public boolean canConsoleUseCommand()
    {
        return false;
    }

    @Override
    public void parse(CommandParserArgs arguments)
    {
        if (arguments.isEmpty())
            throw new TranslatedCommandException("Player needed");
        UserIdent player = arguments.parsePlayer(true);
        if (player == null)
            return;

        if (arguments.isEmpty())
            throw new TranslatedCommandException("Missing value");
        Long amount = FunctionHelper.tryParseLong(arguments.remove());
        if (amount == null)
            throw new TranslatedCommandException("Invalid number");
        if (amount < 1)
            throw new TranslatedCommandException("Invalid number");
        
        if (arguments.isTabCompletion)
            return;

        Wallet sender = APIRegistry.economy.getWallet(arguments.ident);
        if (!sender.withdraw(amount))
            throw new TranslatedCommandException("You do not have enough %s in your wallet", APIRegistry.economy.currency(2));
        arguments.confirm(Translator.format("You paid %s to %s. You now have %s", //
                APIRegistry.economy.toString(amount), player.getUsernameOrUUID(), sender.toString()));

        Wallet receiver = APIRegistry.economy.getWallet(player);
        receiver.add(amount);
        OutputHandler.chatConfirmation(player.getPlayerMP(), Translator.format("You were paid %s from %s. You now have %s", //
                APIRegistry.economy.toString(amount), arguments.sender.getCommandSenderName(), receiver.toString()));
    }

}
