package simplesql

import utest.*

import java.time.{Instant, LocalDate}
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
