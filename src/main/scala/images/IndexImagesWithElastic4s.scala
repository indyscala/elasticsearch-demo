package com.bfritz.example.elasticsearchfromscala.images

import com.sksamuel.elastic4s.ElasticClient
import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.FieldType._

import com.typesafe.scalalogging.slf4j.Logging

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Future, Await}
import scala.concurrent.duration._

import org.elasticsearch.action.admin.indices.create.CreateIndexResponse
import org.elasticsearch.action.index.IndexResponse
import org.elasticsearch.common.geo.GeoPoint
import org.elasticsearch.common.settings.{ImmutableSettings,Settings}

object IndexImagesWithElastic4s extends Logging {
  private val ClusterName = Option(System.getenv("ES_CLUSTER")).getOrElse("3node")

  // implicit val client = ElasticClient.local(localEsSettings())
  implicit val client = ElasticClient.remote(
    remoteEsSettings(ClusterName),
    "localhost" -> 9300,
    "localhost" -> 9301,
    "localhost" -> 9302)

  def main(args: Array[String]) {
    indexImages(ProcessImages.process(args))
  }

  def indexImages(imageList: List[Either[ImageWithError,ImageWithData]]) {
    Await.result(createIndexWithMappings, 3 seconds)

    val futures = for (imageOrError <- imageList) yield indexImage(imageOrError)
    Await.result(Future.sequence(futures), 10 seconds)
  }

  def createIndexWithMappings(): Future[CreateIndexResponse] = {
    client.deleteIndex("images")
    client.execute {
      create index "images" shards 2 replicas 1 mappings (
        "exif" as (
          field("taken") typed DateType,
          field("filename") typed StringType,
          field("path") typed StringType,
          field("location") typed GeoPointType,
          field("focalLength") typed DoubleType
        )
      )
    }
  }

  def indexImage(image: Either[ImageWithError,ImageWithData]): Future[IndexResponse] = {
    val idx = index.into("images/exif")
    val doc = image match {
      case Left(img) => idx fields (
        "filename" -> img.image.filename,
        "path" -> img.image.path
      )
      case Right(img) => idx fields (
        "filename" -> img.image.filename,
        "path" -> img.image.path,
        "taken" -> img.taken.timestamp,
        "cameraMake" -> img.camera.make,
        "cameraModel" -> img.camera.model,
        "location" -> img.taken.location.map(toGeoPoint).getOrElse(null),
        "locForKibana" -> img.taken.location.map(toLatLonString).getOrElse(null),
        "focalLength" -> img.focalLength.getOrElse(null)
      )
    }
    val fn = image.fold(filenameWithErrorMsg(_), _.image.filename)
    logger.debug(s"Indexing $fn")
    client.execute { doc }
  }

  def toGeoPoint(cc: LonLat): GeoPoint = {
    new GeoPoint(cc.latitude, cc.longitude)
  }

  def toLatLonString(cc: LonLat): String = {
    "%s, %s".format(cc.latitude, cc.longitude)
  }

  def filenameWithErrorMsg(imgErr: ImageWithError): String = {
    s"${imgErr.image.filename} (error ${imgErr.throwable.getMessage})"
  }


  /** Enable HTTP access to the local ES instance.
   *
   * By default ES indices are stored in <code>data</code>
   * subdirectory of working directory.
   */
  def localEsSettings(): Settings = {
    ImmutableSettings.settingsBuilder()
      .put("http.enabled", true)
      // .put("path.home", "/tmp/indyscala/es")
      .build
  }

  def remoteEsSettings(clusterName: String): Settings = {
    ImmutableSettings.settingsBuilder()
      .put("cluster.name", clusterName)
      .build
  }
}
