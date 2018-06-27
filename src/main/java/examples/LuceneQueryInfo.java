package examples;

import java.io.Serializable;

import org.apache.geode.cache.lucene.LuceneQueryFactory;

public class LuceneQueryInfo implements Serializable {
  private static final long serialVersionUID = 1L;
  private String indexName;
  private String regionPath;
  private String queryString;
  private String defaultField;
  private int limit;
  private boolean keysOnly;

  public LuceneQueryInfo(final String indexName, final String regionPath, final String queryString,
      final String defaultField, final int limit, final boolean keysOnly) {
    this.indexName = indexName;
    this.regionPath = regionPath;
    this.queryString = queryString;
    this.defaultField = defaultField;
    this.limit = limit;
    this.keysOnly = keysOnly;
  }

  public String getIndexName() {
    return indexName;
  }

  public String getRegionPath() {
    return regionPath;
  }

  public String getQueryString() {
    return queryString;
  }

  public String getDefaultField() {
    return defaultField;
  }

  public int getLimit() {
    if (limit == -1)
      return LuceneQueryFactory.DEFAULT_LIMIT;
    else
      return limit;
  }

  public boolean getKeysOnly() {
    return keysOnly;
  }

  @Override
  public String toString() {
    return (""+indexName+","+regionPath+","+queryString+","+defaultField);
  }
}
