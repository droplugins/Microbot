package net.runelite.client.plugins.microbot.drohunter;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigInformation;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.plugins.microbot.util.misc.Rs2Food;

@ConfigInformation("<html>"
        + "Deadfall hunter script by TaF"
        + "<p>This plugin automates hunting with Deadfall traps all over Gielinor.</p>\n"
        + "<p>Requirements:</p>\n"
        + "<ol>\n"
        + "    <li>Appropriate Hunter level for your chosen creature type</li>\n"
        + "    <li>Knife in bank or inventory</li>\n"
        + "    <li>Axe in bank or inventory</li>\n"
        + "</ol>\n"
        + "<p>Other valuable items:</p>\n"
        + "<ol>\n"
        + "    <li>Kandarin headgear for a chance at extra logs being cut</li>\n"
        + "    <li>Graceful for banking/travel</li>\n"
        + "    <li>Guild hunter outfit for increased catchrates</li>\n"
        + "</ol>\n"
        + "<p>Configure sleep timings in the settings for optimal performance.</p>\n"
        + "<p>Use the overlay option to display trap status and hunter information on screen.</p>"
        + "</html>")
@ConfigGroup("DeadfallHunter")
public interface DeadFallTrapHunterConfig extends Config {

    @ConfigItem(
            position = 0,
            keyName = "deadFallTrapHunting",
            name = "Creature to hunt",
            description = "Select which creature to hunt",
            section = "deadfallsSection"
    )
    default DeadFallTrapHunting deadFallTrapHunting() {
        return DeadFallTrapHunting.PYRE_FOX;
    }

    @ConfigItem(
            position = 1,
            keyName = "axeInInventory",
            name = "Use axe in inventory?",
            description = "Use axe in inventory?",
            section = "deadfallsSection"
    )
    default boolean axeInInventory() {
        return true;
    }

    @ConfigItem(
            position = 2,
            keyName = "UseMeatPouch",
            name = "Use meat pouch?",
            description = "Do you have a meat pouch?",
            section = "deadfallsSection"
    )
    default boolean UseMeatPouch() {
        return true;
    }

    @ConfigItem(
            position = 3,
            keyName = "MeatPouch",
            name = "Meat pouch",
            description = "Which meat pouch should the script use?",
            section = "deadfallsSection"
    )
    default MeatPouch MeatPouch() {
        return MeatPouch.LARGE_MEAT_POUCH;
    }

    @ConfigItem(
            position = 4,
            keyName = "EatAtBank",
            name = "Eat at bank",
            description = "Auto eats food at the bank to fill up your hitpoints.",
            section = "deadfallsSection"
    )
    default boolean AutoEat() {
        return true;
    }

    @ConfigItem(
            position = 5,
            keyName = "FoodToEatAtBank",
            name = "Food to eat at bank",
            description = "What food should we eat at the bank?",
            section = "deadfallsSection"
    )
    default Rs2Food FoodToEatAtBank() {
        return Rs2Food.LOBSTER;
    }

    @ConfigItem(
            position = 6,
            keyName = "runToBankHP",
            name = "Run to bank at HP",
            description = "Run to the bank to eat <= hitpoints",
            section = "deadfallsSection"
    )
    default int runToBankHP() {
        return 25;
    }

    @ConfigItem(
            position = 7,
            keyName = "deadfallProgressiveHunting",
            name = "Deadfalls: Automatically select best creature",
            description = "This will override the selected creature. Furthermore, it will move you to the next location when you meet the requirements.",
            section = "deadfallsSection"
    )
    default boolean deadfallProgressiveHunting() {
        return false;
    }

    @ConfigItem(
            position = 8,
            keyName = "xpMode",
            name = "XP Mode",
            description = "Disables looting and banking to maximize XP per hour",
            section = "deadfallsSection"
    )
    default boolean xpMode() {
        return false;
    }

    @ConfigItem(
            position = 9,
            keyName = "deadfallShowOverlay",
            name = "Deadfalls: Show Overlay",
            description = "Displays overlay with traps and status",
            section = "deadfallsSection"
    )
    default boolean deadfallShowOverlay() {
        return true;
    }

    @ConfigItem(
            position = 10,
            keyName = "deadfallMinSleepAfterCatch",
            name = "Deadfalls: Min. Sleep After Catch",
            description = "Min sleep after catch",
            section = "deadfallsSection"
    )
    default int deadfallMinSleepAfterCatch() {
        return 4500;
    }

    @ConfigItem(
            position = 11,
            keyName = "deadfallMaxSleepAfterCatch",
            name = "Deadfalls: Max. Sleep After Catch",
            description = "Max sleep after catch",
            section = "deadfallsSection"
    )
    default int deadfallMaxSleepAfterCatch() {
        return 7000;
    }

    @ConfigItem(
            position = 12,
            keyName = "deadfallMinSleepAfterLay",
            name = "Deadfalls: Min. Sleep After Lay",
            description = "Min sleep after lay",
            section = "deadfallsSection"
    )
    default int deadfallMinSleepAfterLay() {
        return 2000;
    }

    @ConfigItem(
            position = 13,
            keyName = "deadfallMaxSleepAfterLay",
            name = "Deadfalls: Max. Sleep After Lay",
            description = "Max sleep after lay",
            section = "deadfallsSection"
    )
    default int deadfallMaxSleepAfterLay() {
        return 5000;
    }


}
