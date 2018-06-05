package com.thaj.generator.examples

import cats.effect.IO
import com.thaj.generator.Generator
import scalaz.syntax.std.boolean._

object BatchProcessWithDelay {
  def main(args: Array[String]): Unit = {

    // Our generator is as simple as specifying a zero val and the state changes
    // Here the state is `Int` and the value is also `Int` for simplicity purpose.
    val generator: Generator[Int, Int] =  Generator.create {
      s => {
        (s < 100).option {
          val ss = s + 2
          (ss, ss)
        }
      }
    }

    // With a delay of 2 seconds, there will be a delay of 2 seconds between every batch.
    Generator.runBatch[IO, Int, Int](10, generator.withZero(0).withDelay(2000))(list => IO { println(list) }).unsafeRunSync()
  }
}
