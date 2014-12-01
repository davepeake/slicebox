package se.vgregion.dicom

import java.nio.file.Path
import spray.json.DefaultJsonProtocol
import Attributes._

object MetaDataProtocol {

  case class FileName(value: String) extends AnyVal
  
  case class Patient(
      patientName: PatientName,
      patientID: PatientID,
      patientBirthDate: PatientBirthDate,
      patientSex: PatientSex)

  case class Study(
      patient: Patient,
      studyInstanceUID: StudyInstanceUID,
      studyDescription: StudyDescription,
      studyDate: StudyDate,
      studyID: StudyID,
      accessionNumber: AccessionNumber)
      
  case class Equipment(
      manufacturer: Manufacturer,
      stationName: StationName)
      
  case class FrameOfReference(
      frameOfReferenceUID: FrameOfReferenceUID)
  
  case class Series(
      study: Study,
      equipment: Equipment,
      frameOfReference: FrameOfReference,
      seriesInstanceUID: SeriesInstanceUID,
      seriesDescription: SeriesDescription,
      seriesDate: SeriesDate,
      modality: Modality,
      protocolName: ProtocolName,
      bodyPartExamined: BodyPartExamined)
      
  case class Image(
      series: Series,
      sopInstanceUID: SOPInstanceUID,
      imageType: ImageType)
      
  case class ImageFile(
      image: Image,
      fileName: FileName)
            
  // incoming

  case class AddImage(path: Path)

  case class DeleteImage(path: Path)

  case object GetImages

  case object GetPatients
  
  case class GetStudies(patient: Patient)
  
  case class GetSeries(study: Study)

  case class GetImages(series: Series)

  case class GetImageFiles(image: Image)
  
  // outgoing

  case class Patients(patients: Seq[Patient])

  case class Studies(studies: Seq[Study])
  
  case class SeriesCollection(series: Seq[Series])
  
  case class Images(images: Seq[Image])
  
  case class ImageFiles(imageFiles: Seq[ImageFile])
  
  object FileName extends DefaultJsonProtocol { implicit val format = DefaultJsonProtocol.jsonFormat1(FileName.apply) }
  object Equipment extends DefaultJsonProtocol { implicit val format = DefaultJsonProtocol.jsonFormat2(Equipment.apply) }
  object FrameOfReference extends DefaultJsonProtocol { implicit val format = DefaultJsonProtocol.jsonFormat1(FrameOfReference.apply) }  
  object Patient extends DefaultJsonProtocol { implicit val format = DefaultJsonProtocol.jsonFormat4(Patient.apply) }
  object Patients extends DefaultJsonProtocol { implicit val format = DefaultJsonProtocol.jsonFormat1(Patients.apply) }
  object Study extends DefaultJsonProtocol { implicit val format = DefaultJsonProtocol.jsonFormat6(Study.apply) }
  object Studies extends DefaultJsonProtocol { implicit val format = DefaultJsonProtocol.jsonFormat1(Studies.apply) }
  object Series extends DefaultJsonProtocol { implicit val format = DefaultJsonProtocol.jsonFormat9(Series.apply) }
  object SeriesCollection extends DefaultJsonProtocol { implicit val format = DefaultJsonProtocol.jsonFormat1(SeriesCollection.apply) }
  object Image extends DefaultJsonProtocol { implicit val format = DefaultJsonProtocol.jsonFormat3(Image.apply) }
  object Images extends DefaultJsonProtocol { implicit val format = DefaultJsonProtocol.jsonFormat1(Images.apply) }
  object ImageFile extends DefaultJsonProtocol { implicit val format = DefaultJsonProtocol.jsonFormat2(ImageFile.apply) }
  object ImageFiles extends DefaultJsonProtocol { implicit val format = DefaultJsonProtocol.jsonFormat1(ImageFiles.apply) }

}