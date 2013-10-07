package com.bfritz.example.elasticsearchfromscala.images

import com.drew.metadata.exif.{ExifSubIFDDescriptor,ExifSubIFDDirectory}
import com.typesafe.scalalogging.slf4j.Logging

import java.io.File
import java.math.BigDecimal

import scala.util.control.NonFatal

case class Image(filename: String, path: String)
case class ImageWithData(image: Image, focalLength: Option[BigDecimal])
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

    logger.trace(s"Processing ${image.getName}")
    try {
      val meta = ImageMetadataReader.readMetadata(image)
      val exifDir = meta.getDirectory(classOf[ExifSubIFDDirectory])
      val exifData = new ExifSubIFDDescriptor(exifDir)
      val focalLength = extractFocalLength(exifData)
      Right(ImageWithData(Image(image.getName, image.getParent), focalLength))
    } catch {
      case NonFatal(e) =>
        Left(ImageWithError(Image(image.getName, image.getParent), e))
    }
  }

  def extractFocalLength(exifData: ExifSubIFDDescriptor): Option[BigDecimal] = {
    // typical focal length string: 55.0 mm
    val P = """^([0-9\.]+)\s*mm$""".r
    val focalLengthString = exifData.getFocalLengthDescription
    P.findFirstMatchIn(focalLengthString).map(_ group 1).map(new BigDecimal(_))
  }
}
