package com.bfritz.example.elasticsearchfromscala.images

import com.typesafe.scalalogging.slf4j.Logging

import java.io.File
import java.math.BigDecimal

import scala.collection.JavaConversions._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{blocking, future, Future, Await, TimeoutException}
import scala.concurrent.duration._
import scala.util.control.NonFatal

case class Image(filename: String, path: String)
case class ImageWithData(image: Image, focalLength: Option[BigDecimal])
case class ImageWithError(image: Image, throwable: Throwable)

object ProcessImages extends Logging {
  private val TIMEOUT = 5.seconds
  private val MAX_IMAGES = 1000

  def main(args: Array[String]) {
    val exifDataFutures = process(args)
    var count = 0
    for (
      f <- exifDataFutures.take(MAX_IMAGES);
      imageOrError <- f) {
      count = count + 1
      display(count, imageOrError)
    }

    // Display images until we've hit MAX_IMAGES or TIMEOUT has
    // elapsed.  Without Await.result() , main() will likely exit
    // before the futures complete.
    try {
      Await.result(Future.sequence(exifDataFutures), TIMEOUT)
    } catch {
      // Just log the timeout.  No need to puke a TimeoutException.
      case to: TimeoutException => logger.warn(s"Timed out after $TIMEOUT")
    }
  }

  def process(dirs: Array[String]): List[Future[Either[ImageWithError,ImageWithData]]] = {
    import ImageFinder._
    // FIXME: args.toStream.flatMap makes type system happy--and seems to work, but feels wrong
    // FIXME: might be nice if we didn't block on findImages()
    val imageFiles: Stream[File] = dirs.toStream.flatMap(ImageFinder.findImages(_))
    imageFiles.map(extractMetadata).toList
  }

  def display(count: Int, exif: Either[ImageWithError,ImageWithData]) {
    exif match {
      case Right(imgData) =>
        logger.info(s"$count: $imgData")
      case Left(imgErr) =>
        logger.warn(s"$count: Exif extraction failed $imgErr.", imgErr.throwable)
    }
  }

  import com.drew.metadata.exif.{ExifSubIFDDescriptor,ExifSubIFDDirectory}

  def extractMetadata(image: File): Future[Either[ImageWithError,ImageWithData]] = {
    import com.drew.imaging.ImageMetadataReader

    future {
      blocking {
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
    }
  }

  def extractFocalLength(exifData: ExifSubIFDDescriptor): Option[BigDecimal] = {
    // typical focal length string: 55.0 mm
    val P = """^([0-9\.]+)\s*mm$""".r
    val focalLengthString = exifData.getFocalLengthDescription
    P.findFirstMatchIn(focalLengthString).map(_ group 1).map(new BigDecimal(_))
  }
}
