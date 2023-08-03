package com.lockheedmartin.aem.core.product.comparators;

import com.lockheedmartin.aem.core.product.models.LockheedPublicationItem;

import java.util.Comparator;

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
