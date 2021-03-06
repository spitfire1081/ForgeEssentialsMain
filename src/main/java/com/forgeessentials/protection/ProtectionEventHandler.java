package com.forgeessentials.protection;

import static cpw.mods.fml.common.eventhandler.Event.Result.ALLOW;
import static cpw.mods.fml.common.eventhandler.Event.Result.DENY;
import static net.minecraftforge.event.entity.player.PlayerInteractEvent.Action.LEFT_CLICK_BLOCK;
import static net.minecraftforge.event.entity.player.PlayerInteractEvent.Action.RIGHT_CLICK_AIR;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.WorldSettings.GameType;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LivingSpawnEvent.CheckSpawn;
import net.minecraftforge.event.entity.living.LivingSpawnEvent.SpecialSpawn;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.EntityInteractEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent.Action;
import net.minecraftforge.event.entity.player.PlayerOpenContainerEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.event.world.BlockEvent.BreakEvent;
import net.minecraftforge.event.world.BlockEvent.HarvestDropsEvent;
import net.minecraftforge.event.world.ExplosionEvent;

import com.forgeessentials.api.APIRegistry;
import com.forgeessentials.api.UserIdent;
import com.forgeessentials.api.permissions.AreaZone;
import com.forgeessentials.api.permissions.WorldZone;
import com.forgeessentials.api.permissions.Zone;
import com.forgeessentials.commons.selections.Point;
import com.forgeessentials.commons.selections.WarpPoint;
import com.forgeessentials.commons.selections.WorldArea;
import com.forgeessentials.commons.selections.WorldPoint;
import com.forgeessentials.core.misc.Translator;
import com.forgeessentials.protection.effect.CommandEffect;
import com.forgeessentials.protection.effect.DamageEffect;
import com.forgeessentials.protection.effect.PotionEffect;
import com.forgeessentials.protection.effect.ZoneEffect;
import com.forgeessentials.util.FunctionHelper;
import com.forgeessentials.util.OutputHandler;
import com.forgeessentials.util.PlayerInfo;
import com.forgeessentials.util.TimeoutHandler;
import com.forgeessentials.util.events.PlayerChangedZone;
import com.forgeessentials.util.events.ServerEventHandler;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.Event.Result;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent.PlayerLoggedInEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;

public class ProtectionEventHandler extends ServerEventHandler
{

    /* ------------------------------------------------------------ */
    /* Entity permissions */

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void attackEntityEvent(AttackEntityEvent event)
    {
        if (FMLCommonHandler.instance().getEffectiveSide().isClient())
            return;

        if (event.target == null)
            return;

        EntityPlayer source = event.entityPlayer;
        UserIdent sourceIdent = UserIdent.get(source);
        if (event.target instanceof EntityPlayer)
        {
            // player -> player
            EntityPlayer target = (EntityPlayer) event.target;
            if (!APIRegistry.perms.checkUserPermission(UserIdent.get(target), ModuleProtection.PERM_PVP)
                    || !APIRegistry.perms.checkUserPermission(sourceIdent, ModuleProtection.PERM_PVP)
                    || !APIRegistry.perms.checkUserPermission(sourceIdent, new WorldPoint(target), ModuleProtection.PERM_PVP))
            {
                event.setCanceled(true);
                return;
            }
        }

        // player -> entity
        Entity target = event.target;
        WorldPoint targetPos = new WorldPoint(event.target);

        String permission = ModuleProtection.PERM_DAMAGE_TO + "." + EntityList.getEntityString(target);
        if (ModuleProtection.isDebugMode(source))
            OutputHandler.chatNotification(source, permission);
        if (!APIRegistry.perms.checkUserPermission(sourceIdent, targetPos, permission))
        {
            event.setCanceled(true);
            return;
        }

        permission = MobType.getMobType(target).getDamageToPermission();
        if (ModuleProtection.isDebugMode(source))
            OutputHandler.chatNotification(source, permission);
        if (!APIRegistry.perms.checkUserPermission(sourceIdent, targetPos, permission))
        {
            event.setCanceled(true);
            return;
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void livingHurtEvent(LivingHurtEvent event)
    {
        if (FMLCommonHandler.instance().getEffectiveSide().isClient())
            return;

        if (event.entityLiving == null)
            return;

        if (event.entityLiving instanceof EntityPlayer)
        {
            // living -> player (fall-damage, mob, dispenser, lava)
            EntityPlayer target = (EntityPlayer) event.entityLiving;
            {
                String permission = ModuleProtection.PERM_DAMAGE_BY + "." + event.source.damageType;
                if (ModuleProtection.isDebugMode(target))
                    OutputHandler.chatNotification(target, permission);
                if (!APIRegistry.perms.checkUserPermission(UserIdent.get(target), permission))
                {
                    event.setCanceled(true);
                    return;
                }
            }

            if (event.source.getEntity() != null)
            {
                // non-player-entity -> player (mob)
                Entity source = event.source.getEntity();
                String permission = ModuleProtection.PERM_DAMAGE_BY + "." + EntityList.getEntityString(source);
                if (ModuleProtection.isDebugMode(target))
                    OutputHandler.chatNotification(target, permission);
                if (!APIRegistry.perms.checkUserPermission(UserIdent.get(target), permission))
                {
                    event.setCanceled(true);
                    return;
                }
                permission = MobType.getMobType(source).getDamageByPermission();
                if (ModuleProtection.isDebugMode(target))
                    OutputHandler.chatNotification(target, permission);
                if (!APIRegistry.perms.checkUserPermission(UserIdent.get(target), permission))
                {
                    event.setCanceled(true);
                    return;
                }
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void entityInteractEvent(EntityInteractEvent event)
    {
        if (FMLCommonHandler.instance().getEffectiveSide().isClient())
            return;

        UserIdent ident = UserIdent.get(event.entityPlayer);
        WorldPoint point = new WorldPoint(event.entityPlayer.dimension, (int) event.target.posX, (int) event.target.posY, (int) event.target.posZ);
        String permission = ModuleProtection.PERM_INTERACT_ENTITY + "." + EntityList.getEntityString(event.target);
        if (ModuleProtection.isDebugMode(event.entityPlayer))
            OutputHandler.chatNotification(event.entityPlayer, permission);
        if (!APIRegistry.perms.checkUserPermission(ident, point, ModuleProtection.PERM_INTERACT_ENTITY))
        {
            event.setCanceled(true);
            return;
        }
    }

    /* ------------------------------------------------------------ */
    /* Block permissions */

    @SubscribeEvent(priority = EventPriority.NORMAL)
    public void breakEvent(BreakEvent event)
    {
        if (FMLCommonHandler.instance().getEffectiveSide().isClient())
            return;

        Block block = event.world.getBlock(event.x, event.y, event.z);
        String permission = ModuleProtection.getBlockBreakPermission(block, event.world, event.x, event.y, event.z);
        if (ModuleProtection.isDebugMode(event.getPlayer()))
            OutputHandler.chatNotification(event.getPlayer(), permission);
        WorldPoint point = new WorldPoint(event.getPlayer().dimension, event.x, event.y, event.z);
        if (!APIRegistry.perms.checkUserPermission(UserIdent.get(event.getPlayer()), point, permission))
        {
            event.setCanceled(true);
            return;
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void placeEvent(BlockEvent.PlaceEvent event)
    {
        if (FMLCommonHandler.instance().getEffectiveSide().isClient())
            return;

        UserIdent ident = UserIdent.get(event.player);
        Block block = event.world.getBlock(event.x, event.y, event.z);
        String permission = ModuleProtection.getBlockPlacePermission(block, event.world, event.x, event.y, event.z);
        if (ModuleProtection.isDebugMode(event.player))
            OutputHandler.chatNotification(event.player, permission);
        WorldPoint point = new WorldPoint(event.player.dimension, event.x, event.y, event.z);
        if (!APIRegistry.perms.checkUserPermission(ident, point, permission))
        {
            event.setCanceled(true);
        }
        if (stringToGameType(APIRegistry.perms.getUserPermissionProperty(ident, ModuleProtection.PERM_GAMEMODE)) == GameType.CREATIVE
                && stringToGameType(APIRegistry.perms.getUserPermissionProperty(ident, point, ModuleProtection.PERM_GAMEMODE)) != GameType.CREATIVE)
        {
            OutputHandler.chatError(event.player, Translator.translate("Cannot place block outside creative area"));
            event.setCanceled(true);
            return;
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void multiPlaceEvent(BlockEvent.MultiPlaceEvent event)
    {
        if (FMLCommonHandler.instance().getEffectiveSide().isClient())
            return;

        for (BlockSnapshot b : event.getReplacedBlockSnapshots())
        {
            Block block = event.world.getBlock(b.x, b.y, b.z);
            String permission = ModuleProtection.getBlockPlacePermission(block, event.world, event.x, event.y, event.z);
            if (ModuleProtection.isDebugMode(event.player))
                OutputHandler.chatNotification(event.player, permission);
            WorldPoint point = new WorldPoint(event.player.dimension, b.x, b.y, b.z);
            if (!APIRegistry.perms.checkUserPermission(UserIdent.get(event.player), point, permission))
            {
                event.setCanceled(true);
                return;
            }
        }
    }

    /* ------------------------------------------------------------ */

    @SubscribeEvent(priority = EventPriority.NORMAL)
    public void explosionEvent(ExplosionEvent.Start event)
    {
        if (FMLCommonHandler.instance().getEffectiveSide().isClient())
            return;

        int cx = (int) Math.floor(event.explosion.explosionX);
        int cy = (int) Math.floor(event.explosion.explosionY);
        int cz = (int) Math.floor(event.explosion.explosionZ);
        WorldArea area = new WorldArea(event.world, new Point(cx - event.explosion.explosionSize, cy - event.explosion.explosionSize, cz
                - event.explosion.explosionSize), new Point(cx + event.explosion.explosionSize, cy + event.explosion.explosionSize, cz
                + event.explosion.explosionSize));
        if (!APIRegistry.perms.checkUserPermission(null, area, ModuleProtection.PERM_EXPLOSION))
        {
            event.setCanceled(true);
            return;
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void playerInteractEvent(PlayerInteractEvent event)
    {
        if (FMLCommonHandler.instance().getEffectiveSide().isClient())
            return;

        UserIdent ident = UserIdent.get(event.entityPlayer);

        WorldPoint point;
        if (event.action == RIGHT_CLICK_AIR)
        {
            MovingObjectPosition mop = FunctionHelper.getPlayerLookingSpot(event.entityPlayer);
            if (mop == null)
                point = new WorldPoint(event.entityPlayer.dimension, event.x, event.y, event.z);
            else
                point = new WorldPoint(event.entityPlayer.dimension, mop.blockX, mop.blockY, mop.blockZ);
        }
        else
            point = new WorldPoint(event.entityPlayer.dimension, event.x, event.y, event.z);

        // Check for block interaction
        if (event.action == Action.RIGHT_CLICK_BLOCK || event.action == Action.LEFT_CLICK_BLOCK)
        {
            Block block = event.world.getBlock(event.x, event.y, event.z);
            String permission = ModuleProtection.getBlockInteractPermission(block, event.world, event.x, event.y, event.z);
            if (ModuleProtection.isDebugMode(event.entityPlayer))
                OutputHandler.chatNotification(event.entityPlayer, permission);
            boolean allow = APIRegistry.perms.checkUserPermission(ident, point, permission);
            event.useBlock = allow ? ALLOW : DENY;
        }

        // Check item (and block) usage
        ItemStack stack = event.entityPlayer.getCurrentEquippedItem();
        if (stack != null && !(stack.getItem() instanceof ItemBlock))
        {
            String permission = ModuleProtection.getItemUsePermission(stack);
            if (ModuleProtection.isDebugMode(event.entityPlayer))
                OutputHandler.chatNotification(event.entityPlayer, permission);
            boolean allow = APIRegistry.perms.checkUserPermission(ident, point, permission);
            event.useItem = allow ? ALLOW : DENY;
        }

        if (anyCreativeModeAtPoint(event.entityPlayer, point)
                && stringToGameType(APIRegistry.perms.getUserPermissionProperty(ident, ModuleProtection.PERM_GAMEMODE)) != GameType.CREATIVE)
        {
            // If entity is in creative area, but player not, deny interaction
            event.useBlock = DENY;
            if (event.action != LEFT_CLICK_BLOCK)
                OutputHandler.chatError(event.entityPlayer, Translator.translate("Cannot interact with creative area if not in creative mode."));
        }
    }

    /* ------------------------------------------------------------ */

    @SubscribeEvent(priority = EventPriority.NORMAL)
    public void checkSpawnEvent(CheckSpawn event)
    {
        if (FMLCommonHandler.instance().getEffectiveSide().isClient())
            return;
        if (!(event.entityLiving instanceof EntityLiving))
            return;
        EntityLiving entity = (EntityLiving) event.entityLiving;
        WorldPoint point = new WorldPoint(entity);
        if (!APIRegistry.perms.checkUserPermission(null, point, ModuleProtection.PERM_MOBSPAWN_NATURAL + "." + EntityList.getEntityString(entity)))
        {
            event.setResult(Result.DENY);
            return;
        }
        if (!APIRegistry.perms.checkUserPermission(null, point, MobType.getMobType(entity).getSpawnPermission(false)))
        {
            event.setResult(Result.DENY);
            return;
        }
    }

    @SubscribeEvent(priority = EventPriority.NORMAL)
    public void specialSpawnEvent(SpecialSpawn event)
    {
        if (FMLCommonHandler.instance().getEffectiveSide().isClient())
            return;
        if (!(event.entityLiving instanceof EntityLiving))
            return;
        EntityLiving entity = (EntityLiving) event.entityLiving;
        WorldPoint point = new WorldPoint(entity);
        if (!APIRegistry.perms.checkUserPermission(null, point, ModuleProtection.PERM_MOBSPAWN_FORCED + "." + EntityList.getEntityString(entity)))
        {
            event.setResult(Result.DENY);
            return;
        }
        if (!APIRegistry.perms.checkUserPermission(null, point, MobType.getMobType(entity).getSpawnPermission(true)))
        {
            event.setResult(Result.DENY);
            return;
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void itemPickupEvent(EntityItemPickupEvent event)
    {
        if (FMLCommonHandler.instance().getEffectiveSide().isClient())
            return;
        if (isItemBanned(event.entityPlayer, event.item.getEntityItem()))
        {
            event.setCanceled(true);
            event.item.setDead();
            return;
        }
        if (isInventoryItemBanned(event.entityPlayer, event.item.getEntityItem()))
        {
            event.setCanceled(true);
            return;
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void entityJoinWorldEvent(EntityJoinWorldEvent event)
    {
        if (FMLCommonHandler.instance().getEffectiveSide().isClient())
            return;

        if (event.entity instanceof EntityItem)
        {
            // 1) Do nothing if the whole world is creative!
            WorldZone worldZone = APIRegistry.perms.getServerZone().getWorldZone(event.world.provider.dimensionId);
            if (stringToGameType(worldZone.getGroupPermission(Zone.GROUP_DEFAULT, ModuleProtection.PERM_GAMEMODE)) != GameType.CREATIVE)
            {
                // 2) If creative mode is set for any group at the location where the block was destroyed, prevent drops
                if (anyCreativeModeAtPoint(null, new WorldPoint(event.entity)))
                {
                    event.entity.setDead();
                    return;
                }
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void harvestDropsEvent(HarvestDropsEvent event)
    {
        if (FMLCommonHandler.instance().getEffectiveSide().isClient())
            return;

        WorldPoint point = new WorldPoint(event.world, event.x, event.y, event.z);

        // 1) Do nothing if the whole world is creative!
        WorldZone worldZone = APIRegistry.perms.getServerZone().getWorldZone(event.world.provider.dimensionId);
        if (stringToGameType(worldZone.getGroupPermission(Zone.GROUP_DEFAULT, ModuleProtection.PERM_GAMEMODE)) != GameType.CREATIVE)
        {
            // 2) If creative mode is set for any group at the location where the block was destroyed, prevent drops
            if (anyCreativeModeAtPoint(null, point))
            {
                event.drops.clear();
                return;
            }
        }

        for (Iterator<ItemStack> iterator = event.drops.iterator(); iterator.hasNext();)
            if (isItemBanned(point, iterator.next()))
                iterator.remove();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void itemTossEvent(ItemTossEvent event)
    {
        if (FMLCommonHandler.instance().getEffectiveSide().isClient())
            return;

        // 1) Do nothing if the whole world is creative!
        WorldZone worldZone = APIRegistry.perms.getServerZone().getWorldZone(event.entity.dimension);
        if (stringToGameType(worldZone.getGroupPermission(Zone.GROUP_DEFAULT, ModuleProtection.PERM_GAMEMODE)) != GameType.CREATIVE)
        {
            // 2) Destroy item when player in creative mode
            // 3) If creative mode is set for any group at the location where the block was destroyed, prevent drops
            if (getGamemode(event.player) == GameType.CREATIVE || anyCreativeModeAtPoint(event.player, new WorldPoint(event.entity)))
            {
                // event.entity.worldObj.removeEntity(event.target);
                event.entity.setDead();
                return;
            }
        }
    }

    @SubscribeEvent
    public void playerOpenContainerEvent(PlayerOpenContainerEvent event)
    {
        if (FMLCommonHandler.instance().getEffectiveSide().isClient())
            return;
        // If it's the player's own inventory - ignore
        if (event.entityPlayer.openContainer == event.entityPlayer.inventoryContainer)
            return;
        checkPlayerInventory(event.entityPlayer);
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public void playerLoginEvent(PlayerLoggedInEvent event)
    {
        checkPlayerInventory(event.player);
    }

    @SubscribeEvent
    public void playerChangedZoneEvent(PlayerChangedZone event)
    {
        if (FMLCommonHandler.instance().getEffectiveSide().isClient())
            return;
        EntityPlayerMP player = (EntityPlayerMP) event.entityPlayer;
        UserIdent ident = UserIdent.get(player);

        String inventoryGroup = APIRegistry.perms.getUserPermissionProperty(ident, event.afterPoint.toWorldPoint(), ModuleProtection.PERM_INVENTORY_GROUP);

        GameType lastGm = stringToGameType(APIRegistry.perms.getUserPermissionProperty(ident, event.beforePoint.toWorldPoint(), ModuleProtection.PERM_GAMEMODE));
        GameType gm = stringToGameType(APIRegistry.perms.getUserPermissionProperty(ident, event.afterPoint.toWorldPoint(), ModuleProtection.PERM_GAMEMODE));
        if (gm != GameType.NOT_SET || lastGm != GameType.NOT_SET)
        {
            // If leaving a creative zone and no other gamemode is set, revert to default (survival)
            if (lastGm != GameType.NOT_SET && gm == GameType.NOT_SET)
                gm = GameType.SURVIVAL;

            GameType playerGm = player.theItemInWorldManager.getGameType();
            if (playerGm != gm)
            {
                // OutputHandler.felog.info(String.format("Changing gamemode for %s from %s to %s",
                // ident.getUsernameOrUUID(), playerGm.getName(), gm.getName()));
                if (gm != GameType.CREATIVE)
                {
                    // TODO: Teleport player slightly above ground to prevent fall-death
                }
                player.setGameType(gm);
            }
            if (gm == GameType.CREATIVE)
                inventoryGroup = "creative";
        }

        // Apply inventory-group
        PlayerInfo pi = PlayerInfo.getPlayerInfo(player);
        pi.setInventoryGroup(inventoryGroup);

        checkPlayerInventory(player);
    }

    /* ------------------------------------------------------------ */

    static class ZoneDeniedMessageHandler extends TimeoutHandler
    {

        private final EntityPlayer player;

        public ZoneDeniedMessageHandler(EntityPlayer entityPlayer)
        {
            super(4000);
            this.player = entityPlayer;
        }

        @Override
        protected void doRun()
        {
            OutputHandler.chatError(player, ModuleProtection.MSG_ZONE_DENIED);
        }

    }

    private HashMap<UUID, List<ZoneEffect>> zoneEffects = new HashMap<>();

    private HashMap<UUID, ZoneDeniedMessageHandler> zoneDeniedMh = new HashMap<>();

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void playerChangedZoneEventHigh(PlayerChangedZone event)
    {
        UserIdent ident = UserIdent.get(event.entityPlayer);
        List<ZoneEffect> effects = getZoneEffects(ident);
        effects.clear();

        // Check knockback
        if (!APIRegistry.perms.getUserPermissionProperty(ident, event.afterZone, ModuleProtection.ZONE_KNOCKBACK).equals(Zone.PERMISSION_FALSE))
        {
            getZoneDeniedMessageHandler(event.entityPlayer).run();

            Vec3 center = event.afterPoint.toVec3();
            if (event.afterZone instanceof AreaZone)
            {
                center = ((AreaZone) event.afterZone).getArea().getCenter().toVec3();
                center.yCoord = event.beforePoint.getY();
            }
            Vec3 delta = event.beforePoint.toVec3().subtract(center).normalize();
            WarpPoint target = new WarpPoint(event.beforePoint.getDimension(), event.beforePoint.getX() - delta.xCoord,
                    event.beforePoint.getY() - delta.yCoord, event.beforePoint.getZ() - delta.zCoord, event.afterPoint.getPitch(), event.afterPoint.getYaw());

            FunctionHelper.teleportPlayer((EntityPlayerMP) event.entityPlayer, target);
            event.setCanceled(true);
            return;
        }

        // Check command effect
        String command = APIRegistry.perms.getUserPermissionProperty(ident, event.afterZone, ModuleProtection.ZONE_COMMAND);
        if (command != null && !command.isEmpty())
        {
            int interval = FunctionHelper.parseIntDefault(
                    APIRegistry.perms.getUserPermissionProperty(ident, event.afterZone, ModuleProtection.ZONE_COMMAND_INTERVAL), 0);
            effects.add(new CommandEffect(ident.getPlayerMP(), interval, command));
        }

        int damage = FunctionHelper.parseIntDefault(APIRegistry.perms.getUserPermissionProperty(ident, event.afterZone, ModuleProtection.ZONE_DAMAGE), 0);
        if (damage > 0)
        {
            int interval = FunctionHelper.parseIntDefault(
                    APIRegistry.perms.getUserPermissionProperty(ident, event.afterZone, ModuleProtection.ZONE_DAMAGE_INTERVAL), 0);
            effects.add(new DamageEffect(ident.getPlayerMP(), interval, damage));
        }

        // Check potion effect
        String potion = APIRegistry.perms.getUserPermissionProperty(ident, event.afterZone, ModuleProtection.ZONE_POTION);
        if (potion != null && !potion.isEmpty())
        {
            int interval = FunctionHelper.parseIntDefault(
                    APIRegistry.perms.getUserPermissionProperty(ident, event.afterZone, ModuleProtection.ZONE_POTION_INTERVAL), 0);
            effects.add(new PotionEffect(ident.getPlayerMP(), interval, potion));
        }

        if (effects.isEmpty())
            zoneEffects.remove(ident.getUuid());
        else
            zoneEffects.put(ident.getUuid(), effects);
    }

    @SubscribeEvent
    public void serverTickEvent(TickEvent.ServerTickEvent event)
    {
        if (event.side != Side.SERVER || event.phase == TickEvent.Phase.END)
            return;
        for (List<ZoneEffect> effects : zoneEffects.values())
        {
            for (ZoneEffect effect : effects)
            {
                effect.update();
                if (effect.isLethal())
                    getZoneDeniedMessageHandler(effect.getPlayer()).run();
            }
        }
    }

    @SubscribeEvent
    public void playerLoggedOutEvent(PlayerEvent.PlayerLoggedOutEvent event)
    {
        zoneEffects.remove(event.player.getPersistentID());
        zoneDeniedMh.remove(event.player.getPersistentID());
    }

    /* ------------------------------------------------------------ */

    public static GameType stringToGameType(String gm)
    {
        if (gm == null)
            return GameType.NOT_SET;
        switch (gm.toLowerCase())
        {
        case "0":
        case "s":
        case "survival":
            return GameType.SURVIVAL;
        case "1":
        case "c":
        case "creative":
            return GameType.CREATIVE;
        case "2":
        case "a":
        case "adventure":
            return GameType.ADVENTURE;
        default:
            return GameType.NOT_SET;
        }
    }

    public static GameType getGamemode(EntityPlayer player)
    {
        return stringToGameType(APIRegistry.perms.getUserPermissionProperty(UserIdent.get(player), ModuleProtection.PERM_GAMEMODE));
    }

    public static boolean anyCreativeModeAtPoint(EntityPlayer player, WorldPoint point)
    {
        if (player != null
                && stringToGameType(APIRegistry.perms.getUserPermissionProperty(UserIdent.get(player), point, ModuleProtection.PERM_GAMEMODE)) == GameType.CREATIVE)
            return true;
        for (String group : APIRegistry.perms.getServerZone().getGroups())
        {
            if (stringToGameType(APIRegistry.perms.getGroupPermissionProperty(group, point, ModuleProtection.PERM_GAMEMODE)) == GameType.CREATIVE)
                return true;
        }
        return false;
    }

    public static boolean isItemBanned(EntityPlayer player, ItemStack stack)
    {
        return !APIRegistry.perms.checkUserPermission(UserIdent.get(player), ModuleProtection.getItemBanPermission(stack));
    }

    public static boolean isItemBanned(WorldPoint point, ItemStack stack)
    {
        return !APIRegistry.perms.checkUserPermission(null, point, ModuleProtection.getItemBanPermission(stack));
    }

    public static boolean isInventoryItemBanned(EntityPlayer player, ItemStack stack)
    {
        return !APIRegistry.perms.checkUserPermission(UserIdent.get(player), ModuleProtection.getItemInventoryPermission(stack));
    }

    public static void checkPlayerInventory(EntityPlayer player)
    {
        for (int slotIdx = 0; slotIdx < player.inventory.getSizeInventory(); slotIdx++)
        {
            ItemStack stack = player.inventory.getStackInSlot(slotIdx);
            if (stack != null)
            {
                if (isItemBanned(player, stack))
                {
                    player.inventory.setInventorySlotContents(slotIdx, null);
                    continue;
                }
                if (isInventoryItemBanned(player, stack))
                {
                    EntityItem droppedItem = player.func_146097_a(stack, true, false);
                    droppedItem.motionX = 0;
                    droppedItem.motionY = 0;
                    droppedItem.motionZ = 0;
                    player.inventory.setInventorySlotContents(slotIdx, null);
                }
            }
        }
    }

    private ZoneDeniedMessageHandler getZoneDeniedMessageHandler(EntityPlayer entityPlayer)
    {
        ZoneDeniedMessageHandler mh = zoneDeniedMh.get(entityPlayer.getPersistentID());
        if (mh == null)
        {
            mh = new ZoneDeniedMessageHandler(entityPlayer);
            zoneDeniedMh.put(entityPlayer.getPersistentID(), mh);
        }
        return mh;
    }

    private List<ZoneEffect> getZoneEffects(UserIdent ident)
    {
        List<ZoneEffect> effects = zoneEffects.get(ident.getUuid());
        if (effects == null)
            effects = new ArrayList<>();
        return effects;
    }

}
