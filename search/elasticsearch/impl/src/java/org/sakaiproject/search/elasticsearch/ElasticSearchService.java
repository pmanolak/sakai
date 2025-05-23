/**
 * Copyright (c) 2003-2017 The Apereo Foundation
 *
 * Licensed under the Educational Community License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *             http://opensource.org/licenses/ecl2
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.sakaiproject.search.elasticsearch;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.text.StringEscapeUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.lucene.search.TotalHits;
import org.opensearch.action.admin.cluster.node.stats.NodeStats;
import org.opensearch.action.admin.cluster.node.stats.NodesStatsRequest;
import org.opensearch.action.admin.cluster.node.stats.NodesStatsResponse;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.search.ShardSearchFailure;
import org.opensearch.analysis.common.CommonAnalysisPlugin;
import org.opensearch.client.Request;
import org.opensearch.client.Response;
import org.opensearch.client.RestClient;
import org.opensearch.client.RestHighLevelClient;
import org.opensearch.cluster.node.DiscoveryNodeRole;
import org.opensearch.common.network.InetAddresses;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.set.Sets;
import org.opensearch.node.InternalSettingsPreparer;
import org.opensearch.node.Node;
import org.opensearch.node.NodeValidationException;
import org.opensearch.plugins.Plugin;
import org.opensearch.search.SearchHit;
import org.opensearch.search.SearchHits;
import org.opensearch.search.aggregations.InternalAggregations;
import org.opensearch.search.internal.InternalSearchResponse;
import org.opensearch.search.suggest.Suggest;
import org.opensearch.transport.Netty4Plugin;
import org.sakaiproject.component.api.ServerConfigurationService;
import org.sakaiproject.event.api.Event;
import org.sakaiproject.event.api.Notification;
import org.sakaiproject.event.api.NotificationEdit;
import org.sakaiproject.event.api.NotificationService;
import org.sakaiproject.exception.IdUnusedException;
import org.sakaiproject.search.api.EntityContentProducer;
import org.sakaiproject.search.api.InvalidSearchQueryException;
import org.sakaiproject.search.api.SearchConstants;
import org.sakaiproject.search.api.SearchIndexBuilder;
import org.sakaiproject.search.api.SearchList;
import org.sakaiproject.search.api.SearchService;
import org.sakaiproject.search.api.SearchStatus;
import org.sakaiproject.search.api.SiteSearchIndexBuilder;
import org.sakaiproject.search.api.TermFrequency;
import org.sakaiproject.search.elasticsearch.filter.SearchItemFilter;
import org.sakaiproject.search.elasticsearch.serialization.NodeStatsResponseFactory;
import org.sakaiproject.search.model.SearchBuilderItem;
import org.sakaiproject.site.api.SiteService;
import org.sakaiproject.thread_local.api.ThreadLocalManager;
import org.sakaiproject.tool.api.SessionManager;
import org.sakaiproject.user.api.User;
import org.sakaiproject.user.api.UserDirectoryService;
import org.sakaiproject.util.BasicConfigItem;
import org.springframework.util.SocketUtils;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

/**
 * <p>Drop in replacement for sakai's legacy search service which uses {@link <a href="http://elasticsearch.org">elasticsearch</a>}.
 * This service runs embedded in Sakai it does not require a standalone search server.  Like the legacy search service
 * docs are indexed by notification sent via the event service.  This handoff simply puts the reference and associated
 * metadata into ES.  A timer task runs and looks for docs that do not have content, and then digests the content
 * and stores that in the ES index.  This task is necessary to off load this work from any user initiated thread so in the
 * case of large files we aren't holding on to user threads for log periods of time. </p>
 * <p/>
 * <p>Any elasticsearch properties are automatically fed to initialization by looking for properties that start with
 * "elasticsearch."  For example to enable the REST access set the following in sakai.properties:
 * <p/>
 * <pre>
 *    elasticsearch.http.enabled=true
 *    elasticsearch.http.port=9201
 * </pre>
 * <p/>
 * </p>
 */
@Slf4j
 public class ElasticSearchService implements SearchService {

    /* constant config */
    private static final String PENDING_INDEX_BUILDER_REGISTRATION = ElasticSearchService.class.getName() + ".pendingIndexBuilderRegistration";
    private static final ElasticSearchIndexBuilder NO_OP_INDEX_BUILDER = new NoOpElasticSearchIndexBuilder();

    /* ElasticSearch handles and configs */
    private Node node;
    private RestHighLevelClient client;

    @Setter
    private boolean localNode = false;

    /**
     * set this to true if you intend to run an ElasticSearch cluster that is external to Sakai
     * this instructs ES to not store any data on the local node but only act as a client
     */
    @Setter
    private boolean clientNode = false;

    /* injected dependencies */
    private List<String> triggerFunctions = new ArrayList<>();
    private NodeStatsResponseFactory nodeStatsResponseFactory;
    @Setter
    private NotificationService notificationService;
    @Setter
    private ServerConfigurationService serverConfigurationService;
    @Setter
    private ThreadLocalManager threadLocalManager;
    @Setter
    private UserDirectoryService userDirectoryService;
    @Setter
    private SessionManager sessionManager;
    @Setter
    private SiteService siteService;

    /* internal caches and configs */
    final private ConcurrentHashMap<String, ElasticSearchIndexBuilderRegistration> indexBuilders = new ConcurrentHashMap<>();
    private InternalEventRegistrar eventRegistrar = new InternalEventRegistrar();
    private Set<String> globalContentFunctions = Sets.newConcurrentHashSet();
    /**
     * used in searchXML() to maintain backwards compatibility
     */
    @Setter
    private String sharedKey = null;

    private static class EmbeddedElasticSearchNode extends Node {
        public EmbeddedElasticSearchNode(Settings preparedSettings, Map<String, String> systemProperties, Supplier<String> nodeName, Collection<Class<? extends Plugin>> classpathPlugins) {
            super(InternalSettingsPreparer.prepareEnvironment(preparedSettings, systemProperties, null, nodeName), classpathPlugins, true);
        }
    }

    public void init() {
        if (!isEnabled()) {
            log.info("Search is not enabled. Set search.enable=true to change that.");
            return;
        }
        initializeSearch();
        nodeStatsResponseFactory = new NodeStatsResponseFactory();
    }

    protected void initializeSearch() {
        final Settings settings = initializeSearchSettings();
        if (node == null && !clientNode) node = initializeSearchNode(settings);
        if (client == null) client = initializeSearchClient(settings);
    }

    protected Settings initializeSearchSettings() {
        Settings.Builder settingsBuilder = Settings.builder();

        // load anything set into the ServerConfigurationService that starts with "elasticsearch."
        for (ServerConfigurationService.ConfigItem configItem : serverConfigurationService.getConfigData().getItems()) {
            if (configItem.getName().startsWith(ElasticSearchConstants.CONFIG_PROPERTY_PREFIX + "index.")){
                continue;
            }
            if (configItem.getName().startsWith(ElasticSearchConstants.CONFIG_PROPERTY_PREFIX)) {
                settingsBuilder.put(configItem.getName().replaceFirst(ElasticSearchConstants.CONFIG_PROPERTY_PREFIX, ""), configItem.getValue().toString());
            }
        }

        // these properties are required at an minimum, assure they are set to reasonable defaults.
        if (settingsBuilder.get("node.name") == null) {
            settingsBuilder.put("node.name", serverConfigurationService.getServerId());
        }
        if (settingsBuilder.get("cluster.name") == null) {
            settingsBuilder.put("cluster.name", serverConfigurationService.getServerName());
        }

        if (settingsBuilder.get("path.home") == null) {
            settingsBuilder.put("path.home", serverConfigurationService.getSakaiHomePath() + "/elasticsearch");
        }

        if (settingsBuilder.get("path.data") == null) {
            settingsBuilder.put("path.data", serverConfigurationService.getSakaiHomePath() + "/elasticsearch/" + settingsBuilder.get("node.name"));
        }

        String host = settingsBuilder.get("http.host");
        String port = settingsBuilder.get("http.port");
        String transportPort = settingsBuilder.get("transport.port");

        if (StringUtils.isBlank(host)) {
            host = "127.0.0.1";
            settingsBuilder.put("http.host", host);
        }

        if (StringUtils.isBlank(port)) {
            int availablePort = findAvailableTcpPort(host, 9200);
            port = Integer.toString(availablePort);
            settingsBuilder.put("http.port", availablePort);
        }

        if (StringUtils.isBlank(transportPort)) {
            int availablePort = findAvailableTcpPort(host, 9300);
            transportPort = Integer.toString(availablePort);
            settingsBuilder.put("transport.port", transportPort);
        }

        serverConfigurationService.registerConfigItem(BasicConfigItem.makeConfigItem("elasticsearch.http.host", host, this.getClass().getName()));
        serverConfigurationService.registerConfigItem(BasicConfigItem.makeConfigItem("elasticsearch.http.port", port, this.getClass().getName()));
        serverConfigurationService.registerConfigItem(BasicConfigItem.makeConfigItem("elasticsearch.transport.port", transportPort, this.getClass().getName()));

        settingsBuilder.put("transport.type", "netty4");

        clientNode = serverConfigurationService.getBoolean("search.clientNode", false);

        return settingsBuilder.build();
    }

    public String getNodeName() {
        if (node != null) {
            return node.settings().get("node.name");
        }
        return "";
    }

    protected Node initializeSearchNode(Settings settings) {
        Collection<Class<? extends Plugin>> plugins = Arrays.asList(
                Netty4Plugin.class,
                CommonAnalysisPlugin.class);

        Map<String, String> systemProperties = System.getProperties().entrySet().stream()
                .filter(e -> String.valueOf(e.getKey()).startsWith(ElasticSearchConstants.CONFIG_PROPERTY_PREFIX))
                .collect(Collectors.toMap(e -> String.valueOf(e.getKey()).replace(ElasticSearchConstants.CONFIG_PROPERTY_PREFIX, ""), e -> String.valueOf(e.getValue())));
        Supplier<String> nodeName = () -> settings.get("node.name");
        Node node = new EmbeddedElasticSearchNode(settings, systemProperties, nodeName, plugins);

        log.info("Search node configured with node=[{}], cluster name=[{}], http port=[{}], discovery port=[{}], home=[{}], data=[{}]",
                settings.get("node.name"),
                settings.get("cluster.name"),
                settings.get("http.port"),
                settings.get("transport.port"),
                settings.get("path.home"),
                settings.get("path.data"));

        try {
            node.start();
        } catch (NodeValidationException nve) {
            log.error("Could not start embedded Search node, {}", nve.toString());
            return null;
        }

        return node;
    }

    protected RestHighLevelClient initializeSearchClient(Settings settings) {
        String host = settings.get("http.host", "localhost");
        int port = settings.getAsInt("http.port", 9200);
        HttpHost httpHost = new HttpHost(host, port);
        log.info("Search rest high level client configured with: {}", httpHost.toHostString());
        return new RestHighLevelClient(RestClient.builder(httpHost));
    }

    private int findAvailableTcpPort(String host, int port) {
        try (ServerSocket serverSocket = new ServerSocket(port, 0, InetAddresses.forString(host))) {
            if (serverSocket.getLocalPort() != port) {
                throw new IOException("Port " + port + " is in use and can't be used by search");
            }
            return port;
        } catch (IOException ioe) {
            log.warn("Can't use port {}, {}", port, ioe.toString());
        }
        return findAvailableTcpPort(host, SocketUtils.findAvailableTcpPort(49152));
    }

    public void registerIndexBuilder(ElasticSearchIndexBuilder indexBuilder) {
        if (!isEnabled()) {
            log.info("Search is not enabled. Skipping registration request from index builder [{}]. Set search.enable=true to change that.", indexBuilder.getName());
            return;
        }

        String indexBuilderName = null;
        ElasticSearchIndexBuilderRegistration registration;
        synchronized (indexBuilders) {
            try {
                indexBuilderName = indexBuilder.getName();
                if (indexBuilders.containsKey(indexBuilderName)) {
                    log.error("Skipping duplicate registration request from index builder [{}]. Including stack trace for diagnostic purposes", indexBuilder.getName(), new RuntimeException("Diagnostic"));
                    return;
                } else {
                    registration = new ElasticSearchIndexBuilderRegistration(indexBuilder);
                }

                // Content providers are registered with index builders outside of the DI framework, so a builder's
                // providers may not have registered themselves when the builder registers itself with the search
                // service here. So we let the provider determine when to wire up its events. And that may happen
                // repeatedly, as provider registrations trickle in.
                //
                // Historically, content providers registered their events directly with the search service
                // (SearchService.registerFunction()) during startup. The global index builder was injected into the
                // search service via DI and was configured as the listener for all events hitting the search service.
                // That approach works less well now that we support multiple index builders because not all builders
                // will be interested in all events. Builders can certainly filter out events they don't care about,
                // but it is far better to never deliver them in the first place. Rather than require modifications
                // to all content providers in the wild, though, we allow a mixed model here. Globally
                // registered "functions" will still broadcast to all builders, but new content providers are
                // encouraged to scope their events to their index builder. The index builder is then responsible
                // for keeping its total set of event registrations up to date via the
                // ElasticSearchIndexBuilderEventRegistrar passed into its initialize() method below.
                //
                // The thread local stuff is to work around scenarios where indexBuilder.initialize() calls back
                // to eventRegistrar.updateEventsFor() immediately such that the index builder registration hasn't been
                // put into the indexBuilders map. We want to delay the latter until the index builder is fully
                // initialized, e.g. so we don't return a partially initialized builder to indexBuilderByNameOrDefault()
                // in a search request.
                threadLocalManager.set(PENDING_INDEX_BUILDER_REGISTRATION, registration);
                indexBuilder.initialize(eventRegistrar, client);
                indexBuilders.put(indexBuilderName, registration);
            } catch ( Exception e ) {
                log.error("Failed to initialize index builder [{}]", indexBuilderName, e);
                indexBuilders.remove(indexBuilderName);
            } finally {
                threadLocalManager.set(PENDING_INDEX_BUILDER_REGISTRATION, null);
            }
        }
    }

    private class InternalEventRegistrar implements ElasticSearchIndexBuilderEventRegistrar {
        @Override
        public void updateEventsFor(ElasticSearchIndexBuilder indexBuilder) {
            // Could get fancy with this and only lock on the index builder once retrieved. let's go with
            // coarse grained locking on the entire collection just to be sure to avoid any subtle double-checked
            // locking issues.
            synchronized (ElasticSearchService.this.indexBuilders) {
                // games w atomicreference just so we can reference registration from lambdas
                AtomicReference<ElasticSearchIndexBuilderRegistration> registrationHolder =
                        new AtomicReference<>(indexBuilders.get(indexBuilder.getName()));
                if ( registrationHolder.get() == null ) {
                    registrationHolder.set((ElasticSearchIndexBuilderRegistration)threadLocalManager.get(PENDING_INDEX_BUILDER_REGISTRATION));
                }
                if ( registrationHolder.get() == null ) {
                    return;
                }
                final ElasticSearchIndexBuilderRegistration registration = registrationHolder.get();
                if ( registration.notification == null ) {
                    log.debug("Register a notification to trigger indexation on new elements by index builder [{}]", indexBuilder.getName());
                    // register a transient notification for resources
                    final NotificationEdit notification = notificationService.addTransientNotification();
                    registration.notification = notification;

                    notification.setResourceFilter(indexBuilder.getEventResourceFilter());

                    // Add all the functions that are registered to trigger search index modification
                    // triggerFunctions is injected via DI and is effectively read-only after startup,
                    // so we only set those functions on the notification once, when it is created
                    triggerFunctions.forEach(s -> registerNoDuplicates(s, registration.notification));
                    registerNoDuplicates(SearchService.EVENT_TRIGGER_SEARCH, registration.notification);

                    // globalContentFunctions are registered dynamically as legacy content producers are initialized,
                    // but these registrations are propagated immediately to any registered index builders
                    // (see handleNewGlobalContentFunction() below), so we only need to register the entire collection
                    // once here, when the index builder's notification is first built.
                    globalContentFunctions.forEach(s -> registerNoDuplicates(s, registration.notification));

                    // set the action
                    notification.setAction(new SearchNotificationAction(indexBuilder));
                }

                indexBuilder.getTriggerFunctions().forEach(s -> registerNoDuplicates(s, registration.notification));
                indexBuilder.getContentFunctions().forEach(s -> registerNoDuplicates(s, registration.notification));
            }
        }

        private void handleNewGlobalContentFunction(String function) {
            log.info("Register {} as a trigger for the search service", function);

            if (!isEnabled()) {
                log.debug("Search is not enabled. Set search.enable=true to change that.");
                return;
            }

            synchronized (ElasticSearchService.this.indexBuilders) {
                globalContentFunctions.add(function);
                ElasticSearchService.this.indexBuilders.forEach((k,v) -> registerNoDuplicates(function, v.notification));
            }
        }

        private void registerNoDuplicates(String function, NotificationEdit notification) {
            if ( !(notification.containsFunction(function)) ) {
                notification.addFunction(function);
            }
        }
    }

    @Override
    public SearchList search(String searchTerms, List<String> siteIds, List<String> toolIds, int start, int end, String filterName, String sorterName) throws InvalidSearchQueryException {
        return search(searchTerms, siteIds, toolIds, start, end);
    }

    @Override
    public SearchList search(String searchTerms, List<String> siteIds, List<String> toolIds, int searchStart, int searchEnd) throws InvalidSearchQueryException {
        Pair<SearchResponse, ElasticSearchIndexBuilder> result =
                search(searchTerms, null, siteIds, toolIds, searchStart, searchEnd, null, null, new ArrayList<>());
        return new ElasticSearchList(searchTerms.toLowerCase(), result.getLeft(), this, result.getRight(),
                result.getRight().getFacetName(), result.getRight().getFilter());
    }

    @Override
    public SearchList search(String searchTerms, List<String> siteIds, List<String> toolIds, int searchStart, int searchEnd, String indexBuilderName) throws InvalidSearchQueryException {
        Pair<SearchResponse, ElasticSearchIndexBuilder> result =
                search(searchTerms, indexBuilderName, siteIds, toolIds, searchStart, searchEnd, null, null, new ArrayList<>());
        return new ElasticSearchList(searchTerms.toLowerCase(), result.getLeft(), this, result.getRight(),
                result.getRight().getFacetName(), result.getRight().getFilter());
    }

    @Override
    public SearchList search(String searchTerms, List<String> siteIds, List<String> toolIds, int searchStart, int searchEnd, String indexBuilderName, Map<String,String> additionalSearchInformation) throws InvalidSearchQueryException {
        Pair<SearchResponse, ElasticSearchIndexBuilder> result =
                search(searchTerms, indexBuilderName, siteIds, toolIds, searchStart, searchEnd, null, null, new ArrayList<>(),additionalSearchInformation);
        return new ElasticSearchList(searchTerms.toLowerCase(), result.getLeft(), this, result.getRight(),
                result.getRight().getFacetName(), result.getRight().getFilter());
    }

    @Override
    public SearchResponse searchResponse(String searchTerms, List<String> siteIds, List<String> toolIds, int searchStart, int searchEnd, String indexBuilderName, Map<String,String> additionalSearchInformation) throws InvalidSearchQueryException {
        Pair<SearchResponse, ElasticSearchIndexBuilder> result =
                search(searchTerms, indexBuilderName, siteIds, toolIds, searchStart, searchEnd, null, null, new ArrayList<>(),additionalSearchInformation);
        return result.getLeft();
    }

    SearchResponse search(String searchTerms, List<String> siteIds, List<String> toolIds, int start, int end, List<String> references,String indexBuilderName) throws InvalidSearchQueryException {
        return search(searchTerms, indexBuilderName, siteIds, toolIds, start, end, null, null, references).getLeft();
    }

    Pair<SearchResponse, ElasticSearchIndexBuilder> search(String searchTerms, String indexBuilderName, List<String> siteIds, List<String> toolIds, int start, int end, String filterName, String sorterName, List<String> references) throws InvalidSearchQueryException {
        if (references == null) {
            references = new ArrayList<>();
        }
        if (siteIds == null) {
            siteIds = new ArrayList<>();
        }

        ElasticSearchIndexBuilder indexBuilder = indexBuilderByNameOrDefault(indexBuilderName);
        SearchResponse response = indexBuilder.search(searchTerms, references, siteIds, toolIds, start, end);
        return new ImmutablePair<>(response, indexBuilder);
    }

    Pair<SearchResponse, ElasticSearchIndexBuilder> search(String searchTerms, String indexBuilderName, List<String> siteIds, List<String> toolIds, int start, int end, String filterName, String sorterName, List<String> references, Map<String,String> additionalSearchInformation) throws InvalidSearchQueryException {
        if (references == null) {
            references = new ArrayList<>();
        }
        if (siteIds == null) {
            siteIds = new ArrayList<>();
        }

        ElasticSearchIndexBuilder indexBuilder = indexBuilderByNameOrDefault(indexBuilderName);
        SearchResponse response = indexBuilder.search(searchTerms, references, siteIds, toolIds, start, end, additionalSearchInformation);
        return new ImmutablePair<>(response, indexBuilder);
    }

    protected ElasticSearchIndexBuilder indexBuilderByNameOrDefault(String indexBuilderName) {
        if (StringUtils.isEmpty(indexBuilderName) || !(indexBuilders.containsKey(indexBuilderName))) {
            final ElasticSearchIndexBuilderRegistration registration =
                    indexBuilders.get(ElasticSearchIndexBuilder.DEFAULT_INDEX_BUILDER_NAME);
            return registration == null ? NO_OP_INDEX_BUILDER : registration.indexBuilder;
        }
        return indexBuilders.get(indexBuilderName).indexBuilder;
    }

    public String searchXML(Map<String, String[]> parameterMap) {
        String userid = null;
        String searchTerms = null;
        String checksum = null;
        String contexts = null;
        String ss = null;
        String se = null;
        try {
            String[] useridA = parameterMap.get(REST_USERID);
            String[] searchTermsA = parameterMap.get(REST_TERMS);
            String[] checksumA = parameterMap.get(REST_CHECKSUM);
            String[] contextsA = parameterMap.get(REST_CONTEXTS);
            String[] ssA = parameterMap.get(REST_START);
            String[] seA = parameterMap.get(REST_END);

            StringBuilder sb = new StringBuilder();
            sb.append("<?xml version=\"1.0\"?>");

            boolean requestError = false;
            if (useridA == null || useridA.length != 1) {
                requestError = true;
            } else {
                userid = useridA[0];
            }
            if (searchTermsA == null || searchTermsA.length != 1) {
                requestError = true;
            } else {
                searchTerms = searchTermsA[0];
            }
            if (checksumA == null || checksumA.length != 1) {
                requestError = true;
            } else {
                checksum = checksumA[0];
            }
            if (contextsA == null || contextsA.length != 1) {
                requestError = true;
            } else {
                contexts = contextsA[0];
            }
            if (ssA == null || ssA.length != 1) {
                requestError = true;
            } else {
                ss = ssA[0];
            }
            if (seA == null || seA.length != 1) {
                requestError = true;
            } else {
                se = seA[0];
            }

            if (requestError) {
                throw new Exception("Invalid Request");

            }

            int searchStart = Integer.parseInt(ss);
            int searchEnd = Integer.parseInt(se);
            String[] ctxa = contexts.split(";");
            List<String> ctx = new ArrayList<>(ctxa.length);
            ctx.addAll(Arrays.asList(ctxa));

            if (sharedKey != null && !sharedKey.isEmpty()) {
                String check = digestCheck(userid, searchTerms);
                if (!check.equals(checksum)) {
                    throw new Exception("Security Checksum is not valid");
                }
            }

            org.sakaiproject.tool.api.Session s = sessionManager.startSession();
            User u = userDirectoryService.getUser("admin");
            s.setUserId(u.getId());
            sessionManager.setCurrentSession(s);
            try {

                SearchList sl = search(searchTerms, ctx, null, searchStart, searchEnd);
                sb.append("<results ");
                sb.append(" fullsize=\"").append(sl.getFullSize())
                        .append("\" ");
                sb.append(" start=\"").append(sl.getStart()).append("\" "); //$NON-NLS-2$
                sb.append(" size=\"").append(sl.size()).append("\" "); //$NON-NLS-2$
                sb.append(" >");
                sl.forEach(sr -> sr.toXMLString(sb));
                sb.append("</results>");
                return sb.toString();
            } finally {
                sessionManager.setCurrentSession(null);
            }
        } catch (Exception ex) {
            log.error("Search Service XML response failed ", ex);
            StringBuilder sb = new StringBuilder();
            sb.append("<?xml version=\"1.0\"?>");
            sb.append("<fault>");
            sb.append("<request>");
            sb.append("<![CDATA[");
            sb.append(" userid = ").append(StringEscapeUtils.escapeXml11(userid)).append("\n"); //$NON-NLS-2$
            sb.append(" searchTerms = ").append(StringEscapeUtils.escapeXml11(searchTerms)).append("\n"); //$NON-NLS-2$
            sb.append(" checksum = ").append(StringEscapeUtils.escapeXml11(checksum)).append("\n"); //$NON-NLS-2$
            sb.append(" contexts = ").append(StringEscapeUtils.escapeXml11(contexts)).append("\n"); //$NON-NLS-2$
            sb.append(" ss = ").append(StringEscapeUtils.escapeXml11(ss)).append("\n"); //$NON-NLS-2$
            sb.append(" se = ").append(StringEscapeUtils.escapeXml11(se)).append("\n"); //$NON-NLS-2$
            sb.append("]]>");
            sb.append("</request>");
            sb.append("<error>");
            sb.append("<![CDATA[");
            try {
                StringWriter sw = new StringWriter();
                PrintWriter pw = new PrintWriter(sw);
                log.error("Search Service XML response failed {}", pw);
                pw.flush();
                sb.append(sw);
                pw.close();
                sw.close();
            } catch (Exception ex2) {
                sb.append("Failed to serialize exception ")
                        .append(ex.getMessage())
                        .append("\n");
                sb.append("Case:  ")
                        .append(ex2.getMessage());

            }
            sb.append("]]>");
            sb.append("</error>");
            sb.append("</fault>");
            return sb.toString();
        }
    }

    private String digestCheck(String userid, String searchTerms)
            throws GeneralSecurityException, IOException {
        MessageDigest sha1 = MessageDigest.getInstance("SHA1");
        String chstring = sharedKey + userid + searchTerms;
        return byteArrayToHexStr(sha1.digest(chstring.getBytes(StandardCharsets.UTF_8)));
    }

    private static String byteArrayToHexStr(byte[] data) {
        char[] chars = new char[data.length * 2];
        for (int i = 0; i < data.length; i++) {
            byte current = data[i];
            int hi = (current & 0xF0) >> 4;
            int lo = current & 0x0F;
            chars[2 * i] = (char) (hi < 10 ? ('0' + hi) : ('A' + hi - 10));
            chars[2 * i + 1] = (char) (lo < 10 ? ('0' + lo) : ('A' + lo - 10));
        }
        return new String(chars);
    }


    @Override
    public void registerFunction(String function) {
        eventRegistrar.handleNewGlobalContentFunction(function);
    }

    @Override
    public void refreshInstance() {
        forEachRegisteredIndexBuilder(SearchIndexBuilder::refreshIndex);
    }

    @Override
    public void refreshIndex(String indexBuilderName) {
        log.info("Refresh Index for Index Builder Name={}", indexBuilderName);
        ElasticSearchIndexBuilderRegistration builder =
            indexBuilders.get(indexBuilderName);
        if (builder != null) {
            builder.indexBuilder.refreshIndex();
        }
    }

    @Override
    public void rebuildInstance() {
        forEachRegisteredIndexBuilder(SearchIndexBuilder::rebuildIndex);
    }

    @Override
    public void rebuildIndex(String indexBuilderName) {
        log.info("Rebuild Index Builder Name={}", indexBuilderName);
        ElasticSearchIndexBuilderRegistration builder =
            indexBuilders.get(indexBuilderName);
        if (builder != null) {
            builder.indexBuilder.rebuildIndex();
        }
    }

    public void refreshSite(String currentSiteId) {
        forEachRegisteredIndexBuilder(i -> {
            if ( i instanceof SiteSearchIndexBuilder ) {
                ((SiteSearchIndexBuilder)i).refreshIndex(currentSiteId);
            }
        });
    }

    public void rebuildSite(String currentSiteId) {
        forEachRegisteredIndexBuilder(i -> {
            if ( i instanceof SiteSearchIndexBuilder ) {
                ((SiteSearchIndexBuilder)i).rebuildIndex(currentSiteId);
            }
        });
    }

    @Override
    public void reload() {

    }

    @Override
    public String getStatus() {
        final StringBuilder sb = new StringBuilder();
        forEachRegisteredIndexBuilder(i -> i.getStatus(sb).append("\n\n"));
        return sb.toString();
    }

    @Override
    public long getNDocs() {
        return indexBuilders.reduceValues(Long.MAX_VALUE, v -> v.indexBuilder.getNDocs(), Long::sum);
    }

    @Override
    public int getPendingDocs() {
        return indexBuilders.reduceValues(Long.MAX_VALUE, v -> v.indexBuilder.getPendingDocuments(), Integer::sum);
    }

    @Override
    public List<SearchBuilderItem> getAllSearchItems() {
        return Collections.emptyList();
    }

    @Override
    public List<SearchBuilderItem> getSiteMasterSearchItems() {
        return Collections.emptyList();
    }

    @Override
    public List<SearchBuilderItem> getGlobalMasterSearchItems() {
        return Collections.emptyList();
    }

    @Override
    public List<SearchStatus> getSearchStatus() {
        final List<SearchStatus> indexBuilderStatuses = new ArrayList<>();
        forEachRegisteredIndexBuilder(i -> indexBuilderStatuses.add(i.getSearchStatus()));

        final NodesStatsResponse nodesStatsResponse = getNodesStats();

        if (nodesStatsResponse != null) {
            return indexBuilderStatuses
                    .stream()
                    .map(s -> newSearchStatusWrapper(s, nodesStatsResponse))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }


    protected SearchStatus newSearchStatusWrapper(SearchStatus toWrap, NodesStatsResponse nodesStatsResponse) {
        return new SearchStatus() {

            @Override
            public String getLastLoad() {
                return toWrap.getLastLoad();
            }

            @Override
            public String getLoadTime() {
                return toWrap.getLoadTime();
            }

            @Override
            public String getCurrentWorker() {
                return getNodeName();
            }

            @Override
            public String getCurrentWorkerETC() {
                return getNodeName();
            }

            @Override
            public List<String[]> getWorkerNodes() {
                List<String[]> workers = new ArrayList<>();

                for (NodeStats nodeStat : nodesStatsResponse.getNodes()) {
                    if (nodeStat.getNode().getRoles().contains(DiscoveryNodeRole.DATA_ROLE)) {
                        workers.add(new String[]{nodeStat.getNode().getName() + "(" + nodeStat.getHostname() + ")",
                            null, // No way to get a meaningful "start" time per node, so now just set a null Date.
                            // Historically used an index builder starttime, which was always meaningless in this
                            // context since it's always going to refer to the local node. And we now have
                            // multiple index builders, so it's doubly meaningless. Historical comment below
                            // hints at same problem with the results of 'getStatus()'
                            //TODO will show same status for each node, need to deal with that
                            getStatus()});
                    }
                }
                return workers;
            }

            @Override
            public String getNDocuments() {
                return toWrap.getNDocuments();
            }

            @Override
            public String getPDocuments() {
                return toWrap.getPDocuments();
            }
        };
    }

    protected NodesStatsResponse getNodesStats() {

        if (node != null) {
            return node.client().admin().cluster().nodesStats(new NodesStatsRequest()).actionGet();
        } else {
            Request request = new Request("GET", NodeStatsResponseFactory.NODE_STATS_INDICES_API);
            try {
                Response response = client.getLowLevelClient().performRequest(request);
                int statusCode = response.getStatusLine().getStatusCode();
                if (statusCode == 200) {
                    HttpEntity httpEntity = response.getEntity();
                    if (httpEntity != null) {
                        NodesStatsResponse nodesStatsResponse = nodeStatsResponseFactory.createNodeStatsIndicesFromJSON(httpEntity.getContent());
                        log.debug("Response for request [{}], status [{}], result:\n{}", response.getRequestLine(), statusCode, nodesStatsResponse);
                        return nodesStatsResponse;
                    } else {
                        log.warn("Response for request [{}], status [{}], warnings [{}], contained no data", response.getRequestLine(), statusCode, response.getWarnings());
                    }
                } else {
                    log.warn("Response for request [{}], status [{}], warnings [{}]", response.getRequestLine(), statusCode, response.getWarnings());
                }
            } catch (Exception e) {
                log.warn("Failed retrieving node stats from cluster, {}", e.toString());
            }
        }
        return null;
    }

    protected void forEachRegisteredIndexBuilder(Consumer<ElasticSearchIndexBuilder> consumer) {
        indexBuilders.forEachValue(Long.MAX_VALUE, v -> v.indexBuilder, consumer);
    }

    @Override
    public boolean removeWorkerLock() {
        return true;
    }

    @Override
    public List<Object[]> getSegmentInfo() {
        return Collections.singletonList(new Object[]{"Index Segment Info is not implemented", "", ""});
    }

    @Override
    public void forceReload() {
    }

    @Override
    public TermFrequency getTerms(int documentId) throws IOException {
        throw new UnsupportedOperationException("Search can't does not support this operation at this time.");
    }

    @Override
    public boolean isEnabled() {
        return serverConfigurationService.getBoolean("search.enable", false);
    }

    @Override
    public boolean isEnabledForSite(String siteId) {

		if (!isEnabled()) return false;

		if (StringUtils.isBlank(siteId)) {
			log.warn("empty or null siteId supplied to isEnabledForSite");
			return false;
		}

		if (!serverConfigurationService.getBoolean("search.onlyIndexSearchToolSites", true)) return true;

		try {
			if (siteService.getSite(siteId).getToolForCommonId(SearchConstants.TOOL_ID) != null) return true;
		} catch (IdUnusedException e) {
			log.warn("siteId {} is not valid", siteId);
		}

		return false;
    }

    @Override
    public String getDigestStoragePath() {
        return null;
    }

    @Override
    public String getSearchSuggestion(String searchString) {
        String[] suggestions = getSearchSuggestions(searchString, null, true);
        if (suggestions != null) {
            for (String suggestion : suggestions) {
                if (searchString.equalsIgnoreCase(suggestion)) {
                    continue;
                }
                return suggestion;
            }
        }
        return null;
    }

    @Override
    public String[] getSearchSuggestions(String searchString, String currentSite, boolean allMySites) {
        return getSearchSuggestions(searchString, currentSite, allMySites, null);
    }

    @Override
    public String[] getSearchSuggestions(String searchString, String currentSite, boolean allMySites, String indexBuilderName) {
        final ElasticSearchIndexBuilder indexBuilder = indexBuilderByNameOrDefault(indexBuilderName);
        return indexBuilder.searchSuggestions(searchString, currentSite, allMySites);
    }

    /**
     * This property is hard coded to always return true, and is only present to preserve backwards capatability.
     *
     * TODO We could interpret this to mean whether or not this node will hold data indices
     * and shards be allocated to it.  So setting this to false for this node to only be a search client.
     * That would take some rework to assure nodes don't attempt indexing work that will fail.
     */
    @Override
    public boolean isSearchServer() {
        return true;
    }

    @Override
    public Set<String> getIndexBuilderNames() {
        return indexBuilders.keySet();
    }

    public void destroy(){
        if (node != null && !node.isClosed()) {
            try {
                node.close();
            } catch (IOException ioe) {
                log.error("Error shutting down search");
            }
            node = null;
        }
    }

    //------------------------------------------------------------------------------------------
    //As far as I know, this implementation isn't diagnosable, so this is a dummy implementation
    //------------------------------------------------------------------------------------------
    @Override
    public void enableDiagnostics() {
    }

    @Override
    public void disableDiagnostics() {
    }

    @Override
    public boolean hasDiagnostics() {
        return false;
    }


    public void setTriggerFunctions(List<String> triggerFunctions) {
        // other code assumes this field is always non-null
        if ( triggerFunctions == null ) {
            this.triggerFunctions.clear();
        } else {
            this.triggerFunctions = triggerFunctions;
        }
    }

    private static class ElasticSearchIndexBuilderRegistration {
        public ElasticSearchIndexBuilderRegistration(ElasticSearchIndexBuilder indexBuilder) {
            this(indexBuilder, null);
        }

        public ElasticSearchIndexBuilderRegistration(ElasticSearchIndexBuilder indexBuilder, NotificationEdit notification) {
            this.indexBuilder = indexBuilder;
            this.notification = notification;
        }

        public ElasticSearchIndexBuilder indexBuilder;
        public NotificationEdit notification;
        public String getName() {
            return indexBuilder == null ? null : indexBuilder.getName();
        }
    }

    private static class NoOpElasticSearchIndexBuilder implements ElasticSearchIndexBuilder {

        @Override
        public void initialize(ElasticSearchIndexBuilderEventRegistrar eventRegistrar, RestHighLevelClient client) {
        }

        @Override
        public Set<String> getTriggerFunctions() {
            return Collections.emptySet();
        }

        @Override
        public Set<String> getContentFunctions() {
            return Collections.emptySet();
        }

        @Override
        public String getEventResourceFilter() {
            return "";
        }

        @Override
        public SearchResponse search(String searchTerms, List<String> references, List<String> siteIds, List<String> toolIds, int start, int end) {
            return search(searchTerms,references,siteIds,toolIds,start,end, new HashMap<>());
        }

        @Override
        public SearchResponse search(String searchTerms, List<String> references, List<String> siteIds, List<String> toolIds, int start, int end, Map<String,String> additionalSearchInfromation) {
            return new SearchResponse(
                    new InternalSearchResponse(new SearchHits(new SearchHit[0], new TotalHits(0, TotalHits.Relation.EQUAL_TO), 0.0f), InternalAggregations.EMPTY, new Suggest(Collections.emptyList()), null, false, false, 1),
                    "no-op",
                    1,
                    1,
                    1,
                    0,
                    ShardSearchFailure.EMPTY_ARRAY,
                    SearchResponse.Clusters.EMPTY);
        }

        @Override
        public String[] searchSuggestions(String searchString, String currentSite, boolean allMySites) {
            return new String[0];
        }

        @Override
        public String getFieldFromSearchHit(String fieldReference, SearchHit hit) {
            return "";
        }

        @Override
        public boolean getUseFacetting() {
            return false;
        }

        @Override
        public String getFacetName() {
            return null;
        }

        @Override
        public SearchItemFilter getFilter() {
            return null;
        }

        @Override
        public StringBuilder getStatus(StringBuilder into) {
            return into;
        }

        @Override
        public long getNDocs() {
            return 0;
        }

        @Override
        public SearchStatus getSearchStatus() {
            return new SearchStatus() {
                @Override
                public String getLastLoad() {
                    return "";
                }

                @Override
                public String getLoadTime() {
                    return "";
                }

                @Override
                public String getCurrentWorker() {
                    return "";
                }

                @Override
                public String getCurrentWorkerETC() {
                    return "";
                }

                @Override
                public List<String[]> getWorkerNodes() {
                    return Collections.emptyList();
                }

                @Override
                public String getNDocuments() {
                    return "0";
                }

                @Override
                public String getPDocuments() {
                    return "0";
                }
            };
        }

        @Override
        public String getName() {
            return "NoOp";
        }

        @Override
        public void addResource(Notification notification, Event event) {
            // no-op
        }

        @Override
        public void registerEntityContentProducer(EntityContentProducer ecp) {
            // no-op
        }

        @Override
        public void refreshIndex() {
            // no-op
        }

        @Override
        public void rebuildIndex() {
            // no-op
        }

        @Override
        public boolean isBuildQueueEmpty() {
            return false;
        }

        @Override
        public Collection<EntityContentProducer> getContentProducers() {
            return Collections.emptyList();
        }

        @Override
        public void destroy() {
            // no-op
        }

        @Override
        public int getPendingDocuments() {
            return 0;
        }

        @Override
        public List<SearchBuilderItem> getAllSearchItems() {
            return Collections.emptyList();
        }

        @Override
        public EntityContentProducer newEntityContentProducer(Event event) {
            return null;
        }

        @Override
        public EntityContentProducer newEntityContentProducer(String ref) {
            return null;
        }

        @Override
        public List<SearchBuilderItem> getGlobalMasterSearchItems() {
            return Collections.emptyList();
        }
    }

}
