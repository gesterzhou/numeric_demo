step 0: build special version of geode
git clone git@github.com:gesterzhou/geode.git
cd geode
git checkout feature/GEODE-QueryProvider
./gradlew build -x javadoc -x rat -x spotlessJavaCheck -Dskip.tests=true install
export GEMFIRE=`pwd`"/geode-assembly/build/install/apache-geode"

step 1:
$GEMFIRE/bin/gfsh

set variable --name=APP_QUIET_EXECUTION --value=true
start locator --name=locator1 --port=12345

configure pdx --disk-store=DEFAULT --read-serialized=true
start server --name=server50505 --server-port=50505 --locators=localhost[12345] --group=group50505 --J='-Dgemfire.luceneReindex=true'
deploy --jar=./build/libs/numeric_demo-0.0.1.jar --group=group50505
create lucene index --name=personIndex --region=/Person --field=name,revenue,revenue_float,revenue_double,revenue_long
create lucene index --name=customerIndex --region=/Customer --field=name,revenue,contacts.revenue --serializer=org.apache.geode.cache.lucene.FlatFormatSerializer
create region --name=Person --type=PARTITION_REDUNDANT_PERSISTENT
create region --name=Customer --type=PARTITION_REDUNDANT_PERSISTENT

step 2: feed
On another window, feed some data:
./gradlew run 
Note: It specified: prog.service.LUCENE_REINDEX = true in source code.

step 3: query
# SHOULD
search lucene --region=/Person --name=personIndex --queryString="revenue=763000 revenue=764000" --defaultField=name

# NOT and mixed
search lucene --region=/Person --name=personIndex --queryString="revenue<2000 revenue>9997000 -name:Tom9999*" --defaultField=name

# JSON/PDX
search lucene --region=/Person --name=personIndex --queryString="revenue=400000" --defaultField=name

# Other numeric type such as float is supported
search lucene --region=/Person --name=personIndex --queryString="revenue_float:[763000.0 TO 766000.0]" --defaultField=name

# Nested object
search lucene --region=/Customer --name=customerIndex --queryString="+contacts.revenue:[763000 TO 766000] +revenue:[762000 TO 765000]" --defaultField=name

