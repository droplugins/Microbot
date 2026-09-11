package net.runelite.client.plugins.microbot.drochaos;

import com.google.inject.Provides;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;

@PluginDescriptor(
        name = PluginConstants.MOCROSOFT + "DroChaos",
        description = "Trains Prayer at the Wilderness Chaos Altar with player safety and Lumbridge banking.",
        tags = {"prayer", "chaos altar", "wilderness", "dragon bones"},
        version = DroChaosPlugin.VERSION,
        minClientVersion = "2.1.0",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
public class DroChaosPlugin extends Plugin
{
    static final String VERSION = "1.0.0";

    @Inject
    private DroChaosConfig config;
    @Inject
    private DroChaosScript script;
    @Inject
    private DroChaosOverlay overlay;
    @Inject
    private OverlayManager overlayManager;

    @Provides
    DroChaosConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(DroChaosConfig.class);
    }

    @Override
    protected void startUp()
    {
        overlayManager.add(overlay);
        script.run(config);
    }

    @Override
    protected void shutDown()
    {
        script.shutdown();
        overlayManager.remove(overlay);
    }
}
