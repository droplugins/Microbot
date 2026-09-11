package net.runelite.client.plugins.microbot.drolibrary;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;

import javax.inject.Inject;
import java.awt.*;

public class DroLibraryOverlay extends OverlayPanel {

    private final DroLibraryScript script;

    @Inject
    public DroLibraryOverlay(DroLibraryScript script) {
        this.script = script;
        setPosition(OverlayPosition.TOP_LEFT);
        setLayer(OverlayLayer.ABOVE_SCENE);
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        if (!Microbot.isLoggedIn()) {
            return null;
        }

        panelComponent.getChildren().clear();
        panelComponent.getChildren().add(LineComponent.builder()
                .left("Dro Library " + DroLibraryScript.VERSION)
                .right("Active")
                .rightColor(Color.GREEN)
                .build());

        panelComponent.getChildren().add(LineComponent.builder()
                .left("State:")
                .right(script.getCurrentState())
                .build());

        panelComponent.getChildren().add(LineComponent.builder()
                .left("Action:")
                .right(display(script.getLastAction()))
                .build());

        panelComponent.getChildren().add(LineComponent.builder()
                .left("Customer:")
                .right(script.isCustomerLocked()
                        ? script.getCurrentCustomerName() : "Getting assignment")
                .build());

        Book target = script.getCurrentTargetBook();
        panelComponent.getChildren().add(LineComponent.builder()
                .left("Target:")
                .right(target != null ? target.getShortName() : "None")
                .build());

        return super.render(graphics);
    }

    private String display(String value)
    {
        return value == null || value.trim().isEmpty() ? "Unknown" : value;
    }
}
