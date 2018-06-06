package com.thaj.generator.examples

import cats.effect.IO
import com.thaj.generator.Generator
import scalaz.syntax.std.boolean._

object GeneratorComposition {
  def main(args: Array[String]): Unit = {
    val generator: Generator[Int, Int] =  Generator.create(1) {
      s => {
        (s < 30).option {
          val ss = s + 1
          (ss, ss)
        }
      }
    }

    val generator2: Generator[Int, Int] =  Generator.create(2) {
      s => {
        (s < 30).option {
          val ss = s + 1
          (ss, ss)
        }
      }
    }

    val generator3: Generator[Int, Int] =  Generator.create(3) {
      s => {
        (s < 30).option {
          val ss = s + 1
          (ss, ss)
        }
      }
    }

    val newGen =
      for {
        x <- generator
        y <- generator2.map(_ + x)
        z <- generator3.map(_ * y)
      } yield z

    // Generate data based on the above rule.
    // All we need to do is pass generator and the action that is to be done on each data.
    Generator.run[IO, Int, Int](newGen)(a => IO {println(Thread.currentThread().getName + " " + a) }).unsafeRunSync
  }
}
