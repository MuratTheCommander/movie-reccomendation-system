
package com.example.moviereccomendationsystem.configuration

import org.apache.spark.sql
import org.apache.spark.sql.SparkSession
import org.springframework.context.annotation.{Bean, Configuration}
import org.springframework.web.client.RestTemplate
import redis.clients.jedis.Jedis

import java.nio.file.Paths
import scala.collection.mutable

@Configuration
class AppConfig {


  @Bean
  def sparkSession(): SparkSession
  // (SparkSession, sql.DataFrame, sql.DataFrame, sql.DataFrame, sql.DataFrame)
  = {

    val sparkSession = SparkSession.builder()
      .appName("Movie Reccomendation System")
      .master("local[*]") // Use all available cores
      .getOrCreate()


    println("Spark is being initialized")

    //  (sparkSession,moviesDF,ratingsDF,tagsDF,linksDF)
    sparkSession
  }

  @Bean(name=Array("moviesDFGenerator"))
  def moviesDFGenerator(): sql.DataFrame={
    val resourcePath = Paths.get(getClass.getClassLoader.getResource("DATA/movies.csv").toURI)
    val moviesDF = sparkSession.read.format("csv")
      .option("header", "true")
      .load(resourcePath.toString) // Use absolute file path
    moviesDF.createTempView("movies_df_temp_view")
    println("moviesDF initialized successfully")
    moviesDF
  }

  @Bean(name=Array("linksDFGenerator"))
  def linksDFGenerator(): sql.DataFrame={
    val resourcePath = Paths.get(getClass.getClassLoader.getResource("DATA/links.csv").toURI)
    val linksDF = sparkSession.read.format("csv")
      .option("header", "true")
      .load(resourcePath.toString) // Use absolute file path
    linksDF.createTempView("links_df_temp_view")
    println("linksDF initialized successfully")
    linksDF
  }

  @Bean(name=Array("ratingsDFGenerator"))
  def ratingsDFGenerator(): sql.DataFrame={
    val resourcePath = Paths.get(getClass.getClassLoader.getResource("DATA/ratings.csv").toURI)
    val ratingsDF = sparkSession.read.format("csv")
      .option("header", "true")
      .load(resourcePath.toString) // Use absolute file path
    ratingsDF.createTempView("ratings_df_temp_view")
    println("linksDF initialized successfully")
    ratingsDF
  }

  /* @Bean
  def linksMoviesDFGenerator(): sql.DataFrame = {
    // Get the DataFrames only once
    val moviesDF = moviesDFGenerator()
    val linksDF = linksDFGenerator()

    // Rename movieId columns to avoid ambiguity in the join
    val moviesDFRenamed = moviesDF.withColumnRenamed("movieId", "movies_movieId")
    val linksDFRenamed = linksDF.withColumnRenamed("movieId", "links_movieId")

    // Perform the join between the renamed DataFrames on the 'movieId' column
    val joinedDF = moviesDFRenamed.join(linksDFRenamed, moviesDFRenamed("movies_movieId") === linksDFRenamed("links_movieId"))

    // Optionally create a temp view for the joined DataFrame
    joinedDF.createTempView("links_movies_df_temp_view")
    println("linksMoviesDF initialized successfully")

    joinedDF.show(10)

    // Return the joined DataFrame
    joinedDF
  }   */

  @Bean(name=Array("ratedMoviesSet"))
  def ratedMoviesSet():mutable.HashMap[Long,Double] = {

    val ratedMoviesMap = new mutable.HashMap[Long, Double]()

    println("ratedMoviesMap is defined")

    // You can add some data if necessary (e.g., some initial values to the map)

    ratedMoviesMap  // Return the map
  }

  @Bean(name=Array("likedMoviesSet"))
  def likedMoviesSet():mutable.HashSet[Long] = {

    val likedMoviesSet = new mutable.HashSet[Long]()

    println("likedMoviesSet is defined")

    // You can add some data if necessary (e.g., some initial values to the map)

    likedMoviesSet  // Return the map
  }



  @Bean(name=Array("jedis0"))
  def jedis0:Jedis={
    //movie datas
    val jedis0 = new Jedis("localhost",6379)
    jedis0.select(0)
    println("jedis0 is set up")
    jedis0
  }

  @Bean(name=Array("jedis1"))
  def jedis1:Jedis={
    //content based filtering results
    val jedis1 = new Jedis("localhost", 6379)
    jedis1.select(1)
    println("jedis1 is set up")
    jedis1
  }

  @Bean(name=Array("jedis2"))
  def jedis2:Jedis={
    //movie vectors
    val jedis2 = new Jedis("localhost",6379)
    jedis2.select(2)
    println("jedis2 is set up")
    jedis2
  }

  @Bean(name=Array("jedis3"))
  def jedis3:Jedis={
    //content based filtering results for each movie
    val jedis3 = new Jedis("localhost",6379)
    jedis3.select(3)
    println("jedis3 is set up")
    jedis3
  }

  @Bean
  def restTemplate: RestTemplate = {
    new RestTemplate()
  }

  @Bean(name=Array("contentBasedMoviesSet"))
  def contentBasedMoviesToReccomend():mutable.LinkedHashMap[Long,String]={

    val contentBasedMoviesSet =  new mutable.LinkedHashMap[Long,String]()

    println("contentBasedMoviesSet is defined")


    contentBasedMoviesSet  // Return the map

  }





}
