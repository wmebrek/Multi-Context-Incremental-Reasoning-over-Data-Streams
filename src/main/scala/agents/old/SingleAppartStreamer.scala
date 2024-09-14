package agents.old

import java.io.{BufferedReader, FileNotFoundException, FileReader, IOException}
import java.util.Observable

class SingleAppartStreamer(var sleepTime: Long, var dataSource: Int) extends Observable with Runnable {
  private val baseUri = null

  override def run(): Unit = {
    var fileName : String= null
    dataSource match {
      case 1 =>
        fileName = "dataset/ann.txt"
      case 2 =>
        fileName = "D:/CSPARQL-ReadyToGoPack/examples_files/ann.txt"
      case _ =>
        throw new IllegalStateException("Unexpected value: " + dataSource)
    }
    //read file into stream, try-with-resources
    //		try (Stream<String> stream = Files.lines(Paths.get(fileName))) {
    try {
      val reader = new BufferedReader(new FileReader(fileName))
      try { //stream.forEach(line -> {
        var strCurrentline : String = null
        while (
          (strCurrentline = reader.readLine) != null
        ) {
          this.setChanged()
          this.notifyObservers(strCurrentline)
          try
            Thread.sleep(sleepTime)
          catch {
            case e: InterruptedException =>
              e.printStackTrace()
          }
        }
      } catch {
        case ex: FileNotFoundException =>
          ex.printStackTrace()
        case ex: IOException =>
          ex.printStackTrace()
      } finally if (reader != null) reader.close()
    }
  }
}
