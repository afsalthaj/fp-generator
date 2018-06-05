package com.thaj.generator.examples

import cats.effect.IO
import com.thaj.generator.Generator
import scalaz.syntax.std.boolean._

object MultipleGeneratorBatchProcess {
  def main(args: Array[String]): Unit = {

    // Our generator is as simple as specifying a zero val and the state changes
    // Here the state is `Int` and the value is also `Int` for simplicity purpose.
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

    // Now we use all these generators to generate data batch by batch.
    // The batching  and the state management of each batch and across the batch is done
    // internally. The batches are interleaved with each other through fs2 combinators under the hood.
    // All we need to do is, pass all the generators to runBatch method, and the effect to be done on each batch of data
    Generator.runBatch[IO, Int, Int](10, generator1, generator2, generator3)(list => IO { println(list) }).unsafeRunSync()
  }
}
