Part-6: create index after region with data
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

step 2:
On another window, feed some data:
./gradlew run 
Note: It specified: prog.service.LUCENE_REINDEX = true in source code.

step 3:
create region --name=Person --type=PARTITION_REDUNDANT_PERSISTENT
create region --name=Customer --type=PARTITION_REDUNDANT_PERSISTENT

create lucene index --name=personIndex --region=/Person --field=name,revenue,revenue_float,revenue_double,revenue_long

create lucene index --name=customerIndex --region=/Customer --field=name,revenue,contacts.revenue --serializer=org.apache.geode.cache.lucene.FlatFormatSerializer

step 4: use StringQueryProvider with PointsConfig
gfsh>search lucene --region=/Person --name=personIndex --queryString="revenue=763000" --defaultField=name
 key   |                                                                                       value                                                                                       | score
------ | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | -----
key763 | Person{name='Tom763 Zhou', email='tzhou763@example.com', address='763 Lindon St, Portland_OR_97763', revenue=763000, homepage='Page{id=763, title='PivotalPage763 developer', c.. | 1
Note: exact match for an integer field

gfsh>search lucene --region=/Person --name=personIndex --queryString="revenue=763000 revenue=764000" --defaultField=name
 key   |                                                                                       value                                                                                       | score
------ | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | -----
key763 | Person{name='Tom763 Zhou', email='tzhou763@example.com', address='763 Lindon St, Portland_OR_97763', revenue=763000, homepage='Page{id=763, title='PivotalPage763 developer', c.. | 1
key764 | Person{name='Tom764 Zhou', email='tzhou764@example.com', address='764 Lindon St, Portland_OR_97764', revenue=764000, homepage='Page{id=764, title='PivotalPage764 developer', c.. | 1
Note: use 2 SHOULD conditions, which is equivalent to "A OR B"


gfsh>search lucene --region=/Person --name=personIndex --queryString="+revenue>763000 +revenue<766000" --defaultField=name
 key   |                                                                                       value                                                                                       | score
------ | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | -----
key765 | Person{name='Tom765 Zhou', email='tzhou765@example.com', address='765 Lindon St, Portland_OR_97765', revenue=765000, homepage='Page{id=765, title='PivotalPage765 developer', c.. | 2
key764 | Person{name='Tom764 Zhou', email='tzhou764@example.com', address='764 Lindon St, Portland_OR_97764', revenue=764000, homepage='Page{id=764, title='PivotalPage764 developer', c.. | 2
Note: use 2 MUST conditions, which is equivalent to "A AND B"

gfsh>search lucene --region=/Person --name=personIndex --queryString="+revenue>=763000 +revenue<=766000" --defaultField=name
 key   |                                                                               value                                                                                | score
------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------------ | -----
key766 | Person{name='Tom766 Zhou', email='tzhou766@example.com', address='766 Lindon St, Portland_OR_97766', revenue=766000, homepage='Page{id=766, title='PivotalPage76.. | 2
key763 | Person{name='Tom763 Zhou', email='tzhou763@example.com', address='763 Lindon St, Portland_OR_97763', revenue=763000, homepage='Page{id=763, title='PivotalPage76.. | 2
key764 | Person{name='Tom764 Zhou', email='tzhou764@example.com', address='764 Lindon St, Portland_OR_97764', revenue=764000, homepage='Page{id=764, title='PivotalPage76.. | 2
key765 | Person{name='Tom765 Zhou', email='tzhou765@example.com', address='765 Lindon St, Portland_OR_97765', revenue=765000, homepage='Page{id=765, title='PivotalPage76.. | 2
Note: >=, <= are valid syntax for inclusive condition 

gfsh>search lucene --region=/Person --name=personIndex --queryString="revenue:[763000 TO 766000]" --defaultField=name
Note: the same as --queryString="+revenue>=763000 +revenue<=766000"
gfsh>search lucene --region=/Person --name=personIndex --queryString="revenue_float:[763000.0 TO 766000.0]" --defaultField=name
gfsh>search lucene --region=/Person --name=personIndex --queryString="revenue_double:[763000 TO 766000]" --defaultField=name
gfsh>search lucene --region=/Person --name=personIndex --queryString="revenue_long:[763000 TO 766000]" --defaultField=name
Note: All the 4 numeric types(int, float, double, long) are supported

gfsh>search lucene --region=/Person --name=personIndex --queryString="+revenue_long:[763000 TO 766000] +revenue_float:[762000 TO 765000]" --defaultField=name
 key   |                                                                                                                         value                                                                                                                          | score
------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ | -----
key763 | Person{name='Tom763 Zhou', email='tzhou763@example.com', address='763 Lindon St, Portland_OR_97763', revenue=763000, homepage='Page{id=763, title='PivotalPage763 developer', content='Hello world no 763'}', phoneNumbers='[5036331763, 5036341763]'} | 2
key764 | Person{name='Tom764 Zhou', email='tzhou764@example.com', address='764 Lindon St, Portland_OR_97764', revenue=764000, homepage='Page{id=764, title='PivotalPage764 developer', content='Hello world no 764'}', phoneNumbers='[5036331764, 5036341764]'} | 2
key765 | Person{name='Tom765 Zhou', email='tzhou765@example.com', address='765 Lindon St, Portland_OR_97765', revenue=765000, homepage='Page{id=765, title='PivotalPage765 developer', content='Hello world no 765'}', phoneNumbers='[5036331765, 5036341765]'} | 2

Note: Combination query will return a subset.

gfsh>search lucene --region=/Person --name=personIndex --queryString="revenue<2000 revenue>9997000" --defaultField=name
  key   |                                                                                      value                                                                                       | score
------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | -----
key9999 | Person{name='Tom9999 Zhou', email='tzhou9999@example.com', address='9999 Lindon St, Portland_OR_106999', revenue=9999000, homepage='Page{id=9999, title='PivotalPage9999 devel.. | 1
key1    | Person{name='Tom1 Zhou', email='tzhou1@example.com', address='1 Lindon St, Portland_OR_97001', revenue=1000, homepage='Page{id=1, title='PivotalPage1 developer', content='Hel.. | 1
key0    | Person{name='Tom0 Zhou', email='tzhou0@example.com', address='0 Lindon St, Portland_OR_97000', revenue=0, homepage='Page{id=0, title='PivotalPage0 manager', content='Hello wo.. | 1
key9998 | Person{name='Tom9998 Zhou', email='tzhou9998@example.com', address='9998 Lindon St, Portland_OR_106998', revenue=9998000, homepage='Page{id=9998, title='PivotalPage9998 devel.. | 1
Note: Another example of 2 MUST conditions.

gfsh>search lucene --region=/Person --name=personIndex --queryString="revenue<2000 revenue>9997000 +name=Tom999*" --defaultField=name
  key   |                                                                                      value                                                                                       | score
------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | -----
key9998 | Person{name='Tom9998 Zhou', email='tzhou9998@example.com', address='9998 Lindon St, Portland_OR_106998', revenue=9998000, homepage='Page{id=9998, title='PivotalPage9998 devel.. | 2
key9999 | Person{name='Tom9999 Zhou', email='tzhou9999@example.com', address='9999 Lindon St, Portland_OR_106999', revenue=9999000, homepage='Page{id=9999, title='PivotalPage9999 devel.. | 2
key9997 | Person{name='Tom9997 Zhou', email='tzhou9997@example.com', address='9997 Lindon St, Portland_OR_106997', revenue=9997000, homepage='Page{id=9997, title='PivotalPage9997 devel.. | 1
key9994 | Person{name='Tom9994 Zhou', email='tzhou9994@example.com', address='9994 Lindon St, Portland_OR_106994', revenue=9994000, homepage='Page{id=9994, title='PivotalPage9994 devel.. | 1
key9992 | Person{name='Tom9992 Zhou', email='tzhou9992@example.com', address='9992 Lindon St, Portland_OR_106992', revenue=9992000, homepage='Page{id=9992, title='PivotalPage9992 devel.. | 1
key9990 | Person{name='Tom9990 Zhou', email='tzhou9990@example.com', address='9990 Lindon St, Portland_OR_106990', revenue=9990000, homepage='Page{id=9990, title='PivotalPage9990 manag.. | 1
key9993 | Person{name='Tom9993 Zhou', email='tzhou9993@example.com', address='9993 Lindon St, Portland_OR_106993', revenue=9993000, homepage='Page{id=9993, title='PivotalPage9993 devel.. | 1
key999  | Person{name='Tom999 Zhou', email='tzhou999@example.com', address='999 Lindon St, Portland_OR_97999', revenue=999000, homepage='Page{id=999, title='PivotalPage999 developer', .. | 1
key9995 | Person{name='Tom9995 Zhou', email='tzhou9995@example.com', address='9995 Lindon St, Portland_OR_106995', revenue=9995000, homepage='Page{id=9995, title='PivotalPage9995 devel.. | 1
key9996 | Person{name='Tom9996 Zhou', email='tzhou9996@example.com', address='9996 Lindon St, Portland_OR_106996', revenue=9996000, homepage='Page{id=9996, title='PivotalPage9996 devel.. | 1
key9991 | Person{name='Tom9991 Zhou', email='tzhou9991@example.com', address='9991 Lindon St, Portland_OR_106991', revenue=9991000, homepage='Page{id=9991, title='PivotalPage9991 devel.. | 1
Note: when there're both SHOULD and MUST conditions, SHOULD condition is ignored

gfsh>search lucene --region=/Person --name=personIndex --queryString="revenue<2000 revenue>9997000 -name=Tom9998*" --defaultField=name
  key   |                                                                                      value                                                                                       | score
------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | -----
key0    | Person{name='Tom0 Zhou', email='tzhou0@example.com', address='0 Lindon St, Portland_OR_97000', revenue=0, homepage='Page{id=0, title='PivotalPage0 manager', content='Hello wo.. | 1
key1    | Person{name='Tom1 Zhou', email='tzhou1@example.com', address='1 Lindon St, Portland_OR_97001', revenue=1000, homepage='Page{id=1, title='PivotalPage1 developer', content='Hel.. | 1
key9999 | Person{name='Tom9999 Zhou', email='tzhou9999@example.com', address='9999 Lindon St, Portland_OR_106999', revenue=9999000, homepage='Page{id=9999, title='PivotalPage9999 devel.. | 1
Note: 1 NOT condition will reduce result from 2 SHOULD conditions's query results

gfsh>search lucene --region=/Person --name=personIndex --queryString="revenue<2000 revenue>9997000 -Tom9999*" --defaultField=name
  key   |                                                                               value                                                                               | score
------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------- | -----
key9998 | Person{name='Tom9998 Zhou', email='tzhou9998@example.com', address='9998 Lindon St, Portland_OR_106998', revenue=9998000, homepage='Page{id=9998, title='Pivota.. | 1
key1    | Person{name='Tom1 Zhou', email='tzhou1@example.com', address='1 Lindon St, Portland_OR_97001', revenue=1000, homepage='Page{id=1, title='PivotalPage1 developer.. | 1
key0    | Person{name='Tom0 Zhou', email='tzhou0@example.com', address='0 Lindon St, Portland_OR_97000', revenue=0, homepage='Page{id=0, title='PivotalPage0 manager', co.. | 1
Note: default field takes effect


gfsh>search lucene --region=/Person --name=personIndex --queryString="revenue<2000 revenue>9997000 -name:Tom9999*" --defaultField=name
  key   |                                                                               value                                                                               | score
------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------- | -----
key9998 | Person{name='Tom9998 Zhou', email='tzhou9998@example.com', address='9998 Lindon St, Portland_OR_106998', revenue=9998000, homepage='Page{id=9998, title='Pivota.. | 1
key1    | Person{name='Tom1 Zhou', email='tzhou1@example.com', address='1 Lindon St, Portland_OR_97001', revenue=1000, homepage='Page{id=1, title='PivotalPage1 developer.. | 1
key0    | Person{name='Tom0 Zhou', email='tzhou0@example.com', address='0 Lindon St, Portland_OR_97000', revenue=0, homepage='Page{id=0, title='PivotalPage0 manager', co.. | 1
Note: name:Tom999* is equivalent to name=Tom999*

gfsh>search lucene --region=/Person --name=personIndex --queryString="revenue=400000" --defaultField=name
  key    |                                                                                                                           value                                                                                                                            | score
-------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | -----
jsondoc1 | PDX[8776019,__GEMFIRE_JSON]{address=PDX[16524384,__GEMFIRE_JSON]{city=New York, postalCode=10021, state=NY, streetAddress=21 2nd Street}, age=25, lastName=Smith, name=Tom9_JSON, phoneNumber=[PDX[16427762,__GEMFIRE_JSON]{number=212 555-1234, type=ho.. | 1
key400   | Person{name='Tom400 Zhou', email='tzhou400@example.com', address='400 Lindon St, Portland_OR_97400', revenue=400000, homepage='Page{id=400, title='PivotalPage400 manager', content='Hello world no 400'}', phoneNumbers='[5036331400, 5036341400]'}       | 1

Note: numeric field in JSON object can also be searched.

gfsh>search lucene --region=/Customer --name=customerIndex --queryString="revenue:[762000 TO 765000]" --defaultField=name
Note: should display 4 entries 

gfsh>search lucene --region=/Customer --name=customerIndex --queryString="contacts.revenue:[763000 TO 766000]" --defaultField=name
Note: should display 4 entries 

gfsh>search lucene --region=/Customer --name=customerIndex --queryString="+contacts.revenue:[763000 TO 766000] +revenue:[762000 TO 765000]" --defaultField=name
Note: should display 3 entries

