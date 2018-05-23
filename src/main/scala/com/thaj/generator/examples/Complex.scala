package com.thaj.generator.examples

import com.thaj.generator.{Generator, Zero}

import scala.concurrent.Future
import scalaz.{Monad, \/}
import scalaz.syntax.std.boolean._
import scalaz.syntax.either._
import scala.concurrent.ExecutionContext.Implicits.global

object Complex {
  def main(args: Array[String]): Unit = {

    implicit val zero: Zero[Int] = new Zero[Int] {
      override def zero: Int = 0
    }

    // Similar to `Simple` example, we need a monad instance for the effect that you choose.
    implicit val monad: Monad[Future] = new Monad[Future] {
      override def bind[A, B](fa: Future[A])(f: (A) => Future[B]): Future[B] = fa.flatMap(f)
      override def point[A](a: => A): Future[A] = Future { a }
    }

    // Say u have already got a generator that goes from Int to Int
    implicit val generator: Generator[Int, Int] =  new Generator[Int, Int] {
      override def next: (Int) => Option[(Int, Int)] = s => {
        (s < 1000).option {
          val ss = s + 2
          (ss, ss)
        }
      }
    }

    // then you may need to send data as a batch. The number of batches and the number in each batch should be controllable
    val num: Int = 10

    // Create that many generators as a normal step
    val generators: List[Generator[Int, Int]] = List.fill(num)(Generator[Int, Int])

    // combine them to be just 1
    val combinedGenerator: Generator[Int, List[Int]] = Generator.sequence(generators)

    // What on earth is combined generator. Its a generator where each side effect involving
    // `num` of records (ex: sending data as a batch) until it the data reaches a condition which is 1000 - beautiful!
    combinedGenerator.run[Future, Throwable](100){t => Future[Throwable \/ Unit] {
      println(s"In thread: ${Thread.currentThread().getName}: values are sent as a batch: $t").right[Throwable]
    }}
  }
}
