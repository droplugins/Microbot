package net.runelite.client.plugins.microbot.drohunter;

import net.runelite.client.config.*;

@ConfigInformation("<html>"
        + "Salamander script by Bee & TaF"
        + "<p>This plugin automates salamander hunting all over Gielinor.</p>\n"
        + "<p>Requirements:</p>\n"
        + "<ol>\n"
        + "    <li>Appropriate Hunter level for your chosen salamander type</li>\n"
        + "    <li>Small fishing nets in bank or inventory</li>\n"
        + "    <li>Rope in bank or inventory</li>\n"
        + "</ol>\n"
        + "<p>Configure sleep timings and salamander type in the settings for optimal performance.</p>\n"
        + "<p>Use the overlay option to display trap status and hunter information on screen.</p>"
        + "</html>")
@ConfigGroup("Salamander")
public interface SalamanderConfig extends Config {

    @ConfigItem(
            position = 0,
            keyName = "salamanderHunting",
            name = "Salamander to hunt",
            description = "Select which salamander to hunt",
            section = "salamandersSection"
    )
    default SalamanderHunting salamanderHunting() {
        return SalamanderHunting.GREEN;
    }

    @ConfigItem(
            position = 1,
            keyName = "salamanderProgressiveHunting",
            name = "Salamanders: Automatically select best creature",
            description = "This will override the selected salamander. Furthermore, it will move you to the next location when you meet the requirements.",
            section = "salamandersSection"
    )
    default boolean salamanderProgressiveHunting() {
        return false;
    }

    @ConfigItem(
            position = 2,
            keyName = "salamanderShowOverlay",
            name = "Salamanders: Show Overlay",
            description = "Displays overlay with traps and status",
            section = "salamandersSection"
    )
    default boolean salamanderShowOverlay() {
        return true;
    }

    @ConfigItem(
            position = 3,
            keyName = "withdrawNumber",
            name = "Number of nets/ropes to withdraw",
            description = "Number of nets/ropes to withdraw from bank",
            section = "salamandersSection"
    )
    @Range(
            min = 3,
            max = 13
    )
    default int withdrawNumber() {
        return 8;
    }

    @ConfigItem(
            position = 4,
            keyName = "salamanderMinSleepAfterCatch",
            name = "Salamanders: Min. Sleep After Catch",
            description = "Min sleep after catch",
            section = "salamandersSection"
    )
    default int salamanderMinSleepAfterCatch() {
        return 7500;
    }

    @ConfigItem(
            position = 5,
            keyName = "salamanderMaxSleepAfterCatch",
            name = "Salamanders: Max. Sleep After Catch",
            description = "Max sleep after catch",
            section = "salamandersSection"
    )
    default int salamanderMaxSleepAfterCatch() {
        return 8400;
    }

    @ConfigItem(
            position = 6,
            keyName = "salamanderMinSleepAfterLay",
            name = "Salamanders: Min. Sleep After Lay",
            description = "Min sleep after lay",
            section = "salamandersSection"
    )
    default int salamanderMinSleepAfterLay() {
        return 4000;
    }

    @ConfigItem(
            position = 7,
            keyName = "salamanderMaxSleepAfterLay",
            name = "Salamanders: Max. Sleep After Lay",
            description = "Max sleep after lay",
            section = "salamandersSection"
    )
    default int salamanderMaxSleepAfterLay() {
        return 5400;
    }


}
