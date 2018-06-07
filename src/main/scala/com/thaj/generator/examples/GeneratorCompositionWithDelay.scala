package com.thaj.generator.examples

import cats.effect.IO

import scala.concurrent.duration._
import com.thaj.generator.Generator
import scalaz.syntax.std.boolean._

import scala.concurrent.Await
import scala.util.Try


object GeneratorCompositionWithDelay {
  val number = new java.util.concurrent.atomic.AtomicLong(0)

  def main(args: Array[String]): Unit = {
    val generator1: Generator[Int, Int] = Generator.create {
      s => {
        (s < 30).option {
          val ss = s + 1
          (ss, ss)
        }
      }
    }

    val generator2: Generator[Int, Int] = Generator.create {
      s => {
        (s < 30).option {
          val ss = s * 10
          (ss, ss)
        }
      }
    }

    val simpleComposedGen =
      for {
        _ <- generator1
        y <- generator2
      } yield y

    val fut =
      Generator.run[IO, Int, Int](simpleComposedGen.withZero(0).withDelay(3000))(a => IO {
        println(Thread.currentThread().getName + " " + a)
        number.getAndAdd(a)
      }).unsafeToFuture()

    Try {
      Await.result(fut, 10.seconds)
    }.fold(_ => println(s"The result is ${number.get()}: ${number.get() == 120}"), _ => ())
  }

}
