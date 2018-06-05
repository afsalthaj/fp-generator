package com.thaj.generator.examples

import cats.effect.IO
import com.thaj.generator.Generator
import scalaz.syntax.std.boolean._

object SimpleProcess {
  def main(args: Array[String]): Unit = {

    // Our generator is as simple as specifying a zero val and the state changes
    val generator: Generator[Int, Int] =  Generator.create(0) {
      s => {
        (s < 100).option {
          val ss = s + 1
          (ss, ss)
        }
      }
    }

    // Generate data based on the above rule.
    // All we need to do is pass generator and the action that is to be done on each data.
    Generator.run[IO, Int, Int](generator)(a => IO { println(a) }).unsafeRunSync()
  }
}
