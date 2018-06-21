package com.thaj.generator

import org.specs2.{ScalaCheck, Specification}
import scalaz.{Equal, Monad}

object GeneratorLogicLawSpec extends Specification with ScalaCheck {
  def is =
    s2"""
         Generator type is in fact a monad, and follows monadic laws $generatorLaw
      """

  implicit def monad[S]: Monad[GeneratorLogic[S, ?]] = new Monad[GeneratorLogic[S, ?]] {
    override def point[A](a: => A): GeneratorLogic[S, A] = GeneratorLogic.point(a)
    override def bind[A, B](fa: GeneratorLogic[S, A])(f: A => GeneratorLogic[S, B]): GeneratorLogic[S,B] = fa >>= f
  }

  implicit def equal[A]: Equal[GeneratorLogic[Int, A]] = new Equal[GeneratorLogic[Int, A]] {
    override def equal(a1: GeneratorLogic[Int, A], a2: GeneratorLogic[Int, A]): Boolean =
      a1.next(0) == a2.next(0)
  }

  private def generatorLaw = {
    prop {(x: Int, y: Int) => {
      val generator = GeneratorLogic.create[Int, Int](x => if ( x == 0) None else Some(x + y, x + y))

      val law = Monad[GeneratorLogic[Int, ?]].monadLaw

      law.rightIdentity(generator) &&
        law.leftIdentity[Int, Int](x, y => generator.map(_ + y)) &&
        law.identityAp(generator) &&
        law.homomorphism[Int, Int](identity, x) &&
        law.interchange[Int, Int](GeneratorLogic.point(_ + y), x) &&
        law.mapLikeDerived[Int, Int](_ + y, GeneratorLogic.point(x)) &&
        law.composition[Int, Double, String](GeneratorLogic.point(_.toString), GeneratorLogic.point(_.toDouble), generator) &&
        law.associativeBind[Int, Double, String](generator, a => generator.map(_ + a),  b => generator.map(_.toString + b.toString))// &&
        law.apLikeDerived[Int, String](generator, generator.map( a => b => (a + b + y).toString))
    }}
  }
}
