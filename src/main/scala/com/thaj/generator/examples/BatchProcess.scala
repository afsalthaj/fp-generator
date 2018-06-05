package com.thaj.generator.examples

import cats.effect.IO
import com.thaj.generator.Generator
import scalaz.syntax.std.boolean._

object BatchProcess {
  def main(args: Array[String]): Unit = {

    // Our generator is as simple as specifying a zero val and the state changes
    // Here the state is `Int` and the value is also `Int` for simplicity purpose.
    val generator: Generator[Int, Int] =  Generator.create(0) {
      s => {
        (s < 100).option {
          val ss = s + 2
          (ss, ss)
        }
      }
    }

    // Generate data based on the above rule, and internally it batches the data.
    // All we need to do is pass the batch size, generator and the action that is to be done on each batch.
    Generator.runBatch[IO, Int, Int](10, generator, generator, generator)(list => IO { println(list) }).unsafeRunSync()
  }
}
