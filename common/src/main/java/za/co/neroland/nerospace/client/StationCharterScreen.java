package za.co.neroland.nerospace.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

import za.co.neroland.nerospace.NerospaceCommon;
import za.co.neroland.nerospace.menu.StationCharterMenu;
import za.co.neroland.nerospace.network.FoundStationPayload;
import za.co.neroland.nerospace.network.ModNetwork;
import za.co.neroland.nerospace.network.RenameStationPayload;
import za.co.neroland.nerospace.registry.ModDimensions;

/**
 * The station naming console: type a name and confirm. In <b>found</b> mode it creates a new station (the
 * name persists with its Station Core) without teleporting — fly a rocket to visit. In <b>rename</b> mode
 * (opened by right-clicking a Station Core) it renames that station. Built on the shared procedural hull;
 * the name is sent to the server as a {@link FoundStationPayload} or {@link RenameStationPayload}.
 *
 * <p>2.1: opened in the station dimension (found mode) the station is founded where the player stands, and a
 * "Build starter structure" toggle (default on) appears; off founds the claim with only a Docking Ring.</p>
 */
public class StationCharterScreen extends TexturedContainerScreen<StationCharterMenu> {

    private static final Identifier TEXTURE =
            Identifier.fromNamespaceAndPath(NerospaceCommon.MOD_ID, "textures/gui/rocket.png");
    private static final int ACCENT = 0xFF54D46A;
    private static final int W = 176;
    private static final int H = 78;
    /** Panel height with the "Build starter structure" row (found mode in the station dimension). */
    private static final int H_HERE = 96;

    private EditBox nameBox;
    private SpaceButton confirmButton;
    private SpaceButton starterButton;
    private boolean buildStarter = true;
    /** The layout last applied (null before init's first pass). */
    @org.jetbrains.annotations.Nullable
    private Boolean laidOutHere;
    /** Opened in the station dimension: found mode claims the player's own position. */
    private final boolean inStation;

    public StationCharterScreen(StationCharterMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title, TEXTURE, ACCENT, W, stationDimension() ? H_HERE : H);
        this.inStation = stationDimension();
        this.titleLabelY = -100;       // drawn manually
        this.inventoryLabelY = 10_000; // hidden
    }

    @Override
    protected void init() {
        super.init();
        this.nameBox = new EditBox(this.font, this.leftPos + 12, this.topPos + 28, W - 24, 18,
                Component.translatable("gui.nerospace.station_charter.name"));
        this.nameBox.setMaxLength(48);
        this.nameBox.setHint(Component.translatable("gui.nerospace.station_charter.hint"));
        this.addRenderableWidget(this.nameBox);
        this.setInitialFocus(this.nameBox);
        this.nameBox.setFocused(true);

        this.starterButton = new SpaceButton(this.leftPos + 12, this.topPos + 52, W - 24, 14, starterLabel(), ACCENT,
                b -> {
                    this.buildStarter = !this.buildStarter;
                    b.setMessage(starterLabel());
                });
        this.starterButton.setTooltip(Tooltip.create(
                Component.translatable("gui.nerospace.station_charter.build_starter.tooltip")));
        this.addRenderableWidget(this.starterButton);

        this.confirmButton = new SpaceButton(this.leftPos + 12, this.topPos + 52, W - 24, 16,
                Component.translatable("gui.nerospace.station_charter.confirm"), ACCENT, b -> confirm());
        this.addRenderableWidget(this.confirmButton);
        this.laidOutHere = null; // init rebuilt the widgets (also on resize)
        layoutRows();
    }

    private static boolean stationDimension() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && ModDimensions.STATION_LEVEL.equals(mc.level.dimension());
    }

    /** Whether this screen founds where the player stands (the mode arrives with the first data sync). */
    private boolean claimHere() {
        return this.inStation && this.menu.mode() == StationCharterMenu.MODE_FOUND;
    }

    private Component starterLabel() {
        return Component.translatable("gui.nerospace.station_charter.build_starter",
                this.buildStarter ? CommonComponents.OPTION_ON : CommonComponents.OPTION_OFF);
    }

    /** Shows the starter toggle (and moves Confirm under it) only when founding here. */
    private void layoutRows() {
        boolean here = claimHere();
        if (this.laidOutHere != null && this.laidOutHere.booleanValue() == here) {
            return;
        }
        this.laidOutHere = Boolean.valueOf(here);
        if (this.starterButton != null) {
            this.starterButton.visible = here;
            this.starterButton.active = here;
        }
        if (this.confirmButton != null) {
            this.confirmButton.setY(this.topPos + (here ? 70 : 52));
            this.confirmButton.setTooltip(here
                    ? Tooltip.create(Component.translatable("gui.nerospace.station_charter.here"))
                    : null);
        }
    }

    /** Let the focused name box swallow all typing (incl. the inventory key) so it never closes the screen. */
    //? if >=1.21.9 {
    @Override
    public boolean keyPressed(KeyEvent event) {
    //?} else {
    /*@Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        KeyEvent event = new KeyEvent(keyCode, scanCode, modifiers);
    *///?}
        if (event.key() == 256) { // Escape
            this.onClose();
            return true;
        }
        if (this.nameBox != null && (this.nameBox.keyPressed(event) || this.nameBox.canConsumeInput())) {
            return true;
        }
        //? if >=1.21.9 {
        return super.keyPressed(event);
        //?} else {
        /*return super.keyPressed(event.key(), event.scancode(), event.modifiers());
        *///?}
    }

    private void confirm() {
        String name = this.nameBox.getValue();
        if (this.menu.mode() == StationCharterMenu.MODE_RENAME) {
            ModNetwork.sendToServer(new RenameStationPayload(this.menu.slot(), name));
        } else {
            ModNetwork.sendToServer(new FoundStationPayload(name, claimHere(), this.buildStarter));
        }
        this.onClose();
    }

    @Override
    protected void drawPanel(GuiGraphicsExtractor g) {
        layoutRows(); // the mode syncs after init; keep the rows in step with it
        int l = this.leftPos;
        int t = this.topPos;
        int h = claimHere() ? H_HERE : H;
        g.fill(l - 2, t - 2, l + W + 2, t + h + 2, INK);
        g.fill(l, t, l + W, t + h, 0xFF0E1724);
        g.fill(l, t, l + W, t + 16, 0xFF14283C);
        g.fill(l, t + 16, l + W, t + 17, ACCENT);
    }

    @Override
    protected void extractForeground(GuiGraphicsExtractor g) {
        String key = this.menu.mode() == StationCharterMenu.MODE_RENAME
                ? "gui.nerospace.station_charter.rename"
                : "gui.nerospace.station_charter.title";
        label(g, Component.translatable(key), 12, 5, TITLE);
    }
}
