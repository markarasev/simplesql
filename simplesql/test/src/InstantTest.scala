import simplesql as sq

import sq.sql
import utest.*

import java.time.Instant
import java.time.temporal.ChronoUnit

object InstantTest extends TestSuite:

  val tests = Tests:
    "instant" - {
      val ds = sq.DataSource.pooled("jdbc:sqlite::memory:")
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

end InstantTest
