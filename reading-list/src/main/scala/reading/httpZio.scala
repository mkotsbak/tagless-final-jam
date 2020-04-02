package reading

import java.nio.file.{Path, Paths}

import org.http4s.implicits._
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.HttpApp
import reading.domain._
import reading.fs._
import reading.fsZio.FileServiceZIO
import reading.interpreters.ReadingListServiceCompiler
import zio._
import zio.interop.catz._
import zio.interop.catz.implicits._

trait ZIOModule {
  def dataDir: Path

  type HFileServiceZIO = Has[FileServiceZIO]
  lazy val fileService: ULayer[HFileServiceZIO] = ZLayer.succeed(new FileServiceZIO)

  type HBookRepository = Has[BookRepository[Task]]
  lazy val bookRepository: URLayer[HFileServiceZIO, HBookRepository] = ZLayer.fromService((fs: FileService[Task]) => new FileBackedBookRepository[Task](Paths.get(dataDir.toString, "books"), fs))
  type HUserRepository = Has[UserRepository[Task]]
  lazy val userRepository: URLayer[HFileServiceZIO, HUserRepository] = ZLayer.fromService((fs: FileService[Task]) => new FileBackedUserRepository[Task](Paths.get(dataDir.toString, "users"), fs))

  type HReadingListService = Has[ReadingListService[Task]] // TODO: use
  lazy val readingListService = ZLayer.fromServices((ur: UserRepository[Task], br: BookRepository[Task]) => new ReadingListServiceCompiler[Task]( ur, br))

  type HReadingListHttpService = Has[ReadingListHttpService[Task]]
  lazy val readingListHttpService = ZLayer.fromServices((ur: UserRepository[Task], br: BookRepository[Task], rls: ReadingListServiceCompiler[Task]) =>
    new ReadingListHttpService[Task](ur, br, rls))

  /**
    * HttpApp is a type alias from http4s, and defines the top level request handler used to handle
    * incoming HTTP request. Here we simply use out new ReadingListHttpService, and return '404 Not Found'
    * if the incoming request was not handled.
    */
  lazy val httpApp: ZIO[HReadingListHttpService, Nothing, HttpApp[Task]] = ZIO.access[HReadingListHttpService](_.get.service.orNotFound)

  lazy val prodEnvModule = fileService >>>
    (bookRepository ++ userRepository) >>>
    readingListService.passthrough >>>
    readingListHttpService
}

/**
  * This is the main entry point of our application. It instantiates the [[Module]] providing runtime values
  * such as the directory where data is stored, then starts a http4s server.
  */
object ZioServer extends CatsApp { self =>

  override def run(args: List[String]) = {
    val dataDirValue = Paths.get(sys.env.getOrElse("DATA_DIR", "./reading-list/src/main/resources/data"))
    val module = new ZIOModule {
      override def dataDir: Path = dataDirValue
    }

    module.httpApp.flatMap(app =>
      BlazeServerBuilder[Task]
        .bindHttp(8080, "0.0.0.0")
        .withHttpApp(app)
        .serve
        .compile
        .drain
        .as(0)
        .orDie
    ).provideLayer(module.prodEnvModule)
  }
}
