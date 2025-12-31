package simplesql

import utest.*

import java.time.{Instant, LocalDate, Year}
import java.time.temporal.ChronoUnit

object JavaTimeTest extends TestSuite:

  val tests = Tests:
    "java.time" - {
      val ds = DataSource.simple("jdbc:sqlite::memory:")
      "Instant" - {
        ds.run:
          sql"CREATE TABLE tests (id int PRIMARY KEY, ts timestampz)".write() ==> 0
          val now = Instant.now().truncatedTo(ChronoUnit.MILLIS)
          sql"""INSERT INTO tests VALUES
                (0, null),
                (1, ${Instant.EPOCH}),
                (2, $now)
              """.write() ==> 3
          sql"SELECT ts FROM tests".read[Instant]() ==> Seq(
            null,
            Instant.EPOCH,
            now,
          )
      }
      "Year" - {
        ds.run:
          sql"CREATE TABLE tests (id int PRIMARY KEY, year int)".write() ==> 0
          val now = Year.now()
          sql"""INSERT INTO tests VALUES
                (0, null),
                (1, ${Year.of(Year.MIN_VALUE)}),
                (2, ${Year.of(Year.MAX_VALUE)}),
                (3, $now)
              """.write() ==> 4
          sql"SELECT year FROM tests".read[Year]() ==> Seq(
            null,
            Year.of(Year.MIN_VALUE),
            Year.of(Year.MAX_VALUE),
            now,
          )
          case class Test(id: Int, year: Year) derives Reader
          sql"SELECT * FROM tests".read[Test]() ==> Seq(
            Test(0, null),
            Test(1, Year.of(Year.MIN_VALUE)),
            Test(2, Year.of(Year.MAX_VALUE)),
            Test(3, now),
          )
      }
      "LocalDate" - {
        ds.run:
          sql"CREATE TABLE tests (id int PRIMARY KEY, date datez)".write() ==> 0
          val today = LocalDate.now()
          sql"""INSERT INTO tests VALUES
                (0, null),
                (1, ${LocalDate.EPOCH}),
                (2, $today)
              """.write() ==> 3
          sql"SELECT date FROM tests".read[LocalDate]() ==> Seq(
            null,
            LocalDate.EPOCH,
            today,
          )
      }
    }

end JavaTimeTest
