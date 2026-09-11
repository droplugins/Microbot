package net.runelite.client.plugins.microbot.drodarts;

import com.google.inject.Provides;
import lombok.Getter;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;

@PluginDescriptor(
        name = "[Dro] Darts",
        description = "Direct client-action dart fletching",
        tags = {"fletching", "darts", "dro", "packet"},
        enabledByDefault = false,
        version = "1.0.1"
)
public class DroDartsPlugin extends Plugin
{
    @Inject
    private DroDartsConfig config;

    @Inject
    private DroDartsOverlay overlay;

    @Inject
    private OverlayManager overlayManager;

    @Getter
    private DroDartsScript script;

    @Provides
    DroDartsConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(DroDartsConfig.class);
    }

    @Override
    protected void startUp() throws Exception
    {
        overlayManager.add(overlay);
        script = new DroDartsScript();
        script.run(config);
    }

    @Override
    protected void shutDown() throws Exception
    {
        overlayManager.remove(overlay);
        if (script != null)
        {
            script.shutdown();
        }
    }
}

