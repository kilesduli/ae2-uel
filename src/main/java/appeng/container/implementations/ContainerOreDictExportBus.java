package appeng.container.implementations;

import appeng.api.config.RedstoneMode;
import appeng.api.config.SecurityPermissions;
import appeng.api.config.Settings;
import appeng.container.guisync.GuiSync;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketValueConfig;
import appeng.parts.automation.PartOreDictExportBus;
import appeng.util.Platform;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;

import java.io.IOException;

public class ContainerOreDictExportBus extends ContainerUpgradeable{
    private final PartOreDictExportBus part;

    public ContainerOreDictExportBus(final InventoryPlayer ip, final PartOreDictExportBus anchor) {
        super(ip, anchor);
        this.part = anchor;
    }

    public void detectAndSendChanges() {
        this.verifyPermissions(SecurityPermissions.BUILD, false);

        if (Platform.isServer()) {
            this.setRedStoneMode((RedstoneMode) part.getConfigManager().getSetting(Settings.REDSTONE_CONTROLLED));
        }

        super.standardDetectAndSendChanges();
    }

    @Override
    protected int getHeight() {
        return 170;
    }

    public void saveOreMatch(String oreExp) {
        part.saveOreMatch(oreExp);
        // we should updatechange after save
        part.upgradesChanged();
    }

    public void sendRegex() {
        try {
            NetworkHandler.instance().sendTo((new PacketValueConfig("OreDictExportBus.sendRegex", part.getOreExp())), (EntityPlayerMP) getInventoryPlayer().player);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public RedstoneMode getRedStoneMode() {
        return this.rsMode;
    }

    public void setRedStoneMode(final RedstoneMode rsMode) {
        this.rsMode = rsMode;
    }

    @Override
    protected void setupConfig() {
        super.setupUpgrades();
    }

    @Override
    public int availableUpgrades() {
        return 4;
    }

    @Override
    protected boolean supportCapacity() {
        return false;
    }
}
