package simplesql

import utest.*

object NullTest extends TestSuite:

  val tests = Tests {
    "nulls and Options" - {
      val ds = DataSource.pooled("jdbc:sqlite::memory:")
      "string" - {
        ds.transaction:
          sql"""
          create table user (
            id integer primary key,
            name string,
            email string not null
          )
        """.write()

          sql"""insert into user values
              (${1}, ${Some("admin")}, ${"admin@example.org"})""".write() ==> 1
          sql"""insert into user values
              (${2}, ${None: Option[String]}, ${"admin@example.org"})"""
            .write() ==> 1
          sql"""insert into user values
              (${3}, null, ${"admin@example.org"})""".write() ==> 1

          case class User(id: Int, name: Option[String], email: String)
              derives Reader
          sql"select * from user".read[User]() ==>
            User(1, Some("admin"), "admin@example.org") ::
            User(2, None, "admin@example.org") ::
            User(3, None, "admin@example.org") :: Nil
      }
      "primitive" - {
        ds.run:
          sql"CREATE TABLE tests(id int primary key, number int)".write() ==> 0
          sql"INSERT INTO tests VALUES (0, null), (1, 0), (2, 1)".write() ==> 3
          sql"SELECT number FROM tests".read[Option[Int]]() ==> Seq(
            None,
            Some(0),
            Some(1),
          )
      }
    }
  }
