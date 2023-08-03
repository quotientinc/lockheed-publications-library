package com.lockheedmartin.aem.core.product.comparators;

import com.lockheedmartin.aem.core.product.models.LockheedPublicationItem;

import java.util.Comparator;

public class SortPublicationItemByTitle implements Comparator<LockheedPublicationItem>
{

    @Override
    public int compare(LockheedPublicationItem i1, LockheedPublicationItem i2)
    {
        int comparison = 0;

        try
        {
            comparison = i1.getTitle().toLowerCase().compareTo(i2.getTitle().toLowerCase());
        }
        catch(Exception e)
        {
            //e.printStackTrace();
        }

        return comparison;
    }
}
