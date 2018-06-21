package com.thaj.generator.examples

import cats.effect.IO

import scala.concurrent.duration._
import com.thaj.generator.{Generator, GeneratorLogic}
import scalaz.syntax.std.boolean._

import scala.concurrent.Await
import scala.util.Try


object GeneratorComposition {
  val number = new java.util.concurrent.atomic.AtomicLong(0)

  def main(args: Array[String]): Unit = {
    val generator1: GeneratorLogic[Int, Int] =  GeneratorLogic.create {
      s => {
        (s < 30).option {
          val ss = s + 1
          (ss, ss)
        }
      }
    }

    val generator2: GeneratorLogic[Int, Int] =  GeneratorLogic.create {
      s => {
        (s < 30).option {
          val ss = s * 10
          (ss, ss)
        }
      }
    }

    // Showing the behavior of composed generators.
    // simpleComposedGen takes the input 0 that comes in, passed to generator1 to add (s+1) to get the state and value as (1, 1)
    // the state 1 is then passed to the function  `s*10` to get the new state and value as `10, 10`.
    // Passing 10 again to simpleComposeGen results in (10 + 1) * 10 resulting in 110 and 110. Passing 110 again results in no output
    // as it met the termination condition of generator 1. The following should emit only
    val simpleComposedGen =
    for {
      _ <- generator1
      y <- generator2
    } yield y

    val fut =
      Generator.run[IO, Int, Int](simpleComposedGen.withZero(0))(a => IO {
        println(Thread.currentThread().getName + " " + a)
        number.getAndAdd(a)
      }).unsafeToFuture()

    Try {
      Await.result(fut, 2.seconds)
    }.fold(_ => println(s"The result is ${number.get()}: ${number.get() == 120}"), _ => ())


    // complexGen starts with input 0 passed to generator1 to add (s + 1) to get the new state and value as (1, 1).
    // The value 1 is passed to generator2 which is now modified to s => (s*10), (s*10) + 1. The previous state 1 is then passed
    // to this new generator function to get (10, 11). Repeating the step, when the state 10 is passed to complexGen,
    // it flows through generator1 to get (11, 11), and this value 11 is passed to generator2 to have  s => (s * 10), (s * 10) + 11.
    // The previous state 11 is then passed to this new generator to get (110, 121)
    // Repeating the steps, passing 110 to complexGen results in None, as the generator1 met the termination condition.
    val complexGen =
    for {
      t <- generator1
      y <- generator2.map(_ + t)
    } yield y

    number.set(0)
    val fut2 = Generator.run[IO, Int, Int](complexGen.withZero(0))(a => IO {
      println(Thread.currentThread().getName + " " + a)
      number.getAndAdd(a)
    }).unsafeToFuture()

    Try {
      Await.result(fut2, 2.seconds)
    }.fold(_ => println(s"The result is ${number.get()}: ${number.get() == 132}"), _ => ())

    number.set(0)
    // Let's pass simpleComposedGen, complexGen, generator1. It should print out the numbers 110, 10, 11, 121
    // and all the numbers from 1 to 30. The number should be 110 + 10 + 11 + 121 + 30(30+1)/2
    Generator.run[IO, Int, Int](simpleComposedGen.withZero(0), complexGen.withZero(0), generator1.withZero(0))(a => IO {
      println(Thread.currentThread().getName + " " + a)
      number.getAndAdd(a)
    }).unsafeToFuture()

    Try {
      Await.result(fut, 3.seconds)
    }.fold(_ => println(s"The result is ${number.get()}: ${number.get() == 717}"), _ => ())
  }
}
