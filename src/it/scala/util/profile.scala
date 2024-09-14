import scala.concurrent.duration._
import scala.language.{implicitConversions, postfixOps}


/**
  * Created by FM on 28.05.16.
  */
package object util {

  def withWarmup[R](code: => R): R = withWarmup(1)(code)

  def withWarmup[R](repeat: Int)(code: => R): R = {
    withWarmup(Math.max(repeat / 10, 1), repeat)(code)
  }

  def withWarmup[R](warmupRepeat: Int, repeat: Int)(code: => R): R = {
    Console.out.println("Starting warmup")
    (1 until warmupRepeat).foldLeft(code) { (_: R, i: Int) => {
      Console.out.println("Warmup " + i)
      code
    }
    }

    Console.out.println("Finished warmup")
    profileR(repeat)(code)
  }

  def profile[R](code: => R): R = profileR(1)(code)

  def profileR[R](repeat: Int)(code: => R): R = {
    require(repeat > 0, "Profile: at least 1 repetition required")

    val start = Deadline.now

    val result = (0 until repeat).foldLeft(code) { (_: R, i: Int) => {
      Console.out.println("Repeat " + i)
      code
    }
    }

    val end = Deadline.now

    val elapsed = (end - start) / repeat

    def seconds(millis: Long) = (1.0*millis)/1000.0

    if (repeat > 1) {
      println(s"Elapsed time: ${seconds(elapsed.toMillis)} averaged over $repeat repetitions")

      val totalElapsed = (end - start)

      println(s"Total elapsed time: ${seconds(totalElapsed.toMillis)}")
    }
    else println(s"Elapsed time: ${seconds(elapsed.toMillis)}")

    result
  }
}

