package com.thaj.generator

import cats.effect.Effect
import fs2.Stream

final case class GeneratorWithZero[S, A](g: Generator[S, A], zero: S, delay: Option[Int] = None)

object GeneratorWithZero {
  implicit class GeneratorWithZeroOps[S, A](g: GeneratorWithZero[S, A]) {
    def asFs2Stream[F[_]: Effect]: Stream[F, A] = Generator.asFs2Stream[F, S, A](g.zero, g.delay)(g.g.next)
    def withDelay(n: Int): GeneratorWithZero[S, A] = g.copy(delay = Some(n))
  }
}
