package com.bfritz.example.elasticsearchfromscala.images

import com.drew.metadata.exif.{ExifIFD0Directory,ExifSubIFDDirectory}
import com.github.nscala_time.time.Imports._
import com.typesafe.scalalogging.slf4j.Logging

import java.io.File
import java.math.BigDecimal

import scala.util.control.NonFatal

case class Image(filename: String, path: String)
case class Camera(make: String, model: String)
case class LonLat(longitude: BigDecimal, latitude: BigDecimal)
case class Taken(timestamp: DateTime, location: Option[LonLat])

case class ImageWithData(
  image: Image,
  camera: Camera,
  taken: Taken,
  focalLength: Option[BigDecimal])

case class ImageWithError(image: Image, throwable: Throwable)

object ProcessImages extends Logging {

  def process(dirs: Array[String]): List[Either[ImageWithError,ImageWithData]] = {
    import ImageFinder._
    // FIXME: args.toStream.flatMap makes type system happy--and seems to work, but feels wrong
    // FIXME: might be nice if we didn't block on findImages()
    val imageFiles: Stream[File] = dirs.toStream.flatMap(ImageFinder.findImages(_))
    imageFiles.map(extractMetadata).toList
  }


  def extractMetadata(image: File): Either[ImageWithError,ImageWithData] = {
    import com.drew.imaging.ImageMetadataReader
    import com.drew.metadata.Metadata

    def extractCamera(md: Metadata): Camera = {
      val dir = md.getDirectory(classOf[ExifIFD0Directory])
      Camera(
        dir.getString(ExifIFD0Directory.TAG_MAKE),
        dir.getString(ExifIFD0Directory.TAG_MODEL))
    }

    def extractTaken(md: Metadata): Taken = {
      val dir = md.getDirectory(classOf[ExifIFD0Directory])
      Taken(
        new DateTime(dir.getDate(ExifIFD0Directory.TAG_DATETIME)),
        None)
    }

    def extractFocalLength(md: Metadata): Option[BigDecimal] = {
      val dir = md.getDirectory(classOf[ExifSubIFDDirectory])
      Option(dir.getString(ExifSubIFDDirectory.TAG_FOCAL_LENGTH)).map(new BigDecimal(_))
    }

    logger.trace(s"Processing ${image.getName}")
    val img = Image(image.getName, image.getParent)
    val dataOrError = try {
      val meta = ImageMetadataReader.readMetadata(image)
      Right(ImageWithData(
        img,
        extractCamera(meta),
        extractTaken(meta),
        extractFocalLength(meta)))
    } catch {
      case NonFatal(e) =>
        Left(ImageWithError(img, e))
    }
    logger.trace(s"Image: $dataOrError")
    dataOrError
  }
}
