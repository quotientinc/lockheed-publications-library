package com.lockheedmartin.aem.core.publication.comparators;

import java.util.Comparator;

import com.lockheedmartin.aem.core.publication.models.LockheedPublicationItem;

public class SortPublicationItemByDate implements Comparator<LockheedPublicationItem>
{

    @Override
    public int compare(LockheedPublicationItem i1, LockheedPublicationItem i2)
    {
        int comparison = 0;

        try
        {
            if(i1.getCalendarDate() == null && i2.getCalendarDate() == null)
            {
                return 0;
            }
            else if(i1.getCalendarDate() == null && i2.getCalendarDate() != null)
            {
                return -1;
            }
            else if(i1.getCalendarDate() != null && i2.getCalendarDate() == null)
            {
                return 1;
            }

            comparison = i1.getCalendarDate().compareTo(i2.getCalendarDate());
        }
        catch(Exception e)
        {
            //e.printStackTrace();
        }

        return comparison;
    }
}
