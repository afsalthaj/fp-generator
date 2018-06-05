package com.thaj.generator

import scala.concurrent.duration._
import cats.effect.IO
import org.scalacheck.Gen
import scalaz.syntax.std.boolean._
import org.specs2.{ScalaCheck, Specification}

import scala.util.Try
import org.specs2.matcher.ResultMatchers
import org.specs2.specification.Retries

import scala.concurrent.Await

class SimpleProcessSpec  extends Specification with ScalaCheck  with ResultMatchers with Retries {
  def is =
    s2"""
        $testDataLossInSimpleProcess
      """

  private def testDataLossInSimpleProcess = {
    prop {(n: Int) => {
      val generator = Generator.create[Int, Int] {
        s => {
          (s < n).option {
            val ss = s + 1
            (ss, ss)
          }
        }
      }.withZero(0)

      val number = new java.util.concurrent.atomic.AtomicLong(0)

      val fut = Generator.run[IO, Int, Int](generator)(a => IO {
        number.getAndAdd(a)
      }).unsafeToFuture()

      // TODO; The test that tests determinism is not determinstic as we guess how much to wait !
      Try { Await.result(fut, 1.seconds)}.fold(_ => number.get() must_=== n * (n + 1) / 2, _ => ko)
    }}.set(minTestsOk = 5).setGen(Gen.choose(1, 10))
  }
}
