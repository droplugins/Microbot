package net.runelite.client.plugins.microbot.drolibrary;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.*;

@PluginDescriptor(
        name = "[Dro] Library",
        description = "Automates the Kourend Library library assistant miniquest",
        tags = {"kourend", "library", "microbot"},
        enabledByDefault = false
)
@Slf4j
public class DroLibraryPlugin extends Plugin {

    @Inject
    private DroLibraryConfig config;

    @Provides
    DroLibraryConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(DroLibraryConfig.class);
    }

    @Inject
    private OverlayManager overlayManager;
    @Inject
    private DroLibraryOverlay overlay;

    @Inject
    private DroLibraryScript droLibraryScript;

    @Override
    protected void startUp() throws Exception {
        overlayManager.add(overlay);
        droLibraryScript.run();
    }

    @Override
    protected void shutDown() throws Exception {
        overlayManager.remove(overlay);
        droLibraryScript.shutdown();
    }
}