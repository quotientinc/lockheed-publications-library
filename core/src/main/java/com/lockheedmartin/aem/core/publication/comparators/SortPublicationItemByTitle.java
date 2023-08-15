package com.lockheedmartin.aem.core.publication.comparators;

import java.util.Comparator;

import com.lockheedmartin.aem.core.publication.models.LockheedPublicationItem;

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
