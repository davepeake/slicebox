package se.nimsa.sbx.app.routing

import java.util.UUID

import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.model.{ContentTypes, HttpEntity}
import akka.http.scaladsl.server._
import akka.util.ByteString
import org.scalatest.{FlatSpecLike, Matchers}
import se.nimsa.sbx.anonymization.AnonymizationProtocol._
import se.nimsa.sbx.box.BoxProtocol._
import se.nimsa.sbx.dicom.DicomHierarchy.{Image, Patient}
import se.nimsa.sbx.dicom.DicomProperty._
import se.nimsa.sbx.storage.RuntimeStorage
import se.nimsa.sbx.util.CompressionUtil._
import se.nimsa.sbx.util.FutureUtil.await
import se.nimsa.sbx.util.TestUtil

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

class TransactionRoutesTest extends {
  val dbConfig = TestUtil.createTestDb("transactionroutestest")
  val storage = new RuntimeStorage
} with FlatSpecLike with Matchers with RoutesTestBase {

  override def afterEach() = await(boxDao.clear())

  def addPollBox(name: String) =
    PostAsAdmin("/api/boxes/createconnection", RemoteBoxConnectionData(name)) ~> routes ~> check {
      status should be(Created)
      responseAs[Box]
    }

  def addPushBox(name: String) =
    PostAsAdmin("/api/boxes/connect", RemoteBox(name, "http://some.url/api/transactions/" + UUID.randomUUID())) ~> routes ~> check {
      status should be(Created)
      val box = responseAs[Box]
      box.sendMethod should be(BoxSendMethod.PUSH)
      box.name should be(name)
      box
    }

  "Transaction routes" should "be able to receive a pushed image" in {

    // first, add a box on the poll (university) side
    val uniBox =
      PostAsAdmin("/api/boxes/createconnection", RemoteBoxConnectionData("hosp")) ~> routes ~> check {
        status should be(Created)
        responseAs[Box]
      }

    // then, push an image from the hospital to the uni box we just set up
    val compressedBytes = compress(TestUtil.testImageByteArray)

    val testTransactionId = 1L
    val sequenceNumber = 1L
    val totalImageCount = 1L

    Post(s"/api/transactions/${uniBox.token}/image?transactionid=$testTransactionId&sequencenumber=$sequenceNumber&totalimagecount=$totalImageCount", HttpEntity(compressedBytes)) ~> routes ~> check {
      status should be(NoContent)
    }
  }

  it should "add a record of the received image connected to the incoming transaction" in {

    // first, add a box on the poll (university) side
    val uniBox =
      PostAsAdmin("/api/boxes/createconnection", RemoteBoxConnectionData("hosp")) ~> routes ~> check {
        responseAs[Box]
      }

    // then, push an image from the hospital to the uni box we just set up
    val compressedBytes = compress(TestUtil.testImageByteArray)

    val testTransactionId = 1L
    val sequenceNumber = 1L
    val totalImageCount = 1L

    Post(s"/api/transactions/${uniBox.token}/image?transactionid=$testTransactionId&sequencenumber=$sequenceNumber&totalimagecount=$totalImageCount", HttpEntity(compressedBytes)) ~> routes ~> check {
      status should be(NoContent)
    }

    await(boxDao.listIncomingImages) should have length 1
  }

  it should "return unauthorized when polling outgoing with unvalid token" in {
    Get(s"/api/transactions/abc/outgoing/poll") ~> Route.seal(routes) ~> check {
      status should be(Unauthorized)
    }
  }

  it should "return not found when polling empty outgoing" in {
    // first, add a box on the poll (university) side
    val uniBox = addPollBox("hosp2")

    Get(s"/api/transactions/${uniBox.token}/outgoing/poll") ~> Route.seal(routes) ~> check {
      status should be(NotFound)
    }
  }

  it should "return OutgoingTransactionImage when polling non empty outgoing" in {
    // first, add a box on the poll (university) side
    val uniBox = addPollBox("hosp3")

    // send image which adds outgoing transaction
    PostAsUser(s"/api/boxes/${uniBox.id}/send", Seq(ImageTagValues(1, Seq.empty))) ~> routes ~> check {
      status should be(NoContent)
    }

    // poll outgoing
    Get(s"/api/transactions/${uniBox.token}/outgoing/poll") ~> routes ~> check {
      status should be(OK)

      val transactionImage = responseAs[OutgoingTransactionImage]

      transactionImage.transaction.boxId should be(uniBox.id)
      transactionImage.transaction.id should not be 0
      transactionImage.transaction.sentImageCount shouldBe 0
      transactionImage.transaction.totalImageCount should be(1)
      transactionImage.transaction.status shouldBe TransactionStatus.WAITING
    }
  }

  it should "return an image file when requesting outgoing transaction" in {
    // add image (image will get id 1)
    PostAsUser("/api/images", TestUtil.testImageFormData)

    // first, add a box on the poll (university) side
    val uniBox = addPollBox("hosp4")

    // send image which adds outgoing transaction
    PostAsUser(s"/api/boxes/${uniBox.id}/send", Seq(ImageTagValues(1, Seq.empty))) ~> routes ~> check {
      status should be(NoContent)
    }

    // poll outgoing
    val transactionImage =
      Get(s"/api/transactions/${uniBox.token}/outgoing/poll") ~> routes ~> check {
        status should be(OK)
        responseAs[OutgoingTransactionImage]
      }

    // get image
    Get(s"/api/transactions/${uniBox.token}/outgoing?transactionid=${transactionImage.transaction.id}&imageid=${transactionImage.image.id}") ~> routes ~> check {
      status should be(OK)

      contentType should be(ContentTypes.`application/octet-stream`)

      val dicomData = TestUtil.loadDicomData(decompress(responseAs[ByteString].toArray), withPixelData = true)
      dicomData should not be null
    }
  }

  it should "mark outgoing image as sent when done is received" in {
    // add image (image will get id 1)
    PostAsUser("/api/images", TestUtil.testImageFormData)

    // first, add a box on the poll (university) side
    val uniBox = addPollBox("hosp5")

    // send image which adds outgoing transaction
    PostAsUser(s"/api/boxes/${uniBox.id}/send", Seq(ImageTagValues(1, Seq.empty))) ~> routes ~> check {
      status should be(NoContent)
    }

    // poll outgoing
    val transactionImage =
      Get(s"/api/transactions/${uniBox.token}/outgoing/poll") ~> routes ~> check {
        status should be(OK)
        responseAs[OutgoingTransactionImage]
      }

    // check that outgoing image is not marked as sent at this stage
    transactionImage.image.sent shouldBe false

    // send done
    Post(s"/api/transactions/${uniBox.token}/outgoing/done", transactionImage) ~> routes ~> check {
      status should be(NoContent)
    }

    // poll outgoing to check that outgoing is empty
    Get(s"/api/transactions/${uniBox.token}/outgoing/poll") ~> routes ~> check {
      status should be(NotFound)
    }

    await(boxDao.listOutgoingImages).head.sent shouldBe true

    GetAsUser(s"/api/boxes/outgoing/${transactionImage.transaction.id}/images") ~> routes ~> check {
      status shouldBe OK
      responseAs[List[Image]] should have length 1
    }

    await(boxDao.listOutgoingImages) should have length 1
    await(boxDao.listOutgoingImages).head.sent shouldBe true
  }

  it should "mark correct transaction as failed when failed message is received" in {
    // add image (image will get id 1)
    PostAsUser("/api/images", TestUtil.testImageFormData)

    // first, add a box on the poll (university) side
    val uniBox = addPollBox("hosp5")

    // send image which adds outgoing transaction
    PostAsUser(s"/api/boxes/${uniBox.id}/send", Seq(ImageTagValues(1, Seq.empty))) ~> routes ~> check {
      status should be(NoContent)
    }

    // poll outgoing
    val transactionImage =
      Get(s"/api/transactions/${uniBox.token}/outgoing/poll") ~> routes ~> check {
        status should be(OK)
        responseAs[OutgoingTransactionImage]
      }

    // send failed
    Post(s"/api/transactions/${uniBox.token}/outgoing/failed", FailedOutgoingTransactionImage(transactionImage, "error message")) ~> routes ~> check {
      status should be(NoContent)
    }

    // poll outgoing to check that outgoing contains no valid entries
    Get(s"/api/transactions/${uniBox.token}/outgoing/poll") ~> routes ~> check {
      status should be(NotFound)
    }

    // check contents of outgoing, should contain one failed transaction
    GetAsUser("/api/boxes/outgoing") ~> routes ~> check {
      val outgoingEntries = responseAs[Seq[OutgoingTransaction]]
      outgoingEntries should have length 1
      outgoingEntries.head.status shouldBe TransactionStatus.FAILED
    }

  }

  it should "report a transaction as failed it is marked as failed in the database" in {
    val uniBox = addPollBox("hosp6")

    val transId = 12345

    await(boxDao.insertIncomingTransaction(IncomingTransaction(-1, uniBox.id, uniBox.name, transId, 45, 45, 48, 0, 0, TransactionStatus.FAILED)))

    Get(s"/api/transactions/${uniBox.token}/status?transactionid=$transId") ~> routes ~> check {
      status shouldBe OK
      Await.result(response.entity.toStrict(5.seconds), 5.seconds).data.decodeString("utf-8") shouldBe "FAILED"
    }
  }

  it should "return 404 NotFound when asking for transaction status with an invalid transaction ID" in {
    val uniBox = addPollBox("hosp7")

    val transId = 666

    Get(s"/api/transactions/${uniBox.token}/status?transactionid=$transId") ~> routes ~> check {
      status shouldBe NotFound
    }
  }

  it should "return 204 NoContent and update the status of a transaction" in {
    val uniBox = addPollBox("hosp8")

    val transId = 12345

    await(boxDao.insertIncomingTransaction(IncomingTransaction(-1, uniBox.id, uniBox.name, transId, 45, 45, 48, 0, 0, TransactionStatus.PROCESSING)))

    Put(s"/api/transactions/${uniBox.token}/status?transactionid=$transId", TransactionStatus.FAILED.toString) ~> routes ~> check {
      status shouldBe NoContent
    }

    Get(s"/api/transactions/${uniBox.token}/status?transactionid=$transId") ~> routes ~> check {
      status shouldBe OK
      Await.result(response.entity.toStrict(5.seconds), 5.seconds).data.decodeString("utf-8") shouldBe "FAILED"
    }
  }

  it should "return 404 NotFound when updating transaction status with an invalid transaction ID" in {
    val uniBox = addPollBox("hosp7")

    val transId = 666

    Put(s"/api/transactions/${uniBox.token}/status?transactionid=$transId", TransactionStatus.FAILED.toString) ~> routes ~> check {
      status shouldBe NotFound
    }
  }

  it should "correctly map dicom attributes according to supplied mapping when sending images" in {
    // add image (image will get id 1)
    PostAsUser("/api/images", TestUtil.testImageFormData)

    // first, add a box on the poll (university) side
    val uniBox = addPollBox("hosp6")

    // create attribute mappings
    val patient = GetAsUser(s"/api/metadata/patients") ~> routes ~> check {
      responseAs[List[Patient]].head
    }

    val imageTagValues = ImageTagValues(1, Seq(
      TagValue(PatientName.dicomTag, "TEST NAME"),
      TagValue(PatientID.dicomTag, "TEST ID"),
      TagValue(PatientBirthDate.dicomTag, "19601010")))

    // send image which adds outgoing transaction
    PostAsUser(s"/api/boxes/${uniBox.id}/send", Seq(imageTagValues)) ~> routes ~> check {
      status should be(NoContent)
    }

    // poll outgoing
    val transactionImage =
      Get(s"/api/transactions/${uniBox.token}/outgoing/poll") ~> routes ~> check {
        status should be(OK)
        responseAs[OutgoingTransactionImage]
      }

    // get image
    val transactionId = transactionImage.transaction.id
    val imageId = transactionImage.image.id
    val compressedArray = Get(s"/api/transactions/${uniBox.token}/outgoing?transactionid=$transactionId&imageid=$imageId") ~> routes ~> check {
      status should be(OK)
      responseAs[ByteString]
    }
    val dicomData = TestUtil.loadDicomData(decompress(compressedArray.toArray), withPixelData = false)
    dicomData.attributes.getString(PatientName.dicomTag) should be("TEST NAME") // mapped
    dicomData.attributes.getString(PatientID.dicomTag) should be("TEST ID") // mapped
    dicomData.attributes.getString(PatientBirthDate.dicomTag) should be("19601010") // mapped
    dicomData.attributes.getString(PatientSex.dicomTag) should be(patient.patientSex.value) // not mapped

    // send done
    Post(s"/api/transactions/${uniBox.token}/outgoing/done", transactionImage) ~> routes ~> check {
      status should be(NoContent)
    }
  }

}