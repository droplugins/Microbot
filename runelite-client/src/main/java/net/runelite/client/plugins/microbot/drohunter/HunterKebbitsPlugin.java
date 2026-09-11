package net.runelite.client.plugins.microbot.drohunter;

import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.microbot.util.misc.TimeUtils;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.time.Instant;

/**
 * Main plugin class for automated Kebbit hunting using a falcon.
 * Handles configuration, overlay management, event subscriptions, and script lifecycle.
 */
public class HunterKebbitsPlugin extends Plugin {

    public static final String version = "1.1.2";

    @Inject
    private Client client;

    @Inject
    private DroHunterConfig config;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private HunterKebbitsOverlay kebbitsOverlay;

    @Inject
    private HunterKabbitsScript script;
    private Instant scriptStartTime;

    /**
     * Provides the plugin configuration to RuneLite's config manager.
     *
     * @param configManager the config manager instance
     * @return configuration for this plugin
     */
    /**
     * Called when the plugin is started. Initializes script and overlay.
     */
    @Override
    protected void startUp() {
        scriptStartTime = Instant.now();
        overlayManager.add(kebbitsOverlay);
        script.run(config, this);
    }

    /**
     * Called when the plugin is shut down. Cleans up overlay and script.
     */
    @Override
    protected void shutDown() {
        scriptStartTime = null;
        overlayManager.remove(kebbitsOverlay);
        if (script != null) {
            script.shutdown();
        }
    }

    /**
     * Subscribes to game messages to detect player death.
     *
     * @param event The chat message event
     */
    @Subscribe
    public void onChatMessage(ChatMessage event) {
        if (event.getType() == ChatMessageType.GAMEMESSAGE &&
                event.getMessage().equalsIgnoreCase("oh dear, you are dead!")) {
            script.hasDied = true;
        }
    }


    /**
     * Returns the formatted runtime of the script.
     *
     * @return Duration since the script was started.
     */
    public String getTimeRunning() {
        return scriptStartTime != null
                ? TimeUtils.getFormattedDurationBetween(scriptStartTime, Instant.now())
                : "";
    }
}
