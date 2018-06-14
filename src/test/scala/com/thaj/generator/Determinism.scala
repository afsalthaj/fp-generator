package com.thaj.generator

import scala.concurrent.duration._
import cats.effect.IO
import scalaz.syntax.std.boolean._
import org.specs2.{ScalaCheck, Specification}
import scala.util.{ Try}
import org.specs2.matcher.{ResultMatchers}
import org.specs2.specification.Retries

import scala.concurrent.{Await, Future}

class Determinism  extends Specification with ScalaCheck  with ResultMatchers with Retries {
  def is =
    s2"""
        $testConcurrencyDeterminism
      """

  private val generator = Generator.create[Int, Int] {
    s => {
      (s < 30).option {
        val ss = s + 1
        (ss, ss)
      }
    }
  }.withZero(0)


  private def testConcurrencyDeterminism = {
    prop {(_: Int) => {
      val number = new java.util.concurrent.atomic.AtomicLong(0)
      val fut = Generator.run[IO, Int, Int](generator)(a => IO {
        number.getAndAdd(a)
      }).unsafeToFuture()

      // TODO; Find a better way!
      Try { Await.result(fut, 0.04.seconds)}.fold(_ => number.get() must_=== 465, _ => ko)
    }}
  }
}
