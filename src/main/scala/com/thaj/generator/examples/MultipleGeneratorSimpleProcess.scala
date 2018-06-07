package com.thaj.generator.examples

import cats.effect.IO
import com.thaj.generator.Generator, Generator._
import scalaz.syntax.std.boolean._

object MultipleGeneratorSimpleProcess {
  def main(args: Array[String]): Unit = {

    // Our generator is as simple as specifying a zero val and the state changes
    val generator1 = Generator.create[Int, Int] {
      s =>
        (s < 1000).option {
          val ss = s + 100
          (ss, ss)
        }
    }.withZero(100)


    val generator2 = Generator.create[Int, Int] {
      s => {
        (s < 10).option {
          val ss = s + 1
          (ss, ss)
        }
      }
    }
      .withZero(1)

    val generator3 = Generator.create[Int, Int] {
      s =>
        (s < 4000).option {
          val ss = s + 10
          (ss, ss)
        }
    }.withZero(2000)

    // Thread.sleep(1000) for generator2 results in the some of the results of generator2 to be the last one to stdout
    Generator.run[IO, Int, Int](generator1, generator2, generator3)(a => IO { println(Thread.currentThread().getName + " " + a) }).unsafeRunSync()
  }
}
