package com.lockheedmartin.aem.core.publication.models;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.TreeMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LockheedPublicationItem
{
    @Expose @SerializedName(value = "ID")
    String id;

    @Expose @SerializedName(value = "Title")
    String title;

    @Expose @SerializedName(value = "Description")
    String description;

    @Expose @SerializedName(value = "Place of Publication")
    String placeOfPublication;

    @Expose @SerializedName(value = "Date")
    String date;

    @Expose @SerializedName(value = "URL")
    String url;

    @Expose @SerializedName(value = "SourceURL")
    String sourceURL;    
    
    @Expose @SerializedName(value = "Thumbnail Image")
    String thumbnailUrl;

    @Expose @SerializedName(value = "Tags")
    TreeMap<String, String> tags;

    @Expose @SerializedName(value = "Authors")
    ArrayList<String> authors;

    @Expose @SerializedName(value = "Domain")
    TreeMap<String, String> domain;
    
    @Expose @SerializedName(value = "Country")
    TreeMap<String, String> country;    

    final String datePattern = "EEE, d MMM yyyy HH:mm:ss z";
    
    private final Logger logger = LoggerFactory.getLogger(getClass());


    /* public LockheedPublicationItem(String title, Calendar dateTime, String url, String thumbnailUrl, TreeMap<String, String> tags, TreeMap<String, String> domain, TreeMap<String, String> country, String sourceURL)
    {
        this.title = title;

        if(dateTime != null)
        {
            SimpleDateFormat simpleDateFormat = new SimpleDateFormat(datePattern);
            this.date = simpleDateFormat.format(dateTime.getTime());
        }
        else
        {
            this.date = "";
        }

        this.url = url;
        this.thumbnailUrl = thumbnailUrl;
        this.tags = tags;

        this.domain = domain;
        
        this.country = country;
        
        this.sourceURL = sourceURL;
    } */

    public LockheedPublicationItem(String date, String title, String url, String placeOfPublication, String description, TreeMap<String, String> tags, ArrayList<String> authors)
    {
        this.title = title;

        this.date = date;
        
        this.url = url;

        this.description = description;

        this.authors = authors;

        this.placeOfPublication = placeOfPublication;

        this.tags = tags;

    }
    
    public Calendar getCalendarDate() throws ParseException
    {
        if(this.date.equals(""))
        {
            return null;
        }

        SimpleDateFormat simpleDateFormat = new SimpleDateFormat(datePattern);

        Calendar cd = Calendar.getInstance();
        cd.setTime(simpleDateFormat.parse(this.date));
        return cd;
    }
    public String getTitle() throws ParseException
    {
        return this.title;
    }    
}
