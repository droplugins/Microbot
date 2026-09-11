package net.runelite.client.plugins.microbot.drochaos;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.ItemID;
import net.runelite.api.Player;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.WorldType;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.shortestpath.ShortestPathPlugin;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;
import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.player.Rs2Pvp;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public class DroChaosScript extends Script
{
    private static final int CHAOS_ALTAR_ID = 411;
    private static final int MINIMUM_COMBAT_LEVEL = 3;
    private static final int MAXIMUM_COMBAT_LEVEL = 126;
    private static final int[] CHARGED_BURNING_AMULETS = {
            ItemID.BURNING_AMULET5,
            ItemID.BURNING_AMULET4,
            ItemID.BURNING_AMULET3,
            ItemID.BURNING_AMULET2,
            ItemID.BURNING_AMULET1
    };
    private static final WorldArea CHAOS_TEMPLE = new WorldArea(2947, 3818, 11, 6, 0);
    private static final WorldArea EDGEVILLE_RESPAWN_AREA = new WorldArea(3060, 3450, 66, 70, 0);
    private static final WorldArea LUMBRIDGE_BASEMENT_AREA = new WorldArea(3190, 9590, 60, 65, 0);
    private static final WorldPoint CHAOS_ALTAR = new WorldPoint(2949, 3820, 0);
    private static final WorldPoint CHAOS_ALTAR_APPROACH = new WorldPoint(2972, 3810, 0);

    private DroChaosConfig config;
    private boolean autoRetaliateConfigured;
    private boolean stateInitialized;
    private boolean wasInWilderness;
    private boolean mustBankBeforeTravel;
    private final AtomicBoolean threatLogoutActive = new AtomicBoolean(false);

    @Getter
    private volatile String status = "Idle";

    @Getter
    private volatile String bankName = "Detecting";

    public boolean run(DroChaosConfig config)
    {
        this.config = config;
        Microbot.enableAutoRunOn = false;

        scheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try
            {
                if (!Microbot.isLoggedIn())
                {
                    threatLogoutActive.set(false);
                    return;
                }
                if (Rs2Pvp.isInWilderness() && hasNearbyThreat())
                {
                    requestThreatLogout();
                }
            }
            catch (Exception exception)
            {
                log.error("DroChaos threat watcher failed", exception);
            }
        }, 0, 100, TimeUnit.MILLISECONDS);

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try
            {
                if (!Microbot.isLoggedIn() || !super.run())
                {
                    return;
                }

                if (threatLogoutActive.get())
                {
                    return;
                }

                if (!autoRetaliateConfigured)
                {
                    Rs2Combat.setAutoRetaliate(false);
                    autoRetaliateConfigured = true;
                }

                if (logoutForThreat())
                {
                    return;
                }

                runLoopStep();
            }
            catch (Exception exception)
            {
                log.error("DroChaos loop failed", exception);
                status = "Recovering";
            }
        }, 0, 100, TimeUnit.MILLISECONDS);
        return true;
    }

    private void runLoopStep()
    {
        boolean inWilderness = Rs2Pvp.isInWilderness();
        boolean hasBones = getLiveDragonBoneCount() > 0;

        if (!stateInitialized)
        {
            wasInWilderness = inWilderness;
            mustBankBeforeTravel = !inWilderness && (!hasBones || !hasBurningAmulet());
            stateInitialized = true;
        }

        if (wasInWilderness && !inWilderness)
        {
            mustBankBeforeTravel = true;
            status = "Respawned - banking";
        }
        wasInWilderness = inWilderness;

        if (!inWilderness && (mustBankBeforeTravel || !hasBones || !hasBurningAmulet()))
        {
            mustBankBeforeTravel = true;
            bankAtRespawn();
            return;
        }

        if (!inWilderness)
        {
            status = "Travelling to altar";
            Rs2Walker.walkTo(CHAOS_ALTAR_APPROACH);
            return;
        }

        if (hasBones)
        {
            offerNextBone();
            return;
        }

        suicideWithWine();
    }

    private void bankAtRespawn()
    {
        BankLocation bank = selectRespawnBank();
        if (bank == BankLocation.EDGEVILLE)
        {
            bankName = "Edgeville";
        }
        else if (bank == BankLocation.LUMBRIDGE_BASEMENT)
        {
            bankName = "Lumbridge basement";
        }
        else
        {
            bankName = "Lumbridge upstairs";
        }
        status = "Banking " + bankName.toLowerCase();

        if (!Rs2Bank.isOpen() && !Rs2Bank.walkToBankAndUseBank(bank))
        {
            return;
        }

        int liveBoneCount = getLiveDragonBoneCount();
        if (liveBoneCount == 0 && Rs2Inventory.count() > 0)
        {
            Rs2Bank.depositAll();
        }

        if (!Rs2Bank.hasItem(ItemID.DRAGON_BONES))
        {
            stopForMissingSupply("No Dragon bones in bank");
            return;
        }

        if (!hasBurningAmulet())
        {
            int amuletId = firstChargedBurningAmuletInBank();
            if (amuletId == -1)
            {
                stopForMissingSupply("No charged Burning amulet in bank");
                return;
            }
            Rs2Bank.withdrawAndEquip(amuletId);
            Rs2Inventory.waitForInventoryChanges(2000);
        }

        if (getLiveDragonBoneCount() == 0)
        {
            Rs2Bank.withdrawAll(ItemID.DRAGON_BONES);
            sleepUntil(() -> getLiveDragonBoneCount() > 0, 3000);
        }

        if (getLiveDragonBoneCount() > 0 && hasBurningAmulet() && Rs2Bank.closeBank())
        {
            mustBankBeforeTravel = false;
            status = "Supplies ready";
        }
    }

    private int getLiveDragonBoneCount()
    {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            Client client = Microbot.getClient();
            ItemContainer inventory = client == null ? null : client.getItemContainer(InventoryID.INVENTORY);
            if (inventory == null || inventory.getItems() == null)
            {
                return 0;
            }

            int count = 0;
            for (Item item : inventory.getItems())
            {
                if (item != null && item.getId() == ItemID.DRAGON_BONES)
                {
                    count += item.getQuantity();
                }
            }
            return count;
        }).orElse(0);
    }

    private BankLocation selectRespawnBank()
    {
        WorldPoint location = Rs2Player.getWorldLocation();
        if (location != null && EDGEVILLE_RESPAWN_AREA.contains(location))
        {
            return BankLocation.EDGEVILLE;
        }
        if (location != null && LUMBRIDGE_BASEMENT_AREA.contains(location))
        {
            return BankLocation.LUMBRIDGE_BASEMENT;
        }
        return hasLumbridgeBasementAccess()
                ? BankLocation.LUMBRIDGE_BASEMENT
                : BankLocation.LUMBRIDGE_TOP;
    }

    private boolean hasLumbridgeBasementAccess()
    {
        return Rs2Player.getQuestState(Quest.RECIPE_FOR_DISASTER__ANOTHER_COOKS_QUEST)
                == QuestState.FINISHED;
    }

    private boolean hasBurningAmulet()
    {
        return Rs2Inventory.contains(item -> item != null
                && item.getName().toLowerCase().contains("burning amulet"))
                || Rs2Equipment.isWearing("burning amulet", false);
    }

    private int firstChargedBurningAmuletInBank()
    {
        for (int amuletId : CHARGED_BURNING_AMULETS)
        {
            if (Rs2Bank.hasItem(amuletId))
            {
                return amuletId;
            }
        }
        return -1;
    }

    private void offerNextBone()
    {
        if (!isAtChaosAltar())
        {
            status = "Walking to altar";
            Rs2Walker.walkTo(CHAOS_ALTAR);
            return;
        }

        if (logoutForThreat())
        {
            return;
        }

        Rs2ItemModel bone = Rs2Inventory.getBones().stream()
                .max(Comparator.comparingInt(Rs2ItemModel::getSlot))
                .orElse(null);
        if (bone == null)
        {
            return;
        }

        status = "Offering bones";
        if (Rs2Inventory.interact(bone, "use"))
        {
            sleep(config.fastOffering() ? 50 : 150, config.fastOffering() ? 125 : 225);
            Microbot.getRs2TileObjectCache().query().withId(CHAOS_ALTAR_ID).interact("Use");
            Rs2Player.waitForXpDrop(Skill.PRAYER);
        }
    }

    private boolean isAtChaosAltar()
    {
        WorldPoint location = Rs2Player.getWorldLocation();
        if (location == null || !CHAOS_TEMPLE.contains(location))
        {
            return false;
        }
        Rs2TileObjectModel altar = Microbot.getRs2TileObjectCache().query().withId(CHAOS_ALTAR_ID).nearest();
        return altar != null && altar.isReachable();
    }

    private void suicideWithWine()
    {
        status = "Suiciding on wine";

        if (logoutForThreat())
        {
            return;
        }

        if (Rs2Inventory.contains("Wine of zamorak"))
        {
            Rs2Inventory.drop("Wine of zamorak", true);
            sleep(150, 300);
            return;
        }

        boolean tookWine = Microbot.getRs2TileItemCache().query()
                .withName("Wine of zamorak")
                .within(12)
                .interact("Take");
        if (tookWine)
        {
            sleep(300, 600);
        }
        else
        {
            Rs2Walker.walkTo(CHAOS_ALTAR);
            sleep(300, 600);
        }
    }

    private boolean logoutForThreat()
    {
        if (!Rs2Pvp.isInWilderness() || !hasNearbyThreat())
        {
            return false;
        }

        status = "Threat detected - logging out";
        requestThreatLogout();
        return true;
    }

    private void requestThreatLogout()
    {
        if (!threatLogoutActive.compareAndSet(false, true))
        {
            return;
        }

        try
        {
            status = "Threat detected - logging out";
            Microbot.log("DroChaos: threatening player detected; logging out");
            ShortestPathPlugin.exit();
            Rs2Player.logout();
            sleep(500, 800);
        }
        finally
        {
            if (Microbot.isLoggedIn())
            {
                threatLogoutActive.set(false);
            }
        }
    }

    private boolean hasNearbyThreat()
    {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            Client client = Microbot.getClient();
            Player localPlayer = client == null ? null : client.getLocalPlayer();
            if (localPlayer == null)
            {
                return false;
            }

            WorldPoint localLocation = localPlayer.getWorldLocation();
            int wildernessLevel = Rs2Pvp.getWildernessLevelFrom(localLocation);
            if (wildernessLevel <= 0)
            {
                return false;
            }

            boolean deadmanWorld = WorldType.isDeadmanWorld(client.getWorldType());
            int attackRange = wildernessLevel + (WorldType.isPvpWorld(client.getWorldType()) ? 15 : 0);
            int localCombat = localPlayer.getCombatLevel();
            int minimumThreatLevel = deadmanWorld
                    ? MINIMUM_COMBAT_LEVEL
                    : Math.max(MINIMUM_COMBAT_LEVEL, localCombat - attackRange);
            int maximumThreatLevel = Math.min(MAXIMUM_COMBAT_LEVEL,
                    deadmanWorld
                            ? MAXIMUM_COMBAT_LEVEL
                            : localCombat + attackRange + config.upperCombatBuffer());
            List<Player> players = client.getPlayers();

            for (Player player : players)
            {
                if (player == null || player == localPlayer || player.getWorldLocation() == null)
                {
                    continue;
                }
                if (player.getWorldLocation().distanceTo(localLocation) > config.threatRadius())
                {
                    continue;
                }

                int combatLevel = player.getCombatLevel();
                if (combatLevel >= minimumThreatLevel && combatLevel <= maximumThreatLevel)
                {
                    return true;
                }
            }
            return false;
        }).orElse(false);
    }

    private void stopForMissingSupply(String message)
    {
        status = message;
        Microbot.log("DroChaos: " + message + "; stopping script");
        shutdown();
    }

    @Override
    public void shutdown()
    {
        autoRetaliateConfigured = false;
        stateInitialized = false;
        wasInWilderness = false;
        mustBankBeforeTravel = false;
        threatLogoutActive.set(false);
        status = "Idle";
        bankName = "Detecting";
        super.shutdown();
    }
}
