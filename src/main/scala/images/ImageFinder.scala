package com.bfritz.example.elasticsearchfromscala.images

import java.io.File

object ImageFinder {

  // credit to DNA on SO: http://stackoverflow.com/a/7264833
  def findFiles(f: File): Stream[File] = {
    if (f.isDirectory) {
      // listFiles() can return null; Option avoids NPE
      Option(f.listFiles()).getOrElse(Array[File]()).toStream.flatMap(findFiles)
    } else {
      Stream(f)
    }
  }

  def findImages(f: File): Stream[File] = {
    findFiles(f).filter(f => f.isFile && f.getName.toLowerCase.endsWith(".jpg"))
  }

  implicit def string2File(f: String): File = new File(f)
}
