package com.thaj.generator

import com.thaj.generator.fs2stream.Delay.delayed
import cats.effect.Effect
import com.thaj.generator.fs2stream.Fs2PublisherSubscriber
import fs2.Stream

import scala.concurrent.ExecutionContext.Implicits.global

final case class Generator[S, A](g: GeneratorLogic[S, A], zero: S, delay: Option[Int] = None) {
  def asFs2Stream[F[_] : Effect]: Stream[F, (S, A)] = Generator.asFs2Stream[F, S, A](zero, delay)(g.next)
}

object Generator {
  private[generator] def asFs2Stream[F[_], S, A](z: S, delay: Option[Int])(f: S => Option[(S, A)])(implicit F: Effect[F]): Stream[F, (S, A)] =
    delayed(f(z), delay).flatMap(_.fold(Stream.empty.covaryAll[F,(S, A)]){
      case (state, value) =>
        Stream.eval[F, (S, A)](F.delay((state, value))) ++ asFs2Stream[F, S, A](state, delay)(f)
    })

  /**
    * Generate a batch of data
    */
  def run[F[_]: Effect, S, A](gens: Generator[S, A]*)(f: A => F[Unit], maxRate: Int = 4): F[Unit] =
    Fs2PublisherSubscriber.withQueue(gens.map(_.asFs2Stream[F].map(_._2)).reduce(_ merge _), f, maxRate).compile.drain

  /**
    * Generate a batch of data asynchronously, and process them asynchronously.
    */
  def runBatch[F[_], S, A](n: Int, gens: Generator[S, A]*)(f: List[A] => F[Unit], maxRate: Int = 4)(implicit F: Effect[F]): F[Unit] =
    Fs2PublisherSubscriber.withQueue(
      gens.map(t => {
        val str = t.g.replicateM(n).withZero(t.zero).copy(delay = t.delay).asFs2Stream[F]

        str.noneTerminate.head.collectFirst[List[A]] {
          case None => t.g.runToStream(t.zero).toList
        } onComplete (str.zipWithNext flatMap {
          case ((s, l), None) => Stream(l) ++ delayed(t.g.runToStream(s).toList, t.delay)
          case ((_, l), Some(_)) => Stream.eval(F.delay(l))
        })
    }.filter(_.nonEmpty)).reduce(_ merge _), f, maxRate).compile.drain

  /**
    * Synchronous emission and processing of data
    */
  def runSync[F[_]: Effect, S, A](gens: Generator[S, A]*)(f: A => F[Unit]): F[Unit] =
    gens.map(_.asFs2Stream[F].map(_._2)).reduce(_ merge _).evalMap(f).compile.drain


  implicit class GeneratorWithZeroOps[S, A](g: Generator[S, A]) {
    def withDelay(n: Int): Generator[S, A] = g.copy(delay = Some(n))
  }
}
