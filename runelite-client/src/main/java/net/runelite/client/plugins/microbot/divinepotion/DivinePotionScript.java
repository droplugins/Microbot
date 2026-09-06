package net.runelite.client.plugins.microbot.divinepotion;

import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

import java.util.concurrent.TimeUnit;

public class DivinePotionScript extends Script {

    public boolean run(DivinePotionConfig config) {
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!super.run() || !Microbot.isLoggedIn()) return;

                if (Rs2Player.getBoostedSkillLevel(Skill.HITPOINTS) <= 10) return;

                boolean drank = false;

                if (config.sipMelee() && !drank) {
                    drank = checkAndSip(Skill.ATTACK, "Divine super combat", "Divine attack", "Divine strength");
                }

                if (config.sipRanged() && !drank) {
                    drank = checkAndSip(Skill.RANGED, "Divine ranging");
                }

                if (config.sipMagic() && !drank) {
                    drank = checkAndSip(Skill.MAGIC, "Divine magic");
                }

            } catch (Exception ex) {
                Microbot.log(ex.getMessage());
            }
        }, 0, 600, TimeUnit.MILLISECONDS);
        return true;
    }

    private boolean checkAndSip(Skill skill, String... potionNames) {
        int currentLevel = Rs2Player.getBoostedSkillLevel(skill);
        int baseLevel = Rs2Player.getRealSkillLevel(skill);

        if (currentLevel <= baseLevel) {
            for (String potionName : potionNames) {
                if (Rs2Inventory.hasItem(potionName)) {
                    Rs2Inventory.interact(potionName, "Drink");
                    Rs2Antiban.actionCooldown();
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public void shutdown() {
        super.shutdown();
    }
}