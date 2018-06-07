package com.thaj.generator.examples

import cats.effect.IO
import com.thaj.generator.Generator
import scalaz.syntax.std.boolean._

object SimpleProcessWithDelay {
  def main(args: Array[String]): Unit = {

    // Our generator is as simple as specifying a zero val and the state changes
    val generator = Generator.create[Int, Int] {
      s => {
        (s < 10).option {
          val ss = s + 1
          (ss, ss)
        }
      }
    }.withZero(0).withDelay(2000)
    // Generate data based on the above rule.
    // All we need to do is pass generator and the action that is to be done on each data.
    Generator.run[IO, Int, Int](generator)(a => IO {
      println(Thread.currentThread().getName + " " + a)
    }).unsafeRunSync
  }
}
