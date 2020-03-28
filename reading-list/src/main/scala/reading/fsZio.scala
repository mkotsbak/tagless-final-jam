package reading

import java.nio.file.{Files, Path, Paths}
import java.util.UUID

import io.circe.generic.auto._
import io.circe.{Decoder, Encoder}
import reading.domain._
import reading.fs.FileService
import zio.{Task, ZIO}

import scala.io.Source

object fsZio {

  /**
    * Implements the [[FileService]] abstraction in a pure-fp way, using [[ZIO]] to suspend side effects.
    */
  class FileServiceZIO extends FileService[Task] {

    override def exists(path: Path): Task[Boolean] = ZIO {
      path.toFile.exists()
    }

    override def listDir(path: Path): Task[List[Path]] = Task {
      path.toFile.listFiles().toList.map(_.toPath)
    }

    override def getFileData(path: Path): Task[String] = {
      Task(Source.fromFile(path.toFile))
        .bracketAuto((source: Source) => Task(source.getLines().mkString))
    }

    override def writeFileData(path: Path, data: String): Task[Unit] = Task {
      if (!path.getParent.toFile.exists()) {
        path.getParent.toFile.mkdirs()
      }
      Files.write(path, data.getBytes)
    }
  }
}
