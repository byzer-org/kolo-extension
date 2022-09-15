package tech.mlsql.plugins.mllib.ets.fe

import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.functions.{col, desc, explode, struct}
import org.apache.spark.streaming.SparkOperationUtil
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.{BeforeAndAfterAll}
import streaming.core.strategy.platform.SparkRuntime
import tech.mlsql.test.BasicMLSQLConfig

import java.sql.Timestamp
import java.time.LocalDateTime
import java.util.{Date, UUID}

/**
 *
 * @Author; Andie Huang
 * @Date: 2022/6/27 19:07
 *
 */
class SQLDataSummaryV2Test extends AnyFunSuite with SparkOperationUtil with BasicMLSQLConfig with BeforeAndAfterAll {
  def startParams = Array(
    "-streaming.master", "local[*]",
    "-streaming.name", "unit-test",
    "-streaming.rest", "false",
    "-streaming.platform", "spark",
    "-streaming.enableHiveSupport", "false",
    "-streaming.hive.javax.jdo.option.ConnectionURL", s"jdbc:derby:;databaseName=metastore_db/${UUID.randomUUID().toString};create=true",
    "-streaming.spark.service", "false",
    "-streaming.unittest", "true",
    "-spark.sql.shuffle.partitions", "12",
    "-spark.default.parallelism", "12",
    "-spark.executor.memoryOverheadFactor", "0.2",
    "-spark.dirver.maxResultSize", "2g"
  )
  test("DataSummary should summarize the Dataset") {
    withBatchContext(setupBatchContext(startParams)) { runtime: SparkRuntime =>
      implicit val spark: SparkSession = runtime.sparkSession
      val et = new SQLDataSummaryV2()
      val sseq1 = Seq(
        ("elena", 57, "433000", Timestamp.valueOf(LocalDateTime.of(2021, 3, 8, 18, 0))),
        ("abe", 50, "433000", Timestamp.valueOf(LocalDateTime.of(2021, 3, 8, 18, 0))),
        ("AA", 10, "432000", Timestamp.valueOf(LocalDateTime.of(2021, 3, 8, 18, 0))),
        ("cc", 40, "433000", Timestamp.valueOf(LocalDateTime.of(2021, 3, 8, 18, 0))),
        ("", 30, "434000", Timestamp.valueOf(LocalDateTime.of(2021, 3, 8, 18, 0))),
        ("bb", 21, "533000", Timestamp.valueOf(LocalDateTime.of(2021, 3, 8, 18, 0)))
      )
      val seq_df1 = spark.createDataFrame(sseq1).toDF("name", "age", "income", "date")
      val res1DF = et.train(seq_df1, "", Map("atRound" -> "2"))
      res1DF.show()

      assert(res1DF.collect()(0).mkString(",") === "name,1.0,string,5.0,elena,AA,5.0,0.0,,,,0.0,0.1667,6.0,1.0,1,0.0,")
      assert(res1DF.collect()(1).mkString(",") === "age,2.0,integer,4.0,57.0,10.0,,,34.67,17.77,7.26,0.0,0.0,6.0,1.0,1,30.0,")
      assert(res1DF.collect()(2).mkString(",") === "income,3.0,string,6.0,533000.0,432000.0,6.0,6.0,,,,0.0,0.0,6.0,0.6667,0,0.0,433000.0")
      assert(res1DF.collect()(3).mkString(",") === "date,4.0,timestamp,8.0,2021-03-08 18:00:00,2021-03-08 18:00:00,,,,,,0.0,0.0,6.0,0.1667,0,0.0,2021-03-08 18:00:00.0")
      val sseq = Seq(
        ("elena", 57, 57, 110L, "433000", Timestamp.valueOf(LocalDateTime.of(2021, 3, 8, 18, 0)), 110F, true, null, null, BigDecimal.valueOf(12), 1.123D),
        ("abe", 57, 50, 120L, "433000", Timestamp.valueOf(LocalDateTime.of(2021, 3, 8, 18, 0)), 120F, true, null, null, BigDecimal.valueOf(2), 1.123D),
        ("AA", 57, 10, 130L, "432000", Timestamp.valueOf(LocalDateTime.of(2021, 3, 8, 18, 0)), 130F, true, null, null, BigDecimal.valueOf(2), 2.224D),
        ("cc", 0, 40, 100L, "", Timestamp.valueOf(LocalDateTime.of(2021, 3, 8, 18, 0)), Float.NaN, true, null, null, BigDecimal.valueOf(2), 2D),
        ("", -1, 30, 150L, "434000", Timestamp.valueOf(LocalDateTime.of(2021, 3, 8, 18, 0)), 150F, true, null, null, BigDecimal.valueOf(2), 3.375D),
        ("bb", 57, 21, 160L, "533000", Timestamp.valueOf(LocalDateTime.of(2021, 3, 8, 18, 0)), Float.NaN, false, null, null, BigDecimal.valueOf(2), 3.375D)
      )
      val seq_df = spark.createDataFrame(sseq).toDF("name", "favoriteNumber", "age", "mock_col1", "income", "date", "mock_col2", "alived", "extra", "extra1", "extra2", "extra3")
      val res2DF = et.train(seq_df, "", Map("atRound" -> "2", "metrics" -> "dataLength,max,min,maximumLength,minimumLength,mean,standardDeviation,standardError,nullValueRatio,blankValueRatio,uniqueValueRatio,primaryKeyCandidate,median,mode"))
      res2DF.show()

      assert(res2DF.collect()(0).mkString(",") === "name,1.0,5.0,elena,AA,5.0,0.0,,,,0.0,0.1667,1.0,1,0.0,")
      assert(res2DF.collect()(1).mkString(",") === "favoriteNumber,2.0,4.0,57.0,-1.0,,,37.83,29.69,12.12,0.0,0.0,0.5,0,57.0,57.0")
      assert(res2DF.collect()(2).mkString(",") === "age,3.0,4.0,57.0,10.0,,,34.67,17.77,7.26,0.0,0.0,1.0,1,30.0,")
      assert(res2DF.collect()(3).mkString(",") === "mock_col1,4.0,8.0,160.0,100.0,,,128.33,23.17,9.46,0.0,0.0,1.0,1,120.0,")
      assert(res2DF.collect()(4).mkString(",") === "income,5.0,6.0,533000.0,432000.0,6.0,0.0,,,,0.0,0.1667,0.8,0,0.0,433000.0")
      assert(res2DF.collect()(5).mkString(",") === "date,6.0,8.0,2021-03-08 18:00:00,2021-03-08 18:00:00,,,,,,0.0,0.0,0.1667,0,0.0,2021-03-08 18:00:00.0")
      assert(res2DF.collect()(6).mkString(",") === "mock_col2,7.0,4.0,150.0,110.0,,,127.5,17.08,8.54,0.3333,0.0,1.0,1,110.0,")
      assert(res2DF.collect()(7).mkString(",") === "alived,8.0,1.0,true,false,,,,,,0.0,0.0,0.3333,0,0.0,true")
      assert(res2DF.collect()(8).mkString(",") === "extra,9.0,,,,,,,,,1.0,0.0,0.0,0,0.0,")
      assert(res2DF.collect()(9).mkString(",") === "extra1,10.0,,,,,,,,,1.0,0.0,0.0,0,0.0,")
      assert(res2DF.collect()(10).mkString(",") === "extra2,11.0,16.0,12.0,2.0,,,3.67,4.08,1.67,0.0,0.0,0.3333,0,2.0,2.0")
      assert(res2DF.collect()(11).mkString(",") === "extra3,12.0,8.0,3.38,1.12,,,2.2,1.01,0.41,0.0,0.0,0.6667,0,2.0,")
      val sseq2 = Seq(
        (null, null),
        (null, null)
      )
      val seq_df2 = spark.createDataFrame(sseq2).toDF("col1", "col2")
      val res3DF = et.train(seq_df2, "", Map("atRound" -> "2", "metrics" -> "dataLength,max,min,maximumLength,minimumLength,mean,standardDeviation,standardError,nullValueRatio,blankValueRatio,uniqueValueRatio,primaryKeyCandidate,median,mode"))
      res3DF.show()
      println(res3DF.collect()(0).mkString(","))
      println(res3DF.collect()(1).mkString(","))
      assert(res3DF.collect()(0).mkString(",") === "col1,1.0,,,,,,,,,1.0,0.0,0.0,0,0.0,")
      assert(res3DF.collect()(1).mkString(",") === "col2,2.0,,,,,,,,,1.0,0.0,0.0,0,0.0,")
      //      val paquetDF1 = spark.sqlContext.read.format("parquet").load("/Users/yonghui.huang/Data/benchmarkZL1")
      //      val paquetDF2 = paquetDF1.sample(true, 1)
      //      println(paquetDF2.count())
      //      val df1 = et.train(paquetDF2, "", Map("atRound" -> "2", "relativeError" -> "0.01"))
      //      val df2 = et.train(paquetDF2, "", Map("atRound" -> "2", "metrics" -> "uniqueValueRatio,primaryKeyCandidate", "relativeError" -> "0.01", "approxCountDistinct" -> "true"))
      //      df2.show()
      //      val df2 = et.train(paquetDF2, "", Map("atRound" -> "2", "approxCountDistinct" -> "true"))
      //      df2.show()
    }
  }
}