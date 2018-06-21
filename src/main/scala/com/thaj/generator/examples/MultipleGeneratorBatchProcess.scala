package com.thaj.generator.examples

import cats.effect.IO
import com.thaj.generator.{Generator, GeneratorLogic}
import scalaz.syntax.std.boolean._

object MultipleGeneratorBatchProcess {
  def main(args: Array[String]): Unit = {

    val generator1 =
      GeneratorLogic.create[Int, Int] {
        s =>
          (s < 100).option {
            (s + 1, s + 1)
          }
      }.withZero(0)

    // Note that the thread.sleep(100) when batching with an `n` results in a delay n*100.
    // In this case, the n is 10, then we have a delay of 1000ms between each element in generator2
    // while threads for other generators will keep running.
    val generator2 =
      GeneratorLogic.create[Int, Int] {
        s =>
          (s < 10000).option {
            (s + 100, s + 100)
          }
      }.withZero(2000)

    // Some instances may have different rules, different starting points
    val generator3 =
      GeneratorLogic.create[Int, Int] {
        s => {
          (s < 200000).option {
            (s + 100,  s + 100)
          }
        }
      }.withZero(100000)

   // Thread.sleep(1000) for generator2 results in the some of the results of generator2 to be the last one to stdout
    Generator.runBatch[IO, Int, Int](10, generator1, generator2, generator3){
      list => IO { println(list) }
    }.unsafeRunSync()
  }
}
