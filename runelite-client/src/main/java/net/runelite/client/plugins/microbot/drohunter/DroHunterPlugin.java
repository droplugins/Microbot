package net.runelite.client.plugins.microbot.drohunter;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;

@PluginDescriptor(
        name = "[Dro] Hunter",
        description = "Unified hunter plugin for box traps, birds, kebbits, deadfalls, pitfalls, and salamanders",
        tags = {"hunter", "microbot", "chinchompa", "bird", "kebbit", "deadfall", "pitfall", "salamander"},
        version = DroHunterPlugin.version,
        minClientVersion = "2.0.13",
        cardUrl = "",
        iconUrl = "",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
@Slf4j
public class DroHunterPlugin extends Plugin {
    public static final String version = "2.0.0";

    @Inject
    private DroHunterConfig config;

    @Provides
    DroHunterConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(DroHunterConfig.class);
    }

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private EventBus eventBus;

    @Inject
    private DroHunterOverlay droHunterOverlay;

    @Inject
    private DroHunterScript droHunterScript;

    @Inject
    private BirdHunterPlugin birdHunterController;

    @Inject
    private HunterKebbitsPlugin kebbitsController;

    @Inject
    private DeadFallTrapHunterPlugin deadfallController;

    @Inject
    private PitfallHunterPlugin pitfallController;

    @Inject
    private SalamanderPlugin salamanderController;

    private DroHunterActivity activeActivity;


    @Override
    protected void startUp() throws Exception {
        activeActivity = config.activity();
        startActivity(activeActivity);
    }

    @Override
    protected void shutDown() {
        stopActivity(activeActivity);
        activeActivity = null;
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event) {
        if (!"DroHunter".equals(event.getGroup()) || !"activity".equals(event.getKey())) {
            return;
        }

        DroHunterActivity selectedActivity = config.activity();
        if (selectedActivity == activeActivity) {
            return;
        }

        stopActivity(activeActivity);
        try {
            startActivity(selectedActivity);
            activeActivity = selectedActivity;
        } catch (Exception exception) {
            activeActivity = null;
            log.error("Unable to start DroHunter activity {}", selectedActivity, exception);
        }
    }

    private void startActivity(DroHunterActivity activity) throws Exception {
        if (activity == null) {
            return;
        }

        switch (activity) {
            case BOX_TRAPS:
                overlayManager.add(droHunterOverlay);
                droHunterScript.run(config);
                break;
            case BIRDS:
                eventBus.register(birdHunterController);
                try {
                    birdHunterController.startUp();
                } catch (Exception exception) {
                    eventBus.unregister(birdHunterController);
                    throw exception;
                }
                break;
            case KEBBITS:
                eventBus.register(kebbitsController);
                try {
                    kebbitsController.startUp();
                } catch (Exception exception) {
                    eventBus.unregister(kebbitsController);
                    throw exception;
                }
                break;
            case DEADFALLS:
                eventBus.register(deadfallController);
                try {
                    deadfallController.startUp();
                } catch (Exception exception) {
                    eventBus.unregister(deadfallController);
                    throw exception;
                }
                break;
            case PITFALLS:
                eventBus.register(pitfallController);
                try {
                    pitfallController.startUp();
                } catch (Exception exception) {
                    eventBus.unregister(pitfallController);
                    throw exception;
                }
                break;
            case SALAMANDERS:
                eventBus.register(salamanderController);
                try {
                    salamanderController.startUp();
                } catch (Exception exception) {
                    eventBus.unregister(salamanderController);
                    throw exception;
                }
                break;
            default:
                throw new IllegalArgumentException("Unsupported DroHunter activity: " + activity);
        }

        log.info("DroHunter activity started: {}", activity);
    }

    private void stopActivity(DroHunterActivity activity) {
        if (activity == null) {
            return;
        }

        try {
            switch (activity) {
                case BOX_TRAPS:
                    droHunterScript.shutdown();
                    overlayManager.remove(droHunterOverlay);
                    break;
                case BIRDS:
                    birdHunterController.shutDown();
                    break;
                case KEBBITS:
                    kebbitsController.shutDown();
                    break;
                case DEADFALLS:
                    deadfallController.shutDown();
                    break;
                case PITFALLS:
                    pitfallController.shutDown();
                    break;
                case SALAMANDERS:
                    salamanderController.shutDown();
                    break;
                default:
                    break;
            }
        } catch (Exception exception) {
            log.error("Unable to stop DroHunter activity {} cleanly", activity, exception);
        } finally {
            unregisterActivityController(activity);
        }

        log.info("DroHunter activity stopped: {}", activity);
    }

    private void unregisterActivityController(DroHunterActivity activity) {
        switch (activity) {
            case BIRDS:
                eventBus.unregister(birdHunterController);
                break;
            case KEBBITS:
                eventBus.unregister(kebbitsController);
                break;
            case DEADFALLS:
                eventBus.unregister(deadfallController);
                break;
            case PITFALLS:
                eventBus.unregister(pitfallController);
                break;
            case SALAMANDERS:
                eventBus.unregister(salamanderController);
                break;
            default:
                break;
        }
    }

}
