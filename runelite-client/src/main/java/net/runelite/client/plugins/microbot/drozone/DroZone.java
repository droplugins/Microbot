package net.runelite.client.plugins.microbot.drozone;

import com.google.inject.Provides;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.gameval.ObjectID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.api.npc.Rs2NpcCache;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.api.tileobject.Rs2TileObjectCache;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.antiban.enums.Activity;
import net.runelite.client.plugins.microbot.util.antiban.enums.ActivityIntensity;
import net.runelite.client.plugins.microbot.util.antiban.enums.PlayStyle;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.security.Encryption;
import net.runelite.client.plugins.microbot.util.security.LoginManager;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import javax.inject.Inject;
import java.util.concurrent.TimeUnit;

@PluginDescriptor(
        name = "[Dro] Zone",
        description = "All-in-one automated Nightmare Zone handler. Note: Start plugin in the NMZ lobby or outside; bank restocking is handled via lobby barrels/chests.",
        tags = {"nmz", "combat", "training", "dro"},
        enabledByDefault = false,
        hidden = false
)
@Slf4j
public class DroZone extends Plugin {

    @Inject
    private DroZoneConfig config;

    @Inject
    private DroZoneScript script;

    @Provides
    DroZoneConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(DroZoneConfig.class);
    }

    @Override
    protected void startUp() {
        script.run(config, this);
    }

    @Override
    protected void shutDown() {
        script.shutdown();
        DroZoneScript.setHasSurge(false);
    }

    @Subscribe
    public void onActorDeath(ActorDeath actorDeath) {
        if (config.stopAfterDeath() && actorDeath.getActor() == Microbot.getClient().getLocalPlayer()) {
            Microbot.getClientThread().runOnSeperateThread(() -> {
                Global.sleepUntil(script::isOutside, 10000);
                Microbot.stopPlugin(this);
                return true;
            });
        }
    }

    @Subscribe
    public void onChatMessage(ChatMessage event) {
        if (event.getType() == ChatMessageType.GAMEMESSAGE) {
            if (event.getMessage().equalsIgnoreCase("you feel a surge of special attack power!")) {
                DroZoneScript.setHasSurge(true);
            } else if (event.getMessage().equalsIgnoreCase("your surge of special attack power has ended.")) {
                DroZoneScript.setHasSurge(false);
            }
        }
    }

    @ConfigGroup("drozone")
    public interface DroZoneConfig extends Config {
        @ConfigItem(
                keyName = "overloadPotionAmount",
                name = "Overload Potions",
                description = "Number of overload potions (4-dose) to withdraw",
                position = 1
        )
        default int overloadPotionAmount() {
            return 4;
        }

        @ConfigItem(
                keyName = "absorptionPotionAmount",
                name = "Absorption Potions",
                description = "Number of absorption potions (4-dose) to withdraw",
                position = 2
        )
        default int absorptionPotionAmount() {
            return 20;
        }

        @ConfigItem(
                keyName = "walkToCenter",
                name = "Walk to Center",
                description = "Walk to the center of the NMZ arena",
                position = 3
        )
        default boolean walkToCenter() {
            return true;
        }

        @ConfigItem(
                keyName = "stopAfterDeath",
                name = "Stop After Death",
                description = "Automatically stop plugin on player death",
                position = 4
        )
        default boolean stopAfterDeath() {
            return true;
        }
    }

    public static class DroZoneScript extends Script {

        private DroZoneConfig config;
        private DroZone plugin;

        public static int minAbsorption = Rs2Random.between(100, 200);
        private WorldPoint center = new WorldPoint(2272, 4695, 0);

        @Getter
        @Setter
        private static boolean hasSurge = false;
        private long lastOverloadTime = 0;
        private static final long OVERLOAD_COOLDOWN_MS = 15000;
        private boolean initialSetupDone = false;
        private long dreamEntryTime = 0;

        @Inject
        private Rs2TileObjectCache tileObjectCache;
        @Inject
        private Rs2NpcCache npcCache;

        public boolean canStartNmz() {
            return Rs2Inventory.count("Overload (4)") >= config.overloadPotionAmount();
        }

        public boolean hasPotion(String name) {
            for (int i = 1; i <= 4; i++) {
                if (Rs2Inventory.hasItem(name + " (" + i + ")")) return true;
            }
            return false;
        }

        public void drinkPotion(String name) {
            for (int i = 1; i <= 4; i++) {
                String potName = name + " (" + i + ")";
                if (Rs2Inventory.hasItem(potName)) {
                    int delay = Rs2Random.between(200, 600);
                    Global.sleep(delay);
                    Rs2Inventory.interact(potName, "Drink");
                    return;
                }
            }
        }

        public boolean run(DroZoneConfig config, DroZone plugin) {
            this.config = config;
            this.plugin = plugin;
            initialSetupDone = false;
            dreamEntryTime = 0;

            Microbot.getSpecialAttackConfigs().setSpecialAttack(true);

            // Native antiban initialization with drastically reduced action cooldown chance (1%)
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

            // Delayed randomized gradual zoom setup on startup: executes once after a randomized 1-7 second delay
            Microbot.getClientThread().runOnSeperateThread(() -> {
                Global.sleep(Rs2Random.between(1000, 7000));

                int currentZoom = Rs2Camera.getZoom();
                int targetZoom = Rs2Random.between(250, 450);
                int stepDirection = targetZoom > currentZoom ? 1 : -1;

                while (Math.abs(currentZoom - targetZoom) > 5) {
                    int step = Rs2Random.between(5, 25);
                    currentZoom += step * stepDirection;
                    if ((stepDirection > 0 && currentZoom > targetZoom) || (stepDirection < 0 && currentZoom < targetZoom)) {
                        currentZoom = targetZoom;
                    }
                    Rs2Camera.setZoom(currentZoom);
                    Global.sleep(Rs2Random.between(40, 120));
                }

                Rs2Camera.setPitch(Rs2Random.between(3000, 3064));
                Global.sleep(Rs2Random.between(150, 400));
                Rs2Camera.setYaw(Rs2Random.between(0, 16383));

                return true;
            });

            mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
                try {
                    if (!Microbot.isLoggedIn()) return;

                    boolean withinDreamGracePeriod = !isOutside() && (!initialSetupDone || (System.currentTimeMillis() - dreamEntryTime < 120000));

                    if (!withinDreamGracePeriod) {
                        if (Rs2AntibanSettings.actionCooldownActive) return;
                    }

                    if (isOutside()) {
                        lastOverloadTime = 0;
                        initialSetupDone = false;
                        dreamEntryTime = 0;
                        Rs2Walker.setTarget(null);
                        handleOutsideNmz();
                    } else {
                        if (dreamEntryTime == 0) {
                            dreamEntryTime = System.currentTimeMillis();
                        }
                        handleInsideNmz();
                    }
                } catch (Exception ex) {
                    Microbot.log("[Dro Zone Error] " + ex.getMessage());
                }
            }, 0, 600, TimeUnit.MILLISECONDS);
            return true;
        }

        @Override
        public void shutdown() {
            super.shutdown();
            Rs2Antiban.deactivateAntiban();
            Rs2Antiban.resetAntibanSettings();
            lastOverloadTime = 0;
            initialSetupDone = false;
            dreamEntryTime = 0;
        }

        public boolean isOutside() {
            WorldPoint loc = Rs2Player.getWorldLocation();
            return loc != null && loc.distanceTo(new WorldPoint(2602, 3116, 0)) < 20;
        }

        public boolean isInDream() {
            WorldPoint loc = Rs2Player.getWorldLocation();
            return loc != null && loc.getY() > 4500;
        }

        public void handleOutsideNmz() {
            boolean hasStartedDream = Microbot.getVarbitValue(VarbitID.NZONE_PURCHASEDDREAM) > 0;
            if (!hasStartedDream) {
                startNmzDream();
            } else {
                depositPotions(ObjectID.NZONE_BARREL_3, "Overload", config.overloadPotionAmount());
                depositPotions(ObjectID.NZONE_BARREL_4, "Absorption", config.absorptionPotionAmount());
                handleStore();
                fetchPotions(ObjectID.NZONE_BARREL_3, "Overload", config.overloadPotionAmount());
                if (Rs2Inventory.hasItemAmount("Overload (4)", config.overloadPotionAmount())) {
                    fetchPotions(ObjectID.NZONE_BARREL_4, "Absorption", config.absorptionPotionAmount());
                }
            }
            if (canStartNmz()) {
                consumeEmptyVial();
            } else {
                Global.sleep(2000);
            }
        }

        public void handleInsideNmz() {
            if (!isInDream()) return;

            boolean autoRetaliateOff = Microbot.getVarbitValue(464) == 1;

            if (autoRetaliateOff) {
                Global.sleep(Rs2Random.between(200, 600));
                Rs2Combat.setAutoRetaliate(true);
                Global.sleep(400, 800);
                return;
            }

            if (initialSetupDone && System.currentTimeMillis() - dreamEntryTime >= 120000) {
                Rs2Antiban.takeMicroBreakByChance();
            }

            if (config.walkToCenter()) {
                walkToCenter();
            }

            if (!initialSetupDone) {
                int currentHP = Microbot.getClient().getBoostedSkillLevel(Skill.HITPOINTS);
                int currentAbsorption = Microbot.getVarbitValue(VarbitID.NZONE_ABSORB_POTION_EFFECTS);

                if (currentAbsorption < 900 && hasPotion("Absorption")) {
                    drinkPotion("Absorption");
                    Global.sleep(800, 1400);
                    return;
                }

                boolean overloadBoostActive = Microbot.getClient().getBoostedSkillLevel(Skill.STRENGTH) > Microbot.getClient().getRealSkillLevel(Skill.STRENGTH);

                if (!overloadBoostActive && hasPotion("Overload")) {
                    drinkPotion("Overload");
                    lastOverloadTime = System.currentTimeMillis();
                    Global.sleep(1200, 2000);
                    return;
                }

                if (currentHP > 1) {
                    guzzleOrFeel();
                    Global.sleep(600, 1000);
                    return;
                }

                initialSetupDone = true;
            } else {
                manageOverloads();
                manageSelfHarm();
                useAbsorptionPotion();

                if (!Rs2Player.isInCombat()) {
                    Rs2NpcModel closestNpc = npcCache.query().nearest();
                    if (closestNpc != null) {
                        Global.sleep(Rs2Random.between(600, 900));
                        if (closestNpc.click("Attack")) {
                            int tickDelay = Rs2Random.between(1, 3);
                            Global.sleep(tickDelay * 600);
                            Rs2Antiban.actionCooldown();
                        }
                    }
                }
            }
        }

        private void walkToCenter() {
            WorldPoint playerLoc = Rs2Player.getWorldLocation();
            if (playerLoc != null && center.distanceTo(playerLoc) > 4) {
                Rs2Walker.walkTo(center);
            }
        }

        private void guzzleOrFeel() {
            int delay = Rs2Random.between(200, 600);
            Global.sleep(delay);
            if (Rs2Inventory.hasItem("Locator orb")) {
                Rs2Inventory.interact("Locator orb", "Feel");
                if (initialSetupDone && System.currentTimeMillis() - dreamEntryTime >= 120000) {
                    int tickDelay = Rs2Random.between(1, 3);
                    Global.sleep(tickDelay * 600);
                    Rs2Antiban.actionCooldown();
                }
            } else if (Rs2Inventory.hasItem("Dwarven rock cake")) {
                Rs2Inventory.interact("Dwarven rock cake", "Guzzle");
                if (initialSetupDone && System.currentTimeMillis() - dreamEntryTime >= 120000) {
                    int tickDelay = Rs2Random.between(1, 3);
                    Global.sleep(tickDelay * 600);
                    Rs2Antiban.actionCooldown();
                }
            } else if (Rs2Inventory.hasItem("Rock cake")) {
                Rs2Inventory.interact("Rock cake", "Guzzle");
                if (initialSetupDone && System.currentTimeMillis() - dreamEntryTime >= 120000) {
                    int tickDelay = Rs2Random.between(1, 3);
                    Global.sleep(tickDelay * 600);
                    Rs2Antiban.actionCooldown();
                }
            }
        }

        public void startNmzDream() {
            center = new WorldPoint(Rs2Random.between(2270, 2276), Rs2Random.between(4693, 4696), 0);
            Rs2NpcModel dominic = npcCache.query().withName("Dominic Onion").nearestOnClientThread();
            if (dominic != null) {
                Global.sleep(Rs2Random.between(600, 900));
                dominic.click("Dream");
            }
            Global.sleepUntil(() -> Rs2Widget.hasWidget("Which dream would you like to experience?"), 5000);
            Global.sleep(Rs2Random.between(200, 600));
            Rs2Widget.clickWidget("Previous:");
            Global.sleepUntil(() -> Rs2Widget.hasWidget("Click here to continue"), 5000);
            Global.sleep(Rs2Random.between(200, 600));
            Rs2Widget.clickWidget("Click here to continue");
            Global.sleepUntil(() -> Rs2Widget.hasWidget("Agree to pay"), 5000);
            if (Rs2Widget.hasWidget("Agree to pay")) {
                Global.sleep(Rs2Random.between(200, 600));
                Rs2Keyboard.typeString("1");
                Rs2Keyboard.enter();
            }
        }

        private void depositPotions(int objectId, String baseName, int requiredAmount) {
            int count4 = Rs2Inventory.count(baseName + " (4)");
            int count3 = Rs2Inventory.count(baseName + " (3)");
            int count2 = Rs2Inventory.count(baseName + " (2)");
            int count1 = Rs2Inventory.count(baseName + " (1)");

            if (count4 == requiredAmount && count3 == 0 && count2 == 0 && count1 == 0) return;
            if (count4 == 0 && count3 == 0 && count2 == 0 && count1 == 0) return;

            Rs2TileObjectModel obj = tileObjectCache.query().withId(objectId).nearest();
            if (obj == null) return;
            Global.sleep(Rs2Random.between(200, 600));
            obj.click("Store");
            Global.sleepUntil(() -> Rs2Widget.hasWidget("Store all your "), 5000);
            if (Rs2Widget.hasWidget("Store all your ")) {
                Global.sleep(Rs2Random.between(400, 900));
                Rs2Keyboard.typeString("1");
                Global.sleep(Rs2Random.between(200, 500));
                Rs2Keyboard.enter();
                Global.sleepUntil(() -> !hasPotion(baseName), 5000);
            }
        }

        private void fetchPotions(int objectId, String baseName, int requiredAmount) {
            if (Rs2Inventory.count(baseName + " (4)") == requiredAmount) return;

            Rs2TileObjectModel obj = tileObjectCache.query().withId(objectId).nearest();
            if (obj == null) return;
            Global.sleep(Rs2Random.between(200, 600));
            obj.click("Take");
            Global.sleepUntil(() -> Rs2Widget.hasWidget("How many doses of "), 5000);

            if (Rs2Widget.hasWidget("How many doses of ")) {
                Global.sleep(Rs2Random.between(400, 900));
                int needed = requiredAmount - Rs2Inventory.count(baseName + " (4)");
                Rs2Keyboard.typeString(Integer.toString(needed * 4));
                Global.sleep(Rs2Random.between(200, 500));
                Rs2Keyboard.enter();
                Global.sleepUntil(() -> Rs2Inventory.count(baseName + " (4)") == requiredAmount, 5000);
            }
        }

        public void manageOverloads() {
            if (!isInDream()) return;

            int currentHP = Microbot.getClient().getBoostedSkillLevel(Skill.HITPOINTS);
            boolean overloadBoostActive = Microbot.getClient().getBoostedSkillLevel(Skill.STRENGTH) > Microbot.getClient().getRealSkillLevel(Skill.STRENGTH);

            if (!overloadBoostActive && currentHP >= 51 && hasPotion("Overload")) {
                drinkPotion("Overload");
                lastOverloadTime = System.currentTimeMillis();
                if (System.currentTimeMillis() - dreamEntryTime >= 120000) {
                    int tickDelay = Rs2Random.between(1, 3);
                    Global.sleep(tickDelay * 600);
                    Rs2Antiban.actionCooldown();
                }
                Global.sleep(1200, 2000);
            }
        }

        public void manageSelfHarm() {
            if (!isInDream()) return;

            boolean overloadBoostActive = Microbot.getClient().getBoostedSkillLevel(Skill.STRENGTH) > Microbot.getClient().getRealSkillLevel(Skill.STRENGTH);
            if (!overloadBoostActive) return;

            if (System.currentTimeMillis() - lastOverloadTime < OVERLOAD_COOLDOWN_MS) return;

            int currentHP = Microbot.getClient().getBoostedSkillLevel(Skill.HITPOINTS);
            if (currentHP > 1) {
                guzzleOrFeel();
                Global.sleep(600, 1000);
            }
        }

        public void useAbsorptionPotion() {
            if (!isInDream()) return;

            int currentAbsorption = Microbot.getVarbitValue(VarbitID.NZONE_ABSORB_POTION_EFFECTS);
            if (currentAbsorption < minAbsorption && hasPotion("Absorption")) {
                int drinks = Rs2Random.between(2, 4);
                for (int i = 0; i < drinks; i++) {
                    drinkPotion("Absorption");
                    if (System.currentTimeMillis() - dreamEntryTime >= 120000) {
                        int tickDelay = Rs2Random.between(1, 3);
                        Global.sleep(tickDelay * 600);
                        Rs2Antiban.actionCooldown();
                    }
                    Global.sleep(Rs2Random.between(800, 1400));
                }
                minAbsorption = Rs2Random.between(100, 300);
            }
        }

        public void consumeEmptyVial() {
            if (Microbot.getClientThread().runOnClientThreadOptional(() ->
                            Rs2Widget.getWidget(129, 6) == null || Rs2Widget.getWidget(129, 6).isHidden())
                    .orElse(false)) {
                Rs2TileObjectModel vial = tileObjectCache.query().withId(ObjectID.NZONE_LOBBY_VIAL).nearest();
                if (vial != null) {
                    Global.sleep(Rs2Random.between(200, 600));
                    vial.click("drink");
                }
            }
            Global.sleep(2000, 4000);
            Widget widget = Rs2Widget.getWidget(129, 6);
            if (!Microbot.getClientThread().runOnClientThreadOptional(widget::isHidden).orElse(false)) {
                Global.sleep(Rs2Random.between(200, 600));
                Rs2Widget.clickWidget(widget.getId());
                Global.sleep(300);
                Global.sleep(Rs2Random.between(200, 600));
                Rs2Widget.clickWidget(widget.getId());
            }
            Global.sleep(2000, 4000);
        }

        public void handleStore() {
            if (canStartNmz()) return;
            int overloadAmt = Microbot.getVarbitValue(VarbitID.NZONE_POTION_3);
            int absorptionAmt = Microbot.getVarbitValue(VarbitID.NZONE_POTION_4);

            int requiredOverloadDoses = config.overloadPotionAmount() * 4;
            int requiredAbsorptionDoses = config.absorptionPotionAmount() * 4;

            int overloadDosesNeeded = Math.max(0, requiredOverloadDoses - overloadAmt);
            int absorptionDosesNeeded = Math.max(0, requiredAbsorptionDoses - absorptionAmt);

            if (overloadDosesNeeded == 0 && absorptionDosesNeeded == 0) return;

            int overloadToBuy = Math.min((overloadDosesNeeded + 3) / 4, 255);
            int absorptionToBuy = Math.min((absorptionDosesNeeded + 3) / 4, 255);

            if (overloadAmt >= requiredOverloadDoses || overloadAmt >= 255) overloadToBuy = 0;
            if (absorptionAmt >= requiredAbsorptionDoses || absorptionAmt >= 255) absorptionToBuy = 0;

            if (overloadToBuy == 0 && absorptionToBuy == 0) return;

            int totalCost = overloadToBuy * 1500 + absorptionToBuy * 1000;
            int nmzPoints = Microbot.getVarbitPlayerValue(VarPlayerID.NZONE_REWARDPOINTS);

            if (nmzPoints < totalCost) {
                Microbot.showMessage("BOT SHUTDOWN: Not enough points to buy potions (have " + nmzPoints + ", need " + totalCost + ")");
                Microbot.stopPlugin(plugin);
                return;
            }

            Rs2TileObjectModel chest = tileObjectCache.query().withId(ObjectID.NZONE_LOBBY_CHEST).nearest();
            if (chest == null) return;
            Global.sleep(Rs2Random.between(200, 600));
            chest.click();
            Global.sleepUntil(() -> Rs2Widget.isWidgetVisible(13500418) || Rs2Bank.isBankPinWidgetVisible(), 10000);
            if (Rs2Bank.isBankPinWidgetVisible()) {
                try {
                    Rs2Bank.handleBankPin(Encryption.decrypt(LoginManager.getActiveProfile().getBankPin()));
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                Global.sleepUntil(() -> Rs2Widget.isWidgetVisible(13500418), 10000);
            }

            Widget benefitsBtn = Rs2Widget.getWidget(13500418);
            if (benefitsBtn == null) return;
            if (benefitsBtn.getSpriteId() != 813) {
                Global.sleep(Rs2Random.between(200, 600));
                Rs2Widget.clickWidgetFast(benefitsBtn, 4, 4);
                Global.sleepUntil(() -> {
                    Widget btn = Rs2Widget.getWidget(13500418);
                    return btn != null && btn.getSpriteId() == 813;
                }, 3000);
            }

            for (int i = 0; i < overloadToBuy; i++) {
                if (Microbot.getVarbitValue(VarbitID.NZONE_POTION_3) >= requiredOverloadDoses ||
                        Microbot.getVarbitValue(VarbitID.NZONE_POTION_3) >= 255) break;
                Widget nmzRewardShop = Rs2Widget.getWidget(206, 6);
                if (nmzRewardShop == null) break;
                Global.sleep(Rs2Random.between(200, 600));
                Rs2Widget.clickWidgetFast(nmzRewardShop.getChild(6), 6, 4);
                Global.sleep(600, 1000);
            }

            for (int i = 0; i < absorptionToBuy; i++) {
                if (Microbot.getVarbitValue(VarbitID.NZONE_POTION_4) >= requiredAbsorptionDoses ||
                        Microbot.getVarbitValue(VarbitID.NZONE_POTION_4) >= 255) break;
                Widget nmzRewardShop = Rs2Widget.getWidget(206, 6);
                if (nmzRewardShop == null) break;
                Global.sleep(Rs2Random.between(200, 600));
                Rs2Widget.clickWidgetFast(nmzRewardShop.getChild(9), 9, 4);
                Global.sleep(600, 1000);
            }
        }
    }
}