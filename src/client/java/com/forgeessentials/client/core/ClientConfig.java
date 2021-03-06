package com.forgeessentials.client.core;

import com.forgeessentials.client.ForgeEssentialsClient;
import cpw.mods.fml.client.event.ConfigChangedEvent;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;

@SideOnly(Side.CLIENT)
public class ClientConfig {

    public static Configuration config;

    public ClientConfig(Configuration config)
    {
        ClientConfig.config = config;
        FMLCommonHandler.instance().bus().register(this);
    }

    public void init()
    {
        config.load();
        config.addCustomCategoryComment(Configuration.CATEGORY_GENERAL, "Configure ForgeEssentials .");

        Property prop = config.get(Configuration.CATEGORY_GENERAL, "allowCUI", true);
        prop.comment = "Set to false to disable graphical selections.";
        ForgeEssentialsClient.allowCUI = prop.getBoolean(true);
        // any other parts please config here
        config.save();
    }

    @SubscribeEvent
    public void onConfigChanged(ConfigChangedEvent.OnConfigChangedEvent e) {
        if(e.modID.equals("ForgeEssentialsClient"))
            syncConfig();
    }

    public void syncConfig()
    {
        ForgeEssentialsClient.allowCUI = config.getBoolean("allowCUI", Configuration.CATEGORY_GENERAL, true, "Set to false to disable graphical selections.");

    }
}
