package kmeans

import scala.annotation.tailrec
import scala.collection._
import scala.util.Random
import org.scalameter._
import common._

class KMeans {

  def generatePoints(k: Int, num: Int): Seq[Point] = {
    val randx = new Random(1)
    val randy = new Random(3)
    val randz = new Random(5)
    (0 until num)
      .map({ i =>
        val x = ((i + 1) % k) * 1.0 / k + randx.nextDouble() * 0.5
        val y = ((i + 5) % k) * 1.0 / k + randy.nextDouble() * 0.5
        val z = ((i + 7) % k) * 1.0 / k + randz.nextDouble() * 0.5
        new Point(x, y, z)
      }).to[mutable.ArrayBuffer]
  }

  def initializeMeans(k: Int, points: Seq[Point]): Seq[Point] = {
    val rand = new Random(7)
    (0 until k).map(_ => points(rand.nextInt(points.length))).to[mutable.ArrayBuffer]
  }

  def findClosest(p: Point, means: GenSeq[Point]): Point = {
    assert(means.size > 0)
    var minDistance = p.squareDistance(means(0))
    var closest = means(0)
    var i = 1
    while (i < means.length) {
      val distance = p.squareDistance(means(i))
      if (distance < minDistance) {
        minDistance = distance
        closest = means(i)
      }
      i += 1
    }
    closest
  }

  // This method takes a generic sequence of points and a generic sequence of means. 
  // It returns a generic map collection, which maps each mean to the sequence of 
  // points in the corresponding cluster
  // Hint: Use groupBy and the findClosest method, which is already defined for you. 
  // After that, make sure that all the means are in the GenMap, even if their sequences are empty.
  def classify(points: GenSeq[Point], means: GenSeq[Point]): GenMap[Point, GenSeq[Point]] = {
    val grouped = points.groupBy(p => findClosest(p, means))
    val unmatchedMeans = means.filterNot(grouped.keySet.contains)
    grouped ++ unmatchedMeans.map(p => (p, GenSeq[Point]()))
  }

  def findAverage(oldMean: Point, points: GenSeq[Point]): Point = if (points.length == 0) oldMean else {
    var x = 0.0
    var y = 0.0
    var z = 0.0
    points.seq.foreach { p =>
      x += p.x
      y += p.y
      z += p.z
    }
    new Point(x / points.length, y / points.length, z / points.length)
  }

  // Takes the map of classified points produced in the previous step, and the sequence of previous means.
  // The method returns the new sequence of means.
  // Take care to preserve order in the resulting generic sequence -- the mean i in the resulting 
  // sequence must correspond to the mean i from oldMeans.
  // Hint: Make sure you use the findAverage method that is predefined for you.
  def update(classified: GenMap[Point, GenSeq[Point]], oldMeans: GenSeq[Point]): GenSeq[Point] = {
    oldMeans.map { mean =>
      classified.get(mean) match {
        case Some(xs) => findAverage(mean, xs)
        case None     => mean
      }
    }
  }

  // The algorithm converged iff the square distance between the old and 
  // the new mean is less than or equal to eta, for all means.
  def converged(eta: Double)(oldMeans: GenSeq[Point], newMeans: GenSeq[Point]): Boolean = {
    oldMeans.zip(newMeans).forall {
      case (x, y) => x.squareDistance(y) <= eta
    }
  }

  @tailrec
  // The kMeans method should return the sequence of means, each corresponding to a specific cluster.
  // Hint: kMeans implements the steps 2-4 from the K-means pseudocode.
  // 1. Pick k points called means. This is called initialization.
  // 2. Associate each input point with the mean that is closest to it.
  //    We obtain k clusters of points, and we refer to this process as classifying the points.
  // 3. Update each mean to have the average value of the corresponding cluster.
  // 4. If the k means have significantly changed, go back to step 2. 
  //    If they did not, we say that the algorithm converged.
  // 5. The k means represent different clusters -- every point is in the cluster 
  //    corresponding to the closest mean.
  // your implementation need to be tail recursive
  @tailrec
  final def kMeans(points: GenSeq[Point], means: GenSeq[Point], eta: Double): GenSeq[Point] = {
    val classified = classify(points, means)
    val newMeans = update(classified, means)
    if (!converged(eta)(means, newMeans)) kMeans(points, newMeans, eta)
    else newMeans
  }
}

/** Describes one point in three-dimensional space.
 *
 *  Note: deliberately uses reference equality.
 */
class Point(val x: Double, val y: Double, val z: Double) {
  private def square(v: Double): Double = v * v
  def squareDistance(that: Point): Double = {
    square(that.x - x)  + square(that.y - y) + square(that.z - z)
  }
  private def round(v: Double): Double = (v * 100).toInt / 100.0
  override def toString = s"(${round(x)}, ${round(y)}, ${round(z)})"
}


object KMeansRunner {

  val standardConfig = config(
    Key.exec.minWarmupRuns -> 20,
    Key.exec.maxWarmupRuns -> 40,
    Key.exec.benchRuns -> 25,
    Key.verbose -> true
  ) withWarmer(new Warmer.Default)

  def main(args: Array[String]) {
    val kMeans = new KMeans()

    val numPoints = 500000
    val eta = 0.01
    val k = 32
    val points = kMeans.generatePoints(k, numPoints)
    val means = kMeans.initializeMeans(k, points)

    val seqtime = standardConfig measure {
      kMeans.kMeans(points, means, eta)
    }
    println(s"sequential time: $seqtime ms")

    val partime = standardConfig measure {
      val parPoints = points.par
      val parMeans = means.par
      kMeans.kMeans(parPoints, parMeans, eta)
    }
    println(s"parallel time: $partime ms")
    println(s"speedup: ${seqtime / partime}")
  }

}