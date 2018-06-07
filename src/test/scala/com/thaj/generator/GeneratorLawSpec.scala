package com.thaj.generator

import org.specs2.{ScalaCheck, Specification}
import scalaz.{Equal, Monad}

object GeneratorLawSpec extends Specification with ScalaCheck {
  def is =
    s2"""
         Generator type is in fact a monad, and follows monadic laws $generatorLaw
      """

  implicit def monad[S]: Monad[Generator[S, ?]] = new Monad[Generator[S, ?]] {
    override def point[A](a: => A): Generator[S, A] = Generator.point(a)
    override def bind[A, B](fa: Generator[S, A])(f: A => Generator[S, B]): Generator[S,B] = fa >>= f
  }

  implicit def equal[A]: Equal[Generator[Int, A]] = new Equal[Generator[Int, A]] {
    override def equal(a1: Generator[Int, A], a2: Generator[Int, A]): Boolean =
      a1.next(0) == a2.next(0)
  }

  private def generatorLaw = {
    prop {(x: Int, y: Int) => {
      val generator = Generator.create[Int, Int](x => if ( x == 0) None else Some(x + y, x + y))

      val law = Monad[Generator[Int, ?]].monadLaw

      law.rightIdentity(generator) &&
        law.leftIdentity[Int, Int](x, y => generator.map(_ + y)) &&
        law.identityAp(generator) &&
        law.homomorphism[Int, Int](identity, x) &&
        law.interchange[Int, Int](Generator.point(_ + y), x) &&
        law.mapLikeDerived[Int, Int](_ + y, Generator.point(x)) &&
        law.composition[Int, Double, String](Generator.point(_.toString), Generator.point(_.toDouble), generator) &&
        law.associativeBind[Int, Double, String](generator, a => generator.map(_ + a),  b => generator.map(_.toString + b.toString))// &&
        law.apLikeDerived[Int, String](generator, generator.map( a => b => (a + b + y).toString))
    }}
  }
}
