package simplesql

import utest.*

import java.time.{Instant, LocalDate, Year}

object TupleTest extends TestSuite:

  val tests = Tests {
    test("TupleXXL Reader derivation") {
      2.toShort ==> 2.toShort
      val ds = simplesql.DataSource.pooled("jdbc:sqlite::memory:")
      ds.run:
        sql"""
          create table test (
            one integer,
            two integer,
            three integer,
            four integer,
            five real,
            six real,
            seven integer,
            eight text,
            nine integer,
            ten text,
            eleven integer,
            twelve integer,
            thirteen integer,
            fourteen integer,
            fifteen integer,
            sixteen integer,
            seventeen real,
            eighteen real,
            nineteen text,
            twenty integer,
            twenty_one text,
            twenty_two integer,
            twenty_three integer,
            twenty_four integer
          )
        """.write()
        sql"""
          insert into test values (
            ${1.toByte},
            ${2.toShort},
            ${3},
            ${4},
            ${5},
            ${6},
            ${false},
            ${"eight"},
            9,
            '1970-01-10 00:00:00.000',
            ${Instant.ofEpochMilli(11)},
            ${12},
            ${13.toShort},
            ${14.toByte},
            ${15},
            ${16},
            ${17},
            ${18},
            ${"nineteen"},
            ${true},
            '1970-01-21 00:00:00.000',
            22,
            ${23},
            ${Instant.ofEpochMilli(24)}
          )
        """.write()
      type Tuple24 = (
          Byte,
          Short,
          Int,
          Long,
          Float,
          Double,
          Boolean,
          String,
          Int,
          LocalDate,
          Instant,
          Year,
          Short,
          Byte,
          Long,
          Int,
          Double,
          Float,
          String,
          Boolean,
          LocalDate,
          Int,
          Year,
          Instant,
      )

      val result = ds.run:
        sql"select * from test".read[Tuple24]()

      result ==> Seq(
        (
          1.toByte,
          2.toShort,
          3,
          4L,
          5f,
          6d,
          false,
          "eight",
          9,
          LocalDate.of(1970, 1, 10),
          Instant.ofEpochMilli(11),
          Year.of(12),
          13.toShort,
          14.toByte,
          15L,
          16,
          17d,
          18f,
          "nineteen",
          true,
          LocalDate.of(1970, 1, 21),
          22,
          Year.of(23),
          Instant.ofEpochMilli(24),
        ),
      )
    }
  }

end TupleTest
