package com.thaj.generator

import com.thaj.generator.Delta.{Meta, UpdateInfo}
import org.specs2.{ScalaCheck, Specification}

class FindMetaSpec  extends Specification with ScalaCheck {
  def is =
    s2"""
      $simpleTestOfCaseClass
      $simpleTestForString
      Works for simple test for a case class with a Double field in it $simpleTestForDouble
      Works for simple test for a case class with a Long field in it $simpleTestForLong
      If there is a nested case class in the Exchange, the inner case class should have a HasName instance and will be compared only
        with an inner class having the same name $simpleNestedCaseClassWithSameNames
      If there is a nested case class in the Exchange, the inner case class havng a different names and the
        comparison will not be done $simpleNestedCaseClassWithDifferentNames
      """

  private def simpleTestOfCaseClass = {
    case class Exchange(field: String)

    val s1 = Exchange("value1")
    val s2 = Exchange("value1changed")

    FindDeltaMeta[Exchange].apply(s1, s2) must_=== List(UpdateInfo("field", "value1", "value1changed"))
  }

  private def simpleTestForString = {
    case class Exchange(field: Int)

    val s1 = Exchange(1)
    val s2 = Exchange(2)

    FindDeltaMeta[Exchange].apply(s1, s2) must_=== List(UpdateInfo("field", "1", "2"))
  }

  private def simpleTestForDouble = {
    case class Exchange(field: Double)

    val s1 = Exchange(1)
    val s2 = Exchange(2)

    FindDeltaMeta[Exchange].apply(s1, s2) must_=== List(UpdateInfo("field", "1.0", "2.0"))
  }

  private def simpleTestForLong = {
    case class Exchange(field: Long)

    val s1 = Exchange(1)
    val s2 = Exchange(2)

    FindDeltaMeta[Exchange].apply(s1, s2) must_=== List(UpdateInfo("field", "1", "2"))
  }

  // If there is a nested class, the code doesn't compile without a HasName instance.
  // The key strucutre is key.<keyname>.innerKey.<innerKeyName>.field. Ex: storageAccount.<name-of-storageaccount>.tier
  private def simpleNestedCaseClassWithSameNames = {
    case class StorageAccount(tier: String, name: String)
    case class Exchange(storageAccount: StorageAccount)

    implicit val hasNameStorageAccount: HasName[StorageAccount] = _.name

    val s1 = Exchange(StorageAccount("oldValue", "storage-name"))
    val s2 = Exchange(StorageAccount("newValue", "storage-name"))

    FindDeltaMeta[Exchange].apply(s1, s2) must_=== List(UpdateInfo("storageAccount.storage-name.tier", "oldValue", "newValue"))
  }

  private def simpleNestedCaseClassWithDifferentNames = {
    case class StorageAccount(tier: String, name: String)
    case class Exchange(storageAccount: StorageAccount)

    implicit val hasNameStorageAccount: HasName[StorageAccount] = _.name

    val s1 = Exchange(StorageAccount("oldValue", "storage-name1"))
    val s2 = Exchange(StorageAccount("newValue", "storage-name2"))

    FindDeltaMeta[Exchange].apply(s1, s2) must_=== Meta.empty
  }
}