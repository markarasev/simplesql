package simplesql

import utest.*

object CompositionTest extends TestSuite:

  val tests = Tests {
    test("concat const fragments") {
      val ds = simplesql.DataSource.pooled("jdbc:sqlite::memory:")
      ds.run:
        sql"""
          CREATE TABLE users (
            id integer PRIMARY KEY,
            name text NOT NULL
          )
        """.write()

      val actual = ds.run:
        val fr1 = sql"INSERT INTO users VALUES (1"
        val fr2 = sql", '')"
        (fr1 ++ fr2).write()

      actual ==> 1
      ds.run:
        sql"SELECT * FROM users".read[(Int, String)]() ==> Seq((1, ""))
    }
    test("concat parameterized fragments") {
      val ds = simplesql.DataSource.pooled("jdbc:sqlite::memory:")
      ds.run:
        sql"""
          CREATE TABLE users (
            id integer PRIMARY KEY,
            name text NOT NULL
          )
        """.write()

      val id = 2
      val name = "Bob"
      val actual = ds.run:
        val fr1 = sql"INSERT INTO users VALUES ($id"
        val fr2 = sql", $name"
        val fr3 = sql")"
        (fr1 ++ fr2 ++ fr3).write()

      actual ==> 1
      ds.run:
        sql"SELECT * FROM users".read[(Int, String)]() ==> Seq((id, name))
    }
  }

end CompositionTest
