package org.netbeans.modules.linetools.actions;

import java.util.Comparator;

/**
 *
 * @author meie03
 */
public class CustomNaturalOrderComparator implements Comparator<String>
{
    private boolean caseSensetive;

    public CustomNaturalOrderComparator()
    {
        this.caseSensetive = true;
    }

    public CustomNaturalOrderComparator(boolean caseSensetive)
    {
        this.caseSensetive = caseSensetive;
    }

    @Override
    public int compare(String o1, String o2)
    {
        Comparator<String> naturalOrderComparator = new NaturalOrderComparator();

        if (this.caseSensetive)
        {
            return naturalOrderComparator.compare(o1, o2);
        }
        else
        {
            return naturalOrderComparator.compare(o1.toLowerCase(), o2.toLowerCase());
        }
    }
}
