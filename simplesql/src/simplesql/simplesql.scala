/* This file is copied from the Simple SQL project,
 * https://github.com/jodersky/simplesql
 *
 * Copyright 2020 Jakob Odersky <jakob@odersky.com>
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:

 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors
 *    may be used to endorse or promote products derived from this software
 *    without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 *
 * Simple SQL
 * ==========
 *
 * A no-frills SQL library for Scala 3.
 *
 * SimpleSQL is a very thin wrapper around JDBC, which allows you to take full
 * advantage of *full SQL* and *any database* with a JDBC driver.
 */
package simplesql

import java.sql as jsql
import java.sql.DriverManager
import scala.deriving
import scala.compiletime
import scala.annotation
import java.time.{Instant, LocalDate, Year}
import java.util.UUID

@annotation.implicitNotFound(
  "No database connection found. Make sure to call this in a `run()` or `transaction()` block.",
)
case class Connection(underlying: jsql.Connection)

extension (inline sc: StringContext)
  inline def sql(inline args: Any*): Query =
    ${ Query.sqlImpl('{ sc }, '{ args }) }

/** A thin wrapper around an SQL statement */
case class Query(sql: String, fillStatement: jsql.PreparedStatement => Unit):

  def read[A]()(using conn: Connection, r: Reader[A]): List[A] =
    val elems = collection.mutable.ListBuffer.empty[A]

    var stat: jsql.PreparedStatement = null
    var res: jsql.ResultSet = null
    try
      stat = conn.underlying.prepareStatement(sql)
      fillStatement(stat)
      res = stat.executeQuery()
      while res.next() do elems += r.read(res)
    finally
      if res != null then res.close()
      if stat != null then stat.close()
    elems.result()

  def readOne[A]()(using Connection, Reader[A]): A = read[A]().head

  def readOpt[A]()(using Connection, Reader[A]): Option[A] =
    read[A]().headOption

  def write()(using conn: Connection): Int =
    var stat: jsql.PreparedStatement = null
    try
      stat = conn.underlying.prepareStatement(
        sql,
        jsql.Statement.RETURN_GENERATED_KEYS,
      )
      fillStatement(stat)
      stat.executeUpdate()
    finally if stat != null then stat.close()

end Query

object Query:

  import scala.quoted.{Expr, Quotes, Varargs}

  def sqlImpl(sc0: Expr[StringContext], args0: Expr[Seq[Any]])(using
      qctx: Quotes,
  ): Expr[Query] =
    import scala.quoted.quotes.reflect._

    val args: Seq[Expr[?]] = args0 match
      case Varargs(exprs) => exprs
    val writers: Seq[Expr[SimpleWriter[?]]] =
      for (case '{ $arg: t } <- args) yield
        val w = TypeRepr.of[SimpleWriter].appliedTo(TypeRepr.of[t].widen)
        Implicits.search(w) match
          case iss: ImplicitSearchSuccess =>
            iss.tree.asExprOf[SimpleWriter[?]]
          case isf: ImplicitSearchFailure =>
            report.error(s"could not find implicit for ${w.show}", arg)
            '{ ??? }

    val qstring = sc0.value match
      case None =>
        report.error("string context must be known at compile time", sc0)
        ""
      case Some(sc) =>
        val strings = sc.parts.iterator
        val buf = new StringBuilder(strings.next())
        while (strings.hasNext) {
          buf.append(" ? ")
          buf.append(strings.next())
        }
        buf.result()

    val r = '{
      Query(
        ${ Expr(qstring) },
        (stat: jsql.PreparedStatement) =>
          ${
            val exprs =
              for (((writer, arg), idx) <- writers.zip(args).zipWithIndex.toList)
                yield writer match {
                  case '{ $writer: SimpleWriter[t] } =>
                    '{
                      $writer.write(stat, ${ Expr(idx + 1) }, ${ arg.asExprOf[t] })
                    }
                }
            Expr.block(exprs, 'stat)
          },
      )
    }
    // System.err.println(r.show)
    r
  end sqlImpl

end Query

trait SimpleWriter[-A] {
  def write(stat: jsql.PreparedStatement, idx: Int, value: A): Unit
}

object SimpleWriter:

  given SimpleWriter[Byte] = (stat, idx, value) => stat.setByte(idx, value)
  given SimpleWriter[Short] = (stat, idx, value) => stat.setShort(idx, value)
  given SimpleWriter[Int] = (stat, idx, value) => stat.setInt(idx, value)
  given SimpleWriter[Long] = (stat, idx, value) => stat.setLong(idx, value)
  given SimpleWriter[Float] = (stat, idx, value) => stat.setFloat(idx, value)
  given SimpleWriter[Double] = (stat, idx, value) => stat.setDouble(idx, value)
  given SimpleWriter[Boolean] = (stat, idx, value) => stat.setBoolean(idx, value)
  given SimpleWriter[String] = (stat, idx, value) => stat.setString(idx, value)
  given SimpleWriter[Array[Byte]] = (stat, idx, value) => stat.setBytes(idx, value)
  given SimpleWriter[BigDecimal] = (stat, idx, value) =>
    stat.setBigDecimal(idx, value.bigDecimal)
  given SimpleWriter[UUID] = (stat, idx, value) => stat.setObject(idx, value)
  given SimpleWriter[Instant] = (stat, idx, value) =>
    stat.setTimestamp(idx, jsql.Timestamp.from(value))
  given SimpleWriter[Year] = (stat, idx, value) => stat.setInt(idx, value.getValue)
  given SimpleWriter[LocalDate] = (stat, idx, value) =>
    stat.setDate(idx, jsql.Date.valueOf(value))

  given optWriter[A](using writer: SimpleWriter[A]): SimpleWriter[Option[A]] with {
    def write(stat: jsql.PreparedStatement, idx: Int, value: Option[A]): Unit =
      value match {
        case Some(v) => writer.write(stat, idx, v)
        case None    => stat.setNull(idx, jsql.Types.NULL)
      }
  }

end SimpleWriter

trait SimpleReader[+A]:
  def readIdx(results: jsql.ResultSet, idx: Int): A
  def readName(results: jsql.ResultSet, name: String): A

object SimpleReader:

  private def readNonNull[X, JsqlType](
      _readIdx: jsql.ResultSet => Int => JsqlType,
      _readName: jsql.ResultSet => String => JsqlType,
      transform: JsqlType => X,
  ): SimpleReader[X] =
    new SimpleReader[X]:
      override def readIdx(results: jsql.ResultSet, idx: Int): X =
        val result = _readIdx(results)(idx)
        if results.wasNull()
        then throw new NoSuchElementException(s"null value at column index $idx")
        else transform(result)
      override def readName(results: jsql.ResultSet, name: String): X =
        val result = _readName(results)(name)
        if results.wasNull()
        then throw new NoSuchElementException(s"null value for column name $name")
        else transform(result)

  private def readNullable[X, JsqlType](
      _readIdx: jsql.ResultSet => Int => JsqlType,
      _readName: jsql.ResultSet => String => JsqlType,
      transform: JsqlType => X,
  ): SimpleReader[Option[X]] =
    new SimpleReader[Option[X]]:
      override def readIdx(results: jsql.ResultSet, idx: Int): Option[X] =
        val result = _readIdx(results)(idx)
        if results.wasNull() then None else Option(transform(result))
      override def readName(results: jsql.ResultSet, name: String): Option[X] =
        val result = _readName(results)(name)
        if results.wasNull() then None else Option(transform(result))

  given SimpleReader[Byte] = readNonNull(_.getByte, _.getByte, identity)
  given obyter: SimpleReader[Option[Byte]] =
    readNullable(_.getByte, _.getByte, identity)

  given SimpleReader[Short] = readNonNull(_.getShort, _.getShort, identity)
  given osr: SimpleReader[Option[Short]] =
    readNullable(_.getShort, _.getShort, identity)

  given SimpleReader[Int] = readNonNull(_.getInt, _.getInt, identity)
  given ointr: SimpleReader[Option[Int]] = readNullable(_.getInt, _.getInt, identity)

  given SimpleReader[Long] = readNonNull(_.getLong, _.getLong, identity)
  given olr: SimpleReader[Option[Long]] =
    readNullable(_.getLong, _.getLong, identity)

  given SimpleReader[Float] = readNonNull(_.getFloat, _.getFloat, identity)
  given ofr: SimpleReader[Option[Float]] =
    readNullable(_.getFloat, _.getFloat, identity)

  given SimpleReader[Double] = readNonNull(_.getDouble, _.getDouble, identity)
  given odr: SimpleReader[Option[Double]] =
    readNullable(_.getDouble, _.getDouble, identity)

  given SimpleReader[Boolean] = readNonNull(_.getBoolean, _.getBoolean, identity)
  given oboolr: SimpleReader[Option[Boolean]] =
    readNullable(_.getBoolean, _.getBoolean, identity)

  given SimpleReader[String] = readNonNull(_.getString, _.getString, identity)
  given ostrr: SimpleReader[Option[String]] =
    readNullable(_.getString, _.getString, identity)

  given SimpleReader[Array[Byte]] = readNonNull(_.getBytes, _.getBytes, identity)
  given obar: SimpleReader[Option[Array[Byte]]] =
    readNullable(_.getBytes, _.getBytes, identity)

  // Should UUIDs actually be retrieved the same way across databases?
  given SimpleReader[UUID] =
    // _.getObject(idx, getClass[UUID]) ?
    readNonNull(_.getObject, _.getObject, _.asInstanceOf[UUID])
  given ouuidr: SimpleReader[Option[UUID]] =
    readNullable(_.getObject, _.getObject, _.asInstanceOf[UUID])

  given SimpleReader[Instant] =
    readNonNull(_.getTimestamp, _.getTimestamp, _.toInstant)
  given oinstr: SimpleReader[Option[Instant]] =
    readNullable(_.getTimestamp, _.getTimestamp, _.toInstant)

  given SimpleReader[Year] = readNonNull(_.getInt, _.getInt, Year.of)
  given oyr: SimpleReader[Option[Year]] = readNullable(_.getInt, _.getInt, Year.of)

  given SimpleReader[LocalDate] = readNonNull(_.getDate, _.getDate, _.toLocalDate)
  given oldr: SimpleReader[Option[LocalDate]] =
    readNullable(_.getDate, _.getDate, _.toLocalDate)

trait Reader[A]:
  /** Read a row into the corresponding type. */
  def read(results: jsql.ResultSet): A

object Reader:

  class ProductReader[A](
      m: deriving.Mirror.ProductOf[A],
      readers: Array[SimpleReader[?]],
  ) extends Reader[A]:
    def read(results: jsql.ResultSet): A =
      val elems = new Array[Any](readers.length)
      for i <- 0 until readers.length do
        // results.getMetaData().getColumnName()
        elems(i) = readers(i).readIdx(results, i + 1)
      val prod: Product = new scala.Product:
        def productElement(n: Int): Any = elems(n)
        def productArity: Int = elems.length
        def canEqual(that: Any) = true
        override def productIterator: Iterator[Any] = elems.iterator
      m.fromProduct(prod)
  end ProductReader

  inline given simple[A](using s: SimpleReader[A]): Reader[A] =
    new Reader[A]:
      def read(results: jsql.ResultSet): A = s.readIdx(results, 1)

  inline def summonReaders[T <: Tuple]: List[SimpleReader[?]] =
    inline compiletime.erasedValue[T] match
      case _: EmptyTuple =>
        Nil
      case _: (t *: ts) =>
        compiletime.summonInline[SimpleReader[t]] :: summonReaders[ts]

  inline given [A <: Tuple](using m: deriving.Mirror.ProductOf[A]): Reader[A] =
    ProductReader[A](m, summonReaders[m.MirroredElemTypes].toArray)

  inline def derived[A]: Reader[A] = ${ deriveImpl[A] }

  import scala.quoted.Expr
  import scala.quoted.Type
  import scala.quoted.Quotes
  def deriveImpl[A: Type](using qctx: Quotes): Expr[Reader[A]] =
    import qctx.reflect.*

    val tsym = TypeRepr.of[A].classSymbol

    // TODO: this is maybe too strict. We technically don't need a case class,
    // only an apply method
    if tsym.isEmpty || !tsym.get.flags.is(Flags.Case) then
      report.error("derivation of Readers is only supported for case classes")
      return '{ ??? }

    val fields: List[Symbol] = tsym.get.primaryConstructor.paramSymss.flatten

    val AppliedType(tc, _) = TypeRepr.of[SimpleReader[A]]: @unchecked

    val childReaders: List[Expr[SimpleReader[?]]] = for field <- fields yield
      val childTpe = tc.appliedTo(field.termRef.widenTermRefByName.dealias)
      Implicits.search(childTpe) match
        case iss: ImplicitSearchSuccess =>
          iss.tree.asExprOf[SimpleReader[?]]
        case isf: ImplicitSearchFailure =>
          report.error(s"no ${childTpe.show} found for ${field.fullName}")
          report.error(isf.explanation)
          '{ ??? }

    val childNames: List[String] =
      for field <- fields
      yield field.getAnnotation(TypeRepr.of[col].typeSymbol) match
        case None        => snakify(field.name)
        case Some(annot) =>
          annot.asExprOf[col] match
            case '{ col($x) } => x.valueOrAbort

    '{
      new Reader[A]:
        def read(results: jsql.ResultSet): A = ${
          val reads: List[Term] =
            for (reader, name) <- childReaders.zip(childNames) yield '{
              ${ reader }.readName(results, ${ Expr(name) })
            }.asTerm

          Apply(
            Select(New(TypeTree.of[A]), tsym.get.primaryConstructor),
            reads,
          ).asExprOf[A]

        }
    }

  /** `thisIsSnakeCase => this_is_snake_case` */
  private def snakify(camelCase: String): String =
    val snake = new StringBuilder
    var prevIsLower = false
    for c <- camelCase do
      if prevIsLower && c.isUpper then snake += '_'
      snake += c.toLower
      prevIsLower = c.isLower
    snake.result()

end Reader

class col(val name: String) extends annotation.StaticAnnotation

class DataSource(getConnection: () => Connection):

  def transaction[A](fn: Connection ?=> A): A =
    val conn = getConnection()
    val underlying = conn.underlying
    try
      underlying.setAutoCommit(false)
      val r = fn(using conn)
      underlying.commit()
      r
    catch
      case ex: Throwable =>
        underlying.rollback()
        throw ex
    finally underlying.close()

  def run[A](fn: Connection ?=> A): A =
    val conn = getConnection()
    val underlying = conn.underlying
    try
      underlying.setAutoCommit(true)
      fn(using conn)
    finally underlying.close()

object DataSource:

  def simple(
      jdbcUrl: String,
      username: String | Null = null,
      password: String | Null = null,
  ): DataSource =
    DataSource(() =>
      Connection(DriverManager.getConnection(jdbcUrl, username, password)),
    )

  def pooled(
      jdbcUrl: String,
      username: String | Null = null,
      password: String | Null = null,
  ): DataSource =
    val ds = com.zaxxer.hikari.HikariDataSource()
    ds.setJdbcUrl(jdbcUrl)
    if username != null then ds.setUsername(username)
    if password != null then ds.setPassword(password)
    DataSource(() => Connection(ds.getConnection()))
