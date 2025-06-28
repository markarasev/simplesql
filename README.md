# Simple SQL

A no-frills SQL library for Scala 3.

SimpleSQL is a very thin wrapper around JDBC, which allows you to take full
advantage of *full SQL* and *any database* with a JDBC driver.

SimpleSQL is not a functional DSL to build SQL queriesq, but it does offer safe
string interpolation and utilities to work with product types, wich are the
natural representation of relational data sets.

SimpleSQL only uses Hikari for database connection pooling, but has no
dependencies otherwise (and even that can easily be removed). It is published to
maven central, under `io.crashbox:simplesql_3:0.4.0`, but **it can also be embedded by
copying the file
[simplesql/src/simplesql.scala](https://raw.githubusercontent.com/jodersky/simplesql/master/simplesql/src/simplesql.scala)
into your application**!

## Example

```scala
import simplesql as sq
import simplesql.sql

// a plain DataSource is needed, this example uses a connection pool implemented
// by HicariCP
val ds = sq.DataSource.pooled("jdbc:sqlite::memory:")

// all queries must be run within the context of a connection, use either
// `<ds>.run` or `<ds>.transaction` blocks
ds.transaction:
  sql"""
    create table user (
      id integer primary key,
      name text not null,
      email text not null
    )
  """.write()

  sql"select * from user".read[(Int, String, String)]()
  sql"""insert into user values (1, 'admin', 'admin@example.org')""".write()

  case class User(id: Int, name: String, email: String) derives sq.Reader
  sql"select * from user".read[User]()

  sql"select name, id from user where id = ${1}".read[(String, Int)]()
```

## Explanation

### Database connection

All queries must be run on a connection to a database. SimpleSQL models this
through a `Connection` class, which is just a simple wrapper around
`java.sql.Connection`.

A connection may be obtained as a [context
function](https://dotty.epfl.ch/docs/reference/contextual/context-functions.html)
through either `<datasource>.run` or `<datasource>.transaction`. Both functions
provide a connection, however the latter will automatically roll back any
changes, should an exception be thrown in its body.

An in-scope connection also gives access to the `sql` string interpolator. This
interpolator is a utility to build `simplesql.Query`s, which are builders for
`java.sql.PreparedStatements`. In other words, it can be used to build
injection-safe queries with interpolated parameters. Interpolated parameters
must be primitve types (supported by JDBC).

### Read Queries

Read queries (e.g. selects) must be run in a `read` call. A read must have its
result type specified. The result type may be any primitive type supported by
JDBC `ResultSet`s or a product thereof (including named products, i.e. `case
class`es).

Fields of case classes are converted to `snake_case` in the database. You can
override this by annotating them with `simplesql.col("name")`.

### Write Queries

Write queries (e.g. insertions, updates, deletes and table alterations) must be
run in a `write` call.
