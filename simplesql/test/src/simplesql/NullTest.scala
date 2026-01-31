package simplesql

import utest.*
import SimpleReader.given

import java.time.{Instant, LocalDate, Year}
import java.time.temporal.ChronoUnit

object NullTest extends TestSuite:

  private val ds = DataSource.simple("jdbc:sqlite::memory:")

  private def test[X: SimpleReader: SimpleWriter](
      sqlType: String,
      testValue: X,
  )(using SimpleReader[Option[X]]) =
    ds.run:
      // TODO: add SQL literals support
      val baseCreateTableQuery = sql"CREATE TABLE tests (x _type_to_replace_)"
      baseCreateTableQuery
        .copy(
          sql = baseCreateTableQuery.sql.replaceFirst("_type_to_replace_", sqlType),
        )
        .write()
      sql"INSERT INTO tests VALUES ($testValue), (null)".write()
      case class TestXOption(x: Option[X]) derives Reader
      case class TestX(x: X) derives Reader

      sql"SELECT x FROM tests".read[Option[X]]() ==>
        Seq(Some(testValue), None)
      sql"SELECT x FROM tests".read[TestXOption]() ==>
        Seq(TestXOption(Some(testValue)), TestXOption(None))
      intercept[NoSuchElementException](sql"SELECT x FROM tests".read[X]())
      intercept[NoSuchElementException](sql"SELECT x FROM tests".read[TestX]())
  end test

  override def tests: Tests = Tests {

    "byte" - {
      test[Byte](sqlType = "tinyint", testValue = 1)
    }

    "short" - {
      test[Short](sqlType = "smallint", testValue = 1)
    }

    "int" - {
      test[Int](sqlType = "integer", testValue = 1)
    }

    "long" - {
      test[Long](sqlType = "bigint", testValue = 1)
    }

    "float" - {
      test[Float](sqlType = "real", testValue = 1)
    }

    "double" - {
      test[Double](sqlType = "double", testValue = 1)
    }

    "boolean" - {
      test(sqlType = "boolean", testValue = true)
    }

    "string" - {
      test(sqlType = "text", testValue = "foo")
    }

    "byte array" - {
      // arrays are not comparable by their content by default
      val testValue: Array[Byte] = "foo".getBytes
      case class TestOption(bytes: Option[Array[Byte]]) derives Reader
      case class Test(bytes: Array[Byte]) derives Reader
      ds.run:
        sql"CREATE TABLE tests (bytes bytea)".write()
        sql"INSERT INTO tests VALUES ($testValue), (null)".write()

        sql"SELECT bytes FROM tests"
          .read[Option[Array[Byte]]]()
          .map(_.map(_.toSeq)) ==> Seq(Some(testValue.toSeq), None)
        sql"SELECT bytes FROM tests"
          .read[TestOption]()
          .map(_.bytes.map(_.toSeq)) ==> Seq(Some(testValue.toSeq), None)
        intercept[NoSuchElementException]:
          sql"SELECT bytes FROM tests".read[Array[Byte]]()
        intercept[NoSuchElementException]:
          sql"SELECT bytes FROM tests".read[Test]()
    }

    // UUIDs seem to be unsupported by sqlite

    "instant" - {
      test(
        sqlType = "timestampz",
        testValue = Instant.now().truncatedTo(ChronoUnit.MILLIS),
      )
    }

    "year" - {
      test(sqlType = "int", testValue = Year.now())
    }

    "date" - {
      test(sqlType = "datez", testValue = LocalDate.now())
    }

  }

end NullTest
