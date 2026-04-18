package simplesql

import utest.*

object InTest extends TestSuite:

  val tests = Tests {
    test("parameterized IN") {
      val ds = simplesql.DataSource.pooled("jdbc:sqlite::memory:")
      ds.run:
        sql"""
          CREATE TABLE users (
            id integer PRIMARY KEY,
            name text NOT NULL
          )
        """.write()
        sql"""
          INSERT INTO users VALUES
          (1, 'Jakob'),
          (2, 'Marc'),
          (3, '')
        """.write() ==> 3

        val ids = Query.InParam(1, 3, 4)
        sql"SELECT name FROM users WHERE id IN $ids".read[String]() ==>
          Seq("Jakob", "")
        sql"SELECT name FROM users WHERE id IN $ids LIMIT ${1}".read[String]() ==>
          Seq("Jakob")
    }
  }

end InTest
