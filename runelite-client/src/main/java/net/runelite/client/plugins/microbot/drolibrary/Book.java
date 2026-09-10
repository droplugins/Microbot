package net.runelite.client.plugins.microbot.drolibrary;

import lombok.Getter;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.game.ItemManager;
import net.runelite.client.util.AsyncBufferedImage;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

enum Book
{
    RADAS_CENSUS(
            ItemID.ARCEUUS_LIBRARY_CENSUSRADA,
            "Rada's Census",
            "Census of King Rada III, by Matthias Vorseth."
    ),

    RICKTORS_DIARY_7(
            ItemID.ARCEUUS_LIBRARY_DIARYRICKTOR7,
            "Ricktor's Diary 7",
            "Diary of Steklan Ricktor, volume 7."
    ),

    EATHRAM_RADA_EXTRACT(
            ItemID.ARCEUUS_LIBRARY_EATHRAMRADA,
            "Eathram & Rada extract",
            "An extract from Eathram & Rada, by Anonymous."
    ),

    KILLING_OF_A_KING(
            ItemID.ARCEUUS_LIBRARY_KILLINGKING,
            "Killing of a King",
            "Killing of a King, by Griselle."
    ),

    HOSIDIUS_LETTER(
            ItemID.ARCEUUS_LIBRARY_HOSIDIUSLETTER,
            "Hosidius Letter",
            "A letter from Lord Hosidius to the Council of Elders."
    ),

    WINTERTODT_PARABLE(
            ItemID.ARCEUUS_LIBRARY_WINTERTODTPARABLE,
            "Wintertodt Parable",
            "The Parable of the Wintertodt, by Anonymous."
    ),

    TWILL_ACCORD(
            ItemID.ARCEUUS_LIBRARY_TWILLACCORD,
            "Twill Accord",
            "The Royal Accord of Twill."
    ),

    BYRNES_CORONATION_SPEECH(
            ItemID.ARCEUUS_LIBRARY_BYRNESPEECH,
            "Byrnes Coronation Speech",
            "Speech of King Byrne I, on the occasion of his coronation."
    ),

    IDEOLOGY_OF_DARKNESS(
            ItemID.ARCEUUS_LIBRARY_IDEOLOGYDARKNESS,
            "The Ideology of Darkness",
            "The Ideology of Darkness, by Philophaire."
    ),

    RADAS_JOURNEY(
            ItemID.ARCEUUS_LIBRARY_RADAJOURNEY,
            "Rada's Journey",
            "The Journey of Rada, by Griselle."
    ),

    TRANSVERGENCE_THEORY(
            ItemID.ARCEUUS_LIBRARY_TRANSVERGENCETHEORY,
            "Transvergence Theory",
            "The Theory of Transvergence, by Amon Ducot."
    ),

    TRISTESSAS_TRAGEDY(
            ItemID.ARCEUUS_LIBRARY_TRISTESSATRAGEDY,
            "Tristessa's Tragedy",
            "The Tragedy of Tristessa."
    ),

    TREACHERY_OF_ROYALTY(
            ItemID.ARCEUUS_LIBRARY_TREACHERYROYALTY,
            "The Treachery of Royalty",
            "The Treachery of Royalty, by Professor Answith."
    ),

    TRANSPORTATION_INCANTATIONS(
            ItemID.ARCEUUS_LIBRARY_TELEPORTUNLOCK,
            "Transportation Incantations",
            "Transportation Incantations, by Amon Ducot."
    ),

    SOUL_JOURNEY(
            ItemID.ARCEUUS_LIBRARY_SOULBEARERBOOK,
            "Soul Journey",
            "The Journey of Souls, by Aretha."
    ),

    VARLAMORE_ENVOY(
            ItemID.HOSIDIUSQUEST_BOOK,
            "Varlamore Envoy",
            "The Envoy to Varlamore, by Deryk Paulson."
    );

    private static final Map<Integer, Book> BY_ID = buildById();
    private static final Map<String, Book> BY_NAME = buildByName();

    private static Map<Integer, Book> buildById()
    {
        HashMap<Integer, Book> byId = new HashMap<>();

        for (Book book : values())
        {
            byId.put(book.item, book);
        }

        return Collections.unmodifiableMap(byId);
    }

    private static Map<String, Book> buildByName()
    {
        HashMap<String, Book> byName = new HashMap<>();

        for (Book book : values())
        {
            byName.put(book.name.toLowerCase(), book);
            byName.put(book.shortName.toLowerCase(), book);
        }

        return Collections.unmodifiableMap(byName);
    }

    static Book byId(int id)
    {
        return BY_ID.get(id);
    }

    static Book byName(String name)
    {
        if (name == null)
        {
            return null;
        }

        return BY_NAME.get(name.trim().toLowerCase());
    }

    @Getter
    private final int item;

    @Getter
    private final String name;

    @Getter
    private final String shortName;

    @Getter
    private AsyncBufferedImage icon;

    Book(int id, String shortName, String name)
    {
        this.item = id;
        this.shortName = shortName;
        this.name = name;
    }

    static void fillImages(ItemManager itemManager)
    {
        for (Book book : values())
        {
            book.icon = itemManager.getImage(book.item);
        }
    }
}