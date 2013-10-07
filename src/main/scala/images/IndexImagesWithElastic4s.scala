package com.bfritz.example.elasticsearchfromscala.images

import com.sksamuel.elastic4s.ElasticClient
import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.FieldType._

import com.typesafe.scalalogging.slf4j.Logging

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
    createIndexWithMappings

    for (imageOrError <- imageList) {
      indexImage(imageOrError)
    }
  }

  def createIndexWithMappings() {
    client.execute {
      create index "images" shards 2 replicas 1 mappings (
        "exif" as (
          field("filename") typed StringType,
          field("path") typed StringType,
          field("focalLength") typed DoubleType
        )
      )
    }
  }

  def indexImage(image: Either[ImageWithError,ImageWithData]) {
    val idx = index.into("images/exif")
    val doc = image match {
      case Left(img) => idx fields (
        "filename" -> img.image.filename,
        "path" -> img.image.path
      )
      case Right(img) => idx fields (
        "filename" -> img.image.filename,
        "path" -> img.image.path,
        "focalLength" -> img.focalLength.getOrElse(Nil)
      )
    }
    val fn = image.fold(_.image.filename, _.image.filename)
    logger.debug(s"Indexing $fn")
    client.execute { doc }
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