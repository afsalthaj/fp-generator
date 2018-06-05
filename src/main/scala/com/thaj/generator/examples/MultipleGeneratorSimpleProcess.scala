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

    // Some instances may have different rules, different starting points
    val generator2: Generator[Int, Int] =  Generator.create(2000) {
      s => {
        (s < 10000).option {
          val ss = s + 100
          (ss, ss)
        }
      }
    }

    // Some instances may have different rules, different starting points
    val generator3: Generator[Int, Int] =  Generator.create(100000) {
      s => {
        (s < 200000).option {
          val ss = s + 10000
          (ss, ss)
        }
      }
    }

    // Now we use all these generators to generate data, interleaved randomly.
    // All we need to do is, pass all the generators to run method, and the effect to be done on data
    // Note that, under the hood the generation continues until all the generator's termination condition met.
    // This is achieved through fs2's interleaveAll method under the hood.
    Generator.run[IO, Int, Int](generator1, generator2, generator3)(a => IO { println(a) }).unsafeRunSync()
  }
}
