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
        (s < 100).option {
          val ss = s + 2
          (ss, ss)
        }
      }
    }

    // then you may need to send data as a batch. Let us assume that the size of each batch is 10.
    val num: Int = 10

    // Let us create that many generators.
    val generators: List[Generator[Int, Int]] = List.fill(num)(Generator[Int, Int])

    // We need Int -> List[Int] and not List [ Int -> Int ].. Let us sequence it.
    val combinedGenerator: Generator[Int, List[Int]] = Generator.sequence(generators)

    // What on earth is this combinedGenerator ? Its a generator where each side effect deals with
    // `num` of records (ex: for sending data as a batch) until the data reaches the condition mentioned in the generator instance.
    combinedGenerator.run[Future, Throwable](1){t => Future[Throwable \/ Unit] {
      println(s"In thread: ${Thread.currentThread().getName}: values are sent as a batch: $t").right[Throwable]
    }}
  }
}
