package com.thaj.generator.examples

import cats.effect.IO
import com.thaj.generator.GeneratorLogic
import com.thaj.generator.Generator
import scalaz.syntax.applicative._
import scalaz.syntax.std.boolean._

final case class ProductType (name: String, int: Int)

object ImplicitGeneration {
  def main(args: Array[String]): Unit = {

    // Our generator is as simple as specifying a zero val and the state changes
    // Here the state is `Int` and the value is also `Int` for simplicity purpose.
    implicit val generateInt: GeneratorLogic[ProductType, Int] =  GeneratorLogic.create {
      s => {
        (s.int < 100).option {
          val ss = s.int + 2
          (s.copy(int = ss), ss)
        }
      }
    }

    implicit val generatorForName: GeneratorLogic[ProductType, String]  = GeneratorLogic.create {
      s => {
        Some((s, s.name))
      }
    }

    val generator: GeneratorLogic[ProductType, ProductType] = (GeneratorLogic[ProductType, String] |@| GeneratorLogic[ProductType, Int]) {
      ProductType.apply
    }

    // Generate data based on the above rule, and internally it batches the data.
    // All we need to do is pass the batch size, generator and the action that is to be done on each batch.
    Generator.runBatch[IO, ProductType, ProductType](10, generator.withZero(ProductType("afsal", 0)))(list => IO { println(list) }).unsafeRunSync()
  }
}