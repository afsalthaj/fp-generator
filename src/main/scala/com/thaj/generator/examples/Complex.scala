package com.thaj.generator.examples

import com.thaj.generator.Generator

import scala.concurrent.Future
import scalaz.{Monad, \/}
import scalaz.syntax.std.boolean._
import scalaz.syntax.either._
import scala.concurrent.ExecutionContext.Implicits.global

/**
  * Created by afsalthaj on 5/23/18.
  */
object Complex {
  def main(args: Array[String]): Unit = {

    // Similar to `Simple` example, we need a monad instance for the effect that you choose.
    implicit val monad: Monad[Future] = new Monad[Future] {
      override def bind[A, B](fa: Future[A])(f: (A) => Future[B]): Future[B] = fa.flatMap(f)
      override def point[A](a: => A): Future[A] = Future { a }
    }

    // Say u have already got a generator that goes from Int to Int
    implicit val generator: Generator[Int, Int] =  new Generator[Int, Int] {
      override def zero: Int = 0

      override def next: (Int) => Option[(Int, Int)] = s => {
        (s < 1000).option {
          val ss = s + 10
          (ss, ss)
        }
      }
    }

    // then you may need to send data as a batch. The number of batches and the number in each batch should be controllable
    val numOfBatches: Int = 10

    // Create that many generators as a normal step
    val generators: List[Generator[Int, Int]] = List.fill(numOfBatches)(Generator[Int, Int])

    // combine them to be just 1
    val combinedGenerator: Generator[Int, List[Int]] = Generator.sequence(generators)

    // Let's run the generator, obviously the state tranisiton is `+10`, however u need to tell the API, how you
    // can generate a list from each of those state, which is in this case is `s  => (s to (s + 10)).toList`.
    // Most of the times, you may have the same rule as generator of Int => Int. If yes, that might be a repetition
    // and the next version of library will fix it. Here we fix the size of each batch as 100
    combinedGenerator.run[Future, Throwable](100){t => Future[Throwable \/ Unit] {
      println(s"In thread: ${Thread.currentThread().getName}: values are sent as a batch: $t").right[Throwable]
    }}{ s => (s to (s + 10)).toList }
  }
}
