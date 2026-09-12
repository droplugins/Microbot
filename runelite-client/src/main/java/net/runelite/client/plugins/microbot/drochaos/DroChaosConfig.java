package net.runelite.client.plugins.microbot.drochaos;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigInformation;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;

@ConfigGroup(DroChaosConfig.GROUP)
@ConfigInformation("Uses Dragon bones at the Wilderness Chaos Altar, suicides by repeatedly taking Wines of zamorak, "
        + "then returns to Lumbridge for another inventory. Keep Dragon bones and charged Burning amulets in the bank.")
public interface DroChaosConfig extends Config
{
    String GROUP = "DroChaos";

    @ConfigItem(
            keyName = "fastOffering",
            name = "Fast bone offering",
            description = "Enabled: rapidly offer bones in randomized last-to-first order. Disabled: use one bone and let automatic offering continue.",
            position = 0
    )
    default boolean fastOffering()
    {
        return true;
    }

    @Range(min = 1, max = 64)
    @ConfigItem(
            keyName = "threatRadius",
            name = "Player safety radius",
            description = "Log out when a threatening player is visible within this many tiles.",
            position = 1
    )
    default int threatRadius()
    {
        return 32;
    }

    @Range(min = 0, max = 20)
    @ConfigItem(
            keyName = "upperCombatBuffer",
            name = "Upper combat safety buffer",
            description = "Also treat players this many combat levels above the current maximum attack range as threats.",
            position = 2
    )
    default int upperCombatBuffer()
    {
        return 5;
    }
}
