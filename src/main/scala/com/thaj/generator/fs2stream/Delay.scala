package com.thaj.generator.fs2stream

import cats.effect.Effect
import scala.concurrent.duration._
import cats.syntax.flatMap._
import scala.concurrent.ExecutionContext.Implicits.global
import fs2.Stream

trait Delay {
  def delayed[F[_], A](b: => A, delay: Option[Int])(implicit F: Effect[F]): Stream[F, A] =
    Stream.eval[F, A] {
      F.liftIO {
        cats.effect.IO.sleep(delay.getOrElse(0).milliseconds)
      }.flatMap(_ => F.delay(b))
    }
}

object Delay extends Delay
