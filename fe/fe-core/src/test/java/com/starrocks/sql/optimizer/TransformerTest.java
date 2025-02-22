// This file is licensed under the Elastic License 2.0. Copyright 2021 StarRocks Limited.
package com.starrocks.sql.optimizer;

import com.starrocks.analysis.SqlParser;
import com.starrocks.analysis.SqlScanner;
import com.starrocks.analysis.StatementBase;
import com.starrocks.catalog.Catalog;
import com.starrocks.common.util.SqlParserUtils;
import com.starrocks.qe.ConnectContext;
import com.starrocks.sql.analyzer.Analyzer;
import com.starrocks.sql.analyzer.relation.Relation;
import com.starrocks.sql.optimizer.base.ColumnRefFactory;
import com.starrocks.sql.optimizer.transformer.LogicalPlan;
import com.starrocks.sql.optimizer.transformer.RelationTransformer;
import com.starrocks.utframe.StarRocksAssert;
import com.starrocks.utframe.UtFrameUtils;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.UUID;

public class TransformerTest {
    // use a unique dir so that it won't be conflict with other unit test which
    // may also start a Mocked Frontend
    private static String runningDir = "fe/mocked/TransformerTest/" + UUID.randomUUID().toString() + "/";

    private static ConnectContext connectContext;
    private static StarRocksAssert starRocksAssert;
    private static String DB_NAME = "test";

    @BeforeClass
    public static void beforeClass() throws Exception {
        UtFrameUtils.createMinStarRocksCluster(runningDir);

        // create connect context
        connectContext = UtFrameUtils.createDefaultCtx();
        starRocksAssert = new StarRocksAssert(connectContext);
        starRocksAssert.withDatabase(DB_NAME).useDatabase(DB_NAME);

        starRocksAssert.withTable("CREATE TABLE `t0` (\n" +
                "  `v1` bigint NULL COMMENT \"\",\n" +
                "  `v2` bigint NULL COMMENT \"\",\n" +
                "  `v3` bigint NULL\n" +
                ") ENGINE=OLAP\n" +
                "DUPLICATE KEY(`v1`, `v2`, v3)\n" +
                "DISTRIBUTED BY HASH(`v1`) BUCKETS 3\n" +
                "PROPERTIES (\n" +
                "\"replication_num\" = \"1\",\n" +
                "\"in_memory\" = \"false\",\n" +
                "\"storage_format\" = \"DEFAULT\"\n" +
                ");");

        starRocksAssert.withTable("CREATE TABLE `t1` (\n" +
                "  `v4` bigint NULL COMMENT \"\",\n" +
                "  `v5` bigint NULL COMMENT \"\",\n" +
                "  `v6` bigint NULL\n" +
                ") ENGINE=OLAP\n" +
                "DUPLICATE KEY(`v4`, `v5`, v6)\n" +
                "DISTRIBUTED BY HASH(`v4`) BUCKETS 3\n" +
                "PROPERTIES (\n" +
                "\"replication_num\" = \"1\",\n" +
                "\"in_memory\" = \"false\",\n" +
                "\"storage_format\" = \"DEFAULT\"\n" +
                ");");

        starRocksAssert.withTable("CREATE TABLE `t2` (\n" +
                "  `v7` bigint NULL COMMENT \"\",\n" +
                "  `v8` bigint NULL COMMENT \"\",\n" +
                "  `v9` bigint NULL\n" +
                ") ENGINE=OLAP\n" +
                "DUPLICATE KEY(`v7`, `v8`, v9)\n" +
                "DISTRIBUTED BY HASH(`v7`) BUCKETS 3\n" +
                "PROPERTIES (\n" +
                "\"replication_num\" = \"1\",\n" +
                "\"in_memory\" = \"false\",\n" +
                "\"storage_format\" = \"DEFAULT\"\n" +
                ");");

        starRocksAssert.withTable("CREATE TABLE `tall` (\n" +
                "  `ta` varchar(20) NULL COMMENT \"\",\n" +
                "  `tb` smallint(6) NULL COMMENT \"\",\n" +
                "  `tc` int(11) NULL COMMENT \"\",\n" +
                "  `td` bigint(20) NULL COMMENT \"\",\n" +
                "  `te` float NULL COMMENT \"\",\n" +
                "  `tf` double NULL COMMENT \"\",\n" +
                "  `tg` bigint(20) NULL COMMENT \"\",\n" +
                "  `th` datetime NULL COMMENT \"\",\n" +
                "  `ti` date NULL COMMENT \"\"\n" +
                ") ENGINE=OLAP\n" +
                "DUPLICATE KEY(`ta`)\n" +
                "COMMENT \"OLAP\"\n" +
                "DISTRIBUTED BY HASH(`ta`) BUCKETS 3\n" +
                "PROPERTIES (\n" +
                "\"replication_num\" = \"1\",\n" +
                "\"in_memory\" = \"false\",\n" +
                "\"storage_format\" = \"DEFAULT\"\n" +
                ");");
    }

    @AfterClass
    public static void tearDown() {
        File file = new File(runningDir);
        file.delete();
    }

    @Test
    public void testSingle() {
        runUnitTest("scan");
    }

    @Test
    public void testAggregate() {
        runUnitTest("aggregate");
    }

    @Test
    public void testSort() {
        runUnitTest("sort");
    }

    @Test
    public void testJoin() {
        runUnitTest("join");
    }

    @Test
    public void testSubquery() {
        runUnitTest("subquery");
    }

    public static void analyzeAndBuildOperator(String originStmt, String operatorString, String except) {
        try {
            SqlScanner input =
                    new SqlScanner(new StringReader(originStmt), connectContext.getSessionVariable().getSqlMode());
            SqlParser parser = new SqlParser(input);
            StatementBase statementBase = SqlParserUtils.getFirstStmt(parser);

            Analyzer analyzer = new Analyzer(Catalog.getCurrentCatalog(), connectContext);
            Relation relation = analyzer.analyze(statementBase);
            LogicalPlan logicalPlan = new RelationTransformer(new ColumnRefFactory()).transform(relation);

            OperatorStrings operatorPrinter = new OperatorStrings();
            Assert.assertEquals(operatorString.substring(0, operatorString.length() - 1),
                    operatorPrinter.printOperator(logicalPlan.getRoot()));
        } catch (Exception ex) {
            if (!except.isEmpty()) {
                Assert.assertEquals(ex.getMessage(), except);
                return;
            }
            Assert.fail("build operator fail, message: " + ex.getMessage() + ", sql: " + originStmt);
        }
    }

    private void runUnitTest(String filename) {
        String path = ClassLoader.getSystemClassLoader().getResource("sql").getPath();
        File file = new File(path + "/logical-plan/" + filename + ".sql");
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new FileReader(file));
            String sql = "";
            String result = "";
            String except = "";
            String mode = "";
            String tempStr;

            boolean comment = false;
            while ((tempStr = reader.readLine()) != null) {
                if (tempStr.startsWith("/*")) {
                    comment = true;
                }
                if (tempStr.endsWith("*/")) {
                    comment = false;
                    continue;
                }

                if (comment) {
                    continue;
                }

                if (tempStr.equals("[sql]")) {
                    sql = "";
                    mode = "sql";
                    continue;
                } else if (tempStr.equals("[result]")) {
                    result = "";
                    mode = "result";
                    continue;
                } else if (tempStr.equals("[except]")) {
                    except = "";
                    mode = "except";
                    continue;
                } else if (tempStr.equals("[end]")) {
                    analyzeAndBuildOperator(sql, result, except);
                    continue;
                }

                if (mode.equals("sql")) {
                    sql += tempStr;
                } else if (mode.equals("result")) {
                    result += tempStr + "\n";
                } else if (mode.equals("except")) {
                    except += tempStr;
                }
            }
            reader.close();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e1) {
                    e1.printStackTrace();
                }
            }
        }
    }
}
