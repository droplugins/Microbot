package net.runelite.client.plugins.microbot.drohunter;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum DroHunterActivity {
    BOX_TRAPS("Box traps (chinchompas)"),
    BIRDS("Bird snares"),
    KEBBITS("Falconry kebbits"),
    DEADFALLS("Deadfall traps"),
    PITFALLS("Pitfall hunting"),
    SALAMANDERS("Net traps / salamanders");

    private final String displayName;

    @Override
    public String toString() {
        return displayName;
    }
}
