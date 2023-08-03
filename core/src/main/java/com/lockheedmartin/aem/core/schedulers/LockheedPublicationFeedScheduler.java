/*
 *  Copyright 2015 Adobe Systems Incorporated
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.lockheedmartin.aem.core.schedulers;

import com.day.cq.commons.Externalizer;
import com.day.cq.replication.ReplicationActionType;
import com.day.cq.replication.ReplicationException;
import com.day.cq.replication.ReplicationStatus;
import com.day.cq.replication.Replicator;
import com.day.cq.tagging.Tag;
import com.day.cq.tagging.TagManager;
import com.day.cq.wcm.api.Page;
import com.day.cq.wcm.api.PageManager;
import com.google.gson.*;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.DefaultProxyRoutePlanner;
import org.apache.lucene.queries.function.valuesource.MultiFunction;
import org.apache.sling.api.resource.*;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.commons.scheduler.ScheduleOptions;
import org.apache.sling.commons.scheduler.Scheduler;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.jcr.*;
import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.sling.commons.json.*;

import com.lockheedmartin.aem.core.product.models.LockheedPublicationItem;
import com.lockheedmartin.aem.core.product.comparators.SortPublicationItemByDate;
import com.lockheedmartin.aem.core.product.comparators.SortPublicationItemByTitle;


/**
 * A simple demo for cron-job like tasks that get executed regularly.
 * It also demonstrates how property values can be set. Users can
 * set the property values in /system/console/configMgr
 */
@Designate(ocd=LockheedPublicationFeedScheduler.Config.class)
@Component(service=Runnable.class)
public class LockheedPublicationFeedScheduler implements Runnable {

    @ObjectClassDefinition(name="Lockheed Publication Feed Scheduler Configuration",
                           description = "Simple demo for cron-job like task with properties")
    public static @interface Config {

        @AttributeDefinition(name = "Cron-job expression")
        String scheduler_expression() default "0 0 0 * * ?";

        @AttributeDefinition(name = "Mapping File Path")
        String mapping_file_path() default "";

        @AttributeDefinition(name = "JSON File Path")
        String json_path() default "/content";

        @AttributeDefinition(name = "Enable Service")
        boolean is_enabled() default false;

        @AttributeDefinition(name = "Date String", description = "Format YYYYMMDD")
        String get_date_string() default "20190101";

        @AttributeDefinition(name = "Local Root Path", description = "Path to search on for Lockheed-Martin Publication in AEM")
        String[] get_root_path() default {"/content/lockheed-martin/en-us/products"};        
    }

    @Reference
    private ResourceResolverFactory resolverFactory;

    @Reference
    private Scheduler scheduler;

    @Reference
    private Replicator replicator;

    private javax.jcr.Session session;
    private ResourceResolver resourceResolver;
    private Config config;

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private static final String JOB_NAME = "Lockheed-Martin Publication Feed Job";
    
    private TreeMap<String, List<String>> tagMap = new TreeMap<String, List<String>>();
    private TreeMap<String, String> tagTitleMap = new TreeMap<String, String>();
    
    @Override
    public void run() {}

    @Activate
    protected void activate(final Config config)
    {
        this.config = config;

        try
        {
            try
            {
                this.scheduler.unschedule(JOB_NAME);
                logger.info("Removed Job: " + JOB_NAME);
            }
            catch(Exception e)
            {
                logger.info("Error removing Job:" + JOB_NAME + ":" + e.toString());
            }

            final Runnable job = new Runnable()
            {
                public void run() {
                    try
                    {
                        resourceResolver = resolverFactory.getServiceResourceResolver(null);
                        session = resourceResolver.adaptTo(Session.class);

                        Map<String, Object> param = new HashMap<String, Object>();
                        param.put(ResourceResolverFactory.SUBSERVICE, "LockheedProductFeedScheduler");

                        if(config.is_enabled())
                        {
                            logger.info("------------------------------------------------------------------ ");
                            logger.info("------------------------------------------------------------------ ");
                            logger.info("----------------------- ENABLED AND RUNNING!---------------------- ");
                            logger.info("------------------------------------------------------------------");
                            logger.info("------------------------------------------------------------------ ");
                            
                            getMapping();
                            //writeProductfeedJSONToRepo();
                            writePublicationFeedJSONToRepo();
                        }
                    }
                    catch (Exception e)
                    {
                        logger.error("Run error: {}", e.toString());
                    }
                    finally
                    {
                        session.logout();
                        if(resourceResolver != null)
                        {
                            resourceResolver.close();
                        }
                    }
                }
            };

            ScheduleOptions scheduler_options = scheduler.EXPR(config.scheduler_expression());
            scheduler_options.name(JOB_NAME);
            this.scheduler.schedule(job, scheduler_options);
        }
        catch(Exception e)
        {
            //e.printStackTrace();
            logger.error(e.getMessage());
        }
    }

    private void writePublicationFeedJSONToRepo() throws Exception {
        String jsonString = getProductItemsAsJSON();

        Resource metadataOptionJson = ResourceUtil.getOrCreateResource(
                resourceResolver,
                this.config.json_path() + "/publications.json",
                Collections.singletonMap("jcr:primaryType",(Object) "nt:file"),
                null, false);

        Resource metadataOptionJsonJcrContent = ResourceUtil.getOrCreateResource(
                resourceResolver,
                metadataOptionJson.getPath() + "/jcr:content",
                Collections.singletonMap("jcr:primaryType", (Object) "nt:resource"),
                null, false);

        final ModifiableValueMap metadataOptionJsonProperties = metadataOptionJsonJcrContent.adaptTo(ModifiableValueMap.class);

        if (metadataOptionJsonProperties.get("jcr:data") != null)
        {
            metadataOptionJsonProperties.remove("jcr:data");
        }

        metadataOptionJsonProperties.put("jcr:mimeType", "application/json");

        metadataOptionJsonProperties.put("jcr:encoding", "utf-8");
        final ByteArrayInputStream bais = new ByteArrayInputStream(jsonString.getBytes(StandardCharsets.UTF_8));
        metadataOptionJsonProperties.put("jcr:data", bais);

        resourceResolver.commit();
        /* replicator.replicate(session, ReplicationActionType.ACTIVATE, metadataOptionJson.getPath()); */
        logger.info("----------------------- Publication Written to Repo! ---------------------- ");
    }

    private String getProductItemsAsJSON() throws Exception {
        List<LockheedPublicationItem> items = new ArrayList<>();

        items.addAll(getAEMProductfeedPages());


        //items.sort(new SortPublicationItemByTitle());

        Collections.reverse(items);

        Gson gson = new GsonBuilder()
                        .excludeFieldsWithoutExposeAnnotation()
                        .create();

        return gson.toJson(items);
    }



    private List<LockheedPublicationItem> getAEMProductfeedPages()
    {
        List<LockheedPublicationItem> items = new ArrayList<>();

        List<Page> pageQueue = new ArrayList<>();

        PageManager pageManager;
        pageManager = resourceResolver.adaptTo(PageManager.class);

        Externalizer ext = resourceResolver.adaptTo(Externalizer.class);
        
        for(String rootPath : config.get_root_path()) {

            Page rootPage = pageManager.getPage(rootPath);

            if(rootPage != null)
            {
                pageQueue.add(rootPage);

                while(pageQueue.size() > 0)
                {
                    Page p = pageQueue.remove(0);

                    Iterator<Page> children = p.listChildren();

                    while(children.hasNext())
                    {
                        pageQueue.add(children.next());
                    }

                    Node pNode = p.adaptTo(Node.class);

                    try
                    {
                        if(pNode.hasNode("jcr:content"))
                        {
                            Node content = pNode.getNode("jcr:content");

                            boolean isPublished = true;

                            ReplicationStatus publishedStatus = null;
                            publishedStatus = p.adaptTo(ReplicationStatus.class);
                            //isPublished = publishedStatus.isActivated();

                            boolean isProduct = false;

                            if(content.hasProperty("contentTypeTag"))
                            {
                                if(content.getProperty("contentTypeTag").isMultiple())
                                {
                                    List<Value> contentTypeTagValues = Arrays.asList(content.getProperty("contentTypeTag").getValues());

                                    for(int i = 0; i < contentTypeTagValues.size(); i++)
                                    {
                                        if(contentTypeTagValues.get(i).getString().equals("content-type:products"))
                                        {
                                            isProduct = true;
                                        }                                   
                                    }
                                }
                                else
                                {
                                    if(content.getProperty("contentTypeTag").getString().equals("content-type:products"))
                                    {
                                        isProduct = true;
                                    }
                                }
                            }                       

                            //boolean hasExternalPath = false;

                            //boolean isExternal = isArticle && hasExternalPath;
                            //logger.error("Checking "+p.getPath());
                            //if(isPublished && (isProduct))
                            if(isPublished)
                            {   //logger.error("Parsing "+p.getPath());
                                /** get the page title **/

                                String title = p.getTitle();

                                /** get page url **/
                                String url = p.getPath() + ".html";
                                String sourceURL = url;

                                if(content.hasProperty("externalNewsArticlePath"))
                                {
                                    if(content.getProperty("externalNewsArticlePath").isMultiple())
                                    {
                                        List<Value> externalPaths = Arrays.asList(content.getProperty("externalNewsArticlePath").getValues());

                                        for(int i = 0; i < externalPaths.size(); i++)
                                        {
                                            if(!externalPaths.get(i).getString().isEmpty() && !externalPaths.get(i).getString().equals(","))
                                            {
                                                url = externalPaths.get(i).getString();
                                                break;
                                            }
                                        }
                                    }
                                    else
                                    {
                                        if(!content.getProperty("externalNewsArticlePath").getString().isEmpty() &&  !content.getProperty("externalNewsArticlePath").getString().equals(",")) {
                                            url = content.getProperty("externalNewsArticlePath").getString();
                                        }
                                    }
                                }                            

                                Calendar dateTime = null;

                                /** get the lastModified date **/
                                if(content.hasProperty("dateTime")) {
                                    dateTime = content.getProperty("dateTime").getDate();
                                }
                                else if(content.hasProperty("cq:lastModified"))
                                {
                                    dateTime = content.getProperty("cq:lastModified").getDate();
                                }
                                else if(content.hasProperty("jcr:created"))
                                {
                                    dateTime = content.getProperty("jcr:created").getDate();
                                }
                                else
                                {
                                    dateTime = null;
                                }

                                /** Get thumbnail url for the page **/
                                String thumbnailUrl = "";
                                if(content.hasNode("thumbnailImage"))
                                {
                                    Node thumbnail = content.getNode("thumbnailImage");

                                    if(thumbnail.hasProperty("fileReference"))
                                    {
                                        thumbnailUrl = thumbnail.getProperty("fileReference").getString();
                                    }
                                }

                                /** Get page tags **/
                                
                                TreeMap<String, String> tags = getProductPageTags(content);
                                TreeMap<String, String> domain = getDomain(content);
                                TreeMap<String, String> country = getCountry(content);

                                //items.add(new LockheedPublicationItem(title, dateTime, url, thumbnailUrl, tags, domain, country, sourceURL));
                                items.add(new LockheedPublicationItem(title, url));
                            }
                        }
                    }
                    catch(Exception e)
                    {
                        logger.error(e.getMessage());
                        e.printStackTrace();
                    }
                }
            }
        }

        return items;
    }



    private Document getXmlDocumentFromString(String xmlString)
    {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = null;

        try
        {
            builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new InputSource(new StringReader(xmlString)));

            return doc;
        }
        catch (Exception e)
        {
            //logger.error(e.getMessage());
            //e.printStackTrace();
        }

        return null;
    }

    private void getMapping() {
        String mapFilePath = config.mapping_file_path()+"/jcr:content";
        //logger.error("Get Mapping for "+mapFilePath);
        try {
            if(session.nodeExists(mapFilePath)) {
                String mapInput = session.getNode(mapFilePath).getProperty("jcr:data").getString();
                JsonParser parser = new JsonParser();
                
                if(parser.parse(mapInput).isJsonObject()) {
                    JsonObject mapData = parser.parse(mapInput).getAsJsonObject();
                    
                    if(mapData.has("Tags")) {
                        JsonObject tagData = mapData.getAsJsonObject("Tags");
                        Object[] tagDataKeys = tagData.keySet().toArray();
                        
                        for(int i=0; i<tagDataKeys.length; i++) {
                            String tagKey = tagDataKeys[i].toString();
                            if(tagData.has(tagKey)) {
                                JsonArray tagValue = tagData.getAsJsonArray(tagKey);

                                List<String> tagValueList = new ArrayList<String>();
                                for(int j=0; j<tagValue.size(); j++) {
                                    JsonElement tagValueElem = tagValue.get(j);
                                    tagValueList.add(tagValueElem.getAsString());
                                }
                                tagMap.put(tagKey, tagValueList);
                            }
                        }
                    }
                    
                    if(mapData.has("TagTitle")) {
                        JsonObject tagTitleData = mapData.getAsJsonObject("TagTitle");
                        Object[] tagTitleDataKeys = tagTitleData.keySet().toArray();
                        
                        for(int i=0; i<tagTitleDataKeys.length; i++) {
                            String tagKey = tagTitleDataKeys[i].toString();
 
                            if(tagTitleData.has(tagKey)) {
                                JsonPrimitive tagValue = tagTitleData.getAsJsonPrimitive(tagKey);
                                tagTitleMap.put(tagKey, tagValue.getAsString());
                            }
                        }                        
                    }

                }
            }
        } catch(Exception e) {
            logger.error(e.getMessage());
            e.printStackTrace();            
        }
        
    }

    
    private TreeMap<String, String> getProductPageTags(Node content)
    {
        //List<String> tags = new ArrayList<>();
        TreeMap<String, String> tags = new TreeMap<String, String>();

        try
        {
            TagManager tm = resourceResolver.adaptTo(TagManager.class);
            List<Value> tagValues = new ArrayList<>();

            if(content.hasProperty("capabilitiesTag"))
            {
                if(content.getProperty("capabilitiesTag").isMultiple())
                {
                    tagValues.addAll(Arrays.asList(content.getProperty("capabilitiesTag").getValues()));
                }
                else
                {
                    tagValues.add(content.getProperty("capabilitiesTag").getValue());
                }
            }

            if(content.hasProperty("programOrFunctionTag"))
            {
                if(content.getProperty("programOrFunctionTag").isMultiple())
                {
                    tagValues.addAll(Arrays.asList(content.getProperty("programOrFunctionTag").getValues()));
                }
                else
                {
                    tagValues.add(content.getProperty("programOrFunctionTag").getValue());
                }
            }

            if(!config.mapping_file_path().equals("")) {
                //logger.error("Using Tag Mapping");
                for(Value v: tagValues)
                {
                    //logger.error("Finding "+v.getString());
                    if(tagMap.get(v.getString())!=null) {
                        //logger.error("Found "+v.getString());
                        List<String> tagMapEntries = tagMap.get(v.getString());
                        for(String tagMapEntry: tagMapEntries) {
                            if(tagTitleMap.get(tagMapEntry)!=null) {
                                //logger.error("Adding "+tagMapEntry+":"+tagTitleMap.get(tagMapEntry));
                                tags.put(tagMapEntry, tagTitleMap.get(tagMapEntry));
                            }
                        }
                    }
                }
            } else {
                for(Value v: tagValues)
                {
                    String tagId = v.getString();
                    Tag t = tm.resolve(tagId);
                    tags.put(t.getName(), t.getTitle());
                }                
            }

            //Collections.sort(tags);
        }
        catch(Exception e)
        {
            //e.printStackTrace();
            logger.error(e.getMessage());
        }

        return tags;
    }

    private TreeMap<String, String> getDomain(Node content)
    {
        //List<String> tags = new ArrayList<>();
        TreeMap<String, String> domain = new TreeMap<String, String>();

        try
        {
            TagManager tm = resourceResolver.adaptTo(TagManager.class);
            List<Value> tagValues = new ArrayList<>();

            if(content.hasProperty("domainTag"))
            {
                if(content.getProperty("domainTag").isMultiple())
                {
                    tagValues.addAll(Arrays.asList(content.getProperty("domainTag").getValues()));
                }
                else
                {
                    tagValues.add(content.getProperty("domainTag").getValue());
                }
            }

            for(Value v: tagValues)
            {
                String tagId = v.getString();
                Tag t = tm.resolve(tagId);
                domain.put(t.getName(), t.getTitle());
            }

            //Collections.sort(tags);
        }
        catch(Exception e)
        {
            //e.printStackTrace();
            logger.error(e.getMessage());
        }

        return domain;
    }

    private TreeMap<String, String> getCountry(Node content)
    {
        //List<String> tags = new ArrayList<>();
        TreeMap<String, String> country = new TreeMap<String, String>();

        try
        {
            TagManager tm = resourceResolver.adaptTo(TagManager.class);
            List<Value> tagValues = new ArrayList<>();

            if(content.hasProperty("countryTag"))
            {
                if(content.getProperty("countryTag").isMultiple())
                {
                    tagValues.addAll(Arrays.asList(content.getProperty("countryTag").getValues()));
                }
                else
                {
                    tagValues.add(content.getProperty("countryTag").getValue());
                }
            }

            for(Value v: tagValues)
            {
                String tagId = v.getString();
                Tag t = tm.resolve(tagId);
                country.put(t.getName(), t.getTitle());
            }

            //Collections.sort(tags);
        }
        catch(Exception e)
        {
            //e.printStackTrace();
            logger.error(e.getMessage());
        }

        return country;
    }    
    
      
}
