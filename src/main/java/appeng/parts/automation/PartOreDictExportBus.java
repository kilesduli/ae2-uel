package appeng.parts.automation;

import appeng.api.AEApi;
import appeng.api.config.*;
import appeng.api.networking.IGridNode;
import appeng.api.networking.energy.IEnergyGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.parts.IPartCollisionHelper;
import appeng.api.parts.IPartModel;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.util.AECableType;
import appeng.core.AppEng;
import appeng.core.settings.TickRates;
import appeng.core.sync.GuiBridge;
import appeng.items.parts.PartModels;
import appeng.me.GridAccessException;
import appeng.me.helpers.MachineSource;
import appeng.parts.PartModel;
import appeng.util.InventoryAdaptor;
import appeng.util.Platform;
import appeng.util.item.OreDictFilterMatcher;
import appeng.util.prioritylist.OreDictPriorityList;
import com.google.common.collect.ImmutableList;
import com.google.common.primitives.Ints;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.NotNull;

public class PartOreDictExportBus extends PartSharedItemBus {
    //region Models
    // FIXME: just copy from PartExportBus, after that we will add ore dict specific model if needed
    public static final ResourceLocation MODEL_BASE = new ResourceLocation(AppEng.MOD_ID, "part/oredict_export_bus_base");

    @PartModels
    public static final IPartModel MODELS_OFF = new PartModel(MODEL_BASE, new ResourceLocation(AppEng.MOD_ID, "part/export_bus_off"));

    @PartModels
    public static final IPartModel MODELS_ON = new PartModel(MODEL_BASE, new ResourceLocation(AppEng.MOD_ID, "part/export_bus_on"));

    @PartModels
    public static final IPartModel MODELS_HAS_CHANNEL = new PartModel(MODEL_BASE, new ResourceLocation(AppEng.MOD_ID, "part/export_bus_has_channel"));
    //endregion

    public String oreExp = "";
    OreDictPriorityList<IAEItemStack> priorityList;
    private final IActionSource mySrc;
    private long itemToSend = 1;
    private boolean didSomething = false;
    private Integer lastSendItemStack = -1;

    public PartOreDictExportBus(ItemStack is) {
        super(TickRates.ExportBus, is);
        this.mySrc = new MachineSource(this);
    }

    public String getOreExp() {
        if (this.oreExp == null) {
            return "";
        }
        return oreExp;
    }

    public void saveOreMatch(String oreMatch) {
        if (!this.oreExp.equals(oreMatch)) {
            this.oreExp = oreMatch;

            var ruleList = OreDictFilterMatcher.parseExpression(oreMatch);
            this.priorityList = new OreDictPriorityList<>(ruleList);
            this.getHost().markForSave();
        }
    }

    @Override
    public void readFromNBT(NBTTagCompound data) {
        super.readFromNBT(data);
        this.oreExp = data.getString("oreMatch");

        var rulesList = OreDictFilterMatcher.parseExpression(oreExp);
        this.priorityList = new OreDictPriorityList<>(rulesList);
    }

    @Override
    public void writeToNBT(NBTTagCompound data) {
        super.writeToNBT(data);
        data.setString("oreMatch", getOreExp());
    }

    @Override
    protected TickRateModulation doBusWork() {
        if (!this.getProxy().isActive() || !this.canDoBusWork()) {
            return TickRateModulation.IDLE;
        }

        this.itemToSend = this.calculateItemsToSend();

        try {
            final InventoryAdaptor destination = this.getHandler();
            final IMEMonitor<IAEItemStack> aeInv = this.getProxy().getStorage().getInventory(AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class));
            final IEnergyGrid energy = this.getProxy().getEnergy();

            if (destination != null && this.priorityList != null) {
                for (final IAEItemStack itemStack : ImmutableList.copyOf(aeInv.getStorageList())){
                    if (itemStack.getStackSize() > 0 && this.priorityList.isListed(itemStack)) {
                        this.pushItemIntoTarget(destination, energy, aeInv, itemStack);
                        if (this.itemToSend <= 0) {
                            break;
                        }
                    }
                }
            } else {
                return TickRateModulation.SLEEP;
            }
        } catch (final GridAccessException e) {
            // Ciallo～(∠・ω<)⌒★
        }
        return this.didSomething ? TickRateModulation.FASTER : TickRateModulation.SLEEP;
    }

    @Override
    public void getBoxes(final IPartCollisionHelper bch) {
        bch.addBox(4, 4, 12, 12, 12, 14);
        bch.addBox(5, 5, 14, 11, 11, 15);
        bch.addBox(6, 6, 15, 10, 10, 16);
        bch.addBox(6, 6, 11, 10, 10, 12);
    }

    @Override
    public float getCableConnectionLength(AECableType cable) {
        return 5;
    }

    @Override
    public boolean onPartActivate(final EntityPlayer player, final EnumHand hand, final Vec3d pos) {
        if (Platform.isServer()) {
            Platform.openGUI(player, this.getHost().getTile(), this.getSide(), GuiBridge.GUI_OREDICTEXPORTBUS);
        }
        return true;
    }

    // just repeat from PartExportBus
    private void pushItemIntoTarget(final InventoryAdaptor d, final IEnergyGrid energy, final IMEInventory<IAEItemStack> inv, IAEItemStack org) {
        ItemStack inputStack = org.getCachedItemStack(org.getStackSize());

        ItemStack remaining = d.simulateAdd(inputStack);

        // Store the stack in the cache for next time.
        if (!remaining.isEmpty()) {
            org.setCachedItemStack(remaining);
            if (remaining == inputStack) {
                return;
            }
        }

        final long canFit = Math.min(this.itemToSend, org.getStackSize() - remaining.getCount());

        if (canFit > 0) {
            IAEItemStack ais = org.copy();
            ais.setStackSize(canFit);
            final IAEItemStack itemsToAdd = Platform.poweredExtraction(energy, inv, ais, this.mySrc);

            if (itemsToAdd != null) {
                this.itemToSend -= itemsToAdd.getStackSize();

                inputStack.setCount(Ints.saturatedCast(itemsToAdd.getStackSize()));

                final ItemStack failed = d.addItems(inputStack);
                if (!failed.isEmpty()) {
                    ais.setStackSize(failed.getCount());
                    inv.injectItems(ais, Actionable.MODULATE, this.mySrc);
                } else {
                    this.didSomething = true;
                }
            } else {
                org.setCachedItemStack(inputStack);
            }
        }
    }

    @Override
    public IPartModel getStaticModels() {
        if (this.isActive() && this.isPowered()) {
            return MODELS_HAS_CHANNEL;
        } else if (this.isPowered()) {
            return MODELS_ON;
        } else {
            return MODELS_OFF;
        }
    }
}
