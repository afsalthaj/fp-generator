package com.thaj.generator.examples

import java.util.concurrent.atomic.AtomicReference

import com.thaj.generator.{Generator, Zero}

import scala.concurrent.Future
import scalaz.{Monad, \/}
import scalaz.syntax.std.boolean._
import scalaz.syntax.either._
import scala.concurrent.ExecutionContext.Implicits.global

/**
  * A simple data generation where side effect happens after every instance of generation recursively
  * involving state.
  */
object Simple {

  private val atomicReference = new AtomicReference[List[Int]]()

  implicit val monad: Monad[Future] = new Monad[Future] {
    override def bind[A, B](fa: Future[A])(f: (A) => Future[B]): Future[B] = fa.flatMap(f)
    override def point[A](a: => A): Future[A] = Future { a }
  }

  implicit val zero: Zero[Int] = new Zero[Int] {
    override def zero: Int = 0
  }

  implicit val generator: Generator[Int, Int] = new Generator[Int, Int] {
    override def next: (Int) => Option[(Int, Int)] = s =>
      (s < 10).option((s + 1, s + 1))
  }

  def main(args: Array[String]): Unit = {
    Generator[Int, Int].run[Future, Throwable](1){t => Future[Throwable \/ Unit] {
      println(s"In thread: ${Thread.currentThread().getName}: single value is sent one by one: $t").right[Throwable]
    }}
  }
}
