package org.netbeans.modules.linetools.actions;

import java.text.Collator;
import java.util.Comparator;
import java.util.Locale;

/**
 *
 * @author Alex Meier
 */
public class CustomComparator implements Comparator<String>
{
    private boolean caseSensetive;
    private Locale locale;

    public CustomComparator()
    {
        this.caseSensetive = true;
        this.locale = Locale.ENGLISH;
    }

    public CustomComparator(boolean caseSensetive)
    {
        this.caseSensetive = caseSensetive;
        this.locale = Locale.ENGLISH;
    }

    public CustomComparator(Locale locale)
    {
        this.caseSensetive = true;
        this.locale = locale;
    }

    public CustomComparator(Locale locale, boolean caseSensetive)
    {
        this.caseSensetive = caseSensetive;
        this.locale = locale;
    }

    @Override
    public int compare(String o1, String o2)
    {
        Collator collator = Collator.getInstance(Locale.ENGLISH);

        if (this.caseSensetive)
        {
            return collator.compare(o1, o2);
        }
        else
        {
            return collator.compare(o1.toLowerCase(), o2.toLowerCase());
        }
    }
}
