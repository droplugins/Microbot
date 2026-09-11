package net.runelite.client.plugins.microbot.drohunter;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;

@Slf4j
public class PitfallHunterPlugin extends Plugin
{
    public static final String version = "0.1.53";

    @Inject
    private DroHunterConfig config;

    @Inject
    private PitfallHunterScript script;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private PitfallHunterOverlay overlay;

    @Override
    protected void startUp()
    {
        if (overlayManager != null) {
            overlayManager.add(overlay);
        }
        script.run(config);
        log.info("[PitfallHunter] Started");
    }

    @Override
    protected void shutDown()
    {
        script.shutdown();
        if (overlayManager != null) {
            overlayManager.remove(overlay);
        }
        log.info("[PitfallHunter] Stopped");
    }
}
