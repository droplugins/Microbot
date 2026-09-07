package net.runelite.client.plugins.microbot.divinepotion;

import com.google.inject.Provides;
import javax.inject.Inject;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

@PluginDescriptor(
        name = "<html> [Dro] Divine Potion Sipper</html>",
        description = "Sips divine potions safely with HP check and natural anti-ban mouse movement",
        tags = {"combat", "potions", "divine", "dro"},
        enabledByDefault = false
)
public class DivinePotionPlugin extends Plugin {
    @Inject
    private DivinePotionConfig config;

    @Provides
    DivinePotionConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(DivinePotionConfig.class);
    }

    @Inject
    private DivinePotionScript divinePotionScript;

    @Override
    protected void startUp() {
        divinePotionScript.run(config);
    }

    @Override
    protected void shutDown() {
        divinePotionScript.shutdown();
    }
}