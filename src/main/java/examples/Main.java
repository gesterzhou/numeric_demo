package examples;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.apache.logging.log4j.Logger;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.KeywordAnalyzer;
import org.apache.lucene.analysis.core.LowerCaseFilter;
import org.apache.lucene.analysis.util.CharTokenizer;
import org.apache.lucene.queryparser.classic.ParseException;
import static org.apache.geode.distributed.ConfigurationProperties.OFF_HEAP_MEMORY_SIZE;

import org.apache.geode.cache.Cache;
import org.apache.geode.cache.CacheFactory;
import org.apache.geode.cache.DataPolicy;
import org.apache.geode.cache.EvictionAction;
import org.apache.geode.cache.EvictionAttributes;
import org.apache.geode.cache.GemFireCache;
import org.apache.geode.cache.Region;
import org.apache.geode.cache.RegionFactory;
import org.apache.geode.cache.RegionShortcut;
import org.apache.geode.cache.asyncqueue.internal.AsyncEventQueueImpl;
import org.apache.geode.cache.client.ClientCache;
import org.apache.geode.cache.client.ClientCacheFactory;
import org.apache.geode.cache.client.ClientRegionFactory;
import org.apache.geode.cache.client.ClientRegionShortcut;
import org.apache.geode.cache.client.Pool;
import org.apache.geode.cache.client.PoolManager;
import org.apache.geode.cache.execute.Execution;
import org.apache.geode.cache.execute.Function;
import org.apache.geode.cache.execute.FunctionService;
import org.apache.geode.cache.execute.ResultCollector;
import org.apache.geode.cache.lucene.FlatFormatSerializer;
import org.apache.geode.cache.lucene.LuceneIndex;
import org.apache.geode.cache.lucene.LuceneQuery;
import org.apache.geode.cache.lucene.LuceneQueryException;
import org.apache.geode.cache.lucene.LuceneResultStruct;
import org.apache.geode.cache.lucene.LuceneService;
import org.apache.geode.cache.lucene.LuceneServiceProvider;
import org.apache.geode.cache.lucene.PageableLuceneQueryResults;
import org.apache.geode.cache.lucene.internal.LuceneIndexFactoryImpl;
import org.apache.geode.cache.lucene.internal.LuceneIndexForPartitionedRegion;
import org.apache.geode.cache.lucene.internal.LuceneIndexImpl;
import org.apache.geode.cache.lucene.internal.LuceneServiceImpl;
import org.apache.geode.cache.lucene.internal.PartitionedRepositoryManager;
import org.apache.geode.cache.lucene.internal.repository.IndexRepository;
import org.apache.geode.cache.lucene.internal.repository.RepositoryManager;
import org.apache.geode.distributed.ServerLauncher;
import org.apache.geode.distributed.ServerLauncher.Builder;
import org.apache.geode.internal.DSFIDFactory;
import org.apache.geode.internal.ProcessOutputReader;
import org.apache.geode.internal.cache.BucketNotFoundException;
import org.apache.geode.internal.cache.EntrySnapshot;
import org.apache.geode.internal.cache.EvictionAttributesImpl;
import org.apache.geode.internal.cache.LocalRegion;
import org.apache.geode.internal.cache.PartitionedRegion;
import org.apache.geode.internal.cache.RegionEntry;
import org.apache.geode.internal.logging.LogService;
import org.apache.geode.pdx.JSONFormatter;
import org.apache.geode.pdx.PdxInstance;

public class Main {
  ServerLauncher serverLauncher;
  GemFireCache cache;
  Region PersonRegion;
  Region CustomerRegion;
  LuceneServiceImpl service;
  static int serverPort = 50506;
  static boolean useLocator = true;

  final static int ENTRY_COUNT = 10000;
  final static Logger logger = LogService.getLogger();

  
  // test different numeric type
  final String[] personIndexFields = { "name", "revenue", "revenue_float", "revenue_double", "revenue_long"};
  
  // test nested object
  final String[] customerIndexFields = { "name", "revenue", "contacts.revenue"};
  
  public static void main(final String[] args) throws LuceneQueryException, IOException, InterruptedException, java.text.ParseException {
    Main prog = new Main();
    try {
      // create cache, create region, do feed
      prog.demoNumericQuery();

      System.out.println("Press any key to exit");
      int c = System.in.read();

    } finally {
      prog.stopServer();
    }
    return;
  }

  private void demoNumericQuery() throws InterruptedException, LuceneQueryException, IOException {
    createCache(serverPort);
    PersonRegion = createRegion(RegionShortcut.PARTITION_REDUNDANT_PERSISTENT, "Person");
    CustomerRegion = createRegion(RegionShortcut.PARTITION_REDUNDANT_PERSISTENT, "Customer");
    feed(ENTRY_COUNT);

    createPersonIndex();
    insertAJson(PersonRegion);
    waitUntilFlushed("personIndex", "Person");

    createCustomerIndex();
    insertNestObjects(CustomerRegion);
    waitUntilFlushed("customerIndex", "Customer");

    System.out.println("Press any key to do numeric query in java");
    int c = System.in.read();
    doNumericQueryWithPointsConfigMap();
  }

  private void createCache(int port) {
    if (cache != null) {
      return;
    }

    System.out.println("GEMFIRE="+System.getenv("GEMFIRE"));
    Builder builder = new ServerLauncher.Builder()
        .setMemberName("server"+port)
        .setServerPort(port)
    .set("mcast-port", "0")
    .setPdxPersistent(true)
    .set("locators", "localhost[12345]")
    .set("enable-time-statistics","true")
    .set("statistic-sample-rate","1000")
    .set("statistic-sampling-enabled", "true")
    .set("statistic-archive-file", "server1.gfs");
//        .set("log-level", "debug")
    ;
    serverLauncher  = builder.build();
    serverLauncher.start();

    cache = CacheFactory.getAnyInstance();
    service = (LuceneServiceImpl) LuceneServiceProvider.get(cache);
    service.LUCENE_REINDEX = true;
  }

  private void stopServer() {
    if (serverLauncher != null) {
      serverLauncher.stop();
      serverLauncher = null;
      System.out.println("server is stopped");
    }
  }

  private Region createRegion(RegionShortcut shortcut, String regionName) {
    Region region = ((Cache)cache).createRegionFactory(shortcut).create(regionName);
    return region;
  }
  
  private void createPersonIndex() {
    LuceneIndexFactoryImpl factory = (LuceneIndexFactoryImpl)service.createIndexFactory();
    factory.setFields(personIndexFields).create("personIndex", "Person", true);    
  }
  
  private void createCustomerIndex() {
    LuceneIndexFactoryImpl factory = (LuceneIndexFactoryImpl)service.createIndexFactory();
    factory.setFields(customerIndexFields)
    .setLuceneSerializer(new FlatFormatSerializer())
    .create("customerIndex", "Customer", true);
  }

  private void refreshAndCommit(HashSet<IndexRepository> repositories, PartitionedRegion pr) throws BucketNotFoundException, IOException {
    for (IndexRepository repo:repositories) {
      repo.commit();
    }
  }

  public void doNumericQueryWithPointsConfigMap() throws LuceneQueryException {
    LuceneIndexImpl index = (LuceneIndexImpl)service.getIndex("personIndex", "Person");

    System.out.println("Query with numeric fields using PointsConfigMap:");
    queryByStringQueryParser("personIndex", "Person", "revenue=763000", 0, "name");
    queryByStringQueryParser("personIndex", "Person", "revenue=763000 revenue=764000", 0, "name");
    queryByStringQueryParser("personIndex", "Person", "+revenue>763000 +revenue<766000", 0, "name");
    queryByStringQueryParser("personIndex", "Person", "+revenue>=763000 +revenue<=766000", 0, "name");
    queryByStringQueryParser("personIndex", "Person", "revenue:[763000 TO 766000]", 0, "name");
    queryByStringQueryParser("personIndex", "Person", "revenue_float:[763000.0 TO 766000.0]", 0, "name");
    queryByStringQueryParser("personIndex", "Person", "revenue_double:[763000 TO 766000]", 0, "name");
    queryByStringQueryParser("personIndex", "Person", "revenue_long:[763000 TO 766000]", 0, "name");
    queryByStringQueryParser("personIndex", "Person", "+revenue_long:[763000 TO 766000] +revenue_float:[762000 TO 765000]", 5, "name");
    queryByStringQueryParser("personIndex", "Person", "revenue<2000 revenue>9997000 -name=Tom9998*", 0, "name");

    // for PDX(JSON)
    queryByStringQueryParser("personIndex", "Person", "revenue=400000", 0, "name");
    
    // Nested Object
    queryByStringQueryParser("customerIndex", "Customer", "revenue:[762000 TO 765000]", 0, "name");
    queryByStringQueryParser("customerIndex", "Customer", "contacts.revenue:[763000 TO 766000]", 0, "name");
    queryByStringQueryParser("customerIndex", "Customer", "+contacts.revenue:[763000 TO 766000] +revenue:[762000 TO 765000]", 0, "name");
  }

  private void displayResults(ResultCollector<?,?> rc) {
    ArrayList functionResults = (ArrayList)((ArrayList)rc.getResult()).get(0);
    
    System.out.println("\nClient Function found "+functionResults.size()+" results");
    functionResults.stream().forEach(result -> {
      System.out.println(result);
    });
  }
  
  private void waitUntilFlushed(String indexName, String regionName) throws InterruptedException {
    LuceneIndexImpl index = (LuceneIndexImpl)service.getIndex(indexName, regionName);
    boolean status = false;
    long then = System.currentTimeMillis();
    do {
      status = service.waitUntilFlushed(indexName, regionName, 60000, TimeUnit.MILLISECONDS);
    } while (status == false);
    System.out.println("wait time after feed is:"+(System.currentTimeMillis() - then));
  }

  private void feed(int count) {
    for (int i=0; i<count; i++) {
      PersonRegion.put("key"+i, new Person(i));
    }

    for (int i=0; i<count; i++) {
      CustomerRegion.put("key"+i, new Customer(i));
    }
  }

  private void insertNestObjects(Region region) {
    Customer customer123 = new Customer(123);
    Customer customer456 = new Customer(456);
    region.put("customer123", customer123);
    region.put("customer456", customer456);
  }

  private void insertAJson(Region region) {
    String jsonCustomer = "{"
        + "\"name\": \"Tom9_JSON\","
        + "\"lastName\": \"Smith\","
        + " \"age\": 25,"
        + " \"revenue\": 400000,"
        + "\"address\":"
        + "{"
        + "\"streetAddress\": \"21 2nd Street\","
        + "\"city\": \"New York\","
        + "\"state\": \"NY\","
        + "\"postalCode\": \"10021\""
        + "},"
        + "\"phoneNumber\":"
        + "["
        + "{"
        + " \"type\": \"home\","
        + "\"number\": \"212 555-1234\""
        + "},"
        + "{"
        + " \"type\": \"fax\","
        + "\"number\": \"646 555-4567\""
        + "}"
        + "]"
        + "}";

    region.put("jsondoc1", JSONFormatter.fromJSON(jsonCustomer));
    System.out.println("JSON documents added into Cache: " + jsonCustomer);
    System.out.println(region.get("jsondoc1"));
    System.out.println();

    String jsonCustomer2 = "{"
        + "\"name\": \"Tom99_JSON\","
        + "\"lastName\": \"Smith\","
        + " \"age\": 25,"
        + " \"revenue\": 400001,"
        + "\"address\":"
        + "{"
        + "\"streetAddress\": \"21 2nd Street\","
        + "\"city\": \"New York\","
        + "\"state\": \"NY\","
        + "\"postalCode\": \"10021\""
        + "},"
        + "\"phoneNumber\":"
        + "["
        + "{"
        + " \"type\": \"home\","
        + "\"number\": \"212 555-1234\""
        + "},"
        + "{"
        + " \"type\": \"fax\","
        + "\"number\": \"646 555-4567\""
        + "}"
        + "]"
        + "}";
    region.put("jsondoc2", JSONFormatter.fromJSON(jsonCustomer2));
    System.out.println("JSON documents added into Cache: " + jsonCustomer2);
    System.out.println(region.get("jsondoc2"));
    System.out.println();
  }

  private HashSet getResults(LuceneQuery query, String regionName) throws LuceneQueryException {
    if (query == null) {
      return null;
    }

    PageableLuceneQueryResults<Object, Object> results = query.findPages();
    if (results.size() >0 ) {
      System.out.println("Search found "+results.size()+" pages in "+regionName);
    }

    HashSet values = new HashSet<>();
    int pageno = 0;
    if (results.size() < 20) {
    final AtomicInteger cnt = new AtomicInteger(0);
    while(results.hasNext()) {
      if (query.getPageSize() != 0) {
//        System.out.println("Page:"+pageno+" starts here ------------");
      }
      results.next().stream()
      .forEach(struct -> {
        Object value = struct.getValue();
        if (value instanceof PdxInstance) {
          PdxInstance pdx = (PdxInstance)value;
          Object revenueObj = pdx.getField("revenue");
          String jsonString = JSONFormatter.toJSON(pdx);
          System.out.println("Found a json object:"+jsonString);
          values.add(pdx);
        } else {
          System.out.println("No: "+cnt.get()+":key="+struct.getKey()+",value="+value+",score="+struct.getScore());
          values.add(value);
        }
        cnt.incrementAndGet();
      });
      if (query.getPageSize() != 0) {
//        System.out.println("Page:"+pageno+" ends here, press any key to show next page ------------");
        try {
          int c = System.in.read();
        }
        catch (IOException e) {
          // TODO Auto-generated catch block
          e.printStackTrace();
        }
      }
      pageno++;
    }
    }
    System.out.println("Search found "+values.size()+" results in "+regionName);
    return values;
  }

  private HashSet queryByStringQueryParser(String indexName, String regionName, String queryString, int pageSize, String defaultField) throws LuceneQueryException {
    System.out.println("\nQuery string is "+queryString+", default field is "+defaultField);
    HashSet results = null;
    long then = System.currentTimeMillis();
//  for (int i=0; i<100; i++) {

    LuceneQuery query = service.createLuceneQueryFactory().setPageSize(pageSize).create(indexName, regionName, queryString, defaultField);

    results = getResults(query, regionName);
//  }
    System.out.println("Query took "+(System.currentTimeMillis() - then));
    return results;
  }

  private void queryByIntRange(String indexName, String regionName, String fieldName, int lowerValue, int upperValue) throws LuceneQueryException {
    System.out.println("\nQuery range is:"+fieldName+":["+lowerValue+" TO "+upperValue+"]");
    long then = System.currentTimeMillis();
    IntRangeQueryProvider provider = new IntRangeQueryProvider(fieldName, lowerValue, upperValue);
    LuceneQuery query = service.createLuceneQueryFactory().create(indexName, regionName, provider);

    HashSet results = getResults(query, regionName);
    System.out.println("Query took "+(System.currentTimeMillis() - then));
  }
  
}
