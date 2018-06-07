package com.thaj.generator.examples

import cats.effect.IO
import com.thaj.generator.Generator
import scalaz.syntax.std.boolean._

object MultipleGeneratorBatchProcess {
  def main(args: Array[String]): Unit = {

    val generator1: Generator[Int, Int] =  Generator.create(0) {
      s => {
        (s < 100).option {
          val ss = s + 1
          (ss, ss)
        }
      }
    }

    // Note that the thread.sleep(100) when batching with an `n` results in a delay n*100.
    // In this case, the n is 10, then we have a delay of 1000ms between each element in generator2
    // while threads for other generators will keep running.
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
          val ss = s + 100
          (ss, ss)
        }
      }
    }

   // Thread.sleep(1000) for generator2 results in the some of the results of generator2 to be the last one to stdout
    Generator.runBatch[IO, Int, Int](10, generator1, generator2, generator3)(list => IO { println(list) }).unsafeRunSync()
  }
}
