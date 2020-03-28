package reading

import java.nio.file.{Path, Paths}

import cats.effect.{ContextShift, ExitCode}
import io.circe.generic.auto._
import io.circe.syntax._
import org.http4s.circe._
import org.http4s.dsl.Http4sDsl
import org.http4s.implicits._
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.{HttpApp, HttpRoutes}
import reading.domain._
import reading.fs._
import reading.fsZio.FileServiceZIO
import reading.interpreters.ReadingListServiceCompiler
import zio._
import zio.interop.catz._
import zio.interop.catz.implicits._

trait ZIOModule {
  def dataDir: Path

  lazy val FileService                = new FileServiceZIO
  lazy val BookRepository             = new FileBackedBookRepository[Task](Paths.get(dataDir.toString, "books"), FileService)
  lazy val UserRepository             = new FileBackedUserRepository[Task](Paths.get(dataDir.toString, "users"), FileService)
  lazy val ReadingListServiceCompiler = new ReadingListServiceCompiler[Task](UserRepository, BookRepository)

  lazy val ReadingListHttpService =
    new ReadingListHttpService[Task](UserRepository, BookRepository, ReadingListServiceCompiler)

  /**
    * HttpApp is a type alias from http4s, and defines the top level request handler used to handle
    * incoming HTTP request. Here we simply use out new ReadingListHttpService, and return '404 Not Found'
    * if the incoming request was not handled.
    */
  lazy val HttpApp: HttpApp[Task] = ReadingListHttpService.service.orNotFound
}

/**
  * This is the main entry point of our application. It instantiates the [[Module]] providing runtime values
  * such as the directory where data is stored, then starts a http4s server.
  */
object Server extends CatsApp { self =>

  override def run(args: List[String]) = {
    val dataDirValue = Paths.get(sys.env.getOrElse("DATA_DIR", "./reading-list/src/main/resources/data"))
    val module = new ZIOModule {
      override def dataDir: Path = dataDirValue
    }

    BlazeServerBuilder[Task]
      .bindHttp(8080, "0.0.0.0")
      .withHttpApp(module.HttpApp)
      .serve
      .compile
      .drain
      .as(0).orDie
  }
}
