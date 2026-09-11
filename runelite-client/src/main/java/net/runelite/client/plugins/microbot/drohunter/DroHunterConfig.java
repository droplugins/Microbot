package net.runelite.client.plugins.microbot.drohunter;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigInformation;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup("DroHunter")
@ConfigInformation("Select one Hunter activity, adjust its settings, then enable DroHunter. "
        + "Box traps are the former Micro Hunter/chinchompa mode; the other modes retain their specialized logic.")
public interface DroHunterConfig extends Config, BirdHunterConfig, HunterKebbitsConfig,
        DeadFallTrapHunterConfig, PitfallHunterConfig, SalamanderConfig {
    @ConfigSection(
            name = "General",
            description = "Choose which Hunter activity DroHunter should run.",
            position = 0,
            closedByDefault = false
    )
    String GENERAL_SECTION = "generalSection";

    @ConfigSection(
            name = "Box traps (chinchompas)",
            description = "Settings for the former Micro Hunter box-trap script.",
            position = 1,
            closedByDefault = true
    )
    String BOX_TRAPS_SECTION = "boxTrapsSection";

    @ConfigSection(
            name = "Bird snares",
            description = "Settings for hunting birds with bird snares.",
            position = 2,
            closedByDefault = true
    )
    String BIRDS_SECTION = "birdsSection";

    @ConfigSection(
            name = "Falconry kebbits",
            description = "Settings for hunting kebbits with a falcon.",
            position = 3,
            closedByDefault = true
    )
    String KEBBITS_SECTION = "kebbitsSection";

    @ConfigSection(
            name = "Deadfall traps",
            description = "Creature, banking, loot, overlay, and timing settings for deadfalls.",
            position = 4,
            closedByDefault = true
    )
    String DEADFALLS_SECTION = "deadfallsSection";

    @ConfigSection(
            name = "Pitfall hunting",
            description = "Settings for the retained Micro Pitfall hunting engine.",
            position = 5,
            closedByDefault = true
    )
    String PITFALLS_SECTION = "pitfallsSection";

    @ConfigSection(
            name = "Net traps / salamanders",
            description = "Salamander selection, supplies, overlay, and timing settings for net traps.",
            position = 6,
            closedByDefault = true
    )
    String SALAMANDERS_SECTION = "salamandersSection";

    @ConfigItem(
            position = 0,
            keyName = "activity",
            name = "Activity",
            description = "Select the Hunter activity DroHunter should run",
            section = GENERAL_SECTION
    )
    default DroHunterActivity activity() {
        return DroHunterActivity.BOX_TRAPS;
    }

    @ConfigItem(
            position = 1,
            keyName = "chinMinSleepAfterCatch",
            name = "Box traps: Min. Sleep After Catch",
            description = "Minimum sleep after catching with a box trap",
            section = BOX_TRAPS_SECTION
    )
    default int chinMinSleepAfterCatch() {
        return 8300;
    }
    @ConfigItem(
            position = 2,
            keyName = "chinMaxSleepAfterCatch",
            name = "Box traps: Max. Sleep After Catch",
            description = "Maximum sleep after catching with a box trap",
            section = BOX_TRAPS_SECTION
    )
    default int chinMaxSleepAfterCatch() {
        return 8400;
    }
    @ConfigItem(
            position = 3,
            keyName = "chinMinSleepAfterLay",
            name = "Box traps: Min. Sleep After Lay",
            description = "Minimum sleep after laying a box trap",
            section = BOX_TRAPS_SECTION
    )
    default int chinMinSleepAfterLay() {
        return 5500;
    }
    @ConfigItem(
            position = 4,
            keyName = "chinMaxSleepAfterLay",
            name = "Box traps: Max. Sleep After Lay",
            description = "Maximum sleep after laying a box trap",
            section = BOX_TRAPS_SECTION
    )
    default int chinMaxSleepAfterLay() {
        return 5700;
    }
}
