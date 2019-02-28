package com.thaj.generator

import scalaz.{EitherT, Monad, \/}
import scalaz.syntax.either._
import scala.{ Stream => SStream}
import scalaz.Applicative

trait GeneratorLogic[S, A] { self =>
  def next: S => Option[(S, A)]

  def map[B](f: A => B): GeneratorLogic[S, B] =
    GeneratorLogic.create(self.next(_).map { case (s, a) => (s, f(a)) })

  def >>=[B](f: A => GeneratorLogic[S, B]): GeneratorLogic[S, B] =
    new GeneratorLogic[S, B] {
      override def next: S => Option[(S, B)] = s => {
        self.next(s).flatMap{ case(st, v) => f(v).next(st) }
      }
    }

  def flatMap[B](f: A => GeneratorLogic[S, B]): GeneratorLogic[S, B] = >>=(f)

  def map2[B, C](b: GeneratorLogic[S, B])(f: (A, B) => C): GeneratorLogic[S, C] =
    self >>= (bb => b.map(cc => f(bb, cc)))

  def replicateM(n: Int): GeneratorLogic[S, List[A]] =
    GeneratorLogic.sequence[S, A](List.fill(n)(self))

  def runToStream: S => SStream[A] =
    z => self.next(z).fold(SStream.empty[A]){ case ((ss, a)) => SStream.cons(a, runToStream(ss)) }

  @deprecated("Use Generator.run methods")
  def run[F[_]: Monad, E](zero: S)(sideEffect: A => F[E \/ Unit]): F[\/[E, Unit]] =
    GeneratorLogic.unfoldM(zero)(self.next)(sideEffect)
}

object GeneratorLogic { self =>
  def apply[A, B](implicit ev: GeneratorLogic[A, B]): GeneratorLogic[A, B] = ev

  def create[S, A](f: S => Option[(S, A)]): GeneratorLogic[S, A] =
    new GeneratorLogic[S, A] {
      def next: (S) => Option[(S, A)] = f
    }

  def point[S, A](a: A): GeneratorLogic[S, A]  =
    create[S, A](s => Some(s, a))

  def sequence[S, A](list: List[GeneratorLogic[S, A]]): GeneratorLogic[S, List[A]] =
    list.foldLeft(self.point[S, List[A]](Nil: List[A]))((acc, a) => a.map2(acc)(_ :: _))

  @deprecated("Runs without handling any backpressure/batching/concurrency. Use Generator.run methods")
  def unfoldM[F[_]: Monad, S, A, E](z: S)(f: S => Option[(S, A)])(sideEffect: A => F[E \/ Unit]): F[E \/ Unit] = {
    f(z).fold(
      scalaz.Applicative[F].pure(println("Finished sending the data!").right[E])
    ) {
      case (state, value) =>
        EitherT(sideEffect(value)).foldM(
         t => scalaz.Applicative[F].pure(t.left[Unit]),
          _ =>
            unfoldM[F, S, A, E](state)(f)(sideEffect)
        )
    }
  }

  implicit def applicativeLogic[A]: Applicative[GeneratorLogic[A, ?]] = new Applicative[GeneratorLogic[A, ?]]{
    override def point[B](b: => B) = GeneratorLogic.point(b)
    override def ap[B,C](fa: => GeneratorLogic[A, B])(f: => GeneratorLogic[A, B => C]): GeneratorLogic[A, C] = 
      fa.flatMap(b => f.map(_(b)))
  } 

  implicit class GeneratorOps[S, A](g: GeneratorLogic[S, A]) {
    def withZero(z: S): Generator[S, A] = Generator(g, z)
  }
}
