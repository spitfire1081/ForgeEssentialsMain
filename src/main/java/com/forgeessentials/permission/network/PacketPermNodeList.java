package com.forgeessentials.permission.network;

import com.forgeessentials.permission.ModulePermissions;
import com.forgeessentials.util.OutputHandler;
import cpw.mods.fml.common.network.Player;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.packet.Packet250CustomPayload;
import net.minecraft.world.WorldServer;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.Set;

public class PacketPermNodeList extends ForgeEssentialsPacket {

    public static final byte packetID = 3;
    private static ModulePermissions sendthru;
    private Packet250CustomPayload packet;

    public PacketPermNodeList(Set<String> permissions)
    {

        packet = new Packet250CustomPayload();

        ByteArrayOutputStream streambyte = new ByteArrayOutputStream();
        DataOutputStream stream = new DataOutputStream(streambyte);

        try
        {
            stream.write(packetID);

            for (String perm : permissions)
            {
                stream.writeBytes(perm + ":");
            }

            stream.close();
            streambyte.close();

            packet.channel = FECHANNEL;
            packet.data = streambyte.toByteArray();
            packet.length = packet.data.length;
        }

        catch (Exception e)
        {
            OutputHandler.felog.info("Error creating packet >> " + this.getClass());
        }
    }

    public static void readServer(DataInputStream stream, WorldServer world,
            EntityPlayer player)
    {
        sendthru.sendPermList((Player) player);

    }

    @Override
    public Packet250CustomPayload getPayload()
    {

        return packet;
    }

}
