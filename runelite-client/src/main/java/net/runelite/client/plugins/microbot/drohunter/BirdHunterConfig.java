package net.runelite.client.plugins.microbot.drohunter;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("birdhunter")
public interface BirdHunterConfig extends Config {

    @ConfigItem(
            keyName = "birdBuryBones",
            name = "Birds: Bury Bones",
            description = "Select whether to bury bones during hunting",
            position = 1,
            section = "birdsSection"
    )
    default boolean birdBuryBones() {
        return true;
    }

    @ConfigItem(
            keyName = "huntingRadiusValue",
            name = "Birds: Hunting radius",
            description = "The radius in which the player will set traps and hunt birds. Indicated by yellow borders. " +
                    "The center of the area is where the plugin is started",
            position = 2,
            section = "birdsSection"
    )
    default int huntingRadiusValue() {
        return 2;
    }

}
