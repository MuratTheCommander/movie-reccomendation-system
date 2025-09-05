package com.example.moviereccomendationsystem.service

import org.apache.spark.mllib.linalg.distributed.{CoordinateMatrix, MatrixEntry}
import org.apache.spark.mllib.linalg.{SparseVector, Vectors}
import org.apache.spark.sql
import org.apache.spark.sql.SparkSession
import org.springframework.stereotype.Service
import redis.clients.jedis.Jedis

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import scala.jdk.CollectionConverters.CollectionHasAsScala
import scala.util.control.Breaks.{break, breakable}


@Service
class FunctionService {

  def cosineSimilaritySparse(vectorA: org.apache.spark.mllib.linalg.Vector, vectorB: org.apache.spark.mllib.linalg.Vector): Double = {
    val dotProduct = vectorA.toSparse.indices.intersect(vectorB.toSparse.indices).map { i =>
      vectorA(i) * vectorB(i)
    }.sum

    val normA = math.sqrt(vectorA.toSparse.values.map(x => x * x).sum)
    val normB = math.sqrt(vectorB.toSparse.values.map(x => x * x).sum)


    dotProduct / (normA * normB)
  }

  private def parseVectorString(vectorString: String): SparseVector = {
    // Step 1: Remove any leading or trailing whitespace
    val trimmedString = vectorString.trim

    // Step 2: Split the string into movie ID and vector data
    val parts = trimmedString.split(":") // Split by ":"

    // Ensure we have the expected number of parts (movieId, indices, values)
    if (parts.length != 3) {
      throw new IllegalArgumentException(s"Expected 3 parts, got ${parts.length}: '$trimmedString'")
    }

    val indicesPart = parts(1).trim // Indices part
    val valuesPart = parts(2).trim // Values part

    // Step 3: Remove brackets and split into arrays
    val indices = indicesPart.stripPrefix("[").stripSuffix("]").split(",").map(_.trim.toInt)
    val values = valuesPart.stripPrefix("[").stripSuffix("]").split(",").map(_.trim.toDouble)

    // Step 4: Create and return a SparseVector
    val vectorSize = indices.max + 1 // Assuming indices are 0-based
    val sparseVector = Vectors.sparse(vectorSize, indices, values).asInstanceOf[SparseVector]

    sparseVector
  }

  // Make sure the function is returning SparseVector
  private def buildUserItemVector(
                                   likedMovieIds: mutable.Set[Long],
                                   redisClient: Jedis,
                                   genreThreshold: Double,
                                   actorThreshold: Double,
                                   tagThreshold: Double
                                 ): SparseVector = {

    val vectorSize = 179015 // Total size of the vector (genres, actors, tags)
    val frequencies = Array.fill[Double](vectorSize)(0.0)

    // Maps to count occurrences of each attribute
    val genreCount = mutable.Map[Int, Int]()
    val actorCount = mutable.Map[Int, Int]()
    val tagCount = mutable.Map[Int, Int]()

    // Iterate over each liked movie ID
    for (movieId <- likedMovieIds) {
      val vectorString = redisClient.get(movieId.toString) // fetch the vector as a string

      if (vectorString != null) {
        val vectorData = parseVectorString(vectorString) // Convert to SparseVector

        // Retrieve indices and values
        val indices = vectorData.indices
        val values = vectorData.values

        // Count occurrences for each attribute type
        for (i <- indices.indices) {
          if (values(i) == 1.0) {
            frequencies(indices(i)) += 1.0 // Increment frequency for the index

            // Check if the index belongs to genre, tag, or actor based on ranges
            if (indices(i) >= 0 && indices(i) <= 19) { // Genre range
              genreCount(indices(i)) = genreCount.getOrElse(indices(i), 0) + 1
            } else if (indices(i) >= 20 && indices(i) <= 1600) { // Tag range
              tagCount(indices(i)) = tagCount.getOrElse(indices(i), 0) + 1
            } else if (indices(i) >= 1601 && indices(i) <= 179014) { // Actor range
              actorCount(indices(i)) = actorCount.getOrElse(indices(i), 0) + 1
            }
          }
        }
      }
    }

    // Build the user profile vector
    val userProfileIndices = ArrayBuffer[Int]()
    val userProfileValues = ArrayBuffer[Double]()

    // Total number of liked movies
    val totalLikedMovies = likedMovieIds.size.toDouble

    // Add genres to user profile vector based on threshold
    for ((index, count) <- genreCount) {
      if (count / totalLikedMovies > genreThreshold) {
        userProfileIndices += index
        userProfileValues += 1.0 // Can adjust the value here based on your logic
      }
    }

    // Add tags to user profile vector based on threshold
    for ((index, count) <- tagCount) {
      if (count / totalLikedMovies > tagThreshold) {
        userProfileIndices += index
        userProfileValues += 1.0 // Can adjust the value here based on your logic
      }
    }

    // Add actors to user profile vector based on threshold
    for ((index, count) <- actorCount) {
      if (count / totalLikedMovies > actorThreshold) {
        userProfileIndices += index
        userProfileValues += 1.0 // Can adjust the value here based on your logic
      }
    }

    // Create the final sparse vector for the user profile
    Vectors.sparse(vectorSize, userProfileIndices.toArray, userProfileValues.toArray).asInstanceOf[SparseVector]
  }

  def contentBasedFiltering(jedis2:Jedis,jedis3:Jedis,likedMovies:mutable.Set[Long],genreThreshold:Double,castThreshold:Double,
                            tagThreshold:Double,
                            contentBasedMoviesToRecommend: mutable.LinkedHashMap[Long,String],
                            sparkSession:SparkSession):mutable.LinkedHashMap[Long,String] = {

    println("Content Based Filtering Activated")

    val userVector =  buildUserItemVector(likedMovies,jedis2,genreThreshold,castThreshold,tagThreshold)

    for (key <- jedis2.keys("*").toArray) {
      val movieVectorString = jedis2.get(key.toString) // Fetch movie vector as string
      if (movieVectorString != null) {
        val movieVector = parseVectorString(movieVectorString) // Convert to SparseVector

        // Calculate cosine similarity between the user vector and movie vector
        val similarity = cosineSimilaritySparse(userVector, movieVector)
        println("Similarity for " + key + ": " + similarity)

        // Store the similarity score in jedis3 (sorted set)

        jedis3.zadd("movie_similarity_scores", similarity, key.toString)
      }
    }

    val topScores = jedis3.zrevrangeWithScores("movie_similarity_scores", 0, 99).asScala // Get top 10 scores

    var i = 0

    breakable{
      for(tuple <- topScores){

        if(contentBasedMoviesToRecommend.size < 10){
          if(!contentBasedMoviesToRecommend.contains(tuple.getElement.toLong) && !likedMovies.contains(tuple.getElement.toLong)){
   //         contentBasedMoviesToRecommend += tuple.getElement.toLong
            contentBasedMoviesToRecommend(tuple.getElement.toLong) = sparkSession.sql(
              s"SELECT title FROM movies_df_temp_view WHERE movieId = ${tuple.getElement.toLong}"
            ).head().getString(0)
            println(s"id of ${tuple.getElement.toLong} with the name ${contentBasedMoviesToRecommend.get(tuple.getElement.toLong)}")
            i+=1
          }
        }else{
          break
        }
      }
    }

    println("Content based movies to show below:")
    contentBasedMoviesToRecommend.foreach(println)

    println("Total numbers in the set is " + i)
    println("Content Based filtering deactivated")
    contentBasedMoviesToRecommend
  }

  private def createSparseVectorForCosine(ratingsMap: mutable.Map[Long, Double]): org.apache.spark.mllib.linalg.Vector = {
    // Determine the size of the vector based on the largest movie ID
    val vectorSize = if (ratingsMap.nonEmpty) ratingsMap.keys.max + 1 else 0

    // Extract indices (movie IDs) and values (ratings)
    val (indices, values) = ratingsMap.toSeq.sortBy(_._1).unzip

    val indicesArray = indices.map(_.toInt).toArray

    // Create the sparse vector with specified size, indices, and values
    val vector =  Vectors.sparse(vectorSize.toInt, indicesArray, values.toArray)
    vector
  }



 /* def collaborativeFiltering(sparkSession:SparkSession,ratingsDF: sql.DataFrame,userRatingsMap:mutable.Map[Long, Double]) : mutable.LinkedHashMap[Long,String] = {

    // Convert DataFrame to RDD of MatrixEntry
    val matrixEntriesRDD = ratingsDF.rdd.map(row => {
      val userId = row.getAs[String]("userId").toLong
      val movieId = row.getAs[String]("movieId").toLong
      val rating = row.getAs[String]("rating").toDouble
      MatrixEntry(userId, movieId, rating)
    })

    // Create CoordinateMatrix
    val coordinateMatrix = new CoordinateMatrix(matrixEntriesRDD)

    // Convert CoordinateMatrix to IndexedRowMatrix
    val indexedRowMatrix = coordinateMatrix.toIndexedRowMatrix()

    // Collect rows as an array
    val rowsArray = indexedRowMatrix.rows.collect()

    println(s"there are ${rowsArray.length} elements in the rowsArray")

    val mainVector = createSparseVectorForCosine(userRatingsMap)

    //map where the results of the cosine similarity are sstored
    val similarityMap: mutable.Map[Long, Double] = mutable.Map.empty

    for (row <- rowsArray) {
      val userId = row.index.toString
      val vector = row.vector
      val similarity = cosineSimilaritySparse(mainVector, vector).toString
      similarityMap.addOne((userId.toLong, similarity.toDouble))
      println(s"Cosine similarity between row mainVector and row ${row.index}: $similarity")
    }

    // Retrieve the top 5 users with the highest similarity scores
    val top100SimilarUsers = similarityMap.toSeq.sortBy(-_._2).map(_._1)
    top100SimilarUsers.foreach(println)

    // Execute a single SQL query to get the counts for all users
    val userRatingCounts = sparkSession.sql(
      "SELECT userId, COUNT(*) AS count FROM ratings_df_temp_view GROUP BY userId"
    )

    // Collect the results into a Map
    val userRatingCountsMap: Map[Long, Long] = userRatingCounts
      .collect()
      .map(row => {
        val userId = row.getAs[String]("userId").toLong // Convert String to Long
        val count = row.getAs[Long]("count") // Assuming count is already Long
        (userId, count)
      }) // (userId, count)
      .toMap

    val top100QualityUsers: mutable.Map[Long, Long] = mutable.Map.empty

    top100SimilarUsers.foreach { user_id =>

      top100QualityUsers += (user_id -> userRatingCountsMap(user_id))

    }

    val top10QuallityUsers: Seq[Long] = top100QualityUsers.toSeq.sortBy(-_._2).take(10).map(_._1)

    val usersAndTheirMovies = scala.collection.mutable.Map[Long, Array[Long]]()


    val similarUserMovieToReccomend: mutable.LinkedHashMap[Long, String] = mutable.LinkedHashMap.empty[Long, String]

    for (userId <- top10QuallityUsers) {

      val userRatedMovies = sparkSession.sql(s"SELECT movieId FROM ratings_df_temp_view WHERE userId = $userId ORDER BY rating DESC").collect()
        .map(row => row.getAs[String]("movieId").toLong)

      usersAndTheirMovies(userId) = userRatedMovies
      //      val isExistingMovie = userRatedMovies(0)
      breakable {
        for (i <- userRatedMovies.indices) {
          val currentMovie = userRatedMovies(i)
          if (!similarUserMovieToReccomend.contains(currentMovie)) {
       //     similarUserMovieToReccomend = similarUserMovieToReccomend :+ currentMovie
            similarUserMovieToReccomend(currentMovie) = sparkSession.sql(s"SELECT title FROM movies_df_temp_view WHERE movieId = ${currentMovie}")
              .head().getString(0)
            break() // Break out of the loop when the movie is added
          }
          // Optionally, you can add additional logic in the else block if needed
        }
      }
    }
    println("Movies to send to user: ")
    similarUserMovieToReccomend.foreach(println)
    similarUserMovieToReccomend
  } */

  def collaborativeFiltering(
                              sparkSession: SparkSession,
                              ratingsDF: sql.DataFrame,
                              userRatingsMap: mutable.Map[Long, Double]
                            ): scala.collection.mutable.LinkedHashMap[Long, String] = {

    // Convert DataFrame to RDD of MatrixEntry
    val matrixEntriesRDD = ratingsDF.rdd.map(row => {
      val userId = row.getAs[String]("userId").toLong
      val movieId = row.getAs[String]("movieId").toLong
      val rating = row.getAs[String]("rating").toDouble
      MatrixEntry(userId, movieId, rating)
    })

    // Create CoordinateMatrix
    val coordinateMatrix = new CoordinateMatrix(matrixEntriesRDD)

    // Convert CoordinateMatrix to IndexedRowMatrix
    val indexedRowMatrix = coordinateMatrix.toIndexedRowMatrix()

    // Collect rows as an array
    val rowsArray = indexedRowMatrix.rows.collect()
    println(s"there are ${rowsArray.length} elements in the rowsArray")

    val mainVector = createSparseVectorForCosine(userRatingsMap)

    // Map to store cosine similarity results
    val similarityMap: mutable.Map[Long, Double] = mutable.Map.empty

    for (row <- rowsArray) {
      val userId = row.index.toLong
      val vector = row.vector
      val similarity = cosineSimilaritySparse(mainVector, vector)
      similarityMap.addOne((userId, similarity))
      println(s"Cosine similarity between mainVector and user $userId: $similarity")
    }

    // Retrieve the top 100 users with the highest similarity scores
    val top100SimilarUsers = similarityMap.toSeq.sortBy(-_._2).map(_._1).take(100)
    println("Top 100 similar users from checkpoint 1:")
    top100SimilarUsers.foreach(println)

    // Execute a single SQL query to get the counts for all users
    val userRatingCounts = sparkSession.sql(
      "SELECT userId, COUNT(*) AS count FROM ratings_df_temp_view GROUP BY userId"
    )

    // Collect results into a map
    val userRatingCountsMap: Map[Long, Long] = userRatingCounts
      .collect()
      .map(row => {
        val userId = row.getAs[String]("userId").toLong
        val count = row.getAs[Long]("count")
        (userId, count)
      })
      .toMap

    // Map for storing top 100 quality users with weighted scores
    val top50QualityUsers: mutable.Map[Long, Double] = mutable.Map.empty

    val maxCount = userRatingCountsMap.values.max

    top100SimilarUsers.foreach { userId =>
      val similarityScore = similarityMap.getOrElse(userId, 0.0)
      val ratingCount = userRatingCountsMap.getOrElse(userId, 0L).toDouble / maxCount.toDouble
      // Weighted score calculation
      val weightedScore = similarityScore * 0.7 + ratingCount * 0.3
      top50QualityUsers += (userId -> weightedScore)
    }

    println("Top 50 quality users from checkpoint 3:")
    top50QualityUsers.foreach {
      case (userId, score) => println(s"User $userId with weighted score $score")
    }

    // Select the top 10 quality users
    val top10QualityUsers: Seq[Long] = top50QualityUsers.toSeq
      .sortBy(-_._2)
      .take(10)
      .map(_._1)

    println("Top 10 quality users from checkpoint 3:")
    top10QualityUsers.foreach(println)

    // Map to store users and their top-rated movies
    val usersAndTheirMovies = mutable.Map[Long, Array[Long]]()

    var similarUserMovieToRecommend: List[Long] = List.empty

    for (userId <- top10QualityUsers if similarUserMovieToRecommend.size < 10) {
      val userRatedMovies = sparkSession
        .sql(s"SELECT movieId FROM ratings_df_temp_view WHERE userId = $userId ORDER BY rating DESC")
        .collect()
        .map(row => row.getAs[String]("movieId").toLong)

      usersAndTheirMovies(userId) = userRatedMovies

      for (movieId <- userRatedMovies if similarUserMovieToRecommend.size < 10) {
        if (!userRatingsMap.contains(movieId) && !similarUserMovieToRecommend.contains(movieId)) {
          similarUserMovieToRecommend = similarUserMovieToRecommend :+ movieId
        }
      }
    }

    println("Movies to send to user:")
    similarUserMovieToRecommend.foreach(println)

    // Fetch titles for the final 10 recommended movies
    val movieTitlesMap = scala.collection.mutable.LinkedHashMap[Long, String]()

    similarUserMovieToRecommend.foreach { movieId =>
      val titleRow = sparkSession
        .sql(s"SELECT title FROM movies_df_temp_view WHERE movieId = $movieId")
        .collect()
      if (titleRow.nonEmpty) {
        val title = titleRow.head.getAs[String]("title")
        movieTitlesMap += (movieId -> title)
      }
    }

    println("Movies and titles to send to user:")
    movieTitlesMap.foreach {
      case (movieId, title) => println(s"Movie ID: $movieId, Title: $title")
    }

    movieTitlesMap
  }



}


