package net.runelite.client.plugins.microbot.drolibrary;

import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

class BookPanel extends JPanel
{
    private final JLabel location = new JLabel();

    BookPanel(Book book)
    {
        setBorder(new EmptyBorder(3, 3, 3, 3));
        setBackground(ColorScheme.DARK_GRAY_COLOR);

        GroupLayout layout = new GroupLayout(this);
        setLayout(layout);

        JLabel image = new JLabel();

        if (book.getIcon() != null)
        {
            book.getIcon().addTo(image);
        }

        JLabel name = new JLabel(book.getShortName());
        location.setFont(FontManager.getRunescapeSmallFont());

        layout.setVerticalGroup(
                layout.createParallelGroup()
                        .addComponent(image)
                        .addGroup(
                                layout.createSequentialGroup()
                                        .addComponent(name)
                                        .addComponent(location)
                        )
        );

        layout.setHorizontalGroup(
                layout.createSequentialGroup()
                        .addComponent(image)
                        .addGap(8)
                        .addGroup(
                                layout.createParallelGroup()
                                        .addComponent(name)
                                        .addComponent(location)
                        )
        );

        setComponentZOrder(image, getComponentCount() - 1);
    }

    void setLocation(String location)
    {
        this.location.setText(location);
    }

    void setIsTarget(boolean target)
    {
        location.setForeground(target ? Color.GREEN : Color.ORANGE);
    }

    void setIsHeld(boolean held)
    {
        if (held)
        {
            location.setForeground(Color.WHITE);
        }
    }
}