package appeng.client.gui.implementations;

import appeng.api.config.RedstoneMode;
import appeng.api.config.Settings;
import appeng.client.gui.widgets.GuiImgButton;
import appeng.client.gui.widgets.MEGuiTextField;
import appeng.container.implementations.ContainerOreDictExportBus;
import appeng.core.localization.GuiText;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketConfigButton;
import appeng.core.sync.packets.PacketValueConfig;
import appeng.parts.automation.PartOreDictExportBus;
import appeng.util.item.OreDictFilterMatcher;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.entity.player.InventoryPlayer;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.io.IOException;
import java.util.regex.Pattern;

public class GuiOreDictExportBus extends GuiUpgradeable {
    private final ContainerOreDictExportBus container;
    PartOreDictExportBus part;
    private MEGuiTextField searchFieldInputs;

    private static final Pattern ORE_DICTIONARY_FILTER = Pattern.compile("[0-9a-zA-Z* &|^!()]*");

    public GuiOreDictExportBus(final InventoryPlayer inventoryPlayer, final PartOreDictExportBus te) {
        super(new ContainerOreDictExportBus(inventoryPlayer, te));
        this.container = (ContainerOreDictExportBus) super.inventorySlots;
        part = te;
        this.ySize = 170;
    }

    protected void addButtons() {
        this.redstoneMode = new GuiImgButton(this.guiLeft - 18, this.guiTop + 8, Settings.REDSTONE_CONTROLLED, RedstoneMode.IGNORE);
        this.buttonList.add(this.redstoneMode);

        this.searchFieldInputs = new MEGuiTextField(this.fontRenderer, this.guiLeft + 3, this.guiTop + 22, 170, 12);
        this.searchFieldInputs.setEnableBackgroundDrawing(false);
        this.searchFieldInputs.setMaxStringLength(512);
        this.searchFieldInputs.setTextColor(0xFFFFFF);
        this.searchFieldInputs.setVisible(true);
        this.searchFieldInputs.setFocused(false);
        this.searchFieldInputs.setValidator(str -> ORE_DICTIONARY_FILTER.matcher(str).matches());

        try {
            NetworkHandler.instance().sendToServer(new PacketValueConfig("OreDictExportBus.getRegex", "1"));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void fillRegex(String oreExp) {
        this.searchFieldInputs.setText(oreExp);
    }

    @Override
    protected void mouseClicked(int xCoord, int yCoord, int btn) throws IOException {
        boolean wasFocused = this.searchFieldInputs.isFocused();
        this.searchFieldInputs.mouseClicked(xCoord, yCoord, btn);

        if (btn == 1 && this.searchFieldInputs.isMouseIn(xCoord, yCoord)) {
            this.searchFieldInputs.setText("");
        }

        if (!searchFieldInputs.isFocused() && wasFocused) {
            searchFieldInputs.setText(OreDictFilterMatcher.validateExp(searchFieldInputs.getText()));
            NetworkHandler.instance().sendToServer(new PacketValueConfig("OreDictExportBus.save", searchFieldInputs.getText()));
        }

        super.mouseClicked(xCoord, yCoord, btn);
    }

    @Override
    protected void keyTyped(char typedChar, int key) throws IOException {
        if (!this.checkHotbarKeys(key)) {
            if (key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER) {
                searchFieldInputs.setText(OreDictFilterMatcher.validateExp(searchFieldInputs.getText()));
                NetworkHandler.instance().sendToServer(new PacketValueConfig("OreDictExportBus.save", searchFieldInputs.getText()));
            }
            if (!this.searchFieldInputs.textboxKeyTyped(typedChar, key)) {
                super.keyTyped(typedChar, key);
            }
        }
    }

    @Override
    public void drawFG(int offsetX, int offsetY, int mouseX, int mouseY) {
        this.fontRenderer.drawString(this.getGuiDisplayName(GuiText.OreDictExportBus.getLocal()), 8, 6, 4210752);
        this.fontRenderer.drawString(this.searchFieldInputs.getText().length() + " / " + this.searchFieldInputs.getMaxStringLength(), 120, 36, 4210752);
        this.fontRenderer.drawString("& = AND    " + "| = OR", 8, 36, 4210752);
        this.fontRenderer.drawString("^ = XOR    " + "! = NOT", 8, 48, 4210752);
        this.fontRenderer.drawString("() for priority    " + "* for wildcard", 8, 60, 4210752);
        this.fontRenderer.drawString("Ex.: *Redstone*&!dustRedstone", 8, 72, 4210752);

        if (this.redstoneMode != null) {
            this.redstoneMode.set(this.container.getRedStoneMode());
        }
    }

    @Override
    protected String getBackground() {
        return "guis/oredictexportbus.png";
    }

    @Override
    public void drawBG(int offsetX, int offsetY, int mouseX, int mouseY) {
        super.drawBG(offsetX, offsetY, mouseX, mouseY);
        if (this.searchFieldInputs != null) {
            this.searchFieldInputs.drawTextBox();
        }
    }
}