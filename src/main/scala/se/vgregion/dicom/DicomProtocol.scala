package se.vgregion.dicom

import java.nio.file.Path
import org.dcm4che3.data.Attributes
import DicomHierarchy._

object DicomProtocol {

  import se.vgregion.model.Entity
  
  // domain objects
  
  case class ScpData(id: Long, name: String, aeTitle: String, port: Int) extends Entity

  case class FileName(value: String)

  case class ImageFile(
    id: Long,
    fileName: FileName) extends Entity {
    
    override def equals(o: Any): Boolean = o match {
      case that: ImageFile => that.fileName == fileName
      case _ => false
    }
  }
  
  case class WatchedDirectory(id: Long, path: String) extends Entity


  // messages

    
  sealed trait DirectoryRequest
  
  case class WatchDirectory(pathString: String) extends DirectoryRequest

  case class UnWatchDirectory(id: Long) extends DirectoryRequest

  case object GetWatchedDirectories extends DirectoryRequest
    
  case class WatchedDirectories(directories: Seq[WatchedDirectory])

  
  
  sealed trait ScpRequest
  
  case class AddScp(name: String, aeTitle: String, port: Int) extends ScpRequest

  case class RemoveScp(id: Long) extends ScpRequest 

  case object GetScps extends ScpRequest 

  case class Scps(scps: Seq[ScpData]) 


  sealed trait MetaDataQuery

  case class GetPatients(startIndex: Long, count: Long, orderBy: Option[String], orderAscending: Boolean, filter: Option[String]) extends MetaDataQuery

  case class GetStudies(startIndex: Long, count: Long, patientId: Long) extends MetaDataQuery

  case class GetSeries(startIndex: Long, count: Long, studyId: Long) extends MetaDataQuery

  case class GetImages(seriesId: Long) extends MetaDataQuery
  
  case class GetImageFilesForPatients(patientIds: Seq[Long]) extends MetaDataQuery
  
  case class GetImageFilesForStudies(studyIds: Seq[Long]) extends MetaDataQuery
  
  case class GetImageFilesForSeries(seriesIds: Seq[Long]) extends MetaDataQuery
  
  
  sealed trait MetaDataUpdate
  
  case class DeleteImage(imageId: Long) extends MetaDataUpdate

  case class DeleteSeries(seriesId: Long) extends MetaDataUpdate

  case class DeleteStudy(studyId: Long) extends MetaDataUpdate

  case class DeletePatient(patientId: Long) extends MetaDataUpdate
  
  
  case object GetAllImageFiles

  case class GetImageFile(imageId: Long)
  
  case class AddDataset(dataset: Attributes)
  
  // ***to API***

  case class Patients(patients: Seq[Patient]) 

  case class Studies(studies: Seq[Study]) 

  case class SeriesCollection(series: Seq[Series]) 

  case class Images(images: Seq[Image]) 

  case class ImagesDeleted(images: Seq[Image])

  case class ImageAdded(image: Image)

  case class DirectoryWatched(path: Path)

  case class DirectoryUnwatched(id: Long)

  case class ScpAdded(scpData: ScpData)

  case class ScpRemoved(scpDataId: Long)


  // ***from scp***

  case class DatasetReceivedByScp(dataset: Attributes)

  // ***from direcory watch***

  case class FileAddedToWatchedDirectory(filePath: Path)

  // ***to storage***

  case class DatasetReceived(dataset: Attributes)
  
  case class FileReceived(path: Path)
  
  // ***from storage***

  case class ImageFiles(imageFiles: Seq[ImageFile])
    
  case class ImageFilesDeleted(imageFiles: Seq[ImageFile])

}