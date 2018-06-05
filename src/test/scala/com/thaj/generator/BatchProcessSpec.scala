package com.thaj.generator

import cats.effect.IO
import org.scalacheck.Gen
import org.specs2.{ScalaCheck, Specification}

import scala.concurrent.duration._
import scalaz.syntax.std.boolean._

import scala.concurrent.Await
import scala.util.Try

class BatchProcessSpec extends Specification with ScalaCheck {
  def is =
    s2"""
         $testNoDataLossForBatch
      """

  private def testNoDataLossForBatch = {
    prop {(n: Int) => {
      val number = new java.util.concurrent.atomic.AtomicLong(0)

      val generator = Generator.create[Int, Int] {
        s => {
          (s < n).option {
            val ss = s + 1
            (ss, ss)
          }
        }
      }.withZero(0)

      val fut = Generator.runBatch[IO, Int, Int](n/2, generator)(a => IO {
        number.getAndAdd(a.sum[Int])
      }).unsafeToFuture()

      // TODO; The test that tests determinism is not determinstic as we guess how much to wait !
      Try { Await.result(fut, 1.seconds)}.fold(_ => number.get() must_=== n * (n + 1)/2, _ => ko)
    }}.set(minTestsOk = 5).setGen(Gen.choose(1, 10))
  }

}
