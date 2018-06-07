package com.thaj.generator

import cats.effect.IO
import scalaz.syntax.std.boolean._

object Determinism {
  private def testDeterminism: Unit = {
    val number = new java.util.concurrent.atomic.AtomicLong(1)

    // We consider the fact that the circularbuffer queue size is 100.
    val generator: Generator[Int, Int] =  Generator.create(0) {
      s => {
        (s < 30).option {
          val ss = s + 1
          (ss, ss)
        }
      }
    }

    (0 to 100).foreach { _ =>
      number.set(0)

      Generator.run[IO, Int, Int](generator)(a => IO {
        println(Thread.currentThread().getName + " " + a)
        number.getAndAdd(a)
      }).unsafeToFuture()

      Thread.sleep(2000)
      println(number.get())
      assert(number.get() == 465)
    }
  }

  def main(args: Array[String]): Unit = {
    testDeterminism
  }
}
