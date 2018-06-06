package com.thaj.generator.examples

import cats.effect.IO
import com.thaj.generator.Generator
import scalaz.syntax.std.boolean._

object MultipleGeneratorSimpleProcess {
  def main(args: Array[String]): Unit = {

    // Our generator is as simple as specifying a zero val and the state changes
    val generator1: Generator[Int, Int] =  Generator.create(0) {
      s => {
        (s < 100).option {
          val ss = s + 1
          (ss, ss)
        }
      }
    }


    val generator2: Generator[Int, Int] =  Generator.create(2000) {
      s => {
        Thread.sleep(1000)
        (s < 10000).option {
          val ss = s + 100
          (ss, ss)
        }
      }
    }

    val generator3: Generator[Int, Int] =  Generator.create(100) {
      s => {
        (s < 20000000).option {
          val ss = s + 10000
          (ss, ss)
        }
      }
    }

    // Thread.sleep(1000) for generator2 results in the some of the results of generator2 to be the last one to stdout
    Generator.run[IO, Int, Int](generator1, generator2, generator3)(a => IO { println(a) }).unsafeRunSync()
  }
}
