package se.nimsa.sbx.storage

import scala.slick.driver.H2Driver
import scala.slick.jdbc.JdbcBackend.{ Database, Session }
import org.scalatest.FlatSpec
import org.scalatest.Matchers
import org.scalatest.BeforeAndAfterEach
import se.nimsa.sbx.dicom.DicomPropertyValue._
import se.nimsa.sbx.dicom.DicomHierarchy._
import org.h2.jdbc.JdbcSQLException
import StorageProtocol._
import se.nimsa.sbx.util.TestUtil._
import se.nimsa.sbx.seriestype.SeriesTypeDAO
import se.nimsa.sbx.seriestype.SeriesTypeProtocol.SeriesType
import se.nimsa.sbx.storage.StorageProtocol.SeriesSeriesType
import se.nimsa.sbx.app.GeneralProtocol._

class PropertiesDAOTest extends FlatSpec with Matchers with BeforeAndAfterEach {

  private val db = Database.forURL("jdbc:h2:mem:dicompropertiesdaotest;DB_CLOSE_DELAY=-1", driver = "org.h2.Driver")

  val metaDataDao = new MetaDataDAO(H2Driver)
  val propertiesDao = new PropertiesDAO(H2Driver)
  val seriesTypeDao = new SeriesTypeDAO(H2Driver)

  override def beforeEach() =
    db.withSession { implicit session =>
      seriesTypeDao.create
      metaDataDao.create
      propertiesDao.create
    }

  override def afterEach() =
    db.withSession { implicit session =>
      propertiesDao.drop
      metaDataDao.drop
      seriesTypeDao.drop
    }

  "The properties db" should "be emtpy before anything has been added" in {
    db.withSession { implicit session =>
      propertiesDao.listSeriesSources should be(empty)
      propertiesDao.listSeriesTags should be(empty)
      propertiesDao.listSeriesSeriesTypes should be(empty)
    }
  }

  it should "cascade delete linked series sources when a patient is deleted" in {
    db.withSession { implicit session =>
      insertMetaDataAndProperties
      propertiesDao.seriesSources.size should be(4)
      metaDataDao.patientById(1).foreach(dbPat => {
        metaDataDao.deletePatient(dbPat.id)
        propertiesDao.seriesSources.size should be(0)
      })
    }
  }

  it should "not support adding a series source which links to a non-existing series" in {
    db.withSession { implicit session =>
      intercept[JdbcSQLException] {
        propertiesDao.insertSeriesSource(SeriesSource(666, Source(SourceType.USER, "user", 1)))
      }
    }
  }

  it should "support filtering flat series by source" in {
    db.withSession { implicit session =>
      insertMetaDataAndProperties

      propertiesDao.flatSeries(0, 20, None, true, None, Seq.empty, Seq.empty, Seq.empty).size should be(4)
      propertiesDao.flatSeries(0, 20, None, true, None, Seq(SourceRef(SourceType.BOX, 1)), Seq.empty, Seq.empty).size should be(1)
      propertiesDao.flatSeries(0, 20, None, true, None, Seq(SourceRef(SourceType.BOX, 2)), Seq.empty, Seq.empty).size should be(0)
    }
  }

  it should "support filtering patients by source" in {
    db.withSession { implicit session =>
      insertMetaDataAndProperties
      propertiesDao.patients(0, 20, None, true, None, Seq.empty, Seq.empty, Seq.empty).size should be(1)
      propertiesDao.patients(0, 20, None, true, None, Seq(SourceRef(SourceType.BOX, 1)), Seq.empty, Seq.empty).size should be(1)
      propertiesDao.patients(0, 20, None, true, None, Seq(SourceRef(SourceType.BOX, 2)), Seq.empty, Seq.empty).size should be(0)
      propertiesDao.patients(0, 20, None, true, None, Seq(SourceRef(SourceType.UNKNOWN, 1)), Seq.empty, Seq.empty).size should be(0)
    }
  }

  it should "support filtering studies by source" in {
    db.withSession { implicit session =>
      insertMetaDataAndProperties
      propertiesDao.studiesForPatient(0, 20, 1, Seq.empty, Seq.empty, Seq.empty).size should be(2)
      propertiesDao.studiesForPatient(0, 20, 1, Seq(SourceRef(SourceType.BOX, 1)), Seq.empty, Seq.empty).size should be(1)
      propertiesDao.studiesForPatient(0, 20, 1, Seq(SourceRef(SourceType.BOX, 2)), Seq.empty, Seq.empty).size should be(0)
      propertiesDao.studiesForPatient(0, 20, 1, Seq(SourceRef(SourceType.UNKNOWN, 1)), Seq.empty, Seq.empty).size should be(0)
    }
  }

  it should "support filtering series by source" in {
    db.withSession { implicit session =>
      insertMetaDataAndProperties
      propertiesDao.seriesForStudy(0, 20, 1, Seq.empty, Seq.empty, Seq.empty).size should be(2)
      propertiesDao.seriesForStudy(0, 20, 1, Seq(SourceRef(SourceType.BOX, 1)), Seq.empty, Seq.empty).size should be(1)
      propertiesDao.seriesForStudy(0, 20, 2, Seq(SourceRef(SourceType.SCP, 1)), Seq.empty, Seq.empty).size should be(1)
      propertiesDao.seriesForStudy(0, 20, 2, Seq(SourceRef(SourceType.DIRECTORY, 1)), Seq.empty, Seq.empty).size should be(1)
      propertiesDao.seriesForStudy(0, 20, 1, Seq(SourceRef(SourceType.BOX, 2)), Seq.empty, Seq.empty).size should be(0)
      propertiesDao.seriesForStudy(0, 20, 1, Seq(SourceRef(SourceType.SCP, 2)), Seq.empty, Seq.empty).size should be(0)
    }
  }

  it should "support filtering flat series by series tag" in {
    db.withSession { implicit session =>
      insertMetaDataAndProperties
      propertiesDao.flatSeries(0, 20, None, true, None, Seq.empty, Seq.empty, Seq(1, 2)).size should be(3)
      propertiesDao.flatSeries(0, 20, None, true, None, Seq.empty, Seq.empty, Seq(1)).size should be(2)
      propertiesDao.flatSeries(0, 20, None, true, None, Seq.empty, Seq.empty, Seq(1, 3)).size should be(2)
      propertiesDao.flatSeries(0, 20, None, true, None, Seq.empty, Seq.empty, Seq(3)).size should be(0)
    }
  }

  it should "support filtering patients by series tag" in {
    db.withSession { implicit session =>
      insertMetaDataAndProperties
      propertiesDao.patients(0, 20, None, true, None, Seq.empty, Seq.empty, Seq(1, 2)).size should be(1)
      propertiesDao.patients(0, 20, None, true, None, Seq.empty, Seq.empty, Seq(1)).size should be(1)
      propertiesDao.patients(0, 20, None, true, None, Seq.empty, Seq.empty, Seq(1, 3)).size should be(1)
      propertiesDao.patients(0, 20, None, true, None, Seq.empty, Seq.empty, Seq(3)).size should be(0)
    }
  }

  it should "support filtering studies by series tag" in {
    db.withSession { implicit session =>
      insertMetaDataAndProperties
      propertiesDao.studiesForPatient(0, 20, 1, Seq.empty, Seq.empty, Seq(1, 2)).size should be(2)
      propertiesDao.studiesForPatient(0, 20, 1, Seq.empty, Seq.empty, Seq(1)).size should be(1)
      propertiesDao.studiesForPatient(0, 20, 1, Seq.empty, Seq.empty, Seq(1, 3)).size should be(1)
      propertiesDao.studiesForPatient(0, 20, 1, Seq.empty, Seq.empty, Seq(3)).size should be(0)
    }
  }

  it should "support filtering series by series tag" in {
    db.withSession { implicit session =>
      insertMetaDataAndProperties
      propertiesDao.seriesForStudy(0, 20, 1, Seq.empty, Seq.empty, Seq(1, 2)).size should be(2)
      propertiesDao.seriesForStudy(0, 20, 1, Seq.empty, Seq.empty, Seq(1)).size should be(2)
      propertiesDao.seriesForStudy(0, 20, 1, Seq.empty, Seq.empty, Seq(1, 3)).size should be(2)
      propertiesDao.seriesForStudy(0, 20, 1, Seq.empty, Seq.empty, Seq(3)).size should be(0)
      propertiesDao.seriesForStudy(0, 20, 2, Seq.empty, Seq.empty, Seq(1, 2)).size should be(1)
      propertiesDao.seriesForStudy(0, 20, 2, Seq.empty, Seq.empty, Seq(1)).size should be(0)
      propertiesDao.seriesForStudy(0, 20, 2, Seq.empty, Seq.empty, Seq(3)).size should be(0)
    }
  }

  it should "support filtering patients by series type" in {
    db.withSession { implicit session =>
      insertMetaDataAndProperties
      propertiesDao.patients(0, 20, None, true, None, Seq.empty, Seq(1), Seq.empty).size should be(1)
      propertiesDao.patients(0, 20, None, true, None, Seq.empty, Seq(1, 2), Seq.empty).size should be(1)
      propertiesDao.patients(0, 20, None, true, None, Seq.empty, Seq(1, 2, 3), Seq.empty).size should be(1)
      propertiesDao.patients(0, 20, None, true, None, Seq.empty, Seq(3), Seq.empty).size should be(0)
    }
  }

  it should "support filtering studies by series type" in {
    db.withSession { implicit session =>
      insertMetaDataAndProperties
      propertiesDao.studiesForPatient(0, 20, 1, Seq.empty, Seq(1), Seq.empty).size should be(1)
      propertiesDao.studiesForPatient(0, 20, 1, Seq.empty, Seq(1, 2), Seq.empty).size should be(2)
      propertiesDao.studiesForPatient(0, 20, 1, Seq.empty, Seq(1, 2, 3), Seq.empty).size should be(2)
      propertiesDao.studiesForPatient(0, 20, 1, Seq.empty, Seq(3), Seq.empty).size should be(0)
    }
  }

  it should "support filtering series by series type" in {
    db.withSession { implicit session =>
      insertMetaDataAndProperties
      propertiesDao.seriesForStudy(0, 20, 1, Seq.empty, Seq(1), Seq.empty).size should be(2)
      propertiesDao.seriesForStudy(0, 20, 1, Seq.empty, Seq(1, 2), Seq.empty).size should be(2)
      propertiesDao.seriesForStudy(0, 20, 1, Seq.empty, Seq(1, 2, 3), Seq.empty).size should be(2)
      propertiesDao.seriesForStudy(0, 20, 1, Seq.empty, Seq(3), Seq.empty).size should be(0)
      propertiesDao.seriesForStudy(0, 20, 2, Seq.empty, Seq(1), Seq.empty).size should be(0)
      propertiesDao.seriesForStudy(0, 20, 2, Seq.empty, Seq(1, 2), Seq.empty).size should be(1)
      propertiesDao.seriesForStudy(0, 20, 2, Seq.empty, Seq(1, 2, 3), Seq.empty).size should be(1)
      propertiesDao.seriesForStudy(0, 20, 2, Seq.empty, Seq(3), Seq.empty).size should be(0)
    }
  }

  it should "support filtering flat series by series type" in {
    db.withSession { implicit session =>
      insertMetaDataAndProperties
      propertiesDao.flatSeries(0, 20, None, true, None, Seq.empty, Seq(1), Seq.empty).size should be(2)
      propertiesDao.flatSeries(0, 20, None, true, None, Seq.empty, Seq(1, 2), Seq.empty).size should be(3)
      propertiesDao.flatSeries(0, 20, None, true, None, Seq.empty, Seq(1, 2, 3), Seq.empty).size should be(3)
      propertiesDao.flatSeries(0, 20, None, true, None, Seq.empty, Seq(3), Seq.empty).size should be(0)
    }
  }

  it should "create valid SQL queries (no SQL exceptions) with all combinations of input arguments when listing patients" in {
    db.withSession { implicit session =>
      insertMetaDataAndProperties
      propertiesDao.patients(0, 20, None, true, None, Seq.empty, Seq.empty, Seq.empty)

      propertiesDao.patients(0, 20, Some("PatientID"), true, None, Seq.empty, Seq.empty, Seq.empty)

      propertiesDao.patients(0, 20, None, true, Some("filter"), Seq.empty, Seq.empty, Seq.empty)
      propertiesDao.patients(0, 20, Some("PatientID"), true, Some("filter"), Seq.empty, Seq.empty, Seq.empty)

      propertiesDao.patients(0, 20, None, true, None, Seq(SourceRef(SourceType.BOX, 1)), Seq.empty, Seq.empty)
      propertiesDao.patients(0, 20, Some("PatientID"), true, None, Seq(SourceRef(SourceType.BOX, 1)), Seq.empty, Seq.empty)
      propertiesDao.patients(0, 20, None, true, Some("filter"), Seq(SourceRef(SourceType.BOX, 1)), Seq.empty, Seq.empty)
      propertiesDao.patients(0, 20, Some("PatientID"), true, Some("filter"), Seq(SourceRef(SourceType.BOX, 1)), Seq.empty, Seq.empty)

      propertiesDao.patients(0, 20, None, true, None, Seq.empty, Seq(1), Seq.empty)
      propertiesDao.patients(0, 20, Some("PatientID"), true, None, Seq.empty, Seq(1), Seq.empty)
      propertiesDao.patients(0, 20, None, true, Some("filter"), Seq.empty, Seq(1), Seq.empty)
      propertiesDao.patients(0, 20, Some("PatientID"), true, Some("filter"), Seq.empty, Seq(1), Seq.empty)
      propertiesDao.patients(0, 20, None, true, None, Seq(SourceRef(SourceType.BOX, 1)), Seq(1), Seq.empty)
      propertiesDao.patients(0, 20, Some("PatientID"), true, None, Seq(SourceRef(SourceType.BOX, 1)), Seq(1), Seq.empty)
      propertiesDao.patients(0, 20, None, true, Some("filter"), Seq(SourceRef(SourceType.BOX, 1)), Seq(1), Seq.empty)
      propertiesDao.patients(0, 20, Some("PatientID"), true, Some("filter"), Seq(SourceRef(SourceType.BOX, 1)), Seq(1), Seq.empty)

      propertiesDao.patients(0, 20, None, true, None, Seq.empty, Seq.empty, Seq(1))
      propertiesDao.patients(0, 20, Some("PatientID"), true, None, Seq.empty, Seq.empty, Seq(1))
      propertiesDao.patients(0, 20, None, true, Some("filter"), Seq.empty, Seq.empty, Seq(1))
      propertiesDao.patients(0, 20, Some("PatientID"), true, Some("filter"), Seq.empty, Seq.empty, Seq(1))
      propertiesDao.patients(0, 20, None, true, None, Seq(SourceRef(SourceType.BOX, 1)), Seq.empty, Seq(1))
      propertiesDao.patients(0, 20, Some("PatientID"), true, None, Seq(SourceRef(SourceType.BOX, 1)), Seq.empty, Seq(1))
      propertiesDao.patients(0, 20, None, true, Some("filter"), Seq(SourceRef(SourceType.BOX, 1)), Seq.empty, Seq(1))
      propertiesDao.patients(0, 20, Some("PatientID"), true, Some("filter"), Seq(SourceRef(SourceType.BOX, 1)), Seq.empty, Seq(1))
      propertiesDao.patients(0, 20, None, true, None, Seq.empty, Seq(1), Seq(1))
      propertiesDao.patients(0, 20, Some("PatientID"), true, None, Seq.empty, Seq(1), Seq(1))
      propertiesDao.patients(0, 20, None, true, Some("filter"), Seq.empty, Seq(1), Seq(1))
      propertiesDao.patients(0, 20, Some("PatientID"), true, Some("filter"), Seq.empty, Seq(1), Seq(1))
      propertiesDao.patients(0, 20, None, true, None, Seq(SourceRef(SourceType.BOX, 1)), Seq(1), Seq(1))
      propertiesDao.patients(0, 20, Some("PatientID"), true, None, Seq(SourceRef(SourceType.BOX, 1)), Seq(1), Seq(1))
      propertiesDao.patients(0, 20, None, true, Some("filter"), Seq(SourceRef(SourceType.BOX, 1)), Seq(1), Seq(1))
      propertiesDao.patients(0, 20, Some("PatientID"), true, Some("filter"), Seq(SourceRef(SourceType.BOX, 1)), Seq(1), Seq(1))
    }
  }

  it should "create valid SQL queries (no SQL exceptions) with all combinations of input arguments when listing studies" in {
    db.withSession { implicit session =>
      insertMetaDataAndProperties
      propertiesDao.studiesForPatient(0, 20, 1, Seq.empty, Seq.empty, Seq.empty)
      propertiesDao.studiesForPatient(0, 20, 1, Seq.empty, Seq.empty, Seq(1))
      propertiesDao.studiesForPatient(0, 20, 1, Seq.empty, Seq(1), Seq.empty)
      propertiesDao.studiesForPatient(0, 20, 1, Seq.empty, Seq(1), Seq(1))
      propertiesDao.studiesForPatient(0, 20, 1, Seq(SourceRef(SourceType.BOX, 1)), Seq.empty, Seq.empty)
      propertiesDao.studiesForPatient(0, 20, 1, Seq(SourceRef(SourceType.BOX, 1)), Seq.empty, Seq(1))
      propertiesDao.studiesForPatient(0, 20, 1, Seq(SourceRef(SourceType.BOX, 1)), Seq(1), Seq.empty)
      propertiesDao.studiesForPatient(0, 20, 1, Seq(SourceRef(SourceType.BOX, 1)), Seq(1), Seq(1))
    }
  }

  it should "create valid SQL queries (no SQL exceptions) with all combinations of input arguments when listing series" in {
    db.withSession { implicit session =>
      insertMetaDataAndProperties
      propertiesDao.seriesForStudy(0, 20, 1, Seq.empty, Seq.empty, Seq.empty)
      propertiesDao.seriesForStudy(0, 20, 1, Seq.empty, Seq.empty, Seq(1))
      propertiesDao.seriesForStudy(0, 20, 1, Seq.empty, Seq(1), Seq.empty)
      propertiesDao.seriesForStudy(0, 20, 1, Seq.empty, Seq(1), Seq(1))
      propertiesDao.seriesForStudy(0, 20, 1, Seq(SourceRef(SourceType.BOX, 1)), Seq.empty, Seq.empty)
      propertiesDao.seriesForStudy(0, 20, 1, Seq(SourceRef(SourceType.BOX, 1)), Seq.empty, Seq(1))
      propertiesDao.seriesForStudy(0, 20, 1, Seq(SourceRef(SourceType.BOX, 1)), Seq(1), Seq.empty)
      propertiesDao.seriesForStudy(0, 20, 1, Seq(SourceRef(SourceType.BOX, 1)), Seq(1), Seq(1))
    }
  }

  it should "create valid SQL queries (no SQL exceptions) with all combinations of input arguments when listing flat series" in {
    db.withSession { implicit session =>
      insertMetaDataAndProperties
      propertiesDao.flatSeries(0, 20, None, true, None, Seq.empty, Seq.empty, Seq.empty)

      propertiesDao.flatSeries(0, 20, Some("PatientID"), true, None, Seq.empty, Seq.empty, Seq.empty)

      propertiesDao.flatSeries(0, 20, None, true, Some("filter"), Seq.empty, Seq.empty, Seq.empty)
      propertiesDao.flatSeries(0, 20, Some("PatientID"), true, Some("filter"), Seq.empty, Seq.empty, Seq.empty)

      propertiesDao.flatSeries(0, 20, None, true, None, Seq(SourceRef(SourceType.BOX, 1)), Seq.empty, Seq.empty)
      propertiesDao.flatSeries(0, 20, Some("PatientID"), true, None, Seq(SourceRef(SourceType.BOX, 1)), Seq.empty, Seq.empty)
      propertiesDao.flatSeries(0, 20, None, true, Some("filter"), Seq(SourceRef(SourceType.BOX, 1)), Seq.empty, Seq.empty)
      propertiesDao.flatSeries(0, 20, Some("PatientID"), true, Some("filter"), Seq(SourceRef(SourceType.BOX, 1)), Seq.empty, Seq.empty)

      propertiesDao.flatSeries(0, 20, None, true, None, Seq.empty, Seq(1), Seq.empty)
      propertiesDao.flatSeries(0, 20, Some("PatientID"), true, None, Seq.empty, Seq(1), Seq.empty)
      propertiesDao.flatSeries(0, 20, None, true, Some("filter"), Seq.empty, Seq(1), Seq.empty)
      propertiesDao.flatSeries(0, 20, Some("PatientID"), true, Some("filter"), Seq.empty, Seq(1), Seq.empty)
      propertiesDao.flatSeries(0, 20, None, true, None, Seq(SourceRef(SourceType.BOX, 1)), Seq(1), Seq.empty)
      propertiesDao.flatSeries(0, 20, Some("PatientID"), true, None, Seq(SourceRef(SourceType.BOX, 1)), Seq(1), Seq.empty)
      propertiesDao.flatSeries(0, 20, None, true, Some("filter"), Seq(SourceRef(SourceType.BOX, 1)), Seq(1), Seq.empty)
      propertiesDao.flatSeries(0, 20, Some("PatientID"), true, Some("filter"), Seq(SourceRef(SourceType.BOX, 1)), Seq(1), Seq.empty)

      propertiesDao.flatSeries(0, 20, None, true, None, Seq.empty, Seq.empty, Seq(1))
      propertiesDao.flatSeries(0, 20, Some("PatientID"), true, None, Seq.empty, Seq.empty, Seq(1))
      propertiesDao.flatSeries(0, 20, None, true, Some("filter"), Seq.empty, Seq.empty, Seq(1))
      propertiesDao.flatSeries(0, 20, Some("PatientID"), true, Some("filter"), Seq.empty, Seq.empty, Seq(1))
      propertiesDao.flatSeries(0, 20, None, true, None, Seq(SourceRef(SourceType.BOX, 1)), Seq.empty, Seq(1))
      propertiesDao.flatSeries(0, 20, Some("PatientID"), true, None, Seq(SourceRef(SourceType.BOX, 1)), Seq.empty, Seq(1))
      propertiesDao.flatSeries(0, 20, None, true, Some("filter"), Seq(SourceRef(SourceType.BOX, 1)), Seq.empty, Seq(1))
      propertiesDao.flatSeries(0, 20, Some("PatientID"), true, Some("filter"), Seq(SourceRef(SourceType.BOX, 1)), Seq.empty, Seq(1))
      propertiesDao.flatSeries(0, 20, None, true, None, Seq.empty, Seq(1), Seq(1))
      propertiesDao.flatSeries(0, 20, Some("PatientID"), true, None, Seq.empty, Seq(1), Seq(1))
      propertiesDao.flatSeries(0, 20, None, true, Some("filter"), Seq.empty, Seq(1), Seq(1))
      propertiesDao.flatSeries(0, 20, Some("PatientID"), true, Some("filter"), Seq.empty, Seq(1), Seq(1))
      propertiesDao.flatSeries(0, 20, None, true, None, Seq(SourceRef(SourceType.BOX, 1)), Seq(1), Seq(1))
      propertiesDao.flatSeries(0, 20, Some("PatientID"), true, None, Seq(SourceRef(SourceType.BOX, 1)), Seq(1), Seq(1))
      propertiesDao.flatSeries(0, 20, None, true, Some("filter"), Seq(SourceRef(SourceType.BOX, 1)), Seq(1), Seq(1))
      propertiesDao.flatSeries(0, 20, Some("PatientID"), true, Some("filter"), Seq(SourceRef(SourceType.BOX, 1)), Seq(1), Seq(1))
    }
  }

  it should "remove a series tag when the last occurrence of it has been removed" in {
    db.withSession { implicit session =>
      val (dbPatient1, (dbStudy1, dbStudy2), (dbSeries1, dbSeries2, dbSeries3, dbSeries4), (dbImage1, dbImage2, dbImage3, dbImage4, dbImage5, dbImage6, dbImage7, dbImage8)) =
        insertMetaDataAndProperties
      val seriesTags = propertiesDao.listSeriesTags
      seriesTags.size should be(2)
      seriesTags.map(_.name) should be(List("Tag1", "Tag2"))
      propertiesDao.removeAndCleanupSeriesTagForSeriesId(seriesTags(0).id, dbSeries1.id)
      propertiesDao.listSeriesTags.size should be(2)
      propertiesDao.removeAndCleanupSeriesTagForSeriesId(seriesTags(1).id, dbSeries1.id)
      propertiesDao.listSeriesTags.size should be(2)
      propertiesDao.removeAndCleanupSeriesTagForSeriesId(seriesTags(0).id, dbSeries2.id)
      propertiesDao.listSeriesTags.size should be(1)
      propertiesDao.removeAndCleanupSeriesTagForSeriesId(seriesTags(1).id, dbSeries3.id)
      propertiesDao.listSeriesTags.size should be(0)
    }
  }

  it should "remove a series tag when deleting a series if the series tag attached to the series was the last of its kind" in {
    db.withSession { implicit session =>
      val (dbPatient1, (dbStudy1, dbStudy2), (dbSeries1, dbSeries2, dbSeries3, dbSeries4), (dbImage1, dbImage2, dbImage3, dbImage4, dbImage5, dbImage6, dbImage7, dbImage8)) =
        insertMetaDataAndProperties
      propertiesDao.listSeriesTags.size should be(2)
      propertiesDao.deleteFully(dbSeries4)
      propertiesDao.listSeriesTags.size should be(2)
      propertiesDao.deleteFully(dbSeries1)
      propertiesDao.listSeriesTags.size should be(2)
      propertiesDao.deleteFully(dbSeries2)
      propertiesDao.listSeriesTags.size should be(1)
      propertiesDao.deleteFully(dbSeries3)
      propertiesDao.listSeriesTags.size should be(0)
    }
  }

  def insertMetaDataAndProperties(implicit session: Session) = {
    val (dbPatient1, (dbStudy1, dbStudy2), (dbSeries1, dbSeries2, dbSeries3, dbSeries4), (dbImage1, dbImage2, dbImage3, dbImage4, dbImage5, dbImage6, dbImage7, dbImage8)) =
      insertMetaData(metaDataDao)
    insertProperties(seriesTypeDao, propertiesDao, dbSeries1, dbSeries2, dbSeries3, dbSeries4, dbImage1, dbImage2, dbImage3, dbImage4, dbImage5, dbImage6, dbImage7, dbImage8)
    (dbPatient1, (dbStudy1, dbStudy2), (dbSeries1, dbSeries2, dbSeries3, dbSeries4), (dbImage1, dbImage2, dbImage3, dbImage4, dbImage5, dbImage6, dbImage7, dbImage8))
  }

}
