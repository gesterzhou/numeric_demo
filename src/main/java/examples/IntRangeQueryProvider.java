package examples;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.apache.lucene.document.IntPoint;
import org.apache.lucene.search.Query;

import org.apache.geode.DataSerializer;
import org.apache.geode.cache.lucene.LuceneIndex;
import org.apache.geode.cache.lucene.LuceneQueryException;
import org.apache.geode.cache.lucene.LuceneQueryProvider;
import org.apache.geode.internal.DataSerializableFixedID;
import org.apache.geode.internal.Version;

/* Example: 
 * 
 */
public class IntRangeQueryProvider implements LuceneQueryProvider {
  String fieldName;
  int lowerValue;
  int upperValue;
  
  private transient Query luceneQuery;

  public IntRangeQueryProvider(String fieldName, int lowerValue, int upperValue) {
    this.fieldName = fieldName;
    this.lowerValue = lowerValue;
    this.upperValue = upperValue;
  }
  
  @Override
  public Query getQuery(LuceneIndex index) throws LuceneQueryException {
    if (luceneQuery == null) {
      luceneQuery = IntPoint.newRangeQuery(fieldName, lowerValue, upperValue);
    }
    System.out.println("IntRangeQueryProvider, using java serializable");
    return luceneQuery;
  }

}
