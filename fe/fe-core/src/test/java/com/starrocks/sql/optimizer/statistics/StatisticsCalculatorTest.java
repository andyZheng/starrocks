// This file is licensed under the Elastic License 2.0. Copyright 2021 StarRocks Limited.

package com.starrocks.sql.optimizer.statistics;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.starrocks.catalog.Catalog;
import com.starrocks.catalog.Column;
import com.starrocks.catalog.OlapTable;
import com.starrocks.catalog.Partition;
import com.starrocks.catalog.Type;
import com.starrocks.qe.ConnectContext;
import com.starrocks.sql.optimizer.ExpressionContext;
import com.starrocks.sql.optimizer.Group;
import com.starrocks.sql.optimizer.GroupExpression;
import com.starrocks.sql.optimizer.Memo;
import com.starrocks.sql.optimizer.OptimizerContext;
import com.starrocks.sql.optimizer.Utils;
import com.starrocks.sql.optimizer.base.ColumnRefFactory;
import com.starrocks.sql.optimizer.base.ColumnRefSet;
import com.starrocks.sql.optimizer.dump.MockDumpInfo;
import com.starrocks.sql.optimizer.operator.logical.LogicalAggregationOperator;
import com.starrocks.sql.optimizer.operator.logical.LogicalOlapScanOperator;
import com.starrocks.sql.optimizer.operator.logical.LogicalUnionOperator;
import com.starrocks.sql.optimizer.operator.scalar.BinaryPredicateOperator;
import com.starrocks.sql.optimizer.operator.scalar.CallOperator;
import com.starrocks.sql.optimizer.operator.scalar.ColumnRefOperator;
import com.starrocks.sql.optimizer.operator.scalar.ConstantOperator;
import com.starrocks.utframe.StarRocksAssert;
import com.starrocks.utframe.UtFrameUtils;
import mockit.Expectations;
import mockit.Mocked;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class StatisticsCalculatorTest {
    // use a unique dir so that it won't be conflict with other unit test which
    // may also start a Mocked Frontend
    private static String runningDir = "fe/mocked/StatisticsCalculatorTest/" + UUID.randomUUID().toString() + "/";
    private static ConnectContext connectContext;
    private static OptimizerContext optimizerContext;
    private static ColumnRefFactory columnRefFactory;
    private static StarRocksAssert starRocksAssert;

    @BeforeClass
    public static void beforeClass() throws Exception {
        UtFrameUtils.createMinStarRocksCluster(runningDir);
        // create connect context
        connectContext = UtFrameUtils.createDefaultCtx();
        columnRefFactory = new ColumnRefFactory();
        optimizerContext = new OptimizerContext(new Memo(), columnRefFactory, connectContext.getSessionVariable(),
                connectContext.getDumpInfo());

        starRocksAssert = new StarRocksAssert(connectContext);
        String DB_NAME = "test";
        starRocksAssert.withDatabase(DB_NAME).useDatabase(DB_NAME);
    }

    @AfterClass
    public static void tearDown() {
        File file = new File(runningDir);
        file.delete();
    }

    @Test
    public void testLogicalAggregationRowCount() throws Exception {
        ColumnRefOperator v1 = columnRefFactory.create("v1", Type.INT, true);
        ColumnRefOperator v2 = columnRefFactory.create("v2", Type.INT, true);

        List<ColumnRefOperator> groupByColumns = Lists.newArrayList(v1);
        Map<ColumnRefOperator, CallOperator> aggCall = new HashMap<>();

        Statistics.Builder builder = Statistics.builder();
        builder.setOutputRowCount(10000);
        builder.addColumnStatistics(ImmutableMap.of(v1, new ColumnStatistic(0, 100, 0, 10, 50)));
        builder.addColumnStatistics(ImmutableMap.of(v2, new ColumnStatistic(0, 100, 0, 10, 50)));

        Group childGroup = new Group(0);
        childGroup.setStatistics(builder.build());

        LogicalAggregationOperator aggNode = new LogicalAggregationOperator(groupByColumns, aggCall);
        GroupExpression groupExpression = new GroupExpression(aggNode, Lists.newArrayList(childGroup));
        groupExpression.setGroup(new Group(1));
        ExpressionContext expressionContext = new ExpressionContext(groupExpression);
        StatisticsCalculator statisticsCalculator = new StatisticsCalculator(expressionContext, new ColumnRefSet(),
                columnRefFactory, new MockDumpInfo());
        statisticsCalculator.estimatorStats();
        Assert.assertEquals(50, expressionContext.getStatistics().getOutputRowCount(), 0.001);

        groupByColumns = Lists.newArrayList(v1, v2);
        aggNode = new LogicalAggregationOperator(groupByColumns, aggCall);
        groupExpression = new GroupExpression(aggNode, Lists.newArrayList(childGroup));
        groupExpression.setGroup(new Group(1));
        expressionContext = new ExpressionContext(groupExpression);
        statisticsCalculator = new StatisticsCalculator(expressionContext, new ColumnRefSet(),
                columnRefFactory, new MockDumpInfo());
        statisticsCalculator.estimatorStats();
        Assert.assertEquals(
                50 * 50 * Math.pow(StatisticsEstimateCoefficient.UNKNOWN_GROUP_BY_CORRELATION_COEFFICIENT, 2),
                expressionContext.getStatistics().getOutputRowCount(), 0.001);
    }

    @Test
    public void testLogicalUnion() throws Exception {
        // child 1 output column
        ColumnRefOperator v1 = columnRefFactory.create("v1", Type.INT, true);
        ColumnRefOperator v2 = columnRefFactory.create("v2", Type.INT, true);
        // child 2 output column
        ColumnRefOperator v3 = columnRefFactory.create("v3", Type.INT, true);
        ColumnRefOperator v4 = columnRefFactory.create("v4", Type.INT, true);
        // union node output column
        ColumnRefOperator v5 = columnRefFactory.create("v3", Type.INT, true);
        ColumnRefOperator v6 = columnRefFactory.create("v4", Type.INT, true);
        // child 1 statistics
        Statistics.Builder childBuilder1 = Statistics.builder();
        childBuilder1.setOutputRowCount(10000);
        childBuilder1.addColumnStatistics(ImmutableMap.of(v1, new ColumnStatistic(0, 100, 0, 10, 50)));
        childBuilder1.addColumnStatistics(ImmutableMap.of(v2, new ColumnStatistic(0, 50, 0, 10, 50)));
        Group childGroup1 = new Group(0);
        childGroup1.setStatistics(childBuilder1.build());
        // child 2 statistics
        Statistics.Builder childBuilder2 = Statistics.builder();
        childBuilder2.setOutputRowCount(20000);
        childBuilder2.addColumnStatistics(ImmutableMap.of(v3, new ColumnStatistic(100, 200, 0, 10, 50)));
        childBuilder2.addColumnStatistics(ImmutableMap.of(v4, new ColumnStatistic(0, 100, 0, 10, 100)));
        Group childGroup2 = new Group(1);
        childGroup2.setStatistics(childBuilder2.build());
        // construct group expression
        LogicalUnionOperator unionOperator = new LogicalUnionOperator(Lists.newArrayList(v5, v6),
                Lists.newArrayList(Lists.newArrayList(v1, v2), Lists.newArrayList(v3, v4)), true);
        GroupExpression groupExpression =
                new GroupExpression(unionOperator, Lists.newArrayList(childGroup1, childGroup2));
        groupExpression.setGroup(new Group(2));
        ExpressionContext expressionContext = new ExpressionContext(groupExpression);
        StatisticsCalculator statisticsCalculator = new StatisticsCalculator(expressionContext, new ColumnRefSet(),
                columnRefFactory, new MockDumpInfo());
        statisticsCalculator.estimatorStats();

        ColumnStatistic columnStatisticV5 = expressionContext.getStatistics().getColumnStatistic(v5);
        ColumnStatistic columnStatisticV6 = expressionContext.getStatistics().getColumnStatistic(v6);
        Assert.assertEquals(30000, expressionContext.getStatistics().getOutputRowCount(), 0.001);
        Assert.assertEquals(new StatisticRangeValues(0, 200, 99), StatisticRangeValues.from(columnStatisticV5));
        Assert.assertEquals(new StatisticRangeValues(0, 100, 100), StatisticRangeValues.from(columnStatisticV6));
    }

    @Test
    public void testLogicalOlapTableScan() throws Exception {
        starRocksAssert.withTable("CREATE TABLE `test_all_type` (\n" +
                "  `t1a` varchar(20) NULL COMMENT \"\",\n" +
                "  `t1b` smallint(6) NULL COMMENT \"\",\n" +
                "  `t1c` int(11) NULL COMMENT \"\",\n" +
                "  `t1d` bigint(20) NULL COMMENT \"\",\n" +
                "  `t1e` float NULL COMMENT \"\",\n" +
                "  `t1f` double NULL COMMENT \"\",\n" +
                "  `t1g` bigint(20) NULL COMMENT \"\",\n" +
                "  `id_datetime` datetime NULL COMMENT \"\",\n" +
                "  `id_date` date NULL COMMENT \"\", \n" +
                "  `id_decimal` decimal(10,2) NULL COMMENT \"\"\n" +
                ") ENGINE=OLAP\n" +
                "DUPLICATE KEY(`t1a`)\n" +
                "COMMENT \"OLAP\"\n" +
                "DISTRIBUTED BY HASH(`t1a`) BUCKETS 3\n" +
                "PROPERTIES (\n" +
                "\"replication_num\" = \"1\",\n" +
                "\"in_memory\" = \"false\",\n" +
                "\"storage_format\" = \"DEFAULT\"\n" +
                ");");

        Catalog catalog = connectContext.getCatalog();
        OlapTable table = (OlapTable) catalog.getDb("default_cluster:test").getTable("test_all_type");
        Collection<Partition> partitions = table.getPartitions();
        List<Long> partitionIds =
                partitions.stream().mapToLong(partition -> partition.getId()).boxed().collect(Collectors.toList());
        for (Partition partition : partitions) {
            partition.getBaseIndex().setRowCount(1000);
        }

        LogicalOlapScanOperator olapScanOperator = new LogicalOlapScanOperator(table);
        olapScanOperator.getSelectedPartitionId().addAll(partitionIds);
        GroupExpression groupExpression = new GroupExpression(olapScanOperator, Lists.newArrayList());
        groupExpression.setGroup(new Group(0));
        ExpressionContext expressionContext = new ExpressionContext(groupExpression);
        StatisticsCalculator statisticsCalculator = new StatisticsCalculator(expressionContext, new ColumnRefSet(),
                columnRefFactory, new MockDumpInfo());
        statisticsCalculator.estimatorStats();
        Assert.assertEquals(1000 * partitions.size(), expressionContext.getStatistics().getOutputRowCount(), 0.001);
        starRocksAssert.dropTable("test_all_type");
    }

    @Test
    public void testLogicalOlapTableScanPartitionPrune1(@Mocked CachedStatisticStorage cachedStatisticStorage)
            throws Exception {
        starRocksAssert.withTable("CREATE TABLE `test_all_type` (\n" +
                "  `t1a` varchar(20) NULL COMMENT \"\",\n" +
                "  `t1b` smallint(6) NULL COMMENT \"\",\n" +
                "  `t1c` int(11) NULL COMMENT \"\",\n" +
                "  `t1d` bigint(20) NULL COMMENT \"\",\n" +
                "  `t1e` float NULL COMMENT \"\",\n" +
                "  `t1f` double NULL COMMENT \"\",\n" +
                "  `t1g` bigint(20) NULL COMMENT \"\",\n" +
                "  `id_datetime` datetime NULL COMMENT \"\",\n" +
                "  `id_date` date NULL COMMENT \"\", \n" +
                "  `id_decimal` decimal(10,2) NULL COMMENT \"\"\n" +
                ") ENGINE=OLAP\n" +
                "DUPLICATE KEY(`t1a`)\n" +
                "PARTITION BY RANGE (id_date)\n" +
                "(\n" +
                "PARTITION p1 VALUES LESS THAN (\"2014-01-01\"),\n" +
                "PARTITION p2 VALUES LESS THAN (\"2014-06-01\"), \n" +
                "PARTITION p3 VALUES LESS THAN (\"2014-12-01\")  \n" +
                ")\n" +
                "DISTRIBUTED BY HASH(`t1a`) BUCKETS 3\n" +
                "PROPERTIES (\n" +
                "\"replication_num\" = \"1\",\n" +
                "\"in_memory\" = \"false\",\n" +
                "\"storage_format\" = \"DEFAULT\"\n" +
                ");");
        ColumnRefOperator id_date = columnRefFactory.create("id_date", Type.DATE, true);

        Catalog catalog = connectContext.getCatalog();
        OlapTable table = (OlapTable) catalog.getDb("default_cluster:test").getTable("test_all_type");

        new Expectations() {
            {
                cachedStatisticStorage.getColumnStatistic(table, "id_date");
                result = new ColumnStatistic(0, Utils.getLongFromDateTime(LocalDateTime.of(2014, 12, 01, 0, 0, 0)),
                        0, 0, 30);
                minTimes = 0;
            }
        };

        Collection<Partition> partitions = table.getPartitions();
        // select partition p1
        List<Long> partitionIds = partitions.stream().filter(partition -> partition.getName().equalsIgnoreCase("p1")).
                mapToLong(partition -> partition.getId()).boxed().collect(Collectors.toList());
        for (Partition partition : partitions) {
            partition.getBaseIndex().setRowCount(1000);
        }

        LogicalOlapScanOperator olapScanOperator = new LogicalOlapScanOperator(table,
                Lists.newArrayList(), Maps.newHashMap(), ImmutableMap.of(new Column("id_date", Type.DATE, true),
                id_date.getId()));
        olapScanOperator.getSelectedPartitionId().addAll(partitionIds);

        olapScanOperator.setPredicate(new BinaryPredicateOperator(BinaryPredicateOperator.BinaryType.EQ,
                id_date, ConstantOperator.createDate(LocalDateTime.of(2013, 12, 30, 0, 0, 0))));

        GroupExpression groupExpression = new GroupExpression(olapScanOperator, Lists.newArrayList());
        groupExpression.setGroup(new Group(0));
        ExpressionContext expressionContext = new ExpressionContext(groupExpression);
        StatisticsCalculator statisticsCalculator = new StatisticsCalculator(expressionContext, new ColumnRefSet(),
                columnRefFactory, new MockDumpInfo());
        statisticsCalculator.estimatorStats();
        // partition column count distinct values is 30 in table level, after partition prune,
        // the column statistic distinct values is 10, so the estimate row count is 1000 * (1/10)
        Assert.assertEquals(100, expressionContext.getStatistics().getOutputRowCount(), 0.001);
        ColumnStatistic columnStatistic = expressionContext.getStatistics().getColumnStatistic(id_date);
        Assert.assertEquals(Utils.getLongFromDateTime(LocalDateTime.of(2014, 1, 1, 0, 0, 0)),
                columnStatistic.getMaxValue(), 0.001);

        // select partition p2, p3
        partitionIds.clear();
        partitionIds = partitions.stream().filter(partition -> !(partition.getName().equalsIgnoreCase("p1"))).
                mapToLong(partition -> partition.getId()).boxed().collect(Collectors.toList());
        olapScanOperator = new LogicalOlapScanOperator(table,
                Lists.newArrayList(), Maps.newHashMap(), ImmutableMap.of(new Column("id_date", Type.DATE, true),
                id_date.getId()));
        olapScanOperator.setPredicate(new BinaryPredicateOperator(BinaryPredicateOperator.BinaryType.GE,
                id_date, ConstantOperator.createDate(LocalDateTime.of(2014, 5, 1, 0, 0, 0))));
        olapScanOperator.getSelectedPartitionId().addAll(partitionIds);

        groupExpression = new GroupExpression(olapScanOperator, Lists.newArrayList());
        groupExpression.setGroup(new Group(0));
        expressionContext = new ExpressionContext(groupExpression);
        statisticsCalculator = new StatisticsCalculator(expressionContext, new ColumnRefSet(),
                columnRefFactory, new MockDumpInfo());
        statisticsCalculator.estimatorStats();
        columnStatistic = expressionContext.getStatistics().getColumnStatistic(id_date);

        Assert.assertEquals(1281.4371, expressionContext.getStatistics().getOutputRowCount(), 0.001);
        Assert.assertEquals(Utils.getLongFromDateTime(LocalDateTime.of(2014, 1, 1, 0, 0, 0)),
                columnStatistic.getMinValue(), 0.001);
        Assert.assertEquals(Utils.getLongFromDateTime(LocalDateTime.of(2014, 12, 1, 0, 0, 0)),
                columnStatistic.getMaxValue(), 0.001);
        Assert.assertEquals(20, columnStatistic.getDistinctValuesCount(), 0.001);
        starRocksAssert.dropTable("test_all_type");
    }

    @Test
    public void testLogicalOlapTableScanPartitionPrune2(@Mocked CachedStatisticStorage cachedStatisticStorage)
            throws Exception {
        starRocksAssert.withTable("CREATE TABLE `test_all_type` (\n" +
                "  `t1a` varchar(20) NULL COMMENT \"\",\n" +
                "  `t1b` smallint(6) NULL COMMENT \"\",\n" +
                "  `t1c` int(11) NULL COMMENT \"\",\n" +
                "  `t1d` bigint(20) NULL COMMENT \"\",\n" +
                "  `t1e` float NULL COMMENT \"\",\n" +
                "  `t1f` double NULL COMMENT \"\",\n" +
                "  `t1g` bigint(20) NULL COMMENT \"\",\n" +
                "  `id_datetime` datetime NULL COMMENT \"\",\n" +
                "  `id_date` date NULL COMMENT \"\", \n" +
                "  `id_decimal` decimal(10,2) NULL COMMENT \"\"\n" +
                ") ENGINE=OLAP\n" +
                "DUPLICATE KEY(`t1a`)\n" +
                "PARTITION BY RANGE (id_date)\n" +
                "(\n" +
                "partition p1 values [('2020-04-23'), ('2020-04-24')),\n" +
                "partition p2 values [('2020-04-24'), ('2020-04-25')),\n" +
                "partition p3 values [('2020-04-25'), ('2020-04-26')) \n" +
                ")\n" +
                "DISTRIBUTED BY HASH(`t1a`) BUCKETS 3\n" +
                "PROPERTIES (\n" +
                "\"replication_num\" = \"1\",\n" +
                "\"in_memory\" = \"false\",\n" +
                "\"storage_format\" = \"DEFAULT\"\n" +
                ");");
        ColumnRefOperator id_date = columnRefFactory.create("id_date", Type.DATE, true);

        Catalog catalog = connectContext.getCatalog();
        OlapTable table = (OlapTable) catalog.getDb("default_cluster:test").getTable("test_all_type");

        new Expectations() {
            {
                cachedStatisticStorage.getColumnStatistic(table, "id_date");
                result = new ColumnStatistic(Utils.getLongFromDateTime(LocalDateTime.of(2020, 4, 23, 0, 0, 0)),
                        Utils.getLongFromDateTime(LocalDateTime.of(2020, 4, 25, 0, 0, 0)), 0, 0, 3);
                minTimes = 0;
            }
        };

        Collection<Partition> partitions = table.getPartitions();
        // select partition p2
        List<Long> partitionIds = partitions.stream().filter(partition -> partition.getName().equalsIgnoreCase("p2")).
                mapToLong(partition -> partition.getId()).boxed().collect(Collectors.toList());
        for (Partition partition : partitions) {
            partition.getBaseIndex().setRowCount(1000);
        }

        LogicalOlapScanOperator olapScanOperator = new LogicalOlapScanOperator(table,
                Lists.newArrayList(), Maps.newHashMap(), ImmutableMap.of(new Column("id_date", Type.DATE, true),
                id_date.getId()));
        olapScanOperator.getSelectedPartitionId().addAll(partitionIds);

        GroupExpression groupExpression = new GroupExpression(olapScanOperator, Lists.newArrayList());
        groupExpression.setGroup(new Group(0));
        ExpressionContext expressionContext = new ExpressionContext(groupExpression);
        StatisticsCalculator statisticsCalculator = new StatisticsCalculator(expressionContext, new ColumnRefSet(),
                columnRefFactory, new MockDumpInfo());
        statisticsCalculator.estimatorStats();

        Assert.assertEquals(1000, expressionContext.getStatistics().getOutputRowCount(), 0.001);
        ColumnStatistic columnStatistic = expressionContext.getStatistics().getColumnStatistic(id_date);
        Assert.assertEquals(Utils.getLongFromDateTime(LocalDateTime.of(2020, 4, 24, 0, 0, 0)),
                columnStatistic.getMinValue(), 0.001);
        Assert.assertEquals(Utils.getLongFromDateTime(LocalDateTime.of(2020, 4, 25, 0, 0, 0)),
                columnStatistic.getMaxValue(), 0.001);
        Assert.assertEquals(1, columnStatistic.getDistinctValuesCount(), 0.001);

        // select partition p2, p3
        partitionIds.clear();
        partitionIds = partitions.stream().filter(partition -> !(partition.getName().equalsIgnoreCase("p1"))).
                mapToLong(partition -> partition.getId()).boxed().collect(Collectors.toList());
        olapScanOperator = new LogicalOlapScanOperator(table,
                Lists.newArrayList(), Maps.newHashMap(), ImmutableMap.of(new Column("id_date", Type.DATE, true),
                id_date.getId()));
        olapScanOperator.setPredicate(new BinaryPredicateOperator(BinaryPredicateOperator.BinaryType.GE,
                id_date, ConstantOperator.createDate(LocalDateTime.of(2020, 04, 24, 0, 0, 0))));
        olapScanOperator.getSelectedPartitionId().addAll(partitionIds);

        groupExpression = new GroupExpression(olapScanOperator, Lists.newArrayList());
        groupExpression.setGroup(new Group(0));
        expressionContext = new ExpressionContext(groupExpression);
        statisticsCalculator = new StatisticsCalculator(expressionContext, new ColumnRefSet(),
                columnRefFactory, new MockDumpInfo());
        statisticsCalculator.estimatorStats();
        columnStatistic = expressionContext.getStatistics().getColumnStatistic(id_date);
        // has two partitions
        Assert.assertEquals(2000, expressionContext.getStatistics().getOutputRowCount(), 0.001);
        Assert.assertEquals(Utils.getLongFromDateTime(LocalDateTime.of(2020, 4, 24, 0, 0, 0)),
                columnStatistic.getMinValue(), 0.001);
        Assert.assertEquals(Utils.getLongFromDateTime(LocalDateTime.of(2020, 4, 26, 0, 0, 0)),
                columnStatistic.getMaxValue(), 0.001);
        Assert.assertEquals(2, columnStatistic.getDistinctValuesCount(), 0.001);
        starRocksAssert.dropTable("test_all_type");
    }
}
