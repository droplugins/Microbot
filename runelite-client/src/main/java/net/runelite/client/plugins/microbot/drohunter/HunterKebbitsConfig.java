package net.runelite.client.plugins.microbot.drohunter;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigInformation;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("Hunter Kebbits")
@ConfigInformation("<html>"
        + "<b>Piscatoris Kebbit Hunter by VIP</b><br>"
        + "Version: 1.0<br><br>"
        + "<b>Description:</b><br>"
        + "This script automates Kebbit hunting in the Piscatoris Falconry area.<br><br>"
        + "<b>Requirements:</b>"
        + "<ul>"
        + "    <li>Appropriate Hunter level for the selected Kebbit.</li>"
        + "    <li>Access to the Piscatoris Falconry area.</li>"
        + "    <li>A falcon and Falconer's glove (obtained from Matthias).</li>"
        + "</ul>"
        + "<b>Configuration:</b>"
        + "<ul>"
        + "    <li>Select the type of Kebbit to hunt in the settings.</li>"
        + "    <li>Enable the overlay to monitor the script's status.</li>"
        + "</ul>"
        + "</html>")
public interface HunterKebbitsConfig extends Config {

    /**
     * Manually select the type of Kebbit to hunt.
     * This option is overridden if progressive hunting is enabled.
     *
     * @return The selected Kebbit type.
     */
    @ConfigItem(
            keyName = "kebbitType",
            name = "Kebbit Type",
            description = "Choose which kebbit to hunt",
            position = 1,
            section = "kebbitsSection"
    )
    default KebbitHunting kebbitType() {
        return KebbitHunting.SPOTTED;
    }

    /**
     * Automatically determines the best Kebbit to hunt based on current Hunter level.
     * Overrides the manually selected Kebbit type.
     *
     * @return True if progressive hunting is enabled, false otherwise.
     */
    @ConfigItem(
            position = 1,
            keyName = "kebbitProgressiveHunting",
            name = "Kebbits: Automatically select best creature",
            description = "This will override the selected Kebbit. Furthermore, it will move you to the next location when you meet the requirements.",
            section = "kebbitsSection"
    )
    default boolean kebbitProgressiveHunting() {
        return false;
    }

    /**
     * Toggles the on-screen overlay showing plugin status or stats.
     *
     * @return True to show the overlay, false to hide it.
     */
    @ConfigItem(
            position = 2,
            keyName = "kebbitShowOverlay",
            name = "Kebbits: Show Overlay",
            description = "Displays the overlay",
            section = "kebbitsSection"
    )
    default boolean kebbitShowOverlay() {
        return true;
    }

    /**
     * Minimum delay in milliseconds after a successful catch before the next action.
     * Helps mimic human behavior.
     *
     * @return Minimum sleep time after retrieving the falcon.
     */
    @ConfigItem(
            position = 4,
            keyName = "kebbitMinSleepAfterCatch",
            name = "Kebbits: Min. Sleep After Catch",
            description = "Min sleep after catch",
            section = "kebbitsSection"
    )
    default int kebbitMinSleepAfterCatch() {
        return 7500;
    }

    /**
     * Maximum delay in milliseconds after a successful catch before the next action.
     *
     * @return Maximum sleep time after retrieving the falcon.
     */
    @ConfigItem(
            position = 5,
            keyName = "kebbitMaxSleepAfterCatch",
            name = "Kebbits: Max. Sleep After Catch",
            description = "Max sleep after catch",
            section = "kebbitsSection"
    )
    default int kebbitMaxSleepAfterCatch() {
        return 8400;
    }

    /**
     * Minimum delay in milliseconds after sending the falcon to catch a Kebbit.
     * Prevents rapid retries and simulates natural delay.
     *
     * @return Minimum sleep time after sending the falcon.
     */
    @ConfigItem(
            position = 6,
            keyName = "MinSleepAfterHuntingKebbit",
            name = "Min. Sleep After sending Kyr - Recommended minimum 4000ms",
            description = "Min sleep before Send Kyr to fly",
            section = "kebbitsSection"
    )
    default int MinSleepAfterHuntingKebbit() {
        return 4000;
    }

    /**
     * Maximum delay in milliseconds after sending the falcon to catch a Kebbit.
     *
     * @return Maximum sleep time after sending the falcon.
     */
    @ConfigItem(
            position = 7,
            keyName = "MaxSleepAfterHuntingKebbit",
            name = "Max. Sleep After sending Kyr",
            description = "Max sleep before Send Kyr to fly",
            section = "kebbitsSection"
    )
    default int MaxSleepAfterHuntingKebbit() {
        return 5400;
    }

    @ConfigItem(
            position = 8,
            keyName = "kebbitBuryBones",
            name = "Kebbits: Bury Bones",
            description = "If enabled, bones will be buried automatically instead of dropped.",
            section = "kebbitsSection"

    )
    default boolean kebbitBuryBones()
    {
        return false; // Standard: false, also droppen.
    }
}
