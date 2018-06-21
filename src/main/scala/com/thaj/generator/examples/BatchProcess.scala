package com.thaj.generator.examples

import cats.effect.IO
import com.thaj.generator.{Generator, GeneratorLogic}
import scalaz.syntax.std.boolean._

object BatchProcess {
  def main(args: Array[String]): Unit = {

    // Our generator is as simple as specifying a zero val and the state changes
    // Here the state is `Int` and the value is also `Int` for simplicity purpose.
    val generator: GeneratorLogic[Int, Int] =  GeneratorLogic.create {
      s => {
        (s < 401).option {
          val ss = s + 1
          (ss, ss)
        }
      }
    }

    // Generate data based on the above rule, and internally it batches the data.
    // All we need to do is pass the batch size, generator and the action that is to be done on each batch.
    Generator.runBatch[IO, Int, Int](8, generator.withZero(0).withDelay(1000))(list => IO { println(list) }).unsafeRunSync()
  }
}
