[sql]
select
    sum(l_extendedprice* (1 - l_discount)) as revenue
from
    lineitem,
    part
where
    (
                p_partkey = l_partkey
            and p_brand = 'Brand#45'
            and p_container in ('SM CASE', 'SM BOX', 'SM PACK', 'SM PKG')
            and l_quantity >= 5 and l_quantity <= 5 + 10
            and p_size between 1 and 5
            and l_shipmode in ('AIR', 'AIR REG')
            and l_shipinstruct = 'DELIVER IN PERSON'
        )
   or
    (
                p_partkey = l_partkey
            and p_brand = 'Brand#11'
            and p_container in ('MED BAG', 'MED BOX', 'MED PKG', 'MED PACK')
            and l_quantity >= 15 and l_quantity <= 15 + 10
            and p_size between 1 and 10
            and l_shipmode in ('AIR', 'AIR REG')
            and l_shipinstruct = 'DELIVER IN PERSON'
        )
   or
    (
                p_partkey = l_partkey
            and p_brand = 'Brand#21'
            and p_container in ('LG CASE', 'LG BOX', 'LG PACK', 'LG PKG')
            and l_quantity >= 25 and l_quantity <= 25 + 10
            and p_size between 1 and 15
            and l_shipmode in ('AIR', 'AIR REG')
            and l_shipinstruct = 'DELIVER IN PERSON'
    ) ;
[fragment]
PLAN FRAGMENT 0
OUTPUT EXPRS:29: sum(28: expr)
PARTITION: UNPARTITIONED

RESULT SINK

9:AGGREGATE (merge finalize)
|  output: sum(29: sum(28: expr))
|  group by:
|  use vectorized: true
|
8:EXCHANGE
use vectorized: true

PLAN FRAGMENT 1
OUTPUT EXPRS:
PARTITION: HASH_PARTITIONED: 2: L_PARTKEY

STREAM DATA SINK
EXCHANGE ID: 08
UNPARTITIONED

7:AGGREGATE (update serialize)
|  output: sum(28: expr)
|  group by:
|  use vectorized: true
|
6:Project
|  <slot 28> : 6: L_EXTENDEDPRICE * 1.0 - 7: L_DISCOUNT
|  use vectorized: true
|
5:HASH JOIN
|  join op: INNER JOIN (PARTITIONED)
|  hash predicates:
|  colocate: false, reason:
|  equal join conjunct: 2: L_PARTKEY = 18: P_PARTKEY
|  other join predicates: (((((21: P_BRAND = 'Brand#45') AND (24: P_CONTAINER IN ('SM CASE', 'SM BOX', 'SM PACK', 'SM PKG'))) AND ((5: L_QUANTITY >= 5.0) AND (5: L_QUANTITY <= 15.0))) AND (23: P_SIZE <= 5)) OR ((((21: P_BRAND = 'Brand#11') AND (24: P_CONTAINER IN ('MED BAG', 'MED BOX', 'MED PKG', 'MED PACK'))) AND ((5: L_QUANTITY >= 15.0) AND (5: L_QUANTITY <= 25.0))) AND (23: P_SIZE <= 10))) OR ((((21: P_BRAND = 'Brand#21') AND (24: P_CONTAINER IN ('LG CASE', 'LG BOX', 'LG PACK', 'LG PKG'))) AND ((5: L_QUANTITY >= 25.0) AND (5: L_QUANTITY <= 35.0))) AND (23: P_SIZE <= 15))
|  use vectorized: true
|
|----4:EXCHANGE
|       use vectorized: true
|
2:EXCHANGE
use vectorized: true

PLAN FRAGMENT 2
OUTPUT EXPRS:
PARTITION: RANDOM

STREAM DATA SINK
EXCHANGE ID: 04
HASH_PARTITIONED: 18: P_PARTKEY

3:OlapScanNode
TABLE: part
PREAGGREGATION: ON
PREDICATES: 21: P_BRAND IN ('Brand#45', 'Brand#11', 'Brand#21'), 23: P_SIZE <= 15, 24: P_CONTAINER IN ('SM CASE', 'SM BOX', 'SM PACK', 'SM PKG', 'MED BAG', 'MED BOX', 'MED PKG', 'MED PACK', 'LG CASE', 'LG BOX', 'LG PACK', 'LG PKG'), 23: P_SIZE >= 1
partitions=1/1
rollup: part
tabletRatio=10/10
tabletList=10190,10192,10194,10196,10198,10200,10202,10204,10206,10208
cardinality=20000000
avgRowSize=32.0
numNodes=0
use vectorized: true

PLAN FRAGMENT 3
OUTPUT EXPRS:
PARTITION: RANDOM

STREAM DATA SINK
EXCHANGE ID: 02
HASH_PARTITIONED: 2: L_PARTKEY

1:Project
|  <slot 2> : 2: L_PARTKEY
|  <slot 5> : 5: L_QUANTITY
|  <slot 6> : 6: L_EXTENDEDPRICE
|  <slot 7> : 7: L_DISCOUNT
|  use vectorized: true
|
0:OlapScanNode
TABLE: lineitem
PREAGGREGATION: ON
PREDICATES: 5: L_QUANTITY >= 5.0, 5: L_QUANTITY <= 35.0, 15: L_SHIPMODE IN ('AIR', 'AIR REG'), 14: L_SHIPINSTRUCT = 'DELIVER IN PERSON'
partitions=1/1
rollup: lineitem
tabletRatio=20/20
tabletList=10213,10215,10217,10219,10221,10223,10225,10227,10229,10231 ...
cardinality=42857142
avgRowSize=67.0
numNodes=0
use vectorized: true
[end]

