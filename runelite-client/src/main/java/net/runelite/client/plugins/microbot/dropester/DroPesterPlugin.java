package net.runelite.client.plugins.microbot.dropester;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ObjectID;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.config.*;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.antiban.enums.Activity;
import net.runelite.client.plugins.microbot.util.antiban.enums.ActivityIntensity;
import net.runelite.client.plugins.microbot.util.antiban.enums.PlayStyle;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.prayer.Rs2Prayer;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.*;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

@PluginDescriptor(
        name = "[Dro] Pester",
        description = "Attacks monsters in Pest Control with human-like anti-pattern behaviors.",
        tags = {"pest control", "minigames", "dropester"},
        authors = {"Droplugins", "Mocrosoft"},
        version = "1.2",
        minClientVersion = "2.6.22",
        enabledByDefault = false
)
@Slf4j
public class DroPesterPlugin extends Plugin {

    @Inject
    private DroPesterScript script;

    @Inject
    private DroPesterConfig config;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private DroPesterOverlay overlay;

    @Provides
    DroPesterConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(DroPesterConfig.class);
    }

    @Override
    protected void startUp() throws AWTException {
        if (overlayManager != null) {
            overlayManager.add(overlay);
        }
        script.run(config);
    }

    @Override
    protected void shutDown() {
        script.shutdown();
        if (overlayManager != null) {
            overlayManager.remove(overlay);
        }
    }

    @Subscribe
    public void onChatMessage(ChatMessage event) {
        if (event.getMessage().toLowerCase().contains("i can't reach that")) {
            if (script != null) {
                script.onUnreachableMessage();
            }
        }
    }

    // =========================================================================
    // CONFIGURATION INTERFACE
    // =========================================================================
    @ConfigGroup("dropester")
    @ConfigInformation(
            "Instructions: Start this script next to the boat for your combat level wearing combat gear. The bot will attack monsters."
    )
    public interface DroPesterConfig extends Config {
        @ConfigSection(
                name = "General Settings",
                description = "General settings for DroPester",
                position = 0,
                closedByDefault = false
        )
        String generalSection = "generalSection";

        @ConfigItem(
                keyName = "world",
                name = "Target World",
                description = "Pest Control official world (Default: 344)",
                position = 1,
                section = generalSection
        )
        default int world() {
            return 344;
        }

        @ConfigItem(
                keyName = "useQuickPrayer",
                name = "Use Quick Prayer",
                description = "Turn on Quick Prayer randomly within the first 30 seconds of entering the game",
                position = 2,
                section = generalSection
        )
        default boolean useQuickPrayer() {
            return true;
        }

        @ConfigItem(
                keyName = "drinkCombatPotions",
                name = "Drink Combat Potions",
                description = "Sip a combat potion randomly within the first 30 seconds of entering every game",
                position = 3,
                section = generalSection
        )
        default boolean drinkCombatPotions() {
            return true;
        }
    }

    // =========================================================================
    // MAIN SCRIPT ENGINE
    // =========================================================================
    public static class DroPesterScript extends Script {
        private DroPesterConfig config;

        private boolean wasInGame = false;
        private boolean walkToCenter = false;
        private long gameStartTime = 0;
        private long potionDelay = 0;
        private long prayerDelay = 0;
        private boolean drunkPotionThisGame = false;
        private boolean toggledPrayerThisGame = false;
        private long outsideStartTime = 0;

        // Thread-safe list to store blacklisted NPCs that threw "I can't reach that"
        private final List<Integer> unreachableNpcs = new CopyOnWriteArrayList<>();
        private volatile Rs2NpcModel currentTarget = null;
        private volatile boolean unreachableTriggered = false;

        public void onUnreachableMessage() {
            unreachableTriggered = true;
            if (currentTarget != null && currentTarget.getNpc() != null) {
                unreachableNpcs.add(currentTarget.getNpc().getIndex());
                Microbot.log("Target unreachable (" + currentTarget.getName() + ") - treating as defeated.");
            }
        }

        private static WorldPoint stepTowards(WorldPoint from, WorldPoint to, int maxStep) {
            int dx = to.getX() - from.getX();
            int dy = to.getY() - from.getY();
            int chebyshev = Math.max(Math.abs(dx), Math.abs(dy));
            if (chebyshev <= maxStep) {
                return to;
            }
            double scale = (double) maxStep / chebyshev;
            return new WorldPoint(
                    from.getX() + (int) Math.round(dx * scale),
                    from.getY() + (int) Math.round(dy * scale),
                    from.getPlane()
            );
        }

        public boolean run(DroPesterConfig config) {
            this.config = config;

            Rs2Antiban.resetAntibanSettings();
            Rs2Antiban.setActivity(Activity.GENERAL_COMBAT);
            Rs2Antiban.setActivityIntensity(ActivityIntensity.LOW);
            Rs2Antiban.setPlayStyle(PlayStyle.MODERATE);
            Rs2Antiban.activateAntiban();
            Rs2AntibanSettings.moveMouseOffScreen = true;
            Rs2AntibanSettings.simulateMistakes = true;
            Rs2AntibanSettings.naturalMouse = true;
            Rs2AntibanSettings.usePlayStyle = true;
            Rs2AntibanSettings.behavioralVariability = true;
            Rs2AntibanSettings.nonLinearIntervals = true;
            Rs2AntibanSettings.actionCooldownChance = 0.01;
            Rs2AntibanSettings.moveMouseOffScreenChance = 0.95;

            mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
                try {
                    if (!Microbot.isLoggedIn() || !super.run()) return;

                    if (Rs2AntibanSettings.actionCooldownActive) return;

                    final boolean isInPC = isInPestControl();
                    final boolean isInBoat = isInBoat();

                    if (isInPC) {
                        handleInGameLogic();
                    } else {
                        handleOutsideOrBoatLogic(isInBoat);
                    }

                } catch (Exception ex) {
                    ex.printStackTrace();
                    Microbot.log("DroPester Error: " + ex.getMessage());
                }
            }, 0, 300, TimeUnit.MILLISECONDS);
            return true;
        }

        @Override
        public void shutdown() {
            super.shutdown();
            Rs2Antiban.deactivateAntiban();
            Rs2Antiban.resetAntibanSettings();
        }

        private void handleInGameLogic() {
            if (!wasInGame) {
                wasInGame = true;
                walkToCenter = false;
                gameStartTime = System.currentTimeMillis();
                drunkPotionThisGame = false;
                toggledPrayerThisGame = false;
                outsideStartTime = 0;

                potionDelay = Rs2Random.between(1000, 27000);
                prayerDelay = Rs2Random.between(1000, 27000);
                Microbot.log("Game started. Defending Void Knight...");
            }

            WorldPoint playerLoc = Rs2Player.getWorldLocation();
            if (playerLoc == null) return;

            if (!walkToCenter) {
                WorldPoint worldPoint = WorldPoint.fromRegion(playerLoc.getRegionID(), 32, 17, playerLoc.getPlane());
                if (playerLoc.distanceTo(worldPoint) <= 4) {
                    walkToCenter = true;
                } else {
                    Microbot.log("Running to center platform near Void Knight...");
                    Rs2Walker.walkMiniMap(stepTowards(playerLoc, worldPoint, 14));
                    sleepUntil(() -> !Rs2Player.isMoving(), 4000);
                    return;
                }
            }

            long elapsed = System.currentTimeMillis() - gameStartTime;

            if (config.useQuickPrayer() && !toggledPrayerThisGame && elapsed >= prayerDelay) {
                toggledPrayerThisGame = true;
                if (!Rs2Prayer.isQuickPrayerEnabled() && Microbot.getClient().getBoostedSkillLevel(Skill.PRAYER) > 0) {
                    Widget prayerOrb = Rs2Widget.getWidget(ComponentID.MINIMAP_QUICK_PRAYER_ORB);
                    if (prayerOrb != null) {
                        Microbot.getMouse().click(prayerOrb.getCanvasLocation());
                        sleep(400, 800);
                    }
                }
            }

            if (config.drinkCombatPotions() && !drunkPotionThisGame && elapsed >= potionDelay) {
                drunkPotionThisGame = true;
                Rs2ItemModel potion = Rs2Inventory.get(item ->
                        item != null && item.getName() != null &&
                                (item.getName().toLowerCase().contains("combat") ||
                                        item.getName().toLowerCase().contains("super attack") ||
                                        item.getName().toLowerCase().contains("super strength") ||
                                        item.getName().toLowerCase().contains("bastion") ||
                                        item.getName().toLowerCase().contains("ranging"))
                );

                if (potion != null) {
                    Microbot.log("Drinking potion: " + potion.getName());
                    Rs2Inventory.interact(potion, "drink");
                    sleep(400, 800);
                }
            }

            if (Microbot.getClient().getLocalPlayer().isInteracting()) {
                return;
            }

            // Target Shifters or matching nearby pest targets and click them directly via Rs2NpcModel
            Rs2NpcModel shifterTarget = Microbot.getRs2NpcCache().query()
                    .where(n -> n.getName() != null
                            && n.getName().toLowerCase().contains("shifter")
                            && n.getNpc() != null
                            && !n.getNpc().isDead()
                            && n.getNpc().getHealthRatio() != 0
                            && !unreachableNpcs.contains(n.getNpc().getIndex()))
                    .nearestOnClientThread();

            if (shifterTarget != null) {
                currentTarget = shifterTarget;
                unreachableTriggered = false;
                Microbot.log("Actively attacking nearby Shifter...");
                shifterTarget.click("Attack");

                sleepUntil(() -> !Microbot.getClient().getLocalPlayer().isInteracting() || unreachableTriggered, 4000);

                if (unreachableTriggered) {
                    sleep(200, 400);
                }
                return;
            }

            // Fallback Target: attack any active pest monster
            Rs2NpcModel backupTarget = Microbot.getRs2NpcCache().query()
                    .where(n -> n.getName() != null
                            && !n.getName().equalsIgnoreCase("Void Knight")
                            && n.getNpc() != null
                            && n.getNpc().getCombatLevel() > 0
                            && !n.getNpc().isDead()
                            && n.getNpc().getHealthRatio() != 0
                            && !unreachableNpcs.contains(n.getNpc().getIndex()))
                    .nearestOnClientThread();

            if (backupTarget != null) {
                currentTarget = backupTarget;
                unreachableTriggered = false;
                Microbot.log("Actively attacking nearby Pest monster...");
                backupTarget.click("Attack");

                sleepUntil(() -> !Microbot.getClient().getLocalPlayer().isInteracting() || unreachableTriggered, 4000);

                if (unreachableTriggered) {
                    sleep(200, 400);
                }
            }
        }

        private void handleOutsideOrBoatLogic(boolean isInBoat) {
            if (wasInGame) {
                wasInGame = false;
                walkToCenter = false;
                drunkPotionThisGame = false;
                toggledPrayerThisGame = false;
                unreachableNpcs.clear();
                currentTarget = null;
                outsideStartTime = 0;
                Rs2Walker.setTarget(null);
            }

            if (!isInBoat) {
                if (outsideStartTime == 0) {
                    outsideStartTime = System.currentTimeMillis();
                } else if (System.currentTimeMillis() - outsideStartTime > 210000) { // 3.5 minutes timeout watchdog
                    Microbot.log("Stuck outside/boat for > 3.5 minutes. Resetting position check & world hop...");
                    outsideStartTime = System.currentTimeMillis();
                    if (Rs2Player.getWorld() != config.world()) {
                        Microbot.hopToWorld(config.world());
                        sleepUntil(() -> Rs2Player.getWorld() == config.world(), 5000);
                    }
                }

                if (Rs2Player.getWorld() != config.world()) {
                    Microbot.hopToWorld(config.world());
                    sleepUntil(() -> Rs2Player.getWorld() == config.world(), 5000);
                    return;
                }

                clickGangplank();
                sleepUntil(this::isInBoat, 4000);
            } else {
                // Reset timeout timer when successfully waiting inside the boat
                outsideStartTime = 0;
            }
        }

        private void clickGangplank() {
            int cbLevel = Microbot.getClient().getLocalPlayer().getCombatLevel();
            int gangplankId = ObjectID.GANGPLANK_14315; // Novice
            if (cbLevel >= 100) {
                gangplankId = ObjectID.GANGPLANK_25632; // Veteran
            } else if (cbLevel >= 70) {
                gangplankId = ObjectID.GANGPLANK_25631; // Intermediate
            }
            Microbot.getRs2TileObjectCache().query().interact(gangplankId);
        }

        public boolean isInBoat() {
            return Microbot.getClientThread().runOnClientThreadOptional(
                    () -> Microbot.getClient().getWidget(WidgetInfo.PEST_CONTROL_BOAT_INFO) != null
            ).orElse(false);
        }

        public boolean isInPestControl() {
            return Microbot.getClientThread().runOnClientThreadOptional(
                    () -> Microbot.getClient().getWidget(WidgetInfo.PEST_CONTROL_BLUE_SHIELD) != null
            ).orElse(false);
        }
    }

    // =========================================================================
    // OVERLAY INTERFACE
    // =========================================================================
    public static class DroPesterOverlay extends OverlayPanel {
        @Inject
        DroPesterOverlay(DroPesterPlugin plugin) {
            super(plugin);
            setPosition(OverlayPosition.TOP_LEFT);
        }

        @Override
        public Dimension render(Graphics2D graphics) {
            try {
                panelComponent.setPreferredSize(new Dimension(220, 100));
                panelComponent.getChildren().add(TitleComponent.builder()
                        .text("DroPester v1.2")
                        .color(Color.GREEN)
                        .build());

                panelComponent.getChildren().add(LineComponent.builder().build());

                panelComponent.getChildren().add(LineComponent.builder()
                        .left("Status:")
                        .right(Microbot.status)
                        .build());
            } catch (Exception ex) {
                System.out.println(ex.getMessage());
            }
            return super.render(graphics);
        }
    }
}