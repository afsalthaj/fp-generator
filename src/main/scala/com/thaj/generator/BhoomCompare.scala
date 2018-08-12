package com.thaj.generator

import shapeless.{::, HList, HNil, LabelledGeneric, Lazy, Witness}
import shapeless.labelled.FieldType
import cats.evidence.{As, Is}
import scalaz.{Name => _, _}
import Scalaz._
import com.thaj.generator.Delta.Meta
import Delta._


trait FindDeltaMeta[A] {
  def apply(a: A, b: A): Delta.Meta
}

object FindDeltaMeta extends LowPriorityInstances0 {
  def apply[A](implicit ev: FindDeltaMeta[A]): FindDeltaMeta[A] = ev
}

trait LowPriorityInstances0 extends LowPriorityInstances1 {
  implicit def hNilNamer: FindDeltaMeta[HNil] =
    (_, _) => Delta.Meta.empty

  implicit def hListThatCanHaveFOfGOfAnyVal[F[_], G[_], A, B, K <: Symbol, T <: HList](
    implicit
    witness: Witness.Aux[K],
    IsList: A Is F[G[B]],
    IsAnyVal: B As AnyVal,
    D: Lazy[FindDeltaMeta[T]],
  ): FindDeltaMeta[FieldType[K, A] :: T] =
    (a, b) =>
      (if (a.head == b.head)
        Meta.empty
      else
        Meta(witness.value.name, (a.head: A).toString, (b.head: A).toString)) ++ D.value.apply(a.tail, b.tail)

  implicit def findDiffA[A, R <: HList](implicit E: LabelledGeneric.Aux[A, R], D: FindDeltaMeta[R]): FindDeltaMeta[A] = {
    (a, b) => D.apply(E.to(a), E.to(b))
  }
}

trait LowPriorityInstances1 extends LowPriorityInstances2 {
  implicit def hListWithSimpleAnyVal[A, K <: Symbol, T <: HList](
    implicit
    witness: Witness.Aux[K],
    IsAnyVal: A As AnyVal,
    D: Lazy[FindDeltaMeta[T]],
  ): FindDeltaMeta[FieldType[K, A] :: T] =
    (a, b) =>
      (if (a == b)
        Meta.empty
      else
        Meta(witness.value.name, (a.head: A).toString, (b.head: A).toString)) ++ D.value.apply(a.tail, b.tail)
}

trait LowPriorityInstances2 extends LowPriorityInstances3 {
  implicit def hLIstWithFofHListInsideIt[A, K <: Symbol, H, InnerT <: HList, T <: HList](
    implicit
    witness: Witness.Aux[K],
    IsList: H As List[A],
    eachH: LabelledGeneric.Aux[A, InnerT],
    D: Lazy[FindDeltaMeta[T]],
    E: Lazy[FindDeltaMeta[InnerT]]
  ): FindDeltaMeta[FieldType[K, H] :: T] =
    (a, b) => {
      val r: Seq[(A, A)] = a.head.asInstanceOf[List[A]].flatMap(aa => b.head.asInstanceOf[List[A]].map(bb => (aa, bb)))
      val r2 = { r.map {case (aa, bb) => E.value.apply(eachH.to(aa), eachH.to(bb))} }
      r2.reduce(_ ++ _).mapKeys(t => witness.value.name + "." +t ) ++ D.value.apply(a.tail, b.tail)
    }
}

trait LowPriorityInstances3 extends LowPriorityInstances4 {
  implicit def hListNamerWithHListInsideOfInsideOf[K <: Symbol, H, InnerT <: HList, T <: HList](
    implicit
    witness: Witness.Aux[K],
    eachH: LabelledGeneric.Aux[H, InnerT],
    D: Lazy[FindDeltaMeta[T]],
    E: Lazy[FindDeltaMeta[InnerT]]
  ): FindDeltaMeta[FieldType[K, H] :: T] =
    (a, b) =>
       E.value.apply(eachH.to(a.head.asInstanceOf[H]),
         eachH.to(b.head.asInstanceOf[H])).mapKeys(t => witness.value.name + "." +t) ++
         D.value.apply(a.tail, b.tail)
}

trait LowPriorityInstances4 {
  implicit def simpleHList[A, K <: Symbol, H, T <: HList](
       implicit
       witness: Witness.Aux[K],
       D: Lazy[FindDeltaMeta[T]]
  ): FindDeltaMeta[FieldType[K, H] :: T] =
    new FindDeltaMeta[FieldType[K, H] :: T] {
      override def apply(a: FieldType[K, H] :: T, b: FieldType[K, H] :: T) : Delta.Meta =
        (if (a.head == b.head)
          Meta.empty
        else
          Meta(witness.value.name, (a.head: H).toString, (b.head: H).toString)) ++ D.value.apply(a.tail, b.tail)
    }
}

object BlaXX extends App {
  case class Blob(name: String, usersPermitted: List[String])
  case class StorageAccount(blob: Blob, fileNames: List[String])
  case class Exchange(storageAccounts: List[StorageAccount], sku: String, tier: String)


  val blob1 = Blob("sdasd", List("afsal", "thaj", "bi"))
  val blob2 = Blob("asd", List("ds"))

 // println(FindDiff[Blob].apply(blob1, blob2).map(_.shows))

  val blob = Blob("afsal", List("sdasd", "sdadad"))

  val o1 = StorageAccount(blob1, List(""))
  val o2 = StorageAccount(blob2, List("sad"))
  val o3 = StorageAccount(blob, List("sdadasd"))

  val o4 = Exchange(List(o1, o2), "1", "a")
  val o5 = Exchange(List(o3, o2), "2", "b")

  println(FindDeltaMeta[Exchange].apply(o4, o5).sortBy(_.key).map(_.shows))


  // There are a few bugs in output, but you get the idea

}