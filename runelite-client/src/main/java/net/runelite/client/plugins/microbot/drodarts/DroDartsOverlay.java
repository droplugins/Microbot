package net.runelite.client.plugins.microbot.drodarts;

import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.*;

class DroDartsOverlay extends OverlayPanel {
    private final DroDartsConfig config;
    private final DroDartsPlugin plugin;

    @Inject
    DroDartsOverlay(DroDartsPlugin plugin, DroDartsConfig config) {
        super(plugin);
        this.plugin = plugin;
        this.config = config;
        setPosition(OverlayPosition.TOP_LEFT);
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        panelComponent.getChildren().add(TitleComponent.builder()
                .text("[Dro] Darts")
                .color(Color.CYAN)
                .build());

        panelComponent.getChildren().add(LineComponent.builder()
                .left("Active Tip:")
                .right(config.dartType().name())
                .build());

        if (plugin.getScript() != null) {
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Status:")
                    .right(plugin.getScript().getStatus())
                    .build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Darts:")
                    .right(String.valueOf(plugin.getScript().getDartsFletched()))
                    .build());
        }

        return super.render(graphics);
    }
}
