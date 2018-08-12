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
        comparison will not be done and tracks them as new and deleted $simpleNestedCaseClassWithDifferentNames
      Multiple inner case classes with same names, such that changed values will be tracked correctly $multipleInnerCaseClassWithSameNames
      Multiple inner case classes with different names, such that new and deleted will be tracked correctly $multipleInnerCaseClassWithDifferentNames
      Multiple inner case classes with same and different names, such that new, deleted and changed will be tracked correctly $multipleInnerCaseClassWithDifferentNamesAndSameNames
      2 level nesting will be compared correctly $nestedToNestedInnerClassWithSameNames
       $nestedToNestedInnerClassWithDifferentNames
       $nestedToNestedInnerClassWithSameAndDifferentNames
       $nestedToNestedInnerClassWithSameAndDifferentNamesWith3Levels
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

    FindDeltaMeta[Exchange].apply(s1, s2) must_===
      Meta("storageAccount.storage-name2","<no value>","StorageAccount(newValue,storage-name2)") ++
        Meta("storageAccount.storage-name1","StorageAccount(oldValue,storage-name1)", "<deleted>")
  }

  private def multipleInnerCaseClassWithSameNames = {
    case class StorageAccount(tier: String, name: String)
    case class DataLakeAccount(sku: String, name: String)
    case class Exchange(storageAccount: StorageAccount, dataLakeAccount: DataLakeAccount)

    implicit val hasNameStorageAccount: HasName[StorageAccount] = _.name
    implicit val hasNameDataLakeAccount: HasName[DataLakeAccount] = _.name

    val s1 = Exchange(StorageAccount("oldValue", "storage-name"), DataLakeAccount("oldValue", "datalake-name"))
    val s2 = Exchange(StorageAccount("newValue", "storage-name"), DataLakeAccount("newValue", "datalake-name"))

    FindDeltaMeta[Exchange].apply(s1, s2) must_===
      List(
        UpdateInfo("storageAccount.storage-name.tier", "oldValue", "newValue"),
        UpdateInfo("dataLakeAccount.datalake-name.sku", "oldValue", "newValue")
      )
  }

  private def multipleInnerCaseClassWithDifferentNames = {
    case class StorageAccount(tier: String, name: String)
    case class DataLakeAccount(sku: String, name: String)
    case class Exchange(storageAccount: StorageAccount, dataLakeAccount: DataLakeAccount)

    implicit val hasNameStorageAccount: HasName[StorageAccount] = _.name
    implicit val hasNameDataLakeAccount: HasName[DataLakeAccount] = _.name

    val s1 = Exchange(StorageAccount("oldValue", "storage-name1"), DataLakeAccount("oldValue", "datalake-name1"))
    val s2 = Exchange(StorageAccount("newValue", "storage-name2"), DataLakeAccount("newValue", "datalake-name2"))

    FindDeltaMeta[Exchange].apply(s1, s2) must_===
      Meta("storageAccount.storage-name2","<no value>","StorageAccount(newValue,storage-name2)") ++
        Meta("storageAccount.storage-name1","StorageAccount(oldValue,storage-name1)", "<deleted>") ++
        Meta("dataLakeAccount.datalake-name2","<no value>","DataLakeAccount(newValue,datalake-name2)") ++
        Meta("dataLakeAccount.datalake-name1","DataLakeAccount(oldValue,datalake-name1)", "<deleted>")
  }

  private def multipleInnerCaseClassWithDifferentNamesAndSameNames = {
    case class StorageAccount(tier: String, name: String)
    case class DataLakeAccount(sku: String, name: String)
    case class Exchange(storageAccount: StorageAccount, dataLakeAccount: DataLakeAccount)

    implicit val hasNameStorageAccount: HasName[StorageAccount] = _.name
    implicit val hasNameDataLakeAccount: HasName[DataLakeAccount] = _.name

    val s1 = Exchange(StorageAccount("oldValue", "storage-name"), DataLakeAccount("oldValue", "datalake-name1"))
    val s2 = Exchange(StorageAccount("newValue", "storage-name"), DataLakeAccount("newValue", "datalake-name2"))

    FindDeltaMeta[Exchange].apply(s1, s2) must_===
      Meta("storageAccount.storage-name.tier", "oldValue", "newValue") ++
        Meta("dataLakeAccount.datalake-name2","<no value>","DataLakeAccount(newValue,datalake-name2)") ++
        Meta("dataLakeAccount.datalake-name1","DataLakeAccount(oldValue,datalake-name1)", "<deleted>")
  }

  private def nestedToNestedInnerClassWithSameNames = {
    case class Acl(value: String, name: String)
    case class DataLakeAccount(acl: Acl, name: String)
    case class Exchange(dataLakeAccount: DataLakeAccount)

    implicit val hasNameStorageAccount: HasName[DataLakeAccount] = _.name
    implicit val hasNameAcl: HasName[Acl] = _.name

    val s1 = Exchange(DataLakeAccount(Acl("oldValue", "acl-name"), "datalake-name"))
    val s2 = Exchange(DataLakeAccount(Acl("newValue", "acl-name"), "datalake-name"))
    val s3 = Exchange(DataLakeAccount(Acl("oldValue", "acl-name"), "datalake-name"))
    val s4 = Exchange(DataLakeAccount(Acl("oldValue", "acl-name"), "datalake-name"))

    (FindDeltaMeta[Exchange].apply(s1, s2) must_=== Meta("dataLakeAccount.datalake-name.acl.acl-name.value","oldValue","newValue")) and (
      FindDeltaMeta[Exchange].apply(s3, s4) must_=== Meta.empty
    )
  }

  private def nestedToNestedInnerClassWithDifferentNames = {
    case class Acl(value: String, name: String)
    case class DataLakeAccount(acl: Acl, name: String)
    case class Exchange(dataLakeAccount: DataLakeAccount)

    implicit val hasNameStorageAccount: HasName[DataLakeAccount] = _.name
    implicit val hasNameAcl: HasName[Acl] = _.name

    val s1 = Exchange(DataLakeAccount(Acl("oldValue", "acl-name1"), "datalake-name"))
    val s2 = Exchange(DataLakeAccount(Acl("newValue", "acl-name2"), "datalake-name"))
    val s3 = Exchange(DataLakeAccount(Acl("oldValue", "acl-name3"), "datalake-name"))
    val s4 = Exchange(DataLakeAccount(Acl("oldValue", "acl-name4"), "datalake-name"))

    (FindDeltaMeta[Exchange].apply(s1, s2) must_===
      Meta("dataLakeAccount.datalake-name.acl.acl-name2","<no value>","Acl(newValue,acl-name2)") ++
        Meta("dataLakeAccount.datalake-name.acl.acl-name1","Acl(oldValue,acl-name1)","<deleted>")) and (
      FindDeltaMeta[Exchange].apply(s3, s4) must_===
        Meta("dataLakeAccount.datalake-name.acl.acl-name4","<no value>","Acl(oldValue,acl-name4)") ++
          Meta("dataLakeAccount.datalake-name.acl.acl-name3","Acl(oldValue,acl-name3)","<deleted>")
      )
  }

  private def nestedToNestedInnerClassWithSameAndDifferentNames = {
    case class Acl(value: String, name: String)
    case class DataLakeAccount(acl: Acl, name: String)
    case class Exchange(dataLakeAccount: DataLakeAccount)

    implicit val hasNameStorageAccount: HasName[DataLakeAccount] = _.name
    implicit val hasNameAcl: HasName[Acl] = _.name

    val s1 = Exchange(DataLakeAccount(Acl("oldValue", "acl-name"), "datalake-name"))
    val s2 = Exchange(DataLakeAccount(Acl("newValue", "acl-name"), "datalake-name"))
    val s3 = Exchange(DataLakeAccount(Acl("oldValue", "acl-name3"), "datalake-name"))
    val s4 = Exchange(DataLakeAccount(Acl("oldValue", "acl-name4"), "datalake-name"))

    (FindDeltaMeta[Exchange].apply(s1, s2) must_===
      Meta("dataLakeAccount.datalake-name.acl.acl-name.value","oldValue","newValue")) and (
      FindDeltaMeta[Exchange].apply(s3, s4) must_===
        Meta("dataLakeAccount.datalake-name.acl.acl-name4","<no value>","Acl(oldValue,acl-name4)") ++
          Meta("dataLakeAccount.datalake-name.acl.acl-name3","Acl(oldValue,acl-name3)","<deleted>")
      )
  }

  private def nestedToNestedInnerClassWithSameAndDifferentNamesWith3Levels = {
    case class Permission(permisson: String, name: String)
    case class Acl(permission: Permission, name: String)
    case class DataLakeAccount(acl: Acl, name: String)
    case class Exchange(dataLakeAccount: DataLakeAccount)

    // TODO; Fix missing Permission implicit results in a different result due to lower priority implicit.
    // Catch this at compile time.
    implicit val hasNameStorageAccount: HasName[DataLakeAccount] = _.name
    implicit val hasNameAcl: HasName[Acl] = _.name
    implicit val hasNamePerm: HasName[Permission] = _.name

    val s1 = Exchange(DataLakeAccount(Acl(Permission("oldValue", "permission-name"), "acl-name"), "datalake-name"))
    val s2 = Exchange(DataLakeAccount(Acl(Permission("newValue", "permission-name"), "acl-name"), "datalake-name"))
    val s3 = Exchange(DataLakeAccount(Acl(Permission("oldValue", "permission-name1"), "acl-name"), "datalake-name"))
    val s4 = Exchange(DataLakeAccount(Acl(Permission("oldValue", "permission-name2"), "acl-name"), "datalake-name"))

    (FindDeltaMeta[Exchange].apply(s1, s2) must_===
      Meta("dataLakeAccount.datalake-name.acl.acl-name.permission.permission-name.permisson","oldValue","newValue")) and (
      FindDeltaMeta[Exchange].apply(s3, s4) must_=== Meta(
        "dataLakeAccount.datalake-name.acl.acl-name.permission.permission-name2","<no value>","Permission(oldValue,permission-name2)"
      ) ++ Meta(
        "dataLakeAccount.datalake-name.acl.acl-name.permission.permission-name1","Permission(oldValue,permission-name1)", "<deleted>"
      )
    )
  }
}