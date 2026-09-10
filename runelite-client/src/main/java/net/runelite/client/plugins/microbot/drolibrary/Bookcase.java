package net.runelite.client.plugins.microbot.drolibrary;

import lombok.Getter;
import net.runelite.api.coords.WorldPoint;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

class Bookcase
{
    Bookcase(WorldPoint location)
    {
        this.location = location;
        this.index = new ArrayList<>();
    }

    @Getter
    private final WorldPoint location;

    @Getter
    private final List<Integer> index;

    @Getter
    private boolean isBookSet;

    @Getter
    private Book book;

    @Getter
    private final Set<Book> possibleBooks = new HashSet<>();

    /*
     * This is intentionally separate from isBookSet().
     *
     * isBookSet() means the solver has an actual observation.
     * searched means the automation has physically searched this shelf.
     */
    @Getter
    private boolean searched;

    void clearBook()
    {
        book = null;
        isBookSet = false;
    }

    void setBook(Book book)
    {
        this.book = book;
        this.isBookSet = true;
    }

    void markSearched()
    {
        this.searched = true;
    }

    void clearSearched()
    {
        this.searched = false;
    }

    void resetDiscovery()
    {
        clearBook();
        possibleBooks.clear();
        searched = false;
    }

    String getLocationString()
    {
        StringBuilder b = new StringBuilder();

        boolean north = location.getY() > 3815;
        boolean west = location.getX() < 1625;

        if (location.getPlane() == 0)
        {
            north = location.getY() > 3813;
            west = location.getX() < 1627;
        }

        if (north && west)
        {
            b.append("Northwest");
        }
        else if (north)
        {
            b.append("Northeast");
        }
        else if (west)
        {
            b.append("Southwest");
        }
        else
        {
            b.append("Center");
        }

        b.append(' ');

        switch (location.getPlane())
        {
            case 0:
                b.append("ground floor");
                break;

            case 1:
                b.append("middle floor");
                break;

            case 2:
                b.append("top floor");
                break;

            default:
                b.append("floor ").append(location.getPlane());
                break;
        }

        return b.toString();
    }
}